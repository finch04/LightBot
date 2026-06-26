<template>
  <div class="qkr">
    <!-- 纯文本降级（错误信息） -->
    <div v-if="isPlainText" class="qkr-plain">
      <pre>{{ displayText }}</pre>
    </div>

    <template v-else>
      <!-- QA 优先命中 -->
      <div v-if="data.qa_answer" class="qkr-qa-card">
        <div class="qkr-qa-header">
          <QuestionCircleOutlined class="qkr-qa-icon" />
          <span>命中高匹配问答对</span>
        </div>
        <div class="qkr-qa-body">{{ data.qa_answer }}</div>
      </div>

      <!-- 有结果：摘要 + 卡片列表 -->
      <div v-if="data.results?.length" class="qkr-results">
        <div class="qkr-summary-bar">
          <SearchOutlined class="qkr-summary-icon" />
          <span>找到 {{ data.total }} 条相关内容</span>
          <button class="qkr-detail-btn" @click="detailVisible = true">
            <EyeOutlined />
            <span>查看详情</span>
          </button>
        </div>

        <div class="qkr-card-list">
          <div
            v-for="(item, i) in visibleItems"
            :key="i"
            class="qkr-card"
            @click="openItemDetail(item)"
          >
            <div class="qkr-card-header">
              <span v-if="item.result_type === 'qa_pair'" class="qkr-type-tag">
                <QuestionCircleOutlined /> 问答对
              </span>
              <span v-else class="qkr-type-tag">
                <FileTextOutlined /> 文档
              </span>
              <span class="qkr-card-name">
                {{ item.result_type === 'qa_pair' ? item.question : item.document_name }}
              </span>
              <span v-if="typeof item.score === 'number'" class="qkr-score-badge">
                {{ (item.score * 100).toFixed(0) }}%
              </span>
            </div>
            <div class="qkr-card-preview">
              {{ getPreview(getItemContent(item), 120) }}
            </div>
          </div>

          <div v-if="data.results.length > displayLimit" class="qkr-more-wrap">
            <button class="qkr-more-btn" @click="displayLimit = data.results.length">
              查看剩余 {{ data.results.length - displayLimit }} 条结果
            </button>
          </div>
        </div>
      </div>

      <!-- 无结果 -->
      <div v-if="!data.qa_answer && !data.results?.length" class="qkr-empty">
        <SearchOutlined class="qkr-empty-icon" />
        <span>未在知识库中找到与问题相关的内容</span>
      </div>
    </template>

    <!-- 单条详情弹窗 -->
    <a-modal
      v-model:open="itemDetailVisible"
      :title="itemDetailTitle"
      :footer="null"
      width="680px"
      :bodyStyle="{ maxHeight: '70vh', overflow: 'auto' }"
    >
      <div v-if="activeItem" class="qkr-detail-item">
        <div class="qkr-detail-item-head">
          <span v-if="activeItem.result_type === 'qa_pair'" class="qkr-type-tag-lg">
            <QuestionCircleOutlined /> 问答对
          </span>
          <span v-else class="qkr-type-tag-lg">
            <FileTextOutlined /> 文档片段
          </span>
          <span v-if="typeof activeItem.score === 'number'" class="qkr-score-pill">
            匹配度 {{ (activeItem.score * 100).toFixed(1) }}%
          </span>
          <span v-if="activeItem.document_name" class="qkr-doc-name-inline">
            <FileTextOutlined /> {{ activeItem.document_name }}
          </span>
        </div>
        <template v-if="activeItem.result_type === 'qa_pair'">
          <div class="qkr-detail-section">
            <div class="qkr-detail-label">问题</div>
            <div class="qkr-detail-text">{{ activeItem.question }}</div>
          </div>
          <div class="qkr-detail-section">
            <div class="qkr-detail-label">答案</div>
            <div class="qkr-detail-content-box">{{ activeItem.answer }}</div>
          </div>
        </template>
        <template v-else>
          <div class="qkr-detail-section">
            <div class="qkr-detail-label">内容</div>
            <div class="qkr-detail-content-box">{{ activeItem.content }}</div>
          </div>
        </template>
      </div>
    </a-modal>

    <!-- 全量详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      title="知识库查询结果详情"
      :footer="null"
      width="800px"
      :bodyStyle="{ maxHeight: '75vh', overflow: 'auto', padding: '20px' }"
    >
      <div class="qkr-full-detail">
        <!-- 统计栏 -->
        <div class="qkr-stats-bar">
          <div class="qkr-stat-item">
            <span class="qkr-stat-value">{{ data.total || 0 }}</span>
            <span class="qkr-stat-label">总结果数</span>
          </div>
          <div class="qkr-stat-item">
            <span class="qkr-stat-value">{{ chunkCount }}</span>
            <span class="qkr-stat-label">文档片段</span>
          </div>
          <div class="qkr-stat-item">
            <span class="qkr-stat-value">{{ qaCount }}</span>
            <span class="qkr-stat-label">问答对</span>
          </div>
        </div>

        <!-- QA 高匹配 -->
        <div v-if="data.qa_answer" class="qkr-full-qa">
          <div class="qkr-full-section-title">
            <QuestionCircleOutlined />
            <span>高匹配问答对</span>
          </div>
          <div class="qkr-qa-card">
            <div class="qkr-qa-body qkr-qa-body-lg">{{ data.qa_answer }}</div>
          </div>
        </div>

        <!-- 详细结果 -->
        <div v-if="data.results?.length" class="qkr-full-results">
          <div class="qkr-full-section-title">
            <FileTextOutlined />
            <span>详细结果</span>
          </div>
          <div class="qkr-full-card-list">
            <div v-for="(item, i) in data.results" :key="i" class="qkr-full-card">
              <div class="qkr-full-card-header">
                <span v-if="item.result_type === 'qa_pair'" class="qkr-type-tag-lg">
                  <QuestionCircleOutlined /> 问答对
                </span>
                <span v-else class="qkr-type-tag-lg">
                  <FileTextOutlined /> 文档片段
                </span>
                <span v-if="typeof item.score === 'number'" class="qkr-score-pill">
                  匹配度 {{ (item.score * 100).toFixed(1) }}%
                </span>
              </div>
              <div class="qkr-full-card-body">
                <template v-if="item.result_type === 'qa_pair'">
                  <div class="qkr-detail-section">
                    <div class="qkr-detail-label">问题</div>
                    <div class="qkr-detail-text">{{ item.question }}</div>
                  </div>
                  <div class="qkr-detail-section">
                    <div class="qkr-detail-label">答案</div>
                    <div class="qkr-detail-content-box">{{ item.answer }}</div>
                  </div>
                </template>
                <template v-else>
                  <div class="qkr-detail-section">
                    <div class="qkr-detail-label">文档</div>
                    <div class="qkr-detail-text">{{ item.document_name }}</div>
                  </div>
                  <div class="qkr-detail-section">
                    <div class="qkr-detail-label">内容</div>
                    <div class="qkr-detail-content-box">{{ item.content }}</div>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import {
  SearchOutlined,
  QuestionCircleOutlined,
  FileTextOutlined,
  EyeOutlined
} from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const displayLimit = ref(5)
const detailVisible = ref(false)
const itemDetailVisible = ref(false)
const activeItem = ref(null)

