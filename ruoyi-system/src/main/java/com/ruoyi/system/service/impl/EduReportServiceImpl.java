package com.ruoyi.system.service.impl;

import com.ruoyi.system.ai.tool.BehaviorTool;
import com.ruoyi.system.ai.tool.ProfileTool;
import com.ruoyi.system.mapper.QuizRecordMapper;
import com.ruoyi.system.service.IEduReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 学习报告 Service 实现 — 聚合多表数据生成学习报告
 *
 * 数据来源：
 * - learning_behavior_log → 学习时长/连续天数/行为日志
 * - quiz_record → 答题统计/正确率
 * - learner_profile → 维度数
 *
 * @author edu-agent
 */
@Service
public class EduReportServiceImpl implements IEduReportService {

    private static final Logger log = LoggerFactory.getLogger(EduReportServiceImpl.class);

    @Autowired
    private BehaviorTool behaviorTool;

    @Autowired
    private ProfileTool profileTool;

    @Autowired
    private QuizRecordMapper quizRecordMapper;

    @Override
    public Map<String, Object> getSummary(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);

        try {
            // ① 行为统计（近7天）
            Map<String, Object> behaviorStats = behaviorTool.getRecentStats(userId, 7);

            int totalActions = behaviorStats.get("totalActions") != null
                    ? (int) behaviorStats.get("totalActions") : 0;
            double totalStudyHours = behaviorStats.get("totalStudyHours") != null
                    ? ((Number) behaviorStats.get("totalStudyHours")).doubleValue() : 0.0;
            int todayMinutes = behaviorStats.get("todayMinutes") != null
                    ? (int) behaviorStats.get("todayMinutes") : 0;
            int completedNodes = behaviorStats.get("completedNodes") != null
                    ? (int) behaviorStats.get("completedNodes") : 0;
            int consecutiveDays = behaviorStats.get("consecutiveDays") != null
                    ? (int) behaviorStats.get("consecutiveDays") : 0;
            List<Map<String, Object>> dailyDuration = behaviorStats.get("dailyDuration") != null
                    ? (List<Map<String, Object>>) behaviorStats.get("dailyDuration") : List.of();

            result.put("todayMinutes", todayMinutes);
            result.put("todayStudyHours", Math.round(todayMinutes / 60.0 * 10.0) / 10.0);
            result.put("totalStudyHours", totalStudyHours);
            result.put("totalActions", totalActions);
            result.put("completedNodes", completedNodes);
            result.put("consecutiveDays", consecutiveDays);
            result.put("dailyDuration", dailyDuration);

            // ② 答题统计
            try {
                Map<String, Object> quizStats = quizRecordMapper.selectStats(userId);
                if (quizStats != null) {
                    Object totalObj = quizStats.get("total");
                    Object correctObj = quizStats.get("correct");
                    int total = totalObj != null ? ((Number) totalObj).intValue() : 0;
                    int correct = correctObj != null ? ((Number) correctObj).intValue() : 0;
                    double rate = total > 0 ? Math.round(correct * 1000.0 / total) / 10.0 : 0.0;
                    result.put("quizTotal", total);
                    result.put("quizCorrect", correct);
                    result.put("quizCorrectRate", rate + "%");
                } else {
                    result.put("quizTotal", 0);
                    result.put("quizCorrect", 0);
                    result.put("quizCorrectRate", "0%");
                }
            } catch (Exception e) {
                log.warn("答题统计查询失败: userId={}, error={}", userId, e.getMessage());
                result.put("quizTotal", 0);
                result.put("quizCorrect", 0);
                result.put("quizCorrectRate", "0%");
            }

            // ③ 画像维度数
            try {
                String profileJson = profileTool.loadProfile(userId);
                int dimensionCount = 0;
                if (profileJson != null && !profileJson.equals("{}")) {
                    var json = com.alibaba.fastjson2.JSON.parseObject(profileJson);
                    var dims = json.getJSONObject("dimensions");
                    if (dims != null) {
                        dimensionCount = dims.size();
                    }
                }
                result.put("profileDimensions", dimensionCount);
                result.put("masteredConcepts", dimensionCount);
            } catch (Exception e) {
                log.warn("画像数据提取失败: userId={}, error={}", userId, e.getMessage());
                result.put("profileDimensions", 0);
                result.put("masteredConcepts", 0);
            }

        } catch (Exception e) {
            log.error("学习报告聚合失败: userId={}, error={}", userId, e.getMessage(), e);
            if (!result.containsKey("todayMinutes")) {
                result.put("todayMinutes", 0);
                result.put("totalStudyHours", 0.0);
                result.put("quizCorrectRate", "0%");
                result.put("consecutiveDays", 0);
                result.put("completedNodes", 0);
                result.put("masteredConcepts", 0);
            }
        }

        return result;
    }

    @Override
    public Map<String, Object> getDetailed(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);

        try {
            result.putAll(getSummary(userId));

            // ① 最近行为日志
            result.put("recentLogs", behaviorTool.getRecentLogs(userId, 20));

            // ② 近30天数据
            Map<String, Object> stats30 = behaviorTool.getRecentStats(userId, 30);
            result.put("monthlyStats", stats30);

            // ③ 答题记录
            try {
                result.put("quizRecords", quizRecordMapper.selectByUser(userId, 50));
            } catch (Exception e) {
                result.put("quizRecords", List.of());
            }

        } catch (Exception e) {
            log.error("详细报告生成失败: userId={}, error={}", userId, e.getMessage(), e);
            result.put("message", "数据聚合异常，请稍后再试");
        }

        return result;
    }
}
