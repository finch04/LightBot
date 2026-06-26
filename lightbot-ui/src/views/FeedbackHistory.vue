<template>
  <a-modal
    :open="open"
    title="反馈记录"
    :footer="null"
    width="760px"
    :bodyStyle="{ maxHeight: '65vh', overflow: 'auto', padding: '0' }"
    @update:open="$emit('update:open', $event)"
  >
    <div class="feedback-page">
      <div class="feedback-header">
        <a-radio-group v-model:value="filterRating" button-style="solid" size="small" @change="loadData">
          <a-radio-button value="">全部</a-radio-button>
          <a-radio-button value="like">
            <LikeOutlined /> 有帮助
          </a-radio-button>
          <a-radio-button value="dislike">
            <DislikeOutlined /> 无帮助
          </a-radio-button>
        </a-radio-group>
      </div>

      <a-spin :spinning="loading">
        <div v-if="feedbacks.length === 0 && !loading" class="empty-state">
          <LikeOutlined style="font-size: 48px; color: var(--color-mute); margin-bottom: 16px" />
          <p>暂无反馈记录</p>
          <p class="empty-hint">在对话中对 AI 回复点击点赞/踩即可产生反馈</p>
        </div>

        <div v-else class="feedback-list">
          <div
            v-for="fb in feedbacks"
            :key="fb.id"
            class="feedback-item"
            @click="confirmGoToSession(fb)"
          >
            <div class="feedback-item-header">
              <a-tag :color="fb.rating === 'like' ? 'success' : 'error'" class="feedback-tag">
                <LikeOutlined v-if="fb.rating === 'like'" />
                <DislikeOutlined v-else />
                {{ fb.rating === 'like' ? '有帮助' : '无帮助' }}
              </a-tag>
              <span class="feedback-time">{{ formatTime(fb.createTime) }}</span>
            </div>
            <div class="feedback-content">{{ truncateContent(fb.messageContent) }}</div>
            <div v-if="fb.reason" class="feedback-reason">
              <span class="reason-label">原因：</span>{{ fb.reason }}
            </div>
          </div>
        </div>

        <div v-if="total > pageSize" class="feedback-pagination">
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
import { LikeOutlined, DislikeOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { listMessageFeedbacks } from '../api/chat'

const props = defineProps({ open: Boolean })
const emit = defineEmits(['update:open'])

const router = useRouter()
const loading = ref(false)
const feedbacks = ref([])
const pageNum = ref(1)
const pageSize = 20
const total = ref(0)
const filterRating = ref('')

watch(() => props.open, v => { if (v) loadData() })

async function loadData() {
  loading.value = true
  try {
    const res = await listMessageFeedbacks(pageNum.value, pageSize)
    let records = res.data?.records || []
    if (filterRating.value) {
      records = records.filter(fb => fb.rating === filterRating.value)
    }
    feedbacks.value = records
    total.value = res.data?.total || 0
  } catch {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

function confirmGoToSession(fb) {
  if (!fb.sessionId) return
  Modal.confirm({
    title: '跳转确认',
    content: '即将离开当前页面并跳转到对应会话，是否继续？',
    okText: '跳转',
    cancelText: '取消',
    onOk() {
      emit('update:open', false)
      router.push(`/app/chat/${fb.sessionId}?highlight=${fb.messageId}`)
    },
  })
}

function truncateContent(content) {
  if (!content) return ''
  return content.length > 200 ? content.slice(0, 200) + '...' : content
}

function formatTime(time) {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diffMs = now - date
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin}分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour}小时前`
  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 30) return `${diffDay}天前`
  return date.toLocaleDateString()
}
</script>

<style scoped>
.feedback-page {
  padding: 16px 0;
}
.feedback-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 16px;
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
  color: var(--color-mute);
  margin-top: 8px;
}
.feedback-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.feedback-item {
  padding: 16px;
  background: var(--color-bg-container);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.feedback-item:hover {
  border-color: var(--color-primary);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.feedback-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.feedback-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.feedback-time {
  font-size: 12px;
  color: var(--color-mute);
}
.feedback-content {
  font-size: 14px;
  color: var(--color-body);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.feedback-reason {
  margin-top: 8px;
  padding: 8px 12px;
  background: var(--color-bg-elevated);
  border-radius: 6px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.reason-label {
  font-weight: 500;
  color: var(--color-body);
}
.feedback-pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
