<template>
  <div
    class="app-shell"
    :class="{
      'sidebar-collapsed': sidebarCollapsed,
      'sidebar-resizing': sidebarDragging,
      'mobile-nav-open': mobileNavOpen
    }"
    :style="shellStyle"
  >
    <aside
      ref="sideNavRef"
      id="primary-navigation"
      class="side-nav"
      :class="{ collapsed: sidebarCollapsed, 'mobile-open': mobileNavOpen }"
      :inert="isMobileViewport && !mobileNavOpen"
      @click="handleSideNavClick"
    >
      <div class="side-nav-head">
        <div class="brand-block">
          <div class="brand-mark">
            <img :src="brandLogo" alt="" aria-hidden="true" />
          </div>
          <div>
            <h1>智学协同</h1>
          </div>
        </div>
        <button
          class="panel-toggle-button"
          type="button"
          aria-label="收起左侧栏"
          title="收起左侧栏"
          @click="handleSidebarToggle"
        >
          <span class="material-symbols-outlined">chevron_left</span>
        </button>
      </div>

      <button class="new-session" type="button" @click="handleCreateSession">
        <span class="material-symbols-outlined">add</span>
        新建会话
      </button>

      <nav class="nav-section nav-main">
        <RouterLink
          v-for="item in visibleMainNav"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          :class="{ active: isMainNavActive(item) }"
        >
          <span class="material-symbols-outlined" :class="{ filled: isMainNavActive(item) }">
            {{ item.icon }}
          </span>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <section class="projects-section">
        <div class="sidebar-section-head">
          <span>项目</span>
          <button class="mini-icon-button" type="button" title="新建项目" @click="openCreateProjectDialog">
            <span class="material-symbols-outlined">add</span>
          </button>
        </div>

        <div v-if="!projectTree.length" class="project-empty">
          <strong>暂无项目</strong>
          <p>创建项目后，历史会话会按项目归档，方便继续使用。</p>
        </div>

        <div v-else class="projects-list">
          <article v-for="project in projectTree" :key="project.id" class="project-card">
            <div
              class="project-row"
              :class="{ active: activeProjectId === project.id }"
              @contextmenu.prevent.stop="openContextMenu($event, { type: 'project', projectId: project.id })"
            >
              <button type="button" class="project-main-button" @click="handleProjectClick(project)">
                <div class="project-row-main">
                  <span class="material-symbols-outlined project-chevron">
                    {{ project.collapsed ? 'chevron_right' : 'expand_more' }}
                  </span>
                  <span class="material-symbols-outlined filled">folder_open</span>
                  <strong>{{ project.name }}</strong>
                </div>
              </button>
              <button
                type="button"
                class="project-action-button"
                :title="`在 ${project.name} 中创建会话`"
                @pointerdown.stop
                @click.stop="createSessionInProject(project.id)"
              >
                <span class="material-symbols-outlined">add</span>
              </button>
            </div>

            <div v-if="!project.collapsed" class="session-list">
              <button
                v-for="session in project.sessions"
                :key="session.sessionId"
                type="button"
                class="session-row"
                :class="{ active: activeSessionId === session.sessionId }"
                @click="openSession(project.id, session.sessionId)"
                @contextmenu.prevent.stop="
                  openContextMenu($event, {
                    type: 'session',
                    projectId: project.id,
                    sessionId: session.sessionId
                  })
                "
              >
                <span class="session-title">{{ session.displayTitle }}</span>
                <small>{{ session.lastActiveLabel || '刚刚创建' }}</small>
              </button>
            </div>
          </article>
        </div>
      </section>

      <nav class="nav-section nav-footer">
        <div class="settings-dock" :class="{ active: isSettingsRoute }">
          <RouterLink
            class="nav-link settings-trigger"
            :class="{ active: isSettingsRoute }"
            :to="{ name: 'settings', query: { section: 'settings' } }"
          >
            <span class="material-symbols-outlined" :class="{ filled: isSettingsRoute }">settings</span>
            <span>设置</span>
          </RouterLink>

          <div class="settings-flyout" role="menu" aria-label="设置子菜单">
            <RouterLink
              v-for="item in settingsLinks"
              :key="item.section"
            :to="{ name: 'settings', query: { section: item.section } }"
              class="settings-link"
              :class="{ active: isSettingsNavActive(item) }"
              role="menuitem"
            >
              <span class="material-symbols-outlined">{{ item.icon }}</span>
              <span>{{ item.label }}</span>
            </RouterLink>
          </div>
        </div>
      </nav>
    </aside>

    <button
      v-if="mobileNavOpen"
      class="mobile-nav-scrim"
      type="button"
      aria-label="关闭导航菜单"
      @click="closeMobileNav"
    ></button>

    <button
      v-if="sidebarCollapsed"
      class="edge-restore-button edge-restore-left"
      type="button"
      aria-label="展开左侧栏"
      title="展开左侧栏"
      @click="expandSidebar"
    >
      <span class="material-symbols-outlined">chevron_right</span>
    </button>

    <div
      v-if="!sidebarCollapsed"
      class="layout-resize-handle sidebar-resize-handle"
      role="separator"
      aria-orientation="vertical"
      aria-label="调整左侧栏宽度"
      @pointerdown.prevent.stop="startSidebarResize"
    ></div>

    <div class="workspace">
      <header class="top-nav">
        <button
          ref="mobileMenuButtonRef"
          class="mobile-menu"
          type="button"
          :aria-label="mobileNavOpen ? '关闭导航菜单' : '打开导航菜单'"
          aria-controls="primary-navigation"
          :aria-expanded="mobileNavOpen"
          @click="toggleMobileNav"
        >
          <span class="material-symbols-outlined">menu</span>
        </button>
        <div class="top-title" aria-label="智学协同">
          <span class="top-title-name">智学协同</span>
        </div>
        <div class="top-actions" :class="{ 'has-runtime-task': runtimeIndicator }">
          <button
            v-if="runtimeIndicator"
            class="task-runtime-indicator"
            :class="runtimeIndicator.tone"
            type="button"
            :title="runtimeIndicator.detail"
            :aria-label="`${runtimeIndicator.label}。${runtimeIndicator.detail}`"
            @click="openAgentDispatchCenter"
          >
            <span class="material-symbols-outlined" aria-hidden="true">
              {{ runtimeIndicator.icon }}
            </span>
            <span class="task-runtime-label">{{ runtimeIndicator.label }}</span>
          </button>
          <div v-if="showNotifications" class="notice-menu" @click.stop>
            <button
              class="icon-button notice-trigger"
              type="button"
              title="通知"
              aria-label="通知"
              :aria-expanded="notificationsOpen"
              @click="toggleNotifications"
            >
              <span class="material-symbols-outlined">notifications_active</span>
              <span v-if="unreadNotificationCount" class="notice-badge">{{ unreadNotificationCount > 99 ? '99+' : unreadNotificationCount }}</span>
            </button>
            <section v-if="notificationsOpen" class="notice-popover" aria-label="最近通知">
              <header class="notice-popover-head">
                <div><strong>最近通知</strong><small>{{ unreadNotificationCount ? `${unreadNotificationCount} 条未读` : '全部已读' }}</small></div>
                <button type="button" class="notice-mark-all" :disabled="!unreadNotificationCount || markingAllNotifications" @click="markAllNotificationsRead">
                  {{ markingAllNotifications ? '处理中' : '将当前通知标为已读' }}
                </button>
              </header>
              <div v-if="notificationsLoading" class="notice-state">正在加载通知...</div>
              <div v-else-if="notificationError" class="notice-state error">{{ notificationError }}</div>
              <div v-else-if="!notifications.length" class="notice-state">暂无通知</div>
              <div v-else class="notice-list">
                <article v-for="notice in notifications" :key="notice.noticeId" class="notice-item" :class="{ unread: !notice.isRead }">
                  <button type="button" class="notice-content" :disabled="notice.isRead || noticeMarkingId === notice.noticeId" @click="markNotificationRead(notice)">
                    <span class="notice-title-row"><strong>{{ notice.noticeTitle }}</strong><i v-if="!notice.isRead"></i></span>
                    <span>{{ notice.noticeType === '2' ? '公告' : '通知' }}</span>
                    <small>{{ formatNoticeTime(notice.createTime) }}</small>
                  </button>
                  <div class="notice-actions">
                    <button v-if="!notice.isRead" type="button" class="notice-read-button" :disabled="noticeMarkingId === notice.noticeId" @click="markNotificationRead(notice)">
                      {{ noticeMarkingId === notice.noticeId ? '处理中' : '标为已读' }}
                    </button>
                    <button
                      type="button"
                      class="notice-delete-button"
                      :disabled="noticeDeletingId === notice.noticeId"
                      :title="noticeDeletingId === notice.noticeId ? '正在删除' : '从我的通知中删除'"
                      :aria-label="noticeDeletingId === notice.noticeId ? '正在删除通知' : `从我的通知中删除：${notice.noticeTitle}`"
                      @click="hideNotification(notice)"
                    >
                      <span class="material-symbols-outlined">delete</span>
                    </button>
                  </div>
                </article>
              </div>
            </section>
          </div>
          <button class="icon-button sync-button" type="button" :disabled="syncing" :title="syncing ? '正在同步' : '同步工作区'" :aria-label="syncing ? '正在同步' : '同步工作区'" @click="syncWorkspace">
            <span class="material-symbols-outlined" :class="{ spin: syncing }">sync</span>
          </button>
          <button v-if="showAgentDispatch && route.name !== 'agentDispatch'" class="icon-button dispatch-icon-button" type="button" title="打开Agent协作中心" aria-label="打开Agent协作中心" @click="openAgentDispatchCenter">
            <span class="material-symbols-outlined">account_tree</span>
          </button>
          <button v-if="showAgentDispatch && route.name !== 'agentDispatch'" class="agent-dispatch" type="button" @click="openAgentDispatchCenter">
            <span class="material-symbols-outlined filled">account_tree</span>
            Agent协作中心
          </button>
          <span v-if="syncStatus" class="sync-status" :class="syncStatusTone">{{ syncStatus }}</span>
        </div>
      </header>

      <p
        class="shell-task-status-announcement"
        role="status"
        aria-live="polite"
        aria-atomic="true"
      >
        {{ taskStatusAnnouncement }}
      </p>

      <main class="workspace-main">
        <slot />
      </main>
    </div>

    <div
      v-if="contextMenu"
      class="sidebar-context-menu"
      :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
      @click.stop
    >
      <button type="button" @click="openRenameDialogFromMenu">
        <span class="material-symbols-outlined">edit</span>
        <span>{{ contextMenu.type === 'project' ? '重命名项目' : '重命名会话' }}</span>
      </button>
      <button type="button" class="danger" @click="deleteFromMenu">
        <span class="material-symbols-outlined">delete</span>
        <span>{{ contextMenu.type === 'project' ? '删除项目' : '删除会话' }}</span>
      </button>
    </div>

    <div v-if="editorDialog.open" class="dialog-scrim" @click="closeEditorDialog">
      <section class="dialog-card" @click.stop>
        <header>
          <h3>{{ dialogTitle }}</h3>
          <button class="icon-button" type="button" @click="closeEditorDialog">
            <span class="material-symbols-outlined">close</span>
          </button>
        </header>

        <label class="field">
          <span>{{ editorDialog.mode === 'rename-session' ? '会话名称' : '项目名称' }}</span>
          <input
            v-model.trim="editorDialog.value"
            class="control"
            :placeholder="editorDialog.mode === 'create-project' ? '例如：AI+学习' : '输入新的名称'"
          />
        </label>

        <footer class="dialog-actions">
          <button class="ghost-button" type="button" @click="closeEditorDialog">取消</button>
          <button class="primary-button dialog-submit" type="button" @click="submitEditorDialog">确认</button>
        </footer>
      </section>
    </div>

    <div v-if="deleteDialog.open" class="dialog-scrim delete-dialog-scrim" @click.self="closeDeleteDialog">
      <section class="dialog-card delete-dialog" role="dialog" aria-modal="true" aria-labelledby="delete-dialog-title">
        <div class="delete-dialog-icon" aria-hidden="true">
          <span class="material-symbols-outlined">delete_forever</span>
        </div>
        <header>
          <h3 id="delete-dialog-title">{{ deleteDialogTitle }}</h3>
        </header>
        <p>{{ deleteDialogDescription }}</p>
        <p v-if="deleteDialog.error" class="delete-dialog-error">{{ deleteDialog.error }}</p>
        <footer class="dialog-actions">
          <button class="ghost-button" type="button" :disabled="deleteDialog.submitting" @click="closeDeleteDialog">取消</button>
          <button class="danger-button" type="button" :disabled="deleteDialog.submitting" @click="confirmDelete">
            {{ deleteDialog.submitting ? '正在删除' : '确认删除' }}
          </button>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { systemApi } from '@/services/api'
