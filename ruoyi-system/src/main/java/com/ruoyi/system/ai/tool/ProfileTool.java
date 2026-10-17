package com.ruoyi.system.ai.tool;

import com.ruoyi.system.domain.LearnerProfile;
import com.ruoyi.system.domain.LearnerProfileEvidence;
import com.ruoyi.system.domain.LearnerProfileSnapshot;
import com.ruoyi.system.mapper.LearnerProfileEvidenceMapper;
import com.ruoyi.system.mapper.LearnerProfileMapper;
import com.ruoyi.system.mapper.LearnerProfileSnapshotMapper;
import com.ruoyi.system.mapper.LearnerProfileConfirmationMapper;
import com.ruoyi.system.domain.LearnerProfileConfirmation;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 画像工具 — 学习画像的加载、保存、合并
 *
 * @author edu-agent
 */
@Component
public class ProfileTool {

    private static final Logger log = LoggerFactory.getLogger(ProfileTool.class);

    @Autowired
    private LearnerProfileMapper learnerProfileMapper;

    @Autowired
    private LearnerProfileSnapshotMapper snapshotMapper;

    @Autowired
    private LearnerProfileEvidenceMapper evidenceMapper;

    @Autowired
    private LearnerProfileConfirmationMapper confirmationMapper;

    /**
     * 加载用户的学习画像（从 MySQL 查询）
     */
    public String loadProfile(Long userId) {
        LearnerProfile lp = learnerProfileMapper.selectProfileByUserId(userId);
        if (lp == null || lp.getProfileJson() == null) {
            return "{}";
        }
        log.debug("画像已加载: userId={}, version={}", userId, lp.getVersion());
        return effectiveProfileJson(userId, lp);
    }

    /** Returns the current profile after applying the version-scoped decisions. */
    public String buildEffectiveProfileJson(Long userId, LearnerProfile profile) {
        if (profile == null || profile.getProfileJson() == null) return "{}";
        return effectiveProfileJson(userId, profile);
    }

    /**
     * Persists only validated profile differences. Empty, malformed or unchanged
     * extraction results are intentionally not treated as a profile update.
     */
    @Transactional(rollbackFor = Exception.class)
    public ProfileUpdateResult applyProfileUpdate(Long userId, String extractedFeatures,
                                                   String sessionId, String sourceExcerpt) {
        JSONObject extracted = parseJson(extractedFeatures);
        JSONObject incoming = extracted == null ? null : extracted.getJSONObject("dimensions");
        if (incoming == null || incoming.isEmpty()) {
            return ProfileUpdateResult.unchanged(loadProfile(userId), "本次对话未产生新的画像证据");
        }

        LearnerProfile current = learnerProfileMapper.selectProfileByUserId(userId);
        JSONObject profile = current == null ? new JSONObject() : parseJson(current.getProfileJson());
        if (profile == null) profile = new JSONObject();
        JSONObject dimensions = profile.getJSONObject("dimensions");
        if (dimensions == null) {
            dimensions = new JSONObject();
            profile.put("dimensions", dimensions);
        }

        Set<String> changedKeys = new LinkedHashSet<>();
        Map<String, JSONObject> changedDimensions = new LinkedHashMap<>();
        for (String key : incoming.keySet()) {
            if (!supportedDimension(key)) continue;
            JSONObject normalized = normalizeDimension(incoming.get(key));
            if (normalized == null) continue;
            if (!JSON.toJSONString(dimensions.get(key)).equals(normalized.toJSONString())) {
                dimensions.put(key, normalized);
                changedKeys.add(key);
                changedDimensions.put(key, normalized);
            }
        }
        if (changedKeys.isEmpty()) {
            return ProfileUpdateResult.unchanged(profile.toJSONString(), "提取结果未带来可持久化差异");
        }

        int version = current == null ? 1 : Math.max(0, current.getVersion() == null ? 0 : current.getVersion()) + 1;
        profile.put("version", version);
        profile.put("dimensionCount", dimensions.size());
        profile.put("schemaVersion", 2);
        profile.remove("lastUpdated");
        String profileJson = profile.toJSONString();

        LearnerProfile persisted = current == null ? new LearnerProfile() : current;
        persisted.setUserId(userId);
        persisted.setProfileJson(profileJson);
        persisted.setVersion(version);
        persisted.setDimensionCount(dimensions.size());
        persisted.setLastExtractAt(new Date());
        if (current == null) learnerProfileMapper.insertProfile(persisted);
        else learnerProfileMapper.updateProfile(persisted);

        LearnerProfileSnapshot snapshot = new LearnerProfileSnapshot();
        snapshot.setUserId(userId);
        snapshot.setProfileId(persisted.getId());
        snapshot.setVersion(version);
        snapshot.setProfileJson(profileJson);
        snapshot.setChangeSummary("更新维度：" + String.join("、", changedKeys));
        snapshotMapper.insertSnapshot(snapshot);

        for (Map.Entry<String, JSONObject> entry : changedDimensions.entrySet()) {
            LearnerProfileEvidence evidence = new LearnerProfileEvidence();
            evidence.setUserId(userId);
            evidence.setProfileId(persisted.getId());
            evidence.setProfileVersion(version);
            evidence.setDimensionKey(entry.getKey());
            evidence.setSourceType("CONVERSATION");
            evidence.setSourceRef(trimText(sessionId, 128));
            evidence.setExcerpt(trimText(sourceExcerpt, 500));
            evidence.setConfidence(decimal(entry.getValue().get("confidence")));
            evidence.setEvidenceStatus("PENDING_CONFIRMATION");
            evidenceMapper.insertEvidence(evidence);
        }
        return ProfileUpdateResult.changed(profileJson, version, changedKeys);
    }

