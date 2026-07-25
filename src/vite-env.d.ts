/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

declare module '*.glsl' {
  const source: string
  export default source
}

declare module '*.gmdb' {
  const url: string
  export default url
}

declare module '*.wasm' {
  const url: string
  export default url
}
