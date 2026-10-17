<template>
  <section class="page profile-page">
    <div class="page-title-row">
      <div class="profile-heading">
        <h2>学习画像</h2>
        <p>汇总已记录的画像维度、确认状态与当前版本证据。</p>
      </div>
      <div class="profile-meta panel">
        <div>
          <span>当前版本</span>
          <strong>{{ profile.version ? `版本 ${profile.version}` : '尚未建立' }}</strong>
        </div>
        <div class="meta-divider"></div>
        <div>
          <span>最近有效更新</span>
          <strong>{{ lastUpdatedLabel }}</strong>
        </div>
      </div>
    </div>

    <div v-if="loading" class="profile-grid">
      <SkeletonBlock height="320px" />
      <SkeletonBlock height="320px" />
    </div>

    <UiState
      v-else-if="loadError"
      icon="error"
      title="学习画像加载失败"
      :description="loadError"
      action-label="重新加载"
      action-icon="refresh"
      @action="loadProfile"
    />

    <UiState
      v-else-if="!hasProfile"
      icon="person_search"
      title="尚未形成学习画像"
      description="完成一次学习对话或提交主题练习后，系统会记录可追溯的画像证据。"
      action-label="前往 AI 对话"
      action-icon="chat"
      @action="$router.push('/chat')"
    />

    <template v-else>
      <section class="panel profile-overview" aria-labelledby="profile-overview-title">
        <div class="overview-main">
          <span class="eyebrow">当前版本</span>
          <div class="overview-title-row">
            <h3 id="profile-overview-title">画像总览</h3>
            <span class="status-pill" :class="statusTone(overallStatus)">{{ statusLabel(overallStatus) }}</span>
          </div>
          <p class="overview-summary">{{ summary.text || '暂无分析说明。' }}</p>
          <dl class="overview-stats" aria-label="画像关键数据">
            <div>
              <dt>有效维度</dt>
              <dd>{{ overview.dimensionCount ?? profileDimensions.length }}</dd>
            </div>
            <div>
              <dt>当前证据</dt>
              <dd>{{ overview.evidenceCount ?? evidence.length }}</dd>
            </div>
            <div>
              <dt>待确认</dt>
              <dd>{{ overview.pendingConfirmationCount ?? inferenceStatusCounts.pending }}</dd>
            </div>
            <div>
              <dt>练习验证</dt>
              <dd>{{ overview.verifiedDimensionCount ?? capabilityMetrics.length }}</dd>
            </div>
          </dl>
        </div>

        <aside class="evidence-summary" aria-label="当前版本证据摘要">
          <span class="eyebrow">证据摘要</span>
          <strong>{{ evidenceSummaryTitle }}</strong>
          <p>{{ evidenceSummaryText }}</p>
          <dl class="evidence-breakdown">
            <div><dt>学习对话</dt><dd>{{ evidenceSourceCounts.conversation }}</dd></div>
            <div><dt>主题练习</dt><dd>{{ evidenceSourceCounts.practice }}</dd></div>
            <div v-if="evidenceSourceCounts.other"><dt>其他记录</dt><dd>{{ evidenceSourceCounts.other }}</dd></div>
          </dl>
          <button class="ai-button overview-action" type="button" :disabled="planLoading" @click="generateOrRegenerateActionPlan">
            {{ planLoading ? '正在生成计划' : hasActionPlan ? '重新生成行动计划' : '生成行动计划' }}
          </button>
        </aside>
      </section>

      <section class="dimensions-panel" aria-labelledby="profile-dimensions-title">
        <header>
          <div>
            <span class="eyebrow">当前画像</span>
            <h3 id="profile-dimensions-title">非空画像维度</h3>
          </div>
          <small>偏好与推断不作为掌握度评分；待确认项可逐项处理。</small>
        </header>
        <p v-if="confirmationError" class="confirmation-feedback error" role="alert" aria-live="assertive" aria-atomic="true">{{ confirmationError }}</p>
        <p v-else-if="confirmationMessage" class="confirmation-feedback success" role="status" aria-live="polite" aria-atomic="true">{{ confirmationMessage }}</p>
        <div v-if="profileDimensions.length" class="dimension-grid">
          <article v-for="item in profileDimensions" :key="`${item.key}-${item.profileVersion ?? profile.version ?? 'current'}`" class="dimension-block" :class="inferenceStatusTone(item.status)">
            <div class="dimension-head">
              <div>
                <span>{{ item.category }}</span>
                <h4>{{ item.label }}</h4>
              </div>
              <span class="status-pill" :class="inferenceStatusTone(item.status)">{{ inferenceStatusLabel(item.status) }}</span>
            </div>
            <p class="dimension-value">{{ formatDimensionValue(item) }}</p>
            <p v-if="item.evidence" class="dimension-evidence">
              <span class="material-symbols-outlined" aria-hidden="true">format_quote</span>
              <span>{{ formatDisplayValue(item.evidence) }}</span>
            </p>
            <div class="dimension-footer">
              <span>{{ item.evidenceCount ? `${item.evidenceCount} 条当前版本证据` : '暂无当前版本证据' }}</span>
              <span v-if="item.kind === 'mastery' && item.sampleCount">{{ item.sampleCount }} 道练习样本</span>
            </div>
            <div v-if="isConfirmableInference(item)" class="confirmation-actions" :aria-busy="confirmationBusyKey === item.key">
              <button
                class="primary-button confirmation-button"
                type="button"
                :disabled="Boolean(confirmationBusyKey)"
                :aria-label="`确认${item.label}符合实际情况`"
                @click="confirmInference(item, 'CONFIRMED')"
              >
                {{ confirmationBusyKey === item.key ? '提交中' : '确认符合' }}
              </button>
              <button
                class="ghost-button confirmation-button"
                type="button"
                :disabled="Boolean(confirmationBusyKey)"
                :aria-label="`标记${item.label}不符合实际情况`"
                @click="confirmInference(item, 'REJECTED')"
              >
                标记不符
              </button>
            </div>
          </article>
        </div>
        <p v-else class="section-empty">当前版本没有可展示的非空画像维度。</p>
      </section>

      <section class="panel capability-panel" aria-labelledby="mastery-title">
        <header>
          <div>
            <span class="eyebrow">客观练习结果</span>
            <h3 id="mastery-title"><span class="material-symbols-outlined" aria-hidden="true">query_stats</span>真实掌握度</h3>
          </div>
          <span class="status-pill" :class="statusTone(masteryStatus)">{{ statusLabel(masteryStatus) }}</span>
        </header>
        <div v-if="capabilityMetrics.length" class="metric-list">
          <article v-for="metric in capabilityMetrics" :key="metric.key" class="metric-row">
            <div class="metric-head"><strong>{{ metric.topic ? `${metric.label} · ${metric.topic}` : metric.label }}</strong><span>{{ metric.score.toFixed(0) }}%</span></div>
            <div class="bar-track" role="progressbar" :aria-label="metric.topic ? `${metric.topic}掌握度` : metric.label" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="Math.round(metric.score)"><i :style="{ width: `${metric.score}%` }"></i></div>
            <small>基于 {{ metric.sampleCount }} 道已提交的同主题客观练习</small>
          </article>
        </div>
        <div v-else class="mastery-empty">
          <span class="material-symbols-outlined" aria-hidden="true">rule</span>
          <div>
            <strong>暂无真实掌握度数据</strong>
            <p>本区只展示已提交主题练习形成的客观结果，不使用对话推断、模拟分数或装饰性雷达图。</p>
          </div>
          <button class="ghost-button" type="button" @click="$router.push('/quiz')">前往主题练习</button>
        </div>
      </section>

      <section class="evidence-panel">
        <header>
          <div>
            <span class="eyebrow">可追溯依据</span>
            <h3>画像证据记录</h3>
          </div>
          <small>每条记录都对应一次真实学习活动。</small>
        </header>
        <div v-if="evidence.length" class="evidence-list">
          <article v-for="item in evidence" :key="item.id ?? `${item.dimensionKey}-${item.profileVersion ?? 'unknown'}-${item.createdAt}`">
            <div class="evidence-icon"><span class="material-symbols-outlined">receipt_long</span></div>
            <div class="evidence-content">
              <div class="evidence-head"><h4>{{ item.dimensionLabel }}</h4><span class="status-pill" :class="statusTone(item.status)">{{ statusLabel(item.status) }}</span></div>
              <p>{{ item.excerpt || '未保存可展示的证据摘要。' }}</p>
              <small>{{ sourceLabel(item.sourceType) }} · {{ formatDate(item.createdAt) }}</small>
              <dl class="evidence-meta" aria-label="证据记录元数据">
                <div><dt>ID</dt><dd>{{ item.id ?? '未返回' }}</dd></div>
                <div><dt>画像版本</dt><dd>{{ item.profileVersion ?? '未返回' }}</dd></div>
                <div><dt>状态</dt><dd :title="item.status || '未返回'">{{ statusLabel(item.status) }}</dd></div>
              </dl>
            </div>
          </article>
        </div>
        <div v-else class="truthful-empty evidence-empty"><span class="material-symbols-outlined">fact_check</span><p>暂未收集到可追溯证据，不会展示虚构的“证据日志”。</p></div>
      </section>

      <section v-if="hasActionPlan || planError" ref="planPanelRef" class="panel plan-panel">
        <header>
          <div><span class="eyebrow">行动计划</span><h3>{{ actionPlan?.title || '计划生成失败' }}</h3></div>
          <div v-if="hasActionPlan" class="plan-controls">
            <span class="status-pill" :class="planStatusTone(actionPlan.status)">{{ planStatusLabel(actionPlan.status) }}</span>
            <small v-if="actionPlans.length" class="plan-history-count">共 {{ actionPlans.length }} 份计划</small>
            <button v-if="actionPlan.status === 'DRAFT'" class="primary-button plan-control-button" type="button" :disabled="planLoading" @click="confirmActionPlan">确认计划</button>
            <button v-if="actionPlan.status !== 'ARCHIVED'" class="ghost-button plan-control-button" type="button" :disabled="planLoading" @click="archiveActionPlan">归档</button>
            <button class="ghost-button plan-control-button" type="button" :disabled="planLoading" @click="regenerateActionPlan">重新生成</button>
          </div>
        </header>
        <p v-if="planError" class="error-copy">{{ planError }}</p>
        <template v-else>
          <p>{{ actionPlan.summary }}</p>
          <ol>
            <li v-for="item in planItems" :key="item.id">
              <strong>{{ item.title }}</strong>
              <span>{{ item.description }}</span>
              <div class="plan-step-actions">
                <small :class="{ complete: item.status === 'COMPLETED' }">{{ planItemStatusLabel(item.status) }}</small>
                <button v-if="item.action && actionPlan.status === 'ACTIVE' && item.status !== 'COMPLETED'" type="button" class="ghost-button plan-step-button" :disabled="planItemBusyId === item.id" @click="executePlanItem(item)">
                  <span class="material-symbols-outlined">{{ planItemBusyId === item.id ? 'progress_activity' : item.action.icon }}</span>
                  {{ planItemBusyId === item.id ? '处理中' : item.action.label }}
                </button>
                <button v-if="actionPlan.status === 'ACTIVE' && item.status === 'IN_PROGRESS'" type="button" class="ghost-button plan-step-button" :disabled="planItemBusyId === item.id" @click="updatePlanItemStatus(item, 'COMPLETED')">
                  标记完成
                </button>
              </div>
            </li>
          </ol>
        </template>
      </section>
    </template>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { eduApi } from '@/services/api'
