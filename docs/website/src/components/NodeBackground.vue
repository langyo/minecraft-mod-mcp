<template>
  <div ref="containerRef" class="fixed inset-0 z-0 pointer-events-none" />
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useTheme } from '@/composables/useTheme'

/**
 * NodeBackground — an abstract "MCP data network".
 *
 * Floating voxel-like nodes drift slowly across the canvas. Whenever two nodes
 * come close they form a live link, and small data packets travel along that
 * link — a literal visualization of the project's own architecture:
 *
 *     AI agent  ⇄  MCP bridge  ⇄  running Minecraft
 *
 * Pure Canvas 2D (no WebGL / shaders / deps). Respects theme, reduced-motion,
 * and pauses when off-screen via IntersectionObserver.
 */

const containerRef = ref<HTMLDivElement>()
const { theme } = useTheme()

interface Node {
  x: number
  y: number
  vx: number
  vy: number
  r: number
  role: 'ai' | 'bridge' | 'game'
  phase: number
}

interface Packet {
  from: number
  to: number
  t: number
  speed: number
}

let canvas: HTMLCanvasElement
let ctx: CanvasRenderingContext2D
let nodes: Node[] = []
let packets: Packet[] = []
let animationId = 0
let visible = true
let running = true
let dpr = 1
let w = 0
let h = 0
const reducedMotion =
  typeof window !== 'undefined' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

const COLORS = {
  dark: {
    ai: '#8b5cf6',     // violet — the AI agent
    bridge: '#2ee6c8', // cyan — the MCP bridge
    game: '#5fd35f',   // grass — the game
    link: '46, 230, 200',
    line: '255, 255, 255',
  },
  light: {
    ai: '#7c3aed',
    bridge: '#0d9488',
    game: '#16a34a',
    link: '13, 148, 136',
    line: '15, 23, 42',
  },
} as const

function palette() {
  return theme.value === 'dark' ? COLORS.dark : COLORS.light
}

function rand(min: number, max: number) {
  return min + Math.random() * (max - min)
}

function spawnNodes() {
  const target = Math.max(16, Math.min(34, Math.round((w * h) / 52000)))
  nodes = []
  for (let i = 0; i < target; i++) {
    const roll = Math.random()
    const role: Node['role'] = roll < 0.33 ? 'ai' : roll < 0.66 ? 'bridge' : 'game'
    nodes.push({
      x: rand(0, w),
      y: rand(0, h),
      vx: rand(-0.18, 0.18),
      vy: rand(-0.18, 0.18),
      r: rand(2.2, 4.2),
      role,
      phase: rand(0, Math.PI * 2),
    })
  }
  packets = []
}

function linkDistance() {
  return Math.min(w, h) * 0.22
}

function maybeEmitPacket() {
  const d = linkDistance()
  for (let i = 0; i < nodes.length; i++) {
    for (let j = i + 1; j < nodes.length; j++) {
      const dx = nodes[i].x - nodes[j].x
      const dy = nodes[i].y - nodes[j].y
      if (dx * dx + dy * dy < d * d) {
        if (packets.length < 40 && Math.random() < 0.004) {
          const fwd = Math.random() < 0.5
          packets.push({
            from: fwd ? i : j,
            to: fwd ? j : i,
            t: 0,
            speed: rand(0.006, 0.014),
          })
        }
      }
    }
  }
}

function step() {
  if (!running) return
  animationId = requestAnimationFrame(step)
  if (!visible) return

  ctx.clearRect(0, 0, w, h)
  const c = palette()
  const d = linkDistance()

  // Links
  ctx.lineWidth = 1
  for (let i = 0; i < nodes.length; i++) {
    for (let j = i + 1; j < nodes.length; j++) {
      const a = nodes[i]
      const b = nodes[j]
      const dx = a.x - b.x
      const dy = a.y - b.y
      const dist2 = dx * dx + dy * dy
      if (dist2 < d * d) {
        const dist = Math.sqrt(dist2)
        const alpha = (1 - dist / d) * 0.22
        ctx.strokeStyle = `rgba(${c.link}, ${alpha})`
        ctx.beginPath()
        ctx.moveTo(a.x, a.y)
        ctx.lineTo(b.x, b.y)
        ctx.stroke()
      }
    }
  }

  maybeEmitPacket()

  // Packets
  for (let p = packets.length - 1; p >= 0; p--) {
    const pkt = packets[p]
    pkt.t += pkt.speed
    if (pkt.t >= 1) {
      packets.splice(p, 1)
      continue
    }
    const a = nodes[pkt.from]
    const b = nodes[pkt.to]
    const x = a.x + (b.x - a.x) * pkt.t
    const y = a.y + (b.y - a.y) * pkt.t
    const color = b.role === 'ai' ? c.ai : b.role === 'game' ? c.game : c.bridge
    ctx.fillStyle = color
    ctx.globalAlpha = 0.9
    ctx.beginPath()
    ctx.arc(x, y, 1.8, 0, Math.PI * 2)
    ctx.fill()
    ctx.globalAlpha = 1
  }

  // Nodes (voxel squares with a soft halo + role tint)
  const now = performance.now() / 1000
  for (const n of nodes) {
    const pulse = 0.5 + 0.5 * Math.sin(now * 1.4 + n.phase)
    const color = n.role === 'ai' ? c.ai : n.role === 'game' ? c.game : c.bridge

    // halo
    const haloR = n.r + 3 + pulse * 3
    const grad = ctx.createRadialGradient(n.x, n.y, 0, n.x, n.y, haloR)
    grad.addColorStop(0, hexA(color, 0.28 * (0.5 + pulse * 0.5)))
    grad.addColorStop(1, hexA(color, 0))
    ctx.fillStyle = grad
    ctx.beginPath()
    ctx.arc(n.x, n.y, haloR, 0, Math.PI * 2)
    ctx.fill()

    // core square (pixel / voxel feel)
    const s = n.r
    ctx.fillStyle = color
    ctx.globalAlpha = 0.85
    ctx.fillRect(n.x - s, n.y - s, s * 2, s * 2)
    ctx.globalAlpha = 1

    // motion
    n.x += n.vx
    n.y += n.vy
    if (n.x < -10) n.x = w + 10
    if (n.x > w + 10) n.x = -10
    if (n.y < -10) n.y = h + 10
    if (n.y > h + 10) n.y = -10
  }
}

