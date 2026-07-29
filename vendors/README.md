# Vendored third-party projects

## SuperSplat (`vendors/supersplat`)

Git submodule pointing at [playcanvas/supersplat](https://github.com/playcanvas/supersplat).

```bash
git submodule update --init --recursive
pnpm build:supersplat
```

Build output is copied to `apps/web/public/supersplat/` and served at `/supersplat/`.
If GitHub is unreachable, clone via a mirror then re-point `origin` to the canonical URL.
