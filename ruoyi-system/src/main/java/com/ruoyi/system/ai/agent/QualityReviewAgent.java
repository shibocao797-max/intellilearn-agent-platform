package com.ruoyi.system.ai.agent;

import com.ruoyi.system.ai.graph.EducationState;
import com.ruoyi.system.ai.tool.KnowledgeTool;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 质量审核 Agent — 对已生成资源做一致性、完整性和画像匹配审核。
 *
 * @author edu-agent
 */
@Component
public class QualityReviewAgent implements EduAgent {

    private static final Logger log = LoggerFactory.getLogger(QualityReviewAgent.class);

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private KnowledgeTool knowledgeTool;

    @Value("${edu.ai.quality-threshold:${edu.ai.resource.quality-threshold:0.7}}")
    private double qualityThreshold;

    @Value("${edu.ai.review.llm-enabled:true}")
    private boolean llmReviewEnabled;

    @Value("${edu.ai.review.parallelism:3}")
    private int reviewParallelism;

    @Value("${edu.ai.review.timeout-seconds:12}")
    private int reviewTimeoutSeconds;

    @Override public String agentName() { return "qualityReview"; }
    @Override public String displayName() { return "质量审核Agent"; }
    @Override public String agentRole() { return "审核其他Agent输出的准确性、完整性、画像匹配度和可用性"; }
    @Override public String[] capabilities() { return new String[]{"LLM质量评分", "事实一致性检查", "问题定位", "纠偏提示"}; }
    @Override public String[] tools() { return new String[]{"ChatModel", "KnowledgeTool", "EducationState", "GeneratedResource"}; }

