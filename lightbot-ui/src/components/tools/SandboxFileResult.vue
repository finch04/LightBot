<template>
  <div class="sandbox-file-result">
    <!-- 纯文本降级 -->
    <div v-if="isPlainText" class="sfr-plain">
      <pre>{{ displayText }}</pre>
    </div>

    <template v-else>
      <!-- 错误 -->
      <div v-if="data.success === false || data.error" class="sfr-card sfr-card-error">
        <div class="sfr-header sfr-header-error">
          <CloseCircleOutlined class="sfr-header-icon" />
          <span>执行失败</span>
        </div>
        <pre class="sfr-error-msg">{{ data.error || '未知错误' }}</pre>
      </div>

      <!-- read_file: 文件内容 -->
      <div v-else-if="data.content != null" class="sfr-card">
        <div class="sfr-header">
          <FileTextOutlined class="sfr-header-icon" />
          <span class="sfr-path">{{ data.path }}</span>
          <span v-if="data.size != null" class="sfr-meta">{{ formatSize(data.size) }}</span>
          <button class="sfr-detail-btn" @click="showModal = true">
            <EyeOutlined /> 查看详情
          </button>
        </div>
        <pre class="sfr-content-preview">{{ previewContent(data.content) }}</pre>
        <div v-if="data.content.length > PREVIEW_MAX" class="sfr-truncated-hint">
          ... 内容已截断，点击"查看详情"查看完整内容
        </div>

        <a-modal v-model:open="showModal" title="文件内容" :footer="null" :width="680"
          :bodyStyle="{ maxHeight: '75vh', overflow: 'auto', padding: '20px' }" destroyOnClose>
          <div style="display:flex;align-items:center;gap:24px;padding:12px 16px;margin-bottom:20px;background:#f5f3ff;border:1px solid #ddd6fe;border-radius:8px;">
            <div style="display:flex;align-items:center;gap:8px;">
              <span style="font-size:12px;color:#8b5cf6;white-space:nowrap;">文件路径</span>
              <span style="font-size:13px;font-weight:500;color:#6d28d9;font-family:'Monaco','Menlo',monospace;word-break:break-all;">{{ data.path }}</span>
            </div>
            <div v-if="data.size != null" style="display:flex;align-items:center;gap:8px;margin-left:auto;">
              <span style="font-size:12px;color:#8b5cf6;white-space:nowrap;">文件大小</span>
              <span style="font-size:14px;font-weight:700;color:#6d28d9;">{{ formatSize(data.size) }}</span>
            </div>
          </div>
          <pre style="margin:0;padding:16px;background:#1e1e1e;color:#d4d4d4;font-size:13px;line-height:1.7;white-space:pre-wrap;word-break:break-word;border-radius:8px;font-family:'Monaco','Menlo',monospace;">{{ data.content }}</pre>
        </a-modal>
      </div>

      <!-- list_files: 文件列表 -->
      <div v-else-if="data.files != null" class="sfr-card">
        <div class="sfr-header">
          <FolderOpenOutlined class="sfr-header-icon" />
          <span class="sfr-path">{{ data.dirPath }}</span>
          <span class="sfr-meta">{{ data.total }} 个文件</span>
          <button v-if="data.files.length > LIST_PREVIEW_MAX" class="sfr-detail-btn" @click="showModal = true">
            <EyeOutlined /> 查看全部
          </button>
        </div>
        <div v-if="data.files.length === 0" class="sfr-empty">目录为空</div>
        <div v-else class="sfr-file-list">
          <div v-for="(file, i) in previewFiles" :key="i" class="sfr-file-item">
            <FileTextOutlined class="sfr-file-icon" />
            <span class="sfr-file-name">{{ file }}</span>
          </div>
          <div v-if="data.files.length > LIST_PREVIEW_MAX" class="sfr-more">
            ... 还有 {{ data.files.length - LIST_PREVIEW_MAX }} 个文件
          </div>
        </div>

        <a-modal v-model:open="showModal" title="文件列表" :footer="null" :width="720"
          :bodyStyle="{ maxHeight: '75vh', overflow: 'auto', padding: '20px' }" destroyOnClose>
          <div style="display:flex;align-items:center;gap:24px;padding:12px 16px;margin-bottom:20px;background:#f5f3ff;border:1px solid #ddd6fe;border-radius:8px;">
            <div style="display:flex;align-items:center;gap:8px;">
              <FolderOpenOutlined style="font-size:14px;color:#7c3aed;flex-shrink:0;" />
              <span style="font-size:12px;color:#8b5cf6;white-space:nowrap;">目录路径</span>
              <span style="font-size:13px;font-weight:500;color:#6d28d9;font-family:'Monaco','Menlo',monospace;word-break:break-all;">{{ data.dirPath }}</span>
            </div>
            <div style="display:flex;align-items:center;gap:8px;margin-left:auto;">
              <FileTextOutlined style="font-size:14px;color:#7c3aed;flex-shrink:0;" />
              <span style="font-size:12px;color:#8b5cf6;white-space:nowrap;">文件总数</span>
              <span style="font-size:14px;font-weight:700;color:#6d28d9;">{{ data.total }}</span>
            </div>
          </div>
          <div style="border:1px solid #ddd6fe;border-radius:8px;background:#fff;">
            <div v-for="(file, i) in data.files" :key="i" style="display:flex;align-items:center;gap:6px;padding:4px 12px;color:#374151;">
              <FileTextOutlined style="font-size:12px;color:#a78bfa;flex-shrink:0;" />
              <span style="font-size:12px;font-family:'Monaco','Menlo',monospace;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">{{ file }}</span>
            </div>
          </div>
        </a-modal>
      </div>

      <!-- write_file: 写入结果 -->
      <div v-else-if="data.success === true" class="sfr-card">
        <div class="sfr-header">
          <CheckCircleOutlined class="sfr-header-icon success" />
          <span class="sfr-path">{{ data.path }}</span>
          <span class="sfr-badge success">写入成功</span>
        </div>
        <div v-if="data.size != null" class="sfr-write-info">{{ formatSize(data.size) }} 已写入</div>
      </div>

      <!-- 兜底 -->
      <div v-else class="sfr-card">
        <pre class="sfr-json">{{ formattedJson }}</pre>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import {
  FileTextOutlined, FolderOpenOutlined, CheckCircleOutlined,
  CloseCircleOutlined, EyeOutlined
} from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const PREVIEW_MAX = 500
const LIST_PREVIEW_MAX = 5

