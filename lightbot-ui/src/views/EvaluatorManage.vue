<template>
  <div class="page">
    <LbManageHeader
      title="评估器管理"
      v-model="searchText"
      search-placeholder="搜索评估器名称..."
      :refresh-disabled="loading"
      create-text="新建评估器"
      @refresh="refresh"
      @create="openDialog()"
    >
      <template #searchPrefix><SearchOutlined /></template>
      <template #actions>
        <a-tooltip title="示例评估器">
          <button class="lb-btn" @click="openExampleModal">
            <SnippetsOutlined />
          </button>
        </a-tooltip>
        <button class="lb-btn" @click="router.push('/app/eval/datasets')">
          <ArrowLeftOutlined /> 返回评测
        </button>
        <button class="lb-btn" @click="router.push('/app/eval/experiments')">
          <ExperimentOutlined /> 实验
        </button>
      </template>
    </LbManageHeader>

    <a-spin :spinning="loading">
    <div class="card-grid">
      <EntityCard
        v-for="item in list"
        :key="item.id"
        type="evaluator"
        :name="item.name"
        @click="router.push(`/app/eval/evaluators/${item.id}`)"
      >
        <template #info>
          <a-tooltip :title="item.name"><h3>{{ item.name }}</h3></a-tooltip>
          <span class="card-type" v-if="item.latestVersion">{{ item.latestVersion }}</span>
        </template>
        <template #actions>
          <a-tooltip title="编辑">
            <button class="btn-icon" @click="openDialog(item)"><EditOutlined /></button>
          </a-tooltip>
          <a-tooltip title="删除">
            <button class="btn-icon danger" @click="handleDelete(item.id)"><DeleteOutlined /></button>
          </a-tooltip>
        </template>
        <p class="card-desc">{{ item.description || '暂无描述' }}</p>
        <LbTagList v-if="item.tags" :tags="item.tags" color="purple" />
      </EntityCard>

      <LbEmptyState
        v-if="list.length === 0 && !loading"
        :icon="AuditOutlined"
        :title="searchText ? '没有匹配的评估器' : '还没有评估器，点击右上角创建一个吧'"
      />
    </div>
    </a-spin>

    <!-- 示例评估器弹窗 -->
    <a-modal
      v-model:open="exampleModalVisible"
      title="示例评估器"
      :width="640"
      :footer="null"
      :maskClosable="false"
    >
      <div class="dialog-scroll-body">
      <div class="example-intro">选择一个内置示例，快速创建评估器并学习评估 Prompt 的编写方式</div>
      <div class="example-list">
        <div v-for="ex in examples" :key="ex.key" class="example-card">
          <div class="example-card-header">
            <span class="example-name">{{ ex.name }}</span>
            <a-button type="primary" size="small" :loading="exampleCreating === ex.key" @click="handleCreateExample(ex.key)">
              生成
            </a-button>
          </div>
          <div class="example-desc-text">{{ ex.description }}</div>
          <div class="example-tags">
            <a-tag v-for="tag in ex.tags" :key="tag" color="purple">{{ tag }}</a-tag>
          </div>
        </div>
      </div>
      </div>
    </a-modal>

    <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:open="dialogVisible"
      :title="form.id ? '编辑评估器' : '新建评估器'"
      :width="560"
      :maskClosable="false"
    >
      <a-form :model="form" :label-col="{ flex: '0 0 80px' }">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" placeholder="请输入评估器名称（例如：准确性评估器，不超过 50 字）" :maxlength="50" show-count />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="3" :maxlength="200" show-count placeholder="请输入评估器用途描述（不超过 200 字）" />
        </a-form-item>
        <a-form-item label="标签">
          <TagInput v-model="form.tags" />
        </a-form-item>
      </a-form>
      <template #footer>
        <LbDialogFooter
          :loading="submitting"
          @cancel="dialogVisible = false"
          @confirm="handleSubmit"
        />
      </template>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  PlusOutlined, EditOutlined, DeleteOutlined,
  SearchOutlined, ReloadOutlined, AuditOutlined, ExperimentOutlined,
  SnippetsOutlined, ArrowLeftOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import TagInput from '../components/TagInput.vue'
