<template>
  <section class="bank-panel" aria-labelledby="bank-title">
    <header class="panel-header">
      <div>
        <p class="section-kicker">题库</p>
        <h1 id="bank-title">按需要选题，再开始本轮练习</h1>
        <p>题库只展示作答前需要的信息。答案和解析会在提交后才出现。</p>
      </div>
      <button class="ghost-button compact" type="button" @click="$emit('reset')">
        <span class="material-symbols-outlined" aria-hidden="true">restart_alt</span>
        清除筛选
      </button>
    </header>

    <form class="filter-bar" @submit.prevent="search">
      <label class="search-field" for="question-keyword">
        <span class="sr-only">搜索题目</span>
        <span class="material-symbols-outlined" aria-hidden="true">search</span>
        <input id="question-keyword" v-model.trim="draft.keyword" type="search" placeholder="搜索题干或知识点">
      </label>
      <label>
        <span>题型</span>
        <select v-model="draft.questionType">
          <option value="">全部题型</option>
          <option v-for="item in facets.questionTypes || []" :key="facetValue(item)" :value="facetValue(item)">{{ facetLabel(item) }}</option>
        </select>
      </label>
      <label>
        <span>难度</span>
        <select v-model="draft.difficulty">
          <option value="">全部难度</option>
          <option v-for="item in facets.difficulties || []" :key="facetValue(item)" :value="facetValue(item)">{{ facetLabel(item) }}</option>
        </select>
      </label>
      <label>
        <span>知识点</span>
        <select v-model="draft.knowledgePoint">
          <option value="">全部知识点</option>
          <option v-for="item in facets.knowledgePoints || []" :key="facetValue(item)" :value="facetValue(item)">{{ facetLabel(item) }}</option>
        </select>
      </label>
      <button class="primary-button filter-submit" type="submit" :disabled="loading">筛选题目</button>
    </form>

    <div v-if="loading" class="question-list loading-list" aria-busy="true" aria-live="polite">
      <span v-for="index in 5" :key="index" class="row-skeleton"></span>
    </div>

    <p v-else-if="error" class="panel-message error" role="alert">
      <span class="material-symbols-outlined" aria-hidden="true">error</span>{{ error }}
      <button type="button" @click="$emit('retry')">重试</button>
    </p>

    <div v-else-if="!rows.length" class="empty-state">
      <span class="material-symbols-outlined" aria-hidden="true">search_off</span>
      <h2>没有找到符合条件的题目</h2>
      <p>换一个筛选条件，或清除当前筛选后重新查看。</p>
      <button class="ghost-button compact" type="button" @click="$emit('reset')">清除筛选</button>
    </div>

    <template v-else>
      <div class="list-toolbar">
        <span>共 {{ total }} 题</span>
        <span>选择题目后即可创建一轮练习</span>
      </div>
      <div class="question-list" aria-label="题目列表">
        <article v-for="question in rows" :key="question.id" class="question-row" :class="{ selected: selectedIds.includes(question.id) }">
          <label class="row-selector">
            <input :checked="selectedIds.includes(question.id)" type="checkbox" :value="question.id" @change="$emit('toggle', question.id)">
            <span class="sr-only">选择这道题</span>
          </label>
          <div class="row-main">
            <div class="row-meta">
              <span>{{ typeLabel(question.questionType) }}</span>
              <span>{{ difficultyLabel(question.difficulty) }}</span>
              <span v-for="tag in tagsOf(question)" :key="tag">{{ tag }}</span>
            </div>
            <h2>{{ question.stem || '题干暂不可用' }}</h2>
          </div>
          <button class="row-add" type="button" :aria-pressed="selectedIds.includes(question.id)" @click="$emit('toggle', question.id)">
            <span class="material-symbols-outlined" aria-hidden="true">{{ selectedIds.includes(question.id) ? 'check_circle' : 'add_circle' }}</span>
            {{ selectedIds.includes(question.id) ? '已选' : '加入' }}
          </button>
        </article>
      </div>

      <nav v-if="total > pageSize" class="pagination" aria-label="题库分页">
        <button class="ghost-button compact" type="button" :disabled="pageNum <= 1" @click="$emit('page', pageNum - 1)">上一页</button>
        <span>第 {{ pageNum }} / {{ pageCount }} 页</span>
        <button class="ghost-button compact" type="button" :disabled="pageNum >= pageCount" @click="$emit('page', pageNum + 1)">下一页</button>
      </nav>
    </template>

    <aside v-if="selectedIds.length" class="selection-basket" aria-label="已选题目">
      <div>
        <strong>已选 {{ selectedIds.length }} 题</strong>
        <span>开始后会创建一轮可恢复的练习</span>
      </div>
      <p v-if="activeSession?.id || !canCreate" class="session-guard" :class="{ error: creationError }" role="status">
        <span v-if="activeSession?.id">已有未完成练习，可继续或开始新一轮。</span>
        <span v-else-if="creationChecking">正在确认练习状态。</span>
        <span v-else>{{ creationError || '当前不能创建练习。' }}</span>
        <button v-if="activeSession?.id" type="button" @click="$emit('resume-session')">继续练习</button>
        <button v-else-if="!creationChecking" type="button" @click="$emit('retry-session-check')">重试</button>
      </p>
      <div>
        <button class="text-action" type="button" @click="$emit('clear-selection')">清空</button>
        <button class="primary-button basket-start" type="button" :disabled="starting || (!canCreate && !activeSession?.id)" @click="requestStart" :aria-label="activeSession?.id ? '开始新一轮' : '开始练习'">
          {{ starting ? '正在创建' : activeSession?.id ? '开始新一轮' : '开始练习' }}
          <span class="material-symbols-outlined" aria-hidden="true">arrow_forward</span>
        </button>
      </div>
    </aside>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { safeJson } from '@/utils/format'

