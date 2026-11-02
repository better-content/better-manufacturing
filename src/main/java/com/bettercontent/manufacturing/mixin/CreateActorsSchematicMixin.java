package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;

/** A blueprint requires the same baked actor item, not a baseline or newly evaluated part recipe. */
@Mixin(value = ItemRequirement.class, remap = false)
public abstract class CreateActorsSchematicMixin {
    @Inject(method = "of(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)Lcom/simibubi/create/content/schematics/requirement/ItemRequirement;",
        at = @At("RETURN"), cancellable = true)
    private static void manufacturing$actorRequirement(BlockState state, BlockEntity be,
                                                         CallbackInfoReturnable<ItemRequirement> result) {
        if (be == null) return;
        var blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null) return;
        String output = blockId.toString();
        if (!output.equals("create:mechanical_plough") && !output.equals("create:mechanical_roller")) return;
        var tag = be.saveWithFullMetadata();
        var settings = SettingsNbt.read(tag, output);
        if (settings == null || result.getReturnValue() == null || result.getReturnValue().isInvalid()) return;
        ItemStack actor = new ItemStack(state.getBlock());
        actor.getOrCreateTag().put(SettingsNbt.KEY, tag.get(SettingsNbt.KEY).copy());
        // Keep all native additional requirements (e.g. roller filtering); replace only its actor item.
        var nativeRequirements = new ArrayList<ItemRequirement.StackRequirement>();
        for (var required : result.getReturnValue().getRequiredItems())
            if (!required.stack.is(actor.getItem())) nativeRequirements.add(required);
        nativeRequirements.add(new ItemRequirement.StackRequirement(actor, ItemRequirement.ItemUseType.CONSUME) {
            @Override public boolean matches(ItemStack offered) {
                return offered.is(stack.getItem()) && java.util.Objects.equals(settings,
                    SettingsNbt.read(offered.getTag(), output));
            }
        });
        result.setReturnValue(new ItemRequirement(nativeRequirements));
    }
}
