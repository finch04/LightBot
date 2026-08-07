<template>
  <div class="automation-page">
    <LbPageTabsHeader
      title="自动化"
      :tabs="tabs"
      :active-key="activeTab"
      @update:active-key="onTabChange"
    />
    <div class="tab-content">
      <AutomationRecordsPanel v-if="activeTab === 'records'" />
      <AutomationConfigPanel v-else-if="activeTab === 'config'" />
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LbPageTabsHeader from '../components/common/LbPageTabsHeader.vue'
import AutomationRecordsPanel from './automation/AutomationRecordsPanel.vue'
import AutomationConfigPanel from './automation/AutomationConfigPanel.vue'

const VALID_TABS = ['records', 'config']
const tabs = [
  { key: 'records', label: '任务记录' },
  { key: 'config', label: '任务配置' },
]

const route = useRoute()
const router = useRouter()
const activeTab = ref(VALID_TABS.includes(route.query.tab) ? route.query.tab : 'records')

function onTabChange(key) {
  activeTab.value = key
  router.replace({ query: { ...route.query, tab: key } })
}

watch(
  () => route.query.tab,
  (tab) => {
    if (VALID_TABS.includes(tab) && tab !== activeTab.value) {
      activeTab.value = tab
    }
  },
)
</script>

<style scoped>
.automation-page {
  height: var(--app-content-height);
  overflow: hidden;
  background: var(--color-canvas-soft);
  padding: 28px 32px;
  display: flex;
  flex-direction: column;
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
