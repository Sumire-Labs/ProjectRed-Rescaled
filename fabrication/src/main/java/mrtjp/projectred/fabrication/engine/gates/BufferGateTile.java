// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import mrtjp.fengine.simulate.ICGate;
import mrtjp.fengine.simulate.ICSimulation;

public class BufferGateTile extends SimpleGateTile {

    public BufferGateTile() {
        super(ICGateTileType.BUFFER);
    }

    protected int redstoneOutputMask() {
        return ~((getShape() & 1) << 1 | (getShape() & 2) << 2) & 0xB;
    }

    protected int redstoneInputMask() {
        return 4;
    }

    protected int interactMask() {
        return 0xA;
    }

    protected int getDeadSides() {
        return 2;
    }

    protected int getMaxDeadSides() {
        return 2;
    }

    protected int rotationToDeadSideBit(int r) {
        return switch (r) {
            case 1 -> 0x1;
            case 3 -> 0x2;
            default -> 0;
        };
    }

    public ICGate createGate() {
        return new BufferGate();
    }

    public static class BufferGate implements ICGate {

        public void compute(ICSimulation ic, int[] inputs, int[] outputs) {
            for (int i = 0; i < outputs.length; i++)
                ic.queueRegByteVal(outputs[i], ic.getRegByteVal(inputs[0]) != 0 ? (byte) 1 : (byte) 0);
        }
    }
}
