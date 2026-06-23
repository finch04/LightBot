<template>
  <div class="web-search-result">
    <div v-if="isPlainText" class="ws-plain">
      <pre>{{ displayText }}</pre>
    </div>
    <template v-else>
      <!-- AI 摘要 -->
      <div v-if="data.answer" class="ws-answer">
        <div class="ws-answer-header">
          <BulbOutlined class="ws-icon" />
          <span>AI 摘要</span>
        </div>
        <div class="ws-answer-content">{{ data.answer }}</div>
      </div>

      <!-- 搜索结果摘要栏 -->
      <div class="ws-summary">
        <div class="ws-summary-left">
          <SearchOutlined class="ws-icon" />
          <span class="ws-summary-text">
            搜索 <strong class="ws-query">「{{ data.query }}」</strong>
            共找到 <strong>{{ data.total }}</strong> 条结果
          </span>
        </div>
        <a-button v-if="data.results?.length" type="link" size="small" class="ws-detail-btn" @click="drawerVisible = true">
          <EyeOutlined /> 查看详情
        </a-button>
      </div>

      <!-- 结果列表（紧凑） -->
      <div v-if="data.results?.length" class="ws-items">
        <div v-for="(item, i) in data.results" :key="i" class="ws-item">
          <div class="ws-item-header">
            <span class="ws-item-index">{{ i + 1 }}</span>
            <a :href="item.url" target="_blank" rel="noopener" class="ws-item-title" @click.stop>
              {{ item.title }}
            </a>
            <span v-if="item.score != null" class="ws-item-score">
              {{ Math.round(item.score * 100) }}%
            </span>
          </div>
          <div class="ws-item-url">
            <LinkOutlined class="ws-url-icon" />
            <span>{{ extractDomain(item.url) }}</span>
          </div>
          <div class="ws-item-content">{{ getPreview(item.content, 120) }}</div>
        </div>
      </div>

      <div v-if="!data.answer && !data.results?.length" class="ws-empty">
        未找到相关结果
      </div>

      <!-- 详情弹窗：全部结果卡片 -->
      <a-drawer
        v-model:open="drawerVisible"
        :title="`搜索「${data.query}」— ${data.total} 条结果`"
        placement="right"
        :width="560"
        :bodyStyle="{ padding: '16px', background: 'var(--gray-25)' }"
      >
        <div class="detail-cards">
          <div v-for="(item, i) in data.results" :key="i" class="detail-card">
            <div class="detail-card-header">
              <span class="detail-card-index">{{ i + 1 }}</span>
              <a :href="item.url" target="_blank" rel="noopener" class="detail-card-title">
                {{ item.title }}
              </a>
              <span v-if="item.score != null" class="detail-card-score">
                相关度 {{ Math.round(item.score * 100) }}%
              </span>
            </div>
            <div class="detail-card-url">
              <LinkOutlined />
              <a :href="item.url" target="_blank" rel="noopener">{{ item.url }}</a>
            </div>
            <div class="detail-card-body">{{ item.content || '无摘要' }}</div>
            <template v-if="item.rawContent">
              <div class="detail-card-divider" />
              <div class="detail-card-raw-label">完整内容</div>
              <div class="detail-card-raw">{{ item.rawContent }}</div>
            </template>
          </div>
        </div>
      </a-drawer>
    </template>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { SearchOutlined, BulbOutlined, LinkOutlined, EyeOutlined } from '@ant-design/icons-vue'

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

function extractDomain(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}

