# 灵兽贡献商店 V1

## 玩法规则

- 喂养灵兽获得的 `available` 用于购买商品，`lifetime` 只记录累计贡献，消费时不会减少。
- 商店仅向 `ACTIVE + READY` 城邦的当前成员开放。退出、换邦或城邦不可用时，永久商品和装备数据仍保留，但效果暂停。
- 每周一 `Asia/Shanghai 04:00` 生成一次全服共享轮换，从有效商品池中加权抽取六个不同商品。
- 当前轮换保存完整商品快照；周中重载或修改配置不会改变本周货架，已购永久商品也不会因配置删除而失效。
- 粒子、称号、聊天前缀和互动动作是永久商品，只能购买一次。装饰礼包和魔力石礼包按配置进行每周限购，并进入许愿树共用的待领取奖励箱。

## 装扮与互动

- 粒子只会在玩家自己的城邦世界中运行。离开世界、退邦或下线后立即停止；任务不会加载世界或持有世界租约。
- 称号占位符：`%rookiecitystate_guardian_title%`。
- 聊天前缀占位符：`%rookiecitystate_guardian_chat_prefix%`。
- 旧聊天格式也可使用 `<rookiecitystate_guardian_title>` 和 `<rookiecitystate_guardian_chat_prefix>`；插件只替换 token，不接管聊天格式。
- 已装备动作从灵兽 GUI 播放。玩家必须位于自己的城邦灵兽八格内，同一城邦默认有 15 秒冷却，动画播放期间不能重入。
- 四个装备槽互相独立：`PARTICLE`、`TITLE`、`CHAT_PREFIX`、`ACTION`。

## 配置和数据

- 商品目录：`plugins/RookieCityState/config/guardian_shop.yml`
- 周轮换：`plugins/RookieCityState/data/guardian_shop/rotations/<weekCycle>.yml`
- 玩家所有权、装备和限购：现有 `data/players/<playerUuid>.yml` 的 `guardian_beast.shop` 节点
- 管理操作备份：`plugins/RookieCityState/data/guardian_shop/admin_backups/<playerUuid>/`

商品类型为 `PARTICLE`、`TITLE`、`CHAT_PREFIX`、`ACTION`、`ITEM` 和 `MAGIC_STONE`。商品 ID 只能包含小写字母、数字、下划线和短横线。配置只有在整份校验成功后才替换；当周轮换生成失败时继续使用最近一个有效快照。

轮换只保留最近 12 周。轮换快照包含随机种子、生成时间、配置修订及六个商品的完整内容，因此不依赖之后的商品目录。

## 奖励箱与事务

- 消耗型商品购买成功后写入现有许愿树奖励箱，只允许 `ITEM` 和 `MAGIC_STONE`。
- 奖励箱已满时购买直接失败，不扣贡献。
- 购买在玩家锁内单航班执行；贡献扣除、永久商品或奖励记录及限购次数只原子保存一次。
- 保存失败时恢复购买前快照；`lifetime` 永远不参与扣减。
- 外部发奖的 `DISPATCHING`、`AMBIGUOUS` 和人工核对规则沿用许愿树奖励箱。

## 管理命令

以下命令均需要 `rookiecitystate.admin`，并对撤销、重置操作保留玩家 YAML 备份：

- `/cs guardian shop status`
- `/cs guardian shop status player <玩家>`
- `/cs guardian shop rotate confirm`
- `/cs guardian shop grant product <玩家> <商品ID>`
- `/cs guardian shop revoke product <玩家> <商品ID> confirm`
- `/cs guardian shop reset limits <玩家> confirm`

撤销永久商品时会同步卸下对应装备。已经领取的装饰礼包或魔力石不会被追回。
