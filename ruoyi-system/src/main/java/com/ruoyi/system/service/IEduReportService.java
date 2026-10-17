package com.ruoyi.system.service;

import java.util.Map;

/**
 * 学习报告 Service 接口
 *
 * @author edu-agent
 */
public interface IEduReportService {

    /**
     * 获取学习总览 — 聚合行为日志 + 答题记录 + 路径节点 + 画像
     *
     * @param userId 用户 ID
     * @return 总览数据，包含：
     *   - todayMinutes: 今日学习时长（分钟）
     *   - masteredConcepts: 掌握知识点数（来自画像维度数）
     *   - quizTotal: 总答题次数
     *   - quizCorrectRate: 答题正确率
     *   - consecutiveDays: 连续学习天数
     *   - totalStudyHours: 累计学习时长（小时）
     *   - completedNodes: 完成路径节点数
     *   - profileDimensions: 画像维度数
     *   - dailyDuration: 近7天每日学习时长趋势
     *   - radarData: 六维能力雷达图数据
     *   - recentActions: 最近行为日志列表（前 10 条）
     */
    Map<String, Object> getSummary(Long userId, int rangeDays);

    /**
     * 获取详细学习报告
     *
     * @param userId 用户 ID
     * @return 详细报告数据
     */
    Map<String, Object> getDetailed(Long userId, int rangeDays);
}
