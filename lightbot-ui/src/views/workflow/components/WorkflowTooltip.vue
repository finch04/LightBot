<template>
  <a-tooltip
    v-bind="tooltipAttrs"
    :placement="placement"
    :destroy-tooltip-on-hide="true"
    :get-popup-container="tooltipPopupContainer"
    :overlay-class-name="overlayClassName"
    :overlay-style="mergedOverlayStyle"
    :align="mergedAlign"
    :mouse-enter-delay="0.2"
    :arrow="true"
  >
    <slot />
  </a-tooltip>
</template>

<script setup>
import { computed, useAttrs } from 'vue'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  placement: { type: String, default: 'top' },
  /** 与 AgentDetail 一致：视口内平移，不翻转方位，箭头始终对准触发元素 */
  avoidFlip: { type: Boolean, default: true },
  overlayClassName: { type: String, default: 'no-flip-tooltip' },
  maxWidth: { type: String, default: '320px' },
})

const attrs = useAttrs()

const tooltipAttrs = computed(() => {
  const {
    placement,
    avoidFlip,
    overlayClassName,
    maxWidth,
    align,
    'overlay-class-name': _ocn,
    'overlay-style': _os,
    'overlay-inner-style': _ois,
    ...rest
  } = attrs
  return rest
})

const mergedOverlayStyle = computed(() => ({
  maxWidth: props.maxWidth,
  ...(attrs['overlay-style'] || attrs.overlayStyle || {}),
}))

/** 禁止翻转 placement，仅在原方位平移以避开视口边缘（箭头仍指向触发器） */
const mergedAlign = computed(() => {
  const userAlign = attrs.align || {}
  if (!props.avoidFlip) {
    return Object.keys(userAlign).length ? userAlign : undefined
  }
  return {
    ...userAlign,
    overflow: {
      shiftX: 64,
      shiftY: 64,
      adjustX: false,
      adjustY: false,
      ...(userAlign.overflow || {}),
    },
  }
})

function tooltipPopupContainer() {
  return document.body
}
</script>
