// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import mrtjp.fengine.simulate.ICGate;
import mrtjp.fengine.simulate.ICSimulation;

public class XORGateTile extends SimpleGateTile {

    public XORGateTile() {
        super(ICGateTileType.XOR);
    }

    protected int redstoneOutputMask() {
        return 1;
    }

    protected int redstoneInputMask() {
        return 10;
    }

    public ICGate createGate() {
        return new XORGate();
    }

    public static class XORGate implements ICGate {

        public void compute(ICSimulation ic, int[] inputs, int[] outputs) {
            boolean in0 = ic.getRegByteVal(inputs[0]) != 0;
            boolean in1 = ic.getRegByteVal(inputs[1]) != 0;
            ic.queueRegByteVal(outputs[0], in0 != in1 ? (byte) 1 : (byte) 0);
        }
    }
}
