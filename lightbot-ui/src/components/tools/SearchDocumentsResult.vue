<template>
  <div class="search-docs-result">
    <!-- 纯文本降级 -->
    <div v-if="isPlainText" class="sd-plain">
      <pre>{{ rawResult }}</pre>
    </div>

    <template v-else>
      <!-- 摘要 header（对齐 QA 卡片风格） -->
      <div class="sd-header">
        <FolderOutlined class="sd-header-icon" />
        <span>搜索文档 — 找到 {{ data.total }} 个匹配文档</span>
        <button class="sd-detail-btn" @click="detailVisible = true">查看详情</button>
      </div>

      <!-- 文档卡片列表（摘要） -->
      <div class="sd-docs">
        <div v-for="(doc, i) in data.documents?.slice(0, 3)" :key="i" class="sd-doc-card">
          <div class="sd-doc-index">{{ i + 1 }}</div>
          <div class="sd-doc-info">
            <div class="sd-doc-name">{{ doc.document_name }}</div>
            <div class="sd-doc-meta">
              <span class="sd-kb-tag">知识库: {{ doc.knowledge_name }}</span>
            </div>
          </div>
          <FileTextOutlined class="sd-doc-icon" />
        </div>
        <div v-if="(data.documents?.length || 0) > 3" class="sd-more-hint">
          还有 {{ data.documents.length - 3 }} 个文档，点击"查看详情"查看全部
        </div>
      </div>
    </template>

    <!-- 详情弹窗 -->
    <a-modal v-model:open="detailVisible" title="搜索文档结果" :footer="null" width="640px"
      :bodyStyle="{ maxHeight: '70vh', overflow: 'auto' }">
      <pre class="sd-detail-content">{{ formattedResult }}</pre>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { FolderOutlined, FileTextOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const detailVisible = ref(false)
const rawResult = computed(() => props.event.result || '')

const data = computed(() => {
  try { return JSON.parse(rawResult.value) } catch { return null }
})

const isPlainText = computed(() => !data.value)

const formattedResult = computed(() => {
  if (!data.value) return rawResult.value
  return JSON.stringify(data.value, null, 2)
})
</script>

<style lang="less" scoped>
.search-docs-result {
  border: 1px solid #93c5fd;
  border-left: 3px solid #3b82f6;
  border-radius: 8px;
  overflow: hidden;
  background: #eff6ff;

  .sd-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
    max-height: 300px; overflow-y: auto;
  }

  .sd-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; border-bottom: 1px solid #93c5fd;
    background: #dbeafe; font-size: 12px; font-weight: 600; color: #1e40af;
    .sd-header-icon { color: #2563eb; font-size: 14px; }
    .sd-detail-btn {
      margin-left: auto; appearance: none; border: 1px solid #93c5fd;
      border-radius: 4px; background: #fff; color: #2563eb;
      font-size: 11px; padding: 2px 8px; cursor: pointer;
      &:hover { background: #dbeafe; }
    }
  }

  .sd-docs { padding: 8px 10px; display: flex; flex-direction: column; gap: 4px; }

  .sd-doc-card {
    display: flex; align-items: center; gap: 8px;
    padding: 6px 8px; border: 1px solid #bfdbfe;
    border-radius: 6px; background: #fff;
    transition: border-color 0.2s;
    &:hover { border-color: #93c5fd; }
  }

  .sd-doc-index {
    font-size: 11px; color: #2563eb;
    background: #dbeafe; border-radius: 4px;
    padding: 0 5px; min-width: 20px; text-align: center; flex-shrink: 0;
  }

  .sd-doc-info {
    flex: 1; min-width: 0;
    .sd-doc-name {
      font-size: 12px; font-weight: 500; color: var(--gray-700);
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .sd-doc-meta {
      display: flex; align-items: center; gap: 8px; margin-top: 2px; font-size: 11px;
      .sd-kb-tag {
        color: #1e40af; background: #dbeafe;
        border-radius: 4px; padding: 0 5px;
      }
    }
  }

  .sd-doc-icon { color: #93c5fd; font-size: 14px; flex-shrink: 0; }

  .sd-more-hint {
    font-size: 11px; color: #6b7280; text-align: center;
    padding: 4px 0;
  }
}

.sd-detail-content {
  margin: 0; padding: 0;
  font-family: 'Menlo', 'Monaco', 'Consolas', 'Courier New', monospace;
  font-size: 12px; line-height: 1.6; color: #1e293b;
  white-space: pre-wrap; word-break: break-word;
}
</style>
