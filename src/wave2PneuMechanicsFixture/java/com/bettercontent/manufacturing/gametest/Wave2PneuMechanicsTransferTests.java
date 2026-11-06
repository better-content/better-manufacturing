package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.pneutransfer.PneuTransferSettings;
import com.bettercontent.manufacturing.integration.pneutransfer.PneuTransferSettingsHolder;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import me.desht.pneumaticcraft.common.block.entity.AbstractFluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PneuMechanicsTransferTests {
    private static CraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }
    private static PartVariantRecipe recipe(GameTestHelper helper, String id) {
        var found = helper.getLevel().getRecipeManager().byKey(new ResourceLocation(id)).orElseThrow();
        helper.assertTrue(found instanceof PartVariantRecipe, "Production tank variant recipe missing: " + id);
        return (PartVariantRecipe) found;
    }
    private static ItemStack craft(GameTestHelper helper, String name, String material) {
        var variant = recipe(helper, "pneumaticcraft:" + name);
        var input = grid();
        for (int i = 0; i < variant.getIngredients().size(); i++) {
            var ingredient = variant.getIngredients().get(i);
            if (ingredient.isEmpty()) continue;
            ItemStack[] choices = ingredient.getItems();
            helper.assertTrue(choices.length > 0, "Missing actual pack ingredient for " + name + " index " + i);
            input.setItem(i, choices[0].copy());
        }
        for (var part : variant.parts()) {
            var source = input.getItem(part.slot());
            helper.assertTrue(source.getItem() instanceof ToolPartItem, "Expected native large plate");
            input.setItem(part.slot(), ((ToolPartItem) source.getItem()).withMaterial(MaterialVariantId.parse(material)));
        }
        helper.assertTrue(variant.matches(input, helper.getLevel()), "Production tank recipe does not match actual native materials");
        var result = variant.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(!result.isEmpty() && ItemStack.isSameItemSameTags(result, variant.assemble(input, helper.getLevel().registryAccess())),
            "Production tank preview differs from consumed-part craft");
        return result;
    }
    private static CraftingContainer tierGrid(GameTestHelper helper, PartVariantRecipe variant, ItemStack prior) {
        var input = grid(); int predecessors = 0;
        for (int slot = 0; slot < variant.getIngredients().size(); slot++) {
            var ingredient = variant.getIngredients().get(slot);
            if (ingredient.isEmpty()) continue;
            var choices = ingredient.getItems();
            helper.assertTrue(choices.length > 0, "Native tier ingredient absent at " + variant.getId() + "/" + slot);
            input.setItem(slot, choices[0].copy());
            if (input.getItem(slot).is(prior.getItem())) { input.setItem(slot, prior.copy()); predecessors++; }
        }
        for (var part : variant.parts()) {
            var fitted = input.getItem(part.slot());
            helper.assertTrue(fitted.getItem() instanceof ToolPartItem, "Tier fitting is not a consumed TCon plate");
            input.setItem(part.slot(), ((ToolPartItem) fitted.getItem()).withMaterial(MaterialVariantId.parse("tconstruct:iron")));
        }
        helper.assertTrue(predecessors == (variant.getId().getPath().equals("huge_tank") ? 4 : 2),
            "Native tank tier lost its exact predecessor count: " + variant.getId());
        return input;
    }
    @GameTest(template = "pneu_workshop")
    public static void tankTransferAndNativeContentsSurvivePlacementSaveAndDrop(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("pneumaticcraft") && ModList.get().isLoaded("tconstruct"), "Real PNCR/TCon runtime absent");
        // Pack wood has no native head; slimewood's real 4/6 head speed
        // produces the bounded .75 fitting rate against iron.
        var wood = craft(helper, "small_tank", "tconstruct:slimewood");
        var iron = craft(helper, "small_tank", "tconstruct:iron");
        var cheap = SettingsNbt.read(wood.getTag(), "pneumaticcraft:small_tank");
        var base = SettingsNbt.read(iron.getTag(), "pneumaticcraft:small_tank");
        helper.assertTrue(cheap != null && base != null && cheap.properties().get("transfer_rate") == .75
            && base.properties().get("transfer_rate") == 1, "Native pack plates did not produce contrasting transfer rates");
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.assertTrue(((BlockItem) wood.getItem()).place(new DirectionalPlaceContext(helper.getLevel(), pos, Direction.WEST, wood.copy(), Direction.UP)).consumesAction(),
            "Native PNCR tank placement failed");
        var entity = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(entity instanceof AbstractFluidTankBlockEntity && entity instanceof PneuTransferSettingsHolder,
            "Native tank BE/settings holder absent");
        var tank = (AbstractFluidTankBlockEntity) entity;
        helper.assertTrue(PneuTransferSettings.rate(tank) == .75 && tank.getTank().getCapacity() > 0,
            "Placed tank lost rated transfer or native tank capacity");
        int capacity = tank.getTank().getCapacity();
        tank.getTank().fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
            net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(tank.getTank().getFluidAmount() == 1000, "Native tank refused water before save");
        var stored = tank.saveWithFullMetadata();
        stored.putString("UnrelatedOwnerField", "keep");
        var restored = tank.getType().create(pos, tank.getBlockState());
        helper.assertTrue(restored instanceof AbstractFluidTankBlockEntity, "Native tank reconstitution failed");
        restored.setLevel(helper.getLevel()); restored.load(stored);
        var recovered = (AbstractFluidTankBlockEntity) restored;
        helper.assertTrue(PneuTransferSettings.rate(recovered) == .75 && recovered.getTank().getCapacity() == capacity
            && recovered.getTank().getFluidAmount() == 1000 && stored.getString("UnrelatedOwnerField").equals("keep"),
            "PNCR tank reload lost capacity, native contents, or unrelated NBT");
        var drops = Block.getDrops(tank.getBlockState(), helper.getLevel(), pos, tank);
        helper.assertTrue(drops.stream().anyMatch(drop -> SettingsNbt.read(drop.getTag(), "pneumaticcraft:small_tank") != null),
            "Native PNCR tank drop lost rated construction");
        helper.succeed();
    }

    @GameTest(template = "pneu_workshop")
    public static void configuredEmptyTankTierChainKeepsNativeCapacitiesAndRejectsFilledInput(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("pneumaticcraft") && ModList.get().isLoaded("tconstruct"), "Actual PNCR/TCon required");
        ItemStack prior = craft(helper, "small_tank", "tconstruct:slimewood");
        String[] names = {"small_tank", "medium_tank", "large_tank", "huge_tank"};
        int[] capacities = {32000, 64000, 128000, 512000};
        for (int tier = 0; tier < names.length; tier++) {
            if (tier > 0) {
                var variant = recipe(helper, "pneumaticcraft:" + names[tier]);
                var input = tierGrid(helper, variant, prior);
                helper.assertTrue(variant.matches(input, helper.getLevel()), "Configured empty predecessor rejected: " + names[tier]);
                var output = variant.assemble(input, helper.getLevel().registryAccess());
                helper.assertTrue(!output.isEmpty() && ItemStack.isSameItemSameTags(output, variant.assemble(input, helper.getLevel().registryAccess()))
                    && variant.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
                    "Native tank tier lost output or returned physical predecessors: " + names[tier]);
                prior = output;
            }
            var id = "pneumaticcraft:" + names[tier];
            var baked = SettingsNbt.read(prior.getTag(), id);
            helper.assertTrue(baked != null && baked.properties().get("transfer_rate") == (tier == 0 ? .75 : 1),
                "Tank tier lost fresh consumed-plate rating: " + id);
            var pos = helper.absolutePos(new BlockPos(2 + tier * 3, 2, 2));
            helper.assertTrue(((BlockItem) prior.getItem()).place(new DirectionalPlaceContext(helper.getLevel(), pos,
                Direction.WEST, prior.copy(), Direction.UP)).consumesAction(), "Native tank tier placement failed: " + id);
            var entity = helper.getLevel().getBlockEntity(pos);
            helper.assertTrue(entity instanceof AbstractFluidTankBlockEntity, "Native tank BE absent: " + id);
            var tank = (AbstractFluidTankBlockEntity) entity;
            helper.assertTrue(tank.getTank().getCapacity() == capacities[tier]
                && PneuTransferSettings.rate(tank) == (tier == 0 ? .75 : 1),
                "Native tier capacity or installed rated transfer changed: " + id);
            if (tier == 0) {
                tank.getTank().fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
                    net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                var drops = Block.getDrops(tank.getBlockState(), helper.getLevel(), pos, tank);
                var priorItem = prior.getItem();
                var filled = drops.stream().filter(drop -> drop.is(priorItem)).findFirst().orElseThrow();
                helper.assertTrue(filled.getTagElement("BlockEntityTag") != null
                    && filled.getTagElement("BlockEntityTag").contains("SavedTanks"), "Native tank loot dropped its filled contents");
                var medium = recipe(helper, "pneumaticcraft:medium_tank");
                var forbidden = tierGrid(helper, medium, prior);
                forbidden.setItem(1, filled);
                helper.assertTrue(!medium.matches(forbidden, helper.getLevel())
                    && medium.assemble(forbidden, helper.getLevel().registryAccess()).isEmpty(),
                    "Filled rated tank slipped through strict empty native tier gate");
            }
        }
        helper.succeed();
    }
}
