<template>
  <div class="page">
    <div class="page-header">
      <div>
        <button class="btn-back" @click="router.push('/eval/datasets')">
          <ArrowLeftOutlined /> 返回
        </button>
        <h1 class="page-title">{{ dataset?.name || '评测集详情' }}</h1>
        <p class="page-desc">{{ dataset?.description || '' }}</p>
      </div>
      <div class="header-actions">
        <button class="btn-outline-sm" @click="openVersionDialog()">
          <HistoryOutlined /> 新建版本
        </button>
        <button class="btn-primary-sm" @click="openItemDialog()">
          <PlusOutlined /> 添加数据项
        </button>
      </div>
    </div>

    <div class="content-grid">
      <!-- 左侧：数据项列表 -->
      <div class="panel">
        <div class="panel-header">
          <h3>数据项列表</h3>
          <span class="panel-count">{{ items.length }} 条</span>
        </div>
        <div class="item-list">
          <div v-for="item in items" :key="item.id" class="item-row">
            <div class="item-content">
              <div class="item-field" v-for="(val, key) in parseItemContent(item.dataContent)" :key="key">
                <span class="item-key">{{ key }}:</span>
                <span class="item-val">{{ truncate(val, 80) }}</span>
              </div>
            </div>
            <button class="btn-icon-sm danger" @click="handleDeleteItem(item.id)">
              <DeleteOutlined />
            </button>
          </div>
          <div v-if="items.length === 0" class="item-empty">暂无数据项，点击右上角添加</div>
        </div>
        <div v-if="itemTotal > 0" class="item-pagination">
          <a-pagination
            v-model:current="pageNum"
            :page-size="pageSize"
            :total="itemTotal"
            size="small"
            show-less-items
            :show-total="(total) => `共 ${total} 条`"
            @change="loadItems"
          />
        </div>
      </div>

      <!-- 右侧：版本列表 -->
      <div class="panel">
        <div class="panel-header">
          <h3>版本列表</h3>
        </div>
        <div class="version-list">
          <div v-for="v in versions" :key="v.id" class="version-item">
            <div class="version-info">
              <span class="version-tag">{{ v.version }}</span>
              <a-tag :color="v.status === 'published' ? 'green' : 'blue'" size="small">
                {{ v.status === 'published' ? '已发布' : '草稿' }}
              </a-tag>
            </div>
            <div class="version-meta">
              <span>{{ v.dataCount || 0 }} 条数据</span>
              <span>{{ formatTime(v.createTime) }}</span>
            </div>
          </div>
          <div v-if="versions.length === 0" class="item-empty">暂无版本</div>
        </div>
      </div>
    </div>

    <!-- 新建版本弹窗 -->
    <a-modal
      v-model:open="versionDialogVisible"
      title="新建版本"
      :width="480"
      :footer="null"
      :maskClosable="false"
    >
      <p style="color: #71717a; margin-bottom: 16px;">
        将当前数据集中的所有数据项快照为一个新版本。
      </p>
      <a-form :model="versionForm" :label-col="{ span: 5 }">
        <a-form-item label="版本号" required>
          <a-input v-model:value="versionForm.version" placeholder="如: v1.0" />
        </a-form-item>
      </a-form>
      <div class="dialog-footer">
        <div></div>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="versionDialogVisible = false">取消</button>
          <button class="btn-primary-sm" :disabled="submitting" @click="handleCreateVersion">
            {{ submitting ? '提交中...' : '确定' }}
          </button>
        </div>
      </div>
    </a-modal>

    <!-- 添加数据项弹窗 -->
    <a-modal
      v-model:open="itemDialogVisible"
      title="添加数据项"
      :width="640"
      :footer="null"
      :maskClosable="false"
    >
      <a-form :model="itemForm" :label-col="{ span: 5 }">
        <a-form-item label="输入内容" required>
          <a-textarea v-model:value="itemForm.input" :rows="4" :maxlength="2000" show-count placeholder="用户输入内容 (不超过2000字)" />
        </a-form-item>
        <a-form-item label="期望输出">
          <a-textarea v-model:value="itemForm.referenceOutput" :rows="4" :maxlength="2000" show-count placeholder="期望的输出结果 (不超过2000字)" />
        </a-form-item>
        <a-form-item label="扩展数据">
          <a-textarea
            v-model:value="itemForm.extraData"
            :rows="3"
            :maxlength="500"
            show-count
            placeholder='JSON 格式，如: {"category":"faq","difficulty":"easy"} (不超过500字)'
          />
        </a-form-item>
      </a-form>
      <div class="dialog-footer">
        <div></div>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="itemDialogVisible = false">取消</button>
          <button class="btn-primary-sm" :disabled="submitting" @click="handleCreateItem">
            {{ submitting ? '提交中...' : '确定' }}
          </button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PlusOutlined, ArrowLeftOutlined, HistoryOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getEvalDataset,
  getEvalDatasetVersions, createEvalDatasetVersion,
  getEvalDatasetItems, createEvalDatasetItem, deleteEvalDatasetItem,
} from '../api/evalDataset'

const route = useRoute()
const router = useRouter()
const datasetId = route.params.id
const dataset = ref(null)
const versions = ref([])
const items = ref([])
const itemTotal = ref(0)
const pageNum = ref(1)
const pageSize = 20
const versionDialogVisible = ref(false)
const itemDialogVisible = ref(false)
const submitting = ref(false)