    /** Persists an objectively graded, same-topic practice result as verified mastery. */
    @Transactional(rollbackFor = Exception.class)
    public ProfileUpdateResult applyVerifiedMastery(Long userId, String topic, int sampleCount,
                                                    int correctCount, String sessionId) {
        if (userId == null || userId <= 0 || topic == null || topic.isBlank() || sampleCount < 1) {
            return ProfileUpdateResult.unchanged(loadProfile(userId), "练习验证数据不完整");
        }
        int boundedCorrect = Math.max(0, Math.min(sampleCount, correctCount));
        double score = Math.round(boundedCorrect * 1000.0 / sampleCount) / 10.0;

        LearnerProfile current = learnerProfileMapper.selectProfileByUserId(userId);
        JSONObject profile = current == null ? new JSONObject() : parseJson(current.getProfileJson());
        if (profile == null) profile = new JSONObject();
        JSONObject dimensions = profile.getJSONObject("dimensions");
        if (dimensions == null) {
            dimensions = new JSONObject();
            profile.put("dimensions", dimensions);
        }

        JSONObject mastery = new JSONObject();
        mastery.put("value", score);
        mastery.put("sampleCount", sampleCount);
        mastery.put("topic", trimText(topic, 120));
        mastery.put("confidence", BigDecimal.ONE);
        mastery.put("evidence", "完成“" + trimText(topic, 120) + "”同主题客观练习 "
                + sampleCount + " 道，答对 " + boundedCorrect + " 道。");
        if (JSON.toJSONString(dimensions.get("knowledgeMastery")).equals(mastery.toJSONString())) {
            return ProfileUpdateResult.unchanged(profile.toJSONString(), "练习验证结果未发生变化");
        }
        dimensions.put("knowledgeMastery", mastery);

        int version = current == null ? 1 : Math.max(0, current.getVersion() == null ? 0 : current.getVersion()) + 1;
        profile.put("version", version);
        profile.put("dimensionCount", dimensions.size());
        profile.put("schemaVersion", 2);
        profile.remove("lastUpdated");
        String profileJson = profile.toJSONString();

        LearnerProfile persisted = current == null ? new LearnerProfile() : current;
        persisted.setUserId(userId);
        persisted.setProfileJson(profileJson);
        persisted.setVersion(version);
        persisted.setDimensionCount(dimensions.size());
        persisted.setLastExtractAt(new Date());
        if (current == null) learnerProfileMapper.insertProfile(persisted);
        else learnerProfileMapper.updateProfile(persisted);

        LearnerProfileSnapshot snapshot = new LearnerProfileSnapshot();
        snapshot.setUserId(userId);
        snapshot.setProfileId(persisted.getId());
        snapshot.setVersion(version);
        snapshot.setProfileJson(profileJson);
        snapshot.setChangeSummary("练习验证：" + trimText(topic, 120));
        snapshotMapper.insertSnapshot(snapshot);

        LearnerProfileEvidence evidence = new LearnerProfileEvidence();
        evidence.setUserId(userId);
        evidence.setProfileId(persisted.getId());
        evidence.setProfileVersion(version);
        evidence.setDimensionKey("knowledgeMastery");
        evidence.setSourceType("PRACTICE_SESSION");
        evidence.setSourceRef(trimText(sessionId, 128));
        evidence.setExcerpt(trimText(mastery.getString("evidence"), 500));
        evidence.setConfidence(BigDecimal.ONE);
        evidence.setEvidenceStatus("VERIFIED");
        evidenceMapper.insertEvidence(evidence);
        return ProfileUpdateResult.changed(profileJson, version, Set.of("knowledgeMastery"));
    }

