# XJI Viewer Module (Spark)

Spark 2.0 3DGS 查看与轻量编辑模块。

- 前端入口：`src/modules/viewer/LayerViewerView.vue`
- 渲染核心：`src/lib/spark/`、`rust/`（WASM，仓库级共享）
- 云存储适配：`src/modules/viewer/viewerStorage.ts`

与 `modules/supersplat`（PlayCanvas 高级编辑）独立构建、独立维护。
