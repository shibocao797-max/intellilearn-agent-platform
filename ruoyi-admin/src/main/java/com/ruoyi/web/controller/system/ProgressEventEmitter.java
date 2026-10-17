package com.ruoyi.web.controller.system;

import com.ruoyi.system.ai.graph.AgentProgressCallback;
import com.ruoyi.system.ai.support.UserFacingTextSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 线程安全的 SSE 事件发射器 — 封装 SseEmitter，供多 Agent 并发推送事件使用
 *
 * 实现 AgentProgressCallback 接口，所有 on* 方法通过 synchronized(sendLock) 保证线程安全，
 * 捕获 IOException 后设置 clientDisconnected 标志，调用方可据此提前终止。
 *
 * @author edu-agent
 */
public class ProgressEventEmitter implements AgentProgressCallback {

    private static final Logger log = LoggerFactory.getLogger(ProgressEventEmitter.class);

    private final SseEmitter emitter;
    private final Object sendLock = new Object();
    /** 客户端是否已断开 — 由各 send* 方法在捕获 IOException 后设置 */
    private volatile boolean clientDisconnected = false;

    /**
     * 构造器 — 拷贝原 SseEmitter 的 timeout/error 回调
     */
    public ProgressEventEmitter(SseEmitter emitter) {
        this.emitter = emitter;
        // 将原 emitter 的回调转接到本包装器的断开检测
        emitter.onTimeout(() -> {
            this.clientDisconnected = true;
            log.debug("SSE timeout, clientDisconnected=true");
        });
        emitter.onError(ex -> {
            this.clientDisconnected = true;
            log.debug("SSE error, clientDisconnected=true: {}", ex.getMessage());
        });
    }

    // ===== AgentProgressCallback 接口实现（全部线程安全） =====

    @Override
    public void onPhase(String phase) {
        sendEvent("phase", phase);
    }

    @Override
    public void onIntent(String intent, String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("intent", intent);
        data.put("recommendReason", reason);
        sendEvent("intent", data);
    }

    @Override
    public void onSelectedAgents(List<String> agentNames) {
        sendEvent("selectedAgents", agentNames);
    }

    @Override
    public void onAgentProgress(String agentName, String displayName, String status, Long durationMs) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agentName", agentName);
        data.put("displayName", displayName);
        data.put("status", status);
        if (durationMs != null) {
            data.put("durationMs", durationMs);
        }
        sendEvent("agentProgress", data);
    }

    @Override
    public void onResource(Map<String, Object> resourceData) {
        sendEvent("resource", resourceData);
    }

    @Override
    public void onAgentMessage(Map<String, Object> messageData) {
        sendEvent("agentMessage", messageData);
    }

    @Override
    public void onReview(Map<String, Object> reviewData) {
        sendEvent("review", reviewData);
    }

    @Override
    public void onRevisionStart(Map<String, Object> revisionData) {
        sendEvent("revisionStart", revisionData);
    }

    @Override
    public void onRevisionResource(Map<String, Object> resourceData) {
        sendEvent("revisionResource", resourceData);
    }

    @Override
    public void onRevisionDone(Map<String, Object> revisionSummary) {
        sendEvent("revisionDone", revisionSummary);
    }

    @Override
    public void onLearningPath(Map<String, Object> pathData) {
        sendEvent("learningPath", pathData);
    }

    @Override
    public void onResourcePlan(Map<String, Object> planData) {
        sendEvent("resourcePlan", planData);
    }

    @Override
    public void onFinalRecommendations(Map<String, Object> recommendationData) {
        sendEvent("finalRecommendations", recommendationData);
    }

    @Override
    public void onToken(String token) {
        sendEvent("token", UserFacingTextSanitizer.sanitize(token));
    }

    @Override
    public void onTokenDone() {
        sendEvent("tokenDone", "");
    }

    @Override
    public void onDone() {
        sendEvent("done", "complete");
    }

    @Override
    public void onError(String msg) {
        sendEvent("error", msg != null ? msg : "未知错误");
    }

    @Override
    public boolean isDisconnected() {
        // Transport loss only stops writes to this emitter. It must not cancel AI execution.
        return false;
    }

    // ===== 便捷方法 =====

    /** 完成 SSE 流 */
    public void complete() {
        emitter.complete();
    }

    /** 以错误结束 SSE 流 */
    public void completeWithError(Throwable error) {
        emitter.completeWithError(error);
    }

    // ===== 内部实现 =====

    /**
     * 所有事件的底层发送入口，synchronized 保证线程安全
     */
    private void sendEvent(String eventName, Object data) {
        if (clientDisconnected) {
            // 客户端已断开，不再尝试发送，避免重复 IOException
            return;
        }
        synchronized (sendLock) {
            if (clientDisconnected) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(eventName)
                        .data(UserFacingTextSanitizer.sanitizeValue(data)));
            } catch (IOException e) {
                // 客户端断开连接是正常情况（如中途关闭页面），记录 debug 级别日志
                clientDisconnected = true;
                log.debug("SSE 发送事件 [{}] 失败，客户端已断开: {}", eventName, e.getMessage());
            }
        }
    }
}
