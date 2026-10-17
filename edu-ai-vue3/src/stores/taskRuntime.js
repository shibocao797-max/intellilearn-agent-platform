import { reactive, readonly } from 'vue'
import { eduApi } from '@/services/api'

const POLL_INTERVAL_MS = 1200
const ACTIVE_REFRESH_INTERVAL_MS = 12000
const MAX_RECONNECT_DELAY_MS = 10000
const MAX_TASKS = 100
const MAX_EVENTS_PER_TASK = 240
const MAX_RESOURCES_PER_TASK = 100

const ACTIVE_STATUSES = new Set([
  'CREATED',
  'ACCEPTED',
  'PENDING',
  'QUEUED',
  'RUNNING',
  'PROCESSING',
  'IN_PROGRESS',
  'CANCELLING'
])
const TERMINAL_STATUSES = new Set(['SUCCEEDED', 'COMPLETED', 'SUCCESS', 'DONE', 'FAILED', 'ERROR', 'CANCELLED'])

const state = reactive({
  initialized: false,
  initializing: false,
  activeSessionId: '',
  connectionStatus: 'idle',
  lastConnectionError: '',
  tasks: {},
  taskIdsBySession: {},
  focusedTaskIdsBySession: {}
})

const pollTimers = new Map()
const pollPromises = new Map()
const eventSequencesByTask = new Map()
const eventSignaturesByTask = new Map()
const reconnectAttemptsByTask = new Map()
const latestStartRequestOrderBySession = new Map()
let activeRefreshTimer = null
let initializePromise = null
let taskOrder = 0
let startRequestOrder = 0
let runtimeGeneration = 0
let retainedTaskId = ''

function useTaskRuntimeStore() {
  return {
    state: readonly(state),
    taskForSession,
    focusTask,
    retainTask,
    startTask,
    refreshActiveTasks,
    pollTask,
    cancelTask,
    initialize,
    dispose
  }
}

function taskForSession(sessionId) {
  const normalizedSessionId = textValue(sessionId)
  if (!normalizedSessionId) return null

  const ids = state.taskIdsBySession[normalizedSessionId] || []
  const focusedTaskId = state.focusedTaskIdsBySession[normalizedSessionId]
  const focusedTask = focusedTaskId ? state.tasks[focusedTaskId] : null
  if (focusedTask && focusedTask.sessionId === normalizedSessionId) return focusedTask

  return ids
    .map(taskId => state.tasks[taskId])
    .filter(Boolean)
    .sort(compareTasks)[0] || null
}

function focusTask(taskId) {
  const normalizedTaskId = textValue(taskId)
  const task = normalizedTaskId ? state.tasks[normalizedTaskId] : null
  if (!task?.sessionId) return null

  state.focusedTaskIdsBySession[task.sessionId] = task.taskId
  state.activeSessionId = task.sessionId
  return task
}

function retainTask(taskId) {
  const normalizedTaskId = textValue(taskId)
  const task = normalizedTaskId ? state.tasks[normalizedTaskId] : null
  if (!task) return null

  retainedTaskId = task.taskId
  return task
}

async function initialize(options = {}) {
  const generation = runtimeGeneration
  const sessionId = resolveSessionIdOption(options)
  if (sessionId) state.activeSessionId = sessionId

  if (initializePromise) {
    await initializePromise
    if (!isRuntimeGenerationActive(generation)) return
    if (sessionId) await refreshActiveTasks(sessionId)
    return
  }

  if (state.initialized) {
    if (!isRuntimeGenerationActive(generation)) return
    if (sessionId) await refreshActiveTasks(sessionId)
    return
  }

  state.initializing = true
  state.connectionStatus = 'connecting'
  initializePromise = (async () => {
    await refreshActiveTasks(null, generation)
    if (!isRuntimeGenerationActive(generation)) return
    configureActiveRefresh(generation)
    state.initialized = true
  })().finally(() => {
    if (runtimeGeneration === generation) {
      state.initializing = false
      initializePromise = null
    }
  })

  await initializePromise
}