const props = defineProps({
  rows: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 12 },
  filters: { type: Object, default: () => ({}) },
  facets: { type: Object, default: () => ({}) },
  selectedIds: { type: Array, default: () => [] },
  loading: Boolean,
  starting: Boolean,
  canCreate: Boolean,
  activeSession: { type: Object, default: null },
  creationChecking: Boolean,
  creationError: { type: String, default: '' },
  error: { type: String, default: '' }
})

const emit = defineEmits(['search', 'reset', 'retry', 'toggle', 'clear-selection', 'start', 'new-round', 'page', 'retry-session-check', 'resume-session'])
const draft = ref({ ...props.filters })
const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

watch(() => props.filters, value => { draft.value = { ...value } }, { deep: true })

function search() { emit('search', { ...draft.value }) }
function requestStart() {
  const selected = [...props.selectedIds]
  emit(props.activeSession?.id ? 'new-round' : 'start', props.activeSession?.id ? { questionIds: selected, source: 'BANK' } : selected)
}
function facetValue(item) { return typeof item === 'object' ? String(item.value ?? item.code ?? item.name ?? '') : String(item) }
function facetLabel(item) { return typeof item === 'object' ? String(item.label ?? item.name ?? item.value ?? '') : String(item) }
function typeLabel(value) { return ({ single_choice: '单选题', multi_choice: '多选题', fill_blank: '填空题', short_answer: '简答题', code: '代码题' }[String(value || '').toLowerCase()] || '练习题') }
function difficultyLabel(value) { return ({ easy: '基础', medium: '进阶', hard: '挑战' }[String(value || '').toLowerCase()] || '未分级') }
function tagsOf(question) { const tags = safeJson(question.knowledgePoints, []); return Array.isArray(tags) ? tags.slice(0, 2) : [] }
</script>

