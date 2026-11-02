package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.diesel.DieselEngineData;
import com.jesz.createdieselgenerators.content.diesel_engine.EngineUpgrades;
import com.jesz.createdieselgenerators.content.diesel_engine.normal.DieselEngineBlockEntity;
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

/** Copy the configured turbo rating at the exact native install path, before its stack is consumed. */
@Mixin(targets = "com.jesz.createdieselgenerators.content.diesel_engine.normal.DieselEngineBlock", remap = false)
public abstract class DieselNormalUpgradeMixin {
    @Inject(method = "lambda$use$2", at = @At("HEAD"))
    private static void manufacturing$installTurbo(EngineUpgrades upgrade, Player player, ItemStack held, Level level,
                                                   BlockPos pos, DieselEngineBlockEntity engine, CallbackInfo ci) {
        if (!level.isClientSide && upgrade == EngineUpgrades.TURBOCHARGER && engine.getUpgrade() == EngineUpgrades.EMPTY
            && upgrade.canAddOn(engine)) ((DieselEngineData) engine).manufacturing$setTurbo(DieselEngineData.addon(held));
    }
    @Redirect(method = "lambda$onWrenched$0", at = @At(value = "INVOKE", target = "Lcom/jesz/createdieselgenerators/content/diesel_engine/EngineUpgrades;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack manufacturing$returnTurbo(EngineUpgrades upgrade, UseOnContext context, DieselEngineBlockEntity engine) {
        var item = upgrade.getItem();
        if (upgrade == EngineUpgrades.TURBOCHARGER && ((DieselEngineData) engine).manufacturing$turbo() != null)
            com.bettercontent.manufacturing.persistence.SettingsNbt.write(item.getOrCreateTag(), ((DieselEngineData) engine).manufacturing$turbo());
        ((DieselEngineData) engine).manufacturing$setTurbo(null);
        return item;
    }
    @Redirect(method = "lambda$onRemove$3", at = @At(value = "INVOKE", target = "Lcom/jesz/createdieselgenerators/content/diesel_engine/EngineUpgrades;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack manufacturing$dropTurbo(EngineUpgrades upgrade, Level level, BlockPos pos, DieselEngineBlockEntity engine) {
        var item = upgrade.getItem();
        if (upgrade == EngineUpgrades.TURBOCHARGER && ((DieselEngineData) engine).manufacturing$turbo() != null)
            com.bettercontent.manufacturing.persistence.SettingsNbt.write(item.getOrCreateTag(), ((DieselEngineData) engine).manufacturing$turbo());
        return item;
    }
}
