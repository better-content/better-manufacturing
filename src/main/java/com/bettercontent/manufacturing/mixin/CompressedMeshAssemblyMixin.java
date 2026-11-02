package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve only the baked screen setting through the *native* eight-stage water-filling assembly. */
@Mixin(value = SequencedAssemblyRecipe.class, remap = false)
public abstract class CompressedMeshAssemblyMixin {
    @Shadow public abstract net.minecraft.resources.ResourceLocation getId();
    @Inject(method = "advance", at = @At("RETURN"))
    private void manufacturing$meshSettings(ItemStack input, CallbackInfoReturnable<ItemStack> result) {
        if (!getId().toString().equals("compressedcreativity:sequenced_assembly/mesh_splashing")) return;
        ItemStack output = result.getReturnValue();
        if (output.isEmpty()) return; // Native random result/sequence is authoritative.
        var inputId = ForgeRegistries.ITEMS.getKey(input.getItem());
        var outputId = ForgeRegistries.ITEMS.getKey(output.getItem());
        if (inputId == null || outputId == null || !java.util.Set.of("compressedcreativity:mesh_woven", "compressedcreativity:incomplete_mesh_splashing").contains(inputId.toString())
            || !java.util.Set.of("compressedcreativity:incomplete_mesh_splashing", "compressedcreativity:mesh_splashing").contains(outputId.toString())) return;
        var existing = SettingsNbt.read(input.getTag(), inputId.toString());
        if (existing == null || !existing.properties().keySet().equals(java.util.Set.of("airflow"))) return;
        double flow = existing.properties().get("airflow");
        if (!Double.isFinite(flow) || flow < .75 || flow > 1.5) return;
        SettingsNbt.write(output.getOrCreateTag(), new MachineSettings(outputId.toString(), java.util.Map.of("airflow", flow), existing.materials(), null));
    }
}
