package com.bettercontent.manufacturing.integration.diesel;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.jesz.createdieselgenerators.content.diesel_engine.EngineUpgrades;
import com.jesz.createdieselgenerators.content.diesel_engine.IEngine;
import com.jesz.createdieselgenerators.content.diesel_engine.normal.DieselEngineBlock;
import com.jesz.createdieselgenerators.content.diesel_engine.modular.ModularDieselEngineBlock;
import com.jesz.createdieselgenerators.content.diesel_engine.huge.HugeDieselEngineBlock;
import com.simibubi.create.api.schematic.requirement.SchematicRequirementRegistries;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Objects;

/** Decorate CDG's native block requirements, without inventing a second engine or extra upgrade. */
public final class DieselSchematic {
    private DieselSchematic() {}

    /** Once during common setup after registries, guarded by both optional Create and CDG mods. */
    public static void register() {
        for (String id : new String[]{"diesel_engine", "large_diesel_engine", "huge_diesel_engine"}) {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("createdieselgenerators", id));
            if (block != null && block != Blocks.AIR && SchematicRequirementRegistries.BLOCKS.get(block) == null)
                SchematicRequirementRegistries.BLOCKS.register(block, DieselSchematic::requirements);
        }
    }

    private static ItemRequirement requirements(BlockState state, BlockEntity entity) {
        Block block = state.getBlock();
        ItemRequirement nativeRequirement = block instanceof DieselEngineBlock normal
            ? normal.getRequiredItems(state, entity)
            : block instanceof ModularDieselEngineBlock modular ? modular.getRequiredItems(state, entity)
            : block instanceof HugeDieselEngineBlock ? hugeRequirement(block, entity)
            : ItemRequirement.INVALID;
        return adapt(block.asItem(), entity, nativeRequirement);
    }

    /** Huge has no native special requirement: retain its vanilla block cost and charge for its installed addon. */
    private static ItemRequirement hugeRequirement(Block block, BlockEntity entity) {
        var stacks = new ArrayList<ItemStack>();
        stacks.add(new ItemStack(block));
        if (entity instanceof IEngine engine && engine.getUpgrade() != null) {
            var addon = engine.getUpgrade().getItem();
            if (!addon.isEmpty()) stacks.add(addon);
        }
        return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, stacks);
    }

    /** Replace only existing native inputs; the primary and upgrades keep their exact native counts. */
    public static ItemRequirement adapt(Item engineItem, BlockEntity blockEntity, ItemRequirement nativeRequirement) {
        if (!(blockEntity instanceof IEngine engine) || !(engine instanceof DieselEngineData data)
            || nativeRequirement == null || nativeRequirement.isEmpty() || nativeRequirement.isInvalid()) return nativeRequirement;
        MachineSettings build = data.manufacturing$engine();
        MachineSettings turbo = engine.getUpgrade() == EngineUpgrades.TURBOCHARGER ? data.manufacturing$turbo() : null;
        if (build == null && turbo == null) return nativeRequirement;
        var required = new ArrayList<ItemRequirement.StackRequirement>();
        for (var entry : nativeRequirement.getRequiredItems()) {
            if (entry.stack.is(engineItem) && build != null) required.add(exact(entry, build));
            else if (turbo != null && entry.stack.is(EngineUpgrades.TURBOCHARGER.getItem().getItem()))
                required.add(exact(entry, turbo));
            else required.add(entry);
        }
        return new ItemRequirement(required);
    }

    private static ItemRequirement.StackRequirement exact(ItemRequirement.StackRequirement original, MachineSettings expected) {
        var required = original.stack.copy();
        SettingsNbt.write(required.getOrCreateTag(), expected);
        return new ItemRequirement.StackRequirement(required, original.usage) {
            @Override public boolean matches(ItemStack offered) {
                return original.matches(offered) && Objects.equals(expected,
                    SettingsNbt.read(offered.getTag(), expected.output()));
            }
        };
    }
}
