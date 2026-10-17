<template>
  <section class="page report-page">
    <div class="page-title-row">
      <div>
        <span class="report-eyebrow">学习复盘</span>
        <h2>学习报告</h2>
        <p>基于已记录的互动、带时长行为用时和已评阅测验生成；不展示推测数据。</p>
      </div>
      <div class="report-actions">
        <label class="report-range">
          <span>统计范围</span>
          <select v-model.number="rangeDays" class="control" :disabled="loading" @change="loadReport">
            <option v-for="option in rangeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <button class="ghost-button" type="button" :disabled="loading" @click="loadReport">
          <span class="material-symbols-outlined" :class="{ spin: loading }">refresh</span>
          刷新
        </button>
        <button class="primary-button export-button" type="button" :disabled="loading || reportError || !detailLoaded" @click="downloadReport">
          <span class="material-symbols-outlined filled">download</span>
          导出 HTML
        </button>
      </div>
    </div>

    <section v-if="!loading && !reportError" class="report-insight" aria-labelledby="report-insight-title">
      <div class="report-insight-copy">
        <span class="report-eyebrow">本期结论</span>
        <div class="report-insight-heading">
          <h3 id="report-insight-title">{{ reportInsight.title }}</h3>
          <span v-if="profileSummary.status" class="status-pill" :class="profileStatusTone(profileSummary.status)">
            画像{{ profileStatusLabel(profileSummary.status) }}
          </span>
        </div>
        <p>{{ reportInsight.description }}</p>
      </div>
      <div class="report-insight-actions">
        <button class="primary-button" type="button" @click="runInsightAction">
          <span class="material-symbols-outlined" aria-hidden="true">{{ reportInsight.route ? 'arrow_forward' : 'refresh' }}</span>
          {{ reportInsight.actionLabel }}
        </button>
        <button v-if="profileDetailAvailable" class="ghost-button" type="button" @click="router.push('/profile')">
          <span class="material-symbols-outlined" aria-hidden="true">person_search</span>
          查看学习画像
        </button>
      </div>
    </section>

    <div v-if="loading" class="report-grid">
      <SkeletonBlock v-for="n in 6" :key="n" height="180px" />
    </div>

    <UiState
      v-else-if="reportError"
      icon="error_outline"
      title="学习报告暂时无法加载"
      :description="reportError"
      action-label="重新加载"
      action-icon="refresh"
      @action="loadReport"
    />

    <template v-else>
      <div class="stat-row">
        <article v-for="item in statCards" :key="item.label" class="stat-card">
          <div>
            <span>{{ item.label }}</span>
            <span class="material-symbols-outlined" :style="{ color: item.color }">{{ item.icon }}</span>
          </div>
          <strong>{{ item.value }}<small v-if="item.unit">{{ item.unit }}</small></strong>
          <p>{{ item.note }}</p>
        </article>
      </div>

      <p class="report-data-note">
        <span class="material-symbols-outlined" aria-hidden="true">info</span>
        学习画像：{{ profileStatusText }}；带时长行为用时只累计日志中已有的时长记录。
      </p>
      <p v-if="reportSourceWarnings.length" class="report-source-warning" role="status">
        <span class="material-symbols-outlined" aria-hidden="true">warning</span>
        部分报告数据暂时无法读取：{{ reportSourceWarnings.join('、') }}。页面不会将其显示为零或“暂无数据”。
      </p>
      <div class="report-layout">
        <div class="left-column">
          <section class="panel heatmap-panel">
            <header>
              <div>
                <h3>每日学习行为</h3>
                <p>{{ rangeLabel }}内的行为次数，不等同于掌握程度</p>
              </div>
              <span class="panel-total">{{ behaviorAvailable ? `${activeDayCount} 天活跃 · ${summary.rangeActions || 0} 次` : '—' }}</span>
            </header>

            <div v-if="!behaviorAvailable" class="report-source-unavailable">
              <span class="material-symbols-outlined" aria-hidden="true">cloud_off</span>
              <p>学习行为数据暂时无法读取，请刷新后重试。</p>
            </div>
            <div v-else-if="hasActivity" class="heatmap-scroll">
              <div class="heatmap-grid" :style="{ '--heatmap-columns': 7 }" role="grid" :aria-label="heatmapDescription">
                <span v-for="day in weekdays" :key="day" class="weekday">{{ day }}</span>
                <span v-for="(item, index) in paddedHeatmap" :key="`${item.date || 'blank'}-${index}`" class="heat-cell" :class="{ blank: !item.date }" :style="item.date ? { opacity: item.opacity } : undefined" :title="item.title" :aria-label="item.ariaLabel" :role="item.date ? 'gridcell' : undefined"></span>
              </div>
              <div class="heatmap-meta">
                <span>活跃 {{ activeDayCount }} 天</span>
                <span>单日最高 {{ maxActionCount }} 次</span>
              </div>
            </div>
            <div v-else class="report-empty-block">
              <span class="material-symbols-outlined" aria-hidden="true">calendar_month</span>
              <p>{{ rangeLabel }}内还没有学习行为。开始对话、查看资源或完成练习后，这里会显示真实活跃记录。</p>
            </div>

            <div v-if="hasActivity" class="heat-legend" aria-label="活跃度图例">
              <span>0 次</span><i></i><i class="mid"></i><i class="high"></i><span>{{ maxActionCount }} 次</span>
            </div>
          </section>

          <section class="panel trend-panel">
            <header>
              <div>
                <h3>客观题作答正确率趋势</h3>
                <p>按单选、多选和填空题的已评阅作答记录统计。</p>
              </div>
              <span v-if="trendPoints.length" class="panel-total">{{ trendSummary }}</span>
            </header>

            <div v-if="!quizAvailable" class="report-source-unavailable compact">
              <span class="material-symbols-outlined" aria-hidden="true">cloud_off</span>
              <p>测验数据暂时无法读取，请刷新后重试。</p>
            </div>
            <div v-else-if="trendPoints.length > 1" class="trend-chart-wrap">
              <div class="trend-chart-content">
                <div class="trend-y-axis" aria-hidden="true">
                  <span v-for="value in trendAxisLabels" :key="value">{{ value }}%</span>
                </div>
                <div class="trend-plot">
                  <div class="trend-grid-lines" aria-hidden="true">
                    <span v-for="value in trendAxisLabels" :key="value" :style="{ top: `${100 - value}%` }"></span>
                  </div>
                  <svg class="trend-chart" viewBox="0 0 100 100" preserveAspectRatio="none" role="img" :aria-label="`${rangeLabel}客观题作答正确率趋势`">
                    <path :d="trendAreaPath" fill="var(--secondary)" opacity="0.12" />
                    <path :d="trendLinePath" fill="none" stroke="var(--secondary)" stroke-width="2" vector-effect="non-scaling-stroke" />
                  </svg>
                  <span v-for="point in trendPoints" :key="point.date" class="trend-point" :style="trendPointStyle(point)" :title="trendPointTitle(point)" :aria-label="trendPointTitle(point)"></span>
                </div>
              </div>
              <div class="trend-labels">
                <span>{{ trendPoints[0].date }}</span>
                <span>{{ trendPoints[trendPoints.length - 1].date }}</span>
              </div>
              <p class="trend-note">每个点显示当日已评阅题数和正确率，数据不足时不补画趋势。</p>
            </div>
            <div v-else-if="trendPoints.length" class="report-empty-block compact">
              <span class="single-trend-rate">{{ formatMetric(trendPoints[0].rate) }}%</span>
              <p>目前仅有 1 个统计日，累计更多已评阅作答后才展示趋势。</p>
            </div>
            <div v-else class="report-empty-block compact">
              <span class="material-symbols-outlined" aria-hidden="true">query_stats</span>
              <p>还没有可用于统计的已评阅客观题作答。</p>
            </div>
          </section>
        </div>

        <aside class="right-column">
          <section class="panel distribution-panel">
            <header>
              <div>
                <h3>学习行为构成</h3>
                <p>{{ rangeLabel }}内按行为次数统计，不代表掌握程度</p>
              </div>
            </header>
            <div v-if="!behaviorAvailable" class="report-source-unavailable compact">
              <span class="material-symbols-outlined" aria-hidden="true">cloud_off</span>
              <p>学习行为数据暂时无法读取，请刷新后重试。</p>
            </div>
            <template v-else-if="actionDistribution.length">
              <div class="distribution-list">
                <div v-for="item in actionDistribution" :key="item.name" class="distribution-item">
                  <div class="distribution-item-head">
                    <span><i :style="{ background: item.color }"></i>{{ item.name }}</span>
                    <strong>{{ item.percent }}% · {{ item.count }} 次</strong>
                  </div>
                  <div class="distribution-track" role="img" :aria-label="`${item.name} ${item.percent}%，${item.count} 次`">
                    <span :style="{ width: `${item.percent}%`, background: item.color }"></span>
                  </div>
                </div>
              </div>
            </template>
            <div v-else class="report-empty-block compact">
              <span class="material-symbols-outlined" aria-hidden="true">donut_small</span>
              <p>暂无可统计的学习行为。</p>
            </div>
          </section>

          <section class="panel activity-panel">
            <header>
              <div>
                <h3>近期活动</h3>
                <p>最近记录的学习行为与提交结果</p>
              </div>
              <button v-if="recentActivities.length > activityDisplayLimit" type="button" @click="showAllActivities = !showAllActivities">
                {{ showAllActivities ? '收起' : '查看全部' }}
              </button>
            </header>
            <div v-if="visibleActivities.length" class="activity-list">
              <article v-for="item in visibleActivities" :key="item.key">
                <div class="activity-icon">
                  <span class="material-symbols-outlined">{{ actionIcon(item.actionType) }}</span>
                </div>
                <div>
                  <p>{{ actionText(item) }}</p>
                  <small>{{ formatDate(item.time) }}</small>
                </div>
              </article>
            </div>
            <div v-else-if="!recentActivityAvailable" class="report-source-unavailable compact">
              <span class="material-symbols-outlined" aria-hidden="true">cloud_off</span>
              <p>近期活动数据暂时无法完整读取，请刷新后重试。</p>
            </div>
            <div v-else class="report-empty-block compact">
              <span class="material-symbols-outlined" aria-hidden="true">history</span>
              <p>暂无近期活动。</p>
            </div>
          </section>
        </aside>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { eduApi } from '@/services/api'
