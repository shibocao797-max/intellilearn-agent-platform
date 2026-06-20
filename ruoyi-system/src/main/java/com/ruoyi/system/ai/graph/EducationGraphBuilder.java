package com.ruoyi.system.ai.graph;

import com.ruoyi.system.ai.model.DispatchResult;
import com.ruoyi.system.ai.model.GeneratedResource;
import com.ruoyi.system.ai.agent.ResourceGenAgent;
import com.ruoyi.system.ai.agent.ResourceAgentRegistry;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * 教育多智能体 StateGraph 构建器（改造版）
 *
 * 流程: START → profileUpdate → intentAnalysis → dispatchAgents → resultCollect → END
 *
 * 核心变化:
 * 1. 画像前置 — profileUpdate 作为第一个节点
 * 2. 调度官 — intentAnalysis 输出意图 + 选中的Agent列表
 * 3. 按需并行 — dispatchAgents 只执行选中的Agent
 * 4. 后处理 — resultCollect 汇总结果、记录日志
 *
 * @author edu-agent
 */
@Component
public class EducationGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(EducationGraphBuilder.class);

    // ===== 依赖注入 =====
    @Autowired private ChatModel chatModel;
    @Autowired private ResourceAgentRegistry agentRegistry;
    @Autowired private com.ruoyi.system.ai.tool.ProfileTool profileTool;
    @Autowired private com.ruoyi.system.ai.tool.BehaviorTool behaviorTool;
    @Autowired private com.ruoyi.system.ai.service.ConversationService conversationService;
    @Autowired private com.ruoyi.system.ai.agent.ProfileUpdateAgent profileUpdateAgent;
    @Autowired private com.ruoyi.system.ai.agent.TextReplyAgent textReplyAgent;

    /** 并行线程池 — Agent并行执行的线程数 */
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(6);

    // ===== 图构建 =====

    public CompiledGraph<EducationState> build() {
        log.info("构建 EducationGraph（新架构: 意图分析→条件画像→按需并行）...");
        try {
            StateGraph<EducationState> graph = new StateGraph<>(
                EducationState.SCHEMA, map -> new EducationState(new HashMap<>(map)));

            // 4个节点（新顺序: 意图前置 → 画像可基于意图决定是否更新）
            graph.addNode("intentAnalysis",  toAsync(this::intentAnalysis));
            graph.addNode("profileUpdate",   toAsync(this::profileUpdate));
            graph.addNode("dispatchAgents",  toAsync(this::dispatchAgents));
            graph.addNode("resultCollect",   toAsync(this::resultCollect));

            // 边 — 线性流程（意图分析先于画像更新）
            graph.addEdge(StateGraph.START, "intentAnalysis");
            graph.addEdge("intentAnalysis", "profileUpdate");
            graph.addEdge("profileUpdate", "dispatchAgents");
            graph.addEdge("dispatchAgents", "resultCollect");
            graph.addEdge("resultCollect", StateGraph.END);

            return graph.compile();
        } catch (org.bsc.langgraph4j.GraphStateException e) {
            throw new RuntimeException("状态图构建失败: " + e.getMessage(), e);
        }
    }

    // ===== 节点 2: 画像更新（基于意图分析结果，条件触发） =====

    Map<String, Object> profileUpdate(EducationState s) {
        log.info("② profileUpdate: userId={}, intent={}", s.userId(), s.intent());
        // 传入意图参数，让 ProfileUpdateAgent 根据意图判断是否需要更新
        String updatedProfile = profileUpdateAgent.execute(s, s.intent());
        return Map.of(
            EducationState.PROFILE_JSON, updatedProfile,
            EducationState.CURRENT_PHASE, "PROFILE_UPDATED"
        );
    }

    // ===== 节点 1: 意图分析（调度官） =====

    Map<String, Object> intentAnalysis(EducationState s) {
        log.info("① intentAnalysis（调度官）: 分析意图...");

        // 用最新画像构建调度官 Prompt
        String dispatchPrompt = buildDispatchPrompt(s.userMessage(), s.profileJson());
        String llmOutput = chatModel.chat(dispatchPrompt);

        // 用 DispatchResult 解析（专门为调度官设计的模型）
        DispatchResult dispatch = DispatchResult.fromJson(llmOutput);

        log.info("  调度官决策: intent={}, selectedAgents={}, reason={}",
                dispatch.getIntent(), dispatch.getSelectedAgents(), dispatch.getReason());

        // 映射意图类型
        String intent = dispatch.getIntent();

        return Map.of(
            EducationState.INTENT, intent,
            EducationState.ENTITIES, dispatch.getEntitiesJson(),
            // 新的：调度官选中的Agent列表
            EducationState.SELECTED_AGENTS, dispatch.getSelectedAgents(),
            EducationState.RECOMMEND_REASON, dispatch.getReason(),
            EducationState.CURRENT_PHASE, "INTENT_ANALYZED"
        );
    }

    // ===== 节点 3: 按需并行调度 =====

    @SuppressWarnings("unchecked")
    Map<String, Object> dispatchAgents(EducationState s) {
        List<String> selectedAgentNames = s.selectedAgents();
        log.info("③ dispatchAgents: 选中 {} 个Agent → {}", selectedAgentNames.size(), selectedAgentNames);

        // 线程安全的结果收集
        java.util.Queue<Map<String, Object>> resourceResults = new ConcurrentLinkedQueue<>();
        StringBuilder textReplyContent = new StringBuilder();

        // 为每个选中的Agent创建异步任务
        var futures = selectedAgentNames.stream()
            .map(name -> CompletableFuture.runAsync(() -> {
                try {
                    long start = System.currentTimeMillis();

                    switch (name) {
                        case "textReply" -> {
                            // 文本回复Agent — 生成文本内容
                            String text = textReplyAgent.generate(s);
                            synchronized (textReplyContent) {
                                textReplyContent.append(text);
                            }
                            log.info("  ✓ TextReplyAgent 完成 ({}ms)", System.currentTimeMillis() - start);
                        }
                        default -> {
                            // 资源生成类 Agent (docGen/quizGen/mindmapGen/codeGen)
                            ResourceGenAgent agent = agentRegistry.getAgent(name);
                            if (agent != null) {
                                String gen = agent.generate(s);
                                double quality = agent.validate(gen);
                                String id = agent.persist(s, gen);
                                resourceResults.add(toMap(GeneratedResource.success(
                                    agent.agentName(), agent.resourceType(), agent.displayName(),
                                    id, quality, System.currentTimeMillis() - start, gen)));
                                log.info("  ✓ Agent [{}] 完成, 质量={} ({}ms)",
                                        agent.agentName(), quality, System.currentTimeMillis() - start);
                            } else {
                                log.warn("  ✗ Agent [{}] 未在注册中心找到", name);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("  ✗ Agent [{}] 执行失败: {}", name, e.getMessage());
                }
            }, asyncExecutor))
            .toList();

        // 等待所有Agent完成（最多5分钟）
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("dispatchAgents 超时或异常: {}", e.getMessage());
        }

        log.info("  dispatchAgents 结束: 资源数={}, 文本长度={}",
                resourceResults.size(), textReplyContent.length());

        return Map.of(
            EducationState.GENERATED_RESOURCES, new ArrayList<>(resourceResults),
            EducationState.TEXT_REPLY, textReplyContent.toString(),
            EducationState.GENERATION_STATUS, "DONE",
            EducationState.CURRENT_PHASE, "AGENTS_DONE"
        );
    }

    // ===== 节点 4: 后处理汇总 =====

    // ================================================================
    // SSE 流式编排（新增）— 分步执行 StateGraph 各节点，实时推送进度
    // 与 compile().invoke() 同步模式不同，此方法通过 AgentProgressCallback
    // 在每个 Agent 完成/失败时立即推送事件，实现多智能体协作可视化。
    // ================================================================

    /**
     * 以 SSE 实时进度模式执行完整的 Agent 编排流程
     *
     * @param userId    用户 ID
     * @param message   用户消息
     * @param sessionId 会话 ID
     * @param callback  进度回调（由 Controller 的 ProgressEventEmitter 实现）
     * @return 最终的 EducationState（含 textReply 等完整结果，供 Controller 持久化使用）
     */
    public EducationState executeWithProgress(Long userId, String message, String sessionId,
                                              AgentProgressCallback callback) {
        // === 构建初始状态 ===
        EducationState s0 = new EducationState(new HashMap<>(Map.of(
            EducationState.USER_MESSAGE, message,
            EducationState.USER_ID, userId,
            EducationState.SESSION_ID, sessionId
        )));

        // === 初始化画像（加载已有画像，不触发 LLM 抽取） ===
        // 新流程：先加载已有画像供意图分析使用，画像更新移到意图分析之后
        String existingProfile = profileTool.loadProfile(userId);
        EducationState s1 = applyState(s0, Map.of(
            EducationState.PROFILE_JSON, existingProfile,
            EducationState.CURRENT_PHASE, "PROFILE_LOADED"
        ));

        callback.onPhase("PROFILE_LOADED");
        if (callback.isDisconnected()) return null;

        // === 步骤 1: 意图分析（调度官）—— 先分析意图，再决定是否更新画像 ===
        Map<String, Object> r2 = intentAnalysis(s1);
        EducationState s2 = applyState(s1, r2);

        callback.onIntent(s2.intent(), s2.recommendReason());
        callback.onSelectedAgents(s2.selectedAgents());

        if (callback.isDisconnected()) return null;

        // === 步骤 2: 条件画像更新（基于已识别的意图） ===
        String updatedProfile = profileUpdateAgent.execute(s2, s2.intent());
        EducationState s2b = applyState(s2, Map.of(
            EducationState.PROFILE_JSON, updatedProfile,
            EducationState.CURRENT_PHASE, "PROFILE_UPDATED"
        ));

        callback.onPhase("PROFILE_UPDATED");
        if (callback.isDisconnected()) return null;

        // === 步骤 3: 按需并行调度（实时推送进度） ===
        EducationState s3 = dispatchAgentsWithProgress(s2b, callback);

        if (callback.isDisconnected()) return null;

        // === 步骤 4: 后处理 ===
        Map<String, Object> r4 = resultCollect(s3);
        EducationState s4 = applyState(s3, r4);

        callback.onPhase("DONE");
        callback.onDone();
        return s4;
    }

    /**
     * 将节点返回的 Map 合并到现有 EducationState 中，生成新状态
     *
     * EducationState 是不可变的（每次图节点返回后重建），
     * 此方法模拟 LangGraph 的状态合并逻辑。
     */
    private EducationState applyState(EducationState prev, Map<String, Object> updates) {
        Map<String, Object> merged = new HashMap<>(prev.data());
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            // Appender 通道：追加到现有列表
            if (EducationState.MESSAGES.equals(key) || EducationState.GENERATED_RESOURCES.equals(key)) {
                Object existing = merged.get(key);
                if (existing instanceof List && newValue instanceof List) {
                    List<Object> combined = new ArrayList<>((List<?>) existing);
                    combined.addAll((List<?>) newValue);
                    merged.put(key, combined);
                    continue;
                }
            }
            // 默认通道：覆盖
            merged.put(key, newValue);
        }
        return new EducationState(merged);
    }

    /**
     * 并行调度 Agent 并实时推送进度事件（核心新方法）
     *
     * 与 dispatchAgents() 的关键区别：
     * 1. textReply 使用真正的流式 LLM（StreamingChatModel），token 实时推送
     * 2. 每个 Agent 启动/完成/失败时通过 callback 立即通知前端
     * 3. 资源生成完成时立即推送 resource 事件，无需等待全部完成
     */
    @SuppressWarnings("unchecked")
    private EducationState dispatchAgentsWithProgress(EducationState s, AgentProgressCallback callback) {
        List<String> selectedAgentNames = s.selectedAgents();
        log.info("③ dispatchAgentsWithProgress: 选中 {} 个Agent → {}",
                selectedAgentNames.size(), selectedAgentNames);

        // 线程安全的结果收集
        java.util.Queue<Map<String, Object>> resourceResults = new ConcurrentLinkedQueue<>();
        StringBuilder textReplyContent = new StringBuilder();

        // 为每个选中的Agent创建异步任务
        var futures = selectedAgentNames.stream()
            .map(name -> CompletableFuture.runAsync(() -> {
                long start = System.currentTimeMillis();
                try {
                    switch (name) {
                        case "textReply" -> {
                            // 文本回复Agent — 使用真正的流式 LLM，token 实时推送
                            callback.onAgentProgress("textReply", "文本回复Agent", "running", null);
                            try {
                                // 使用 CountDownLatch 等待流式完成
                                java.util.concurrent.CountDownLatch textLatch =
                                        new java.util.concurrent.CountDownLatch(1);
                                StringBuilder collected = new StringBuilder();

                                textReplyAgent.generateStreaming(s,
                                    token -> {
                                        // 每收到一个 token 立即推送
                                        if (!callback.isDisconnected()) {
                                            callback.onToken(token);
                                        }
                                    },
                                    fullText -> {
                                        // 流式完成
                                        callback.onTokenDone();
                                        synchronized (textReplyContent) {
                                            textReplyContent.append(fullText);
                                        }
                                        textLatch.countDown();
                                    },
                                    error -> {
                                        log.error("TextReplyAgent 流式生成失败: {}", error.getMessage());
                                        callback.onAgentProgress("textReply", "文本回复Agent",
                                                "failed", System.currentTimeMillis() - start);
                                        textLatch.countDown();
                                    }
                                );

                                // 等待流式完成（最长5分钟），同时检查客户端状态
                                boolean done = textLatch.await(5, TimeUnit.MINUTES);
                                if (done) {
                                    callback.onAgentProgress("textReply", "文本回复Agent",
                                            "completed", System.currentTimeMillis() - start);
                                } else {
                                    callback.onAgentProgress("textReply", "文本回复Agent",
                                            "failed", System.currentTimeMillis() - start);
                                }
                            } catch (Exception e) {
                                log.error("TextReplyAgent 异常: {}", e.getMessage());
                                callback.onAgentProgress("textReply", "文本回复Agent",
                                        "failed", System.currentTimeMillis() - start);
                            }
                        }

                        default -> {
                            // 资源生成类 Agent (docGen/quizGen/mindmapGen/codeGen)
                            ResourceGenAgent agent = agentRegistry.getAgent(name);
                            if (agent != null) {
                                String displayName = agent.displayName();
                                callback.onAgentProgress(name, displayName, "running", null);

                                String gen = agent.generate(s);
                                double quality = agent.validate(gen);
                                String id = agent.persist(s, gen);
                                long duration = System.currentTimeMillis() - start;

                                Map<String, Object> resMap = toMap(GeneratedResource.success(
                                    agent.agentName(), agent.resourceType(), agent.displayName(),
                                    id, quality, duration, gen));
                                resourceResults.add(resMap);

                                callback.onAgentProgress(name, displayName, "completed", duration);
                                callback.onResource(resMap);
                                log.info("  ✓ Agent [{}] 完成, 质量={} ({}ms)",
                                        agent.agentName(), quality, duration);
                            } else {
                                log.warn("  ✗ Agent [{}] 未在注册中心找到", name);
                                callback.onAgentProgress(name, name, "failed", null);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("  ✗ Agent [{}] 执行失败: {}", name, e.getMessage());
                    callback.onAgentProgress(name, name, "failed",
                            System.currentTimeMillis() - start);
                }
            }, asyncExecutor))
            .toList();

        // 等待所有Agent完成（最多5分钟）
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("dispatchAgentsWithProgress 超时或异常: {}", e.getMessage());
        }

        // 如果 textReply 流式失败且没有产生任何文本，使用同步回退
        if (textReplyContent.length() == 0 && !callback.isDisconnected()) {
            log.warn("textReply 流式未产出文本，尝试同步回退...");
            try {
                String fallback = textReplyAgent.generate(s);
                if (fallback != null && !fallback.isBlank()) {
                    synchronized (textReplyContent) {
                        textReplyContent.append(fallback);
                    }
                    // 将回退文本切成块推送给前端
                    int chunkSize = 30;
                    for (int i = 0; i < fallback.length(); i += chunkSize) {
                        int end = Math.min(i + chunkSize, fallback.length());
                        callback.onToken(fallback.substring(i, end));
                    }
                    callback.onTokenDone();
                }
            } catch (Exception e) {
                log.error("textReply 同步回退也失败: {}", e.getMessage());
            }
        }

        log.info("  dispatchAgentsWithProgress 结束: 资源数={}, 文本长度={}",
                resourceResults.size(), textReplyContent.length());

        return applyState(s, Map.of(
            EducationState.GENERATED_RESOURCES, new ArrayList<>(resourceResults),
            EducationState.TEXT_REPLY, textReplyContent.toString(),
            EducationState.GENERATION_STATUS, "DONE",
            EducationState.CURRENT_PHASE, "AGENTS_DONE"
        ));
    }

    // ===== 节点 4: 后处理汇总 =====

    Map<String, Object> resultCollect(EducationState s) {
        log.info("④ resultCollect: 汇总结果、记录日志");
        Long uid = s.userId();

        // 记录行为日志（含会话ID）
        behaviorTool.logBehavior(uid, "CONVERSATION", s.intent(), null,
                s.sessionId(), 0);

        // 最终画像更新确认
        String currentProfile = s.profileJson();
        if (currentProfile == null || currentProfile.equals("{}")) {
            // 如果画像仍为空，尝试补充抽取
            try {
                String ext = chatModel.chat(buildFinalProfilePrompt(
                        conversationService.getRecentContext(uid, 10)));
                currentProfile = profileTool.mergeProfile(
                        profileTool.loadProfile(uid), ext);
                profileTool.saveProfile(uid, currentProfile);
            } catch (Exception e) {
                log.warn("后处理画像补充更新失败: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(EducationState.NEXT_NODE, "END");
        result.put(EducationState.PROFILE_JSON, currentProfile);
        result.put(EducationState.CURRENT_PHASE, "DONE");
        return result;
    }

    // ===== Prompts =====

    /**
     * 调度官 Prompt — 结合画像和用户问题，决定启动哪些Agent
     */
    private String buildDispatchPrompt(String userMessage, String profileJson) {
        return String.format("""
            你是一个智能教育调度官。根据用户画像和当前问题，决定启动哪些Agent来处理。

            ## 用户画像
            %s

            ## 可用Agent列表
            - textReply: 文本回复（问答、讲解概念、闲聊）— 几乎所有场景都启动
            - docGen: 文档生成（用户需要系统性学习某个知识点时启动）
            - quizGen: 题库生成（用户需要练习、测试、题目时启动）
            - mindmapGen: 思维导图（用户需要梳理知识结构、或画像显示偏好视觉型学习时启动）
            - codeGen: 代码案例（话题涉及编程、算法时启动）

            ## 选择规则
            - textReply 几乎所有场景都选中（用户总需要一个文本回复）
            - 用户明确要求某种资源时，选中对应的Agent
            - 如果画像显示认知风格为"视觉型"，优先启动 mindmapGen
            - 如果画像显示 weakPoints 不为空，优先启动 quizGen 帮用户练习
            - 如果用户只是闲聊/打招呼，只选 textReply

            ## 输出格式（纯JSON，不要包含```json包裹）
            {
              "intent": "RESOURCE_GEN|QUIZ_REQUEST|GENERAL_CHAT",
              "selectedAgents": ["textReply","quizGen"],
              "entities": {"subject":"学科","topic":"具体知识点"},
              "confidence": 0.9,
              "reason": "选择理由"
            }

            ## 用户问题
            %s
            """, profileJson, userMessage);
    }

    /**
     * 兜底画像提取 Prompt
     */
    private String buildFinalProfilePrompt(String recentContext) {
        return String.format("""
            从对话中增量抽取学习特征。只提取本轮新出现的特征。
            ## 最近活动: %s
            ## 可抽取维度: knowledgeLevel/cognitiveStyle/learningPace/weakPoints/interests/learningGoal/preferredResourceType
            ## 输出JSON: {"dimensions":{"维度":{"value":...,"confidence":0.8,"evidence":"..."}}}
            没有新发现输出{}
            """, recentContext);
    }

    // ===== Helpers =====

    private static <S extends org.bsc.langgraph4j.state.AgentState> AsyncNodeAction<S>
            toAsync(java.util.function.Function<S, Map<String, Object>> fn) {
        return AsyncNodeAction.node_async(s -> {
            try { return fn.apply((S) s); }
            catch (Exception e) {
                log.error("节点执行异常: {}", e.getMessage(), e);
                return Map.of(EducationState.ERROR, e.getMessage());
            }
        });
    }

    private Map<String, Object> toMap(GeneratedResource gr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("agentName", gr.getAgentName());
        m.put("resourceType", gr.getResourceType());
        m.put("displayName", gr.getDisplayName());
        m.put("persistedId", gr.getPersistedId());
        m.put("quality", gr.getQuality());
        m.put("durationMs", gr.getDurationMs());
        m.put("success", gr.isSuccess());
        m.put("error", gr.getError());
        // 截断过长内容，前端卡片仅显示摘要
        if (gr.getContent() != null && !gr.getContent().isBlank()) {
            m.put("content", gr.getContent().length() > 2000
                    ? gr.getContent().substring(0, 2000) + "..." : gr.getContent());
        }
        return m;
    }
}
