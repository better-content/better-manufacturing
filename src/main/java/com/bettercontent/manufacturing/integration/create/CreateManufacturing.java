package com.bettercontent.manufacturing.integration.create;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
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
    private CreateManufacturing() {}
    public static void attach(BlockEntityBehaviourEvent<DrillBlockEntity> event) {
        if (behaviour(event.getBlockEntity()) == null) event.attach(new DrillBuildBehaviour(event.getBlockEntity()));
    }
    public static DrillBuildBehaviour behaviour(Object blockEntity) {
        return blockEntity instanceof DrillBlockEntity drill ? BlockEntityBehaviour.get(drill, DrillBuildBehaviour.TYPE) : null;
    }
    public static void install(Level level, BlockPos pos, ItemStack source) {
        if (!source.hasTag() || !source.getTag().contains(SettingsNbt.KEY)) return;
        var block = level.getBlockEntity(pos);
        if (block == null) return;
        if (com.bettercontent.manufacturing.integration.createfluids.FluidVariantBehaviour.supports(block)
            && (block.getBlockState().getBlock().asItem() == source.getItem()
                || "create:fluid_pipe".equals(String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(source.getItem()))))) {
            var fluid = com.bettercontent.manufacturing.integration.createfluids.FluidVariantBehaviour.get(block);
            if (fluid == null && block instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart) {
                fluid = new com.bettercontent.manufacturing.integration.createfluids.FluidVariantBehaviour(smart);
                smart.attachBehaviourLate(fluid);
            }
            if (fluid != null) fluid.install(source.getTag());
            return;
        }
        if (block instanceof com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlockEntity engine
            && block.getBlockState().getBlock().asItem() == source.getItem()) {
            var steam = com.bettercontent.manufacturing.integration.createbulk.SteamVariantBehaviour.get(engine);
            if (steam == null) { steam = new com.bettercontent.manufacturing.integration.createbulk.SteamVariantBehaviour(engine); engine.attachBehaviourLate(steam); }
            steam.install(source.getTag()); return;
        }
        if (KineticVariantBehaviour.supports(block) && block.getBlockState().getBlock().asItem() == source.getItem()) {
            var kinetic = (KineticBlockEntity) block;
            var variant = KineticVariantBehaviour.get(kinetic);
            if (variant == null) { variant = new KineticVariantBehaviour(kinetic); kinetic.attachBehaviourLate(variant); }
            variant.install(source.getTag()); return;
        }
        if (!(block instanceof DrillBlockEntity drill) || !AllBlocks.MECHANICAL_DRILL.isIn(source)) return;
        var behaviour = behaviour(drill);
        if (behaviour == null) { behaviour = new DrillBuildBehaviour(drill); drill.attachBehaviourLate(behaviour); }
        behaviour.install(source.getTag());
    }
    public static ItemRequirement requirement(DrillBuildBehaviour behaviour) {
        var wanted = AllBlocks.MECHANICAL_DRILL.asStack(); wanted.setTag(behaviour.record());
        return new ItemRequirement(new ItemRequirement.StackRequirement(wanted, ItemRequirement.ItemUseType.CONSUME) {
            @Override public boolean matches(ItemStack offered) {
                return offered.is(stack.getItem()) && java.util.Objects.equals(SettingsNbt.read(stack.getTag(), DrillEngineering.CHASSIS), SettingsNbt.read(offered.getTag(), DrillEngineering.CHASSIS));
            }
        });
    }
    public static boolean goggles(KineticBlockEntity blockEntity, List<Component> lines) {
        var behaviour = behaviour(blockEntity);
        if (behaviour == null) return false;
        var settings = SettingsNbt.read(behaviour.record(), DrillEngineering.CHASSIS);
        if (settings == null) return false;
        lines.add(Component.translatable("better_manufacturing.variant.rate", String.format(java.util.Locale.ROOT, "%.2f", settings.rate())));
        return true;
    }
}
