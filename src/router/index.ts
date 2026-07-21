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
      redirect: () => (useAuthStore().isAuthenticated ? '/app/home' : '/login'),
    },
    {
      path: '/app',
      component: () => import('@/layouts/CloudLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: { name: 'home' },
        },
        {
          path: 'home',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
        },
        {
          path: 'projects',
          name: 'projects',
          component: () => import('@/views/ProjectListView.vue'),
        },
        {
          path: 'upload',
          name: 'upload',
          component: () => import('@/views/UploadView.vue'),
        },
        {
          path: 'layer',
          name: 'layer',
          component: () => import('@/modules/viewer/LayerViewerView.vue'),
          meta: { transition: 'cloud-fade' },
        },
        {
          path: 'supersplat',
          name: 'supersplat',
          component: () => import('@/views/SuperSplatEditorView.vue'),
          meta: { immersive: true },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore()
  if (to.meta.public) {
    if (authStore.isAuthenticated && to.name === 'login') {
      return { name: 'home' }
    }
    return true
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  return true
})

export default router
