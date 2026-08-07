/**
 * 业务办理页前端渲染解析
 *
 * 顺序：
 * 1. registerBusinessPageComponent 宿主注入组件
 * 2. payload.pageHtml / pageUrl → H5BusinessPageFrame（srcdoc 或外链 iframe）
 * 3. BusinessPageFallback
 */
import { defineAsyncComponent, markRaw, shallowRef } from 'vue'

const H5BusinessPageFrame = defineAsyncComponent(() => import('./H5BusinessPageFrame.vue'))
const BusinessPageFallback = defineAsyncComponent(() => import('./BusinessPageFallback.vue'))

/** @type {import('vue').ShallowRef<Map<string, any>>} */
const runtimeComponents = shallowRef(new Map())

/**
 * 上层业务前端注入定制 UI（按已注册的 pageType）。
 * @param {string} pageType
 * @param {import('vue').Component} component
 */
export function registerBusinessPageComponent(pageType, component) {
  const key = String(pageType || '').trim()
  if (!key || !component) return
  const next = new Map(runtimeComponents.value)
  next.set(key, markRaw(component))
  runtimeComponents.value = next
}

/**
 * @param {string} pageType
 */
export function unregisterBusinessPageComponent(pageType) {
  const key = String(pageType || '').trim()
  if (!key) return
  const next = new Map(runtimeComponents.value)
  next.delete(key)
  runtimeComponents.value = next
}

export function resolveBusinessPageComponent(pageType, payload) {
  const key = String(pageType || '').trim()
  if (key && runtimeComponents.value.has(key)) {
    return runtimeComponents.value.get(key)
  }
  // needsPageContent：历史消息已瘦身掉 pageHtml，组件内会按 pageType 回填
  if (payload?.pageHtml || payload?.pageUrl || payload?.needsPageContent) return H5BusinessPageFrame
  return BusinessPageFallback
}

export function isRegisteredBusinessPage(pageType) {
  return runtimeComponents.value.has(String(pageType || '').trim())
}

export function listBusinessPageTypes() {
  return [...runtimeComponents.value.keys()]
}

export function getBusinessPageComponents() {
  return Object.fromEntries(runtimeComponents.value)
}
