<template>
  <div class="file-preview">
    <!-- 加载中 -->
    <div v-if="loading" class="preview-loading">
      <a-spin />
      <span>加载中...</span>
    </div>

    <!-- PDF 预览 -->
    <iframe
      v-else-if="isPdf"
      :src="fileUrl"
      class="preview-iframe"
      frameborder="0"
    ></iframe>

    <!-- HTML 预览 -->
    <iframe
      v-else-if="isHtml && fileUrl"
      :src="fileUrl"
      class="preview-iframe"
      frameborder="0"
      sandbox="allow-same-origin"
    ></iframe>

    <!-- 图片预览 -->
    <div v-else-if="isImage" class="preview-image-wrapper">
      <img :src="fileUrl" class="preview-image" :alt="fileName" />
    </div>

    <!-- Markdown 预览 -->
    <div
      v-else-if="isMarkdown && content"
      class="preview-markdown markdown-body"
      v-html="renderedMarkdown"
    ></div>

    <!-- 代码/文本预览 -->
    <pre v-else-if="isText && content" class="preview-text">{{ content }}</pre>

    <!-- Office 文档预览（Microsoft Online） -->
    <iframe
      v-else-if="isOfficeOnline"
      :src="officePreviewUrl"
      class="preview-iframe"
      frameborder="0"
    ></iframe>

    <!-- 不支持预览 -->
    <div v-else class="preview-unsupported">
      <FileOutlined class="preview-icon" />
      <p>{{ fileName }}</p>
      <p class="preview-hint">该文件类型不支持在线预览</p>
      <button v-if="fileUrl" class="btn-primary-sm" @click="handleDownload">
        <DownloadOutlined /> 下载文件
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { marked } from 'marked'
import { FileOutlined, DownloadOutlined } from '@ant-design/icons-vue'

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
  }
})

// 文件类型判断
const extension = computed(() => {
  if (props.fileType) return props.fileType.toLowerCase()
  if (props.fileName) {
    const dot = props.fileName.lastIndexOf('.')
    return dot > 0 ? props.fileName.substring(dot + 1).toLowerCase() : ''
  }
  return ''
})

const isPdf = computed(() => extension.value === 'pdf')
const isImage = computed(() => ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg'].includes(extension.value))
const isMarkdown = computed(() => ['md', 'markdown'].includes(extension.value))
const isHtml = computed(() => ['html', 'htm'].includes(extension.value))
const isText = computed(() => ['txt', 'csv', 'json', 'xml', 'log'].includes(extension.value))
// Microsoft Online Preview 只支持新格式（docx/pptx/xlsx）
const isOfficeOnline = computed(() => ['docx', 'pptx', 'xlsx'].includes(extension.value))

// Office 在线预览 URL
const officePreviewUrl = computed(() => {
  if (!props.fileUrl) return ''
  return `https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(props.fileUrl)}`
})

// Markdown 渲染
const renderedMarkdown = computed(() => {
  if (!props.content) return ''
  return marked(props.content)
})

// 下载文件
function handleDownload() {
  if (props.fileUrl) {
    window.open(props.fileUrl, '_blank')
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
  color: #a1a1aa;
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
  background: #f9fafb;
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

/* Markdown 样式 */
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
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}

.preview-markdown :deep(pre) {
  background: #f5f5f5;
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
  color: #52525b;
}

.preview-markdown :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.preview-markdown :deep(th),
.preview-markdown :deep(td) {
  border: 1px solid #ebebeb;
  padding: 8px 12px;
  text-align: left;
}

.preview-markdown :deep(th) {
  background: #f9fafb;
  font-weight: 600;
}

.preview-text {
  flex: 1;
  background: #f5f5f5;
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
  color: #a1a1aa;
}

.preview-icon {
  font-size: 48px;
  color: #d4d4d8;
}

.preview-hint {
  font-size: 13px;
  color: #a1a1aa;
}

.btn-primary-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: #171717;
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
