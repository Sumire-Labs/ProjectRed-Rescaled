// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import mrtjp.fengine.simulate.ICGate;
import mrtjp.fengine.simulate.ICSimulation;

public class NORGateTile extends SimpleGateTile {

    public NORGateTile() {
        super(ICGateTileType.NOR);
    }

    protected int redstoneOutputMask() {
        return 1;
    }

    protected int redstoneInputMask() {
        return ~getShape() << 1 & 0xE;
    }

    protected int interactMask() {
        return 0xE;
    }

    protected int getDeadSides() {
        return 3;
    }

    public ICGate createGate() {
        return new NORGate();
    }

    public static class NORGate implements ICGate {

        public void compute(ICSimulation ic, int[] inputs, int[] outputs) {
            for (int i = 0; i < inputs.length; i++) {
                if (ic.getRegByteVal(inputs[i]) != 0) {
                    ic.queueRegByteVal(outputs[0], (byte) 0);
                    return;
                }
            }
            ic.queueRegByteVal(outputs[0], (byte) 1);
        }
    }
}
