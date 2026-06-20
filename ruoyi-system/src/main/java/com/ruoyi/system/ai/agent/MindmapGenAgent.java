package com.ruoyi.system.ai.agent;

import com.ruoyi.system.ai.graph.EducationState;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 思维导图生成 Agent — 生成 Markmap 格式的层级化知识结构
 *
 * @author edu-agent
 */
@Component
public class MindmapGenAgent implements ResourceGenAgent {

    private static final Logger log = LoggerFactory.getLogger(MindmapGenAgent.class);

    @Autowired
    private ChatModel chatModel;  // DeepSeek-V4

    @Autowired
    private com.ruoyi.system.ai.tool.MindmapTool mindmapTool;

    @Override public String agentName() { return "mindmapGen"; }
    @Override public String resourceType() { return "mindmap"; }
    @Override public String displayName() { return "思维导图"; }
    @Override public String agentRole() { return "生成Markmap格式知识结构思维导图，适配视觉型学习者"; }
    @Override public String[] capabilities() { return new String[]{"知识结构化", "概念关联", "层级拆解", "视觉化呈现"}; }
    @Override public String[] tools() { return new String[]{"ChatModel"}; }

    @Override
    public boolean shouldGenerate(EducationState state) {
        // 认知风格为 visual 时生成思维导图
        String profile = state.profileJson();
        return profile != null && profile.contains("\"visual\"");
    }

    @Override
    public String buildSystemPrompt(EducationState state) {
        return """
            你是知识结构梳理专家。将知识点组织为 PlantUML 思维导图格式。

            **严格按照以下格式输出（不要包含任何额外说明文字）：**
            @startmindmap
            * 中心主题
            ** 一级分支（核心概念1）
            *** 二级分支（子概念）
            **** 三级细节（具体知识点）
            *** 二级分支（子概念2）
            ** 一级分支（核心概念2）
            @endmindmap

            规则:
            - 必须用 @startmindmap 开头、@endmindmap 结尾，之间只能有 * 开头的节点
            - 层级用 * 数量表示：* = 根、** = 一级、*** = 二级、**** = 三级（最多4层）
            - 每个节点文字不超过15个字
            - 逻辑关系清晰（上层是下层的概括）
            - 覆盖该主题的全部核心知识点
            """;
    }

    @Override
    public String buildUserPrompt(EducationState state) {
        String topic = extractTopic(state);
        return String.format("请为「%s」生成知识结构思维导图。", topic);
    }

    @Override
    public String generate(EducationState state) {
        log.info("MindmapGenAgent 开始生成思维导图...");
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
        // 从 State 中取出 userId，确保 AI 生成的思维导图归属到当前用户
        Long resourceId = mindmapTool.renderAndSave(topic, result, state.userId());
        log.info("MindmapGenAgent 思维导图已持久化: id={}, topic={}, userId={}",
                resourceId, topic, state.userId());
        return "mindmap:" + resourceId;
    }

    private String extractTopic(EducationState state) {
        try {
            String entities = state.entitiesJson();
            return com.alibaba.fastjson2.JSON.parseObject(entities).getString("topic");
        } catch (Exception e) { return state.userMessage(); }
    }
}
