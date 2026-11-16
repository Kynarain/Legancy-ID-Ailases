package cn.kynarain.legacyidaliases.impl;

import java.util.Map;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by {@code SimpleRegistry} through a mixin: gives the mod access to the raw id map and
 * to the alias map that is consulted by every identifier lookup of the registry.
 *
 * <p>Not part of the public API - use {@link cn.kynarain.legacyidaliases.api.LegacyIdAliases}.
 */
public interface LegacyAliasHolder {
	Map<Identifier, RegistryEntry.Reference<?>> legacyAliases();

	/**
	 * @return the entry registered under {@code id}, ignoring aliases
	 */
	@Nullable
	RegistryEntry.Reference<?> legacyRawEntry(Identifier id);

	/**
	 * @return {@code true} if a real (non-alias) entry is registered under {@code id}
	 */
	boolean legacyRawContains(Identifier id);

	/**
	 * @return the ids of the real (non-alias) entries of this registry
	 */
	java.util.Set<Identifier> legacyRawIds();
}
