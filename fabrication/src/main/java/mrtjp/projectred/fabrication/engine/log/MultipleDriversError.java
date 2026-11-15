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

import java.util.ArrayList;
import java.util.List;

import static mrtjp.projectred.fabrication.engine.log.CompileProblemSeverity.ERROR;

public class MultipleDriversError extends CompileProblem {

    public TileCoord coord = TileCoord.origin;
    public final List<Integer> registerList = new ArrayList<>();

    public MultipleDriversError() {
        super(CompileProblemType.MULTIPLE_DRIVERS, CompileProblemSeverity.ERROR);
    }

    public MultipleDriversError(TileCoord coord, List<Integer> registerList) {
        super(CompileProblemType.MULTIPLE_DRIVERS, CompileProblemSeverity.ERROR);
        this.coord = coord;
        this.registerList.addAll(registerList);
    }

    public void save(NBTTagCompound tag) {
        tag.setTag("coord", EngineData.tileCoordToNBT(coord));
        tag.setIntArray("registers", registerList.stream().mapToInt(i -> i).toArray());
    }

    public void load(NBTTagCompound tag) {
        coord = EngineData.tileCoordFromNBT(tag.getCompoundTag("coord"));
        registerList.clear();
        for (int i : tag.getIntArray("registers")) {
            registerList.add(i);
        }
    }

    public void writeDesc(MCDataOutput out) {
        out.writeByte(coord.x).writeByte(coord.y).writeByte(coord.z);
        out.writeShort(registerList.size());
        for (int i : registerList) {
            out.writeVarInt(i);
        }
    }

    public void readDesc(MCDataInput in) {
        coord = new TileCoord(in.readByte(), in.readByte(), in.readByte());
        registerList.clear();
        int size = in.readUShort();
        for (int i = 0; i < size; i++) {
            registerList.add(in.readVarInt());
        }
    }

}
