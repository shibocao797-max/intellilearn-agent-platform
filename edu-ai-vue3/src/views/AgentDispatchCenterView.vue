<template>
  <section class="page agent-page">
    <header class="agent-header">
      <div>
        <span class="agent-kicker">AI AGENT WORKSPACE</span>
        <h1>Agent协作中心</h1>
        <p>观察{{ coordinatorNickname }}如何分派任务，以及专家 Agent 如何完成协作。</p>
      </div>

      <div class="header-status" role="status" aria-live="polite">
        <span class="status-dot" :class="coordinatorTone" aria-hidden="true"></span>
        <div>
          <strong>{{ statusTitle }}</strong>
          <small>{{ activeSessionLabel }}</small>
        </div>
      </div>
    </header>

    <form class="agent-command" aria-label="启动 Agent 协作" @submit.prevent="dispatchCommand">
      <span class="material-symbols-outlined command-icon" aria-hidden="true">terminal</span>
      <label class="sr-only" for="agent-command-input">本轮任务</label>
      <input
        id="agent-command-input"
        v-model.trim="command"
        type="text"
        :disabled="dispatching"
        placeholder="输入本轮任务，例如：生成一套二叉树学习资源"
      />
      <span class="command-context">{{ selectedAgentKeys.length ? `${selectedAgentKeys.length} 个 Agent 已调度` : '等待新的调度任务' }}</span>
      <button class="command-clear" type="button" :disabled="dispatching || !command" aria-label="清空任务" title="清空任务" @click="clearCommand">
        <span class="material-symbols-outlined" aria-hidden="true">close</span>
      </button>
      <button v-if="canCancel || isCancelling" class="command-cancel" type="button" :disabled="cancellationPending || isCancelling" @click="cancelCurrentTask">
        <span class="material-symbols-outlined" aria-hidden="true">stop_circle</span>
        <span>{{ cancellationPending || isCancelling ? '取消中' : '取消任务' }}</span>
      </button>
      <button v-else class="command-submit" type="submit" :disabled="starting || !command">
        <span class="material-symbols-outlined" aria-hidden="true">{{ starting ? 'progress_activity' : 'play_arrow' }}</span>
        <span>{{ starting ? '启动中' : '开始协作' }}</span>
      </button>
    </form>

    <div v-if="runtimeNotice" class="runtime-notice" :class="runtimeNotice.tone" :role="runtimeNotice.tone === 'error' ? 'alert' : 'status'">
      <span class="material-symbols-outlined" aria-hidden="true">{{ runtimeNotice.icon }}</span>
      <div>
        <strong>{{ runtimeNotice.title }}</strong>
        <p>{{ runtimeNotice.message }}</p>
      </div>
      <button v-if="runtimeNotice.retryable" type="button" title="立即重试连接" aria-label="立即重试连接" @click="retryNow">
        <span class="material-symbols-outlined" aria-hidden="true">sync</span>
      </button>
    </div>

    <main class="agent-layout">
      <section class="map-panel" aria-labelledby="map-title">
        <header class="panel-head map-head">
          <div>
            <span class="section-kicker">AGENT PIPELINE</span>
            <h2 id="map-title">Agent 总览</h2>
            <p>全部 {{ allAgents.length }} 个 Agent 按真实执行状态持续更新。</p>
          </div>
          <div class="map-legend" aria-label="Agent 状态图例">
            <span><i class="legend-dot idle"></i>待命</span>
            <span><i class="legend-dot selected"></i>已调度</span>
            <span><i class="legend-dot running"></i>执行中</span>
            <span><i class="legend-dot success"></i>完成</span>
          </div>
        </header>

        <div class="coordinator-node" :class="coordinatorTone">
          <div class="coordinator-icon" aria-hidden="true">
            <span class="material-symbols-outlined">account_tree</span>
          </div>
          <div class="coordinator-copy">
            <span class="node-kicker">ORCHESTRATOR</span>
            <h3>{{ coordinatorNickname }}</h3>
            <p>{{ coordinatorDescription }}</p>
            <small v-if="intentReason" class="intent-reason">调度依据：{{ intentReason }}</small>
          </div>
          <div class="coordinator-state">
            <strong>{{ phaseLabel }}</strong>
            <small>{{ dispatchStatus }}</small>
          </div>
        </div>

        <div class="agent-groups">
          <section v-for="group in agentGroups" :key="group.key" class="agent-group" :class="`group-${group.key}`" :aria-labelledby="`${group.key}-title`">
            <header class="group-head">
              <div class="group-mark" aria-hidden="true">
                <span class="material-symbols-outlined">{{ group.icon }}</span>
              </div>
              <div>
                <h3 :id="`${group.key}-title`">{{ group.title }}</h3>
              </div>
              <span class="group-count">{{ group.agents.length }}</span>
            </header>

            <div class="agent-grid">
              <button
                v-for="agent in group.agents"
                :key="agent.key"
                type="button"
                class="agent-node"
                :class="[agentState(agent).tone, { inspected: activeAgentKey === agent.key }]"
                :aria-pressed="activeAgentKey === agent.key"
                aria-controls="agent-inspector"
                :aria-label="`${agent.label}，${agentState(agent).label}。点击查看详情。`"
                @click="activeAgentKey = agent.key"
              >
                <div class="agent-node-top">
                  <span class="material-symbols-outlined agent-icon" aria-hidden="true">{{ agent.icon }}</span>
                  <span class="agent-signal" :class="agentState(agent).tone" aria-hidden="true"></span>
                  <em>{{ agentState(agent).label }}</em>
                </div>
                <strong>{{ agent.label }}</strong>
                <span class="agent-meta">{{ agentMeta(agent) }}</span>
              </button>
            </div>
          </section>
        </div>
      </section>

      <aside class="feed-panel" aria-labelledby="feed-title">
        <header class="panel-head feed-head">
          <div>
            <span class="section-kicker">LIVE TRACE</span>
            <h2 id="feed-title">运行轨迹</h2>
            <p>只展示服务端真实返回的 Agent 事件。</p>
          </div>
          <span class="event-count" aria-label="事件数量">{{ workflowEvents.length }}</span>
        </header>

        <section id="agent-inspector" class="agent-inspector" aria-live="polite">
          <template v-if="focusedAgent">
            <div class="inspector-head">
              <span class="material-symbols-outlined" aria-hidden="true">{{ focusedAgent.icon }}</span>
              <div>
                <span class="node-kicker">FOCUSED AGENT</span>
                <h3>{{ focusedAgent.label }}</h3>
              </div>
              <span class="inspector-state" :class="agentState(focusedAgent).tone">{{ agentState(focusedAgent).label }}</span>
            </div>
            <p>{{ focusedAgent.description }}</p>
            <small>{{ agentMeta(focusedAgent) }}</small>
          </template>
          <template v-else>
            <span class="material-symbols-outlined inspector-empty-icon" aria-hidden="true">touch_app</span>
            <div>
              <h3>选择一个 Agent</h3>
              <p>查看它的职责、状态和最近协作记录。</p>
            </div>
          </template>
        </section>

        <section class="feed-section" aria-labelledby="events-title">
          <header class="feed-section-head">
            <h3 id="events-title">最近事件</h3>
            <span>{{ workflowEvents.length ? '最近 8 条' : '暂无事件' }}</span>
          </header>

          <ol v-if="workflowEvents.length" class="event-list">
            <li v-for="event in workflowEvents.slice(-8).reverse()" :key="event.id" :class="event.tone">
              <span class="event-marker" aria-hidden="true">
                <span class="material-symbols-outlined">{{ eventIcon(event) }}</span>
              </span>
              <div>
                <strong>{{ event.title }}</strong>
                <p>{{ event.summary }}</p>
              </div>
            </li>
          </ol>
          <div v-else class="feed-empty">
            <span class="material-symbols-outlined" aria-hidden="true">timeline</span>
            <p>启动一次协作后，这里会显示 Agent 的调度、交接、审校和交付事件。</p>
          </div>
        </section>

        <div class="agent-metrics" aria-label="Agent 统计">
          <div><strong>{{ allAgents.length }}</strong><span>可用 Agent</span></div>
          <div><strong>{{ selectedAgentKeys.length }}</strong><span>初始调度</span></div>
          <div><strong>{{ completedAgentCount }}</strong><span>已完成</span></div>
        </div>
      </aside>
    </main>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { DEFAULT_COORDINATOR_NICKNAME, useWorkspaceStore } from '@/stores/workspace'
