package com.bettercontent.manufacturing.integration.createactors;

import com.simibubi.create.AllBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** A persistent actor BE for Create's otherwise BE-less plough. */
public final class CreateActorsBlocks {
    private CreateActorsBlocks() {}
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "better_manufacturing");
    public static final RegistryObject<BlockEntityType<CreateActorsPloughBlockEntity>> PLOUGH = TYPES.register(
        "plough_settings", () -> BlockEntityType.Builder.of(CreateActorsPloughBlockEntity::new,
            AllBlocks.MECHANICAL_PLOUGH.get()).build(null));

    public static void register(IEventBus bus) { TYPES.register(bus); }
}
