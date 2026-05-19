<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">知识库</h1>
        <p class="page-desc">管理知识库，上传文档，基于 RAG 进行问答</p>
      </div>
      <button class="btn-primary" @click="showCreate = true">
        <el-icon><Plus /></el-icon> 新建知识库
      </button>
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
        </div>
        <div class="card-stats">
          <span>{{ k.documentCount || 0 }} 文档</span>
          <span>{{ k.chunkCount || 0 }} 分块</span>
        </div>
      </div>

      <div v-if="list.length === 0" class="empty-state">
        <p>还没有知识库，点击右上角创建一个吧</p>
      </div>
    </div>

    <!-- 创建弹窗 -->
    <el-dialog v-model="showCreate" title="新建知识库" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="知识库名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="知识库描述（可选）" />
        </el-form-item>
        <el-form-item label="Embed模型">
          <el-input v-model="form.embeddingModel" placeholder="text-embedding-3-small" />
        </el-form-item>
        <el-form-item label="分块大小">
          <el-input-number v-model="form.chunkSize" :min="100" :max="2000" :step="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getKnowledgeList, createKnowledge } from '../api/knowledge'

const router = useRouter()
const list = ref([])
const showCreate = ref(false)
const submitting = ref(false)

const form = reactive({
  name: '',
  description: '',
  embeddingModel: 'text-embedding-3-small',
  chunkSize: 512,
  chunkOverlap: 50,
})

async function loadData() {
  const res = await getKnowledgeList({ pageNum: 1, pageSize: 50 })
  list.value = res.data.records || []
}

async function handleCreate() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  submitting.value = true
  try {
    await createKnowledge({ ...form })
    ElMessage.success('创建成功')
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
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}
.btn-primary:hover {
  background: #27272a;
}

.knowledge-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
.knowledge-card {
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.knowledge-card:hover {
  border-color: #0070f3;
  box-shadow: 0 4px 12px rgba(0, 112, 243, 0.1);
}
.card-header {
  display: flex;
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
