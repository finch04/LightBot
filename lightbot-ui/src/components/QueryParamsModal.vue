<template>
  <a-modal
    v-model:open="visible"
    title="检索配置"
    :width="480"
    :maskClosable="false"
    @cancel="handleCancel"
  >
    <a-form :model="form" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
      <!-- 检索模式 -->
      <a-form-item>
        <template #label>
          <span>检索模式</span>
          <a-tooltip :title="isMilvus ? 'vector=纯向量语义检索；keyword=BM25关键词检索；hybrid=两者加权融合' : 'vector=向量语义检索；keyword=全文检索；hybrid=两者RRF融合'">
            <QuestionCircleOutlined class="field-tip-icon" />
          </a-tooltip>
        </template>
        <a-select v-model:value="form.search_mode">
          <a-select-option value="vector">向量检索</a-select-option>
          <a-select-option value="keyword">{{ isMilvus ? '关键词检索' : '全文检索' }}</a-select-option>
          <a-select-option value="hybrid">混合检索</a-select-option>
        </a-select>
      </a-form-item>

      <a-form-item>
        <template #label>
          <span>返回数量</span>
          <a-tooltip title="检索返回的最大文档块数量。值越大召回越全，但噪声也可能增加，建议 3-10">
            <QuestionCircleOutlined class="field-tip-icon" />
          </a-tooltip>
        </template>
        <a-input-number v-model:value="form.final_top_k" :min="1" :max="100" style="width: 100%" />
      </a-form-item>

      <a-form-item>
        <template #label>
          <span>相似度阈值</span>
          <a-tooltip title="仅返回相似度分数 ≥ 该阈值的文档块。值越高结果越精准但可能漏召回，建议 0.3-0.7">
            <QuestionCircleOutlined class="field-tip-icon" />
          </a-tooltip>
        </template>
        <a-input-number v-model:value="form.similarity_threshold" :min="0" :max="1" :step="0.05" style="width: 100%" />
      </a-form-item>

      <!-- Milvus hybrid 模式专属参数 -->
      <template v-if="isMilvus && form.search_mode === 'hybrid'">
        <a-form-item>
          <template #label>
            <span>向量权重</span>
            <a-tooltip title="混合检索中向量语义的权重占比。值越大越偏向语义理解，建议 0.5-0.8">
              <QuestionCircleOutlined class="field-tip-icon" />
            </a-tooltip>
          </template>
          <a-input-number v-model:value="form.vector_weight" :min="0" :max="1" :step="0.1" style="width: 100%" />
        </a-form-item>

        <a-form-item>
          <template #label>
            <span>BM25权重</span>
            <a-tooltip title="混合检索中BM25关键词的权重占比。值越大越偏向精确关键词匹配，建议 0.2-0.5">
              <QuestionCircleOutlined class="field-tip-icon" />
            </a-tooltip>
          </template>
          <a-input-number v-model:value="form.bm25_weight" :min="0" :max="1" :step="0.1" style="width: 100%" />
        </a-form-item>

        <a-form-item>
          <template #label>
            <span>BM25候选数</span>
            <a-tooltip title="BM25检索阶段的候选文档数量，越大召回越全但耗时越长，建议为返回数量的2-3倍">
              <QuestionCircleOutlined class="field-tip-icon" />
            </a-tooltip>
          </template>
          <a-input-number v-model:value="form.bm25_top_k" :min="1" :max="200" style="width: 100%" />
        </a-form-item>
      </template>

      <!-- pgvector hybrid 模式专属参数 -->
      <template v-if="!isMilvus && form.search_mode === 'hybrid'">
        <a-form-item>
          <template #label>
            <span>向量权重</span>
            <a-tooltip title="RRF融合中向量检索的权重。值越大越偏向语义理解，建议 0.5-0.8">
              <QuestionCircleOutlined class="field-tip-icon" />
            </a-tooltip>
          </template>
          <a-input-number v-model:value="form.vector_weight" :min="0" :max="1" :step="0.1" style="width: 100%" />
        </a-form-item>

        <a-form-item>
          <template #label>
            <span>关键词权重</span>
            <a-tooltip title="RRF融合中全文检索的权重。值越大越偏向精确关键词匹配，建议 0.2-0.5">
              <QuestionCircleOutlined class="field-tip-icon" />
            </a-tooltip>
          </template>
          <a-input-number v-model:value="form.keyword_weight" :min="0" :max="1" :step="0.1" style="width: 100%" />
        </a-form-item>
      </template>

      <!-- Reranker（通用） -->
      <a-divider style="margin: 12px 0" />
      <a-form-item>
        <template #label>
          <span>启用重排序</span>
          <a-tooltip title="使用 Reranker 模型对检索结果进行精排，提升相关性">
            <QuestionCircleOutlined class="field-tip-icon" />
          </a-tooltip>
        </template>
        <a-switch v-model:checked="form.use_reranker" />
      </a-form-item>

      <template v-if="form.use_reranker">
        <a-form-item>
          <template #label>
            <span>重排序模型</span>
            <a-tooltip title="指定重排序模型，不选择时使用系统默认配置的重排序模型">
              <QuestionCircleOutlined class="field-tip-icon" />
            </a-tooltip>
          </template>
          <ModelSelect v-model="form.reranker_model" model-type="rerank" placeholder="系统默认" />
        </a-form-item>

        <a-form-item>
          <template #label>
            <span>召回候选数</span>
            <a-tooltip title="重排序前的候选文档数量，需 ≥ 返回数量。越大重排序效果越好但耗时越长">
              <QuestionCircleOutlined class="field-tip-icon" />
            </a-tooltip>
          </template>
          <a-input-number v-model:value="form.recall_top_k" :min="1" :max="200" style="width: 100%" />
        </a-form-item>
      </template>

      <!-- 图检索（仅 Milvus 知识库） -->
      <template v-if="isMilvus">
      <a-divider style="margin: 12px 0" />
      <a-form-item>
        <template #label>
          <span>启用图检索</span>
          <a-tooltip title="基于 Milvus 向量检索 + Neo4j 图遍历的检索增强，通过 PPR 算法排序，与常规检索结果融合">
            <QuestionCircleOutlined class="field-tip-icon" />
          </a-tooltip>
        </template>
        <a-switch v-model:checked="form.use_graph_retrieval" />
      </a-form-item>

      <template v-if="form.use_graph_retrieval">
        <a-form-item>
          <template #label>
            <span>实体候选数</span>
            <a-tooltip title="Milvus Entity 向量检索的候选数量，作为 PPR 种子节点">
              <QuestionCircleOutlined class="field-tip-icon" />
            </a-tooltip>
          </template>
          <a-input-number v-model:value="form.graph_entity_top_k" :min="1" :max="100" style="width: 100%" />
        </a-form-item>

        <a-form-item>
          <template #label>
            <span>三元组候选数</span>
            <a-tooltip title="Milvus Triple 向量检索的候选数量，用于补充种子节点">
              <QuestionCircleOutlined class="field-tip-icon" />
            </a-tooltip>
          </template>
          <a-input-number v-model:value="form.graph_triple_top_k" :min="1" :max="100" style="width: 100%" />
        </a-form-item>

        <a-form-item>
          <template #label>
            <span>子图最大节点</span>
            <a-tooltip title="Neo4j 2-hop 子图遍历的最大节点数，越大越全面但耗时越长">
              <QuestionCircleOutlined class="field-tip-icon" />
            </a-tooltip>
          </template>
          <a-input-number v-model:value="form.graph_max_nodes" :min="10" :max="500" style="width: 100%" />
        </a-form-item>

        <a-form-item>
          <template #label>
            <span>图检索返回数</span>
            <a-tooltip title="图检索最终返回的结果数量（实体描述+三元组描述）">
              <QuestionCircleOutlined class="field-tip-icon" />
            </a-tooltip>
          </template>
          <a-input-number v-model:value="form.graph_top_k" :min="1" :max="50" style="width: 100%" />
        </a-form-item>

        <a-form-item>
          <template #label>
            <span>图检索权重</span>
            <a-tooltip title="RRF 融合中图检索结果的权重。值越大越偏向图谱语义，建议 0.2-0.5">
              <QuestionCircleOutlined class="field-tip-icon" />
            </a-tooltip>
          </template>
          <a-input-number v-model:value="form.graph_weight" :min="0" :max="1" :step="0.1" style="width: 100%" />
        </a-form-item>

        <a-form-item>
          <template #label>
            <span>
              PPR 阻尼系数
              <a-popover trigger="click" placement="right" :overlay-style="{ maxWidth: '360px' }">
                <template #content>
                  <div style="font-size: 13px; line-height: 1.6;">
                    <p style="font-weight: 600; margin-bottom: 8px;">Personalized PageRank (PPR)</p>
                    <p>PPR 是一种图排序算法，用于衡量节点相对于种子节点的"重要性"。</p>
                    <p style="margin-top: 8px;"><b>核心思想：</b></p>
                    <p>从种子节点出发，每一步以概率 <b>d</b> 沿边随机游走，以概率 <b>1-d</b> 跳回种子节点。经过多轮迭代后，节点被访问的概率即为其 PPR 分数。</p>
                    <p style="margin-top: 8px;"><b>参数含义：</b></p>
                    <ul style="padding-left: 16px; margin: 4px 0;">
                      <li><b>d = 0.85</b>（默认）：更依赖图结构，结果更全面</li>
                      <li><b>d = 0.5</b>：更聚焦种子节点附近，结果更精准</li>
                      <li><b>d → 1</b>：纯随机游走，可能偏离种子</li>
                    </ul>
                    <p style="margin-top: 8px;"><b>实现策略：</b></p>
                    <ol style="padding-left: 16px; margin: 4px 0;">
                      <li>Milvus 向量检索获取种子实体</li>
                      <li>Neo4j 查询种子的 2-hop 子图</li>
                      <li>迭代 15 轮 PPR 计算节点分数</li>
                      <li>按分数排序，取 top 实体和三元组</li>
                    </ol>
                  </div>
                </template>
                <QuestionCircleOutlined class="field-tip-icon" style="cursor: pointer;" />
              </a-popover>
            </span>
          </template>
          <a-input-number v-model:value="form.ppr_damping" :min="0" :max="1" :step="0.05" style="width: 100%" />
        </a-form-item>
      </template>
      </template>
    </a-form>

    <template #footer>
      <div style="display: flex; justify-content: space-between;">
        <button class="btn-outline-sm" @click="handleReset">恢复默认</button>
        <div style="display: flex; gap: 8px;">
          <button class="btn-outline-sm" @click="handleCancel">取消</button>
          <button class="btn-outline-sm" @click="handleApply">仅应用测试（不保存）</button>
          <button class="btn-primary-sm" :disabled="saving" @click="handleSave">
            {{ saving ? '保存中...' : '保存为默认' }}
          </button>
        </div>
      </div>
    </template>
  </a-modal>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { message } from 'ant-design-vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import { getQueryParams, updateQueryParams } from '../api/knowledge'
