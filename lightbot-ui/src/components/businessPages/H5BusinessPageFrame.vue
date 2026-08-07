<template>
  <div class="h5-frame" :class="{ 'is-done': done, 'is-cancelled': isCancelled }">
    <!-- 办理中：展示可交互 H5 表单 -->
    <div v-if="!done" class="h5-shell">
      <div class="h5-title">{{ title }}</div>
      <p v-if="hint" class="h5-hint">{{ hint }}</p>
      <div class="h5-iframe-wrap">
        <iframe
          v-if="srcdocOnce"
          ref="iframeRef"
          class="h5-iframe"
          :srcdoc="srcdocOnce"
          :title="title"
          sandbox="allow-scripts allow-forms allow-same-origin allow-modals"
          referrerpolicy="no-referrer"
          @load="onIframeLoad"
        />
        <iframe
          v-else-if="pageUrl"
          ref="iframeRef"
          class="h5-iframe"
          :src="iframeSrc"
          :title="title"
          sandbox="allow-scripts allow-forms allow-same-origin allow-popups allow-popups-to-escape-sandbox"
          referrerpolicy="no-referrer"
          @load="onIframeLoad"
        />
      </div>
      <div v-if="loadError" class="h5-error">{{ loadError }}</div>
    </div>

    <!-- 办结：回执摘要，不再露出可点的取消/提交 -->
    <div v-else class="h5-receipt">
      <div class="h5-receipt-head">
        <CheckCircleOutlined v-if="!isCancelled" class="h5-receipt-icon ok" />
        <CloseCircleOutlined v-else class="h5-receipt-icon cancel" />
        <div class="h5-receipt-texts">
          <div class="h5-receipt-title">{{ title }}</div>
          <div class="h5-receipt-status">{{ doneStatusText }}</div>
        </div>
      </div>
      <dl v-if="summaryEntries.length" class="h5-receipt-fields">
        <div v-for="item in summaryEntries" :key="item.key" class="h5-receipt-row">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </div>
      </dl>
      <p v-else class="h5-receipt-empty">未记录具体办理字段</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'
import { BUSINESS_PAGE_MSG_SOURCE, injectBusinessPageBridge } from './businessPageBridge'
import { buildBusinessPageSummaryEntries, toCloneableJson } from './businessPageResultUtils'
import { useBusinessPageSubmit } from './useBusinessPageSubmit'

const props = defineProps({
  payload: { type: Object, required: true },
  messageIndex: { type: Number, default: -1 },
  workflowMode: { type: Boolean, default: false },
})
const emit = defineEmits(['workflow-submit', 'workflow-cancel'])

const iframeRef = ref(null)
const loadError = ref('')
/** 进行中只生成一次 srcdoc，避免无填过程中重载 */
const srcdocOnce = ref('')
const { submitting, done, submittedResult, submitResult, cancelResult } = useBusinessPageSubmit(props, emit)

const isCancelled = computed(() => submittedResult.value?.action === 'cancel')
const summaryEntries = computed(() => buildBusinessPageSummaryEntries(
  submittedResult.value?.values,
  submittedResult.value?.fieldLabels,
))
const doneStatusText = computed(() => (isCancelled.value ? '已取消' : '办理已受理'))

const title = computed(() => props.payload?.title || props.payload?.displayName || '业务办理')
const hint = computed(() => {
  const raw = String(props.payload?.options?.hint || '').trim()
  if (!raw || raw === '业务办理页' || raw === 'H5 业务页') return ''
  return raw
})
const pageHtml = computed(() => String(props.payload?.pageHtml || '').trim())
const pageUrl = computed(() => String(props.payload?.pageUrl || '').trim())

const initPayload = computed(() => toCloneableJson({
  pageType: props.payload?.pageType,
  title: title.value,
  props: props.payload?.props || {},
  options: props.payload?.options || {},
  actions: props.payload?.actions || ['submit', 'cancel'],
  // 平台身份快照（与业务 props 分离；历史消息重放用工具结果固化值）
  callerContext: props.payload?.callerContext ?? null,
  identityHeaders: props.payload?.identityHeaders || {},
}) || {
  pageType: props.payload?.pageType,
  title: title.value,
  props: {},
  options: {},
  actions: ['submit', 'cancel'],
  callerContext: null,
  identityHeaders: {},
})

const iframeSrc = computed(() => {
  if (!pageUrl.value) return ''
  try {
    const url = new URL(pageUrl.value)
    url.searchParams.set('pageType', props.payload?.pageType || '')
    url.searchParams.set('mode', props.payload?.mode || 'inline')
    // 身份不得进入 URL（防日志/Referer 泄露）；外链仅靠 load 后 postMessage init
    url.searchParams.delete('payload')
    return url.toString()
  } catch {
    return pageUrl.value
  }
})

