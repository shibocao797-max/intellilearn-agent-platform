<template>
  <section class="transfer-page">
    <div class="transfer-shell panel">
      <div class="transfer-head">
        <div>
          <p class="transfer-kicker">工作区迁移</p>
          <h1>工作区迁移</h1>
          <p class="transfer-copy">
            工作区导入必须绑定当前登录账号，并同步到该账号的工作区；旧 JSON 可手动粘贴后导入。
          </p>
        </div>
        <div class="transfer-origin">
          <span>当前地址</span>
          <strong>{{ currentOrigin }}</strong>
        </div>
      </div>

      <div class="transfer-actions">
        <button class="primary-button transfer-button" type="button" @click="loadCurrentWorkspace">
          读取当前工作区
        </button>
        <button class="ghost-button transfer-button" type="button" @click="copyWorkspace">
          复制内容
        </button>
        <button class="ghost-button transfer-button" type="button" @click="importWorkspace">
          导入到当前地址
        </button>
        <button class="ghost-button transfer-button" type="button" @click="openChat">
          返回聊天页
        </button>
      </div>

      <label class="field transfer-field">
        <span>工作区 JSON</span>
        <textarea
          v-model="workspacePayload"
          class="transfer-textarea"
          spellcheck="false"
          placeholder="先读取当前工作区，或把旧地址导出的 JSON 粘贴到这里。"
        ></textarea>
      </label>

      <div class="transfer-status" :class="{ error: statusTone === 'error', success: statusTone === 'success' }">
        {{ statusText }}
      </div>

      <div class="transfer-notes">
        <p>会恢复：项目分组、项目名、会话别名、当前选中项。</p>
      <p>不会恢复：后端不存在的会话；导入不会修改登录态，只会更新当前账号的工作区。</p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { authApi, eduApi } from '@/services/api'
import { getToken, setAuthProfile } from '@/services/storage'

const ACCOUNT_STORAGE_KEY_PREFIX = 'eduai-workspace-v2'

const workspacePayload = ref('')
const statusText = ref('准备就绪。可以先读取当前工作区，或粘贴旧地址导出的 JSON。')
const statusTone = ref('neutral')
const currentOrigin = computed(() => window.location.origin)

async function loadCurrentWorkspace() {
  const token = getToken()
  if (!token) {
    workspacePayload.value = ''
    statusTone.value = 'error'
    statusText.value = '请先登录账号，再读取当前账号工作区。旧 JSON 可手动粘贴到下方文本框。'
    return
  }
  const account = await resolveCurrentAccount()
  if (!account || account.token !== getToken()) {
    statusTone.value = 'error'
    statusText.value = '当前登录身份无法验证，请重新登录后再读取工作区。'
    return
  }

  const raw = localStorage.getItem(storageKeyForUser(account.userId)) || ''
  workspacePayload.value = raw
  statusTone.value = raw ? 'success' : 'neutral'
  statusText.value = raw
    ? `已读取当前账号的本地工作区缓存，长度 ${raw.length}。`
    : '当前账号没有本地工作区缓存。'
}

async function copyWorkspace() {
  if (!workspacePayload.value) {
    statusTone.value = 'error'
    statusText.value = '没有可复制的工作区内容。'
    return
  }

  try {
    await navigator.clipboard.writeText(workspacePayload.value)
    statusTone.value = 'success'
    statusText.value = '已复制到剪贴板。'
  } catch {
    statusTone.value = 'error'
    statusText.value = '复制失败，请手动选中文本复制。'
  }
}

async function importWorkspace() {
  const raw = workspacePayload.value.trim()
  if (!raw) {
    statusTone.value = 'error'
    statusText.value = '请先粘贴工作区 JSON。'
    return
  }

  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed?.projects)) {
      throw new Error('缺少 projects 数组')
    }

    const account = await resolveCurrentAccount()
    const requestToken = getToken()
    if (!account || account.token !== requestToken) {
      statusTone.value = 'error'
      statusText.value = '请先登录要接收工作区的账号，再执行导入。'
      return
    }

    await eduApi.saveWorkspace(parsed)
    if (getToken() !== requestToken) {
      statusTone.value = 'error'
      statusText.value = '登录账号已切换，本次导入结果未写入本地缓存。'
      return
    }
    localStorage.setItem(storageKeyForUser(account.userId), JSON.stringify(parsed))
    statusTone.value = 'success'
    statusText.value = '已导入并同步到当前账号。回到聊天页后刷新即可看到恢复结果。'
  } catch (error) {
    statusTone.value = 'error'
    statusText.value = `导入失败：${error?.message || 'JSON 格式不正确'}`
  }
}

