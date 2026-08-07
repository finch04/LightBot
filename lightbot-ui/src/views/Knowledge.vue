<template>
  <div class="page">
    <LbManageHeader
      title="知识库"
      v-model="searchText"
      search-placeholder="搜索知识库名称..."
      :refresh-disabled="loading"
      create-text="新建知识库"
      @refresh="refresh"
      @create="openCreateModal"
    >
      <template #searchPrefix><SearchOutlined /></template>
      <template #actions>
        <button class="lb-btn lb-btn--accent lb-btn--accent--knowledge" @click="router.push('/app/graph')">
          <ApartmentOutlined /> 知识图谱
        </button>
      </template>
    </LbManageHeader>

    <a-spin :spinning="loading">
    <div class="knowledge-grid">
      <EntityCard
        v-for="k in list"
        :key="k.id"
        type="knowledge"
        :name="k.name"
        @click="router.push(`/app/knowledge/${k.id}`)"
      >
        <template #info>
          <a-tooltip :title="k.name">
            <h3 class="card-title">{{ k.name }}</h3>
          </a-tooltip>
          <a-tooltip v-if="k.description" :title="k.description" placement="topLeft" :overlay-style="{ maxWidth: '400px' }">
            <p class="card-desc">{{ truncateText(k.description, 50) }}</p>
          </a-tooltip>
          <p v-else class="card-desc">暂无描述</p>
        </template>
        <template #actions>
          <a-tooltip title="删除知识库">
            <button class="btn-icon danger" @click="handleDelete(k.id)">
              <DeleteOutlined />
            </button>
          </a-tooltip>
        </template>
        <div class="card-stats">
          <a-tooltip title="文档数">
            <span class="card-stat-item">
              <FileTextOutlined class="card-stat-icon" />
              <span class="card-stat-value">{{ k.documentCount || 0 }}</span>
            </span>
          </a-tooltip>
          <a-tooltip title="分片数">
            <span class="card-stat-item">
              <BlockOutlined class="card-stat-icon" />
              <span class="card-stat-value">{{ k.chunkCount || 0 }}</span>
            </span>
          </a-tooltip>
          <a-tooltip title="Token 数">
            <span class="card-stat-item">
              <FontColorsOutlined class="card-stat-icon" />
              <span class="card-stat-value">{{ formatTokenCount(k.totalTokens) }}</span>
            </span>
          </a-tooltip>
          <span v-if="k.type" class="card-type-icon-wrap">
            <a-tooltip :title="k.type === 'milvus' ? 'Milvus' : k.type === 'dify' ? 'Dify Dataset（只读）' : 'PostgreSQL'">
              <CloudServerOutlined v-if="k.type === 'milvus'" class="card-type-icon milvus" />
              <ApiOutlined v-else-if="k.type === 'dify'" class="card-type-icon dify" />
              <DatabaseOutlined v-else class="card-type-icon pg" />
            </a-tooltip>
          </span>
        </div>
      </EntityCard>

      <LbEmptyState
        v-if="list.length === 0 && !loading"
        :icon="DatabaseOutlined"
        :title="searchText ? '没有匹配的知识库' : '还没有知识库，点击右上角创建一个吧'"
      />
    </div>
    </a-spin>

    <!-- 创建弹窗 -->
    <a-modal v-model:open="showCreate" title="新建知识库" :width="720" :maskClosable="false">
      <div class="dialog-scroll-body">
      <a-form :model="form" :label-col="{ flex: '0 0 110px' }">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" placeholder="请输入知识库名称（不超过 50 字）" :maxlength="50" show-count />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="3" placeholder="知识库描述（不超过50字，可选）" :maxlength="50" show-count />
        </a-form-item>
        <a-form-item label="知识库类型" required>
          <div class="kb-type-cards">
            <div
              class="kb-type-card"
              :class="{ active: form.type === 'pg' }"
              @click="form.type = 'pg'"
            >
              <div class="kb-type-header">
                <DatabaseOutlined class="kb-type-icon" />
                <span class="kb-type-title">PostgreSQL</span>
              </div>
              <div class="kb-type-desc">基于 pgvector 向量扩展，轻量易部署，适合中小规模知识库，与 PostgreSQL 生态无缝集成</div>
            </div>
            <div
              class="kb-type-card"
              :class="{ active: form.type === 'milvus' }"
              @click="form.type = 'milvus'"
            >
              <div class="kb-type-header">
                <CloudServerOutlined class="kb-type-icon" />
                <span class="kb-type-title">Milvus</span>
              </div>
              <div class="kb-type-desc">高性能分布式向量数据库，支持亿级向量检索、混合检索（BM25 + 向量），适合大规模生产场景</div>
            </div>
            <div
              class="kb-type-card"
              :class="{ active: form.type === 'dify' }"
              @click="form.type = 'dify'"
            >
              <div class="kb-type-header">
                <ApiOutlined class="kb-type-icon" />
                <span class="kb-type-title">Dify Dataset</span>
              </div>
              <div class="kb-type-desc">连接已有 Dify 知识库，只读检索；文档、分块和问答由 Dify 管理</div>
            </div>
          </div>
        </a-form-item>
        <template v-if="form.type === 'dify'">
          <a-form-item label="Dify API 地址" required>
            <a-input v-model:value="form.difyConfig.apiUrl" placeholder="请输入 Dify API 地址（以 /v1 结尾，不超过 512 字）" :maxlength="512" />
          </a-form-item>
          <a-form-item label="Dataset ID" required>
            <a-input v-model:value="form.difyConfig.datasetId" placeholder="请输入 Dify Dataset ID（不超过 128 字）" :maxlength="128" />
          </a-form-item>
          <a-form-item label="Dataset Token" required>
            <a-input-password v-model:value="form.difyConfig.token" placeholder="请输入 Dataset API Token（仅加密保存，不超过 2048 字）" :maxlength="2048" />
          </a-form-item>
          <a-alert type="info" show-icon message="测试不会保存配置；创建时会再次验证连接。Dify Dataset 为只读知识库。" />
        </template>
        <template v-else>
          <a-form-item label="Embed模型" required>
            <ModelSelect
              v-model="form.embeddingModel"
              model-type="embedding"
              placeholder="选择嵌入模型"
              @change="({ modelId }) => { selectedEmbeddingModelId = modelId }"
            />
          </a-form-item>
          <a-form-item label="知识图谱">
            <div style="display: flex; align-items: center; gap: 8px;">
              <a-switch v-model:checked="form.graphEnabled" />
              <a-tooltip title="开启后，文档入库完成时自动触发知识图谱抽取，提取实体和关系用于图谱检索增强（RAG Graph）。需配置 Neo4j。">
                <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
              </a-tooltip>
            </div>
          </a-form-item>
        </template>
      </a-form>
      </div>
      <template #footer>
        <a-button @click="showCreate = false">取消</a-button>
        <a-button
          v-if="form.type === 'dify'"
          :loading="testingDifyConnection"
          @click="handleTestDifyConnection"
        >
          测试连接
        </a-button>
        <a-button type="primary" :loading="submitting" @click="handleCreate">创建</a-button>
      </template>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined, ApartmentOutlined, DatabaseOutlined, CloudServerOutlined, ApiOutlined, FileTextOutlined, BlockOutlined, FontColorsOutlined, QuestionCircleOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getKnowledgeList, createKnowledge, deleteKnowledge, testDifyDraftConnection } from '../api/knowledge'
