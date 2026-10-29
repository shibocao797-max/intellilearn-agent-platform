package com.ruoyi.web.controller.system.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public class ChatRequest {
    @Size(max = 12000, message = "message cannot exceed 12000 characters")
    private String message;

    @Size(max = 64, message = "sessionId cannot exceed 64 characters")
    private String sessionId;

    @Size(max = 10, message = "at most 10 attachments may be submitted")
    private List<Long> attachmentIds;

    @Size(max = 128, message = "clientRequestId cannot exceed 128 characters")
    private String clientRequestId;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<Long> getAttachmentIds() {
        return attachmentIds;
    }

    public void setAttachmentIds(List<Long> attachmentIds) {
        this.attachmentIds = attachmentIds;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }
}
