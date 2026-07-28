# 航线规划改动 · 本地开发

## 工作流（约定）

1. **只在本地改**：源码在 `../Wayline`，备份镜像在本目录  
2. **本地测通**：构建后同步到本机 `/route/` 或用 Vite；**不**擅自 `git push`  
3. **你确认后再 Git**：明确说「提交 / 推远程」后再 commit / push  
4. **改算法必改文档**：见 [ALGORITHM.md](./ALGORITHM.md)

## 文档

| 文件 | 内容 |
|------|------|
| **[ALGORITHM.md](./ALGORITHM.md)** | **航线规划算法原理（权威，随代码更新）** |
| `README.md`（本文件） | 本地开发 / 部署约定 |
| `patches/*.diff` | 补丁备份 |

## 当前状态

- 算法改动在本地 `../Wayline` 工作区（未确认前不 push）  
- 本目录为同步备份  

## 目录

```
local/wayline-planning/
  ALGORITHM.md                          # 原理（请优先维护）
  README.md
  src/...                               # 源码备份
  patches/*.diff
```

## 本地预览

云平台入口：`http://192.168.63.129/app/wayline`（iframe → `/route/`）  
源码改完后需 **构建并同步到本机 `/route/`** 才能在该地址看到更新（5174 端口可能被防火墙拦截）。

```bash
export PATH="/root/.nvm/versions/node/v22.23.1/bin:$PATH"
cd /root/workspace/Wayline
npx vite build
rsync -av --exclude '*-3dtiles/' --exclude 'cesium/' --exclude 'Data.gmdb' --exclude 'assets/*.gmdb' --exclude 'route/' \
  dist/ /www/wwwroot/192.168.63.129/route/
```

然后打开 `/app/wayline` 并 Ctrl+F5。

## 从备份重新铺回 Wayline

```bash
OVERLAY=/root/workspace/XJICloud/local/wayline-planning
WL=/root/workspace/Wayline
cp -a $OVERLAY/src/utils/* $WL/src/utils/
cp -a $OVERLAY/src/algorithm/core/missionPlanner.js $WL/src/algorithm/core/
cp -a $OVERLAY/src/components/plannerPanel.vue $WL/src/components/
cp -a $OVERLAY/src/components/mapContainer.vue $WL/src/components/
cp -a $OVERLAY/server/index.mjs $WL/server/
```

## 功能摘要

1. 水平重叠提高、立面加密、环绕自动张数  
2. 信息增益补拍（β 速度↔质量），控件在「采样参数」区  
3. 地图绿色 `IG*` 补拍点  

原理与文献脉络见 **[ALGORITHM.md](./ALGORITHM.md)**。
