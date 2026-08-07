<template>
  <div class="config-panel">
    <div class="panel-toolbar">
      <a-input
        v-model:value="keyword"
        allow-clear
        placeholder="搜索任务名称..."
        style="width: 220px"
      >
        <template #prefix><SearchOutlined /></template>
      </a-input>
      <a-select
        v-model:value="agentFilter"
        allow-clear
        show-search
        placeholder="智能体"
        style="width: 180px"
        :options="agentOptions"
        :filter-option="filterAgent"
        :loading="agentsLoading"
      />
      <a-select
        v-model:value="enabledFilter"
        allow-clear
        placeholder="启用状态"
        style="width: 120px"
        :options="[
          { value: true, label: '已启用' },
          { value: false, label: '已停用' },
        ]"
      />
      <div class="panel-toolbar__spacer" />
      <button type="button" class="lb-btn lb-btn--primary" @click="openCreate">
        <PlusOutlined /> 新建定时任务
      </button>
    </div>

    <div class="panel-table-wrap">
      <a-table
        :columns="columns"
        :data-source="jobs"
        :pagination="pagination"
        :loading="loading"
        row-key="id"
        size="middle"
        :scroll="{ x: 1100 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <button type="button" class="btn-link cell-name" @click="openEdit(record)">
              {{ record.name }}
            </button>
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-switch
              :checked="record.enabled"
              size="small"
              @change="(checked) => toggleEnabled(record, checked)"
            />
          </template>
          <template v-else-if="column.key === 'schedule'">
            <a-tooltip :title="scheduleText(record)" placement="topLeft">
              <span class="cell-ellipsis">{{ scheduleText(record) }}</span>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'instruction'">
            <a-tooltip :title="record.instruction" placement="topLeft">
              <span class="cell-ellipsis">{{ record.instruction }}</span>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'nextRunAt'">
            {{ formatNextRun(record.nextRunAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <button type="button" class="btn-link" @click="openEdit(record)">编辑</button>
            <button type="button" class="btn-link" @click="runJob(record)">执行</button>
            <button type="button" class="btn-link btn-link--danger" @click="removeJob(record)">删除</button>
          </template>
        </template>
        <template #emptyText>
          <LbEmptyState
            :icon="ThunderboltOutlined"
            title="暂无定时任务"
            desc="创建任务后，可按设定时间自动调用智能体执行文字指令"
          />
        </template>
      </a-table>
    </div>

    <a-modal
      v-model:open="formOpen"
      :title="editingId ? '编辑定时任务' : '新建定时任务'"
      :width="560"
      destroy-on-close
      :confirm-loading="saving"
      @ok="saveJob"
      @cancel="formOpen = false"
    >
      <a-form :label-col="{ flex: '0 0 90px' }" class="job-form">
        <a-form-item label="任务名称" required>
          <a-input v-model:value="form.name" placeholder="例如：每日早报汇总" :maxlength="64" />
        </a-form-item>
        <a-form-item label="智能体" required>
          <a-select
            v-model:value="form.agentId"
            show-search
            allow-clear
            placeholder="选择要执行的智能体"
            style="width: 100%"
            :options="agentOptions"
            :filter-option="filterAgent"
            :loading="agentsLoading"
          />
        </a-form-item>
        <a-form-item label="文字指令" required>
          <a-textarea
            v-model:value="form.instruction"
            :rows="3"
            placeholder="定时触发时发送给智能体的指令内容"
            :maxlength="2000"
            show-count
          />
        </a-form-item>
        <a-form-item label="触发方式" required>
          <a-radio-group v-model:value="form.scheduleType" class="schedule-type">
            <a-radio-button value="once">一次性</a-radio-button>
            <a-radio-button value="daily">每天</a-radio-button>
            <a-radio-button value="weekly">每周</a-radio-button>
            <a-radio-button value="monthly">每月</a-radio-button>
            <a-radio-button value="cron">Cron</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="form.scheduleType === 'once'" label="执行时刻" required>
          <a-date-picker
            v-model:value="form.onceAt"
            show-time
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm"
            style="width: 220px"
            placeholder="选择执行日期与时间"
            :disabled-date="disablePastDate"
            :show-now="false"
          />
          <div class="form-tip">到点执行一次，完成后任务自动结束</div>
        </a-form-item>
        <a-form-item v-if="form.scheduleType === 'weekly'" label="星期">
          <a-select
            v-model:value="form.weekdays"
            mode="multiple"
            placeholder="选择星期"
            style="width: 100%"
            :options="weekdayOptions"
          />
        </a-form-item>
        <a-form-item v-if="form.scheduleType === 'monthly'" label="日期">
          <a-select
            v-model:value="form.monthDay"
            style="width: 160px"
            :options="monthDayOptions"
            placeholder="每月几号"
          />
          <div class="form-tip">若当月无该日（如 31 号），将跳过该月</div>
        </a-form-item>
        <a-form-item
          v-if="form.scheduleType !== 'cron' && form.scheduleType !== 'once'"
          label="执行时间"
          required
        >
          <a-time-picker
            v-model:value="form.time"
            format="HH:mm"
            value-format="HH:mm"
            style="width: 160px"
            placeholder="选择时间"
          />
        </a-form-item>
        <a-form-item v-else-if="form.scheduleType === 'cron'" label="Cron" required>
          <a-input
            v-model:value="form.cron"
            placeholder="例如：0 8 * * *（每天 08:00）"
          />
          <div class="form-tip">标准 5 段：分 时 日 月 周</div>
          <div class="cron-preview" :class="{ 'cron-preview--error': !cronPreview.ok && form.cron.trim() }">
            <div class="cron-preview__title">接下来 5 次执行</div>
            <ol v-if="cronPreview.ok" class="cron-preview__list">
              <li v-for="(t, i) in cronPreview.times" :key="i">{{ t }}</li>
            </ol>
            <div v-else class="cron-preview__error">
              {{ form.cron.trim() ? cronPreview.error : '输入 Cron 后将预览执行时间' }}
            </div>
          </div>
        </a-form-item>
        <a-form-item label="启用">
          <a-switch v-model:checked="form.enabled" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  PlusOutlined,
  SearchOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import dayjs from 'dayjs'
import LbEmptyState from '../../components/common/LbEmptyState.vue'
import { getAgents } from '../../api/agent'
import {
  createAutomationJob,
  deleteAutomationJob,
  disableAutomationJob,
  enableAutomationJob,
  listAutomationJobs,
  runAutomationJob,
  updateAutomationJob,
} from '../../api/automation'
import { previewCronNextRuns } from '../../utils/cronPreview'

/** ISO：1=周一 … 7=周日 */
const weekdayOptions = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' },
]

const monthDayOptions = Array.from({ length: 31 }, (_, i) => ({
  value: i + 1,
  label: `每月 ${i + 1} 日`,
}))

const columns = [
  { title: '任务名称', dataIndex: 'name', key: 'name', width: 168, ellipsis: true },
  { title: '智能体', dataIndex: 'agentName', key: 'agentName', width: 120, ellipsis: true },
  { title: '触发方式', key: 'schedule', width: 180, ellipsis: true },
  { title: '文字指令', dataIndex: 'instruction', key: 'instruction', ellipsis: true },
  { title: '下次触发', dataIndex: 'nextRunAt', key: 'nextRunAt', width: 170 },
  { title: '启用', key: 'enabled', width: 72 },
  { title: '操作', key: 'actions', width: 168, fixed: 'right' },
]

const pagination = reactive({
  current: 1,
  pageSize: 10,
  showSizeChanger: true,
  showTotal: (t) => `共 ${t} 条`,
})

const jobs = ref([])
const loading = ref(false)
const keyword = ref('')
const agentFilter = ref(undefined)
const enabledFilter = ref(undefined)
const formOpen = ref(false)
const saving = ref(false)
const editingId = ref(null)
const agentsLoading = ref(false)
const agentOptions = ref([])

const form = reactive({
  name: '',
  agentId: undefined,
  instruction: '',
  scheduleType: 'daily',
  time: '09:00',
  onceAt: undefined,
  weekdays: [1],
  monthDay: 1,
  cron: '0 9 * * *',
  enabled: true,
})

const cronPreview = computed(() => previewCronNextRuns(form.cron, 5))

function formatNextRun(v) {
  if (!v) return '—'
  return dayjs(v).isValid() ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : String(v)
}

function scheduleText(job) {
  if (job.scheduleType === 'once') return `一次性 ${job.onceAt || '—'}`
  if (job.scheduleType === 'cron') return `Cron ${job.cron || '—'}`
  if (job.scheduleType === 'weekly') {
    const days = (job.weekdays || [])
      .map((d) => weekdayOptions.find((o) => o.value === d)?.label || d)
      .join('、')
    return `每周 ${days || '—'} ${job.time || ''}`
  }
  if (job.scheduleType === 'monthly') {
    return `每月 ${job.monthDay || '—'} 日 ${job.time || ''}`
  }
  return `每天 ${job.time || '—'}`
}

function disablePastDate(current) {
  return current && current < dayjs().startOf('day')
}

function filterAgent(input, option) {
  return String(option?.label || '').toLowerCase().includes(String(input || '').toLowerCase())
}

function resetForm() {
  form.name = ''
  form.agentId = undefined
  form.instruction = ''
  form.scheduleType = 'daily'
  form.time = '09:00'
  form.onceAt = dayjs().add(1, 'hour').format('YYYY-MM-DD HH:mm')
  form.weekdays = [1]
  form.monthDay = 1
  form.cron = '0 9 * * *'
  form.enabled = true
}

function openCreate() {
  editingId.value = null
  resetForm()
  formOpen.value = true
}

function openEdit(job) {
  editingId.value = job.id
  form.name = job.name
  form.agentId = job.agentId != null ? String(job.agentId) : undefined
  form.instruction = job.instruction
  form.scheduleType = job.scheduleType || 'daily'
  form.time = job.time
  form.onceAt = job.onceAt || dayjs().add(1, 'hour').format('YYYY-MM-DD HH:mm')
  form.weekdays = [...(job.weekdays || [])]
  form.monthDay = job.monthDay || 1
  form.cron = job.cron
  form.enabled = !!job.enabled
  formOpen.value = true
}

function buildPayload() {
  return {
    name: form.name.trim(),
    agentId: form.agentId,
    instruction: form.instruction.trim(),
    scheduleType: form.scheduleType,
    time: form.time,
    onceAt: form.onceAt,
    weekdays: [...(form.weekdays || [])],
    monthDay: form.monthDay,
    cron: (form.cron || '').trim(),
    enabled: !!form.enabled,
  }
}

function validateForm() {
  if (!form.name.trim()) {
    message.warning('请填写任务名称')
    return false
  }
  if (!form.agentId) {
    message.warning('请选择智能体')
    return false
  }
  if (!form.instruction.trim()) {
    message.warning('请填写文字指令')
    return false
  }
  if (form.scheduleType === 'once') {
    if (!form.onceAt) {
      message.warning('请选择执行时刻')
      return false
    }
    if (dayjs(form.onceAt).isBefore(dayjs())) {
      message.warning('执行时刻不能早于当前时间')
      return false
    }
  }
  if (form.scheduleType === 'cron') {
    if (!form.cron.trim()) {
      message.warning('请填写 Cron 表达式')
      return false
    }
    if (!cronPreview.value.ok) {
      message.warning(cronPreview.value.error || 'Cron 表达式无效')
      return false
    }
  }
  if (form.scheduleType !== 'cron' && form.scheduleType !== 'once' && !form.time) {
    message.warning('请选择执行时间')
    return false
  }
  if (form.scheduleType === 'weekly' && !(form.weekdays || []).length) {
    message.warning('请选择星期')
    return false
  }
  if (form.scheduleType === 'monthly' && !form.monthDay) {
    message.warning('请选择每月日期')
    return false
  }
  return true
}

async function saveJob() {
  if (!validateForm()) return Promise.reject()
  saving.value = true
  try {
    const payload = buildPayload()
    if (editingId.value) {
      await updateAutomationJob(editingId.value, payload)
      message.success('已保存')
    } else {
      await createAutomationJob(payload)
      message.success('已创建')
    }
    formOpen.value = false
    await loadJobs()
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(job, checked) {
  const prev = job.enabled
  job.enabled = !!checked
  try {
    if (checked) {
      const res = await enableAutomationJob(job.id)
      Object.assign(job, res?.data || {})
      message.success('已启用')
    } else {
      const res = await disableAutomationJob(job.id)
      Object.assign(job, res?.data || {})
      message.success('已停用')
    }
  } catch {
    job.enabled = prev
  }
}

function runJob(job) {
  Modal.confirm({
    title: '立即执行一次？',
    content: `将调用「${job.agentName || '智能体'}」执行该任务指令`,
    okText: '执行',
    async onOk() {
      await runAutomationJob(job.id)
      message.success('已提交执行，可在任务记录中查看结果')
    },
  })
}

function removeJob(job) {
  Modal.confirm({
    title: '确认删除该定时任务？',
    content: `「${job.name}」删除后不可恢复`,
    okType: 'danger',
    okText: '删除',
    async onOk() {
      await deleteAutomationJob(job.id)
      message.success('已删除')
      await loadJobs()
    },
  })
}

async function loadJobs() {
  loading.value = true
  try {
    const res = await listAutomationJobs({
      keyword: keyword.value?.trim() || undefined,
      enabled: enabledFilter.value,
      agentId: agentFilter.value || undefined,
    })
    jobs.value = res?.data || []
  } finally {
    loading.value = false
  }
}

async function loadAgents() {
  agentsLoading.value = true
  try {
    const res = await getAgents({ pageNum: 1, pageSize: 200, includeDefault: false })
    const list = res?.data?.records || []
    agentOptions.value = list.map((a) => ({
      value: String(a.id),
      label: a.name || `智能体 ${a.id}`,
    }))
  } catch {
    agentOptions.value = []
  } finally {
    agentsLoading.value = false
  }
}

let filterTimer
watch([keyword, enabledFilter, agentFilter], () => {
  clearTimeout(filterTimer)
  filterTimer = setTimeout(loadJobs, 250)
})

onMounted(async () => {
  await Promise.all([loadAgents(), loadJobs()])
})
</script>

<style scoped>
.config-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  gap: 12px;
}
.panel-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.panel-toolbar__spacer {
  flex: 1;
}
.panel-table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 4px 8px 8px;
}
.cell-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.cell-name {
  font-weight: 500;
}
.btn-link {
  border: none;
  background: none;
  padding: 0;
  margin-right: 10px;
  color: var(--color-link);
  cursor: pointer;
  font-size: 13px;
}
.btn-link:last-child {
  margin-right: 0;
}
.btn-link:hover {
  opacity: 0.8;
}
.btn-link--danger {
  color: var(--color-error);
}
.schedule-type {
  display: flex;
  flex-wrap: wrap;
}
.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-mute);
}
.cron-preview {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas-soft);
}
.cron-preview--error {
  border-color: color-mix(in srgb, var(--color-error) 35%, transparent);
  background: color-mix(in srgb, var(--color-error) 6%, var(--color-canvas));
}
.cron-preview__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 6px;
}
.cron-preview__list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--color-body);
  line-height: 1.7;
  font-variant-numeric: tabular-nums;
}
.cron-preview__error {
  font-size: 12px;
  color: var(--color-mute);
}
.cron-preview--error .cron-preview__error {
  color: var(--color-error);
}
.job-form :deep(.ant-form-item) {
  margin-bottom: 16px;
}
</style>
