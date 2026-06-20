package com.ruoyi.system.ai.agent;

import com.ruoyi.system.ai.graph.EducationState;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 文档生成 Agent — 生成结构化 Markdown 教学文档
 *
 * @author edu-agent
 */
@Component
public class DocGenAgent implements ResourceGenAgent {

    private static final Logger log = LoggerFactory.getLogger(DocGenAgent.class);

    @Autowired
    private ChatModel chatModel;  // DeepSeek-V4

    @Autowired
    private com.ruoyi.system.ai.tool.ResourceTool resourceTool;

    @Override public String agentName() { return "docGen"; }
    @Override public String resourceType() { return "document"; }
    @Override public String displayName() { return "学习文档"; }
    @Override public String agentRole() { return "生成个性化Markdown教学文档，适配学习者水平和认知风格"; }
    @Override public String[] capabilities() { return new String[]{"文档生成", "概念讲解", "例题嵌入", "章节梳理"}; }
    @Override public String[] tools() { return new String[]{"ChatModel"}; }

    @Override
    public boolean shouldGenerate(EducationState state) {
        // 文档适用于大多数场景，几乎总是参与生成
        return true;
    }

    @Override
    public String buildSystemPrompt(EducationState state) {
        String subject = extractSubject(state);
        String cogStyle = extractCognitiveStyle(state);
        String weakPoints = extractWeakPoints(state);
        return String.format("""
            你是%s领域的教学文档撰写专家。
            请根据学习者的画像（知识水平、认知风格、薄弱点）撰写个性化教学文档。

            文档结构要求:
            # 标题
            ## 学习目标
            - 目标1
            - 目标2
            ## 核心概念
            ### 概念1
            解释 + 示例
            ### 概念2
            解释 + 示例
            ## 实践应用
            ## 常见误区
            ## 本章小结
            ## 思考题

            风格要求: 适应%s认知风格，重点解释%s等薄弱知识点。
            正文使用中文，每个概念配合具体例子说明。
            """, subject, cogStyle, weakPoints);
    }

    @Override
    public String buildUserPrompt(EducationState state) {
        String topic = extractTopic(state);
        String level = extractKnowledgeLevel(state);
        return String.format("请撰写关于「%s」的教学文档，难度适配%s水平的学习者。", topic, level);
    }

    @Override
    public String generate(EducationState state) {
        log.info("DocGenAgent 开始生成文档...");
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
        // 从 State 中取出 userId，确保 AI 生成的资源归属到当前用户
        Long resourceId = resourceTool.saveDocument(topic, result, "教学文档", state.userId());
        log.info("DocGenAgent 文档已持久化: id={}, topic={}, userId={}, length={}",
                resourceId, topic, state.userId(), result.length());
        return "doc:" + resourceId;
    }

    // === 辅助方法 ===

    private String extractSubject(EducationState state) {
        String entities = state.entitiesJson();
        if (entities.contains("subject")) {
            try {
                return com.alibaba.fastjson2.JSON.parseObject(entities).getString("subject");
            } catch (Exception ignored) {}
        }
        return "计算机科学";
    }

    private String extractTopic(EducationState state) {
        String entities = state.entitiesJson();
        if (entities.contains("topic")) {
            try {
                return com.alibaba.fastjson2.JSON.parseObject(entities).getString("topic");
            } catch (Exception ignored) {}
        }
        return state.userMessage();
    }

    private String extractKnowledgeLevel(EducationState state) {
        String profile = state.profileJson();
        if (profile != null && profile.contains("beginner")) return "入门";
        if (profile != null && profile.contains("advanced")) return "进阶";
        return "中等";
    }

    private String extractCognitiveStyle(EducationState state) {
        String profile = state.profileJson();
        if (profile != null && profile.contains("visual")) return "视觉型";
        if (profile != null && profile.contains("verbal")) return "语言型";
        return "综合型";
    }

    private String extractWeakPoints(EducationState state) {
        String profile = state.profileJson();
        if (profile != null && profile.contains("weakPoints")) {
            try {
                var json = com.alibaba.fastjson2.JSON.parseObject(profile);
                var dims = json.getJSONObject("dimensions");
                if (dims != null) {
                    var wp = dims.getJSONObject("weakPoints");
                    if (wp != null) return wp.getJSONArray("value").toString();
                }
            } catch (Exception ignored) {}
        }
        return "待发现";
    }
}
