<template>
  <div class="avatar-frame-wrapper" :style="wrapperStyle">
    <div class="avatar-frame-content" :style="contentStyle">
      <slot />
    </div>
    <svg
      v-if="activeFrame"
      class="avatar-frame-svg"
      :viewBox="viewBox"
      :width="size"
      :height="size"
      overflow="visible"
    >
      <defs>
        <filter :id="glowId" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur in="SourceGraphic" :stdDeviation="blurStd" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        <radialGradient :id="glowId + '-flash'" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="#ffffff" stop-opacity="0.25" />
          <stop offset="40%" stop-color="#44bbff" stop-opacity="0.08" />
          <stop offset="100%" stop-color="#0088ff" stop-opacity="0" />
        </radialGradient>
      </defs>

      <!-- ===== Lightning: 行进式闪电覆盖头像 ===== -->
      <g v-if="activeFrame === 'lightning'">
        <!-- 电击闪光：每次释放时头像区域微微泛白 -->
        <circle
          :cx="c" :cy="c" :r="size * 0.48"
          :fill="`url(#${glowId}-flash)`"
          class="lt-flash"
          :style="{ transformOrigin: `${c}px ${c}px` }"
        />
        <template v-for="(bolt, i) in lightningBolts" :key="'bolt-' + i">
          <!-- 3层叠加：外层辉光 → 中层尾迹 → 内层头部 -->
          <path
            :d="bolt.d"
            fill="none"
            stroke="#0066cc"
            :stroke-width="bolt.glowWidth"
            stroke-linecap="round"
            stroke-linejoin="round"
            :filter="`url(#${glowId})`"
            class="lt-glow"
            :style="{ '--len': bolt.len, animationDelay: `${bolt.delay}s` }"
          />
          <path
            :d="bolt.d"
            fill="none"
            :stroke="bolt.trailColor"
            :stroke-width="bolt.trailWidth"
            stroke-linecap="round"
            stroke-linejoin="round"
            class="lt-trail"
            :style="{ '--len': bolt.len, animationDelay: `${bolt.delay}s` }"
          />
          <path
            :d="bolt.d"
            fill="none"
            :stroke="bolt.headColor"
            :stroke-width="bolt.headWidth"
            stroke-linecap="round"
            stroke-linejoin="round"
            class="lt-head"
            :style="{ '--len': bolt.len, animationDelay: `${bolt.delay}s` }"
          />
        </template>
      </g>

      <!-- ===== Flame ===== -->
      <g v-if="activeFrame === 'flame'" style="animation: flame-rotate 20s linear infinite reverse; transform-origin: 50% 50%;">
        <path
          v-for="(flame, i) in flamePaths"
          :key="'fl-' + i"
          :d="flame.d"
          :fill="flame.fill"
          :opacity="flame.opacity"
          :filter="`url(#${glowId})`"
          class="flame-tongue"
          :style="{ animationDelay: `${i * 0.15}s`, transformOrigin: flame.origin }"
        />
      </g>

      <!-- ===== Stars ===== -->
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
            :filter="`url(#${glowId})`"
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
const glowId = `af-${uid}`

const activeFrame = computed(() => {
  const f = props.frame
  if (!f || f === 'none') return null
  return ['lightning', 'flame', 'stars'].includes(f) ? f : null
})

const c = computed(() => props.size / 2)
const viewBox = computed(() => `0 0 ${props.size} ${props.size}`)
const blurStd = computed(() => props.size < 36 ? 1.5 : 2.5)
const orbitR = computed(() => props.size * 0.47)

// ===== 带种子的伪随机 =====
function seededRandom(seed) {
  let s = seed
  return () => {
    s = (s * 16807 + 0) % 2147483647
    return (s - 1) / 2147483646
  }
}

// ===== 锯齿环形闭合路径 + 总长度 =====
function makeJaggedRing(cx, cy, radius, jitter, segments, rng, startAngle = 0) {
  const pts = []
  for (let i = 0; i <= segments; i++) {
    const angle = startAngle + (i / segments) * Math.PI * 2
    const r = radius + (rng() - 0.5) * jitter * 2
    pts.push({ x: cx + r * Math.cos(angle), y: cy + r * Math.sin(angle) })
  }
  let d = `M${pts[0].x.toFixed(1)},${pts[0].y.toFixed(1)}`
  for (let i = 1; i < pts.length; i++) {
    const prev = pts[i - 1]
    const curr = pts[i]
    if (i % 3 === 0) {
      const mx = (prev.x + curr.x) / 2
      const my = (prev.y + curr.y) / 2
      const dx = curr.x - prev.x
      const dy = curr.y - prev.y
      const len = Math.sqrt(dx * dx + dy * dy) || 1
      const peak = (rng() - 0.5) * jitter * 2.4
      d += ` L${(mx + (-dy / len) * peak).toFixed(1)},${(my + (dx / len) * peak).toFixed(1)}`
    }
    d += ` L${curr.x.toFixed(1)},${curr.y.toFixed(1)}`
  }
  d += ' Z'

  let totalLen = 0
  for (let i = 1; i < pts.length; i++) {
    const dx = pts[i].x - pts[i - 1].x
    const dy = pts[i].y - pts[i - 1].y
    totalLen += Math.sqrt(dx * dx + dy * dy)
  }
  const dx0 = pts[0].x - pts[pts.length - 1].x
  const dy0 = pts[0].y - pts[pts.length - 1].y
  totalLen += Math.sqrt(dx0 * dx0 + dy0 * dy0)

  return { d, len: Math.round(totalLen) }
}