import { dataOf, formatDate } from '@/utils/format'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import UiState from '@/components/UiState.vue'

const loading = ref(true)
const loadError = ref('')
const profile = ref({})
const planLoading = ref(false)
const actionPlan = ref(null)
const actionPlans = ref([])
const planError = ref('')
const planPanelRef = ref(null)
const planItemBusyId = ref(null)
const confirmationBusyKey = ref('')
const confirmationMessage = ref('')
const confirmationError = ref('')
const router = useRouter()

const DIMENSION_META = {
  learningGoal: { label: '学习目标', category: '目标与偏好', kind: 'preference', order: 10 },
  preferredResourceType: { label: '资源偏好', category: '目标与偏好', kind: 'preference', order: 20 },
  cognitiveStyle: { label: '认知风格', category: '学习方式', kind: 'preference', order: 30 },
  knowledgeDepthPreference: { label: '知识深度偏好', category: '学习方式', kind: 'preference', order: 40 },
  learningStrategyMode: { label: '学习策略', category: '学习方式', kind: 'preference', order: 50 },
  selfDrivenLearning: { label: '自主学习习惯', category: '学习方式', kind: 'preference', order: 60 },
  knowledgeLevel: { label: '知识基础', category: '画像推断', kind: 'inference', order: 70 },
  weakPoints: { label: '待巩固知识点', category: '画像推断', kind: 'inference', order: 80 },
  strengths: { label: '优势知识点', category: '画像推断', kind: 'inference', order: 90 },
  knowledgeMastery: { label: '知识掌握度', category: '客观指标', kind: 'mastery', order: 100 }
}

