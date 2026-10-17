import { computed, reactive, readonly } from 'vue'
import { authApi, eduApi } from '@/services/api'
import { getAuthProfile, getToken, setAuthProfile } from '@/services/storage'

const STORAGE_KEY_PREFIX = 'eduai-workspace-v2'
const DEFAULT_PROJECT_NAME = '默认项目'
const UNTITLED_PROJECT_NAME = '未命名项目'
const NEW_SESSION_NAME = '新会话'
const PROJECT_NAME_PREFIX = '项目'
const DEFAULT_COORDINATOR_NICKNAME = '协调智能体'
const MAX_COORDINATOR_NICKNAME_LENGTH = 20
const CONTROL_CHARACTER_PATTERN = /[\u0000-\u001f\u007f-\u009f]/

const state = reactive({
  initialized: false,
  initializing: null,
  currentUserId: '',
  sessions: [],
  projects: [],
  sessionNames: {},
  coordinatorNickname: DEFAULT_COORDINATOR_NICKNAME,
  activeProjectId: '',
  activeSessionId: '',
  sessionSelectionVersion: 0
})

let workspaceSaveQueue = Promise.resolve()
let workspaceMutationVersion = 0
let workspaceSyncedVersion = 0
let workspaceContextVersion = 0
let workspaceToken = ''

const projectTree = computed(() => {
  const sessionMap = new Map(
    state.sessions.map(session => [
      session.sessionId,
      {
        ...session,
        displayTitle: displaySessionTitle(session),
        lastActiveLabel: formatSessionTime(session.lastActiveTime)
      }
    ])
  )

  return state.projects.map(project => ({
    ...project,
    sessions: project.sessionIds
      .map(sessionId => sessionMap.get(sessionId))
      .filter(Boolean)
  }))
})

function useWorkspaceStore() {
  return {
    state: readonly(state),
    projectTree,
    initialize,
    refreshWorkspace,
    refreshSessions,
    createProject,
    renameProject,
    deleteProject,
    createSessionInProject,
    createSessionInActiveProject,
    renameSession,
    deleteSession,
    setCoordinatorNickname,
    selectProject,
    selectSession,
    toggleProject,
    clear: resetState
  }
}

async function initialize() {
  const userId = await resolveCurrentUserId()
  if (!userId) return
  const token = getToken()
  if (!token) return

  if (state.initialized && state.currentUserId === userId && workspaceToken === token) return

  if (state.initializing) {
    await state.initializing
    if (state.initialized && state.currentUserId === userId && workspaceToken === token) return
  }

  resetState()
  state.currentUserId = userId
  workspaceToken = token
  const contextVersion = workspaceContextVersion
  state.initializing = bootstrapWorkspace(userId, contextVersion).finally(() => {
    if (!isCurrentWorkspaceContext(userId, contextVersion)) return
    state.initialized = true
    state.initializing = null
  })
  await state.initializing
}

async function bootstrapWorkspace(userId, contextVersion) {
  const scopedMetadata = loadScopedMetadata(userId)
  if (!await waitForWorkspaceSaveQueue(userId, contextVersion)) return
  const remoteResponse = await eduApi.workspace().catch(() => null)
  if (!isCurrentWorkspaceContext(userId, contextVersion)) return
  const remotePayload = remoteResponse?.data || null
  const remoteMetadata = normalizeMetadata(remotePayload)
  const hasRemoteWorkspace = hasWorkspaceData(remotePayload)
  const initialMetadata = normalizeMetadata(hasRemoteWorkspace ? remoteMetadata : scopedMetadata)

  if (!hasRemoteWorkspace && hasCoordinatorNicknameField(remotePayload)) {
    initialMetadata.coordinatorNickname = remoteMetadata.coordinatorNickname
  }

  applyMetadata(initialMetadata)
  await refreshSessions({ persist: false, expectedUserId: userId, expectedContextVersion: contextVersion })
  if (!isCurrentWorkspaceContext(userId, contextVersion)) return
  persistMetadata()
}

