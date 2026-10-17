<template>
  <section class="quiz-home" aria-labelledby="quiz-home-title">
    <h2 id="quiz-home-title" class="sr-only">测验首页</h2>

    <div v-if="loading" class="home-loading" aria-busy="true" aria-live="polite">
      <span class="home-skeleton primary-skeleton"></span>
      <div class="secondary-skeletons">
        <span class="home-skeleton"></span>
        <span class="home-skeleton"></span>
      </div>
    </div>

    <div v-else class="home-layout">
      <article v-if="activeSession" class="home-primary-card continue-session" aria-label="继续未完成练习">
        <div class="primary-content">
          <span class="action-icon material-symbols-outlined" aria-hidden="true">resume</span>
          <div>
            <p class="action-label">继续上次练习</p>
            <h3>继续完成这轮题目</h3>
            <ul class="session-facts" aria-label="本轮练习状态">
              <li>
                <span class="material-symbols-outlined" aria-hidden="true">folder_open</span>
                来源：{{ sourceLabel(activeSession.source) }}
              </li>
              <li>
                <span class="material-symbols-outlined" aria-hidden="true">format_list_numbered</span>
                当前第 {{ sessionPosition }} 题
              </li>
              <li>
                <span class="material-symbols-outlined" aria-hidden="true">task_alt</span>
                已提交 {{ submittedCount }} / {{ totalCount }} 题
              </li>
              <li v-if="startedText">
                <span class="material-symbols-outlined" aria-hidden="true">schedule</span>
                {{ startedText }}
              </li>
            </ul>
          </div>
        </div>
        <div class="home-primary-actions">
          <button class="primary-button home-primary" type="button" @click="$emit('resume')">
            继续练习
            <span class="material-symbols-outlined" aria-hidden="true">arrow_forward</span>
          </button>
          <button class="ghost-button home-primary" type="button" @click="$emit('new-round', { source: 'BANK' })">
            开始新一轮
            <span class="material-symbols-outlined" aria-hidden="true">restart_alt</span>
          </button>
        </div>
      </article>

      <article v-else class="home-primary-card quick-start-card">
        <div class="primary-content">
          <span class="action-icon material-symbols-outlined" aria-hidden="true">play_circle</span>
          <div>
            <p class="action-label">快速开始</p>
            <h3>开始一轮 10 题练习</h3>
            <p class="card-description">从当前题库创建一轮练习。作答过程会保存，完成后可以回顾需要巩固的题目。</p>
            <ul class="quick-start-facts" aria-label="本轮练习安排">
              <li><span class="material-symbols-outlined" aria-hidden="true">format_list_numbered</span>本轮 10 题</li>
              <li><span class="material-symbols-outlined" aria-hidden="true">save</span>作答自动保存</li>
              <li><span class="material-symbols-outlined" aria-hidden="true">flag</span>可标记稍后处理</li>
            </ul>
            <p v-if="bankTotal > 0" class="primary-stat"><strong>{{ bankTotal }}</strong> 题当前可练习</p>
            <p v-else-if="bankTotal === 0" class="inline-state">当前题库暂无可练习题</p>
            <p v-else-if="summary.bankError" class="inline-state error-state">题库数量暂时无法加载</p>
            <p v-else class="inline-state">题库数量暂时不可用</p>
          </div>
        </div>
        <button class="primary-button home-primary" type="button" :disabled="starting || bankTotal === 0 || Boolean(error)" @click="$emit('quick-start')">
          {{ starting ? '正在创建' : '开始练习' }}
          <span class="material-symbols-outlined" aria-hidden="true">arrow_forward</span>
        </button>
      </article>

      <div class="secondary-actions">
        <article class="home-secondary-card">
          <div class="secondary-heading">
            <span class="quiet-icon material-symbols-outlined" aria-hidden="true">library_books</span>
            <div>
              <p class="action-label">自行选题</p>
              <h3>从题库开始</h3>
            </div>
          </div>
          <p class="secondary-copy">按知识点、难度和题型筛选，再把想练的题加入本轮。</p>
          <p v-if="bankTotal > 0" class="secondary-stat"><strong>{{ bankTotal }}</strong><span>题可练习</span></p>
          <p v-else-if="bankTotal === 0" class="inline-state">当前题库暂无可练习题</p>
          <p v-else-if="summary.bankError" class="inline-state error-state">题库数量暂时无法加载</p>
          <p v-else class="inline-state">题库数量暂时不可用</p>
          <button class="text-action" type="button" @click="$emit('open-bank')">
            浏览题库 <span class="material-symbols-outlined" aria-hidden="true">arrow_forward</span>
          </button>
        </article>

        <article class="home-secondary-card">
          <div class="secondary-heading">
            <span class="quiet-icon material-symbols-outlined" aria-hidden="true">history_edu</span>
            <div>
              <p class="action-label">巩固复习</p>
              <h3>回顾错题</h3>
            </div>
          </div>
          <p class="secondary-copy">只显示最近一次作答仍需要巩固的题目，重新作答会更新掌握状态。</p>
          <p v-if="wrongTotal > 0" class="secondary-stat"><strong>{{ wrongTotal }}</strong><span>题待巩固</span></p>
          <p v-else-if="wrongTotal === 0" class="inline-state">暂时没有待巩固的错题</p>
          <p v-else-if="summary.wrongError" class="inline-state error-state">错题数量暂时无法加载</p>
          <p v-else class="inline-state">错题数量暂时不可用</p>
          <button class="text-action" type="button" @click="$emit('open-wrong')">
            查看错题 <span class="material-symbols-outlined" aria-hidden="true">arrow_forward</span>
          </button>
        </article>
      </div>

      <section class="learning-summary" aria-labelledby="learning-summary-title">
        <div class="summary-intro">
          <p class="action-label">累计练习</p>
          <h3 id="learning-summary-title">{{ hasStudyRecord ? '来自已提交记录的学习概览' : '暂无学习记录' }}</h3>
          <p v-if="hasStudyRecord">正确率仅统计已经自动判分的客观题。</p>
          <p v-else-if="summary.statsError" class="error-state">累计数据暂时无法加载</p>
          <p v-else>完成第一题后，会在这里显示可回顾的练习数据。</p>
        </div>

        <div class="summary-metrics" aria-label="累计练习数据">
          <div class="summary-metric">
            <span>客观题已答</span>
            <strong>{{ statsAvailable ? objectiveAnsweredCount : '--' }}</strong><small v-if="statsAvailable">题</small>
          </div>
          <div class="summary-metric">
            <span>客观题正确率</span>
            <strong>{{ statsAvailable && objectiveAnsweredCount > 0 && objectiveCorrectRate !== null ? `${objectiveCorrectRate}%` : '--' }}</strong>
          </div>
          <div class="summary-metric">
            <span>待评阅</span>
            <strong>{{ statsAvailable ? pendingReviewCount : '--' }}</strong><small v-if="statsAvailable">题</small>
          </div>
        </div>
      </section>
    </div>

    <p v-if="error" class="panel-error" role="alert">
      <span class="material-symbols-outlined" aria-hidden="true">error</span>
      {{ error }}
      <button type="button" @click="$emit('retry')">重试</button>
    </p>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  activeSession: { type: Object, default: null },
  summary: { type: Object, default: () => ({}) },
  loading: Boolean,
  starting: Boolean,
  error: { type: String, default: '' }
})

