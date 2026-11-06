package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.sifter.*;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import com.oierbravo.createsifter.content.contraptions.components.sifter.SifterBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import java.util.*;

@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class SifterFrameTests {
    static ItemStack handle(String material) { return ((ToolPartItem) ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:tool_handle"))).withMaterial(new MaterialId(material)); }
    static ItemStack mesh(GameTestHelper helper,String kind,String material) {
        var recipe=(PartVariantRecipe)helper.getLevel().getRecipeManager().byKey(new ResourceLocation("createsifter:"+kind+"_mesh")).orElseThrow();
        var grid=CreateDrillTests.grid(); for(int slot:new int[]{1,3,5,7}) grid.setItem(slot,handle(material));
        String center=switch(kind){case "string"->"minecraft:string";case "andesite"->"create:andesite_alloy";default->"create:"+kind+"_ingot";};
        grid.setItem(4,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(center))));
        var result=recipe.assemble(grid,helper.getLevel().registryAccess()); helper.assertTrue(!result.isEmpty(),"Mesh craft failed"); return result;
    }
    static SifterBlockEntity machine(GameTestHelper helper,int x,ItemStack mesh,boolean wet) {
        helper.setBlock(x,2,2,ForgeRegistries.BLOCKS.getValue(new ResourceLocation("createsifter:sifter")).defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,wet));
        var block=(SifterBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x,2,2))); block.meshInv.insertItem(0,mesh.copy(),false);
        helper.setBlock(x,1,2,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));
        ((CreativeMotorBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x,1,2)))).generatedSpeed.setValue(32); return block;
    }
    private static int count(SifterBlockEntity block,Item item) { int n=0; for(int i=0;i<block.outputInv.getSlots();i++) if(block.outputInv.getStackInSlot(i).is(item)) n+=block.outputInv.getStackInSlot(i).getCount(); return n; }
    @GameTest(template="drill_workshop",timeoutTicks=220)
    public static void ratesPreserveNativeAssayAndWetIdentity(GameTestHelper helper) {
        for(String kind:List.of("string","andesite","zinc","brass")) for(String material:List.of("tconstruct:wood","tconstruct:slimewood","tconstruct:iron")) mesh(helper,kind,material);
        // Pinned native TCon wood and iron handles both have 0 mining-speed modifier.
        // Slimewood's -0.15 is a real contrast against iron's 0, not an authored machine bonus.
        var slow=machine(helper,2,mesh(helper,"string","tconstruct:slimewood"),false);
        var iron=machine(helper,6,mesh(helper,"string","tconstruct:iron"),false);
        var wet=machine(helper,10,mesh(helper,"string","tconstruct:copper"),true);
        helper.runAtTickTime(10,()->{
            helper.assertTrue(Math.abs(slow.getSpeed())==32 && Math.abs(iron.getSpeed())==32,"No native kinetic power");
            helper.assertTrue(Math.abs(((ScreenAccess) slow).manufacturing$screenState().rate()-.85)<.001
                    && Math.abs(((ScreenAccess) iron).manufacturing$screenState().rate()-1)<.001
                    && Math.abs(slow.calculateStressApplied()/iron.calculateStressApplied()-.85)<.001,
                "Stress differs from baked native handle work: stress=" + slow.calculateStressApplied() + "/" + iron.calculateStressApplied()
                    + " state=" + ((ScreenAccess) slow).manufacturing$screenState().rate() + "/"
                    + ((ScreenAccess) iron).manufacturing$screenState().rate());
            for(var block:List.of(slow,iron,wet)) block.inputInv.setStackInSlot(0,new ItemStack(Items.CACTUS));
        });
        helper.runAtTickTime(100,()->helper.assertTrue(iron.inputInv.getStackInSlot(0).isEmpty() && !slow.inputInv.getStackInSlot(0).isEmpty(),"Settings did not change actual completion time"));
        helper.runAtTickTime(140,()->{
            for(var block:List.of(slow,iron)) helper.assertTrue(count(block,Items.COAL)==2 && count(block,Items.FLINT)==2,"Material changed native output");
            helper.assertTrue(count(wet,Items.CLAY_BALL)==2 && count(wet,Items.COAL)==0,"Native wet recipe was changed or blocked by an added qualification"); helper.succeed();
        });
    }
    @GameTest(template="drill_workshop",timeoutTicks=220)
    public static void brassChassisKeepsBatchAndFilterSemantics(GameTestHelper helper) {
        helper.setBlock(2,2,2,ForgeRegistries.BLOCKS.getValue(new ResourceLocation("createsifter:brass_sifter")).defaultBlockState());
        var block=(SifterBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(2,2,2)));
        block.meshInv.insertItem(0,mesh(helper,"brass","tconstruct:iron"),false);
        helper.setBlock(2,1,2,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));
        ((CreativeMotorBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(2,1,2)))).generatedSpeed.setValue(128);
        helper.runAtTickTime(10,()->{
            com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(block,com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour.TYPE).setFilter(new ItemStack(Items.GOLD_NUGGET));
            block.inputInv.setStackInSlot(0,new ItemStack(Items.CACTUS,4));
        });
        helper.runAtTickTime(150,()->{
            helper.assertTrue(block.inputInv.getStackInSlot(0).isEmpty() && count(block,Items.GOLD_NUGGET)==8 && count(block,Items.REDSTONE)==0,"Native batch/filter behavior changed");
            var original=block.meshInv.getStackInSlot(0).copy(); helper.getLevel().destroyBlock(block.getBlockPos(),true);
            var drops=helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(block.getBlockPos()).inflate(2),entity->entity.getItem().is(original.getItem()));
            helper.assertTrue(drops.size()==1 && ItemStack.isSameItemSameTags(original,drops.get(0).getItem()),"Breaking lost mesh settings"); helper.succeed();
        });
    }
    @GameTest(template="drill_workshop",timeoutTicks=350)
    public static void nativeOverflowPersistenceAndRemoval(GameTestHelper helper) {
        var original=mesh(helper,"string","tconstruct:wood"); var block=machine(helper,2,original,false);
        for(int i=0;i<block.outputInv.getSlots();i++) block.outputInv.setStackInSlot(i,new ItemStack(Items.COBBLESTONE,63));
        block.inputInv.setStackInSlot(0,new ItemStack(Items.CACTUS));
        helper.runAtTickTime(150,()->{
            helper.assertTrue(block.inputInv.getStackInSlot(0).isEmpty() && count(block,Items.COAL)==0,"Material adapter changed native overflow behavior");
            var loaded=new SifterBlockEntity(block.getType(),block.getBlockPos(),block.getBlockState()); loaded.setLevel(helper.getLevel()); loaded.load(block.saveWithFullMetadata());
            helper.assertTrue(ItemStack.isSameItemSameTags(loaded.meshInv.getStackInSlot(0),original),"Save/load lost mesh settings");
            for(int i=0;i<block.outputInv.getSlots();i++) block.outputInv.setStackInSlot(i,ItemStack.EMPTY);
            block.inputInv.setStackInSlot(0,new ItemStack(Items.CACTUS));
        });
        helper.runAtTickTime(280,()->{
            helper.assertTrue(count(block,Items.COAL)==2 && count(block,Items.FLINT)==2,"Ordinary native output changed");
            var player=net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"mesh_variant"));
            block.removeMesh(player);
            helper.assertTrue(player.getInventory().items.stream().anyMatch(stack->ItemStack.isSameItemSameTags(stack,original)),"Extraction lost mesh settings");
            var malformed=original.copy(); malformed.getTag().getCompound(SettingsNbt.KEY).putInt("version",999); block.meshInv.setStackInSlot(0,malformed);
            helper.assertTrue(((ScreenAccess)block).manufacturing$screenState().rate()==1,"Invalid settings failed to use baseline");
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(new ResourceLocation("better_manufacturing:retool_mesh_frame")).isEmpty(),"Exact-part retooling still active"); helper.succeed();
        });
    }
}
