<template>
  <div class="avatar-frame-wrapper" :style="wrapperStyle">
    <div class="avatar-frame-content" :style="contentStyle">
      <slot />
    </div>
    <svg v-if="activeFrame" class="avatar-frame-svg" :viewBox="viewBox" :width="size" :height="size">
      <defs>
        <filter :id="glowFilterId" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur in="SourceGraphic" :stdDeviation="blurStd" result="blur" />
          <feComposite in="SourceGraphic" in2="blur" operator="over" />
        </filter>
        <filter :id="glowFilterId + '-strong'" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur in="SourceGraphic" :stdDeviation="blurStd * 2" result="blur" />
          <feComposite in="SourceGraphic" in2="blur" operator="over" />
        </filter>
      </defs>

      <!-- Lightning Frame -->
      <g v-if="activeFrame === 'lightning'" :style="{ animation: `lightning-rotate ${rotateDuration}s linear infinite` }">
        <path
          v-for="(bolt, i) in lightningBolts"
          :key="'lb-' + i"
          :d="bolt.d"
          fill="none"
          stroke="#00aaff"
          :stroke-width="bolt.width"
          stroke-linecap="round"
          :filter="`url(#${glowFilterId})`"
          class="lightning-bolt"
          :style="{ animationDelay: `${i * 0.18}s` }"
        />
        <path
          v-for="(bolt, i) in lightningBoltsInner"
          :key="'lbi-' + i"
          :d="bolt.d"
          fill="none"
          stroke="#66ddff"
          :stroke-width="bolt.width"
          stroke-linecap="round"
          :filter="`url(#${glowFilterId}-strong)`"
          class="lightning-bolt"
          :style="{ animationDelay: `${i * 0.18 + 0.09}s` }"
        />
      </g>

      <!-- Flame Frame -->
      <g v-if="activeFrame === 'flame'" :style="{ animation: `flame-rotate 20s linear infinite reverse`, transformOrigin: '50% 50%' }">
        <path
          v-for="(flame, i) in flamePaths"
          :key="'fl-' + i"
          :d="flame.d"
          :fill="flame.fill"
          :opacity="flame.opacity"
          :filter="`url(#${glowFilterId})`"
          class="flame-tongue"
          :style="{ animationDelay: `${i * 0.15}s`, transformOrigin: flame.origin }"
        />
      </g>

      <!-- Stars Frame -->
      <g v-if="activeFrame === 'stars'">
        <circle
          :cx="c" :cy="c" :r="orbitR"
          fill="none" stroke="#ffd700" stroke-width="0.5" opacity="0.15"
        />
        <g
          v-for="(star, i) in starPaths"
          :key="'st-' + i"
          class="star-orbit"
          :style="{ animation: `star-orbit ${star.period}s linear infinite`, transformOrigin: `${c}px ${c}px`, animationDelay: `${star.delay}s` }"
        >
          <polygon
            :points="star.points"
            :fill="star.fill"
            class="star-twinkle"
            :style="{ animationDelay: `${i * 0.4}s`, transformOrigin: star.center }"
            :filter="`url(#${glowFilterId})`"
          />
        </g>
      </g>
    </svg>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  frame: { type: String, default: '' },
  size: { type: Number, default: 28 },
})

const uid = Math.random().toString(36).slice(2, 8)
const glowFilterId = `af-glow-${uid}`

const activeFrame = computed(() => {
  const f = props.frame
  if (!f || f === 'none') return null
  if (['lightning', 'flame', 'stars'].includes(f)) return f
  return null
})

const c = computed(() => props.size / 2)
const viewBox = computed(() => `0 0 ${props.size} ${props.size}`)
const blurStd = computed(() => props.size < 36 ? 1.5 : 2.5)
const rotateDuration = computed(() => props.size < 36 ? 10 : 8)
const orbitR = computed(() => props.size * 0.47)

// ===== Lightning Bolts =====
const boltCount = computed(() => props.size < 36 ? 5 : 7)

function makeBoltPath(angle, rInner, rOuter, jitter) {
  const cx = c.value
  const cy = c.value
  const rad = (angle * Math.PI) / 180
  const segments = 4
  const points = []
  for (let s = 0; s <= segments; s++) {
    const t = s / segments
    const r = rInner + (rOuter - rInner) * t
    const perpOffset = s === 0 || s === segments ? 0 : (Math.random() - 0.5) * jitter
    const perpRad = rad + Math.PI / 2
    const x = cx + r * Math.cos(rad) + perpOffset * Math.cos(perpRad)
    const y = cy + r * Math.sin(rad) + perpOffset * Math.sin(perpRad)
    points.push({ x, y })
  }
  return 'M' + points.map(p => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' L')
}

const lightningBolts = computed(() => {
  const count = boltCount.value
  const rIn = props.size * 0.42
  const rOut = props.size * 0.55
  const jitter = props.size * 0.12
  const w = props.size < 36 ? 1 : 1.5
  return Array.from({ length: count }, (_, i) => ({
    d: makeBoltPath(i * (360 / count), rIn, rOut, jitter),
    width: w,
  }))
})

const lightningBoltsInner = computed(() => {
  const count = boltCount.value
  const rIn = props.size * 0.38
  const rOut = props.size * 0.48
  const jitter = props.size * 0.08
  const w = props.size < 36 ? 0.6 : 1
  return Array.from({ length: count }, (_, i) => ({
    d: makeBoltPath(i * (360 / count) + 10, rIn, rOut, jitter),
    width: w,
  }))
})

// ===== Flame Tongues =====
const flameCount = computed(() => props.size < 36 ? 6 : 9)

