<template>
  <div class="data-center-page" :class="{ 'data-center-page--designer': designerOpen }">
    <LbPageTabsHeader
      v-show="!designerOpen"
      title="数据中心"
      :tabs="tabs"
      :active-key="activeTab"
      @update:active-key="onTabChange"
    />
    <div class="tab-content">
      <DataPoolPanel v-if="activeTab === 'pool' && !designerOpen" />
      <DataModelPanel
        v-if="activeTab === 'model' || designerOpen"
        @update:designer-open="designerOpen = $event"
      />
      <RegionPanel v-if="activeTab === 'region' && !designerOpen" />
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LbPageTabsHeader from '../components/common/LbPageTabsHeader.vue'
import DataPoolPanel from './data-center/DataPoolPanel.vue'
import DataModelPanel from './data-center/DataModelPanel.vue'
import RegionPanel from './data-center/RegionPanel.vue'

/** 问数增强入口在数据模型卡片上（抽屉），不再单独 Tab */
const VALID_TABS = ['pool', 'model', 'region']
const tabs = [
  { key: 'pool', label: '数据池' },
  { key: 'model', label: '数据模型' },
  { key: 'region', label: '地区库' },
]

const route = useRoute()
const router = useRouter()

function resolveTab(tab) {
  if (tab === 'ask') return 'model'
  return VALID_TABS.includes(tab) ? tab : 'pool'
}

const activeTab = ref(resolveTab(route.query.tab))
const designerOpen = ref(false)

function onTabChange(key) {
  activeTab.value = key
  router.replace({ query: { ...route.query, tab: key } })
}

watch(
  () => route.query.tab,
  (tab) => {
    const next = resolveTab(tab)
    if (tab === 'ask') {
      router.replace({ query: { ...route.query, tab: 'model' } })
    }
    if (next !== activeTab.value) {
      activeTab.value = next
    }
  },
)
</script>

<style scoped>
.data-center-page {
  height: var(--app-content-height);
  overflow: hidden;
  background: var(--color-canvas-soft);
  padding: 28px 32px;
  display: flex;
  flex-direction: column;
}
/* 配置表单时铺满整页内容区（覆盖标题与 Tab） */
.data-center-page--designer {
  padding: 20px 24px;
}
.tab-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.tab-content > * {
  flex: 1;
  min-height: 0;
}
</style>
