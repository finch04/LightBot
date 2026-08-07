<template>
  <div class="gfb-card">
    <div class="gfb-title">{{ title }}</div>
    <p v-if="hint && !done" class="gfb-hint">{{ hint }}</p>
    <template v-if="!done">
      <a-form layout="vertical" class="gfb-form">
        <a-form-item v-for="field in fields" :key="field.key" :label="field.label || field.key" :required="field.required">
          <a-input-number
            v-if="field.type === 'number'"
            v-model:value="form[field.key]"
            style="width: 100%"
            :disabled="submitting"
          />
          <a-select
            v-else-if="field.type === 'select'"
            v-model:value="form[field.key]"
            :options="normalizeOptions(field.options)"
            style="width: 100%"
            :disabled="submitting"
          />
          <a-textarea
            v-else-if="field.type === 'textarea'"
            v-model:value="form[field.key]"
            :rows="3"
            :disabled="submitting"
          />
          <a-input v-else v-model:value="form[field.key]" :disabled="submitting" />
        </a-form-item>
      </a-form>
      <div class="gfb-actions">
        <a-button v-if="canCancel" :disabled="submitting" @click="onCancel">取消</a-button>
        <a-button v-if="canSubmit" type="primary" :loading="submitting" @click="onSubmit">提交</a-button>
      </div>
    </template>
    <div v-else class="gfb-done">
      <dl v-if="submittedEntries.length" class="gfb-summary">
        <div v-for="[k, v] in submittedEntries" :key="k" class="gfb-summary-row">
          <dt>{{ fieldLabel(k) }}</dt>
          <dd>{{ formatDisplay(v) }}</dd>
        </div>
      </dl>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'
import { useBusinessPageSubmit } from './useBusinessPageSubmit'

const props = defineProps({
  payload: { type: Object, required: true },
  messageIndex: { type: Number, default: -1 },
  workflowMode: { type: Boolean, default: false },
})
const emit = defineEmits(['workflow-submit', 'workflow-cancel'])

const title = computed(() => props.payload?.title || props.payload?.displayName || '业务办理')
const hint = computed(() => props.payload?.options?.hint || '通用表单模板')
const actions = computed(() => Array.isArray(props.payload?.actions) ? props.payload.actions : ['submit', 'cancel'])
const canSubmit = computed(() => actions.value.includes('submit'))
const canCancel = computed(() => actions.value.includes('cancel'))
const fields = computed(() => {
  const schema = props.payload?.formSchema
  const list = Array.isArray(schema?.fields) ? schema.fields : []
  return list.filter((f) => f?.key)
})

const form = reactive({})
watch(fields, (list) => {
  const propsMap = props.payload?.props || {}
  list.forEach((f) => {
    if (form[f.key] === undefined) {
      form[f.key] = propsMap[f.key] ?? f.defaultValue ?? (f.type === 'number' ? null : '')
    }
  })
}, { immediate: true })

const { submitting, done, submittedResult, submitResult, cancelResult } = useBusinessPageSubmit(props, emit)

const submittedEntries = computed(() => {
  const values = submittedResult.value?.values
  if (!values || typeof values !== 'object') return []
  return Object.entries(values)
})

function fieldLabel(key) {
  const f = fields.value.find((x) => x.key === key)
  return f?.label || key
}

function formatDisplay(v) {
  if (v == null || v === '') return '—'
  if (typeof v === 'object') {
    try { return JSON.stringify(v) } catch { return String(v) }
  }
  return String(v)
}

function normalizeOptions(options) {
  if (!Array.isArray(options)) return []
  return options.map((opt) => (typeof opt === 'object'
    ? { label: opt.label || opt.value, value: opt.value }
    : { label: String(opt), value: opt }))
}

async function onSubmit() {
  await submitResult({
    action: 'submit',
    pageType: props.payload?.pageType,
    values: { ...form },
  })
}

async function onCancel() {
  await cancelResult({ action: 'cancel', pageType: props.payload?.pageType })
}
</script>

<style scoped>
.gfb-card {
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 14px;
  padding: 16px;
  max-width: 420px;
  background: #fff;
}
.gfb-title { font-weight: 600; font-size: 16px; margin-bottom: 4px; }
.gfb-hint { margin: 0 0 12px; font-size: 12px; color: #6b7280; }
.gfb-actions { display: flex; justify-content: flex-end; gap: 8px; }
.gfb-done { margin-top: 8px; }
.gfb-summary { margin: 0; display: flex; flex-direction: column; gap: 6px; }
.gfb-summary-row {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 8px;
  font-size: 13px;
  line-height: 1.4;
}
.gfb-summary-row dt { margin: 0; color: #71717a; }
.gfb-summary-row dd { margin: 0; color: #171717; word-break: break-word; }
</style>
