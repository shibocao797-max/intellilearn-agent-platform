package com.ruoyi.system.ai.tool;

import com.ruoyi.system.domain.LearningBehaviorLog;
import com.ruoyi.system.mapper.LearningBehaviorLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 行为日志工具 — 学习行为记录与聚合
 *
 * 已实现：写入 learning_behavior_log 表 + 聚合查询统计。
 * 为学习报告、用户首页、学习效果评估提供数据源。
 *
 * @author edu-agent
 */
@Component
public class BehaviorTool {

    private static final Logger log = LoggerFactory.getLogger(BehaviorTool.class);

    @Autowired
    private LearningBehaviorLogMapper behaviorLogMapper;

    /** Java 层计算连续天数时查询的日期条数上限 */
    private static final int MAX_CONSECUTIVE_DAYS_QUERY = 60;

    /**
     * 记录一次学习行为 — 写入 learning_behavior_log 表
     *
     * @param userId       用户 ID
     * @param actionType   行为类型（VIEW_RESOURCE / ANSWER_QUIZ / COMPLETE_NODE / CONVERSATION / START_QUIZ / GENERATE_RESOURCE）
     * @param targetType   目标类型（resource / quiz / path_node / agent）
     * @param targetId     目标 ID（可为 null）
     * @param durationSec  持续时间（秒，可为 0）
     */
    public void logBehavior(Long userId, String actionType, String targetType,
                            Long targetId, int durationSec) {
        try {
            LearningBehaviorLog logEntry = new LearningBehaviorLog(
                    userId, actionType, targetType, targetId, null);
            logEntry.setDurationSec(durationSec > 0 ? durationSec : null);
            logEntry.setCreateTime(new Date());
            behaviorLogMapper.insertLog(logEntry);
            log.debug("行为记录: userId={}, action={}, target={}, targetId={}",
                    userId, actionType, targetType, targetId);
        } catch (Exception e) {
            // 行为记录失败不阻塞主流程
            log.warn("行为日志写入失败（不影响主流程）: userId={}, action={}, error={}",
                    userId, actionType, e.getMessage());
        }
    }

    /**
     * 记录一次学习行为（含 sessionId）
     *
     * @param userId       用户 ID
     * @param actionType   行为类型
     * @param targetType   目标类型
     * @param targetId     目标 ID
     * @param sessionId    会话 ID
     * @param durationSec  持续时间（秒）
     */
    public void logBehavior(Long userId, String actionType, String targetType,
                            Long targetId, String sessionId, int durationSec) {
        try {
            LearningBehaviorLog logEntry = new LearningBehaviorLog(
                    userId, actionType, targetType, targetId, sessionId);
            logEntry.setDurationSec(durationSec > 0 ? durationSec : null);
            logEntry.setCreateTime(new Date());
            behaviorLogMapper.insertLog(logEntry);
            log.debug("行为记录: userId={}, action={}, target={}, sessionId={}",
                    userId, actionType, targetType, sessionId);
        } catch (Exception e) {
            log.warn("行为日志写入失败（不影响主流程）: userId={}, action={}, error={}",
                    userId, actionType, e.getMessage());
        }
    }

    /**
     * 获取用户最近 N 天的综合学习行为统计
     *
     * @param userId 用户 ID
     * @param days   统计天数（1=今日, 7=近一周, 30=近一月）
     * @return 统计摘要，包含：
     *   - totalStudyHours: 总学习时长（小时）
     *   - completedNodes:  完成节点数
     *   - quizCorrectRate: 答题正确率（0-100%）
     *   - consecutiveDays: 连续学习天数
     *   - todayMinutes:    今日学习时长（分钟）
     *   - totalActions:    行为总次数
     *   - actionDistribution: 行为类型分布
     *   - dailyDuration:   每日学习时长趋势
     */
    public Map<String, Object> getRecentStats(Long userId, int days) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalStudyHours", 0.0);
        result.put("completedNodes", 0);
        result.put("quizCorrectRate", "0%");
        result.put("consecutiveDays", 0);
        result.put("todayMinutes", 0);
        result.put("totalActions", 0);
        result.put("actionDistribution", List.of());
        result.put("dailyDuration", List.of());

