<template>
  <div class="condition-group-form">
    <div v-for="(group, gIdx) in groups" :key="group.id" class="condition-group-card">
      <div class="condition-group-header">
        <span class="condition-group-badge">条件 {{ gIdx + 1 }}</span>
        <a-input
          v-model:value="group.label"
          size="small"
          class="condition-group-title-input"
          placeholder="分支名称"
          :disabled="disabled"
          @change="emitChange"
        />
        <a-button
          v-if="groups.length > 1"
          type="text"
          danger
          size="small"
          :disabled="disabled"
          @click="removeGroup(gIdx)"
        >
          <DeleteOutlined />
        </a-button>
      </div>

      <div class="condition-rules">
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
    </div>

    <div class="condition-else-card">
      <span class="condition-group-badge else-badge">都未命中</span>
      <div class="condition-else-hint">
        上方条件按顺序匹配，均未命中时走画布右侧「都未命中」出口（须连线）。
      </div>
    </div>

    <a-button
      type="dashed"
      block
      size="small"
      class="param-add-btn"
      :disabled="disabled || groups.length >= MAX_GROUPS"
      @click="addGroup"
    >
      <PlusOutlined /> 添加条件组{{ groups.length >= MAX_GROUPS ? `（最多 ${MAX_GROUPS} 组）` : '' }}
    </a-button>
    <div class="condition-tip">
      在画布上从条件节点<strong>右侧各出口</strong>拖线到目标节点；组顺序即匹配优先级。
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { CONDITION_OPERATORS } from '../nodeConfigMeta'
import { createConditionId } from '../nodeMeta'
import VariablePickerInput from './VariablePickerInput.vue'

/** 软上限，防止节点过高；需要时可再调大 */
const MAX_GROUPS = 20

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change', 'remove-group'])

const groups = computed({
  get: () => props.modelValue || [],
  set: v => {
    emit('update:modelValue', v)
    emit('change', v)
  },
})

function emitChange() {
  emit('change', groups.value)
}

function addGroup() {
  if (groups.value.length >= MAX_GROUPS) return
  groups.value = [
    ...groups.value,
    {
      id: createConditionId(),
      label: '否则如果',
      relation: 'and',
      rules: [{ id: createConditionId(), variable: '{{query}}', operator: 'contains', value: '' }],
    },
  ]
}

function removeGroup(idx) {
  const g = groups.value[idx]
  if (!g || groups.value.length <= 1) return
  const list = [...groups.value]
  list.splice(idx, 1)
  groups.value = list
  emit('remove-group', g.id)
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
  if ((list[gIdx].rules || []).length <= 1) return
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
.condition-group-card,
.condition-else-card {
  border: 1px solid var(--color-border-slate);
  border-radius: 8px;
  padding: 12px;
  background: var(--color-canvas-soft);
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
  background: var(--color-warn-bg-deep);
  color: #b45309;
}
.else-badge {
  background: #f3e8ff;
  color: #7c3aed;
  display: inline-block;
  margin-bottom: 8px;
}
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
  color: var(--color-mute);
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
  color: var(--color-mute);
  line-height: 1.5;
}
.condition-tip {
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.5;
}
.param-add-btn {
  margin-top: 8px;
  margin-bottom: 8px;
}
</style>
