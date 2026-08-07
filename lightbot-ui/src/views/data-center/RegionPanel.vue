<template>
  <div class="region-layout">
    <!-- 顶栏 -->
    <header class="region-top">
      <div class="region-top__intro">
        <h3>国标行政区划</h3>
        <p>
          供问数「本级及下级」隔离展开；业务表与
          <code>callerContext.regionId</code> 使用 6 位区划编码。
        </p>
      </div>
      <div class="region-top__stats" v-if="stats.count > 0">
        <span class="region-stat">
          <em>{{ stats.provinces }}</em> 省
        </span>
        <span class="region-stat">
          <em>{{ stats.cities }}</em> 市
        </span>
        <span class="region-stat">
          <em>{{ stats.districts }}</em> 区县
        </span>
        <span class="region-stat region-stat--total">
          共 <em>{{ stats.count }}</em> 条
        </span>
      </div>
      <div class="region-top__actions">
        <span v-if="stats.count > 0" class="region-ready">已就绪</span>
        <button type="button" class="lb-btn lb-btn--sm" :disabled="busy" @click="onRefresh">
          <RefreshCw :size="14" />
          刷新
        </button>
        <button
          v-if="stats.count === 0"
          type="button"
          class="lb-btn lb-btn--sm lb-btn--primary"
          :disabled="busy"
          @click="onSeed"
        >
          <Download :size="14" />
          导入国标数据
        </button>
        <a-popconfirm
          v-else
          title="将清空并重新导入全部区划，确认？"
          ok-text="重导"
          cancel-text="取消"
          @confirm="onReseed"
        >
          <button type="button" class="lb-btn lb-btn--sm" :disabled="busy">
            <RotateCcw :size="14" />
            重新导入
          </button>
        </a-popconfirm>
      </div>
    </header>

    <!-- 空库 -->
    <div v-if="!bootLoading && stats.count === 0" class="region-empty-wrap">
      <LbEmptyState
        :icon="MapPinned"
        title="地区库尚未导入"
        desc="执行建表 SQL 后，点击下方按钮导入国标省市区（约 3400 条）。"
      >
        <template #action>
          <button type="button" class="lb-btn lb-btn--primary" :disabled="busy" @click="onSeed">
            导入国标数据
          </button>
        </template>
      </LbEmptyState>
    </div>

    <!-- 主区：左树 / 搜索 · 右详 -->
    <div v-else class="region-body">
      <aside class="region-rail">
        <div class="region-rail__search">
          <a-input
            v-model:value="keyword"
            allow-clear
            placeholder="搜索名称或编码，如 成都 / 510100"
            @pressEnter="onSearch"
          >
            <template #prefix><Search :size="14" class="region-search-icon" /></template>
          </a-input>
        </div>

        <div class="region-rail__scroll">
          <a-spin :spinning="loading">
            <!-- 搜索结果 -->
            <div v-if="searchMode" class="region-search-list">
              <div class="region-search-list__meta">
                找到 {{ searchRows.length }} 条
                <button type="button" class="btn-link" @click="clearSearch">返回树形</button>
              </div>
              <button
                v-for="row in searchRows"
                :key="row.code"
                type="button"
                :class="['region-hit', { active: selected?.code === row.code }]"
                @click="selectRegion(row)"
              >
                <span :class="['region-level', `region-level--${row.level}`]">
                  {{ levelLabel(row.level) }}
                </span>
                <span class="region-hit__name">{{ row.name }}</span>
                <code class="region-hit__code">{{ row.code }}</code>
              </button>
              <div v-if="!loading && searchRows.length === 0" class="region-rail__hint">
                无匹配结果，试试名称或完整编码
              </div>
            </div>

            <!-- 树 -->
            <div v-else class="region-tree-wrap">
              <a-tree
                v-if="treeData.length"
                v-model:expanded-keys="expandedKeys"
                v-model:selected-keys="selectedKeys"
                block-node
                :tree-data="treeData"
                :load-data="onLoadData"
                :field-names="{ title: 'title', key: 'key', children: 'children' }"
                @select="onTreeSelect"
              >
                <template #title="{ name, code, level }">
                  <span class="region-node">
                    <span :class="['region-level', `region-level--${level || 1}`]">
                      {{ levelLabel(level) }}
                    </span>
                    <span class="region-node__name">{{ name }}</span>
                    <code class="region-node__code">{{ code }}</code>
                  </span>
                </template>
              </a-tree>
              <div v-else-if="!loading" class="region-rail__hint">暂无省级数据</div>
            </div>
          </a-spin>
        </div>
      </aside>

      <section class="region-detail">
        <template v-if="selected">
          <div class="region-detail__head">
            <div class="region-detail__title-row">
              <span :class="['region-level', 'region-level--lg', `region-level--${selected.level}`]">
                {{ levelLabel(selected.level) }}
              </span>
              <h2>{{ selected.name }}</h2>
            </div>
            <nav v-if="pathNodes.length" class="region-crumb" aria-label="区划路径">
              <template v-for="(n, i) in pathNodes" :key="n.code">
                <button
                  v-if="i < pathNodes.length - 1"
                  type="button"
                  class="region-crumb__link"
                  @click="selectRegion(n)"
                >
                  {{ n.name }}
                </button>
                <span v-else class="region-crumb__current">{{ n.name }}</span>
                <span v-if="i < pathNodes.length - 1" class="region-crumb__sep">/</span>
              </template>
            </nav>
          </div>

          <div class="region-detail__code-card">
            <div class="region-detail__code-label">区划编码</div>
            <div class="region-detail__code-row">
              <code>{{ selected.code }}</code>
              <button type="button" class="lb-btn lb-btn--sm" @click="copyText(selected.code)">
                <Copy :size="14" />
                复制
              </button>
            </div>
            <p class="region-detail__hint">
              问数隔离示例：
              <code>{{ callerSnippet }}</code>
              <button type="button" class="btn-link" @click="copyText(callerSnippet)">复制片段</button>
            </p>
          </div>

          <div class="region-detail__meta">
            <div class="region-meta-item">
              <span class="region-meta-item__label">层级</span>
              <span>{{ levelLabel(selected.level) }}（level={{ selected.level }}）</span>
            </div>
            <div class="region-meta-item">
              <span class="region-meta-item__label">上级编码</span>
              <span>{{ selected.parentCode || '—' }}</span>
            </div>
            <div class="region-meta-item">
              <span class="region-meta-item__label">本级及下级</span>
              <span>
                <template v-if="descendantLoading">计算中…</template>
                <template v-else>{{ descendantCount }} 个编码</template>
              </span>
            </div>
          </div>

          <div class="region-detail__children">
            <div class="region-detail__section-head">
              <h4>直接下级</h4>
              <span v-if="childRows.length">{{ childRows.length }} 个</span>
            </div>
            <a-spin :spinning="childrenLoading">
              <div v-if="childRows.length" class="region-child-grid">
                <button
                  v-for="c in childRows"
                  :key="c.code"
                  type="button"
                  class="region-child"
                  @click="selectRegion(c)"
                >
                  <span class="region-child__name">{{ c.name }}</span>
                  <code>{{ c.code }}</code>
                </button>
              </div>
              <div v-else-if="!childrenLoading" class="region-rail__hint">
                {{ selected.level >= 3 ? '区县级无下级区划' : '暂无下级' }}
              </div>
            </a-spin>
          </div>
        </template>

        <div v-else class="region-welcome">
          <div class="region-welcome__hero">
            <div class="region-welcome__mark" aria-hidden="true">
              <MapPinned :size="28" />
            </div>
            <div>
              <h2>浏览国标区划</h2>
              <p>
                从左侧选择省 / 市 / 区，查看编码、路径与下级列表；可将编码复制到
                <code>callerContext.regionId</code> 做问数隔离。
              </p>
            </div>
          </div>

          <div class="region-welcome__dist">
            <div
              v-for="item in distCards"
              :key="item.key"
              class="region-dist"
            >
              <div class="region-dist__top">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
              <div class="region-dist__track">
                <i :style="{ width: item.pct + '%' }" :class="`region-dist__bar region-dist__bar--${item.key}`" />
              </div>
            </div>
          </div>

          <div class="region-welcome__steps">
            <div v-for="(step, i) in welcomeSteps" :key="step.title" class="region-step">
              <span class="region-step__n">{{ i + 1 }}</span>
              <div>
                <h4>{{ step.title }}</h4>
                <p>{{ step.desc }}</p>
              </div>
            </div>
          </div>

          <div v-if="quickProvinces.length" class="region-welcome__quick">
            <div class="region-welcome__quick-head">
              <h4>快速进入</h4>
              <span>点击省级直接打开详情</span>
            </div>
            <div class="region-quick-grid">
              <button
                v-for="p in quickProvinces"
                :key="p.code"
                type="button"
                class="region-quick"
                @click="selectRegion(p)"
              >
                <span>{{ p.name }}</span>
                <code>{{ p.code }}</code>
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { Copy, Download, MapPinned, RefreshCw, RotateCcw, Search } from 'lucide-vue-next'
import LbEmptyState from '../../components/common/LbEmptyState.vue'
import {
  getRegionStats,
  getRegionPath,
  listRegionChildren,
  listRegionDescendants,
  searchRegions,
  seedRegions,
  reseedRegions,
} from '../../api/region'

