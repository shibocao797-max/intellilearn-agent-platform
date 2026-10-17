import { computed, reactive, readonly } from 'vue'
import { authApi } from '@/services/api'
import { getToken, setAuthProfile } from '@/services/storage'

const CACHE_KEY_PREFIX = 'eduai-account-profile-cache-v1'
const LEGACY_KEY_PREFIX = 'eduai-learning-preferences-v1'
const DEFAULT_MENU_ITEMS = [
  { key: 'chat', label: '聊天', note: '核心学习入口，始终显示', icon: 'chat', enabled: true, required: true },
  { key: 'agent-dispatch', label: 'Agent 协作中心', note: '查看本轮学习协作与执行状态', icon: 'account_tree', enabled: true, required: false },
  { key: 'profile', label: '个人资料', note: '查看学习画像与个人资料', icon: 'person', enabled: true, required: false },
  { key: 'resources', label: '资源', note: '浏览已生成的学习资源', icon: 'article', enabled: true, required: false },
  { key: 'quiz', label: '测验', note: '进入练习与知识检验', icon: 'quiz', enabled: true, required: false },
  { key: 'report', label: '报告', note: '回顾阶段学习结果', icon: 'analytics', enabled: true, required: false }
]

const state = reactive(createDefaultState())
let saveQueue = Promise.resolve()
let mutationVersion = 0
let contextVersion = 0
let authenticatedToken = ''

const menuItems = computed(() => state.menuItems.map(item => ({ ...item })))
const visibleMenuKeys = computed(() => state.menuItems.filter(item => item.enabled).map(item => item.key))
const notificationsEnabled = computed(() => state.notificationsEnabled)
const autoSyncEnabled = computed(() => state.autoSyncEnabled)
const focusMode = computed(() => state.focusMode)
const bio = computed(() => state.bio)

export function usePreferencesStore() {
  initialize()

  return {
    state: readonly(state),
    menuItems,
    visibleMenuKeys,
    notificationsEnabled,
    autoSyncEnabled,
    focusMode,
    bio,
    initialize,
    setMenuEnabled,
    moveMenuItem,
    setPreference,
    setBio,
    clear: clearLocalCache
  }
}

async function initialize(userId) {
  const authenticatedUserId = await resolveAuthenticatedUserId()
  const resolvedToken = getToken()
  const requestedUserId = normalizeUserId(userId)
  const resolvedUserId = authenticatedUserId && (!requestedUserId || requestedUserId === authenticatedUserId)
    ? authenticatedUserId
    : ''
  if (!resolvedUserId) {
    if (state.currentUserId || state.initialized) resetState()
    return false
  }

  if (state.initializing && state.currentUserId === resolvedUserId && authenticatedToken === resolvedToken) {
    return state.initializing
  }
  if (state.initialized && state.currentUserId === resolvedUserId && authenticatedToken === resolvedToken) return true

  resetState()
  state.currentUserId = resolvedUserId
  authenticatedToken = resolvedToken
  const requestContext = ++contextVersion
  state.syncStatus = 'loading'
  state.initializing = bootstrapAccount(resolvedUserId, requestContext)
    .catch(error => {
      if (isCurrentContext(resolvedUserId, requestContext)) {
        applyData(readCache(resolvedUserId) || readLegacyPreferences(resolvedUserId) || {})
        state.syncStatus = 'error'
        state.syncError = error?.message || '账户资料加载失败'
      }
      return false
    })
    .finally(() => {
      if (isCurrentContext(resolvedUserId, requestContext)) {
        state.initialized = true
        state.initializing = null
      }
    })
  return state.initializing
}

async function bootstrapAccount(userId, requestContext) {
  const legacyPreferences = readLegacyPreferences(userId)
  const response = await authApi.getAccountProfile()
  if (!isCurrentContext(userId, requestContext)) return false

  const remotePayload = response?.data || {}
  const hasRemoteData = typeof remotePayload.hasRemoteData === 'boolean'
    ? remotePayload.hasRemoteData
    : (remotePayload.exists === true || hasAccountData(remotePayload))
  const remoteData = normalizeAccountData(remotePayload)

  if (!hasRemoteData && hasMeaningfulPreferences(legacyPreferences)) {
    applyData({ ...remoteData, ...legacyPreferences })
    const migrated = await persistCurrentData(userId, requestContext, { throwOnError: false, migration: true })
    if (migrated) {
      clearLegacyPreferences(userId)
      state.syncStatus = 'saved'
      state.syncError = ''
      return true
    }

    // Keep the migrated draft visible as a local fallback, but do not claim it is remote.
    persistCache(userId)
    state.syncStatus = 'error'
    return false
  }

  if (hasRemoteData) {
    applyData(remoteData)
    persistCache(userId)
    clearLegacyPreferences(userId)
    state.syncStatus = 'saved'
  } else {
    // A successful empty response is authoritative. A stale account cache
    // must not resurrect data after the server row was cleared.
    applyData(remoteData)
    clearCache(userId)
    state.syncStatus = 'empty'
  }
  state.syncError = ''
  return true
}

