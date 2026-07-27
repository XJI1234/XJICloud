---
name: city-white-models
description: >-
  Convert Geobuilding city GLTF packs to Cesium 3D Tiles, deploy multi-city white
  models under Wayline /route/, and diagnose “only one city shows” issues. Use
  when the user mentions 白模, 建筑白模, 3D Tiles, tileset, city-models-raw,
  nanjing/shanghai/wuhan/taizhou/hongkong models, or multi-city Cesium display.
---

# 城市建筑白模（Geobuilding → 3D Tiles → Cesium）

## 架构（必须走这条链路）

```
city-models-raw/<城市>_GLTF…/
  modelinfo.json + *.gltf (+ Draco)
        │  scripts/build-nanjing-3dtiles.mjs
        ▼
/route/<city>-3dtiles/
  tileset.json + content/*.b3dm (+ 可选 manifest.json)
        │  Cesium3DTileset.fromUrl
        ▼
Wayline Cesium 场景（叠天地图）
```

- **不要**把 raw GLTF zip 直接丢给 Cesium；必须先转 3D Tiles。
- Cesium 按视距 LOD 流式拉瓦片：全国俯瞰（~5000km）**看不到**成片白模是正常的；需侧栏「定位到」或降到约 **1–2km**。
- 源码在 `../Wayline`；线上站点常见根：`/www/wwwroot/192.168.63.129/`。

## 新城市接入清单

```
- [ ] 1. raw 解压到 city-models-raw/，确认有 modelinfo.json（WGS84 center/bbox）
- [ ] 2. 用同一转换脚本输出到 route/<id>-3dtiles/
- [ ] 3. cityModels.js 登记 id / tilesetPath / coverage / center（中心用城区密区，勿用南缘质心）
- [ ] 4. Nginx：location /route/<id>-3dtiles/ { try_files $uri =404; }（禁止 SPA 回退成 index.html）
- [ ] 5. 构建 Wayline 并 rsync 到 /route/（排除 *-3dtiles、cesium、Data.gmdb）
- [ ] 6. index.html 禁止长期缓存；iframe 带 ?v= 防旧 JS
- [ ] 7. 用验证页或访问日志确认 tileset.json + content/*.b3dm 均 200
```

### 转换命令（示例）

```bash
cd /root/workspace/Wayline
node scripts/build-nanjing-3dtiles.mjs \
  --input /www/wwwroot/192.168.63.129/city-models-raw/<城市目录> \
  --output /www/wwwroot/192.168.63.129/route/<id>-3dtiles
```

### 构建与同步

```bash
cd /root/workspace/Wayline
./node_modules/.bin/vite build   # 避免 pnpm approve-builds 卡死
rsync -a --delete \
  --exclude 'Data.gmdb' --exclude '*-3dtiles' --exclude 'cesium' \
  dist/ /www/wwwroot/192.168.63.129/route/
```

## 多城显示：正确行为 vs 错觉

| 现象 | 含义 |
|------|------|
| 全国视角无白模 | LOD 未细化到叶子；提示用户「定位到」 |
| 默认只像南京 | 默认 `activeCityModelId=nanjing` + 先飞南京密区 |
| Network 有沪/泰 content | 数据已进场景；可能高度不够或未停在该城上空 |
| 只有某城 tileset.json、无 content | 相机从未进入该城覆盖区，或飞城时请求被裁光 |

**同时显示** = 多个 `Cesium3DTileset` 均 `show=true`，由 Cesium 按视锥流式加载；不是全国高度下五城轮廓同时可见。

## 「只显示一城」排查顺序

按序做，避免一上来重转数据：

1. **产物**：各城 `tileset.json` 存在；leaf 数 ≈ raw `modelinfo` 条数；content 下 b3dm 齐全。
2. **坐标**：抽 leaf `transform` 反算 lon/lat，应对应该城（勿全是南京）。
3. **HTTP**：`Host` 指向站点；`tileset.json` 与 `content/1.b3dm` 均为 200（不是 HTML）。
4. **线上 JS**：`index.html` 引用的 hashed bundle 是否含全部 `*-3dtiles`；访问日志是否仍在拉旧 `index-*.js`。
5. **浏览器请求**：是否五城都请求了 `tileset.json`；聚焦城是否有大量 `content/*.b3dm`。
6. **前端逻辑**：
   - 勿 `show=false` 藏非当前城（除非刻意单城模式）。
   - `cullRequestsWhileMovingMultiplier` 过大（如 60）→ 飞城过程几乎不拉瓦片。
   - 五城同时 SSE 过低 → 抢带宽；应对：**当前城 SSE 低、其它城 SSE 高**，且先 `ensure` 当前城。
   - `flyTo` 要可 await，落地后再等 `tilesLoaded` / `allTilesLoaded`。
7. **单城对照页**：`/route/city-model-verify.html?city=shanghai`  
   - 这里有、主界面无 → 查多城加载/聚焦/缓存。  
   - 这里也无 → 查 tileset/几何/Nginx。

常用日志命令见 [reference.md](reference.md)。

## 前端关键约定（Wayline）

- 目录：`src/utils/cityModels.js`（目录）、`CesiumManager.js`（加载/聚焦）、`plannerPanel.vue`（「定位到」）、`buildingSearch.js`（按城搜索）。
- **禁止**启动时 prefetch 各城 `manifest.json`（上海可到数百 MB，会 OOM）。
- 聚焦：`setActiveCityModel(id, { flyTo: true })` → 优先加载该城 → 提高该城 SSE、降低其它城 → flyTo → 等瓦片。
- 观察高度建议约 **1800m**、pitch **-55°**；`center` 用人民广场级密区坐标。

## Nginx 要点

```nginx
location /route/<id>-3dtiles/ {
    try_files $uri =404;
    expires 7d;
    add_header Access-Control-Allow-Origin *;
}
location = /route/index.html {
    add_header Cache-Control "no-cache, no-store, must-revalidate" always;
    expires -1;
}
```

## 验证页

部署：`/route/city-model-verify.html`（单城切换，暗色椭球即可验证几何）。

## 更多细节

路径表、转换参数、日志样例、历史坑 → [reference.md](reference.md)
