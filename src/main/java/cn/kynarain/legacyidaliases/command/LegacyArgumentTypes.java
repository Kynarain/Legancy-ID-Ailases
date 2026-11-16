package cn.kynarain.legacyidaliases.command;

import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer;

/**
 * Registers the legacy argument types so clients with the mod can parse them locally (and so that
 * {@code /give}'s command tree stays valid for them).
 */
public final class LegacyArgumentTypes {
	private LegacyArgumentTypes() {
	}

	public static void register() {
		ArgumentTypeRegistry.registerArgumentType(
			LegacyItemStackArgumentType.TYPE_ID,
			LegacyItemStackArgumentType.class,
			ConstantArgumentSerializer.of(LegacyItemStackArgumentType::itemStack)
		);
	}
}
