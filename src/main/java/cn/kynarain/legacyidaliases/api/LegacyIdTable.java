package cn.kynarain.legacyidaliases.api;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.util.Identifier;

/**
 * An immutable legacy id to modern identifier table of one registry.
 *
 * <p>Tables are sorted by {@link LegacyId} so iteration is stable between runs.
 */
public final class LegacyIdTable {
	public static final LegacyIdTable EMPTY = new LegacyIdTable(Collections.emptyNavigableMap());

	private final NavigableMap<LegacyId, Identifier> entries;

	private LegacyIdTable(NavigableMap<LegacyId, Identifier> entries) {
		this.entries = entries;
	}

	/**
	 * Copies and sorts the given mappings into a new table.
	 */
	public static LegacyIdTable of(Map<LegacyId, Identifier> entries) {
		return new LegacyIdTable(Collections.unmodifiableNavigableMap(new TreeMap<>(entries)));
	}

	public Optional<Identifier> lookup(LegacyId id) {
		return Optional.ofNullable(this.entries.get(id));
	}

	public Optional<Identifier> lookup(int id, int meta) {
		return this.lookup(new LegacyId(id, meta));
	}

	/**
	 * Looks up {@code id:meta} and falls back to {@code id:0} for ids without a variant table, so
	 * {@code /give @s 1:7} still resolves to stone instead of failing.
	 */
	public Optional<Identifier> lookupWithFallback(int id, int meta) {
		Optional<Identifier> direct = this.lookup(id, meta);
		if (direct.isPresent() || meta == 0) {
			return direct;
		}
		return this.lookup(id, 0);
	}

	public Map<LegacyId, Identifier> entries() {
		return this.entries;
	}

	public Set<Integer> ids() {
		Set<Integer> ids = new TreeSet<>();
		for (LegacyId id : this.entries.keySet()) {
			ids.add(id.id());
		}
		return Collections.unmodifiableSet(ids);
	}

	public int size() {
		return this.entries.size();
	}

	public boolean isEmpty() {
		return this.entries.isEmpty();
	}
}
