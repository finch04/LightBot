<template>
  <a-spin :spinning="loading">
    <div class="content-grid">
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
                v-model:provider-id="chatProviderId"
                v-model:model-id="chatModelId"
                model-type="llm"
                placeholder="选择对话模型"
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
            <span>用于：AI 生成系统提示词、AI 生成推荐问题、知识库思维导图、内容安全扫描、数据模型补全字段英文名等</span>
          </div>
        </div>
      </div>

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
                v-model:provider-id="embeddingProviderId"
                v-model:model-id="embeddingModelId"
                model-type="embedding"
                placeholder="选择向量模型"
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
                v-model:provider-id="rerankProviderId"
                v-model:model-id="rerankModelId"
                model-type="rerank"
                placeholder="选择重排模型"
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
                v-model:provider-id="ttsProviderId"
                v-model:model-id="ttsModelId"
                model-type="tts"
                placeholder="选择 TTS 模型"
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
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { SaveOutlined, BulbOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getAllDefaultModels,
  updateDefaultChatModel,
  updateDefaultEmbeddingModel,
  updateDefaultTtsModel,
  updateDefaultRerankModel,
} from '../api/systemConfig'
import ModelSelect from './ModelSelect.vue'

const loading = ref(false)
const chatProviderId = ref(null)
const chatModelId = ref(null)
const embeddingProviderId = ref(null)
const embeddingModelId = ref(null)
const rerankProviderId = ref(null)
const rerankModelId = ref(null)
const ttsProviderId = ref(null)
const ttsModelId = ref(null)
const chatSaving = ref(false)
const embeddingSaving = ref(false)
const ttsSaving = ref(false)
const rerankSaving = ref(false)

function applyModelConfig(kind, cfg) {
  const pid = cfg?.providerId ? String(cfg.providerId) : null
  const mid = cfg?.modelId ? String(cfg.modelId) : null
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

async function loadDefaults() {
  loading.value = true
  try {
    const res = await getAllDefaultModels()
    const data = res.data || {}
    applyModelConfig('chat', data.chat)
    applyModelConfig('embedding', data.embedding)
    applyModelConfig('tts', data.tts)
    applyModelConfig('rerank', data.rerank)
  } finally {
    loading.value = false
  }
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

onMounted(loadDefaults)
</script>

<style scoped>
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
</style>
