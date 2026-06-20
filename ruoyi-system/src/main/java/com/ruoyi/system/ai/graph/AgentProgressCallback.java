package com.ruoyi.system.ai.graph;

import java.util.List;
import java.util.Map;

/**
 * Agent 执行进度回调接口 — 定义在 ruoyi-system 模块，避免对 Spring MVC (SseEmitter) 的依赖
 *
 * 由 ruoyi-admin 模块的 ProgressEventEmitter 实现，通过 SseEmitter 将事件推送到前端。
 * Graph 层只依赖此接口，不感知 SSE 传输细节。
 *
 * @author edu-agent
 */
public interface AgentProgressCallback {

    /** 阶段进度 */
    void onPhase(String phase);

    /** 意图识别结果 */
    void onIntent(String intent, String reason);

    /** 调度官选中的 Agent 列表 */
    void onSelectedAgents(List<String> agentNames);

    /** 单个 Agent 的进度更新（开始/完成/失败） */
    void onAgentProgress(String agentName, String displayName, String status, Long durationMs);

    /** 单个生成的资源卡片 */
    void onResource(Map<String, Object> resourceData);

    /** 流式文本 token */
    void onToken(String token);

    /** 文本流结束 */
    void onTokenDone();

    /** 流结束 */
    void onDone();

    /** 错误信息 */
    void onError(String msg);

    /** 是否已断开（客户端断开或超时） */
    boolean isDisconnected();
}
