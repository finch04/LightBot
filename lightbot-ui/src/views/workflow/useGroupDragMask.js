import { inject, computed, unref } from 'vue'

/** WorkflowEdit provide：当前正在拖动的循环/批处理容器 id */
export const WORKFLOW_DRAGGING_GROUP_ID_KEY = Symbol('workflowDraggingGroupId')

/**
 * 容器拖拽时，子节点（含内置开始/结束）是否显示遮罩
 * @param {Object} props 节点 props（需含 parentNode / data.groupId）
 */
export function useGroupDragMask(props) {
  const draggingGroupId = inject(WORKFLOW_DRAGGING_GROUP_ID_KEY, null)

  const isGroupChildDragMasked = computed(() => {
    if (props?.data?.groupDragMasked) return true
    const gid = unref(draggingGroupId)
    if (!gid) return false
    const parentId = props?.parentNode || props?.parentId || props?.data?.groupId
    return parentId === gid
  })

  return { isGroupChildDragMasked }
}
