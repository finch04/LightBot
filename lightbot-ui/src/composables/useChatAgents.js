import { ref, computed } from 'vue'
import { getAgents, getAgentDetail, getAgentChatCapabilities, listAgentVersions } from '../api/agent'
import {
  buildUploadHint,
  buildFileAcceptTypes,
} from '../utils/chatAttachment'

/**
 * Chat 页面 Agent 管理 composable
 * 管理 Agent 列表、选择、版本、聊天能力
 */
export function useChatAgents({ sessionId, loading, pendingAttachments, voiceListening, stopVoiceInput }) {
  const agents = ref([])
  const selectedAgentId = ref(null)
  const currentAgent = ref(null)
  const chatCapabilities = ref({})
  const selectedConfigVersion = ref(0)
  const configVersionOptions = ref([])

  // ===== Computed =====
  const showFileUploadBtn = computed(() => {
    const c = chatCapabilities.value
    if (!c) return false
    return Boolean(c.allowFileUpload || c.allowDocumentUpload || c.allowMediaUpload)
  })

  const showVoiceInputBtn = computed(() =>
    Boolean(chatCapabilities.value?.multimodalEnabled && chatCapabilities.value?.enableAudioInput))

  const showTtsBtn = computed(() => Boolean(chatCapabilities.value?.enableTts))

  const fileAcceptTypes = computed(() => buildFileAcceptTypes(chatCapabilities.value))
  const fileUploadHint = computed(() => buildUploadHint(chatCapabilities.value))

  const currentWelcomeMessage = computed(() => {
    if (currentAgent.value?.welcomeMessage) {
      return currentAgent.value.welcomeMessage
    }
    return '## 你好，我是 LightBot\n有什么可以帮你的？'
  })

  const currentRecommendedQuestions = computed(() => {
    if (currentAgent.value?.recommendedQuestions) {
      try {
        const questions = typeof currentAgent.value.recommendedQuestions === 'string'
          ? JSON.parse(currentAgent.value.recommendedQuestions)
          : currentAgent.value.recommendedQuestions
        return Array.isArray(questions) ? questions : []
      } catch { return [] }
    }
    return []
  })

  // ===== Functions =====
  function handleAgentSelect({ key }) {
    selectedAgentId.value = key
    pendingAttachments.value = []
    loadCurrentAgent(key)
    loadAgentConfigVersions(key)
  }

  async function loadChatCapabilities(agentId, configVersion) {
    if (!agentId) {
      chatCapabilities.value = {}
      return
    }
    try {
      const res = await getAgentChatCapabilities(agentId, configVersion ?? 0)
      chatCapabilities.value = res.data || {}
    } catch {
      chatCapabilities.value = {}
    }
    if (!showFileUploadBtn.value) {
      pendingAttachments.value = []
    }
    if (!showVoiceInputBtn.value && voiceListening.value) {
      stopVoiceInput()
    }
  }

  async function onConfigVersionChange(version) {
    if (loading.value) return
    selectedConfigVersion.value = version
    if (selectedAgentId.value) {
      await loadChatCapabilities(selectedAgentId.value, version)
    }
  }

  async function loadAgentConfigVersions(agentId) {
    if (!agentId) {
      configVersionOptions.value = []
      selectedConfigVersion.value = 0
      return
    }
    try {
      const opts = [{
        value: 0,
        versionLabel: '暂存草稿',
        selectLabel: '暂存草稿',
        badge: 'draft',
      }]
      const res = await listAgentVersions(agentId)
      const versions = res.data || []
      for (const v of versions) {
        const num = v.version
        if (num == null || num <= 0) continue
        const ver = `v${num}`
        opts.push({
          value: num,
          versionLabel: ver,
          selectLabel: v.current ? `${ver} · 线上` : ver,
          badge: v.current ? 'online' : null,
        })
      }
      configVersionOptions.value = opts
      const currentPublished = versions.find(v => v.current)
      selectedConfigVersion.value = currentPublished?.version ?? 0
      await loadChatCapabilities(agentId, selectedConfigVersion.value)
    } catch {
      configVersionOptions.value = [{
        value: 0,
        versionLabel: '暂存草稿',
        selectLabel: '暂存草稿',
        badge: 'draft',
      }]
      selectedConfigVersion.value = 0
      await loadChatCapabilities(agentId, 0)
    }
  }

  async function loadCurrentAgent(agentId) {
    if (!agentId) {
      currentAgent.value = null
      chatCapabilities.value = {}
      return
    }
    try {
      const res = await getAgentDetail(agentId)
      currentAgent.value = res.data?.agent || null
      await loadChatCapabilities(agentId, selectedConfigVersion.value)
    } catch {
      currentAgent.value = null
      chatCapabilities.value = {}
    }
  }

  async function loadAgents(preferredAgentId) {
    try {
      const res = await getAgents({ pageNum: 1, pageSize: 100 })
      agents.value = res.data.records || []

      // 新对话时选中 Agent：优先 URL 指定，否则默认 Agent
      if (!sessionId.value) {
        if (preferredAgentId) {
          selectedAgentId.value = String(preferredAgentId)
          await loadAgentConfigVersions(preferredAgentId)
        } else {
          const defaultAgent = agents.value.find(a => a.isDefault)
          if (defaultAgent) {
            selectedAgentId.value = String(defaultAgent.id)
          } else if (agents.value.length > 0) {
            selectedAgentId.value = String(agents.value[0].id)
          }
          if (selectedAgentId.value) {
            await loadAgentConfigVersions(selectedAgentId.value)
          }
        }
      }
    } catch (e) {
      // ignore
    }
  }

  function agentVersionLabel(a) {
    const status = a.status
    const ver = a.version
    if (status === 'published' && ver > 0) return `v${ver}`
    if (status === 'published_editing' && ver > 0) return `v${ver}·编辑中`
    if (status === 'draft') return '草稿'
    return ''
  }

  return {
    agents,
    selectedAgentId,
    currentAgent,
    chatCapabilities,
    selectedConfigVersion,
    configVersionOptions,
    showFileUploadBtn,
    showVoiceInputBtn,
    showTtsBtn,
    fileAcceptTypes,
    fileUploadHint,
    currentWelcomeMessage,
    currentRecommendedQuestions,
    handleAgentSelect,
    loadChatCapabilities,
    onConfigVersionChange,
    loadAgentConfigVersions,
    loadCurrentAgent,
    loadAgents,
    agentVersionLabel,
  }
}
