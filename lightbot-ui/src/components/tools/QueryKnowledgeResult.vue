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
        <button class="qk-detail-btn" @click="detailVisible = true">查看详情</button>
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
          <!-- 头部 -->
          <div class="qk-item-header">
            <span class="qk-item-type-badge" :class="item.result_type">
              <QuestionCircleOutlined v-if="item.result_type === 'qa_pair'" />
              <FileTextOutlined v-else />
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

          <!-- 内容 -->
          <div v-if="expandedItems.has(i)" class="qk-item-content">
            {{ item.result_type === 'qa_pair' ? item.answer : item.content }}
          </div>
          <div v-else class="qk-item-preview">
            {{ getPreview(item.result_type === 'qa_pair' ? item.answer : item.content, 100) }}
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

    <!-- 详情弹窗 -->
    <a-modal v-model:open="detailVisible" title="知识库查询结果" :footer="null" width="680px"
      :bodyStyle="{ maxHeight: '70vh', overflow: 'auto' }">
      <pre class="qk-detail-content">{{ formattedResult }}</pre>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { SearchOutlined, QuestionCircleOutlined, RightOutlined, FileTextOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const displayLimit = ref(5)
const expandedItems = ref(new Set())
const detailVisible = ref(false)

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
const formattedResult = computed(() => data.value ? JSON.stringify(data.value, null, 2) : rawResult.value)
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
    .qk-detail-btn {
      margin-left: auto; appearance: none; border: 1px solid var(--main-200);
      border-radius: 4px; background: #fff; color: var(--main-600);
      font-size: 11px; padding: 2px 8px; cursor: pointer;
      &:hover { background: var(--main-50); }
    }
  }

  // QA 优先命中卡片（粉紫色系，保持原样）
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

  // 结果卡片列表
  .qk-items { display: flex; flex-direction: column; gap: 6px; }

  // 通用卡片样式（对齐 QA 卡片风格）
  .qk-item {
    border: 1px solid var(--gray-150);
    border-radius: 8px;
    overflow: hidden;
    cursor: pointer;
    transition: border-color 0.2s, box-shadow 0.2s;

    &:hover {
      border-color: var(--gray-250);
      box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
    }

    // 文档卡片 — 蓝色系
    &.chunk {
      border-left: 3px solid var(--main-400);

      .qk-item-type-badge.chunk {
        background: var(--main-50);
        color: var(--main-700);
      }
    }

    // 问答对卡片 — 粉紫色系（与 QA 优先命中同色系）
    &.qa_pair {
      border-left: 3px solid #db2777;

      .qk-item-type-badge.qa_pair {
        background: #fce7f3;
        color: #9d174d;
      }
    }
  }

  // 卡片头部
  .qk-item-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 10px;
    background: var(--gray-25);
    transition: background 0.15s;

    .qk-item:hover & { background: var(--gray-50); }
  }

  .qk-item-type-badge {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 11px;
    padding: 2px 8px;
    border-radius: 4px;
    white-space: nowrap;
    font-weight: 500;
    flex-shrink: 0;
  }

  .qk-item-label {
    flex: 1;
    font-size: 12px;
    font-weight: 500;
    color: var(--gray-700);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .qk-item-score {
    font-size: 11px;
    font-weight: 600;
    color: var(--gray-700);
    background: var(--gray-25);
    border: 1px solid var(--gray-100);
    border-radius: 4px;
    padding: 1px 6px;
    white-space: nowrap;
    flex-shrink: 0;
  }

  .qk-item-expand {
    font-size: 10px;
    color: var(--gray-400);
    transition: transform 0.2s;
    flex-shrink: 0;
    &.expanded { transform: rotate(90deg); }
  }

  // 内容预览
  .qk-item-preview {
    padding: 6px 12px;
    font-size: 12px;
    color: var(--gray-500);
    line-height: 1.5;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  // 展开内容
  .qk-item-content {
    padding: 10px 12px;
    font-size: 13px;
    line-height: 1.7;
    color: var(--gray-700);
    white-space: pre-wrap;
    word-break: break-word;
    border-top: 1px solid var(--gray-100);
  }

  // 查看更多
  .qk-more {
    margin-top: 4px;
    text-align: center;
    .qk-more-btn {
      appearance: none; border: 1px dashed var(--gray-200);
      border-radius: 6px; background: transparent; color: var(--gray-500);
      font-size: 12px; padding: 6px 12px; cursor: pointer; width: 100%;
      transition: all 0.15s;
      &:hover { background: var(--gray-25); color: var(--gray-700); }
    }
  }

  .qk-empty {
    padding: 10px; text-align: center; font-size: 12px;
    color: var(--gray-500); border: 1px dashed var(--gray-200); border-radius: 6px;
  }
}

.qk-detail-content {
  margin: 0; padding: 0;
  font-family: 'Menlo', 'Monaco', 'Consolas', 'Courier New', monospace;
  font-size: 12px; line-height: 1.6; color: #1e293b;
  white-space: pre-wrap; word-break: break-word;
}
</style>