async function refreshSessions(options = {}) {
  const { persist = true, expectedUserId = '', expectedContextVersion = workspaceContextVersion } = options
  const requestUserId = expectedUserId || state.currentUserId || await resolveCurrentUserId()
  if (!isCurrentWorkspaceContext(requestUserId, expectedContextVersion)) return false
  const mutationVersionBeforeRequest = workspaceMutationVersion
  const res = await eduApi.sessions().catch(() => null)
  if (!isCurrentWorkspaceContext(requestUserId, expectedContextVersion) || mutationVersionBeforeRequest !== workspaceMutationVersion) return false
  const rows = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : null
  if (!rows) return false

  state.sessions = rows
    .map(session => ({
      sessionId: String(session.sessionId || '').trim(),
      title: String(session.title || '').trim(),
      lastActiveTime: session.lastActiveTime || '',
      messageCount: Number(session.messageCount || 0)
    }))
    .filter(session => session.sessionId)
    .sort((a, b) => String(b.lastActiveTime || '').localeCompare(String(a.lastActiveTime || '')))

  reconcileProjects()
  if (persist) persistMetadata()
  return true
}

async function refreshWorkspace() {
  if (!state.currentUserId) await initialize()
  const userId = state.currentUserId
  if (!userId) return
  const contextVersion = workspaceContextVersion
  if (!await waitForWorkspaceSaveQueue(userId, contextVersion)) return

  const mutationVersionBeforeRequest = workspaceMutationVersion

  const remoteResponse = await eduApi.workspace()
  if (!isCurrentWorkspaceContext(userId, contextVersion)) return
  const remotePayload = remoteResponse?.data || null
  const localWorkspaceIsSynced = workspaceSyncedVersion >= workspaceMutationVersion
  if (mutationVersionBeforeRequest === workspaceMutationVersion && localWorkspaceIsSynced) {
    if (hasWorkspaceData(remotePayload)) {
      applyMetadata(remotePayload)
    } else if (hasCoordinatorNicknameField(remotePayload)) {
      state.coordinatorNickname = normalizeCoordinatorNickname(remotePayload.coordinatorNickname)
    }
  }

  await refreshSessions({ persist: false, expectedUserId: userId, expectedContextVersion: contextVersion })
  if (!isCurrentWorkspaceContext(userId, contextVersion)) return
  persistMetadata()
}

function createProject(name = '') {
  const project = {
    id: createId('project'),
    name: name.trim() || createProjectName(),
    sessionIds: [],
    collapsed: false,
    createdAt: new Date().toISOString()
  }

  state.projects.unshift(project)
  state.activeProjectId = project.id
  persistMetadata()
  return project
}

function renameProject(projectId, name) {
  const project = state.projects.find(item => item.id === projectId)
  if (!project) return

  const nextName = name.trim()
  if (!nextName) return

  project.name = nextName
  persistMetadata()
}

async function deleteProject(projectId) {
  const project = state.projects.find(item => item.id === projectId)
  if (!project) return

  const sessionIds = [...project.sessionIds]
  for (const sessionId of sessionIds) {
    await deleteSession(sessionId, { persist: false, allowEmptyProject: true })
  }

  state.projects = state.projects.filter(item => item.id !== projectId)
  if (!state.projects.length) {
    createProject(DEFAULT_PROJECT_NAME)
  }

  normalizeActiveSelection()
  persistMetadata()
}

async function createSessionInProject(projectId) {
  const targetProject = ensureProject(projectId || state.activeProjectId)
  const res = await eduApi.newSession()
  const newSessionId = String(res?.data?.sessionId || res?.sessionId || '').trim()
  if (!newSessionId) return ''

  state.sessions.unshift({
    sessionId: newSessionId,
    title: '',
    lastActiveTime: new Date().toISOString(),
    messageCount: 0
  })

  targetProject.sessionIds = [newSessionId, ...targetProject.sessionIds.filter(id => id !== newSessionId)]
  targetProject.collapsed = false
  state.activeProjectId = targetProject.id
  state.activeSessionId = newSessionId
  markSessionSelectionChanged()
  persistMetadata()
  return newSessionId
}

async function createSessionInActiveProject() {
  const activeProject = ensureProject(state.activeProjectId)
  return createSessionInProject(activeProject.id)
}

function renameSession(sessionId, name) {
  const nextName = name.trim()
  if (!nextName) return

  state.sessionNames[sessionId] = nextName
  persistMetadata()
}

