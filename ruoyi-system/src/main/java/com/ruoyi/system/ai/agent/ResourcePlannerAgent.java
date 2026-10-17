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
 * 资源规划 Agent — 对生成资源统一排序、打包和解释推荐理由。
 *
 * @author edu-agent
 */
@Component
public class ResourcePlannerAgent implements EduAgent {

    private static final Logger log = LoggerFactory.getLogger(ResourcePlannerAgent.class);

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private com.ruoyi.system.ai.tool.BehaviorTool behaviorTool;

    @Autowired
    private com.ruoyi.system.ai.tool.ResourceTool resourceTool;

    @Value("${edu.ai.resource-planner.llm-enabled:true}")
    private boolean llmPlannerEnabled;

    @Override public String agentName() { return "resourcePlanner"; }
    @Override public String displayName() { return "资源规划Agent"; }
    @Override public String agentRole() { return "根据画像、审核结果和行为统计统一排序资源，并解释先学什么"; }
    @Override public String[] capabilities() { return new String[]{"LLM资源排序", "推荐解释", "学习包打包", "下一步建议"}; }
    @Override public String[] tools() { return new String[]{"ChatModel", "BehaviorTool", "ResourceTool", "EducationState"}; }

    public Map<String, Object> plan(EducationState state,
                                    List<Map<String, Object>> resources,
                                    List<Map<String, Object>> reviews,
                                    Map<String, Object> learningPath) {
        List<Map<String, Object>> ordered = new ArrayList<>();
        if (resources != null) {
            ordered.addAll(resources);
        }
        ordered.sort(Comparator
                .comparingInt((Map<String, Object> r) -> priority(state.profileJson(), stringValue(r.get("resourceType"))))
                .thenComparing((Map<String, Object> r) -> -numberValue(r.get("quality"), 0.0)));

        Map<String, Object> stats = behaviorTool.getRecentStats(state.userId(), 7);

        Map<String, Object> plan = null;
        if (llmPlannerEnabled) {
            try {
                plan = planByLlm(state, ordered, reviews, learningPath, stats);
            } catch (Exception e) {
                log.warn("ResourcePlannerAgent LLM资源规划失败，使用规则兜底: {}", e.getMessage());
            }
        }
        if (plan == null) {
            plan = planByRules(state, ordered, reviews, learningPath, stats);
        }

        try {
            Long id = resourceTool.saveResource(
                    extractTopic(state) + " - 资源推荐规划",
                    renderPlanContent(plan),
                    "article",
                    "资源规划,resource_plan",
                    state.userId());
            plan.put("persistedId", "resource_plan:" + id);
        } catch (Exception e) {
            plan.put("persistedId", "");
            plan.put("persistError", e.getMessage());
        }
        return plan;
    }

    private String renderPlanContent(Map<String, Object> plan) {
        StringBuilder content = new StringBuilder();
        appendField(content, "主题", plan.get("topic"));
        appendField(content, "推荐策略", plan.get("recommendStrategy"));

        Object packageValue = plan.get("packageSummary");
        if (packageValue instanceof Map<?, ?> packageSummary) {
            Object count = packageSummary.get("resourceCount");
            Object types = packageSummary.get("resourceTypes");
            if (count != null || types != null) {
                content.append("资源包：").append(count == null ? "" : count).append(" 项");
                if (types != null) content.append("，包含 ").append(types);
                content.append('\n');
            }
        }

        Object orderedValue = plan.get("orderedResources");
        if (orderedValue instanceof List<?> ordered && !ordered.isEmpty()) {
            content.append("\n推荐顺序：\n");
            int index = 1;
            for (Object value : ordered) {
                if (!(value instanceof Map<?, ?> item)) continue;
                Object name = item.get("displayName");
                if (name == null || String.valueOf(name).isBlank()) name = item.get("resourceType");
                Object rank = item.get("rank");
                content.append(rank == null ? index : rank).append(". ")
                        .append(name == null ? "学习资源" + index : name);
                Object reason = item.get("reason");
                if (reason != null && !String.valueOf(reason).isBlank()) content.append("：").append(reason);
                content.append('\n');
                Object useWhen = item.get("useWhen");
                if (useWhen != null && !String.valueOf(useWhen).isBlank()) {
                    content.append("使用时机：").append(useWhen).append('\n');
                }
                index++;
            }
        }

        appendField(content, "下一步", plan.get("nextBestAction"));
        return content.toString().trim();
    }

