package com.ruoyi.system.ai.agent;

import com.ruoyi.system.ai.graph.EducationState;
import com.ruoyi.system.ai.utils.SseUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * 文本回复 Agent — 处理对话闲聊、概念讲解、答疑解惑
 *
 * 作为基础Agent，几乎所有意图都会启动。
 * 输出纯文本回复内容，由 Controller 层通过 SSE 流式推送给前端。
 *
 * @author edu-agent
 */
@Component
public class TextReplyAgent implements EduAgent {

    private static final Logger log = LoggerFactory.getLogger(TextReplyAgent.class);

    @Autowired
    private ChatModel chatModel;

    /** 流式模型 — 用于 generateStreaming() 实现真正的逐 token 流式输出 */
    @Autowired
    private StreamingChatModel streamingChatModel;

    /** 注入 ConversationService 以读取 Redis 短期记忆中的最近对话上下文 */
    @Autowired
    private com.ruoyi.system.ai.service.ConversationService conversationService;

    @Override public String agentName() { return "textReply"; }
    @Override public String displayName() { return "文本回复Agent"; }
    @Override public String agentRole() { return "处理对话闲聊、概念讲解、答疑解惑，是所有场景的基础Agent"; }
    @Override public String[] capabilities() { return new String[]{"文本问答", "概念讲解", "知识梳理", "学习引导"}; }
    @Override public String[] tools() { return new String[]{"ChatModel", "ConversationService"}; }

    /**
     * 生成文本回复
     *
     * @param state 当前教育状态（含画像、用户消息等）
     * @return 生成的文本回复内容
     */
    public String generate(EducationState state) {
        log.info("TextReplyAgent 开始生成文本回复...");
        String systemPrompt = buildSystemPrompt(state);
        String userPrompt = state.userMessage();

        try {
            return chatModel.chat(java.util.List.of(
                    dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                    dev.langchain4j.data.message.UserMessage.from(userPrompt)
            )).aiMessage().text();
        } catch (Exception e) {
            log.error("TextReplyAgent 生成失败: {}", e.getMessage());
            return "抱歉，我暂时无法回答这个问题，请稍后再试。";
        }
    }

    /**
     * 使用真正的流式 LLM（StreamingChatModel）逐 token 生成文本回复
     *
     * 与 generate() 不同，此方法不返回完整文本，而是通过回调实时推送每个 token。
     * 由 Controller 层调用，配合 ProgressEventEmitter 实现 SSE 实时流式输出。
     *
     * @param state      当前教育状态（含画像、用户消息等）
     * @param onToken    每收到一个 token 时回调（由 Controller 调用 emitter.sendToken）
     * @param onComplete 流式生成完成时回调（由 Controller 调用 emitter.sendTokenDone）
     * @param onError    流式生成出错时回调
     */
    public void generateStreaming(EducationState state,
                                  Consumer<String> onToken,
                                  Consumer<String> onComplete,
                                  Consumer<Throwable> onError) {
        log.info("TextReplyAgent 开始流式生成文本回复...");
        String systemPrompt = buildSystemPrompt(state);
        String userPrompt = state.userMessage();
        SseUtil.streamAsync(streamingChatModel, systemPrompt, userPrompt,
                onToken::accept, onComplete::accept, onError::accept);
    }

