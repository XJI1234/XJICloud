import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { setOnUnauthorized } from '@/api/client'
import { clearUserSession } from '@/utils/session'
import './styles/main.css'
import './styles/cloud.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(i18n)
app.use(router)

setOnUnauthorized(() => {
  clearUserSession()
  void router.replace({ name: 'login' })
})

app.mount('#app')
