// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.log;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import mrtjp.fengine.TileCoord;

import java.util.Collection;
import java.util.List;


public class IOTypeMismatchError extends MultiPositionProblem {

    public IOTypeMismatchError(Collection<TileCoord> coordList) {
        super(CompileProblemType.IO_TYPE_MISMATCH, CompileProblemSeverity.ERROR, coordList);
    }

    public IOTypeMismatchError() {
        super(CompileProblemType.IO_TYPE_MISMATCH, CompileProblemSeverity.ERROR);
    }

}