import { useWorkspaceStore } from '@/stores/workspace'
import { usePreferencesStore } from '@/stores/preferences'
import { useTaskRuntimeStore } from '@/stores/taskRuntime'
import { sanitizeDisplayText } from '@/utils/format'
import brandLogo from '@/assets/brand/intellilearn-logo.svg'

const SIDEBAR_LAYOUT_KEY_PREFIX = 'eduai-sidebar-layout-v1'
const SIDEBAR_MIN_WIDTH = 180
const SIDEBAR_MAX_WIDTH = 460

const route = useRoute()
const router = useRouter()
const workspace = useWorkspaceStore()
const preferences = usePreferencesStore()
const taskRuntime = useTaskRuntimeStore()
const preferencesInitialized = computed(() => preferences.state.initialized)

const projectTree = computed(() => workspace.projectTree.value)
const activeProjectId = computed(() => workspace.state.activeProjectId)
const activeSessionId = computed(() => workspace.state.activeSessionId)
const isSettingsRoute = computed(() => route.path === '/settings')
const dialogTitle = computed(() => {
  if (editorDialog.mode === 'create-project') return '新建项目'
  if (editorDialog.mode === 'rename-project') return '重命名项目'
  return '重命名会话'
})
const deleteDialogTitle = computed(() => deleteDialog.type === 'project' ? '删除项目' : '删除会话')
const deleteDialogDescription = computed(() => {
  if (deleteDialog.type === 'project') {
    return deleteDialog.sessionCount
      ? `删除项目“${deleteDialog.targetName}”后，项目下的 ${deleteDialog.sessionCount} 个会话也会一并删除。此操作无法撤销。`
      : `确定删除项目“${deleteDialog.targetName}”吗？此操作无法撤销。`
  }
  return `确定删除会话“${deleteDialog.targetName}”吗？此操作无法撤销。`
})
const shellStyle = computed(() => ({
  '--sidebar-runtime-width': `${sidebarCollapsed.value ? 0 : sidebarWidth.value}px`
}))

