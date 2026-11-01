package com.bettercontent.manufacturing.integration.create;

import com.bettercontent.manufacturing.persistence.BuildNbt;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.content.kinetics.drill.DrillBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import java.util.List;

public final class CreateManufacturing {
    private static final java.util.Map<DrillBuildBehaviour, Boolean> LOADED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private CreateManufacturing() {}
    static void track(DrillBuildBehaviour behaviour) { LOADED.put(behaviour, Boolean.TRUE); }
    public static void materialsChanged() {
        DrillBuildBehaviour[] behaviours;
        synchronized (LOADED) { behaviours = LOADED.keySet().toArray(DrillBuildBehaviour[]::new); }
        for (var behaviour : behaviours) {
            var world = behaviour.blockEntity.getLevel();
            if (world != null && world.getServer() != null) world.getServer().execute(() -> {
                if (!behaviour.blockEntity.isRemoved() && !behaviour.blockEntity.isChunkUnloaded()
                        && world.getBlockEntity(behaviour.getPos()) == behaviour.blockEntity) behaviour.refreshKinetics();
            });
        }
    }
    public static void attach(BlockEntityBehaviourEvent<DrillBlockEntity> event) {
        // Fresh BlockItem placement precedes Create's first-read/first-tick event.
        // Do not replace an already installed early behaviour when that event arrives.
        if (behaviour(event.getBlockEntity()) == null) event.attach(new DrillBuildBehaviour(event.getBlockEntity()));
    }
    public static DrillBuildBehaviour behaviour(Object blockEntity) {
        return blockEntity instanceof DrillBlockEntity drill ? BlockEntityBehaviour.get(drill, DrillBuildBehaviour.TYPE) : null;
    }
    public static void install(Level level, BlockPos pos, ItemStack source) {
        if (!source.hasTag() || !source.getTag().contains(BuildNbt.KEY) || !AllBlocks.MECHANICAL_DRILL.isIn(source)) return;
        if (!(level.getBlockEntity(pos) instanceof DrillBlockEntity drill)) return;
        var behaviour = behaviour(drill);
        if (behaviour == null) {
            behaviour = new DrillBuildBehaviour(drill);
            drill.attachBehaviourLate(behaviour);
        }
        behaviour.install(source.getTag());
    }
    public static ItemRequirement requirement(DrillBuildBehaviour behaviour) {
        if (!behaviour.state().allowed()) return ItemRequirement.INVALID;
        var wanted = AllBlocks.MECHANICAL_DRILL.asStack(); wanted.setTag(behaviour.record());
        return new ItemRequirement(new ItemRequirement.StackRequirement(wanted, ItemRequirement.ItemUseType.CONSUME) {
            @Override public boolean matches(ItemStack offered) {
                return offered.is(stack.getItem()) && offered.hasTag() && DrillEngineering.safeItem(offered)
                    && stack.getTag().get(BuildNbt.KEY).equals(offered.getTag().get(BuildNbt.KEY));
            }
        });
    }
    public static boolean goggles(KineticBlockEntity blockEntity, List<Component> lines) {
        var behaviour = behaviour(blockEntity);
        if (behaviour == null) return false;
        var state = behaviour.state();
        if (!state.allowed()) lines.add(Component.translatable("better_manufacturing.drill.blocked", state.blockedReason()));
        else if (!behaviour.hasRecord()) lines.add(Component.translatable("better_manufacturing.drill.stock"));
        else {
            lines.add(Component.translatable("better_manufacturing.drill.head", state.build().components().get("head").materialVariant()));
            lines.add(Component.translatable("better_manufacturing.drill.rate", String.format(java.util.Locale.ROOT, "%.2f", state.rate())));
            lines.add(Component.translatable("better_manufacturing.drill.native_tier"));
        }
        if (state.allowed() && blockEntity.getLevel() != null) {
            var target = blockEntity.getBlockPos().relative(blockEntity.getBlockState().getValue(com.simibubi.create.content.kinetics.drill.DrillBlock.FACING));
            if (blockEntity.isOverStressed()) lines.add(Component.translatable("better_manufacturing.drill.overstressed"));
            else if (blockEntity.getSpeed() == 0) lines.add(Component.translatable("better_manufacturing.drill.unpowered"));
            else if (blockEntity.getLevel().hasChunkAt(target)) {
                var targetState = blockEntity.getLevel().getBlockState(target);
                if (!state.canCut(targetState)) lines.add(Component.translatable("better_manufacturing.drill.insufficient_tier", targetState.getBlock().getName()));
            }
        }
        return true;
    }
}
