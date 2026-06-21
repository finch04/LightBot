<template>
  <div class="search-docs-result">
    <!-- 纯文本降级（错误信息） -->
    <div v-if="isPlainText" class="sd-plain">
      <pre>{{ rawResult }}</pre>
    </div>

    <template v-else>
      <!-- 摘要 -->
      <div class="sd-summary">
        <FolderOutlined class="sd-summary-icon" />
        <span>找到 {{ data.total }} 个匹配文档</span>
      </div>

      <!-- 文档卡片列表 -->
      <div class="sd-docs">
        <div v-for="(doc, i) in data.documents" :key="i" class="sd-doc-card">
          <div class="sd-doc-index">{{ i + 1 }}</div>
          <div class="sd-doc-info">
            <div class="sd-doc-name">{{ doc.document_name }}</div>
            <div class="sd-doc-meta">
              <span class="sd-kb-tag">知识库: {{ doc.knowledge_name }}</span>
              <span class="sd-doc-id">ID: {{ doc.document_id }}</span>
            </div>
          </div>
          <FileTextOutlined class="sd-doc-icon" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { FolderOutlined, FileTextOutlined } from '@ant-design/icons-vue'

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
.search-docs-result {
  .sd-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
    max-height: 300px; overflow-y: auto;
  }

  .sd-summary {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; color: var(--gray-700);
    margin-bottom: 8px;
    .sd-summary-icon { color: var(--main-600); font-size: 13px; }
  }

  .sd-docs { display: flex; flex-direction: column; gap: 4px; }

  .sd-doc-card {
    display: flex; align-items: center; gap: 8px;
    padding: 8px 10px; border: 1px solid var(--gray-150);
    border-radius: 6px; transition: border-color 0.2s;
    &:hover { border-color: var(--gray-300); }
  }

  .sd-doc-index {
    font-size: 11px; color: var(--gray-700);
    background: var(--gray-100); border-radius: 4px;
    padding: 0 5px; min-width: 20px; text-align: center; flex-shrink: 0;
  }

  .sd-doc-info {
    flex: 1; min-width: 0;
    .sd-doc-name {
      font-size: 13px; font-weight: 500; color: var(--gray-700);
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .sd-doc-meta {
      display: flex; align-items: center; gap: 8px; margin-top: 2px; font-size: 11px;
      .sd-kb-tag {
        color: var(--main-700); background: var(--main-50);
        border-radius: 4px; padding: 0 5px;
      }
      .sd-doc-id { color: var(--gray-400); }
    }
  }

  .sd-doc-icon { color: var(--gray-400); font-size: 14px; flex-shrink: 0; }
}
</style>
