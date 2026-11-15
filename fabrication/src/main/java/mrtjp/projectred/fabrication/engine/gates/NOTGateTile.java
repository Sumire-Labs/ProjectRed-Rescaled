// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import mrtjp.fengine.simulate.ICGate;
import mrtjp.fengine.simulate.ICSimulation;

public class NOTGateTile extends SimpleGateTile {

    public NOTGateTile() {
        super(ICGateTileType.NOT);
    }

    protected int redstoneOutputMask() {
        return ~((getShape() & 1) << 1 | (getShape() & 2) >> 1 | (getShape() & 4) << 1) & 0xB;
    }

    protected int redstoneInputMask() {
        return 4;
    }

    protected int interactMask() {
        return 0xB;
    }

    protected int getDeadSides() {
        return 3;
    }

    protected int rotationToDeadSideBit(int r) {
        return switch (r) {
            case 0 -> 0x2;
            case 1 -> 0x1;
            case 3 -> 0x4;
            default -> 0;
        };
    }

    public ICGate createGate() {
        return new NOTGate();
    }

    public static class NOTGate implements ICGate {

        public void compute(ICSimulation ic, int[] inputs, int[] outputs) {
            boolean isHigh = ic.getRegByteVal(inputs[0]) != 0;
            for (int i = 0; i < outputs.length; i++) {
                ic.queueRegByteVal(outputs[i], isHigh ? 0 : (byte) 1);
            }
        }
    }
}
