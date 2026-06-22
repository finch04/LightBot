<template>
  <div class="open-kb-doc-result">
    <div v-if="isPlainText" class="okd-plain">
      <pre>{{ rawResult }}</pre>
    </div>
    <template v-else>
      <!-- header -->
      <div class="okd-header">
        <FileTextOutlined class="okd-header-icon" />
        <span class="okd-header-title">{{ data.document_name }}</span>
        <span class="okd-header-lines">共 {{ totalChars }} 字</span>
        <button class="okd-detail-btn" @click="detailVisible = true">查看详情</button>
      </div>
      <!-- 截断预览 -->
      <div class="okd-content">
        <pre class="okd-preview-text">{{ previewText }}</pre>
        <div v-if="isTruncated" class="okd-more-hint">
          已截断显示，点击"查看详情"查看完整内容
        </div>
      </div>
    </template>

    <!-- 详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      :title="data?.document_name || '文档内容'"
      :footer="null"
      width="700px"
      :bodyStyle="{ maxHeight: '70vh', overflow: 'auto' }"
      :maskClosable="false"
    >
      <div class="okd-detail-meta">
        <span>共 {{ contentLines.length }} 行，{{ totalChars }} 字</span>
      </div>
      <div class="okd-detail-lines">
        <div v-for="(line, i) in contentLines" :key="i" class="okd-line">
          <span class="okd-line-num">{{ i + 1 }}</span>
          <span class="okd-line-text">{{ line }}</span>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { FileTextOutlined } from '@ant-design/icons-vue'

const PREVIEW_MAX_CHARS = 500

const props = defineProps({ event: { type: Object, required: true } })

const detailVisible = ref(false)
const rawResult = computed(() => props.event.result || '')
const data = computed(() => { try { return JSON.parse(rawResult.value) } catch { return null } })
const isPlainText = computed(() => !data.value)

const fullContent = computed(() => data.value?.content || '')
const totalChars = computed(() => fullContent.value.length)

const contentLines = computed(() => fullContent.value.split('\n'))

const isTruncated = computed(() => fullContent.value.length > PREVIEW_MAX_CHARS)

const previewText = computed(() => {
  if (!isTruncated.value) return fullContent.value
  return fullContent.value.slice(0, PREVIEW_MAX_CHARS)
})
</script>

<style lang="less" scoped>
.open-kb-doc-result {
  border: 1px solid #a5b4fc;
  border-left: 3px solid #6366f1;
  border-radius: 8px;
  overflow: hidden;
  background: #eef2ff;

  .okd-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .okd-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; border-bottom: 1px solid #a5b4fc;
    background: #e0e7ff; font-size: 12px; font-weight: 600; color: #3730a3;
    .okd-header-icon { color: #4f46e5; font-size: 14px; }
    .okd-header-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .okd-header-lines { color: #818cf8; font-weight: 400; white-space: nowrap; }
    .okd-detail-btn {
      margin-left: 8px; appearance: none; border: 1px solid #a5b4fc;
      border-radius: 4px; background: #fff; color: #4f46e5;
      font-size: 11px; padding: 2px 8px; cursor: pointer; white-space: nowrap;
      &:hover { background: #e0e7ff; }
    }
  }

  .okd-content {
    padding: 6px 10px;
  }

  .okd-preview-text {
    margin: 0;
    font-size: 12px;
    line-height: 1.6;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    color: var(--gray-700);
    white-space: pre-wrap;
    word-break: break-word;
  }

  .okd-more-hint {
    font-size: 11px; color: #6b7280; text-align: center; padding: 4px 0;
  }
}

.okd-detail-meta {
  font-size: 12px;
  color: #818cf8;
  margin-bottom: 10px;
}

.okd-detail-lines {
  .okd-line {
    display: flex; gap: 8px; padding: 1px 10px;
    font-size: 12px; line-height: 1.6;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    &:hover { background: #f5f3ff; }
    .okd-line-num {
      color: #a5b4fc; min-width: 40px; text-align: right;
      user-select: none; flex-shrink: 0;
    }
    .okd-line-text { color: var(--gray-700); white-space: pre-wrap; word-break: break-word; }
  }
}
</style>
