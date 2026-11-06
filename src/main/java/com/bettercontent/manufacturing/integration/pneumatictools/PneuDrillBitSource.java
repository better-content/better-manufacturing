package com.bettercontent.manufacturing.integration.pneumatictools;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.api.NumericEvaluator;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Set;

/** A consumed head travels with the original native material through PNCR processing. */
public final class PneuDrillBitSource {
    public static final String MARKER = "better_manufacturing:drill_bit_source";
    public static final String DIAMOND = "pneumaticcraft:drill_bit_diamond";
    public static final String NETHERITE = "pneumaticcraft:drill_bit_netherite";
    public static final String UNASSEMBLED = "pneumaticcraft:unassembled_netherite_drill_bit";
    private static final Set<String> THERMO = Set.of("pneumaticcraft:drill_bit_iron",
        "pneumaticcraft:drill_bit_compressed_iron", DIAMOND);
    private PneuDrillBitSource() {}

    public static boolean thermoInput(ItemStack stack, String output) {
        if (!THERMO.contains(output)) return false;
        String material = switch (output) {
            case "pneumaticcraft:drill_bit_iron" -> "forge:ingots/iron";
            case "pneumaticcraft:drill_bit_compressed_iron" -> "forge:ingots/compressed_iron";
            default -> "forge:storage_blocks/diamond";
        };
        return stack.is(TagKey.create(Registries.ITEM, new ResourceLocation(material))) && prepared(stack, output);
    }

    public static boolean assemblyInput(ItemStack stack) {
        return id(stack).equals(UNASSEMBLED) && prepared(stack, DIAMOND);
    }

    private static boolean prepared(ItemStack stack, String output) {
        if (stack.isEmpty() || !stack.hasTag() || !stack.getTag().contains(MARKER, Tag.TAG_STRING)
            || !stack.getTag().getString(MARKER).equals(output)) return false;
        MachineSettings settings = SettingsNbt.read(stack.getTag(), output);
        return valid(settings, output);
    }

    public static boolean valid(MachineSettings settings, String output) {
        return settings != null && settings.output().equals(output)
            && settings.properties().keySet().equals(Set.of("work_rate"))
            && settings.numericInputs() != null
            && settings.numericInputs().function().equals(PneumatictoolsSettings.FUNCTION)
            && settings.numericInputs().parts().keySet().equals(Set.of("head"));
    }

    public static ItemStack thermoOutput(ItemStack source, ItemStack nativeOutput) {
        String output = id(nativeOutput);
        if (!thermoInput(source, output)) return ItemStack.EMPTY;
        ItemStack result = nativeOutput.copy();
        var tag = source.getTag().copy();
        tag.remove(MARKER);
        result.setTag(tag);
        return result;
    }

    public static ItemStack assemblyOutput(ItemStack source, ItemStack nativeOutput) {
        if (!id(nativeOutput).equals(NETHERITE) || !assemblyInput(source)) return ItemStack.EMPTY;
        MachineSettings diamond = SettingsNbt.read(source.getTag(), DIAMOND);
        if (!valid(diamond, DIAMOND)) return ItemStack.EMPTY;
        try {
            var netherite = new MachineSettings(NETHERITE,
                NumericEvaluator.evaluate(NETHERITE, diamond.numericInputs(), diamond.harvestTier()),
                diamond.materials(), diamond.harvestTier(), diamond.numericInputs());
            ItemStack result = nativeOutput.copy();
            var tag = source.getTag().copy();
            tag.remove(MARKER);
            SettingsNbt.write(tag, netherite);
            result.setTag(tag);
            return result;
        } catch (IllegalArgumentException invalid) { return ItemStack.EMPTY; }
    }

    public static String id(ItemStack stack) {
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }
}
