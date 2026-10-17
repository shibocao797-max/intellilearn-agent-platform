<template>
  <div class="chat-page" :class="{ 'trace-collapsed': traceCollapsed, 'trace-resizing': traceDragging }" :style="chatLayoutStyle">
    <section class="chat-stage">
      <div ref="chatScrollRef" class="chat-scroll" @scroll.passive="handleChatScroll">
        <div class="date-pill">今天</div>

        <div v-if="loadingHistory" class="history-loading">
          <SkeletonBlock height="120px" />
          <SkeletonBlock height="160px" />
        </div>

        <div v-for="message in messages" :key="message.id" class="message-row" :class="message.role">
          <div v-if="message.role !== 'user'" class="agent-avatar">
            <span class="material-symbols-outlined">smart_toy</span>
          </div>
          <div class="message-stack">
            <div v-if="message.role !== 'user'" class="message-meta">
              <strong>{{ displayMessageAgent(message) }}</strong>
              <span>系统</span>
            </div>
            <div class="message-bubble">
              <p>{{ displayMessageContent(message) || '正在生成回复...' }}</p>
              <div v-if="message.attachments?.length" class="message-attachments">
                <div v-for="attachment in message.attachments" :key="attachment.attachmentId || attachment.id || attachment.localId" class="message-attachment">
                  <span class="material-symbols-outlined">{{ attachmentIcon(attachment) }}</span>
                  <span>{{ attachment.name }}</span>
                  <small>{{ formatAttachmentSize(attachment.size) }}</small>
                </div>
              </div>
              <div v-if="visibleMessageResources(message).length" class="embedded-resources">
                <div class="resource-heading">
                  <span class="material-symbols-outlined">auto_awesome</span>
                  已生成资源
                </div>
                <div class="embedded-grid">
                  <button
                    v-for="resource in visibleMessageResources(message)"
                    :key="resource.persistedId || resource.id || resource.displayName"
                    type="button"
                    class="embedded-card"
                    :class="{
                      'image-card': resourceImage(resource),
                      'video-card': isVideoResource(resource),
                      'animation-card': isAnimationResource(resource),
                      processing: isResourceProcessing(resource),
                      'review-reviewing': resourceReviewStatus(resource) === 'reviewing'
                    }"
                    @click="openResource(resource)"
                  >
                    <div class="embedded-card-media">
                      <img
                        v-if="resourceImage(resource)"
                        class="embedded-thumb"
                        :src="resourceImage(resource)"
                        :alt="resource.displayName || resource.title || '生成图片'"
                      />
                      <div v-else class="embedded-card-placeholder" :class="`placeholder-${resourceTypeOf(resource)}`">
                        <div class="embedded-placeholder-top">
                          <span class="embedded-placeholder-type">{{ resourceTypeLabel(resourceTypeOf(resource)) }}</span>
                          <span class="embedded-placeholder-state" :class="{ processing: isResourceProcessing(resource) }">
                            {{ embeddedStateLabel(resource) }}
                          </span>
                        </div>
                        <div class="embedded-placeholder-main" :class="`main-${resourceTypeOf(resource)}`">
                          <template v-if="resourceTypeOf(resource) === 'code'">
                            <pre class="embedded-code-preview">{{ embeddedCodePreview(resource) }}</pre>
                          </template>
                          <template v-else-if="resourceTypeOf(resource) === 'quiz'">
                            <div class="embedded-quiz-preview">
                              <span>选择题</span>
                              <span>判断题</span>
                              <span>简答题</span>
                            </div>
                          </template>
                          <template v-else-if="resourceTypeOf(resource) === 'video'">
                            <div class="embedded-video-preview">
                              <span class="material-symbols-outlined">play_circle</span>
                              <strong>{{ isResourceProcessing(resource) ? '视频生成中' : '可点击播放' }}</strong>
                              <small>{{ embeddedSummary(resource) }}</small>
                            </div>
                          </template>
                          <template v-else-if="resourceTypeOf(resource) === 'animation'">
                            <div class="embedded-animation-preview">
                              <span class="material-symbols-outlined">animation</span>
                              <strong>{{ isResourceProcessing(resource) ? '动画生成中' : '交互动画' }}</strong>
                              <small>{{ embeddedSummary(resource) }}</small>
                            </div>
                          </template>
                          <template v-else-if="resourceTypeOf(resource) === 'mindmap'">
                            <div class="embedded-mindmap-preview">
                              <span class="node center"></span>
                              <span class="node top"></span>
                              <span class="node left"></span>
                              <span class="node right"></span>
                              <span class="line vertical"></span>
                              <span class="line left-branch"></span>
                              <span class="line right-branch"></span>
                            </div>
                          </template>
                          <template v-else>
                            <div class="embedded-text-preview">
                              <strong>{{ resource.displayName || resource.title || resourceTypeLabel(resourceTypeOf(resource)) }}</strong>
                              <p>{{ embeddedSummary(resource) }}</p>
                            </div>
                          </template>
                        </div>
                        <div class="embedded-placeholder-lines">
                          <span
                            v-for="(width, index) in embeddedPlaceholderLineWidths(resource)"
                            :key="index"
                            :style="{ width }"
                          ></span>
                        </div>
                        <div v-if="isResourceProcessing(resource)" class="embedded-progress-line">
                          <span :style="{ width: embeddedProgressWidth(resource) }"></span>
                        </div>
                      </div>
                    </div>
                    <div class="embedded-card-body">
                      <strong>{{ resource.displayName || resource.title || resourceTypeLabel(resourceTypeOf(resource)) }}</strong>
                      <p>{{ embeddedSummary(resource) }}</p>
                      <small>{{ resourceTypeLabel(resourceTypeOf(resource)) }} · {{ embeddedFooterLabel(resource) }}</small>
                    </div>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="historyError && !loadingHistory" class="chat-empty-guide">
          <UiState
            compact
            icon="cloud_off"
            title="会话记录暂时无法加载"
            :description="historyError"
            action-label="重新加载"
            action-icon="refresh"
            @action="loadConversation(activeSessionId)"
          />
        </div>

        <div v-else-if="!messages.length && !loadingHistory" class="chat-empty-guide">
          <UiState
            compact
            icon="forum"
            :title="chatGuide.emptyTitle"
            :description="chatGuide.emptyDescription"
            :action-label="chatGuide.primaryAction"
            action-icon="auto_awesome"
            @action="sendQuick"
          />
          <div class="chat-quick-suggestions" aria-label="快捷提问建议">
            <button
              v-for="suggestion in chatGuide.suggestions"
              :key="suggestion"
              type="button"
              @click="sendSuggestion(suggestion)"
            >
              {{ suggestion }}
            </button>
          </div>
        </div>
      </div>

      <div class="composer-wrap">
        <form class="composer" @submit.prevent="send">
          <input
            ref="attachmentInputRef"
            class="attachment-input"
            type="file"
            multiple
            tabindex="-1"
            @change="handleAttachmentSelection"
          />
          <div v-if="attachments.length" class="composer-attachments" aria-live="polite">
            <div v-for="attachment in attachments" :key="attachment.localId" class="composer-attachment" :class="attachment.status">
              <span class="material-symbols-outlined attachment-icon">{{ attachmentIcon(attachment) }}</span>
              <span class="attachment-copy">
                <strong :title="attachment.name">{{ attachment.name }}</strong>
                <small>{{ attachmentStatusLabel(attachment) }} · {{ formatAttachmentSize(attachment.size) }}</small>
              </span>
              <button
                v-if="attachment.status === 'error'"
                type="button"
                class="attachment-action"
                :title="attachment.errorAction === 'delete' ? '重试移除' : '重新上传'"
                :aria-label="attachment.errorAction === 'delete' ? '重试移除' : '重新上传'"
                :disabled="streaming"
                @click="retryAttachment(attachment)"
              >
                <span class="material-symbols-outlined">refresh</span>
              </button>
              <button
                type="button"
                class="attachment-action"
                title="移除附件"
                aria-label="移除附件"
                :disabled="streaming || attachment.status === 'removing'"
                @click="removeAttachment(attachment)"
              >
                <span class="material-symbols-outlined">close</span>
              </button>
            </div>
          </div>
          <textarea
            v-model="input"
            :disabled="streaming || speechProcessing"
            :placeholder="chatGuide.inputPlaceholder"
            @keydown.enter.exact.prevent="send"
          ></textarea>
          <div class="composer-actions">
            <div>
              <button
                type="button"
                class="icon-button"
                title="添加附件"
                aria-label="添加附件"
                :disabled="streaming || speechProcessing"
                @click="openAttachmentPicker"
              >
                <span class="material-symbols-outlined">attach_file</span>
              </button>
              <button
                type="button"
                class="icon-button speech-button"
                :class="{ recording: speechRecording, processing: speechProcessing }"
                :disabled="streaming || speechProcessing"
                :title="speechRecording ? '结束录音' : '语音输入'"
                :aria-label="speechRecording ? '结束录音' : '语音输入'"
                @click="toggleSpeechRecording"
              >
                <span class="material-symbols-outlined">{{ speechRecording ? 'stop_circle' : 'mic' }}</span>
              </button>
              <span v-if="speechStatusText" class="composer-speech-status" :class="{ recording: speechRecording, processing: speechProcessing }">
                <i></i>
                {{ speechStatusText }}
              </span>
            </div>
            <button
              v-if="streaming"
              class="send-button stop-task-button"
              type="button"
              :class="{ cancelling: taskCancellationPending }"
              :disabled="taskCancellationPending"
              :title="taskCancellationPending ? '正在停止任务' : '停止当前任务'"
              :aria-label="taskCancellationPending ? '正在停止任务' : '停止当前任务'"
              @click="cancelActiveTask"
            >
              <span class="material-symbols-outlined" :class="{ rotating: taskCancellationPending }">{{ taskCancellationPending ? 'progress_activity' : 'stop' }}</span>
            </button>
            <button
              v-else
              class="send-button"
              type="submit"
              :disabled="!canSend || speechRecording || speechProcessing || attachmentSendBlocked"
              aria-label="发送消息"
            >
              <span class="material-symbols-outlined">send</span>
            </button>
            <span class="sr-only" role="status" aria-live="polite" aria-atomic="true">{{ runtimeAnnouncement }}</span>
          </div>
        </form>
      </div>
    </section>

    <button
      v-if="traceCollapsed"
      ref="traceTriggerRef"
      class="edge-restore-button edge-restore-right"
      type="button"
      aria-label="查看生成过程"
      title="查看生成过程"
      aria-controls="chat-agent-trace"
      :aria-expanded="!traceCollapsed"
      @click="expandTrace"
    >
      <span class="material-symbols-outlined">chevron_left</span>
    </button>

    <div
      v-if="!traceCollapsed"
      class="trace-resize-handle"
      role="separator"
      aria-orientation="vertical"
      aria-label="调整右侧栏宽度"
      @pointerdown.prevent.stop="startTraceResize"
    ></div>

    <aside
      id="chat-agent-trace"
      ref="tracePanelRef"
      class="agent-trace"
      :class="{ collapsed: traceCollapsed }"
      tabindex="-1"
      :inert="traceCollapsed ? '' : undefined"
      :aria-hidden="traceCollapsed ? 'true' : 'false'"
    >
      <header class="trace-header">
        <button
          class="panel-toggle-button"
          type="button"
          aria-label="右侧栏操作"
          title="右侧栏操作"
          @click="handleTraceHeaderToggle"
        >
          <span class="material-symbols-outlined">
            {{ rightPanelMode === 'monitor' ? 'chevron_right' : 'view_sidebar' }}
          </span>
        </button>
        <div class="trace-heading">
          <h2>
            <span class="material-symbols-outlined">{{ rightPanelMode === 'monitor' ? 'psychology' : 'memory' }}</span>
            {{ rightPanelTitle }}
          </h2>
          <small>{{ rightPanelHint }}</small>
        </div>
      </header>

      <div class="trace-panel-tabs">
        <button type="button" class="trace-tab" :class="{ active: rightPanelMode === 'trace' }" @click="setRightPanelMode('trace')">
          生成过程
        </button>
        <button type="button" class="trace-tab" :class="{ active: rightPanelMode === 'monitor' }" @click="setRightPanelMode('monitor')">
          调用详情
        </button>
        <button type="button" class="trace-tab" :class="{ active: rightPanelMode === 'split' }" @click="setRightPanelMode('split')">
          完整详情
        </button>
      </div>

      <div class="agent-trace-body" :class="{ split: rightPanelMode === 'split' }">
        <section v-if="showMonitorPanel" class="trace-section monitor-section">
          <div class="trace-section-head">
            <div>
              <strong>智能体监控</strong>
              <small>{{ monitorSummary }}</small>
            </div>
            <span class="status-pill ai" :class="{ pulse: streaming }">{{ streaming ? '运行中' : '待命' }}</span>
          </div>

          <div class="monitor-groups">
            <section v-for="group in monitorGroups" :key="group.key" class="monitor-group">
              <header class="monitor-group-head">
                <strong>{{ group.title }}</strong>
                <small>{{ group.subtitle }}</small>
              </header>
              <div class="monitor-list">
                <article v-for="agent in group.items" :key="agent.key" class="monitor-item" :class="[agent.tone, { active: agent.active }]">
                  <div class="monitor-item-head">
                    <strong>{{ agent.label }}</strong>
                    <span class="monitor-badge" :class="agent.tone">{{ agent.statusLabel }}</span>
                  </div>
                  <p>{{ agent.description }}</p>
                  <small>{{ agent.meta }}</small>
                </article>
              </div>
            </section>
          </div>
        </section>

        <section v-if="showTracePanel" class="trace-section trace-section-panel">
          <div class="trace-status">
            <div>
              <span>当前阶段</span>
              <strong>{{ currentPhaseLabel }}</strong>
            </div>
            <span class="status-pill ai" :class="{ pulse: streaming }">{{ streaming ? '调度中' : '就绪' }}</span>
          </div>

          <div class="trace-list">
            <div v-for="step in traceSteps" :key="step.key" class="trace-item" :class="step.status">
              <div class="trace-dot">
                <span v-if="step.status === 'done'" class="material-symbols-outlined">check</span>
                <span v-if="step.status === 'running'" class="dot-inner"></span>
              </div>
              <div class="trace-card">
                <div>
                  <strong>{{ step.title }}</strong>
                  <small>{{ step.statusText }}</small>
                </div>
                <p>{{ step.description }}</p>
              </div>
            </div>
          </div>
        </section>
      </div>
    </aside>

    <div v-if="previewResource" class="resource-preview-backdrop" @click.self="closeResourcePreview">
      <section
        ref="resourcePreviewPanelRef"
        class="resource-preview-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="resource-preview-title"
        tabindex="-1"
      >
        <header>
          <div>
            <span class="status-pill primary">{{ resourceTypeLabel(previewResource.resourceType || previewResource.type) }}</span>
            <h3 id="resource-preview-title">{{ previewResource.displayName || previewResource.title || '生成资源' }}</h3>
          </div>
          <button ref="resourcePreviewCloseRef" class="icon-button" type="button" aria-label="关闭预览" title="关闭预览" @click="closeResourcePreview">
            <span class="material-symbols-outlined">close</span>
          </button>
        </header>
        <div class="resource-preview-body">
          <img
            v-if="resourceImage(previewResource)"
            :src="resourceImage(previewResource)"
            :alt="previewResource.displayName || previewResource.title || '生成图片'"
          />
          <iframe
            v-else-if="previewAnimationUrl"
            :src="previewAnimationUrl"
            class="preview-animation-frame"
            title="动画预览"
            sandbox="allow-scripts"
          ></iframe>
          <video
            v-else-if="resourceVideo(previewResource)"
            class="preview-video"
            :src="resourceVideo(previewResource)"
            controls
            playsinline
          ></video>
          <pre v-else-if="previewResourceText" class="preview-text-block">{{ previewResourceText }}</pre>
          <div v-else class="preview-status">
            <span class="material-symbols-outlined">{{ isVideoResource(previewResource) ? 'movie' : resourceIcon(previewResource.resourceType || previewResource.type) }}</span>
            <p>{{ previewStatusText(previewResource) }}</p>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { eduApi } from '@/services/api'