import { dataOf, formatDate, percent } from '@/utils/format'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import UiState from '@/components/UiState.vue'

const rangeOptions = [
  { value: 7, label: '近 7 天' },
  { value: 30, label: '近 30 天' },
  { value: 90, label: '近 90 天' }
]
const weekdays = ['一', '二', '三', '四', '五', '六', '日']
const activityDisplayLimit = 8
const trendAxisLabels = [100, 75, 50, 25, 0]

const loading = ref(true)
const reportError = ref('')
const rangeDays = ref(30)
const summary = ref({})
const detailed = ref({})
const profileDetail = ref({})
const showAllActivities = ref(false)
const detailLoaded = ref(false)
const detailedLoadError = ref('')
const profileDetailAvailable = ref(false)
const profileLoadError = ref('')
const router = useRouter()

const rangeLabel = computed(() => rangeOptions.find(item => item.value === rangeDays.value)?.label || `近 ${rangeDays.value} 天`)
const behaviorAvailable = computed(() => summary.value.behaviorAvailable === true)
const quizAvailable = computed(() => summary.value.quizAvailable === true)
const sourceErrors = computed(() => [...new Set([
  ...(Array.isArray(summary.value.sourceErrors) ? summary.value.sourceErrors : []),
  ...(Array.isArray(detailed.value.sourceErrors) ? detailed.value.sourceErrors : [])
])])
const reportSourceWarnings = computed(() => [...new Set([
  ...sourceErrors.value,
  ...(detailedLoadError.value ? ['近期活动'] : []),
  ...(profileLoadError.value ? ['学习画像'] : [])
])])
const recentActivityAvailable = computed(() => detailLoaded.value && detailed.value.behaviorActivityAvailable !== false && detailed.value.quizActivityAvailable !== false)
const profileOverview = computed(() => {
  const payload = profileDetail.value
  const view = payload && typeof payload.profileView === 'object' && !Array.isArray(payload.profileView) ? payload.profileView : {}
  const overview = payload && typeof payload.overview === 'object' && !Array.isArray(payload.overview)
    ? payload.overview
    : view.overview && typeof view.overview === 'object' ? view.overview : {}
  return overview
})
const profileSummary = computed(() => {
  const overview = profileOverview.value
  const dimensionCount = Number(overview.dimensionCount ?? profileDetail.value.dimensionCount ?? 0)
  const evidenceCount = Number(overview.evidenceCount ?? 0)
  return {
    status: String(overview.status || profileDetail.value.profileView?.overallStatus || '').toUpperCase(),
    dimensionCount: Number.isFinite(dimensionCount) ? dimensionCount : 0,
    evidenceCount: Number.isFinite(evidenceCount) ? evidenceCount : 0,
    pending: Number(overview.pendingConfirmationCount || 0),
    confirmed: Number(overview.confirmedCount || 0),
    verified: Number(overview.verifiedDimensionCount || 0),
    recorded: Number(overview.recordedDimensionCount || 0)
  }
})
const profileStatusText = computed(() => {
  const item = profileSummary.value
  if (!profileDetailAvailable.value) return '画像状态暂时无法读取'
  if (!item.dimensionCount && !item.evidenceCount) return '尚未形成可追溯画像'
  const parts = [`${item.dimensionCount} 项维度`]
  if (item.verified) parts.push(`${item.verified} 项已验证`)
  if (item.confirmed) parts.push(`${item.confirmed} 项已确认`)
  if (item.pending) parts.push(`${item.pending} 项待确认`)
  return parts.join('，')
})
const reportInsight = computed(() => {
  const actions = Number(summary.value.rangeActions || 0)
  const assessed = Number(summary.value.quizAssessedTotal || 0)
  const pendingProfile = profileSummary.value.pending
  const rate = Number(summary.value.quizCorrectRate)

  if (!behaviorAvailable.value && !quizAvailable.value) {
    return {
      title: '报告数据暂时不可用',
      description: '学习行为和测验数据都没有成功读取，请刷新后再查看本期复盘。',
      actionLabel: '重新加载',
      route: ''
    }
  }
  if (!behaviorAvailable.value) {
    return {
      title: '学习行为数据暂时无法读取',
      description: '测验数据已经读取，但本期行为次数和用时不可用；刷新成功前不会把缺失数据解释为零。',
      actionLabel: '重新加载',
      route: ''
    }
  }
  if (!quizAvailable.value) {
    return {
      title: '测验数据暂时无法读取',
      description: '学习行为数据已经读取，但本期已评阅作答不可用；刷新成功前不会把缺失数据解释为没有测验。',
      actionLabel: '重新加载',
      route: ''
    }
  }
  if (actions === 0 && assessed === 0) {
    return {
      title: '本期还没有可复盘的学习记录',
      description: '从一次 AI 对话、资源学习或主题练习开始，系统才会生成真实的学习证据。',
      actionLabel: '开始学习',
      route: '/chat'
    }
  }
  if (assessed === 0) {
    return {
      title: '已有学习记录，下一步完成一次测验',
      description: `${rangeLabel.value}已记录 ${actions} 次学习行为，但还没有已评阅的客观题作答，暂时无法判断作答表现。`,
      actionLabel: '去完成测验',
      route: '/quiz'
    }
  }
  if (assessed < 5) {
    return {
      title: '测验样本还不足以形成稳定结论',
      description: `${rangeLabel.value}只有 ${assessed} 条已评阅客观题作答，当前正确率仅作记录；继续完成练习后再观察趋势。`,
      actionLabel: '继续测验',
      route: '/quiz'
    }
  }
  if (Number.isFinite(rate) && rate < 60) {
    return {
      title: '本期客观题作答正确率偏低',
      description: `${rangeLabel.value}有 ${assessed} 条已评阅客观题作答，正确率为 ${formatMetric(rate)}%；建议继续练习并结合错题定位需要巩固的内容。`,
      actionLabel: '去做练习',
      route: '/quiz'
    }
  }
  if (pendingProfile > 0) {
    return {
      title: '学习表现已有记录，还有画像结论待确认',
      description: `${rangeLabel.value}已有 ${assessed} 条已评阅客观题作答；学习画像中有 ${pendingProfile} 项推断等待你确认。`,
      actionLabel: '查看画像',
      route: '/profile'
    }
  }
  return {
    title: '本期学习记录可以继续积累',
    description: `${rangeLabel.value}有 ${assessed} 条已评阅客观题作答，正确率 ${formatMetric(rate)}%；继续保持学习记录，趋势会更有参考价值。`,
    actionLabel: '继续学习',
    route: '/chat'
  }
})
const statCards = computed(() => {
  const assessed = Number(summary.value.quizAssessedTotal || 0)
  const pending = Number(summary.value.quizPendingReview || 0)
  const correctRate = summary.value.quizCorrectRate
  const todayActions = Number(summary.value.todayActions || 0)
  const todayMinutes = Number(summary.value.todayMinutes || 0)
  return [
    {
      label: '今日学习行为',
      value: behaviorAvailable.value ? todayActions : '—',
      unit: behaviorAvailable.value ? '次' : '',
      note: behaviorAvailable.value
        ? (todayActions === 0 ? '今日还没有学习行为' : todayMinutes > 0 ? `含 ${todayMinutes} 分钟已记录用时` : '有行为记录，但暂未记录用时')
        : '学习行为数据暂时无法读取',
      icon: 'touch_app',
      color: 'var(--secondary)'
    },
    {
      label: `${rangeLabel.value}带时长行为用时`,
      value: behaviorAvailable.value ? formatMetric(summary.value.rangeStudyHours) : '—',
      unit: behaviorAvailable.value ? '小时' : '',
      note: behaviorAvailable.value ? `已记录 ${Number(summary.value.rangeActions || 0)} 次行为；仅累计带时长记录` : '学习行为数据暂时无法读取',
      icon: 'timer',
      color: 'var(--primary)'
    },
    {
      label: '客观题作答正确率',
      value: quizAvailable.value ? (assessed ? percent(correctRate) : '暂无') : '—',
      unit: quizAvailable.value && assessed ? '%' : '',
      note: quizAvailable.value ? (assessed ? `已评阅 ${assessed} 条作答${pending ? ` · 待评 ${pending} 条` : ''}` : (pending ? `有 ${pending} 条作答待评阅` : '完成客观题后生成')) : '测验数据暂时无法读取',
      icon: 'quiz',
      color: 'var(--primary)'
    },
    {
      label: '当前连续学习',
      value: behaviorAvailable.value ? Number(summary.value.consecutiveDays || 0) : '—',
      unit: behaviorAvailable.value ? '天' : '',
      note: behaviorAvailable.value ? '从今天起按连续有记录的日期计算' : '学习行为数据暂时无法读取',
      icon: 'local_fire_department',
      color: 'var(--success)'
    }
  ]
})