const versionForm = reactive({ version: '' })
const itemForm = reactive({ input: '', referenceOutput: '', extraData: '' })

onMounted(async () => {
  await loadDataset()
  await Promise.all([loadVersions(), loadItems()])
})

async function loadDataset() {
  const res = await getEvalDataset(datasetId)
  dataset.value = res.data
}

async function loadVersions() {
  const res = await getEvalDatasetVersions(datasetId)
  versions.value = res.data || []
}

async function loadItems() {
  const res = await getEvalDatasetItems({ datasetId, pageNum: pageNum.value, pageSize })
  items.value = res.data?.records || []
  itemTotal.value = res.data?.total || 0
}

function openVersionDialog() {
  Object.assign(versionForm, { version: '' })
  versionDialogVisible.value = true
}

async function handleCreateVersion() {
  if (!versionForm.version.trim()) return message.warning('请输入版本号')
  submitting.value = true
  try {
    await createEvalDatasetVersion({
      datasetId,
      version: versionForm.version,
    })
    message.success('版本创建成功')
    versionDialogVisible.value = false
    loadVersions()
  } finally {
    submitting.value = false
  }
}

function openItemDialog() {
  Object.assign(itemForm, { input: '', referenceOutput: '', extraData: '' })
  itemDialogVisible.value = true
}

async function handleCreateItem() {
  if (!itemForm.input.trim()) return message.warning('请输入内容')
  submitting.value = true
  try {
    const dataContent = { input: itemForm.input }
    if (itemForm.referenceOutput.trim()) {
      dataContent.reference_output = itemForm.referenceOutput
    }
    if (itemForm.extraData.trim()) {
      try {
        Object.assign(dataContent, JSON.parse(itemForm.extraData))
      } catch {
        return message.warning('扩展数据 JSON 格式不正确')
      }
    }
    await createEvalDatasetItem({
      datasetId,
      dataContent: JSON.stringify(dataContent),
    })
    message.success('数据项添加成功')
    itemDialogVisible.value = false
    loadItems()
  } finally {
    submitting.value = false
  }
}

function handleDeleteItem(id) {
  Modal.confirm({
    title: '确认删除',
    content: '删除后将无法恢复，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteEvalDatasetItem(id)
      message.success('删除成功')
      loadItems()
    },
  })
}

function parseItemContent(dataContent) {
  if (!dataContent) return {}
  try {
    return typeof dataContent === 'string' ? JSON.parse(dataContent) : dataContent
  } catch {
    return { content: dataContent }
  }
}

function truncate(str, len) {
  if (!str) return ''
  const s = String(str)
  return s.length > len ? s.substring(0, len) + '...' : s
}

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.page {
  padding: 20px 24px;
  height: 100vh;
  overflow-y: auto;
  background: #fafafa;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.btn-back {
  background: none;
  border: none;
  color: #71717a;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
}
.btn-back:hover { color: #0070f3; }
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
.header-actions {
  display: flex;
  gap: 8px;
}
.btn-outline-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: #fff;
  color: #171717;
  border: 1px solid #d4d4d8;
  border-radius: 100px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-outline-sm:hover {
  border-color: #0070f3;
  color: #0070f3;
}
.btn-primary-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: #171717;
  color: #fff;
  border: none;
  border-radius: 100px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary-sm:hover:not(:disabled) { background: #27272a; }
.btn-primary-sm:disabled { background: #d4d4d8; cursor: not-allowed; }
.btn-cancel {
  padding: 6px 16px;
  background: transparent;
  border: 1px solid #d9d9d9;
  border-radius: 100px;
  cursor: pointer;
  font-size: 13px;
}
.btn-cancel:hover { border-color: #0070f3; color: #0070f3; }
.btn-icon-sm {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #71717a;
  font-size: 12px;
  flex-shrink: 0;
}
.btn-icon-sm:hover { background: #f5f5f5; }
.btn-icon-sm.danger:hover { color: #ee0000; background: #f7d4d6; }

.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.panel {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 8px;
  padding: 16px;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.panel-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #171717;
  margin: 0;
}
.panel-count {
  font-size: 13px;
  color: #a1a1aa;
}

.item-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: calc(100vh - 260px);
  overflow-y: auto;
}
.item-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #f5f5f5;
  border-radius: 8px;
}
.item-row:hover { border-color: #e4e4e7; }
.item-content { flex: 1; min-width: 0; }
.item-field {
  font-size: 13px;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-key {
  color: #71717a;
  margin-right: 4px;
}
.item-val {
  color: #171717;
}
.item-empty {
  text-align: center;
  padding: 40px;
  color: #a1a1aa;
  font-size: 13px;
}
.item-pagination {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}

.version-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: calc(100vh - 260px);
  overflow-y: auto;
}
.version-item {
  padding: 10px 12px;
  border: 1px solid #f5f5f5;
  border-radius: 8px;
}
.version-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.version-tag {
  font-size: 14px;
  font-weight: 600;
  color: #171717;
}
.version-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #a1a1aa;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 16px;
}
.dialog-footer-right { display: flex; gap: 8px; }
</style>
