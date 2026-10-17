package com.ruoyi.web.controller.system;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.IEduReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 学习报告 Controller — 聚合多数据源生成真实学习报告
 *
 * @author edu-agent
 */
@RestController
@RequestMapping("/edu/report")
public class EduReportController extends BaseController {

    @Autowired
    private IEduReportService eduReportService;

    /**
     * 学习总览 — 供用户首页统计卡片 + 图表使用
     *
     * GET /edu/report/summary
     * 返回: todayMinutes, masteredConcepts, quizTotal, quizCorrectRate,
     *       consecutiveDays, totalStudyHours, completedNodes, radarData, dailyDuration
     */
    @GetMapping("/summary")
    public AjaxResult getSummary(@RequestParam(defaultValue = "30") Integer rangeDays) {
        Long userId = SecurityUtils.getUserId();
        Map<String, Object> summary = eduReportService.getSummary(userId, rangeDays == null ? 30 : rangeDays);
        return success(summary);
    }

    /**
     * 详细报告 — 含行为日志、答题记录、学习路径等信息
     *
     * GET /edu/report/detailed
     */
    @GetMapping("/detailed")
    public AjaxResult getDetailed(@RequestParam(defaultValue = "30") Integer rangeDays) {
        Long userId = SecurityUtils.getUserId();
        Map<String, Object> detailed = eduReportService.getDetailed(userId, rangeDays == null ? 30 : rangeDays);
        return success(detailed);
    }
}