async function setCoordinatorNickname(value) {
  const nextNickname = validateCoordinatorNickname(value)
  const previousNickname = state.coordinatorNickname
  if (nextNickname === previousNickname) return nextNickname
  const requestUserId = state.currentUserId
  const requestContextVersion = workspaceContextVersion
  if (!requestUserId) throw new Error('当前账号尚未完成初始化，请刷新后重试。')

  state.coordinatorNickname = nextNickname
  markWorkspaceMutation()
  const payload = buildMetadataPayload()

  try {
    persistScopedMetadata(requestUserId, payload)
    await enqueueWorkspaceSave(requestUserId, payload, { throwOnError: true, contextVersion: requestContextVersion })
    if (!isCurrentWorkspaceContext(requestUserId, requestContextVersion)) {
      throw new Error('登录账号已切换，本次称呼保存结果未应用到当前页面。')
    }
    return nextNickname
  } catch (error) {
    if (!isCurrentWorkspaceContext(requestUserId, requestContextVersion)) throw error

    state.coordinatorNickname = previousNickname
    markWorkspaceMutation()
    const rollbackPayload = buildMetadataPayload()

    try {
      persistScopedMetadata(requestUserId, rollbackPayload)
      await enqueueWorkspaceSave(requestUserId, rollbackPayload, { throwOnError: true, contextVersion: requestContextVersion })
    } catch {
      // The visible state still rolls back; a later workspace mutation retries the full snapshot.
    }

    throw error
  }
}

async function deleteSession(sessionId, options = {}) {
  const { persist = true, allowEmptyProject = false } = options
  const previousActiveSessionId = state.activeSessionId

  await eduApi.deleteSession(sessionId)

  state.sessions = state.sessions.filter(item => item.sessionId !== sessionId)
  delete state.sessionNames[sessionId]

  state.projects.forEach(project => {
    project.sessionIds = project.sessionIds.filter(id => id !== sessionId)
  })

  if (!allowEmptyProject && !state.projects.length) {
    createProject(DEFAULT_PROJECT_NAME)
  }

  normalizeActiveSelection()
  if (state.activeSessionId !== previousActiveSessionId) {
    markSessionSelectionChanged()
  }

  if (persist) persistMetadata()
  else markWorkspaceMutation()
}

function selectProject(projectId) {
  const project = ensureProject(projectId)
  state.activeProjectId = project.id
  project.collapsed = false

  if (project.sessionIds.length && !project.sessionIds.includes(state.activeSessionId)) {
    state.activeSessionId = project.sessionIds[0]
  } else if (!project.sessionIds.length) {
    state.activeSessionId = ''
  }

  markSessionSelectionChanged()
  persistMetadata()
}

function selectSession(projectId, sessionId) {
  const project = ensureProject(projectId)
  project.collapsed = false
  state.activeProjectId = project.id
  state.activeSessionId = sessionId
  markSessionSelectionChanged()
  persistMetadata()
}

function toggleProject(projectId) {
  const project = state.projects.find(item => item.id === projectId)
  if (!project) return

  project.collapsed = !project.collapsed
  persistMetadata()
}

async function resolveCurrentUserId() {
  const tokenAtStart = getToken()
  if (!tokenAtStart) return ''
  const info = await authApi.getInfo().catch(() => null)
  if (!info || tokenAtStart !== getToken()) return ''
  const user = info?.user || null
  if (!user?.userId) return ''

  setAuthProfile({
    userId: user.userId,
    userName: user.userName || '',
    nickName: user.nickName || '',
    roles: Array.isArray(info?.roles) ? info.roles : [],
    userType: info?.userType || ''
  })

  return String(user.userId)
}

function resetState() {
  state.initialized = false
  state.initializing = null
  state.currentUserId = ''
  state.sessions = []
  state.projects = []
  state.sessionNames = {}
  state.coordinatorNickname = DEFAULT_COORDINATOR_NICKNAME
  state.activeProjectId = ''
  state.activeSessionId = ''
  workspaceMutationVersion = 0
  workspaceSyncedVersion = 0
  workspaceContextVersion += 1
  workspaceToken = ''
}

function applyMetadata(payload) {
  const normalized = normalizeMetadata(payload)
  state.projects = normalized.projects
  state.sessionNames = normalized.sessionNames
  state.coordinatorNickname = normalized.coordinatorNickname
  state.activeProjectId = normalized.activeProjectId
  state.activeSessionId = normalized.activeSessionId
}

function normalizeMetadata(payload) {
  return {
    projects: Array.isArray(payload?.projects) && payload.projects.length
      ? payload.projects.map(normalizeProject)
      : [createSeedProject()],
    sessionNames: normalizeSessionNames(payload?.sessionNames),
    coordinatorNickname: normalizeCoordinatorNickname(payload?.coordinatorNickname),
    activeProjectId: String(payload?.activeProjectId || ''),
    activeSessionId: String(payload?.activeSessionId || '')
  }
}

function normalizeCoordinatorNickname(value) {
  const nickname = String(value || '').trim()
  if (!nickname || CONTROL_CHARACTER_PATTERN.test(nickname)) return DEFAULT_COORDINATOR_NICKNAME
  return [...nickname].slice(0, MAX_COORDINATOR_NICKNAME_LENGTH).join('')
}

