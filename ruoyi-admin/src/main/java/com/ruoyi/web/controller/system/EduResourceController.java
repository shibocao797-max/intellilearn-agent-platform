package com.ruoyi.web.controller.system;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.EduResource;
import com.ruoyi.system.ai.support.UserFacingTextSanitizer;
import com.ruoyi.system.mapper.EduResourceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 用户端 AI 生成资源 Controller
 *
 * @author edu-agent
 */
@RestController
@RequestMapping("/edu/resource")
public class EduResourceController extends BaseController {

    @Autowired
    private EduResourceMapper eduResourceMapper;

    /** 学习行为追踪工具 — 记录用户查看资源行为（required=false 兼容旧环境） */
    @Autowired(required = false)
    private com.ruoyi.system.ai.tool.BehaviorTool behaviorTool;

    @Autowired(required = false)
    private com.ruoyi.system.ai.media.AgnesMediaClient agnesMediaClient;

    @Autowired(required = false)
    private com.ruoyi.system.ai.tool.ResourceTool resourceTool;

    // ================================================================
    // 用户端接口（已登录用户可访问）
    // ================================================================

    /**
     * 仅查询当前登录用户自己的 AI 生成资源。
     *
     * @param status 状态筛选：visible（默认，返回全部已生成资源）、all 或 0/1/2/3
     */
    @GetMapping("/user/list")
    public TableDataInfo userList(@RequestParam(required = false) String type,
                               @RequestParam(defaultValue = "visible") String status,
                               @RequestParam(defaultValue = "1") Integer pageNum,
                               @RequestParam(defaultValue = "20") Integer pageSize) {
        EduResource query = new EduResource();
        query.setType(type);
        String normalizedStatus = normalizeUserListStatus(status);
        if (normalizedStatus != null) {
            query.setStatus(normalizedStatus);
        }
        // 只查当前用户自己的资源；审核状态不决定资源是否展示。
        query.setUserId(SecurityUtils.getUserId());
        startPage();
        List<EduResource> list = eduResourceMapper.selectResourceList(query);
        list.replaceAll(this::sanitizeResource);
        return getDataTable(list);
    }

