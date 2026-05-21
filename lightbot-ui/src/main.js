import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd, { message } from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'

import App from './App.vue'
import router from './router'

// 限制全局最多显示3条消息提示，防止堆叠
message.config({ maxCount: 3 })

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(Antd)
app.mount('#app')
