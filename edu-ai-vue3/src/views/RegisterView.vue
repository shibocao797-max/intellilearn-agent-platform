<template>
  <main class="auth-page register-page" :class="{ 'is-submitting': loading, 'has-error': Boolean(error) }">
    <LoginKnowledgeScene mode="register" />

    <div class="auth-shell">
      <AuthBrandPanel mode="register" />

      <div class="auth-main-grid">
        <AuthVisualStage mode="register" />

        <section class="auth-form-panel" aria-labelledby="register-title">
        <div class="auth-form-content">
          <header class="auth-form-head">
            <span class="auth-kicker">开始你的学习空间</span>
            <h1 id="register-title">创建账号</h1>
            <p>保存材料、任务和复习记录。</p>
          </header>

          <div v-if="error" id="register-error" class="register-alert" role="alert" aria-live="polite">
            <span class="material-symbols-outlined" aria-hidden="true">error</span>
            <span>{{ error }}</span>
          </div>

          <form class="register-form" @submit.prevent="submit">
            <label class="field">
              <span>用户名</span>
              <input
                v-model.trim="form.username"
                class="control standalone-control"
                required
                autocomplete="username"
                placeholder="输入登录账号"
                :aria-describedby="error ? 'register-error' : undefined"
                :aria-invalid="Boolean(error)"
              />
            </label>

            <label class="field">
              <span>密码</span>
              <div class="input-with-icon password-control">
                <input v-model="form.password" class="control" required :type="showPassword ? 'text' : 'password'" autocomplete="new-password" placeholder="设置登录密码" :aria-describedby="error ? 'register-error' : undefined" :aria-invalid="Boolean(error)" />
                <button class="password-toggle" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
                  <span class="material-symbols-outlined" aria-hidden="true">{{ showPassword ? 'visibility_off' : 'visibility' }}</span>
                </button>
              </div>
            </label>

            <label class="field">
              <span>确认密码</span>
              <div class="input-with-icon password-control">
                <input v-model="form.confirmPassword" class="control" required :type="showConfirmPassword ? 'text' : 'password'" autocomplete="new-password" placeholder="再次输入密码" :aria-describedby="error ? 'register-error' : undefined" :aria-invalid="Boolean(error)" />
                <button class="password-toggle" type="button" :aria-label="showConfirmPassword ? '隐藏确认密码' : '显示确认密码'" @click="showConfirmPassword = !showConfirmPassword">
                  <span class="material-symbols-outlined" aria-hidden="true">{{ showConfirmPassword ? 'visibility_off' : 'visibility' }}</span>
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
                <input v-model.trim="form.code" class="control captcha-input" required autocomplete="one-time-code" inputmode="numeric" placeholder="输入验证码" :aria-describedby="error ? 'register-error' : undefined" :aria-invalid="Boolean(error)" />
              </div>
            </label>

            <button class="primary-button auth-submit register-submit" type="submit" :disabled="loading">
              <span v-if="loading" class="material-symbols-outlined spin" aria-hidden="true">progress_activity</span>
              <span v-else class="material-symbols-outlined" aria-hidden="true">how_to_reg</span>
              {{ loading ? '注册中' : '创建账号' }}
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
import { useRouter } from 'vue-router'
import { authApi } from '@/services/api'
import AuthBrandPanel from '@/components/AuthBrandPanel.vue'
import AuthVisualStage from '@/components/AuthVisualStage.vue'
import LoginKnowledgeScene from '@/components/LoginKnowledgeScene.vue'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const captchaImg = ref('')
const captchaEnabled = ref(true)
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
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
  error.value = ''

  if (form.password !== form.confirmPassword) {
    error.value = '两次输入的密码不一致'
    return
  }

  loading.value = true
  try {
    await authApi.register({
      username: form.username,
      password: form.password,
      code: form.code,
      uuid: form.uuid
    })

    router.replace({
      name: 'login',
      query: {
        registered: '1',
        username: form.username
      }
    })
  } catch (e) {
    error.value = e.message || '注册失败，请稍后重试'
    if (captchaEnabled.value) await loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(loadCaptcha)
</script>
