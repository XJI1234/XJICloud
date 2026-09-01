import type { Web2Container } from './container.types'

let container: Web2Container | undefined

export function installContainer(next: Web2Container) {
  container = next
}

export function getContainer(): Web2Container {
  if (!container) {
    throw new Error('Web2 container is not installed')
  }
  return container
}
