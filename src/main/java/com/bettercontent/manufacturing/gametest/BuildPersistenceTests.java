package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingContent;
import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.api.*;
import com.bettercontent.manufacturing.persistence.BuildNbt;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.Map;

@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class BuildPersistenceTests {
    private static final String CHASSIS = "create:mechanical_drill", FUNCTION = "better_manufacturing:cutting";
    private static MachineBuild sample() {
        return new MachineBuild(MachineBuild.VERSION, CHASSIS, FUNCTION, Map.of("head", new ComponentProvenance("tconstruct:pick_head",
            "tconstruct:iron#example", "tconstruct:head", 180, true, Map.of("better_compat_fixes:jackhammer_wear", "23"))));
    }
    @GameTest(template = "empty")
    public static void roundTrip(GameTestHelper helper) {
        CompoundTag holder = new CompoundTag(); holder.putInt("UnrelatedNativeData", 87);
        BuildNbt.write(holder, sample());
        var result = BuildNbt.read(holder.copy(), CHASSIS, FUNCTION);
        helper.assertTrue(result.valid() && result.build().equals(sample()), "Build/variant/wear did not round trip");
        helper.assertTrue(holder.getInt("UnrelatedNativeData") == 87, "Native data was modified");
        var qualified = new MachineBuild(MachineBuild.VERSION, CHASSIS, FUNCTION, sample().components(), java.util.Set.of("test:access"));
        BuildNbt.write(holder, qualified);
        helper.assertTrue(BuildNbt.read(holder, CHASSIS, FUNCTION).build().equals(qualified), "Qualifications did not round trip");
        var ids = holder.getCompound(BuildNbt.KEY).getList("qualifications", net.minecraft.nbt.Tag.TAG_STRING);
        ids.add(net.minecraft.nbt.StringTag.valueOf("test:access"));
        var invalid = holder.copy();
        helper.assertTrue(!BuildNbt.read(holder, CHASSIS, FUNCTION).valid() && holder.equals(invalid), "Duplicate qualifications were normalized or mutated");
        BuildNbt.write(holder, sample());
        holder.getCompound(BuildNbt.KEY).putInt("version", 1);
        holder.getCompound(BuildNbt.KEY).remove("qualifications");
        holder.getCompound(BuildNbt.KEY).putString("function", "better_manufacturing:create/cutting");
        var obsolete = holder.copy();
        var rejected = BuildNbt.read(holder, CHASSIS, "better_manufacturing:create/cutting");
        helper.assertTrue(rejected.present() && !rejected.valid() && holder.equals(obsolete),
            "Removed v1 Drill data was accepted or mutated");
        holder.getCompound(BuildNbt.KEY).put("qualifications", new net.minecraft.nbt.ListTag());
        obsolete = holder.copy();
        helper.assertTrue(!BuildNbt.read(holder, CHASSIS, "better_manufacturing:create/cutting").valid()
            && holder.equals(obsolete), "Obsolete record with extra fields was accepted or mutated");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void unknownVersionIsPreserved(GameTestHelper helper) {
        CompoundTag holder = new CompoundTag(); BuildNbt.write(holder, sample());
        holder.getCompound(BuildNbt.KEY).putInt("version", 999);
        CompoundTag before = holder.copy();
        var result = BuildNbt.read(holder, CHASSIS, FUNCTION);
        helper.assertTrue(result.present() && !result.valid(), "Unknown version became a valid stock build");
        helper.assertTrue(holder.equals(before), "Unknown provenance was destroyed");
        holder.putString(BuildNbt.KEY, "invalid"); before = holder.copy();
        helper.assertTrue(!BuildNbt.read(holder, CHASSIS, FUNCTION).valid() && holder.equals(before), "Malformed tag was mutated or accepted");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void stockAndChassisChecks(GameTestHelper helper) {
        var stock = BuildNbt.read(new CompoundTag(), CHASSIS, FUNCTION);
        helper.assertTrue(stock.valid() && !stock.present() && !stock.build().hasProvenance(), "Stock gained manufactured salvage credit");
        CompoundTag holder = new CompoundTag(); BuildNbt.write(holder, sample());
        helper.assertTrue(!BuildNbt.read(holder, "create:mechanical_saw", FUNCTION).valid(), "Wrong chassis was accepted");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void componentBudget(GameTestHelper helper) {
        helper.assertTrue(ManufacturingContent.COMPONENTS.size() == 12
            && java.util.Set.copyOf(ManufacturingContent.COMPONENT_IDS).equals(java.util.Set.of(
                "pla_stock", "pp_stock", "lactide", "propylene", "tin_carboxylate_catalyst",
                "supported_dehydrogenation_catalyst", "coordination_polymerisation_catalyst",
                "lactic_culture", "compressed_iron_valve_blank", "dense_cell_blank",
                "conductive_casing_blank", "mellarium_temp_blank")), "Physical component roster changed");
        helper.assertTrue(ManufacturingContent.COMPONENTS.size() <= ManufacturingContent.COMPONENT_CEILING,
            "Component budget exceeded");
        helper.assertTrue(ManufacturingContent.COMPONENTS.stream().allMatch(r -> r.isPresent()), "Missing component registrations");
        long ownBlocks = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKeys().stream()
            .filter(id -> id.getNamespace().equals(ManufacturingMod.ID)).count();
        helper.assertTrue(ownBlocks == 0, "Manufacturing introduced a block");
        helper.succeed();
    }
}
