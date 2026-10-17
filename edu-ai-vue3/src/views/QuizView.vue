<template>
  <section class="page quiz-module-page">
    <div class="quiz-module-shell" :inert="dialogMode ? true : null">
      <header v-if="!isPractice" class="module-header" :class="{ 'home-header': view === 'home' }">
        <div>
          <p class="module-kicker">学习练习</p>
          <h1>测验</h1>
          <p>选题、作答和复盘都在这里完成。</p>
        </div>
        <nav class="module-tabs" aria-label="测验功能">
          <button v-for="tab in tabs" :key="tab.view" class="module-tab" :class="{ active: view === tab.view }" type="button" :aria-current="view === tab.view ? 'page' : undefined" @click="navigate(tab.view)">
            <span class="material-symbols-outlined" aria-hidden="true">{{ tab.icon }}</span>{{ tab.label }}
          </button>
        </nav>
      </header>

      <QuizHomePanel
        v-if="view === 'home'"
        :active-session="activeSession"
        :summary="homeSummary"
        :loading="homeLoading"
        :starting="creatingSession"
        :error="homeError"
        @resume="resumeActiveSession"
        @quick-start="quickStart"
        @new-round="quickStart"
        @open-bank="navigate('bank')"
        @open-wrong="navigate('wrong')"
        @retry="retryHome"
      />

      <QuestionBankPanel
        v-else-if="view === 'bank'"
        :rows="bank.rows"
        :total="bank.total"
        :page-num="bank.pageNum"
        :page-size="bank.pageSize"
        :filters="bank.filters"
        :facets="facets"
        :selected-ids="bankSelection"
        :loading="bank.loading"
        :starting="creatingSession"
        :can-create="canCreatePractice"
        :active-session="activeSession"
        :creation-checking="activeSessionState === 'checking'"
        :creation-error="activeSessionState === 'error' ? activeSessionError : ''"
        :error="bank.error"
        @search="searchBank"
        @reset="resetBank"
        @retry="loadBank"
        @toggle="toggleBankQuestion"
        @clear-selection="bankSelection = []"
        @start="startBankPractice"
        @new-round="startBankNewRound"
        @page="changeBankPage"
        @retry-session-check="refreshActiveSession()"
        @resume-session="resumeActiveSession"
      />

      <WrongAnswerPanel
        v-else-if="view === 'wrong'"
        :rows="wrong.rows"
        :total="wrong.total"
        :page-num="wrong.pageNum"
        :page-size="wrong.pageSize"
        :selected-ids="wrongSelection"
        :loading="wrong.loading"
        :starting="creatingSession"
        :can-create="canCreatePractice"
        :active-session="activeSession"
        :creation-checking="activeSessionState === 'checking'"
        :creation-error="activeSessionState === 'error' ? activeSessionError : ''"
        :error="wrong.error"
        @refresh="loadWrong"
        @toggle="toggleWrongQuestion"
        @clear-selection="wrongSelection = []"
        @start="startWrongPractice"
        @new-round="startWrongNewRound"
        @page="changeWrongPage"
        @open-bank="navigate('bank')"
        @retry-session-check="refreshActiveSession()"
        @resume-session="resumeActiveSession"
      />

      <div
        v-else-if="view === 'practice' && session"
        class="practice-panel-boundary"
        @click.capture="guardPracticeNavigation"
        @keydown.capture="guardPracticeNavigation"
      >
        <PracticeSessionPanel
          ref="practicePanel"
          :session="session"
          :feedback-by-question="feedbackByQuestion"
          :busy-question-ids="busyQuestionIds"
          :finishing="finishingSession"
          :error="practicePanelError"
          @leave="requestLeave"
          @finish="requestFinish"
          @save-item="savePracticeItem"
          @update-position="updateSessionPosition"
          @submit-item="submitPracticeItem"
          @explain="loadAiExplanation"
        />
      </div>

      <section v-else-if="view === 'practice'" class="module-state" aria-live="polite">
        <span class="material-symbols-outlined" aria-hidden="true">error</span>
        <h1>{{ sessionError ? '无法恢复这轮练习' : '正在恢复练习' }}</h1>
        <p>{{ sessionError || '正在读取已保存的题目和作答状态。' }}</p>
        <div v-if="sessionError" class="state-actions">
          <button class="primary-button state-button" type="button" @click="retrySession">重新加载</button>
          <button class="ghost-button state-button" type="button" @click="navigate('home')">返回测验</button>
        </div>
      </section>

      <SessionResultPanel
        v-else-if="view === 'result'"
        :result="result"
        :loading="resultLoading"
        :starting="creatingSession"
        :can-create="canCreatePractice"
        :active-session="activeSession"
        :creation-checking="activeSessionState === 'checking'"
        :creation-error="activeSessionState === 'error' ? activeSessionError : ''"
        :error="resultError"
        @retry="loadResult(route.query.session)"
        @retry-wrong="startResultWrongPractice"
        @new-round="startResultNewRound"
        @open-bank="navigate('bank')"
        @open-wrong="navigate('wrong')"
        @retry-session-check="refreshActiveSession()"
        @resume-session="resumeActiveSession"
      />
    </div>

    <div v-if="dialogMode" class="quiz-dialog-scrim" @click.self="closeDialog">
      <section class="quiz-dialog" role="dialog" aria-modal="true" :aria-labelledby="dialogTitleId">
        <template v-if="dialogMode === 'leave'">
          <span class="dialog-icon material-symbols-outlined" aria-hidden="true">save</span>
          <h2 id="leave-dialog-title">离开当前练习？</h2>
          <p>已输入的草稿会保存到这轮练习。之后可从测验首页继续。</p>
          <div class="dialog-actions stacked">
            <button ref="dialogPrimary" class="primary-button dialog-button" type="button" :disabled="finishingSession" @click="saveAndLeave">
              {{ finishingSession ? '正在保存' : '保存并离开' }}
            </button>
            <button class="ghost-button dialog-button" type="button" :disabled="finishingSession" @click="abandonSession">放弃本轮</button>
            <button class="text-dialog-button" type="button" @click="closeDialog">继续答题</button>
          </div>
        </template>
        <template v-else-if="dialogMode === 'finish'">
          <span class="dialog-icon material-symbols-outlined" aria-hidden="true">flag</span>
          <h2 id="finish-dialog-title">结束本轮练习？</h2>
          <p>未提交的题目不会被计为完成。结束后可查看本轮结果并决定是否订正错题。</p>
          <p v-if="practiceNavigationError" class="dialog-error" role="alert">{{ practiceNavigationError }}</p>
          <div class="dialog-actions">
            <button class="ghost-button dialog-button" type="button" :disabled="finishingSession" @click="closeDialog">继续答题</button>
            <button ref="dialogPrimary" class="primary-button dialog-button" type="button" :disabled="finishingSession" @click="finishSession">{{ finishingSession ? '正在结束' : '结束并查看结果' }}</button>
          </div>
        </template>
        <template v-else>
          <span class="dialog-icon material-symbols-outlined" aria-hidden="true">restart_alt</span>
          <h2 id="replace-dialog-title">{{ replacementDialogTitle }}</h2>
          <p>{{ replacementDialogDescription }}</p>
          <dl class="replace-session-facts" aria-label="当前练习状态">
            <div><dt>当前进度</dt><dd>{{ replacementSubmittedCount }} / {{ replacementTotalCount }} 题已提交</dd></div>
            <div><dt>新一轮</dt><dd>{{ pendingSessionRequest?.questionIds?.length || 0 }} 题</dd></div>
          </dl>
          <p v-if="replacementError" class="dialog-error" role="alert">{{ replacementError }}</p>
          <div class="dialog-actions stacked">
            <button ref="dialogPrimary" class="primary-button dialog-button" type="button" :disabled="creatingSession" @click="replaceActiveSession">
              {{ creatingSession ? '正在创建新一轮' : replacementPrimaryLabel }}
            </button>
            <button v-if="activeSession?.id" class="ghost-button dialog-button" type="button" :disabled="creatingSession" @click="resumeFromReplacement">继续当前练习</button>
            <button class="text-dialog-button" type="button" :disabled="creatingSession" @click="cancelSessionReplacement">取消</button>
          </div>
        </template>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import QuizHomePanel from '@/components/QuizHomePanel.vue'
