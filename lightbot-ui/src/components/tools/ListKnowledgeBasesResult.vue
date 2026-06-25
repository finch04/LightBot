<template>
  <div class="list-kb-result">
    <div v-if="isPlainText" class="lkb-plain">
      <pre>{{ displayText }}</pre>
    </div>
    <template v-else>
      <!-- header -->
      <div class="lkb-header">
        <DatabaseOutlined class="lkb-header-icon" />
        <span>知识库列表 — 共 {{ data.total }} 个</span>
        <button v-if="(data.knowledge_bases?.length || 0) > 3" class="lkb-detail-btn" @click="detailVisible = true">查看详情</button>
      </div>
      <!-- 知识库卡片 -->
      <div class="lkb-items">
        <div v-for="(kb, i) in data.knowledge_bases?.slice(0, 3)" :key="i" class="lkb-item">
          <div class="lkb-item-row">
            <DatabaseOutlined class="lkb-item-icon" />
            <span class="lkb-item-name">{{ kb.name }}</span>
            <span class="lkb-item-stat">文档 {{ kb.document_count }}</span>
            <span class="lkb-item-stat">Token {{ formatNumber(kb.total_tokens) }}</span>
          </div>
          <div v-if="kb.description" class="lkb-item-desc">{{ kb.description }}</div>
        </div>
        <div v-if="(data.knowledge_bases?.length || 0) > 3" class="lkb-more-hint">
          还有 {{ data.knowledge_bases.length - 3 }} 个知识库
        </div>
      </div>
    </template>

    <a-modal v-model:open="detailVisible" title="知识库列表" :footer="null" width="640px"
      :bodyStyle="{ maxHeight: '70vh', overflow: 'auto' }">
      <pre class="lkb-detail-content">{{ formattedResult }}</pre>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { DatabaseOutlined } from '@ant-design/icons-vue'

const props = defineProps({ event: { type: Object, required: true } })

const detailVisible = ref(false)
const rawResult = computed(() => props.event.result || '')
const data = computed(() => { try { return JSON.parse(rawResult.value) } catch { return null } })
const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)
const formattedResult = computed(() => data.value ? JSON.stringify(data.value, null, 2) : rawResult.value)

function formatNumber(n) {
  if (n == null) return '0'
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return String(n)
}
</script>

<style lang="less" scoped>
.list-kb-result {
  border: 1px solid #c4b5fd;
  border-left: 3px solid #8b5cf6;
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-purple-bg);

  .lkb-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .lkb-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; border-bottom: 1px solid #c4b5fd;
    background: var(--color-purple-bg); font-size: 12px; font-weight: 600; color: #5b21b6;
    .lkb-header-icon { color: #7c3aed; font-size: 14px; }
    .lkb-detail-btn {
      margin-left: auto; appearance: none; border: 1px solid #c4b5fd;
      border-radius: 4px; background: var(--color-canvas); color: #7c3aed;
      font-size: 11px; padding: 2px 8px; cursor: pointer;
      &:hover { background: var(--color-purple-bg); }
    }
  }

  .lkb-items { padding: 8px 10px; display: flex; flex-direction: column; gap: 4px; }

  .lkb-item {
    border: 1px solid #ddd6fe; border-radius: 6px;
    background: var(--color-canvas); overflow: hidden;
    &:hover { border-color: #c4b5fd; }
  }

  .lkb-item-row {
    display: flex; align-items: center; gap: 6px;
    padding: 6px 8px;
    .lkb-item-icon { color: #7c3aed; font-size: 12px; }
    .lkb-item-name { font-size: 12px; font-weight: 500; color: var(--gray-700); flex: 1; }
    .lkb-item-stat {
      font-size: 11px; color: #6d28d9; background: var(--color-purple-bg);
      border-radius: 4px; padding: 0 5px; white-space: nowrap;
    }
  }

  .lkb-item-desc {
    padding: 4px 8px; font-size: 11px; color: var(--gray-500);
    line-height: 1.4; border-top: 1px solid #f3f0ff;
  }

  .lkb-more-hint {
    font-size: 11px; color: var(--color-mute); text-align: center; padding: 4px 0;
  }
}

.lkb-detail-content {
  margin: 0; padding: 0;
  font-family: 'Menlo', 'Monaco', 'Consolas', 'Courier New', monospace;
  font-size: 12px; line-height: 1.6; color: var(--color-text-code);
  white-space: pre-wrap; word-break: break-word;
}
</style>
