package com.bettercontent.manufacturing.integration.emi;

/** IDs for ordinary configured crafting previews; no mesh recovery category is registered. */
public final class MeshEmiPlugin {
    private MeshEmiPlugin() {}
    public static net.minecraft.resources.ResourceLocation variantId(net.minecraft.resources.ResourceLocation recipe, String material) {
        // Encoding, rather than dropping the variant separator, keeps rock#stone and
        // other TCon material variants distinct without interpreting their live traits.
        return new net.minecraft.resources.ResourceLocation("better_manufacturing", "variant/" + recipe.getNamespace()
            + "/" + recipe.getPath() + "/" + java.util.HexFormat.of().formatHex(
                material.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    /** Historical fixture identifier only; it does not register a recovery page. */
    public static net.minecraft.resources.ResourceLocation id(String route, String mesh, String material) {
        return new net.minecraft.resources.ResourceLocation("better_manufacturing", "/" + route + "/" + mesh + "/"
            + java.util.HexFormat.of().formatHex(material.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
