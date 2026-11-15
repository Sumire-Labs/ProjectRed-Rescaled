// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import mrtjp.fengine.api.IPathFinder;
import mrtjp.fengine.assemble.PathFinderResult;
import mrtjp.projectred.fabrication.engine.ICSimulationContainer;
import mrtjp.projectred.fabrication.engine.IIOConnectionTile;
import mrtjp.projectred.fabrication.engine.IRotatableICTile;
import mrtjp.projectred.fabrication.engine.log.MultipleDriversError;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Optional;

import static mrtjp.projectred.fabrication.engine.PRFabricationEngine.*;

public abstract class SingleBitIOGateTile extends RedstoneGateTile implements IIOConnectionTile {

    public static final int IO_BIT_PACKET = 7;

    protected byte ioBit = 0;
    private int regId = REG_ZERO;
    private int outputCopyGate = -1;

    public SingleBitIOGateTile(ICGateTileType gateType) {
        super(gateType);
    }

    //region Save/Load

    public void save(NBTTagCompound tag) {
        super.save(tag);
        tag.setInteger("reg", regId);
        tag.setByte("io_bit", ioBit);
    }

    public void load(NBTTagCompound tag) {
        super.load(tag);
        regId = tag.getInteger("reg");
        ioBit = tag.getByte("io_bit");
    }
    //endregion

    //region Network

    public void writeDesc(MCDataOutput out) {
        super.writeDesc(out);
        out.writeByte(ioBit);
    }

    public void readDesc(MCDataInput in) {
        super.readDesc(in);
        ioBit = in.readByte();
    }

    public void read(MCDataInput in, int key) {
        switch (key) {
            case IO_BIT_PACKET -> ioBit = in.readByte();
            default -> super.read(in, key);
        }
    }

    protected void sendIOBitUpdate() {
        getWriteStream(IO_BIT_PACKET).writeByte(ioBit);
    }
    //endregion

    public void toggleWorldInput() {
        getEditor().getStateMachine().onInputRegistersChanged(getIOSide(), this::toggleWorldInputMask);
    }

    protected short toggleWorldInputMask(short currentMask) {
        // Default implementation just toggles the corresponding bit
        return (short) (currentMask ^ (1 << ioBit));
    }

    protected void shiftIOBit(boolean up) {
        ioBit = (byte) ((ioBit + (up ? 1 : 15)) % 16);
        sendIOBitUpdate();
        getEditor().markTileChange();
    }

    protected void toggleDirection() {
        configureShapeAndSend((getShape() + 1) % 2);
    }

    protected int getStaticOutputRegister(int ioBit) {
        return outputRegisterId(getIOSide(), ioBit);
    }

    protected int getStaticInputRegister(int ioBit) {
        return inputRegisterId(getIOSide(), ioBit);
    }

    //region GateTile overrides

    protected boolean canRotate() {
        return false;
    }
    //endregion

    //region IIOConnectionTile overrides

    public boolean isInputIOMode() {
        return getShape() == 0;
    }

    public int getIOSide() {
        return getRotation();
    }
    //endregion

    //region BaseTile overrides

    public void onSimRegistersChanged(int rMask, ICSimulationContainer container) {
        int oldState = getState();
        int newState =  pullInputMask(container) & 0xF | pullOutputMask(container) << 4;
        if (oldState != newState) {
            setState(newState);
            sendStateUpdate();
        }
    }

    protected int pullInputMask(ICSimulationContainer container) {
        return !isInputIOMode() && container.pullRegisterValue(regId) != 0 ? 0x4 : 0;
    }

    protected int pullOutputMask(ICSimulationContainer container) {
        return isInputIOMode() && container.pullRegisterValue(regId) != 0 ? 0x4 : 0;
    }
    //endregion

    //region IGateRenderKey overrides

    public int state2() {
        return ioBit & 0xFF;
    }
    //endregion

    //region RedstoneGateTile overrides

    protected int redstoneOutputMask() {
        //TODO these are wrong i think?
        return isInputIOMode() ? 0x0 : 0x4;
    }

    protected int redstoneInputMask() {
        return isInputIOMode() ? 0x4 : 0x0;
    }
    //endregion

    //region FETile overrides

    public void allocate(Allocator allocator) {
        outputCopyGate = isInputIOMode() ? -1 : allocator.allocGateID();
        if (isInputIOMode()) { // Input from world, output into simulation
            regId = allocator.allocRegisterID(getStaticInputRegister(ioBit));
        } else { // Input from simulation, output into world
            regId = REG_ZERO; // Will be located, then remapped to target static register
        }
    }

    public void locate(IPathFinder pathFinder) {
        if (!isInputIOMode()) {
            int absR = toAbsoluteRotation(2);
            int absDir = IRotatableICTile.rotationToDir(absR);
            PathFinderResult pfr = pathFinder.doPathFinding((d, p) -> d == absDir && acceptsPathPort(d,p));
            if (pfr.outputRegisters.size() > 1) {
                getEditor().getStateMachine().getCompilerLog().addProblem(new MultipleDriversError(getPos(), pfr.outputRegisters));
            }
            if (!pfr.outputRegisters.isEmpty()) {
                regId = pfr.outputRegisters.get(0);
            }
        }
    }

    public void registerRemaps(RemapRegistry remapRegistry) {
        // Static input registers and shared drivers cannot be renamed to one output pin.
    }

    public void consumeRemaps(RemapProvider remapProvider) {
        regId = remapProvider.getRemappedRegisterID(regId);
    }

    public void collect(Collector collector) {
        if (!isInputIOMode()) collector.addGate(outputCopyGate,new IOCopyGate(),
                java.util.List.of(regId),java.util.List.of(getStaticOutputRegister(ioBit)));
        // Static registers are pre-added during assembler instantiation
//        if (isInputIOMode()) {
//            collector.addRegister(regId, new ByteRegister());
//        }
    }

    public Optional<Integer> getOutputRegister(int outDir, int outPort) {
        int gateOutputDir = IRotatableICTile.rotationToDir(toAbsoluteRotation(2));
        return isInputIOMode() && outDir == gateOutputDir ? Optional.of(regId) : Optional.empty();
    }

    public Optional<Integer> getInputRegister(int inDir, int inPort) {
        int gateInputDir = IRotatableICTile.rotationToDir(toAbsoluteRotation(2));
        return !isInputIOMode() && inDir == gateInputDir ? Optional.of(regId) : Optional.empty();
    }
    //endregion
}
