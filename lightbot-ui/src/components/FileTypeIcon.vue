<template>
  <img
    class="file-type-icon"
    :src="iconUrl"
    :style="{ width: `${size}px`, height: `${size}px` }"
    alt=""
    draggable="false"
  />
</template>

<script setup>
import { computed } from 'vue'
import { resolveFileIconUrl } from '@/utils/fileIcon'

const props = defineProps({
  name: { type: String, default: '' },
  isDir: { type: Boolean, default: false },
  folderVariant: { type: String, default: 'default' },
  size: { type: Number, default: 16 },
})

const iconUrl = computed(() =>
  resolveFileIconUrl(props.name, { isDir: props.isDir, folderVariant: props.folderVariant })
)
</script>

<style scoped>
.file-type-icon {
  display: inline-block;
  object-fit: contain;
  flex-shrink: 0;
  vertical-align: middle;
}
</style>
