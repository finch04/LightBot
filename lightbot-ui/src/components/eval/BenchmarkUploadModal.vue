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
        <a-input v-model:value="form.name" placeholder="输入基准名称" />
      </a-form-item>
      <a-form-item label="描述">
        <a-textarea v-model:value="form.description" placeholder="可选描述" :rows="2" />
      </a-form-item>
      <a-form-item required>
        <template #label>
          JSONL 文件
          <QuestionCircleOutlined style="margin-left: 4px; color: #999; cursor: pointer;" @click="jsonlHelpVisible = true" />
        </template>
        <a-upload
          :before-upload="beforeUpload"
          :max-count="1"
          accept=".jsonl,.json"
        >
          <a-button>
            <UploadOutlined /> 选择文件
          </a-button>
        </a-upload>
        <div style="color: #bbb; font-size: 11px; margin-top: 4px;">每行一个 JSON 对象，包含 query、gold_answer、gold_chunk_ids 字段</div>
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
    </div>
  </a-modal>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import { UploadOutlined, QuestionCircleOutlined } from '@ant-design/icons-vue'
import { uploadBenchmark } from '../../api/knowledgeEval'

const props = defineProps({
  open: Boolean,
  knowledgeId: { type: String, required: true },
})
const emit = defineEmits(['update:open', 'success'])

const loading = ref(false)
const jsonlHelpVisible = ref(false)
const form = reactive({
  name: '',
  description: '',
  file: null,
})

function beforeUpload(file) {
  form.file = file
  return false
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
    message.success('上传成功')
    emit('update:open', false)
    emit('success')
    form.name = ''
    form.description = ''
    form.file = null
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
