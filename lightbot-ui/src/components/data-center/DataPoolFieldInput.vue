<template>
  <a-textarea
    v-if="field.type === 'textarea'"
    :value="modelValue"
    :rows="3"
    :placeholder="`请输入${field.label}`"
    @update:value="emitValue"
  />
  <a-input-number
    v-else-if="field.type === 'number'"
    :value="modelValue"
    style="width: 100%"
    :placeholder="`请输入${field.label}`"
    :min="field.props?.min"
    :max="field.props?.max"
    @update:value="emitValue"
  />
  <a-select
    v-else-if="field.type === 'select'"
    :value="modelValue"
    allow-clear
    style="width: 100%"
    :placeholder="`请选择${field.label}`"
    :options="selectOptions"
    @update:value="emitValue"
  />
  <a-radio-group
    v-else-if="field.type === 'radio'"
    :value="modelValue"
    @update:value="emitValue"
  >
    <a-radio v-for="opt in selectOptions" :key="String(opt.value)" :value="opt.value">
      {{ opt.label }}
    </a-radio>
  </a-radio-group>
  <a-select
    v-else-if="field.type === 'checkbox'"
    mode="multiple"
    allow-clear
    :value="asArray(modelValue)"
    style="width: 100%"
    :placeholder="`请选择${field.label}`"
    :options="selectOptions"
    @update:value="emitValue"
  />
  <a-date-picker
    v-else-if="field.type === 'date'"
    :value="modelValue"
    value-format="YYYY-MM-DD"
    style="width: 100%"
    :placeholder="`请选择${field.label}`"
    @update:value="emitValue"
  />
  <a-date-picker
    v-else-if="field.type === 'datetime'"
    :value="modelValue"
    show-time
    value-format="YYYY-MM-DD HH:mm:ss"
    style="width: 100%"
    :placeholder="`请选择${field.label}`"
    @update:value="emitValue"
  />
  <div v-else-if="field.type === 'upload'" class="upload-field">
    <a-upload
      :file-list="fileList"
      :multiple="uploadLimit > 1"
      :max-count="uploadLimit"
      :custom-request="customUpload"
      :before-upload="beforeUpload"
      @remove="onRemove"
    >
      <button type="button" class="lb-btn" :disabled="fileList.length >= uploadLimit">
        <UploadOutlined /> 上传附件
      </button>
    </a-upload>
    <div class="upload-field__tip">最多 {{ uploadLimit }} 个文件，单文件 ≤ 20MB</div>
  </div>
  <a-input
    v-else
    :value="modelValue"
    :placeholder="`请输入${field.label}`"
    @update:value="emitValue"
  />
</template>

<script setup>
import { computed } from 'vue'
import { message } from 'ant-design-vue'
import { UploadOutlined } from '@ant-design/icons-vue'
import { uploadDataPoolAttachment } from '../../api/dataCenter'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number, Array, Object], default: undefined },
})

const emit = defineEmits(['update:modelValue'])

const selectOptions = computed(() =>
  (props.field.props?.options || []).map((o) => ({
    value: o.value,
    label: o.label ?? o.value,
  })),
)

const uploadLimit = computed(() => {
  const limit = Number(props.field.props?.limit)
  return Number.isFinite(limit) && limit > 0 ? limit : 10
})

const fileList = computed(() => {
  const list = asArray(props.modelValue)
  return list.map((item, index) => ({
    uid: String(item.path || item.url || index),
    name: item.name || `文件${index + 1}`,
    status: 'done',
    url: item.url,
    response: item,
  }))
})

function asArray(value) {
  if (value == null || value === '') return []
  if (Array.isArray(value)) return value
  // 兼容 jsonb 未解析时的 { type, value } 形态
  if (typeof value === 'object' && typeof value.value === 'string') {
    try {
      const parsed = JSON.parse(value.value)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return []
}

function emitValue(value) {
  emit('update:modelValue', value)
}

function beforeUpload(file) {
  const max = 20 * 1024 * 1024
  if (file.size > max) {
    message.error('单文件不能超过 20MB')
    return false
  }
  if (fileList.value.length >= uploadLimit.value) {
    message.warning(`最多上传 ${uploadLimit.value} 个文件`)
    return false
  }
  return true
}

async function customUpload({ file, onSuccess, onError }) {
  try {
    const res = await uploadDataPoolAttachment(file)
    const data = res?.data
    if (!data?.url) {
      throw new Error('上传失败')
    }
    const next = [...asArray(props.modelValue), {
      name: data.name,
      url: data.url,
      path: data.path,
      size: data.size,
    }]
    emitValue(next)
    onSuccess(data)
  } catch (e) {
    onError(e)
  }
}

function onRemove(file) {
  const uid = file.uid
  const next = asArray(props.modelValue).filter((item, index) =>
    String(item.path || item.url || index) !== String(uid))
  emitValue(next)
  return true
}
</script>

<style scoped>
.upload-field__tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-mute);
}
</style>
