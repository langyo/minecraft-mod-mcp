import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView'),
    },
    {
      path: '/accounts',
      name: 'accounts',
      component: () => import('@/views/AccountsView'),
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/SettingsView'),
    },
    {
      path: '/install',
      name: 'install',
      component: () => import('@/views/InstallView'),
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

export default router