const rawResult = computed(() => props.event.result || '')
const data = computed(() => {
  try { return JSON.parse(rawResult.value) } catch { return null }
})
const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)
const visibleItems = computed(() => (data.value?.results || []).slice(0, displayLimit.value))
const chunkCount = computed(() => (data.value?.results || []).filter(r => r.result_type === 'chunk').length)
const qaCount = computed(() => (data.value?.results || []).filter(r => r.result_type === 'qa_pair').length)

const itemDetailTitle = computed(() => {
  if (!activeItem.value) return ''
  if (activeItem.value.result_type === 'qa_pair') return '问答对详情'
  return activeItem.value.document_name || '文档片段详情'
})

function getItemContent(item) {
  return item.result_type === 'qa_pair' ? (item.answer || item.content) : item.content
}

function getPreview(text, maxLen) {
  if (!text) return ''
  return text.length <= maxLen ? text : text.substring(0, maxLen) + '...'
}

function openItemDetail(item) {
  activeItem.value = item
  itemDetailVisible.value = true
}
</script>

<style scoped>
.qkr { font-size: 13px; }

/* 纯文本降级 */
.qkr-plain {
  margin: 0; padding: 8px 10px;
  background: var(--color-canvas-soft); border-radius: 6px;
  font-size: 12px; line-height: 1.5; color: var(--gray-700);
  white-space: pre-wrap; word-break: break-word; max-height: 300px; overflow-y: auto;
}
.qkr-plain pre { margin: 0; }

/* QA 高匹配卡片 */
.qkr-qa-card {
  border: 1px solid var(--blue-300); border-left: 3px solid var(--blue-500);
  border-radius: 8px; background: var(--blue-50); overflow: hidden; margin-bottom: 8px;
}
.qkr-qa-header {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 10px; border-bottom: 1px solid var(--blue-200);
  background: var(--blue-100); font-size: 12px; font-weight: 600; color: var(--blue-700);
}
.qkr-qa-icon { color: var(--blue-500); }
.qkr-qa-body {
  padding: 10px; font-size: 13px; line-height: 1.6; color: var(--color-ink);
  white-space: pre-wrap; word-break: break-word;
}
.qkr-qa-body-lg { font-size: 14px; line-height: 1.8; }

/* 结果区域 */
.qkr-results { margin-bottom: 8px; }
.qkr-summary-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 12px; background: var(--blue-100);
  border: 1px solid var(--blue-200); border-radius: 8px;
  font-size: 13px; font-weight: 500; color: var(--blue-700); margin-bottom: 10px;
}
.qkr-summary-icon { color: var(--blue-500); font-size: 14px; }
.qkr-detail-btn {
  margin-left: auto; appearance: none; border: none; border-radius: 6px;
  background: var(--blue-500); color: #fff; font-size: 12px;
  padding: 5px 14px; cursor: pointer; display: inline-flex;
  align-items: center; gap: 6px; font-weight: 500;
}
.qkr-detail-btn:hover { background: var(--blue-600); }

