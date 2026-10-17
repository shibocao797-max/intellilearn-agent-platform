<template>
  <div class="app-container edu-report">
    <el-card v-loading="loading">
      <div slot="header">
        <span>学习报告</span>
        <el-button style="float: right; padding: 3px 0" type="text" @click="loadReport">刷新</el-button>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="学习总览" name="summary">
          <!-- 统计卡片 -->
          <el-row :gutter="20" class="stat-cards">
            <el-col :xs="24" :sm="12" :md="6">
              <div class="stat-card blue">
                <div class="stat-icon"><i class="el-icon-time"></i></div>
                <div class="stat-info">
                  <div class="stat-value">{{ summary.todayMinutes || 0 }}</div>
                  <div class="stat-label">今日学习(分钟)</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <div class="stat-card green">
                <div class="stat-icon"><i class="el-icon-check"></i></div>
                <div class="stat-info">
                  <div class="stat-value">{{ summary.masteredConcepts || summary.profileDimensions || 0 }}</div>
                  <div class="stat-label">已掌握知识点</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <div class="stat-card orange">
                <div class="stat-icon"><i class="el-icon-edit"></i></div>
                <div class="stat-info">
                  <div class="stat-value">{{ summary.quizTotal || 0 }}</div>
                  <div class="stat-label">答题总数</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <div class="stat-card red">
                <div class="stat-icon"><i class="el-icon-trophy"></i></div>
                <div class="stat-info">
                  <div class="stat-value">{{ summary.consecutiveDays || 0 }}</div>
                  <div class="stat-label">连续学习天数</div>
                </div>
              </div>
            </el-col>
          </el-row>

          <!-- 详细统计 -->
          <el-row :gutter="20" style="margin-top: 20px;">
            <el-col :xs="24" :md="12">
              <div class="report-panel">
                <div class="panel-title">学习概况</div>
                <div class="detail-list">
                  <div class="detail-item">
                    <span class="label">累计学习时长</span>
                    <span class="value">{{ summary.totalStudyHours || 0 }} 小时</span>
                  </div>
                  <div class="detail-item">
                    <span class="label">答题正确率</span>
                    <span class="value">{{ formatRate(summary.quizCorrectRate) }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="label">答对题数</span>
                    <span class="value">{{ summary.quizCorrect || 0 }} 题</span>
                  </div>
                  <div class="detail-item">
                    <span class="label">完成学习节点</span>
                    <span class="value">{{ summary.completedNodes || 0 }} 个</span>
                  </div>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :md="12">
              <div class="report-panel">
                <div class="panel-title">学习趋势</div>
                <div v-if="dailyDuration.length > 0" class="trend-chart">
                  <div v-for="(item, idx) in dailyDuration" :key="idx" class="trend-bar">
                    <div class="bar-label">{{ item.date }}</div>
                    <div class="bar-track">
                      <div class="bar-fill" :style="{ width: getBarWidth(item.minutes) }"></div>
                    </div>
                    <div class="bar-value">{{ item.minutes }}分钟</div>
                  </div>
                </div>
                <div v-else class="empty-chart">暂无数据</div>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>

        <el-tab-pane label="详细报告" name="detail">
          <el-row :gutter="20" class="detail-summary">
            <el-col :xs="24" :sm="8">
              <div class="mini-stat">
                <div class="mini-value">{{ monthlyStats.totalActions || 0 }}</div>
                <div class="mini-label">近 30 天行为</div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="8">
              <div class="mini-stat">
                <div class="mini-value">{{ monthlyStats.totalStudyHours || 0 }}</div>
                <div class="mini-label">近 30 天学习小时</div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="8">
              <div class="mini-stat">
                <div class="mini-value">{{ detailed.quizRecords.length }}</div>
                <div class="mini-label">最近答题记录</div>
              </div>
            </el-col>
          </el-row>

          <div class="section-title">行为类型分布</div>
          <el-table :data="actionDistribution" size="small" border>
            <el-table-column prop="actionType" label="行为类型" min-width="160">
              <template slot-scope="scope">{{ getActionName(scope.row.actionType) }}</template>
            </el-table-column>
            <el-table-column prop="cnt" label="次数" width="120" />
          </el-table>

          <div class="section-title">最近学习行为</div>
          <el-table :data="detailed.recentLogs" size="small" border>
            <el-table-column prop="createTime" label="时间" min-width="160" />
            <el-table-column prop="actionType" label="行为" min-width="150">
              <template slot-scope="scope">{{ getActionName(scope.row.actionType) }}</template>
            </el-table-column>
            <el-table-column prop="targetType" label="对象类型" width="120" />
            <el-table-column prop="targetId" label="对象 ID" width="100" />
            <el-table-column prop="durationSec" label="时长" width="100">
              <template slot-scope="scope">{{ formatSeconds(scope.row.durationSec) }}</template>
            </el-table-column>
          </el-table>

          <div class="section-title">最近答题记录</div>
          <el-table :data="detailed.quizRecords" size="small" border>
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
            <el-table-column prop="score" label="得分" width="100" />
            <el-table-column prop="timeSpentSec" label="用时" width="100">
              <template slot-scope="scope">{{ formatSeconds(scope.row.timeSpentSec) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script>
import { getReportDetailed, getReportSummary } from '@/api/edu/report'

export default {
  name: 'EduReport',
  data() {
    return {
      loading: false,
      activeTab: 'summary',
      summary: {},
      dailyDuration: [],
      detailed: {
        recentLogs: [],
        quizRecords: []
      },
      monthlyStats: {},
      actionDistribution: []
    }
  },
  created() {
    this.loadReport()
  },
  methods: {
    async loadReport() {
      this.loading = true
      try {
        const res = await getReportSummary()
        this.summary = res.data || {}
        this.dailyDuration = this.normalizeDailyDuration(this.summary.dailyDuration)
        const detailRes = await getReportDetailed()
        const detailData = detailRes.data || {}
        this.detailed = {
          recentLogs: detailData.recentLogs || [],
          quizRecords: detailData.quizRecords || []
        }
        this.monthlyStats = detailData.monthlyStats || {}
        this.actionDistribution = this.monthlyStats.actionDistribution || []
      } catch (e) {
        console.error('加载报告失败', e)
      } finally {
        this.loading = false
      }
    },

    getBarWidth(minutes) {
      const max = Math.max(...this.dailyDuration.map(d => d.minutes), 1)
      return (minutes / max * 100) + '%'
    },

    normalizeDailyDuration(list) {
      return (list || []).map(item => ({
        date: item.date || item.day || '',
        minutes: Number(item.minutes || 0)
      }))
    },

    formatRate(value) {
      if (value === null || value === undefined || value === '') return '0%'
      return String(value).includes('%') ? String(value) : `${value}%`
    },

    formatSeconds(value) {
      if (!value) return '-'
      if (value < 60) return value + '秒'
      return Math.round(value / 60) + '分钟'
    },

    getActionName(type) {
      const map = {
        VIEW_RESOURCE: '查看资源',
        ANSWER_QUIZ: '提交答题',
        COMPLETE_NODE: '完成节点',
        CONVERSATION: 'AI 对话',
        START_QUIZ: '开始练习',
        GENERATE_RESOURCE: '生成资源'
      }
      return map[type] || type || '-'
    }
  }
}
</script>

<style scoped lang="scss">
.stat-cards {
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 20px;
  border-radius: 8px;
  color: #fff;
  margin-bottom: 12px;

  &.blue { background: linear-gradient(135deg, #1890ff, #096dd9); }
  &.green { background: linear-gradient(135deg, #52c41a, #389e0d); }
  &.orange { background: linear-gradient(135deg, #fa8c16, #d46b08); }
  &.red { background: linear-gradient(135deg, #ff4d4f, #cf1322); }

  .stat-icon {
    font-size: 36px;
    margin-right: 16px;
    opacity: 0.8;
  }

  .stat-info {
    .stat-value {
      font-size: 28px;
      font-weight: 600;
    }

    .stat-label {
      font-size: 14px;
      opacity: 0.9;
    }
  }
}

.report-panel {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  min-height: 220px;
  background: #fff;

  .panel-title {
    font-weight: 600;
    color: #303133;
    margin-bottom: 12px;
  }
}

.detail-list {
  .detail-item {
    display: flex;
    justify-content: space-between;
    padding: 12px 0;
    border-bottom: 1px solid #f0f0f0;

    &:last-child {
      border-bottom: none;
    }

    .label {
      color: #666;
    }

    .value {
      font-weight: 600;
      color: #333;
    }
  }
}

.detail-summary {
  margin-bottom: 16px;
}

.mini-stat {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  background: #fafafa;
  margin-bottom: 12px;

  .mini-value {
    font-size: 24px;
    font-weight: 600;
    color: #303133;
  }

  .mini-label {
    margin-top: 4px;
    color: #909399;
  }
}

.section-title {
  margin: 20px 0 10px;
  font-weight: 600;
  color: #303133;
}

.trend-chart {
  .trend-bar {
    display: flex;
    align-items: center;
    margin-bottom: 8px;

    .bar-label {
      width: 80px;
      font-size: 12px;
      color: #999;
    }

    .bar-track {
      flex: 1;
      height: 20px;
      background: #f0f2f5;
      border-radius: 10px;
      overflow: hidden;
      margin: 0 12px;
    }

    .bar-fill {
      height: 100%;
      background: linear-gradient(90deg, #1890ff, #096dd9);
      border-radius: 10px;
      transition: width 0.3s;
    }

    .bar-value {
      width: 70px;
      text-align: right;
      font-size: 12px;
      color: #666;
    }
  }
}

.empty-chart {
  text-align: center;
  color: #999;
  padding: 40px;
}
</style>