const stats = ref({ count: 0, provinces: 0, cities: 0, districts: 0 })
const busy = ref(false)
const bootLoading = ref(true)
const loading = ref(false)
const keyword = ref('')
const searchMode = ref(false)
const searchRows = ref([])
const treeData = ref([])
const expandedKeys = ref([])
const selectedKeys = ref([])
const selected = ref(null)
const pathNodes = ref([])
const childRows = ref([])
const childrenLoading = ref(false)
const descendantCount = ref(0)
const descendantLoading = ref(false)

const callerSnippet = computed(() => {
  if (!selected.value?.code) return ''
  return `"regionId": "${selected.value.code}"`
})

const distCards = computed(() => {
  const total = Math.max(Number(stats.value.count) || 1, 1)
  const rows = [
    { key: 'province', label: '省级', value: Number(stats.value.provinces) || 0 },
    { key: 'city', label: '地市', value: Number(stats.value.cities) || 0 },
    { key: 'district', label: '区县', value: Number(stats.value.districts) || 0 },
  ]
  return rows.map((r) => ({
    ...r,
    pct: Math.max(6, Math.round((r.value / total) * 100)),
  }))
})

const welcomeSteps = [
  { title: '选择或搜索', desc: '左侧展开树节点，或输入「成都」「510100」定位。' },
  { title: '核对编码', desc: '右侧展示 6 位国标码与行政区划路径。' },
  { title: '用于隔离', desc: '复制编码填入 callerContext.regionId，问数按本级及下级过滤。' },
]

