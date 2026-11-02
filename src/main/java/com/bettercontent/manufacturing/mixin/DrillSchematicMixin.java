package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.create.CreateManufacturing;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SmartBlockEntity.class, remap = false)
public abstract class DrillSchematicMixin {
    @Inject(method = "getRequiredItems", at = @At("HEAD"), cancellable = true)
    private void manufacturing$exactBuild(BlockState state, CallbackInfoReturnable<ItemRequirement> callback) {
        var behaviour = CreateManufacturing.behaviour(this);
        if (behaviour != null && behaviour.hasRecord()) callback.setReturnValue(CreateManufacturing.requirement(behaviour));
        var variant = com.bettercontent.manufacturing.integration.create.KineticVariantBehaviour.get(this);
        if (variant != null && variant.settings() != null) callback.setReturnValue(new ItemRequirement(new ItemRequirement.StackRequirement(variant.item(), ItemRequirement.ItemUseType.CONSUME) {
            @Override public boolean matches(net.minecraft.world.item.ItemStack offered) {
                return offered.is(stack.getItem()) && java.util.Objects.equals(variant.settings(),
                    com.bettercontent.manufacturing.persistence.SettingsNbt.read(offered.getTag(), variant.outputId()));
            }
        }));
        // Compressed Creativity SmartBlockEntities need the exact rated block item
        // during schematic placement, not an unconfigured item of the same type.
        if (net.minecraftforge.fml.ModList.get().isLoaded("compressedcreativity")) {
            var compressed = com.bettercontent.manufacturing.integration.compressed.CompressedVariant.get(this);
            if (compressed != null && compressed.settings() != null)
                callback.setReturnValue(new ItemRequirement(new ItemRequirement.StackRequirement(compressed.item(), ItemRequirement.ItemUseType.CONSUME) {
                    @Override public boolean matches(net.minecraft.world.item.ItemStack offered) {
                        return offered.is(stack.getItem()) && java.util.Objects.equals(compressed.settings(),
                            com.bettercontent.manufacturing.persistence.SettingsNbt.read(offered.getTag(), compressed.outputId()));
                    }
                }));
        }
    }
}