<style scoped>
.bank-panel { width: min(100%, 1360px); display: grid; gap: 20px; }
.panel-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; }
.section-kicker { margin: 0 0 6px; color: var(--primary); font-size: 13px; line-height: 20px; font-weight: 800; }
.panel-header h1 { margin: 0; color: var(--on-surface); font-size: clamp(28px, 3vw, 40px); line-height: 1.2; letter-spacing: 0; }
.panel-header p:not(.section-kicker) { max-width: 660px; margin: 10px 0 0; color: var(--on-surface-variant); font-size: 15px; line-height: 24px; }
.compact { width: auto; min-height: 38px; padding: 0 12px; display: inline-flex; align-items: center; gap: 6px; white-space: nowrap; }
.compact .material-symbols-outlined { font-size: 18px; }
.filter-bar { display: grid; grid-template-columns: minmax(240px, 1.5fr) repeat(3, minmax(138px, .72fr)) max-content; align-items: end; gap: 10px; padding: 14px; border: 1px solid var(--outline-variant); border-radius: var(--radius-lg); background: var(--surface-container-lowest); }
.filter-bar label:not(.search-field) { display: grid; gap: 5px; min-width: 0; color: var(--on-surface-variant); font-size: 12px; line-height: 18px; font-weight: 700; }
.filter-bar select, .search-field input { width: 100%; min-height: 40px; box-sizing: border-box; border: 1px solid var(--outline-variant); border-radius: var(--radius-md); color: var(--on-surface); background: var(--surface-container-lowest); font: inherit; }
.filter-bar select { padding: 0 9px; }
.search-field { min-height: 40px; display: flex; align-items: center; gap: 7px; padding: 0 10px; border: 1px solid var(--outline-variant); border-radius: var(--radius-md); color: var(--on-surface-variant); background: var(--surface-container-lowest); }
.search-field:focus-within, .filter-bar select:focus { border-color: var(--primary); outline: 2px solid color-mix(in srgb, var(--primary) 18%, transparent); outline-offset: 1px; }
.search-field input { min-width: 0; padding: 0; border: 0; outline: 0; background: transparent; }
.search-field .material-symbols-outlined { font-size: 20px; }
.filter-submit { width: auto; min-width: 96px; padding: 0 14px; white-space: nowrap; }
.list-toolbar { display: flex; justify-content: space-between; gap: 14px; color: var(--on-surface-variant); font-size: 13px; line-height: 20px; }
.question-list { border: 1px solid var(--outline-variant); border-radius: var(--radius-lg); overflow: hidden; background: var(--surface-container-lowest); }
.question-row { display: grid; grid-template-columns: 44px minmax(0, 1fr) max-content; align-items: center; gap: 14px; min-height: 94px; padding: 14px 16px; border-bottom: 1px solid var(--surface-container); transition: background 160ms ease; }
.question-row:last-child { border-bottom: 0; }
.question-row.selected { background: var(--primary-fixed); }
.row-selector { width: 44px; height: 44px; display: grid; place-items: center; cursor: pointer; }
.row-selector input { width: 18px; height: 18px; accent-color: var(--primary); }
.row-main { min-width: 0; }
.row-meta { display: flex; flex-wrap: wrap; gap: 6px; }
.row-meta span { padding: 2px 7px; border-radius: 999px; color: var(--on-surface-variant); background: var(--surface-container); font-size: 12px; line-height: 18px; font-weight: 700; }
.row-main h2 { display: -webkit-box; margin: 8px 0 0; overflow: hidden; color: var(--on-surface); font-size: 15px; line-height: 23px; font-weight: 650; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.row-add { min-height: 40px; display: inline-flex; align-items: center; gap: 5px; border: 0; border-radius: var(--radius-md); padding: 0 10px; color: var(--primary); background: transparent; font: inherit; font-size: 13px; font-weight: 800; cursor: pointer; }
.row-add:hover, .row-add[aria-pressed='true'] { background: color-mix(in srgb, var(--primary) 10%, transparent); }
.row-add .material-symbols-outlined { font-size: 20px; }
.pagination { display: flex; justify-content: center; align-items: center; gap: 12px; padding: 4px 0 84px; color: var(--on-surface-variant); font-size: 13px; }
.selection-basket { position: sticky; bottom: 14px; z-index: 2; display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 14px 12px 18px; border: 1px solid color-mix(in srgb, var(--primary) 24%, var(--outline-variant)); border-radius: var(--radius-lg); background: color-mix(in srgb, var(--surface-container-lowest) 96%, var(--primary-fixed)); box-shadow: 0 12px 30px rgba(13, 28, 46, 0.14); }
.selection-basket strong, .selection-basket span { display: block; }
.selection-basket strong { color: var(--on-surface); font-size: 15px; line-height: 22px; }
.selection-basket span { margin-top: 2px; color: var(--on-surface-variant); font-size: 12px; line-height: 18px; }
.selection-basket > div:last-child { display: flex; align-items: center; gap: 8px; }
.text-action { border: 0; padding: 0 6px; color: var(--primary); background: transparent; font: inherit; font-size: 13px; font-weight: 800; cursor: pointer; }
.basket-start { width: auto; min-height: 44px; display: inline-flex; align-items: center; gap: 6px; padding: 0 14px; }
.panel-message, .empty-state { min-height: 220px; display: grid; place-items: center; align-content: center; gap: 8px; padding: 24px; border: 1px solid var(--outline-variant); border-radius: var(--radius-lg); color: var(--on-surface-variant); background: var(--surface-container-lowest); text-align: center; }
.panel-message.error { min-height: auto; display: flex; justify-content: center; color: var(--error); }
.panel-message button { border: 0; color: inherit; background: transparent; font: inherit; font-weight: 800; text-decoration: underline; cursor: pointer; }
.empty-state .material-symbols-outlined { color: var(--primary); font-size: 42px; }.empty-state h2 { margin: 4px 0 0; color: var(--on-surface); font-size: 18px; }.empty-state p { margin: 0; font-size: 14px; line-height: 22px; }
.loading-list { padding: 0; }.row-skeleton { height: 92px; border-bottom: 1px solid var(--surface-container); background: linear-gradient(90deg, var(--surface-container) 0%, var(--surface-container-high) 44%, var(--surface-container) 100%); background-size: 240% 100%; animation: row-shimmer 1.3s ease-in-out infinite; }.row-skeleton:last-child { border-bottom: 0; }
@keyframes row-shimmer { from { background-position: 100% 0; } to { background-position: -100% 0; } }
@media (max-width: 1040px) { .filter-bar { grid-template-columns: repeat(2, minmax(0, 1fr)); }.filter-submit { width: 100%; }.search-field { grid-column: 1 / -1; } }
@media (max-width: 760px) { .panel-header { align-items: flex-start; flex-direction: column; }.question-row { grid-template-columns: 44px minmax(0, 1fr); align-items: start; gap: 8px 12px; }.row-selector { margin-top: 3px; }.row-add { grid-column: 2; justify-self: start; }.row-main h2 { -webkit-line-clamp: 3; }.selection-basket { align-items: flex-start; flex-direction: column; }.selection-basket > div:last-child { width: 100%; justify-content: space-between; }.basket-start { flex: 1; justify-content: center; }.pagination { padding-bottom: 110px; } }
@media (max-width: 640px) { .filter-bar { grid-template-columns: 1fr; }.search-field { grid-column: auto; }.list-toolbar { align-items: flex-start; flex-direction: column; gap: 4px; }.selection-basket > div:last-child { gap: 12px; }.text-action { min-height: 40px; }.basket-start { min-width: 0; padding: 0 12px; } }
.session-guard { margin: 7px 0 0; color: var(--on-surface-variant); font-size: 12px; line-height: 18px; }.session-guard.error { color: var(--error); }.session-guard span { display: inline; }.session-guard button { min-height: 28px; border: 0; padding: 0 2px; color: inherit; background: transparent; font: inherit; font-weight: 800; text-decoration: underline; cursor: pointer; }
</style>