const quickProvinces = computed(() =>
  (treeData.value || [])
    .map((n) => n.raw || { code: n.code, name: n.name, level: n.level, parentCode: n.parentCode })
    .filter((p) => p?.code)
    .slice(0, 12),
)

function levelLabel(level) {
  return ({ 1: '省', 2: '市', 3: '区' })[level] || '—'
}

function toTreeNode(item) {
  return {
    key: item.code,
    title: item.name,
    name: item.name,
    code: item.code,
    level: item.level,
    parentCode: item.parentCode,
    isLeaf: item.level >= 3,
    children: item.level >= 3 ? undefined : [],
    raw: item,
  }
}

async function loadStats() {
  try {
    const res = await getRegionStats()
    const d = res?.data || {}
    stats.value = {
      count: Number(d.count || 0),
      provinces: Number(d.provinces || 0),
      cities: Number(d.cities || 0),
      districts: Number(d.districts || 0),
    }
  } catch {
    stats.value = { count: 0, provinces: 0, cities: 0, districts: 0 }
  }
}

async function loadProvinces() {
  loading.value = true
  try {
    const res = await listRegionChildren()
    treeData.value = (res?.data || []).map(toTreeNode)
  } catch (e) {
    treeData.value = []
    message.error(e?.message || '加载省级失败')
  } finally {
    loading.value = false
  }
}

function onLoadData(treeNode) {
  return new Promise(async (resolve) => {
    try {
      const code = treeNode.key || treeNode.dataRef?.key
      const res = await listRegionChildren(code)
      const children = (res?.data || []).map(toTreeNode)
      const ref = treeNode.dataRef || treeNode
      ref.children = children.length ? children : undefined
      ref.isLeaf = children.length === 0
      treeData.value = [...treeData.value]
    } catch (e) {
      message.error(e?.message || '加载下级失败')
    } finally {
      resolve()
    }
  })
}

