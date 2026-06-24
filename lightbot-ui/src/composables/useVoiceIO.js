import { nextTick } from 'vue'
import { message } from 'ant-design-vue'

/**
 * Chat 页面语音 I/O composable
 * 管理语音识别（输入）和语音合成（TTS 输出）
 *
 * 注意：voiceListening, speakingMsgKey 由外部创建并传入
 */
export function useVoiceIO({
  input, inputRef, chatCapabilities, autoResize,
  voiceListening, speakingMsgKey,
}) {
  let speechRecognition = null

  // ===== 语音识别 =====
  function toggleVoiceInput() {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition
    if (!SR) {
      message.warning('当前浏览器不支持语音识别，请使用 Chrome/Edge')
      return
    }
    if (voiceListening.value && speechRecognition) {
      stopVoiceInput()
      return
    }
    const voiceInputBase = input.value
    speechRecognition = new SR()
    speechRecognition.lang = 'zh-CN'
    speechRecognition.interimResults = true
    speechRecognition.continuous = true
    speechRecognition.onstart = () => { voiceListening.value = true }
    speechRecognition.onend = () => { stopVoiceInput() }
    speechRecognition.onerror = () => { stopVoiceInput() }
    speechRecognition.onresult = (event) => {
      let finalText = ''
      let interimText = ''
      for (let i = 0; i < event.results.length; i++) {
        const part = event.results[i][0].transcript
        if (event.results[i].isFinal) {
          finalText += part
        } else {
          interimText += part
        }
      }
      const merged = `${voiceInputBase}${voiceInputBase && finalText ? ' ' : ''}${finalText}${interimText}`
      input.value = merged.trim() ? merged : voiceInputBase
      autoResize()
      nextTick(() => inputRef.value?.focus())
    }
    speechRecognition.start()
  }

  function stopVoiceInput() {
    voiceListening.value = false
    try {
      speechRecognition?.stop()
    } catch {
      /* ignore */
    }
  }

  // ===== TTS 语音合成 =====
  function messagePlainText(content) {
    if (!content) return ''
    return content
      .replace(/```[\s\S]*?```/g, ' ')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/[#*_~>[\]()!]/g, '')
      .replace(/\s+/g, ' ')
      .trim()
  }

  function speakMessage(msg, index) {
    if (!chatCapabilities.value?.enableTts) return
    const text = messagePlainText(msg.content)
    if (!text) return
    if (!window.speechSynthesis) {
      message.warning('当前浏览器不支持语音朗读')
      return
    }
    if (speakingMsgKey.value === index) {
      window.speechSynthesis.cancel()
      speakingMsgKey.value = null
      return
    }
    window.speechSynthesis.cancel()
    const utter = new SpeechSynthesisUtterance(text)
    utter.lang = 'zh-CN'
    utter.rate = 1
    speakingMsgKey.value = index
    utter.onend = () => { speakingMsgKey.value = null }
    utter.onerror = () => { speakingMsgKey.value = null }
    window.speechSynthesis.speak(utter)
  }

  function cleanup() {
    window.speechSynthesis?.cancel()
    stopVoiceInput()
  }

  return {
    toggleVoiceInput,
    stopVoiceInput,
    speakMessage,
    messagePlainText,
    cleanup,
  }
}
