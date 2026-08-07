<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">系统管理</h2>
    </div>

    <a-tabs v-model:activeKey="activeTab" class="settings-tabs">
      <a-tab-pane key="landing" tab="Landing配置" />
      <a-tab-pane key="users" tab="用户管理" />
      <a-tab-pane key="memory" tab="长期记忆" />
      <a-tab-pane key="token" tab="企业 Token" />
      <a-tab-pane key="apiKey" tab="企业 API Key" />
    </a-tabs>

    <!-- Tab: Landing 管理 -->
    <div v-show="activeTab === 'landing'">
    <a-spin :spinning="landingLoading">
    <div class="panel landing-panel">
      <div class="panel-header">
        <div class="panel-title-wrap">
          <h3>首页内容配置</h3>
          <span class="panel-desc">配置公开 Landing 页的标题、描述、功能展示等内容</span>
        </div>
      </div>
      <div class="panel-body">
        <a-form :label-col="{ span: 4 }" :wrapper-col="{ span: 18 }">
          <a-form-item label="主标题">
            <a-input v-model:value="landing.title" placeholder="智元" :maxlength="30" show-count />
          </a-form-item>
          <a-form-item label="副标题轮播">
            <div class="subtitle-list">
              <div v-for="(sub, idx) in landing.subtitles" :key="(sub || 'subtitle') + '-' + idx" class="subtitle-row">
                <a-input v-model:value="landing.subtitles[idx]" placeholder="请输入副标题（不超过 30 字）" style="flex:1" :maxlength="30" show-count />
                <button class="btn-icon-danger" @click="landing.subtitles.splice(idx, 1)">
                  <DeleteOutlined />
                </button>
              </div>
              <button class="btn-add" @click="landing.subtitles.push('')">
                <PlusOutlined /> 添加副标题
              </button>
            </div>
          </a-form-item>
          <a-form-item label="描述文字">
            <a-textarea v-model:value="landing.description" :rows="3" placeholder="请输入平台介绍（不超过 200 字）" :maxlength="200" show-count />
          </a-form-item>
          <a-form-item label="GitHub 地址">
            <a-input v-model:value="landing.github" placeholder="请输入 GitHub 地址（不超过 200 字）" :maxlength="200" show-count />
          </a-form-item>
          <a-form-item label="版权信息">
            <a-input v-model:value="landing.copyright" placeholder="© 2026 智元" :maxlength="100" show-count />
          </a-form-item>
          <a-form-item label="功能展示">
            <div class="features-toggle-bar">
              <span class="features-count">共 {{ landing.features.length }} 项</span>
              <button class="btn-text-toggle" @click="featuresExpanded = !featuresExpanded">
                {{ featuresExpanded ? '收起' : '展开' }}
                <RightOutlined class="features-toggle-icon" :class="{ expanded: featuresExpanded }" />
              </button>
            </div>
            <div v-show="featuresExpanded" class="feature-list">
              <div v-for="(feat, idx) in landing.features" :key="(feat.title || feat.icon || 'feature') + '-' + idx" class="feature-card">
                <div class="feature-card-header">
                  <span class="feature-index">#{{ idx + 1 }}</span>
                  <div class="feature-card-actions">
                    <a-tooltip title="上移">
                      <button class="btn-icon-move" :disabled="idx === 0" @click="moveFeature(idx, -1)">
                        <UpOutlined />
                      </button>
                    </a-tooltip>
                    <a-tooltip title="下移">
                      <button class="btn-icon-move" :disabled="idx === landing.features.length - 1" @click="moveFeature(idx, 1)">
                        <DownOutlined />
                      </button>
                    </a-tooltip>
                    <a-tooltip title="删除">
                      <button class="btn-icon-danger" @click="landing.features.splice(idx, 1)">
                        <DeleteOutlined />
                      </button>
                    </a-tooltip>
                  </div>
                </div>
                <a-form-item label="图标" :label-col="{ span: 3 }" :wrapper-col="{ span: 20 }">
                  <a-select
                    v-model:value="feat.icon"
                    placeholder="选择图标"
                  >
                    <a-select-option v-for="ic in iconOptions" :key="ic.value" :value="ic.value">
                      <div class="icon-grid-option">
                        <component :is="ic.icon" />
                        <span>{{ ic.label }}</span>
                      </div>
                    </a-select-option>
                  </a-select>
                </a-form-item>
                <a-form-item label="标题" :label-col="{ span: 3 }" :wrapper-col="{ span: 20 }">
                  <a-input v-model:value="feat.title" placeholder="请输入功能名称（不超过 20 字）" :maxlength="20" show-count />
                </a-form-item>
                <a-form-item label="描述" :label-col="{ span: 3 }" :wrapper-col="{ span: 20 }">
                  <a-textarea v-model:value="feat.desc" :rows="2" placeholder="请输入功能描述（不超过 40 字）" :maxlength="40" show-count />
                </a-form-item>
              </div>
              <button class="btn-add" @click="landing.features.push({ icon: '', title: '', desc: '' })">
                <PlusOutlined /> 添加功能
              </button>
            </div>
          </a-form-item>
          <a-form-item :wrapper-col="{ offset: 4, span: 18 }">
            <button class="btn-primary" :disabled="landingSaving" @click="saveLandingConfig">
              <SaveOutlined /> {{ landingSaving ? '保存中...' : '保存 Landing 配置' }}
            </button>
          </a-form-item>
        </a-form>
      </div>
    </div>
    </a-spin>
    </div>

    <!-- Tab 3: 用户管理 -->
    <div v-show="activeTab === 'users'">
      <UserManage />
    </div>

    <!-- Tab: 企业长期记忆默认策略 -->
    <div v-show="activeTab === 'memory'">
      <a-spin :spinning="memoryPolicyLoading">
        <div class="panel" style="grid-column: 1 / -1;">
          <div class="panel-header split-header">
            <div class="panel-title-wrap">
              <h3>企业长期记忆默认策略</h3>
              <span class="panel-desc">控制台调试与开放 API（未单独覆盖的 Key）均跟随此默认；各 Key 可在「企业 API Key」中分策略覆盖</span>
            </div>
            <button class="btn-primary" :disabled="memoryPolicySaving" @click="saveMemoryPolicy">
              <SaveOutlined /> {{ memoryPolicySaving ? '保存中...' : '保存配置' }}
            </button>
          </div>
          <div class="panel-body">
            <div class="memory-settings">
              <label class="setting-row">
                <span class="setting-text">
                  <strong>启用长期记忆</strong>
                  <small>关闭后控制台调试与开放 API 均不注入记忆（除非 Key 单独覆盖开启）</small>
                </span>
                <a-switch v-model:checked="memoryPolicyForm.enabled" />
              </label>
              <label class="setting-row">
                <span class="setting-text">
                  <strong>自动抽取记忆</strong>
                  <small>回复结束后尝试保存明确偏好 / 「请记住」类内容</small>
                </span>
                <a-switch v-model:checked="memoryPolicyForm.autoExtract" :disabled="!memoryPolicyForm.enabled" />
              </label>
              <label class="setting-row">
                <span class="setting-text">
                  <strong>每轮注入数量</strong>
                  <small>控制进入 Prompt 的记忆条数</small>
                </span>
                <a-input-number v-model:value="memoryPolicyForm.injectLimit" :min="1" :max="15" :disabled="!memoryPolicyForm.enabled" />
              </label>
              <label class="setting-row">
                <span class="setting-text">
                  <strong>记忆作用域</strong>
                  <small>跨 Agent 共享，或按 Agent 隔离</small>
                </span>
                <a-select v-model:value="memoryPolicyForm.scope" :disabled="!memoryPolicyForm.enabled" class="setting-select">
                  <a-select-option value="user">跨 Agent 生效</a-select-option>
                  <a-select-option value="agent">按 Agent 隔离</a-select-option>
                </a-select>
              </label>
            </div>
          </div>
        </div>
      </a-spin>
    </div>

    <!-- Tab 4: Token 管理 -->
    <div v-show="activeTab === 'token'">
    <a-spin :spinning="tokenLoading">
    <div class="content-grid">
      <!-- 全局统计大屏 -->
      <div class="panel token-stats-panel">
        <div class="panel-header">
          <h3>企业今日 Token 消耗</h3>
          <span class="panel-desc panel-desc-right">{{ tokenStats.date }}</span>
          <button class="btn-icon-refresh" @click="loadTokenStats" :disabled="tokenStatsLoading">
            <SyncOutlined :spin="tokenStatsLoading" />
          </button>
        </div>
        <div class="panel-body">
          <div class="token-stat-cards">
            <div class="token-stat-card">
              <div class="token-stat-label">全局已用</div>
              <div class="token-stat-value">{{ formatToken(tokenStats.globalUsed) }}</div>
              <div class="token-stat-sub">/ {{ formatToken(tokenStats.globalLimit) }}</div>
              <a-progress
                :percent="tokenStats.globalLimit ? Math.min(100, (tokenStats.globalUsed / tokenStats.globalLimit * 100)) : 0"
                :stroke-color="tokenStats.globalUsed / tokenStats.globalLimit > 0.8 ? '#ef4444' : '#10b981'"
                :show-info="false"
                size="small"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- 限额配置 -->
      <div class="panel token-config-panel">
        <div class="panel-header">
          <div class="panel-title-wrap">
            <h3>企业限额配置</h3>
            <span class="panel-desc">全局与建设者调试日限额；开放 API 另受各 Key 日配额约束</span>
          </div>
        </div>
        <div class="panel-body">
          <a-form :label-col="{ span: 8 }">
            <a-form-item label="单次调用上限">
              <a-input-number v-model:value="tokenConfig.singleCallLimit" :min="1000" :step="1000" style="width: 100%" addon-after="tokens" />
            </a-form-item>
            <a-form-item label="用户日限额">
              <a-input-number v-model:value="tokenConfig.userDailyLimit" :min="10000" :step="100000" style="width: 100%" addon-after="tokens" />
            </a-form-item>
            <a-form-item label="全局日限额">
              <a-input-number v-model:value="tokenConfig.globalDailyLimit" :min="100000" :step="1000000" style="width: 100%" addon-after="tokens" />
            </a-form-item>
            <a-form-item :wrapper-col="{ offset: 8 }">
              <button class="btn-primary" :disabled="tokenSaving" @click="saveTokenConfig">
                <SaveOutlined /> {{ tokenSaving ? '保存中...' : '保存配置' }}
              </button>
            </a-form-item>
          </a-form>
        </div>
      </div>
    </div>

    <!-- 用户消耗排行 -->
    <div class="panel token-ranking-panel">
      <div class="panel-header">
        <h3>建设者调试 Token 排行</h3>
        <a-radio-group
          v-model:value="rankingRange"
          button-style="solid"
          size="small"
          class="ranking-range-switch"
          @change="loadTokenRanking"
        >
          <a-radio-button value="today">今日</a-radio-button>
          <a-radio-button value="7d">近 7 天</a-radio-button>
          <a-radio-button value="14d">近 2 周</a-radio-button>
          <a-radio-button value="30d">近 1 个月</a-radio-button>
        </a-radio-group>
        <span class="panel-desc">{{ rangeLabelMap[rankingRange] }} Top {{ tokenRanking.length }}</span>
        <button class="btn-icon-refresh" @click="loadTokenRanking" :disabled="tokenRankingLoading">
          <SyncOutlined :spin="tokenRankingLoading" />
        </button>
      </div>
      <div class="panel-body">
        <a-table
          :data-source="tokenRanking"
          :columns="rankingColumns"
          :pagination="false"
          size="small"
          :scroll="{ y: 400 }"
        >
          <template #bodyCell="{ column, record, index }">
            <template v-if="column.key === 'rank'">
              <span class="rank-badge" :class="{ 'rank-top': index < 3 }">{{ index + 1 }}</span>
            </template>
            <template v-if="column.key === 'user'">
              <div class="ranking-user">
                <a-avatar :size="28" :src="record.avatar">
                  {{ (record.username || record.userId || '?').charAt(0) }}
                </a-avatar>
                <span class="ranking-username">{{ record.username || '用户 ' + record.userId }}</span>
              </div>
            </template>
            <template v-if="column.key === 'usedTokens'">
              <span class="token-amount">{{ formatToken(record.usedTokens) }}</span>
            </template>
          </template>
        </a-table>
        <div v-if="!tokenRanking.length && !tokenLoading" class="empty-tip">暂无数据</div>
      </div>
    </div>
    </a-spin>
    </div>

    <!-- Tab 5: API Key 管理 -->
    <div v-show="activeTab === 'apiKey'">
    <a-spin :spinning="apiKeyLoading">
    <div class="panel" style="grid-column: 1 / -1;">
      <div class="panel-header">
        <div class="panel-title-wrap">
          <h3>企业 API Key</h3>
        </div>
        <span class="panel-desc">用于外部系统调用智元接口的认证凭证</span>
        <a class="apikey-docs-link" href="/docs" target="_blank" rel="noopener noreferrer">在线文档</a>
        <button class="btn-primary" style="margin-left: auto;" @click="showCreateApiKey">
          <PlusOutlined /> 创建企业 API Key
        </button>
      </div>
      <div class="panel-body">
        <div v-if="apiKeyList.length === 0 && !apiKeyLoading" class="empty-tip">暂无企业 API Key，点击上方按钮创建一个</div>
        <div v-else class="apikey-cards-grid">
          <div v-for="key in apiKeyList" :key="key.id" class="apikey-card">
            <div class="apikey-card-header">
              <div class="apikey-card-info">
                <KeyOutlined style="font-size: 16px; color: var(--color-link);" />
                <span class="apikey-card-name">{{ key.name }}</span>
              </div>
              <code class="apikey-card-prefix">{{ key.keyPrefix }}</code>
            </div>
            <div class="apikey-card-body">
              <div class="apikey-card-row">
                <span class="apikey-card-label">权限</span>
                <a-tag :color="isFullApiKeyPermission(key.permissions) ? 'blue' : 'default'">
                  {{ apiKeyPermissionLabel(key.permissions) }}
                </a-tag>
              </div>
              <div class="apikey-card-row">
                <span class="apikey-card-label">限流</span>
                <span>{{ key.rateLimit || 60 }} 次/分钟</span>
              </div>
              <div class="apikey-card-row">
                <span class="apikey-card-label">Token配额</span>
                <span>{{ formatToken(key.dailyQuota) }} / 日{{ key.usedTokens > 0 ? '（已用 ' + formatToken(key.usedTokens) + '）' : '' }}</span>
              </div>
              <div class="apikey-card-row">
                <span class="apikey-card-label">长期记忆</span>
                <span>{{ memoryConfigSummary(key) }}</span>
              </div>
              <div class="apikey-card-row">
                <span class="apikey-card-label">业务办理页</span>
                <span>{{ bizPageConfigSummary(key) }}</span>
              </div>
              <div class="apikey-card-row" v-if="key.agentIds && key.agentIds.length > 0">
                <span class="apikey-card-label">绑定Agent</span>
                <span>{{ key.agentIds.length }} 个</span>
              </div>
              <div class="apikey-card-row">
                <span class="apikey-card-label">过期时间</span>
                <span>{{ key.expiresAt || '永不过期' }}</span>
              </div>
              <div class="apikey-card-row">
                <span class="apikey-card-label">最近使用</span>
                <span>{{ key.lastUsedAt ? formatTime(key.lastUsedAt) : '-' }}</span>
              </div>
            </div>
            <div class="apikey-card-footer">
              <div class="apikey-card-switch">
                <span class="apikey-card-switch-label">{{ key.isEnabled === 1 ? '已启用' : '已禁用' }}</span>
                <a-switch :checked="key.isEnabled === 1" size="small" @change="handleToggleApiKey(key)" />
              </div>
              <div class="apikey-card-actions">
                <a-button type="text" size="small" @click="openKeyMemoryPolicy(key)">
                  <SettingOutlined /> 记忆策略
                </a-button>
                <a-button type="text" size="small" @click="openKeyBizPagePolicy(key)">
                  <AppstoreOutlined /> 业务页
                </a-button>
                <a-button type="text" size="small" @click="openMemoryDrawer(key)">
                  <DatabaseOutlined /> 记忆治理
                </a-button>
                <a-popconfirm title="确定要删除此企业 API Key 吗？" @confirm="handleDeleteApiKey(key)" ok-text="确定" cancel-text="取消">
                  <a-button type="text" size="small" danger>
                    <DeleteOutlined /> 删除
                  </a-button>
                </a-popconfirm>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    </a-spin>
    </div>

    <a-modal
      v-model:open="keyPolicyVisible"
      :title="keyPolicyTitle"
      :confirmLoading="keyPolicySaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="saveKeyMemoryPolicy"
    >
      <div class="dialog-scroll-body">
        <a-spin :spinning="keyPolicyLoading">
          <a-form :label-col="{ flex: '0 0 120px' }">
            <a-form-item label="跟随企业默认">
              <a-switch v-model:checked="keyPolicyForm.inherit" />
              <div class="form-hint">开启后忽略下方覆盖项，完全使用「长期记忆」Tab 中的企业默认</div>
            </a-form-item>
            <template v-if="!keyPolicyForm.inherit">
              <a-form-item label="启用长期记忆">
                <a-switch v-model:checked="keyPolicyForm.enabled" />
              </a-form-item>
              <a-form-item label="自动抽取记忆">
                <a-switch v-model:checked="keyPolicyForm.autoExtract" :disabled="!keyPolicyForm.enabled" />
              </a-form-item>
              <a-form-item label="每轮注入数量">
                <a-input-number v-model:value="keyPolicyForm.injectLimit" :min="1" :max="15" :disabled="!keyPolicyForm.enabled" style="width: 100%" />
              </a-form-item>
              <a-form-item label="记忆作用域">
                <a-select v-model:value="keyPolicyForm.scope" :disabled="!keyPolicyForm.enabled" style="width: 100%">
                  <a-select-option value="user">跨 Agent 生效</a-select-option>
                  <a-select-option value="agent">按 Agent 隔离</a-select-option>
                </a-select>
              </a-form-item>
            </template>
            <a-form-item v-if="keyPolicyEffective" label="当前生效">
              <div class="effective-policy">
                {{ keyPolicyEffective.enabled ? '已启用' : '已关闭' }}
                · 抽取 {{ keyPolicyEffective.autoExtract ? '开' : '关' }}
                · 注入 {{ keyPolicyEffective.injectLimit }}
                · {{ keyPolicyEffective.scope === 'agent' ? '按 Agent' : '跨 Agent' }}
              </div>
            </a-form-item>
          </a-form>
        </a-spin>
      </div>
    </a-modal>

    <a-modal
      v-model:open="keyBizPageVisible"
      :title="`业务办理页白名单 · ${keyBizPageKey?.name || ''}`"
      :confirmLoading="keyBizPageSaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="saveKeyBizPagePolicy"
      destroy-on-close
    >
      <a-spin :spinning="keyBizPageLoading">
        <a-form :label-col="{ flex: '0 0 120px' }">
          <a-form-item label="全部已启用页">
            <a-switch v-model:checked="keyBizPageForm.inherit" />
            <div class="form-hint">关闭后仅允许下方勾选的 pageType</div>
          </a-form-item>
          <a-form-item v-if="!keyBizPageForm.inherit" label="允许的页面">
            <a-select
              v-model:value="keyBizPageForm.allowedPageTypes"
              mode="multiple"
              style="width: 100%"
              :options="keyBizPageOptions"
              placeholder="选择 pageType"
            />
          </a-form-item>
        </a-form>
      </a-spin>
    </a-modal>

    <a-drawer
      v-model:open="memoryDrawerVisible"
      :title="memoryDrawerTitle"
      :width="720"
      :bodyStyle="{ padding: '16px' }"
      destroy-on-close
    >
      <p class="memory-drawer-desc">
        正式长期记忆按「企业 API Key + externalUserId」隔离。清空会影响该外部用户的跨会话偏好，请谨慎操作。
      </p>
      <a-spin :spinning="memoryUsersLoading">
        <a-empty v-if="!memoryUsers.length && !memoryUsersLoading" description="暂无外部用户记忆" />
        <div v-else class="memory-user-list">
          <div v-for="item in memoryUsers" :key="item.externalUserId" class="memory-user-card">
            <div class="memory-user-head">
              <div>
                <code class="memory-user-id">{{ item.externalUserId }}</code>
                <div class="memory-user-meta">
                  启用 {{ item.activeCount ?? 0 }} / 共 {{ item.totalCount ?? 0 }}
                  <span v-if="item.lastUpdateTime"> · 更新于 {{ formatTime(item.lastUpdateTime) }}</span>
                </div>
              </div>
              <div class="memory-user-actions">
                <a-button size="small" @click="loadMemoryDetails(item.externalUserId)">查看明细</a-button>
                <a-popconfirm
                  title="确定清空该外部用户在本 Key 下的全部记忆？"
                  ok-text="清空"
                  cancel-text="取消"
                  @confirm="handleClearUserMemories(item.externalUserId)"
                >
                  <a-button size="small" danger>清空</a-button>
                </a-popconfirm>
              </div>
            </div>
            <div v-if="selectedMemoryUserId === item.externalUserId" class="memory-detail-panel">
              <a-spin :spinning="memoryDetailsLoading">
                <a-empty v-if="!memoryDetails.length && !memoryDetailsLoading" description="暂无明细" />
                <div v-else class="memory-detail-list">
                  <div v-for="mem in memoryDetails" :key="mem.id" class="memory-detail-item">
                    <div class="memory-detail-main">
                      <a-tag>{{ mem.memoryType || 'memory' }}</a-tag>
                      <a-tag :color="mem.status === 'active' ? 'green' : 'default'">{{ mem.status || '-' }}</a-tag>
                      <span class="memory-detail-content">{{ mem.content }}</span>
                    </div>
                    <a-popconfirm title="确定删除这条记忆？" @confirm="handleDeleteMemory(mem.id)">
                      <a-button type="text" size="small" danger>
                        <DeleteOutlined />
                      </a-button>
                    </a-popconfirm>
                  </div>
                </div>
              </a-spin>
            </div>
          </div>
        </div>
      </a-spin>
    </a-drawer>

    <!-- 创建 API Key 弹窗 -->
    <a-modal
      v-model:open="apiKeyCreateVisible"
      title="创建企业 API Key"
      :maskClosable="false"
      @ok="handleCreateApiKey"
      :confirmLoading="apiKeyCreateLoading"
      ok-text="创建"
      cancel-text="取消"
    >
      <div class="dialog-scroll-body">
      <a-form :label-col="{ flex: '0 0 120px' }">
        <a-form-item label="名称" required>
          <a-input v-model:value="apiKeyForm.name" placeholder="请输入 API Key 名称（例如：生产环境 API，不超过 64 字）" :maxlength="64" show-count />
        </a-form-item>
        <a-form-item label="权限">
          <a-select v-model:value="apiKeyForm.permissions">
            <a-select-option value="chat">仅对话（对话接口）</a-select-option>
            <a-select-option value="full">完全访问（对话 + 开放数据 API）</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="每分钟限流">
          <a-input-number v-model:value="apiKeyForm.rateLimit" :min="1" :max="10000" style="width: 100%" placeholder="60" />
        </a-form-item>
        <a-form-item label="每日Token配额">
          <a-input-number v-model:value="apiKeyForm.dailyQuota" :min="0" :max="100000000" :step="10000" style="width: 100%" placeholder="100000" />
        </a-form-item>
        <a-form-item label="绑定Agent">
          <AgentSelect
            v-model:value="apiKeyForm.agentIds"
            mode="multiple"
            placeholder="不选择则可访问全部Agent"
          />
        </a-form-item>
      </a-form>
      </div>
    </a-modal>

    <!-- 密钥展示弹窗（创建后一次性显示） -->
    <a-modal
      v-model:open="apiKeySecretVisible"
      title="企业 API Key 已创建"
      :maskClosable="false"
      :footer="null"
      width="720px"
    >
      <a-alert type="warning" message="请立即复制密钥，关闭后将无法再次查看完整密钥" show-icon style="margin-bottom: 16px;" />
      <div style="display: flex; gap: 8px; align-items: center;">
        <code class="apikey-secret-value">{{ apiKeyCreatedSecret }}</code>
        <a-button type="primary" @click="copyApiKeySecret" style="flex-shrink: 0;">
          <CopyOutlined /> 复制
        </a-button>
      </div>
    </a-modal>

  </div>

