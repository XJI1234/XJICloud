# 白模参考手册

## 路径一览

| 角色 | 典型路径 |
|------|----------|
| Wayline 源码 | `/root/workspace/Wayline` |
| XJICloud 源码 | `/root/workspace/XJICloud` |
| 站点根 | `/www/wwwroot/192.168.63.129/` |
| Raw GLTF | `/www/wwwroot/192.168.63.129/city-models-raw/` |
| 3D Tiles | `/www/wwwroot/192.168.63.129/route/<id>-3dtiles/` |
| 搜索索引 | `/route/<id>-3dtiles/building-search-index.json` |
| POI 缓存 | `Wayline/data/<id>-osm-pois.json` |
| 航线页 | `/www/wwwroot/192.168.63.129/route/` |
| Nginx | `/www/server/panel/vhost/nginx/192.168.63.129.conf` |
| 访问日志 | `/www/wwwlogs/192.168.63.129.log` |
| 转 3D Tiles | `Wayline/scripts/build-nanjing-3dtiles.mjs` |
| 建搜索索引 | `Wayline/scripts/build-building-search-index.py` |
| 城市目录 | `Wayline/src/utils/cityModels.js` |

### 已部署城市 id

| id | 中文 | tileset | 搜索索引脚本 |
|----|------|---------|--------------|
| nanjing | 南京 | nanjing-3dtiles | `search-index:nanjing` |
| shanghai | 上海 | shanghai-3dtiles | `search-index:shanghai` |
| wuhan | 武汉 | wuhan-3dtiles | `search-index:wuhan` |
| taizhou | 泰州 | taizhou-3dtiles | `search-index:taizhou` |
| hongkong | 香港 | hongkong-3dtiles | `search-index:hongkong` |

（历史：`hangzhou-3dtiles` 可能仍在磁盘，新逻辑以 `CITY_MODEL_CATALOG` 为准。）

---

## Raw 包特征（Geobuilding）

- 目录名类似：`城市_GLTF模型贴图_draco压缩_5000米范围分块_日期_时间`
- 必备：`modelinfo.json`（键为 `1.gltf`…，值含 `center`/`bbox` WGS84）
- 同目录大量 `.gltf` + Draco；「如何在网页端浏览.txt」是 Three.js 预览说明，**Cesium 不走那条路**

## 转换产物特征

- `asset.version`: `1.1`，`generator`: `build-city-model-3dtiles`
- `refine`: `ADD`；叶子带 `content.uri` + `transform`（ENU→ECEF）+ `boundingVolume.region`
- `manifest.json`：建筑 footprint 索引，**可极大**（上海 ~483MB）— 浏览器勿整包预拉
- b3dm：GLB + Draco；本地坐标约 ±2.5km

### 转换脚本常用参数

| 参数 | 含义 |
|------|------|
| `--input` / `--output` | raw 目录 / 3dtiles 输出 |
| `--leafSize` | 叶子合并粒度（默认 12） |
| `--minHeight` / `--maxHeight` | region 高度（-30 / 600） |
| `--force` / `--limit` | 强制重转 / 调试限量 |

---

## 搜索索引详解

### 流水线

```
manifest.json (流式 ijson)
  → 建筑空间格网
OSM Overpass（分格 + 缓存 data/<id>-osm-pois.json）
  → name/aliases POI
match: contain | nearest(≤ nearest-m) | none
  → --matched-only 只保留有 buildingId 的
building-search-index.json
  { cityId, cityName, coverage, stats, entries[] }
```

### 脚本参数

| 参数 | 含义 |
|------|------|
| `--manifest` / `--output` | 输入 manifest / 输出索引 |
| `--poi-cache` | Overpass 断点续跑缓存 |
| `--poi-geojson` | 可选：本地 GeoJSON 代替 Overpass |
| `--bbox` | `west,south,east,north`；默认从建筑推算 |
| `--city-id` / `--city-name` | 写入索引与每条 entry（跨城区分） |
| `--grid-rows` / `--grid-cols` | Overpass 分格（沪建议 10×10，泰 4×4） |
| `--cell-size` | 空间索引格（默认 0.01°） |
| `--nearest-m` | 邻近匹配上限米（默认 35） |
| `--matched-only` | 只要匹配上白模的 POI |