const contextMenu = ref(null)
const sidebarWidth = ref(320)
const sidebarCollapsed = ref(false)
const sidebarDragging = ref(false)
const mobileNavOpen = ref(false)
const sideNavRef = ref(null)
const mobileMenuButtonRef = ref(null)
const isMobileViewport = ref(typeof window !== 'undefined' && window.innerWidth <= 1024)
const notificationsOpen = ref(false)
const notificationsLoading = ref(false)
const notificationError = ref('')
const notifications = ref([])
const unreadNotificationCount = ref(0)
const noticeMarkingId = ref(null)
const noticeDeletingId = ref(null)
const markingAllNotifications = ref(false)
const syncing = ref(false)
const syncStatus = ref('')
const syncStatusTone = ref('')
const editorDialog = reactive({
  open: false,
  mode: 'create-project',
  projectId: '',
  sessionId: '',
  value: ''
})
const deleteDialog = reactive({
  open: false,
  type: '',
  projectId: '',
  sessionId: '',
  targetName: '',
  sessionCount: 0,
  submitting: false,
  error: ''
})

const mainNav = [
  { path: '/chat', label: '聊天', icon: 'chat' },
  { path: '/agent-dispatch', label: 'Agent协作中心', icon: 'account_tree' },
  { path: '/profile', label: '个人资料', icon: 'person' },
  { path: '/resources', label: '资源', icon: 'article' },
  { path: '/quiz', label: '测验', icon: 'quiz' },
  { path: '/report', label: '报告', icon: 'analytics' }
]

