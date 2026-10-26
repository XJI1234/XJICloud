// Preserved from the shipped map before aircraft integration; see public/sky-operator/MAP.CONTRACT.md.
// src/production/operator-console/renderer/cesium-config.ts
function createOrbitViewerOptions() {
  return {
    animation: false,
    timeline: false,
    geocoder: false,
    homeButton: false,
    sceneModePicker: false,
    baseLayerPicker: false,
    navigationHelpButton: false,
    fullscreenButton: false,
    infoBox: false,
    selectionIndicator: false,
    scene3DOnly: true,
    skyBox: false,
    skyAtmosphere: false,
    baseLayer: false,
    targetFrameRate: 60,
    msaaSamples: 1,
    requestRenderMode: true,
    maximumRenderTimeChange: Number.POSITIVE_INFINITY
  };
}
function attachWaylineImagery(Cesium, viewer2, tianDiTu) {
  viewer2.imageryLayers.removeAll();
  if (tianDiTu !== void 0) {
    viewer2.imageryLayers.addImageryProvider(new Cesium.UrlTemplateImageryProvider({
      url: tianDiTu.imageryUrl,
      credit: new Cesium.Credit("Map data (c) National Geomatics Center of China")
    }));
    viewer2.imageryLayers.addImageryProvider(new Cesium.UrlTemplateImageryProvider({
      url: tianDiTu.annotationUrl,
      credit: new Cesium.Credit("Map labels (c) National Geomatics Center of China")
    }));
    viewer2.scene.backgroundColor = Cesium.Color.fromCssColorString("#1d2e36");
    return "tianditu";
  }
  viewer2.imageryLayers.addImageryProvider(new Cesium.UrlTemplateImageryProvider({
    url: "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
    credit: new Cesium.Credit("Tiles (c) Esri")
  }));
  viewer2.scene.backgroundColor = Cesium.Color.fromCssColorString("#1d2e36");
  return "esri";
}

// src/production/operator-console/renderer/hangzhou-city-model.ts
var hangzhouCityCameraView = {
  longitude: 120.16654968261719,
  latitude: 30.321480751037598,
  height: 4e3,
  heading: 0.45,
  pitch: -Math.PI / 4,
  roll: 0
};
function hangzhouTilesetUrl(baseUrl) {
  return new URL("city-tiles/hangzhou/tileset.json", baseUrl).href;
}
async function loadHangzhouCityModel(Cesium, viewer2, baseUrl = document.baseURI) {
  const tileset = await Cesium.Cesium3DTileset.fromUrl(hangzhouTilesetUrl(baseUrl), {
    show: true,
    maximumScreenSpaceError: 12,
    skipLevelOfDetail: true,
    preferLeaves: true,
    dynamicScreenSpaceError: true
  });
  viewer2.scene.primitives.add(tileset);
  return tileset;
}
var nanjingCityCameraView = {
  longitude: 118.778,
  latitude: 32.043,
  height: 4e3,
  heading: 0.45,
  pitch: -Math.PI / 4,
  roll: 0
};
function nanjingTilesetUrl(baseUrl) {
  return new URL("city-tiles/nanjing/tileset.json", baseUrl).href;
}
function setNanjingCamera(Cesium, viewer2) {
  viewer2.camera.setView({
    destination: Cesium.Cartesian3.fromDegrees(
      nanjingCityCameraView.longitude,
      nanjingCityCameraView.latitude,
      nanjingCityCameraView.height
    ),
    orientation: {
      heading: nanjingCityCameraView.heading,
      pitch: nanjingCityCameraView.pitch,
      roll: nanjingCityCameraView.roll
    }
  });
  viewer2.scene.requestRender();
}
async function loadNanjingCityModel(Cesium, viewer2, baseUrl = document.baseURI) {
  const tileset = await Cesium.Cesium3DTileset.fromUrl(nanjingTilesetUrl(baseUrl), {
    show: true,
    maximumScreenSpaceError: 12,
    skipLevelOfDetail: true,
    preferLeaves: true,
    dynamicScreenSpaceError: true
  });
  viewer2.scene.primitives.add(tileset);
  return tileset;
}

