package cn.kynarain.legacyidaliases.api;

import java.util.Objects;

/**
 * A legacy (pre-flattening) identifier: the numeric registry id plus the damage/metadata value
 * that used to select a variant of the same item or block.
 *
 * <p>Examples: {@code 1} (stone), {@code 35:14} (red wool), {@code 276} (diamond sword).
 */
public record LegacyId(int id, int meta) implements Comparable<LegacyId> {
	public static final int MAX_META = 0xFFFF;

	public LegacyId {
		if (id < 0) {
			throw new IllegalArgumentException("Legacy id must not be negative: " + id);
		}
		if (meta < 0 || meta > MAX_META) {
			throw new IllegalArgumentException("Legacy meta out of range: " + meta);
		}
	}

	public static LegacyId of(int id) {
		return new LegacyId(id, 0);
	}

	public static LegacyId of(int id, int meta) {
		return new LegacyId(id, meta);
	}

	/**
	 * Parses {@code "<id>"} or {@code "<id>:<meta>"}.
	 *
	 * @throws IllegalArgumentException if the text is not a legacy id
	 */
	public static LegacyId parse(String text) {
		String trimmed = Objects.requireNonNull(text, "text").trim();
		int separator = trimmed.indexOf(':');
		try {
			if (separator < 0) {
				return new LegacyId(Integer.parseInt(trimmed), 0);
			}
			return new LegacyId(
				Integer.parseInt(trimmed.substring(0, separator)),
				Integer.parseInt(trimmed.substring(separator + 1))
			);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Not a legacy id: " + text, exception);
		}
	}

	public boolean hasMeta() {
		return this.meta != 0;
	}

	@Override
	public String toString() {
		return this.meta == 0 ? Integer.toString(this.id) : this.id + ":" + this.meta;
	}

	@Override
	public int compareTo(LegacyId other) {
		int byId = Integer.compare(this.id, other.id);
		return byId != 0 ? byId : Integer.compare(this.meta, other.meta);
	}
}
