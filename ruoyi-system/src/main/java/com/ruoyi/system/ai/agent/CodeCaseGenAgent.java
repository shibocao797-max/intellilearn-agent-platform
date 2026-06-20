package com.ruoyi.system.ai.agent;

import com.ruoyi.system.ai.graph.EducationState;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 代码案例生成 Agent — 生成带中文注释的教学代码
 *
 * @author edu-agent
 */
@Component
public class CodeCaseGenAgent implements ResourceGenAgent {

    private static final Logger log = LoggerFactory.getLogger(CodeCaseGenAgent.class);

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private com.ruoyi.system.ai.tool.CodeTool codeTool;

    @Override public String agentName() { return "codeGen"; }
    @Override public String resourceType() { return "code"; }
    @Override public String displayName() { return "代码案例"; }
    @Override public String agentRole() { return "生成带中文注释的教学代码案例，标注易错点和最佳实践"; }
    @Override public String[] capabilities() { return new String[]{"代码生成", "注释教学", "易错点标注", "最佳实践"}; }
    @Override public String[] tools() { return new String[]{"ChatModel"}; }

    @Override
    public boolean shouldGenerate(EducationState state) {
        // 仅当主题与编程相关时生成代码
        String entities = state.entitiesJson();
        return entities.contains("Python") || entities.contains("Java")
                || entities.contains("代码") || entities.contains("编程");
    }

    @Override
    public String buildSystemPrompt(EducationState state) {
        String language = extractLanguage(state);
        String topic = extractTopic(state);
        String difficulty = extractDifficulty(state);
        String weakPoints = extractWeakPoints(state);

        return String.format("""
            你是%s编程教学专家。请为「%s」编写教学代码案例。

            ## 学习者背景
            - 难度等级: %s
            - 薄弱点: %s

            ## 输出要求
            请按以下结构输出，每部分用 Markdown 标题分隔：

            ### 问题描述
            （用1-2句话清晰描述这个代码案例要解决的问题/场景）

            ### 解题思路
            （用3-5个要点说明核心思路，每个要点一行）

            ### 完整代码
            提供带中文注释的完整教学代码：
            - 每行关键代码带中文注释（解释**为什么**这样写，不是翻译语法）
            - 包含完整的入口函数（main 或 if __name__ == '__main__'）
            - 在代码末尾用注释标注预期输出
            - 标注3-5个常见易错点（用 `# 【易错】` 标记在易错位置）
            - 变量命名有意义（不要用 a/b/c）
            - 代码风格符合 %s 社区最佳实践

            ### 关键知识点
            （列出3-5个本案例涉及的核心知识点）

            ### 测试用例
            （提供2-3个测试用例，含输入和预期输出）
            """, language, topic, difficulty, weakPoints, language);
    }

    @Override
    public String buildUserPrompt(EducationState state) {
        String topic = extractTopic(state);
        String language = extractLanguage(state);
        return String.format("请生成关于「%s」的%s教学代码案例。", topic, language);
    }

    @Override
    public String generate(EducationState state) {
        log.info("CodeCaseGenAgent 开始生成代码...");
        String systemPrompt = buildSystemPrompt(state);
        String userPrompt = buildUserPrompt(state);

        return chatModel.chat(List.of(
                dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                dev.langchain4j.data.message.UserMessage.from(userPrompt)
        )).aiMessage().text();
    }

    @Override
    public String persist(EducationState state, String result) {
        String topic = extractTopic(state);
        String language = extractLanguage(state);

        Long resourceId = codeTool.saveCodeExample(topic, language, result, "代码案例", state.userId());
        log.info("CodeCaseGenAgent 代码已持久化: edu_resource.id={}, topic={}, userId={}",
                resourceId, topic, state.userId());
        return "code:" + resourceId;
    }

    // === 辅助方法 ===
    private String extractTopic(EducationState state) {
        try {
            return com.alibaba.fastjson2.JSON.parseObject(state.entitiesJson()).getString("topic");
        } catch (Exception e) { return state.userMessage(); }
    }

    private String extractLanguage(EducationState state) {
        String entities = state.entitiesJson();
        if (entities.contains("Python")) return "Python";
        if (entities.contains("Java")) return "Java";
        if (entities.contains("C++") || entities.contains("Cpp")) return "C++";
        if (entities.contains("JavaScript") || entities.contains("JS")) return "JavaScript";
        return "Python";  // 默认
    }

    private String extractDifficulty(EducationState state) {
        try {
            return com.alibaba.fastjson2.JSON.parseObject(state.entitiesJson()).getString("difficulty");
        } catch (Exception e) { return "medium"; }
    }

    private String extractWeakPoints(EducationState state) {
        String profile = state.profileJson();
        if (profile != null && profile.contains("weakPoints")) {
            try {
                var json = com.alibaba.fastjson2.JSON.parseObject(profile);
                var dims = json.getJSONObject("dimensions");
                if (dims != null) {
                    var wp = dims.getJSONObject("weakPoints");
                    if (wp != null && wp.getJSONArray("value") != null)
                        return wp.getJSONArray("value").toString();
                }
            } catch (Exception ignored) {}
        }
        return "待发现";
    }
}
