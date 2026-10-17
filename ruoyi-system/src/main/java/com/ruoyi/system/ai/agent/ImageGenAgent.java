package com.ruoyi.system.ai.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.ruoyi.system.ai.graph.EducationState;
import com.ruoyi.system.ai.media.AgnesMediaClient;
import com.ruoyi.system.ai.tool.ResourceTool;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Image generation Agent backed by Agnes Image API.
 */
@Component
public class ImageGenAgent implements ResourceGenAgent {

    private static final Logger log = LoggerFactory.getLogger(ImageGenAgent.class);

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private AgnesMediaClient agnesMediaClient;

    @Autowired
    private ResourceTool resourceTool;

    @Override public String agentName() { return "imageGen"; }
    @Override public String resourceType() { return "image"; }
    @Override public String displayName() { return "教学配图"; }
    @Override public String agentRole() { return "生成教学插图、知识点示意图、封面图和视频关键帧"; }
    @Override public String[] capabilities() { return new String[]{"文生图", "教学插图", "关键帧", "视觉化资源"}; }
    @Override public String[] tools() { return new String[]{"ChatModel", "AgnesImageAPI", "ResourceTool"}; }

    @Override
    public boolean shouldGenerate(EducationState state) {
        String message = state.userMessage();
        String profile = state.profileJson();
        return containsAny(message, "图片", "插图", "图解", "示意图", "配图", "封面", "关键帧", "可视化")
                || containsAny(profile, "视觉", "visual");
    }

    @Override
    public String buildSystemPrompt(EducationState state) {
        return String.format("""
            你是教学视觉设计师。请把学习主题转成适合 AI 文生图模型的中文生成提示词。

            学生画像:
            %s

            要求:
            - 画面必须服务知识理解，不做无关装饰。
            - 适合课堂投屏和资源卡片展示。
            - 包含主体、关系、构图、风格、文字限制、避免项。
            - 不要要求图片里出现大量中文小字。

            只输出一段最终图像 prompt，不要 Markdown。
            """, state.profileJson());
    }

    @Override
    public String buildUserPrompt(EducationState state) {
        return String.format("主题: %s\n用户问题: %s", extractTopic(state), state.userMessage());
    }

    @Override
    public String generate(EducationState state) {
        log.info("ImageGenAgent starts image generation.");
        String imagePrompt = chatModel.chat(List.of(
                dev.langchain4j.data.message.SystemMessage.from(buildSystemPrompt(state)),
                dev.langchain4j.data.message.UserMessage.from(buildUserPrompt(state))
        )).aiMessage().text();
        Map<String, Object> media = agnesMediaClient.generateImage(imagePrompt, "1024x768");
        media.put("topic", extractTopic(state));
        media.put("prompt", imagePrompt);
        media.put("usage", "教学插图/知识点示意图/视频关键帧");
        return JSON.toJSONString(media, JSONWriter.Feature.PrettyFormat);
    }

    @Override
    public String persist(EducationState state, String result) {
        String imageUrl = extractJsonText(result, "url");
        Long resourceId = resourceTool.saveMediaResource(
                extractTopic(state) + " - 教学配图",
                result,
                "image",
                "教学配图,image,agnes,keyframe,visual",
                state.userId(),
                imageUrl,
                imageUrl,
                null);
        log.info("ImageGenAgent persisted resource: id={}, userId={}", resourceId, state.userId());
        return "image:" + resourceId;
    }

    @Override
    public double validate(String result) {
        if (result == null || result.isBlank()) return 0.0;
        double score = 0.3;
        if (result.contains("\"provider\":\"agnes\"") || result.contains("\"provider\": \"agnes\"")) score += 0.15;
        if (result.contains("\"prompt\"")) score += 0.2;
        if (result.contains("\"url\"") || result.contains("\"b64_json\"")) score += 0.25;
        if (result.contains("\"completed\"")) score += 0.1;
        if (result.contains("config_missing")) score = 0.45;
        return Math.min(1.0, score);
    }

    @Override
    public String regenerate(EducationState state, String previousResult, Map<String, Object> review) {
        if (previousResult != null && previousResult.contains("config_missing")) {
            return previousResult;
        }
        return ResourceGenAgent.super.regenerate(state, previousResult, review);
    }

    private String extractTopic(EducationState state) {
        try {
            String topic = JSON.parseObject(state.entitiesJson()).getString("topic");
            if (topic != null && !topic.isBlank()) return topic;
        } catch (Exception ignored) {}
        return state.userMessage();
    }

    private String extractJsonText(String json, String key) {
        try {
            String value = JSON.parseObject(json).getString(key);
            return value == null || value.isBlank() ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) return false;
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
