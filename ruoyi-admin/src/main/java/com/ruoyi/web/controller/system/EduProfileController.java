package com.ruoyi.web.controller.system;

import java.util.Map;
import java.util.LinkedHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.LearnerProfile;
import com.ruoyi.system.mapper.LearnerProfileMapper;
import com.ruoyi.system.ai.tool.ProfileTool;

/**
 * 学习画像 Controller
 *
 * @author edu-agent
 */
@RestController
@RequestMapping("/edu/profile")
public class EduProfileController extends BaseController {

    @Autowired
    private LearnerProfileMapper learnerProfileMapper;

    @Autowired
    private ProfileTool profileTool;

    /** 查询当前用户画像 */
    @GetMapping
    public AjaxResult getProfile() {
        Long userId = SecurityUtils.getUserId();
        String profileJson = profileTool.loadProfile(userId);
        return success(Map.of("userId", userId, "profile", profileJson));
    }

    /** 画像详情（含维度解析） */
    @GetMapping("/detail")
    public AjaxResult detail() {
        Long userId = SecurityUtils.getUserId();
        LearnerProfile lp = learnerProfileMapper.selectProfileByUserId(userId);
        if (lp == null) {
            return success(Map.of("profile", Map.of("version", 0, "dimensionCount", 0,
                    "dimensions", Map.of())));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", lp.getVersion());
        result.put("dimensionCount", lp.getDimensionCount());
        result.put("lastUpdated", lp.getLastExtractAt() != null ? lp.getLastExtractAt().toString() : "");
        result.put("profileJson", lp.getProfileJson());

        try {
            var json = com.alibaba.fastjson2.JSON.parseObject(lp.getProfileJson());
            result.put("dimensions", json.get("dimensions"));
        } catch (Exception e) {
            result.put("dimensions", Map.of());
        }

        return success(result);
    }
}
