// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Rotation;
import codechicken.lib.vec.Scale;
import codechicken.lib.vec.Vector3;
import mrtjp.fengine.simulate.ICGate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;


public abstract class SimpleGateTile extends SidedRedstoneGateTile {

    public static final Cuboid6[] ZONE_BOUNDS = new Cuboid6[16];

    static {
        Cuboid6[] sideBoxes = {
            new Cuboid6(6, 2, 0, 10, 2.5, 6),
            new Cuboid6(10, 2, 6, 16, 2.5, 10),
            new Cuboid6(6, 2, 10, 10, 2.5, 16),
            new Cuboid6(0, 2, 6, 6, 2.5, 10),
        };

        for (int sr = 0; sr < 4; sr++) {
            for (int tr = 0; tr < 4; tr++) {
                int i = sr << 2 | tr;
                ZONE_BOUNDS[i] = sideBoxes[sr].copy()
                        .apply(new Scale(1/16D))
                        .apply(Rotation.quarterRotations[tr].at(Vector3.center));
            }
        }
    }

    public SimpleGateTile(ICGateTileType gateType) {
        super(gateType);
    }

    //region BaseTile overrides

    protected Cuboid6 getBoundsForIOToggleZone(int r) {
        return ZONE_BOUNDS[getRotation() << 2 | r];
    }

    protected void toggleDeadSide(int r) {
        if (getDeadSides() == 0) return;

        int oldShape = getShape();
        int shape = oldShape ^ rotationToDeadSideBit(r);
        if (Integer.bitCount(shape) > getMaxDeadSides()) return;

        configureShapeAndSend(shape);
    }


    //endregion

    //region SimpleGateTile logic override points
    protected int interactMask() {
        return 0;
    }

    protected int getDeadSides() {
        return 0;
    }

    protected int getMaxDeadSides() {
        return getDeadSides() - 1;
    }

    protected int rotationToDeadSideBit(int r) {
        return 1 << (r - 1);
    }

    protected abstract ICGate createGate();

    protected void collectGate(Collector collector, int gateId, int[] inputRegisters, int[] outputRegisters) {

        List<Integer> inputRegistersList = new ArrayList<>();
        List<Integer> outputRegistersList = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            if (inputRegisters[r] != -1) inputRegistersList.add(inputRegisters[r]);
            if (outputRegisters[r] != -1) outputRegistersList.add(outputRegisters[r]);
        }

        collector.addGate(gateId, createGate(), inputRegistersList, outputRegistersList);
    }
    //endregion
}
