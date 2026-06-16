<template>
  <div
    v-if="level != null && level >= 0"
    class="level-badge"
    :class="`level-badge--lv${level}`"
    :style="{ width: `${svgW}px`, height: `${svgH}px` }"
  >
    <!-- 底层：GIF 等级动画（sprite 逐帧播放） -->
    <div class="level-sprite" :style="spriteStyle" />

    <!-- 顶层：缩短版等级铭牌（文字 + 边框） -->
    <svg
      class="level-badge-svg"
      :viewBox="`0 0 ${vbW} ${vbH}`"
      xmlns="http://www.w3.org/2000/svg"
    >
      <defs>
        <clipPath :id="clipId">
          <path :d="outlinePath" />
        </clipPath>
        <linearGradient v-if="level === 6" :id="clipId + '-rainbow'" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color="#ff2442" />
          <stop offset="50%" stop-color="#ff6b9d" />
          <stop offset="100%" stop-color="#ff2442" />
        </linearGradient>
      </defs>

      <!-- 铭牌背景（半透明，让底层动画透出） -->
      <path
        :d="outlinePath"
        :fill="level === 6 ? `url(#${clipId}-rainbow)` : badgeColor"
        fill-opacity="0.85"
      />
      <!-- 边框 -->
      <path
        :d="outlinePath"
        fill="none"
        :stroke="borderColor"
        stroke-width="1.2"
      />

      <!-- Lv（无空格） -->
      <text :x="vbW * 0.34" :y="vbH / 2" text-anchor="middle" dominant-baseline="central"
        fill="#fff" font-size="8.5" font-weight="700" font-family="Arial,sans-serif"
        style="text-shadow: 0 0 2px rgba(0,0,0,0.3)">Lv</text>
      <!-- 等级数字（紧跟 Lv） -->
      <text :x="vbW * 0.72" :y="vbH / 2" text-anchor="middle" dominant-baseline="central"
        fill="#fff" font-size="10" font-weight="700" font-family="Arial,sans-serif"
        style="text-shadow: 0 0 2px rgba(0,0,0,0.3)">{{ level }}</text>
    </svg>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  level: { type: Number, default: null },
  size: { type: Number, default: 16 },
})

const uid = Math.random().toString(36).slice(2, 8)
const clipId = `lb-${uid}`

// 缩短版铭牌 viewBox
const vbW = 22
const vbH = 12

const svgW = computed(() => (props.size / vbH) * vbW)
const svgH = computed(() => props.size)

const outlinePath = 'M0,2 Q0,0 2,0 L20,0 Q22,0 22,2 L22,10 Q22,12 20,12 L2,12 Q0,12 0,10 Z'

const colorMap = {
  0: '#aaa',
  1: '#73c9e5',
  2: '#80d972',
  3: '#ffc842',
  4: '#ff9f43',
  5: '#e853a3',
  6: '#ff2442',
}

const borderMap = {
  0: '#888',
  1: '#5bb8d4',
  2: '#5cc45a',
  3: '#e6b422',
  4: '#e68a22',
  5: '#cc3d8a',
  6: '#dd1133',
}

// 各等级 sprite sheet 尺寸：{ frameW, frameH, frames }
const spriteInfo = {
  1: { frameW: 92,  frameH: 138, frames: 41 },
  2: { frameW: 93,  frameH: 138, frames: 41 },
  3: { frameW: 92,  frameH: 138, frames: 41 },
  4: { frameW: 93,  frameH: 139, frames: 41 },
  5: { frameW: 92,  frameH: 139, frames: 41 },
}

const badgeColor = computed(() => colorMap[props.level] || colorMap[0])
const borderColor = computed(() => borderMap[props.level] || borderMap[0])

const spriteStyle = computed(() => {
  const lv = props.level
  const info = spriteInfo[lv]
  if (!info) return { display: 'none' }

  const { frameW, frameH, frames } = info
  const totalH = frameH * frames

  return {
    backgroundImage: `url(/level_${lv}_sprite.webp)`,
    backgroundSize: `${frameW}px ${totalH}px`,
    backgroundPosition: `0 0`,
    '--sprite-h': `${totalH}px`,
    '--frame-h': `${frameH}px`,
  }
})
</script>

<style scoped>
.level-badge {
  display: inline-block;
  position: relative;
  overflow: hidden;
  vertical-align: middle;
  flex-shrink: 0;
  border-radius: 4px;
}

/* GIF 等级动画 sprite */
.level-sprite {
  position: absolute;
  inset: 0;
  background-repeat: no-repeat;
  animation: level-sprite-run 2s steps(41) infinite;
}

/* 缩短版等级铭牌（最顶层） */
.level-badge-svg {
  position: relative;
  display: block;
  width: 100%;
  height: 100%;
}

/* Lv6 彩虹脉冲 */
.level-badge--lv6 {
  animation: lv6-pulse 2s ease-in-out infinite;
}

@keyframes level-sprite-run {
  from { background-position-y: 0; }
  to   { background-position-y: calc(-1 * var(--sprite-h)); }
}

@keyframes lv6-pulse {
  0%, 100% { filter: drop-shadow(0 0 2px #ff2442); }
  50%      { filter: drop-shadow(0 0 5px #ff6b9d); }
}
</style>