function onTreeSelect(keys, info) {
  selectedKeys.value = keys
  const node = info?.node
  const raw = node?.raw || node?.dataRef?.raw || {
    code: node?.code || node?.key,
    name: node?.name || node?.title,
    level: node?.level,
    parentCode: node?.parentCode,
  }
  if (raw?.code) selectRegion(raw, { fromTree: true })
}

async function selectRegion(row, opts = {}) {
  if (!row?.code) return
  selected.value = {
    code: row.code,
    name: row.name,
    parentCode: row.parentCode,
    level: row.level,
  }
  selectedKeys.value = [row.code]
  await Promise.all([loadPath(row.code), loadChildren(row), loadDescendantCount(row.code)])

  // 非搜索模式下尽量展开祖先（路径已加载）
  if (!opts.fromTree && !searchMode.value && pathNodes.value.length > 1) {
    const ancestors = pathNodes.value.slice(0, -1).map((n) => n.code)
    expandedKeys.value = [...new Set([...expandedKeys.value, ...ancestors])]
  }
}

async function loadPath(code) {
  try {
    const res = await getRegionPath(code)
    pathNodes.value = res?.data || []
    if (pathNodes.value.length) {
      const last = pathNodes.value[pathNodes.value.length - 1]
      selected.value = {
        code: last.code,
        name: last.name,
        parentCode: last.parentCode,
        level: last.level,
      }
    }
  } catch {
    pathNodes.value = selected.value ? [selected.value] : []
  }
}

async function loadChildren(row) {
  childrenLoading.value = true
  try {
    if (row.level >= 3) {
      childRows.value = []
      return
    }
    const res = await listRegionChildren(row.code)
    childRows.value = res?.data || []
  } catch {
    childRows.value = []
  } finally {
    childrenLoading.value = false
  }
}

async function loadDescendantCount(code) {
  descendantLoading.value = true
  try {
    const res = await listRegionDescendants(code)
    descendantCount.value = (res?.data || []).length
  } catch {
    descendantCount.value = 0
  } finally {
    descendantLoading.value = false
  }
}

async function onSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    clearSearch()
    return
  }
  searchMode.value = true
  loading.value = true
  try {
    const res = await searchRegions(kw, 80)
    searchRows.value = res?.data || []
  } catch (e) {
    searchRows.value = []
    message.error(e?.message || '搜索失败')
  } finally {
    loading.value = false
  }
}

function clearSearch() {
  keyword.value = ''
  searchMode.value = false
  searchRows.value = []
}

let searchTimer = null
watch(keyword, (v) => {
  clearTimeout(searchTimer)
  const kw = String(v || '').trim()
  if (!kw) {
    if (searchMode.value) clearSearch()
    return
  }
  searchTimer = setTimeout(() => onSearch(), 320)
})

async function copyText(text) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}

async function onRefresh() {
  await loadStats()
  if (searchMode.value) await onSearch()
  else await loadProvinces()
  if (selected.value?.code) await selectRegion(selected.value)
}

async function onSeed() {
  busy.value = true
  try {
    const res = await seedRegions()
    const n = res?.data?.imported || 0
    message.success(n > 0 ? `已导入 ${n} 条` : '地区库已有数据')
    await loadStats()
    await loadProvinces()
  } catch (e) {
    message.error(e?.message || '导入失败')
  } finally {
    busy.value = false
  }
}

async function onReseed() {
  busy.value = true
  try {
    const res = await reseedRegions()
    message.success(`已重新导入 ${res?.data?.imported || 0} 条`)
    selected.value = null
    pathNodes.value = []
    childRows.value = []
    selectedKeys.value = []
    expandedKeys.value = []
    clearSearch()
    await loadStats()
    await loadProvinces()
  } catch (e) {
    message.error(e?.message || '重新导入失败')
  } finally {
    busy.value = false
  }
}

watch(
  () => stats.value.count,
  (n) => {
    if (n > 0 && treeData.value.length === 0 && !searchMode.value) {
      loadProvinces()
    }
  },
)

onMounted(async () => {
  bootLoading.value = true
  try {
    await loadStats()
    if (stats.value.count > 0) await loadProvinces()
  } finally {
    bootLoading.value = false
  }
})
</script>

