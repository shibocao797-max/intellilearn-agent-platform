package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/** One replayable event emitted by an AI task. */
public class EduAiTaskEvent
{
    private Long sequence;
    private String taskId;
    private String sessionId;
    private String eventType;
    private String agentKey;
    private String status;
    private String payloadJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private Date occurredTime;

    public Long getSequence() { return sequence; }
    public void setSequence(Long sequence) { this.sequence = sequence; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getAgentKey() { return agentKey; }
    public void setAgentKey(String agentKey) { this.agentKey = agentKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Date getOccurredTime() { return occurredTime; }
    public void setOccurredTime(Date occurredTime) { this.occurredTime = occurredTime; }
}
