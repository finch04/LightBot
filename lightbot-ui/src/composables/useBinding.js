import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { toBindingId, resolveBindingItems } from '../utils/bindingId'

/**
 * 通用绑定管理 composable
 * 消除 AgentDetail.vue 中 5 个绑定 Tab 的重复代码
 *
 * @param {Object} config
 * @param {number} config.limit - 绑定数量上限
 * @param {string} config.entityLabel - 实体标签（用于提示信息）
 * @param {Function} config.loadApi - 加载列表的 API 函数
 * @param {Function} [config.filterPredicate] - 可选列表过滤条件（默认过滤 disabled）
 * @param {string[]} [config.searchFields] - 搜索字段（默认 ['name', 'displayName', 'description']）
 * @param {string} [config.verb] - 操作动词（默认 '绑定'，Skill 用 '启用'）
 * @param {Object} config.deps - 依赖
 * @param {import('vue').Ref<boolean>} config.deps.isVersionPreview - 是否为版本预览模式
 * @param {import('vue').Ref<boolean>} config.deps.bindingCatalogsLoaded - 目录是否已加载
 */
export function useBinding({
  limit,
  entityLabel,
  loadApi,
  filterPredicate,
  searchFields = ['name', 'displayName', 'description'],
  verb = '绑定',
  deps: { isVersionPreview, bindingCatalogsLoaded },
}) {
  const selectedIds = ref(new Set())
  const list = ref([])
  const searchText = ref('')
  const loading = ref(false)

  const selected = computed(() =>
    resolveBindingItems(selectedIds.value, list.value, {
      entityLabel,
      catalogReady: bindingCatalogsLoaded.value,
    })
  )

  const filteredList = computed(() => {
    let result = list.value
    if (filterPredicate) {
      result = result.filter(filterPredicate)
    }
    if (searchText.value) {
      const keyword = searchText.value.toLowerCase()
      result = result.filter(item =>
        searchFields.some(field => item[field]?.toLowerCase().includes(keyword))
      )
    }
    return result
  })

  function toggle(item) {
    if (isVersionPreview.value) return
    const id = toBindingId(item.id)
    const ids = new Set(selectedIds.value)
    if (ids.has(id)) {
      ids.delete(id)
    } else {
      if (ids.size >= limit) {
        message.warning(`每个 Agent 最多${verb} ${limit} 个${entityLabel}`)
        return
      }
      ids.add(id)
    }
    selectedIds.value = ids
  }

  function remove(id) {
    if (isVersionPreview.value) return
    const ids = new Set(selectedIds.value)
    ids.delete(toBindingId(id))
    selectedIds.value = ids
  }

  function clear() {
    if (isVersionPreview.value) return
    selectedIds.value = new Set()
  }

  async function load() {
    loading.value = true
    try {
      const res = await loadApi({ pageNum: 1, pageSize: 100 })
      list.value = (res.data?.records || []).map(item => ({ ...item, id: toBindingId(item.id) }))
    } catch (e) {
      // ignore
    } finally {
      loading.value = false
    }
  }

  return {
    selectedIds,
    list,
    searchText,
    loading,
    selected,
    filteredList,
    toggle,
    remove,
    clear,
    load,
  }
}