    /**
     * 保存学习画像到 MySQL
     */
    @Deprecated
    public void saveProfile(Long userId, String profileJson) {
        log.warn("已拒绝无证据的画像保存: userId={}; 请调用 applyProfileUpdate", userId);
    }

    /**
     * 合并画像 — 新特征覆盖旧特征，不丢失旧信息
     */
    @Deprecated
    public String mergeProfile(String oldProfile, String newFeatures) {
        log.warn("已拒绝未验证的画像合并；请调用 applyProfileUpdate");
        return oldProfile;
    }

    /**
     * 检查画像是否在冷却期内（避免高频 LLM 调用更新画像）
     */
    public boolean isInCooldown(String profileJson, int cooldownMinutes) {
        if (profileJson == null || profileJson.equals("{}")) {
            return false;
        }
        try {
            var json = JSON.parseObject(profileJson);
            String lastUpdateStr = json.getString("lastUpdated");
            if (lastUpdateStr == null || lastUpdateStr.isBlank()) return false;
            var lastUpdate = LocalDateTime.parse(lastUpdateStr);
            var cooldownThreshold = LocalDateTime.now().minusMinutes(cooldownMinutes);
            return lastUpdate.isAfter(cooldownThreshold);
        } catch (Exception e) {
            log.debug("冷却期检查失败（默认不冷却）: {}", e.getMessage());
            return false;
        }
    }

    public boolean isInCooldown(Long userId, int cooldownMinutes) {
        LearnerProfile profile = learnerProfileMapper.selectProfileByUserId(userId);
        if (profile == null || profile.getLastExtractAt() == null) return false;
        long elapsed = System.currentTimeMillis() - profile.getLastExtractAt().getTime();
        return elapsed >= 0 && elapsed < cooldownMinutes * 60_000L;
    }