const dailyActivity = computed(() => Array.isArray(summary.value.dailyActivity) ? summary.value.dailyActivity : [])
const hasActivity = computed(() => dailyActivity.value.some(item => Number(item.actionCount || 0) > 0))
const activeDayCount = computed(() => dailyActivity.value.filter(item => Number(item.actionCount || 0) > 0).length)
const maxActionCount = computed(() => Math.max(0, ...dailyActivity.value.map(item => Number(item.actionCount || 0))))
const paddedHeatmap = computed(() => {
  if (!dailyActivity.value.length) return []
  const firstDate = new Date(`${dailyActivity.value[0].date}T00:00:00`)
  const blankCount = Number.isNaN(firstDate.getTime()) ? 0 : (firstDate.getDay() + 6) % 7
  const peak = Math.max(1, ...dailyActivity.value.map(item => Number(item.actionCount || 0)))
  const blanks = Array.from({ length: blankCount }, () => ({ date: '', opacity: 0, title: '' }))
  const items = dailyActivity.value.map(item => {
    const count = Number(item.actionCount || 0)
    const minutes = Number(item.minutes || 0)
    return {
      date: item.date,
      opacity: count ? Math.min(1, 0.24 + (count / peak) * 0.76) : 0.08,
      title: `${item.date}：${count} 次学习行为，${formatMetric(minutes)} 分钟已记录用时`,
      ariaLabel: `${item.date}，${count} 次学习行为，${formatMetric(minutes)} 分钟已记录用时`
    }
  })
  return [...blanks, ...items]
})

