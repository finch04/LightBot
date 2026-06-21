<template>
  <div class="query-knowledge-result">
    <!-- 纯文本降级（错误信息、无结果提示） -->
    <div v-if="isPlainText" class="qk-plain">
      <pre>{{ rawResult }}</pre>
    </div>

    <template v-else>
      <!-- QA 优先命中 -->
      <div v-if="data.qa_answer" class="qk-qa-answer">
        <div class="qk-qa-header">
          <QuestionCircleOutlined class="qk-qa-icon" />
          <span>命中高匹配问答对</span>
        </div>
        <div class="qk-qa-content">{{ data.qa_answer }}</div>
      </div>

      <!-- 摘要 -->
      <div v-if="data.results?.length" class="qk-summary">
        <SearchOutlined class="qk-summary-icon" />
        <span>找到 {{ data.total }} 条相关内容</span>
      </div>

      <!-- 结果列表 -->
      <div v-if="data.results?.length" class="qk-items">
        <div
          v-for="(item, i) in visibleItems"
          :key="i"
          class="qk-item"
          :class="item.result_type"
          @click="toggleItem(i)"
        >
          <div class="qk-item-header">
            <span class="qk-item-index">{{ i + 1 }}</span>
            <span class="qk-item-type-badge" :class="item.result_type">
              {{ item.result_type === 'qa_pair' ? '问答对' : '文档' }}
            </span>
            <span class="qk-item-label">
              {{ item.result_type === 'qa_pair' ? item.question : item.document_name }}
            </span>
            <span v-if="typeof item.score === 'number'" class="qk-item-score">
              {{ (item.score * 100).toFixed(0) }}%
            </span>
            <RightOutlined class="qk-item-expand" :class="{ expanded: expandedItems.has(i) }" />
          </div>
          <div v-if="expandedItems.has(i)" class="qk-item-content">
            {{ item.result_type === 'qa_pair' ? item.answer : item.content }}
          </div>
          <div v-else class="qk-item-preview">
            {{ getPreview(item.result_type === 'qa_pair' ? item.answer : item.content, 120) }}
          </div>
        </div>

        <!-- 查看更多 -->
        <div v-if="data.results.length > displayLimit" class="qk-more">
          <button class="qk-more-btn" @click="displayLimit = data.results.length">
            查看剩余 {{ data.results.length - displayLimit }} 条结果
          </button>
        </div>
      </div>

      <!-- 无结果 -->
      <div v-if="!data.qa_answer && !data.results?.length" class="qk-empty">
        未在知识库中找到与问题相关的内容
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { SearchOutlined, QuestionCircleOutlined, RightOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const displayLimit = ref(5)
const expandedItems = ref(new Set())

function toggleItem(index) {
  const s = new Set(expandedItems.value)
  if (s.has(index)) s.delete(index)
  else s.add(index)
  expandedItems.value = s
}

function getPreview(text, maxLen) {
  if (!text) return ''
  return text.length <= maxLen ? text : text.substring(0, maxLen) + '...'
}

const rawResult = computed(() => props.event.result || '')

const data = computed(() => {
  try {
    return JSON.parse(rawResult.value)
  } catch {
    return null
  }
})

const isPlainText = computed(() => !data.value)
const visibleItems = computed(() => (data.value?.results || []).slice(0, displayLimit.value))
</script>

<style lang="less" scoped>
.query-knowledge-result {
  .qk-plain pre {
    margin: 0;
    padding: 8px 10px;
    background: var(--gray-25);
    border-radius: 6px;
    font-size: 12px;
    line-height: 1.5;
    color: var(--gray-700);
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 300px;
    overflow-y: auto;
  }

  .qk-summary {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 10px;
    background: var(--gray-25);
    border-radius: 6px;
    font-size: 12px;
    color: var(--gray-700);
    margin-bottom: 8px;

    .qk-summary-icon { color: var(--main-600); font-size: 13px; }
  }

  .qk-qa-answer {
    border: 1px solid #f9a8d4;
    border-radius: 8px;
    background: #fdf2f8;
    overflow: hidden;
    margin-bottom: 8px;

    .qk-qa-header {
      display: flex; align-items: center; gap: 6px;
      padding: 8px 10px; border-bottom: 1px solid #f9a8d4;
      font-size: 12px; font-weight: 600; color: #9d174d;
      .qk-qa-icon { color: #db2777; }
    }

    .qk-qa-content {
      padding: 10px; font-size: 13px; line-height: 1.6;
      color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
    }
  }

  .qk-items { display: flex; flex-direction: column; gap: 4px; }

  .qk-item {
    border: 1px solid var(--gray-150);
    border-radius: 6px; overflow: hidden; cursor: pointer;
    transition: border-color 0.2s;
    &:hover { border-color: var(--gray-300); }
    &.qa_pair { border-left: 3px solid #db2777; }
    &.chunk { border-left: 3px solid var(--main-500); }
  }

  .qk-item-header {
    display: flex; align-items: center; gap: 6px;
    padding: 6px 10px; background: var(--gray-25);

    .qk-item-index {
      font-size: 11px; color: var(--gray-700);
      background: var(--gray-100); border-radius: 4px;
      padding: 0 5px; min-width: 20px; text-align: center;
    }

    .qk-item-type-badge {
      font-size: 10px; padding: 0 5px; border-radius: 4px; white-space: nowrap;
      &.qa_pair { background: #fce7f3; color: #9d174d; }
      &.chunk { background: var(--main-50); color: var(--main-700); }
    }

    .qk-item-label {
      flex: 1; font-size: 12px; font-weight: 500; color: var(--gray-700);
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }

    .qk-item-score {
      font-size: 11px; color: var(--gray-700);
      background: var(--gray-25); border: 1px solid var(--gray-100);
      border-radius: 4px; padding: 0 5px; white-space: nowrap;
    }

    .qk-item-expand {
      font-size: 10px; color: var(--gray-400); transition: transform 0.2s;
      &.expanded { transform: rotate(90deg); }
    }
  }

  .qk-item-preview {
    padding: 6px 10px; font-size: 12px; color: var(--gray-500);
    line-height: 1.5; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }

  .qk-item-content {
    padding: 8px 10px; font-size: 12px; color: var(--gray-700);
    line-height: 1.6; white-space: pre-wrap; word-break: break-word;
    max-height: 200px; overflow-y: auto; border-top: 1px solid var(--gray-100);
  }

  .qk-more {
    margin-top: 4px; text-align: center;
    .qk-more-btn {
      appearance: none; border: 1px dashed var(--gray-200);
      border-radius: 6px; background: transparent; color: var(--gray-500);
      font-size: 12px; padding: 6px 12px; cursor: pointer; width: 100%;
      &:hover { background: var(--gray-25); color: var(--gray-700); }
    }
  }

  .qk-empty {
    padding: 10px; text-align: center; font-size: 12px;
    color: var(--gray-500); border: 1px dashed var(--gray-200); border-radius: 6px;
  }
}
</style>
