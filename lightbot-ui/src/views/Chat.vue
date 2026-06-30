<template>
  <div class="chat-container">
    <!-- 顶部栏 -->
    <div v-if="sessionId" class="chat-topbar">
      <div class="chat-topbar-left">
        <a-tooltip v-if="!titleEditing" title="修改标题">
          <div class="chat-topbar-title" @click="startTitleEdit">
            {{ sessionTitle || '新对话' }}
            <EditOutlined class="chat-topbar-title-icon" />
          </div>
        </a-tooltip>
        <div v-else class="chat-topbar-title-edit">
          <a-input
            ref="titleInputRef"
            v-model:value="titleEditValue"
            size="small"
            :maxlength="50"
            @press-enter="confirmTitleEdit"
            @blur="confirmTitleEdit"
            @keydown.esc="cancelTitleEdit"
          />
          <a-tooltip title="取消">
            <button
              class="btn-title-cancel"
              @mousedown.prevent
              @click="cancelTitleEdit"
            >
              <CloseOutlined />
            </button>
          </a-tooltip>
        </div>
      </div>
      <div class="chat-topbar-right">
        <a-tooltip title="会话文件">
          <a-badge :count="sessionFileCount" :overflow-count="99" :number-style="{ fontSize: '10px', boxShadow: 'none' }">
            <button class="btn-topbar-file" @click="openFileDrawer">
              <FolderOpenOutlined />
            </button>
          </a-badge>
        </a-tooltip>
      </div>
    </div>
    <!-- 消息列表 -->
    <div class="chat-messages" ref="messagesRef">
      <!-- 欢迎状态（新对话 + 无消息） -->
      <div v-if="!sessionId && messages.length === 0 && !loadingHistory" class="empty-state">
        <img src="/lightbot-logo-single.png" alt="LightBot" class="empty-logo" />
        <div class="welcome-content"><MarkdownPreview :content="currentWelcomeMessage" /></div>
        <!-- 推荐问题：全部展示 -->
        <div v-if="currentRecommendedQuestions.length > 0" class="recommended-questions">
          <button
            v-for="(q, qi) in currentRecommendedQuestions"
            :key="qi"
            class="btn-question"
            @click="input = q; $nextTick(() => $refs.inputRef?.focus())"
          >
            {{ q }}
          </button>
        </div>
        <!-- 无默认Agent提示 -->
        <div v-if="!selectedAgentId && agents.length > 0" class="no-default-hint">
          没有默认Agent，<router-link to="/app/agents">去创建</router-link>
        </div>
      </div>

      <!-- 加载更早的消息 -->
      <div v-if="hasMoreMessages && !streaming && initialLoadDone" class="load-more-area">
        <a-button size="small" :loading="loadingOlder" @click="loadOlderMessages">
          加载更早的消息
        </a-button>
      </div>

      <!-- 虚拟滚动消息列表 -->
      <div v-if="loadingHistory" class="history-loading">
        <LoadingOutlined spin class="history-loading-icon" />
      </div>
      <div
        v-else
        class="virtual-list-container"
        :style="{ height: virtualizer.getTotalSize() + 'px', position: 'relative' }"
      >
        <div
          v-for="virtualRow in virtualizer.getVirtualItems()"
          :key="virtualRow.key"
          :data-index="virtualRow.index"
          :ref="el => { if (el) virtualizer.measureElement(el) }"
          :style="{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            transform: `translateY(${virtualRow.start}px)`,
          }"
        >
          <div :class="['message', messages[virtualRow.index]?.role, { 'message-highlight': highlightMessageId === messages[virtualRow.index]?._id }]">
            <!-- AI 头像 -->
            <div v-if="messages[virtualRow.index]?.role === 'assistant'" class="message-avatar">
              <img v-if="currentAgent?.avatar" :src="currentAgent.avatar" alt="" class="message-avatar-img" />
              <span v-else class="message-avatar-initial">{{ (currentAgent?.name || 'A')[0].toUpperCase() }}</span>
            </div>
            <div class="message-body">
              <!-- 编辑模式：独立于 message-content-wrapper，占满整行 -->
              <div v-if="editingMessageId === messages[virtualRow.index]._id" class="edit-message-outer">
                <button class="btn-copy edit-btn" @click="cancelEdit" title="取消">
                  <CloseOutlined />
                </button>
                <div class="edit-message-box">
                  <a-textarea
                    ref="editInputRef"
                    v-model:value="editContent"
                    :auto-size="{ minRows: 2, maxRows: 8 }"
                    @keydown="handleEditKeydown"
                  />
                </div>
                <button
                  class="btn-copy edit-btn edit-btn-send"
                  :disabled="!editContent.trim() || loading"
                  @click="submitEdit"
                  title="发送"
                >
                  <SendOutlined />
                </button>
              </div>
              <div v-else class="message-content-wrapper" :class="{ 'user-message-stack': messages[virtualRow.index]?.role === 'user' }">
                <!-- 引用回复内容 -->
                <a-tooltip v-if="getReplyToInfo(messages[virtualRow.index])" :title="getReplyToInfo(messages[virtualRow.index]).content" placement="topLeft" :overlay-style="{ maxWidth: '480px' }">
                  <div class="reply-quote" :class="{ clickable: hasReplyTarget(messages[virtualRow.index]) }" @click.stop="scrollToMessage(messages[virtualRow.index]._replyToMessageId)">
                    <div class="reply-quote-bar"></div>
                    <div class="reply-quote-content">
                      <span class="reply-quote-role">{{ getReplyToInfo(messages[virtualRow.index]).role === 'assistant' ? 'AI' : '你' }}</span>
                      <span class="reply-quote-text">{{ getReplyToInfo(messages[virtualRow.index]).content }}</span>
                    </div>
                  </div>
                </a-tooltip>
                <!-- 用户附件 -->
                <div
                  v-if="messages[virtualRow.index]?.role === 'user' && getMsgAttachments(messages[virtualRow.index]).length && !messages[virtualRow.index]._sensitiveBlock"
                  class="user-message-attachments"
                >
                  <ChatAttachmentTile
                    v-for="(att, ai) in getMsgAttachments(messages[virtualRow.index])"
                    :key="att.id || ai"
                    :att="att"
                    :thumb-url="getAttThumbUrl(att)"
                    @preview="openAttachmentPreview"
                  />
                </div>
                <!-- 敏感词拦截提示 -->
                <div v-if="messages[virtualRow.index]?._sensitiveBlock" class="sensitive-block-alert" :class="messages[virtualRow.index]._sensitiveBlock">
                  <WarningOutlined class="sensitive-block-icon" />
                  <span class="sensitive-block-text">{{ messages[virtualRow.index].content }}</span>
                </div>
                <!-- 深度思考面板 -->
                <div v-if="messages[virtualRow.index]?._reasoningContent && !messages[virtualRow.index]._sensitiveBlock" class="reasoning-panel">
                  <div class="reasoning-header" @click="toggleReasoningExpand(virtualRow.index)">
                    <BulbOutlined class="reasoning-icon" />
                    <span class="reasoning-title">深度思考</span>
                    <LoadingOutlined v-if="messages[virtualRow.index]._streaming && !messages[virtualRow.index]._reasoningDone" class="reasoning-spinner" />
                    <RightOutlined :class="{ expanded: messages[virtualRow.index]._reasoningExpanded }" class="tool-expand-icon" />
                  </div>
                  <div v-show="messages[virtualRow.index]._reasoningExpanded" class="reasoning-content">{{ messages[virtualRow.index]._reasoningContent }}</div>
                </div>
                <!-- Skill 启用 -->
                <div v-if="getTopCapabilityEvents(messages[virtualRow.index]).length > 0 && !messages[virtualRow.index]._sensitiveBlock" class="capability-block-inline">
                  <AgentCapabilityPanel
                    :events="getTopCapabilityEvents(messages[virtualRow.index])"
                    :is-done="!messages[virtualRow.index]._streaming || messages[virtualRow.index]._toolsDone"
                    :default-expanded="true"
                    @heightChange="onCapabilityHeightChange"
                  />
                </div>
                <!-- 工作流节点执行 -->
                <div v-if="messages[virtualRow.index]?._workflowEvents?.length > 0 && !messages[virtualRow.index]._sensitiveBlock" class="workflow-block-inline">
                  <WorkflowNodesGroupComponent
                    :workflow-events="messages[virtualRow.index]._workflowEvents"
                    :is-done="!messages[virtualRow.index]._streaming"
                    :default-expanded="!!messages[virtualRow.index]._streaming"
                    :is-streaming="!!messages[virtualRow.index]._streaming"
                  />
                </div>
                <!-- 有工具事件：按 offset 位置插入工具块 -->
                <template v-if="!messages[virtualRow.index]._sensitiveBlock && messages[virtualRow.index]._toolEvents?.length > 0 && getToolBlockOffsets(messages[virtualRow.index]).length > 0">
                  <template v-for="(segment, si) in splitContentByOffsets(messages[virtualRow.index])" :key="si">
                    <div v-if="segment.type === 'text'" class="message-content">
                      <MarkdownPreview :content="segment.text" :finalized="isSegmentFinalized(messages[virtualRow.index], segment, si)" />
                    </div>
                    <div v-else-if="segment.type === 'tool'" class="tool-block-inline">
                      <AgentCapabilityPanel
                        v-if="getCapabilityEventsForOffset(messages[virtualRow.index], segment.offset).length > 0"
                        :events="getCapabilityEventsForOffset(messages[virtualRow.index], segment.offset)"
                        :is-done="isToolBlockDone(messages[virtualRow.index], segment.offset)"
                        :default-expanded="true"
                        @heightChange="onCapabilityHeightChange"
                      />
                      <ToolCallsGroupComponent
                        v-if="getPureToolEvents(getToolEventsForOffset(messages[virtualRow.index], segment.offset)).length > 0"
                        :tool-events="getPureToolEvents(getToolEventsForOffset(messages[virtualRow.index], segment.offset))"
                        :is-done="isToolBlockDone(messages[virtualRow.index], segment.offset)"
                        :default-expanded="true"
                        :message-index="virtualRow.index"
                        @heightChange="onCapabilityHeightChange"
                      />
                    </div>
                  </template>
                </template>
                <!-- 有工具事件但 offset 尚未到达 -->
                <template v-else-if="!messages[virtualRow.index]._sensitiveBlock && messages[virtualRow.index]._toolEvents?.length > 0">
                  <div v-if="messages[virtualRow.index].content" class="message-content"><MarkdownPreview :content="messages[virtualRow.index].content" :finalized="!messages[virtualRow.index]._streaming" /></div>
                  <div class="tool-block-inline">
                    <AgentCapabilityPanel
                      v-if="getInlineCapabilityEvents(messages[virtualRow.index]).length > 0"
                      :events="getInlineCapabilityEvents(messages[virtualRow.index])"
                      :is-done="messages[virtualRow.index]._toolsDone"
                      :default-expanded="true"
                      @heightChange="onCapabilityHeightChange"
                    />
                    <ToolCallsGroupComponent
                      v-if="getPureToolEvents(messages[virtualRow.index]._toolEvents).length > 0"
                      :tool-events="getPureToolEvents(messages[virtualRow.index]._toolEvents)"
                      :is-done="messages[virtualRow.index]._toolsDone"
                      :default-expanded="true"
                      :message-index="virtualRow.index"
                      @heightChange="onCapabilityHeightChange"
                    />
                  </div>
                </template>
                <!-- 无工具事件：正常渲染 -->
                <template v-else-if="!messages[virtualRow.index]._sensitiveBlock">
                  <div v-if="messages[virtualRow.index].content && messages[virtualRow.index].content !== '[附件]'" class="message-content">
                    <MentionTextRenderer
                      v-if="getMsgMentions(messages[virtualRow.index]).length"
                      :content="messages[virtualRow.index].content"
                      :mentions="getMsgMentions(messages[virtualRow.index])"
                      :finalized="!messages[virtualRow.index]._streaming"
                    />
                    <MarkdownPreview v-else :content="messages[virtualRow.index].content" :finalized="!messages[virtualRow.index]._streaming" />
                  </div>
                </template>
                <!-- 1.3 模型重试提示 -->
                <div v-if="messages[virtualRow.index]._errorRetry" class="error-retry-block">
                  <div class="error-retry-header">
                    <LoadingOutlined v-if="messages[virtualRow.index]._streaming" class="error-retry-icon" spin />
                    <WarningOutlined v-else class="error-retry-icon" />
                    <span class="error-retry-title">AI 连接异常，正在重试</span>
                    <span class="error-retry-count">{{ messages[virtualRow.index]._errorRetry.attempt }}/{{ messages[virtualRow.index]._errorRetry.maxRetries }}</span>
                  </div>
                  <div class="error-retry-message">{{ messages[virtualRow.index]._errorRetry.message }}</div>
                </div>
                <!-- 1.3 结构化错误事件：LLM 调用中断、工具异常等 -->
                <div v-if="messages[virtualRow.index]._error" class="error-block">
                  <div class="error-block-header">
                    <CloseCircleOutlined class="error-block-icon" />
                    <span class="error-block-title">AI 调用异常</span>
                    <span class="error-block-code">{{ messages[virtualRow.index]._error.code }}</span>
                  </div>
                  <div class="error-block-message">{{ messages[virtualRow.index]._error.message }}</div>
                </div>
                <!-- 操作按钮 -->
                <div
                  v-if="!messages[virtualRow.index]._streaming && (messages[virtualRow.index].content || messages[virtualRow.index]._error) && !messages[virtualRow.index]._sensitiveBlock"
                  class="message-actions"
                >
                  <a-tooltip
                    v-if="messages[virtualRow.index].role === 'assistant' && showTtsBtn"
                    :title="speakingMsgKey === virtualRow.index ? '停止朗读' : '朗读'"
                  >
                    <button
                      class="btn-copy"
                      :class="{ speaking: speakingMsgKey === virtualRow.index }"
                      @click="speakMessage(messages[virtualRow.index], virtualRow.index)"
                    >
                      <SoundOutlined />
                    </button>
                  </a-tooltip>
                  <a-tooltip
                    v-if="!isBackendErrorMessage(messages[virtualRow.index])"
                    :title="messages[virtualRow.index]._copied ? '已复制' : '复制'"
                  >
                    <button
                      class="btn-copy"
                      :class="{ copied: messages[virtualRow.index]._copied }"
                      @click="copyMessage(messages[virtualRow.index])"
                    >
                      <CheckOutlined v-if="messages[virtualRow.index]._copied" />
                      <CopyOutlined v-else />
                    </button>
                  </a-tooltip>
                  <a-tooltip v-if="messages[virtualRow.index].role === 'assistant' && canRegenerate(virtualRow.index)" title="重新生成">
                    <button class="btn-copy btn-action-text" :disabled="loading" @click="regenerateReply(virtualRow.index)">
                      <ReloadOutlined />
                    </button>
                  </a-tooltip>
                  <a-tooltip
                    v-if="messages[virtualRow.index].role === 'assistant' && messages[virtualRow.index]._requestId && !isBackendErrorMessage(messages[virtualRow.index])"
                    :title="messages[virtualRow.index]._requestIdCopied ? '已复制' : '复制 Request ID'"
                  >
                    <button
                      class="btn-copy btn-action-text"
                      :class="{ copied: messages[virtualRow.index]._requestIdCopied }"
                      @click="copyRequestId(messages[virtualRow.index])"
                    >
                      <CheckOutlined v-if="messages[virtualRow.index]._requestIdCopied" />
                      <NumberOutlined v-else />
                    </button>
                  </a-tooltip>
                  <a-tooltip
                    v-if="messages[virtualRow.index].role === 'assistant' && messages[virtualRow.index]._id && !isBackendErrorMessage(messages[virtualRow.index])"
                    :title="messages[virtualRow.index]._msgIdCopied ? '已复制' : '复制 Message ID'"
                  >
                    <button
                      class="btn-copy btn-action-text"
                      :class="{ copied: messages[virtualRow.index]._msgIdCopied }"
                      @click="copyMessageId(messages[virtualRow.index])"
                    >
                      <CheckOutlined v-if="messages[virtualRow.index]._msgIdCopied" />
                      <TagOutlined v-else />
                    </button>
                  </a-tooltip>
                  <a-tooltip v-if="messages[virtualRow.index].role === 'assistant' && !isBackendErrorMessage(messages[virtualRow.index])" title="查看原始内容">
                    <button
                      class="btn-copy"
                      @click="openRawModal(virtualRow.index)"
                    >
                      <EyeOutlined />
                    </button>
                  </a-tooltip>
                  <a-tooltip
                    v-if="messages[virtualRow.index].role === 'user' && !loading && isLastUserMessage(virtualRow.index) && !messages[virtualRow.index]._replyToMessageId"
                    title="编辑"
                  >
                    <button class="btn-copy" @click="startEdit(virtualRow.index)">
                      <EditOutlined />
                    </button>
                  </a-tooltip>
                  <a-tooltip v-if="messages[virtualRow.index].role === 'assistant' && !isBackendErrorMessage(messages[virtualRow.index])" title="引用回复">
                    <button class="btn-copy" @click="startReply(virtualRow.index)">
                      <CommentOutlined />
                    </button>
                  </a-tooltip>
                  <!-- 消息反馈：点赞/踩 -->
                  <template v-if="messages[virtualRow.index].role === 'assistant' && messages[virtualRow.index]._id && !messages[virtualRow.index]._streaming">
                    <a-tooltip title="有帮助">
                      <button
                        class="btn-copy btn-feedback"
                        :class="{ 'feedback-liked': getMessageFeedbackType(messages[virtualRow.index]) === 'like' }"
                        @click="handleMessageFeedback(messages[virtualRow.index], 'like')"
                      >
                        <LikeFilled v-if="getMessageFeedbackType(messages[virtualRow.index]) === 'like'" />
                        <LikeOutlined v-else />
                      </button>
                    </a-tooltip>
                    <a-tooltip title="无帮助">
                      <button
                        class="btn-copy btn-feedback"
                        :class="{ 'feedback-disliked': getMessageFeedbackType(messages[virtualRow.index]) === 'dislike' }"
                        @click="showDislikeModal(messages[virtualRow.index])"
                      >
                        <DislikeFilled v-if="getMessageFeedbackType(messages[virtualRow.index]) === 'dislike'" />
                        <DislikeOutlined v-else />
                      </button>
                    </a-tooltip>
                  </template>
                  <a-tooltip v-if="!isBackendErrorMessage(messages[virtualRow.index])" :title="messages[virtualRow.index]._starred ? '取消收藏' : '收藏'">
                    <button class="btn-copy" :class="{ starred: messages[virtualRow.index]._starred }" @click="toggleStarMessage(virtualRow.index)">
                      <StarFilled v-if="messages[virtualRow.index]._starred" />
                      <StarOutlined v-else />
                    </button>
                  </a-tooltip>
                  <a-tooltip title="删除">
                    <button
                      class="btn-copy btn-delete"
                      @click="handleDeleteMessage(virtualRow.index)"
                    >
                      <DeleteOutlined />
                    </button>
                  </a-tooltip>
                  <span v-if="messages[virtualRow.index]._createTime" class="message-time">发表于 {{ formatTime(messages[virtualRow.index]._createTime) }}</span>
                </div>
              </div>
              <!-- RAG引用列表 -->
              <div v-if="messages[virtualRow.index]?.role === 'assistant' && getMsgRagRefs(messages[virtualRow.index]).length > 0 && !messages[virtualRow.index]._streaming" class="rag-references">
                <div class="rag-header" @click="toggleRefsSection(messages[virtualRow.index])">
                  <RightOutlined :class="{ expanded: isRefsSectionExpanded(messages[virtualRow.index]) }" />
                  <FileTextOutlined />
                  <span>参考文献 ({{ getMsgRagRefs(messages[virtualRow.index]).length }})</span>
                </div>
                <div v-if="isRefsSectionExpanded(messages[virtualRow.index])" class="rag-list">
                  <div v-for="(ref, ri) in getMsgRagRefs(messages[virtualRow.index])" :key="ri" class="rag-item">
                    <div class="rag-item-header" @click="toggleReference(messages[virtualRow.index], ri)">
                      <div class="rag-title-left">
                        <RightOutlined :class="{ expanded: isReferenceExpanded(messages[virtualRow.index], ri) }" />
                        <template v-if="ref.sourceType === 'qa_pair'">
                          <a-tag color="success" class="rag-qa-tag">问答对</a-tag>
                          <span class="rag-doc-name">{{ getRagQaQuestion(ref) }}</span>
                        </template>
                        <span v-else class="rag-doc-name">{{ ref.documentName }}</span>
                      </div>
                      <div class="rag-title-right">
                        <span class="rag-score">{{ (ref.score * 100).toFixed(1) }}%</span>
                        <a-tooltip v-if="ref.knowledgeId" title="查看知识库">
                          <LinkOutlined class="rag-nav-btn" @click.stop="goToKnowledge(ref.knowledgeId, ref.documentId)" />
                        </a-tooltip>
                      </div>
                    </div>
                    <div v-if="isReferenceExpanded(messages[virtualRow.index], ri)" class="rag-item-content">
                      {{ ref.contentPreview }}
                    </div>
                  </div>
                </div>
              </div>
              <!-- 耗时显示 -->
              <div v-if="messages[virtualRow.index]?.role === 'assistant' && virtualRow.index === messages.length - 1 && !messages[virtualRow.index]._streaming && lastReplyElapsed !== null" class="reply-elapsed">
                {{ formatElapsed(lastReplyElapsed) }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 加载中（等待第一个 chunk） -->
      <div v-if="loading && !streaming" class="message assistant">
        <div class="message-body">
          <div class="message-content status-content">
            <div class="status-loading">
              <span class="status-spinner"></span>
              <span class="status-text">{{ currentStatus || '正在思考...' }}</span>
            </div>
          </div>
        </div>
      </div>
      <!-- 流式输出中但尚未创建助手消息时显示加载动画（避免与消息列表中的助手气泡重复） -->
      <div v-if="loading && streaming && !hasStreamContent && !hasStreamingAssistantMessage" class="message assistant">
        <div class="message-body">
          <div class="message-content status-content">
            <div class="status-loading">
              <span class="status-spinner"></span>
              <span class="status-text">{{ currentStatus || '正在思考...' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="chat-input-wrapper">
      <div class="chat-input-shell">
        <!-- 切换会话加载遮罩 -->
        <div v-if="switchingSession" class="toolbar-loading-mask">
          <LoadingOutlined spin class="toolbar-loading-icon" />
        </div>
        <div class="chat-input-toolbar">
          <!-- Agent 列表为空时显示气泡引导 -->
          <a-popover v-if="agents.length === 0" trigger="click" placement="topLeft">
            <template #content>
              <div class="empty-agent-tip">
                系统里还没有智能体，<router-link to="/app/agents">点击创建智能体</router-link>
              </div>
            </template>
            <button type="button" class="btn-agent">
              <RobotOutlined />
            </button>
          </a-popover>
          <!-- Agent 列表不为空时正常下拉 -->
          <a-dropdown v-else :trigger="['click']" placement="topLeft">
            <a-tooltip :title="currentAgent?.name || '选择 Agent'">
              <button type="button" class="btn-agent">
                <RobotOutlined v-if="!currentAgent" />
                <img v-else-if="currentAgent.avatar" :src="currentAgent.avatar" alt="" class="btn-agent-avatar" />
                <span v-else class="btn-agent-initial">{{ currentAgent.name[0] }}</span>
              </button>
            </a-tooltip>
            <template #overlay>
              <a-menu @click="handleAgentSelect" :selectedKeys="selectedAgentId ? [String(selectedAgentId)] : []">
                <a-menu-item v-for="a in agents" :key="String(a.id)">
                  <div class="agent-menu-item">
                    <img v-if="a.avatar" :src="a.avatar" alt="" class="agent-menu-icon" />
                    <span v-else class="agent-menu-icon">{{ a.name[0] }}</span>
                    <span class="agent-menu-name">{{ a.name }}</span>
                    <span v-if="agentVersionLabel(a)" class="agent-version-tag">{{ agentVersionLabel(a) }}</span>
                    <span v-if="a.isDefault" class="agent-default-tag">默认</span>
                  </div>
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <span v-if="currentAgent?.name" class="chat-toolbar-agent-name">{{ currentAgent.name }}</span>
          <a-select
            v-if="selectedAgentId && configVersionOptions.length > 0"
            v-model:value="selectedConfigVersion"
            class="config-version-select"
            :disabled="loading"
            popup-class-name="config-version-select-dropdown"
            @change="onConfigVersionChange"
          >
            <a-select-option
              v-for="opt in configVersionOptions"
              :key="String(opt.value)"
              :value="opt.value"
              :label="opt.selectLabel"
            >
              <span class="version-option-row">
                <span class="version-option-num">{{ opt.versionLabel }}</span>
                <a-tag v-if="opt.badge === 'draft'" class="version-status-tag draft" :bordered="false">草稿</a-tag>
                <a-tag v-else-if="opt.badge === 'online'" class="version-status-tag online" color="success" :bordered="false">线上</a-tag>
              </span>
            </a-select-option>
          </a-select>
          <a-tooltip v-if="sessionTokenCount > 0" :title="`本次会话累计消耗 ${sessionTokenCount.toLocaleString()} tokens`">
            <div class="token-pill">
              <ThunderboltOutlined class="token-pill-icon" />
              <span class="token-pill-value">{{ formatTokenCount(sessionTokenCount) }}</span>
              <span class="token-pill-label">tokens</span>
            </div>
          </a-tooltip>
        </div>
        <!-- 引用回复预览条 -->
        <div v-if="replyTo.active" class="reply-preview-bar">
          <a-tooltip :title="replyTo.content" placement="topLeft" :mouseEnterDelay="0.3" :overlay-style="{ maxWidth: '520px' }">
            <div class="reply-preview-content">
              <span class="reply-preview-text">{{ replyTo.content }}</span>
            </div>
          </a-tooltip>
          <button class="reply-preview-close" @click="cancelReply">
            <CloseOutlined />
          </button>
        </div>
        <div class="chat-input">
          <input
            ref="fileInputRef"
            type="file"
            class="hidden-file-input"
            :accept="fileAcceptTypes"
            @change="onFileSelected"
          />
          <a-tooltip
            v-if="showFileUploadBtn"
            overlay-class-name="no-flip-tooltip chat-upload-tooltip"
            :overlay-style="{ maxWidth: '360px' }"
          >
            <template #title>
              <span class="chat-upload-hint">{{ fileUploadHint || '上传附件' }}</span>
            </template>
            <button
              type="button"
              class="btn-attach"
              :class="{ 'btn-attach--uploading': uploading }"
              :disabled="loading || uploading"
              @click="triggerFileUpload"
            >
              <LoadingOutlined v-if="uploading" spin />
              <PaperClipOutlined v-else />
            </button>
          </a-tooltip>
          <ChatMentionInput
            ref="inputRef"
            v-model="input"
            :agent-id="selectedAgentId"
            :agent-version-id="selectedAgentVersionId"
            :disabled="loading"
            placeholder="输入消息... (Enter 发送, Shift+Enter 换行, @ 提及资源)"
            @send="sendMessage"
          />
          <div class="chat-input-actions">
            <div v-if="voiceListening" class="voice-listening-indicator">
              <VoiceMicVisualizer :active="voiceListening" />
              <span class="voice-listening-text">聆听中</span>
            </div>
            <a-tooltip v-if="showVoiceInputBtn" title="语音转文字">
              <button
                type="button"
                class="btn-voice"
                :class="{ listening: voiceListening }"
                :disabled="loading"
                @click="toggleVoiceInput"
              >
                <AudioOutlined />
              </button>
            </a-tooltip>
            <button
              v-if="loading"
              class="btn-stop"
              @click="stopGenerating"
              title="停止生成"
            >
              <PauseCircleOutlined />
            </button>
            <button
              v-else
              class="btn-send"
              :disabled="!canSend"
              @click="sendMessage"
            >
              <SendOutlined />
            </button>
          </div>
        </div>
      </div>
      <div v-if="uploading || pendingAttachments.length > 0" class="pending-attachments">
        <span class="pending-att-count">
          <template v-if="uploading">附件上传中…</template>
          <template v-else>已选 {{ pendingAttachments.length }} 个附件</template>
        </span>
        <div class="pending-att-thumbs">
          <ChatAttachmentTile
            v-if="uploading"
            :att="{ type: 'uploading', fileName: '上传中' }"
            uploading
          />
          <ChatAttachmentTile
            v-for="(att, i) in pendingAttachments"
            :key="att.id || i"
            :att="att"
            :thumb-url="getAttThumbUrl(att)"
            removable
            @preview="openAttachmentPreview"
            @remove="removeAttachment(i)"
          />
        </div>
      </div>
      <div class="input-hint">LightBot 可能会犯错，请核实重要信息。</div>
    </div>

    <ChatAttachmentPreview
      v-model:open="attachmentPreviewOpen"
      :attachment="attachmentPreviewAtt"
    />

    <!-- 原始内容弹窗 -->
    <a-modal
      v-model:open="rawModal.visible"
      :footer="null"
      width="680px"
      :bodyStyle="{ maxHeight: '70vh', overflow: 'auto' }"
    >
      <template #title>
        <span>{{ rawModal.title }}</span>
        <a-tooltip title="复制">
          <button class="raw-modal-meta-btn" @click="copyRawContent" style="margin-left:12px;">
            <CheckOutlined v-if="rawModal.copied" style="color:#16a34a;" />
            <CopyOutlined v-else />
          </button>
        </a-tooltip>
        <a-tooltip v-if="rawModal.metadata" title="查看 Metadata">
          <button class="raw-modal-meta-btn" @click="openMetadataModal">
            <CodeOutlined />
          </button>
        </a-tooltip>
      </template>
      <pre class="raw-modal-content">{{ rawModal.content }}</pre>
    </a-modal>

    <!-- Metadata 弹窗 -->
    <a-modal
      v-model:open="metadataModal.visible"
      :footer="null"
      width="680px"
      :bodyStyle="{ maxHeight: '70vh', overflow: 'auto' }"
    >
      <template #title>
        <span>Metadata</span>
        <button class="raw-modal-meta-btn" @click="copyMetadata" style="margin-left:12px;">
          <CheckOutlined v-if="metadataModal.copied" style="color:#16a34a;" />
          <CopyOutlined v-else />
        </button>
      </template>
      <pre class="raw-modal-content">{{ metadataModal.json }}</pre>
    </a-modal>

    <!-- Ask User 弹窗 -->
    <a-modal
      v-model:open="askUserModal.visible"
      title="AI 向您提问"
      :footer="null"
      :maskClosable="false"
      width="520px"
    >
      <div style="padding:8px 0;">
        <div style="display:flex;align-items:flex-start;gap:10px;padding:14px 16px;background:#dbeafe;border:1px solid #93c5fd;border-radius:10px;font-size:14px;line-height:1.7;color:#1e40af;margin-bottom:16px;">
          <QuestionCircleOutlined style="color:#2563eb;font-size:18px;margin-top:2px;flex-shrink:0;" />
          <span style="font-weight:500;">{{ askUserModal.question }}</span>
        </div>
        <div v-if="askUserModal.options.length > 0" style="display:flex;flex-direction:column;gap:8px;margin-bottom:16px;">
          <button v-for="(opt, i) in askUserModal.options" :key="i" @click="submitAskUserResponse(opt)"
            style="display:flex;align-items:center;gap:10px;padding:12px 16px;background:#fff;border:1px solid #d4d4d8;border-radius:10px;font-size:14px;color:var(--color-primary);cursor:pointer;transition:all 0.15s;text-align:left;width:100%;"
            onmouseover="this.style.borderColor='#0070f3';this.style.background='#f0f7ff'"
            onmouseout="this.style.borderColor='#d4d4d8';this.style.background='#fff'">
            <span style="display:inline-flex;align-items:center;justify-content:center;min-width:26px;height:26px;background:#3b82f6;color:#fff;border-radius:50%;font-size:12px;font-weight:600;flex-shrink:0;">{{ i + 1 }}</span>
            <span style="flex:1;line-height:1.5;">{{ opt }}</span>
          </button>
        </div>
        <div style="display:flex;gap:8px;align-items:flex-end;">
          <a-textarea
            v-model:value="askUserModal.freeText"
            :placeholder="askUserModal.options.length > 0 ? '或者输入自定义回答...' : '请输入您的回答...'"
            :auto-size="{ minRows: 2, maxRows: 4 }"
            @keydown.enter.ctrl="submitAskUserResponse(askUserModal.freeText)"
            style="flex:1;"
          />
          <a-button type="primary" :disabled="!askUserModal.freeText.trim()" @click="submitAskUserResponse(askUserModal.freeText)">
            发送
          </a-button>
        </div>
        <div style="text-align:right;margin-top:6px;font-size:11px;color:#a1a1aa;">Ctrl+Enter 发送</div>
      </div>
    </a-modal>

    <!-- Dislike 原因弹窗 -->
    <a-modal
      v-model:open="dislikeModalVisible"
      title="反馈原因（可选）"
      @ok="submitDislikeReason"
      @cancel="skipDislikeReason"
      okText="提交"
      cancelText="跳过"
      :maskClosable="false"
      :closable="false"
      :keyboard="false"
      width="420px"
    >
      <div style="padding: 4px 0;">
        <a-textarea
          v-model:value="dislikeReason"
          placeholder="请描述问题所在，帮助我们改进回答质量..."
          :auto-size="{ minRows: 3, maxRows: 6 }"
        />
      </div>
    </a-modal>
  </div>

  <!-- 会话文件列表抽屉 -->
  <a-drawer
    v-model:open="fileDrawerOpen"
    title="会话文件"
    :width="520"
    :mask-closable="true"
    @afterOpenChange="onFileDrawerOpened"
  >
    <template #extra>
      <a-tooltip title="刷新">
        <button
          class="btn-drawer-refresh"
          :class="{ refreshing: fileDrawerLoading }"
          :disabled="fileDrawerLoading"
          @click="refreshSessionFiles"
        >
          <ReloadOutlined :spin="fileDrawerLoading" />
        </button>
      </a-tooltip>
    </template>
    <div v-if="fileDrawerLoading" class="file-drawer-loading">
      <LoadingOutlined spin class="file-drawer-loading-icon" />
    </div>
    <div v-else-if="sessionAttachments.length === 0" class="file-drawer-empty">
      <FileTextOutlined class="file-drawer-empty-icon" />
      <p>暂无文件</p>
      <p class="file-drawer-empty-hint">上传附件或让 AI 生成文件后会出现在这里</p>
    </div>
    <div v-else class="file-drawer-list">
      <div v-if="userUploads.length > 0" class="file-drawer-section">
        <div class="file-drawer-section-title">用户上传 ({{ userUploads.length }})</div>
        <div class="file-drawer-grid">
          <ChatAttachmentTile
            v-for="(att, i) in userUploads"
            :key="att.id || i"
            :att="att"
            :thumb-url="getAttThumbUrl(att)"
            @preview="openSessionFilePreview"
          />
        </div>
      </div>
      <div v-if="aiFiles.length > 0" class="file-drawer-section">
        <div class="file-drawer-section-title">AI 生成 ({{ aiFiles.length }})</div>
        <div class="file-drawer-grid">
          <ChatAttachmentTile
            v-for="(att, i) in aiFiles"
            :key="att.id || i"
            :att="att"
            :thumb-url="getAttThumbUrl(att)"
            @preview="openSessionFilePreview"
          />
        </div>
      </div>
    </div>
  </a-drawer>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick, watch, computed, provide } from 'vue'
import { useVirtualizer } from '@tanstack/vue-virtual'
import { useRoute, useRouter } from 'vue-router'
import { SendOutlined, CopyOutlined, CheckOutlined, RobotOutlined, FileTextOutlined, RightOutlined, LinkOutlined, PauseCircleOutlined, LoadingOutlined, CheckCircleOutlined, BulbOutlined, WarningOutlined, PaperClipOutlined, AudioOutlined, CloseOutlined, PlayCircleOutlined, EyeOutlined, SoundOutlined, ReloadOutlined, NumberOutlined, TagOutlined, DeleteOutlined, QuestionCircleOutlined, CodeOutlined, EditOutlined, CommentOutlined, ThunderboltOutlined, LikeOutlined, DislikeOutlined, LikeFilled, DislikeFilled, StarOutlined, StarFilled, CloseCircleOutlined, FolderOpenOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { chatStream, refreshChatAttachmentPreviews, submitMessageFeedback, getMessageFeedback, batchGetMessageFeedbacks } from '../api/chat'
import { validatePendingAttachmentMix } from '../utils/chatAttachment'
import { enrichVideoThumbnails } from '../utils/videoThumbnail'
import { getSessionMessages, getSession, createSession, getSessionTitle, updateSessionTitle, deleteMessage as deleteMessageApi, toggleMessageStar, getSessionAttachments } from '../api/chatSession'
import { useUserStore } from '../stores/user'
import { safeJsonParse } from '../utils/request'
import { copyToClipboard } from '../utils/clipboard'
import { formatTime } from '../utils/format'
import MarkdownPreview from '../components/MarkdownPreview.vue'
import ToolCallsGroupComponent from '../components/ToolCallsGroupComponent.vue'
import WorkflowNodesGroupComponent from '../components/WorkflowNodesGroupComponent.vue'
import AgentCapabilityPanel from '../components/AgentCapabilityPanel.vue'
import ChatAttachmentPreview from '../components/ChatAttachmentPreview.vue'
import ChatAttachmentTile from '../components/ChatAttachmentTile.vue'
import VoiceMicVisualizer from '../components/VoiceMicVisualizer.vue'
import ChatMentionInput from '../components/ChatMentionInput.vue'
import MentionTextRenderer from '../components/MentionTextRenderer.vue'
import { useChatAgents } from '../composables/useChatAgents'
import { useChatAttachments } from '../composables/useChatAttachments'
import { useVoiceIO } from '../composables/useVoiceIO'
import { useAskUser } from '../composables/useAskUser'
import { useStreamSmoother } from '../composables/useStreamSmoother'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const input = ref('')
const inputHistory = ref([])
const historyIndex = ref(-1)
const loading = ref(false)
const streaming = ref(false)
const messages = ref([])
const sessionTokenCount = ref(0)
const messagesRef = ref(null)
const inputRef = ref(null)
const skipNextWatch = ref(false)
const loadingHistory = ref(false)

// 编辑重发状态
const editingMessageId = ref(null)
const editContent = ref('')
const editInputRef = ref(null)
const replyTo = reactive({ active: false, messageId: null, content: '', role: '' })
const highlightMessageId = ref(null)
const messagePage = ref(1)
const hasMoreMessages = ref(false)
const loadingOlder = ref(false)
const initialLoadDone = ref(false)
const currentStatus = ref('')
const reconnecting = ref(false)
const lastReplyElapsed = ref(null)
let sendStartTime = 0
const hasStreamContent = ref(false)
/** 原始内容弹窗状态 */
const rawModal = reactive({ visible: false, content: '', title: '', metadata: null, copied: false })
/** Metadata 弹窗状态 */
const metadataModal = reactive({ visible: false, json: '', copied: false })
/** 竞态保护：每次 loadHistory 递增，过期请求不写入状态 */
let loadHistoryRequestId = 0
/** 切换会话时 agent/版本加载中 */
const switchingSession = ref(false)

// ===== 虚拟滚动 =====
const isNearBottom = ref(true)
/** 用户主动上划，暂停流式自动滚动 */
const userScrolledUp = ref(false)

// ===== Composables =====
const sessionId = computed(() => route.params.sessionId || null)
const pendingAttachments = ref([])
const fileInputRef = ref(null)
const uploading = ref(false)
const attachmentPreviewOpen = ref(false)
const attachmentPreviewAtt = ref(null)
const voiceListening = ref(false)
const speakingMsgKey = ref(null)

// ===== 顶部栏 & 文件抽屉 =====
const sessionTitle = ref('')
const titleEditing = ref(false)
const titleEditValue = ref('')
const titleInputRef = ref(null)
const sessionAttachments = ref([])
const fileDrawerOpen = ref(false)
const fileDrawerLoading = ref(false)
const fileDrawerLoadedOnce = ref(false)
const sessionFileCount = computed(() => sessionAttachments.value.length)
const userUploads = computed(() => sessionAttachments.value.filter(a => a.source === 'user_upload'))
const aiFiles = computed(() => sessionAttachments.value.filter(a => a.source === 'ai_generated'))

// Agent 管理（chatCapabilities 在此 composable 内部创建）
const {
  agents, selectedAgentId, currentAgent, chatCapabilities,
  selectedConfigVersion, selectedAgentVersionId, configVersionOptions,
  showFileUploadBtn, showVoiceInputBtn, showTtsBtn,
  fileAcceptTypes, fileUploadHint,
  currentWelcomeMessage, currentRecommendedQuestions,
  handleAgentSelect, loadChatCapabilities, onConfigVersionChange,
  loadAgentConfigVersions, loadCurrentAgent, loadAgents, agentVersionLabel,
} = useChatAgents({
  sessionId, loading, pendingAttachments, voiceListening, stopVoiceInput: () => { voiceListening.value = false },
})

// 附件管理
const {
  getAttThumbUrl, openAttachmentPreview,
  triggerFileUpload, onFileSelected, removeAttachment,
} = useChatAttachments({
  selectedAgentId, sessionId, chatCapabilities, pendingAttachments,
  fileInputRef, uploading, attachmentPreviewOpen, attachmentPreviewAtt,
})

// 语音 I/O
const {
  toggleVoiceInput, stopVoiceInput,
  speakMessage, messagePlainText, cleanup: voiceCleanup,
} = useVoiceIO({
  input, inputRef, chatCapabilities, autoResize,
  voiceListening, speakingMsgKey,
})

const virtualizer = useVirtualizer({
  count: messages.value.length,
  getScrollElement: () => messagesRef.value,
  estimateSize: (index) => {
    const msg = messages.value[index]
    if (!msg) return 80
    if (msg.role === 'user') return msg._replyToMessageId ? 90 : 60
    const len = msg.content?.length || 0
    let size = Math.max(80, Math.min(600, Math.ceil(len / 40) * 22 + 60))
    // 工具事件额外占高：每个 tool_call/tool_result 约 32px，tool_status 约 24px
    const toolCount = (msg._toolEvents || []).filter(e => e.type === 'tool_call' || e.type === 'tool_result').length
    const statusCount = (msg._toolEvents || []).filter(e => e.type === 'tool_status').length
    if (toolCount > 0 || statusCount > 0) {
      size += 60 + toolCount * 32 + statusCount * 24
    }
    // 参考文献额外占高（默认展开，记录的为收起状态）
    const refCount = getMsgRagRefs(msg).length
    if (refCount > 0 && !refsSectionExpandedMap.value.has(index)) size += 40 + refCount * 36
    else if (refCount > 0) size += 32
    return Math.min(size, 2000)
  },
  overscan: 5,
})

watch(() => messages.value.length, (newLen, oldLen) => {
  virtualizer.value.setOptions({
    ...virtualizer.value.options,
    count: newLen,
  })
})

function handleScroll() {
  const el = messagesRef.value
  if (!el) return
  const threshold = 150
  const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < threshold
  isNearBottom.value = nearBottom
  // 流式输出期间：上划暂停自动滚动，回到底部恢复
  if (streaming.value) {
    userScrolledUp.value = !nearBottom
  }
}

function onCapabilityHeightChange(evt) {
  const rowEl = evt?.target?.closest?.('[data-index]')
  if (!rowEl) return
  const container = messagesRef.value
  if (container) container.style.overflowAnchor = 'none'
  virtualizer.value.measureElement(rowEl)
  nextTick(() => { if (container) container.style.overflowAnchor = '' })
}

// ===== Ask User 弹窗 =====
const {
  askUserModal, findAskUserEvent, isAskUserUnanswered, showAskUserModal,
} = useAskUser({ messages })

// 10.1 流式输出平滑缓冲：消除 token 不均匀到达导致的界面闪烁
let currentStreamingMsg = null
const streamSmoother = useStreamSmoother({
  onFlush: (text) => {
    if (currentStreamingMsg) {
      clearErrorRetry(currentStreamingMsg)
      currentStreamingMsg.content += text
      scrollToBottom()
    }
  },
})

function clearErrorRetry(msg) {
  if (msg?._errorRetry) {
    msg._errorRetry = null
  }
}

async function submitAskUserResponse(answer) {
  if (!answer?.trim()) return
  askUserModal.visible = false
  const text = answer.trim()
  messages.value.push({ role: 'user', content: text, _attachments: [] })
  isNearBottom.value = true
  userScrolledUp.value = false
  scrollToBottom()
  await runChatStream({ message: text, attachments: [], regenerate: false })
}

function scrollToBottom() {
  if (!isNearBottom.value || userScrolledUp.value) return
  const el = messagesRef.value
  if (!el) return
  nextTick(() => {
    el.scrollTop = el.scrollHeight
  })
}

function formatTokenCount(tokens) {
  if (!tokens) return '0'
  if (tokens >= 10000) return (tokens / 10000).toFixed(1) + '万'
  return tokens.toLocaleString()
}

/** 流式输出期间，自动滚动深度思考面板到底部 */
function scrollReasoningToBottom() {
  if (userScrolledUp.value) return
  nextTick(() => {
    const panels = messagesRef.value?.querySelectorAll('.reasoning-content')
    if (panels?.length) {
      const last = panels[panels.length - 1]
      last.scrollTop = last.scrollHeight
    }
  })
}

/** 强制滚动到底部（切换会话后使用，不检查 isNearBottom）
 *  多次延迟滚动，确保工具面板、参考文献等延迟渲染内容撑开后仍能定位到底部
 */
function forceScrollToBottom() {
  const el = messagesRef.value
  if (!el) return
  const doScroll = () => { el.scrollTop = el.scrollHeight }
  nextTick(() => {
    doScroll()
    requestAnimationFrame(doScroll)
    setTimeout(doScroll, 100)
    setTimeout(doScroll, 300)
    setTimeout(doScroll, 600)
  })
}

/**
 * 展开/折叠内容后，将展开的区域滚动到可视区域内
 * @param {number} msgIndex - 消息在列表中的索引
 * @param {HTMLElement} [expandEl] - 展开的元素（可选，用于定位）
 */
function scrollAfterExpand(msgIndex, expandEl) {
  nextTick(() => {
    requestAnimationFrame(() => {
      const container = messagesRef.value
      if (!container) return
      // 优先用展开元素定位，否则用消息元素
      const target = expandEl || container.querySelector(`[data-index="${msgIndex}"]`)
      if (!target) return
      const containerRect = container.getBoundingClientRect()
      const targetRect = target.getBoundingClientRect()
      // 目标元素底部超出可视区域，滚动使其可见
      if (targetRect.bottom > containerRect.bottom) {
        container.scrollTop += targetRect.bottom - containerRect.bottom + 16
      }
    })
  })
}

/** 消息列表中是否已有流式中的助手消息（与占位加载条互斥） */
const hasStreamingAssistantMessage = computed(() =>
  messages.value.some(m => m.role === 'assistant' && m._streaming)
)
const abortController = ref(null)
const toolEvents = ref([])
// 用于存储每条消息的展开状态，key为消息索引，value为Set<refIndex>
const expandedRefsMap = ref(new Map())
// 用于存储每条消息的参考文献区域是否展开，key为消息索引，value为boolean
const refsSectionExpandedMap = ref(new Map())
// 消息反馈状态：messageId → "like"/"dislike"
const messageFeedbackMap = ref(new Map())

const canSend = computed(() =>
  !loading.value && (input.value.trim().length > 0 || pendingAttachments.value.length > 0))

const userInitial = computed(() => {
  const name = userStore.user?.username || userStore.user?.nickname || 'U'
  return name[0].toUpperCase()
})

function handleChatKeydown(e) {
  // Ctrl+/ — 聚焦输入框
  if (e.ctrlKey && e.code === 'Slash') {
    e.preventDefault()
    inputRef.value?.focus()
    return
  }
  // Escape — 停止生成
  if (e.key === 'Escape' && streaming.value) {
    e.preventDefault()
    stopGenerating()
  }
}

function handleEditKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    submitEdit()
  } else if (e.key === 'Escape') {
    cancelEdit()
  }
}

function autoResize() {
  // ChatMentionInput 使用 contenteditable，由 CSS 控制高度（min-height/max-height + overflow-y），
  // 不再需要 JS 手动调整。保留空函数避免破坏 useVoiceIO 等调用方。
}


/**
 * 从消息metadata中解析RAG引用
 */
function getMsgRagRefs(msg) {
  if (!msg.metadata) return []
  try {
    const metadata = typeof msg.metadata === 'string' ? safeJsonParse(msg.metadata) : msg.metadata
    return metadata?.ragReferences || []
  } catch {
    return []
  }
}

function getRagQaQuestion(ref) {
  if (!ref || ref.sourceType !== 'qa_pair') return ''
  if (ref.question) return ref.question
  const content = ref.contentPreview || ''
  const match = content.match(/^问题：([^\n]*)/)
  return match?.[1] || ref.documentName || '问答对'
}

/**
 * 判断某个引用是否展开
 */
function isReferenceExpanded(msg, index) {
  const msgIndex = messages.value.indexOf(msg)
  const key = `${msgIndex}-${index}`
  return expandedRefsMap.value.has(key)
}

/**
 * 切换引用展开状态
 */
function toggleReference(msg, index) {
  const msgIndex = messages.value.indexOf(msg)
  const key = `${msgIndex}-${index}`
  const newMap = new Map(expandedRefsMap.value)
  if (newMap.has(key)) {
    newMap.delete(key)
  } else {
    newMap.set(key, true)
  }
  expandedRefsMap.value = newMap
  scrollAfterExpand(msgIndex)
}

/**
 * 判断参考文献区域是否展开（默认展开，记录的为收起状态）
 */
function isRefsSectionExpanded(msg) {
  const msgIndex = messages.value.indexOf(msg)
  return !refsSectionExpandedMap.value.has(msgIndex)
}

/**
 * 切换参考文献区域展开状态
 */
function toggleRefsSection(msg) {
  const msgIndex = messages.value.indexOf(msg)
  const newMap = new Map(refsSectionExpandedMap.value)
  if (newMap.has(msgIndex)) {
    newMap.delete(msgIndex)
  } else {
    newMap.set(msgIndex, true)
  }
  refsSectionExpandedMap.value = newMap
  scrollAfterExpand(msgIndex)
}

function toggleReasoningExpand(index) {
  const msg = messages.value[index]
  if (!msg) return
  msg._reasoningExpanded = !msg._reasoningExpanded
}

function getMessageFeedbackType(msg) {
  return messageFeedbackMap.value.get(msg._id || msg.id) || null
}

async function handleMessageFeedback(msg, rating) {
  const msgId = msg._id || msg.id
  const current = messageFeedbackMap.value.get(msgId)
  // 乐观更新
  if (current === rating) {
    messageFeedbackMap.value.delete(msgId)
  } else {
    messageFeedbackMap.value.set(msgId, rating)
  }
  try {
    const res = await submitMessageFeedback(msgId, { rating })
    // 服务端返回 null 表示取消，否则更新为实际值
    if (res?.data) {
      messageFeedbackMap.value.set(msgId, res.data.rating)
      message.success(rating === 'like' ? '已反馈有用' : '已反馈无帮助')
    } else {
      messageFeedbackMap.value.delete(msgId)
      message.success('已取消反馈')
    }
  } catch {
    // 回滚
    if (current) {
      messageFeedbackMap.value.set(msgId, current)
    } else {
      messageFeedbackMap.value.delete(msgId)
    }
  }
}

// dislike 原因弹窗
const dislikeModalVisible = ref(false)
const dislikeReason = ref('')
const dislikeTargetMsg = ref(null)

function showDislikeModal(msg) {
  dislikeTargetMsg.value = msg
  dislikeReason.value = ''
  dislikeModalVisible.value = true
}

async function submitDislikeReason() {
  await submitDislikeFeedback(dislikeReason.value || null)
}

async function skipDislikeReason() {
  await submitDislikeFeedback(null)
}

async function submitDislikeFeedback(reason) {
  const msg = dislikeTargetMsg.value
  if (!msg) return
  dislikeModalVisible.value = false
  const msgId = msg._id || msg.id
  const current = messageFeedbackMap.value.get(msgId)
  messageFeedbackMap.value.set(msgId, 'dislike')
  try {
    await submitMessageFeedback(msgId, { rating: 'dislike', reason })
    message.success('已反馈无帮助')
  } catch {
    if (current) {
      messageFeedbackMap.value.set(msgId, current)
    } else {
      messageFeedbackMap.value.delete(msgId)
    }
  }
}

async function loadBatchFeedbacks(msgs) {
  const ids = msgs.filter(m => m.role === 'assistant' && m._id).map(m => m._id)
  if (ids.length === 0) return
  try {
    const res = await batchGetMessageFeedbacks(ids)
    if (res?.data) {
      const map = new Map(messageFeedbackMap.value)
      for (const [msgId, fb] of Object.entries(res.data)) {
        if (fb?.rating) map.set(msgId, fb.rating)
      }
      messageFeedbackMap.value = map
    }
  } catch {
    // interceptor handled
  }
}

function parseAttachmentsFromMetadata(metadata) {
  if (!metadata) return []
  try {
    const meta = typeof metadata === 'string' ? safeJsonParse(metadata) : metadata
    return Array.isArray(meta?.attachments) ? meta.attachments : []
  } catch {
    return []
  }
}

/** 从 msg.metadata 或 msg._mentions 提取 mention 快照（用于历史 chip 回显） */
function getMsgMentions(msg) {
  if (!msg) return []
  if (Array.isArray(msg._mentions) && msg._mentions.length) return msg._mentions
  try {
    const meta = typeof msg.metadata === 'string' ? safeJsonParse(msg.metadata) : msg.metadata
    return Array.isArray(meta?.mentions) ? meta.mentions : []
  } catch {
    return []
  }
}

function getMsgAttachments(msg) {
  return msg._attachments || []
}

async function enrichMessagesAttachments(msgs) {
  const needRefresh = []
  for (const msg of msgs) {
    if (msg.role !== 'user') continue
    const atts = msg._attachments || []
    for (const a of atts) {
      if (a?.objectKey) needRefresh.push(a)
    }
  }
  if (!needRefresh.length) return
  try {
    const res = await refreshChatAttachmentPreviews(needRefresh)
    const refreshedByKey = new Map((res.data || []).map(a => [a.objectKey, a]))
    for (const msg of msgs) {
      if (!msg._attachments?.length) continue
      msg._attachments = msg._attachments.map(a => {
        const refreshed = refreshedByKey.get(a.objectKey)
        return refreshed ? { ...a, ...refreshed } : a
      })
      await enrichVideoThumbnails(msg._attachments)
    }
  } catch {
    // 预览 URL 刷新失败时仍展示文件名
  }
}

function parseMessage(m) {
  let toolEvents = []
  let workflowEvents = []
  let toolBlockOffsets = []
  let reasoningContent = ''
  let sensitiveBlock = null
  let attachments = []
  let requestId = null

  // 解析metadata（处理JSON字符串嵌套）
  let metadata = m.metadata
  if (metadata && typeof metadata === 'string') {
    try {
      // 使用原生JSON.parse解析（metadata中没有Long ID精度问题）
      metadata = JSON.parse(metadata)

      // 如果解析后仍是字符串（双重转义），再解析一次
      if (typeof metadata === 'string') {
        metadata = JSON.parse(metadata)
      }
    } catch (e) {
      console.error('[parseMessage] metadata解析失败:', e, metadata?.substring?.(0, 200))
      metadata = null
    }
  }

  // 提取字段
  if (metadata) {
    if (metadata.toolEvents) toolEvents = metadata.toolEvents
    if (metadata.workflowEvents) workflowEvents = metadata.workflowEvents
    if (metadata.toolBlockOffsets) {
      toolBlockOffsets = metadata.toolBlockOffsets.map(o => Number(o))
    }
    if (metadata.reasoningContent) reasoningContent = metadata.reasoningContent
    if (metadata.sensitiveBlock) sensitiveBlock = metadata.sensitiveBlock
    if (metadata.requestId) requestId = metadata.requestId
    attachments = parseAttachmentsFromMetadata(metadata)
  }

  // 规范化 toolEvents 中的 contentOffset 为数字类型
  if (toolEvents.length > 0) {
    toolEvents = toolEvents.map(e => ({
      ...e,
      contentOffset: e.contentOffset != null ? Number(e.contentOffset) : e.contentOffset
    }))
  }

  const roleRaw = m.role?.code || m.role
  const role = roleRaw != null ? String(roleRaw).toLowerCase() : ''

  return {
    role,
    content: m.content,
    metadata: metadata ?? m.metadata,
    _id: m.id,
    _parentId: m.parentId || null,
    _messageType: m.messageType?.code || m.messageType || 'text',
    _attachments: attachments,
    _toolEvents: toolEvents,
    _workflowEvents: workflowEvents,
    _toolBlockOffsets: toolBlockOffsets,
    _toolBlocksDone: [],
    _toolExpanded: false,
    _toolsDone: true,
    _reasoningContent: reasoningContent,
    _reasoningExpanded: true,
    _reasoningDone: true,
    _sensitiveBlock: sensitiveBlock,
    _requestId: requestId,
    _replyToMessageId: m.replyToMessageId || null,
    _replyToContent: null,
    _replyToRole: null,
    _starred: !!m.starred,
    _createTime: m.createTime || null,
  }
}

/** 仅最后一条助手回复可重新生成（其后无用户新消息） */
function isBackendErrorMessage(msg) {
  return msg?.role === 'assistant' && !!msg._error
}

function canRegenerate(index) {
  if (loading.value || streaming.value) return false
  const msg = messages.value[index]
  if (!msg || msg.role !== 'assistant' || msg._streaming || msg._sensitiveBlock) return false
  return index === messages.value.length - 1
}

async function loadHistory() {
  // 流式对话进行中不加载历史，避免替换 messages 数组破坏 stream 闭包引用
  if (streaming.value) return

  if (!sessionId.value) {
    messages.value = []
    selectedAgentId.value = null
    currentAgent.value = null
    lastReplyElapsed.value = null
    sessionTokenCount.value = 0
    sessionTitle.value = ''
    switchingSession.value = false
    return
  }
  // 竞态保护：递增请求 ID
  const reqId = ++loadHistoryRequestId
  // 切换对话时先清空旧内容，避免旧消息在加载期间残留
  messages.value = []
  initialLoadDone.value = false
  lastReplyElapsed.value = null
  sessionAttachments.value = []
  fileDrawerLoadedOnce.value = false
  input.value = ''
  inputHistory.value = []
  historyIndex.value = -1
  pendingAttachments.value = []
  cancelReply()
  loadingHistory.value = true
  switchingSession.value = true
  messagePage.value = 1
  try {
    // 并行加载消息（第1页）和会话详情
    const [msgRes, sessionRes] = await Promise.all([
      getSessionMessages(sessionId.value, { pageNum: 1, pageSize: 10 }),
      getSession(sessionId.value),
    ])
    // 请求已过期，丢弃结果
    if (reqId !== loadHistoryRequestId) return

    const records = msgRes.data?.records || []
    // API 按创建时间倒序返回，前端正序显示（旧→新）
    const parsed = records.reverse().map(m => parseMessage(m))
    await enrichMessagesAttachments(parsed)
    if (reqId !== loadHistoryRequestId) return
    messages.value = parsed
    hasMoreMessages.value = records.length === 10

    // 批量加载消息反馈状态
    loadBatchFeedbacks(parsed)

    // 从会话中恢复 agentId 和 agentVersionId
    const session = sessionRes.data
    sessionTitle.value = session?.title || '新对话'
    sessionTokenCount.value = session?.totalTokens || 0
    if (session?.agentId) {
      selectedAgentId.value = session.agentId
      // 先加载版本列表（传入会话保存的版本 ID），再加载 agent 详情
      const versionDeleted = await loadAgentConfigVersions(session.agentId, session.agentVersionId)
      if (versionDeleted) {
        message.warning('当前对话Agent版本可能已被删除，已切换到草稿版本，你可以重新选择Agent版本')
      }
      if (reqId !== loadHistoryRequestId) return
      await loadCurrentAgent(session.agentId)
      if (reqId !== loadHistoryRequestId) return
    }
    isNearBottom.value = true
    userScrolledUp.value = false
    forceScrollToBottom()

    // 历史消息中自动弹出未回答的 ask_user 弹窗
    nextTick(() => {
      for (let i = messages.value.length - 1; i >= 0; i--) {
        if (messages.value[i].role === 'assistant' && isAskUserUnanswered(i)) {
          showAskUserModal(i)
          break
        }
      }
    })
  } catch (e) {
    if (reqId !== loadHistoryRequestId) return
    messages.value = []
  } finally {
    initialLoadDone.value = true
    if (reqId === loadHistoryRequestId) {
      loadingHistory.value = false
      switchingSession.value = false
    }
  }
}

async function loadOlderMessages() {
  if (loadingOlder.value || !hasMoreMessages.value || streaming.value) return
  // 新对话时 sessionId 为 null，不应请求
  if (!sessionId.value) return

  // 记录 prepend 前的虚拟化器总尺寸
  const oldTotalSize = virtualizer.value.getTotalSize()
  const oldScrollTop = messagesRef.value?.scrollTop || 0

  loadingOlder.value = true
  try {
    messagePage.value++
    const res = await getSessionMessages(sessionId.value, {
      pageNum: messagePage.value,
      pageSize: 10,
    })
    const records = res.data?.records || []
    if (records.length > 0) {
      const olderMessages = records.reverse().map(m => parseMessage(m))
      await enrichMessagesAttachments(olderMessages)
      messages.value = [...olderMessages, ...messages.value]
      hasMoreMessages.value = records.length === 10
      // 保持滚动位置：prepend 后虚拟化器重新计算所有 item 位置，
      // 需要用 scrollToOffset 将 scrollTop 加上新增内容的高度
      await nextTick()
      const newTotalSize = virtualizer.value.getTotalSize()
      const sizeDelta = newTotalSize - oldTotalSize
      virtualizer.value.scrollToOffset(oldScrollTop + sizeDelta)
    } else {
      hasMoreMessages.value = false
    }
  } catch {
    // 静默失败
  } finally {
    loadingOlder.value = false
  }
}


async function sendMessage() {
  const text = input.value.trim()
  const attachments = [...pendingAttachments.value]
  if ((!text && attachments.length === 0) || loading.value) return
  const mixCheck = validatePendingAttachmentMix(attachments)
  if (!mixCheck.ok) {
    message.warning(mixCheck.message)
    return
  }

  // 从 ChatMentionInput 提取结构化 mentions（在重置 input 之前）
  const mentionInputComp = inputRef.value
  const sentMentions = mentionInputComp?.getMentions?.() || undefined

  const displayContent = text || (attachments.length ? '[附件]' : '')
  const sentAttachments = attachments.map(a => ({ ...a }))
  await enrichVideoThumbnails(sentAttachments)

  const userMsg = { role: 'user', content: displayContent, _attachments: sentAttachments }
  // 携带 mention 快照（用于前端 chip 渲染，后端 message.metadata 也会持久化一份）
  if (sentMentions?.length) {
    userMsg._mentions = sentMentions
  }
  // 携带引用回复信息（用于前端渲染引用摘要）
  const currentReplyToId = replyTo.active ? replyTo.messageId : null
  const currentReplyToContent = replyTo.active ? replyTo.content : ''
  const currentReplyToRole = replyTo.active ? replyTo.role : ''
  if (currentReplyToId) {
    userMsg._replyToMessageId = currentReplyToId
    userMsg._replyToContent = currentReplyToContent
    userMsg._replyToRole = currentReplyToRole
  }
  messages.value.push(userMsg)
  // 记录输入历史（去重：与上一条相同则不重复记录）
  if (text && (inputHistory.value.length === 0 || inputHistory.value[inputHistory.value.length - 1] !== text)) {
    inputHistory.value.push(text)
  }
  historyIndex.value = -1
  input.value = ''
  // 清空 ChatMentionInput 中的 chip（input 清空会触发 watch 重置 innerHTML，mentions 也需手动清）
  mentionInputComp?.clear?.()
  pendingAttachments.value = []
  cancelReply()
  autoResize()
  isNearBottom.value = true
  userScrolledUp.value = false
  scrollToBottom()

  await runChatStream({
    message: text,
    attachments: sentAttachments,
    mentions: sentMentions,
    regenerate: false,
    replyToMessageId: currentReplyToId,
  })
}

async function regenerateReply(assistantIndex) {
  if (loading.value || !canRegenerate(assistantIndex)) return
  let userIdx = assistantIndex - 1
  while (userIdx >= 0 && messages.value[userIdx].role !== 'user') {
    userIdx--
  }
  if (userIdx < 0) return
  const userMsg = messages.value[userIdx]
  messages.value.pop()
  isNearBottom.value = true
  userScrolledUp.value = false
  scrollToBottom()
  await runChatStream({
    message: userMsg.content === '[附件]' ? '' : (userMsg.content || ''),
    attachments: userMsg._attachments || [],
    regenerate: true,
  })
}

function isLastUserMessage(index) {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i].role === 'user') return i === index
  }
  return false
}

