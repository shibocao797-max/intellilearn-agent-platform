package com.ruoyi.system.ai.tool;

import com.ruoyi.system.domain.QuizQuestion;
import com.ruoyi.system.mapper.QuizQuestionMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 题库工具 — 练习题写入 quiz_question 表
 *
 * @author edu-agent
 */
@Component
public class QuizTool {

    private static final Logger log = LoggerFactory.getLogger(QuizTool.class);

    @Autowired
    private QuizQuestionMapper quizQuestionMapper;

    /**
     * 批量保存生成的题目到 quiz_question 表
     *
     * LLM 返回的 JSON 可能被 ```json ... ``` 包裹，本方法做了容错提取。
     *
     * @param questionsJson LLM 返回的 JSON 数组格式题目列表
     * @return 成功保存的数量
     */
    public int saveQuestions(String questionsJson) {
        return saveQuestionsAndReturnIds(questionsJson).size();
    }

    /**
     * 批量保存题目并返回本次保存的题目 ID。
     *
     * 该方法只使用局部变量保存 ID，避免 Spring 单例 Bean 在并发请求下互相覆盖。
     */
    public List<Long> saveQuestionsAndReturnIds(String questionsJson) {
        List<Long> savedIds = new ArrayList<>();
        if (questionsJson == null || questionsJson.isBlank()) {
            return savedIds;
        }
        // 容错：去掉 markdown 代码块包裹
        String cleaned = questionsJson.trim();
        if (cleaned.startsWith("```")) {
            int start = cleaned.indexOf("\n") + 1;
            int end = cleaned.lastIndexOf("```");
            if (start > 0 && end > start) {
                cleaned = cleaned.substring(start, end).trim();
            }
        }
        // 提取第一个 [ 到最后一个 ] 之间的 JSON 数组
        int arrStart = cleaned.indexOf('[');
        int arrEnd = cleaned.lastIndexOf(']');
        if (arrStart >= 0 && arrEnd > arrStart) {
            cleaned = cleaned.substring(arrStart, arrEnd + 1);
        }

        try {
            JSONArray arr = JSON.parseArray(cleaned);
            if (arr == null || arr.isEmpty()) {
                log.warn("题目 JSON 解析为空数组");
                return savedIds;
            }
            for (Object obj : arr) {
                JSONObject qj = (JSONObject) obj;
                // 跳过缺少必填字段的题目
                if (!qj.containsKey("stem") || !qj.containsKey("answer")) {
                    log.debug("跳过不完整题目: {}", qj);
                    continue;
                }
                QuizQuestion q = new QuizQuestion();
                q.setQuestionType(qj.getString("type"));
                q.setStem(qj.getString("stem"));
                // options 是 JSON 数组，直接存 JSON 字符串
                Object opts = qj.get("options");
                if (opts instanceof JSONArray) {
                    q.setOptions(((JSONArray) opts).toJSONString());
                } else if (opts != null) {
                    q.setOptions(opts.toString());
                }
                q.setAnswer(qj.getString("answer"));
                q.setExplanation(qj.getString("explanation"));
                q.setDifficulty(qj.getString("difficulty"));
                // knowledgePoints 是 JSON 数组，直接存 JSON 字符串
                Object kps = qj.get("knowledgePoints");
                if (kps instanceof JSONArray) {
                    q.setKnowledgePoints(((JSONArray) kps).toJSONString());
                } else if (kps != null) {
                    q.setKnowledgePoints(kps.toString());
                }
                q.setSource("AI_GENERATED");
                q.setStatus("0");
                q.setCreateBy("AI");
                quizQuestionMapper.insertQuestion(q);
                savedIds.add(q.getId());  // insertQuestion 配置了 useGeneratedKeys，id 自动回填
            }
            log.info("题目保存成功: count={}, ids={}", savedIds.size(), savedIds);
            return savedIds;
        } catch (Exception e) {
            log.warn("题目 JSON 解析失败: {}", e.getMessage());
            return savedIds;
        }
    }

    /**
     * 根据难度查询已有题目
     *
     * @param knowledgePoints 知识点（当前版本暂做简单匹配）
     * @param difficulty      难度: easy/medium/hard（null=不限）
     * @param limit           最大条数
     * @return JSON 数组字符串
     */
    public String findQuestions(String knowledgePoints, String difficulty, int limit) {
        var list = quizQuestionMapper.selectByDifficulty(difficulty, limit);
        return JSON.toJSONString(list);
    }

    /**
     * 获取最近一次 saveQuestions 保存的题目 ID 列表（逗号分隔）
     * 供 QuizGenAgent.persist() 返回给前端，用于卡片点击跳转
     */
    public String getLastSavedIds() {
        log.warn("getLastSavedIds 已废弃：请使用 saveQuestionsAndReturnIds 避免并发串扰");
        return "";
    }
}