function validateCoordinatorNickname(value) {
  const nickname = String(value || '').trim()
  if (!nickname) throw new Error('AI 助手称呼不能为空。')
  if (CONTROL_CHARACTER_PATTERN.test(nickname)) throw new Error('AI 助手称呼不能包含控制字符。')
  if ([...nickname].length > MAX_COORDINATOR_NICKNAME_LENGTH) {
    throw new Error(`AI 助手称呼不能超过 ${MAX_COORDINATOR_NICKNAME_LENGTH} 个字符。`)
  }
  return nickname
}

function normalizeSessionNames(value) {
  if (!value || typeof value !== 'object') return {}

  return Object.entries(value).reduce((acc, [sessionId, name]) => {
    const nextSessionId = String(sessionId || '').trim()
    const nextName = String(name || '').trim()
    if (nextSessionId && nextName) {
      acc[nextSessionId] = nextName
    }
    return acc
  }, {})
}

function buildMetadataPayload() {
  return {
    projects: state.projects.map(project => ({
      id: project.id,
      name: project.name,
      sessionIds: [...project.sessionIds],
      collapsed: !!project.collapsed,
      createdAt: project.createdAt || new Date().toISOString()
    })),
    sessionNames: { ...state.sessionNames },
    coordinatorNickname: state.coordinatorNickname,
    activeProjectId: state.activeProjectId,
    activeSessionId: state.activeSessionId
  }
}

function persistMetadata() {
  markWorkspaceMutation()
  const payload = buildMetadataPayload()
  persistScopedMetadata(state.currentUserId, payload)
  enqueueWorkspaceSave(state.currentUserId, payload)
}

function markWorkspaceMutation() {
  workspaceMutationVersion += 1
}

async function waitForWorkspaceSaveQueue(userId, contextVersion = workspaceContextVersion) {
  while (isCurrentWorkspaceContext(userId, contextVersion)) {
    const pendingQueue = workspaceSaveQueue
    await pendingQueue.catch(() => null)
    if (!isCurrentWorkspaceContext(userId, contextVersion)) return false
    if (pendingQueue === workspaceSaveQueue) return true
  }
  return false
}

function enqueueWorkspaceSave(userId, payload, options = {}) {
  const {
    throwOnError = false,
    mutationVersion = workspaceMutationVersion,
    contextVersion = workspaceContextVersion
  } = options
  const snapshot = JSON.parse(JSON.stringify(payload))

  const saveOperation = workspaceSaveQueue
    .catch(() => null)
    .then(() => {
      if (!isCurrentWorkspaceContext(userId, contextVersion)) return null
      return eduApi.saveWorkspace(snapshot).then(result => {
        if (isCurrentWorkspaceContext(userId, contextVersion)) {
          workspaceSyncedVersion = Math.max(workspaceSyncedVersion, mutationVersion)
        }
        return result
      })
    })

  workspaceSaveQueue = saveOperation.catch(() => null)
  return throwOnError ? saveOperation : workspaceSaveQueue
}