import { useTaskRuntimeStore } from '@/stores/taskRuntime'
import { sanitizeDisplayText } from '@/utils/format'

const workspace = useWorkspaceStore()
const taskRuntime = useTaskRuntimeStore()
const command = ref('')
const activeAgentKey = ref('')
const starting = ref(false)
const cancellationPending = ref(false)
const actionError = ref('')
let failedTaskSubmission = null

const agentGroups = [
  {
    key: 'context',
    title: '上下文与回答',
    description: '理解问题，读取学习上下文并形成直接回答。',
    icon: 'hub',
    agents: [
      { key: 'textReply', label: '答疑讲解 Agent', description: '解释问题与关键概念。', icon: 'question_answer' },
      { key: 'profileUpdate', label: '学习画像 Agent', description: '更新当前学习画像。', icon: 'person_search', fixed: true },
      { key: 'learningPath', label: '学习路径 Agent', description: '整理阶段化学习路径。', icon: 'route', fixed: true }
    ]
  },
  {
    key: 'creation',
    title: '内容生成专家',
    description: '按意图生成不同类型的学习内容和媒体资源。',
    icon: 'auto_awesome',
    agents: [
      { key: 'docGen', label: '文档生成 Agent', description: '生成系统化学习文档。', icon: 'description' },
      { key: 'quizGen', label: '题库生成 Agent', description: '生成练习与测验题目。', icon: 'quiz' },
      { key: 'mindmapGen', label: '思维导图 Agent', description: '组织知识结构和关联。', icon: 'account_tree' },
      { key: 'codeGen', label: '代码案例 Agent', description: '生成可阅读的代码案例。', icon: 'code' },
      { key: 'readingExpand', label: '阅读拓展 Agent', description: '补充延伸阅读资料。', icon: 'menu_book' },
      { key: 'imageGen', label: '图像生成 Agent', description: '生成适合讲解的配图。', icon: 'image' },
      { key: 'videoGen', label: '视频生成 Agent', description: '生成教学视频资源。', icon: 'movie' },
      { key: 'animationGen', label: '动画生成 Agent', description: '生成可播放的教学动画。', icon: 'animation' }
    ]
  },
  {
    key: 'delivery',
    title: '质量与交付',
    description: '审校结果，处理修订并整理最终资源顺序。',
    icon: 'fact_check',
    agents: [
      { key: 'qualityReview', label: '质量审校 Agent', description: '审校内容质量与学习适配度。', icon: 'verified', fixed: true },
      { key: 'resourcePlanner', label: '资源编排 Agent', description: '组织资源顺序和推荐理由。', icon: 'format_list_numbered', fixed: true }
    ]
  }
]

