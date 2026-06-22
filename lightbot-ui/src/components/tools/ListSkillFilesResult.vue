<template>
  <div class="list-skill-files-result">
    <div v-if="isPlainText" class="lsf-plain">
      <pre>{{ rawResult }}</pre>
    </div>
    <template v-else>
      <div class="lsf-header">
        <FolderOpenOutlined class="lsf-icon" />
        <span class="lsf-slug">{{ data.slug }}</span>
        <span class="lsf-count">{{ data.total }} 个文件</span>
      </div>
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

const isPlainText = computed(() => !data.value)
</script>

<style lang="less" scoped>
.list-skill-files-result {
  .lsf-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .lsf-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; color: var(--gray-700);
    margin-bottom: 8px;
    .lsf-icon { color: var(--main-600); font-size: 13px; }
    .lsf-slug { font-weight: 600; font-family: monospace; }
    .lsf-count { color: var(--gray-500); margin-left: auto; }
  }

  .lsf-files { display: flex; flex-direction: column; gap: 2px; }

  .lsf-file {
    display: flex; align-items: center; gap: 6px;
    padding: 4px 10px; font-size: 12px;
    border-radius: 4px;
    &:hover { background: var(--gray-25); }
    .lsf-file-icon { color: var(--gray-400); font-size: 12px; }
    .lsf-file-name { color: var(--gray-700); font-family: monospace; }
  }
}
</style>
