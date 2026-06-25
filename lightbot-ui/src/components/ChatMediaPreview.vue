<template>
  <a-modal
    v-model:open="visible"
    :title="title"
    :footer="null"
    :width="modalWidth"
    centered
    destroy-on-close
    class="chat-media-preview-modal"
    :class="{ 'is-video-preview': mediaType === 'video' }"
    @cancel="handleClose"
  >
    <div class="preview-body" :class="{ 'preview-body--video': mediaType === 'video' }">
      <div v-if="mediaType === 'image'" class="image-preview-wrap">
        <div class="image-toolbar">
          <button type="button" class="toolbar-btn" :disabled="scale <= 0.5" @click="zoomOut">
            <ZoomOutOutlined />
          </button>
          <span class="scale-label">{{ Math.round(scale * 100) }}%</span>
          <button type="button" class="toolbar-btn" :disabled="scale >= 3" @click="zoomIn">
            <ZoomInOutlined />
          </button>
          <button type="button" class="toolbar-btn" @click="resetView">
            <FullscreenOutlined />
          </button>
        </div>
        <div
          ref="viewportRef"
          class="image-viewport"
          :class="{ 'is-dragging': dragging }"
          @wheel.prevent="onImageWheel"
          @mousedown="onPanStart"
        >
          <img
            ref="imageRef"
            :src="src"
            :alt="fileName"
            class="preview-image"
            :style="imageTransformStyle"
            draggable="false"
            @load="onImageLoad"
          />
        </div>
      </div>
      <div v-else-if="mediaType === 'video'" class="video-preview-wrap">
        <video
          ref="videoRef"
          :src="src"
          class="preview-video"
          controls
          controlslist="nodownload"
          playsinline
          @loadedmetadata="onVideoLoaded"
        />
      </div>
    </div>
  </a-modal>
</template>

<script setup>
import { ref, watch, computed, onUnmounted } from 'vue'
import { ZoomInOutlined, ZoomOutOutlined, FullscreenOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  src: { type: String, default: '' },
  mediaType: { type: String, default: 'image' },
  fileName: { type: String, default: '' },
})

const emit = defineEmits(['update:open'])

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

const scale = ref(1)
const panX = ref(0)
const panY = ref(0)
const dragging = ref(false)
const dragStart = ref({ x: 0, y: 0, panX: 0, panY: 0 })
const videoRef = ref(null)
const imageRef = ref(null)
const viewportRef = ref(null)
const videoModalWidth = ref(720)

const title = computed(() => {
  if (props.fileName) return props.fileName
  return props.mediaType === 'video' ? '视频预览' : '图片预览'
})

const modalWidth = computed(() => {
  if (props.mediaType === 'video') return videoModalWidth.value
  return 'auto'
})

const imageTransformStyle = computed(() => ({
  transform: `translate(${panX.value}px, ${panY.value}px) scale(${scale.value})`,
}))

watch(() => props.open, (open) => {
  if (open) {
    resetView()
    videoModalWidth.value = 720
  } else {
    if (videoRef.value) {
      videoRef.value.pause()
    }
    stopPan()
  }
})

function resetView() {
  scale.value = 1
  panX.value = 0
  panY.value = 0
}

function zoomIn() {
  scale.value = Math.min(3, +(scale.value + 0.25).toFixed(2))
}

function zoomOut() {
  scale.value = Math.max(0.5, +(scale.value - 0.25).toFixed(2))
}

function onImageWheel(e) {
  if (e.deltaY < 0) zoomIn()
  else zoomOut()
}

function onImageLoad() {
  resetView()
}

function onPanStart(e) {
  if (e.button !== 0) return
  dragging.value = true
  dragStart.value = {
    x: e.clientX,
    y: e.clientY,
    panX: panX.value,
    panY: panY.value,
  }
  document.addEventListener('mousemove', onPanMove)
  document.addEventListener('mouseup', onPanEnd)
}

function onPanMove(e) {
  if (!dragging.value) return
  panX.value = dragStart.value.panX + (e.clientX - dragStart.value.x)
  panY.value = dragStart.value.panY + (e.clientY - dragStart.value.y)
}

function onPanEnd() {
  stopPan()
}

function stopPan() {
  dragging.value = false
  document.removeEventListener('mousemove', onPanMove)
  document.removeEventListener('mouseup', onPanEnd)
}

function onVideoLoaded(e) {
  const el = e.target
  const vw = el.videoWidth || 960
  const vh = el.videoHeight || 540
  const bodyPad = 48
  const maxW = window.innerWidth * 0.92 - bodyPad
  const maxH = window.innerHeight * 0.88 - 120
  let w = vw
  let h = vh
  const ratio = w / h
  if (w > maxW) {
    w = maxW
    h = w / ratio
  }
  if (h > maxH) {
    h = maxH
    w = h * ratio
  }
  w = Math.max(320, Math.round(w))
  h = Math.max(180, Math.round(h))
  el.style.width = `${w}px`
  el.style.height = `${h}px`
  videoModalWidth.value = w + bodyPad
  el.play?.().catch(() => {})
}

function handleClose() {
  if (videoRef.value) {
    videoRef.value.pause()
    videoRef.value.currentTime = 0
    videoRef.value.style.width = ''
    videoRef.value.style.height = ''
  }
  stopPan()
}

onUnmounted(() => {
  stopPan()
})
</script>

<style scoped>
.preview-body {
  min-width: 280px;
  max-width: min(92vw, 1200px);
}
.preview-body--video {
  min-width: 0;
  max-width: none;
}
.image-preview-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.image-toolbar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}
.toolbar-btn {
  width: 32px;
  height: 32px;
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  background: var(--color-canvas);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-body);
}
.toolbar-btn:hover:not(:disabled) {
  border-color: var(--color-link);
  color: var(--color-link);
}
.toolbar-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.scale-label {
  font-size: 13px;
  color: var(--color-mute);
  min-width: 48px;
  text-align: center;
}
.image-viewport {
  width: min(88vw, 1100px);
  height: min(78vh, 720px);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-canvas-soft-2);
  border-radius: 8px;
  cursor: grab;
  user-select: none;
  touch-action: none;
}
.image-viewport.is-dragging {
  cursor: grabbing;
}
.preview-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  transform-origin: center center;
  transition: transform 0.08s ease-out;
  will-change: transform;
  pointer-events: none;
}
.video-preview-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
}
.preview-video {
  display: block;
  border-radius: 8px;
  background: #000;
}
</style>

<style>
.chat-media-preview-modal .ant-modal-body {
  padding-top: 12px;
}
.chat-media-preview-modal.is-video-preview .ant-modal-body {
  padding: 12px 24px 20px;
}
.chat-media-preview-modal.is-video-preview .ant-modal-content {
  max-width: 96vw;
}
</style>
