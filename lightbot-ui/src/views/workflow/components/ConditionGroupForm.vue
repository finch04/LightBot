<template>
  <div class="condition-group-form">
    <div v-for="(group, gIdx) in groups" :key="group.id" class="condition-group-card">
      <div class="condition-group-header">
        <span class="condition-group-badge" :class="`handle-${group.sourceHandle}`">
          {{ handleLabel(group.sourceHandle) }}
        </span>
        <a-input
          v-model:value="group.label"
          size="small"
          class="condition-group-title-input"
          :disabled="disabled"
          @change="emitChange"
        />
        <a-button
          v-if="groups.length > 1 && group.sourceHandle !== 'out_c'"
          type="text"
          danger
          size="small"
          :disabled="disabled"
          @click="removeGroup(gIdx)"
        >
          <DeleteOutlined />
        </a-button>
      </div>

      <div v-if="group.rules?.length" class="condition-rules">
        <div class="condition-relation-row">
          <span class="relation-label">组内关系</span>
          <a-radio-group v-model:value="group.relation" size="small" :disabled="disabled" @change="emitChange">
            <a-radio-button value="and">且 (AND)</a-radio-button>
            <a-radio-button value="or">或 (OR)</a-radio-button>
          </a-radio-group>
        </div>
        <div v-for="(rule, rIdx) in group.rules" :key="rule.id" class="condition-rule-row">
          <VariablePickerInput
            v-model="rule.variable"
            placeholder="{{query}}"
            :disabled="disabled"
            @change="emitChange"
          />
          <a-select
            v-model:value="rule.operator"
            :disabled="disabled"
            style="width: 110px"
            @change="emitChange"
          >
            <a-select-option v-for="op in CONDITION_OPERATORS" :key="op.value" :value="op.value">
              {{ op.label }}
            </a-select-option>
          </a-select>
          <a-input
            v-if="!['empty', 'not_empty'].includes(rule.operator)"
            v-model:value="rule.value"
            placeholder="比较值"
            :disabled="disabled"
            @change="emitChange"
          />
          <a-button type="text" danger size="small" :disabled="disabled" @click="removeRule(gIdx, rIdx)">
            <DeleteOutlined />
          </a-button>
        </div>
        <a-button type="dashed" block size="small" class="param-add-btn" :disabled="disabled" @click="addRule(gIdx)">
          <PlusOutlined /> 添加条件
        </a-button>
      </div>
      <div v-else class="condition-else-hint">
        否则分支：当上方条件均未命中时，走「{{ handleLabel('out_c') }}」出口连线
      </div>
    </div>

    <a-button
      v-if="groups.length < 3"
      type="dashed"
      block
      size="small"
      class="param-add-btn"
      :disabled="disabled"
      @click="addGroup"
    >
      <PlusOutlined /> 添加条件组（最多 3 组，对应上/下/右出口）
    </a-button>
    <div class="condition-tip">
      在画布上从条件节点<strong>上/下/右</strong>出口拖线到目标节点；组顺序与出口一一对应。
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { CONDITION_OPERATORS } from '../nodeConfigMeta'
import { createConditionId } from '../nodeMeta'
import VariablePickerInput from './VariablePickerInput.vue'

const HANDLE_LABELS = {
  out_a: '上分支',
  out_b: '下分支',
  out_c: '右分支(否则)',
}

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change'])

const groups = computed({
  get: () => props.modelValue || [],
  set: v => {
    emit('update:modelValue', v)
    emit('change', v)
  },
})

function handleLabel(handle) {
  return HANDLE_LABELS[handle] || handle
}

function emitChange() {
  emit('change', groups.value)
}

function nextHandle() {
  const used = new Set(groups.value.map(g => g.sourceHandle))
  for (const h of ['out_a', 'out_b', 'out_c']) {
    if (!used.has(h)) return h
  }
  return 'out_c'
}

function addGroup() {
  if (groups.value.length >= 3) return
  const list = [...groups.value]
  list.splice(list.length - 1, 0, {
    id: createConditionId(),
    label: '否则如果',
    relation: 'and',
    sourceHandle: nextHandle(),
    rules: [{ id: createConditionId(), variable: '{{query}}', operator: 'contains', value: '' }],
  })
  groups.value = list
}

function removeGroup(idx) {
  const g = groups.value[idx]
  if (g?.sourceHandle === 'out_c') return
  const list = [...groups.value]
  list.splice(idx, 1)
  groups.value = list
}

function addRule(gIdx) {
  const list = [...groups.value]
  if (!list[gIdx].rules) list[gIdx].rules = []
  list[gIdx].rules.push({
    id: createConditionId(),
    variable: '{{query}}',
    operator: 'contains',
    value: '',
  })
  groups.value = list
}

function removeRule(gIdx, rIdx) {
  const list = [...groups.value]
  list[gIdx].rules.splice(rIdx, 1)
  groups.value = list
}
</script>

<style scoped>
.condition-group-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.condition-group-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  background: #f8fafc;
}
.condition-group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.condition-group-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}
.handle-out_a { background: #fef3c7; color: #b45309; }
.handle-out_b { background: #dbeafe; color: #1d4ed8; }
.handle-out_c { background: #f3e8ff; color: #7c3aed; }
.condition-group-title-input {
  flex: 1;
}
.condition-relation-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.relation-label {
  font-size: 12px;
  color: #64748b;
}
.condition-rule-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.condition-rule-row .variable-picker-input {
  flex: 1;
  min-width: 120px;
}
.condition-else-hint {
  font-size: 12px;
  color: #64748b;
  padding: 8px;
  background: #fff;
  border-radius: 6px;
  border: 1px dashed #cbd5e1;
}
.condition-tip {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}
.param-add-btn {
  margin-top: 8px;
  margin-bottom: 16px;
}
</style>
