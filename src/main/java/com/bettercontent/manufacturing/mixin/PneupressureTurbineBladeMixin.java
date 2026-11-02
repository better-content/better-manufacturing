package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneupressure.PneupressureSettings;
import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import it.unimi.dsi.fastutil.ints.IntList;
import me.desht.pneumaticcraft.common.recipes.machine.PressureChamberRecipeImpl;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.HashMap;

/** Capture actual native chamber inputs before extraction; never change pressure, progress, or native output quantities. */
@Mixin(value = PressureChamberRecipeImpl.class, remap = false)
public abstract class PneupressureTurbineBladeMixin {
    @Unique private final ThreadLocal<MachineSettings> pneupressure$blade = new ThreadLocal<>();
    @Inject(method = "craftRecipe", at = @At("HEAD"))
    private void pneupressure$before(IItemHandler inventory, IntList slots, boolean simulate, CallbackInfoReturnable<NonNullList<ItemStack>> cir) {
        pneupressure$blade.remove();
        if (!((PressureChamberRecipeImpl) (Object) this).getId().toString().equals("pneumaticcraft:pressure_chamber/turbine_blade")) return;
        var inputs = new HashMap<String, ItemStack>();
        for (int i = 0; i < slots.size(); i++) {
            var stack = inventory.getStackInSlot(slots.getInt(i));
            var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null) continue;
            if (id.toString().equals("tconstruct:small_blade")) inputs.put("body", stack.copy());
            if (id.toString().equals("tconstruct:tough_handle")) inputs.put("handle", stack.copy());
        }
        if (inputs.size() == 2) {
            try { pneupressure$blade.set(PneupressureSettings.bake("pneumaticcraft:turbine_blade", PneupressureSettings.ROTATION, inputs)); }
            catch (IllegalArgumentException unsupported) { /* Native pressure chamber still decides eligibility. */ }
        }
    }
    @Inject(method = "craftRecipe", at = @At("RETURN"), cancellable = true)
    private void pneupressure$after(IItemHandler inventory, IntList slots, boolean simulate, CallbackInfoReturnable<NonNullList<ItemStack>> cir) {
        var settings = pneupressure$blade.get();
        pneupressure$blade.remove();
        if (settings == null || cir.getReturnValue().isEmpty()) return;
        var copy = NonNullList.<ItemStack>create();
        for (var stack : cir.getReturnValue()) {
            var result = stack.copy();
            var id = ForgeRegistries.ITEMS.getKey(result.getItem());
            if (id != null && id.toString().equals(settings.output())) SettingsNbt.write(result.getOrCreateTag(), settings);
            copy.add(result);
        }
        cir.setReturnValue(copy);
    }
}
