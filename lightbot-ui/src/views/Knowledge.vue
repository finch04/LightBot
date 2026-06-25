<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">知识库</h1>
        <p class="page-desc">管理知识库，上传文档，基于 RAG 进行问答</p>
      </div>
      <div class="page-header-actions">
        <a-input
          v-model:value="searchText"
          placeholder="搜索知识库名称..."
          allow-clear
          style="width: 220px"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <button class="btn-outline" @click="loadData" :disabled="loading">
          <ReloadOutlined :spin="loading" /> 刷新
        </button>
        <button class="btn-outline" @click="router.push('/app/graph')">
          <ApartmentOutlined /> 知识图谱
        </button>
        <button class="btn-primary" @click="openCreateModal">
          <PlusOutlined /> 新建知识库
        </button>
      </div>
    </div>

    <a-spin :spinning="loading">
    <div class="knowledge-grid">
      <div
        v-for="k in list"
        :key="k.id"
        class="knowledge-card"
        @click="router.push(`/app/knowledge/${k.id}`)"
      >
        <div class="card-header">
          <div class="card-icon">{{ (k.name || 'K')[0].toUpperCase() }}</div>
          <div class="card-info">
            <h3 class="card-title">{{ k.name }}</h3>
            <a-tooltip v-if="k.description" :title="k.description" placement="topLeft" :overlay-style="{ maxWidth: '400px' }">
              <p class="card-desc">{{ truncateText(k.description, 50) }}</p>
            </a-tooltip>
            <p v-else class="card-desc">暂无描述</p>
          </div>
          <a-tooltip title="删除知识库">
            <button class="btn-icon danger" @click.stop="handleDelete(k.id)">
              <DeleteOutlined />
            </button>
          </a-tooltip>
        </div>
        <div class="card-stats">
          <span class="card-stat-item">
            <FileTextOutlined class="card-stat-icon" />
            <span class="card-stat-value">{{ k.documentCount || 0 }}</span>
            <span class="card-stat-label">文档</span>
          </span>
          <span class="card-stat-item">
            <BlockOutlined class="card-stat-icon" />
            <span class="card-stat-value">{{ k.chunkCount || 0 }}</span>
            <span class="card-stat-label">分片</span>
          </span>
          <span class="card-stat-item">
            <NumberOutlined class="card-stat-icon" />
            <span class="card-stat-value">{{ formatTokenCount(k.totalTokens) }}</span>
            <span class="card-stat-label">Token</span>
          </span>
          <span v-if="k.type" class="card-type-icon-wrap">
            <a-tooltip :title="k.type === 'milvus' ? 'Milvus' : 'PostgreSQL'">
              <CloudServerOutlined v-if="k.type === 'milvus'" class="card-type-icon milvus" />
              <DatabaseOutlined v-else class="card-type-icon pg" />
            </a-tooltip>
          </span>
        </div>
      </div>

      <div v-if="list.length === 0 && !loading" class="empty-state">
        <DatabaseOutlined class="empty-icon" />
        <p v-if="searchText">没有匹配的知识库</p>
        <p v-else>还没有知识库，点击右上角创建一个吧</p>
      </div>
    </div>
    </a-spin>

    <!-- 创建弹窗 -->
    <a-modal v-model:open="showCreate" title="新建知识库" :width="480" @ok="handleCreate" :confirm-loading="submitting" :maskClosable="false">
      <a-form :model="form" :label-col="{ span: 6 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" placeholder="知识库名称（不超过30字）" :maxlength="30" show-count />
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
          </div>
        </a-form-item>
        <a-form-item label="Embed模型" required>
          <ModelSelect v-model="form.embeddingModel" model-type="embedding" placeholder="选择嵌入模型" @change="onEmbeddingModelChange" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined, ApartmentOutlined, DatabaseOutlined, CloudServerOutlined, FileTextOutlined, BlockOutlined, NumberOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getKnowledgeList, createKnowledge, deleteKnowledge } from '../api/knowledge'
