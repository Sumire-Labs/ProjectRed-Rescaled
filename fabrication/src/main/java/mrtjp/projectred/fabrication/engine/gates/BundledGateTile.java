// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import mrtjp.projectred.fabrication.engine.IBundledConnectableICTile;
import mrtjp.projectred.fabrication.engine.IConnectableICTile;

public class BundledGateTile extends RedstoneGateTile implements IBundledConnectableICTile {

    public BundledGateTile(ICGateTileType gateType) {
        super(gateType);
    }

    //region BundledGateTile override points

    protected boolean canGateConnectTo(IConnectableICTile target, int r) {
        if (target instanceof IBundledConnectableICTile) {
            return canConnectBundled(r);
        }
        return super.canGateConnectTo(target, r);
    }

    protected boolean canConnectBundled(int r) {
        return canInputBundled(r) || canOutputBundled(r);
    }

    protected boolean canOutputBundled(int r) {
        return (bundledOutputMask() & 1 << r) != 0;
    }

    protected boolean canInputBundled(int r) {
        return (bundledInputMask() & 1 << r) != 0;
    }

    protected int bundledOutputMask() {
        return 0;
    }

    protected int bundledInputMask() {
        return 0;
    }
    //endregion
}