<style scoped>
.region-layout {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.region-top {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
  padding: 16px 18px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 16px;
}
.region-top__intro {
  flex: 1;
  min-width: 200px;
}
.region-top__intro h3 {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
}
.region-top__intro p {
  margin: 0;
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.5;
}
.region-top__intro code {
  font-size: 11px;
  padding: 0 4px;
  border-radius: 3px;
  background: var(--color-canvas-soft-2);
}
.region-top__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.region-stat {
  font-size: 12px;
  color: var(--color-mute);
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  border-radius: 999px;
  padding: 4px 10px;
  font-variant-numeric: tabular-nums;
}
.region-stat em {
  font-style: normal;
  font-weight: 600;
  color: var(--color-ink);
  margin-right: 2px;
}
.region-stat--total {
  background: var(--color-canvas-soft-2);
}
.region-top__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-left: auto;
}
.region-top__actions .lb-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.region-ready {
  font-size: 12px;
  color: var(--color-ink);
  background: var(--color-success-bg, #f0fdf4);
  border: 1px solid var(--color-border-green, #bbf7d0);
  border-radius: 999px;
  padding: 3px 10px;
}

.region-empty-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 16px;
}

.region-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 14px;
  overflow: hidden;
}

.region-rail {
  width: 340px;
  flex-shrink: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 16px;
  padding: 12px;
  overflow: hidden;
}
.region-rail__search {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  flex-shrink: 0;
}
.region-rail__search :deep(.ant-input-affix-wrapper) {
  flex: 1;
  border-radius: 8px;
}
.region-search-icon {
  color: var(--color-mute);
  display: block;
}
.region-rail__scroll {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 2px;
}
.region-rail__hint {
  padding: 20px 8px;
  font-size: 12px;
  color: var(--color-mute);
  text-align: center;
}

.region-tree-wrap {
  padding-right: 4px;
}
.region-tree-wrap :deep(.ant-tree) {
  background: transparent;
  font-size: 13px;
}
.region-tree-wrap :deep(.ant-tree-treenode) {
  padding: 2px 0;
  width: 100%;
}
.region-tree-wrap :deep(.ant-tree-node-content-wrapper) {
  border-radius: 8px;
  padding: 4px 8px !important;
  transition: background-color 0.15s ease;
  width: 100%;
}
.region-tree-wrap :deep(.ant-tree-node-content-wrapper:hover) {
  background: var(--color-canvas-soft-2) !important;
}
.region-tree-wrap :deep(.ant-tree-node-selected) {
  background: var(--color-canvas-soft-2) !important;
  box-shadow: inset 2px 0 0 var(--color-ink);
}
.region-tree-wrap :deep(.ant-tree-switcher) {
  display: flex;
  align-items: center;
  justify-content: center;
}

.region-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
}
.region-node__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-ink);
}
.region-node__code {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--color-mute);
  font-variant-numeric: tabular-nums;
  background: transparent;
}

.region-level {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
  padding: 3px 5px;
  border-radius: 4px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
}
.region-level--1 {
  color: var(--color-ink);
  background: var(--color-canvas-soft-2);
}
.region-level--2 {
  color: var(--color-body);
  background: var(--color-info-bg, #e8f4ff);
}
.region-level--3 {
  color: var(--color-mute);
  background: var(--color-canvas-soft);
}
.region-level--lg {
  font-size: 12px;
  padding: 4px 8px;
}

.region-search-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.region-search-list__meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--color-mute);
  padding: 0 2px 6px;
}
.region-hit {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  text-align: left;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  border-left: 2px solid transparent;
  background: transparent;
  cursor: pointer;
  color: var(--color-ink);
  transition: background-color 0.15s ease, border-color 0.15s ease;
}
.region-hit:hover {
  background: var(--color-canvas-soft-2);
}
.region-hit.active {
  background: var(--color-canvas-soft-2);
  border-color: var(--color-hairline);
  border-left-color: var(--color-ink);
}
.region-hit__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}
.region-hit__code {
  font-size: 11px;
  color: var(--color-mute);
  font-variant-numeric: tabular-nums;
}

.region-detail {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 22px 24px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 16px;
  overflow: auto;
}