async function startTask(payload) {
  const generation = runtimeGeneration
  const requestPayload = payload && typeof payload === 'object' ? { ...payload } : {}
  requestPayload.clientRequestId = textValue(requestPayload.clientRequestId) || createClientRequestId()
  const sessionId = textValue(requestPayload.sessionId)
  const requestOrder = ++startRequestOrder
  if (sessionId) {
    state.activeSessionId = sessionId
    latestStartRequestOrderBySession.set(sessionId, requestOrder)
  }

  state.connectionStatus = 'connecting'
  state.lastConnectionError = ''

  try {
    const response = await eduApi.startTask(requestPayload)
    if (!isRuntimeGenerationActive(generation)) return null
    const snapshot = extractTaskSnapshot(response)
    const taskId = textValue(snapshot?.taskId || snapshot?.id || extractData(response)?.taskId)
    if (!taskId) throw new Error('服务端未返回 taskId，无法跟踪任务。')

    const task = mergeTaskSnapshot({
      ...snapshot,
      taskId,
      sessionId: textValue(snapshot?.sessionId) || sessionId,
      status: snapshot?.status || 'PENDING',
      currentPhase: snapshot?.currentPhase || snapshot?.phase || 'START'
    })
    task._startRequestOrder = requestOrder
    if (!sessionId || latestStartRequestOrderBySession.get(sessionId) === requestOrder) {
      focusTask(taskId)
    }
    markConnected(task)
    scheduleTaskPoll(taskId, 0, { generation })
    return task
  } catch (error) {
    if (!isRuntimeGenerationActive(generation)) return null
    markReconnecting(null, error)
    throw error
  }
}

async function refreshActiveTasks(sessionId = state.activeSessionId, generation = runtimeGeneration) {
  const normalizedSessionId = textValue(sessionId)
  if (!isRuntimeGenerationActive(generation)) return []
  if (normalizedSessionId) state.activeSessionId = normalizedSessionId
  try {
    const response = await eduApi.activeTasks(normalizedSessionId || undefined)
    if (!isRuntimeGenerationActive(generation)) return []
    const snapshots = extractTaskSnapshots(response)
    const tasks = snapshots
      .map(snapshot => mergeTaskSnapshot({ ...snapshot, sessionId: textValue(snapshot?.sessionId) || normalizedSessionId }, { includeContent: false }))
      .filter(Boolean)

    markConnected()
    tasks.forEach(task => {
      if (isTerminalTask(task)) {
        if (task._finalized) {
          stopTaskPolling(task.taskId)
        } else {
          task._finalizationPending = true
          scheduleTaskPoll(task.taskId, 0, { allowTerminal: true, generation })
        }
      } else {
        scheduleTaskPoll(task.taskId, 0, { generation })
      }
    })
    return tasks
  } catch (error) {
    if (!isRuntimeGenerationActive(generation)) return []
    markReconnecting(null, error)
    return []
  }
}

async function pollTask(taskId, expectedGeneration = runtimeGeneration) {
  const normalizedTaskId = textValue(taskId)
  if (!normalizedTaskId || !isRuntimeGenerationActive(expectedGeneration)) return null
  if (pollPromises.has(normalizedTaskId)) return pollPromises.get(normalizedTaskId)

  stopTaskPolling(normalizedTaskId)
  const promise = performTaskPoll(normalizedTaskId, expectedGeneration).finally(() => {
    if (runtimeGeneration === expectedGeneration) pollPromises.delete(normalizedTaskId)
  })
  pollPromises.set(normalizedTaskId, promise)
  return promise
}

async function performTaskPoll(taskId, generation = runtimeGeneration) {
  if (!isRuntimeGenerationActive(generation)) return null
  let task = state.tasks[taskId] || createTask({ taskId })

  try {
    const snapshotResponse = await eduApi.task(taskId)
    if (!isRuntimeGenerationActive(generation)) return null
    const snapshot = extractTaskSnapshot(snapshotResponse)
    if (snapshot) task = mergeTaskSnapshot({ ...snapshot, taskId }, { includeContent: false })

    const afterSeq = Number(task.lastSequence || 0)
    const eventsResponse = await eduApi.taskEvents(taskId, afterSeq)
    if (!isRuntimeGenerationActive(generation)) return null
    const events = extractEvents(eventsResponse)
    ingestEvents(task, events)
    applySnapshotContentFallback(task, snapshot)
    markConnected(task)

    if (isTerminalTask(task)) {
      task._finalizationPending = true
      const finalized = await refreshFinalSnapshot(taskId, generation)
      if (!isRuntimeGenerationActive(generation)) return task
      if (finalized) {
        task._finalizationPending = false
        stopTaskPolling(taskId)
      }
    } else {
      task._finalized = false
      task._finalizationPending = false
      scheduleTaskPoll(taskId, POLL_INTERVAL_MS, { generation })
    }
    return task
  } catch (error) {
    if (!isRuntimeGenerationActive(generation)) return null
    markReconnecting(task, error)
    task._finalizationPending = isTerminalTask(task)
    scheduleTaskPoll(taskId, nextReconnectDelay(taskId), {
      allowTerminal: isTerminalTask(task),
      generation
    })
    return task
  }
}

