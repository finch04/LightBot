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
      <div class="header-actions">
        <button class="btn-outline-sm" @click="openMembersModal">
          <TeamOutlined /> 成员
        </button>
        <button class="btn-primary-sm" @click="openEditDialog">
          <EditOutlined /> 编辑
        </button>
      </div>
    </div>

    <div class="content-grid">
      <!-- 文档列表 -->
      <div class="panel">
        <div class="panel-header">
          <h3>文档列表</h3>
          <button class="btn-primary-sm" @click="openUploadModal">上传文档</button>
        </div>
        <div class="doc-list">
          <div v-for="doc in documents" :key="doc.id" class="doc-item" @click="openDocModal(doc)">
            <a-tooltip :title="statusText(doc.status?.code || doc.status)">
              <span class="doc-status-icon" :class="doc.status?.code || doc.status">
                <CheckCircleOutlined v-if="(doc.status?.code || doc.status) === 'completed'" />
                <SyncOutlined v-else-if="(doc.status?.code || doc.status) === 'pending' || (doc.status?.code || doc.status) === 'processing'" spin />
                <CloseCircleOutlined v-else-if="(doc.status?.code || doc.status) === 'failed'" />
                <ExclamationCircleOutlined v-else />
              </span>
            </a-tooltip>
            <span class="doc-name">{{ doc.name }}</span>
            <div class="doc-meta">
              <span v-if="doc.chunkCount" class="doc-chunk-count">{{ doc.chunkCount }} 分块</span>
              <button
                v-if="(doc.status?.code || doc.status) === 'uploaded' || (doc.status?.code || doc.status) === 'failed'"
                class="btn-link"
                @click.stop="openIngestModal(doc)"
              >入库</button>
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
                  <div v-if="msg.role === 'user'" class="rag-content">{{ msg.content }}</div>
                  <div v-else class="rag-content markdown-body" v-html="renderMarkdown(msg.content)"></div>
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
            <div v-if="activeTab === 'mindmap'" class="rag-section">
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
                <p v-else-if="mindmapLoaded">暂无思维导图，点击下方按钮AI自动生成</p>
                <p v-else>加载中...</p>
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
      :width="900"
      :footer="null"
      centered
      :bodyStyle="{ padding: '0' }"
    >
      <template #extra>
        <span v-if="previewContent" class="doc-char-count">{{ previewContent.length }} 字符</span>
        <button class="btn-outline-sm" @click="handleDownload">
          <DownloadOutlined /> 下载
        </button>
      </template>
      <a-tabs v-model:activeKey="docModalTab" class="doc-modal-tabs">
        <a-tab-pane v-if="hasSourcePreview" key="source" tab="源文件预览">
          <div class="tab-pane-body">
            <FilePreview
              :fileUrl="downloadUrl"
              :fileName="currentDoc?.name"
              :fileType="currentDoc?.fileType"
              :content="previewContent"
              :loading="!previewLoaded"
            />
          </div>
        </a-tab-pane>
        <a-tab-pane key="text" tab="文本预览">
          <div class="tab-pane-body">
            <div v-if="currentDoc?.errorMessage" class="error-message">
              <ExclamationCircleOutlined /> {{ currentDoc.errorMessage }}
            </div>
            <div v-if="previewLoaded && !previewContent" class="modal-empty">
              <p>文档解析失败，无法预览文本内容</p>
              <button class="btn-primary-sm" @click="handleDownload">
                <DownloadOutlined /> 下载文件查看
              </button>
            </div>
            <div v-else-if="previewContent" class="text-content-preview">
              <div v-if="isMarkdownFile" class="markdown-content" v-html="renderedMarkdown"></div>
              <pre v-else class="plain-text">{{ previewContent }}</pre>
            </div>
            <div v-else class="modal-empty">加载中...</div>
          </div>
        </a-tab-pane>
        <a-tab-pane v-if="chunks.length > 0" key="chunks" tab="分块列表">
          <div class="tab-pane-body chunk-list-pane">
            <div class="chunk-list">
              <div v-for="(chunk, i) in chunks" :key="chunk.id" class="chunk-item" @click="openChunkDetail(chunk)">
                <div class="chunk-header">
                  <span class="chunk-index">#{{ chunk.chunkIndex ?? i + 1 }}</span>
                  <div class="chunk-header-right">
                    <span class="chunk-meta">{{ chunk.tokenCount || 0 }} tokens</span>
                    <a-tag v-if="chunk.status" :color="chunkStatusColor(chunk.status)" size="small" style="flex-shrink:0">
                      {{ chunkStatusText(chunk.status) }}
                    </a-tag>
                  </div>
                </div>
                <div class="chunk-preview">{{ chunk.content?.length > 100 ? chunk.content.substring(0, 100) + '...' : chunk.content }}</div>
              </div>
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-modal>

    <!-- 分块详情弹窗 -->
    <a-modal
      v-model:open="chunkDetailVisible"
      :title="`分块 #${currentChunk?.chunkIndex ?? ''}`"
      :width="720"
      :footer="null"
    >
      <div class="chunk-detail-meta">
        <span>{{ currentChunk?.tokenCount || 0 }} tokens</span>
        <a-tag v-if="currentChunk?.status" :color="chunkStatusColor(currentChunk.status)" style="margin-left: 8px">
          {{ chunkStatusText(currentChunk.status) }}
        </a-tag>
      </div>
      <pre class="chunk-detail-content">{{ currentChunk?.content }}</pre>
    </a-modal>

    <!-- 上传文档弹窗 -->
    <a-modal
      v-model:open="uploadVisible"
      title="上传文档"
      :width="520"
      :footer="null"
    >
      <div class="upload-section">
        <div class="upload-dropzone" @click="triggerFileInput" @drop.prevent="onDrop" @dragover.prevent>
          <input ref="fileInputRef" type="file" multiple accept=".md,.txt,.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.csv,.html,.htm" style="display: none" @change="onFileSelect" />
          <p class="dropzone-text">拖拽文件到此处，或点击选择</p>
          <p class="dropzone-hint">支持 md/txt/pdf/doc/docx/ppt/pptx/xls/xlsx/csv/html</p>
        </div>

        <!-- OCR 开关 -->
        <div class="ocr-section">
          <div class="ocr-toggle">
            <a-switch v-model:checked="ocrEnabled" size="small" @change="handleOcrToggle" />
            <span class="ocr-label">启用 OCR 识别</span>
          </div>
          <div v-if="ocrEnabled" class="ocr-status">
            <div v-if="ocrChecking" class="ocr-status-text checking">
              <LoadingOutlined spin /> 检测中...
            </div>
            <div v-else-if="ocrHealth?.healthy" class="ocr-status-text success">
              <CheckCircleOutlined /> OCR服务正常
              <div class="ocr-model-info">{{ ocrHealth.modelPath }}</div>
            </div>
            <div v-else-if="ocrHealth" class="ocr-status-text error">
              <CloseCircleOutlined /> {{ ocrHealth.message }}
            </div>
          </div>
        </div>

        <div class="upload-file-list" v-if="uploadFiles.length > 0">
          <div v-for="(file, i) in uploadFiles" :key="i" class="upload-file-item">
            <span class="upload-file-name">{{ file.name }}</span>
            <span class="upload-file-status" :class="file._status">
              {{ file._status === 'uploading' ? '上传中...' : file._status === 'success' ? '上传成功' : file._status === 'error' ? '上传失败' : '' }}
            </span>
            <button class="btn-icon-sm danger" @click="removeUploadFile(i)">
              <CloseOutlined />
            </button>
          </div>
        </div>

        <div class="upload-actions">
          <button class="btn-outline-sm" @click="uploadVisible = false">取消</button>
          <button class="btn-primary-sm" :disabled="uploadFiles.length === 0 || uploadSubmitting" @click="handleBatchUpload">
            {{ uploadSubmitting ? '上传中...' : '开始上传' }}
          </button>
        </div>
      </div>
    </a-modal>

    <!-- 入库弹窗 -->
    <a-modal
      v-model:open="ingestVisible"
      :title="`文档入库 - ${ingestDoc?.name || ''}`"
      :width="560"
      :footer="null"
      :maskClosable="false"
    >
      <div class="ingest-section">
        <a-form :model="ingestForm" :label-col="{ span: 6 }">
          <a-form-item label="分块策略" required>
            <a-select v-model:value="ingestForm.chunkStrategy" style="width: 100%">
              <a-select-option value="general">通用分块 - 按分隔符和长度切分</a-select-option>
              <a-select-option value="book">书籍分块 - 按章节标题切分</a-select-option>
              <a-select-option value="separator">严格分隔 - 遇分隔符即切分</a-select-option>
              <a-select-option value="qa">问答对分块 - 适合FAQ/客服对话</a-select-option>
              <a-select-option value="laws">法规分块 - 按条款结构切分</a-select-option>
            </a-select>
            <div v-if="ingestForm.chunkStrategy === 'general' && knowledgeDefaultStrategy" class="default-strategy-hint">
              <a-tag color="blue">使用知识库配置: {{ strategyLabelMap[knowledgeDefaultStrategy] || knowledgeDefaultStrategy }}</a-tag>
            </div>
          </a-form-item>
          <a-form-item label="分块大小" required>
            <a-input-number v-model:value="ingestForm.chunkSize" :min="100" :max="2000" :step="100" style="width: 100%" />
          </a-form-item>
          <a-form-item label="重叠百分比" required>
            <a-input-number v-model:value="ingestForm.chunkOverlap" :min="0" :max="99" :step="5" style="width: 100%" />
          </a-form-item>
          <a-form-item label="分块分隔符">
            <a-input v-model:value="ingestForm.chunkDelimiter" placeholder="默认按换行符分隔" allow-clear />
          </a-form-item>
        </a-form>

        <!-- 预览分块 -->
        <div v-if="previewChunksList.length > 0" class="preview-chunks">
          <div class="preview-header">分块预览（共 {{ previewChunksList.length }} 块）</div>
          <div class="preview-list">
            <div v-for="(chunk, i) in previewChunksList" :key="i" class="preview-item">
              <div class="preview-item-header">#{{ i + 1 }} (约 {{ Math.round(chunk.length * 1.2) }} tokens)</div>
              <div class="preview-item-content">{{ chunk.length > 200 ? chunk.substring(0, 200) + '...' : chunk }}</div>
            </div>
          </div>
        </div>

        <div class="ingest-actions">
          <button class="btn-outline-sm" :disabled="ingestPreviewing" @click="handlePreviewChunks">
            {{ ingestPreviewing ? '预览中...' : '预览分块' }}
          </button>
          <div style="flex:1"></div>
          <button class="btn-outline-sm" @click="ingestVisible = false">取消</button>
          <button class="btn-primary-sm" :disabled="ingestSubmitting" @click="handleIngest">
            {{ ingestSubmitting ? '入库中...' : '确认入库' }}
          </button>
        </div>
      </div>
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
        <a-form-item label="Embed模型" required>
          <a-select v-model:value="editForm.embeddingModel" placeholder="选择嵌入模型" allow-clear style="width: 100%">
            <a-select-option v-for="m in embeddingModels" :key="m.id" :value="m.modelId">
              {{ m.name }} ({{ m.modelId }})
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="RAG Top K">
          <a-input-number v-model:value="editForm.ragTopK" :min="1" :max="20" style="width: 100%" />
        </a-form-item>
        <a-form-item label="RAG 相似度阈值">
          <a-input-number v-model:value="editForm.ragThreshold" :min="0" :max="1" :step="0.05" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 成员管理弹窗 -->
    <a-modal v-model:open="membersVisible" title="成员管理" :width="560" :footer="null">
      <div class="members-section">
        <div class="member-list">
          <div v-for="member in membersWithInfo" :key="member.userId" class="member-item">
            <div class="member-info">
              <div class="member-avatar">{{ (member.nickname || member.username || 'U')[0] }}</div>
              <div class="member-detail">
                <span class="member-name">{{ member.nickname || member.username || '用户' }}</span>
              </div>
              <a-tag :color="roleColor(member.role)">{{ roleText(member.role) }}</a-tag>
            </div>
            <div class="member-actions" v-if="isManagerOrCreator">
              <a-select
                v-if="member.role !== 'creator'"
                :value="member.role"
                size="small"
                style="width: 100px"
                @change="(val) => handleChangeRole(member.userId, val)"
              >
                <a-select-option value="manager">管理者</a-select-option>
                <a-select-option value="developer">开发者</a-select-option>
                <a-select-option value="viewer">查看者</a-select-option>
              </a-select>
              <button
                v-if="member.role !== 'creator'"
                class="btn-icon-sm danger"
                @click="handleRemoveMember(member.userId)"
              >
                <CloseOutlined />
              </button>
            </div>
          </div>
          <div v-if="membersWithInfo.length === 0" class="empty-tip">暂无成员</div>
        </div>
        <div v-if="isManagerOrCreator" class="add-member-form">
          <button class="btn-primary-sm" @click="openInviteModal">
            <PlusOutlined /> 添加成员
          </button>
        </div>
      </div>
    </a-modal>

    <!-- 邀请成员弹窗 -->
    <a-modal v-model:open="inviteVisible" title="邀请成员" :width="480" :footer="null" :maskClosable="false">
      <div class="invite-section">
        <a-input
          v-model:value="inviteKeyword"
          placeholder="搜索用户名或昵称..."
          allow-clear
          @input="onInviteSearch"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <div class="invite-results">
          <div v-for="u in inviteResults" :key="u.id" class="invite-item">
            <div class="invite-user">
              <div class="member-avatar">{{ (u.nickname || u.username || 'U')[0] }}</div>
              <div class="invite-info">
                <span class="invite-name">{{ u.nickname || u.username }}</span>
                <span class="invite-username">@{{ u.username }}</span>
              </div>
            </div>
            <a-select
              v-model:value="inviteRole"
              size="small"
              style="width: 90px"
            >
              <a-select-option value="manager">管理者</a-select-option>
              <a-select-option value="developer">开发者</a-select-option>
              <a-select-option value="viewer">查看者</a-select-option>
            </a-select>
            <button class="btn-primary-sm" @click="handleInvite(u.id)">邀请</button>
          </div>
          <div v-if="inviteKeyword && inviteResults.length === 0" class="empty-tip">未找到用户</div>
          <div v-if="!inviteKeyword" class="empty-tip">输入关键词搜索用户</div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, watch } from 'vue'
