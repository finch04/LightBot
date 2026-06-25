<template>
  <div class="page" :class="{ 'page--loading': pageLoading || saving || publishing }">
    <!-- 加载遮罩 -->
    <div v-if="pageLoading" class="sync-overlay">
      <div class="sync-overlay-content">
        <a-spin size="large" />
        <p class="sync-overlay-text">加载中...</p>
      </div>
    </div>

    <div class="page-header">
      <div class="page-header-left">
        <div class="page-header-titles">
          <button class="btn-back" @click="handleGoBack">
            <ArrowLeftOutlined /> 返回
          </button>
          <div class="page-title-row">
            <h1 class="page-title">{{ headerName || 'Agent 详情' }}</h1>
            <a-tooltip :title="agent.id ? `ID：${agent.id}` : '新建保存后生成 ID'">
              <button type="button" class="btn-agent-id" @click="copyAgentId">
                <IdcardOutlined />
              </button>
            </a-tooltip>
          </div>
          <p class="page-desc">{{ headerDescription || '暂无描述' }}</p>
        </div>
      </div>
      <div class="header-actions">
        <span class="agent-status-badge" :class="agentStatusClass">{{ agentStatusText }}</span>
        <button v-if="agent.agentType === 'workflow'" class="btn-outline" @click="goWorkflowEdit">
          <SettingOutlined /> 工作流编排
        </button>
        <button class="btn-outline" @click="startChat">
          <MessageOutlined /> 对话
        </button>
        <button
          v-if="agent.agentType === 'workflow'"
          class="btn-outline"
          @click="handleSaveWorkflowBasic"
          :disabled="saving || isVersionPreview"
        >
          <SaveOutlined /> 保存
        </button>
        <button
          v-if="agent.agentType !== 'workflow'"
          class="btn-outline"
          @click="openVersionDrawer"
        >
          <HistoryOutlined /> 版本管理
        </button>
        <button v-if="agent.agentType !== 'workflow'" class="btn-outline" @click="handleSave" :disabled="saving || isVersionPreview">
          <SaveOutlined /> 暂存
        </button>
        <button
          v-if="agent.agentType !== 'workflow'"
          class="btn-primary"
          @click="handlePublish"
          :disabled="publishing || saving || isVersionPreview"
        >
          <CheckCircleOutlined /> {{ publishing ? '发布中...' : '发布' }}
        </button>
      </div>
    </div>

    <a-alert
      v-if="isVersionPreview"
      type="info"
      show-icon
      class="version-preview-banner-top"
      :message="`正在预览历史版本 v${selectedVersion}（只读）`"
    >
      <template #description>
        <button type="button" class="link-btn" @click="selectAgentVersion('draft')">返回当前编辑</button>
      </template>
    </a-alert>

    <div
      class="agent-edit-surface"
      :class="{ 'is-version-preview': isVersionPreview }"
      @mousedown.capture="blockPreviewInteraction"
      @click.capture="blockPreviewInteraction"
      @keydown.capture="blockPreviewKeydown"
      @input.capture="blockPreviewInput"
      @change.capture="blockPreviewInput"
    >
    <div class="content-grid">
      <div class="content-grid-main">
      <!-- 基本信息 -->
      <div class="panel panel-stretch panel--basic">
        <div class="panel-header">
          <h3>基本信息</h3>
          <a-tooltip v-if="isVersionPreview" title="基本信息不受版本管理，修改后不会随版本回滚">
            <span class="non-version-badge"><InfoCircleOutlined /> 不受版本管理</span>
          </a-tooltip>
        </div>
        <div class="panel-body" :class="{ 'preview-lock-zone': isVersionPreview }">
        <a-form :model="agent" :label-col="{ span: 6 }">
          <a-form-item label="名称">
            <a-input v-model:value="agent.name" placeholder="Agent 名称" :maxlength="50" :disabled="isVersionPreview" :readonly="isVersionPreview" show-count />
          </a-form-item>
          <a-form-item label="头像">
            <div class="avatar-upload" :class="{ 'is-readonly': isVersionPreview }">
              <div class="avatar-preview" :class="{ 'has-avatar': avatarUrl }">
                <img v-if="avatarUrl" :src="avatarUrl" alt="avatar" class="avatar-img" @error="agent.avatar = ''" />
                <span v-else class="avatar-placeholder">{{ (agent.name || 'A')[0] }}</span>
                <div v-if="!isVersionPreview" class="avatar-overlay" @click="triggerAvatarUpload">
                  <UploadOutlined />
                </div>
              </div>
              <input ref="avatarInputRef" type="file" accept=".jpg,.jpeg,.png,.gif,.webp,.bmp" style="display: none" :disabled="isVersionPreview" @change="onAvatarFileChange" />
              <span class="avatar-tip">支持 jpg/jpeg/png/gif/webp，建议 200x200</span>
            </div>
          </a-form-item>
          <a-form-item label="描述">
            <a-textarea v-model:value="agent.description" :rows="2" placeholder="Agent 描述（不超过50字）" :maxlength="50" :disabled="isVersionPreview" :readonly="isVersionPreview" show-count />
          </a-form-item>
          <!-- 类型选择：放在前面，影响后续字段显示 -->
          <a-form-item label="类型">
            <a-select v-model:value="agent.agentType" style="width: 100%" :disabled="!!agentId || isVersionPreview">
              <a-select-option value="chat">对话型</a-select-option>
              <a-select-option value="workflow">工作流型</a-select-option>
            </a-select>
            <div v-if="agentId" class="param-hint">Agent 类型创建后不可修改</div>
          </a-form-item>
          <!-- 欢迎语和推荐问题：对话页展示，工作流型也可配置 -->
          <a-form-item label="欢迎语">
            <a-textarea v-model:value="agent.welcomeMessage" :rows="2" :maxlength="200" show-count placeholder="对话时显示的欢迎语（可选）" :disabled="isVersionPreview" :readonly="isVersionPreview" />
          </a-form-item>
          <a-form-item label="推荐问题">
            <div class="inline-field-block">
              <div class="inline-field-toolbar">
                <button class="btn-ai-sm" :disabled="generatingQuestions || isVersionPreview" @click="handleGenerateQuestions">
                  <ThunderboltOutlined :spin="generatingQuestions" />
                  {{ generatingQuestions ? '生成中...' : 'AI 生成' }}
                </button>
                <button
                  v-if="recommendedQuestions.length < 3"
                  type="button"
                  class="btn-add-inline"
                  :disabled="isVersionPreview"
                  @click="addRecommendedQuestion"
                >
                  <PlusOutlined /> 添加
                </button>
              </div>
              <div v-if="recommendedQuestions.length === 0" class="sub-config-empty">暂无推荐问题，最多 3 条</div>
              <div v-else class="config-list-scroll config-list-scroll--compact">
                <div v-for="(q, i) in recommendedQuestions" :key="i" class="list-table-row list-table-row--2col">
                  <a-input v-model:value="recommendedQuestions[i]" placeholder="输入推荐问题（不超过 30 字）" :maxlength="30" size="small" :disabled="isVersionPreview" />
                  <a-tooltip title="删除">
                    <button type="button" class="btn-icon-sm danger" :disabled="isVersionPreview" @click="removeRecommendedQuestion(i)">
                      <CloseOutlined />
                    </button>
                  </a-tooltip>
                </div>
              </div>
            </div>
          </a-form-item>
                </a-form>
        </div>
      </div>

      </div>

      <!-- 模型参数 + 对话配置（同一卡片 Tab 切换） -->
      <div v-if="agent.agentType !== 'workflow'" class="content-grid-side">
        <div class="panel panel-stretch panel--config-unified">
          <div class="panel-header">
            <h3>模型配置</h3>
          </div>
          <a-tabs
            v-model:activeKey="configTab"
            style="padding-top: 0px;"
            class="config-panel-tabs"
            :class="{ 'config-panel-tabs--preview': isVersionPreview }"
          >
            <template #rightExtra>
              <button
                v-if="configTab === 'model'"
                type="button"
                class="btn-ai-sm"
                @click="confirmRestoreDefaults"
                :disabled="!agentConfig.providerId || isVersionPreview"
              >
                <UndoOutlined /> 恢复默认
              </button>
              <span v-if="configTab === 'model'" class="panel-tip">根据提供商动态显示</span>
            </template>
            <a-tab-pane key="prompt" tab="提示词">
              <div class="config-tab-pane-body" :class="{ 'preview-lock-zone': isVersionPreview }">
                <a-form :model="agent" :label-col="{ span: 6 }">
                  <a-form-item v-if="agent.agentType !== 'workflow'" label="系统提示词">
                    <div class="prompt-wrapper">
                      <a-textarea
                        v-model:value="agent.systemPrompt"
                        :rows="6"
                        :maxlength="2000"
                        show-count
                        placeholder="定义 Agent 的行为和角色，可使用 {{变量名}} 引用下方配置的变量..."
                        :disabled="isVersionPreview"
                        :readonly="isVersionPreview"
                      />
                      <a-tooltip :title="generatingPrompt ? '生成中...' : 'AI生成提示词'">
                        <button class="btn-ai-icon" :disabled="generatingPrompt || isVersionPreview" @click="handleGeneratePrompt">
                          <ThunderboltOutlined :spin="generatingPrompt" />
                        </button>
                      </a-tooltip>
                    </div>
                    <div class="prompt-var-tip">
                      提示词中变量的选项来自下方「变量配置」。可通过入参变量表单填写，或对话请求的
                      <code>biz_params</code> 字段传递；传入的值将替换提示词中对应的 <code v-pre>{{变量名}}</code> 位置。
                    </div>
                    <div class="prompt-insert-vars">
                      <template v-if="validPromptVariables.length">
                        <span class="insert-label">插入变量：</span>
                        <button
                          v-for="v in validPromptVariables"
                          :key="v.key"
                          type="button"
                          class="var-insert-btn"
                          :disabled="isVersionPreview"
                          @click="insertPromptVariable(v.key)"
                        >
                          {{ v.label || v.key }}
                        </button>
                      </template>
                      <button
                        v-if="agent.systemPrompt"
                        type="button"
                        class="var-insert-btn preview-toggle-btn"
                        :class="{ active: promptPreviewVisible }"
                        @click="promptPreviewVisible = !promptPreviewVisible"
                      >
                        {{ promptPreviewVisible ? '隐藏预览' : '预览替换' }}
                      </button>
                    </div>
                    <div v-if="promptPreviewVisible && agent.systemPrompt" class="prompt-preview-box">
                      <div class="prompt-preview-label">变量替换预览（使用默认值）</div>
                      <pre class="prompt-preview-content" v-html="promptPreviewHtml"></pre>
                    </div>
                  </a-form-item>
                  <!-- 变量配置 -->
                  <div v-if="agent.agentType !== 'workflow'" class="sub-config-card">
                    <div class="sub-config-card-header">
                      <div>
                        <h4 class="sub-config-card-title">变量配置</h4>
                        <p class="sub-config-card-desc">
                          在系统提示词中用 <code v-pre>{{变量名}}</code> 引用，支持中英文
                        </p>
                      </div>
                      <button type="button" class="btn-add-inline" :disabled="isVersionPreview" @click="addPromptVariable">
                        <PlusOutlined /> 添加
                      </button>
                    </div>
                    <div v-if="promptVariables.length === 0" class="sub-config-empty">暂无变量</div>
                    <template v-else>
                      <div class="list-table-head list-table-head--4col">
                        <span>变量名</span>
                        <span>显示名称</span>
                        <span>默认值</span>
                        <span class="col-action">操作</span>
                      </div>
                      <div class="config-list-scroll">
                        <div v-for="(v, idx) in promptVariables" :key="v._id" class="list-table-row list-table-row--4col">
                          <a-input v-model:value="v.key" placeholder="company_name" size="small" :disabled="isVersionPreview" />
                          <a-input v-model:value="v.label" placeholder="显示名称" size="small" :disabled="isVersionPreview" />
                          <a-input v-model:value="v.defaultValue" placeholder="可选" size="small" :disabled="isVersionPreview" />
                          <a-tooltip title="删除">
                            <button type="button" class="btn-icon-sm danger" :disabled="isVersionPreview" @click="removePromptVariable(idx)">
                              <DeleteOutlined />
                            </button>
                          </a-tooltip>
                        </div>
                      </div>
                    </template>
                  </div>
                </a-form>
              </div>
            </a-tab-pane>
            <a-tab-pane key="model" tab="模型参数">
              <div class="config-tab-pane-body" :class="{ 'preview-lock-zone': isVersionPreview }">
        <a-form :model="agentConfig" :label-col="{ span: 6 }">

          <a-form-item label="模型">
            <ModelSelect
              :model-value="modelSelectValue"
              :disabled="isVersionPreview"
              @change="onModelSelectChange"
            />
          </a-form-item>

          <!-- 模型能力（嵌套在模型参数内，参考变量配置） -->
          <div v-if="capabilityFields.length" class="sub-config-card sub-config-card--model">
            <div class="sub-config-card-header">
              <div class="sub-config-card-title-row">
                <span class="capability-collapse-trigger" @click="capabilityCollapsed = !capabilityCollapsed">
                  <RightOutlined v-if="capabilityCollapsed" class="capability-collapse-icon" />
                  <DownOutlined v-else class="capability-collapse-icon" />
                </span>
                <h4 class="sub-config-card-title">模型能力</h4>
                <a-tooltip
                  title="多模态、联网搜索等由模型提供商动态提供；开启后对话页将显示对应入口。需先开启「多模态」才能配置图片/视频/语音输入。"
                  overlay-class-name="no-flip-tooltip"
                  :overlay-style="{ maxWidth: '320px' }"
                  placement="topLeft"
                >
                  <QuestionCircleOutlined class="field-hint-icon" />
                </a-tooltip>
              </div>
              <button
                type="button"
                class="btn-add-inline"
                :disabled="isVersionPreview || !hasCapabilitySwitches"
                @click="toggleAllCapabilities"
              >
                <CheckOutlined v-if="!allCapabilitiesEnabled" />
                <CloseOutlined v-else />
                {{ allCapabilitiesEnabled ? '全部关闭' : '全部开启' }}
              </button>
            </div>
            <div v-show="!capabilityCollapsed" class="capability-grid">
              <div class="capability-grid-primary">
                <template v-for="field in primaryCapabilityFields" :key="field.key">
                  <div
                    v-if="field.type === 'switch'"
                    class="capability-option-item"
                    :class="{ 'is-disabled': isCapabilityFieldDisabled(field) }"
                  >
                    <span class="capability-option-label">{{ field.label }}</span>
                    <a-switch
                      v-model:checked="agentConfig[field.key]"
                      size="small"
                      :disabled="isCapabilityFieldDisabled(field)"
                    />
                    <span class="capability-option-status">{{ agentConfig[field.key] ? '开' : '关' }}</span>
                    <a-tooltip
                      v-if="field.hint"
                      :title="field.hint"
                      overlay-class-name="no-flip-tooltip"
                      :overlay-style="{ maxWidth: '320px' }"
                      placement="topLeft"
                    >
                      <QuestionCircleOutlined class="tool-option-help" />
                    </a-tooltip>
                  </div>
                </template>
              </div>
              <div v-if="webSearchSubFields.length" class="capability-grid-sub">
                <div class="capability-sub-header">联网搜索配置</div>
                <div class="capability-grid-sub-items">
                  <template v-for="field in webSearchSubFields" :key="field.key">
                    <div v-if="field.type === 'switch'" class="capability-option-item">
                      <span class="capability-option-label">{{ field.label }}</span>
                      <a-switch
                        v-model:checked="agentConfig[field.key]"
                        size="small"
                        :disabled="isCapabilityFieldDisabled(field)"
                      />
                      <span class="capability-option-status">{{ agentConfig[field.key] ? '开' : '关' }}</span>
                      <a-tooltip
                        v-if="field.hint"
                        :title="field.hint"
                        overlay-class-name="no-flip-tooltip"
                        :overlay-style="{ maxWidth: '320px' }"
                        placement="topLeft"
                      >
                        <QuestionCircleOutlined class="tool-option-help" />
                      </a-tooltip>
                    </div>
                    <div v-else-if="field.type === 'number'" class="capability-option-item capability-option-item--number">
                      <span class="capability-option-label">{{ field.label }}</span>
                      <a-input-number
                        v-model:value="agentConfig[field.key]"
                        :min="field.min"
                        :max="field.max"
                        :step="field.step"
                        size="small"
                        class="capability-number-input"
                        :disabled="isVersionPreview"
                      />
                      <a-tooltip
                        v-if="field.hint"
                        :title="field.hint"
                        overlay-class-name="no-flip-tooltip"
                        :overlay-style="{ maxWidth: '320px' }"
                        placement="topLeft"
                      >
                        <QuestionCircleOutlined class="tool-option-help" />
                      </a-tooltip>
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </div>

          <a-form-item v-for="field in modelTuneFields" :key="field.key" :label="field.label">
            <div v-if="field.type === 'select'" class="field-control-row">
              <a-select
                v-model:value="agentConfig[field.key]"
                placeholder="请选择"
                class="field-control-grow"
                :disabled="isVersionPreview"
              >
                <a-select-option v-for="opt in field.options" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </a-select-option>
              </a-select>
              <a-tooltip
                v-if="field.hint"
                :title="field.hint"
                overlay-class-name="no-flip-tooltip"
                :overlay-style="{ maxWidth: '320px' }"
                placement="topLeft"
              >
                <QuestionCircleOutlined class="field-hint-icon" />
              </a-tooltip>
            </div>
            <div v-else-if="field.type === 'slider'" class="field-control-row field-control-row--slider">
              <a-slider
                v-model:value="agentConfig[field.key]"
                :min="field.min"
                :max="field.max"
                :step="field.step"
                class="field-control-grow"
                :disabled="isVersionPreview"
              />
              <span class="param-value">{{ agentConfig[field.key] }}</span>
              <a-tooltip
                v-if="field.hint"
                :title="field.hint"
                overlay-class-name="no-flip-tooltip"
                :overlay-style="{ maxWidth: '320px' }"
                placement="topLeft"
              >
                <QuestionCircleOutlined class="field-hint-icon" />
              </a-tooltip>
            </div>
            <div v-else-if="field.type === 'number'" class="field-control-row">
              <a-input-number
                v-model:value="agentConfig[field.key]"
                :min="field.min"
                :max="field.max"
                :step="field.step"
                class="field-control-grow"
                :disabled="isVersionPreview"
              />
              <a-tooltip
                v-if="field.hint"
                :title="field.hint"
                overlay-class-name="no-flip-tooltip"
                :overlay-style="{ maxWidth: '320px' }"
                placement="topLeft"
              >
                <QuestionCircleOutlined class="field-hint-icon" />
              </a-tooltip>
            </div>
            <div v-else-if="field.type === 'switch'" class="field-control-row">
              <a-switch v-model:checked="agentConfig[field.key]" :disabled="isVersionPreview" />
              <span class="tool-option-value">{{ agentConfig[field.key] ? '已开启' : '已关闭' }}</span>
              <a-tooltip
                v-if="field.hint"
                :title="field.hint"
                overlay-class-name="no-flip-tooltip"
                :overlay-style="{ maxWidth: '320px' }"
                placement="topLeft"
              >
                <QuestionCircleOutlined class="field-hint-icon" />
              </a-tooltip>
            </div>
            <div v-else class="field-control-row">
              <a-input v-model:value="agentConfig[field.key]" placeholder="请输入" class="field-control-grow" :disabled="isVersionPreview" />
              <a-tooltip
                v-if="field.hint"
                :title="field.hint"
                overlay-class-name="no-flip-tooltip"
                :overlay-style="{ maxWidth: '320px' }"
                placement="topLeft"
              >
                <QuestionCircleOutlined class="field-hint-icon" />
              </a-tooltip>
            </div>
          </a-form-item>
                </a-form>
              </div>
            </a-tab-pane>
            <a-tab-pane key="chat" tab="对话配置">
              <div class="config-tab-pane-body config-tab-pane-body--chat" :class="{ 'preview-lock-zone': isVersionPreview }">
            <a-form :model="agentConfig" :label-col="{ span: 6 }" class="panel-form">

              <a-form-item label="文件读取">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <a-switch v-model:checked="agentConfig.enableFileRead" :disabled="isVersionPreview" />
                  <span class="tool-option-value">{{ agentConfig.enableFileRead ? '已启用' : '未启用' }}</span>
                  <a-tooltip
                    title="开启后对话页可上传文档，与多模态图片/视频独立"
                    overlay-class-name="no-flip-tooltip"
                    :overlay-style="{ maxWidth: '360px' }"
                    placement="topLeft"
                  >
                    <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
                  </a-tooltip>
                </div>
                <div class="param-hint">支持 MD/TXT/PDF/Word/PPT/Excel/CSV/HTML 等</div>
              </a-form-item>
              <a-form-item label="内容安全扫描" v-if="agentConfig.enableFileRead">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <a-switch v-model:checked="agentConfig.enableContentSecurityScan" :disabled="isVersionPreview" />
                  <span class="tool-option-value">{{ agentConfig.enableContentSecurityScan ? '已启用' : '未启用' }}</span>
                  <a-tooltip
                    title="扫描上传文件中的提示词注入和敏感信息，开启后检测到风险将拒绝处理"
                    overlay-class-name="no-flip-tooltip"
                    :overlay-style="{ maxWidth: '360px' }"
                    placement="topLeft"
                  >
                    <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
                  </a-tooltip>
                </div>
              </a-form-item>
              <a-form-item label="流式输出">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <a-switch v-model:checked="agentConfig.streamOutput" :disabled="isVersionPreview" />
                  <span class="tool-option-value">{{ agentConfig.streamOutput !== false ? '已启用' : '未启用' }}</span>
                  <a-tooltip
                    title="开启后模型回复逐字流式展示；关闭后等待完整回复再一次性展示"
                    overlay-class-name="no-flip-tooltip"
                    :overlay-style="{ maxWidth: '320px' }"
                    placement="topLeft"
                  >
                    <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
                  </a-tooltip>
                </div>
                <div class="param-hint">默认开启流式输出</div>
              </a-form-item>
          <a-form-item label="上下文条数">
            <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
              <a-input-number
                v-model:value="agentConfig.maxContextMessages"
                :min="1"
                :max="50"
                :step="5"
                placeholder="默认20"
                style="flex: 1"
                :disabled="isVersionPreview"
              />
              <a-tooltip
                title="与模型对话时最多携带的历史消息条数。条数越多上下文越完整，但消耗的Token也越多"
                overlay-class-name="no-flip-tooltip"
                :overlay-style="{ maxWidth: '320px' }"
                placement="topLeft"
              >
                <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
              </a-tooltip>
            </div>
            <div class="param-hint">与模型对话时最多携带的历史消息条数，默认20条</div>
          </a-form-item>
          <a-form-item label="上下文摘要">
            <div style="display: flex; align-items: center; gap: 8px;">
              <a-switch v-model:checked="agentConfig.enableSummary" :disabled="isVersionPreview" />
              <span class="tool-option-value">{{ agentConfig.enableSummary ? '已启用' : '未启用' }}</span>
              <a-tooltip
                title="当上下文大小超过阈值时，自动对早期对话进行摘要，以优化Token使用"
                overlay-class-name="no-flip-tooltip"
                :overlay-style="{ maxWidth: '320px' }"
                placement="topLeft"
              >
                <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
              </a-tooltip>
            </div>
          </a-form-item>
          <a-form-item v-if="agentConfig.enableSummary" label="摘要触发阈值">
            <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
              <a-input-number
                v-model:value="agentConfig.summaryThresholdKb"
                :min="10"
                :max="1000"
                :step="10"
                placeholder="100"
                style="flex: 1"
                :disabled="isVersionPreview"
              />
              <span style="font-size: 13px; color: var(--color-mute); white-space: nowrap;">KB</span>
              <a-tooltip
                title="当上下文大小超过该值时，启用摘要功能以优化上下文使用。单位为 KB，默认值为 100KB"
                overlay-class-name="no-flip-tooltip"
                :overlay-style="{ maxWidth: '320px' }"
                placement="topLeft"
              >
                <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
              </a-tooltip>
            </div>
            <div class="param-hint">上下文超过该大小时自动摘要，默认 100KB</div>
          </a-form-item>
          <a-form-item v-if="agentConfig.enableSummary">
            <template #label>
              <div style="display: flex; align-items: center; gap: 6px;">
                <span>摘要提示词</span>
                <a-tooltip
                  title="触发上下文摘要时使用的提示词，必须保留 {messages} 占位符"
                  overlay-class-name="no-flip-tooltip"
                  :overlay-style="{ maxWidth: '320px' }"
                  placement="topLeft"
                >
                  <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
                </a-tooltip>
              </div>
            </template>
            <a-textarea
              v-model:value="agentConfig.summaryPrompt"
              :rows="4"
              :maxlength="1000"
              show-count
              placeholder="留空使用系统默认摘要提示词"
              :disabled="isVersionPreview"
            />
          </a-form-item>
          <a-form-item v-if="agentConfig.enableSummary">
            <template #label>
              <div style="display: flex; align-items: center; gap: 6px;">
                <span>保留消息数</span>
                <a-tooltip
                  title="摘要触发后，除摘要消息外保留最近 N 条消息不被压缩（摘要后保留消息数）"
                  overlay-class-name="no-flip-tooltip"
                  :overlay-style="{ maxWidth: '320px' }"
                  placement="topLeft"
                >
                  <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
                </a-tooltip>
              </div>
            </template>
            <a-input-number
              v-model:value="agentConfig.summaryKeepMessages"
              :min="1"
              :max="50"
              :step="1"
              placeholder="默认 6"
              style="width: 100%"
              :disabled="isVersionPreview"
            />
          </a-form-item>
          <a-form-item v-if="agentConfig.enableSummary">
            <template #label>
              <div style="display: flex; align-items: center; gap: 6px;">
                <span>工具结果上限</span>
                <a-tooltip
                  title="摘要清洗历史工具结果时，保留的预览 Token 数，超出部分截断（工具结果预览上限）"
                  overlay-class-name="no-flip-tooltip"
                  :overlay-style="{ maxWidth: '320px' }"
                  placement="topLeft"
                >
                  <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
                </a-tooltip>
              </div>
            </template>
            <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
              <a-input-number
                v-model:value="agentConfig.summaryToolResultTokenLimit"
                :min="50"
                :max="5000"
                :step="50"
                placeholder="默认 500"
                style="flex: 1"
                :disabled="isVersionPreview"
              />
              <span style="font-size: 13px; color: var(--color-mute); white-space: nowrap;">Tokens</span>
            </div>
          </a-form-item>
          <a-form-item>
            <template #label>
              <div style="display: flex; align-items: center; gap: 6px;">
                <span>最大执行步数</span>
                <a-tooltip
                  title="单次对话中模型与工具交互的最大轮次。超出后停止工具调用并返回当前结果"
                  overlay-class-name="no-flip-tooltip"
                  :overlay-style="{ maxWidth: '320px' }"
                  placement="topLeft"
                >
                  <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
                </a-tooltip>
              </div>
            </template>
            <a-input-number
              v-model:value="agentConfig.maxExecutionSteps"
              :min="1"
              :max="100"
              :step="1"
              placeholder="默认 10"
              style="width: 100%"
              :disabled="isVersionPreview"
            />
          </a-form-item>
          <a-form-item>
            <template #label>
              <div style="display: flex; align-items: center; gap: 6px;">
                <span>模型重试次数</span>
                <a-tooltip
                  title="模型调用失败时的最大重试次数。重试间隔递增（1s, 2s, 4s）"
                  overlay-class-name="no-flip-tooltip"
                  :overlay-style="{ maxWidth: '320px' }"
                  placement="topLeft"
                >
                  <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
                </a-tooltip>
              </div>
            </template>
            <a-input-number
              v-model:value="agentConfig.modelRetryTimes"
              :min="0"
              :max="10"
              :step="1"
              placeholder="默认 2"
              style="width: 100%"
              :disabled="isVersionPreview"
            />
          </a-form-item>
          <a-form-item>
            <template #label>
              <div style="display: flex; align-items: center; gap: 6px;">
                <span>用户输入敏感词</span>
              </div>
            </template>
            <div style="display: flex; align-items: center; gap: 8px;">
              <a-switch v-model:checked="agentConfig.userSensitiveFilterEnabled" :disabled="isVersionPreview" />
              <span class="tool-option-value">{{ agentConfig.userSensitiveFilterEnabled ? '已启用' : '未启用' }}</span>
              <a-tooltip
                  title="检测用户发送的消息。命中后拒绝发送并提示用户修改，不会调用模型"
                  overlay-class-name="no-flip-tooltip"
                  :overlay-style="{ maxWidth: '320px' }"
                  placement="topLeft"
                >
                  <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
                </a-tooltip>
            </div>
          </a-form-item>
          <template v-if="agentConfig.userSensitiveFilterEnabled">
            <a-form-item label="用户敏感词">
              <div class="inline-field-block">
                <button type="button" class="btn-add-inline btn-add-inline--block" :disabled="isVersionPreview" @click="addUserSensitiveWord">
                  <PlusOutlined /> 添加敏感词
                </button>
                <div class="sensitive-word-list">
                  <div v-for="(word, idx) in userSensitiveWords" :key="'usw-' + idx" class="list-table-row list-table-row--2col">
                    <a-input v-model:value="userSensitiveWords[idx]" placeholder="命中则拦截用户消息" size="small" :disabled="isVersionPreview" />
                    <a-tooltip title="删除">
                      <button type="button" class="btn-icon-sm danger" :disabled="isVersionPreview" @click="removeUserSensitiveWord(idx)">
                        <DeleteOutlined />
                      </button>
                    </a-tooltip>
                  </div>
                </div>
              </div>
            </a-form-item>
          </template>
          <a-form-item>
            <template #label>
              <div style="display: flex; align-items: center; gap: 6px;">
                <span>AI 输出敏感词</span>
              </div>
            </template>
            <div style="display: flex; align-items: center; gap: 8px;">
              <a-switch v-model:checked="agentConfig.sensitiveFilterEnabled" :disabled="isVersionPreview" />
              <span class="tool-option-value">{{ agentConfig.sensitiveFilterEnabled ? '已启用' : '未启用' }}</span>
                <a-tooltip
                  title="检测模型回复。拦截策略会停止输出并展示拦截提示；替换策略将命中词替换为指定文本"
                  overlay-class-name="no-flip-tooltip"
                  :overlay-style="{ maxWidth: '320px' }"
                  placement="topLeft"
                >
                  <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
                </a-tooltip>
            </div>
          </a-form-item>
          <template v-if="agentConfig.sensitiveFilterEnabled">
            <a-form-item label="处理策略">
              <a-select v-model:value="agentConfig.sensitiveFilterStrategy" style="width: 100%" :disabled="isVersionPreview">
                <a-select-option value="replace">替换为指定文本</a-select-option>
                <a-select-option value="block">拦截并提示</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item v-if="agentConfig.sensitiveFilterStrategy !== 'block'" label="替换文本">
              <a-input v-model:value="agentConfig.sensitiveFilterReplaceText" placeholder="默认 ***" :disabled="isVersionPreview" />
            </a-form-item>
            <a-form-item label="AI 敏感词列表">
              <div class="inline-field-block">
                <button type="button" class="btn-add-inline btn-add-inline--block" :disabled="isVersionPreview" @click="addSensitiveWord">
                  <PlusOutlined /> 添加敏感词
                </button>
                <div class="sensitive-word-list">
                  <div v-for="(word, idx) in sensitiveWords" :key="'sw-' + idx" class="list-table-row list-table-row--2col">
                    <a-input v-model:value="sensitiveWords[idx]" placeholder="AI 输出命中则处理" size="small" :disabled="isVersionPreview" />
                    <a-tooltip title="删除">
                      <button type="button" class="btn-icon-sm danger" :disabled="isVersionPreview" @click="removeSensitiveWord(idx)">
                        <DeleteOutlined />
                      </button>
                    </a-tooltip>
                  </div>
                </div>
              </div>
            </a-form-item>
          </template>
                        </a-form>
              </div>
            </a-tab-pane>
          </a-tabs>
        </div>
      </div>

      <!-- 工作流配置 -->
      <div class="panel panel-stretch panel--workflow" v-if="agent.agentType === 'workflow'">
        <div class="panel-header">
          <h3>工作流配置</h3>
        </div>
        <div class="panel-body">
        <div class="workflow-entry-content">

          <!-- 配置说明 -->
          <div class="workflow-guide">
            <div class="guide-title">
              <BookOutlined style="margin-right: 6px;" />
              配置说明
            </div>
            <ul class="guide-list">
              <li>从左侧节点库拖拽节点到画布</li>
              <li>连接节点形成执行流程</li>
              <li>点击节点配置详细参数</li>
              <li>每个LLM节点可独立选择模型</li>
            </ul>
          </div>

          <!-- 节点统计 -->
          <div class="workflow-stats" v-if="workflowStats.total > 0">
            <div class="stats-header">节点统计</div>
            <div class="stats-grid">
              <div class="stat-item">
                <span class="stat-value">{{ workflowStats.total }}</span>
                <span class="stat-label">总节点</span>
              </div>
              <div class="stat-item">
                <span class="stat-value llm">{{ workflowStats.llm }}</span>
                <span class="stat-label">大模型</span>
              </div>
              <div class="stat-item">
                <span class="stat-value condition">{{ workflowStats.condition }}</span>
                <span class="stat-label">条件</span>
              </div>
              <div class="stat-item">
                <span class="stat-value tool">{{ workflowStats.tool }}</span>
                <span class="stat-label">工具</span>
              </div>
              <div class="stat-item">
                <span class="stat-value retrieval">{{ workflowStats.retrieval }}</span>
                <span class="stat-label">检索</span>
              </div>
              <div class="stat-item">
                <span class="stat-value edges">{{ workflowStats.edges }}</span>
                <span class="stat-label">连线</span>
              </div>
            </div>
          </div>

          <!-- 配置状态 -->
          <div class="workflow-status">
            <CheckCircleOutlined v-if="hasWorkflowConfig" style="color: #22c55e" />
            <ExclamationCircleOutlined v-else style="color: #f59e0b" />
            <span class="status-text">{{ hasWorkflowConfig ? '已配置工作流' : '尚未配置工作流' }}</span>
          </div>

          <div class="workflow-entry-hint">
            <InfoCircleOutlined />
            <span>点击右上角「工作流编排」按钮进入工作流画布界面</span>
          </div>
        </div>
        </div>
      </div>
    </div>

    <!-- 绑定扩展（预览时 Tab 可切换查看，仅内容区只读） -->
    <a-alert
      v-if="agent.agentType !== 'workflow' && invalidBindingCount > 0 && !isVersionPreview"
      type="warning"
      show-icon
      class="binding-deleted-alert"
    >
      <template #message>
        <div class="binding-alert-message-row">
          <span>{{ invalidBindingAlertMessage }}</span>
          <a-button
            v-if="deletedBindingCount > 0"
            type="link"
            danger
            size="small"
            class="btn-remove-deleted-bindings"
            @click="removeAllDeletedBindings"
          >
            <DeleteOutlined />
            移除已删除绑定
          </a-button>
        </div>
      </template>
      <template #description>
        <div class="binding-deleted-detail-row">
          <div
            v-for="(line, li) in deletedBindingDetailLines"
            :key="li"
            class="binding-deleted-detail-line"
          >{{ line }}</div>
          <p class="binding-deleted-detail-hint">已删除的绑定运行时无法调用，已禁用的绑定需先启用才能使用。暂存或发布前将提示确认。</p>
        </div>
      </template>
    </a-alert>
    <a-tabs
      v-if="agent.agentType !== 'workflow'"
      v-model:activeKey="bindingTab"
      @change="onBindingTabChange"
      class="binding-tabs"
      :class="{ 'binding-tabs--preview': isVersionPreview }"
    >
      <!-- 工具绑定 -->
      <a-tab-pane key="tools" tab="工具">
        <div class="binding-tab-pane" :class="{ 'preview-lock-zone': isVersionPreview }">
        <div class="tool-options-bar">
          <div class="tool-option-item">
            <span class="tool-option-label">工具调用模式</span>
            <a-switch v-model:checked="agentConfig.asyncToolCalls" size="default" :disabled="isVersionPreview" />
            <span class="tool-option-value">{{ agentConfig.asyncToolCalls ? '异步（并行）' : '串行（逐个）' }}</span>
            <a-tooltip
              title="串行模式：每次只调用一个工具，等待结果后再决定是否继续调用；异步模式：AI可同时调用多个工具，提升效率但可能消耗更多Token"
              overlay-class-name="no-flip-tooltip"
              :overlay-style="{ maxWidth: '320px' }"
              placement="topLeft"
            >
              <QuestionCircleOutlined class="tool-option-help" />
            </a-tooltip>
          </div>
        </div>
        <div class="knowledge-bind">
          <div class="selected-knowledge">
            <div class="selected-header">
              <span class="selected-label">已绑定 {{ selectedTools.length }}/{{ BIND_LIMITS.tool }} 个工具</span>
              <button v-if="!isVersionPreview && selectedTools.length > 0" class="btn-clear" @click="clearSelectedTools">
                <DeleteOutlined /> 清空
              </button>
            </div>
            <div class="selected-knowledge-tags">
              <div v-if="selectedTools.length === 0" class="empty-tip">
                暂未绑定工具，请从下方列表选择
              </div>
              <div
                v-for="t in selectedTools"
                :key="t.id"
                class="knowledge-tag tool-tag"
                :class="{ 'binding-tag--deleted': t._deleted, 'binding-tag--disabled': t._disabled && !t._deleted }"
              >
                <span class="tag-avatar" style="background: linear-gradient(135deg, #10b981, #059669)">{{ (t.displayName || t.name || '?')[0].toUpperCase() }}</span>
                <span>{{ t.displayName || t.name }}</span>
                <span v-if="t._deleted" class="binding-deleted-tag">已删除</span>
                <span v-else-if="t._disabled" class="binding-disabled-tag">已禁用</span>
                <span v-else-if="isKnowledgeTool(t)" class="tool-knowledge-badge">知识库</span>
                <span v-else class="tool-type-badge">{{ toolTypeLabels[t.toolType?.code || t.toolType] || t.toolType }}</span>
                <button v-if="!isVersionPreview" class="tag-remove" @click="removeTool(t.id)">
                  <CloseOutlined />
                </button>
              </div>
            </div>
          </div>
          <div class="knowledge-list">
            <div class="list-header">
              <span>可选工具（{{ selectedTools.length }}/{{ BIND_LIMITS.tool }}）</span>
              <div class="list-header-actions">
                <SystemToolDrawer v-if="!isVersionPreview" placement="bottomRight" />
                <a-input
                  v-model:value="toolSearchText"
                  placeholder="搜索工具..."
                  size="small"
                  style="width: 200px"
                  :disabled="isVersionPreview"
                >
                  <template #prefix><SearchOutlined /></template>
                </a-input>
              </div>
            </div>
            <div class="type-filter-bar">
              <button
                v-for="opt in toolTypeOptions"
                :key="opt.value"
                class="type-filter-btn"
                :class="{ active: toolTypeFilter === opt.value }"
                :disabled="isVersionPreview"
                @click="toolTypeFilter = opt.value; loadToolList(opt.value || undefined)"
              >{{ opt.label }}</button>
            </div>
            <div class="list-body">
              <div
                v-for="t in filteredToolList"
                :key="t.name"
                class="knowledge-item"
                :class="{ selected: selectedToolIds.has(toBindingId(t.id)), 'is-preview-locked': isVersionPreview }"
                @click="!isVersionPreview && toggleTool(t)"
              >
                <div class="item-icon tool-icon-bg">
                  <span v-if="isKnowledgeTool(t)" class="knowledge-badge">知识库</span>
                  <span v-else-if="(t.toolType?.code || t.toolType) === 'builtin'" class="builtin-badge">内置</span>
                  {{ (t.displayName || t.name || '?')[0].toUpperCase() }}
                </div>
                <div class="item-info">
                  <div class="item-name">{{ t.displayName || t.name }}</div>
                  <a-tooltip v-if="t.description" :title="t.description" placement="topLeft" :overlay-style="{ maxWidth: '400px' }">
                    <div class="item-desc">{{ truncateText(t.description, 50) }}</div>
                  </a-tooltip>
                  <div v-else class="item-desc">暂无描述</div>
                </div>
                <div class="item-check" v-if="selectedToolIds.has(toBindingId(t.id))">
                  <CheckOutlined />
                </div>
              </div>
              <div v-if="filteredToolList.length === 0" class="empty-tip">
                暂无可用工具
              </div>
            </div>
          </div>
        </div>
        </div>
      </a-tab-pane>

      <!-- 知识库绑定 -->
      <a-tab-pane key="knowledge" tab="知识库">
        <div class="binding-tab-pane" :class="{ 'preview-lock-zone': isVersionPreview }">
        <div class="knowledge-bind">
          <div class="selected-knowledge">
            <div class="selected-header">
              <span class="selected-label">已绑定 {{ selectedKnowledge.length }}/{{ BIND_LIMITS.knowledge }} 个知识库</span>
              <span v-if="selectedKnowledge.length > 0" class="kb-tool-hint">
                <InfoCircleOutlined />
                <span>绑定知识库后将自动导入知识库工具，</span>
                <a @click.prevent="$refs.kbToolDrawerRef?.open()">点击查看知识库工具</a>
              </span>
              <button v-if="!isVersionPreview && selectedKnowledge.length > 0" class="btn-clear" @click="clearSelectedKnowledge">
                <DeleteOutlined /> 清空
              </button>
            </div>
            <div class="selected-knowledge-tags">
              <div v-if="selectedKnowledge.length === 0" class="empty-tip">
                暂未绑定知识库，请从下方列表选择
              </div>
              <div
                v-for="k in selectedKnowledge"
                :key="k.id"
                class="knowledge-tag"
                :class="{ 'binding-tag--deleted': k._deleted }"
              >
                <span class="tag-avatar" style="background: linear-gradient(135deg, #6366f1, #4f46e5)">{{ (k.name || 'K')[0].toUpperCase() }}</span>
                <span>{{ k.name }}</span>
                <span v-if="k._deleted" class="binding-deleted-tag">已删除</span>
                <button v-if="!isVersionPreview" class="tag-remove" @click="removeKnowledge(k.id)">
                  <CloseOutlined />
                </button>
              </div>
            </div>
          </div>
          <div class="knowledge-list">
            <div class="list-header">
              <span>可用知识库（{{ selectedKnowledge.length }}/{{ BIND_LIMITS.knowledge }}）</span>
              <a-input
                v-model:value="searchText"
                placeholder="搜索知识库..."
                size="small"
                style="width: 200px"
                :disabled="isVersionPreview"
              >
                <template #prefix><SearchOutlined /></template>
              </a-input>
            </div>
            <div class="list-body">
              <div
                v-for="k in filteredKnowledgeList"
                :key="k.id"
                class="knowledge-item"
                :class="{ selected: selectedKnowledgeIds.has(toBindingId(k.id)), 'is-preview-locked': isVersionPreview }"
                @click="!isVersionPreview && toggleKnowledge(k)"
              >
                <div class="item-icon knowledge-icon">
                  {{ (k.name || 'K')[0].toUpperCase() }}
                </div>
                <div class="item-info">
                  <div class="item-name">
                    {{ k.name }}
                    <a-tooltip v-if="k.type" :title="k.type === 'milvus' ? 'Milvus' : 'PostgreSQL'">
                      <CloudServerOutlined v-if="k.type === 'milvus'" class="kb-type-icon milvus" />
                      <DatabaseOutlined v-else class="kb-type-icon pg" />
                    </a-tooltip>
                  </div>
                  <a-tooltip v-if="k.description" :title="k.description" placement="topLeft" :overlay-style="{ maxWidth: '400px' }">
                    <div class="item-desc">{{ truncateText(k.description, 50) }}</div>
                  </a-tooltip>
                  <div v-else class="item-desc">暂无描述</div>
                </div>
                <div class="item-check" v-if="selectedKnowledgeIds.has(toBindingId(k.id))">
                  <CheckOutlined />
                </div>
              </div>
              <div v-if="filteredKnowledgeList.length === 0" class="empty-tip">
                暂无可用知识库
              </div>
            </div>
          </div>
        </div>
        <!-- 知识库工具抽屉（通过 hint 链接触发，不渲染默认按钮） -->
        <SystemToolDrawer ref="kbToolDrawerRef" placement="bottomRight">
          <template #trigger></template>
        </SystemToolDrawer>
        </div>
      </a-tab-pane>

      <!-- MCP -->
      <a-tab-pane key="mcp" tab="MCP">
        <div class="binding-tab-pane" :class="{ 'preview-lock-zone': isVersionPreview }">
        <div class="knowledge-bind">
          <div class="selected-knowledge">
            <div class="selected-header">
              <span class="selected-label">已绑定 {{ selectedMcpServers.length }}/{{ BIND_LIMITS.mcp }} 个 MCP Server</span>
              <button v-if="!isVersionPreview && selectedMcpServers.length > 0" class="btn-clear" @click="clearSelectedMcpServers">
                <DeleteOutlined /> 清空
              </button>
            </div>
            <div class="selected-knowledge-tags">
              <div v-if="selectedMcpServers.length === 0" class="empty-tip">
                暂未绑定 MCP Server，请从下方列表选择
              </div>
              <div
                v-for="s in selectedMcpServers"
                :key="s.id"
                class="knowledge-tag mcp-tag"
                :class="{ 'binding-tag--deleted': s._deleted, 'binding-tag--disabled': s._disabled && !s._deleted }"
              >
                <span class="tag-avatar" style="background: linear-gradient(135deg, #8b5cf6, #7c3aed)">{{ (s.name || 'M')[0].toUpperCase() }}</span>
                <span>{{ s.name }}</span>
                <span v-if="s._deleted" class="binding-deleted-tag">已删除</span>
                <span v-else-if="s._disabled" class="binding-disabled-tag">已禁用</span>
                <button v-if="!isVersionPreview" class="tag-remove" @click="removeMcpServer(s.id)">
                  <CloseOutlined />
                </button>
              </div>
            </div>
          </div>
          <div class="knowledge-list">
            <div class="list-header">
              <span>可用 MCP Server（{{ selectedMcpServers.length }}/{{ BIND_LIMITS.mcp }}）</span>
              <a-input
                v-model:value="mcpSearchText"
                placeholder="搜索 MCP Server..."
                size="small"
                style="width: 200px"
                :disabled="isVersionPreview"
              >
                <template #prefix><SearchOutlined /></template>
              </a-input>
            </div>
            <div class="list-body">
              <div
                v-for="s in filteredMcpServerList"
                :key="s.id"
                class="knowledge-item"
                :class="{ selected: selectedMcpServerIds.has(toBindingId(s.id)), 'is-preview-locked': isVersionPreview }"
                @click="!isVersionPreview && toggleMcpServer(s)"
              >
                <div class="item-icon mcp-icon-bg">
                  {{ (s.name || 'M')[0].toUpperCase() }}
                </div>
                <div class="item-info">
                  <div class="item-name">{{ s.name }}</div>
                  <a-tooltip v-if="s.description" :title="s.description" placement="topLeft" :overlay-style="{ maxWidth: '400px' }">
                    <div class="item-desc">{{ truncateText(s.description, 50) }}</div>
                  </a-tooltip>
                  <div v-else class="item-desc">暂无描述</div>
                </div>
                <div class="item-check" v-if="selectedMcpServerIds.has(toBindingId(s.id))">
                  <CheckOutlined />
                </div>
              </div>
              <div v-if="filteredMcpServerList.length === 0" class="empty-tip">
                暂无可用 MCP Server
              </div>
            </div>
          </div>
        </div>
        </div>
      </a-tab-pane>


      <!-- SubAgent 绑定 -->
      <a-tab-pane key="subagents" tab="SubAgents">
        <div class="binding-tab-pane" :class="{ 'preview-lock-zone': isVersionPreview }">
        <div class="subagent-bind">
          <div class="selected-subagents">
            <div class="selected-header">
              <span class="selected-label">已绑定 {{ selectedSubAgents.length }}/{{ BIND_LIMITS.subAgent }} 个 SubAgent</span>
              <button v-if="!isVersionPreview && selectedSubAgents.length > 0" class="btn-clear" @click="clearSelectedSubAgents">
                <DeleteOutlined /> 清空
              </button>
            </div>
            <div class="selected-subagents-tags">
              <div v-if="selectedSubAgents.length === 0" class="empty-tip">
                暂未绑定 SubAgent，从下方列表选择
              </div>
              <div
                v-for="s in selectedSubAgents"
                :key="s.id"
                class="knowledge-tag subagent-tag"
                :class="{ 'binding-tag--deleted': s._deleted, 'binding-tag--disabled': s._disabled && !s._deleted }"
              >
                <span class="tag-avatar" style="background: linear-gradient(135deg, #f59e0b, #d97706)">{{ (s.displayName || s.name || 'S')[0].toUpperCase() }}</span>
                <span>{{ s.displayName || s.name }}</span>
                <span v-if="s._deleted" class="binding-deleted-tag">已删除</span>
                <span v-else-if="s._disabled" class="binding-disabled-tag">已禁用</span>
                <button v-if="!isVersionPreview" class="tag-remove" @click="removeSubAgent(s.id)">
                  <CloseOutlined />
                </button>
              </div>
            </div>
          </div>
          <div class="subagent-list">
            <div class="list-header">
              <span>可用的 SubAgent（{{ selectedSubAgents.length }}/{{ BIND_LIMITS.subAgent }}）</span>
              <a-input
                v-model:value="subAgentSearchText"
                placeholder="搜索 SubAgent..."
                size="small"
                style="width: 200px"
                :disabled="isVersionPreview"
              >
                <template #prefix><SearchOutlined /></template>
              </a-input>
            </div>
            <div class="list-body">
              <div
                v-for="s in filteredSubAgentList"
                :key="s.id"
                class="subagent-item"
                :class="{ selected: selectedSubAgentIds.has(toBindingId(s.id)), 'is-preview-locked': isVersionPreview }"
                @click="!isVersionPreview && toggleSubAgent(s)"
              >
                <div class="item-icon subagent-icon">
                  <span v-if="s.isBuiltin === 1" class="builtin-badge">内置</span>
                  {{ (s.displayName || s.name || 'S')[0].toUpperCase() }}
                </div>
                <div class="item-info">
                  <div class="item-name">{{ s.displayName }}</div>
                  <a-tooltip v-if="s.description" :title="s.description" placement="topLeft" :overlay-style="{ maxWidth: '400px' }">
                    <div class="item-desc">{{ truncateText(s.description, 50) }}</div>
                  </a-tooltip>
                  <div v-else class="item-desc">暂无描述</div>
                  <div class="item-tools" v-if="s.tools && JSON.parse(s.tools).length > 0">
                    工具: {{ JSON.parse(s.tools).join(', ') }}
                  </div>
                </div>
                <div class="item-check" v-if="selectedSubAgentIds.has(toBindingId(s.id))">
                  <CheckOutlined />
                </div>
              </div>
              <div v-if="filteredSubAgentList.length === 0" class="empty-tip">
                暂无可用 SubAgent
              </div>
            </div>
          </div>
        </div>
        </div>
      </a-tab-pane>
      <!-- Skill 绑定 -->
      <a-tab-pane key="skill" tab="Skill">
        <div class="binding-tab-pane" :class="{ 'preview-lock-zone': isVersionPreview }">
        <div class="subagent-bind">
          <div class="selected-subagents">
            <div class="selected-header">
              <span class="selected-label">已启用 {{ selectedSkills.length }}/{{ BIND_LIMITS.skill }} 个 Skill</span>
              <button v-if="!isVersionPreview && selectedSkills.length > 0" class="btn-clear" @click="clearSelectedSkills">
                <DeleteOutlined /> 清空
              </button>
            </div>
            <div class="selected-subagents-tags">
              <div v-if="selectedSkills.length === 0" class="empty-tip">
                暂未启用 Skill，从下方列表选择
              </div>
              <div
                v-for="s in selectedSkills"
                :key="s.id"
                class="knowledge-tag skill-tag"
                :class="{ 'binding-tag--deleted': s._deleted, 'binding-tag--disabled': s._disabled && !s._deleted }"
              >
                <span class="tag-avatar" style="background: linear-gradient(135deg, #ec4899, #db2777)">{{ (s.displayName || s.name || 'S')[0].toUpperCase() }}</span>
                <span class="tag-name-wrap">
                  <span>{{ s.displayName || s.name }}</span>
                  <span v-if="s.isBuiltin === 1" class="binding-inline-badge">内置</span>
                </span>
                <span v-if="s._deleted" class="binding-deleted-tag">已删除</span>
                <span v-else-if="s._disabled" class="binding-disabled-tag">已禁用</span>
                <button v-if="!isVersionPreview" class="tag-remove" @click="removeSkill(s.id)">
                  <CloseOutlined />
                </button>
              </div>
            </div>
          </div>
          <div class="subagent-list">
            <div class="list-header">
              <span>可用的 Skill（{{ selectedSkills.length }}/{{ BIND_LIMITS.skill }}）</span>
              <a-input
                v-model:value="skillSearchText"
                placeholder="搜索 Skill..."
                size="small"
                style="width: 200px"
                :disabled="isVersionPreview"
              >
                <template #prefix><SearchOutlined /></template>
              </a-input>
            </div>
            <div class="list-body">
              <div
                v-for="s in filteredSkillList"
                :key="s.id"
                class="subagent-item"
                :class="{ selected: selectedSkillIds.has(toBindingId(s.id)), 'is-preview-locked': isVersionPreview }"
                @click="!isVersionPreview && toggleSkill(s)"
              >
                <div class="item-icon skill-icon">
                  <span v-if="s.isBuiltin === 1" class="builtin-badge">内置</span>
                  {{ (s.displayName || s.name || 'S')[0].toUpperCase() }}
                </div>
                <div class="item-info">
                  <div class="item-name">{{ s.displayName || s.name }}</div>
                  <a-tooltip v-if="s.description" :title="s.description" placement="topLeft" :overlay-style="{ maxWidth: '400px' }">
                    <div class="item-desc">{{ truncateText(s.description, 50) }}</div>
                  </a-tooltip>
                  <div v-else class="item-desc">暂无描述</div>
                  <div class="item-tools" v-if="s.slug">slug: {{ s.slug }}</div>
                </div>
                <div class="item-check" v-if="selectedSkillIds.has(toBindingId(s.id))">
                  <CheckOutlined />
                </div>
              </div>
              <div v-if="filteredSkillList.length === 0" class="empty-tip">
                暂无可用 Skill
              </div>
            </div>
          </div>
        </div>
        </div>
      </a-tab-pane>

    </a-tabs>
    </div>
    <a-modal
      v-model:open="publishModalVisible"
      title="发布 Agent"
      ok-text="确认发布"
      cancel-text="取消"
      class="publish-modal"
      :confirm-loading="publishing"
      @ok="confirmPublishAgent"
    >
      <div class="publish-modal-content">
        <p class="publish-modal-tip">选填发布说明（最多 50 字），可在版本历史中查看。</p>
        <a-textarea
          v-model:value="publishDescription"
          class="publish-modal-textarea"
          :maxlength="50"
          show-count
          :rows="3"
          placeholder="例如：优化系统提示词、调整模型参数"
        />
      </div>
    </a-modal>

    <a-drawer
      v-model:open="versionDrawerVisible"
      placement="right"
      :width="480"
      class="agent-version-drawer"
    >
      <template #title>
        <span>版本历史</span>
        <QuestionCircleOutlined class="version-help-icon" @click.stop="versionHelpVisible = true" />
      </template>
      <div
        class="version-drawer-item draft"
        :class="{ active: selectedVersion === 'draft' }"
        @click="selectAgentVersion('draft')"
      >
        <div class="version-drawer-title">当前编辑</div>
        <div class="version-drawer-desc">继续编辑未发布的修改</div>
      </div>
      <a-divider style="margin: 12px 0" />
      <a-spin :spinning="versionLoading">
        <div
          v-for="(item, idx) in versionList"
          :key="item.version"
          class="version-drawer-item"
          :class="{ active: selectedVersion === item.version }"
          @click="selectAgentVersion(item.version)"
        >
          <div class="version-drawer-header">
            <span class="version-drawer-title">{{ idx === 0 ? '线上版本' : `v${item.version}` }}</span>
            <a-tag v-if="idx === 0" color="green" size="small">最新</a-tag>
            <span class="version-drawer-spacer"></span>
            <a-tooltip v-if="idx !== 0" title="删除该版本" :overlayStyle="{ maxWidth: '200px' }">
              <button
                type="button"
                class="btn-icon-sm danger version-delete-btn"
                @click.stop="confirmDeleteVersion(item)"
              >
                <DeleteOutlined />
              </button>
            </a-tooltip>
          </div>
          <div v-if="item.description" class="version-drawer-note">{{ item.description }}</div>
          <div class="version-drawer-desc">{{ formatVersionDesc(item) }}</div>
        </div>
        <a-empty v-if="!versionLoading && versionList.length === 0" description="暂无发布版本" />
      </a-spin>

      <div v-if="versionPreview" class="version-preview-detail">
        <h4 class="preview-detail-title">版本配置快照</h4>

        <!-- 1. 提示词 -->
        <div class="preview-section">
          <div class="preview-section-title">提示词</div>
          <div class="preview-field">
            <label>系统提示词</label>
            <div class="preview-value pre">{{ versionPreview.basicInfo?.systemPrompt || '（空）' }}</div>
          </div>
        </div>

        <!-- 2. 模型参数 -->
        <div class="preview-section">
          <div class="preview-section-title">模型参数</div>
          <div class="preview-field">
            <label>提供商 / 模型</label>
            <div class="preview-value">{{ previewModelLabel }}</div>
          </div>
          <div class="preview-field-grid">
            <div class="preview-field" v-if="versionPreview.modelParams?.temperature !== undefined">
              <label>Temperature</label>
              <div class="preview-value">{{ versionPreview.modelParams.temperature }}</div>
            </div>
            <div class="preview-field" v-if="versionPreview.modelParams?.topP !== undefined">
              <label>Top P</label>
              <div class="preview-value">{{ versionPreview.modelParams.topP }}</div>
            </div>
            <div class="preview-field" v-if="versionPreview.modelParams?.maxTokens !== undefined">
              <label>Max Tokens</label>
              <div class="preview-value">{{ versionPreview.modelParams.maxTokens }}</div>
            </div>
            <div class="preview-field" v-if="versionPreview.modelParams?.presencePenalty !== undefined">
              <label>Presence Penalty</label>
              <div class="preview-value">{{ versionPreview.modelParams.presencePenalty }}</div>
            </div>
            <div class="preview-field" v-if="versionPreview.modelParams?.frequencyPenalty !== undefined">
              <label>Frequency Penalty</label>
              <div class="preview-value">{{ versionPreview.modelParams.frequencyPenalty }}</div>
            </div>
            <div class="preview-field" v-if="versionPreview.modelParams?.repetitionPenalty !== undefined">
              <label>Repetition Penalty</label>
              <div class="preview-value">{{ versionPreview.modelParams.repetitionPenalty }}</div>
            </div>
          </div>
          <div v-if="capabilityPreviewItems.length" class="preview-field-grid preview-field-grid--capabilities">
            <div
              v-for="item in capabilityPreviewItems"
              :key="item.key"
              class="preview-field"
            >
              <label>{{ item.label }}</label>
              <div class="preview-value">
                {{ item.display }}
                <span v-if="item.fromDefault" class="preview-default-tag">默认</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 3. 对话配置 -->
        <div class="preview-section">
          <div class="preview-section-title">对话配置</div>
          <div class="preview-field-grid">
            <div
              v-for="row in chatConfigPreviewRows"
              :key="row.label"
              class="preview-field"
            >
              <label>{{ row.label }}</label>
              <div class="preview-value">
                {{ row.text }}
                <span v-if="row.fromDefault" class="preview-default-tag">默认</span>
              </div>
            </div>
            <div class="preview-field" v-if="userSensitiveWordsPreview.length > 0">
              <label>用户敏感词列表</label>
              <div class="preview-value">
                <div v-for="(w, i) in userSensitiveWordsPreview" :key="i" class="preview-tag danger">{{ w }}</div>
              </div>
            </div>
            <div class="preview-field" v-if="sensitiveWordsPreview.length > 0">
              <label>AI敏感词列表</label>
              <div class="preview-value">
                <div v-for="(w, i) in sensitiveWordsPreview" :key="i" class="preview-tag danger">{{ w }}</div>
              </div>
            </div>
          </div>
          <!-- 变量配置 -->
          <div class="preview-field" v-if="promptVariablesPreview.length > 0">
            <label>变量配置</label>
            <div class="preview-value">
              <table class="preview-table">
                <thead>
                  <tr>
                    <th>变量名</th>
                    <th>显示名称</th>
                    <th>默认值</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(v, i) in promptVariablesPreview" :key="i">
                    <td><code>{{ v.key }}</code></td>
                    <td>{{ v.label || '-' }}</td>
                    <td>{{ v.defaultValue || '-' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- 4. 绑定关系 -->
        <div class="preview-section">
          <div class="preview-section-title">绑定关系</div>
          <div class="preview-field">
            <label>知识库</label>
            <div class="preview-value">
              <div v-if="previewKnowledgeList.length === 0" class="preview-empty">未绑定</div>
              <div v-else>
                <div
                  v-for="k in previewKnowledgeList"
                  :key="k.id"
                  class="preview-tag"
                  :class="{ 'preview-tag--deleted': k._deleted }"
                >
                  {{ k.name }}
                  <span v-if="k._deleted" class="binding-deleted-tag">已删除</span>
                </div>
              </div>
            </div>
          </div>
          <div class="preview-field">
            <label>工具</label>
            <div class="preview-value">
              <div v-if="previewToolList.length === 0" class="preview-empty">未绑定</div>
              <div v-else>
                <div
                  v-for="t in previewToolList"
                  :key="t.id"
                  class="preview-tag"
                  :class="{ 'preview-tag--deleted': t._deleted, 'preview-tag--disabled': t._disabled && !t._deleted }"
                >
                  {{ t.displayName || t.name }}
                  <span v-if="t._deleted" class="binding-deleted-tag">已删除</span>
                  <span v-else-if="t._disabled" class="binding-disabled-tag">已禁用</span>
                  <span v-else-if="isKnowledgeTool(t)" class="tool-knowledge-badge">知识库</span>
                  <span v-else class="preview-tag-type">{{ toolTypeLabels[t.toolType?.code || t.toolType] || t.toolType }}</span>
                </div>
              </div>
            </div>
          </div>
          <div class="preview-field">
            <label>MCP Server</label>
            <div class="preview-value">
              <div v-if="previewMcpList.length === 0" class="preview-empty">未绑定</div>
              <div v-else>
                <div
                  v-for="m in previewMcpList"
                  :key="m.id"
                  class="preview-tag mcp"
                  :class="{ 'preview-tag--deleted': m._deleted, 'preview-tag--disabled': m._disabled && !m._deleted }"
                >
                  {{ m.name }}
                  <span v-if="m._deleted" class="binding-deleted-tag">已删除</span>
                  <span v-else-if="m._disabled" class="binding-disabled-tag">已禁用</span>
                </div>
              </div>
            </div>
          </div>
          <div class="preview-field">
            <label>SubAgent</label>
            <div class="preview-value">
              <div v-if="previewSubAgentList.length === 0" class="preview-empty">未绑定</div>
              <div v-else>
                <div
                  v-for="s in previewSubAgentList"
                  :key="s.id"
                  class="preview-tag subagent"
                  :class="{ 'preview-tag--deleted': s._deleted, 'preview-tag--disabled': s._disabled && !s._deleted }"
                >
                  {{ s.displayName || s.name }}
                  <span v-if="s._deleted" class="binding-deleted-tag">已删除</span>
                  <span v-else-if="s._disabled" class="binding-disabled-tag">已禁用</span>
                </div>
              </div>
            </div>
          </div>
          <div class="preview-field">
            <label>Skill</label>
            <div class="preview-value">
              <div v-if="previewSkillList.length === 0" class="preview-empty">未启用</div>
              <div v-else>
                <div
                  v-for="s in previewSkillList"
                  :key="s.id"
                  class="preview-tag subagent"
                  :class="{ 'preview-tag--deleted': s._deleted, 'preview-tag--disabled': s._disabled && !s._deleted }"
                >
                  {{ s.displayName || s.name }}
                  <span v-if="s._deleted" class="binding-deleted-tag">已删除</span>
                  <span v-else-if="s._disabled" class="binding-disabled-tag">已禁用</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <a-button v-if="selectedVersion !== 'draft'" type="primary" block @click="confirmRestoreVersion">
          恢复到此版本
        </a-button>
      </template>
    </a-drawer>

    <!-- 版本控制说明弹窗 -->
    <a-modal
      v-model:open="versionHelpVisible"
      title="版本控制说明"
      :footer="null"
      width="560px"
    >
      <div class="version-help-content">
        <p>版本快照记录的是<strong>对话配置与编排数据</strong>，以下字段不在版本控制范围内，修改后不会随版本回滚：</p>
        <div class="version-help-table">
          <div class="version-help-row header">
            <span>字段</span><span>说明</span>
          </div>
          <div class="version-help-row"><span>Agent 名称</span><span>智能体的显示名称</span></div>
          <div class="version-help-row"><span>Agent 描述</span><span>智能体的简介描述</span></div>
          <div class="version-help-row"><span>头像</span><span>智能体头像图片</span></div>
          <div class="version-help-row"><span>欢迎语</span><span>对话页欢迎语</span></div>
          <div class="version-help-row"><span>推荐问题</span><span>对话页推荐问题</span></div>
          <div class="version-help-row"><span>图标</span><span>智能体图标（emoji）</span></div>
          <div class="version-help-row"><span>是否默认</span><span>默认智能体标记</span></div>
        </div>
        <p class="version-help-note">提示：系统提示词、变量配置、模型配置、对话配置、工具/知识库/SubAgent 绑定等均受版本控制。</p>
      </div>
    </a-modal>

    <!-- 保存/发布同步遮罩 -->
    <div v-if="saving || publishing" class="sync-overlay">
      <div class="sync-overlay-content">
        <a-spin size="large" />
        <p class="sync-overlay-text">{{ publishing ? 'Agent 发布中...' : 'Agent 修改正在同步中...' }}</p>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, h } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ArrowLeftOutlined, SaveOutlined, CloseOutlined, SearchOutlined, CheckOutlined, MessageOutlined, PlusOutlined, ThunderboltOutlined, UploadOutlined, LoadingOutlined, UndoOutlined, ToolOutlined, QuestionCircleOutlined, ApiOutlined, DeleteOutlined, BookOutlined, RobotOutlined, SettingOutlined, CheckCircleOutlined, ExclamationCircleOutlined, HistoryOutlined, InfoCircleOutlined, IdcardOutlined, RightOutlined, DownOutlined, DatabaseOutlined, CloudServerOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getAgentDetail, updateAgent, updateAgentKnowledge, updateAgentTools, getAgentToolIds, getAgentToolDetails, generateAgentPrompt, generateAgentQuestions, uploadAgentAvatar, updateAgentMcpServers, updateAgentSubAgents, updateAgentSkills, publishAgent, listAgentVersions, getAgentVersionDetail, restoreAgentVersion, deleteAgentVersion } from '../api/agent'