function startEdit(index) {
  const msg = messages.value[index]
  if (!msg || loading.value) return
  editingMessageId.value = msg._id || `local-${index}`
  editContent.value = msg.content || ''
  nextTick(() => {
    const el = editInputRef.value
    if (el) {
      const textarea = el.$el ? el.$el : el
      if (textarea.focus) textarea.focus()
    }
  })
}

function cancelEdit() {
  editingMessageId.value = null
  editContent.value = ''
}

function startReply(index) {
  const msg = messages.value[index]
  if (!msg || loading.value) return
  replyTo.active = true
  replyTo.messageId = msg._id
  replyTo.content = (msg.content || '').slice(0, 100)
  replyTo.role = msg.role
  nextTick(() => inputRef.value?.focus())
}

function cancelReply() {
  replyTo.active = false
  replyTo.messageId = null
  replyTo.content = ''
  replyTo.role = ''
}

async function toggleStarMessage(index) {
  const msg = messages.value[index]
  if (!msg?._id) return
  try {
    await toggleMessageStar(msg._id)
    msg._starred = !msg._starred
    message.success(msg._starred ? '收藏成功' : '已取消收藏')
  } catch {
    message.error('操作失败')
  }
}

function getReplyToInfo(msg) {
  if (!msg._replyToMessageId) return null
  // 当前会话发送的消息直接携带引用内容
  if (msg._replyToContent) {
    return { content: msg._replyToContent, role: msg._replyToRole }
  }
  // 历史消息：从已加载的消息列表中查找被引用的消息
  const refMsg = messages.value.find(m => m._id === msg._replyToMessageId)
  if (refMsg) {
    return { content: (refMsg.content || '').slice(0, 100), role: refMsg.role }
  }
  return null
}

