<template>
  <div v-if="events && events.length > 0" class="capability-panel">
    <template v-for="(evt, i) in events" :key="i">
      <!-- Skill 启用 -->
      <div v-if="evt.type === 'skill_active'" class="cap-block cap-skill">
        <button type="button" class="cap-header" @click="toggle(i, $event)">
          <ThunderboltOutlined class="cap-icon" />
          <span class="cap-title">已启用 {{ (evt.skills || []).length }} 个 Skill</span>
          <LoadingOutlined v-if="!isDone" class="cap-spinner" />
          <RightOutlined :class="{ expanded: expanded.has(i) }" class="expand-icon" />
        </button>
        <div v-show="expanded.has(i)" class="cap-body">
          <div v-for="(sk, si) in evt.skills || []" :key="si" class="cap-skill-item">
            <span class="cap-skill-name">{{ sk.displayName || sk.name }}</span>
            <span v-if="sk.builtin" class="cap-inline-badge">内置</span>
            <span v-if="sk.slug" class="cap-meta">slug: {{ sk.slug }}</span>
          </div>
        </div>
      </div>

      <!-- SubAgent 委派 -->
      <div v-else-if="evt.type === 'subagent_call'" class="cap-block cap-subagent">
        <button type="button" class="cap-header" @click="toggle(i, $event)">
          <RobotOutlined class="cap-icon" />
          <span class="cap-title">委派 SubAgent：<strong>{{ evt.displayName || evt.subagentName }}</strong></span>
          <LoadingOutlined v-if="!isDone && !hasSubagentResult(i)" class="cap-spinner" />
          <RightOutlined :class="{ expanded: expanded.has(i) }" class="expand-icon" />
        </button>
        <div v-show="expanded.has(i)" class="cap-body">
          <div v-if="evt.task" class="cap-task">
            <span class="cap-label">任务</span>
            <pre>{{ evt.task }}</pre>
          </div>
          <!-- 子代理中间执行过程 -->
          <div v-if="getSubagentSteps(i).length > 0" class="cap-steps">
            <span class="cap-label">执行过程</span>
            <div v-for="(step, si) in getSubagentSteps(i)" :key="si" class="cap-step">
              <span v-if="step.type === 'subagent_tool_call'" class="cap-step-call">
                <CodeOutlined class="cap-step-icon" /> 调用工具: <strong>{{ step.toolName }}</strong>
              </span>
              <span v-else-if="step.type === 'subagent_tool_result'" class="cap-step-result">
                <CheckCircleOutlined class="cap-step-icon success" /> 工具结果
                <span v-if="step.content" class="cap-step-preview">{{ step.content }}</span>
              </span>
              <span v-else-if="step.type === 'subagent_token'" class="cap-step-token">
                {{ step.content }}
              </span>
            </div>
          </div>
          <!-- 子代理错误 / 重试 -->
          <div v-if="getSubagentErrorRetry(i)" class="cap-subagent-error-retry">
            <LoadingOutlined spin class="cap-step-icon" />
            <span>{{ getSubagentErrorRetry(i).message }}</span>
            <span class="cap-retry-count">{{ getSubagentErrorRetry(i).attempt }}/{{ getSubagentErrorRetry(i).maxRetries }}</span>
          </div>
          <div v-if="getSubagentError(i)" class="cap-subagent-error">
            <CloseCircleOutlined class="cap-step-icon error" />
            <span>{{ getSubagentError(i).message }}</span>
            <span v-if="getSubagentError(i).code" class="cap-error-code">{{ getSubagentError(i).code }}</span>
          </div>
          <!-- 最终结果 -->
          <div v-if="getSubagentResult(i)" class="cap-result">
            <span class="cap-label">执行结果</span>
            <pre>{{ getSubagentResult(i) }}</pre>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { ThunderboltOutlined, RobotOutlined, LoadingOutlined, RightOutlined, CodeOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  events: { type: Array, default: () => [] },
  isDone: { type: Boolean, default: true },
  defaultExpanded: { type: Boolean, default: true },
})

const emit = defineEmits(['heightChange'])

const expanded = ref(new Set())
const userToggled = new Set()

function toggle(i, event) {
  userToggled.add(i)
  const s = new Set(expanded.value)
  if (s.has(i)) s.delete(i)
  else s.add(i)
  expanded.value = s
  nextTick(() => emit('heightChange', event))
}

function syncExpanded() {
  if (!props.defaultExpanded) return
  const s = new Set(expanded.value)
  props.events.forEach((_, i) => { if (!userToggled.has(i)) s.add(i) })
  expanded.value = s
}

watch(() => props.events?.length, syncExpanded, { immediate: true })

function hasSubagentResult(callIndex) {
  return !!getSubagentResult(callIndex) || !!getSubagentError(callIndex)
}

