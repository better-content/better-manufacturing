package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.persistence.BuildNbt;
import com.bettercontent.manufacturing.recipe.*;
import com.bettercontent.manufacturing.tconstruct.NativeParts;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.recipe.partbuilder.PartRecipe;

@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class MaterialCraftingTests {
    private static CraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }
    private static ItemStack head() {
        var part = (ToolPartItem) ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct", "pick_head"));
        return part.withMaterial(new MaterialId("tconstruct:iron"));
    }
    private static MaterialShapedRecipe recipe(int count) {
        String json = """
            {"type":"better_manufacturing:material_shaped","pattern":["PC"],
             "key":{"P":{"item":"tconstruct:pick_head"},"C":{"item":"minecraft:cobblestone"}},
             "result":{"item":"minecraft:furnace","count":COUNT},"function":"better_manufacturing:fixture",
             "transfers":[{"role":"head","slot":0}]}
            """.replace("COUNT", Integer.toString(count));
        return ManufacturingRecipes.SHAPED.get().fromJson(new ResourceLocation("better_manufacturing:fixture"), JsonParser.parseString(json).getAsJsonObject());
    }
    @GameTest(template = "empty")
    public static void mirroredOffsetCrafting(GameTestHelper helper) {
        var recipe = recipe(1); var grid = grid();
        grid.setItem(4, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE)); grid.setItem(5, head());
        NativeParts.read(grid.getItem(5));
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Mirrored shifted recipe did not match");
        ItemStack preview = recipe.assemble(grid, helper.getLevel().registryAccess());
        ItemStack actual = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(ItemStack.isSameItemSameTags(preview, actual), "Preview differs from actual build");
        var build = BuildNbt.read(actual.getTag(), "minecraft:furnace", "better_manufacturing:fixture");
        helper.assertTrue(build.valid() && build.build().components().get("head").materialVariant().equals("tconstruct:iron"), "Material was not transferred from explicit mirrored slot");
        helper.assertTrue(!grid.getItem(5).hasTag() || !grid.getItem(5).getTag().contains(BuildNbt.KEY), "Craft preview mutated input");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void batchAndUnrelatedNbt(GameTestHelper helper) {
        var grid = grid(); grid.setItem(0, head()); grid.setItem(1, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
        var output = recipe(2).assemble(grid, helper.getLevel().registryAccess());
        var build = BuildNbt.read(output.getTag(), "minecraft:furnace", "better_manufacturing:fixture");
        helper.assertTrue(output.getCount() == 2 && build.build().components().get("head").materialQuanta() == 90
            && !build.build().components().get("head").wholePart(), "Batch manufactured two refundable whole heads");
        grid.getItem(0).getOrCreateTag().putInt("BetterContentJackhammerWear", 7);
        helper.assertTrue(recipe(2).assemble(grid, helper.getLevel().registryAccess()).isEmpty(), "Stateful component duplicated through batch output");
        var worn = recipe(1).assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(!worn.isEmpty() && BuildNbt.read(worn.getTag(), "minecraft:furnace", "better_manufacturing:fixture").build()
            .components().get("head").retainedData().get("better_manufacturing:source_part_tag").contains("BetterContentJackhammerWear:7"), "Existing wear lost");
        grid.getItem(0).getOrCreateTag().putString("UnrelatedInventory", "must not copy");
        helper.assertTrue(recipe(1).assemble(grid, helper.getLevel().registryAccess()).isEmpty(), "Unreviewed metadata accepted");
        helper.assertTrue(grid.getItem(0).getTag().contains("UnrelatedInventory"), "Rejected craft erased input data");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void recipeNetworkRoundTrip(GameTestHelper helper) {
        var original = recipe(1);
        var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            ManufacturingRecipes.SHAPED.get().toNetwork(buffer, original);
            var copy = ManufacturingRecipes.SHAPED.get().fromNetwork(original.getId(), buffer);
            var grid = grid(); grid.setItem(0, head()); grid.setItem(1, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
            var expected = original.assemble(grid, helper.getLevel().registryAccess());
            helper.assertTrue(!expected.isEmpty() && ItemStack.isSameItemSameTags(expected, copy.assemble(grid, helper.getLevel().registryAccess())), "Network recipe lost transfers");
        } finally { buffer.release(); }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void nativeStatsAndBoundedMetadata(GameTestHelper helper) {
        var material = new MaterialId("tconstruct:wood");
        var stats = com.bettercontent.manufacturing.tconstruct.TinkersStats.read(material);
        var handleKey = new com.bettercontent.manufacturing.api.MaterialEngineeringProfile.Stat("tconstruct:handle", "mining_speed",
            com.bettercontent.manufacturing.api.MaterialEngineeringProfile.Semantics.ADDITIVE_MODIFIER);
        var headKey = new com.bettercontent.manufacturing.api.MaterialEngineeringProfile.Stat("tconstruct:head", "mining_speed",
            com.bettercontent.manufacturing.api.MaterialEngineeringProfile.Semantics.BASE);
        helper.assertTrue(stats.containsKey(handleKey) && stats.containsKey(headKey), "Native stat roles conflated");
        helper.assertTrue(handleKey.relative(stats.get(handleKey), stats.get(handleKey)) == 1, "Reference handle lost its base multiplier");
        helper.assertTrue(com.bettercontent.manufacturing.tconstruct.TinkersStats.read(new MaterialId("better_manufacturing:unknown")).isEmpty(), "Unknown material gained fallback stats");
        var input = head(); var nested = new net.minecraft.nbt.CompoundTag();
        for (int i = 0; i < 10; i++) { var parent = new net.minecraft.nbt.CompoundTag(); parent.put("child", nested); nested = parent; }
        input.getOrCreateTag().put("display", nested);
        boolean rejected = false;
        try { NativeParts.read(input); } catch (IllegalArgumentException tooDeep) { rejected = true; }
        helper.assertTrue(rejected && input.getTag().contains("display"), "Nested metadata was accepted or erased");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void pinnedPartCosts(GameTestHelper helper) {
        for (var entry : NativeParts.COSTS.entrySet()) {
            var id = new ResourceLocation("tconstruct", "tools/parts/builder/" + entry.getKey());
            var recipe = helper.getLevel().getRecipeManager().byKey(id).orElseThrow();
            helper.assertTrue(recipe instanceof PartRecipe && ((PartRecipe) recipe).getCost() == entry.getValue(), "Pinned part cost drift: " + id);
        }
        helper.succeed();
    }
}
