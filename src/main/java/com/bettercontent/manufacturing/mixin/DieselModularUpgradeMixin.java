package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.diesel.DieselEngineData;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.jesz.createdieselgenerators.content.diesel_engine.EngineUpgrades;
import com.jesz.createdieselgenerators.content.diesel_engine.modular.ModularDieselEngineBlockEntity;
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

/** CDG's native modular upgrade is attached to the clicked segment, not reassigned to a controller. */
@Mixin(targets = "com.jesz.createdieselgenerators.content.diesel_engine.modular.ModularDieselEngineBlock", remap = false)
public abstract class DieselModularUpgradeMixin {
    @Inject(method = "lambda$use$0", at = @At("HEAD"))
    private static void manufacturing$installTurbo(EngineUpgrades upgrade, Player player, ItemStack held, Level level,
                                                   BlockPos pos, ModularDieselEngineBlockEntity engine, CallbackInfo ci) {
        var controller = engine.getControllerBE();
        if (!level.isClientSide && controller != null && upgrade == EngineUpgrades.TURBOCHARGER
            && controller.getUpgrade() == EngineUpgrades.EMPTY && upgrade.canAddOn(engine))
            ((DieselEngineData) engine).manufacturing$setTurbo(DieselEngineData.addon(held));
    }
    @Redirect(method = "lambda$onWrenched$1", at = @At(value = "INVOKE", target = "Lcom/jesz/createdieselgenerators/content/diesel_engine/EngineUpgrades;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack manufacturing$returnTurbo(EngineUpgrades upgrade, UseOnContext context, ModularDieselEngineBlockEntity engine) {
        var item = upgrade.getItem();
        var controller = engine.getControllerBE();
        if (controller != null) {
            var data = (DieselEngineData) controller;
            if (upgrade == EngineUpgrades.TURBOCHARGER && data.manufacturing$turbo() != null)
                SettingsNbt.write(item.getOrCreateTag(), data.manufacturing$turbo());
            data.manufacturing$setTurbo(null);
        }
        return item;
    }
    @Redirect(method = "lambda$onRemove$2", at = @At(value = "INVOKE", target = "Lcom/jesz/createdieselgenerators/content/diesel_engine/EngineUpgrades;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack manufacturing$dropTurbo(EngineUpgrades upgrade, Level level, BlockPos pos, ModularDieselEngineBlockEntity engine) {
        var item = upgrade.getItem();
        if (upgrade == EngineUpgrades.TURBOCHARGER && ((DieselEngineData) engine).manufacturing$turbo() != null)
            SettingsNbt.write(item.getOrCreateTag(), ((DieselEngineData) engine).manufacturing$turbo());
        return item;
    }
}
