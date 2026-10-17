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
    /** 本轮多智能体协作轨迹 ID */
    public static final String RUN_TRACE_ID   = "run_trace_id";
    /** Server-validated, untrusted attachment excerpts. Never contains storage URLs. */
    public static final String ATTACHMENT_CONTEXT = "attachment_context";

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
    /** 经过审核纠偏后的活跃资源列表，后续学习路径和推荐以此为准 */
    public static final String ACTIVE_RESOURCES = "active_resources";
    /** 生成状态: IDLE/PENDING/RUNNING/DONE */
    public static final String GENERATION_STATUS   = "generation_status";

    // ===== 质量评估 =====
    /** 各 Agent 质量分 Map */
    public static final String QUALITY_SCORES = "quality_scores";
    /** 评估结果 JSON */
    public static final String EVAL_RESULT    = "eval_result";
    /** 审核 Agent 输出的逐资源审核结果 */
    public static final String REVIEW_RESULTS = "review_results";
    /** 审核 Agent 触发的纠偏记录 */
    public static final String QUALITY_CORRECTIONS = "quality_corrections";
    /** 路径 Agent 输出的个性化学习路径 */
    public static final String LEARNING_PATH  = "learning_path";
    /** 资源规划 Agent 输出的推荐顺序与打包策略 */
    public static final String RESOURCE_PLAN  = "resource_plan";
    /** 最终学习建议，面向前端/接口直接展示 */
    public static final String FINAL_RECOMMENDATIONS = "final_recommendations";
    /** Agent 间显式消息总线，记录任务分发、资源提交、审核打回、纠偏响应等交互 */
    public static final String AGENT_MESSAGES = "agent_messages";
    /** 可回放的多 Agent 协作轨迹，由 agent_messages 派生汇总 */
    public static final String COORDINATION_TRACE = "coordination_trace";
    /** Agent 运行状态快照 */
    public static final String AGENT_STATUSES = "agent_statuses";

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
            Map.entry(RUN_TRACE_ID,        Channels.base(() -> "")),
            Map.entry(ATTACHMENT_CONTEXT,  Channels.base(() -> "")),
            Map.entry(INTENT,              Channels.base(() -> "GENERAL_CHAT")),
            Map.entry(ENTITIES,            Channels.base(() -> "{}")),
            Map.entry(SHOULD_RECOMMEND,    Channels.base(() -> false)),
            Map.entry(RECOMMEND_REASON,    Channels.base(() -> "")),
            Map.entry(PROFILE_JSON,        Channels.base(() -> "{}")),
            Map.entry(ACTIVE_RESOURCES,    Channels.base(() -> List.of())),
            Map.entry(GENERATION_STATUS,   Channels.base(() -> "IDLE")),
            Map.entry(QUALITY_SCORES,      Channels.base(() -> Map.of())),
            Map.entry(EVAL_RESULT,         Channels.base(() -> "{}")),
            Map.entry(REVIEW_RESULTS,      Channels.base(() -> List.of())),
            Map.entry(QUALITY_CORRECTIONS, Channels.base(() -> List.of())),
            Map.entry(LEARNING_PATH,       Channels.base(() -> Map.of())),
            Map.entry(RESOURCE_PLAN,       Channels.base(() -> Map.of())),
            Map.entry(FINAL_RECOMMENDATIONS, Channels.base(() -> Map.of())),
            Map.entry(COORDINATION_TRACE,  Channels.base(() -> List.of())),
            Map.entry(AGENT_STATUSES,      Channels.base(() -> Map.of())),
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
            Map.entry(GENERATED_RESOURCES, Channels.appender(ArrayList::new)),
            Map.entry(AGENT_MESSAGES,      Channels.appender(ArrayList::new))
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

    /** 本轮多智能体协作轨迹 ID */
    public String runTraceId() {
        return this.<String>value(RUN_TRACE_ID).orElse("");
    }

    public String attachmentContext() {
        return this.<String>value(ATTACHMENT_CONTEXT).orElse("");
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

    /** 审核结果 */
    public List<Map<String, Object>> reviewResults() {
        return this.<List<Map<String, Object>>>value(REVIEW_RESULTS).orElse(List.of());
    }

    /** 经过审核纠偏后的活跃资源 */
    public List<Map<String, Object>> activeResources() {
        return this.<List<Map<String, Object>>>value(ACTIVE_RESOURCES).orElse(List.of());
    }

    /** 审核纠偏记录 */
    public List<Map<String, Object>> qualityCorrections() {
        return this.<List<Map<String, Object>>>value(QUALITY_CORRECTIONS).orElse(List.of());
    }

    /** 学习路径 */
    public Map<String, Object> learningPath() {
        return this.<Map<String, Object>>value(LEARNING_PATH).orElse(Map.of());
    }

    /** 资源规划 */
    public Map<String, Object> resourcePlan() {
        return this.<Map<String, Object>>value(RESOURCE_PLAN).orElse(Map.of());
    }

    /** 最终学习建议 */
    public Map<String, Object> finalRecommendations() {
        return this.<Map<String, Object>>value(FINAL_RECOMMENDATIONS).orElse(Map.of());
    }

    /** Agent 间显式消息 */
    public List<Map<String, Object>> agentMessages() {
        return this.<List<Map<String, Object>>>value(AGENT_MESSAGES).orElse(List.of());
    }

    /** 多 Agent 协作轨迹 */
    public List<Map<String, Object>> coordinationTrace() {
        return this.<List<Map<String, Object>>>value(COORDINATION_TRACE).orElse(List.of());
    }

    /** Agent 状态 */
    public Map<String, Object> agentStatuses() {
        return this.<Map<String, Object>>value(AGENT_STATUSES).orElse(Map.of());
    }
}