### 条目内嵌 `building`（重要）

```json
{
  "id": "78-12",
  "center": { "longitude": 121.47, "latitude": 31.23 },
  "bbox": [west, south, east, north],
  "footprint": [[w,s],[e,s],[e,n],[w,n]],
  "roofHeight": 42,
  "area": 1200
}
```

- **必须用 bbox 四角当 footprint**，不要塞原始复杂多边形（体积爆炸）。
- 前端 `buildModelTargetFromBuilding` 可直接出采样目标，无需再下 manifest。

### Overpass 实操

- 镜像：`overpass-api.de`、`overpass.kumi.systems`、`overpass.openstreetmap.ru`
- 限流：加大重试间隔；格间 `sleep ~1.2s`
- **失败的 cell 不要写入 `doneCells`**，否则续跑会永久跳过该格
- 完成标志：缓存里 `doneCells` 为空（脚本写完会清掉）
- 多轮：`for round in 1 2 3 4 5; do npm run search-index:<id>; done` 直到完成

### 从缓存瘦身重建（无网络）

POI 缓存已完整时，再跑同一命令即可跳过 Overpass，只重算匹配 + 写出瘦索引。

### 索引健康检查

```bash
H='Host: 192.168.63.129'
for c in nanjing shanghai wuhan taizhou hongkong; do
  curl -s -o /dev/null -w "$c %{http_code} %{size_download}\n" -H "$H" \
    http://127.0.0.1/route/$c-3dtiles/building-search-index.json
done

python3 - <<'PY'
import json
from pathlib import Path
for c in ['nanjing','shanghai','wuhan','taizhou','hongkong']:
  p=Path(f'/www/wwwroot/192.168.63.129/route/{c}-3dtiles/building-search-index.json')
  if not p.exists():
    print(c, 'MISSING'); continue
  d=json.loads(p.read_text())
  e=(d.get('entries') or [None])[0] or {}
  print(c, 'entries', len(d.get('entries') or []),
        'city', d.get('cityName'),
        'hasBuilding', bool(e.get('building')),
        'MB', round(p.stat().st_size/1e6,1))
PY
```

建议单城索引 gzip 后可接受；未压缩沪/港控制在约 **≤30MB**（bbox 内嵌后）。

### package.json 脚本模板

```json
"search-index:shanghai": "python3 scripts/build-building-search-index.py --matched-only --city-id shanghai --city-name 上海 --manifest /www/wwwroot/192.168.63.129/route/shanghai-3dtiles/manifest.json --output /www/wwwroot/192.168.63.129/route/shanghai-3dtiles/building-search-index.json --poi-cache data/shanghai-osm-pois.json --bbox 120.89198,30.7015,121.98772,31.83481 --grid-rows 10 --grid-cols 10",
"search-index:all-cities": "npm run search-index:nanjing && npm run search-index:taizhou && npm run search-index:hongkong && npm run search-index:wuhan && npm run search-index:shanghai"
```

---

## 前端加载策略（经验固化）

```
mapContainer onMounted
  → cesiumManager.init
  → 立刻 setCesiumManagerInfo + 注册 click/mousemove   ← 勿延后
  → flyTo 初视角
  → 后台 void setActiveCityModel / setCityModelEnabled（不阻塞采样）

启用白模
  → ensure 当前城 tileset 优先
  → 再挂载其余城（全部 show=true）
  → 当前城 SSE ≈ 8，其它 ≈ 48
  → cullRequestsWhileMovingMultiplier ≈ 10（勿用 60）
  → 「定位到」：await flyTo → 等 tilesLoaded
```

### Cesium.Math 命名冲突（必记）