const NAV_BY_KEY = new Map([
  ['chat', mainNav[0]],
  ['agent-dispatch', mainNav[1]],
  ['profile', mainNav[2]],
  ['resources', mainNav[3]],
  ['quiz', mainNav[4]],
  ['report', mainNav[5]]
])
const FOCUS_MENU_KEYS = new Set(['chat', 'resources', 'quiz', 'report'])

function normalizeMenuKey(value) {
  const key = String(value || '').trim()
  if (NAV_BY_KEY.has(key)) return key
  return [...NAV_BY_KEY.entries()].find(([, item]) => item.path === key)?.[0] || ''
}

const visibleMainNav = computed(() => {
  if (!preferencesInitialized.value) return mainNav

  const configuredKeys = Array.isArray(preferences.visibleMenuKeys.value)
    ? preferences.visibleMenuKeys.value.map(normalizeMenuKey).filter(Boolean)
    : [...NAV_BY_KEY.keys()]
  const allowedKeys = preferences.focusMode.value
    ? configuredKeys.filter(key => FOCUS_MENU_KEYS.has(key))
    : configuredKeys
  const orderedKeys = allowedKeys.includes('chat')
    ? [...new Set(allowedKeys)]
    : ['chat', ...new Set(allowedKeys)]

  // Preference order controls the sidebar, while chat remains an always-available learning entry.
  return orderedKeys.map(key => NAV_BY_KEY.get(key)).filter(Boolean)
})
const showNotifications = computed(() => !preferencesInitialized.value || (preferences.notificationsEnabled.value && !preferences.focusMode.value))
const showAgentDispatch = computed(() => !preferencesInitialized.value || !preferences.focusMode.value)
const autoSyncEnabled = computed(() => preferencesInitialized.value && preferences.autoSyncEnabled.value)
const activeRuntimeTask = computed(() => {
  const sessionTask = taskRuntime.taskForSession(activeSessionId.value)
  if (sessionTask && isRuntimeTaskActive(sessionTask)) return sessionTask

  return Object.values(taskRuntime.state.tasks)
    .filter(isRuntimeTaskActive)
    .sort((a, b) => Date.parse(b.updatedAt || b.createdAt || '') - Date.parse(a.updatedAt || a.createdAt || ''))[0] || null
})
const announcedTaskId = ref('')
const announcementTask = computed(() => activeRuntimeTask.value || taskRuntime.state.tasks[announcedTaskId.value] || null)
const runtimeIndicator = computed(() => {
  const task = activeRuntimeTask.value
  if (!task) return null

  if (task.connectionStatus === 'reconnecting' || taskRuntime.state.connectionStatus === 'reconnecting') {
    return {
      tone: 'reconnecting',
      icon: 'sync_problem',
      label: '任务重连中',
      detail: '任务仍在服务端执行，正在恢复状态连接。'
    }
  }

  if (task._finalizationPending) {
    return {
      tone: 'running',
      icon: 'sync',
      label: '任务收尾同步中',
      detail: `任务 ${String(task.taskId || '').slice(0, 8)} 正在补齐最终状态。`
    }
  }

  if (String(task.status).toUpperCase() === 'CANCELLING') {
    return {
      tone: 'cancelling',
      icon: 'pending_actions',
      label: '正在取消任务',
      detail: `任务 ${task.taskId.slice(0, 8)} 正在等待取消回执。`
    }
  }

  const runningCount = Object.values(task.progressByAgent || {})
    .filter(progress => ['RUNNING', 'GENERATING', 'PROCESSING', 'REVISING', 'IN_PROGRESS'].includes(String(progress?.status || '').toUpperCase()))
    .length
  return {
    tone: 'running',
    icon: 'progress_activity',
    label: runningCount ? `${runningCount} 个 Agent 协作中` : '学习任务执行中',
    detail: `任务 ${String(task.taskId || '').slice(0, 8)}，点击查看真实运行轨迹。`
  }
})
const taskStatusAnnouncement = computed(() => {
  const task = announcementTask.value
  if (!task) {
    if (taskRuntime.state.connectionStatus === 'connecting') return '正在连接任务服务。'
    return taskRuntime.state.connectionStatus === 'reconnecting' ? '任务连接正在恢复。' : ''
  }

  const taskLabel = String(task.taskId || '').slice(0, 8) || '当前任务'
  if (taskRuntime.state.connectionStatus === 'connecting') return `任务 ${taskLabel} 正在连接任务服务。`
  if (task.connectionStatus === 'reconnecting' || taskRuntime.state.connectionStatus === 'reconnecting') {
    return `任务 ${taskLabel} 连接正在恢复，服务端任务仍在执行。`
  }
  if (task._finalizationPending) return `任务 ${taskLabel} 已进入终态，正在同步最终状态。`

  const statusLabels = {
    CREATED: '已创建',
    ACCEPTED: '已接收',
    PENDING: '等待执行',
    QUEUED: '排队中',
    RUNNING: '执行中',
    PROCESSING: '处理中',
    IN_PROGRESS: '执行中',
    CANCELLING: '正在取消',
    SUCCEEDED: '已完成',
    COMPLETED: '已完成',
    SUCCESS: '已完成',
    DONE: '已完成',
    FAILED: '执行失败',
    ERROR: '执行失败',
    CANCELLED: '已取消'
  }
  const status = String(task.status || '').toUpperCase()
  const phase = task.currentPhase ? `，当前阶段${task.currentPhase}` : ''
  const error = statusLabels[status] === '执行失败' && task.error ? `，${sanitizeDisplayText(task.error)}` : ''
  return `任务 ${taskLabel}${statusLabels[status] || '状态已更新'}${phase}${error}。`
})

