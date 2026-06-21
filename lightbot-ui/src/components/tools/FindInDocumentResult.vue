<template>
  <div class="find-in-doc-result">
    <!-- 纯文本降级（错误信息） -->
    <div v-if="isPlainText" class="fid-plain">
      <pre>{{ rawResult }}</pre>
    </div>

    <template v-else>
      <!-- 原文翻页模式 -->
      <div v-if="data.mode === 'open'" class="fid-open-mode">
        <div class="fid-open-header">
          <FileTextOutlined class="fid-icon" />
          <span class="fid-doc-name">{{ data.document_name }}</span>
          <span class="fid-line-info">
            共 {{ data.total_lines }} 行，当前第 {{ data.start_line }}-{{ data.end_line }} 行
          </span>
        </div>
        <div class="fid-content">
          <div
            v-for="(line, i) in contentLines"
            :key="i"
            class="fid-line"
          >
            <span class="fid-line-num">{{ data.start_line + i }}</span>
            <span class="fid-line-text">{{ line }}</span>
          </div>
        </div>
        <div v-if="data.has_more" class="fid-more-hint">
          还有后续内容，可使用 offset={{ data.next_offset }} 继续读取
        </div>
      </div>

      <!-- 关键词搜索模式 -->
      <div v-if="data.mode === 'search'">
        <div class="fid-search-summary">
          <FileSearchOutlined class="fid-icon" />
          <span>共 {{ data.total_matches }} 处匹配，来自 {{ data.documents.length }} 个文档</span>
        </div>

        <div v-for="(doc, di) in data.documents" :key="di" class="fid-doc-block">
          <div class="fid-doc-header">
            <FileSearchOutlined class="fid-icon" />
            <span class="fid-doc-name">{{ doc.document_name }}</span>
            <span class="fid-match-count">{{ doc.match_count }} 处匹配</span>
          </div>
          <div class="fid-matches">
            <div v-for="(match, mi) in doc.matches" :key="mi" class="fid-match">
              <div class="fid-match-line-num">第 {{ match.line_num }} 行</div>
              <div class="fid-match-context">
                <div
                  v-for="(line, li) in match.context_lines"
                  :key="li"
                  class="fid-context-line"
                  :class="{ 'is-matched': line.matched }"
                >
                  <span class="fid-line-prefix">{{ line.matched ? '>> ' : '   ' }}</span>
                  <span class="fid-line-text">{{ line.text }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { FileTextOutlined, FileSearchOutlined } from '@ant-design/icons-vue'

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

const contentLines = computed(() => {
  if (!data.value?.content) return []
  return data.value.content.split('\n').filter(l => l.length > 0)
})
</script>

<style lang="less" scoped>
.find-in-doc-result {
  .fid-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
    max-height: 300px; overflow-y: auto;
  }

  .fid-icon { font-size: 13px; color: var(--main-600); }

  .fid-search-summary {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; color: var(--gray-700);
    margin-bottom: 8px;
  }

  // 原文翻页模式
  .fid-open-mode {
    border: 1px solid var(--gray-150); border-radius: 8px; overflow: hidden;
  }

  .fid-open-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; background: var(--gray-25);
    border-bottom: 1px solid var(--gray-100); font-size: 12px;
    .fid-doc-name { font-weight: 600; color: var(--gray-700); }
    .fid-line-info { color: var(--gray-500); margin-left: auto; }
  }

  .fid-content { max-height: 360px; overflow-y: auto; }

  .fid-line {
    display: flex; gap: 8px; padding: 1px 10px;
    font-size: 12px; line-height: 1.6;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    &:hover { background: var(--gray-25); }
    .fid-line-num {
      color: var(--gray-400); min-width: 32px; text-align: right;
      user-select: none; flex-shrink: 0;
    }
    .fid-line-text {
      color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
    }
  }

  .fid-more-hint {
    padding: 6px 10px; font-size: 11px; color: var(--gray-500);
    background: var(--gray-25); border-top: 1px solid var(--gray-100);
    text-align: center;
  }

  // 关键词搜索模式
  .fid-doc-block {
    border: 1px solid var(--gray-150); border-radius: 8px;
    overflow: hidden; margin-bottom: 8px;
    &:last-child { margin-bottom: 0; }
  }

  .fid-doc-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; background: var(--gray-25);
    border-bottom: 1px solid var(--gray-100); font-size: 12px;
    .fid-doc-name {
      font-weight: 600; color: var(--gray-700); flex: 1;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .fid-match-count {
      font-size: 11px; color: var(--main-700);
      background: var(--main-50); border-radius: 4px;
      padding: 0 5px; white-space: nowrap;
    }
  }

  .fid-matches {
    .fid-match {
      border-bottom: 1px solid var(--gray-100);
      &:last-child { border-bottom: none; }
    }

    .fid-match-line-num {
      padding: 4px 10px; font-size: 11px; font-weight: 600;
      color: var(--gray-600); background: var(--gray-10);
    }

    .fid-context-line {
      display: flex; padding: 1px 10px;
      font-size: 12px; line-height: 1.6;
      font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
      &.is-matched {
        background: var(--main-50); color: var(--main-800); font-weight: 500;
      }
      .fid-line-prefix { color: var(--gray-400); user-select: none; flex-shrink: 0; }
      .fid-line-text { color: var(--gray-700); white-space: pre-wrap; word-break: break-word; }
    }
  }
}
</style>