async function setMenuEnabled(key, enabled) {
  if (!await ensureReady()) return false
  const item = state.menuItems.find(menuItem => menuItem.key === key)
  if (!item) return false
  const previous = snapshotData()
  item.enabled = item.required ? true : Boolean(enabled)
  if (item.enabled === previous.menuItems.find(previousItem => previousItem.key === key)?.enabled) return true
  return persistMutation(previous, () => ({ preferences: { menuItems: buildMenuPayload() } }))
}

async function moveMenuItem(key, direction) {
  if (!await ensureReady()) return false
  const currentIndex = state.menuItems.findIndex(item => item.key === key)
  const offset = direction === 'up' ? -1 : direction === 'down' ? 1 : Number(direction)
  const targetIndex = currentIndex + offset
  if (currentIndex < 0 || !Number.isInteger(offset) || targetIndex < 0 || targetIndex >= state.menuItems.length) return false

  const previous = snapshotData()
  const [item] = state.menuItems.splice(currentIndex, 1)
  state.menuItems.splice(targetIndex, 0, item)
  return persistMutation(previous, () => ({ preferences: { menuItems: buildMenuPayload() } }))
}

async function setPreference(key, value) {
  if (!await ensureReady()) return false
  const preferenceKey = normalizePreferenceKey(key)
  if (!preferenceKey) return false
  const nextValue = Boolean(value)
  if (state[preferenceKey] === nextValue) return true

  const previous = snapshotData()
  state[preferenceKey] = nextValue
  return persistMutation(previous, () => ({ preferences: { [preferenceKey]: nextValue } }))
}

async function setBio(value) {
  if (!await ensureReady()) return false
  const nextBio = normalizeBio(value)
  if (state.bio === nextBio) return true

  const previous = snapshotData()
  state.bio = nextBio
  return persistMutation(previous, () => ({ bio: state.bio }))
}

async function ensureReady() {
  const userId = await resolveAuthenticatedUserId()
  return Boolean(userId && await initialize(userId) && state.initialized && state.currentUserId === userId)
}

async function resolveAuthenticatedUserId() {
  const tokenAtStart = getToken()
  if (!tokenAtStart) return ''
  const info = await authApi.getInfo().catch(() => null)
  if (!info || tokenAtStart !== getToken()) return ''
  const user = info?.user
  if (!user?.userId) return ''

  const userId = String(user.userId)
  setAuthProfile({
    userId: user.userId,
    userName: user.userName || '',
    nickName: user.nickName || '',
    avatar: user.avatar || '',
    roles: Array.isArray(info.roles) ? info.roles : [],
    userType: info.userType || ''
  })
  return userId
}

function persistMutation(previous, payloadFactory) {
  const version = ++mutationVersion
  const userId = state.currentUserId
  const requestContext = contextVersion
  persistCache(userId)
  const operation = saveQueue
    .catch(() => false)
    .then(() => persistCurrentData(userId, requestContext, { version, previous, payloadFactory, throwOnError: false }))

  saveQueue = operation.catch(() => false)
  return operation
}

async function persistCurrentData(userId, requestContext, options = {}) {
  const { version = mutationVersion, previous = null, payloadFactory = null, migration = false } = options
  if (!isCurrentContext(userId, requestContext)) return false

  state.syncStatus = 'saving'
  state.syncError = ''
  const payload = payloadFactory ? payloadFactory() : buildAccountPayload()
  try {
    const response = await authApi.updateAccountProfile(payload)
    if (!isCurrentContext(userId, requestContext)) return false
    if (response?.data && typeof response.data === 'object') {
      applyData(response.data)
    }
    state.updatedAt = Date.now()
    state.syncStatus = 'saved'
    state.syncError = ''
    persistCache(userId)
    return true
  } catch (error) {
    if (!isCurrentContext(userId, requestContext)) return false
    state.syncStatus = 'error'
    state.syncError = error?.message || (migration ? '旧数据迁移失败' : '账户资料保存失败')
    if (previous && version === mutationVersion) {
      applyData(previous)
      persistCache(userId)
    }
    return false
  }
}

function buildAccountPayload() {
  return {
    bio: state.bio,
    preferences: buildPreferencesPayload()
  }
}

function buildPreferencesPayload() {
  return {
    menuItems: buildMenuPayload(),
    notificationsEnabled: state.notificationsEnabled,
    autoSyncEnabled: state.autoSyncEnabled,
    focusMode: state.focusMode
  }
}

function buildMenuPayload() {
  return state.menuItems.map(item => ({ key: item.key, enabled: item.enabled }))
}

function createDefaultState() {
  return {
    initialized: false,
    initializing: null,
    currentUserId: '',
    menuItems: DEFAULT_MENU_ITEMS.map(item => ({ ...item })),
    notificationsEnabled: true,
    autoSyncEnabled: true,
    focusMode: false,
    bio: '',
    updatedAt: 0,
    syncStatus: 'idle',
    syncError: ''
  }
}

function resetState() {
  const nextContext = contextVersion + 1
  Object.assign(state, createDefaultState())
  contextVersion = nextContext
  mutationVersion += 1
  authenticatedToken = ''
}

