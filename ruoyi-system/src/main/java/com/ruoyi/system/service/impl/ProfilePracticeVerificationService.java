package com.ruoyi.system.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.ruoyi.system.ai.tool.ProfileTool;
import com.ruoyi.system.domain.QuizPracticeItem;
import com.ruoyi.system.domain.QuizQuestion;
import com.ruoyi.system.mapper.QuizPracticeSessionMapper;
import com.ruoyi.system.mapper.QuizQuestionMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Builds verified mastery only from completed, objectively graded practice. */
@Service
public class ProfilePracticeVerificationService
{
    private static final Logger log = LoggerFactory.getLogger(ProfilePracticeVerificationService.class);
    private static final int MIN_TOPIC_SAMPLE_COUNT = 5;
    private static final Set<String> OBJECTIVE_TYPES = Set.of("single_choice", "multi_choice", "fill_blank");

    @Autowired
    private QuizPracticeSessionMapper practiceSessionMapper;

    @Autowired
    private QuizQuestionMapper questionMapper;

    @Autowired
    private ProfileTool profileTool;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void verifyCompletedSession(Long userId, String sessionId)
    {
        List<QuizPracticeItem> items = practiceSessionMapper.selectItemsBySessionIdAndUserId(sessionId, userId);
        Map<String, TopicScore> scores = new LinkedHashMap<>();
        boolean hasUnmappedObjective = false;
        for (QuizPracticeItem item : items) {
            if (!"SUBMITTED".equals(item.getState()) || Boolean.TRUE.equals(item.getPendingReview())
                    || item.getIsCorrect() == null) continue;
            QuizQuestion question = questionFromSnapshot(item);
            if (question == null || !OBJECTIVE_TYPES.contains(question.getQuestionType())) continue;
            String topic = firstKnowledgePoint(question.getKnowledgePoints());
            if (topic == null) {
                hasUnmappedObjective = true;
                continue;
            }
            TopicScore score = scores.computeIfAbsent(topic, ignored -> new TopicScore());
            score.sampleCount++;
            if (Boolean.TRUE.equals(item.getIsCorrect())) score.correctCount++;
        }

        Map.Entry<String, TopicScore> best = scores.size() == 1 && !hasUnmappedObjective
                ? scores.entrySet().iterator().next() : null;
        if (best != null && best.getValue().sampleCount < MIN_TOPIC_SAMPLE_COUNT) best = null;
        if (best == null) {
            log.debug("练习画像验证跳过: userId={}, sessionId={}, 练习必须只有一个知识点且至少 {} 道客观题",
                    userId, sessionId, MIN_TOPIC_SAMPLE_COUNT);
            return;
        }

        TopicScore score = best.getValue();
        profileTool.applyVerifiedMastery(userId, best.getKey(), score.sampleCount,
                score.correctCount, sessionId);
    }

    private static String firstKnowledgePoint(String raw)
    {
        if (raw == null || raw.isBlank()) return null;
        try {
            JSONArray values = JSON.parseArray(raw);
            if (values == null) return null;
            for (Object value : values) {
                if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
            }
        } catch (Exception ignored) {
            // Malformed legacy knowledge-point data is not verification evidence.
        }
        return null;
    }

    private QuizQuestion questionFromSnapshot(QuizPracticeItem item)
    {
        if (item.getQuestionSnapshotJson() != null && !item.getQuestionSnapshotJson().isBlank()) {
            try { return JSON.parseObject(item.getQuestionSnapshotJson(), QuizQuestion.class); }
            catch (Exception ignored) { }
        }
        return item.getQuestionId() == null ? null : questionMapper.selectQuestionById(item.getQuestionId());
    }

    private static final class TopicScore
    {
        private int sampleCount;
        private int correctCount;
    }
}
