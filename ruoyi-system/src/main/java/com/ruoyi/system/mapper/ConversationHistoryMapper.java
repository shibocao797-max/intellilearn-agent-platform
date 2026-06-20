package com.ruoyi.system.mapper;

import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.ConversationHistory;

/**
 * 对话历史表 Mapper 接口
 *
 * 支持：单条插入、按会话查询历史、按用户聚合会话列表、软删除会话。
 *
 * @author edu-agent
 */
public interface ConversationHistoryMapper
{
    /**
     * 插入一条对话记录（用于全量持久化）
     *
     * @param history 对话记录
     * @return 插入行数
     */
    public int insertHistory(ConversationHistory history);

    /**
     * 查询指定会话的全部消息（按时间升序）
     *
     * @param userId    用户ID（隔离关键）
     * @param sessionId 会话ID
     * @return 消息列表
     */
    public List<ConversationHistory> selectBySession(Long userId, String sessionId);

    /**
     * 查询用户的所有会话列表（聚合：session_id + 最后活跃时间 + 消息数 + 摘要）
     *
     * @param userId 用户ID
     * @return 会话摘要列表
     */
    public List<Map<String, Object>> selectSessionsByUser(Long userId);

    /**
     * 根据用户 ID 查询最近 N 条消息（供画像抽取等场景使用）
     *
     * @param userId 用户ID
     * @param limit  条数限制
     * @return 消息列表
     */
    public List<ConversationHistory> selectRecentByUser(Long userId, int limit);

    /**
     * 软删除指定会话下的所有消息（标记删除，暂不物理删除）
     *
     * @param userId    用户ID（隔离关键）
     * @param sessionId 会话ID
     * @return 删除行数
     */
    public int softDeleteSession(Long userId, String sessionId);
}