function hexA(hex: string, a: number) {
  const v = hex.replace('#', '')
  const r = parseInt(v.slice(0, 2), 16)
  const g = parseInt(v.slice(2, 4), 16)
  const b = parseInt(v.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${a})`
}

function resize() {
  if (!containerRef.value) return
  w = window.innerWidth
  h = window.innerHeight
  dpr = Math.min(window.devicePixelRatio || 1, 1.5)
  canvas.width = Math.floor(w * dpr)
  canvas.height = Math.floor(h * dpr)
  canvas.style.width = w + 'px'
  canvas.style.height = h + 'px'
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  spawnNodes()
}

function onVisibility() {
  visible = !document.hidden
}

function setupListeners() {
  window.addEventListener('resize', resize)
  document.addEventListener('visibilitychange', onVisibility)

  const mq = window.matchMedia('(prefers-reduced-motion: reduce)')
  const onMotion = (e: MediaQueryListEvent) => {
    if (e.matches) {
      running = false
      cancelAnimationFrame(animationId)
      ctx.clearRect(0, 0, w, h)
      drawStatic()
    } else if (!running) {
      running = true
      step()
    }
  }
  mq.addEventListener?.('change', onMotion)
  ;(setupListeners as any)._cleanup = () => {
    mq.removeEventListener?.('change', onMotion)
  }
}

// A single static frame for reduced-motion users: show the network at rest.
function drawStatic() {
  if (!ctx) return
  ctx.clearRect(0, 0, w, h)
  const c = palette()
  const d = linkDistance()
  ctx.lineWidth = 1
  for (let i = 0; i < nodes.length; i++) {
    for (let j = i + 1; j < nodes.length; j++) {
      const a = nodes[i]
      const b = nodes[j]
      const dx = a.x - b.x
      const dy = a.y - b.y
      const dist2 = dx * dx + dy * dy
      if (dist2 < d * d) {
        const dist = Math.sqrt(dist2)
        ctx.strokeStyle = `rgba(${c.link}, ${(1 - dist / d) * 0.18})`
        ctx.beginPath()
        ctx.moveTo(a.x, a.y)
        ctx.lineTo(b.x, b.y)
        ctx.stroke()
      }
    }
  }
  for (const n of nodes) {
    const color = n.role === 'ai' ? c.ai : n.role === 'game' ? c.game : c.bridge
    ctx.fillStyle = color
    ctx.globalAlpha = 0.7
    ctx.fillRect(n.x - n.r, n.y - n.r, n.r * 2, n.r * 2)
    ctx.globalAlpha = 1
  }
}

let observer: IntersectionObserver

onMounted(() => {
  canvas = document.createElement('canvas')
  ctx = canvas.getContext('2d')!
  canvas.style.display = 'block'
  containerRef.value?.appendChild(canvas)
  resize()
  setupListeners()

  observer = new IntersectionObserver(
    ([entry]) => { visible = entry.isIntersecting },
    { threshold: 0 },
  )
  if (containerRef.value) observer.observe(containerRef.value)

  if (reducedMotion) {
    running = false
    drawStatic()
  } else {
    step()
  }
})

onBeforeUnmount(() => {
  cancelAnimationFrame(animationId)
  window.removeEventListener('resize', resize)
  document.removeEventListener('visibilitychange', onVisibility)
  ;(setupListeners as any)._cleanup?.()
  observer?.disconnect()
  canvas?.remove()
})

// Redraw tint when theme flips.
watch(theme, () => {
  if (!running) drawStatic()
})
</script>
