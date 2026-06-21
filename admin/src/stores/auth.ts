import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { adminLogin, setAdminToken, ApiError } from '@/api/adminClient'

export const useAdminAuthStore = defineStore('adminAuth', () => {
  const token = ref(localStorage.getItem('xjicloud_admin_token'))
  const username = ref(localStorage.getItem('xjicloud_admin_username'))

  const isAuthenticated = computed(() => Boolean(token.value))

  async function login(user: string, password: string) {
    const response = await adminLogin(user, password)
    token.value = response.token
    username.value = response.username
    setAdminToken(response.token)
    localStorage.setItem('xjicloud_admin_username', response.username)
  }

  function logout() {
    token.value = null
    username.value = null
    setAdminToken(null)
    localStorage.removeItem('xjicloud_admin_username')
  }

  return { token, username, isAuthenticated, login, logout }
})

export { ApiError }
