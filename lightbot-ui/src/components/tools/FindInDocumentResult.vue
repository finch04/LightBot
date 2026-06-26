<template>
  <div class="find-in-doc-result">
    <!-- 纯文本降级 -->
    <div v-if="isPlainText" class="fid-plain">
      <pre>{{ displayText }}</pre>
    </div>

    <template v-else>
      <!-- 原文翻页模式 -->
      <template v-if="data.mode === 'open'">
        <div class="fid-header">
          <FileTextOutlined class="fid-header-icon" />
          <span class="fid-header-title">{{ data.document_name }}</span>
          <span class="fid-header-info">共 {{ data.total_lines }} 行，第 {{ data.start_line }}-{{ data.end_line }} 行</span>
          <button class="fid-detail-btn" @click="detailVisible = true">
            <EyeOutlined />
            <span>查看详情</span>
          </button>
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
          <button class="fid-detail-btn fid-detail-btn-right" @click="detailVisible = true">
            <EyeOutlined />
            <span>查看详情</span>
          </button>
        </div>
        <div class="fid-summary">
          <div v-for="(doc, di) in data.documents" :key="di" class="fid-doc-card">
            <div class="fid-doc-row">
              <span class="fid-doc-name">{{ doc.document_name }}</span>
              <span class="fid-match-tag">{{ doc.match_count }} 处匹配</span>
            </div>
          </div>
        </div>
      </template>
    </template>

    <!-- 详情弹窗 -->
    <a-modal 
      v-model:open="detailVisible" 
      :title="detailTitle"
      :footer="null" 
      width="780px"
      :bodyStyle="{ maxHeight: '75vh', overflow: 'auto', padding: '20px' }"
    >
      <div class="fid-detail-container">
        <!-- 原文翻页模式详情 -->
        <template v-if="data.mode === 'open'">
          <div class="fid-detail-meta">
            <span>文档: {{ data.document_name }}</span>
            <span>总行数: {{ data.total_lines }}</span>
            <span>显示范围: 第 {{ data.start_line }}-{{ data.end_line }} 行</span>
          </div>
          
          <div class="fid-detail-content">
            <div 
              v-for="(line, i) in contentLines" 
              :key="i" 
              class="fid-detail-line"
              :class="{ highlight: isHighlighted(data.start_line + i) }"
            >
              <span class="fid-detail-line-num">{{ data.start_line + i }}</span>
              <span class="fid-detail-line-text">{{ line || ' ' }}</span>
            </div>
          </div>
        </template>

        <!-- 关键词搜索模式详情 -->
        <template v-if="data.mode === 'search'">
          <div class="fid-detail-stats">
            <div class="fid-stat-item">
              <span class="fid-stat-value">{{ data.total_matches || 0 }}</span>
              <span class="fid-stat-label">总匹配数</span>
            </div>
            <div class="fid-stat-item">
              <span class="fid-stat-value">{{ data.documents?.length || 0 }}</span>
              <span class="fid-stat-label">涉及文档</span>
            </div>
          </div>

          <div class="fid-detail-docs">
            <div class="fid-section-title">
              <FileSearchOutlined />
              <span>匹配结果</span>
            </div>
            
            <div v-for="(doc, di) in data.documents" :key="di" class="fid-detail-doc">
              <div class="fid-doc-header">
                <span class="fid-doc-icon"><FileTextOutlined /></span>
                <span class="fid-doc-title">{{ doc.document_name }}</span>
                <span class="fid-doc-count">{{ doc.match_count }} 处匹配</span>
              </div>
              
              <div class="fid-doc-matches">
                <div 
                  v-for="(match, mi) in doc.matches"
                  :key="mi" 
                  class="fid-match-item"
                >
                  <span class="fid-match-line">匹配 #{{ mi + 1 }} · 第 {{ match.line_num }} 行</span>
                  <div class="fid-match-context">
                    <div 
                      v-for="(ctx, ci) in match.context_lines" 
                      :key="ci"
                      class="fid-context-line"
                      :class="{ matched: ctx.matched }"
                    >
                      <span class="ctx-line-num">{{ ctx.line_num }}</span>
                      <span class="ctx-line-text">{{ ctx.text || ' ' }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { 
  FileTextOutlined, 
  FileSearchOutlined, 
  EyeOutlined 
} from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const detailVisible = ref(false)
const rawResult = computed(() => props.event.result || '')

const data = computed(() => {
  try { return JSON.parse(rawResult.value) } catch { return null }
})

const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)