const view = computed(() => isRecord(profile.value.profileView) ? profile.value.profileView : {})
const capabilityMetrics = computed(() => (Array.isArray(view.value.capabilityMetrics) ? view.value.capabilityMetrics : [])
  .filter(item => {
    const score = Number(item?.score)
    const sampleCount = Math.floor(Number(item?.sampleCount))
    return Number.isFinite(score) && score >= 0 && score <= 100 && Number.isFinite(sampleCount) && sampleCount >= 1
  })
  .map(item => ({ ...item, score: Number(item.score), sampleCount: Math.floor(Number(item.sampleCount)) })))
const preferences = computed(() => Array.isArray(view.value.preferences) ? view.value.preferences : [])
const inferences = computed(() => (Array.isArray(view.value.inferences) ? view.value.inferences : []).filter(isCurrentInference))
const evidence = computed(() => Array.isArray(view.value.evidence) ? view.value.evidence : [])
const summary = computed(() => isRecord(view.value.summary) ? view.value.summary : {})
const overview = computed(() => isRecord(profile.value.overview)
  ? profile.value.overview
  : isRecord(view.value.overview) ? view.value.overview : {})
const dimensionEvidenceCounts = computed(() => evidence.value.reduce((counts, item) => {
  const key = String(item?.dimensionKey || '').trim()
  if (key) counts[key] = (counts[key] || 0) + 1
  return counts
}, {}))
const profileDimensions = computed(() => {
  const rawDimensions = isRecord(profile.value.dimensions) ? profile.value.dimensions : {}
  const presented = new Map()
  preferences.value.forEach(item => presented.set(item.key, { ...item, kind: 'preference' }))
  inferences.value.forEach(item => presented.set(item.key, { ...item, kind: 'inference' }))
  capabilityMetrics.value.forEach(item => presented.set(item.key, { ...item, kind: 'mastery' }))

  return [...new Set([...Object.keys(rawDimensions), ...presented.keys()])]
    .map(key => {
      const raw = dimensionPayload(rawDimensions[key])
      const item = presented.get(key) || {}
      const meta = DIMENSION_META[key] || {}
      const value = item.value !== undefined ? item.value : raw.value
      if (!hasMeaningfulValue(value)) return null
      const kind = item.kind || meta.kind || 'preference'
      return {
        ...raw,
        ...item,
        key,
        kind,
        value,
        label: item.label || meta.label || key,
        category: meta.category || '画像维度',
        order: meta.order ?? 999,
        evidence: item.evidence || raw.evidence || '',
        evidenceCount: dimensionEvidenceCounts.value[key] || 0,
        profileVersion: item.profileVersion ?? (kind === 'mastery' ? profile.value.version : null),
        status: item.status || raw.confirmationStatus || (kind === 'mastery' ? 'VERIFIED' : 'RECORDED')
      }
    })
    .filter(Boolean)
    .sort((left, right) => left.order - right.order || left.label.localeCompare(right.label, 'zh-CN'))
})
const inferenceStatusCounts = computed(() => inferences.value.reduce((counts, item) => {
  const status = normalizeStatus(item.status) || 'PENDING_CONFIRMATION'
  if (status === 'CONFIRMED') counts.confirmed += 1
  else if (status === 'REJECTED') counts.rejected += 1
  else counts.pending += 1
  return counts
}, { pending: 0, confirmed: 0, rejected: 0 }))
const evidenceSourceCounts = computed(() => evidence.value.reduce((counts, item) => {
  if (item?.sourceType === 'CONVERSATION') counts.conversation += 1
  else if (item?.sourceType === 'PRACTICE_SESSION') counts.practice += 1
  else counts.other += 1
  return counts
}, { conversation: 0, practice: 0, other: 0 }))
const evidenceSummaryTitle = computed(() => evidence.value.length ? `${evidence.value.length} 条当前版本证据` : '当前版本暂无证据')
const evidenceSummaryText = computed(() => {
  if (!evidence.value.length) return '系统不会用历史记录或模拟内容填充当前版本的证据摘要。'
  const parts = []
  if (evidenceSourceCounts.value.conversation) parts.push(`${evidenceSourceCounts.value.conversation} 条来自学习对话`)
  if (evidenceSourceCounts.value.practice) parts.push(`${evidenceSourceCounts.value.practice} 条来自主题练习`)
  if (evidenceSourceCounts.value.other) parts.push(`${evidenceSourceCounts.value.other} 条来自其他学习记录`)
  return `${parts.join('，')}。下方可查看每条记录的来源和画像版本。`
})
const masteryStatus = computed(() => capabilityMetrics.value.length ? 'VERIFIED' : 'INSUFFICIENT_EVIDENCE')
const hasProfile = computed(() => profileDimensions.value.length > 0 || evidence.value.length > 0)
const overallStatus = computed(() => resolveOverallStatus())
const hasActionPlan = computed(() => Boolean(actionPlan.value?.id))
const planItems = computed(() => (Array.isArray(actionPlan.value?.items) ? actionPlan.value.items : []).map(item => ({ ...item, action: resolvePlanAction(item) })))
const lastUpdatedLabel = computed(() => {
  if (!profile.value.lastUpdated) return '暂无有效更新'
  const timestamp = new Date(profile.value.lastUpdated).getTime()
  if (Number.isNaN(timestamp)) return '更新时间格式异常'
  if (!Number.isNaN(timestamp) && timestamp > Date.now() + 5 * 60 * 1000) return '时间异常，等待下一次有效更新'
  return formatDate(profile.value.lastUpdated)
})