</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  SaveOutlined, PlusOutlined, DeleteOutlined,
  UpOutlined, DownOutlined,
  RobotOutlined, TeamOutlined, ApartmentOutlined, ApiOutlined,
  ToolOutlined, ThunderboltOutlined, ExperimentOutlined, EyeOutlined,
  FormOutlined, DatabaseOutlined, NodeIndexOutlined, BranchesOutlined,
  CloudOutlined, CodeOutlined, FileTextOutlined, RocketOutlined,
  SafetyOutlined, SettingOutlined, SyncOutlined,
  AppstoreOutlined, ControlOutlined, ClusterOutlined, BlockOutlined,
  KeyOutlined, CopyOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getLandingConfig, updateLandingConfig } from '../api/landing'
import { getTokenBudgetConfig, updateTokenBudgetConfig, getTokenBudgetStats, getTokenBudgetRanking } from '../api/tokenBudget'
import { getEnterpriseLongMemoryPolicy, updateEnterpriseLongMemoryPolicy } from '../api/longMemory'
import {
  listApiKeys,
  createApiKey,
  toggleApiKey,
  deleteApiKey,
  getApiKeyMemoryConfig,
  updateApiKeyMemoryConfig,
  listApiKeyMemoryUsers,
  listApiKeyMemories,
  clearApiKeyUserMemories,
  deleteApiKeyMemory,
} from '../api/apiKey'
import {
  listBusinessPages,
  getApiKeyBusinessPageConfig,
  updateApiKeyBusinessPageConfig,
} from '../api/businessPage'
import AgentSelect from '../components/AgentSelect.vue'
import UserManage from './UserManage.vue'
import { copyToClipboard } from '../utils/clipboard'
import { formatTime } from '../utils/format'

