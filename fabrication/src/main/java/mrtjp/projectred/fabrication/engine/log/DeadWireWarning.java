// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.log;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import mrtjp.fengine.TileCoord;

import java.util.List;


public class DeadWireWarning extends SimpleLocatableProblem {

    public DeadWireWarning() {
        super(CompileProblemType.DEAD_WIRE, CompileProblemSeverity.WARNING);
    }

    public DeadWireWarning(TileCoord coord) {
        super(CompileProblemType.DEAD_WIRE, CompileProblemSeverity.WARNING, coord);
    }

}
