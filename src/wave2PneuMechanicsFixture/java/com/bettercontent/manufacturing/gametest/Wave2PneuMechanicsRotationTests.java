package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.api.NumericEvaluator;
import com.bettercontent.manufacturing.api.NumericInputs;
import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.Map;

@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PneuMechanicsRotationTests {
    private static CraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }
    private static PartVariantRecipe recipe(GameTestHelper helper, String path) {
        var found = helper.getLevel().getRecipeManager().byKey(new ResourceLocation("pneumaticcraft:" + path)).orElseThrow();
        helper.assertTrue(found instanceof PartVariantRecipe, "Actual PNCR part-dependent recipe missing: " + path);
        return (PartVariantRecipe) found;
    }
    private static CraftingContainer ingredients(GameTestHelper helper, PartVariantRecipe recipe) {
        var input = grid();
        for (int slot = 0; slot < recipe.getIngredients().size(); slot++) {
            var ingredient = recipe.getIngredients().get(slot);
            if (ingredient.isEmpty()) continue;
            var choices = ingredient.getItems();
            helper.assertTrue(choices.length > 0, "Native PNCR ingredient absent: " + recipe.getId() + "/" + slot);
            input.setItem(slot, choices[0].copy());
        }
        for (var part : recipe.parts()) if (input.getItem(part.slot()).getItem() instanceof ToolPartItem tool)
            input.setItem(part.slot(), tool.withMaterial(MaterialVariantId.parse("tconstruct:iron")));
        return input;
    }
    private static ItemStack rated(ItemStack original, String output, String property, double rate) {
        var stack = original.copy();
        if (!property.equals("rotation_rate") || rate > 1.5) {
            // Preserve the native item while making its copied numeric record unverifiable.
            var payload = original.hasTag() && original.getTag().contains(SettingsNbt.KEY)
                ? original.getTag().getCompound(SettingsNbt.KEY).copy() : new net.minecraft.nbt.CompoundTag();
            payload.putInt("version", MachineSettings.VERSION);
            payload.putString("output", output);
            payload.put("materials", new net.minecraft.nbt.CompoundTag());
            payload.put("parts", new net.minecraft.nbt.CompoundTag());
            payload.put("components", new net.minecraft.nbt.CompoundTag());
            payload.putString("function", "better_manufacturing:invalid_rotation");
            stack.getOrCreateTag().put(SettingsNbt.KEY, payload);
        } else {
            var numeric = new NumericInputs("better_manufacturing:pneupressure/rotation",
                Map.of("body", Map.of(Measure.HEAD_MINING_SPEED, new NumericInputs.Stat(rate, 1)),
                    "handle", Map.of(Measure.HANDLE_MINING_SPEED, new NumericInputs.Stat(rate - 1, 0))), Map.of());
            SettingsNbt.write(stack.getOrCreateTag(), new MachineSettings(output,
                NumericEvaluator.evaluate(output, numeric, null), Map.of(), null, numeric));
        }
        return stack;
    }
    private static double rotation(ItemStack stack, String id) {
        var settings = SettingsNbt.read(stack.getTag(), id);
        if (settings == null) throw new AssertionError("Configured native rotor missing: " + id);
        return settings.properties().get("rotation_rate");
    }
    @GameTest(template = "pneu_workshop")
    public static void nativeBladesAndRotorsRejectTamperedSavedRatings(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("pneumaticcraft") && ModList.get().isLoaded("tconstruct"), "Real PNCR/TCon required");
        var rotorRecipe = recipe(helper, "turbine_rotor");
        var rotorGrid = ingredients(helper, rotorRecipe);
        helper.assertTrue(rotorGrid.getItem(6).is(rotorGrid.getItem(8).getItem()) && rotorGrid.getItem(6).getCount() == 1
            && rotorGrid.getItem(8).getCount() == 1 && !rotorGrid.getItem(0).isEmpty(),
            "Native rotor lost two physical turbine blades or compressed-iron ingot");
        var blade = rotorGrid.getItem(6);
        rotorGrid.setItem(6, rated(blade, "pneumaticcraft:turbine_blade", "rotation_rate", .75));
        rotorGrid.setItem(8, rated(blade, "pneumaticcraft:turbine_blade", "rotation_rate", .75));
        helper.assertTrue(rotorRecipe.matches(rotorGrid, helper.getLevel()), "Native turbine-blade inputs rejected");
        var valid = rotorRecipe.assemble(rotorGrid, helper.getLevel().registryAccess());
        helper.assertTrue(!valid.isEmpty() && rotation(valid, "pneumaticcraft:turbine_rotor") < 1
            && ItemStack.isSameItemSameTags(valid, rotorRecipe.assemble(rotorGrid, helper.getLevel().registryAccess()))
            && rotorRecipe.getRemainingItems(rotorGrid).stream().allMatch(ItemStack::isEmpty),
            "Physical rotor blade gate, preview or part consumption changed");
        rotorGrid.setItem(6, rated(blade, "pneumaticcraft:turbine_blade", "rotation_rate", 999));
        rotorGrid.setItem(8, rated(blade, "pneumaticcraft:turbine_blade", "work_rate", 1.5));
        var malformedBlades = rotorGrid.getItem(6).copy();
        var rejectedRotor = rotorRecipe.assemble(rotorGrid, helper.getLevel().registryAccess());
        helper.assertTrue(rejectedRotor.isEmpty() && ItemStack.isSameItemSameTags(malformedBlades, rotorGrid.getItem(6)),
            "Malformed configured blades must reject the rotor craft without changing the inputs");
        var fluxRecipe = recipe(helper, "flux_compressor"); var fluxGrid = ingredients(helper, fluxRecipe);
        helper.assertTrue(fluxGrid.getItem(4).is(valid.getItem()), "Native flux recipe lost its turbine rotor gate");
        fluxGrid.setItem(4, valid);
        var ratedFlux = fluxRecipe.assemble(fluxGrid, helper.getLevel().registryAccess());
        var ratedSettings = SettingsNbt.read(ratedFlux.getTag(), "pneumaticcraft:flux_compressor");
        helper.assertTrue(ratedSettings != null && ratedSettings.properties().get("compression_rate") < 1
            && fluxRecipe.getRemainingItems(fluxGrid).stream().allMatch(ItemStack::isEmpty),
            "Native flux compressor ignored physically consumed rated rotor/parts");
        fluxGrid.setItem(4, rated(valid, "pneumaticcraft:turbine_rotor", "rotation_rate", 999));
        var invalidFlux = fluxRecipe.assemble(fluxGrid, helper.getLevel().registryAccess());
        helper.assertTrue(invalidFlux.isEmpty(),
            "Malformed configured rotor must reject the compressor craft, not become baseline production");
        helper.succeed();
    }
}
