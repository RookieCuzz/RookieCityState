# 默认配置与配套材质

发布 JAR 内的默认资源分为三组，均由源码目录 `src/main/resources` 管理。

## 配置资源

- `resources/*.yml`：主配置、语言、许愿树、守护兽与社交配置。
- `resources/gui/*.yml`：全部 GUI 布局、标题、按钮名称和 lore。
- `resources/shop/*.yml`：默认城邦商店。

首次启动会把可编辑副本安装到 `plugins/RookieCityState/config`，同时把发布时原版保存到
`plugins/RookieCityState/defaults`。升级时只补充缺失的必要配置项，不覆盖管理员已经修改的值。

## 默认城邦世界

- 归档：`world_templates/citystate_template.zip`
- 清单：`world_templates/citystate_template.yml`
- 格式：Java 1.21.4
- 出生点：`0, 101, 0`
- 世界边界：512 格

模板通过 SHA-256 校验后才会安装，并且只在目标模板世界不存在时写入。

## ModelEngine 模型与贴图

默认包含 7 个 r1 蓝图：一个灵兽蛋，以及三种守护兽各自的幼体和成年体。蓝图位于
`modelengine/blueprints/rookiecitystate/r1`，每个 `.bbmodel` 都已经内嵌 PNG 贴图，不需要再手工复制
单独的 PNG 材质。

插件首次启动只安装缺失蓝图到
`plugins/ModelEngine/blueprints/rookiecitystate/r1`，不会覆盖服主已有的同名文件。ModelEngine 负责把
蓝图和内嵌贴图编译进它生成的客户端资源包；RookieCityState 不再额外发布一份容易失配的材质包。

`manifest.yml` 与 `GuardianBundledAssets` 保存发布模型的 ID 和 SHA-256。构建测试会检查：

- 七个模型全部存在且哈希一致；
- `model_identifier` 与文件名一致；
- 贴图确实以内嵌 PNG 保存；
- 蛋、幼体、成年体动画完整；
- `guardian_beast.yml` 引用的默认模型没有遗漏。

若要发布自定义模型，建议使用新的模型 ID 与 revision，避免覆盖 r1 后导致已有服务器无法区分默认资源和自定义资源。

## 本地素材与工具

根目录的 `Chunker`、ModelEngine 测试 JAR 和 `others/模型_解包检查` 只是地图转换或素材核对工具，
不参与 Maven 构建，也不会进入发布 JAR；它们已加入 `.gitignore`，但本地文件不会被删除。