const phaseMeta = {
  START: '正在建立连接',
  PROFILE_LOADED: '正在读取学习上下文',
  PROFILE_UPDATED: '正在准备 Agent 协作',
  INTENT_ANALYZED: '正在分析任务意图',
  DISPATCHING: '正在分派 Agent 任务',
  AGENTS_DONE: '生成 Agent 已完成',
  QUALITY_REVIEWING: '正在进行质量审校',
  QUALITY_CORRECTING: '正在修订资源',
  QUALITY_REVIEWED: '质量审校已完成',
  LEARNING_PATH_PLANNING: '正在整理学习路径',
  LEARNING_PATH_READY: '学习路径已就绪',
  LEARNING_PATH_SKIPPED: '已跳过学习路径',
  RESOURCE_PLANNING: '正在编排学习资源',
  RESOURCE_PLAN_READY: '资源编排已就绪',
  RESOURCE_PLAN_SKIPPED: '已跳过资源编排',
  DONE: '本轮协作已完成',
  FAILED: '本轮协作失败'
}

const allAgents = computed(() => agentGroups.flatMap(group => group.agents))
const coordinatorNickname = computed(() => workspace.state.coordinatorNickname || DEFAULT_COORDINATOR_NICKNAME)
const currentTask = computed(() => taskRuntime.taskForSession(workspace.state.activeSessionId))
const currentPhase = computed(() => currentTask.value?.currentPhase || '')
const selectedAgentKeys = computed(() => currentTask.value?.selectedAgents || [])
const progressByAgent = computed(() => currentTask.value?.progressByAgent || {})
const workflowEvents = computed(() => (currentTask.value?.events || []).map(describeWorkflowEvent).filter(Boolean))
const profileUpdated = computed(() => currentPhase.value === 'PROFILE_UPDATED' || (currentTask.value?.events || []).some(event => event.eventName === 'phase' && phaseValue(event.payload) === 'PROFILE_UPDATED'))
const intentReason = computed(() => {
  if (currentTask.value?.intentReason) return sanitizeDisplayText(currentTask.value.intentReason)
  const event = [...(currentTask.value?.events || [])].reverse().find(item => item.eventName === 'intent')
  return textValue(event?.payload?.recommendReason || event?.payload?.reason)
})
const focusedAgent = computed(() => activeAgentKey.value ? allAgents.value.find(agent => agent.key === activeAgentKey.value) || null : null)
const runStatus = computed(() => runtimeStatus(currentTask.value))
const isCancelling = computed(() => String(currentTask.value?.status || '').toUpperCase() === 'CANCELLING')
const canCancel = computed(() => isActiveTask(currentTask.value) && !isCancelling.value)
const dispatching = computed(() => starting.value || cancellationPending.value || canCancel.value)
const completedAgentCount = computed(() => allAgents.value.filter(agent => agentState(agent).tone === 'success').length)
const phaseLabel = computed(() => phaseMeta[currentPhase.value] || ({
  reconnecting: '正在恢复任务连接',
  cancelling: '正在取消本轮协作',
  error: '本轮协作失败',
  cancelled: '本轮协作已取消',
  success: '本轮协作已完成',
  running: '等待 Agent 回执'
}[runStatus.value] || '等待新的调度任务'))
const dispatchStatus = computed(() => ({
  reconnecting: '重连中',
  cancelling: '取消中',
  error: '需处理',
  cancelled: '已取消',
  success: '已完成',
  running: '执行中'
}[runStatus.value] || '待命'))
const coordinatorTone = computed(() => ({ reconnecting: 'reconnecting', cancelling: 'cancelling', error: 'error', cancelled: 'cancelled', success: 'success', running: 'running' }[runStatus.value] || 'idle'))
const statusTitle = computed(() => ({
  reconnecting: '任务连接正在恢复',
  cancelling: '正在取消本轮协作',
  error: '本轮协作需要处理',
  cancelled: '本轮协作已取消',
  success: '本轮协作已完成',
  running: 'Agent 正在协作'
}[runStatus.value] || '当前会话暂无任务'))
const activeSessionLabel = computed(() => {
  if (currentTask.value) return `任务 ${currentTask.value.taskId.slice(0, 8)} · 会话 ${currentTask.value.sessionId.slice(0, 8)}`
  return workspace.state.activeSessionId ? `当前会话 ${workspace.state.activeSessionId.slice(0, 8)}` : '提交任务后将自动创建会话'
})
const coordinatorDescription = computed(() => {
  if (runStatus.value === 'reconnecting') return '连接中断不会改变任务状态，恢复后将继续同步真实事件。'
  if (runStatus.value === 'cancelling') return '取消请求已受理，正在等待当前 Agent 到达可停止位置。'
  if (runStatus.value === 'error') return textValue(currentTask.value?.error || actionError.value, '服务端未完成本轮任务。')
  if (runStatus.value === 'cancelled') return '服务端已确认取消，本页保留取消前收到的运行轨迹。'
  if (runStatus.value === 'running') return phaseMeta[currentPhase.value] || '正在等待服务端返回协作状态。'
  if (runStatus.value === 'success') return '本轮 Agent 已完成协作，完整事件保留在运行轨迹中。'
  return `等待任务后，${coordinatorNickname.value}会根据意图选择合适的专家。`
})
const runtimeNotice = computed(() => {
  if (actionError.value) return { tone: 'error', icon: 'error', title: '操作未完成', message: textValue(actionError.value), retryable: false }
  if (!currentTask.value) return { tone: 'idle', icon: 'hourglass_empty', title: '当前会话暂无任务', message: '新任务启动后，服务端事件会在这里持续同步。', retryable: false }
  if (runStatus.value === 'reconnecting') return { tone: 'reconnecting', icon: 'sync_problem', title: '连接中断，任务状态保持不变', message: textValue(taskRuntime.state.lastConnectionError, '正在自动恢复连接。'), retryable: true }
  if (runStatus.value === 'cancelling') return { tone: 'cancelling', icon: 'pending_actions', title: '正在取消任务', message: '取消请求已受理，请等待服务端确认。', retryable: false }
  if (runStatus.value === 'error') return { tone: 'error', icon: 'error', title: '任务执行失败', message: textValue(currentTask.value.error, '服务端返回了失败状态。'), retryable: false }
  if (runStatus.value === 'cancelled') return { tone: 'cancelled', icon: 'cancel', title: '任务已取消', message: '已停止高频轮询，当前轨迹仍可查看。', retryable: false }
  return null
})

