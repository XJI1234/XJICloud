/// <reference types="vite/client" />
/// <reference types="vite-plugin-glsl/ext" />

declare module '*.svg' {
  const src: string
  export default src
}

declare module '*.glb' {
  const src: string
  export default src
}
