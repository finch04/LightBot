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

// ===== 竖向锯齿闪电路径（从圆弧顶劈到底） =====
function makeLightningPath(cx, cy, radius, jitter, segments, rng, angle) {
  const arcSpan = Math.PI * 0.78
  const startA = angle - arcSpan / 2
  const endA = angle + arcSpan / 2
  const pts = []
  for (let i = 0; i <= segments; i++) {
    const t = i / segments
    const a = startA + t * (endA - startA)
    const r = radius + (rng() - 0.5) * jitter * 2
    pts.push({ x: cx + r * Math.cos(a), y: cy + r * Math.sin(a) })
  }
  let d = `M${pts[0].x.toFixed(1)},${pts[0].y.toFixed(1)}`
  for (let i = 1; i < pts.length; i++) {
    if (i % 3 === 0) {
      const prev = pts[i - 1]
      const curr = pts[i]
      const mx = (prev.x + curr.x) / 2
      const my = (prev.y + curr.y) / 2
      const dx = curr.x - prev.x
      const dy = curr.y - prev.y
      const len = Math.sqrt(dx * dx + dy * dy) || 1
      const peak = (rng() - 0.5) * jitter * 2.4
      d += ` L${(mx + (-dy / len) * peak).toFixed(1)},${(my + (dx / len) * peak).toFixed(1)}`
    }
    d += ` L${pts[i].x.toFixed(1)},${pts[i].y.toFixed(1)}`
  }
  let totalLen = 0
  for (let i = 1; i < pts.length; i++) {
    const dx = pts[i].x - pts[i - 1].x
    const dy = pts[i].y - pts[i - 1].y
    totalLen += Math.sqrt(dx * dx + dy * dy)
  }
  return { d, len: Math.round(totalLen) }
}

// ===== 行进式闪电（从上到下劈入，依次触发） =====
const boltCount = computed(() => props.size < 36 ? 2 : 3)
const cycleDuration = 2.4

const lightningBolts = computed(() => {
  const cx = c.value
  const cy = c.value
  const segments = props.size < 36 ? 20 : 28
  const s = props.size

  return Array.from({ length: boltCount.value }, (_, i) => {
    const rng = seededRandom(100 + i * 37)
    // 均匀分布在圆周上
    const centerAngle = -Math.PI / 2 + (i / boltCount.value) * Math.PI * 2
    const bolt = makeLightningPath(cx, cy, s * 0.48, s * 0.06, segments, rng, centerAngle)
    const isSmall = s < 36

    return {
      d: bolt.d,
      len: bolt.len,
      headColor: '#ffffff',
      headWidth: isSmall ? 2.5 : 3.5,
      trailColor: '#55ccff',
      trailWidth: isSmall ? 2 : 3,
      glowWidth: isSmall ? 4 : 6,
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

/* ===== Lightning: 劈入式闪电，从上到下行进，留下发光通路 ===== */

/* 闪光：劈入瞬间头像区域泛白 */
.lt-flash {
  opacity: 0;
  animation: lt-flash-anim 2.4s ease-out infinite;
  will-change: opacity;
}
@keyframes lt-flash-anim {
  0%   { opacity: 0; }
  5%   { opacity: 0.7; }
  20%  { opacity: 0; }
  100% { opacity: 0; }
}

/* 外层辉光：跟随头部行进，扩散模糊光 */
.lt-glow {
  stroke-dasharray: calc(var(--len) * 0.5) calc(var(--len) * 0.5);
  stroke-dashoffset: 0;
  opacity: 0;
  animation: lt-glow-move 2.4s linear infinite;
  will-change: stroke-dashoffset, opacity;
}
@keyframes lt-glow-move {
  0%   { stroke-dashoffset: 0; opacity: 0; }
  8%   { opacity: 0.4; }
  55%  { stroke-dashoffset: calc(var(--len) * -0.55); opacity: 0.3; }
  75%  { stroke-dashoffset: calc(var(--len) * -1); opacity: 0.15; }
  90%  { stroke-dashoffset: calc(var(--len) * -1); opacity: 0; }
  100% { stroke-dashoffset: calc(var(--len) * -1); opacity: 0; }
}

/* 中层尾迹：闪电经过后留下的发光通路 */
.lt-trail {
  stroke-dasharray: calc(var(--len) * 0.65) calc(var(--len) * 0.35);
  stroke-dashoffset: 0;
  opacity: 0;
  animation: lt-trail-move 2.4s linear infinite;
  will-change: stroke-dashoffset, opacity;
}
@keyframes lt-trail-move {
  0%   { stroke-dashoffset: 0; opacity: 0; }
  8%   { opacity: 0.8; }
  50%  { stroke-dashoffset: calc(var(--len) * -0.55); opacity: 0.7; }
  70%  { stroke-dashoffset: calc(var(--len) * -1); opacity: 0.4; }
  85%  { stroke-dashoffset: calc(var(--len) * -1); opacity: 0.15; }
  95%  { stroke-dashoffset: calc(var(--len) * -1); opacity: 0; }
  100% { stroke-dashoffset: calc(var(--len) * -1); opacity: 0; }
}

/* 内层头部：闪电劈入的亮白尖端 */
.lt-head {
  stroke-dasharray: calc(var(--len) * 0.08) calc(var(--len) * 0.92);
  stroke-dashoffset: 0;
  opacity: 0;
  animation: lt-head-move 2.4s linear infinite;
  will-change: stroke-dashoffset, opacity;
}
@keyframes lt-head-move {
  0%   { stroke-dashoffset: 0; opacity: 0; }
  5%   { opacity: 1; }
  65%  { stroke-dashoffset: calc(var(--len) * -0.92); opacity: 1; }
  72%  { stroke-dashoffset: calc(var(--len) * -1); opacity: 0.6; }
  82%  { stroke-dashoffset: calc(var(--len) * -1); opacity: 0; }
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
