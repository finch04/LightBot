<template>
  <div class="model-layout" :class="{ 'model-layout--designer': designerOpen }">
    <!-- 左侧分类：配置表单时隐藏，让设计器铺满 -->
    <aside v-show="!designerOpen" class="category-rail">
      <div class="category-rail__header">
        <span>分类</span>
        <button type="button" class="btn-icon" title="新建分类" @click="openCategoryDialog()">
          <PlusOutlined />
        </button>
      </div>
      <button
        type="button"
        :class="['category-item', { active: activeCategoryId === 'all' }]"
        @click="activeCategoryId = 'all'"
      >
        <span>全部</span>
        <span class="category-count">{{ models.length }}</span>
      </button>
      <button
        v-for="cat in categories"
        :key="cat.id"
        type="button"
        :class="['category-item', { active: activeCategoryId === cat.id }]"
        @click="activeCategoryId = cat.id"
      >
        <span class="category-name">{{ cat.name }}</span>
        <span class="category-count">{{ countByCategory(cat.id) }}</span>
        <a-dropdown :trigger="['click']" @click.stop>
          <EllipsisOutlined class="category-more" @click.stop />
          <template #overlay>
            <a-menu @click="({ key }) => handleCategoryMenu(key, cat)">
              <a-menu-item key="edit">重命名</a-menu-item>
              <a-menu-item key="delete" class="menu-danger">删除</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </button>
    </aside>

    <!-- 右侧模型列表 / 设计器 -->
    <div class="model-main">
      <template v-if="!designerOpen">
        <div class="panel-toolbar">
          <a-input
            v-model:value="searchText"
            placeholder="搜索数据模型..."
            allow-clear
            style="width: 240px"
          >
            <template #prefix><SearchOutlined /></template>
          </a-input>
          <button type="button" class="lb-btn lb-btn--primary" @click="openModelCreate">
            <PlusOutlined /> 新建数据模型
          </button>
        </div>

        <div v-if="filteredModels.length" class="card-grid">
          <article v-for="item in filteredModels" :key="item.id" class="model-card">
            <div class="model-card__top">
              <div class="model-card__info">
                <h3>{{ item.name }}</h3>
                <div class="model-card__sub">
                  <span class="model-card__cat">{{ categoryName(item.categoryId) }}</span>
                  <code v-if="item.tableName" class="model-card__table" :title="item.tableName">{{ item.tableName }}</code>
                </div>
              </div>
            </div>
            <p class="model-card__desc">{{ item.description || '暂无描述' }}</p>
            <div class="model-card__meta">
              <span>{{ item.schema?.fields?.length || 0 }} 个字段</span>
              <span>{{ (item.schema?.uniqueKeys?.length || 0) + (item.schema?.indexes?.length || 0) }} 条约束/索引</span>
              <span>{{ item.updateTime }}</span>
            </div>
            <div class="model-card__footer">
              <button type="button" class="lb-btn lb-btn--primary lb-btn--sm" @click="openDesigner(item)">
                配置表单
              </button>
              <button type="button" class="btn-link" @click="openModelEdit(item)">编辑信息</button>
              <button type="button" class="btn-link" @click="openAskEnhance(item)">问数增强</button>
              <button type="button" class="btn-link btn-link--danger" @click="removeModel(item)">删除</button>
            </div>
          </article>
        </div>
        <LbEmptyState
          v-else
          :icon="InboxOutlined"
          :title="searchText ? '没有匹配的数据模型' : '该分类下还没有数据模型'"
          :desc="searchText ? '试试其他关键词' : '创建一个模型后即可配置表单与索引检索'"
        />
      </template>

      <!-- 表单设计器视图：覆盖整页内容区 -->
      <div v-else class="designer-view">
        <div class="designer-header">
          <button type="button" class="page-back-icon" title="返回" @click="confirmCancelDesigner">
            <ArrowLeftOutlined />
          </button>
          <div class="designer-title">
            <h3>{{ editingModel?.name || '数据模型' }}</h3>
            <span>{{ designerTab === 'form' ? '拖拽左侧字段到画布，配置数据表单结构' : '配置有序搜索条件、唯一约束与普通索引' }}</span>
          </div>
          <div class="designer-tabs" role="tablist" aria-label="配置视图切换">
            <button
              type="button"
              role="tab"
              :aria-selected="designerTab === 'form'"
              :class="['designer-tab', { active: designerTab === 'form' }]"
              @click="switchDesignerTab('form')"
            >表单结构</button>
            <button
              type="button"
              role="tab"
              :aria-selected="designerTab === 'constraints'"
              :class="['designer-tab', { active: designerTab === 'constraints' }]"
              @click="switchDesignerTab('constraints')"
            >索引与检索</button>
          </div>
          <div class="designer-header__actions">
            <button
              v-if="designerTab === 'form'"
              type="button"
              class="btn-ai-assist"
              :disabled="suggestingKeys"
              @click="suggestEmptyFieldKeys"
            >
              <ThunderboltOutlined :spin="suggestingKeys" />
              {{ suggestingKeys ? '补全中…' : '补全字段英文名' }}
            </button>
            <button type="button" class="lb-btn" @click="confirmCancelDesigner">取消</button>
            <button type="button" class="lb-btn lb-btn--primary" @click="confirmSaveDesigner">
              <SaveOutlined /> 保存
            </button>
          </div>
        </div>
        <DataModelFormDesigner
          v-show="designerTab === 'form'"
          ref="designerRef"
          :model-value="editingModel?.schema || { fields: [] }"
        />
        <DataModelConstraintConfig
          v-show="designerTab === 'constraints'"
          ref="constraintRef"
          :fields="constraintFields"
          :model-value="constraintDraft"
        />
      </div>
    </div>

    <!-- 分类弹窗 -->
    <a-modal
      v-model:open="categoryDialogVisible"
      :title="categoryForm.id ? '重命名分类' : '新建分类'"
      :width="400"
      @ok="saveCategory"
      @cancel="categoryDialogVisible = false"
    >
      <a-form :label-col="{ flex: '0 0 80px' }">
        <a-form-item label="名称" required>
          <a-input v-model:value="categoryForm.name" placeholder="如：业务主数据" :maxlength="20" show-count />
        </a-form-item>
      </a-form>
    </a-modal>

    <AskEnhanceDrawer v-model:open="askDrawerOpen" :model="askDrawerModel" />

    <!-- 模型信息弹窗 -->
    <a-modal
      v-model:open="modelDialogVisible"
      :title="modelForm.id ? '编辑数据模型' : '新建数据模型'"
      :width="520"
      @ok="saveModelInfo"
      @cancel="modelDialogVisible = false"
    >
      <a-form :label-col="{ flex: '0 0 100px' }">
        <a-form-item label="名称" required>
          <a-input v-model:value="modelForm.name" placeholder="如：客户档案" :maxlength="40" show-count />
        </a-form-item>
        <a-form-item label="分类" required>
          <a-select v-model:value="modelForm.categoryId" style="width: 100%" placeholder="选择分类">
            <a-select-option v-for="cat in categories" :key="cat.id" :value="cat.id">
              {{ cat.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="数据库表名" required>
          <a-input
            :value="modelForm.tableNameSuffix"
            :placeholder="modelForm.id ? '' : '如：customer'"
            :maxlength="48"
            :disabled="!!modelForm.id"
            addon-before="sjc_data_"
            @update:value="onTableSuffixInput"
          />
          <div class="table-name-hint">
            完整表名：{{ TABLE_NAME_PREFIX }}{{ modelForm.tableNameSuffix || 'xxx' }}
            <span v-if="modelForm.id">（创建后不可修改）</span>
          </div>
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="modelForm.description" :rows="3" placeholder="可选" :maxlength="200" show-count />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import {
  ArrowLeftOutlined, PlusOutlined, SearchOutlined, EllipsisOutlined, InboxOutlined, SaveOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import DataModelFormDesigner from '../../components/data-center/DataModelFormDesigner.vue'
import DataModelConstraintConfig from '../../components/data-center/DataModelConstraintConfig.vue'
import LbEmptyState from '../../components/common/LbEmptyState.vue'
import AskEnhanceDrawer from './AskEnhanceDrawer.vue'
import {
  listDataModelCategories,
  createDataModelCategory,
  updateDataModelCategory,
  deleteDataModelCategory,
  listDataModels,
  createDataModel,
  updateDataModel,
  updateDataModelSchema,
  suggestDataModelFieldKeys,
  deleteDataModel,
} from '../../api/dataCenter'

const emit = defineEmits(['update:designerOpen'])

const activeCategoryId = ref('all')
const searchText = ref('')
const loading = ref(false)
const designerOpen = ref(false)
const designerTab = ref('form')
const designerRef = ref(null)
const constraintRef = ref(null)
const suggestingKeys = ref(false)
const editingModel = ref(null)
const constraintFields = ref([])
const constraintDraft = ref({
  fuzzySearchFields: [],
  searchConditions: [],
  uniqueKeys: [],
  indexes: [],
})

const categories = ref([])
const models = ref([])

/** 数据模型物理表名前缀（不可编辑） */
const TABLE_NAME_PREFIX = 'sjc_data_'
const TABLE_SUFFIX_REG = /^[a-z][a-z0-9_]*$/

const categoryDialogVisible = ref(false)
const categoryForm = reactive({ id: null, name: '' })
const modelDialogVisible = ref(false)
const modelForm = reactive({
  id: null,
  name: '',
  categoryId: undefined,
  tableNameSuffix: '',
  description: '',
})
const askDrawerOpen = ref(false)
const askDrawerModel = ref(null)

function openAskEnhance(item) {
  askDrawerModel.value = item
  askDrawerOpen.value = true
}

function setDesignerOpen(open) {
  designerOpen.value = open
  emit('update:designerOpen', open)
}

function emptyConstraintDraft() {
  return { fuzzySearchFields: [], searchConditions: [], uniqueKeys: [], indexes: [] }
}

function formatTime(v) {
  if (!v) return ''
  return String(v).replace('T', ' ').slice(0, 19)
}

function emptySchema() {
  return {
    fields: [],
    fuzzySearchFields: [],
    searchConditions: [],
    uniqueKeys: [],
    indexes: [],
  }
}

function normalizeModel(m) {
  return {
    ...m,
    id: m.id != null ? String(m.id) : m.id,
    categoryId: m.categoryId != null ? String(m.categoryId) : m.categoryId,
    schema: m.schema || emptySchema(),
    updateTime: formatTime(m.updateTime),
  }
}

async function loadAll() {
  loading.value = true
  try {
    const [catsRes, listRes] = await Promise.all([
      listDataModelCategories(),
      listDataModels(),
    ])
    categories.value = (catsRes?.data || []).map((c) => ({
      ...c,
      id: c.id != null ? String(c.id) : c.id,
    }))
    models.value = (listRes?.data || []).map(normalizeModel)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadAll()
})

function loadConstraintDraft(schema) {
  constraintDraft.value = {
    fuzzySearchFields: Array.isArray(schema?.fuzzySearchFields) ? [...schema.fuzzySearchFields] : [],
    searchConditions: Array.isArray(schema?.searchConditions) ? [...schema.searchConditions] : [],
    uniqueKeys: Array.isArray(schema?.uniqueKeys)
      ? schema.uniqueKeys.map((r) => ({ id: r.id, fields: [...(r.fields || [])] }))
      : [],
    indexes: Array.isArray(schema?.indexes)
      ? schema.indexes.map((r) => ({ id: r.id, fields: [...(r.fields || [])] }))
      : [],
  }
}

function syncConstraintFields() {
  constraintFields.value = designerRef.value?.getFields?.() || editingModel.value?.schema?.fields || []
}

function switchDesignerTab(tab) {
  if (tab === 'constraints') syncConstraintFields()
  designerTab.value = tab
}

function parseTableSuffix(tableName) {
  if (!tableName || typeof tableName !== 'string') return ''
  if (tableName.startsWith(TABLE_NAME_PREFIX)) {
    return tableName.slice(TABLE_NAME_PREFIX.length)
  }
  return tableName
}

function onTableSuffixInput(value) {
  const raw = String(value ?? '')
  modelForm.tableNameSuffix = raw
    .replace(new RegExp(`^${TABLE_NAME_PREFIX}`), '')
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, '')
    .slice(0, 48)
}

function countByCategory(categoryId) {
  return models.value.filter((m) => m.categoryId === categoryId).length
}

function categoryName(categoryId) {
  return categories.value.find((c) => c.id === categoryId)?.name || '未分类'
}

const filteredModels = computed(() => {
  let result = models.value
  if (activeCategoryId.value !== 'all') {
    result = result.filter((m) => m.categoryId === activeCategoryId.value)
  }
  const kw = searchText.value.trim().toLowerCase()
  if (kw) {
    result = result.filter((m) =>
      m.name.toLowerCase().includes(kw) || (m.description || '').toLowerCase().includes(kw))
  }
  return result
})

function openCategoryDialog(cat) {
  categoryForm.id = cat?.id || null
  categoryForm.name = cat?.name || ''
  categoryDialogVisible.value = true
}

function handleCategoryMenu(key, cat) {
  if (key === 'edit') openCategoryDialog(cat)
  if (key === 'delete') {
    Modal.confirm({
      title: '删除分类？',
      content: '仅当分类下没有数据模型时才能删除。',
      okType: 'danger',
      async onOk() {
        await deleteDataModelCategory(cat.id)
        if (activeCategoryId.value === cat.id) activeCategoryId.value = 'all'
        message.success('已删除分类')
        await loadAll()
      },
    })
  }
}

async function saveCategory() {
  if (!categoryForm.name.trim()) {
    message.warning('请输入分类名称')
    return
  }
  const payload = { name: categoryForm.name.trim() }
  if (categoryForm.id) {
    await updateDataModelCategory(categoryForm.id, payload)
  } else {
    await createDataModelCategory(payload)
  }
  categoryDialogVisible.value = false
  message.success('分类已保存')
  await loadAll()
}

function openModelCreate() {
  modelForm.id = null
  modelForm.name = ''
  modelForm.categoryId = activeCategoryId.value === 'all'
    ? categories.value[0]?.id
    : activeCategoryId.value
  modelForm.tableNameSuffix = ''
  modelForm.description = ''
  modelDialogVisible.value = true
}

function openModelEdit(item) {
  modelForm.id = item.id
  modelForm.name = item.name
  modelForm.categoryId = item.categoryId
  modelForm.tableNameSuffix = parseTableSuffix(item.tableName)
  modelForm.description = item.description || ''
  modelDialogVisible.value = true
}

async function saveModelInfo() {
  if (!modelForm.name.trim()) {
    message.warning('请输入名称')
    return
  }
  if (!modelForm.categoryId) {
    message.warning('请选择分类')
    return
  }
  if (modelForm.id) {
    await updateDataModel(modelForm.id, {
      name: modelForm.name.trim(),
      categoryId: modelForm.categoryId,
      description: modelForm.description,
    })
    modelDialogVisible.value = false
    message.success('模型信息已保存')
    await loadAll()
    return
  }
  const suffix = (modelForm.tableNameSuffix || '').trim()
  if (!suffix) {
    message.warning('请填写数据库表名')
    return
  }
  if (!TABLE_SUFFIX_REG.test(suffix)) {
    message.warning('表名后缀需以小写字母开头，仅含小写字母、数字、下划线')
    return
  }
  const createdRes = await createDataModel({
    name: modelForm.name.trim(),
    categoryId: modelForm.categoryId,
    tableNameSuffix: suffix,
    description: modelForm.description,
  })
  const created = createdRes?.data
  modelDialogVisible.value = false
  message.success('已创建，可开始配置表单')
  await loadAll()
  const item = models.value.find((m) => m.id === String(created?.id)) || normalizeModel(created)
  openDesigner(item)
}

function removeModel(item) {
  Modal.confirm({
    title: '删除数据模型？',
    content: '将删除模型元数据及其物理数据表，此操作不可恢复。',
    okType: 'danger',
    async onOk() {
      await deleteDataModel(item.id)
      message.success('已删除')
      await loadAll()
    },
  })
}

function openDesigner(item) {
  editingModel.value = item
  designerTab.value = 'form'
  loadConstraintDraft(item?.schema)
  constraintFields.value = item?.schema?.fields || []
  setDesignerOpen(true)
  nextTick(() => syncConstraintFields())
}

function closeDesigner() {
  editingModel.value = null
  designerTab.value = 'form'
  constraintDraft.value = emptyConstraintDraft()
  constraintFields.value = []
  setDesignerOpen(false)
}

function pruneConstraints(constraints, fields) {
  const valid = new Set((fields || []).map((f) => f.key).filter(Boolean))
  // 系统时间字段允许出现在搜索条件中
  valid.add('createTime')
  valid.add('updateTime')
  const fuzzyValid = new Set(
    (fields || [])
      .filter((f) => f.key && (f.type === 'input' || f.type === 'textarea'))
      .map((f) => f.key),
  )
  return {
    fuzzySearchFields: (constraints.fuzzySearchFields || []).filter((k) => fuzzyValid.has(k)),
    searchConditions: (constraints.searchConditions || []).filter((k) => valid.has(k)),
    uniqueKeys: (constraints.uniqueKeys || [])
      .map((r) => ({ id: r.id, fields: (r.fields || []).filter((k) => valid.has(k)) }))
      .filter((r) => r.fields.length > 0),
    indexes: (constraints.indexes || [])
      .map((r) => ({ id: r.id, fields: (r.fields || []).filter((k) => valid.has(k)) }))
      .filter((r) => r.fields.length > 0),
  }
}

function buildMergedSchema() {
  const fieldPart = designerRef.value?.getSchema?.() || { fields: [] }
  const fields = fieldPart.fields || []
  constraintFields.value = fields
  const raw = constraintRef.value?.getConfig?.() || constraintDraft.value
  const constraints = pruneConstraints(raw, fields)
  return {
    fields,
    fuzzySearchFields: constraints.fuzzySearchFields,
    searchConditions: constraints.searchConditions,
    uniqueKeys: constraints.uniqueKeys,
    indexes: constraints.indexes,
  }
}

function confirmSaveDesigner() {
  const fieldErr = designerRef.value?.validate?.()
  if (fieldErr) {
    message.warning(fieldErr)
    if (designerTab.value !== 'form') switchDesignerTab('form')
    return
  }
  Modal.confirm({
    title: '确认保存？',
    content: '保存后将同步物理表结构与索引，并返回列表。',
    okText: '保存',
    cancelText: '再看看',
    async onOk() {
      const err = designerRef.value?.validate?.()
      if (err) {
        message.warning(err)
        return Promise.reject(new Error(err))
      }
      syncConstraintFields()
      const schema = buildMergedSchema()
      if (!editingModel.value?.id) return
      await updateDataModelSchema(editingModel.value.id, schema)
      message.success(
        `已保存（${schema.fields.length} 个字段，`
        + `${schema.uniqueKeys.length} 条唯一约束，`
        + `${schema.indexes.length} 条索引）`,
      )
      closeDesigner()
      await loadAll()
    },
  })
}

function confirmCancelDesigner() {
  Modal.confirm({
    title: '确认取消？',
    content: '未保存的修改将丢失，确定返回列表吗？',
    okText: '确定取消',
    okType: 'danger',
    cancelText: '继续编辑',
    onOk() {
      closeDesigner()
    },
  })
}

/**
 * AI 补全英文名为空的字段；已填写的英文名保持不变
 */
async function suggestEmptyFieldKeys() {
  if (designerTab.value !== 'form') {
    switchDesignerTab('form')
    await nextTick()
  }
  const designer = designerRef.value
  if (!designer?.collectEmptyKeyTargets) return
  const { targets, occupiedKeys } = designer.collectEmptyKeyTargets()
  if (!targets.length) {
    message.info('没有需要补全的字段（英文名均为空才可补全）')
    return
  }
  const missingLabel = targets.find((f) => !String(f.label || '').trim())
  if (missingLabel) {
    message.warning('请先填写字段中文名，再补全英文名')
    return
  }
  suggestingKeys.value = true
  try {
    const res = await suggestDataModelFieldKeys({
      names: targets.map((f) => String(f.label).trim()),
      occupiedKeys,
    })
    const keys = res?.data?.keys || []
    const filled = designer.applySuggestedKeys(targets, keys)
    if (filled > 0) {
      message.success(`已补全 ${filled} 个字段英文名`)
    } else {
      message.warning('未能生成有效英文名，请稍后重试或手动填写')
    }
  } catch (e) {
    // request 拦截器通常已提示；此处兜底
    if (!e?.__handled) {
      message.error(e?.message || '补全失败')
    }
  } finally {
    suggestingKeys.value = false
  }
}
</script>

<style scoped>
.model-layout {
  display: flex;
  gap: 16px;
  height: 100%;
  min-height: 0;
}
.model-layout--designer {
  gap: 0;
}
.category-rail {
  width: 260px;
  flex-shrink: 0;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 16px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}
.category-rail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-mute);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.category-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-left: 2px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--color-ink);
  font-size: 13px;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}
