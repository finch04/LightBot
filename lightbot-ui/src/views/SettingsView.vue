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
            <a-form-item label="模型提供商">
              <a-select
                v-model:value="chatConfig.providerId"
                placeholder="选择提供商"
                style="width: 100%"
                allow-clear
                @change="(val) => onProviderChange('chat', val)"
              >
                <a-select-option v-for="p in providerList" :key="p.id" :value="String(p.id)">
                  {{ p.name }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="模型">
              <a-select
                v-model:value="chatConfig.modelId"
                placeholder="选择对话模型"
                style="width: 100%"
                allow-clear
                :disabled="!chatConfig.providerId"
              >
                <a-select-option v-for="m in chatModels" :key="m.modelId" :value="m.modelId">
                  {{ m.name || m.modelId }}
                </a-select-option>
              </a-select>
              <span v-if="!chatConfig.providerId" class="form-tip">请先选择模型提供商</span>
              <span v-else-if="chatModels.length === 0" class="form-tip warn">该提供商暂无可用对话模型</span>
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
            <a-form-item label="模型提供商">
              <a-select
                v-model:value="embeddingConfig.providerId"
                placeholder="选择提供商"
                style="width: 100%"
                allow-clear
                @change="(val) => onProviderChange('embedding', val)"
              >
                <a-select-option v-for="p in providerList" :key="p.id" :value="String(p.id)">
                  {{ p.name }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="模型">
              <a-select
                v-model:value="embeddingConfig.modelId"
                placeholder="选择向量模型"
                style="width: 100%"
                allow-clear
                :disabled="!embeddingConfig.providerId"
              >
                <a-select-option v-for="m in embeddingModels" :key="m.modelId" :value="m.modelId">
                  {{ m.name || m.modelId }}
                </a-select-option>
              </a-select>
              <span v-if="!embeddingConfig.providerId" class="form-tip">请先选择模型提供商</span>
              <span v-else-if="embeddingModels.length === 0" class="form-tip warn">该提供商暂无可用向量模型</span>
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
            <a-form-item label="模型提供商">
              <a-select
                v-model:value="rerankConfig.providerId"
                placeholder="选择提供商"
                style="width: 100%"
                allow-clear
                @change="(val) => onProviderChange('rerank', val)"
              >
                <a-select-option v-for="p in providerList" :key="p.id" :value="String(p.id)">
                  {{ p.name }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="模型">
              <a-select
                v-model:value="rerankConfig.modelId"
                placeholder="选择重排模型"
                style="width: 100%"
                allow-clear
                :disabled="!rerankConfig.providerId"
              >
                <a-select-option v-for="m in rerankModels" :key="m.modelId" :value="m.modelId">
                  {{ m.name || m.modelId }}
                </a-select-option>
              </a-select>
              <span v-if="!rerankConfig.providerId" class="form-tip">请先选择模型提供商</span>
              <span v-else-if="rerankModels.length === 0" class="form-tip warn">该提供商暂无可用重排模型</span>
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
            <a-form-item label="模型提供商">
              <a-select
                v-model:value="ttsConfig.providerId"
                placeholder="选择提供商"
                style="width: 100%"
                allow-clear
                @change="(val) => onProviderChange('tts', val)"
              >
                <a-select-option v-for="p in providerList" :key="p.id" :value="String(p.id)">
                  {{ p.name }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="模型">
              <a-select
                v-model:value="ttsConfig.modelId"
                placeholder="选择 TTS 模型"
                style="width: 100%"
                allow-clear
                :disabled="!ttsConfig.providerId"
              >
                <a-select-option v-for="m in ttsModels" :key="m.modelId" :value="m.modelId">
                  {{ m.name || m.modelId }}
                </a-select-option>
              </a-select>
              <span v-if="!ttsConfig.providerId" class="form-tip">请先选择模型提供商</span>
              <span v-else-if="ttsModels.length === 0" class="form-tip warn">该提供商暂无可用 TTS 模型</span>
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
import { ref, reactive, onMounted } from 'vue'
import { SaveOutlined, BulbOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getDefaultChatModel, updateDefaultChatModel,
  getDefaultEmbeddingModel, updateDefaultEmbeddingModel,
  getDefaultTtsModel, updateDefaultTtsModel,
  getDefaultRerankModel, updateDefaultRerankModel,
} from '../api/systemConfig'
import { getModelProviders } from '../api/modelProvider'
import { getModelsByProvider } from '../api/model'

const providerList = ref([])
const chatModels = ref([])
const embeddingModels = ref([])
const ttsModels = ref([])
const rerankModels = ref([])

const chatConfig = reactive({ providerId: null, modelId: null })
const embeddingConfig = reactive({ providerId: null, modelId: null })
const ttsConfig = reactive({ providerId: null, modelId: null })
const rerankConfig = reactive({ providerId: null, modelId: null })

const chatSaving = ref(false)
const embeddingSaving = ref(false)
const ttsSaving = ref(false)
const rerankSaving = ref(false)

onMounted(async () => {
  await loadProviders()
  await Promise.all([loadChatConfig(), loadEmbeddingConfig(), loadTtsConfig(), loadRerankConfig()])
})

async function loadProviders() {
  try {
    const res = await getModelProviders({ pageNum: 1, pageSize: 100 })
    providerList.value = res.data?.records || []
  } catch (e) {
    console.error('[Settings] 加载提供商失败:', e)
  }
}

/**
 * 加载某类型的模型列表（统一处理 Long ID 字符串化）
 */
async function loadModelsByType(providerId, modelType) {
  if (!providerId) return []
  try {
    const res = await getModelsByProvider(providerId)
    const all = res.data || []
    return all.filter(m => (m.type?.code || m.type) === modelType)
  } catch (e) {
    console.error('[Settings] 加载模型失败:', e)
    return []
  }
}

async function loadChatConfig() {
  try {
    const res = await getDefaultChatModel()
    // 后端 providerId 已用 ToStringSerializer 输出为字符串，但仍兜底转换避免精度丢失
    chatConfig.providerId = res.data?.providerId ? String(res.data.providerId) : null
    chatConfig.modelId = res.data?.modelId || null
    if (chatConfig.providerId) {
      chatModels.value = await loadModelsByType(chatConfig.providerId, 'llm')
    }
  } catch (e) {
    console.error('[Settings] 加载对话模型配置失败:', e)
  }
}

async function loadEmbeddingConfig() {
  try {
    const res = await getDefaultEmbeddingModel()
    embeddingConfig.providerId = res.data?.providerId ? String(res.data.providerId) : null
    embeddingConfig.modelId = res.data?.modelId || null
    if (embeddingConfig.providerId) {
      embeddingModels.value = await loadModelsByType(embeddingConfig.providerId, 'embedding')
    }
  } catch (e) {
    console.error('[Settings] 加载向量模型配置失败:', e)
  }
}

async function loadRerankConfig() {
  try {
    const res = await getDefaultRerankModel()
    rerankConfig.providerId = res.data?.providerId ? String(res.data.providerId) : null
    rerankConfig.modelId = res.data?.modelId || null
    if (rerankConfig.providerId) {
      rerankModels.value = await loadModelsByType(rerankConfig.providerId, 'rerank')
    }
  } catch (e) {
    console.error('[Settings] 加载重排模型配置失败:', e)
  }
}

async function loadTtsConfig() {
  try {
    const res = await getDefaultTtsModel()
    ttsConfig.providerId = res.data?.providerId ? String(res.data.providerId) : null
    ttsConfig.modelId = res.data?.modelId || null
    if (ttsConfig.providerId) {
      ttsModels.value = await loadModelsByType(ttsConfig.providerId, 'tts')
    }
  } catch (e) {
    console.error('[Settings] 加载TTS模型配置失败:', e)
  }
}

async function onProviderChange(kind, providerId) {
  if (kind === 'chat') {
    chatConfig.modelId = null
    chatModels.value = await loadModelsByType(providerId, 'llm')
  } else if (kind === 'embedding') {
    embeddingConfig.modelId = null
    embeddingModels.value = await loadModelsByType(providerId, 'embedding')
  } else if (kind === 'tts') {
    ttsConfig.modelId = null
    ttsModels.value = await loadModelsByType(providerId, 'tts')
  } else if (kind === 'rerank') {
    rerankConfig.modelId = null
    rerankModels.value = await loadModelsByType(providerId, 'rerank')
  }
}

async function saveChatModel() {
  if (!chatConfig.providerId) return message.warning('请选择模型提供商')
  if (!chatConfig.modelId) return message.warning('请选择模型')
  chatSaving.value = true
  try {
    await updateDefaultChatModel({ providerId: chatConfig.providerId, modelId: chatConfig.modelId })
    message.success('默认对话模型已保存')
  } catch (e) {
    message.error(e.response?.data?.message || '保存失败')
  } finally {
    chatSaving.value = false
  }
}

async function saveEmbeddingModel() {
  if (!embeddingConfig.providerId) return message.warning('请选择模型提供商')
  if (!embeddingConfig.modelId) return message.warning('请选择模型')
  embeddingSaving.value = true
  try {
    await updateDefaultEmbeddingModel({ providerId: embeddingConfig.providerId, modelId: embeddingConfig.modelId })
    message.success('默认向量模型已保存')
  } catch (e) {
    message.error(e.response?.data?.message || '保存失败')
  } finally {
    embeddingSaving.value = false
  }
}

async function saveRerankModel() {
  if (!rerankConfig.providerId) return message.warning('请选择模型提供商')
  if (!rerankConfig.modelId) return message.warning('请选择模型')
  rerankSaving.value = true
  try {
    await updateDefaultRerankModel({ providerId: rerankConfig.providerId, modelId: rerankConfig.modelId })
    message.success('默认重排模型已保存')
  } catch (e) {
    message.error(e.response?.data?.message || '保存失败')
  } finally {
    rerankSaving.value = false
  }
}

async function saveTtsModel() {
  if (!ttsConfig.providerId) return message.warning('请选择模型提供商')
  if (!ttsConfig.modelId) return message.warning('请选择模型')
  ttsSaving.value = true
  try {
    await updateDefaultTtsModel({ providerId: ttsConfig.providerId, modelId: ttsConfig.modelId })
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
.form-tip {
  font-size: 12px;
  color: #71717a;
  margin-top: 4px;
  display: block;
}
.form-tip.warn {
  color: #f59e0b;
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