import ModelSelect from '../components/ModelSelect.vue'
import { truncateText } from '../utils/format'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const searchText = ref('')
const showCreate = ref(false)
const submitting = ref(false)
const selectedEmbeddingModelId = ref(null)
const form = reactive({
  name: '',
  description: '',
  type: 'pg',
  embeddingModel: null,
})

function formatTokenCount(count) {
  if (!count || count <= 0) return '0'
  if (count >= 1000000) return (count / 1000000).toFixed(1) + 'M'
  if (count >= 1000) return (count / 1000).toFixed(1) + 'K'
  return String(count)
}

function openCreateModal() {
  form.embeddingModel = null
  selectedEmbeddingModelId.value = null
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

let searchDebounceTimer = null
watch(searchText, () => {
  clearTimeout(searchDebounceTimer)
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
  if (!form.embeddingModel) {
    message.warning('请选择 Embed 模型')
    return
  }
  submitting.value = true
  try {
    await createKnowledge({ ...form, embeddingModel: selectedEmbeddingModelId.value, config: '{}' })
    message.success('创建成功')
    showCreate.value = false
    form.name = ''
    form.description = ''
    form.type = 'pg'
    loadData()
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.page {
  padding: 32px;
  height: 100vh;
  overflow-y: auto;
  background: #fafafa;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32px;
}
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 4px;
}
.page-desc {
  font-size: 14px;
  color: #71717a;
}
.btn-primary {
  display: flex;
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
  transition: background 0.15s;
}
.btn-primary:hover {
  background: #27272a;
}
.page-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.btn-outline {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: transparent;
  border: 1px solid #d9d9d9;
  border-radius: 100px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-outline:hover {
  border-color: #0070f3;
  color: #0070f3;
}

.knowledge-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
.knowledge-card {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.knowledge-card:hover {
  border-color: #0070f3;
  box-shadow: 0px 2px 2px rgba(0,0,0,0.04), 0px 8px 8px -8px rgba(0,0,0,0.04), inset 0 0 0 1px rgba(0,0,0,0.08);
}
.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.card-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
  flex-shrink: 0;
}
.card-info {
  flex: 1;
  min-width: 0;
}
.btn-icon {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #71717a;
}
.btn-icon:hover {
  background: #f5f5f5;
}
.btn-icon.danger:hover {
  color: #ee0000;
  background: #f7d4d6;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 6px;
}
.card-desc {
  font-size: 13px;
  color: #71717a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-stats {
  display: flex;
  gap: 6px;
  font-size: 12px;
  color: #71717a;
  align-items: center;
}
.card-stat-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: #f4f4f5;
  border-radius: 10px;
  line-height: 1;
}
.card-stat-icon {
  font-size: 11px;
  color: #a1a1aa;
}
.card-stat-value {
  font-weight: 600;
  color: #3f3f46;
  font-variant-numeric: tabular-nums;
}
.card-stat-label {
  color: #a1a1aa;
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
.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 60px 20px;
  color: #a1a1aa;
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  display: block;
}

/* 知识库类型选择卡片 */
.kb-type-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 100%;
}
.kb-type-card {
  border: 1.5px solid #e4e4e7;
  border-radius: 10px;
  padding: 14px 16px;
  cursor: pointer;
  transition: all 0.15s;
  background: #fff;
}
.kb-type-card:hover {
  border-color: #a1a1aa;
}
.kb-type-card.active {
  border-color: #171717;
  background: #fafafa;
  box-shadow: 0 0 0 1px #171717;
}
.kb-type-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.kb-type-icon {
  font-size: 18px;
  color: #a1a1aa;
  transition: color 0.15s;
}
.kb-type-card.active .kb-type-icon {
  color: #171717;
}
.kb-type-title {
  font-size: 14px;
  font-weight: 600;
  color: #171717;
}
.kb-type-desc {
  font-size: 12px;
  color: #71717a;
  line-height: 1.5;
}
</style>
