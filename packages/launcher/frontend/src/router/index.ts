import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: () => import('@/views/HomeView'),
    },
    {
      path: '/mcp',
      name: 'mcp',
      component: () => import('@/views/MCPMonitorView'),
    },
    {
      path: '/versions',
      name: 'versions',
      component: () => import('@/views/InstallView'),
    },
    {
      path: '/accounts',
      name: 'accounts',
      component: () => import('@/views/AccountsView'),
    },
    {
      path: '/vm',
      name: 'vm',
      component: () => import('@/views/SettingsView'),
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

export default router