.category-item:hover {
  background: var(--color-canvas-soft-2);
}
.category-item.active {
  background: var(--color-canvas-soft-2);
  border-color: var(--color-hairline);
  border-left-color: var(--color-ink);
  font-weight: 600;
}
.category-item:active {
  transform: scale(0.98);
}
.category-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.category-count {
  font-size: 11px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 0 6px;
  border-radius: 999px;
  font-variant-numeric: tabular-nums;
}
.category-more {
  opacity: 0;
  color: var(--color-mute);
  font-size: 14px;
}
.category-item:hover .category-more,
.category-item.active .category-more {
  opacity: 1;
}
.btn-icon {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-mute);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.btn-icon:hover {
  background: var(--color-canvas-soft-2);
  color: var(--color-ink);
}
.model-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.panel-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  flex-shrink: 0;
}
.panel-toolbar .lb-btn--primary {
  margin-left: auto;
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  overflow-y: auto;
  padding-bottom: 8px;
}
.model-card {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.model-card:hover {
  border-color: var(--color-link);
  box-shadow: var(--shadow-3);
}
.model-card__top {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.model-card__info {
  min-width: 0;
  width: 100%;
}
.model-card__info h3 {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
}
.model-card__sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}
.model-card__cat {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 1px 8px;
  border-radius: 999px;
}
.model-card__table {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  font-size: 11px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  color: var(--color-mute);
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.model-card__desc {
  margin: 0;
  font-size: 13px;
  color: var(--color-body);
  line-height: 1.5;
  min-height: 40px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.model-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  font-size: 12px;
  color: var(--color-mute);
  font-variant-numeric: tabular-nums;
}
.model-card__footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  padding-top: 10px;
  margin-top: auto;
  border-top: 1px solid var(--color-hairline);
}
.btn-link {
  border: none;
  background: transparent;
  color: var(--color-link);
  font-size: 13px;
  cursor: pointer;
  padding: 0;
  transition: opacity 0.15s ease;
}
.btn-link:hover {
  opacity: 0.8;
}
.btn-link--danger {
  color: var(--color-error);
  margin-left: auto;
}
.model-main > .lb-empty {
  flex: 1;
}
.designer-view {
  flex: 1;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: hidden;
}
.designer-header {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  flex-wrap: wrap;
}
.designer-title {
  min-width: 0;
}
.designer-title h3 {
  margin: 0 0 2px;
  font-size: 20px;
  font-weight: 600;
  color: var(--color-ink);
  line-height: 1.3;
}
.designer-title span {
  font-size: 12px;
  color: var(--color-mute);
}
.designer-tabs {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  margin-left: auto;
  border-radius: 10px;
  border: 1px solid var(--color-hairline-strong);
  background: var(--color-canvas-soft);
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.03);
}
.designer-tab {
  border: 1px solid transparent;
  background: transparent;
  color: var(--color-mute);
  font-size: 14px;
  font-weight: 600;
  padding: 8px 18px;
  min-width: 104px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}
.designer-tab:hover {
  color: var(--color-ink);
  background: var(--color-canvas);
}
.designer-tab.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
}
.designer-tab.active:hover {
  color: var(--color-on-primary);
  background: #27272a;
}
.designer-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
/* 辅助 AI 操作：轻量文本按钮，与取消/保存主操作区分 */
.btn-ai-assist {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-right: 4px;
  padding: 4px 10px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  font-size: 12px;
  color: var(--color-link);
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
  line-height: 1.5;
}
.btn-ai-assist:hover:not(:disabled) {
  background: var(--color-info-bg, rgba(0, 112, 243, 0.08));
  border-color: var(--color-link);
}
.btn-ai-assist:disabled {
  color: var(--color-mute);
  cursor: not-allowed;
}
.designer-view :deep(.form-designer),
.designer-view :deep(.constraint-config) {
  flex: 1 1 auto;
  min-height: 0;
  height: 100%;
}
:deep(.menu-danger) {
  color: var(--color-error) !important;
}
.table-name-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.4;
  font-family: 'SFMono-Regular', Consolas, monospace;
}
</style>
