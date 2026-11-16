package cn.kynarain.legacyidaliases.api;

import java.util.function.BiConsumer;

/**
 * A contributor of additional legacy ids, typically implemented by other mods.
 *
 * <p>Implementations must be registered during mod initialization, before the vanilla registries
 * are frozen, and must be side-effect free: they are called once per registry while the table is
 * built.
 */
@FunctionalInterface
public interface LegacyIdSource {
	/**
	 * @param registry the registry whose table is being built
	 * @param sink     accepts {@code legacy id -> modern identifier} pairs
	 */
	void contribute(LegacyRegistry registry, BiConsumer<LegacyId, net.minecraft.util.Identifier> sink);
}