/** 引用目标是否在已加载的消息列表中（控制是否可点击跳转） */
function hasReplyTarget(msg) {
  if (!msg?._replyToMessageId) return false
  return messages.value.some(m => m._id === msg._replyToMessageId)
}

/** 滚动到被引用的消息并高亮闪烁 */
function scrollToMessage(messageId) {
  if (!messageId) return
  const idx = messages.value.findIndex(m => m._id === messageId)
  if (idx < 0) return
  virtualizer.value.scrollToIndex(idx, { align: 'center' })
  // 延迟高亮，等虚拟滚动渲染完成
  setTimeout(() => {
    highlightMessageId.value = messageId
    setTimeout(() => { highlightMessageId.value = null }, 2000)
  }, 100)
}

async function submitEdit() {
  const newText = editContent.value.trim()
  if (!newText || loading.value) return

  const editIdx = messages.value.findIndex(m => m._id === editingMessageId.value)
  if (editIdx < 0) return

  const msg = messages.value[editIdx]
  msg.content = newText
  editingMessageId.value = null
  editContent.value = ''

  // 删除本地的最后一条助手消息
  const lastIdx = messages.value.length - 1
  if (lastIdx > editIdx && messages.value[lastIdx].role === 'assistant') {
    messages.value.pop()
  }

  isNearBottom.value = true
  userScrolledUp.value = false
  scrollToBottom()

  await runChatStream({
    message: newText,
    attachments: msg._attachments || [],
    regenerate: true,
    editMessageId: msg._id || null,
  })
}

