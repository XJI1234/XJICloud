import { createApp } from 'vue'
import App from './App.vue'
import router from './app/router'
import i18n from './app/i18n'
import { CONTAINER_KEY } from './shared/di'
import { createWeb2Container } from './app/create-container'
import { installContainer } from './app/runtime'
import './presentation/styles/shell.css'
import './presentation/styles/viewer-canvas.css'

const container = createWeb2Container()
installContainer(container)

const app = createApp(App)
app.provide(CONTAINER_KEY, container)
app.use(i18n)
app.use(router)
app.mount('#app')
