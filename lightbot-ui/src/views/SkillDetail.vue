<template>
  <div class="skill-detail-page">
    <!-- 顶部导航栏 -->
    <div class="detail-header">
      <button class="btn-back" @click="goBack">
        <ArrowLeftOutlined /> 返回
      </button>
      <div class="header-info">
        <h1 class="header-title">{{ skill.displayName || skill.name || 'Skill 详情' }}</h1>
        <div class="header-tags">
          <span v-if="skill.slug" class="tag tag-slug">{{ skill.slug }}</span>
          <a-tag :color="skill.status === 'disabled' ? 'default' : 'success'">
            {{ skill.status === 'disabled' ? '已禁用' : '已启用' }}
          </a-tag>
          <a-tag v-if="skill.isBuiltin === 1" color="blue">内置</a-tag>
          <span v-if="skill.version" class="tag tag-version">v{{ skill.version }}</span>
        </div>
      </div>
      <div class="header-actions">
        <button class="btn-outline" @click="handleExport">
          <ExportOutlined /> 导出
        </button>
        <button v-if="skill.isBuiltin !== 1" class="btn-outline danger" @click="handleDelete">
          <DeleteOutlined /> 删除
        </button>
      </div>
    </div>

    <!-- Tab 内容 -->
    <a-spin :spinning="loading" tip="加载中..." style="flex:1; min-height:0; display:flex; flex-direction:column;">
    <a-tabs v-model:activeKey="activeTab" class="detail-tabs">
      <!-- Tab 1: 基本信息 -->
      <a-tab-pane key="info" tab="基本信息">
        <div class="tab-body">
          <a-descriptions bordered :column="1" size="small">
            <a-descriptions-item label="显示名称">{{ skill.displayName || skill.name || '—' }}</a-descriptions-item>
            <a-descriptions-item label="技能名称">{{ skill.name || '—' }}</a-descriptions-item>
            <a-descriptions-item label="slug">{{ skill.slug || '—' }}</a-descriptions-item>
            <a-descriptions-item label="版本">{{ skill.version || '1.0.0' }}</a-descriptions-item>
            <a-descriptions-item label="来源">
              <a-tag v-if="skill.sourceType === 'builtin'" color="blue">内置</a-tag>
              <a-tag v-else-if="skill.sourceType === 'remote'" color="cyan">远程</a-tag>
              <a-tag v-else color="default">上传</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="描述">{{ skill.description || '—' }}</a-descriptions-item>
            <a-descriptions-item label="排序">{{ skill.sortOrder ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="依赖工具">
              {{ formatIdLabels(skill.toolIds, toolOptions) || '无' }}
            </a-descriptions-item>
            <a-descriptions-item label="依赖 MCP">
              {{ formatIdLabels(skill.mcpServerIds, mcpOptions) || '无' }}
            </a-descriptions-item>
          </a-descriptions>
          <div class="detail-section">
            <div class="detail-section-title">
              提示词模板
              <button v-if="skill.promptTemplate" class="btn-outline-sm" style="margin-left: 8px" @click="promptMdPreview = !promptMdPreview">
                <EyeOutlined v-if="promptMdPreview" /> <EditOutlined v-else />
                {{ promptMdPreview ? '查看原文' : 'Markdown 预览' }}
              </button>
            </div>
            <div v-if="promptMdPreview && skill.promptTemplate" class="md-rendered">
              <MarkdownPreview :content="skill.promptTemplate" :finalized="true" :image-preview="false" :strip-frontmatter="true" />
            </div>
            <pre v-else class="detail-pre">{{ skill.promptTemplate || '—' }}</pre>
          </div>
          <div v-if="skill.config && skill.config !== '{}'" class="detail-section">
            <div class="detail-section-title">扩展配置</div>
            <pre class="detail-pre">{{ skill.config }}</pre>
          </div>
        </div>
      </a-tab-pane>

      <!-- Tab 2: 文件管理 -->
      <a-tab-pane key="files" tab="文件管理">
        <div class="tab-body file-manager">
          <div class="file-toolbar">
            <button class="btn-outline-sm" @click="refreshFiles">
              <ReloadOutlined :spin="filesLoading" /> 刷新
            </button>
            <button v-if="skill.isBuiltin !== 1" class="btn-outline-sm" @click="showCreateFile = true">
              <FileAddOutlined /> 新建文件
            </button>
            <span v-if="skill.isBuiltin === 1" class="builtin-hint">内置 Skill 文件只读</span>
          </div>
          <div class="file-content">
            <!-- 文件树 -->
            <div class="file-tree-panel">
              <div v-if="filesLoading" class="tree-loading">
                <a-spin size="small" /> 加载中...
              </div>
              <div v-else-if="fileTree.length === 0" class="empty-tree">
                暂无文件
              </div>
              <div v-else class="tree-container">
                <div v-for="node in fileTree" :key="node.path">
                  <TreeNode
                    :node="node"
                    :depth="0"
                    :selected="selectedFile"
                    @select="handleFileSelect"
                  />
                </div>
              </div>
            </div>
            <!-- 文件预览 -->
            <div class="file-preview">
              <template v-if="selectedFile">
                <div class="preview-header">
                  <span class="preview-path">{{ selectedFile }}</span>
                  <div class="preview-actions">
                    <button
                      v-if="isMarkdown(selectedFile)"
                      class="btn-outline-sm"
                      @click="mdPreview = !mdPreview"
                    >
                      <EyeOutlined v-if="mdPreview" /> <EditOutlined v-else />
                      {{ mdPreview ? '查看原文' : 'Markdown 预览' }}
                    </button>
                    <button
                      v-if="isEditable(selectedFile) && !isMarkdown(selectedFile) && skill.isBuiltin !== 1"
                      class="btn-outline-sm"
                      :disabled="fileSaving"
                      @click="handleSaveFile"
                    >
                      <SaveOutlined /> {{ fileSaving ? '保存中...' : '保存' }}
                    </button>
                    <button
                      v-if="isMarkdown(selectedFile) && !mdPreview && skill.isBuiltin !== 1"
                      class="btn-outline-sm"
                      :disabled="fileSaving"
                      @click="handleSaveFile"
                    >
                      <SaveOutlined /> {{ fileSaving ? '保存中...' : '保存' }}
                    </button>
                    <button
                      v-if="skill.isBuiltin !== 1"
                      class="btn-outline-sm danger"
                      @click="handleDeleteFile"
                    >
                      <DeleteOutlined /> 删除
                    </button>
                  </div>
                </div>
                <div class="preview-body">
                  <a-spin :spinning="fileLoading">
                    <template v-if="isImage(selectedFile)">
                      <img :src="fileDataUrl" class="preview-image" />
                    </template>
                    <template v-else-if="isMarkdown(selectedFile)">
                      <div v-if="mdPreview" class="md-rendered">
                        <MarkdownPreview :content="fileContent" :finalized="true" :image-preview="false" :strip-frontmatter="true" />
                      </div>
                      <template v-else>
                        <pre v-if="skill.isBuiltin === 1" class="file-readonly">{{ fileContent }}</pre>
                        <a-textarea
                          v-else
                          v-model:value="fileContent"
                          :rows="24"
                          class="file-editor"
                          placeholder="文件内容"
                        />
                      </template>
                    </template>
                    <template v-else-if="isEditable(selectedFile)">
                      <pre v-if="skill.isBuiltin === 1" class="file-readonly">{{ fileContent }}</pre>
                      <a-textarea
                        v-else
                        v-model:value="fileContent"
                        :rows="24"
                        class="file-editor"
                        placeholder="文件内容"
                      />
                    </template>
                    <template v-else>
                      <div class="preview-unsupported">
                        <FileOutlined style="font-size: 32px; color: #d4d4d8" />
                        <p>该文件类型暂不支持预览</p>
                        <p class="file-meta">{{ selectedFile }} ({{ formatFileSize(fileSize) }})</p>
                      </div>
                    </template>
                  </a-spin>
                </div>
              </template>
              <template v-else>
                <div class="preview-empty">
                  <FolderOpenOutlined style="font-size: 40px; color: #d4d4d8" />
                  <p>选择左侧文件查看内容</p>
                </div>
              </template>
            </div>
          </div>
        </div>
      </a-tab-pane>

      <!-- Tab 3: 依赖 -->
      <a-tab-pane key="deps" tab="依赖">
        <div class="tab-body">
          <div class="deps-section">
            <h3 class="deps-title">依赖工具</h3>
            <div v-if="depTools.length === 0" class="deps-empty">无依赖工具</div>
            <div v-else class="deps-grid">
              <a-tooltip v-for="t in depTools" :key="t.id" :title="t.description" placement="topLeft" :overlay-style="{ maxWidth: '360px' }">
                <div class="dep-card" @click="openToolDetail(t)">
                  <span class="dep-icon" style="background: linear-gradient(135deg, #10b981, #059669)">
                    {{ (t.displayName || t.name || '?')[0].toUpperCase() }}
                  </span>
                  <div class="dep-info">
                    <span class="dep-name">{{ t.displayName || t.name }}</span>
                    <span class="dep-desc">{{ truncateText(t.description, 40) }}</span>
                  </div>
                </div>
              </a-tooltip>
            </div>
          </div>
          <div class="deps-section">
            <h3 class="deps-title">依赖 MCP Server</h3>
            <div v-if="depMcps.length === 0" class="deps-empty">无依赖 MCP Server</div>
            <div v-else class="deps-grid">
              <a-tooltip v-for="m in depMcps" :key="m.id" :title="m.description" placement="topLeft" :overlay-style="{ maxWidth: '360px' }">
                <div class="dep-card" @click="openMcpDetail(m)">
                  <span class="dep-icon" style="background: linear-gradient(135deg, #8b5cf6, #7c3aed)">
                    {{ (m.name || 'M')[0].toUpperCase() }}
                  </span>
                  <div class="dep-info">
                    <span class="dep-name">{{ m.name }}</span>
                    <span class="dep-desc">{{ truncateText(m.description, 40) }}</span>
                  </div>
                </div>
              </a-tooltip>
            </div>
          </div>
          <div class="deps-section">
            <h3 class="deps-title">依赖 Skill</h3>
            <div v-if="depSkills.length === 0" class="deps-empty">无依赖 Skill</div>
            <div v-else class="deps-grid">
              <a-tooltip v-for="s in depSkills" :key="s.id" :title="s.description" placement="topLeft" :overlay-style="{ maxWidth: '360px' }">
                <div class="dep-card" @click="router.push('/app/skills/' + s.id)">
                  <span class="dep-icon" style="background: linear-gradient(135deg, #f59e0b, #d97706)">
                    {{ (s.displayName || s.name || 'S')[0].toUpperCase() }}
                  </span>
                  <div class="dep-info">
                    <span class="dep-name">{{ s.displayName || s.name }}</span>
                    <span class="dep-desc">{{ truncateText(s.description, 40) }}</span>
                  </div>
                </div>
              </a-tooltip>
            </div>
          </div>
        </div>
      </a-tab-pane>
    </a-tabs>
    </a-spin>

    <!-- 新建文件弹窗 -->
    <a-modal v-model:open="showCreateFile" title="新建文件" :width="480" :maskClosable="false">
      <a-form :model="newFileForm" :label-col="{ span: 5 }">
        <a-form-item label="路径" required>
          <a-input v-model:value="newFileForm.path" placeholder="如 SKILL.md 或 images/logo.png" />
        </a-form-item>
        <a-form-item label="类型">
          <a-radio-group v-model:value="newFileForm.isDir">
            <a-radio :value="false">文件</a-radio>
            <a-radio :value="true">目录</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="!newFileForm.isDir" label="内容">
          <a-textarea v-model:value="newFileForm.content" :rows="6" placeholder="文件初始内容（可选）" />
        </a-form-item>
      </a-form>
      <template #footer>
        <button class="btn-cancel" @click="showCreateFile = false">取消</button>
        <button class="btn-primary-sm" :disabled="!newFileForm.path || creatingFile" @click="handleCreateFile">
          {{ creatingFile ? '创建中...' : '创建' }}
        </button>
      </template>
    </a-modal>

    <!-- 工具详情弹窗 -->
    <a-modal v-model:open="toolDetailVisible" :title="toolDetail?.displayName || toolDetail?.name || '工具详情'" :width="640" :footer="null" :maskClosable="false">
      <div v-if="toolDetail" class="dep-detail-body">
        <a-descriptions bordered :column="1" size="small">
          <a-descriptions-item label="名称">{{ toolDetail.displayName || toolDetail.name }}</a-descriptions-item>
          <a-descriptions-item label="标识">{{ toolDetail.name }}</a-descriptions-item>
          <a-descriptions-item label="类型">{{ toolDetail.toolType?.label || toolDetail.toolType?.code || toolDetail.toolType || '—' }}</a-descriptions-item>
          <a-descriptions-item label="描述">{{ toolDetail.description || '—' }}</a-descriptions-item>
          <a-descriptions-item v-if="toolDetail.endpointUrl" label="端点">{{ toolDetail.endpointUrl }}</a-descriptions-item>
        </a-descriptions>
      </div>
    </a-modal>

    <!-- MCP 详情弹窗 -->
    <a-modal v-model:open="mcpDetailVisible" :title="mcpDetail?.name || 'MCP Server 详情'" :width="640" :footer="null" :maskClosable="false">
      <div v-if="mcpDetail" class="dep-detail-body">
        <a-descriptions bordered :column="1" size="small">
          <a-descriptions-item label="名称">{{ mcpDetail.name }}</a-descriptions-item>
          <a-descriptions-item label="描述">{{ mcpDetail.description || '—' }}</a-descriptions-item>
          <a-descriptions-item label="安装类型">{{ mcpDetail.installType?.label || mcpDetail.installType?.code || mcpDetail.installType || '—' }}</a-descriptions-item>
          <a-descriptions-item label="传输协议">{{ mcpDetail.transport?.label || mcpDetail.transport?.code || mcpDetail.transport || '—' }}</a-descriptions-item>
          <a-descriptions-item v-if="mcpDetail.host" label="服务地址">{{ mcpDetail.host }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="mcpDetail.status === 'disabled' ? 'default' : 'success'">
              {{ mcpDetail.status === 'disabled' ? '已禁用' : '已启用' }}
            </a-tag>
          </a-descriptions-item>
        </a-descriptions>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, watch, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeftOutlined, ExportOutlined, DeleteOutlined, ReloadOutlined,
  FileAddOutlined, FolderOutlined, FileTextOutlined, FileOutlined,
  FolderOpenOutlined, SaveOutlined, RightOutlined, DownOutlined,
  EyeOutlined, EditOutlined
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getSkills, deleteSkill, exportSkillZip,
  getSkillFiles, readSkillFile, createSkillFile, updateSkillFile, deleteSkillFile
} from '../api/skill'
import { getTools } from '../api/tool'
import { getMcpServers } from '../api/mcp'
import { getEnabledSkills } from '../api/skill'
import { truncateText } from '../utils/format'
import MarkdownPreview from '../components/MarkdownPreview.vue'

// ==================== 文件树节点组件 ====================

const TreeNode = {
  name: 'TreeNode',
  props: {
    node: { type: Object, required: true },
    depth: { type: Number, default: 0 },
    selected: { type: String, default: null },
  },
  emits: ['select'],
  setup(props, { emit }) {
    const expanded = ref(false)
    const toggle = () => {
      if (props.node.isDir) expanded.value = !expanded.value
    }
    const handleClick = () => {
      if (props.node.isDir) {
        expanded.value = !expanded.value
      } else {
        emit('select', props.node.path)
      }
    }
    return () => {
      const { node, depth, selected } = props
      const indent = depth * 20
      const isSelected = !node.isDir && selected === node.path
      return h('div', { class: 'tree-node-wrapper' }, [
        h('div', {
          class: ['tree-node-row', { 'tree-selected': isSelected, 'tree-dir': node.isDir }],
          style: { paddingLeft: indent + 'px' },
          onClick: handleClick,
        }, [
          // 展开/折叠箭头
          node.isDir
            ? h('span', { class: 'tree-arrow' }, [
                expanded.value
                  ? h(DownOutlined, { style: 'font-size: 10px; color: #a1a1aa' })
                  : h(RightOutlined, { style: 'font-size: 10px; color: #a1a1aa' })
              ])
            : h('span', { class: 'tree-arrow-placeholder' }),
          // 图标
          node.isDir
            ? h(FolderOutlined, { style: 'font-size: 14px; color: #d4a017; margin-right: 6px' })
            : h(FileTextOutlined, { style: 'font-size: 14px; color: var(--color-mute); margin-right: 6px' }),
          // 文件名
          h('span', { class: 'tree-name' }, node.name),
        ]),
        // 子节点
        node.isDir && expanded.value && node.children
          ? h('div', { class: 'tree-children' },
              node.children.map(child =>
                h(TreeNode, {
                  node: child,
                  depth: depth + 1,
                  selected: selected,
                  onSelect: (path) => emit('select', path),
                })
              )
            )
          : null,
      ])
    }
  },
}

const route = useRoute()
const router = useRouter()

const skill = ref({})
const activeTab = ref('info')
const loading = ref(false)
const promptMdPreview = ref(true) // 提示词模板默认 markdown 渲染

// 文件管理
const filesLoading = ref(false)
const fileTree = ref([])
const selectedFile = ref(null)
const fileContent = ref('')
const fileDataUrl = ref('')
const fileSize = ref(0)
const fileLoading = ref(false)
const mdPreview = ref(true) // true=渲染markdown，false=查看原文
const fileSaving = ref(false)
const showCreateFile = ref(false)
const creatingFile = ref(false)
const newFileForm = reactive({ path: '', content: '', isDir: false })

// 依赖详情弹窗
const toolDetailVisible = ref(false)
const toolDetail = ref(null)
const mcpDetailVisible = ref(false)
const mcpDetail = ref(null)

// 依赖
const toolOptions = ref([])
const mcpOptions = ref([])
const toolList = ref([])
const mcpList = ref([])
const skillList = ref([])

const depTools = computed(() => {
  const ids = parseIdArray(skill.value.toolIds)
  return toolList.value.filter(t => ids.includes(String(t.id)))
})

const depMcps = computed(() => {
  const ids = parseIdArray(skill.value.mcpServerIds)
  return mcpList.value.filter(m => ids.includes(String(m.id)))
})

const depSkills = computed(() => {
  const deps = parseIdArray(skill.value.skillDependencies)
  if (!deps.length) return []
  return skillList.value.filter(s => deps.includes(s.slug))
})

const EDITABLE_EXTENSIONS = ['.md', '.txt', '.json', '.yaml', '.yml', '.xml', '.html', '.htm', '.css', '.js', '.py', '.sh', '.toml', '.ini', '.cfg', '.conf', '.env', '.gitignore', '.gitkeep']
const IMAGE_EXTENSIONS = ['.png', '.jpg', '.jpeg', '.gif', '.svg', '.webp', '.bmp', '.ico']

function isEditable(path) {
  if (!path) return false
  const lower = path.toLowerCase()
  if (!lower.includes('.')) return true
  return EDITABLE_EXTENSIONS.some(ext => lower.endsWith(ext))
}

function isImage(path) {
  if (!path) return false
  const lower = path.toLowerCase()
  return IMAGE_EXTENSIONS.some(ext => lower.endsWith(ext))
}

function isMarkdown(path) {
  if (!path) return false
  const lower = path.toLowerCase()
  return lower.endsWith('.md') || lower.endsWith('.markdown')
}

function formatFileSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return size.toFixed(i > 0 ? 1 : 0) + ' ' + units[i]
}

function parseIdArray(raw) {
  if (!raw) return []
  try {
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    return Array.isArray(arr) ? arr.map(String) : []
  } catch {
    return []
  }
}

function formatIdLabels(raw, options) {
  const ids = parseIdArray(raw)
  if (!ids.length) return ''
  const labelMap = Object.fromEntries((options || []).map(o => [String(o.value), o.label]))
  return ids.map(id => labelMap[id] || `[${id}]`).join('、')
}

function goBack() {
  router.push('/app/extensions?tab=skills')
}

async function loadSkill() {
  loading.value = true
  try {
    const id = route.params.id
    const res = await getSkills({ pageNum: 1, pageSize: 100 })
    const records = res.data?.records || res.data || []
    skill.value = records.find(r => String(r.id) === String(id)) || {}
    if (!skill.value.id) {
      message.error('Skill 不存在')
      router.push('/app/extensions?tab=skills')
    }
  } catch {
    router.push('/app/extensions?tab=skills')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [toolRes, mcpRes, skillRes] = await Promise.all([
      getTools({ pageNum: 1, pageSize: 200 }),
      getMcpServers({ pageNum: 1, pageSize: 100 }),
      getEnabledSkills(),
    ])
    toolList.value = toolRes.data?.records || []
    mcpList.value = mcpRes.data?.records || []
    skillList.value = skillRes.data || []
    toolOptions.value = toolList.value.map(t => ({ label: t.displayName || t.name, value: String(t.id) }))
    mcpOptions.value = mcpList.value.map(m => ({ label: m.name, value: String(m.id) }))
  } catch {
    // ignore
  }
}

// ==================== 依赖详情 ====================

function openToolDetail(tool) {
  toolDetail.value = tool
  toolDetailVisible.value = true
}

function openMcpDetail(mcp) {
  mcpDetail.value = mcp
  mcpDetailVisible.value = true
}

// ==================== 文件管理 ====================

async function refreshFiles() {
  if (!skill.value.id) return
  filesLoading.value = true
  try {
    const res = await getSkillFiles(skill.value.id)
    fileTree.value = res.data || []
  } catch {
    // interceptor handles error
  } finally {
    filesLoading.value = false
  }
}

async function handleFileSelect(path) {
  if (!path) return
  // 目录路径以 / 结尾，不加载
  if (path.endsWith('/')) return

  selectedFile.value = path
  fileLoading.value = true
  fileContent.value = ''
  fileDataUrl.value = ''
  fileSize.value = 0
  mdPreview.value = true

  try {
    const res = await readSkillFile(skill.value.id, path)
    // 拦截器已解包 response.data，res = {code, data, message}，res.data 即 base64 字符串
    const base64Data = res.data
    if (!base64Data) {
      fileContent.value = ''
      return
    }

    if (isImage(path)) {
      // 图片：base64 → blob URL
      const mimeType = getMimeType(path)
      const byteChars = atob(base64Data)
      const byteArray = new Uint8Array(byteChars.length)
      for (let i = 0; i < byteChars.length; i++) {
        byteArray[i] = byteChars.charCodeAt(i)
      }
      const blob = new Blob([byteArray], { type: mimeType })
      fileSize.value = blob.size
      fileDataUrl.value = URL.createObjectURL(blob)
    } else {
      // 文本：base64 → utf-8
      const byteChars = atob(base64Data)
      const byteArray = new Uint8Array(byteChars.length)
      for (let i = 0; i < byteChars.length; i++) {
        byteArray[i] = byteChars.charCodeAt(i)
      }
      fileContent.value = new TextDecoder('utf-8').decode(byteArray)
      fileSize.value = byteArray.length
    }
  } catch {
    // interceptor handles error
  } finally {
    fileLoading.value = false
  }
}

function getMimeType(path) {
  const lower = path.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg'
  if (lower.endsWith('.gif')) return 'image/gif'
  if (lower.endsWith('.svg')) return 'image/svg+xml'
  if (lower.endsWith('.webp')) return 'image/webp'
  if (lower.endsWith('.bmp')) return 'image/bmp'
  if (lower.endsWith('.ico')) return 'image/x-icon'
  return 'application/octet-stream'
}

async function handleSaveFile() {
  if (!selectedFile.value || !skill.value.id || skill.value.isBuiltin === 1) return
  fileSaving.value = true
  try {
    await updateSkillFile(skill.value.id, {
      path: selectedFile.value,
      content: fileContent.value,
    })
    message.success('保存成功')
  } catch {
    // interceptor handles error
  } finally {
    fileSaving.value = false
  }
}

async function handleDeleteFile() {
  if (!selectedFile.value || !skill.value.id || skill.value.isBuiltin === 1) return
  Modal.confirm({
    title: '删除文件',
    content: `确定删除 ${selectedFile.value} 吗？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteSkillFile(skill.value.id, selectedFile.value)
      message.success('已删除')
      selectedFile.value = null
      fileContent.value = ''
      fileDataUrl.value = ''
      refreshFiles()
    },
  })
}

async function handleCreateFile() {
  if (!newFileForm.path || !skill.value.id || skill.value.isBuiltin === 1) return
  creatingFile.value = true
  try {
    await createSkillFile(skill.value.id, {
      path: newFileForm.path,
      content: newFileForm.isDir ? undefined : (newFileForm.content || ''),
      isDir: newFileForm.isDir,
    })
    message.success('创建成功')
    showCreateFile.value = false
    newFileForm.path = ''
    newFileForm.content = ''
    newFileForm.isDir = false
    refreshFiles()
  } catch {
    // interceptor handles error
  } finally {
    creatingFile.value = false
  }
}

// ==================== 导出/删除 ====================

async function handleExport() {
  if (!skill.value.id) return
  try {
    const res = await exportSkillZip(skill.value.id)
    const blob = new Blob([res.data], { type: 'application/zip' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${skill.value.slug || 'skill'}.zip`
    a.click()
    URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch {
    // interceptor handles error
  }
}

function handleDelete() {
  if (!skill.value.id) return
  Modal.confirm({
    title: '删除 Skill',
    content: `确定删除「${skill.value.displayName || skill.value.name}」吗？此操作不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteSkill(skill.value.id)
      message.success('已删除')
      router.push('/app/extensions?tab=skills')
    },
  })
}

// Tab 切换时加载文件
watch(activeTab, (tab) => {
  if (tab === 'files' && fileTree.value.length === 0) {
    refreshFiles()
  }
})

onMounted(() => {
  loadSkill()
  loadOptions()
})

onUnmounted(() => {
  if (fileDataUrl.value) URL.revokeObjectURL(fileDataUrl.value)
})
</script>

<style scoped>
.skill-detail-page {
  height: 100vh;
  overflow: hidden;
  background: var(--color-canvas-soft);
  display: flex;
  flex-direction: column;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 32px 16px;
  background: var(--color-canvas);
  border-bottom: 1px solid var(--color-hairline);
  flex-shrink: 0;
}

.btn-back {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  color: var(--color-body);
  flex-shrink: 0;
}
.btn-back:hover { border-color: var(--color-ink); color: var(--color-ink); }

.header-info {
  flex: 1;
  min-width: 0;
}
.header-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0 0 4px;
}
.header-tags {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.detail-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
/* a-spin 内部容器必须继承 flex 布局，否则 tabs 无法撑满 */
:deep(.ant-spin-nested-loading),
:deep(.ant-spin-nested-loading > .ant-spin-container) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}
.detail-tabs :deep(.ant-tabs-nav) {
  padding: 0 32px;
  background: var(--color-canvas);
  margin-bottom: 0;
}
.detail-tabs :deep(.ant-tabs-content-holder) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.detail-tabs :deep(.ant-tabs-content) {
  height: auto;
}
.detail-tabs :deep(.ant-tabs-tabpane) {
  min-height: 0;
}

.tab-body {
  padding: 24px 32px;
}

.tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}
.tag-slug {
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  color: #be185d;
  background: var(--color-purple-bg);
}
.tag-version {
  color: #0369a1;
  background: var(--color-info-bg);
}

.detail-section {
  margin-top: 20px;
}
.detail-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 8px;
}
.detail-pre {
  background: var(--color-canvas-soft-2);
  padding: 16px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  min-height: 200px;
}

/* 文件管理 */
.file-manager {
  padding: 16px 32px;
  display: flex;
  flex-direction: column;
  height: 100%;
}
.file-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-shrink: 0;
}
.builtin-hint {
  font-size: 12px;
  color: var(--color-mute);
  margin-left: 4px;
}
.file-content {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

/* 文件树面板 */
.file-tree-panel {
  width: 280px;
  flex-shrink: 0;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  overflow-y: auto;
  background: var(--color-canvas);
}
.tree-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: var(--color-mute);
  font-size: 13px;
}
.empty-tree {
  text-align: center;
  padding: 40px 0;
  color: var(--color-mute);
  font-size: 13px;
}
.tree-container {
  padding: 8px 0;
}

/* 树节点行 */
:deep(.tree-node-row) {
  display: flex;
  align-items: center;
  height: 30px;
  padding-right: 12px;
  cursor: pointer;
  user-select: none;
  transition: background 0.1s;
  white-space: nowrap;
}
:deep(.tree-node-row:hover) {
  background: var(--color-canvas-soft-2);
}
:deep(.tree-selected) {
  background: #eff6ff !important;
}
:deep(.tree-selected .tree-name) {
  color: #1d4ed8;
  font-weight: 500;
}
:deep(.tree-dir) {
  font-weight: 500;
}
:deep(.tree-arrow) {
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: 2px;
}
:deep(.tree-arrow-placeholder) {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  margin-right: 2px;
}
:deep(.tree-name) {
  font-size: 13px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
}
:deep(.tree-children) {
  /* 子节点缩进由 depth * 20px 控制 */
}

/* 文件预览 */
.file-preview {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-canvas);
}
.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid var(--color-hairline);
  background: var(--color-canvas-soft);
  flex-shrink: 0;
}
.preview-path {
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  font-size: 13px;
  color: var(--color-body);
}
.preview-actions {
  display: flex;
  gap: 8px;
}
.preview-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.preview-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  display: block;
  margin: 16px auto;
}
.md-rendered {
  padding: 16px 20px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-ink);
}
.md-rendered :deep(h1),
.md-rendered :deep(h2),
.md-rendered :deep(h3) {
  margin-top: 1em;
  margin-bottom: 0.5em;
}
.md-rendered :deep(pre) {
  background: var(--color-canvas-soft-2);
  padding: 12px 16px;
  border-radius: 6px;
  overflow-x: auto;
}
.md-rendered :deep(code) {
  background: var(--color-canvas-soft-2);
  padding: 2px 5px;
  border-radius: 3px;
  font-size: 13px;
}
.md-rendered :deep(pre code) {
  background: none;
  padding: 0;
}
.md-rendered :deep(img) {
  max-width: 100%;
  border-radius: 6px;
}
.md-rendered :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}
.md-rendered :deep(th),
.md-rendered :deep(td) {
  border: 1px solid var(--color-hairline);
  padding: 8px 12px;
  text-align: left;
}
.md-rendered :deep(th) {
  background: var(--color-canvas-soft);
  font-weight: 600;
}
.preview-empty, .preview-unsupported {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 300px;
  color: var(--color-mute);
  font-size: 14px;
  gap: 8px;
}
.file-meta {
  font-size: 12px;
  color: #d4d4d8;
}
.file-readonly {
  background: var(--color-canvas-soft);
  padding: 16px;
  margin: 0;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  min-height: 300px;
  overflow-y: auto;
}
.file-editor {
  border: none;
  border-radius: 0;
  resize: none;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
}
.file-editor:focus {
  box-shadow: none;
}

/* 依赖 */
.deps-section {
  margin-bottom: 24px;
}
.deps-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0 0 12px;
}
.deps-empty {
  color: var(--color-mute);
  font-size: 13px;
  padding: 12px 0;
}
.deps-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}
.dep-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  background: var(--color-canvas);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.dep-card:hover {
  border-color: var(--color-hairline);
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.dep-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  font-size: 16px;
  flex-shrink: 0;
}
.dep-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.dep-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-ink);
}
.dep-desc {
  font-size: 12px;
  color: var(--color-mute);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dep-detail-body {
  max-height: 60vh;
  overflow-y: auto;
}

/* 按钮 */
.btn-outline {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  transition: border-color 0.2s;
  color: var(--color-body);
}
.btn-outline:hover { border-color: var(--color-link); color: var(--color-link); }
.btn-outline.danger:hover { border-color: #ef4444; color: #ef4444; }
.btn-outline-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  color: var(--color-body);
}
.btn-outline-sm:hover { border-color: var(--color-link); color: var(--color-link); }
.btn-outline-sm.danger:hover { border-color: #ef4444; color: #ef4444; }
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
.btn-primary-sm:disabled { background: #d4d4d8; cursor: not-allowed; }
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

/* a-descriptions 表头不换行（参考 TaskCenter） */
:deep(.ant-descriptions-view) {
  table-layout: fixed;
}
:deep(.ant-descriptions-item-label) {
  width: 100px;
  min-width: 100px;
  white-space: nowrap;
}
:deep(.ant-descriptions-item-content) {
  word-break: break-all;
  overflow-wrap: break-word;
}
</style>
