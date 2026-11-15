// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import mrtjp.fengine.simulate.ByteRegister;
import mrtjp.projectred.fabrication.engine.ICSimulationContainer;
import net.minecraft.nbt.NBTTagCompound;

public abstract class InternalStateGateTile extends SidedRedstoneGateTile {

    public static final int STATE2_PACKET = 6;

    protected int stateReg = -1;
    private byte state2 = 0;

    public InternalStateGateTile(ICGateTileType gateType) {
        super(gateType);
    }

    public int getState2() {
        return state2 & 0xFF;
    }

    public void save(NBTTagCompound tag) {
        super.save(tag);
        tag.setByte("state2", state2);
        tag.setInteger("regS", stateReg);
    }

    public void load(NBTTagCompound tag) {
        super.load(tag);
        state2 = tag.getByte("state2");
        stateReg = tag.getInteger("regS");
    }

    public void writeDesc(MCDataOutput out) {
        super.writeDesc(out);
        out.writeByte(state2);
    }

    public void readDesc(MCDataInput in) {
        super.readDesc(in);
        state2 = in.readByte();
    }

    public void read(MCDataInput in, int key) {
        switch (key) {
            case STATE2_PACKET -> state2 = in.readByte();
            default -> super.read(in, key);
        }
    }

    protected void sendState2Update() {
        getWriteStream(STATE2_PACKET).writeByte(state2);
    }

    public void onSimRegistersChanged(int rMask, ICSimulationContainer container) {
        super.onSimRegistersChanged(rMask, container);
        byte oldS2 = state2;
        state2 = container.pullRegisterValue(stateReg);
        if (oldS2 != state2) {
            sendState2Update();
        }
    }

    protected void clearRegisterIds() {
        super.clearRegisterIds();
        stateReg = -1;
    }

    //region FETile overrides

    public void allocate(Allocator allocator) {
        super.allocate(allocator);
        stateReg = allocator.allocRegisterID();
    }

    public void consumeRemaps(RemapProvider remapProvider) {
        super.consumeRemaps(remapProvider);
        stateReg = remapProvider.getRemappedRegisterID(stateReg);
    }

    public void collect(Collector collector) {
        super.collect(collector);
        collector.addRegister(stateReg, new ByteRegister());
    }
    //endregion
}