import EntityCard from '../components/EntityCard.vue'
import LbDialogFooter from '../components/common/LbDialogFooter.vue'
import LbManageHeader from '../components/common/LbManageHeader.vue'
import LbEmptyState from '../components/common/LbEmptyState.vue'
import LbTagList from '../components/common/LbTagList.vue'
import {
  getEvaluators, createEvaluator, updateEvaluator, deleteEvaluator,
  listEvaluatorExamples, createFromEvaluatorExample,
} from '../api/evaluator'
import { useDebouncedWatch } from '../composables/useDebounce'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const searchText = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ id: null, name: '', description: '', tags: '' })
const exampleModalVisible = ref(false)
const examples = ref([])
const exampleCreating = ref(null)

onMounted(() => loadData())
useDebouncedWatch(searchText, () => loadData())

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: 1, pageSize: 100 }
    if (searchText.value) params.keyword = searchText.value
    const res = await getEvaluators(params)
    list.value = res.data?.records || []
  } finally {
    loading.value = false
  }
}

// 刷新按钮语义：清空搜索关键词，回到全量列表
function refresh() {
  searchText.value = ''
  loadData()
}

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      description: row.description || '',
      tags: row.tags || '',
    })
  } else {
    Object.assign(form, { id: null, name: '', description: '', tags: '' })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.name.trim()) return message.warning('请输入名称')
  submitting.value = true
  try {
    if (form.id) {
      await updateEvaluator(form.id, { name: form.name, description: form.description, tags: form.tags })
      message.success('更新成功')
    } else {
      await createEvaluator({ name: form.name, description: form.description, tags: form.tags })
      message.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

function handleDelete(id) {
  Modal.confirm({
    title: '确认删除',
    content: '删除后将无法恢复，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteEvaluator(id)
      message.success('删除成功')
      loadData()
    },
  })
}

async function openExampleModal() {
  exampleModalVisible.value = true
  try {
    const res = await listEvaluatorExamples()
    examples.value = res.data || []
  } catch {
    examples.value = []
  }
}

async function handleCreateExample(key) {
  exampleCreating.value = key
  try {
    await createFromEvaluatorExample(key)
    message.success('示例评估器创建成功')
    exampleModalVisible.value = false
    loadData()
  } finally {
    exampleCreating.value = null
  }
}
</script>

<style scoped>
.page {
  padding: var(--space-xl);
  padding-right: calc(var(--space-xl) + var(--scroll-content-gap));
  height: var(--app-content-height);
  overflow-y: auto;
  background: var(--color-canvas-soft);
  scrollbar-gutter: stable;
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
  color: var(--color-ink);
  margin-bottom: 4px;
}
.page-desc {
  font-size: 14px;
  color: var(--color-mute);
}
.page-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.btn-primary {
  display: flex;
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
.btn-primary:hover { background: #27272a; }
.btn-outline {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-outline:hover {
  border-color: var(--color-link);
  color: var(--color-link);
}
.btn-primary-sm {
  padding: 6px 16px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 100px;
  cursor: pointer;
  font-size: 13px;
}
.btn-primary-sm:hover { background: #27272a; }
.btn-primary-sm:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-cancel {
  padding: 6px 16px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  cursor: pointer;
  font-size: 13px;
}
.btn-cancel:hover { border-color: var(--color-link); color: var(--color-link); }
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.card-type {
  font-size: 12px;
  color: var(--color-link);
  background: var(--color-info-bg);
  padding: 2px 8px;
  border-radius: 100px;
}
.card-desc {
  font-size: 13px;
  color: var(--color-mute);
  margin-bottom: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-tags { display: flex; gap: 4px; flex-wrap: wrap; }
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
.dialog-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 16px;
}
.dialog-footer-right { display: flex; gap: 8px; }
.example-intro {
  font-size: 13px;
  color: var(--color-mute);
  margin-bottom: 16px;
}
.example-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.example-card {
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 16px;
}
.example-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.example-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
}
.example-desc-text {
  font-size: 13px;
  color: var(--color-mute);
  margin-bottom: 10px;
  line-height: 1.5;
}
.example-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
