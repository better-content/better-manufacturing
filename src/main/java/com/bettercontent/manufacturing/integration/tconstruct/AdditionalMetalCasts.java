package com.bettercontent.manufacturing.integration.tconstruct;

import com.bettercontent.manufacturing.ManufacturingMod;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Bronze and brass alternatives for TCon's existing reusable cast shapes, not new part types. */
public final class AdditionalMetalCasts {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ManufacturingMod.ID);
    // Exactly the reusable shapes in the pinned TCon casts/multi_use tag (arrow is gold-only).
    public static final List<String> SHAPES = List.of(
        "ingot", "nugget", "gem", "rod", "repair_kit", "plate", "gear", "coin", "wire",
        "pick_head", "small_axe_head", "small_blade", "adze_head", "hammer_head",
        "broad_axe_head", "broad_blade", "large_plate", "tool_binding", "tough_binding",
        "tool_handle", "tough_handle", "bow_limb", "bow_grip", "helmet_plating",
        "chestplate_plating", "leggings_plating", "boots_plating", "maille");

    static {
        for (String metal : List.of("bronze", "brass")) {
            for (String shape : SHAPES) {
                ITEMS.register(metal + "_" + shape + "_cast", () -> new Item(new Item.Properties().stacksTo(64)));
            }
        }
    }

    private AdditionalMetalCasts() {}
}
