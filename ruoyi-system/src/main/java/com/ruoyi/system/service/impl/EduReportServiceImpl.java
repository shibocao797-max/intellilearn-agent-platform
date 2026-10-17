package com.ruoyi.system.service.impl;

import com.ruoyi.system.ai.tool.BehaviorTool;
import com.ruoyi.system.ai.tool.ProfileTool;
import com.ruoyi.system.mapper.QuizRecordMapper;
import com.ruoyi.system.service.IEduReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学习报告数据聚合。
 *
 * 报告只返回可追溯的行为、测验和画像数据；没有记录时返回明确的空集合，
 * 由前端展示空状态，绝不补造图表数据。
 */
@Service
public class EduReportServiceImpl implements IEduReportService {

    private static final Logger log = LoggerFactory.getLogger(EduReportServiceImpl.class);
    private static final Set<Integer> SUPPORTED_RANGE_DAYS = Set.of(7, 30, 90);

    @Autowired
    private BehaviorTool behaviorTool;

    @Autowired
    private ProfileTool profileTool;

    @Autowired
    private QuizRecordMapper quizRecordMapper;

    @Override
    public Map<String, Object> getSummary(Long userId, int rangeDays) {
        int days = normalizeRangeDays(rangeDays);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rangeDays", days);

        Map<String, Object> rangeStats = behaviorTool.getRecentStats(userId, days);
        Map<String, Object> todayStats = behaviorTool.getRecentStats(userId, 1);
        List<String> sourceErrors = new ArrayList<>();
        boolean behaviorAvailable = isSourceAvailable(rangeStats) && isSourceAvailable(todayStats);
        result.put("behaviorAvailable", behaviorAvailable);
        if (!behaviorAvailable) {
            sourceErrors.add("学习行为");
        }
        result.put("todayActions", asInt(todayStats.get("totalActions")));
        result.put("todayMinutes", asInt(todayStats.get("todayMinutes")));
        result.put("rangeStudyHours", asDouble(rangeStats.get("totalStudyHours")));
        result.put("rangeActions", asInt(rangeStats.get("totalActions")));
        result.put("consecutiveDays", asInt(rangeStats.get("consecutiveDays")));
        result.put("dailyActivity", buildDailyActivity(rangeStats.get("dailyDuration"), days));
        result.put("actionDistribution", normalizeActionDistribution(rangeStats.get("actionDistribution")));

        if (!appendQuizSummary(result, userId, days)) {
            sourceErrors.add("测验");
        }
        appendProfileSummary(result, userId);
        try {
            result.put("quizTrend", quizRecordMapper.selectPerformanceTrend(userId, days));
        } catch (Exception error) {
            log.warn("读取测验趋势失败: userId={}, days={}, error={}", userId, days, error.getMessage());
            result.put("quizAvailable", false);
            result.put("quizTrend", List.of());
            if (!sourceErrors.contains("测验")) {
                sourceErrors.add("测验");
            }
        }
        result.put("sourceErrors", sourceErrors);
        return result;
    }

    @Override
    public Map<String, Object> getDetailed(Long userId, int rangeDays) {
        int days = normalizeRangeDays(rangeDays);
        Map<String, Object> result = new LinkedHashMap<>(getSummary(userId, days));
        List<String> sourceErrors = mutableSourceErrors(result);
        try {
            result.put("recentLogs", behaviorTool.getRecentLogs(userId, days, 20));
            result.put("behaviorActivityAvailable", true);
        } catch (Exception error) {
            log.warn("读取近期学习行为失败: userId={}, error={}", userId, error.getMessage());
            result.put("recentLogs", List.of());
            result.put("behaviorActivityAvailable", false);
            addSourceError(sourceErrors, "近期学习行为");
        }
        try {
            result.put("quizRecords", quizRecordMapper.selectByUserInRange(userId, days, 50));
            result.put("quizActivityAvailable", true);
        } catch (Exception error) {
            log.warn("读取近期测验记录失败: userId={}, error={}", userId, error.getMessage());
            result.put("quizRecords", List.of());
            result.put("quizActivityAvailable", false);
            addSourceError(sourceErrors, "近期测验记录");
        }
        return result;
    }

