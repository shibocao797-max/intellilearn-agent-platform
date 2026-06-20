<template>
  <div class="app-container edu-chat">
    <!-- 左侧会话列表 -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <el-button type="primary" icon="el-icon-plus" size="small" @click="handleNewSession">
          新建对话
        </el-button>
      </div>
      <div class="session-list">
        <div
          v-for="item in sessions"
          :key="item.sessionId"
          :class="['session-item', { active: currentSessionId === item.sessionId }]"
          @click="switchSession(item.sessionId)"
        >
          <div class="session-title">{{ item.title || '新对话' }}</div>
          <div class="session-meta">
            <span>{{ item.messageCount }} 条消息</span>
            <el-button
              type="text"
              icon="el-icon-delete"
              size="mini"
              @click.stop="handleDeleteSession(item.sessionId)"
            />
          </div>
        </div>
        <div v-if="sessions.length === 0" class="empty-tip">暂无会话</div>
      </div>
    </div>

    <!-- 右侧对话区域 -->
    <div class="chat-main">
      <!-- 消息列表 -->
      <div ref="messageList" class="message-list">
        <!-- 欢迎页 -->
        <div v-if="messages.length === 0" class="welcome">
          <div class="welcome-icon">🎓</div>
          <h2>教育 AI 智能助手</h2>
          <p>我是你的专属学习助手，由多个 AI 智能体协同工作，为你生成个性化学习资源。</p>
          <div class="agent-tags">
            <el-tag v-for="agent in agentList" :key="agent.name" :type="agent.type" size="small">
              {{ agent.icon }} {{ agent.name }}
            </el-tag>
          </div>
          <div class="quick-actions">
            <el-button size="small" @click="sendQuick('帮我讲解一下数据结构中的二叉树')">讲解二叉树</el-button>
            <el-button size="small" @click="sendQuick('生成一套关于SQL的练习题')">SQL练习题</el-button>
            <el-button size="small" @click="sendQuick('画一个机器学习的思维导图')">机器学习思维导图</el-button>
            <el-button size="small" @click="sendQuick('写一个Python冒泡排序的代码案例')">Python排序代码</el-button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(msg, index) in messages" :key="index" :class="['message-item', msg.role]">
          <div class="message-avatar">
            <span v-if="msg.role === 'user'">👤</span>
            <span v-else>🤖</span>
          </div>
          <div class="message-content">
            <!-- 用户消息 -->
            <div v-if="msg.role === 'user'" class="message-text">{{ msg.content }}</div>

            <!-- AI 回复 -->
            <template v-else>
              <!-- 智能体协作过程 -->
              <div v-if="msg.agentProgress && msg.agentProgress.length > 0" class="agent-workflow">
                <div class="workflow-header">
                  <i class="el-icon-connection"></i>
                  <span>多智能体协作完成</span>
                  <el-tag size="mini" type="success">共调用 {{ msg.agentProgress.length }} 个智能体</el-tag>
                </div>
                <div class="workflow-steps">
                  <div
                    v-for="(agent, ai) in msg.agentProgress"
                    :key="ai"
                    :class="['step-item', agent.status]"
                  >
                    <div class="step-icon">
                      <i :class="getStepIcon(agent.status)"></i>
                    </div>
                    <div class="step-info">
                      <span class="step-name">{{ agent.displayName }}</span>
                      <span v-if="agent.durationMs" class="step-time">{{ formatDuration(agent.durationMs) }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 文本回复 -->
              <div v-if="msg.content" class="message-text markdown-body" v-html="renderMarkdown(msg.content)"></div>

              <!-- 资源卡片 -->
              <div v-if="msg.resources && msg.resources.length > 0" class="resource-section">
                <div class="resource-title">
                  <i class="el-icon-folder-opened"></i>
                  <span>生成的学习资源</span>
                </div>
                <div class="resource-cards">
                  <div
                    v-for="(res, ri) in msg.resources"
                    :key="ri"
                    class="resource-card"
                    @click="openResource(res)"
                  >
                    <div class="card-icon">{{ getResourceIcon(res.resourceType) }}</div>
                    <div class="card-info">
                      <div class="card-name">{{ res.displayName }}</div>
                      <div class="card-meta">
                        <el-tag size="mini" :type="getResourceTagType(res.resourceType)">
                          {{ getResourceTypeName(res.resourceType) }}
                        </el-tag>
                        <span v-if="res.quality" class="quality">
                          <i class="el-icon-circle-check"></i> 质量 {{ (res.quality * 100).toFixed(0) }}%
                        </span>
                      </div>
                    </div>
                    <div class="card-action">
                      <i class="el-icon-arrow-right"></i>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>

        <!-- 流式输出中 -->
        <div v-if="isStreaming" class="message-item assistant streaming">
          <div class="message-avatar">
            <span>🤖</span>
          </div>
          <div class="message-content">
            <!-- 智能体工作状态 -->
            <div class="agent-workflow working">
              <div class="workflow-header">
                <i class="el-icon-loading"></i>
                <span>智能体正在工作...</span>
              </div>

              <!-- 意图分析 -->
              <div v-if="streamingPhase === 'INTENT_ANALYZING'" class="workflow-phase">
                <div class="phase-icon analyzing">
                  <i class="el-icon-search"></i>
                </div>
                <div class="phase-text">正在分析你的问题...</div>
              </div>

              <!-- 选中的智能体 -->
              <div v-if="streamingAgents.length > 0" class="workflow-steps">
                <div
                  v-for="(agent, ai) in streamingAgents"
                  :key="ai"
                  :class="['step-item', agent.status]"
                >
                  <div class="step-icon">
                    <i v-if="agent.status === 'running'" class="el-icon-loading"></i>
                    <i v-else-if="agent.status === 'completed'" class="el-icon-circle-check"></i>
                    <i v-else class="el-icon-time"></i>
                  </div>
                  <div class="step-info">
                    <span class="step-name">{{ agent.displayName }}</span>
                    <span v-if="agent.status === 'running'" class="step-status">执行中...</span>
                    <span v-else-if="agent.status === 'completed'" class="step-status success">已完成</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 流式文本 -->
            <div v-if="streamingText" class="message-text streaming-text markdown-body">
              <div v-html="renderMarkdown(streamingText)"></div>
              <span class="cursor">|</span>
            </div>

            <!-- 已生成的资源 -->
            <div v-if="streamingResources.length > 0" class="resource-section">
              <div class="resource-title">
                <i class="el-icon-folder-opened"></i>
                <span>已生成 {{ streamingResources.length }} 个资源</span>
              </div>
              <div class="resource-cards">
                <div
                  v-for="(res, ri) in streamingResources"
                  :key="ri"
                  class="resource-card new"
                  @click="openResource(res)"
                >
                  <div class="card-icon">{{ getResourceIcon(res.resourceType) }}</div>
                  <div class="card-info">
                    <div class="card-name">{{ res.displayName }}</div>
                    <div class="card-meta">
                      <el-tag size="mini" :type="getResourceTagType(res.resourceType)">
                        {{ getResourceTypeName(res.resourceType) }}
                      </el-tag>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-area">
        <div class="input-wrapper">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="2"
            :placeholder="isStreaming ? '智能体正在工作中，请稍候...' : '输入你的学习问题，AI 将为你生成个性化资源...'"
            resize="none"
            :disabled="isStreaming"
            @keydown.enter.native.exact="handleSend"
          />
          <el-button
            class="send-btn"
            type="primary"
            icon="el-icon-s-promotion"
            :loading="isStreaming"
            :disabled="!inputMessage.trim() && !isStreaming"
            @click="handleSend"
          >
            {{ isStreaming ? '处理中' : '发送' }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 资源详情弹窗 -->
    <el-dialog :title="resourceDialogTitle" :visible.sync="resourceDialogVisible" width="80%" top="5vh">
      <div class="resource-detail" v-loading="resourceDialogLoading">
        <!-- 思维导图图片 -->
        <div v-if="resourceDialogType === 'mindmap' && resourceDialogTag === 'png_base64'" class="detail-image">
          <img :src="resourceDialogContent" alt="思维导图" style="max-width: 100%;" />
        </div>
        <!-- 代码类型 -->
        <div v-else-if="resourceDialogType === 'code'" class="detail-code">
          <pre><code>{{ resourceDialogContent }}</code></pre>
        </div>
        <!-- 其他类型 -->
        <div v-else class="detail-content" v-html="renderMarkdown(resourceDialogContent)"></div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getSessions, newSession, deleteSession, getConversation } from '@/api/edu/agent'
import { getResourceDetail } from '@/api/edu/resource'

// 使用 require 导入 marked（兼容 webpack 4）
const marked = require('marked')

// 配置 marked
marked.setOptions({
  breaks: true,
  gfm: true
})

export default {
  name: 'EduChat',
  data() {
    return {
      sessions: [],
      currentSessionId: '',
      messages: [],
      inputMessage: '',
      isStreaming: false,
      streamingText: '',
      streamingPhase: '',
      streamingAgents: [],
      streamingResources: [],
      _sseController: null,
      _currentEvent: null,
      resourceDialogVisible: false,
      resourceDialogTitle: '',
      resourceDialogContent: '',
      resourceDialogType: '',
      resourceDialogTag: '',
      resourceDialogLoading: false,
      agentList: [
        { name: '文本回复', type: '', icon: '💬' },
        { name: '文档生成', type: 'success', icon: '📄' },
        { name: '题库生成', type: 'warning', icon: '📝' },
        { name: '思维导图', type: 'danger', icon: '🧠' },
        { name: '代码案例', type: 'info', icon: '💻' }
      ]
    }
  },
  created() {
    this.loadSessions()
  },
  beforeDestroy() {
    this.closeSSE()
  },
  methods: {
    // 加载会话列表
    async loadSessions() {
      try {
        const res = await getSessions()
        this.sessions = res.data || []
      } catch (e) {
        console.error('加载会话列表失败', e)
      }
    },

    // 新建会话
    async handleNewSession() {
      try {
        const res = await newSession()
        this.currentSessionId = res.data.sessionId
        this.messages = []
        await this.loadSessions()
      } catch (e) {
        this.$message.error('新建会话失败')
      }
    },

    // 切换会话
    async switchSession(sessionId) {
      this.currentSessionId = sessionId
      try {
        const res = await getConversation(sessionId)
        this.messages = (res.data || []).map(msg => {
          let resources = []
          if (msg.metadataJson) {
            try {
              resources = JSON.parse(msg.metadataJson)
            } catch (e) {}
          }
          return {
            role: msg.role,
            content: msg.content,
            resources: resources
          }
        })
        this.scrollToBottom()
      } catch (e) {
        console.error('加载对话历史失败', e)
      }
    },

    // 删除会话
    async handleDeleteSession(sessionId) {
      try {
        await this.$confirm('确认删除该会话？', '提示', { type: 'warning' })
        await deleteSession(sessionId)
        if (this.currentSessionId === sessionId) {
          this.currentSessionId = ''
          this.messages = []
        }
        await this.loadSessions()
        this.$message.success('删除成功')
      } catch (e) {
        if (e !== 'cancel') {
          this.$message.error('删除失败')
        }
      }
    },

    // 发送快捷消息
    sendQuick(text) {
      if (this.isStreaming) return
      this.inputMessage = text
      this.handleSend()
    },

    // 发送消息
    async handleSend(e) {
      if (e) e.preventDefault()
      const message = this.inputMessage.trim()
      if (!message || this.isStreaming) return

      // 如果没有会话，先创建
      if (!this.currentSessionId) {
        await this.handleNewSession()
      }

      // 添加用户消息
      this.messages.push({ role: 'user', content: message })
      this.inputMessage = ''
      this.scrollToBottom()

      // 开始 SSE 流式请求
      this.startSSE(message)
    },

    // 启动 SSE 流式请求
    startSSE(message) {
      this.isStreaming = true
      this.streamingText = ''
      this.streamingPhase = 'INTENT_ANALYZING'
      this.streamingAgents = []
      this.streamingResources = []

      const token = this.$store.getters.token
      const baseURL = process.env.VUE_APP_BASE_API
      const url = `${baseURL}/edu/ai/chat/stream`

      const controller = new AbortController()

      fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ' + token
        },
        body: JSON.stringify({
          message: message,
          sessionId: this.currentSessionId
        }),
        signal: controller.signal
      }).then(response => {
        const reader = response.body.getReader()
        const decoder = new TextDecoder()

        const read = () => {
          reader.read().then(({ done, value }) => {
            if (done) {
              this.finishStreaming()
              return
            }

            const text = decoder.decode(value, { stream: true })
            this.processSSEData(text)
            read()
          }).catch(err => {
            if (err.name !== 'AbortError') {
              console.error('SSE 读取错误', err)
            }
            this.finishStreaming()
          })
        }

        read()
      }).catch(err => {
        console.error('SSE 请求错误', err)
        this.finishStreaming()
      })

      this._sseController = controller
    },

    // 处理 SSE 数据
    processSSEData(rawData) {
      const lines = rawData.split('\n')

      for (const line of lines) {
        if (line.startsWith('event:')) {
          this._currentEvent = line.substring(6).trim()
        } else if (line.startsWith('data:') && this._currentEvent) {
          const data = line.substring(5).trim()

          switch (this._currentEvent) {
            case 'token':
              this.streamingText += data
              this.streamingPhase = 'GENERATING'
              this.scrollToBottom()
              break

            case 'tokenDone':
              break

            case 'phase':
              this.streamingPhase = data
              break

            case 'intent':
              try {
                const intentData = JSON.parse(data)
                this.streamingPhase = 'INTENT_DONE'
                console.log('意图识别:', intentData)
              } catch (e) {}
              break

            case 'selectedAgents':
              try {
                const agents = JSON.parse(data)
                this.streamingAgents = agents.map(name => ({
                  name: name,
                  displayName: this.getAgentDisplayName(name),
                  status: 'running'
                }))
                this.streamingPhase = 'AGENTS_RUNNING'
              } catch (e) {}
              break

            case 'resource':
              try {
                const resource = JSON.parse(data)
                this.streamingResources.push(resource)
                const agent = this.streamingAgents.find(a => a.name === resource.agentName)
                if (agent) {
                  agent.status = 'completed'
                  agent.durationMs = resource.durationMs
                }
              } catch (e) {}
              break

            case 'profile':
              this.streamingPhase = 'PROFILE_UPDATED'
              break

            case 'done':
              this.finishStreaming()
              break

            case 'error':
              this.$message.error(data || 'AI 处理失败')
              this.finishStreaming()
              break
          }

          this._currentEvent = null
        }
      }
    },

    // 完成流式输出
    finishStreaming() {
      if (this.streamingText || this.streamingResources.length > 0) {
        // 标记所有 agent 为完成
        this.streamingAgents.forEach(a => {
          if (a.status === 'running') a.status = 'completed'
        })

        this.messages.push({
          role: 'assistant',
          content: this.streamingText,
          resources: [...this.streamingResources],
          agentProgress: [...this.streamingAgents]
        })
      }
      this.isStreaming = false
      this.streamingText = ''
      this.streamingPhase = ''
      this.streamingAgents = []
      this.streamingResources = []
      this.scrollToBottom()
    },

    // 关闭 SSE
    closeSSE() {
      if (this._sseController) {
        this._sseController.abort()
        this._sseController = null
      }
    },

    // 打开资源详情
    async openResource(res) {
      if (!res.persistedId) return

      const id = res.persistedId.split(':').pop()
      this.resourceDialogTitle = res.displayName || '资源详情'
      this.resourceDialogContent = ''
      this.resourceDialogType = res.resourceType || ''
      this.resourceDialogTag = ''
      this.resourceDialogVisible = true
      this.resourceDialogLoading = true

      try {
        const detail = await getResourceDetail(id)
        this.resourceDialogContent = detail.data.content || '暂无内容'
        this.resourceDialogTag = detail.data.tag || ''
      } catch (e) {
        this.resourceDialogContent = '加载失败'
        this.$message.error('加载资源详情失败')
      } finally {
        this.resourceDialogLoading = false
      }
    },

    // 渲染 Markdown
    renderMarkdown(text) {
      if (!text) return ''
      try {
        return marked(text)
      } catch (e) {
        console.error('Markdown 渲染失败:', e)
        return text.replace(/\n/g, '<br>')
      }
    },

    // 滚动到底部
    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.messageList
        if (el) {
          el.scrollTop = el.scrollHeight
        }
      })
    },

    // 格式化时长
    formatDuration(ms) {
      if (ms < 1000) return ms + 'ms'
      return (ms / 1000).toFixed(1) + 's'
    },

    // 获取步骤图标
    getStepIcon(status) {
      switch (status) {
        case 'running': return 'el-icon-loading'
        case 'completed': return 'el-icon-circle-check'
        case 'failed': return 'el-icon-circle-close'
        default: return 'el-icon-time'
      }
    },

    // 获取 Agent 显示名
    getAgentDisplayName(name) {
      const nameMap = {
        textReply: '文本回复 Agent',
        docGen: '文档生成 Agent',
        quizGen: '题库生成 Agent',
        mindmapGen: '思维导图 Agent',
        codeGen: '代码案例 Agent',
        videoGen: '视频生成 Agent'
      }
      return nameMap[name] || name
    },

    // 获取资源图标
    getResourceIcon(type) {
      const iconMap = {
        document: '📄',
        quiz: '📝',
        mindmap: '🧠',
        code: '💻',
        video: '🎥'
      }
      return iconMap[type] || '📦'
    },

    // 获取资源类型名称
    getResourceTypeName(type) {
      const typeMap = {
        document: '学习文档',
        quiz: '练习题库',
        mindmap: '思维导图',
        code: '代码案例',
        video: '教学视频'
      }
      return typeMap[type] || type
    },

    // 获取资源标签类型
    getResourceTagType(type) {
      const typeMap = {
        document: '',
        quiz: 'success',
        mindmap: 'warning',
        code: 'info',
        video: 'danger'
      }
      return typeMap[type] || ''
    }
  }
}
</script>

