package cn.kynarain.legacyidaliases.impl;

import cn.kynarain.legacyidaliases.api.LegacyId;
import cn.kynarain.legacyidaliases.api.LegacyIdAliases;
import cn.kynarain.legacyidaliases.api.LegacyIdSource;
import cn.kynarain.legacyidaliases.api.LegacyIdTable;
import cn.kynarain.legacyidaliases.api.LegacyIdTarget;
import cn.kynarain.legacyidaliases.api.LegacyRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds, caches and injects the legacy tables. Not part of the public API.
 */
public final class LegacyTables {
	private static final Logger LOGGER = LoggerFactory.getLogger("Legacy ID Aliases");
	private static final Object LOCK = new Object();
	/** Legacy item ids end at 2267 (the last music disc), new items continue from here. */
	private static final int FIRST_ALLOCATED_ITEM_ID = 2268;

	private static final Map<LegacyRegistry, LegacyIdTable> TABLES = new EnumMap<>(LegacyRegistry.class);
	private static final Set<LegacyRegistry> BUILT = EnumSet.noneOf(LegacyRegistry.class);
	private static final Map<LegacyRegistry, Stats> STATS = new EnumMap<>(LegacyRegistry.class);
	private static final Map<LegacyRegistry, List<Identifier>> UNRESOLVED = new EnumMap<>(LegacyRegistry.class);
	private static final Map<Registry<?>, Boolean> SEEN = new WeakHashMap<>();
	private static final Set<Registry<?>> INJECTED = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final List<LegacyIdSource> SOURCES = new CopyOnWriteArrayList<>();

	private LegacyTables() {
	}

	public record Stats(int entries, int aliases, int unresolved) {
	}

	public static LegacyIdTable table(LegacyRegistry registry) {
		return table(registry, registry.registry());
	}

	public static LegacyIdTable table(LegacyRegistry registry, @Nullable Registry<?> live) {
		Objects.requireNonNull(registry, "registry");
		synchronized (LOCK) {
			if (!BUILT.contains(registry)) {
				TABLES.put(registry, build(registry, live));
				BUILT.add(registry);
			}
			return TABLES.get(registry);
		}
	}

	public static Map<LegacyRegistry, Stats> stats() {
		synchronized (LOCK) {
			return Map.copyOf(STATS);
		}
	}

	/**
	 * @return the live registry instance of a registry, including the dynamic ones that were seen
	 *         while freezing
	 */
	@Nullable
	public static Registry<?> live(LegacyRegistry type) {
		Registry<?> direct = type.registry();
		if (direct != null) {
			return direct;
		}
		synchronized (LOCK) {
			for (Registry<?> registry : SEEN.keySet()) {
				if (LegacyRegistry.byKey(registry.getKey()).filter(value -> value == type).isPresent()) {
					return registry;
				}
			}
		}
		return null;
	}

	public static void registerSource(LegacyIdSource source) {
		Objects.requireNonNull(source, "source");
		SOURCES.add(source);
		synchronized (LOCK) {
			if (!BUILT.isEmpty()) {
				rebuildLocked();
			}
		}
	}

	/**
	 * Re-reads the user mapping files and rebuilds every alias.
	 */
	public static void reload() {
		synchronized (LOCK) {
			LegacyData.reloadUser();
			rebuildLocked();
		}
	}

	/**
	 * Called from {@code SimpleRegistry#freeze()} before the registry becomes immutable.
	 */
	public static void injectAliases(Registry<?> registry) {
		if (!(registry instanceof LegacyAliasHolder holder)) {
			return;
		}
		Optional<LegacyRegistry> type = LegacyRegistry.byKey(registry.getKey());
		if (type.isEmpty()) {
			return;
		}
		synchronized (LOCK) {
			SEEN.put(registry, Boolean.TRUE);
			if (!INJECTED.add(registry)) {
				return;
			}
			LegacyIdTable table = table(type.get(), registry);
			Map<Identifier, RegistryEntry.Reference<?>> aliases = holder.legacyAliases();
			int added = 0;
			int unresolved = 0;
			for (Map.Entry<LegacyId, LegacyIdTarget> entry : table.entries().entrySet()) {
				RegistryEntry.Reference<?> aliasTarget = rawEntry(registry, entry.getValue().identifier());
				if (aliasTarget == null) {
					unresolved++;
					continue;
				}
				for (Identifier alias : LegacyIdAliases.aliasIds(type.get(), entry.getKey())) {
					if (holder.legacyRawContains(alias)) {
						continue;
					}
					if (aliases.putIfAbsent(alias, aliasTarget) == null) {
						added++;
					}
				}
			}
			STATS.put(type.get(), new Stats(table.size(), added, unresolved));
			LOGGER.info(
				"Legacy ids for {}: {} entries, {} aliases, {} unresolved",
				type.get().fullId(),
				table.size(),
				added,
				unresolved
			);
		}
	}

	/**
	 * Runs the injection on every registry that was seen so far; used after the tables change.
	 */
	public static void reinjectAll() {
		synchronized (LOCK) {
			rebuildLocked();
		}
	}

	private static void rebuildLocked() {
		List<Registry<?>> seen = new ArrayList<>(SEEN.keySet());
		for (Registry<?> registry : seen) {
			if (registry instanceof LegacyAliasHolder holder) {
				holder.legacyAliases().clear();
			}
		}
		TABLES.clear();
		BUILT.clear();
		INJECTED.clear();
		STATS.clear();
		UNRESOLVED.clear();
		for (Registry<?> registry : seen) {
			injectAliases(registry);
		}
	}