import { marked } from 'marked'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeftOutlined, EditOutlined, TeamOutlined, PlusOutlined, CloseOutlined, SearchOutlined,
  CheckCircleOutlined, ClockCircleOutlined, SyncOutlined, CloseCircleOutlined, ExclamationCircleOutlined,
  DownloadOutlined, LoadingOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getKnowledge, updateKnowledge, getDocuments, uploadDocument, uploadDocuments, deleteDocument,
  previewDocument, getDocumentDownloadUrl, getChunks, askKnowledge, askKnowledgeStream,
  generateMindmap, getMindmap, getKnowledgeMembers, addKnowledgeMember, updateKnowledgeMemberRole,
  removeKnowledgeMember, ingestDocument, previewChunks, getDefaultIngestConfig, checkOcrHealth,
} from '../api/knowledge'
import { searchUsers } from '../api/auth'
import { getModelsByType } from '../api/model'
import { useUserStore } from '../stores/user'
import { Transformer } from 'markmap-lib'
import { Markmap } from 'markmap-view'
import FilePreview from '../components/FilePreview.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const knowledgeId = route.params.id

const knowledge = ref({})
const documents = ref([])

const strategyLabelMap = {
  general: '通用分块', book: '书籍分块', separator: '严格分隔', qa: '问答对分块', laws: '法规分块',
}
const knowledgeDefaultStrategy = computed(() => {
  try {
    const cfg = typeof knowledge.value.config === 'string' ? JSON.parse(knowledge.value.config) : (knowledge.value.config || {})
    return cfg.defaultChunkStrategy || ''
  } catch { return '' }
})
const activeTab = ref('ask')
const ragQuestion = ref('')
const ragMessages = ref([])
const ragLoading = ref(false)
const ragRef = ref(null)
const mindmapData = ref(null)
const mindmapLoading = ref(false)
const mindmapSvgRef = ref(null)
const mindmapLoaded = ref(false)