import QuestionBankPanel from '@/components/QuestionBankPanel.vue'
import WrongAnswerPanel from '@/components/WrongAnswerPanel.vue'
import PracticeSessionPanel from '@/components/PracticeSessionPanel.vue'
import SessionResultPanel from '@/components/SessionResultPanel.vue'
import { eduApi } from '@/services/api'
import { dataOf } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const tabs = [
  { view: 'home', label: '开始练习', icon: 'play_circle' },
  { view: 'bank', label: '题库', icon: 'library_books' },
  { view: 'wrong', label: '错题复习', icon: 'history_edu' }
]
const validViews = new Set(['home', 'bank', 'wrong', 'practice', 'result'])
const activeSession = ref(null)
const activeSessionState = ref('checking')
const activeSessionError = ref('')
const homeError = ref('')
const homeLoading = ref(false)
const homeSummary = reactive({ bankTotal: null, bankError: '', wrongTotal: null, wrongError: '', objectiveAnsweredCount: null, objectiveCorrectRate: null, pendingReviewCount: null, statsError: '' })
const facets = ref({ questionTypes: [], difficulties: [], knowledgePoints: [] })
const bank = reactive({ rows: [], total: 0, pageNum: 1, pageSize: 12, filters: { keyword: '', questionType: '', difficulty: '', knowledgePoint: '' }, loading: false, error: '' })
const wrong = reactive({ rows: [], total: 0, pageNum: 1, pageSize: 12, loading: false, error: '' })
const bankSelection = ref([])
const wrongSelection = ref([])
const session = ref(null)
const sessionError = ref('')
const result = ref({})
const resultLoading = ref(false)
const resultError = ref('')
const creatingSession = ref(false)
const finishingSession = ref(false)
const practiceError = ref('')
const practiceNavigationError = ref('')
const feedbackByQuestion = ref({})
const busyQuestionIds = ref([])
const dialogMode = ref('')
const pendingSessionRequest = ref(null)
const replacementError = ref('')
const practicePanel = ref(null)
const dialogPrimary = ref(null)
const pendingItemSaves = new Set()
let routeLoadId = 0
let legacySessionRequest = ''

