import { createRouter, createWebHistory } from 'vue-router'
import { getInitStatus } from '../api/auth'

const routes = [
  {
    path: '/',
    name: 'Landing',
    component: () => import('../views/Landing.vue'),
    meta: { public: true },
  },
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
    path: '/init',
    name: 'InitAdmin',
    component: () => import('../views/InitAdmin.vue'),
    meta: { public: true },
  },
  {
    path: '/app',
    component: () => import('../layouts/MainLayout.vue'),
    redirect: '/app/chat',
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
        path: 'graph',
        name: 'StandaloneGraph',
        component: () => import('../views/StandaloneGraph.vue'),
      },
      {
        path: 'model-providers',
        name: 'ModelProviders',
        component: () => import('../views/ModelProviderManage.vue'),
        meta: { requiresAdmin: true },
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
        path: 'workflow/:agentId',
        name: 'WorkflowEdit',
        component: () => import('../views/WorkflowEdit.vue'),
        meta: { hideSidebar: true },
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
        path: 'skills/:id',
        name: 'SkillDetail',
        component: () => import('../views/SkillDetail.vue'),
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
        path: 'observability',
        name: 'Observability',
        component: () => import('../views/Observability.vue'),
      },
      {
        path: 'observability/workflow-trace/:id',
        name: 'WorkflowTraceDetail',
        component: () => import('../views/WorkflowTraceDetail.vue'),
      },
      {
        path: 'tool-calls',
        name: 'ToolCallLog',
        component: () => import('../views/ToolCallLog.vue'),
      },
      {
        path: 'prompts',
        name: 'Prompts',
        component: () => import('../views/PromptManage.vue'),
      },
      {
        path: 'prompts/:id',
        name: 'PromptDetail',
        component: () => import('../views/PromptDetail.vue'),
      },
      {
        path: 'prompts/:promptKey/versions',
        name: 'PromptVersionHistory',
        component: () => import('../views/PromptVersionHistory.vue'),
      },
      {
        path: 'prompt-templates',
        name: 'PromptTemplates',
        component: () => import('../views/PromptTemplateManage.vue'),
      },
      {
        path: 'playground',
        name: 'Playground',
        component: () => import('../views/Playground.vue'),
      },
      {
        path: 'eval',
        name: 'Eval',
        component: () => import('../views/Eval.vue'),
      },
      {
        path: 'eval/datasets',
        redirect: '/app/eval',
      },
      {
        path: 'eval/datasets/:id',
        name: 'EvalDatasetDetail',
        component: () => import('../views/EvalDatasetDetail.vue'),
      },
      {
        path: 'eval/evaluators',
        redirect: '/app/eval',
      },
      {
        path: 'eval/evaluators/:id',
        name: 'EvaluatorDetail',
        component: () => import('../views/EvaluatorDetail.vue'),
      },
      {
        path: 'eval/experiments',
        redirect: '/app/eval',
      },
      {
        path: 'eval/experiments/:id',
        name: 'ExperimentDetail',
        component: () => import('../views/ExperimentDetail.vue'),
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
      {
        path: 'sessions',
        name: 'Sessions',
        component: () => import('../views/SessionManage.vue'),
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('../views/SettingsView.vue'),
        meta: { requiresAdmin: true },
      },
      {
        path: 'about',
        name: 'About',
        component: () => import('../views/AboutView.vue'),
      },
      {
        path: 'starred',
        name: 'Starred',
        component: () => import('../views/StarredMessages.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 初始化状态缓存，避免每次路由切换都请求
let initCheckDone = false
let systemInitialized = false

router.beforeEach(async (to, from, next) => {
  // 1. 检查系统是否已初始化（仅首次检查）
  if (!initCheckDone) {
    try {
      const res = await getInitStatus()
      systemInitialized = res.data?.initialized === true
    } catch {
      // 接口异常时放行，避免阻塞用户
      systemInitialized = true
    }
    initCheckDone = true
  }

  // 2. 未初始化时，强制跳转到初始化页面（除非已在初始化页面）
  if (!systemInitialized && to.path !== '/init') {
    return next('/init')
  }

  // 3. 已初始化时，不允许再访问初始化页面
  if (systemInitialized && to.path === '/init') {
    return next('/login')
  }

  // 4. 原有认证逻辑
  if (!to.meta.public && !localStorage.getItem('token')) {
    next('/login')
  } else if (to.meta.requiresAdmin && localStorage.getItem('role') !== 'admin') {
    next('/app/chat')
  } else {
    next()
  }
})

export default router