const route = useRoute()
const router = useRouter()
const VALID_TABS = ['landing', 'users', 'memory', 'token', 'apiKey']
// 旧链接 ?tab=model 跳转到模型管理
if (route.query.tab === 'model') {
  router.replace({ path: '/app/model-providers', query: { tab: 'defaults' } })
}
// 业务办理页已迁到能力中心
if (route.query.tab === 'bizPage') {
  router.replace({ path: '/app/extensions', query: { tab: 'businessPages' } })
}
const activeTab = ref(VALID_TABS.includes(route.query.tab) ? route.query.tab : 'landing')
const landingLoading = ref(false)
const loadedTabs = new Set()

onMounted(() => {
  loadTabData(activeTab.value)
})

watch(activeTab, (tab) => {
  loadTabData(tab)
  // 同步 URL query 参数
  router.replace({ query: { ...route.query, tab } })
})

async function loadTabData(tab) {
  if (loadedTabs.has(tab)) return
  loadedTabs.add(tab)
  if (tab === 'landing') {
    landingLoading.value = true
    try {
      await loadLandingConfig()
    } finally {
      landingLoading.value = false
    }
  } else if (tab === 'memory') {
    memoryPolicyLoading.value = true
    try {
      await loadMemoryPolicy()
    } finally {
      memoryPolicyLoading.value = false
    }
  } else if (tab === 'token') {
    tokenLoading.value = true
    try {
      await Promise.all([loadTokenConfig(), loadTokenStats(), loadTokenRanking()])
    } finally {
      tokenLoading.value = false
    }
  } else if (tab === 'apiKey') {
    apiKeyLoading.value = true
    try {
      await loadApiKeys()
    } finally {
      apiKeyLoading.value = false
    }
  }
}

