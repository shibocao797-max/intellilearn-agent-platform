package com.ruoyi.system.ai.model;

/**
 * 资源生成结果 — ResourceGenerate SubGraph 输出的单个资源条目
 *
 * @author edu-agent
 */
public class GeneratedResource {

    /** Agent 名称 */
    private String agentName;

    /** 资源类型（对应 edu_resource.type） */
    private String resourceType;

    /** 显示名 */
    private String displayName;

    /** 持久化后的 ID 或 URL */
    private String persistedId;

    /** 生成质量分 0.0-1.0 */
    private double quality;

    /** 生成耗时（毫秒） */
    private long durationMs;

    /** 生成的原始内容（文档/题目/代码等） */
    private String content;

    /** 是否生成成功 */
    private boolean success;

    /** 错误信息（失败时） */
    private String error;

    // === 静态工厂 ===

    public static GeneratedResource success(String agentName, String resourceType,
                                              String displayName, String persistedId,
                                              double quality, long durationMs, String content) {
        GeneratedResource r = new GeneratedResource();
        r.agentName = agentName;
        r.resourceType = resourceType;
        r.displayName = displayName;
        r.persistedId = persistedId;
        r.quality = quality;
        r.durationMs = durationMs;
        r.content = content;
        r.success = true;
        r.error = null;
        return r;
    }

    public static GeneratedResource failure(String agentName, String resourceType,
                                              String displayName, String error) {
        GeneratedResource r = new GeneratedResource();
        r.agentName = agentName;
        r.resourceType = resourceType;
        r.displayName = displayName;
        r.quality = 0.0;
        r.success = false;
        r.error = error;
        return r;
    }

    // === getters/setters ===

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPersistedId() { return persistedId; }
    public void setPersistedId(String persistedId) { this.persistedId = persistedId; }
    public double getQuality() { return quality; }
    public void setQuality(double quality) { this.quality = quality; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
