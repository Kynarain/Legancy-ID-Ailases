# Legacy ID Aliases

把 1.13 扁平化之前的**数字 ID**（`物品/方块 id + 损伤值`）重新变回可用标识符的 Fabric 模组，
面向 Minecraft **1.21.1**。

```mcfunction
/give @s 35:14      # 红色羊毛 (35 号 = 羊毛, meta 14 = 红色)
/give @s 1:1        # 花岗岩
/give @s 276        # 钻石剑
/give @s minecraft:35_14
```

旧数字 ID 直接成为现代扁平化标识符的**别名**：既能在 `/give` 里用，也能被注册表查询、
数据包和别的模组当作普通 identifier 解析。这个模组同时是一个**对外提供 API 的库模组**，
其他模组可以读取映射表、注册自己的旧 ID，或者复用模组提供的命令参数类型。

## 功能

- **物品 / 方块**：`id` 与 `id:meta` 两种写法；`35:14`、`minecraft:35`、`minecraft:35_14` 均可解析。
- **注册表别名**：别名在注册表 `freeze()` 之前注入，之后所有按 identifier / registry key
  的查询都会命中别名，而不会覆盖任何真实注册项。
- **命令层**：`/give` 的物品参数被替换为支持数字 ID 的参数类型，仍然接受原版写法与数据组件
  （如 `35:14[minecraft:custom_name='红色羊毛']`）。
- **其他命令自动受益**：因为别名注入在注册表层，`/clear @s 35:14`、`/item replace ... with 35:14`、
  `/setblock ~ ~ ~ minecraft:35` 这类由原版自己解析 identifier 的命令同样可以使用数字 ID。
- **其他注册表**：`entity_type`、`biome`、`mob_effect`、`enchantment`、`particle_type`
  同样支持旧数字 ID。
- **旧版没有的数字 ID**：1.13 之后新增的物品会按“加入游戏的版本顺序”继续编号（从 2268 起），
  保证每个条目都有稳定的数字 ID。
- **用户可扩展的 JSON**：`config/legacy_id_aliases/*.json`，覆盖或补充任何映射。

## 数据来源

映射表不是硬编码的，优先使用原版自带的数据：

| 数据 | 来源 |
| --- | --- |
| 方块 `id:meta` → 现代方块 | `net.minecraft.datafixer.fix.BlockStateFlattening`（按 `id << 4 \| meta` 索引） |
| 方块物品 | 上表的方块 id（原版方块物品与方块同名） |
| 物品 `id:meta` → 现代物品 | `ItemInstanceTheFlatteningFix.FLATTENING_MAP`（羊毛、染料、头颅、鱼、唱片……） |
| 1.12 数字 ID 的名称 | `tools/generate_data.py` 生成的 1.12 数据集 |
| 1.13+ 新增物品的顺序 | 同上，按各个版本物品列表首次出现的位置排序 |

`tools/generate_data.py` 只生成原版没有暴露的部分（旧名称、新增顺序、少量改名），
方块/物品的扁平化映射始终在运行时从原版数据推导，因此原版更新后不会出现两份互相矛盾的映射。

## 命令

| 命令 | 说明 |
| --- | --- |
| `/legacyids info` | 显示各注册表的条目数、别名数、无法解析的条目数 |
| `/legacyids lookup <registry> <id[:meta]>` | 查询某一个旧 ID 对应的现代标识符与别名 |
| `/legacyids reload` | 重新读取 `config/legacy_id_aliases/` 并重建别名 |

需要权限等级 2（OP）。

## JSON 映射格式

`config/legacy_id_aliases/` 下的每个 `*.json` 都会被加载（按文件名排序，后加载的覆盖先加载的）。
第一次启动会自动生成 `example.json`：

```json
{
  "schema": 1,
  "registries": {
    "item": {
      "35:14": "minecraft:red_wool",
      "1000": "minecraft:paper"
    },
    "block": {
      "196": "minecraft:oak_sign"
    },
    "entity_type": {
      "60": "minecraft:creeper"
    }
  }
}
```

- 键：`id` 或 `id:meta`（只有 `item` / `block` 支持 meta）。
- 值：现代标识符，可以省略 `minecraft:` 命名空间；也可以写成对象
  `{"id": "minecraft:red_wool"}`。
- 注册表名：`item`、`block`、`entity_type`、`biome`、`mob_effect`、`enchantment`、
  `particle_type`，也接受带命名空间的写法（`minecraft:item`）。
- 用户文件优先级最高，会覆盖内置映射；`/legacyids reload` 可热重载。

## 对外 API