    public List<Map<String, Object>> review(EducationState state, List<Map<String, Object>> resources) {
        List<Map<String, Object>> reviews = new ArrayList<>();
        if (resources == null || resources.isEmpty()) {
            return reviews;
        }
        int parallelism = Math.max(1, Math.min(reviewParallelism, resources.size()));
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
            for (Map<String, Object> resource : resources) {
                futures.add(CompletableFuture.supplyAsync(() -> reviewOne(state, resource), executor));
            }
            for (int i = 0; i < futures.size(); i++) {
                Map<String, Object> resource = resources.get(i);
                try {
                    reviews.add(futures.get(i).get(Math.max(5, reviewTimeoutSeconds), TimeUnit.SECONDS));
                } catch (Exception e) {
                    futures.get(i).cancel(true);
                    reviews.add(timeoutFallback(state, resource, e));
                }
            }
        } finally {
            executor.shutdownNow();
        }
        return reviews;
    }

    private Map<String, Object> timeoutFallback(EducationState state, Map<String, Object> resource, Exception e) {
        log.warn("QualityReviewAgent 审核超时或失败，使用规则兜底: agent={}, error={}",
                stringValue(resource.get("agentName")), e.getMessage());
        Map<String, Object> fallback = reviewOneFallback(state, resource);
        @SuppressWarnings("unchecked")
        List<String> issues = (List<String>) fallback.getOrDefault("issues", new ArrayList<String>());
        if (issues instanceof ArrayList<String> mutableIssues) {
            mutableIssues.add("LLM质量审核超时，已切换为规则兜底审核");
        } else {
            List<String> copy = new ArrayList<>(issues);
            copy.add("LLM质量审核超时，已切换为规则兜底审核");
            fallback.put("issues", copy);
        }
        fallback.put("reviewMode", "timeout_rule_fallback");
        fallback.put("evidenceCheck", "LLM审核超时，使用规则兜底；关键事实建议人工复核");
        return fallback;
    }

    private Map<String, Object> reviewOne(EducationState state, Map<String, Object> resource) {
        if (llmReviewEnabled) {
            try {
                return reviewOneByLlm(state, resource);
            } catch (Exception e) {
                log.warn("QualityReviewAgent LLM审核失败，使用规则兜底: agent={}, error={}",
                        stringValue(resource.get("agentName")), e.getMessage());
            }
        }
        return reviewOneFallback(state, resource);
    }

    private Map<String, Object> reviewOneByLlm(EducationState state, Map<String, Object> resource) {
        String output = chatModel.chat(java.util.List.of(
                dev.langchain4j.data.message.SystemMessage.from(buildSystemPrompt()),
                dev.langchain4j.data.message.UserMessage.from(buildUserPrompt(state, resource))
        )).aiMessage().text();
        com.alibaba.fastjson2.JSONObject json = AgentJsonSupport.parseFirstObject(output);

        Map<String, Object> fallback = reviewOneFallback(state, resource);
        Map<String, Object> review = new LinkedHashMap<>();
        String agentName = stringValue(resource.get("agentName"));
        String resourceType = stringValue(resource.get("resourceType"));
        String displayName = stringValue(resource.get("displayName"));
        double score = AgentJsonSupport.clamp(
                AgentJsonSupport.numberValue(json.get("score"), numberValue(fallback.get("score"), 0.0)),
                0.0, 1.0);
        List<String> issues = AgentJsonSupport.stringList(json.get("issues"));
        List<String> suggestions = AgentJsonSupport.stringList(json.get("suggestions"));
        String decision = normalizeDecision(json.getString("decision"), score, issues);
        boolean passed = "PASS".equals(decision) && score >= qualityThreshold && issues.isEmpty();
        if (isAsyncVideoTask(resourceType, resource)) {
            score = Math.max(score, qualityThreshold);
            passed = true;
            issues.clear();
            decision = "PASS";
            suggestions.add("视频为异步生成任务，保留 videoId 供后续轮询更新");
        }
        if (!passed && issues.isEmpty()) {
            issues.addAll(AgentJsonSupport.stringList(fallback.get("issues")));
        }
        if (suggestions.isEmpty()) {
            suggestions.addAll(AgentJsonSupport.stringList(fallback.get("suggestions")));
        }
        if (suggestions.isEmpty()) {
            suggestions.add(passed ? "审核通过，可进入学习路径规划" : "根据审核问题重写资源");
        }

        review.put("agentName", agentName);
        review.put("resourceType", resourceType);
        review.put("displayName", displayName);
        review.put("persistedId", stringValue(resource.get("persistedId")));
        review.put("score", AgentJsonSupport.round2(score));
        review.put("passed", passed);
        review.put("threshold", qualityThreshold);
        review.put("decision", passed ? "PASS" : "REVISE");
        review.put("issues", issues);
        review.put("suggestions", suggestions);
        review.put("profileFit", firstNonBlank(json.getString("profileFit"), stringValue(fallback.get("profileFit"))));
        review.put("hallucinationRisk", firstNonBlank(json.getString("hallucinationRisk"), "unknown"));
        review.put("evidenceCheck", firstNonBlank(json.getString("evidenceCheck"), ""));
        review.put("evidenceUsed", evidenceUsed(state, resource));
        review.put("revisionPrompt", buildRevisionPrompt(json, issues, suggestions, resourceType));
        review.put("reviewMode", "llm");
        return review;
    }

    private boolean isAsyncVideoTask(String resourceType, Map<String, Object> resource) {
        if (!"video".equals(resourceType)) {
            return false;
        }
        String content = stringValue(resource.get("content"));
        return content.contains("\"videoId\"")
                && (content.contains("\"pending\"") || content.contains("\"submitted\""));
    }

    private String buildSystemPrompt() {
        return """
            你是教育多智能体系统中的 Critic/QualityReviewAgent，负责审核其他资源 Agent 的输出。
            你必须严格判断资源是否适合学生直接学习，不要为了通过而放宽标准。

            审核维度：
            1. 专业准确性：是否有明显事实、概念、逻辑错误。
            2. 主题贴合度：是否紧扣用户问题和抽取主题。
            3. 画像匹配度：是否贴合知识水平、认知风格、薄弱点、目标和资源偏好。
            4. 结构可用性：是否适合前端卡片展示和学生学习。
            5. 多模态可用性：图片必须有 url/b64_json，视频必须有 videoId 或 videoUrl，动画必须可播放。
            6. 幻觉风险：链接、论文、官方文档、代码、题目答案是否存在编造风险。
            7. 依据可信：若提供了知识库片段，必须检查资源是否与片段一致；若知识库未命中，必须提高事实核验要求。

            审核边界：当前 resource 是资源包中的单个交付物，只审核它自身的职责，不要求它独立包含用户请求中的全部资源类型。
            document/article 只需完成教学文档；quiz 只需完成题目与解析；mindmap 只需完成知识结构；code 只需完成可用代码；
            reading 只需完成拓展阅读内容；image/video/animation 只需完成对应媒体资源及其可用元数据。
            缺少同一资源包中的其他资源，不能单独作为当前 resource 的失败理由；资源包完整性由编排层另行判断。

            只输出 JSON 对象，不要 Markdown，不要解释文字。字段固定：
            {
              "score": 0.0到1.0,
              "decision": "PASS" 或 "REVISE",
              "issues": ["具体问题"],
              "suggestions": ["具体修改建议"],
              "revisionPrompt": "给原 Agent 的重写指令，必须具体、可执行",
              "profileFit": "和画像的匹配判断",
              "hallucinationRisk": "low/medium/high",
              "evidenceCheck": "事实依据或风险说明"
            }
            """;
    }

    private String buildUserPrompt(EducationState state, Map<String, Object> resource) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("userMessage", state.userMessage());
        input.put("entities", state.entitiesJson());
        input.put("profile", state.profileJson());
        input.put("qualityThreshold", qualityThreshold);
        input.put("agentName", stringValue(resource.get("agentName")));
        input.put("resourceType", stringValue(resource.get("resourceType")));
        input.put("reviewScope", "仅审核当前 resource 的职责与可用性，不要求单个 resource 交付完整资源包");
        input.put("referenceSnippets", knowledgeTool.retrieveForPrompt(
                state.userMessage() + "\n" + state.entitiesJson() + "\n" + trimText(stringValue(resource.get("content")), 1500), 3));
        input.put("resource", AgentJsonSupport.compactResource(resource, 2500));
        return "请审核下面资源，并严格输出 JSON：\n"
                + com.alibaba.fastjson2.JSON.toJSONString(input);
    }

    private Map<String, Object> reviewOneFallback(EducationState state, Map<String, Object> resource) {
        String agentName = stringValue(resource.get("agentName"));
        String resourceType = stringValue(resource.get("resourceType"));
        String displayName = stringValue(resource.get("displayName"));
        String content = stringValue(resource.get("content"));
        boolean success = Boolean.TRUE.equals(resource.get("success"));
        double baseQuality = numberValue(resource.get("quality"), success ? 0.5 : 0.0);

        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        double score = Math.min(1.0, Math.max(0.0, baseQuality));
        if (!success) {
            issues.add("资源生成失败");
            score = 0.0;
        }
        if (content.isBlank()) {
            issues.add("内容为空，无法用于学习");
            score = Math.min(score, 0.2);
        } else {
            if (content.length() < 120) {
                issues.add("内容偏短，讲解或操作信息不足");
                score -= 0.2;
                suggestions.add("补充学习目标、示例、练习或检查点");
            }
            if (("image".equals(resourceType) || "video".equals(resourceType)) && content.contains("config_missing")) {
                issues.add("媒体生成密钥未配置，当前只生成了占位资源");
                score = Math.min(score, 0.45);
                suggestions.add("配置 Agnes 媒体生成密钥后重新生成资源");
            }
            if (!containsTopic(content, state.userMessage(), state.entitiesJson())) {
                issues.add("与用户问题的主题贴合度不足");
                score -= 0.15;
                suggestions.add("围绕用户当前主题重写资源核心段落");
            }
            if (requiresStructuredOutput(resourceType) && !hasStructuredMarkers(content)) {
                issues.add("缺少清晰结构，前端资源卡片难以摘要展示");
                score -= 0.15;
                suggestions.add("加入标题、步骤、目标、检查点等结构化栏目");
            }
            if ("image".equals(resourceType) && !(content.contains("\"url\"") || content.contains("\"b64_json\""))) {
                issues.add("图片资源缺少可预览 URL 或 Base64 字段");
                score -= 0.2;
                suggestions.add("检查 Agnes 图片接口响应并写入 sourceUrl");
            }
            if ("video".equals(resourceType)) {
                if (!content.contains("\"videoId\"")) {
                    issues.add("视频资源缺少 video_id，无法后续查询生成结果");
                    score -= 0.2;
                    suggestions.add("检查 Agnes 视频创建任务响应并保存 videoId");
                } else if (content.contains("\"pending\"") || content.contains("\"submitted\"")) {
                    score = Math.max(score, qualityThreshold);
                    suggestions.add("视频为异步生成任务，保留 videoId 供后续轮询更新");
                }
            }
        }

        if (suggestions.isEmpty()) {
            suggestions.add("审核通过，可进入学习路径规划");
        }
        score = Math.max(0.0, Math.min(1.0, score));

        Map<String, Object> review = new LinkedHashMap<>();
        review.put("agentName", agentName);
        review.put("resourceType", resourceType);
        review.put("displayName", displayName);
        review.put("persistedId", stringValue(resource.get("persistedId")));
        review.put("score", round(score));
        boolean passed = score >= qualityThreshold && issues.isEmpty();
        review.put("passed", passed);
        review.put("threshold", qualityThreshold);
        review.put("decision", passed ? "PASS" : "REVISE");
        review.put("issues", issues);
        review.put("suggestions", suggestions);
        review.put("profileFit", inferProfileFit(state.profileJson(), resourceType));
        review.put("hallucinationRisk", inferHallucinationRisk(resourceType, content));
        review.put("evidenceCheck", "规则兜底审核");
        review.put("evidenceUsed", evidenceUsed(state, resource));
        review.put("revisionPrompt", buildFallbackRevisionPrompt(resourceType, issues, suggestions));
        review.put("reviewMode", "rule_fallback");
        return review;
    }

    private List<Map<String, Object>> evidenceUsed(EducationState state, Map<String, Object> resource) {
        return knowledgeTool.retrieveEvidence(
                state.userMessage() + "\n" + state.entitiesJson() + "\n" + trimText(stringValue(resource.get("content")), 1500),
                3);
    }

    public Map<String, Object> summary(List<Map<String, Object>> reviews) {
        int passed = 0;
        double total = 0.0;
        for (Map<String, Object> review : reviews) {
            if (Boolean.TRUE.equals(review.get("passed"))) passed++;
            total += numberValue(review.get("score"), 0.0);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reviewedCount", reviews.size());
        summary.put("passedCount", passed);
        summary.put("averageScore", reviews.isEmpty() ? 0.0 : round(total / reviews.size()));
        summary.put("status", reviews.isEmpty() ? "NO_RESOURCE" : (passed == reviews.size() ? "PASS" : "NEED_ATTENTION"));
        return summary;
    }

    private boolean containsTopic(String content, String message, String entitiesJson) {
        String topic = "";
        try {
            topic = com.alibaba.fastjson2.JSON.parseObject(entitiesJson).getString("topic");
        } catch (Exception ignored) {}
        if (topic != null && !topic.isBlank() && content.contains(topic)) return true;
        if (message == null || message.isBlank()) return true;
        String compact = message.replaceAll("\\s+", "");
        if (compact.length() <= 8) return content.contains(compact);
        return content.contains(compact.substring(0, Math.min(8, compact.length())));
    }

    private boolean requiresStructuredOutput(String resourceType) {
        return !"mindmap".equals(resourceType)
                && !"image".equals(resourceType)
                && !"video".equals(resourceType);
    }

    private boolean hasStructuredMarkers(String content) {
        return content.contains("#") || content.contains("###") || content.contains("1.") || content.contains("- ");
    }

    private String inferProfileFit(String profileJson, String resourceType) {
        if (profileJson == null || profileJson.isBlank()) return "画像不足，按通用标准审核";
        if (("mindmap".equals(resourceType) || "animation".equals(resourceType)
                || "image".equals(resourceType) || "video".equals(resourceType))
                && (profileJson.contains("视觉") || profileJson.contains("visual"))) {
            return "匹配视觉型偏好";
        }
        if ("quiz".equals(resourceType) && profileJson.contains("weakPoints")) {
            return "匹配薄弱点练习需求";
        }
        if ("code".equals(resourceType) && (profileJson.contains("动手") || profileJson.contains("实践"))) {
            return "匹配动手实践偏好";
        }
        return "通用匹配";
    }

    private String normalizeDecision(String decision, double score, List<String> issues) {
        String value = decision == null ? "" : decision.trim().toUpperCase();
        if ("PASS".equals(value) || "REVISE".equals(value)) return value;
        return score >= qualityThreshold && (issues == null || issues.isEmpty()) ? "PASS" : "REVISE";
    }

    private String inferHallucinationRisk(String resourceType, String content) {
        if ("reading".equals(resourceType) && content != null && content.contains("http")) return "medium";
        if ("quiz".equals(resourceType) || "code".equals(resourceType)) return "medium";
        if (content != null && content.contains("待核验")) return "medium";
        return "low";
    }

    private String buildRevisionPrompt(com.alibaba.fastjson2.JSONObject json,
                                       List<String> issues,
                                       List<String> suggestions,
                                       String resourceType) {
        String llmPrompt = json.getString("revisionPrompt");
        if (llmPrompt != null && !llmPrompt.isBlank()) return llmPrompt;
        return buildFallbackRevisionPrompt(resourceType, issues, suggestions);
    }

    private String buildFallbackRevisionPrompt(String resourceType, List<String> issues, List<String> suggestions) {
        return "请重写该" + resourceType + "资源。必须解决这些问题：" + issues
                + "。必须落实这些建议：" + suggestions
                + "。保留原主题，补充结构化栏目、例子、检查点，并确保内容贴合学生画像。";
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : (second == null ? "" : second);
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

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String trimText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