// 文档弹窗
const docModalVisible = ref(false)
const docModalTab = ref('source')
const currentDoc = ref(null)
const previewContent = ref('')
const previewLoaded = ref(false)
const downloadUrl = ref('')
const chunks = ref([])

// 分块详情弹窗
const chunkDetailVisible = ref(false)
const currentChunk = ref(null)

// 上传弹窗
const uploadVisible = ref(false)
const uploadSubmitting = ref(false)
const uploadFiles = ref([])
const fileInputRef = ref(null)
const ocrEnabled = ref(false)
const ocrChecking = ref(false)
const ocrHealth = ref(null)

// 入库弹窗
const ingestVisible = ref(false)
const ingestDoc = ref(null)
const ingestSubmitting = ref(false)
const ingestPreviewing = ref(false)
const previewChunksList = ref([])
const ingestForm = reactive({
  chunkStrategy: 'general',
  chunkSize: 512,
  chunkOverlap: 10,
  chunkDelimiter: '',
})

// 编辑弹窗
const editVisible = ref(false)
const editSubmitting = ref(false)
const embeddingModels = ref([])
const editForm = reactive({
  name: '',
  description: '',
  embeddingModel: '',
  ragTopK: 5,
  ragThreshold: 0.7,
})

// 成员管理
const members = ref([])
const membersWithInfo = ref([])
const currentMemberRole = ref(null)
const membersVisible = ref(false)
const inviteVisible = ref(false)
const inviteKeyword = ref('')
const inviteResults = ref([])
const inviteRole = ref('viewer')
let inviteSearchTimer = null

