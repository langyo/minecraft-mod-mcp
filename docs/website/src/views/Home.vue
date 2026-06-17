<template>
  <div ref="snapContainer" class="snap-container">
    <!-- Page 1: Hero -->
    <section ref="heroSection" class="snap-section">
      <div class="section-inner">
        <div class="text-center max-w-3xl mx-auto">
          <div
            v-for="(item, i) in heroItems"
            :key="i"
            class="reveal"
            :class="{ 'is-visible': heroVisible }"
            :style="{ transitionDelay: `${0.1 + i * 0.12}s` }"
            v-html="item"
          />
        </div>
      </div>
    </section>

    <!-- Page 2: Capabilities -->
    <section id="capabilities" class="snap-section" ref="capabilitiesSection">
      <div class="section-inner">
        <div class="max-w-5xl mx-auto w-full">
          <div class="text-center mb-8 reveal" :class="{ 'is-visible': capabilitiesVisible }">
            <p class="text-xs sm:text-sm font-mono font-medium tracking-widest uppercase text-tertiary mb-2">01 · {{ t('cap.kicker') }}</p>
            <h2 class="text-4xl sm:text-5xl font-bold tracking-tight">
              <span class="bg-gradient-to-r from-emerald-400 to-cyan-400 bg-clip-text text-transparent">
                {{ t('cap.heading') }}
              </span>
            </h2>
            <p class="text-sm mt-3 max-w-xl mx-auto text-secondary">{{ t('cap.intro') }}</p>
          </div>
          <div class="card-grid">
            <FeatureCard v-for="(c, i) in capabilities" :key="c.id" :capability="c"
              class="reveal" :class="{ 'is-visible': capabilitiesVisible }"
              :style="{ transitionDelay: `${0.12 + i * 0.08}s` }"
            />
          </div>
        </div>
      </div>
    </section>

    <!-- Page 3: The loop — Actuate + Detach working together -->
    <section id="loop" class="snap-section" ref="loopSection">
      <div class="section-inner">
        <div class="max-w-5xl mx-auto w-full">
          <div class="text-center mb-8 reveal" :class="{ 'is-visible': loopVisible }">
            <p class="text-xs sm:text-sm font-mono font-medium tracking-widest uppercase text-tertiary mb-2">02 · {{ t('loop.kicker') }}</p>
            <h2 class="text-4xl sm:text-5xl font-bold tracking-tight">
              <span class="bg-gradient-to-r from-violet-400 to-emerald-400 bg-clip-text text-transparent">
                {{ t('loop.heading') }}
              </span>
            </h2>
            <p class="text-sm mt-3 max-w-xl mx-auto text-secondary">{{ t('loop.intro') }}</p>
          </div>

          <div class="loop-diagram reveal" :class="{ 'is-visible': loopVisible }">
            <div class="loop-node loop-node-ai">
              <div class="i-lucide-bot w-6 h-6 mb-1" style="color: var(--mc-violet)" />
              <div class="text-sm font-semibold text-primary">{{ t('loop.ai.title') }}</div>
              <div class="text-xs text-tertiary mt-0.5">{{ t('loop.ai.sub') }}</div>
            </div>

            <div class="loop-conn">
              <div class="loop-conn-line"></div>
              <div class="loop-conn-label">{{ t('loop.mcp') }}</div>
            </div>

            <div class="loop-node loop-node-bridge">
              <div class="i-lucide-cable w-6 h-6 mb-1" style="color: var(--mc-cyan)" />
              <div class="text-sm font-semibold text-primary">{{ t('loop.bridge.title') }}</div>
              <div class="text-xs text-tertiary mt-0.5">{{ t('loop.bridge.sub') }}</div>
            </div>

            <div class="loop-conn">
              <div class="loop-conn-line"></div>
              <div class="loop-conn-label">{{ t('loop.reflect') }}</div>
            </div>

            <div class="loop-node loop-node-game">
              <div class="i-lucide-gamepad-2 w-6 h-6 mb-1" style="color: var(--mc-grass)" />
              <div class="text-sm font-semibold text-primary">{{ t('loop.game.title') }}</div>
              <div class="text-xs text-tertiary mt-0.5">{{ t('loop.game.sub') }}</div>
            </div>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 mt-6">
            <div
              v-for="(tip, i) in loopTips"
              :key="i"
              class="glass-card-static reveal flex items-start gap-3 p-4"
              :class="{ 'is-visible': loopVisible }"
              :style="{ transitionDelay: `${0.12 + i * 0.08}s` }"
            >
              <div :class="`${tip.icon} w-5 h-5 mt-0.5 flex-shrink-0`" :style="{ color: tip.color }" />
              <p class="text-xs leading-relaxed text-secondary">{{ t(tip.key) }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Page 4: Vision-model pairing (the "See" capability amplified) -->
    <section id="vision" class="snap-section" ref="visionSection">
      <div class="section-inner">
        <div class="max-w-3xl mx-auto w-full">
          <div class="text-center mb-8 reveal" :class="{ 'is-visible': visionVisible }">
            <p class="text-xs sm:text-sm font-mono font-medium tracking-widest uppercase text-tertiary mb-2">03 · {{ t('vision.kicker') }}</p>
            <h2 class="text-4xl sm:text-5xl font-bold tracking-tight">
              <span class="bg-gradient-to-r from-amber-400 to-cyan-400 bg-clip-text text-transparent">
                {{ t('vision.heading') }}
              </span>
            </h2>
          </div>

          <div
            class="glass-card-static reveal text-left"
            :class="{ 'is-visible': visionVisible }"
          >
            <div class="flex items-start gap-3 mb-4">
              <div class="i-lucide-scan-eye w-7 h-7 flex-shrink-0" style="color: var(--mc-amber)" />
              <div>
                <h3 class="text-lg font-semibold text-primary">{{ t('vision.title') }}</h3>
                <p class="text-sm text-secondary mt-1">{{ t('vision.desc') }}</p>
              </div>
            </div>

            <div class="rounded-xl p-4 my-4" style="background: var(--bg-secondary); border: 1px dashed var(--border-subtle);">
              <div class="flex items-center gap-2 mb-3">
                <span class="w-2.5 h-2.5 rounded-sm" style="background: var(--mc-amber)"></span>
                <span class="text-xs font-mono text-tertiary">viewport + coordinate grid</span>
              </div>
              <!-- faux screenshot with grid + click reticle -->
              <div class="vision-mock">
                <div class="vision-grid"></div>
                <div class="vision-reticle">
                  <div class="vision-reticle-ring"></div>
                  <div class="vision-reticle-dot"></div>
                </div>
                <div class="vision-pixel vision-pixel-a"></div>
                <div class="vision-pixel vision-pixel-b"></div>
              </div>
              <p class="text-xs text-tertiary mt-3 font-mono">{{ t('vision.caption') }}</p>
            </div>

            <ul class="flex flex-col gap-2">
              <li v-for="(pt, i) in visionPoints" :key="i" class="flex items-start gap-2 text-sm text-secondary">
                <div class="i-lucide-check w-4 h-4 mt-0.5 flex-shrink-0" style="color: var(--mc-cyan)" />
                <span>{{ t(pt) }}</span>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- Page 5: Quick Start -->
    <section id="quickstart" class="snap-section" ref="quickstartSection">
      <div class="section-inner">
        <div class="max-w-3xl mx-auto w-full">
          <div class="text-center mb-8 reveal" :class="{ 'is-visible': quickstartVisible }">
            <p class="text-xs sm:text-sm font-mono font-medium tracking-widest uppercase text-tertiary mb-2">04 · {{ t('quickstart.kicker') }}</p>
            <h2 class="text-4xl sm:text-5xl font-bold tracking-tight">
              <span class="bg-gradient-to-r from-emerald-400 to-teal-400 bg-clip-text text-transparent">
                {{ t('quickstart.heading') }}
              </span>
            </h2>
          </div>

          <div class="flex flex-col gap-3">
            <div
              v-for="(step, i) in steps"
              :key="i"
              class="glass-card-static reveal flex items-start gap-4"
              :class="{ 'is-visible': quickstartVisible }"
              :style="{ transitionDelay: `${0.12 + i * 0.08}s` }"
            >
              <div class="flex-shrink-0 w-9 h-9 rounded-lg flex items-center justify-center font-bold text-slate-900 font-mono"
                style="background: linear-gradient(135deg, #5fd35f, #2ee6c8);">
                {{ i + 1 }}
              </div>
              <div class="flex-1 min-w-0">
                <h3 class="text-base font-semibold mb-1 text-primary">{{ t(step.titleKey) }}</h3>
                <p class="text-sm text-secondary mb-2">{{ t(step.descKey) }}</p>
                <pre v-if="step.code" class="text-xs font-mono px-3 py-2 rounded-lg overflow-x-auto"
                  style="background: var(--bg-secondary); color: var(--text-primary);"><code>{{ step.code }}</code></pre>
                <a v-if="step.link" :href="step.link" target="_blank" rel="noopener"
                  class="text-sm inline-flex items-center gap-1 no-underline text-secondary hover:text-[var(--text-primary)] transition-colors mt-1">
                  <div class="i-lucide-external-link w-3.5 h-3.5" />
                  {{ t('quickstart.guide') }}
                </a>
              </div>
            </div>
          </div>

          <div class="mt-5 text-center reveal" :class="{ 'is-visible': quickstartVisible }">
            <div class="inline-flex items-center gap-3 px-4 py-2 rounded-xl glass">
              <div class="i-lucide-link w-4 h-4" style="color: var(--mc-cyan)" />
              <span class="text-xs text-secondary">{{ t('quickstart.oneLink') }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Page 6: Loaders + Compatibility -->
    <section id="loaders" class="snap-section" ref="loadersSection">
      <div class="section-inner">
        <div class="max-w-5xl mx-auto w-full">
          <div class="text-center mb-8 reveal" :class="{ 'is-visible': loadersVisible }">
            <p class="text-xs sm:text-sm font-mono font-medium tracking-widest uppercase text-tertiary mb-2">05 · {{ t('loader.kicker') }}</p>
            <h2 class="text-4xl sm:text-5xl font-bold tracking-tight">
              <span class="bg-gradient-to-r from-cyan-400 to-emerald-400 bg-clip-text text-transparent">
                {{ t('loader.heading') }}
              </span>
            </h2>
            <p class="text-sm mt-3 max-w-lg mx-auto text-secondary">{{ t('loader.intro') }}</p>
          </div>

          <div class="card-grid grid-cols-3 mb-8">
            <div
              v-for="(l, i) in loaders"
              :key="l.id"
              class="glass-card-static flex flex-col items-center justify-center gap-3 text-center reveal py-8"
              :class="{ 'is-visible': loadersVisible }"
              :style="{ transitionDelay: `${0.12 + i * 0.08}s` }"
            >
              <div class="w-12 h-12 rounded-xl flex items-center justify-center" :style="{ background: `${l.color}1a`, border: `1px solid ${l.color}33` }">
                <div :class="`${l.icon} w-6 h-6`" :style="{ color: l.color }" />
              </div>
              <span class="text-base font-semibold text-primary">{{ t(l.nameKey) }}</span>
            </div>
          </div>

          <div class="tools-scroll-outer reveal" :class="{ 'is-visible': loadersVisible }">
            <button
              class="tools-scroll-arrow tools-scroll-arrow-left"
              :class="{ 'is-hidden': !versionsCanScrollLeft }"
              @click="scrollVersionsBy(-1)"
              aria-label="Scroll left"
            >
              <div class="i-lucide-chevron-left w-5 h-5" />
            </button>
            <div class="tools-scroll-container" ref="versionsScrollRef" @wheel="onVersionsWheel">
              <div class="tools-scroll-track">
                <div
                  v-for="(v, i) in versions"
                  :key="i"
                  class="glass-card-static flex flex-col items-center justify-center gap-1.5 reveal py-4 px-5"
                  :class="{ 'is-visible': loadersVisible }"
                  :style="{ minWidth: '110px', transitionDelay: `${0.08 + i * 0.04}s` }"
                >
                  <span class="text-sm font-mono font-semibold text-primary">{{ v.version }}</span>
                  <div class="flex gap-1.5 mt-1">
                    <span v-for="lo in v.supported" :key="lo"
                      class="w-1.5 h-1.5 rounded-full"
                      :style="{ background: loaderColor(lo) }"
                      :title="lo" />
                  </div>
                </div>
              </div>
            </div>
            <button
              class="tools-scroll-arrow tools-scroll-arrow-right"
              :class="{ 'is-hidden': !versionsCanScrollRight }"
              @click="scrollVersionsBy(1)"
              aria-label="Scroll right"
            >
              <div class="i-lucide-chevron-right w-5 h-5" />
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Page 7: About + Footer -->
    <section id="about" class="snap-start flex flex-col about-section" ref="aboutSection">
      <div class="flex-1 flex items-center justify-center px-4 py-6 sm:py-8">
        <div
          class="glass-card-static text-center p-5 sm:p-6 max-w-xl mx-auto reveal"
          :class="{ 'is-visible': aboutVisible }"
        >
          <div class="mb-2 flex justify-center">
            <img
              :src="logoSrc"
              alt="Minecraft Mod MCP Logo"
              class="w-10 h-10 sm:w-12 sm:h-12 object-contain rounded-xl animate-glow"
              draggable="false"
            />
          </div>
          <div
            class="about-text leading-normal max-w-lg mx-auto about-content prose prose-sm prose-zinc dark:prose-invert text-secondary"
            v-html="renderedAboutText"
          ></div>
        </div>
      </div>

      <footer
        class="border-t backdrop-blur-md reveal py-6 border-subtle bg-footer delay-300"
        :class="{ 'is-visible': aboutVisible }"
      >
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-full flex flex-col items-center justify-center gap-3 text-sm text-muted">
          <a href="https://github.com/langyo/minecraft-mod-mcp" target="_blank" rel="noopener" class="nav-icon-btn no-underline group" title="GitHub">
            <div class="i-lucide-github w-5 h-5 group-hover:text-[var(--text-primary)] transition-colors" />
          </a>
          <span class="text-tertiary">「{{ t('site.slogan') }}」</span>
          <span>{{ t('site.footer.copyright', { year: new Date().getFullYear() }) }}</span>
        </div>
      </footer>
    </section>
  </div>

  <!-- Scroll Indicator -->
  <nav class="fixed right-4 sm:right-6 top-1/2 -translate-y-1/2 z-40 flex flex-col gap-3">
    <button
      v-for="i in 7"
      :key="i"
      class="w-1 rounded-full transition-all duration-300 cursor-pointer border-none p-0"
      :style="{
        height: currentPage === i - 1 ? '2rem' : '1.5rem',
        background: currentPage === i - 1 ? 'var(--mc-cyan)' : 'var(--text-muted)',
      }"
      :title="pageLabels[i - 1]"
      @click="scrollToPage(i - 1)"
    />
  </nav>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { marked } from 'marked'
import { capabilities, loaders } from '@/types/feature'
import FeatureCard from '@/components/FeatureCard.vue'
import logoSrc from '@res/logos/minecraft-mod-mcp.webp'

const aboutDocs = import.meta.glob('../../docs/**/about.md', { query: '?raw', import: 'default', eager: true })

const { t, locale } = useI18n()

const renderedAboutText = computed(() => {
  const currentLang = locale.value
  const fallbackLang = 'en'
  const docPath = `../../docs/${currentLang}/about.md`
  const fallbackPath = `../../docs/${fallbackLang}/about.md`
  const mdContent = (aboutDocs[docPath] as string) || (aboutDocs[fallbackPath] as string) || ''
  return marked.parse(mdContent)
})

const snapContainer = ref<HTMLDivElement>()
const heroSection = ref<HTMLElement>()
const capabilitiesSection = ref<HTMLElement>()
const loopSection = ref<HTMLElement>()
const visionSection = ref<HTMLElement>()
const quickstartSection = ref<HTMLElement>()
const loadersSection = ref<HTMLElement>()
const aboutSection = ref<HTMLElement>()

const currentPage = ref(0)

const pageLabels = computed(() => [
  t('site.nav.home'),
  t('cap.heading'),
  t('loop.heading'),
  t('vision.heading'),
  t('quickstart.heading'),
  t('loader.heading'),
  t('site.nav.about'),
])

const sections = computed(() => [
  heroSection.value,
  capabilitiesSection.value,
  loopSection.value,
  visionSection.value,
  quickstartSection.value,
  loadersSection.value,
  aboutSection.value,
])

function scrollToPage(index: number) {
  const el = sections.value[index]
  if (el) snapContainer.value?.scrollTo({ top: el.offsetTop, behavior: 'smooth' })
}

const heroVisible = ref(false)
const capabilitiesVisible = ref(false)
const loopVisible = ref(false)
const visionVisible = ref(false)
const quickstartVisible = ref(false)
const loadersVisible = ref(false)
const aboutVisible = ref(false)

interface Step {
  titleKey: string
  descKey: string
  code?: string
  link?: string
}

const steps = computed<Step[]>(() => [
  {
    titleKey: 'quickstart.step1.title',
    descKey: 'quickstart.step1.desc',
    code: 'npm install -g minecraft-mod-mcp',
  },
  {
    titleKey: 'quickstart.step2.title',
    descKey: 'quickstart.step2.desc',
    code: 'minecraft-mod-mcp launch 1.21.11 --loader fabric',
  },
  {
    titleKey: 'quickstart.step3.title',
    descKey: 'quickstart.step3.desc',
  },
  {
    titleKey: 'quickstart.step4.title',
    descKey: 'quickstart.step4.desc',
    link: 'https://github.com/langyo/minecraft-mod-mcp/blob/master/docs/guides/en/AI-TOOLS.md',
  },
])

const loopTips = [
  { icon: 'i-lucide-mouse-off', color: 'var(--mc-violet)', key: 'loop.tip.detach' },
  { icon: 'i-lucide-radio-tower', color: 'var(--mc-cyan)', key: 'loop.tip.alive' },
  { icon: 'i-lucide-keyboard', color: 'var(--mc-grass)', key: 'loop.tip.alttab' },
]

const visionPoints = ['vision.point.grid', 'vision.point.model', 'vision.point.autonomy']

interface VersionRow {
  version: string
  supported: string[]
}

const versions: VersionRow[] = [
  { version: '26.1.2', supported: ['forge', 'neoforge'] },
  { version: '1.21.11', supported: ['forge', 'fabric', 'neoforge'] },
  { version: '1.21.x', supported: ['forge', 'fabric', 'neoforge'] },
  { version: '1.20.6', supported: ['forge', 'fabric', 'neoforge'] },
  { version: '1.20.x', supported: ['forge', 'fabric', 'neoforge'] },
  { version: '1.19.x', supported: ['forge', 'fabric'] },
  { version: '1.18.x', supported: ['forge', 'fabric'] },
  { version: '1.16.x', supported: ['forge', 'fabric'] },
  { version: '1.12.2', supported: ['forge'] },
  { version: '1.8.9', supported: ['forge'] },
  { version: '1.7.x', supported: ['forge'] },
]

function loaderColor(id: string): string {
  return loaders.find(l => l.id === id)?.color ?? '#64748b'
}

const versionsScrollRef = ref<HTMLDivElement>()
const versionsCanScrollLeft = ref(false)
const versionsCanScrollRight = ref(false)

function updateVersionsScrollState() {
  const el = versionsScrollRef.value
  if (!el) return
  versionsCanScrollLeft.value = el.scrollLeft > 2
  versionsCanScrollRight.value = el.scrollLeft + el.clientWidth < el.scrollWidth - 2
}

function scrollVersionsBy(direction: number) {
  const el = versionsScrollRef.value
  if (!el) return
  el.scrollBy({ left: direction * 220, behavior: 'smooth' })
}

let _wheelAccum = 0
let _wheelRaf = 0

function onVersionsWheel(e: WheelEvent) {
  const el = versionsScrollRef.value
  if (!el) return
  const delta = e.deltaY || e.deltaX
  if (delta === 0) return
  const atStart = el.scrollLeft <= 0 && delta < 0
  const atEnd = el.scrollLeft + el.clientWidth >= el.scrollWidth - 1 && delta > 0
  if (atStart || atEnd) {
    _wheelAccum = 0
    return
  }
  e.preventDefault()
  _wheelAccum += delta
  if (!_wheelRaf) {
    _wheelRaf = requestAnimationFrame(function step() {
      if (Math.abs(_wheelAccum) < 0.5) {
        _wheelRaf = 0
        updateVersionsScrollState()
        return
      }
      const consume = _wheelAccum * 0.25
      _wheelAccum -= consume
      el.scrollLeft += consume
      _wheelRaf = requestAnimationFrame(step)
    })
  }
}

const heroItems = computed(() => [
  `<div class="flex justify-center mb-6"><img src="${logoSrc}" alt="Minecraft Mod MCP" class="w-20 h-20 sm:w-24 sm:h-24 object-contain rounded-2xl animate-glow" draggable="false" /></div>`,
  `<div class="text-5xl sm:text-7xl font-bold tracking-tight mb-4"><span class="bg-gradient-to-r from-emerald-400 via-teal-300 to-cyan-400 bg-clip-text text-transparent">${t('site.title')}</span></div>`,
  `<p class="text-lg sm:text-xl font-medium mb-2 text-primary">${t('site.tagline')}</p>`,
  `<p class="text-base max-w-xl mx-auto" style="color: var(--text-tertiary)">${t('site.description')}</p>`,
  `<div class="flex flex-wrap items-center justify-center gap-3 mt-8">
    <a href="https://github.com/langyo/minecraft-mod-mcp/releases/latest" target="_blank" rel="noopener" class="btn-primary no-underline group"><span class="i-lucide-download w-4 h-4 mr-1.5"></span> ${t('hero.download')}</a>
    <a href="https://github.com/langyo/minecraft-mod-mcp" target="_blank" rel="noopener" class="btn-ghost no-underline group"><span class="i-lucide-github w-4 h-4 mr-1.5 opacity-70 group-hover:opacity-100 transition-opacity"></span> GitHub</a>
  </div>`,
  `<div class="mt-10 animate-float opacity-20"><span class="i-lucide-chevrons-down inline-block w-6 h-6" style="color: var(--text-primary)"></span></div>`,
])

let observer: IntersectionObserver

onMounted(() => {
  heroVisible.value = true

  const sectionMap: Record<string, () => void> = {}
  if (capabilitiesSection.value) sectionMap[capabilitiesSection.value.id || 'capabilities'] = () => { capabilitiesVisible.value = true }
  if (loopSection.value) sectionMap[loopSection.value.id || 'loop'] = () => { loopVisible.value = true }
  if (visionSection.value) sectionMap[visionSection.value.id || 'vision'] = () => { visionVisible.value = true }
  if (quickstartSection.value) sectionMap[quickstartSection.value.id || 'quickstart'] = () => { quickstartVisible.value = true }
  if (loadersSection.value) sectionMap[loadersSection.value.id || 'loaders'] = () => { loadersVisible.value = true }
  if (aboutSection.value) sectionMap[aboutSection.value.id || 'about'] = () => { aboutVisible.value = true }

  observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        const fn = sectionMap[entry.target.id]
        if (fn) fn()
        const idx = sections.value.indexOf(entry.target as HTMLElement)
        if (idx !== -1) currentPage.value = idx
      }
    }
  }, { root: snapContainer.value, threshold: 0.25 })

  for (const el of sections.value) {
    if (el) observer.observe(el)
  }

  nextTick(() => {
    updateVersionsScrollState()
    versionsScrollRef.value?.addEventListener('scroll', updateVersionsScrollState, { passive: true })
    window.addEventListener('resize', onVersionsResize)
  })
})

