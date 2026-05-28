/* tslint:disable */
/* eslint-disable */

export class ChunkDecoder {
    private constructor();
    free(): void;
    [Symbol.dispose](): void;
    finish(): any;
    push(bytes: Uint8Array): void;
}

export class CsplatArray {
    private constructor();
    free(): void;
    [Symbol.dispose](): void;
    bhatt_lod(lod_base: number): void;
    has_lod(): boolean;
    inject_rgba8(rgba: Uint8Array): void;
    len(): number;
    tiny_lod(lod_base: number, merge_filter: boolean): void;
    to_extsplats(): object;
    to_extsplats_lod(): object;
    to_packedsplats(): object;
    to_packedsplats_lod(): object;
    maxShDegree: number;
    numSplats: number;
}

export class GsplatArray {
    private constructor();
    free(): void;
    [Symbol.dispose](): void;
    bhatt_lod(lod_base: number): void;
    has_lod(): boolean;
    inject_rgba8(rgba: Uint8Array): void;
    len(): number;
    tiny_lod(lod_base: number, merge_filter: boolean): void;
    to_extsplats(): object;
    to_extsplats_lod(): object;
    to_packedsplats(encoding: any): object;
    to_packedsplats_lod(encoding: any): object;
    maxShDegree: number;
    numSplats: number;
}

export function bhatt_lod_extsplats(num_splats: number, ext1: Uint32Array, ext2: Uint32Array, extra: object | null | undefined, lod_base: number, rgba?: Uint8Array | null): object;

export function bhatt_lod_packedsplats(num_splats: number, packed: Uint32Array, extra: object | null | undefined, lod_base: number, rgba: Uint8Array | null | undefined, encoding: any): object;

export function decode_to_csplatarray(file_type: string | null | undefined, path_name: string | null | undefined, encoding: any): ChunkDecoder;

export function decode_to_extsplats(file_type?: string | null, path_name?: string | null, sh1_codes?: Uint32Array | null, sh2_codes?: Uint32Array | null, sh3_codes?: Array<any> | null): ChunkDecoder;

export function decode_to_gsplatarray(file_type?: string | null, path_name?: string | null): ChunkDecoder;

export function decode_to_packedsplats(file_type: string | null | undefined, path_name: string | null | undefined, encoding: any, sh1_codes?: Uint32Array | null, sh2_codes?: Uint32Array | null, sh3_codes?: Uint32Array | null): ChunkDecoder;

export function dispose_lod_tree(lod_id: number): void;

export function extsplats_to_gsplatarray(num_splats: number, ext1: Uint32Array, ext2: Uint32Array, extra?: object | null): GsplatArray;

export function get_lod_tree_level(lod_id: number, level: number): object;

export function init_lod_tree(num_splats: number, lod_tree: Uint32Array): object;

export function new_lod_tree(capacity: number): object;

export function new_shared_lod_tree(orig_lod_id: number): object;

export function packedsplats_to_csplatarray(num_splats: number, packed: Uint32Array, extra: object | null | undefined, encoding: any): CsplatArray;

export function packedsplats_to_gsplatarray(num_splats: number, packed: Uint32Array, extra: object | null | undefined, encoding: any): GsplatArray;

export function simd_enabled(): boolean;

export function sort32_splats(num_splats: number, readback: Uint32Array, ordering: Uint32Array): number;

export function sort_splats(num_splats: number, readback: Uint16Array, ordering: Uint32Array): number;

export function tiny_lod_extsplats(num_splats: number, ext1: Uint32Array, ext2: Uint32Array, extra: object | null | undefined, lod_base: number, merge_filter: boolean, rgba?: Uint8Array | null): object;

export function tiny_lod_packedsplats(num_splats: number, packed: Uint32Array, extra: object | null | undefined, lod_base: number, merge_filter: boolean, rgba: Uint8Array | null | undefined, encoding: any): object;

export function traverse_lod_trees(max_splats: number, pixel_scale_limit: number, _last_pixel_limit: number | null | undefined, lod_ids: Uint32Array, root_pages: Uint32Array, view_to_objects: Float32Array, lod_scales: Float32Array, behind_foveates: Float32Array, cone_foveates: Float32Array, cone_fov0s: Float32Array, cone_fovs: Float32Array): object;

export function update_lod_trees(lod_ids: Uint32Array, page_bases: Uint32Array, chunk_bases: Uint32Array, counts: Uint32Array, lod_trees: Array<any>): object;

export function wasm_start(): void;

export type InitInput = RequestInfo | URL | Response | BufferSource | WebAssembly.Module;

