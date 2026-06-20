package com.ruoyi.system.ai.model;

import java.util.Collections;
import java.util.List;

/**
 * 调度官（IntentAnalysis）输出结果模型
 *
 * 包含意图类型 + 选中的Agent列表 + 推理理由，
 * 用于 dispatchAgents 节点按需并行执行。
 *
 * @author edu-agent
 */
public class DispatchResult {

    /** 意图类型: RESOURCE_GEN/QUIZ_REQUEST/GENERAL_CHAT */
    private String intent;

    /** 调度官选中的 Agent 名称列表，如 ["textReply","quizGen","mindmapGen"] */
    private List<String> selectedAgents;

    /** 抽取的实体 JSON */
    private String entitiesJson;

    /** 推荐理由 */
    private String reason;

    /** 置信度 */
    private double confidence;

    // === 静态工厂 ===

    /** JSON 解析失败时的安全兜底 — 至少启动文本回复Agent */
    public static DispatchResult fallback() {
        DispatchResult r = new DispatchResult();
        r.intent = "GENERAL_CHAT";
        r.selectedAgents = List.of("textReply");
        r.entitiesJson = "{}";
        r.reason = "调度分析失败，默认启动文本回复";
        r.confidence = 0.0;
        return r;
    }

    /**
     * 从 LLM 返回的 JSON 解析为 DispatchResult
     */
    public static DispatchResult fromJson(String jsonStr) {
        try {
            // 清理 markdown 代码块包裹
            String cleaned = jsonStr.trim();
            if (cleaned.startsWith("```")) {
                int start = cleaned.indexOf("\n") + 1;
                int end = cleaned.lastIndexOf("```");
                if (start > 0 && end > start) {
                    cleaned = cleaned.substring(start, end).trim();
                }
            }

            var json = com.alibaba.fastjson2.JSON.parseObject(cleaned);
            DispatchResult r = new DispatchResult();
            r.intent = json.getString("intent");
            if (r.intent == null || r.intent.isBlank()) {
                r.intent = "GENERAL_CHAT";
            }

            // selectedAgents — 必须是 List<String>
            var agentsArr = json.getJSONArray("selectedAgents");
            if (agentsArr != null && !agentsArr.isEmpty()) {
                r.selectedAgents = agentsArr.toJavaList(String.class);
            } else {
                // 兜底：至少启动 textReply
                r.selectedAgents = List.of("textReply");
            }

            // entities
            var entities = json.getJSONObject("entities");
            r.entitiesJson = entities != null ? entities.toJSONString() : "{}";

            r.reason = json.getString("reason");
            if (r.reason == null) r.reason = "";

            r.confidence = json.getDoubleValue("confidence");

            return r;
        } catch (Exception e) {
            return fallback();
        }
    }

    // === getters/setters ===

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public List<String> getSelectedAgents() { return selectedAgents; }
    public void setSelectedAgents(List<String> selectedAgents) { this.selectedAgents = selectedAgents; }

    public String getEntitiesJson() { return entitiesJson; }
    public void setEntitiesJson(String entitiesJson) { this.entitiesJson = entitiesJson; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
}