        try {
            // ① 聚合统计（指定天数内）
            Map<String, Object> stats = behaviorLogMapper.selectStatsByDays(userId, days);
            if (stats != null) {
                long totalDurationSec = stats.get("totalDurationSec") != null
                        ? ((Number) stats.get("totalDurationSec")).longValue() : 0L;
                long totalCount = stats.get("totalCount") != null
                        ? ((Number) stats.get("totalCount")).longValue() : 0L;
                long completeNodeCount = stats.get("completeNodeCount") != null
                        ? ((Number) stats.get("completeNodeCount")).longValue() : 0L;
                result.put("totalStudyHours", Math.round(totalDurationSec / 3600.0 * 10.0) / 10.0);
                result.put("completedNodes", (int) completeNodeCount);
                result.put("totalActions", (int) totalCount);
            }

            // ② 今日学习时长（精确到分钟）
            Map<String, Object> todayStats = behaviorLogMapper.selectStatsByDays(userId, 1);
            if (todayStats != null && todayStats.get("totalDurationSec") != null) {
                long todaySec = ((Number) todayStats.get("totalDurationSec")).longValue();
                int todayMinutes = (int) Math.round(todaySec / 60.0);
                result.put("todayMinutes", todayMinutes);
            }

            // ③ 行为类型分布
            List<Map<String, Object>> dist = behaviorLogMapper.selectActionDistribution(userId, days);
            if (dist != null) result.put("actionDistribution", dist);

            // ④ 每日学习时长趋势
            List<Map<String, Object>> daily = behaviorLogMapper.selectDailyDuration(userId, days);
            if (daily != null) result.put("dailyDuration", daily);

            // ⑤ 连续学习天数（Java 层从活跃日期列表计算，避免纯 SQL 复杂性）
            int consecutiveDays = computeConsecutiveDays(userId);
            result.put("consecutiveDays", consecutiveDays);

        } catch (Exception e) {
            log.warn("行为统计聚合失败（返回空结果）: userId={}, error={}", userId, e.getMessage());
        }

        log.debug("行为统计查询: userId={}, days={}, result={}", userId, days, result);
        return result;
    }

    /**
     * Java 层计算连续学习天数 — 从今天往前数，遇到没有活跃记录的日期就停止
     *
     * 算法：获取最近 60 天的活跃日期集合（Set<LocalDate>），
     * 从 today 往前遍历，每遇到一个活跃日期就计数，遇到缺失就 break。
     */
    private int computeConsecutiveDays(Long userId) {
        try {
            List<String> dateStrs = behaviorLogMapper.selectConsecutiveDays(userId);
            if (dateStrs == null || dateStrs.isEmpty()) return 0;

            Set<LocalDate> activeDates = new HashSet<>();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (String ds : dateStrs) {
                try {
                    activeDates.add(LocalDate.parse(ds, fmt));
                } catch (Exception ignore) {
                    // 日期解析失败跳过
                }
            }

            int consecutive = 0;
            LocalDate cursor = LocalDate.now();
            // 从今天开始往前数，最多 60 天
            for (int i = 0; i < MAX_CONSECUTIVE_DAYS_QUERY; i++) {
                if (activeDates.contains(cursor)) {
                    consecutive++;
                    cursor = cursor.minus(1, ChronoUnit.DAYS);
                } else {
                    break;  // 中断，停止计数
                }
            }
            return consecutive;
        } catch (Exception e) {
            log.warn("连续天数计算失败: userId={}, error={}", userId, e.getMessage());
            return 0;
        }
    }

    /**
     * 获取用户最近 N 条行为日志
     *
     * @param userId 用户 ID
     * @param limit  返回条数
     * @return 行为日志列表
     */
    public List<LearningBehaviorLog> getRecentLogs(Long userId, int limit) {
        try {
            return behaviorLogMapper.selectRecentByUser(userId, limit);
        } catch (Exception e) {
            log.warn("行为日志查询失败: userId={}, error={}", userId, e.getMessage());
            return List.of();
        }
    }

}
