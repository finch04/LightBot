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
import { ThunderboltOutlined, RobotOutlined, LoadingOutlined, RightOutlined } from '@ant-design/icons-vue'

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

watch(() => props.events, syncExpanded, { immediate: true, deep: true })

function hasSubagentResult(callIndex) {
  return !!getSubagentResult(callIndex)
}

function getSubagentResult(callIndex) {
  const call = props.events[callIndex]
  if (!call || call.type !== 'subagent_call') return null
  const name = call.subagentName
  const offset = call.contentOffset
  const resultEvt = props.events.find(
    e => e.type === 'subagent_result'
      && e.subagentName === name
      && e.contentOffset === offset
  )
  return resultEvt?.result || null
}
</script>

<style lang="less" scoped>
.capability-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.cap-block {
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  overflow: hidden;
}

.cap-skill {
  background: #fdf2f8;
  border-color: #f9a8d4;
}

.cap-subagent {
  background: #fffbeb;
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
  color: #374151;
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
  color: #9ca3af;
}

.expand-icon {
  font-size: 10px;
  transition: transform 0.2s;
  color: #9ca3af;
}

.expand-icon.expanded {
  transform: rotate(90deg);
}

.cap-body {
  padding: 0 12px 10px 34px;
  font-size: 12px;
  color: #4b5563;
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
  color: #9ca3af;
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
  color: #6b7280;
}
</style>
