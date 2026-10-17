package com.ruoyi.system.ai.agent;

import com.ruoyi.system.ai.graph.EducationState;

/**
 * 资源生成 Agent 统一接口
 *
 * 继承 EduAgent 基接口，新增资源生成相关方法。
 * 所有资源生成 Agent 实现此接口。
 * 新增 Agent: 新建一个类实现此接口 + @Component 注解，自动注册到 ResourceAgentRegistry。
 * 零改动现有代码。
 *
 * @author edu-agent
 */
public interface ResourceGenAgent extends EduAgent {

    /** Agent 唯一标识，如 "docGen"、"quizGen" */
    String agentName();

    /** 生成的资源类型，对应 edu_resource.type，如 "document"、"quiz"、"mindmap"、"code" */
    String resourceType();

    /** 资源类型中文显示名，如 "学习文档"、"练习题" */
    String displayName();

    /**
     * 判断本 Agent 是否应当参与本次生成
     * @param state 当前教育状态（含画像、意图、推荐类型）
     */
    boolean shouldGenerate(EducationState state);

    /** 构建 System Prompt */
    String buildSystemPrompt(EducationState state);

    /** 构建 User Prompt */
    String buildUserPrompt(EducationState state);

    /**
     * 执行生成（调用 LLM）
     * @param state 当前教育状态
     * @return 生成结果文本
     */
    String generate(EducationState state);

    /**
     * 根据质量审核反馈进行二次生成。
     *
     * 默认行为是重新生成一次；需要强协作的 Agent 可覆盖此方法，
     * 将 review 中的问题和建议显式纳入修订提示词。
     *
     * @param state 当前教育状态
     * @param previousResult 首轮生成结果
     * @param review 审核 Agent 对首轮结果的反馈
     * @return 修订后的资源内容
     */
    @SuppressWarnings("unchecked")
    default String regenerate(EducationState state, String previousResult, java.util.Map<String, Object> review) {
        java.util.Map<String, Object> merged = new java.util.HashMap<>(state.data());
        String feedback = buildRevisionPrompt(state, previousResult, review);
        merged.put(EducationState.USER_MESSAGE, feedback);
        return generate(new EducationState(merged));
    }

    /**
     * 构建审核打回后的修订提示词。
     *
     * 默认优先消费 QualityReviewAgent 输出的 revisionPrompt；没有该字段时，
     * 使用 issues/suggestions/resourceRef 组装兜底修订指令。
     */
    @SuppressWarnings("unchecked")
    default String buildRevisionPrompt(EducationState state, String previousResult, java.util.Map<String, Object> review) {
        java.util.Map<String, Object> request = latestRevisionRequest(state, review);
        java.util.Map<String, Object> requestPayload = java.util.Map.of();
        if (request != null && request.get("payload") instanceof java.util.Map<?, ?> payload) {
            requestPayload = new java.util.LinkedHashMap<>((java.util.Map<String, Object>) payload);
        }
        Object explicitPrompt = requestPayload.getOrDefault("revisionPrompt",
                review != null ? review.get("revisionPrompt") : "");
        String revisionPrompt = explicitPrompt == null ? "" : String.valueOf(explicitPrompt);
        String feedback = String.format("""
            %s

            你刚收到来自质量审核 Agent 的 REVISION_REQUEST，请基于这条 Agent 消息重写本资源。
            消息ID: %s
            请求ID: %s
            审核重写指令: %s
            审核问题: %s
            修改建议: %s
            资源引用: %s

            上一版资源:
            %s
            """,
                state.userMessage(),
                request != null ? request.get("messageId") : "direct-call",
                requestPayload.getOrDefault("revisionRequestId", ""),
                !revisionPrompt.isBlank() ? revisionPrompt : "请解决审核问题并提升资源质量。",
                requestPayload.getOrDefault("issues", review != null ? review.get("issues") : "无"),
                requestPayload.getOrDefault("suggestions", review != null ? review.get("suggestions") : "无"),
                requestPayload.getOrDefault("resourceRef", java.util.Map.of()),
                previousResult != null && previousResult.length() > 3000
                        ? previousResult.substring(0, 3000) : previousResult);
        return feedback;
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> latestRevisionRequest(EducationState state,
                                                                java.util.Map<String, Object> review) {
        String requestId = revisionRequestId(review);
        java.util.List<java.util.Map<String, Object>> messages = state.agentMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            java.util.Map<String, Object> message = messages.get(i);
            if (!"REVISION_REQUEST".equals(String.valueOf(message.get("type")))) continue;
            if (!agentName().equals(String.valueOf(message.get("toAgent")))) continue;
            Object payloadObj = message.get("payload");
            if (payloadObj instanceof java.util.Map<?, ?> payload) {
                Object id = ((java.util.Map<String, Object>) payload).get("revisionRequestId");
                if (!requestId.equals(String.valueOf(id))) continue;
            }
            return message;
        }
        return null;
    }

    private String revisionRequestId(java.util.Map<String, Object> review) {
        if (review == null) return "rev-" + agentName();
        String key = String.valueOf(review.get("agentName")) + "|" + String.valueOf(review.get("persistedId"));
        return "rev-" + key.replaceAll("[^a-zA-Z0-9_-]", "-");
    }

    /**
     * 持久化生成结果
     * @param state  当前教育状态
     * @param result 生成结果文本
     * @return 持久化后的资源 ID 或 URL
     */
    String persist(EducationState state, String result);

    /**
     * 验证生成结果质量
     * @param result 生成结果文本
     * @return 质量分数 0.0-1.0，默认非空即合格
     */
    default double validate(String result) {
        return (result != null && !result.isBlank()) ? 1.0 : 0.0;
    }
}