const view = computed(() => {
  const requested = String(route.query.view || '')
  if (validViews.has(requested)) return requested
  return route.query.session ? 'practice' : 'home'
})
const isPractice = computed(() => view.value === 'practice')
const canCreatePractice = computed(() => activeSessionState.value === 'ready')
const practicePanelError = computed(() => practiceError.value || practiceNavigationError.value)
const dialogTitleId = computed(() => dialogMode.value === 'leave' ? 'leave-dialog-title' : dialogMode.value === 'finish' ? 'finish-dialog-title' : 'replace-dialog-title')
const replacementSession = computed(() => pendingSessionRequest.value?.activeSessionSnapshot || activeSession.value)
const replacementTotalCount = computed(() => sessionTotal(replacementSession.value))
const replacementSubmittedCount = computed(() => sessionSubmitted(replacementSession.value))
const replacementIsComplete = computed(() => replacementTotalCount.value > 0 && replacementSubmittedCount.value >= replacementTotalCount.value)
const replacementDialogTitle = computed(() => replacementIsComplete.value ? '当前练习已答完，开始新一轮？' : '结束当前练习并开始新一轮？')
const replacementDialogDescription = computed(() => replacementIsComplete.value
  ? '当前练习会先保存为已完成记录，再创建全新的练习。旧答案不会被覆盖。'
  : '当前练习会保存为已结束记录；已提交答案继续保留，未提交题目不会计入完成。')
const replacementPrimaryLabel = computed(() => replacementIsComplete.value ? '完成当前并开始新一轮' : '结束当前并开始新一轮')

watch(() => route.fullPath, () => syncRoute(), { immediate: true })
watch(dialogMode, async mode => {
  if (!mode) return
  await nextTick()
  dialogPrimary.value?.focus()
})

async function syncRoute() {
  const loadId = ++routeLoadId
  const legacyIds = parseQuestionIds(route.query.ids)
  if (legacyIds.length && !route.query.session) {
    const requestKey = legacyIds.join(',')
    if (legacySessionRequest !== requestKey) {
      legacySessionRequest = requestKey
      try {
        await createSession(legacyIds, 'AI_CHAT')
      } catch (error) {
        homeError.value = messageOf(error, '无法从这组题目创建练习，请稍后重试。')
        await router.replace({ name: 'quiz', query: { view: 'home', setupError: homeError.value } })
      }
    }
    return
  }

  if (view.value === 'home') await loadHome(loadId)
  if (view.value === 'bank') {
    await Promise.all([loadFacets(), loadBank(), refreshActiveSession(loadId)])
  }
  if (view.value === 'wrong') await Promise.all([loadWrong(), refreshActiveSession(loadId)])
  if (view.value === 'practice') await loadSession(route.query.session, loadId)
  if (view.value === 'result') await Promise.all([loadResult(route.query.session, loadId), refreshActiveSession(loadId)])
}

function navigate(nextView, query = {}) {
  if (isPractice.value && nextView !== 'practice') {
    requestLeave()
    return
  }
  const nextQuery = { view: nextView, ...query }
  router.push({ name: 'quiz', query: nextQuery })
}

async function loadHome(loadId = routeLoadId) {
  homeError.value = String(route.query.setupError || '')
  homeLoading.value = true
  homeSummary.bankTotal = null
  homeSummary.bankError = ''
  homeSummary.wrongTotal = null
  homeSummary.wrongError = ''
  homeSummary.objectiveAnsweredCount = null
  homeSummary.objectiveCorrectRate = null
  homeSummary.pendingReviewCount = null
  homeSummary.statsError = ''
  const [activeResult, bankResult, wrongResult, statsResult] = await Promise.allSettled([
    refreshActiveSession(loadId),
    Promise.resolve().then(() => eduApi.quizQuestions({ pageNum: 1, pageSize: 1 })),
    Promise.resolve().then(() => eduApi.wrongQuizQuestions({ pageNum: 1, pageSize: 1 })),
    Promise.resolve().then(() => eduApi.quizStats())
  ])
  if (loadId !== routeLoadId) return

  if (activeResult.status === 'rejected' || activeSessionState.value === 'error') {
    if (!homeError.value) homeError.value = activeSessionError.value || messageOf(activeResult.reason, '无法读取未完成练习，请稍后重试。')
  }

  if (bankResult.status === 'fulfilled') {
    homeSummary.bankTotal = validTotal(dataOf(bankResult.value, {})?.total)
  } else {
    homeSummary.bankError = messageOf(bankResult.reason, '题库数量暂时无法加载。')
  }

  if (wrongResult.status === 'fulfilled') {
    homeSummary.wrongTotal = validTotal(dataOf(wrongResult.value, {})?.total)
  } else {
    homeSummary.wrongError = messageOf(wrongResult.reason, '错题数量暂时无法加载。')
  }

  if (statsResult.status === 'fulfilled') {
    const stats = dataOf(statsResult.value, {}) || {}
    homeSummary.objectiveAnsweredCount = validTotal(stats.objectiveAnsweredCount)
    homeSummary.objectiveCorrectRate = validRate(stats.objectiveCorrectRate)
    homeSummary.pendingReviewCount = validTotal(stats.pendingReviewCount)
  } else {
    homeSummary.statsError = messageOf(statsResult.reason, '累计练习数据暂时无法加载。')
  }

  homeLoading.value = false
}

