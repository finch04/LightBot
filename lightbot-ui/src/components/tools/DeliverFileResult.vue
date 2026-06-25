<template>
  <div class="deliver-file-result">
    <!-- 纯文本降级 -->
    <div v-if="isPlainText" class="dfr-plain">
      <pre>{{ displayText }}</pre>
    </div>

    <template v-else>
      <!-- 错误 -->
      <div v-if="data._error || (!data.success && !data.artifacts)" class="dfr-card dfr-card-error">
        <div class="dfr-header dfr-header-error">
          <CloseCircleOutlined class="dfr-header-icon" />
          <span>文件交付失败</span>
        </div>
        <pre class="dfr-error-msg">{{ data.message || data.error || '未知错误' }}</pre>
      </div>

      <!-- 成功：文件卡片列表 -->
      <div v-else class="dfr-artifacts">
        <div class="dfr-summary">
          <CheckCircleOutlined class="dfr-summary-icon" />
          <span>已交付 {{ data.total || data.artifacts?.length || 0 }} 个文件</span>
          <span v-if="data.errors?.length" class="dfr-summary-warn">
            （{{ data.errors.length }} 个失败）
          </span>
        </div>

        <div class="dfr-grid">
          <div v-for="(file, i) in data.artifacts" :key="i" class="dfr-file-card"
               @click="openPreview(file)">
            <!-- 图片缩略图 -->
            <div v-if="isImage(file.contentType)" class="dfr-thumb">
              <img :src="file.url" :alt="file.name" loading="lazy" @error="onThumbError" />
            </div>
            <!-- 文件图标 -->
            <div v-else class="dfr-icon-wrap">
              <component :is="getFileIcon(file.contentType, file.name)" class="dfr-file-icon" />
            </div>
            <!-- 文件信息 -->
            <div class="dfr-file-info">
              <div class="dfr-file-name" :title="file.name">{{ file.name }}</div>
              <div class="dfr-file-meta">{{ formatSize(file.size) }}</div>
            </div>
            <!-- 下载按钮 -->
            <a :href="file.url" target="_blank" rel="noopener" class="dfr-download"
               @click.stop title="下载">
              <DownloadOutlined />
            </a>
          </div>
        </div>

        <!-- 失败列表 -->
        <div v-if="data.errors?.length" class="dfr-errors">
          <div v-for="(err, i) in data.errors" :key="i" class="dfr-error-item">
            <WarningOutlined /> {{ err }}
          </div>
        </div>
      </div>
    </template>

    <!-- 图片预览弹窗 -->
    <a-modal v-model:open="previewVisible" :footer="null" :width="800" destroyOnClose
             :bodyStyle="{ padding: 0, textAlign: 'center', background: '#000' }">
      <img v-if="previewFile" :src="previewFile.url" :alt="previewFile.name"
           style="max-width:100%;max-height:75vh;object-fit:contain;" />
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, h } from 'vue'
import {
  CheckCircleOutlined, CloseCircleOutlined, DownloadOutlined,
  WarningOutlined, FileImageOutlined, FilePdfOutlined,
  FileExcelOutlined, FileWordOutlined, FilePptOutlined,
  FileZipOutlined, FileTextOutlined, FileOutlined,
  CodeOutlined
} from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const previewVisible = ref(false)
const previewFile = ref(null)

const rawResult = computed(() => props.event.result || '')

const data = computed(() => {
  try {
    return JSON.parse(rawResult.value)
  } catch {
    return null
  }
})

const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)

function isImage(contentType) {
  return contentType && contentType.startsWith('image/')
}

function getFileIcon(contentType, name) {
  if (!contentType) return FileOutlined
  if (contentType.includes('pdf')) return FilePdfOutlined
  if (contentType.includes('excel') || contentType.includes('spreadsheet') || name?.endsWith('.csv')) return FileExcelOutlined
  if (contentType.includes('word') || contentType.includes('document')) return FileWordOutlined
  if (contentType.includes('powerpoint') || contentType.includes('presentation')) return FilePptOutlined
  if (contentType.includes('zip') || contentType.includes('compressed')) return FileZipOutlined
  if (contentType.includes('json') || contentType.includes('javascript') || contentType.includes('html') || contentType.includes('xml')) return CodeOutlined
  if (contentType.startsWith('text/')) return FileTextOutlined
  if (contentType.startsWith('image/')) return FileImageOutlined
  return FileOutlined
}

