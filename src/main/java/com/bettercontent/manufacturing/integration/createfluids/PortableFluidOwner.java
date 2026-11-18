package com.bettercontent.manufacturing.integration.createfluids;

import com.simibubi.create.content.contraptions.actors.psi.PortableFluidInterfaceBlockEntity;

/** Typed owner carried by the pinned Create portable handler's existing Mixin. */
public interface PortableFluidOwner {
    PortableFluidInterfaceBlockEntity manufacturing$owner();
}
