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
      v-model:expandedKeys="expandedKeys"
      v-model:selectedKeys="selectedKeys"
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
            <component :is="node.icon" class="sft-node-icon" />
            <span class="sft-node-label" :title="node.title">{{ node.title }}</span>
          </span>
          <span v-if="node.isLeaf" class="sft-node-actions">
            <button class="sft-act" title="预览" @click.stop="emit('select', node.dataRef)">
              <EyeOutlined />
            </button>
            <button class="sft-act" title="下载" @click.stop="emit('download', node.dataRef)">
              <DownloadOutlined />
            </button>
            <button class="sft-act sft-act-danger" title="删除" @click.stop="removeFile(node.dataRef)">
              <DeleteOutlined />
            </button>
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
  FolderOpenOutlined, FileOutlined, FileImageOutlined, FilePdfOutlined,
} from '@ant-design/icons-vue'
import { getSessionFileTree, deleteSessionFile } from '../api/chatSession'

const props = defineProps({
  sessionId: { type: [String, Number], default: '' },
  /** 用于外部触发刷新 */
  refreshTick: { type: Number, default: 0 },
})
const emit = defineEmits(['select', 'refreshed'])

const loading = ref(false)
const expandedKeys = ref([])
const selectedKeys = ref([])
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
    emit('refreshed', res.data?.stats)
  } catch (e) {
    treeData.value = []
  } finally {
    loading.value = false
  }
}

async function onLoadChildren(treeNode) {
  if (!treeNode || treeNode.isLeaf || treeNode.children?.length) return
  const path = treeNode.dataRef?.path
  if (!path) return
  try {
    const res = await getSessionFileTree(props.sessionId, path)
    const children = (res.data?.entries || []).map(toTreeNode)
    treeNode.children = children
    treeData.value = [...treeData.value]
  } catch (e) {
    treeNode.children = []
  }
}

function toTreeNode(entry) {
  const isLeaf = !entry.directory
  return {
    key: entry.path,
    title: entry.name,
    path: entry.path,
    isLeaf,
    icon: isLeaf ? fileIcon(entry) : FolderOpenOutlined,
    dataRef: entry,
    children: isLeaf ? undefined : [],
  }
}

function fileIcon(entry) {
  const mime = entry.mimeType || ''
  const name = (entry.name || '').toLowerCase()
  if (mime.startsWith('image/') || /\.(png|jpe?g|gif|webp|svg)$/.test(name)) return FileImageOutlined
  if (mime === 'application/pdf' || name.endsWith('.pdf')) return FilePdfOutlined
  return FileOutlined
}

function onSelect(keys, info) {
  const node = info?.node?.dataRef
  if (node && !node.directory) {
    emit('select', node)
  }
}

async function removeFile(entry) {
  Modal.confirm({
    title: '删除文件',
    content: `确认删除「${entry.name}」？该操作不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteSessionFile(props.sessionId, entry.path)
        message.success('已删除')
        await loadRoot()
      } catch (e) {
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
.sft-node-icon { font-size: 14px; color: var(--color-mute); flex-shrink: 0; }
.sft-node-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sft-node-actions { display: none; gap: 2px; flex-shrink: 0; }
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
