<template>
  <div class="get-mindmap-result">
    <div v-if="isPlainText" class="gm-plain">
      <pre>{{ rawResult }}</pre>
    </div>
    <template v-else>
      <!-- header -->
      <div class="gm-header">
        <BranchesOutlined class="gm-header-icon" />
        <span>思维导图 — {{ data.knowledge_name }}</span>
        <button class="gm-detail-btn" @click="detailVisible = true">查看详情</button>
      </div>
      <!-- 思维导图预览 -->
      <div class="gm-content">
        <pre class="gm-preview">{{ previewText }}</pre>
      </div>
    </template>

    <a-modal v-model:open="detailVisible" :title="'思维导图 — ' + (data?.knowledge_name || '')" :footer="null" width="700px"
      :bodyStyle="{ maxHeight: '70vh', overflow: 'auto' }">
      <pre class="gm-detail-content">{{ fullText }}</pre>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { BranchesOutlined } from '@ant-design/icons-vue'

const props = defineProps({ event: { type: Object, required: true } })

const detailVisible = ref(false)
const rawResult = computed(() => props.event.result || '')
const data = computed(() => { try { return JSON.parse(rawResult.value) } catch { return null } })
const isPlainText = computed(() => !data.value)

const fullText = computed(() => {
  if (!data.value?.mindmap) return ''
  const mm = data.value.mindmap
  return typeof mm === 'string' ? mm : JSON.stringify(mm, null, 2)
})

const previewText = computed(() => {
  const t = fullText.value
  return t.length > 500 ? t.substring(0, 500) + '\n...' : t
})
</script>

<style lang="less" scoped>
.get-mindmap-result {
  border: 1px solid #fdba74;
  border-left: 3px solid #f97316;
  border-radius: 8px;
  overflow: hidden;
  background: #fff7ed;

  .gm-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .gm-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; border-bottom: 1px solid #fdba74;
    background: #ffedd5; font-size: 12px; font-weight: 600; color: #9a3412;
    .gm-header-icon { color: #ea580c; font-size: 14px; }
    .gm-detail-btn {
      margin-left: auto; appearance: none; border: 1px solid #fdba74;
      border-radius: 4px; background: #fff; color: #ea580c;
      font-size: 11px; padding: 2px 8px; cursor: pointer;
      &:hover { background: #ffedd5; }
    }
  }

  .gm-content {
    padding: 8px 10px;
    .gm-preview {
      margin: 0; font-size: 12px; line-height: 1.6;
      color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
      font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    }
  }
}

.gm-detail-content {
  margin: 0; padding: 0;
  font-family: 'Menlo', 'Monaco', 'Consolas', 'Courier New', monospace;
  font-size: 12px; line-height: 1.6; color: #1e293b;
  white-space: pre-wrap; word-break: break-word;
}
</style>