function isRecord(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function statusLabel(status) {
  const normalized = normalizeStatus(status)
  return {
    INSUFFICIENT_EVIDENCE: '样本不足',
    PENDING_CONFIRMATION: '待确认',
    PARTIALLY_CONFIRMED: '部分确认',
    CONFIRMED: '已确认',
    REJECTED: '已拒绝',
    VERIFIED: '已验证',
    RECORDED: '已记录'
  }[normalized] || '未评估'
}
function statusTone(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'PENDING_CONFIRMATION') return 'warning'
  if (normalized === 'PARTIALLY_CONFIRMED') return 'partial'
  if (normalized === 'CONFIRMED') return 'success'
  if (normalized === 'REJECTED') return 'rejected'
  if (normalized === 'VERIFIED') return 'verified'
  return ''
}
function inferenceStatusLabel(status) { return statusLabel(normalizeStatus(status) || 'PENDING_CONFIRMATION') }
function inferenceStatusTone(status) { return statusTone(normalizeStatus(status) || 'PENDING_CONFIRMATION') }
function isPendingConfirmation(status) { return normalizeStatus(status) === 'PENDING_CONFIRMATION' || !status }
function sourceLabel(type) { return type === 'CONVERSATION' ? '学习对话' : type === 'PRACTICE_SESSION' ? '主题练习' : type || '学习记录' }

function normalizeStatus(status) {
  const value = String(status || '').trim().toUpperCase()
  const aliases = {
    PENDING: 'PENDING_CONFIRMATION',
    PARTIAL: 'PARTIALLY_CONFIRMED',
    PARTIAL_CONFIRMATION: 'PARTIALLY_CONFIRMED',
    PARTIALLY_CONFIRMATION: 'PARTIALLY_CONFIRMED',
    SAMPLE_INSUFFICIENT: 'INSUFFICIENT_EVIDENCE',
    NEEDS_EVIDENCE: 'INSUFFICIENT_EVIDENCE'
  }
  return aliases[value] || value
}

function hasMeaningfulValue(value) {
  if (value === undefined || value === null) return false
  if (typeof value === 'string') {
    const text = value.trim()
    if (!text) return false
    try { return hasMeaningfulValue(JSON.parse(text)) } catch { return true }
  }
  if (Array.isArray(value)) return value.some(hasMeaningfulValue)
  if (isRecord(value)) return Object.values(value).some(hasMeaningfulValue)
  return true
}

function dimensionPayload(raw) {
  if (!isRecord(raw)) return { value: raw }
  if (Object.prototype.hasOwnProperty.call(raw, 'value')) return raw
  return { value: raw }
}

function formatDimensionValue(item) {
  const score = Number(item?.score)
  if (item?.kind === 'mastery' && Number.isFinite(score)) return `${score.toFixed(0)}%`
  return formatDisplayValue(item?.value)
}

function isCurrentInference(item) {
  if (!isRecord(item) || !hasMeaningfulValue(item.value)) return false
  if (item.current === false || item.isCurrent === false || item.active === false || item.effective === false || item.valid === false) return false
  const status = normalizeStatus(item.status)
  if (['ARCHIVED', 'EXPIRED', 'INACTIVE', 'INVALID', 'SUPERSEDED'].includes(status)) return false
  if (item.profileVersion === undefined || item.profileVersion === null || profile.value.version === undefined || profile.value.version === null) return false
  return String(item.profileVersion) === String(profile.value.version)
}

function isConfirmableInference(item) {
  if (!item || item.kind !== 'inference' || !isPendingConfirmation(item.status)) return false
  const itemVersion = Number(item.profileVersion)
  const currentVersion = Number(profile.value.version)
  return Number.isInteger(itemVersion) && itemVersion > 0
    && Number.isInteger(currentVersion) && itemVersion === currentVersion
}

