# Wayline 本机规划后端部署指南

XJICloud 云平台上的「航线规划」前端部署在 VM 的 `/route/` 路径下，**航线计算 API 运行在用户本机**（默认 `http://127.0.0.1:8787`）。每位使用航线规划的用户需在本机启动规划服务。

## 环境要求

- Windows 10 / 11
- [Node.js 20+](https://nodejs.org/)（推荐 22+）
- Git（用于拉取代码）

## 安装步骤

```powershell
git clone git@github.com:XJI1234/Wayline.git
cd Wayline
npm install -g pnpm
pnpm install
```

## 启动规划服务

**方式 A（推荐）：** 双击项目根目录下的 `start-wayline-server.bat`

**方式 B：** 命令行

```powershell
cd Wayline
set PLANNING_SERVER_HOST=127.0.0.1
set PLANNING_SERVER_PORT=8787
pnpm run server:start
```

## 验证

浏览器打开 `http://127.0.0.1:8787/api/health`，应返回：

```json
{"ok": true, "service": "planning-server"}
```

## 使用流程

1. 在本机启动 `start-wayline-server.bat`（保持窗口运行）
2. 浏览器访问 XJICloud 云平台并登录
3. 点击左侧工具栏「航线规划」
4. 若本机后端未启动，页面会提示「请先启动本机航线规划服务」

## 安全说明

后端默认绑定 `127.0.0.1:8787`，仅本机可访问，无需开放 Windows 防火墙端口。

## 可选：WASM 加速

若本机已安装 Emscripten，可执行 `pnpm run wasm:build` 启用 C++ WASM 规划内核；未构建时自动回退 JavaScript 实现。

## 常见问题

| 问题 | 处理 |
|------|------|
| 8787 端口被占用 | 修改 `PLANNING_SERVER_PORT`，并同步更新 Wayline `.env.production` 后重新构建前端 |
| HTTPS 云平台无法调用本机 HTTP | 现代 Chrome/Edge 通常允许访问 loopback；若被拦截，请用 HTTP 访问云平台或查看浏览器 Console |
| 地图不显示 | 确认 VM 上 `/route/` 静态资源已完整部署（含 Data.gmdb、hangzhou-3dtiles） |