import ModelSelect from '../components/ModelSelect.vue'
import EntityCard from '../components/EntityCard.vue'
import LbManageHeader from '../components/common/LbManageHeader.vue'
import LbEmptyState from '../components/common/LbEmptyState.vue'
import { truncateText } from '../utils/format'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const searchText = ref('')
const showCreate = ref(false)
const submitting = ref(false)
const testingDifyConnection = ref(false)
const form = reactive({
  name: '',
  description: '',
  type: 'pg',
  embeddingModel: null,
  graphEnabled: false,
  difyConfig: { apiUrl: '', datasetId: '', token: '' },
})
/** 知识库仅持久化 modelId（与详情编辑一致），下拉 v-model 仍用复合值 */
const selectedEmbeddingModelId = ref(null)

function formatTokenCount(count) {
  if (!count || count <= 0) return '0'
  if (count >= 1000000) return (count / 1000000).toFixed(1) + 'M'
  if (count >= 1000) return (count / 1000).toFixed(1) + 'K'
  return String(count)
}

function openCreateModal() {
  form.embeddingModel = null
  selectedEmbeddingModelId.value = null
  form.graphEnabled = false
  form.difyConfig.apiUrl = ''
  form.difyConfig.datasetId = ''
  form.difyConfig.token = ''
  showCreate.value = true
}

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: 1, pageSize: 50 }
    if (searchText.value) params.name = searchText.value
    const res = await getKnowledgeList(params)
    list.value = res.data.records || []
  } finally {
    loading.value = false
  }
}