const settingsLinks = [
  { section: 'user', label: '个人中心', icon: 'account_circle' },
  { section: 'menu', label: '菜单', icon: 'segment' },
  { section: 'settings', label: '偏好设置', icon: 'settings' }
]

let stopSidebarDrag = null
let syncStatusTimer = null
let autoSyncTimer = null

async function toggleNotifications() {
  if (!showNotifications.value) return
  notificationsOpen.value = !notificationsOpen.value
  if (notificationsOpen.value) await loadNotifications()
}

async function loadNotifications() {
  if (!showNotifications.value) return
  notificationsLoading.value = true
  notificationError.value = ''
  try {
    const response = await systemApi.noticesTop()
    const rows = Array.isArray(response?.data) ? response.data : Array.isArray(response) ? response : []
    notifications.value = rows.map(notice => ({
      noticeId: notice.noticeId,
      noticeTitle: notice.noticeTitle || '未命名通知',
      noticeType: notice.noticeType || '',
      createTime: notice.createTime || '',
      isRead: Boolean(notice.isRead)
    }))
    const serverUnread = Number(response?.unreadCount)
    unreadNotificationCount.value = Number.isFinite(serverUnread)
      ? Math.max(0, serverUnread)
      : notifications.value.filter(notice => !notice.isRead).length
  } catch (error) {
    notificationError.value = error?.message || '通知加载失败，请稍后重试。'
  } finally {
    notificationsLoading.value = false
  }
}

async function markNotificationRead(notice) {
  if (!notice?.noticeId || notice.isRead || noticeMarkingId.value === notice.noticeId) return
  noticeMarkingId.value = notice.noticeId
  try {
    await systemApi.markNoticeRead(notice.noticeId)
    notice.isRead = true
    unreadNotificationCount.value = Math.max(0, unreadNotificationCount.value - 1)
  } catch (error) {
    notificationError.value = error?.message || '标记已读失败，请重试。'
  } finally {
    noticeMarkingId.value = null
  }
}

async function markAllNotificationsRead() {
  const ids = notifications.value.filter(notice => !notice.isRead).map(notice => notice.noticeId).filter(Boolean)
  if (!ids.length || markingAllNotifications.value) return
  markingAllNotifications.value = true
  try {
    await systemApi.markAllNoticesRead(ids)
    notifications.value.forEach(notice => { notice.isRead = true })
    unreadNotificationCount.value = 0
  } catch (error) {
    notificationError.value = error?.message || '全部标为已读失败，请重试。'
  } finally {
    markingAllNotifications.value = false
  }
}

async function hideNotification(notice) {
  if (!notice?.noticeId || noticeDeletingId.value === notice.noticeId) return
  noticeDeletingId.value = notice.noticeId
  notificationError.value = ''
  try {
    await systemApi.hideNotice(notice.noticeId)
    const wasUnread = !notice.isRead
    notifications.value = notifications.value.filter(item => item.noticeId !== notice.noticeId)
    if (wasUnread) unreadNotificationCount.value = Math.max(0, unreadNotificationCount.value - 1)
    await loadNotifications()
  } catch (error) {
    notificationError.value = error?.message || '删除通知失败，请重试。'
  } finally {
    noticeDeletingId.value = null
  }
}

function formatNoticeTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

async function syncWorkspace({ silent = false } = {}) {
  if (syncing.value) return
  syncing.value = true
  if (!silent) syncStatus.value = ''
  try {
    await workspace.refreshWorkspace()
    if (!silent) setSyncStatus('工作区和会话列表已同步', 'success')
  } catch (error) {
    if (!silent) setSyncStatus(error?.message || '同步失败，请稍后重试。', 'error')
  } finally {
    syncing.value = false
  }
}

function configureAutoSync() {
  if (autoSyncTimer) {
    window.clearInterval(autoSyncTimer)
    autoSyncTimer = null
  }
  if (!autoSyncEnabled.value) return
  autoSyncTimer = window.setInterval(() => syncWorkspace({ silent: true }), 10 * 60 * 1000)
}

function handleWindowFocus() {
  if (autoSyncEnabled.value) syncWorkspace({ silent: true })
  if (activeSessionId.value) taskRuntime.refreshActiveTasks(activeSessionId.value)
}