const heatmapDescription = computed(() => {
  const activeDays = dailyActivity.value.filter(item => Number(item.actionCount || 0) > 0)
  if (!activeDays.length) return `${rangeLabel.value}内没有学习行为。`
  const busiestDay = activeDays.reduce((current, item) => Number(item.actionCount || 0) > Number(current.actionCount || 0) ? item : current)
  return `${rangeLabel.value}内共 ${Number(summary.value.rangeActions || 0)} 次学习行为，活跃 ${activeDays.length} 天；最活跃的是 ${busiestDay.date}，共 ${Number(busiestDay.actionCount || 0)} 次。`
})

const actionDistribution = computed(() => {
  const raw = Array.isArray(summary.value.actionDistribution) ? summary.value.actionDistribution : []
  const total = raw.reduce((sum, item) => sum + Number(item.count || 0), 0)
  if (!total) return []
  const colors = ['var(--primary)', 'var(--secondary)', 'var(--success)', 'var(--tertiary-fixed-dim)', 'var(--warning)']
  return raw.map((item, index) => ({
    name: actionName(item.actionType),
    count: Number(item.count || 0),
    percent: Math.round(Number(item.count || 0) / total * 100),
    color: colors[index % colors.length]
  }))
})

const trendPoints = computed(() => {
  const raw = Array.isArray(summary.value.quizTrend) ? summary.value.quizTrend : []
  return raw
    .map(item => {
      const assessed = Number(item.assessed_count ?? item.assessedCount ?? 0)
      const correct = Number(item.correct_count ?? item.correctCount ?? 0)
      if (!Number.isFinite(assessed) || !Number.isFinite(correct) || assessed <= 0) return null
      return {
        date: String(item.date || '').slice(0, 10),
        rate: correct / assessed * 100,
        assessed,
        correct
      }
    })
    .filter(item => item && item.date && Number.isFinite(item.rate))
})

