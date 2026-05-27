<template>
  <div class="voice-mic-visualizer" role="img" aria-label="麦克风音量">
    <span
      v-for="(h, i) in barHeights"
      :key="i"
      class="voice-bar"
      :style="{ height: `${h}px` }"
    />
  </div>
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue'

const props = defineProps({
  active: { type: Boolean, default: false },
})

const BAR_COUNT = 5
const barHeights = ref(Array(BAR_COUNT).fill(4))

let audioContext = null
let analyser = null
let mediaStream = null
let rafId = null
let usingFallback = false

watch(
  () => props.active,
  (on) => {
    if (on) start()
    else stop()
  },
  { immediate: true },
)

async function start() {
  stop()
  usingFallback = false
  if (!navigator.mediaDevices?.getUserMedia) {
    startFallbackAnimation()
    return
  }
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false })
    audioContext = new (window.AudioContext || window.webkitAudioContext)()
    const source = audioContext.createMediaStreamSource(mediaStream)
    analyser = audioContext.createAnalyser()
    analyser.fftSize = 128
    analyser.smoothingTimeConstant = 0.75
    source.connect(analyser)
    const buffer = new Uint8Array(analyser.frequencyBinCount)
    const tick = () => {
      if (!analyser || !props.active) return
      analyser.getByteFrequencyData(buffer)
      const step = Math.max(1, Math.floor(buffer.length / BAR_COUNT))
      barHeights.value = Array.from({ length: BAR_COUNT }, (_, i) => {
        let sum = 0
        const startIdx = i * step
        for (let j = 0; j < step; j++) sum += buffer[startIdx + j] || 0
        const avg = sum / step
        return Math.max(4, Math.min(28, 4 + (avg / 255) * 24))
      })
      rafId = requestAnimationFrame(tick)
    }
    tick()
  } catch {
    startFallbackAnimation()
  }
}

/** 无法读取麦克风流时，用律动动画提示「正在聆听」 */
function startFallbackAnimation() {
  usingFallback = true
  let phase = 0
  const tick = () => {
    if (!props.active || !usingFallback) return
    phase += 0.14
    barHeights.value = Array.from({ length: BAR_COUNT }, (_, i) => {
      return 5 + Math.abs(Math.sin(phase + i * 0.75)) * 16
    })
    rafId = requestAnimationFrame(tick)
  }
  tick()
}

function stop() {
  usingFallback = false
  if (rafId != null) {
    cancelAnimationFrame(rafId)
    rafId = null
  }
  mediaStream?.getTracks().forEach((t) => t.stop())
  mediaStream = null
  if (audioContext) {
    audioContext.close().catch(() => {})
    audioContext = null
  }
  analyser = null
  barHeights.value = Array(BAR_COUNT).fill(4)
}

onUnmounted(() => {
  stop()
})
</script>

<style scoped>
.voice-mic-visualizer {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 3px;
  height: 28px;
  padding: 0 4px;
}
.voice-bar {
  display: block;
  width: 3px;
  min-height: 4px;
  border-radius: 2px;
  background: linear-gradient(180deg, #f87171 0%, #ef4444 100%);
  transition: height 0.06s ease-out;
}
</style>
