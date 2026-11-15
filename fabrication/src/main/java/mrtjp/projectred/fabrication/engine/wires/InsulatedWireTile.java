// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.wires;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import mrtjp.fengine.api.PropagationFunction;
import mrtjp.projectred.fabrication.engine.IBundledConnectableICTile;
import mrtjp.projectred.fabrication.engine.IConnectableICTile;
import mrtjp.projectred.fabrication.engine.IInsulatedConnectableICTile;
import mrtjp.projectred.fabrication.engine.IRedstoneConnectableICTile;

public class InsulatedWireTile extends RedstoneWireTile implements IInsulatedConnectableICTile, IRedstoneConnectableICTile {

    public InsulatedWireTile(int colour) {
        super(ICWireTileType.INSULATED[colour].tileType, colour);
    }

    protected int getRenderHue() {
        return -1; // Insulated wires don't render hue
    }

    public int getInsulatedColour() {
        return getWireType();
    }

    protected int getTextureIndex() {
        return signal != 0 ? 1 : 0;
    }

    public boolean canConnectTo(IConnectableICTile target, int towardsDir) {
        if (target instanceof IInsulatedConnectableICTile)
            return ((IInsulatedConnectableICTile) target).getInsulatedColour() == getInsulatedColour();

        if (target instanceof IBundledConnectableICTile)
            return true;

        return super.canConnectTo(target, towardsDir);
    }

    public PropagationFunction propagationFunc(int inDir, int inPort) {
        // Accept only matching colour, and forward only to that colour
        return ((outDir, outPort) ->
                inPort == getInsulatedColour() && outPort == inPort &&
                        maskConnectsToDir(inDir) && maskConnectsToDir(outDir));
    }
}
