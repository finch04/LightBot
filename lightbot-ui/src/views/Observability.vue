<template>
  <div class="observability">
    <!-- 类型切换 Tab -->
    <div class="trace-tabs">
      <a-radio-group v-model:value="activeTab" button-style="solid">
        <a-radio-button value="chat">对话型链路</a-radio-button>
        <a-radio-button value="workflow">工作流链路</a-radio-button>
        <a-radio-button value="tool">工具调用链路</a-radio-button>
      </a-radio-group>
    </div>

    <!-- 顶部统计卡片 -->
    <div v-if="activeTab !== 'tool'" class="stats-overview">
      <LbStatCard :icon="BarChartOutlined" accent="blue" :value="overview.totalCount ?? '-'" label="总请求数" :loading="statsLoading" />
      <LbStatCard :icon="ThunderboltOutlined" accent="purple" :value="formatTokens(overview.totalTokens)" label="总Token" :loading="statsLoading" />
      <LbStatCard :icon="ClockCircleOutlined" accent="teal" :value="formatDuration(overview.avgDurationMs)" label="平均耗时" :loading="statsLoading" />
      <LbStatCard :icon="ToolOutlined" accent="orange" :value="overview.totalToolCalls ?? '-'" label="工具调用" :loading="statsLoading" />
    </div>

    <!-- 对话/工作流 筛选栏 -->
    <div v-if="activeTab !== 'tool'" class="filter-bar">
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
      <a-button @click="handleRefresh"><ReloadOutlined /> 刷新</a-button>
      <a-button v-if="isAdmin" danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete"><DeleteOutlined /> 批量删除</a-button>
    </div>

    <!-- 工具调用 筛选栏 -->
    <div v-if="activeTab === 'tool'" class="filter-bar">
      <a-input v-model:value="toolFilter.toolName" placeholder="工具名称" style="width: 180px" allow-clear @pressEnter="loadToolCalls(1)" />
      <a-select v-model:value="toolFilter.status" placeholder="状态" style="width: 120px" allowClear>
        <a-select-option value="success">成功</a-select-option>
        <a-select-option value="error">失败</a-select-option>
        <a-select-option value="pending">执行中</a-select-option>
      </a-select>
      <a-range-picker
        v-model:value="toolFilter.timeRange"
        :show-time="{ format: 'HH:mm' }"
        format="YYYY-MM-DD HH:mm"
        style="width: 380px"
      />
      <a-button type="primary" @click="loadToolCalls(1)"><SearchOutlined /> 查询</a-button>
      <a-button @click="handleToolRefresh"><ReloadOutlined /> 刷新</a-button>
      <a-button v-if="isAdmin" danger :disabled="selectedToolRowKeys.length === 0" @click="handleBatchDeleteTool"><DeleteOutlined /> 批量删除</a-button>
    </div>

    <!-- Trace 列表 -->
    <a-table v-if="activeTab !== 'tool'"
      :dataSource="traces"
      :columns="columns"
      :loading="loading"
      :pagination="pagination"
      :rowSelection="{ selectedRowKeys, onChange: keys => selectedRowKeys = keys }"
      rowKey="id"
      size="middle"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
        <!-- 仅 Request ID / Agent / 模型 三列保留悬浮 tooltip，其余列不弹 -->
        <template v-else-if="column.dataIndex === 'requestId'">
          <a-tooltip :title="record.requestId || undefined" placement="topLeft">
            <span class="cell-ellipsis">{{ record.requestId }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'agentName'">
          <a-tooltip :title="record.agentName || undefined" placement="topLeft">
            <span class="cell-ellipsis">{{ record.agentName || '-' }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'model'">
          <a-tooltip :title="record.model || undefined" placement="topLeft">
            <span class="cell-ellipsis">{{ record.model || '-' }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="traceStatusColor(record.status)">
            {{ traceStatusLabel(record.status) }}
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

    <!-- 工具调用 列表 -->
    <a-table v-if="activeTab === 'tool'"
      :dataSource="toolRecords"
      :columns="toolColumns"
      :loading="toolLoading"
      :pagination="toolPagination"
      :rowSelection="{ selectedRowKeys: selectedToolRowKeys, onChange: keys => selectedToolRowKeys = keys }"
      rowKey="id"
      size="small"
      @change="handleToolTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'toolName'">
          <span class="tool-name-cell"><ToolOutlined /> {{ record.toolName }}</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 'success' ? 'success' : record.status === 'error' ? 'error' : 'processing'">
            {{ record.status === 'success' ? '成功' : record.status === 'error' ? '失败' : '执行中' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'toolInput'">
          <span class="cell-truncate">{{ formatJsonPreview(record.toolInput) }}</span>
        </template>
        <template v-else-if="column.key === 'toolOutput'">
          <span class="cell-truncate">{{ truncate(record.toolOutput, 80) }}</span>
        </template>
        <template v-else-if="column.key === 'createdAt'">
          {{ formatTime(record.createdAt) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button type="link" size="small" @click="openToolDetail(record)">详情</a-button>
        </template>
      </template>
    </a-table>

    <!-- 工具调用 详情弹窗 -->
    <a-modal
      v-model:open="toolDetailVisible"
      title="工具调用详情"
      :width="720"
      :footer="null"
      :maskClosable="false"
    >
      <div v-if="toolDetailRecord" class="dialog-scroll-body">
        <a-descriptions :column="2" size="small" bordered style="margin-bottom: 16px">
          <a-descriptions-item label="工具名称">
            <ToolOutlined /> {{ toolDetailRecord.toolName }}
          </a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="toolDetailRecord.status === 'success' ? 'success' : toolDetailRecord.status === 'error' ? 'error' : 'processing'">
              {{ toolDetailRecord.status === 'success' ? '成功' : toolDetailRecord.status === 'error' ? '失败' : '执行中' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="消息ID" :span="2">{{ toolDetailRecord.messageId || '-' }}</a-descriptions-item>
          <a-descriptions-item label="调用时间" :span="2">{{ formatTime(toolDetailRecord.createdAt) }}</a-descriptions-item>
          <a-descriptions-item label="错误信息" :span="2" v-if="toolDetailRecord.errorMessage">
            <span style="color: var(--color-error)">{{ toolDetailRecord.errorMessage }}</span>
          </a-descriptions-item>
        </a-descriptions>

        <a-divider style="margin: 12px 0" />

        <div class="detail-section">
          <div class="detail-label">输入参数</div>
          <pre class="detail-pre">{{ formatJson(toolDetailRecord.toolInput) }}</pre>
        </div>

        <div class="detail-section">
          <div class="detail-label">
            输出结果
            <button class="btn-copy" @click="copyToolOutput">
              <CheckOutlined v-if="toolOutputCopied" style="color: var(--green-500)" />
              <CopyOutlined v-else />
            </button>
          </div>
          <pre class="detail-pre detail-pre-json">{{ formatJson(toolDetailRecord.toolOutput) }}</pre>
        </div>
      </div>
    </a-modal>

    <!-- Trace 详情抽屉 -->
    <a-drawer
      v-model:open="detailVisible"
      :width="860"
      placement="right"
    >
      <template #title>
        <div style="display:flex;align-items:center;justify-content:space-between;width:100%;padding-right:40px;">
          <span>Trace 详情</span>
          <a-button v-if="detailTrace?.sessionId" size="small" @click="goToChat(detailTrace.sessionId)">
            跳转到对话 →
          </a-button>
        </div>
      </template>
      <template v-if="detailTrace">
        <!-- 基本信息 -->
        <div class="detail-info">
          <div v-if="detailTrace.requestId" class="info-row" style="grid-column: 1 / -1;">
            <span class="info-label">Request ID</span>
            <span class="info-value request-id-text">{{ detailTrace.requestId }}</span>
            <button class="btn-copy btn-copy-inline" @click="copyToClipboard(detailTrace.requestId, 'trace_rid')">
              <CheckOutlined v-if="copiedKey === 'trace_rid'" style="color: var(--green-500)" />
              <CopyOutlined v-else />
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
                <span class="token-input">入 {{ detailTrace.inputTokens ?? 0 }}</span>
                <span class="token-sep">/</span>
                <span class="token-output">出 {{ detailTrace.outputTokens ?? 0 }}</span>
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
                <CheckOutlined v-if="copiedKey === 'error'" style="color: var(--green-500)" />
                <CopyOutlined v-else />
                {{ copiedKey === 'error' ? '已复制' : '复制' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 用户提问与完整模型输入 -->
        <div v-if="traceModelInput.hasData" class="model-input-section">
          <h4>用户提问与模型输入</h4>

          <div v-if="traceModelInput.userContent || traceModelInput.userAttachments.length || traceModelInput.bizParams" class="mi-block">
            <div class="mi-block-title-row">
              <span class="mi-block-title">本轮用户输入</span>
              <button v-if="traceModelInput.userContent" class="btn-copy-sm" @click="copyToClipboard(traceModelInput.userContent, 'mi_user')">
                <CheckOutlined v-if="copiedKey === 'mi_user'" style="color: var(--green-500)" />
                <CopyOutlined v-else />
              </button>
            </div>
            <div v-if="traceModelInput.userContent" class="mi-pre-wrap">
              <MentionTextRenderer
                v-if="shouldShowTraceMentions(traceModelInput.userContent, traceModelInput.userMentions)"
                :content="traceModelInput.userContent"
                :mentions="traceModelInput.userMentions"
              />
              <template v-else>{{ traceModelInput.userContent }}</template>
            </div>
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
            <div class="mi-block-title-row">
              <span class="mi-block-title">系统提示词（含工具引导等）</span>
              <button class="btn-copy-sm" @click="copyToClipboard(traceModelInput.systemPrompt, 'mi_sys')">
                <CheckOutlined v-if="copiedKey === 'mi_sys'" style="color: var(--green-500)" />
                <CopyOutlined v-else />
              </button>
            </div>
            <pre class="mi-pre">{{ traceModelInput.systemPrompt }}</pre>
          </div>

          <div v-if="traceModelInput.llmMessages.length" class="mi-block">
            <div class="mi-block-title-row">
              <span class="mi-block-title">发送给模型的消息（{{ traceModelInput.llmMessages.length }} 条）</span>
              <button class="btn-copy-sm" @click="copyAllLlmMessages()">
                <CheckOutlined v-if="copiedKey === 'mi_msgs'" style="color: var(--green-500)" />
                <CopyOutlined v-else />
              </button>
            </div>
            <!-- ask_user 关联提示 -->
            <div v-if="traceModelInput.currentUserParentId" class="ask-user-banner">
              <span class="ask-user-icon">Q</span>
              <span>此消息是回复 ask_user 工具的提问，关联父消息 ID: {{ traceModelInput.currentUserParentId }}</span>
            </div>
            <div v-for="(m, mi) in traceModelInput.llmMessages" :key="'lm-' + mi"
              class="mi-msg"
              :class="{
                'ask-user-trigger': m._askUserRole === 'trigger',
                'ask-user-response': m._askUserRole === 'response',
                'mi-msg-history': m.source === 'history',
              }"
            >
              <div class="mi-msg-head">
                <a-tag size="small" :color="roleTagColor(m.role)">{{ roleLabel(m.role) }}</a-tag>
                <a-tag v-if="m.source === 'history'" size="small" color="default">历史</a-tag>
                <a-tag v-else size="small" color="blue">本轮</a-tag>
                <a-tag v-if="m.orphanPlaceholder" size="small" color="orange">未完成回复</a-tag>
                <span v-if="m._askUserRole === 'trigger'" class="ask-user-badge trigger-badge">ask_user 触发</span>
                <span v-if="m._askUserRole === 'response'" class="ask-user-badge response-badge">ask_user 回复</span>
                <button v-if="m.content" class="btn-copy-sm" @click="copyToClipboard(m.content, 'mi_msg_' + mi)">
                  <CheckOutlined v-if="copiedKey === 'mi_msg_' + mi" style="color: var(--green-500)" />
                  <CopyOutlined v-else />
                </button>
              </div>
              <div v-if="m.content" class="mi-pre">
                <MentionTextRenderer
                  v-if="m.role === 'user' && shouldShowTraceMentions(m.content, resolveTraceMessageMentions(m, traceModelInput.userMentions))"
                  :content="m.content"
                  :mentions="resolveTraceMessageMentions(m, traceModelInput.userMentions)"
                />
                <template v-else>{{ m.content }}</template>
              </div>
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

        <!-- 请求配置与工具定义 -->
        <div v-if="traceModelInput.requestConfig || traceModelInput.requestTools.length" class="request-detail-actions">
          <a-button v-if="traceModelInput.requestConfig" size="small" @click="configModalVisible = true">
            <template #icon><SettingOutlined /></template>
            请求配置
          </a-button>
          <a-button v-if="traceModelInput.requestTools.length" size="small" @click="toolsModalVisible = true">
            <template #icon><ToolOutlined /></template>
            工具定义（{{ traceModelInput.toolCount }}）
          </a-button>
        </div>

        <!-- AI完整回复（模型原始输出，含深度思考） -->
        <div v-if="detailTrace.replyContent" class="reply-section">
          <h4>AI完整回复</h4>
          <div class="reply-content-box">{{ detailTrace.replyContent }}</div>
        </div>

        <!-- 用户对话页可见正文 -->
        <div v-if="detailTrace.displayContent" class="reply-section">
          <h4>最终展示内容</h4>
          <div class="reply-content-box display-content-box">{{ detailTrace.displayContent }}</div>
        </div>

        <!-- 瀑布图 -->
        <ObservabilitySubAgentTree :spans="parsedSpans" />
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
                    ></div>
                  </template>
                  <template v-else>
                    <div
                      class="wf-bar"
                      :class="'wf-bar-' + spanTypeClass(group.name)"
                      :style="{ left: group._offsetPercent + '%', width: Math.max(group._widthPercent, 1) + '%' }"
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
                        <CheckOutlined v-if="copiedKey === 'reasoning_' + sub.spanId + '_' + si" style="color: var(--green-500)" />
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
                        <CheckOutlined v-if="copiedKey === 'reply_' + sub.spanId + '_' + si" style="color: var(--green-500)" />
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
                    <div v-if="sub.attributes?.content" class="sd-content-box">
                      <MentionTextRenderer
                        v-if="shouldShowTraceMentions(sub.attributes.content, normalizeTraceMentions(sub.attributes.mentions))"
                        :content="sub.attributes.content"
                        :mentions="normalizeTraceMentions(sub.attributes.mentions)"
                      />
                      <template v-else>{{ sub.attributes.content }}</template>
                    </div>
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

    <!-- 请求配置弹窗 -->
    <a-modal v-model:open="configModalVisible" title="请求配置" :width="600" :footer="null" :maskClosable="false">
      <div class="dialog-scroll-body">
        <div class="config-grid">
          <div v-for="(value, key) in traceModelInput.requestConfig" :key="key" class="config-item">
            <span class="config-key">{{ key }}</span>
            <span class="config-val">{{ formatConfigValue(value) }}</span>
          </div>
        </div>
      </div>
    </a-modal>

    <!-- 工具定义弹窗 -->
    <a-modal v-model:open="toolsModalVisible" title="工具定义" :width="700" :footer="null" :maskClosable="false" @after-close="onToolsModalClosed">
      <a-input
        v-model:value="toolSearchKeyword"
        placeholder="搜索工具标识"
        allow-clear
        class="tool-def-search"
      >
        <template #prefix><SearchOutlined /></template>
      </a-input>
      <div class="dialog-scroll-body">
        <div
          v-for="(tool, ti) in traceModelInput.requestTools"
          :key="ti"
          :ref="el => setToolDefRef(el, ti)"
          class="tool-def-item"
          :class="{ 'tool-def-dimmed': toolSearchKeyword && !toolMatchesSearch(tool) }"
        >
          <div class="tool-def-header">
            <a-tag color="blue" v-html="highlightToolName(tool.name)"></a-tag>
            <span class="tool-def-desc">{{ tool.description }}</span>
          </div>
          <pre v-if="tool.inputSchema" class="mi-pre" style="margin-top: 6px; max-height: 150px;">{{ formatJson(tool.inputSchema) }}</pre>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BarChartOutlined,
  ThunderboltOutlined,
  ClockCircleOutlined,
  ToolOutlined,
  SearchOutlined,
  CopyOutlined,
  CheckOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import MediaAttachmentThumb from '../components/MediaAttachmentThumb.vue'
import MentionTextRenderer from '../components/MentionTextRenderer.vue'
import ObservabilitySubAgentTree from '../components/observability/ObservabilitySubAgentTree.vue'
import LbStatCard from '../components/common/LbStatCard.vue'
import { getTraces, getTraceDetail, getTraceOverview, deleteTraces } from '../api/observability'
import { getToolCalls, deleteToolCalls } from '../api/toolCall'
import { getSession } from '../api/chatSession'
import { formatTime, formatJson } from '../utils/format'
import { contentHasMentionTokens } from '../utils/mention_utils'
import { copyToClipboard as sharedCopy } from '../utils/clipboard'
import { normalizeObservabilityTab } from '../utils/observabilityTabs'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.user?.role === 'admin')

const loading = ref(false)
const statsLoading = ref(false)
const traces = ref([])
const overview = ref({})
const activeTab = ref(normalizeObservabilityTab(route.query.tab))
const selectedRowKeys = ref([])
const selectedToolRowKeys = ref([])
const detailVisible = ref(false)
const detailTrace = ref(null)
const expandedSpans = reactive(new Set())
const copiedKey = ref(null)
const configModalVisible = ref(false)
const toolsModalVisible = ref(false)
const toolSearchKeyword = ref('')
const toolDefRefs = new Map()
let copyTimer = null

function setToolDefRef(el, index) {
  if (el) {
    toolDefRefs.set(index, el)
  } else {
    toolDefRefs.delete(index)
  }
}

function escapeToolHtml(str) {
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function toolMatchesSearch(tool) {
  const kw = toolSearchKeyword.value.trim().toLowerCase()
  if (!kw) return false
  return String(tool?.name || '').toLowerCase().includes(kw)
}

function highlightToolName(name) {
  const raw = escapeToolHtml(name || '')
  const kw = toolSearchKeyword.value.trim()
  if (!kw) return raw
  const escapedKw = escapeToolHtml(kw).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return raw.replace(new RegExp(escapedKw, 'gi'), '<mark class="tool-def-hl">$&</mark>')
}

function onToolsModalClosed() {
  toolSearchKeyword.value = ''
  toolDefRefs.clear()
}

watch(toolSearchKeyword, (kw) => {
  if (!kw.trim()) return
  const tools = traceModelInput.value.requestTools || []
  const firstIndex = tools.findIndex(toolMatchesSearch)
  if (firstIndex < 0) return
  nextTick(() => {
    toolDefRefs.get(firstIndex)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  })
})

async function copyToClipboard(text, key) {
  await sharedCopy(text)
  copiedKey.value = key
  clearTimeout(copyTimer)
  copyTimer = setTimeout(() => { copiedKey.value = null }, 2000)
}

function normalizeTraceMentions(raw, content = '') {
  if (!Array.isArray(raw) || raw.length === 0) {
    return contentHasMentionTokens(content)
      ? content.match(/@(knowledge|subagent|skill|tool):(\d+)/g)?.map(token => {
          const [, type = '', resourceId = ''] = token.match(/@(knowledge|subagent|skill|tool):(\d+)/) || []
          return {
            type,
            resourceId: String(resourceId),
            name: `${type}:${resourceId}`,
            token,
          }
        }).filter(Boolean) || []
      : []
  }
  return raw.map(m => ({
    type: m?.type || '',
    resourceId: m?.resourceId != null ? String(m.resourceId) : '',
    name: m?.name || (m?.type && m?.resourceId ? `${m.type}:${m.resourceId}` : m?.token || ''),
    token: m?.token || (m?.type && m?.resourceId ? `@${m.type}:${m.resourceId}` : ''),
  }))
}

function resolveTraceMessageMentions(m, fallbackMentions) {
  const perMsg = normalizeTraceMentions(m?.mentions, m?.content || '')
  if (perMsg.length > 0) return perMsg
  // 本轮 user_message span 的 mentions 仅对最后一条 user 消息有效
  if (m?.source === 'current' || m?._askUserRole === 'response') {
    return normalizeTraceMentions(fallbackMentions, m?.content || '')
  }
  return normalizeTraceMentions([], m?.content || '')
}

function shouldShowTraceMentions(content, mentions) {
  return normalizeTraceMentions(mentions, content).length > 0 || contentHasMentionTokens(content)
}

function copyAllLlmMessages() {
  const msgs = traceModelInput.value.llmMessages || []
  const text = msgs.map(m => {
    const role = roleLabel(m.role)
    const content = m.content || '（无文本内容）'
    return `[${role}]\n${content}`
  }).join('\n\n---\n\n')
  copyToClipboard(text, 'mi_msgs')
}

const filter = reactive({
  requestId: '',
  sessionId: null,
  status: undefined,
  timeRange: null,
})

// 工具调用状态
const toolLoading = ref(false)
const toolRecords = ref([])
const toolDetailVisible = ref(false)
const toolDetailRecord = ref(null)
const toolOutputCopied = ref(false)

const toolFilter = reactive({
  toolName: '',
  status: undefined,
  timeRange: null,
})

const toolPagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const toolColumns = [
  { title: '工具名称', key: 'toolName', width: 150 },
  { title: '状态', key: 'status', width: 80, align: 'center' },
  { title: '输入参数', key: 'toolInput', width: 220, ellipsis: true },
  { title: '输出结果', key: 'toolOutput', width: 300, ellipsis: true },
  { title: '时间', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 70, align: 'center' },
]

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const chatColumns = [
  { title: '时间', key: 'createTime', width: 170 },
  // 仅这三列在 bodyCell 里用 a-tooltip；此处不用 ellipsis:true，避免原生 title 双层 tooltip
  { title: 'Request ID', dataIndex: 'requestId', key: 'requestId', width: 160 },
  { title: 'Agent', dataIndex: 'agentName', key: 'agentName', width: 120 },
  { title: '模型', dataIndex: 'model', key: 'model', width: 140 },
  { title: 'Token (入/出)', key: 'totalTokens', width: 130 },
  { title: '耗时', key: 'totalDurationMs', width: 100 },
  { title: '工具', dataIndex: 'toolCallCount', width: 70, align: 'center' },
  { title: '状态', key: 'status', width: 80 },
  { title: '操作', key: 'action', width: 80 },
]

const workflowColumns = [
  { title: '时间', key: 'createTime', width: 170 },
  { title: 'Request ID', dataIndex: 'requestId', key: 'requestId', width: 160 },
  { title: 'Agent', dataIndex: 'agentName', key: 'agentName', width: 120 },
  { title: 'Token (入/出)', key: 'totalTokens', width: 130 },
  { title: '耗时', key: 'totalDurationMs', width: 100 },
  { title: '状态', key: 'status', width: 80 },
  { title: '操作', key: 'action', width: 80 },
]

const columns = computed(() => activeTab.value === 'workflow' ? workflowColumns : chatColumns)

function parseSpansFromDetail() {
  if (!detailTrace.value?.spans) return []
  try {
    const raw = detailTrace.value.spans
    return Array.isArray(raw) ? raw : (typeof raw === 'string' ? JSON.parse(raw) : [])
  } catch {
    return []
  }
}

// 共享的 parsedSpans computed，避免 traceModelInput 和 waterfallGroups 重复解析
const parsedSpans = computed(() => parseSpansFromDetail())

const traceModelInput = computed(() => {
  const spans = parsedSpans.value
  const userSpan = spans.find(s => s.name === 'user_message')
  const llmSpan = spans.find(s => s.name === 'messages_to_llm')
  const userAttrs = userSpan?.attributes || {}
  const llmAttrs = llmSpan?.attributes || {}
  const userAttachments = traceAttachments(userAttrs)
  const llmMessages = traceLlmMessages(llmAttrs)
  const systemPrompt = llmAttrs.systemPrompt || llmMessages.find(m => m.role === 'system')?.content || ''
  const requestConfig = llmAttrs.config || null
  const requestTools = llmAttrs.tools || []

  // 提取 ask_user 父子关联
  const askUserLinks = spans
    .filter(s => s.name === 'ask_user_link' && s.attributes)
    .map(s => ({
      parentMessageId: s.attributes.parentMessageId,
      childMessageId: s.attributes.childMessageId,
    }))

  return {
    hasData: !!(userAttrs.content || userAttachments.length || userAttrs.bizParams
      || systemPrompt || llmMessages.length || requestConfig || requestTools.length),
    userContent: userAttrs.content || '',
    userMentions: normalizeTraceMentions(userAttrs.mentions),
    userAttachments,
    bizParams: userAttrs.bizParams || null,
    systemPrompt,
    llmMessages: annotateAskUserRoles(llmMessages, userAttrs),
    requestConfig,
    requestTools,
    toolCount: llmAttrs.toolCount ?? requestTools.length,
    askUserLinks,
    currentUserMessageId: userAttrs.messageId || null,
    currentUserParentId: userAttrs.parentMessageId || null,
  }
})

const waterfallGroups = computed(() => {
  const spans = parsedSpans.value
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
    subagent_batch: 'SubAgent 批次',
    subagent_task: 'SubAgent 任务',
    rag_search: 'RAG检索',
    ai_reasoning: 'AI思考',
    ai_reply: 'AI回复',
    ask_user_link: 'ask_user 关联',
  }
  return map[name] || name
}

function traceHiddenAttrKeys(k) {
  return ['replyPreview', 'content', 'toolNames', 'attachments', 'mentions', 'messages', 'messageCount', 'systemPrompt', 'bizParams', 'config', 'tools', 'toolCount'].includes(k)
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

/**
 * 标注 ask_user 触发/回复角色：
 * - trigger: 包含 ask_user 工具调用的 assistant 消息
 * - response: 当前用户消息（有 parentMessageId 时的最后一条 user 消息）
 */
function annotateAskUserRoles(messages, userAttrs) {
  if (!messages || !messages.length) return messages
  const hasAskUserResponse = !!userAttrs?.parentMessageId
  if (!hasAskUserResponse) return messages

  return messages.map((m, i) => {
    const annotated = { ...m }
    // 检测是否为 ask_user 触发消息（assistant 消息的 toolEvents 中有 ask_user）
    // 注意：llmMessages 是发给模型的精简格式，不含 toolEvents
    // 通过位置推断：最后一条 user 消息之前的 assistant 消息中，最近的一条
    if (m.role === 'assistant') {
      // 检查下一条消息是否是 user（即当前用户回复）
      const nextMsg = messages[i + 1]
      if (nextMsg && nextMsg.role === 'user' && i === messages.length - 2) {
        annotated._askUserRole = 'trigger'
      }
    }
    if (m.role === 'user' && i === messages.length - 1) {
      annotated._askUserRole = 'response'
    }
    return annotated
  })
}

function spanTypeClass(name) {
  if (name === 'llm_call') return 'llm'
  if (name === 'tool_execute') return 'tool'
  if (name === 'subagent_batch' || name === 'subagent_task') return 'tool'
  if (name === 'rag_search') return 'rag'
  if (name === 'ai_reasoning') return 'reasoning'
  if (name === 'ai_reply') return 'reply'
  if (name === 'ask_user_link') return 'askuser'
  return 'other'
}

function toggleSpanDetail(group) {
  if (expandedSpans.has(group.spanId)) {
    expandedSpans.delete(group.spanId)
  } else {
    expandedSpans.add(group.spanId)
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
      traceSource: activeTab.value,
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
  statsLoading.value = true
  try {
    const res = await getTraceOverview(activeTab.value)
    overview.value = res.data || {}
  } catch { /* ignore */ } finally {
    statsLoading.value = false
  }
}

function onTabChange() {
  pagination.current = 1
  selectedRowKeys.value = []
  selectedToolRowKeys.value = []
  if (activeTab.value === 'tool') {
    loadToolCalls(1)
  } else {
    loadTraces(1)
    loadOverview()
  }
}

function handleRefresh() {
  filter.requestId = ''
  filter.sessionId = null
  filter.status = undefined
  filter.timeRange = null
  selectedRowKeys.value = []
  loadTraces(1)
  loadOverview()
}

function handleToolRefresh() {
  toolFilter.toolName = ''
  toolFilter.status = undefined
  toolFilter.timeRange = null
  selectedToolRowKeys.value = []
  loadToolCalls(1)
}

function handleTableChange(pag) {
  loadTraces(pag.current)
}

async function goToChat(sessionId) {
  if (!sessionId) return
  try {
    const res = await getSession(sessionId)
    const source = res.data?.source
    if (source === 'api' || source === 'automation') {
      // 集成/自动化会话只读：跳转会话管理对应 Tab
      const scope = source === 'api' ? 'api' : 'automation'
      router.push({ path: '/app/sessions', query: { scope, sessionId: String(sessionId) } })
      return
    }
  } catch {
    // 回退到对话页（平台会话或查询失败）
  }
  router.push(`/app/chat/${sessionId}`)
}

async function openDetail(record) {
  if (activeTab.value === 'workflow') {
    router.push(`/app/observability/workflow-trace/${record.id}`)
    return
  }
  detailVisible.value = true
  expandedSpans.clear()
  configModalVisible.value = false
  toolsModalVisible.value = false
  try {
    const res = await getTraceDetail(record.id)
    detailTrace.value = res.data
  } catch {
    detailTrace.value = record
  }
}

function formatDuration(ms) {
  if (!ms && ms !== 0) return '-'
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(1) + 's'
}

function traceStatusLabel(status) {
  if (status === 'completed') return '成功'
  if (status === 'failed') return '失败'
  return '运行中'
}

function traceStatusColor(status) {
  if (status === 'completed') return 'success'
  if (status === 'failed') return 'error'
  return 'processing'
}

function formatTokens(tokens) {
  if (!tokens && tokens !== 0) return '-'
  if (tokens >= 10000) return (tokens / 10000).toFixed(1) + 'w'
  return String(tokens)
}

// 工具调用方法
async function loadToolCalls(page) {
  toolLoading.value = true
  try {
    toolPagination.current = page || 1
    const params = {
      pageNum: toolPagination.current,
      pageSize: toolPagination.pageSize,
    }
    if (toolFilter.toolName?.trim()) params.toolName = toolFilter.toolName.trim()
    if (toolFilter.status) params.status = toolFilter.status
    if (toolFilter.timeRange?.length === 2) {
      params.startTime = toolFilter.timeRange[0].format('YYYY-MM-DD HH:mm:ss')
      params.endTime = toolFilter.timeRange[1].format('YYYY-MM-DD HH:mm:ss')
    }
    const res = await getToolCalls(params)
    toolRecords.value = res.data.records || []
    toolPagination.total = res.data.total || 0
  } finally {
    toolLoading.value = false
  }
}

function handleToolTableChange(pag) {
  loadToolCalls(pag.current)
}

function openToolDetail(record) {
  toolDetailRecord.value = record
  toolDetailVisible.value = true
}

async function copyToolOutput() {
  const text = toolDetailRecord.value?.toolOutput || ''
  if (!text) return
  await sharedCopy(text)
  toolOutputCopied.value = true
  message.success('已复制')
  setTimeout(() => { toolOutputCopied.value = false }, 1500)
}

function handleBatchDelete() {
  Modal.confirm({
    title: '确认批量删除',
    content: `即将删除 ${selectedRowKeys.value.length} 条调用链记录，删除后无法恢复。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteTraces(selectedRowKeys.value)
      message.success(`成功删除 ${selectedRowKeys.value.length} 条记录`)
      selectedRowKeys.value = []
      loadTraces(1)
      loadOverview()
    },
  })
}

function handleBatchDeleteTool() {
  Modal.confirm({
    title: '确认批量删除',
    content: `即将删除 ${selectedToolRowKeys.value.length} 条工具调用记录，删除后无法恢复。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteToolCalls(selectedToolRowKeys.value)
      message.success(`成功删除 ${selectedToolRowKeys.value.length} 条记录`)
      selectedToolRowKeys.value = []
      loadToolCalls(1)
    },
  })
}

function formatJsonPreview(jsonStr) {
  if (!jsonStr) return '-'
  try {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
    const str = JSON.stringify(obj)
    return str.length > 60 ? str.substring(0, 60) + '...' : str
  } catch {
    return jsonStr.length > 60 ? jsonStr.substring(0, 60) + '...' : jsonStr
  }
}

function truncate(str, len) {
  if (!str) return '-'
  return str.length > len ? str.substring(0, len) + '...' : str
}

function formatConfigValue(value) {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

onMounted(() => {
  activeTab.value = normalizeObservabilityTab(route.query.tab)
  onTabChange()
})

watch(activeTab, (tab) => {
  if (normalizeObservabilityTab(route.query.tab) !== tab) {
    router.replace({ query: { tab } })
  }
  onTabChange()
})

watch(() => route.query.tab, (tab) => {
  const normalized = normalizeObservabilityTab(tab)
  if (normalized !== activeTab.value) {
    activeTab.value = normalized
  }
})

onUnmounted(() => clearTimeout(copyTimer))
</script>

<style scoped>
.observability {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

/* 类型切换 Tab */
.trace-tabs {
  margin-bottom: 20px;
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
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
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
.total-icon { background: linear-gradient(135deg, var(--blue-500), var(--blue-700)); }
.token-icon { background: linear-gradient(135deg, var(--purple-700), var(--purple-800)); }
.duration-icon { background: linear-gradient(135deg, #13c2c2, #08979c); }
.tool-icon { background: linear-gradient(135deg, var(--color-warning), var(--color-warning-deep)); }
.stat-value { font-size: 22px; font-weight: 600; line-height: 1.2; }
.stat-label { font-size: 12px; color: var(--color-mute); margin-top: 2px; }

/* 筛选栏 */
.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}

/* 工具调用日志筛选 */
.tool-call-filter {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}
.tool-name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.cell-truncate {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--color-body);
}

/* Trace 列表：Request ID / Agent / 模型 截断展示，完整内容靠 a-tooltip */
.cell-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

/* Token 详情 */
.token-detail { font-size: 13px; }
.token-input { color: var(--color-link); }
.token-sep { color: var(--color-hairline-strong); margin: 0 3px; }
.token-output { color: var(--green-500); }

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
  color: var(--color-mute);
  border-bottom: 1px solid var(--color-hairline);
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
  color: var(--color-hairline-strong);
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
.wf-bar-llm { background: linear-gradient(90deg, var(--blue-500), var(--blue-400)); }
.wf-bar-tool { background: linear-gradient(90deg, var(--color-warning), var(--color-warning-deep)); }
.wf-bar-rag { background: linear-gradient(90deg, var(--green-500), var(--green-400)); }
.wf-bar-reasoning { background: linear-gradient(90deg, var(--purple-700), var(--purple-500)); }
.wf-bar-reply { background: linear-gradient(90deg, #13c2c2, #36cfc9); }
.wf-bar-askuser { background: linear-gradient(90deg, var(--color-warning), #ffa940); }
.wf-bar-other { background: linear-gradient(90deg, var(--gray-400), var(--gray-300)); }
.wf-duration {
  width: 70px;
  min-width: 70px;
  text-align: right;
  font-size: 12px;
  color: var(--color-body);
}
.wf-expand-icon { font-size: 10px; color: var(--color-mute); }
.wf-count { font-size: 10px; color: var(--color-warning); margin-left: 4px; font-weight: 500; }
.wf-bar-segment { border-right: 1px solid rgba(255,255,255,0.6); }
.waterfall-row { cursor: pointer; border-radius: 4px; }
.waterfall-row:hover { background: var(--color-canvas-soft); }
.waterfall-row.wf-active { background: var(--color-link-bg-soft); }

/* 行内 Span 详情 */
.span-inline-detail {
  padding: 12px 16px 12px 32px;
  background: var(--color-canvas-soft);
  border-left: 3px solid var(--color-link);
  margin: 0 0 4px 0;
  border-radius: 0 6px 6px 0;
  animation: slideDown 0.15s ease-out;
}
.span-sub-detail {
  padding: 8px 0;
  border-bottom: 1px dashed var(--color-hairline);
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
  color: var(--color-link);
  background: var(--color-info-bg);
  padding: 1px 6px;
  border-radius: 4px;
}
.sd-sub-time {
  font-size: 11px;
  color: var(--color-mute);
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
.sd-key { color: var(--color-mute); font-size: 12px; min-width: 50px; }
.sd-val { font-size: 12px; color: var(--color-ink); }
.sd-section { margin-top: 8px; }
.sd-section-title { font-size: 12px; color: var(--color-mute); margin-bottom: 4px; font-weight: 500; }
.sd-content-box {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.reasoning-box { color: var(--purple-700); background: var(--color-purple-bg); border-color: var(--purple-300); }
.sd-json {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
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
  background: var(--color-canvas-soft);
  border-radius: 8px;
}
.info-row { display: flex; align-items: center; gap: 8px; }
.info-label { color: var(--color-mute); font-size: 13px; min-width: 50px; }
.info-value { font-size: 13px; }
.error-row { grid-column: 1 / -1; align-items: flex-start; }
.error-content-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.error-text { color: var(--color-error); font-size: 12px; word-break: break-word; white-space: pre-wrap; }
.request-id-text { font-family: 'Geist Mono', Menlo, monospace; font-size: 12px; word-break: break-all; }
.btn-copy-inline { align-self: flex-start; }

.empty-spans {
  text-align: center;
  color: var(--color-hairline-strong);
  padding: 40px;
  font-size: 13px;
}

/* 用户提问与模型输入 */
.model-input-section {
  margin-bottom: 24px;
  padding: 16px;
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
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
  color: var(--color-ink);
  margin-bottom: 0;
}
.mi-block-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.mi-pre-wrap {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-ink);
}
.mi-pre {
  margin: 0;
  padding: 12px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
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
  color: var(--color-mute);
  margin-bottom: 4px;
}
.mi-msg {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--color-hairline);
}
.mi-msg:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}
.mi-msg.ask-user-trigger,
.mi-msg.ask-user-response {
  border-bottom-color: transparent;
}
.mi-msg-history {
  opacity: 0.75;
}
.mi-msg-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.mi-empty-text {
  font-size: 12px;
  color: var(--color-mute);
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
  color: var(--color-body);
  padding: 4px 10px;
  background: var(--color-canvas-soft-2);
  border-radius: 6px;
  max-width: 100%;
  word-break: break-all;
}

/* 请求配置与工具定义按钮 */
.request-detail-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-bottom: 20px;
}
/* 弹窗滚动区域：滚动由全局 .ant-modal-body 负责（modal-scroll.css），wrapper 只负责让滚动条与内容保持距离 */
.dialog-scroll-body .detail-pre {
  max-height: none;
  overflow: visible;
}

/* 请求配置 */
.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 8px;
}
.config-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
}
.config-key {
  font-size: 12px;
  color: var(--color-mute);
  min-width: 80px;
  font-weight: 500;
}
.config-val {
  font-size: 12px;
  color: var(--color-ink);
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  word-break: break-all;
}

/* 工具定义 */
.tool-def-search {
  margin-bottom: 12px;
}
.tool-def-item {
  margin-bottom: 10px;
  padding: 10px 12px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  transition: opacity 0.2s;
}
.tool-def-item.tool-def-dimmed {
  opacity: 0.4;
}
.tool-def-item:last-child { margin-bottom: 0; }
.tool-def-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tool-def-desc {
  font-size: 12px;
  color: var(--color-mute);
}
:deep(.tool-def-hl) {
  background: var(--color-warning-bg, #fff3cd);
  color: var(--color-warning, #d48806);
  padding: 0 1px;
  border-radius: 2px;
}

/* AI回复内容 */
.trace-llm-msg {
  margin-bottom: 10px;
}
.trace-llm-role {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-mute);
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
  color: var(--color-mute);
  margin-top: 4px;
  display: inline-block;
}

.reply-section { margin-bottom: 20px; }
.reply-section h4 { font-size: 15px; font-weight: 600; margin: 0 0 8px; }
.btn-copy {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  font-size: 12px;
  color: var(--color-mute);
  cursor: pointer;
  transition: all 0.15s;
}
.btn-copy:hover {
  border-color: var(--color-link);
  color: var(--color-link);
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
  color: var(--color-mute);
  cursor: pointer;
  transition: all 0.15s;
}
.btn-copy-sm:hover {
  background: var(--color-canvas-soft-2);
  color: var(--color-link);
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
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 14px 16px;
  font-size: 13px;
  line-height: 1.8;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 工具调用详情 */
.detail-section {
  margin-bottom: 16px;
}
.detail-label {
  font-size: 13px;
  color: var(--color-mute);
  margin-bottom: 6px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}
.btn-copy {
  appearance: none;
  border: none;
  background: none;
  cursor: pointer;
  padding: 2px;
  display: inline-flex;
  align-items: center;
  color: var(--color-mute);
  font-size: 13px;
  &:hover { color: var(--color-body); }
}

.detail-pre {
  background: var(--color-canvas-soft-2);
  border-radius: 6px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow: auto;
  margin: 0;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
}
.detail-pre-json {
  background: #1e1e1e;
  color: #d4d4d4;
}

/* ask_user 父子消息关联 */
.ask-user-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 12px;
  background: var(--color-warn-bg);
  border: 1px solid var(--color-warning-soft);
  border-radius: 6px;
  font-size: 12px;
  color: var(--color-warning-deep);
}
.ask-user-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: var(--color-warning);
  color: var(--color-on-primary);
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}
.ask-user-trigger {
  border-left: 3px solid var(--color-warning);
  padding-left: 12px;
  background: var(--color-warn-bg);
  border-radius: 0 6px 6px 0;
}
.ask-user-response {
  border-left: 3px solid #13c2c2;
  padding-left: 12px;
  margin-left: 16px;
  background: #e6fffb;
  border-radius: 0 6px 6px 0;
}
[data-theme="dark"] .ask-user-response {
  background: rgba(19, 194, 194, 0.12);
}
.ask-user-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 500;
}
.trigger-badge {
  background: var(--color-warn-bg-deep);
  color: var(--color-warning-deep);
}
.response-badge {
  background: #b5f5ec;
  color: #006d75;
}
[data-theme="dark"] .response-badge {
  background: rgba(19, 194, 194, 0.24);
  color: #5ae5e5;
}
</style>