```java
import cn.kynarain.legacyidaliases.api.LegacyId;
import cn.kynarain.legacyidaliases.api.LegacyIdAliases;
import cn.kynarain.legacyidaliases.api.LegacyRegistry;

// 1. 查表
Optional<Identifier> wool = LegacyIdAliases.resolveItem(35, 14);           // minecraft:red_wool
LegacyIdAliases.table(LegacyRegistry.ITEM).lookup(276, 0);                 // minecraft:diamond_sword

// 2. 让指定注册表接受一个别名（不覆盖真实注册项）
LegacyIdAliases.addAlias(Registries.ITEM, Identifier.of("35", "14"), Identifier.of("minecraft", "red_wool"));

// 3. 贡献自己的旧 ID（在模组初始化阶段调用）
LegacyIdAliases.registerSource((registry, sink) -> {
    if (registry == LegacyRegistry.ITEM) {
        sink.accept(LegacyId.of(9000, 3), Identifier.of("yourmod", "ruby"));
    }
});
```

注册表别名接口：

- `LegacyIdAliases.supportsAliases(Registry<?>)`：当前版本是否支持别名（1.21.1 靠模组自己的
  `SimpleRegistry` mixin 实现，1.21.2+ 的原版 `addAlias` 不存在于本分支）。
- `LegacyIdAliases.aliasIds(LegacyRegistry, LegacyId)`：某个旧 ID 的全部别名 identifier。

命令参数类型也可以被其他模组复用：

```java
LegacyItemStackArgumentType.itemStack(registryAccess) // ArgumentType<ItemStackArgument>
// 已注册的类型 id：legacy_id_aliases:legacy_item_stack
```

## 安装

- 服务端、客户端都能装；**客户端不是必需的**。
- 没装模组的客户端：`/give` 的物品参数节点不会出现在客户端的命令树里（原版协议无法识别自定义参数
  类型），但客户端仍然会原样发送命令字符串，服务端照常解析执行，所以 `/give @s 35:14` 依然有效；
  只是没有补全和红色波浪线提示。
- 装了模组的客户端：补全、语法校验、`/legacyids` 全部可用。

依赖：Fabric Loader ≥ 0.16、Fabric API、Java 21、Minecraft 1.21.1。

## 已知限制

- **带 meta 的药水**（`373:<meta>`）无法映射：1.13 之后药水种类由数据组件表达，不是一个物品 ID。
- **动态注册表**（biome / enchantment）的别名在服务器数据加载后、注册表冻结时注入；修改映射后
  需要 `/legacyids reload` 或重进世界才会重建。
- 旧版中“没有物品形式的方块”（水、传送门方块、可可果、作物等）没有物品可给；而曾经是独立
  物品 ID 的方块（如告示牌）会返回该方块的物品形式。
- 1.12 的“M”变体生物群系在世界生成中已被移除，它们没有现代对应项，因此不映射。
- 1.13 之后被删除的粒子（`footstep`、`barrier`、`take`、`snowshovel`）不映射。
- 新增物品的编号顺序来自公开的逐版本物品列表；1.16 的列表在数据源中缺失，因此 1.16 新增物品
  按 1.17 的边界排序（编号仍然是稳定、递增的）。
- 只有 `item` / `block` 使用 meta；其他注册表一个数字对应一个条目。

## 构建与开发

```bash
# JDK 21
./gradlew build            # 产物: build/libs/legacy-id-aliases-<version>.jar
./gradlew runServer        # 开发用服务端
```

数据表重新生成（需要网络，数据来源为 PrismarineJS/minecraft-data）：

```bash
python tools/generate_data.py --download
```

自检（启动真实服务端并验证映射与 `/give` 解析，完成后自动关服）：

```bash
JAVA_TOOL_OPTIONS=-Dlegacy_id_aliases.selftest=true ./gradlew runServer
```

## 版本号

遵循 [SemVer 2.0.0](https://semver.org/lang/zh-CN/)：`MAJOR.MINOR.PATCH`，变更记录见
[CHANGELOG.md](CHANGELOG.md)。

## 许可证

GPL-2.0-only，见 [LICENSE](LICENSE)。

## 映射规模

`/legacyids info` 在 1.21.1 上的实测结果（生产环境 jar，Fabric Loader 0.16.14）：

| 注册表 | 条目数 | 别名数 | 无法解析 |
| --- | --- | --- | --- |
| `minecraft:block` | 4534 | 9068 | 0 |
| `minecraft:item` | 4577 | 9154 | 0 |
| `minecraft:entity_type` | 130 | 130 | 0 |
| `minecraft:biome` | 64 | 64 | 0 |
| `minecraft:mob_effect` | 39 | 39 | 0 |
| `minecraft:enchantment` | 42 | 42 | 0 |
| `minecraft:particle_type` | 112 | 112 | 0 |

“无法解析”指映射目标在 1.21.1 中已不存在而被丢弃的条目（被移除的生物群系、已删除的粒子等），
它们不会留下坏映射。
