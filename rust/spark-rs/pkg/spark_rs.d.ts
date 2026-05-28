/* tslint:disable */
/* eslint-disable */

export function decode_rad_header(bytes: Uint8Array): any;

export function get_raycast_buffer(): Uint32Array;

export function get_raycast_buffer2(): Uint32Array;

export function raycast_ext_buffers(origin_x: number, origin_y: number, origin_z: number, dir_x: number, dir_y: number, dir_z: number, min_opacity: number, near: number, far: number, count: number): Float32Array;

export function raycast_packed_buffer(origin_x: number, origin_y: number, origin_z: number, dir_x: number, dir_y: number, dir_z: number, min_opacity: number, near: number, far: number, count: number, ln_scale_min: number, ln_scale_max: number, lod_opacity: boolean): Float32Array;

export function raycast_packed_splats(origin_x: number, origin_y: number, origin_z: number, dir_x: number, dir_y: number, dir_z: number, min_opacity: number, near: number, far: number, num_splats: number, packed_splats: Uint32Array, ln_scale_min: number, ln_scale_max: number, lod_opacity: boolean): Float32Array;

export function simd_enabled(): boolean;

export function wasm_start(): void;

export type InitInput = RequestInfo | URL | Response | BufferSource | WebAssembly.Module;

export interface InitOutput {
    readonly memory: WebAssembly.Memory;
    readonly decode_rad_header: (a: any) => [number, number, number];
    readonly get_raycast_buffer: () => any;
    readonly get_raycast_buffer2: () => any;
    readonly raycast_ext_buffers: (a: number, b: number, c: number, d: number, e: number, f: number, g: number, h: number, i: number, j: number) => any;
    readonly raycast_packed_buffer: (a: number, b: number, c: number, d: number, e: number, f: number, g: number, h: number, i: number, j: number, k: number, l: number, m: number) => any;
    readonly raycast_packed_splats: (a: number, b: number, c: number, d: number, e: number, f: number, g: number, h: number, i: number, j: number, k: any, l: number, m: number, n: number) => any;
    readonly simd_enabled: () => number;
    readonly wasm_start: () => void;
    readonly __wbindgen_free: (a: number, b: number, c: number) => void;
    readonly __wbindgen_exn_store: (a: number) => void;
    readonly __externref_table_alloc: () => number;
    readonly __wbindgen_externrefs: WebAssembly.Table;
    readonly __wbindgen_malloc: (a: number, b: number) => number;
    readonly __wbindgen_realloc: (a: number, b: number, c: number, d: number) => number;
    readonly __externref_table_dealloc: (a: number) => void;
    readonly __wbindgen_start: () => void;
}

export type SyncInitInput = BufferSource | WebAssembly.Module;

/**
 * Instantiates the given `module`, which can either be bytes or
 * a precompiled `WebAssembly.Module`.
 *
 * @param {{ module: SyncInitInput }} module - Passing `SyncInitInput` directly is deprecated.
 *
 * @returns {InitOutput}
 */
export function initSync(module: { module: SyncInitInput } | SyncInitInput): InitOutput;

/**
 * If `module_or_path` is {RequestInfo} or {URL}, makes a request and
 * for everything else, calls `WebAssembly.instantiate` directly.
 *
 * @param {{ module_or_path: InitInput | Promise<InitInput> }} module_or_path - Passing `InitInput` directly is deprecated.
 *
 * @returns {Promise<InitOutput>}
 */
export default function __wbg_init (module_or_path?: { module_or_path: InitInput | Promise<InitInput> } | InitInput | Promise<InitInput>): Promise<InitOutput>;
