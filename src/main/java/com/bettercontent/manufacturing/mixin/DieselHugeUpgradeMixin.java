package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.diesel.DieselEngineData;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.jesz.createdieselgenerators.content.diesel_engine.EngineUpgrades;
import com.jesz.createdieselgenerators.content.diesel_engine.huge.HugeDieselEngineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carry the final turbo setting through CDG's native huge-engine install, wrench and break paths. */
@Mixin(targets = "com.jesz.createdieselgenerators.content.diesel_engine.huge.HugeDieselEngineBlock", remap = false)
public abstract class DieselHugeUpgradeMixin {
    @Inject(method = "lambda$use$0", at = @At("HEAD"))
    private static void manufacturing$installTurbo(EngineUpgrades upgrade, Player player, ItemStack held, Level level,
                                                   BlockPos pos, HugeDieselEngineBlockEntity engine, CallbackInfo ci) {
        if (!level.isClientSide && upgrade == EngineUpgrades.TURBOCHARGER
            && engine.getUpgrade() == EngineUpgrades.EMPTY && upgrade.canAddOn(engine))
            ((DieselEngineData) engine).manufacturing$setTurbo(DieselEngineData.addon(held));
    }

    @Redirect(method = "lambda$onWrenched$1", at = @At(value = "INVOKE", target = "Lcom/jesz/createdieselgenerators/content/diesel_engine/EngineUpgrades;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack manufacturing$returnTurbo(EngineUpgrades upgrade, UseOnContext context, HugeDieselEngineBlockEntity engine) {
        var item = upgrade.getItem();
        var data = (DieselEngineData) engine;
        if (upgrade == EngineUpgrades.TURBOCHARGER && data.manufacturing$turbo() != null)
            SettingsNbt.write(item.getOrCreateTag(), data.manufacturing$turbo());
        data.manufacturing$setTurbo(null);
        return item;
    }

    @Redirect(method = "lambda$onRemove$2", at = @At(value = "INVOKE", target = "Lcom/jesz/createdieselgenerators/content/diesel_engine/EngineUpgrades;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack manufacturing$dropTurbo(EngineUpgrades upgrade, Level level, BlockPos pos, HugeDieselEngineBlockEntity engine) {
        var item = upgrade.getItem();
        var data = (DieselEngineData) engine;
        if (upgrade == EngineUpgrades.TURBOCHARGER && data.manufacturing$turbo() != null)
            SettingsNbt.write(item.getOrCreateTag(), data.manufacturing$turbo());
        return item;
    }
}
