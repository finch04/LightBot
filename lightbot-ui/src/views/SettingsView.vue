<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">系统设置</h1>
      <p class="page-desc">配置系统默认 AI 模型，用于 Agent 配置之外的系统级 AI 调用</p>
    </div>

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
            <span>用于：知识库检索结果重排序（如 DashScope gte-rerank 系列）</span>
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
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { SaveOutlined, BulbOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getDefaultChatModel, updateDefaultChatModel,
  getDefaultEmbeddingModel, updateDefaultEmbeddingModel,
  getDefaultTtsModel, updateDefaultTtsModel,
  getDefaultRerankModel, updateDefaultRerankModel,
} from '../api/systemConfig'
import ModelSelect from '../components/ModelSelect.vue'

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
  await Promise.all([loadChatConfig(), loadEmbeddingConfig(), loadTtsConfig(), loadRerankConfig()])
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
</script>

<style scoped>
.page {
  padding: 32px;
  height: 100vh;
  overflow-y: auto;
  background: #fafafa;
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
</style>
