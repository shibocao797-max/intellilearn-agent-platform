<template>
  <section class="result-panel" aria-labelledby="result-title">
    <div v-if="loading" class="result-loading" aria-busy="true" aria-live="polite"><span></span><span></span></div>

    <div v-else-if="error" class="result-error" role="alert">
      <span class="material-symbols-outlined" aria-hidden="true">error</span>{{ error }}
      <button type="button" @click="$emit('retry')">重试</button>
    </div>

    <template v-else>
      <header class="result-hero">
        <div>
          <span class="result-icon material-symbols-outlined" aria-hidden="true">fact_check</span>
          <p class="section-kicker">本轮结果</p>
          <h1 id="result-title">{{ resultTitle }}</h1>
          <p>{{ resultSummary }}</p>
        </div>
      </header>

      <section class="result-facts" aria-label="本轮练习统计">
        <div><span>已提交</span><strong>{{ submittedCount }}</strong><small>/ {{ totalCount }} 题</small></div>
        <div v-if="hasObjectiveScore"><span>客观题答对</span><strong>{{ objectiveCorrectCount }}</strong><small>/ {{ objectiveSubmittedCount }} 题</small></div>
        <div v-if="hasObjectiveScore"><span>客观题正确率</span><strong>{{ objectiveCorrectRate }}%</strong></div>
        <div v-if="pendingReviewCount"><span>待评阅</span><strong>{{ pendingReviewCount }}</strong><small>题</small></div>
      </section>

      <section class="review-section" aria-labelledby="review-title">
        <header>
          <div>
            <p class="section-kicker">作答回顾</p>
            <h2 id="review-title">{{ wrongItems.length ? '待订正题目' : '本轮没有需要立即订正的客观题' }}</h2>
          </div>
          <button v-if="wrongItems.length" class="primary-button retry-button" type="button" :disabled="starting || (!canCreate && !activeSession?.id)" @click="requestWrongRetry" :aria-label="activeSession?.id ? '用这些错题开始新一轮' : '错题再练'">
            {{ starting ? '正在创建' : activeSession?.id ? '开始新一轮' : '错题再练' }}
            <span class="material-symbols-outlined" aria-hidden="true">restart_alt</span>
          </button>
        </header>

        <p v-if="wrongItems.length && (activeSession?.id || !canCreate)" class="session-guard" :class="{ error: creationError }" role="status">
          <span v-if="activeSession?.id">已有未完成练习，可继续或开始新一轮。</span>
          <span v-else-if="creationChecking">正在确认练习状态。</span>
          <span v-else>{{ creationError || '当前不能创建错题复习。' }}</span>
          <button v-if="activeSession?.id" type="button" @click="$emit('resume-session')">继续练习</button>
          <button v-else-if="!creationChecking" type="button" @click="$emit('retry-session-check')">重试</button>
        </p>
        <div v-if="wrongItems.length" class="review-list">
          <article v-for="item in wrongItems" :key="itemKey(item)" class="review-row">
            <span class="material-symbols-outlined" aria-hidden="true">priority_high</span>
            <div>
              <p>第 {{ questionPosition(item) }} 题</p>
              <h3>{{ questionStem(item) }}</h3>
            </div>
          </article>
        </div>
        <p v-else class="review-empty">简答和代码题如需评阅，会单独显示为待评阅，不计入客观题正确率。</p>
      </section>

      <footer class="result-actions">
        <button v-if="activeSession?.id" class="ghost-button result-action" type="button" @click="$emit('new-round', { source: 'BANK' })">
          <span class="material-symbols-outlined" aria-hidden="true">restart_alt</span>
          开始新一轮
        </button>
        <button class="ghost-button result-action" type="button" @click="$emit('open-bank')">
          <span class="material-symbols-outlined" aria-hidden="true">library_books</span>
          回到题库
        </button>
        <button class="primary-button result-action" type="button" @click="$emit('open-wrong')">
          <span class="material-symbols-outlined" aria-hidden="true">history_edu</span>
          查看错题复习
        </button>
      </footer>
    </template>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  result: { type: Object, default: () => ({}) },
  loading: Boolean,
  starting: Boolean,
  canCreate: Boolean,
  activeSession: { type: Object, default: null },
  creationChecking: Boolean,
  creationError: { type: String, default: '' },
  error: { type: String, default: '' }
})
const emit = defineEmits(['retry', 'retry-wrong', 'new-round', 'open-bank', 'open-wrong', 'retry-session-check', 'resume-session'])

const items = computed(() => [...(props.result?.items || [])].sort((a, b) => Number(a.position || 0) - Number(b.position || 0)))
const totalCount = computed(() => Number(props.result?.totalCount ?? items.value.length ?? 0))
const submittedCount = computed(() => Number(props.result?.submittedCount ?? items.value.filter(submitted).length ?? 0))
const objectiveSubmittedCount = computed(() => Number(props.result?.objectiveSubmittedCount ?? 0))
const objectiveCorrectCount = computed(() => Number(props.result?.objectiveCorrectCount ?? 0))
const objectiveCorrectRate = computed(() => Number(props.result?.objectiveCorrectRate ?? 0))
const pendingReviewCount = computed(() => Number(props.result?.pendingReviewCount ?? items.value.filter(item => item.pendingReview).length ?? 0))
const hasObjectiveScore = computed(() => Object.prototype.hasOwnProperty.call(props.result || {}, 'objectiveSubmittedCount'))
const wrongItems = computed(() => items.value.filter(item => item.isCorrect === false && !item.pendingReview))
const wrongIds = computed(() => wrongItems.value.map(itemKey).filter(Boolean))
function requestWrongRetry() {
  const ids = [...wrongIds.value]
  emit(props.activeSession?.id ? 'new-round' : 'retry-wrong', props.activeSession?.id ? { questionIds: ids, source: 'WRONG' } : ids)
}
const resultTitle = computed(() => submittedCount.value === totalCount.value && totalCount.value > 0 ? '本轮练习已完成' : '本轮练习已保存')
const resultSummary = computed(() => submittedCount.value === totalCount.value && totalCount.value > 0 ? '可以先订正错题，也可以回题库开始下一轮。' : `已提交 ${submittedCount.value} 题，未提交的题目没有被当作完成。`)
function submitted(item) { return Boolean(item.submittedAt || item.submittedAnswer || item.state === 'SUBMITTED') }
function itemKey(item) { return String(item?.questionId ?? item?.question?.id ?? item?.id ?? '') }
function questionPosition(item) { return Number(item.position || 1) }
function questionStem(item) { return item?.question?.stem || item?.stem || '题干暂不可用' }
</script>