const showModal = ref(false)

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

const formattedJson = computed(() => {
  if (!data.value) return rawResult.value
  try { return JSON.stringify(data.value, null, 2) } catch { return rawResult.value }
})

const previewFiles = computed(() => {
  if (!data.value?.files) return []
  return data.value.files.slice(0, LIST_PREVIEW_MAX)
})

function previewContent(content) {
  if (!content) return ''
  return content.length > PREVIEW_MAX ? content.substring(0, PREVIEW_MAX) + '...' : content
}

function formatSize(n) {
  if (n < 1024) return n + ' 字符'
  return (n / 1024).toFixed(1) + ' KB'
}
</script>

<style lang="less" scoped>
.sandbox-file-result {
  font-size: 12px;

  .sfr-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  // ── 卡片容器 ──
  .sfr-card {
    border: 1px solid #c4b5fd;
    border-left: 3px solid #8b5cf6;
    border-radius: 8px;
    overflow: hidden;
    background: var(--color-purple-bg);
  }
  .sfr-card-error {
    border-color: #fca5a5;
    border-left-color: #ef4444;
    background: var(--color-error-bg);
  }

  // ── Header ──
  .sfr-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px;
    background: var(--color-purple-bg); border-bottom: 1px solid #c4b5fd;
    font-size: 12px; font-weight: 600; color: #5b21b6;
  }
  .sfr-header-error {
    background: var(--color-error-bg); border-bottom-color: #fca5a5; color: #991b1b;
  }
  .sfr-header-icon {
    font-size: 14px; color: #7c3aed; flex-shrink: 0;
    &.success { color: #16a34a; }
  }
  .sfr-header-error .sfr-header-icon { color: #dc2626; }

  .sfr-path {
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 11px; color: #6d28d9;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    flex: 1; min-width: 0;
  }
  .sfr-meta {
    font-size: 11px; color: #8b5cf6; flex-shrink: 0;
    font-weight: 400;
  }
  .sfr-badge {
    font-size: 11px; font-weight: 600; padding: 1px 8px; border-radius: 4px; flex-shrink: 0;
    &.success { color: #16a34a; background: var(--color-success-bg); }
  }
  .sfr-detail-btn {
    appearance: none;
    border: 1px solid #c4b5fd;
    border-radius: 6px;
    background: var(--color-canvas);
    color: #7c3aed;
    display: inline-flex; align-items: center; gap: 4px;
    font-size: 12px; cursor: pointer;
    padding: 6px 12px; border-radius: 6px; flex-shrink: 0;
    font-weight: 500;
    transition: all 0.2s ease;
    &:hover {
      background: var(--color-purple-bg);
      transform: translateY(-1px);
      box-shadow: 0 2px 6px rgba(139, 92, 246, 0.15);
    }
    &:active { transform: translateY(0); }
  }

  // ── 错误内容 ──
  .sfr-error-msg {
    margin: 0; padding: 10px 12px;
    color: #b91c1c; font-size: 12px; line-height: 1.6;
    white-space: pre-wrap; word-break: break-word;
  }

  // ── read_file 预览 ──
  .sfr-content-preview {
    margin: 0; padding: 10px 12px;
    background: var(--color-purple-bg); color: var(--gray-700);
    font-size: 12px; line-height: 1.6;
    white-space: pre-wrap; word-break: break-word;
    max-height: 200px; overflow-y: auto;
    font-family: 'Monaco', 'Menlo', monospace;
  }
  .sfr-truncated-hint {
    padding: 6px 12px; font-size: 11px; color: #8b5cf6;
    text-align: center;
    border-top: 1px solid #ddd6fe;
    background: var(--color-purple-bg);
  }

  // ── list_files ──
  .sfr-empty {
    padding: 16px 12px; text-align: center;
    color: #a78bfa; font-style: italic;
  }
  .sfr-file-list { padding: 4px 0; }
  .sfr-file-item {
    display: flex; align-items: center; gap: 6px;
    padding: 4px 12px; color: var(--gray-700);
    &:hover { background: var(--color-purple-bg); }
  }
  .sfr-file-icon { font-size: 12px; color: #a78bfa; flex-shrink: 0; }
  .sfr-file-name {
    font-size: 12px; font-family: 'Monaco', 'Menlo', monospace;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }
  .sfr-more {
    padding: 6px 12px; font-size: 11px; color: #8b5cf6;
    text-align: center; border-top: 1px dashed #ddd6fe;
    background: var(--color-purple-bg);
  }

  // ── write_file ──
  .sfr-write-info {
    padding: 8px 12px; color: #6d28d9; font-size: 12px;
  }

  // ── 兜底 JSON ──
  .sfr-json {
    margin: 0; padding: 10px 12px;
    background: #1e1e1e; color: #d4d4d4;
    font-size: 12px; line-height: 1.5;
    white-space: pre-wrap; word-break: break-word;
    font-family: 'Monaco', 'Menlo', monospace;
  }

}
</style>
