<template>
  <section class="page users-page">
    <div class="admin-breadcrumbs">
      <span>管理</span>
      <span class="material-symbols-outlined">chevron_right</span>
      <span>系统</span>
      <span class="material-symbols-outlined">chevron_right</span>
      <strong>用户管理</strong>
    </div>
    <h2 class="page-heading">用户列表</h2>

    <section class="panel filter-panel">
      <div class="filter-grid">
        <label class="field">
          <span>用户名 / 账号</span>
          <input v-model.trim="query.userName" class="control" placeholder="输入用户名" />
        </label>
        <label class="field">
          <span>手机号码</span>
          <input v-model.trim="query.phonenumber" class="control" placeholder="输入手机号" />
        </label>
        <label class="field">
          <span>状态</span>
          <select v-model="query.status" class="control">
            <option value="">全部状态</option>
            <option value="0">正常</option>
            <option value="1">停用</option>
          </select>
        </label>
        <div class="filter-actions">
          <button class="primary-button query-button" type="button" @click="search">
            <span class="material-symbols-outlined">search</span>
            查询
          </button>
          <button class="ghost-button" type="button" @click="reset">重置</button>
        </div>
      </div>
    </section>

    <div class="toolbar">
      <div>
        <button class="primary-button toolbar-button" type="button" @click="newUser">
          <span class="material-symbols-outlined">add</span>
          新增用户
        </button>
        <button class="danger-button" type="button" :disabled="!selectedRows.length" @click="deleteSelected">
          <span class="material-symbols-outlined">delete</span>
          批量删除
        </button>
      </div>
      <div>
        <button class="ghost-button" type="button"><span class="material-symbols-outlined">upload</span>导入</button>
        <button class="ghost-button" type="button"><span class="material-symbols-outlined">download</span>导出</button>
      </div>
    </div>

    <div class="admin-layout">
      <section class="table-panel">
        <div v-if="loading" class="table-loading">
          <SkeletonBlock height="460px" />
        </div>
        <table v-else>
          <thead>
            <tr>
              <th><input type="checkbox" :checked="allChecked" @change="toggleAll" /></th>
              <th>用户 ID</th>
              <th>账号</th>
              <th>昵称</th>
              <th>部门</th>
              <th>状态</th>
              <th>创建时间</th>
              <th class="right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.userId" :class="{ active: selected?.userId === user.userId }" @click="openUser(user)">
              <td><input type="checkbox" :checked="selectedRows.includes(user.userId)" @click.stop @change="toggleRow(user.userId)" /></td>
              <td><code>用户-{{ user.userId }}</code></td>
              <td><strong class="account">{{ user.userName }}</strong></td>
              <td>
                <div class="user-cell">
                  <span class="avatar">{{ initials(user.nickName || user.userName) }}</span>
                  {{ user.nickName || user.userName }}
                </div>
              </td>
              <td>{{ user.dept?.deptName || user.deptName || '未分配' }}</td>
              <td>
                <button class="toggle" :class="{ on: user.status === '0' }" type="button" @click.stop="changeStatus(user)">
                  <span></span>
                </button>
              </td>
              <td>{{ formatDate(user.createTime) }}</td>
              <td class="right">
                <button class="row-action" type="button" title="编辑" @click.stop="openUser(user)"><span class="material-symbols-outlined">edit</span></button>
                <button class="row-action danger" type="button" title="删除" @click.stop="deleteUser(user)"><span class="material-symbols-outlined">delete</span></button>
              </td>
            </tr>
            <tr v-if="!users.length">
              <td colspan="8">
                <UiState compact icon="group_off" title="暂无用户数据" description="当前筛选条件下没有用户记录。" />
              </td>
            </tr>
          </tbody>
        </table>
        <footer class="table-footer">
          <span>显示第 {{ users.length ? 1 : 0 }} 到 {{ users.length }} 条，共 {{ total }} 条</span>
          <div class="pager-mini">
            <button type="button" :disabled="query.pageNum <= 1" @click="turnPage(-1)"><span class="material-symbols-outlined">chevron_left</span></button>
            <strong>{{ query.pageNum }}</strong>
            <button type="button" :disabled="query.pageNum >= pageCount" @click="turnPage(1)"><span class="material-symbols-outlined">chevron_right</span></button>
          </div>
        </footer>
      </section>

      <aside class="user-drawer" :class="{ empty: !selected }">
        <template v-if="selected">
          <header>
            <h3>用户详情</h3>
            <button class="icon-button" type="button" @click="selected = null"><span class="material-symbols-outlined">close</span></button>
          </header>
          <div class="drawer-body">
            <div class="drawer-profile">
              <span class="drawer-avatar">{{ initials(editForm.nickName || editForm.userName) }}</span>
              <h4>{{ editForm.nickName || editForm.userName }}</h4>
              <p>@{{ editForm.userName }} | 用户-{{ editForm.userId || '新建' }}</p>
            </div>

            <div class="drawer-section">
              <h5>基本信息</h5>
              <label class="field">
                <span>账号</span>
                <input v-model.trim="editForm.userName" class="control" />
              </label>
              <label class="field">
                <span>昵称</span>
                <input v-model.trim="editForm.nickName" class="control" />
              </label>
              <label class="field">
                <span>邮箱</span>
                <input v-model.trim="editForm.email" class="control" />
              </label>
              <label class="field">
                <span>手机</span>
                <input v-model.trim="editForm.phonenumber" class="control" />
              </label>
            </div>

            <div class="drawer-section">
              <h5>分配角色</h5>
              <div class="role-pills">
                <label v-for="role in roles" :key="role.roleId" :class="{ checked: editForm.roleIds?.includes(role.roleId) }">
                  <input v-model="editForm.roleIds" :value="role.roleId" type="checkbox" />
                  <span class="material-symbols-outlined">check</span>
                  {{ role.roleName }}
                </label>
              </div>
              <p>角色定义了对系统管理和 AI 功能的访问权限。</p>
            </div>
          </div>
          <footer>
            <button class="ghost-button" type="button" @click="selected = null">取消</button>
            <button class="primary-button save-button" type="button" :disabled="saving" @click="saveUser">保存更改</button>
          </footer>
        </template>
        <UiState v-else compact icon="manage_accounts" title="选择用户" description="点击左侧用户行查看详情和角色配置。" />
      </aside>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { systemApi } from '@/services/api'
