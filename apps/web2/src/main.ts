import { createApp } from 'vue'
import App from './App.vue'
import router from './app/router'
import i18n from './app/i18n'
import { CONTAINER_KEY } from './shared/di'
import { createWeb2Container } from './app/create-container'
import { installContainer } from './app/runtime'
import { enterLocalDebugUseCase } from './features/identity/application/use-cases/auth.usecase'
import { isAuthenticated } from './features/identity/domain/entities/user-session.entity'
import './presentation/styles/shell.css'
import './presentation/styles/viewer-canvas.css'

const container = createWeb2Container({
  onUnauthorized() {
    if (typeof window === 'undefined') {
      return
    }
    if (window.location.pathname.startsWith('/login')) {
      return
    }
    window.location.assign('/login')
  },
})
if (import.meta.env.DEV && !isAuthenticated(container.session.read())) {
  enterLocalDebugUseCase(container.session, 'local')
}
installContainer(container)

const app = createApp(App)
app.provide(CONTAINER_KEY, container)
app.use(i18n)
app.use(router)
app.mount('#app')
