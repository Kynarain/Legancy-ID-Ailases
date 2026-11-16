package cn.kynarain.legacyidaliases.api;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

/**
 * The vanilla registries that accept legacy numeric aliases.
 *
 * <p>Every constant carries the JSON name used by the mapping files, which is always the registry
 * id without its namespace (for example {@code item} for {@code minecraft:item}).
 */
public enum LegacyRegistry {
	BLOCK("block", RegistryKeys.BLOCK),
	ITEM("item", RegistryKeys.ITEM),
	ENTITY_TYPE("entity_type", RegistryKeys.ENTITY_TYPE),
	BIOME("biome", RegistryKeys.BIOME),
	MOB_EFFECT("mob_effect", RegistryKeys.STATUS_EFFECT),
	ENCHANTMENT("enchantment", RegistryKeys.ENCHANTMENT),
	PARTICLE_TYPE("particle_type", RegistryKeys.PARTICLE_TYPE);

	private final String id;
	private final RegistryKey<? extends Registry<?>> key;

	LegacyRegistry(String id, RegistryKey<? extends Registry<?>> key) {
		this.id = id;
		this.key = key;
	}

	/**
	 * @return the registry id without namespace, for example {@code item}
	 */
	public String id() {
		return this.id;
	}

	/**
	 * @return the full registry id, for example {@code minecraft:item}
	 */
	public String fullId() {
		return "minecraft:" + this.id;
	}

	public RegistryKey<? extends Registry<?>> key() {
		return this.key;
	}

	/**
	 * @return {@code true} if legacy ids of this registry use the damage/metadata suffix
	 */
	public boolean supportsMeta() {
		return this == ITEM || this == BLOCK;
	}

	/**
	 * @return the live registry instance for the static registries, {@code null} for the dynamic
	 *         ones (biomes and enchantments are loaded with the server data)
	 */
	public Registry<?> registry() {
		try {
			return switch (this) {
				case BLOCK -> Registries.BLOCK;
				case ITEM -> Registries.ITEM;
				case ENTITY_TYPE -> Registries.ENTITY_TYPE;
				case MOB_EFFECT -> Registries.STATUS_EFFECT;
				case PARTICLE_TYPE -> Registries.PARTICLE_TYPE;
				case BIOME, ENCHANTMENT -> null;
			};
		} catch (RuntimeException exception) {
			return null;
		}
	}

	public static Optional<LegacyRegistry> byId(String id) {
		if (id == null) {
			return Optional.empty();
		}
		String normalised = id.trim().toLowerCase(Locale.ROOT);
		int separator = normalised.indexOf(':');
		if (separator >= 0) {
			normalised = normalised.substring(separator + 1);
		}
		for (LegacyRegistry value : values()) {
			if (value.id.equals(normalised)) {
				return Optional.of(value);
			}
		}
		return Optional.empty();
	}

	public static Optional<LegacyRegistry> byKey(RegistryKey<?> key) {
		for (LegacyRegistry value : values()) {
			if (value.key.equals(key)) {
				return Optional.of(value);
			}
		}
		return Optional.empty();
	}
}
