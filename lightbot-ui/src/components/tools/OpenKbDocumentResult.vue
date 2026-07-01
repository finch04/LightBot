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
        <button class="okd-detail-btn" @click="openModal">
          <EyeOutlined />
          <span>查看详情</span>
        </button>
      </div>

      <!-- 内容预览 -->
      <div class="okd-content">
        <div class="okd-preview-text">{{ previewText }}</div>
        <div v-if="isTruncated" class="okd-more-hint">
          内容已截断，点击"查看详情"查看全部
        </div>
      </div>
    </template>

    <!-- 详情弹窗（带 2 个 tab） -->
    <a-modal
      v-model:open="detailVisible"
      :title="data?.document_name || '文档内容'"
      :footer="null"
      width="780px"
      centered
      :get-container="getToolResultModalContainer"
      :wrap-style="toolResultModalWrapStyle"
      :body-style="buildToolResultModalBodyStyle({ padding: '0' })"
    >
      <div class="okd-modal-body">
        <!-- Tab 切换 -->
        <div class="okd-modal-tabs">
          <button
            class="okd-modal-tab"
            :class="{ active: activeTab === 'chunks' }"
            @click="activeTab = 'chunks'"
          >
            <FileTextOutlined />
            <span>分块内容</span>
          </button>
          <button
            class="okd-modal-tab"
            :class="{ active: activeTab === 'preview' }"
            @click="activeTab = 'preview'"
          >
            <EyeOutlined />
            <span>源文件预览</span>
          </button>
        </div>

        <!-- Tab 1: 分块内容 -->
        <div v-if="activeTab === 'chunks'" class="okd-modal-tab-content">
          <div class="okd-modal-meta">
            <FileTextOutlined />
            <span>共 {{ totalChars }} 字</span>
          </div>
          <div class="okd-modal-text">{{ formattedContent }}</div>
        </div>

        <!-- Tab 2: 源文件预览 -->
        <div v-if="activeTab === 'preview'" class="okd-modal-tab-content">
          <div v-if="filePreview.loading" class="okd-preview-loading">
            <a-spin size="small" />
            <span>加载源文件中...</span>
          </div>
          <div v-else-if="filePreview.error" class="okd-preview-error">
            <span>{{ filePreview.error }}</span>
          </div>
          <div v-else-if="filePreview.url">
            <iframe
              v-if="isPreviewableIframe"
              :src="filePreview.url"
              class="okd-iframe"
              frameborder="0"
            ></iframe>
            <div v-else-if="isPreviewableText" class="okd-text-preview">
              <pre>{{ filePreview.textContent }}</pre>
            </div>
            <div v-else class="okd-unsupported">
              <FileOutlined style="font-size:32px;color:#d4d4d8;" />
              <p style="margin:8px 0 0;font-size:13px;color:#a1a1aa;">
                .{{ filePreview.fileType }} 格式不支持在线预览
              </p>
              <button class="okd-download-btn" @click="openFile">
                <DownloadOutlined /> 下载文件
              </button>
            </div>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { FileTextOutlined, EyeOutlined, FileOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import { getDocumentDownloadUrl } from '../../api/knowledge'
import {
  getToolResultModalContainer,
  toolResultModalWrapStyle,
  buildToolResultModalBodyStyle,
} from '../../composables/useToolResultModal'

const PREVIEW_MAX_CHARS = 300

const props = defineProps({ event: { type: Object, required: true } })

const detailVisible = ref(false)
const activeTab = ref('chunks')
const showDebug = ref(false)

const filePreview = ref({
  url: '',
  fileName: '',
  fileType: '',
  loading: false,
  error: '',
  textContent: ''
})

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
    let parsed = JSON.parse(jsonStr)
    if (typeof parsed === 'string') {
      parsed = JSON.parse(parsed)
    }
    if (parsed && typeof parsed === 'object' && parsed.content !== undefined) {
      return parsed
    }
    return null
  } catch (e) {
    console.error('[OpenKbDocumentResult] JSON解析失败:', e, rawResult.value?.substring?.(0, 100))
    return null
  }
})

const isPlainText = computed(() => !data.value)
const displayText = computed(() => {
  if (typeof data.value === 'string') return data.value
  return rawResult.value
})

