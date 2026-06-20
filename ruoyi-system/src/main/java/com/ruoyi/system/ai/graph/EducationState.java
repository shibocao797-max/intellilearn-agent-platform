package com.ruoyi.system.ai.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 教育场景的共享状态 — LangGraph4j AgentState 子类
 *
 * 所有 Agent (Node) 通过此状态共享数据。
 * 画像 (PROFILE_JSON) 是持续更新的全局上下文，不是前置步骤。
 *
 * @author edu-agent
 */
public class EducationState extends AgentState {

    // ===== 输入通道 =====
    /** 用户当前消息 */
    public static final String USER_MESSAGE   = "user_message";
    /** 用户 ID */
    public static final String USER_ID        = "user_id";
    /** 会话 ID (UUID) */
    public static final String SESSION_ID     = "session_id";

    // ===== 意图分析 =====
    /** 意图类型: RESOURCE_GEN/QUIZ_REQUEST/GENERAL_CHAT */
    public static final String INTENT            = "intent";
    /** 抽取的实体 JSON */
    public static final String ENTITIES          = "entities";
    /** 是否推荐资源 */
    public static final String SHOULD_RECOMMEND  = "should_recommend";
    /** 推荐理由 */
    public static final String RECOMMEND_REASON  = "recommend_reason";

    // ===== 画像（持续更新，所有 Node 共享） =====
    /** 学习画像完整 JSON */
    public static final String PROFILE_JSON = "profile_json";

    // ===== 对话消息（Appender 累积） =====
    /** 对话消息列表 */
    public static final String MESSAGES = "messages";

    // ===== 生成结果（Appender 累积） =====
    /** 生成的资源列表 */
    public static final String GENERATED_RESOURCES = "generated_resources";
    /** 生成状态: IDLE/PENDING/RUNNING/DONE */
    public static final String GENERATION_STATUS   = "generation_status";

    // ===== 质量评估 =====
    /** 各 Agent 质量分 Map */
    public static final String QUALITY_SCORES = "quality_scores";
    /** 评估结果 JSON */
    public static final String EVAL_RESULT    = "eval_result";

    // ===== 控制流 =====
    /** 下一步节点名 */
    public static final String NEXT_NODE   = "next_node";
    /** 错误信息 */
    public static final String ERROR       = "error";
    /** 重试计数 */
    public static final String RETRY_COUNT = "retry_count";

    // ===== 调度官输出（新增） =====
    /** 调度官选中的 Agent 名称列表，如 ["textReply","quizGen","mindmapGen"] */
    public static final String SELECTED_AGENTS = "selected_agents";
    /** 文本回复内容（TextReplyAgent 输出） */
    public static final String TEXT_REPLY = "text_reply";
    /** 进度事件（每个Agent完成时推送） */
    public static final String PROGRESS_EVENT = "progress_event";
    /** 当前阶段标识（前端展示用） */
    public static final String CURRENT_PHASE = "current_phase";

    /**
     * Schema 定义 — 每个字段的合并策略
     * Channel.Default: 新值覆盖旧值
     * Channel.Appender: 新值追加到列表末尾
     */
    @SuppressWarnings("unchecked")
    public static final Map<String, Channel<?>> SCHEMA;

    static {
        SCHEMA = Map.<String, Channel<?>>ofEntries(
            // === 默认值（覆盖）通道 ===
            Map.entry(USER_MESSAGE,        Channels.base(() -> "")),
            Map.entry(USER_ID,             Channels.base(() -> 0L)),
            Map.entry(SESSION_ID,          Channels.base(() -> "")),
            Map.entry(INTENT,              Channels.base(() -> "GENERAL_CHAT")),
            Map.entry(ENTITIES,            Channels.base(() -> "{}")),
            Map.entry(SHOULD_RECOMMEND,    Channels.base(() -> false)),
            Map.entry(RECOMMEND_REASON,    Channels.base(() -> "")),
            Map.entry(PROFILE_JSON,        Channels.base(() -> "{}")),
            Map.entry(GENERATION_STATUS,   Channels.base(() -> "IDLE")),
            Map.entry(QUALITY_SCORES,      Channels.base(() -> Map.of())),
            Map.entry(EVAL_RESULT,         Channels.base(() -> "{}")),
            Map.entry(NEXT_NODE,           Channels.base(() -> "START")),
            Map.entry(ERROR,               Channels.base(() -> "")),
            Map.entry(RETRY_COUNT,         Channels.base(() -> 0)),
            // === 调度官新增字段 ===
            Map.entry(SELECTED_AGENTS,    Channels.base(() -> List.of())),
            Map.entry(TEXT_REPLY,         Channels.base(() -> "")),
            Map.entry(PROGRESS_EVENT,     Channels.base(() -> Map.of())),
            Map.entry(CURRENT_PHASE,      Channels.base(() -> "START")),
            // === 追加通道（累积） ===
            Map.entry(MESSAGES,            Channels.appender(ArrayList::new)),
            Map.entry(GENERATED_RESOURCES, Channels.appender(ArrayList::new))
        );
    }

    /**
     * 构造器
     */
    public EducationState(Map<String, Object> initData) {
        super(initData);
    }

    // ===== 类型安全访问器 =====

    /** 用户消息 */
    public String userMessage() {
        return this.<String>value(USER_MESSAGE).orElse("");
    }

    /** 用户 ID */
    public Long userId() {
        return this.<Long>value(USER_ID).orElse(0L);
    }

    /** 会话 ID */
    public String sessionId() {
        return this.<String>value(SESSION_ID).orElse("");
    }

    /** 意图类型 */
    public String intent() {
        return this.<String>value(INTENT).orElse("GENERAL_CHAT");
    }

    /** 实体 JSON */
    public String entitiesJson() {
        return this.<String>value(ENTITIES).orElse("{}");
    }

    /** 推荐理由 */
    public String recommendReason() {
        return this.<String>value(RECOMMEND_REASON).orElse("");
    }

    /** 学习画像 JSON */
    public String profileJson() {
        return this.<String>value(PROFILE_JSON).orElse("{}");
    }

    /** 调度官选中的 Agent 列表 */
    @SuppressWarnings("unchecked")
    public List<String> selectedAgents() {
        return this.<List<String>>value(SELECTED_AGENTS).orElse(List.of());
    }

    /** 文本回复内容 */
    public String textReply() {
        return this.<String>value(TEXT_REPLY).orElse("");
    }

    /** 当前阶段标识 */
    public String currentPhase() {
        return this.<String>value(CURRENT_PHASE).orElse("START");
    }
}
