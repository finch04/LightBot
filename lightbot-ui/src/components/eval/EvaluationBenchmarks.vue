<template>
  <div class="eval-benchmarks">
    <div class="eval-desc">
      管理 RAG 评估的基准数据集，支持 AI 自动生成或上传 JSONL 格式文件
    </div>
    <div class="benchmarks-toolbar">
      <a-button size="small" :loading="loading" @click="loadBenchmarks">
        <template #icon><ReloadOutlined /></template> 刷新
      </a-button>
      <a-button size="small" @click="showUploadModal = true">
        <template #icon><UploadOutlined /></template> 上传基准
      </a-button>
      <a-button size="small" type="primary" @click="onGenerateClick">
        <template #icon><ThunderboltOutlined /></template> AI 生成
      </a-button>
    </div>

    <div v-if="loading && benchmarks.length === 0" class="benchmarks-empty">加载中...</div>
    <div v-else-if="benchmarks.length === 0" class="benchmarks-empty">暂无评估基准，请上传或 AI 生成</div>

    <div v-else class="benchmark-list">
      <div v-for="bm in benchmarks" :key="bm.id" class="benchmark-card" :class="{ 'benchmark-generating': bm.status === 'generating' }">
        <div class="benchmark-card-header">
          <div class="benchmark-card-left">
            <span class="benchmark-name">{{ bm.name }}</span>
            <template v-if="bm.status === 'generating'">
              <a-tag color="processing"><LoadingOutlined spin /> 生成中</a-tag>
            </template>
            <template v-else>
              <span class="benchmark-count">{{ bm.questionCount }} 题</span>
              <a-tag v-if="bm.hasGoldChunks && bm.hasGoldAnswer" color="purple">检索 + 问答</a-tag>
              <a-tag v-else-if="bm.hasGoldChunks" color="blue">检索评估</a-tag>
              <a-tag v-else-if="bm.hasGoldAnswer" color="gold">问答评估</a-tag>
              <a-tag v-else>仅查询</a-tag>
            </template>
          </div>
          <div class="benchmark-card-actions">
            <template v-if="bm.status !== 'generating'">
              <a-tooltip title="预览题目">
                <EyeOutlined class="action-icon" @click="previewBenchmark(bm)" />
              </a-tooltip>
              <a-tooltip title="下载 JSONL">
                <DownloadOutlined class="action-icon" @click="handleDownload(bm)" />
              </a-tooltip>
            </template>
            <a-tooltip title="删除">
              <DeleteOutlined class="action-icon action-danger" @click="handleDeleteConfirm(bm)" />
            </a-tooltip>
          </div>
        </div>
        <div v-if="bm.description && bm.status !== 'generating'" class="benchmark-desc">{{ bm.description }}</div>
      </div>
    </div>

    <!-- 预览弹窗 -->
    <a-modal
      v-model:open="previewVisible"
      :title="previewBenchmark_?.name + ' - 题目预览'"
      width="1200px"
      :footer="null"
    >
      <div class="preview-summary">
        <span>问题数: <strong>{{ previewBenchmark_?.questionCount }}</strong></span>
        <span>Gold Chunks: <strong>{{ hasGoldChunks ? '有' : '无' }}</strong></span>
        <span>Gold Answer: <strong>{{ hasGoldAnswer ? '有' : '无' }}</strong></span>
      </div>
      <a-table
        :dataSource="previewItems"
        :columns="previewColumns"
        :pagination="{ pageSize: 10, pageSizeOptions: ['5','10','20','50'], showSizeChanger: true, showTotal: t => `共 ${t} 条` }"
        size="small"
        :loading="previewLoading"
        :scroll="{ x: 1100 }"
      >
        <template #bodyCell="{ column, text }">
          <template v-if="column.key === 'query'">
            <span class="cell-wrap">{{ text }}</span>
          </template>
          <template v-if="column.key === 'goldChunkIds'">
            <template v-if="text">
              <a-tooltip placement="topLeft" :overlayStyle="{ maxWidth: '480px' }">
                <template #title>{{ formatGoldChunksFull(text) }}</template>
                <span class="cell-ellipsis">{{ formatGoldChunksBrief(text) }}</span>
              </a-tooltip>
            </template>
            <span v-else>-</span>
          </template>
          <template v-if="column.key === 'goldAnswer'">
            <a-tooltip v-if="text" placement="topLeft" :overlayStyle="{ maxWidth: '480px' }">
              <template #title>{{ text }}</template>
              <span class="cell-ellipsis">{{ text }}</span>
            </a-tooltip>
            <span v-else>-</span>
          </template>
        </template>
      </a-table>
    </a-modal>

    <BenchmarkGenerateModal v-model:open="showGenerateModal" :knowledge-id="knowledgeId" @success="loadBenchmarks" />
    <BenchmarkUploadModal v-model:open="showUploadModal" :knowledge-id="knowledgeId" @success="loadBenchmarks" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  ReloadOutlined, UploadOutlined, ThunderboltOutlined,
  EyeOutlined, DownloadOutlined, DeleteOutlined, LoadingOutlined,
} from '@ant-design/icons-vue'
import {
  listBenchmarks, getBenchmarkDetail, deleteBenchmark, downloadBenchmark,
} from '../../api/knowledgeEval'
import BenchmarkGenerateModal from './BenchmarkGenerateModal.vue'
import BenchmarkUploadModal from './BenchmarkUploadModal.vue'

