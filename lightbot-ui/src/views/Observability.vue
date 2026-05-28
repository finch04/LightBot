<template>
  <div class="observability">
    <!-- 顶部统计卡片 -->
    <div class="stats-overview">
      <div class="stat-card">
        <div class="stat-icon total-icon"><BarChartOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ overview.totalCount ?? '-' }}</div>
          <div class="stat-label">总请求数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon token-icon"><ThunderboltOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ formatTokens(overview.totalTokens) }}</div>
          <div class="stat-label">总Token</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon duration-icon"><ClockCircleOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ formatDuration(overview.avgDurationMs) }}</div>
          <div class="stat-label">平均耗时</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon tool-icon"><ToolOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ overview.totalToolCalls ?? '-' }}</div>
          <div class="stat-label">工具调用</div>
        </div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <a-input v-model:value="filter.requestId" placeholder="Request ID" :style="{ width: '200px' }" allow-clear />
      <a-input-number v-model:value="filter.sessionId" placeholder="会话ID" :style="{ width: '160px' }" />
      <a-select v-model:value="filter.status" placeholder="状态" :style="{ width: '120px' }" allowClear>
        <a-select-option value="completed">成功</a-select-option>
        <a-select-option value="failed">失败</a-select-option>
        <a-select-option value="running">运行中</a-select-option>
      </a-select>
      <a-range-picker
        v-model:value="filter.timeRange"
        :show-time="{ format: 'HH:mm' }"
        format="YYYY-MM-DD HH:mm"
        :style="{ width: '360px' }"
      />
      <a-button type="primary" @click="loadTraces(1)"><SearchOutlined /> 查询</a-button>
    </div>

    <!-- Trace 列表 -->
    <a-table
      :dataSource="traces"
      :columns="columns"
      :loading="loading"
      :pagination="pagination"
      rowKey="id"
      size="middle"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 'completed' ? 'success' : record.status === 'failed' ? 'error' : 'processing'">
            {{ record.status === 'completed' ? '成功' : record.status === 'failed' ? '失败' : '运行中' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'totalTokens'">
          <span class="token-detail">
            <span class="token-input">{{ record.inputTokens ?? 0 }}</span>
            <span class="token-sep">/</span>
            <span class="token-output">{{ record.outputTokens ?? 0 }}</span>
          </span>
        </template>
        <template v-else-if="column.key === 'totalDurationMs'">
          {{ formatDuration(record.totalDurationMs) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
        </template>
      </template>
    </a-table>

    <!-- Trace 详情抽屉 -->
    <a-drawer
      v-model:open="detailVisible"
      title="Trace 详情"
      :width="860"
      placement="right"
    >
      <template v-if="detailTrace">
        <!-- 基本信息 -->
        <div class="detail-info">
          <div v-if="detailTrace.requestId" class="info-row" style="grid-column: 1 / -1;">
            <span class="info-label">Request ID</span>
            <span class="info-value request-id-text">{{ detailTrace.requestId }}</span>
            <button class="btn-copy btn-copy-inline" @click="copyToClipboard(detailTrace.requestId, 'trace_rid')">
              <CheckOutlined v-if="copiedKey === 'trace_rid'" style="color: #52c41a" />
              <CopyOutlined v-else />
              {{ copiedKey === 'trace_rid' ? '已复制' : '复制' }}
            </button>
          </div>
          <div class="info-row">
            <span class="info-label">Agent</span>
            <span class="info-value">{{ detailTrace.agentName || '-' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">模型</span>
            <a-tag>{{ detailTrace.model || '-' }}</a-tag>
          </div>
          <div class="info-row">
            <span class="info-label">Token</span>
            <span class="info-value">
              <span class="token-detail">
                <span class="token-input" :title="'输入: ' + (detailTrace.inputTokens ?? 0)">入 {{ detailTrace.inputTokens ?? 0 }}</span>
                <span class="token-sep">/</span>
                <span class="token-output" :title="'输出: ' + (detailTrace.outputTokens ?? 0)">出 {{ detailTrace.outputTokens ?? 0 }}</span>
              </span>
            </span>
          </div>
          <div class="info-row">
            <span class="info-label">耗时</span>
            <span class="info-value">{{ formatDuration(detailTrace.totalDurationMs) }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">状态</span>
            <a-tag :color="detailTrace.status === 'completed' ? 'success' : 'error'">
              {{ detailTrace.status === 'completed' ? '成功' : '失败' }}
            </a-tag>
          </div>
          <div v-if="detailTrace.errorMessage" class="info-row error-row">
            <span class="info-label">错误</span>
            <div class="error-content-wrap">
              <span class="info-value error-text">{{ detailTrace.errorMessage }}</span>
              <button class="btn-copy btn-copy-inline" @click="copyToClipboard(detailTrace.errorMessage, 'error')">
                <CheckOutlined v-if="copiedKey === 'error'" style="color: #52c41a" />
                <CopyOutlined v-else />
                {{ copiedKey === 'error' ? '已复制' : '复制' }}
              </button>
            </div>
          </div>
          <div v-if="detailTrace.sessionId" class="info-row" style="grid-column: 1 / -1;">
            <span class="info-label">会话</span>
            <a-button type="link" size="small" @click="goToChat(detailTrace.sessionId)" style="padding: 0; height: auto;">
              跳转到对话 →
            </a-button>
          </div>
        </div>

        <!-- 用户提问与完整模型输入 -->
        <div v-if="traceModelInput.hasData" class="model-input-section">
          <h4>用户提问与模型输入</h4>

          <div v-if="traceModelInput.userContent || traceModelInput.userAttachments.length || traceModelInput.bizParams" class="mi-block">
            <div class="mi-block-title">本轮用户输入</div>
            <div v-if="traceModelInput.userContent" class="mi-pre-wrap">{{ traceModelInput.userContent }}</div>
            <div v-if="traceModelInput.bizParams && Object.keys(traceModelInput.bizParams).length" class="mi-sub">
              <span class="mi-sub-label">入参变量 biz_params</span>
              <pre class="mi-pre">{{ JSON.stringify(traceModelInput.bizParams, null, 2) }}</pre>
            </div>
            <div v-if="traceModelInput.userAttachments.length" class="trace-att-thumbs">
              <MediaAttachmentThumb
                v-for="(att, ai) in traceModelInput.userAttachments"
                :key="'ua-' + ai"
                :att="att"
              />
            </div>
          </div>

          <div v-if="traceModelInput.systemPrompt" class="mi-block">
            <div class="mi-block-title">系统提示词（含工具引导等）</div>
            <pre class="mi-pre">{{ traceModelInput.systemPrompt }}</pre>
          </div>

          <div v-if="traceModelInput.llmMessages.length" class="mi-block">
            <div class="mi-block-title">发送给模型的消息（{{ traceModelInput.llmMessages.length }} 条）</div>
            <div v-for="(m, mi) in traceModelInput.llmMessages" :key="'lm-' + mi" class="mi-msg">
              <div class="mi-msg-head">
                <a-tag size="small" :color="roleTagColor(m.role)">{{ roleLabel(m.role) }}</a-tag>
              </div>
              <pre v-if="m.content" class="mi-pre">{{ m.content }}</pre>
              <div v-else class="mi-empty-text">（无文本内容）</div>
              <div v-if="m.media && m.media.length" class="trace-att-thumbs">
                <template v-for="(med, mdi) in m.media" :key="'med-' + mi + '-' + mdi">
                  <MediaAttachmentThumb v-if="traceMediaCanThumb(med)" :att="med" />
                  <span v-else class="msg-att-file-tag trace-inline-tag">
                    {{ med.fileName || med.mimeType || '多模态附件' }}
                    <span v-if="med.inlineData">（内联 base64，约 {{ med.approxChars }} 字符）</span>
                  </span>
                </template>
              </div>
            </div>
          </div>
        </div>

        <!-- AI完整回复 -->
        <div v-if="detailTrace.replyContent" class="reply-section">
          <div class="reply-header">
            <h4>AI回复内容</h4>
            <button class="btn-copy" @click="copyToClipboard(detailTrace.replyContent, 'reply')">
              <CheckOutlined v-if="copiedKey === 'reply'" style="color: #52c41a" />
              <CopyOutlined v-else />
              {{ copiedKey === 'reply' ? '已复制' : '复制' }}
            </button>
          </div>
          <div class="reply-content-box">{{ detailTrace.replyContent }}</div>
        </div>

        <!-- 瀑布图 -->
        <div class="waterfall-section">
          <h4>调用链路</h4>
          <div class="waterfall-container" v-if="waterfallGroups.length > 0">
            <div class="waterfall-header">
              <span class="wf-label">阶段</span>
              <span class="wf-bar-area">
                <span v-for="p in [0, 25, 50, 75, 100]" :key="p" class="wf-tick" :style="{ left: p + '%' }">{{ p }}%</span>
              </span>
              <span class="wf-duration">耗时</span>
            </div>
            <template v-for="group in waterfallGroups" :key="group.spanId">
              <div
                class="waterfall-row"
                :class="{ 'wf-active': expandedSpans.has(group.spanId) }"
                @click="toggleSpanDetail(group)"
              >
                <span class="wf-label" :style="{ paddingLeft: group._depth * 20 + 8 + 'px' }">
                  <span class="wf-expand-icon">{{ expandedSpans.has(group.spanId) ? '▼' : '▶' }}</span>
                  {{ spanNameLabel(group.name) }}
                  <span v-if="group.spans.length > 1" class="wf-count">x{{ group.spans.length }}</span>
                </span>
                <span class="wf-bar-area">
                  <!-- 子段可视化：每个子段按占比显示 -->
                  <template v-if="group.spans.length > 1">
                    <div
                      v-for="(sub, si) in group._subSegments"
                      :key="si"
                      class="wf-bar wf-bar-segment"
                      :class="'wf-bar-' + spanTypeClass(group.name)"
                      :style="{ left: sub._offsetPercent + '%', width: Math.max(sub._widthPercent, 1) + '%' }"
                      :title="group.name + ' #' + (si + 1) + ': ' + formatDuration(sub.durationMs)"
                    ></div>
                  </template>
                  <template v-else>
                    <div
                      class="wf-bar"
                      :class="'wf-bar-' + spanTypeClass(group.name)"
                      :style="{ left: group._offsetPercent + '%', width: Math.max(group._widthPercent, 1) + '%' }"
                      :title="group.name + ': ' + formatDuration(group.totalDurationMs)"
                    ></div>
                  </template>
                </span>
                <span class="wf-duration">{{ formatDuration(group.totalDurationMs) }}</span>
              </div>
              <!-- 行内展开的 Span 组详情 -->
              <div v-if="expandedSpans.has(group.spanId)" class="span-inline-detail">
                <!-- 多个子段时显示汇总 -->
                <div v-if="group.spans.length > 1" class="sd-section">
                  <div class="sd-section-title">汇总（共 {{ group.spans.length }} 次调用，总耗时 {{ formatDuration(group.totalDurationMs) }}）</div>
                </div>
                <!-- 每个子段的详情 -->
                <div v-for="(sub, si) in group.spans" :key="sub.spanId + '_' + si" class="span-sub-detail">
                  <div v-if="group.spans.length > 1" class="sd-sub-header">
                    <span class="sd-sub-index">#{{ si + 1 }}</span>
                    <span class="sd-sub-time">{{ formatDuration(sub.durationMs) }}（占比 {{ ((sub.durationMs / group.totalDurationMs) * 100).toFixed(0) }}%）</span>
                  </div>
                  <div class="sd-grid">
                    <div class="sd-item"><span class="sd-key">名称</span><span class="sd-val">{{ sub.name }}</span></div>
                    <div class="sd-item"><span class="sd-key">Span ID</span><span class="sd-val">{{ sub.spanId }}</span></div>
                    <div class="sd-item"><span class="sd-key">父Span</span><span class="sd-val">{{ sub.parentSpanId || '-' }}</span></div>
                    <div class="sd-item"><span class="sd-key">状态</span><span class="sd-val">{{ sub.status }}</span></div>
                  </div>
                  <!-- AI回复内容 -->
                  <div v-if="sub.attributes?.replyPreview" class="sd-section">
                    <div class="sd-section-title">AI回复</div>
                    <div class="sd-content-box">{{ sub.attributes.replyPreview }}</div>
                  </div>
                  <!-- AI思考内容 -->
                  <div v-if="sub.attributes?.content && sub.name === 'ai_reasoning'" class="sd-section">
                    <div class="sd-section-title-row">
                      <span class="sd-section-title">思考过程</span>
                      <button class="btn-copy-sm" @click="copyToClipboard(sub.attributes.content, 'reasoning_' + sub.spanId + '_' + si)">
                        <CheckOutlined v-if="copiedKey === 'reasoning_' + sub.spanId + '_' + si" style="color: #52c41a" />
                        <CopyOutlined v-else />
                      </button>
                    </div>
                    <div class="sd-content-box reasoning-box">{{ sub.attributes.content }}</div>
                  </div>
                  <!-- 最终回复内容 -->
                  <div v-if="sub.attributes?.content && sub.name === 'ai_reply'" class="sd-section">
                    <div class="sd-section-title-row">
                      <span class="sd-section-title">完整回复</span>
                      <button class="btn-copy-sm" @click="copyToClipboard(sub.attributes.content, 'reply_' + sub.spanId + '_' + si)">
                        <CheckOutlined v-if="copiedKey === 'reply_' + sub.spanId + '_' + si" style="color: #52c41a" />
                        <CopyOutlined v-else />
                      </button>
                    </div>
                    <div class="sd-content-box">{{ sub.attributes.content }}</div>
                  </div>
                  <!-- 工具调用详情 -->
                  <div v-if="sub.attributes?.toolNames" class="sd-section">
                    <div class="sd-section-title">调用工具</div>
                    <div class="sd-content-box">{{ sub.attributes.toolNames }}</div>
                  </div>
                  <!-- 用户输入（含附件） -->
                  <div v-if="sub.name === 'user_message'" class="sd-section">
                    <div class="sd-section-title">用户问题</div>
                    <div v-if="sub.attributes?.content" class="sd-content-box">{{ sub.attributes.content }}</div>
                    <div v-if="traceAttachments(sub.attributes).length" class="sd-section-title" style="margin-top: 10px;">用户附件</div>
                    <div v-if="traceAttachments(sub.attributes).length" class="trace-att-thumbs">
                      <template v-for="(att, ti) in traceAttachments(sub.attributes)" :key="ti">
                        <MediaAttachmentThumb v-if="traceMediaCanThumb(att)" :att="att" />
                        <span v-else class="msg-att-file-tag trace-inline-tag">{{ att.fileName || att.type || '附件' }}</span>
                      </template>
                    </div>
                  </div>
                  <!-- 发送给 LLM 的消息列表（瀑布图内展开，完整内容见上方「用户提问与模型输入」） -->
                  <div v-if="sub.name === 'messages_to_llm' && traceLlmMessages(sub.attributes).length" class="sd-section">
                    <div class="sd-section-title">发送给模型的消息（{{ traceLlmMessages(sub.attributes).length }} 条，详见上方完整输入区）</div>
                  </div>
                  <!-- 其他属性 -->
                  <div v-if="sub.attributes && Object.keys(sub.attributes).filter(k => !traceHiddenAttrKeys(k)).length" class="sd-section">
                    <div class="sd-section-title">属性</div>
                    <pre class="sd-json">{{ formatAttrs(sub.attributes) }}</pre>
                  </div>
                </div>
              </div>
            </template>
          </div>
          <div v-else class="empty-spans">暂无调用链数据</div>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import {
  BarChartOutlined,
  ThunderboltOutlined,
  ClockCircleOutlined,
  ToolOutlined,
  SearchOutlined,
  CopyOutlined,
  CheckOutlined,
} from '@ant-design/icons-vue'
import MediaAttachmentThumb from '../components/MediaAttachmentThumb.vue'
import { getTraces, getTraceDetail, getTraceOverview } from '../api/observability'
import { useRouter } from 'vue-router'

const router = useRouter()

const loading = ref(false)
const traces = ref([])
const overview = ref({})
const detailVisible = ref(false)
const detailTrace = ref(null)
const expandedSpans = ref(new Set())
const copiedKey = ref(null)
let copyTimer = null

function copyToClipboard(text, key) {
  navigator.clipboard.writeText(text)
  copiedKey.value = key
  clearTimeout(copyTimer)
  copyTimer = setTimeout(() => { copiedKey.value = null }, 2000)
}

const filter = reactive({
  requestId: '',
  sessionId: null,
  status: undefined,
  timeRange: null,
})

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const columns = [
  { title: '时间', key: 'createTime', width: 100 },
  { title: 'Request ID', dataIndex: 'requestId', width: 160, ellipsis: true },
  { title: 'Agent', dataIndex: 'agentName', width: 120, ellipsis: true },
  { title: '模型', dataIndex: 'model', width: 140, ellipsis: true },
  { title: 'Token (入/出)', key: 'totalTokens', width: 130 },
  { title: '耗时', key: 'totalDurationMs', width: 100 },
  { title: '工具', dataIndex: 'toolCallCount', width: 70, align: 'center' },
  { title: '状态', key: 'status', width: 80 },
  { title: '操作', key: 'action', width: 80 },
]

function parseSpansFromDetail() {
  if (!detailTrace.value?.spans) return []
  try {
    const raw = detailTrace.value.spans
    return Array.isArray(raw) ? raw : (typeof raw === 'string' ? JSON.parse(raw) : [])
  } catch {
    return []
  }
}

const traceModelInput = computed(() => {
  const spans = parseSpansFromDetail()
  const userSpan = spans.find(s => s.name === 'user_message')
  const llmSpan = spans.find(s => s.name === 'messages_to_llm')
  const userAttrs = userSpan?.attributes || {}
  const llmAttrs = llmSpan?.attributes || {}
  const userAttachments = traceAttachments(userAttrs)
  const llmMessages = traceLlmMessages(llmAttrs)
  const systemPrompt = llmAttrs.systemPrompt || llmMessages.find(m => m.role === 'system')?.content || ''
  return {
    hasData: !!(userAttrs.content || userAttachments.length || userAttrs.bizParams
      || systemPrompt || llmMessages.length),
    userContent: userAttrs.content || '',
    userAttachments,
    bizParams: userAttrs.bizParams || null,
    systemPrompt,
    llmMessages,
  }
})

const waterfallGroups = computed(() => {
  const spans = parseSpansFromDetail()
  if (spans.length === 0) return []

  const totalDuration = detailTrace.value.totalDurationMs || 1
  const minStart = Math.min(...spans.map(s => s.startTime || 0))
  const spanMap = new Map(spans.map(s => [s.spanId, s]))

  // 计算每个span的深度
  function calcDepth(s) {
    let depth = 0, pid = s.parentSpanId
    while (pid && spanMap.has(pid)) { depth++; pid = spanMap.get(pid).parentSpanId }
    return depth
  }

  // 按 spanId 分组，保持原始顺序
  const groupMap = new Map()
  for (const s of spans) {
    if (!groupMap.has(s.spanId)) {
      groupMap.set(s.spanId, [])
    }
    groupMap.get(s.spanId).push(s)
  }

  // 按首个span的startTime排序
  const sortedEntries = [...groupMap.entries()].sort((a, b) => {
    return (a[1][0].startTime || 0) - (b[1][0].startTime || 0)
  })

  return sortedEntries.map(([spanId, group]) => {
    const firstSpan = group[0]
    const depth = calcDepth(firstSpan)

    // 组的起止时间 = 所有子span的最小startTime ~ 最大endTime
    const groupStart = Math.min(...group.map(s => s.startTime || 0))
    const groupEnd = Math.max(...group.map(s => (s.startTime || 0) + (s.durationMs || 0)))
    const groupDuration = groupEnd - groupStart
    const totalDurationMs = group.reduce((sum, s) => sum + (s.durationMs || 0), 0)

    const offsetMs = groupStart - minStart
    const offsetPercent = Math.min(100, (offsetMs / totalDuration) * 100)
    const widthPercent = Math.min(100 - offsetPercent, (groupDuration / totalDuration) * 100)

    // 子段：每个span在组内的相对位置
    const subSegments = group.map(s => {
      const subOffsetMs = (s.startTime || 0) - groupStart
      const subOffsetPercent = groupDuration > 0 ? (subOffsetMs / groupDuration) * 100 : 0
      const subWidthPercent = groupDuration > 0 ? Math.min(100 - subOffsetPercent, ((s.durationMs || 0) / groupDuration) * 100) : 100
      return { ...s, _offsetPercent: subOffsetPercent, _widthPercent: subWidthPercent }
    })

    return {
      spanId,
      name: firstSpan.name,
      parentSpanId: firstSpan.parentSpanId,
      status: firstSpan.status,
      spans: group,
      totalDurationMs,
      _depth: depth,
      _offsetPercent: offsetPercent,
      _widthPercent: widthPercent,
      _subSegments: subSegments,
    }
  })
})

function spanNameLabel(name) {
  const map = {
    session_resolve: '会话解析',
    agent_load: 'Agent加载',
    build_messages: '消息构建',
    load_model_tools: '模型+工具加载',
    user_message: '用户输入',
    messages_to_llm: '模型输入',
    llm_call: 'LLM调用',
    tool_execute: '工具执行',
    rag_search: 'RAG检索',
    ai_reasoning: 'AI思考',
    ai_reply: 'AI回复',
  }
  return map[name] || name
}

function traceHiddenAttrKeys(k) {
  return ['replyPreview', 'content', 'toolNames', 'attachments', 'messages', 'messageCount', 'systemPrompt', 'bizParams'].includes(k)
}

function roleLabel(role) {
  const map = { system: '系统', user: '用户', assistant: '助手', tool: '工具' }
  return map[role] || role || '未知'
}

function roleTagColor(role) {
  if (role === 'system') return 'purple'
  if (role === 'user') return 'blue'
  if (role === 'assistant') return 'green'
  return 'default'
}

/** 是否可用缩略图预览（有 previewUrl 的图片/视频） */
function traceMediaCanThumb(med) {
  if (!med?.previewUrl) return false
  const t = (med.type || med.mimeType || '').toLowerCase()
  return t === 'image' || t === 'video' || t.includes('image') || t.includes('video')
}

function traceAttachments(attrs) {
  if (!attrs?.attachments || !Array.isArray(attrs.attachments)) return []
  return attrs.attachments
}

function traceLlmMessages(attrs) {
  if (!attrs?.messages || !Array.isArray(attrs.messages)) return []
  return attrs.messages
}

function spanTypeClass(name) {
  if (name === 'llm_call') return 'llm'
  if (name === 'tool_execute') return 'tool'
  if (name === 'rag_search') return 'rag'
  if (name === 'ai_reasoning') return 'reasoning'
  if (name === 'ai_reply') return 'reply'
  return 'other'
}

function toggleSpanDetail(group) {
  if (expandedSpans.value.has(group.spanId)) {
    expandedSpans.value.delete(group.spanId)
  } else {
    expandedSpans.value.add(group.spanId)
  }
}

function formatAttrs(attrs) {
  const filtered = Object.fromEntries(
    Object.entries(attrs).filter(([k]) => !traceHiddenAttrKeys(k))
  )
  return JSON.stringify(filtered, null, 2)
}

async function loadTraces(page) {
  loading.value = true
  try {
    pagination.current = page || 1
    const params = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    }
    if (filter.requestId?.trim()) params.requestId = filter.requestId.trim()
    if (filter.sessionId) params.sessionId = filter.sessionId
    if (filter.status) params.status = filter.status
    if (filter.timeRange?.length === 2) {
      params.startTime = filter.timeRange[0].format('YYYY-MM-DD HH:mm:ss')
      params.endTime = filter.timeRange[1].format('YYYY-MM-DD HH:mm:ss')
    }
    const res = await getTraces(params)
    traces.value = res.data.records || []
    pagination.total = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadOverview() {
  try {
    const res = await getTraceOverview()
    overview.value = res.data || {}
  } catch { /* ignore */ }
}

function handleTableChange(pag) {
  loadTraces(pag.current)
}

function goToChat(sessionId) {
  router.push(`/chat/${sessionId}`)
}

async function openDetail(record) {
  detailVisible.value = true
  expandedSpans.value = new Set()
  try {
    const res = await getTraceDetail(record.id)
    detailTrace.value = res.data
  } catch {
    detailTrace.value = record
  }
}

function formatTime(t) {
  if (!t) return '-'
  if (Array.isArray(t)) {
    return `${String(t[3]).padStart(2,'0')}:${String(t[4]).padStart(2,'0')}:${String(t[5]).padStart(2,'0')}`
  }
  const d = new Date(t)
  return d.toLocaleTimeString('zh-CN', { hour12: false })
}

function formatDuration(ms) {
  if (!ms && ms !== 0) return '-'
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(1) + 's'
}

function formatTokens(tokens) {
  if (!tokens && tokens !== 0) return '-'
  if (tokens >= 10000) return (tokens / 10000).toFixed(1) + 'w'
  return String(tokens)
}

onMounted(() => {
  loadTraces(1)
  loadOverview()
})
</script>

<style scoped>
.observability {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

/* 统计卡片 */
.stats-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: var(--bg-card, #fff);
  border: 1px solid var(--border-color, #e8e8e8);
  border-radius: 10px;
  padding: 18px 20px;
}
.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #fff;
}
.total-icon { background: linear-gradient(135deg, #1890ff, #096dd9); }
.token-icon { background: linear-gradient(135deg, #722ed1, #531dab); }
.duration-icon { background: linear-gradient(135deg, #13c2c2, #08979c); }
.tool-icon { background: linear-gradient(135deg, #fa8c16, #d46b08); }
.stat-value { font-size: 22px; font-weight: 600; line-height: 1.2; }
.stat-label { font-size: 12px; color: #8c8c8c; margin-top: 2px; }

/* 筛选栏 */
.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}

/* Token 详情 */
.token-detail { font-size: 13px; }
.token-input { color: #1890ff; }
.token-sep { color: #d9d9d9; margin: 0 3px; }
.token-output { color: #52c41a; }

/* 瀑布图 */
.waterfall-section { margin-top: 24px; }
.waterfall-section h4 { font-size: 15px; font-weight: 600; margin-bottom: 12px; }
.waterfall-header, .waterfall-row {
  display: flex;
  align-items: center;
  height: 32px;
}
.waterfall-header {
  font-size: 12px;
  color: #8c8c8c;
  border-bottom: 1px solid #f0f0f0;
}
.wf-label {
  width: 180px;
  min-width: 180px;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 4px;
}
.wf-bar-area {
  flex: 1;
  position: relative;
  height: 20px;
}
.wf-tick {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  font-size: 10px;
  color: #d9d9d9;
}
.wf-bar {
  position: absolute;
  height: 16px;
  top: 2px;
  border-radius: 3px;
  min-width: 4px;
  cursor: pointer;
  transition: opacity 0.2s;
}
.wf-bar:hover { opacity: 0.8; }
.wf-bar-llm { background: linear-gradient(90deg, #1890ff, #40a9ff); }
.wf-bar-tool { background: linear-gradient(90deg, #faad14, #ffc53d); }
.wf-bar-rag { background: linear-gradient(90deg, #52c41a, #73d13d); }
.wf-bar-reasoning { background: linear-gradient(90deg, #722ed1, #9254de); }
.wf-bar-reply { background: linear-gradient(90deg, #13c2c2, #36cfc9); }
.wf-bar-other { background: linear-gradient(90deg, #bfbfbf, #d9d9d9); }
.wf-duration {
  width: 70px;
  min-width: 70px;
  text-align: right;
  font-size: 12px;
  color: #595959;
}
.wf-expand-icon { font-size: 10px; color: #8c8c8c; }
.wf-count { font-size: 10px; color: #fa8c16; margin-left: 4px; font-weight: 500; }
.wf-bar-segment { border-right: 1px solid rgba(255,255,255,0.6); }
.waterfall-row { cursor: pointer; border-radius: 4px; }
.waterfall-row:hover { background: #fafafa; }
.waterfall-row.wf-active { background: #e6f7ff; }

/* 行内 Span 详情 */
.span-inline-detail {
  padding: 12px 16px 12px 32px;
  background: #fafafa;
  border-left: 3px solid #1890ff;
  margin: 0 0 4px 0;
  border-radius: 0 6px 6px 0;
  animation: slideDown 0.15s ease-out;
}
.span-sub-detail {
  padding: 8px 0;
  border-bottom: 1px dashed #e8e8e8;
}
.span-sub-detail:last-child { border-bottom: none; }
.sd-sub-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.sd-sub-index {
  font-size: 11px;
  font-weight: 600;
  color: #1890ff;
  background: #e6f7ff;
  padding: 1px 6px;
  border-radius: 4px;
}
.sd-sub-time {
  font-size: 11px;
  color: #8c8c8c;
}
@keyframes slideDown {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}
.sd-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 16px;
  margin-bottom: 10px;
}
.sd-item { display: flex; align-items: center; gap: 6px; }
.sd-key { color: #8c8c8c; font-size: 12px; min-width: 50px; }
.sd-val { font-size: 12px; color: #262626; }
.sd-section { margin-top: 8px; }
.sd-section-title { font-size: 12px; color: #8c8c8c; margin-bottom: 4px; font-weight: 500; }
.sd-content-box {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.reasoning-box { color: #722ed1; background: #f9f0ff; border-color: #d3adf7; }
.sd-json {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 11px;
  overflow-x: auto;
  max-height: 150px;
  overflow-y: auto;
  margin: 0;
}

/* 详情信息 */
.detail-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 20px;
  margin-bottom: 20px;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}
.info-row { display: flex; align-items: center; gap: 8px; }
.info-label { color: #8c8c8c; font-size: 13px; min-width: 50px; }
.info-value { font-size: 13px; }
.error-row { grid-column: 1 / -1; align-items: flex-start; }
.error-content-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.error-text { color: #ff4d4f; font-size: 12px; word-break: break-word; white-space: pre-wrap; }
.request-id-text { font-family: 'Geist Mono', Menlo, monospace; font-size: 12px; word-break: break-all; }
.btn-copy-inline { align-self: flex-start; }

.empty-spans {
  text-align: center;
  color: #bfbfbf;
  padding: 40px;
  font-size: 13px;
}

/* 用户提问与模型输入 */
.model-input-section {
  margin-bottom: 24px;
  padding: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}
.model-input-section h4 {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 14px;
}
.mi-block {
  margin-bottom: 16px;
}
.mi-block:last-child {
  margin-bottom: 0;
}
.mi-block-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 8px;
}
.mi-pre-wrap {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.6;
  color: #1e293b;
}
.mi-pre {
  margin: 0;
  padding: 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 480px;
  overflow-y: auto;
}
.mi-sub {
  margin-top: 10px;
}
.mi-sub-label {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}
.mi-msg {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px dashed #e2e8f0;
}
.mi-msg:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}
.mi-msg-head {
  margin-bottom: 6px;
}
.mi-empty-text {
  font-size: 12px;
  color: #94a3b8;
}
/* 附件缩略图（与对话页一致） */
.trace-att-thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.trace-inline-tag {
  font-size: 12px;
  color: #52525b;
  padding: 4px 10px;
  background: #f4f4f5;
  border-radius: 6px;
  max-width: 100%;
  word-break: break-all;
}

/* AI回复内容 */
.trace-llm-msg {
  margin-bottom: 10px;
}
.trace-llm-role {
  font-size: 11px;
  font-weight: 600;
  color: #71717a;
  text-transform: uppercase;
  margin-bottom: 4px;
}
.trace-llm-content {
  max-height: 120px;
  overflow-y: auto;
  font-size: 12px;
}
.trace-llm-media-hint {
  font-size: 11px;
  color: #a1a1aa;
  margin-top: 4px;
  display: inline-block;
}

.reply-section { margin-bottom: 20px; }
.reply-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.reply-header h4 { font-size: 15px; font-weight: 600; margin: 0; }
.btn-copy {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 6px;
  font-size: 12px;
  color: #71717a;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-copy:hover {
  border-color: #0070f3;
  color: #0070f3;
}
.btn-copy-sm {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 6px;
  background: transparent;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  color: #8c8c8c;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-copy-sm:hover {
  background: #f0f0f0;
  color: #0070f3;
}
.sd-section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.sd-section-title-row .sd-section-title {
  margin-bottom: 0;
}
.reply-content-box {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 14px 16px;
  font-size: 13px;
  line-height: 1.8;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