function resolveOverallStatus() {
  const explicit = normalizeStatus(overview.value.status || view.value.overallStatus || view.value.profileStatus || summary.value.status)
  const statuses = inferences.value.map(item => normalizeStatus(item.status) || 'PENDING_CONFIRMATION')
  const pendingCount = statuses.filter(status => status === 'PENDING_CONFIRMATION').length
  const resolvedCount = statuses.filter(status => status === 'CONFIRMED' || status === 'REJECTED').length
  if (pendingCount > 0 && resolvedCount > 0) return 'PARTIALLY_CONFIRMED'
  if (pendingCount > 0) return 'PENDING_CONFIRMATION'
  if (statuses.length > 0 && statuses.every(status => status === 'REJECTED')) return 'REJECTED'
  if (statuses.some(status => status === 'REJECTED')) return 'PARTIALLY_CONFIRMED'
  if (explicit === 'VERIFIED' || explicit === 'INSUFFICIENT_EVIDENCE') return explicit
  if (resolvedCount > 0) return 'CONFIRMED'
  return explicit || (capabilityMetrics.value.length || evidence.value.length ? 'PENDING_CONFIRMATION' : 'INSUFFICIENT_EVIDENCE')
}

function formatDisplayValue(value) {
  if (value === undefined || value === null || value === '') return '待补充'
  let normalized = value
  if (typeof value === 'string') {
    try { normalized = JSON.parse(value) } catch { return value }
  }
  if (Array.isArray(normalized)) {
    const labels = normalized.map(item => formatDisplayValue(item)).filter(Boolean)
    return labels.length ? labels.join('、') : '待补充'
  }
  if (typeof normalized === 'object') {
    const type = normalized.type || normalized.resourceType
    const topic = normalized.topic || normalized.name || normalized.label
    if (type) {
      const label = resourceLabel(type)
      return topic ? `${label}（${topic}）` : label
    }
    if (topic) {
      const details = []
      if (Number.isFinite(Number(normalized.errorCount))) details.push(`错误 ${Number(normalized.errorCount)} 次`)
      if (hasMeaningfulValue(normalized.mastery)) details.push(`掌握情况：${formatDisplayValue(normalized.mastery)}`)
      return details.length ? `${topic}（${details.join('，')}）` : String(topic)
    }
    const values = Object.values(normalized).filter(value => ['string', 'number', 'boolean'].includes(typeof value))
    return values.length ? values.map(value => String(value)).join('、') : '待补充'
  }
  return String(normalized)
}

function resourceLabel(type) {
  const labels = {
    video_resource: '教学视频', video: '教学视频',
    html_animation: '互动动画', animation: '互动动画',
    teaching_image: '教学配图', image: '教学配图',
    study_document: '学习文档', document: '学习文档',
    quiz: '练习题', mindmap: '思维导图', code: '代码案例'
  }
  return labels[type] || type || '学习资源'
}

async function loadProfile() {
  loading.value = true
  loadError.value = ''
  try {
    const response = await eduApi.profileDetail()
    const payload = dataOf(response, null)
    if (!isRecord(payload) || !isRecord(payload.profileView) || !Number.isFinite(Number(payload.dimensionCount ?? 0))) {
      throw new Error('服务返回的学习画像数据不完整，请稍后重新加载。')
    }
    profile.value = payload
    return true
  } catch (error) {
    profile.value = {}
    loadError.value = error?.message || '请检查网络连接或登录状态后重试。'
    return false
  } finally { loading.value = false }
}

async function confirmInference(item, status) {
  const dimensionKey = String(item?.key || '').trim()
  const profileVersion = Number(profile.value.version)
  if (!dimensionKey || confirmationBusyKey.value || !isConfirmableInference(item)) return

  confirmationError.value = ''
  confirmationMessage.value = ''
  if (!Number.isInteger(profileVersion) || profileVersion < 0) {
    confirmationError.value = '当前画像版本无效，无法提交确认。请重新加载画像后再试。'
    return
  }

  confirmationBusyKey.value = dimensionKey
  confirmationMessage.value = `正在提交“${item.label || dimensionKey}”的确认结果。`
  try {
    const confirmationResponse = await eduApi.confirmProfileDimension(dimensionKey, { profileVersion, status })
    const confirmationPayload = dataOf(confirmationResponse, null)
    if (isRecord(confirmationPayload?.profileView)) applyProfileView(confirmationPayload.profileView)
    else updateLocalInferenceStatus(dimensionKey, profileVersion, status)

    try {
      const response = await eduApi.profileDetail()
      const payload = dataOf(response, null)
      if (!isValidProfilePayload(payload)) throw new Error('画像刷新数据不完整')
      profile.value = payload
      confirmationMessage.value = `“${item.label || dimensionKey}”已${status === 'CONFIRMED' ? '确认符合' : '标记为不符'}。`
    } catch {
      confirmationMessage.value = `确认结果已保存，但最新画像未能自动刷新；页面已保留本次结果，请稍后重新加载。`
    }
  } catch (error) {
    confirmationMessage.value = ''
    confirmationError.value = `“${item.label || dimensionKey}”提交失败：${error?.message || '请检查网络连接后重试。'}`
  } finally {
    confirmationBusyKey.value = ''
  }
}

function updateLocalInferenceStatus(dimensionKey, profileVersion, status) {
  const profileView = profile.value.profileView
  if (!isRecord(profileView) || !Array.isArray(profileView.inferences)) return
  profile.value = {
    ...profile.value,
    profileView: {
      ...profileView,
      inferences: profileView.inferences.map(item => {
        if (item?.key !== dimensionKey) return item
        if (item.profileVersion !== undefined && item.profileVersion !== null && String(item.profileVersion) !== String(profileVersion)) return item
        return { ...item, status }
      })
    }
  }
}

function applyProfileView(nextView) {
  const nextOverview = isRecord(nextView.overview) ? nextView.overview : {}
  profile.value = {
    ...profile.value,
    profileView: nextView,
    overview: nextOverview,
    dimensions: isRecord(nextView.dimensions) ? nextView.dimensions : profile.value.dimensions,
    dimensionCount: nextOverview.dimensionCount ?? profile.value.dimensionCount
  }
}

