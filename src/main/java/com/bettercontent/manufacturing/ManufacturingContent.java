package com.bettercontent.manufacturing;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.List;

/** Stable physical component identities; recipes own acquisition and native consumption. */
public final class ManufacturingContent {
    public static final int COMPONENT_CEILING = 12;
    public static final List<String> COMPONENT_IDS = List.of("pla_stock", "pp_stock", "lactide", "propylene",
            "tin_carboxylate_catalyst", "supported_dehydrogenation_catalyst", "coordination_polymerisation_catalyst",
            "lactic_culture", "compressed_iron_valve_blank");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ManufacturingMod.ID);
    public static final List<RegistryObject<Item>> COMPONENTS = COMPONENT_IDS.stream()
            .map(id -> ITEMS.register(id, () -> new Item(new Item.Properties()))).toList();
    static { if (COMPONENT_IDS.size() > COMPONENT_CEILING) throw new IllegalStateException("Manufacturing component budget exceeded"); }
    private ManufacturingContent() {}
}
