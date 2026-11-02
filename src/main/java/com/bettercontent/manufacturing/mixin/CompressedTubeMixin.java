package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.compressed.CompressedVariant;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.lgmrszd.compressedcreativity.blocks.bracketed_pressure_tube.BracketedPressureTubeBlock;
import com.lgmrszd.compressedcreativity.blocks.bracketed_pressure_tube.BracketedPressureTubeBlockEntity;
import com.lgmrszd.compressedcreativity.index.CCMisc;
import me.desht.pneumaticcraft.api.pressure.PressureTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

/** Retain the original PNC tier and pressure network; only constrain the crafted tube's safe limit. */
public final class CompressedTubeMixin {
    private CompressedTubeMixin() {}
    @Mixin(value = BracketedPressureTubeBlockEntity.class, remap = false)
    public abstract static class NativeTube {
        @ModifyArg(method = "<init>(Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lme/desht/pneumaticcraft/api/pressure/PressureTier;I)V",
            at = @At(value = "INVOKE", target = "Lme/desht/pneumaticcraft/api/tileentity/IAirHandlerMachineFactory;createAirHandler(Lme/desht/pneumaticcraft/api/pressure/PressureTier;I)Lme/desht/pneumaticcraft/api/tileentity/IAirHandlerMachine;"), index = 0)
        private PressureTier manufacturing$boundedTier(PressureTier original) {
            return new PressureTier() {
                private float factor() {
                    var variant = CompressedVariant.get(NativeTube.this);
                    return (float) Math.min(1, variant == null ? 1 : variant.property("pressure_envelope"));
                }
                @Override public float getDangerPressure() { return original.getDangerPressure() * factor(); }
                @Override public float getCriticalPressure() { return original.getCriticalPressure() * factor(); }
            };
        }
    }
    @Mixin(value = BracketedPressureTubeBlock.class, remap = false)
    public abstract static class BracketApplication {
        @Unique private static final ThreadLocal<MachineSettings> manufacturing$beforeRemoval = new ThreadLocal<>();
        @Unique private static boolean manufacturing$validEnvelope(MachineSettings settings) {
            if (settings == null || !settings.properties().keySet().equals(java.util.Set.of("pressure_envelope"))) return false;
            double value = settings.properties().get("pressure_envelope");
            return Double.isFinite(value) && value >= .75 && value <= 1;
        }
        @Inject(method = "removeBracket", at = @At("HEAD"))
        private void manufacturing$captureTube(BlockGetter world, BlockPos pos, boolean inWorld, CallbackInfoReturnable<Optional<ItemStack>> result) {
            manufacturing$beforeRemoval.remove();
            var block = world.getBlockEntity(pos);
            if (block instanceof BracketedPressureTubeBlockEntity tube) {
                var id = ForgeRegistries.BLOCKS.getKey(tube.getBlockState().getBlock());
                if (id != null) {
                    var settings = SettingsNbt.read(tube.saveWithoutMetadata(), id.toString());
                    if (manufacturing$validEnvelope(settings)) manufacturing$beforeRemoval.set(settings);
                }
            }
        }
        @Inject(method = "removeBracket", at = @At("RETURN"), cancellable = true)
        private void manufacturing$returnConfiguredNativeTube(BlockGetter world, BlockPos pos, boolean inWorld, CallbackInfoReturnable<Optional<ItemStack>> result) {
            var previous = manufacturing$beforeRemoval.get();
            manufacturing$beforeRemoval.remove();
            if (previous == null || result.getReturnValue().isEmpty()) return;
            ItemStack nativeTube = result.getReturnValue().get();
            var id = ForgeRegistries.ITEMS.getKey(nativeTube.getItem());
            if (id == null || !id.getNamespace().equals("pneumaticcraft")) return;
            double envelope = previous.properties().get("pressure_envelope");
            SettingsNbt.write(nativeTube.getOrCreateTag(), new MachineSettings(id.toString(), java.util.Map.of("pressure_envelope", envelope), previous.materials(), null));
        }
        @Inject(method = "getRequiredItems", at = @At("RETURN"), cancellable = true)
        private void manufacturing$schematicConfiguredNativeTube(BlockState state, net.minecraft.world.level.block.entity.BlockEntity entity,
            CallbackInfoReturnable<com.simibubi.create.content.schematics.requirement.ItemRequirement> result) {
            if (!(entity instanceof BracketedPressureTubeBlockEntity tube)) return;
            var sourceId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            if (sourceId == null) return;
            var settings = SettingsNbt.read(tube.saveWithoutMetadata(), sourceId.toString());
            if (!manufacturing$validEnvelope(settings)) return;
            var nativeItem = ((BracketedPressureTubeBlock) (Object) this).getCloneItemStack(state, null, tube.getLevel(), tube.getBlockPos(), null);
            var itemId = ForgeRegistries.ITEMS.getKey(nativeItem.getItem());
            if (itemId == null || !itemId.getNamespace().equals("pneumaticcraft")) return;
            var wanted = SettingsNbt.read(nativeItem.getTag(), itemId.toString());
            if (wanted == null) return;
            result.setReturnValue(new com.simibubi.create.content.schematics.requirement.ItemRequirement(
                new com.simibubi.create.content.schematics.requirement.ItemRequirement.StackRequirement(nativeItem,
                    com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType.CONSUME) {
                    @Override public boolean matches(ItemStack offered) {
                        return offered.is(stack.getItem()) && java.util.Objects.equals(wanted,
                            SettingsNbt.read(offered.getTag(), itemId.toString()));
                    }
                }));
        }
        @Inject(method = "getCloneItemStack", at = @At("RETURN"))
        private void manufacturing$pickConfiguredNativeTube(BlockState state, net.minecraft.world.phys.HitResult ray,
            BlockGetter world, BlockPos pos, net.minecraft.world.entity.player.Player player, CallbackInfoReturnable<ItemStack> result) {
            var block = world.getBlockEntity(pos);
            if (!(block instanceof BracketedPressureTubeBlockEntity tube)) return;
            var sourceId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            var outputId = ForgeRegistries.ITEMS.getKey(result.getReturnValue().getItem());
            if (sourceId == null || outputId == null || !outputId.getNamespace().equals("pneumaticcraft")) return;
            var settings = SettingsNbt.read(tube.saveWithoutMetadata(), sourceId.toString());
            if (!manufacturing$validEnvelope(settings)) return;
            double envelope = settings.properties().get("pressure_envelope");
            SettingsNbt.write(result.getReturnValue().getOrCreateTag(), new MachineSettings(outputId.toString(),
                java.util.Map.of("pressure_envelope", envelope), settings.materials(), null));
        }
        @Redirect(method = "usingBracketOnPressureTube", at = @At(value = "INVOKE", target = "Lcom/lgmrszd/compressedcreativity/index/CCMisc;setBlockAndUpdateKeepAir(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
        private static void manufacturing$carryConfiguredTube(Level level, BlockPos pos, BlockState target) {
            var before = level.getBlockEntity(pos);
            var sourceId = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
            var targetId = ForgeRegistries.BLOCKS.getKey(target.getBlock());
            MachineSettings source = before == null || sourceId == null ? null : SettingsNbt.read(before.saveWithoutMetadata(), sourceId.toString());
            CCMisc.setBlockAndUpdateKeepAir(level, pos, target);
            if (!manufacturing$validEnvelope(source) || targetId == null || !targetId.getNamespace().equals("compressedcreativity")) return;
            double envelope = source.properties().get("pressure_envelope");
            var result = new net.minecraft.nbt.CompoundTag();
            SettingsNbt.write(result, new MachineSettings(targetId.toString(), java.util.Map.of("pressure_envelope", envelope), source.materials(), null));
            var variant = CompressedVariant.get(level.getBlockEntity(pos));
            if (variant == null && level.getBlockEntity(pos) instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart) {
                variant = new CompressedVariant(smart); smart.attachBehaviourLate(variant);
            }
            if (variant != null) variant.install(result);
        }
    }
}