function agentState(agent) {
  const progress = progressByAgent.value[agent.key]
  const selected = selectedAgentKeys.value.includes(agent.key)
  const status = String(progress?.status || '').toLowerCase()

  if (status.includes('fail') || status === 'error') return { label: '需处理', tone: 'error' }
  if (['completed', 'revised', 'corrected', 'done', 'success'].includes(status)) return { label: '已完成', tone: 'success' }
  if (['running', 'generating', 'processing', 'revising', 'in_progress'].includes(status)) {
    return runStatus.value === 'cancelled' ? { label: '已中止', tone: 'cancelled' } : { label: '执行中', tone: 'running' }
  }

  if (agent.key === 'profileUpdate') {
    if (currentPhase.value === 'PROFILE_LOADED') return { label: '正在更新', tone: 'running' }
    if (profileUpdated.value) return { label: '已更新', tone: 'success' }
  }

  if (selected) return { label: '已调度', tone: 'selected' }
  if (agent.fixed) return { label: '流程接力', tone: 'fixed' }
  return { label: '待命', tone: 'idle' }
}

function agentMeta(agent) {
  const progress = progressByAgent.value[agent.key]
  if (progress?.durationMs != null) return `耗时 ${formatDuration(progress.durationMs)}`
  if (agentState(agent).tone === 'fixed') return '自动后置流程'
  if (selectedAgentKeys.value.includes(agent.key)) return '本轮已加入调度'
  return '等待调用'
}

function groupSummary(group) {
  const active = group.agents.filter(agent => ['selected', 'running', 'success', 'error'].includes(agentState(agent).tone)).length
  return active ? `${active} 个 Agent 已有状态更新` : `${group.agents.length} 个 Agent 待命`
}

function formatDuration(value) {
  const duration = Number(value)
  if (!Number.isFinite(duration)) return '未知'
  if (duration < 1000) return `${duration}ms`
  return `${(duration / 1000).toFixed(1)}s`
}

function agentDisplayName(key, fallback = '') {
  const aliases = {
    dispatcher: coordinatorNickname.value,
    coordinator: coordinatorNickname.value,
    orchestrator: coordinatorNickname.value,
    selectedAgents: '专家 Agent 集群',
    finalRecommendation: '最终建议'
  }
  return sanitizeDisplayText(allAgents.value.find(agent => agent.key === key)?.label || aliases[key] || fallback || key || '未命名 Agent')
}

function textValue(value, fallback = '') {
  const text = String(value ?? '').trim()
  return sanitizeDisplayText(text || fallback)
}

