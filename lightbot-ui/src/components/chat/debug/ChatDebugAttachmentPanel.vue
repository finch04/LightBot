<template>
  <div class="debug-attachment-panel">
    <a-alert type="info" class="debug-preview-info" show-icon>
      <template #message>
        <div class="debug-preview-header">
          <strong>支持预览的文件类型</strong>
          <a-button size="small" type="link" @click="showPreviewList = !showPreviewList">
            {{ showPreviewList ? '收起' : '展开列表' }}
          </a-button>
        </div>
        <ul v-if="showPreviewList" class="debug-preview-list">
          <li><strong>图片</strong>：{{ IMAGE_PREVIEW.join(', ') }}</li>
          <li><strong>视频</strong>：{{ VIDEO_PREVIEW.join(', ') }}</li>
          <li><strong>PDF</strong>：pdf</li>
          <li><strong>HTML</strong>：html, htm</li>
          <li><strong>Markdown</strong>：md, markdown</li>
          <li><strong>纯文本 / 代码 / 数据 / 配置</strong>：共 {{ TEXT_PREVIEW.length }} 种：{{ TEXT_PREVIEW.join(', ') }}</li>
          <li><strong>Office 文档</strong>（不支持预览，仅显示提示）：doc, docx, xls, xlsx, ppt, pptx</li>
        </ul>
      </template>
    </a-alert>

    <div class="debug-panel-toolbar">
      <a-button type="primary" @click="$emit('parse')">解析预览</a-button>
      <a-select
        v-model:value="selectedSample"
        :options="sampleOptions"
        style="width: 180px"
        @change="loadSample"
      />
      <a-button @click="triggerUpload">选择本地文件</a-button>
      <a-button @click="clearAttachments">清空</a-button>
    </div>

    <a-alert
      type="info"
      show-icon
      class="debug-hint"
      :message="`纯前端预览：本地文件不会上传到 MinIO，单文件最大 ${maxSizeMb}MB。`"
    />

    <input
      ref="fileInputRef"
      type="file"
      multiple
      class="debug-file-input"
      @change="onFilesSelected"
    />

    <div class="debug-field">
      <div class="debug-editor-label">用户消息内容</div>
      <a-textarea v-model:value="content" :rows="3" />
    </div>

    <div class="debug-attachment-list">
      <div
        v-for="att in attachments"
        :key="att.id"
        class="debug-attachment-item"
      >
        <div class="debug-attachment-main">
          <span class="debug-attachment-name">{{ att.fileName }}</span>
          <span class="debug-attachment-meta">{{ att.type }} · {{ formatSize(att.size) }}</span>
        </div>
        <a-tag v-if="att.localOnly" color="blue">local</a-tag>
        <a-button size="small" danger @click="removeAttachment(att.id)">移除</a-button>
      </div>
      <div v-if="!attachments.length" class="debug-empty">暂无附件，请加载样例或选择本地文件。</div>
    </div>

    <a-alert
      v-if="parseError"
      type="error"
      :message="parseError"
      show-icon
      closable
      class="debug-parse-error"
      @close="parseError = ''"
    />
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  TEXT_SOURCE_PREVIEW_EXTENSIONS,
  IMAGE_SOURCE_PREVIEW_EXTENSIONS,
  VIDEO_SOURCE_PREVIEW_EXTENSIONS,
  OFFICE_NO_SOURCE_PREVIEW_EXTENSIONS,
} from '@/utils/filePreview'

const MAX_LOCAL_FILE_SIZE = 10 * 1024 * 1024
const maxSizeMb = MAX_LOCAL_FILE_SIZE / 1024 / 1024

const TEXT_PREVIEW = [...TEXT_SOURCE_PREVIEW_EXTENSIONS].sort()
const IMAGE_PREVIEW = [...IMAGE_SOURCE_PREVIEW_EXTENSIONS].sort()
const VIDEO_PREVIEW = [...VIDEO_SOURCE_PREVIEW_EXTENSIONS].sort()
const showPreviewList = ref(false)

const sampleOptions = [
  { value: 'mixed', label: '混合附件' },
  { value: 'image', label: '图片预览' },
  { value: 'text', label: '文本/Markdown' },
  { value: 'unsupported', label: '不支持格式' },
]

const emit = defineEmits(['parse'])

const fileInputRef = ref(null)
const selectedSample = ref('mixed')
const content = ref('请查看这些附件，并总结每个文件的内容。')
const attachments = ref([])
const parseError = ref('')
const objectUrls = new Set()

function createObjectUrl(blob) {
  const url = URL.createObjectURL(blob)
  objectUrls.add(url)
  return url
}

function revokeObjectUrl(url) {
  if (!url || !objectUrls.has(url)) return
  URL.revokeObjectURL(url)
  objectUrls.delete(url)
}

function revokeAttachment(att) {
  revokeObjectUrl(att.previewUrl)
  revokeObjectUrl(att.thumbnailUrl)
}

