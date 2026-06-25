<template>
  <div class="sandbox-file-result">
    <div v-if="isPlainText" class="sfr-plain">
      <pre>{{ displayText }}</pre>
    </div>
    <template v-else>
      <!-- read_file: 显示文件内容 -->
      <div v-if="data.content != null" class="sfr-content">
        <div class="sfr-header">
          <span class="sfr-path">{{ data.path }}</span>
          <span v-if="data.size != null" class="sfr-size">{{ data.size }} 字符</span>
        </div>
        <pre class="sfr-file-content">{{ data.content }}</pre>
      </div>

      <!-- list_files: 显示文件列表 -->
      <div v-else-if="data.files != null" class="sfr-list">
        <div class="sfr-header">
          <span class="sfr-path">{{ data.dirPath }}</span>
          <span class="sfr-count">{{ data.total }} 个文件</span>
        </div>
        <div v-if="data.files.length === 0" class="sfr-empty">目录为空</div>
        <div v-else class="sfr-file-list">
          <div v-for="(file, i) in data.files" :key="i" class="sfr-file-item">
            <FileTextOutlined /> {{ file }}
          </div>
        </div>
      </div>

      <!-- write_file: 显示写入结果 -->
      <div v-else-if="data.success != null" class="sfr-write">
        <div class="sfr-header">
          <span class="sfr-path">{{ data.path }}</span>
          <span v-if="data.success" class="sfr-badge success">写入成功</span>
          <span v-else class="sfr-badge error">写入失败</span>
        </div>
        <div v-if="data.size != null" class="sfr-write-info">{{ data.size }} 字符已写入</div>
      </div>

      <!-- 错误 -->
      <div v-else-if="data.error" class="sfr-error">
        <pre>{{ data.error }}</pre>
      </div>

      <!-- 兜底 -->
      <div v-else class="sfr-json">
        <pre>{{ formattedJson }}</pre>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { FileTextOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true }
})

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
</script>

<style lang="less" scoped>
.sandbox-file-result {
  font-size: 12px;

  .sfr-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .sfr-header {
    display: flex; align-items: center; gap: 8px;
    padding: 6px 10px; background: var(--gray-25);
    border-bottom: 1px solid var(--gray-100);
  }

  .sfr-path {
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 11px; color: var(--gray-600);
    flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }

  .sfr-size, .sfr-count {
    font-size: 11px; color: var(--gray-400); flex-shrink: 0;
  }

  .sfr-badge {
    font-size: 11px; font-weight: 600; padding: 1px 6px;
    border-radius: 4px;
    &.success { color: #16a34a; background: #dcfce7; }
    &.error { color: #dc2626; background: #fee2e2; }
  }

  .sfr-file-content {
    margin: 0; padding: 8px 10px;
    background: var(--gray-25); color: var(--gray-700);
    font-size: 12px; line-height: 1.5;
    white-space: pre-wrap; word-break: break-word;
    max-height: 400px; overflow-y: auto;
  }

  .sfr-empty {
    padding: 12px 10px; text-align: center;
    color: var(--gray-400); font-style: italic;
  }

  .sfr-file-list {
    padding: 4px 0;
  }

  .sfr-file-item {
    display: flex; align-items: center; gap: 6px;
    padding: 4px 10px; color: var(--gray-700);
    font-size: 12px;
    &:hover { background: var(--gray-50); }
  }

  .sfr-write-info {
    padding: 6px 10px; color: var(--gray-500); font-size: 12px;
  }

  .sfr-error pre {
    margin: 0; padding: 8px 10px;
    background: #fef2f2; color: #dc2626;
    font-size: 12px; line-height: 1.5;
    white-space: pre-wrap; word-break: break-word;
  }

  .sfr-json pre {
    margin: 0; padding: 8px 10px;
    background: #1e1e1e; color: #d4d4d4;
    font-size: 12px; line-height: 1.5;
    white-space: pre-wrap; word-break: break-word;
  }
}
</style>
