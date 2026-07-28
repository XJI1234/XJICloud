---
name: city-white-models
description: >-
  Convert Geobuilding city GLTF packs to Cesium 3D Tiles, build OSM building
  search indexes, deploy multi-city white models under Wayline /route/, and
  diagnose display/search issues (only one city shows, scene not ready,
  ia.max is not a function). Use when the user mentions 白模, 建筑白模,
  搜索采样, 搜索索引, building-search-index, 3D Tiles, tileset,
  city-models-raw, or multi-city Cesium display.
---

# 城市建筑白模（Geobuilding → 3D Tiles → Cesium）

## 架构（必须走这条链路）

```
city-models-raw/<城市>_GLTF…/
  modelinfo.json + *.gltf (+ Draco)
        │  scripts/build-nanjing-3dtiles.mjs
        ▼
/route/<city>-3dtiles/
  tileset.json + content/*.b3dm + manifest.json
        │  scripts/build-building-search-index.py  (+ OSM Overpass)
        ▼
  building-search-index.json   ← 搜索采样用
        │  Cesium3DTileset.fromUrl / buildingSearch.js
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
- [ ] 3. cityModels.js 登记 id / tilesetPath / manifestPath / searchIndexPath / coverage / center
- [ ] 4. Nginx：location /route/<id>-3dtiles/ { try_files $uri =404; }
- [ ] 5. 构建搜索索引：npm run search-index:<id>（见下方）
- [ ] 6. 构建 Wayline 并 rsync 到 /route/（排除 *-3dtiles、cesium、Data.gmdb）
- [ ] 7. index.html 禁止长期缓存；iframe 带 ?v= 防旧 JS
- [ ] 8. 验证 tileset + search-index 均 200；侧栏搜索结果带城市名
```

### 转换 / 构建同步

```bash
cd /root/workspace/Wayline
# Node 22+（vite 需要 node:sqlite）
export PATH="$HOME/.nvm/versions/node/v22.23.1/bin:$PATH"

node scripts/build-nanjing-3dtiles.mjs \
  --input /www/wwwroot/192.168.63.129/city-models-raw/<城市目录> \
  --output /www/wwwroot/192.168.63.129/route/<id>-3dtiles

./node_modules/.bin/vite build
rsync -a --delete \
  --exclude 'Data.gmdb' --exclude '*-3dtiles' --exclude 'cesium' \
  dist/ /www/wwwroot/192.168.63.129/route/
```

---

## 搜索索引制作（OSM POI ↔ 白模）

### 原理

1. 从 `manifest.json` 建建筑空间索引（大文件用 **ijson 流式**，勿整包 `json.load` 上海 ~480MB）。
2. 按城市 bbox 向 **Overpass** 拉带 `name` 的建筑/POI（分格请求，结果缓存到 `data/<id>-osm-pois.json`）。
3. POI 与白模 footprint 做 contain / nearest(≤35m) 匹配。
4. 写出 `building-search-index.json`：每条含 `cityId`/`cityName`，并**内嵌 bbox 矩形 `building`**（勿嵌完整 footprint，否则沪/港索引可达数十 MB，首搜卡死）。
5. 前端 `searchBuildings({ allCities: true })` 跨城搜；结果标注城市；选中后切城 + 用内嵌轮廓，**不拉完整 manifest**。

### 一键命令

```bash
cd /root/workspace/Wayline
npm run search-index:nanjing    # 单城
npm run search-index:shanghai
npm run search-index:wuhan
npm run search-index:taizhou
npm run search-index:hongkong
npm run search-index:all-cities # 顺序跑五城
```

等价手工示例（上海）：

```bash
python3 scripts/build-building-search-index.py --matched-only \
  --city-id shanghai --city-name 上海 \
  --manifest /www/wwwroot/192.168.63.129/route/shanghai-3dtiles/manifest.json \
  --output  /www/wwwroot/192.168.63.129/route/shanghai-3dtiles/building-search-index.json \
  --poi-cache data/shanghai-osm-pois.json \
  --bbox 120.89198,30.7015,121.98772,31.83481 \
  --grid-rows 10 --grid-cols 10
```

