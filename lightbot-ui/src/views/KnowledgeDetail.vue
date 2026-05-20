<template>
  <div class="page">
    <div class="page-header">
      <div>
        <button class="btn-back" @click="router.push('/knowledge')">
          <ArrowLeftOutlined /> 返回
        </button>
        <h1 class="page-title">{{ knowledge.name }}</h1>
        <p class="page-desc">{{ knowledge.description || '暂无描述' }}</p>
      </div>
      <button class="btn-primary-sm" @click="openEditDialog">
        <EditOutlined /> 编辑
      </button>
    </div>

    <div class="content-grid">
      <!-- 文档列表 -->
      <div class="panel">
        <div class="panel-header">
          <h3>文档列表</h3>
          <a-upload :show-upload-list="false" :before-upload="handleUpload" accept=".md">
            <button class="btn-primary-sm">上传文档</button>
          </a-upload>
        </div>
        <div class="doc-list">
          <div v-for="doc in documents" :key="doc.id" class="doc-item" @click="openDocModal(doc)">
            <div class="doc-info">
              <span class="doc-name">{{ doc.name }}</span>
              <span class="doc-status" :class="doc.status?.code || doc.status">
                {{ statusText(doc.status?.code || doc.status) }}
              </span>
            </div>
            <div class="doc-meta">
              {{ doc.chunkCount || 0 }} 分块
              <button class="btn-link danger" @click.stop="deleteDoc(doc.id)">删除</button>
            </div>
          </div>
          <div v-if="documents.length === 0" class="doc-empty">暂无文档</div>
        </div>
      </div>

      <!-- RAG 问答 + 思维导图 -->
      <div class="panel">
        <a-tabs v-model:activeKey="activeTab">
          <a-tab-pane key="ask" tab="RAG 问答">
            <div class="rag-section">
              <div class="rag-messages" ref="ragRef">
                <div v-for="(msg, i) in ragMessages" :key="i" :class="['rag-msg', msg.role]">
                  <div class="rag-content">{{ msg.content }}</div>
                </div>
              </div>
              <div class="rag-input">
                <input
                  v-model="ragQuestion"
                  placeholder="基于知识库提问..."
                  @keydown.enter="askRag"
                />
                <button class="btn-primary-sm" :disabled="!ragQuestion.trim() || ragLoading" @click="askRag">
                  提问
                </button>
              </div>
            </div>
          </a-tab-pane>
          <a-tab-pane key="mindmap" tab="思维导图">
            <div class="mindmap-section">
              <div v-if="mindmapData" class="mindmap-container">
                <svg ref="mindmapSvgRef" class="mindmap-svg"></svg>
                <div class="mindmap-actions">
                  <button class="btn-primary-sm" :disabled="mindmapLoading" @click="handleGenerateMindmap">
                    {{ mindmapLoading ? '生成中...' : '重新生成' }}
                  </button>
                </div>
              </div>
              <div v-else class="mindmap-empty">
                <p v-if="documents.length === 0">请先上传文档后再生成思维导图</p>
                <p v-else>暂无思维导图，点击下方按钮AI自动生成</p>
                <button class="btn-primary-sm" :disabled="mindmapLoading || documents.length === 0" @click="handleGenerateMindmap">
                  {{ mindmapLoading ? '生成中...' : '生成思维导图' }}
                </button>
              </div>
            </div>
          </a-tab-pane>
        </a-tabs>
      </div>
    </div>

    <!-- 文档预览/分块弹窗 -->
    <a-modal
      v-model:open="docModalVisible"
      :title="currentDoc?.name || '文档详情'"
      :width="720"
      :footer="null"
    >
      <a-tabs v-model:activeKey="docModalTab">
        <a-tab-pane key="preview" tab="文档预览">
          <div class="modal-preview" v-if="previewContent">
            <pre>{{ previewContent }}</pre>
          </div>
          <div v-else class="modal-empty">加载中...</div>
        </a-tab-pane>
        <a-tab-pane key="chunks" tab="分块列表">
          <div class="chunk-list" v-if="chunks.length > 0">
            <div v-for="(chunk, i) in chunks" :key="chunk.id" class="chunk-item">
              <div class="chunk-header">
                <span class="chunk-index">#{{ chunk.chunkIndex ?? i + 1 }}</span>
                <span class="chunk-meta">{{ chunk.tokenCount || 0 }} tokens</span>
              </div>
              <pre class="chunk-content">{{ chunk.content }}</pre>
            </div>
          </div>
          <div v-else class="modal-empty">暂无分块数据</div>
        </a-tab-pane>
      </a-tabs>
    </a-modal>

    <!-- 编辑知识库弹窗 -->
    <a-modal v-model:open="editVisible" title="编辑知识库" :width="480" @ok="handleEdit" :confirm-loading="editSubmitting">
      <a-form :model="editForm" :label-col="{ span: 6 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="editForm.name" placeholder="知识库名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="editForm.description" :rows="3" placeholder="知识库描述" />
        </a-form-item>
        <a-form-item label="Embed模型">
          <a-input v-model:value="editForm.embeddingModel" />
        </a-form-item>
        <a-form-item label="分块大小">
          <a-input-number v-model:value="editForm.chunkSize" :min="100" :max="2000" :step="100" style="width: 100%" />
        </a-form-item>
        <a-form-item label="RAG Top K">
          <a-input-number v-model:value="editForm.ragTopK" :min="1" :max="20" style="width: 100%" />
        </a-form-item>
        <a-form-item label="RAG 相似度阈值">
          <a-input-number v-model:value="editForm.ragThreshold" :min="0" :max="1" :step="0.05" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined, EditOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getKnowledge, updateKnowledge, getDocuments, uploadDocument, deleteDocument,
  previewDocument, getChunks, askKnowledge, generateMindmap, getMindmap,
} from '../api/knowledge'
import { Transformer } from 'markmap-lib'
import { Markmap } from 'markmap-view'