async function cancelTask(taskId) {
  const generation = runtimeGeneration
  const normalizedTaskId = textValue(taskId)
  if (!normalizedTaskId || !isRuntimeGenerationActive(generation)) return null

  const existingTask = state.tasks[normalizedTaskId]
  if (existingTask && isTerminalTask(existingTask) && existingTask._finalized) return existingTask

  try {
    const response = await eduApi.cancelTask(normalizedTaskId)
    if (!isRuntimeGenerationActive(generation)) return null
    const snapshot = extractTaskSnapshot(response)
    const task = mergeTaskSnapshot({
      ...(snapshot || {}),
      taskId: normalizedTaskId,
      sessionId: textValue(snapshot?.sessionId) || existingTask?.sessionId || '',
      status: snapshot?.status || 'CANCELLED',
      currentPhase: snapshot?.currentPhase || snapshot?.phase || 'CANCELLED'
    })
    markConnected(task)
    if (isTerminalTask(task)) {
      task._finalizationPending = true
      const finalized = await refreshFinalSnapshot(normalizedTaskId, generation)
      if (finalized) stopTaskPolling(normalizedTaskId)
    } else {
      scheduleTaskPoll(normalizedTaskId, 0, { generation })
    }
    return task
  } catch (error) {
    if (!isRuntimeGenerationActive(generation)) return null
    markReconnecting(existingTask, error)
    throw error
  }
}

function dispose() {
  runtimeGeneration += 1
  if (activeRefreshTimer) window.clearInterval(activeRefreshTimer)
  activeRefreshTimer = null
  pollTimers.forEach(timer => window.clearTimeout(timer))
  pollTimers.clear()
  pollPromises.clear()
  eventSequencesByTask.clear()
  eventSignaturesByTask.clear()
  reconnectAttemptsByTask.clear()
  latestStartRequestOrderBySession.clear()
  initializePromise = null
  taskOrder = 0
  startRequestOrder = 0
  retainedTaskId = ''
  state.initialized = false
  state.initializing = false
  state.activeSessionId = ''
  state.connectionStatus = 'idle'
  state.lastConnectionError = ''
  state.tasks = {}
  state.taskIdsBySession = {}
  state.focusedTaskIdsBySession = {}
}

function configureActiveRefresh(generation = runtimeGeneration) {
  if (activeRefreshTimer) window.clearInterval(activeRefreshTimer)
  activeRefreshTimer = window.setInterval(() => {
    if (isRuntimeGenerationActive(generation) && state.activeSessionId) {
      refreshActiveTasks(state.activeSessionId, generation)
    }
  }, ACTIVE_REFRESH_INTERVAL_MS)
}

function scheduleTaskPoll(taskId, delay, options = {}) {
  const generation = options.generation ?? runtimeGeneration
  const task = state.tasks[taskId]
  if (!isRuntimeGenerationActive(generation)) return
  if (task && isTerminalTask(task) && !options.allowTerminal) return
  stopTaskPolling(taskId)
  pollTimers.set(taskId, window.setTimeout(() => {
    if (isRuntimeGenerationActive(generation)) pollTask(taskId, generation)
  }, Math.max(0, delay)))
}

function stopTaskPolling(taskId) {
  const timer = pollTimers.get(taskId)
  if (timer) window.clearTimeout(timer)
  pollTimers.delete(taskId)
}

