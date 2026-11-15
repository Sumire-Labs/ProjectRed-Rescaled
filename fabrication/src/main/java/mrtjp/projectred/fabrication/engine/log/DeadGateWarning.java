// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.log;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import mrtjp.fengine.TileCoord;

import java.util.List;


public class DeadGateWarning extends SimpleLocatableProblem {

    public DeadGateWarning() {
        super(CompileProblemType.DEAD_GATE, CompileProblemSeverity.WARNING);
    }

    public DeadGateWarning(TileCoord coord) {
        super(CompileProblemType.DEAD_GATE, CompileProblemSeverity.WARNING, coord);
    }

}
