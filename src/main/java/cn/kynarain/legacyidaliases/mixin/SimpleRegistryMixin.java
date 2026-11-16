package cn.kynarain.legacyidaliases.mixin;

import cn.kynarain.legacyidaliases.impl.LegacyAliasHolder;
import cn.kynarain.legacyidaliases.impl.LegacyTables;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds aliases to vanilla registries.
 *
 * <p>Minecraft 1.21.1 has no alias support of its own - {@code Registry#addAlias} only exists in
 * later versions - so the alias map lives here and every id/key lookup of the registry consults it
 * before falling back to the real entries. Aliases never shadow a registered id.
 *
 * <p>The alias map is filled by {@link LegacyTables#injectAliases(Registry)} at the head of
 * {@code freeze()}, i.e. right before the registry becomes immutable.
 */
@Mixin(SimpleRegistry.class)
public abstract class SimpleRegistryMixin<T> implements LegacyAliasHolder {
	@Shadow
	@Final
	private Map<Identifier, RegistryEntry.Reference<T>> idToEntry;

	@Unique
	private Map<Identifier, RegistryEntry.Reference<?>> legacyIdAliases$aliases;

	@Unique
	@Nullable
	private RegistryEntry.Reference<?> legacyIdAliases$lookup(Identifier id) {
		if (this.idToEntry.containsKey(id) || this.legacyIdAliases$aliases == null) {
			return null;
		}
		return this.legacyIdAliases$aliases.get(id);
	}

	@Override
	public Map<Identifier, RegistryEntry.Reference<?>> legacyAliases() {
		if (this.legacyIdAliases$aliases == null) {
			this.legacyIdAliases$aliases = new HashMap<>();
		}
		return this.legacyIdAliases$aliases;
	}

	@Override
	public RegistryEntry.Reference<?> legacyRawEntry(Identifier id) {
		return this.idToEntry.get(id);
	}

	@Override
	public boolean legacyRawContains(Identifier id) {
		return this.idToEntry.containsKey(id);
	}

	@Override
	public Set<Identifier> legacyRawIds() {
		return Collections.unmodifiableSet(this.idToEntry.keySet());
	}

	@Inject(method = "freeze()Lnet/minecraft/registry/Registry;", at = @At("HEAD"))
	private void legacyIdAliases$injectAliases(CallbackInfoReturnable<Registry<T>> cir) {
		LegacyTables.injectAliases((Registry<?>) (Object) this);
	}

	@Inject(method = "getEntry(Lnet/minecraft/util/Identifier;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
	private void legacyIdAliases$getEntry(Identifier id, CallbackInfoReturnable<Optional<RegistryEntry.Reference<T>>> cir) {
		RegistryEntry.Reference<?> alias = this.legacyIdAliases$lookup(id);
		if (alias != null) {
			@SuppressWarnings("unchecked")
			RegistryEntry.Reference<T> entry = (RegistryEntry.Reference<T>) alias;
			cir.setReturnValue(Optional.of(entry));
		}
	}

	@Inject(method = "get(Lnet/minecraft/util/Identifier;)Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
	private void legacyIdAliases$get(Identifier id, CallbackInfoReturnable<T> cir) {
		RegistryEntry.Reference<?> alias = this.legacyIdAliases$lookup(id);
		if (alias != null) {
			@SuppressWarnings("unchecked")
			T value = (T) alias.value();
			cir.setReturnValue(value);
		}
	}

	@Inject(method = "containsId(Lnet/minecraft/util/Identifier;)Z", at = @At("HEAD"), cancellable = true)
	private void legacyIdAliases$containsId(Identifier id, CallbackInfoReturnable<Boolean> cir) {
		if (this.legacyIdAliases$lookup(id) != null) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getEntry(Lnet/minecraft/registry/RegistryKey;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
	private void legacyIdAliases$getEntryByKey(
		RegistryKey<T> key,
		CallbackInfoReturnable<Optional<RegistryEntry.Reference<T>>> cir
	) {
		RegistryEntry.Reference<?> alias = this.legacyIdAliases$lookup(key.getValue());
		if (alias != null) {
			@SuppressWarnings("unchecked")
			RegistryEntry.Reference<T> entry = (RegistryEntry.Reference<T>) alias;
			cir.setReturnValue(Optional.of(entry));
		}
	}

	@Inject(method = "get(Lnet/minecraft/registry/RegistryKey;)Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
	private void legacyIdAliases$getByKey(RegistryKey<T> key, CallbackInfoReturnable<T> cir) {
		RegistryEntry.Reference<?> alias = this.legacyIdAliases$lookup(key.getValue());
		if (alias != null) {
			@SuppressWarnings("unchecked")
			T value = (T) alias.value();
			cir.setReturnValue(value);
		}
	}

	@Inject(method = "contains(Lnet/minecraft/registry/RegistryKey;)Z", at = @At("HEAD"), cancellable = true)
	private void legacyIdAliases$contains(RegistryKey<T> key, CallbackInfoReturnable<Boolean> cir) {
		if (this.legacyIdAliases$lookup(key.getValue()) != null) {
			cir.setReturnValue(true);
		}
	}
}
