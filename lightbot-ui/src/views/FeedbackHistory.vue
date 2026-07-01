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
        <a-radio-group v-model:value="filterRating" button-style="solid" size="small" @change="onFilterChange">
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
              <div class="feedback-item-meta">
                <a-tag :color="fb.rating === 'like' ? 'success' : 'error'" class="feedback-tag">
                  <LikeOutlined v-if="fb.rating === 'like'" />
                  <DislikeOutlined v-else />
                  {{ fb.rating === 'like' ? '有帮助' : '无帮助' }}
                </a-tag>
                <span v-if="fb.agentName" class="feedback-agent">
                  {{ fb.agentName }}
                  <span v-if="fb.agentVersion != null" class="feedback-agent-version">
                    {{ formatAgentVersion(fb.agentVersion) }}
                  </span>
                </span>
              </div>
              <span class="feedback-time">{{ formatTime(fb.createTime) }}</span>
            </div>
            <a-tooltip
              v-if="isContentTruncated(fb.messageContent)"
              :title="fb.messageContent"
              placement="topLeft"
              :overlay-style="{ maxWidth: '520px' }"
              overlay-class-name="feedback-content-tooltip"
              @click.stop
            >
              <div class="feedback-content feedback-content--truncated">{{ previewContent(fb.messageContent) }}</div>
            </a-tooltip>
            <div v-else class="feedback-content">{{ fb.messageContent }}</div>
            <div
              v-if="fb.rating === 'dislike' && fb.reason"
              class="feedback-reason-block"
              @click.stop
            >
              <div class="feedback-reason-head">
                <CommentOutlined class="feedback-reason-icon" />
                <span class="feedback-reason-title">无帮助原因</span>
              </div>
              <div class="feedback-reason-text">{{ fb.reason }}</div>
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
import { LikeOutlined, DislikeOutlined, CommentOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { listMessageFeedbacks } from '../api/chat'
import { formatRelativeTime as formatTime } from '../utils/format'

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

function onFilterChange() {
  pageNum.value = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const res = await listMessageFeedbacks(pageNum.value, pageSize, filterRating.value)
    feedbacks.value = res.data?.records || []
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
      router.push(`/app/chat/${fb.sessionId}`)
    },
  })
}

const CONTENT_PREVIEW_MAX = 200

function isContentTruncated(content) {
  return Boolean(content && content.length > CONTENT_PREVIEW_MAX)
}

function previewContent(content) {
  if (!content) return ''
  if (!isContentTruncated(content)) return content
  return content.slice(0, CONTENT_PREVIEW_MAX) + '…'
}

function formatAgentVersion(version) {
  if (version == null) return ''
  return version === 0 ? '草稿' : `v${version}`
}

</script>

<style scoped>
.feedback-page {
  padding: 16px 8px 16px 0;
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
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.feedback-item-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.feedback-agent {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.feedback-agent-version {
  margin-left: 4px;
  color: var(--color-mute);
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
.feedback-content--truncated {
  cursor: help;
}
.feedback-reason-block {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #fecaca;
  border-left: 3px solid #ef4444;
  background: var(--color-error-bg, #fef2f2);
}
.feedback-reason-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.feedback-reason-icon {
  font-size: 13px;
  color: #dc2626;
}
.feedback-reason-title {
  font-size: 12px;
  font-weight: 600;
  color: #b91c1c;
  letter-spacing: 0.02em;
}
.feedback-reason-text {
  font-size: 13px;
  line-height: 1.65;
  color: #7f1d1d;
  white-space: pre-wrap;
  word-break: break-word;
}
.feedback-pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>

<style>
.feedback-content-tooltip .ant-tooltip-inner {
  max-height: 320px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  text-align: left;
}
</style>