import { normalizeRightPanelMode, useUiStore } from '@/stores/ui'
import { DEFAULT_COORDINATOR_NICKNAME, useWorkspaceStore } from '@/stores/workspace'
import { useTaskRuntimeStore } from '@/stores/taskRuntime'
import { agentLabel, cleanAiDisplayText, dataOf, resourceIcon, resourceTypeLabel, safeJson, sanitizeDisplayText } from '@/utils/format'
import {
  extractResourceNumericId,
  normalizeResourcePayload,
  resolveResourceImage,
  resolveResourceVideo,
  uniqueResourceKey
} from '@/utils/resource'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import UiState from '@/components/UiState.vue'

const TRACE_LAYOUT_KEY_PREFIX = 'eduai-chat-trace-layout-v1'
const TRACE_MIN_WIDTH = 280
const TRACE_MAX_WIDTH = 420
const VIDEO_POLL_INTERVAL_MS = 4000
const VIDEO_POLL_MAX_ATTEMPTS = 30

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const workspace = useWorkspaceStore()
const taskRuntime = useTaskRuntimeStore()

const chatGuide = Object.freeze({
  emptyTitle: '从一个知识点开始学习',
  emptyDescription: '提出困惑、获得分步讲解，再继续完成练习和复盘。',
  primaryAction: '开始示例学习',
  inputPlaceholder: '输入想弄懂的知识点、困惑或练习需求...',
  quickPrompt: '请用高中生能理解的方式解释量子物理中的叠加态，并生成思维导图和练习题。',
  suggestions: ['先讲清核心概念', '给我一道分层练习题', '帮我制定下一步学习计划']
})

const messages = ref([])
const input = ref('')
const streaming = ref(false)
const loadingHistory = ref(false)
const historyError = ref('')
const selectedAgents = ref([])
const agentProgressMap = ref({})
const currentPhase = ref('')
const streamError = ref('')
const eventLog = ref([])
const traceWidth = ref(320)
const traceCollapsed = ref(true)
const traceDragging = ref(false)
const previewResource = ref(null)
const previewAnimationUrl = ref('')
const chatScrollRef = ref(null)
const followOutput = ref(true)
const attachmentInputRef = ref(null)
const attachments = ref([])
const traceTriggerRef = ref(null)
const tracePanelRef = ref(null)
const resourcePreviewPanelRef = ref(null)
const resourcePreviewCloseRef = ref(null)
const isTraceDrawerViewport = ref(false)
const speechRecording = ref(false)
const speechProcessing = ref(false)
const speechStatusText = ref('')
const speechChunks = ref([])
const speechAudioContext = ref(null)
const speechMediaStream = ref(null)
const speechSource = ref(null)
const speechProcessor = ref(null)
const MAX_SPEECH_RECORDING_MS = 5 * 60 * 1000

const activeSessionId = computed(() => workspace.state.activeSessionId)
const coordinatorNickname = computed(() => workspace.state.coordinatorNickname || DEFAULT_COORDINATOR_NICKNAME)
const activeRuntimeTask = computed(() => taskRuntime.taskForSession(activeSessionId.value))
const taskCancellationPending = computed(() => String(activeRuntimeTask.value?.status || '').toUpperCase() === 'CANCELLING')
const runtimeAnnouncement = computed(() => {
  const task = activeRuntimeTask.value
  if (!task) return ''
  const status = String(task.status || '').toUpperCase()
  if (status === 'CANCELLING') return '正在停止当前任务。'
  if (status === 'CANCELLED') return '当前任务已停止。'
  if (status === 'FAILED') return `当前任务执行失败。${task.error || ''}`
  if (status === 'SUCCEEDED') return '当前任务已完成，结果已经保存到会话。'
  const runningCount = Object.values(task.progressByAgent || {})
    .filter(progress => ['RUNNING', 'GENERATING', 'PROCESSING', 'REVISING', 'IN_PROGRESS'].includes(String(progress?.status || '').toUpperCase()))
    .length
  return runningCount
    ? `任务正在后台执行，当前有 ${runningCount} 个智能体运行。${phaseLabel(task.currentPhase)}`
    : `任务正在后台执行。${phaseLabel(task.currentPhase)}`
})
const attachmentSendBlocked = computed(() => attachments.value.some(attachment => attachment.status !== 'ready'))
const readyAttachmentIds = computed(() => attachments.value
  .filter(attachment => attachment.status === 'ready' && attachment.attachmentId)
  .map(attachment => attachment.attachmentId))
const canSend = computed(() => Boolean(input.value.trim() || readyAttachmentIds.value.length))
const rightPanelMode = computed(() => ui.rightPanelMode.value)
const showTracePanel = computed(() => rightPanelMode.value !== 'monitor')
const showMonitorPanel = computed(() => rightPanelMode.value !== 'trace')
const chatLayoutStyle = computed(() => ({
  '--trace-runtime-width': `${traceCollapsed.value ? 0 : traceWidth.value}px`
}))

let latestResourceKeys = new Set()
let stopTraceDrag = null
let latestConversationRequest = 0
let loadedConversationSessionId = ''
let scrollFrame = 0
let hasBootstrappedConversation = false
let speechStopTimer = null
let speechRequestVersion = 0
let speechAbortController = null
let speechDisposed = false
let pageActive = true
let lifecycleSuspended = false
let suspendedSessionId = null
let conversationLoadInterrupted = false
let pageListenersAttached = false
let traceDrawerMediaQuery = null
let traceRestoreFocusTarget = null
let previewRestoreFocusTarget = null
let previewRequestVersion = 0
const videoPollers = new Map()
const reloadedTerminalTasks = new Set()
const ACTIVE_TASK_STATUSES = new Set(['CREATED', 'ACCEPTED', 'PENDING', 'QUEUED', 'RUNNING', 'PROCESSING', 'IN_PROGRESS', 'CANCELLING'])
let failedTaskSubmission = null

const monitorDefinitions = [
  { key: 'textReply', description: '负责直接回答问题与对话讲解。', fixed: false },
  { key: 'docGen', description: '负责生成系统化学习文档。', fixed: false },
  { key: 'quizGen', description: '负责生成练习题与测验题库。', fixed: false },
  { key: 'mindmapGen', description: '负责生成知识结构与思维导图。', fixed: false },
  { key: 'codeGen', description: '负责生成代码案例与编程示例。', fixed: false },
  { key: 'readingExpand', description: '负责补充阅读资料与拓展内容。', fixed: false },
  { key: 'imageGen', description: '负责生成图片、配图与图解。', fixed: false },
  { key: 'videoGen', description: '负责生成教学视频资源。', fixed: false },
  { key: 'animationGen', description: '负责生成可播放教学动画。', fixed: false },
  { key: 'profileUpdate', description: '负责更新学习画像。', fixed: true },
  { key: 'qualityReview', description: '负责审核生成内容质量。', fixed: true },
  { key: 'learningPath', description: '负责整理阶段化学习路径。', fixed: true },
  { key: 'resourcePlanner', description: '负责汇总资源排序与推荐。', fixed: true }
]