import { getWorkflowConfig } from '../api/workflow'
import { getTools } from '../api/tool'
import { getToolTypes } from '../api/enum'
import { getModelProviders, getProviderConfigFields, getProviderModelCapabilities } from '../api/modelProvider'
import ModelSelect from '../components/ModelSelect.vue'
import { getKnowledgeList } from '../api/knowledge'
import { getMcpServers } from '../api/mcp'
import { getSubAgents } from '../api/subagent'
import { getSkills } from '../api/skill'
import { safeJsonParse } from '../utils/request'
import { truncateText } from '../utils/format'
import {
  toBindingId,
  toBindingIdSet,
  stripBindingKeysFromConfig,
  resolveBindingItems,
  countDeletedBindingItems,
  countDisabledBindingItems,
  markBindingItemDeletedFlag,
  formatDeletedBindingDetailLines,
  removeDeletedIdsFromSet,
} from '../utils/bindingId'
import SystemToolDrawer from '../components/SystemToolDrawer.vue'
import { useBinding } from '../composables/useBinding'
const route = useRoute()
const router = useRouter()
const agentId = route.params.id

const pageLoading = ref(false)
const avatarInputRef = ref(null)

const BIND_LIMITS = { knowledge: 10, mcp: 5, tool: 10, subAgent: 5, skill: 10 }
/** 右侧配置卡片：模型参数 / 对话配置 */
const configTab = ref('prompt')