const fullContent = computed(() => {
  if (!data.value?.content) return ''
  let content = data.value.content
  if (typeof content === 'string') {
    content = content
      .replace(/\\n/g, '\n')
      .replace(/\\t/g, '\t')
      .replace(/\\\\/g, '\\')
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

// 源文件预览类型判断
const isPreviewableIframe = computed(() => {
  const t = filePreview.value.fileType?.toLowerCase()
  return ['pdf', 'html', 'htm'].includes(t)
})

const isPreviewableText = computed(() => {
  const t = filePreview.value.fileType?.toLowerCase()
  return ['md', 'markdown', 'txt', 'csv', 'json', 'xml', 'log'].includes(t)
})

// 打开弹窗时重置 tab
function openModal() {
  activeTab.value = 'chunks'
  detailVisible.value = true
}

// 切换到源文件预览 tab 时加载
watch(activeTab, (tab) => {
  if (tab === 'preview' && !filePreview.value.url && !filePreview.value.loading) {
    loadFilePreview()
  }
})

async function loadFilePreview() {
  // document_id 可能是 BigInt，转为字符串避免精度丢失
  const docId = data.value?.document_id != null ? String(data.value.document_id) : null
  if (!docId) {
    filePreview.value.error = '无法获取文档ID'
    return
  }
  filePreview.value.loading = true
  filePreview.value.error = ''
  try {
    const res = await getDocumentDownloadUrl(docId)
    const vo = res.data || res
    filePreview.value.url = vo.url || ''
    filePreview.value.fileName = vo.fileName || data.value.document_name || ''
    filePreview.value.fileType = vo.fileType || ''

    // 文本类文件直接 fetch 内容
    if (isPreviewableText.value && filePreview.value.url) {
      try {
        const resp = await fetch(filePreview.value.url)
        filePreview.value.textContent = await resp.text()
      } catch {
        filePreview.value.textContent = fullContent.value
      }
    }
  } catch (e) {
    filePreview.value.error = '加载源文件失败: ' + (e.message || '未知错误')
  } finally {
    filePreview.value.loading = false
  }
}

function openFile() {
  if (filePreview.value.url) window.open(filePreview.value.url, '_blank')
}
</script>

<style lang="less" scoped>
.open-kb-doc-result {
  border: 1px solid var(--color-border-blue);
  border-left: 3px solid #3b82f6;
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-info-bg);

  .okd-plain {
    .okd-debug-info {
      margin-bottom: 8px;
      padding: 8px;
      background: var(--color-warn-bg-deep);
      border: 1px solid #fcd34d;
      border-radius: 4px;
      font-size: 11px;
      color: #92400e;
      font-family: monospace;
    }

    pre {
      margin: 0;
      padding: 10px 12px;
      background: var(--color-canvas-soft);
      border-radius: 6px;
      font-size: 12px;
      line-height: 1.6;
      color: var(--color-text-dark);
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
    background: var(--color-info-bg);
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
      background: var(--color-canvas);
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
        background: var(--color-info-bg);
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
    background: var(--color-canvas);

    .okd-preview-text {
      font-size: 13px;
      line-height: 1.8;
      color: var(--color-text-dark);
      white-space: pre-wrap;
      word-break: break-word;
      margin: 0;
    }

    .okd-more-hint {
      margin-top: 10px;
      font-size: 12px;
      color: var(--color-mute);
      text-align: center;
      padding: 8px 0;
      border-top: 1px dashed #bfdbfe;
    }
  }
}

// ============ 弹窗样式 ============
.okd-modal-body {
  .okd-modal-tabs {
    display: flex;
    border-bottom: 1px solid var(--color-hairline);
    background: var(--color-canvas-soft);

    .okd-modal-tab {
      flex: 1;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      padding: 10px 16px;
      border: none;
      background: transparent;
      color: var(--color-mute);
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      border-bottom: 2px solid transparent;
      transition: all 0.2s;

      &:hover {
        color: #2563eb;
        background: rgba(59, 130, 246, 0.04);
      }

      &.active {
        color: #2563eb;
        border-bottom-color: #3b82f6;
        font-weight: 600;
      }
    }
  }

  .okd-modal-tab-content {
    padding: 16px 20px;
  }

  .okd-modal-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: #2563eb;
    font-weight: 500;
    margin-bottom: 16px;
    padding: 10px 14px;
    background: var(--color-info-bg);
    border-radius: 6px;
    border: 1px solid var(--color-border-blue);
  }

  .okd-modal-text {
    font-size: 14px;
    line-height: 1.9;
    color: var(--color-ink);
    white-space: pre-wrap;
    word-break: break-word;
    padding: 20px;
    background: var(--color-canvas-soft);
    border: 1px solid var(--color-hairline);
    border-radius: 8px;
    max-height: 55vh;
    overflow-y: auto;
  }

  .okd-preview-loading {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 40px;
    color: var(--color-mute);
    font-size: 13px;
  }

  .okd-preview-error {
    padding: 20px;
    text-align: center;
    color: #ef4444;
    font-size: 13px;
  }

  .okd-iframe {
    width: 100%;
    min-height: 450px;
    border: none;
  }

  .okd-text-preview {
    max-height: 50vh;
    overflow-y: auto;

    pre {
      margin: 0;
      font-size: 13px;
      line-height: 1.6;
      color: var(--color-text-dark);
      white-space: pre-wrap;
      word-break: break-word;
      font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    }
  }

  .okd-unsupported {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 40px;
    gap: 4px;
  }

  .okd-download-btn {
    appearance: none;
    border: 1px solid var(--color-border-blue);
    border-radius: 6px;
    background: var(--color-canvas);
    color: #2563eb;
    font-size: 12px;
    padding: 6px 14px;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-weight: 500;
    margin-top: 8px;
    transition: all 0.2s;

    &:hover {
      background: var(--color-info-bg);
    }
  }
}
</style>
