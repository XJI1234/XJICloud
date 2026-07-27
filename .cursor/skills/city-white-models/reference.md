# 白模参考手册

## 路径一览

| 角色 | 典型路径 |
|------|----------|
| Wayline 源码 | `/root/workspace/Wayline` |
| XJICloud 源码 | `/root/workspace/XJICloud` |
| 站点根 | `/www/wwwroot/192.168.63.129/` |
| Raw GLTF | `/www/wwwroot/192.168.63.129/city-models-raw/` |
| 3D Tiles | `/www/wwwroot/192.168.63.129/route/<id>-3dtiles/` |
| 航线页 | `/www/wwwroot/192.168.63.129/route/` |
| Nginx 站点配置 | `/www/server/panel/vhost/nginx/192.168.63.129.conf` |
| 访问日志 | `/www/wwwlogs/192.168.63.129.log` |
| 转换脚本 | `Wayline/scripts/build-nanjing-3dtiles.mjs` |
| 城市目录 | `Wayline/src/utils/cityModels.js` |

### 已部署城市 id

| id | 中文 | tileset 目录 |
|----|------|----------------|
| nanjing | 南京 | nanjing-3dtiles |
| shanghai | 上海 | shanghai-3dtiles |
| wuhan | 武汉 | wuhan-3dtiles |
| taizhou | 泰州 | taizhou-3dtiles |
| hongkong | 香港 | hongkong-3dtiles |

（历史：`hangzhou-3dtiles` 可能仍在磁盘，新逻辑以 `CITY_MODEL_CATALOG` 为准。）

## Raw 包特征（Geobuilding）

- 目录名类似：`城市_GLTF模型贴图_draco压缩_5000米范围分块_日期_时间`
- 必备：`modelinfo.json`（键为 `1.gltf`…，值含 `center`/`bbox` WGS84）
- 同目录大量 `.gltf` + Draco；附带「如何在网页端浏览.txt」是 Three.js 本地预览说明，**Cesium 不走那条路**

## 转换产物特征

- `asset.version`: `1.1`，`generator`: `build-city-model-3dtiles`
- `refine`: `ADD`；叶子带 `content.uri` + `transform`（ENU→ECEF）+ `boundingVolume.region`
- 叶子 `geometricError` 常为 `0`；根 GE 约城市跨度量级（数十～百余 km）
- `manifest.json`：建筑 footprint 索引，**可极大**（上海曾 ~483MB）— 仅点选/搜索按需加载
- b3dm 内嵌 GLB + `KHR_draco_mesh_compression`；本地坐标约 ±2.5km、高 0～数百米

### 转换脚本常用参数

| 参数 | 含义 | 默认倾向 |
|------|------|----------|
| `--input` | raw 目录 | 必填 |
| `--output` | 输出 3dtiles 目录 | 必填 |
| `--leafSize` | 叶子合并粒度 | 12 |
| `--minHeight` / `--maxHeight` | region 高度 | -30 / 600 |
| `--force` | 强制重转 | false |
| `--limit` | 调试限量 | null |

## 前端加载策略（经验固化）

```
启用白模
  → ensure 当前城 tileset 优先
  → 再串行/有序挂载其余城（全部 show=true）
  → 当前城 maximumScreenSpaceError ≈ 8
  → 其它城 ≈ 48（仍可见，少抢请求）
  → cullRequestsWhileMovingMultiplier ≈ 10（勿用 60）
  → 「定位到」：await flyTo → 等 tilesLoaded → requestRender
```

状态文案应区分：

- 已挂载但高度 > ~120km：提示「全国视角不可见，请定位到」
- 已聚焦某城：提示「同时显示中（聚焦 X）」

## 排查命令速查

### 产物与坐标