// Landing 配置
const featuresExpanded = ref(true)
const landingSaving = ref(false)
const landing = reactive({
  title: '',
  subtitles: [],
  description: '',
  features: [],
  github: '',
  copyright: '',
})

// 图标选项（value 对应 Landing.vue 的 iconMap key）
const iconOptions = [
  { value: 'Agent', label: '智能体', icon: markRaw(RobotOutlined) },
  { value: 'SubAgent', label: '子智能体', icon: markRaw(TeamOutlined) },
  { value: 'Knowledge', label: '知识库', icon: markRaw(DatabaseOutlined) },
  { value: 'Workflow', label: '工作流', icon: markRaw(ApartmentOutlined) },
  { value: 'Mcp', label: 'MCP', icon: markRaw(ApiOutlined) },
  { value: 'Tool', label: '工具', icon: markRaw(ToolOutlined) },
  { value: 'Skill', label: '技能', icon: markRaw(ThunderboltOutlined) },
  { value: 'Prompt', label: 'Prompt', icon: markRaw(FormOutlined) },
  { value: 'Eval', label: '评测', icon: markRaw(ExperimentOutlined) },
  { value: 'Observability', label: '可观测', icon: markRaw(EyeOutlined) },
  { value: 'NodeIndexOutlined', label: '节点', icon: markRaw(NodeIndexOutlined) },
  { value: 'BranchesOutlined', label: '分支', icon: markRaw(BranchesOutlined) },
  { value: 'CloudOutlined', label: '云端', icon: markRaw(CloudOutlined) },
  { value: 'CodeOutlined', label: '代码', icon: markRaw(CodeOutlined) },
  { value: 'FileTextOutlined', label: '文档', icon: markRaw(FileTextOutlined) },
  { value: 'RocketOutlined', label: '部署', icon: markRaw(RocketOutlined) },
  { value: 'SafetyOutlined', label: '安全', icon: markRaw(SafetyOutlined) },
  { value: 'SettingOutlined', label: '配置', icon: markRaw(SettingOutlined) },
  { value: 'SyncOutlined', label: '同步', icon: markRaw(SyncOutlined) },
  { value: 'AppstoreOutlined', label: '应用', icon: markRaw(AppstoreOutlined) },
  { value: 'ControlOutlined', label: '控制', icon: markRaw(ControlOutlined) },
  { value: 'ClusterOutlined', label: '集群', icon: markRaw(ClusterOutlined) },
  { value: 'BlockOutlined', label: '模块', icon: markRaw(BlockOutlined) },
]

