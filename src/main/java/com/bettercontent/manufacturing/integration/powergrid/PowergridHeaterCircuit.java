package com.bettercontent.manufacturing.integration.powergrid;

/** Update the existing native resistor in place, without replacing the electrical network. */
public interface PowergridHeaterCircuit {
    void manufacturing$setHeaterResistance(float ohms);
}
