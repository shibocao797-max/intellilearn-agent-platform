package com.ruoyi.system.mapper;

import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.LearningBehaviorLog;

/**
 * 学习行为日志 Mapper 接口
 *
 * @author edu-agent
 */
public interface LearningBehaviorLogMapper {

    /** 插入一条行为记录 */
    int insertLog(LearningBehaviorLog log);

    /**
     * 查询用户最近 N 条行为记录
     *
     * @param userId 用户 ID
     * @param limit  返回条数上限
     */
    List<LearningBehaviorLog> selectRecentByUser(Long userId, int limit);

    /** 查询指定统计范围内的近期行为。 */
    List<LearningBehaviorLog> selectRecentByUserAndDays(Long userId, int days, int limit);

    /**
     * 按行为类型查询用户统计（指定天数内）
     *
     * @param userId 用户 ID
     * @param days   最近天数（如 1=今日, 7=近一周, 30=近一月）
     * @return 聚合统计 Map，含 key: totalCount, uniqueDates 等
     */
    Map<String, Object> selectStatsByDays(Long userId, int days);

    /**
     * 查询用户最近 N 天的各类行为次数分布
     *
     * @param userId 用户 ID
     * @param days   最近天数
     * @return 每行为类型的 count，如 [{actionType:"VIEW_RESOURCE", cnt:15}, ...]
     */
    List<Map<String, Object>> selectActionDistribution(Long userId, int days);

    /**
     * 查询用户最近 N 天的每日学习时长（按日期聚合）
     *
     * @param userId 用户 ID
     * @param days   最近天数
     * @return 每日学习分钟数，如 [{date:"2026-06-05", minutes:85}, ...]
     */
    List<Map<String, Object>> selectDailyDuration(Long userId, int days);

    /**
     * 查询最近活跃日期列表 — 返回日期字符串如 "2026-06-06"
     * 供 Java 层计算连续学习天数（从今天往前，中断即停）
     *
     * @param userId 用户 ID
     * @return 活跃日期集合（已去重，降序排列）
     */
    List<String> selectConsecutiveDays(Long userId);
}