const isManagerOrCreator = computed(() => {
  return currentMemberRole.value === 'creator' || currentMemberRole.value === 'manager'
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
    message.success('上传成功')
    setTimeout(loadDocuments, 500)
  } catch (e) {
    // interceptor已处理错误提示
  }
  return false
}

// ========== 上传弹窗 ==========

function openUploadModal() {
  uploadFiles.value = []
  ocrEnabled.value = false
  ocrHealth.value = null
  uploadVisible.value = true
}

function triggerFileInput() {
  fileInputRef.value?.click()
}

function onFileSelect(e) {
  const files = Array.from(e.target.files || [])
  addUploadFiles(files)
  e.target.value = ''
}

function onDrop(e) {
  const files = Array.from(e.dataTransfer?.files || [])
  addUploadFiles(files)
}

function addUploadFiles(files) {
  for (const file of files) {
    const exists = uploadFiles.value.some(f => f.name === file.name && f.size === file.size)
    if (!exists) {
      uploadFiles.value.push(Object.assign(file, { _status: 'pending' }))
    }
  }
}

function removeUploadFile(index) {
  uploadFiles.value.splice(index, 1)
}

async function handleOcrToggle(checked) {
  if (checked) {
    ocrChecking.value = true
    ocrHealth.value = null
    try {
      const res = await checkOcrHealth()
      ocrHealth.value = res.data
    } catch (e) {
      ocrHealth.value = { healthy: false, message: 'OCR服务检测失败' }
    } finally {
      ocrChecking.value = false
    }
  } else {
    ocrHealth.value = null
  }
}

async function handleBatchUpload() {
  if (uploadFiles.value.length === 0 || uploadSubmitting.value) return
  uploadSubmitting.value = true

  try {
    const files = uploadFiles.value.map(f => f)
    await uploadDocuments(knowledgeId, files, ocrEnabled.value)
    message.success(`批量上传成功，共 ${files.length} 个文件`)
    uploadVisible.value = false
    uploadFiles.value = []
    setTimeout(loadDocuments, 500)
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    uploadSubmitting.value = false
  }
}

