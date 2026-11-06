package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessNative;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.common.block.entity.FluidMixerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.List;

/** Native PNCR recipe, redstone/pressure gates and fluid inventory remain authoritative. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PneuProcessMixerTests {
    private static final ResourceLocation ID = new ResourceLocation("pneumaticcraft:fluid_mixer");
    private static final ResourceLocation OBSIDIAN = new ResourceLocation("pneumaticcraft:fluid_mixer/mix_obsidian");
    private static TransientCraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }
    private static ItemStack mixerItem(GameTestHelper helper, String material) {
        var found = helper.getLevel().getRecipeManager().byKey(ID)
            .orElseThrow(() -> new AssertionError("Effective PNCR fluid mixer construction absent"));
        helper.assertTrue(found instanceof PartVariantRecipe, "PNCR mixer did not consume a configured plate");
        var recipe = (PartVariantRecipe) found;
        var grid = grid();
        for (int slot : new int[]{1, 4, 5, 6, 7, 8}) {
            var options = recipe.getIngredients().get(slot).getItems();
            helper.assertTrue(options.length > 0, "Native PNCR ingredient unavailable at " + slot);
            grid.setItem(slot, options[0].copy());
        }
        var part = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:large_plate"));
        helper.assertTrue(part instanceof ToolPartItem, "Real TCon plate missing");
        grid.setItem(3, ((ToolPartItem) part).withMaterial(new MaterialId(material)));
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Native mixer shape/plate rejected");
        var item = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(item.getCount() == 1 && SettingsNbt.read(item.getTag(), ID.toString()) != null,
            "Native mixer yield/settings changed");
        return item;
    }
    private static FluidMixerBlockEntity mixer(GameTestHelper helper, int x, ItemStack item) {
        var pos = helper.absolutePos(new BlockPos(x, 2, 2));
        helper.assertTrue(((BlockItem) item.getItem()).place(new DirectionalPlaceContext(helper.getLevel(), pos,
            Direction.WEST, item.copy(), Direction.UP)).consumesAction(), "Native mixer placement failed");
        var block = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(block instanceof FluidMixerBlockEntity, "Native PNCR mixer entity missing");
        var mixer = (FluidMixerBlockEntity) block;
        helper.assertTrue(mixer.getInputTank1().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000
            && mixer.getInputTank2().fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000,
            "Native 1000 mB water/lava recipe inputs not accepted");
        return mixer;
    }
    @GameTest(template = "wave2_pneu_process_workshop", timeoutTicks = 80)
    public static void materialRateChangesNativeMixerWorkAndAirNotPressureOrYield(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("pneumaticcraft") && ModList.get().isLoaded("tconstruct"),
            "Real PNCR/TCon not loaded");
        var recipe = helper.getLevel().getRecipeManager().byKey(OBSIDIAN)
            .orElseThrow(() -> new AssertionError("Native PNCR water/lava->obsidian mixer recipe missing"));
        helper.assertTrue(recipe instanceof me.desht.pneumaticcraft.api.crafting.recipe.FluidMixerRecipe,
            "Native PNCR fluid recipe serializer changed");
        // Slimewood is a real eligible head (speed 4 versus iron 6); pack
        // wood explicitly has no head and cannot supply this large plate.
        var wood = mixer(helper, 2, mixerItem(helper, "tconstruct:slimewood"));
        var iron = mixer(helper, 5, mixerItem(helper, "tconstruct:iron"));
        var unpowered = mixer(helper, 8, new ItemStack(ForgeRegistries.ITEMS.getValue(ID)));
        helper.assertTrue(Math.abs(PneuProcessNative.rate(wood, "process_rate") - Math.sqrt(.75)) < .001
            && PneuProcessNative.rate(iron, "process_rate") == 1, "Baked liner + native rotor rate lost");
        var woodAir = wood.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY)
            .orElseThrow(() -> new AssertionError("Native mixer air handler missing"));
        var ironAir = iron.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY)
            .orElseThrow(() -> new AssertionError("Native mixer air handler missing"));
        wood.addAir(woodAir.getVolume());
        iron.addAir(ironAir.getVolume());
        helper.assertTrue(wood.getPressure() >= .5 && iron.getPressure() >= .5, "Required PNCR recipe pressure not supplied");
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(wood.getCurrentRecipeIdSynced().equals(OBSIDIAN.toString())
                && iron.getCurrentRecipeIdSynced().equals(OBSIDIAN.toString()), "Native recipe ID was changed");
            helper.assertTrue(wood.craftingProgress > 0 && iron.craftingProgress > wood.craftingProgress
                && unpowered.craftingProgress == 0, "Native mixer rate or pressure gate did not operate");
            helper.assertTrue(unpowered.getInputTank1().getFluidAmount() == 1000
                && unpowered.getInputTank2().getFluidAmount() == 1000, "Unpowered native mixer consumed inputs");
        });
        helper.runAtTickTime(60, () -> {
            for (var machine : List.of(wood, iron)) {
                helper.assertTrue(machine.getPrimaryInventory().getStackInSlot(0).is(Items.OBSIDIAN)
                    && machine.getPrimaryInventory().getStackInSlot(0).getCount() == 1,
                    "Rated mixer changed native obsidian output/yield");
                helper.assertTrue(machine.getInputTank1().getFluidAmount() == 0
                    && machine.getInputTank2().getFluidAmount() == 0,
                    "Rated mixer changed native 1000 mB fluid input costs");
            }
            helper.assertTrue(unpowered.getPrimaryInventory().getStackInSlot(0).isEmpty(),
                "Unpowered native mixer created output");
            helper.succeed();
        });
    }
}