import { formatDate, rowsOf } from '@/utils/format'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import UiState from '@/components/UiState.vue'

const loading = ref(false)
const saving = ref(false)
const users = ref([])
const roles = ref([])
const total = ref(0)
const selected = ref(null)
const selectedRows = ref([])
const query = reactive({
  userName: '',
  phonenumber: '',
  status: '',
  pageNum: 1,
  pageSize: 10
})
const editForm = reactive({
  userId: '',
  userName: '',
  nickName: '',
  email: '',
  phonenumber: '',
  status: '0',
  roleIds: []
})

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))
const allChecked = computed(() => users.value.length > 0 && users.value.every(user => selectedRows.value.includes(user.userId)))

function initials(name) {
  return String(name || '?').trim().slice(0, 2).toUpperCase()
}

function fillForm(data) {
  Object.assign(editForm, {
    userId: data.userId || '',
    userName: data.userName || '',
    nickName: data.nickName || '',
    email: data.email || '',
    phonenumber: data.phonenumber || '',
    status: data.status ?? '0',
    roleIds: data.roleIds || data.roles?.map(role => role.roleId) || []
  })
}

async function loadUsers() {
  loading.value = true
  try {
    const res = await systemApi.users(query)
    users.value = rowsOf(res)
    total.value = Number(res.total || users.value.length)
    if (!selected.value && users.value.length) openUser(users.value[0])
  } catch (e) {
    console.warn('用户列表加载失败', e)
    users.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  const res = await systemApi.roles({ pageNum: 1, pageSize: 100 }).catch(e => {
    console.warn('角色列表加载失败', e)
    return null
  })
  roles.value = rowsOf(res)
}

async function openUser(user) {
  selected.value = user
  fillForm(user)
  const res = await systemApi.user(user.userId).catch(() => null)
  if (res) {
    const detail = res.data || user
    selected.value = detail
    fillForm({ ...detail, roleIds: res.roleIds, roles: res.roles })
    if (res.roles?.length) roles.value = res.roles
  }
}

function newUser() {
  selected.value = { userId: '', status: '0' }
  fillForm({ userId: '', status: '0', roleIds: [] })
}

async function saveUser() {
  saving.value = true
  try {
    await systemApi.updateUser({ ...editForm })
    await loadUsers()
  } catch (e) {
    window.alert(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function changeStatus(user) {
  const next = user.status === '0' ? '1' : '0'
  try {
    await systemApi.changeUserStatus({ userId: user.userId, status: next })
    user.status = next
  } catch (e) {
    window.alert(e.message || '状态修改失败')
  }
}

async function deleteUser(user) {
  if (!window.confirm(`确认删除用户 ${user.userName}？`)) return
  try {
    await systemApi.deleteUser(user.userId)
    if (selected.value?.userId === user.userId) selected.value = null
    await loadUsers()
  } catch (e) {
    window.alert(e.message || '删除失败')
  }
}

async function deleteSelected() {
  if (!selectedRows.value.length || !window.confirm(`确认删除选中的 ${selectedRows.value.length} 个用户？`)) return
  try {
    await systemApi.deleteUser(selectedRows.value.join(','))
    selectedRows.value = []
    await loadUsers()
  } catch (e) {
    window.alert(e.message || '批量删除失败')
  }
}

function toggleRow(userId) {
  selectedRows.value = selectedRows.value.includes(userId)
    ? selectedRows.value.filter(id => id !== userId)
    : [...selectedRows.value, userId]
}

function toggleAll() {
  selectedRows.value = allChecked.value ? [] : users.value.map(user => user.userId)
}

function search() {
  query.pageNum = 1
  loadUsers()
}

function reset() {
  query.userName = ''
  query.phonenumber = ''
  query.status = ''
  search()
}

function turnPage(step) {
  query.pageNum += step
  loadUsers()
}

onMounted(async () => {
  await Promise.all([loadUsers(), loadRoles()])
})
</script>

<style scoped>
.admin-breadcrumbs {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--on-surface-variant);
  font-size: 14px;
  margin-bottom: var(--space-xs);
}

.admin-breadcrumbs .material-symbols-outlined {
  font-size: 16px;
}

.filter-panel {
  margin: var(--space-lg) 0;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-md);
  align-items: end;
}

.filter-actions,
.toolbar,
.toolbar > div {
  display: flex;
  gap: var(--space-sm);
}

.query-button,
.toolbar-button,
.save-button {
  width: auto;
}

.toolbar {
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-lg);
}

.admin-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: var(--space-lg);
}

