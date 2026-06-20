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