<style scoped lang="scss">
.edu-chat {
  display: flex;
  height: calc(100vh - 84px);
  padding: 0;
  background: #f0f2f5;
}

// 左侧会话列表
.chat-sidebar {
  width: 280px;
  background: #fff;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;

  .sidebar-header {
    padding: 16px;
    border-bottom: 1px solid #e8e8e8;
  }

  .session-list {
    flex: 1;
    overflow-y: auto;
    padding: 8px;
  }

  .session-item {
    padding: 12px;
    border-radius: 8px;
    cursor: pointer;
    margin-bottom: 4px;
    transition: all 0.2s;

    &:hover {
      background: #f5f7fa;
    }

    &.active {
      background: #e6f7ff;
      border-left: 3px solid #1890ff;
    }

    .session-title {
      font-size: 14px;
      color: #333;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .session-meta {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 4px;
      font-size: 12px;
      color: #999;
    }
  }

  .empty-tip {
    text-align: center;
    color: #999;
    padding: 20px;
  }
}

// 右侧对话区域
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

// 欢迎页
.welcome {
  text-align: center;
  padding: 80px 20px;

  .welcome-icon {
    font-size: 72px;
    margin-bottom: 20px;
  }

  h2 {
    color: #1a1a1a;
    margin-bottom: 12px;
    font-size: 28px;
  }

  p {
    color: #666;
    margin-bottom: 24px;
    font-size: 16px;
  }

  .agent-tags {
    display: flex;
    justify-content: center;
    gap: 12px;
    margin-bottom: 32px;
    flex-wrap: wrap;
  }

  .quick-actions {
    display: flex;
    justify-content: center;
    gap: 12px;
    flex-wrap: wrap;
  }
}