    private boolean appendQuizSummary(Map<String, Object> result, Long userId, int days) {
        try {
            Map<String, Object> quizStats = quizRecordMapper.selectStatsInRange(userId, days);
            int assessed = asInt(quizStats == null ? null : quizStats.get("objectiveAnsweredCount"));
            int correct = asInt(quizStats == null ? null : quizStats.get("objectiveCorrectCount"));
            int pending = asInt(quizStats == null ? null : quizStats.get("pendingReviewCount"));
            result.put("quizAssessedTotal", assessed);
            result.put("quizCorrect", correct);
            result.put("quizPendingReview", pending);
            result.put("quizCorrectRate", assessed > 0
                    ? Math.round(correct * 1000.0 / assessed) / 10.0
                    : null);
            result.put("quizAvailable", true);
            return true;
        } catch (Exception error) {
            log.warn("读取测验统计失败: userId={}, days={}, error={}", userId, days, error.getMessage());
            result.put("quizAssessedTotal", 0);
            result.put("quizCorrect", 0);
            result.put("quizPendingReview", 0);
            result.put("quizCorrectRate", null);
            result.put("quizAvailable", false);
            return false;
        }
    }

    private void appendProfileSummary(Map<String, Object> result, Long userId) {
        try {
            String profileJson = profileTool.loadProfile(userId);
            int dimensionCount = 0;
            if (profileJson != null && !profileJson.equals("{}")) {
                var json = com.alibaba.fastjson2.JSON.parseObject(profileJson);
                var dimensions = json.getJSONObject("dimensions");
                if (dimensions != null) {
                    dimensionCount = dimensions.size();
                }
            }
            result.put("profileDimensions", dimensionCount);
        } catch (Exception error) {
            log.warn("读取学习画像统计失败: userId={}, error={}", userId, error.getMessage());
            result.put("profileDimensions", 0);
        }
    }

    private List<Map<String, Object>> buildDailyActivity(Object rawValue, int days) {
        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        if (rawValue instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (!(item instanceof Map<?, ?> rawItem)) {
                    continue;
                }
                Object rawDate = rawItem.get("date");
                if (rawDate == null) {
                    continue;
                }
                String date = String.valueOf(rawDate);
                if (date.length() >= 10) {
                    date = date.substring(0, 10);
                }
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("minutes", asDouble(rawItem.get("minutes")));
                value.put("actionCount", asInt(rawItem.get("actionCount")));
                byDate.put(date, value);
            }
        }

        List<Map<String, Object>> daily = new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusDays(days - 1L);
        for (int offset = 0; offset < days; offset++) {
            String date = startDate.plusDays(offset).toString();
            Map<String, Object> values = byDate.getOrDefault(date, Map.of());
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date);
            day.put("minutes", asDouble(values.get("minutes")));
            day.put("actionCount", asInt(values.get("actionCount")));
            daily.add(day);
        }
        return daily;
    }

    private List<Map<String, Object>> normalizeActionDistribution(Object rawValue) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(rawValue instanceof List<?> rawList)) {
            return result;
        }
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawItem)) {
                continue;
            }
            int count = asInt(rawItem.get("cnt"));
            if (count <= 0) {
                continue;
            }
            Map<String, Object> value = new LinkedHashMap<>();
            Object actionType = rawItem.get("actionType");
            value.put("actionType", actionType == null ? "" : String.valueOf(actionType));
            value.put("count", count);
            result.add(value);
        }
        return result;
    }

    private int normalizeRangeDays(int requestedDays) {
        return SUPPORTED_RANGE_DAYS.contains(requestedDays) ? requestedDays : 30;
    }

    private boolean isSourceAvailable(Map<String, Object> stats) {
        return stats != null && !Boolean.FALSE.equals(stats.get("sourceAvailable"));
    }

    private List<String> mutableSourceErrors(Map<String, Object> result) {
        List<String> errors = new ArrayList<>();
        Object rawErrors = result.get("sourceErrors");
        if (rawErrors instanceof List<?> values) {
            for (Object value : values) {
                if (value != null) {
                    addSourceError(errors, String.valueOf(value));
                }
            }
        }
        result.put("sourceErrors", errors);
        return errors;
    }

    private void addSourceError(List<String> sourceErrors, String sourceName) {
        if (!sourceErrors.contains(sourceName)) {
            sourceErrors.add(sourceName);
        }
    }

    private int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
