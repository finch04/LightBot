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
          <div v-for="doc in documents" :key="doc.id" class="doc-item">
            <div class="doc-info">
              <span class="doc-name">{{ doc.name }}</span>
              <span class="doc-status" :class="doc.status?.code || doc.status">
                {{ statusText(doc.status?.code || doc.status) }}
              </span>
            </div>
            <div class="doc-meta">
              {{ doc.chunkCount || 0 }} 分块
              <button class="btn-link" @click="previewDoc(doc)">预览</button>
              <button class="btn-link danger" @click="deleteDoc(doc.id)">删除</button>
            </div>
          </div>
          <div v-if="documents.length === 0" class="doc-empty">暂无文档</div>
        </div>
      </div>

      <!-- 预览 / RAG 问答 -->
      <div class="panel">
        <a-tabs v-model:activeKey="activeTab">
          <a-tab-pane key="preview" tab="文档预览">
            <div class="preview-content" v-if="previewContent">
              <pre>{{ previewContent }}</pre>
            </div>
            <div v-else class="preview-empty">选择一个文档进行预览</div>
          </a-tab-pane>
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
                <p>暂无思维导图，点击下方按钮AI自动生成</p>
                <button class="btn-primary-sm" :disabled="mindmapLoading" @click="handleGenerateMindmap">
                  {{ mindmapLoading ? '生成中...' : '生成思维导图' }}
                </button>
              </div>
            </div>
          </a-tab-pane>
        </a-tabs>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getKnowledge, getDocuments, uploadDocument, deleteDocument, previewDocument, askKnowledge, generateMindmap, getMindmap } from '../api/knowledge'
import { Transformer } from 'markmap-lib'
import { Markmap } from 'markmap-view'

const route = useRoute()
const router = useRouter()
const knowledgeId = route.params.id

const knowledge = ref({})
const documents = ref([])
const activeTab = ref('preview')
const previewContent = ref('')
const ragQuestion = ref('')
const ragMessages = ref([])
const ragLoading = ref(false)
const ragRef = ref(null)
const mindmapData = ref(null)
const mindmapLoading = ref(false)
const mindmapSvgRef = ref(null)

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

async function previewDoc(doc) {
  try {
    const res = await previewDocument(doc.id)
    previewContent.value = res.data
    activeTab.value = 'preview'
  } catch (e) {
    message.error('预览失败')
  }
}

async function deleteDoc(docId) {
  try {
    await deleteDocument(docId)
    message.success('删除成功')
    loadDocuments()
  } catch (e) {
    message.error('删除失败')
  }
}

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

async function loadMindmap() {
  try {
    const res = await getMindmap(knowledgeId)
    if (res.data) {
      mindmapData.value = res.data
      await nextTick()
      renderMindmap()
    }
  } catch (e) {
    // 未生成过思维导图，忽略
  }
}

async function handleGenerateMindmap() {
  mindmapLoading.value = true
  try {
    const res = await generateMindmap(knowledgeId)
    mindmapData.value = res.data
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
    const tree = typeof mindmapData.value === 'string' ? JSON.parse(mindmapData.value) : mindmapData.value
    const md = jsonToMarkdown(tree, 0)
    const transformer = new Transformer()
    const { root } = transformer.transform(md)
    mindmapSvgRef.value.innerHTML = ''
    Markmap.create(mindmapSvgRef.value, null, root)
  } catch (e) {
    console.error('[Mindmap] 渲染失败:', e)
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

.preview-content pre {
  background: #f5f5f5;
  padding: 16px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  max-height: 500px;
  white-space: pre-wrap;
}
.preview-empty {
  text-align: center;
  padding: 60px;
  color: #a1a1aa;
}

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
</style>
