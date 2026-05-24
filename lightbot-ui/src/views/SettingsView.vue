<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">系统设置</h1>
      <p class="page-desc">配置系统默认AI模型，用于生成提示词、推荐问题等功能</p>
    </div>

    <div class="content-grid">
      <!-- 默认AI配置 -->
      <div class="panel">
        <div class="panel-header">
          <h3>默认AI模型</h3>
          <span class="panel-desc">系统级AI调用使用的模型配置</span>
        </div>
        <div class="panel-body">
          <a-form :label-col="{ span: 6 }">
            <a-form-item label="模型提供商">
              <a-select
                v-model:value="config.providerId"
                placeholder="选择提供商"
                style="width: 100%"
                @change="handleProviderChange"
              >
                <a-select-option v-for="p in providerList" :key="p.id" :value="p.id">
                  {{ p.name }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="模型">
              <a-select
                v-model:value="config.modelId"
                placeholder="选择模型"
                style="width: 100%"
                :disabled="!config.providerId || modelList.length === 0"
              >
                <a-select-option v-for="m in modelList" :key="m.modelId" :value="m.modelId">
                  {{ m.name || m.modelId }}
                </a-select-option>
              </a-select>
              <span v-if="!config.providerId" class="form-tip">请先选择模型提供商</span>
              <span v-else-if="modelList.length === 0" class="form-tip warn">该提供商暂无可用模型，请先在"模型管理"中添加</span>
            </a-form-item>
            <a-form-item :wrapper-col="{ offset: 6 }">
              <button class="btn-primary" @click="handleSave" :disabled="saving">
                <SaveOutlined /> {{ saving ? '保存中...' : '保存配置' }}
              </button>
            </a-form-item>
          </a-form>
        </div>
      </div>

      <!-- 使用说明 -->
      <div class="panel">
        <div class="panel-header">
          <h3>使用说明</h3>
        </div>
        <div class="panel-body">
          <div class="info-list">
            <div class="info-item">
              <div class="info-icon"><BulbOutlined /></div>
              <div class="info-content">
                <h4>AI生成提示词</h4>
                <p>在Agent详情页点击"AI生成"按钮，系统将使用此配置自动生成系统提示词</p>
              </div>
            </div>
            <div class="info-item">
              <div class="info-icon"><BulbOutlined /></div>
              <div class="info-content">
                <h4>AI生成推荐问题</h4>
                <p>在Agent详情页点击"生成推荐问题"按钮，系统将使用此配置生成推荐问题列表</p>
              </div>
            </div>
            <div class="info-item">
              <div class="info-icon"><BulbOutlined /></div>
              <div class="info-content">
                <h4>思维导图生成</h4>
                <p>未来功能：系统将使用此配置生成思维导图等AI辅助内容</p>
              </div>
            </div>
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
import { getDefaultAiConfig, updateDefaultAiConfig } from '../api/systemConfig'
import { getModelProviders } from '../api/modelProvider'
import { getModelsByProvider } from '../api/model'

const providerList = ref([])
const modelList = ref([])
const saving = ref(false)

const config = reactive({
  providerId: null,
  modelId: null
})

onMounted(async () => {
  await loadProviders()
  await loadConfig()
})

async function loadProviders() {
  try {
    const res = await getModelProviders({ pageNum: 1, pageSize: 100 })
    providerList.value = res.data?.records || []
  } catch (e) {
    console.error('[Settings] 加载提供商失败:', e)
  }
}

async function loadConfig() {
  try {
    const res = await getDefaultAiConfig()
    if (res.data) {
      config.providerId = res.data.providerId
      config.modelId = res.data.modelId
      if (config.providerId) {
        await loadModels(config.providerId)
      }
    }
  } catch (e) {
    console.error('[Settings] 加载配置失败:', e)
  }
}

async function handleProviderChange(providerId) {
  config.modelId = null
  if (providerId) {
    await loadModels(providerId)
  }
}

async function loadModels(providerId) {
  try {
    const res = await getModelsByProvider(providerId)
    modelList.value = res.data || []
  } catch (e) {
    console.error('[Settings] 加载模型失败:', e)
    modelList.value = []
  }
}

async function handleSave() {
  if (!config.providerId) {
    message.warning('请选择模型提供商')
    return
  }
  if (!config.modelId) {
    message.warning('请选择模型')
    return
  }

  saving.value = true
  try {
    await updateDefaultAiConfig({
      providerId: config.providerId,
      modelId: config.modelId
    })
    message.success('配置已保存')
  } catch (e) {
    message.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
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
  align-items: baseline;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid #ebebeb;
}
.panel-header h3 {
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
.btn-primary:hover {
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
}
.form-tip.warn {
  color: #f59e0b;
}

/* 使用说明 */
.info-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.info-item {
  display: flex;
  gap: 12px;
}
.info-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #007cf0, #00dfd8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.info-content h4 {
  font-size: 14px;
  font-weight: 500;
  color: #171717;
  margin: 0 0 4px;
}
.info-content p {
  font-size: 13px;
  color: #71717a;
  margin: 0;
}
</style>