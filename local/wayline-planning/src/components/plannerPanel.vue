<template>
    <div class="planner-panel">
    <div class="hero-card is-compact">
      <div>
        <p class="eyebrow">{{ plannerMeta.badge }}</p>
        <p class="hero-copy">{{ plannerMeta.description }}</p>
      </div>
    </div>

    <div class="panel-section" :class="{ collapsed: !sectionExpanded.camera }">
      <button class="section-header section-toggle" type="button" :aria-expanded="sectionExpanded.camera" @click="toggleSection('camera')">
        <div class="section-header-copy">
          <h2>相机参数</h2>
          <span>决定视场与覆盖能力</span>
        </div>
        <span class="section-toggle-indicator" :class="{ expanded: sectionExpanded.camera }">{{ sectionExpanded.camera ? '收起' : '展开' }}</span>
      </button>
      <div v-show="sectionExpanded.camera" class="section-body">
        <div class="form-grid two-columns">
          <label class="field">
            <span>焦距 mm</span>
            <el-input-number v-model="form.focalLengthMm" :min="1" :max="200" :step="1" controls-position="right" />
          </label>
          <label class="field">
            <span>像素数 万像素</span>
            <el-input-number v-model="form.megapixelsWan" :min="100" :max="10000" :step="100" controls-position="right" />
          </label>
        </div>
        <p class="hint">当前按 4:3 成像比例和 35mm 全画幅等效视场推算，适合无人机任务规划的快速预估。</p>
      </div>
    </div>

    <div class="panel-section" :class="{ collapsed: !sectionExpanded.target }">
      <button class="section-header section-toggle" type="button" :aria-expanded="sectionExpanded.target" @click="toggleSection('target')">
        <div class="section-header-copy">
          <h2>{{ isSearchPlanner ? '搜索定位' : isShapePlanner ? '图形框选' : '目标选区' }}</h2>
          <span>{{ targetSectionHint }}</span>
        </div>
        <span class="section-toggle-indicator" :class="{ expanded: sectionExpanded.target }">{{ sectionExpanded.target ? '收起' : '展开' }}</span>
      </button>
      <div v-show="sectionExpanded.target" class="section-body">
        <div v-if="isSearchPlanner" class="search-sampler">
          <div class="search-sampler-hero">
            <p class="search-sampler-kicker">Search to Sample</p>
            <h3>按楼名定位白模</h3>
            <p>输入楼宇、地标或地址关键词，选中后自动提取轮廓并生成采样航线。</p>
          </div>

          <label class="search-sampler-field">
            <span class="search-sampler-field-label">关键词</span>
            <div class="search-sampler-input-row">
              <span class="search-sampler-glyph" aria-hidden="true">⌕</span>
              <el-input
                v-model="buildingSearchQuery"
                clearable
                placeholder="例如：黄龙体育中心、阿里巴巴、浙江大学"
                @input="handleBuildingSearchInput"
                @clear="clearBuildingSearchResults"
                @keydown.enter.prevent="handleBuildingSearchEnter"
              />
            </div>
          </label>

          <div class="search-sampler-status">
            <p v-if="buildingSearchError" class="hint building-search-error">{{ buildingSearchError }}</p>
            <p v-else-if="buildingSearchLoading" class="hint">正在检索建筑索引…</p>
            <p v-else-if="buildingSearchQuery.trim() && !buildingSearchResults.length" class="hint">未找到匹配结果，可切换到「建筑采样」点选白模。</p>
            <p v-else-if="!buildingSearchQuery.trim()" class="hint">支持跨城中文楼名模糊匹配，结果会标注城市；命中后将飞到目标并完成选中。</p>
          </div>

          <div v-if="buildingSearchResults.length" class="search-result-list">
            <div class="search-result-list-head">
              <strong>搜索结果</strong>
              <span>{{ buildingSearchResults.length }} 条</span>
            </div>
            <button
              v-for="(item, index) in buildingSearchResults"
              :key="`${item.cityId || 'city'}-${item.id}`"
              type="button"
              class="search-result-item"
              @click="handleSelectSearchResult(item)"
            >
              <span class="search-result-index">{{ index + 1 }}</span>
              <span class="search-result-copy">
                <strong>
                  <span v-if="item.cityName" class="search-result-city">{{ item.cityName }}</span>
                  {{ item.name }}
                </strong>
                <span>{{ formatSearchResultMeta(item) }}</span>
              </span>
              <span class="search-result-badge" :class="item.match === 'contain' ? 'is-exact' : 'is-near'">
                {{ item.match === 'contain' ? '轮廓匹配' : item.match === 'nearest' ? '邻近匹配' : '待确认' }}
              </span>
            </button>
          </div>

          <div class="search-sampler-actions">
            <button
              class="action-button action-button-model"
              :class="{ active: selectionMode === 'model' }"
              @click="handleSelectModelTarget"
            >
              {{ selectionMode === 'model' ? '完成点选补选' : '地图点选补选' }}
            </button>
            <button
              v-if="selectedModelTargets.length"
              class="action-button action-button-alt"
              @click="handleClearSelectedModels"
            >
              清空已选
            </button>
          </div>
        </div>

        <div v-else-if="isShapePlanner" class="shape-sampler">
          <div class="shape-sampler-hero">
            <p class="shape-sampler-kicker">Draw to Sample</p>
            <h3>地图绘制采样区域</h3>
            <p>选择图形后在地图上点击绘制，完成后自动作为采样轮廓生成航线。</p>
          </div>

          <div class="shape-tool-grid">
            <button
              v-for="tool in shapeToolOptions"
              :key="tool.value"
              type="button"
              class="shape-tool-card"
              :class="{ active: shapeTool === tool.value && isShapeDrawing }"
              @click="handleStartShapeTool(tool.value)"
            >
              <span class="shape-tool-icon">{{ tool.icon }}</span>
              <span class="shape-tool-copy">
                <strong>{{ tool.label }}</strong>
                <span>{{ tool.description }}</span>
              </span>
            </button>
          </div>

          <div class="shape-status-card">
            <div class="shape-status-row">
              <span>当前工具</span>
              <strong>{{ currentShapeToolLabel }}</strong>
            </div>
            <div class="shape-status-row">
              <span>绘制状态</span>
              <strong>{{ shapeStatusText }}</strong>
            </div>
            <div class="shape-status-row">
              <span>已选顶点</span>
              <strong>{{ shapeDraft.vertices.length }} 个</strong>
            </div>
          </div>

          <p class="hint">{{ shapeGuideText }}</p>

          <div class="shape-sampler-actions">
            <button
              v-if="shapeTool === 'polygon' && isShapeDrawing"
              class="action-button action-button-model"
              :disabled="shapeDraft.vertices.length < 3"
              @click="handleFinalizeShapePolygon"
            >
              完成多边形
            </button>
            <button
              v-if="isShapeDrawing && shapeDraft.vertices.length"
              class="action-button action-button-alt"
              @click="handleUndoShapeVertex"
            >
              撤销一点
            </button>
            <button
              v-if="isShapeDrawing"
              class="action-button action-button-alt"
              @click="handleCancelShapeDrawing"
            >
              取消绘制
            </button>
            <button
              v-if="selectedModelTargets.length"
              class="action-button action-button-alt"
              @click="handleClearShapeSelection"
            >
              清空区域
            </button>
          </div>
        </div>

        <div v-else class="point-actions">
          <template v-if="isBuildingPlanner">
            <button class="action-button action-button-model" :class="{ active: selectionMode === 'model' }" @click="handleSelectModelTarget">
              {{ selectionMode === 'model' ? '完成多选' : '白模多选建筑' }}
            </button>
            <button v-if="selectedModelTargets.length" class="action-button action-button-alt" @click="handleClearSelectedModels">清空已选建筑</button>
          </template>
          <template v-if="!isBuildingPlanner">
            <button class="action-button" :class="{ active: selectionMode === 'center' }" @click="handleSelectPoint('center')">地图选中心点</button>
            <button class="action-button" :class="{ active: selectionMode === 'edge' }" @click="handleSelectPoint('edge')">地图选边缘点</button>
            <button class="action-button action-button-alt" @click="openHistoryDialog('center')">历史记录选中心点</button>
            <button class="action-button action-button-alt" @click="openHistoryDialog('edge')">历史记录选边缘点</button>
          </template>
        </div>
        <template v-if="!isBuildingPlanner">
          <div class="form-grid two-columns">
            <label class="field">
              <span>中心点经度</span>
              <el-input-number v-model="coordinateForm.centerLongitude" :precision="11" :step="0.000001" controls-position="right" />
            </label>
            <label class="field">
              <span>中心点纬度</span>
              <el-input-number v-model="coordinateForm.centerLatitude" :precision="11" :step="0.000001" controls-position="right" />
            </label>
            <label class="field">
              <span>边缘点经度</span>
              <el-input-number v-model="coordinateForm.edgeLongitude" :precision="11" :step="0.000001" controls-position="right" />
            </label>
            <label class="field">
              <span>边缘点纬度</span>
              <el-input-number v-model="coordinateForm.edgeLatitude" :precision="11" :step="0.000001" controls-position="right" />
            </label>
          </div>
          <div class="point-status">
            <div class="status-item">
              <span>中心点</span>
              <strong>{{ centerPoint ? formatPoint(centerPoint) : '未选择' }}</strong>
            </div>
            <div class="status-item">
              <span>边缘点</span>
              <strong>{{ edgePoint ? formatPoint(edgePoint) : '未选择' }}</strong>
            </div>
          </div>
        </template>
        <div v-if="isBuildingPlanner && selectedModelTarget" class="model-selection-card" :class="{ 'is-search': isSearchPlanner, 'is-shape': isShapePlanner }">
          <div class="model-selection-title">{{ isSearchPlanner ? '已锁定采样目标' : isShapePlanner ? '已框选采样区域' : '已选白模目标' }}</div>
          <div class="model-selection-grid">
            <div>
              <span>{{ isShapePlanner ? '区域类型' : '建筑数量' }}</span>
              <strong>{{ isShapePlanner ? (selectedModelTarget.shapeType === 'circle' ? '圆形' : selectedModelTarget.shapeType === 'rectangle' ? '矩形' : '多边形') : `${selectedModelTargets.length} 栋` }}</strong>
            </div>
            <div>
              <span>目标名称</span>
              <strong>{{ selectedModelTarget.name || '白模目标' }}</strong>
            </div>
            <div>
              <span>估算半径</span>
              <strong>{{ formatMeters(selectedModelTarget.radiusMeters) }}</strong>
            </div>
            <div>
              <span>建议低点高程</span>
              <strong>{{ formatMeters(selectedModelTarget.lowElevationM) }}</strong>
            </div>
            <div>
              <span>建议高点高程</span>
              <strong>{{ formatMeters(selectedModelTarget.highElevationM) }}</strong>
            </div>
          </div>
          <p v-if="selectedModelTargets.length > 1" class="hint">当前将根据多选建筑的整体外轮廓生成一条联合采样航线，适合街区或组团建筑统一采集。</p>
          <p v-else-if="isSearchPlanner" class="hint">已根据搜索结果匹配白模轮廓与高度范围，可直接生成采样航线。</p>
          <p v-else-if="isShapePlanner" class="hint">已根据绘制图形生成采样轮廓，请确认高度参数后生成航线。</p>
          <p v-else class="hint">系统已根据所点白模自动提取建筑轮廓与建议高度范围，可直接生成建筑采样航线。</p>
          <div v-if="selectedModelTargets.length && !isShapePlanner" class="model-selection-list">
            <span class="model-selection-list-label">已选建筑</span>
            <div class="model-selection-tags">
              <span v-for="target in selectedModelTargets" :key="target.targetId" class="model-selection-tag">{{ target.name || '白模目标' }}</span>
            </div>
          </div>
        </div>
        <p v-if="!isBuildingPlanner && historyEndpoint" class="hint">历史位置数据源：{{ historyEndpoint }}</p>
      </div>
    </div>

    <div class="panel-section" :class="{ collapsed: !sectionExpanded.sampling }">
      <button class="section-header section-toggle" type="button" :aria-expanded="sectionExpanded.sampling" @click="toggleSection('sampling')">
        <div class="section-header-copy">
          <h2>采样参数</h2>
          <span>核心任务参数</span>
        </div>
        <span class="section-toggle-indicator" :class="{ expanded: sectionExpanded.sampling }">{{ sectionExpanded.sampling ? '收起' : '展开' }}</span>
      </button>
      <div v-show="sectionExpanded.sampling" class="section-body">
      <div v-if="smartRecommendationContext" class="smart-recommend-card">
        <div class="smart-recommend-header">
          <div class="smart-recommend-copy">
            <strong>智能参数推荐</strong>
            <span>{{ smartRecommendationContext.message }}</span>
          </div>
          <div class="smart-recommend-actions">
            <button class="secondary-button smart-recommend-toggle" @click="smartRecommendExpanded = !smartRecommendExpanded">
              {{ smartRecommendExpanded ? '收起' : '展开' }}
            </button>
            <button class="secondary-button smart-recommend-apply" @click="applySmartPreset(smartParameterPresets.find((item) => item.key === 'gs3d') || smartParameterPresets[1])">应用 3DGS</button>
          </div>
        </div>
        <div class="smart-recommend-summary">
          <div class="smart-summary-item">
            <span>目标</span>
            <strong>{{ smartRecommendationContext.title }}</strong>
          </div>
          <div class="smart-summary-item">
            <span>半径</span>
            <strong>{{ formatMeters(smartRecommendationContext.radiusMeters) }}</strong>
          </div>
          <div class="smart-summary-item">
            <span>高度差</span>
            <strong>{{ formatMeters(smartRecommendationContext.heightSpanMeters) }}</strong>
          </div>
        </div>
        <div class="smart-preset-grid" :class="{ compact: !smartRecommendExpanded }">
          <div
            v-for="preset in visibleSmartParameterPresets"
            :key="preset.key"
            class="smart-preset-card"
            :class="{ featured: preset.key === 'gs3d' }"
          >
            <div class="smart-preset-head">
              <div>
                <span class="smart-preset-label">{{ preset.label }}</span>
                <strong>{{ preset.tagline }}</strong>
              </div>
            </div>
            <p v-if="smartRecommendExpanded" class="smart-preset-description">{{ preset.description }}</p>
            <div class="smart-preset-values">
              <span>GSD {{ preset.values.gsdMm.toFixed(1) }}</span>
              <span>高度 {{ preset.values.lowElevationM }}-{{ preset.values.highElevationM }}m</span>
              <span>俯角 {{ preset.values.pitchDeg }}°</span>
              <span>重叠 V{{ preset.values.verticalOverlapPercent }}% / H{{ preset.values.horizontalOverlapPercent }}%</span>
              <span v-if="smartRecommendExpanded && preset.values.photosPerLoop != null">张数 {{ preset.values.photosPerLoop }}</span>
            </div>
            <button class="action-button smart-preset-apply" @click="applySmartPreset(preset)">应用</button>
          </div>
        </div>
        <p class="hint smart-recommend-hint" v-if="isBuildingPlanner && smartRecommendExpanded">建筑模式按轮廓自动分段，单圈张数不参与计算。</p>
      </div>
      <div class="form-grid two-columns">
        <label class="field">
          <span>采样 GSD mm/像素</span>
          <el-input-number v-model="form.gsdMm" :min="0.1" :max="100" :step="0.1" controls-position="right" />
        </label>
        <label class="field">
          <span>低点高程 m</span>
          <el-input-number v-model="form.lowElevationM" :min="0" :max="10000" :step="1" controls-position="right" />
        </label>
        <label class="field">
          <span>高点高程 m</span>
          <el-input-number v-model="form.highElevationM" :min="0" :max="10000" :step="1" controls-position="right" />
        </label>
      </div>
      <div class="form-grid two-columns ig-reshoot-block">
        <label class="field full-width auto-photos-row">
          <span>信息增益补拍（3DGS）</span>
          <el-switch v-model="form.igReshootEnabled" inline-prompt active-text="开" inactive-text="关" />
        </label>
        <label class="field full-width" v-if="form.igReshootEnabled">
          <span>速度 ← → 质量（β 补拍预算）</span>
          <el-slider v-model="igQualitySpeedPercent" :min="0" :max="100" :step="5" show-input />
        </label>
        <p class="hint" v-if="form.igReshootEnabled">{{ igBalanceHint }}</p>
        <p class="hint" v-else>关闭后只生成种子航线，不追加 IG 补拍点。</p>
      </div>
      </div>
    </div>

    <div class="panel-section" :class="{ collapsed: !sectionExpanded.advanced }">
      <button class="section-header section-toggle" type="button" :aria-expanded="sectionExpanded.advanced" @click="toggleSection('advanced')">
        <div class="section-header-copy">
          <h2>高级参数</h2>
          <span>控制航线细节</span>
        </div>
        <span class="section-toggle-indicator" :class="{ expanded: sectionExpanded.advanced }">{{ sectionExpanded.advanced ? '收起' : '展开' }}</span>
      </button>
      <div v-show="sectionExpanded.advanced" class="section-body">
        <div class="form-grid two-columns">
          <label class="field">
            <span>镜头俯角 °</span>
            <el-input-number v-model="form.pitchDeg" :min="-60" :max="60" :step="1" controls-position="right" />
          </label>
          <label class="field">
            <span>单圈张数</span>
            <el-input-number
              v-model="form.photosPerLoop"
              :min="4"
              :max="200"
              :step="1"
              controls-position="right"
              :disabled="form.autoPhotosPerLoop || isBuildingPlanner"
            />
          </label>
          <label class="field full-width">
            <span>垂直重叠率 %</span>
            <el-slider v-model="form.verticalOverlapPercent" :min="10" :max="95" :step="1" show-input />
          </label>
          <label class="field full-width">
            <span>水平重叠率 %（3DGS / SfM）</span>
            <el-slider v-model="form.horizontalOverlapPercent" :min="40" :max="90" :step="1" show-input />
          </label>
          <label class="field full-width auto-photos-row" v-if="!isBuildingPlanner">
            <span>按水平重叠自动计算单圈张数</span>
            <el-switch v-model="form.autoPhotosPerLoop" inline-prompt active-text="开" inactive-text="关" />
          </label>
          <p class="hint" v-if="!isBuildingPlanner && form.autoPhotosPerLoop">
            建议单圈约 {{ recommendedOrbitPhotosPerLoop }} 张（HFOV × 水平重叠）
          </p>
        </div>
      </div>
    </div>

    <div class="panel-section">
      <div class="section-header">
        <h2>场景增强</h2>
        <span>多城市建筑白模（南京 / 上海 / 武汉 / 泰州 / 香港）</span>
      </div>
      <div class="toggle-row">
        <div class="toggle-copy">
          <strong>建筑白模</strong>
          <span>{{ mapProvider === 'tencent' ? '切换到 Cesium 场景后可启用' : '五城已挂载；侧栏「定位到」飞近后可见白模' }}</span>
        </div>
        <el-switch v-model="cityModelEnabledProxy" :disabled="mapProvider === 'tencent'" inline-prompt active-text="开" inactive-text="关" />
      </div>
      <div class="city-model-picker" v-if="mapProvider !== 'tencent'">
        <span class="city-model-picker-label">定位到</span>
        <el-select
          v-model="activeCityModelIdProxy"
          size="small"
          placeholder="选择城市"
          :disabled="!cityModelEnabledProxy"
          style="width: 100%"
        >
          <el-option
            v-for="city in cityModelOptions"
            :key="city.id"
            :label="city.name"
            :value="city.id"
          />
        </el-select>
      </div>
      <p class="hint">{{ cityModelHint }}</p>
    </div>

    <div class="panel-actions">
      <div class="panel-actions-primary">
        <button class="primary-button primary-action-button" :disabled="!canGenerate || planningInProgress" @click="handleGenerate">{{ planningInProgress ? '正在结合白模重规划…' : plannerMeta.generateText }}</button>
      </div>
      <div class="panel-actions-secondary">
        <button class="secondary-button secondary-action-button" @click="handleClear">清空航线</button>
        <div ref="exportDropdownRef" class="export-dropdown" :class="{ open: exportMenuOpen, disabled: !routePlan }">
          <button class="secondary-button secondary-action-button export-trigger" type="button" :disabled="!routePlan" @click.stop="toggleExportMenu">
            <span>导出航线</span>
            <span class="export-trigger-meta" :class="{ open: exportMenuOpen }">
              <span class="export-trigger-chevron"></span>
            </span>
          </button>
          <transition name="export-menu-fade">
            <div v-if="exportMenuOpen && routePlan" class="export-menu">
              <button class="export-option" type="button" @click="handleExport('kml')">
                <strong>KML</strong>
              </button>
              <button class="export-option" type="button" @click="handleExport('kmz')">
                <strong>KMZ</strong>
              </button>
            </div>
          </transition>
        </div>
      </div>
    </div>

    <div class="panel-section stats-section" :class="{ collapsed: !sectionExpanded.result }" v-if="routePlan">
      <button class="section-header section-toggle" type="button" :aria-expanded="sectionExpanded.result" @click="toggleSection('result')">
        <div class="section-header-copy">
          <h2>规划结果</h2>
          <span>自动估算任务负载</span>
        </div>
        <span class="section-toggle-indicator" :class="{ expanded: sectionExpanded.result }">{{ sectionExpanded.result ? '收起' : '展开' }}</span>
      </button>
      <div v-show="sectionExpanded.result" class="section-body">
      <div class="stats-grid">
        <div class="stat-card" v-if="routePlan.multiUav">
          <span>并行机数</span>
          <strong>{{ routePlan.droneCount }}</strong>
        </div>
        <div class="stat-card">
          <span>环绕层数</span>
          <strong>{{ routePlan.summary.ringCount }}</strong>
        </div>
        <div class="stat-card">
          <span>{{ routePlan.multiUav ? '总航线里程' : '航线里程' }}</span>
          <strong>{{ formatMeters(routePlan.summary.pathLengthMeters) }}</strong>
        </div>
        <div class="stat-card">
          <span>{{ routePlan.multiUav ? '并行航时' : '预计航时' }}</span>
          <strong>{{ formatDuration(routePlan.summary.estimatedFlightTimeSeconds) }}</strong>
        </div>
        <div class="stat-card" v-if="routePlan.multiUav && routePlan.fleetSummary">
          <span>相对单机节省</span>
          <strong>{{ routePlan.fleetSummary.timeSavingPercent }}%</strong>
        </div>
        <div class="stat-card">
          <span>航点数量</span>
          <strong>{{ routePlan.summary.waypointCount }}</strong>
        </div>
        <div class="stat-card">
          <span>影像数量</span>
          <strong>{{ routePlan.summary.imageCount }}</strong>
        </div>
        <div class="stat-card">
          <span>拍摄间隔</span>
          <strong>{{ routePlan.summary.shotIntervalSeconds.toFixed(1) }} s</strong>
        </div>
        <div class="stat-card" v-if="routePlan.obstacleAnalysis?.analyzed">
          <span>避障状态</span>
          <strong>{{ obstacleStatusText }}</strong>
        </div>
        <div class="stat-card" v-if="routePlan.obstacleAnalysis?.analyzed">
          <span>绕飞航段</span>
          <strong>{{ routePlan.obstacleAnalysis.detouredSegments }}</strong>
        </div>
        <div class="stat-card" v-if="routePlan.obstacleAnalysis?.analyzed">
          <span>风险航段</span>
          <strong>{{ routePlan.obstacleAnalysis.riskySegments }}</strong>
        </div>
      </div>
      <div class="detail-grid">
        <div class="stat-card" v-if="routePlan.informationGainReshoot?.enabled">
          <span>IG 补拍点</span>
          <strong>{{ routePlan.informationGainReshoot.selectedCount }}</strong>
        </div>
        <div class="stat-card" v-if="routePlan.informationGainReshoot?.coverageImprovementRatio != null">
          <span>覆盖缺口改善</span>
          <strong>{{ Math.round((routePlan.informationGainReshoot.coverageImprovementRatio || 0) * 100) }}%</strong>
        </div>
        <div class="detail-item">
          <span>目标半径</span>
          <strong>{{ formatMeters(routePlan.targetRadiusMeters) }}</strong>
        </div>
        <div class="detail-item">
          <span>环绕半径</span>
          <strong>{{ formatMeters(routePlan.orbitRadiusMeters) }}</strong>
        </div>
        <div class="detail-item">
          <span>垂直覆盖</span>
          <strong>{{ formatMeters(routePlan.verticalCoverageMeters) }}</strong>
        </div>
        <div class="detail-item">
          <span>层间高差</span>
          <strong>{{ formatMeters(routePlan.summary.verticalStepMeters) }}</strong>
        </div>
        <div class="detail-item">
          <span>水平视场</span>
          <strong>{{ routePlan.fieldOfView.horizontalFovDeg.toFixed(1) }}°</strong>
        </div>
        <div class="detail-item">
          <span>垂直视场</span>
          <strong>{{ routePlan.fieldOfView.verticalFovDeg.toFixed(1) }}°</strong>
        </div>
        <div class="detail-item" v-if="routePlan.obstacleAnalysis?.minimumClearanceMeters != null">
          <span>最小净空</span>
          <strong>{{ formatMeters(routePlan.obstacleAnalysis.minimumClearanceMeters) }}</strong>
        </div>
        <div class="detail-item" v-if="routePlan.obstacleAnalysis?.maximumObstacleHeightMeters != null">
          <span>碰撞区最高白模</span>
          <strong>{{ formatMeters(routePlan.obstacleAnalysis.maximumObstacleHeightMeters) }}</strong>
        </div>
      </div>
      <div class="fleet-legend" v-if="routePlan.missions?.length">
        <div class="fleet-legend-title">机队航线</div>
        <div class="fleet-legend-list">
          <div
            v-for="mission in routePlan.missions"
            :key="mission.droneId"
            class="fleet-legend-item"
          >
            <span class="fleet-legend-swatch" :style="{ background: mission.color || getDroneColor(mission.droneIndex) }"></span>
            <div class="fleet-legend-copy">
              <strong>{{ mission.droneId }}</strong>
              <span>{{ formatMeters(mission.summary?.pathLengthMeters) }} · {{ formatDuration(mission.summary?.estimatedFlightTimeSeconds) }} · {{ mission.summary?.waypointCount || 0 }} 点</span>
            </div>
          </div>
        </div>
        <p
          v-for="message in (routePlan.fleetSummary?.messages || [])"
          :key="message"
          class="hint"
        >{{ message }}</p>
      </div>
      <div class="obstacle-analysis" v-if="routePlan.informationGainReshoot?.messages?.length">
        <p v-for="message in routePlan.informationGainReshoot.messages" :key="message" class="analysis-message">
          {{ message }}
        </p>
      </div>
      <div class="obstacle-analysis" v-if="routePlan.obstacleAnalysis">
        <p v-for="message in routePlan.obstacleAnalysis.messages" :key="message" class="analysis-message">
          {{ message }}
        </p>
        <div v-if="routePlan.obstacleAnalysis.suggestions?.length" class="suggestion-panel">
          <span class="suggestion-title">参数建议</span>
          <ul class="suggestion-list">
            <li v-for="suggestion in routePlan.obstacleAnalysis.suggestions" :key="suggestion">{{ suggestion }}</li>
          </ul>
        </div>
      </div>
      <p class="hint">预计航时按默认巡航速度 5m/s 估算。拖动地图查看 3D 航点、圈层和完整螺旋航迹。</p>
      </div>
    </div>

  </div>

  <el-dialog
    v-model="historyDialogVisible"
    :title="historyDialogMode === 'center' ? '从历史位置中选择中心点' : '从历史位置中选择边缘点'"
    :z-index="3200"
    class="history-dialog"
    append-to-body
    align-center
    top="6vh"
  >
      <div class="dialog-toolbar">
        <div class="dialog-status">
          <span>可选位置 {{ historyRecords.length }} 条</span>
          <span v-if="historyEndpoint">来源：{{ historyEndpoint }}</span>
        </div>
        <button class="secondary-button dialog-refresh" :disabled="historyLoading" @click="loadHistoryRecords">刷新列表</button>
      </div>

      <el-table v-loading="historyLoading" :data="historyRecords" height="420" empty-text="暂无可用历史位置记录">
        <el-table-column label="上传时间" min-width="170">
          <template #default="scope">
            <span>{{ formatHistoryTime(scope.row.receivedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="记录 ID" min-width="230" show-overflow-tooltip />
        <el-table-column label="纬度" min-width="160">
          <template #default="scope">
            <span>{{ formatCoordinate(scope.row.latitude) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="经度" min-width="160">
          <template #default="scope">
            <span>{{ formatCoordinate(scope.row.longitude) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="source" label="来源" min-width="140" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <button class="primary-button table-select-button" @click="applyHistoryRecord(scope.row)">选用</button>
          </template>
        </el-table-column>
      </el-table>
  </el-dialog>
</template>

<script setup>
  import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
  import { storeToRefs } from 'pinia'
  import { ElMessage } from 'element-plus'
  import JSZip from 'jszip'
  import { useMapStore } from '@/stores/map'
  import { createSceneObstacleAnalysisOptions } from '@/algorithm/adapters/sceneObstacleAdapter'
  import { planMissionWithObstacleAnalysis } from '@/algorithm/core/missionPlanner'
  import { recommendOrbitPhotosPerLoop } from '@/utils/routePlanner'
  import {
    haversineDistance,
    toWpmlHeadingAngle,
    formatHeadingLabel,
  } from '@/utils/routePlanner'
  import {
    getDroneColor,
    normalizeDroneCount,
  } from '@/utils/multiUavPlanner'
  import { fetchLocationHistory } from '@/utils/locationHistory'
  import { loadBuildingSearchIndex, searchBuildings } from '@/utils/buildingSearch'
  import { CITY_MODEL_CATALOG } from '@/utils/cityModels'
  import { SHAPE_TOOLS, getShapeToolLabel } from '@/utils/shapeFootprint'

  const props = defineProps({
    plannerType: {
      type: String,
      default: 'orbit',
    },
  })

  const mapStore = useMapStore()
  const {
    centerPoint,
    edgePoint,
    routePlan,
    selectionMode,
    mapProvider,
    cityModelEnabled,
    cityModelStatus,
    activeCityModelId,
    cesiumManagerInfo,
    selectedModelTarget,
    selectedModelTargets,
    shapeTool,
    shapeDraft,
    droneCount,
  } = storeToRefs(mapStore)
  const {
    setSelectionMode,
    setCenterPoint,
    setEdgePoint,
    setRoutePlan,
    clearRoutePlan,
    setCityModelEnabled,
    setActiveCityModelId,
    clearSelectedModelTargets,
    setSelectedModelTarget,
    beginShapeDrawing,
    cancelShapeDrawing,
    undoShapeVertex,
    finalizeShapePolygon,
  } = mapStore

  const form = reactive({
    focalLengthMm: 24,
    megapixelsWan: 4800,
    gsdMm: 5,
    lowElevationM: 0,
    highElevationM: 60,
    pitchDeg: -15,
    photosPerLoop: 36,
    verticalOverlapPercent: 75,
    horizontalOverlapPercent: 70,
    autoPhotosPerLoop: true,
    igReshootEnabled: true,
    igQualitySpeedBalance: 0.55,
    droneCount: normalizeDroneCount(droneCount.value),
  })

  const coordinateForm = reactive({
    centerLongitude: undefined,
    centerLatitude: undefined,
    edgeLongitude: undefined,
    edgeLatitude: undefined,
  })
  const historyDialogVisible = ref(false)
  const historyDialogMode = ref('center')
  const historyRecords = ref([])
  const historyLoading = ref(false)
  const historyEndpoint = ref('')
  const planningInProgress = ref(false)
  const exportMenuOpen = ref(false)
  const exportDropdownRef = ref(null)
  const smartRecommendExpanded = ref(false)
  const buildingSearchQuery = ref('')
  const buildingSearchResults = ref([])
  const buildingSearchLoading = ref(false)
  const buildingSearchError = ref('')
  const sectionExpanded = reactive({
    camera: false,
    target: true,
    sampling: true,
    advanced: false,
    result: false,
  })
  let planningRequestId = 0
  let buildingSearchTimer = null
  let buildingSearchRequestId = 0

  const toggleSection = (sectionKey) => {
    sectionExpanded[sectionKey] = !sectionExpanded[sectionKey]
  }

  const closeExportMenu = () => {
    exportMenuOpen.value = false
  }

  const toggleExportMenu = () => {
    if (!routePlan.value) {
      ElMessage.warning('请先生成航线')
      return
    }

    exportMenuOpen.value = !exportMenuOpen.value
  }

  const handleDocumentClick = (event) => {
    if (!exportDropdownRef.value?.contains(event.target)) {
      closeExportMenu()
    }
  }

  onMounted(() => {
    document.addEventListener('click', handleDocumentClick)
    if (props.plannerType === 'search') {
      loadBuildingSearchIndex()
    }
  })

  onBeforeUnmount(() => {
    document.removeEventListener('click', handleDocumentClick)
    if (buildingSearchTimer) {
      window.clearTimeout(buildingSearchTimer)
      buildingSearchTimer = null
    }
  })

  const clampNumber = (value, min, max) => Math.min(Math.max(Number(value) || 0, min), max)
  const roundToStep = (value, step = 1) => Math.round((Number(value) || 0) / step) * step
  const roundToEven = (value) => {
    const rounded = Math.max(4, Math.round((Number(value) || 0) / 2) * 2)
    return rounded % 2 === 0 ? rounded : rounded + 1
  }

  const isSearchPlanner = computed(() => props.plannerType === 'search')
  const isShapePlanner = computed(() => props.plannerType === 'shape')
  const isBuildingPlanner = computed(() => props.plannerType === 'building' || props.plannerType === 'search' || props.plannerType === 'shape')
  const planningPlannerType = computed(() => (isBuildingPlanner.value ? 'building' : 'orbit'))
  const shapeToolOptions = SHAPE_TOOLS
  const isShapeDrawing = computed(() => `${selectionMode.value || ''}`.startsWith('shape-'))
  const currentShapeToolLabel = computed(() => {
    if (!shapeTool.value) {
      return '未选择'
    }

    return getShapeToolLabel(shapeTool.value)
  })
  const shapeStatusText = computed(() => {
    if (selectedModelTarget.value?.detectionSource === 'shape-draw' && !isShapeDrawing.value) {
      return '框选完成'
    }

    if (isShapeDrawing.value) {
      return '绘制中'
    }

    return '待开始'
  })
  const shapeGuideText = computed(() => {
    if (!isShapeDrawing.value) {
      return '选择上方图形后开始在地图绘制；矩形与圆形两点完成，多边形需至少 3 点后点击「完成多边形」。'
    }

    if (shapeTool.value === 'polygon') {
      return '连续点击地图添加顶点，至少 3 点后可完成；移动鼠标可预览连线。'
    }

    if (shapeTool.value === 'rectangle') {
      return '先点第一个对角点，移动鼠标预览矩形，再点对角另一点完成。'
    }

    return '先点圆心，移动鼠标预览半径，再点圆周完成圆形区域。'
  })

  const targetSectionHint = computed(() => {
    if (isSearchPlanner.value) {
      return '搜索楼名定位白模，自动提取轮廓与高度'
    }

    if (isShapePlanner.value) {
      return '选择图形并在地图绘制区域轮廓'
    }

    if (props.plannerType === 'building') {
      return '选择白模模型，自动提取建筑轮廓与高度范围'
    }

    return '先选中心点，再选边缘点'
  })

  const plannerMeta = computed(() => {
    if (isSearchPlanner.value) {
      return {
        title: '搜索采样',
        description: '用楼名或地标快速锁定白模目标，自动提取轮廓与高度区间并生成采样航线。',
        badge: 'Search Sample',
        generateText: '生成搜索采样航线',
        successLabel: '搜索采样',
      }
    }

    if (isShapePlanner.value) {
      return {
        title: '图形采样',
        description: '选择多边形、矩形或圆形，在地图上绘制框选区域，自动生成环绕采样航线。',
        badge: 'Shape Sample',
        generateText: '生成图形采样航线',
        successLabel: '图形采样',
      }
    }

    if (props.plannerType === 'building') {
      return {
        title: '建筑采样',
        description: '支持连续多选白模建筑，自动提取整体轮廓与高度区间，快速生成联合采样航线。',
        badge: 'Building Scan',
        generateText: '生成建筑采样航线',
        successLabel: '建筑采样',
      }
    }

    return {
      title: '单点环绕采样',
      description: '自动根据相机、GSD、目标高度范围和两点选区，生成专业的螺旋环绕采样航线。',
      badge: '3D Orbit Scan',
      generateText: '生成环绕航线',
      successLabel: '单点环绕采样',
    }
  })

  const smartRecommendationContext = computed(() => {
    if (isBuildingPlanner.value) {
      if (!selectedModelTarget.value?.footprintPoints?.length) {
        return null
      }

      const lowElevationM = Number(selectedModelTarget.value.lowElevationM ?? form.lowElevationM)
      const highElevationM = Math.max(Number(selectedModelTarget.value.highElevationM ?? form.highElevationM), lowElevationM + 10)

      return {
        missionType: 'building',
        title: selectedModelTarget.value.name || '当前白模目标',
        radiusMeters: clampNumber(selectedModelTarget.value.radiusMeters || 30, 10, 180),
        lowElevationM,
        highElevationM,
        heightSpanMeters: Math.max(highElevationM - lowElevationM, 10),
        message: selectedModelTargets.value.length > 1
          ? '已按多建筑轮廓生成推荐。'
          : '已按白模轮廓生成推荐。',
      }
    }

    if (!centerPoint.value) {
      return null
    }

    const hasEdgePoint = Boolean(edgePoint.value)
    const estimatedRadiusMeters = hasEdgePoint
      ? haversineDistance(centerPoint.value, edgePoint.value)
      : 30
    const lowElevationM = Number(form.lowElevationM || 0)
    const highElevationM = Math.max(Number(form.highElevationM || 0), lowElevationM + 10)

    return {
      missionType: 'orbit',
      title: hasEdgePoint ? '当前中心点与边缘点' : '当前中心点',
      radiusMeters: clampNumber(estimatedRadiusMeters, 10, 220),
      lowElevationM,
      highElevationM,
      heightSpanMeters: Math.max(highElevationM - lowElevationM, 20),
      message: hasEdgePoint
        ? '已按两点距离生成推荐。'
        : '已按中心点生成初步推荐。',
    }
  })

  const smartParameterPresets = computed(() => {
    const context = smartRecommendationContext.value
    if (!context) {
      return []
    }

    const radiusMeters = context.radiusMeters
    const heightSpanMeters = context.heightSpanMeters
    const isBuildingMode = context.missionType === 'building'

    const baseGsdMm = isBuildingMode
      ? radiusMeters <= 18
        ? 3.5
        : radiusMeters <= 35
          ? 4.5
          : radiusMeters <= 60
            ? 5.5
            : radiusMeters <= 100
              ? 6.5
              : 8
      : radiusMeters <= 15
        ? 4
        : radiusMeters <= 30
          ? 5
          : radiusMeters <= 60
            ? 6
            : radiusMeters <= 100
              ? 8
              : 10

    const basePitchDeg = isBuildingMode
      ? radiusMeters <= 20
        ? -20
        : radiusMeters <= 45
          ? -17
          : -14
      : radiusMeters <= 18
        ? -18
        : radiusMeters <= 50
          ? -14
          : -11

    const baseOverlap = isBuildingMode
      ? heightSpanMeters >= 80
        ? 76
        : 72
      : heightSpanMeters >= 80
        ? 72
        : 68

    const basePhotosPerLoop = radiusMeters <= 18
      ? 20
      : radiusMeters <= 35
        ? 24
        : radiusMeters <= 60
          ? 30
          : radiusMeters <= 100
            ? 38
            : 46

    const baseLowElevation = roundToStep(Math.max(context.lowElevationM, 0), 5)
    const baseHighElevation = roundToStep(Math.max(context.highElevationM, baseLowElevation + 10), 5)

    const createValues = ({
      gsdFactor,
      pitchOffset,
      overlapOffset,
      horizontalOverlap,
      lowOffset,
      highOffset,
      photosOffset,
      autoPhotosPerLoop = false,
    }) => {
      const verticalOverlapPercent = clampNumber(roundToStep(baseOverlap + overlapOffset, 1), 55, 85)
      const horizontalOverlapPercent = clampNumber(roundToStep(horizontalOverlap, 1), 40, 90)
      const recommendedPhotos = recommendOrbitPhotosPerLoop({
        focalLengthMm: form.focalLengthMm,
        horizontalOverlapPercent,
      })
      const heuristicPhotos = clampNumber(roundToEven(basePhotosPerLoop + photosOffset), 12, 96)

      return {
        gsdMm: clampNumber(roundToStep(baseGsdMm * gsdFactor, 0.5), 1.5, 20),
        lowElevationM: Math.max(0, roundToStep(baseLowElevation + lowOffset, 5)),
        highElevationM: Math.max(roundToStep(baseHighElevation + highOffset, 5), roundToStep(baseLowElevation + lowOffset + 10, 5)),
        pitchDeg: clampNumber(roundToStep(basePitchDeg + pitchOffset, 1), -35, -6),
        verticalOverlapPercent,
        horizontalOverlapPercent,
        autoPhotosPerLoop: Boolean(autoPhotosPerLoop),
        photosPerLoop: isBuildingMode
          ? null
          : autoPhotosPerLoop
            ? recommendedPhotos
            : Math.max(heuristicPhotos, autoPhotosPerLoop ? recommendedPhotos : 0),
      }
    }

    return [
      {
        key: 'efficient',
        label: '效率优先',
        tagline: '更快',
        description: '覆盖优先，飞行更省时。',
        values: createValues({
          gsdFactor: 1.35,
          pitchOffset: 3,
          overlapOffset: -6,
          horizontalOverlap: 55,
          lowOffset: 0,
          highOffset: 0,
          photosOffset: -6,
          autoPhotosPerLoop: false,
        }),
        igReshootEnabled: true,
        igQualitySpeedBalance: 0.25,
      },
      {
        key: 'balanced',
        label: '均衡推荐',
        tagline: '默认首选',
        description: '清晰度与效率较均衡。',
        values: createValues({
          gsdFactor: 1,
          pitchOffset: 0,
          overlapOffset: 0,
          horizontalOverlap: 70,
          lowOffset: 0,
          highOffset: 5,
          photosOffset: 0,
          autoPhotosPerLoop: true,
        }),
        igReshootEnabled: true,
        igQualitySpeedBalance: 0.55,
      },
      {
        key: 'gs3d',
        label: '3DGS 素材',
        tagline: '推荐',
        description: '高重叠密采样，适合后续 3D 高斯重建。',
        values: createValues({
          gsdFactor: 0.85,
          pitchOffset: -3,
          overlapOffset: 8,
          horizontalOverlap: 75,
          lowOffset: -5,
          highOffset: 10,
          photosOffset: 12,
          autoPhotosPerLoop: true,
        }),
        igReshootEnabled: true,
        igQualitySpeedBalance: 0.72,
      },
      {
        key: 'detail',
        label: '精细优先',
        tagline: '更细',
        description: '密度更高，适合细节采集。',
        values: createValues({
          gsdFactor: 0.8,
          pitchOffset: -4,
          overlapOffset: 8,
          horizontalOverlap: 80,
          lowOffset: -5,
          highOffset: 10,
          photosOffset: 8,
          autoPhotosPerLoop: true,
        }),
        igReshootEnabled: true,
        igQualitySpeedBalance: 0.85,
      },
    ]
  })

  const recommendedOrbitPhotosPerLoop = computed(() => {
    const radiusHint = smartRecommendationContext.value?.radiusMeters
    const gsdMm = Number(form.gsdMm) || 5
    const megapixelsWan = Number(form.megapixelsWan) || 4800
    const totalPixels = Math.max(megapixelsWan * 10000, 1)
    const widthPx = Math.sqrt(totalPixels * (4 / 3))
    const coverageWidthMeters = (widthPx * gsdMm) / 1000
    // Approximate orbit radius ≈ target radius + standoff; standoff ≈ coverage/(2tan(hfov/2))
    const focal = Math.max(Number(form.focalLengthMm) || 24, 1)
    const hfovRad = 2 * Math.atan(36 / (2 * focal))
    const standOff = coverageWidthMeters / (2 * Math.tan(hfovRad / 2))
    const orbitRadiusMeters = Number.isFinite(radiusHint)
      ? Math.max(radiusHint + standOff, standOff)
      : undefined

    return recommendOrbitPhotosPerLoop({
      focalLengthMm: form.focalLengthMm,
      horizontalOverlapPercent: form.horizontalOverlapPercent,
      orbitRadiusMeters,
      imageCoverageWidthMeters: coverageWidthMeters,
    })
  })

  const igQualitySpeedPercent = computed({
    get: () => Math.round(clampNumber(Number(form.igQualitySpeedBalance) * 100, 0, 100)),
    set: (value) => {
      form.igQualitySpeedBalance = clampNumber(Number(value) / 100, 0, 1)
    },
  })

  const igBalanceHint = computed(() => {
    const beta = Number(form.igQualitySpeedBalance) || 0
    if (beta < 0.34) {
      return '速度优先：少量补拍，只填最大覆盖空洞。'
    }
    if (beta > 0.66) {
      return '质量优先：更多补拍 + 次外圈视差，利于 3DGS。'
    }
    return '均衡：在航时与重建完整性之间折中。'
  })

  const visibleSmartParameterPresets = computed(() => {
    if (smartRecommendExpanded.value) {
      return smartParameterPresets.value
    }

    return smartParameterPresets.value.filter((preset) => preset.key === 'gs3d' || preset.key === 'balanced')
  })

  watch(
    () => smartRecommendationContext.value?.title,
    () => {
      smartRecommendExpanded.value = false
    },
  )

  watch(
    () => Boolean(routePlan.value),
    (hasRoutePlan, hadRoutePlan) => {
      if (hasRoutePlan && !hadRoutePlan) {
        sectionExpanded.result = true
      }

      if (!hasRoutePlan) {
        closeExportMenu()
      }
    },
  )

  const applySmartPreset = (preset) => {
    if (!preset?.values) {
      return
    }

    form.gsdMm = preset.values.gsdMm
    form.lowElevationM = preset.values.lowElevationM
    form.highElevationM = preset.values.highElevationM
    form.pitchDeg = preset.values.pitchDeg
    form.verticalOverlapPercent = preset.values.verticalOverlapPercent
    form.horizontalOverlapPercent = preset.values.horizontalOverlapPercent ?? form.horizontalOverlapPercent
    form.autoPhotosPerLoop = Boolean(preset.values.autoPhotosPerLoop)

    if (preset.igReshootEnabled != null) {
      form.igReshootEnabled = Boolean(preset.igReshootEnabled)
    }
    if (preset.igQualitySpeedBalance != null) {
      form.igQualitySpeedBalance = Number(preset.igQualitySpeedBalance)
    }

    if (preset.values.photosPerLoop != null) {
      form.photosPerLoop = preset.values.photosPerLoop
    } else if (form.autoPhotosPerLoop) {
      form.photosPerLoop = recommendOrbitPhotosPerLoop({
        focalLengthMm: form.focalLengthMm,
        horizontalOverlapPercent: form.horizontalOverlapPercent,
      })
    }

    ElMessage.success(`已应用${preset.label}参数推荐`)
  }

  watch(
    [
      () => form.autoPhotosPerLoop,
      () => form.focalLengthMm,
      () => form.horizontalOverlapPercent,
    ],
    () => {
      if (!form.autoPhotosPerLoop || isBuildingPlanner.value) {
        return
      }

      form.photosPerLoop = recommendOrbitPhotosPerLoop({
        focalLengthMm: form.focalLengthMm,
        horizontalOverlapPercent: form.horizontalOverlapPercent,
      })
    },
  )

  watch(
    centerPoint,
    (point) => {
      coordinateForm.centerLongitude = point?.longitude
      coordinateForm.centerLatitude = point?.latitude
    },
    { immediate: true, deep: true },
  )

  watch(
    edgePoint,
    (point) => {
      coordinateForm.edgeLongitude = point?.longitude
      coordinateForm.edgeLatitude = point?.latitude
    },
    { immediate: true, deep: true },
  )

  const canGenerate = computed(() => {
    if (isBuildingPlanner.value) {
      return Boolean(selectedModelTarget.value?.footprintPoints?.length >= 3 && form.highElevationM >= form.lowElevationM)
    }

    return Boolean(centerPoint.value && edgePoint.value && form.highElevationM >= form.lowElevationM)
  })

  const cityModelEnabledProxy = computed({
    get: () => cityModelEnabled.value,
    set: (value) => {
      setCityModelEnabled(value)
    },
  })

  const cityModelOptions = CITY_MODEL_CATALOG.map((city) => ({
    id: city.id,
    name: city.name,
  }))

  const activeCityModelIdProxy = computed({
    get: () => activeCityModelId.value,
    set: (value) => {
      setActiveCityModelId(value)
      if (cesiumManagerInfo.value?.setActiveCityModel) {
        void cesiumManagerInfo.value.setActiveCityModel(value, { flyTo: true })
      }
      clearBuildingSearchResults()
      if (buildingSearchQuery.value.trim()) {
        void runBuildingSearch(buildingSearchQuery.value)
      }
    },
  })

  const cityModelHint = computed(() => {
    if (mapProvider.value === 'tencent') {
      return '当前使用腾讯地图 GL，建筑白模不会加载。'
    }

    return cityModelStatus.value
  })

  const obstacleStatusText = computed(() => {
    const status = routePlan.value?.obstacleAnalysis?.status

    if (status === 'safe') {
      return '已通过白模检查'
    }

    if (status === 'detoured') {
      return '已自动绕障'
    }

    if (status === 'risky') {
      return '存在碰撞风险'
    }

    if (status === 'unavailable') {
      return '未执行白模分析'
    }

    return '未分析'
  })

  const formatPoint = (point) => `${point.longitude.toFixed(6)}, ${point.latitude.toFixed(6)}`
  const formatCoordinate = (value) => Number(value || 0).toFixed(11)
  const formatMeters = (value) => `${Number(value || 0).toFixed(1)} m`
  const formatDuration = (seconds) => {
    const totalSeconds = Math.max(Math.round(seconds || 0), 0)
    const minutes = Math.floor(totalSeconds / 60)
    const remainderSeconds = totalSeconds % 60
    return `${minutes}分${remainderSeconds}秒`
  }
  const formatHistoryTime = (value) => {
    if (!value) {
      return '--'
    }

    const date = new Date(value)
    if (Number.isNaN(date.getTime())) {
      return value
    }

    return new Intl.DateTimeFormat('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(date)
  }

  const handleSelectPoint = (mode) => {
    setSelectionMode(mode)
    ElMessage.info(mode === 'center' ? '请在地图上点击目标中心点' : '请在地图上点击目标边缘点')
  }

  const ensureCesiumReady = () => {
    const manager = cesiumManagerInfo.value
    if (manager?.viewer && !manager.viewer.isDestroyed?.()) {
      return manager
    }
    return null
  }

  const handleSelectModelTarget = async () => {
    if (selectionMode.value === 'model') {
      setSelectionMode('model')
      ElMessage.success(selectedModelTargets.value.length ? `已完成多选，共选中 ${selectedModelTargets.value.length} 栋建筑` : '已退出白模多选')
      return
    }

    if (mapProvider.value === 'tencent') {
      ElMessage.warning('请先切换到 Cesium 场景，再选择白模模型')
      return
    }

    const manager = ensureCesiumReady()
    if (!manager) {
      ElMessage.warning('地图场景尚未初始化完成，请稍候再试')
      return
    }

    if (!cityModelEnabled.value) {
      setCityModelEnabled(true)
    }

    try {
      await manager.prepareCityModelForPlanning()
      setSelectionMode('model')
      ElMessage.info('请连续点击白模建筑进行多选；再次点击按钮可结束选择')
    } catch (error) {
      const message = error instanceof Error ? error.message : '白模目标选择不可用'
      ElMessage.error(message)
    }
  }

  const handleClearSelectedModels = () => {
    clearSelectedModelTargets()
    ElMessage.success('已清空所选建筑')
  }

  const handleStartShapeTool = (tool) => {
    if (mapProvider.value === 'tencent') {
      ElMessage.warning('请先切换到 Cesium 场景，再进行图形绘制')
      return
    }

    if (!ensureCesiumReady()) {
      ElMessage.warning('地图场景尚未初始化完成，请稍候再试')
      return
    }

    beginShapeDrawing(tool)
    ElMessage.info(`已开始${getShapeToolLabel(tool)}绘制，请在地图上点击`)
  }

  const handleFinalizeShapePolygon = () => {
    const target = finalizeShapePolygon()
    if (!target) {
      ElMessage.warning('多边形至少需要 3 个顶点')
      return
    }

    ElMessage.success(`已完成${target.name}`)
  }

  const handleUndoShapeVertex = () => {
    undoShapeVertex()
  }

  const handleCancelShapeDrawing = () => {
    cancelShapeDrawing()
    ElMessage.info('已取消图形绘制')
  }

  const handleClearShapeSelection = () => {
    cancelShapeDrawing()
    clearSelectedModelTargets()
    clearRoutePlan()
    ElMessage.success('已清空图形采样区域')
  }

  const clearBuildingSearchResults = () => {
    buildingSearchResults.value = []
    buildingSearchError.value = ''
    buildingSearchLoading.value = false
  }

  const formatSearchResultMeta = (item) => {
    const parts = []
    if (item.cityName) {
      parts.push(item.cityName)
    }
    if (item.address) {
      parts.push(item.address)
    }
    if (item.category) {
      parts.push(item.category)
    }
    if (item.match === 'nearest' && item.distanceM != null) {
      parts.push(`邻近 ${Number(item.distanceM).toFixed(0)} m`)
    } else if (item.match === 'contain') {
      parts.push('轮廓内匹配')
    }
    if (item.buildingId) {
      parts.push(`ID ${item.buildingId}`)
    }
    return parts.join(' · ') || '可定位到白模'
  }

  const runBuildingSearch = async (rawQuery) => {
    const query = `${rawQuery || ''}`.trim()
    const requestId = ++buildingSearchRequestId

    if (!query) {
      clearBuildingSearchResults()
      return
    }

    buildingSearchLoading.value = true
    buildingSearchError.value = ''

    try {
      const result = await searchBuildings(query, {
        limit: 12,
        allCities: true,
      })
      if (requestId !== buildingSearchRequestId) {
        return
      }

      if (result.error) {
        buildingSearchError.value = result.error
        buildingSearchResults.value = []
        return
      }

      buildingSearchResults.value = result.results
    } catch (error) {
      if (requestId !== buildingSearchRequestId) {
        return
      }

      buildingSearchError.value = error instanceof Error ? error.message : '建筑搜索失败'
      buildingSearchResults.value = []
    } finally {
      if (requestId === buildingSearchRequestId) {
        buildingSearchLoading.value = false
      }
    }
  }

  const handleBuildingSearchInput = () => {
    if (buildingSearchTimer) {
      window.clearTimeout(buildingSearchTimer)
    }

    buildingSearchTimer = window.setTimeout(() => {
      runBuildingSearch(buildingSearchQuery.value)
    }, 220)
  }

  const handleBuildingSearchEnter = () => {
    if (buildingSearchTimer) {
      window.clearTimeout(buildingSearchTimer)
      buildingSearchTimer = null
    }

    runBuildingSearch(buildingSearchQuery.value)
  }

  const handleSelectSearchResult = async (item) => {
    if (!item) {
      return
    }

    if (mapProvider.value === 'tencent') {
      ElMessage.warning('请先切换到 Cesium 场景，再搜索并选择白模')
      return
    }

    const manager = ensureCesiumReady()
    if (!manager) {
      ElMessage.warning('地图场景尚未初始化完成，请稍候再试')
      return
    }

    if (!cityModelEnabled.value) {
      setCityModelEnabled(true)
    }

    try {
      const resultCityId = item.cityId || activeCityModelId.value
      if (resultCityId && resultCityId !== activeCityModelId.value) {
        setActiveCityModelId(resultCityId)
        if (manager.setActiveCityModel) {
          await manager.setActiveCityModel(resultCityId, { flyTo: false })
        }
      }

      await manager.prepareCityModelForPlanning()

      let modelTarget = null
      if (item.building && Array.isArray(item.building.footprint) && item.building.footprint.length >= 3) {
        modelTarget = manager.buildModelTargetFromBuilding(item.building, {
          displayName: item.name,
          detectionSource: 'search-index',
        })
      } else if (item.buildingId) {
        modelTarget = await manager.extractModelTargetByBuildingId(item.buildingId, {
          displayName: item.name,
          detectionSource: 'search-index',
          cityId: resultCityId,
        })
      }

      if (!modelTarget) {
        const longitude = Number(item.longitude)
        const latitude = Number(item.latitude)
        if (Number.isFinite(longitude) && Number.isFinite(latitude)) {
          await manager.flyTo({
            longitude,
            latitude,
            height: 680,
          }, { pitch: -45 }, { duration: 1.2 })
        }

        const cityLabel = item.cityName ? `（${item.cityName}）` : ''
        ElMessage.warning(`已定位到「${item.name}」${cityLabel}，但未匹配到白模轮廓，请改用地图点选`)
        return
      }

      if (selectionMode.value !== 'model') {
        clearSelectedModelTargets()
      }

      setSelectedModelTarget(modelTarget)
      await manager.flyTo({
        longitude: modelTarget.centerPoint.longitude,
        latitude: modelTarget.centerPoint.latitude,
        height: Math.max(Number(modelTarget.roofHeight || 0) * 4, 420),
      }, { pitch: -45 }, { duration: 1.2 })

      buildingSearchQuery.value = item.name
      clearBuildingSearchResults()
      const cityLabel = item.cityName ? ` · ${item.cityName}` : ''
      ElMessage.success(`已选中「${item.name}」${cityLabel}`)
    } catch (error) {
      const message = error instanceof Error ? error.message : '搜索选中失败'
      ElMessage.error(message)
    }
  }

  const loadHistoryRecords = async () => {
    historyLoading.value = true

    try {
      const result = await fetchLocationHistory()
      historyRecords.value = result.records.map((item) => ({
        ...item,
        latitude: Number(item.latitude),
        longitude: Number(item.longitude),
      }))
      historyEndpoint.value = result.endpoint
    } catch (error) {
      const message = error instanceof Error ? error.message : '无法读取历史位置记录'
      ElMessage.error(message)
    } finally {
      historyLoading.value = false
    }
  }

  const openHistoryDialog = async (mode) => {
    historyDialogMode.value = mode
    historyDialogVisible.value = true

    if (historyRecords.value.length === 0) {
      await loadHistoryRecords()
    }
  }

  const applyHistoryRecord = (record) => {
    const point = {
      longitude: Number(record.longitude),
      latitude: Number(record.latitude),
      height: Number(record.altitude || 0),
    }

    if (historyDialogMode.value === 'center') {
      setCenterPoint(point)
    } else {
      setEdgePoint(point)
    }

    historyDialogVisible.value = false
    ElMessage.success(historyDialogMode.value === 'center' ? '已填入中心点' : '已填入边缘点')

    if (routePlan.value) {
      void regenerateRoute(true)
    }
  }

  const regenerateRoute = async (silent = false) => {
    if (form.highElevationM < form.lowElevationM) {
      return
    }

    if (isBuildingPlanner.value) {
      if (!selectedModelTarget.value?.footprintPoints?.length) {
        return
      }
    } else if (!centerPoint.value || !edgePoint.value) {
      return
    }

    const currentRequestId = ++planningRequestId
    planningInProgress.value = true

    try {
      const result = await planMissionWithObstacleAnalysis({
        plannerType: planningPlannerType.value,
        form: { ...form },
        centerPoint: centerPoint.value,
        edgePoint: edgePoint.value,
        selectedModelTarget: selectedModelTarget.value,
        selectedModelTargets: selectedModelTargets.value,
      }, createSceneObstacleAnalysisOptions({
        mapProvider: mapProvider.value,
        cityModelEnabled: cityModelEnabled.value,
        cesiumManager: cesiumManagerInfo.value,
      }))

      if (currentRequestId !== planningRequestId) {
        return
      }

      setRoutePlan(result)

      if (!silent) {
        const fleetSuffix = result.multiUav
          ? `（${result.droneCount} 机并行，预计节省约 ${result.fleetSummary?.timeSavingPercent ?? 0}% 航时）`
          : ''
        if (result.obstacleAnalysis?.status === 'risky') {
          ElMessage.warning(`航线已生成${fleetSuffix}，但仍存在白模碰撞风险，请参考参数建议调整`)
        } else if (result.obstacleAnalysis?.status === 'detoured') {
          ElMessage.success(`${plannerMeta.value.successLabel}航线已生成${fleetSuffix}，并已结合白模完成绕障修正${result.planningRuntime === 'wasm-cpp' ? '（基础规划已由 C++/WASM 加速）' : ''}`)
        } else if (result.planningRuntime === 'wasm-cpp') {
          ElMessage.success(`${plannerMeta.value.successLabel}航线已生成${fleetSuffix}，当前基础规划由 C++/WASM 内核计算`)
        } else {
          ElMessage.success(`${plannerMeta.value.successLabel}航线已生成${fleetSuffix}（当前使用 JavaScript 规划实现）`)
        }
      }
    } catch (error) {
      const message = error instanceof Error
        ? error.message
        : (typeof error === 'string' ? error : `航线规划失败：${JSON.stringify(error)}`)
      console.error('[wayline] planning failed', error)
      ElMessage.error(message || '航线规划失败')
    } finally {
      if (currentRequestId === planningRequestId) {
        planningInProgress.value = false
      }
    }
  }

  const applyCoordinateInput = (mode) => {
    if (mode === 'center') {
      if (coordinateForm.centerLongitude == null || coordinateForm.centerLatitude == null) {
        return
      }

      setCenterPoint({
        longitude: coordinateForm.centerLongitude,
        latitude: coordinateForm.centerLatitude,
        height: 0,
      })

      if (routePlan.value) {
        void regenerateRoute(true)
      }
      return
    }

    if (coordinateForm.edgeLongitude == null || coordinateForm.edgeLatitude == null) {
      return
    }

    setEdgePoint({
      longitude: coordinateForm.edgeLongitude,
      latitude: coordinateForm.edgeLatitude,
      height: 0,
    })

    if (routePlan.value) {
      void regenerateRoute(true)
    }
  }

  watch(
    [() => coordinateForm.centerLongitude, () => coordinateForm.centerLatitude],
    () => {
      applyCoordinateInput('center')
    },
  )

  watch(
    [() => coordinateForm.edgeLongitude, () => coordinateForm.edgeLatitude],
    () => {
      applyCoordinateInput('edge')
    },
  )

  watch(
    selectedModelTarget,
    (target) => {
      if (!target) {
        return
      }

      form.lowElevationM = Number(target.lowElevationM || form.lowElevationM)
      form.highElevationM = Number(target.highElevationM || form.highElevationM)

      if (routePlan.value) {
        void regenerateRoute(true)
      }
    },
    { deep: true },
  )

  watch(
    [
      () => form.focalLengthMm,
      () => form.megapixelsWan,
      () => form.gsdMm,
      () => form.lowElevationM,
      () => form.highElevationM,
      () => form.pitchDeg,
      () => form.photosPerLoop,
      () => form.verticalOverlapPercent,
      () => form.horizontalOverlapPercent,
      () => form.autoPhotosPerLoop,
      () => form.igReshootEnabled,
      () => form.igQualitySpeedBalance,
      () => form.droneCount,
    ],
    () => {
      if (routePlan.value) {
        void regenerateRoute(true)
      }
    },
  )

  watch(
    droneCount,
    (value) => {
      form.droneCount = normalizeDroneCount(value)
    },
    { immediate: true },
  )

  watch(
    [mapProvider, cityModelEnabled, cesiumManagerInfo],
    () => {
      if (routePlan.value) {
        void regenerateRoute(true)
      }
    },
  )

  const handleGenerate = () => {
    if (isBuildingPlanner.value && mapProvider.value === 'tencent') {
      ElMessage.warning('建筑白模仅在「SQLite 离线地图」(Cesium) 模式下可用，请先切换地图模式')
      return
    }

    if (isBuildingPlanner.value && !selectedModelTarget.value?.footprintPoints?.length) {
      ElMessage.warning('请先选择至少一栋白模建筑，识别轮廓后再生成航线')
      return
    }

    if (!isBuildingPlanner.value && (!centerPoint.value || !edgePoint.value)) {
      ElMessage.warning('请先完成中心点和边缘点设置')
      return
    }

    if (form.highElevationM < form.lowElevationM) {
      ElMessage.warning('高点高程不能低于低点高程')
      return
    }

    void regenerateRoute(false)
  }

  const handleClear = () => {
    clearRoutePlan()
    ElMessage.success('航线已清空')
  }

  const cssHexToKmlColor = (hex, alpha = 'ff') => {
    const raw = String(hex || '#4dabf7').replace('#', '')
    if (raw.length !== 6) {
      return `${alpha}f74dab`
    }
    const r = raw.slice(0, 2)
    const g = raw.slice(2, 4)
    const b = raw.slice(4, 6)
    return `${alpha}${b}${g}${r}`.toLowerCase()
  }

  const handleExport = async (format) => {
    if (!routePlan.value) {
      ElMessage.warning('请先生成航线')
      return
    }

    closeExportMenu()

    if (format === 'kml') {
      const kmlContent = generateKML(routePlan.value)
      const dataUri = 'data:application/vnd.google-earth.kml+xml;charset=utf-8,' + encodeURIComponent(kmlContent)
      const droneTag = routePlan.value.multiUav ? `-uav${routePlan.value.droneCount}` : ''
      const exportFileDefaultName = `route-plan${droneTag}-${new Date().toISOString().slice(0, 10)}.kml`

      const linkElement = document.createElement('a')
      linkElement.setAttribute('href', dataUri)
      linkElement.setAttribute('download', exportFileDefaultName)
      linkElement.click()

      ElMessage.success(routePlan.value.multiUav ? `已导出 ${routePlan.value.droneCount} 机区分色 KML` : '航线已导出为KML格式')
    } else if (format === 'kmz') {
      await exportAsKMZ(routePlan.value)
    }
  }

  const generateKML = (routePlan) => {
    const missions = Array.isArray(routePlan.missions) && routePlan.missions.length
      ? routePlan.missions
      : [{
          droneId: 'UAV-1',
          color: '#4dabf7',
          baseWaypoints: routePlan.baseWaypoints || routePlan.waypoints || [],
          waypoints: routePlan.waypoints || [],
        }]

    let kml = '<?xml version="1.0" encoding="UTF-8"?>\n'
    kml += '<kml xmlns="http://www.opengis.net/kml/2.2">\n'
    kml += '<Document>\n'
    kml += `<name>${routePlan.multiUav ? `${routePlan.droneCount}机并行采样航线` : '无人机航线规划'}</name>\n`
    kml += `<description>${routePlan.multiUav ? '多无人机扇区均分采样航线' : '单点环绕采样航线'}</description>\n`

    missions.forEach((mission) => {
      const waypoints = (mission.baseWaypoints?.length ? mission.baseWaypoints : mission.waypoints || [])
        .filter((waypoint) => waypoint && !waypoint.closeLoop)
      const color = cssHexToKmlColor(mission.color || getDroneColor(mission.droneIndex))

      kml += '<Folder>\n'
      kml += `<name>${mission.droneId || 'UAV'}</name>\n`

      kml += '<Placemark>\n'
      kml += `<name>${mission.droneId || 'UAV'} 航线</name>\n`
      kml += '<Style>\n'
      kml += '<LineStyle>\n'
      kml += `<color>${color}</color>\n`
      kml += '<width>3</width>\n'
      kml += '</LineStyle>\n'
      kml += '</Style>\n'
      kml += '<LineString>\n'
      kml += '<tessellate>1</tessellate>\n'
      kml += '<coordinates>\n'

      waypoints.forEach((waypoint) => {
        kml += `${waypoint.longitude},${waypoint.latitude},${waypoint.altitude} `
      })

      kml += '</coordinates>\n'
      kml += '</LineString>\n'
      kml += '</Placemark>\n'

      waypoints.forEach((waypoint, index) => {
        kml += '<Placemark>\n'
        kml += `<name>${mission.droneId || 'UAV'}-${index + 1}</name>\n`
        kml += '<Point>\n'
        kml += `<coordinates>${waypoint.longitude},${waypoint.latitude},${waypoint.altitude}</coordinates>\n`
        kml += '</Point>\n'
        kml += '<description>\n'
        kml += `无人机: ${mission.droneId || 'UAV'}<br/>\n`
        kml += `航点编号: ${index + 1}<br/>\n`
        kml += `经度: ${waypoint.longitude}<br/>\n`
        kml += `纬度: ${waypoint.latitude}<br/>\n`
        kml += `高度: ${waypoint.altitude} m<br/>\n`
        kml += `偏航角: ${formatHeadingLabel(waypoint.heading)}（正北为 0°，顺时针 0–360°）<br/>\n`
        kml += `俯仰角: ${waypoint.pitch}°<br/>\n`
        kml += `环索引: ${waypoint.ringIndex}<br/>\n`
        kml += `类型: ${waypoint.kind}\n`
        kml += '</description>\n'
        kml += '</Placemark>\n'
      })

      kml += '</Folder>\n'
    })

    kml += '</Document>\n'
    kml += '</kml>\n'

    return kml
  }

  const downloadBlob = (blob, fileName) => {
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    link.click()
    URL.revokeObjectURL(url)
  }

  const buildSingleKmzBlob = async (planLike) => {
    const templateKml = generateTemplateKML(planLike)
    const waylinesWpml = generateWaylinesWPML(planLike)
    const zip = new JSZip()
    zip.file('template.kml', templateKml)
    zip.file('waylines.wpml', waylinesWpml)
    zip.folder('res')

    return zip.generateAsync({
      type: 'blob',
      mimeType: 'application/vnd.google-earth.kmz',
      compression: 'DEFLATE',
      compressionOptions: {
        level: 6,
      },
    })
  }

  const exportAsKMZ = async (routePlan) => {
    const timestamp = new Date().toISOString().slice(0, 10)
    const missions = Array.isArray(routePlan.missions) && routePlan.missions.length > 1
      ? routePlan.missions
      : null

    if (!missions) {
      const kmzBlob = await buildSingleKmzBlob(routePlan)
      downloadBlob(kmzBlob, `route-plan-${timestamp}.kmz`)
      ElMessage.success('航线已导出为KMZ格式')
      return
    }

    const pack = new JSZip()
    for (const mission of missions) {
      const planLike = {
        ...routePlan,
        waypoints: mission.waypoints,
        baseWaypoints: mission.baseWaypoints,
        rings: mission.rings,
        summary: {
          ...routePlan.summary,
          ...mission.summary,
        },
      }
      const kmzBlob = await buildSingleKmzBlob(planLike)
      pack.file(`${mission.droneId || `UAV-${mission.droneIndex + 1}`}.kmz`, kmzBlob)
    }

    const packBlob = await pack.generateAsync({
      type: 'blob',
      mimeType: 'application/zip',
      compression: 'DEFLATE',
      compressionOptions: { level: 6 },
    })

    downloadBlob(packBlob, `route-plan-uav${missions.length}-${timestamp}.zip`)
    ElMessage.success(`已按机分别导出 ${missions.length} 个 KMZ（打包为 ZIP）`)
  }

  const resolveExportWaypoints = (routePlan) => {
    const candidates = routePlan.baseWaypoints?.length
      ? routePlan.baseWaypoints
      : routePlan.waypoints || []
    return candidates.filter((waypoint) => waypoint && !waypoint.closeLoop)
  }

  const buildWpmlHeadingParamXml = (waypoint, indent = '') => {
    const headingAngle = toWpmlHeadingAngle(waypoint.heading)
    return [
      `${indent}<wpml:waypointHeadingParam>`,
      `${indent}  <wpml:waypointHeadingMode>smoothTransition</wpml:waypointHeadingMode>`,
      `${indent}  <wpml:waypointHeadingAngle>${headingAngle}</wpml:waypointHeadingAngle>`,
      `${indent}  <wpml:waypointHeadingAngleEnable>1</wpml:waypointHeadingAngleEnable>`,
      `${indent}  <wpml:waypointHeadingPathMode>followBadArc</wpml:waypointHeadingPathMode>`,
      `${indent}</wpml:waypointHeadingParam>`,
    ].join('\n')
  }

  const generateTemplateKML = (routePlan) => {
    const waypoints = resolveExportWaypoints(routePlan)
    const timestamp = Date.now()
    
    let kml = '<?xml version="1.0" encoding="UTF-8"?>\n'
    kml += '<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:wpml="http://www.dji.com/wpmz/1.0.2">\n'
    kml += '<Document>\n'
    
    // 创建信息
    kml += '<wpml:author>Vue-Cesium-Airline</wpml:author>\n'
    kml += `<wpml:createTime>${timestamp}</wpml:createTime>\n`
    kml += `<wpml:updateTime>${timestamp}</wpml:updateTime>\n`
    
    // 任务配置
    kml += '<wpml:missionConfig>\n'
    kml += '<wpml:flyToWaylineMode>safely</wpml:flyToWaylineMode>\n'
    kml += '<wpml:finishAction>goHome</wpml:finishAction>\n'
    kml += '<wpml:exitOnRCLost>goContinue</wpml:exitOnRCLost>\n'
    kml += '<wpml:executeRCLostAction>hover</wpml:executeRCLostAction>\n'
    kml += '<wpml:takeOffSecurityHeight>20</wpml:takeOffSecurityHeight>\n'
    kml += '<wpml:globalTransitionalSpeed>8</wpml:globalTransitionalSpeed>\n'
    kml += '<wpml:droneInfo>\n'
    kml += '<wpml:droneEnumValue>67</wpml:droneEnumValue>\n'  // M30
    kml += '<wpml:droneSubEnumValue>0</wpml:droneSubEnumValue>\n'
    kml += '</wpml:droneInfo>\n'
    kml += '<wpml:payloadInfo>\n'
    kml += '<wpml:payloadEnumValue>52</wpml:payloadEnumValue>\n'  // M30相机
    kml += '<wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>\n'
    kml += '</wpml:payloadInfo>\n'
    kml += '</wpml:missionConfig>\n'
    
    // 模板信息
    kml += '<Folder>\n'
    kml += '<wpml:templateType>waypoint</wpml:templateType>\n'
    kml += '<wpml:templateId>0</wpml:templateId>\n'
    kml += '<wpml:waylineCoordinateSysParam>\n'
    kml += '<wpml:coordinateMode>WGS84</wpml:coordinateMode>\n'
    kml += '<wpml:heightMode>EGM96</wpml:heightMode>\n'
    kml += `<wpml:globalShootHeight>${routePlan.planningInputs.gsdMm * 1000}</wpml:globalShootHeight>\n`
    kml += '<wpml:positioningType>GPS</wpml:positioningType>\n'
    kml += '<wpml:surfaceFollowModeEnable>0</wpml:surfaceFollowModeEnable>\n'
    kml += '</wpml:waylineCoordinateSysParam>\n'
    kml += `<wpml:autoFlightSpeed>${routePlan.summary.cruiseSpeedMetersPerSecond}</wpml:autoFlightSpeed>\n`
    kml += '<wpml:gimbalPitchMode>usePointSetting</wpml:gimbalPitchMode>\n'
    // Global heading is fallback only; each waypoint sets its own 0–360° compass yaw.
    kml += '<wpml:globalWaypointHeadingParam>\n'
    kml += '<wpml:waypointHeadingMode>smoothTransition</wpml:waypointHeadingMode>\n'
    kml += '<wpml:waypointHeadingAngle>0</wpml:waypointHeadingAngle>\n'
    kml += '<wpml:waypointHeadingAngleEnable>1</wpml:waypointHeadingAngleEnable>\n'
    kml += '<wpml:waypointHeadingPathMode>followBadArc</wpml:waypointHeadingPathMode>\n'
    kml += '</wpml:globalWaypointHeadingParam>\n'
    kml += '<wpml:globalWaypointTurnMode>toPointAndStopWithDiscontinuityCurvature</wpml:globalWaypointTurnMode>\n'
    kml += '<wpml:globalUseStraightLine>0</wpml:globalUseStraightLine>\n'
    
    // 航点信息（每点写入朝向角，导出到航线）
    waypoints.forEach((waypoint, index) => {
      kml += '<Placemark>\n'
      kml += '<Point>\n'
      kml += `<coordinates>${waypoint.longitude},${waypoint.latitude}</coordinates>\n`
      kml += '</Point>\n'
      kml += `<wpml:index>${index}</wpml:index>\n`
      kml += `<wpml:ellipsoidHeight>${waypoint.altitude}</wpml:ellipsoidHeight>\n`
      kml += `<wpml:height>${waypoint.altitude}</wpml:height>\n`
      kml += '<wpml:useGlobalHeight>1</wpml:useGlobalHeight>\n'
      kml += '<wpml:useGlobalSpeed>1</wpml:useGlobalSpeed>\n'
      kml += '<wpml:useGlobalHeadingParam>0</wpml:useGlobalHeadingParam>\n'
      kml += `${buildWpmlHeadingParamXml(waypoint)}\n`
      kml += '<wpml:useGlobalTurnParam>1</wpml:useGlobalTurnParam>\n'
      kml += `<wpml:gimbalPitchAngle>${waypoint.pitch}</wpml:gimbalPitchAngle>\n`
      kml += '</Placemark>\n'
    })
    
    kml += '</Folder>\n'
    kml += '</Document>\n'
    kml += '</kml>\n'
    
    return kml
  }

  const generateWaylinesWPML = (routePlan) => {
    const waypoints = resolveExportWaypoints(routePlan)
    
    let wpml = '<?xml version="1.0" encoding="UTF-8"?>\n'
    wpml += '<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:wpml="http://www.dji.com/wpmz/1.0.2">\n'
    wpml += '<Document>\n'
    
    // 任务配置
    wpml += '<wpml:missionConfig>\n'
    wpml += '<wpml:flyToWaylineMode>safely</wpml:flyToWaylineMode>\n'
    wpml += '<wpml:finishAction>goHome</wpml:finishAction>\n'
    wpml += '<wpml:exitOnRCLost>goContinue</wpml:exitOnRCLost>\n'
    wpml += '<wpml:executeRCLostAction>hover</wpml:executeRCLostAction>\n'
    wpml += '<wpml:takeOffSecurityHeight>20</wpml:takeOffSecurityHeight>\n'
    wpml += `<wpml:globalTransitionalSpeed>${routePlan.summary.cruiseSpeedMetersPerSecond}</wpml:globalTransitionalSpeed>\n`
    wpml += '<wpml:droneInfo>\n'
    wpml += '<wpml:droneEnumValue>67</wpml:droneEnumValue>\n'
    wpml += '<wpml:droneSubEnumValue>0</wpml:droneSubEnumValue>\n'
    wpml += '</wpml:droneInfo>\n'
    wpml += '<wpml:payloadInfo>\n'
    wpml += '<wpml:payloadEnumValue>52</wpml:payloadEnumValue>\n'
    wpml += '<wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>\n'
    wpml += '</wpml:payloadInfo>\n'
    wpml += '</wpml:missionConfig>\n'
    
    // 航线信息
    wpml += '<Folder>\n'
    wpml += '<wpml:templateId>0</wpml:templateId>\n'
    wpml += '<wpml:waylineId>0</wpml:waylineId>\n'
    wpml += '<wpml:executeHeightMode>WGS84</wpml:executeHeightMode>\n'
    wpml += `<wpml:autoFlightSpeed>${routePlan.summary.cruiseSpeedMetersPerSecond}</wpml:autoFlightSpeed>\n`
    
    // 航点信息（每点独立朝向，供机上执行）
    waypoints.forEach((waypoint, index) => {
      wpml += '<Placemark>\n'
      wpml += '<Point>\n'
      wpml += `<coordinates>${waypoint.longitude},${waypoint.latitude}</coordinates>\n`
      wpml += '</Point>\n'
      wpml += `<wpml:index>${index}</wpml:index>\n`
      wpml += `<wpml:executeHeight>${waypoint.altitude}</wpml:executeHeight>\n`
      wpml += `<wpml:waypointSpeed>${routePlan.summary.cruiseSpeedMetersPerSecond}</wpml:waypointSpeed>\n`
      wpml += `${buildWpmlHeadingParamXml(waypoint)}\n`
      wpml += '<wpml:waypointTurnParam>\n'
      wpml += '<wpml:waypointTurnMode>toPointAndStopWithDiscontinuityCurvature</wpml:waypointTurnMode>\n'
      wpml += '<wpml:waypointTurnDampingDist>0</wpml:waypointTurnDampingDist>\n'
      wpml += '</wpml:waypointTurnParam>\n'
      wpml += '</Placemark>\n'
    })
    
    wpml += '</Folder>\n'
    wpml += '</Document>\n'
    wpml += '</kml>\n'
    
    return wpml
  }
</script>

<style scoped lang="scss">
  .planner-panel {
    height: 100%;
    max-height: 100%;
    overflow-y: auto;
    overflow-x: hidden;
    padding: 14px 14px 18px;
    padding-bottom: 20px;
    border-radius: 0;
    background: transparent;
    overscroll-behavior: contain;
    scrollbar-gutter: stable;
    -webkit-overflow-scrolling: touch;
    border: none;
    box-shadow: none;
  }
  .planner-panel::-webkit-scrollbar { width: 8px; }
  .planner-panel::-webkit-scrollbar-thumb { background: var(--border-color, #2d3139); border-radius: var(--radius-md, 8px); }
  .hero-card {
    position: relative; display: flex; align-items: flex-start; justify-content: space-between; gap: 14px;
    padding: 14px; border-radius: var(--radius-md, 8px); background: var(--bg-subcard, #272b33);
    color: var(--text-primary, #f3f4f6); overflow: hidden; border: 1px solid var(--border-color, #2d3139); box-shadow: none;
  }
  .hero-card.is-compact { padding: 12px 14px; }
  .hero-card::before, .hero-card::after { display: none; }
  .eyebrow { margin: 0; font-size: 11px; letter-spacing: 0.1em; text-transform: uppercase; color: var(--accent-primary, #3b78e7); font-weight: 650; }
  .hero-card h1 { margin: 8px 0 0; font-size: 22px; line-height: 1.2; letter-spacing: -0.02em; font-weight: 700; color: var(--text-primary, #f3f4f6); }
  .hero-copy { margin: 6px 0 0; max-width: none; font-size: 12px; line-height: 1.55; color: var(--text-secondary, #9ca3af); }
  .hero-badge { padding: 6px 10px; border-radius: var(--radius-md, 8px); background: var(--accent-soft, rgba(59, 120, 231, 0.16)); color: var(--accent-primary, #3b78e7); font-weight: 600; font-size: 12px; white-space: nowrap; border: 1px solid transparent; }
  .panel-section { margin-top: 12px; padding: 14px; border-radius: var(--radius-md, 8px); background: var(--bg-subcard, #272b33); border: 1px solid var(--border-color, #2d3139); box-shadow: none; }
  .panel-section:hover { transform: none; box-shadow: none; border-color: var(--border-color, #2d3139); }
  .section-header { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
  .section-header-copy { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; min-width: 0; }
  .section-toggle { width: 100%; padding: 0; background: transparent; border: none; text-align: left; cursor: pointer; }
  .section-toggle-indicator { flex: none; display: inline-flex; align-items: center; justify-content: center; min-width: 52px; height: 28px; padding: 0 10px; border-radius: var(--radius-md, 8px); background: var(--bg-card, #1e2127); color: var(--text-secondary, #9ca3af); font-size: 12px; font-weight: 600; line-height: 1; border: 1px solid var(--border-color, #2d3139); }
  .section-toggle-indicator.expanded { background: var(--accent-soft, rgba(59, 120, 231, 0.16)); color: var(--accent-primary, #3b78e7); border-color: transparent; }
  .section-body { margin-top: 12px; }
  .panel-section.collapsed { padding-bottom: 14px; }
  .panel-section.collapsed .section-header { margin-bottom: 0; }
  .section-header h2 { margin: 0; font-size: 15px; font-weight: 650; color: var(--text-primary, #f3f4f6); }
  .section-header span, .hint { color: var(--text-secondary, #9ca3af); font-size: 12px; line-height: 1.55; }
  .form-grid { display: grid; gap: 12px; }
  .two-columns { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .field { display: flex; flex-direction: column; gap: 8px; }
  .field span { font-size: 12px; color: var(--text-secondary, #9ca3af); font-weight: 500; }
  .full-width { grid-column: 1 / -1; }
  .point-actions, .panel-actions { display: flex; gap: 10px; flex-wrap: wrap; }
  .panel-actions-primary, .panel-actions-secondary { width: 100%; display: flex; gap: 10px; }
  .toggle-row { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 12px; border-radius: var(--radius-md, 8px); background: var(--bg-card, #1e2127); border: 1px solid var(--border-color, #2d3139); }
  .city-model-picker {
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 10px;
    align-items: center;
    margin-top: 10px;
    padding: 10px 12px;
    border-radius: var(--radius-md, 8px);
    background: var(--bg-subcard, #272b33);
    border: 1px solid var(--border-color, #2d3139);
  }
  .city-model-picker-label {
    color: var(--text-secondary, #9ca3af);
    font-size: 12px;
    font-weight: 600;
    white-space: nowrap;
  }
  .toggle-copy { display: flex; flex-direction: column; gap: 4px; }
  .toggle-copy strong { font-size: 14px; color: var(--text-primary, #f3f4f6); font-weight: 600; }
  .toggle-copy span { font-size: 12px; color: var(--text-secondary, #9ca3af); line-height: 1.5; }
  .point-actions { margin-bottom: 12px; }
  .search-sampler { display: flex; flex-direction: column; gap: 14px; margin-bottom: 4px; }
  .search-sampler-hero {
    padding: 14px 14px 12px;
    border-radius: var(--radius-md, 8px);
    background:
      linear-gradient(135deg, rgba(59, 120, 231, 0.18), transparent 58%),
      var(--bg-subcard, #272b33);
    border: 1px solid var(--border-color, #2d3139);
  }
  .search-sampler-kicker {
    margin: 0 0 6px;
    font-size: 11px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--accent-primary, #3b78e7);
    font-weight: 600;
  }
  .search-sampler-hero h3 {
    margin: 0;
    font-size: 18px;
    line-height: 1.3;
    color: var(--text-primary, #f3f4f6);
    font-weight: 650;
  }
  .search-sampler-hero p {
    margin: 8px 0 0;
    font-size: 12px;
    line-height: 1.55;
    color: var(--text-secondary, #9ca3af);
  }
  .search-sampler-field { display: flex; flex-direction: column; gap: 8px; }
  .search-sampler-field-label { font-size: 12px; color: var(--text-secondary, #9ca3af); font-weight: 500; }
  .search-sampler-input-row {
    position: relative;
    display: flex;
    align-items: center;
  }
  .search-sampler-glyph {
    position: absolute;
    left: 12px;
    z-index: 2;
    color: var(--accent-primary, #3b78e7);
    font-size: 15px;
    pointer-events: none;
  }
  .search-sampler-input-row :deep(.el-input__wrapper) {
    padding-left: 34px;
    min-height: 42px;
  }
  .search-sampler-status .hint { margin: 0; }
  .search-result-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-height: 260px;
    overflow: auto;
    padding: 8px;
    border-radius: var(--radius-md, 8px);
    background: var(--bg-card, #1e2127);
    border: 1px solid var(--border-color, #2d3139);
  }
  .search-result-list-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 2px 4px 6px;
    color: var(--text-secondary, #9ca3af);
    font-size: 12px;
  }
  .search-result-list-head strong {
    color: var(--text-primary, #f3f4f6);
    font-size: 13px;
  }
  .search-result-item {
    display: grid;
    grid-template-columns: 28px minmax(0, 1fr) auto;
    gap: 10px;
    align-items: center;
    width: 100%;
    padding: 10px 10px;
    border: 1px solid transparent;
    border-radius: 6px;
    background: var(--bg-subcard, #272b33);
    color: var(--text-primary, #f3f4f6);
    text-align: left;
    cursor: pointer;
    font: inherit;
    transition: background 0.15s ease, border-color 0.15s ease;
  }
  .search-result-item:hover {
    background: var(--bg-hover, #2a2e36);
    border-color: var(--accent-primary, #3b78e7);
  }
  .search-result-index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border-radius: 6px;
    background: var(--bg-card, #1e2127);
    color: var(--accent-primary, #3b78e7);
    font-size: 12px;
    font-weight: 650;
  }
  .search-result-copy {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
  }
  .search-result-copy strong {
    font-size: 13px;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .search-result-city {
    display: inline-block;
    margin-right: 6px;
    padding: 1px 6px;
    border-radius: 4px;
    font-size: 11px;
    font-weight: 650;
    vertical-align: 1px;
    color: #93c5fd;
    background: rgba(59, 120, 231, 0.18);
  }
  .search-result-copy span {
    font-size: 12px;
    color: var(--text-secondary, #9ca3af);
    line-height: 1.4;
  }
  .search-result-badge {
    flex: 0 0 auto;
    padding: 4px 8px;
    border-radius: 6px;
    font-size: 11px;
    font-weight: 600;
    white-space: nowrap;
  }
  .search-result-badge.is-exact {
    color: #86efac;
    background: rgba(34, 197, 94, 0.14);
  }
  .search-result-badge.is-near {
    color: #93c5fd;
    background: rgba(59, 120, 231, 0.16);
  }
  .search-sampler-actions {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
  }
  .model-selection-card.is-search {
    border-color: rgba(59, 120, 231, 0.45);
    box-shadow: inset 0 0 0 1px rgba(59, 120, 231, 0.12);
  }
  .model-selection-card.is-shape {
    border-color: rgba(59, 120, 231, 0.5);
    box-shadow: inset 0 0 0 1px rgba(59, 120, 231, 0.14);
  }
  .shape-sampler { display: flex; flex-direction: column; gap: 14px; margin-bottom: 4px; }
  .shape-sampler-hero {
    padding: 14px 14px 12px;
    border-radius: var(--radius-md, 8px);
    background:
      linear-gradient(145deg, rgba(59, 120, 231, 0.2), transparent 55%),
      var(--bg-subcard, #272b33);
    border: 1px solid var(--border-color, #2d3139);
  }
  .shape-sampler-kicker {
    margin: 0 0 6px;
    font-size: 11px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--accent-primary, #3b78e7);
    font-weight: 600;
  }
  .shape-sampler-hero h3 {
    margin: 0;
    font-size: 18px;
    line-height: 1.3;
    color: var(--text-primary, #f3f4f6);
    font-weight: 650;
  }
  .shape-sampler-hero p {
    margin: 8px 0 0;
    font-size: 12px;
    line-height: 1.55;
    color: var(--text-secondary, #9ca3af);
  }
  .shape-tool-grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: 8px;
  }
  .shape-tool-card {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    padding: 12px;
    border: 1px solid var(--border-color, #2d3139);
    border-radius: var(--radius-md, 8px);
    background: var(--bg-card, #1e2127);
    color: var(--text-primary, #f3f4f6);
    text-align: left;
    cursor: pointer;
    font: inherit;
    transition: background 0.15s ease, border-color 0.15s ease;
  }
  .shape-tool-card:hover,
  .shape-tool-card.active {
    background: var(--bg-hover, #2a2e36);
    border-color: var(--accent-primary, #3b78e7);
  }
  .shape-tool-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: 8px;
    background: var(--bg-subcard, #272b33);
    color: var(--accent-primary, #3b78e7);
    font-size: 18px;
    flex: 0 0 auto;
  }
  .shape-tool-copy {
    display: flex;
    flex-direction: column;
    gap: 3px;
    min-width: 0;
  }
  .shape-tool-copy strong {
    font-size: 14px;
    font-weight: 650;
  }
  .shape-tool-copy span {
    font-size: 12px;
    color: var(--text-secondary, #9ca3af);
    line-height: 1.4;
  }
  .shape-status-card {
    display: grid;
    gap: 8px;
    padding: 12px;
    border-radius: var(--radius-md, 8px);
    background: var(--bg-card, #1e2127);
    border: 1px solid var(--border-color, #2d3139);
  }
  .shape-status-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    font-size: 12px;
    color: var(--text-secondary, #9ca3af);
  }
  .shape-status-row strong {
    color: var(--text-primary, #f3f4f6);
    font-size: 13px;
    font-weight: 600;
  }
  .shape-sampler-actions {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
  }
  .shape-sampler-actions .action-button:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
  .building-search-error { color: #f87171; }
  .action-button, .primary-button, .secondary-button { border: 1px solid transparent; border-radius: var(--radius-md, 8px); padding: 10px 14px; font: inherit; font-size: 13px; font-weight: 600; cursor: pointer; box-shadow: none; transition: background 0.15s ease, border-color 0.15s ease, opacity 0.15s ease; }
  .action-button:hover, .primary-button:hover, .secondary-button:hover { transform: none; }
  .action-button, .action-button-alt, .action-button-model { background: var(--bg-card, #1e2127); color: var(--text-primary, #f3f4f6); border-color: var(--border-color, #2d3139); }
  .action-button:hover, .action-button-alt:hover, .action-button-model:hover { background: var(--bg-hover, #2a2e36); }
  .action-button.active { background: var(--accent-primary, #3b78e7); color: #ffffff; border-color: var(--accent-primary, #3b78e7); }
  .primary-button { flex: 1; background: var(--accent-primary, #3b78e7); color: #ffffff; border-color: var(--accent-primary, #3b78e7); }
  .primary-button:hover { background: var(--accent-primary-hover, #4b86ef); }
  .secondary-button { background: var(--bg-card, #1e2127); color: var(--text-primary, #f3f4f6); border-color: var(--border-color, #2d3139); }
  .secondary-button:hover { background: var(--bg-hover, #2a2e36); }
  .primary-action-button { display: inline-flex; align-items: center; justify-content: center; width: 100%; min-height: 48px; font-size: 15px; }
  .secondary-action-button { display: inline-flex; align-items: center; flex: 1; min-height: 44px; justify-content: center; text-align: center; }
  .dialog-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
  .dialog-status { display: flex; flex-direction: column; gap: 4px; color: var(--text-secondary, #9ca3af); font-size: 13px; }
  .dialog-refresh { flex: none; padding: 8px 12px; }
  .table-select-button { width: 100%; padding: 8px 0; }
  :deep(.history-dialog) { max-width: calc(100vw - 64px); width: 1180px; }
  :deep(.history-dialog .el-dialog__body) { padding-top: 12px; }
  :deep(.history-dialog .el-table .cell) { line-height: 1.5; }
  .primary-button:disabled { cursor: not-allowed; opacity: 0.45; }
  .point-status, .detail-grid, .stats-grid { display: grid; gap: 10px; }
  .point-status, .detail-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); margin-top: 12px; }
  .status-item, .detail-item, .stat-card { position: relative; padding: 12px; border-radius: var(--radius-md, 8px); background: var(--bg-card, #1e2127); border: 1px solid var(--border-color, #2d3139); overflow: hidden; }
  .stat-card::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 2px; background: var(--accent-primary, #3b78e7); }
  .status-item span, .detail-item span, .stat-card span { display: block; font-size: 12px; color: var(--text-secondary, #9ca3af); }
  .status-item strong, .detail-item strong, .stat-card strong { display: block; margin-top: 6px; font-size: 15px; color: var(--text-primary, #f3f4f6); font-weight: 600; }
  .model-selection-card { margin-top: 12px; padding: 12px; border-radius: var(--radius-md, 8px); background: var(--bg-card, #1e2127); border: 1px solid var(--border-color, #2d3139); }
  .model-selection-title { font-size: 13px; font-weight: 600; color: var(--text-primary, #f3f4f6); }
  .model-selection-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 10px; }
  .model-selection-grid span { display: block; font-size: 12px; color: var(--text-secondary, #9ca3af); }
  .model-selection-grid strong { display: block; margin-top: 6px; font-size: 14px; color: var(--text-primary, #f3f4f6); font-weight: 600; }
  .model-selection-list { margin-top: 12px; display: flex; flex-direction: column; gap: 8px; }
  .model-selection-list-label { font-size: 12px; color: var(--text-secondary, #9ca3af); }
  .model-selection-tags { display: flex; flex-wrap: wrap; gap: 6px; }
  .model-selection-tag { display: inline-flex; align-items: center; padding: 5px 9px; border-radius: var(--radius-md, 8px); background: var(--bg-subcard, #272b33); color: var(--text-primary, #f3f4f6); font-size: 12px; font-weight: 500; border: 1px solid var(--border-color, #2d3139); }
  .smart-recommend-card { margin-bottom: 12px; padding: 14px; border-radius: var(--radius-md, 8px); background: var(--bg-card, #1e2127); border: 1px solid var(--border-color, #2d3139); }
  .smart-recommend-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
  .smart-recommend-actions { display: inline-flex; align-items: center; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
  .smart-recommend-copy { display: flex; flex-direction: column; gap: 4px; }
  .smart-recommend-copy strong { font-size: 14px; color: var(--text-primary, #f3f4f6); font-weight: 600; }
  .smart-recommend-copy span { font-size: 12px; color: var(--text-secondary, #9ca3af); line-height: 1.55; }
  .smart-recommend-apply { flex: none; }
  .smart-recommend-toggle { flex: none; padding-inline: 12px; }
  .fleet-legend {
    margin-top: 14px;
    padding: 12px;
    border-radius: var(--radius-md, 8px);
    background: var(--bg-subcard, #272b33);
    border: 1px solid var(--border-color, #2d3139);
  }
  .fleet-legend-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary, #f3f4f6);
    margin-bottom: 10px;
  }
  .fleet-legend-list {
    display: grid;
    gap: 8px;
  }
  .fleet-legend-item {
    display: flex;
    align-items: flex-start;
    gap: 10px;
  }
  .fleet-legend-swatch {
    width: 12px;
    height: 12px;
    border-radius: 999px;
    margin-top: 4px;
    flex: 0 0 auto;
  }
  .fleet-legend-copy strong {
    display: block;
    font-size: 13px;
    color: var(--text-primary, #f3f4f6);
  }
  .fleet-legend-copy span {
    display: block;
    margin-top: 2px;
    font-size: 12px;
    color: var(--text-secondary, #9ca3af);
  }
    .smart-recommend-summary, .smart-preset-grid { display: grid; gap: 10px; margin-top: 12px; }
  .smart-recommend-summary { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .smart-summary-item, .smart-preset-card { padding: 12px; border-radius: var(--radius-md, 8px); background: var(--bg-subcard, #272b33); border: 1px solid var(--border-color, #2d3139); }
  .smart-summary-item span, .smart-preset-label { display: block; font-size: 12px; color: var(--text-secondary, #9ca3af); }
  .smart-summary-item strong { display: block; margin-top: 6px; font-size: 14px; color: var(--text-primary, #f3f4f6); font-weight: 600; }
  .smart-preset-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .smart-preset-card { display: flex; flex-direction: column; min-height: 100%; }
  .smart-preset-grid.compact { grid-template-columns: 1fr; }
  .smart-preset-card.featured { border-color: var(--accent-primary, #3b78e7); }
  .smart-preset-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
  .smart-preset-head strong { display: block; margin-top: 6px; font-size: 14px; color: var(--text-primary, #f3f4f6); font-weight: 600; }
  .smart-preset-description { margin: 10px 0 0; font-size: 12px; line-height: 1.55; color: var(--text-secondary, #9ca3af); }
  .smart-preset-values { display: grid; gap: 4px; margin-top: 10px; }
  .smart-preset-values span { font-size: 12px; color: var(--text-soft, #d1d5db); }
  .smart-preset-apply { margin-top: auto; align-self: stretch; justify-content: center; text-align: center; }
  .smart-preset-grid.compact .smart-preset-card { padding: 12px; }
  .smart-preset-grid.compact .smart-preset-head strong { margin-top: 4px; }
  .smart-preset-grid.compact .smart-preset-values { grid-template-columns: repeat(2, minmax(0, 1fr)); margin-top: 8px; }
  .smart-recommend-hint { margin: 10px 0 0; }
  .stats-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); margin-bottom: 10px; }
  .stats-section { margin-bottom: 2px; }
  .obstacle-analysis { margin-top: 14px; }
  .analysis-message { margin: 0 0 10px; padding: 10px 12px; border-radius: var(--radius-md, 8px); background: var(--bg-card, #1e2127); color: var(--text-soft, #d1d5db); font-size: 12px; line-height: 1.55; border: 1px solid var(--border-color, #2d3139); }
  .suggestion-panel { padding: 12px; border-radius: var(--radius-md, 8px); background: var(--bg-card, #1e2127); border: 1px solid var(--border-color, #2d3139); }
  .suggestion-title { display: block; font-size: 13px; font-weight: 600; color: var(--text-primary, #f3f4f6); }
  .suggestion-list { margin: 8px 0 0; padding-left: 18px; color: var(--text-secondary, #9ca3af); font-size: 12px; line-height: 1.7; }
  .export-dropdown { position: relative; flex: 1; }
  .export-dropdown.disabled { opacity: 0.55; }
  .export-trigger { position: relative; width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 10px; padding-inline: 14px; }
  .export-trigger-meta { display: inline-flex; align-items: center; justify-content: center; width: 26px; height: 26px; white-space: nowrap; border-radius: var(--radius-md, 8px); background: var(--bg-subcard, #272b33); border: 1px solid var(--border-color, #2d3139); }
  .export-trigger-chevron { width: 7px; height: 7px; border-right: 2px solid var(--text-secondary, #9ca3af); border-bottom: 2px solid var(--text-secondary, #9ca3af); transform: rotate(45deg) translateY(-1px); }
  .export-trigger-meta.open { transform: rotate(180deg); background: var(--bg-hover, #2a2e36); }
  .export-menu { position: absolute; left: 0; right: 0; top: calc(100% + 8px); padding: 8px; display: grid; gap: 6px; border-radius: var(--radius-md, 8px); background: var(--bg-card, #1e2127); border: 1px solid var(--border-color, #2d3139); box-shadow: var(--shadow-dark, 0 4px 16px rgba(0, 0, 0, 0.35)); }
  .export-option { display: flex; align-items: center; justify-content: center; width: 100%; padding: 12px; border: 1px solid var(--border-color, #2d3139); border-radius: var(--radius-md, 8px); background: var(--bg-subcard, #272b33); color: var(--text-primary, #f3f4f6); text-align: center; cursor: pointer; transition: background 0.15s ease; }
  .export-option strong { font-size: 15px; letter-spacing: 0.03em; color: var(--text-primary, #f3f4f6); font-weight: 600; }
  .export-option:hover { background: var(--bg-hover, #2a2e36); }
  .export-menu-fade-enter-active, .export-menu-fade-leave-active { transition: opacity 0.15s ease, transform 0.15s ease; }
  .export-menu-fade-enter-from, .export-menu-fade-leave-to { opacity: 0; transform: translateY(-4px); }
  .panel-actions { position: sticky; bottom: -8px; z-index: 5; margin-top: 14px; padding: 10px; border-radius: var(--radius-md, 8px); background: var(--bg-card, #1e2127); border: 1px solid var(--border-color, #2d3139); box-shadow: var(--shadow-dark, 0 4px 16px rgba(0, 0, 0, 0.35)); }
  :deep(.el-input-number), :deep(.el-input), :deep(.el-textarea__inner) { width: 100%; }
  :deep(.el-input-number .el-input__wrapper), :deep(.el-input__wrapper) { border-radius: var(--radius-md, 8px); background: var(--bg-card, #1e2127) !important; box-shadow: 0 0 0 1px var(--border-color, #2d3139) inset !important; }
  :deep(.el-input-number.is-controls-right .el-input-number__decrease), :deep(.el-input-number.is-controls-right .el-input-number__increase) { width: 28px; color: var(--text-secondary, #9ca3af); }
  :deep(.el-input-number .el-input__inner), :deep(.el-input__inner) { color: var(--text-primary, #f3f4f6); font-weight: 500; }
  :deep(.el-input-number .el-input__wrapper:hover), :deep(.el-input__wrapper:hover), :deep(.el-input-number .el-input__wrapper.is-focus), :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px var(--accent-primary, #3b78e7) inset !important; }
  :deep(.el-slider__runway) { height: 6px; border-radius: 8px; background: var(--bg-card, #1e2127); }
  :deep(.el-slider__bar) { height: 6px; border-radius: 8px; background: var(--accent-primary, #3b78e7); }
  :deep(.el-slider__button) { width: 14px; height: 14px; border: 2px solid var(--bg-card, #1e2127); background: var(--accent-primary, #3b78e7); box-shadow: none; }
  :deep(.el-slider__input) { width: 108px; }
  :deep(.el-switch__core) { border-color: var(--border-color, #2d3139) !important; background: var(--bg-card, #1e2127) !important; }
  @media (max-width: 900px) {
    .planner-panel { max-height: 100%; }
    .two-columns, .point-status, .detail-grid, .stats-grid, .model-selection-grid, .smart-recommend-summary, .smart-preset-grid { grid-template-columns: 1fr; }
    .hero-card { flex-direction: column; }
    .panel-actions { bottom: -6px; }
    .smart-recommend-header { flex-direction: column; align-items: stretch; }
    .dialog-toolbar { flex-direction: column; align-items: stretch; }
    :deep(.history-dialog) { width: calc(100vw - 24px); max-width: calc(100vw - 24px); margin: 0; }
    .panel-actions-secondary { flex-direction: column; }
    .export-dropdown { width: 100%; }
  }
</style>
