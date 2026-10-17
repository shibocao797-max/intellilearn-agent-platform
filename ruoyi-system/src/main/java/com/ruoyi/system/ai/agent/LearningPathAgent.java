package com.ruoyi.system.ai.agent;

import com.ruoyi.system.ai.graph.EducationState;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习路径 Agent — 基于画像、问题、资源和审核结果生成个性化学习路径。
 *
 * @author edu-agent
 */
@Component
public class LearningPathAgent implements EduAgent {

    private static final Logger log = LoggerFactory.getLogger(LearningPathAgent.class);

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private com.ruoyi.system.ai.tool.ResourceTool resourceTool;

    @Value("${edu.ai.learning-path.llm-enabled:true}")
    private boolean llmPathEnabled;

    @Override public String agentName() { return "learningPath"; }
    @Override public String displayName() { return "学习路径Agent"; }
    @Override public String agentRole() { return "把资源组织为阶段化学习路径，给出目标、时长、练习和掌握标准"; }
    @Override public String[] capabilities() { return new String[]{"LLM路径规划", "资源编排", "掌握标准", "个性化推荐"}; }
    @Override public String[] tools() { return new String[]{"ChatModel", "EducationState", "ResourceTool"}; }

    public Map<String, Object> plan(EducationState state,
                                    List<Map<String, Object>> resources,
                                    List<Map<String, Object>> reviews) {
        List<Map<String, Object>> usableResources = filterUsable(resources, reviews);
        List<Map<String, Object>> ordered = orderResources(usableResources);

        Map<String, Object> path = null;
        if (llmPathEnabled) {
            try {
                path = planByLlm(state, ordered, reviews);
            } catch (Exception e) {
                log.warn("LearningPathAgent LLM路径规划失败，使用规则兜底: {}", e.getMessage());
            }
        }
        if (path == null) {
            path = planByRules(state, ordered, reviews);
        }

        String content = renderPathContent(path);
        try {
            Long id = resourceTool.saveResource(
                    extractTopic(state) + " - 个性化学习路径",
                    content,
                    "article",
                    "学习路径,learning_path",
                    state.userId());
            path.put("persistedId", "learning_path:" + id);
        } catch (Exception e) {
            path.put("persistedId", "");
            path.put("persistError", e.getMessage());
        }
        return path;
    }

    private String renderPathContent(Map<String, Object> path) {
        StringBuilder content = new StringBuilder();
        appendField(content, "主题", path.get("topic"));
        appendField(content, "学习目标", path.get("goal"));
        appendField(content, "画像依据", path.get("profileBasis"));
        appendField(content, "预计时长", path.get("totalEstimatedTime"));

        Object stagesValue = path.get("stages");
        if (stagesValue instanceof List<?> stages && !stages.isEmpty()) {
            content.append("\n学习阶段：\n");
            int index = 1;
            for (Object value : stages) {
                if (!(value instanceof Map<?, ?> stage)) continue;
                Object order = stage.get("order");
                Object name = stage.get("stageName");
                content.append(order == null ? index : order).append(". ")
                        .append(name == null ? "学习阶段" + index : name).append('\n');
                appendField(content, "目标", stage.get("objective"));
                appendField(content, "练习", stage.get("practice"));
                appendField(content, "掌握标准", stage.get("masteryCriteria"));
                appendField(content, "推荐理由", stage.get("recommendReason"));
                index++;
            }
        }

        Object rubric = path.get("masteryRubric");
        if (rubric instanceof List<?> items && !items.isEmpty()) {
            content.append("\n掌握标准：");
            content.append(items.stream().map(String::valueOf).filter(item -> !item.isBlank()).reduce((left, right) -> left + "；" + right).orElse(""));
            content.append('\n');
        }
        return content.toString().trim();
    }

