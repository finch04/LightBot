<template>
  <nav class="lb-breadcrumb">
    <button
      v-if="showArrow && items.length > 0"
      type="button"
      class="lb-breadcrumb__arrow"
      @click="handleArrowClick"
    >
      <ArrowLeftOutlined />
    </button>
    <ul v-if="visibleItems.length" class="lb-breadcrumb__list">
      <li
        v-for="(item, idx) in visibleItems"
        :key="idx"
        class="lb-breadcrumb__item"
      >
        <button
          v-if="idx < visibleItems.length - 1 && item.onClick"
          type="button"
          class="lb-breadcrumb__link"
          @click="item.onClick"
        >
          {{ item.label }}
        </button>
        <span v-else class="lb-breadcrumb__current">{{ item.label }}</span>
        <span v-if="idx < visibleItems.length - 1" class="lb-breadcrumb__sep">/</span>
      </li>
    </ul>
  </nav>
</template>

<script setup>
/**
 * 通用面包屑导航
 * 路径项 + 可选返回箭头；最后一项为当前页（不可点击），其余项点击触发 onClick。
 * 独立原子组件，LbDetailHeader / 管理页顶部 / 内嵌导航均可复用。
 */
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { computed } from 'vue'

const props = defineProps({
  items: {
    type: Array,
    default: () => [],
  },
  showArrow: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['back'])

/** 无 label 的项仅用于返回箭头，不渲染面包屑文字 */
const visibleItems = computed(() =>
  (props.items || []).filter((item) => item?.label != null && String(item.label).trim() !== '')
)

function handleArrowClick() {
  const first = props.items[0]
  if (first && typeof first.onClick === 'function') {
    first.onClick()
    return
  }
  emit('back')
}
</script>
