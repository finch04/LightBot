<template>
  <div class="session-file-tree">
    <div v-if="loading && !treeData.length" class="sft-empty">
      <LoadingOutlined spin /> 加载中...
    </div>
    <div v-else-if="!treeData.length" class="sft-empty">
      <FileTextOutlined class="sft-empty-icon" />
      <p>当前工作区为空</p>
      <p class="sft-empty-hint">上传附件或让 AI 生成文件后会出现在这里</p>
    </div>
    <a-tree
      v-else
      :key="treeRenderKey"
      v-model:expandedKeys="expandedKeys"
      v-model:selectedKeys="selectedKeys"
      v-model:loadedKeys="loadedKeys"
      :tree-data="treeData"
      :load-data="onLoadChildren"
      :block-node="true"
      :show-line="false"
      class="sft-tree"
      @select="onSelect"
    >
      <template #title="node">
        <div class="sft-node" :class="{ leaf: node.isLeaf }">
          <span class="sft-node-name">
            <FileTypeIcon
              :name="nodeIconName(node)"
              :is-dir="!node.isLeaf"
              :size="16"
              class="sft-node-icon"
            />
            <span class="sft-node-label" :title="node.title">{{ node.title }}</span>
          </span>
          <span v-if="node.isLeaf" class="sft-node-actions">
            <a-tooltip title="预览" placement="top" :get-popup-container="tooltipPopupContainer">
              <button class="sft-act" @click.stop="emit('preview', node.dataRef)">
                <EyeOutlined />
              </button>
            </a-tooltip>
            <a-tooltip title="下载" placement="top" :get-popup-container="tooltipPopupContainer">
              <button class="sft-act" @click.stop="downloadFile(node.dataRef)">
                <DownloadOutlined />
              </button>
            </a-tooltip>
            <a-tooltip title="删除" placement="top" :get-popup-container="tooltipPopupContainer">
              <button class="sft-act sft-act-danger" @click.stop="removeFile(node.dataRef)">
                <DeleteOutlined />
              </button>
            </a-tooltip>
          </span>
        </div>
      </template>
    </a-tree>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, shallowRef } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  LoadingOutlined, FileTextOutlined, EyeOutlined, DownloadOutlined, DeleteOutlined,
} from '@ant-design/icons-vue'
import { getSessionFileTree, getSessionFileDownloadUrl, deleteSessionFile } from '../api/chatSession'
import FileTypeIcon from './FileTypeIcon.vue'

const props = defineProps({
  sessionId: { type: [String, Number], default: '' },
  refreshTick: { type: Number, default: 0 },
})
const emit = defineEmits(['preview', 'refreshed'])

function tooltipPopupContainer() {
  return document.body
}

const loading = ref(false)
const expandedKeys = ref([])
const selectedKeys = ref([])
const loadedKeys = ref([])
const treeRenderKey = ref(0)
const treeData = shallowRef([])

watch(() => props.sessionId, () => loadRoot())
watch(() => props.refreshTick, () => loadRoot())
onMounted(() => loadRoot())

async function loadRoot() {
  if (!props.sessionId) {
    treeData.value = []
    return
  }
  loading.value = true
  try {
    const res = await getSessionFileTree(props.sessionId, '')
    treeData.value = (res.data?.entries || []).map(toTreeNode)
    expandedKeys.value = []
    selectedKeys.value = []
    loadedKeys.value = []
    treeRenderKey.value++
    emit('refreshed', res.data?.stats)
  } catch {
    treeData.value = []
  } finally {
    loading.value = false
  }
}

async function onLoadChildren(treeNode) {
  const path = resolveNodePath(treeNode)
  if (!path) return
  await loadChildrenForPath(path)
}

async function loadChildrenForPath(path) {
  try {
    const res = await getSessionFileTree(props.sessionId, path)
    const children = (res.data?.entries || []).map(toTreeNode)
    updateNodeChildren(treeData.value, path, children)
    treeData.value = [...treeData.value]
    if (!loadedKeys.value.includes(path)) {
      loadedKeys.value = [...loadedKeys.value, path]
    }
  } catch {
    updateNodeChildren(treeData.value, path, [])
    treeData.value = [...treeData.value]
  }
}