const drawerVisible = ref(false)
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

  // AI 摘要
  .ws-answer {
    border: 1px solid #bfdbfe;
    border-radius: 8px;
    background: linear-gradient(135deg, #eff6ff 0%, #f0f9ff 100%);
    overflow: hidden;
    margin-bottom: 10px;

    .ws-answer-header {
      display: flex; align-items: center; gap: 6px;
      padding: 8px 12px; border-bottom: 1px solid #bfdbfe;
      font-size: 12px; font-weight: 600; color: #1e40af;
      background: rgba(255, 255, 255, 0.5);
    }

    .ws-answer-content {
      padding: 10px 12px; font-size: 13px; line-height: 1.7;
      color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
    }
  }

  // 摘要栏
  .ws-summary {
    display: flex; align-items: center; justify-content: space-between;
    padding: 6px 12px;
    background: linear-gradient(135deg, var(--gray-25) 0%, #f8fafc 100%);
    border: 1px solid var(--gray-150);
    border-radius: 8px;
    margin-bottom: 10px;

    .ws-summary-left {
      display: flex; align-items: center; gap: 8px;
    }

    .ws-summary-text {
      font-size: 12px; color: var(--gray-600);
      strong { color: var(--gray-800); }
    }

    .ws-query {
      color: var(--main-600); font-weight: 600;
    }

    .ws-detail-btn {
      font-size: 12px; padding: 0 4px; height: 24px;
      color: var(--main-600); flex-shrink: 0;
      &:hover { color: var(--main-500); }
    }
  }

  // 紧凑结果列表
  .ws-items { display: flex; flex-direction: column; gap: 6px; }

  .ws-item {
    border: 1px solid #dbeafe;
    border-radius: 8px;
    padding: 8px 12px;
    transition: all 0.2s;
    background: linear-gradient(135deg, #f0f7ff 0%, #f8faff 100%);
    &:hover {
      border-color: var(--main-300);
      box-shadow: 0 2px 8px rgba(24, 144, 255, 0.1);
    }
  }

  .ws-item-header {
    display: flex; align-items: center; gap: 8px; margin-bottom: 3px;

    .ws-item-index {
      font-size: 11px; font-weight: 700; color: #fff;
      background: var(--main-500); border-radius: 50%;
      width: 20px; height: 20px; display: flex;
      align-items: center; justify-content: center;
      flex-shrink: 0;
    }

    .ws-item-title {
      font-size: 13px; font-weight: 500; color: var(--main-700);
      text-decoration: none; flex: 1; min-width: 0;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
      &:hover { text-decoration: underline; color: var(--main-500); }
    }

    .ws-item-score {
      font-size: 11px; font-weight: 600; color: #52c41a;
      background: #f6ffed; border: 1px solid #b7eb8f;
      border-radius: 4px; padding: 0 6px; white-space: nowrap;
      flex-shrink: 0;
    }
  }

  .ws-item-url {
    display: flex; align-items: center; gap: 4px;
    font-size: 11px; color: var(--gray-400); margin-bottom: 4px;
    padding-left: 28px;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;

    .ws-url-icon { font-size: 10px; flex-shrink: 0; }
  }

  .ws-item-content {
    font-size: 12px; color: var(--gray-600); line-height: 1.5;
    padding-left: 28px;
  }

  .ws-empty {
    padding: 16px; text-align: center; font-size: 12px;
    color: var(--gray-500); border: 1px dashed var(--gray-200);
    border-radius: 8px; background: var(--gray-25);
  }
}

// 详情弹窗卡片
.detail-cards {
  display: flex; flex-direction: column; gap: 12px;
}

.detail-card {
  background: #fff;
  border: 1px solid var(--gray-150);
  border-radius: 10px;
  padding: 14px 16px;
  transition: box-shadow 0.2s;
  &:hover { box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08); }

  .detail-card-header {
    display: flex; align-items: center; gap: 8px; margin-bottom: 6px;

    .detail-card-index {
      font-size: 12px; font-weight: 700; color: #fff;
      background: var(--main-500); border-radius: 50%;
      width: 22px; height: 22px; display: flex;
      align-items: center; justify-content: center;
      flex-shrink: 0;
    }

    .detail-card-title {
      font-size: 14px; font-weight: 600; color: var(--main-700);
      text-decoration: none; flex: 1; min-width: 0;
      word-break: break-word;
      &:hover { text-decoration: underline; color: var(--main-500); }
    }

    .detail-card-score {
      font-size: 11px; font-weight: 500; color: #52c41a;
      background: #f6ffed; border: 1px solid #b7eb8f;
      border-radius: 4px; padding: 1px 8px; white-space: nowrap;
      flex-shrink: 0;
    }
  }

  .detail-card-url {
    display: flex; align-items: center; gap: 6px;
    font-size: 11px; color: var(--gray-400);
    margin-bottom: 8px; padding-left: 30px;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;

    a { color: var(--gray-400); text-decoration: none; &:hover { color: var(--main-500); } }
  }

  .detail-card-body {
    font-size: 13px; line-height: 1.7; color: var(--gray-700);
    white-space: pre-wrap; word-break: break-word;
  }

  .detail-card-divider {
    height: 1px; background: var(--gray-150); margin: 10px 0;
  }

  .detail-card-raw-label {
    font-size: 12px; color: var(--gray-500); font-weight: 500; margin-bottom: 6px;
  }

  .detail-card-raw {
    font-size: 12px; line-height: 1.6; color: var(--gray-600);
    white-space: pre-wrap; word-break: break-word;
    max-height: 400px; overflow-y: auto;
    padding: 10px; background: var(--gray-25);
    border: 1px solid var(--gray-150); border-radius: 6px;
  }
}
</style>
