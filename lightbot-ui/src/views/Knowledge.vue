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
        <button class="btn-outline" @click="loadData">
          <ReloadOutlined /> 刷新
        </button>
        <button class="btn-primary" @click="openCreateModal">
          <PlusOutlined /> 新建知识库
        </button>
      </div>
    </div>

    <div class="knowledge-grid">
      <div
        v-for="k in list"
        :key="k.id"
        class="knowledge-card"
        @click="router.push(`/knowledge/${k.id}`)"
      >
        <div class="card-header">
          <div class="card-icon">K</div>
          <div class="card-info">
            <h3 class="card-title">{{ k.name }}</h3>
            <p class="card-desc">{{ k.description || '暂无描述' }}</p>
          </div>
          <a-tooltip title="删除知识库">
            <button class="btn-icon danger" @click.stop="handleDelete(k.id)">
              <DeleteOutlined />
            </button>
          </a-tooltip>
        </div>
        <div class="card-stats">
          <span>{{ k.documentCount || 0 }} 文档</span>
          <span>{{ k.chunkCount || 0 }} 分块</span>
        </div>
      </div>

      <div v-if="list.length === 0" class="empty-state">
        <p v-if="searchText">没有匹配的知识库</p>
        <p v-else>还没有知识库，点击右上角创建一个吧</p>
      </div>
    </div>

    <!-- 创建弹窗 -->
    <a-modal v-model:open="showCreate" title="新建知识库" :width="480" @ok="handleCreate" :confirm-loading="submitting" :maskClosable="false">
      <a-form :model="form" :label-col="{ span: 6 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" placeholder="知识库名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="3" placeholder="知识库描述（可选）" />
        </a-form-item>
        <a-form-item label="Embed模型" required>
          <a-select v-model:value="form.embeddingModel" placeholder="选择嵌入模型" style="width: 100%">
            <a-select-option v-for="m in embeddingModels" :key="m.id" :value="m.modelId">
              {{ m.name }} ({{ m.modelId }})
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="分块大小">
          <a-input-number v-model:value="form.chunkSize" :min="100" :max="2000" :step="100" style="width: 100%" />
        </a-form-item>
        <a-form-item label="RAG Top K">
          <a-input-number v-model:value="form.ragTopK" :min="1" :max="20" style="width: 100%" />
        </a-form-item>
        <a-form-item label="RAG 相似度阈值">
          <a-input-number v-model:value="form.ragThreshold" :min="0" :max="1" :step="0.05" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getKnowledgeList, createKnowledge, deleteKnowledge } from '../api/knowledge'
import { getModelsByType } from '../api/model'

const router = useRouter()
const list = ref([])
const searchText = ref('')
const showCreate = ref(false)
const submitting = ref(false)
const embeddingModels = ref([])

const form = reactive({
  name: '',
  description: '',
  embeddingModel: null,
  chunkSize: 512,
  chunkOverlap: 50,
  ragTopK: 5,
  ragThreshold: 0.7,
})

async function openCreateModal() {
  showCreate.value = true
  try {
    const res = await getModelsByType('embedding')
    embeddingModels.value = res.data || []
  } catch { /* ignore */ }
}

async function loadData() {
  const params = { pageNum: 1, pageSize: 50 }
  if (searchText.value) params.name = searchText.value
  const res = await getKnowledgeList(params)
  list.value = res.data.records || []
}

watch(searchText, () => loadData())

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
    const config = JSON.stringify({ ragTopK: form.ragTopK, ragThreshold: form.ragThreshold })
    await createKnowledge({ ...form, config })
    message.success('创建成功')
    showCreate.value = false
    form.name = ''
    form.description = ''
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
  background: linear-gradient(135deg, #007cf0, #00dfd8);
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
  margin-bottom: 4px;
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
  gap: 16px;
  font-size: 13px;
  color: #a1a1aa;
}
.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 60px 20px;
  color: #a1a1aa;
}
</style>
