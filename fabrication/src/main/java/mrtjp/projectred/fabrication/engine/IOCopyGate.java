package mrtjp.projectred.fabrication.engine;
import mrtjp.fengine.simulate.ICGate;
import mrtjp.fengine.simulate.ICSimulation;

/** Keeps static input IDs and independent output pins intact across direct connections and fan-out. */
public final class IOCopyGate implements ICGate {
    public void compute(ICSimulation simulation,int[] inputs,int[] outputs) {
        for(int i=0;i<outputs.length;i++) simulation.queueRegByteVal(outputs[i],simulation.getRegByteVal(inputs[i]));
    }
}
