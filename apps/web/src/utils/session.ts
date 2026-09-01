import { useAuthStore } from '@/stores/auth'
import { useProjectStore } from '@/stores/project'
import { useTrainingJobStore } from '@/stores/trainingJob'

export function clearUserSession() {
  const trainingJobStore = useTrainingJobStore()
  const projectStore = useProjectStore()
  const authStore = useAuthStore()

  trainingJobStore.resetOnLogout()
  projectStore.resetOnLogout()
  authStore.logout()
}
