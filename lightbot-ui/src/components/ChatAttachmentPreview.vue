<template>
  <!-- 图片 / 视频：沿用对话媒体预览（缩放、拖动） -->
  <ChatMediaPreview
    v-if="isMedia"
    v-model:open="visible"
    :src="mediaSrc"
    :media-type="attachment?.type === 'video' ? 'video' : 'image'"
    :file-name="attachment?.fileName || ''"
  />

  <!-- 文档：与知识库一致的源文件 + 文本双 Tab 预览 -->
  <a-modal
    v-else-if="isDocument"
    v-model:open="visible"
    :title="attachment?.fileName || '附件预览'"
    :width="900"
    :footer="null"
    centered
    destroy-on-close
    class="chat-attachment-preview-modal"
    :body-style="{ padding: 0 }"
    @cancel="handleClose"
  >
    <template v-if="textContent" #extra>
      <span class="doc-char-count">{{ textContent.length }} 字符</span>
    </template>
    <a-tabs v-model:activeKey="docTab" class="doc-modal-tabs">
      <a-tab-pane v-if="hasSourcePreview" key="source" tab="源文件预览">
        <div class="tab-pane-body">
          <FilePreview
            :file-url="sourceUrl"
            :file-name="attachment?.fileName"
            :file-type="fileExtension"
            :content="textContent"
            :loading="false"
          />
        </div>
      </a-tab-pane>
      <a-tab-pane key="text" tab="文本预览">
        <div class="tab-pane-body">
          <div v-if="!textContent" class="modal-empty">
            <p>暂无解析文本，请尝试「源文件预览」或重新上传</p>
          </div>
          <div v-else class="text-content-preview">
            <div v-if="isMarkdown" class="markdown-content markdown-body" v-html="renderedMarkdown" />
            <pre v-else class="plain-text">{{ textContent }}</pre>
          </div>
        </div>
      </a-tab-pane>
    </a-tabs>
  </a-modal>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { renderMarkdownSync } from '@/utils/markdown_preview'
import ChatMediaPreview from './ChatMediaPreview.vue'
import FilePreview from './FilePreview.vue'
import { getFileExtension } from '../utils/chatAttachment'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 对话附件对象：含 type / previewUrl / parsedText / fileName 等 */
  attachment: { type: Object, default: null },
})

const emit = defineEmits(['update:open'])

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

const docTab = ref('text')

const isMedia = computed(() => {
  const t = props.attachment?.type
  return t === 'image' || t === 'video'
})

const isDocument = computed(() => props.attachment?.type === 'document')

const mediaSrc = computed(() => {
  const att = props.attachment
  if (!att) return ''
  if (att.type === 'video') return att.previewUrl || ''
  return att.previewUrl || att.thumbnailUrl || ''
})

const sourceUrl = computed(() => props.attachment?.previewUrl || '')

const textContent = computed(() => props.attachment?.parsedText || '')

const fileExtension = computed(() => {
  const ext = getFileExtension(props.attachment?.fileName || '')
  return ext.startsWith('.') ? ext.slice(1) : ext
})

const isMarkdown = computed(() => ['md', 'markdown'].includes(fileExtension.value))

const renderedMarkdown = computed(() => {
  if (!textContent.value || !isMarkdown.value) return ''
  return renderMarkdownSync(textContent.value)
})

const hasSourcePreview = computed(() => {
  if (!sourceUrl.value) return false
  const ext = fileExtension.value
  if (['pdf', 'html', 'htm'].includes(ext)) return true
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg'].includes(ext)) return true
  if (['docx', 'pptx', 'xlsx'].includes(ext)) return true
  return false
})

watch(
  () => props.open,
  (open) => {
    if (!open) return
    docTab.value = hasSourcePreview.value ? 'source' : 'text'
  }
)

function handleClose() {
  visible.value = false
}
</script>

<style scoped>
.doc-char-count {
  font-size: 12px;
  color: var(--color-mute);
  margin-right: 8px;
}
.doc-modal-tabs :deep(.ant-tabs-nav) {
  margin: 0 16px;
}
.tab-pane-body {
  min-height: 420px;
  max-height: 70vh;
  overflow: auto;
  padding: 0;
}
.tab-pane-body :deep(.file-preview) {
  min-height: 400px;
}
.modal-empty {
  padding: 48px 24px;
  text-align: center;
  color: var(--color-mute);
  font-size: 14px;
}
.text-content-preview {
  padding: 16px 20px;
}
.text-content-preview .plain-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.6;
  color: #3f3f46;
  font-family: inherit;
}
.text-content-preview .markdown-content {
  font-size: 14px;
  line-height: 1.7;
  color: #18181b;
}
</style>