async function runChatStream({ message, attachments, mentions, regenerate, editMessageId: editMsgId, replyToMessageId: replyMsgId }) {
  loading.value = true
  streaming.value = true
  hasStreamContent.value = false
  lastReplyElapsed.value = null
  currentStatus.value = '正在思考...'
  toolEvents.value = []
  sendStartTime = Date.now()

  let assistantMsg = null
  let pushed = false
  let pendingRequestId = null
  abortController.value = new AbortController()

  const attachRequestId = (msg) => {
    if (msg && pendingRequestId) {
      msg._requestId = pendingRequestId
    }
  }

  try {
    let sid = sessionId.value
    const currentAgentId = selectedAgentId.value

    if (!sid) {
      const res = await createSession(currentAgentId || undefined)
      sid = res.data.id
      skipNextWatch.value = true
      router.replace(`/app/chat/${sid}`)
    }

    const chatPayload = {
      message: message || undefined,
      sessionId: sid,
      agentId: currentAgentId || undefined,
      configVersion: selectedConfigVersion.value ?? 0,
      agentVersionId: selectedAgentVersionId.value || undefined,
      regenerate: regenerate || undefined,
      editMessageId: editMsgId || undefined,
      replyToMessageId: replyMsgId || undefined,
      mentions: mentions?.length ? mentions.map(m => ({
        type: m.type,
        resourceId: String(m.resourceId),
        name: m.name,
        token: m.token,
      })) : undefined,
      attachments: attachments?.length ? attachments.map(a => ({
        id: a.id,
        type: a.type,
        mimeType: a.mimeType,
        objectKey: a.objectKey,
        previewUrl: a.previewUrl,
        fileName: a.fileName,
        parsedText: a.parsedText,
        parsedTextTruncated: a.parsedTextTruncated,
      })) : undefined,
    }
    await chatStream(
      chatPayload,
      {
        onRequestId: (requestId) => {
          if (requestId) {
            pendingRequestId = requestId
            attachRequestId(assistantMsg)
          }
        },
        // onChunk: 文本内容（经 streamSmoother 平滑后写入消息）
        onChunk: (chunk) => {
          if (!pushed) {
            messages.value.push({ role: 'assistant', content: '', _streaming: true, _toolsDone: false, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: false, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: false })
            assistantMsg = messages.value[messages.value.length - 1]
            currentStreamingMsg = assistantMsg
            attachRequestId(assistantMsg)
            pushed = true
            hasStreamContent.value = true
            streamSmoother.start()
          }
          if (reconnecting.value) reconnecting.value = false
          streamSmoother.push(chunk)
        },
        // onStatus: 状态消息
        onStatus: (status) => {
          currentStatus.value = status
          scrollToBottom()
        },
        // onToolEvent: 工具调用/结果/状态事件
        onToolEvent: (event) => {
          if (!pushed) {
            messages.value.push({ role: 'assistant', content: '', _streaming: true, _toolsDone: false, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: true, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: false })
            assistantMsg = messages.value[messages.value.length - 1]
            currentStreamingMsg = assistantMsg
            attachRequestId(assistantMsg)
            pushed = true
            hasStreamContent.value = true
            streamSmoother.start()
          }
          if (event.type === 'tool_complete') {
            const offset = event.contentOffset ?? assistantMsg._currentToolOffset
            markToolBlockDone(assistantMsg, offset)
            return
          }
          if (event.type === 'reasoning_content') {
            clearErrorRetry(assistantMsg)
            assistantMsg._reasoningContent = (assistantMsg._reasoningContent || '') + event.content
            assistantMsg._reasoningDone = true
            scrollToBottom()
            scrollReasoningToBottom()
            return
          }
          // 敏感词拦截事件：标记消息为拦截状态
          if (event.type === 'sensitive_block') {
            assistantMsg._sensitiveBlock = event.scope || 'ai_output'
            assistantMsg.content = event.message || assistantMsg.content
            assistantMsg._streaming = false
            assistantMsg._toolsDone = true
            loading.value = false
            streaming.value = false
            hasStreamContent.value = false
            currentStatus.value = ''
            lastReplyElapsed.value = Date.now() - sendStartTime
            abortController.value = null
            return
          }
          // 1.3 模型调用重试事件：保留流式状态，只更新专门提示块
          if (event.type === 'error_retry') {
            assistantMsg._errorRetry = {
              message: event.message || 'AI连接异常，正在重试中',
              code: event.code || 'LLM_ERROR',
              attempt: event.attempt || 1,
              maxRetries: event.maxRetries || event.attempt || 1,
            }
            currentStatus.value = assistantMsg._errorRetry.message
            scrollToBottom()
            return
          }
          // 1.3 结构化错误事件：LLM 调用中断、工具异常等
          if (event.type === 'error') {
            assistantMsg._error = {
              message: event.message || '未知错误',
              code: event.code || 'UNKNOWN',
            }
            assistantMsg._errorRetry = null
            assistantMsg._streaming = false
            assistantMsg._toolsDone = true
            loading.value = false
            streaming.value = false
            hasStreamContent.value = false
            currentStatus.value = ''
            lastReplyElapsed.value = Date.now() - sendStartTime
            abortController.value = null
            scrollToBottom()
            return
          }
          // 工作流 LLM 流式输出：经 streamSmoother 平滑后写入消息
          if (event.type === 'workflow_llm_chunk') {
            if (!pushed) {
              messages.value.push({ role: 'assistant', content: '', _streaming: true, _toolsDone: false, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: false, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: false })
              assistantMsg = messages.value[messages.value.length - 1]
              currentStreamingMsg = assistantMsg
              attachRequestId(assistantMsg)
              pushed = true
              hasStreamContent.value = true
              streamSmoother.start()
            }
            clearErrorRetry(assistantMsg)
            streamSmoother.push(event.content || '')
            return
          }
          // 工作流节点执行事件（实时推送，无需等待最终回复）
          if (event.type === 'workflow_node_start' || event.type === 'workflow_node_complete' || event.type === 'workflow_complete') {
            if (!assistantMsg._workflowEvents) assistantMsg._workflowEvents = []
            assistantMsg._workflowEvents.push(event)
            hasStreamContent.value = true
            if (event.type === 'workflow_node_start') {
              currentStatus.value = `正在执行: ${event.nodeLabel || event.nodeType || '节点'}`
            } else if (event.type === 'workflow_node_complete') {
              const label = event.nodeLabel || event.nodeType || '节点'
              const dur = event.durationMs != null ? ` (${event.durationMs}ms)` : ''
              currentStatus.value = event.success === false
                ? `${label} 执行失败`
                : `${label} 已完成${dur}`
            } else if (event.type === 'workflow_complete') {
              currentStatus.value = '工作流执行完成，正在整理回复…'
            }
            scrollToBottom()
            return
          }

          streamSmoother.flush()

          const offset = event.contentOffset ?? assistantMsg.content.length
          if (event.contentOffset == null) {
            event.contentOffset = offset
          }
          if (event.type === 'skill_active') {
            assistantMsg._toolEvents.push(event)
            hasStreamContent.value = true
            currentStatus.value = `已启用 ${(event.skills || []).length} 个 Skill`
            scrollToBottom()
            return
          }
          if (event.type === 'subagent_call' || event.type === 'subagent_result'
              || event.type === 'subagent_token' || event.type === 'subagent_tool_call' || event.type === 'subagent_tool_result') {
            assistantMsg._toolEvents.push(event)
            if (event.type === 'subagent_call') {
              assistantMsg._toolExpanded = true
              assistantMsg._currentToolOffset = offset
              registerToolBlockOffset(assistantMsg, offset)
              currentStatus.value = `委派 SubAgent: ${event.displayName || event.subagentName || ''}`
            } else if (event.type === 'subagent_tool_call') {
              currentStatus.value = `SubAgent 调用工具: ${event.toolName || ''}`
            } else if (event.type === 'subagent_token') {
              currentStatus.value = `SubAgent 输出中...`
            }
            hasStreamContent.value = true
            scrollToBottom()
            return
          }
          if (event.type === 'tool_call') {
            assistantMsg._toolExpanded = true
            assistantMsg._currentToolOffset = offset
            registerToolBlockOffset(assistantMsg, offset)
          } else if (assistantMsg._currentToolOffset == null || assistantMsg._currentToolOffset < 0) {
            assistantMsg._currentToolOffset = offset
            registerToolBlockOffset(assistantMsg, offset)
          }

          assistantMsg._toolEvents.push(event)
          toolEvents.value.push(event)
          if (event.type === 'tool_status' && event.message) {
            currentStatus.value = event.message
          }
          scrollToBottom()
        },
        // onMetadata: metadata消息（含工具事件与 offset，每轮工具调用后更新）
        onMetadata: (metadataStr) => {
          if (!assistantMsg) return
          applyToolMetadata(assistantMsg, safeJsonParse(metadataStr))
        },
        // onDone: 完成
        onDone: (meta) => {
          // 10.1 停止平滑缓冲，flush 剩余内容
          streamSmoother.stop()
          currentStreamingMsg = null
          reconnecting.value = false
          if (assistantMsg) {
            if (!assistantMsg._toolBlockOffsets?.length) {
              assistantMsg._toolBlockOffsets = getToolBlockOffsets(assistantMsg)
            }
            assistantMsg._streaming = false
            assistantMsg._toolsDone = true
            assistantMsg._toolExpanded = false
            // 后端 [DONE] 事件携带消息ID，直接赋值（无需刷新）
            if (meta?.assistantMessageId) {
              assistantMsg._id = meta.assistantMessageId
            }
            // 累加本次回复的 Token 消耗
            if (meta?.totalTokens) {
              sessionTokenCount.value += meta.totalTokens
            }
            // 合并 [DONE] 携带的完整 metadata（ragReferences、reasoningContent、requestId 等）
            // 确保流式消息与历史消息的 metadata 一致
            if (meta) {
              const { assistantMessageId, userMessageId, totalTokens, ...restMeta } = meta
              if (Object.keys(restMeta).length > 0) {
                assistantMsg.metadata = { ...(assistantMsg.metadata || {}), ...restMeta }
              }
            }
          }
          loading.value = false
          streaming.value = false
          hasStreamContent.value = false
          currentStatus.value = ''
          lastReplyElapsed.value = Date.now() - sendStartTime
          abortController.value = null
          // 轮询等待标题生成完成
          pollSessionTitle(sid)
          // 流式结束后滚动到底部（延迟等待渲染完成）
          if (isNearBottom.value) {
            setTimeout(() => {
              const el = messagesRef.value
              if (el) el.scrollTop = el.scrollHeight
            }, 300)
          }
          // 流式结束后自动弹出 ask_user 弹窗
          nextTick(() => {
            const lastIdx = messages.value.length - 1
            if (lastIdx >= 0 && messages.value[lastIdx].role === 'assistant' && isAskUserUnanswered(lastIdx)) {
              showAskUserModal(lastIdx)
            }
          })
        },
      },
      abortController.value?.signal,
      { maxRetries: 3, retryDelay: 2000, onReconnecting: () => {
        reconnecting.value = true
        currentStatus.value = '正在重连...'
      }}
    )
  } catch (e) {
    // 10.1 异常时停止平滑缓冲，flush 剩余内容
    streamSmoother.stop()
    currentStreamingMsg = null
    reconnecting.value = false
    // 用户主动中断
    if (e.name === 'AbortError') {
      if (!assistantMsg) {
        messages.value.push({ role: 'assistant', content: '', _streaming: false, _toolsDone: true, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: false, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: true })
        assistantMsg = messages.value[messages.value.length - 1]
      }
      assistantMsg.content += '\n\n*AI 输出已终止*'
      assistantMsg._streaming = false
    } else {
      if (!assistantMsg) {
        messages.value.push({ role: 'assistant', content: '', _streaming: false, _toolsDone: true, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: false, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: true })
        assistantMsg = messages.value[messages.value.length - 1]
      }
      assistantMsg._error = {
        message: 'AI 大模型调用失败，请检查模型配置是否正确。\n\n错误详情：' + (e.message || '未知错误'),
        code: 'REQUEST_ERROR',
      }
      assistantMsg._streaming = false
      assistantMsg._toolsDone = true
    }
    loading.value = false
    streaming.value = false
    hasStreamContent.value = false
    currentStatus.value = ''
    abortController.value = null
  }
}

