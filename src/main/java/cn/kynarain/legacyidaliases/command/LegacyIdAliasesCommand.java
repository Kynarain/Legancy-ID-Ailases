package cn.kynarain.legacyidaliases.command;

import cn.kynarain.legacyidaliases.api.LegacyId;
import cn.kynarain.legacyidaliases.api.LegacyIdAliases;
import cn.kynarain.legacyidaliases.api.LegacyIdTable;
import cn.kynarain.legacyidaliases.api.LegacyRegistry;
import cn.kynarain.legacyidaliases.impl.LegacyTables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Optional;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * {@code /legacyids info|lookup|reload} for server operators.
 */
public final class LegacyIdAliasesCommand {
	private static final int PERMISSION_LEVEL = 2;

	private LegacyIdAliasesCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(
			CommandManager.literal("legacyids")
				.requires(source -> source.hasPermissionLevel(PERMISSION_LEVEL))
				.executes(context -> info(context.getSource()))
				.then(CommandManager.literal("info").executes(context -> info(context.getSource())))
				.then(
					CommandManager.literal("reload")
						.executes(context -> {
							LegacyTables.reload();
							context.getSource()
								.sendFeedback(
									() -> Text.literal("Legacy ID Aliases: mappings reloaded, aliases rebuilt."),
									true
								);
							return 1;
						})
				)
				.then(
					CommandManager.literal("lookup")
						.then(
							CommandManager.argument("registry", StringArgumentType.word())
								.suggests((context, builder) -> {
									for (LegacyRegistry registry : LegacyRegistry.values()) {
										builder.suggest(registry.id());
									}
									return builder.buildFuture();
								})
								.then(
									CommandManager.argument("id", StringArgumentType.word())
										.executes(context -> lookup(
											context.getSource(),
											StringArgumentType.getString(context, "registry"),
											StringArgumentType.getString(context, "id")
										))
								)
						)
				)
		);
	}

	private static int info(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal("Legacy ID Aliases - mapping tables:"), false);
		for (LegacyRegistry registry : LegacyRegistry.values()) {
			LegacyIdTable table = LegacyIdAliases.table(registry);
			LegacyTables.Stats stats = LegacyTables.stats().get(registry);
			String detail = stats == null
				? ""
				: " (" + stats.aliases() + " aliases, " + stats.unresolved() + " unresolved)";
			source.sendFeedback(
				() -> Text.literal("  " + registry.fullId() + ": " + table.size() + " entries" + detail),
				false
			);
		}
		return 1;
	}

	private static int lookup(ServerCommandSource source, String registryName, String idText) {
		LegacyRegistry registry = LegacyRegistry.byId(registryName).orElse(null);
		if (registry == null) {
			source.sendError(Text.literal("Unknown registry '" + registryName + "'"));
			return 0;
		}
		LegacyId legacyId;
		try {
			legacyId = LegacyId.parse(idText);
		} catch (IllegalArgumentException exception) {
			source.sendError(Text.literal("Not a legacy id: '" + idText + "'"));
			return 0;
		}
		Optional<Identifier> resolved = LegacyIdAliases.resolve(registry, legacyId);
		if (resolved.isEmpty()) {
			source.sendError(Text.literal(registry.id() + " " + legacyId + " is not mapped"));
			return 0;
		}
		Registry<?> live = LegacyTables.live(registry);
		boolean present = live != null && live.containsId(resolved.get());
		String suffix = present ? "" : " (missing in the current registry)";
		String alias = String.valueOf(LegacyIdAliases.aliasId(registry, legacyId));
		source.sendFeedback(
			() -> Text.literal(
				registry.id() + " " + legacyId + " -> " + resolved.get() + " / alias " + alias + suffix
			),
			false
		);
		return 1;
	}

}
