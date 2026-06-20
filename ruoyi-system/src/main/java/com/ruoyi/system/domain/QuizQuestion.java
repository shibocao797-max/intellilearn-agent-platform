package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 题库表 quiz_question 实体
 *
 * 存储 AI 生成和手动录入的练习题，支持选择题/填空题/简答题/代码题。
 * 题目通过 knowledge_points JSON 字段关联知识点。
 *
 * @author edu-agent
 */
public class QuizQuestion extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 题型: single_choice / multi_choice / fill_blank / short_answer / code */
    private String questionType;

    /** 题干文本 */
    private String stem;

    /** 选项JSON（选择题用，如 ["A. xxx", "B. xxx"]） */
    private String options;

    /** 正确答案 */
    private String answer;

    /** 答案解析 */
    private String explanation;

    /** 难度: easy / medium / hard */
    private String difficulty;

    /** 关联知识点 JSON 数组 */
    private String knowledgePoints;

    /** 来源: AI_GENERATED / MANUAL / IMPORTED */
    private String source;

    /** 历史正确率（动态更新） */
    private java.math.BigDecimal correctRate;

    /** 状态: 0=启用 1=停用 */
    private String status;

    /** 创建者（默认 "AI"） */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    // ===== getters/setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }

    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getKnowledgePoints() { return knowledgePoints; }
    public void setKnowledgePoints(String knowledgePoints) { this.knowledgePoints = knowledgePoints; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public java.math.BigDecimal getCorrectRate() { return correctRate; }
    public void setCorrectRate(java.math.BigDecimal correctRate) { this.correctRate = correctRate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String getCreateBy() { return createBy; }
    @Override
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    @Override
    public Date getCreateTime() { return createTime; }
    @Override
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    @Override
    public Date getUpdateTime() { return updateTime; }
    @Override
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("questionType", getQuestionType())
            .append("stem", getStem())
            .append("difficulty", getDifficulty())
            .append("source", getSource())
            .toString();
    }
}
