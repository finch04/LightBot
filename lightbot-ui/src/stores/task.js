import { reactive } from 'vue'

/**
 * 任务中心 SSE 实时计数（跨组件共享）
 * MainLayout SSE 连接写入，TaskCenter 读取
 */
export const taskCounts = reactive({
  active: 0,
  pending: 0,
  running: 0,
})

export function updateTaskCounts(counts) {
  taskCounts.active = counts.active || 0
  taskCounts.pending = counts.pending || 0
  taskCounts.running = counts.running || 0
}