async function refreshActiveSession(loadId = routeLoadId) {
  activeSessionState.value = 'checking'
  activeSessionError.value = ''
  try {
    const response = await eduApi.activeQuizSession()
    if (loadId !== routeLoadId) return null
    const payload = dataOf(response, null)
    activeSession.value = normalizeSession(payload?.session ?? payload)
    activeSessionState.value = 'ready'
    return activeSession.value
  } catch (error) {
    if (loadId !== routeLoadId) return null
    activeSessionState.value = 'error'
    activeSessionError.value = messageOf(error, '无法确认是否存在未完成练习，请重试。')
    return null
  }
}

function retryHome() {
  if (route.query.setupError) {
    router.replace({ name: 'quiz', query: { view: 'home' } })
    return
  }
  loadHome(++routeLoadId)
}

async function loadFacets() {
  try {
    facets.value = dataOf(await eduApi.quizQuestionFacets(), {}) || {}
  } catch {
    facets.value = { questionTypes: [], difficulties: [], knowledgePoints: [] }
  }
}

async function loadBank() {
  bank.loading = true
  bank.error = ''
  try {
    const response = await eduApi.quizQuestions({ pageNum: bank.pageNum, pageSize: bank.pageSize, ...bank.filters })
    const payload = dataOf(response, {}) || {}
    bank.rows = Array.isArray(payload.rows) ? payload.rows : []
    bank.total = Number(payload.total || 0)
  } catch (error) {
    bank.rows = []
    bank.total = 0
    bank.error = messageOf(error, '题库加载失败，请检查网络后重试。')
  } finally {
    bank.loading = false
  }
}

async function loadWrong() {
  wrong.loading = true
  wrong.error = ''
  try {
    const payload = dataOf(await eduApi.wrongQuizQuestions({ pageNum: wrong.pageNum, pageSize: wrong.pageSize }), {}) || {}
    wrong.rows = Array.isArray(payload.rows) ? payload.rows : []
    wrong.total = Number(payload.total || 0)
  } catch (error) {
    wrong.rows = []
    wrong.total = 0
    wrong.error = messageOf(error, '错题列表加载失败，请检查网络后重试。')
  } finally {
    wrong.loading = false
  }
}

async function loadSession(sessionId, loadId = routeLoadId) {
  if (!sessionId) {
    session.value = null
    sessionError.value = '缺少练习会话标识，无法恢复本轮练习。'
    return
  }
  sessionError.value = ''
  try {
    const current = normalizeSession(dataOf(await eduApi.quizSession(sessionId), null))
    if (loadId !== routeLoadId) return
    if (!current?.id) throw new Error('练习会话不存在或已不可用。')
    if (current.status === 'COMPLETED') {
      await router.replace({ name: 'quiz', query: { view: 'result', session: current.id } })
      return
    }
    if (current.status === 'ABANDONED') {
      await router.replace({ name: 'quiz', query: { view: 'home', setupError: '这轮练习已经结束，历史答案不会被重新打开。' } })
      return
    }
    if (current.status !== 'IN_PROGRESS') throw new Error('这轮练习当前不可继续。')
    session.value = current
    practiceNavigationError.value = ''
    if (current.status === 'IN_PROGRESS') {
      activeSession.value = current
      activeSessionState.value = 'ready'
      activeSessionError.value = ''
    }
  } catch (error) {
    if (loadId !== routeLoadId) return
    session.value = null
    sessionError.value = messageOf(error, '无法恢复这轮练习，请返回测验重新选择题目。')
  }
}

function retrySession() {
  loadSession(route.query.session, ++routeLoadId)
}

async function loadResult(sessionId, loadId = routeLoadId) {
  if (!sessionId) {
    resultError.value = '缺少练习会话标识，无法查看结果。'
    return
  }
  resultLoading.value = true
  resultError.value = ''
  try {
    const response = dataOf(await eduApi.quizSessionResult(sessionId), {}) || {}
    const payload = normalizeSession(response.session ? { ...response.session, ...(response.result || {}) } : { ...response, ...(response.result || {}) }) || {}
    if (loadId !== routeLoadId) return
    result.value = payload
    session.value = payload
  } catch (error) {
    if (loadId !== routeLoadId) return
    resultError.value = messageOf(error, '本轮结果加载失败，请稍后重试。')
  } finally {
    if (loadId === routeLoadId) resultLoading.value = false
  }
}

function searchBank(nextFilters) {
  bank.filters = { ...bank.filters, ...nextFilters }
  bank.pageNum = 1
  loadBank()
}

function resetBank() {
  bank.filters = { keyword: '', questionType: '', difficulty: '', knowledgePoint: '' }
  bank.pageNum = 1
  bankSelection.value = []
  loadBank()
}

function changeBankPage(pageNum) { bank.pageNum = Math.max(1, Number(pageNum) || 1); loadBank() }
function changeWrongPage(pageNum) { wrong.pageNum = Math.max(1, Number(pageNum) || 1); loadWrong() }
function toggleBankQuestion(id) { bankSelection.value = toggleId(bankSelection.value, id) }
function toggleWrongQuestion(id) { wrongSelection.value = toggleId(wrongSelection.value, id) }

async function quickStart() {
  if (creatingSession.value) return
  creatingSession.value = true
  homeError.value = ''
  try {
    const ids = await pickQuickPracticeIds()
    if (!ids.length) throw new Error('当前题库没有可开始练习的题目。')
    await createSession(ids, 'BANK', true, 'quick')
  } catch (error) {
    homeError.value = messageOf(error, '无法创建练习，请稍后重试。')
  } finally {
    creatingSession.value = false
  }
}