function isRuntimeTaskActive(task) {
  return ['CREATED', 'ACCEPTED', 'PENDING', 'QUEUED', 'RUNNING', 'PROCESSING', 'IN_PROGRESS', 'CANCELLING']
    .includes(String(task?.status || '').toUpperCase()) || Boolean(task?._finalizationPending)
}

function setSyncStatus(message, tone) {
  syncStatus.value = message
  syncStatusTone.value = tone
  if (syncStatusTimer) window.clearTimeout(syncStatusTimer)
  syncStatusTimer = window.setTimeout(() => {
    syncStatus.value = ''
    syncStatusTone.value = ''
  }, 3200)
}

async function handleCreateSession() {
  await workspace.createSessionInActiveProject()
  closeContextMenu()
  closeMobileNav()
  router.push('/chat')
}

async function createSessionInProject(projectId) {
  workspace.selectProject(projectId)
  await workspace.createSessionInActiveProject()
  closeContextMenu()
  router.push('/chat')
}

function handleProjectClick(project) {
  if (activeProjectId.value === project.id && !project.collapsed) {
    workspace.toggleProject(project.id)
    return
  }

  workspace.selectProject(project.id)
}

function openSession(projectId, sessionId) {
  workspace.selectSession(projectId, sessionId)
  closeContextMenu()
  router.push('/chat')
}

function openCreateProjectDialog() {
  closeContextMenu()
  editorDialog.open = true
  editorDialog.mode = 'create-project'
  editorDialog.projectId = ''
  editorDialog.sessionId = ''
  editorDialog.value = ''
}

function openRenameDialogFromMenu() {
  if (!contextMenu.value) return

  editorDialog.open = true
  editorDialog.mode = contextMenu.value.type === 'project' ? 'rename-project' : 'rename-session'
  editorDialog.projectId = contextMenu.value.projectId || ''
  editorDialog.sessionId = contextMenu.value.sessionId || ''
  editorDialog.value = resolveCurrentName()
  closeContextMenu()
}

function deleteFromMenu() {
  if (!contextMenu.value) return

  if (contextMenu.value.type === 'project') {
    const targetProject = projectTree.value.find(item => item.id === contextMenu.value.projectId)
    openDeleteDialog({
      type: 'project',
      projectId: contextMenu.value.projectId,
      targetName: targetProject?.name || '未命名项目',
      sessionCount: targetProject?.sessions?.length || 0
    })
    return
  }

  const session = projectTree.value
    .flatMap(project => project.sessions)
    .find(item => item.sessionId === contextMenu.value.sessionId)
  openDeleteDialog({
    type: 'session',
    sessionId: contextMenu.value.sessionId,
    targetName: session?.displayTitle || '当前会话'
  })
}

function openDeleteDialog(payload) {
  deleteDialog.open = true
  deleteDialog.type = payload.type
  deleteDialog.projectId = payload.projectId || ''
  deleteDialog.sessionId = payload.sessionId || ''
  deleteDialog.targetName = payload.targetName || ''
  deleteDialog.sessionCount = payload.sessionCount || 0
  deleteDialog.submitting = false
  deleteDialog.error = ''
  closeContextMenu()
}

function closeDeleteDialog(force = false) {
  if (deleteDialog.submitting && !force) return
  deleteDialog.open = false
  deleteDialog.type = ''
  deleteDialog.projectId = ''
  deleteDialog.sessionId = ''
  deleteDialog.targetName = ''
  deleteDialog.sessionCount = 0
  deleteDialog.error = ''
}

async function confirmDelete() {
  if (!deleteDialog.open || deleteDialog.submitting) return
  deleteDialog.submitting = true
  deleteDialog.error = ''
  try {
    if (deleteDialog.type === 'project') {
      await workspace.deleteProject(deleteDialog.projectId)
    } else {
      await workspace.deleteSession(deleteDialog.sessionId)
    }
    closeDeleteDialog(true)
    router.push('/chat')
  } catch (error) {
    deleteDialog.error = error?.message || (deleteDialog.type === 'project' ? '删除项目失败，请稍后重试。' : '删除会话失败，请稍后重试。')
  } finally {
    deleteDialog.submitting = false
  }
}

function closeEditorDialog() {
  editorDialog.open = false
  editorDialog.projectId = ''
  editorDialog.sessionId = ''
  editorDialog.value = ''
}

function submitEditorDialog() {
  const value = editorDialog.value.trim()
  if (!value) return

  if (editorDialog.mode === 'create-project') {
    workspace.createProject(value)
    closeEditorDialog()
    return
  }

  if (editorDialog.mode === 'rename-project') {
    workspace.renameProject(editorDialog.projectId, value)
    closeEditorDialog()
    return
  }

  workspace.renameSession(editorDialog.sessionId, value)
  closeEditorDialog()
}

function openContextMenu(event, payload) {
  const safeX = Math.min(event.clientX, window.innerWidth - 212)
  const safeY = Math.min(event.clientY, window.innerHeight - 120)

  contextMenu.value = {
    x: Math.max(12, safeX),
    y: Math.max(12, safeY),
    ...payload
  }
}

function closeContextMenu() {
  contextMenu.value = null
}