async function refreshFinalSnapshot(taskId, generation = runtimeGeneration) {
  if (!isRuntimeGenerationActive(generation)) return false
  try {
    const response = await eduApi.task(taskId)
    if (!isRuntimeGenerationActive(generation)) return false
    const snapshot = extractTaskSnapshot(response)
    if (!snapshot) throw new Error('暂时无法获取任务最终状态。')
    const task = mergeTaskSnapshot({ ...snapshot, taskId })
    if (!task) throw new Error('暂时无法获取任务最终状态。')

    // The final snapshot and event stream are separate requests. Re-read the tail
    // here so direct cancellation responses cannot hide the terminal event.
    const eventsResponse = await eduApi.taskEvents(taskId, Number(task.lastSequence || 0))
    if (!isRuntimeGenerationActive(generation)) return false
    ingestEvents(task, extractEvents(eventsResponse))
    applySnapshotContentFallback(task, snapshot)
    markConnected(task)
    if (isTerminalTask(task)) {
      task._finalized = true
      task._finalizationPending = false
      stopTaskPolling(taskId)
      return true
    }

    task._finalized = false
    task._finalizationPending = false
    scheduleTaskPoll(taskId, POLL_INTERVAL_MS, { generation })
    return false
  } catch (error) {
    if (!isRuntimeGenerationActive(generation)) return false
    const task = state.tasks[taskId]
    if (task) {
      task._finalized = false
      task._finalizationPending = true
    }
    markReconnecting(task, error)
    scheduleTaskPoll(taskId, nextReconnectDelay(taskId), { allowTerminal: true, generation })
    return false
  }
}

function mergeTaskSnapshot(snapshot, options = {}) {
  if (!snapshot || typeof snapshot !== 'object') return null
  const taskId = textValue(snapshot.taskId || snapshot.id)
  if (!taskId) return null

  const existing = state.tasks[taskId]
  const task = existing || createTask({ taskId })
  const previousSessionId = task.sessionId
  const nextSessionId = textValue(snapshot.sessionId || snapshot.conversationId) || previousSessionId

  task.taskId = taskId
  task.sessionId = nextSessionId
  const result = normalizeObject(snapshot.result) || {}
  const currentStatus = normalizeStatus(task.status)
  const snapshotStatus = normalizeStatus(snapshot.status || snapshot.taskStatus || currentStatus)
  let nextStatus = snapshot.cancelRequested === true && ACTIVE_STATUSES.has(snapshotStatus)
    ? 'CANCELLING'
    : snapshotStatus
  if (TERMINAL_STATUSES.has(currentStatus) && !TERMINAL_STATUSES.has(nextStatus)) {
    nextStatus = currentStatus
  } else if (currentStatus === 'CANCELLING' && ACTIVE_STATUSES.has(nextStatus)) {
    nextStatus = 'CANCELLING'
  }
  task.status = nextStatus
  if (!isTerminalTask(task)) {
    task._finalized = false
    task._finalizationPending = false
  }
  task.currentPhase = textValue(snapshot.currentPhase || snapshot.phase || result.currentPhase) || task.currentPhase
  task.selectedAgents = normalizeAgentList(snapshot.selectedAgents ?? result.selectedAgents ?? task.selectedAgents)
  task.progressByAgent = normalizeProgressMap(snapshot.progressByAgent ?? snapshot.agentProgress ?? result.agentStatuses ?? task.progressByAgent)
  task.error = normalizeError(snapshot.error ?? snapshot.errorMessage ?? task.error)
  task.intent = normalizeObject(snapshot.intent) || normalizeObject(result.intent) || result.intent || task.intent
  task.intentReason = textValue(snapshot.intentReason || snapshot.recommendReason || snapshot.reason || result.recommendReason) || task.intentReason
  task.createdAt = snapshot.createdAt || snapshot.createTime || task.createdAt
  task.updatedAt = snapshot.updatedAt || snapshot.updateTime || new Date().toISOString()
  task.connectionStatus = 'connected'

  if (options.includeContent !== false) {
    const snapshotText = extractSnapshotText(snapshot) || extractSnapshotText(result)
    if (snapshotText) task.text = snapshotText
    normalizeResourceList(snapshot.resources ?? result.generatedResources ?? result.activeResources).forEach(resource => addResource(task, resource))
  }

  if (!existing) state.tasks[taskId] = task
  registerTaskForSession(task, previousSessionId)
  pruneTaskCache()
  return task
}