async function pickQuickPracticeIds() {
  const knownTotal = validTotal(homeSummary.bankTotal)
  const total = knownTotal === null
    ? validTotal(dataOf(await eduApi.quizQuestions({ pageNum: 1, pageSize: 1 }), {})?.total)
    : knownTotal
  if (!total) return []

  const pageSize = Math.min(50, total)
  const pageCount = Math.max(1, Math.ceil(total / pageSize))
  const randomPage = randomInt(pageCount) + 1
  const pages = [...new Set([randomPage, pageCount > 1 ? (randomPage % pageCount) + 1 : randomPage])]
  const rows = []
  for (const pageNum of pages) {
    const payload = dataOf(await eduApi.quizQuestions({ pageNum, pageSize }), {}) || {}
    if (Array.isArray(payload.rows)) rows.push(...payload.rows)
    if (rows.length >= Math.min(20, total)) break
  }

  const previousIds = new Set((activeSession.value?.items || []).map(item => String(item.questionId ?? item.question?.id ?? item.id)).filter(Boolean))
  const shuffled = shuffle([...new Map(rows.filter(item => item?.id).map(item => [String(item.id), item])).values()])
  const fresh = shuffled.filter(item => !previousIds.has(String(item.id)))
  const fallback = shuffled.filter(item => previousIds.has(String(item.id)))
  return [...fresh, ...fallback].slice(0, Math.min(10, total)).map(item => item.id)
}

async function startBankPractice(ids = bankSelection.value) {
  const questionIds = Array.isArray(ids) ? ids : []
  if (!questionIds.length || creatingSession.value) return
  creatingSession.value = true
  bank.error = ''
  try {
    const created = await createSession(questionIds, 'BANK', true, 'bank')
    if (created) bankSelection.value = []
  } catch (error) {
    bank.error = messageOf(error, '无法创建这轮练习，请稍后重试。')
  } finally {
    creatingSession.value = false
  }
}

async function startBankNewRound(request = {}) {
  await startBankPractice(request.questionIds)
}

async function startWrongPractice(ids = wrongSelection.value) {
  const questionIds = Array.isArray(ids) ? ids : []
  if (!questionIds.length || creatingSession.value) return
  creatingSession.value = true
  wrong.error = ''
  try {
    const created = await createSession(questionIds, 'WRONG', true, 'wrong')
    if (created) wrongSelection.value = []
  } catch (error) {
    wrong.error = messageOf(error, '无法创建错题复习，请稍后重试。')
  } finally {
    creatingSession.value = false
  }
}

async function startWrongNewRound(request = {}) {
  await startWrongPractice(request.questionIds)
}

async function startResultWrongPractice(ids) {
  if (!ids?.length || creatingSession.value) return
  creatingSession.value = true
  resultError.value = ''
  try {
    await createSession(ids, 'WRONG', true, 'result-wrong')
  } catch (error) {
    resultError.value = messageOf(error, '无法创建错题复习，请稍后重试。')
  } finally {
    creatingSession.value = false
  }
}

async function startResultNewRound(request = {}) {
  if (request.source === 'WRONG' && request.questionIds?.length) {
    await startResultWrongPractice(request.questionIds)
    return
  }
  await quickStart()
}

async function createSession(questionIds, source, skipLoading = false, origin = '') {
  if (!skipLoading) creatingSession.value = true
  try {
    const normalizedIds = [...new Set((questionIds || []).map(value => String(value)).filter(Boolean))]
    if (!normalizedIds.length) throw new Error('至少选择一道题后才能开始练习。')
    const existingSession = await refreshActiveSession()
    if (existingSession?.id) {
      pendingSessionRequest.value = {
        questionIds: normalizedIds,
        source,
        origin,
        activeSessionId: existingSession.id,
        activeSessionSnapshot: existingSession
      }
      replacementError.value = ''
      dialogMode.value = 'replace'
      return null
    }
    if (activeSessionState.value !== 'ready') {
      throw new Error(activeSessionError.value || '无法确认是否存在未完成练习，请重试。')
    }
    return await createFreshSession(normalizedIds, source, origin)
  } finally {
    if (!skipLoading) creatingSession.value = false
  }
}

async function createFreshSession(questionIds, source, origin = '') {
  const created = normalizeSession(dataOf(await eduApi.createQuizSession({ questionIds, source }), null))
  if (!created?.id) throw new Error('练习会话创建失败。')
  session.value = created
  activeSession.value = created
  activeSessionState.value = 'ready'
  activeSessionError.value = ''
  clearSelectionForOrigin(origin)
  await router.replace({ name: 'quiz', query: { view: 'practice', session: created.id } })
  return created
}

async function replaceActiveSession() {
  if (creatingSession.value || !pendingSessionRequest.value) return
  creatingSession.value = true
  replacementError.value = ''
  const request = pendingSessionRequest.value
  try {
    const current = activeSession.value?.id ? activeSession.value : await refreshActiveSession()
    if (current?.id && !request.activeSessionEnded) {
      const complete = sessionSubmitted(current) >= sessionTotal(current) && sessionTotal(current) > 0
      await eduApi.finishQuizSession(current.id, { abandon: !complete })
      request.activeSessionEnded = true
    }
    activeSession.value = null
    activeSessionState.value = 'ready'
    const created = await createFreshSession(request.questionIds, request.source, request.origin)
    pendingSessionRequest.value = null
    dialogMode.value = ''
    replacementError.value = ''
    return created
  } catch (error) {
    replacementError.value = messageOf(error, '无法结束当前练习并创建新一轮，请重试。')
  } finally {
    creatingSession.value = false
  }
}