<style scoped>
.result-panel { display: grid; gap: 28px; max-width: 960px; margin: 0 auto; padding: 22px 0 36px; }.result-hero { max-width: 760px; padding: 8px 0 4px; display: flex; align-items: center; justify-content: space-between; gap: 22px; }.result-hero > div { min-width: 0; }.result-icon { width: 48px; height: 48px; display: grid; place-items: center; margin-bottom: 16px; border-radius: 12px; color: var(--primary); background: var(--primary-fixed); font-size: 27px; }.section-kicker { margin: 0 0 6px; color: var(--primary); font-size: 13px; line-height: 20px; font-weight: 800; }.result-hero h1 { margin: 0; color: var(--on-surface); font-size: clamp(30px, 4vw, 44px); line-height: 1.16; letter-spacing: 0; }.result-hero > div > p:last-child { margin: 14px 0 0; color: var(--on-surface-variant); font-size: 16px; line-height: 25px; }.result-facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); border: 1px solid var(--outline-variant); border-radius: var(--radius-lg); background: var(--surface-container-lowest); overflow: hidden; }.result-facts > div { min-height: 106px; display: grid; align-content: center; gap: 1px; padding: 16px 20px; border-right: 1px solid var(--surface-container); }.result-facts > div:last-child { border-right: 0; }.result-facts span, .result-facts small { color: var(--on-surface-variant); font-size: 12px; line-height: 18px; }.result-facts strong { color: var(--on-surface); font-size: 30px; line-height: 38px; }.review-section { display: grid; gap: 16px; }.review-section > header { display: flex; align-items: end; justify-content: space-between; gap: 16px; }.review-section h2 { margin: 0; color: var(--on-surface); font-size: 22px; line-height: 29px; }.retry-button { width: auto; min-height: 44px; display: inline-flex; align-items: center; gap: 6px; padding: 0 15px; }.review-list { border: 1px solid var(--outline-variant); border-radius: var(--radius-lg); overflow: hidden; background: var(--surface-container-lowest); }.review-row { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 12px; padding: 15px 18px; border-bottom: 1px solid var(--surface-container); }.review-row:last-child { border-bottom: 0; }.review-row > .material-symbols-outlined { color: var(--error); font-size: 22px; }.review-row p, .review-row h3 { margin: 0; }.review-row p { color: var(--on-surface-variant); font-size: 12px; line-height: 18px; font-weight: 700; }.review-row h3 { display: -webkit-box; overflow: hidden; margin-top: 3px; color: var(--on-surface); font-size: 15px; line-height: 22px; font-weight: 650; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.review-empty { margin: 0; padding: 20px; border: 1px dashed var(--outline-variant); border-radius: var(--radius-lg); color: var(--on-surface-variant); font-size: 14px; line-height: 22px; }.result-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 10px; padding-top: 2px; }.result-action { width: auto; display: inline-flex; align-items: center; gap: 6px; padding: 0 14px; }.result-action .material-symbols-outlined { font-size: 19px; }.result-error { display: flex; align-items: center; gap: 8px; min-height: 180px; justify-content: center; color: var(--error); }.result-error button { border: 0; padding: 0; color: inherit; font: inherit; font-weight: 800; text-decoration: underline; cursor: pointer; }.result-loading { display: grid; gap: 18px; }.result-loading span { min-height: 180px; border-radius: var(--radius-lg); background: linear-gradient(90deg, var(--surface-container) 0%, var(--surface-container-high) 44%, var(--surface-container) 100%); background-size: 240% 100%; animation: shimmer 1.3s ease-in-out infinite; }.result-loading span:last-child { min-height: 250px; }@keyframes shimmer { from { background-position: 100% 0; } to { background-position: -100% 0; } }
@media (max-width: 640px) { .result-hero { align-items: flex-start; }.result-facts > div { min-height: 88px; border-right: 0; border-bottom: 1px solid var(--surface-container); }.result-facts > div:last-child { border-bottom: 0; }.review-section > header { align-items: flex-start; flex-direction: column; }.result-actions { display: grid; grid-template-columns: 1fr; }.result-action { width: 100%; justify-content: center; }.retry-button { width: 100%; justify-content: center; } }
.session-guard { margin: 0; color: var(--on-surface-variant); font-size: 13px; line-height: 20px; }.session-guard.error { color: var(--error); }.session-guard button { min-height: 28px; border: 0; padding: 0 2px; color: inherit; background: transparent; font: inherit; font-weight: 800; text-decoration: underline; cursor: pointer; }
</style>
