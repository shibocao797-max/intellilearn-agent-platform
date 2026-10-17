<template>
  <section class="wrong-panel" aria-labelledby="wrong-title">
    <header class="panel-header">
      <div>
        <p class="section-kicker">错题复习</p>
        <h1 id="wrong-title">把还没掌握的题，再做一遍。</h1>
        <p>这里只保留每道题最近一次仍答错的记录。重新作答正确后，它会离开待巩固列表。</p>
      </div>
      <button class="ghost-button compact" type="button" :disabled="loading" @click="$emit('refresh')">
        <span class="material-symbols-outlined" aria-hidden="true">refresh</span>
        刷新
      </button>
    </header>

    <div v-if="loading" class="wrong-list loading-list" aria-busy="true" aria-live="polite">
      <span v-for="index in 5" :key="index" class="row-skeleton"></span>
    </div>

    <p v-else-if="error" class="panel-message error" role="alert">
      <span class="material-symbols-outlined" aria-hidden="true">error</span>{{ error }}
      <button type="button" @click="$emit('refresh')">重试</button>
    </p>

    <div v-else-if="!rows.length" class="empty-state">
      <span class="material-symbols-outlined" aria-hidden="true">task_alt</span>
      <h2>当前没有待巩固的错题</h2>
      <p>完成一轮练习后，仍答错的题目会在这里出现。</p>
      <button class="primary-button empty-action" type="button" @click="$emit('open-bank')">去题库练习</button>
    </div>

    <template v-else>
      <div class="list-toolbar">
        <span>共 {{ total }} 道待巩固题</span>
        <span>可选择部分题目创建复习</span>
      </div>
      <div class="wrong-list" aria-label="待巩固错题">
        <article v-for="question in rows" :key="question.id" class="wrong-row" :class="{ selected: selectedIds.includes(question.id) }">
          <label class="row-selector">
            <input :checked="selectedIds.includes(question.id)" type="checkbox" :value="question.id" @change="$emit('toggle', question.id)">
            <span class="sr-only">选择这道错题</span>
          </label>
          <div class="row-main">
            <div class="row-meta">
              <span>{{ typeLabel(question.questionType) }}</span>
              <span v-if="question.wrongCount">已错 {{ question.wrongCount }} 次</span>
              <span v-if="question.lastWrongAt">最近作答 {{ dateLabel(question.lastWrongAt) }}</span>
            </div>
            <h2>{{ question.stem || '题干暂不可用' }}</h2>
          </div>
          <button class="row-add" type="button" :aria-pressed="selectedIds.includes(question.id)" @click="$emit('toggle', question.id)">
            <span class="material-symbols-outlined" aria-hidden="true">{{ selectedIds.includes(question.id) ? 'check_circle' : 'add_circle' }}</span>
            {{ selectedIds.includes(question.id) ? '已选' : '加入复习' }}
          </button>
        </article>
      </div>

      <nav v-if="total > pageSize" class="pagination" aria-label="错题分页">
        <button class="ghost-button compact" type="button" :disabled="pageNum <= 1" @click="$emit('page', pageNum - 1)">上一页</button>
        <span>第 {{ pageNum }} / {{ pageCount }} 页</span>
        <button class="ghost-button compact" type="button" :disabled="pageNum >= pageCount" @click="$emit('page', pageNum + 1)">下一页</button>
      </nav>
    </template>

    <aside v-if="selectedIds.length" class="selection-basket" aria-label="已选错题">
      <div>
        <strong>已选 {{ selectedIds.length }} 道错题</strong>
        <span>创建一轮复习后，可随时保存并返回</span>
      </div>
      <p v-if="activeSession?.id || !canCreate" class="session-guard" :class="{ error: creationError }" role="status">
        <span v-if="activeSession?.id">已有未完成练习，可继续或开始新一轮。</span>
        <span v-else-if="creationChecking">正在确认练习状态。</span>
        <span v-else>{{ creationError || '当前不能创建复习。' }}</span>
        <button v-if="activeSession?.id" type="button" @click="$emit('resume-session')">继续练习</button>
        <button v-else-if="!creationChecking" type="button" @click="$emit('retry-session-check')">重试</button>
      </p>
      <div>
        <button class="text-action" type="button" @click="$emit('clear-selection')">清空</button>
        <button class="primary-button basket-start" type="button" :disabled="starting || (!canCreate && !activeSession?.id)" @click="requestStart" :aria-label="activeSession?.id ? '开始新一轮' : '开始复习'">
          {{ starting ? '正在创建' : activeSession?.id ? '开始新一轮' : '开始复习' }}
          <span class="material-symbols-outlined" aria-hidden="true">arrow_forward</span>
        </button>
      </div>
    </aside>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  rows: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 12 },
  selectedIds: { type: Array, default: () => [] },
  loading: Boolean,
  starting: Boolean,
  canCreate: Boolean,
  activeSession: { type: Object, default: null },
  creationChecking: Boolean,
  creationError: { type: String, default: '' },
  error: { type: String, default: '' }
})