	public static boolean addAlias(Registry<?> registry, Identifier alias, Identifier target) {
		if (!(registry instanceof LegacyAliasHolder holder)) {
			return false;
		}
		RegistryEntry.Reference<?> entry = rawEntry(registry, target);
		if (entry == null || holder.legacyRawContains(alias)) {
			return false;
		}
		holder.legacyAliases().putIfAbsent(alias, entry);
		return true;
	}

	public static boolean removeAlias(Registry<?> registry, Identifier alias) {
		return registry instanceof LegacyAliasHolder holder && holder.legacyAliases().remove(alias) != null;
	}

	public static Optional<RegistryEntry.Reference<?>> rawEntryOf(Registry<?> registry, Identifier id) {
		return Optional.ofNullable(rawEntry(registry, id));
	}

	@Nullable
	static RegistryEntry.Reference<?> rawEntry(Registry<?> registry, Identifier id) {
		if (registry instanceof LegacyAliasHolder holder) {
			return holder.legacyRawEntry(id);
		}
		return registry.getEntry(id).orElse(null);
	}

	static boolean rawContains(Registry<?> registry, Identifier id) {
		if (registry instanceof LegacyAliasHolder holder) {
			return holder.legacyRawContains(id);
		}
		return registry.containsId(id);
	}

	static Set<Identifier> rawIds(Registry<?> registry) {
		if (registry instanceof LegacyAliasHolder holder) {
			return holder.legacyRawIds();
		}
		return registry.getIds();
	}

	private static LegacyIdTable build(LegacyRegistry registry, @Nullable Registry<?> live) {
		LegacyData.load();
		Map<LegacyId, LegacyIdTarget> collected = new LinkedHashMap<>();
		switch (registry) {
			case BLOCK -> LegacyVanillaData.generateBlocks(collected);
			case ITEM -> LegacyVanillaData.generateItems(collected);
			default -> LegacyVanillaData.generateFromNames(registry, collected);
		}
		collected.putAll(LegacyData.bundledTable(registry));
		for (LegacyIdSource source : SOURCES) {
			try {
				source.contribute(registry, collected::put);
			} catch (RuntimeException exception) {
				LOGGER.warn("Legacy id source {} failed for {}", source.getClass().getName(), registry.id(), exception);
			}
		}
		collected.putAll(LegacyData.userTable(registry));
		List<Identifier> unresolved = new ArrayList<>();
		if (live != null) {
			collected.values().removeIf(target -> {
				Identifier identifier = target.identifier();
				boolean missing = !rawContains(live, identifier);
				if (missing) {
					unresolved.add(identifier);
				}
				return missing;
			});
		}
		UNRESOLVED.put(registry, List.copyOf(unresolved));
		if (live != null) {
			allocateMissing(registry, live, collected);
		}
		LegacyIdTable table = LegacyIdTable.of(collected);
		LOGGER.debug("Built {} legacy ids for {}", table.size(), registry.fullId());
		return table;
	}

	/**
	 * @return the identifiers of the bundled/user mappings that the current registry does not know,
	 *         for example after a Minecraft update renamed or removed an entry
	 */
	public static List<Identifier> unresolved(LegacyRegistry registry) {
		synchronized (LOCK) {
			return UNRESOLVED.getOrDefault(registry, List.of());
		}
	}

	/**
	 * Assigns ids to entries that did not exist before the flattening.
	 *
	 * <p>Items (and the block items created with them) continue the old numbering in the order the
	 * entries were added to the game; the remaining registries continue in registry order, because
	 * no per-version dataset exists for them.
	 */
	private static void allocateMissing(LegacyRegistry registry, Registry<?> live, Map<LegacyId, LegacyIdTarget> collected) {
		Set<Identifier> targeted = new HashSet<>();
		for (LegacyIdTarget target : collected.values()) {
			targeted.add(target.identifier());
		}
		if (registry == LegacyRegistry.ITEM || registry == LegacyRegistry.BLOCK) {
			int nextId = Math.max(FIRST_ALLOCATED_ITEM_ID, maxId(collected) + 1);
			for (LegacyData.AddedItem added : LegacyData.additions()) {
				Identifier identifier = Identifier.tryParse("minecraft:" + added.name());
				if (identifier == null || targeted.contains(identifier) || !rawContains(live, identifier)) {
					continue;
				}
				collected.put(new LegacyId(nextId++, 0), LegacyIdTarget.of(identifier));
				targeted.add(identifier);
			}
			return;
		}
		int nextId = Math.max(maxId(collected), maxLegacyId(registry)) + 1;
		for (Identifier identifier : new ArrayList<>(rawIds(live))) {
			if (targeted.contains(identifier)) {
				continue;
			}
			collected.put(new LegacyId(nextId++, 0), LegacyIdTarget.of(identifier));
			targeted.add(identifier);
		}
	}

	private static int maxLegacyId(LegacyRegistry registry) {
		int max = -1;
		for (Integer id : LegacyData.registryNames(registry).keySet()) {
			max = Math.max(max, id);
		}
		return max;
	}

	private static int maxId(Map<LegacyId, LegacyIdTarget> collected) {
		int max = -1;
		for (LegacyId id : collected.keySet()) {
			max = Math.max(max, id.id());
		}
		return max;
	}
}
