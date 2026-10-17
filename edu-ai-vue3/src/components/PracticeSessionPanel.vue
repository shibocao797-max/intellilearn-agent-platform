<template>
  <section class="practice-session" aria-label="正在练习">
    <header class="practice-header">
      <button class="back-button" type="button" @click="$emit('leave')">
        <span class="material-symbols-outlined" aria-hidden="true">arrow_back</span>
        返回测验
      </button>
      <div class="session-context">
        <span>本轮练习</span>
        <strong>{{ submittedCount }} / {{ items.length }} 题已提交</strong>
      </div>
      <time :datetime="`PT${elapsedSeconds}S`" :title="'本次打开后的用时'">
        <span class="material-symbols-outlined" aria-hidden="true">timer</span>{{ elapsedLabel }}
      </time>
      <button class="end-button" type="button" :disabled="finishing" @click="$emit('finish')">
        {{ finishing ? '正在结束' : '结束练习' }}
      </button>
    </header>

    <div v-if="loading" class="practice-skeleton" aria-busy="true" aria-live="polite">
      <span></span><span></span>
    </div>

    <div v-else-if="!items.length" class="practice-empty">
      <span class="material-symbols-outlined" aria-hidden="true">error</span>
      <h1>这轮练习没有可用题目</h1>
      <p>题目可能已被移除。返回测验后重新选择题目。</p>
      <button class="primary-button" type="button" @click="$emit('leave')">返回测验</button>
    </div>

    <div v-else class="practice-layout">
      <main class="question-workspace" aria-live="polite">
        <article class="question-sheet" :key="currentKey" aria-labelledby="practice-question-title">
          <header class="question-heading">
            <div class="question-badges">
              <span>题目 {{ currentIndex + 1 }}</span>
              <span>{{ typeLabel(question.questionType) }}</span>
              <span v-if="question.difficulty">{{ difficultyLabel(question.difficulty) }}</span>
              <span v-for="tag in knowledgeTags(question)" :key="tag">{{ tag }}</span>
            </div>
            <h1 id="practice-question-title">{{ question.stem || '题干暂不可用' }}</h1>
          </header>

          <fieldset v-if="isChoiceQuestion" class="answer-fieldset" :disabled="isSubmitted || isBusy">
            <legend>选择你的答案</legend>
            <label v-for="option in options" :key="option.key" class="option-row" :class="optionState(option)">
              <input v-if="isMultiChoice" v-model="selectedMulti" type="checkbox" :value="option.key">
              <input v-else v-model="selectedSingle" type="radio" :name="`quiz-${currentKey}`" :value="option.key">
              <span class="option-key" aria-hidden="true">{{ option.key }}</span>
              <span>{{ option.text }}</span>
              <span v-if="isSubmitted && isCorrectOption(option)" class="option-mark correct material-symbols-outlined" aria-label="正确答案">check_circle</span>
              <span v-else-if="isSubmitted && optionWasSelected(option) && feedback?.isCorrect === false" class="option-mark wrong material-symbols-outlined" aria-label="你的答案">cancel</span>
            </label>
          </fieldset>

          <div v-else class="free-answer">
            <label for="practice-free-answer">{{ isCodeQuestion ? '代码作答' : '你的答案' }}</label>
            <textarea
              id="practice-free-answer"
              v-model="selectedSingle"
              class="answer-input"
              :class="{ code: isCodeQuestion }"
              :disabled="isSubmitted || isBusy"
              :placeholder="answerPlaceholder"
              spellcheck="false"
            ></textarea>
          </div>

          <p v-if="error" class="action-error" role="alert">
            <span class="material-symbols-outlined" aria-hidden="true">error</span>{{ error }}
          </p>

          <section v-if="isSubmitted" class="feedback-panel" :class="feedbackState" aria-labelledby="feedback-title">
            <header>
              <span class="material-symbols-outlined" aria-hidden="true">{{ feedbackIcon }}</span>
              <div>
                <h2 id="feedback-title">{{ feedbackTitle }}</h2>
                <p>{{ feedbackSummary }}</p>
              </div>
            </header>
            <template v-if="feedback && !feedback.pendingReview">
              <p v-if="feedback.correctAnswer" class="correct-answer"><strong>参考答案</strong>{{ feedback.correctAnswer }}</p>
              <p v-if="feedback.explanation" class="explanation">{{ feedback.explanation }}</p>
              <button v-if="!feedback.explanation" class="explain-button" type="button" :disabled="isBusy" @click="$emit('explain', currentItem)">
                <span class="material-symbols-outlined" aria-hidden="true">lightbulb</span>
                请求讲解
              </button>
            </template>
          </section>

          <footer class="answer-dock">
            <button class="flag-button" type="button" :disabled="isSubmitted || isBusy" :aria-pressed="Boolean(currentItem.flagged)" @click="toggleFlag">
              <span class="material-symbols-outlined" aria-hidden="true">{{ currentItem.flagged ? 'bookmark_added' : 'bookmark' }}</span>
              {{ currentItem.flagged ? '已标记稍后处理' : '标记稍后处理' }}
            </button>
            <div class="dock-actions">
              <button v-if="!isSubmitted" class="ghost-button clear-button" type="button" :disabled="!hasAnswer || isBusy" @click="clearAnswer">清空</button>
              <button v-if="!isSubmitted" class="primary-button submit-button" type="button" :disabled="!hasAnswer || isBusy" @click="submitCurrent">
                <span v-if="isBusy" class="material-symbols-outlined spin" aria-hidden="true">progress_activity</span>
                {{ isBusy ? '正在提交' : '提交答案' }}
              </button>
              <button v-else class="primary-button submit-button" type="button" @click="nextQuestion">
                {{ nextLabel }}<span class="material-symbols-outlined" aria-hidden="true">arrow_forward</span>
              </button>
            </div>
          </footer>
        </article>
      </main>

      <aside class="question-nav" aria-label="本轮题号导航">
        <div class="nav-title"><span>本轮练习</span><strong>题号</strong></div>
        <div class="number-grid">
          <button v-for="(item, index) in items" :key="itemKey(item)" class="number-button" :class="navState(item, index)" type="button" :aria-current="index === currentIndex ? 'step' : undefined" :aria-label="navLabel(item, index)" @click="goTo(index)">{{ index + 1 }}</button>
        </div>
        <div class="nav-key">
          <span><i class="key-current"></i>当前</span><span><i class="key-done"></i>已提交</span><span><i class="key-flag"></i>稍后处理</span>
        </div>
      </aside>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { safeJson } from '@/utils/format'

