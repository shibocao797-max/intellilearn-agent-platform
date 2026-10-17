<template>
  <RouterView v-if="isPublicPage" />
  <AppShell v-else>
    <RouterView v-slot="{ Component, route: viewRoute }">
      <KeepAlive>
        <component :is="Component" v-if="viewRoute.name === 'chat'" />
      </KeepAlive>
      <component :is="Component" v-if="viewRoute.name !== 'chat'" />
    </RouterView>
  </AppShell>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '@/components/AppShell.vue'

const route = useRoute()
const isPublicPage = computed(() => route.meta.public)
</script>
