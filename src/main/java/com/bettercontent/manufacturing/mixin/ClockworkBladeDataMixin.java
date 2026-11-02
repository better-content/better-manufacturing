package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkBladeRating;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.BladeData;
import java.util.ArrayList;

@Mixin(targets = "org.valkyrienskies.clockwork.content.contraptions.propeller.blades.BladeData", remap = false)
public abstract class ClockworkBladeDataMixin implements ClockworkBladeRating {
    @Unique private double manufacturing$bladeResponse = 1;
    @Override public double manufacturing$bladeResponse() { return manufacturing$bladeResponse; }
    @Override public void manufacturing$bladeResponse(double response) {
        manufacturing$bladeResponse = Double.isFinite(response) && response >= .75 && response <= 1.5 ? response : 1;
    }

    /** Native deserializer already defines blade width, pitch and length; attach rating in identical list order. */
    @Mixin(targets = "org.valkyrienskies.clockwork.content.contraptions.propeller.blades.BladeData$Companion", remap = false)
    public static abstract class FromController {
        @Inject(method = "fromTag", at = @At("RETURN"))
        private void manufacturing$bladeRatings(CompoundTag tag, CallbackInfoReturnable<ArrayList<BladeData>> callback) {
            if (tag == null || !tag.contains("Blades")) return;
            NonNullList<ItemStack> nativeSlots = NonNullList.withSize(8, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag.getCompound("Blades"), nativeSlots);
            int blade = 0;
            for (var stack : nativeSlots) {
                if (stack.isEmpty()) continue;
                if (blade >= callback.getReturnValue().size()) return;
                var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id != null) {
                    var settings = SettingsNbt.read(stack.getTag(), id.toString());
                    Object data = callback.getReturnValue().get(blade);
                    if (data instanceof ClockworkBladeRating rated)
                        rated.manufacturing$bladeResponse(
                            settings == null ? 1 : settings.properties().getOrDefault("actuator_response", 1d));
                }
                blade++;
            }
        }
    }
}