function resumeFromReplacement() {
  const id = activeSession.value?.id
  pendingSessionRequest.value = null
  replacementError.value = ''
  dialogMode.value = ''
  if (id) router.push({ name: 'quiz', query: { view: 'practice', session: id } })
}

function cancelSessionReplacement() {
  pendingSessionRequest.value = null
  replacementError.value = ''
  dialogMode.value = ''
}

function clearSelectionForOrigin(origin) {
  if (origin === 'bank') bankSelection.value = []
  if (origin === 'wrong' || origin === 'result-wrong') wrongSelection.value = []
}

function resumeActiveSession() {
  const id = activeSession.value?.id
  if (id) router.push({ name: 'quiz', query: { view: 'practice', session: id } })
  else loadHome()
}

async function savePracticeItem(payload) {
  if (!session.value?.id || !payload.questionId) return
  const id = String(payload.questionId)
  const sessionId = session.value.id
  practiceError.value = ''
  practiceNavigationError.value = ''
  mergeSessionItem(id, { draftAnswer: payload.draftAnswer, flagged: payload.flagged })
  if (payload.currentPosition !== undefined && session.value) session.value = { ...session.value, currentPosition: payload.currentPosition }
  const saveRequest = eduApi.updateQuizSessionItem(sessionId, id, {
    draftAnswer: payload.draftAnswer,
    flagged: payload.flagged,
    currentPosition: payload.currentPosition
  }).then(response => {
    const returned = dataOf(response, null)
    applyItemResponse(id, returned, { draftAnswer: payload.draftAnswer, flagged: payload.flagged, currentPosition: payload.currentPosition })
    return true
  }).catch(error => {
    practiceError.value = messageOf(error, '草稿保存失败，请检查网络后重试。')
    return false
  }).finally(() => pendingItemSaves.delete(saveRequest))
  pendingItemSaves.add(saveRequest)
  return await saveRequest
}

async function updateSessionPosition(payload) {
  if (!session.value?.id || !payload?.questionId || payload.currentPosition === undefined) return
  const id = String(payload.questionId)
  practiceError.value = ''
  practiceNavigationError.value = ''
  session.value = { ...session.value, currentPosition: payload.currentPosition }
  try {
    const returned = dataOf(await eduApi.updateQuizSessionItem(session.value.id, id, { currentPosition: payload.currentPosition }), null)
    applyItemResponse(id, returned, { currentPosition: payload.currentPosition })
  } catch (error) {
    practiceError.value = messageOf(error, '当前位置保存失败，请稍后重试。')
  }
}

async function submitPracticeItem(payload) {
  if (!session.value?.id || !payload.questionId || busyQuestionIds.value.includes(String(payload.questionId))) return
  const id = String(payload.questionId)
  practiceError.value = ''
  practiceNavigationError.value = ''
  markBusy(id, true)
  try {
    const response = dataOf(await eduApi.submitQuizSessionItem(session.value.id, id, {
      userAnswer: payload.userAnswer,
      timeSpent: payload.timeSpent,
      idempotencyKey: createIdempotencyKey()
    }), {}) || {}
    feedbackByQuestion.value = { ...feedbackByQuestion.value, [id]: response }
    mergeSessionItem(id, {
      state: 'SUBMITTED',
      submittedAnswer: payload.userAnswer,
      submittedAt: response.submittedAt || new Date().toISOString(),
      timeSpentSec: payload.timeSpent,
      isCorrect: Object.prototype.hasOwnProperty.call(response, 'isCorrect') ? response.isCorrect : undefined,
      pendingReview: Boolean(response.pendingReview)
    })
  } catch (error) {
    practiceError.value = messageOf(error, '答案提交失败，请检查网络后重试。')
  } finally {
    markBusy(id, false)
  }
}

async function loadAiExplanation(item) {
  const id = String(item?.questionId || item?.question?.id || '')
  if (!id || !session.value?.id) return
  markBusy(id, true)
  practiceError.value = ''
  try {
    const currentFeedback = feedbackByQuestion.value[id] || {}
    const payload = dataOf(await eduApi.aiExplain({ questionId: id, userExplanation: item.submittedAnswer || item.draftAnswer || '', sessionId: session.value.id }), {}) || {}
    feedbackByQuestion.value = { ...feedbackByQuestion.value, [id]: { ...currentFeedback, explanation: payload.explanation || '暂时未能生成进一步讲解。' } }
  } catch (error) {
    practiceError.value = messageOf(error, '讲解生成失败，请稍后重试。')
  } finally {
    markBusy(id, false)
  }
}

function requestLeave() {
  practiceNavigationError.value = ''
  practicePanel.value?.flushDraft?.()
  dialogMode.value = 'leave'
}
function requestFinish() { practiceNavigationError.value = ''; practicePanel.value?.flushDraft?.(); dialogMode.value = 'finish' }
function closeDialog() {
  if (dialogMode.value === 'replace') {
    cancelSessionReplacement()
    return
  }
  dialogMode.value = ''
}
async function saveAndLeave() {
  if (finishingSession.value) return
  finishingSession.value = true
  practiceNavigationError.value = ''
  practicePanel.value?.flushDraft?.()
  if (!await waitForPendingItemSaves()) {
    practiceNavigationError.value = practiceError.value || '草稿尚未保存，暂时不能离开。'
    dialogMode.value = ''
    finishingSession.value = false
    return
  }
  closeDialog()
  await router.push({ name: 'quiz', query: { view: 'home' } })
  finishingSession.value = false
}

