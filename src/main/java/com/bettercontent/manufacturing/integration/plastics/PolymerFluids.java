package com.bettercontent.manufacturing.integration.plastics;

import com.bettercontent.manufacturing.ManufacturingMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Tank-only process fluids. No source blocks or bucket items: in particular, propylene is not a placeable gas. */
public final class PolymerFluids {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, ManufacturingMod.ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, ManufacturingMod.ID);

    private PolymerFluids() {}

    private static void fluid(String name, int color, boolean gas) {
        var type = TYPES.register(name, () -> new FluidType(FluidType.Properties.create()
                .density(gas ? -100 : 1050).viscosity(gas ? 100 : 1400).canConvertToSource(false)) {
            @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer) {
                consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions() {
                    @Override public ResourceLocation getStillTexture() { return new ResourceLocation("minecraft:block/water_still"); }
                    @Override public ResourceLocation getFlowingTexture() { return new ResourceLocation("minecraft:block/water_flow"); }
                    @Override public int getTintColor() { return color; }
                });
            }
        });
        RegistryObject<Fluid> source = RegistryObject.create(new ResourceLocation(ManufacturingMod.ID, name), ForgeRegistries.FLUIDS);
        RegistryObject<Fluid> flowing = RegistryObject.create(new ResourceLocation(ManufacturingMod.ID, "flowing_" + name), ForgeRegistries.FLUIDS);
        var properties = new ForgeFlowingFluid.Properties(type, source, flowing);
        FLUIDS.register(name, () -> new ForgeFlowingFluid.Source(properties));
        FLUIDS.register("flowing_" + name, () -> new ForgeFlowingFluid.Flowing(properties));
    }

    static {
        fluid("sugar_solution", 0xffd4a471, false);
        fluid("lactic_acid_broth", 0xffccbe96, false);
        fluid("concentrated_lactic_acid", 0xffe8dcb4, false);
        fluid("dry_lactide", 0xfff1dfb5, false);
        fluid("molten_pla", 0xffede1bf, false);
        fluid("hot_propylene", 0xffc3d0dc, true);
        fluid("propylene_stream", 0xffbbd4dc, true);
        fluid("molten_pp", 0xffb3dce5, false);
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
    }
}
