package com.ruoyi.system.ai.service;

import com.ruoyi.system.ai.graph.EducationGraphBuilder;
import com.ruoyi.system.ai.graph.EducationState;
import org.bsc.langgraph4j.CompiledGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Agent 编排服务 — 图执行入口
 *
 * 初始化编译后的 StateGraph，对外提供同步/异步调用。
 *
 * @author edu-agent
 */
@Service
public class AgentOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrationService.class);

    @Autowired
    private EducationGraphBuilder graphBuilder;

    /** 编译后的图（线程安全） */
    private CompiledGraph<EducationState> compiledGraph;

    @PostConstruct
    public void init() {
        this.compiledGraph = graphBuilder.build();
        log.info("AgentOrchestrationService 初始化完成，图已编译");
    }

    /**
     * 同步执行图 — 阻塞等待完成，返回最终状态
     *
     * @param userId   用户 ID
     * @param message  用户消息
     * @param sessionId 会话 ID
     * @return 最终的 EducationState
     */
    public EducationState execute(Long userId, String message, String sessionId) {
        Map<String, Object> initData = Map.of(
            EducationState.USER_MESSAGE, message,
            EducationState.USER_ID,      userId,
            EducationState.SESSION_ID,   sessionId
        );

        try {
            // invoke(Map, Config) → Optional<State>
            java.util.Optional<EducationState> result = compiledGraph.invoke(initData,
                    org.bsc.langgraph4j.RunnableConfig.builder().build());
            EducationState finalState = result.orElseThrow(() ->
                    new RuntimeException("图执行未返回最终状态"));
            log.info("图执行完成: intent={}, resources={}",
                    finalState.intent(),
                    finalState.value(EducationState.GENERATED_RESOURCES)
                            .map(r -> ((java.util.List<?>) r).size()).orElse(0));
            return finalState;
        } catch (Exception e) {
            log.error("图执行异常: {}", e.getMessage(), e);
            throw new RuntimeException("AI 处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步执行图 — 返回 CompletableFuture
     */
    public CompletableFuture<EducationState> executeAsync(Long userId, String message, String sessionId) {
        return CompletableFuture.supplyAsync(() -> execute(userId, message, sessionId));
    }

    /**
     * 获取已编译的图（用于 Studio 等外部注册）
     */
    public CompiledGraph<EducationState> getCompiledGraph() {
        return compiledGraph;
    }
}