defineEmits(['resume', 'new-round', 'quick-start', 'open-bank', 'open-wrong', 'retry'])

const bankTotal = computed(() => validTotal(props.summary?.bankTotal))
const wrongTotal = computed(() => validTotal(props.summary?.wrongTotal))
const objectiveAnsweredCount = computed(() => validTotal(props.summary?.objectiveAnsweredCount) ?? 0)
const objectiveCorrectRate = computed(() => validRate(props.summary?.objectiveCorrectRate))
const pendingReviewCount = computed(() => validTotal(props.summary?.pendingReviewCount) ?? 0)
const statsAvailable = computed(() => !props.summary?.statsError && validTotal(props.summary?.objectiveAnsweredCount) !== null && validTotal(props.summary?.pendingReviewCount) !== null)
const hasStudyRecord = computed(() => statsAvailable.value && (objectiveAnsweredCount.value > 0 || pendingReviewCount.value > 0))
const totalCount = computed(() => validTotal(props.activeSession?.totalCount) ?? props.activeSession?.items?.length ?? 0)
const submittedCount = computed(() => Math.min(validTotal(props.activeSession?.submittedCount) ?? 0, totalCount.value))
const sessionPosition = computed(() => {
  const raw = Number(props.activeSession?.currentPosition)
  const position = Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 1
  return totalCount.value > 0 ? Math.min(position, totalCount.value) : position
})
const currentTime = ref(Date.now())
const startedText = computed(() => startedAtLabel(props.activeSession?.startedAt, currentTime.value))
let currentTimeTimer = 0

onMounted(() => {
  currentTimeTimer = window.setInterval(() => { currentTime.value = Date.now() }, 60000)
})

onBeforeUnmount(() => {
  window.clearInterval(currentTimeTimer)
})

