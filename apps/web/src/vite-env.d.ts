/// <reference types="vite/client" />
/// <reference types="vite-plugin-glsl/ext" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  /** Optional absolute origin for Wayline iframe, e.g. http://192.168.63.129 */
  readonly VITE_WAYLINE_ORIGIN?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

declare module '*.svg' {
  const src: string
  export default src
}

declare module '*.glb' {
  const src: string
  export default src
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
