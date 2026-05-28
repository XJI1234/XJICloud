# XJI Viewer

基于 [Spark 2.0](https://github.com/sparkjs-dev/spark) 的 3D Gaussian Splatting（3DGS）模型查看与编辑工作台。支持在浏览器或 Electron 桌面端加载 PLY / SPZ 模型，进行实时渲染、标注、局部编辑与导出。

![Vue 3](https://img.shields.io/badge/Vue-3.5-42b883)
![TypeScript](https://img.shields.io/badge/TypeScript-6.0-3178c6)
![Three.js](https://img.shields.io/badge/Three.js-r180-black)
![Electron](https://img.shields.io/badge/Electron-37-47848f)

## 功能特性

### 模型查看
- 加载 **PLY**、**SPZ** 格式的 3DGS 模型
- 基于 Spark 2.0 的高性能 WebGL 渲染，带载入揭示动画
- 左键旋转、右键平移、滚轮缩放
- 重置视角 / 保存默认视角（写入同目录配置文件）
- 屏幕平面顺时针 / 逆时针旋转（15° 步进）

### 标注与测量
- **气泡标注**：点击模型表面添加文字标注，支持拖拽 reposition
- **立方体标记**：拖拽绘制 3D 立方体框，可添加标签文字
- 标注配置持久化到 `.viewer.json`

### 模型编辑
- **颜色标记**：画笔为 splat 着色
- **模型擦除**：将选中区域 splat 透明度置零
- **橡皮擦**：恢复原始颜色与透明度
- 可调节画笔半径与深度
- 支持撤回 / 重做（最多 24 步）
- 导出编辑结果为 **SPZ** 文件

### 项目信息
- 管理项目名称及内置字段（经纬度、建筑名称、楼层数、高度）
- 支持自定义字段，随模型目录一并保存

### 运行形态
- **Web 版**：通过 Vite 开发服务器或静态构建产物运行（需浏览器支持 File System Access API 以读写配置文件）
- **桌面版**：Electron 封装，提供原生文件对话框与目录读写

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端框架 | Vue 3 + TypeScript + Vite |
| 3D 渲染 | Three.js + Spark 2.0（`SparkRenderer` / `SplatMesh` / Dyno 着色器管线） |
| 高性能计算 | Rust → WebAssembly（`spark-rs`、`spark-worker-rs`） |
| 桌面端 | Electron 37 |
| 构建打包 | electron-builder（NSIS 安装包 + 便携版） |

## 环境要求

- **Node.js** ≥ 18
- **npm** ≥ 9
- （可选）**Rust** ≥ 1.82 + `wasm32-unknown-unknown` 目标，仅在需要重新编译 WASM 模块时

## 快速开始

### 安装依赖

```bash
npm run deps:install
```

> 该命令使用 npmmirror 镜像加速 npm 与 Electron 下载，适合国内网络环境。也可直接使用 `npm install`。

### Web 开发模式

```bash
npm run dev
```

浏览器访问 `http://127.0.0.1:5174`。

### Electron 桌面开发模式

```bash
npm run electron:dev
```

同时启动 Vite 开发服务器与 Electron 窗口。

### 构建

```bash
# 前端静态资源
npm run build

# Windows 桌面安装包（NSIS + 便携版）
npm run electron:build
```

产物输出至 `release/` 目录。

### 重新编译 Rust WASM（可选）

```bash
npm run build:wasm
```

编译结果写入 `rust/spark-rs/pkg/` 与 `rust/spark-worker-rs/pkg/`。

## 使用说明

### 打开模型

1. 点击侧边栏 **打开模型目录**
2. 选择包含 `.ply` 或 `.spz` 文件的文件夹
3. 若目录中有多个模型，会弹出选择对话框

桌面版通过 Electron 原生对话框；Web 版需 Chrome / Edge 等支持 [File System Access API](https://developer.mozilla.org/en-US/docs/Web/API/File_System_Access_API) 的浏览器。

### 配置文件

每个模型会在同目录下自动生成或读取 `{模型名}.viewer.json`，结构示例：

```json
{
  "version": 2,
  "defaultView": {
    "position": [0, 0, 5],
    "quaternion": [0, 0, 0, 1]
  },
  "pointAnnotations": [],
  "cubeMarkers": [],
  "projectInfo": {
    "projectName": "示例项目",
    "fields": [
      { "key": "coordinates", "label": "经纬度", "value": "116.39, 39.90" },
      { "key": "buildingName", "label": "建筑名称", "value": "" },
      { "key": "floorCount", "label": "楼层数", "value": "" },
      { "key": "height", "label": "高度", "value": "" }
    ]
  }
}
```

### 快捷键

| 按键 | 功能 |
|------|------|
| `O` | 打开模型目录 |
| `1` | 颜色标记模式 |
| `2` | 模型擦除模式 |
| `3` | 橡皮擦模式 |
| `Esc` | 返回查看模式 |
| `Ctrl+Z` / `Cmd+Z` | 撤回编辑 |
| `Ctrl+Y` / `Cmd+Shift+Z` | 重做编辑 |
| `Delete` | 删除选中的标注或立方体 |

## 项目结构

```
Viewer/
├── src/
│   ├── App.vue                 # 主界面与侧边栏逻辑
│   ├── components/
│   │   └── SparkViewport.vue   # Spark 渲染视口、编辑与标注
│   ├── lib/spark/              # Spark 2.0 渲染库（TypeScript + GLSL）
│   ├── utils/
│   │   └── desktopRuntime.ts   # Electron 桌面桥接
│   └── styles/
├── electron/                   # Electron 主进程与 preload
├── rust/
│   ├── spark-lib/              # 共享 Rust 库（PLY/SPZ 编解码等）
│   ├── spark-rs/               # 主 WASM 模块
│   ├── spark-worker-rs/        # Worker WASM 模块（排序等）
│   └── build_wasm.js           # WASM 构建脚本
├── public/
└── vite.config.ts
```

## 致谢

- 渲染核心基于 [Spark](https://github.com/sparkjs-dev/spark)（World Labs Technologies）
- 3D 基础库 [Three.js](https://threejs.org/)

## License

本项目应用层代码可自由使用。`src/lib/spark/` 与 `rust/` 下的 Spark 相关代码遵循 Spark 原始许可（Proprietary），使用前请参阅 [sparkjs-dev/spark](https://github.com/sparkjs-dev/spark) 仓库说明。