async function copyMessage(msg) {
  await copyToClipboard(msg.content)
  msg._copied = true
  setTimeout(() => { msg._copied = false }, 2000)
}

async function copyRequestId(msg) {
  if (!msg._requestId) return
  await copyToClipboard(msg._requestId)
  msg._requestIdCopied = true
  message.success('Request ID 已复制')
  setTimeout(() => { msg._requestIdCopied = false }, 2000)
}

async function copyMessageId(msg) {
  if (!msg._id) return
  await copyToClipboard(String(msg._id))
  msg._msgIdCopied = true
  message.success('Message ID 已复制')
  setTimeout(() => { msg._msgIdCopied = false }, 2000)
}

function openRawModal(index) {
  const msg = messages.value[index]
  if (!msg) return
  rawModal.content = msg.content || ''
  rawModal.title = msg.role === 'assistant' ? '原文' : '消息原文'
  rawModal.metadata = msg.metadata || null
  rawModal.visible = true
}

function openMetadataModal() {
  if (!rawModal.metadata) return
  const raw = rawModal.metadata
  try {
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    metadataModal.json = JSON.stringify(obj, null, 2)
  } catch {
    metadataModal.json = typeof raw === 'string' ? raw : JSON.stringify(raw)
  }
  metadataModal.copied = false
  metadataModal.visible = true
}

