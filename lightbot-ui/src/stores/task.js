import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 任务中心 SSE 实时计数（跨组件共享）
 * MainLayout SSE 连接写入，TaskCenter 读取
 */
export const useTaskStore = defineStore('task', () => {
  const active = ref(0)
  const pending = ref(0)
  const running = ref(0)

  function updateCounts(counts) {
    active.value = counts.active || 0
    pending.value = counts.pending || 0
    running.value = counts.running || 0
  }

  return { active, pending, running, updateCounts }
})
