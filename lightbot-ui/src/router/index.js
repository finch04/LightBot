import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    redirect: '/chat',
    children: [
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('../views/Chat.vue'),
      },
      {
        path: 'chat/:sessionId',
        name: 'ChatSession',
        component: () => import('../views/Chat.vue'),
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('../views/Knowledge.vue'),
      },
      {
        path: 'knowledge/:id',
        name: 'KnowledgeDetail',
        component: () => import('../views/KnowledgeDetail.vue'),
      },
      {
        path: 'model-providers',
        name: 'ModelProviders',
        component: () => import('../views/ModelProviderManage.vue'),
      },
      {
        path: 'agents',
        name: 'Agents',
        component: () => import('../views/AgentManage.vue'),
      },
      {
        path: 'agents/:id',
        name: 'AgentDetail',
        component: () => import('../views/AgentDetail.vue'),
      },
      {
        path: 'mcp',
        name: 'MCP',
        component: () => import('../views/McpManage.vue'),
      },
      {
        path: 'skills',
        name: 'Skills',
        component: () => import('../views/SkillManage.vue'),
      },
      {
        path: 'tools',
        name: 'Tools',
        component: () => import('../views/ToolManage.vue'),
      },
      {
        path: 'extensions',
        name: 'Extensions',
        component: () => import('../views/Extensions.vue'),
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/DashboardView.vue'),
      },
      {
        path: 'logs',
        name: 'Logs',
        component: () => import('../views/LogMonitor.vue'),
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/ProfileView.vue'),
      },
      {
        path: 'tasks',
        name: 'Tasks',
        component: () => import('../views/TaskCenter.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  if (!to.meta.public && !localStorage.getItem('token')) {
    next('/login')
  } else {
    next()
  }
})

export default router
