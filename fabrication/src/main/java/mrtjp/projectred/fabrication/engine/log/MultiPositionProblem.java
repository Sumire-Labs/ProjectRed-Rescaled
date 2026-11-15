// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.log;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import codechicken.lib.colour.EnumColour;
import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.vec.Vector3;
import mrtjp.fengine.TileCoord;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static mrtjp.projectred.fabrication.engine.log.CompileProblemSeverity.ERROR;

public abstract class MultiPositionProblem extends CompileProblem {

    public final List<TileCoord> coordList = new LinkedList<>();

    public MultiPositionProblem(CompileProblemType type, CompileProblemSeverity severity) {
        super(type, severity);
    }

    public MultiPositionProblem(CompileProblemType type, CompileProblemSeverity severity, Collection<TileCoord> coordList) {
        this(type, severity);
        this.coordList.addAll(coordList);
    }

    public void save(NBTTagCompound tag) {
        EngineData.saveTileCoordList(tag, "coordList", coordList);
    }

    public void load(NBTTagCompound tag) {
        coordList.clear();
        EngineData.loadTileCoordList(tag, "coordList", coordList);
    }

    public void writeDesc(MCDataOutput out) {
        out.writeShort(coordList.size());
        for (TileCoord coord : coordList) {
            out.writeByte(coord.x).writeByte(coord.y).writeByte(coord.z);
        }
    }

    public void readDesc(MCDataInput in) {
        coordList.clear();
        int size = in.readUShort();
        for (int i = 0; i < size; i++) {
            coordList.add(new TileCoord(in.readByte(), in.readByte(), in.readByte()));
        }
    }

}