async function copyRawContent() {
  if (!rawModal.content) return
  await copyToClipboard(rawModal.content)
  rawModal.copied = true
  message.success('已复制')
  setTimeout(() => { rawModal.copied = false }, 2000)
}

async function copyMetadata() {
  if (!metadataModal.json) return
  await copyToClipboard(metadataModal.json)
  metadataModal.copied = true
  message.success('Metadata 已复制')
  setTimeout(() => { metadataModal.copied = false }, 2000)
}

function handleDeleteMessage(index) {
  const msg = messages.value[index]
  if (!msg) return
  const label = msg.role === 'assistant' ? 'AI 回复' : '用户消息'
  Modal.confirm({
    title: `删除${label}`,
    content: `确定要删除这条${label}吗？删除后不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      // 有 _id 说明是已持久化的消息，需要调后端删除
      if (msg._id && sessionId.value) {
        try {
          await deleteMessageApi(sessionId.value, msg._id)
        } catch {
          // 业务错误已由拦截器提示
          return
        }
      }
      messages.value.splice(index, 1)
      message.success('已删除')
    },
  })
}

function stopGenerating() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
}

function registerToolBlockOffset(msg, offset) {
  if (offset == null || offset < 0) return
  if (!msg._toolBlockOffsets) msg._toolBlockOffsets = []
  // 使用宽松相等检查是否已存在
  if (!msg._toolBlockOffsets.some(o => o == offset)) {
    msg._toolBlockOffsets.push(offset)
    msg._toolBlockOffsets.sort((a, b) => a - b)
  }
}

const CAPABILITY_EVENT_TYPES = new Set(['skill_active', 'subagent_call', 'subagent_result', 'subagent_token', 'subagent_tool_call', 'subagent_tool_result'])

function getCapabilityEvents(msg) {
  return (msg._toolEvents || []).filter(e => CAPABILITY_EVENT_TYPES.has(e.type))
}

function getTopCapabilityEvents(msg) {
  return getCapabilityEvents(msg).filter(e => e.type === 'skill_active')
}

function getCapabilityEventsForOffset(msg, offset) {
  // 使用宽松相等，兼容数字和字符串类型的 offset
  return getCapabilityEvents(msg).filter(e => e.type !== 'skill_active' && e.contentOffset == offset)
}

function getInlineCapabilityEvents(msg) {
  const offsets = getToolBlockOffsets(msg)
  if (offsets.length > 0) return []
  return getCapabilityEvents(msg).filter(e => e.type !== 'skill_active')
}

function getPureToolEvents(events) {
  return (events || []).filter(e => !CAPABILITY_EVENT_TYPES.has(e.type))
}

function getToolBlockOffsets(msg) {
  if (msg._toolBlockOffsets?.length > 0) return msg._toolBlockOffsets
  const fromEvents = [...new Set(
    (msg._toolEvents || [])
      .filter(e => e.type === 'tool_call' || e.type === 'subagent_call')
      .map(e => e.contentOffset)
      .filter(o => o != null && o >= 0)
  )]
  return fromEvents.sort((a, b) => a - b)
}

function getToolEventsForOffset(msg, offset) {
  const events = msg._toolEvents || []
  // 使用宽松相等，兼容数字和字符串类型的 offset
  const matched = events.filter(e => e.contentOffset == offset)
  if (matched.length > 0) return matched
  const offsets = getToolBlockOffsets(msg)
  if (offsets.length === 1 && offsets[0] == offset) {
    return events.filter(e => e.contentOffset == null)
  }
  return matched
}

function isToolBlockDone(msg, offset) {
  // 兼容数字和字符串类型的 offset 比较
  if (msg._toolBlocksDone?.some(o => o == offset)) return true
  if (!msg._streaming) return true
  const atOffset = getToolEventsForOffset(msg, offset)
  return atOffset.some(e => e.type === 'tool_result' || e.type === 'subagent_result')
}

function markToolBlockDone(msg, offset) {
  if (offset == null || offset < 0) return
  if (!msg._toolBlocksDone) msg._toolBlocksDone = []
  // 使用宽松相等检查是否已存在
  if (!msg._toolBlocksDone.some(o => o == offset)) {
    msg._toolBlocksDone.push(offset)
  }
}

function applyToolMetadata(msg, meta) {
  if (!meta) return
  msg.metadata = { ...(msg.metadata || {}), ...meta }
  if (meta.toolEvents?.length) {
    msg._toolEvents = meta.toolEvents
  }
  // 流式阶段已实时推送的 workflow 事件不再被 metadata 整体替换，避免节点列表闪跳
  if (meta.workflowEvents?.length && !msg._workflowEvents?.length) {
    msg._workflowEvents = meta.workflowEvents
  }
  if (meta.toolBlockOffsets?.length) {
    msg._toolBlockOffsets = meta.toolBlockOffsets
  }
}

function splitContentByOffsets(msg) {
  const content = msg.content || ''
  const offsets = getToolBlockOffsets(msg)
  if (offsets.length === 0) return [{ type: 'text', text: content }]

  const segments = []
  let lastIdx = 0
  for (const offset of offsets) {
    if (offset > lastIdx && offset <= content.length) {
      segments.push({ type: 'text', text: content.substring(lastIdx, offset) })
    }
    segments.push({ type: 'tool', offset })
    lastIdx = offset
  }
  if (lastIdx < content.length) {
    segments.push({ type: 'text', text: content.substring(lastIdx) })
  }
  return segments
}

function isSegmentFinalized(msg, segment, index) {
  if (!msg?._streaming) return true
  if (segment.type !== 'text') return true
  const segments = splitContentByOffsets(msg)
  const lastTextIndex = [...segments].map((s, i) => ({ s, i })).reverse().find(item => item.s.type === 'text')?.i
  return index !== lastTextIndex
}

function formatElapsed(ms) {
  if (ms < 1000) return `耗时 ${ms}ms`
  return `耗时 ${(ms / 1000).toFixed(1)}s`
}

function goToKnowledge(knowledgeId, documentId) {
  const query = documentId ? { docId: String(documentId) } : {}
  router.push({ path: `/app/knowledge/${knowledgeId}`, query })
}

// ===== 顶部栏标题编辑 =====
function startTitleEdit() {
  titleEditValue.value = sessionTitle.value || '新对话'
  titleEditing.value = true
  nextTick(() => {
    const el = titleInputRef.value
    if (el) {
      const input = el.$el ? el.$el.querySelector('input') : el
      if (input) { input.focus(); input.select() }
    }
  })
}

async function confirmTitleEdit() {
  titleEditing.value = false
  const newTitle = titleEditValue.value.trim()
  if (!newTitle || newTitle === sessionTitle.value) return
  sessionTitle.value = newTitle
  if (sessionId.value) {
    try {
      await updateSessionTitle(sessionId.value, newTitle)
      window.dispatchEvent(new CustomEvent('session-title-updated'))
    } catch { /* ignore */ }
  }
}

function cancelTitleEdit() {
  titleEditing.value = false
  titleEditValue.value = ''
}

// ===== 文件抽屉 =====
function openFileDrawer() {
  // 首次打开提前置 loading，避免抽屉展开动画期间闪烁空状态
  if (!fileDrawerLoadedOnce.value) {
    fileDrawerLoading.value = true
  }
  fileDrawerOpen.value = true
}

async function onFileDrawerOpened(open) {
  if (!open || !sessionId.value) return
  await loadSessionFiles()
}

function refreshSessionFiles() {
  loadSessionFiles()
}

async function loadSessionFiles() {
  if (!sessionId.value) return
  fileDrawerLoading.value = true
  try {
    const res = await getSessionAttachments(sessionId.value)
    sessionAttachments.value = res.data || []
    const needRefresh = sessionAttachments.value.filter(a => a.objectKey)
    if (needRefresh.length > 0) {
      try {
        const refreshed = await refreshChatAttachmentPreviews(needRefresh)
        const refreshedByKey = new Map((refreshed.data || []).map(a => [a.objectKey, a]))
        sessionAttachments.value = sessionAttachments.value.map(a => {
          const r = refreshedByKey.get(a.objectKey)
          return r ? { ...a, ...r } : a
        })
      } catch { /* ignore */ }
    }
    fileDrawerLoadedOnce.value = true
  } catch {
    sessionAttachments.value = []
  } finally {
    fileDrawerLoading.value = false
  }
}

function openSessionFilePreview(att) {
  attachmentPreviewAtt.value = att
  attachmentPreviewOpen.value = true
}

// scrollToBottom 已在虚拟滚动区域定义

/** 轮询等待会话标题生成完成（轻量接口，跳过缓存） */
let pollTitleTimer = null
function pollSessionTitle(sid) {
  if (!sid) return
  if (pollTitleTimer) clearInterval(pollTitleTimer)
  let count = 0
  const maxRetries = 8
  const interval = 2000
  pollTitleTimer = setInterval(async () => {
    try {
      const res = await getSessionTitle(sid)
      const title = res.data
      if (title && title !== '新对话') {
        clearInterval(pollTitleTimer)
        pollTitleTimer = null
        sessionTitle.value = title
        window.dispatchEvent(new CustomEvent('session-title-updated'))
      }
    } catch {
      // ignore
    }
    count++
    if (count >= maxRetries) {
      clearInterval(pollTitleTimer)
      pollTitleTimer = null
    }
  }, interval)
}

/** scroll 事件处理器引用，用于 onUnmounted 移除 */
const scrollHandler = () => {
  handleScroll()
  const container = messagesRef.value
  if (container && container.scrollTop < 50 && hasMoreMessages.value && !loadingOlder.value && !streaming.value) {
    loadOlderMessages()
  }
}

onUnmounted(() => {
  voiceCleanup()
  if (pollTitleTimer) {
    clearInterval(pollTitleTimer)
    pollTitleTimer = null
  }
  const container = messagesRef.value
  if (container) {
    container.removeEventListener('scroll', scrollHandler)
  }
  document.removeEventListener('keydown', handleChatKeydown)
})

onMounted(async () => {
  const queryAgentId = route.query.agentId
  loadHistory()
  await loadAgents(queryAgentId || undefined)
  if (queryAgentId) {
    await loadCurrentAgent(queryAgentId)
    await loadAgentConfigVersions(queryAgentId)
    router.replace({ path: '/app/chat' })
  } else if (selectedAgentId.value) {
    await loadAgentConfigVersions(selectedAgentId.value)
  }

  // 滚动到顶部自动加载更早的消息 + 虚拟滚动距离检测
  const container = messagesRef.value
  if (container) {
    container.addEventListener('scroll', scrollHandler)
  }
  // 对话页面快捷键
  document.addEventListener('keydown', handleChatKeydown)
})

watch(() => route.params.sessionId, (newVal, oldVal) => {
  // sendMessage 内部 router.replace 触发的 watcher，跳过避免重新加载消息
  if (skipNextWatch.value) {
    skipNextWatch.value = false
    return
  }
  // 流式对话进行中，中断当前流
  if (streaming.value && abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
  // 切换对话时清空展开状态
  expandedRefsMap.value = new Map()
  refsSectionExpandedMap.value = new Map()
  loadHistory()
})

// 切换 Agent 时更新欢迎语
watch(selectedAgentId, (newId) => {
  if (newId && !sessionId.value) {
    loadCurrentAgent(newId)
  } else if (!newId) {
    currentAgent.value = null
  }
})

// 新建对话时重新查询 agent 列表，确保默认 agent 被选中
watch(sessionId, (newVal, oldVal) => {
  if (!newVal && oldVal) {
    loadAgents()
  }
})
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-canvas);
}

/* ===== 顶部栏 ===== */
.chat-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 32px;
  border-bottom: 1px solid var(--color-hairline);
  background: var(--color-canvas-soft);
  flex-shrink: 0;
  gap: 16px;
  width: 100%;
}

.chat-topbar-left {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}

.chat-topbar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 4px 8px;
  border-radius: 6px;
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s;
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
}

.chat-topbar-title:hover {
  background: var(--color-canvas-soft-2);
  border-color: var(--color-hairline);
}

.chat-topbar-title-icon {
  font-size: 12px;
  color: var(--color-mute);
  opacity: 0;
  transition: opacity 0.15s;
  flex-shrink: 0;
}

.chat-topbar-title:hover .chat-topbar-title-icon {
  opacity: 1;
}

.chat-topbar-title-edit {
  flex: 1;
  max-width: 360px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.chat-topbar-title-edit :deep(input) {
  font-size: 15px;
  font-weight: 600;
}

.btn-title-cancel {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas);
  color: var(--color-mute);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.btn-title-cancel:hover {
  background: var(--color-canvas-soft-2);
  color: var(--color-error);
  border-color: var(--color-hairline-strong);
}

.chat-topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.btn-topbar-file {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  border: none;
  background: var(--color-canvas-soft-2);
  color: var(--color-mute);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  transition: background 0.15s, color 0.15s;
}

.btn-topbar-file:hover {
  background: var(--color-hairline);
  color: var(--color-ink);
}

/* ===== 消息列表 ===== */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
}
.chat-messages::-webkit-scrollbar {
  width: 6px;
}
.chat-messages::-webkit-scrollbar-thumb {
  background: #d4d4d8;
  border-radius: 3px;
}

.load-more-area {
  text-align: center;
  padding: 12px 0;
}

/* 虚拟滚动容器 */
.history-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
}
.history-loading-icon {
  font-size: 24px;
  color: var(--color-mute);
}
.virtual-list-container {
  width: 100%;
  max-width: 800px;
  margin: 0 auto;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px;
}
.empty-logo {
  height: 64px;
  margin-bottom: 24px;
  object-fit: contain;
}
.welcome-content {
  text-align: center;
  max-width: 600px;
  margin-bottom: 24px;
  font-size: 15px;
  line-height: 1.7;
  color: var(--color-ink);
}
.welcome-content :deep(h1),
.welcome-content :deep(h2) {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 8px;
}
.welcome-content :deep(p) {
  margin: 0 0 8px;
  color: var(--color-mute);
}
.recommended-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 600px;
}
.btn-question {
  padding: 8px 16px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  font-size: 13px;
  color: var(--color-body);
  cursor: pointer;
  transition: all 0.15s;
}
.btn-question:hover {
  border-color: var(--color-link);
  color: var(--color-link);
  background: var(--color-link-bg-soft);
}
.no-default-hint {
  font-size: 13px;
  color: var(--color-mute);
  margin-top: 8px;
}
.no-default-hint a {
  color: var(--color-link);
  text-decoration: none;
}
.no-default-hint a:hover {
  text-decoration: underline;
}

/* 消息 */
.message {
  padding: 12px 32px;
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}
.message.assistant {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.message-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #7928ca, #ff0080);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  overflow: hidden;
}
.message-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.message-avatar-initial {
  line-height: 1;
}
.message-body {
  min-width: 0;
}
.message.assistant .message-body {
  flex: 1;
  width: 100%;
}
.message.user .message-body {
  text-align: right;
  width: 100%;
}
.message-meta {
  font-size: 12px;
  color: var(--color-mute);
  margin-bottom: 6px;
}
.message-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}
.btn-action-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  font-size: 12px;
}
.btn-action-text span {
  line-height: 1;
}
.message-content-wrapper {
  position: relative;
  width: 100%;
}
/* 用户消息：附件在上、气泡在下，整体靠右与头像侧对齐 */
.message.user .message-content-wrapper.user-message-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  width: 100%;
}
.message.user .user-message-attachments {
  display: inline-flex;
  flex-direction: row-reverse;
  flex-wrap: wrap-reverse;
  justify-content: flex-end;
  align-items: flex-end;
  gap: 8px;
  margin-bottom: 6px;
  max-width: 80%;
}
.message-content {
  font-size: 15px;
  line-height: 1.7;
  color: var(--color-ink);
  word-break: break-word;
}
.message.user .message-content {
  display: inline-block;
  background: var(--color-canvas-soft-2);
  padding: 10px 16px;
  border-radius: 12px 12px 2px 12px;
  text-align: left;
  max-width: 80%;
}
/* 用户消息气泡内紧凑样式 */
.message.user .message-content :deep(.markdown-preview p) {
  margin: 0;
}
.message.user .message-content :deep(.markdown-preview) {
  line-height: 1.5;
}

/* 复制按钮 */
.btn-copy {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 6px;
  padding: 4px 6px;
  background: none;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  color: var(--color-mute);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
}
.message:hover .btn-copy,
.message-actions:hover .btn-copy {
  opacity: 1;
}
.message-actions .btn-copy {
  opacity: 1;
}
.message.assistant .message-actions {
  justify-content: flex-start;
}
.message.user .message-actions {
  justify-content: flex-end;
}
.message-time {
  font-size: 12px;
  color: var(--color-mute);
  white-space: nowrap;
  opacity: 0.8;
}
.message.assistant .message-time {
  margin-left: auto;
}
.message.user .message-time {
  order: -1;
  margin-right: auto;
}
.btn-copy:hover {
  color: var(--color-body);
}
.btn-copy.copied {
  color: #16a34a;
  opacity: 1;
}
.btn-copy.starred {
  color: #f59e0b;
  opacity: 1;
}
.btn-delete:hover {
  color: #ef4444;
}
.btn-feedback:hover {
  color: var(--blue-500);
}
.btn-feedback.feedback-liked {
  color: #16a34a;
  opacity: 1;
}
.btn-feedback.feedback-disliked {
  color: #ef4444;
  opacity: 1;
}
.btn-copy.active {
  color: var(--color-link);
  background: var(--color-link-bg-soft);
}

/* 编辑消息 */
.edit-message-outer {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.message.user .edit-message-outer {
  text-align: left;
}
.edit-message-box {
  flex: 1;
  min-width: 0;
  background: var(--color-canvas-soft-2);
  border-radius: 12px 12px 2px 12px;
  padding: 10px 16px;
}
.edit-message-box :deep(textarea) {
  background: transparent;
  border: none;
  outline: none;
  box-shadow: none;
  font-size: 14px;
  line-height: 1.5;
  padding: 0;
  resize: none;
}
.edit-message-box :deep(textarea:focus) {
  border: none;
  outline: none;
  box-shadow: none;
}
.edit-btn {
  flex-shrink: 0;
  opacity: 1;
}
.edit-btn-send:not(:disabled) {
  color: var(--color-link);
}
.edit-btn-send:disabled {
  color: var(--color-hairline-strong);
  cursor: not-allowed;
}

/* 引用回复预览条 */
.reply-preview-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin: 8px 12px 8px;
  background: var(--color-canvas-soft-2);
  border-radius: 8px;
  border-left: 3px solid #0070f3;
}
.reply-preview-content {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 1.4;
  overflow: hidden;
}
.reply-preview-text {
  color: var(--color-mute);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
  width: 100%;
}
.reply-preview-close {
  flex-shrink: 0;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--color-mute);
  padding: 2px;
  display: flex;
  align-items: center;
  transition: color 0.15s;
}
.reply-preview-close:hover {
  color: var(--color-ink);
}

/* 引用回复内容展示 */
.reply-quote {
  display: flex;
  gap: 8px;
  padding: 6px 10px;
  margin-bottom: 6px;
  margin-left: 42px;
  background: rgba(0, 112, 243, 0.06);
  border-radius: 8px;
  border-left: 3px solid #0070f3;
  align-self: stretch;
  transition: background 0.15s;
}
.reply-quote.clickable {
  cursor: pointer;
}
.reply-quote.clickable:hover {
  background: rgba(0, 112, 243, 0.12);
}
.reply-quote-bar {
  display: none;
}
.reply-quote-content {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
}
.reply-quote-role {
  color: var(--color-link);
  font-weight: 500;
  margin-right: 6px;
  flex-shrink: 0;
}
.reply-quote-text {
  color: var(--color-mute);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline;
  max-width: 100%;
}
/* 引用跳转高亮 */
.message-highlight {
  animation: reply-highlight-flash 2s ease-out;
}
@keyframes reply-highlight-flash {
  0% { background: rgba(0, 112, 243, 0.18); }
  30% { background: rgba(0, 112, 243, 0.12); }
  100% { background: transparent; }
}

/* 原始内容弹窗 */
.raw-modal-content {
  margin: 0;
  padding: 0;
  background: none;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Menlo', 'Monaco', 'Consolas', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-code);
}

.raw-modal-meta-btn {
  appearance: none;
  border: none;
  background: none;
  color: var(--gray-400);
  font-size: 14px;
  cursor: pointer;
  padding: 2px 6px;
  margin-left: 8px;
  border-radius: 4px;
  vertical-align: middle;
  transition: all 0.15s;
  &:hover { color: var(--main-600); background: var(--gray-100); }
}

/* Markdown 渲染 */
.message-content :deep(h1),
.message-content :deep(h2),
.message-content :deep(h3),
.message-content :deep(h4),
.message-content :deep(h5),
.message-content :deep(h6) {
  margin: 16px 0 8px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--color-ink);
}
.message-content :deep(h1) { font-size: 1.5em; }
.message-content :deep(h2) { font-size: 1.3em; }
.message-content :deep(h3) { font-size: 1.15em; }
.message-content :deep(h4),
.message-content :deep(h5),
.message-content :deep(h6) { font-size: 1em; }
.message-content :deep(p) {
  margin: 0 0 12px;
}
.message-content :deep(code) {
  background: var(--color-canvas-soft-2);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Geist Mono', 'Menlo', monospace;
}
.message-content :deep(pre) {
  background: #171717;
  border-radius: 8px;
  padding: 16px;
  overflow-x: auto;
  margin: 12px 0;

  /* 细滚动条 */
  &::-webkit-scrollbar { height: 4px; }
  &::-webkit-scrollbar-track { background: transparent; }
  &::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.15); border-radius: 2px; }
  &::-webkit-scrollbar-thumb:hover { background: rgba(255, 255, 255, 0.25); }
}
.message-content :deep(pre code) {
  background: transparent !important;
  color: #e4e4e7;
  padding: 0;
  font-size: 13px;
  line-height: 1.6;
}
.message-content :deep(ul),
.message-content :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}
.message-content :deep(li > p),
.message-content :deep(ol > p),
.message-content :deep(ul > p) {
  margin: 2px 0;
}
.message-content :deep(li) {
  margin: 2px 0;
}
.message-content :deep(blockquote) {
  border-left: 3px solid var(--color-link);
  padding-left: 12px;
  margin: 12px 0;
  color: var(--color-body);
}
.message-content :deep(table) {
  border-collapse: collapse;
  margin: 12px 0;
  width: 100%;
}
.message-content :deep(th),
.message-content :deep(td) {
  border: 1px solid var(--color-hairline);
  padding: 8px 12px;
  text-align: left;
  font-size: 14px;
}
.message-content :deep(th) {
  background: var(--color-canvas-soft-2);
}

/* 输入区 */
.chat-input-wrapper {
  padding: 0 32px 24px;
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}
.chat-input-shell {
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  background: var(--color-canvas);
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s;
  position: relative;
}
.chat-input-shell:focus-within {
  border-color: var(--color-link);
  box-shadow: 0 0 0 3px rgba(0, 112, 243, 0.08);
}
.chat-input-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: var(--color-canvas-soft);
  border-bottom: 1px solid var(--color-hairline);
  position: relative;
}
.toolbar-loading-mask {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  border-radius: inherit;
}
.toolbar-loading-icon {
  font-size: 16px;
  color: var(--color-link);
}
.chat-toolbar-agent-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-input {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 8px 8px 4px;
  background: var(--color-canvas);
}
.chat-input-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

/* Agent 选择按钮 */
.btn-agent {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: var(--color-canvas-soft-2);
  color: var(--color-mute);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 16px;
  transition: background 0.15s;
  overflow: hidden;
}
.btn-agent:hover {
  background: var(--color-hairline);
}
.btn-toolbar-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--color-mute);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 15px;
  transition: background 0.15s, color 0.15s;
}
.btn-toolbar-icon:hover {
  background: var(--color-canvas-soft-2);
  color: var(--color-ink);
}
.btn-agent-avatar {
  width: 36px;
  height: 36px;
  object-fit: cover;
}
.btn-agent-initial {
  font-size: 14px;
  font-weight: 600;
  background: linear-gradient(135deg, #7928ca, #ff0080);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  line-height: 1;
}

/* Agent 下拉菜单项 */
.agent-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 160px;
}
.agent-menu-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}
span.agent-menu-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  background: linear-gradient(135deg, #7928ca, #ff0080);
  color: #fff;
}
.agent-menu-icon.default-icon {
  background: #0070f3;
  color: #fff;
}
.agent-menu-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agent-version-tag {
  font-size: 10px;
  padding: 1px 6px;
  background: var(--color-canvas-soft-2);
  color: var(--color-mute);
  border-radius: 100px;
  flex-shrink: 0;
}
.agent-default-tag {
  font-size: 10px;
  padding: 1px 6px;
  background: var(--color-info-bg);
  color: #2563eb;
  border-radius: 100px;
  flex-shrink: 0;
}
.empty-agent-tip {
  font-size: 13px;
  color: var(--color-body);
  white-space: nowrap;
}
.empty-agent-tip a {
  color: var(--color-link);
  font-weight: 500;
}

.config-version-select {
  margin-left: auto;
  flex-shrink: 0;
  min-width: 128px;
  max-width: 200px;
}
.version-option-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.version-option-num {
  font-size: 13px;
  color: var(--color-ink);
}
.version-status-tag {
  margin: 0;
  font-size: 11px;
  line-height: 18px;
  padding: 0 6px;
}
.version-status-tag.draft {
  background: var(--color-canvas-soft-2);
  color: var(--color-body);
}
.message-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
}
.btn-copy.speaking {
  color: var(--color-link);
}

.input-textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 15px;
  line-height: 1.5;
  font-family: inherit;
  color: var(--color-ink);
  background: transparent;
  max-height: 200px;
  min-height: 36px;
  display: flex;
  align-items: center;
}
.input-textarea::placeholder {
  color: var(--color-mute);
}
.hidden-file-input {
  display: none;
}
.btn-attach,
.btn-voice {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: var(--color-canvas-soft-2);
  color: var(--color-mute);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 16px;
}
.btn-attach:hover:not(:disabled),
.btn-voice:hover:not(:disabled) {
  background: var(--color-hairline);
}
.btn-attach:disabled,
.btn-voice:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-attach--uploading {
  background: var(--color-info-bg);
  color: var(--color-link);
  animation: attach-btn-pulse 1s ease-in-out infinite;
}
@keyframes attach-btn-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(0, 112, 243, 0.35); }
  50% { box-shadow: 0 0 0 6px rgba(0, 112, 243, 0); }
}
.btn-voice.listening {
  background: var(--color-error-bg);
  color: #ef4444;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.25);
}
.voice-listening-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 8px;
  background: var(--color-error-bg);
  border: 1px solid var(--color-error-soft);
}
.voice-listening-text {
  font-size: 12px;
  color: #ef4444;
  white-space: nowrap;
  user-select: none;
}
.msg-att-thumb {
  position: relative;
  width: 52px;
  height: 52px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--color-hairline);
  flex-shrink: 0;
  display: block;
  background: var(--color-canvas-soft-2);
  padding: 0;
  cursor: pointer;
}
.msg-att-hover-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  background: rgba(0, 0, 0, 0.48);
  color: #fff;
  opacity: 0;
  transition: opacity 0.15s ease;
  pointer-events: none;
}
.msg-att-thumb:hover .msg-att-hover-mask,
.att-thumb-wrap:hover .msg-att-hover-mask {
  opacity: 1;
}
.msg-att-hover-mask .mask-icon {
  font-size: 16px;
}
.msg-att-hover-mask .mask-text {
  font-size: 11px;
  line-height: 1;
}
.msg-att-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.msg-att-thumb--video {
  background: #18181b;
}
.msg-att-play-badge {
  position: absolute;
  right: 3px;
  bottom: 3px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.92);
  line-height: 1;
  pointer-events: none;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.45);
}
.msg-att-play-badge.sm {
  font-size: 12px;
}
.msg-att-file-tag {
  font-size: 12px;
  color: var(--color-body);
  padding: 4px 10px;
  background: var(--color-canvas-soft-2);
  border-radius: 6px;
}
.pending-attachments {
  margin-top: 8px;
}
.pending-att-count {
  font-size: 12px;
  color: var(--color-mute);
  display: block;
  margin-bottom: 6px;
}
.pending-att-thumbs {
  display: flex;
  flex-direction: row-reverse;
  flex-wrap: wrap-reverse;
  justify-content: flex-end;
  gap: 6px;
}
.pending-att-item {
  position: relative;
  flex-shrink: 0;
}
.att-thumb-wrap {
  position: relative;
  width: 48px;
  height: 48px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--color-hairline);
  padding: 0;
  background: var(--color-canvas-soft-2);
  cursor: pointer;
}
.att-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.pending-att-item .att-name {
  font-size: 12px;
  color: var(--color-body);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pending-att-item .att-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  padding: 0;
  cursor: pointer;
  color: var(--color-mute);
  z-index: 1;
}
.att-remove {
  border: none;
  background: transparent;
  color: var(--color-mute);
  cursor: pointer;
  padding: 0;
  line-height: 1;
}
.btn-send {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: #0070f3;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.15s;
}
.btn-send:hover:not(:disabled) {
  background: #005bc4;
}
.btn-send:disabled {
  background: var(--color-hairline-strong);
  cursor: not-allowed;
}
.btn-stop {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: #ef4444;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.15s;
  font-size: 18px;
}
.btn-stop:hover {
  background: #dc2626;
}

/* 工具块内联容器 */
.tool-block-inline {
  margin: 8px 0;
  width: 100%;
}
.capability-block-inline {
  margin-top: 8px;
  width: 100%;
}

.workflow-block-inline {
  margin: 8px 0;
  width: 100%;
}

/* 深度思考面板 */
.reasoning-panel {
  width: 100%;
  margin-bottom: 8px;
  border: 1px solid var(--color-warn-bg-deep);
  border-radius: 8px;
  overflow: hidden;
}
.reasoning-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: var(--color-warn-bg);
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  color: #a16207;
  transition: background 0.15s;
}
.reasoning-header:hover {
  background: var(--color-warn-bg-deep);
}
.reasoning-icon {
  color: #eab308;
  font-size: 14px;
}
.reasoning-title {
  font-weight: 500;
}
.reasoning-spinner {
  color: #eab308;
  font-size: 12px;
  animation: spin 1s linear infinite;
}
.reasoning-header .tool-expand-icon {
  margin-left: auto;
  font-size: 12px;
  color: #ca8a04;
  transition: transform 0.2s ease;
}
.reasoning-header .tool-expand-icon.expanded {
  transform: rotate(90deg);
}
.reasoning-content {
  padding: 10px 12px;
  background: var(--color-warn-bg);
  font-size: 13px;
  color: var(--color-mute);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow-y: auto;
}

/* 敏感词拦截提示 */
.sensitive-block-alert {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.6;
  animation: fadeIn 0.3s ease;
}
.sensitive-block-alert.user_input {
  background: var(--color-error-bg);
  border: 1px solid var(--color-error-soft);
  color: #991b1b;
}
.sensitive-block-alert.user_input .sensitive-block-icon {
  color: #ef4444;
}
.sensitive-block-alert.ai_output {
  background: var(--color-warn-bg);
  border: 1px solid var(--color-warn-bg-deep);
  color: #9a3412;
}
.sensitive-block-alert.ai_output .sensitive-block-icon {
  color: #f97316;
}
.sensitive-block-icon {
  font-size: 16px;
  flex-shrink: 0;
  margin-top: 2px;
}
.sensitive-block-text {
  flex: 1;
}

.error-retry-block {
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.24);
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 10px;
  font-size: 14px;
  line-height: 1.6;
  animation: fadeIn 0.3s ease;
}
.error-retry-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.error-retry-icon {
  color: #d97706;
  font-size: 15px;
  flex-shrink: 0;
}
.error-retry-title {
  font-weight: 600;
  color: #92400e;
}
.error-retry-count {
  margin-left: auto;
  font-size: 12px;
  color: #92400e;
  background: rgba(245, 158, 11, 0.14);
  padding: 1px 8px;
  border-radius: 4px;
  font-family: var(--font-mono);
}
.error-retry-message {
  color: #92400e;
}

/* 1.3 结构化错误事件 */
.error-block {
  background: var(--color-error-bg);
  border: 1px solid var(--color-error-soft);
  border-radius: 10px;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.6;
  animation: fadeIn 0.3s ease;
}
.error-block-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.error-block-icon {
  color: #ef4444;
  font-size: 16px;
  flex-shrink: 0;
}
.error-block-title {
  font-weight: 600;
  color: #991b1b;
}
.error-block-code {
  margin-left: auto;
  font-size: 12px;
  color: #991b1b;
  background: rgba(239, 68, 68, 0.1);
  padding: 1px 8px;
  border-radius: 4px;
  font-family: var(--font-mono);
}
.error-block-message {
  color: #991b1b;
}

.input-hint {
  text-align: center;
  font-size: 12px;
  color: var(--color-mute);
  margin-top: 8px;
}
.token-pill {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  padding: 2px 10px;
  background: var(--color-canvas-soft-2);
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  font-size: 12px;
  white-space: nowrap;
}
.token-pill-icon {
  color: #f59e0b;
  font-size: 12px;
}
.token-pill-value {
  color: var(--color-ink);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.token-pill-label {
  color: var(--color-mute);
}
/* RAG 引用样式 */
.rag-references {
  margin-top: 12px;
  padding: 12px;
  background: var(--blue-50);
  border-radius: 8px;
  border: 1px solid var(--blue-200);
}
.rag-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--blue-700);
  cursor: pointer;
  user-select: none;
}
.rag-header .anticon:first-child {
  font-size: 10px;
  transition: transform 0.2s;
}
.rag-header .anticon:first-child.expanded {
  transform: rotate(90deg);
}
.rag-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}
.rag-item {
  background: var(--color-canvas);
  border-radius: 6px;
  border: 1px solid var(--blue-200);
  border-left: 3px solid var(--blue-400);
  overflow: hidden;
}
.rag-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.15s;
}
.rag-item-header:hover {
  background: var(--blue-50);
}
.rag-item-header .anticon {
  font-size: 10px;
  color: var(--blue-400);
  transition: transform 0.2s;
}
.rag-item-header .anticon.expanded {
  transform: rotate(90deg);
}
.rag-title-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}
.rag-title-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: auto;
}
.rag-doc-name {
  flex: 1;
  font-size: 13px;
  color: var(--gray-700);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rag-qa-tag {
  flex-shrink: 0;
  font-size: 12px;
}
.rag-score {
  font-size: 12px;
  color: var(--blue-600);
  font-weight: 600;
  background: var(--blue-50);
  border: 1px solid var(--blue-200);
  border-radius: 4px;
  padding: 1px 6px;
}
.rag-nav-btn {
  font-size: 12px;
  color: var(--blue-500);
  margin-left: 4px;
  cursor: pointer;
  transition: color 0.2s;
}
.rag-nav-btn:hover {
  color: var(--blue-600);
}
.rag-item-content {
  padding: 12px;
  background: var(--gray-25, #fafafa);
  border-top: 1px solid var(--blue-100);
  font-size: 12px;
  color: var(--gray-600);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

/* 状态加载样式 */
.status-content {
  display: flex;
  align-items: center;
}
.status-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--color-canvas-soft-2);
  border-radius: 12px;
  animation: fadeIn 0.3s ease;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}
.status-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--color-hairline);
  border-top-color: var(--color-link);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.status-text {
  font-size: 13px;
  color: var(--color-body);
}

/* 耗时显示 */
.reply-elapsed {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-mute);
}
</style>

<style>
.chat-upload-tooltip .chat-upload-hint {
  display: block;
  white-space: pre-line;
  line-height: 1.5;
}
[data-theme="dark"] .toolbar-loading-mask {
  background: rgba(24, 24, 27, 0.7);
}
[data-theme="dark"] .message.user .message-content {
  background: #27272a;
}
[data-theme="dark"] .edit-message-box {
  background: #27272a;
}
[data-theme="dark"] .reply-preview-bar {
  background: #27272a;
}
[data-theme="dark"] .message-content code {
  background: #27272a;
}
[data-theme="dark"] .reasoning-panel {
  border-color: #3b2f0a;
}
[data-theme="dark"] .reasoning-header {
  background: #3b2f0a;
  color: #fbbf24;
}
[data-theme="dark"] .reasoning-header:hover {
  background: #422006;
}
[data-theme="dark"] .reasoning-content {
  background: #27272a;
}

/* ===== 文件抽屉 ===== */
.file-drawer-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
}

.file-drawer-loading-icon {
  font-size: 24px;
  color: var(--color-mute);
}

.btn-drawer-refresh {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas);
  color: var(--color-mute);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.btn-drawer-refresh:hover:not(.refreshing) {
  background: var(--color-canvas-soft-2);
  color: var(--color-ink);
  border-color: var(--color-hairline-strong);
}

.btn-drawer-refresh.refreshing {
  cursor: not-allowed;
  color: var(--color-mute);
}

.file-drawer-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  text-align: center;
  color: var(--color-mute);
}

.file-drawer-empty-icon {
  font-size: 48px;
  color: var(--color-hairline-strong);
  margin-bottom: 16px;
}

.file-drawer-empty p {
  margin: 0;
  font-size: 14px;
}

.file-drawer-empty-hint {
  font-size: 12px !important;
  color: var(--color-mute) !important;
  margin-top: 8px !important;
}

.file-drawer-section {
  margin-bottom: 24px;
}

.file-drawer-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-body);
  margin-bottom: 12px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--color-hairline);
}

.file-drawer-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
</style>
