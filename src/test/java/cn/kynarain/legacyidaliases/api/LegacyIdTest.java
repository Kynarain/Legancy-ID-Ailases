package cn.kynarain.legacyidaliases.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LegacyIdTest {
	@Test
	void parsesIdWithoutMeta() {
		LegacyId id = LegacyId.parse("35");
		assertEquals(35, id.id());
		assertEquals(0, id.meta());
		assertEquals("35", id.toString());
	}

	@Test
	void parsesIdWithMeta() {
		LegacyId id = LegacyId.parse("35:14");
		assertEquals(35, id.id());
		assertEquals(14, id.meta());
		assertEquals("35:14", id.toString());
	}

	@Test
	void parsesWhitespace() {
		assertEquals(new LegacyId(276, 0), LegacyId.parse(" 276 "));
	}

	@Test
	void rejectsNonNumeric() {
		assertThrows(IllegalArgumentException.class, () -> LegacyId.parse("minecraft:stone"));
		assertThrows(IllegalArgumentException.class, () -> LegacyId.parse("35:"));
		assertThrows(IllegalArgumentException.class, () -> LegacyId.parse(""));
	}

	@Test
	void rejectsNegative() {
		assertThrows(IllegalArgumentException.class, () -> new LegacyId(-1, 0));
		assertThrows(IllegalArgumentException.class, () -> new LegacyId(1, -1));
	}

	@Test
	void sortsByIdThenMeta() {
		LegacyId stone = LegacyId.of(1, 0);
		LegacyId granite = LegacyId.of(1, 1);
		LegacyId diamond = LegacyId.of(264, 0);
		org.junit.jupiter.api.Assertions.assertTrue(stone.compareTo(granite) < 0);
		org.junit.jupiter.api.Assertions.assertTrue(granite.compareTo(diamond) < 0);
	}
}