const emit = defineEmits(['refresh', 'toggle', 'clear-selection', 'start', 'new-round', 'page', 'open-bank', 'retry-session-check', 'resume-session'])
const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
function requestStart() {
  const selected = [...props.selectedIds]
  emit(props.activeSession?.id ? 'new-round' : 'start', props.activeSession?.id ? { questionIds: selected, source: 'WRONG' } : selected)
}
function typeLabel(value) { return ({ single_choice: '单选题', multi_choice: '多选题', fill_blank: '填空题', short_answer: '简答题', code: '代码题' }[String(value || '').toLowerCase()] || '练习题') }
function dateLabel(value) { const date = new Date(value); return Number.isNaN(date.getTime()) ? String(value) : `${date.getMonth() + 1}/${date.getDate()}` }
</script>

<style scoped>
.wrong-panel { display: grid; gap: 20px; max-width: 1120px; }.panel-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; }.section-kicker { margin: 0 0 6px; color: var(--primary); font-size: 13px; line-height: 20px; font-weight: 800; }.panel-header h1 { margin: 0; color: var(--on-surface); font-size: clamp(28px, 3vw, 40px); line-height: 1.2; letter-spacing: 0; }.panel-header p:not(.section-kicker) { max-width: 650px; margin: 10px 0 0; color: var(--on-surface-variant); font-size: 15px; line-height: 24px; }.compact { width: auto; min-height: 38px; display: inline-flex; align-items: center; gap: 6px; padding: 0 12px; white-space: nowrap; }.compact .material-symbols-outlined { font-size: 18px; }
.list-toolbar { display: flex; justify-content: space-between; gap: 14px; color: var(--on-surface-variant); font-size: 13px; line-height: 20px; }.wrong-list { border: 1px solid var(--outline-variant); border-radius: var(--radius-lg); overflow: hidden; background: var(--surface-container-lowest); }.wrong-row { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 14px; min-height: 94px; padding: 14px 16px; border-bottom: 1px solid var(--surface-container); transition: background 160ms ease; }.wrong-row:last-child { border-bottom: 0; }.wrong-row.selected { background: var(--primary-fixed); }.row-selector { width: 28px; height: 44px; display: grid; place-items: center; cursor: pointer; }.row-selector input { width: 18px; height: 18px; accent-color: var(--primary); }.row-main { min-width: 0; }.row-meta { display: flex; flex-wrap: wrap; gap: 6px; }.row-meta span { padding: 2px 7px; border-radius: 999px; color: var(--on-surface-variant); background: var(--surface-container); font-size: 12px; line-height: 18px; font-weight: 700; }.row-main h2 { display: -webkit-box; margin: 8px 0 0; overflow: hidden; color: var(--on-surface); font-size: 15px; line-height: 23px; font-weight: 650; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.row-add { min-height: 40px; display: inline-flex; align-items: center; gap: 5px; border: 0; border-radius: var(--radius-md); padding: 0 10px; color: var(--primary); background: transparent; font: inherit; font-size: 13px; font-weight: 800; cursor: pointer; }.row-add:hover, .row-add[aria-pressed='true'] { background: color-mix(in srgb, var(--primary) 10%, transparent); }.row-add .material-symbols-outlined { font-size: 20px; }
.pagination { display: flex; justify-content: center; align-items: center; gap: 12px; padding: 4px 0 84px; color: var(--on-surface-variant); font-size: 13px; }.selection-basket { position: sticky; bottom: 14px; z-index: 2; display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 14px 12px 18px; border: 1px solid color-mix(in srgb, var(--primary) 24%, var(--outline-variant)); border-radius: var(--radius-lg); background: color-mix(in srgb, var(--surface-container-lowest) 96%, var(--primary-fixed)); box-shadow: 0 12px 30px rgba(13, 28, 46, 0.14); }.selection-basket strong, .selection-basket span { display: block; }.selection-basket strong { color: var(--on-surface); font-size: 15px; line-height: 22px; }.selection-basket span { margin-top: 2px; color: var(--on-surface-variant); font-size: 12px; line-height: 18px; }.selection-basket > div:last-child { display: flex; align-items: center; gap: 8px; }.text-action { border: 0; padding: 0 6px; color: var(--primary); background: transparent; font: inherit; font-size: 13px; font-weight: 800; cursor: pointer; }.basket-start { width: auto; min-height: 44px; display: inline-flex; align-items: center; gap: 6px; padding: 0 14px; }
.panel-message, .empty-state { min-height: 220px; display: grid; place-items: center; align-content: center; gap: 8px; padding: 24px; border: 1px solid var(--outline-variant); border-radius: var(--radius-lg); color: var(--on-surface-variant); background: var(--surface-container-lowest); text-align: center; }.panel-message.error { min-height: auto; display: flex; justify-content: center; color: var(--error); }.panel-message button { border: 0; color: inherit; background: transparent; font: inherit; font-weight: 800; text-decoration: underline; cursor: pointer; }.empty-state .material-symbols-outlined { color: var(--success); font-size: 42px; }.empty-state h2 { margin: 4px 0 0; color: var(--on-surface); font-size: 18px; }.empty-state p { margin: 0; font-size: 14px; line-height: 22px; }.empty-action { width: auto; min-height: 40px; margin-top: 6px; padding: 0 14px; }.loading-list { padding: 0; }.row-skeleton { height: 92px; border-bottom: 1px solid var(--surface-container); background: linear-gradient(90deg, var(--surface-container) 0%, var(--surface-container-high) 44%, var(--surface-container) 100%); background-size: 240% 100%; animation: row-shimmer 1.3s ease-in-out infinite; }.row-skeleton:last-child { border-bottom: 0; }@keyframes row-shimmer { from { background-position: 100% 0; } to { background-position: -100% 0; } }
@media (max-width: 640px) { .panel-header { align-items: flex-start; flex-direction: column; }.wrong-row { grid-template-columns: auto minmax(0, 1fr); gap: 8px 12px; }.row-add { grid-column: 2; justify-self: start; }.selection-basket { align-items: flex-start; flex-direction: column; }.selection-basket > div:last-child { width: 100%; justify-content: space-between; }.basket-start { flex: 1; justify-content: center; }.pagination { padding-bottom: 110px; } }
.session-guard { margin: 7px 0 0; color: var(--on-surface-variant); font-size: 12px; line-height: 18px; }.session-guard.error { color: var(--error); }.session-guard span { display: inline; }.session-guard button { min-height: 28px; border: 0; padding: 0 2px; color: inherit; background: transparent; font: inherit; font-weight: 800; text-decoration: underline; cursor: pointer; }
</style>
