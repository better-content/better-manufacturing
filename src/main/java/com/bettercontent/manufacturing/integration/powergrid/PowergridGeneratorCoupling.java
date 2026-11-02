package com.bettercontent.manufacturing.integration.powergrid;

/** Adjust only generator internal electrical resistance, never induced voltage or kinetic force. */
public interface PowergridGeneratorCoupling {
    void manufacturing$applyInternalResistance(double ratio);
}
