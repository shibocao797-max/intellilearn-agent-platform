<template>
  <section class="auth-visual-stage" :class="`mode-${mode}`" aria-hidden="true">
    <div class="auth-stage-trace trace-a"></div>
    <div class="auth-stage-trace trace-b"></div>
    <div class="auth-stage-trace trace-c"></div>
    <div class="auth-stage-trace trace-d"></div>

    <span
      v-for="node in nodes"
      :key="node.id"
      class="auth-stage-node"
      :class="node.tone"
      :style="{ left: `${node.x}%`, top: `${node.y}%`, '--node-delay': `${node.delay}ms` }"
    ></span>

    <div class="auth-stage-core">
      <span class="auth-stage-orbit orbit-one"></span>
      <span class="auth-stage-orbit orbit-two"></span>
      <span class="auth-stage-core-mark">
        <img :src="brandLogo" alt="" />
      </span>
    </div>

    <div class="auth-stage-rail rail-a"><span></span><i></i><b></b></div>
    <div class="auth-stage-rail rail-b"><span></span><i></i><b></b></div>
  </section>
</template>

<script setup>
import brandLogo from '@/assets/brand/intellilearn-logo.svg'

defineProps({
  mode: {
    type: String,
    default: 'login'
  }
})

const nodes = [
  { id: 'a', x: 12, y: 22, tone: 'blue', delay: 0 },
  { id: 'b', x: 27, y: 10, tone: 'mint', delay: 420 },
  { id: 'c', x: 75, y: 17, tone: 'warm', delay: 760 },
  { id: 'd', x: 90, y: 40, tone: 'blue', delay: 220 },
  { id: 'e', x: 82, y: 76, tone: 'mint', delay: 620 },
  { id: 'f', x: 31, y: 87, tone: 'warm', delay: 980 },
  { id: 'g', x: 8, y: 68, tone: 'blue', delay: 560 }
]
</script>
