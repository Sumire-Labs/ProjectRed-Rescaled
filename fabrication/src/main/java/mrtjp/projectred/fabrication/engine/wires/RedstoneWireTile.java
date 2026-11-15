// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.wires;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import mrtjp.fengine.api.IPathFinderManifest;
import mrtjp.projectred.fabrication.engine.ICSimulationContainer;
import mrtjp.projectred.fabrication.engine.ICTileType;
import mrtjp.projectred.fabrication.engine.IConnectableICTile;
import mrtjp.projectred.fabrication.engine.IRedstoneConnectableICTile;
import mrtjp.projectred.fabrication.engine.log.DeadWireWarning;
import net.minecraft.nbt.NBTTagCompound;

import java.util.HashSet;
import java.util.Set;

public abstract class RedstoneWireTile extends WireTile implements IRedstoneConnectableICTile {

    private static final int PACKET_SIGNAL = 2;

    protected final Set<Integer> inputRegisters = new HashSet<>();

    protected byte signal = 0;
    public byte getSignal() { return signal; }

    public RedstoneWireTile(ICTileType tileType, int renderType) {
        super(tileType, renderType);
    }

    public void save(NBTTagCompound tag) {
        super.save(tag);
        tag.setByte("signal", signal);
        tag.setByte("numInputReg", (byte) inputRegisters.size());

        int i = 0;
        for (Integer inputReg : inputRegisters) tag.setInteger("inR" + (i++), inputReg);
    }

    public void load(NBTTagCompound tag) {
        super.load(tag);
        signal = tag.getByte("signal");

        int numInputReg = tag.getByte("numInputReg") & 0xFF;
        for (int i = 0; i < numInputReg; i++) inputRegisters.add(tag.getInteger("inR" + i));
    }

    public void writeDesc(MCDataOutput out) {
        super.writeDesc(out);
        out.writeByte(signal);
    }

    public void readDesc(MCDataInput in) {
        super.readDesc(in);
        signal = in.readByte();
    }

    public void read(MCDataInput in, int key) {
        switch (key) {
            case PACKET_SIGNAL:
                signal = in.readByte();
                break;
            default:
                super.read(in, key);
        }
    }

    protected void sendSignalUpdate() {
        getWriteStream(PACKET_SIGNAL).writeByte(signal);
    }

    public boolean canConnectTo(IConnectableICTile target, int towardsDir) {
        if (target instanceof IRedstoneConnectableICTile)
            return true;

        return false;
    }

    public void onSimRegistersChanged(int rMask, ICSimulationContainer container) {

        byte oldSignal = signal;
        signal = 0;

        for (Integer inputReg : inputRegisters) {
            boolean isHigh = container.pullRegisterValue(inputReg) > 0;
            signal = isHigh ? (byte) 255 : 0;
            if (isHigh) break;
        }

        if (oldSignal != signal) sendSignalUpdate();
    }

    public void searchManifest(IPathFinderManifest manifest) {
        inputRegisters.clear();
        inputRegisters.addAll(manifest.getOutputRegisters());

        if (inputRegisters.isEmpty()) {
            getEditor().getStateMachine().getCompilerLog().addProblem(new DeadWireWarning(getPos()));
        }
    }

    public void consumeRemaps(RemapProvider remapProvider) {

        Set<Integer> remappedInputs = new HashSet<>();
        for (Integer inputReg : inputRegisters) {
            int remapped = remapProvider.getRemappedRegisterID(inputReg);
            remappedInputs.add(remapped);
        }

        inputRegisters.clear();
        inputRegisters.addAll(remappedInputs);
    }
}