// src/production/operator-console/renderer/route-map.ts
var cesium = () => {
  const value = globalThis.Cesium;
  if (value === void 0) throw new Error("Cesium \u672A\u52A0\u8F7D");
  return value;
};
var positionOf = (Cesium, point) => Cesium.Cartesian3.fromDegrees(point.longitude, point.latitude, point.altitude ?? 0);
var viewer;
var mapVisible = false;
var cameraMoving = false;
var mapIdleTimer;
function wakeRouteMap() {
  if (viewer === void 0 || viewer.isDestroyed() || !viewer.useDefaultRenderLoop) return;
  viewer.targetFrameRate = 60;
  clearTimeout(mapIdleTimer);
  // Explicit rendering still runs scene updates; lower those while idle too.
  mapIdleTimer = setTimeout(() => {
    if (viewer === void 0 || viewer.isDestroyed()) return;
    cameraMoving = false;
    viewer.targetFrameRate = 5;
  }, 1000);
}
function beginRouteMapInteraction() {
  cameraMoving = true;
  wakeRouteMap();
}
var drawnRouteId = null;
var drawnPositions = [];
var notice = "\u5BFC\u5165 Wayline \u5BFC\u51FA\u7684 KML \u6216 KMZ \u6587\u4EF6\u4EE5\u9884\u89C8\u822A\u8FF9\u3002";
var routeMapNotice = () => notice;
async function ensureRouteMap(host) {
  if (viewer !== void 0 && !viewer.isDestroyed()) {
    viewer.resize();
    return;
  }
  const Cesium = cesium();
  Cesium.Ion.defaultAccessToken = "";
  viewer = new Cesium.Viewer(host, createOrbitViewerOptions());
  // The first visibility transition must resize the host and request a frame.
  viewer.useDefaultRenderLoop = false;
  const provider = attachWaylineImagery(Cesium, viewer);
  viewer.scene.globe.depthTestAgainstTerrain = true;
  viewer.scene.screenSpaceCameraController.minimumZoomDistance = 20;
  viewer.scene.screenSpaceCameraController.enableTilt = true;
  setNanjingCamera(Cesium, viewer);
  cameraMoving = false;
  // Collision correction also emits camera events; only input/flight wakes the map.
  viewer.camera.moveEnd.addEventListener(() => {
    const wasMoving = cameraMoving;
    cameraMoving = false;
    if (wasMoving) wakeRouteMap();
  });
  const previousCameraView = Cesium.Matrix4.clone(viewer.camera.viewMatrix);
  viewer.scene.postRender.addEventListener(() => {
    // Tile arrival also redraws the scene; only actual camera motion extends activity.
    const currentCameraView = viewer.camera.viewMatrix;
    if (cameraMoving && !Cesium.Matrix4.equalsEpsilon(currentCameraView, previousCameraView, 1e-7)) wakeRouteMap();
    Cesium.Matrix4.clone(currentCameraView, previousCameraView);
  });
  for (const event of ["pointerdown", "wheel", "keydown", "touchstart"]) {
    host.addEventListener(event, beginRouteMapInteraction, { passive: true });
  }
  document.addEventListener("visibilitychange", () => setRouteMapVisible(mapVisible));
  const imagery = provider === "tianditu" ? "\u5929\u5730\u56FE\u5F71\u50CF" : "Esri \u536B\u661F\u56FE";
  // Optional building metadata must not delay route drawing or map interaction.
  void loadRouteCityModels(Cesium, viewer, imagery);
}
async function loadRouteCityModels(Cesium, routeViewer, imagery) {
  const loaded = [];
  const failed = [];
  try {
    await loadNanjingCityModel(Cesium, routeViewer);
    loaded.push("\u5357\u4EAC\u4E09\u7EF4\u767D\u6A21");
  } catch (error) {
    failed.push(`\u5357\u4EAC\u4E09\u7EF4\u767D\u6A21\u52A0\u8F7D\u5931\u8D25\uFF1A${error instanceof Error ? error.message : String(error)}`);
  }
  if (routeViewer.isDestroyed()) return;
  try {
    await loadHangzhouCityModel(Cesium, routeViewer);
    loaded.push("\u676D\u5DDE\u4E09\u7EF4\u767D\u6A21");
  } catch (error) {
    failed.push(`\u676D\u5DDE\u4E09\u7EF4\u767D\u6A21\u52A0\u8F7D\u5931\u8D25\uFF1A${error instanceof Error ? error.message : String(error)}`);
  }
  if (routeViewer.isDestroyed()) return;
  if (loaded.length > 0) {
    notice = `${loaded.join("\u3001")}\u5DF2\u52A0\u8F7D\uFF0C\u5F53\u524D\u5E95\u56FE\uFF1A${imagery}\u3002\u5BFC\u5165 Wayline \u5BFC\u51FA\u7684 KML \u6216 KMZ \u6587\u4EF6\u4EE5\u53E0\u52A0\u822A\u8FF9\u3002`;
  } else {
    notice = failed.join("\uFF1B") || `\u57CE\u5E02\u767D\u6A21\u52A0\u8F7D\u5931\u8D25\uFF0C\u5F53\u524D\u5E95\u56FE\uFF1A${imagery}\u3002`;
  }
}
function setRouteMapVisible(visible) {
  mapVisible = visible;
  if (viewer === void 0 || viewer.isDestroyed()) return;
  visible = visible && !document.hidden;
  if (viewer.useDefaultRenderLoop === visible) return;
  viewer.useDefaultRenderLoop = visible;
  if (visible) {
    viewer.resize();
    viewer.scene.requestRender();
    wakeRouteMap();
  } else {
    clearTimeout(mapIdleTimer);
  }
}
function showRoutePreview(routeId, preview, fly = true) {
  const Cesium = cesium();
  if (viewer === void 0 || viewer.isDestroyed()) return;
  const routeViewer = viewer;
  viewer.entities.removeAll();
  drawnRouteId = routeId;
  const positions = preview.polyline.map((point) => positionOf(Cesium, point));
  drawnPositions = positions;
  if (positions.length < 2) return;
  viewer.entities.add({
    name: "\u5BFC\u5165\u822A\u7EBF",
    polyline: {
      positions,
      width: 5,
      material: Cesium.Color.fromCssColorString("#d7f16a"),
      clampToGround: false
    }
  });
  preview.polyline.slice(1, -1).forEach((point, index) => {
    routeViewer.entities.add({
      name: `\u822A\u70B9${index + 2}`,
      position: positionOf(Cesium, point),
      point: { pixelSize: 8, color: Cesium.Color.fromCssColorString("#52d6ff"), outlineColor: Cesium.Color.BLACK, outlineWidth: 1 }
    });
  });
  viewer.entities.add({
    name: "\u8D77\u70B9",
    position: positionOf(Cesium, preview.startMarker),
    point: { pixelSize: 12, color: Cesium.Color.fromCssColorString("#d7f16a"), outlineColor: Cesium.Color.BLACK, outlineWidth: 2 },
    label: {
      text: "\u8D77\u70B9",
      font: "12px sans-serif",
      fillColor: Cesium.Color.WHITE,
      outlineColor: Cesium.Color.BLACK,
      outlineWidth: 2,
      style: Cesium.LabelStyle.FILL_AND_OUTLINE,
      verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
      pixelOffset: new Cesium.Cartesian2(0, -14)
    }
  });
  viewer.entities.add({
    name: "\u7EC8\u70B9",
    position: positionOf(Cesium, preview.endMarker),
    point: { pixelSize: 12, color: Cesium.Color.fromCssColorString("#ff9f43"), outlineColor: Cesium.Color.BLACK, outlineWidth: 2 },
    label: {
      text: "\u7EC8\u70B9",
      font: "12px sans-serif",
      fillColor: Cesium.Color.WHITE,
      outlineColor: Cesium.Color.BLACK,
      outlineWidth: 2,
      style: Cesium.LabelStyle.FILL_AND_OUTLINE,
      verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
      pixelOffset: new Cesium.Cartesian2(0, -14)
    }
  });
  if (fly) {
    beginRouteMapInteraction();
    viewer.camera.flyToBoundingSphere(Cesium.BoundingSphere.fromPoints(positions), {
      duration: 0.8,
      offset: new Cesium.HeadingPitchRange(0, -Math.PI / 2.8, 0)
    });
  }
  notice = `${preview.polyline.length} \u4E2A\u822A\u70B9`;
}
function locateDrawnRoute() {
  if (viewer === void 0 || viewer.isDestroyed() || drawnPositions.length < 2) return;
  const Cesium = cesium();
  beginRouteMapInteraction();
  viewer.camera.flyToBoundingSphere(Cesium.BoundingSphere.fromPoints(drawnPositions), {
    duration: 0.8,
    offset: new Cesium.HeadingPitchRange(0, -Math.PI / 2.8, 0)
  });
}
function clearRoutePreview() {
  drawnRouteId = null;
  drawnPositions = [];
  if (viewer === void 0 || viewer.isDestroyed()) return;
  viewer.entities.removeAll();
}
function drawnPreviewId() {
  return drawnRouteId;
}


export { clearRoutePreview, drawnPreviewId, ensureRouteMap, locateDrawnRoute, routeMapNotice, setRouteMapVisible, showRoutePreview };
export const getRouteMapViewer = () => viewer;
