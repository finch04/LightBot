<template>
  <div v-if="confirmForm" class="workflow-confirm-form">
    <a-alert type="warning" show-icon message="等待人工确认" :description="confirmForm.message || '请填写以下信息并确认后继续'" class="confirm-alert" />
    <a-form layout="vertical" class="confirm-fields">
      <a-form-item
        v-for="field in formFields"
        :key="field.key"
        :label="field.label || field.key"
        :required="field.required"
      >
        <a-select
          v-if="field.type === 'select'"
          v-model:value="formValues[field.key]"
          :placeholder="`请选择${field.label || field.key}`"
          :options="selectOptions(field.options)"
          style="width: 100%"
        />
        <a-textarea
          v-else-if="field.type === 'textarea'"
          v-model:value="formValues[field.key]"
          :rows="3"
          :placeholder="field.label || field.key"
        />
        <a-input-number
          v-else-if="field.type === 'number'"
          v-model:value="formValues[field.key]"
          style="width: 100%"
        />
        <a-input
          v-else
          v-model:value="formValues[field.key]"
          :placeholder="field.label || field.key"
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" :loading="submitting" @click="handleSubmit">
          确认并继续
        </a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup>
import { reactive, watch, computed } from 'vue'

const props = defineProps({
  confirmForm: { type: Object, default: null },
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['submit'])

const formValues = reactive({})

const formFields = computed(() => {
  const fields = props.confirmForm?.formFields
  return Array.isArray(fields) ? fields : []
})

watch(
  () => props.confirmForm,
  (form) => {
    Object.keys(formValues).forEach(k => delete formValues[k])
    if (!form?.formFields) return
    for (const field of form.formFields) {
      if (!field?.key) continue
      formValues[field.key] = field.defaultValue ?? (field.type === 'number' ? null : '')
    }
  },
  { immediate: true, deep: true }
)

function selectOptions(options) {
  if (!Array.isArray(options)) return []
  return options.map(opt => {
    if (typeof opt === 'string') return { label: opt, value: opt }
    return { label: opt.label ?? opt.value, value: opt.value ?? opt.label }
  })
}

function handleSubmit() {
  for (const field of formFields.value) {
    if (!field.required) continue
    const val = formValues[field.key]
    if (val == null || String(val).trim() === '') {
      return
    }
  }
  emit('submit', { ...formValues })
}
</script>

<style scoped>
.workflow-confirm-form {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  background: #fff7ed;
}
.confirm-alert {
  margin-bottom: 12px;
}
.confirm-fields {
  margin-top: 4px;
}
</style>
