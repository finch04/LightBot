<template>
  <div class="search-docs-result">
    <!-- 纯文本降级 -->
    <div v-if="isPlainText" class="sd-plain">
      <pre>{{ displayText }}</pre>
    </div>

    <template v-else>
      <!-- 摘要 header（对齐 QA 卡片风格） -->
      <div class="sd-header">
        <FolderOutlined class="sd-header-icon" />
        <span>搜索文档 — 找到 {{ data.total }} 个匹配文档</span>
        <button v-if="data.total > 0" class="sd-detail-btn" @click="detailVisible = true">
          <EyeOutlined />
          <span>查看详情</span>
        </button>
      </div>

      <!-- 文档卡片列表（摘要） -->
      <div class="sd-docs">
        <div v-for="(doc, i) in data.documents?.slice(0, 3)" :key="i" class="sd-doc-card">
          <div class="sd-doc-index">{{ i + 1 }}</div>
          <div class="sd-doc-info">
            <div class="sd-doc-name">{{ doc.document_name }}</div>
            <div class="sd-doc-meta">
              <span class="sd-kb-tag">
                <DatabaseOutlined />
                {{ doc.knowledge_name }}
              </span>
            </div>
          </div>
          <FileTextOutlined class="sd-doc-icon" />
        </div>
        <div v-if="(data.documents?.length || 0) > 3" class="sd-more-hint">
          还有 {{ data.documents.length - 3 }} 个文档，点击"查看详情"查看全部
        </div>
      </div>
    </template>

    <!-- 详情弹窗 - 卡片表格形式 -->
    <a-modal 
      v-model:open="detailVisible" 
      title="搜索文档结果详情" 
      :footer="null" 
      width="720px"
      :bodyStyle="{ maxHeight: '75vh', overflow: 'auto', padding: '20px' }"
    >
      <div class="sd-detail-container">
        <!-- 统计信息 -->
        <div class="sd-detail-stats">
          <div class="sd-stat-item">
            <span class="sd-stat-value">{{ data.total || 0 }}</span>
            <span class="sd-stat-label">匹配文档数</span>
          </div>
          <div class="sd-stat-item">
            <span class="sd-stat-value">{{ knowledgeBaseCount }}</span>
            <span class="sd-stat-label">涉及知识库</span>
          </div>
        </div>

        <!-- 文档列表 -->
        <div v-if="data.documents?.length" class="sd-detail-list">
          <div class="sd-section-title">
            <FileTextOutlined />
            <span>文档列表</span>
          </div>
          
          <div class="sd-detail-cards">
            <div 
              v-for="(doc, i) in data.documents" 
              :key="i" 
              class="sd-detail-card"
            >
              <div class="sd-card-number">{{ i + 1 }}</div>
              <div class="sd-card-content">
                <div class="sd-card-name">{{ doc.document_name }}</div>
                <div class="sd-card-kb">
                  <DatabaseOutlined />
                  <span>{{ doc.knowledge_name }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="sd-detail-empty">
          <InboxOutlined />
          <p>未找到匹配的文档</p>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { 
  FolderOutlined, 
  FileTextOutlined, 
  EyeOutlined,
  DatabaseOutlined,
  InboxOutlined
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

// 计算涉及的知识库数量
const knowledgeBaseCount = computed(() => {
  if (!data.value?.documents) return 0
  const kbSet = new Set(data.value.documents.map(d => d.knowledge_name))
  return kbSet.size
})
</script>

<style lang="less" scoped>
.search-docs-result {
  border: 1px solid var(--color-border-blue);
  border-left: 3px solid #3b82f6;
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-info-bg);

  .sd-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
    max-height: 300px; overflow-y: auto;
  }

  .sd-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; border-bottom: 1px solid var(--color-border-blue);
    background: var(--color-info-bg); font-size: 12px; font-weight: 600; color: var(--color-link);
    .sd-header-icon { color: var(--color-link); font-size: 14px; }
    .sd-detail-btn {
      margin-left: auto;
      appearance: none;
      border: 1px solid var(--color-border-blue);
      border-radius: 6px;
      background: var(--color-canvas);
      color: var(--color-link);
      font-size: 12px;
      padding: 6px 12px;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-weight: 500;
      transition: all 0.2s ease;
      
      &:hover { 
        background: var(--color-info-bg); 
        transform: translateY(-1px);
        box-shadow: 0 2px 6px rgba(37, 99, 235, 0.15);
      }
      
      &:active {
        transform: translateY(0);
      }
    }
  }

  .sd-docs { padding: 8px 10px; display: flex; flex-direction: column; gap: 4px; }

  .sd-doc-card {
    display: flex; align-items: center; gap: 8px;
    padding: 6px 8px; border: 1px solid var(--color-border-blue);
    border-radius: 6px; background: var(--color-canvas);
    transition: border-color 0.2s;
    &:hover { border-color: var(--color-border-blue); }
  }

  .sd-doc-index {
    font-size: 11px; color: var(--color-link);
    background: var(--color-info-bg); border-radius: 4px;
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
        display: inline-flex;
        align-items: center;
        gap: 4px;
        color: var(--color-link); background: var(--color-info-bg);
        border-radius: 4px; padding: 2px 6px;
      }
    }
  }

  .sd-doc-icon { color: var(--color-mute); font-size: 14px; flex-shrink: 0; }

  .sd-more-hint {
    font-size: 11px; color: var(--color-mute); text-align: center;
    padding: 4px 0;
  }
}

// ============ 详情弹窗样式 ============
.sd-detail-container {
  .sd-detail-stats {
    display: flex;
    gap: 16px;
    margin-bottom: 20px;
    padding: 16px;
    background: var(--color-info-bg);
    border-radius: 8px;
    
    .sd-stat-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      flex: 1;
      
      .sd-stat-value {
        font-size: 24px;
        font-weight: 700;
        color: var(--color-link);
      }
      
      .sd-stat-label {
        font-size: 12px;
        color: var(--color-mute);
      }
    }
  }
  
  .sd-section-title {
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
  
  .sd-detail-cards {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  
  .sd-detail-card {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 14px 16px;
    background: var(--color-canvas);
    border: 1px solid var(--color-border-blue);
    border-radius: 8px;
    transition: all 0.2s;
    
    &:hover {
      border-color: var(--color-border-blue);
      box-shadow: 0 2px 8px rgba(37, 99, 235, 0.08);
    }
    
    .sd-card-number {
      width: 28px;
      height: 28px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--color-info-bg);
      color: var(--color-link);
      font-size: 13px;
      font-weight: 600;
      border-radius: 6px;
      flex-shrink: 0;
    }
    
    .sd-card-content {
      flex: 1;
      min-width: 0;
      
      .sd-card-name {
        font-size: 14px;
        font-weight: 500;
        color: var(--gray-800);
        margin-bottom: 6px;
        word-break: break-word;
      }
      
      .sd-card-kb {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        font-size: 12px;
        color: var(--color-link);
        background: var(--color-info-bg);
        padding: 4px 10px;
        border-radius: 4px;
      }
    }
  }
  
  .sd-detail-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
    padding: 40px;
    color: var(--gray-400);
    font-size: 14px;
    
    :deep(.anticon) {
      font-size: 48px;
    }
  }
}
</style>