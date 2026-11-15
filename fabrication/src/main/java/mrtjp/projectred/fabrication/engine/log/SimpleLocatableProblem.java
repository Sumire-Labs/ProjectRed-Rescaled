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

import java.util.List;

import static mrtjp.projectred.fabrication.engine.log.CompileProblemSeverity.ERROR;

public abstract class SimpleLocatableProblem extends CompileProblem {

    public TileCoord coord = TileCoord.origin;

    public SimpleLocatableProblem(CompileProblemType type, CompileProblemSeverity severity) {
        super(type, severity);
    }

    public SimpleLocatableProblem(CompileProblemType type, CompileProblemSeverity severity, TileCoord coord) {
        this(type, severity);
        this.coord = coord;
    }

    public void save(NBTTagCompound tag) {
        tag.setTag("coord", EngineData.tileCoordToNBT(coord));
    }

    public void load(NBTTagCompound tag) {
        coord = EngineData.tileCoordFromNBT(tag.getCompoundTag("coord"));
    }

    public void writeDesc(MCDataOutput out) {
        out.writeByte(coord.x).writeByte(coord.y).writeByte(coord.z);
    }

    public void readDesc(MCDataInput in) {
        coord = new TileCoord(in.readByte(), in.readByte(), in.readByte());
    }

}