const props = defineProps({
  knowledgeId: { type: String, required: true },
  docTotal: { type: Number, default: 0 },
})

const loading = ref(false)
const benchmarks = ref([])
const showGenerateModal = ref(false)
const showUploadModal = ref(false)

const previewVisible = ref(false)
const previewBenchmark_ = ref(null)
const previewLoading = ref(false)
const previewItems = ref([])

const hasGoldChunks = computed(() => previewItems.value.some(item => item.goldChunkIds))
const hasGoldAnswer = computed(() => previewItems.value.some(item => item.goldAnswer))

function parseGoldChunkIds(text) {
  if (!text) return []
  try {
    const arr = typeof text === 'string' ? JSON.parse(text) : text
    return Array.isArray(arr) ? arr : []
  } catch { return [] }
}

function formatGoldChunksBrief(text) {
  const ids = parseGoldChunkIds(text)
  if (ids.length === 0) return '-'
  const shown = ids.slice(0, 3).join(', ')
  return ids.length > 3 ? `${shown} ...等${ids.length}个` : shown
}

function formatGoldChunksFull(text) {
  const ids = parseGoldChunkIds(text)
  return ids.length > 0 ? ids.join(', ') : '-'
}

const previewColumns = computed(() => {
  const cols = [
    { title: '问题', dataIndex: 'query', key: 'query', width: 280, ellipsis: true },
  ]
  if (hasGoldChunks.value) {
    cols.push({ title: 'Gold Chunks', dataIndex: 'goldChunkIds', key: 'goldChunkIds', width: 200 })
  }
  cols.push({ title: 'Gold Answer', dataIndex: 'goldAnswer', key: 'goldAnswer', width: 420 })
  return cols
})

function onGenerateClick() {
  if (props.docTotal === 0) {
    Modal.warning({ title: '暂无文档', content: '知识库中暂无文档，请先上传文档后再生成评估基准。' })
    return
  }
  showGenerateModal.value = true
}

async function loadBenchmarks() {
  loading.value = true
  try {
    const res = await listBenchmarks(props.knowledgeId)
    benchmarks.value = res.data || []
  } catch (e) {
    message.error('加载基准列表失败')
  } finally {
    loading.value = false
  }
}

async function previewBenchmark(bm) {
  previewBenchmark_.value = bm
  previewVisible.value = true
  previewLoading.value = true
  try {
    const res = await getBenchmarkDetail(props.knowledgeId, bm.id, { pageNum: 1, pageSize: 100 })
    previewItems.value = res.data?.records || []
  } catch (e) {
    message.error('加载题目失败')
  } finally {
    previewLoading.value = false
  }
}

async function handleDownload(bm) {
  try {
    const res = await downloadBenchmark(props.knowledgeId, bm.id)
    const blob = new Blob([res], { type: 'application/octet-stream' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${bm.name}.jsonl`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    message.error('下载失败')
  }
}

function handleDeleteConfirm(bm) {
  Modal.confirm({
    title: '确定删除该基准？',
    content: `删除后不可恢复：${bm.name}`,
    okType: 'danger',
    onOk: async () => {
      await deleteBenchmark(props.knowledgeId, bm.id)
      message.success('已删除')
      loadBenchmarks()
    },
  })
}

defineExpose({ loadBenchmarks })

onMounted(loadBenchmarks)
</script>

<style scoped>
.eval-benchmarks { display: flex; flex-direction: column; gap: 12px; height: 100%; }
.eval-desc { color: #999; font-size: 12px; line-height: 1.6; padding: 0 2px; }
.benchmarks-toolbar { display: flex; gap: 8px; justify-content: flex-end; }
.benchmarks-empty { text-align: center; color: #999; padding: 40px 0; }
.benchmark-list { display: flex; flex-direction: column; gap: 8px; overflow-y: auto; }
.benchmark-card {
  border: 1px solid #e8e8e8; border-radius: 6px; padding: 12px;
  display: flex; flex-direction: column; gap: 6px;
}
.benchmark-generating {
  border-color: #91caff;
  background: #e6f4ff;
}
.benchmark-card-header { display: flex; justify-content: space-between; align-items: center; }
.benchmark-card-left { display: flex; align-items: center; gap: 8px; }
.benchmark-name { font-weight: 500; }
.benchmark-count { color: #666; font-size: 12px; background: #f0f0f0; padding: 2px 8px; border-radius: 4px; }
.benchmark-desc { color: #888; font-size: 12px; }
.benchmark-card-actions { display: flex; gap: 12px; }
.action-icon { cursor: pointer; color: #666; font-size: 14px; }
.action-icon:hover { color: #1890ff; }
.action-danger:hover { color: #ff4d4f; }
.preview-summary {
  display: flex; gap: 24px; margin-bottom: 12px; padding: 8px 12px;
  background: #f6f8fa; border-radius: 6px; font-size: 13px; color: #333;
}
.preview-summary strong { margin-left: 4px; }
.cell-ellipsis {
  display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical;
  overflow: hidden; text-overflow: ellipsis; word-break: break-all;
}
.cell-wrap {
  word-break: break-all;
  white-space: pre-wrap;
}
</style>
