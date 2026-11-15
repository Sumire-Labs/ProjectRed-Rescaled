// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.vec.Cuboid6;
import mrtjp.fengine.simulate.ByteRegister;
import mrtjp.fengine.simulate.ICGate;
import mrtjp.fengine.simulate.ICSimulation;
import mrtjp.projectred.fabrication.engine.ICSimulationContainer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static mrtjp.projectred.fabrication.engine.EngineContext.LOGGER;
import static mrtjp.projectred.fabrication.engine.IRotatableICTile.flipMaskZ;

public class CounterGateTile extends InternalStateGateTile {

    private static final int KEY_VALUE = 10;
    private static final int KEY_MAX = 11;
    private static final int KEY_INCR = 12;
    private static final int KEY_DECR = 13;

    // Count stored as short
    private int[] countRegs = new int[] { -1, -1 };

    private int value = 0;
    private int max = 10;
    private int incr = 1;
    private int decr = 1;

    public CounterGateTile() {
        super(ICGateTileType.COUNTER);
    }

    //region save/load

    public void save(NBTTagCompound tag) {
        super.save(tag);
        tag.setIntArray("reg_count", countRegs);
        tag.setShort("val", (short) value);
        tag.setShort("max", (short) max);
        tag.setShort("inc", (short) incr);
        tag.setShort("dec", (short) decr);
    }

    public void load(NBTTagCompound tag) {
        super.load(tag);
        countRegs = tag.getIntArray("reg_count");
        value = tag.getShort("val") & 0xFFFF;
        max = tag.getShort("max") & 0xFFFF;
        incr = tag.getShort("inc") & 0xFFFF;
        decr = tag.getShort("dec") & 0xFFFF;
    }

    public void writeDesc(MCDataOutput packet) {
        super.writeDesc(packet);
        packet.writeShort(value);
        packet.writeShort(max);
        packet.writeShort(incr);
        packet.writeShort(decr);
    }

    public void readDesc(MCDataInput packet) {
        super.readDesc(packet);
        value = packet.readUShort();
        max = packet.readUShort();
        incr = packet.readUShort();
        decr = packet.readUShort();
    }
    //endregion

    //region Packets

    public void read(MCDataInput packet, int key) {
        switch (key) {
            case KEY_VALUE -> value = packet.readUShort();
            case KEY_MAX -> max = packet.readUShort();
            case KEY_INCR -> incr = packet.readUShort();
            case KEY_DECR -> decr = packet.readUShort();
            default -> super.read(packet, key);
        }
    }

    protected void sendValueUpdate() {
        getWriteStream(KEY_VALUE).writeShort(value);
    }

    protected void sendMaxUpdate() {
        getWriteStream(KEY_MAX).writeShort(max);
    }

    protected void sendIncrUpdate() {
        getWriteStream(KEY_INCR).writeShort(incr);
    }

    protected void sendDecrUpdate() {
        getWriteStream(KEY_DECR).writeShort(decr);
    }
    //endregion

    //region BaseTile overrides

    public void onSimRegistersChanged(int rMask, ICSimulationContainer container) {
        super.onSimRegistersChanged(rMask, container);
        int value = container.pullShortValue(countRegs, 0) & 0xFFFF;
        if (value != this.value) {
            this.value = value;
            sendValueUpdate();
        }
    }

    private void addMax(int delta) {
        int oldMax = max;
        max = Math.max(1, Math.min(max + delta, Short.MAX_VALUE)); // Technically, this can be 2x since we store unsigned
        if (max != oldMax) {
            sendMaxUpdate();
            if (incr > max) {
                incr = max;
                sendIncrUpdate();
            }
            if (decr > max) {
                decr = max;
                sendDecrUpdate();
            }
            getEditor().markTileChange();
        }
    }

    private void addIncr(int delta) {
        int oldIncr = incr;
        incr = Math.max(1, Math.min(incr + delta, max));
        if (incr != oldIncr) {
            sendIncrUpdate();
            getEditor().markTileChange();
        }
    }

    private void addDecr(int delta) {
        int oldDecr = decr;
        decr = Math.max(1, Math.min(decr + delta, max));
        if (decr != oldDecr) {
            sendDecrUpdate();
            getEditor().markTileChange();
        }
    }
    //endregion

    //region GateTile overrides

    protected void reflectAndSend() {
        configureShapeAndSend(getShape() == 0 ? 1 : 0);
    }

    protected boolean canReflect() {
        return true;
    }
    //endregion

    //region Gate render data

    public boolean isPointerStarted() {
        return getEditor().getStateMachine().isSimulating();
    }

    public int pointerValue() {
        return value;
    }

    public int pointerMax() {
        return max;
    }

    public int state() {
        // required bc renderer expects state to be stored reflected
        int state = super.state();
        return getShape() == 0 ? state : state & 0xF0 | flipMaskZ(state) & 0xF;
    }
    //endregion

    //region RedstoneGateTile overrides

    protected int redstoneOutputMask() {
        return 0x5;
    }