function resolveNodePath(treeNode) {
  if (!treeNode) return ''
  return treeNode.dataRef?.path || treeNode.path || treeNode.key || ''
}

function updateNodeChildren(nodes, path, children) {
  for (const n of nodes) {
    if (n.path === path) {
      n.children = children
      return true
    }
    if (Array.isArray(n.children) && updateNodeChildren(n.children, path, children)) {
      return true
    }
  }
  return false
}

function toTreeNode(entry) {
  const isLeaf = !entry.directory
  const displayName = entry.fileName || entry.name
  return {
    key: entry.path,
    title: displayName,
    path: entry.path,
    isLeaf,
    dataRef: entry,
    children: isLeaf ? undefined : [],
  }
}

function nodeIconName(node) {
  if (!node.isLeaf) return node.path || node.title || ''
  const ref = node.dataRef
  return ref?.fileName || ref?.name || node.title || ''
}

async function onSelect(_keys, info) {
  const node = info?.node?.dataRef || info?.node
  if (!node || node.isLeaf || node.directory === false) return
  const path = node.path
  if (!path) return
  const idx = expandedKeys.value.indexOf(path)
  if (idx >= 0) {
    expandedKeys.value = expandedKeys.value.filter(k => k !== path)
  } else {
    expandedKeys.value = [...expandedKeys.value, path]
    if (!loadedKeys.value.includes(path)) {
      await loadChildrenForPath(path)
    }
  }
}

async function downloadFile(entry) {
  if (!entry?.path) return
  try {
    const res = await getSessionFileDownloadUrl(props.sessionId, entry.path)
    if (res.data) window.open(res.data, '_blank')
  } catch {
    message.error('获取下载链接失败')
  }
}

async function removeFile(entry) {
  Modal.confirm({
    title: '删除文件',
    content: `确认删除「${entry.fileName || entry.name}」？该操作不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteSessionFile(props.sessionId, entry.path)
        message.success('已删除')
        await loadRoot()
      } catch {
        message.error('删除失败')
      }
    },
  })
}

defineExpose({ refresh: loadRoot, removeFile })
</script>

<style scoped>
.session-file-tree { height: 100%; overflow: auto; padding: 4px 0; }
.sft-empty {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 48px 16px; text-align: center; color: var(--color-mute);
}
.sft-empty-icon { font-size: 40px; color: var(--color-hairline-strong); margin-bottom: 12px; }
.sft-empty p { margin: 0; font-size: 13px; }
.sft-empty-hint { font-size: 12px !important; color: var(--color-mute) !important; margin-top: 6px !important; }

.sft-tree :deep(.ant-tree-node-content-wrapper) { padding-right: 4px; }
.sft-node { display: flex; align-items: center; justify-content: space-between; width: 100%; }
.sft-node-name { display: flex; align-items: center; gap: 6px; min-width: 0; overflow: hidden; }
.sft-node-icon { flex-shrink: 0; }
.sft-node-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sft-node-actions { display: none; gap: 2px; flex-shrink: 0; align-items: center; }
.sft-node-actions :deep(.ant-tooltip-open) { display: inline-flex; }
.sft-node:hover .sft-node-actions { display: inline-flex; }
.sft-act {
  width: 22px; height: 22px; border: none; background: transparent; cursor: pointer;
  color: var(--color-mute); border-radius: 4px; display: inline-flex; align-items: center; justify-content: center;
  font-size: 13px;
}
.sft-act:hover { background: var(--color-canvas-soft-2); color: var(--color-ink); }
.sft-act-danger:hover { color: #ef4444; background: #fef2f2; }
[data-theme="dark"] .sft-act-danger:hover { background: rgba(239,68,68,0.15); }
</style>
