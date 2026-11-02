package com.bettercontent.manufacturing.mixin.createfluids;

import com.bettercontent.manufacturing.integration.createfluids.FluidVariantBehaviour;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.Objects;

/** Replace exactly the native primary item with its configured counterpart; preserve every
 * additional native schematic item requirement (including casing items). */
@Mixin(value = ItemRequirement.class, remap = false)
public abstract class FluidSchematicMixin {
    @Inject(method = "of(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)Lcom/simibubi/create/content/schematics/requirement/ItemRequirement;",
        at = @At("RETURN"), cancellable = true)
    private static void manufacturing$exactFluidBuild(BlockState state, BlockEntity block,
                                                          CallbackInfoReturnable<ItemRequirement> cir) {
        var variant = FluidVariantBehaviour.get(block);
        var nativeItems = cir.getReturnValue();
        if (variant == null || variant.settings() == null || nativeItems == null || nativeItems.isInvalid()) return;
        var stack = variant.item();
        if (stack.isEmpty()) return;
        var requirements = new ArrayList<>(nativeItems.getRequiredItems());
        for (int i = 0; i < requirements.size(); i++) {
            var original = requirements.get(i);
            if (original.usage != ItemRequirement.ItemUseType.CONSUME || !original.stack.is(stack.getItem())) continue;
            requirements.set(i, new ItemRequirement.StackRequirement(stack, original.usage) {
                @Override public boolean matches(ItemStack offered) {
                    return offered.is(this.stack.getItem()) && Objects.equals(variant.settings(),
                        SettingsNbt.read(offered.getTag(), variant.outputId()));
                }
            });
            cir.setReturnValue(new ItemRequirement(requirements));
            return;
        }
    }
}
