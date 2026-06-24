import { reactive, provide } from 'vue'

/**
 * Chat 页面 Ask User 弹窗 composable
 * 管理 AI 向用户提问的交互式弹窗
 *
 * 注意：submitAskUserResponse 需要调用 runChatStream（定义在 Chat.vue），
 * 因此由 Chat.vue 定义并传入 provide，避免循环依赖
 */
export function useAskUser({ messages }) {
  const askUserModal = reactive({
    visible: false,
    question: '',
    options: [],
    isOpenEnded: false,
    messageIndex: -1,
    freeText: '',
  })

  function findAskUserEvent(msg) {
    if (!msg?._toolEvents?.length) return null
    for (let i = msg._toolEvents.length - 1; i >= 0; i--) {
      const evt = msg._toolEvents[i]
      if (evt.type === 'tool_result' && evt.toolName === 'ask_user') {
        try {
          const parsed = JSON.parse(evt.result)
          if (parsed && typeof parsed === 'object' && parsed.question) return parsed
        } catch { /* ignore */ }
      }
    }
    return null
  }

  function isAskUserUnanswered(msgIndex) {
    const msg = messages.value[msgIndex]
    if (!msg || msg.role !== 'assistant') return false
    if (!findAskUserEvent(msg)) return false
    for (let i = msgIndex + 1; i < messages.value.length; i++) {
      if (messages.value[i].role === 'user') return false
    }
    return true
  }

  function showAskUserModal(msgIndex) {
    const msg = messages.value[msgIndex]
    const askData = findAskUserEvent(msg)
    if (!askData) return
    askUserModal.question = askData.question
    askUserModal.options = askData.options || []
    askUserModal.isOpenEnded = askData.is_open_ended === true
    askUserModal.messageIndex = msgIndex
    askUserModal.freeText = ''
    askUserModal.visible = true
  }

  // provide 给深层组件（如 AskUserResult.vue）
  provide('showAskUserModal', showAskUserModal)
  provide('isAskUserUnanswered', isAskUserUnanswered)

  return {
    askUserModal,
    findAskUserEvent,
    isAskUserUnanswered,
    showAskUserModal,
  }
}
