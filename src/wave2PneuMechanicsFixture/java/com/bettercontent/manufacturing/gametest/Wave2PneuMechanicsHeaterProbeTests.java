package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.lgmrszd.compressedcreativity.index.CCCreativeTabs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded negative loaded-profile probe; recipe result indexing cannot prove exhaustive acquisition absence. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PneuMechanicsHeaterProbeTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(Wave2PneuMechanicsHeaterProbeTests.class);
    private static final ResourceLocation HEATER = new ResourceLocation("compressedcreativity:heater");
    private Wave2PneuMechanicsHeaterProbeTests() {}

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void registeredHeaterHasDropButNoAdvertisedRecipeInFocusedPneuProfile(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("compressedcreativity"), "Compressed Creativity absent");
        var block = ForgeRegistries.BLOCKS.getValue(HEATER);
        var item = ForgeRegistries.ITEMS.getValue(HEATER);
        helper.assertTrue(block != null && item instanceof BlockItem && ((BlockItem) item).getBlock() == block,
            "Heater is not a registered placeable block item");
        var manager = helper.getLevel().getRecipeManager();
        int advertised = 0;
        int noResult = 0;
        int unreadable = 0;
        int total = 0;
        for (var recipe : manager.getRecipes()) {
            total++;
            try {
                var result = recipe.getResultItem(helper.getLevel().registryAccess());
                if (result.is(item)) {
                    advertised++;
                    LOGGER.info("Heater recipe result: id={} type={} serializer={}", recipe.getId(),
                        recipe.getType(), ForgeRegistries.RECIPE_SERIALIZERS.getKey(recipe.getSerializer()));
                }
                if (result.isEmpty()) noResult++;
            } catch (RuntimeException exception) {
                unreadable++;
                LOGGER.info("Heater probe could not inspect recipe result: id={} type={}",
                    recipe.getId(), recipe.getType(), exception);
            }
        }
        helper.assertTrue(total > 0, "Focused recipe manager unexpectedly empty");
        LOGGER.info("Heater loaded recipe probe: all-types total={} advertised={} emptyResult={} unreadable={}",
            total, advertised, noResult, unreadable);
        helper.assertTrue(advertised == 0, "Heater has an advertised acquisition recipe in focused profile");

        var pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, block.defaultBlockState());
        var absolute = helper.absolutePos(pos);
        var state = helper.getLevel().getBlockState(absolute);
        var drops = Block.getDrops(state, helper.getLevel(), absolute, helper.getLevel().getBlockEntity(absolute));
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(item) && drops.get(0).getCount() == 1,
            "Heater native/pack block loot did not return one registered heater");

        // Dedicated creative tab may have no built display entries in a headless server.
        var tab = CCCreativeTabs.BASE_CREATIVE_TAB.get();
        int creativeEntries = tab.getDisplayItems().size();
        boolean listed = tab.getDisplayItems().stream().anyMatch(stack -> stack.is(item));
        LOGGER.info("Heater creative classification: tabRegistered={} headlessEntries={} listed={}",
            tab != null, creativeEntries, listed);
        helper.succeed();
    }
}