// ========== 入库弹窗 ==========

async function openIngestModal(doc) {
  ingestDoc.value = doc
  previewChunksList.value = []

  // 加载知识库默认配置
  try {
    const res = await getDefaultIngestConfig(route.params.id)
    const defaults = res.data || {}
    Object.assign(ingestForm, {
      chunkStrategy: defaults.chunkStrategy || 'general',
      chunkSize: defaults.chunkSize || 512,
      chunkOverlap: defaults.chunkOverlap || 10,
      chunkDelimiter: defaults.chunkDelimiter || '',
    })
  } catch {
    Object.assign(ingestForm, {
      chunkStrategy: 'general',
      chunkSize: 512,
      chunkOverlap: 10,
      chunkDelimiter: '',
    })
  }

  ingestVisible.value = true
}

async function handlePreviewChunks() {
  if (!ingestDoc.value) return
  ingestPreviewing.value = true
  try {
    const data = {
      chunkStrategy: ingestForm.chunkStrategy,
      chunkSize: ingestForm.chunkSize,
      chunkOverlap: ingestForm.chunkOverlap,
      chunkDelimiter: ingestForm.chunkDelimiter || null,
    }
    const res = await previewChunks(ingestDoc.value.id, data)
    previewChunksList.value = res.data || []
    if (previewChunksList.value.length === 0) {
      message.info('未产生分块，请检查文档内容')
    }
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    ingestPreviewing.value = false
  }
}

async function handleIngest() {
  if (!ingestDoc.value) return
  ingestSubmitting.value = true
  try {
    const data = {
      chunkStrategy: ingestForm.chunkStrategy,
      chunkSize: ingestForm.chunkSize,
      chunkOverlap: ingestForm.chunkOverlap,
      chunkDelimiter: ingestForm.chunkDelimiter || null,
    }
    await ingestDocument(ingestDoc.value.id, data)
    message.success('入库任务已提交，正在处理...')
    ingestVisible.value = false
    setTimeout(loadDocuments, 500)
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    ingestSubmitting.value = false
  }
}

// ========== 文档弹窗 ==========

async function openDocModal(doc) {
  currentDoc.value = doc
  // Office文档默认展示文本预览，其他默认展示源文件预览
  const officeTypes = ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx']
  docModalTab.value = officeTypes.includes(doc.fileType) ? 'text' : 'source'
  previewContent.value = ''
  previewLoaded.value = false
  downloadUrl.value = ''
  chunks.value = []
  docModalVisible.value = true

  // 并行加载预览、下载链接和分块
  const [previewRes, downloadRes, chunksRes] = await Promise.allSettled([
    previewDocument(doc.id),
    getDocumentDownloadUrl(doc.id),
    getChunks(doc.id),
  ])
  if (previewRes.status === 'fulfilled') {
    previewContent.value = previewRes.value.data || ''
  }
  previewLoaded.value = true
  if (downloadRes.status === 'fulfilled') {
    downloadUrl.value = downloadRes.value.data?.url || ''
  }
  if (chunksRes.status === 'fulfilled') {
    chunks.value = (chunksRes.value.data || []).sort((a, b) => (a.chunkIndex ?? 0) - (b.chunkIndex ?? 0))
  }
}

function handleDownload() {
  if (downloadUrl.value) {
    window.open(downloadUrl.value, '_blank')
  } else {
    message.warning('下载链接获取中，请稍后重试')
  }
}

function openChunkDetail(chunk) {
  currentChunk.value = chunk
  chunkDetailVisible.value = true
}

// ========== 编辑知识库 ==========

async function openEditDialog() {
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
    ragTopK: config.ragTopK ?? 5,
    ragThreshold: config.ragThreshold ?? 0.7,
  })
  editVisible.value = true

  // 加载嵌入模型列表
  try {
    const res = await getModelsByType('embedding')
    embeddingModels.value = res.data || []
  } catch { /* ignore */ }
}

async function handleEdit() {
  if (!editForm.name.trim()) return message.warning('请输入名称')
  if (!editForm.embeddingModel) return message.warning('请选择 Embed 模型')
  editSubmitting.value = true
  try {
    const config = JSON.stringify({ ragTopK: editForm.ragTopK, ragThreshold: editForm.ragThreshold })
    await updateKnowledge({
      id: knowledgeId,
      name: editForm.name,
      description: editForm.description,
      embeddingModel: editForm.embeddingModel,
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
        // interceptor已处理错误提示
      }
    },
  })
}

// ========== RAG 问答 ==========

function renderMarkdown(text) {
  if (!text) return ''
  return marked(text)
}