function resolveCurrentName() {
  if (!contextMenu.value) return ''

  if (contextMenu.value.type === 'project') {
    return projectTree.value.find(item => item.id === contextMenu.value.projectId)?.name || ''
  }

  return (
    projectTree.value
      .flatMap(project => project.sessions)
      .find(item => item.sessionId === contextMenu.value.sessionId)?.displayTitle || ''
  )
}

function currentSettingsSection() {
  const section = String(route.query.section || '').trim()
  return settingsLinks.some(item => item.section === section) ? section : 'settings'
}

function isMainNavActive(item) {
  return route.path === item.path
}

function isSettingsNavActive(item) {
  return route.path === '/settings' && currentSettingsSection() === item.section
}

async function openAgentDispatchCenter() {
  closeMobileNav()
  const task = activeRuntimeTask.value
  const sessionId = String(task?.sessionId || '').trim()
  if (sessionId && sessionId !== activeSessionId.value) {
    let project = projectTree.value.find(item => item.sessions.some(session => session.sessionId === sessionId))
    if (!project) {
      await workspace.refreshWorkspace().catch(() => null)
      project = projectTree.value.find(item => item.sessions.some(session => session.sessionId === sessionId))
    }
    if (!project) {
      setSyncStatus('任务对应会话暂未加载，请同步工作区后重试。', 'error')
      return
    }
    workspace.selectSession(project.id, sessionId)
    await taskRuntime.refreshActiveTasks(sessionId)
  }
  if (task?.taskId) taskRuntime.focusTask(task.taskId)
  await router.push('/agent-dispatch')
}

async function toggleMobileNav() {
  if (mobileNavOpen.value) {
    closeMobileNav()
    return
  }
  mobileNavOpen.value = true
  await nextTick()
  firstFocusableInSideNav()?.focus()
}

function closeMobileNav(restoreFocus = true) {
  const wasOpen = mobileNavOpen.value
  mobileNavOpen.value = false
  if (wasOpen && restoreFocus) nextTick(() => mobileMenuButtonRef.value?.focus())
}

function focusableItemsInSideNav() {
  if (!sideNavRef.value) return []
  return [...sideNavRef.value.querySelectorAll('a[href], button:not(:disabled), [tabindex]:not([tabindex="-1"])')]
    .filter(element => !element.hasAttribute('hidden') && element.getAttribute('aria-hidden') !== 'true')
}

function firstFocusableInSideNav() {
  return focusableItemsInSideNav()[0] || null
}

function handleSidebarToggle() {
  if (window.innerWidth <= 1024) {
    closeMobileNav()
    return
  }
  collapseSidebar()
}

function handleSideNavClick(event) {
  if (window.innerWidth > 1024) return
  const target = event.target instanceof Element ? event.target : null
  if (target?.closest('a') || target?.closest('.new-session')) closeMobileNav(false)
}

function handleViewportChange() {
  isMobileViewport.value = window.innerWidth <= 1024
  if (!isMobileViewport.value) closeMobileNav(false)
}

function clampSidebarWidth(value) {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return SIDEBAR_MIN_WIDTH
  return Math.min(SIDEBAR_MAX_WIDTH, Math.max(SIDEBAR_MIN_WIDTH, numeric))
}

function loadSidebarLayout() {
  try {
    const raw = localStorage.getItem(sidebarLayoutKey())
    if (!raw) return

    const parsed = JSON.parse(raw)
    sidebarWidth.value = clampSidebarWidth(parsed?.width)
    sidebarCollapsed.value = Boolean(parsed?.collapsed)
  } catch {
    sidebarWidth.value = SIDEBAR_MIN_WIDTH
    sidebarCollapsed.value = false
  }
}

function persistSidebarLayout() {
  localStorage.setItem(
    sidebarLayoutKey(),
    JSON.stringify({
      width: sidebarWidth.value,
      collapsed: sidebarCollapsed.value
    })
  )
}

function sidebarLayoutKey() {
  const userId = String(workspace.state.currentUserId || '').trim() || 'anonymous'
  return `${SIDEBAR_LAYOUT_KEY_PREFIX}:${userId}`
}

function expandSidebar() {
  sidebarCollapsed.value = false
  sidebarWidth.value = clampSidebarWidth(sidebarWidth.value)
  persistSidebarLayout()
}

function collapseSidebar() {
  sidebarCollapsed.value = true
  persistSidebarLayout()
}

function startSidebarResize(event) {
  if (window.innerWidth <= 1024) return

  closeContextMenu()
  sidebarCollapsed.value = false
  sidebarDragging.value = true

  const startX = event.clientX
  const startWidth = sidebarWidth.value

  const handlePointerMove = moveEvent => {
    sidebarWidth.value = clampSidebarWidth(startWidth + (moveEvent.clientX - startX))
  }

  const handlePointerUp = () => {
    sidebarDragging.value = false
    persistSidebarLayout()
    window.removeEventListener('pointermove', handlePointerMove)
    window.removeEventListener('pointerup', handlePointerUp)
    stopSidebarDrag = null
  }

  stopSidebarDrag = handlePointerUp
  window.addEventListener('pointermove', handlePointerMove)
  window.addEventListener('pointerup', handlePointerUp)
}

