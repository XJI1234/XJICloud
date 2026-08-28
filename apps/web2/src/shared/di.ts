import type { InjectionKey } from 'vue'
import type { Web2Container } from '@/app/container.types'

export const CONTAINER_KEY: InjectionKey<Web2Container> = Symbol('web2-container')