    /** Builds facts for the user-facing profile screen without inventing scores. */
    public Map<String, Object> buildProfileView(Long userId, LearnerProfile profile) {
        JSONObject payload = profile == null ? new JSONObject() : parseJson(profile.getProfileJson());
        JSONObject dimensions = payload == null ? new JSONObject() : payload.getJSONObject("dimensions");
        if (dimensions == null) dimensions = new JSONObject();
        List<LearnerProfileEvidence> evidenceRows = profile == null || profile.getId() == null || profile.getVersion() == null
                ? List.of()
                : evidenceMapper.selectEvidenceByProfile(userId, profile.getId(), profile.getVersion());
        Map<String, LearnerProfileConfirmation> confirmations = new LinkedHashMap<>();
        if (profile != null && profile.getId() != null && profile.getVersion() != null) {
            for (LearnerProfileConfirmation row : confirmationMapper.selectByProfile(userId, profile.getId(), profile.getVersion())) {
                confirmations.put(row.getDimensionKey(), row);
            }
        }
        List<Map<String, Object>> metrics = new ArrayList<>();
        List<Map<String, Object>> preferences = new ArrayList<>();
        List<Map<String, Object>> inferences = new ArrayList<>();
        Map<String, Map<String, Object>> dimensionViews = new LinkedHashMap<>();
        Map<String, Object> effectiveDimensions = new LinkedHashMap<>();

        for (String key : dimensions.keySet()) {
            JSONObject value = normalizeDimension(dimensions.get(key));
            if (value == null) continue;
            LearnerProfileConfirmation confirmation = confirmations.get(key);
            if (confirmation == null || !"REJECTED".equals(confirmation.getStatus())) {
                effectiveDimensions.put(key, dimensions.get(key));
            }
            if ("knowledgeMastery".equals(key) && value.get("value") instanceof Number && intValue(value.get("sampleCount")) > 0) {
                Map<String, Object> metric = dimensionItem(key, value);
                double score = ((Number) value.get("value")).doubleValue();
                metric.put("score", score <= 1 ? score * 100 : Math.min(100, Math.max(0, score)));
                metric.put("sampleCount", intValue(value.get("sampleCount")));
                metric.put("topic", value.getString("topic"));
                metric.put("status", "VERIFIED");
                metrics.add(metric);
                dimensionViews.put(key, metric);
            } else if (Set.of("learningGoal", "cognitiveStyle", "preferredResourceType", "knowledgeDepthPreference", "learningStrategyMode", "selfDrivenLearning").contains(key)) {
                Map<String, Object> preference = dimensionItem(key, value);
                preference.put("status", "RECORDED");
                preferences.add(preference);
                dimensionViews.put(key, preference);
            } else if (Set.of("weakPoints", "strengths", "knowledgeLevel").contains(key)) {
                if (!hasMeaningfulValue(value.get("value"))) continue;
                Map<String, Object> item = dimensionItem(key, value);
                item.put("status", confirmation == null ? "PENDING_CONFIRMATION" : confirmation.getStatus());
                item.put("confirmationId", confirmation == null ? null : confirmation.getId());
                item.put("profileVersion", profile == null ? null : profile.getVersion());
                inferences.add(item);
                dimensionViews.put(key, item);
            }
        }

        List<Map<String, Object>> evidence = new ArrayList<>();
        for (LearnerProfileEvidence row : evidenceRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("dimensionKey", row.getDimensionKey());
            item.put("dimensionLabel", labelOf(row.getDimensionKey()));
            item.put("profileVersion", row.getProfileVersion());
            item.put("sourceType", row.getSourceType());
            item.put("sourceRef", row.getSourceRef());
            item.put("excerpt", row.getExcerpt());
            item.put("confidence", row.getConfidence());
            String evidenceStatus = row.getEvidenceStatus();
            item.put("status", evidenceStatus == null || evidenceStatus.isBlank() ? "RECORDED" : evidenceStatus);
            item.put("evidenceStatus", evidenceStatus);
            item.put("createdAt", row.getCreateTime());
            evidence.add(item);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        long pendingCount = inferences.stream().filter(item -> "PENDING_CONFIRMATION".equals(item.get("status"))).count();
        long confirmedCount = inferences.stream().filter(item -> "CONFIRMED".equals(item.get("status"))).count();
        long rejectedCount = inferences.stream().filter(item -> "REJECTED".equals(item.get("status"))).count();
        String overallStatus;
        if (metrics.isEmpty() && evidence.isEmpty() && inferences.isEmpty()) {
            overallStatus = "INSUFFICIENT_EVIDENCE";
            summary.put("status", "INSUFFICIENT_EVIDENCE");
            summary.put("text", "尚未积累足够的可验证学习证据。偏好和推断不会被当作能力评分。");
        } else if (pendingCount > 0 && (confirmedCount > 0 || rejectedCount > 0)) {
            overallStatus = "PARTIALLY_CONFIRMED";
            summary.put("status", overallStatus);
            summary.put("text", "当前画像包含 " + evidence.size() + " 条可追溯证据；部分推断已处理，仍有结论等待确认。");
        } else if (pendingCount > 0) {
            overallStatus = "PENDING_CONFIRMATION";
            summary.put("status", overallStatus);
            summary.put("text", "当前画像包含 " + evidence.size() + " 条可追溯证据；待确认结论需要由你确认或通过练习进一步验证。");
        } else if (!inferences.isEmpty() && rejectedCount == inferences.size() && metrics.isEmpty()) {
            overallStatus = "REJECTED";
            summary.put("status", overallStatus);
            summary.put("text", "当前推断均已标记为不符合，请通过新的学习记录重新建立画像。");
        } else if (rejectedCount > 0) {
            overallStatus = "PARTIALLY_CONFIRMED";
            summary.put("status", overallStatus);
            summary.put("text", "当前画像包含已处理的推断和练习验证结果；被标记不符的结论不会用于后续推荐。");
        } else if (!inferences.isEmpty() && confirmedCount == inferences.size()) {
            overallStatus = "CONFIRMED";
            summary.put("status", overallStatus);
            summary.put("text", "当前画像推断已由你确认；可量化能力仍以练习结果为准。");
        } else {
            overallStatus = metrics.isEmpty() ? "PENDING_CONFIRMATION" : "VERIFIED";
            summary.put("status", overallStatus);
            summary.put("text", metrics.isEmpty() ? "当前画像暂无可确认的非空推断。" : "当前画像包含已通过练习数据验证的可量化指标。");
        }
        summary.put("generatedAt", new Date());
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("status", overallStatus);
        overview.put("version", profile == null ? 0 : profile.getVersion());
        overview.put("dimensionCount", effectiveDimensions.size());
        overview.put("evidenceCount", evidence.size());
        overview.put("verifiedDimensionCount", dimensionViews.values().stream()
                .filter(item -> "VERIFIED".equals(item.get("status"))).count());
        overview.put("recordedDimensionCount", dimensionViews.values().stream()
                .filter(item -> "RECORDED".equals(item.get("status"))).count());
        overview.put("pendingConfirmationCount", pendingCount);
        overview.put("confirmedCount", confirmedCount);
        overview.put("rejectedCount", rejectedCount);
        overview.put("summary", summary.get("text"));
        String evidenceStatus = evidence.isEmpty()
                ? "INSUFFICIENT_EVIDENCE"
                : evidence.stream().allMatch(item -> "VERIFIED".equals(item.get("status")))
                    ? "VERIFIED"
                    : "RECORDED";
        overview.put("evidenceStatus", evidenceStatus);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimensions", effectiveDimensions);
        result.put("overview", overview);
        result.put("capabilityMetrics", metrics);
        result.put("preferences", preferences);
        result.put("inferences", inferences);
        result.put("evidence", evidence);
        result.put("overallStatus", overallStatus);
        result.put("evidenceStatus", evidenceStatus);
        result.put("summary", summary);
        return result;
    }

    public Map<String, Object> buildActionPlan(Long userId, LearnerProfile profile) {
        Map<String, Object> view = buildProfileView(userId, profile);
        List<?> evidence = (List<?>) view.get("evidence");
        List<?> preferences = (List<?>) view.get("preferences");
        List<?> inferences = effectiveInferences((List<?>) view.get("inferences"));
        List<Map<String, String>> steps = new ArrayList<>();
        if (evidence == null || evidence.isEmpty()) {
            steps.add(planStep("完成主题诊断", "先完成至少 5 道同主题练习，建立可量化的学习证据。"));
            steps.add(planStep("补充学习目标", "在 AI 对话中说明目标、已有基础和偏好的学习方式。"));
            steps.add(planStep("确认画像推断", "待系统收集到依据后，确认或修正其中的学习偏好与待巩固点。"));
            return Map.of("status", "NEEDS_EVIDENCE", "title", "画像完善计划", "summary", "当前证据不足，先完成以下步骤再生成个性化计划。", "steps", steps);
        }
        String focus = firstDimensionValue(inferences, "待确认结论");
        String goal = firstDimensionValue(preferences, "当前学习目标");
        steps.add(planStep("确认当前画像", "先确认“" + focus + "”是否符合实际学习情况，避免基于错误推断安排学习。"));
        steps.add(planStep("完成针对性练习", "围绕“" + goal + "”完成一组练习，并保留答题结果作为后续依据。"));
        steps.add(planStep("复盘并更新", "完成练习后查看新增证据与画像变化，再安排下一阶段学习。"));
        return Map.of("status", "PENDING_CONFIRMATION", "title", "基于当前证据的学习计划", "summary", "计划仅使用已记录证据；待确认推断不会被当作确定能力结论。", "steps", steps);
    }

    private String effectiveProfileJson(Long userId, LearnerProfile profile) {
        JSONObject payload = parseJson(profile.getProfileJson());
        if (payload == null) return "{}";
        JSONObject dimensions = payload.getJSONObject("dimensions");
        if (dimensions == null || profile.getId() == null || profile.getVersion() == null) return payload.toJSONString();

        Map<String, LearnerProfileConfirmation> confirmations = new LinkedHashMap<>();
        for (LearnerProfileConfirmation row : confirmationMapper.selectByProfile(userId, profile.getId(), profile.getVersion())) {
            confirmations.put(row.getDimensionKey(), row);
        }
        for (String key : List.of("knowledgeLevel", "weakPoints", "strengths")) {
            LearnerProfileConfirmation confirmation = confirmations.get(key);
            if (confirmation != null && "REJECTED".equals(confirmation.getStatus())) {
                dimensions.remove(key);
                continue;
            }
            Object raw = dimensions.get(key);
            if (raw instanceof JSONObject dimension) {
                dimension.put("confirmationStatus", confirmation == null ? "PENDING_CONFIRMATION" : confirmation.getStatus());
            }
        }
        return payload.toJSONString();
    }

    private static List<?> effectiveInferences(List<?> inferences) {
        if (inferences == null || inferences.isEmpty()) return List.of();
        List<Object> result = new ArrayList<>();
        for (Object raw : inferences) {
            if (raw instanceof Map<?, ?> item && "REJECTED".equals(String.valueOf(item.get("status")))) continue;
            result.add(raw);
        }
        return result;
    }

    private static boolean supportedDimension(String key) {
        return Set.of("knowledgeLevel", "cognitiveStyle", "weakPoints", "strengths", "learningGoal",
                "preferredResourceType", "knowledgeDepthPreference", "learningStrategyMode",
                "selfDrivenLearning").contains(key);
    }

    private static JSONObject normalizeDimension(Object raw) {
        if (raw == null) return null;
        JSONObject source;
        if (raw instanceof JSONObject object) source = object;
        else {
            source = new JSONObject();
            source.put("value", raw);
        }
        Object rawValue = source.containsKey("value") ? source.get("value") : source.get("score");
        if (rawValue == null) return null;
        JSONObject result = new JSONObject();
        result.put("value", rawValue);
        BigDecimal confidence = decimal(source.get("confidence"));
        if (confidence != null) result.put("confidence", confidence);
        String evidence = source.getString("evidence");
        if (evidence != null && !evidence.isBlank()) result.put("evidence", trimText(evidence, 500));
        if (source.get("sampleCount") != null) result.put("sampleCount", intValue(source.get("sampleCount")));
        String topic = source.getString("topic");
        if (topic != null && !topic.isBlank()) result.put("topic", trimText(topic, 120));
        return result;
    }

    private static Map<String, Object> dimensionItem(String key, JSONObject source) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", labelOf(key));
        item.put("value", readable(source.get("value")));
        item.put("confidence", source.get("confidence"));
        item.put("evidence", source.getString("evidence"));
        item.put("topic", source.getString("topic"));
        return item;
    }