async function abandonSession() {
  if (!session.value?.id) return
  finishingSession.value = true
  practiceError.value = ''
  try {
    await eduApi.finishQuizSession(session.value.id, { abandon: true })
    activeSession.value = null
    session.value = null
    closeDialog()
    await router.push({ name: 'quiz', query: { view: 'home' } })
  } catch (error) {
    practiceError.value = messageOf(error, '无法放弃本轮练习，请稍后重试。')
    closeDialog()
  } finally {
    finishingSession.value = false
  }
}

async function finishSession() {
  if (!session.value?.id) return
  finishingSession.value = true
  practiceError.value = ''
  practiceNavigationError.value = ''
  const id = session.value.id
  try {
    if (!await waitForPendingItemSaves()) {
      practiceNavigationError.value = practiceError.value || '草稿尚未保存，暂时不能结束本轮。'
      return
    }
    await eduApi.finishQuizSession(id, { abandon: false })
    activeSession.value = null
    closeDialog()
    await router.push({ name: 'quiz', query: { view: 'result', session: id } })
  } catch (error) {
    practiceError.value = messageOf(error, '无法结束本轮练习，请稍后重试。')
    closeDialog()
  } finally {
    finishingSession.value = false
  }
}

async function waitForPendingItemSaves() {
  const saves = [...pendingItemSaves]
  if (!saves.length) return true
  const results = await Promise.all(saves)
  return !results.some(saved => saved === false)
}

function mergeSessionItem(questionId, patch) {
  if (!session.value) return
  session.value = {
    ...session.value,
    items: (session.value.items || []).map(item => String(item.questionId ?? item.question?.id ?? item.id) === String(questionId) ? { ...item, ...patch } : item)
  }
}

function applyItemResponse(questionId, response, fallback) {
  if (response?.items) {
    session.value = normalizeSession(response)
    return
  }
  mergeSessionItem(questionId, response && typeof response === 'object' ? { ...fallback, ...response } : fallback)
}

function markBusy(questionId, busy) {
  const id = String(questionId)
  busyQuestionIds.value = busy ? [...new Set([...busyQuestionIds.value, id])] : busyQuestionIds.value.filter(current => current !== id)
}

function guardPracticeNavigation(event) {
  const target = event.target instanceof Element ? event.target.closest('.number-button') : null
  if (!target || (event.type === 'keydown' && event.key !== 'Enter' && event.key !== ' ')) return

  const targetPosition = Number.parseInt(target.textContent || '', 10)
  if (!Number.isFinite(targetPosition) || canNavigateToPracticePosition(targetPosition)) return

  event.preventDefault()
  event.stopImmediatePropagation()
  practiceNavigationError.value = `请先提交第 ${currentPracticePosition()} 题，再继续下一题。`
}

function canNavigateToPracticePosition(targetPosition) {
  const currentPosition = currentPracticePosition()
  if (targetPosition <= currentPosition) return true
  if (targetPosition !== currentPosition + 1) return false
  const currentItem = (session.value?.items || []).find(item => Number(item.position) === currentPosition)
  return isPracticeItemSubmitted(currentItem)
}

function currentPracticePosition() {
  const position = Number(session.value?.currentPosition)
  return Number.isFinite(position) && position > 0 ? Math.floor(position) : 1
}

function isPracticeItemSubmitted(item) {
  return item?.state === 'SUBMITTED'
    || Boolean(item?.submittedAt)
    || (Object.prototype.hasOwnProperty.call(item || {}, 'submittedAnswer') && item.submittedAnswer !== null && item.submittedAnswer !== undefined)
}

function sessionTotal(value) {
  return Math.max(0, Number(value?.totalCount ?? value?.items?.length ?? 0) || 0)
}

function sessionSubmitted(value) {
  if (!value) return 0
  const items = Array.isArray(value.items) ? value.items : []
  const itemCount = items.filter(isPracticeItemSubmitted).length
  return Math.max(itemCount, Number(value.submittedCount || 0) || 0)
}

function normalizeSession(raw) {
  if (!raw || typeof raw !== 'object') return null
  const id = raw.id ?? raw.sessionId
  if (!id) return null
  return { ...raw, id: String(id), items: Array.isArray(raw.items) ? raw.items : [] }
}
function validTotal(value) { const total = Number(value); return Number.isFinite(total) && total >= 0 ? total : null }
function validRate(value) { if (value === null || value === undefined || value === '') return null; const rate = Number(value); return Number.isFinite(rate) && rate >= 0 && rate <= 100 ? rate : null }
function toggleId(ids, id) { const value = String(id); return ids.map(String).includes(value) ? ids.filter(current => String(current) !== value) : [...ids, id] }
function parseQuestionIds(value) { return String(value || '').split(',').map(item => item.trim()).filter(Boolean) }
function messageOf(error, fallback) { return error?.message || fallback }
function createIdempotencyKey() { return globalThis.crypto?.randomUUID?.() || `quiz-${Date.now()}-${Math.random().toString(36).slice(2)}` }
function randomInt(max) { return max > 1 ? Math.floor(Math.random() * max) : 0 }
function shuffle(values) {
  const result = [...values]
  for (let index = result.length - 1; index > 0; index -= 1) {
    const target = randomInt(index + 1)
    ;[result[index], result[target]] = [result[target], result[index]]
  }
  return result
}
</script>