export interface InitOutput {
    readonly memory: WebAssembly.Memory;
    readonly __wbg_chunkdecoder_free: (a: number, b: number) => void;
    readonly __wbg_csplatarray_free: (a: number, b: number) => void;
    readonly __wbg_get_csplatarray_maxShDegree: (a: number) => number;
    readonly __wbg_get_csplatarray_numSplats: (a: number) => number;
    readonly __wbg_get_gsplatarray_maxShDegree: (a: number) => number;
    readonly __wbg_get_gsplatarray_numSplats: (a: number) => number;
    readonly __wbg_gsplatarray_free: (a: number, b: number) => void;
    readonly __wbg_set_csplatarray_maxShDegree: (a: number, b: number) => void;
    readonly __wbg_set_csplatarray_numSplats: (a: number, b: number) => void;
    readonly __wbg_set_gsplatarray_maxShDegree: (a: number, b: number) => void;
    readonly __wbg_set_gsplatarray_numSplats: (a: number, b: number) => void;
    readonly bhatt_lod_extsplats: (a: number, b: any, c: any, d: number, e: number, f: number) => [number, number, number];
    readonly bhatt_lod_packedsplats: (a: number, b: any, c: number, d: number, e: number, f: any) => [number, number, number];
    readonly chunkdecoder_finish: (a: number) => [number, number, number];
    readonly chunkdecoder_push: (a: number, b: any) => [number, number];
    readonly csplatarray_bhatt_lod: (a: number, b: number) => void;
    readonly csplatarray_has_lod: (a: number) => number;
    readonly csplatarray_inject_rgba8: (a: number, b: any) => void;
    readonly csplatarray_len: (a: number) => number;
    readonly csplatarray_tiny_lod: (a: number, b: number, c: number) => void;
    readonly csplatarray_to_extsplats: (a: number) => [number, number, number];
    readonly csplatarray_to_extsplats_lod: (a: number) => [number, number, number];
    readonly csplatarray_to_packedsplats: (a: number) => [number, number, number];
    readonly csplatarray_to_packedsplats_lod: (a: number) => [number, number, number];
    readonly decode_to_csplatarray: (a: number, b: number, c: number, d: number, e: any) => [number, number, number];
    readonly decode_to_extsplats: (a: number, b: number, c: number, d: number, e: number, f: number, g: number) => [number, number, number];
    readonly decode_to_gsplatarray: (a: number, b: number, c: number, d: number) => [number, number, number];
    readonly decode_to_packedsplats: (a: number, b: number, c: number, d: number, e: any, f: number, g: number, h: number) => [number, number, number];
    readonly dispose_lod_tree: (a: number) => void;
    readonly extsplats_to_gsplatarray: (a: number, b: any, c: any, d: number) => [number, number, number];
    readonly get_lod_tree_level: (a: number, b: number) => [number, number, number];
    readonly gsplatarray_bhatt_lod: (a: number, b: number) => void;
    readonly gsplatarray_inject_rgba8: (a: number, b: any) => void;
    readonly gsplatarray_len: (a: number) => number;
    readonly gsplatarray_tiny_lod: (a: number, b: number, c: number) => void;
    readonly gsplatarray_to_extsplats: (a: number) => [number, number, number];
    readonly gsplatarray_to_extsplats_lod: (a: number) => [number, number, number];
    readonly gsplatarray_to_packedsplats: (a: number, b: any) => [number, number, number];
    readonly gsplatarray_to_packedsplats_lod: (a: number, b: any) => [number, number, number];
    readonly init_lod_tree: (a: number, b: any) => [number, number, number];
    readonly new_lod_tree: (a: number) => [number, number, number];
    readonly new_shared_lod_tree: (a: number) => [number, number, number];
    readonly packedsplats_to_csplatarray: (a: number, b: any, c: number, d: any) => [number, number, number];
    readonly packedsplats_to_gsplatarray: (a: number, b: any, c: number, d: any) => [number, number, number];
    readonly simd_enabled: () => number;
    readonly sort32_splats: (a: number, b: any, c: any) => number;
    readonly sort_splats: (a: number, b: any, c: any) => number;
    readonly tiny_lod_extsplats: (a: number, b: any, c: any, d: number, e: number, f: number, g: number) => [number, number, number];
    readonly tiny_lod_packedsplats: (a: number, b: any, c: number, d: number, e: number, f: number, g: any) => [number, number, number];
    readonly traverse_lod_trees: (a: number, b: number, c: number, d: number, e: number, f: number, g: number, h: number, i: number, j: number, k: number, l: number, m: number, n: number, o: number, p: number, q: number, r: number, s: number) => [number, number, number];
    readonly update_lod_trees: (a: number, b: number, c: number, d: number, e: number, f: number, g: number, h: number, i: any) => [number, number, number];
    readonly wasm_start: () => void;
    readonly gsplatarray_has_lod: (a: number) => number;
    readonly __wbindgen_malloc: (a: number, b: number) => number;
    readonly __wbindgen_realloc: (a: number, b: number, c: number, d: number) => number;
    readonly __wbindgen_free: (a: number, b: number, c: number) => void;
    readonly __wbindgen_exn_store: (a: number) => void;
    readonly __externref_table_alloc: () => number;
    readonly __wbindgen_externrefs: WebAssembly.Table;
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