const trendSummary = computed(() => {
  const total = trendPoints.value.reduce((sum, item) => sum + item.assessed, 0)
  return `${trendPoints.value.length} 天 · ${total} 条作答`
})

const trendLinePath = computed(() => createTrendPath(trendPoints.value))
const trendAreaPath = computed(() => {
  const line = trendLinePath.value
  return line ? `${line} L 100 100 L 0 100 Z` : ''
})

const recentActivities = computed(() => {
  const logs = Array.isArray(detailed.value.recentLogs) ? detailed.value.recentLogs : []
  const quizRecords = Array.isArray(detailed.value.quizRecords) ? detailed.value.quizRecords : []
  const activityLogs = detailed.value.quizActivityAvailable === false
    ? logs
    : logs.filter(item => String(item.actionType || item.type || '').toUpperCase() !== 'ANSWER_QUIZ')
  return [
    ...activityLogs.map(item => ({ ...item, key: `log-${item.id}`, time: item.createTime, source: 'log' })),
    ...quizRecords.map(item => ({ ...item, key: `quiz-${item.id}`, time: item.answerTime || item.createTime, actionType: 'ANSWER_QUIZ', source: 'quiz' }))
  ]
    .filter(item => item.time)
    .sort((left, right) => new Date(right.time).getTime() - new Date(left.time).getTime())
})

const visibleActivities = computed(() => showAllActivities.value ? recentActivities.value : recentActivities.value.slice(0, activityDisplayLimit))

function actionName(type) {
  const map = {
    VIEW_RESOURCE: '查看资源',
    CONVERSATION: 'AI 对话',
    GENERATE_RESOURCE: '生成资源',
    ANSWER_QUIZ: '题库练习',
    START_QUIZ: '开始练习',
    COMPLETE_NODE: '完成学习节点',
    DELETE_RESOURCE: '资源管理'
  }
  return map[type] || type || '学习行为'
}

function actionIcon(type) {
  const map = {
    VIEW_RESOURCE: 'description',
    CONVERSATION: 'smart_toy',
    GENERATE_RESOURCE: 'auto_awesome',
    ANSWER_QUIZ: 'quiz',
    START_QUIZ: 'play_circle',
    COMPLETE_NODE: 'task_alt',
    DELETE_RESOURCE: 'folder_delete'
  }
  return map[type] || 'history'
}

function actionText(item) {
  if (item.source === 'quiz') {
    const score = item.score === undefined || item.score === null ? '已提交' : `得分 ${item.score}`
    const result = item.isCorrect === true ? '答对' : item.isCorrect === false ? '答错' : ''
    const question = item.questionId ? `题目 #${item.questionId}` : '客观题'
    return `完成${question}${result ? ` · ${result}` : ''} · ${score}`
  }
  const subject = activitySubject(item)
  return subject ? `${actionName(item.actionType || item.type)} · ${subject}` : actionName(item.actionType || item.type)
}

function activitySubject(item) {
  const metadata = parseMetadata(item.metadataJson)
  const subject = metadata.title || metadata.resourceTitle || metadata.topic || metadata.name || metadata.subject
  if (subject) return String(subject)
  if (item.targetId) return `${String(item.targetType || '').toLowerCase() === 'resource' ? '资源' : '对象'} #${item.targetId}`
  return ''
}

