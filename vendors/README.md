# Vendored third-party assets

## SuperSplat (`vendors/supersplat`)

**Prebuilt** static assets from [playcanvas/supersplat](https://github.com/playcanvas/supersplat) (currently based on **v2.32.2**), with `BASE_HREF=/supersplat/`.

- Source is **not** kept in this repo (no submodule / no `npm run build` for SuperSplat).
- `pnpm --filter @xjicloud/web dev|build` runs `scripts/copy-supersplat-dist.mjs`, which copies these files into `apps/web/public/supersplat/` (gitignored).
- Served at `/supersplat/` for the iframe editor.

To refresh the vendor bundle: build SuperSplat elsewhere with `BASE_HREF=/supersplat/`, then replace the files under `vendors/supersplat/` (omit `*.map` if desired).