// 消息项
.message-item {
  display: flex;
  margin-bottom: 24px;

  &.user {
    flex-direction: row-reverse;

    .message-content {
      background: linear-gradient(135deg, #1890ff, #096dd9);
      color: #fff;
      border-radius: 16px 0 16px 16px;
      max-width: 65%;
    }

    .message-text {
      font-size: 15px;
    }
  }

  &.assistant {
    .message-content {
      max-width: 80%;
    }
  }
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
  background: #f0f2f5;
}

.message-content {
  margin: 0 12px;
}

.message-text {
  line-height: 1.8;
  word-break: break-word;
  font-size: 15px;
}

// Markdown 渲染样式
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
  h4 { font-size: 16px; }

  p {
    margin-bottom: 12px;
  }

  ul, ol {
    padding-left: 24px;
    margin-bottom: 12px;

    li {
      margin-bottom: 4px;
    }
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
    padding: 16px;
    border-radius: 8px;
    overflow-x: auto;
    margin: 12px 0;

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

    tr:hover td {
      background: #f5f7fa;
    }
  }

  a {
    color: #1890ff;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }

  hr {
    border: none;
    border-top: 1px solid #e8e8e8;
    margin: 16px 0;
  }

  img {
    max-width: 100%;
    border-radius: 4px;
  }
}

// 智能体工作流
.agent-workflow {
  background: #fafafa;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;

  &.working {
    background: linear-gradient(135deg, #f0f9ff, #e6f7ff);
    border-color: #91d5ff;
  }

  .workflow-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    font-size: 14px;
    font-weight: 500;
    color: #333;

    i {
      color: #1890ff;
    }
  }

  .workflow-phase {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    background: #fff;
    border-radius: 8px;
    margin-bottom: 12px;

    .phase-icon {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #1890ff;
      color: #fff;

      &.analyzing {
        animation: pulse 1.5s infinite;
      }
    }

    .phase-text {
      font-size: 14px;
      color: #666;
    }
  }
}

.workflow-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 13px;
  transition: all 0.3s;

  &.running {
    border-color: #1890ff;
    background: #e6f7ff;

    .step-icon {
      color: #1890ff;
    }
  }

  &.completed {
    border-color: #52c41a;
    background: #f6ffed;

    .step-icon {
      color: #52c41a;
    }
  }

  .step-icon {
    font-size: 16px;
    color: #999;
  }

  .step-info {
    display: flex;
    flex-direction: column;

    .step-name {
      font-weight: 500;
      color: #333;
    }

    .step-status {
      font-size: 12px;
      color: #999;

      &.success {
        color: #52c41a;
      }
    }

    .step-time {
      font-size: 12px;
      color: #999;
    }
  }
}