const contentLines = computed(() => {
  if (!data.value?.content) return []
  return data.value.content.split('\n')
})

const previewLines = computed(() => contentLines.value.slice(0, 10))

const detailTitle = computed(() => {
  if (data.value?.mode === 'open') {
    return data.value.document_name || '文档内容'
  }
  return '文档搜索结果'
})

// 判断某行是否是高亮行（用于搜索模式）
function isHighlighted(lineNum) {
  // 这里可以添加高亮逻辑
  return false
}
</script>

<style lang="less" scoped>
.find-in-doc-result {
  border: 1px solid var(--green-300);
  border-left: 3px solid var(--green-500);
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-success-bg);

  .fid-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
    max-height: 300px; overflow-y: auto;
  }

  .fid-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; border-bottom: 1px solid var(--green-300);
    background: var(--color-success-bg); font-size: 12px; font-weight: 600; color: var(--green-700);
    .fid-header-icon { color: var(--green-600); font-size: 14px; }
    .fid-header-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .fid-header-info { color: var(--green-400); font-weight: 400; white-space: nowrap; }
    .fid-detail-btn {
      appearance: none;
      border: 1px solid var(--green-300);
      border-radius: 6px;
      background: var(--color-canvas);
      color: var(--green-600);
      font-size: 12px;
      padding: 6px 12px;
      cursor: pointer;
      white-space: nowrap;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-weight: 500;
      transition: all 0.2s ease;
      flex-shrink: 0;

      &:hover {
        background: var(--color-success-bg);
        transform: translateY(-1px);
        box-shadow: 0 2px 6px rgba(22, 163, 74, 0.15);
      }

      &:active {
        transform: translateY(0);
      }
    }

    .fid-detail-btn-right {
      margin-left: auto;
    }
  }

  .fid-content { padding: 6px 0; max-height: 240px; overflow-y: auto; }

  .fid-line {
    display: flex; gap: 8px; padding: 1px 10px;
    font-size: 12px; line-height: 1.6;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    &:hover { background: var(--color-success-bg); }
    .fid-line-num {
      color: var(--green-300); min-width: 32px; text-align: right;
      user-select: none; flex-shrink: 0;
    }
    .fid-line-text { color: var(--gray-700); white-space: pre-wrap; word-break: break-word; }
  }

  .fid-summary { padding: 8px 10px; display: flex; flex-direction: column; gap: 4px; }

  .fid-doc-card {
    padding: 6px 8px; border: 1px solid var(--green-200);
    border-radius: 6px; background: var(--color-canvas);
    .fid-doc-row {
      display: flex; align-items: center; gap: 8px;
    }
    .fid-doc-name {
      flex: 1; font-size: 12px; font-weight: 500; color: var(--color-text-dark);
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .fid-match-tag {
      font-size: 11px; color: var(--green-700); background: var(--color-success-bg);
      border-radius: 4px; padding: 0 6px; white-space: nowrap; flex-shrink: 0;
    }
  }

  .fid-more-hint {
    font-size: 11px; color: var(--color-mute); text-align: center;
    padding: 4px 0;
  }
}

