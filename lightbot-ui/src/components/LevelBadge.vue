<template>
  <svg
    v-if="level != null && level >= 0"
    class="level-badge"
    :class="`level-badge--lv${level}`"
    :width="svgW"
    :height="svgH"
    :viewBox="`0 0 ${vbW} ${vbH}`"
    xmlns="http://www.w3.org/2000/svg"
  >
    <defs>
      <!-- 盾牌外框裁剪 -->
      <clipPath :id="clipId">
        <path :d="outlinePath" />
      </clipPath>
      <!-- Lv6 彩虹渐变 -->
      <linearGradient v-if="level === 6" :id="clipId + '-rainbow'" x1="0" y1="0" x2="1" y2="0">
        <stop offset="0%" stop-color="#ff2442" />
        <stop offset="50%" stop-color="#ff6b9d" />
        <stop offset="100%" stop-color="#ff2442" />
      </linearGradient>
      <!-- VIP 反光渐变（45° 倾斜亮带，从左下到右上） -->
      <linearGradient v-if="level >= 4" :id="clipId + '-shine'" x1="1" y1="1" x2="0" y2="0"
        gradientUnits="objectBoundingBox">
        <stop offset="0%" stop-color="#ffffff" stop-opacity="0" />
        <stop offset="35%" stop-color="#ffffff" stop-opacity="0" />
        <stop offset="48%" stop-color="#ffffff" stop-opacity="0.6" />
        <stop offset="52%" stop-color="#ffffff" stop-opacity="0.6" />
        <stop offset="65%" stop-color="#ffffff" stop-opacity="0" />
        <stop offset="100%" stop-color="#ffffff" stop-opacity="0" />
      </linearGradient>
    </defs>

    <!-- 彩色填充背景 -->
    <path
      :d="outlinePath"
      :fill="level === 6 ? `url(#${clipId}-rainbow)` : badgeColor"
    />
    <!-- 边框描边 -->
    <path
      :d="outlinePath"
      fill="none"
      :stroke="borderColor"
      stroke-width="1.2"
    />

    <!-- Lv -->
    <text :x="vbW * 0.31" :y="vbH / 2" text-anchor="middle" dominant-baseline="central"
      fill="#fff" font-size="8" font-weight="700" font-family="Arial,sans-serif"
      style="text-shadow: 0 0 2px rgba(0,0,0,0.3)">Lv</text>
    <!-- 数字 -->
    <text :x="vbW * 0.73" :y="vbH / 2" text-anchor="middle" dominant-baseline="central"
      fill="#fff" font-size="9.5" font-weight="700" font-family="Arial,sans-serif"
      style="text-shadow: 0 0 2px rgba(0,0,0,0.3)">{{ level }}</text>

    <!-- Lv5 闪光条 -->
    <rect v-if="level >= 5" class="lv-shimmer" x="-20" y="0" width="14" :height="vbH"
      fill="#fff" opacity="0.3" :clip-path="`url(#${clipId})`" />
    <!-- Lv4+ VIP 反光：45° 倾斜亮带从左上到右下扫过整个徽章 -->
    <rect v-if="level >= 4" class="lv-vip-shine"
      :x="-vbW" :y="-vbH * 2" :width="vbW * 3" :height="vbH * 5"
      :fill="`url(#${clipId}-shine)`" :clip-path="`url(#${clipId})`" />
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  level: { type: Number, default: null },
  size: { type: Number, default: 16 },
})

const uid = Math.random().toString(36).slice(2, 8)
const clipId = `lb-${uid}`

const vbW = 26
const vbH = 16

const svgW = computed(() => (props.size / vbH) * vbW)
const svgH = computed(() => props.size)

const outlinePath = 'M0,3 Q0,0 3,0 L23,0 Q26,0 26,3 L26,13 Q26,16 23,16 L3,16 Q0,16 0,13 Z'

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

const badgeColor = computed(() => colorMap[props.level] || colorMap[0])
const borderColor = computed(() => borderMap[props.level] || borderMap[0])
</script>

<style scoped>
.level-badge {
  display: inline-block;
  vertical-align: middle;
  flex-shrink: 0;
}

/* Lv4+ VIP 反光：每 2 秒从左上到右下斜扫 */
.lv-vip-shine {
  animation: vip-shine 2s ease-in-out infinite;
}

/* Lv5: 粉色闪光 */
.level-badge--lv5 .lv-shimmer {
  animation: lv5-shimmer 2s ease-in-out infinite;
}

/* Lv6: 彩虹脉冲 + 闪光 */
.level-badge--lv6 {
  animation: lv6-pulse 2s ease-in-out infinite;
}
.level-badge--lv6 .lv-shimmer {
  animation: lv5-shimmer 1.5s ease-in-out infinite;
}

@keyframes vip-shine {
  0%   { transform: translate(-14px, -32px); }
  55%  { transform: translate(14px, 48px); }
  100% { transform: translate(14px, 48px); }
}

@keyframes lv5-shimmer {
  0%   { transform: translateX(-20px); }
  100% { transform: translateX(46px); }
}

@keyframes lv6-pulse {
  0%, 100% { filter: drop-shadow(0 0 2px #ff2442); }
  50%      { filter: drop-shadow(0 0 5px #ff6b9d); }
}
</style>
