package com.ruoyi.system.ai.agent;

import com.ruoyi.system.ai.graph.EducationState;
import com.ruoyi.system.ai.tool.KnowledgeTool;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 拓展阅读 Agent — 生成分层阅读材料、链接和阅读顺序。
 *
 * @author edu-agent
 */
@Component
public class ReadingExpandAgent implements ResourceGenAgent {

    private static final Logger log = LoggerFactory.getLogger(ReadingExpandAgent.class);
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private com.ruoyi.system.ai.tool.ResourceTool resourceTool;

    @Autowired
    private KnowledgeTool knowledgeTool;

    @Override public String agentName() { return "readingExpand"; }
    @Override public String resourceType() { return "reading"; }
    @Override public String displayName() { return "拓展阅读"; }
    @Override public String agentRole() { return "围绕学习主题推荐文章、论文、官方文档和阅读顺序"; }
    @Override public String[] capabilities() { return new String[]{"资料推荐", "阅读路径", "难度分层", "可信来源标注"}; }
    @Override public String[] tools() { return new String[]{"ChatModel", "KnowledgeTool", "ResourceTool"}; }

    @Override
    public boolean shouldGenerate(EducationState state) {
        return !"GENERAL_CHAT".equals(state.intent());
    }

    @Override
    public String buildSystemPrompt(EducationState state) {
        String evidence = knowledgeTool.retrieveForPrompt(state.userMessage() + "\n" + state.entitiesJson(), 5);
        return String.format("""
            你是教育资源策展专家。请根据学生画像，为主题设计拓展阅读清单。

            ## 学生画像
            %s

            ## 可信依据片段
            %s

            ## 输出要求
            使用 Markdown，必须包含以下栏目：
            ### 阅读目标
            ### 阅读顺序
            用 1、2、3 编号说明先读什么、为什么。
            ### 推荐材料
            每条包含：标题、类型（官方文档/教材章节/论文/博客/实践手册）、适合程度、预计时间、链接。
            如果不确定真实链接，必须写“待核验”，不要编造看似真实的 URL。
            ### 知识库依据
            列出推荐顺序参考了哪些知识库片段；如果知识库未命中，明确说明“知识库未命中，外部链接需人工核验”。
            ### 阅读检查点
            ### 延伸问题

            推荐材料要覆盖入门、进阶、实战三个层次，并说明与画像哪一维有关。
            """, state.profileJson(), evidence);
    }

    @Override
    public String buildUserPrompt(EducationState state) {
        return String.format("请为「%s」生成拓展阅读材料和阅读顺序。", extractTopic(state));
    }

    @Override
    public String generate(EducationState state) {
        log.info("ReadingExpandAgent 开始生成拓展阅读...");
        return chatModel.chat(List.of(
                dev.langchain4j.data.message.SystemMessage.from(buildSystemPrompt(state)),
                dev.langchain4j.data.message.UserMessage.from(buildUserPrompt(state))
        )).aiMessage().text();
    }

    @Override
    public String persist(EducationState state, String result) {
        Long resourceId = resourceTool.saveResource(
                extractTopic(state) + " - 拓展阅读",
                result,
                "article",
                "拓展阅读,reading",
                state.userId());
        log.info("ReadingExpandAgent 已持久化: id={}, userId={}", resourceId, state.userId());
        return "reading:" + resourceId;
    }

    @Override
    public double validate(String result) {
        if (result == null || result.isBlank()) return 0.0;
        double score = 0.2;
        if (result.length() >= 500) score += 0.25;
        if (result.contains("阅读顺序")) score += 0.25;
        if (result.contains("适合程度") || result.contains("预计时间")) score += 0.15;
        if (URL_PATTERN.matcher(result).find() || result.contains("待核验")) score += 0.15;
        return Math.min(1.0, score);
    }

    private String extractTopic(EducationState state) {
        try {
            String topic = com.alibaba.fastjson2.JSON.parseObject(state.entitiesJson()).getString("topic");
            if (topic != null && !topic.isBlank()) return topic;
        } catch (Exception ignored) {}
        return state.userMessage();
    }
}