function createTask(seed = {}) {
  const task = {
    taskId: textValue(seed.taskId),
    sessionId: textValue(seed.sessionId),
    status: normalizeStatus(seed.status || 'PENDING'),
    currentPhase: textValue(seed.currentPhase),
    selectedAgents: [],
    progressByAgent: {},
    events: [],
    text: '',
    resources: [],
    error: '',
    intent: null,
    intentReason: '',
    connectionStatus: 'connecting',
    lastSequence: 0,
    createdAt: seed.createdAt || new Date().toISOString(),
    updatedAt: seed.updatedAt || new Date().toISOString(),
    _finalized: false,
    _finalizationPending: false,
    _order: ++taskOrder
  }
  if (task.taskId) state.tasks[task.taskId] = task
  registerTaskForSession(task)
  return task
}

function registerTaskForSession(task, previousSessionId = '') {
  if (previousSessionId && previousSessionId !== task.sessionId) {
    state.taskIdsBySession[previousSessionId] = (state.taskIdsBySession[previousSessionId] || []).filter(id => id !== task.taskId)
  }
  if (!task.sessionId) return

  const ids = state.taskIdsBySession[task.sessionId] || []
  state.taskIdsBySession[task.sessionId] = [task.taskId, ...ids.filter(id => id !== task.taskId)]
}

function trimTaskEvents(task, sequenceSet, signatureSet) {
  if (task.events.length <= MAX_EVENTS_PER_TASK) return

  task.events.splice(0, task.events.length - MAX_EVENTS_PER_TASK)
  sequenceSet.clear()
  signatureSet.clear()
  task.events.forEach(event => {
    if (Number.isFinite(event.sequence) && event.sequence > 0) sequenceSet.add(event.sequence)
    signatureSet.add(eventSignature(event))
  })
}

function pruneTaskCache() {
  const taskIds = Object.keys(state.tasks)
  if (taskIds.length <= MAX_TASKS) return

  const removable = Object.values(state.tasks)
    .filter(task => !isTaskProtected(task))
    .sort((a, b) => compareTasks(b, a))

  while (Object.keys(state.tasks).length > MAX_TASKS && removable.length) {
    removeTaskFromCache(removable.shift().taskId)
  }
}

function isTaskProtected(task) {
  const isCurrentFocusedTask = task?.sessionId === state.activeSessionId
    && state.focusedTaskIdsBySession[task.sessionId] === task.taskId
  return Boolean(
    task && (
      isCurrentFocusedTask
      || retainedTaskId === task.taskId
      || ACTIVE_STATUSES.has(normalizeStatus(task.status))
      || pollTimers.has(task.taskId)
      || pollPromises.has(task.taskId)
    )
  )
}

function removeTaskFromCache(taskId) {
  const task = state.tasks[taskId]
  if (!task) return

  stopTaskPolling(taskId)
  delete state.tasks[taskId]
  eventSequencesByTask.delete(taskId)
  eventSignaturesByTask.delete(taskId)
  reconnectAttemptsByTask.delete(taskId)
  if (state.focusedTaskIdsBySession[task.sessionId] === taskId) {
    delete state.focusedTaskIdsBySession[task.sessionId]
  }
  if (retainedTaskId === taskId) retainedTaskId = ''
  Object.keys(state.taskIdsBySession).forEach(sessionId => {
    state.taskIdsBySession[sessionId] = state.taskIdsBySession[sessionId].filter(id => id !== taskId)
    if (!state.taskIdsBySession[sessionId].length) {
      delete state.taskIdsBySession[sessionId]
      latestStartRequestOrderBySession.delete(sessionId)
    }
  })
}

function ingestEvents(task, rawEvents) {
  if (!task || !Array.isArray(rawEvents) || !rawEvents.length) return

  const sequenceSet = eventSequencesByTask.get(task.taskId) || new Set(task.events.map(event => event.sequence).filter(Number.isFinite))
  const signatureSet = eventSignaturesByTask.get(task.taskId) || new Set(task.events.map(eventSignature))
  eventSequencesByTask.set(task.taskId, sequenceSet)
  eventSignaturesByTask.set(task.taskId, signatureSet)

  const normalizedEvents = rawEvents
    .map((event, index) => normalizeEvent(event, task, index))
    .filter(Boolean)
    .sort((a, b) => a.sequence - b.sequence)

  normalizedEvents.forEach(event => {
    const hasSequence = Number.isFinite(event.sequence) && event.sequence > 0
    const signature = eventSignature(event)
    if ((hasSequence && sequenceSet.has(event.sequence)) || (!hasSequence && signatureSet.has(signature))) return

    if (hasSequence) sequenceSet.add(event.sequence)
    signatureSet.add(signature)
    task.events.push(event)
    task.lastSequence = Math.max(task.lastSequence, hasSequence ? event.sequence : 0)
    applyEvent(task, event)
  })

  task.events.sort((a, b) => a.sequence - b.sequence)
  trimTaskEvents(task, sequenceSet, signatureSet)
  task.updatedAt = task.events.at(-1)?.timestamp || task.updatedAt
}

