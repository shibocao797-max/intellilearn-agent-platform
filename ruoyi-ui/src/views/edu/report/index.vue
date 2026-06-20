<template>
  <div class="app-container edu-report">
    <el-card v-loading="loading">
      <div slot="header">
        <span>学习报告</span>
        <el-button style="float: right; padding: 3px 0" type="text" @click="loadReport">刷新</el-button>
      </div>

      <!-- 统计卡片 -->
      <el-row :gutter="20" class="stat-cards">
        <el-col :span="6">
          <div class="stat-card blue">
            <div class="stat-icon"><i class="el-icon-time"></i></div>
            <div class="stat-info">
              <div class="stat-value">{{ summary.todayMinutes || 0 }}</div>
              <div class="stat-label">今日学习(分钟)</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card green">
            <div class="stat-icon"><i class="el-icon-check"></i></div>
            <div class="stat-info">
              <div class="stat-value">{{ summary.masteredConcepts || 0 }}</div>
              <div class="stat-label">已掌握知识点</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card orange">
            <div class="stat-icon"><i class="el-icon-edit"></i></div>
            <div class="stat-info">
              <div class="stat-value">{{ summary.quizTotal || 0 }}</div>
              <div class="stat-label">答题总数</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
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
        <el-col :span="12">
          <el-card>
            <div slot="header">学习概况</div>
            <div class="detail-list">
              <div class="detail-item">
                <span class="label">累计学习时长</span>
                <span class="value">{{ summary.totalStudyHours || 0 }} 小时</span>
              </div>
              <div class="detail-item">
                <span class="label">答题正确率</span>
                <span class="value">{{ summary.quizCorrectRate || 0 }}%</span>
              </div>
              <div class="detail-item">
                <span class="label">完成学习节点</span>
                <span class="value">{{ summary.completedNodes || 0 }} 个</span>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card>
            <div slot="header">学习趋势</div>
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
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script>
import { getReportSummary } from '@/api/edu/report'

export default {
  name: 'EduReport',
  data() {
    return {
      loading: false,
      summary: {},
      dailyDuration: []
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
        this.dailyDuration = res.data.dailyDuration || []
      } catch (e) {
        console.error('加载报告失败', e)
      } finally {
        this.loading = false
      }
    },

    getBarWidth(minutes) {
      const max = Math.max(...this.dailyDuration.map(d => d.minutes), 1)
      return (minutes / max * 100) + '%'
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
