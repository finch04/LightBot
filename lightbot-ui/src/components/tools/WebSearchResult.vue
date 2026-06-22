<template>
  <div class="web-search-result">
    <div v-if="isPlainText" class="ws-plain">
      <pre>{{ displayText }}</pre>
    </div>
    <template v-else>
      <!-- AI 摘要 -->
      <div v-if="data.answer" class="ws-answer">
        <div class="ws-answer-header">
          <GlobalOutlined class="ws-icon" />
          <span>AI 摘要</span>
        </div>
        <div class="ws-answer-content">{{ data.answer }}</div>
      </div>

      <!-- 搜索结果摘要 -->
      <div class="ws-summary">
        <GlobalOutlined class="ws-icon" />
        <span>找到 {{ data.total }} 条搜索结果</span>
      </div>

      <!-- 结果列表 -->
      <div v-if="data.results?.length" class="ws-items">
        <div v-for="(item, i) in data.results" :key="i" class="ws-item">
          <div class="ws-item-header">
            <span class="ws-item-index">{{ i + 1 }}</span>
            <a :href="item.url" target="_blank" rel="noopener" class="ws-item-title" @click.stop>
              {{ item.title }}
            </a>
          </div>
          <div class="ws-item-url">{{ item.url }}</div>
          <div class="ws-item-content">{{ getPreview(item.content, 200) }}</div>
        </div>
      </div>

      <div v-if="!data.answer && !data.results?.length" class="ws-empty">
        未找到相关结果
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { GlobalOutlined } from '@ant-design/icons-vue'

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

function getPreview(text, maxLen) {
  if (!text) return ''
  return text.length <= maxLen ? text : text.substring(0, maxLen) + '...'
}
</script>

<style lang="less" scoped>
.web-search-result {
  .ws-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
    max-height: 300px; overflow-y: auto;
  }

  .ws-icon { font-size: 13px; color: var(--main-600); }

  .ws-answer {
    border: 1px solid #bfdbfe;
    border-radius: 8px;
    background: #eff6ff;
    overflow: hidden;
    margin-bottom: 8px;

    .ws-answer-header {
      display: flex; align-items: center; gap: 6px;
      padding: 8px 10px; border-bottom: 1px solid #bfdbfe;
      font-size: 12px; font-weight: 600; color: #1e40af;
    }

    .ws-answer-content {
      padding: 10px; font-size: 13px; line-height: 1.6;
      color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
    }
  }

  .ws-summary {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; color: var(--gray-700);
    margin-bottom: 8px;
  }

  .ws-items { display: flex; flex-direction: column; gap: 6px; }

  .ws-item {
    border: 1px solid var(--gray-150);
    border-radius: 6px; padding: 8px 10px;
    transition: border-color 0.2s;
    &:hover { border-color: var(--gray-300); }
  }

  .ws-item-header {
    display: flex; align-items: center; gap: 6px; margin-bottom: 2px;

    .ws-item-index {
      font-size: 11px; color: var(--gray-700);
      background: var(--gray-100); border-radius: 4px;
      padding: 0 5px; min-width: 20px; text-align: center; flex-shrink: 0;
    }

    .ws-item-title {
      font-size: 13px; font-weight: 500; color: var(--main-700);
      text-decoration: none;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
      &:hover { text-decoration: underline; }
    }
  }

  .ws-item-url {
    font-size: 11px; color: var(--gray-400); margin-bottom: 4px;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }

  .ws-item-content {
    font-size: 12px; color: var(--gray-600); line-height: 1.5;
  }

  .ws-empty {
    padding: 10px; text-align: center; font-size: 12px;
    color: var(--gray-500); border: 1px dashed var(--gray-200); border-radius: 6px;
  }
}
</style>
