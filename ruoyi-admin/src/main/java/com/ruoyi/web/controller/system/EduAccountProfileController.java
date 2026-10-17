package com.ruoyi.web.controller.system;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.ai.service.UserAccountProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Account-owned profile and preferences API for the Vue3 student frontend. */
@RestController
@RequestMapping("/edu/account/profile")
public class EduAccountProfileController extends BaseController
{
    @Autowired
    private UserAccountProfileService accountProfileService;

    @GetMapping
    public AjaxResult getProfile()
    {
        Long userId = SecurityUtils.getUserId();
        return success(accountProfileService.getProfile(userId));
    }

    @PutMapping
    public AjaxResult saveProfile(@RequestBody(required = false) Map<String, Object> payload)
    {
        Long userId = SecurityUtils.getUserId();
        return success(accountProfileService.saveProfile(userId, payload));
    }
}