const currentPhaseLabel = computed(() => phaseLabel(currentPhase.value))
const rightPanelTitle = computed(() => {
  if (rightPanelMode.value === 'monitor') return '调用详情'
  if (rightPanelMode.value === 'split') return '完整执行详情'
  return '生成过程'
})
const rightPanelHint = computed(() => {
  if (rightPanelMode.value === 'monitor') return '按需查看本轮实际参与的 Agent 与执行状态。'
  if (rightPanelMode.value === 'split') return '同时查看学习任务推进和本轮调用明细。'
  return '仅在需要时查看本轮学习任务的处理进度。'
})
const monitorItems = computed(() => monitorDefinitions.map(definition => {
  const progress = agentProgressMap.value[definition.key] || null
  const selected = selectedAgents.value.includes(definition.key)
  const active = selected || Boolean(progress)
  const status = String(progress?.status || (selected ? 'selected' : definition.fixed ? 'standby' : 'idle')).toLowerCase()
  return {
    key: definition.key,
    label: agentLabel(definition.key),
    description: definition.description,
    active,
    status,
    statusLabel: monitorStatusLabel(status),
    tone: monitorTone(status, active),
    meta: progress?.durationMs
      ? `耗时 ${formatDuration(progress.durationMs)}`
      : definition.fixed
        ? '等待服务端流程调度'
        : selected
          ? '已加入本轮调度'
          : '本轮未调用'
  }
}))
const monitorSummary = computed(() => {
  const activeCount = monitorItems.value.filter(item => item.active).length
  const completedCount = monitorItems.value.filter(item => item.status === 'completed' || item.status === 'revised').length
  return `本轮已调用 ${activeCount} 个，已完成 ${completedCount} 个`
})
const monitorGroups = computed(() => ([
  {
    key: 'selected',
    title: '本轮已调用',
    subtitle: '当前会话实际参与的智能体',
    items: monitorItems.value.filter(item => item.active)
  },
  {
    key: 'available',
    title: '可调用智能体',
    subtitle: '当前系统可用，但本轮未参与',
    items: monitorItems.value.filter(item => !item.active)
  }
]))
const traceSteps = computed(() => {
  const phaseOrder = {
    START: 0,
    PROFILE_LOADED: 0,
    PROFILE_UPDATED: 1,
    DISPATCHING: 2,
    AGENTS_DONE: 2,
    QUALITY_REVIEWING: 3,
    QUALITY_CORRECTING: 3,
    QUALITY_REVIEWED: 3,
    LEARNING_PATH_PLANNING: 4,
    LEARNING_PATH_READY: 4,
    LEARNING_PATH_SKIPPED: 4,
    RESOURCE_PLANNING: 4,
    DONE: 5
  }
  const doneIndex = phaseOrder[currentPhase.value] ?? -1
  const specs = [
    ['intent', '意图分析', '识别当前问题的学习目标与资源需求。'],
    ['profile', '画像更新', '加载并更新当前学习者画像。'],
    ['dispatch', '智能体调度', selectedAgents.value.length ? selectedAgents.value.map(agentLabel).join('、') : '根据问题自动选择合适的智能体。'],
    ['review', '质量审核', '审核生成内容并决定是否修订。'],
    ['path', '学习路径', '整理下一步建议和资源组合。']
  ]
  return specs.map((item, index) => {
    const running = streaming.value && index === Math.max(0, Math.min(specs.length - 1, doneIndex < 0 ? 0 : doneIndex))
    const done = doneIndex > index || (!streaming.value && currentPhase.value === 'DONE')
    return {
      key: item[0],
      title: item[1],
      description: item[2],
      status: done ? 'done' : running ? 'running' : 'pending',
      statusText: done ? '完成' : running ? '运行中' : '待处理'
    }
  })
})
const previewResourceText = computed(() => {
  const resource = previewResource.value
  if (!resource) return ''
  if (resourceImage(resource) || resourceVideo(resource) || previewAnimationUrl.value) return ''

  const content = String(resource.content || '').trim()
  const description = String(resource.description || '').trim()
  const title = String(resource.title || resource.displayName || '').trim()
  const type = String(resource.resourceType || resource.type || '').toLowerCase()

  if (content) return cleanAiDisplayText(content, { keepCodeBlocks: false })
  if (description) return cleanAiDisplayText(description, { keepCodeBlocks: false })
  if (type === 'quiz') {
    return title ? `题库资源：${title}\n\n该题库已经保留在当前会话中，可继续在会话上下文里查看与追问。` : '该题库资源已生成。'
  }
  return ''
})

function displayMessageContent(message) {
  if (message?.role === 'user' && !message?.content && message?.attachments?.length) return '已发送附件'
  if (message?.role !== 'user' && !message?.content && message?.statusMessage) return message.statusMessage
  if (message?.role !== 'user' && !message?.content && message?.taskStatus === 'SUCCEEDED') return '任务已完成，但没有返回可展示内容。'
  return cleanAiDisplayText(message?.content || '', { keepCodeBlocks: true })
}

function phaseLabel(phase) {
  const map = {
    START: '开始分析',
    PROFILE_LOADED: '画像加载',
    PROFILE_UPDATED: '画像更新',
    INTENT_ANALYZED: '意图分析完成',
    QUALITY_REVIEWING: '质量审核',
    QUALITY_CORRECTING: '内容修订',
    QUALITY_REVIEWED: '质量审核完成',
    LEARNING_PATH_PLANNING: '学习路径规划',
    LEARNING_PATH_READY: '学习路径已就绪',
    LEARNING_PATH_SKIPPED: '跳过学习路径',
    RESOURCE_PLANNING: '资源规划',
    RESOURCE_PLAN_READY: '资源规划已就绪',
    RESOURCE_PLAN_SKIPPED: '跳过资源规划',
    AGENTS_DONE: '智能体完成',
    DONE: '已完成',
    FAILED: '执行失败',
    CANCELLED: '已停止'
  }
  return map[String(phase || '').trim()] || '等待输入'
}

function monitorStatusLabel(status) {
  const map = {
    idle: '未调用',
    standby: '待命',
    selected: '已调度',
    running: '运行中',
    completed: '已完成',
    revising: '修订中',
    revised: '已修订',
    failed: '失败',
    revision_failed: '修订失败'
  }
  return map[status] || '待命'
}

function monitorTone(status, active) {
  if (status === 'failed' || status === 'revision_failed') return 'danger'
  if (status === 'completed' || status === 'revised') return 'success'
  if (status === 'running' || status === 'revising') return 'running'
  if (active) return 'active'
  return 'idle'
}

function formatDuration(durationMs) {
  const value = Number(durationMs)
  if (!Number.isFinite(value) || value <= 0) return ''
  if (value < 1000) return `${Math.round(value)} ms`
  return `${(value / 1000).toFixed(value >= 10000 ? 0 : 1)} s`
}

function setRightPanelMode(mode) {
  const nextMode = normalizeRightPanelMode(mode)
  ui.setRightPanelMode(nextMode)

  const nextQuery = { ...route.query }
  if (nextMode === 'trace') {
    delete nextQuery.panel
  } else {
    nextQuery.panel = nextMode
  }

  router.replace({
    path: route.path,
    query: nextQuery
  })
}

function handleTraceHeaderToggle() {
  if (rightPanelMode.value === 'trace' || rightPanelMode.value === 'split') {
    setRightPanelMode('monitor')
    return
  }
  collapseTrace()
}

function syncRightPanelModeFromRoute() {
  const nextMode = normalizeRightPanelMode(String(route.query.panel || ''), 'trace')
  ui.setRightPanelMode(nextMode)
}

function appendAssistant() {
  latestResourceKeys = new Set()
  const message = {
    id: `assistant-${Date.now()}`,
    role: 'assistant',
    content: '',
    resources: []
  }
  messages.value.push(message)
  scheduleScrollToBottom('smooth')
  return message
}

function displayMessageAgent(message) {
  const agent = String(message?.agent || message?.agentName || '').trim()
  if (!agent || isCoordinatorAgent(agent)) return coordinatorNickname.value
  return agent
}

function isCoordinatorAgent(value) {
  const normalized = String(value || '').trim().toLowerCase().replace(/[\s_-]+/g, '')
  return [
    'dispatcher',
    'coordinator',
    'orchestrator',
    'maincoordinator',
    '主协调agent',
    '主协调智能体',
    '协调智能体'
  ].includes(normalized)
}

function appendResource(targetMessage, payload) {
  const resource = normalizeResourcePayload(payload)
  if (!resource) return
  const stored = upsertMessageResource(targetMessage, resource)
  const key = uniqueResourceKey(stored)
  if (key) latestResourceKeys.add(key)
  ensureVideoPolling(stored)
  scheduleScrollToBottom('smooth')
}

function sameConversationMessage(left, right) {
  if (left?.id && right?.id && String(left.id) === String(right.id)) return true
  const leftContent = String(left?.content || '').trim()
  const rightContent = String(right?.content || '').trim()
  return Boolean(left?.role && right?.role && left.role === right.role && leftContent && leftContent === rightContent)
}

function mergeConversationMessages(historyMessages, localMessages) {
  const merged = [...historyMessages]
  const matchedIndexes = new Set()

  localMessages.forEach(localMessage => {
    let matchIndex = -1
    for (let index = merged.length - 1; index >= 0; index -= 1) {
      if (!matchedIndexes.has(index) && sameConversationMessage(localMessage, merged[index])) {
        matchIndex = index
        break
      }
    }

    if (matchIndex < 0) {
      merged.push(localMessage)
      return
    }

    matchedIndexes.add(matchIndex)
    const serverMessage = merged[matchIndex]
    merged[matchIndex] = {
      ...serverMessage,
      taskId: localMessage.taskId || serverMessage.taskId,
      pendingTask: localMessage.pendingTask || serverMessage.pendingTask,
      taskStatus: localMessage.taskStatus || serverMessage.taskStatus,
      statusMessage: localMessage.statusMessage || serverMessage.statusMessage,
      resources: serverMessage.resources?.length ? serverMessage.resources : (localMessage.resources || []),
      attachments: serverMessage.attachments?.length ? serverMessage.attachments : (localMessage.attachments || [])
    }
  })

  return merged
}

async function loadConversation(targetSessionId = activeSessionId.value) {
  const requestId = ++latestConversationRequest
  const normalizedSessionId = String(targetSessionId || '').trim()

  if (!normalizedSessionId) {
    messages.value = []
    loadedConversationSessionId = ''
    historyError.value = ''
    return
  }

  const sameSession = loadedConversationSessionId === normalizedSessionId
  if (!sameSession) {
    messages.value = []
    loadedConversationSessionId = normalizedSessionId
  }

  loadingHistory.value = true
  historyError.value = ''

  try {
    const res = await eduApi.conversation(normalizedSessionId)
    if (requestId !== latestConversationRequest) return

    const rows = dataOf(res, [])
    const historyMessages = rows.map(row => {
      const metadata = safeJson(row.metadataJson, {})
      const resources = (row.resources || metadata?.resources || metadata?.generatedResources || [])
        .map(item => normalizeResourcePayload(item))
        .filter(Boolean)
      const attachments = (row.attachments || metadata?.attachments || [])
        .map(normalizeMessageAttachment)
        .filter(Boolean)

      return {
        id: row.id || `${row.role}-${row.createTime}`,
        role: row.role,
        agent: row.agentName || row.agent || metadata?.agentName || metadata?.agent || metadata?.sourceAgent || '',
        content: row.content,
        taskId: row.taskId || metadata?.taskId || '',
        resources,
        attachments
      }
    })

    messages.value = mergeConversationMessages(historyMessages, messages.value)

    messages.value.forEach(message => {
      message.resources?.forEach(resource => ensureVideoPolling(resource))
    })

    await nextTick()
    scrollToBottom('auto')
  } catch (error) {
    if (requestId !== latestConversationRequest) return
    historyError.value = error?.message || '请检查网络连接或稍后重试。'
  } finally {
    if (requestId === latestConversationRequest) {
      loadingHistory.value = false
    }
  }
}

async function createSession() {
  await workspace.createSessionInActiveProject()
  messages.value = []
  currentPhase.value = ''
  agentProgressMap.value = {}
  followOutput.value = true
  await nextTick()
  scrollToBottom('auto')
}

async function sendQuick() {
  input.value = chatGuide.quickPrompt
  await send()
}

async function sendSuggestion(suggestion) {
  input.value = suggestion
  await send()
}

function openAttachmentPicker() {
  if (streaming.value || speechProcessing.value) return
  attachmentInputRef.value?.click()
}