const route = useRoute()
const router = useRouter()
const knowledgeId = route.params.id

const knowledge = ref({})
const documents = ref([])
const activeTab = ref('ask')
const ragQuestion = ref('')
const ragMessages = ref([])
const ragLoading = ref(false)
const ragRef = ref(null)
const mindmapData = ref(null)
const mindmapLoading = ref(false)
const mindmapSvgRef = ref(null)

// 文档弹窗
const docModalVisible = ref(false)
const docModalTab = ref('preview')
const currentDoc = ref(null)
const previewContent = ref('')
const chunks = ref([])

// 编辑弹窗
const editVisible = ref(false)
const editSubmitting = ref(false)
const editForm = reactive({
  name: '',
  description: '',
  embeddingModel: '',
  chunkSize: 512,
  chunkOverlap: 50,
  ragTopK: 5,
  ragThreshold: 0.7,
})

async function loadKnowledge() {
  const res = await getKnowledge(knowledgeId)
  knowledge.value = res.data
}

async function loadDocuments() {
  const res = await getDocuments(knowledgeId)
  documents.value = res.data || []
}

async function handleUpload(file) {
  try {
    await uploadDocument(knowledgeId, file)
    message.success('上传成功，正在处理...')
    setTimeout(loadDocuments, 1000)
  } catch (e) {
    message.error('上传失败')
  }
  return false
}

// ========== 文档弹窗 ==========

async function openDocModal(doc) {
  currentDoc.value = doc
  docModalTab.value = 'preview'
  previewContent.value = ''
  chunks.value = []
  docModalVisible.value = true

  // 并行加载预览和分块
  const [previewRes, chunksRes] = await Promise.allSettled([
    previewDocument(doc.id),
    getChunks(doc.id),
  ])
  if (previewRes.status === 'fulfilled') {
    previewContent.value = previewRes.value.data
  }
  if (chunksRes.status === 'fulfilled') {
    chunks.value = chunksRes.value.data || []
  }
}

// ========== 编辑知识库 ==========

function openEditDialog() {
  const k = knowledge.value
  // 解析已有的config JSONB
  let config = {}
  try {
    config = typeof k.config === 'string' ? JSON.parse(k.config) : (k.config || {})
  } catch { config = {} }

  Object.assign(editForm, {
    name: k.name || '',
    description: k.description || '',
    embeddingModel: k.embeddingModel || '',
    chunkSize: k.chunkSize || 512,
    chunkOverlap: k.chunkOverlap || 50,
    ragTopK: config.ragTopK ?? 5,
    ragThreshold: config.ragThreshold ?? 0.7,
  })
  editVisible.value = true
}

async function handleEdit() {
  if (!editForm.name.trim()) return message.warning('请输入名称')
  editSubmitting.value = true
  try {
    const config = JSON.stringify({ ragTopK: editForm.ragTopK, ragThreshold: editForm.ragThreshold })
    await updateKnowledge({
      id: knowledgeId,
      name: editForm.name,
      description: editForm.description,
      embeddingModel: editForm.embeddingModel,
      chunkSize: editForm.chunkSize,
      chunkOverlap: editForm.chunkOverlap,
      config,
    })
    message.success('更新成功')
    editVisible.value = false
    loadKnowledge()
  } finally {
    editSubmitting.value = false
  }
}

// ========== 删除文档 ==========

function deleteDoc(docId) {
  Modal.confirm({
    title: '确认删除',
    content: '删除后文档将无法恢复，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteDocument(docId)
        message.success('删除成功')
        loadDocuments()
      } catch (e) {
        message.error('删除失败')
      }
    },
  })
}

// ========== RAG 问答 ==========

async function askRag() {
  const q = ragQuestion.value.trim()
  if (!q || ragLoading.value) return
  ragMessages.value.push({ role: 'user', content: q })
  ragQuestion.value = ''
  ragLoading.value = true
  try {
    const res = await askKnowledge(knowledgeId, q)
    ragMessages.value.push({ role: 'assistant', content: res.data })
  } catch (e) {
    ragMessages.value.push({ role: 'assistant', content: '查询失败：' + (e.message || '未知错误') })
  } finally {
    ragLoading.value = false
  }
}

