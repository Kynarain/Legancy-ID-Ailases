#!/usr/bin/env python3
"""Generates the bundled legacy-id datasets of Legacy ID Aliases.

The generated files are *sources* for the runtime tables, not the final tables:
the mod still derives the block/block-item tables from Minecraft's own
`BlockStateFlattening`/`ItemInstanceTheFlatteningFix` data at runtime, which is
why this script only ships the data that vanilla does not expose:

* legacy 1.12 item ids -> 1.12 item names (ids >= 256),
* legacy 1.12 ids -> names for the other registries,
* the ordered list of items that did not exist before the flattening (1.13+),
  in the order they were added version by version,
* a small rename table for legacy names that were renamed after 1.12.

Usage:
    python tools/generate_data.py [--data-dir tools/mcdata] [--download]

The source datasets come from PrismarineJS/minecraft-data (MIT licensed) and are
fetched on demand with --download; they are not committed to this repository.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.request
from pathlib import Path

RAW = "https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc"

# Chronological list used to order items that were added after 1.12.
VERSIONS = [
    "1.13", "1.13.1", "1.13.2", "1.14", "1.14.1", "1.14.3", "1.14.4",
    "1.15", "1.15.1", "1.15.2", "1.16", "1.16.1", "1.16.2", "1.16.3", "1.16.4",
    "1.16.5", "1.17", "1.17.1", "1.18", "1.18.1", "1.18.2", "1.19", "1.19.2",
    "1.19.3", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5",
    "1.20.6", "1.21", "1.21.1",
]

# Names that changed after the flattening. Values are the 1.21.1 names.
# Names produced by the vanilla flattening tables that are stale in 1.21.1 (block items).
_BLOCKY = {
    "grass": "short_grass",
    "tallgrass": "short_grass",
    "oak_bark": "oak_wood",
    "spruce_bark": "spruce_wood",
    "birch_bark": "birch_wood",
    "jungle_bark": "jungle_wood",
    "mob_spawner": "spawner",
    "melon_block": "melon",
    "acacia_bark": "acacia_wood",
    "dark_oak_bark": "dark_oak_wood",
    "grass_path": "dirt_path",
    "sign": "oak_sign",
    "wall_sign": "oak_wall_sign",
    "portal": "nether_portal",
    "deadbush": "dead_bush",
    "lit_pumpkin": "jack_o_lantern",
    "snow_layer": "snow",
    "stonebrick": "stone_bricks",
    "silver_glazed_terracotta": "light_gray_glazed_terracotta",
    "silver_shulker_box": "light_gray_shulker_box",
    "hardened_clay": "terracotta",
    "brick_block": "bricks",
    "red_nether_brick": "red_nether_bricks",
    "waterlily": "lily_pad",
    "web": "cobweb",
    "noteblock": "note_block",
    "slime": "slime_block",
    "magma": "magma_block",
}

RENAMES = {
    "item": {
        **_BLOCKY,
        # 1.13 -> 1.14 dye split
        "rose_red": "red_dye",
        "cactus_green": "green_dye",
        "dandelion_yellow": "yellow_dye",
        "clownfish": "tropical_fish",
        # item renames after the flattening
        "sign": "oak_sign",
        "wooden_door": "oak_door",
        "boat": "oak_boat",
        "reeds": "sugar_cane",
        "speckled_melon": "glistering_melon_slice",
        "netherbrick": "nether_brick",
        "chorus_fruit_popped": "popped_chorus_fruit",
        "bed": "white_bed",
        "banner": "white_banner",
        "fireworks": "firework_rocket",
        "firework_charge": "firework_star",
        "record_11": "music_disc_11",
        "record_13": "music_disc_13",
        "record_blocks": "music_disc_blocks",
        "record_cat": "music_disc_cat",
        "record_chirp": "music_disc_chirp",
        "record_far": "music_disc_far",
        "record_mall": "music_disc_mall",
        "record_mellohi": "music_disc_mellohi",
        "record_stal": "music_disc_stal",
        "record_strad": "music_disc_strad",
        "record_wait": "music_disc_wait",
        "record_ward": "music_disc_ward",
    },
    "block": dict(_BLOCKY),
    "entity_type": {
        "xp_orb": "experience_orb",
        "eye_of_ender_signal": "eye_of_ender",
        "xp_bottle": "experience_bottle",
        "evocation_illager": "evoker",
        "vindication_illager": "vindicator",
        "illusion_illager": "illusioner",
        "evocation_fangs": "evoker_fangs",
        "commandblock_minecart": "command_block_minecart",
        "zombie_pigman": "zombified_piglin",
        "snowman": "snow_golem",
        "villager_golem": "iron_golem",
        "ender_crystal": "end_crystal",
        "fireworks_rocket": "firework_rocket",
        "primed_tnt": "tnt",
        "falling_sand": "falling_block",
        "fish": "cod",
    },
    "biome": {
        # 1.13 / 1.18 renames of biomes that still exist. Removed biomes (the *_hills variants,
        # mutated_*, mushroom_field_shore, ...) are deliberately left unmapped.
        "mountains": "windswept_hills",
        "wooded_mountains": "windswept_forest",
        "gravelly_mountains": "windswept_gravelly_hills",
        "snowy_tundra": "snowy_plains",
        "giant_tree_taiga": "old_growth_pine_taiga",
        "giant_spruce_taiga": "old_growth_spruce_taiga",
        "tall_birch_forest": "old_growth_birch_forest",
        "jungle_edge": "sparse_jungle",
        "stone_shore": "stony_shore",
        "shattered_savanna": "windswept_savanna",
        "wooded_badlands_plateau": "wooded_badlands",
    },
    "mob_effect": {
        "bad_luck": "unluck",
    },
    "enchantment": {
        "sweeping": "sweeping_edge",
    },
    "particle_type": {
        # 1.12 particle ids -> modern particle ids. Removed particles (footstep, barrier,
        # snowshovel, take) stay unmapped on purpose.
        "explode": "poof",
        "largeexplode": "explosion",
        "hugeexplosion": "explosion_emitter",
        "fireworks_spark": "firework",
        "wake": "fishing",
        "suspended": "underwater",
        "depthsuspend": "underwater",
        "magic_crit": "enchanted_hit",
        "spell": "effect",
        "instant_spell": "instant_effect",
        "mob_spell": "entity_effect",
        "largesmoke": "large_smoke",
        "mob_spell_ambient": "entity_effect",
        "witch_magic": "witch",
        "drip_water": "dripping_water",
        "drip_lava": "dripping_lava",
        "angry_villager": "angry_villager",
        "happy_villager": "happy_villager",
        "townaura": "mycelium",
        "enchantmenttable": "enchant",
        "reddust": "dust",
        "snowballpoof": "item_snowball",
        "slime": "item_slime",
        "iconcrack": "item",
        "blockcrack": "block",
        "blockdust": "block",
        "droplet": "rain",
        "mobappearance": "elder_guardian",
        "dragonbreath": "dragon_breath",
        "end_rod": "end_rod",
        "fallingdust": "falling_dust",
        "totem": "totem_of_undying",
    },
}
REGISTRY_FILES = {
    "entity_type": "1.12_entities.json",
    "biome": "1.12_biomes.json",
    "mob_effect": "1.12_effects.json",
    "enchantment": "1.12_enchantments.json",
    "particle_type": "1.12_particles.json",
}

# Registries whose dataset mixes several id namespaces; the legacy numeric id is the internal one.
INTERNAL_ID_REGISTRIES = {"entity_type"}


def fetch(url: str, dest: Path) -> bool:
    if dest.exists() and dest.stat().st_size > 0:
        return True
    try:
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read()
    except Exception as error:  # noqa: BLE001 - best effort download
        print(f"  ! {url} -> {error}", file=sys.stderr)
        return False
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    return True


def load(path: Path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def normalise_name(name: str) -> str:
    """`MiningFatigue`, `Mining Fatigue` and `mining_fatigue` all become `mining_fatigue`."""
    text = name.strip().replace("-", "_").replace(" ", "_")
    text = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", "_", text)
    return re.sub(r"_+", "_", text).lower()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", default="tools/mcdata", type=Path)
    parser.add_argument("--download", action="store_true", help="fetch missing source datasets")
    parser.add_argument(
        "--output",
        default="src/main/resources/data/legacy_id_aliases/legacy_ids",
        type=Path,
    )
    args = parser.parse_args()
    data_dir: Path = args.data_dir
    data_dir.mkdir(parents=True, exist_ok=True)

    def source(name: str) -> Path:
        path = data_dir / name
        if args.download:
            fetch(f"{RAW}/{name.replace('1.12_', '1.12/')}", path)
        return path

    items_112 = load(source("1.12_items.json"))
    item_names_112 = {int(item["id"]): item["name"] for item in items_112}
    legacy_names = {str(i): n for i, n in sorted(item_names_112.items()) if i >= 256}

    versions: dict[str, list[dict]] = {}
    for version in VERSIONS:
        path = data_dir / "versions" / f"items_{version}.json"
        if not path.exists():
            if args.download:
                fetch(f"{RAW}/{version}/items.json", path)
            if not path.exists():
                print(f"  ! no item list for {version}", file=sys.stderr)
                continue
        versions[version] = load(path)

    current = versions.get("1.21.1")
    if current is None:
        print("error: the 1.21.1 item list is required", file=sys.stderr)
        return 1
    modern_names = [item["name"] for item in current]
    modern_by_name = {item["name"]: item for item in current}

    first_seen: dict[str, str] = {}
    for version in VERSIONS:
        for item in versions.get(version, []):
            first_seen.setdefault(item["name"], version)

    legacy_flat = set(item_names_112.values())
    additions = []
    for order, name in enumerate(modern_names):
        if name in legacy_flat:
            continue
        version = first_seen.get(name, "1.21.1")
        protocol_id = int(modern_by_name[name].get("id", order))
        additions.append(
            {
                "name": name,
                "since": version,
                "_version": VERSIONS.index(version) if version in VERSIONS else len(VERSIONS),
                "_id": protocol_id,
                "_order": order,
            }
        )
    additions.sort(key=lambda entry: (entry["_version"], entry["_id"], entry["_order"]))
    for entry in additions:
        del entry["_version"], entry["_id"], entry["_order"]

    registry_names = {}
    for registry, filename in REGISTRY_FILES.items():
        path = source(filename)
        if not path.exists():
            print(f"  ! missing {path}", file=sys.stderr)
            continue
        entries = load(path)
        table = {}
        for entry in entries:
            if registry in INTERNAL_ID_REGISTRIES:
                # entity ids from minecraft-data contain both the mob list and the "object" list,
                # whose ids are slot indexes; the legacy entity id is the internal id.
                if entry.get("type") not in (None, "mob"):
                    continue
                identifier = entry.get("internalId", entry.get("id"))
            else:
                identifier = entry.get("id")
            raw = normalise_name(entry.get("name", ""))
            if not raw or identifier is None:
                continue
            table[str(int(identifier))] = raw
        registry_names[registry] = dict(sorted(table.items(), key=lambda kv: int(kv[0])))

    output: Path = args.output
    output.mkdir(parents=True, exist_ok=True)

    def dump(name: str, payload) -> None:
        path = output / name
        data = json.dumps(payload, indent=1, ensure_ascii=False) + "\n"
        with path.open("w", encoding="utf-8", newline="\n") as handle:
            handle.write(data)
        print(f"wrote {path} ({path.stat().st_size} bytes)")

    dump("legacy_item_names.json", legacy_names)
    dump("legacy_registry_names.json", registry_names)
    dump("modern_item_additions.json", additions)
    dump(
        "renames.json",
        {registry: dict(sorted(table.items())) for registry, table in RENAMES.items()},
    )

    # Diagnostics: legacy names that 1.21.1 does not know even after renames.
    renamed = {**RENAMES["item"]}
    modern_set = set(modern_names)
    unresolved = sorted(
        f"{i}={n}"
        for i, n in item_names_112.items()
        if i >= 256 and n not in modern_set and renamed.get(n, n) not in modern_set
    )
    print(f"\nlegacy item ids >= 256: {len(legacy_names)}")
    print(f"post-1.12 item additions: {len(additions)}")
    print(f"registries with legacy tables: {', '.join(sorted(registry_names))}")
    print(f"legacy item names still unresolved: {len(unresolved)}")
    for entry in unresolved:
        print(f"  {entry}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