function applyEvent(task, event) {
  const payload = event.payload
  switch (event.eventName) {
    case 'phase': {
      const phase = textValue(payload?.phase || payload?.currentPhase || payload)
      if (phase) task.currentPhase = phase
      break
    }
    case 'intent': {
      task.intent = normalizeObject(payload) || payload
      task.intentReason = textValue(payload?.recommendReason || payload?.reason || task.intentReason)
      break
    }
    case 'selectedAgents':
      task.selectedAgents = normalizeAgentList(payload?.selectedAgents ?? payload?.agents ?? payload)
      break
    case 'agentProgress':
      applyAgentProgress(task, payload)
      break
    case 'token':
      task.text += extractTokenText(payload)
      break
    case 'resource':
    case 'revisionResource':
      addResource(task, payload?.resource || payload, event.eventName)
      break
    case 'review':
      applyResourceReview(task, payload)
      break
    case 'done':
      task.status = normalizeStatus(event.status || payload?.status || 'SUCCEEDED')
      task.currentPhase = textValue(payload?.currentPhase || payload?.phase) || 'DONE'
      if (payload && typeof payload === 'object') {
        normalizeResourceList(payload.resources).forEach(resource => addResource(task, resource))
        if (!task.text) task.text = extractSnapshotText(payload)
      }
      break
    case 'error':
      task.status = normalizeStatus(event.status || 'FAILED')
      task.currentPhase = textValue(payload?.currentPhase || payload?.phase) || 'FAILED'
      task.error = normalizeError(payload) || '任务执行失败。'
      break
    case 'cancelRequested':
      if (!isTerminalTask(task)) task.status = 'CANCELLING'
      break
    default:
      break
  }
}

function applyAgentProgress(task, payload) {
  const rows = Array.isArray(payload) ? payload : [payload]
  rows.filter(row => row && typeof row === 'object').forEach(row => {
    const agentName = textValue(row.agentName || row.agent || row.name || row.key)
    if (!agentName) return
    task.progressByAgent[agentName] = { ...(task.progressByAgent[agentName] || {}), ...row, agentName }
  })
}

function applyResourceReview(task, payload) {
  if (!task || !payload || typeof payload !== 'object') return

  const resourceRef = textValue(
    payload.persistedId
      || payload.resourceId
      || payload.resourceRef?.persistedId
      || payload.resourceRef?.resourceId
  )
  const agentName = textValue(payload.agentName || payload.resourceRef?.agentName)
  const index = task.resources.findIndex(resource => {
    const persistedId = textValue(resource.persistedId || resource.resourceId || resource.id)
    return (resourceRef && persistedId === resourceRef)
      || (agentName && textValue(resource.agentName) === agentName && !resourceRef)
  })
  if (index < 0) return

  const passed = payload.passed === true || String(payload.passed || '').toLowerCase() === 'true'
  task.resources[index] = {
    ...task.resources[index],
    reviewStatus: passed ? 'published' : 'rejected',
    reviewDecision: textValue(payload.decision),
    reviewPassed: passed,
    ...(payload.score !== undefined ? { reviewScore: payload.score } : {}),
    ...(payload.issues !== undefined ? { reviewIssues: payload.issues } : {})
  }
}

function addResource(task, resource, eventName = '') {
  if (!resource || typeof resource !== 'object') return
  const normalized = { ...resource }
  if (eventName) normalized.eventName = eventName
  const identity = textValue(normalized.resourceId || normalized.persistedId || normalized.id || normalized.url || normalized.fileUrl)
  const existingIndex = identity
    ? task.resources.findIndex(item => textValue(item.resourceId || item.id || item.url || item.fileUrl) === identity)
    : -1
  if (existingIndex >= 0) {
    task.resources[existingIndex] = { ...task.resources[existingIndex], ...normalized }
  } else {
    task.resources.push(normalized)
  }
  if (task.resources.length > MAX_RESOURCES_PER_TASK) {
    task.resources.splice(0, task.resources.length - MAX_RESOURCES_PER_TASK)
  }
}

