<template>
  <Teleport to="body">
    <div
      v-if="visible"
      ref="pickerRef"
      class="mention-picker"
      :style="pickerStyle"
      @mousedown.prevent
    >
      <a-spin :spinning="loading" size="small">
        <div v-if="filteredItems.length === 0 && !loading" class="mention-empty">
          {{ query ? `无匹配项：${query}` : '无可 @ 的资源' }}
        </div>
        <template v-else>
          <div
            v-for="(group, gi) in visibleGroups"
            :key="group.type"
            class="mention-group"
          >
            <div class="mention-group-label">{{ groupLabel(group) }}</div>
            <div
              v-for="(item, ii) in group.items"
              :key="item.token"
              class="mention-item"
              :class="{ active: flatIndex(gi, ii) === activeIndex, disabled: !item.enabled }"
              @mouseenter="$emit('hover', flatIndex(gi, ii))"
              @click.stop="$emit('select', item)"
            >
              <EntitySelectOption
                :type="group.type"
                :name="item.name"
                :tag="!item.enabled ? (item.disabledReason || '不可用') : ''"
                :tag-muted="!item.enabled ? '已禁用' : ''"
                :desc="item.description"
              />
            </div>
          </div>
        </template>
      </a-spin>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, ref, watch, nextTick, onBeforeUnmount } from 'vue'
import EntitySelectOption from './EntitySelectOption.vue'
import { MENTION_TYPE_LABELS } from '../utils/mentionDisplay'

const props = defineProps({
  visible: { type: Boolean, default: false },
  groups: { type: Array, default: () => [] },
  query: { type: String, default: '' },
  activeIndex: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  /** 光标位置 rect（{left, top, bottom}）：浮层贴近 @ 符号，fixed 脱离父容器 overflow 裁剪 */
  caretRect: { type: Object, default: null },
})

defineEmits(['select', 'hover'])

const pickerRef = ref(null)
const pos = ref({ left: 0, top: 0, width: 320 })
const PICKER_WIDTH = 320
const PICKER_MAX_HEIGHT = 280
const GAP = 6

const visibleGroups = computed(() => {
  const q = (props.query || '').trim().toLowerCase()
  if (!q) return props.groups
  return props.groups
    .map(g => ({
      ...g,
      items: (g.items || []).filter(it =>
        (it.name || '').toLowerCase().includes(q) ||
        (it.description || '').toLowerCase().includes(q),
      ),
    }))
    .filter(g => g.items.length > 0)
})

const filteredItems = computed(() => visibleGroups.value.flatMap(g => g.items))

async function scheduleUpdatePos() {
  await nextTick()
  updatePos()
  await nextTick()
  updatePos()
}

// 浮层显示/内容变化时基于 caretRect 与真实高度重新定位
watch(
  () => [props.visible, props.caretRect, props.query, props.loading, filteredItems.value.length],
  async ([visible]) => {
    if (!visible) {
      window.removeEventListener('scroll', updatePos, true)
      window.removeEventListener('resize', updatePos)
      return
    }
    await scheduleUpdatePos()
    window.addEventListener('scroll', updatePos, true)
    window.addEventListener('resize', updatePos)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  window.removeEventListener('scroll', updatePos, true)
  window.removeEventListener('resize', updatePos)
})

function updatePos() {
  const cr = props.caretRect
  if (!cr) return

  const el = pickerRef.value
  const pickerHeight = el
    ? Math.min(el.getBoundingClientRect().height, PICKER_MAX_HEIGHT)
    : 0

  // 上弹优先：弹窗底边贴近 @ 符号顶边（用真实高度，避免内容少时浮空）
  let top = cr.top - GAP - (pickerHeight || 48)
  if (top < 8) {
    top = cr.bottom + GAP
  }

  let left = cr.left
  if (left + PICKER_WIDTH > window.innerWidth - 8) {
    left = window.innerWidth - PICKER_WIDTH - 8
  }
  if (left < 8) left = 8
  pos.value = { left, top, width: PICKER_WIDTH }
}

const pickerStyle = computed(() => ({
  left: `${pos.value.left}px`,
  top: `${pos.value.top}px`,
  width: `${pos.value.width}px`,
}))

function groupLabel(group) {
  return MENTION_TYPE_LABELS[group?.type] || group?.label || group?.type || ''
}

// 计算 (groupIdx, itemIdx) 在扁平列表中的全局索引
function flatIndex(gi, ii) {
  let count = 0
  for (let i = 0; i < gi; i++) {
    count += visibleGroups.value[i].items.length
  }
  return count + ii
}

defineExpose({
  filteredItems,
  visibleGroups,
})
</script>

<style lang="less">
/* Teleport 到 body，不能用 scoped，否则样式不生效 */
.mention-picker {
  position: fixed;
  z-index: 1050;
  max-height: 280px;
  overflow-y: auto;
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-hairline-strong);
  border-radius: 12px;
  box-shadow: var(--shadow-4);
  font-size: 13px;
  color: var(--color-ink);
}

.mention-empty {
  padding: 12px;
  text-align: center;
  color: var(--color-mute);
}

.mention-group-label {
  padding: 6px 12px 4px;
  font-size: 11px;
  color: var(--color-mute);
  font-weight: 500;
  letter-spacing: 0.2px;
}

.mention-item {
  padding: 7px 12px;
  cursor: pointer;
  transition: background-color 0.15s;
  color: var(--color-ink);

  &:hover, &.active {
    background: var(--color-canvas-soft-3);
  }

  &.disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
}

.mention-item .entity-select-name {
  color: var(--color-ink);
}

.mention-item .entity-select-desc {
  color: var(--color-mute);
}
</style>
