package com.ruoyi.system.ai.service;

import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.system.domain.ConversationHistory;
import com.ruoyi.system.mapper.ConversationHistoryMapper;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 对话历史 Service — Redis 短期记忆 + MySQL 全量持久化
 *
 * 两层架构:
 * - Redis: 最近 20 条消息，TTL 1 天，用于 LLM 上下文注入
 * - MySQL: 全量永久存储，用于历史回看、画像抽取、数据分析
 *
 * 隔离策略: 所有查询带 user_id 条件，session_id 不跨用户暴露。
 *
 * @author edu-agent
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    /** Redis 短期记忆条数上限（超过后截断为最近 N 条） */
    private static final int REDIS_MAX_MSG = 20;

    /** Redis Key 前缀: "chat:{userId}:{sessionId}" */
    private static final String REDIS_KEY_PREFIX = "chat:";

    /** Redis TTL: 1 天自动过期，单位秒 */
    private static final int REDIS_TTL_SECONDS = 86400;

    @Autowired
    private ConversationHistoryMapper historyMapper;

    @Autowired
    private RedisCache redisCache;

    /**
     * 保存一条消息 — 双写 MySQL（全量）+ Redis（短期记忆）
     *
     * @param userId     用户 ID
     * @param sessionId  会话 ID
     * @param role       角色: user / assistant / system
     * @param content    消息内容
     * @param intentType 意图类型（可为 null）
     */
    public void saveMessage(Long userId, String sessionId, String role,
                            String content, String intentType) {
        saveMessage(userId, sessionId, role, content, intentType, null);
    }

    /**
     * 保存一条消息（含资源数据）— assistant 消息时将生成的资源列表序列化到 metadata_json，
     * 供历史会话回看时恢复资源卡片。
     *
     * @param resources 生成的资源列表（仅 assistant 角色需要，user/system 传 null）
     */
    public void saveMessage(Long userId, String sessionId, String role,
                            String content, String intentType,
                            List<Map<String, Object>> resources) {
        // ① MySQL 全量持久化
        ConversationHistory ch = new ConversationHistory();
        ch.setUserId(userId);
        ch.setSessionId(sessionId);
        ch.setRole(role);
        ch.setContent(content);
        ch.setIntentType(intentType);

        // 将资源数据序列化到 metadata_json（历史会话回看时恢复资源卡片）
        if (resources != null && !resources.isEmpty()) {
            try {
                ch.setMetadataJson(JSON.toJSONString(resources));
            } catch (Exception e) {
                log.warn("资源数据序列化失败（不阻塞主流程）: {}", e.getMessage());
            }
        }

        ch.setCreateTime(new Date());
        historyMapper.insertHistory(ch);

        // ② Redis 短期记忆（滚动窗口，保留最近 20 条）
        String redisKey = REDIS_KEY_PREFIX + userId + ":" + sessionId;
        try {
            // 构造消息条目
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", role);
            msg.put("content", content);
            msg.put("timestamp", LocalDateTime.now().toString());

            // 从 Redis 读取已有消息列表，追加新消息
            String existingJson = redisCache.getCacheObject(redisKey);
            List<Object> messages;
            if (existingJson != null && !existingJson.isEmpty()) {
                messages = new ArrayList<>(JSON.parseArray(existingJson));
            } else {
                messages = new ArrayList<>();
            }
            messages.add(msg);

            // 保留最近 20 条
            if (messages.size() > REDIS_MAX_MSG) {
                messages = messages.subList(messages.size() - REDIS_MAX_MSG, messages.size());
            }

            // 写入 Redis 并设置过期
            redisCache.setCacheObject(redisKey, JSON.toJSONString(messages),
                    REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Redis 写入失败不阻塞主流程（MySQL 已持久化）
            log.warn("Redis 短期记忆写入失败（不影响主流程）: key={}, error={}",
                    redisKey, e.getMessage());
        }
    }

    /**
     * 获取指定会话的完整消息列表（从 MySQL 全量查询）
     *
     * @param sessionId 会话 ID
     * @return 消息列表（含 resources 字段，从 metadata_json 反序列化）
     */
    public List<Map<String, Object>> getMessages(Long userId, String sessionId) {
        List<ConversationHistory> list = historyMapper.selectBySession(userId, sessionId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ConversationHistory ch : list) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("userId", ch.getUserId());
            msg.put("sessionId", ch.getSessionId());
            msg.put("role", ch.getRole());
            msg.put("content", ch.getContent());
            msg.put("intentType", ch.getIntentType());
            msg.put("createTime", ch.getCreateTime() != null ? ch.getCreateTime().toString() : "");

            // 从 metadata_json 反序列化资源列表（历史会话资源卡片恢复）
            String metadataJson = ch.getMetadataJson();
            if (metadataJson != null && !metadataJson.isEmpty()) {
                try {
                    msg.put("resources", JSON.parseArray(metadataJson));
                } catch (Exception e) {
                    msg.put("resources", List.of());
                }
            } else {
                msg.put("resources", List.of());
            }

            result.add(msg);
        }
        return result;
    }

    /**
     * 获取指定用户最近 N 轮的对话摘要文本（供画像抽取使用）
     *
     * 从 MySQL 查询最近消息，截取前 200 字符防止 prompt 过长。
     */
    public String getRecentContext(Long userId, int rounds) {
        List<ConversationHistory> list = historyMapper.selectRecentByUser(userId, rounds * 2);
        List<String> lines = new ArrayList<>();
        // selectRecentByUser 按时间倒序，需要反转以获得时间升序
        Collections.reverse(list);
        for (ConversationHistory ch : list) {
            String content = ch.getContent();
            if (content != null && content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            lines.add(ch.getRole() + ": " + content);
        }
        return String.join("\n", lines);
    }

    /**
     * 从 Redis 获取指定会话的最近 N 条消息（格式化文本，供 Agent prompt 注入）
     *
     * 用于 TextReplyAgent 等需要多轮上下文的场景。
     * 从当前 session 的 Redis 短期记忆中读取，不跨 session 混杂。
     */
    public String getRecentMessages(Long userId, String sessionId, int count) {
        String redisKey = REDIS_KEY_PREFIX + userId + ":" + sessionId;
        try {
            String json = redisCache.getCacheObject(redisKey);
            if (json == null || json.isEmpty()) {
                return "";
            }
            var messages = JSON.parseArray(json);
            if (messages == null || messages.isEmpty()) {
                return "";
            }
            // 取最近 count 条
            int start = Math.max(0, messages.size() - count);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < messages.size(); i++) {
                var msg = (com.alibaba.fastjson2.JSONObject) messages.get(i);
                sb.append(msg.getString("role"))
                  .append(": ")
                  .append(msg.getString("content"))
                  .append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("读取 Redis 短期记忆失败: key={}, error={}", redisKey, e.getMessage());
            return "";
        }
    }

    /**
     * 获取用户跨所有会话的最近消息摘要（供新会话/TextReplyAgent 注入长期记忆）
     *
     * 与 getRecentContext() 的区别：
     * - getRecentContext() 用于画像抽取，消息截断 200 字符
     * - 本方法用于 TextReplyAgent，保留较完整内容，帮助 AI 在新会话中"记住"之前的学习主题
     *
     * @param userId       用户 ID
     * @param maxMessages  最大消息条数（建议 ≤ 10）
     * @return 格式化文本摘要，无记录时返回空字符串
     */
    public String getRecentCrossSessionContext(Long userId, int maxMessages) {
        List<ConversationHistory> list = historyMapper.selectRecentByUser(userId, maxMessages);
        if (list == null || list.isEmpty()) return "";
        // 按时间升序排列
        java.util.Collections.reverse(list);
        StringBuilder sb = new StringBuilder();
        sb.append("该学生近期学习动态（跨所有会话）：\n");
        for (ConversationHistory ch : list) {
            // 只取 user 消息（代表提问主题），避免 AI 回复过长
            if (!"user".equals(ch.getRole())) continue;
            String content = ch.getContent();
            if (content == null || content.isBlank()) continue;
            // 保留前 150 字符作为主题摘要
            if (content.length() > 150) {
                content = content.substring(0, 150) + "...";
            }
            sb.append("- ").append(content).append("\n");
        }
        return sb.toString();
    }

    /**
     * 生成新的会话 ID（8 位 UUID）
     */
    public String newSessionId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // ===== 兼容旧方法（保留签名，内部委托给新方法） =====

    /**
     * @deprecated 使用 getMessages(Long userId, String sessionId) 替代
     */
    @Deprecated
    public List<Map<String, Object>> getMessages(String sessionId) {
        // 兼容旧代码：sessionId 不在内存中就无法查询，
        // 旧代码中此方法已极少被直接调用（Controller 通过 userId 调用新签名）
        log.warn("调用了已过时的 getMessages(sessionId)，返回空列表。请使用 getMessages(userId, sessionId)");
        return List.of();
    }
}
