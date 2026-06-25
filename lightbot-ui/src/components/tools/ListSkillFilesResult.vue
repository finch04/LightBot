<template>
  <div class="list-skill-files-result">
    <div v-if="isPlainText" class="lsf-plain">
      <pre>{{ displayText }}</pre>
    </div>
    <template v-else>
      <!-- header -->
      <div class="lsf-header">
        <FolderOpenOutlined class="lsf-icon" />
        <span class="lsf-slug">{{ data.slug }}</span>
        <span class="lsf-count">{{ data.total }} 个文件</span>
      </div>
      <!-- 文件列表 -->
      <div class="lsf-files">
        <div v-for="(file, i) in data.files" :key="i" class="lsf-file">
          <FileTextOutlined class="lsf-file-icon" />
          <span class="lsf-file-name">{{ file }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { FolderOpenOutlined, FileTextOutlined } from '@ant-design/icons-vue'

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
</script>

<style lang="less" scoped>
.list-skill-files-result {
  border: 1px solid #c4b5fd;
  border-left: 3px solid #8b5cf6;
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-purple-bg);

  .lsf-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .lsf-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; border-bottom: 1px solid #c4b5fd;
    background: var(--color-purple-bg); font-size: 12px; font-weight: 600; color: #5b21b6;
    .lsf-icon { color: #7c3aed; font-size: 14px; }
    .lsf-slug { font-family: monospace; }
    .lsf-count {
      margin-left: auto; font-size: 11px; color: #6d28d9;
      background: var(--color-purple-bg); border: 1px solid #c4b5fd;
      border-radius: 4px; padding: 0 6px;
    }
  }

  .lsf-files { padding: 8px 10px; display: flex; flex-direction: column; gap: 2px; }

  .lsf-file {
    display: flex; align-items: center; gap: 6px;
    padding: 5px 8px; font-size: 12px;
    border: 1px solid #ddd6fe; border-radius: 6px;
    background: var(--color-canvas);
    &:hover { border-color: #c4b5fd; }
    .lsf-file-icon { color: #7c3aed; font-size: 12px; }
    .lsf-file-name { color: var(--gray-700); font-family: monospace; }
  }
}
</style>
