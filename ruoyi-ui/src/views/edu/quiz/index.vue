<template>
  <div class="app-container edu-quiz">
    <el-row :gutter="20">
      <!-- 左侧题目列表 -->
      <el-col :span="8">
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
      <el-col :span="16">
        <el-card v-if="currentQuestion">
          <div slot="header">
            <span>{{ getTypeName(currentQuestion.questionType) }}</span>
            <el-tag size="mini" :type="getDifficultyTag(currentQuestion.difficulty)" style="margin-left: 8px;">
              {{ getDifficultyName(currentQuestion.difficulty) }}
            </el-tag>
          </div>

          <!-- 题干 -->
          <div class="question-stem">{{ currentQuestion.stem }}</div>

          <!-- 选项（选择题） -->
          <div v-if="isChoiceType(currentQuestion.questionType)" class="options">
            <el-radio-group v-if="currentQuestion.questionType === 'single_choice'" v-model="userAnswer">
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

            <el-checkbox-group v-else-if="currentQuestion.questionType === 'multi_choice'" v-model="userAnswerMulti">
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
  </div>
</template>

<script>
import { getQuizList, submitAnswer, aiExplain } from '@/api/edu/quiz'

export default {
  name: 'EduQuiz',
  data() {
    return {
      questions: [],
      currentQuestion: null,
      userAnswer: '',
      userAnswerMulti: [],
      submitting: false,
      showResult: false,
      result: {},
      aiExplanation: '',
      loadingExplanation: false
    }
  },
  created() {
    this.loadQuestions()
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

    selectQuestion(q) {
      this.currentQuestion = q
      this.userAnswer = ''
      this.userAnswerMulti = []
      this.showResult = false
      this.result = {}
      this.aiExplanation = ''
    },

    isChoiceType(type) {
      return type === 'single_choice' || type === 'multi_choice'
    },

    parseOptions(options) {
      if (!options) return []
      try {
        if (typeof options === 'string') {
          return JSON.parse(options)
        }
        return options
      } catch (e) {
        return []
      }
    },

    async handleSubmit() {
      const answer = this.currentQuestion.questionType === 'multi_choice'
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

    getTypeName(type) {
      const map = {
        single_choice: '单选题',
        multi_choice: '多选题',
        fill_blank: '填空题',
        short_answer: '简答题',
        code: '编程题'
      }
      return map[type] || type
    },

    getTypeTag(type) {
      const map = {
        single_choice: '',
        multi_choice: 'success',
        fill_blank: 'warning',
        short_answer: 'info',
        code: 'danger'
      }
      return map[type] || ''
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
  margin-bottom: 20px;
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
</style>
