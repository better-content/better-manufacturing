package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.powergrid.PowergridRatings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe native copper-coil consumption, not assumed inventory order or a part/material ledger. */
@Mixin(targets = "org.patryk3211.powergrid.kinetics.generator.winding.WindingItem", remap = false)
public abstract class PowergridWindingPlacementMixin {
    @Unique private static final ThreadLocal<Placement> manufacturing$placement = new ThreadLocal<>();
    @Unique private record Placement(BlockPos clicked, BlockState original, Direction side, ItemStack held,
                                    ItemStack[] inventory, BlockPos first) {}

    @Inject(method = "useOn", at = @At("HEAD"), remap = true)
    private void manufacturing$capture(UseOnContext context, CallbackInfoReturnable<InteractionResult> ci) {
        var stack = context.getItemInHand();
        var player = context.getPlayer();
        ItemStack[] inventory = null;
        if (player != null && !player.getAbilities().instabuild) {
            var contents = player.getInventory(); inventory = new ItemStack[contents.getContainerSize()];
            for (int i = 0; i < inventory.length; i++) inventory[i] = contents.getItem(i).copy();
        }
        var tag = stack.getTag(); BlockPos first = null;
        if (tag != null && tag.contains("Position", Tag.TAG_INT_ARRAY)) {
            var coords = tag.getIntArray("Position");
            if (coords.length == 3) first = new BlockPos(coords[0], coords[1], coords[2]);
        }
        manufacturing$placement.set(new Placement(context.getClickedPos(), context.getLevel().getBlockState(context.getClickedPos()),
            context.getClickedFace(), stack.copy(), inventory, first));
    }

    /** Native WindingItem treats ANY NBT as a selection; baked settings alone must not impersonate Position. */
    @Redirect(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hasTag()Z", remap = true), remap = true)
    private boolean manufacturing$hasNativeSelection(ItemStack stack) {
        var tag = stack.getTag();
        return tag != null && tag.contains("Position", Tag.TAG_INT_ARRAY) && tag.getIntArray("Position").length == 3;
    }

    /** Selection foil remains a Position marker, not an incidental consequence of configured NBT. */
    @Redirect(method = "isFoil", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hasTag()Z", remap = true), remap = true)
    private boolean manufacturing$foilOnSelectionOnly(ItemStack stack) {
        return manufacturing$hasNativeSelection(stack);
    }

    /** Native selection resets Position; keep only the validated baked coil rating on the remaining stack. */
    @Redirect(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;setTag(Lnet/minecraft/nbt/CompoundTag;)V", remap = true), remap = true)
    private void manufacturing$preserveDuringNativeSelection(ItemStack stack, CompoundTag next) {
        var settings = SettingsNbt.read(stack.getTag(), "powergrid:copper_coil");
        if (settings != null) {
            if (next == null) next = new CompoundTag();
            SettingsNbt.write(next, settings);
        }
        stack.setTag(next);
    }

    /** Native sneak-reset in air also clears Position, not the crafted coil's fixed rating. */
    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;setTag(Lnet/minecraft/nbt/CompoundTag;)V", remap = true), remap = true)
    private void manufacturing$resetSelectionAir(ItemStack stack, CompoundTag next) {
        manufacturing$preserveDuringNativeSelection(stack, next);
    }

    @Inject(method = "useOn", at = @At("RETURN"), remap = true)
    private void manufacturing$install(UseOnContext context, CallbackInfoReturnable<InteractionResult> ci) {
        Placement before = manufacturing$placement.get(); manufacturing$placement.remove();
        if (before == null || context.getLevel().isClientSide || ci.getReturnValue() != InteractionResult.SUCCESS
            || context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) return;
        var level = context.getLevel();
        var original = ForgeRegistries.BLOCKS.getKey(before.original().getBlock());
        if (original == null) return;
        if (original.toString().equals("powergrid:winding")) {
            BlockPos added = before.clicked().relative(before.side());
            if (isWinding(level.getBlockState(added))) PowergridRatings.installWinding(level.getBlockEntity(added),
                PowergridRatings.coilRatio(before.held()));
            return;
        }
        if (!original.toString().equals("create:shaft") || before.first() == null) return;
        var first = before.first();
        Direction.Axis axis = org.patryk3211.powergrid.kinetics.generator.winding.WindingItem.getPlacementAxis(before.clicked(), first);
        int delta = org.patryk3211.powergrid.kinetics.generator.winding.WindingItem.getPlacementDelta(before.clicked(), first);
        int size = Math.abs(delta) + 1;
        BlockPos start = delta > 0 ? first : before.clicked();
        var consumedCoils = new ArrayList<Double>(size);
        Player player = context.getPlayer();
        if (player != null && before.inventory() != null) {
            var contents = player.getInventory();
            for (int i = 0; i < before.inventory().length && consumedCoils.size() < size; i++) {
                ItemStack old = before.inventory()[i], now = contents.getItem(i);
                if (old.isEmpty() || old.getItem() != before.held().getItem()) continue;
                int used = Math.max(0, old.getCount() - (now.getItem() == old.getItem() ? now.getCount() : 0));
                for (int j = 0; j < used && consumedCoils.size() < size; j++) consumedCoils.add(PowergridRatings.coilRatio(old));
            }
        }
        // Creative/dispenser contexts consume the held stack directly; vanilla inventory removal above is untouched.
        while (consumedCoils.size() < size) consumedCoils.add(PowergridRatings.coilRatio(before.held()));
        for (int i = 0; i < size; i++) {
            BlockPos part = start.relative(axis, i);
            if (isWinding(level.getBlockState(part))) PowergridRatings.installWinding(level.getBlockEntity(part), consumedCoils.get(i));
        }
    }
    @Unique private static boolean isWinding(BlockState state) {
        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && id.toString().equals("powergrid:winding");
    }
}