function applySnapshotContentFallback(task, snapshot) {
  if (!snapshot || typeof snapshot !== 'object') return
  if (!task.text) task.text = extractSnapshotText(snapshot)
  if (!task.resources.length) normalizeResourceList(snapshot.resources).forEach(resource => addResource(task, resource))
}

function normalizeEvent(rawEvent, task, index) {
  if (!rawEvent || typeof rawEvent !== 'object') return null
  const eventName = canonicalEventName(rawEvent.eventName || rawEvent.eventType || rawEvent.type || rawEvent.name)
  if (!eventName) return null

  const rawSequence = Number(rawEvent.sequence ?? rawEvent.seq ?? rawEvent.eventSeq)
  const sequence = Number.isFinite(rawSequence) && rawSequence > 0
    ? rawSequence
    : Number(task.lastSequence || 0) + index + 1
  let payload = parseJsonValue(rawEvent.payload ?? rawEvent.data ?? rawEvent.content ?? rawEvent.body)
  if (eventName === 'agentProgress' && payload && typeof payload === 'object' && !payload.agentName && rawEvent.agentKey) {
    payload = { ...payload, agentName: rawEvent.agentKey }
  }
  return {
    id: textValue(rawEvent.eventId || rawEvent.id) || `${task.taskId}-${sequence}-${eventName}`,
    taskId: task.taskId,
    sequence,
    eventName,
    payload,
    agentKey: textValue(rawEvent.agentKey),
    status: normalizeStatus(rawEvent.status || ''),
    timestamp: rawEvent.timestamp || rawEvent.occurredTime || rawEvent.createdAt || rawEvent.createTime || new Date().toISOString()
  }
}

function extractTaskSnapshots(response) {
  const data = extractData(response)
  if (Array.isArray(data)) return data
  if (!data || typeof data !== 'object') return []

  for (const key of ['tasks', 'items', 'rows', 'list', 'records']) {
    if (Array.isArray(data[key])) return data[key]
  }
  const snapshot = extractTaskSnapshot(data)
  return snapshot?.taskId || snapshot?.id ? [snapshot] : []
}

function extractTaskSnapshot(response) {
  const data = extractData(response)
  if (!data || typeof data !== 'object' || Array.isArray(data)) return null
  if (data.task && typeof data.task === 'object') return extractData(data.task)
  if (data.activeTask && typeof data.activeTask === 'object') return extractData(data.activeTask)
  return data
}

function extractEvents(response) {
  const data = extractData(response)
  if (Array.isArray(data)) return data
  if (!data || typeof data !== 'object') return []
  for (const key of ['events', 'items', 'rows', 'list', 'records']) {
    if (Array.isArray(data[key])) return data[key]
  }
  return data.eventName || data.eventType || data.type ? [data] : []
}

function extractData(value) {
  let current = value
  for (let depth = 0; depth < 4; depth += 1) {
    if (!current || typeof current !== 'object' || Array.isArray(current) || !Object.prototype.hasOwnProperty.call(current, 'data')) break
    const next = current.data
    if (next === undefined || next === null || next === current) break
    current = next
  }
  return current
}

function normalizeStatus(value) {
  const status = textValue(value).toUpperCase().replace(/[\s-]+/g, '_')
  if (!status) return 'PENDING'
  if (['CANCELED', 'CANCELLED'].includes(status)) return 'CANCELLED'
  if (['SUCCEEDED', 'SUCCESS', 'COMPLETED', 'DONE'].includes(status)) return 'SUCCEEDED'
  if (['FAILED', 'ERROR'].includes(status)) return 'FAILED'
  if (['INPROGRESS', 'IN_PROGRESS'].includes(status)) return 'IN_PROGRESS'
  return status
}

function normalizeAgentList(value) {
  const parsed = parseJsonValue(value)
  const rows = Array.isArray(parsed)
    ? parsed
    : Array.isArray(parsed?.selectedAgents)
      ? parsed.selectedAgents
      : Array.isArray(parsed?.agents)
        ? parsed.agents
        : []
  return [...new Set(rows.map(item => textValue(item?.agentName || item?.name || item)).filter(Boolean))]
}

