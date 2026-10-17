<template>
  <section class="page settings-page">
    <div class="page-title-row settings-header">
      <div>
        <h2>个人中心</h2>
      </div>
    </div>

    <div class="settings-shell">
      <aside class="settings-tabs panel">
        <button
          v-for="item in sections"
          :key="item.key"
          type="button"
          class="settings-tab"
          :class="{ active: activeSection === item.key }"
          @click="switchSection(item.key)"
        >
          <span class="material-symbols-outlined">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </button>
      </aside>

      <div class="settings-content">
        <section v-if="activeSection === 'user'" class="panel settings-panel">
          <div class="profile-hero">
            <button
              class="profile-avatar-button"
              type="button"
              :disabled="avatarUploading"
              :aria-label="avatarUploading ? '正在上传头像' : '更换头像'"
              title="更换头像"
              @click="openAvatarPicker"
            >
              <img v-if="avatarUrl" :src="avatarUrl" :alt="`${profile.nickName || profile.userName || '当前账号'}的头像`" />
              <span v-else class="profile-avatar-fallback" aria-hidden="true">{{ profileInitial }}</span>
              <span class="profile-avatar-action" aria-hidden="true">
                <span class="material-symbols-outlined" :class="{ spinning: avatarUploading }">{{ avatarUploading ? 'progress_activity' : 'photo_camera' }}</span>
              </span>
            </button>
            <input ref="avatarInput" class="visually-hidden" type="file" accept="image/jpeg,image/png,image/gif" aria-label="选择头像图片" @change="handleAvatarChange" />
            <div>
              <span class="section-kicker">账号资料</span>
              <h3>个人资料</h3>
              <p>头像和昵称会同步到当前登录账号。</p>
            </div>
            <button class="ghost-button avatar-change-button" type="button" :disabled="avatarUploading" @click="openAvatarPicker">
              <span class="material-symbols-outlined">image</span>
              {{ avatarUploading ? '正在上传' : '更换头像' }}
            </button>
          </div>

          <div class="profile-form-grid">
            <label class="profile-field">
              <span>昵称</span>
              <input v-model.trim="nickNameDraft" class="control" maxlength="30" autocomplete="nickname" placeholder="设置一个用于学习端展示的昵称" />
              <small>最多 30 个字符。</small>
            </label>
            <article class="profile-account-card">
              <div>
                <span class="info-label">登录账号</span>
                <strong>{{ profile.userName || '当前账号' }}</strong>
                <small>账号名由登录凭据决定，不能在此修改。</small>
              </div>
              <span class="material-symbols-outlined" aria-hidden="true">verified_user</span>
            </article>
          </div>

          <section class="coordinator-name-section" aria-labelledby="coordinator-name-title">
            <header>
              <div>
                <span class="section-kicker">AI 助手</span>
                <h3 id="coordinator-name-title">主协调智能体称呼</h3>
                <p>设置你在聊天和 Agent 协作中心看到的称呼，只改变展示名称，不会改变智能体能力或调度方式。</p>
              </div>
              <span class="material-symbols-outlined" aria-hidden="true">smart_toy</span>
            </header>

            <div class="coordinator-name-layout">
              <div class="coordinator-name-editor">
                <label for="coordinator-nickname">AI 助手称呼</label>
                <input
                  id="coordinator-nickname"
                  v-model="coordinatorNicknameDraft"
                  class="control"
                  type="text"
                  autocomplete="off"
                  placeholder="例如：学习搭档"
                  :aria-invalid="Boolean(coordinatorNicknameError)"
                  aria-describedby="coordinator-nickname-help"
                  @input="coordinatorFeedback = null"
                  @keydown.enter.prevent="saveCoordinatorNickname"
                />
                <div id="coordinator-nickname-help" class="coordinator-name-help">
                  <small :class="{ error: coordinatorNicknameError }">{{ coordinatorNicknameError || '最多 20 个字符，保存后会同步到当前账号。' }}</small>
                  <small>{{ coordinatorNicknameLength }} / 20</small>
                </div>
              </div>

              <div class="coordinator-name-preview" aria-label="聊天称呼预览">
                <span class="material-symbols-outlined" aria-hidden="true">smart_toy</span>
                <div>
                  <small>聊天预览</small>
                  <strong>{{ coordinatorNicknamePreview }}</strong>
                  <p>我会协调合适的专家 Agent 来完成这次学习任务。</p>
                </div>
              </div>
            </div>

            <footer class="coordinator-name-actions">
              <p v-if="coordinatorFeedback" class="profile-feedback" :class="coordinatorFeedback.type" :role="coordinatorFeedback.type === 'error' ? 'alert' : 'status'" aria-live="polite">{{ coordinatorFeedback.text }}</p>
              <small v-else>该称呼随当前账号跨设备同步；专家 Agent 的名称不会改变。</small>
              <div>
                <button class="ghost-button" type="button" :disabled="coordinatorSaving || coordinatorIsDefault" @click="restoreCoordinatorNickname">
                  <span class="material-symbols-outlined" aria-hidden="true">restart_alt</span>
                  恢复默认
                </button>
                <button class="primary-button" type="button" :disabled="!canSaveCoordinatorNickname || coordinatorSaving" @click="saveCoordinatorNickname">
                  <span class="material-symbols-outlined" :class="{ spinning: coordinatorSaving }" aria-hidden="true">{{ coordinatorSaving ? 'progress_activity' : 'save' }}</span>
                  {{ coordinatorSaving ? '正在保存' : '保存称呼' }}
                </button>
              </div>
            </footer>
          </section>

          <section class="profile-bio-card" aria-labelledby="profile-bio-title">
            <header>
              <div>
                <span class="section-kicker">自我介绍</span>
                <h3 id="profile-bio-title">个人简介</h3>
                <p>用几句话介绍你的学习方向、兴趣或希望获得的帮助。</p>
              </div>
              <span class="material-symbols-outlined" aria-hidden="true">badge</span>
            </header>
            <label class="profile-bio-field" for="profile-bio">
              <span class="visually-hidden">个人简介</span>
              <textarea
                id="profile-bio"
                v-model="bioDraft"
                class="control"
                maxlength="240"
                rows="5"
                placeholder="例如：我是一名正在准备算法竞赛的高中生，喜欢通过图解、代码案例和分层练习理解复杂概念。"
              ></textarea>
              <small>{{ bioDraft.length }} / 240</small>
            </label>
            <footer class="profile-bio-actions">
              <div>
                <p v-if="bioFeedback" class="profile-feedback" :class="bioFeedback.type" role="status" aria-live="polite">{{ bioFeedback.text }}</p>
              </div>
              <div>
                <button class="ghost-button" type="button" :disabled="!bioDraft || bioSaving" @click="clearBioDraft">清空</button>
                <button class="primary-button bio-save-button" type="button" :disabled="!canSaveBio || bioSaving" @click="saveBio">
                  <span class="material-symbols-outlined" :class="{ spinning: bioSaving }">{{ bioSaving ? 'progress_activity' : 'save' }}</span>
                  {{ bioSaving ? '正在保存' : '保存简介' }}
                </button>
              </div>
            </footer>
          </section>

          <section class="profile-session-card" aria-label="登录状态">
            <div class="profile-session-copy">
              <span class="profile-session-status"><span aria-hidden="true"></span>当前已登录</span>
              <strong>{{ profile.userName || '当前账号' }}</strong>
              <small>退出后需要重新登录才能继续访问学习资料和会话。</small>
            </div>
            <button class="danger-button profile-logout-button" type="button" @click="requestLogout($event.currentTarget)">
              <span class="material-symbols-outlined">logout</span>
              退出登录
            </button>
          </section>

          <footer class="profile-actions">
            <p v-if="profileFeedback" class="profile-feedback" :class="profileFeedback.type" role="status" aria-live="polite">{{ profileFeedback.text }}</p>
            <span v-else></span>
            <button class="primary-button" type="button" :disabled="!canSaveProfile || profileSaving" @click="saveProfile">
              <span class="material-symbols-outlined" :class="{ spinning: profileSaving }">{{ profileSaving ? 'progress_activity' : 'save' }}</span>
              {{ profileSaving ? '正在保存' : '保存昵称' }}
            </button>
          </footer>
        </section>

        <section v-else-if="activeSection === 'menu'" class="panel settings-panel">
          <div class="section-heading">
            <div>
              <h3>菜单</h3>
              <p>控制学习端常用入口的显示与顺序。聊天是核心入口，始终保留。</p>
            </div>
          </div>

          <div class="menu-list">
            <article v-for="(item, index) in menuItems" :key="item.key" class="menu-item">
              <div>
                <strong>{{ item.label }}</strong>
                <small>{{ item.note }}</small>
              </div>
              <div class="menu-item-actions">
                <div class="menu-order-actions" aria-label="调整菜单顺序">
                  <button class="icon-button menu-order-button" type="button" :disabled="index === 0" :aria-label="`将${item.label}上移`" :title="`将${item.label}上移`" @click="moveMenuItem(item.key, 'up')">
                    <span class="material-symbols-outlined">arrow_upward</span>
                  </button>
                  <button class="icon-button menu-order-button" type="button" :disabled="index === menuItems.length - 1" :aria-label="`将${item.label}下移`" :title="`将${item.label}下移`" @click="moveMenuItem(item.key, 'down')">
                    <span class="material-symbols-outlined">arrow_downward</span>
                  </button>
                </div>
                <label class="menu-toggle">
                  <span class="visually-hidden">显示{{ item.label }}</span>
                  <input :checked="item.enabled" type="checkbox" :disabled="item.required" @change="setMenuEnabled(item.key, $event.target.checked)" />
                </label>
              </div>
            </article>
          </div>
          <p class="preferences-save-status" role="status" aria-live="polite">
            <span class="material-symbols-outlined" aria-hidden="true">task_alt</span>
            {{ preferencesSaveStatus }}
          </p>
        </section>

        <section v-else class="panel settings-panel">
          <div class="section-heading">
            <div>
              <h3>设置</h3>
              <p>管理通知、同步与专注模式。</p>
            </div>
          </div>

          <div class="menu-list">
            <label class="menu-item">
              <div>
                <strong>消息提醒</strong>
                <small>显示顶部的系统通知入口与未读提醒。</small>
              </div>
              <input :checked="notificationsEnabled" type="checkbox" @change="setPreference('notificationsEnabled', $event.target.checked)" />
            </label>
            <label class="menu-item">
              <div>
                <strong>自动同步</strong>
                <small>回到页面或每 10 分钟刷新项目与会话列表。</small>
              </div>
              <input :checked="autoSyncEnabled" type="checkbox" @change="setPreference('autoSyncEnabled', $event.target.checked)" />
            </label>
            <label class="menu-item">
              <div>
                <strong>专注模式</strong>
                <small>隐藏通知和调度入口，仅保留核心学习导航。</small>
              </div>
              <input :checked="focusMode" type="checkbox" @change="setPreference('focusMode', $event.target.checked)" />
            </label>
          </div>
          <p class="preferences-save-status" role="status" aria-live="polite">
            <span class="material-symbols-outlined" aria-hidden="true">task_alt</span>
            {{ preferencesSaveStatus }}
          </p>
        </section>
      </div>
    </div>

    <div v-if="logoutDialogOpen" class="profile-logout-scrim" role="presentation" @click.self="cancelLogout">
      <section ref="logoutDialog" class="profile-logout-dialog" role="dialog" aria-modal="true" aria-labelledby="profile-logout-title" aria-describedby="profile-logout-description" tabindex="-1" @keydown="handleLogoutDialogKeydown">
        <span class="material-symbols-outlined profile-logout-icon" aria-hidden="true">logout</span>
        <h3 id="profile-logout-title">确认退出登录？</h3>
        <p id="profile-logout-description">退出后，本机登录状态会被清除；下次继续学习时需要重新登录。</p>
        <p v-if="logoutError" class="profile-logout-error" role="alert">{{ logoutError }}</p>
        <div class="profile-logout-actions">
          <button class="ghost-button" type="button" :disabled="logoutPending" @click="cancelLogout">取消</button>
          <button ref="logoutConfirmButton" class="danger-button" type="button" :disabled="logoutPending" @click="confirmLogout">
            <span class="material-symbols-outlined" :class="{ spinning: logoutPending }">{{ logoutPending ? 'progress_activity' : 'logout' }}</span>
            {{ logoutPending ? '正在退出' : '确认退出' }}
          </button>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '@/services/api'