.region-welcome {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 100%;
}
.region-welcome__hero {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 18px 20px;
  border-radius: 14px;
  border: 1px solid var(--color-hairline);
  background:
    linear-gradient(135deg, var(--color-canvas-soft) 0%, var(--color-canvas) 55%),
    var(--color-canvas-soft);
}
.region-welcome__mark {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--color-ink);
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  box-shadow: 0 1px 1px #00000005, 0 2px 2px #0000000a;
}
.region-welcome__hero h2 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
  color: var(--color-ink);
  letter-spacing: -0.02em;
}
.region-welcome__hero p {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--color-mute);
  max-width: 520px;
}
.region-welcome__hero code {
  font-size: 12px;
  padding: 0 4px;
  border-radius: 3px;
  background: var(--color-canvas-soft-2);
  color: var(--color-ink);
}

.region-welcome__dist {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.region-dist {
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas);
}
.region-dist__top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--color-mute);
}
.region-dist__top strong {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-ink);
  font-variant-numeric: tabular-nums;
}
.region-dist__track {
  height: 6px;
  border-radius: 999px;
  background: var(--color-canvas-soft-2);
  overflow: hidden;
}
.region-dist__bar {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--color-ink);
}
.region-dist__bar--city {
  background: var(--color-link);
  opacity: 0.75;
}
.region-dist__bar--district {
  background: var(--color-hairline-strong);
}

.region-welcome__steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.region-step {
  display: flex;
  gap: 10px;
  padding: 14px;
  border-radius: 12px;
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas-soft);
}
.region-step__n {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-on-primary);
  background: var(--color-ink);
}
.region-step h4 {
  margin: 0 0 4px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
}
.region-step p {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--color-mute);
}

.region-welcome__quick-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.region-welcome__quick-head h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}
.region-welcome__quick-head span {
  font-size: 12px;
  color: var(--color-mute);
}
.region-quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
  gap: 8px;
}
.region-quick {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  text-align: left;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas);
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease, transform 0.1s ease;
}
.region-quick:hover {
  border-color: var(--color-hairline-strong);
  background: var(--color-canvas-soft);
}
.region-quick:active {
  transform: scale(0.98);
}
.region-quick span {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-ink);
}
.region-quick code {
  font-size: 11px;
  color: var(--color-mute);
  font-variant-numeric: tabular-nums;
}

.region-detail__head h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--color-ink);
  letter-spacing: -0.02em;
}
.region-detail__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.region-crumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-mute);
}
.region-crumb__link {
  border: none;
  background: none;
  padding: 0;
  color: var(--color-link);
  cursor: pointer;
  font-size: 13px;
}
.region-crumb__link:hover {
  color: var(--color-link-deep);
}
.region-crumb__current {
  color: var(--color-ink);
  font-weight: 500;
}
.region-crumb__sep {
  color: var(--color-hairline-strong);
  margin: 0 2px;
}

.region-detail__code-card {
  padding: 16px 18px;
  border-radius: 12px;
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
}
.region-detail__code-label {
  font-size: 12px;
  color: var(--color-mute);
  margin-bottom: 8px;
}
.region-detail__code-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.region-detail__code-row code {
  font-size: 28px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-ink);
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}
.region-detail__code-row .lb-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.region-detail__hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--color-mute);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.region-detail__hint code {
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  color: var(--color-ink);
}

.region-detail__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.region-meta-item {
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas);
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--color-ink);
  font-variant-numeric: tabular-nums;
}
.region-meta-item__label {
  font-size: 11px;
  color: var(--color-mute);
}

.region-detail__children {
  flex: 1;
  min-height: 0;
}
.region-detail__section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
}
.region-detail__section-head h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}
.region-detail__section-head span {
  font-size: 12px;
  color: var(--color-mute);
  font-variant-numeric: tabular-nums;
}
.region-child-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(148px, 1fr));
  gap: 8px;
}
.region-child {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  text-align: left;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas);
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}
.region-child:hover {
  border-color: var(--color-hairline-strong);
  background: var(--color-canvas-soft);
}
.region-child__name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-ink);
}
.region-child code {
  font-size: 11px;
  color: var(--color-mute);
  font-variant-numeric: tabular-nums;
}

.btn-link {
  border: none;
  background: transparent;
  color: var(--color-link);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}
.btn-link:hover {
  color: var(--color-link-deep);
}

@media (max-width: 960px) {
  .region-body {
    flex-direction: column;
  }
  .region-rail {
    width: 100%;
    max-height: 42vh;
  }
  .region-detail__meta,
  .region-welcome__dist,
  .region-welcome__steps {
    grid-template-columns: 1fr;
  }
}
</style>
