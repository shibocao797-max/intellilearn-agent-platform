package com.ruoyi.web.controller.system;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.EduResource;
import com.ruoyi.system.mapper.EduResourceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    // ================================================================
    // 用户端接口（已登录用户可访问）
    // ================================================================

    /**
     *
     * 仅查询当前登录用户自己的 AI 生成资源
     */
    @GetMapping("/user/list")
    public TableDataInfo userList(@RequestParam(required = false) String type,
                               @RequestParam(defaultValue = "1") Integer pageNum,
                               @RequestParam(defaultValue = "20") Integer pageSize) {
        EduResource query = new EduResource();
        query.setType(type);
        query.setStatus("0");
        // 关键：只查当前用户自己的 AI 生成资源，与首页推荐资源(user_id IS NULL)隔离
        query.setUserId(SecurityUtils.getUserId());
        startPage();
        List<EduResource> list = eduResourceMapper.selectResourceList(query);
        return getDataTable(list);
    }

    /**
     * 用户端资源详情
     */
    @GetMapping("/detail/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        EduResource res = eduResourceMapper.selectResourceById(id);
        if (res != null) {
            // 记录查看资源行为（静默记录，失败不影响主流程）
            try {
                if (behaviorTool != null) {
                    behaviorTool.logBehavior(SecurityUtils.getUserId(), "VIEW_RESOURCE",
                            "resource", id, 0);
                }
            } catch (Exception ignore) { /* 行为记录失败不阻塞 */ }
        }
        return res != null ? success(res) : error("资源不存在");
    }
}
