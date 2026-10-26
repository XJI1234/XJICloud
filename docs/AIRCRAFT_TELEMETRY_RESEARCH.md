# MSDK 飞机位置与航向调研（2026-10-26）

## 已证实的接口

项目 Relay 使用 DJI MSDK 5.17.0。该版本已缓存的 provided JAR 中，`DJIFlightControllerKey`（`FlightControllerKey` 的父类）具有下列键；真实 Android 构建还会检查调用能否编译。

| 键 | 官方描述与本次使用 |
|---|---|
| `KeyAircraftLocation` | 二维经纬度；保留现有来源，不更换坐标系统 |
| `KeyCompassHeading` | 单位度，北 0、东 90，原值范围 [-180,180]；归一化为 [0,360)，表示机头朝向 |
| `KeyCompassHasError` | 明确为 true 时不显示方向箭头 |
| `KeyGPSIsValid` | 明确为 false 时不将当前位置作为有效位置 |
| `KeyAircraftLocation3D` | 经纬度和高度；公开描述未明确给出高度的海拔/椭球/相对起飞点基准 |
| `KeyTakeoffLocationAltitude` | 起飞高度，单位米；公开描述也未明确垂直基准 |
| `KeyAltitude` | 项目已有契约将其定义为相对起飞点高度；保持原语义，不当海拔使用 |

参考：[DJI FlightControllerKey](https://developer.dji.com/api-reference-v5/android-api/Components/IKeyManager/Key_FlightController_FlightControllerKey.html)、[LocationCoordinate3D](https://developer.dji.com/api-reference-v5/android-api/Components/IKeyManager/Value_Common_Struct_LocationCoordinate3D.html)。MSDK 本地 UXSDK 的 `MapWidgetModel.java` 同样订阅三维位置和指南针航向。

## 本次决策

1. 新增可选 `headingDegrees`、`gpsIsValid`、`positionSampleId`、`positionAgeMillis`、`locationAltitudeMeters`、`takeoffAltitudeMeters`。新键不可用不阻断原遥测。
2. `positionSampleId` 只在位置键采样时更新，包含观察来源唯一标识和采样序号。同一坐标的新样本可保持新鲜，电量/直播/航向变化不能刷新位置样本。
3. `positionAgeMillis` 在手机组装快照时由单调时钟计算；网页用网关接收时间扣除该年龄作锚点，同一采样 ID 的重复上报不能向后移动锚点。不比较手机与电脑墙钟。
4. 两个新增高度原值保留为诊断/后续核验，不计算成椭球高度。当前缺少可靠垂直基准时使用明确标注的平面位置，详情继续显示相对起飞点高度；不能声称悬空高度已准确实现。
5. 所有数据只供显示，不改变飞控门禁、航线参数或任务选择。Mini 4 Pro + RC-N2 的键可用性、更新频率与数值准确性仍待真机验证；本次不重新宣称其航线能力。

## 已归档研究证据

官方网页读取输出、URL、HTTP 状态与接口摘录分别归档于 `D:/XJI-Test-Records/2026-10-26/`，会话后缀 `7dc5cdf9-0757-4c99-acc2-84b9e7ed496a`、`574a53ae-694d-42a4-a86f-813ae671d46f`、`e40d0fa1-3871-4f81-95c6-c033a2dcc48a`。这是接口文档研究，不能作为机型实测证据。
