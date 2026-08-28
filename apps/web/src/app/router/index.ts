import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/domains/identity/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/domains/identity/pages/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      redirect: () => (useAuthStore().isAuthenticated ? '/app/home' : '/login'),
    },
    {
      path: '/app',
      component: () => import('@/app/layouts/CloudLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: { name: 'home' },
        },
        {
          path: 'home',
          name: 'home',
          component: () => import('@/domains/project/pages/HomeView.vue'),
        },
        {
          path: 'projects',
          name: 'projects',
          component: () => import('@/domains/project/pages/ProjectListView.vue'),
        },
        {
          path: 'upload',
          name: 'upload',
          component: () => import('@/domains/training/pages/UploadView.vue'),
        },
        {
          path: 'layer',
          name: 'layer',
          component: () => import('@/domains/viewer/pages/LayerViewerView.vue'),
          meta: { transition: 'cloud-fade' },
        },
        {
          path: 'supersplat',
          name: 'supersplat',
          component: () => import('@/domains/editor/pages/SuperSplatEditorView.vue'),
          meta: { immersive: true },
        },
        {
          path: 'help',
          name: 'help',
          component: () => import('@/domains/support/pages/HelpView.vue'),
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
