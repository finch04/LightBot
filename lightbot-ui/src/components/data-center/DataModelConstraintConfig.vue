<template>
  <div class="constraint-config">
    <!-- 搜索条件：模糊搜索 + 筛选项（紧凑双行） -->
    <section class="section">
      <div class="row-block">
        <div class="row-block__head">
          <div class="row-block__title">
            <h4>模糊搜索</h4>
            <span>关键词 OR 匹配；未配则不展示搜索框</span>
          </div>
          <a-select
            v-model:value="fuzzyAddKey"
            placeholder="添加字段"
            class="row-block__select"
            :options="fuzzyCandidateOptions"
            allow-clear
            @change="onAddFuzzyField"
          />
        </div>
        <div v-if="fuzzySearchFields.length" class="ordered-list">
          <div v-for="(key, idx) in fuzzySearchFields" :key="key" class="ordered-item">
            <span class="ordered-item__index">{{ idx + 1 }}.</span>
            <span class="ordered-item__label">{{ fieldLabel(key) }}</span>
            <span class="ordered-item__key">{{ key }}</span>
            <div class="ordered-item__actions">
              <button type="button" class="btn-link" :disabled="idx === 0" @click="moveFuzzy(idx, -1)">上移</button>
              <button
                type="button"
                class="btn-link"
                :disabled="idx === fuzzySearchFields.length - 1"
                @click="moveFuzzy(idx, 1)"
              >下移</button>
              <button type="button" class="btn-link btn-link--danger" @click="removeFuzzy(idx)">移除</button>
            </div>
          </div>
        </div>
        <div v-else class="section__empty">尚未配置</div>
      </div>

      <div class="row-block row-block--divided">
        <div class="row-block__head">
          <div class="row-block__title">
            <h4>筛选项</h4>
            <span>分字段筛选；未配则无高级筛选</span>
          </div>
          <a-select
            v-model:value="searchAddKey"
            placeholder="添加字段"
            class="row-block__select"
            :options="searchCandidateOptions"
            allow-clear
            @change="onAddSearchField"
          />
        </div>
        <div v-if="searchConditions.length" class="ordered-list">
          <div v-for="(key, idx) in searchConditions" :key="key" class="ordered-item">
            <span class="ordered-item__index">{{ idx + 1 }}.</span>
            <span class="ordered-item__label">{{ fieldLabel(key) }}</span>
            <span class="ordered-item__key">{{ key }}</span>
            <div class="ordered-item__actions">
              <button type="button" class="btn-link" :disabled="idx === 0" @click="moveSearch(idx, -1)">上移</button>
              <button
                type="button"
                class="btn-link"
                :disabled="idx === searchConditions.length - 1"
                @click="moveSearch(idx, 1)"
              >下移</button>
              <button type="button" class="btn-link btn-link--danger" @click="removeSearch(idx)">移除</button>
            </div>
          </div>
        </div>
        <div v-else class="section__empty">尚未配置</div>
      </div>
    </section>

    <!-- 唯一约束 -->
    <section class="section">
      <div class="row-block__head">
        <div class="row-block__title">
          <h4>唯一约束</h4>
          <span>添加字段新建；可继续追加组成联合唯一</span>
        </div>
        <a-select
          v-model:value="uniqueAddKey"
          placeholder="添加字段"
          class="row-block__select"
          :options="indexableFieldOptions"
          allow-clear
          @change="(key) => onAddRuleSeed('unique', key)"
        />
      </div>
      <div v-if="uniqueKeys.length" class="rule-list">
        <div v-for="(rule, ruleIdx) in uniqueKeys" :key="rule.id" class="rule-block">
          <div class="rule-block__header">
            <span class="rule-block__title">约束 {{ ruleIdx + 1 }}</span>
            <div class="rule-block__header-actions">
              <a-select
                :value="ruleAddKeys[rule.id]"
                placeholder="添加字段"
                class="rule-block__select"
                :options="ruleFieldOptions(rule)"
                allow-clear
                @update:value="(v) => (ruleAddKeys[rule.id] = v)"
                @change="(key) => onAddFieldToRule('unique', rule, key)"
              />
              <button type="button" class="btn-link btn-link--danger" @click="removeRule('unique', rule.id)">
                删除
              </button>
            </div>
          </div>
          <div class="ordered-list">
            <div v-for="(key, idx) in rule.fields" :key="key" class="ordered-item">
              <span class="ordered-item__index">{{ idx + 1 }}.</span>
              <span class="ordered-item__label">{{ fieldLabel(key) }}</span>
              <span class="ordered-item__key">{{ key }}</span>
              <div class="ordered-item__actions">
                <button
                  type="button"
                  class="btn-link"
                  :disabled="idx === 0"
                  @click="moveFieldInRule('unique', rule, idx, -1)"
                >上移</button>
                <button
                  type="button"
                  class="btn-link"
                  :disabled="idx === rule.fields.length - 1"
                  @click="moveFieldInRule('unique', rule, idx, 1)"
                >下移</button>
                <button
                  type="button"
                  class="btn-link btn-link--danger"
                  @click="removeFieldFromRule('unique', rule, idx)"
                >移除</button>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="section__empty">尚未配置</div>
    </section>

    <!-- 普通索引 -->
    <section class="section">
      <div class="row-block__head">
        <div class="row-block__title">
          <h4>普通索引</h4>
          <span>添加字段新建；顺序影响最左匹配</span>
        </div>
        <a-select
          v-model:value="indexAddKey"
          placeholder="添加字段"
          class="row-block__select"
          :options="indexableFieldOptions"
          allow-clear
          @change="(key) => onAddRuleSeed('index', key)"
        />
      </div>
      <div v-if="indexes.length" class="rule-list">
        <div v-for="(rule, ruleIdx) in indexes" :key="rule.id" class="rule-block">
          <div class="rule-block__header">
            <div class="rule-block__title-wrap">
              <span class="rule-block__title">索引 {{ ruleIdx + 1 }}</span>
              <span v-if="isCoveredByUnique(rule.fields)" class="rule-block__tip">与某条唯一约束序列相同</span>
            </div>
            <div class="rule-block__header-actions">
              <a-select
                :value="ruleAddKeys[rule.id]"
                placeholder="添加字段"
                class="rule-block__select"
                :options="ruleFieldOptions(rule)"
                allow-clear
                @update:value="(v) => (ruleAddKeys[rule.id] = v)"
                @change="(key) => onAddFieldToRule('index', rule, key)"
              />
              <button type="button" class="btn-link btn-link--danger" @click="removeRule('index', rule.id)">
                删除
              </button>
            </div>
          </div>
          <div class="ordered-list">
            <div v-for="(key, idx) in rule.fields" :key="key" class="ordered-item">
              <span class="ordered-item__index">{{ idx + 1 }}.</span>
              <span class="ordered-item__label">{{ fieldLabel(key) }}</span>
              <span class="ordered-item__key">{{ key }}</span>
              <div class="ordered-item__actions">
                <button
                  type="button"
                  class="btn-link"
                  :disabled="idx === 0"
                  @click="moveFieldInRule('index', rule, idx, -1)"
                >上移</button>
                <button
                  type="button"
                  class="btn-link"
                  :disabled="idx === rule.fields.length - 1"
                  @click="moveFieldInRule('index', rule, idx, 1)"
                >下移</button>
                <button
                  type="button"
                  class="btn-link btn-link--danger"
                  @click="removeFieldFromRule('index', rule, idx)"
                >移除</button>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="section__empty">尚未配置</div>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'

