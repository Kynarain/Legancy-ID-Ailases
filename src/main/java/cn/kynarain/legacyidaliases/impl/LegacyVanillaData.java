package cn.kynarain.legacyidaliases.impl;

import cn.kynarain.legacyidaliases.api.LegacyId;
import cn.kynarain.legacyidaliases.api.LegacyIdTarget;
import cn.kynarain.legacyidaliases.api.LegacyRegistry;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import net.minecraft.datafixer.fix.BlockStateFlattening;
import net.minecraft.datafixer.fix.ItemInstanceTheFlatteningFix;
import net.minecraft.datafixer.fix.ItemPotionFix;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Derives the legacy tables from Minecraft's own flattening data instead of shipping a second copy
 * of it:
 *
 * <ul>
 *   <li>blocks: {@link BlockStateFlattening}, indexed by {@code (id << 4) | meta};</li>
 *   <li>items: the same block data for block items, plus {@link ItemInstanceTheFlatteningFix} for
 *       the items whose damage value used to select a different item (wool, dye, skulls, ...);</li>
 *   <li>potions: {@link ItemPotionFix#ID_TO_POTIONS}, the pre-flattening damage table.</li>
 * </ul>
 */
final class LegacyVanillaData {
	private static final int BLOCK_COUNT = 256;
	private static final int META_COUNT = 16;
	private static final Identifier AIR = Identifier.of("minecraft", "air");
	private static final Identifier SPAWN_EGG_NAME = Identifier.of("minecraft", "spawn_egg");

	private static final int POTION_ITEM = 373;
	private static final int SPLASH_POTION_ITEM = 438;
	private static final int LINGERING_POTION_ITEM = 441;
	/** Set in the legacy potion damage to ask for a splash potion. */
	private static final int SPLASH_FLAG = 0x4000;
	private static final Identifier WATER_POTION = Identifier.of("minecraft", "water");
	private static final Identifier POTION = Identifier.of("minecraft", "potion");
	private static final Identifier SPLASH_POTION = Identifier.of("minecraft", "splash_potion");
	private static final Identifier LINGERING_POTION = Identifier.of("minecraft", "lingering_potion");

	private LegacyVanillaData() {
	}

	static void generateBlocks(Map<LegacyId, LegacyIdTarget> out) {
		for (int id = 0; id < BLOCK_COUNT; id++) {
			for (int meta = 0; meta < META_COUNT; meta++) {
				Identifier identifier = flattenedBlock(LegacyRegistry.BLOCK, id, meta);
				if (identifier != null) {
					out.putIfAbsent(new LegacyId(id, meta), LegacyIdTarget.of(identifier));
				}
			}
		}
	}

	static void generateItems(Map<LegacyId, LegacyIdTarget> out) {
		// Legacy item ids below 256 are exactly the block ids, but only the blocks that had an item
		// (there is no item for water, portal blocks, cocoa, ...).
		for (int id = 0; id < BLOCK_COUNT; id++) {
			for (int meta = 0; meta < META_COUNT; meta++) {
				Identifier identifier = flattenedBlock(LegacyRegistry.ITEM, id, meta);
				if (identifier != null && itemExists(identifier)) {
					out.putIfAbsent(new LegacyId(id, meta), LegacyIdTarget.of(identifier));
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
					out.putIfAbsent(new LegacyId(id, meta), LegacyIdTarget.of(identifier));
					variants = true;
				}
			}
			if (!variants) {
				String target = flattening.get(base + ".0");
				Identifier identifier = target != null ? modernItem(target) : modernItem(base);
				if (identifier != null) {
					out.putIfAbsent(new LegacyId(id, 0), LegacyIdTarget.of(identifier));
				}
			}
			if (SPAWN_EGG_NAME.getPath().equals(entry.getValue())) {
				generateSpawnEggs(id, out);
			}
		}

		generatePotions(out);
	}

	/**
	 * Legacy potions are an item id plus a damage value; the potion type itself lives in a data
	 * component since 1.13. The damage table is the one vanilla still ships in
	 * {@link ItemPotionFix} (low 7 bits select the potion, {@code 0x4000} asks for a splash potion).
	 */
	private static void generatePotions(Map<LegacyId, LegacyIdTarget> out) {
		String[] damageToPotion = ItemPotionFix.ID_TO_POTIONS;
		for (int damage = 0; damage < damageToPotion.length; damage++) {
			Identifier potion = potionId(damageToPotion[damage]);
			if (potion == null) {
				continue;
			}
			putPotion(out, POTION_ITEM, damage, POTION, potion);
			putPotion(out, POTION_ITEM, SPLASH_FLAG | damage, SPLASH_POTION, potion);
			// 1.9+ has separate items for splash and lingering potions; the damage is reused as the
			// potion selector here so 438:5 and 441:5 behave like the old numeric ids.
			putPotion(out, SPLASH_POTION_ITEM, damage, SPLASH_POTION, potion);
			putPotion(out, LINGERING_POTION_ITEM, damage, LINGERING_POTION, potion);
		}
	}

	private static void putPotion(
		Map<LegacyId, LegacyIdTarget> out,
		int itemId,
		int meta,
		Identifier item,
		Identifier potion
	) {
		out.put(new LegacyId(itemId, meta), LegacyIdTarget.ofPotion(item, potion));
	}

	/**
	 * @return the potion registry id of a legacy potion name, or {@code null} if 1.21.1 removed it
	 */
	private static Identifier potionId(String name) {
		if (name == null) {
			return WATER_POTION;
		}
		Identifier identifier = Identifier.tryParse(name);
		return identifier != null && Registries.POTION.containsId(identifier) ? identifier : null;
	}

	/**
	 * Legacy {@code 383:<entity id>} used to be a spawn egg of the entity with that legacy id.
	 */
	private static void generateSpawnEggs(int id, Map<LegacyId, LegacyIdTarget> out) {
		for (Map.Entry<Integer, String> entry : LegacyData.registryNames(LegacyRegistry.ENTITY_TYPE).entrySet()) {
			String renamed = LegacyData.rename(LegacyRegistry.ENTITY_TYPE, "minecraft:" + entry.getValue());
			if (renamed == null) {
				continue;
			}
			Identifier egg = Identifier.tryParse(renamed + "_spawn_egg");
			if (egg != null && itemExists(egg)) {
				out.putIfAbsent(new LegacyId(id, entry.getKey()), LegacyIdTarget.of(egg));
			}
		}
	}

	private static boolean itemExists(Identifier identifier) {
		return LegacyTables.rawContains(Registries.ITEM, identifier);
	}

	static void generateFromNames(LegacyRegistry registry, Map<LegacyId, LegacyIdTarget> out) {
		for (Map.Entry<Integer, String> entry : LegacyData.registryNames(registry).entrySet()) {
			String renamed = LegacyData.rename(registry, "minecraft:" + entry.getValue());
			if (renamed == null) {
				continue;
			}
			Identifier identifier = Identifier.tryParse(renamed);
			if (identifier != null) {
				out.putIfAbsent(new LegacyId(entry.getKey(), 0), LegacyIdTarget.of(identifier));
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
