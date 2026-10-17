import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles/tokens.css'
import './styles/app.css'
import './styles/auth.css'

const app = createApp(App)
app.use(router)

router.isReady().then(() => {
  app.mount('#app')
})
