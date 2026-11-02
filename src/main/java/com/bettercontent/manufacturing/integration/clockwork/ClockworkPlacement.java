package com.bettercontent.manufacturing.integration.clockwork;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Installs the crafted record only when the placed Clockwork block matches its held item. */
public final class ClockworkPlacement {
    private ClockworkPlacement() {}
    public static void register() { MinecraftForge.EVENT_BUS.addListener(ClockworkPlacement::onPlace); }
    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getLevel() instanceof net.minecraft.world.level.Level level)
            || level.isClientSide()) return;
        var block = ForgeRegistries.BLOCKS.getKey(event.getPlacedBlock().getBlock());
        if (block == null || !block.getNamespace().equals("vs_clockwork")) return;
        var entity = level.getBlockEntity(event.getPos());
        if (!(entity instanceof ClockworkSettingsStore holder)) return;
        for (var hand : InteractionHand.values()) {
            var stack = player.getItemInHand(hand);
            var item = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (item == null || !item.equals(block) || SettingsNbt.read(stack.getTag(), block.toString()) == null) continue;
            holder.manufacturing$install(stack.getTag());
            if (entity instanceof KineticBlockEntity kinetic && kinetic.hasNetwork())
                kinetic.getOrCreateNetwork().updateStressFor(kinetic, kinetic.calculateStressApplied());
            return;
        }
    }
}
