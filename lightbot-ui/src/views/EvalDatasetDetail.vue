<template>
  <div class="page">
    <div class="page-header">
      <div>
        <button class="btn-back" @click="router.back()">
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
        <a-spin :spinning="itemsLoading">
        <div class="item-list" :class="{ 'item-list-min': itemsLoading }">
          <div v-for="item in items" :key="item.id" class="item-row">
            <div class="item-content">
              <div class="item-field" v-for="(val, key) in parseItemContent(item.dataContent)" :key="key">
                <span class="item-key">{{ key }}:</span>
                <a-tooltip v-if="val && val.length > 80" :title="val" placement="topLeft" :overlay-style="{ maxWidth: '500px' }">
                  <span class="item-val">{{ truncate(val, 80) }}</span>
                </a-tooltip>
                <span v-else class="item-val">{{ val }}</span>
              </div>
            </div>
            <a-tooltip title="删除">
              <button class="btn-icon-sm danger" @click="handleDeleteItem(item.id)">
                <DeleteOutlined />
              </button>
            </a-tooltip>
          </div>
          <div v-if="items.length === 0 && !itemsLoading" class="item-empty">暂无数据项，点击右上角添加</div>
        </div>
        </a-spin>
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
            <div class="version-body">
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
            <a-tooltip title="查看详情">
              <button class="btn-icon-sm" @click.stop="openVersionDetail(v)">
                <EyeOutlined />
              </button>
            </a-tooltip>
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
      <p style="color: var(--color-mute); margin-bottom: 16px;">
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

    <!-- 版本数据项详情弹窗 -->
    <a-modal
      v-model:open="versionDetailVisible"
      :title="`版本 ${versionDetailVersion} 数据项快照`"
      :width="720"
      :footer="null"
      :maskClosable="false"
    >
      <a-spin :spinning="versionDetailLoading">
        <div class="version-detail-list">
          <div v-for="item in versionDetailItems" :key="item.id" class="item-row">
            <div class="item-content">
              <div class="item-field" v-for="(val, key) in parseItemContent(item.dataContent)" :key="key">
                <span class="item-key">{{ key }}:</span>
                <a-tooltip v-if="val && val.length > 80" :title="val" placement="topLeft" :overlay-style="{ maxWidth: '500px' }">
                  <span class="item-val">{{ truncate(val, 80) }}</span>
                </a-tooltip>
                <span v-else class="item-val">{{ val }}</span>
              </div>
            </div>
          </div>
          <div v-if="versionDetailItems.length === 0 && !versionDetailLoading" class="item-empty">该版本无数据项</div>
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PlusOutlined, ArrowLeftOutlined, HistoryOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { formatDate as formatTime } from '../utils/format'
import {
  getEvalDataset,
  getEvalDatasetVersions, createEvalDatasetVersion, getEvalDatasetVersionItems,
  getEvalDatasetItems, createEvalDatasetItem, deleteEvalDatasetItem,
} from '../api/evalDataset'

const route = useRoute()
const router = useRouter()
const datasetId = route.params.id
const dataset = ref(null)
const versions = ref([])
const items = ref([])
const itemsLoading = ref(false)
const itemTotal = ref(0)
const pageNum = ref(1)
const pageSize = 20
const versionDialogVisible = ref(false)
const itemDialogVisible = ref(false)
const versionDetailVisible = ref(false)
const versionDetailLoading = ref(false)
const versionDetailVersion = ref('')
const versionDetailItems = ref([])
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
  itemsLoading.value = true
  try {
    const res = await getEvalDatasetItems({ datasetId, pageNum: pageNum.value, pageSize })
    items.value = res.data?.records || []
    itemTotal.value = res.data?.total || 0
  } finally {
    itemsLoading.value = false
  }
}

function openVersionDialog() {
  if (items.value.length === 0) {
    return message.warning('数据集暂无数据项，请先添加数据项再创建版本')
  }
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

async function openVersionDetail(v) {
  versionDetailVersion.value = v.version
  versionDetailVisible.value = true
  versionDetailLoading.value = true
  versionDetailItems.value = []
  try {
    const res = await getEvalDatasetVersionItems(v.id)
    versionDetailItems.value = res.data || []
  } catch {
    versionDetailItems.value = []
  } finally {
    versionDetailLoading.value = false
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

const FIELD_LABEL_MAP = {
  input: '用户输入',
  reference_output: '期望输出',
}

function parseItemContent(dataContent) {
  if (!dataContent) return {}
  try {
    const raw = typeof dataContent === 'string' ? JSON.parse(dataContent) : dataContent
    const result = {}
    for (const [key, val] of Object.entries(raw)) {
      result[FIELD_LABEL_MAP[key] || key] = val
    }
    return result
  } catch {
    return { content: dataContent }
  }
}

function truncate(str, len) {
  if (!str) return ''
  const s = String(str)
  return s.length > len ? s.substring(0, len) + '...' : s
}

</script>

<style scoped>
.page {
  padding: 20px calc(24px + var(--scroll-content-gap)) 20px 24px;
  height: 100vh;
  overflow-y: auto;
  background: var(--color-canvas-soft);
  scrollbar-gutter: stable;
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
  color: var(--color-mute);
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
}
.btn-back:hover { color: var(--color-link); }
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
.header-actions {
  display: flex;
  gap: 8px;
}
.btn-outline-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: var(--color-canvas);
  color: var(--color-ink);
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-outline-sm:hover {
  border-color: var(--color-link);
  color: var(--color-link);
}
.btn-primary-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 100px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary-sm:hover:not(:disabled) { background: #27272a; }
.btn-primary-sm:disabled { background: var(--color-hairline-strong); color: var(--color-mute); cursor: not-allowed; }
.btn-cancel {
  padding: 6px 16px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  cursor: pointer;
  font-size: 13px;
}
.btn-cancel:hover { border-color: var(--color-link); color: var(--color-link); }
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
  color: var(--color-mute);
  font-size: 12px;
  flex-shrink: 0;
}
.btn-icon-sm:hover { background: var(--color-canvas-soft-2); }
.btn-icon-sm.danger:hover { color: var(--color-error); background: var(--color-error-soft); }

.content-grid {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 16px;
}
.panel {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 16px;
  min-width: 0;
  overflow: hidden;
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
  color: var(--color-ink);
  margin: 0;
}
.panel-count {
  font-size: 13px;
  color: var(--color-mute);
}

.item-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: calc(100vh - 260px);
  overflow-y: auto;
}
.item-list-min {
  min-height: 120px;
}
.item-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
}
.item-row:hover { border-color: var(--color-hairline); }
.item-content { flex: 1; min-width: 0; }
.item-field {
  font-size: 13px;
  line-height: 1.6;
}
.item-key {
  color: var(--color-mute);
  margin-right: 4px;
}
.item-val {
  color: var(--color-ink);
}
.item-empty {
  text-align: center;
  padding: 40px;
  color: var(--color-mute);
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
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.version-item:hover {
  border-color: var(--color-hairline);
  box-shadow: 0px 2px 2px rgba(0,0,0,0.04), 0px 8px 8px -8px rgba(0,0,0,0.04), inset 0 0 0 1px rgba(0,0,0,0.08);
}
.version-body { flex: 1; min-width: 0; }
.version-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.version-tag {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}
.version-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--color-mute);
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 16px;
}
.dialog-footer-right { display: flex; gap: 8px; }
.version-detail-list {
  max-height: 60vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