// ===== 行进式闪电 =====
const boltCount = computed(() => props.size < 36 ? 2 : 3)
const cycleDuration = 1.8

const lightningBolts = computed(() => {
  const cx = c.value
  const cy = c.value
  const segments = props.size < 36 ? 28 : 44
  const s = props.size

  return Array.from({ length: boltCount.value }, (_, i) => {
    const rng = seededRandom(100 + i * 37)
    // 每条闪电均匀分布在圆周上
    const startAngle = (i / boltCount.value) * Math.PI * 2
    // 半径 0.48 + 大幅锯齿偏移，让路径跨越头像区域
    const r = s * (0.46 + rng() * 0.04)
    const j = s * (0.14 + rng() * 0.06)
    const ring = makeJaggedRing(cx, cy, r, j, segments, rng, startAngle)
    const isSmall = s < 36

    return {
      d: ring.d,
      len: ring.len,
      headColor: '#ffffff',
      headWidth: isSmall ? 2 : 3,
      trailColor: '#55ccff',
      trailWidth: isSmall ? 1.5 : 2.5,
      glowWidth: isSmall ? 3 : 5,
      delay: i * (cycleDuration / boltCount.value),
    }
  })
})

// ===== Flame =====
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
    return {
      d: makeFlamePath(angle, baseR, layer === 0 ? h : h * 0.7, layer === 0 ? w : w * 0.8),
      fill: colors[i % colors.length],
      opacity: layer === 0 ? 0.9 : 0.6,
      origin: `${c.value}px ${c.value}px`,
    }
  })
})

// ===== Stars =====
const starCount = computed(() => props.size < 36 ? 4 : 6)

function makeStarPoints(cx, cy, outerR, innerR, pts) {
  let result = ''
  for (let i = 0; i < pts * 2; i++) {
    const angle = (i * Math.PI) / pts - Math.PI / 2
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

/* 电击闪光：头像区域泛白 */
.lt-flash {
  opacity: 0;
  animation: lt-flash-anim 1.8s ease-out infinite;
  will-change: opacity;
}
@keyframes lt-flash-anim {
  0%   { opacity: 0; }
  3%   { opacity: 0.8; }
  10%  { opacity: 0; }
  100% { opacity: 0; }
}

/* 外层辉光：粗模糊光晕，前半程可见 */
.lt-glow {
  stroke-dasharray: calc(var(--len) * 0.15) calc(var(--len) * 0.85);
  stroke-dashoffset: 0;
  opacity: 0;
  animation: lt-glow-move 1.8s linear infinite;
  will-change: stroke-dashoffset, opacity;
}
@keyframes lt-glow-move {
  0%   { stroke-dashoffset: 0; opacity: 0; }
  2%   { opacity: 0.5; }
  50%  { opacity: 0.3; }
  80%  { stroke-dashoffset: calc(var(--len) * -1); opacity: 0; }
  100% { stroke-dashoffset: calc(var(--len) * -1); opacity: 0; }
}

/* 中层尾迹：半透明蓝，视觉残留 */
.lt-trail {
  stroke-dasharray: calc(var(--len) * 0.35) calc(var(--len) * 0.65);
  stroke-dashoffset: 0;
  opacity: 0;
  animation: lt-trail-move 1.8s linear infinite;
  will-change: stroke-dashoffset, opacity;
}
@keyframes lt-trail-move {
  0%   { stroke-dashoffset: 0; opacity: 0; }
  2%   { opacity: 0.7; }
  60%  { opacity: 0.5; }
  80%  { stroke-dashoffset: calc(var(--len) * -1); opacity: 0.15; }
  90%  { stroke-dashoffset: calc(var(--len) * -1); opacity: 0; }
  100% { stroke-dashoffset: calc(var(--len) * -1); opacity: 0; }
}

/* 内层头部：亮白短段 */
.lt-head {
  stroke-dasharray: calc(var(--len) * 0.06) calc(var(--len) * 0.94);
  stroke-dashoffset: 0;
  opacity: 0;
  animation: lt-head-move 1.8s linear infinite;
  will-change: stroke-dashoffset, opacity;
}
@keyframes lt-head-move {
  0%   { stroke-dashoffset: 0; opacity: 0; }
  2%   { opacity: 1; }
  78%  { stroke-dashoffset: calc(var(--len) * -1); opacity: 1; }
  85%  { stroke-dashoffset: calc(var(--len) * -1); opacity: 0; }
  100% { stroke-dashoffset: calc(var(--len) * -1); opacity: 0; }
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