/** 不可作为筛选项 */
const NO_SEARCH_TYPES = new Set(['upload'])
/** 可参与模糊搜索的文本类字段 */
const FUZZY_TYPES = new Set(['input', 'textarea'])
/** 不可参与唯一 / 索引 */
const NO_INDEX_TYPES = new Set(['upload', 'checkbox'])

const props = defineProps({
  /** 当前模型字段快照：{ key, label, type, system? }[] */
  fields: { type: Array, default: () => [] },
  modelValue: {
    type: Object,
    default: () => ({
      fuzzySearchFields: [],
      searchConditions: [],
      uniqueKeys: [],
      indexes: [],
    }),
  },
})

const fuzzySearchFields = ref([])
const searchConditions = ref([])
const uniqueKeys = ref([])
const indexes = ref([])
const fuzzyAddKey = ref(undefined)
const searchAddKey = ref(undefined)
const uniqueAddKey = ref(undefined)
const indexAddKey = ref(undefined)
/** 各规则行内「添加字段」选中值 */
const ruleAddKeys = reactive({})

const fieldMap = computed(() => {
  const map = new Map()
  for (const f of props.fields || []) {
    if (f?.key) map.set(f.key, f)
  }
  return map
})

const fuzzyCandidateOptions = computed(() => {
  const selected = new Set(fuzzySearchFields.value)
  return (props.fields || [])
    .filter((f) => f.key && FUZZY_TYPES.has(f.type) && !selected.has(f.key))
    .map((f) => ({ value: f.key, label: `${f.label || f.key}（${f.key}）` }))
})

const searchCandidateOptions = computed(() => {
  const selected = new Set(searchConditions.value)
  return (props.fields || [])
    .filter((f) => f.key && !NO_SEARCH_TYPES.has(f.type) && !selected.has(f.key))
    .map((f) => ({ value: f.key, label: `${f.label || f.key}（${f.key}）` }))
})