const props = defineProps({
  session: { type: Object, default: () => ({}) },
  feedbackByQuestion: { type: Object, default: () => ({}) },
  busyQuestionIds: { type: Array, default: () => [] },
  loading: Boolean,
  finishing: Boolean,
  error: { type: String, default: '' }
})
const emit = defineEmits(['leave', 'finish', 'save-item', 'update-position', 'submit-item', 'explain'])
const currentIndex = ref(0)
const drafts = ref({})
const openedAt = ref(Date.now())
const now = ref(Date.now())
const saveTimers = new Map()
let clockTimer

const items = computed(() => [...(props.session?.items || [])].sort((a, b) => Number(a.position || 0) - Number(b.position || 0)))
const currentItem = computed(() => items.value[currentIndex.value] || {})
const question = computed(() => currentItem.value.question || currentItem.value)
const currentKey = computed(() => itemKey(currentItem.value))
const feedback = computed(() => props.feedbackByQuestion[currentKey.value] || currentItem.value.feedback || persistedFeedback(currentItem.value))
const isSubmitted = computed(() => isItemSubmitted(currentItem.value))
const isBusy = computed(() => props.busyQuestionIds.map(String).includes(currentKey.value))
const normalizedType = computed(() => String(question.value.questionType || '').toLowerCase())
const isMultiChoice = computed(() => normalizedType.value === 'multi_choice')
const isChoiceQuestion = computed(() => ['single_choice', 'multi_choice'].includes(normalizedType.value) && options.value.length > 0)
const isCodeQuestion = computed(() => normalizedType.value === 'code')
const options = computed(() => normalizeOptions(question.value.options))
const rawAnswer = computed(() => drafts.value[currentKey.value] ?? currentItem.value.draftAnswer ?? currentItem.value.submittedAnswer ?? '')
const selectedSingle = computed({ get: () => (Array.isArray(rawAnswer.value) ? '' : String(rawAnswer.value)), set: value => updateAnswer(value) })
const selectedMulti = computed({ get: () => splitMulti(rawAnswer.value), set: value => updateAnswer([...value].sort().join(',')) })
const hasAnswer = computed(() => Boolean(String(rawAnswer.value).trim()))
const submittedCount = computed(() => items.value.filter(isItemSubmitted).length)
const elapsedSeconds = computed(() => Math.max(0, Math.floor((now.value - openedAt.value) / 1000)))
const elapsedLabel = computed(() => formatDuration(elapsedSeconds.value))
const answerPlaceholder = computed(() => isCodeQuestion.value ? '在这里输入代码。代码题会提交给后续讲解或评阅。' : '输入你的答案')
const feedbackState = computed(() => feedback.value?.pendingReview ? 'pending' : feedback.value?.isCorrect === true ? 'correct' : feedback.value?.isCorrect === false ? 'wrong' : 'pending')
const feedbackIcon = computed(() => feedback.value?.pendingReview ? 'pending_actions' : feedback.value?.isCorrect === true ? 'check_circle' : feedback.value?.isCorrect === false ? 'error' : 'task_alt')
const feedbackTitle = computed(() => feedback.value?.pendingReview ? '已提交，等待评阅' : feedback.value?.isCorrect === true ? '回答正确' : feedback.value?.isCorrect === false ? '需要订正' : '已提交')
const feedbackSummary = computed(() => feedback.value?.pendingReview ? '简答和代码题不会被伪装成自动判分结果。' : feedback.value?.isCorrect === true ? '这道题已记录为本轮完成。' : feedback.value?.isCorrect === false ? '看看参考答案和解析，再决定是否继续。' : '答案已保存到本轮练习。')
const nextLabel = computed(() => currentIndex.value === items.value.length - 1 ? '完成本轮' : '下一题')