```bash
# 各城 tileset / b3dm 数量
for c in nanjing shanghai wuhan taizhou hongkong; do
  echo -n "$c tileset "; wc -c < /www/wwwroot/192.168.63.129/route/$c-3dtiles/tileset.json
  echo -n "$c b3dm "; ls /www/wwwroot/192.168.63.129/route/$c-3dtiles/content/*.b3dm | wc -l
done

# raw 范围
python3 - <<'PY'
import json, os
raw='/www/wwwroot/192.168.63.129/city-models-raw'
for name in sorted(os.listdir(raw)):
  p=f'{raw}/{name}/modelinfo.json'
  if not os.path.isfile(p): continue
  m=json.load(open(p))
  lons=[v['center'][0] for v in m.values() if v.get('center')]
  lats=[v['center'][1] for v in m.values() if v.get('center')]
  print(name[:36], len(m), f'lon[{min(lons):.3f},{max(lons):.3f}]', f'lat[{min(lats):.3f},{max(lats):.3f}]')
PY
```

### HTTP（注意 Host）

```bash
H='Host: 192.168.63.129'
for c in nanjing shanghai wuhan taizhou hongkong; do
  curl -s -o /dev/null -w "$c tileset %{http_code} %{size_download}\n" -H "$H" \
    http://127.0.0.1/route/$c-3dtiles/tileset.json
  curl -s -o /dev/null -w "$c content1 %{http_code} %{size_download}\n" -H "$H" \
    http://127.0.0.1/route/$c-3dtiles/content/1.b3dm
done
```

### 访问日志（是否真在拉某城）

```bash
# 替换时间窗后统计各城请求
awk '$4 >= "[27/Jul/2026:17:15:00"' /www/wwwlogs/192.168.63.129.log \
  | grep -iE 'Edg|Chrome' | grep '3dtiles' \
  | sed 's|.*/route/\([^/]*\)-3dtiles/.*|\1|' | sort | uniq -c | sort -rn

# 当前入口 JS
grep -oE 'assets/index-[^"]+\.js' /www/wwwroot/192.168.63.129/route/index.html
# bundle 是否含五城
grep -oE '(nanjing|shanghai|wuhan|taizhou|hongkong)-3dtiles' \
  /www/wwwroot/192.168.63.129/route/assets/index-*.js | sort | uniq -c
```

## 历史坑（排查时优先怀疑）

1. **旧 JS 缓存**：用户 Edge 仍加载只含 `nanjing-3dtiles` 的旧 hashed 包 → `index.html` no-cache + iframe `?v=Date.now()`。
2. **SPA 回退**：`/route/` 的 `try_files … /route/index.html` 把缺失的 `tileset.json` 变成 HTML → Cesium 解析失败。每个 `*-3dtiles/` 必须独立 `=404`。
3. **manifest OOM**：启动预拉上海 manifest（数百 MB）卡死/崩 → 仅 pick/search 懒加载。
4. **飞城不拉瓦片**：`cullRequestsWhileMovingMultiplier` 过大；flyTo 未 await。
5. **中心点偏郊区**：用 bbox 质心会落到闵行等较稀区域 → 改用城区密区坐标。
6. **「同时显示」误解**：五城挂载 ≠ 全国高度五城轮廓可见；以「定位到」验收。
7. **请求预算**：五城同时低 SSE 时，南京先占满；聚焦时提高当前城优先级。

## 验收标准

- [ ] 验证页五城按钮切换后，约 1–2km 高度均可见白色建筑块
- [ ] 主界面「定位到」上海/武汉/泰州/香港，落地后数秒内出现 content 请求且可见白模
- [ ] 全国俯瞰状态栏明确提示需定位，不声称「已看见建筑」
- [ ] Network 中无 `manifest.json` 的启动期巨型下载
- [ ] 切换城市后入口仍是最新 `index-*.js`（无旧包独占）

## 相关代码锚点

- `Wayline/src/utils/cityModels.js` — `CITY_MODEL_CATALOG`
- `Wayline/src/utils/CesiumManager.js` — `_ensureAllLocalCityModelTilesets`、`_prioritizeCityModelTileset`、`setActiveCityModel`、`_focusOnCityModel`
- `Wayline/src/components/plannerPanel.vue` — 「定位到」选择器
- `Wayline/src/components/mapContainer.vue` — 天地图全国初视角，勿抢飞城区
- `XJICloud/src/views/WaylineEditorView.vue` — iframe `/route/index.html?v=`