function onVersionsResize() {
  updateVersionsScrollState()
}

onBeforeUnmount(() => {
  observer?.disconnect()
  versionsScrollRef.value?.removeEventListener('scroll', updateVersionsScrollState)
  window.removeEventListener('resize', onVersionsResize)
})
</script>

<style scoped>
.snap-container {
  position: relative;
  height: 100vh;
  height: 100dvh;
  overflow-y: scroll;
  scroll-snap-type: y mandatory;
  scroll-behavior: smooth;
}

.snap-section {
  scroll-snap-align: start;
  height: 100vh;
  height: 100dvh;
  width: 100%;
}

.reveal {
  opacity: 0;
  transform: translateY(28px) scale(0.97);
  transition: opacity 0.3s cubic-bezier(0.16, 1, 0.3, 1),
              transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.reveal.is-visible {
  opacity: 1;
  transform: translateY(0) scale(1);
}

.about-content :deep(p) {
  margin-bottom: 1.2em;
  color: var(--text-secondary);
}
.about-content :deep(p:last-child) {
  margin-bottom: 0;
}
.about-content :deep(a) {
  color: var(--text-primary);
  text-decoration: underline;
  text-decoration-color: var(--border-subtle);
  text-underline-offset: 4px;
  transition: all 0.3s ease;
}
.about-content :deep(a:hover) {
  color: var(--mc-cyan);
  text-decoration-color: var(--mc-cyan);
}
.about-content :deep(strong) {
  color: var(--text-primary);
  font-weight: 600;
}

.about-text {
  font-size: 0.8125rem;
}

.delay-300 {
  transition-delay: 0.3s;
}

/* ---- The AI ⇄ Bridge ⇄ Game loop diagram ---- */
.loop-diagram {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
  flex-wrap: nowrap;
}

.loop-node {
  flex: 1 1 0;
  min-width: 0;
  text-align: center;
  padding: 1rem 0.75rem;
  border-radius: 1rem;
  background: var(--bg-glass);
  border: 1px solid var(--border-subtle);
  backdrop-filter: blur(12px);
  transition: transform 0.3s ease, border-color 0.3s ease;
}
.loop-node:hover {
  transform: translateY(-3px);
  border-color: var(--border-hover);
}

.loop-conn {
  flex: 0 0 auto;
  width: 48px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}
.loop-conn-line {
  width: 100%;
  height: 2px;
  background: linear-gradient(90deg, var(--mc-violet), var(--mc-cyan), var(--mc-grass));
  opacity: 0.5;
  position: relative;
}
.loop-conn-line::before {
  content: '';
  position: absolute;
  inset: 0;
  background: inherit;
  filter: blur(4px);
  opacity: 0.6;
}
.loop-conn-label {
  position: absolute;
  top: -1.25rem;
  left: 50%;
  transform: translateX(-50%);
  font-size: 0.625rem;
  font-family: ui-monospace, monospace;
  color: var(--text-tertiary);
  white-space: nowrap;
  letter-spacing: 0.05em;
}

@media (max-width: 639px) {
  .loop-diagram {
    flex-direction: column;
    gap: 0.5rem;
  }
  .loop-conn {
    width: 2px;
    height: 28px;
  }
  .loop-conn-line {
    width: 2px;
    height: 100%;
    background: linear-gradient(180deg, var(--mc-violet), var(--mc-cyan), var(--mc-grass));
  }
  .loop-conn-label {
    top: 50%;
    left: auto;
    right: 0.5rem;
    transform: translateY(-50%);
  }
}

/* ---- Vision mock screenshot ---- */
.vision-mock {
  position: relative;
  height: 150px;
  border-radius: 0.5rem;
  overflow: hidden;
  background:
    linear-gradient(135deg, rgba(95,211,95,0.10), rgba(46,230,200,0.10)),
    var(--bg-primary);
  border: 1px solid var(--border-subtle);
}
.vision-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(255,255,255,0.07) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(255,255,255,0.07) 1px, transparent 1px);
  background-size: 24px 24px;
}
.vision-reticle {
  position: absolute;
  left: 62%;
  top: 38%;
  width: 0;
  height: 0;
}
.vision-reticle-ring {
  position: absolute;
  width: 26px;
  height: 26px;
  left: -13px;
  top: -13px;
  border: 1.5px solid var(--mc-amber);
  border-radius: 50%;
  animation: pulse-ring 2s ease-out infinite;
}
.vision-reticle-dot {
  position: absolute;
  width: 4px;
  height: 4px;
  left: -2px;
  top: -2px;
  background: var(--mc-amber);
  border-radius: 50%;
}
.vision-pixel {
  position: absolute;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  opacity: 0.6;
}
.vision-pixel-a { left: 28%; top: 60%; background: var(--mc-grass); }
.vision-pixel-b { left: 44%; top: 26%; background: var(--mc-cyan); }