watch(items, nextItems => {
  if (!nextItems.length) return
  if (currentIndex.value >= nextItems.length) currentIndex.value = 0
}, { deep: true })

watch(() => props.session?.id, () => {
  const savedPosition = Number(props.session?.currentPosition)
  const savedIndex = items.value.findIndex(item => Number(item.position) === savedPosition)
  currentIndex.value = savedIndex >= 0 ? savedIndex : 0
  openedAt.value = Date.now()
}, { immediate: true })

function itemKey(item) { return String(item?.questionId ?? item?.question?.id ?? item?.id ?? '') }
function positionValue(index = currentIndex.value) { return Number(items.value[index]?.position ?? index + 1) }
function hasExplicitSubmittedAnswer(item) { return Object.prototype.hasOwnProperty.call(item || {}, 'submittedAnswer') && item.submittedAnswer !== null && item.submittedAnswer !== undefined }
function isItemSubmitted(item) { return item?.state === 'SUBMITTED' || Boolean(item?.submittedAt) || hasExplicitSubmittedAnswer(item) }
function persistedFeedback(item) { if (!isItemSubmitted(item)) return null; const keys = ['isCorrect', 'pendingReview', 'correctAnswer', 'explanation', 'score']; const hasFeedback = keys.some(key => Object.prototype.hasOwnProperty.call(item || {}, key)); return hasFeedback ? Object.fromEntries(keys.filter(key => Object.prototype.hasOwnProperty.call(item, key)).map(key => [key, item[key]])) : null }
function updateAnswer(value) { drafts.value = { ...drafts.value, [currentKey.value]: value }; queueSave() }
function clearAnswer() { updateAnswer('') }
function queueSave() { const id = currentKey.value; if (!id || isSubmitted.value) return; clearTimeout(saveTimers.get(id)); saveTimers.set(id, window.setTimeout(() => flushDraft(), 550)) }
function flushDraft(currentPosition = positionValue()) { const id = currentKey.value; if (!id) return; clearTimeout(saveTimers.get(id)); saveTimers.delete(id); if (isSubmitted.value) { emit('update-position', { questionId: id, currentPosition }); return }; emit('save-item', { questionId: id, draftAnswer: rawAnswer.value, flagged: Boolean(currentItem.value.flagged), currentPosition }) }
function toggleFlag() { const id = currentKey.value; if (!id || isSubmitted.value) return; emit('save-item', { questionId: id, draftAnswer: rawAnswer.value, flagged: !currentItem.value.flagged, currentPosition: positionValue() }) }
function submitCurrent() { if (!hasAnswer.value || isBusy.value || isSubmitted.value) return; const priorSeconds = Number(currentItem.value.timeSpentSec || 0); emit('submit-item', { questionId: currentKey.value, userAnswer: rawAnswer.value, timeSpent: Math.max(1, priorSeconds + elapsedSeconds.value) }) }
function goTo(index) { if (index === currentIndex.value) return; flushDraft(positionValue(index)); currentIndex.value = index; openedAt.value = Date.now() }
function nextQuestion() { if (currentIndex.value === items.value.length - 1) emit('finish'); else goTo(currentIndex.value + 1) }
function splitMulti(value) { return String(value || '').split(',').map(item => item.trim()).filter(Boolean).sort() }
function normalizeOptions(raw) { const parsed = safeJson(raw, raw); const source = Array.isArray(parsed) ? parsed : parsed && typeof parsed === 'object' ? Object.entries(parsed).map(([key, text]) => ({ key, text })) : typeof raw === 'string' ? raw.split(/\r?\n|;/).filter(Boolean) : []; return source.map((item, index) => { if (item && typeof item === 'object') return { key: String(item.key || item.value || String.fromCharCode(65 + index)).trim(), text: String(item.text || item.label || item.content || '').trim() }; const value = String(item).trim(); const match = value.match(/^([A-Za-z])\s*[.、:：\-]\s*(.+)$/); return { key: match?.[1].toUpperCase() || String.fromCharCode(65 + index), text: match?.[2] || value } }).filter(item => item.text) }
function optionWasSelected(option) { return isMultiChoice.value ? selectedMulti.value.includes(option.key) : selectedSingle.value === option.key }
function correctKeys() { return String(feedback.value?.correctAnswer || '').toUpperCase().match(/[A-Z]/g) || [] }
function isCorrectOption(option) { return correctKeys().includes(option.key) }
function optionState(option) { if (!isSubmitted.value) return { selected: optionWasSelected(option) }; return { selected: optionWasSelected(option), correct: isCorrectOption(option), wrong: optionWasSelected(option) && feedback.value?.isCorrect === false, muted: !optionWasSelected(option) && !isCorrectOption(option) } }
function typeLabel(value) { return ({ single_choice: '单选题', multi_choice: '多选题', fill_blank: '填空题', short_answer: '简答题', code: '代码题' }[String(value || '').toLowerCase()] || '练习题') }
function difficultyLabel(value) { return ({ easy: '基础', medium: '进阶', hard: '挑战' }[String(value || '').toLowerCase()] || '练习') }
function knowledgeTags(source) { const tags = safeJson(source.knowledgePoints, []); return Array.isArray(tags) ? tags.slice(0, 2) : [] }
function navState(item, index) { return { current: index === currentIndex.value, submitted: isItemSubmitted(item), correct: item.isCorrect === true || props.feedbackByQuestion[itemKey(item)]?.isCorrect === true, flagged: Boolean(item.flagged) } }
function navLabel(item, index) { const state = isItemSubmitted(item) ? '已提交' : item.flagged ? '标记稍后处理' : '未作答'; return `第 ${index + 1} 题，${state}` }
function formatDuration(seconds) { const minutes = Math.floor(seconds / 60); return `${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}` }

