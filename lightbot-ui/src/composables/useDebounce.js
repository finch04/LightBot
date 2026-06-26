import { ref, watch, onBeforeUnmount } from 'vue'

/**
 * 防抖 watch：searchText 变化后延迟 delay ms 再调用 callback
 * @param {import('vue').Ref} source 需要监听的 ref
 * @param {Function} callback 防抖后的回调
 * @param {number} [delay=300] 延迟毫秒
 * @returns {{ debouncedValue: import('vue').Ref } }
 */
export function useDebouncedWatch(source, callback, delay = 300) {
  let timer = null

  const debouncedValue = ref(source.value)

  watch(source, (val) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      debouncedValue.value = val
      callback()
    }, delay)
  })

  onBeforeUnmount(() => {
    if (timer) clearTimeout(timer)
  })

  return { debouncedValue }
}
