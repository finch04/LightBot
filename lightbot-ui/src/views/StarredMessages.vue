<template>
  <a-modal
    :open="open"
    title="收藏消息"
    :footer="null"
    width="760px"
    :bodyStyle="{ maxHeight: '65vh', overflow: 'auto', padding: '0' }"
    @update:open="$emit('update:open', $event)"
  >
    <div class="starred-page">
      <a-spin :spinning="loading">
        <div v-if="messages.length === 0 && !loading" class="empty-state">
          <StarOutlined style="font-size: 48px; color: var(--color-mute); margin-bottom: 16px" />
          <p>暂无收藏消息</p>
          <p class="empty-hint">在对话中点击消息下方的收藏图标即可收藏</p>
        </div>

        <div v-else class="starred-list">
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="starred-item"
            @click="confirmGoToSession(msg)"
          >
            <div class="starred-item-header">
              <span class="starred-role" :class="msg.role">
                {{ msg.role === 'assistant' ? 'AI' : '用户' }}
              </span>
              <span class="starred-time">{{ formatTime(msg.createTime) }}</span>
              <a-tooltip title="取消收藏">
                <button class="btn-unstar" @click.stop="unstar(msg)">
                  <StarFilled style="color: #f59e0b" />
                </button>
              </a-tooltip>
            </div>
            <div class="starred-content">{{ truncateContent(msg.content) }}</div>
          </div>
        </div>

        <div v-if="total > pageSize" class="starred-pagination">
          <a-pagination
            v-model:current="pageNum"
            :total="total"
            :page-size="pageSize"
            :show-size-changer="false"
            @change="loadData"
          />
        </div>
      </a-spin>
    </div>
  </a-modal>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { StarOutlined, StarFilled } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getStarredMessages, toggleMessageStar } from '../api/chatSession'
import { formatRelativeTime as formatTime } from '../utils/format'

const props = defineProps({ open: Boolean })
const emit = defineEmits(['update:open'])

const router = useRouter()
const loading = ref(false)
const messages = ref([])
const pageNum = ref(1)
const pageSize = 20
const total = ref(0)

watch(() => props.open, v => { if (v) loadData() })

async function loadData() {
  loading.value = true
  try {
    const res = await getStarredMessages({ pageNum: pageNum.value, pageSize })
    messages.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function unstar(msg) {
  try {
    await toggleMessageStar(msg.id)
    messages.value = messages.value.filter(m => m.id !== msg.id)
    total.value--
    if (messages.value.length === 0 && pageNum.value > 1) {
      pageNum.value--
      loadData()
    }
  } catch {
    message.error('操作失败')
  }
}

function confirmGoToSession(msg) {
  if (!msg.sessionId) return
  Modal.confirm({
    title: '跳转确认',
    content: '即将离开当前页面并跳转到对应会话，是否继续？',
    okText: '跳转',
    cancelText: '取消',
    onOk() {
      emit('update:open', false)
      router.push(`/app/chat/${msg.sessionId}`)
    },
  })
}

function truncateContent(content) {
  if (!content) return ''
  return content.length > 200 ? content.slice(0, 200) + '...' : content
}

</script>

<style scoped>
.starred-page {
  padding: 16px 8px 16px 0;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  color: var(--color-mute);
}
.empty-hint {
  font-size: 13px;
  margin-top: 4px;
}
.starred-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.starred-item {
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 14px 16px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.starred-item:hover {
  border-color: var(--color-link);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.starred-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.starred-role {
  font-size: 12px;
  font-weight: 500;
  padding: 1px 6px;
  border-radius: 4px;
}
.starred-role.user {
  background: var(--color-link-bg-soft);
  color: var(--color-link);
}
.starred-role.assistant {
  background: #f0fdf4;
  color: #16a34a;
}
.starred-time {
  font-size: 12px;
  color: var(--color-mute);
  flex: 1;
}
.btn-unstar {
  background: none;
  border: none;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  opacity: 0.6;
  transition: opacity 0.15s;
}
.btn-unstar:hover {
  opacity: 1;
}
.starred-content {
  font-size: 14px;
  color: var(--color-body);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.starred-pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