```js
// ❌ 错误：解构 Math 会盖住全局 Math，flyTo 里 Math.max 变成 ia.max is not a function
const { Math, ... } = Cesium

// ✅ 正确
const { Math: CesiumMath, ... } = Cesium
CesiumMath.toRadians(...)
globalThis.Math.max(...)
```

---

## 排查命令速查

### 产物与坐标

```bash
for c in nanjing shanghai wuhan taizhou hongkong; do
  echo -n "$c tileset "; wc -c < /www/wwwroot/192.168.63.129/route/$c-3dtiles/tileset.json
  echo -n "$c b3dm "; ls /www/wwwroot/192.168.63.129/route/$c-3dtiles/content/*.b3dm | wc -l
done
```

### HTTP（注意 Host）

```bash
H='Host: 192.168.63.129'
for c in nanjing shanghai wuhan taizhou hongkong; do
  curl -s -o /dev/null -w "$c tileset %{http_code} %{size_download}\n" -H "$H" \
    http://127.0.0.1/route/$c-3dtiles/tileset.json
  curl -s -o /dev/null -w "$c index %{http_code} %{size_download}\n" -H "$H" \
    http://127.0.0.1/route/$c-3dtiles/building-search-index.json
done
```

### 访问日志

```bash
awk '$4 >= "[28/Jul/2026:05:00:00"' /www/wwwlogs/192.168.63.129.log \
  | grep -iE 'Edg|Chrome' | grep '3dtiles' \
  | sed 's|.*/route/\([^/]*\)-3dtiles/.*|\1|' | sort | uniq -c | sort -rn

grep -oE 'assets/index-[^"]+\.js' /www/wwwroot/192.168.63.129/route/index.html
```

---

## 历史坑（排查时优先怀疑）

1. **旧 JS 缓存**：只含南京的旧 hashed 包 → `index.html` no-cache + iframe `?v=`。
2. **SPA 回退**：缺瓦片时返回 `index.html` → 每城 `*-3dtiles/` 必须 `try_files =404`。
3. **manifest OOM**：启动预拉上海 manifest → 禁止；选中靠搜索索引内嵌。
4. **飞城不拉瓦片**：`cullRequestsWhileMovingMultiplier` 过大；flyTo 未 await。
5. **中心点偏郊区**：用 bbox 质心 → 改城区密区坐标。
6. **「同时显示」误解**：挂载 ≠ 全国可见；以「定位到」验收。
7. **场景未初始化**：白模已显示但 `cesiumManagerInfo==null` → 过早 await 五城加载；应 init 后立刻登记。
8. **`ia.max is not a function`**：Cesium `Math` 遮蔽原生 `Math.max`。
9. **搜索索引过大**：内嵌完整 footprint → 改 bbox 矩形。
10. **Overpass 丢格**：失败 cell 标 done → 续跑永远缺 POI。

---

## 验收标准

- [ ] 验证页五城约 1–2km 可见白模
- [ ] 「定位到」各城有 content 请求且可见建筑
- [ ] 全国俯瞰提示需定位，不谎称已见建筑
- [ ] Network 无启动期巨型 `manifest.json`
- [ ] 五城 `building-search-index.json` 200；条目含 `cityName` + `building`
- [ ] 跨城搜索结果带城市标签；点选可飞到并选中
- [ ] 建筑采样 / 搜索采样不再误报「场景尚未初始化」
- [ ] 入口为最新 `index-*.js`

## 相关代码锚点

- `Wayline/src/utils/cityModels.js` — `CITY_MODEL_CATALOG`（含 `searchIndexPath`）
- `Wayline/src/utils/buildingSearch.js` — 跨城搜索
- `Wayline/src/utils/CesiumManager.js` — tileset / flyTo / `buildModelTargetFromBuilding`；**CesiumMath 命名**
- `Wayline/src/components/plannerPanel.vue` — 搜索采样 UI、城市标签
- `Wayline/src/components/mapContainer.vue` — **早登记** `setCesiumManagerInfo`
- `Wayline/scripts/build-building-search-index.py` — 索引构建
- `XJICloud/src/views/WaylineEditorView.vue` — iframe `?v=`
