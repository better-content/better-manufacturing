package com.bettercontent.manufacturing.integration.pneutransfer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Only the plate-paid, same-item lapis preparation may drive the matrix thermo route. */
public final class PreparedThermoInput {
    private PreparedThermoInput() {}

    public static boolean isUpgradeMatrixLapis(ItemStack stack) {
        return stack.is(Items.LAPIS_LAZULI) && stack.hasTag()
            && stack.getTag().getCompound("better_manufacturing").getInt("upgrade_matrix_lapis") == 1;
    }
}
