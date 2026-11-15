// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import mrtjp.projectred.fabrication.engine.ICTileType;


import static mrtjp.projectred.fabrication.engine.EnginePlacement.*;

public enum ICGateTileType {

    REDSTONE_IO         (ICTileType.REDSTONE_IO_GATE, IO_EDGE),
    BUNDLED_COLOR_IO    (ICTileType.BUNDLED_COLOR_IO_GATE, IO_EDGE),
    BUNDLED_BUS_IO      (ICTileType.BUNDLED_BUS_IO_GATE, IO_EDGE),
    ANALOG_IO           (ICTileType.ANALOG_IO_GATE, IO_EDGE),

    OR                   (ICTileType.OR_GATE, INTERNAL),
    NOR                  (ICTileType.NOR_GATE, INTERNAL),
    NOT                  (ICTileType.NOT_GATE, INTERNAL),
    AND                  (ICTileType.AND_GATE, INTERNAL),
    NAND                 (ICTileType.NAND_GATE, INTERNAL),
    XOR                  (ICTileType.XOR_GATE, INTERNAL),
    XNOR                 (ICTileType.XNOR_GATE, INTERNAL),
    BUFFER               (ICTileType.BUFFER_GATE, INTERNAL),
    MULTIPLEXER          (ICTileType.MULTIPLEXER_GATE, INTERNAL),
    PULSE                (ICTileType.PULSE_GATE, INTERNAL),
    REPEATER             (ICTileType.REPEATER_GATE, INTERNAL),
    RANDOMIZER           (ICTileType.RANDOMIZER_GATE, INTERNAL),
    SR_LATCH             (ICTileType.SR_LATCH_GATE, INTERNAL),
    TOGGLE_LATCH         (ICTileType.TOGGLE_LATCH_GATE, INTERNAL),
    TRANSPARENT_LATCH    (ICTileType.TRANSPARENT_LATCH_GATE, INTERNAL),
    TIMER                (ICTileType.TIMER_GATE, INTERNAL),
    SEQUENCER            (ICTileType.SEQUENCER_GATE, INTERNAL),
    COUNTER              (ICTileType.COUNTER_GATE, INTERNAL),
    STATE_CELL           (ICTileType.STATE_CELL_GATE, INTERNAL),
    SYNCHRONIZER         (ICTileType.SYNCHRONIZER_GATE, INTERNAL),
    ;

    public final ICTileType tileType;
    public final EnginePlacement placementType;
    ICGateTileType(ICTileType tileType, EnginePlacement placementType) {
        this.tileType = tileType;
        this.placementType = placementType;
    }
}