function isValidProfilePayload(payload) {
  return isRecord(payload) && isRecord(payload.profileView) && Number.isFinite(Number(payload.dimensionCount ?? 0))
}

async function loadActionPlans() {
  try {
    const [currentResponse, listResponse] = await Promise.all([eduApi.currentActionPlan(), eduApi.actionPlans()])
    const current = dataOf(currentResponse, currentResponse) || null
    const list = dataOf(listResponse, listResponse)
    actionPlans.value = (Array.isArray(list) ? list : [])
      .slice()
      .sort((left, right) => new Date(right?.createTime || 0).getTime() - new Date(left?.createTime || 0).getTime())
    const latestDraft = actionPlans.value.find(plan => plan?.status === 'DRAFT') || null
    actionPlan.value = current?.id ? current : latestDraft
  } catch (error) {
    planError.value = error?.message || '行动计划加载失败，请稍后重试。'
  }
}

async function generateOrRegenerateActionPlan() {
  if (hasActionPlan.value && actionPlan.value.status !== 'ARCHIVED') {
    await regenerateActionPlan()
    return
  }
  await generateActionPlan()
}

async function generateActionPlan() {
  planLoading.value = true
  planError.value = ''
  try {
    applyActionPlanResponse(await eduApi.generateActionPlan())
    if (!hasActionPlan.value) planError.value = '未获取到行动计划，请稍后重试。'
  } catch (error) {
    actionPlan.value = null
    planError.value = error?.message || '行动计划生成失败，请稍后重试。'
  } finally {
    planLoading.value = false
    await nextTick()
    planPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

async function confirmActionPlan() {
  if (!actionPlan.value?.id) return
  planLoading.value = true
  planError.value = ''
  try {
    applyActionPlanResponse(await eduApi.confirmActionPlan(actionPlan.value.id))
  } catch (error) {
    planError.value = error?.message || '确认行动计划失败，请稍后重试。'
  } finally { planLoading.value = false }
}

async function archiveActionPlan() {
  if (!actionPlan.value?.id) return
  planLoading.value = true
  planError.value = ''
  try {
    applyActionPlanResponse(await eduApi.archiveActionPlan(actionPlan.value.id))
  } catch (error) {
    planError.value = error?.message || '归档行动计划失败，请稍后重试。'
  } finally { planLoading.value = false }
}

async function regenerateActionPlan() {
  if (!actionPlan.value?.id || actionPlan.value.status === 'ARCHIVED') {
    await generateActionPlan()
    return
  }
  planLoading.value = true
  planError.value = ''
  try {
    applyActionPlanResponse(await eduApi.regenerateActionPlan(actionPlan.value.id))
    await nextTick()
    planPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (error) {
    planError.value = error?.message || '重新生成行动计划失败，请稍后重试。'
  } finally { planLoading.value = false }
}

function applyActionPlanResponse(response) {
  const payload = dataOf(response, response) || null
  actionPlan.value = payload?.id ? payload : null
  if (!actionPlan.value?.id) return
  const existingIndex = actionPlans.value.findIndex(plan => plan.id === actionPlan.value.id)
  if (existingIndex >= 0) actionPlans.value.splice(existingIndex, 1, actionPlan.value)
  else actionPlans.value.unshift(actionPlan.value)
}

function resolvePlanAction(item) {
  const title = String(item?.title || '')
  const description = String(item?.description || '')
  const text = `${title} ${description}`
  if (text.includes('练习') || text.includes('诊断')) return { type: 'route', route: '/quiz', label: '前往练习', icon: 'quiz' }
  if (text.includes('学习目标') || text.includes('对话')) return { type: 'route', route: '/chat', label: '前往对话', icon: 'chat' }
  if (text.includes('画像') || text.includes('复盘') || text.includes('更新')) return { type: 'refresh', label: '刷新画像', icon: 'refresh' }
  return null
}

async function executePlanItem(item) {
  if (!item?.id || !item.action || planItemBusyId.value === item.id) return
  planItemBusyId.value = item.id
  try {
    await updatePlanItemStatus(item, 'IN_PROGRESS', { keepBusy: true })
    if (item.action.type === 'refresh') {
      const refreshed = await loadProfile()
      if (!refreshed) throw new Error(loadError.value || '刷新画像失败')
      return
    }
    await router.push(item.action.route)
  } catch (error) {
    planError.value = error?.message || '执行计划步骤失败，请稍后重试。'
  } finally {
    planItemBusyId.value = null
  }
}

async function updatePlanItemStatus(item, status, options = {}) {
  if (!item?.id) return
  if (!options.keepBusy) planItemBusyId.value = item.id
  planError.value = ''
  try {
    applyActionPlanResponse(await eduApi.updateActionPlanItem(item.id, status))
  } catch (error) {
    planError.value = error?.message || '更新步骤状态失败，请稍后重试。'
    throw error
  } finally {
    if (!options.keepBusy) planItemBusyId.value = null
  }
}

function planStatusLabel(status) {
  return { DRAFT: '待确认', ACTIVE: '执行中', ARCHIVED: '已归档' }[status] || '未知状态'
}

function planStatusTone(status) {
  return status === 'ACTIVE' ? 'success' : status === 'DRAFT' ? 'warning' : ''
}

function planItemStatusLabel(status) {
  return { PENDING: '待开始', IN_PROGRESS: '进行中', COMPLETED: '已完成', SKIPPED: '已跳过' }[status] || '未知状态'
}

onMounted(async () => {
  await Promise.all([loadProfile(), loadActionPlans()])
})
</script>

<style scoped>
.profile-heading { min-width: 0; }
.profile-heading > p { overflow-wrap: anywhere; }
.profile-meta { display: flex; align-items: center; gap: var(--space-md); padding: var(--space-sm) var(--space-md); }
.profile-meta span, .profile-meta strong { display: block; font-size: 13px; }
.profile-meta span { color: var(--on-surface-variant); }
.profile-meta strong { color: var(--primary); white-space: nowrap; }
.meta-divider { width: 1px; height: 32px; background: var(--outline-variant); }
.profile-grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-lg); margin-bottom: var(--space-lg); }
.profile-page h2 { letter-spacing: 0; }
.profile-page h3 { margin: 4px 0 0; display: flex; align-items: center; gap: var(--space-xs); font-size: 20px; line-height: 28px; }
.profile-page h4 { margin: 0; font-size: 16px; line-height: 24px; }
.eyebrow { color: var(--primary); font-size: 12px; font-weight: 700; }
.capability-panel header, .dimensions-panel > header, .evidence-panel > header, .plan-panel > header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-md); margin-bottom: var(--space-md); }

