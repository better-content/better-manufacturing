package com.bettercontent.manufacturing.nativeparts.fluidslogistics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bettercontent.manufacturing.integration.createfluids.FluidNativeRates;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guard; native transformed-handler identity and conservation need a provider fixture. */
class PortableFluidOwnerContractTest {
    @Test void unknownHandlerKeepsTheNativeUnlimitedFallback() {
        Object unknown = new Object();
        assertEquals(Integer.MAX_VALUE, FluidNativeRates.interfaceLimit(unknown));
        assertDoesNotThrow(() -> FluidNativeRates.transferred(unknown, 500));
        assertEquals(Integer.MAX_VALUE, FluidNativeRates.interfaceLimit(unknown));
    }

    @Test void typedConstructorCaptureReplacesSyntheticOwnerReflectionWithoutChangingNativeHooks() throws Exception {
        Path source = Path.of("src/main/java/com/bettercontent/manufacturing");
        String rates = Files.readString(source.resolve("integration/createfluids/FluidNativeRates.java"));
        String hooks = Files.readString(source.resolve("mixin/createfluids/FluidTransferMixins.java"));
        assertFalse(rates.contains("getDeclaredField("));
        assertFalse(rates.contains("this$0"));
        assertTrue(rates.contains("handler instanceof PortableFluidOwner"));
        assertTrue(rates.contains("Integer.MAX_VALUE"));
        assertTrue(hooks.contains("implements PortableFluidOwner"));
        assertTrue(hooks.contains("@Inject(method = \"<init>(Lcom/simibubi/create/content/contraptions/actors/psi/PortableFluidInterfaceBlockEntity;Lnet/minecraftforge/fluids/capability/IFluidHandler;)V\""));
        assertTrue(hooks.contains("manufacturing$outerOwner = owner"));
        assertTrue(hooks.contains("if (action.execute()) FluidNativeRates.transferred(this, cir.getReturnValue())"));
    }
}