function moveFeature(idx, direction) {
  const target = idx + direction
  if (target < 0 || target >= landing.features.length) return
  const arr = landing.features
  const temp = arr[idx]
  arr[idx] = arr[target]
  arr[target] = temp
}

async function loadLandingConfig() {
  try {
    const res = await getLandingConfig()
    const raw = res?.data ?? res
    const cfg = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (cfg) {
      landing.title = cfg.title || ''
      landing.subtitles = cfg.subtitles || []
      landing.description = cfg.description || ''
      landing.features = (cfg.features || []).map(f => ({ ...f }))
      landing.github = cfg.github || ''
      landing.copyright = cfg.copyright || ''
    }
  } catch (e) {
    console.error('[Settings] 加载 Landing 配置失败:', e)
  }
}

async function saveLandingConfig() {
  if (!landing.title.trim()) return message.warning('请输入主标题')
  landingSaving.value = true
  try {
    const payload = {
      title: landing.title,
      subtitles: landing.subtitles.filter(s => s.trim()),
      description: landing.description,
      features: landing.features.filter(f => f.title.trim()),
      github: landing.github,
      copyright: landing.copyright,
    }
    await updateLandingConfig(payload)
    message.success('Landing 配置已保存')
  } catch (e) {
    message.error(e.response?.data?.message || '保存失败')
  } finally {
    landingSaving.value = false
  }
}

// Token 管理
const tokenLoading = ref(false)
const tokenStatsLoading = ref(false)
const tokenRankingLoading = ref(false)
const tokenSaving = ref(false)
const tokenConfig = reactive({ singleCallLimit: 32000, userDailyLimit: 1000000, globalDailyLimit: 10000000 })
const tokenStats = reactive({ globalUsed: 0, globalLimit: 0, date: '' })
const tokenRanking = ref([])
// 排行榜时间范围：今日走 Redis（实时），其他走 DB llm_trace 历史聚合
const rankingRange = ref('today')
const rangeLabelMap = { today: '今日', '7d': '近 7 天', '14d': '近 2 周', '30d': '近 1 个月' }
const rankingColumns = [
  { title: '排名', key: 'rank', width: 80, align: 'center' },
  { title: '用户', key: 'user', width: 200, ellipsis: true },
  { title: '消耗 Token', key: 'usedTokens', width: 120, align: 'right' },
]

