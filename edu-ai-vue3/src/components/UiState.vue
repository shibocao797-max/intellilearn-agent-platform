<template>
  <div class="ui-state" :class="compact ? 'compact' : ''">
    <div class="ui-state-visual">
      <span class="material-symbols-outlined">{{ icon }}</span>
    </div>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <button v-if="actionLabel" class="primary-button" type="button" @click="$emit('action')">
      <span v-if="actionIcon" class="material-symbols-outlined">{{ actionIcon }}</span>
      {{ actionLabel }}
    </button>
  </div>
</template>

<script setup>
defineEmits(['action'])
defineProps({
  icon: { type: String, default: 'inventory_2' },
  title: { type: String, required: true },
  description: { type: String, required: true },
  actionLabel: { type: String, default: '' },
  actionIcon: { type: String, default: '' },
  compact: { type: Boolean, default: false }
})
</script>
