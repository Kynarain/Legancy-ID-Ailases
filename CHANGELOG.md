# Changelog

所有值得注意的改动都记录在这里，版本号遵循 [SemVer 2.0.0](https://semver.org/lang/zh-CN/)。

## [Unreleased]

## [0.2.0] - 2026-11-17

### Added

- 药水支持：`373:<damage>`（含 `0x4000` 喷溅位）、`438:<damage>`、`441:<damage>` 会解析成
  `potion` / `splash_potion` / `lingering_potion` 并附带 `minecraft:potion_contents` 组件，
  药水种类来自原版 `ItemPotionFix.ID_TO_POTIONS`。
- `LegacyIdTarget`（identifier + 可选的药水组件）、`LegacyIdAliases.resolveTarget` 与
  `resolveItemStack`。
- JSON 映射值支持 `{"id": "...", "potion": "..."}`，`/give` 里数字形式也可以继续追加组件
  （例如 `373:5[minecraft:custom_model_data=7]`）。

### Changed

- **不兼容**：`LegacyIdTable` 的值类型改为 `LegacyIdTarget`，`LegacyIdSource` 的 sink 也改为
  `BiConsumer<LegacyId, LegacyIdTarget>`（`0.y.z` 阶段，MINOR 版本允许破坏性变更）。

### Fixed

- `373:<任意 meta>` 之前会静默回退成水瓶，现在按旧版伤害表解析。

## [0.1.1] - 2026-11-17

### Fixed

- 首次启动写入的示例映射文件不再被当成真实映射加载：它现在保存为
  `config/legacy_id_aliases/example.json.example`，复制成 `.json` 才会生效。

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