import { getAuthProfile, getToken, removeToken, setAuthProfile } from '@/services/storage'
import { usePreferencesStore } from '@/stores/preferences'
import { DEFAULT_COORDINATOR_NICKNAME, MAX_COORDINATOR_NICKNAME_LENGTH, useWorkspaceStore } from '@/stores/workspace'

const route = useRoute()
const router = useRouter()
const workspace = useWorkspaceStore()
const preferencesStore = usePreferencesStore()
const menuItems = preferencesStore.menuItems
const notificationsEnabled = preferencesStore.notificationsEnabled
const autoSyncEnabled = preferencesStore.autoSyncEnabled
const focusMode = preferencesStore.focusMode
const bio = preferencesStore.bio

const sections = [
  { key: 'user', label: '个人中心', icon: 'account_circle' },
  { key: 'menu', label: '菜单', icon: 'segment' },
  { key: 'settings', label: '设置', icon: 'settings' }
]

const profile = reactive({
  userId: '',
  userName: '',
  nickName: '',
  avatar: '',
  email: '',
  phonenumber: '',
  sex: ''
})
const nickNameDraft = ref('')
const avatarInput = ref(null)
const avatarUploading = ref(false)
const profileSaving = ref(false)
const profileFeedback = ref(null)
const coordinatorNicknameDraft = ref(DEFAULT_COORDINATOR_NICKNAME)
const coordinatorSaving = ref(false)
const coordinatorFeedback = ref(null)
const bioDraft = ref('')
const bioSaving = ref(false)
const bioFeedback = ref(null)
const logoutDialogOpen = ref(false)
const logoutPending = ref(false)
const logoutError = ref('')
const logoutDialog = ref(null)
const logoutConfirmButton = ref(null)
let lastLogoutTrigger = null
let profileContextVersion = 0
let profileContextToken = ''

