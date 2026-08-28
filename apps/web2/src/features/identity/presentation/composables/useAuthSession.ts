import { inject } from 'vue'
import { CONTAINER_KEY } from '@/shared/di'
import {
  loginUseCase,
  logoutUseCase,
  probeNeedCaptchaUseCase,
  registerUseCase,
} from '../../application/use-cases/auth.usecase'
import { isAuthenticated } from '../../domain/entities/user-session.entity'

export function useAuthSession() {
  const container = inject(CONTAINER_KEY)!
  const { auth, session, resetWorkspace } = container

  return {
    session: () => session.read(),
    isAuthenticated: () => isAuthenticated(session.read()),
    login: (input: Parameters<typeof loginUseCase>[1]) => loginUseCase({ auth, session }, input),
    register: (input: Parameters<typeof registerUseCase>[1]) => registerUseCase({ auth, session }, input),
    logout: () => logoutUseCase({ session, onWorkspaceReset: resetWorkspace }),
    probeNeedCaptcha: (username: string) => probeNeedCaptchaUseCase({ auth }, username),
    getCaptcha: () => auth.getCaptcha(),
  }
}
