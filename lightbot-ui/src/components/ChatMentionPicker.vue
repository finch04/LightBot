<template>
  <Teleport to="body">
    <div
      v-if="visible"
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
            <div class="mention-group-label">{{ group.label }}</div>
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

const pos = ref({ left: 0, top: 0, width: 320 })
const PICKER_WIDTH = 320
const PICKER_MAX_HEIGHT = 280

// 浮层显示/光标变化时基于 caretRect 计算坐标
watch(
  () => [props.visible, props.caretRect],
  async ([v]) => {
    if (!v) return
    await nextTick()
    updatePos()
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
  // 上弹优先：浮层底部贴近光标顶部，留 6px 间隙
  let top = cr.top - 6 - PICKER_MAX_HEIGHT
  // 上方空间不足：改为下弹，浮层顶部贴近光标底部
  if (top < 8) top = cr.bottom + 6
  // 左对齐 @ 符号
  let left = cr.left
  // 右边界保护
  if (left + PICKER_WIDTH > window.innerWidth - 8) {
    left = window.innerWidth - PICKER_WIDTH - 8
  }
  pos.value = { left, top, width: PICKER_WIDTH }
}

const pickerStyle = computed(() => ({
  left: `${pos.value.left}px`,
  top: `${pos.value.top}px`,
  width: `${pos.value.width}px`,
}))


// 按本地 query 过滤候选（前端即时响应，避免每次按键都打后端）
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

const filteredItems = computed(() => {
  return visibleGroups.value.flatMap(g => g.items)
})

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
  background: var(--bg-color, #fff);
  border: 1.5px solid #1f1f1f;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18);
  font-size: 13px;
}

.mention-empty {
  padding: 12px;
  text-align: center;
  color: var(--text-color-secondary, #999);
}

.mention-group-label {
  padding: 6px 12px 4px;
  font-size: 11px;
  color: var(--text-color-secondary, #999);
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.mention-item {
  padding: 7px 12px;
  cursor: pointer;
  transition: background-color 0.15s;

  &:hover, &.active {
    background: var(--bg-color-hover, #f3f4f6);
  }

  &.disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
}
</style>
