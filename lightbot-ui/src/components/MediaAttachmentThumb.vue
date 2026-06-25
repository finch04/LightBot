<template>
  <button
    v-if="canPreview"
    type="button"
    class="msg-att-thumb"
    :class="[
      { 'msg-att-thumb--video': mediaType === 'video' },
      size === 'sm' ? 'msg-att-thumb--sm' : '',
    ]"
    @click="openPreview"
  >
    <img v-if="thumbUrl" :src="thumbUrl" :alt="displayName" />
    <span v-else class="msg-att-thumb-placeholder">{{ mediaType === 'video' ? '视频' : '图片' }}</span>
    <span class="msg-att-hover-mask">
      <EyeOutlined class="mask-icon" />
      <span class="mask-text">预览</span>
    </span>
    <span v-if="mediaType === 'video'" class="msg-att-play-badge" :class="{ sm: size === 'sm' }">
      <PlayCircleOutlined />
    </span>
  </button>
  <span v-else-if="showFallbackTag" class="msg-att-file-tag">{{ displayName }}</span>

  <ChatMediaPreview
    v-model:open="mediaPreviewOpen"
    :src="mediaPreviewSrc"
    :media-type="mediaPreviewType"
    :file-name="mediaPreviewName"
  />
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { EyeOutlined, PlayCircleOutlined } from '@ant-design/icons-vue'
import ChatMediaPreview from './ChatMediaPreview.vue'
import { captureVideoThumbnail } from '../utils/videoThumbnail'

const props = defineProps({
  /** { type, previewUrl, fileName, mimeType, thumbnailUrl } */
  att: { type: Object, required: true },
  /** md=52px, sm=48px */
  size: { type: String, default: 'md' },
  showFallbackTag: { type: Boolean, default: true },
})

const thumbUrl = ref('')
const mediaPreviewOpen = ref(false)
const mediaPreviewSrc = ref('')
const mediaPreviewType = ref('image')
const mediaPreviewName = ref('')

const mediaType = computed(() => resolveMediaType(props.att))
const displayName = computed(() => props.att?.fileName || props.att?.type || '附件')
const canPreview = computed(() => {
  if (mediaType.value !== 'image' && mediaType.value !== 'video') return false
  return !!(props.att?.previewUrl || thumbUrl.value)
})

function resolveMediaType(att) {
  if (!att) return ''
  if (att.type === 'image' || att.type === 'video') return att.type
  const mime = (att.mimeType || '').toLowerCase()
  if (mime.startsWith('image/')) return 'image'
  if (mime.startsWith('video/')) return 'video'
  return att.type || ''
}

async function loadThumb() {
  const att = props.att
  if (!att) {
    thumbUrl.value = ''
    return
  }
  const type = resolveMediaType(att)
  if (type === 'image') {
    thumbUrl.value = att.thumbnailUrl || att.previewUrl || ''
    return
  }
  if (type === 'video') {
    if (att.thumbnailUrl) {
      thumbUrl.value = att.thumbnailUrl
      return
    }
    if (att.previewUrl) {
      try {
        thumbUrl.value = await captureVideoThumbnail(att.previewUrl, { maxWidth: 112, maxHeight: 72 })
      } catch {
        thumbUrl.value = ''
      }
      return
    }
  }
  thumbUrl.value = ''
}

watch(() => props.att, loadThumb, { immediate: true, deep: true })

function openPreview() {
  const att = props.att
  const type = resolveMediaType(att)
  const url = type === 'video'
    ? (att.previewUrl || '')
    : (att.previewUrl || thumbUrl.value)
  if (!url) return
  mediaPreviewSrc.value = url
  mediaPreviewType.value = type === 'video' ? 'video' : 'image'
  mediaPreviewName.value = att.fileName || ''
  mediaPreviewOpen.value = true
}
</script>

<style scoped>
.msg-att-thumb {
  position: relative;
  width: 52px;
  height: 52px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--color-hairline);
  flex-shrink: 0;
  display: block;
  background: var(--color-canvas-soft-2);
  padding: 0;
  cursor: pointer;
}
.msg-att-thumb--sm {
  width: 48px;
  height: 48px;
}
.msg-att-thumb--video {
  background: #18181b;
}
.msg-att-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.msg-att-thumb-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 11px;
  color: var(--color-mute);
}
.msg-att-hover-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  background: rgba(0, 0, 0, 0.48);
  color: #fff;
  opacity: 0;
  transition: opacity 0.15s ease;
  pointer-events: none;
}
.msg-att-thumb:hover .msg-att-hover-mask {
  opacity: 1;
}
.msg-att-hover-mask .mask-icon {
  font-size: 16px;
}
.msg-att-hover-mask .mask-text {
  font-size: 11px;
  line-height: 1;
}
.msg-att-play-badge {
  position: absolute;
  right: 3px;
  bottom: 3px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.92);
  line-height: 1;
  pointer-events: none;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.45);
}
.msg-att-play-badge.sm {
  font-size: 12px;
}
.msg-att-file-tag {
  font-size: 12px;
  color: var(--color-body);
  padding: 4px 10px;
  background: var(--color-canvas-soft-2);
  border-radius: 6px;
}
</style>
