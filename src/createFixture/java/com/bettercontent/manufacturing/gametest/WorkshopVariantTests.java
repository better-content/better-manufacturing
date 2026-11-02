package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.create.*;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.kinetics.press.*;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.*;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class WorkshopVariantTests {
    static ItemStack craft(GameTestHelper helper,String name,String material) {
        // The real KubeJS wave-two profiles select production20, which removes the old
        // native IDs for these four roots. The no-script core profile retains them.
        String legacy="create:crafting/kinetics/"+name;
        String expected=net.minecraftforge.fml.ModList.get().isLoaded("kubejs") ? switch(name) {
            case "millstone", "mechanical_press", "mechanical_pump" -> "kubejs:powered_works/direct_root/"+name;
            case "mechanical_mixer" -> "kubejs:powered_works/mechanical_mixer";
            default -> legacy;
        } : legacy;
        var found=helper.getLevel().getRecipeManager().byKey(new ResourceLocation(expected))
            .orElseThrow(() -> new AssertionError("Missing expected active Create recipe "+expected));
        helper.assertTrue(found instanceof PartVariantRecipe,"Expected configured Create recipe "+expected);
        var recipe=(PartVariantRecipe)found;
        var grid=CreateDrillTests.grid();
        for(int i=0;i<recipe.getIngredients().size();i++) {
            var options=recipe.getIngredients().get(i).getItems();
            if(options.length>0) grid.setItem(i%recipe.getWidth()+i/recipe.getWidth()*3,options[0].copy());
        }
        for(var binding:recipe.parts()) {
            int slot=binding.slot()%recipe.getWidth()+binding.slot()/recipe.getWidth()*3;
            if(grid.getItem(slot).getItem() instanceof ToolPartItem part) grid.setItem(slot,part.withMaterial(slimeknights.tconstruct.library.materials.definition.MaterialVariantId.parse(material)));
            else grid.setItem(slot,craft(helper,net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(grid.getItem(slot).getItem()).getPath(),material));
        }
        helper.assertTrue(recipe.matches(grid,helper.getLevel()),"Expected active Create recipe "+expected+" rejected real ingredients/part: "+material);
        var output=recipe.assemble(grid,helper.getLevel().registryAccess()); helper.assertTrue(!output.isEmpty(),"Failed to craft "+expected+" with "+material); return output;
    }
    @GameTest(template="drill_workshop")
    public static void pumpUsesBakedPressureAndNativeNetwork(GameTestHelper helper) {
        for (int i=0;i<2;i++) {
            var part = craft(helper,"mechanical_pump",i==0?"tconstruct:wood":"tconstruct:iron");
            var relative = new BlockPos(2+i*5,2,2);
            var placed = ((BlockItem)part.getItem()).place(new DirectionalPlaceContext(helper.getLevel(),helper.absolutePos(relative),Direction.WEST,part.copy(),Direction.UP));
            helper.assertTrue(placed.consumesAction(),"Pump placement failed");
            var pump=(com.simibubi.create.content.fluids.pump.PumpBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(relative));
            helper.assertTrue(KineticVariantBehaviour.get(pump)!=null && KineticVariantBehaviour.get(pump).rate()==(i==0?.75:1),"Pump settings lost at placement");
            var restored=pump.getType().create(pump.getBlockPos(),pump.getBlockState()); restored.setLevel(helper.getLevel()); restored.load(pump.saveWithFullMetadata());
            helper.assertTrue(KineticVariantBehaviour.get(restored).rate()==(i==0?.75:1),"Pump save/load lost rating");
            var drops=Block.getDrops(pump.getBlockState(),helper.getLevel(),pump.getBlockPos(),pump);
            helper.assertTrue(drops.size()==1 && ItemStack.isSameItemSameTags(part,drops.get(0)),"Pump loot lost settings");
            helper.setBlock(relative, com.simibubi.create.AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING,Direction.EAST));
            pump=(com.simibubi.create.content.fluids.pump.PumpBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(relative));
            com.bettercontent.manufacturing.integration.create.CreateManufacturing.install(helper.getLevel(),pump.getBlockPos(),part);
            helper.setBlock(relative.east(), com.simibubi.create.AllBlocks.FLUID_PIPE.getDefaultState());
            helper.setBlock(relative.east(2), com.simibubi.create.AllBlocks.FLUID_PIPE.getDefaultState());
            helper.setBlock(relative.west(), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.EAST));
            ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)helper.getLevel().getBlockEntity(pump.getBlockPos().west())).generatedSpeed.setValue(64);
            helper.assertTrue(pump.calculateStressApplied() > 0,"Pump native stress missing");
        }
        var first=(com.simibubi.create.content.fluids.pump.PumpBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(2,2,2)));
        var second=(com.simibubi.create.content.fluids.pump.PumpBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(7,2,2)));
        helper.assertTrue(Math.abs(first.calculateStressApplied()/second.calculateStressApplied()-.75)<.001,"Pump pressure did not carry matching stress");
        helper.runAtTickTime(15,()->{
            double[] pressures=new double[2];
            for(int i=0;i<2;i++) {
                var pump=i==0?first:second; pump.setSpeed(64); pump.updatePressureChange(); pump.tick();
                var pipe=(com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity)helper.getLevel().getBlockEntity(pump.getBlockPos().relative(Direction.EAST));
                var flow=com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(pipe,com.simibubi.create.content.fluids.FluidTransportBehaviour.TYPE);
                var pressure=flow.getConnection(Direction.WEST).getPressure();
                pressures[i]=Math.abs(pressure.getFirst())+Math.abs(pressure.getSecond());
            }
            helper.assertTrue(pressures[1]>0 && Math.abs(pressures[0]/pressures[1]-.75)<.01,"Native pipe pressure ignored baked pump setting: "+pressures[0]+" / "+pressures[1]+" pump="+first.getBlockState()+" speed="+first.getSpeed()+" pipe="+helper.getLevel().getBlockState(first.getBlockPos().east()));
            helper.succeed();
        });
    }
    @GameTest(template="drill_workshop")
    public static void workshopSettingsDriveNativeWorkAndSurvivePickup(GameTestHelper helper) {
        String[] names={"mechanical_saw","millstone","mechanical_press","mechanical_mixer","encased_fan"};
        for(String name:new String[]{"mechanical_saw","millstone","mechanical_press","encased_fan"}) craft(helper,name,"tconstruct:rock#stone");
        craft(helper,"mechanical_mixer","tconstruct:wood#oak");
        for(int index=0;index<names.length;index++) {
            double[] progress=new double[2],stress=new double[2];
            for(int choice=0;choice<2;choice++) {
                String material=choice==1?"tconstruct:iron":index==0||index==3?"tconstruct:wood":"tconstruct:copper";
                var item=craft(helper,names[index],material); var pos=helper.absolutePos(new BlockPos(2+index*3,3,2+choice*4));
                var result=((BlockItem)item.getItem()).place(new DirectionalPlaceContext(helper.getLevel(),pos,Direction.WEST,item.copy(),Direction.UP));
                helper.assertTrue(result.consumesAction(),"Workshop placement failed");
                var block=(KineticBlockEntity)helper.getLevel().getBlockEntity(pos); var variant=KineticVariantBehaviour.get(block);
                helper.assertTrue(variant!=null && variant.settings()!=null && (choice==1?variant.rate()==1:variant.rate()<1),"Workshop settings were not installed");
                var loaded=block.getType().create(pos,block.getBlockState()); loaded.setLevel(helper.getLevel()); loaded.load(block.saveWithFullMetadata());
                helper.assertTrue(KineticVariantBehaviour.get(loaded).settings().equals(variant.settings()),"Workshop save/load lost settings");
                var drops=Block.getDrops(block.getBlockState(),helper.getLevel(),pos,block);
                helper.assertTrue(drops.size()==1 && ItemStack.isSameItemSameTags(item,drops.get(0)),"Workshop drop lost settings");
                helper.assertTrue(block.getRequiredItems(block.getBlockState()).getRequiredItems().get(0).matches(item),"Schematic lost variant identity");
                block.initialize(); block.setSpeed(64); stress[choice]=block.calculateStressApplied();
                if(block instanceof SawBlockEntity saw) { saw.inventory.setStackInSlot(0,new ItemStack(Items.OAK_LOG)); saw.inventory.remainingTime=1000; }
                if(block instanceof MillstoneBlockEntity mill) { mill.inputInv.setStackInSlot(0,new ItemStack(Items.WHEAT)); mill.timer=1000; }
                if(block instanceof MechanicalPressBlockEntity press) press.getPressingBehaviour().start(PressingBehaviour.Mode.WORLD);
                if(block instanceof MechanicalMixerBlockEntity mixer) { mixer.running=true; mixer.runningTicks=20; mixer.processingTicks=1000; }
                for(int tick=0;tick<6;tick++) {
                    block.setSpeed(64);
                    if(block instanceof MechanicalPressBlockEntity press) press.getPressingBehaviour().tick(); else block.tick();
                }
                if(block instanceof SawBlockEntity saw) progress[choice]=1000-saw.inventory.remainingTime;
                if(block instanceof MillstoneBlockEntity mill) progress[choice]=1000-mill.timer;
                if(block instanceof MechanicalPressBlockEntity press) progress[choice]=press.getPressingBehaviour().runningTicks;
                if(block instanceof MechanicalMixerBlockEntity mixer) progress[choice]=1000-mixer.processingTicks;
                if(block instanceof com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity fan) progress[choice]=fan.getMaxDistance();
            }
            helper.assertTrue(progress[0]>0 && progress[0]<progress[1],names[index]+" native work did not use settings: "+progress[0]+" / "+progress[1]);
            helper.assertTrue(stress[0]>0 && stress[0]<stress[1],names[index]+" did not match stress to work");
        }
        helper.succeed();
    }
}
