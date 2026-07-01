<template>
  <div class="chat-att-tile" :class="[`chat-att-tile--${att.type || 'file'}`, { 'is-uploading': uploading }]">
    <button
      v-if="!uploading && canPreview"
      type="button"
      class="chat-att-tile-btn"
      @click="$emit('preview', att)"
    >
      <img
        v-if="showThumb"
        :src="thumbUrl"
        :alt="att.fileName || ''"
        class="chat-att-tile-img"
      />
      <span v-else class="chat-att-tile-doc">
        <FileTypeIcon :name="iconName" :size="22" class="doc-icon" />
        <span class="doc-name">{{ shortName }}</span>
      </span>
      <span class="msg-att-hover-mask">
        <EyeOutlined class="mask-icon" />
        <span class="mask-text">预览</span>
      </span>
      <span v-if="att.type === 'video' && showThumb" class="msg-att-play-badge sm"><PlayCircleOutlined /></span>
    </button>
    <div v-else-if="uploading" class="chat-att-tile-uploading">
      <LoadingOutlined spin class="upload-spin" />
      <span class="upload-label">上传中</span>
    </div>
    <span v-else class="chat-att-tile-fallback">{{ att.fileName || att.type }}</span>
    <button
      v-if="removable && !uploading"
      type="button"
      class="chat-att-tile-remove"
      title="移除"
      @click.stop="$emit('remove')"
    >
      <CloseOutlined />
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  EyeOutlined,
  CloseOutlined,
  PlayCircleOutlined,
  LoadingOutlined,
} from '@ant-design/icons-vue'
import FileTypeIcon from './FileTypeIcon.vue'

const props = defineProps({
  att: { type: Object, required: true },
  /** 缩略图 URL（图片/视频） */
  thumbUrl: { type: String, default: '' },
  uploading: { type: Boolean, default: false },
  removable: { type: Boolean, default: false },
})

defineEmits(['preview', 'remove'])

const showThumb = computed(() => {
  const t = props.att?.type
  return Boolean(props.thumbUrl && (t === 'image' || t === 'video'))
})

const iconName = computed(() => {
  const att = props.att
  if (att?.fileName) return att.fileName
  if (att?.type === 'image') return 'image.png'
  if (att?.type === 'video') return 'video.mp4'
  if (att?.type === 'document') return 'document.pdf'
  return att?.type || 'file'
})

const canPreview = computed(() => {
  if (props.uploading) return false
  const att = props.att
  if (!att) return false
  if (att.type === 'image' || att.type === 'video') {
    return Boolean(props.thumbUrl || att.previewUrl)
  }
  if (att.type === 'document') {
    return Boolean(att.previewUrl || att.objectKey)
  }
  return false
})

const shortName = computed(() => {
  const name = props.att?.fileName || ''
  if (name.length <= 14) return name
  const dot = name.lastIndexOf('.')
  if (dot > 0) {
    return name.slice(0, 8) + '…' + name.slice(dot)
  }
  return name.slice(0, 12) + '…'
})
</script>

<style scoped>
.chat-att-tile {
  position: relative;
  flex-shrink: 0;
}
.chat-att-tile-btn {
  position: relative;
  width: 52px;
  height: 52px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--color-hairline);
  padding: 0;
  background: var(--color-canvas-soft-2);
  cursor: pointer;
  display: block;
}
.chat-att-tile--document .chat-att-tile-btn {
  width: auto;
  min-width: 52px;
  max-width: 120px;
  height: 52px;
}
.chat-att-tile-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.chat-att-tile-doc {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 100%;
  height: 100%;
  padding: 6px 8px;
  box-sizing: border-box;
}
.doc-icon {
  flex-shrink: 0;
}
.doc-name {
  font-size: 10px;
  color: var(--color-body);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.2;
}
.chat-att-tile-uploading {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  border: 1px dashed #93c5fd;
  background: linear-gradient(110deg, #f4f4f5 8%, #e0f2fe 18%, #f4f4f5 33%);
  background-size: 200% 100%;
  animation: att-shimmer 1.2s ease-in-out infinite;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.upload-spin {
  font-size: 18px;
  color: var(--color-link);
}
.upload-label {
  font-size: 10px;
  color: #3b82f6;
}
.chat-att-tile-fallback {
  font-size: 12px;
  color: var(--color-body);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
  padding: 8px;
}
.chat-att-tile-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  padding: 0;
  cursor: pointer;
  color: var(--color-mute);
  z-index: 2;
  line-height: 1;
}
.chat-att-tile-remove:hover {
  color: #ef4444;
  border-color: #fecaca;
}
.chat-att-tile-btn:hover .msg-att-hover-mask {
  opacity: 1;
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
  font-size: 12px;
  color: rgba(255, 255, 255, 0.92);
  line-height: 1;
  pointer-events: none;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.45);
}
.msg-att-play-badge.sm {
  font-size: 11px;
}
@keyframes att-shimmer {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}
</style>
