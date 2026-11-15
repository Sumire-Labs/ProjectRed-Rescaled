// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.wires;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import mrtjp.fengine.api.PropagationFunction;
import mrtjp.projectred.fabrication.engine.ICTileType;

public class RedAlloyWireTile extends RedstoneWireTile {

    public RedAlloyWireTile() {
        super(ICTileType.RED_ALLOY_WIRE, -1);
    }

    protected int getRenderHue() {
        return (signal&0xFF)/2+60<<24|0xFF;
    }

    protected int getTextureIndex() {
        return 0;
    }

    public PropagationFunction propagationFunc(int inDir, int inPort) {
        // Red alloy merges colours into one signal. Avoid sixteen equivalent visits at plain pins.
        return ((outDir, outPort) -> {
            if (!maskConnectsToDir(inDir) || !maskConnectsToDir(outDir)) return false;
            var neighbour=getTileTowardsDir(outDir);
            int required=neighbour instanceof mrtjp.projectred.fabrication.engine.IInsulatedConnectableICTile insulated
                    ? insulated.getInsulatedColour() : 0;
            return outPort==required;
        });
    }

}
