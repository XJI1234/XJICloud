import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      redirect: () => (useAuthStore().isAuthenticated ? '/app/layer' : '/login'),
    },
    {
      path: '/app',
      component: () => import('@/layouts/CloudLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'projects',
          name: 'projects',
          component: () => import('@/views/ProjectListView.vue'),
        },
        {
          path: 'layer',
          name: 'layer',
          component: () => import('@/views/LayerViewerView.vue'),
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore()
  if (to.meta.public) {
    if (authStore.isAuthenticated && to.name === 'login') {
      return { name: 'layer' }
    }
    return true
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  return true
})

export default router
