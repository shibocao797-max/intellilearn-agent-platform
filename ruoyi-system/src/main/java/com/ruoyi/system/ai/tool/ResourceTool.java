package com.ruoyi.system.ai.tool;

import com.ruoyi.common.utils.file.FileStorageService;
import com.ruoyi.system.ai.support.UserFacingTextSanitizer;
import com.ruoyi.system.domain.EduResource;
import com.ruoyi.system.mapper.EduResourceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 资源工具 — 资源存储/检索
 *
 * 已完成持久化改造：saveDocument() 写入 edu_resource 表，
 * searchResources() 调用已有 Mapper 查询。
 * uploadFile() 复用 FileStorageService，自动适配本地存储或 MinIO。
 *
 * @author edu-agent
 */
@Component
public class ResourceTool {

    private static final Logger log = LoggerFactory.getLogger(ResourceTool.class);

    /** 可见发布资源 */
    public static final String STATUS_ACTIVE = "0";
    /** AI 初稿，等待审核 */
    public static final String STATUS_REVIEWING = "1";
    /** 已被审核纠偏后的新版替换 */
    public static final String STATUS_REPLACED = "2";
    /** 审核未通过且未生成可用替代 */
    public static final String STATUS_REJECTED = "3";

    @Autowired
    private EduResourceMapper eduResourceMapper;

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * 保存生成的文档资源到 edu_resource 表
     *
     * @param title   资源标题
     * @param content Markdown 正文内容
     * @param tags    标签（逗号分隔）
     * @param userId  用户ID（AI 生成资源归属的用户）
     * @return 资源 ID（自增主键）
     */
    public Long saveDocument(String title, String content, String tags, Long userId) {
        EduResource res = new EduResource();
        res.setTitle(UserFacingTextSanitizer.sanitize(title));
        res.setContent(UserFacingTextSanitizer.sanitize(content));
        res.setType("article");         // 文档类型
        res.setSource("internal");      // AI 内部生成
        res.setTag(UserFacingTextSanitizer.sanitize(tags));
        res.setStatus(STATUS_REVIEWING);
        res.setCreateBy("AI");
        res.setUserId(userId);          // AI 生成资源归属指定用户
        eduResourceMapper.insertResource(res);
        // insertResource 配置了 useGeneratedKeys，id 自动回填
        log.info("文档已保存: id={}, title={}, userId={}, length={}", res.getId(), title, userId,
                content != null ? content.length() : 0);
        return res.getId();
    }

    /**
     * 保存任意类型的资源到 edu_resource 表（通用方法）
     *
     * 各类 Agent 通过此方法统一落库，type 字段区分资源类型。
     *
     * @param title   资源标题
     * @param content 资源内容
     * @param type    资源类型: article / mindmap / code / video / image
     * @param tags    标签
     * @param userId  用户ID（AI 生成资源归属的用户）
     * @return 资源 ID
     */
    public Long saveResource(String title, String content, String type, String tags, Long userId) {
        EduResource res = new EduResource();
        res.setTitle(UserFacingTextSanitizer.sanitize(title));
        res.setContent(UserFacingTextSanitizer.sanitize(content));
        res.setType(type);
        res.setSource("internal");
        res.setTag(UserFacingTextSanitizer.sanitize(tags));
        res.setStatus(STATUS_REVIEWING);
        res.setCreateBy("AI");
        res.setUserId(userId);          // AI 生成资源归属指定用户
        eduResourceMapper.insertResource(res);
        log.info("资源已保存: id={}, title={}, type={}, userId={}", res.getId(), title, type, userId);
        return res.getId();
    }

    /**
     * 保存媒体资源，额外写入 source_url / cover_url，便于后续前端直接预览。
     */
    public Long saveMediaResource(String title, String content, String type, String tags,
                                  Long userId, String sourceUrl, String coverUrl, String duration) {
        EduResource res = new EduResource();
        res.setTitle(UserFacingTextSanitizer.sanitize(title));
        res.setContent(UserFacingTextSanitizer.sanitize(content));
        res.setType(type);
        res.setSource("agnes");
        // URLs are protocol data. Sanitizing provider names inside a hostname
        // or path would persist an invalid address and break media previews.
        res.setSourceUrl(UserFacingTextSanitizer.sanitizeMediaUrl(sourceUrl));
        res.setCoverUrl(UserFacingTextSanitizer.sanitizeMediaUrl(coverUrl));
        res.setDuration(duration);
        res.setTag(UserFacingTextSanitizer.sanitize(tags));
        res.setStatus(STATUS_REVIEWING);
        res.setCreateBy("AI");
        res.setUserId(userId);
        eduResourceMapper.insertResource(res);
        log.info("媒体资源已保存: id={}, title={}, type={}, sourceUrl={}, userId={}",
                res.getId(), title, type, sourceUrl, userId);
        return res.getId();
    }

