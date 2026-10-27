<script setup lang="ts">
import { RouterView, useRoute } from 'vue-router'
import AppHeader from '@/shared/ui/AppHeader.vue'

const route = useRoute()
</script>

<template>
  <AppHeader v-if="route.meta.chrome !== false" />
  <RouterView />
</template>