function validTotal(value) {
  const total = Number(value)
  return Number.isFinite(total) && total >= 0 ? total : null
}

function validRate(value) {
  if (value === null || value === undefined || value === '') return null
  const rate = Number(value)
  return Number.isFinite(rate) && rate >= 0 && rate <= 100 ? rate : null
}

function sourceLabel(value) {
  return ({ BANK: '题库选题', WRONG: '错题复习', AI_CHAT: '对话题组' })[String(value || '').toUpperCase()] || '本轮练习'
}

function startedAtLabel(value, now) {
  if (!value) return ''
  const time = new Date(value).getTime()
  if (!Number.isFinite(time)) return ''
  const minutes = Math.max(0, Math.floor((now - time) / 60000))
  if (minutes < 1) return '刚刚开始'
  if (minutes < 60) return `开始于 ${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `开始于 ${hours} 小时前`
  const days = Math.floor(hours / 24)
  return `开始于 ${days} 天前`
}
</script>

<style scoped>
.quiz-home { width: min(100%, 1360px); display: grid; gap: 20px; }
.home-layout, .home-loading { display: grid; grid-template-columns: minmax(0, 1fr); gap: 20px; }
.home-primary-card, .home-secondary-card { border: 1px solid var(--outline-variant); border-radius: var(--radius-lg); background: var(--surface-container-lowest); box-shadow: var(--shadow-soft); }
.home-primary-card { min-height: 244px; display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 32px; padding: 30px 34px; border-color: color-mix(in srgb, var(--primary) 25%, var(--outline-variant)); }
.continue-session { background: linear-gradient(135deg, color-mix(in srgb, var(--primary-fixed) 62%, var(--surface-container-lowest)), var(--surface-container-lowest) 72%); }
.primary-content { display: flex; align-items: flex-start; gap: 16px; }
.action-icon, .quiet-icon { width: 42px; height: 42px; flex: 0 0 42px; display: grid; place-items: center; border-radius: 12px; font-size: 23px; }
.action-icon { color: var(--on-primary); background: var(--primary); }
.quiet-icon { color: var(--primary); background: var(--primary-fixed); }
.action-label { margin: 0 0 5px; color: var(--primary); font-size: 12px; font-weight: 800; line-height: 18px; }
.home-primary-card h3, .home-secondary-card h3 { margin: 0; color: var(--on-surface); font-size: 22px; line-height: 29px; }
.card-description, .secondary-copy { margin: 8px 0 0; color: var(--on-surface-variant); font-size: 14px; line-height: 22px; }
.card-description { max-width: 660px; }
.quick-start-facts { display: flex; flex-wrap: wrap; align-items: center; margin: 16px 0 0; padding: 0; color: var(--on-surface-variant); font-size: 13px; line-height: 20px; list-style: none; }
.quick-start-facts li { display: inline-flex; align-items: center; gap: 5px; }
.quick-start-facts li + li { margin-left: 16px; padding-left: 16px; border-left: 1px solid var(--outline-variant); }
.quick-start-facts .material-symbols-outlined { color: var(--primary); font-size: 17px; }
.session-facts { display: flex; flex-wrap: wrap; gap: 8px 16px; margin: 15px 0 0; padding: 0; color: var(--on-surface-variant); font-size: 13px; line-height: 20px; list-style: none; }
.session-facts li { display: inline-flex; align-items: center; gap: 5px; }
.session-facts .material-symbols-outlined { color: var(--primary); font-size: 17px; }
.primary-stat, .secondary-stat { display: inline-flex; align-items: baseline; gap: 5px; margin: 15px 0 0; color: var(--on-surface-variant); font-size: 13px; line-height: 20px; }
.primary-stat strong, .secondary-stat strong { color: var(--primary); font-size: 22px; line-height: 26px; }
.inline-state { margin: 15px 0 0; color: var(--on-surface-variant); font-size: 13px; line-height: 20px; }
.error-state { color: var(--error); }
.home-primary-actions { display: grid; align-self: center; gap: 8px; }
.home-primary { width: max-content; min-height: 44px; align-self: center; display: inline-flex; align-items: center; justify-content: center; gap: 8px; padding: 0 16px; box-shadow: 0 8px 18px rgba(30, 64, 175, .16); }
.home-primary.primary-button { background: var(--primary); }
.home-primary:active, .text-action:active { transform: translateY(1px); }
.secondary-actions, .secondary-skeletons { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
.home-secondary-card { min-height: 178px; display: flex; flex-direction: column; padding: 24px; }
.secondary-heading { display: flex; align-items: center; gap: 12px; }
.secondary-heading .action-label { margin-bottom: 2px; }
.home-secondary-card h3 { font-size: 17px; line-height: 23px; }
.secondary-copy { margin-top: 10px; }
.home-secondary-card .secondary-stat, .home-secondary-card .inline-state { margin-top: 10px; }
.home-secondary-card .secondary-stat strong { font-size: 19px; line-height: 22px; }
.text-action { width: max-content; min-height: 44px; display: inline-flex; align-items: center; gap: 4px; margin-top: auto; border: 0; padding: 0 2px; color: var(--primary); background: transparent; font: inherit; font-size: 14px; font-weight: 800; white-space: nowrap; cursor: pointer; }
.text-action:hover { text-decoration: underline; }
.text-action .material-symbols-outlined { font-size: 18px; }
.learning-summary { display: grid; grid-template-columns: minmax(0, .8fr) minmax(0, 1.2fr); align-items: center; gap: 32px; min-height: 118px; padding: 24px 34px; border-top: 1px solid var(--outline-variant); border-bottom: 1px solid var(--outline-variant); background: color-mix(in srgb, var(--surface-container-low) 74%, var(--surface-container-lowest)); }
.summary-intro h3, .summary-intro p { margin: 0; }
.summary-intro h3 { color: var(--on-surface); font-size: 18px; line-height: 25px; }
.summary-intro p:not(.action-label) { margin-top: 6px; color: var(--on-surface-variant); font-size: 13px; line-height: 20px; }
.summary-intro .error-state { color: var(--error); }
.summary-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); }
.summary-metric { min-width: 0; padding: 4px 24px; border-left: 1px solid var(--outline-variant); }
.summary-metric:first-child { border-left: 0; }
.summary-metric span, .summary-metric small { color: var(--on-surface-variant); font-size: 13px; line-height: 20px; }
.summary-metric span { display: block; margin-bottom: 5px; }
.summary-metric strong { color: var(--primary); font-size: 26px; line-height: 32px; }
.summary-metric small { margin-left: 4px; }
.home-loading { min-height: 244px; }
.home-skeleton { display: block; min-height: 178px; border: 1px solid var(--outline-variant); border-radius: var(--radius-lg); background: linear-gradient(90deg, var(--surface-container-low) 0%, var(--surface-container-high) 44%, var(--surface-container-low) 100%); background-size: 240% 100%; animation: home-shimmer 1.3s ease-in-out infinite; }
.primary-skeleton { min-height: 244px; }
@keyframes home-shimmer { from { background-position: 100% 0; } to { background-position: -100% 0; } }
.panel-error { display: inline-flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 0; color: var(--error); font-size: 14px; line-height: 20px; }
.panel-error .material-symbols-outlined { font-size: 19px; }
.panel-error button { border: 0; padding: 0; color: inherit; background: transparent; font: inherit; font-weight: 800; text-decoration: underline; cursor: pointer; }
.home-primary:focus-visible, .text-action:focus-visible, .panel-error button:focus-visible { outline: 2px solid var(--primary); outline-offset: 3px; }
@media (max-width: 860px) { .home-primary-card { grid-template-columns: 1fr; min-height: 0; gap: 20px; }.home-primary { align-self: start; }.secondary-actions, .secondary-skeletons { grid-template-columns: 1fr; }.home-secondary-card { min-height: 0; }.learning-summary { grid-template-columns: 1fr; gap: 20px; }.summary-metrics { max-width: 620px; }.primary-skeleton { min-height: 220px; } }
@media (max-width: 520px) { .quiz-home { gap: 16px; }.home-layout, .home-loading, .secondary-actions, .secondary-skeletons { gap: 16px; }.home-primary-card { padding: 20px; }.primary-content { gap: 12px; }.action-icon { width: 38px; height: 38px; flex-basis: 38px; font-size: 21px; }.home-primary-card h3 { font-size: 20px; line-height: 27px; }.session-facts { display: grid; gap: 7px; }.quick-start-facts { display: grid; gap: 7px; }.quick-start-facts li + li { margin-left: 0; padding-left: 0; border-left: 0; }.home-primary { width: 100%; justify-content: center; }.home-secondary-card { padding: 18px; }.home-secondary-card .text-action { margin-top: 10px; }.learning-summary { min-height: 0; padding: 20px; }.summary-metrics { grid-template-columns: 1fr; gap: 12px; }.summary-metric { padding: 0; border-left: 0; }.summary-metric span { display: inline; margin: 0 8px 0 0; }.summary-metric strong { font-size: 22px; line-height: 28px; } }
@media (prefers-reduced-motion: reduce) { .home-skeleton { animation: none; } }
</style>
