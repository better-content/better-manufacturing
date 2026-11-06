package com.bettercontent.manufacturing.integration.emi;

/** IDs for ordinary configured crafting previews; no mesh recovery category is registered. */
public final class MeshEmiPlugin {
    private MeshEmiPlugin() {}
    /** Historical fixture identifier only; it does not register a recovery page. */
    public static net.minecraft.resources.ResourceLocation id(String route, String mesh, String material) {
        return new net.minecraft.resources.ResourceLocation("better_manufacturing", "/" + route + "/" + mesh + "/"
            + java.util.HexFormat.of().formatHex(material.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
