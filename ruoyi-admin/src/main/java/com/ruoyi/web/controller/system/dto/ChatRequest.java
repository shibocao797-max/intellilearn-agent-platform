package com.ruoyi.web.controller.system.dto;
import java.util.List;
public class ChatRequest {
    private String message; private String sessionId; private List<Long> attachmentIds; private String clientRequestId;
    public String getMessage() { return message; } public void setMessage(String message) { this.message = message; }
    public String getSessionId() { return sessionId; } public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public List<Long> getAttachmentIds() { return attachmentIds; } public void setAttachmentIds(List<Long> attachmentIds) { this.attachmentIds = attachmentIds; }
    public String getClientRequestId() { return clientRequestId; } public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }
}
