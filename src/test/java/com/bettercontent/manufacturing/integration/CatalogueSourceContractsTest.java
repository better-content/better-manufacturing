package com.bettercontent.manufacturing.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Static pack-source contracts only. Runtime recipe selection, native processes and persistence need GameTests. */
class CatalogueSourceContractsTest {
    private static final Path DATA = Path.of("..", "..", "better-content-modpack", "kubejs", "data");

    private static JsonObject recipe(String path) throws IOException {
        try (var reader = Files.newBufferedReader(DATA.resolve(path + ".json"))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject branch(JsonObject conditional, boolean manufacturing) {
        assertEquals("forge:conditional", conditional.get("type").getAsString());
        for (JsonElement element : conditional.getAsJsonArray("recipes")) {
            JsonObject candidate = element.getAsJsonObject();
            boolean positive = false, negative = false;
            for (JsonElement condition : candidate.getAsJsonArray("conditions")) {
                JsonObject object = condition.getAsJsonObject();
                if ("forge:mod_loaded".equals(object.get("type").getAsString())
                    && "better_manufacturing".equals(object.get("modid").getAsString())) positive = true;
                if ("forge:not".equals(object.get("type").getAsString())) {
                    JsonObject value = object.getAsJsonObject("value");
                    if (value != null && "forge:mod_loaded".equals(value.get("type").getAsString())
                        && "better_manufacturing".equals(value.get("modid").getAsString())) negative = true;
                }
            }
            if (manufacturing ? positive : negative) return candidate.getAsJsonObject("recipe");
        }
        fail("Missing " + (manufacturing ? "Manufacturing" : "without-Manufacturing") + " branch");
        return null;
    }

    private static long countItem(JsonObject recipe, String item) {
        JsonObject keys = recipe.getAsJsonObject("key");
        if (keys == null) return 0;
        long count = 0;
        for (JsonElement row : recipe.getAsJsonArray("pattern")) {
            for (char symbol : row.getAsString().toCharArray()) {
                if (symbol == ' ' || !keys.has(String.valueOf(symbol))) continue;
                JsonObject ingredient = keys.getAsJsonObject(String.valueOf(symbol));
                if (ingredient.has("item") && item.equals(ingredient.get("item").getAsString())) count++;
            }
        }
        return count;
    }

    @Test void oc2rMachineCraftsKeepTheirNativeCostsAndNoManufacturingFallback() throws IOException {
        // These nine were full 3x3 grids: a physical part must not replace native material.
        for (String name : List.of("charger", "computer", "disk_drive", "flash_memory_flasher",
                "keyboard", "network_connector", "network_hub", "redstone_interface", "projector")) {
            JsonObject root = recipe("oc2r/recipes/" + name);
            JsonObject paid = branch(root, true);
            JsonObject fallback = branch(root, false);
            assertEquals("create:mechanical_crafting", paid.get("type").getAsString(), name);
            assertEquals("minecraft:crafting_shaped", fallback.get("type").getAsString(), name);
            assertEquals(4, paid.getAsJsonArray("pattern").size(), name);
            assertEquals(1, countItem(paid, "tconstruct:tool_handle"), name);
            assertEquals(1, countItem(paid, "tconstruct:large_plate"), name);
            if (name.equals("charger"))
                assertEquals(1, countItem(paid, "minecraft:light_weighted_pressure_plate"),
                    "Charger must still consume the native pressure plate, not replace it with a TCon plate");
            assertEquals(paid.get("result"), fallback.get("result"), "Output/NBT changed: " + name);
            // An absent optional Manufacturing mod cannot require its physical parts.
            assertEquals(0, countItem(fallback, "tconstruct:tool_handle"), "No-Manufacturing handle leak: " + name);
            assertEquals(0, countItem(fallback, "tconstruct:large_plate"), "No-Manufacturing plate leak: " + name);
        }
    }

    @Test void earlySophisticatedBundlesKeepTheNativeCraftingStationAndPayDisplacedCosts() throws IOException {
        for (String[] route : new String[][] {
                {"sophisticatedstorage", "upgrade_base", "upgrade_base_sophisticatedstorage"},
                {"sophisticatedbackpacks", "upgrade_base", "upgrade_base_sophisticatedbackpacks"},
                {"sophisticatedstorage", "controller", "controller_sophisticatedstorage"},
                {"sophisticatedstorage", "storage_io", "storage_io_sophisticatedstorage"}}) {
            String output = route[0] + ":" + route[1];
            JsonObject finalRecipe = branch(recipe(route[0] + "/recipes/" + route[1]), true);
            JsonObject prep = branch(recipe("better_manufacturing/recipes/sophisticated_cost_bundle/" + route[2]), true);
            assertEquals("minecraft:crafting_shaped", finalRecipe.get("type").getAsString(), output);
            assertEquals("better_manufacturing:sophisticated_cost_bundle", prep.get("type").getAsString(), output);
            assertEquals(output, prep.get("marker").getAsString(), output);
            JsonArray costs = prep.getAsJsonArray("costs");
            assertEquals(3, costs.size(), "Missing displaced native material or one of two parts: " + output);
            Set<String> items = Set.of(costs.get(1).getAsJsonObject().get("item").getAsString(),
                costs.get(2).getAsJsonObject().get("item").getAsString());
            assertEquals(Set.of("tconstruct:tool_handle", "tconstruct:large_plate"), items, output);
            boolean marked = false;
            for (var entry : finalRecipe.getAsJsonObject("key").entrySet()) {
                JsonObject ingredient = entry.getValue().getAsJsonObject();
                if (ingredient.has("type") && "better_manufacturing:sophisticated_cost_bundle".equals(ingredient.get("type").getAsString())
                    && output.equals(ingredient.get("marker").getAsString())) marked = true;
            }
            assertTrue(marked, "Final craft does not consume its exact paid bundle: " + output);
        }
    }

    @Test void beesTierFallbackDoesNotRequireAnAbsentManufacturingSerializer() throws IOException {
        for (String tier : List.of("basic_", "", "advanced_")) {
            for (String kind : List.of("speed", "efficiency", "extraction", "processing")) {
                String name = "gyrofuge_" + tier + kind + "_unit";
                JsonObject nativeRecipe = branch(recipe("complicated_bees/recipes/" + name), false);
                assertEquals("minecraft:crafting_shaped", nativeRecipe.get("type").getAsString(), name);
                assertFalse(nativeRecipe.has("parts"), name);
                assertFalse(nativeRecipe.has("function"), name);
            }
        }
    }

    @Test void clockworkSawPrepNamesTheTwoNativeOutputsWithoutChangingTheNativeWorkBudget() throws IOException {
        for (String[] variant : new String[][] {{"propeller_blade", "3"}, {"wide_propeller_blade", "2"}}) {
            String name = variant[0];
            JsonObject prep = branch(recipe("better_manufacturing/recipes/clockwork_" + name + "_cutting_prep"), true);
            assertEquals("better_manufacturing:clockwork_blade_cutting_prep", prep.get("type").getAsString(), name);
            assertEquals("vs_clockwork:" + name, prep.get("output").getAsString(), name);
            // The effective cutting ID is emitted by 52_clockwork_blade_cutting.js; this
            // test does not claim that KubeJS loaded it or that the saw hook transferred NBT.
        }
    }
}