function makeFlamePath(angle, baseR, height, width) {
  const cx = c.value
  const cy = c.value
  const rad = (angle * Math.PI) / 180
  const tipR = baseR + height
  const bx1 = cx + baseR * Math.cos(rad - width / baseR)
  const by1 = cy + baseR * Math.sin(rad - width / baseR)
  const bx2 = cx + baseR * Math.cos(rad + width / baseR)
  const by2 = cy + baseR * Math.sin(rad + width / baseR)
  const tipX = cx + tipR * Math.cos(rad)
  const tipY = cy + tipR * Math.sin(rad)
  const cp1x = cx + (baseR + height * 0.7) * Math.cos(rad - width * 0.4 / baseR)
  const cp1y = cy + (baseR + height * 0.7) * Math.sin(rad - width * 0.4 / baseR)
  const cp2x = cx + (baseR + height * 0.7) * Math.cos(rad + width * 0.4 / baseR)
  const cp2y = cy + (baseR + height * 0.7) * Math.sin(rad + width * 0.4 / baseR)
  return `M${bx1.toFixed(1)},${by1.toFixed(1)} Q${cp1x.toFixed(1)},${cp1y.toFixed(1)} ${tipX.toFixed(1)},${tipY.toFixed(1)} Q${cp2x.toFixed(1)},${cp2y.toFixed(1)} ${bx2.toFixed(1)},${by2.toFixed(1)} Z`
}

const flamePaths = computed(() => {
  const count = flameCount.value
  const baseR = props.size * 0.4
  const h = props.size * 0.18
  const w = props.size * 0.12
  const colors = ['#ff4500', '#ff6600', '#ff8c00', '#ffaa00']
  return Array.from({ length: count }, (_, i) => {
    const angle = i * (360 / count)
    const layer = i % 2
    const rh = layer === 0 ? h : h * 0.7
    const rw = layer === 0 ? w : w * 0.8
    return {
      d: makeFlamePath(angle, baseR, rh, rw),
      fill: colors[i % colors.length],
      opacity: layer === 0 ? 0.9 : 0.6,
      origin: `${c.value}px ${c.value}px`,
    }
  })
})

// ===== Stars =====
const starCount = computed(() => props.size < 36 ? 4 : 6)

function makeStarPoints(cx, cy, outerR, innerR, points) {
  let result = ''
  for (let i = 0; i < points * 2; i++) {
    const angle = (i * Math.PI) / points - Math.PI / 2
    const r = i % 2 === 0 ? outerR : innerR
    result += `${(cx + r * Math.cos(angle)).toFixed(1)},${(cy + r * Math.sin(angle)).toFixed(1)} `
  }
  return result.trim()
}

const starPaths = computed(() => {
  const count = starCount.value
  const r = orbitR.value
  const colors = ['#ffd700', '#fff8dc', '#ffec8b', '#ffd700', '#fffacd', '#ffe4b5']
  return Array.from({ length: count }, (_, i) => {
    const angle = (i * 360) / count
    const sx = c.value + r * Math.cos((angle * Math.PI) / 180)
    const sy = c.value + r * Math.sin((angle * Math.PI) / 180)
    const starR = props.size < 36 ? 2 : 3
    return {
      points: makeStarPoints(sx, sy, starR, starR * 0.4, 4),
      fill: colors[i % colors.length],
      center: `${sx.toFixed(1)}px ${sy.toFixed(1)}px`,
      period: 3 + i * 1.2,
      delay: i * 0.8,
    }
  })
})

const wrapperStyle = computed(() => ({
  position: 'relative',
  width: `${props.size}px`,
  height: `${props.size}px`,
  flexShrink: 0,
}))

const contentStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  borderRadius: '50%',
  overflow: 'hidden',
}))
</script>

<style scoped>
.avatar-frame-wrapper {
  display: inline-block;
  line-height: 0;
}
.avatar-frame-content {
  position: relative;
  z-index: 1;
}
.avatar-frame-svg {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 2;
  pointer-events: none;
}

/* ===== Lightning ===== */
.lightning-bolt {
  animation: lightning-flicker 1.2s ease-in-out infinite;
  will-change: opacity;
}

@keyframes lightning-flicker {
  0% { opacity: 0; }
  5% { opacity: 1; }
  10% { opacity: 0.2; }
  15% { opacity: 0.9; }
  20% { opacity: 0.1; }
  30% { opacity: 0.8; }
  40% { opacity: 0; }
  45% { opacity: 1; }
  50% { opacity: 0.3; }
  60% { opacity: 0; }
  70% { opacity: 0.7; }
  75% { opacity: 0; }
  85% { opacity: 0.9; }
  90% { opacity: 0.1; }
  95% { opacity: 0.6; }
  100% { opacity: 0; }
}

@keyframes lightning-rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ===== Flame ===== */
.flame-tongue {
  animation: flame-dance 0.8s ease-in-out infinite alternate;
  will-change: transform, opacity;
}

@keyframes flame-dance {
  0% { transform: scaleY(0.75) translateY(1px); opacity: 0.7; }
  50% { transform: scaleY(1.05) translateY(-1px); opacity: 1; }
  100% { transform: scaleY(0.85) translateY(0px); opacity: 0.8; }
}

@keyframes flame-rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ===== Stars ===== */
.star-orbit {
  animation: star-orbit-motion linear infinite;
  will-change: transform;
}

@keyframes star-orbit-motion {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.star-twinkle {
  animation: star-twinkle-anim 1.5s ease-in-out infinite alternate;
  will-change: transform, opacity;
}

@keyframes star-twinkle-anim {
  0% { opacity: 0.3; transform: scale(0.6); }
  50% { opacity: 1; transform: scale(1.2); }
  100% { opacity: 0.5; transform: scale(0.8); }
}
</style>
