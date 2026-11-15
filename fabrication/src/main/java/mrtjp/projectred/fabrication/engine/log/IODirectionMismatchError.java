// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.log;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import mrtjp.fengine.TileCoord;

import java.util.Collection;
import java.util.List;


public class IODirectionMismatchError extends MultiPositionProblem {

    public IODirectionMismatchError(Collection<TileCoord> coordList) {
        super(CompileProblemType.IO_DIR_MISMATCH, CompileProblemSeverity.ERROR, coordList);
    }

    public IODirectionMismatchError() {
        super(CompileProblemType.IO_DIR_MISMATCH, CompileProblemSeverity.ERROR);
    }

}
