<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">Prompt</h1>
        <p class="page-desc">测试和调试你的AI提示词</p>
      </div>
      <div class="page-header-actions">
        <a-input
          v-model:value="searchText"
          placeholder="搜索 Prompt Key..."
          allow-clear
          style="width: 220px"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <button class="btn-outline" @click="loadData" :disabled="loading">
          <ReloadOutlined :spin="loading" /> 刷新
        </button>
        <button class="btn-outline" @click="router.push('/app/playground')">
          <PlayCircleOutlined /> Playground
        </button>
        <button class="btn-outline" @click="router.push('/app/prompt-templates')">
          <SettingOutlined /> 管理模板
        </button>
        <button class="btn-primary" @click="openDialog()">
          <PlusOutlined /> 新建 Prompt
        </button>
      </div>
    </div>

    <a-spin :spinning="loading">
    <div class="card-grid">
      <div
        v-for="item in list"
        :key="item.id"
        class="card-item"
        @click="router.push(`/app/prompts/${item.promptKey}`)"
      >
        <div class="card-top">
          <div class="card-icon-wrapper">
            <div class="card-icon">P</div>
            <span class="card-version-badge" v-if="item.latestVersion">{{ item.latestVersion }}</span>
          </div>
          <div class="card-info">
            <h3>{{ item.promptKey }}</h3>
          </div>
          <div class="card-actions" @click.stop>
            <button class="btn-icon" @click="openDialog(item)"><EditOutlined /></button>
            <button class="btn-icon danger" @click="handleDelete(item.id)"><DeleteOutlined /></button>
          </div>
        </div>
        <p class="card-desc">{{ item.description || '暂无描述' }}</p>
        <div class="card-tags" v-if="item.tags">
          <a-tag v-for="tag in item.tags.split(',')" :key="tag" color="blue">{{ tag.trim() }}</a-tag>
        </div>
      </div>

      <div v-if="list.length === 0 && !loading" class="empty-state">
        <FileTextOutlined class="empty-icon" />
        <p v-if="searchText">没有匹配的 Prompt</p>
        <p v-else>还没有 Prompt，点击右上角创建一个吧</p>
      </div>
    </div>
    </a-spin>

    <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:open="dialogVisible"
      :title="form.id ? '编辑 Prompt' : '新建 Prompt'"
      :width="560"
      :footer="null"
      :maskClosable="false"
    >
      <a-form :model="form" :label-col="{ span: 5 }">
        <a-form-item label="Prompt Key" required>
          <a-input
            v-model:value="form.promptKey"
            :disabled="!!form.id"
            :maxlength="100"
            show-count
            placeholder="如: customer_service (不超过100字)"
          />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="3" :maxlength="200" show-count placeholder="Prompt 的用途描述 (不超过200字)" />
        </a-form-item>
        <a-form-item label="标签">
          <TagInput v-model="form.tags" />
        </a-form-item>
      </a-form>
      <div class="dialog-footer">
        <div></div>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="dialogVisible = false">取消</button>
          <button class="btn-primary-sm" :disabled="submitting" @click="handleSubmit">
            {{ submitting ? '提交中...' : '确定' }}
          </button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  PlusOutlined, EditOutlined, DeleteOutlined,
  SearchOutlined, ReloadOutlined, FileTextOutlined,
  PlayCircleOutlined, SettingOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import TagInput from '../components/TagInput.vue'
import { getPrompts, createPrompt, updatePrompt, deletePrompt } from '../api/prompt'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const searchText = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ id: null, promptKey: '', description: '', tags: '' })

onMounted(() => loadData())
watch(searchText, () => loadData())

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: 1, pageSize: 100 }
    if (searchText.value) params.keyword = searchText.value
    const res = await getPrompts(params)
    list.value = res.data?.records || []
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      promptKey: row.promptKey || '',
      description: row.description || '',
      tags: row.tags || '',
    })
  } else {
    Object.assign(form, { id: null, promptKey: '', description: '', tags: '' })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.promptKey.trim()) return message.warning('请输入 Prompt Key')
  submitting.value = true
  try {
    if (form.id) {
      await updatePrompt(form.id, { description: form.description, tags: form.tags })
      message.success('更新成功')
    } else {
      await createPrompt({ promptKey: form.promptKey, description: form.description, tags: form.tags })
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
      await deletePrompt(id)
      message.success('删除成功')
      loadData()
    },
  })
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
  background: #171717;
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
.btn-primary-sm {
  padding: 6px 16px;
  background: #171717;
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
  border: 1px solid #d9d9d9;
  border-radius: 100px;
  cursor: pointer;
  font-size: 13px;
}
.btn-cancel:hover { border-color: #0070f3; color: #0070f3; }
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
.btn-icon:hover { background: #f5f5f5; }
.btn-icon.danger:hover { color: #ee0000; background: #f7d4d6; }
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.card-item {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.card-item:hover {
  border-color: #0070f3;
  box-shadow: 0px 2px 2px rgba(0,0,0,0.04), 0px 8px 8px -8px rgba(0,0,0,0.04), inset 0 0 0 1px rgba(0,0,0,0.08);
}
.card-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.card-icon-wrapper {
  position: relative;
  flex-shrink: 0;
}
.card-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
}
.card-version-badge {
  position: absolute;
  top: -6px;
  right: -6px;
  font-size: 10px;
  color: #0070f3;
  background: #e8f4ff;
  border: 1px solid #b3d8ff;
  border-radius: 100px;
  padding: 0 5px;
  line-height: 16px;
  white-space: nowrap;
}
.card-info { flex: 1; }
.card-info h3 {
  font-size: 16px;
  font-weight: 600;
  color: #171717;
  margin: 0;
}
.card-actions { display: flex; gap: 4px; }
.card-desc {
  font-size: 13px;
  color: #71717a;
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
  color: #a1a1aa;
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
</style>
