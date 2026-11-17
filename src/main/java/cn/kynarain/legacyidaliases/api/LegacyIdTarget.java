package cn.kynarain.legacyidaliases.api;

import java.util.Objects;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * What a legacy id resolves to.
 *
 * <p>Most ids resolve to a single identifier. A few legacy variants however are stored in data
 * components instead of the item id - the only such case in vanilla is a potion: {@code 373:5} is
 * {@code minecraft:potion} plus {@code minecraft:potion_contents = {potion: "minecraft:healing"}}.
 * Those carry the extra component here, and {@link #createStack(int)} produces the matching stack.
 */
public record LegacyIdTarget(Identifier identifier, @Nullable Identifier potion) {
	private static final Identifier WATER = Identifier.of("minecraft", "water");

	public LegacyIdTarget {
		Objects.requireNonNull(identifier, "identifier");
	}

	public static LegacyIdTarget of(Identifier identifier) {
		return new LegacyIdTarget(identifier, null);
	}

	public static LegacyIdTarget ofPotion(Identifier item, Identifier potion) {
		return new LegacyIdTarget(item, potion);
	}

	/**
	 * @return the data component changes of this target, empty when the identifier alone is enough
	 */
	public ComponentChanges components() {
		RegistryEntry<Potion> entry = this.potionEntry();
		if (entry == null) {
			return ComponentChanges.EMPTY;
		}
		return ComponentChanges.builder()
			.add(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(entry))
			.build();
	}

	@Nullable
	public RegistryEntry<Potion> potionEntry() {
		if (this.potion == null) {
			return null;
		}
		return Registries.POTION.getEntry(this.potion).orElse(null);
	}

	public ItemStack createStack(int count) {
		ItemStack stack = new ItemStack(Registries.ITEM.get(this.identifier), count);
		ComponentChanges changes = this.components();
		if (!changes.isEmpty()) {
			stack.applyUnvalidatedChanges(changes);
		}
		return stack;
	}

	/**
	 * @return {@code true} when this target needs no component (a plain potion is a water bottle)
	 */
	public boolean isPlain() {
		return this.potion == null || WATER.equals(this.potion);
	}
}