async function askRag() {
  const q = ragQuestion.value.trim()
  if (!q || ragLoading.value) return
  ragMessages.value.push({ role: 'user', content: q })
  ragQuestion.value = ''
  ragLoading.value = true

  // 添加空的assistant消息，用于流式填充
  const assistantIndex = ragMessages.value.length
  ragMessages.value.push({ role: 'assistant', content: '' })

  try {
    await askKnowledgeStream(knowledgeId, q,
      (chunk) => {
        // 流式追加内容
        ragMessages.value[assistantIndex].content += chunk
        // 自动滚动到底部
        nextTick(() => {
          if (ragRef.value) {
            ragRef.value.scrollTop = ragRef.value.scrollHeight
          }
        })
      },
      () => {
        // 流式完成
        ragLoading.value = false
      }
    )
  } catch (e) {
    ragMessages.value[assistantIndex].content = '查询失败：' + (e.message || '未知错误')
    ragLoading.value = false
  }
}

// 文件类型判断（用于文本预览tab）
// Excel/CSV/Word 转为 Markdown 表格，MD 文件本身是 Markdown
const isMarkdownFile = computed(() => ['md', 'xlsx', 'xls', 'csv', 'doc', 'docx'].includes(currentDoc.value?.fileType))
// Office文档（doc/docx/xls/xlsx/ppt/pptx）不支持源文件预览，只展示文本化后的内容
const hasSourcePreview = computed(() => {
  const ft = currentDoc.value?.fileType
  if (!ft) return false
  return !['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(ft)
})

// Markdown渲染
const renderedMarkdown = computed(() => {
  if (!previewContent.value) return ''
  return marked(previewContent.value)
})

function statusText(s) {
  const map = { uploaded: '待入库', pending: '分块中', processing: '向量化中', completed: '已完成', failed: '失败' }
  return map[s] || s
}

function chunkStatusText(s) {
  const code = s?.code || s
  const map = { chunked: '已分块', vectorizing: '向量化中', vectorized: '已向量化', failed: '失败' }
  return map[code] || code
}

function chunkStatusColor(s) {
  const code = s?.code || s
  const map = { chunked: 'default', vectorizing: 'processing', vectorized: 'success', failed: 'error' }
  return map[code] || 'default'
}

// ========== 思维导图 ==========

async function loadMindmap() {
  if (mindmapLoaded.value) return
  mindmapLoaded.value = true
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

// 切换到思维导图tab时才加载/重新渲染
watch(activeTab, (tab) => {
  if (tab === 'mindmap') {
    nextTick(() => {
      if (mindmapData.value) {
        renderMindmap()
      } else {
        loadMindmap()
      }
    })
  }
})

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
    // interceptor已处理错误提示
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

// ========== 成员管理 ==========

async function loadMembers() {
  try {
    const res = await getKnowledgeMembers(knowledgeId)
    members.value = res.data || []
    // 后端已连表查询返回用户昵称、头像，直接使用
    membersWithInfo.value = members.value
    // 判断当前用户角色
    const userId = userStore.user?.id
    const myMember = members.value.find(m => String(m.userId) === String(userId))
    currentMemberRole.value = myMember?.role || null
  } catch (e) {
    // interceptor已处理错误提示
  }
}

function openMembersModal() {
  membersVisible.value = true
  loadMembers()
}

function openInviteModal() {
  inviteKeyword.value = ''
  inviteResults.value = []
  inviteRole.value = 'viewer'
  inviteVisible.value = true
}

function onInviteSearch() {
  clearTimeout(inviteSearchTimer)
  const kw = inviteKeyword.value.trim()
  if (!kw) {
    inviteResults.value = []
    return
  }
  inviteSearchTimer = setTimeout(async () => {
    try {
      const res = await searchUsers(kw)
      // 过滤掉已经是成员的用户
      const memberIds = new Set(members.value.map(m => String(m.userId)))
      inviteResults.value = (res.data || []).filter(u => !memberIds.has(String(u.id)))
    } catch { /* ignore */ }
  }, 300)
}

async function handleInvite(userId) {
  try {
    await addKnowledgeMember(knowledgeId, String(userId), inviteRole.value)
    message.success('邀请成功')
    inviteVisible.value = false
    loadMembers()
  } catch (e) {
    // interceptor已处理错误提示
  }
}

async function handleChangeRole(userId, role) {
  try {
    await updateKnowledgeMemberRole(knowledgeId, userId, role)
    message.success('角色更新成功')
    loadMembers()
  } catch (e) {
    // interceptor已处理错误提示
  }
}

async function handleRemoveMember(userId) {
  Modal.confirm({
    title: '确认移除',
    content: '确定要移除该成员吗？',
    okText: '确认',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await removeKnowledgeMember(knowledgeId, userId)
        message.success('成员已移除')
        loadMembers()
      } catch (e) {
        // interceptor已处理错误提示
      }
    },
  })
}

function roleText(role) {
  const map = { creator: '创建者', manager: '管理者', developer: '开发者', viewer: '查看者' }
  return map[role] || role
}

function roleColor(role) {
  const map = { creator: 'red', manager: 'orange', developer: 'blue', viewer: 'green' }
  return map[role] || 'default'
}

onMounted(() => {
  loadKnowledge()
  loadDocuments()
  loadMembers()
})
</script>

