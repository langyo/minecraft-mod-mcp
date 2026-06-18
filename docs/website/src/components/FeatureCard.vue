<template>
  <div
    class="glass-card-static group cursor-default flex flex-col h-full relative overflow-hidden"
    :style="{ '--card-color': capability.color }"
  >
    <!-- tinted corner glow -->
    <div
      class="absolute -top-10 -right-10 w-32 h-32 rounded-full blur-2xl opacity-30 group-hover:opacity-50 transition-opacity duration-500"
      :style="{ background: capability.color }"
    />

    <div class="relative flex items-center gap-4 mb-4">
      <div
        class="w-14 h-14 rounded-xl flex items-center justify-center flex-shrink-0 transition-transform duration-300 group-hover:scale-110"
        :style="{ background: `${capability.color}1a`, border: `1px solid ${capability.color}33` }"
      >
        <div :class="`${capability.icon} w-6 h-6`" :style="{ color: capability.color }" />
      </div>
      <div class="min-w-0">
        <p class="text-xs font-mono uppercase tracking-widest mb-0.5" :style="{ color: capability.color }">
          {{ t(capability.verbKey) }}
        </p>
        <h3 class="text-lg font-semibold text-primary leading-tight">
          {{ t(capability.titleKey) }}
        </h3>
      </div>
    </div>

    <p class="relative text-sm leading-relaxed text-secondary mb-3">
      {{ t(capability.descKey) }}
    </p>

    <ul class="relative flex flex-col gap-1.5 mt-auto pt-3 border-t border-subtle">
      <li
        v-for="(pt, i) in points"
        :key="i"
        class="flex items-start gap-2 text-xs text-tertiary"
      >
        <span
          class="mt-0.5 w-1 h-1 rounded-full flex-shrink-0"
          :style="{ background: capability.color, transform: 'translateY(2px)' }"
        />
        <span>{{ pt }}</span>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Capability } from '@/types/feature'

const props = defineProps<{
  capability: Capability
}>()

const { t, tm, rt } = useI18n()

const points = computed<string[]>(() => {
  const raw = tm(props.capability.pointsKey)
  if (!Array.isArray(raw)) return []
  return raw.map((entry: unknown) => {
    // tm() returns objects for nested messages; rt() unwraps the resolved string.
    return typeof entry === 'string' ? entry : rt(entry as never)
  })
})
</script>
