import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/services/storage'

const routes = [
  { path: '/', redirect: '/chat' },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/register', name: 'register', component: () => import('@/views/RegisterView.vue'), meta: { public: true } },
  { path: '/workspace-transfer', name: 'workspaceTransfer', component: () => import('@/views/WorkspaceTransferView.vue'), meta: { public: true, title: '工作区迁移' } },
  { path: '/chat', name: 'chat', component: () => import('@/views/ChatView.vue'), meta: { title: 'AI 对话' } },
  { path: '/profile', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { title: '学习画像' } },
  { path: '/resources', name: 'resources', component: () => import('@/views/ResourcesView.vue'), meta: { title: '学习资源' } },
  { path: '/quiz', name: 'quiz', component: () => import('@/views/QuizView.vue'), meta: { title: '测验' } },
  { path: '/report', name: 'report', component: () => import('@/views/ReportView.vue'), meta: { title: '学习报告' } },
  { path: '/agent-dispatch', name: 'agentDispatch', component: () => import('@/views/AgentDispatchCenterView.vue'), meta: { title: 'Agent协作中心' } },
  { path: '/users', redirect: '/settings' },
  { path: '/settings', name: 'settings', component: () => import('@/views/SettingsView.vue'), meta: { title: '设置中心' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.public || getToken()) {
    next()
    return
  }

  next({ path: '/login', query: { redirect: to.fullPath } })
})

export default router
