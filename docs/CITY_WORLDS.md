# 独立城邦世界部署手册

## 1. 安装前提

服务端固定使用 Paper 1.21.4 与 Java 21。安装并确认以下插件全部成功启用：

1. Vault 和一个经济插件
2. BKCommonLib 与 My_Worlds
3. FastAsyncWorldEdit 与 RookieRegions 2.0.0+（源码项目 `RookieAreaMusic`）
4. RookieCityState

`plugins/My_Worlds/config.yml` 中必须配置：

```yaml
useWorldInventories: false
```

RookieCityState 启动时会检查这一项。不要同时使用 Multiverse、ASP 或其他插件管理
`rcs_city_`、`rcs_template_`、`rcs_recovery_` 前缀的世界。

## 2. 准备模板

插件现在内置经过校验的 Java 1.21.4 城堡岛模板，SHA-256 为
`C0A4B402A71DB61067A04E60291BC3DA22EB70E225C9E453FC2D9E93F55823CF`。默认开启
`city_state.world.bundled_template.enabled`；当 `citystate_template` 世界目录不存在时，插件会在
单线程世界 I/O 队列中安全解压，设置 512×512 边界和 `(0,101,0)` 出生点，并通过 RookieRegions API
创建 `city_core`。已有同名世界绝不会被内置资源覆盖。

内置世界已经包含完整主城，因此默认关闭 `city_state.wish_tree.schematics.main.enabled`；创建城邦时
只额外粘贴一级许愿树。模板来源与哈希记录在 JAR 的
`world_templates/citystate_template.yml`，安装归属标记为
`.rookiecitystate-bundled-template.yml`，该标记不会复制进玩家城邦世界。

如果关闭内置模板或改用自定义模板，则必须在第一次允许玩家创建城邦前完成：

1. 使用 MyWorlds 创建或导入模板世界，准备地形、天气、时间、生物与游戏规则；是否粘贴主城由
   `city_state.wish_tree.schematics.main.enabled` 决定，许愿树始终由 FAWE 维护。
2. 将世界边界设为 512，并把世界出生点放在边界内。
3. 使用 RookieRegions 创建原生区域 `city_core`。它必须是有限区域，且直接挂在该世界的
   `__global__` 根区域下，不能包含 owner/member；模板中的 flags 会复制到城邦世界。
4. 确认模板内没有玩家，保存后执行 `/cs plugin reload` 或重启服务器。

校验成功后插件生成不可变快照 `rcs_template_<template_revision>`。新城邦只从该快照复制，
不会直接读取之后被修改的模板。修改模板、出生点或 `city_core` 后必须把
`city_state.world.template_revision` 加一；旧城邦和旧快照不会被覆盖。

模板或核心区域校验失败时仅禁止创建/补建世界，已有 `READY` 城邦仍可按需加载。

## 3. 访问与保护

- PRIVATE 城邦只允许会长、成员和管理员进入。
- PUBLIC 城邦允许访客参观，但访客不能建造、使用容器、操作实体/载具、倒取流体或掉落/拾取物品。
- `city_area` 覆盖整个 512×512 边界；会长是 owner，成员是 member。
- `city_core` 强制禁止建造、容器和普通交互。拥有 `rookiecitystate.admin` 的在线管理员会临时获得
  RookieRegions bypass，离开世界或退出服务器时自动移除。
- 所有受管世界关闭 PVP，传送门也被禁用。

玩家第一次从普通世界进入城邦时会保存返回位置。城邦之间跳转不会覆盖它；执行
`/cs world exit` 时优先返回原位置，原世界不可用时返回配置的大厅出生点。

## 4. 生命周期与恢复

创建流程在单线程磁盘队列中复制快照，随后回到主线程加载世界，并通过 RookieRegions 原子 API
异步创建 `city_area → city_core → city_wish_tree` 区域层级后卸载。
只有这些步骤完成后才扣款并注册城邦。世界文件包含 `.rookiecitystate-world.yml` 归属标记；
没有正确标记或路径不匹配的目录绝不会自动删除。

解散时玩家先被迁出，RookieRegions 按子区到父区清理该世界的持久化区域，世界再保存并从
MyWorlds 注销，然后归档到：

```text
plugins/RookieCityState/data/archives/<cityUuid>/<timestamp>/
```

归档默认保存 7 天。恢复只生成 `rcs_recovery_<archiveId>` 管理员世界，不恢复原城邦、成员、
银行或申请。`READY` 世界目录缺失会把城邦置为 `ERROR`，插件不会克隆空地图顶替。

创建、扣款、退款和归档阶段记录在 `data/operations`。如果进程恰好中断于外部扣款调用，
控制台会输出需要核对的操作 ID。管理员核对经济流水后执行：

```text
/cs operation list
/cs operation resolve <ID> charged
/cs operation resolve <ID> not_charged
```

不要猜测状态；错误结论可能造成漏扣或资产损失。

## 5. 发布前真服合同测试

在目标 Paper 1.21.4 实例至少验证：模板快照、城邦复制、世界 UUID 独立、RookieRegions 区域、
共享背包、保存/卸载、重启后建筑保持、解散归档和归档恢复。还应在复制与归档期间强制终止
测试服务器，确认重启后 `/cs world reconcile` 能恢复且不会重复扣款。

最后批量暂存 200 个城邦，确认只有存在玩家、传送租约或维护操作的世界保持加载，并监控
磁盘容量与主线程 tick。模板复制和归档移动不得出现在主线程耗时记录中。

许愿树还必须验证：首次占位结构生成、五级粘贴、`city_wish_tree` 保护、实体交互去重、
升级期间强制终止后的备份回滚，以及 Vault/PlayerPoints/指令奖励在 `AMBIGUOUS` 状态下不会自动重发。