    /**
     * 构建 System Prompt — 注入画像摘要、本会话上下文、跨会话长期记忆
     *
     * 三层记忆注入：
     * 1. 画像摘要（结构化，从 profileJson/dimensions 提取关键维度）
     * 2. 本会话上下文（Redis 短期记忆，最近 20 条）
     * 3. 跨会话长期记忆（MySQL 全量记录，最近 5 条用户提问主题）
     */
    private String buildSystemPrompt(EducationState state) {
        String profileHint = buildProfileSummary(state);
        String intent = state.intent();

        // 第一层：本会话最近 20 条消息（Redis 短期记忆）
        String recentHistory = conversationService.getRecentMessages(
                state.userId(), state.sessionId(), 20);
        String historyBlock = "";
        if (recentHistory != null && !recentHistory.isEmpty()) {
            historyBlock = "\n\n## 本会话对话历史\n" + recentHistory;
        } else {
            // 新会话：注入跨会话长期记忆（最近 5 条用户提问，帮助 AI 了解学习背景）
            String crossContext = conversationService.getRecentCrossSessionContext(
                    state.userId(), 5);
            if (crossContext != null && !crossContext.isEmpty()) {
                historyBlock = "\n\n## 历史学习记录（跨会话记忆）\n" + crossContext;
            }
        }

        return String.format("""
            你是一个智能教育 AI 学习助手，名叫"学伴"。

            回答策略:
            1. 先理解学生的问题和困惑点
            2. 用简单易懂的语言解释核心概念
            3. 配合具体例子说明
            4. 如有代码示例，给出关键代码并加注释
            5. 提出引导性追问，检验理解程度

            回复风格: 鼓励性、循序渐进、适应当前水平。正文使用中文。
            当前对话意图: %s
            %s
            %s
            """, intent, profileHint, historyBlock);
    }

    /**
     * 根据画像构建结构化摘要（替代截断原始 JSON 的做法）
     *
     * 从 profileJson 的 dimensions 中提取关键维度信息，
     * 帮助 AI 在对话中适配学习者的知识水平、认知风格和薄弱点。
     */
    private String buildProfileSummary(EducationState state) {
        String profile = state.profileJson();
        if (profile == null || profile.equals("{}") || profile.length() < 20) {
            return "用户尚未建立学习画像，请引导用户多聊学习话题以建立画像。";
        }

        try {
            var json = com.alibaba.fastjson2.JSON.parseObject(profile);
            var dims = json.getJSONObject("dimensions");
            if (dims == null || dims.isEmpty()) {
                return "用户学习画像维度数据尚未建立，请引导用户多聊学习话题。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("用户学习画像摘要:\n");

            // 知识基础（影响讲解深度）
            var klevel = dims.getJSONObject("knowledgeLevel");
            if (klevel != null && klevel.get("value") != null) {
                sb.append("- 知识基础: ").append(klevel.get("value")).append("\n");
            }

            // 认知风格（影响教学方式）
            var cstyle = dims.getJSONObject("cognitiveStyle");
            if (cstyle != null && cstyle.get("value") != null) {
                sb.append("- 认知风格: ").append(cstyle.get("value"));
                sb.append("（视觉型→多用图表/导图，听觉型→多用比喻/口语化讲解，动手型→提供代码/练习）\n");
            }

            // 薄弱点（需要重点讲解的方向）
            var weak = dims.getJSONObject("weakPoints");
            if (weak != null && weak.get("value") != null) {
                sb.append("- 薄弱知识领域: ").append(weak.get("value")).append("\n");
            }

            // 优势领域（可以跳过的内容）
            var strengths = dims.getJSONObject("strengths");
            if (strengths != null && strengths.get("value") != null) {
                sb.append("- 已掌握领域: ").append(strengths.get("value")).append("\n");
            }

            // 学习目标（对齐方向）
            var goal = dims.getJSONObject("learningGoal");
            if (goal != null && goal.get("value") != null) {
                sb.append("- 学习目标: ").append(goal.get("value")).append("\n");
            }

            // 资源偏好
            var pref = dims.getJSONObject("preferredResourceType");
            if (pref != null && pref.get("value") != null) {
                sb.append("- 资源偏好: ").append(pref.get("value")).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            // 解析失败时回退到截断方式
            String shortProfile = profile.length() > 300 ? profile.substring(0, 300) + "..." : profile;
            return "用户当前学习画像: " + shortProfile +
                   "\n请根据画像调整讲解深度和方式（如知识水平、认知风格、薄弱点等）。";
        }
    }
}