<style scoped>
.quiz-module-page { max-width: 1500px; margin: 0 auto; }.quiz-module-shell { min-height: min(720px, calc(100dvh - var(--topbar-height) - 2 * var(--margin-desktop))); }.module-header { display: flex; justify-content: space-between; align-items: end; gap: 24px; margin: 2px 0 28px; }.module-header.home-header { width: min(100%, 1360px); margin-bottom: 12px; }.module-kicker { margin: 0 0 5px; color: var(--primary); font-size: 13px; line-height: 20px; font-weight: 800; }.module-header h1 { margin: 0; color: var(--on-surface); font-size: clamp(34px, 4vw, 48px); line-height: 1.1; letter-spacing: 0; }.module-header.home-header h1 { font-size: clamp(32px, 3.2vw, 42px); }.module-header > div > p:last-child { margin: 10px 0 0; color: var(--on-surface-variant); font-size: 15px; line-height: 24px; }.module-tabs { display: flex; align-items: center; gap: 4px; padding: 4px; border: 1px solid var(--outline-variant); border-radius: var(--radius-md); background: var(--surface-container-low); }.module-tab { min-height: 44px; display: inline-flex; align-items: center; gap: 6px; border: 0; border-radius: calc(var(--radius-md) - 4px); padding: 0 11px; color: var(--on-surface-variant); background: transparent; font: inherit; font-size: 14px; font-weight: 750; white-space: nowrap; cursor: pointer; }.module-tab:hover { color: var(--on-surface); background: var(--surface-container); }.module-tab.active { color: var(--primary); background: var(--surface-container-lowest); box-shadow: 0 1px 3px rgba(13, 28, 46, .1); }.module-tab .material-symbols-outlined { font-size: 19px; }.module-state { min-height: 440px; display: grid; place-items: center; align-content: center; gap: 10px; color: var(--on-surface-variant); text-align: center; }.module-state > .material-symbols-outlined { color: var(--error); font-size: 46px; }.module-state h1, .module-state p { margin: 0; }.module-state h1 { color: var(--on-surface); font-size: 25px; line-height: 32px; }.module-state p { max-width: 480px; font-size: 14px; line-height: 22px; }.state-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; margin-top: 4px; }.state-button { width: auto; padding: 0 16px; }
.quiz-dialog-scrim { position: fixed; inset: 0; z-index: 150; display: grid; place-items: center; padding: 24px; background: rgba(13, 28, 46, .36); }.quiz-dialog { width: min(420px, 100%); display: grid; justify-items: center; gap: 12px; padding: 26px; border: 1px solid var(--outline-variant); border-radius: var(--radius-lg); color: var(--on-surface); background: var(--surface-container-lowest); box-shadow: 0 24px 58px rgba(13, 28, 46, .26); text-align: center; }.dialog-icon { width: 44px; height: 44px; display: grid; place-items: center; border-radius: 12px; color: var(--primary); background: var(--primary-fixed); font-size: 25px; }.quiz-dialog h2, .quiz-dialog p { margin: 0; }.quiz-dialog h2 { font-size: 21px; line-height: 29px; }.quiz-dialog p { color: var(--on-surface-variant); font-size: 14px; line-height: 22px; }.dialog-actions { width: 100%; display: flex; justify-content: center; gap: 10px; margin-top: 6px; }.dialog-actions.stacked { display: grid; }.dialog-button { width: auto; padding: 0 16px; }.dialog-actions.stacked .dialog-button { width: 100%; }.text-dialog-button { min-height: 38px; border: 0; color: var(--on-surface-variant); background: transparent; font: inherit; font-size: 14px; font-weight: 750; cursor: pointer; }.text-dialog-button:hover { color: var(--primary); }
.replace-session-facts { width: 100%; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin: 2px 0; }
.replace-session-facts div { padding: 10px; border-radius: var(--radius-md); background: var(--surface-container-low); }
.replace-session-facts dt { color: var(--on-surface-variant); font-size: 12px; line-height: 18px; }
.replace-session-facts dd { margin: 2px 0 0; color: var(--on-surface); font-size: 14px; line-height: 20px; font-weight: 800; }
.dialog-error { width: 100%; color: var(--error) !important; }
@media (max-width: 860px) { .module-header { align-items: flex-start; flex-direction: column; }.module-tabs { width: 100%; overflow-x: auto; }.module-tab { flex: 1 0 auto; justify-content: center; } }
@media (max-width: 620px) { .module-header { margin-bottom: 20px; }.module-header h1 { font-size: 34px; }.module-tabs { padding: 3px; }.module-tab { min-height: 44px; padding: 0 9px; font-size: 13px; }.module-tab .material-symbols-outlined { font-size: 18px; }.quiz-dialog-scrim { align-items: end; padding: 12px; }.quiz-dialog { width: 100%; box-sizing: border-box; }.dialog-actions:not(.stacked) { display: grid; }.dialog-button { width: 100%; } }
</style>