function parseMetadata(value) {
  if (!value) return {}
  if (typeof value === 'object' && !Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

function trendPointStyle(point) {
  const index = trendPoints.value.findIndex(item => item.date === point.date)
  const left = trendPointX(trendPoints.value, point, index)
  const top = 100 - Math.max(0, Math.min(100, point.rate))
  return { left: `${left}%`, top: `${top}%` }
}

function trendPointTitle(point) {
  return `${point.date}：${formatMetric(point.rate)}%，已评阅 ${point.assessed} 条作答，答对 ${point.correct} 条`
}

function trendPointX(points, point, index) {
  if (points.length <= 1) return 50
  const start = Date.parse(`${points[0].date}T00:00:00`)
  const end = Date.parse(`${points[points.length - 1].date}T00:00:00`)
  const current = Date.parse(`${point.date}T00:00:00`)
  if (!Number.isFinite(start) || !Number.isFinite(end) || !Number.isFinite(current) || end <= start) {
    return index / (points.length - 1) * 100
  }
  return Math.max(0, Math.min(100, (current - start) / (end - start) * 100))
}

function profileStatusLabel(status) {
  return {
    INSUFFICIENT_EVIDENCE: '样本不足',
    PENDING_CONFIRMATION: '待确认',
    PARTIALLY_CONFIRMED: '部分确认',
    CONFIRMED: '已确认',
    REJECTED: '已拒绝',
    VERIFIED: '已验证',
    RECORDED: '已记录'
  }[status] || '状态未知'
}

function profileStatusTone(status) {
  if (status === 'PENDING_CONFIRMATION' || status === 'INSUFFICIENT_EVIDENCE') return 'warning'
  if (status === 'CONFIRMED' || status === 'VERIFIED') return 'success'
  return ''
}

function runInsightAction() {
  if (reportInsight.value.route) {
    router.push(reportInsight.value.route)
    return
  }
  loadReport()
}

function createTrendPath(points) {
  if (!points.length) return ''
  return points.map((item, index) => {
    const x = trendPointX(points, item, index)
    const y = 100 - Math.max(0, Math.min(100, item.rate))
    return `${index ? 'L' : 'M'} ${x.toFixed(2)} ${y.toFixed(2)}`
  }).join(' ')
}

function formatMetric(value) {
  const number = Number(value || 0)
  if (!Number.isFinite(number)) return 0
  return Math.round(number * 10) / 10
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[char])
}

function downloadReport() {
  const summaryRows = statCards.value.map(item => `<tr><th>${escapeHtml(item.label)}</th><td>${escapeHtml(item.value)} ${escapeHtml(item.unit)}</td><td>${escapeHtml(item.note)}</td></tr>`).join('')
  const distributionRows = actionDistribution.value.map(item => `<li>${escapeHtml(item.name)}：${item.percent}%（${item.count} 次）</li>`).join('') || '<li>暂无可统计的学习行为</li>'
  const trendRows = trendPoints.value.map(item => `<li>${escapeHtml(item.date)}：${formatMetric(item.rate)}%，已评阅 ${item.assessed} 条作答，答对 ${item.correct} 条</li>`).join('') || '<li>暂无已评阅客观题作答趋势</li>'
  const activityRows = recentActivities.value.slice(0, 20).map(item => `<li>${escapeHtml(formatDate(item.time))} · ${escapeHtml(actionText(item))}</li>`).join('') || '<li>暂无近期活动</li>'
  const documentHtml = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>学习报告</title><style>body{font:14px/1.6 -apple-system,BlinkMacSystemFont,"Microsoft YaHei",sans-serif;color:#0d1c2e;max-width:860px;margin:48px auto;padding:0 24px}h1{font-size:28px}h2{margin-top:32px;font-size:20px}table{width:100%;border-collapse:collapse}th,td{padding:12px;border:1px solid #dce3f2;text-align:left}th{width:30%;background:#f4f7ff}li{margin:6px 0}.meta{color:#5d6472}.insight{padding:16px;border-left:4px solid #1f4b7a;background:#f4f7ff}.warning{color:#9a6700}@media print{body{margin:0}}</style></head><body><h1>学习报告</h1><p class="meta">统计范围：${escapeHtml(rangeLabel.value)} · 导出时间：${escapeHtml(formatDate(new Date()))}</p><div class="insight"><strong>${escapeHtml(reportInsight.value.title)}</strong><p>${escapeHtml(reportInsight.value.description)}</p></div><h2>学习概览</h2><table>${summaryRows}</table><h2>学习画像</h2><p>${escapeHtml(profileStatusText.value)}</p><h2>学习行为构成</h2><ul>${distributionRows}</ul><h2>正确率趋势</h2><ul>${trendRows}</ul><h2>近期活动</h2><ul>${activityRows}</ul>${reportSourceWarnings.value.length ? `<p class="warning">部分数据源未读取：${escapeHtml(reportSourceWarnings.value.join('、'))}</p>` : ''}</body></html>`
  const blob = new Blob([documentHtml], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `eduai-learning-report-${rangeDays.value}d.html`
  anchor.click()
  URL.revokeObjectURL(url)
}

async function loadReport() {
  loading.value = true
  reportError.value = ''
  showAllActivities.value = false
  detailLoaded.value = false
  detailedLoadError.value = ''
  profileDetailAvailable.value = false
  profileLoadError.value = ''
  try {
    const params = { rangeDays: rangeDays.value }
    const [summaryResult, detailResult, profileResult] = await Promise.allSettled([
      eduApi.reportSummary(params),
      eduApi.reportDetailed(params),
      eduApi.profileDetail()
    ])
    const summaryPayload = summaryResult.status === 'fulfilled' ? reportPayloadOf(summaryResult.value) : null
    const detailPayload = detailResult.status === 'fulfilled' ? detailedPayloadOf(detailResult.value) : null
    const profilePayload = profileResult.status === 'fulfilled' ? profileDetailPayloadOf(profileResult.value) : null
    const availableSummary = summaryPayload || detailPayload

    if (!availableSummary) {
      throw new Error(firstLoadError(summaryResult, detailResult))
    }

    summary.value = availableSummary
    detailed.value = detailPayload || {}
    profileDetail.value = profilePayload || {}
    detailLoaded.value = Boolean(detailPayload)
    profileDetailAvailable.value = Boolean(profilePayload)
    if (!detailPayload) {
      detailedLoadError.value = detailResult.status === 'rejected'
        ? detailResult.reason?.message || '近期活动数据暂时无法读取。'
        : '近期活动数据返回不完整，请刷新后重试。'
    }
    if (!profilePayload) {
      profileLoadError.value = profileResult.status === 'rejected'
        ? profileResult.reason?.message || '学习画像数据暂时无法读取。'
        : '学习画像数据返回不完整，请刷新后重试。'
    }
    const serverRangeDays = Number(summary.value.rangeDays)
    if (rangeOptions.some(item => item.value === serverRangeDays)) {
      rangeDays.value = serverRangeDays
    }
  } catch (error) {
    console.warn('学习报告加载失败', error)
    summary.value = {}
    detailed.value = {}
    profileDetail.value = {}
    reportError.value = error?.message || '请检查网络后重新加载。'
  } finally {
    loading.value = false
  }
}

function reportPayloadOf(response) {
  const payload = dataOf(response, null)
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return null
  const requiredKeys = [
    'rangeDays', 'behaviorAvailable', 'quizAvailable', 'todayActions', 'todayMinutes',
    'rangeStudyHours', 'rangeActions', 'consecutiveDays', 'dailyActivity',
    'actionDistribution', 'quizTrend'
  ]
  if (!requiredKeys.every(key => Object.prototype.hasOwnProperty.call(payload, key))) return null
  if (!Number.isFinite(Number(payload.rangeDays))) return null
  if (typeof payload.behaviorAvailable !== 'boolean' || typeof payload.quizAvailable !== 'boolean') return null
  if (!Array.isArray(payload.dailyActivity) || !Array.isArray(payload.actionDistribution) || !Array.isArray(payload.quizTrend)) return null
  return payload
}

function detailedPayloadOf(response) {
  const payload = reportPayloadOf(response)
  if (!payload) return null
  const requiredKeys = ['behaviorActivityAvailable', 'quizActivityAvailable', 'recentLogs', 'quizRecords']
  if (!requiredKeys.every(key => Object.prototype.hasOwnProperty.call(payload, key))) return null
  if (typeof payload.behaviorActivityAvailable !== 'boolean' || typeof payload.quizActivityAvailable !== 'boolean') return null
  if (!Array.isArray(payload.recentLogs) || !Array.isArray(payload.quizRecords)) return null
  return payload
}

function profileDetailPayloadOf(response) {
  const payload = dataOf(response, null)
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return null
  if (!payload.profileView || typeof payload.profileView !== 'object' || Array.isArray(payload.profileView)) return null
  const overview = payload.overview || payload.profileView.overview
  if (!overview || typeof overview !== 'object' || Array.isArray(overview)) return null
  return payload
}

function firstLoadError(summaryResult, detailResult) {
  const rejected = [summaryResult, detailResult].find(result => result.status === 'rejected')
  if (rejected?.reason?.message) return rejected.reason.message
  return '服务返回的学习报告数据不完整，请稍后重新加载。'
}

onMounted(loadReport)
</script>

<style scoped>
.report-eyebrow {
  display: block;
  margin-bottom: 4px;
  color: var(--primary);
  font-size: 13px;
  font-weight: 700;
  line-height: 18px;
}

.report-actions,
.report-range,
.heatmap-panel header,
.trend-panel header,
.distribution-panel header,
.activity-panel header,
.profile-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.report-actions {
  flex: 0 0 auto;
  flex-wrap: nowrap;
  justify-content: flex-end;
}

.report-actions button,
.report-range {
  white-space: nowrap;
}

.report-insight {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-xl);
  margin-bottom: var(--space-lg);
  padding: var(--space-lg) var(--space-xl);
  border: 1px solid color-mix(in srgb, var(--primary) 18%, var(--outline-variant));
  border-left: 4px solid var(--primary);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--primary-fixed) 34%, var(--surface-container-lowest));
}