async function loadTokenConfig() {
  const res = await getTokenBudgetConfig()
  const data = res.data || {}
  tokenConfig.singleCallLimit = data.singleCallLimit ?? 32000
  tokenConfig.userDailyLimit = data.userDailyLimit ?? 1000000
  tokenConfig.globalDailyLimit = data.globalDailyLimit ?? 10000000
}

async function loadTokenStats() {
  tokenStatsLoading.value = true
  try {
    const res = await getTokenBudgetStats()
    const data = res.data || {}
    tokenStats.globalUsed = data.globalUsed ?? 0
    tokenStats.globalLimit = data.globalLimit ?? 0
    tokenStats.date = data.date ?? ''
  } finally {
    tokenStatsLoading.value = false
  }
}

async function loadTokenRanking() {
  tokenRankingLoading.value = true
  try {
    const res = await getTokenBudgetRanking({ range: rankingRange.value, limit: 20 })
    tokenRanking.value = res.data || []
  } finally {
    tokenRankingLoading.value = false
  }
}

async function saveTokenConfig() {
  tokenSaving.value = true
  try {
    await updateTokenBudgetConfig({
      singleCallLimit: tokenConfig.singleCallLimit,
      userDailyLimit: tokenConfig.userDailyLimit,
      globalDailyLimit: tokenConfig.globalDailyLimit,
    })
    message.success('Token 限额配置已保存')
  } catch (e) {
    message.error(e.response?.data?.message || '保存失败')
  } finally {
    tokenSaving.value = false
  }
}

function formatToken(val) {
  if (val == null) return '0'
  if (val >= 1_000_000) return (val / 1_000_000).toFixed(1) + 'M'
  if (val >= 1_000) return (val / 1_000).toFixed(1) + 'K'
  return String(val)
}

// 企业长期记忆默认策略
const memoryPolicyLoading = ref(false)
const memoryPolicySaving = ref(false)
const memoryPolicyForm = reactive({
  enabled: true,
  autoExtract: true,
  injectLimit: 6,
  scope: 'user',
})

async function loadMemoryPolicy() {
  const res = await getEnterpriseLongMemoryPolicy()
  Object.assign(memoryPolicyForm, {
    enabled: res.data?.enabled !== false,
    autoExtract: res.data?.autoExtract !== false,
    injectLimit: res.data?.injectLimit ?? 6,
    scope: res.data?.scope === 'agent' ? 'agent' : 'user',
  })
}

async function saveMemoryPolicy() {
  memoryPolicySaving.value = true
  try {
    const res = await updateEnterpriseLongMemoryPolicy({ ...memoryPolicyForm })
    Object.assign(memoryPolicyForm, res.data || memoryPolicyForm)
    message.success('企业长期记忆策略已保存')
    // 允许 API Key 页重新拉取卡片摘要
    loadedTabs.delete('apiKey')
  } catch {
    // interceptor handled
  } finally {
    memoryPolicySaving.value = false
  }
}

// API Key 管理
const apiKeyLoading = ref(false)
const apiKeyList = ref([])
const apiKeyCreateVisible = ref(false)
const apiKeyCreateLoading = ref(false)
const apiKeySecretVisible = ref(false)
const apiKeyCreatedSecret = ref('')
const apiKeyForm = reactive({ name: '', permissions: 'chat', rateLimit: 60, dailyQuota: 100000, agentIds: [] })

// API Key 记忆策略覆盖
const keyPolicyVisible = ref(false)
const keyPolicyLoading = ref(false)
const keyPolicySaving = ref(false)
const keyPolicyKey = ref(null)
const keyPolicyEffective = ref(null)
const keyPolicyForm = reactive({
  inherit: true,
  enabled: true,
  autoExtract: true,
  injectLimit: 6,
  scope: 'user',
})

const keyPolicyTitle = computed(() => {
  const name = keyPolicyKey.value?.name || '企业 API Key'
  return `记忆策略 · ${name}`
})

function memoryConfigSummary(key) {
  if (!key?.memoryConfig) return '跟随企业默认'
  try {
    const cfg = typeof key.memoryConfig === 'string' ? JSON.parse(key.memoryConfig) : key.memoryConfig
    if (!cfg || cfg.inherit !== false) return '跟随企业默认'
    const on = cfg.enabled !== false
    const scopeText = cfg.scope === 'agent' ? '按 Agent' : '跨 Agent'
    return `${on ? '自定义·启用' : '自定义·关闭'} · ${scopeText}`
  } catch {
    return '跟随企业默认'
  }
}

async function openKeyMemoryPolicy(key) {
  keyPolicyKey.value = key
  keyPolicyVisible.value = true
  keyPolicyLoading.value = true
  keyPolicyEffective.value = null
  try {
    const res = await getApiKeyMemoryConfig(key.id)
    const data = res.data || {}
    Object.assign(keyPolicyForm, {
      inherit: data.inherit !== false,
      enabled: data.enabled !== false,
      autoExtract: data.autoExtract !== false,
      injectLimit: data.injectLimit ?? 6,
      scope: data.scope === 'agent' ? 'agent' : 'user',
    })
    keyPolicyEffective.value = data.effective || null
  } catch {
    Object.assign(keyPolicyForm, { inherit: true, enabled: true, autoExtract: true, injectLimit: 6, scope: 'user' })
  } finally {
    keyPolicyLoading.value = false
  }
}

async function saveKeyMemoryPolicy() {
  if (!keyPolicyKey.value?.id) return
  keyPolicySaving.value = true
  try {
    const payload = keyPolicyForm.inherit
      ? { inherit: true }
      : {
          inherit: false,
          enabled: keyPolicyForm.enabled,
          autoExtract: keyPolicyForm.autoExtract,
          injectLimit: keyPolicyForm.injectLimit,
          scope: keyPolicyForm.scope,
        }
    const res = await updateApiKeyMemoryConfig(keyPolicyKey.value.id, payload)
    keyPolicyEffective.value = res.data?.effective || null
    message.success('记忆策略已保存')
    keyPolicyVisible.value = false
    await loadApiKeys()
  } catch {
    // interceptor handled
  } finally {
    keyPolicySaving.value = false
  }
}

// —— 企业 API Key · 业务办理页白名单（模板管理已迁到能力中心） ——
const bizPages = ref([])
const keyBizPageVisible = ref(false)
const keyBizPageLoading = ref(false)
const keyBizPageSaving = ref(false)
const keyBizPageKey = ref(null)
const keyBizPageForm = reactive({ inherit: true, allowedPageTypes: [] })
const keyBizPageOptions = ref([])