### 制作要点

| 项 | 做法 |
|----|------|
| 依赖 | `python3-ijson`（大 manifest 流式） |
| POI 缓存 | `data/<id>-osm-pois.json`；有 `doneCells` 可续跑 |
| Overpass 429/504 | 多镜像 + 重试；**失败格不要标 done**；多轮直至 `doneCells` 清空 |
| 瘦身重建 | 已有完整 POI 缓存时再跑一遍即可（跳过 Overpass），更新内嵌 building |
| 条目字段 | `name/aliases/cityId/cityName/buildingId/building/match/distanceM/...` |

### 前端约定

- `buildingSearch.js`：默认跨城；`cityName` 打在结果上。
- `plannerPanel.vue`：结果标题城市标签；选中 → `setActiveCityModel` → `buildModelTargetFromBuilding(item.building)`。
- **禁止**为搜索去 `Promise.all` 拉齐所有城 `manifest.json`。

细节命令与参数 → [reference.md](reference.md#搜索索引详解)。

---

## 多城显示：正确行为 vs 错觉

| 现象 | 含义 |
|------|------|
| 全国视角无白模 | LOD 未细化；请「定位到」 |
| 默认只像南京 | 默认聚焦南京 + 先加载南京 |
| Network 有沪 content | 已进场景；可能高度不够 |
| 只有 tileset.json、无 content | 相机未进覆盖区，或飞城请求被裁光 |

**同时显示** = 多 tileset `show=true` + 视锥流式；≠ 全国高度五城轮廓可见。

## 「只显示一城」排查顺序

1. 产物：`tileset.json` / b3dm 齐全；坐标属该城。
2. HTTP：`Host` 正确；200 且非 HTML。
3. 线上 JS：hashed bundle 含全部 `*-3dtiles`；无旧包缓存。
4. 访问日志：聚焦城是否有大量 `content/*.b3dm`。
5. 前端：`cullRequestsWhileMovingMultiplier≈10`；当前城 SSE 低、其它高；`flyTo` 可 await。
6. 对照页：`/route/city-model-verify.html?city=shanghai`。

## 搜索 / 采样常见故障排查

| 提示或现象 | 原因 | 处理 |
|------------|------|------|
| `地图场景尚未初始化完成` | `setCesiumManagerInfo` 被挡在五城 tileset 全部 await 之后；地图已出但 store 仍为 null | Viewer 创建后**立刻** `setCesiumManagerInfo` + 注册 click；白模后台加载（`mapContainer.vue`） |
| `ia.max is not a function` | 解构了 `Math` from Cesium，盖住原生 `Math.max`；选中搜索结果 `flyTo` 时炸 | 改为 `Math: CesiumMath`；`Math.max` → `globalThis.Math.max`；`toRadians/toDegrees` 用 `CesiumMath` |
| 搜索无其它城 / 无城市标签 | 缺 `building-search-index.json` 或旧前端 | 跑 `search-index:*`；确认索引含 `cityName`；强刷新 JS |
| 首搜极卡 / 卡死 | 索引内嵌完整 footprint，沪+港可 >70MB | 内嵌改用 **bbox 四角矩形**；Nginx gzip |
| 选中后无轮廓 / 要下几百 MB | 走了整包 manifest | 用索引内嵌 `building`；`extractModelTargetByBuildingId` 只拉目标城 |
| Overpass 大量失败、条目很少 | 429/504 且失败格被标 done | 不标 done、多轮续跑、加镜像、加大 sleep |

## 前端关键约定（Wayline）

- `cityModels.js` / `CesiumManager.js` / `plannerPanel.vue` / `buildingSearch.js` / `mapContainer.vue`
- 禁止启动 prefetch 各城 `manifest.json`
- 聚焦：优先当前城 → SSE 优先级 → await flyTo → 等瓦片
- 观察高度 ~1800m、pitch -55°；`center` 用城区密区

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

`/route/city-model-verify.html?city=shanghai` — 单城切换验证几何。

## 更多细节

路径表、索引参数、日志样例、历史坑 → [reference.md](reference.md)