const promptVariables = ref([])
const validPromptVariables = computed(() =>
  promptVariables.value.filter(v => v.key && /^\S+$/.test(v.key.trim()))
)

const promptPreviewVisible = ref(false)
const promptPreviewHtml = computed(() => {
  const template = agent.systemPrompt || ''
  if (!template) return ''
  const varMap = {}
  validPromptVariables.value.forEach(v => {
    varMap[v.key.trim()] = v.defaultValue || ''
  })
  return template.replace(/\{\{([^{}]+?)\}\}/g, (_, key) => {
    if (key in varMap) {
      const val = varMap[key]
      return val ? `<span class="preview-replaced">${escapeHtml(val)}</span>` : `<span class="preview-empty">[${key}]</span>`
    }
    return `<span class="preview-unset">[${key}]</span>`
  })
})
function escapeHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

const isVersionPreview = computed(() => selectedVersion.value !== 'draft' && selectedVersion.value != null)

/** 版本预览下禁止操作的交互元素（Tab 切换除外） */
/** 预览态下仍允许交互的区域（Tab 切换、返回编辑链接） */
const PREVIEW_ALLOW_SELECTOR = '.ant-tabs-nav, .ant-tabs-tab, .version-preview-banner-top, .version-preview-banner-top .link-btn'

