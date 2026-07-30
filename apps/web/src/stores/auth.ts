import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import { setToken } from '@/api/client'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(localStorage.getItem('xjicloud_token'))
  const userId = ref<string | null>(localStorage.getItem('xjicloud_user_id'))
  const username = ref<string | null>(localStorage.getItem('xjicloud_username'))
  const displayName = ref<string | null>(localStorage.getItem('xjicloud_display_name'))

  const isAuthenticated = computed(() => Boolean(accessToken.value))

  function persistSession(response: authApi.AuthResponse) {
    accessToken.value = response.accessToken
    userId.value = response.userId
    username.value = response.username
    displayName.value = response.displayName
    setToken(response.accessToken)
    localStorage.setItem('xjicloud_user_id', response.userId)
    localStorage.setItem('xjicloud_username', response.username)
    localStorage.setItem('xjicloud_display_name', response.displayName)
  }

  async function login(loginUsername: string, password: string) {
    const response = await authApi.login(loginUsername, password)
    persistSession(response)
    return response
  }

  async function register(loginUsername: string, password: string, name?: string) {
    const response = await authApi.register(loginUsername, password, name)
    persistSession(response)
    return response
  }

  function logout() {
    accessToken.value = null
    userId.value = null
    username.value = null
    displayName.value = null
    setToken(null)
    localStorage.removeItem('xjicloud_user_id')
    localStorage.removeItem('xjicloud_username')
    localStorage.removeItem('xjicloud_display_name')
  }

  return {
    accessToken,
    userId,
    username,
    displayName,
    isAuthenticated,
    login,
    register,
    logout,
  }
})
