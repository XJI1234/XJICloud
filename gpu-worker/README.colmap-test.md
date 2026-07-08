# COLMAP Sparse Test Docker

这个测试镜像不依赖后端，只做一件事：读取 `south-building.zip`，自动执行 COLMAP sparse 重建，并把结果写到挂载的 `/output`。

## 输入

把官方数据集放到：

```text
gpu-worker/testdata/input/south-building.zip
```

Zip 解压后可以是图片直接位于根目录，也可以位于 `images/` 子目录。

## 运行

```bash
cd XJICloud
docker compose -f deploy/docker-compose.gpu-test.yml up --build
```

## 输出

成功后检查：

```text
gpu-worker/testdata/output/sparse/0/
```

通常会看到类似这些文件：

```text
cameras.bin
images.bin
points3D.bin
```

## 可选环境变量

- `COLMAP_SINGLE_CAMERA=0|1`：默认 `0`，官方 `south-building` 数据集建议保持 `0`
- `COLMAP_USE_GPU=1|0`：默认 `1`，目标机器有 GPU 时开启
- `EXPORT_PLY=1|0`：默认 `0`，设为 `1` 时额外导出 `/output/model.ply`