    /**
     * 规范化资源列表状态筛选。
     *
     * <p>空值、{@code visible} 或 {@code all} 表示不过滤状态；其余仅接受资源表约定的 0/1/2/3。
     * 非法值按 all 处理，避免因无效筛选值把用户资源误判为空。</p>
     */
    private String normalizeUserListStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if ("all".equals(normalized) || "visible".equals(normalized)) {
            return null;
        }
        return switch (normalized) {
            case "0", "1", "2", "3" -> normalized;
            default -> null;
        };
    }

    /**
     * 用户端资源详情
     */
    @GetMapping("/detail/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.getUserId();
        EduResource res = eduResourceMapper.selectResourceByIdAndUserId(id, currentUserId);
        if (isHiddenFromUser(res)) {
            res = null;
        }
        if (res != null) {
            // 记录查看资源行为（静默记录，失败不影响主流程）
            try {
                if (behaviorTool != null) {
                    behaviorTool.logBehavior(currentUserId, "VIEW_RESOURCE",
                            "resource", id, 0);
                }
            } catch (Exception ignore) { /* 行为记录失败不阻塞 */ }
        }
        return res != null ? success(sanitizeResource(res)) : error("资源不存在");
    }

    /**
     * 删除当前登录用户自己的资源。删除条件同时包含资源 ID 和用户 ID，
     * 管理端推荐资源及其他用户资源均不能通过此接口删除。
     */
    @DeleteMapping("/user/{id}")
    public AjaxResult deleteUserResource(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.getUserId();
        int rows = eduResourceMapper.deleteResourceByIdAndUserId(id, currentUserId);
        if (rows != 1) {
            return error("资源不存在或无删除权限");
        }
        try {
            if (behaviorTool != null) {
                behaviorTool.logBehavior(currentUserId, "DELETE_RESOURCE", "resource", id, 0);
            }
        } catch (Exception ignore) { /* 行为记录失败不阻塞删除 */ }
        return success(Map.of("deleted", id));
    }

    /**
     * 可播放教学动画资源。
     *
     * 后端直接返回 AI 生成的自包含 HTML，供 iframe 或新窗口播放。
     */
    @GetMapping(value = "/animation/{id}/play", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> playAnimation(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.getUserId();
        EduResource res = eduResourceMapper.selectResourceByIdAndUserId(id, currentUserId);
        if (isHiddenFromUser(res)) {
            return ResponseEntity.status(404).contentType(MediaType.TEXT_PLAIN).body("资源不存在或无权访问");
        }
        if (!"animation".equals(res.getType()) || res.getContent() == null
                || !res.getContent().toLowerCase().contains("<!doctype html")) {
            return ResponseEntity.status(400).contentType(MediaType.TEXT_PLAIN).body("该资源不是可播放动画");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header("X-Content-Type-Options", "nosniff")
                .body(UserFacingTextSanitizer.sanitize(res.getContent()));
    }

    /**
     * 查询 Agnes 视频异步任务状态。
     */
    @GetMapping("/video/{videoId}/status")
    public AjaxResult videoStatus(@PathVariable String videoId,
                                  @RequestParam(required = false) Long resourceId,
                                  @RequestParam(defaultValue = "1") Integer attempts,
                                  @RequestParam(defaultValue = "2000") Long intervalMs) {
        if (agnesMediaClient == null) {
            return error("媒体服务未启用");
        }
        try {
            Long currentUserId = SecurityUtils.getUserId();
            EduResource ownedResource = resourceId == null ? null
                    : eduResourceMapper.selectResourceByIdAndUserId(resourceId, currentUserId);
            if (resourceId != null && isHiddenFromUser(ownedResource)) {
                return error("资源不存在或无访问权限");
            }
            int safeAttempts = Math.max(1, Math.min(10, attempts == null ? 1 : attempts));
            long safeIntervalMs = Math.max(0L, Math.min(10000L, intervalMs == null ? 2000L : intervalMs));
            var status = agnesMediaClient.pollVideoStatus(videoId, safeAttempts, safeIntervalMs);
            Object videoUrl = status.get("videoUrl");
            boolean completed = "completed".equals(String.valueOf(status.get("status")));
            boolean failed = "failed".equals(String.valueOf(status.get("status")));
            boolean hasVideoUrl = videoUrl != null && !String.valueOf(videoUrl).isBlank();
            boolean resourceUpdated = false;
            if (resourceId != null && resourceTool != null) {
                if (completed && hasVideoUrl) {
                    resourceUpdated = resourceTool.updateMediaResult(resourceId,
                            com.alibaba.fastjson2.JSON.toJSONString(status),
                            String.valueOf(videoUrl),
                            null,
                            com.ruoyi.system.ai.tool.ResourceTool.STATUS_ACTIVE);
                } else if (failed) {
                    resourceUpdated = resourceTool.updateMediaResult(resourceId,
                            com.alibaba.fastjson2.JSON.toJSONString(status),
                            null,
                            null,
                            com.ruoyi.system.ai.tool.ResourceTool.STATUS_REJECTED);
                }
            }
            status.put("resourceUpdated", resourceUpdated);
            return success(UserFacingTextSanitizer.sanitizeValue(status));
        } catch (Exception e) {
            return error("视频状态查询失败: " + e.getMessage());
        }
    }

    private boolean isHiddenFromUser(EduResource resource) {
        return resource == null;
    }

    private EduResource sanitizeResource(EduResource resource) {
        if (resource == null) {
            return null;
        }
        resource.setTitle(UserFacingTextSanitizer.sanitize(resource.getTitle()));
        resource.setDescription(UserFacingTextSanitizer.sanitize(resource.getDescription()));
        resource.setContent(UserFacingTextSanitizer.sanitize(resource.getContent()));
        resource.setTag(UserFacingTextSanitizer.sanitize(resource.getTag()));
        // Keep media URLs byte-for-byte stable. Provider-name redaction must
        // never turn a valid hostname or path into an unusable URL.
        resource.setSourceUrl(UserFacingTextSanitizer.sanitizeMediaUrl(resource.getSourceUrl()));
        resource.setCoverUrl(UserFacingTextSanitizer.sanitizeMediaUrl(resource.getCoverUrl()));
        return resource;
    }
}
