<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">系统管理</h1>
      <p class="page-desc">管理系统配置、Landing 页面和用户</p>
    </div>

    <a-tabs v-model:activeKey="activeTab" class="settings-tabs">
      <a-tab-pane key="model" tab="默认模型管理" />
      <a-tab-pane key="landing" tab="Landing 管理" />
      <a-tab-pane key="users" tab="用户管理" />
      <a-tab-pane key="token" tab="Token 管理" />
      <a-tab-pane key="apiKey" tab="API Key 管理" />
    </a-tabs>

    <!-- Tab 1: 默认模型管理 -->
    <div v-show="activeTab === 'model'">
    <a-spin :spinning="modelLoading">
    <div class="content-grid">
      <!-- 默认对话模型 -->
      <div class="panel">
        <div class="panel-header">
          <div class="panel-title-wrap">
            <h3>默认对话模型</h3>
            <span class="panel-desc">系统级对话/生成场景使用</span>
          </div>
        </div>
        <div class="panel-body">
          <a-form :label-col="{ span: 6 }">
            <a-form-item label="模型">
              <ModelSelect
                v-model="chatValue"
                model-type="llm"
                placeholder="选择对话模型"
                @change="(m) => onModelChange('chat', m)"
              />
            </a-form-item>
            <a-form-item :wrapper-col="{ offset: 6 }">
              <button class="btn-primary" :disabled="chatSaving" @click="saveModel('chat')">
                <SaveOutlined /> {{ chatSaving ? '保存中...' : '保存配置' }}
              </button>
            </a-form-item>
          </a-form>
          <div class="panel-tip">
            <BulbOutlined />
            <span>用于：AI 生成系统提示词、AI 生成推荐问题、知识库思维导图、内容安全扫描等</span>
          </div>
        </div>
      </div>

      <!-- 默认向量模型 -->
      <div class="panel">
        <div class="panel-header">
          <div class="panel-title-wrap">
            <h3>默认向量模型</h3>
            <span class="panel-desc">向量化与检索场景使用</span>
          </div>
        </div>
        <div class="panel-body">
          <a-form :label-col="{ span: 6 }">
            <a-form-item label="模型">
              <ModelSelect
                v-model="embeddingValue"
                model-type="embedding"
                placeholder="选择向量模型"
                @change="(m) => onModelChange('embedding', m)"
              />
            </a-form-item>
            <a-form-item :wrapper-col="{ offset: 6 }">
              <button class="btn-primary" :disabled="embeddingSaving" @click="saveModel('embedding')">
                <SaveOutlined /> {{ embeddingSaving ? '保存中...' : '保存配置' }}
              </button>
            </a-form-item>
          </a-form>
          <div class="panel-tip">
            <BulbOutlined />
            <span>用于：知识库默认 Embedding（新建知识库未指定时使用）、文本相似度计算等</span>
          </div>
        </div>
      </div>

      <!-- 默认重排模型 -->
      <div class="panel">
        <div class="panel-header">
          <div class="panel-title-wrap">
            <h3>默认重排模型</h3>
            <span class="panel-desc">RAG 召回后精排使用</span>
          </div>
        </div>
        <div class="panel-body">
          <a-form :label-col="{ span: 6 }">
            <a-form-item label="模型">
              <ModelSelect
                v-model="rerankValue"
                model-type="rerank"
                placeholder="选择重排模型"
                @change="(m) => onModelChange('rerank', m)"
              />
            </a-form-item>
            <a-form-item :wrapper-col="{ offset: 6 }">
              <button class="btn-primary" :disabled="rerankSaving" @click="saveModel('rerank')">
                <SaveOutlined /> {{ rerankSaving ? '保存中...' : '保存配置' }}
              </button>
            </a-form-item>
          </a-form>
          <div class="panel-tip">
            <BulbOutlined />
            <span>用于：知识库检索结果重排序</span>
          </div>
        </div>
      </div>

      <!-- 默认TTS模型 -->
      <div class="panel">
        <div class="panel-header">
          <div class="panel-title-wrap">
            <h3>默认 TTS 模型</h3>
            <span class="panel-desc">语音合成场景使用</span>
          </div>
        </div>
        <div class="panel-body">
          <a-form :label-col="{ span: 6 }">
            <a-form-item label="模型">
              <ModelSelect
                v-model="ttsValue"
                model-type="tts"
                placeholder="选择 TTS 模型"
                @change="(m) => onModelChange('tts', m)"
              />
            </a-form-item>
            <a-form-item :wrapper-col="{ offset: 6 }">
              <button class="btn-primary" :disabled="ttsSaving" @click="saveModel('tts')">
                <SaveOutlined /> {{ ttsSaving ? '保存中...' : '保存配置' }}
              </button>
            </a-form-item>
          </a-form>
          <div class="panel-tip">
            <BulbOutlined />
            <span>用于：文本转语音播放、AI 回复语音化等</span>
          </div>
        </div>
      </div>
    </div>
    </a-spin>
    </div>

    <!-- Tab 2: Landing 管理 -->
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
            <a-input v-model:value="landing.title" placeholder="LightBot" :maxlength="30" show-count />
          </a-form-item>
          <a-form-item label="副标题轮播">
            <div class="subtitle-list">
              <div v-for="(sub, idx) in landing.subtitles" :key="idx" class="subtitle-row">
                <a-input v-model:value="landing.subtitles[idx]" placeholder="副标题" style="flex:1" :maxlength="30" show-count />
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
            <a-textarea v-model:value="landing.description" :rows="3" placeholder="平台介绍文字" :maxlength="200" show-count />
          </a-form-item>
          <a-form-item label="GitHub 地址">
            <a-input v-model:value="landing.github" placeholder="https://github.com/..." :maxlength="200" />
          </a-form-item>
          <a-form-item label="版权信息">
            <a-input v-model:value="landing.copyright" placeholder="© 2026 LightBot" :maxlength="100" show-count />
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
              <div v-for="(feat, idx) in landing.features" :key="idx" class="feature-card">
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
                  <a-input v-model:value="feat.title" placeholder="功能名称" :maxlength="20" show-count />
                </a-form-item>
                <a-form-item label="描述" :label-col="{ span: 3 }" :wrapper-col="{ span: 20 }">
                  <a-textarea v-model:value="feat.desc" :rows="2" placeholder="功能描述（建议不超过40字）" :maxlength="40" show-count />
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

    <!-- Tab 4: Token 管理 -->
    <div v-show="activeTab === 'token'">
    <a-spin :spinning="tokenLoading">
    <div class="content-grid">
      <!-- 全局统计大屏 -->
      <div class="panel token-stats-panel">
        <div class="panel-header">
          <h3>今日 Token 消耗</h3>
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
            <h3>限额配置</h3>
            <span class="panel-desc">调整 Token 使用限制</span>
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
        <h3>用户 Token 消耗排行</h3>
        <span class="panel-desc panel-desc-right">今日 Top {{ tokenRanking.length }}</span>
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
          <h3>API Key 管理</h3>
          <a-popover trigger="click" placement="right" :overlay-style="{ maxWidth: '480px' }">
            <template #content>
              <div style="font-size: 13px; line-height: 1.8; padding: 4px 0;">
                <p style="font-weight: 600; font-size: 14px; margin-bottom: 8px;">API Key 使用说明</p>
                <p><b>什么是 API Key？</b></p>
                <p>API Key 是用于外部系统调用 LightBot 接口的认证凭证，格式为 <code>lbkey_xxxx...</code>，支持通过 HTTP Header 传递。</p>
                <p style="margin-top: 8px;"><b>如何使用？</b></p>
                <p>在请求头中添加：</p>
                <p><code>Authorization: Bearer lbkey_your_key_here</code></p>
                <p style="margin-top: 8px;"><b>支持的接口：</b></p>
                <ul style="padding-left: 16px; margin: 4px 0;">
                  <li><code>chat</code> 权限：仅可调用对话相关接口（/api/chat/**）</li>
                  <li><code>full</code> 权限：可调用所有已认证接口</li>
                </ul>
                <p style="margin-top: 8px;"><b>注意事项：</b></p>
                <ul style="padding-left: 16px; margin: 4px 0;">
                  <li>密钥仅在创建时显示一次，请妥善保管</li>
                  <li>可随时启用/禁用或删除密钥</li>
                  <li>支持设置过期时间，过期后自动失效</li>
                  <li>如密钥泄露，请立即删除并重新生成</li>
                </ul>
              </div>
            </template>
            <QuestionCircleOutlined style="font-size: 15px; color: #a1a1aa; cursor: pointer; margin-left: 6px;" />
          </a-popover>
        </div>
        <span class="panel-desc">用于外部系统调用 LightBot 接口的认证凭证</span>
        <button class="btn-primary" style="margin-left: auto;" @click="showCreateApiKey">
          <PlusOutlined /> 创建 API Key
        </button>
      </div>
      <div class="panel-body">
        <div v-if="apiKeyList.length === 0 && !apiKeyLoading" class="empty-tip">暂无 API Key，点击上方按钮创建一个</div>
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
                <a-tag :color="key.permissions === 'full' ? 'blue' : 'default'">
                  {{ key.permissions === 'full' ? '完全访问' : '仅对话' }}
                </a-tag>
              </div>
              <div class="apikey-card-row">
                <span class="apikey-card-label">过期时间</span>
                <span>{{ key.expiresAt || '永不过期' }}</span>
              </div>
              <div class="apikey-card-row">
                <span class="apikey-card-label">最近使用</span>
                <span>{{ key.lastUsedAt || '-' }}</span>
              </div>
            </div>
            <div class="apikey-card-footer">
              <div class="apikey-card-switch">
                <span class="apikey-card-switch-label">{{ key.isEnabled === 1 ? '已启用' : '已禁用' }}</span>
                <a-switch :checked="key.isEnabled === 1" size="small" @change="handleToggleApiKey(key)" />
              </div>
              <div class="apikey-card-actions">
                <a-popconfirm title="确定要删除此 API Key 吗？" @confirm="handleDeleteApiKey(key)" ok-text="确定" cancel-text="取消">
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

    <!-- 创建 API Key 弹窗 -->
    <a-modal
      v-model:open="apiKeyCreateVisible"
      title="创建 API Key"
      :maskClosable="false"
      @ok="handleCreateApiKey"
      :confirmLoading="apiKeyCreateLoading"
      ok-text="创建"
      cancel-text="取消"
    >
      <a-form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="apiKeyForm.name" placeholder="如：生产环境API" :maxlength="64" />
        </a-form-item>
        <a-form-item label="权限">
          <a-select v-model:value="apiKeyForm.permissions">
            <a-select-option value="chat">仅对话</a-select-option>
            <a-select-option value="full">完全访问</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 密钥展示弹窗（创建后一次性显示） -->
    <a-modal
      v-model:open="apiKeySecretVisible"
      title="API Key 已创建"
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
import { ref, reactive, onMounted, watch, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  SaveOutlined, BulbOutlined, PlusOutlined, DeleteOutlined,
  UpOutlined, DownOutlined,
  RobotOutlined, TeamOutlined, ApartmentOutlined, ApiOutlined,
  ToolOutlined, ThunderboltOutlined, ExperimentOutlined, EyeOutlined,
  FormOutlined, DatabaseOutlined, NodeIndexOutlined, BranchesOutlined,
  CloudOutlined, CodeOutlined, FileTextOutlined, RocketOutlined,
  SafetyOutlined, SettingOutlined, ThunderboltFilled, SyncOutlined,
  AppstoreOutlined, ControlOutlined, ClusterOutlined, BlockOutlined,
  QuestionCircleOutlined, KeyOutlined, CopyOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getAllDefaultModels,
  updateDefaultChatModel,
  updateDefaultEmbeddingModel,
  updateDefaultTtsModel,
  updateDefaultRerankModel,
} from '../api/systemConfig'
import { getLandingConfig, updateLandingConfig } from '../api/landing'
import { getTokenBudgetConfig, updateTokenBudgetConfig, getTokenBudgetStats, getTokenBudgetRanking } from '../api/tokenBudget'
import { listApiKeys, createApiKey, toggleApiKey, deleteApiKey } from '../api/apiKey'
import ModelSelect from '../components/ModelSelect.vue'
import UserManage from './UserManage.vue'
import { copyToClipboard } from '../utils/clipboard'

const route = useRoute()
const router = useRouter()
const VALID_TABS = ['model', 'landing', 'users', 'token', 'apiKey']
const activeTab = ref(VALID_TABS.includes(route.query.tab) ? route.query.tab : 'model')
const modelLoading = ref(false)
const landingLoading = ref(false)
const loadedTabs = new Set()

const chatValue = ref(null)
const embeddingValue = ref(null)
const ttsValue = ref(null)
const rerankValue = ref(null)

const chatSaving = ref(false)
const embeddingSaving = ref(false)
const ttsSaving = ref(false)
const rerankSaving = ref(false)

// 缓存 providerId:modelId 用于保存
const chatProviderId = ref(null)
const chatModelId = ref(null)
const embeddingProviderId = ref(null)
const embeddingModelId = ref(null)
const rerankProviderId = ref(null)
const rerankModelId = ref(null)
const ttsProviderId = ref(null)
const ttsModelId = ref(null)

onMounted(() => {
  loadTabData('model')
})

watch(activeTab, (tab) => {
  loadTabData(tab)
  // 同步 URL query 参数
  router.replace({ query: { ...route.query, tab } })
})

async function loadTabData(tab) {
  if (loadedTabs.has(tab)) return
  loadedTabs.add(tab)
  if (tab === 'model') {
    modelLoading.value = true
    try {
      const res = await getAllDefaultModels()
      const data = res.data || {}
      applyModelConfig('chat', data.chat)
      applyModelConfig('embedding', data.embedding)
      applyModelConfig('tts', data.tts)
      applyModelConfig('rerank', data.rerank)
    } finally {
      modelLoading.value = false
    }
  } else if (tab === 'landing') {
    landingLoading.value = true
    try {
      await loadLandingConfig()
    } finally {
      landingLoading.value = false
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

function applyModelConfig(kind, cfg) {
  const pid = cfg?.providerId ? String(cfg.providerId) : null
  const mid = cfg?.modelId ? String(cfg.modelId) : null
  if (kind === 'chat') { chatProviderId.value = pid; chatModelId.value = mid; if (pid && mid) chatValue.value = `${pid}:${mid}` }
  else if (kind === 'embedding') { embeddingProviderId.value = pid; embeddingModelId.value = mid; if (pid && mid) embeddingValue.value = `${pid}:${mid}` }
  else if (kind === 'rerank') { rerankProviderId.value = pid; rerankModelId.value = mid; if (pid && mid) rerankValue.value = `${pid}:${mid}` }
  else if (kind === 'tts') { ttsProviderId.value = pid; ttsModelId.value = mid; if (pid && mid) ttsValue.value = `${pid}:${mid}` }
}

function onModelChange(kind, { providerId, modelId }) {
  const pid = providerId ? String(providerId) : providerId
  const mid = modelId ? String(modelId) : modelId
  if (kind === 'chat') { chatProviderId.value = pid; chatModelId.value = mid }
  else if (kind === 'embedding') { embeddingProviderId.value = pid; embeddingModelId.value = mid }
  else if (kind === 'rerank') { rerankProviderId.value = pid; rerankModelId.value = mid }
  else if (kind === 'tts') { ttsProviderId.value = pid; ttsModelId.value = mid }
}

const modelSaveConfig = {
  chat:      { providerId: chatProviderId,      modelId: chatModelId,      saving: chatSaving,      api: updateDefaultChatModel,      label: '默认对话模型' },
  embedding: { providerId: embeddingProviderId, modelId: embeddingModelId, saving: embeddingSaving, api: updateDefaultEmbeddingModel, label: '默认向量模型' },
  rerank:    { providerId: rerankProviderId,    modelId: rerankModelId,    saving: rerankSaving,    api: updateDefaultRerankModel,    label: '默认重排模型' },
  tts:       { providerId: ttsProviderId,       modelId: ttsModelId,       saving: ttsSaving,       api: updateDefaultTtsModel,       label: '默认 TTS 模型' },
}

async function saveModel(kind) {
  const cfg = modelSaveConfig[kind]
  if (!cfg.providerId.value || !cfg.modelId.value) return message.warning('请选择模型')
  cfg.saving.value = true
  try {
    await cfg.api({ providerId: cfg.providerId.value, modelId: cfg.modelId.value })
    message.success(`${cfg.label}已保存`)
  } catch (e) {
    message.error(e.response?.data?.message || '保存失败')
  } finally {
    cfg.saving.value = false
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
const rankingColumns = [
  { title: '排名', key: 'rank', width: 80, align: 'center' },
  { title: '用户', key: 'user' },
  { title: '消耗 Token', key: 'usedTokens', align: 'right' },
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
    const res = await getTokenBudgetRanking(20)
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

// API Key 管理
const apiKeyLoading = ref(false)
const apiKeyList = ref([])
const apiKeyCreateVisible = ref(false)
const apiKeyCreateLoading = ref(false)
const apiKeySecretVisible = ref(false)
const apiKeyCreatedSecret = ref('')
const apiKeyForm = reactive({ name: '', permissions: 'chat' })

async function loadApiKeys() {
  const res = await listApiKeys()
  apiKeyList.value = res.data || []
}

function showCreateApiKey() {
  apiKeyForm.name = ''
  apiKeyForm.permissions = 'chat'
  apiKeyCreateVisible.value = true
}

async function handleCreateApiKey() {
  if (!apiKeyForm.name.trim()) return message.warning('请输入名称')
  apiKeyCreateLoading.value = true
  try {
    const res = await createApiKey({
      name: apiKeyForm.name.trim(),
      permissions: apiKeyForm.permissions,
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
</script>

<style scoped>
.page {
  padding: 32px;
  height: 100vh;
  overflow-y: auto;
  background: var(--color-canvas-soft);
}
.settings-tabs {
  margin-bottom: 24px;
}
.settings-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}
.page-header {
  margin-bottom: 24px;
}
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-primary);
  margin: 0 0 4px;
}
.page-desc {
  font-size: 14px;
  color: var(--color-mute);
  margin: 0;
}
.content-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(480px, 1fr));
  gap: 24px;
}
.panel {
  background: #fff;
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
}
.panel-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid #ebebeb;
}
.panel-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.panel-title-wrap h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-primary);
  margin: 0;
}
.panel-desc {
  font-size: 13px;
  color: var(--color-mute);
}
.panel-desc-right {
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
  background: #f0f7ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  font-size: 12px;
  color: #1d4ed8;
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
  border: 1px solid #fca5a5;
  background: #fff;
  color: #dc2626;
  border-radius: 6px;
  cursor: pointer;
  flex-shrink: 0;
}
.btn-icon-danger:hover {
  background: #fef2f2;
}
.btn-icon-move {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid #d4d4d8;
  background: #fff;
  color: #52525b;
  border-radius: 6px;
  cursor: pointer;
  flex-shrink: 0;
}
.btn-icon-move:hover:not(:disabled) {
  background: #f4f4f5;
  border-color: #a1a1aa;
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
  border: 1px dashed #d4d4d8;
  background: var(--color-canvas-soft);
  color: #52525b;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.btn-add:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
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
  color: #a1a1aa;
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
  background: #fff;
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
  color: var(--color-primary);
  line-height: 1.2;
}
.token-stat-sub {
  font-size: 13px;
  color: #a1a1aa;
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
  background: #f4f4f5;
  color: var(--color-mute);
}
.rank-badge.rank-top {
  background: var(--color-primary);
  color: #fff;
}
.token-amount {
  font-weight: 600;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}
.btn-icon-refresh {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid #d4d4d8;
  background: #fff;
  color: #52525b;
  border-radius: 6px;
  cursor: pointer;
}
.btn-icon-refresh:hover:not(:disabled) {
  background: #f4f4f5;
  border-color: #a1a1aa;
}
.btn-icon-refresh:disabled {
  color: #d4d4d8;
  cursor: not-allowed;
}
.empty-tip {
  text-align: center;
  padding: 40px 0;
  color: #a1a1aa;
  font-size: 14px;
}
.ranking-user {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ranking-username {
  font-weight: 500;
  color: var(--color-primary);
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
  border-color: #d4d4d8;
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
  color: var(--color-primary);
}
.apikey-card-prefix {
  font-family: 'Monaco', 'Consolas', monospace;
  font-size: 12px;
  color: var(--color-mute);
  background: #f4f4f5;
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
  color: #52525b;
}
.apikey-card-label {
  color: #a1a1aa;
  flex-shrink: 0;
  min-width: 64px;
}
.apikey-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid #f4f4f5;
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
  background: #f4f4f5;
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  padding: 12px;
  white-space: nowrap;
  overflow-x: auto;
  color: var(--color-primary);
}
</style>