import ModelSelect from './ModelSelect.vue'

const props = defineProps({
  knowledgeId: { type: String, required: true },
  knowledgeType: { type: String, default: 'pg' },
})

const emit = defineEmits(['apply'])

const visible = ref(false)
const saving = ref(false)

const isMilvus = computed(() => props.knowledgeType === 'milvus')

const pgDefaults = {
  search_mode: 'vector',
  final_top_k: 5,
  similarity_threshold: 0.5,
  vector_weight: 0.7,
  keyword_weight: 0.3,
  use_reranker: false,
  reranker_model: '',
  recall_top_k: 50,
  use_graph_retrieval: false,
  graph_entity_top_k: 10,
  graph_triple_top_k: 10,
  graph_max_nodes: 100,
  graph_top_k: 5,
  graph_weight: 0.3,
  ppr_damping: 0.85,
}

const milvusDefaults = {
  search_mode: 'vector',
  final_top_k: 10,
  similarity_threshold: 0.0,
  vector_weight: 0.7,
  bm25_weight: 0.3,
  bm25_top_k: 30,
  use_reranker: false,
  reranker_model: '',
  recall_top_k: 50,
  use_graph_retrieval: false,
  graph_entity_top_k: 10,
  graph_triple_top_k: 10,
  graph_max_nodes: 100,
  graph_top_k: 5,
  graph_weight: 0.3,
  ppr_damping: 0.85,
}