.profile-overview { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(260px, .65fr); gap: var(--space-xl); margin-bottom: var(--space-xl); }
.overview-main, .evidence-summary { min-width: 0; }
.overview-title-row { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-sm); }
.overview-summary { max-width: 720px; margin: var(--space-md) 0 var(--space-lg); color: var(--on-surface-variant); line-height: 1.7; overflow-wrap: anywhere; }
.overview-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 0; border-top: 1px solid var(--outline-variant); border-bottom: 1px solid var(--outline-variant); }
.overview-stats > div { min-width: 0; padding: var(--space-md) var(--space-sm); border-inline-start: 1px solid var(--outline-variant); }
.overview-stats > div:first-child { border-inline-start: 0; padding-inline-start: 0; }
.overview-stats dt, .evidence-breakdown dt { color: var(--on-surface-variant); font-size: 12px; }
.overview-stats dd { margin: 2px 0 0; color: var(--on-surface); font-size: 24px; line-height: 30px; font-weight: 700; }
.evidence-summary { display: flex; flex-direction: column; justify-content: center; padding-inline-start: var(--space-xl); border-inline-start: 1px solid var(--outline-variant); }
.evidence-summary > strong { margin-top: var(--space-xs); font-size: 18px; line-height: 26px; }
.evidence-summary > p { margin: var(--space-sm) 0; color: var(--on-surface-variant); line-height: 1.6; overflow-wrap: anywhere; }
.evidence-breakdown { display: flex; flex-wrap: wrap; gap: var(--space-sm) var(--space-lg); margin: 0; }
.evidence-breakdown > div { display: flex; align-items: baseline; gap: 6px; }
.evidence-breakdown dd { margin: 0; color: var(--on-surface); font-weight: 700; }
.overview-action { width: auto; align-self: flex-start; margin-top: var(--space-lg); }

.dimensions-panel { margin-bottom: var(--space-xl); padding: 0 0 var(--space-lg); border-bottom: 1px solid var(--outline-variant); }
.dimensions-panel > header small { max-width: 360px; color: var(--on-surface-variant); text-align: end; overflow-wrap: anywhere; }
.dimension-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); border-top: 1px solid var(--outline-variant); }
.dimension-block { min-width: 0; padding: var(--space-lg) var(--space-md); border-bottom: 1px solid var(--outline-variant); border-inline-start: 3px solid transparent; }
.dimension-block:nth-child(odd) { border-inline-end: 1px solid var(--outline-variant); padding-inline-start: 0; }
.dimension-block:nth-child(even) { padding-inline-end: 0; }
.dimension-block.warning { border-inline-start-color: var(--warning); }
.dimension-block.confirmed, .dimension-block.verified { border-inline-start-color: var(--success); }
.dimension-block.rejected { border-inline-start-color: var(--error); }
.dimension-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-sm); }
.dimension-head > div { min-width: 0; }
.dimension-head > div > span { display: block; margin-bottom: 2px; color: var(--on-surface-variant); font-size: 12px; }
.dimension-value { margin: var(--space-sm) 0; color: var(--on-surface); font-size: 16px; line-height: 25px; font-weight: 650; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word; }
.dimension-evidence { display: flex; align-items: flex-start; gap: 6px; margin: 0; color: var(--on-surface-variant); font-size: 13px; line-height: 20px; overflow-wrap: anywhere; }
.dimension-evidence .material-symbols-outlined { flex: 0 0 auto; font-size: 18px; color: var(--primary); }
.dimension-footer { display: flex; flex-wrap: wrap; gap: 6px var(--space-md); margin-top: var(--space-md); color: var(--on-surface-variant); font-size: 12px; }
.confirmation-actions { display: flex; flex-wrap: wrap; gap: var(--space-sm); margin-top: var(--space-md); }
.confirmation-button { width: auto; min-height: 40px; }
.confirmation-feedback { margin: 0 0 var(--space-md); padding: 10px 12px; border-inline-start: 3px solid var(--success); color: var(--success); background: var(--success-container); overflow-wrap: anywhere; }
.confirmation-feedback.error { border-inline-start-color: var(--error); color: var(--on-error-container); background: var(--error-container); }
.section-empty { margin: 0; color: var(--on-surface-variant); }

