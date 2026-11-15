// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import codechicken.lib.colour.EnumColour;
import codechicken.lib.vec.*;
import mrtjp.projectred.fabrication.engine.ICInterfaceType;

import java.util.List;


public class BundledColorIOGateTile extends SingleBitIOGateTile {

    private static final Cuboid6[] INPUT_TOGGLE_ZONE_BOUNDS = new Cuboid6[4];
    private static final Cuboid6[] DIR_ZONE_BOUNDS = new Cuboid6[4];
    private static final Cuboid6[] COLOR_ZONE_BOUNDS = new Cuboid6[4];

    static {
        for (int r = 0; r < 4; r++) {
            Transformation t = new Scale(1/16D).with(Rotation.quarterRotations[r].at(Vector3.center));
            INPUT_TOGGLE_ZONE_BOUNDS[r] = new Cuboid6(1, 2, 0, 15, 3, 3).apply(t);       // Toggle state of IO register
            DIR_ZONE_BOUNDS[r]          = new Cuboid6(3, 2, 6, 13, 5, 10).apply(t);      // Toggle IO mode
            COLOR_ZONE_BOUNDS[r]        = new Cuboid6(6, 2, 10, 10, 4, 14).apply(t);     // Toggle colour
        }
    }

    public BundledColorIOGateTile() {
        super(ICGateTileType.BUNDLED_COLOR_IO);
    }

    //region IIOConnectionTile overrides

    public ICInterfaceType getInterfaceType() {
        return ICInterfaceType.BUNDLED;
    }
    //endregion

    //region BaseTile overrides

    //endregion

}
