package cn.kynarain.legacyidaliases.impl;

import cn.kynarain.legacyidaliases.api.LegacyId;
import cn.kynarain.legacyidaliases.api.LegacyRegistry;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import net.minecraft.datafixer.fix.BlockStateFlattening;
import net.minecraft.datafixer.fix.ItemInstanceTheFlatteningFix;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Derives the legacy tables from Minecraft's own flattening data instead of shipping a second copy
 * of it:
 *
 * <ul>
 *   <li>blocks: {@link BlockStateFlattening}, indexed by {@code (id << 4) | meta};</li>
 *   <li>items: the same block data for block items, plus {@link ItemInstanceTheFlatteningFix} for
 *       the items whose damage value used to select a different item (wool, dye, skulls, ...).</li>
 * </ul>
 */
final class LegacyVanillaData {
	private static final int BLOCK_COUNT = 256;
	private static final int META_COUNT = 16;
	private static final Identifier AIR = Identifier.of("minecraft", "air");
	private static final Identifier SPAWN_EGG_NAME = Identifier.of("minecraft", "spawn_egg");

	private LegacyVanillaData() {
	}

	static void generateBlocks(Map<LegacyId, Identifier> out) {
		for (int id = 0; id < BLOCK_COUNT; id++) {
			for (int meta = 0; meta < META_COUNT; meta++) {
				Identifier identifier = flattenedBlock(LegacyRegistry.BLOCK, id, meta);
				if (identifier != null) {
					out.putIfAbsent(new LegacyId(id, meta), identifier);
				}
			}
		}
	}

	static void generateItems(Map<LegacyId, Identifier> out) {
		// Legacy item ids below 256 are exactly the block ids, but only the blocks that had an item
		// (there is no item for water, portal blocks, cocoa, ...).
		for (int id = 0; id < BLOCK_COUNT; id++) {
			for (int meta = 0; meta < META_COUNT; meta++) {
				Identifier identifier = flattenedBlock(LegacyRegistry.ITEM, id, meta);
				if (identifier != null && itemExists(identifier)) {
					out.putIfAbsent(new LegacyId(id, meta), identifier);
				}
			}
		}

		Map<String, String> flattening = flatteningMap();
		for (Map.Entry<Integer, String> entry : LegacyData.itemNames().entrySet()) {
			int id = entry.getKey();
			String base = "minecraft:" + entry.getValue();
			boolean variants = false;
			for (int meta = 0; meta < META_COUNT; meta++) {
				String target = flattening.get(base + "." + meta);
				if (target == null) {
					continue;
				}
				Identifier identifier = modernItem(target);
				if (identifier != null) {
					out.putIfAbsent(new LegacyId(id, meta), identifier);
					variants = true;
				}
			}
			if (!variants) {
				String target = flattening.get(base + ".0");
				Identifier identifier = target != null ? modernItem(target) : modernItem(base);
				if (identifier != null) {
					out.putIfAbsent(new LegacyId(id, 0), identifier);
				}
			}
			if (SPAWN_EGG_NAME.getPath().equals(entry.getValue())) {
				generateSpawnEggs(id, out);
			}
		}
	}

	/**
	 * Legacy {@code 383:<entity id>} used to be a spawn egg of the entity with that legacy id.
	 */
	private static void generateSpawnEggs(int id, Map<LegacyId, Identifier> out) {
		for (Map.Entry<Integer, String> entry : LegacyData.registryNames(LegacyRegistry.ENTITY_TYPE).entrySet()) {
			String renamed = LegacyData.rename(LegacyRegistry.ENTITY_TYPE, "minecraft:" + entry.getValue());
			if (renamed == null) {
				continue;
			}
			Identifier egg = Identifier.tryParse(renamed + "_spawn_egg");
			if (egg != null && itemExists(egg)) {
				out.putIfAbsent(new LegacyId(id, entry.getKey()), egg);
			}
		}
	}

	private static boolean itemExists(Identifier identifier) {
		return LegacyTables.rawContains(Registries.ITEM, identifier);
	}

	static void generateFromNames(LegacyRegistry registry, Map<LegacyId, Identifier> out) {
		for (Map.Entry<Integer, String> entry : LegacyData.registryNames(registry).entrySet()) {
			String renamed = LegacyData.rename(registry, "minecraft:" + entry.getValue());
			if (renamed == null) {
				continue;
			}
			Identifier identifier = Identifier.tryParse(renamed);
			if (identifier != null) {
				out.putIfAbsent(new LegacyId(entry.getKey(), 0), identifier);
			}
		}
	}

	private static Identifier modernItem(String flatteningTarget) {
		String renamed = LegacyData.rename(LegacyRegistry.ITEM, flatteningTarget);
		return renamed == null ? null : Identifier.tryParse(renamed);
	}

	/**
	 * @return the flattened block id for {@code id:meta}, or {@code null} for air / unknown ids
	 */
	private static Identifier flattenedBlock(LegacyRegistry registry, int id, int meta) {
		Dynamic<?> state = BlockStateFlattening.lookupState((id << 4) | meta);
		if (state == null) {
			return null;
		}
		String name = state.get("Name").asString("");
		if (name.isEmpty()) {
			return null;
		}
		String renamed = LegacyData.rename(registry, name);
		if (renamed == null) {
			return null;
		}
		Identifier identifier = Identifier.tryParse(renamed);
		return identifier == null || identifier.equals(AIR) ? null : identifier;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String> flatteningMap() {
		return (Map<String, String>) (Map<?, ?>) ItemInstanceTheFlatteningFix.FLATTENING_MAP;
	}
}