const indexableFieldOptions = computed(() =>
  (props.fields || [])
    .filter((f) => f.key && !NO_INDEX_TYPES.has(f.type))
    .map((f) => ({ value: f.key, label: `${f.label || f.key}（${f.key}）` })),
)

function ruleFieldOptions(rule) {
  const selected = new Set(rule.fields || [])
  return indexableFieldOptions.value.filter((opt) => !selected.has(opt.value))
}

function fieldLabel(key) {
  return fieldMap.value.get(key)?.label || key
}

function seqKey(keys) {
  return (keys || []).join('\u0001')
}

function isCoveredByUnique(fields) {
  const key = seqKey(fields)
  return uniqueKeys.value.some((r) => seqKey(r.fields) === key)
}

function genRuleId(prefix) {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
}

function targetList(mode) {
  return mode === 'unique' ? uniqueKeys : indexes
}

function hydrateFromModel(value) {
  fuzzySearchFields.value = Array.isArray(value?.fuzzySearchFields)
    ? [...value.fuzzySearchFields]
    : []
  searchConditions.value = Array.isArray(value?.searchConditions)
    ? [...value.searchConditions]
    : []
  uniqueKeys.value = Array.isArray(value?.uniqueKeys)
    ? value.uniqueKeys.map((r) => ({
      id: r.id || genRuleId('uk'),
      fields: [...(r.fields || [])],
    }))
    : []
  indexes.value = Array.isArray(value?.indexes)
    ? value.indexes.map((r) => ({
      id: r.id || genRuleId('idx'),
      fields: [...(r.fields || [])],
    }))
    : []
}

watch(
  () => props.modelValue,
  (value) => hydrateFromModel(value),
  { immediate: true, deep: true },
)

/** 字段变更时剔除失效 key */
watch(
  () => props.fields,
  (list) => {
    const valid = new Set((list || []).map((f) => f.key).filter(Boolean))
    const fuzzyValid = new Set(
      (list || []).filter((f) => f.key && FUZZY_TYPES.has(f.type)).map((f) => f.key),
    )
    fuzzySearchFields.value = fuzzySearchFields.value.filter((k) => fuzzyValid.has(k))
    searchConditions.value = searchConditions.value.filter((k) => valid.has(k))
    uniqueKeys.value = uniqueKeys.value
      .map((r) => ({ ...r, fields: r.fields.filter((k) => valid.has(k)) }))
      .filter((r) => r.fields.length > 0)
    indexes.value = indexes.value
      .map((r) => ({ ...r, fields: r.fields.filter((k) => valid.has(k)) }))
      .filter((r) => r.fields.length > 0)
  },
  { deep: true },
)

function onAddFuzzyField(key) {
  if (!key) return
  if (!fuzzySearchFields.value.includes(key)) {
    fuzzySearchFields.value.push(key)
  }
  fuzzyAddKey.value = undefined
}

function moveFuzzy(index, delta) {
  const next = index + delta
  if (next < 0 || next >= fuzzySearchFields.value.length) return
  const list = [...fuzzySearchFields.value]
  const cur = list[index]
  list[index] = list[next]
  list[next] = cur
  fuzzySearchFields.value = list
}

function removeFuzzy(index) {
  fuzzySearchFields.value.splice(index, 1)
}

function onAddSearchField(key) {
  if (!key) return
  if (!searchConditions.value.includes(key)) {
    searchConditions.value.push(key)
  }
  searchAddKey.value = undefined
}

function moveSearch(index, delta) {
  const next = index + delta
  if (next < 0 || next >= searchConditions.value.length) return
  const list = [...searchConditions.value]
  const cur = list[index]
  list[index] = list[next]
  list[next] = cur
  searchConditions.value = list
}

function removeSearch(index) {
  searchConditions.value.splice(index, 1)
}

/** 顶部下拉：用所选字段新建一条约束/索引 */
function onAddRuleSeed(mode, key) {
  if (!key) return
  const list = targetList(mode)
  const fields = [key]
  const dup = list.value.some((r) => seqKey(r.fields) === seqKey(fields))
  if (dup) {
    message.warning(mode === 'unique' ? '已存在相同字段序列的唯一约束' : '已存在相同字段序列的索引')
  } else {
    list.value.push({
      id: genRuleId(mode === 'unique' ? 'uk' : 'idx'),
      fields,
    })
    if (mode === 'index' && isCoveredByUnique(fields)) {
      message.info('该字段序列与某条唯一约束相同，唯一约束已隐含索引')
    }
  }
  if (mode === 'unique') uniqueAddKey.value = undefined
  else indexAddKey.value = undefined
}