function blockPreviewInteraction(e) {
  if (!isVersionPreview.value) return
  if (e.target.closest(PREVIEW_ALLOW_SELECTOR)) return
  e.preventDefault()
  e.stopImmediatePropagation()
}

function blockPreviewKeydown(e) {
  if (!isVersionPreview.value) return
  if (e.target.closest(PREVIEW_ALLOW_SELECTOR)) return
  e.preventDefault()
  e.stopImmediatePropagation()
}

function blockPreviewInput(e) {
  if (!isVersionPreview.value) return
  if (e.target.closest(PREVIEW_ALLOW_SELECTOR)) return
  e.preventDefault()
  e.stopImmediatePropagation()
}

const previewModelLabel = computed(() => {
  const modelParams = versionPreview.value?.modelParams
  if (!modelParams) return '—'
  const modelId = modelParams.modelId || '—'
  const providerId = modelParams.providerId
  const provider = providerList.value.find(p => String(p.id) === String(providerId))
  const name = provider?.name || providerId || '—'
  return `${name} / ${modelId}`
})

// 版本预览 - 用户敏感词
const userSensitiveWordsPreview = computed(() => {
  const words = versionPreview.value?.chatConfig?.userSensitiveWords
  if (!words) return []
  if (Array.isArray(words)) return words.filter(w => w)
  try {
    const parsed = JSON.parse(words)
    return Array.isArray(parsed) ? parsed.filter(w => w) : []
  } catch {
    return []
  }
})

// 版本预览 - AI敏感词
const sensitiveWordsPreview = computed(() => {
  const words = versionPreview.value?.chatConfig?.sensitiveWords
  if (!words) return []
  if (Array.isArray(words)) return words.filter(w => w)
  try {
    const parsed = JSON.parse(words)
    return Array.isArray(parsed) ? parsed.filter(w => w) : []
  } catch {
    return []
  }
})