function createSampleAttachment({ fileName, type, mimeType, content: sampleContent, size }) {
  const blob = new Blob([sampleContent], { type: mimeType })
  const url = createObjectUrl(blob)
  return {
    id: `sample-${fileName}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    type,
    fileName,
    size: size ?? blob.size,
    mimeType,
    previewUrl: url,
    thumbnailUrl: type === 'image' ? url : '',
    localOnly: true,
  }
}

function loadSample() {
  clearAttachments()
  const imageSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360"><rect width="640" height="360" fill="#eef2ff"/><circle cx="165" cy="170" r="72" fill="#6366f1"/><rect x="285" y="115" width="250" height="110" rx="18" fill="#14b8a6"/><text x="320" y="255" font-size="32" fill="#111827">智元 Debug</text></svg>`
  const md = '# Debug 附件样例\n\n- 支持 Markdown 预览\n- 支持本地 Blob URL\n- 不需要上传到后端\n'
  const csv = 'name,score\n智元,0.96\nDebug Lab,0.91\n'
  const unsupported = 'Office placeholder'
  const createImageSamples = () => [
    createSampleAttachment({ fileName: 'debug-image.svg', type: 'image', mimeType: 'image/svg+xml', content: imageSvg }),
  ]
  const createTextSamples = () => [
    createSampleAttachment({ fileName: 'debug-notes.md', type: 'document', mimeType: 'text/markdown', content: md }),
    createSampleAttachment({ fileName: 'scores.csv', type: 'document', mimeType: 'text/csv', content: csv }),
  ]
  const createUnsupportedSamples = () => [
    createSampleAttachment({ fileName: 'legacy-report.docx', type: 'document', mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', content: unsupported, size: 256000 }),
  ]
  const sampleBuilders = {
    image: createImageSamples,
    text: createTextSamples,
    unsupported: createUnsupportedSamples,
    mixed: () => [...createImageSamples(), ...createTextSamples(), ...createUnsupportedSamples()],
  }
  attachments.value = sampleBuilders[selectedSample.value]?.() || []
  parseError.value = ''
}

function triggerUpload() {
  fileInputRef.value?.click()
}

function inferAttachmentType(file) {
  const name = file.name || ''
  if (file.type.startsWith('image/')) return 'image'
  if (file.type.startsWith('video/')) return 'video'
  return 'document'
}

function onFilesSelected(e) {
  parseError.value = ''
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length) return
  const next = []
  for (const file of files) {
    if (file.size > MAX_LOCAL_FILE_SIZE) {
      message.warning(`${file.name} 超过 ${maxSizeMb}MB，已跳过`)
      continue
    }
    const url = createObjectUrl(file)
    const type = inferAttachmentType(file)
    next.push({
      id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      type,
      fileName: file.name,
      size: file.size,
      mimeType: file.type,
      previewUrl: url,
      thumbnailUrl: type === 'image' ? url : '',
      localOnly: true,
    })
  }
  attachments.value = [...attachments.value, ...next]
}

function removeAttachment(id) {
  const target = attachments.value.find((att) => att.id === id)
  if (target) revokeAttachment(target)
  attachments.value = attachments.value.filter((att) => att.id !== id)
}

function clearAttachments() {
  attachments.value.forEach(revokeAttachment)
  attachments.value = []
}

function formatSize(size) {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function validateAndGetMessage() {
  parseError.value = ''
  if (!attachments.value.length) {
    parseError.value = '请至少准备一个附件'
    return null
  }
  return {
    role: 'user',
    content: content.value || '[附件]',
    metadata: {
      attachments: attachments.value.map((att) => ({ ...att })),
    },
  }
}

loadSample()

onBeforeUnmount(() => {
  clearAttachments()
})

defineExpose({ validateAndGetMessage, loadSample })
</script>

<style scoped>
.debug-attachment-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.debug-preview-info {
  margin-bottom: 12px;
}
.debug-preview-info :deep(.ant-alert-message) {
  font-size: 12px;
  color: var(--gray-600);
}
.debug-preview-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: 600;
  color: var(--gray-800);
}
.debug-preview-list {
  margin: 0;
  padding-left: 16px;
  line-height: 1.8;
}
.debug-preview-list li {
  margin-bottom: 4px;
}

.debug-panel-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.debug-hint,
.debug-field {
  margin-bottom: 12px;
}

.debug-file-input {
  display: none;
}

.debug-editor-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--gray-700);
  margin-bottom: 8px;
}

.debug-attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.debug-attachment-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--gray-200);
  border-radius: 6px;
  background: var(--color-canvas);
}

.debug-attachment-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.debug-attachment-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--gray-800);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.debug-attachment-meta,
.debug-empty {
  font-size: 12px;
  color: var(--gray-500);
}

.debug-empty {
  padding: 24px;
  text-align: center;
}

.debug-parse-error {
  margin-top: 8px;
}
</style>