    private static Map<String, String> planStep(String title, String description) {
        return Map.of("title", title, "description", description);
    }

    private static String firstDimensionValue(List<?> items, String fallback) {
        if (items == null || items.isEmpty() || !(items.get(0) instanceof Map<?, ?> item)) return fallback;
        Object value = item.get("value");
        return value == null || String.valueOf(value).isBlank() ? fallback : trimText(String.valueOf(value), 80);
    }

    private static JSONObject parseJson(String raw) {
        try { return raw == null || raw.isBlank() ? new JSONObject() : JSON.parseObject(raw); }
        catch (Exception ignored) { return null; }
    }

    private static BigDecimal decimal(Object raw) {
        if (raw == null) return null;
        try {
            double value = Double.parseDouble(String.valueOf(raw));
            return BigDecimal.valueOf(Math.max(0, Math.min(1, value)));
        } catch (Exception ignored) { return null; }
    }

    private static int intValue(Object raw) {
        try { return Math.max(0, Integer.parseInt(String.valueOf(raw))); }
        catch (Exception ignored) { return 0; }
    }

    private static boolean hasMeaningfulValue(Object value) {
        if (value == null) return false;
        if (value instanceof String text) return !text.isBlank();
        if (value instanceof Collection<?> collection) return collection.stream().anyMatch(ProfileTool::hasMeaningfulValue);
        if (value instanceof Map<?, ?> map) return map.values().stream().anyMatch(ProfileTool::hasMeaningfulValue);
        return true;
    }

