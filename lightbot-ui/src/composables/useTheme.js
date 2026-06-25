import { ref, computed, watch, onMounted } from 'vue'
import { theme } from 'ant-design-vue'

const isDark = ref(false)

export function useTheme() {
  const themeConfig = computed(() => ({
    algorithm: isDark.value ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: isDark.value
      ? { colorBgContainer: '#1a1a1a', colorBgElevated: '#222222', colorBgLayout: '#111111', colorBorder: '#333333', colorText: '#e4e4e7', colorTextSecondary: '#a1a1aa' }
      : {},
  }))

  function toggleTheme() {
    isDark.value = !isDark.value
  }

  watch(isDark, (dark) => {
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light')
    localStorage.setItem('lightbot-theme', dark ? 'dark' : 'light')
  }, { immediate: true })

  return { isDark, themeConfig, toggleTheme }
}