const form = reactive({ ...pgDefaults })

function getDefaults() {
  return props.knowledgeType === 'milvus' ? { ...milvusDefaults } : { ...pgDefaults }
}

async function open() {
  try {
    const res = await getQueryParams(props.knowledgeId)
    const saved = res.data || {}
    const defaults = getDefaults()
    Object.assign(form, { ...defaults, ...saved })
  } catch {
    Object.assign(form, getDefaults())
  }
  visible.value = true
}

function handleReset() {
  Object.assign(form, getDefaults())
}

function handleCancel() {
  visible.value = false
}

function handleApply() {
  emit('apply', { ...form })
  message.success('已应用到本次检索测试')
}

async function handleSave() {
  saving.value = true
  try {
    await updateQueryParams(props.knowledgeId, { ...form })
    emit('apply', { ...form })
    message.success('检索配置已保存')
    visible.value = false
  } catch {
    // interceptor handled
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.field-tip-icon {
  font-size: 13px;
  color: #a1a1aa;
  cursor: help;
  margin-left: 4px;
}
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
.btn-outline-sm:hover {
  border-color: #0070f3;
  color: #0070f3;
}
.btn-primary-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: #171717;
  color: #fff;
  border: none;
  border-radius: 100px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary-sm:hover:not(:disabled) {
  background: #27272a;
}
.btn-primary-sm:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}
</style>
