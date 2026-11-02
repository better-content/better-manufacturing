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
    public static void explicitSubassemblyTransfers(GameTestHelper helper) {
        var serializer = ManufacturingRecipes.SHAPED.get();
        var sourceRecipe = serializer.fromJson(new ResourceLocation("better_manufacturing:subassembly_fixture"), JsonParser.parseString("""
            {"type":"better_manufacturing:material_shaped","pattern":["PP"],
             "key":{"P":{"item":"tconstruct:pick_head"}},"result":{"item":"minecraft:furnace"},
             "function":"better_manufacturing:fixture","qualifications":["test:fixture_access"],
             "transfers":[{"role":"head","slot":0},{"role":"support","slot":1}]}
            """).getAsJsonObject());
        var input = grid(); input.setItem(0, head()); input.setItem(1, head());
        input.getItem(1).getOrCreateTag().putInt("BetterContentJackhammerWear", 7);
        var source = sourceRecipe.assemble(input, helper.getLevel().registryAccess());
        String upgradedJson = """
            {"type":"better_manufacturing:material_shaped","pattern":["FC"],
             "key":{"F":{"item":"minecraft:furnace"},"C":{"item":"minecraft:cobblestone"}},
             "result":{"item":"minecraft:smoker"},"function":"better_manufacturing:fixture_upgrade",
             "transfers":[{"role":"cutter","slot":0,"source_role":"head","source_function":"better_manufacturing:fixture"},
                          {"role":"brace","slot":0,"source_role":"support","source_function":"better_manufacturing:fixture"}],
             "qualification_transfers":[{"slot":0,"qualification":"test:fixture_access"}]}
            """;
        var json = JsonParser.parseString(upgradedJson).getAsJsonObject();
        var upgraded = serializer.fromJson(new ResourceLocation("better_manufacturing:upgrade_fixture"), json);
        var shifted = grid(); shifted.setItem(7, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE)); shifted.setItem(8, source);
        var result = upgraded.assemble(shifted, helper.getLevel().registryAccess());
        helper.assertTrue(!result.isEmpty(), "Explicit mirrored subassembly transfer failed");
        var before = BuildNbt.read(source.getTag(), "minecraft:furnace", "better_manufacturing:fixture").build();
        var after = BuildNbt.read(result.getTag(), "minecraft:smoker", "better_manufacturing:fixture_upgrade").build();
        helper.assertTrue(after.components().get("cutter").equals(before.components().get("head"))
            && after.components().get("brace").equals(before.components().get("support")) && after.qualifications().equals(before.qualifications()), "Subassembly lost quantities, original wear or access identity");
        var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, upgraded);
            var copy = serializer.fromNetwork(upgraded.getId(), buffer);
            helper.assertTrue(ItemStack.isSameItemSameTags(copy.assemble(shifted, helper.getLevel().registryAccess()), result), "Network subassembly bindings changed");
        } finally { buffer.release(); }
        json.getAsJsonArray("transfers").remove(1);
        helper.assertTrue(serializer.fromJson(upgraded.getId(), json).assemble(shifted, helper.getLevel().registryAccess()).isEmpty(), "Subassembly silently lost an unaccounted component");
        json = JsonParser.parseString(upgradedJson).getAsJsonObject(); json.remove("qualification_transfers");
        helper.assertTrue(serializer.fromJson(upgraded.getId(), json).assemble(shifted, helper.getLevel().registryAccess()).isEmpty(), "Subassembly silently lost progression identity");
        source.getOrCreateTag().putString("Inventory", "preserve");
        helper.assertTrue(upgraded.assemble(shifted, helper.getLevel().registryAccess()).isEmpty() && source.getTag().contains("Inventory"), "Subassembly erased unsupported inventory metadata");
        helper.succeed();
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
        var base = recipe(1);
        var original = new MaterialShapedRecipe(base.base(), base.function(), base.transfers(), java.util.Set.of("test:fixture_access"));
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
    public static void shapelessBindingsAndAccess(GameTestHelper helper) {
        var json = JsonParser.parseString("""
            {"type":"better_manufacturing:material_shapeless","ingredients":[{"item":"minecraft:cobblestone"},{"item":"tconstruct:pick_head"}],
             "result":{"item":"minecraft:furnace"},"function":"better_manufacturing:fixture",
             "transfers":[{"role":"head","ingredient":1}],"qualifications":["test:fixture_access"]}
            """).getAsJsonObject();
        var serializer = ManufacturingRecipes.SHAPELESS.get();
        var recipe = serializer.fromJson(new ResourceLocation("better_manufacturing:shapeless_fixture"), json);
        var grid = grid(); grid.setItem(8, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE)); grid.setItem(2, head());
        var before = grid.getItem(2).copy();
        var result = recipe.assemble(grid, helper.getLevel().registryAccess());
        var decoded = BuildNbt.read(result.getTag(), "minecraft:furnace", "better_manufacturing:fixture");
        helper.assertTrue(!result.isEmpty() && decoded.valid() && decoded.build().qualifications().equals(java.util.Set.of("test:fixture_access")), "Shapeless recipe failed to bind ingredient/access");
        helper.assertTrue(ItemStack.isSameItemSameTags(before, grid.getItem(2)), "Shapeless preview modified input");
        var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, recipe);
            var copy = serializer.fromNetwork(recipe.getId(), buffer);
            helper.assertTrue(ItemStack.isSameItemSameTags(result, copy.assemble(grid, helper.getLevel().registryAccess())), "Shapeless network lost provenance/access");
        } finally { buffer.release(); }
        grid.getItem(2).getOrCreateTag().putString("UnreviewedInventory", "keep");
        helper.assertTrue(recipe.assemble(grid, helper.getLevel().registryAccess()).isEmpty() && grid.getItem(2).getTag().contains("UnreviewedInventory"), "Unsafe shapeless metadata accepted/erased");
        json.getAsJsonArray("ingredients").set(0, JsonParser.parseString("{\"item\":\"tconstruct:pick_head\"}"));
        var transfers = json.getAsJsonArray("transfers");
        transfers.add(JsonParser.parseString("{\"role\":\"other_head\",\"ingredient\":0}"));
        recipe = serializer.fromJson(recipe.getId(), json);
        grid.clearContent(); grid.setItem(0, head());
        grid.setItem(1, ((ToolPartItem) head().getItem()).withMaterial(new MaterialId("tconstruct:wood")));
        helper.assertTrue(recipe.assemble(grid, helper.getLevel().registryAccess()).isEmpty(), "Ambiguous material/role assignment was guessed");
        grid.setItem(1, head());
        result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(!result.isEmpty() && BuildNbt.read(result.getTag(), "minecraft:furnace", "better_manufacturing:fixture").build().components().size() == 2,
            "Identical provenance permutations did not match");
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
