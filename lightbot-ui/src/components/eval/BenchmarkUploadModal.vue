<template>
  <a-modal
    :open="open"
    title="上传评估基准"
    :confirm-loading="loading"
    @ok="handleOk"
    @cancel="$emit('update:open', false)"
    :maskClosable="false"
    width="480px"
  >
    <a-form :model="form" layout="vertical">
      <a-form-item label="基准名称" required>
        <a-input v-model:value="form.name" placeholder="输入基准名称（最多50字）" :maxlength="50" show-count />
      </a-form-item>
      <a-form-item label="描述">
        <a-textarea v-model:value="form.description" placeholder="可选描述（最多200字）" :rows="2" :maxlength="200" show-count />
      </a-form-item>
      <a-form-item required>
        <template #label>
          JSONL 文件
          <QuestionCircleOutlined style="margin-left: 4px; color: #999; cursor: pointer;" @click="jsonlHelpVisible = true" />
        </template>
        <a-upload
          :before-upload="beforeUpload"
          :file-list="fileList"
          :max-count="1"
          accept=".jsonl,.json"
          @remove="handleRemove"
        >
          <a-button>
            <UploadOutlined /> 选择文件
          </a-button>
        </a-upload>
        <div style="color: #bbb; font-size: 11px; margin-top: 4px;">
          每行一个 JSON 对象，包含 query、gold_answer、gold_chunk_ids 字段；最大支持 10MB
        </div>
        <div v-if="fileSizeWarning" style="color: #faad14; font-size: 12px; margin-top: 4px;">
          <ExclamationCircleOutlined /> 文件较大，上传后将异步处理
        </div>
      </a-form-item>
    </a-form>
  </a-modal>

  <!-- JSONL 格式说明弹窗 -->
  <a-modal
    v-model:open="jsonlHelpVisible"
    title="JSONL 格式说明"
    :footer="null"
    width="560px"
  >
    <div class="jsonl-help">
      <p><strong>JSONL（JSON Lines）</strong>是一种每行一个独立 JSON 对象的文本格式，用于评估基准数据的批量导入。</p>
      <p><strong>每行必需字段：</strong></p>
      <ul>
        <li><code>query</code> — 评估问题（必填）</li>
        <li><code>gold_answer</code> — 标准答案（可选，用于 LLM 评判）</li>
        <li><code>gold_chunk_ids</code> — 标准片段 ID 数组（可选，用于检索指标计算）</li>
      </ul>
      <p><strong>示例文件内容：</strong></p>
      <pre class="jsonl-example">{"query":"什么是知识图谱？","gold_answer":"知识图谱是一种结构化的语义知识库","gold_chunk_ids":["101","102"]}
{"query":"RAG 的工作原理是什么？","gold_answer":"检索增强生成通过检索相关文档来辅助大模型回答问题","gold_chunk_ids":["203","204","205"]}
{"query":"向量检索的优势有哪些？","gold_answer":"向量检索支持语义相似度匹配，能够理解同义词和近义表达"}</pre>
      <p style="color: #999; font-size: 12px;">将上述内容保存为 <code>.jsonl</code> 文件即可上传。</p>
      <p style="color: #faad14; font-size: 12px; margin-top: 8px;">
        <ExclamationCircleOutlined /> 文件超过 2MB 时将自动提交到任务中心异步处理，处理进度可在任务列表中查看。
      </p>
    </div>
  </a-modal>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { message } from 'ant-design-vue'
import { UploadOutlined, QuestionCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons-vue'
import { uploadBenchmark } from '../../api/knowledgeEval'

const props = defineProps({
  open: Boolean,
  knowledgeId: { type: String, required: true },
})
const emit = defineEmits(['update:open', 'success'])

const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB
const ASYNC_THRESHOLD = 2 * 1024 * 1024 // 2MB 超过此阈值异步处理

const loading = ref(false)
const jsonlHelpVisible = ref(false)
const fileList = ref([])
const form = reactive({
  name: '',
  description: '',
  file: null,
})

const fileSizeWarning = computed(() => {
  return form.file && form.file.size > ASYNC_THRESHOLD
})

function validateJsonlContent(content) {
  // 检查是否为空
  if (!content || !content.trim()) {
    return { valid: false, error: '文件内容为空' }
  }
  const lines = content.split('\n').filter(line => line.trim())
  if (lines.length === 0) {
    return { valid: false, error: '文件内容为空' }
  }
  // 检查第一行是否为合法 JSON
  try {
    JSON.parse(lines[0])
  } catch (e) {
    return { valid: false, error: '文件格式不正确，请上传 JSONL 格式文件（每行一个 JSON 对象）' }
  }
  for (let i = 0; i < lines.length; i++) {
    try {
      const obj = JSON.parse(lines[i])
      if (!obj.query || typeof obj.query !== 'string') {
        return { valid: false, error: `第 ${i + 1} 行缺少 query 字段或格式错误` }
      }
    } catch (e) {
      return { valid: false, error: `第 ${i + 1} 行 JSON 解析失败: ${e.message}` }
    }
  }
  return { valid: true, lineCount: lines.length }
}

function beforeUpload(file) {
  // 1. 校验文件扩展名
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!ext || !['jsonl', 'json'].includes(ext)) {
    message.error('文件格式不正确，请上传 .jsonl 或 .json 格式文件')
    return false
  }
  // 2. 校验文件大小
  if (file.size > MAX_FILE_SIZE) {
    message.error(`文件大小超过限制（最大 ${MAX_FILE_SIZE / 1024 / 1024}MB）`)
    return false
  }
  // 3. 读取并校验内容
  const reader = new FileReader()
  reader.onload = (e) => {
    const content = e.target.result
    const result = validateJsonlContent(content)
    if (!result.valid) {
      message.error(result.error)
      form.file = null
      fileList.value = []
    } else {
      message.success(`文件校验通过，共 ${result.lineCount} 条数据`)
      form.file = file
      fileList.value = [file]
    }
  }
  reader.readAsText(file)
  return false
}

function handleRemove() {
  form.file = null
  fileList.value = []
}

async function handleOk() {
  if (!form.name?.trim()) {
    message.warning('请输入基准名称')
    return
  }
  if (!form.file) {
    message.warning('请选择 JSONL 文件')
    return
  }
  loading.value = true
  try {
    await uploadBenchmark(props.knowledgeId, form.name, form.description, form.file)
    if (fileSizeWarning.value) {
      message.success('上传成功，后台异步处理中，可在任务中心查看进度')
    } else {
      message.success('上传成功')
    }
    emit('update:open', false)
    // 异步路径延长刷新等待
    setTimeout(() => emit('success'), fileSizeWarning.value ? 2000 : 500)
    form.name = ''
    form.description = ''
    form.file = null
    fileList.value = []
  } catch (e) {
    message.error('上传失败: ' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.jsonl-help p { margin-bottom: 8px; line-height: 1.6; }
.jsonl-help ul { padding-left: 20px; margin-bottom: 12px; }
.jsonl-help li { margin-bottom: 4px; }
.jsonl-help code { background: #f5f5f5; padding: 1px 4px; border-radius: 3px; font-size: 12px; }
.jsonl-example {
  background: #f6f8fa; border: 1px solid #e8e8e8; border-radius: 6px;
  padding: 12px; font-size: 12px; line-height: 1.6; overflow-x: auto;
  white-space: pre-wrap; word-break: break-all;
}
</style>
