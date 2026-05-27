<template>
  <a-modal
    v-model:open="visible"
    :title="title"
    :footer="null"
    width="auto"
    centered
    destroy-on-close
    class="chat-media-preview-modal"
    @cancel="handleClose"
  >
    <div class="preview-body">
      <div v-if="mediaType === 'image'" class="image-preview-wrap">
        <div class="image-toolbar">
          <button type="button" class="toolbar-btn" :disabled="scale <= 0.5" @click="zoomOut">
            <ZoomOutOutlined />
          </button>
          <span class="scale-label">{{ Math.round(scale * 100) }}%</span>
          <button type="button" class="toolbar-btn" :disabled="scale >= 3" @click="zoomIn">
            <ZoomInOutlined />
          </button>
          <button type="button" class="toolbar-btn" @click="resetZoom">
            <FullscreenOutlined />
          </button>
        </div>
        <div class="image-scroll" @wheel.prevent="onImageWheel">
          <img
            :src="src"
            :alt="fileName"
            class="preview-image"
            :style="{ transform: `scale(${scale})` }"
            draggable="false"
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
import { ref, watch, computed } from 'vue'
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
const videoRef = ref(null)

const title = computed(() => {
  if (props.fileName) return props.fileName
  return props.mediaType === 'video' ? '视频预览' : '图片预览'
})

watch(() => props.open, (open) => {
  if (open) {
    scale.value = 1
  } else if (videoRef.value) {
    videoRef.value.pause()
  }
})

function zoomIn() {
  scale.value = Math.min(3, +(scale.value + 0.25).toFixed(2))
}

function zoomOut() {
  scale.value = Math.max(0.5, +(scale.value - 0.25).toFixed(2))
}

function resetZoom() {
  scale.value = 1
}

function onImageWheel(e) {
  if (e.deltaY < 0) zoomIn()
  else zoomOut()
}

function onVideoLoaded() {
  videoRef.value?.play?.().catch(() => {})
}

function handleClose() {
  if (videoRef.value) {
    videoRef.value.pause()
    videoRef.value.currentTime = 0
  }
}
</script>

<style scoped>
.preview-body {
  min-width: 280px;
  max-width: min(90vw, 960px);
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
  border: 1px solid #e4e4e7;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #52525b;
}
.toolbar-btn:hover:not(:disabled) {
  border-color: #0070f3;
  color: #0070f3;
}
.toolbar-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.scale-label {
  font-size: 13px;
  color: #71717a;
  min-width: 48px;
  text-align: center;
}
.image-scroll {
  max-height: 70vh;
  overflow: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f4f4f5;
  border-radius: 8px;
  padding: 12px;
}
.preview-image {
  max-width: 100%;
  transform-origin: center center;
  transition: transform 0.15s ease;
  user-select: none;
}
.video-preview-wrap {
  display: flex;
  justify-content: center;
}
.preview-video {
  max-width: min(85vw, 880px);
  max-height: 70vh;
  border-radius: 8px;
  background: #000;
}
</style>

<style>
.chat-media-preview-modal .ant-modal-body {
  padding-top: 12px;
}
</style>
