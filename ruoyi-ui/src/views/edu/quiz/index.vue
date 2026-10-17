<template>
  <div class="app-container edu-quiz">
    <el-row :gutter="16" class="quiz-stats">
      <el-col :xs="24" :sm="8">
        <div class="stat-box">
          <div class="stat-value">{{ quizStats.total || 0 }}</div>
          <div class="stat-label">累计答题</div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8">
        <div class="stat-box success">
          <div class="stat-value">{{ quizStats.correct || 0 }}</div>
          <div class="stat-label">回答正确</div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8">
        <div class="stat-box warning">
          <div class="stat-value">{{ formatSeconds(quizStats.avgTimeSec) }}</div>
          <div class="stat-label">平均用时</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <!-- 左侧题目列表 -->
      <el-col :xs="24" :md="8">
        <el-card>
          <div slot="header">
            <span>题目列表</span>
            <el-button style="float: right; padding: 3px 0" type="text" @click="loadQuestions">
              刷新
            </el-button>
          </div>
          <div class="question-list">
            <div
              v-for="q in questions"
              :key="q.id"
              :class="['question-item', { active: currentQuestion && currentQuestion.id === q.id }]"
              @click="selectQuestion(q)"
            >
              <div class="q-type">
                <el-tag size="mini" :type="getTypeTag(q.questionType)">
                  {{ getTypeName(q.questionType) }}
                </el-tag>
              </div>
              <div class="q-stem">{{ q.stem }}</div>
              <div class="q-meta">
                <el-tag size="mini" :type="getDifficultyTag(q.difficulty)">
                  {{ getDifficultyName(q.difficulty) }}
                </el-tag>
              </div>
            </div>
            <div v-if="questions.length === 0" class="empty-tip">暂无题目</div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧答题区域 -->
      <el-col :xs="24" :md="16">
        <el-card v-if="currentQuestion" v-loading="questionLoading">
          <div slot="header">
            <span>{{ getTypeName(currentQuestion.questionType) }}</span>
            <el-tag size="mini" :type="getDifficultyTag(currentQuestion.difficulty)" style="margin-left: 8px;">
              {{ getDifficultyName(currentQuestion.difficulty) }}
            </el-tag>
            <span v-if="currentQuestion.correctRate !== null && currentQuestion.correctRate !== undefined" class="correct-rate">
              历史正确率 {{ formatRate(currentQuestion.correctRate) }}
            </span>
          </div>

          <!-- 题干 -->
          <div class="question-stem">{{ currentQuestion.stem }}</div>
          <div v-if="parseKnowledgePoints(currentQuestion.knowledgePoints).length" class="knowledge-points">
            <el-tag
              v-for="point in parseKnowledgePoints(currentQuestion.knowledgePoints)"
              :key="point"
              size="mini"
              type="info"
            >
              {{ point }}
            </el-tag>
          </div>

          <!-- 选项（选择题） -->
          <div v-if="isChoiceType(currentQuestion.questionType)" class="options">
            <el-radio-group v-if="isSingleChoice(currentQuestion.questionType)" v-model="userAnswer">
              <el-radio
                v-for="(opt, idx) in parseOptions(currentQuestion.options)"
                :key="idx"
                :label="opt.key"
                class="option-item"
              >
                <span class="opt-key">{{ opt.key }}.</span>
                <span class="opt-value">{{ opt.value }}</span>
              </el-radio>
            </el-radio-group>

            <el-checkbox-group v-else-if="isMultiChoice(currentQuestion.questionType)" v-model="userAnswerMulti">
              <el-checkbox
                v-for="(opt, idx) in parseOptions(currentQuestion.options)"
                :key="idx"
                :label="opt.key"
                class="option-item"
              >
                <span class="opt-key">{{ opt.key }}.</span>
                <span class="opt-value">{{ opt.value }}</span>
              </el-checkbox>
            </el-checkbox-group>
          </div>

          <!-- 填空题/简答题 -->
          <div v-else class="text-answer">
            <el-input
              v-model="userAnswer"
              type="textarea"
              :rows="3"
              placeholder="请输入你的答案..."
            />
          </div>

          <!-- 提交按钮 -->
          <div class="action-bar">
            <el-button type="primary" :loading="submitting" @click="handleSubmit">
              提交答案
            </el-button>
            <el-button @click="handleReset">重置</el-button>
          </div>

          <!-- 答题结果 -->
          <div v-if="showResult" class="result-section">
            <el-alert
              :title="result.isCorrect ? '回答正确！' : '回答错误'"
              :type="result.isCorrect ? 'success' : 'error'"
              show-icon
              :closable="false"
            />
            <div class="result-detail">
              <div class="result-item">
                <span class="label">正确答案：</span>
                <span class="value correct">{{ result.correctAnswer }}</span>
              </div>
              <div v-if="result.explanation" class="result-item">
                <span class="label">解析：</span>
                <span class="value">{{ result.explanation }}</span>
              </div>
            </div>

            <!-- AI 引导式讲解 -->
            <div v-if="aiExplanation" class="ai-explanation">
              <div class="ai-header">
                <span>🤖 AI 引导式讲解</span>
              </div>
              <div class="ai-content">{{ aiExplanation }}</div>
            </div>
            <el-button
              v-if="!aiExplanation"
              type="text"
              icon="el-icon-chat-dot-round"
              :loading="loadingExplanation"
              @click="loadAiExplanation"
            >
              获取 AI 讲解
            </el-button>
          </div>
        </el-card>

        <el-card v-else>
          <div class="empty-state">
            <i class="el-icon-edit-outline" style="font-size: 48px; color: #ccc;"></i>
            <p>请从左侧选择题目开始练习</p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="record-card" v-loading="recordsLoading">
      <div slot="header">
        <span>最近答题记录</span>
        <el-button style="float: right; padding: 3px 0" type="text" @click="loadRecords">刷新</el-button>
      </div>
      <el-table :data="records" size="small" border>
        <el-table-column prop="answerTime" label="答题时间" min-width="160" />
        <el-table-column prop="questionId" label="题目 ID" width="100" />
        <el-table-column prop="userAnswer" label="我的答案" min-width="160" show-overflow-tooltip />
        <el-table-column prop="isCorrect" label="结果" width="100">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.isCorrect ? 'success' : 'danger'">
              {{ scope.row.isCorrect ? '正确' : '错误' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="得分" width="90" />
        <el-table-column prop="timeSpentSec" label="用时" width="100">
          <template slot-scope="scope">{{ formatSeconds(scope.row.timeSpentSec) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { aiExplain, getQuizDetail, getQuizList, getQuizRecords, getQuizStats, submitAnswer } from '@/api/edu/quiz'

export default {
  name: 'EduQuiz',
  data() {
    return {
      questions: [],
      currentQuestion: null,
      questionLoading: false,
      userAnswer: '',
      userAnswerMulti: [],
      submitting: false,
      showResult: false,
      result: {},
      aiExplanation: '',
      loadingExplanation: false,
      quizStats: {},
      records: [],
      recordsLoading: false
    }
  },
  created() {
    this.loadQuestions()
    this.loadStats()
    this.loadRecords()
  },
  methods: {
    async loadQuestions() {
      try {
        const res = await getQuizList()
        this.questions = res.data || []
      } catch (e) {
        console.error('加载题目失败', e)
      }
    },

    async selectQuestion(q) {
      this.currentQuestion = q
      this.userAnswer = ''
      this.userAnswerMulti = []
      this.showResult = false
      this.result = {}
      this.aiExplanation = ''
      this.questionLoading = true
      try {
        const res = await getQuizDetail(q.id)
        this.currentQuestion = res.data || q
      } catch (e) {
        this.$message.error('加载题目详情失败')
      } finally {
        this.questionLoading = false
      }
    },

    isChoiceType(type) {
      return this.isSingleChoice(type) || this.isMultiChoice(type)
    },

    normalizeQuestionType(type) {
      const map = {
        single: 'single_choice',
        choice: 'single_choice',
        radio: 'single_choice',
        singleChoice: 'single_choice',
        multiple: 'multi_choice',
        multi: 'multi_choice',
        checkbox: 'multi_choice',
        multiple_choice: 'multi_choice',
        multiChoice: 'multi_choice',
        fill: 'fill_blank',
        blank: 'fill_blank',
        fillBlank: 'fill_blank',
        short: 'short_answer',
        answer: 'short_answer',
        shortAnswer: 'short_answer'
      }
      return map[type] || type
    },

    isSingleChoice(type) {
      return this.normalizeQuestionType(type) === 'single_choice'
    },

    isMultiChoice(type) {
      return this.normalizeQuestionType(type) === 'multi_choice'
    },

    parseOptions(options) {
      if (!options) return []
      try {
        let parsed = options
        if (typeof options === 'string') {
          parsed = JSON.parse(options)
        }
        return (parsed || []).map((item, idx) => {
          if (typeof item === 'string') {
            const match = item.match(/^([A-Z])[\.\、\s]+(.+)$/)
            return {
              key: match ? match[1] : String.fromCharCode(65 + idx),
              value: match ? match[2] : item
            }
          }
          return {
            key: item.key || item.label || String.fromCharCode(65 + idx),
            value: item.value || item.text || item.content || ''
          }
        })
      } catch (e) {
        return []
      }
    },

    async handleSubmit() {
      const answer = this.isMultiChoice(this.currentQuestion.questionType)
        ? this.userAnswerMulti.join(',')
        : this.userAnswer

      if (!answer) {
        this.$message.warning('请输入答案')
        return
      }

      this.submitting = true
      try {
        const res = await submitAnswer({
          questionId: this.currentQuestion.id,
          userAnswer: answer
        })
        this.result = res.data
        this.showResult = true
        await Promise.all([this.loadStats(), this.loadRecords()])
      } catch (e) {
        this.$message.error('提交失败')
      } finally {
        this.submitting = false
      }
    },

    handleReset() {
      this.userAnswer = ''
      this.userAnswerMulti = []
      this.showResult = false
      this.result = {}
      this.aiExplanation = ''
    },

    async loadAiExplanation() {
      this.loadingExplanation = true
      try {
        const res = await aiExplain({
          questionId: this.currentQuestion.id
        })
        this.aiExplanation = res.data.explanation
      } catch (e) {
        this.$message.error('获取讲解失败')
      } finally {
        this.loadingExplanation = false
      }
    },

    async loadStats() {
      try {
        const res = await getQuizStats()
        this.quizStats = res.data || {}
      } catch (e) {
        console.error('加载题库统计失败', e)
      }
    },

    async loadRecords() {
      this.recordsLoading = true
      try {
        const res = await getQuizRecords({ limit: 20 })
        this.records = res.data || []
      } catch (e) {
        console.error('加载答题记录失败', e)
      } finally {
        this.recordsLoading = false
      }
    },

    parseKnowledgePoints(points) {
      if (!points) return []
      try {
        const parsed = typeof points === 'string' ? JSON.parse(points) : points
        return Array.isArray(parsed) ? parsed : []
      } catch (e) {
        return []
      }
    },

    formatSeconds(value) {
      if (!value) return '-'
      if (value < 60) return Math.round(value) + '秒'
      return Math.round(value / 60) + '分钟'
    },

    formatRate(value) {
      const n = Number(value)
      if (Number.isNaN(n)) return value
      return n <= 1 ? Math.round(n * 100) + '%' : Math.round(n) + '%'
    },

    getTypeName(type) {
      const normalized = this.normalizeQuestionType(type)
      const map = {
        single_choice: '单选题',
        multi_choice: '多选题',
        fill_blank: '填空题',
        short_answer: '简答题',
        code: '编程题'
      }
      return map[normalized] || type
    },

    getTypeTag(type) {
      const normalized = this.normalizeQuestionType(type)
      const map = {
        single_choice: '',
        multi_choice: 'success',
        fill_blank: 'warning',
        short_answer: 'info',
        code: 'danger'
      }
      return map[normalized] || ''
    },

    getDifficultyName(diff) {
      const map = { easy: '简单', medium: '中等', hard: '困难' }
      return map[diff] || diff
    },

    getDifficultyTag(diff) {
      const map = { easy: 'success', medium: 'warning', hard: 'danger' }
      return map[diff] || ''
    }
  }
}
</script>

<style scoped lang="scss">
.quiz-stats {
  margin-bottom: 16px;
}

.stat-box {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  background: #f8fbff;
  margin-bottom: 12px;

  &.success {
    background: #f6ffed;
  }

  &.warning {
    background: #fff7e6;
  }

  .stat-value {
    font-size: 24px;
    font-weight: 600;
    color: #303133;
  }

  .stat-label {
    margin-top: 4px;
    color: #909399;
  }
}

.question-list {
  max-height: calc(100vh - 250px);
  overflow-y: auto;
}

.question-item {
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #f5f7fa;
  }

  &.active {
    background: #e6f7ff;
    border-left: 3px solid #1890ff;
  }

  .q-stem {
    font-size: 13px;
    color: #333;
    margin: 8px 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .q-meta {
    display: flex;
    gap: 8px;
  }
}

.empty-tip {
  text-align: center;
  color: #999;
  padding: 20px;
}

.question-stem {
  font-size: 16px;
  line-height: 1.8;
  color: #333;
  margin-bottom: 12px;
}

.correct-rate {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}

.knowledge-points {
  margin-bottom: 16px;

  .el-tag {
    margin-right: 6px;
    margin-bottom: 6px;
  }
}

.options {
  margin-bottom: 20px;
}

.option-item {
  display: block;
  padding: 12px;
  margin-bottom: 8px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  transition: all 0.2s;

  &:hover {
    border-color: #1890ff;
    background: #f0f8ff;
  }

  .opt-key {
    font-weight: 600;
    margin-right: 8px;
  }
}

.text-answer {
  margin-bottom: 20px;
}

.action-bar {
  margin-bottom: 20px;
  display: flex;
  gap: 12px;
}

.result-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e8e8e8;
}

.result-detail {
  margin-top: 16px;

  .result-item {
    margin-bottom: 12px;

    .label {
      font-weight: 600;
      color: #333;
    }

    .value {
      color: #666;

      &.correct {
        color: #52c41a;
        font-weight: 600;
      }
    }
  }
}

.ai-explanation {
  margin-top: 20px;
  background: #f0f8ff;
  border-radius: 8px;
  padding: 16px;

  .ai-header {
    font-weight: 600;
    margin-bottom: 12px;
    color: #1890ff;
  }

  .ai-content {
    line-height: 1.8;
    color: #333;
  }
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #999;

  p {
    margin-top: 16px;
  }
}

.record-card {
  margin-top: 16px;
}
</style>