async function handleAttachmentSelection(event) {
  const files = Array.from(event.target?.files || [])
  if (event.target) event.target.value = ''
  if (!files.length || streaming.value) return

  try {
    if (!activeSessionId.value) await createSession()
  } catch (error) {
    window.alert(error?.message || '创建会话失败，暂时无法上传附件。')
    return
  }

  if (!pageActive || !activeSessionId.value) return
  const newAttachments = files.map(file => ({
    localId: `attachment-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    file,
    name: file.name || '未命名文件',
    size: file.size,
    type: file.type,
    status: 'uploading',
    attachmentId: null,
    error: '',
    errorAction: ''
  }))
  attachments.value.push(...newAttachments)
  await Promise.all(newAttachments.map(uploadAttachment))
}

async function uploadAttachment(attachment) {
  const current = attachments.value.find(item => item.localId === attachment.localId)
  if (!pageActive || !current || !current.file) return

  current.status = 'uploading'
  current.error = ''
  current.errorAction = ''
  current.controller?.abort()
  const controller = new AbortController()
  current.controller = controller
  const data = new FormData()
  data.append('file', current.file)
  data.append('sessionId', activeSessionId.value)

  try {
    const response = await eduApi.uploadChatAttachment(data, { signal: controller.signal })
    if (controller.signal.aborted) return
    const payload = response?.data || response || {}
    const attachmentId = payload.attachmentId ?? payload.id
    if (attachmentId === undefined || attachmentId === null || attachmentId === '') {
      throw new Error('附件上传接口未返回附件编号')
    }
    const pending = attachments.value.find(item => item.localId === attachment.localId)
    if (!pending) return
    pending.attachmentId = attachmentId
    pending.status = 'ready'
    pending.serverStatus = String(payload.status || '')
    pending.controller = null
    pending.errorAction = ''
  } catch (error) {
    const pending = attachments.value.find(item => item.localId === attachment.localId)
    if (!pending || controller.signal.aborted || error?.name === 'AbortError') return
    pending.status = 'error'
    pending.error = error?.message || '上传失败，请重试。'
    pending.controller = null
    pending.errorAction = 'upload'
  }
}

async function retryAttachment(attachment) {
  if (streaming.value || attachment.status === 'uploading' || attachment.status === 'removing') return
  if (attachment.errorAction === 'delete') {
    await removeAttachment(attachment)
    return
  }
  if (!activeSessionId.value) {
    try {
      await createSession()
    } catch (error) {
      attachment.error = error?.message || '创建会话失败，无法重新上传。'
      return
    }
  }
  await uploadAttachment(attachment)
}

async function removeAttachment(attachment) {
  if (attachment.status === 'removing' || streaming.value) return
  if (attachment.status === 'uploading') {
    attachment.controller?.abort()
    attachments.value = attachments.value.filter(item => item.localId !== attachment.localId)
    return
  }
  if (!attachment.attachmentId) {
    attachments.value = attachments.value.filter(item => item.localId !== attachment.localId)
    return
  }

  attachment.status = 'removing'
  try {
    await eduApi.deleteChatAttachment(attachment.attachmentId)
    attachments.value = attachments.value.filter(item => item.localId !== attachment.localId)
  } catch (error) {
    attachment.status = 'error'
    attachment.error = error?.message || '移除附件失败，请重试。'
    attachment.errorAction = 'delete'
  }
}

function normalizeMessageAttachment(attachment) {
  if (!attachment || typeof attachment !== 'object') return null
  const attachmentId = attachment.attachmentId ?? attachment.id
  return {
    attachmentId,
    name: attachment.name || attachment.fileName || '附件',
    size: Number(attachment.size ?? attachment.fileSize ?? 0),
    type: attachment.type || attachment.contentType || ''
  }
}

function attachmentIcon(attachment) {
  const type = String(attachment?.type || '').toLowerCase()
  const name = String(attachment?.name || '').toLowerCase()
  if (type.startsWith('image/') || /\.(png|jpe?g|gif|webp|bmp|svg)$/.test(name)) return 'image'
  if (type.includes('pdf') || name.endsWith('.pdf')) return 'picture_as_pdf'
  if (type.includes('sheet') || /\.(csv|xls|xlsx)$/.test(name)) return 'table_chart'
  if (type.includes('presentation') || /\.(ppt|pptx)$/.test(name)) return 'slideshow'
  if (type.includes('zip') || /\.(zip|rar|7z)$/.test(name)) return 'folder_zip'
  return 'description'
}

function attachmentStatusLabel(attachment) {
  if (attachment.status === 'uploading') return '上传中'
  if (attachment.status === 'removing') return '正在移除'
  if (attachment.status === 'ready' && attachment.serverStatus === 'READY_IMAGE') return '图片已上传，当前对话不读取图片内容'
  if (attachment.status === 'ready') return '已附加'
  return attachment.error || '上传失败'
}

function formatAttachmentSize(size) {
  const bytes = Number(size)
  if (!Number.isFinite(bytes) || bytes <= 0) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

async function toggleSpeechRecording() {
  if (streaming.value || speechProcessing.value) return
  if (speechRecording.value) {
    await stopSpeechRecording()
    return
  }
  await startSpeechRecording()
}

async function startSpeechRecording() {
  if (!navigator.mediaDevices?.getUserMedia || !(window.AudioContext || window.webkitAudioContext)) {
    speechStatusText.value = ''
    window.alert('当前浏览器不支持语音输入，请使用最新版 Chrome 或 Edge。')
    return
  }

  speechChunks.value = []
  speechProcessing.value = true
  speechStatusText.value = '正在准备麦克风...'
  const requestVersion = ++speechRequestVersion
  try {
    const AudioContextCtor = window.AudioContext || window.webkitAudioContext
    const mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    if (speechDisposed || requestVersion !== speechRequestVersion) {
      mediaStream.getTracks().forEach(track => track.stop())
      return
    }
    const audioContext = new AudioContextCtor()
    if (speechDisposed || requestVersion !== speechRequestVersion) {
      mediaStream.getTracks().forEach(track => track.stop())
      await audioContext.close().catch(() => {})
      return
    }
    await audioContext.resume()
    const source = audioContext.createMediaStreamSource(mediaStream)
    const processor = audioContext.createScriptProcessor(2048, 1, 1)

    processor.onaudioprocess = event => {
      if (!speechRecording.value) return
      const channel = event.inputBuffer.getChannelData(0)
      const pcm = floatTo16BitPcm(downsampleBuffer(channel, audioContext.sampleRate, 16000))
      if (pcm.byteLength > 0) {
        speechChunks.value.push(new Int16Array(pcm.slice(0)))
      }
    }

    source.connect(processor)
    processor.connect(audioContext.destination)
    speechMediaStream.value = mediaStream
    speechAudioContext.value = audioContext
    speechSource.value = source
    speechProcessor.value = processor
    speechRecording.value = true
    speechStatusText.value = '正在录音，点击麦克风结束'
    speechStopTimer = window.setTimeout(() => {
      if (speechRecording.value) stopSpeechRecording({ timedOut: true })
    }, MAX_SPEECH_RECORDING_MS)
  } catch (error) {
    if (speechDisposed || requestVersion !== speechRequestVersion) return
    cleanupSpeechAudio()
    speechStatusText.value = ''
    window.alert(error?.name === 'NotAllowedError'
      ? '麦克风权限未开启，请在浏览器地址栏允许使用麦克风后重试。'
      : (error?.message || '启动语音输入失败，请检查麦克风。'))
  } finally {
    if (requestVersion === speechRequestVersion) speechProcessing.value = false
  }
}

async function stopSpeechRecording({ timedOut = false } = {}) {
  const chunks = speechChunks.value.slice()
  speechChunks.value = []
  speechRecording.value = false
  if (speechStopTimer) {
    window.clearTimeout(speechStopTimer)
    speechStopTimer = null
  }
  cleanupSpeechAudio()

  if (!chunks.length) {
    speechStatusText.value = ''
    window.alert('没有采集到语音，请重新录制。')
    return
  }

  speechProcessing.value = true
  speechStatusText.value = timedOut ? '录音达到上限，正在识别语音...' : '录音已结束，正在识别语音...'
  const requestVersion = ++speechRequestVersion
  speechAbortController?.abort()
  speechAbortController = new AbortController()
  try {
    const samples = mergePcmChunks(chunks)
    const audioData = encodeWavBase64(samples, 16000)
    const response = await eduApi.transcribeSpeech(
      { audioData, mimeType: 'audio/wav' },
      { signal: speechAbortController.signal }
    )
    if (speechDisposed || requestVersion !== speechRequestVersion) return
    const text = String(response?.data?.text || '').trim()
    if (!text) {
      throw new Error('未识别到有效语音内容')
    }
    input.value = input.value ? `${input.value} ${text}` : text
    speechStatusText.value = '语音已转成文字，可直接发送'
  } catch (error) {
    if (speechDisposed || requestVersion !== speechRequestVersion || error?.name === 'AbortError') return
    speechStatusText.value = ''
    window.alert(error?.message || '语音识别失败，请重试。')
  } finally {
    if (requestVersion === speechRequestVersion) {
      speechProcessing.value = false
      speechAbortController = null
    }
  }
}

function cleanupSpeechAudio() {
  if (speechProcessor.value) {
    speechProcessor.value.disconnect()
    speechProcessor.value.onaudioprocess = null
    speechProcessor.value = null
  }
  if (speechSource.value) {
    speechSource.value.disconnect()
    speechSource.value = null
  }
  if (speechMediaStream.value) {
    speechMediaStream.value.getTracks().forEach(track => track.stop())
    speechMediaStream.value = null
  }
  const audioContext = speechAudioContext.value
  speechAudioContext.value = null
  audioContext?.close().catch(() => {})
}

function downsampleBuffer(buffer, inputRate, targetRate) {
  if (inputRate === targetRate) return buffer
  const sampleRateRatio = inputRate / targetRate
  const result = new Float32Array(Math.round(buffer.length / sampleRateRatio))
  let offsetBuffer = 0
  for (let offsetResult = 0; offsetResult < result.length; offsetResult += 1) {
    const nextOffsetBuffer = Math.round((offsetResult + 1) * sampleRateRatio)
    let total = 0
    let count = 0
    for (let index = offsetBuffer; index < nextOffsetBuffer && index < buffer.length; index += 1) {
      total += buffer[index]
      count += 1
    }
    result[offsetResult] = count ? total / count : 0
    offsetBuffer = nextOffsetBuffer
  }
  return result
}

function floatTo16BitPcm(float32Array) {
  const pcmBuffer = new ArrayBuffer(float32Array.length * 2)
  const view = new DataView(pcmBuffer)
  for (let index = 0; index < float32Array.length; index += 1) {
    const sample = Math.max(-1, Math.min(1, float32Array[index]))
    view.setInt16(index * 2, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
  }
  return pcmBuffer
}

function mergePcmChunks(chunks) {
  const length = chunks.reduce((total, chunk) => total + chunk.length, 0)
  const merged = new Int16Array(length)
  let offset = 0
  chunks.forEach(chunk => {
    merged.set(chunk, offset)
    offset += chunk.length
  })
  return merged
}

function encodeWavBase64(samples, sampleRate) {
  const wavBuffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(wavBuffer)
  const writeString = (offset, value) => {
    for (let index = 0; index < value.length; index += 1) view.setUint8(offset + index, value.charCodeAt(index))
  }
  writeString(0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeString(8, 'WAVE')
  writeString(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeString(36, 'data')
  view.setUint32(40, samples.length * 2, true)
  samples.forEach((sample, index) => view.setInt16(44 + index * 2, sample, true))

  const bytes = new Uint8Array(wavBuffer)
  let binary = ''
  for (let offset = 0; offset < bytes.length; offset += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000))
  }
  return btoa(binary)
}

async function send() {
  const text = input.value.trim()
  const outgoingAttachments = attachments.value.filter(attachment => attachment.status === 'ready' && attachment.attachmentId)
  if ((!text && !outgoingAttachments.length) || streaming.value || speechRecording.value || speechProcessing.value || attachmentSendBlocked.value) return
  if (!activeSessionId.value) await createSession()
  if (!activeSessionId.value) return

  const attachmentIds = outgoingAttachments.map(attachment => attachment.attachmentId)
  const signature = taskSubmissionSignature(activeSessionId.value, text, attachmentIds)
  const retrySubmission = failedTaskSubmission?.signature === signature ? failedTaskSubmission : null
  const userMessage = retrySubmission?.userMessage || {
      id: `user-${Date.now()}`,
      role: 'user',
      content: text,
      attachments: outgoingAttachments.map(normalizeMessageAttachment)
    }
  const sentAttachmentLocalIds = new Set(outgoingAttachments.map(attachment => attachment.localId))
  followOutput.value = true
  if (!retrySubmission) messages.value.push(userMessage)
  const assistant = retrySubmission?.assistant || appendAssistant()
  assistant.content = ''
  assistant.pendingTask = true
  input.value = ''
  streaming.value = true
  currentPhase.value = 'START'
  streamError.value = ''
  selectedAgents.value = []
  agentProgressMap.value = {}
  eventLog.value = []
  const clientRequestId = retrySubmission?.clientRequestId || createClientRequestId()

  try {
    const task = await taskRuntime.startTask({
      message: text,
      sessionId: activeSessionId.value,
      attachmentIds,
      clientRequestId
    })
    failedTaskSubmission = null
    assistant.taskId = task?.taskId || ''
    assistant.pendingTask = false
    attachments.value = attachments.value.filter(attachment => !sentAttachmentLocalIds.has(attachment.localId))
    syncRuntimeTask(task)
  } catch (error) {
    failedTaskSubmission = {
      signature,
      clientRequestId: retrySubmission?.clientRequestId || clientRequestId,
      userMessage,
      assistant
    }
    assistant.pendingTask = false
    streaming.value = false
    streamError.value = error?.message || '任务创建失败，请稍后重试。'
    currentPhase.value = 'FAILED'
    assistant.content = streamError.value
    input.value = text
  }

  await nextTick()
  scrollToBottom('smooth')
}

function createClientRequestId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function taskSubmissionSignature(sessionId, text, attachmentIds) {
  return JSON.stringify([String(sessionId || ''), text, [...attachmentIds].map(String).sort()])
}

async function cancelActiveTask() {
  const task = activeRuntimeTask.value
  const status = String(task?.status || '').toUpperCase()
  if (!task?.taskId || status === 'CANCELLING' || !ACTIVE_TASK_STATUSES.has(status)) return
  try {
    await taskRuntime.cancelTask(task.taskId)
  } catch (error) {
    const assistant = messages.value.find(message => message.taskId === task.taskId)
    if (assistant && !assistant.content) assistant.statusMessage = error?.message || '停止任务失败，请稍后重试。'
    streamError.value = error?.message || '停止任务失败，请稍后重试。'
  }
}

function syncRuntimeTask(task) {
  const taskSessionId = String(task?.sessionId || '').trim()
  const currentSessionId = String(activeSessionId.value || '').trim()
  if (!task || !taskSessionId || taskSessionId !== currentSessionId) {
    streaming.value = false
    return
  }

  const status = String(task.status || '').toUpperCase()
  streaming.value = ACTIVE_TASK_STATUSES.has(status)
  currentPhase.value = task.currentPhase || (status === 'SUCCEEDED' ? 'DONE' : status === 'FAILED' ? 'FAILED' : status === 'CANCELLED' ? 'CANCELLED' : currentPhase.value)
  selectedAgents.value = Array.isArray(task.selectedAgents) ? [...task.selectedAgents] : []
  agentProgressMap.value = task.progressByAgent && typeof task.progressByAgent === 'object' ? { ...task.progressByAgent } : {}
  eventLog.value = Array.isArray(task.events)
    ? task.events.map(event => ({ eventName: event.eventType || event.eventName || 'message', payload: event.payload }))
    : []
  streamError.value = sanitizeDisplayText(task.error || task.errorMessage || '')

  let assistant = messages.value.find(message => message.taskId === task.taskId)
  if (!assistant && task.taskId) {
    assistant = [...messages.value].reverse().find(message => message.role === 'assistant' && message.pendingTask)
    if (assistant) {
      assistant.taskId = task.taskId
      assistant.pendingTask = false
    }
  }
  if (!assistant && task.taskId) {
    assistant = appendAssistant()
    assistant.taskId = task.taskId
  }
  if (assistant) {
    assistant.taskStatus = status
    assistant.content = String(task.text || assistant.content || '')
    assistant.statusMessage = !assistant.content && streamError.value ? streamError.value : ''
    const runtimeResources = Array.isArray(task.resources) ? task.resources : []
    runtimeResources.forEach(resource => appendResource(assistant, resource))
    if (status === 'FAILED' && streamError.value && !assistant.content) assistant.content = streamError.value
    if (status === 'CANCELLED' && !assistant.content) assistant.content = '本轮任务已停止。'
  }

  if (!ACTIVE_TASK_STATUSES.has(status) && task.taskId && !reloadedTerminalTasks.has(task.taskId)) {
    reloadedTerminalTasks.add(task.taskId)
    workspace.refreshSessions()
  }
}

function openResource(resource) {
  if (pageActive && document.activeElement instanceof HTMLElement) {
    previewRestoreFocusTarget = document.activeElement
  }
  openResourcePreview(resource)
}

function closeResourcePreview(options = {}) {
  const restoreFocus = options?.restoreFocus !== false
  previewRequestVersion += 1
  if (previewAnimationUrl.value) {
    URL.revokeObjectURL(previewAnimationUrl.value)
  }
  previewAnimationUrl.value = ''
  previewResource.value = null

  const restoreTarget = previewRestoreFocusTarget
  previewRestoreFocusTarget = null
  if (restoreFocus && pageActive) {
    nextTick(() => focusElement(restoreTarget))
  }
}

async function openResourcePreview(resource) {
  const normalized = normalizeResourcePayload(resource)
  if (!normalized) return
  const requestVersion = ++previewRequestVersion

  previewResource.value = { ...normalized }
  if (previewAnimationUrl.value) {
    URL.revokeObjectURL(previewAnimationUrl.value)
  }
  previewAnimationUrl.value = ''
  await nextTick()
  if (!pageActive || requestVersion !== previewRequestVersion) return
  focusElement(resourcePreviewCloseRef.value || resourcePreviewPanelRef.value)

  const detailId = extractResourceNumericId(normalized)
  const type = String(normalized.resourceType || normalized.type || '').toLowerCase()

  if (detailId) {
    try {
      const detailRes = await eduApi.resourceDetail(detailId)
      if (!pageActive || requestVersion !== previewRequestVersion || !previewResource.value) return
      const detailPayload = normalizeResourcePayload(detailRes?.data || normalized)
      previewResource.value = {
        ...previewResource.value,
        ...detailPayload
      }
    } catch {
      previewResource.value = { ...previewResource.value }
    }
  }

  if (type === 'animation' && previewResource.value?.id) {
    try {
      const html = await eduApi.animationHtml(previewResource.value.id)
      if (!pageActive || requestVersion !== previewRequestVersion || !previewResource.value) return
      const blob = new Blob([html], { type: 'text/html' })
      previewAnimationUrl.value = URL.createObjectURL(blob)
    } catch {
      previewAnimationUrl.value = ''
    }
  }
}

function resourceImage(resource) {
  return resolveResourceImage(resource)
}

function resourceVideo(resource) {
  return resolveResourceVideo(resource)
}

function resourceTypeOf(resource) {
  return String(resource?.resourceType || resource?.type || '').toLowerCase()
}

function isVideoResource(resource) {
  return resourceTypeOf(resource) === 'video'
}

function isAnimationResource(resource) {
  return resourceTypeOf(resource) === 'animation'
}

function resourceReviewStatus(resource) {
  const raw = String(resource?.reviewStatus || '').trim().toLowerCase()
  const aliases = {
    active: 'published',
    ready: 'published',
    published: 'published',
    reviewing: 'reviewing',
    pending: 'reviewing',
    submitted: 'reviewing',
    replaced: 'replaced',
    rejected: 'rejected'
  }
  if (aliases[raw]) return aliases[raw]

  // Historical task payloads may only carry the persisted numeric status.
  const persistedStatus = String(resource?.status || '').trim()
  return ({ '0': 'published', '1': 'reviewing', '2': 'replaced', '3': 'rejected' })[persistedStatus] || ''
}

function resourceReviewLabel(resource) {
  const labels = {
    published: '已发布',
    reviewing: '审核中'
  }
  return labels[resourceReviewStatus(resource)] || ''
}

function resourceGenerationFailed(resource) {
  const mediaStatus = String(resource?.mediaStatus || resource?.statusText || resource?.status || '').toLowerCase()
  return resource?.success === false
    || String(resource?.generationStatus || '').toLowerCase() === 'failed'
    || ['failed', 'error', 'cancelled'].includes(mediaStatus)
}

function visibleMessageResources(message) {
  return (Array.isArray(message?.resources) ? message.resources : [])
    // Quality review is metadata only. Keep every resource that was generated
    // successfully; hide only explicit generation or media failures.
    .filter(resource => !resourceGenerationFailed(resource))
}

function isResourceProcessing(resource) {
  return ['processing', 'pending', 'reviewing', 'submitted'].includes(String(resource?.mediaStatus || resource?.statusText || '').toLowerCase())
}

function readableResourceStatus(status) {
  const normalized = String(status || '').toLowerCase()
  if (normalized === 'completed') return '已完成'
  if (normalized === 'failed') return '暂不可用'
  if (normalized === 'processing') return '生成中'
  if (normalized === 'pending') return '排队中'
  if (normalized === 'submitted') return '已提交'
  return normalized || '等待生成'
}

function embeddedSummary(resource) {
  const raw = cleanAiDisplayText(resource?.description || resource?.content || '', {
    singleLine: true,
    maxLength: 72,
    keepCodeBlocks: false
  })
  if (raw) return raw

  const type = resourceTypeOf(resource)
  const fallback = {
    mindmap: '已生成结构化知识梳理，可点击查看导图详情。',
    code: '包含可运行代码与关键实现说明。',
    quiz: '已整理练习题与答案解析，可进入查看。',
    video: isResourceProcessing(resource) ? '视频正在生成中，稍后可播放。' : '视频资源已生成，可进入播放查看。',
    animation: isResourceProcessing(resource) ? '动画正在生成中，稍后可交互预览。' : '可播放教学动画已准备完成。',
    article: '已生成拓展阅读内容，可进入查看完整文本。',
    document: '已生成学习文档，可进入查看完整内容。',
    image: '已生成教学配图，可进入放大查看。'
  }

  return fallback[type] || '该资源可点击进入查看详情。'
}

function embeddedFooterLabel(resource) {
  if (resourceGenerationFailed(resource)) return '暂不可用'
  if (isResourceProcessing(resource)) return readableResourceStatus(resource?.mediaStatus || resource?.statusText)
  const reviewLabel = resourceReviewLabel(resource)
  if (reviewLabel) return reviewLabel
  if (resource?.duration) return resource.duration
  if (resource?.tag) return sanitizeDisplayText(resource.tag)
  if (isVideoResource(resource) && resourceVideo(resource)) return '可播放'
  if (isAnimationResource(resource)) return '可交互'
  return 'AI 生成'
}

function embeddedStateLabel(resource) {
  if (resourceGenerationFailed(resource)) return '暂不可用'
  if (isResourceProcessing(resource)) return readableResourceStatus(resource?.mediaStatus || resource?.statusText)
  const reviewLabel = resourceReviewLabel(resource)
  if (reviewLabel) return reviewLabel
  if (isVideoResource(resource)) return resourceVideo(resource) ? '视频资源' : '等待预览'
  if (isAnimationResource(resource)) return '动态演示'
  if (resourceTypeOf(resource) === 'quiz') return '练习资源'
  if (resourceTypeOf(resource) === 'code') return '示例代码'
  return '可查看'
}

function embeddedCodePreview(resource) {
  const content = String(resource?.content || resource?.description || '').trim()
  if (!content) {
    return 'def solve():\n    pass\n\n# 点击查看完整示例'
  }

  const cleaned = content
    .replace(/```[a-zA-Z]*/g, '')
    .replace(/```/g, '')
    .split('\n')
    .map(line => line.trimEnd())
    .filter(Boolean)
    .slice(0, 4)
    .join('\n')

  return cleaned || 'def solve():\n    pass\n\n# 点击查看完整示例'
}

function embeddedPlaceholderLineWidths(resource) {
  const map = {
    mindmap: ['88%', '64%', '76%'],
    code: ['92%', '78%', '56%'],
    quiz: ['72%', '90%', '62%'],
    video: ['86%', '58%', '74%'],
    animation: ['82%', '68%', '58%'],
    article: ['84%', '92%', '68%'],
    document: ['84%', '92%', '68%'],
    image: ['88%', '62%', '74%']
  }
  return map[resourceTypeOf(resource)] || ['88%', '76%', '60%']
}

function embeddedProgressWidth(resource) {
  return String(resource?.mediaStatus || '').toLowerCase() === 'processing' ? '82%' : '36%'
}

function videoStatusLabel(resource) {
  const status = String(resource?.mediaStatus || '').toLowerCase()
  if (status === 'completed' && resourceVideo(resource)) return '可播放'
  if (status === 'failed') return '暂不可用'
  if (status === 'processing' || status === 'pending') return '生成中'
  if (status === 'submitted') return '已提交'
  return resourceVideo(resource) ? '点击播放' : '等待生成'
}

function previewStatusText(resource) {
  if (!resource) return '资源预览不可用'
  if (isVideoResource(resource)) {
    return `视频正在生成，当前状态：${videoStatusLabel(resource)}`
  }
  if (isAnimationResource(resource)) {
    return isResourceProcessing(resource) ? `动画正在生成，当前状态：${readableResourceStatus(resource?.mediaStatus || resource?.statusText)}` : '该动画预览正在准备中，请稍后再次打开。'
  }
  return '该资源暂时没有可展示的预览内容。'
}

function upsertMessageResource(targetMessage, resource) {
  const resources = targetMessage.resources || (targetMessage.resources = [])
  const index = resources.findIndex(item => sameResource(item, resource)
    || (resource.revisionOf && String(item.persistedId || '') === String(resource.revisionOf)))

  if (index >= 0) {
    resources[index] = { ...resources[index], ...resource }
    if (resource.revisionOf) latestResourceKeys.delete(String(resource.revisionOf))
    return resources[index]
  }

  resources.push(resource)
  return resources[resources.length - 1]
}

function sameResource(left, right) {
  if (!left || !right) return false
  const leftPersistedId = String(left.persistedId || '').trim()
  const rightPersistedId = String(right.persistedId || '').trim()
  if (leftPersistedId && rightPersistedId && leftPersistedId === rightPersistedId) return true

  const leftVideoId = String(left.videoId || '').trim()
  const rightVideoId = String(right.videoId || '').trim()
  return Boolean(leftVideoId && rightVideoId && leftVideoId === rightVideoId)
}

function ensureVideoPolling(resource) {
  if (!pageActive || !isVideoResource(resource) || resourceVideo(resource) || !resource?.videoId) return
  const key = String(resource.persistedId || resource.videoId || '').trim()
  if (!key || videoPollers.has(key)) return

  const pollState = { attempts: 0, timer: null }
  videoPollers.set(key, pollState)

  const poll = async () => {
    pollState.attempts += 1
    try {
      const response = await eduApi.videoStatus(resource.videoId, {
        resourceId: extractResourceNumericId(resource),
        attempts: 1,
        intervalMs: 0
      })
      if (!pageActive || videoPollers.get(key) !== pollState) return

      const statusData = response?.data || {}
      if (statusData.status) resource.mediaStatus = statusData.status
      if (statusData.videoUrl) {
        resource.sourceUrl = statusData.videoUrl
        resource.coverUrl = resource.coverUrl || statusData.videoUrl
      }

      syncPreviewResource(resource)

      const done = Boolean(statusData.videoUrl)
        || ['completed', 'failed'].includes(String(statusData.status || '').toLowerCase())
        || pollState.attempts >= VIDEO_POLL_MAX_ATTEMPTS

      if (done) {
        stopVideoPolling(key)
        return
      }
    } catch {
      if (pollState.attempts >= 3) {
        stopVideoPolling(key)
        return
      }
    }

    if (!pageActive || videoPollers.get(key) !== pollState) return
    pollState.timer = window.setTimeout(poll, VIDEO_POLL_INTERVAL_MS)
  }

  poll()
}

function syncPreviewResource(resource) {
  if (!previewResource.value || !sameResource(previewResource.value, resource)) return
  previewResource.value = { ...previewResource.value, ...resource }
}

function stopVideoPolling(key) {
  const pollState = videoPollers.get(key)
  if (!pollState) return
  if (pollState.timer) window.clearTimeout(pollState.timer)
  videoPollers.delete(key)
}

function stopAllVideoPolling() {
  Array.from(videoPollers.keys()).forEach(stopVideoPolling)
}

function resetConversationRuntime() {
  stopAllVideoPolling()
  streaming.value = false
  historyError.value = ''
  currentPhase.value = ''
  eventLog.value = []
  selectedAgents.value = []
  agentProgressMap.value = {}
  latestResourceKeys = new Set()
  followOutput.value = true
}

function isNearBottom() {
  const container = chatScrollRef.value
  if (!container) return true
  const remaining = container.scrollHeight - container.clientHeight - container.scrollTop
  return remaining <= 72
}

function scrollToBottom(behavior = 'smooth') {
  const container = chatScrollRef.value
  if (!container) return
  const reducedMotion = typeof window !== 'undefined'
    && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (behavior === 'smooth' && !reducedMotion) {
    container.scrollTo({ top: container.scrollHeight, behavior: 'smooth' })
    return
  }

  container.scrollTop = container.scrollHeight
}

function scheduleScrollToBottom(behavior = 'smooth') {
  if (!pageActive) return
  if (!followOutput.value && !isNearBottom()) return
  if (scrollFrame) window.cancelAnimationFrame(scrollFrame)
  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = 0
    scrollToBottom(behavior)
  })
}

function handleChatScroll() {
  followOutput.value = isNearBottom()
}

function clampTraceWidth(value) {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return TRACE_MIN_WIDTH
  return Math.min(TRACE_MAX_WIDTH, Math.max(TRACE_MIN_WIDTH, numeric))
}

function loadTraceLayout() {
  try {
    const raw = localStorage.getItem(traceLayoutKey())
    if (!raw) return
    const parsed = JSON.parse(raw)
    traceWidth.value = clampTraceWidth(parsed?.width)
    traceCollapsed.value = Boolean(parsed?.collapsed)
  } catch {
    traceWidth.value = TRACE_MIN_WIDTH
    traceCollapsed.value = true
  }
}

function persistTraceLayout() {
  localStorage.setItem(traceLayoutKey(), JSON.stringify({
    width: traceWidth.value,
    collapsed: traceCollapsed.value
  }))
}

function traceLayoutKey() {
  const userId = String(workspace.state.currentUserId || '').trim() || 'anonymous'
  return `${TRACE_LAYOUT_KEY_PREFIX}:${userId}`
}

function expandTrace() {
  if (isTraceDrawerViewport.value) {
    traceRestoreFocusTarget = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : traceTriggerRef.value
  }
  traceCollapsed.value = false
  traceWidth.value = clampTraceWidth(traceWidth.value)
  persistTraceLayout()
  if (isTraceDrawerViewport.value) {
    nextTick(() => focusElement(tracePanelRef.value))
  }
}

function collapseTrace() {
  const restoreFocus = pageActive && isTraceDrawerViewport.value
  traceCollapsed.value = true
  persistTraceLayout()
  if (restoreFocus) {
    const restoreTarget = traceRestoreFocusTarget
    traceRestoreFocusTarget = null
    nextTick(() => focusElement(restoreTarget?.isConnected ? restoreTarget : traceTriggerRef.value))
  }
}

function focusElement(element) {
  if (!(element instanceof HTMLElement) || !element.isConnected) return
  try {
    element.focus({ preventScroll: true })
  } catch {
    element.focus()
  }
}

function focusableElements(container) {
  if (!(container instanceof HTMLElement)) return []
  const selector = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled]):not([type="hidden"])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    'iframe',
    '[tabindex]:not([tabindex="-1"])'
  ].join(',')
  return Array.from(container.querySelectorAll(selector))
    .filter(element => element instanceof HTMLElement && element.getClientRects().length > 0)
}

function trapResourcePreviewFocus(event) {
  const panel = resourcePreviewPanelRef.value
  const focusable = focusableElements(panel)
  if (!focusable.length) {
    event.preventDefault()
    focusElement(panel)
    return
  }

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const activeElement = document.activeElement
  if (event.shiftKey && (activeElement === first || !panel.contains(activeElement))) {
    event.preventDefault()
    focusElement(last)
  } else if (!event.shiftKey && (activeElement === last || !panel.contains(activeElement))) {
    event.preventDefault()
    focusElement(first)
  }
}

function handlePageKeydown(event) {
  if (!pageActive) return
  if (previewResource.value) {
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      closeResourcePreview()
    } else if (event.key === 'Tab') {
      trapResourcePreviewFocus(event)
    }
    return
  }

  if (event.key === 'Escape' && isTraceDrawerViewport.value && !traceCollapsed.value) {
    event.preventDefault()
    event.stopPropagation()
    collapseTrace()
  }
}

function handleTraceDrawerViewportChange(event) {
  isTraceDrawerViewport.value = event.matches
}

function attachPageListeners() {
  if (pageListenersAttached) return
  pageListenersAttached = true
  document.addEventListener('keydown', handlePageKeydown, true)
  traceDrawerMediaQuery ||= window.matchMedia('(max-width: 1280px)')
  isTraceDrawerViewport.value = traceDrawerMediaQuery.matches
  if (traceDrawerMediaQuery.addEventListener) {
    traceDrawerMediaQuery.addEventListener('change', handleTraceDrawerViewportChange)
  } else {
    traceDrawerMediaQuery.addListener(handleTraceDrawerViewportChange)
  }
}

function detachPageListeners() {
  if (!pageListenersAttached) return
  pageListenersAttached = false
  document.removeEventListener('keydown', handlePageKeydown, true)
  if (traceDrawerMediaQuery?.removeEventListener) {
    traceDrawerMediaQuery.removeEventListener('change', handleTraceDrawerViewportChange)
  } else {
    traceDrawerMediaQuery?.removeListener(handleTraceDrawerViewportChange)
  }
}

function pauseAttachmentUploads() {
  attachments.value.forEach(attachment => {
    if (attachment.status !== 'uploading') return
    attachment.controller?.abort()
    attachment.controller = null
    attachment.status = 'error'
    attachment.error = '页面已离开，附件上传已暂停，请重新上传。'
    attachment.errorAction = 'upload'
  })
}

function suspendSpeechInput() {
  speechRequestVersion += 1
  speechDisposed = true
  speechAbortController?.abort()
  speechAbortController = null
  speechRecording.value = false
  speechProcessing.value = false
  speechStatusText.value = ''
  speechChunks.value = []
  if (speechStopTimer) {
    window.clearTimeout(speechStopTimer)
    speechStopTimer = null
  }
  cleanupSpeechAudio()
}

function resumeVideoPolling() {
  messages.value.forEach(message => {
    message.resources?.forEach(resource => ensureVideoPolling(resource))
  })
}

function suspendVisiblePageEffects() {
  pageActive = false
  suspendedSessionId = activeSessionId.value
  if (loadingHistory.value) {
    latestConversationRequest += 1
    loadingHistory.value = false
    conversationLoadInterrupted = true
  }
  pauseAttachmentUploads()
  suspendSpeechInput()
  stopTraceDrag?.()
  stopAllVideoPolling()
  closeResourcePreview({ restoreFocus: false })
  if (scrollFrame) {
    window.cancelAnimationFrame(scrollFrame)
    scrollFrame = 0
  }
  detachPageListeners()
}

function startTraceResize(event) {
  if (window.innerWidth <= 1280) return
  traceCollapsed.value = false
  traceDragging.value = true

  const startX = event.clientX
  const startWidth = traceWidth.value

  const handlePointerMove = moveEvent => {
    traceWidth.value = clampTraceWidth(startWidth - (moveEvent.clientX - startX))
  }

  const handlePointerUp = () => {
    traceDragging.value = false
    persistTraceLayout()
    window.removeEventListener('pointermove', handlePointerMove)
    window.removeEventListener('pointerup', handlePointerUp)
    stopTraceDrag = null
  }

  stopTraceDrag = handlePointerUp
  window.addEventListener('pointermove', handlePointerMove)
  window.addEventListener('pointerup', handlePointerUp)
}

watch(activeSessionId, async value => {
  if (!hasBootstrappedConversation) return
  attachments.value.forEach(attachment => attachment.controller?.abort())
  attachments.value = []
  resetConversationRuntime()
  if (!pageActive) return
  await loadConversation(value)
})

watch(activeRuntimeTask, task => {
  if (!pageActive) return
  syncRuntimeTask(task)
}, { deep: true, immediate: true })

watch(() => route.query.panel, () => {
  syncRightPanelModeFromRoute()
}, { immediate: true })

onMounted(async () => {
  pageActive = true
  speechDisposed = false
  attachPageListeners()
  syncRightPanelModeFromRoute()
  await workspace.initialize()
  loadTraceLayout()
  await taskRuntime.initialize()
  hasBootstrappedConversation = true
  await loadConversation(activeSessionId.value)
  syncRuntimeTask(activeRuntimeTask.value)
})

onActivated(async () => {
  if (!hasBootstrappedConversation) return
  pageActive = true
  speechDisposed = false
  attachPageListeners()
  if (lifecycleSuspended) {
    lifecycleSuspended = false
    const sessionChanged = suspendedSessionId !== activeSessionId.value
    if (sessionChanged || conversationLoadInterrupted) {
      conversationLoadInterrupted = false
      await loadConversation(activeSessionId.value)
    }
    suspendedSessionId = null
    syncRuntimeTask(activeRuntimeTask.value)
    resumeVideoPolling()
  }
  await nextTick()
  if (isTraceDrawerViewport.value && !traceCollapsed.value) {
    focusElement(tracePanelRef.value)
  }
  scrollToBottom('auto')
})

onDeactivated(() => {
  lifecycleSuspended = true
  suspendVisiblePageEffects()
})

onBeforeUnmount(() => {
  suspendVisiblePageEffects()
})
</script>

<style scoped>
.chat-page {
  --trace-runtime-width: 320px;
  position: relative;
  height: calc(100vh - var(--topbar-height));
  display: flex;
  overflow: hidden;
}

.chat-page:not(.trace-resizing) .agent-trace,
.chat-page:not(.trace-resizing) .trace-resize-handle {
  transition: width 180ms cubic-bezier(0.25, 1, 0.5, 1), min-width 180ms cubic-bezier(0.25, 1, 0.5, 1), right 180ms cubic-bezier(0.25, 1, 0.5, 1);
}

.chat-stage {
  position: relative;
  flex: 1;
  min-width: 0;
  background: var(--surface-bright);
  display: flex;
  flex-direction: column;
}

.chat-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-lg) var(--margin-desktop);
  display: flex;
  flex-direction: column;
  gap: calc(var(--space-lg) + 8px);
}

.date-pill {
  align-self: center;
  border-radius: 9999px;
  background: var(--surface-container);
  color: var(--outline);
  padding: var(--space-xs) var(--space-sm);
  font-size: 12px;
  font-weight: 500;
}

.message-row {
  width: min(900px, 100%);
  display: flex;
  gap: var(--space-md);
  margin: 0 auto;
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.user + .message-row.assistant,
.message-row.assistant + .message-row.user {
  margin-top: 11px;
}

.agent-avatar {
  width: 40px;
  height: 40px;
  margin-top: var(--space-sm);
  border-radius: var(--radius-md);
  display: grid;
  place-items: center;
  background: var(--surface-container);
  color: var(--primary);
  border: 1px solid var(--outline-variant);
}

.message-stack {
  min-width: 0;
  max-width: 82%;
}

.message-meta {
  display: flex;
  gap: var(--space-xs);
  align-items: center;
  margin-bottom: var(--space-xs);
  font-size: 14px;
}

.message-meta strong {
  overflow-wrap: anywhere;
}

.message-meta span {
  border-radius: var(--radius-sm);
  background: var(--surface-container);
  color: var(--outline);
  padding: 1px 6px;
  font-size: 12px;
}

.message-bubble {
  border-radius: var(--radius-xl);
  padding: var(--space-md);
  box-shadow: var(--shadow-soft);
}

.assistant .message-bubble {
  border-top-left-radius: var(--radius-sm);
  border: 1px solid #ddd6fe;
  background: #f5f3ff;
}

.user .message-bubble {
  border-top-right-radius: var(--radius-sm);
  background: var(--primary);
  color: var(--on-primary);
}

.message-bubble p {
  margin: 0;
  white-space: pre-wrap;
  font-size: 18px;
  line-height: 28px;
}

.message-attachments {
  display: grid;
  gap: 6px;
  margin-top: var(--space-sm);
}

.message-attachment {
  max-width: 100%;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: var(--radius-sm);
  color: inherit;
  background: rgba(255, 255, 255, 0.1);
  font-size: 12px;
}

.message-row.assistant .message-attachment {
  border-color: var(--outline-variant);
  background: var(--surface-container-low);
}

.message-attachment > span:nth-child(2) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-attachment small {
  opacity: 0.8;
}

.embedded-resources {
  margin-top: var(--space-lg);
  padding-top: var(--space-md);
  border-top: 1px solid #ddd6fe;
}

.resource-heading {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-bottom: var(--space-sm);
  color: var(--on-surface-variant);
  font-weight: 600;
}

.embedded-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-sm);
}

.embedded-card {
  min-height: 272px;
  text-align: left;
  border-radius: var(--radius-lg);
  border: 1px solid var(--outline-variant);
  background: var(--surface-container-lowest);
  display: flex;
  flex-direction: column;
  gap: 0;
  cursor: pointer;
  overflow: hidden;
  transition: transform 180ms ease, box-shadow 180ms ease, border-color 180ms ease;
}

.embedded-card:hover {
  transform: translateY(-2px);
  border-color: rgba(0, 40, 142, 0.22);
  box-shadow: 0 10px 24px rgba(30, 64, 175, 0.1);
}

.embedded-card.processing {
  border-color: rgba(113, 42, 226, 0.24);
}

.embedded-card.review-reviewing {
  border-color: rgba(180, 83, 9, 0.24);
}

.embedded-card-media {
  position: relative;
  height: 164px;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(240, 244, 255, 0.9), rgba(230, 236, 252, 0.9));
}

.embedded-card .material-symbols-outlined {
  color: var(--secondary);
}

.embedded-card.image-card {
  min-height: 300px;
}

.embedded-card.animation-card .embedded-card-media,
.embedded-card.video-card .embedded-card-media {
  background:
    radial-gradient(circle at top right, rgba(113, 42, 226, 0.16), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(236, 240, 255, 0.88));
}

.embedded-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  background: var(--surface-container);
  transition: transform 500ms ease;
}

.embedded-card:hover .embedded-thumb {
  transform: scale(1.04);
}

.embedded-card-placeholder {
  width: 100%;
  height: 100%;
  padding: var(--space-sm);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  background:
    radial-gradient(circle at top right, rgba(113, 42, 226, 0.16), transparent 32%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(236, 240, 255, 0.88));
}

.embedded-placeholder-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-xs);
}

.embedded-placeholder-type,
.embedded-placeholder-state {
  min-height: 24px;
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  border-radius: 9999px;
  font-size: 11px;
  font-weight: 600;
}

.embedded-placeholder-type {
  background: rgba(0, 40, 142, 0.1);
  color: var(--primary);
}

.embedded-placeholder-state {
  background: rgba(50, 53, 55, 0.08);
  color: var(--on-surface-variant);
}

.embedded-placeholder-state.processing {
  background: rgba(113, 42, 226, 0.12);
  color: var(--secondary);
}

.embedded-placeholder-main {
  flex: 1;
  min-height: 0;
  border-radius: var(--radius-md);
  border: 1px solid rgba(197, 205, 228, 0.7);
  background: rgba(255, 255, 255, 0.72);
  overflow: hidden;
}

.embedded-text-preview,
.embedded-video-preview,
.embedded-animation-preview {
  height: 100%;
  padding: var(--space-sm);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
}

.embedded-text-preview strong,
.embedded-video-preview strong,
.embedded-animation-preview strong {
  font-size: 14px;
  line-height: 22px;
}

.embedded-text-preview p,
.embedded-video-preview small,
.embedded-animation-preview small {
  margin: 0;
  color: var(--on-surface-variant);
  line-height: 18px;
  font-size: 12px;
}

.embedded-code-preview {
  height: 100%;
  margin: 0;
  padding: var(--space-sm);
  white-space: pre-wrap;
  color: #1f2937;
  background:
    linear-gradient(180deg, rgba(17, 24, 39, 0.04), rgba(17, 24, 39, 0.08)),
    linear-gradient(90deg, rgba(99, 102, 241, 0.06), transparent 24%);
  font-family: "JetBrains Mono", monospace;
  font-size: 11px;
  line-height: 18px;
}

.embedded-quiz-preview {
  height: 100%;
  padding: var(--space-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  align-content: center;
  gap: 6px;
  flex-wrap: wrap;
}

.embedded-quiz-preview span {
  min-height: 30px;
  padding: 0 10px;
  border-radius: 9999px;
  display: inline-flex;
  align-items: center;
  background: rgba(113, 42, 226, 0.12);
  color: var(--secondary);
  font-size: 12px;
  font-weight: 600;
}

.embedded-video-preview,
.embedded-animation-preview {
  align-items: center;
  text-align: center;
}

.embedded-video-preview .material-symbols-outlined,
.embedded-animation-preview .material-symbols-outlined {
  font-size: 48px;
  color: var(--secondary);
}

.embedded-mindmap-preview {
  position: relative;
  height: 100%;
}

.embedded-mindmap-preview .node {
  position: absolute;
  width: 16px;
  height: 16px;
  border-radius: 9999px;
  background: rgba(0, 40, 142, 0.16);
  border: 2px solid rgba(0, 40, 142, 0.42);
}

.embedded-mindmap-preview .center {
  left: 50%;
  top: 50%;
  width: 22px;
  height: 22px;
  transform: translate(-50%, -50%);
  background: rgba(113, 42, 226, 0.2);
  border-color: rgba(113, 42, 226, 0.52);
}

.embedded-mindmap-preview .top {
  left: 50%;
  top: 24%;
  transform: translateX(-50%);
}

.embedded-mindmap-preview .left {
  left: 24%;
  top: 58%;
}

.embedded-mindmap-preview .right {
  right: 24%;
  top: 40%;
}

.embedded-mindmap-preview .line {
  position: absolute;
  background: linear-gradient(90deg, rgba(0, 40, 142, 0.24), rgba(113, 42, 226, 0.28));
  transform-origin: center;
}

.embedded-mindmap-preview .vertical {
  width: 2px;
  height: 34px;
  left: 50%;
  top: calc(24% + 16px);
  transform: translateX(-50%);
}

.embedded-mindmap-preview .left-branch {
  width: 60px;
  height: 2px;
  left: calc(24% + 16px);
  top: 60%;
  transform: rotate(-26deg);
}

.embedded-mindmap-preview .right-branch {
  width: 66px;
  height: 2px;
  right: calc(24% + 16px);
  top: 46%;
  transform: rotate(18deg);
}

.embedded-placeholder-lines {
  display: grid;
  gap: 5px;
}

.embedded-placeholder-lines span {
  display: block;
  height: 6px;
  border-radius: 9999px;
  background: linear-gradient(90deg, rgba(0, 40, 142, 0.12), rgba(113, 42, 226, 0.08));
}

.embedded-progress-line {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 4px;
  background: rgba(255, 255, 255, 0.6);
}

.embedded-progress-line span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--secondary), #8b5cf6);
}

.embedded-card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: var(--space-sm);
  gap: 6px;
}

.embedded-card-body strong {
  font-size: 15px;
  line-height: 22px;
}

.embedded-card-body p {
  flex: 1;
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--on-surface-variant);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.embedded-card-body small {
  color: var(--outline);
  font-size: 12px;
}

.composer-wrap {
  flex: 0 0 auto;
  padding: 0 var(--margin-desktop) var(--space-sm);
  background: var(--surface-bright);
}

.composer-wrap > * {
  width: min(900px, 100%);
  margin: 0 auto;
}

.composer {
  position: relative;
  border-radius: var(--radius-lg);
  border: 1px solid var(--outline-variant);
  background: var(--surface-container-lowest);
  box-shadow: var(--shadow-soft);
  overflow: hidden;
}

.attachment-input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
}

.composer-attachments {
  max-height: 164px;
  display: grid;
  gap: 6px;
  overflow-y: auto;
  padding: var(--space-sm) var(--space-sm) 0;
}

.composer-attachment {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: var(--space-xs);
  min-height: 46px;
  padding: 6px 8px;
  border: 1px solid var(--outline-variant);
  border-radius: var(--radius-md);
  background: var(--surface-container-low);
}

.composer-attachment.ready {
  border-color: rgba(5, 150, 105, 0.34);
}

.composer-attachment.error {
  border-color: rgba(186, 26, 26, 0.42);
  background: color-mix(in srgb, var(--error-container) 46%, var(--surface-container-lowest));
}

.composer-attachment.removing {
  opacity: 0.64;
}

.attachment-icon {
  color: var(--primary);
}

.composer-attachment.error .attachment-icon {
  color: var(--error);
}

.attachment-copy {
  min-width: 0;
  display: grid;
  gap: 1px;
}

.attachment-copy strong,
.attachment-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-copy strong {
  font-size: 13px;
  line-height: 18px;
}

.attachment-copy small {
  color: var(--on-surface-variant);
  font-size: 12px;
  line-height: 16px;
}

.composer-attachment.error .attachment-copy small {
  color: var(--error);
}

.attachment-action {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: var(--radius-sm);
  color: var(--on-surface-variant);
  background: transparent;
}

.attachment-action:hover:not(:disabled) {
  color: var(--primary);
  background: var(--surface-container);
}

.attachment-action:disabled {
  cursor: not-allowed;
  opacity: 0.52;
}

.composer:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px rgba(0, 40, 142, 0.1);
}

.composer textarea {
  width: 100%;
  height: 88px;
  border: 0;
  outline: none;
  resize: none;
  padding: var(--space-md);
  color: var(--on-surface);
  background: transparent;
  font-size: 18px;
  line-height: 28px;
}

.composer-actions {
  min-height: 52px;
  padding: var(--space-sm);
  border-top: 1px solid var(--surface-container);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.composer-actions > div {
  display: flex;
  align-items: center;
}

.composer-speech-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: var(--space-xs);
  color: var(--primary);
  font-size: 12px;
  line-height: 20px;
}

.composer-speech-status i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.composer-speech-status.recording {
  color: #c23737;
}

.composer-speech-status.recording i {
  animation: speech-dot-pulse 1s ease-in-out infinite;
}

.speech-button.recording {
  color: #fff;
  background: #c23737;
  box-shadow: 0 0 0 4px rgba(194, 55, 55, 0.15);
}

.speech-button.processing {
  color: var(--primary);
  background: rgba(0, 40, 142, 0.08);
}

.speech-button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

@keyframes speech-dot-pulse {
  0%,
  100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.45; transform: scale(1.2); }
}

.send-button {
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 9999px;
  display: grid;
  place-items: center;
  color: var(--on-primary);
  background: var(--primary);
  transition: transform 140ms cubic-bezier(0.25, 1, 0.5, 1), opacity 200ms cubic-bezier(0.25, 1, 0.5, 1), background-color 200ms cubic-bezier(0.25, 1, 0.5, 1);
}

.send-button:not(:disabled):active {
  transform: scale(0.96);
}

.stop-task-button {
  color: var(--on-error-container);
  background: var(--error-container);
}

.stop-task-button .rotating {
  animation: task-stop-rotate 900ms linear infinite;
}

@keyframes task-stop-rotate {
  to { transform: rotate(360deg); }
}

.send-button:disabled {
  cursor: not-allowed;
  color: var(--outline);
  background: var(--surface-variant);
}

@media (prefers-reduced-motion: reduce) {
  .send-button,
  .stop-task-button .rotating {
    animation: none;
    transition-duration: 0.01ms;
  }
}

.trace-resize-handle {
  position: absolute;
  top: 0;
  bottom: 0;
  right: calc(var(--trace-runtime-width) - 14px);
  width: 28px;
  z-index: 8;
  cursor: col-resize;
  touch-action: none;
}

.trace-resize-handle::before {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 1px;
  transform: translateX(-50%);
  background: rgba(117, 118, 132, 0.35);
}

.trace-resize-handle:hover::before,
.trace-resizing .trace-resize-handle::before {
  background: rgba(0, 40, 142, 0.5);
  width: 3px;
}

.agent-trace {
  width: var(--trace-runtime-width);
  min-width: var(--trace-runtime-width);
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--outline-variant);
  background: var(--surface-container-low);
  box-shadow: -4px 0 12px rgba(0, 0, 0, 0.02);
  overflow: hidden;
}

.agent-trace.collapsed {
  border-left-color: transparent;
  box-shadow: none;
}

.trace-header {
  padding: var(--space-md);
  border-bottom: 1px solid var(--outline-variant);
  background: var(--surface-container-lowest);
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
}

.trace-heading {
  min-width: 0;
}

.trace-heading h2 {
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: 20px;
  line-height: 28px;
}

.trace-heading h2 .material-symbols-outlined {
  color: var(--secondary);
}

.trace-heading small {
  display: block;
  margin-top: 4px;
  color: var(--on-surface-variant);
  line-height: 18px;
}

.trace-panel-tabs {
  padding: 0 var(--space-md) var(--space-sm);
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-xs);
  background: var(--surface-container-lowest);
  border-bottom: 1px solid var(--outline-variant);
}

.trace-tab {
  min-height: 36px;
  border: 1px solid var(--outline-variant);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--on-surface-variant);
  font-weight: 600;
}

.trace-tab.active {
  color: var(--primary);
  border-color: rgba(0, 40, 142, 0.2);
  background: rgba(0, 40, 142, 0.08);
}

.agent-trace-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.agent-trace-body.split {
  gap: var(--space-sm);
  padding-bottom: var(--space-sm);
}

.trace-section {
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.agent-trace-body.split .monitor-section {
  flex: 0 0 46%;
}

.agent-trace-body.split .trace-section-panel {
  flex: 1;
}

.trace-section-head {
  padding: var(--space-md);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  border-bottom: 1px solid var(--outline-variant);
  background: rgba(234, 221, 255, 0.2);
}

.trace-section-head div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.trace-section-head small {
  color: var(--on-surface-variant);
}

.monitor-groups {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-md);
  display: grid;
  gap: var(--space-md);
}

.monitor-group {
  display: grid;
  gap: var(--space-sm);
}

.monitor-group-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.monitor-group-head small {
  color: var(--on-surface-variant);
}

.monitor-list {
  display: grid;
  gap: var(--space-sm);
}

.monitor-item {
  border-radius: var(--radius-md);
  border: 1px solid var(--outline-variant);
  background: var(--surface-container-lowest);
  padding: var(--space-sm);
  display: grid;
  gap: 6px;
}

.monitor-item.active {
  border-color: rgba(0, 40, 142, 0.18);
}

.monitor-item.running {
  border-color: rgba(113, 42, 226, 0.28);
  box-shadow: 0 0 0 1px rgba(113, 42, 226, 0.16);
}

.monitor-item.success {
  border-color: rgba(38, 131, 77, 0.28);
  background: rgba(38, 131, 77, 0.05);
}

.monitor-item.danger {
  border-color: rgba(194, 55, 55, 0.28);
  background: rgba(194, 55, 55, 0.05);
}

.monitor-item.idle {
  opacity: 0.82;
}

.monitor-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-xs);
}

.monitor-item p,
.monitor-item small {
  margin: 0;
  color: var(--on-surface-variant);
  font-size: 12px;
  line-height: 18px;
}

.monitor-badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 600;
  background: var(--surface-container);
  color: var(--outline);
}

.monitor-badge.active,
.monitor-badge.running {
  background: rgba(113, 42, 226, 0.14);
  color: var(--secondary);
}

.monitor-badge.success {
  background: rgba(38, 131, 77, 0.14);
  color: var(--success);
}

.monitor-badge.danger {
  background: rgba(194, 55, 55, 0.14);
  color: #c23737;
}

.trace-status {
  padding: var(--space-md);
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--outline-variant);
  background: rgba(234, 221, 255, 0.35);
  margin-top: var(--space-md);
}

.trace-status div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.trace-status span:first-child {
  color: var(--on-surface-variant);
  font-size: 12px;
  font-weight: 600;
}

.trace-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-md);
}

.trace-item {
  position: relative;
  display: flex;
  gap: var(--space-md);
  padding-bottom: var(--space-lg);
}

.trace-item::before {
  content: "";
  position: absolute;
  left: 7px;
  top: 18px;
  bottom: 0;
  border-left: 2px solid var(--surface-variant);
}

.trace-dot {
  z-index: 1;
  width: 18px;
  height: 18px;
  border-radius: 9999px;
  border: 2px solid var(--outline-variant);
  background: var(--surface-container-lowest);
  display: grid;
  place-items: center;
}

.trace-item.done .trace-dot {
  border-color: var(--success);
  color: var(--success);
}

.trace-item.running .trace-dot {
  border-color: var(--secondary);
  background: var(--secondary);
  box-shadow: 0 0 0 4px rgba(113, 42, 226, 0.18);
  animation: pulse 1.8s infinite;
}

.trace-dot .material-symbols-outlined {
  font-size: 12px;
}

.dot-inner {
  width: 6px;
  height: 6px;
  border-radius: 9999px;
  background: white;
}

.trace-card {
  flex: 1;
  border-radius: var(--radius-md);
  border: 1px solid var(--outline-variant);
  background: var(--surface-container-lowest);
  padding: var(--space-sm);
}

.trace-item.running .trace-card {
  border-color: var(--secondary);
  box-shadow: 0 0 0 1px rgba(113, 42, 226, 0.18);
}

.trace-card div {
  display: flex;
  justify-content: space-between;
  gap: var(--space-xs);
}

.trace-card strong {
  font-size: 14px;
}

.trace-card small,
.trace-card p {
  color: var(--on-surface-variant);
  font-size: 12px;
  line-height: 18px;
}

.trace-card p {
  margin: 4px 0 0;
}

.edge-restore-right {
  position: absolute;
  top: 16px;
  right: 12px;
  z-index: 4;
}

.history-loading {
  width: min(900px, 100%);
  margin: 0 auto;
  display: grid;
  gap: var(--space-lg);
}

.chat-empty-guide {
  width: min(900px, 100%);
  min-height: 320px;
  margin: 0 auto;
  display: grid;
  place-items: center;
  align-content: center;
  gap: var(--space-sm);
}

.chat-empty-guide :deep(.ui-state) {
  width: min(620px, 100%);
}

.chat-quick-suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  max-width: 620px;
}

.chat-quick-suggestions button {
  min-height: 34px;
  border: 1px solid var(--outline-variant);
  border-radius: 17px;
  background: var(--surface-container-lowest);
  color: var(--primary);
  padding: 6px 12px;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
}

.chat-quick-suggestions button:hover {
  border-color: var(--primary);
  background: var(--primary-fixed);
}

.chat-quick-suggestions button:focus-visible {
  outline: 3px solid var(--secondary);
  outline-offset: 2px;
}


.resource-preview-backdrop {
  position: fixed;
  inset: 0;
  z-index: 30;
  padding: var(--space-xl);
  display: grid;
  place-items: center;
  background: rgba(20, 21, 31, 0.56);
}

.resource-preview-panel {
  width: min(960px, 92vw);
  max-height: 88vh;
  border-radius: var(--radius-lg);
  border: 1px solid var(--outline-variant);
  background: var(--surface-container-lowest);
  box-shadow: var(--shadow-soft);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.resource-preview-panel header {
  padding: var(--space-md);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-md);
  border-bottom: 1px solid var(--outline-variant);
}

.resource-preview-panel h3 {
  margin: var(--space-xs) 0 0;
  font-size: 18px;
  line-height: 26px;
}

.resource-preview-panel img {
  max-height: calc(88vh - 92px);
  width: 100%;
  object-fit: contain;
  background: var(--surface-container);
}

.resource-preview-body {
  display: flex;
  flex-direction: column;
  min-height: 240px;
  background: var(--surface-container);
}

.preview-animation-frame {
  width: 100%;
  height: calc(88vh - 92px);
  border: 0;
  background: white;
}

.preview-video {
  width: 100%;
  max-height: calc(88vh - 92px);
  background: #06070a;
}

.preview-text-block {
  margin: 0;
  padding: var(--space-lg);
  min-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--on-surface);
  background: var(--surface-container-lowest);
  font-family: "JetBrains Mono", "Microsoft YaHei", monospace;
  font-size: 14px;
  line-height: 24px;
}

.preview-status {
  min-height: 320px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: var(--space-md);
  padding: var(--space-xl);
  text-align: center;
  color: var(--on-surface-variant);
}

.preview-status .material-symbols-outlined {
  font-size: 64px;
  color: var(--primary);
}

@media (max-width: 1280px) {
  .trace-resize-handle {
    display: none;
  }

  .agent-trace {
    position: absolute;
    z-index: 30;
    inset-block: 0;
    inset-inline-end: 0;
    width: min(420px, calc(100vw - 24px));
    min-width: min(420px, calc(100vw - 24px));
    transform: translateX(0);
    box-shadow: -12px 0 28px rgba(0, 0, 0, 0.12);
  }

  .chat-page:not(.trace-resizing) .agent-trace {
    transition: transform 260ms cubic-bezier(0.25, 1, 0.5, 1);
  }

  .agent-trace.collapsed {
    width: min(420px, calc(100vw - 24px));
    min-width: min(420px, calc(100vw - 24px));
    transform: translateX(100%);
  }

  .edge-restore-right {
    display: grid;
  }
}

@media (max-width: 720px) {
  .chat-scroll,
  .composer-wrap {
    padding-left: var(--margin-mobile);
    padding-right: var(--margin-mobile);
  }

  .message-stack {
    max-width: 100%;
  }

  .embedded-grid {
    grid-template-columns: 1fr;
  }

  .composer-attachment {
    grid-template-columns: 24px minmax(0, 1fr) auto auto;
    gap: 6px;
  }

  .resource-preview-backdrop {
    padding: var(--space-md);
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-page:not(.trace-resizing) .agent-trace,
  .chat-page:not(.trace-resizing) .trace-resize-handle {
    transition-duration: 0.01ms;
  }
}
</style>
