// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.log;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import java.util.function.Supplier;

public enum CompileProblemType {
    MULTIPLE_DRIVERS(MultipleDriversError::new),
    DEAD_WIRE(DeadWireWarning::new),
    DEAD_GATE(DeadGateWarning::new),
    IO_DIR_MISMATCH(IODirectionMismatchError::new),
    IO_TYPE_MISMATCH(IOTypeMismatchError::new),
    NO_INPUTS(NoInputsError::new),
    NO_OUTPUTS(NoOutputsError::new),
    LEGACY_TILE(UnsupportedLegacyTileError::new),
    ;

    public static final CompileProblemType[] VALUES = values();

    private final Supplier<CompileProblem> issueSupplier;

    CompileProblemType(Supplier<CompileProblem> issueSupplier) {
        this.issueSupplier = issueSupplier;
    }

    public CompileProblem newInstance() {
        return issueSupplier.get();
    }

    public int getID() {
        return ordinal();
    }

    public static CompileProblem createById(int type) {
        return VALUES[type].newInstance();
    }
}