.report-insight-copy {
  min-width: 0;
}

.report-insight-heading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.report-insight-heading h3 {
  margin: 0;
  color: var(--on-surface);
  font-size: 22px;
  line-height: 30px;
}

.report-insight-copy > p {
  max-width: 760px;
  margin: var(--space-xs) 0 0;
  color: var(--on-surface-variant);
  font-size: 14px;
  line-height: 22px;
}

.report-insight-actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-sm);
}

.report-insight-actions .primary-button,
.report-insight-actions .ghost-button {
  width: auto;
}

.report-range {
  color: var(--on-surface-variant);
  font-size: 13px;
  font-weight: 600;
}

.report-range .control {
  width: 116px;
}

.export-button {
  width: auto;
}

.report-grid,
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-lg);
  margin-bottom: var(--space-lg);
}

.stat-card {
  min-height: 156px;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: var(--space-lg);
  border: 1px solid var(--outline-variant);
  border-radius: var(--radius-lg);
  background: var(--surface-container-lowest);
  box-shadow: var(--shadow-soft);
}

.stat-card > div {
  display: flex;
  justify-content: space-between;
  color: var(--on-surface-variant);
  font-size: 14px;
  font-weight: 600;
}

.stat-card strong {
  color: var(--on-surface);
  font-size: 32px;
  line-height: 40px;
}

.stat-card small {
  margin-left: var(--space-xs);
  color: var(--on-surface-variant);
  font-size: 16px;
  font-weight: 400;
}

.stat-card p,
.report-data-note,
.heatmap-panel header p,
.trend-panel header p,
.distribution-panel header p,
.activity-panel header p {
  margin: 0;
  color: var(--on-surface-variant);
  font-size: 14px;
  line-height: 20px;
}

.stat-card p {
  margin-top: auto;
}

.report-data-note {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin: calc(-1 * var(--space-sm)) 0 var(--space-lg);
}

.report-source-warning {
  display: flex;
  align-items: flex-start;
  gap: var(--space-xs);
  margin: calc(-1 * var(--space-sm)) 0 var(--space-lg);
  color: var(--warning);
  font-size: 14px;
  line-height: 20px;
}

.report-source-warning .material-symbols-outlined {
  flex: 0 0 auto;
  font-size: 18px;
}

.report-data-note .material-symbols-outlined {
  color: var(--primary);
  font-size: 18px;
}

.report-layout {
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr);
  gap: var(--space-lg);
}

.left-column,
.right-column {
  min-width: 0;
  display: grid;
  align-content: start;
  gap: var(--space-lg);
}

.report-layout .panel {
  min-width: 0;
}

.heatmap-panel header,
.trend-panel header,
.distribution-panel header,
.activity-panel header {
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--space-md);
}

.heatmap-panel h3,
.trend-panel h3,
.distribution-panel h3,
.activity-panel h3 {
  margin: 0 0 4px;
  font-size: 20px;
  line-height: 28px;
}

.panel-total {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  background: var(--surface-container);
  color: var(--on-surface-variant);
  font-size: 12px;
  font-weight: 700;
}

.heatmap-scroll {
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
}

.heatmap-grid {
  min-width: 520px;
  display: grid;
  grid-template-columns: repeat(var(--heatmap-columns), minmax(0, 1fr));
  gap: var(--space-xs);
}