<style scoped>
.page {
  padding: 20px 24px;
  height: 100vh;
  overflow-y: auto;
  background: #fafafa;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
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
  border-radius: 8px;
  padding: 16px;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.panel-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #171717;
}
.header-actions {
  display: flex;
  gap: 8px;
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

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.doc-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #f5f5f5;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.doc-item:hover {
  border-color: #0070f3;
}
.doc-status-icon {
  font-size: 16px;
  flex-shrink: 0;
  width: 20px;
  text-align: center;
}
.doc-status-icon.uploaded { color: #a1a1aa; }
.doc-status-icon.pending { color: #2563eb; }
.doc-status-icon.processing { color: #d97706; }
.doc-status-icon.completed { color: #16a34a; }
.doc-status-icon.failed { color: #dc2626; }
.doc-name {
  flex: 1;
  font-size: 14px;
  color: #171717;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
.doc-chunk-count {
  font-size: 12px;
  color: #a1a1aa;
  flex-shrink: 0;
}
.doc-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #a1a1aa;
  flex-shrink: 0;
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

/* RAG & 思维导图共用 */
.rag-section {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 220px);
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
  padding: 12px 16px;
  border-radius: 8px;
  max-width: 85%;
  font-size: 14px;
  line-height: 1.6;
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

/* RAG消息中的markdown样式 */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin-top: 12px;
  margin-bottom: 8px;
  font-weight: 600;
}
.markdown-body :deep(p) {
  margin-bottom: 8px;
}
.markdown-body :deep(code) {
  background: rgba(0,0,0,0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}
.markdown-body :deep(pre) {
  background: rgba(0,0,0,0.04);
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
  margin-bottom: 8px;
}
.markdown-body :deep(li) {
  margin-bottom: 4px;
}
.markdown-body :deep(strong) {
  font-weight: 600;
}

/* 思维导图 */
.mindmap-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mindmap-svg {
  flex: 1;
  width: 100%;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}
.mindmap-actions {
  display: flex;
  justify-content: flex-end;
}
.mindmap-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #a1a1aa;
}
.mindmap-empty p {
  margin-bottom: 16px;
}

/* 文档弹窗 */
.doc-char-count {
  font-size: 12px;
  color: #a1a1aa;
  margin-right: 8px;
}
.doc-modal-tabs {
  margin-top: -12px;
}
.doc-modal-tabs :deep(.ant-tabs-nav) {
  margin: 0;
  padding: 0 24px;
  background: #fafafa;
  border-bottom: 1px solid #ebebeb;
}
.doc-modal-tabs :deep(.ant-tabs-content-holder) {
  padding: 0;
}
.tab-pane-body {
  height: 520px;
  overflow: auto;
}
.chunk-list-pane {
  height: 520px;
  overflow: hidden;
}
.text-content-preview {
  padding: 20px 24px;
}
.plain-text {
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  color: #3f3f46;
}
.markdown-content {
  font-size: 14px;
  line-height: 1.8;
  color: #27272a;
}
.markdown-content h1 {
  font-size: 20px;
  font-weight: 700;
  margin: 24px 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e4e7;
}
.markdown-content h2 {
  font-size: 17px;
  font-weight: 600;
  margin: 20px 0 10px;
}
.markdown-content h3,
.markdown-content h4 {
  font-size: 15px;
  font-weight: 600;
  margin: 16px 0 8px;
}
.markdown-content p {
  margin: 0 0 12px;
}
.markdown-content code {
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: #e11d48;
}
.markdown-content pre {
  background: #f8f9fa;
  padding: 14px 16px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 12px 0;
  border: 1px solid #e4e4e7;
}
.markdown-content pre code {
  background: none;
  padding: 0;
  color: inherit;
}
.markdown-content ul,
.markdown-content ol {
  padding-left: 24px;
  margin: 0 0 12px;
}
.markdown-content li {
  margin-bottom: 4px;
}
.markdown-content blockquote {
  border-left: 3px solid #a1a1aa;
  padding: 4px 0 4px 16px;
  margin: 12px 0;
  color: #71717a;
  background: #fafafa;
}
.markdown-content table {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 13px;
}
.markdown-content th,
.markdown-content td {
  border: 1px solid #e4e4e7;
  padding: 8px 12px;
  text-align: left;
}
.markdown-content th {
  background: #f8f9fa;
  font-weight: 600;
  color: #27272a;
}
.markdown-content tr:hover td {
  background: #fafafa;
}
.markdown-content img {
  max-width: 100%;
  border-radius: 4px;
}
.markdown-content hr {
  border: none;
  border-top: 1px solid #e4e4e7;
  margin: 16px 0;
}
.error-message {
  background: #fef2f2;
  border-bottom: 1px solid #fecaca;
  padding: 10px 16px;
  color: #dc2626;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.modal-empty {
  text-align: center;
  padding: 40px;
  color: #a1a1aa;
}

/* 分块列表 */
.chunk-list {
  height: 500px;
  overflow-y: auto;
  overflow-x: hidden;
}
.chunk-list::-webkit-scrollbar {
  width: 6px;
}
.chunk-list::-webkit-scrollbar-thumb {
  background: #d4d4d8;
  border-radius: 3px;
}
.chunk-list::-webkit-scrollbar-thumb:hover {
  background: #a1a1aa;
}
.chunk-item {
  border: 1px solid #ebebeb;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  margin-bottom: 8px;
  overflow: hidden;
}
.chunk-item:hover {
  border-color: #0070f3;
}
.chunk-header {
  height: 30px;
  line-height: 30px;
  padding: 0 12px;
  background: #f9fafb;
  border-bottom: 1px solid #ebebeb;
  display: flex;
  justify-content: space-between;
  align-items: center;
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
.chunk-preview {
  height: 50px;
  line-height: 20px;
  padding: 5px 12px;
  font-size: 13px;
  color: #52525b;
  overflow: hidden;
}

/* 分块详情弹窗 */
.chunk-detail-meta {
  font-size: 12px;
  color: #a1a1aa;
  margin-bottom: 12px;
}
.chunk-detail-content {
  background: #f5f5f5;
  padding: 16px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  max-height: 500px;
  overflow-y: auto;
}

/* 成员管理 */
.members-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.member-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.member-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: 1px solid #f5f5f5;
  border-radius: 8px;
}
.member-info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.member-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #0070f3;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}
.member-detail {
  display: flex;
  flex-direction: column;
}
.member-name {
  font-size: 14px;
  color: #171717;
  font-weight: 500;
}
.member-id {
  font-size: 12px;
  color: #a1a1aa;
}
.member-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.btn-icon-sm {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #71717a;
  font-size: 12px;
}
.btn-icon-sm:hover {
  background: #f5f5f5;
}
.btn-icon-sm.danger:hover {
  color: #ee0000;
  background: #f7d4d6;
}
.add-member-form {
  display: flex;
  gap: 8px;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #ebebeb;
}
.empty-tip {
  text-align: center;
  padding: 24px;
  color: #a1a1aa;
  font-size: 13px;
}

/* 上传弹窗 */
.upload-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.upload-form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.upload-label {
  font-size: 13px;
  font-weight: 500;
  color: #171717;
}
.upload-dropzone {
  border: 2px dashed #d4d4d8;
  border-radius: 8px;
  padding: 32px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s;
}
.upload-dropzone:hover {
  border-color: #0070f3;
}
.dropzone-text {
  font-size: 14px;
  color: #52525b;
  margin-bottom: 4px;
}
.dropzone-hint {
  font-size: 12px;
  color: #a1a1aa;
}
.upload-file-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 200px;
  overflow-y: auto;
}
.upload-file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid #f5f5f5;
  border-radius: 6px;
}
.upload-file-name {
  flex: 1;
  font-size: 13px;
  color: #171717;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.upload-file-status {
  font-size: 12px;
  color: #a1a1aa;
}
.upload-file-status.uploading {
  color: #d97706;
}
.upload-file-status.success {
  color: #16a34a;
}
.upload-file-status.error {
  color: #dc2626;
}

/* OCR 开关 */
.ocr-section {
  background: #fafafa;
  border-radius: 8px;
  padding: 12px;
}
.ocr-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ocr-label {
  font-size: 13px;
  color: #52525b;
}
.ocr-status {
  margin-top: 8px;
}
.ocr-status-text {
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.ocr-status-text.success {
  color: #16a34a;
}
.ocr-status-text.error {
  color: #dc2626;
}
.ocr-status-text.checking {
  color: #d97706;
}
.ocr-model-info {
  margin-top: 4px;
  padding-left: 18px;
  font-size: 12px;
  white-space: pre-line;
  line-height: 1.6;
}

.upload-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid #ebebeb;
}
.form-hint {
  font-size: 12px;
  color: #a1a1aa;
  margin-top: 2px;
}

/* 入库弹窗 */
.ingest-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.default-strategy-hint {
  margin-top: -8px;
}
.preview-chunks {
  border: 1px solid #ebebeb;
  border-radius: 8px;
  overflow: hidden;
}
.preview-header {
  background: #f9fafb;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 500;
  color: #52525b;
  border-bottom: 1px solid #ebebeb;
}
.preview-list {
  max-height: 300px;
  overflow-y: auto;
}
.preview-item {
  padding: 8px 12px;
  border-bottom: 1px solid #f5f5f5;
}
.preview-item:last-child {
  border-bottom: none;
}
.preview-item-header {
  font-size: 12px;
  font-weight: 600;
  color: #71717a;
  margin-bottom: 4px;
}
.preview-item-content {
  font-size: 13px;
  color: #52525b;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
.ingest-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid #ebebeb;
}
.chunk-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 邀请弹窗 */
.invite-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.invite-results {
  max-height: 360px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.invite-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid #f5f5f5;
  border-radius: 8px;
}
.invite-user {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.invite-info {
  display: flex;
  flex-direction: column;
}
.invite-name {
  font-size: 14px;
  color: #171717;
  font-weight: 500;
}
.invite-username {
  font-size: 12px;
  color: #a1a1aa;
}
</style>
