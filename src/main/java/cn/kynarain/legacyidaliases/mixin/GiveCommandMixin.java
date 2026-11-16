package cn.kynarain.legacyidaliases.mixin;

import cn.kynarain.legacyidaliases.command.LegacyItemStackArgumentType;
import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.GiveCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Swaps the {@code item} argument of {@code /give} for the legacy aware argument type, so
 * {@code /give @s 35:14} works.
 *
 * <p>The argument still produces a vanilla {@link net.minecraft.command.argument.ItemStackArgument}
 * and still accepts identifiers + data components, so nothing else about {@code /give} changes.
 *
 * <p>The command tree packet only carries the argument type of the server, therefore clients
 * without this mod silently lose the item argument node but can still send the command string -
 * {@code ClientPlayNetworkHandler#sendChatCommand} does not validate locally. Clients with the mod
 * additionally get suggestions and client side parsing.
 */
@Mixin(GiveCommand.class)
public class GiveCommandMixin {
	@ModifyArg(
		method = "register",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/command/CommandManager;argument(Ljava/lang/String;Lcom/mojang/brigadier/arguments/ArgumentType;)Lcom/mojang/brigadier/builder/RequiredArgumentBuilder;"
		),
		index = 1
	)
	private static ArgumentType<?> legacyIdAliases$useLegacyItemArgument(ArgumentType<?> original) {
		if (original instanceof ItemStackArgumentType itemStackArgumentType) {
			return new LegacyItemStackArgumentType(itemStackArgumentType);
		}
		return original;
	}
}