    /**
     * 根据标签和难度检索已有资源（SQL 模糊匹配）
     *
     * @param tag 标签关键词
     * @return 资源列表
     */
    public List<EduResource> searchResources(String tag) {
        if (tag == null || tag.isBlank()) {
            return Collections.emptyList();
        }
        EduResource query = new EduResource();
        query.setTag(tag);
        query.setStatus(STATUS_ACTIVE);
        return eduResourceMapper.selectResourceList(query);
    }

    /**
     * 更新 AI 资源状态。用于审核通过、纠偏替换、审核拒绝等版本流转。
     */
    public boolean updateResourceStatus(String persistedId, String status) {
        Long id = parseResourceId(persistedId);
        if (id == null || status == null || status.isBlank()) {
            return false;
        }
        EduResource resource = new EduResource();
        resource.setId(id);
        resource.setStatus(status);
        resource.setUpdateBy("AI");
        return eduResourceMapper.updateResourceStatus(resource) > 0;
    }

    public void publishResource(String persistedId) {
        updateResourceStatus(persistedId, STATUS_ACTIVE);
    }

    public void markReplaced(String persistedId) {
        updateResourceStatus(persistedId, STATUS_REPLACED);
    }

    public void markRejected(String persistedId) {
        updateResourceStatus(persistedId, STATUS_REJECTED);
    }

    public boolean updateMediaResult(Long resourceId, String content, String sourceUrl, String coverUrl, String status) {
        if (resourceId == null) {
            return false;
        }
        EduResource resource = new EduResource();
        resource.setId(resourceId);
        resource.setContent(UserFacingTextSanitizer.sanitize(content));
        // Keep the upstream media URL unchanged when polling an async result.
        resource.setSourceUrl(UserFacingTextSanitizer.sanitizeMediaUrl(sourceUrl));
        resource.setCoverUrl(UserFacingTextSanitizer.sanitizeMediaUrl(coverUrl));
        resource.setStatus(status);
        resource.setUpdateBy("AI");
        return eduResourceMapper.updateMediaResource(resource) > 0;
    }

    private Long parseResourceId(String persistedId) {
        if (persistedId == null || persistedId.isBlank()) {
            return null;
        }
        int index = persistedId.lastIndexOf(':');
        String raw = index >= 0 ? persistedId.substring(index + 1) : persistedId;
        try {
            return Long.parseLong(raw);
        } catch (Exception e) {
            log.warn("无法解析资源ID: persistedId={}", persistedId);
            return null;
        }
    }

    /**
     * 上传 AI 生成文件，返回访问 URL。
     */
    public String uploadFile(String fileName, byte[] content, String contentType) {
        log.info("上传文件: fileName={}, size={}, type={}",
                fileName, content != null ? content.length : 0, contentType);
        try {
            String safeFileName = safeFileName(fileName, contentType);
            return fileStorageService.uploadBytes("ai-generated", safeFileName, content, contentType);
        } catch (Exception e) {
            log.error("AI生成文件上传失败: fileName={}, error={}", fileName, e.getMessage(), e);
            return "";
        }
    }

    private String safeFileName(String fileName, String contentType) {
        String name = fileName == null || fileName.isBlank()
                ? "ai-file-" + UUID.randomUUID()
                : fileName.trim().replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
        if (!name.contains(".")) {
            name = name + extensionOf(contentType);
        }
        return System.currentTimeMillis() + "_" + name;
    }

    private String extensionOf(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return ".bin";
        }
        String type = contentType.toLowerCase(Locale.ROOT);
        if (type.contains("png")) return ".png";
        if (type.contains("jpeg") || type.contains("jpg")) return ".jpg";
        if (type.contains("html")) return ".html";
        if (type.contains("json")) return ".json";
        if (type.contains("svg")) return ".svg";
        if (type.contains("text")) return ".txt";
        return ".bin";
    }
}