async function resolveCurrentAccount() {
  const tokenAtStart = getToken()
  if (!tokenAtStart) return ''
  const info = await authApi.getInfo().catch(() => null)
  if (!info || tokenAtStart !== getToken() || !info.user?.userId) return ''

  setAuthProfile({
    userId: info.user.userId,
    userName: info.user.userName || '',
    nickName: info.user.nickName || '',
    avatar: info.user.avatar || '',
    roles: Array.isArray(info.roles) ? info.roles : [],
    userType: info.userType || ''
  })
  return { userId: String(info.user.userId), token: tokenAtStart }
}

function storageKeyForUser(userId) {
  return `${ACCOUNT_STORAGE_KEY_PREFIX}:${userId}`
}

function openChat() {
  window.location.href = '/chat'
}
</script>

<style scoped>
.transfer-page {
  min-height: 100vh;
  padding: clamp(24px, 5vw, 56px);
  display: grid;
  place-items: center;
  background:
    radial-gradient(circle at top left, rgba(109, 40, 217, 0.12), transparent 32%),
    linear-gradient(180deg, #f7f8ff 0%, #eef2ff 100%);
}

.transfer-shell {
  width: min(960px, 100%);
  padding: clamp(24px, 4vw, 40px);
  display: grid;
  gap: 24px;
}

.transfer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.transfer-kicker {
  margin: 0 0 8px;
  color: var(--primary);
  font-family: "JetBrains Mono", monospace;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.transfer-head h1 {
  margin: 0 0 10px;
  font-size: clamp(32px, 5vw, 46px);
  line-height: 1.05;
}

.transfer-copy {
  max-width: 640px;
  margin: 0;
  color: var(--on-surface-variant);
  font-size: 16px;
  line-height: 26px;
}

.transfer-copy code {
  padding: 2px 6px;
  border-radius: 8px;
  background: rgba(109, 40, 217, 0.08);
  color: var(--primary);
}

.transfer-origin {
  min-width: 220px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid var(--outline-variant);
  background: var(--surface-container-low);
  display: grid;
  gap: 6px;
}

.transfer-origin span {
  color: var(--on-surface-variant);
  font-size: 12px;
  line-height: 18px;
  font-weight: 700;
}

.transfer-origin strong {
  font-size: 14px;
  line-height: 22px;
  word-break: break-all;
}

.transfer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.transfer-button {
  width: auto;
  min-width: 148px;
}

.transfer-field {
  gap: 10px;
}

.transfer-textarea {
  min-height: 320px;
  width: 100%;
  border: 1px solid var(--outline-variant);
  border-radius: 18px;
  background: #ffffff;
  padding: 16px 18px;
  color: var(--on-surface);
  font-family: "JetBrains Mono", monospace;
  font-size: 13px;
  line-height: 20px;
  resize: vertical;
  outline: none;
}

.transfer-textarea:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px rgba(0, 40, 142, 0.1);
}

.transfer-status {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--outline-variant);
  background: var(--surface-container-low);
  color: var(--on-surface-variant);
  font-size: 14px;
  line-height: 22px;
  font-weight: 600;
}

.transfer-status.success {
  border-color: rgba(22, 163, 74, 0.18);
  background: rgba(220, 252, 231, 0.72);
  color: #166534;
}

.transfer-status.error {
  border-color: rgba(220, 38, 38, 0.18);
  background: rgba(254, 226, 226, 0.78);
  color: #991b1b;
}

.transfer-notes {
  display: grid;
  gap: 8px;
  color: var(--on-surface-variant);
  font-size: 14px;
  line-height: 22px;
}

.transfer-notes p {
  margin: 0;
}

@media (max-width: 860px) {
  .transfer-head {
    flex-direction: column;
  }

  .transfer-origin {
    min-width: 0;
    width: 100%;
  }
}
</style>