defineExpose({ flushDraft })

onMounted(() => { clockTimer = window.setInterval(() => { now.value = Date.now() }, 1000) })
onUnmounted(() => { window.clearInterval(clockTimer); saveTimers.forEach(timer => clearTimeout(timer)) })
</script>

<style scoped>
.practice-session { min-height: calc(100dvh - var(--topbar-height) - 2 * var(--margin-desktop)); display: grid; gap: 18px; max-width: 1280px; margin: 0 auto; }.practice-header { min-height: 52px; display: grid; grid-template-columns: 1fr auto auto auto; align-items: center; gap: 16px; border-bottom: 1px solid var(--outline-variant); }.back-button, .end-button { min-height: 40px; display: inline-flex; align-items: center; gap: 6px; border: 0; background: transparent; color: var(--on-surface); font: inherit; font-size: 14px; font-weight: 800; cursor: pointer; }.back-button { justify-self: start; padding: 0; }.back-button:hover { color: var(--primary); }.back-button .material-symbols-outlined { font-size: 20px; }.session-context { min-width: 128px; display: grid; gap: 1px; text-align: right; }.session-context span { color: var(--on-surface-variant); font-size: 12px; line-height: 18px; }.session-context strong { color: var(--on-surface); font-size: 14px; line-height: 20px; }.practice-header time { display: inline-flex; align-items: center; gap: 5px; color: var(--on-surface-variant); font-family: 'JetBrains Mono', monospace; font-size: 13px; }.practice-header time .material-symbols-outlined { font-size: 18px; }.end-button { justify-self: end; padding: 0 12px; border: 1px solid var(--outline-variant); border-radius: var(--radius-md); color: var(--error); }.end-button:hover:not(:disabled) { border-color: var(--error); background: var(--error-container); }.end-button:disabled { opacity: .6; cursor: wait; }
.practice-layout { display: grid; grid-template-columns: minmax(0, 1fr) 194px; align-items: start; gap: 22px; }.question-sheet { min-height: 620px; display: flex; flex-direction: column; border: 1px solid var(--outline-variant); border-radius: var(--radius-lg); background: var(--surface-container-lowest); box-shadow: var(--shadow-soft); }.question-heading { padding: 30px 34px 24px; }.question-badges { display: flex; flex-wrap: wrap; gap: 6px; }.question-badges span { padding: 3px 8px; border-radius: 999px; color: var(--on-surface-variant); background: var(--surface-container); font-size: 12px; line-height: 18px; font-weight: 700; }.question-badges span:first-child { color: var(--primary); background: var(--primary-fixed); }.question-heading h1 { white-space: pre-wrap; margin: 20px 0 0; color: var(--on-surface); font-size: clamp(21px, 2.1vw, 29px); line-height: 1.46; letter-spacing: 0; font-weight: 720; }.answer-fieldset, .free-answer { margin: 0; padding: 0 34px 30px; border: 0; }.answer-fieldset legend, .free-answer > label { display: block; margin-bottom: 12px; color: var(--on-surface-variant); font-size: 14px; line-height: 20px; font-weight: 800; }.answer-fieldset { display: grid; gap: 10px; }.option-row { min-height: 58px; display: grid; grid-template-columns: auto auto minmax(0, 1fr) auto; align-items: center; gap: 12px; padding: 10px 14px; border: 1px solid var(--outline-variant); border-radius: var(--radius-md); color: var(--on-surface); cursor: pointer; transition: border-color 150ms ease, background 150ms ease; }.option-row:hover { border-color: color-mix(in srgb, var(--primary) 50%, var(--outline-variant)); }.option-row.selected { border-color: var(--primary); background: var(--primary-fixed); }.option-row.correct { border-color: var(--success); background: var(--success-container); }.option-row.wrong { border-color: var(--error); background: var(--error-container); }.option-row.muted { opacity: .62; }.option-row input { width: 17px; height: 17px; margin: 0; accent-color: var(--primary); }.option-key { width: 24px; height: 24px; display: grid; place-items: center; border-radius: 50%; color: var(--on-surface-variant); background: var(--surface-container); font-size: 12px; font-weight: 800; }.option-mark { font-size: 20px; }.option-mark.correct { color: var(--success); }.option-mark.wrong { color: var(--error); }.free-answer { display: grid; }.answer-input { min-height: 180px; box-sizing: border-box; resize: vertical; border: 1px solid var(--outline-variant); border-radius: var(--radius-md); padding: 14px; color: var(--on-surface); background: var(--surface-container-lowest); font: inherit; font-size: 15px; line-height: 24px; outline: none; }.answer-input:focus { border-color: var(--primary); box-shadow: 0 0 0 2px color-mix(in srgb, var(--primary) 16%, transparent); }.answer-input.code { min-height: 240px; font-family: 'JetBrains Mono', Consolas, monospace; font-size: 13px; line-height: 22px; }.action-error { display: flex; align-items: center; gap: 7px; margin: 0 34px 20px; color: var(--error); font-size: 14px; line-height: 20px; }.action-error .material-symbols-outlined { font-size: 19px; }
.feedback-panel { display: grid; gap: 12px; margin: 0 34px 26px; padding: 16px; border: 1px solid var(--outline-variant); border-radius: var(--radius-md); }.feedback-panel.correct { border-color: color-mix(in srgb, var(--success) 56%, var(--outline-variant)); background: var(--success-container); }.feedback-panel.wrong { border-color: color-mix(in srgb, var(--error) 48%, var(--outline-variant)); background: var(--error-container); }.feedback-panel.pending { background: var(--surface-container-low); }.feedback-panel header { display: flex; gap: 10px; align-items: flex-start; }.feedback-panel header > .material-symbols-outlined { font-size: 24px; color: var(--primary); }.feedback-panel.correct header > .material-symbols-outlined { color: var(--success); }.feedback-panel.wrong header > .material-symbols-outlined { color: var(--error); }.feedback-panel h2, .feedback-panel p { margin: 0; }.feedback-panel h2 { color: var(--on-surface); font-size: 16px; line-height: 22px; }.feedback-panel header p, .explanation { margin-top: 2px; color: var(--on-surface-variant); font-size: 14px; line-height: 21px; }.correct-answer { display: grid; gap: 3px; color: var(--on-surface); font-size: 14px; line-height: 21px; }.correct-answer strong { font-size: 12px; }.explain-button { width: max-content; min-height: 34px; display: inline-flex; align-items: center; gap: 6px; border: 0; border-radius: var(--radius-sm); padding: 0 10px; color: var(--primary); background: var(--surface-container-lowest); font: inherit; font-size: 13px; font-weight: 800; cursor: pointer; }.explain-button:disabled { opacity: .65; cursor: wait; }.explain-button .material-symbols-outlined { font-size: 18px; }
.answer-dock { position: sticky; bottom: 0; display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-top: auto; padding: 16px 34px; border-top: 1px solid var(--outline-variant); border-radius: 0 0 var(--radius-lg) var(--radius-lg); background: color-mix(in srgb, var(--surface-container-lowest) 94%, transparent); backdrop-filter: blur(8px); }.flag-button { min-height: 40px; display: inline-flex; align-items: center; gap: 6px; border: 0; padding: 0; color: var(--on-surface-variant); background: transparent; font: inherit; font-size: 13px; font-weight: 750; cursor: pointer; }.flag-button[aria-pressed='true'] { color: var(--primary); }.flag-button:disabled { opacity: .55; cursor: not-allowed; }.flag-button .material-symbols-outlined { font-size: 19px; }.dock-actions { display: flex; gap: 8px; }.clear-button, .submit-button { width: auto; min-width: 86px; padding: 0 14px; }.submit-button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; }.question-nav { position: sticky; top: calc(var(--topbar-height) + 18px); display: grid; gap: 16px; padding: 16px; border: 1px solid var(--outline-variant); border-radius: var(--radius-lg); background: var(--surface-container-lowest); }.nav-title { display: grid; gap: 1px; }.nav-title span { color: var(--on-surface-variant); font-size: 12px; line-height: 18px; }.nav-title strong { color: var(--on-surface); font-size: 17px; line-height: 24px; }.number-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 7px; }.number-button { aspect-ratio: 1; min-width: 0; border: 1px solid var(--outline-variant); border-radius: 8px; color: var(--on-surface-variant); background: var(--surface-container-lowest); font: inherit; font-size: 13px; font-weight: 800; cursor: pointer; }.number-button:hover { border-color: var(--primary); color: var(--primary); }.number-button.current { border-color: var(--primary); color: var(--on-primary); background: var(--primary); }.number-button.submitted { color: var(--success); background: var(--success-container); }.number-button.correct { border-color: var(--success); }.number-button.flagged { box-shadow: inset 0 -3px 0 var(--warning); }.nav-key { display: grid; gap: 6px; color: var(--on-surface-variant); font-size: 11px; line-height: 16px; }.nav-key span { display: flex; align-items: center; gap: 6px; }.nav-key i { width: 8px; height: 8px; border-radius: 50%; background: var(--outline); }.nav-key .key-current { background: var(--primary); }.nav-key .key-done { background: var(--success); }.nav-key .key-flag { background: var(--warning); }
.practice-skeleton { display: grid; grid-template-columns: minmax(0, 1fr) 194px; gap: 22px; }.practice-skeleton span { min-height: 580px; border-radius: var(--radius-lg); background: linear-gradient(90deg, var(--surface-container) 0%, var(--surface-container-high) 44%, var(--surface-container) 100%); background-size: 240% 100%; animation: shimmer 1.3s ease-in-out infinite; }.practice-empty { min-height: 400px; display: grid; place-items: center; align-content: center; gap: 10px; color: var(--on-surface-variant); text-align: center; }.practice-empty .material-symbols-outlined { color: var(--error); font-size: 44px; }.practice-empty h1, .practice-empty p { margin: 0; }.practice-empty h1 { color: var(--on-surface); font-size: 24px; }.practice-empty p { max-width: 400px; font-size: 14px; line-height: 22px; }.practice-empty .primary-button { width: auto; padding: 0 16px; }@keyframes shimmer { from { background-position: 100% 0; } to { background-position: -100% 0; } }
@media (max-width: 900px) { .practice-layout, .practice-skeleton { grid-template-columns: 1fr; }.question-nav { position: static; order: -1; }.nav-title { display: flex; justify-content: space-between; align-items: center; }.number-grid { grid-template-columns: repeat(auto-fit, minmax(38px, 1fr)); }.nav-key { display: flex; flex-wrap: wrap; gap: 10px; } }
@media (max-width: 620px) { .practice-session { min-height: auto; }.practice-header { grid-template-columns: 1fr auto; gap: 8px; }.session-context { grid-column: 1; justify-self: start; min-width: 0; text-align: left; }.practice-header time { grid-column: 2; grid-row: 2; }.end-button { padding: 0 9px; }.question-heading, .answer-fieldset, .free-answer { padding-left: 18px; padding-right: 18px; }.question-heading { padding-top: 22px; }.question-heading h1 { margin-top: 16px; font-size: 20px; }.feedback-panel { margin-left: 18px; margin-right: 18px; }.answer-dock { align-items: stretch; flex-direction: column; padding: 12px 18px; }.dock-actions { display: grid; grid-template-columns: 1fr 1.5fr; }.clear-button, .submit-button { width: 100%; }.option-row { grid-template-columns: auto auto minmax(0, 1fr) auto; gap: 8px; padding: 10px; } }
</style>
