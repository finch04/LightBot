<template>
  <!-- 面包屑模式 + slim：仅顶栏一行（面包屑 + 可选标题 + tags + 操作） -->
  <div
    v-if="breadcrumb && breadcrumb.length && slim"
    class="lb-detail-header lb-detail-header--stack lb-detail-header--slim"
  >
    <div class="lb-detail-header__top-bar">
      <div class="lb-detail-header__top-left">
        <LbBreadcrumb :items="breadcrumb" :show-arrow="showBack" @back="emit('back')" />
        <h2 v-if="title" class="lb-detail-header__title lb-detail-header__title--inline">{{ title }}</h2>
        <div v-if="$slots.tags || tagsList.length" class="lb-detail-header__tags lb-detail-header__tags--inline">
          <slot name="tags">
            <a-tag v-for="t in tagsList" :key="t" color="blue">{{ t }}</a-tag>
          </slot>
        </div>
      </div>
      <div v-if="$slots.extra" class="lb-detail-header__extra">
        <slot name="extra" />
      </div>
    </div>
  </div>

  <!-- 面包屑模式：顶栏 + 标题区 两行布局 -->
  <div v-else-if="breadcrumb && breadcrumb.length" class="lb-detail-header lb-detail-header--stack">
    <div class="lb-detail-header__top-bar">
      <LbBreadcrumb :items="breadcrumb" :show-arrow="showBack" @back="emit('back')" />
      <div v-if="$slots.extra" class="lb-detail-header__extra">
        <slot name="extra" />
      </div>
    </div>
    <div class="lb-detail-header__title-section">
      <div class="lb-detail-header__title-text">
        <div class="lb-detail-header__title-row">
          <a-tooltip :title="title">
            <h2 class="lb-detail-header__title">{{ title }}</h2>
          </a-tooltip>
          <div v-if="$slots.tags || tagsList.length" class="lb-detail-header__tags">
            <slot name="tags">
              <a-tag v-for="t in tagsList" :key="t" color="blue">{{ t }}</a-tag>
            </slot>
          </div>
          <div v-if="$slots.stats" class="lb-detail-header__stats">
            <slot name="stats" />
          </div>
        </div>
        <p v-if="desc" class="lb-detail-header__desc">{{ desc }}</p>
      </div>
    </div>
  </div>

  <!-- 兼容模式：单行布局（原 showBack + title + tags + extra） -->
  <div v-else class="lb-detail-header">
    <div class="lb-detail-header__main">
      <button v-if="showBack" type="button" class="page-back-icon" :title="`${title} 返回`" @click="emit('back')">
        <ArrowLeftOutlined />
      </button>
      <a-tooltip :title="title">
        <h2 class="lb-detail-header__title">{{ title }}</h2>
      </a-tooltip>
      <div v-if="$slots.tags || tagsList.length" class="lb-detail-header__tags">
        <slot name="tags">
          <a-tag v-for="t in tagsList" :key="t" color="blue">{{ t }}</a-tag>
        </slot>
      </div>
    </div>
    <div v-if="$slots.extra" class="lb-detail-header__extra">
      <slot name="extra" />
    </div>
  </div>
</template>

<script setup>
/**
 * 详情页头部
 * 支持三种模式：
 * 1. 面包屑模式（推荐）：传入 breadcrumb 数组，渲染「顶栏（面包屑 + 操作）+ 标题区（h2 + 描述竖线 + 元信息）」两行布局，
 *    参考 Linear / Notion / Dify。解决「返回按钮放哪都丑」问题——箭头融入面包屑导航路径。
 * 2. 面包屑 + slim：仅顶栏一行；有 title 时紧跟面包屑，tags 再跟其后（适用于 Agent 详情等需压缩高度的页面）。
 * 3. 兼容模式：不传 breadcrumb 但 showBack=true，退化为原「返回按钮 + 标题 + tags + extra」单行布局。
 */
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { computed } from 'vue'
import LbBreadcrumb from './LbBreadcrumb.vue'

const props = defineProps({
  title: { type: String, required: true },
  desc: { type: String, default: '' },
  tags: { type: [String, Array], default: '' },
  showBack: { type: Boolean, default: true },
  breadcrumb: {
    type: Array,
    default: undefined,
  },
  slim: { type: Boolean, default: false },
})

const emit = defineEmits(['back'])

const tagsList = computed(() => {
  if (!props.tags) return []
  if (Array.isArray(props.tags)) return props.tags
  return String(props.tags)
    .split(/[,，]/)
    .map((t) => t.trim())
    .filter(Boolean)
})
</script>
