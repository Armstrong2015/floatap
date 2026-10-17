<div align="center">

# 浮序 Floatap

在自己的安卓手机上，悬浮选定位置，按间隔、定时或顺序把点击做完。

[![平台](https://img.shields.io/badge/平台-Android_8.0+-3DDC84?style=flat-square&logo=android&logoColor=white)](docs/03-产品设计开发文档.md)
[![阶段](https://img.shields.io/badge/阶段-M0_文档-1f6feb?style=flat-square)](docs/01-应用蓝皮书.md)
[![桌面端](https://img.shields.io/badge/桌面端-Windows_后置-8b949e?style=flat-square&logo=windows&logoColor=white)](docs/01-应用蓝皮书.md#11-路线图)
[![网络](https://img.shields.io/badge/网络-首期不申请-6e7781?style=flat-square)](docs/02-产品需求分析.md)

本仓库目前是产品与设计文档，还没有应用代码。

</div>

## 它在做什么

```mermaid
flowchart LR
  A["用手指标出坐标"] --> B["设定间隔与停止条件"]
  B --> C["切到目标应用"]
  C --> D["悬浮球按脚本点击"]
  D --> E["到时、到次数或手动停止"]
```

点的位置由人指定。浮序不登录账号、不填写支付信息、不替用户下单。

## 文档

| | 文档 | 看什么 |
| --- | --- | --- |
| 01 | [应用蓝皮书](docs/01-应用蓝皮书.md) | 定位、场景、边界、路线图 |
| 02 | [产品需求分析](docs/02-产品需求分析.md) | 用户故事、功能编号、验收标准 |
| 03 | [产品设计开发文档](docs/03-产品设计开发文档.md) | 架构、状态机、脚本格式、里程碑 |

## 一次作业怎么跑

```mermaid
sequenceDiagram
  actor User as 使用者
  participant App as 浮序
  participant Target as 目标应用
  User->>App: 打开悬浮窗和辅助功能
  User->>App: 新建作业并点选坐标
  User->>Target: 切到要操作的界面
  App->>Target: 按等待时间逐次点击
  User->>App: 点悬浮球停止
  Note over App: 作业仍留在列表里
```

## 版本

图例：蓝是已经落地的范围，黄是紧接着要做的，灰是单独评审后再开工。

```mermaid
flowchart LR
  M0["M0 文档与仓库"] --> M1["M1 单点连点"]
  M1 --> M2["M2 定时与顺序"]
  M2 --> M3["M3 颜色条件"]
  M3 --> M4["M4 画面匹配"]
  M4 --> M5["M5 Windows"]

  classDef done fill:#1f6feb,stroke:#1f6feb,color:#ffffff
  classDef next fill:#9a6700,stroke:#9a6700,color:#ffffff
  classDef later fill:#6e7781,stroke:#6e7781,color:#ffffff
  class M0 done
  class M1,M2 next
  class M3,M4,M5 later
```

| 里程碑 | 交付 |
| --- | --- |
| M0 | 蓝皮书、需求、设计文档 |
| M1 | 悬浮球、手动选点、固定间隔、运行一段时间后停止 |
| M2 | 定时、随机间隔、多点顺序循环 |
| M3 | 导入导出、运行记录、像素颜色条件 |
| M4 | 局部画面匹配，以及如果 / 否则 |
| M5 | Windows 端读取同一份 JSON |

## 运行时各管一段

```mermaid
flowchart TB
  UI["界面<br/>引导、作业、选点"] --> Store["本机作业库"]
  UI --> Run["运行服务<br/>等待、停止条件"]
  Run --> Store
  Run --> A11y["辅助功能<br/>派发一次点击"]
  Run --> Ball["悬浮球与通知<br/>开始、暂停、停止"]
```

界面不直接点击。辅助功能不读作业库。调度只走运行服务。

## 使用边界

浮序只在用户已经授权的这台手机上点击。使用者自己遵守应用商店、游戏、票务平台的规则和当地法律。

| 会做 | 不做 |
| --- | --- |
| 悬浮在其他应用上 | 注入、root、Hook 其他进程 |
| 用户亲手标定的坐标 | 隐藏本应用、伪造输入来源 |
| 间隔、定时、顺序循环 | 对抗反作弊或安全软件 |
| 到时长或次数后停止 | 验证码破解、协议抢票、代抢 |
| 脚本留在本机 | 云端替别人点、批量控制多台设备 |