// ============ 详情弹窗样式 ============
.fid-detail-container {
  // 原文翻页模式
  .fid-detail-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    padding: 12px 16px;
    background: var(--color-success-bg);
    border-radius: 8px;
    margin-bottom: 16px;
    font-size: 13px;
    color: var(--green-700);
    
    span {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      
      &:not(:last-child)::after {
        content: '·';
        margin-left: 12px;
        color: var(--green-300);
      }
    }
  }
  
  .fid-detail-content {
    border: 1px solid var(--green-200);
    border-radius: 8px;
    overflow: hidden;
    background: var(--color-canvas);
    max-height: 55vh;
    overflow-y: auto;
    
    .fid-detail-line {
      display: flex;
      gap: 12px;
      padding: 2px 12px;
      font-size: 13px;
      line-height: 1.7;
      font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
      
      &:hover {
        background: var(--color-success-bg);
      }
      
      &.highlight {
        background: var(--color-warn-bg-deep);
      }
      
      .fid-detail-line-num {
        color: var(--green-300);
        min-width: 45px;
        text-align: right;
        user-select: none;
        flex-shrink: 0;
      }
      
      .fid-detail-line-text {
        color: var(--gray-800);
        white-space: pre-wrap;
        word-break: break-word;
        flex: 1;
      }
    }
  }
  
  // 关键词搜索模式
  .fid-detail-stats {
    display: flex;
    gap: 16px;
    margin-bottom: 20px;
    padding: 16px;
    background: var(--color-success-bg);
    border-radius: 8px;
    
    .fid-stat-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      flex: 1;
      
      .fid-stat-value {
        font-size: 24px;
        font-weight: 700;
        color: var(--green-600);
      }
      
      .fid-stat-label {
        font-size: 12px;
        color: var(--color-mute);
      }
    }
  }
  
  .fid-section-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 600;
    color: var(--gray-700);
    margin-bottom: 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--gray-100);
  }
  
  .fid-detail-docs {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  
  .fid-detail-doc {
    border: 1px solid var(--green-200);
    border-radius: 10px;
    overflow: hidden;
    background: var(--color-canvas);
    
    .fid-doc-header {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 16px;
      background: var(--color-success-bg);
      border-bottom: 1px solid var(--green-200);
      
      .fid-doc-icon {
        color: var(--green-600);
        font-size: 16px;
      }
      
      .fid-doc-title {
        flex: 1;
        font-size: 14px;
        font-weight: 500;
        color: var(--gray-800);
        word-break: break-word;
      }
      
      .fid-doc-count {
        font-size: 12px;
        font-weight: 500;
        color: var(--green-600);
        background: var(--color-success-bg);
        padding: 4px 10px;
        border-radius: 12px;
        flex-shrink: 0;
      }
    }
    
    .fid-doc-matches {
      padding: 12px 16px;
      display: flex;
      flex-direction: column;
      gap: 16px;
      max-height: 300px;
      overflow-y: auto;
    }
    
    .fid-match-item {
      .fid-match-line {
        font-size: 12px;
        font-weight: 500;
        color: var(--green-600);
        margin-bottom: 8px;
      }
      
      .fid-match-context {
        border: 1px solid var(--color-hairline);
        border-radius: 6px;
        overflow: hidden;
        background: var(--color-canvas-soft);
      }
      
      .fid-context-line {
        display: flex;
        gap: 8px;
        padding: 4px 10px;
        font-size: 12px;
        line-height: 1.6;
        font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
        
        &.matched {
          background: var(--color-warn-bg-deep);
          font-weight: 500;
        }
        
        .ctx-line-num {
          color: var(--color-mute);
          min-width: 35px;
          text-align: right;
          flex-shrink: 0;
        }
        
        .ctx-line-text {
          color: var(--gray-700);
          white-space: pre-wrap;
          word-break: break-word;
        }
      }
    }
    
    .fid-more-matches {
      text-align: center;
      font-size: 12px;
      color: var(--color-mute);
      padding: 8px;
      font-style: italic;
    }
  }
}
</style>