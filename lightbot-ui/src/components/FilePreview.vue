<template>
  <div class="file-preview">
    <!-- 加载中 -->
    <div v-if="effectiveLoading" class="preview-loading">
      <a-spin />
      <span>加载中...</span>
    </div>

    <!-- Office 等：知识库不提供源文件预览 -->
    <div v-else-if="isBlockedSourcePreview" class="preview-unsupported">
      <FileOutlined class="preview-icon" />
      <p>{{ fileName }}</p>
      <p class="preview-hint">
        <template v-if="extension">.{{ extension }} </template>格式不支持在线预览
      </p>
      <button v-if="effectiveDownloadUrl" class="btn-primary-sm" @click="handleDownload">
        <DownloadOutlined /> 下载文件
      </button>
    </div>

    <!-- PDF 预览 -->
    <iframe
      v-else-if="isPdf && fileUrl"
      :src="fileUrl"
      class="preview-iframe"
      frameborder="0"
    ></iframe>

    <!-- HTML 预览 -->
    <iframe
      v-else-if="isHtml && (fileUrl || resolvedContent)"
      :src="fileUrl || undefined"
      :srcdoc="!fileUrl && resolvedContent ? resolvedContent : undefined"
      class="preview-iframe"
      frameborder="0"
      sandbox="allow-same-origin"
    ></iframe>

    <!-- 图片预览 -->
    <div v-else-if="isImage && fileUrl" class="preview-image-wrapper">
      <img :src="fileUrl" class="preview-image" :alt="fileName" />
    </div>

    <!-- Markdown 预览 -->
    <div
      v-else-if="isMarkdown && resolvedContent"
      class="preview-markdown markdown-body"
      v-html="renderedMarkdown"
    ></div>

    <!-- 代码/文本预览 -->
    <pre v-else-if="isText && resolvedContent" class="preview-text">{{ resolvedContent }}</pre>

    <!-- 不支持预览 -->
    <div v-else class="preview-unsupported">
      <FileOutlined class="preview-icon" />
      <p>{{ fileName }}</p>
      <p class="preview-hint">
        <template v-if="extension">.{{ extension }} </template>格式不支持在线预览
      </p>
      <button v-if="effectiveDownloadUrl" class="btn-primary-sm" @click="handleDownload">
        <DownloadOutlined /> 下载文件
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { renderMarkdownSync } from '@/utils/markdown_preview'
import { FileOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import { fetchTextContent, isTextLikeFile, hasSourceFilePreview } from '@/utils/filePreview'

const props = defineProps({
  /** 文件URL */
  fileUrl: {
    type: String,
    default: ''
  },
  /** 文件名 */
  fileName: {
    type: String,
    default: ''
  },
  /** 文件类型（扩展名） */
  fileType: {
    type: String,
    default: ''
  },
  /** 文本内容（用于文本/Markdown预览） */
  content: {
    type: String,
    default: ''
  },
  /** 是否加载中 */
  loading: {
    type: Boolean,
    default: false
  },
  /** 下载 URL（可与预览 URL 不同） */
  downloadUrl: {
    type: String,
    default: ''
  }
})

const fetchedContent = ref('')
const fetching = ref(false)

const extension = computed(() => {
  if (props.fileType) return props.fileType.toLowerCase()
  if (props.fileName) {
    const dot = props.fileName.lastIndexOf('.')
    return dot > 0 ? props.fileName.substring(dot + 1).toLowerCase() : ''
  }
  return ''
})

const resolvedContent = computed(() => props.content || fetchedContent.value)
const effectiveLoading = computed(() => props.loading || fetching.value)
const effectiveDownloadUrl = computed(() => props.downloadUrl || props.fileUrl)

const isPdf = computed(() => extension.value === 'pdf')
const isImage = computed(() => ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg'].includes(extension.value))
const isMarkdown = computed(() => ['md', 'markdown'].includes(extension.value))
const isHtml = computed(() => ['html', 'htm'].includes(extension.value))
const isText = computed(() => ['txt', 'csv', 'json', 'xml', 'log'].includes(extension.value))

const isBlockedSourcePreview = computed(() => {
  const ext = extension.value
  return ext ? !hasSourceFilePreview(ext) : false
})

const renderedMarkdown = computed(() => {
  if (!resolvedContent.value) return ''
  return renderMarkdownSync(resolvedContent.value)
})

watch(
  () => [props.fileUrl, props.fileName, props.content],
  async () => {
    fetchedContent.value = ''
    if (props.content || !props.fileUrl || !isTextLikeFile(props.fileName)) {
      fetching.value = false
      return
    }
    fetching.value = true
    try {
      fetchedContent.value = await fetchTextContent(props.fileUrl)
    } catch {
      fetchedContent.value = ''
    } finally {
      fetching.value = false
    }
  },
  { immediate: true },
)

function handleDownload() {
  if (effectiveDownloadUrl.value) {
    window.open(effectiveDownloadUrl.value, '_blank')
  }
}
</script>

<style scoped>
.file-preview {
  width: 100%;
  height: 100%;
  min-height: 400px;
  display: flex;
  flex-direction: column;
}

.preview-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--color-mute);
}

.preview-iframe {
  flex: 1;
  width: 100%;
  min-height: 500px;
  border: none;
}

.preview-image-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: var(--color-canvas-soft);
}

.preview-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.preview-markdown {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.8;
}

.preview-markdown :deep(h1),
.preview-markdown :deep(h2),
.preview-markdown :deep(h3) {
  margin-top: 16px;
  margin-bottom: 8px;
  font-weight: 600;
}

.preview-markdown :deep(p) {
  margin-bottom: 12px;
}

.preview-markdown :deep(code) {
  background: var(--color-canvas-soft-2);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}

.preview-markdown :deep(pre) {
  background: var(--color-canvas-soft-2);
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
}

.preview-markdown :deep(pre code) {
  background: none;
  padding: 0;
}

.preview-markdown :deep(ul),
.preview-markdown :deep(ol) {
  padding-left: 24px;
  margin-bottom: 12px;
}

.preview-markdown :deep(li) {
  margin-bottom: 4px;
}

.preview-markdown :deep(blockquote) {
  border-left: 4px solid #0070f3;
  padding-left: 16px;
  margin: 12px 0;
  color: var(--color-body);
}

.preview-markdown :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.preview-markdown :deep(th),
.preview-markdown :deep(td) {
  border: 1px solid var(--color-hairline);
  padding: 8px 12px;
  text-align: left;
}

.preview-markdown :deep(th) {
  background: var(--color-canvas-soft);
  font-weight: 600;
}

.preview-text {
  flex: 1;
  background: var(--color-canvas-soft-2);
  padding: 16px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  overflow-y: auto;
}

.preview-unsupported {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--color-mute);
}

.preview-icon {
  font-size: 48px;
  color: #d4d4d8;
}

.preview-hint {
  font-size: 13px;
  color: var(--color-mute);
}

.btn-primary-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 100px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.btn-primary-sm:hover {
  background: #27272a;
}
</style>
