<template>
  <div class="find-in-doc-result">
    <!-- 纯文本降级 -->
    <div v-if="isPlainText" class="fid-plain">
      <pre>{{ rawResult }}</pre>
    </div>

    <template v-else>
      <!-- 原文翻页模式 -->
      <template v-if="data.mode === 'open'">
        <div class="fid-header">
          <FileTextOutlined class="fid-header-icon" />
          <span class="fid-header-title">{{ data.document_name }}</span>
          <span class="fid-header-info">共 {{ data.total_lines }} 行，第 {{ data.start_line }}-{{ data.end_line }} 行</span>
          <button class="fid-detail-btn" @click="detailVisible = true">查看详情</button>
        </div>
        <div class="fid-content">
          <div v-for="(line, i) in previewLines" :key="i" class="fid-line">
            <span class="fid-line-num">{{ data.start_line + i }}</span>
            <span class="fid-line-text">{{ line }}</span>
          </div>
          <div v-if="contentLines.length > 10" class="fid-more-hint">
            还有 {{ contentLines.length - 10 }} 行，点击"查看详情"查看全部
          </div>
        </div>
      </template>

      <!-- 关键词搜索模式 -->
      <template v-if="data.mode === 'search'">
        <div class="fid-header">
          <FileSearchOutlined class="fid-header-icon" />
          <span>文档搜索 — {{ data.total_matches }} 处匹配，{{ data.documents.length }} 个文档</span>
          <button class="fid-detail-btn" @click="detailVisible = true">查看详情</button>
        </div>
        <div class="fid-summary">
          <div v-for="(doc, di) in data.documents?.slice(0, 2)" :key="di" class="fid-doc-card">
            <div class="fid-doc-name">{{ doc.document_name }}</div>
            <div class="fid-doc-meta">
              <span class="fid-match-tag">{{ doc.match_count }} 处匹配</span>
            </div>
          </div>
          <div v-if="(data.documents?.length || 0) > 2" class="fid-more-hint">
            还有 {{ data.documents.length - 2 }} 个文档，点击"查看详情"查看全部
          </div>
        </div>
      </template>
    </template>

    <!-- 详情弹窗 -->
    <a-modal v-model:open="detailVisible" title="文档查找结果" :footer="null" width="700px"
      :bodyStyle="{ maxHeight: '70vh', overflow: 'auto' }">
      <pre class="fid-detail-content">{{ formattedResult }}</pre>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { FileTextOutlined, FileSearchOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const detailVisible = ref(false)
const rawResult = computed(() => props.event.result || '')

const data = computed(() => {
  try { return JSON.parse(rawResult.value) } catch { return null }
})

const isPlainText = computed(() => !data.value)

const contentLines = computed(() => {
  if (!data.value?.content) return []
  return data.value.content.split('\n').filter(l => l.length > 0)
})

const previewLines = computed(() => contentLines.value.slice(0, 10))

const formattedResult = computed(() => {
  if (!data.value) return rawResult.value
  return JSON.stringify(data.value, null, 2)
})
</script>

<style lang="less" scoped>
.find-in-doc-result {
  border: 1px solid #86efac;
  border-left: 3px solid #22c55e;
  border-radius: 8px;
  overflow: hidden;
  background: #f0fdf4;

  .fid-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
    max-height: 300px; overflow-y: auto;
  }

  .fid-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; border-bottom: 1px solid #86efac;
    background: #dcfce7; font-size: 12px; font-weight: 600; color: #166534;
    .fid-header-icon { color: #16a34a; font-size: 14px; }
    .fid-header-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .fid-header-info { color: #4ade80; font-weight: 400; white-space: nowrap; }
    .fid-detail-btn {
      margin-left: 8px; appearance: none; border: 1px solid #86efac;
      border-radius: 4px; background: #fff; color: #16a34a;
      font-size: 11px; padding: 2px 8px; cursor: pointer; white-space: nowrap;
      &:hover { background: #dcfce7; }
    }
  }

  .fid-content { padding: 6px 0; max-height: 240px; overflow-y: auto; }

  .fid-line {
    display: flex; gap: 8px; padding: 1px 10px;
    font-size: 12px; line-height: 1.6;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    &:hover { background: #dcfce7; }
    .fid-line-num {
      color: #86efac; min-width: 32px; text-align: right;
      user-select: none; flex-shrink: 0;
    }
    .fid-line-text { color: var(--gray-700); white-space: pre-wrap; word-break: break-word; }
  }

  .fid-summary { padding: 8px 10px; display: flex; flex-direction: column; gap: 4px; }

  .fid-doc-card {
    padding: 6px 8px; border: 1px solid #bbf7d0;
    border-radius: 6px; background: #fff;
    .fid-doc-name {
      font-size: 12px; font-weight: 500; color: var(--gray-700);
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .fid-doc-meta {
      margin-top: 2px;
      .fid-match-tag {
        font-size: 11px; color: #166534; background: #dcfce7;
        border-radius: 4px; padding: 0 5px;
      }
    }
  }

  .fid-more-hint {
    font-size: 11px; color: #6b7280; text-align: center;
    padding: 4px 0;
  }
}

.fid-detail-content {
  margin: 0; padding: 0;
  font-family: 'Menlo', 'Monaco', 'Consolas', 'Courier New', monospace;
  font-size: 12px; line-height: 1.6; color: #1e293b;
  white-space: pre-wrap; word-break: break-word;
}
</style>