.weekday {
  color: var(--on-surface-variant);
  font-size: 11px;
  text-align: center;
}

.heat-cell {
  aspect-ratio: 1;
  border-radius: var(--radius-sm);
  background: var(--primary);
  cursor: help;
}

.heat-cell.blank {
  visibility: hidden;
}

.heat-legend {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: var(--space-xs);
  margin-top: var(--space-sm);
  color: var(--on-surface-variant);
  font-size: 11px;
}

.heat-legend i {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  background: var(--primary);
  opacity: 0.25;
}

.heat-legend .mid { opacity: 0.6; }
.heat-legend .high { opacity: 1; }

.heatmap-meta {
  display: flex;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
  color: var(--on-surface-variant);
  font-size: 12px;
}

.trend-chart-wrap {
  min-height: 240px;
}

.trend-chart-content {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: var(--space-sm);
}

.trend-y-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 0 0 1px;
  color: var(--on-surface-variant);
  font-size: 11px;
  line-height: 16px;
  text-align: right;
}

.trend-plot {
  position: relative;
  height: 220px;
  min-width: 0;
}

.trend-grid-lines,
.trend-chart {
  position: absolute;
  inset: 0;
}

.trend-grid-lines {
  pointer-events: none;
}

.trend-grid-lines span {
  position: absolute;
  right: 0;
  left: 0;
  border-top: 1px solid var(--outline-variant);
}

.trend-chart {
  width: 100%;
  height: 100%;
  overflow: visible;
}

.trend-point {
  position: absolute;
  z-index: 2;
  width: 8px;
  height: 8px;
  transform: translate(-50%, -50%);
  border: 2px solid var(--surface-container-lowest);
  border-radius: 50%;
  background: var(--secondary);
  box-shadow: 0 0 0 1px var(--secondary);
  cursor: help;
}

.trend-labels {
  display: flex;
  justify-content: space-between;
  margin-top: var(--space-xs);
  color: var(--on-surface-variant);
  font-size: 12px;
}

.trend-note {
  margin: var(--space-sm) 0 0;
  color: var(--on-surface-variant);
  font-size: 12px;
  line-height: 18px;
}

.distribution-list {
  display: grid;
  gap: var(--space-md);
  margin-top: var(--space-lg);
}

.distribution-item {
  display: grid;
  gap: var(--space-xs);
}

.distribution-item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-sm);
}

.distribution-item-head span {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.distribution-item-head i {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.distribution-item-head strong {
  color: var(--on-surface);
  font-size: 13px;
  white-space: nowrap;
}

.distribution-track {
  height: 8px;
  overflow: hidden;
  border-radius: 9999px;
  background: var(--surface-container);
}

.distribution-track span {
  display: block;
  height: 100%;
  min-width: 4px;
  border-radius: inherit;
}

.activity-panel header button {
  border: 0;
  background: transparent;
  color: var(--primary);
  font-weight: 700;
  cursor: pointer;
}

.activity-list {
  display: grid;
  gap: var(--space-sm);
  max-height: 460px;
  overflow-y: auto;
}

.activity-list article {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
}

.activity-icon {
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--secondary) 18%, var(--outline-variant));
  border-radius: 50%;
  background: color-mix(in srgb, var(--secondary-fixed) 46%, var(--surface-container-lowest));
  color: var(--secondary);
}

.activity-icon .material-symbols-outlined {
  font-size: 16px;
}

.activity-list p {
  margin: 0;
}

.activity-list small {
  color: var(--on-surface-variant);
}

.report-empty-block {
  min-height: 148px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: var(--space-sm);
  padding: var(--space-lg);
  border: 1px dashed var(--outline-variant);
  border-radius: var(--radius-md);
  color: var(--on-surface-variant);
  text-align: center;
}

.report-source-unavailable {
  min-height: 148px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: var(--space-sm);
  padding: var(--space-lg);
  border: 1px dashed color-mix(in srgb, var(--warning) 46%, var(--outline-variant));
  border-radius: var(--radius-md);
  color: var(--warning);
  text-align: center;
}

.report-source-unavailable.compact {
  min-height: 118px;
}

.report-source-unavailable .material-symbols-outlined {
  font-size: 30px;
}

.report-source-unavailable p {
  max-width: 460px;
  margin: 0;
  line-height: 22px;
}

.report-empty-block.compact {
  min-height: 118px;
}

.report-empty-block .material-symbols-outlined {
  color: var(--outline);
  font-size: 30px;
}

.report-empty-block p {
  max-width: 460px;
  margin: 0;
  line-height: 22px;
}

.single-trend-rate {
  color: var(--secondary);
  font-size: 28px;
  font-weight: 700;
  line-height: 36px;
}

@media (prefers-reduced-motion: reduce) {
  .report-page .spin {
    animation: none;
  }
}

@media (max-width: 1180px) {
  .report-page .page-title-row {
    align-items: stretch;
    flex-direction: column;
  }

  .report-actions {
    justify-content: flex-start;
  }

  .report-grid,
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .report-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .report-insight {
    align-items: stretch;
    flex-direction: column;
    gap: var(--space-lg);
    padding: var(--space-lg);
  }

  .report-insight-actions {
    justify-content: stretch;
  }

  .report-insight-actions .primary-button,
  .report-insight-actions .ghost-button {
    flex: 1 1 160px;
  }

  .report-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .report-range {
    justify-content: space-between;
  }

  .report-range .control {
    width: 150px;
  }

  .heatmap-meta {
    align-items: flex-start;
    flex-direction: column;
  }

  .report-grid,
  .stat-row {
    grid-template-columns: 1fr;
  }
}
</style>
