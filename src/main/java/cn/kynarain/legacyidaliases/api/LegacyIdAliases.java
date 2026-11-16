package cn.kynarain.legacyidaliases.api;

import cn.kynarain.legacyidaliases.impl.LegacyAliasHolder;
import cn.kynarain.legacyidaliases.impl.LegacyTables;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Public entry point of Legacy ID Aliases.
 *
 * <p>The mod turns legacy numeric ids (the pre-1.13 {@code id} plus damage value) into aliases of
 * the flattened identifiers. Two things are exposed:
 *
 * <ul>
 *   <li>{@link #table(LegacyRegistry)} / {@link #resolve} - the id mapping tables, usable without
 *       touching commands;</li>
 *   <li>{@link #addAlias(Registry, Identifier, Identifier)} - real registry aliases, resolved by
 *       every identifier lookup in a registry that supports them.</li>
 * </ul>
 */
public final class LegacyIdAliases {
	public static final String MOD_ID = "legacy_id_aliases";

	private LegacyIdAliases() {
	}

	/**
	 * @return the (cached) mapping table of the given registry
	 */
	public static LegacyIdTable table(LegacyRegistry registry) {
		return LegacyTables.table(registry);
	}

	/**
	 * Resolves a legacy id of the given registry.
	 *
	 * <p>The returned identifier is the vanilla one; it is not guaranteed to exist in the current
	 * registry (a mapping can be stale after a Minecraft update), use
	 * {@link #resolveEntry(LegacyRegistry, LegacyId)} if an existing entry is required.
	 */
	public static Optional<Identifier> resolve(LegacyRegistry registry, LegacyId id) {
		return registry.supportsMeta()
			? table(registry).lookupWithFallback(id.id(), id.meta())
			: table(registry).lookup(id.id(), 0);
	}

	public static Optional<Identifier> resolveItem(int id, int meta) {
		return resolve(LegacyRegistry.ITEM, new LegacyId(id, meta));
	}

	public static Optional<Identifier> resolveBlock(int id, int meta) {
		return resolve(LegacyRegistry.BLOCK, new LegacyId(id, meta));
	}

	/**
	 * Resolves a legacy id and asks the live registry for the matching entry.
	 */
	public static Optional<RegistryEntry.Reference<?>> resolveEntry(LegacyRegistry registry, LegacyId id) {
		Registry<?> live = LegacyTables.live(registry);
		Optional<Identifier> resolved = resolve(registry, id);
		if (resolved.isEmpty() || live == null) {
			return Optional.empty();
		}
		return LegacyTables.rawEntryOf(live, resolved.get());
	}

	/**
	 * Registers an extra id source. Call during mod initialization so the source participates in
	 * the tables built when the registries are frozen.
	 */
	public static void registerSource(LegacyIdSource source) {
		LegacyTables.registerSource(source);
	}

	/**
	 * Adds a real registry alias. Aliases are visible to every identifier/registry-key lookup of
	 * that registry, including vanilla command arguments, but never shadow a registered id.
	 *
	 * @return {@code true} if the alias was added
	 */
	public static boolean addAlias(Registry<?> registry, Identifier alias, Identifier target) {
		return LegacyTables.addAlias(registry, alias, target);
	}

	/**
	 * Removes an alias added by {@link #addAlias(Registry, Identifier, Identifier)}.
	 */
	public static boolean removeAlias(Registry<?> registry, Identifier alias) {
		return LegacyTables.removeAlias(registry, alias);
	}

	/**
	 * @return the alias identifier of a legacy id as registered in the target registry
	 */
	@Nullable
	public static Identifier aliasId(LegacyRegistry registry, LegacyId id) {
		List<Identifier> aliases = aliasIds(registry, id);
		return aliases.isEmpty() ? null : aliases.get(0);
	}

	/**
	 * Computes every alias identifier of a legacy id.
	 *
	 * <p>For items and blocks the legacy {@code id:meta} form is used as the {@code namespace:path}
	 * split ({@code 35:14}), plus {@code minecraft:<id>} for {@code meta == 0} and
	 * {@code minecraft:<id>_<meta>} for variants. Other registries use {@code minecraft:<id>}.
	 */
	public static List<Identifier> aliasIds(LegacyRegistry registry, LegacyId id) {
		List<Identifier> aliases = new ArrayList<>(3);
		String numericId = Integer.toString(id.id());
		if (!registry.supportsMeta()) {
			aliases.add(Identifier.of("minecraft", numericId));
			return aliases;
		}
		if (id.meta() == 0) {
			aliases.add(Identifier.of("minecraft", numericId));
			aliases.add(Identifier.of(numericId, "0"));
			return aliases;
		}
		aliases.add(Identifier.of(numericId, Integer.toString(id.meta())));
		aliases.add(Identifier.of("minecraft", numericId + "_" + id.meta()));
		return aliases;
	}

	/**
	 * @return {@code true} if the given registry supports aliases on this Minecraft version
	 */
	public static boolean supportsAliases(Registry<?> registry) {
		return registry instanceof LegacyAliasHolder;
	}
}
