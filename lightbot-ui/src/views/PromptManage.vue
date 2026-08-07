<template>
  <div class="page">
    <LbManageHeader
      title="提示词"
      v-model="searchText"
      search-placeholder="搜索提示词标识..."
      :refresh-disabled="loading"
      create-text="新建提示词"
      @refresh="refresh"
      @create="openDialog()"
    >
      <template #searchPrefix><SearchOutlined /></template>
      <template #actions>
        <button class="lb-btn lb-btn--accent lb-btn--accent--default" @click="router.push('/app/playground')">
          <PlayCircleOutlined /> Playground
        </button>
        <button class="lb-btn" @click="router.push('/app/prompt-templates')">
          <SettingOutlined /> 管理模板
        </button>
      </template>
    </LbManageHeader>

    <a-spin :spinning="loading" style="min-height: 300px; display: block;">
    <LbEntityGrid :min-card-width="320">
      <TransitionGroup name="lb-list">
        <EntityCard
          v-for="item in list"
          :key="item.id"
          type="prompt"
          :name="item.promptKey"
          @click="router.push(`/app/prompts/${item.promptKey}`)"
        >
          <template #icon>
            {{ (item.promptKey || 'P')[0].toUpperCase() }}
            <span class="card-version-badge" v-if="item.latestVersion">{{ item.latestVersion }}</span>
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
          <LbTagList v-if="item.tags" :tags="item.tags" />
        </EntityCard>
      </TransitionGroup>

      <LbEmptyState
        v-if="list.length === 0 && !loading"
        :icon="FileTextOutlined"
        :title="searchText ? '没有匹配的提示词' : '还没有提示词，点击右上角创建一个吧'"
      />
    </LbEntityGrid>
    </a-spin>

    <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:open="dialogVisible"
      :title="form.id ? '编辑提示词' : '新建提示词'"
      :width="560"
      :maskClosable="false"
    >
      <a-form :model="form" :label-col="{ flex: '0 0 100px' }">
        <a-form-item label="Prompt Key" required>
          <a-input
            v-model:value="form.promptKey"
            :disabled="!!form.id"
            :maxlength="100"
            show-count
            placeholder="请输入 Prompt Key（例如：customer_service，不超过 100 字）"
          />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="3" :maxlength="200" show-count placeholder="请输入 Prompt 用途描述（不超过 200 字）" />
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
  SearchOutlined, ReloadOutlined, FileTextOutlined,
  PlayCircleOutlined, SettingOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import TagInput from '../components/TagInput.vue'
import EntityCard from '../components/EntityCard.vue'
import LbDialogFooter from '../components/common/LbDialogFooter.vue'
import LbManageHeader from '../components/common/LbManageHeader.vue'
import LbEmptyState from '../components/common/LbEmptyState.vue'
import LbTagList from '../components/common/LbTagList.vue'
import LbEntityGrid from '../components/common/LbEntityGrid.vue'
import { getPrompts, createPrompt, updatePrompt, deletePrompt } from '../api/prompt'
import { useDebouncedWatch } from '../composables/useDebounce'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const searchText = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ id: null, promptKey: '', description: '', tags: '' })

onMounted(() => loadData())
useDebouncedWatch(searchText, () => loadData())

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

// 刷新按钮语义：清空搜索关键词，回到全量列表
function refresh() {
  searchText.value = ''
  loadData()
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
.card-version-badge {
  position: absolute;
  top: -6px;
  right: -6px;
  font-size: 10px;
  color: var(--color-link);
  background: var(--color-info-bg);
  border: 1px solid #b3d8ff;
  border-radius: 100px;
  padding: 0 5px;
  line-height: 16px;
  white-space: nowrap;
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
</style>
