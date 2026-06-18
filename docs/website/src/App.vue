<template>
  <div class="relative min-h-screen">
    <NodeBackground />
    <NavBar />
    <main class="relative z-10">
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>
    </main>
  </div>
</template>

<script setup lang="ts">
import NodeBackground from './components/NodeBackground.vue'
import NavBar from './components/NavBar.vue'
</script>
