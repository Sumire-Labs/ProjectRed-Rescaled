// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.vec.Cuboid6;
import mrtjp.projectred.core.Configurator;
import mrtjp.projectred.fabrication.engine.ICSimulationContainer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;
import java.util.function.Consumer;


public abstract class RedstoneTimerGateTile extends TimedStateGateTile {

    public static final int START_TIME_PACKET = 15;
    public static final int MAX_TIME_PACKET = 16;
    protected int pointerMax = 38;
    protected long pointerStartTime = -1;

    public RedstoneTimerGateTile(ICGateTileType gateType) { super(gateType); }

    public void save(NBTTagCompound tag) {
        super.save(tag);
        tag.setLong("tstart", pointerStartTime);
        tag.setInteger("tmax", pointerMax);
    }

    public void load(NBTTagCompound tag) {
        super.load(tag);
        pointerStartTime = tag.getLong("tstart");
        pointerMax = tag.getInteger("tmax");
    }

    public void writeDesc(MCDataOutput out) {
        super.writeDesc(out);
        out.writeLong(pointerStartTime);
        out.writeInt(pointerMax);
    }

    public void readDesc(MCDataInput in) {
        super.readDesc(in);
        pointerStartTime = in.readLong();
        pointerMax = in.readInt();
    }

    public void read(MCDataInput in, int key) {
        switch (key) {
            case START_TIME_PACKET -> pointerStartTime = in.readLong();
            case MAX_TIME_PACKET -> pointerMax = in.readInt();
            default -> super.read(in, key);
        }
    }

    protected void sendTimeStateUpdate() {
        getWriteStream(START_TIME_PACKET).writeLong(pointerStartTime);
    }

    protected void sendMaxTimeUpdate() {
        getWriteStream(MAX_TIME_PACKET).writeInt(pointerMax);
    }

    //region BaseTile overrides

    private void addTimerMax(int delta) {
        int newMax = pointerMax + delta;
        int min = Math.max(4, Configurator.minTimerTicks()) - 2;
        if (newMax < min) newMax = min;

        if (newMax != pointerMax) {
            pointerMax = newMax;
            sendMaxTimeUpdate();
            getEditor().markTileChange();
        }
    }

    public void onSimRegistersChanged(int rMask, ICSimulationContainer container) {
        super.onSimRegistersChanged(rMask, container);
        long startTime = container.pullLongValue(timeRegs, 0);
        if (startTime != pointerStartTime) {
            pointerStartTime = startTime;
            sendTimeStateUpdate();
        }
    }
    //endregion

    //region IGateRenderData

    public abstract boolean isPointerStarted();

    public int pointerMax() {
        return pointerMax;
    }

    public int pointerValue() {
        long simTime = getEditor().getStateMachine().getSimSystemTime();
        return (int) (simTime - pointerStartTime);
    }
    //endregion

    // Static utility for other gates that need time adjustment zones

}