// 刷新按钮语义：清空搜索关键词，回到全量列表
function refresh() {
  searchText.value = ''
  loadData()
}

let searchDebounceTimer = null
watch(searchText, () => {
  clearTimeout(searchDebounceTimer)
  // 立刻置 loading，避免 debounce 的 300ms 窗口期里 list=[] + loading=false 触发空状态闪现
  loading.value = true
  searchDebounceTimer = setTimeout(() => loadData(), 300)
})

function handleDelete(id) {
  Modal.confirm({
    title: '确认删除知识库',
    content: '删除后知识库及其所有文档将无法恢复，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteKnowledge(id)
      message.success('删除成功')
      loadData()
    },
  })
}

async function handleCreate() {
  if (!form.name.trim()) {
    message.warning('请输入名称')
    return
  }
  if (form.type !== 'dify' && !selectedEmbeddingModelId.value) {
    message.warning('请选择 Embed 模型')
    return
  }
  if (form.type === 'dify' && (!form.difyConfig.apiUrl.trim() || !form.difyConfig.datasetId.trim() || !form.difyConfig.token.trim())) {
    message.warning('请填写 Dify API 地址、Dataset ID 和 Token')
    return
  }
  submitting.value = true
  try {
    await createKnowledge({
      ...form,
      embeddingModel: form.type === 'dify' ? null : selectedEmbeddingModelId.value,
      graphEnabled: form.type === 'dify' ? false : form.graphEnabled,
      config: '{}',
    })
    message.success('创建成功')
    showCreate.value = false
    form.name = ''
    form.description = ''
    form.type = 'pg'
    form.graphEnabled = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleTestDifyConnection() {
  if (!form.difyConfig.apiUrl.trim() || !form.difyConfig.datasetId.trim() || !form.difyConfig.token.trim()) {
    message.warning('请填写 Dify API 地址、Dataset ID 和 Token')
    return
  }
  testingDifyConnection.value = true
  try {
    await testDifyDraftConnection({ ...form.difyConfig })
    message.success('Dify Dataset 连接成功')
  } finally {
    testingDifyConnection.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>

.knowledge-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 14px;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: 100%;
  max-width: 100%;
}
.card-desc {
  font-size: 13px;
  color: var(--color-mute);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 6px;
  width: 100%;
  max-width: 100%;
}
.card-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
  color: var(--color-mute);
  align-items: center;
}
.card-stat-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: var(--color-canvas-soft-2);
  border-radius: 10px;
  line-height: 1;
}
.card-stat-icon {
  font-size: 11px;
  color: var(--color-mute);
}
.card-stat-value {
  font-weight: 600;
  color: var(--color-ink);
  font-variant-numeric: tabular-nums;
}
.card-type-icon-wrap {
  margin-left: auto;
  display: flex;
  align-items: center;
}
.card-type-icon {
  font-size: 15px;
  cursor: help;
}
.card-type-icon.pg {
  color: #3b82f6;
}
.card-type-icon.milvus {
  color: #8b5cf6;
}
.card-type-icon.dify {
  color: #14b8a6;
}
.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 60px 20px;
  color: var(--color-mute);
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  display: block;
}

/* 知识库类型选择卡片 */
.kb-type-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}
.kb-type-card {
  border: 1.5px solid #e4e4e7;
  border-radius: 10px;
  padding: 14px 16px;
  cursor: pointer;
  transition: all 0.15s;
  background: var(--color-canvas);
}
.kb-type-card:hover {
  border-color: var(--color-mute);
}
.kb-type-card.active {
  border-color: var(--color-ink);
  background: var(--color-canvas-soft);
  box-shadow: 0 0 0 1px var(--color-primary);
}
.kb-type-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.kb-type-icon {
  font-size: 18px;
  color: var(--color-mute);
  transition: color 0.15s;
}
.kb-type-card.active .kb-type-icon {
  color: var(--color-ink);
}
.kb-type-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}
.kb-type-desc {
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.5;
}
@media (max-width: 640px) {
  .kb-type-cards { grid-template-columns: 1fr; }
}
</style>
