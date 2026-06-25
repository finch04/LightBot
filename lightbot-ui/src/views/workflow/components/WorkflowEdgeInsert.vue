<template>
  <div v-if="visible" class="edge-insert-anchor">
    <a-popover
      v-model:open="menuOpen"
      trigger="click"
      placement="rightTop"
      overlay-class-name="edge-insert-popover"
      :get-popup-container="getPopupContainer"
      @open-change="onOpenChange"
    >
      <template #content>
        <div class="edge-insert-menu" @mousedown.stop>
          <div class="edge-insert-menu-title">插入节点</div>
          <a-input
            v-model:value="search"
            allow-clear
            size="small"
            placeholder="搜索节点类型"
            class="edge-insert-search"
          />
          <div class="edge-insert-groups">
            <div v-for="group in filteredGroups" :key="group.key" class="edge-insert-group">
              <div class="edge-insert-group-title">{{ group.title }}</div>
              <button
                v-for="type in group.items"
                :key="type"
                type="button"
                class="edge-insert-item"
                @click="onPick(type)"
              >
                <span class="edge-insert-dot" :style="{ background: getNodeColor(type) }" />
                <span class="edge-insert-item-title">{{ getNodeMeta(type).title }}</span>
                <span class="edge-insert-item-desc">{{ getNodeMeta(type).desc }}</span>
              </button>
            </div>
            <a-empty v-if="filteredGroups.length === 0" description="无匹配节点" :image="false" />
          </div>
        </div>
      </template>
      <button type="button" class="edge-insert-btn" title="在连线中间插入节点" @click.stop>
        <PlusOutlined />
      </button>
    </a-popover>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { getInsertableNodeLibraryGroups, getNodeMeta, getNodeColor } from '../nodeMeta'

const props = defineProps({
  visible: { type: Boolean, default: false },
})

const emit = defineEmits(['select', 'menu-open', 'menu-close'])

const menuOpen = ref(false)
const search = ref('')

const filteredGroups = computed(() => getInsertableNodeLibraryGroups(search.value))

watch(() => props.visible, (v) => {
  if (!v) menuOpen.value = false
})

function getPopupContainer() {
  return document.body
}

function onOpenChange(open) {
  if (open) {
    emit('menu-open')
  } else {
    search.value = ''
    emit('menu-close')
  }
}

function onPick(type) {
  menuOpen.value = false
  search.value = ''
  emit('select', type)
}
</script>

<style scoped>
.edge-insert-anchor {
  pointer-events: auto;
}
.edge-insert-btn {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid #6366f1;
  background: var(--color-canvas);
  color: var(--color-link);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.35);
  transition: transform 0.15s, background 0.15s, color 0.15s;
}
.edge-insert-btn:hover {
  background: #6366f1;
  color: #fff;
  transform: scale(1.08);
}
</style>

<style>
.edge-insert-popover .ant-popover-inner {
  padding: 0;
}
.edge-insert-menu {
  width: 280px;
  max-height: 360px;
  display: flex;
  flex-direction: column;
}
.edge-insert-menu-title {
  padding: 10px 12px 8px;
  font-weight: 600;
  font-size: 13px;
  color: var(--color-ink);
  border-bottom: 1px solid #f3f4f6;
}
.edge-insert-search {
  margin: 8px 10px;
  width: calc(100% - 20px);
}
.edge-insert-groups {
  overflow-y: auto;
  padding: 4px 8px 10px;
  max-height: 280px;
}
.edge-insert-group-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-mute);
  padding: 6px 4px 4px;
}
.edge-insert-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  text-align: left;
}
.edge-insert-item:hover {
  background: var(--color-canvas-soft-2);
}
.edge-insert-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.edge-insert-item-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-dark);
  flex-shrink: 0;
}
.edge-insert-item-desc {
  font-size: 11px;
  color: var(--color-mute);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
</style>