function getSubagentResult(callIndex) {
  const call = props.events[callIndex]
  if (!call || call.type !== 'subagent_call') return null
  const name = call.subagentName
  const offset = call.contentOffset
  const resultEvt = props.events.find(
    e => e.type === 'subagent_result'
      && e.subagentName === name
      && e.contentOffset == offset
  )
  return resultEvt?.result || null
}

/**
 * 获取子代理执行中间步骤（工具调用、工具结果、token 输出）
 */
function getSubagentSteps(callIndex) {
  const call = props.events[callIndex]
  if (!call || call.type !== 'subagent_call') return []
  const name = call.subagentName
  const offset = call.contentOffset
  return props.events.filter(
    e => (e.type === 'subagent_tool_call' || e.type === 'subagent_tool_result' || e.type === 'subagent_token')
      && e.subagentName === name
      && e.contentOffset == offset
  )
}

function getSubagentError(callIndex) {
  const call = props.events[callIndex]
  if (!call || call.type !== 'subagent_call') return null
  const name = call.subagentName
  const offset = call.contentOffset
  return props.events.find(
    e => e.type === 'subagent_error' && e.subagentName === name && e.contentOffset == offset
  ) || null
}

function getSubagentErrorRetry(callIndex) {
  const call = props.events[callIndex]
  if (!call || call.type !== 'subagent_call') return null
  const name = call.subagentName
  const offset = call.contentOffset
  const retries = props.events.filter(
    e => e.type === 'subagent_error_retry' && e.subagentName === name && e.contentOffset == offset
  )
  return retries.length ? retries[retries.length - 1] : null
}
</script>

<style lang="less" scoped>
.capability-panel {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.cap-block {
  border-radius: 8px;
  border: 1px solid var(--color-hairline);
  overflow: hidden;
}

.cap-skill {
  background: var(--color-purple-bg);
  border-color: #f9a8d4;
}

.cap-subagent {
  background: var(--color-warn-bg);
  border-color: #fcd34d;
}

.cap-header {
  appearance: none;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-size: 13px;
  color: var(--color-text-dark);
}

.cap-icon {
  font-size: 14px;
}

.cap-skill .cap-icon {
  color: #db2777;
}

.cap-subagent .cap-icon {
  color: #d97706;
}

.cap-title {
  flex: 1;
}

.cap-spinner {
  color: var(--color-mute);
}

.expand-icon {
  font-size: 10px;
  transition: transform 0.2s;
  color: var(--color-mute);
}

.expand-icon.expanded {
  transform: rotate(90deg);
}

.cap-body {
  padding: 0 12px 10px 34px;
  font-size: 12px;
  color: var(--color-body);
}

.cap-skill-item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 4px 0;
}

.cap-skill-name {
  font-weight: 500;
  color: #9d174d;
}

.cap-inline-badge {
  font-size: 10px;
  padding: 0 5px;
  border-radius: 4px;
  background: #3b82f6;
  color: #fff;
}

.cap-meta {
  color: var(--color-mute);
  font-size: 11px;
}

.cap-task pre,
.cap-result pre {
  margin: 4px 0 0;
  padding: 8px;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  max-height: 200px;
  overflow: auto;
}

.cap-label {
  font-weight: 500;
  color: var(--color-mute);
}

.cap-steps {
  margin-top: 6px;
}

.cap-step {
  padding: 3px 0;
  font-size: 12px;
  line-height: 1.5;
}

.cap-step-call {
  color: #7c3aed;
  display: flex;
  align-items: center;
  gap: 4px;
}

.cap-step-result {
  color: #059669;
  display: flex;
  align-items: center;
  gap: 4px;
}

.cap-step-icon {
  font-size: 11px;
}

.cap-step-icon.success {
  color: #10b981;
}

.cap-step-preview {
  color: var(--color-mute);
  font-size: 11px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cap-step-token {
  color: var(--color-mute);
  font-size: 12px;
  line-height: 1.6;
  word-break: break-word;
}

.cap-subagent-error-retry {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  padding: 6px 8px;
  background: rgba(251, 191, 36, 0.15);
  border-radius: 6px;
  font-size: 12px;
  color: #b45309;
}

.cap-subagent-error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 6px;
  padding: 6px 8px;
  background: rgba(239, 68, 68, 0.1);
  border-radius: 6px;
  font-size: 12px;
  color: #dc2626;
}

.cap-error-code {
  font-size: 10px;
  padding: 0 4px;
  border-radius: 3px;
  background: rgba(0, 0, 0, 0.06);
  color: var(--color-mute);
  flex-shrink: 0;
}

.cap-retry-count {
  font-size: 11px;
  color: var(--color-mute);
  margin-left: auto;
}

.cap-step-icon.error {
  color: #ef4444;
}
</style>
