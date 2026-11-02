package com.bettercontent.manufacturing.integration.clockwork;

/** Runtime blade geometry remains native; only a fixed crafted response coefficient travels with native BladeData. */
public interface ClockworkBladeRating {
    double manufacturing$bladeResponse();
    void manufacturing$bladeResponse(double response);
}