    private static String trimText(String text, int max) {
        if (text == null) return "";
        String value = text.trim();
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String readable(Object value) {
        if (value instanceof JSONArray array) {
            List<String> labels = new ArrayList<>();
            for (Object item : array) {
                if (item instanceof JSONObject object) {
                    String type = object.getString("type");
                    String topic = object.getString("topic");
                    String label = resourceLabel(type);
                    if (topic != null && !topic.isBlank()) label += "（" + topic + "）";
                    labels.add(label);
                } else if (item != null) {
                    labels.add(resourceLabel(String.valueOf(item)));
                }
            }
            return labels.isEmpty() ? "暂未记录" : String.join("、", new LinkedHashSet<>(labels));
        }
        if (value instanceof JSONObject object) {
            String topic = object.getString("topic");
            String name = object.getString("name");
            return topic != null && !topic.isBlank() ? topic : name != null && !name.isBlank() ? name : "待确认";
        }
        return String.valueOf(value);
    }

    private static String resourceLabel(String type) {
        if (type == null || type.isBlank()) return "学习资源";
        return Map.ofEntries(
                Map.entry("video_resource", "教学视频"), Map.entry("video", "教学视频"),
                Map.entry("html_animation", "互动动画"), Map.entry("animation", "互动动画"),
                Map.entry("teaching_image", "教学配图"), Map.entry("image", "教学配图"),
                Map.entry("study_document", "学习文档"), Map.entry("document", "学习文档"),
                Map.entry("quiz", "练习题"), Map.entry("mindmap", "思维导图"), Map.entry("code", "代码案例")
        ).getOrDefault(type, type);
    }

    private static String labelOf(String key) {
        return Map.of(
                "knowledgeMastery", "知识掌握度", "knowledgeLevel", "知识基础", "cognitiveStyle", "认知风格",
                "weakPoints", "待巩固知识点", "strengths", "优势知识点", "learningGoal", "学习目标",
                "preferredResourceType", "资源偏好", "knowledgeDepthPreference", "知识深度偏好",
                "learningStrategyMode", "学习策略", "selfDrivenLearning", "自主学习习惯"
        ).getOrDefault(key, key);
    }

    public record ProfileUpdateResult(String profileJson, boolean changed, Integer version, Set<String> changedDimensions, String reason) {
        static ProfileUpdateResult unchanged(String profileJson, String reason) {
            return new ProfileUpdateResult(profileJson, false, null, Set.of(), reason);
        }
        static ProfileUpdateResult changed(String profileJson, int version, Set<String> keys) {
            return new ProfileUpdateResult(profileJson, true, version, Set.copyOf(keys), "画像已更新");
        }
    }
}
