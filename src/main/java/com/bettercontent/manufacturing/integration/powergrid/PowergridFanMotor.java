package com.bettercontent.manufacturing.integration.powergrid;

/** Small native adjustment bridge; the circuit remains entirely Power Grid owned. */
public interface PowergridFanMotor {
    void manufacturing$setMotorResistance(float resistance);
}
