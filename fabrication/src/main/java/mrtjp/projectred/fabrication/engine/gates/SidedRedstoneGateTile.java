// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import mrtjp.fengine.api.IPathFinder;
import mrtjp.fengine.assemble.PathFinderResult;
import mrtjp.fengine.simulate.ByteRegister;
import mrtjp.projectred.fabrication.engine.ICSimulationContainer;
import mrtjp.projectred.fabrication.engine.IRotatableICTile;
import mrtjp.projectred.fabrication.engine.log.DeadGateWarning;
import mrtjp.projectred.fabrication.engine.log.MultipleDriversError;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Arrays;
import java.util.Optional;

import static mrtjp.projectred.fabrication.engine.PRFabricationEngine.REG_ZERO;

public abstract class SidedRedstoneGateTile extends RedstoneGateTile {

    private final int[] inputRegisters = new int[] { -1, -1, -1, -1 };
    private final int[] outputRegisters = new int[] { -1, -1, -1, -1 };
    private int gateId = -1;

    public SidedRedstoneGateTile(ICGateTileType gateType) {
        super(gateType);
    }

    public void save(NBTTagCompound tag) {
        super.save(tag);
        for (int i = 0; i < 4; i++) {
            tag.setInteger("regIn" + i, inputRegisters[i]);
            tag.setInteger("regOut" + i, outputRegisters[i]);
        }
        tag.setInteger("gate", gateId);
    }

    public void load(NBTTagCompound tag) {
        super.load(tag);
        for (int i = 0; i < 4; i++) {
            inputRegisters[i] = tag.getInteger("regIn" + i);
            outputRegisters[i] = tag.getInteger("regOut" + i);
        }
        gateId = tag.getInteger("gate");
    }

    public void onSimRegistersChanged(int rMask, ICSimulationContainer container) {
        int oldState = getState();
        int newState =  pullInputMask(container) & 0xF | pullOutputMask(container) << 4;
        if (oldState != newState) {
            setState(newState);
            sendStateUpdate();
        }
    }

    protected int pullInputMask(ICSimulationContainer container) {
        int mask = 0;
        for (int r = 0; r < 4; r++) {
            if (canInputRedstone(r)) {
                int regId = inputRegisters[r];
                if (container.pullRegisterValue(regId) != 0) mask |= 1 << r;
            }
        }
        return mask;
    }

    protected int pullOutputMask(ICSimulationContainer container) {
        int mask = 0;
        for (int r = 0; r < 4; r++) {
            if (canOutputRedstone(r)) {
                int regId = outputRegisters[r];
                if (container.pullRegisterValue(regId) != 0) mask |= 1 << r;
            }
        }
        return mask;
    }

    //region FETile overrides

    public void allocate(Allocator allocator) {
        clearRegisterIds();

        for (int r = 0; r < 4; r++) {
            if (canOutputRedstone(r)) outputRegisters[r] = allocator.allocRegisterID();
        }

        gateId = allocator.allocGateID();
    }

    public void locate(IPathFinder pathFinder) {

        int req = 0;
        int found = 0;
        for (int r = 0; r < 4; r++) {
            if (canInputRedstone(r)) {
                req++;
                inputRegisters[r] = searchInputRegister(r, pathFinder);
                if (inputRegisters[r] != REG_ZERO) found++;
            }
        }

        if (req > 0 && found == 0) {
            getEditor().getStateMachine().getCompilerLog().addProblem(new DeadGateWarning(getPos()));
        }
    }

    public void consumeRemaps(RemapProvider remapProvider) {

        for (int r = 0; r < 4; r++) {
            if (inputRegisters[r] != -1) inputRegisters[r] = remapProvider.getRemappedRegisterID(inputRegisters[r]);
            if (outputRegisters[r] != -1) outputRegisters[r] = remapProvider.getRemappedRegisterID(outputRegisters[r]);
        }
    }

    public void collect(Collector collector) {

        for (int r = 0; r < 4; r++) {
            if (outputRegisters[r] != -1) collector.addRegister(outputRegisters[r], new ByteRegister());
        }

        collectGate(collector, gateId, inputRegisters, outputRegisters);
    }

    public Optional<Integer> getOutputRegister(int outDir, int outPort) {
        int absR = IRotatableICTile.dirToRotation(outDir);
        int r = toInternalRotation(absR);

        if (r == -1 || outputRegisters[r] == -1) return Optional.empty();
        return Optional.of(outputRegisters[r]);
    }

    public Optional<Integer> getInputRegister(int inDir, int inPort) {
        int absR = IRotatableICTile.dirToRotation(inDir);
        int r = toInternalRotation(absR);

        if (r == -1 || inputRegisters[r] == -1) return Optional.empty();
        return Optional.of(inputRegisters[r]);
    }

    //endregion

    protected void clearRegisterIds() {
        Arrays.fill(inputRegisters, -1);
        Arrays.fill(outputRegisters, -1);
    }

    private int searchInputRegister(int r, IPathFinder pathFinder) {
        int absR = toAbsoluteRotation(r);
        int absDir = IRotatableICTile.rotationToDir(absR);
        PathFinderResult pfr = pathFinder.doPathFinding((d, p) -> d == absDir && acceptsPathPort(d,p));
        if (pfr.outputRegisters.size() > 1) {
            getEditor().getStateMachine().getCompilerLog().addProblem(new MultipleDriversError(getPos(), pfr.outputRegisters));
        }
        if (!pfr.outputRegisters.isEmpty()) {
            return pfr.outputRegisters.get(0);
        }

        return REG_ZERO;
    }

    //region SidedRedstoneGateTile logic override points

    protected abstract void collectGate(Collector collector, int gateId, int[] inputRegisters, int[] outputRegisters);

    //endregion
}
