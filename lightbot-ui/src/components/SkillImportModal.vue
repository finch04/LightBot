<template>
  <a-modal
    :open="open"
    title="导入 Skill"
    :width="600"
    :maskClosable="false"
    :footer="null"
    @cancel="handleCancel"
  >
    <!-- 步骤一：上传 ZIP -->
    <div v-if="step === 'upload'">
      <a-upload-dragger
        :before-upload="handleBeforeUpload"
        :show-upload-list="false"
        accept=".zip"
      >
        <p style="font-size: 32px; color: var(--color-mute); margin-bottom: 8px">
          <CloudUploadOutlined />
        </p>
        <p style="font-size: 14px; color: #52525b">拖拽 ZIP 文件到此处，或点击上传</p>
        <p style="font-size: 12px; color: #a1a1aa">ZIP 中必须包含 SKILL.md 文件，大小不超过 10MB</p>
      </a-upload-dragger>
    </div>

    <!-- 步骤二：预览 -->
    <div v-if="step === 'preview'">
      <a-spin :spinning="loading">
        <a-descriptions bordered :column="1" size="small" style="margin-bottom: 16px">
          <a-descriptions-item label="Slug">
            <a-input
              v-if="preview"
              v-model:value="overrideSlug"
              :placeholder="preview.slug || '自定义 slug'"
              size="small"
              style="width: 240px"
            />
          </a-descriptions-item>
          <a-descriptions-item label="名称">{{ preview?.name || '—' }}</a-descriptions-item>
          <a-descriptions-item label="描述">{{ preview?.description || '—' }}</a-descriptions-item>
          <a-descriptions-item label="版本">{{ preview?.version || '1.0.0' }}</a-descriptions-item>
          <a-descriptions-item label="依赖工具">
            {{ preview?.toolDependencies?.length ? preview.toolDependencies.join(', ') : '无' }}
          </a-descriptions-item>
          <a-descriptions-item label="依赖 Skill">
            {{ preview?.skillDependencies?.length ? preview.skillDependencies.join(', ') : '无' }}
          </a-descriptions-item>
          <a-descriptions-item label="文件列表">
            {{ preview?.fileNames?.join(', ') || '—' }}
          </a-descriptions-item>
        </a-descriptions>
      </a-spin>
      <div style="display: flex; justify-content: flex-end; gap: 8px">
        <button class="btn-cancel" @click="handleCancel">取消</button>
        <button class="btn-primary-sm" :disabled="committing" @click="handleCommit">
          {{ committing ? '导入中...' : '确认导入' }}
        </button>
      </div>
    </div>
  </a-modal>
</template>

<script setup>
import { ref, watch } from 'vue'
import { CloudUploadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { importSkillPreview, importSkillCommit } from '../api/skill'

const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB

const props = defineProps({ open: Boolean })
const emit = defineEmits(['update:open', 'imported'])

const step = ref('upload')
const loading = ref(false)
const committing = ref(false)
const preview = ref(null)
const draftId = ref(null)
const overrideSlug = ref('')

watch(() => props.open, (val) => {
  if (val) {
    step.value = 'upload'
    preview.value = null
    draftId.value = null
    overrideSlug.value = ''
  }
})

function handleBeforeUpload(file) {
  // 文件大小校验
  if (file.size > MAX_FILE_SIZE) {
    message.error(`文件大小超过限制（${(file.size / 1024 / 1024).toFixed(1)}MB > 10MB），请压缩后重试`)
    return false
  }
  if (!file.name.endsWith('.zip')) {
    message.error('仅支持 .zip 格式的文件')
    return false
  }

  loading.value = true
  step.value = 'preview'
  importSkillPreview(file)
    .then(res => {
      preview.value = res.data
      draftId.value = res.data.draftId
      overrideSlug.value = res.data.slug || ''
    })
    .catch(() => {
      step.value = 'upload'
      message.error('ZIP 解析失败，请检查格式')
    })
    .finally(() => {
      loading.value = false
    })
  return false // 阻止 antd 自动上传
}

async function handleCommit() {
  if (!draftId.value) return
  committing.value = true
  try {
    await importSkillCommit(draftId.value, overrideSlug.value || undefined)
    message.success('Skill 导入成功')
    emit('update:open', false)
    emit('imported')
  } catch (e) {
    // interceptor handles error
  } finally {
    committing.value = false
  }
}

function handleCancel() {
  emit('update:open', false)
}
</script>

<style scoped>
.btn-cancel {
  padding: 6px 14px;
  background: var(--color-canvas);
  color: var(--color-mute);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.btn-cancel:hover { border-color: var(--color-ink); color: var(--color-ink); }
.btn-primary-sm {
  padding: 6px 14px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary-sm:hover:not(:disabled) { background: #27272a; }
.btn-primary-sm:disabled { background: var(--color-hairline-strong); color: var(--color-mute); cursor: not-allowed; }
</style>
