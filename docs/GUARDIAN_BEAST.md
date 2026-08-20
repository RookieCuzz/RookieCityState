# 城邦公共灵兽 V1

## 玩家玩法

- 新城邦首先显示公共灵兽蛋。会长在“我的城邦 → 城邦公共灵兽”中选择赤焰龙、森灵龙或霜晶龙；确认后普通玩家不能更改。
- 成员主手持有效食物右键灵兽即可喂养，每次消耗一个。空手或无效物品右键打开状态界面，访客只能查看。
- 每名玩家每天最多喂养五次，次数在所有城邦间共享。每日按 `Asia/Shanghai 04:00` 重置。
- 每日随机三种不重复的喜爱食物，喜爱食物提供双倍饱食度和双倍贡献。城邦每日饱食目标默认为 20。
- 当天首次达到目标时累计一个“喂满日”；喂满后仍可继续获得个人贡献，但城邦饱食度不会继续增加。
- 累计喂满日达到 `3/8/18/33/53` 时依次达到 1～5 级。0 级是蛋，1～2 级是幼体，3～5 级是成年体。

个人 `available` 和 `lifetime` 贡献保存在玩家数据中，退出或更换城邦不会清空。`available` 可在[灵兽贡献商店](GUARDIAN_SHOP.md)消费，`lifetime` 永不扣减并用于累计排行。

## ModelEngine 部署

ModelEngine 是硬依赖。插件第一次启动会把七个版本化 `.bbmodel` 补充到：

`plugins/ModelEngine/blueprints/rookiecitystate/r1/`

已有同名文件不会被覆盖。安装结果和 SHA-256 清单位于：

`plugins/RookieCityState/data/guardian_models/r1-manifest.yml`

首次安装后执行 `/meg reload models`，再部署 ModelEngine 生成的资源包。RookieCityState 不会替服务器发送资源包。可用 `/cs guardian models status` 检查七个模型是否已经注册。

任一蓝图 JSON、贴图、模型 ID、动画或幼体/成年体差异校验失败时，只禁用公共灵兽模块，不影响城邦世界与许愿树。

## 配置和数据

- 配置：`plugins/RookieCityState/config/guardian_beast.yml`
- 城邦灵兽：`plugins/RookieCityState/data/guardian_beasts/<cityUuid>.yml`
- 玩家贡献和每日次数：现有 `data/players/<playerUuid>.yml` 的 `guardian_beast` 节点
- 已删除城邦审计：`data/guardian_beasts/deleted/`

配置重载只有整份校验成功后才生效。当日喜爱食物、倍率和目标使用持久化快照，修改从下一个重置日开始生效。

## 管理命令

- `/cs guardian status city <城邦|UUID>`
- `/cs guardian status player <玩家|UUID>`
- `/cs guardian reset daily <城邦> confirm`
- `/cs guardian reset species <城邦> confirm`
- `/cs guardian set days <城邦> <0-53> confirm`
- `/cs guardian grant contribution <玩家> <数量>`
- `/cs guardian visual retry <城邦>`
- `/cs guardian models status`
- `/cs guardian models install`

所有命令均需要 `rookiecitystate.admin`。重置会先生成时间戳备份，重置种类不会清除任何玩家的个人贡献。

## PlaceholderAPI

- `%rookiecitystate_guardian_species%`
- `%rookiecitystate_guardian_form%`
- `%rookiecitystate_guardian_level%`
- `%rookiecitystate_guardian_completed_days%`
- `%rookiecitystate_guardian_daily_fullness%`
- `%rookiecitystate_guardian_daily_target%`
- `%rookiecitystate_guardian_contribution_available%`
- `%rookiecitystate_guardian_contribution_lifetime%`
