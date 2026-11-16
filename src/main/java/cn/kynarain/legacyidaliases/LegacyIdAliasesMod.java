package cn.kynarain.legacyidaliases;

import cn.kynarain.legacyidaliases.command.LegacyArgumentTypes;
import cn.kynarain.legacyidaliases.command.LegacyIdAliasesCommand;
import cn.kynarain.legacyidaliases.impl.LegacyData;
import cn.kynarain.legacyidaliases.impl.LegacyTables;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entrypoint.
 *
 * <p>Startup order does not matter: the mapping tables are loaded lazily, and the registries ask for
 * their aliases from {@code SimpleRegistry#freeze()}.
 */
public final class LegacyIdAliasesMod implements ModInitializer {
	public static final String MOD_ID = "legacy_id_aliases";
	public static final Logger LOGGER = LoggerFactory.getLogger("Legacy ID Aliases");

	@Override
	public void onInitialize() {
		LegacyData.load();
		LegacyArgumentTypes.register();
		LegacyIdAliasesCommand.register();
		LegacyIdAliasesSelfTest.install();
		// Safety net: if the registries were frozen before this initializer ran (loaders do not
		// guarantee the order), add the aliases to the already frozen registries as well.
		LegacyTables.reinjectAll();
		String version = FabricLoader.getInstance()
			.getModContainer(MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("dev");
		LOGGER.info("Legacy ID Aliases {} loaded", version);
	}
}