const activeSection = computed(() => {
  const raw = String(route.query.section || '').trim()
  return sections.some(item => item.key === raw) ? raw : 'settings'
})

const profileInitial = computed(() => {
  const source = nickNameDraft.value || profile.nickName || profile.userName || 'U'
  return source.slice(0, 1).toUpperCase()
})
const avatarUrl = computed(() => resolveAvatarUrl(profile.avatar))
const canSaveProfile = computed(() => {
  const nextName = nickNameDraft.value.trim()
  return Boolean(nextName) && nextName !== profile.nickName && !avatarUploading.value
})
const coordinatorNicknameLength = computed(() => [...coordinatorNicknameDraft.value.trim()].length)
const coordinatorNicknameError = computed(() => {
  const nickname = coordinatorNicknameDraft.value.trim()
  if (!nickname) return 'AI 助手称呼不能为空。'
  if (/[\u0000-\u001f\u007f-\u009f]/.test(nickname)) return '不能包含换行、制表符等控制字符。'
  if (coordinatorNicknameLength.value > MAX_COORDINATOR_NICKNAME_LENGTH) return `不能超过 ${MAX_COORDINATOR_NICKNAME_LENGTH} 个字符。`
  return ''
})
const coordinatorNicknamePreview = computed(() => coordinatorNicknameDraft.value.trim() || DEFAULT_COORDINATOR_NICKNAME)
const canSaveCoordinatorNickname = computed(() => (
  !coordinatorNicknameError.value &&
  coordinatorNicknameDraft.value.trim() !== workspace.state.coordinatorNickname &&
  !coordinatorSaving.value
))
const coordinatorIsDefault = computed(() => (
  coordinatorNicknameDraft.value.trim() === DEFAULT_COORDINATOR_NICKNAME &&
  workspace.state.coordinatorNickname === DEFAULT_COORDINATOR_NICKNAME
))
const canSaveBio = computed(() => bioDraft.value !== bio.value && !bioSaving.value)
const preferencesSaveStatus = computed(() => {
  const status = preferencesStore.state.syncStatus
  if (status === 'loading') return '正在从当前账号加载偏好…'
  if (status === 'saving') return '正在同步到当前账号…'
  if (status === 'error') return preferencesStore.state.syncError || '账户偏好同步失败，请重试。'
  if (status === 'empty') return '当前账号还没有自定义偏好，修改后会自动同步。'
  const updatedAt = preferencesStore.state.updatedAt
  if (!updatedAt) return '偏好会自动同步到当前账号'
  return `已同步到当前账号 · ${new Date(updatedAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
})

function switchSection(section) {
  router.replace({ name: 'settings', query: { section } })
}

function setMenuEnabled(key, enabled) {
  preferencesStore.setMenuEnabled(key, enabled)
}

function moveMenuItem(key, direction) {
  preferencesStore.moveMenuItem(key, direction)
}

function setPreference(key, enabled) {
  preferencesStore.setPreference(key, enabled)
}

function clearBioDraft() {
  if (bioSaving.value) return
  bioDraft.value = ''
  bioFeedback.value = null
}

async function saveBio() {
  if (!canSaveBio.value || bioSaving.value) return
  bioSaving.value = true
  bioFeedback.value = null
  try {
    if (!await preferencesStore.setBio(bioDraft.value)) {
      throw new Error(preferencesStore.state.syncError || '账户资料暂时无法保存')
    }
    bioDraft.value = bio.value
    bioFeedback.value = {
      type: 'success',
      text: bio.value ? '个人简介已保存到当前账号。' : '个人简介已清空。'
    }
  } catch {
    bioFeedback.value = { type: 'error', text: preferencesStore.state.syncError || '个人简介暂时无法保存，请稍后重试。' }
  } finally {
    bioSaving.value = false
  }
}

async function loadProfile() {
  const response = await authApi.getProfile().catch(() => null) || await authApi.getInfo().catch(() => null)
  const user = response?.data || response?.user || {}
  if (!user?.userId || !getToken()) {
    throw new Error('当前登录身份无法验证，请重新登录。')
  }
  profileContextVersion += 1
  profileContextToken = getToken()
  profile.userId = user.userId || ''
  profile.userName = user.userName || ''
  profile.nickName = user.nickName || ''
  profile.avatar = user.avatar || ''
  profile.email = user.email || ''
  profile.phonenumber = user.phonenumber || ''
  profile.sex = user.sex || ''
  nickNameDraft.value = profile.nickName
}

function openAvatarPicker() {
  if (!avatarUploading.value) avatarInput.value?.click()
}

async function handleAvatarChange(event) {
  const input = event.target
  const [file] = input?.files || []
  if (!file) return

  profileFeedback.value = null
  if (!file.type.startsWith('image/')) {
    profileFeedback.value = { type: 'error', text: '请选择图片文件后再上传。' }
    input.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    profileFeedback.value = { type: 'error', text: '头像图片不能超过 5 MB。' }
    input.value = ''
    return
  }

  avatarUploading.value = true
  const requestVersion = profileContextVersion
  const requestToken = profileContextToken || getToken()
  const requestUserId = String(profile.userId || '')
  try {
    const formData = new FormData()
    formData.append('avatarfile', file)
    const response = await authApi.uploadAvatar(formData)
    const avatar = response?.imgUrl || ''
    if (!avatar) throw new Error('服务器未返回头像地址。')
    if (!isCurrentProfileContext(requestUserId, requestToken, requestVersion)) return
    profile.avatar = avatar
    syncCachedProfile(requestUserId, requestToken, requestVersion)
    profileFeedback.value = { type: 'success', text: '头像已更新。' }
  } catch (error) {
    profileFeedback.value = { type: 'error', text: error?.message || '头像上传失败，请稍后重试。' }
  } finally {
    avatarUploading.value = false
    input.value = ''
  }
}

async function saveProfile() {
  const nickName = nickNameDraft.value.trim()
  if (!nickName || profileSaving.value) return

  profileSaving.value = true
  profileFeedback.value = null
  const requestVersion = profileContextVersion
  const requestToken = profileContextToken || getToken()
  const requestUserId = String(profile.userId || '')
  try {
    await authApi.updateProfile({
      nickName,
      email: profile.email,
      phonenumber: profile.phonenumber,
      sex: profile.sex
    })
    if (!isCurrentProfileContext(requestUserId, requestToken, requestVersion)) return
    profile.nickName = nickName
    syncCachedProfile(requestUserId, requestToken, requestVersion)
    profileFeedback.value = { type: 'success', text: '昵称已保存。' }
  } catch (error) {
    profileFeedback.value = { type: 'error', text: error?.message || '昵称保存失败，请稍后重试。' }
  } finally {
    profileSaving.value = false
  }
}

async function saveCoordinatorNickname() {
  if (!canSaveCoordinatorNickname.value || coordinatorSaving.value) return
  coordinatorSaving.value = true
  coordinatorFeedback.value = null

  try {
    const savedNickname = await workspace.setCoordinatorNickname(coordinatorNicknameDraft.value)
    coordinatorNicknameDraft.value = savedNickname
    coordinatorFeedback.value = { type: 'success', text: 'AI 助手称呼已保存，并同步到当前账号。' }
  } catch (error) {
    coordinatorNicknameDraft.value = workspace.state.coordinatorNickname
    coordinatorFeedback.value = { type: 'error', text: error?.message || '称呼保存失败，已恢复保存前的名称。' }
  } finally {
    coordinatorSaving.value = false
  }
}

async function restoreCoordinatorNickname() {
  if (coordinatorSaving.value) return
  coordinatorNicknameDraft.value = DEFAULT_COORDINATOR_NICKNAME
  coordinatorFeedback.value = null

  if (workspace.state.coordinatorNickname === DEFAULT_COORDINATOR_NICKNAME) return
  await saveCoordinatorNickname()
}

function resolveAvatarUrl(value) {
  const avatar = String(value || '').trim()
  if (!avatar || /^(https?:|data:|blob:)/i.test(avatar)) return avatar
  const path = avatar.startsWith('/') ? avatar : `/${avatar}`
  return `${import.meta.env.VITE_API_BASE || '/dev-api'}${path}`
}

function isCurrentProfileContext(userId, token, version) {
  return Boolean(
    userId &&
    token &&
    version === profileContextVersion &&
    token === getToken() &&
    String(profile.userId || '') === String(userId)
  )
}

function syncCachedProfile(userId, token, version) {
  if (!isCurrentProfileContext(userId, token, version)) return false
  const current = getAuthProfile() || {}
  if (String(current.userId || '') !== String(userId)) return false
  setAuthProfile({
    ...current,
    userId: profile.userId || current.userId,
    userName: profile.userName,
    nickName: profile.nickName,
    avatar: profile.avatar
  })
  return true
}

async function requestLogout(trigger) {
  if (logoutPending.value) return
  logoutError.value = ''
  lastLogoutTrigger = trigger instanceof HTMLElement ? trigger : null
  logoutDialogOpen.value = true
  await nextTick()
  logoutConfirmButton.value?.focus()
}

async function cancelLogout() {
  if (logoutPending.value) return
  logoutError.value = ''
  logoutDialogOpen.value = false
  await nextTick()
  if (lastLogoutTrigger && document.contains(lastLogoutTrigger)) {
    lastLogoutTrigger.focus()
  }
  lastLogoutTrigger = null
}

function handleLogoutDialogKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    cancelLogout()
    return
  }
  if (event.key !== 'Tab') return

  const focusable = [...(logoutDialog.value?.querySelectorAll('button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])') || [])]
    .filter(element => element instanceof HTMLElement && !element.hasAttribute('hidden'))
  if (!focusable.length) {
    event.preventDefault()
    logoutDialog.value?.focus()
    return
  }

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

async function confirmLogout() {
  if (logoutPending.value) return
  logoutPending.value = true
  logoutError.value = ''
  profileContextVersion += 1
  profileContextToken = ''
  try {
    await authApi.logout()
    workspace.clear()
    preferencesStore.clear()
    removeToken()
    await router.replace({ name: 'login' })
  } catch (error) {
    logoutError.value = error?.message || '退出登录失败，请稍后重试。'
    try {
      await loadProfile()
    } catch {
      // Keep the dialog open so the user can retry or leave the page safely.
    }
  } finally {
    logoutPending.value = false
  }
}

onMounted(async () => {
  try {
    await loadProfile()
    await workspace.initialize()
    coordinatorNicknameDraft.value = workspace.state.coordinatorNickname
    await preferencesStore.initialize(profile.userId)
    bioDraft.value = bio.value
  } catch {
    removeToken()
    await router.replace({ name: 'login', query: { redirect: route.fullPath } })
  }
})
</script>

<style scoped>
.settings-header {
  margin-bottom: var(--space-lg);
}

.settings-shell {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: var(--space-lg);
}

.settings-tabs {
  height: fit-content;
  display: grid;
  gap: var(--space-xs);
  padding: var(--space-sm);
}

.settings-tab {
  min-height: 48px;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--on-surface-variant);
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 0 var(--space-sm);
  text-align: left;
  transition: background 160ms ease, color 160ms ease;
}

.settings-tab:hover {
  background: var(--surface-container);
  color: var(--primary);
}

.settings-tab.active {
  background: var(--surface-container);
  color: var(--primary);
  font-weight: 700;
}

.settings-content {
  min-width: 0;
}

.settings-panel {
  display: grid;
  gap: var(--space-lg);
}

.section-heading {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.section-heading h3 {
  margin: 0 0 var(--space-xs);
  font-size: 28px;
  line-height: 36px;
}

.section-heading p {
  margin: 0;
  color: var(--on-surface-variant);
}

.profile-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-lg);
}

.profile-avatar-button {
  position: relative;
  width: 88px;
  height: 88px;
  display: grid;
  place-items: center;
  overflow: hidden;
  padding: 0;
  border: 1px solid var(--outline-variant);
  border-radius: 50%;
  background: var(--primary-container);
  color: var(--on-primary);
  cursor: pointer;
}

.profile-avatar-button:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--primary) 55%, transparent);
  outline-offset: 3px;
}

.profile-avatar-button:disabled {
  cursor: wait;
}

.profile-avatar-button img,
.profile-avatar-fallback {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
}

.profile-avatar-button img {
  object-fit: cover;
}

.profile-avatar-fallback {
  font-size: 30px;
  font-weight: 700;
}

.profile-avatar-action {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: 2px solid var(--surface-container-lowest);
  border-radius: 50%;
  background: var(--on-surface);
  color: var(--surface-container-lowest);
}

.profile-avatar-action .material-symbols-outlined {
  font-size: 16px;
}

.section-kicker {
  display: block;
  margin-bottom: 4px;
  color: var(--primary);
  font-size: 13px;
  font-weight: 700;
  line-height: 18px;
}

.profile-hero h3 {
  margin: 0 0 4px;
  font-size: 24px;
  line-height: 32px;
}

.profile-hero p {
  margin: 0;
  color: var(--on-surface-variant);
}

.avatar-change-button {
  white-space: nowrap;
}

.profile-form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(260px, 0.75fr);
  gap: var(--space-md);
}

.profile-field,
.profile-account-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--outline-variant);
  background: var(--surface-container-lowest);
  padding: var(--space-lg);
}

.profile-field {
  display: grid;
  gap: var(--space-xs);
}

.profile-field > span {
  color: var(--on-surface);
  font-size: 14px;
  font-weight: 700;
}

.profile-field small,
.profile-account-card small {
  color: var(--on-surface-variant);
  line-height: 20px;
}

.coordinator-name-section {
  display: grid;
  gap: var(--space-md);
  padding: var(--space-lg) 0;
  border-top: 1px solid var(--outline-variant);
  border-bottom: 1px solid var(--outline-variant);
}

.coordinator-name-section > header,
.coordinator-name-actions,
.coordinator-name-actions > div {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-md);
}

.coordinator-name-section h3 {
  margin: 3px 0 6px;
  color: var(--on-surface);
  font-size: 20px;
  line-height: 28px;
}

.coordinator-name-section header p,
.coordinator-name-actions > small {
  margin: 0;
  color: var(--on-surface-variant);
  font-size: 13px;
  line-height: 20px;
}

.coordinator-name-section > header > .material-symbols-outlined {
  flex: 0 0 auto;
  color: var(--primary);
  font-size: 28px;
}

.coordinator-name-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, .8fr);
  gap: var(--space-md);
  align-items: stretch;
}

.coordinator-name-editor {
  display: grid;
  align-content: start;
  gap: 7px;
}

.coordinator-name-editor > label {
  color: var(--on-surface);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.coordinator-name-editor .control[aria-invalid="true"] {
  border-color: var(--error);
}

.coordinator-name-help {
  display: flex;
  justify-content: space-between;
  gap: var(--space-sm);
  color: var(--on-surface-variant);
}

.coordinator-name-help small {
  font-size: 12px;
  line-height: 18px;
}

.coordinator-name-help .error {
  color: var(--error);
}

.coordinator-name-preview {
  min-width: 0;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-sm);
  padding: var(--space-md);
  border: 1px solid var(--outline-variant);
  border-left: 3px solid var(--primary);
  border-radius: var(--radius-md);
  background: var(--surface-container-low);
}

.coordinator-name-preview > .material-symbols-outlined {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--primary);
  color: var(--on-primary);
  font-size: 20px;
}

.coordinator-name-preview small,
.coordinator-name-preview strong,
.coordinator-name-preview p {
  display: block;
}

.coordinator-name-preview small {
  color: var(--on-surface-variant);
  font-size: 11px;
  line-height: 16px;
}

.coordinator-name-preview strong {
  margin-top: 1px;
  overflow-wrap: anywhere;
  color: var(--on-surface);
  font-size: 15px;
  line-height: 21px;
}

.coordinator-name-preview p {
  margin: 4px 0 0;
  color: var(--on-surface-variant);
  font-size: 12px;
  line-height: 18px;
}

.coordinator-name-actions {
  align-items: center;
}

.coordinator-name-actions > div {
  flex: 0 0 auto;
}

.coordinator-name-actions .profile-feedback {
  margin: 0;
}

.coordinator-name-actions button {
  white-space: nowrap;
}

.profile-bio-card {
  display: grid;
  gap: var(--space-md);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--outline-variant);
}

.profile-bio-card header,
.profile-bio-actions,
.profile-bio-actions > div:last-child {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-md);
}

.profile-bio-card h3 {
  margin: 3px 0 6px;
  color: var(--on-surface);
  font-size: 20px;
  line-height: 28px;
}

.profile-bio-card header p,
.profile-bio-actions small {
  margin: 0;
  color: var(--on-surface-variant);
  font-size: 13px;
  line-height: 20px;
}

.profile-bio-card > header > .material-symbols-outlined {
  color: var(--secondary);
  font-size: 28px;
}

.profile-bio-field {
  display: grid;
  gap: 6px;
}

.profile-bio-field textarea {
  min-height: 132px;
  resize: vertical;
}

.profile-bio-field small {
  justify-self: end;
  color: var(--on-surface-variant);
  font-size: 12px;
  line-height: 16px;
}

.profile-bio-actions > div:first-child {
  min-width: 0;
}

.profile-bio-actions .profile-feedback {
  margin: 0;
}

.bio-save-button {
  width: auto;
  white-space: nowrap;
}

.profile-account-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-md);
}

.profile-account-card > .material-symbols-outlined {
  flex: 0 0 auto;
  color: var(--success);
}

.info-label {
  color: var(--on-surface-variant);
  font-size: 13px;
  line-height: 18px;
}

.profile-account-card strong {
  font-size: 18px;
  line-height: 28px;
}

.profile-actions {
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
}

.profile-session-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
  padding: var(--space-md) var(--space-lg);
  border: 1px solid var(--outline-variant);
  border-radius: var(--radius-lg);
  background: var(--surface-container-low);
}

.profile-session-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.profile-session-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--success);
  font-size: 13px;
  font-weight: 700;
  line-height: 18px;
}

.profile-session-status > span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.profile-session-copy strong {
  overflow: hidden;
  color: var(--on-surface);
  font-size: 16px;
  line-height: 24px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-session-copy small {
  color: var(--on-surface-variant);
  line-height: 20px;
}

.profile-logout-button {
  flex: 0 0 auto;
  white-space: nowrap;
}

.profile-logout-scrim {
  position: fixed;
  inset: 0;
  z-index: 110;
  display: grid;
  place-items: center;
  padding: var(--space-lg);
  background: rgba(13, 28, 46, 0.32);
}

.profile-logout-dialog {
  width: min(390px, 100%);
  display: grid;
  justify-items: center;
  gap: var(--space-sm);
  padding: var(--space-lg);
  border: 1px solid var(--outline-variant);
  border-radius: var(--radius-lg);
  background: var(--surface-container-lowest);
  box-shadow: 0 24px 58px rgba(13, 28, 46, 0.24);
  text-align: center;
}

.profile-logout-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: var(--error-container);
  color: var(--error);
  font-size: 24px;
}

.profile-logout-dialog h3,
.profile-logout-dialog p {
  margin: 0;
}

.profile-logout-dialog h3 {
  color: var(--on-surface);
  font-size: 20px;
  line-height: 28px;
}

.profile-logout-dialog > p {
  color: var(--on-surface-variant);
  font-size: 14px;
  line-height: 22px;
}

.profile-logout-error {
  width: 100%;
  box-sizing: border-box;
  padding: var(--space-sm);
  border-radius: var(--radius-md);
  background: var(--error-container);
  color: var(--on-error-container) !important;
}

.profile-logout-actions {
  width: 100%;
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  margin-top: var(--space-xs);
}

.profile-feedback {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
}

.profile-feedback.success {
  color: var(--success);
}

.profile-feedback.error {
  color: var(--error);
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  clip-path: inset(50%);
}

.menu-list {
  display: grid;
  gap: var(--space-sm);
}

.menu-item {
  border-radius: var(--radius-lg);
  border: 1px solid var(--outline-variant);
  background: var(--surface-container-lowest);
  padding: var(--space-md) var(--space-lg);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
}

.menu-item strong {
  display: block;
  margin-bottom: 4px;
}

.menu-item small {
  color: var(--on-surface-variant);
  line-height: 20px;
}

.menu-item input {
  width: 18px;
  height: 18px;
  accent-color: var(--primary);
}

.menu-item-actions,
.menu-order-actions {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.menu-order-button {
  width: 34px;
  height: 34px;
  padding: 0;
}

.menu-order-button .material-symbols-outlined {
  font-size: 18px;
}

.menu-toggle {
  display: inline-flex;
  align-items: center;
}

.preferences-save-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--success);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.preferences-save-status .material-symbols-outlined {
  font-size: 18px;
}

@media (max-width: 1180px) {
  .settings-shell {
    grid-template-columns: 1fr;
  }

  .settings-tabs {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .profile-form-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .settings-tabs {
    grid-template-columns: 1fr;
  }

  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .profile-hero {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .avatar-change-button {
    grid-column: 1 / -1;
    justify-self: start;
  }

  .profile-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .profile-session-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .profile-actions .primary-button {
    justify-content: center;
  }

  .menu-item {
    align-items: flex-start;
  }

  .menu-item-actions {
    align-self: center;
  }

  .profile-bio-card header,
  .profile-bio-actions,
  .coordinator-name-section > header,
  .coordinator-name-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .profile-bio-actions > div:last-child,
  .coordinator-name-actions > div {
    width: 100%;
  }

  .profile-bio-actions button,
  .coordinator-name-actions button {
    flex: 1;
  }

  .coordinator-name-layout {
    grid-template-columns: 1fr;
  }
}
</style>
