package com.ruoyi.web.controller.system;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.ai.graph.EducationGraphBuilder;
import com.ruoyi.system.ai.graph.EducationState;
import com.ruoyi.system.ai.service.AgentOrchestrationService;
import com.ruoyi.system.ai.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 教育 AI 对话 Controller — 多智能体系统核心对话入口（改造版）
 *
 * 适配新架构: 画像前置 → 调度官 → 按需并行 → 结果汇总
 * SSE 事件新增: event:phase（阶段进度）、event:selectedAgents（调度官选中列表）
 *
 * 路由前缀: /edu/ai
 *
 * @author edu-agent
 */
@RestController
@RequestMapping("/edu/ai")
public class EduAgentController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(EduAgentController.class);

    @Autowired
    private AgentOrchestrationService orchestrationService;

    @Autowired
    private EducationGraphBuilder graphBuilder;

    @Autowired
    private ConversationService conversationService;

    // 注入 Mapper 用于会话管理 API（聚合查询 + 删除）
    @Autowired
    private com.ruoyi.system.mapper.ConversationHistoryMapper conversationHistoryMapper;

    /**

    /**
     * 核心对话接口 — 接收用户消息，返回意图 + 选中的Agent + 生成的资源
     *
     * POST /edu/ai/chat
     */
    @PostMapping("/chat")
    public AjaxResult chat(@RequestBody Map<String, String> body) {
        Long userId = SecurityUtils.getUserId();
        String message = body.get("message");
        String sessionId = body.getOrDefault("sessionId", conversationService.newSessionId());

        if (message == null || message.isBlank()) {
            return error("消息不能为空");
        }

        // 保存用户消息
        conversationService.saveMessage(userId, sessionId, "user", message, null);

        // 执行 StateGraph（新架构: 画像更新→调度官→按需并行→结果汇总）
        EducationState finalState;
        try {
            finalState = orchestrationService.execute(userId, message, sessionId);
        } catch (Exception e) {
            log.error("对话处理失败: {}", e.getMessage(), e);
            return error("AI 处理失败，请稍后重试: " + e.getMessage());
        }

        // 构造响应
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sessionId", sessionId);
        response.put("intent", finalState.intent());
        response.put("selectedAgents", finalState.selectedAgents());
        response.put("recommendReason", finalState.recommendReason());

        // 生成结果
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>)
                finalState.value(EducationState.GENERATED_RESOURCES).orElse(List.of());
        response.put("generatedResources", resources);
        response.put("resourceCount", resources.size());

        // 文本回复（TextReplyAgent 输出）
        response.put("textReply", finalState.textReply());

        // 画像信息
        String profileJson = finalState.profileJson();
        response.put("profileJson", profileJson);

        // 阶段标识
        response.put("currentPhase", finalState.currentPhase());

        // 保存助手消息（存储完整的 AI 回复文本 + 生成的资源列表，供历史会话回看恢复资源卡片）
        String textReply = finalState.textReply();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> syncResources = (List<Map<String, Object>>)
                finalState.value(EducationState.GENERATED_RESOURCES).orElse(List.of());
        conversationService.saveMessage(userId, sessionId, "assistant",
                textReply != null && !textReply.isBlank() ? textReply : "AI 已处理",
                finalState.intent(), syncResources);

        return success(response);
    }

    /**
     * SSE 流式对话 — 新架构适配
     *
     * SSE 事件协议（新增 phase、selectedAgents 事件）:
     *   event:phase           data:"PROFILE_UPDATED"       — 阶段进度
     *   event:intent          data:{...}                    — 意图识别结果
     *   event:selectedAgents  data:["textReply","quizGen"]  — 调度官选中列表
     *   event:resource        data:{...}                    — 单个资源卡片（逐个推送）
     *   event:token           data:文本片段                 — 流式文本
     *   event:tokenDone       data:""                       — 文本流结束
     *   event:profile         data:{...}                    — 画像更新
     *   event:done            data:complete                 — 流结束，附带完整 textReply 内容
     *   event:error           data:错误信息                 — 异常终止
     *
     * POST /edu/ai/chat/stream
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, String> body) {
        Long userId = SecurityUtils.getUserId();
        String message = body.get("message");
        String sessionId = body.getOrDefault("sessionId", conversationService.newSessionId());

        if (message == null || message.isBlank()) {
            SseEmitter err = new SseEmitter();
            err.completeWithError(new IllegalArgumentException("消息不能为空"));
            return err;
        }

        // 保存用户消息
        conversationService.saveMessage(userId, sessionId, "user", message, null);

        SseEmitter rawEmitter = new SseEmitter(600_000L);
        final String sid = sessionId;
        final Long uid = userId;

        rawEmitter.onTimeout(() -> log.warn("SSE 超时: userId={}", uid));
        rawEmitter.onError(ex -> log.error("SSE 错误: {}", ex.getMessage()));

        // 包装为线程安全的 ProgressEventEmitter
        ProgressEventEmitter emitter = new ProgressEventEmitter(rawEmitter);

        // 后台执行 SSE 流式编排（新架构：分步执行 + 实时推送进度）
        CompletableFuture.runAsync(() -> {
            try {
                // === 阶段通知：开始 ===
                emitter.onPhase("START");

                // === 使用 EducationGraphBuilder 的分步执行模式（实时推送进度） ===
                // executeWithProgress 现在返回 EducationState，以便持久化完整回复
                EducationState finalState = graphBuilder.executeWithProgress(uid, message, sid, emitter);

                // === 完成 ===
                emitter.onDone();
                emitter.complete();

                // 保存 AI 回复到 MySQL — 使用真实的 textReply 内容 + 生成的资源列表
                String textReply;
                List<Map<String, Object>> streamResources = List.of();
                if (finalState != null) {
                    textReply = finalState.textReply();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> resources = (List<Map<String, Object>>)
                            finalState.value(EducationState.GENERATED_RESOURCES).orElse(List.of());
                    streamResources = resources;
                } else {
                    textReply = "AI 已处理（客户端断开）";
                }
                if (textReply == null || textReply.isBlank()) {
                    textReply = "AI 已处理";
                }
                conversationService.saveMessage(uid, sid, "assistant", textReply, null, streamResources);

            } catch (Exception e) {
                log.error("SSE 处理异常: {}", e.getMessage(), e);
                emitter.onError("AI 处理失败: " + e.getMessage());
                emitter.complete();
            }
        });

        return rawEmitter;
    }

    // ===== 辅助方法 =====

    private void sendError(SseEmitter emitter, String msg) {
        try {
            String safeMsg = (msg != null && !msg.isBlank()) ? msg : "未知错误";
            emitter.send(SseEmitter.event().name("error").data(safeMsg));
        } catch (IOException ignored) {}
    }

    // ===== 会话管理 API =====

    /**
     * 获取用户所有会话列表（从 conversation_history 聚合查询）
     *
     * GET /edu/ai/sessions
     * 返回: [{sessionId, title, lastActiveTime, messageCount}, ...]
     */
    @GetMapping("/sessions")
    public AjaxResult getSessions() {
        Long userId = SecurityUtils.getUserId();
        var sessions = conversationHistoryMapper.selectSessionsByUser(userId);
        return success(sessions);
    }

    /**
     * 新建会话 — 生成并返回新的 sessionId
     *
     * POST /edu/ai/session/new
     * 返回: {sessionId: "abc12345"}
     */
    @PostMapping("/session/new")
    public AjaxResult newSession() {
        String sessionId = conversationService.newSessionId();
        return success(Map.of("sessionId", sessionId));
    }

    /**
     * 删除会话 — 物理删除该会话下所有消息
     *
     * DELETE /edu/ai/session/{sessionId}
     */
    @DeleteMapping("/session/{sessionId}")
    public AjaxResult deleteSession(@PathVariable String sessionId) {
        Long userId = SecurityUtils.getUserId();
        int rows = conversationHistoryMapper.softDeleteSession(userId, sessionId);
        log.info("会话已删除: userId={}, sessionId={}, rows={}", userId, sessionId, rows);
        return success(Map.of("deleted", rows));
    }

    /**
     * 获取指定会话的对话历史
     * GET /edu/ai/conversation/{sessionId}
     */
    @GetMapping("/conversation/{sessionId}")
    public AjaxResult getConversation(@PathVariable String sessionId) {
        Long userId = SecurityUtils.getUserId();
        return success(conversationService.getMessages(userId, sessionId));
    }
}
