<template>
  <main class="auth-page login-page" :class="{ 'is-submitting': loading, 'has-error': Boolean(error) }">
    <LoginKnowledgeScene mode="login" />

    <div class="auth-shell">
      <AuthBrandPanel mode="login" />

      <div class="auth-main-grid">
        <AuthVisualStage mode="login" />

        <section class="auth-form-panel" aria-labelledby="login-title">
        <div class="auth-form-content">
          <header class="auth-form-head">
            <span class="auth-kicker">欢迎回来</span>
            <h1 id="login-title">继续学习</h1>
            <p>登录后，接着上次的内容继续。</p>
          </header>

          <div v-if="error" id="login-error" class="login-alert" role="alert" aria-live="polite">
            <span class="material-symbols-outlined" aria-hidden="true">error</span>
            <span>{{ error }}</span>
          </div>

          <div v-if="successMessage" class="login-success" role="status" aria-live="polite">
            <span class="material-symbols-outlined" aria-hidden="true">check_circle</span>
            <span>{{ successMessage }}</span>
          </div>

          <form class="login-form" @submit.prevent="submit">
            <label class="field">
              <span>账号</span>
              <input
                v-model.trim="form.username"
                class="control standalone-control"
                required
                autocomplete="username"
                placeholder="输入账号"
                :aria-describedby="error ? 'login-error' : undefined"
                :aria-invalid="Boolean(error)"
              />
            </label>

            <label class="field">
              <span>密码</span>
              <div class="input-with-icon password-control">
                <input
                  v-model="form.password"
                  class="control"
                  required
                  autocomplete="current-password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="输入密码"
                  :aria-describedby="error ? 'login-error' : undefined"
                  :aria-invalid="Boolean(error)"
                />
                <button class="password-toggle" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
                  <span class="material-symbols-outlined" aria-hidden="true">{{ showPassword ? 'visibility_off' : 'visibility' }}</span>
                </button>
              </div>
            </label>

            <label v-if="captchaEnabled" class="field">
              <span>验证码</span>
              <div class="captcha-row">
                <button class="captcha-box" type="button" title="刷新验证码" aria-label="刷新验证码" @click="loadCaptcha">
                  <img v-if="captchaImg" :src="captchaImg" alt="验证码" />
                  <span v-else class="material-symbols-outlined" aria-hidden="true">refresh</span>
                </button>
                <input v-model.trim="form.code" class="control captcha-input" required autocomplete="one-time-code" inputmode="numeric" placeholder="输入验证码" :aria-describedby="error ? 'login-error' : undefined" :aria-invalid="Boolean(error)" />
              </div>
            </label>

            <button class="primary-button auth-submit login-submit" type="submit" :disabled="loading">
              <span v-if="loading" class="material-symbols-outlined spin" aria-hidden="true">progress_activity</span>
              <span v-else class="material-symbols-outlined" aria-hidden="true">arrow_forward</span>
              {{ loading ? '验证中' : '登录' }}
            </button>
          </form>
        </div>
        </section>
      </div>
    </div>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '@/services/api'
import { removeToken, setAuthProfile, setToken } from '@/services/storage'
import AuthBrandPanel from '@/components/AuthBrandPanel.vue'
import AuthVisualStage from '@/components/AuthVisualStage.vue'
import LoginKnowledgeScene from '@/components/LoginKnowledgeScene.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref('')
const successMessage = ref('')
const showPassword = ref(false)
const captchaImg = ref('')
const captchaEnabled = ref(true)
const form = reactive({
  username: '',
  password: '',
  code: '',
  uuid: ''
})

async function loadCaptcha() {
  try {
    const res = await authApi.captcha()
    captchaEnabled.value = res.captchaEnabled !== false
    form.uuid = res.uuid || ''
    captchaImg.value = res.img ? `data:image/gif;base64,${res.img}` : ''
  } catch {
    captchaEnabled.value = false
  }
}

async function submit() {
  loading.value = true
  error.value = ''

  try {
    const res = await authApi.login({ ...form })
    setToken(res.token)

    const info = await authApi.getInfo().catch(error => {
      removeToken()
      throw error
    })
    if (!info?.user?.userId) {
      removeToken()
      throw new Error('登录身份校验失败，请重试。')
    }
    setAuthProfile({
      userId: info.user.userId,
      userName: info.user.userName || '',
      nickName: info.user.nickName || '',
      avatar: info.user.avatar || '',
      roles: Array.isArray(info.roles) ? info.roles : [],
      userType: info.userType || ''
    })

    router.replace(route.query.redirect || '/chat')
  } catch (e) {
    error.value = e.message || '请检查账号、密码和验证码'
    if (captchaEnabled.value) await loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  form.username = String(route.query.username || '').trim()
  successMessage.value = route.query.registered === '1' ? '注册完成，现在可以登录。' : ''
  loadCaptcha()
})
</script>