function describeWorkflowEvent(event) {
  const eventName = event.eventName
  const payload = event.payload
  let title = ''
  let summary = ''
  let tone = 'running'

  if (eventName === 'phase') {
    const phase = phaseValue(payload)
    if (!phase || phase === 'START') return null
    title = phaseMeta[phase] || phase
    summary = '服务端已进入这一阶段。'
  } else if (eventName === 'intent' && payload && typeof payload === 'object') {
    title = `${coordinatorNickname.value}完成意图分析`
    summary = textValue(payload.recommendReason, textValue(payload.intent, '已完成任务意图分析。'))
  } else if (eventName === 'selectedAgents') {
    const agents = Array.isArray(payload)
      ? payload
      : Array.isArray(payload?.selectedAgents)
        ? payload.selectedAgents
        : Array.isArray(payload?.agents)
          ? payload.agents
          : []
    title = `${coordinatorNickname.value}完成分派`
    summary = `本轮初始选择了 ${agents.length} 个专家 Agent。`
  } else if (eventName === 'agentProgress' && payload && typeof payload === 'object') {
    const status = String(payload.status || '').toLowerCase()
    const name = agentDisplayName(payload.agentName || payload.agent || payload.name)
    title = `${name}${['completed', 'done', 'success', 'revised', 'corrected'].includes(status) ? '已完成' : status.includes('fail') || status === 'error' ? '执行失败' : '状态更新'}`
    summary = textValue(payload.message || payload.summary || payload.stage, `服务端状态：${textValue(payload.status, '已更新')}`)
    if (['completed', 'done', 'success', 'revised', 'corrected'].includes(status)) tone = 'success'
    if (status.includes('fail') || status === 'error') tone = 'error'
  } else if (eventName === 'agentMessage' && payload && typeof payload === 'object') {
    title = `${agentDisplayName(payload.fromAgent, payload.fromDisplayName)} -> ${agentDisplayName(payload.toAgent, payload.toDisplayName)}`
    summary = textValue(payload.summary, textValue(payload.type, '完成一次 Agent 交接。'))
  } else if (eventName === 'review' && payload && typeof payload === 'object') {
    const passed = payload.passed === true || String(payload.passed).toLowerCase() === 'true'
    title = passed ? '质量审校通过' : '质量审校需要处理'
    const score = payload.score == null ? '' : `评分 ${payload.score}`
    summary = [score, textValue(payload.decision, passed ? '资源可以进入后续 Agent 流程。' : '资源需要修订或跳过。')].filter(Boolean).join('，')
    tone = passed ? 'success' : 'error'
  } else if (eventName === 'revisionStart') {
    title = '开始修订资源'
    summary = textValue(payload?.reason || payload?.revisionPrompt, '质量审校已发出修订要求。')
  } else if (eventName === 'revisionDone') {
    title = '资源修订完成'
    summary = textValue(payload?.summary || payload?.status, '修订结果已回传质量审校。')
    tone = 'success'
  } else if (eventName === 'resource' || eventName === 'revisionResource') {
    title = eventName === 'revisionResource' ? '修订资源已产出' : '学习资源已产出'
    summary = textValue(payload?.title || payload?.displayName || payload?.resourceType, '资源已提交质量审校。')
  } else if (eventName === 'learningPath') {
    title = '学习路径已整理'
    summary = payload?.stageCount == null ? '后续学习顺序已整理完成。' : `已整理 ${payload.stageCount} 个学习阶段。`
    tone = 'success'
  } else if (eventName === 'resourcePlan') {
    title = '学习资源已编排'
    summary = payload?.orderedCount == null ? '已形成学习资源的推荐顺序。' : `已形成 ${payload.orderedCount} 项资源的推荐顺序。`
    tone = 'success'
  } else if (eventName === 'finalRecommendations') {
    title = '最终学习建议已形成'
    summary = textValue(payload?.nextBestAction, '下一步学习行动已准备好。')
    tone = 'success'
  } else if (eventName === 'done') {
    title = '交付回执已完成'
    summary = '服务端已发送本轮完成事件。'
    tone = 'success'
  } else if (eventName === 'error') {
    title = '本轮协作失败'
    summary = textValue(payload?.message || payload, '服务端返回了未命名错误。')
    tone = 'error'
  }

  if (!title) return null
  return {
    ...event,
    title,
    summary,
    tone
  }
}

function eventIcon(event) {
  if (event.tone === 'error') return 'error'
  if (event.tone === 'success') return 'check_circle'
  return 'arrow_forward'
}

async function dispatchCommand() {
  const text = command.value.trim()
  if (!text || dispatching.value) return

  starting.value = true
  actionError.value = ''
  let signature = ''
  let clientRequestId = ''
  try {
    if (!workspace.state.activeSessionId) await workspace.createSessionInActiveProject()
    const sessionId = workspace.state.activeSessionId
    if (!sessionId) throw new Error('无法创建学习会话，请刷新后重试。')
    signature = JSON.stringify([sessionId, text])
    clientRequestId = failedTaskSubmission?.signature === signature
      ? failedTaskSubmission.clientRequestId
      : createClientRequestId()
    await taskRuntime.startTask({ message: text, sessionId, clientRequestId })
    failedTaskSubmission = null
    command.value = ''
  } catch (error) {
    if (signature && clientRequestId) failedTaskSubmission = { signature, clientRequestId }
    actionError.value = error?.message || '无法创建调度任务。'
  } finally {
    starting.value = false
  }
}

async function cancelCurrentTask() {
  if (!currentTask.value || cancellationPending.value || isCancelling.value) return
  cancellationPending.value = true
  actionError.value = ''
  try {
    await taskRuntime.cancelTask(currentTask.value.taskId)
  } catch (error) {
    actionError.value = error?.message || '取消任务失败，请稍后重试。'
  } finally {
    cancellationPending.value = false
  }
}

async function retryNow() {
  actionError.value = ''
  if (currentTask.value?.taskId) await taskRuntime.pollTask(currentTask.value.taskId)
  else await taskRuntime.refreshActiveTasks(workspace.state.activeSessionId)
}

function clearCommand() {
  command.value = ''
  actionError.value = ''
}

function runtimeStatus(task) {
  if ((task?.connectionStatus === 'reconnecting' && isActiveTask(task)) || (!task && taskRuntime.state.connectionStatus === 'reconnecting')) return 'reconnecting'
  const status = String(task?.status || '').toUpperCase()
  if (status === 'CANCELLING') return 'cancelling'
  if (['FAILED', 'ERROR'].includes(status)) return 'error'
  if (['CANCELLED', 'CANCELED'].includes(status)) return 'cancelled'
  if (['SUCCEEDED', 'COMPLETED', 'SUCCESS', 'DONE'].includes(status)) return 'success'
  if (isActiveTask(task)) return 'running'
  return 'idle'
}