watch(
  pageHtml,
  (html) => {
    if (!html || srcdocOnce.value || done.value) return
    srcdocOnce.value = injectBusinessPageBridge(html, initPayload.value.options, initPayload.value)
  },
  { immediate: true },
)

function postInit() {
  const win = iframeRef.value?.contentWindow
  if (!win || done.value || (!srcdocOnce.value && !pageUrl.value)) return
  try {
    win.postMessage({
      source: BUSINESS_PAGE_MSG_SOURCE,
      type: 'init',
      payload: initPayload.value,
    }, '*')
  } catch (e) {
    console.warn('[H5BusinessPage] postMessage init 失败（不影响填报）:', e)
  }
}

function onIframeLoad() {
  loadError.value = ''
  postInit()
}

async function onMessage(event) {
  const data = event?.data
  if (!data || data.source !== BUSINESS_PAGE_MSG_SOURCE) return
  if (iframeRef.value?.contentWindow && event.source !== iframeRef.value.contentWindow) return

  if (data.type === 'resize' && typeof data.height === 'number' && iframeRef.value && !done.value) {
    const h = Math.min(Math.max(Math.ceil(data.height), 60), 720)
    if (Math.abs((iframeRef.value.clientHeight || 0) - h) >= 2) {
      iframeRef.value.style.height = `${h}px`
    }
    return
  }

  if (submitting.value || done.value) return

  if (data.type === 'ready') {
    postInit()
    return
  }
  if (data.type === 'submit') {
    const extra = (data.extra && typeof data.extra === 'object') ? data.extra : {}
    await submitResult({
      action: 'submit',
      pageType: props.payload?.pageType,
      values: data.values || data.result || {},
      // 字段展示名由页面 DOM 采集，禁止平台硬编码业务词典
      fieldLabels: (extra.fieldLabels && typeof extra.fieldLabels === 'object')
        ? extra.fieldLabels
        : (data.fieldLabels || {}),
      ...(Object.keys(extra).length ? { extra } : {}),
    })
    return
  }
  if (data.type === 'cancel') {
    await cancelResult({
      action: 'cancel',
      pageType: props.payload?.pageType,
    })
  }
}

onMounted(() => {
  window.addEventListener('message', onMessage)
  if (!done.value && !pageHtml.value && !pageUrl.value) {
    loadError.value = '未配置页面内容（内嵌 HTML 或外链）'
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('message', onMessage)
})
</script>

<style scoped>
.h5-frame {
  width: 100%;
  max-width: 480px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 14px;
  background: #fff;
  overflow: hidden;
}
.h5-frame.is-done {
  border-color: #a7f3d0;
  background: linear-gradient(180deg, #f0fdf4 0%, #fff 48px);
}
.h5-frame.is-cancelled {
  border-color: #e5e7eb;
  background: #fafafa;
}
.h5-shell { padding: 12px 12px 8px; }
.h5-title { font-weight: 600; font-size: 15px; margin-bottom: 8px; }
.h5-hint { margin: -2px 0 8px; font-size: 12px; color: #6b7280; }
.h5-iframe-wrap { border-radius: 8px; overflow: hidden; }
.h5-iframe {
  width: 100%;
  height: 1px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  display: block;
  overflow: hidden;
}
.h5-error {
  margin-top: 8px;
  font-size: 12px;
  color: #cf1322;
}

.h5-receipt { padding: 14px 16px 16px; }
.h5-receipt-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.h5-receipt-icon {
  font-size: 20px;
  margin-top: 1px;
  flex-shrink: 0;
}
.h5-receipt-icon.ok { color: #059669; }
.h5-receipt-icon.cancel { color: #9ca3af; }
.h5-receipt-texts { min-width: 0; flex: 1; }
.h5-receipt-title {
  font-weight: 600;
  font-size: 15px;
  color: #111827;
  line-height: 1.35;
}
.h5-receipt-status {
  margin-top: 2px;
  font-size: 12px;
  color: #059669;
  font-weight: 500;
}
.h5-frame.is-cancelled .h5-receipt-status { color: #6b7280; }
.h5-receipt-fields {
  margin: 12px 0 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #e5e7eb;
}
.h5-receipt-row {
  display: grid;
  grid-template-columns: 88px 1fr;
  gap: 8px;
  font-size: 13px;
  line-height: 1.5;
  & + & {
    margin-top: 6px;
    padding-top: 6px;
    border-top: 1px dashed #f3f4f6;
  }
  dt {
    margin: 0;
    color: #6b7280;
    font-weight: 500;
  }
  dd {
    margin: 0;
    color: #111827;
    word-break: break-word;
    font-variant-numeric: tabular-nums;
  }
}
.h5-receipt-empty {
  margin: 12px 0 0;
  font-size: 12px;
  color: #9ca3af;
}
</style>
