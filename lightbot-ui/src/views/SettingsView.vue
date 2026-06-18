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
    </a-tabs>

    <!-- Tab 1: 默认模型管理 -->
    <div v-show="activeTab === 'model'">
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
              <button class="btn-primary" :disabled="chatSaving" @click="saveChatModel">
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
              <button class="btn-primary" :disabled="embeddingSaving" @click="saveEmbeddingModel">
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
              <button class="btn-primary" :disabled="rerankSaving" @click="saveRerankModel">
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
              <button class="btn-primary" :disabled="ttsSaving" @click="saveTtsModel">
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
    </div>

    <!-- Tab 2: Landing 管理 -->
    <div v-show="activeTab === 'landing'">
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
            <div class="feature-list">
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
    </div>

    <!-- Tab 3: 用户管理 -->
    <div v-show="activeTab === 'users'">
      <UserManage />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, markRaw } from 'vue'
import {
  SaveOutlined, BulbOutlined, PlusOutlined, DeleteOutlined,
  UpOutlined, DownOutlined,
  RobotOutlined, TeamOutlined, ApartmentOutlined, ApiOutlined,
  ToolOutlined, ThunderboltOutlined, ExperimentOutlined, EyeOutlined,
  FormOutlined, DatabaseOutlined, NodeIndexOutlined, BranchesOutlined,
  CloudOutlined, CodeOutlined, FileTextOutlined, RocketOutlined,
  SafetyOutlined, SettingOutlined, ThunderboltFilled, SyncOutlined,
  AppstoreOutlined, ControlOutlined, ClusterOutlined, BlockOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getDefaultChatModel, updateDefaultChatModel,
  getDefaultEmbeddingModel, updateDefaultEmbeddingModel,
  getDefaultTtsModel, updateDefaultTtsModel,
  getDefaultRerankModel, updateDefaultRerankModel,
} from '../api/systemConfig'
import { getLandingConfig, updateLandingConfig } from '../api/landing'
import ModelSelect from '../components/ModelSelect.vue'
import UserManage from './UserManage.vue'

const activeTab = ref('model')

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

onMounted(async () => {
  await Promise.all([loadChatConfig(), loadEmbeddingConfig(), loadTtsConfig(), loadRerankConfig(), loadLandingConfig()])
})

function onModelChange(kind, { providerId, modelId }) {
  const pid = providerId ? String(providerId) : providerId
  const mid = modelId ? String(modelId) : modelId
  if (kind === 'chat') { chatProviderId.value = pid; chatModelId.value = mid }
  else if (kind === 'embedding') { embeddingProviderId.value = pid; embeddingModelId.value = mid }
  else if (kind === 'rerank') { rerankProviderId.value = pid; rerankModelId.value = mid }
  else if (kind === 'tts') { ttsProviderId.value = pid; ttsModelId.value = mid }
}

async function loadChatConfig() {
  try {
    const res = await getDefaultChatModel()
    const pid = res.data?.providerId ? String(res.data.providerId) : null
    const mid = res.data?.modelId ? String(res.data.modelId) : null
    chatProviderId.value = pid
    chatModelId.value = mid
    if (pid && mid) chatValue.value = `${pid}:${mid}`
  } catch (e) {
    console.error('[Settings] 加载对话模型配置失败:', e)
  }
}

async function loadEmbeddingConfig() {
  try {
    const res = await getDefaultEmbeddingModel()
    const pid = res.data?.providerId ? String(res.data.providerId) : null
    const mid = res.data?.modelId ? String(res.data.modelId) : null
    embeddingProviderId.value = pid
    embeddingModelId.value = mid
    if (pid && mid) embeddingValue.value = `${pid}:${mid}`
  } catch (e) {
    console.error('[Settings] 加载向量模型配置失败:', e)
  }
}

async function loadRerankConfig() {
  try {
    const res = await getDefaultRerankModel()
    const pid = res.data?.providerId ? String(res.data.providerId) : null
    const mid = res.data?.modelId ? String(res.data.modelId) : null
    rerankProviderId.value = pid
    rerankModelId.value = mid
    if (pid && mid) rerankValue.value = `${pid}:${mid}`
  } catch (e) {
    console.error('[Settings] 加载重排模型配置失败:', e)
  }
}

async function loadTtsConfig() {
  try {
    const res = await getDefaultTtsModel()
    const pid = res.data?.providerId ? String(res.data.providerId) : null
    const mid = res.data?.modelId ? String(res.data.modelId) : null
    ttsProviderId.value = pid
    ttsModelId.value = mid
    if (pid && mid) ttsValue.value = `${pid}:${mid}`
  } catch (e) {
    console.error('[Settings] 加载TTS模型配置失败:', e)
  }
}

async function saveChatModel() {
  if (!chatProviderId.value || !chatModelId.value) return message.warning('请选择模型')
  chatSaving.value = true
  try {
    await updateDefaultChatModel({ providerId: chatProviderId.value, modelId: chatModelId.value })
    message.success('默认对话模型已保存')
  } catch (e) {
    message.error(e.response?.data?.message || '保存失败')
  } finally {
    chatSaving.value = false
  }
}

async function saveEmbeddingModel() {
  if (!embeddingProviderId.value || !embeddingModelId.value) return message.warning('请选择模型')
  embeddingSaving.value = true
  try {
    await updateDefaultEmbeddingModel({ providerId: embeddingProviderId.value, modelId: embeddingModelId.value })
    message.success('默认向量模型已保存')
  } catch (e) {
    message.error(e.response?.data?.message || '保存失败')
  } finally {
    embeddingSaving.value = false
  }
}

async function saveRerankModel() {
  if (!rerankProviderId.value || !rerankModelId.value) return message.warning('请选择模型')
  rerankSaving.value = true
  try {
    await updateDefaultRerankModel({ providerId: rerankProviderId.value, modelId: rerankModelId.value })
    message.success('默认重排模型已保存')
  } catch (e) {
    message.error(e.response?.data?.message || '保存失败')
  } finally {
    rerankSaving.value = false
  }
}

async function saveTtsModel() {
  if (!ttsProviderId.value || !ttsModelId.value) return message.warning('请选择模型')
  ttsSaving.value = true
  try {
    await updateDefaultTtsModel({ providerId: ttsProviderId.value, modelId: ttsModelId.value })
    message.success('默认 TTS 模型已保存')
  } catch (e) {
    message.error(e.response?.data?.message || '保存失败')
  } finally {
    ttsSaving.value = false
  }
}

// Landing 配置
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
</script>

<style scoped>
.page {
  padding: 32px;
  height: 100vh;
  overflow-y: auto;
  background: #fafafa;
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
  color: #171717;
  margin: 0 0 4px;
}
.page-desc {
  font-size: 14px;
  color: #71717a;
  margin: 0;
}
.content-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(480px, 1fr));
  gap: 24px;
}
.panel {
  background: #fff;
  border: 1px solid #ebebeb;
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
  color: #171717;
  margin: 0;
}
.panel-desc {
  font-size: 13px;
  color: #71717a;
}
.panel-body {
  padding: 20px;
}
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: #171717;
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
  background: #fafafa;
  color: #52525b;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.btn-add:hover {
  border-color: #171717;
  color: #171717;
}
.feature-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.feature-card {
  border: 1px solid #ebebeb;
  border-radius: 8px;
  padding: 12px 16px;
  background: #fafafa;
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
  color: #71717a;
}
</style>
