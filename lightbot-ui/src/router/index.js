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
        path: 'graph',
        name: 'StandaloneGraph',
        component: () => import('../views/StandaloneGraph.vue'),
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
        redirect: '/eval/datasets',
      },
      {
        path: 'eval/datasets',
        name: 'EvalDatasets',
        component: () => import('../views/EvalDatasetManage.vue'),
      },
      {
        path: 'eval/datasets/:id',
        name: 'EvalDatasetDetail',
        component: () => import('../views/EvalDatasetDetail.vue'),
      },
      {
        path: 'eval/evaluators',
        name: 'Evaluators',
        component: () => import('../views/EvaluatorManage.vue'),
      },
      {
        path: 'eval/evaluators/:id',
        name: 'EvaluatorDetail',
        component: () => import('../views/EvaluatorDetail.vue'),
      },
      {
        path: 'eval/experiments',
        name: 'Experiments',
        component: () => import('../views/ExperimentManage.vue'),
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
        path: 'settings',
        name: 'Settings',
        component: () => import('../views/SettingsView.vue'),
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