function clearLocalCache() {
  const userId = state.currentUserId
  if (userId) {
    clearCache(userId)
    clearLegacyPreferences(userId)
  }
  resetState()
}

function applyData(nextData) {
  const normalized = normalizeAccountData(nextData)
  state.menuItems = normalized.menuItems
  state.notificationsEnabled = normalized.notificationsEnabled
  state.autoSyncEnabled = normalized.autoSyncEnabled
  state.focusMode = normalized.focusMode
  state.bio = normalized.bio
  state.updatedAt = Number(nextData?.updatedAt) || state.updatedAt || 0
}

function snapshotData() {
  return {
    menuItems: state.menuItems.map(item => ({ ...item })),
    notificationsEnabled: state.notificationsEnabled,
    autoSyncEnabled: state.autoSyncEnabled,
    focusMode: state.focusMode,
    bio: state.bio,
    updatedAt: state.updatedAt
  }
}

function normalizeAccountData(payload) {
  const preferences = payload?.preferences && typeof payload.preferences === 'object' ? payload.preferences : payload
  return {
    menuItems: normalizeMenuItems(preferences?.menuItems),
    notificationsEnabled: preferences?.notificationsEnabled !== false,
    autoSyncEnabled: preferences?.autoSyncEnabled !== false,
    focusMode: Boolean(preferences?.focusMode),
    bio: normalizeBio(payload?.bio),
    updatedAt: Number(payload?.updatedAt) || 0
  }
}

function normalizeMenuItems(items) {
  const source = Array.isArray(items) ? items : []
  const byKey = new Map(source.map(item => [String(item?.key || ''), item]))
  return DEFAULT_MENU_ITEMS.map(item => {
    const saved = byKey.get(item.key)
    return {
      ...item,
      enabled: item.required ? true : saved?.enabled !== false
    }
  }).sort((left, right) => {
    const leftIndex = source.findIndex(item => item?.key === left.key)
    const rightIndex = source.findIndex(item => item?.key === right.key)
    return (leftIndex < 0 ? Number.MAX_SAFE_INTEGER : leftIndex) - (rightIndex < 0 ? Number.MAX_SAFE_INTEGER : rightIndex)
  })
}

function hasAccountData(payload) {
  if (!payload || typeof payload !== 'object') return false
  if (String(payload.bio || '').trim()) return true
  const preferences = payload.preferences
  if (!preferences || typeof preferences !== 'object') return false
  return Array.isArray(preferences.menuItems) && preferences.menuItems.length > 0 ||
    Object.prototype.hasOwnProperty.call(preferences, 'notificationsEnabled') ||
    Object.prototype.hasOwnProperty.call(preferences, 'autoSyncEnabled') ||
    Object.prototype.hasOwnProperty.call(preferences, 'focusMode')
}

function hasMeaningfulPreferences(value) {
  if (!value || typeof value !== 'object') return false
  if (normalizeBio(value.bio)) return true
  if (Array.isArray(value.menuItems) && value.menuItems.some(item => item?.enabled === false || item?.key !== undefined)) return true
  return value.notificationsEnabled === false || value.autoSyncEnabled === false || value.focusMode === true
}

function persistCache(userId) {
  if (!userId) return
  try {
    localStorage.setItem(cacheKeyForUser(userId), JSON.stringify({ ...buildAccountPayload(), updatedAt: state.updatedAt }))
  } catch {
    // Browser storage is only a cache. Remote account data remains authoritative.
  }
}

function clearCache(userId) {
  try {
    localStorage.removeItem(cacheKeyForUser(userId))
  } catch {
    // Ignore unavailable browser storage.
  }
}

function readCache(userId) {
  return readJson(cacheKeyForUser(userId))
}

function readLegacyPreferences(userId) {
  return readJson(legacyKeyForUser(userId))
}

function clearLegacyPreferences(userId) {
  try {
    localStorage.removeItem(legacyKeyForUser(userId))
  } catch {
    // Ignore unavailable browser storage.
  }
}

function readJson(key) {
  try {
    const raw = localStorage.getItem(key)
    const parsed = raw ? JSON.parse(raw) : null
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

function isCurrentContext(userId, requestContext) {
  return Boolean(
    userId &&
    userId === state.currentUserId &&
    requestContext === contextVersion &&
    authenticatedToken &&
    authenticatedToken === getToken()
  )
}

function cacheKeyForUser(userId) {
  return `${CACHE_KEY_PREFIX}:${userId}`
}

function legacyKeyForUser(userId) {
  return `${LEGACY_KEY_PREFIX}:${userId}`
}

function normalizeUserId(value) {
  return String(value || '').trim()
}

function normalizeBio(value) {
  return [...String(value || '').trim()].slice(0, 240).join('')
}

function normalizePreferenceKey(key) {
  const aliases = {
    notify: 'notificationsEnabled',
    notifications: 'notificationsEnabled',
    notificationsEnabled: 'notificationsEnabled',
    sync: 'autoSyncEnabled',
    autoSync: 'autoSyncEnabled',
    autoSyncEnabled: 'autoSyncEnabled',
    focus: 'focusMode',
    focusMode: 'focusMode'
  }
  return aliases[key] || ''
}
