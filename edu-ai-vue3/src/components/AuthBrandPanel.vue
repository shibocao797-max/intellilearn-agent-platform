<template>
  <header class="auth-topbar" aria-label="智学协同学习工作台">
    <RouterLink class="auth-brand" to="/login" aria-label="智学协同学习工作台">
      <span class="auth-brand-mark">
        <img :src="brandLogo" alt="" aria-hidden="true" />
      </span>
      <span class="auth-brand-copy">
        <strong>智学协同</strong>
        <small>学习工作台</small>
      </span>
    </RouterLink>

    <p class="auth-switch">
      <span>{{ isRegister ? '已有账号？' : '第一次来？' }}</span>
      <RouterLink :to="{ name: isRegister ? 'login' : 'register' }">
        {{ isRegister ? '返回登录' : '创建账号' }}
      </RouterLink>
    </p>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import brandLogo from '@/assets/brand/intellilearn-logo.svg'

const props = defineProps({
  mode: {
    type: String,
    default: 'login'
  }
})

const isRegister = computed(() => props.mode === 'register')
</script>
