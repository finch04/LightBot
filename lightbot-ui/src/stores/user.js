import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi, getMe, initAdmin as initAdminApi } from '../api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref(null)
  /** 系统是否已初始化（有用户存在），null表示未检查 */
  const initialized = ref(null)

  async function login(form) {
    const res = await loginApi(form)
    token.value = res.data.token
    user.value = res.data.user
    localStorage.setItem('token', res.data.token)
    if (res.data.user?.role) {
      localStorage.setItem('role', res.data.user.role)
    }
    if (res.data.user.firstLogin) {
      localStorage.setItem('first-login', '1')
    }
    initialized.value = true
    return res
  }

  async function fetchUser() {
    const res = await getMe()
    user.value = res.data
    if (res.data?.role) {
      localStorage.setItem('role', res.data.role)
    }
  }

  async function logout() {
    try {
      await logoutApi()
    } catch (e) {
      // ignore
    }
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('role')
  }

  async function initAdmin(form) {
    const res = await initAdminApi(form)
    token.value = res.data.token
    user.value = res.data.user
    localStorage.setItem('token', res.data.token)
    if (res.data.user?.role) {
      localStorage.setItem('role', res.data.user.role)
    }
    initialized.value = true
    return res
  }

  return { token, user, initialized, login, fetchUser, logout, initAdmin }
})