/* ---- Tools horizontal scroller ---- */
.tools-scroll-outer {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
}
.tools-scroll-arrow {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--border-subtle);
  background: var(--bg-glass);
  backdrop-filter: blur(12px);
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
  z-index: 2;
}
.tools-scroll-arrow:hover {
  color: var(--text-primary);
  border-color: var(--text-secondary);
}
.tools-scroll-arrow.is-hidden {
  opacity: 0;
  pointer-events: none;
}
.tools-scroll-container {
  overflow-x: auto;
  overflow-y: hidden;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
  flex: 1;
  min-width: 0;
  overscroll-behavior-x: contain;
}
.tools-scroll-container::-webkit-scrollbar {
  display: none;
}
.tools-scroll-track {
  display: flex;
  gap: 16px;
  width: max-content;
}

.section-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 4rem 1rem 2rem;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1.5rem;
}

@media (max-width: 639px) {
  .card-grid {
    display: flex;
    gap: 12px;
    overflow-x: auto;
    overflow-y: hidden;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
    overscroll-behavior-x: contain;
  }
  .card-grid::-webkit-scrollbar {
    display: none;
  }
  .card-grid > :deep(*) {
    flex-shrink: 0;
    width: calc((100% - 12px) / 1.2);
    min-width: calc((100% - 12px) / 1.2);
  }
  .section-inner {
    padding: 4.5rem 0.75rem 1rem;
  }
  .tools-scroll-arrow {
    display: none;
  }
  .tools-scroll-track {
    gap: 12px;
  }
}

@media (max-width: 639px) and (max-height: 500px) {
  .section-inner {
    padding: 3.75rem 0.75rem 0.5rem;
  }
}

.about-section {
  min-height: 100vh;
  min-height: 100dvh;
}
</style>
