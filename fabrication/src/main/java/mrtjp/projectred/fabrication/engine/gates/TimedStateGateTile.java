// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import mrtjp.fengine.simulate.ByteRegister;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Arrays;

public abstract class TimedStateGateTile extends InternalStateGateTile {

    protected int[] timeRegs = new int[8];

    {
        Arrays.fill(timeRegs, -1);
    }

    public TimedStateGateTile(ICGateTileType gateType) {
        super(gateType);
    }

    //region BaseTile overrides

    public void save(NBTTagCompound tag) {
        super.save(tag);
        tag.setInteger("regS", stateReg);
        tag.setIntArray("reg_time", timeRegs);
    }

    public void load(NBTTagCompound tag) {
        super.load(tag);
        stateReg = tag.getInteger("regS");
        timeRegs = tag.getIntArray("reg_time");
    }
    //endregion

    protected void clearRegisterIds() {
        super.clearRegisterIds();
        Arrays.fill(timeRegs, -1);
    }

    //region FETile overrides

    public void allocate(Allocator allocator) {
        super.allocate(allocator);
        for (int i = 0; i < 8; i++) {
            timeRegs[i] = allocator.allocRegisterID();
        }
    }

    public void consumeRemaps(RemapProvider remapProvider) {
        super.consumeRemaps(remapProvider);
        for (int i = 0; i < 8; i++) {
            timeRegs[i] = remapProvider.getRemappedRegisterID(timeRegs[i]);
        }
    }

    public void collect(Collector collector) {
        super.collect(collector);
        for (int timeReg : timeRegs) {
            collector.addRegister(timeReg, new ByteRegister());
        }
    }
    //endregion
}