function bizPageConfigSummary(key) {
  if (!key?.businessPageConfig) return '全部已启用页'
  try {
    const cfg = typeof key.businessPageConfig === 'string' ? JSON.parse(key.businessPageConfig) : key.businessPageConfig
    if (!cfg || cfg.inherit !== false) return '全部已启用页'
    const n = Array.isArray(cfg.allowedPageTypes) ? cfg.allowedPageTypes.length : 0
    return n === 0 ? '已禁止全部' : `白名单 ${n} 个`
  } catch {
    return '全部已启用页'
  }
}

async function loadBizPages() {
  try {
    const res = await listBusinessPages()
    bizPages.value = res.data || []
  } catch {
    bizPages.value = []
  }
}

async function openKeyBizPagePolicy(key) {
  keyBizPageKey.value = key
  keyBizPageVisible.value = true
  keyBizPageLoading.value = true
  try {
    if (!bizPages.value.length) await loadBizPages()
    keyBizPageOptions.value = (bizPages.value || [])
      .filter((p) => p.enabled === 1)
      .map((p) => ({ label: `${p.displayName} (${p.pageType})`, value: p.pageType }))
    const res = await getApiKeyBusinessPageConfig(key.id)
    const data = res.data || {}
    keyBizPageForm.inherit = data.inherit !== false
    keyBizPageForm.allowedPageTypes = Array.isArray(data.allowedPageTypes) ? [...data.allowedPageTypes] : []
  } finally {
    keyBizPageLoading.value = false
  }
}

async function saveKeyBizPagePolicy() {
  if (!keyBizPageKey.value?.id) return
  keyBizPageSaving.value = true
  try {
    await updateApiKeyBusinessPageConfig(keyBizPageKey.value.id, keyBizPageForm.inherit
      ? { inherit: true }
      : { inherit: false, allowedPageTypes: keyBizPageForm.allowedPageTypes })
    message.success('业务页白名单已保存')
    keyBizPageVisible.value = false
    await loadApiKeys()
  } finally {
    keyBizPageSaving.value = false
  }
}

function isFullApiKeyPermission(permissions) {
  return permissions === 'full' || permissions === 'FULL' || permissions === '完全访问'
}

function apiKeyPermissionLabel(permissions) {
  return isFullApiKeyPermission(permissions) ? '完全访问' : '仅对话'
}

async function loadApiKeys() {
  const res = await listApiKeys()
  apiKeyList.value = res.data || []
}

async function showCreateApiKey() {
  apiKeyForm.name = ''
  apiKeyForm.permissions = 'chat'
  apiKeyForm.rateLimit = 60
  apiKeyForm.dailyQuota = 100000
  apiKeyForm.agentIds = []
  apiKeyCreateVisible.value = true
}

async function handleCreateApiKey() {
  if (!apiKeyForm.name.trim()) return message.warning('请输入名称')
  apiKeyCreateLoading.value = true
  try {
    const res = await createApiKey({
      name: apiKeyForm.name.trim(),
      permissions: apiKeyForm.permissions,
      rateLimit: apiKeyForm.rateLimit,
      dailyQuota: apiKeyForm.dailyQuota,
      agentIds: apiKeyForm.agentIds.length > 0 ? apiKeyForm.agentIds : null,
    })
    const data = res.data || {}
    apiKeyCreatedSecret.value = data.secret
    apiKeyCreateVisible.value = false
    apiKeySecretVisible.value = true
    await loadApiKeys()
  } catch {
    // interceptor handled
  } finally {
    apiKeyCreateLoading.value = false
  }
}

async function copyApiKeySecret() {
  await copyToClipboard(apiKeyCreatedSecret.value)
  message.success('已复制到剪贴板')
}

async function handleToggleApiKey(key) {
  try {
    await toggleApiKey(key.id)
    message.success(key.isEnabled === 1 ? '已禁用' : '已启用')
    await loadApiKeys()
  } catch {
    // interceptor handled
  }
}

async function handleDeleteApiKey(key) {
  try {
    await deleteApiKey(key.id)
    message.success('已删除')
    await loadApiKeys()
  } catch {
    // interceptor handled
  }
}

// 企业 API Key 外部用户记忆治理
const memoryDrawerVisible = ref(false)
const memoryDrawerKey = ref(null)
const memoryUsersLoading = ref(false)
const memoryUsers = ref([])
const selectedMemoryUserId = ref('')
const memoryDetailsLoading = ref(false)
const memoryDetails = ref([])

const memoryDrawerTitle = computed(() => {
  const name = memoryDrawerKey.value?.name || '企业 API Key'
  return `长期记忆治理 · ${name}`
})

async function openMemoryDrawer(key) {
  memoryDrawerKey.value = key
  memoryDrawerVisible.value = true
  selectedMemoryUserId.value = ''
  memoryDetails.value = []
  await loadMemoryUsers()
}

async function loadMemoryUsers() {
  if (!memoryDrawerKey.value?.id) return
  memoryUsersLoading.value = true
  try {
    const res = await listApiKeyMemoryUsers(memoryDrawerKey.value.id)
    memoryUsers.value = res.data || []
  } catch {
    memoryUsers.value = []
  } finally {
    memoryUsersLoading.value = false
  }
}

async function loadMemoryDetails(externalUserId) {
  if (!memoryDrawerKey.value?.id || !externalUserId) return
  selectedMemoryUserId.value = externalUserId
  memoryDetailsLoading.value = true
  try {
    const res = await listApiKeyMemories(memoryDrawerKey.value.id, { externalUserId })
    memoryDetails.value = res.data || []
  } catch {
    memoryDetails.value = []
  } finally {
    memoryDetailsLoading.value = false
  }
}

async function handleClearUserMemories(externalUserId) {
  if (!memoryDrawerKey.value?.id) return
  try {
    const res = await clearApiKeyUserMemories(memoryDrawerKey.value.id, externalUserId)
    message.success(`已清空 ${res.data?.deleted ?? 0} 条记忆`)
    if (selectedMemoryUserId.value === externalUserId) {
      selectedMemoryUserId.value = ''
      memoryDetails.value = []
    }
    await loadMemoryUsers()
  } catch {
    // interceptor handled
  }
}

async function handleDeleteMemory(memoryId) {
  if (!memoryDrawerKey.value?.id) return
  try {
    await deleteApiKeyMemory(memoryDrawerKey.value.id, memoryId)
    message.success('记忆已删除')
    await loadMemoryDetails(selectedMemoryUserId.value)
    await loadMemoryUsers()
  } catch {
    // interceptor handled
  }
}
</script>

