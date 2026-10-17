package com.ruoyi.web.controller.system;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.ai.service.UserWorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * User workspace API for the Vue3 user-facing frontend.
 */
@RestController
@RequestMapping("/edu/workspace")
public class EduWorkspaceController extends BaseController
{
    @Autowired
    private UserWorkspaceService workspaceService;

    @GetMapping
    public AjaxResult getWorkspace()
    {
        Long userId = SecurityUtils.getUserId();
        return success(workspaceService.getWorkspace(userId));
    }

    @PutMapping
    public AjaxResult saveWorkspace(@RequestBody(required = false) Map<String, Object> payload)
    {
        Long userId = SecurityUtils.getUserId();
        workspaceService.saveWorkspace(userId, payload);
        return success();
    }
}
