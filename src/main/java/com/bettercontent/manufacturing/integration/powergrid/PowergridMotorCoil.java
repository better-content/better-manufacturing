package com.bettercontent.manufacturing.integration.powergrid;

/** Native motor circuit resistor adjustment after placement, preserving network authority. */
public interface PowergridMotorCoil {
    void manufacturing$setMotorResistance(float resistance);
}