function createClientRequestId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `dispatch-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function isActiveTask(task) {
  return ['CREATED', 'ACCEPTED', 'PENDING', 'QUEUED', 'RUNNING', 'PROCESSING', 'IN_PROGRESS', 'CANCELLING']
    .includes(String(task?.status || '').toUpperCase())
}

function phaseValue(payload) {
  return textValue(payload?.phase || payload?.currentPhase || payload)
}
</script>

<style scoped>
.agent-page {
  max-width: 1480px;
  display: grid;
  gap: 16px;
  margin: 0 auto;
}

.agent-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}

.agent-kicker,
.section-kicker,
.node-kicker {
  display: block;
  color: var(--primary);
  font-size: 10px;
  font-weight: 800;
  line-height: 15px;
}

.agent-header h1 {
  margin: 3px 0 0;
  color: var(--on-surface);
  font-size: 32px;
  line-height: 40px;
}

.agent-header p {
  margin: 6px 0 0;
  color: var(--on-surface-variant);
  font-size: 14px;
  line-height: 21px;
}

.header-status {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 190px;
  padding: 10px 0 9px 14px;
  border-left: 1px solid var(--outline-variant);
}

.status-dot,
.agent-signal {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--outline-variant);
}

.status-dot.running,
.agent-signal.running { background: var(--primary); }
.status-dot.success,
.agent-signal.success { background: var(--success); }
.status-dot.error,
.agent-signal.error { background: var(--error); }
.status-dot.reconnecting,
.agent-signal.reconnecting,
.status-dot.cancelling,
.agent-signal.cancelling { background: var(--warning); }
.status-dot.cancelled,
.agent-signal.cancelled { background: var(--outline); }
.agent-signal.selected { background: var(--secondary); }
.agent-signal.fixed { background: var(--warning); }

.header-status strong,
.header-status small { display: block; }
.header-status strong { color: var(--on-surface); font-size: 13px; line-height: 18px; }
.header-status small { margin-top: 2px; color: var(--outline); font-size: 11px; line-height: 16px; }

.agent-command {
  min-width: 0;
  display: grid;
  grid-template-columns: auto minmax(160px, 1fr) auto auto auto;
  align-items: center;
  gap: 10px;
  min-height: 58px;
  padding: 8px 10px;
  border: 1px solid var(--outline-variant);
  border-radius: 8px;
  background: var(--surface-container-lowest);
  box-shadow: var(--shadow-soft);
}

.command-icon { color: var(--primary); font-size: 20px; }
.agent-command input {
  width: 100%;
  min-width: 0;
  min-height: 40px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--on-surface);
  font: inherit;
  font-size: 14px;
}

.agent-command input::placeholder { color: var(--outline); }
.agent-command input:focus-visible { box-shadow: 0 2px 0 var(--primary); }
.command-context { color: var(--outline); font-size: 11px; line-height: 16px; white-space: nowrap; }
.command-clear,
.command-submit,
.command-cancel {
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border-radius: 6px;
  padding: 0 10px;
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.command-clear { width: 40px; border: 1px solid var(--outline-variant); background: transparent; color: var(--outline); }
.command-clear:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); }
.command-submit { border: 1px solid var(--primary); background: var(--primary); color: var(--on-primary); }
.command-submit:hover:not(:disabled) { background: #0035b9; }
.command-cancel { border: 1px solid rgba(186, 26, 26, .35); background: var(--error-container); color: var(--error); }
.command-cancel:hover:not(:disabled) { border-color: var(--error); }
.command-clear:disabled,
.command-submit:disabled,
.command-cancel:disabled { cursor: not-allowed; opacity: .5; }
.command-clear .material-symbols-outlined,
.command-submit .material-symbols-outlined,
.command-cancel .material-symbols-outlined { font-size: 18px; }

.runtime-notice {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 11px 12px;
  border: 1px solid var(--outline-variant);
  background: var(--surface-container-low);
  color: var(--on-surface-variant);
}
.runtime-notice > .material-symbols-outlined { flex: 0 0 auto; margin-top: 1px; color: var(--primary); font-size: 19px; }
.runtime-notice > div { min-width: 0; flex: 1; }
.runtime-notice strong,
.runtime-notice p { display: block; margin: 0; }
.runtime-notice strong { color: var(--on-surface); font-size: 12px; line-height: 17px; }
.runtime-notice p { margin-top: 2px; color: var(--on-surface-variant); font-size: 11px; line-height: 16px; }
.runtime-notice button { width: 32px; height: 32px; display: grid; place-items: center; border: 1px solid var(--outline-variant); background: transparent; color: var(--primary); cursor: pointer; }
.runtime-notice button:hover { border-color: var(--primary); }
.runtime-notice button:focus-visible { outline: 3px solid var(--secondary); outline-offset: 2px; }
.runtime-notice.error { border-color: rgba(186, 26, 26, .3); background: var(--error-container); }
.runtime-notice.error > .material-symbols-outlined { color: var(--error); }
.runtime-notice.reconnecting,
.runtime-notice.cancelling { border-color: rgba(138, 89, 0, .3); }
.runtime-notice.reconnecting > .material-symbols-outlined,
.runtime-notice.cancelling > .material-symbols-outlined { color: var(--warning); }
.runtime-notice.cancelled { border-color: var(--outline-variant); }

.agent-layout {
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}

.map-panel,
.feed-panel {
  min-width: 0;
  border: 1px solid var(--outline-variant);
  border-radius: 8px;
  background: var(--surface-container-lowest);
  box-shadow: var(--shadow-soft);
}

.map-panel { padding: 22px; }
.feed-panel { position: sticky; top: 20px; padding: 18px; }
.panel-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.panel-head h2 { margin: 3px 0 0; color: var(--on-surface); font-size: 21px; line-height: 27px; }
.panel-head p { margin: 5px 0 0; color: var(--outline); font-size: 11px; line-height: 17px; }

.map-legend {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px 12px;
  color: var(--outline);
  font-size: 10px;
  line-height: 16px;
}

.map-legend span { display: inline-flex; align-items: center; gap: 4px; }
.legend-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--outline-variant); }
.legend-dot.selected { background: var(--secondary); }
.legend-dot.running { background: var(--primary); }
.legend-dot.success { background: var(--success); }

.coordinator-node {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
  padding: 14px;
  border: 1px solid rgba(0, 40, 142, .2);
  border-left: 4px solid var(--primary);
  background: var(--surface-container-low);
}

.coordinator-node.success { border-left-color: var(--success); }
.coordinator-node.error { border-left-color: var(--error); }
.coordinator-node.reconnecting,
.coordinator-node.cancelling { border-left-color: var(--warning); }
.coordinator-node.cancelled { border-left-color: var(--outline); }
.coordinator-icon { width: 40px; height: 40px; display: grid; place-items: center; background: var(--primary); color: var(--on-primary); }
.coordinator-node.success .coordinator-icon { background: var(--success); }
.coordinator-node.error .coordinator-icon { background: var(--error); }
.coordinator-node.reconnecting .coordinator-icon,
.coordinator-node.cancelling .coordinator-icon { background: var(--warning); }
.coordinator-node.cancelled .coordinator-icon { background: var(--outline); }
.coordinator-icon .material-symbols-outlined { font-size: 22px; }
.coordinator-copy { min-width: 0; }
.coordinator-copy h3 { margin: 1px 0 0; overflow-wrap: anywhere; color: var(--on-surface); font-size: 16px; line-height: 22px; }
.coordinator-copy p { margin: 3px 0 0; overflow: hidden; color: var(--on-surface-variant); font-size: 12px; line-height: 18px; text-overflow: ellipsis; white-space: nowrap; }
.coordinator-copy .intent-reason { display: block; margin-top: 4px; overflow: hidden; color: var(--primary); font-size: 10px; line-height: 15px; text-overflow: ellipsis; white-space: nowrap; }
.coordinator-state { min-width: 104px; padding-left: 12px; border-left: 1px solid var(--outline-variant); text-align: right; }
.coordinator-state strong,
.coordinator-state small { display: block; }
.coordinator-state strong { color: var(--on-surface); font-size: 11px; line-height: 16px; }
.coordinator-state small { margin-top: 3px; color: var(--outline); font-size: 10px; line-height: 15px; }
.coordinator-node.running .coordinator-state strong { color: var(--primary); }
.coordinator-node.success .coordinator-state strong { color: var(--success); }
.coordinator-node.error .coordinator-state strong { color: var(--error); }
.coordinator-node.reconnecting .coordinator-state strong,
.coordinator-node.cancelling .coordinator-state strong { color: var(--warning); }
.coordinator-node.cancelled .coordinator-state strong { color: var(--outline); }

.agent-groups { display: grid; gap: 20px; }
.agent-group { padding-top: 16px; border-top: 1px solid var(--outline-variant); }
.group-head { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 9px; margin-bottom: 10px; }
.group-mark { width: 30px; height: 30px; display: grid; place-items: center; border: 1px solid var(--outline-variant); color: var(--primary); }
.group-mark .material-symbols-outlined { font-size: 17px; }
.group-head h3 { margin: 0; color: var(--on-surface); font-size: 13px; line-height: 18px; }
.group-head p { margin: 2px 0 0; color: var(--outline); font-size: 10px; line-height: 15px; }
.group-count { min-width: 24px; color: var(--outline); font-size: 12px; font-weight: 800; text-align: right; }
.agent-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.group-creation .agent-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }

.agent-node {
  min-width: 0;
  min-height: 92px;
  display: grid;
  align-content: start;
  gap: 3px;
  padding: 10px;
  border: 1px solid var(--outline-variant);
  border-radius: 6px;
  background: transparent;
  color: var(--on-surface);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: transform 240ms ease, opacity 240ms ease;
}

.agent-node:hover { border-color: var(--primary); background: var(--primary-fixed); transform: translateY(-1px); }
.agent-node.inspected { border-color: var(--primary); box-shadow: inset 3px 0 var(--primary); }
.agent-node.selected { border-color: rgba(0, 95, 158, .5); background: rgba(0, 95, 158, .04); }
.agent-node.running { border-color: rgba(0, 40, 142, .55); background: rgba(0, 40, 142, .06); }
.agent-node.success { border-color: rgba(47, 133, 90, .4); }
.agent-node.error { border-color: rgba(186, 26, 26, .45); background: var(--error-container); }
.agent-node.cancelled { border-color: var(--outline-variant); background: var(--surface-container-low); }
.agent-node.fixed { border-style: dashed; }
.agent-node-top { min-width: 0; display: flex; align-items: center; gap: 6px; }
.agent-icon { color: var(--outline); font-size: 17px; }
.agent-node.running .agent-icon { color: var(--primary); }
.agent-node.success .agent-icon { color: var(--success); }
.agent-node.error .agent-icon { color: var(--error); }
.agent-node.cancelled .agent-icon { color: var(--outline); }
.agent-node-top em { margin-left: auto; color: var(--outline); font-size: 9px; font-style: normal; font-weight: 800; line-height: 14px; }
.agent-node.selected .agent-node-top em { color: var(--secondary); }
.agent-node.running .agent-node-top em { color: var(--primary); }
.agent-node.success .agent-node-top em { color: var(--success); }
.agent-node.error .agent-node-top em { color: var(--error); }
.agent-node.fixed .agent-node-top em { color: var(--warning); }
.agent-node > strong { overflow: hidden; color: var(--on-surface); font-size: 12px; line-height: 17px; text-overflow: ellipsis; white-space: nowrap; }
.agent-node > small { overflow: hidden; color: var(--on-surface-variant); font-size: 10px; line-height: 15px; text-overflow: ellipsis; white-space: nowrap; }
.agent-meta { color: var(--outline); font-size: 9px; line-height: 14px; }

.event-count { min-width: 28px; padding: 4px 7px; border-radius: 999px; background: var(--primary-fixed); color: var(--primary); font-size: 11px; font-weight: 800; text-align: center; }
.agent-inspector { margin-top: 18px; padding: 14px 0; border-top: 1px solid var(--outline-variant); border-bottom: 1px solid var(--outline-variant); }
.inspector-head { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 9px; }
.inspector-head > .material-symbols-outlined { color: var(--primary); font-size: 20px; }
.inspector-head h3 { margin: 1px 0 0; color: var(--on-surface); font-size: 14px; line-height: 19px; }
.inspector-state { color: var(--outline); font-size: 10px; font-weight: 800; }
.inspector-state.selected { color: var(--secondary); }
.inspector-state.running { color: var(--primary); }
.inspector-state.success { color: var(--success); }
.inspector-state.error { color: var(--error); }
.inspector-state.reconnecting { color: var(--warning); }
.inspector-state.cancelled { color: var(--outline); }
.agent-inspector > p { margin: 8px 0 0; color: var(--on-surface-variant); font-size: 12px; line-height: 18px; }
.agent-inspector > small { display: block; margin-top: 4px; color: var(--outline); font-size: 10px; line-height: 15px; }
.inspector-empty-icon { float: left; margin: 2px 9px 20px 0; color: var(--primary); font-size: 20px; }
.agent-inspector h3 { margin: 0; color: var(--on-surface); font-size: 13px; line-height: 18px; }
.agent-inspector div p { margin: 3px 0 0; color: var(--on-surface-variant); font-size: 11px; line-height: 17px; }

.feed-section { padding-top: 16px; }
.feed-section-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.feed-section-head h3 { margin: 0; color: var(--on-surface); font-size: 13px; line-height: 18px; }
.feed-section-head span { color: var(--outline); font-size: 10px; line-height: 15px; }
.event-list { display: grid; gap: 11px; max-height: 410px; margin: 12px 0 0; padding: 0; overflow-y: auto; list-style: none; }
.event-list li { display: grid; grid-template-columns: 24px minmax(0, 1fr); gap: 8px; align-items: start; }
.event-marker { width: 24px; height: 24px; display: grid; place-items: center; border: 1px solid var(--outline-variant); border-radius: 50%; color: var(--primary); }
.event-marker .material-symbols-outlined { font-size: 14px; }
.event-list li.success .event-marker { border-color: rgba(47, 133, 90, .35); color: var(--success); }
.event-list li.error .event-marker { border-color: rgba(186, 26, 26, .35); color: var(--error); }
.event-list strong,
.event-list p { display: block; margin: 0; }
.event-list strong { color: var(--on-surface); font-size: 11px; line-height: 16px; }
.event-list p { margin-top: 1px; color: var(--on-surface-variant); font-size: 10px; line-height: 15px; }
.feed-empty { display: flex; align-items: flex-start; gap: 8px; margin-top: 12px; padding: 11px; background: var(--surface-container-low); color: var(--on-surface-variant); }
.feed-empty .material-symbols-outlined { flex: 0 0 auto; color: var(--primary); font-size: 18px; }
.feed-empty p { margin: 0; font-size: 11px; line-height: 17px; }

.agent-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1px; margin-top: 18px; border: 1px solid var(--outline-variant); background: var(--outline-variant); }
.agent-metrics div { padding: 9px 7px; background: var(--surface-container-low); text-align: center; }
.agent-metrics strong,
.agent-metrics span { display: block; }
.agent-metrics strong { color: var(--on-surface); font-size: 18px; line-height: 22px; }
.agent-metrics span { margin-top: 2px; color: var(--outline); font-size: 10px; line-height: 15px; }

.sr-only { position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.agent-node:focus-visible,
.command-clear:focus-visible,
.command-submit:focus-visible,
.agent-command input:focus-visible { outline: 3px solid var(--secondary); outline-offset: 2px; }

@media (max-width: 1180px) {
  .agent-layout { grid-template-columns: 1fr; }
  .feed-panel { position: static; }
}

@media (max-width: 760px) {
  .agent-header { align-items: flex-start; flex-direction: column; gap: 12px; }
  .header-status { width: 100%; min-width: 0; padding-left: 0; border-left: 0; }
  .agent-command { grid-template-columns: auto minmax(0, 1fr) auto; }
  .command-context { grid-column: 2 / -1; grid-row: 2; }
  .command-submit { grid-column: 1 / -1; }
  .map-panel,
  .feed-panel { padding: 16px; }
  .panel-head { align-items: flex-start; flex-direction: column; }
  .map-legend { justify-content: flex-start; }
  .group-creation .agent-grid,
  .agent-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 460px) {
  .agent-header h1 { font-size: 28px; line-height: 35px; }
  .agent-command { grid-template-columns: auto minmax(0, 1fr) auto; }
  .group-creation .agent-grid,
  .agent-grid { grid-template-columns: 1fr; }
  .coordinator-node { grid-template-columns: auto minmax(0, 1fr); }
  .coordinator-state { grid-column: 1 / -1; min-width: 0; padding: 8px 0 0; border-top: 1px solid var(--outline-variant); border-left: 0; text-align: left; }
  .coordinator-state small { display: inline; margin-left: 6px; }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: .01ms !important;
    animation-iteration-count: 1 !important;
    scroll-behavior: auto !important;
    transition-duration: .01ms !important;
  }
}
</style>
