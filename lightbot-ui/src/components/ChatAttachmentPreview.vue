<template>
  <!-- 图片 / 视频：沿用对话媒体预览（缩放、拖动） -->
  <ChatMediaPreview
    v-if="isMedia"
    v-model:open="visible"
    :src="mediaSrc"
    :media-type="attachment?.type === 'video' ? 'video' : 'image'"
    :file-name="attachment?.fileName || ''"
  />

  <!-- 文档：与知识库/会话文件预览统一 -->
  <FilePreviewModal
    v-else-if="isDocument"
    v-model:open="visible"
    :file-name="displayName"
    :file-url="sourceUrl"
    :download-url="sourceUrl"
    :file-type="fileExtension"
  />
</template>

<script setup>
import { computed } from 'vue'
import ChatMediaPreview from './ChatMediaPreview.vue'
import FilePreviewModal from './FilePreviewModal.vue'
import { getFileExtension } from '../utils/chatAttachment'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 对话附件对象：含 type / previewUrl / fileName 等 */
  attachment: { type: Object, default: null },
})

const emit = defineEmits(['update:open'])

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

const isMedia = computed(() => {
  const t = props.attachment?.type
  return t === 'image' || t === 'video'
})

const isDocument = computed(() => props.attachment?.type === 'document')

const displayName = computed(() => props.attachment?.fileName || '附件预览')

const mediaSrc = computed(() => {
  const att = props.attachment
  if (!att) return ''
  if (att.type === 'video') return att.previewUrl || ''
  return att.previewUrl || att.thumbnailUrl || ''
})

const sourceUrl = computed(() => props.attachment?.previewUrl || '')

const fileExtension = computed(() => {
  const ext = getFileExtension(props.attachment?.fileName || '')
  return ext.startsWith('.') ? ext.slice(1) : ext
})
</script>
