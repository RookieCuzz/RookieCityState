# 访客点赞与热门城邦 V1

该模块为公开城邦提供独立于综合排名的社交热度。只有生命周期为 `ACTIVE`、世界状态为
`READY` 且可见性为 `PUBLIC` 的城邦会出现在热门榜；切换为私有不会删除历史数据，但会
立即退出榜单并终止访客计时。

## 玩家规则

- 非本城邦成员在同一个公开城邦世界连续停留 60 秒后，取得本周点赞资格。
- 离开世界、下线、城邦转私有、城邦失效或中途加入目标城邦都会中止本次计时。
- 资格在下一个周一 `Asia/Shanghai 04:00` 失效。
- 每位玩家每周全服共有 5 票，同一城邦每周只能点赞一次，点赞后不能主动取消。
- 点赞不产生城邦币、物品或个人货币奖励。
- `/cs social hot` 打开热门榜；也可以从城邦主界面的“热门城邦”进入。

默认热度为 `近7日独立合格访客 × 1 + 近7日点赞 × 3`。排序依次比较热度、近 7 日
点赞、近 7 日独立访客、历史总赞和城邦 UUID，不会影响原有综合排名。

## 配置与数据

`config/city_social.yml` 可设置时区、04:00 边界、合格停留秒数、每周票数、热度窗口、
权重和明细保留天数。重载时会先验证完整文件；校验失败继续使用旧配置。

每个城邦的数据保存在 `data/social/cities/<城邦UUID>.yml`，历史总赞和点赞明细在同一次
原子保存中提交。超过默认 35 天的明细会被清理，历史总赞永久保留。损坏或未来版本文件
会移动到 `data/quarantine/social`，并通过 `data/social/errors` 标记该城邦社交模块为
`ERROR`，不会按空数据覆盖。城邦解散后文件移入删除审计目录。

访客追踪每秒只检查在线玩家当前所在的已加载世界，不会加载城邦世界，也不会持有世界租约。

## 管理命令

- `/cs social status city <城邦|UUID>`：查看城邦社交状态及当前热度。
- `/cs social status player <玩家|UUID>`：查看本周票数、资格与已点赞城邦。
- `/cs social vote revoke <城邦> <玩家> <weekCycle> confirm`：备份后撤销指定周点赞。
- `/cs social reset recent <城邦> confirm`：备份后清除保留期内明细，保留历史总赞。
- `/cs social reset all <城邦> confirm`：备份后清除明细和历史总赞。
- `/cs social rebuild`：从城邦社交文件重建投票索引和热门榜。

管理操作需要 `rookiecitystate.admin`，撤票和重置会写入插件管理日志。

## 开发接口

`RookieCityStateAPI.getCitySocialService()` 提供 `getView`、`getPopularCities`、
`isQualified` 和异步结果形式的 `like`。`CityVisitQualifiedEvent` 与 `CityLikedEvent`
只会在对应数据成功落盘后触发。所有 Bukkit 对象相关调用应在主线程发起；`like` 会自动
切回主线程执行事务。
