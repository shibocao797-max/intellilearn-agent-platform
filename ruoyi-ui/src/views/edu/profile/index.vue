<template>
  <div class="app-container">
    <el-card v-loading="loading">
      <div slot="header">
        <span>我的学习画像</span>
        <el-tag v-if="profile.version > 0" style="margin-left: 12px;" type="success">
          已更新 {{ profile.dimensionCount }} 个维度
        </el-tag>
      </div>

      <div v-if="profile.version === 0" class="empty-state">
        <i class="el-icon-user" style="font-size: 48px; color: #ccc;"></i>
        <p>暂无学习画像数据</p>
        <p class="tip">开始与 AI 对话后，系统会自动构建你的学习画像</p>
      </div>

      <div v-else class="profile-content">
        <!-- 基础信息 -->
        <el-row :gutter="20" class="profile-header">
          <el-col :span="8">
            <div class="stat-card">
              <div class="stat-value">{{ profile.dimensionCount || 0 }}</div>
              <div class="stat-label">画像维度</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="stat-card">
              <div class="stat-value">{{ profile.version || 0 }}</div>
              <div class="stat-label">更新次数</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="stat-card">
              <div class="stat-value">{{ profile.lastUpdated ? formatDate(profile.lastUpdated) : '-' }}</div>
              <div class="stat-label">最近更新</div>
            </div>
          </el-col>
        </el-row>

        <!-- 维度详情 -->
        <div class="dimensions-section">
          <h3>画像维度详情</h3>
          <el-row :gutter="16">
            <el-col v-for="(dim, key) in dimensions" :key="key" :span="12">
              <div class="dimension-card">
                <div class="dim-header">
                  <span class="dim-name">{{ getDimensionName(key) }}</span>
                  <el-tag size="mini" :type="getConfidenceType(dim.confidence)">
                    置信度: {{ (dim.confidence * 100).toFixed(0) }}%
                  </el-tag>
                </div>
                <div class="dim-value">{{ formatDimensionValue(dim.value) }}</div>
                <div v-if="dim.evidence" class="dim-evidence">
                  <span class="label">依据：</span>{{ dim.evidence }}
                </div>
              </div>
            </el-col>
          </el-row>
          <div v-if="Object.keys(dimensions).length === 0" class="empty-dim">
            暂无维度数据
          </div>
        </div>

        <!-- 原始 JSON（可折叠） -->
        <div class="raw-json">
          <el-collapse>
            <el-collapse-item title="查看原始画像 JSON">
              <pre>{{ formatJson(profile.profileJson) }}</pre>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import { getProfileDetail } from '@/api/edu/profile'

export default {
  name: 'EduProfile',
  data() {
    return {
      loading: false,
      profile: {
        version: 0,
        dimensionCount: 0,
        lastUpdated: '',
        profileJson: ''
      },
      dimensions: {}
    }
  },
  created() {
    this.loadProfile()
  },
  methods: {
    async loadProfile() {
      this.loading = true
      try {
        const res = await getProfileDetail()
        this.profile = res.data || this.profile
        this.dimensions = res.data.dimensions || {}
      } catch (e) {
        console.error('加载画像失败', e)
      } finally {
        this.loading = false
      }
    },

    getDimensionName(key) {
      const nameMap = {
        knowledgeLevel: '知识水平',
        cognitiveStyle: '认知风格',
        learningPace: '学习节奏',
        weakPoints: '薄弱知识点',
        interests: '兴趣方向',
        learningGoal: '学习目标',
        preferredResourceType: '偏好资源类型',
        major: '专业',
        grade: '年级'
      }
      return nameMap[key] || key
    },

    getConfidenceType(confidence) {
      if (confidence >= 0.8) return 'success'
      if (confidence >= 0.6) return 'warning'
      return 'danger'
    },

    formatDimensionValue(value) {
      if (Array.isArray(value)) return value.join('、')
      if (typeof value === 'object') return JSON.stringify(value)
      return String(value)
    },

    formatDate(dateStr) {
      if (!dateStr) return '-'
      return dateStr.substring(0, 10)
    },

    formatJson(jsonStr) {
      try {
        return JSON.stringify(JSON.parse(jsonStr), null, 2)
      } catch (e) {
        return jsonStr || '{}'
      }
    }
  }
}
</script>

<style scoped lang="scss">
.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #999;

  p {
    margin-top: 16px;
    font-size: 16px;
  }

  .tip {
    font-size: 14px;
    color: #bbb;
  }
}

.profile-header {
  margin-bottom: 30px;
}

.stat-card {
  text-align: center;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 8px;

  .stat-value {
    font-size: 28px;
    font-weight: 600;
    color: #1890ff;
  }

  .stat-label {
    margin-top: 8px;
    font-size: 14px;
    color: #666;
  }
}

.dimensions-section {
  h3 {
    font-size: 16px;
    margin-bottom: 16px;
    color: #333;
  }
}

.dimension-card {
  padding: 16px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  margin-bottom: 16px;

  .dim-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;

    .dim-name {
      font-weight: 600;
      color: #333;
    }
  }

  .dim-value {
    font-size: 15px;
    color: #1890ff;
    margin-bottom: 8px;
  }

  .dim-evidence {
    font-size: 13px;
    color: #999;

    .label {
      color: #666;
    }
  }
}

.empty-dim {
  text-align: center;
  color: #999;
  padding: 40px;
}

.raw-json {
  margin-top: 24px;

  pre {
    background: #f5f7fa;
    padding: 16px;
    border-radius: 8px;
    overflow-x: auto;
    font-size: 13px;
    line-height: 1.5;
  }
}
</style>