/** 规则内下拉：向当前约束/索引追加字段 */
function onAddFieldToRule(mode, rule, key) {
  if (!key || !rule) return
  if (rule.fields.includes(key)) {
    ruleAddKeys[rule.id] = undefined
    return
  }
  const nextFields = [...rule.fields, key]
  const list = targetList(mode)
  const dup = list.value.some((r) => r.id !== rule.id && seqKey(r.fields) === seqKey(nextFields))
  if (dup) {
    message.warning(mode === 'unique' ? '已存在相同字段序列的唯一约束' : '已存在相同字段序列的索引')
  } else {
    rule.fields.push(key)
    if (mode === 'index' && isCoveredByUnique(rule.fields)) {
      message.info('该字段序列与某条唯一约束相同，唯一约束已隐含索引')
    }
  }
  ruleAddKeys[rule.id] = undefined
}

function moveFieldInRule(mode, rule, index, delta) {
  const next = index + delta
  if (next < 0 || next >= rule.fields.length) return
  const list = [...rule.fields]
  const cur = list[index]
  list[index] = list[next]
  list[next] = cur
  const target = targetList(mode)
  const dup = target.value.some((r) => r.id !== rule.id && seqKey(r.fields) === seqKey(list))
  if (dup) {
    message.warning(mode === 'unique' ? '调整后与已有唯一约束字段序列重复' : '调整后与已有索引字段序列重复')
    return
  }
  rule.fields = list
}

function removeFieldFromRule(mode, rule, index) {
  rule.fields.splice(index, 1)
  if (rule.fields.length === 0) {
    removeRule(mode, rule.id)
  }
}

function removeRule(mode, id) {
  if (mode === 'unique') {
    uniqueKeys.value = uniqueKeys.value.filter((r) => r.id !== id)
  } else {
    indexes.value = indexes.value.filter((r) => r.id !== id)
  }
  delete ruleAddKeys[id]
}

/**
 * 按当前有效字段裁剪后返回配置
 * @returns {{ fuzzySearchFields: string[], searchConditions: string[], uniqueKeys: {id:string,fields:string[]}[], indexes: {id:string,fields:string[]}[] }}
 */
function getConfig() {
  const valid = new Set((props.fields || []).map((f) => f.key).filter(Boolean))
  const fuzzyValid = new Set(
    (props.fields || []).filter((f) => f.key && FUZZY_TYPES.has(f.type)).map((f) => f.key),
  )
  return {
    fuzzySearchFields: fuzzySearchFields.value.filter((k) => fuzzyValid.has(k)),
    searchConditions: searchConditions.value.filter((k) => valid.has(k)),
    uniqueKeys: uniqueKeys.value
      .map((r) => ({
        id: r.id,
        fields: r.fields.filter((k) => valid.has(k)),
      }))
      .filter((r) => r.fields.length > 0),
    indexes: indexes.value
      .map((r) => ({
        id: r.id,
        fields: r.fields.filter((k) => valid.has(k)),
      }))
      .filter((r) => r.fields.length > 0),
  }
}

defineExpose({ getConfig })
</script>

<style scoped>
.constraint-config {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 10px;
  padding-right: 4px;
}
.section {
  flex: 0 0 auto;
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  background: var(--color-canvas);
  padding: 10px 12px;
}
.row-block--divided {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--color-hairline);
}
.row-block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 32px;
}
.row-block__title {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px 10px;
  min-width: 0;
}
.row-block__title h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
  white-space: nowrap;
}
.row-block__title span {
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.3;
}
.row-block__select,
.rule-block__select {
  width: 168px;
  flex-shrink: 0;
}
.section__empty {
  margin-top: 6px;
  padding: 0;
  text-align: left;
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.4;
}
.ordered-list,
.rule-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}
.rule-list {
  gap: 8px;
}
.rule-block {
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  background: var(--color-canvas-soft);
  padding: 8px 10px;
}
.rule-block__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}
.rule-block__title-wrap {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px 10px;
  min-width: 0;
}
.rule-block__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-mute);
}
.rule-block__tip {
  font-size: 12px;
  color: var(--color-mute);
}
.rule-block__header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}
.ordered-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  background: var(--color-canvas);
}
.ordered-item__index {
  color: var(--color-mute);
  font-size: 12px;
  min-width: 18px;
}
.ordered-item__label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-ink);
}
.ordered-item__key {
  font-size: 12px;
  color: var(--color-mute);
  font-family: 'SFMono-Regular', Consolas, monospace;
}
.ordered-item__actions {
  margin-left: auto;
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}
.btn-link {
  border: none;
  background: transparent;
  color: var(--color-link);
  font-size: 12px;
  cursor: pointer;
  padding: 0 4px;
}
.btn-link:disabled {
  color: var(--color-mute);
  cursor: not-allowed;
  opacity: 0.5;
}
.btn-link--danger {
  color: var(--color-error);
}
</style>
