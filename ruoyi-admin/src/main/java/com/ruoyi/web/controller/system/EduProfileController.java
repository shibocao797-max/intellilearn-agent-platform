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
import com.ruoyi.system.service.IEduActionPlanService;
import com.ruoyi.system.service.IEduProfileConfirmationService;

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

    @Autowired
    private IEduActionPlanService actionPlanService;

    @Autowired
    private IEduProfileConfirmationService profileConfirmationService;

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
        Map<String, Object> profileView = profileTool.buildProfileView(userId, lp);
        if (lp == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("version", 0);
            empty.put("dimensionCount", 0);
            empty.put("lastUpdated", null);
            empty.put("profileJson", "{}");
            empty.put("dimensions", profileView.getOrDefault("dimensions", Map.of()));
            empty.put("overview", profileView.getOrDefault("overview", Map.of()));
            empty.put("profileView", profileView);
            return success(empty);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", lp.getVersion());
        result.put("dimensionCount", lp.getDimensionCount());
        result.put("lastUpdated", lp.getLastExtractAt() != null ? lp.getLastExtractAt().toString() : null);
        result.put("profileJson", profileTool.buildEffectiveProfileJson(userId, lp));
        result.put("dimensions", profileView.getOrDefault("dimensions", Map.of()));
        result.put("overview", profileView.getOrDefault("overview", Map.of()));
        result.put("profileView", profileView);

        return success(result);
    }

    /** Confirm or reject the current conclusion for one profile dimension. */
    @PatchMapping("/dimensions/{dimensionKey}/confirmation")
    public AjaxResult confirmDimension(@PathVariable String dimensionKey,
                                       @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getUserId();
        Integer profileVersion = number(body == null ? null : body.get("profileVersion"));
        String status = body == null || body.get("status") == null ? null : String.valueOf(body.get("status"));
        String rejectReason = body == null || body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return success(profileConfirmationService.confirmDimension(userId, dimensionKey, profileVersion, status, rejectReason));
    }

    private static Integer number(Object value) {
        if (value == null) return null;
        try { return Integer.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    /** Returns an actionable plan with an explicit evidence-sufficiency state. */
    @PostMapping("/action-plan")
    public AjaxResult actionPlan() {
        Long userId = SecurityUtils.getUserId();
        return success(actionPlanService.generate(userId));
    }
}
