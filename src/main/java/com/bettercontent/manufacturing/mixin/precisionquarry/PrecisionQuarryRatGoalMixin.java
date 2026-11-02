package com.bettercontent.manufacturing.mixin.precisionquarry;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.precisionquarry.PrecisionQuarrySettings;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.misc.RatUpgradeUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The installed upgrade stack already persists with the native rat inventory. Never replace native drops. */
@Mixin(targets = "com.github.alexthe666.rats.server.entity.ai.goal.harvest.RatQuarryGoal", remap = false)
public abstract class PrecisionQuarryRatGoalMixin {
    @Shadow @Final private TamedRat rat;
    private MachineSettings manufacturing$installedHead() {
        // Match Rats' actual upgrade lookup (equipment slots and embedded combined upgrades),
        // rather than borrowing a loose quarry item from an unrelated inventory slot.
        var quarryUpgrade = ForgeRegistries.ITEMS.getValue(new ResourceLocation("rats", "rat_upgrade_quarry"));
        if (quarryUpgrade == null) return null;
        ItemStack installed = RatUpgradeUtils.getUpgrade(rat, quarryUpgrade);
        return PrecisionQuarrySettings.read(installed.getTag(), "rats:rat_upgrade_quarry");
    }
    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0)
    private int manufacturing$breakWork(int nativeTicks) {
        var settings = manufacturing$installedHead();
        return settings == null ? nativeTicks : Math.max(1, (int) Math.round(nativeTicks / settings.rate()));
    }
    @Inject(method = "canMineBlock", at = @At("RETURN"), cancellable = true)
    private void manufacturing$nativeTier(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return; // never override RatUtils and the quarry ignore tags
        var settings = manufacturing$installedHead();
        if (settings == null || settings.harvestTier() == null || settings.properties().getOrDefault("hardness_limit", 2.0) >= 2) return;
        var tierId = ResourceLocation.tryParse(settings.harvestTier());
        if (tierId == null) return;
        var tier = TierSortingRegistry.byName(tierId);
        var block = rat.level().getBlockState(pos);
        if (tier != null && block.requiresCorrectToolForDrops() && !TierSortingRegistry.isCorrectTierForDrops(tier, block))
            cir.setReturnValue(false);
    }
}
