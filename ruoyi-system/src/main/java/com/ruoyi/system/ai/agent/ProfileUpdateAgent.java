package com.ruoyi.system.ai.agent;

import com.ruoyi.system.ai.graph.EducationState;
import com.ruoyi.system.ai.tool.ProfileTool;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 画像更新 Agent — 6维学习画像
 *
 * 从对话历史中增量抽取学习特征：
 *   knowledgeLevel, cognitiveStyle, weakPoints, strengths, learningGoal, preferredResourceType
 *
 * 含冷却期和意图过滤，避免高频 LLM 调用。
 *
 * @author edu-agent
 */
@Component
public class ProfileUpdateAgent implements EduAgent {

    private static final Logger log = LoggerFactory.getLogger(ProfileUpdateAgent.class);

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ProfileTool profileTool;

    @Autowired
    private com.ruoyi.system.ai.service.ConversationService conversationService;

    @Override public String agentName() { return "profileUpdate"; }
    @Override public String displayName() { return "画像更新Agent"; }
    @Override public String agentRole() { return "从对话中增量抽取6维学习特征，构建动态学习画像"; }
    @Override public String[] capabilities() { return new String[]{"特征抽取", "画像构建"}; }
    @Override public String[] tools() { return new String[]{"ChatModel", "ProfileTool", "ConversationService"}; }

    /**
     * 执行画像更新 — 带冷却期过滤 + 意图过滤
     */
    public String execute(EducationState state, String currentIntent) {
        Long uid = state.userId();

        // === 条件 1: 闲聊不更新画像 ===
        if ("GENERAL_CHAT".equals(currentIntent)) {
            log.debug("ProfileUpdateAgent 跳过更新: 意图为 GENERAL_CHAT");
            return profileTool.loadProfile(uid);
        }

        // === 条件 2: 画像查询不更新 ===
        if ("PROFILE_INQUIRY".equals(currentIntent)) {
            log.debug("ProfileUpdateAgent 跳过更新: 意图为 PROFILE_INQUIRY");
            return profileTool.loadProfile(uid);
        }

        // === 条件 3: 冷却期检查（默认 5 分钟） ===
        String existProfile = profileTool.loadProfile(uid);
        if (profileTool.isInCooldown(existProfile, 5)) {
            log.debug("ProfileUpdateAgent 跳过更新: 在冷却期内（5分钟）");
            return existProfile;
        }

        log.info("ProfileUpdateAgent 开始更新画像: userId={}, intent={}", uid, currentIntent);

        try {
            // 1. 获取最近对话历史
            String recentContext = conversationService.getRecentContext(uid, 10);

            // 2. LLM 增量抽取新特征
            String extractionPrompt = buildExtractionPrompt(existProfile, recentContext);
            String extractedFeatures = chatModel.chat(extractionPrompt);

            // 3. 合并画像
            String mergedProfile = profileTool.mergeProfile(existProfile, extractedFeatures);

            // 4. 保存画像
            profileTool.saveProfile(uid, mergedProfile);

            log.info("ProfileUpdateAgent 画像更新完成: userId={}, profileLength={}",
                    uid, mergedProfile != null ? mergedProfile.length() : 0);
            return mergedProfile;

        } catch (Exception e) {
            log.error("ProfileUpdateAgent 画像更新失败: userId={}, error={}", uid, e.getMessage(), e);
            return profileTool.loadProfile(uid);
        }
    }

    /**
     * 兼容旧签名 — 无意图参数时调用
     */
    @Deprecated
    public String execute(EducationState state) {
        return execute(state, state.intent());
    }

    /**
     * 构建画像抽取 Prompt — 6维度模型
     */
    private String buildExtractionPrompt(String existProfile, String recentContext) {
        return String.format("""
            你是学习特征分析专家。从对话中增量抽取学习者特征。

            ## 已有画像
            %s

            ## 最近对话
            %s

            ## 抽取维度（6个）
            - knowledgeLevel: 知识基础分级（入门/基础/中等/进阶/专家）
            - cognitiveStyle: 认知风格（视觉型/听觉型/动手型/综合型）
            - weakPoints: 薄弱知识点 [{topic, errorCount}]
            - strengths: 优势知识点 [{topic, mastery, evidence}]
            - learningGoal: 学习目标
            - preferredResourceType: 偏好资源类型 [{type, score:0-1}]

            ## 要求
            - 只提取本轮对话中新出现的特征
            - 每条特征须提供 evidence（从对话中引用的原文或推断依据）
            - confidence 取值 0.0-1.0
            - 没有新发现时输出 {}
            - 输出纯 JSON（不要包含 ```json 包裹）
            {"dimensions":{"维度名":{"value":...,"confidence":0.8,"evidence":"..."}}}
            """, existProfile, recentContext);
    }
}
