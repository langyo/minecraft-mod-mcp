import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App'
import i18n from './locales'
import router from './router'
import './styles/base.scss'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)

document.documentElement.lang = i18n.global.locale.value

app.mount('#app')
