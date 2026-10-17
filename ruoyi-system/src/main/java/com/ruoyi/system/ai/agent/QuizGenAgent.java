package com.ruoyi.system.ai.agent;

import com.ruoyi.system.ai.graph.EducationState;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 题库生成 Agent — 生成选择题、填空题、简答题、代码题
 *
 * @author edu-agent
 */
@Component
public class QuizGenAgent implements ResourceGenAgent {

    private static final Logger log = LoggerFactory.getLogger(QuizGenAgent.class);

    @Autowired
    private ChatModel chatModel;  // DeepSeek-V4

    @Autowired
    private com.ruoyi.system.ai.tool.QuizTool quizTool;

    @Override public String agentName() { return "quizGen"; }
    @Override public String resourceType() { return "quiz"; }
    @Override public String displayName() { return "练习题"; }
    @Override public String agentRole() { return "生成多样练习题目（选择/填空/简答/代码），适配学习者薄弱点"; }
    @Override public String[] capabilities() { return new String[]{"题目生成", "难度适配", "答案解析", "薄弱点覆盖"}; }
    @Override public String[] tools() { return new String[]{"ChatModel"}; }

    @Override
    public boolean shouldGenerate(EducationState state) {
        // 练习适用于大多数学习场景
        return true;
    }

    @Override
    public String buildSystemPrompt(EducationState state) {
        String weakPoints = extractWeakPoints(state);
        String level = extractKnowledgeLevel(state);
        return String.format("""
            你是题库设计专家。根据学习者的知识水平和薄弱点出题。

            输出格式（严格JSON数组，不要包含其他文字）:
            [{
              "type": "single_choice",
              "stem": "题干文本",
              "options": ["A. 选项A", "B. 选项B", "C. 选项C", "D. 选项D"],
              "answer": "A",
              "explanation": "详细解析，说明为什么选A以及为什么不选其他选项",
              "difficulty": "easy",
              "knowledgePoints": ["知识点1"]
            }]

            要求:
            - 重点覆盖学习者的薄弱知识点: %s
            - 难度匹配学习者水平: %s
            - 选择题选项要有干扰性（不能一看就知道答案）
            - 解析要详细、有教育意义
            - 题型比例: 选择题:简答题:代码题 = 5:3:2
            """, weakPoints, level);
    }

    @Override
    public String buildUserPrompt(EducationState state) {
        String topic = extractTopic(state);
        return String.format("请为「%s」生成10道练习题。", topic);
    }

    @Override
    public String generate(EducationState state) {
        log.info("QuizGenAgent 开始生成题目...");
        String systemPrompt = buildSystemPrompt(state);
        String userPrompt = buildUserPrompt(state);

        return chatModel.chat(List.of(
                dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                dev.langchain4j.data.message.UserMessage.from(userPrompt)
        )).aiMessage().text();
    }

    @Override
    public String persist(EducationState state, String result) {
        List<Long> savedIds = quizTool.saveQuestionsAndReturnIds(result);
        String ids = savedIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        log.info("QuizGenAgent 题目已持久化: count={}, ids={}", savedIds.size(), ids);
        return "quiz:" + ids;
    }

    @Override
    public double validate(String result) {
        // 验证：必须能解析为JSON数组，且每题有必填字段
        // 容错处理：LLM 返回可能被 ```json...``` 包裹混入多余文本
        try {
            String cleaned = result.trim();
            // 去掉 markdown 代码块包裹
            if (cleaned.startsWith("```")) {
                int start = cleaned.indexOf("\n") + 1;
                int end = cleaned.lastIndexOf("```");
                if (start > 0 && end > start) {
                    cleaned = cleaned.substring(start, end).trim();
                }
            }
            // 提取第一个 [ 到最后一个 ] 之间的 JSON 数组
            int arrStart = cleaned.indexOf('[');
            int arrEnd = cleaned.lastIndexOf(']');
            if (arrStart >= 0 && arrEnd > arrStart) {
                cleaned = cleaned.substring(arrStart, arrEnd + 1);
            }
            var questions = com.alibaba.fastjson2.JSON.parseArray(cleaned);
            if (questions == null || questions.isEmpty()) return 0.0;
            int validCount = 0;
            for (Object q : questions) {
                var qj = (com.alibaba.fastjson2.JSONObject) q;
                if (qj.containsKey("stem") && qj.containsKey("answer")) {
                    validCount++;
                }
            }
            // 至少一半题目合法即通过
            return validCount > 0 ? (double) validCount / questions.size() : 0.0;
        } catch (Exception e) {
            log.warn("QuizGenAgent JSON 验证失败: {}", e.getMessage());
            return 0.0;
        }
    }

    // === 辅助方法 ===
    private String extractTopic(EducationState state) {
        try {
            String entities = state.entitiesJson();
            return com.alibaba.fastjson2.JSON.parseObject(entities).getString("topic");
        } catch (Exception e) { return state.userMessage(); }
    }

    private String extractKnowledgeLevel(EducationState state) {
        String profile = state.profileJson();
        if (profile != null && profile.contains("beginner")) return "入门";
        if (profile != null && profile.contains("advanced")) return "进阶";
        return "中等";
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