function normalizeProgressMap(value) {
  const parsed = parseJsonValue(value)
  if (Array.isArray(parsed)) {
    return parsed.reduce((acc, row) => {
      const key = textValue(row?.agentName || row?.agent || row?.name || row?.key)
      if (key) acc[key] = { ...row, agentName: key }
      return acc
    }, {})
  }
  if (!parsed || typeof parsed !== 'object') return {}
  return Object.entries(parsed).reduce((acc, [key, row]) => {
    const agentName = textValue(row?.agentName || key)
    if (agentName) acc[agentName] = row && typeof row === 'object' ? { ...row, agentName } : { agentName, status: row }
    return acc
  }, {})
}

function normalizeResourceList(value) {
  const parsed = parseJsonValue(value)
  return Array.isArray(parsed) ? parsed.filter(item => item && typeof item === 'object') : []
}

function normalizeObject(value) {
  const parsed = parseJsonValue(value)
  return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
}

function parseJsonValue(value) {
  let parsed = value
  for (let depth = 0; depth < 2 && typeof parsed === 'string'; depth += 1) {
    const trimmed = parsed.trim()
    if (!trimmed || (!trimmed.startsWith('{') && !trimmed.startsWith('[') && !trimmed.startsWith('"'))) break
    try {
      parsed = JSON.parse(trimmed)
    } catch {
      break
    }
  }
  return parsed
}

function canonicalEventName(value) {
  const original = textValue(value)
  const key = original.replace(/[-_\s]/g, '').toLowerCase()
  const names = {
    phase: 'phase',
    intent: 'intent',
    selectedagents: 'selectedAgents',
    agentprogress: 'agentProgress',
    token: 'token',
    resource: 'resource',
    revisionresource: 'revisionResource',
    done: 'done',
    error: 'error'
  }
  return names[key] || original
}

function extractTokenText(payload) {
  if (typeof payload === 'string') return payload
  return textValue(payload?.token ?? payload?.text ?? payload?.content ?? payload?.delta)
}

function extractSnapshotText(snapshot) {
  return textValue(snapshot?.text ?? snapshot?.textReply ?? snapshot?.responseText ?? snapshot?.answer ?? snapshot?.content)
}

function normalizeError(value) {
  if (!value) return ''
  if (typeof value === 'string') return value.trim()
  return textValue(value.message || value.msg || value.errorMessage || value.detail)
}

function isTerminalTask(task) {
  return Boolean(task && TERMINAL_STATUSES.has(normalizeStatus(task.status)))
}

function markConnected(task) {
  if (task) {
    task.connectionStatus = 'connected'
    reconnectAttemptsByTask.delete(task.taskId)
  }
  const hasReconnectingTask = Object.values(state.tasks)
    .some(item => ACTIVE_STATUSES.has(normalizeStatus(item.status)) && item.connectionStatus === 'reconnecting')
  state.connectionStatus = hasReconnectingTask ? 'reconnecting' : 'connected'
  if (!hasReconnectingTask) state.lastConnectionError = ''
}

function markReconnecting(task, error) {
  const message = error?.message || '暂时无法连接任务服务。'
  state.connectionStatus = 'reconnecting'
  state.lastConnectionError = message
  if (task) task.connectionStatus = 'reconnecting'
}

function nextReconnectDelay(taskId) {
  const attempts = (reconnectAttemptsByTask.get(taskId) || 0) + 1
  reconnectAttemptsByTask.set(taskId, attempts)
  return Math.min(POLL_INTERVAL_MS * (2 ** Math.min(attempts, 4)), MAX_RECONNECT_DELAY_MS)
}

function isRuntimeGenerationActive(generation) {
  return generation === runtimeGeneration
}

function resolveSessionIdOption(options) {
  if (typeof options === 'string') return textValue(options)
  return textValue(options?.sessionId)
}

function compareTasks(a, b) {
  const timeDifference = Date.parse(b.updatedAt || b.createdAt || '') - Date.parse(a.updatedAt || a.createdAt || '')
  return Number.isFinite(timeDifference) && timeDifference !== 0 ? timeDifference : (b._order || 0) - (a._order || 0)
}

function eventSignature(event) {
  return `${event.eventName}|${event.sequence}|${JSON.stringify(event.payload)}`
}

function textValue(value) {
  return String(value ?? '').trim()
}

function createClientRequestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}

export { useTaskRuntimeStore }