/* 结果卡片 */
.qkr-card-list { display: flex; flex-direction: column; gap: 6px; }
.qkr-card {
  border: 1px solid var(--blue-200); border-left: 3px solid var(--blue-500);
  border-radius: 8px; overflow: hidden; cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.qkr-card:hover { border-color: var(--blue-400); box-shadow: 0 2px 8px rgba(37, 99, 235, 0.1); }
.qkr-card-header {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; background: var(--blue-50);
}
.qkr-type-tag {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 11px; padding: 2px 8px; border-radius: 4px;
  white-space: nowrap; font-weight: 500; flex-shrink: 0;
  background: var(--blue-100); color: var(--blue-700);
}
.qkr-card-name {
  flex: 1; font-size: 12px; font-weight: 500; color: var(--gray-700);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.qkr-score-badge {
  font-size: 11px; font-weight: 600; color: var(--blue-700);
  background: var(--blue-50); border: 1px solid var(--blue-200);
  border-radius: 4px; padding: 1px 6px; white-space: nowrap; flex-shrink: 0;
}
.qkr-card-preview {
  padding: 6px 12px; font-size: 12px; color: var(--color-body);
  line-height: 1.5; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* 更多按钮 */
.qkr-more-wrap { margin-top: 4px; text-align: center; }
.qkr-more-btn {
  appearance: none; border: 1px dashed var(--color-hairline-strong);
  border-radius: 6px; background: transparent; color: var(--color-body);
  font-size: 12px; padding: 6px 12px; cursor: pointer; width: 100%;
}
.qkr-more-btn:hover { border-color: var(--blue-400); color: var(--blue-500); }

/* 空状态 */
.qkr-empty {
  display: flex; align-items: center; gap: 8px; justify-content: center;
  padding: 16px 12px; font-size: 13px; color: var(--color-body);
  border: 1px solid var(--color-hairline-strong); border-radius: 8px;
  background: var(--color-canvas-soft);
}
.qkr-empty-icon { color: var(--color-mute); font-size: 16px; }

/* 单条详情弹窗 */
.qkr-detail-item-head {
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 16px; padding-bottom: 12px;
  border-bottom: 2px solid var(--blue-100);
}
.qkr-type-tag-lg {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 12px; padding: 4px 10px; border-radius: 4px; font-weight: 500;
  background: var(--blue-100); color: var(--blue-700);
}
.qkr-score-pill {
  font-size: 12px; font-weight: 600; color: var(--blue-700);
  background: var(--blue-100); padding: 3px 10px; border-radius: 12px;
}
.qkr-doc-name-inline {
  font-size: 12px; color: var(--color-body);
  display: inline-flex; align-items: center; gap: 4px;
}
.qkr-detail-section { margin-bottom: 16px; }
.qkr-detail-label {
  font-size: 12px; font-weight: 600; color: var(--blue-600);
  margin-bottom: 6px; text-transform: uppercase; letter-spacing: 0.5px;
}
.qkr-detail-text { font-size: 14px; line-height: 1.7; color: var(--color-ink); }
.qkr-detail-content-box {
  font-size: 14px; line-height: 1.7; color: var(--color-ink);
  white-space: pre-wrap; word-break: break-word;
  padding: 12px; background: var(--color-canvas-soft);
  border: 2px solid var(--blue-200); border-radius: 6px;
  max-height: 400px; overflow-y: auto;
}

/* 全量详情弹窗 */
.qkr-stats-bar {
  display: flex; gap: 16px; margin-bottom: 20px; padding: 16px;
  background: var(--blue-50); border-radius: 8px; border: 1px solid var(--blue-200);
}
.qkr-stat-item {
  display: flex; flex-direction: column; align-items: center; gap: 4px; flex: 1;
}
.qkr-stat-value { font-size: 24px; font-weight: 700; color: var(--blue-500); }
.qkr-stat-label { font-size: 12px; color: var(--blue-400); }

.qkr-full-qa { margin-bottom: 20px; }
.qkr-full-section-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 14px; font-weight: 600; color: var(--blue-700);
  margin-bottom: 12px; padding-bottom: 8px; border-bottom: 2px solid var(--blue-200);
}
.qkr-full-card-list { display: flex; flex-direction: column; gap: 14px; }
.qkr-full-card {
  border: 2px solid var(--blue-200); border-left: 4px solid var(--blue-500);
  border-radius: 10px; overflow: hidden; background: var(--color-canvas);
}
.qkr-full-card-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; background: var(--blue-50);
  border-bottom: 1px solid var(--blue-100);
}
.qkr-full-card-body { padding: 16px; }
</style>