.table-panel {
  overflow: hidden;
  border-radius: var(--radius-lg);
  border: 1px solid var(--outline-variant);
  background: var(--surface-container-lowest);
  box-shadow: var(--shadow-soft);
}

.table-loading {
  padding: var(--space-lg);
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: var(--space-md);
  border-bottom: 1px solid rgba(196, 197, 213, 0.5);
  text-align: left;
  white-space: nowrap;
}

th {
  background: var(--surface);
  color: var(--on-surface-variant);
  font-size: 14px;
  font-weight: 700;
}

tbody tr {
  cursor: pointer;
  transition: background 160ms ease;
}

tbody tr:hover,
tbody tr.active {
  background: var(--surface-container-low);
}

code {
  font-family: "JetBrains Mono", monospace;
  font-size: 13px;
}

.right {
  text-align: right;
}

.account {
  color: var(--primary);
}

.user-cell {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.avatar,
.drawer-avatar {
  display: grid;
  place-items: center;
  border-radius: 9999px;
  background: var(--primary-fixed);
  color: var(--primary);
  font-weight: 800;
}

.avatar {
  width: 32px;
  height: 32px;
  font-size: 12px;
}

.toggle {
  position: relative;
  width: 42px;
  height: 22px;
  border: 0;
  border-radius: 9999px;
  background: var(--outline-variant);
  padding: 2px;
}

.toggle span {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  border-radius: 9999px;
  background: white;
  transition: left 160ms ease;
}

.toggle.on {
  background: var(--primary);
}

.toggle.on span {
  left: 22px;
}

.row-action {
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--primary);
  padding: 6px;
}

.row-action:hover {
  background: var(--surface-container);
}

.row-action.danger {
  color: var(--error);
}

.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md);
  background: var(--surface);
  color: var(--on-surface-variant);
  font-size: 14px;
}

.pager-mini {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.pager-mini button,
.pager-mini strong {
  min-width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--outline-variant);
  background: var(--surface-container-lowest);
  display: grid;
  place-items: center;
}

.pager-mini strong {
  border-color: var(--primary);
  background: var(--primary);
  color: var(--on-primary);
}

.user-drawer {
  min-height: 560px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border-radius: var(--radius-lg);
  border: 1px solid var(--outline-variant);
  background: var(--surface-container-lowest);
  box-shadow: var(--shadow-soft);
}

.user-drawer.empty {
  justify-content: center;
}

.user-drawer header,
.user-drawer footer {
  padding: var(--space-lg);
  display: flex;
  justify-content: space-between;
  gap: var(--space-sm);
  align-items: center;
  background: var(--surface);
}

.user-drawer header {
  border-bottom: 1px solid var(--outline-variant);
}

.user-drawer footer {
  border-top: 1px solid var(--outline-variant);
}

.user-drawer h3,
.drawer-profile h4 {
  margin: 0;
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-lg);
  display: grid;
  gap: var(--space-xl);
}

.drawer-profile {
  display: grid;
  justify-items: center;
  text-align: center;
  gap: var(--space-sm);
}

.drawer-avatar {
  width: 80px;
  height: 80px;
  font-size: 28px;
}

.drawer-profile p {
  margin: 0;
  color: var(--on-surface-variant);
  font-family: "JetBrains Mono", monospace;
  font-size: 13px;
}

.drawer-section {
  display: grid;
  gap: var(--space-md);
}

.drawer-section h5 {
  margin: 0;
  padding-bottom: var(--space-xs);
  border-bottom: 1px solid rgba(196, 197, 213, 0.45);
  color: var(--primary);
  font-size: 12px;
  letter-spacing: 0.08em;
}

.role-pills {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.role-pills label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  padding: 7px 12px;
  border-radius: 9999px;
  border: 1px solid var(--outline-variant);
  color: var(--on-surface-variant);
  cursor: pointer;
}

.role-pills input {
  display: none;
}

.role-pills .material-symbols-outlined {
  width: 0;
  opacity: 0;
  font-size: 16px;
}

.role-pills label.checked {
  border-color: var(--primary);
  background: var(--primary-fixed);
  color: var(--primary);
}

.role-pills label.checked .material-symbols-outlined {
  width: auto;
  opacity: 1;
}

.drawer-section p {
  margin: 0;
  color: var(--outline);
  font-size: 12px;
}

@media (max-width: 1280px) {
  .admin-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .filter-grid {
    grid-template-columns: 1fr;
  }

  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .table-panel {
    overflow-x: auto;
  }
}
</style>
