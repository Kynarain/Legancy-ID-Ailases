package cn.kynarain.legacyidaliases;

import cn.kynarain.legacyidaliases.api.LegacyId;
import cn.kynarain.legacyidaliases.api.LegacyIdAliases;
import cn.kynarain.legacyidaliases.api.LegacyIdTable;
import cn.kynarain.legacyidaliases.api.LegacyIdTarget;
import cn.kynarain.legacyidaliases.api.LegacyRegistry;
import cn.kynarain.legacyidaliases.impl.LegacyTables;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Development helper: validates the generated tables and the legacy {@code /give} syntax on a real
 * server. Enabled with {@code -Dlegacy_id_aliases.selftest=true}; it stops the server afterwards.
 */
public final class LegacyIdAliasesSelfTest {
	private static final String PROPERTY = "legacy_id_aliases.selftest";

	private LegacyIdAliasesSelfTest() {
	}

	public static void install() {
		if (!Boolean.getBoolean(PROPERTY)) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(LegacyIdAliasesSelfTest::run);
	}

	private static void run(MinecraftServer server) {
		List<String> report = new ArrayList<>();
		int failures = 0;
		for (LegacyRegistry registry : LegacyRegistry.values()) {
			LegacyIdTable table = LegacyIdAliases.table(registry);
			LegacyTables.Stats stats = LegacyTables.stats().get(registry);
			report.add(
				String.format(
					"%-14s entries=%-5d aliases=%-5d unresolved=%s",
					registry.id(),
					table.size(),
					stats == null ? -1 : stats.aliases(),
					stats == null ? "n/a" : Integer.toString(stats.unresolved())
				)
			);
		}

		failures += expect(server, report, "give @s 35:14", "minecraft:red_wool");
		failures += expect(server, report, "give @s 1:1", "minecraft:granite");
		failures += expect(server, report, "give @s 5:2", "minecraft:birch_planks");
		failures += expect(server, report, "give @s 17:1", "minecraft:spruce_log");
		failures += expect(server, report, "give @s 95:14", "minecraft:red_stained_glass");
		failures += expect(server, report, "give @s 276", "minecraft:diamond_sword");
		failures += expect(server, report, "give @s minecraft:35", "minecraft:white_wool");
		failures += expect(server, report, "give @s minecraft:35_14", "minecraft:red_wool");
		failures += expect(server, report, "give @s 383:50", "minecraft:creeper_spawn_egg");
		failures += expect(server, report, "give @s 397:3", "minecraft:player_head");
		failures += expect(server, report, "give @s 351:1", "minecraft:red_dye");
		failures += expect(server, report, "give @s 2267", "minecraft:music_disc_wait");
		failures += expect(server, report, "give @s 2268", null);
		failures += expect(server, report, "give @s minecraft:stone", "minecraft:stone");
		// Potions: the variant lives in a data component, not in the item id. A potion without
		// contents is a water bottle, so 373 and 373:0 carry no component while 373:5 does.
		failures += expect(server, report, "give @s 373", "minecraft:potion", null);
		failures += expect(server, report, "give @s 373:0", "minecraft:potion", null);
		failures += expect(server, report, "give @s 373:5", "minecraft:potion", "minecraft:healing");
		failures += expect(server, report, "give @s 373:16389", "minecraft:splash_potion", "minecraft:healing");
		failures += expect(server, report, "give @s 438:5", "minecraft:splash_potion", "minecraft:healing");
		failures += expect(server, report, "give @s 441:34", "minecraft:lingering_potion", "minecraft:strong_swiftness");
		failures += expect(server, report, "give @s 373:66", "minecraft:potion", "minecraft:long_swiftness");
		failures += expect(server, report, "give @s 373:5 3", "minecraft:potion", "minecraft:healing");
		failures += expect(server, report, "give @s 373:5[minecraft:custom_model_data=7]", "minecraft:potion", "minecraft:healing");
		// The registry aliases also cover the vanilla commands that parse identifiers themselves.
		failures += expectParse(server, report, "clear @s 35:14");
		failures += expectParse(server, report, "item replace entity @s weapon.mainhand with 35:14");
		failures += expectParse(server, report, "setblock 0 0 0 minecraft:35");

		// Public API: runtime mappings, stats and unresolved diagnostics.
		LegacyId runtimePlain = new LegacyId(9900, 3);
		LegacyId runtimePotion = LegacyId.of(9901);
		LegacyIdAliases.addMapping(
			LegacyRegistry.ITEM,
			runtimePlain,
			LegacyIdTarget.of(Identifier.of("minecraft", "diamond"))
		);
		LegacyIdAliases.addMapping(
			LegacyRegistry.ITEM,
			runtimePotion,
			LegacyIdTarget.ofPotion(Identifier.of("minecraft", "potion"), Identifier.of("minecraft", "swiftness"))
		);
		failures += expect(server, report, "give @s 9900:3", "minecraft:diamond");
		failures += expect(server, report, "give @s 9901", "minecraft:potion", "minecraft:swiftness");
		report.add(
			"runtime mapping: " + LegacyIdAliases.resolveItem(9900, 3).orElse(null)
				+ ", alias=" + LegacyIdAliases.aliasId(LegacyRegistry.ITEM, runtimePlain)
				+ ", removed=" + LegacyIdAliases.removeMapping(LegacyRegistry.ITEM, runtimePlain)
		);
		report.add("stats(item)=" + LegacyIdAliases.stats().get(LegacyRegistry.ITEM));
		report.add("unresolved(item)=" + LegacyIdAliases.unresolved(LegacyRegistry.ITEM));

		LegacyId sample = new LegacyId(35, 14);
		report.add(
			"resolve(35:14)   = "
				+ LegacyIdAliases.resolveItem(35, 14).orElse(null)
				+ "  alias="
				+ LegacyIdAliases.aliasId(LegacyRegistry.ITEM, sample)
		);
		report.add("item registry aliases: " + (LegacyIdAliases.supportsAliases(Registries.ITEM) ? "supported" : "missing"));
		for (LegacyRegistry registry : LegacyRegistry.values()) {
			List<Identifier> unresolved = LegacyTables.unresolved(registry);
			if (!unresolved.isEmpty()) {
				java.util.LinkedHashSet<Identifier> unique = new java.util.LinkedHashSet<>(unresolved);
				StringBuilder names = new StringBuilder();
				for (Identifier identifier : unique) {
					if (names.length() > 0) {
						names.append(", ");
					}
					names.append(identifier.getPath());
				}
				report.add(
					registry.id() + " unresolved (" + unresolved.size() + " entries, " + unique.size() + " names): " + names
				);
			}
		}

		StringBuilder message = new StringBuilder("\n===== Legacy ID Aliases self test =====\n");
		for (String line : report) {
			message.append("  ").append(line).append('\n');
		}
		message.append("  failures=").append(failures).append('\n');
		message.append("===== end self test =====");
		LegacyIdAliasesMod.LOGGER.info(message.toString());

		server.stop(false);
	}