// 流式文本光标
.streaming-text {
  position: relative;

  .cursor {
    display: inline-block;
    animation: blink 1s infinite;
    color: #1890ff;
    font-weight: bold;
  }
}

// 资源区域
.resource-section {
  margin-top: 16px;

  .resource-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 500;
    color: #333;
    margin-bottom: 12px;

    i {
      color: #1890ff;
    }
  }
}

.resource-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.resource-card {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
  min-width: 200px;

  &:hover {
    border-color: #1890ff;
    box-shadow: 0 4px 12px rgba(24, 144, 255, 0.15);
    transform: translateY(-2px);
  }

  &.new {
    animation: slideIn 0.3s ease-out;
    border-color: #52c41a;
    background: #f6ffed;
  }

  .card-icon {
    font-size: 32px;
    margin-right: 12px;
  }

  .card-info {
    flex: 1;

    .card-name {
      font-size: 14px;
      font-weight: 500;
      color: #333;
      margin-bottom: 4px;
    }

    .card-meta {
      display: flex;
      align-items: center;
      gap: 8px;

      .quality {
        font-size: 12px;
        color: #52c41a;

        i {
          margin-right: 2px;
        }
      }
    }
  }

  .card-action {
    color: #999;
  }
}

// 输入区域
.input-area {
  padding: 20px 24px;
  border-top: 1px solid #e8e8e8;
  background: #fff;

  .input-wrapper {
    display: flex;
    gap: 12px;
    align-items: flex-end;
  }

  .send-btn {
    height: 56px;
    padding: 0 24px;
    font-size: 16px;
  }
}

// 资源详情弹窗
.resource-detail {
  max-height: 60vh;
  overflow-y: auto;

  .detail-image {
    text-align: center;
    padding: 20px;

    img {
      max-width: 100%;
      border-radius: 8px;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    }
  }

  .detail-code {
    pre {
      background: #1e1e1e;
      color: #d4d4d4;
      padding: 20px;
      border-radius: 8px;
      overflow-x: auto;

      code {
        font-family: 'Monaco', 'Menlo', monospace;
        font-size: 14px;
        line-height: 1.6;
      }
    }
  }

  .detail-content {
    line-height: 1.8;
    font-size: 15px;
  }
}

// 动画
@keyframes pulse {
  0% { opacity: 1; }
  50% { opacity: 0.5; }
  100% { opacity: 1; }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
