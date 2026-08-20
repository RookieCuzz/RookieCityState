# RookieCityState

面向 Paper 1.21.4、Java 21 的独立世界城邦插件。每个城邦从不可变模板快照创建一个
`rcs_city_<UUID>` 世界；地图按需加载，无人且没有传送租约 300 秒后保存并卸载。
发布包内置 Java 1.21.4 城堡岛模板；首次启动仅在 `citystate_template` 不存在时自动安装，
不会覆盖管理员已有世界。

## 运行依赖

- 必需：Vault、一个 Vault 经济插件、BKCommonLib、My_Worlds、FastAsyncWorldEdit、RookieRegions
- 公共灵兽额外硬依赖：ModelEngine `R4.0.9`
- 可选：PlaceholderAPI、PlayerPoints
- 目标合同组合：MyWorlds/BKCommonLib `1.21.5-v1`、Paper 1.21.4 兼容的 FAWE 构建、
  RookieRegions API `2.0.0+`（源码项目 `RookieAreaMusic`）
- MyWorlds 的 `useWorldInventories` 必须为 `false`，城邦世界与主世界共享物品栏和经验

安装及模板准备见 [独立城邦世界部署手册](docs/CITY_WORLDS.md)。上述目标组合必须先在
Paper 1.21.4 真服完成合同测试；插件不会自动切换到其他世界后端。

## 命令

- 玩家：`/cs gui main`、`/cs world exit`、`/cs social hot`
- 管理员：`/cs world status|load|unload|provision <城邦>`、`/cs world reconcile`
- 归档：`/cs archive list`、`/cs archive restore <归档ID>`
- 恢复世界：`/cs recovery unload|delete <世界名>`
- 异常支付：`/cs operation list`、`/cs operation resolve <操作ID> charged|not_charged`
- 许愿树：`/cs wishtree status|reset|grant|claim|visual ...`
- 公共灵兽：`/cs guardian status|reset|set|grant|visual|models ...`
- 灵兽贡献商店：`/cs guardian shop status|rotate|grant|revoke|reset ...`
- 城邦社交：`/cs social status|vote|reset|rebuild ...`

除 `world exit` 外，世界管理命令需要 `rookiecitystate.admin`。

许愿树玩法、结构文件与恢复说明见 [许愿树 V1 手册](docs/WISH_TREE.md)。
公共灵兽玩法、模型部署与运维说明见 [公共灵兽 V1 手册](docs/GUARDIAN_BEAST.md)。

发布包内置配置、城邦模板、ModelEngine 蓝图与贴图清单见
[默认配置与配套材质](docs/DEFAULT_ASSETS.md)。

灵兽贡献消费、每周轮换、装扮和奖励箱规则见 [灵兽贡献商店 V1 手册](docs/GUARDIAN_SHOP.md)。

公开城邦参观、点赞与热门榜规则见 [访客点赞与热门城邦 V1 手册](docs/CITY_SOCIAL.md)。

## 构建

- 构建与测试：`mvn clean verify`
- 成品：`target/RookieCityState.jar`