    protected int redstoneInputMask() {
        return 0xA;
    }
    //endregion

    protected void clearRegisterIds() {
        super.clearRegisterIds();
        Arrays.fill(countRegs, -1);
    }

    //region FETile overrides

    public void allocate(Allocator allocator) {
        super.allocate(allocator);
        for (int i = 0; i < 2; i++) {
            countRegs[i] = allocator.allocRegisterID();
        }
    }

    public void consumeRemaps(RemapProvider remapProvider) {
        super.consumeRemaps(remapProvider);
        for (int i = 0; i < 2; i++) {
            countRegs[i] = remapProvider.getRemappedRegisterID(countRegs[i]);
        }
    }

    public void collect(Collector collector) {
        super.collect(collector);
        for (int i = 0; i < 2; i++) {
            collector.addRegister(countRegs[i], new ByteRegister());
        }
    }

    protected void collectGate(Collector collector, int gateId, int[] inputRegisters, int[] outputRegisters) {
        List<Integer> inputRegs = new ArrayList<>();
        List<Integer> outputRegs = new ArrayList<>();

        inputRegs.add(stateReg);                                 // 0: state
        inputRegs.add(inputRegisters[getShape() == 0 ? 1 : 3]);  // 1: incr input
        inputRegs.add(inputRegisters[getShape() == 0 ? 3 : 1]);  // 2: decr input
        for (int i = 0; i < 2; i++) {                            // 3-4: counter value
            inputRegs.add(countRegs[i]);
        }

        outputRegs.add(stateReg);                                // 0: state
        outputRegs.add(outputRegisters[0]);                      // 1: max output
        outputRegs.add(outputRegisters[2]);                      // 2: min output
        for (int i = 0; i < 2; i++) {                            // 3-4: counter value
            outputRegs.add(countRegs[i]);
        }

        collector.addGate(gateId, new CounterGate(max, incr, decr), inputRegs, outputRegs);
    }

    public static class CounterGate implements ICGate {

        private final int max;
        private final int incr;
        private final int decr;

        public CounterGate(int max, int incr, int decr) {
            this.max = max;
            this.incr = incr;
            this.decr = decr;
        }

        private static byte readState(ICSimulation ic, int[] inputs) { return ic.getRegByteVal(inputs[0]); }
        private static byte readInputMask(ICSimulation ic, int[] inputs) {
            int mask = 0;
            if (ic.getRegByteVal(inputs[1]) != 0) mask |= 2; // incr
            if (ic.getRegByteVal(inputs[2]) != 0) mask |= 8; // decr
            return (byte) mask;
        }
        private static short readCount(ICSimulation ic, int[] inputs) { return ic.getRegShortVal(inputs, 3); }
        private static void writeState(ICSimulation ic, int[] outputs, byte state) { ic.queueRegByteVal(outputs[0], state); }
        private static void writeOutputMask(ICSimulation ic, int[] outputs, byte oMask) {
            ic.queueRegByteVal(outputs[1], (byte) ((oMask & 2) != 0 ? 1 : 0)); // max
            ic.queueRegByteVal(outputs[2], (byte) ((oMask & 4) != 0 ? 1 : 0)); // min
        }
        private static void writeCount(ICSimulation ic, int[] outputs, short count) { ic.queueRegShortVal(outputs, 3, count); }

        public void compute(ICSimulation ic, int[] inputs, int[] outputs) {
            int stateVal = readState(ic, inputs);
            int state = stateVal & 0xF;
            int prevInputMask = (stateVal >> 4) & 0xF;
            int inputMask = readInputMask(ic, inputs);
            int highMask = inputMask & ~prevInputMask;

            switch (state) {
                case 0 -> { // Initial state
                    writeState(ic, outputs, (byte) 1);          // min high state
                    writeOutputMask(ic, outputs, (byte) 0x4);   // min output high
                    writeCount(ic, outputs, (short) 0);         // reset count
                }

                case 1 -> { // Running state
                    if (highMask == 0x2) { // If only incr went high
                        int newCount = Math.min(max, (readCount(ic, inputs) & 0xFFFF) + incr);  // increment
                        writeCount(ic, outputs, (short) newCount);                              // update count
                        writeOutputMask(ic, outputs, (byte) (newCount == max ? 0x2 : 0x0));     // write output

                    } else if (highMask == 0x8) { // If only decr went high
                        int newCount = (Math.max(0, (readCount(ic, inputs) & 0xFFFF) - decr));  // decrement
                        writeCount(ic, outputs, (short) newCount);                              // update count
                        writeOutputMask(ic, outputs, (byte) (newCount == 0 ? 0x4 : 0x0));       // write output
                    }

                    writeState(ic, outputs, (byte) (inputMask << 4 | 1)); // stay in running state
                }
                default -> {
                    LOGGER.error("Invalid state: " + state);
                    writeState(ic, outputs, (byte) 0); // reset to initial state
                }
            }
        }
    }
    //endregion
}