function handleGlobalPointer(event) {
  const target = event.target instanceof Element ? event.target : null
  if (notificationsOpen.value && !target?.closest('.notice-menu')) {
    notificationsOpen.value = false
  }
  if (!contextMenu.value) return
  if (target?.closest('.sidebar-context-menu')) return

  closeContextMenu()
}

function handleGlobalKeydown(event) {
  if (event.key === 'Tab' && mobileNavOpen.value && isMobileViewport.value) {
    const focusable = focusableItemsInSideNav()
    if (!focusable.length) return
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    const active = document.activeElement
    if (event.shiftKey && (active === first || !sideNavRef.value?.contains(active))) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && (active === last || !sideNavRef.value?.contains(active))) {
      event.preventDefault()
      first.focus()
    }
    return
  }
  if (event.key !== 'Escape') return
  if (deleteDialog.open) {
    closeDeleteDialog()
    return
  }
  if (mobileNavOpen.value) {
    closeMobileNav()
    return
  }
  notificationsOpen.value = false
  closeContextMenu()
  if (editorDialog.open) closeEditorDialog()
}

watch(mobileNavOpen, value => {
  document.body.classList.toggle('mobile-nav-lock', value)
})

watch(autoSyncEnabled, () => {
  configureAutoSync()
})

watch(showNotifications, visible => {
  if (!visible) notificationsOpen.value = false
})

watch(activeRuntimeTask, task => {
  if (task?.taskId) {
    taskRuntime.retainTask(task.taskId)
    announcedTaskId.value = task.taskId
  }
})

watch(activeSessionId, sessionId => {
  if (sessionId && taskRuntime.state.initialized) taskRuntime.refreshActiveTasks(sessionId)
})

watch(() => route.fullPath, () => {
  closeMobileNav(false)
})

onMounted(async () => {
  await workspace.initialize()
  loadSidebarLayout()
  await taskRuntime.initialize({ sessionId: workspace.state.activeSessionId })
  await preferences.initialize(workspace.state.currentUserId)
  configureAutoSync()
  if (showNotifications.value) loadNotifications()
  window.addEventListener('pointerdown', handleGlobalPointer)
  window.addEventListener('keydown', handleGlobalKeydown)
  window.addEventListener('resize', handleViewportChange)
  window.addEventListener('focus', handleWindowFocus)
})

onBeforeUnmount(() => {
  window.removeEventListener('pointerdown', handleGlobalPointer)
  window.removeEventListener('keydown', handleGlobalKeydown)
  window.removeEventListener('resize', handleViewportChange)
  window.removeEventListener('focus', handleWindowFocus)
  document.body.classList.remove('mobile-nav-lock')
  stopSidebarDrag?.()
  if (syncStatusTimer) window.clearTimeout(syncStatusTimer)
  if (autoSyncTimer) window.clearInterval(autoSyncTimer)
  taskRuntime.dispose()
})
</script>

<style scoped>
.shell-task-status-announcement {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.task-runtime-indicator {
  min-height: 36px;
  max-width: 220px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 10px;
  border: 1px solid var(--outline-variant);
  border-radius: 6px;
  background: var(--surface-container-low);
  color: var(--on-surface-variant);
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
  cursor: pointer;
  animation: runtime-indicator-in 240ms ease-out;
  transition: transform 240ms cubic-bezier(0.25, 1, 0.5, 1), opacity 240ms cubic-bezier(0.25, 1, 0.5, 1);
}

.task-runtime-indicator:hover { transform: translateY(-1px); }
.task-runtime-indicator:focus-visible { outline: 3px solid var(--secondary); outline-offset: 2px; }
.task-runtime-indicator.running { border-color: rgba(0, 40, 142, .28); color: var(--primary); }
.task-runtime-indicator.reconnecting,
.task-runtime-indicator.cancelling { border-color: rgba(138, 89, 0, .34); color: var(--warning); }
.task-runtime-indicator .material-symbols-outlined { flex: 0 0 auto; font-size: 18px; }
.task-runtime-label { min-width: 0; overflow: hidden; text-overflow: ellipsis; }

@keyframes runtime-indicator-in {
  from { opacity: .65; transform: translateY(-2px); }
}

@media (max-width: 720px) {
  .task-runtime-indicator { max-width: 154px; overflow: hidden; text-overflow: ellipsis; }
}

@media (max-width: 390px) {
  .top-nav { gap: 2px; padding: 0 4px; }
  .mobile-menu { width: 40px; height: 40px; flex: 0 0 40px; }
  .top-title { flex: 0 1 auto; min-width: 0; overflow: hidden; }
  .top-title-name { font-size: 22px; line-height: 30px; white-space: nowrap; }
  .top-actions { min-width: 0; max-width: calc(100% - 44px); flex: 1 1 auto; justify-content: flex-end; gap: 2px; }
  .notice-menu { flex: 0 0 40px; }
  .top-actions .icon-button { width: 40px; height: 40px; flex: 0 0 40px; }
  .top-actions.has-runtime-task .dispatch-icon-button { display: none; }
  .task-runtime-indicator { min-width: 0; max-width: 104px; flex: 0 1 104px; height: 36px; padding: 0 6px; gap: 4px; overflow: hidden; }
  .task-runtime-indicator .material-symbols-outlined { font-size: 16px; }
}

@media (prefers-reduced-motion: reduce) {
  .task-runtime-indicator { animation: none; transition: none; }
}
</style>