    private void appendField(StringBuilder content, String label, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            content.append(label).append('：').append(value).append('\n');
        }
    }

    private Map<String, Object> planByLlm(EducationState state,
                                          List<Map<String, Object>> ordered,
                                          List<Map<String, Object>> reviews,
                                          Map<String, Object> learningPath,
                                          Map<String, Object> stats) {
        String output = chatModel.chat(List.of(
                dev.langchain4j.data.message.SystemMessage.from(buildLlmSystemPrompt()),
                dev.langchain4j.data.message.UserMessage.from(buildLlmUserPrompt(state, ordered, reviews, learningPath, stats))
        )).aiMessage().text();
        com.alibaba.fastjson2.JSONObject json = AgentJsonSupport.parseFirstObject(output);
        List<Map<String, Object>> orderedResources = normalizeOrderedResources(json.get("orderedResources"), ordered, reviews, state);
        if (orderedResources.isEmpty() && !ordered.isEmpty()) {
            throw new IllegalArgumentException("LLM resource plan has no valid orderedResources");
        }

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("agentName", agentName());
        plan.put("displayName", displayName());
        plan.put("topic", firstNonBlank(json.getString("topic"), extractTopic(state)));
        plan.put("recommendStrategy", firstNonBlank(json.getString("recommendStrategy"), strategy(state.profileJson(), stats)));
        plan.put("behaviorBasis", stats);
        plan.put("orderedResources", orderedResources);
        Object packageSummary = json.get("packageSummary");
        plan.put("packageSummary", packageSummary != null ? packageSummary : packageSummary(ordered, learningPath));
        plan.put("nextBestAction", firstNonBlank(json.getString("nextBestAction"), nextBestAction(ordered)));
        plan.put("planningMode", "llm");
        plan.put("riskControl", firstNonBlank(json.getString("riskControl"), ""));
        return plan;
    }

    private String buildLlmSystemPrompt() {
        return """
            你是教育多智能体系统中的 ResourcePlannerAgent，负责把审核后的资源按学生画像、行为数据和学习路径统一排序推荐。
            你的目标不是按固定类型排序，而是解释“这个学生现在为什么先用这个资源”。

            只输出 JSON 对象，不要 Markdown。字段固定：
            {
              "topic": "主题",
              "recommendStrategy": "个性化推荐策略",
              "orderedResources": [{
                "rank": 1,
                "agentName": "...",
                "resourceType": "...",
                "displayName": "...",
                "persistedId": "...",
                "quality": 0.8,
                "reason": "结合画像/行为/审核结果的推荐理由",
                "useWhen": "使用时机",
                "riskNote": "低质量或 pending 资源的风险提示"
              }],
              "packageSummary": {"resourceCount":0,"resourceTypes":[],"hasLearningPath":true,"packageName":"个性化学习资源包"},
              "nextBestAction": "下一步最应该做什么",
              "riskControl": "如何规避低质量、pending、多模态失败等风险"
            }
            orderedResources 只能引用输入中已有 persistedId，不允许编造资源。
            """;
    }

    private String buildLlmUserPrompt(EducationState state,
                                      List<Map<String, Object>> ordered,
                                      List<Map<String, Object>> reviews,
                                      Map<String, Object> learningPath,
                                      Map<String, Object> stats) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("userMessage", state.userMessage());
        input.put("entities", state.entitiesJson());
        input.put("profile", state.profileJson());
        input.put("behaviorStats", stats);
        input.put("resources", AgentJsonSupport.compactResources(ordered, 1200));
        input.put("reviews", reviews == null ? List.of() : reviews);
        input.put("learningPath", learningPath == null ? Map.of() : learningPath);
        return "请输出资源排序推荐 JSON：\n" + com.alibaba.fastjson2.JSON.toJSONString(input);
    }

    private Map<String, Object> planByRules(EducationState state,
                                            List<Map<String, Object>> ordered,
                                            List<Map<String, Object>> reviews,
                                            Map<String, Object> learningPath,
                                            Map<String, Object> stats) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("agentName", agentName());
        plan.put("displayName", displayName());
        plan.put("topic", extractTopic(state));
        plan.put("recommendStrategy", strategy(state.profileJson(), stats));
        plan.put("behaviorBasis", stats);
        plan.put("orderedResources", buildOrderedResources(state, ordered, reviews));
        plan.put("packageSummary", packageSummary(ordered, learningPath));
        plan.put("nextBestAction", nextBestAction(ordered));
        plan.put("planningMode", "rule_fallback");
        return plan;
    }

    private List<Map<String, Object>> normalizeOrderedResources(Object raw,
                                                                List<Map<String, Object>> ordered,
                                                                List<Map<String, Object>> reviews,
                                                                EducationState state) {
        List<Map<String, Object>> rawItems = AgentJsonSupport.mapList(raw);
        Map<String, Map<String, Object>> resourceById = new HashMap<>();
        for (Map<String, Object> resource : ordered) {
            String id = stringValue(resource.get("persistedId"));
            if (!id.isBlank()) {
                resourceById.put(id, resource);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> rawItem : rawItems) {
            String id = stringValue(rawItem.get("persistedId"));
            Map<String, Object> resource = resourceById.get(id);
            if (resource == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            String type = stringValue(resource.get("resourceType"));
            item.put("rank", numberAsInt(rawItem.get("rank"), rank++));
            item.put("agentName", resource.get("agentName"));
            item.put("resourceType", type);
            item.put("displayName", resource.get("displayName"));
            item.put("persistedId", resource.get("persistedId"));
            item.put("quality", resource.get("quality"));
            item.put("review", findReview(resource, reviews));
            item.put("reason", firstNonBlank(stringValue(rawItem.get("reason")), reason(state.profileJson(), type)));
            item.put("useWhen", firstNonBlank(stringValue(rawItem.get("useWhen")), useWhen(type)));
            item.put("riskNote", firstNonBlank(stringValue(rawItem.get("riskNote")), ""));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> buildOrderedResources(EducationState state,
                                                            List<Map<String, Object>> ordered,
                                                            List<Map<String, Object>> reviews) {
        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> resource : ordered) {
            Map<String, Object> item = new LinkedHashMap<>();
            String type = stringValue(resource.get("resourceType"));
            item.put("rank", rank++);
            item.put("agentName", resource.get("agentName"));
            item.put("resourceType", type);
            item.put("displayName", resource.get("displayName"));
            item.put("persistedId", resource.get("persistedId"));
            item.put("quality", resource.get("quality"));
            item.put("review", findReview(resource, reviews));
            item.put("reason", reason(state.profileJson(), type));
            item.put("useWhen", useWhen(type));
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> packageSummary(List<Map<String, Object>> ordered,
                                               Map<String, Object> learningPath) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("resourceCount", ordered.size());
        summary.put("resourceTypes", ordered.stream()
                .map(r -> stringValue(r.get("resourceType")))
                .filter(s -> !s.isBlank())
                .distinct()
                .toList());
        summary.put("hasLearningPath", learningPath != null && !learningPath.isEmpty());
        summary.put("packageName", "个性化学习资源包");
        return summary;
    }

    private String strategy(String profileJson, Map<String, Object> stats) {
        List<String> strategies = new ArrayList<>();
        if (profileJson != null && (profileJson.contains("视觉") || profileJson.contains("visual"))) {
            strategies.add("视觉资源前置");
        }
        if (profileJson != null && profileJson.contains("weakPoints")) {
            strategies.add("练习诊断强化");
        }
        Object totalActions = stats != null ? stats.get("totalActions") : null;
        if (numberValue(totalActions, 0.0) <= 2) {
            strategies.add("新学习者采用低门槛入门顺序");
        }
        if (strategies.isEmpty()) {
            strategies.add("概念理解优先，实践与检测随后");
        }
        return String.join("；", strategies);
    }

    private String reason(String profileJson, String type) {
        if (("mindmap".equals(type) || "animation".equals(type) || "image".equals(type) || "video".equals(type))
                && profileJson != null && (profileJson.contains("视觉") || profileJson.contains("visual"))) {
            return "画像显示偏视觉理解，优先用结构图、配图、动画或视频降低抽象度";
        }
        if ("quiz".equals(type) && profileJson != null && profileJson.contains("weakPoints")) {
            return "已有薄弱点信号，练习题用于诊断和纠错";
        }
        if ("document".equals(type)) return "先建立完整概念框架";
        if ("code".equals(type)) return "用于把概念迁移到可执行任务";
        if ("image".equals(type)) return "用于把抽象概念转成可观察的视觉线索";
        if ("video".equals(type)) return "用于展示动态过程和课堂演示片段";
        if ("reading".equals(type)) return "用于课后深化和查证权威资料";
        return "补充当前学习任务";
    }

    private String useWhen(String type) {
        return switch (type) {
            case "document" -> "开始学习时";
            case "mindmap" -> "看完文档后复盘结构时";
            case "image" -> "第一次理解抽象概念或需要视觉锚点时";
            case "animation" -> "概念抽象、难以想象过程时";
            case "video" -> "需要动态演示或答辩展示时";
            case "code" -> "需要动手验证和迁移时";
            case "quiz" -> "完成讲解后检测掌握时";
            case "reading" -> "需要拓展或准备答辩说明时";
            default -> "按学习路径对应阶段使用";
        };
    }

    private String nextBestAction(List<Map<String, Object>> ordered) {
        if (ordered.isEmpty()) return "先继续对话澄清学习目标，再生成资源";
        Map<String, Object> first = ordered.get(0);
        return "先学习「" + stringValue(first.get("displayName")) + "」，完成后进入下一阶段";
    }

    private Map<String, Object> findReview(Map<String, Object> resource, List<Map<String, Object>> reviews) {
        if (reviews == null) return Map.of();
        String key = key(resource);
        for (Map<String, Object> review : reviews) {
            if (key.equals(key(review))) return review;
        }
        return Map.of();
    }

    private int priority(String profileJson, String type) {
        if (profileJson != null && (profileJson.contains("视觉") || profileJson.contains("visual"))) {
            if ("mindmap".equals(type)) return 10;
            if ("image".equals(type)) return 15;
            if ("animation".equals(type)) return 20;
            if ("video".equals(type)) return 25;
        }
        return switch (type) {
            case "document" -> 10;
            case "mindmap" -> 20;
            case "image" -> 25;
            case "animation" -> 30;
            case "video" -> 35;
            case "code" -> 40;
            case "quiz" -> 50;
            case "reading" -> 60;
            default -> 90;
        };
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

    private double numberValue(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
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