.capability-panel { margin-bottom: var(--space-xl); }
.metric-list { display: grid; gap: var(--space-lg); }
.metric-head { display: flex; justify-content: space-between; gap: var(--space-md); margin-bottom: 6px; }
.metric-head strong { min-width: 0; overflow-wrap: anywhere; }
.metric-head span { flex: 0 0 auto; color: var(--primary); font-weight: 700; }
.bar-track { height: 8px; border-radius: 999px; overflow: hidden; background: var(--surface-container); }
.bar-track i { display: block; height: 100%; background: var(--primary); border-radius: inherit; }
.metric-row small, .evidence-list small { display: block; margin-top: 6px; color: var(--on-surface-variant); }
.mastery-empty { display: flex; align-items: center; gap: var(--space-md); min-height: 128px; padding: var(--space-lg) 0; border-top: 1px solid var(--outline-variant); border-bottom: 1px solid var(--outline-variant); }
.mastery-empty > .material-symbols-outlined { flex: 0 0 auto; color: var(--primary); font-size: 32px; }
.mastery-empty > div { min-width: 0; flex: 1; }
.mastery-empty strong { display: block; font-size: 16px; }
.mastery-empty p { margin: 4px 0 0; color: var(--on-surface-variant); line-height: 1.6; overflow-wrap: anywhere; }
.mastery-empty .ghost-button { flex: 0 0 auto; width: auto; }

.evidence-panel { padding: 0 0 var(--space-lg); margin-bottom: var(--space-xl); border-bottom: 1px solid var(--outline-variant); }
.evidence-list { display: grid; gap: var(--space-sm); }
.evidence-list article { display: flex; gap: var(--space-md); min-width: 0; padding: var(--space-md) 0; border-top: 1px solid var(--outline-variant); }
.evidence-icon { flex: 0 0 40px; width: 40px; height: 40px; display: grid; place-items: center; border-radius: var(--radius-md); color: var(--primary); background: var(--primary-fixed); }
.evidence-content { min-width: 0; flex: 1; }
.evidence-head { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-sm); }
.evidence-head h4 { min-width: 0; overflow-wrap: anywhere; }
.evidence-list p { margin: 6px 0 0; color: var(--on-surface-variant); white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word; }
.evidence-meta { display: flex; flex-wrap: wrap; gap: 6px var(--space-md); margin: 10px 0 0; }
.evidence-meta div { display: flex; min-width: 0; gap: 4px; font-size: 12px; }
.evidence-meta dt { color: var(--on-surface-variant); }
.evidence-meta dd { margin: 0; font-weight: 600; overflow-wrap: anywhere; }

.status-pill { display: inline-flex; align-items: center; flex: 0 0 auto; width: max-content; max-width: 100%; padding: 3px 8px; border-radius: 999px; color: var(--on-surface-variant); background: var(--surface-container); font-size: 12px; }
.status-pill.warning { color: #854d0e; background: var(--warning-container); }
.status-pill.partial { color: #725b00; background: #fff6c8; }
.status-pill.success { color: var(--success); background: var(--success-container); }
.status-pill.rejected { color: var(--on-error-container); background: var(--error-container); }
.status-pill.verified { color: #0a5f8f; background: #e4f4ff; }

.plan-panel { margin-bottom: var(--space-lg); }
.plan-panel > p { color: var(--on-surface-variant); overflow-wrap: anywhere; }
.plan-panel ol { display: grid; gap: var(--space-sm); padding-inline-start: 20px; }
.plan-panel li { padding-inline-start: 4px; }
.plan-panel li strong, .plan-panel li span { display: block; overflow-wrap: anywhere; }
.plan-panel li span { margin-top: 4px; color: var(--on-surface-variant); }
.plan-controls { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: var(--space-xs); }
.plan-history-count { color: var(--on-surface-variant); font-size: 12px; }
.plan-control-button, .plan-step-button { width: auto; min-height: 32px; display: inline-flex; align-items: center; gap: 4px; padding: 0 10px; font-size: 12px; }
.plan-step-actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-sm); margin-top: var(--space-sm); }
.plan-step-actions small { color: var(--on-surface-variant); font-size: 12px; }
.plan-step-actions small.complete { color: var(--success); }
.plan-step-button .material-symbols-outlined { font-size: 16px; }
.error-copy { color: var(--on-error-container); overflow-wrap: anywhere; }
button:focus-visible { outline: 3px solid var(--primary); outline-offset: 2px; }

@media (max-width: 1080px) {
  .profile-overview { grid-template-columns: 1fr; gap: var(--space-lg); }
  .evidence-summary { padding: var(--space-lg) 0 0; border-inline-start: 0; border-top: 1px solid var(--outline-variant); }
  .profile-meta { width: 100%; justify-content: space-between; }
}

@media (max-width: 760px) {
  .dimension-grid { grid-template-columns: 1fr; }
  .dimension-block:nth-child(odd), .dimension-block:nth-child(even) { padding-inline: 0; border-inline-end: 0; }
  .dimension-block:first-child { border-top: 0; }
  .overview-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .overview-stats > div:nth-child(odd) { border-inline-start: 0; padding-inline-start: 0; }
  .overview-stats > div:nth-child(n + 3) { border-top: 1px solid var(--outline-variant); }
  .mastery-empty { align-items: flex-start; flex-wrap: wrap; }
  .mastery-empty .ghost-button { margin-inline-start: 40px; }
}

@media (max-width: 560px) {
  .profile-meta { align-items: flex-start; flex-direction: column; }
  .meta-divider { width: 100%; height: 1px; }
  .capability-panel > header, .dimensions-panel > header, .evidence-panel > header, .plan-panel > header { flex-direction: column; }
  .dimensions-panel > header small { text-align: start; }
  .confirmation-actions { align-items: stretch; flex-direction: column; }
  .confirmation-button, .plan-control-button, .plan-step-button { width: 100%; min-height: 44px; }
  .evidence-list article { gap: var(--space-sm); }
  .evidence-meta { flex-direction: column; gap: 4px; }
  .mastery-empty .ghost-button { margin-inline-start: 0; }
}
</style>
