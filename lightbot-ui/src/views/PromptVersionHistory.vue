<template>
  <div class="page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <button class="btn-back" @click="router.push(`/app/prompts/${promptKey}`)">
          <ArrowLeftOutlined /> 返回
        </button>
        <h1 class="page-title">版本记录</h1>
        <p class="page-desc">Prompt Key: {{ promptKey }}</p>
      </div>
    </div>

    <!-- 提示条 -->
    <a-alert
      message="勾选两个版本进行对比，或点击操作列的详情按钮查看版本详情"
      type="info"
      show-icon
      style="margin-bottom: 16px"
    />

    <!-- 版本表格 -->
    <a-table
      :dataSource="versions"
      :columns="columns"
      :pagination="false"
      rowKey="id"
      size="middle"
      :loading="loading"
      :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
    >
      <template #bodyCell="{ column, record, index }">
        <template v-if="column.key === 'version'">
          <a-tag color="blue">{{ record.version }}</a-tag>
          <a-tag v-if="index === 0" color="green" size="small">当前版本</a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 'release' ? 'green' : 'blue'" size="small">
            {{ record.status === 'release' ? '正式版本' : '草稿' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
        <template v-if="column.key === 'action'">
          <button class="btn-link" @click="openDetail(record)">详情</button>
        </template>
      </template>
    </a-table>

    <!-- 底部对比按钮 -->
    <div class="compare-bar" v-if="selectedRowKeys.length === 2">
      <button class="btn-primary-sm" @click="openCompare()">
        <SwapOutlined /> 对比选中版本
      </button>
    </div>

    <!-- 版本详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      title="版本详情"
      :width="700"
      :footer="null"
      :maskClosable="false"
    >
      <div v-if="detailVersion" class="version-detail">
        <div class="detail-row">
          <span class="detail-label">版本号</span>
          <a-tag color="blue">{{ detailVersion.version }}</a-tag>
        </div>
        <div class="detail-row">
          <span class="detail-label">发布时间</span>
          <span>{{ formatTime(detailVersion.createTime) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">版本描述</span>
          <span>{{ detailVersion.versionDesc || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">状态</span>
          <a-tag :color="detailVersion.status === 'release' ? 'green' : 'blue'">
            {{ detailVersion.status === 'release' ? '正式版本' : '草稿' }}
          </a-tag>
        </div>
        <div class="detail-row">
          <span class="detail-label">模型配置</span>
          <pre class="detail-json">{{ formatConfig(detailVersion.modelConfig) }}</pre>
        </div>
        <div class="detail-row">
          <span class="detail-label">参数</span>
          <pre class="detail-json">{{ formatConfig(detailVersion.variables) }}</pre>
        </div>
        <a-divider />
        <div class="detail-content-title">Prompt 内容</div>
        <a-textarea :value="detailVersion.template" :rows="12" readonly class="template-editor" />
      </div>
      <div class="dialog-footer">
        <div class="dialog-footer-left">
          <button class="btn-outline-sm" @click="restoreVersion(detailVersion)">
            <RollbackOutlined /> 恢复到编辑区
          </button>
          <button class="btn-outline-sm" @click="compareWithPrevious(detailVersion)">
            <SwapOutlined /> 与前版本对比
          </button>
        </div>
        <button class="btn-cancel" @click="detailVisible = false">关闭</button>
      </div>
    </a-modal>

    <!-- 版本对比弹窗 -->
    <a-modal
      v-model:open="compareVisible"
      :title="'版本对比 - ' + promptKey"
      :width="1200"
      :footer="null"
      :maskClosable="false"
    >
      <div v-if="compareOld && compareNew" class="compare-content">
        <!-- 颜色图例 -->
        <div class="compare-legend">
          <span class="legend-item"><span class="legend-dot removed"></span> 删除</span>
          <span class="legend-item"><span class="legend-dot added"></span> 新增</span>
          <span class="legend-item"><span class="legend-dot modified"></span> 修改</span>
        </div>

        <!-- 版本信息对比 -->
        <div class="compare-info-grid">
          <div class="compare-info-card">
            <div class="compare-info-title">旧版本: {{ compareOld.version }}</div>
            <div class="compare-info-meta">{{ formatTime(compareOld.createTime) }}</div>
          </div>
          <div class="compare-info-card">
            <div class="compare-info-title">新版本: {{ compareNew.version }}</div>
            <div class="compare-info-meta">{{ formatTime(compareNew.createTime) }}</div>
          </div>
        </div>

        <!-- 模型配置对比 -->
        <div class="compare-section" v-if="compareOld.modelConfig || compareNew.modelConfig">
          <div class="compare-section-title">模型配置对比</div>
          <div class="compare-config-grid">
            <div class="compare-config-col">
              <pre class="config-pre">{{ formatConfig(compareOld.modelConfig) }}</pre>
            </div>
            <div class="compare-config-col">
              <pre class="config-pre">{{ formatConfig(compareNew.modelConfig) }}</pre>
            </div>
          </div>
        </div>

        <!-- 内容对比 -->
        <div class="compare-section">
          <div class="compare-section-title">内容对比</div>
          <div class="diff-view">
            <div v-for="(line, i) in diffLines" :key="i" :class="['diff-line', line.type]">
              <span class="diff-line-num">{{ i + 1 }}</span>
              <span class="diff-line-icon">
                <template v-if="line.type === 'removed'">-</template>
                <template v-else-if="line.type === 'added'">+</template>
                <template v-else-if="line.type === 'modified'">~</template>
                <template v-else>&nbsp;</template>
              </span>
              <span class="diff-line-old">{{ line.old }}</span>
              <span class="diff-line-new">{{ line.new }}</span>
            </div>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeftOutlined, SwapOutlined, RollbackOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getPromptVersions } from '../api/prompt'

const route = useRoute()
const router = useRouter()
const promptKey = route.params.promptKey || route.params.id
const versions = ref([])
const loading = ref(false)

const columns = [
  { title: '版本号', key: 'version', dataIndex: 'version', width: 180 },
  { title: '发布时间', key: 'createTime', dataIndex: 'createTime', width: 180 },
  { title: '版本说明', dataIndex: 'versionDesc', key: 'versionDesc', ellipsis: true },
  { title: '状态', key: 'status', dataIndex: 'status', width: 100 },
  { title: '操作', key: 'action', width: 80 },
]

// 选择
const selectedRowKeys = ref([])
function onSelectChange(keys) {
  selectedRowKeys.value = keys.length > 2 ? keys.slice(-2) : keys
}

// 详情弹窗
const detailVisible = ref(false)
const detailVersion = ref(null)

function openDetail(v) {
  detailVersion.value = v
  detailVisible.value = true
}

function restoreVersion(v) {
  if (!v) return
  router.push(`/app/prompts/${promptKey}?restoreVersion=${v.version}`)
}

function compareWithPrevious(v) {
  const idx = versions.value.findIndex(item => item.id === v.id)
  if (idx < versions.value.length - 1) {
    const older = versions.value[idx + 1]
    doCompare(older, v)
  } else {
    message.info('没有更早的版本可以对比')
  }
}

// 对比弹窗
const compareVisible = ref(false)
const compareOld = ref(null)
const compareNew = ref(null)

function openCompare() {
  const selected = selectedRowKeys.value.map(id => versions.value.find(v => v.id === id)).filter(Boolean)
  if (selected.length !== 2) return
  // 按时间排序，旧的在前
  const sorted = [...selected].sort((a, b) => new Date(a.createTime) - new Date(b.createTime))
  doCompare(sorted[0], sorted[1])
}

function doCompare(oldV, newV) {
  compareOld.value = oldV
  compareNew.value = newV
  compareVisible.value = true
}

const diffLines = computed(() => {
  if (!compareOld.value || !compareNew.value) return []
  const oldLines = (compareOld.value.template || '').split('\n')
  const newLines = (compareNew.value.template || '').split('\n')
  const maxLen = Math.max(oldLines.length, newLines.length)
  const result = []
  for (let i = 0; i < maxLen; i++) {
    const old = oldLines[i] ?? ''
    const new_ = newLines[i] ?? ''
    if (old === new_) {
      result.push({ type: 'unchanged', old, new: new_ })
    } else if (old && !new_) {
      result.push({ type: 'removed', old, new: '' })
    } else if (!old && new_) {
      result.push({ type: 'added', old: '', new: new_ })
    } else {
      result.push({ type: 'modified', old, new: new_ })
    }
  }
  return result
})

function formatConfig(cfg) {
  if (!cfg) return '-'
  try {
    return JSON.stringify(JSON.parse(cfg), null, 2)
  } catch {
    return cfg
  }
}

function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

onMounted(async () => {
  loading.value = true
  try {
    const res = await getPromptVersions(promptKey)
    versions.value = res.data || []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page {
  padding: 20px 24px;
  min-height: 100vh;
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
.btn-link {
  background: none;
  border: none;
  color: #0070f3;
  cursor: pointer;
  font-size: 13px;
  padding: 0;
}
.btn-link:hover { text-decoration: underline; }
.btn-primary-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 20px;
  background: #171717;
  color: #fff;
  border: none;
  border-radius: 100px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary-sm:hover { background: #27272a; }
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
.btn-outline-sm:hover { border-color: #0070f3; color: #0070f3; }
.btn-cancel {
  padding: 6px 16px;
  background: transparent;
  border: 1px solid #d9d9d9;
  border-radius: 100px;
  cursor: pointer;
  font-size: 13px;
}
.btn-cancel:hover { border-color: #0070f3; color: #0070f3; }

.compare-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 版本详情 */
.version-detail {
  max-height: 60vh;
  overflow-y: auto;
}
.detail-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 8px;
}
.detail-label {
  font-size: 13px;
  color: #71717a;
  min-width: 80px;
  flex-shrink: 0;
}
.detail-mono {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  color: #171717;
  word-break: break-all;
}
.detail-json {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  color: #171717;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  flex: 1;
  background: #fafafa;
  border: 1px solid #ebebeb;
  border-radius: 6px;
  padding: 8px 12px;
}
.detail-content-title {
  font-size: 14px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 8px;
}
.template-editor {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
}
.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}
.dialog-footer-left {
  display: flex;
  gap: 8px;
}

/* 对比弹窗 */
.compare-content {
  max-height: 70vh;
  overflow-y: auto;
}
.compare-legend {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #71717a;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 2px;
}
.legend-dot.removed { background: #fee2e2; border: 1px solid #dc2626; }
.legend-dot.added { background: #dcfce7; border: 1px solid #16a34a; }
.legend-dot.modified { background: #fef3c7; border: 1px solid #d97706; }

.compare-info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.compare-info-card {
  border: 1px solid #ebebeb;
  border-radius: 8px;
  padding: 12px;
}
.compare-info-title {
  font-size: 14px;
  font-weight: 600;
  color: #171717;
}
.compare-info-meta {
  font-size: 12px;
  color: #a1a1aa;
  margin-top: 4px;
}
.compare-section {
  margin-bottom: 16px;
}
.compare-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 8px;
}
.compare-config-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.compare-config-col {
  border: 1px solid #ebebeb;
  border-radius: 8px;
  padding: 12px;
  background: #fafafa;
}
.config-pre {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  color: #171717;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

/* Diff 视图 */
.diff-view {
  border: 1px solid #ebebeb;
  border-radius: 8px;
  overflow: hidden;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
}
.diff-line {
  display: grid;
  grid-template-columns: 40px 30px 1fr 1fr;
  border-bottom: 1px solid #f5f5f5;
}
.diff-line:last-child { border-bottom: none; }
.diff-line-num {
  padding: 4px 8px;
  color: #a1a1aa;
  text-align: right;
  background: #fafafa;
  border-right: 1px solid #ebebeb;
}
.diff-line-icon {
  padding: 4px 6px;
  text-align: center;
  font-weight: 700;
}
.diff-line-old,
.diff-line-new {
  padding: 4px 12px;
  white-space: pre-wrap;
  word-break: break-word;
}
.diff-line.unchanged .diff-line-icon { color: #a1a1aa; }
.diff-line.removed {
  background: #fef2f2;
}
.diff-line.removed .diff-line-icon { color: #dc2626; }
.diff-line.removed .diff-line-old { color: #dc2626; text-decoration: line-through; }
.diff-line.added {
  background: #f0fdf4;
}
.diff-line.added .diff-line-icon { color: #16a34a; }
.diff-line.added .diff-line-new { color: #16a34a; font-weight: 600; }
.diff-line.modified {
  background: #fffbeb;
}
.diff-line.modified .diff-line-icon { color: #d97706; }
.diff-line.modified .diff-line-old { color: #dc2626; text-decoration: line-through; }
.diff-line.modified .diff-line-new { color: #16a34a; font-weight: 600; }
</style>
