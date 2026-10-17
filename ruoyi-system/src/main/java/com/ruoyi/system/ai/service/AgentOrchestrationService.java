package com.ruoyi.system.ai.service;

import com.ruoyi.system.ai.graph.AgentProgressCallback;
import com.ruoyi.system.ai.graph.EducationGraphBuilder;
import com.ruoyi.system.ai.graph.EducationState;
import jakarta.annotation.PostConstruct;
import org.bsc.langgraph4j.CompiledGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class AgentOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrationService.class);

    @Autowired
    private EducationGraphBuilder graphBuilder;

    @Autowired
    @Qualifier("eduAiTaskExecutor")
    private TaskExecutor taskExecutor;

    /**
     * 保留已编译图，供只读场景复用。
     * 实际执行统一走 executeWithProgress，避免同步接口和 SSE 链路分叉。
     */
    private CompiledGraph<EducationState> compiledGraph;

    @PostConstruct
    public void init() {
        this.compiledGraph = graphBuilder.build();
        log.info("AgentOrchestrationService 初始化完成，图已编译");
    }

    public EducationState execute(Long userId, String message, String sessionId) {
        return execute(userId, message, sessionId, "");
    }

    public EducationState execute(Long userId, String message, String sessionId, String attachmentContext) {
        try {
            EducationState finalState = graphBuilder.executeWithProgress(
                    userId, message, sessionId, attachmentContext, SilentProgressCallback.INSTANCE);
            if (finalState == null) {
                throw new RuntimeException("图执行未返回最终状态");
            }
            log.info("图执行完成: intent={}, resources={}",
                    finalState.intent(),
                    finalState.value(EducationState.GENERATED_RESOURCES)
                            .map(r -> ((List<?>) r).size())
                            .orElse(0));
            return finalState;
        } catch (Exception e) {
            log.error("图执行异常: {}", e.getMessage(), e);
            throw new RuntimeException("AI 处理失败: " + e.getMessage(), e);
        }
    }

    public CompletableFuture<EducationState> executeAsync(Long userId, String message, String sessionId) {
        return CompletableFuture.supplyAsync(() -> execute(userId, message, sessionId), taskExecutor);
    }

    public CompiledGraph<EducationState> getCompiledGraph() {
        return compiledGraph;
    }

    private static final class SilentProgressCallback implements AgentProgressCallback {
        private static final SilentProgressCallback INSTANCE = new SilentProgressCallback();

        @Override public void onPhase(String phase) {}
        @Override public void onIntent(String intent, String reason) {}
        @Override public void onSelectedAgents(List<String> agentNames) {}
        @Override public void onAgentProgress(String agentName, String displayName, String status, Long durationMs) {}
        @Override public void onResource(Map<String, Object> resourceData) {}
        @Override public void onAgentMessage(Map<String, Object> messageData) {}
        @Override public void onReview(Map<String, Object> reviewData) {}
        @Override public void onRevisionStart(Map<String, Object> revisionData) {}
        @Override public void onRevisionResource(Map<String, Object> resourceData) {}
        @Override public void onRevisionDone(Map<String, Object> revisionSummary) {}
        @Override public void onLearningPath(Map<String, Object> pathData) {}
        @Override public void onResourcePlan(Map<String, Object> planData) {}
        @Override public void onFinalRecommendations(Map<String, Object> recommendationData) {}
        @Override public void onToken(String token) {}
        @Override public void onTokenDone() {}
        @Override public void onDone() {}
        @Override public void onError(String msg) {}
        @Override public boolean isDisconnected() { return false; }
    }
}