	/**
	 * @param expected the expected item id, or {@code null} to only require a successful parse
	 */
	private static int expectParse(MinecraftServer server, List<String> report, String command) {
		try {
			ParseResults<ServerCommandSource> results = server.getCommandManager()
				.getDispatcher()
				.parse(command, server.getCommandSource());
			if (!results.getExceptions().isEmpty()) {
				report.add("FAIL " + command + " -> " + results.getExceptions().values().iterator().next().getMessage());
				return 1;
			}
			report.add("ok   " + command);
			return 0;
		} catch (RuntimeException exception) {
			report.add("FAIL " + command + " -> " + exception);
			return 1;
		}
	}

	private static int expect(MinecraftServer server, List<String> report, String command, String expected) {
		return expect(server, report, command, expected, null);
	}

	/**
	 * @param expected the expected item id, or {@code null} to only require a successful parse;
	 *                 {@code expectedPotion} additionally checks the potion contents component
	 */
	private static int expect(
		MinecraftServer server,
		List<String> report,
		String command,
		@Nullable String expected,
		@Nullable String expectedPotion
	) {
		ParseResults<ServerCommandSource> results;
		try {
			results = server.getCommandManager().getDispatcher().parse(command, server.getCommandSource());
		} catch (RuntimeException exception) {
			report.add("FAIL " + command + " -> " + exception);
			return 1;
		}
		if (!results.getExceptions().isEmpty()) {
			report.add("FAIL " + command + " -> " + results.getExceptions().values().iterator().next().getMessage());
			return 1;
		}
		CommandContextBuilder<ServerCommandSource> context = results.getContext();
		Map<String, ParsedArgument<ServerCommandSource, ?>> arguments = context.getArguments();
		ParsedArgument<ServerCommandSource, ?> item = arguments.get("item");
		if (item == null) {
			report.add("FAIL " + command + " -> no item argument");
			return 1;
		}
		if (!(item.getResult() instanceof ItemStackArgument stackArgument)) {
			report.add("FAIL " + command + " -> unexpected argument " + item.getResult());
			return 1;
		}
		ItemStack stack;
		try {
			stack = stackArgument.createStack(1, false);
		} catch (CommandSyntaxException exception) {
			report.add("FAIL " + command + " -> " + exception.getMessage());
			return 1;
		}
		Identifier id = Registries.ITEM.getId(stack.getItem());
		PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
		String potion = contents == null
			? null
			: contents.potion().map(entry -> Registries.POTION.getId(entry.value()).toString()).orElse(null);
		boolean nameSet = stack.get(DataComponentTypes.CUSTOM_NAME) != null;
		String detail = " -> " + id + (potion == null ? "" : " / " + potion) + (nameSet ? " / custom_name" : "");
		if (expected == null && expectedPotion == null) {
			report.add("ok   " + command + detail);
			return 0;
		}
		boolean matches = (expected == null || expected.equals(id.toString()))
			&& (expectedPotion == null || expectedPotion.equals(potion));
		report.add((matches ? "ok   " : "FAIL ") + command + detail + (matches ? "" : " (unexpected)"));
		return matches ? 0 : 1;
	}
}
