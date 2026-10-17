<template>
  <div class="app-container">
    <!-- 筛选条件 -->
    <el-form :inline="true" :model="queryParams" class="filter-form">
      <el-form-item label="资源类型">
        <el-select v-model="queryParams.type" placeholder="全部类型" clearable size="small">
          <el-option label="全部" value="" />
          <el-option label="学习文档" value="document" />
          <el-option label="思维导图" value="mindmap" />
          <el-option label="代码案例" value="code" />
          <el-option label="题库" value="quiz" />
          <el-option label="教学动画" value="animation" />
          <el-option label="视频" value="video" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="small" @click="handleQuery">搜索</el-button>
      </el-form-item>
    </el-form>

    <!-- 资源列表 -->
    <el-row :gutter="16">
      <el-col v-for="item in resourceList" :key="item.id" :xs="24" :sm="12" :md="8" :lg="6">
        <div class="resource-card" @click="handleDetail(item)">
          <div class="card-cover" :style="{ background: item.headerColor || '#ffe4d6' }">
            <span class="card-icon">{{ item.icon || getResourceIcon(item.type) }}</span>
          </div>
          <div class="card-body">
            <div class="card-title">{{ item.title }}</div>
            <div class="card-desc">{{ item.description || '暂无描述' }}</div>
            <div class="card-footer">
              <el-tag size="mini" :type="getTagType(item.type)">
                {{ getResourceTypeName(item.type) }}
              </el-tag>
              <span class="card-time">{{ formatDate(item.createTime) }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div v-if="resourceList.length === 0" class="empty-state">
      <i class="el-icon-folder-open" style="font-size: 48px; color: #ccc;"></i>
      <p>暂无学习资源</p>
      <p class="tip">在对话中让 AI 为你生成学习资源</p>
    </div>

    <!-- 分页 -->
    <el-pagination
      v-if="total > 0"
      layout="total, sizes, prev, pager, next"
      :total="total"
      :page-sizes="[12, 24, 48]"
      :page-size="queryParams.pageSize"
      :current-page="queryParams.pageNum"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />

    <!-- 资源详情弹窗 -->
    <el-dialog :title="detail.title" :visible.sync="detailVisible" width="80%" top="5vh" @closed="releaseAnimationFrame">
      <div class="resource-detail" v-loading="detailLoading">
        <div class="detail-header">
          <el-tag :type="getTagType(detail.type)">{{ getResourceTypeName(detail.type) }}</el-tag>
          <span class="detail-time">{{ detail.createTime }}</span>
        </div>
        <!-- 教学动画 -->
        <div v-if="detail.type === 'animation'" class="detail-animation">
          <iframe
            v-if="animationFrameUrl"
            :src="animationFrameUrl"
            sandbox="allow-scripts"
            frameborder="0"
          ></iframe>
          <el-empty v-else description="动画资源暂无可播放内容" />
        </div>
        <!-- 思维导图图片 -->
        <div v-else-if="detail.type === 'mindmap' && isMindmapImageTag(detail.tag)" class="detail-image">
          <img :src="detail.sourceUrl || detail.coverUrl || detail.content" alt="思维导图" style="max-width: 100%;" />
        </div>
        <!-- 其他类型内容 -->
        <div v-else class="detail-content markdown-body" v-html="renderContent(detail)"></div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { fetchAnimationHtml, getResourceDetail, getResourceList } from '@/api/edu/resource'
import { sanitizeHtml } from '@/utils/sanitize'

// 使用 require 导入 marked（兼容 webpack 4）
const markedLib = require('marked')
const markedParser = markedLib.marked || markedLib.parse || markedLib

// 配置 marked
const setMarkedOptions = markedParser.setOptions || markedLib.setOptions
if (setMarkedOptions) setMarkedOptions.call(markedParser, {
  breaks: true,
  gfm: true
})

export default {
  name: 'EduResource',
  data() {
    return {
      queryParams: {
        type: '',
        pageNum: 1,
        pageSize: 12
      },
      resourceList: [],
      total: 0,
      detailVisible: false,
      detailLoading: false,
      detail: {},
      animationFrameUrl: ''
    }
  },
  created() {
    this.loadResources()
  },
  methods: {
    async loadResources() {
      try {
        const res = await getResourceList(this.queryParams)
        this.resourceList = res.rows || []
        this.total = res.total || 0
      } catch (e) {
        console.error('加载资源失败', e)
      }
    },

    handleQuery() {
      this.queryParams.pageNum = 1
      this.loadResources()
    },

    handleSizeChange(size) {
      this.queryParams.pageSize = size
      this.loadResources()
    },

    handleCurrentChange(page) {
      this.queryParams.pageNum = page
      this.loadResources()
    },

    async handleDetail(item) {
      this.detail = { ...item }
      this.detailVisible = true
      this.detailLoading = true
      this.releaseAnimationFrame()

      try {
        const res = await getResourceDetail(item.id)
        this.detail = res.data || item
        if (this.detail.type === 'animation') {
          const html = await fetchAnimationHtml(item.id)
          this.animationFrameUrl = URL.createObjectURL(new Blob([html], { type: 'text/html' }))
        }
      } catch (e) {
        this.$message.error(e.message || '加载资源详情失败')
      } finally {
        this.detailLoading = false
      }
    },

    releaseAnimationFrame() {
      if (this.animationFrameUrl) {
        URL.revokeObjectURL(this.animationFrameUrl)
        this.animationFrameUrl = ''
      }
    },

    getResourceIcon(type) {
      const iconMap = {
        document: '📄',
        quiz: '📝',
        mindmap: '🧠',
        code: '💻',
        animation: '🎬',
        video: '🎥'
      }
      return iconMap[type] || '📦'
    },

    getResourceTypeName(type) {
      const typeMap = {
        document: '学习文档',
        quiz: '题库',
        mindmap: '思维导图',
        code: '代码案例',
        animation: '教学动画',
        video: '视频'
      }
      return typeMap[type] || type
    },

    getTagType(type) {
      const typeMap = {
        document: '',
        quiz: 'success',
        mindmap: 'warning',
        code: 'info',
        animation: 'danger',
        video: 'danger'
      }
      return typeMap[type] || ''
    },

    formatDate(dateStr) {
      if (!dateStr) return ''
      return dateStr.substring(0, 10)
    },

    renderContent(detail) {
      if (!detail || !detail.content) return ''
      // 思维导图 Base64 图片
      if (detail.type === 'mindmap' && this.isMindmapImageTag(detail.tag)) {
        const src = detail.sourceUrl || detail.coverUrl || detail.content
        return `<img src="${src}" alt="思维导图" style="max-width:100%;" />`
      }
      // 代码类型
      if (detail.type === 'code') {
        return `<pre><code>${this.escapeHtml(detail.content)}</code></pre>`
      }
      // 默认 Markdown 渲染
      return this.renderMarkdown(detail.content)
    },

    isMindmapImageTag(tag) {
      return typeof tag === 'string' && tag.split(',').includes('png_base64')
    },

    escapeHtml(text) {
      if (!text) return ''
      return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
    },

    renderMarkdown(text) {
      if (!text) return ''
      try {
        const html = typeof markedParser === 'function' ? markedParser(text) : String(text)
        return sanitizeHtml(html)
      } catch (e) {
        console.error('Markdown 渲染失败:', e)
        return sanitizeHtml(String(text).replace(/\n/g, '<br>'))
      }
    }
  }
}
</script>

<style scoped lang="scss">
.filter-form {
  margin-bottom: 20px;
}

.resource-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 16px;
  cursor: pointer;
  transition: all 0.3s;
  border: 1px solid #e8e8e8;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }

  .card-cover {
    height: 80px;
    display: flex;
    align-items: center;
    justify-content: center;

    .card-icon {
      font-size: 36px;
    }
  }

  .card-body {
    padding: 12px;

    .card-title {
      font-size: 14px;
      font-weight: 500;
      color: #333;
      margin-bottom: 8px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .card-desc {
      font-size: 12px;
      color: #999;
      margin-bottom: 12px;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .card-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .card-time {
        font-size: 12px;
        color: #ccc;
      }
    }
  }
}

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

.resource-detail {
  .detail-header {
    margin-bottom: 20px;
    display: flex;
    align-items: center;
    gap: 12px;

    .detail-time {
      color: #999;
      font-size: 13px;
    }
  }

  .detail-image {
    text-align: center;
    padding: 20px;

    img {
      max-width: 100%;
      border-radius: 8px;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    }
  }

  .detail-animation {
    height: 62vh;

    iframe {
      width: 100%;
      height: 100%;
      border: 1px solid #e8e8e8;
      border-radius: 8px;
      background: #fff;
    }
  }

  .detail-content {
    line-height: 1.8;
    max-height: 60vh;
    overflow-y: auto;
  }

  .markdown-body {
    h1, h2, h3, h4, h5, h6 {
      margin-top: 16px;
      margin-bottom: 8px;
      font-weight: 600;
      color: #1a1a1a;
    }

    h1 { font-size: 24px; border-bottom: 1px solid #e8e8e8; padding-bottom: 8px; }
    h2 { font-size: 20px; border-bottom: 1px solid #e8e8e8; padding-bottom: 6px; }
    h3 { font-size: 18px; }

    p {
      margin-bottom: 12px;
    }

    ul, ol {
      padding-left: 24px;
      margin-bottom: 12px;
    }

    blockquote {
      border-left: 4px solid #1890ff;
      padding: 8px 16px;
      margin: 12px 0;
      background: #f0f8ff;
      color: #666;
    }

    code {
      background: #f5f5f5;
      padding: 2px 6px;
      border-radius: 4px;
      font-family: 'Monaco', 'Menlo', monospace;
      font-size: 13px;
      color: #e74c3c;
    }

    pre {
      background: #1e1e1e;
      color: #d4d4d4;
      padding: 20px;
      border-radius: 8px;
      overflow-x: auto;

      code {
        background: none;
        color: inherit;
        padding: 0;
      }
    }

    table {
      border-collapse: collapse;
      width: 100%;
      margin: 12px 0;

      th, td {
        border: 1px solid #e8e8e8;
        padding: 8px 12px;
        text-align: left;
      }

      th {
        background: #fafafa;
        font-weight: 600;
      }
    }

    a {
      color: #1890ff;
      text-decoration: none;
    }
  }
}

.el-pagination {
  margin-top: 20px;
  text-align: right;
}
</style>
