package com.bettercontent.manufacturing.integration.createfluids;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.contraptions.actors.psi.PortableFluidInterfaceBlockEntity;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.Field;

/** Native capacity/transfer helpers; never alter recipes, fluid types or output quantities. */
public final class FluidNativeRates {
    private static final Field PORTABLE_OWNER = findPortableOwner();
    private FluidNativeRates() {}
    private static Field findPortableOwner() {
        try {
            var field = PortableFluidInterfaceBlockEntity.InterfaceFluidHandler.class.getDeclaredField("this$0");
            return field.trySetAccessible() ? field : null;
        } catch (ReflectiveOperationException | SecurityException unavailable) {
            return null; // Unknown Create implementation retains its native capability unchanged.
        }
    }
    private static Object owner(Object handler) {
        if (handler instanceof PortableFluidInterfaceBlockEntity) return handler;
        if (PORTABLE_OWNER == null || !(handler instanceof PortableFluidInterfaceBlockEntity.InterfaceFluidHandler)) return null;
        try { return PORTABLE_OWNER.get(handler); }
        catch (ReflectiveOperationException | SecurityException unavailable) { return null; }
    }
    public static double rate(Object block, String property) {
        var variant = FluidVariantBehaviour.get(block);
        return variant == null ? 1 : variant.rate(property);
    }
    public static int duration(Object block, int baseline) {
        return Math.max(6, Math.round((float) (baseline / rate(block, "fluid_rate"))));
    }
    private static int ratedInterfaceLimit(FluidVariantBehaviour variant) {
        if (variant == null || variant.settings() == null || !variant.settings().properties().containsKey("fluid_rate"))
            return Integer.MAX_VALUE; // Missing/invalid records use exactly Create's native handler.
        return Math.max(1, (int) Math.round(1000 * variant.rate("fluid_rate")));
    }
    public static int interfaceLimit(Object handler) {
        var variant = FluidVariantBehaviour.get(owner(handler));
        return variant == null ? Integer.MAX_VALUE : variant.remainingTransfer(ratedInterfaceLimit(variant));
    }
    public static void transferred(Object handler, int actualAmount) {
        var variant = FluidVariantBehaviour.get(owner(handler));
        if (variant != null) variant.recordTransfer(actualAmount, ratedInterfaceLimit(variant));
    }
    public static FluidStack capped(FluidStack input, int limit) {
        if (input.isEmpty() || input.getAmount() <= limit) return input;
        var copy = input.copy(); copy.setAmount(limit); return copy;
    }
    public static void resizeBasin(BasinBlockEntity basin) {
        var variant = FluidVariantBehaviour.get(basin);
        if (variant == null || variant.settings() == null || !variant.settings().properties().containsKey("fluid_capacity"))
            return; // Do not touch Create's native buffers for unconfigured/invalid basins.
        int capacity = (int) Math.round(1000 * variant.rate("fluid_capacity"));
        for (boolean side : new boolean[]{false, true}) {
            var behaviour = basin.getTanks().get(side);
            if (behaviour != null) behaviour.forEach(segment ->
                ((com.bettercontent.manufacturing.mixin.createfluids.FluidCapacityMixins.TankSegmentAccess) (Object) segment)
                    .manufacturing$fluidTank().setCapacity(capacity));
        }
    }
}
