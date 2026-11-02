package com.bettercontent.manufacturing.integration.pneumatictools;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.common.item.DrillBitItem;
import me.desht.pneumaticcraft.common.item.JackHammerItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/** Settings-only runtime; physical TCon heads and their wear remain Better Compat Fixes-owned. */
public final class PneumatictoolsRuntime {
    public static final String BIT_STACK_TAG = "better_manufacturing:drill_bit";
    private static final String NATIVE_TCON_HEAD = "BetterContentTconPickHead";

    private PneumatictoolsRuntime() {}

    public static MachineSettings settings(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return null;
        MachineSettings settings = SettingsNbt.read(stack.getTag(), id.toString());
        if (settings == null || !settings.properties().keySet().equals(Set.of("work_rate"))) return null;
        double rate = settings.properties().get("work_rate");
        return Double.isFinite(rate) && rate >= .75 && rate <= 1.5 ? settings : null;
    }

    public static boolean hasPhysicalHead(ItemStack jackhammer) {
        // This is a precedence guard only, not ownership of the installed head or wear payload.
        return jackhammer.hasTag() && jackhammer.getTag().contains(NATIVE_TCON_HEAD, Tag.TAG_COMPOUND);
    }

    public static ItemStack installedBit(ItemStack jackhammer) {
        JackHammerItem.DrillBitHandler handler = JackHammerItem.getDrillBitHandler(jackhammer);
        return handler == null ? ItemStack.EMPTY : handler.getStackInSlot(0);
    }

    public static double rate(ItemStack jackhammer) {
        if (hasPhysicalHead(jackhammer)) return 1;
        ItemStack bit = installedBit(jackhammer);
        MachineSettings bitSettings = bit.getItem() instanceof DrillBitItem ? settings(bit) : null;
        // Bit rate replaces native bit-grade speed. A rated tool construction is a fallback,
        // never a second multiplier on top of a rated bit or a physical/wearing TCon head.
        MachineSettings selected = bitSettings == null ? settings(jackhammer) : bitSettings;
        return selected == null ? 1 : selected.rate();
    }

    public static boolean canHarvest(ItemStack jackhammer, BlockState block) {
        if (hasPhysicalHead(jackhammer)) return true;
        MachineSettings tool = settings(jackhammer);
        ItemStack bit = installedBit(jackhammer);
        MachineSettings bitSettings = bit.getItem() instanceof DrillBitItem ? settings(bit) : null;
        return correctTier(tool, block) && correctTier(bitSettings, block);
    }

    private static boolean correctTier(MachineSettings settings, BlockState block) {
        if (settings == null || settings.harvestTier() == null) return true;
        Tier tier;
        ResourceLocation id = ResourceLocation.tryParse(settings.harvestTier());
        if (id == null) return true;
        tier = TierSortingRegistry.byName(id);
        return tier == null || TierSortingRegistry.isCorrectTierForDrops(tier, block);
    }

    public static boolean isConfiguredBit(ItemStack bit) {
        return bit.getItem() instanceof DrillBitItem && settings(bit) != null;
    }

    public static ItemStack readStoredBit(ItemStack jackhammer) {
        if (!jackhammer.hasTag() || !jackhammer.getTag().contains(BIT_STACK_TAG, Tag.TAG_COMPOUND)) return ItemStack.EMPTY;
        ItemStack bit = ItemStack.of(jackhammer.getTag().getCompound(BIT_STACK_TAG));
        // Ignore malformed/outdated full stacks and let native enum restoration handle fallback.
        return bit.getCount() == 1 && isConfiguredBit(bit) ? bit : ItemStack.EMPTY;
    }

    public static void saveStoredBit(ItemStack jackhammer, ItemStack bit) {
        CompoundTag tag = jackhammer.getOrCreateTag();
        if (isConfiguredBit(bit) && bit.getCount() == 1) tag.put(BIT_STACK_TAG, bit.save(new CompoundTag()));
        else tag.remove(BIT_STACK_TAG);
    }
}
