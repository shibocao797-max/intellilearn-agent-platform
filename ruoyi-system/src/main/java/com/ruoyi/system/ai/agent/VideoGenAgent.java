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
 * Real video generation Agent backed by Agnes Video async API.
 */
@Component
public class VideoGenAgent implements ResourceGenAgent {

    private static final Logger log = LoggerFactory.getLogger(VideoGenAgent.class);

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private AgnesMediaClient agnesMediaClient;

    @Autowired
    private ResourceTool resourceTool;

    @Override public String agentName() { return "videoGen"; }
    @Override public String resourceType() { return "video"; }
    @Override public String displayName() { return "教学视频"; }
    @Override public String agentRole() { return "提交真实教学视频生成任务，并保存 video_id、状态和视频 URL"; }
    @Override public String[] capabilities() { return new String[]{"文生视频", "图生视频", "视频任务轮询", "多模态教学资源"}; }
    @Override public String[] tools() { return new String[]{"ChatModel", "AgnesVideoAPI", "ResourceTool"}; }

    @Override
    public boolean shouldGenerate(EducationState state) {
        return containsAny(state.userMessage(), "真实视频", "教学视频", "短视频", "视频文件", "mp4", "文生视频", "图生视频");
    }

    @Override
    public String buildSystemPrompt(EducationState state) {
        return String.format("""
            你是教学短视频导演。请把学习主题转成适合文生视频模型的中文视频 prompt。

            学生画像:
            %s

            要求:
            - 输出 5 秒左右的课堂教学短视频描述。
            - 包含主体、镜头运动、动态过程、画面风格、避免项。
            - 画面必须解释知识点，不做广告片或纯氛围镜头。
            - 不要要求视频中出现大量小字。

            只输出一段最终视频 prompt，不要 Markdown。
            """, state.profileJson());
    }

    @Override
    public String buildUserPrompt(EducationState state) {
        return String.format("主题: %s\n用户问题: %s", extractTopic(state), state.userMessage());
    }

    @Override
    public String generate(EducationState state) {
        log.info("VideoGenAgent starts video task.");
        String videoPrompt = chatModel.chat(List.of(
                dev.langchain4j.data.message.SystemMessage.from(buildSystemPrompt(state)),
                dev.langchain4j.data.message.UserMessage.from(buildUserPrompt(state))
        )).aiMessage().text();
        Map<String, Object> media = agnesMediaClient.createVideo(videoPrompt, null);
        media.put("topic", extractTopic(state));
        media.put("prompt", videoPrompt);
        media.put("workflow", "async_video_task");
        media.put("async", true);
        media.put("userMessage", "视频生成任务已创建，完成后可通过 videoId 查询状态。");
        return JSON.toJSONString(media, JSONWriter.Feature.PrettyFormat);
    }

    @Override
    public String persist(EducationState state, String result) {
        String videoUrl = extractJsonText(result, "videoUrl");
        Long resourceId = resourceTool.saveMediaResource(
                extractTopic(state) + " - 教学视频",
                result,
                "video",
                "教学视频,video,agnes,multimodal,async",
                state.userId(),
                videoUrl,
                null,
                "约5秒");
        log.info("VideoGenAgent persisted resource: id={}, userId={}", resourceId, state.userId());
        return "video:" + resourceId;
    }

    @Override
    public double validate(String result) {
        if (result == null || result.isBlank()) return 0.0;
        double score = 0.35;
        if (result.contains("\"provider\":\"agnes\"") || result.contains("\"provider\": \"agnes\"")) score += 0.15;
        if (result.contains("\"prompt\"")) score += 0.15;
        if (result.contains("\"videoId\"")) score += 0.2;
        if (result.contains("\"videoUrl\"")) score += 0.15;
        if (result.contains("config_missing")) score = 0.45;
        if (result.contains("\"fallback\"")) score = 0.45;
        if (result.contains("\"pending\"") || result.contains("\"submitted\"")) score = Math.max(score, 0.72);
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
