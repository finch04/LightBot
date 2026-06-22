<template>
  <div class="open-kb-doc-result">
    <!-- 纯文本降级 -->
    <div v-if="isPlainText" class="okd-plain">
      <div class="okd-debug-info" v-if="showDebug">
        <strong>调试信息：</strong><br>
        rawResult: {{ rawResult?.substring?.(0, 50) }}...<br>
        data: {{ data }}<br>
        isPlainText: {{ isPlainText }}
      </div>
      <pre>{{ displayText }}</pre>
    </div>

    <template v-else>
      <!-- header：文档名 + 查看按钮 -->
      <div class="okd-header">
        <FileTextOutlined class="okd-header-icon" />
        <span class="okd-header-title">{{ data.document_name }}</span>
        <span class="okd-header-info">共 {{ totalChars }} 字</span>
        <button class="okd-detail-btn" @click="detailVisible = true">
          <EyeOutlined />
          <span>查看完整内容</span>
        </button>
      </div>

      <!-- 内容预览 -->
      <div class="okd-content">
        <div class="okd-preview-text">{{ previewText }}</div>
        <div v-if="isTruncated" class="okd-more-hint">
          内容已截断，点击"查看完整内容"查看全部
        </div>
      </div>
    </template>

    <!-- 完整内容弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      :title="data?.document_name || '文档内容'"
      :footer="null"
      width="780px"
      :bodyStyle="{ maxHeight: '75vh', overflow: 'auto', padding: '20px' }"
      :maskClosable="false"
    >
      <div class="okd-modal-body">
        <div class="okd-modal-meta">
          <FileTextOutlined />
          <span>共 {{ totalChars }} 字</span>
        </div>
        <div class="okd-modal-content">{{ formattedContent }}</div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { FileTextOutlined, EyeOutlined } from '@ant-design/icons-vue'

const PREVIEW_MAX_CHARS = 300

const props = defineProps({ event: { type: Object, required: true } })

const detailVisible = ref(false)

// 调试开关（开发时设为true）
const showDebug = ref(false)

// 获取原始结果（支持字符串或对象）
const rawResult = computed(() => {
  const r = props.event.result
  if (typeof r === 'object' && r !== null) return JSON.stringify(r)
  return r || ''
})

// 解析JSON数据（支持双重转义）
const data = computed(() => {
  try { 
    let jsonStr = rawResult.value
    
    // 第一次解析
    let parsed = JSON.parse(jsonStr)
    
    // 如果解析后仍是字符串（双重转义），再解析一次
    if (typeof parsed === 'string') {
      parsed = JSON.parse(parsed)
    }
    
    // 确保解析成功且是对象，且有content字段
    if (parsed && typeof parsed === 'object' && parsed.content !== undefined) {
      return parsed
    }
    return null
  } catch (e) { 
    console.error('[OpenKbDocumentResult] JSON解析失败:', e, rawResult.value?.substring?.(0, 100))
    return null 
  }
})

// 判断是否纯文本（解析失败或不是预期格式）
const isPlainText = computed(() => !data.value)

// 纯文本时显示的内容
const displayText = computed(() => {
  if (typeof data.value === 'string') return data.value
  return rawResult.value
})

// 完整内容（处理转义的换行符和制表符）
const fullContent = computed(() => {
  if (!data.value?.content) return ''
  // 处理可能的转义字符
  let content = data.value.content
  if (typeof content === 'string') {
    // 处理 \\n 或 \n 转义为真实换行
    content = content
      .replace(/\\n/g, '\n')  // \\n -> \n
      .replace(/\\t/g, '\t')  // \\t -> \t
      .replace(/\\\\/g, '\\')  // \\\\ -> \
  }
  return content
})

const totalChars = computed(() => fullContent.value.length)
const isTruncated = computed(() => fullContent.value.length > PREVIEW_MAX_CHARS)

const previewText = computed(() => {
  if (!isTruncated.value) return fullContent.value
  return fullContent.value.slice(0, PREVIEW_MAX_CHARS) + '...'
})

const formattedContent = computed(() => fullContent.value)
</script>

<style lang="less" scoped>
.open-kb-doc-result {
  border: 1px solid #93c5fd;
  border-left: 3px solid #3b82f6;
  border-radius: 8px;
  overflow: hidden;
  background: #eff6ff;

  .okd-plain {
    .okd-debug-info {
      margin-bottom: 8px;
      padding: 8px;
      background: #fef3c7;
      border: 1px solid #fcd34d;
      border-radius: 4px;
      font-size: 11px;
      color: #92400e;
      font-family: monospace;
    }
    
    pre {
      margin: 0;
      padding: 10px 12px;
      background: #f8fafc;
      border-radius: 6px;
      font-size: 12px;
      line-height: 1.6;
      color: #374151;
      white-space: pre-wrap;
      word-break: break-word;
      max-height: 300px;
      overflow-y: auto;
    }
  }

  .okd-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border-bottom: 1px solid #93c5fd;
    background: #dbeafe;
    font-size: 13px;
    font-weight: 600;
    color: #1e40af;

    .okd-header-icon {
      color: #2563eb;
      font-size: 16px;
      flex-shrink: 0;
    }

    .okd-header-title {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .okd-header-info {
      color: #60a5fa;
      font-weight: 400;
      white-space: nowrap;
      font-size: 12px;
    }

    .okd-detail-btn {
      margin-left: 8px;
      appearance: none;
      border: none;
      border-radius: 6px;
      background: #fff;
      color: #2563eb;
      font-size: 12px;
      padding: 6px 12px;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-weight: 500;
      transition: all 0.2s ease;
      flex-shrink: 0;

      &:hover {
        background: #dbeafe;
        transform: translateY(-1px);
        box-shadow: 0 2px 6px rgba(37, 99, 235, 0.15);
      }

      &:active {
        transform: translateY(0);
      }
    }
  }

  .okd-content {
    padding: 12px 14px;
    background: #fff;

    .okd-preview-text {
      font-size: 13px;
      line-height: 1.8;
      color: #374151;
      white-space: pre-wrap;
      word-break: break-word;
      margin: 0;
    }

    .okd-more-hint {
      margin-top: 10px;
      font-size: 12px;
      color: #2563eb;
      text-align: center;
      padding: 8px 0;
      border-top: 1px dashed #bfdbfe;
    }
  }
}

// 弹窗样式
.okd-modal-body {
  .okd-modal-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: #2563eb;
    font-weight: 500;
    margin-bottom: 16px;
    padding: 10px 14px;
    background: #eff6ff;
    border-radius: 6px;
    border: 1px solid #bfdbfe;
  }

  .okd-modal-content {
    font-size: 14px;
    line-height: 1.9;
    color: #1f2937;
    white-space: pre-wrap;
    word-break: break-word;
    padding: 20px;
    background: #f8fafc;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    max-height: 60vh;
    overflow-y: auto;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  }
}
</style>