<style scoped>
.page {
  padding: var(--space-xl);
  padding-right: calc(var(--space-xl) + var(--scroll-content-gap));
  height: var(--app-content-height);
  overflow-y: auto;
  background: var(--color-canvas-soft);
  scrollbar-gutter: stable;
}
.settings-tabs {
  margin-bottom: 24px;
}
.settings-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}
.page-header {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0;
}
.content-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(480px, 1fr));
  gap: 24px;
}
.panel {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
}
.panel-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-hairline);
}
.panel-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.panel-title-wrap h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0;
}
.panel-desc {
  font-size: 13px;
  color: var(--color-mute);
}
.panel-desc-right {
  margin-left: auto;
}
/* 排行榜时间范围切换器：推到右侧 */
.ranking-range-switch {
  margin-left: auto;
}
.panel-body {
  padding: 20px;
}
.btn-primary {
  display: inline-flex;
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
.panel-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  padding: 10px 12px;
  background: var(--color-link-bg-soft);
  border: 1px solid transparent;
  border-radius: 8px;
  font-size: 12px;
  color: var(--color-link);
}
.panel-tip :deep(svg) {
  flex-shrink: 0;
}
.landing-panel {
  grid-column: 1 / -1;
}
.subtitle-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.subtitle-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.btn-icon-danger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--color-error-soft);
  background: var(--color-canvas);
  color: var(--color-error);
  border-radius: 6px;
  cursor: pointer;
  flex-shrink: 0;
}
.btn-icon-danger:hover {
  background: var(--color-error-soft);
}
.btn-icon-move {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas);
  color: var(--color-body);
  border-radius: 6px;
  cursor: pointer;
  flex-shrink: 0;
}
.btn-icon-move:hover:not(:disabled) {
  background: var(--color-canvas-soft-2);
  border-color: var(--color-mute);
}
.btn-icon-move:disabled {
  color: #d4d4d8;
  cursor: not-allowed;
}
.feature-card-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}
.icon-grid-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
.icon-grid-option :deep(.anticon) {
  font-size: 16px;
}
.btn-add {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  border: 1px dashed var(--color-hairline-strong);
  background: var(--color-canvas-soft);
  color: var(--color-body);
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.btn-add:hover {
  border-color: var(--color-ink);
  color: var(--color-ink);
}
.feature-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.features-toggle-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.features-count {
  font-size: 12px;
  color: var(--color-mute);
}
.btn-text-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border: none;
  background: none;
  color: var(--color-link);
  font-size: 13px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.15s;
}
.btn-text-toggle:hover {
  background: rgba(0, 112, 243, 0.06);
}
.features-toggle-icon {
  font-size: 11px;
  transition: transform 0.2s;
}
.features-toggle-icon.expanded {
  transform: rotate(90deg);
}
.feature-card {
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 12px 16px;
  background: var(--color-canvas-soft);
}
.feature-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.feature-index {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-mute);
}
.token-ranking-panel {
  margin-top: 24px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
}
.token-stat-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.token-stat-card {
  padding: 16px;
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
}
.token-stat-label {
  font-size: 13px;
  color: var(--color-mute);
  margin-bottom: 4px;
}
.token-stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-ink);
  line-height: 1.2;
}
.token-stat-sub {
  font-size: 13px;
  color: var(--color-mute);
  margin-bottom: 8px;
}
.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
  background: var(--color-canvas-soft-2);
  color: var(--color-mute);
}
.rank-badge.rank-top {
  background: var(--color-primary);
  color: #fff;
}
.token-amount {
  font-weight: 600;
  color: var(--color-ink);
  font-variant-numeric: tabular-nums;
}
.btn-icon-refresh {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas);
  color: var(--color-body);
  border-radius: 6px;
  cursor: pointer;
}
.btn-icon-refresh:hover:not(:disabled) {
  background: var(--color-canvas-soft-2);
  border-color: var(--color-hairline-strong);
}
.btn-icon-refresh:disabled {
  color: var(--color-hairline);
  cursor: not-allowed;
}
.empty-tip {
  text-align: center;
  padding: 40px 0;
  color: var(--color-mute);
  font-size: 14px;
}
.ranking-user {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ranking-username {
  font-weight: 500;
  color: var(--color-ink);
}
.apikey-docs-link {
  margin-left: 8px;
  font-size: 13px;
  color: var(--color-link);
  text-decoration: none;
}
.apikey-docs-link:hover {
  text-decoration: underline;
}
.apikey-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}
.apikey-card {
  border: 1px solid var(--color-hairline);
  border-radius: 10px;
  padding: 16px;
  transition: border-color 0.2s;
}
.apikey-card:hover {
  border-color: var(--color-hairline);
}
.apikey-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.apikey-card-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.apikey-card-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}
.apikey-card-prefix {
  font-family: 'Monaco', 'Consolas', monospace;
  font-size: 12px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 2px 8px;
  border-radius: 6px;
}
.apikey-card-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}
.apikey-card-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-body);
}
.apikey-card-label {
  color: var(--color-mute);
  flex-shrink: 0;
  min-width: 64px;
}
.apikey-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid var(--color-hairline);
}
.apikey-card-switch {
  display: flex;
  align-items: center;
  gap: 8px;
}
.apikey-card-switch-label {
  font-size: 12px;
  color: var(--color-mute);
}
.apikey-card-actions {
  display: flex;
  gap: 4px;
}
.apikey-secret-value {
  flex: 1;
  font-family: 'Monaco', 'Consolas', monospace;
  font-size: 13px;
  background: var(--color-canvas-soft-2);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  padding: 12px;
  white-space: nowrap;
  overflow-x: auto;
  color: var(--color-ink);
}
.split-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.memory-settings {
  display: grid;
  grid-template-columns: repeat(2, minmax(240px, 1fr));
  gap: 12px;
}
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 76px;
  padding: 14px 16px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  color: var(--color-body);
  background: var(--color-canvas-soft);
}
.setting-text {
  display: grid;
  gap: 4px;
  min-width: 0;
}
.setting-text strong {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}
.setting-text small {
  font-size: 12px;
  line-height: 1.4;
  color: var(--color-mute);
}
.setting-select {
  width: 160px;
}
.form-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.4;
}
.effective-policy {
  font-size: 13px;
  color: var(--color-body);
}
.memory-drawer-desc {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--color-mute);
  line-height: 1.5;
}
@media (max-width: 960px) {
  .memory-settings {
    grid-template-columns: 1fr;
  }
}
.memory-user-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.memory-user-card {
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 12px 14px;
  background: var(--color-canvas-soft);
}
.memory-user-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.memory-user-id {
  font-size: 13px;
  color: var(--color-ink);
}
.memory-user-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-mute);
}
.memory-user-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.memory-detail-panel {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-hairline);
}
.memory-detail-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.memory-detail-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
}
.memory-detail-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.memory-detail-content {
  flex: 1 1 100%;
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-body);
  word-break: break-word;
}
</style>
