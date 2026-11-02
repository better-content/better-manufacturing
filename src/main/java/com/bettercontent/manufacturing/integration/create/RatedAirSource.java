package com.bettercontent.manufacturing.integration.create;

import com.simibubi.create.content.kinetics.fan.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;

/** Uses Create's own distance formula and caps with the baked effective air RPM. */
public record RatedAirSource(IAirCurrentSource source, double rate) implements IAirCurrentSource {
    @Override public AirCurrent getAirCurrent() { return source.getAirCurrent(); }
    @Override public Level getAirCurrentWorld() { return source.getAirCurrentWorld(); }
    @Override public BlockPos getAirCurrentPos() { return source.getAirCurrentPos(); }
    @Override public float getSpeed() { return (float) (source.getSpeed() * rate); }
    @Override public Direction getAirflowOriginSide() { return source.getAirflowOriginSide(); }
    @Override public Direction getAirFlowDirection() { return source.getAirFlowDirection(); }
    @Override public boolean isSourceRemoved() { return source.isSourceRemoved(); }
}