function openPreview(file) {
  if (isImage(file.contentType)) {
    previewFile.value = file
    previewVisible.value = true
  } else {
    window.open(file.url, '_blank')
  }
}

function onThumbError(e) {
  e.target.style.display = 'none'
}

function formatSize(bytes) {
  if (!bytes || bytes === 0) return '未知'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<style lang="less" scoped>
.deliver-file-result {
  font-size: 12px;

  .dfr-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  // ── 卡片容器 ──
  .dfr-card {
    border: 1px solid #c4b5fd;
    border-left: 3px solid #8b5cf6;
    border-radius: 8px;
    overflow: hidden;
    background: var(--color-purple-bg);
  }
  .dfr-card-error {
    border-color: #fca5a5;
    border-left-color: #ef4444;
    background: var(--color-error-bg);
  }

  // ── Header ──
  .dfr-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px;
    background: var(--color-purple-bg); border-bottom: 1px solid #c4b5fd;
    font-size: 12px; font-weight: 600; color: #5b21b6;
  }
  .dfr-header-error {
    background: var(--color-error-bg); border-bottom-color: #fca5a5; color: #991b1b;
  }
  .dfr-header-icon {
    font-size: 14px; flex-shrink: 0;
    color: #7c3aed;
  }
  .dfr-header-error .dfr-header-icon { color: #dc2626; }

  // ── 错误内容 ──
  .dfr-error-msg {
    margin: 0; padding: 10px 12px;
    color: #b91c1c; font-size: 12px; line-height: 1.6;
    white-space: pre-wrap; word-break: break-word;
  }

  // ── 成功区域 ──
  .dfr-artifacts {
    border: 1px solid #c4b5fd;
    border-left: 3px solid #8b5cf6;
    border-radius: 8px;
    overflow: hidden;
    background: var(--color-purple-bg);
  }

  .dfr-summary {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 12px;
    background: var(--color-purple-bg); border-bottom: 1px solid #c4b5fd;
    font-size: 12px; font-weight: 600; color: #5b21b6;
  }
  .dfr-summary-icon { font-size: 14px; color: #16a34a; }
  .dfr-summary-warn { color: #d97706; font-weight: 400; }

  // ── 文件网格 ──
  .dfr-grid {
    display: flex; flex-direction: column; gap: 1px;
    background: #ddd6fe;
  }

  .dfr-file-card {
    display: flex; align-items: center; gap: 10px;
    padding: 10px 12px;
    background: var(--color-purple-bg);
    cursor: pointer;
    transition: background 0.15s;
    &:hover { background: var(--color-purple-bg); }
  }

  // ── 缩略图 ──
  .dfr-thumb {
    width: 40px; height: 40px; flex-shrink: 0;
    border-radius: 6px; overflow: hidden;
    border: 1px solid #ddd6fe;
    background: var(--color-canvas);
    display: flex; align-items: center; justify-content: center;
    img {
      width: 100%; height: 100%; object-fit: cover;
    }
  }

  // ── 图标 ──
  .dfr-icon-wrap {
    width: 40px; height: 40px; flex-shrink: 0;
    border-radius: 6px;
    border: 1px solid #ddd6fe;
    background: var(--color-canvas);
    display: flex; align-items: center; justify-content: center;
  }
  .dfr-file-icon {
    font-size: 20px; color: #7c3aed;
  }

  // ── 文件信息 ──
  .dfr-file-info {
    flex: 1; min-width: 0;
  }
  .dfr-file-name {
    font-size: 13px; font-weight: 500; color: #1e1b4b;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }
  .dfr-file-meta {
    font-size: 11px; color: #8b5cf6; margin-top: 2px;
  }

  // ── 下载按钮 ──
  .dfr-download {
    width: 32px; height: 32px; flex-shrink: 0;
    display: flex; align-items: center; justify-content: center;
    border-radius: 6px;
    color: #7c3aed; font-size: 14px;
    transition: all 0.15s;
    &:hover {
      background: var(--color-purple-bg);
      color: #5b21b6;
    }
  }

  // ── 错误列表 ──
  .dfr-errors {
    padding: 8px 12px;
    border-top: 1px dashed #fca5a5;
    background: var(--color-error-bg);
  }
  .dfr-error-item {
    font-size: 11px; color: #b91c1c; padding: 2px 0;
    display: flex; align-items: center; gap: 4px;
  }
}
</style>