// 版本预览 - 变量配置
const promptVariablesPreview = computed(() => {
  const vars = versionPreview.value?.chatConfig?.promptVariables
  if (!vars) return []
  if (Array.isArray(vars)) return vars
  try {
    const parsed = JSON.parse(vars)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
})

// 版本预览 - 绑定列表（优先使用后端按 ID 回查的实体，否则回退本地列表匹配）
const previewKnowledgeList = computed(() => {
  if (versionPreview.value?.knowledges?.length) {
    return versionPreview.value.knowledges.map(markBindingItemDeletedFlag)
  }
  return resolveBindingItems(
    toBindingIdSet(versionPreview.value?.knowledgeIds),
    knowledgeList.value,
    { entityLabel: '知识库' }
  )
})

const previewToolList = computed(() => {
  if (versionPreview.value?.tools?.length) {
    return versionPreview.value.tools.map(markBindingItemDeletedFlag)
  }
  return resolveBindingItems(
    toBindingIdSet(versionPreview.value?.toolIds),
    toolList.value,
    { entityLabel: '工具' }
  )
})

const previewMcpList = computed(() => {
  if (versionPreview.value?.mcpServers?.length) {
    return versionPreview.value.mcpServers.map(markBindingItemDeletedFlag)
  }
  return resolveBindingItems(
    toBindingIdSet(versionPreview.value?.mcpServerIds),
    mcpServerList.value,
    { entityLabel: 'MCP Server' }
  )
})

const previewSubAgentList = computed(() => {
  if (versionPreview.value?.subAgents?.length) {
    return versionPreview.value.subAgents.map(markBindingItemDeletedFlag)
  }
  return resolveBindingItems(
    toBindingIdSet(versionPreview.value?.subAgentIds),
    subAgentList.value,
    { entityLabel: 'SubAgent' }
  )
})

const previewSkillList = computed(() => {
  if (versionPreview.value?.skills?.length) {
    return versionPreview.value.skills.map(markBindingItemDeletedFlag)
  }
  return resolveBindingItems(
    toBindingIdSet(versionPreview.value?.skillIds),
    skillList.value,
    { entityLabel: 'Skill' }
  )
})

const agent = reactive({
  id: null,
  name: '',
  description: '',
  systemPrompt: '',
  agentType: 'chat',
  icon: '',
})

// 页面标题显示的名称和描述（仅在加载和保存后更新，不随表单输入实时同步）
const headerName = ref('')
const headerDescription = ref('')

// 模型配置（存储在 config JSONB 中）
const agentConfig = reactive({
  providerId: null,
  enableFileRead: false,
  enableContentSecurityScan: true,
  streamOutput: true,
  userSensitiveFilterEnabled: false,
  userSensitiveWords: [],
  sensitiveFilterEnabled: false,
  sensitiveFilterStrategy: 'replace',
  sensitiveFilterReplaceText: '***',
  sensitiveWords: [],
  enableSummary: false,
  summaryThresholdKb: 100,
  summaryPrompt: '',
  summaryKeepMessages: 6,
  summaryToolResultTokenLimit: 500,
  maxExecutionSteps: 10,
  modelRetryTimes: 2,
})

const userSensitiveWords = ref([''])
const sensitiveWords = ref([''])

const providerList = ref([])
const configFields = ref([])
const capabilityCollapsed = ref(false)

/** 模型能力卡片字段（与温度、TopP 等调参分离） */
const CAPABILITY_FIELD_KEYS = new Set([
  'multimodalEnabled',
  'enableImageInput',
  'enableVideoInput',
  'enableAudioInput',
  'enableWebSearch',
  'webSearchForceSearch',
  'webSearchMaxKeyword',
  'enableTts',
  'enableReasoning',
])

/** 依赖多模态总开关的子能力 */
const MULTIMODAL_DEPENDENT_KEYS = new Set(['enableImageInput', 'enableVideoInput', 'enableAudioInput'])

/** 依赖联网搜索的子配置 */
const WEB_SEARCH_SUB_KEYS = new Set(['webSearchForceSearch', 'webSearchMaxKeyword'])

const CAPABILITY_PRIMARY_ORDER = [
  'multimodalEnabled',
  'enableImageInput',
  'enableVideoInput',
  'enableAudioInput',
  'enableWebSearch',
  'enableTts',
  'enableReasoning',
]

const capabilityFields = computed(() =>
  configFields.value.filter(f => CAPABILITY_FIELD_KEYS.has(f.key))
)

const modelTuneFields = computed(() =>
  configFields.value.filter(f => f.key !== 'modelId' && !CAPABILITY_FIELD_KEYS.has(f.key))
)

const hasCapabilitySwitches = computed(() =>
  capabilityFields.value.some(f => f.type === 'switch')
)

const primaryCapabilityFields = computed(() => {
  const map = new Map(capabilityFields.value.map(f => [f.key, f]))
  return CAPABILITY_PRIMARY_ORDER.map(k => map.get(k)).filter(Boolean)
})

const webSearchSubFields = computed(() => {
  if (!agentConfig.enableWebSearch) return []
  return capabilityFields.value.filter(f => WEB_SEARCH_SUB_KEYS.has(f.key))
})

const capabilitySwitchFields = computed(() =>
  capabilityFields.value.filter(f => f.type === 'switch')
)

const allCapabilitiesEnabled = computed(() => {
  const switches = capabilitySwitchFields.value
  return switches.length > 0 && switches.every(f => !!agentConfig[f.key])
})

function isCapabilityFieldDisabled(field) {
  if (isVersionPreview.value) return true
  if (MULTIMODAL_DEPENDENT_KEYS.has(field.key) && !agentConfig.multimodalEnabled) return true
  return false
}

watch(
  () => agentConfig.multimodalEnabled,
  (enabled) => {
    if (enabled) return
    for (const key of MULTIMODAL_DEPENDENT_KEYS) {
      if (agentConfig[key]) agentConfig[key] = false
    }
  }
)

watch(
  () => agentConfig.enableWebSearch,
  (enabled) => {
    if (enabled) return
    agentConfig.webSearchForceSearch = false
    for (const field of capabilityFields.value) {
      if (field.key === 'webSearchMaxKeyword' && field.defaultValue !== undefined) {
        agentConfig.webSearchMaxKeyword = field.defaultValue
      }
    }
  }
)

/** 从 config 合并模型能力字段到 modelParams（兼容旧版快照） */
function mergeCapabilityFromConfig(target, cfg) {
  if (!cfg || !target) return
  for (const key of CAPABILITY_FIELD_KEYS) {
    if (cfg[key] !== undefined) {
      target[key] = cfg[key]
    }
  }
}

function formatCapabilityPreviewDisplay(field, value) {
  if (field.type === 'switch') return value ? '开启' : '关闭'
  if (value === undefined || value === null) return '—'
  return String(value)
}

function toggleAllCapabilities() {
  if (isVersionPreview.value) return
  const enable = !allCapabilitiesEnabled.value
  for (const field of capabilitySwitchFields.value) {
    agentConfig[field.key] = enable
  }
  message.success(enable ? '已开启全部模型能力' : '已关闭全部模型能力')
}

const modelList = ref([])
/** 绑定资源目录是否已加载（避免 Tab 懒加载导致误判「已删除」） */
const bindingCatalogsLoaded = ref(false)

// ===== 通用绑定管理 =====
const knowledge = useBinding({
  limit: 10, entityLabel: '知识库', loadApi: getKnowledgeList,
  verb: '绑定',
  deps: { isVersionPreview, bindingCatalogsLoaded },
})
const tools = useBinding({
  limit: 10, entityLabel: '工具', loadApi: getTools,
  filterPredicate: t => (t.toolType?.code || t.toolType) !== 'knowledge' && t.status !== 'disabled',
  verb: '绑定',
  deps: { isVersionPreview, bindingCatalogsLoaded },
})
const mcp = useBinding({
  limit: 5, entityLabel: 'MCP Server', loadApi: getMcpServers,
  filterPredicate: s => s.status !== 'disabled',
  verb: '绑定',
  deps: { isVersionPreview, bindingCatalogsLoaded },
})
const subAgent = useBinding({
  limit: 5, entityLabel: 'SubAgent', loadApi: getSubAgents,
  filterPredicate: s => s.status !== 'disabled',
  verb: '绑定',
  deps: { isVersionPreview, bindingCatalogsLoaded },
})
const skill = useBinding({
  limit: 10, entityLabel: 'Skill', loadApi: getSkills,
  filterPredicate: s => s.status !== 'disabled',
  verb: '启用',
  deps: { isVersionPreview, bindingCatalogsLoaded },
})

// 兼容模板中使用的变量名
const selectedKnowledgeIds = knowledge.selectedIds
const knowledgeList = knowledge.list
const knowledgeLoading = knowledge.loading
const searchText = knowledge.searchText
const selectedToolIds = tools.selectedIds
const toolList = tools.list
const toolSearchText = tools.searchText
const toolTypeFilter = ref('')
const toolTypeList = ref([])
const toolTypeLabels = { builtin: '内置', knowledge: '知识库', api: 'API调用' }
const toolTypeOptions = computed(() => {
  const options = [{ value: '', label: '全部' }]
  for (const t of toolTypeList.value) {
    // 知识库工具由中间件自动注入，不在筛选栏展示
    if (t.value === 'knowledge') continue
    options.push({ value: t.value, label: t.label })
  }
  return options
})
const saving = ref(false)
const publishing = ref(false)
const publishModalVisible = ref(false)
const publishDescription = ref('')
const versionDrawerVisible = ref(false)
const versionList = ref([])
const versionLoading = ref(false)
const selectedVersion = ref('draft')
const versionPreview = ref(null)
const versionHelpVisible = ref(false)
/** 草稿编辑基线快照，用于离开页未保存提示 */
const formBaselineSnapshot = ref(null)

/** 对话配置版本快照默认值（与编排页表单默认一致） */
const CHAT_CONFIG_PREVIEW_DEFAULTS = {
  enableFileRead: false,
  enableContentSecurityScan: true,
  streamOutput: true,
  maxContextMessages: 20,
  enableSummary: false,
  summaryThresholdKb: 100,
  summaryPrompt: '',
  summaryKeepMessages: 6,
  summaryToolResultTokenLimit: 500,
  maxExecutionSteps: 10,
  modelRetryTimes: 2,
  userSensitiveFilterEnabled: false,
  sensitiveFilterEnabled: false,
  sensitiveFilterStrategy: 'replace',
  sensitiveFilterReplaceText: '***',
  asyncToolCalls: false,
}

function chatConfigPreviewValue(key) {
  const cfg = versionPreview.value?.chatConfig
  if (cfg && Object.prototype.hasOwnProperty.call(cfg, key) && cfg[key] !== null) {
    return { value: cfg[key], fromDefault: false }
  }
  return { value: CHAT_CONFIG_PREVIEW_DEFAULTS[key], fromDefault: true }
}

/** 版本快照对话配置展示行 */
const chatConfigPreviewRows = computed(() => {
  if (!versionPreview.value) return []
  const fileRead = chatConfigPreviewValue('enableFileRead')
  const contentScan = chatConfigPreviewValue('enableContentSecurityScan')
  const stream = chatConfigPreviewValue('streamOutput')
  const ctx = chatConfigPreviewValue('maxContextMessages')
  const summary = chatConfigPreviewValue('enableSummary')
  const threshold = chatConfigPreviewValue('summaryThresholdKb')
  const userSens = chatConfigPreviewValue('userSensitiveFilterEnabled')
  const aiSens = chatConfigPreviewValue('sensitiveFilterEnabled')
  const strategy = chatConfigPreviewValue('sensitiveFilterStrategy')
  const replaceText = chatConfigPreviewValue('sensitiveFilterReplaceText')
  const asyncTools = chatConfigPreviewValue('asyncToolCalls')
  const rows = [
    { label: '文件读取', text: fileRead.value ? '开启' : '关闭', fromDefault: fileRead.fromDefault },
    { label: '内容安全扫描', text: contentScan.value ? '开启' : '关闭', fromDefault: contentScan.fromDefault },
    { label: '流式输出', text: stream.value !== false ? '开启' : '关闭', fromDefault: stream.fromDefault },
    { label: '上下文条数', text: String(ctx.value ?? 20), fromDefault: ctx.fromDefault },
    { label: '上下文摘要', text: summary.value ? '开启' : '关闭', fromDefault: summary.fromDefault },
  ]
  if (summary.value) {
    rows.push({
      label: '摘要触发阈值',
      text: `${threshold.value ?? 100} KB`,
      fromDefault: threshold.fromDefault,
    })
    const keepMsgs = chatConfigPreviewValue('summaryKeepMessages')
    const toolLimit = chatConfigPreviewValue('summaryToolResultTokenLimit')
    const summaryPromptVal = chatConfigPreviewValue('summaryPrompt')
    rows.push(
      { label: '摘要提示词', text: summaryPromptVal.value ? '自定义' : '系统默认', fromDefault: summaryPromptVal.fromDefault },
      { label: '摘要保留消息数', text: String(keepMsgs.value ?? 6), fromDefault: keepMsgs.fromDefault },
      { label: '工具结果预览上限', text: `${toolLimit.value ?? 500} Tokens`, fromDefault: toolLimit.fromDefault },
    )
  }
  const maxSteps = chatConfigPreviewValue('maxExecutionSteps')
  const retryTimes = chatConfigPreviewValue('modelRetryTimes')
  rows.push(
    { label: '最大执行步数', text: String(maxSteps.value ?? 10), fromDefault: maxSteps.fromDefault },
    { label: '模型重试次数', text: String(retryTimes.value ?? 2), fromDefault: retryTimes.fromDefault },
  )
  rows.push(
    { label: '用户敏感词', text: userSens.value ? '开启' : '关闭', fromDefault: userSens.fromDefault },
    { label: 'AI输出敏感词', text: aiSens.value ? '开启' : '关闭', fromDefault: aiSens.fromDefault },
  )
  if (aiSens.value) {
    rows.push({
      label: '处理策略',
      text: strategy.value === 'block' ? '拦截并提示' : `替换为 ${replaceText.value || '***'}`,
      fromDefault: strategy.fromDefault || replaceText.fromDefault,
    })
  }
  rows.push({
    label: '工具调用模式',
    text: asyncTools.value ? '异步（并行）' : '串行（逐个）',
    fromDefault: asyncTools.fromDefault,
  })
  return rows
})

/** 版本快照中的模型能力展示（缺失字段用提供商默认值填充） */
const capabilityPreviewItems = computed(() => {
  if (!versionPreview.value) return []
  const mp = versionPreview.value.modelParams || {}
  const webSearchOn = mp.enableWebSearch !== undefined
    ? !!mp.enableWebSearch
    : !!capabilityFields.value.find(f => f.key === 'enableWebSearch')?.defaultValue
  const previewFields = [
    ...primaryCapabilityFields.value,
    ...(webSearchOn ? webSearchSubFields.value : []),
  ]
  return previewFields.map(field => {
    const fromSnapshot = mp[field.key] !== undefined && mp[field.key] !== null
    const raw = fromSnapshot ? mp[field.key] : field.defaultValue
    return {
      key: field.key,
      label: field.label,
      display: formatCapabilityPreviewDisplay(field, raw),
      fromDefault: !fromSnapshot,
    }
  })
})

const agentStatus = ref('draft')
const bindingTab = ref('tools')
const agentVersion = ref(0)

// MCP Server 绑定
const selectedMcpServerIds = mcp.selectedIds
const mcpServerList = mcp.list
const mcpSearchText = mcp.searchText
const mcpLoading = mcp.loading

// SubAgent 绑定
const selectedSubAgentIds = subAgent.selectedIds
const subAgentList = subAgent.list
const subAgentSearchText = subAgent.searchText
const subAgentLoading = subAgent.loading

// Skill 绑定
const selectedSkillIds = skill.selectedIds
const skillList = skill.list
const skillSearchText = skill.searchText
const skillLoading = skill.loading
const recommendedQuestions = ref([])
const generatingPrompt = ref(false)
const generatingQuestions = ref(false)
const avatarUploading = ref(false)

// 工作流相关
const workflowData = ref(null)

// 是否已配置工作流
const hasWorkflowConfig = computed(() => {
  if (!workflowData.value) return false
  const nodes = workflowData.value.nodes || []
  // 检查是否有 start 和 end 节点之外的其他节点
  return nodes.filter(n => n.type !== 'start' && n.type !== 'end').length > 0
})

// 工作流节点统计
const workflowStats = computed(() => {
  if (!workflowData.value) return { total: 0, llm: 0, condition: 0, retrieval: 0, tool: 0, edges: 0 }
  const nodes = workflowData.value.nodes || []
  const edges = workflowData.value.edges || []
  return {
    total: nodes.length,
    llm: nodes.filter(n => n.type === 'llm').length,
    condition: nodes.filter(n => n.type === 'condition').length,
    retrieval: nodes.filter(n => n.type === 'retrieval').length,
    tool: nodes.filter(n => n.type === 'tool').length,
    edges: edges.length
  }
})

function goWorkflowEdit() {
  router.push(`/app/workflow/${agentId}`)
}

function syncPromptVariablesFromConfig(parsed) {
  const list = parsed?.promptVariables
  if (Array.isArray(list) && list.length) {
    promptVariables.value = list.map(v => ({
      _id: `${v.key || ''}-${Date.now()}-${Math.random()}`,
      key: v.key || '',
      label: v.label || '',
      defaultValue: v.defaultValue || '',
      description: v.description || '',
    }))
  } else {
    promptVariables.value = []
  }
}

function syncSensitiveWordsFromConfig(parsed) {
  const userList = parsed?.userSensitiveWords
  if (Array.isArray(userList) && userList.length) {
    userSensitiveWords.value = userList.map(w => String(w || ''))
  } else {
    userSensitiveWords.value = ['']
  }
  if (agentConfig.userSensitiveFilterEnabled == null) {
    agentConfig.userSensitiveFilterEnabled = false
  }
  const list = parsed?.sensitiveWords
  if (Array.isArray(list) && list.length) {
    sensitiveWords.value = list.map(w => String(w || ''))
  } else {
    sensitiveWords.value = ['']
  }
  if (agentConfig.sensitiveFilterEnabled == null) {
    agentConfig.sensitiveFilterEnabled = false
  }
  if (!agentConfig.sensitiveFilterStrategy) {
    agentConfig.sensitiveFilterStrategy = 'replace'
  }
  if (!agentConfig.sensitiveFilterReplaceText) {
    agentConfig.sensitiveFilterReplaceText = '***'
  }
}

function addUserSensitiveWord() {
  if (isVersionPreview.value) return
  userSensitiveWords.value.push('')
}

function removeUserSensitiveWord(idx) {
  if (isVersionPreview.value) return
  userSensitiveWords.value.splice(idx, 1)
  if (userSensitiveWords.value.length === 0) {
    userSensitiveWords.value.push('')
  }
}

function addSensitiveWord() {
  if (isVersionPreview.value) return
  sensitiveWords.value.push('')
}

function removeSensitiveWord(idx) {
  if (isVersionPreview.value) return
  sensitiveWords.value.splice(idx, 1)
  if (sensitiveWords.value.length === 0) {
    sensitiveWords.value.push('')
  }
}

function serializePromptVariables() {
  return promptVariables.value
    .filter(v => v.key?.trim())
    .map(({ key, label, defaultValue, description }) => ({
      key: key.trim(),
      label: (label || key).trim(),
      defaultValue: defaultValue || '',
      description: description || '',
    }))
}

function addPromptVariable() {
  if (isVersionPreview.value) return
  promptVariables.value.push({
    _id: `var-${Date.now()}-${Math.random()}`,
    key: '',
    label: '',
    defaultValue: '',
    description: '',
  })
}

function removePromptVariable(idx) {
  if (isVersionPreview.value) return
  promptVariables.value.splice(idx, 1)
}

function insertPromptVariable(key) {
  if (isVersionPreview.value) return
  const token = `{{${key}}}`
  agent.systemPrompt = (agent.systemPrompt || '') + (agent.systemPrompt ? ' ' : '') + token
}

function addRecommendedQuestion() {
  if (isVersionPreview.value) return
  if (recommendedQuestions.value.length < 3) {
    recommendedQuestions.value.push('')
  }
}

function removeRecommendedQuestion(idx) {
  if (isVersionPreview.value) return
  recommendedQuestions.value.splice(idx, 1)
}

const avatarUrl = computed(() => {
  if (!agent.avatar) return ''
  return agent.avatar
})

/** ModelSelect 复合值 */
const modelSelectValue = computed(() => {
  const pid = agentConfig.providerId
  const mid = agentConfig.modelId
  if (pid && mid) return `${String(pid)}:${String(mid)}`
  return null
})

async function onModelSelectChange({ providerId, modelId }) {
  if (isVersionPreview.value) return
  const prevProviderId = agentConfig.providerId
  agentConfig.providerId = providerId ? String(providerId) : providerId
  agentConfig.modelId = modelId ? String(modelId) : modelId
  if (providerId && String(prevProviderId) !== String(providerId)) {
    // 切换提供商时，清除旧模型参数并加载新配置
    for (const key of currentConfigFieldKeys.value) {
      if (key !== 'providerId' && key !== 'modelId') {
        delete agentConfig[key]
      }
    }
    await loadConfigFields(providerId)
  }
}

// 绑定 computed（从 composable 获取）
const selectedKnowledge = knowledge.selected
const filteredKnowledgeList = knowledge.filteredList
const selectedTools = tools.selected
const selectedMcpServers = mcp.selected
const filteredMcpServerList = mcp.filteredList
const selectedSubAgents = subAgent.selected
const selectedSkills = skill.selected

// filteredToolList 需要额外处理 toolTypeFilter，单独定义
const filteredToolList = computed(() => {
  let list = tools.filteredList.value
  if (toolTypeFilter.value) {
    list = list.filter(t => {
      const type = t.toolType?.code || t.toolType
      return type === toolTypeFilter.value
    })
  }
  return list
})

const deletedBindingCount = computed(() => {
  if (!bindingCatalogsLoaded.value) return 0
  return (
    countDeletedBindingItems(selectedKnowledge.value)
    + countDeletedBindingItems(selectedTools.value)
    + countDeletedBindingItems(selectedMcpServers.value)
    + countDeletedBindingItems(selectedSubAgents.value)
    + countDeletedBindingItems(selectedSkills.value)
  )
})

const disabledBindingCount = computed(() => {
  if (!bindingCatalogsLoaded.value) return 0
  return (
    countDisabledBindingItems(selectedKnowledge.value)
    + countDisabledBindingItems(selectedTools.value)
    + countDisabledBindingItems(selectedMcpServers.value)
    + countDisabledBindingItems(selectedSubAgents.value)
    + countDisabledBindingItems(selectedSkills.value)
  )
})

const invalidBindingCount = computed(() => deletedBindingCount.value + disabledBindingCount.value)

const invalidBindingAlertMessage = computed(() => {
  const parts = []
  if (deletedBindingCount.value > 0) parts.push(`${deletedBindingCount.value} 个已删除`)
  if (disabledBindingCount.value > 0) parts.push(`${disabledBindingCount.value} 个已禁用`)
  return `已绑定资源中有 ${parts.join('、')}`
})

/** 已删除绑定分类型明细（Alert / 保存弹窗） */
const deletedBindingSections = computed(() => [
  { label: '知识库', items: selectedKnowledge.value },
  { label: '工具', items: selectedTools.value },
  { label: 'MCP', items: selectedMcpServers.value },
  { label: 'SubAgent', items: selectedSubAgents.value },
  { label: 'Skill', items: selectedSkills.value },
])

const deletedBindingDetailLines = computed(() =>
  formatDeletedBindingDetailLines(deletedBindingSections.value)
)

function removeAllDeletedBindings() {
  if (isVersionPreview.value) return
  let n = 0
  n += removeDeletedIdsFromSet(selectedKnowledgeIds.value, selectedKnowledge.value)
  n += removeDeletedIdsFromSet(selectedToolIds.value, selectedTools.value)
  n += removeDeletedIdsFromSet(selectedMcpServerIds.value, selectedMcpServers.value)
  n += removeDeletedIdsFromSet(selectedSubAgentIds.value, selectedSubAgents.value)
  n += removeDeletedIdsFromSet(selectedSkillIds.value, selectedSkills.value)
  if (n > 0) {
    message.success(`已移除 ${n} 个已删除的绑定`)
  }
}

const filteredSubAgentList = computed(() => {
  // 可选列表只展示已启用的，已禁用但已绑定的在已选列表中展示
  let list = subAgentList.value.filter(s => s.enabled !== 0 && s.enabled !== false)
  if (subAgentSearchText.value) {
    const keyword = subAgentSearchText.value.toLowerCase()
    list = list.filter(s =>
      s.name?.toLowerCase().includes(keyword) ||
      s.displayName?.toLowerCase().includes(keyword) ||
      s.description?.toLowerCase().includes(keyword)
    )
  }
  return list
})

const filteredSkillList = computed(() => {
  // 可选列表只展示已启用的，已禁用但已绑定的在已选列表中展示
  let list = skillList.value.filter(s => s.status !== 'disabled')
  if (skillSearchText.value) {
    const keyword = skillSearchText.value.toLowerCase()
    list = list.filter(s =>
      s.name?.toLowerCase().includes(keyword) ||
      s.displayName?.toLowerCase().includes(keyword) ||
      s.slug?.toLowerCase().includes(keyword) ||
      s.description?.toLowerCase().includes(keyword)
    )
  }
  return list
})

async function loadProviders() {
  try {
    const res = await getModelProviders({ pageNum: 1, pageSize: 50 })
    providerList.value = res.data.records || []
  } catch (e) {
    // ignore
  }
}

async function loadConfigFields(providerId) {
  if (!providerId) return
  try {
    const [configRes, capRes] = await Promise.all([
      getProviderConfigFields(providerId),
      getProviderModelCapabilities(providerId),
    ])
    configFields.value = [...(configRes.data || []), ...(capRes.data || [])]
    // 记录 configFields 的 key，用于切换提供商时精准清除
    currentConfigFieldKeys.value = new Set(configFields.value.map(f => f.key))
    // 为缺失的字段设置默认值（版本预览时模型能力不自动填默认，由快照决定）
    for (const field of configFields.value) {
      if (isVersionPreview.value && CAPABILITY_FIELD_KEYS.has(field.key)) {
        continue
      }
      if (agentConfig[field.key] === undefined && field.defaultValue !== undefined) {
        agentConfig[field.key] = field.defaultValue
      }
    }
  } catch (e) {
    configFields.value = []
    currentConfigFieldKeys.value = new Set()
  }
}

// 记录当前 configFields 的 key，用于切换提供商时只清除模型参数
const currentConfigFieldKeys = ref(new Set())

function confirmRestoreDefaults() {
  if (isVersionPreview.value) return
  Modal.confirm({
    title: '恢复默认配置',
    content: '确定要将当前提供商下的模型参数恢复为默认值吗？未保存的修改将被覆盖。',
    okText: '继续',
    cancelText: '取消',
    onOk: () => new Promise((resolve, reject) => {
      Modal.confirm({
        title: '再次确认',
        content: '恢复后将无法撤销，是否继续恢复默认配置？',
        okText: '确定恢复',
        okType: 'danger',
        cancelText: '取消',
        onOk: () => {
          restoreDefaults()
          resolve()
        },
        onCancel: () => reject(new Error('cancel')),
      })
    }),
  })
}

function restoreDefaults() {
  if (isVersionPreview.value) return
  for (const field of modelTuneFields.value) {
    if (field.defaultValue !== undefined) {
      agentConfig[field.key] = field.defaultValue
    }
  }
  message.success('已恢复模型参数默认值')
}

async function loadAgent() {
  try {
    const res = await getAgentDetail(agentId)
    const { agent: agentData, knowledgeIds, mcpServerIds, subAgentIds, skillIds } = res.data

    // 分离基本信息和配置
    const { config, agentType, ...basicInfo } = agentData
    Object.assign(agent, basicInfo)
    if (agentType?.code) {
      agent.agentType = agentType.code
    } else if (agentType) {
      agent.agentType = agentType
    }
    if (agent.agentType === 'assistant') {
      agent.agentType = 'chat'
    }
    agentStatus.value = agentData.status?.code || agentData.status || 'draft'
    agentVersion.value = agentData.version || 0
    headerName.value = agent.name || ''
    headerDescription.value = agent.description || ''

    // 解析 config JSONB
    if (config) {
      try {
        const parsed = typeof config === 'string' ? safeJsonParse(config) : config
        if (parsed) {
          stripBindingKeysFromConfig(parsed)
          Object.assign(agentConfig, parsed)
          stripBindingKeysFromConfig(agentConfig)
        }
        syncPromptVariablesFromConfig(parsed || {})
        syncSensitiveWordsFromConfig(parsed || {})
        if (agentConfig.streamOutput === undefined) {
          agentConfig.streamOutput = true
        }
      } catch (e) {
        // ignore
      }
    }

    if (agent.agentType === 'workflow') {
      await loadWorkflowSummary()
    }

    // 解析推荐问题
    if (agentData.recommendedQuestions) {
      try {
        recommendedQuestions.value = typeof agentData.recommendedQuestions === 'string'
          ? JSON.parse(agentData.recommendedQuestions)
          : agentData.recommendedQuestions
      } catch { recommendedQuestions.value = [] }
    }

    // 加载提供商列表和配置字段
    await loadProviders()
    if (agentConfig.providerId) {
      await loadConfigFields(agentConfig.providerId)
    }

    selectedKnowledgeIds.value = toBindingIdSet(knowledgeIds)
    selectedSubAgentIds.value = toBindingIdSet(subAgentIds)
    selectedSkillIds.value = toBindingIdSet(skillIds)

    // 工具绑定 ID（须从接口读取，已删除的工具不会出现在 detail 列表中）
    const toolIdsRes = await getAgentToolIds(agentId)
    selectedToolIds.value = toBindingIdSet(toolIdsRes.data)

    // MCP Server IDs（从 detail 接口获取，统一字符串避免精度丢失）
    selectedMcpServerIds.value = toBindingIdSet(mcpServerIds)

    if (agent.agentType !== 'workflow') {
      await loadBindingCatalogs()
      await mergeBoundToolDetails()
    } else {
      bindingCatalogsLoaded.value = true
    }
  } catch (e) {
    bindingCatalogsLoaded.value = false
    // interceptor已处理错误提示
  } finally {
    refreshFormBaseline()
  }
}

function captureFormSnapshot() {
  const sortedIds = (set) => [...set].map(toBindingId).filter(Boolean).sort()
  return JSON.stringify({
    agent: {
      name: agent.name,
      description: agent.description,
      systemPrompt: agent.systemPrompt,
      welcomeMessage: agent.welcomeMessage,
      avatar: agent.avatar,
      agentType: agent.agentType,
    },
    agentConfig: { ...agentConfig },
    recommendedQuestions: [...recommendedQuestions.value],
    promptVariables: promptVariables.value.map(v => ({ key: v.key, label: v.label, defaultValue: v.defaultValue })),
    knowledgeIds: sortedIds(selectedKnowledgeIds.value),
    toolIds: sortedIds(selectedToolIds.value),
    mcpServerIds: sortedIds(selectedMcpServerIds.value),
    subAgentIds: sortedIds(selectedSubAgentIds.value),
    skillIds: sortedIds(selectedSkillIds.value),
    userSensitiveWords: [...userSensitiveWords.value],
    sensitiveWords: [...sensitiveWords.value],
  })
}

function refreshFormBaseline() {
  formBaselineSnapshot.value = captureFormSnapshot()
}

function isFormDirty() {
  if (isVersionPreview.value || !formBaselineSnapshot.value) return false
  return captureFormSnapshot() !== formBaselineSnapshot.value
}

function confirmLeaveUnsaved(onConfirm) {
  Modal.confirm({
    title: '未保存的修改',
    content: '当前有未保存的修改，离开后将丢失，是否继续？',
    okText: '离开',
    okType: 'danger',
    cancelText: '继续编辑',
    onOk: onConfirm,
  })
}

/** 保存/发布前确认：仍绑定已删除或已禁用的资源 */
function confirmSaveWithDeletedBindings() {
  const lines = deletedBindingDetailLines.value
  const hasDeleted = deletedBindingCount.value > 0
  const hasDisabled = disabledBindingCount.value > 0
  const title = hasDeleted && hasDisabled ? '存在已删除或已禁用的绑定'
    : hasDeleted ? '存在已删除的绑定' : '存在已禁用的绑定'
  const tipText = hasDeleted && hasDisabled
    ? `仍有 ${deletedBindingCount.value} 个已删除、${disabledBindingCount.value} 个已禁用的资源处于选中状态，保存后 Agent 可能无法正常使用：`
    : hasDeleted
      ? `仍有 ${deletedBindingCount.value} 个已删除的资源处于选中状态，保存后 Agent 可能无法正常使用：`
      : `仍有 ${disabledBindingCount.value} 个已禁用的资源处于选中状态，保存后 Agent 可能无法正常使用：`
  return new Promise((resolve) => {
    Modal.confirm({
      title,
      width: 480,
      content: h('div', { class: 'deleted-binding-modal' }, [
        h('p', { class: 'deleted-binding-modal-tip' }, tipText),
        ...lines.map(line => h('div', { class: 'deleted-binding-modal-line' }, line)),
        h('p', { class: 'deleted-binding-modal-foot' },
          hasDeleted ? '建议先点击「移除已删除绑定」或手动解绑后再保存。' : '建议先启用对应的资源或手动解绑后再保存。'),
      ]),
      okText: '仍然保存',
      okType: 'danger',
      cancelText: '返回修改',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
}

function handleGoBack() {
  // 未保存提示统一由 onBeforeRouteLeave 处理，避免与返回按钮重复弹窗
  router.push('/app/agents')
}

async function copyAgentId() {
  if (!agent.id) {
    message.info('新建保存后生成 ID')
    return
  }
  const text = String(agent.id)
  try {
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(text)
    } else {
      fallbackCopy(text)
    }
    message.success('已复制智能体 ID')
  } catch {
    fallbackCopy(text)
    message.success('已复制智能体 ID')
  }
}

function fallbackCopy(text) {
  const ta = document.createElement('textarea')
  ta.value = text
  ta.style.cssText = 'position:fixed;left:-9999px'
  document.body.appendChild(ta)
  ta.select()
  document.execCommand('copy')
  document.body.removeChild(ta)
}

onBeforeRouteLeave((_to, _from, next) => {
  if (!isFormDirty()) {
    next()
    return
  }
  confirmLeaveUnsaved(() => next())
})

const loadKnowledgeList = knowledge.load

async function loadToolTypes() {
  try {
    const res = await getToolTypes()
    toolTypeList.value = res.data || []
  } catch (e) {
    console.error('[AgentDetail] 加载工具类型枚举失败:', e)
  }
}

function normalizeToolRecord(t) {
  if (!t) return t
  return {
    ...t,
    id: toBindingId(t.id),
  }
}

function isKnowledgeTool(t) {
  return (t?.toolType?.code || t?.toolType) === 'knowledge'
}

function summarizeSchema(schema) {
  try {
    const obj = typeof schema === 'string' ? JSON.parse(schema) : schema
    const props = obj?.properties
    if (!props) return '无参数'
    return Object.keys(props).join(', ')
  } catch { return '无参数' }
}

function parseToolsList(tools) {
  try {
    const arr = typeof tools === 'string' ? JSON.parse(tools) : tools
    return Array.isArray(arr) && arr.length > 0 ? arr.join(', ') : '无'
  } catch { return '无' }
}

/** 合并工具目录（不整表覆盖，避免切换 Tab 时丢失已绑定/知识库） */
function mergeToolsIntoCatalog(incoming) {
  const byId = new Map()
  for (const t of toolList.value) {
    const id = toBindingId(t.id)
    if (id) byId.set(id, normalizeToolRecord(t))
  }
  for (const t of incoming || []) {
    const id = toBindingId(t.id)
    if (id) byId.set(id, normalizeToolRecord(t))
  }
  for (const id of selectedToolIds.value) {
    if (!id || byId.has(id)) continue
    const prev = toolList.value.find(x => toBindingId(x.id) === id)
    if (prev) byId.set(id, normalizeToolRecord(prev))
  }
  toolList.value = [...byId.values()]
}

async function loadToolList(toolType) {
  try {
    const params = { pageNum: 1, pageSize: 200 }
    if (toolType) params.toolType = toolType
    const res = await getTools(params)
    const incoming = (res.data?.records || []).map(normalizeToolRecord)
    mergeToolsIntoCatalog(incoming)
    await mergeBoundToolDetails()
  } catch (e) {
    console.error('[AgentDetail] 加载工具列表失败:', e)
  }
}

const toggleKnowledge = knowledge.toggle
const removeKnowledge = knowledge.remove
const clearSelectedKnowledge = knowledge.clear

const toggleTool = tools.toggle
const removeTool = tools.remove
const clearSelectedTools = tools.clear

const toggleMcpServer = mcp.toggle
const removeMcpServer = mcp.remove
const clearSelectedMcpServers = mcp.clear

const toggleSubAgent = subAgent.toggle
const removeSubAgent = subAgent.remove
const clearSelectedSubAgents = subAgent.clear

const toggleSkill = skill.toggle
const removeSkill = skill.remove
const clearSelectedSkills = skill.clear

const loadSubAgentList = subAgent.load

const loadSkillList = skill.load

/** 进入详情时预加载全部绑定目录，避免未点 Tab 误判「已删除」 */
async function loadBindingCatalogs() {
  bindingCatalogsLoaded.value = false
  try {
    await Promise.all([
      loadKnowledgeList(),
      loadMcpServerList(),
      loadSubAgentList(),
      loadSkillList(),
      loadToolTypes(),
      loadToolList(toolTypeFilter.value || undefined),
    ])
  } finally {
    bindingCatalogsLoaded.value = true
  }
}

/** 将已绑定工具实体并入目录（listByIds 结果，不受分页/筛选影响） */
async function mergeBoundToolDetails() {
  if (!agentId) return
  try {
    const res = await getAgentToolDetails(agentId)
    mergeToolsIntoCatalog((res.data || []).map(normalizeToolRecord))
  } catch {
    // ignore
  }
}

// Tab 切换刷新
async function onBindingTabChange(tab) {
  if (tab === 'tools') {
    await Promise.all([loadToolTypes(), loadToolList(toolTypeFilter.value || undefined)])
  } else if (tab === 'mcp') {
    await loadMcpServerList()
  } else if (tab === 'knowledge') {
    await loadKnowledgeList()
  } else if (tab === 'subagents') {
    await loadSubAgentList()
  } else if (tab === 'skill') {
    await loadSkillList()
  }
}

const loadMcpServerList = mcp.load

async function handleGeneratePrompt() {
  if (isVersionPreview.value) return
  generatingPrompt.value = true
  try {
    const res = await generateAgentPrompt(agentId)
    const generated = res.data || ''
    agent.systemPrompt = generated.length > 2000 ? generated.slice(0, 2000) : generated
    if (generated.length > 2000) {
      message.warning('生成内容已截断至 2000 字')
    } else {
      message.success('提示词生成成功')
    }
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    generatingPrompt.value = false
  }
}

async function handleGenerateQuestions() {
  if (isVersionPreview.value) return
  generatingQuestions.value = true
  try {
    const res = await generateAgentQuestions(agentId)
    const questions = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
    recommendedQuestions.value = questions.map(q => q.length > 30 ? q.slice(0, 30) : q)
    const truncated = questions.some(q => q.length > 30)
    if (truncated) {
      message.warning('部分推荐问题已截断至 30 字')
    } else {
      message.success('推荐问题生成成功')
    }
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    generatingQuestions.value = false
  }
}

function triggerAvatarUpload() {
  if (isVersionPreview.value) return
  avatarInputRef.value?.click()
}

async function onAvatarFileChange(e) {
  if (isVersionPreview.value) return
  const file = e.target.files[0]
  if (!file) return
  avatarUploading.value = true
  try {
    const res = await uploadAgentAvatar(agentId, file)
    agent.avatar = res.data
    message.success('头像上传成功')
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    avatarUploading.value = false
    // 清空 input 以允许重新选择同一文件
    if (avatarInputRef.value) avatarInputRef.value.value = ''
  }
}

async function handleSaveWorkflowBasic() {
  if (!agent.name?.trim()) {
    message.warning('请输入 Agent 名称')
    return false
  }
  const questions = recommendedQuestions.value.filter(q => q && q.trim())
  if (questions.length > 3) {
    message.warning('推荐问题最多3个')
    return false
  }
  for (const q of questions) {
    if (q.length > 30) {
      message.warning('每个推荐问题不超过30字')
      return false
    }
  }

  saving.value = true
  try {
    await updateAgent({
      ...agent,
      agentType: agent.agentType?.code || agent.agentType,
      recommendedQuestions: JSON.stringify(questions),
    })
    message.success('保存成功')
    await loadAgent()
    return true
  } catch {
    return false
  } finally {
    saving.value = false
  }
}

async function handleSave(options = {}) {
  const { skipDeletedBindingCheck = false } = options
  if (isVersionPreview.value) return false
  if (!agent.name?.trim()) {
    message.warning('请输入 Agent 名称')
    return false
  }
  if (!agent.agentType) {
    message.warning('请选择类型')
    return false
  }
  if (agent.agentType === 'workflow') {
    return handleSaveWorkflowBasic()
  }
  if (!agentConfig.providerId) {
    message.warning('请选择模型提供商')
    return false
  }
  if (!agentConfig.modelId) {
    message.warning('请选择模型')
    return false
  }

  // 2. 过滤空的推荐问题并校验
  const questions = recommendedQuestions.value.filter(q => q && q.trim())
  if (questions.length > 3) {
    message.warning('推荐问题最多3个')
    return false
  }
  for (const q of questions) {
    if (q.length > 30) {
      message.warning('每个推荐问题不超过30字')
      return false
    }
  }

  // 4. 校验知识库数量
  if (selectedKnowledgeIds.value.size > BIND_LIMITS.knowledge) {
    message.warning(`每个 Agent 最多绑定 ${BIND_LIMITS.knowledge} 个知识库`)
    return false
  }
  if (selectedToolIds.value.size > BIND_LIMITS.tool) {
    message.warning(`每个 Agent 最多绑定 ${BIND_LIMITS.tool} 个工具`)
    return false
  }
  if (selectedMcpServerIds.value.size > BIND_LIMITS.mcp) {
    message.warning(`每个 Agent 最多绑定 ${BIND_LIMITS.mcp} 个 MCP Server`)
    return false
  }
  if (selectedSubAgentIds.value.size > BIND_LIMITS.subAgent) {
    message.warning(`每个 Agent 最多绑定 ${BIND_LIMITS.subAgent} 个 SubAgent`)
    return false
  }

  // 4.1 校验 Skill 依赖工具是否已绑定（知识库类型工具默认绑定，跳过）
  const skillDepWarnings = []
  const toolIdSet = selectedToolIds.value
  const toolMetaMap = new Map(toolList.value.map(t => [String(t.id), { name: t.displayName || t.name, type: t.toolType?.code || t.toolType }]))
  const knowledgeToolIds = new Set([...toolMetaMap].filter(([, v]) => v.type === 'knowledge').map(([k]) => k))
  for (const sid of selectedSkillIds.value) {
    const skill = skillList.value.find(s => String(s.id) === String(sid))
    const depToolIds = Array.isArray(skill.toolIds) ? skill.toolIds
      : typeof skill.toolIds === 'string' && skill.toolIds ? JSON.parse(skill.toolIds) : []
    if (!depToolIds.length) continue
    const missing = depToolIds
      .map(tid => String(tid))
      .filter(tid => !toolIdSet.has(tid) && !knowledgeToolIds.has(tid))
    if (missing.length) {
      const names = missing.map(tid => toolMetaMap.get(tid)?.name || tid).join('、')
      skillDepWarnings.push(`「${skill.displayName || skill.name}」依赖 ${names}`)
    }
  }
  // 4.2 校验 Skill 依赖 MCP Server 是否已绑定
  const mcpIdSet = selectedMcpServerIds.value
  const mcpMetaMap = new Map(mcpServerList.value.map(m => [String(m.id), m.name || m.displayName || m.id]))
  for (const sid of selectedSkillIds.value) {
    const skill = skillList.value.find(s => String(s.id) === String(sid))
    const depMcpIds = Array.isArray(skill.mcpServerIds) ? skill.mcpServerIds
      : typeof skill.mcpServerIds === 'string' && skill.mcpServerIds ? JSON.parse(skill.mcpServerIds) : []
    if (!depMcpIds.length) continue
    const missing = depMcpIds
      .map(mid => String(mid))
      .filter(mid => !mcpIdSet.has(mid))
    if (missing.length) {
      const names = missing.map(mid => mcpMetaMap.get(mid) || mid).join('、')
      skillDepWarnings.push(`「${skill.displayName || skill.name}」依赖 MCP Server: ${names}`)
    }
  }
  if (skillDepWarnings.length) {
    const confirmed = await new Promise(resolve => {
      Modal.confirm({
        title: 'Skill 依赖未绑定',
        content: h('div', { style: 'white-space: pre-line' },
          skillDepWarnings.join('\n') + '\n\n未绑定的依赖将无法在对话中使用，是否继续？'),
        okText: '继续保存',
        cancelText: '取消',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      })
    })
    if (!confirmed) return false
  }

  const serializedVars = serializePromptVariables()
  const keys = serializedVars.map(v => v.key)
  if (keys.length !== new Set(keys).size) {
    message.warning('变量名不能重复')
    return false
  }
  for (const v of serializedVars) {
    if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(v.key)) {
      message.warning(`变量名格式不正确: ${v.key}`)
      return false
    }
  }

  if (!skipDeletedBindingCheck && invalidBindingCount.value > 0) {
    const proceed = await confirmSaveWithDeletedBindings()
    if (!proceed) return false
  }

  saving.value = true
  try {
    // 0. 开关关闭时清除关联配置
    if (!agentConfig.userSensitiveFilterEnabled) {
      userSensitiveWords.value = ['']
      agentConfig.userSensitiveWords = []
    }
    if (!agentConfig.sensitiveFilterEnabled) {
      sensitiveWords.value = ['']
      agentConfig.sensitiveWords = []
      agentConfig.sensitiveFilterStrategy = 'replace'
      agentConfig.sensitiveFilterReplaceText = '***'
    }
    if (!agentConfig.enableSummary) {
      agentConfig.summaryThresholdKb = 100
      agentConfig.summaryPrompt = ''
      agentConfig.summaryKeepMessages = 6
      agentConfig.summaryToolResultTokenLimit = 500
    }

    // 1. 构建 config JSONB（包含 provider + 所有配置项）
    const serializedVars = serializePromptVariables()
    const serializedUserSensitiveWords = userSensitiveWords.value
      .map(w => (w || '').trim())
      .filter(Boolean)
    const serializedSensitiveWords = sensitiveWords.value
      .map(w => (w || '').trim())
      .filter(Boolean)
    const configObj = {
      ...agentConfig,
      promptVariables: serializedVars,
      userSensitiveWords: serializedUserSensitiveWords,
      sensitiveWords: serializedSensitiveWords,
    }
    stripBindingKeysFromConfig(configObj)
    const configStr = JSON.stringify(configObj)

    // 2. 更新 Agent
    await updateAgent({
      ...agent,
      agentType: agent.agentType?.code || agent.agentType,
      config: configStr,
      recommendedQuestions: JSON.stringify(questions),
    })

    // 3. 更新知识库绑定
    await updateAgentKnowledge(agentId, Array.from(selectedKnowledgeIds.value))

    // 4. 更新工具绑定
    await updateAgentTools(agentId, Array.from(selectedToolIds.value))

    // 5. 更新 MCP Server 绑定
    await updateAgentMcpServers(agentId, Array.from(selectedMcpServerIds.value))

    // 6. 更新 SubAgent 绑定
    await updateAgentSubAgents(agentId, Array.from(selectedSubAgentIds.value))

    // 7. 更新 Skill 绑定
    await updateAgentSkills(agentId, Array.from(selectedSkillIds.value))

    await loadAgent()
    saving.value = false
    message.success('暂存成功')
    return true
  } catch (e) {
    // interceptor已处理错误提示
    return false
  } finally {
    saving.value = false
  }
}

const agentStatusText = computed(() => {
  const map = {
    draft: '草稿',
    published: agentVersion.value > 0 ? `已发布 v${agentVersion.value}` : '已发布',
    published_editing: agentVersion.value > 0 ? `编辑中 v${agentVersion.value}` : '编辑中',
  }
  return map[agentStatus.value] || agentStatus.value
})

const agentStatusClass = computed(() => {
  const code = (agentStatus.value || 'draft').replace(/_/g, '-')
  return `status-${code}`
})

async function loadWorkflowSummary() {
  try {
    const wfRes = await getWorkflowConfig(agentId)
    const draft = wfRes.data?.draft
    const published = wfRes.data?.published
    workflowData.value = draft || published || { nodes: [], edges: [] }
    if (wfRes.data?.status) {
      agentStatus.value = wfRes.data.status?.code || wfRes.data.status
    }
    if (wfRes.data?.publishedVersion != null) {
      agentVersion.value = wfRes.data.publishedVersion
    }
  } catch {
    workflowData.value = { nodes: [], edges: [] }
  }
}

function formatVersionTime(val) {
  if (val == null || val === '') return ''
  const raw = String(val)
  const normalized = raw.includes('T') && !raw.endsWith('Z')
    ? raw.replace(/(\.\d{3})\d*/, '$1')
    : raw
  const d = new Date(normalized)
  if (Number.isNaN(d.getTime())) {
    return raw.slice(0, 19).replace('T', ' ')
  }
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatVersionDesc(item) {
  const parts = []
  const time = formatVersionTime(item.publishedAt)
  if (time) parts.push(time)
  return parts.join(' · ') || '—'
}

async function openVersionDrawer() {
  versionDrawerVisible.value = true
  versionLoading.value = true
  try {
    await Promise.all([
      loadKnowledgeList(),
      loadMcpServerList(),
      loadSubAgentList(),
      loadSkillList(),
      listAgentVersions(agentId).then(res => {
        versionList.value = res.data || []
      })
    ])
  } catch (e) {
    message.error(e.message || '加载版本失败')
  } finally {
    versionLoading.value = false
  }
}

/** 将版本详情应用到页面表单（只读预览态下回显完整配置与绑定） */
async function applyVersionPreviewToForm(data) {
  const basic = data.basicInfo || {}
  const modelParams = data.modelParams || {}
  const chatConfig = data.chatConfig || {}

  if (basic.systemPrompt !== undefined) agent.systemPrompt = basic.systemPrompt || ''

  const mergedConfig = { ...modelParams, ...chatConfig }
  Object.keys(mergedConfig).forEach(key => {
    if (mergedConfig[key] !== undefined) {
      agentConfig[key] = mergedConfig[key]
    }
  })
  if (agentConfig.providerId != null) agentConfig.providerId = String(agentConfig.providerId)
  if (agentConfig.modelId != null) agentConfig.modelId = String(agentConfig.modelId)
  syncPromptVariablesFromConfig(agentConfig)
  syncSensitiveWordsFromConfig(agentConfig)

  selectedKnowledgeIds.value = toBindingIdSet(data.knowledgeIds)
  selectedMcpServerIds.value = toBindingIdSet(data.mcpServerIds)
  selectedSubAgentIds.value = toBindingIdSet(data.subAgentIds)
  selectedToolIds.value = toBindingIdSet(data.toolIds)
  selectedSkillIds.value = toBindingIdSet(data.skillIds)

  if (data.tools?.length) {
    for (const t of data.tools) {
      if (!toolList.value.some(x => String(x.id) === String(t.id))) {
        toolList.value.push({ ...t, id: String(t.id) })
      }
    }
  }
  if (data.knowledges?.length) {
    for (const k of data.knowledges) {
      if (!knowledgeList.value.some(x => String(x.id) === String(k.id))) {
        knowledgeList.value.push({ ...k, id: String(k.id) })
      }
    }
  }
  if (data.mcpServers?.length) {
    for (const m of data.mcpServers) {
      if (!mcpServerList.value.some(x => String(x.id) === String(m.id))) {
        mcpServerList.value.push({ ...m, id: String(m.id) })
      }
    }
  }
  if (data.subAgents?.length) {
    for (const s of data.subAgents) {
      if (!subAgentList.value.some(x => String(x.id) === String(s.id))) {
        subAgentList.value.push({ ...s, id: String(s.id) })
      }
    }
  }
  if (data.skills?.length) {
    for (const s of data.skills) {
      if (!skillList.value.some(x => String(x.id) === String(s.id))) {
        skillList.value.push({ ...s, id: String(s.id) })
      }
    }
  }

  const providerId = agentConfig.providerId
  if (providerId) {
    await loadConfigFields(providerId)
    // 快照未记录的模型能力字段保持不填，避免沿用上一版本残留
    for (const field of capabilityFields.value) {
      if (modelParams[field.key] === undefined) {
        delete agentConfig[field.key]
      }
    }
  }
}

function normalizeVersionDetailData(data) {
  const payload = data.payload || {}
  const basicInfo = data.basicInfo || (payload.systemPrompt !== undefined ? {
    systemPrompt: payload.systemPrompt,
    welcomeMessage: payload.welcomeMessage,
    recommendedQuestions: payload.recommendedQuestions
  } : {})

  let modelParams = data.modelParams || {}
  let chatConfig = data.chatConfig || {}
  if (!data.modelParams && payload.config) {
    const cfg = payload.config
    modelParams = {
      providerId: cfg.providerId,
      modelId: cfg.modelId,
      temperature: cfg.temperature,
      topP: cfg.topP,
      maxTokens: cfg.maxTokens,
      presencePenalty: cfg.presencePenalty,
      frequencyPenalty: cfg.frequencyPenalty,
      repetitionPenalty: cfg.repetitionPenalty,
    }
    mergeCapabilityFromConfig(modelParams, cfg)
    chatConfig = {
      streamOutput: cfg.streamOutput,
      maxContextMessages: cfg.maxContextMessages,
      enableSummary: cfg.enableSummary,
      summaryThresholdKb: cfg.summaryThresholdKb,
      summaryPrompt: cfg.summaryPrompt,
      summaryKeepMessages: cfg.summaryKeepMessages,
      summaryToolResultTokenLimit: cfg.summaryToolResultTokenLimit,
      maxExecutionSteps: cfg.maxExecutionSteps,
      modelRetryTimes: cfg.modelRetryTimes,
      userSensitiveFilterEnabled: cfg.userSensitiveFilterEnabled,
      userSensitiveWords: cfg.userSensitiveWords,
      sensitiveFilterEnabled: cfg.sensitiveFilterEnabled,
      sensitiveFilterStrategy: cfg.sensitiveFilterStrategy,
      sensitiveFilterReplaceText: cfg.sensitiveFilterReplaceText,
      sensitiveWords: cfg.sensitiveWords,
      asyncToolCalls: cfg.asyncToolCalls,
      enableFileRead: cfg.enableFileRead,
      promptVariables: cfg.promptVariables
    }
  }

  const knowledgeIds = data.knowledgeIds?.length
    ? data.knowledgeIds
    : (payload.knowledgeIds || payload.knowledges || [])
  const toolIds = data.toolIds?.length
    ? data.toolIds
    : (payload.toolIds || payload.tools || [])
  const mcpServerIds = data.mcpServerIds?.length
    ? data.mcpServerIds
    : (payload.mcpServerIds || payload.mcpServers || [])
  const subAgentIds = data.subAgentIds?.length
    ? data.subAgentIds
    : (payload.subAgentIds || payload.subagents || [])
  const skillIds = data.skillIds?.length
    ? data.skillIds
    : (payload.skillIds || payload.skills || [])

  if (payload.config) {
    mergeCapabilityFromConfig(modelParams, payload.config)
  }

  return {
    basicInfo,
    modelParams,
    chatConfig,
    knowledgeIds: (knowledgeIds || []).map(String),
    toolIds: (toolIds || []).map(String),
    mcpServerIds: (mcpServerIds || []).map(String),
    subAgentIds: (subAgentIds || []).map(String),
    skillIds: (skillIds || []).map(String),
    knowledges: data.knowledges || [],
    tools: data.tools || [],
    mcpServers: data.mcpServers || [],
    subAgents: data.subAgents || [],
    skills: data.skills || [],
    description: data.description,
    payload
  }
}

async function selectAgentVersion(version) {
  selectedVersion.value = version
  if (version === 'draft') {
    versionPreview.value = null
    await loadAgent()
    return
  }
  versionLoading.value = true
  try {
    const res = await getAgentVersionDetail(agentId, version)
    const data = res.data || {}
    if (data.kind === 'workflow') {
      message.info('工作流版本请在编排页查看')
      versionPreview.value = null
      return
    }

    const normalized = normalizeVersionDetailData(data)
    versionPreview.value = normalized
    await applyVersionPreviewToForm(normalized)
  } catch (e) {
    message.error(e.message || '加载版本失败')
  } finally {
    versionLoading.value = false
  }
}

function confirmDeleteVersion(item) {
  const version = item.version
  Modal.confirm({
    title: '删除版本',
    content: `确定要删除 v${version} 版本吗？删除后无法恢复。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteAgentVersion(agentId, version)
        message.success(`已删除 v${version}`)
        // 如果当前预览的就是被删除版本，回到草稿
        if (selectedVersion.value === version) {
          await selectAgentVersion('draft')
        }
        await openVersionDrawer()
      } catch (e) {
        message.error(e.message || '删除失败')
      }
    },
  })
}

function confirmRestoreVersion() {
  if (selectedVersion.value === 'draft') return
  const version = selectedVersion.value
  Modal.confirm({
    title: '恢复到此版本',
    content: `确定将 v${version} 的配置恢复到当前编辑态吗？未发布的修改将被覆盖。`,
    okText: '确认恢复',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await restoreAgentVersion(agentId, version)
        message.success(`已恢复 v${version} 到当前编辑`)
        await selectAgentVersion('draft')
        agentStatus.value = 'published_editing'
      } catch (e) {
        message.error(e.message || '恢复失败')
      }
    },
  })
}

async function handlePublish() {
  if (invalidBindingCount.value > 0) {
    const proceed = await confirmSaveWithDeletedBindings()
    if (!proceed) return
  }
  publishDescription.value = ''
  publishModalVisible.value = true
}

async function confirmPublishAgent() {
  publishing.value = true
  try {
    const saved = await handleSave({ skipDeletedBindingCheck: true })
    if (!saved) {
      return Promise.reject()
    }
    const desc = publishDescription.value?.trim() || undefined
    const res = await publishAgent(agentId, desc ? { description: desc } : {})
    agentVersion.value = res.data?.version ?? agentVersion.value + 1
    agentStatus.value = res.data?.status || 'published'
    publishing.value = false
    publishModalVisible.value = false
    message.success(`发布成功 v${agentVersion.value}`)
  } catch {
    // interceptor 已提示
  } finally {
    publishing.value = false
  }
}

function startChat() {
  router.push({ path: '/app/chat', query: { agentId: agentId } })
}

onMounted(async () => {
  pageLoading.value = true
  try {
    if (agentId) {
      await loadAgent()
    } else {
      await Promise.all([loadToolTypes(), loadToolList()])
      bindingCatalogsLoaded.value = true
    }
  } finally {
    pageLoading.value = false
  }
})

</script>

<style scoped>
.page {
  position: relative;
  padding: 32px;
  height: 100vh;
  overflow-y: auto;
  background: var(--color-canvas-soft);
}
.page--loading {
  overflow: hidden;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  gap: 16px;
}
.page-header-left {
  display: flex;
  align-items: flex-start;
  min-width: 0;
  flex: 1;
}
.page-header-titles {
  min-width: 0;
  flex: 1;
}
.page-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.page-title-row .page-title {
  margin-bottom: 0;
}
.btn-agent-id {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-mute);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  transition: color 0.2s, background 0.2s;
}
.btn-agent-id:hover {
  color: var(--color-link);
  background: var(--color-info-bg);
}
.btn-back {
  background: none;
  border: none;
  color: var(--color-mute);
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
}
.btn-back:hover {
  color: var(--color-link);
}
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 4px;
}
.page-desc {
  font-size: 14px;
  color: var(--color-mute);
}
.btn-primary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 100px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary:hover:not(:disabled) {
  background: #27272a;
}
.btn-primary:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.agent-status-badge {
  display: inline-flex;
  align-items: center;
  padding: 5px 12px;
  border-radius: 100px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
  white-space: nowrap;
  border: 1px solid transparent;
}
.agent-status-badge.status-draft {
  background: var(--color-canvas-soft-2);
  color: var(--color-body);
  border-color: var(--color-hairline);
}
.agent-status-badge.status-published {
  background: var(--color-success-bg);
  color: #15803d;
  border-color: #bbf7d0;
}
.agent-status-badge.status-published-editing {
  background: var(--color-warn-bg-deep);
  color: #b45309;
  border-color: #fde68a;
}
.publish-modal-content {
  padding-bottom: 8px;
}
.publish-modal-tip {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--color-mute);
}
.publish-modal-textarea {
  margin-bottom: 28px;
}
:deep(.publish-modal .ant-modal-body) {
  padding-bottom: 28px;
}
:deep(.publish-modal .ant-modal-footer) {
  margin-top: 4px;
  padding-top: 20px;
  border-top: 1px solid var(--color-hairline);
}
.link-btn {
  border: none;
  background: none;
  color: var(--color-link);
  cursor: pointer;
  padding: 0;
  font-size: 13px;
}
.agent-edit-surface {
  position: relative;
}
/* 版本预览：锁定区内所有子元素不可点，容器本身可滚动 */
.agent-edit-surface.is-version-preview .preview-lock-zone {
  position: relative;
  opacity: 0.72;
  user-select: none;
  filter: grayscale(0.06);
  pointer-events: auto;
}
.agent-edit-surface.is-version-preview .preview-lock-zone * {
  pointer-events: none !important;
  cursor: not-allowed !important;
}
.agent-edit-surface.is-version-preview .config-panel-tabs--preview :deep(.ant-tabs-nav),
.agent-edit-surface.is-version-preview .config-panel-tabs--preview :deep(.ant-tabs-extra-content),
.agent-edit-surface.is-version-preview .binding-tabs--preview :deep(.ant-tabs-nav) {
  opacity: 1;
  filter: none;
  pointer-events: auto;
}
.agent-edit-surface.is-version-preview :deep(.ant-tabs-tab) {
  cursor: pointer !important;
  pointer-events: auto !important;
}
.agent-edit-surface.is-version-preview .avatar-upload.is-readonly {
  pointer-events: none;
}
.version-preview-banner-top {
  margin-bottom: 16px;
  pointer-events: auto;
}
.agent-version-drawer .version-drawer-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 6px;
  border: 1px solid transparent;
}
.agent-version-drawer .version-drawer-item:hover {
  background: var(--color-canvas-soft-2);
}
.agent-version-drawer .version-drawer-item.active {
  background: var(--color-info-bg);
  border-color: #c7d2fe;
}
.version-drawer-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.version-drawer-spacer {
  flex: 1;
}
.version-delete-btn {
  opacity: 0;
  transition: opacity 0.15s;
}
.agent-version-drawer .version-drawer-item:hover .version-delete-btn {
  opacity: 1;
}
.version-drawer-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--color-ink);
}
.version-drawer-note {
  font-size: 13px;
  color: var(--color-text-dark);
  margin-bottom: 4px;
  word-break: break-word;
}
.version-drawer-desc {
  font-size: 12px;
  color: var(--color-mute);
}
.agent-version-drawer :deep(.ant-drawer-body) {
  overflow-y: auto;
  max-height: calc(100vh - 108px);
}
.version-preview-detail {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--color-hairline);
}
.preview-detail-title {
  margin: 0 0 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-code);
}
.preview-section {
  margin-bottom: 20px;
  padding: 12px;
  background: var(--color-canvas-soft);
  border-radius: 8px;
  border: 1px solid var(--color-border-slate);
}
.preview-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-dark);
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--color-border-slate);
}
.preview-field {
  margin-bottom: 12px;
}
.preview-field:last-child {
  margin-bottom: 0;
}
.preview-field label {
  display: block;
  font-size: 12px;
  color: var(--color-mute);
  margin-bottom: 4px;
}
.preview-value {
  font-size: 13px;
  color: var(--color-text-dark);
  line-height: 1.5;
}
.preview-value.pre {
  white-space: pre-wrap;
  max-height: 200px;
  overflow-y: auto;
  background: var(--color-canvas);
  padding: 8px;
  border-radius: 6px;
  border: 1px solid var(--color-border-slate);
}
.preview-field-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.preview-field-grid .preview-field {
  margin-bottom: 0;
}
.preview-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  margin: 0 4px 4px 0;
  background: var(--color-info-bg);
  border: 1px solid var(--color-border-blue);
  border-radius: 100px;
  font-size: 12px;
  color: #1e40af;
}
.preview-tag.danger {
  background: var(--color-error-bg);
  border-color: #fecaca;
  color: #dc2626;
}
.preview-tag.mcp {
  background: var(--color-purple-bg);
  border-color: #e9d5ff;
  color: #7c3aed;
}
.preview-tag.subagent {
  background: var(--color-warn-bg);
  border-color: #fcd34d;
  color: #b45309;
}
.preview-tag-type {
  font-size: 10px;
  padding: 1px 6px;
  background: rgba(0, 0, 0, 0.06);
  border-radius: 100px;
  color: var(--color-mute);
}
.preview-empty {
  font-size: 12px;
  color: var(--color-mute);
  font-style: italic;
}
.preview-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  background: var(--color-canvas);
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--color-border-slate);
}
.preview-table th,
.preview-table td {
  padding: 8px 12px;
  text-align: left;
  border-bottom: 1px solid var(--color-border-slate);
}
.preview-table th {
  background: var(--color-canvas-soft);
  font-weight: 500;
  color: var(--color-body);
}
.preview-table td {
  color: var(--color-text-dark);
}
.preview-table code {
  background: var(--color-canvas-soft);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  color: var(--color-ink);
}
.prompt-var-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.6;
}
.prompt-var-tip code {
  font-size: 11px;
  background: var(--color-canvas-soft-2);
  padding: 1px 4px;
  border-radius: 4px;
}
.prompt-insert-vars {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.insert-label {
  font-size: 12px;
  color: var(--color-mute);
}
.var-insert-btn {
  padding: 2px 10px;
  font-size: 12px;
  border: 1px solid var(--color-purple-border);
  background: var(--color-info-bg);
  color: #4338ca;
  border-radius: 100px;
  cursor: pointer;
}
.var-insert-btn:hover {
  background: var(--color-purple-bg);
}
.preview-toggle-btn {
  border-color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  color: var(--color-body);
}
.preview-toggle-btn.active {
  border-color: var(--color-link);
  background: var(--color-info-bg);
  color: var(--color-link);
}
.prompt-preview-box {
  margin-top: 10px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  overflow: hidden;
}
.prompt-preview-label {
  padding: 6px 12px;
  background: var(--color-canvas-soft);
  font-size: 12px;
  color: var(--color-mute);
  border-bottom: 1px solid var(--color-hairline);
}
.prompt-preview-content {
  padding: 12px;
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow-y: auto;
}
.prompt-preview-content :deep(.preview-replaced) {
  background: var(--color-success-bg);
  color: #166534;
  padding: 1px 4px;
  border-radius: 3px;
}
.prompt-preview-content :deep(.preview-empty),
.prompt-preview-content :deep(.preview-unset) {
  background: var(--color-warn-bg-deep);
  color: #854d0e;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}
/* 子配置卡片（变量、模型配置等） */
.sub-config-card {
  margin: 8px 0 16px;
  padding: 14px 16px;
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  border-radius: 10px;
}
.sub-config-card--model {
  margin-top: 16px;
}
.sub-config-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.sub-config-card-title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}
.sub-config-card-desc {
  margin: 0;
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.5;
}
.sub-config-card-desc code {
  font-size: 11px;
  background: var(--color-canvas);
  padding: 1px 5px;
  border-radius: 4px;
  border: 1px solid var(--color-hairline);
}
.sub-config-empty {
  padding: 16px;
  text-align: center;
  font-size: 13px;
  color: var(--color-mute);
  background: var(--color-canvas);
  border: 1px dashed #e4e4e7;
  border-radius: 8px;
}
.btn-add-inline {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas);
  border-radius: 6px;
  cursor: pointer;
  color: var(--color-body);
  transition: border-color 0.15s, color 0.15s;
}
.btn-add-inline:hover {
  border-color: var(--color-link);
  color: var(--color-link);
}
.btn-add-inline--block {
  width: 100%;
  justify-content: center;
  margin-bottom: 8px;
  border-style: dashed;
}
.inline-field-block {
  width: 100%;
}
.inline-field-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.list-table-head {
  display: grid;
  gap: 8px;
  padding: 6px 10px;
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-mute);
  text-transform: uppercase;
  letter-spacing: 0.02em;
  background: var(--color-canvas);
  border-radius: 6px;
  border: 1px solid var(--color-hairline);
}
.list-table-head--4col {
  grid-template-columns: 1fr 1fr 1fr 36px;
}
.list-table-row {
  display: grid;
  gap: 8px;
  align-items: center;
  padding: 6px 8px;
  margin-bottom: 6px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
}
.list-table-row:last-child {
  margin-bottom: 0;
}
.list-table-row--4col {
  grid-template-columns: 1fr 1fr 1fr 36px;
}
.list-table-row--2col {
  grid-template-columns: 1fr 36px;
}
.list-table-head .col-action,
.list-table-row--4col > :last-child,
.list-table-row--2col > :last-child {
  justify-self: center;
}
.config-list-scroll {
  max-height: 200px;
  overflow-y: auto;
  padding: 2px 12px 2px 0;
  margin-right: 0;
  scrollbar-gutter: stable;
}
.config-list-scroll--compact {
  max-height: 132px;
}
.config-list-scroll--two-rows {
  max-height: 92px;
}
.config-list-scroll::-webkit-scrollbar {
  width: 6px;
}
.config-list-scroll::-webkit-scrollbar-thumb {
  background: #d4d4d8;
  border-radius: 3px;
}
.config-list-scroll::-webkit-scrollbar-thumb:hover {
  background: #a1a1aa;
}
.model-config-form :deep(.ant-form-item) {
  margin-bottom: 16px;
}
.btn-outline {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: var(--color-canvas);
  color: var(--color-ink);
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}
.btn-outline:hover {
  border-color: var(--color-link);
  color: var(--color-link);
}
.btn-icon-sm {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-mute);
  font-size: 12px;
}
.btn-icon-sm:hover {
  background: var(--color-canvas-soft-2);
}
.btn-icon-sm.danger:hover {
  color: var(--color-error);
  background: var(--color-error-soft);
}
.prompt-wrapper {
  position: relative;
}
.btn-ai-icon {
  position: absolute;
  right: 8px;
  bottom: 8px;
  width: 28px;
  height: 28px;
  border: none;
  background: rgba(0, 112, 243, 0.1);
  border-radius: 6px;
  color: var(--color-link);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: all 0.15s;
  z-index: 1;
}
.btn-ai-icon:hover:not(:disabled) {
  background: rgba(0, 112, 243, 0.2);
}
.btn-ai-icon:disabled {
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  cursor: not-allowed;
}
.id-field {
  display: flex;
  align-items: center;
  gap: 12px;
}
.id-value {
  font-size: 14px;
  color: var(--color-ink);
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  background: var(--color-canvas-soft-2);
  padding: 4px 12px;
  border-radius: 4px;
}
.id-hint {
  font-size: 12px;
  color: var(--color-mute);
}
.btn-ai-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  font-size: 12px;
  color: var(--color-link);
  cursor: pointer;
  transition: all 0.15s;
}
.btn-ai-sm:hover:not(:disabled) {
  border-color: var(--color-link);
  background: var(--color-info-bg);
}
.btn-ai-sm:disabled {
  color: var(--color-mute);
  border-color: var(--color-hairline);
  cursor: not-allowed;
}
.config-label-wrap {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 4px;
  line-height: 1.3;
}
.config-label-wrap .config-key {
  flex-basis: 100%;
}
.config-hint-icon {
  margin-left: 4px;
  color: var(--color-mute);
  font-size: 13px;
  cursor: help;
}
.config-label {
  font-size: 14px;
}
.config-key {
  font-size: 11px;
  color: var(--color-mute);
  font-weight: normal;
}
.btn-primary:hover:not(:disabled) {
  background: #27272a;
}
.btn-primary:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: stretch;
}
.content-grid-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  min-height: 0;
}
.content-grid-side {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.sub-config-card-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.sub-config-card-title-row .sub-config-card-title {
  margin-bottom: 0;
}
.capability-collapse-trigger {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  padding: 2px;
  border-radius: 3px;
  transition: background 0.15s;
}
.capability-collapse-trigger:hover {
  background: var(--color-canvas-soft-2);
}
.capability-collapse-icon {
  font-size: 10px;
  color: var(--color-mute);
  transition: transform 0.2s;
}
.capability-grid {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.capability-grid-primary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.capability-grid-sub {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed var(--color-hairline);
}
.capability-sub-header {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-mute);
  margin-bottom: 10px;
}
.capability-grid-sub-items {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.preview-field-grid--capabilities {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-hairline);
}
.preview-default-tag {
  margin-left: 6px;
  font-size: 11px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 1px 6px;
  border-radius: 4px;
}
.capability-option-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 10px 12px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  transition: border-color 0.2s, opacity 0.2s;
}
.capability-option-item:hover:not(.is-disabled) {
  border-color: var(--color-hairline);
}
.capability-option-item.is-disabled {
  opacity: 0.55;
}
.capability-option-item .capability-option-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
.capability-option-label {
  font-size: 13px;
  color: var(--color-ink);
  font-weight: 500;
  white-space: nowrap;
}
.capability-option-status {
  font-size: 12px;
  color: var(--color-mute);
  min-width: 20px;
}
.capability-number-input {
  width: 64px !important;
}
.capability-text-input {
  width: 100px;
}
.field-control-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.field-control-row--slider {
  gap: 12px;
}
.field-control-grow {
  flex: 1;
  min-width: 0;
}
.field-hint-icon {
  font-size: 14px;
  color: var(--color-mute);
  cursor: help;
  flex-shrink: 0;
}
.field-hint-icon:hover {
  color: #1890ff;
}
.switch-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.panel-stretch {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: calc(100vh - 200px);
}
.panel--config-unified.panel-stretch {
  height: 100%;
}
.panel--basic .panel-body,
.config-tab-pane-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  max-height: calc(100vh - 280px);
}
.panel--config-unified {
  padding: 0;
  overflow: hidden;
}
.panel-header--config {
  padding: 20px 20px 0;
  margin-bottom: 0;
}
.config-panel-tabs {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}
.config-panel-tabs :deep(.ant-tabs-nav) {
  margin: 0;
  padding: 0px 20px 0;
}
.config-panel-tabs :deep(.ant-tabs-nav)::before {
  border-bottom-color: #f0f0f0;
}
.config-panel-tabs :deep(.ant-tabs-extra-content) {
  display: flex;
  align-items: center;
  gap: 1px;
}
.config-panel-tabs :deep(.ant-tabs-content-holder) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.config-panel-tabs :deep(.ant-tabs-content),
.config-panel-tabs :deep(.ant-tabs-tabpane) {
  height: 100%;
}
.config-tab-pane-body {
  padding: 16px 20px 16px 12px;
}
.config-tab-pane-body--chat {
  padding-top: 16px;
}
.panel--basic {
  max-height: none;
}
.panel--basic .panel-body {
  padding: 8px 20px 12px 8px;
}
.panel--workflow {
  max-height: calc(100vh - 96px);
}
.panel--workflow .panel-body {
  max-height: calc(100vh - 176px);
}
.panel {
  display: flex;
  flex-direction: column;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 20px;
  overflow: visible;
}
.panel--workflow {
  overflow: hidden;
}
.panel-body {
  flex: 1;
  min-height: auto;
  overflow-y: visible;
  padding: 8px 20px 12px 8px;
  margin-right: 0;
}
.panel-body::-webkit-scrollbar {
  width: 8px;
}
.panel-body--chat {
  max-height: none;
  overflow-y: visible;
}
.panel-form {
  padding-bottom: 16px;
}
.panel-body::-webkit-scrollbar-thumb {
  background: #d4d4d8;
  border-radius: 4px;
  border: 2px solid transparent;
  background-clip: padding-box;
}
.panel-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.panel.full-width {
  grid-column: 1 / -1;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}
.panel-header--stack {
  align-items: flex-start;
}
.panel-subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.5;
  font-weight: normal;
}
.panel-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
}
.non-version-badge {
  font-size: 11px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  padding: 2px 8px;
  white-space: nowrap;
}
.panel-tip {
  font-size: 12px;
  color: var(--color-mute);
}

.tool-options-bar {
  display: flex;
  gap: 24px;
  padding: 12px 16px;
  background: var(--color-canvas-soft);
  border-radius: 8px;
  margin-bottom: 16px;
}
.tool-option-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tool-option-label {
  font-size: 13px;
  color: var(--color-body);
  font-weight: 500;
}
.tool-option-value {
  font-size: 13px;
  color: var(--color-ink);
}
.tool-option-help {
  font-size: 14px;
  color: var(--color-mute);
  cursor: help;
  transition: color 0.2s;
}
.tool-option-help:hover {
  color: #1890ff;
}

.param-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.param-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-ink);
  min-width: 40px;
  text-align: right;
}
.param-hint {
  font-size: 12px;
  color: var(--color-mute);
  margin-top: 4px;
}

.knowledge-bind {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.selected-knowledge {
  display: flex;
  flex-direction: column;
}
.selected-knowledge-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
  min-height: 40px;
  padding: 12px;
  background: var(--color-canvas-soft);
  border-radius: 8px;
}
.knowledge-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: var(--color-info-bg);
  border: 1px solid var(--color-border-blue);
  border-radius: 100px;
  font-size: 13px;
  color: #1e40af;
}
.tag-avatar {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}
.binding-deleted-alert {
  margin-top: 16px;
}
.binding-alert-message-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.binding-deleted-detail-row {
  margin-top: 4px;
}
.binding-deleted-detail-line {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-body);
}
.binding-deleted-detail-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--color-mute);
  text-align: right;
}
.btn-remove-deleted-bindings {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}
.btn-remove-deleted-bindings:hover {
  box-shadow: 0 2px 6px rgba(239, 68, 68, 0.25);
}
.binding-tag--deleted,
.preview-tag--deleted {
  background: var(--color-error-bg);
  border-color: #fecaca;
  color: #991b1b;
}
.binding-deleted-tag {
  font-size: 11px;
  line-height: 1;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--color-error-bg);
  color: #b91c1c;
  font-weight: 500;
}
.binding-tag--disabled,
.preview-tag--disabled {
  background: var(--color-warn-bg);
  border-color: #fde68a;
  color: #92400e;
}
.binding-disabled-tag {
  font-size: 11px;
  line-height: 1;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--color-warn-bg-deep);
  color: #b45309;
  font-weight: 500;
}
.tag-remove {
  background: none;
  border: none;
  color: #60a5fa;
  cursor: pointer;
  padding: 0;
  font-size: 12px;
  display: flex;
  align-items: center;
}
.tag-remove:hover {
  color: #ef4444;
}
.knowledge-list {
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  overflow: hidden;
}
.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--color-canvas-soft);
  border-bottom: 1px solid var(--color-hairline);
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-dark);
}
.list-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.list-body {
  max-height: 300px;
  overflow-y: auto;
}
.knowledge-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.15s;
}
.knowledge-item:hover {
  background: var(--color-canvas-soft);
}
.knowledge-item.selected {
  background: var(--color-info-bg);
}
.item-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, #007cf0, #00dfd8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  position: relative;
  flex-shrink: 0;
  position: relative;
}
.knowledge-icon {
  background: linear-gradient(135deg, #8b5cf6, #6366f1);
}
.kb-type-mini-tag {
  font-size: 10px;
  line-height: 16px;
  padding: 0 4px;
  flex-shrink: 0;
}
.kb-type-icon {
  font-size: 13px;
  flex-shrink: 0;
  cursor: help;
}
.kb-type-icon.pg { color: #3b82f6; }
.kb-type-icon.milvus { color: #8b5cf6; }
.subagent-icon {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}
.skill-icon {
  background: linear-gradient(135deg, #ec4899, #db2777) !important;
}
.builtin-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  font-size: 10px;
  padding: 1px 4px;
  background: #3b82f6;
  color: #fff;
  border-radius: 4px;
  z-index: 1;
}
.knowledge-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  font-size: 10px;
  padding: 1px 4px;
  background: #7c3aed;
  color: #fff;
  border-radius: 4px;
  z-index: 1;
}
.binding-inline-badge {
  display: inline-flex;
  align-items: center;
  font-size: 10px;
  padding: 0 5px;
  margin-right: 4px;
  background: #3b82f6;
  color: #fff;
  border-radius: 4px;
  vertical-align: middle;
  position: static;
}
.tag-name-wrap {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.tag-name-wrap .binding-inline-badge {
  margin-right: 0;
}
.item-info {
  flex: 1;
  min-width: 0;
}
.item-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-ink);
  display: flex;
  align-items: center;
  gap: 6px;
}
.item-desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-mute);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-check {
  color: var(--color-link);
  font-size: 16px;
}
.empty-tip {
  text-align: center;
  padding: 24px;
  color: var(--color-mute);
  font-size: 13px;
}
.kb-tool-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  font-size: 12px;
  color: var(--color-mute);
  white-space: nowrap;
}
.kb-tool-hint a {
  color: #7c3aed;
  font-weight: 500;
  cursor: pointer;
}
.kb-tool-hint a:hover {
  text-decoration: underline;
}

/* 头像上传 */
.avatar-upload {
  display: flex;
  align-items: center;
  gap: 16px;
}
.avatar-preview {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, #7928ca, #ff0080);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  position: relative;
  cursor: pointer;
  overflow: hidden;
  flex-shrink: 0;
}
.avatar-preview.has-avatar {
  background: var(--color-canvas-soft-2);
}
.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-placeholder {
  font-size: 28px;
  font-weight: 700;
}
.avatar-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
  color: #fff;
  font-size: 20px;
}
.avatar-preview:hover .avatar-overlay {
  opacity: 1;
}
.avatar-tip {
  font-size: 12px;
  color: var(--color-mute);
}

/* 工具绑定样式 */
.tool-tag {
  background: var(--color-success-bg);
  border-color: #bbf7d0;
  color: #166534;
}
.tool-type-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 100px;
  background: rgba(0, 0, 0, 0.06);
  color: var(--color-mute);
}
.tool-knowledge-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 100px;
  background: var(--color-purple-bg);
  color: #7c3aed;
  border: 1px solid var(--color-warn-bg-deep);
  font-weight: 500;
}
.tool-icon-bg {
  background: linear-gradient(135deg, #10b981, #059669) !important;
}
.mcp-tag {
  background: var(--color-purple-bg);
  border-color: #e9d5ff;
  color: #7c3aed;
}
.mcp-icon-bg {
  background: linear-gradient(135deg, #7c3aed, #a855f7) !important;
}
.type-filter-bar {
  display: flex;
  gap: 4px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--color-hairline);
}
.type-filter-btn {
  padding: 2px 10px;
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  background: var(--color-canvas);
  font-size: 12px;
  color: var(--color-mute);
  cursor: pointer;
  transition: all 0.15s;
}
.type-filter-btn:hover {
  border-color: var(--color-link);
  color: var(--color-link);
}
.type-filter-btn.active {
  background: #0070f3;
  border-color: var(--color-link);
  color: #fff;
}
.agent-tabs {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 0 20px 20px;
}

.sensitive-word-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sensitive-word-list .list-table-row--2col {
  margin-bottom: 0;
}
.workflow-entry-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 24px;
  padding: 12px 16px;
  background: var(--color-info-bg);
  border: 1px solid var(--color-border-blue);
  border-radius: 8px;
  font-size: 13px;
  color: #1d4ed8;
}
.skill-placeholder {
  padding: 48px 0;
  text-align: center;
}
.panel--workflow-flat .panel-body {
  padding: 0;
}
.binding-tabs {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 16px 20px 20px;
  margin-top: 16px;
}

/* 清空按钮样式 */
.selected-header {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.selected-label {
  font-size: 13px;
  color: var(--color-mute);
  font-weight: 500;
}
.btn-clear {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 4px;
  font-size: 12px;
  color: var(--color-mute);
  cursor: pointer;
  transition: all 0.15s;
}
.btn-clear:hover {
  border-color: #ef4444;
  color: #ef4444;
  background: var(--color-error-bg);
}

/* SubAgent 绑定 */
.selected-subagents-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  min-height: 40px;
  padding: 12px;
  background: var(--color-canvas-soft);
  border-radius: 8px;
}
.subagent-tag {
  background: var(--color-warn-bg);
  border-color: #fcd34d;
  color: #b45309;
}
.skill-tag {
  background: var(--color-purple-bg);
  border-color: #f9a8d4;
  color: #be185d;
}
.subagent-list {
  flex: 1;
  min-width: 0;
}
.subagent-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.15s;
}
.subagent-item:hover {
  background: var(--color-canvas-soft);
}
.subagent-item.selected {
  background: var(--color-info-bg);
}
.subagent-item .builtin-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  font-size: 10px;
  padding: 1px 4px;
  background: #0070f3;
  color: #fff;
  border-radius: 4px;
  z-index: 1;
}
.subagent-item .item-tools {
  margin-top: 4px;
  font-size: 11px;
  color: var(--color-link);
}

/* 工作流配置样式 */
.workflow-entry-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.workflow-guide {
  background: var(--color-canvas-soft);
  border-radius: 8px;
  padding: 12px 16px;
}

.guide-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-dark);
  margin-bottom: 8px;
}

.guide-list {
  margin: 0;
  padding-left: 16px;
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.8;
}

.workflow-stats {
  background: var(--color-canvas-soft);
  border-radius: 8px;
  padding: 12px 16px;
}

.stats-header {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-dark);
  margin-bottom: 10px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  background: var(--color-canvas);
  border-radius: 6px;
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-ink);
}

.stat-value.llm { color: #7c3aed; }
.stat-value.condition { color: #d97706; }
.stat-value.tool { color: #059669; }
.stat-value.retrieval { color: #4f46e5; }
.stat-value.edges { color: var(--color-mute); }

.stat-label {
  font-size: 11px;
  color: var(--color-mute);
  margin-top: 2px;
}

.workflow-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--color-canvas-soft);
  border-radius: 8px;
}

.workflow-status .status-text {
  font-size: 14px;
  color: var(--color-body);
}

/* 版本帮助图标 */
.version-help-icon {
  margin-left: 8px;
  color: var(--color-mute);
  font-size: 14px;
  cursor: pointer;
  transition: color 0.2s;
}
.version-help-icon:hover { color: #1890ff; }

/* 版本控制说明弹窗 */
.version-help-content p {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--color-body);
  line-height: 1.6;
}
.version-help-table {
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 12px;
}
.version-help-row {
  display: flex;
  border-bottom: 1px solid var(--color-hairline);
}
.version-help-row:last-child { border-bottom: none; }
.version-help-row.header {
  background: var(--color-canvas-soft);
  font-weight: 600;
  font-size: 12px;
  color: var(--color-text-dark);
}
.version-help-row span {
  flex: 1;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--color-body);
}
.version-help-row.header span { color: var(--color-text-dark); }
.version-help-note {
  margin: 0;
  font-size: 12px !important;
  color: #9ca3af !important;
  background: var(--color-canvas-soft);
  padding: 8px 12px;
  border-radius: 6px;
  border-left: 3px solid #1890ff;
}

/* 保存/发布同步遮罩 */
.sync-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.sync-overlay-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.sync-overlay-text {
  font-size: 14px;
  color: var(--color-body);
  font-weight: 500;
}

/* 卡片详情弹窗 */
.card-detail-popover {
  min-width: 240px;
}
.card-detail-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.card-detail-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
}
.card-detail-desc {
  font-size: 13px;
  color: var(--color-body);
  line-height: 1.6;
  margin-bottom: 12px;
}
.card-detail-meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 6px;
}
.card-detail-label {
  color: var(--color-mute);
  flex-shrink: 0;
}
.card-detail-value {
  color: var(--color-ink);
  word-break: break-all;
}
.card-detail-value.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: #be185d;
  background: var(--color-purple-bg);
  padding: 1px 6px;
  border-radius: 4px;
}
.card-detail-toggle {
  display: block;
  width: 100%;
  margin-top: 12px;
  padding: 6px 0;
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  background: var(--color-canvas);
  color: var(--color-ink);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.card-detail-toggle:hover {
  border-color: var(--color-ink);
  color: var(--color-ink);
}
.card-detail-toggle.bound {
  background: var(--color-error-bg);
  border-color: #fca5a5;
  color: #dc2626;
}
.card-detail-toggle.bound:hover {
  background: var(--color-error-bg);
}

</style>

<style>
.deleted-binding-modal-tip {
  margin-bottom: 8px;
  font-size: 14px;
  color: var(--color-ink);
}
.deleted-binding-modal-line {
  font-size: 13px;
  line-height: 1.65;
  color: var(--color-body);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
.deleted-binding-modal-foot {
  margin-top: 10px;
  margin-bottom: 0;
  font-size: 12px;
  color: var(--color-mute);
}
</style>
