# Changelog

所有值得注意的改动都记录在这里，版本号遵循 [SemVer 2.0.0](https://semver.org/lang/zh-CN/)。

## [Unreleased]

## [0.1.0] - 2026-11-17

首个可用版本。

### Added

- 旧数字 ID 映射表：方块与方块物品来自原版 `BlockStateFlattening`，物品 meta 变体来自原版
  `ItemInstanceTheFlatteningFix`。
- 注册表别名：在 `SimpleRegistry#freeze()` 之前注入，`id`、`id:meta`（`35:14`）、
  `minecraft:<id>`、`minecraft:<id>_<meta>` 均可被任何 identifier / registry key 查询命中。
- `/give` 支持旧数字 ID：自定义参数类型 `legacy_id_aliases:legacy_item_stack` 替换原版物品参数，
  仍然支持标识符与数据组件；未安装模组的客户端也能执行（只是没有补全）。
- 其他注册表：`entity_type`、`biome`、`mob_effect`、`enchantment`、`particle_type`。
- 旧版不存在的物品从 2268 起按版本加入顺序继续编号。
- 用户 JSON 映射格式（`config/legacy_id_aliases/*.json`）与 `/legacyids info|lookup|reload`。
- 对外 API：`LegacyIdAliases`（查表、注册别名、贡献旧 ID、别名命名规则）。