    private void appendField(StringBuilder content, String label, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            content.append(label).append('：').append(value).append('\n');
        }
    }

    private Map<String, Object> planByLlm(EducationState state,
                                          List<Map<String, Object>> ordered,
                                          List<Map<String, Object>> reviews) {
        String output = chatModel.chat(List.of(
                dev.langchain4j.data.message.SystemMessage.from(buildLlmSystemPrompt()),
                dev.langchain4j.data.message.UserMessage.from(buildLlmUserPrompt(state, ordered, reviews))
        )).aiMessage().text();
        com.alibaba.fastjson2.JSONObject json = AgentJsonSupport.parseFirstObject(output);
        List<Map<String, Object>> stages = normalizeStages(json.get("stages"), ordered, state);
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("LLM learning path has no valid stages");
        }

        Map<String, Object> path = new LinkedHashMap<>();
        path.put("agentName", agentName());
        path.put("displayName", displayName());
        path.put("topic", firstNonBlank(json.getString("topic"), extractTopic(state)));
        path.put("goal", firstNonBlank(json.getString("goal"), inferGoal(state)));
        path.put("profileBasis", firstNonBlank(json.getString("profileBasis"), profileBasis(state.profileJson())));
        path.put("totalEstimatedTime", firstNonBlank(json.getString("totalEstimatedTime"), estimateTotalTime(ordered)));
        path.put("stages", stages);
        List<String> rubric = AgentJsonSupport.stringList(json.get("masteryRubric"));
        path.put("masteryRubric", rubric.isEmpty() ? defaultMasteryRubric() : rubric);
        Object reviewDependency = json.get("reviewDependency");
        path.put("reviewDependency", reviewDependency != null ? reviewDependency : summarizeReview(reviews));
        path.put("planningMode", "llm");
        path.put("planningRationale", firstNonBlank(json.getString("planningRationale"), ""));
        return path;
    }

    private String buildLlmSystemPrompt() {
        return """
            你是教育多智能体系统中的 LearningPathAgent，负责根据学生画像、审核后的资源和质量反馈动态规划学习路径。
            不要按固定模板机械排序。必须根据资源质量、画像、薄弱点、认知风格、学习目标和资源可用性决定阶段数量和顺序。

            只输出 JSON 对象，不要 Markdown。字段固定：
            {
              "topic": "学习主题",
              "goal": "个性化学习目标",
              "profileBasis": "用到的画像依据",
              "totalEstimatedTime": "总预计时间",
              "planningRationale": "路径设计理由",
              "stages": [{
                "order": 1,
                "stageName": "阶段名称",
                "objective": "阶段目标",
                "resourceRef": {"agentName":"...","resourceType":"...","displayName":"...","persistedId":"...","quality":0.8},
                "estimatedTime": "预计时间",
                "practice": "阶段练习或行动",
                "masteryCriteria": "掌握标准",
                "recommendReason": "为什么这个学生此时学这个"
              }],
              "masteryRubric": ["总掌握标准"],
              "reviewDependency": {"reviewedCount":0,"passedCount":0,"usedOnlyPassedResources":true}
            }
            资源引用只能使用输入资源中的 persistedId，不允许编造。
            """;
    }

    private String buildLlmUserPrompt(EducationState state,
                                      List<Map<String, Object>> ordered,
                                      List<Map<String, Object>> reviews) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("userMessage", state.userMessage());
        input.put("entities", state.entitiesJson());
        input.put("profile", state.profileJson());
        input.put("resources", AgentJsonSupport.compactResources(ordered, 1200));
        input.put("reviews", reviews == null ? List.of() : reviews);
        return "请生成动态个性化学习路径，严格输出 JSON：\n"
                + com.alibaba.fastjson2.JSON.toJSONString(input);
    }

    private Map<String, Object> planByRules(EducationState state,
                                            List<Map<String, Object>> ordered,
                                            List<Map<String, Object>> reviews) {
        Map<String, Object> path = new LinkedHashMap<>();
        path.put("agentName", agentName());
        path.put("displayName", displayName());
        path.put("topic", extractTopic(state));
        path.put("goal", inferGoal(state));
        path.put("profileBasis", profileBasis(state.profileJson()));
        path.put("totalEstimatedTime", estimateTotalTime(ordered));
        path.put("stages", buildStages(state, ordered));
        path.put("masteryRubric", defaultMasteryRubric());
        path.put("reviewDependency", summarizeReview(reviews));
        path.put("planningMode", "rule_fallback");
        return path;
    }

    private List<Map<String, Object>> normalizeStages(Object rawStages,
                                                      List<Map<String, Object>> ordered,
                                                      EducationState state) {
        List<Map<String, Object>> stages = AgentJsonSupport.mapList(rawStages);
        Map<String, Map<String, Object>> resourceById = new HashMap<>();
        for (Map<String, Object> resource : ordered) {
            String id = stringValue(resource.get("persistedId"));
            if (!id.isBlank()) {
                resourceById.put(id, resource);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> stage : stages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("order", numberAsInt(stage.get("order"), index));
            item.put("stageName", firstNonBlank(stringValue(stage.get("stageName")), "学习阶段" + index));
            item.put("objective", firstNonBlank(stringValue(stage.get("objective")), "完成「" + extractTopic(state) + "」相关学习"));
            item.put("resourceRef", normalizeResourceRef(stage.get("resourceRef"), ordered, resourceById, index - 1));
            item.put("estimatedTime", firstNonBlank(stringValue(stage.get("estimatedTime")), "15-20分钟"));
            item.put("practice", firstNonBlank(stringValue(stage.get("practice")), "完成资源后的自我复述"));
            item.put("masteryCriteria", firstNonBlank(stringValue(stage.get("masteryCriteria")), "能用自己的话解释并回答一个追问"));
            item.put("recommendReason", firstNonBlank(stringValue(stage.get("recommendReason")), "根据画像、资源质量和学习顺序推荐"));
            result.add(item);
            index++;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeResourceRef(Object rawRef,
                                                     List<Map<String, Object>> ordered,
                                                     Map<String, Map<String, Object>> resourceById,
                                                     int index) {
        if (rawRef instanceof Map<?, ?> rawMap) {
            Map<String, Object> ref = new LinkedHashMap<>((Map<String, Object>) rawMap);
            String id = stringValue(ref.get("persistedId"));
            if (!id.isBlank() && resourceById.containsKey(id)) {
                return resourceRef(resourceById.get(id));
            }
        }
        if (index >= 0 && index < ordered.size()) {
            return resourceRef(ordered.get(index));
        }
        return Map.of("agentName", "textReply", "resourceType", "text", "displayName", "文本答疑");
    }

    private List<String> defaultMasteryRubric() {
        return List.of(
                "能用自己的话解释核心概念",
                "能完成至少 80% 的练习题并说清错因",
                "能把知识点迁移到一个小案例或真实任务",
                "能根据思维导图复述知识结构");
    }

    private List<Map<String, Object>> filterUsable(List<Map<String, Object>> resources,
                                                   List<Map<String, Object>> reviews) {
        if (resources == null) return List.of();
        Map<String, Boolean> passMap = new LinkedHashMap<>();
        if (reviews != null) {
            for (Map<String, Object> review : reviews) {
                passMap.put(key(review), Boolean.TRUE.equals(review.get("passed")));
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> resource : resources) {
            if (!Boolean.TRUE.equals(resource.get("success"))) continue;
            Boolean passed = passMap.get(key(resource));
            if (passed == null || passed) {
                result.add(resource);
            }
        }
        if (result.isEmpty()) {
            for (Map<String, Object> resource : resources) {
                if (Boolean.TRUE.equals(resource.get("success"))) result.add(resource);
            }
        }
        return result;
    }

    private List<Map<String, Object>> orderResources(List<Map<String, Object>> resources) {
        List<Map<String, Object>> ordered = new ArrayList<>(resources);
        ordered.sort(Comparator.comparingInt(r -> priority(stringValue(r.get("resourceType")))));
        return ordered;
    }

    private List<Map<String, Object>> buildStages(EducationState state, List<Map<String, Object>> ordered) {
        List<Map<String, Object>> stages = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> resource : ordered) {
            String type = stringValue(resource.get("resourceType"));
            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("order", index);
            stage.put("stageName", stageName(type, index));
            stage.put("objective", objective(type, extractTopic(state)));
            stage.put("resourceRef", resourceRef(resource));
            stage.put("estimatedTime", estimatedTime(type));
            stage.put("practice", practice(type));
            stage.put("masteryCriteria", masteryCriteria(type));
            stage.put("recommendReason", recommendReason(state.profileJson(), type));
            stages.add(stage);
            index++;
        }
        if (stages.isEmpty()) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("order", 1);
            fallback.put("stageName", "诊断与答疑");
            fallback.put("objective", "先澄清当前问题，再补充资源");
            fallback.put("resourceRef", Map.of("agentName", "textReply", "resourceType", "text", "displayName", "文本答疑"));
            fallback.put("estimatedTime", "10-15分钟");
            fallback.put("practice", "复述本轮回答中的 3 个关键点");
            fallback.put("masteryCriteria", "能提出一个更具体的后续学习问题");
            fallback.put("recommendReason", "当前可用资源不足，先采用对话式学习");
            stages.add(fallback);
        }
        return stages;
    }

    private Map<String, Object> resourceRef(Map<String, Object> resource) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("agentName", resource.get("agentName"));
        ref.put("resourceType", resource.get("resourceType"));
        ref.put("displayName", resource.get("displayName"));
        ref.put("persistedId", resource.get("persistedId"));
        ref.put("quality", resource.get("quality"));
        return ref;
    }

    private int priority(String type) {
        return switch (type) {
            case "document" -> 10;
            case "mindmap" -> 20;
            case "animation" -> 30;
            case "code" -> 40;
            case "quiz" -> 50;
            case "reading" -> 60;
            default -> 90;
        };
    }

    private String stageName(String type, int index) {
        return switch (type) {
            case "document" -> "概念建立";
            case "mindmap" -> "结构梳理";
            case "animation" -> "动态理解";
            case "code" -> "实操迁移";
            case "quiz" -> "诊断练习";
            case "reading" -> "拓展深化";
            default -> "学习阶段" + index;
        };
    }

    private String objective(String type, String topic) {
        return switch (type) {
            case "document" -> "系统理解「" + topic + "」的核心概念和常见误区";
            case "mindmap" -> "建立「" + topic + "」的知识结构和概念关系";
            case "animation" -> "通过动态过程理解抽象机制";
            case "code" -> "把知识点落到可运行案例和实践任务";
            case "quiz" -> "用练习题暴露薄弱点并完成纠错";
            case "reading" -> "补充权威资料和进阶视角";
            default -> "完成对应资源学习";
        };
    }

    private String estimatedTime(String type) {
        return switch (type) {
            case "document" -> "20-30分钟";
            case "mindmap" -> "10-15分钟";
            case "animation" -> "8-12分钟";
            case "code" -> "30-45分钟";
            case "quiz" -> "20-25分钟";
            case "reading" -> "30-60分钟";
            default -> "15-20分钟";
        };
    }

    private String practice(String type) {
        return switch (type) {
            case "document" -> "整理 5 条关键笔记，并写出 1 个例子";
            case "mindmap" -> "遮住导图后手绘一次知识结构";
            case "animation" -> "暂停动画，解释每个镜头对应的知识点";
            case "code" -> "改动 1 个参数或场景，观察输出变化";
            case "quiz" -> "完成题目并把错题原因归入薄弱点";
            case "reading" -> "按阅读顺序摘录 3 条新观点";
            default -> "完成资源后的自我复述";
        };
    }

    private String masteryCriteria(String type) {
        return switch (type) {
            case "quiz" -> "正确率达到 80% 以上，且能解释错题原因";
            case "code" -> "能独立复现代码，并说明关键设计取舍";
            case "mindmap" -> "能不看资料复述一级和二级结构";
            case "animation" -> "能把动态过程转写成步骤说明";
            default -> "能用自己的话解释并回答一个追问";
        };
    }

    private String recommendReason(String profileJson, String type) {
        if (profileJson != null && (profileJson.contains("视觉") || profileJson.contains("visual"))
                && ("mindmap".equals(type) || "animation".equals(type))) {
            return "匹配视觉型认知偏好";
        }
        if (profileJson != null && profileJson.contains("weakPoints") && "quiz".equals(type)) {
            return "用于针对薄弱点进行练习诊断";
        }
        if ("reading".equals(type)) {
            return "用于补充权威资料和迁移阅读";
        }
        return "按先理解、再结构化、再实践、再检测的学习顺序推荐";
    }

    private String inferGoal(EducationState state) {
        String profile = state.profileJson();
        if (profile != null && profile.contains("learningGoal")) {
            return "围绕画像中的学习目标，解决当前问题：" + state.userMessage();
        }
        return "掌握当前问题相关知识，并形成可练习、可复盘的学习闭环";
    }

    private String profileBasis(String profileJson) {
        if (profileJson == null || profileJson.isBlank() || "{}".equals(profileJson)) {
            return "暂无稳定画像，使用通用学习顺序";
        }
        List<String> basis = new ArrayList<>();
        if (profileJson.contains("knowledgeLevel")) basis.add("知识水平");
        if (profileJson.contains("cognitiveStyle")) basis.add("认知风格");
        if (profileJson.contains("weakPoints")) basis.add("薄弱点");
        if (profileJson.contains("preferredResourceType")) basis.add("资源偏好");
        return basis.isEmpty() ? "画像字段较少，使用当前问题推断" : String.join("、", basis);
    }

    private String estimateTotalTime(List<Map<String, Object>> ordered) {
        int minutes = 0;
        for (Map<String, Object> resource : ordered) {
            String type = stringValue(resource.get("resourceType"));
            minutes += switch (type) {
                case "document" -> 25;
                case "mindmap" -> 12;
                case "animation" -> 10;
                case "code" -> 40;
                case "quiz" -> 25;
                case "reading" -> 45;
                default -> 20;
            };
        }
        if (minutes == 0) minutes = 15;
        return minutes + "分钟";
    }

    private Map<String, Object> summarizeReview(List<Map<String, Object>> reviews) {
        int passed = 0;
        int total = reviews == null ? 0 : reviews.size();
        if (reviews != null) {
            for (Map<String, Object> review : reviews) {
                if (Boolean.TRUE.equals(review.get("passed"))) passed++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reviewedCount", total);
        summary.put("passedCount", passed);
        summary.put("usedOnlyPassedResources", total > 0 && passed > 0);
        return summary;
    }

    private String extractTopic(EducationState state) {
        try {
            String topic = com.alibaba.fastjson2.JSON.parseObject(state.entitiesJson()).getString("topic");
            if (topic != null && !topic.isBlank()) return topic;
        } catch (Exception ignored) {}
        return state.userMessage();
    }

    private String key(Map<String, Object> map) {
        return stringValue(map.get("agentName")) + "|" + stringValue(map.get("persistedId"));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : (second == null ? "" : second);
    }

    private int numberAsInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }
}
