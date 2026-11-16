package cn.kynarain.legacyidaliases.command;

import cn.kynarain.legacyidaliases.api.LegacyId;
import cn.kynarain.legacyidaliases.api.LegacyIdAliases;
import cn.kynarain.legacyidaliases.api.LegacyIdTable;
import cn.kynarain.legacyidaliases.api.LegacyRegistry;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * {@link ItemStackArgument} argument that additionally accepts the legacy numeric syntax, so
 * {@code /give @s 35:14} hands out red wool.
 *
 * <p>Accepted forms:
 *
 * <ul>
 *   <li>{@code 35} - legacy id 35, damage 0</li>
 *   <li>{@code 35:14} - legacy id 35, damage 14</li>
 *   <li>{@code minecraft:35} or {@code minecraft:35_14} - the alias identifiers of the registry</li>
 *   <li>anything else - parsed by the vanilla item argument (identifiers, data components, ...)</li>
 * </ul>
 */
public final class LegacyItemStackArgumentType implements ArgumentType<ItemStackArgument> {
	public static final Identifier TYPE_ID = Identifier.of(LegacyIdAliases.MOD_ID, "legacy_item_stack");
	static final DynamicCommandExceptionType UNKNOWN_LEGACY_ID = new DynamicCommandExceptionType(
		id -> Text.translatable("argument." + LegacyIdAliases.MOD_ID + ".unknown_legacy_id", id)
	);
	private static final Collection<String> EXAMPLES = List.of(
		"35:14",
		"minecraft:35",
		"1",
		"276",
		"minecraft:stone"
	);

	private final ItemStackArgumentType delegate;

	public LegacyItemStackArgumentType(CommandRegistryAccess registryAccess) {
		this(ItemStackArgumentType.itemStack(registryAccess));
	}

	public LegacyItemStackArgumentType(ItemStackArgumentType delegate) {
		this.delegate = delegate;
	}

	public static LegacyItemStackArgumentType itemStack(CommandRegistryAccess registryAccess) {
		return new LegacyItemStackArgumentType(registryAccess);
	}

	@Override
	public ItemStackArgument parse(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();
		LegacyId legacyId = readLegacyId(reader);
		if (legacyId == null) {
			reader.setCursor(start);
			return this.delegate.parse(reader);
		}
		int tokenEnd = reader.getCursor();
		Optional<Identifier> resolved = LegacyIdAliases.resolveItem(legacyId.id(), legacyId.meta());
		if (resolved.isEmpty() || !Registries.ITEM.containsId(resolved.get())) {
			reader.setCursor(start);
			throw UNKNOWN_LEGACY_ID.createWithContext(reader, legacyId.toString());
		}
		// Let the vanilla argument parse the resolved identifier together with the trailing data
		// components, so "35:14" and "35:14[minecraft:custom_name='x']" both work.
		String replacement = resolved.get().toString();
		StringReader rewritten = new StringReader(replacement + reader.getRemaining());
		ItemStackArgument argument = this.delegate.parse(rewritten);
		int consumedAfterToken = rewritten.getCursor() - replacement.length();
		reader.setCursor(tokenEnd + consumedAfterToken);
		return argument;
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		String remaining = builder.getRemaining();
		if (isLegacyPrefix(remaining)) {
			suggestLegacyIds(remaining, builder);
		}
		return this.delegate.listSuggestions(context, builder);
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}


	private static boolean isLegacyPrefix(String remaining) {
		return remaining.matches("[0-9]{1,5}(:[0-9]{0,3})?");
	}

	private static void suggestLegacyIds(String remaining, SuggestionsBuilder builder) {
		LegacyIdTable table = LegacyIdAliases.table(LegacyRegistry.ITEM);
		if (table.isEmpty()) {
			return;
		}
		int separator = remaining.indexOf(':');
		if (separator < 0) {
			int shown = 0;
			for (Integer id : table.ids()) {
				String candidate = Integer.toString(id);
				if (candidate.startsWith(remaining)) {
					builder.suggest(candidate);
					if (++shown >= 64) {
						break;
					}
				}
			}
			return;
		}
		int id;
		try {
			id = Integer.parseInt(remaining.substring(0, separator));
		} catch (NumberFormatException exception) {
			return;
		}
		String prefix = Integer.toString(id) + ":";
		int shown = 0;
		for (Map.Entry<LegacyId, Identifier> entry : table.entries().entrySet()) {
			if (entry.getKey().id() != id) {
				continue;
			}
			String candidate = prefix + entry.getKey().meta();
			if (candidate.startsWith(remaining) && !candidate.equals(remaining)) {
				builder.suggest(candidate);
				if (++shown >= 64) {
					break;
				}
			}
		}
	}

	/**
	 * Reads {@code <id>} or {@code <id>:<meta>} and rewinds the reader when the token is not a
	 * legacy id.
	 */
	private static LegacyId readLegacyId(StringReader reader) {
		int start = reader.getCursor();
		try {
			if (!reader.canRead() || !isDigit(reader.peek())) {
				return null;
			}
			int id = reader.readInt();
			int meta = 0;
			if (reader.canRead() && reader.peek() == ':') {
				int afterSeparator = reader.getCursor() + 1;
				if (afterSeparator < reader.getTotalLength() && isDigit(reader.getString().charAt(afterSeparator))) {
					reader.skip();
					meta = reader.readInt();
				}
			}
			if (reader.canRead() && reader.peek() == ':') {
				reader.setCursor(start);
				return null;
			}
			return new LegacyId(id, meta);
		} catch (CommandSyntaxException | IllegalArgumentException exception) {
			reader.setCursor(start);
			return null;
		}
	}

	private static boolean isDigit(char character) {
		return character >= '0' && character <= '9';
	}


}
