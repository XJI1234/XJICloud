import { createRouter, createWebHistory } from 'vue-router'
import { getContainer } from '@/app/runtime'
import { isAuthenticated } from '@/features/identity/domain/entities/user-session.entity'
import { resolveAuthNavigation } from '@/features/identity/application/use-cases/auth.usecase'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/features/identity/presentation/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      redirect: () => (isAuthenticated(getContainer().session.read()) ? '/app/home' : '/login'),
    },
    {
      path: '/app',
      component: () => import('@/presentation/layouts/CloudLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: { name: 'home' } },
        { path: 'home', name: 'home', component: () => import('@/features/project/presentation/HomeView.vue') },
        { path: 'projects', name: 'projects', component: () => import('@/features/project/presentation/ProjectListView.vue') },
        { path: 'upload', name: 'upload', component: () => import('@/features/dataset-training/presentation/UploadView.vue') },
        {
          path: 'layer',
          name: 'layer',
          component: () => import('@/features/viewer/presentation/LayerViewerView.vue'),
          meta: { transition: 'app-fade' },
        },
        {
          path: 'supersplat',
          name: 'supersplat',
          component: () => import('@/features/editor/presentation/SuperSplatEditorView.vue'),
          meta: { immersive: true },
        },
        {
          path: 'wayline',
          name: 'wayline',
          component: () => import('@/features/wayline/presentation/WaylineLandingView.vue'),
        },
        {
          path: 'wayline/editor',
          name: 'wayline-editor',
          component: () => import('@/features/wayline/presentation/WaylineEditorView.vue'),
          meta: { immersive: true },
        },
        { path: 'help', name: 'help', component: () => import('@/presentation/views/HelpView.vue') },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const session = getContainer().session.read()
  const redirect = resolveAuthNavigation({
    isAuthenticated: isAuthenticated(session),
    isPublicLogin: Boolean(to.meta.public && to.name === 'login'),
    requiresAuth: Boolean(to.meta.requiresAuth),
    fullPath: to.fullPath,
  })
  return redirect ?? true
})

export default router