function loadScopedMetadata(userId) {
  if (!userId) return null

  try {
    const raw = localStorage.getItem(storageKeyForUser(userId))
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function persistScopedMetadata(userId, payload) {
  if (!userId) return
  localStorage.setItem(storageKeyForUser(userId), JSON.stringify(payload))
}

function storageKeyForUser(userId) {
  return `${STORAGE_KEY_PREFIX}:${userId}`
}

function isCurrentWorkspaceContext(userId, contextVersion) {
  return Boolean(
    userId &&
    userId === state.currentUserId &&
    contextVersion === workspaceContextVersion &&
    workspaceToken &&
    workspaceToken === getToken()
  )
}

function hasWorkspaceData(payload) {
  if (!payload) return false
  if (Array.isArray(payload.projects) && payload.projects.length) return true
  if (payload.sessionNames && Object.keys(payload.sessionNames).length) return true
  return Boolean(payload.activeProjectId || payload.activeSessionId)
}

function hasCoordinatorNicknameField(payload) {
  return Boolean(payload && Object.prototype.hasOwnProperty.call(payload, 'coordinatorNickname'))
}

function reconcileProjects() {
  if (!state.projects.length) {
    state.projects = [createSeedProject()]
  }

  const knownSessionIds = new Set(state.sessions.map(item => item.sessionId))
  const assignedSessionIds = new Set()

  state.projects = state.projects.map(project => {
    const uniqueSessionIds = []

    project.sessionIds.forEach(sessionId => {
      if (!knownSessionIds.has(sessionId) || assignedSessionIds.has(sessionId)) return
      uniqueSessionIds.push(sessionId)
      assignedSessionIds.add(sessionId)
    })

    return {
      ...normalizeProject(project),
      sessionIds: uniqueSessionIds
    }
  })

  mergeLegacyDefaultProjects()

  let keptEmptyDefaultProject = false
  state.projects = state.projects.filter(project => {
    const isEmptyDefaultProject = isDefaultProject(project) && project.sessionIds.length === 0
    if (!isEmptyDefaultProject) return true
    if (keptEmptyDefaultProject) return false
    keptEmptyDefaultProject = true
    return true
  })

  const targetProject = ensureProject(state.activeProjectId)
  state.sessions.forEach(session => {
    if (assignedSessionIds.has(session.sessionId)) return
    targetProject.sessionIds.push(session.sessionId)
  })

  normalizeActiveSelection()
}

function normalizeActiveSelection() {
  const project = state.projects.find(item => item.id === state.activeProjectId) || state.projects[0]
  state.activeProjectId = project?.id || ''

  const activeSessionExists = state.sessions.some(item => item.sessionId === state.activeSessionId)
  if (activeSessionExists) return

  if (project?.sessionIds?.length) {
    state.activeSessionId = project.sessionIds[0]
    return
  }

  state.activeSessionId = state.sessions[0]?.sessionId || ''
}

function ensureProject(projectId) {
  if (!projectId) {
    if (state.projects[0]) return state.projects[0]
    const seed = createSeedProject()
    state.projects = [seed]
    return seed
  }

  const existing = state.projects.find(item => item.id === projectId)
  if (existing) return existing

  return createProject(projectId ? UNTITLED_PROJECT_NAME : DEFAULT_PROJECT_NAME)
}

function displaySessionTitle(session) {
  const alias = String(state.sessionNames[session.sessionId] || '').trim()
  if (alias) return alias
  if (session.title) return session.title
  return NEW_SESSION_NAME
}

function formatSessionTime(value) {
  if (!value) return ''

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''

  const now = new Date()
  const sameDay = date.toDateString() === now.toDateString()
  if (sameDay) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }

  return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

function normalizeProject(project) {
  return {
    id: String(project?.id || createId('project')),
    name: String(project?.name || createProjectName()),
    sessionIds: Array.isArray(project?.sessionIds) ? [...new Set(project.sessionIds.map(String).filter(Boolean))] : [],
    collapsed: Boolean(project?.collapsed),
    createdAt: project?.createdAt || new Date().toISOString()
  }
}

function mergeLegacyDefaultProjects() {
  const defaultProjects = state.projects.filter(isDefaultProject)
  if (defaultProjects.length <= 1) return

  const canonicalProject =
    defaultProjects.find(project => project.id === state.activeProjectId) ||
    defaultProjects[0]
  const mergedSessionIds = []
  const seenSessionIds = new Set()

  defaultProjects.forEach(project => {
    project.sessionIds.forEach(sessionId => {
      if (seenSessionIds.has(sessionId)) return
      seenSessionIds.add(sessionId)
      mergedSessionIds.push(sessionId)
    })
    if (project.id === state.activeProjectId) {
      state.activeProjectId = canonicalProject.id
    }
  })

  canonicalProject.name = DEFAULT_PROJECT_NAME
  canonicalProject.sessionIds = mergedSessionIds
  canonicalProject.collapsed = defaultProjects.every(project => project.collapsed)
  canonicalProject.createdAt = defaultProjects
    .map(project => project.createdAt)
    .filter(Boolean)
    .sort()[0] || canonicalProject.createdAt

  state.projects = state.projects.filter(project => !isDefaultProject(project) || project.id === canonicalProject.id)
}

function isDefaultProject(project) {
  return project?.name === DEFAULT_PROJECT_NAME
}

function createSeedProject() {
  return {
    id: createId('project'),
    name: DEFAULT_PROJECT_NAME,
    sessionIds: [],
    collapsed: false,
    createdAt: new Date().toISOString()
  }
}

function createProjectName() {
  return `${PROJECT_NAME_PREFIX} ${state.projects.length + 1}`
}

function createId(prefix) {
  return `${prefix}-${Math.random().toString(36).slice(2, 8)}-${Date.now().toString(36)}`
}

function markSessionSelectionChanged() {
  state.sessionSelectionVersion += 1
}

export { DEFAULT_COORDINATOR_NICKNAME, MAX_COORDINATOR_NICKNAME_LENGTH, useWorkspaceStore }
