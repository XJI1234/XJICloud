import { useAuthStore } from '@/domains/identity/stores/auth'
import { useProjectStore } from '@/domains/project/stores/project'
import { useTrainingJobStore } from '@/domains/training/stores/trainingJob'

export function clearUserSession() {
  const trainingJobStore = useTrainingJobStore()
  const projectStore = useProjectStore()
  const authStore = useAuthStore()

  trainingJobStore.resetOnLogout()
  projectStore.resetOnLogout()
  authStore.logout()
}
