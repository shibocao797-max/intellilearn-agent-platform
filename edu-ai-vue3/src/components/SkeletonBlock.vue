<template>
  <div class="skeleton-block" :style="{ height }"></div>
</template>

<script setup>
defineProps({
  height: { type: String, default: '120px' }
})
</script>