function statusText(s) {
  const map = { pending: '待处理', processing: '处理中', completed: '已完成', failed: '失败' }
  return map[s] || s
}

// ========== 思维导图 ==========

async function loadMindmap() {
  try {
    const res = await getMindmap(knowledgeId)
    if (res.data) {
      mindmapData.value = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
      await nextTick()
      renderMindmap()
    }
  } catch (e) {
    // 未生成过思维导图，忽略
  }
}

async function handleGenerateMindmap() {
  if (documents.value.length === 0) {
    message.warning('请先上传文档后再生成思维导图')
    return
  }
  mindmapLoading.value = true
  try {
    const res = await generateMindmap(knowledgeId)
    mindmapData.value = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
    message.success('思维导图生成成功')
    await nextTick()
    renderMindmap()
  } catch (e) {
    message.error('生成失败：' + (e.message || '未知错误'))
  } finally {
    mindmapLoading.value = false
  }
}

function renderMindmap() {
  if (!mindmapSvgRef.value || !mindmapData.value) return
  try {
    const tree = mindmapData.value
    if (!tree || !tree.content) {
      message.error('思维导图数据结构异常，请重新生成')
      mindmapData.value = null
      return
    }
    const md = jsonToMarkdown(tree, 0)
    const transformer = new Transformer()
    const { root } = transformer.transform(md)
    mindmapSvgRef.value.innerHTML = ''
    Markmap.create(mindmapSvgRef.value, null, root)
  } catch (e) {
    console.error('[Mindmap] 渲染失败:', e)
    message.error('思维导图渲染失败，请重新生成')
    mindmapData.value = null
  }
}

function jsonToMarkdown(node, level) {
  const prefix = '#'.repeat(level + 1)
  let md = `${prefix} ${node.content}\n`
  if (node.children) {
    for (const child of node.children) {
      md += jsonToMarkdown(child, level + 1)
    }
  }
  return md
}

onMounted(() => {
  loadKnowledge()
  loadDocuments()
  loadMindmap()
})
</script>

<style scoped>
.page {
  padding: 32px;
  height: 100vh;
  overflow-y: auto;
  background: #fafafa;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
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
.btn-back:hover {
  color: #0070f3;
}
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

.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.panel {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 20px;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.panel-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #171717;
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

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.doc-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid #f5f5f5;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.doc-item:hover {
  border-color: #0070f3;
}
.doc-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.doc-name {
  font-size: 14px;
  color: #171717;
}
.doc-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 100px;
}
.doc-status.completed {
  background: #dcfce7;
  color: #16a34a;
}
.doc-status.pending,
.doc-status.processing {
  background: #fef3c7;
  color: #d97706;
}
.doc-status.failed {
  background: #fee2e2;
  color: #dc2626;
}
.doc-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #a1a1aa;
}
.btn-link {
  background: none;
  border: none;
  color: #0070f3;
  cursor: pointer;
  font-size: 13px;
}
.btn-link.danger {
  color: #ee0000;
}
.doc-empty {
  text-align: center;
  padding: 40px;
  color: #a1a1aa;
}

/* RAG */
.rag-section {
  display: flex;
  flex-direction: column;
  height: 400px;
}
.rag-messages {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 12px;
}
.rag-msg.user {
  align-self: flex-end;
  background: #f5f5f5;
  padding: 8px 12px;
  border-radius: 8px;
  max-width: 80%;
  font-size: 14px;
}
.rag-msg.assistant {
  align-self: flex-start;
  background: #eff6ff;
  padding: 8px 12px;
  border-radius: 8px;
  max-width: 80%;
  font-size: 14px;
}
.rag-input {
  display: flex;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #ebebeb;
}
.rag-input input {
  flex: 1;
  border: 1px solid #ebebeb;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 14px;
  outline: none;
}
.rag-input input:focus {
  border-color: #171717;
}

/* 思维导图 */
.mindmap-section {
  min-height: 400px;
}
.mindmap-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mindmap-svg {
  width: 100%;
  height: 420px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}
.mindmap-actions {
  display: flex;
  justify-content: flex-end;
}
.mindmap-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
  color: #a1a1aa;
}
.mindmap-empty p {
  margin-bottom: 16px;
}

/* 文档弹窗 */
.modal-preview {
  max-height: 500px;
  overflow-y: auto;
}
.modal-preview pre {
  background: #f5f5f5;
  padding: 16px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.modal-empty {
  text-align: center;
  padding: 40px;
  color: #a1a1aa;
}

/* 分块列表 */
.chunk-list {
  max-height: 500px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.chunk-item {
  border: 1px solid #ebebeb;
  border-radius: 8px;
  overflow: hidden;
}
.chunk-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f9f9f9;
  border-bottom: 1px solid #ebebeb;
}
.chunk-index {
  font-size: 13px;
  font-weight: 600;
  color: #171717;
}
.chunk-meta {
  font-size: 12px;
  color: #a1a1aa;
}
.chunk-content {
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  max-height: 200px;
  overflow-y: auto;
}
</style>
