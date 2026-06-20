package com.ruoyi.system.ai.agent;

/**
 * 教育智能体统一基接口
 *
 * 所有 Agent（画像、资源生成、路径规划、辅导等）均实现此接口，
 * 便于统一管理和竞赛展示。
 *
 * @author edu-agent
 */
public interface EduAgent {

    /** Agent 唯一标识，如 "textReply"、"docGen" */
    String agentName();

    /** Agent 中文显示名，如 "文本回复Agent"、"文档生成Agent" */
    String displayName();

    /** Agent 角色描述，如 "通过对话抽取学习特征构建动态画像" */
    String agentRole();

    /** Agent 能力列表，如 ["文本问答", "概念讲解", "知识梳理"] */
    String[] capabilities();

    /** Agent 使用的工具列表，如 ["ChatModel", "ProfileTool", "MySqlSaver"] */
    String[] tools();
}
