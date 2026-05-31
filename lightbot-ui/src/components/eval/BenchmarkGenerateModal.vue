<template>
  <a-modal
    :open="open"
    title="AI 生成评估基准"
    @ok="handleOk"
    @cancel="$emit('update:open', false)"
    :maskClosable="false"
    width="480px"
  >
    <template #extra>
      <QuestionCircleOutlined style="color: #999; cursor: pointer; font-size: 16px;" @click="helpVisible = true" />
    </template>
    <a-form :model="form" layout="vertical">
      <a-form-item label="基准名称" required>
        <a-input v-model:value="form.name" placeholder="输入基准名称" />
      </a-form-item>
      <a-form-item label="描述">
        <a-textarea v-model:value="form.description" placeholder="可选描述" :rows="2" />
      </a-form-item>
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="生成数量">
            <a-input-number v-model:value="form.count" :min="1" :max="100" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item>
            <template #label>
              <span>相似 Chunks 数量
                <a-tooltip title="生成时参考的相邻片段数量，越多生成的题目越复杂">
                  <QuestionCircleOutlined style="color: #999; font-size: 13px; margin-left: 4px;" />
                </a-tooltip>
              </span>
            </template>
            <a-input-number v-model:value="form.neighborCount" :min="1" :max="10" style="width: 100%" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item label="生成模型">
        <ModelSelect v-model="form.modelValue" placeholder="不选则使用系统默认模型" @change="onModelChange" />
      </a-form-item>
    </a-form>
  </a-modal>

  <!-- AI 生成说明弹窗 -->
  <a-modal
    v-model:open="helpVisible"
    title="AI 生成评估基准说明"
    :footer="null"
    width="520px"
  >
    <div class="help-content">
      <p>AI 生成评估基准基于知识库中的实际内容，自动生成高质量的评估题目。</p>
      <p><strong>生成原理：</strong></p>
      <ol>
        <li>从知识库中随机采样文本片段作为"锚点"</li>
        <li>通过向量检索找到语义相关的相邻片段</li>
        <li>将这些片段作为上下文，调用 LLM 生成问题和标准答案</li>
        <li>自动关联标准片段 ID，用于后续检索指标计算</li>
      </ol>
      <p><strong>生成内容：</strong></p>
      <ul>
        <li><code>query</code> — 基于上下文生成的评估问题</li>
        <li><code>gold_answer</code> — 由上下文推导的标准答案</li>
        <li><code>gold_chunk_ids</code> — 问题相关的标准片段 ID</li>
      </ul>
      <p style="color: #999; font-size: 12px;">生成任务将进入任务队列异步执行，可在任务中心查看进度。</p>
    </div>
  </a-modal>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import { generateBenchmark } from '../../api/knowledgeEval'
import ModelSelect from '../ModelSelect.vue'

const props = defineProps({
  open: Boolean,
  knowledgeId: { type: String, required: true },
})
const emit = defineEmits(['update:open', 'success'])

const helpVisible = ref(false)
const form = reactive({
  name: '',
  description: '',
  count: 10,
  neighborCount: 3,
  modelValue: null,
})
const selectedModel = ref({ providerId: null, modelId: null })

function onModelChange({ providerId, modelId }) {
  selectedModel.value = { providerId, modelId }
}

async function handleOk() {
  if (!form.name?.trim()) {
    message.warning('请输入基准名称')
    return
  }
  try {
    const params = {
      name: form.name,
      description: form.description,
      count: form.count,
      neighborCount: form.neighborCount,
    }
    if (selectedModel.value.providerId) {
      params.providerId = selectedModel.value.providerId
      params.modelId = selectedModel.value.modelId
    }
    await generateBenchmark(props.knowledgeId, params)
    message.success('评估基准生成任务已进入任务队列')
    emit('update:open', false)
    // 延迟刷新，等待后端任务处理
    setTimeout(() => emit('success'), 1500)
    form.name = ''
    form.description = ''
    form.count = 10
    form.neighborCount = 3
    form.modelValue = null
  } catch (e) {
    message.error('提交失败: ' + (e.message || '未知错误'))
  }
}
</script>

<style scoped>
.help-content p { margin-bottom: 8px; line-height: 1.6; }
.help-content ol,
.help-content ul { padding-left: 20px; margin-bottom: 12px; }
.help-content li { margin-bottom: 4px; }
.help-content code { background: #f5f5f5; padding: 1px 4px; border-radius: 3px; font-size: 12px; }
</style>
