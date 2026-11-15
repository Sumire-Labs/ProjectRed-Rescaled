// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.zones.*;
import net.minecraft.util.text.TextFormatting;
import java.util.*;
import java.util.function.*;
import mrtjp.projectred.fabrication.engine.zones.InteractionZone;
import static mrtjp.projectred.fabrication.engine.zones.ZoneLabels.UL_SIDE_DISABLED;
import static mrtjp.projectred.fabrication.engine.zones.ZoneLabels.UL_SIDE_ENABLED;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import codechicken.lib.vec.Cuboid6;

import java.util.List;

import static mrtjp.projectred.fabrication.engine.gates.SimpleGateTile.ZONE_BOUNDS;
import static mrtjp.projectred.fabrication.engine.gates.SimpleGateTile.addDeadSidesInteractions;

/**
 * Combination of {@link TimedStateGateTile} and {@link SimpleGateTile}.
 * TODO: Find a cleaner way to do this
 */
public abstract class SimpleTimedStateGateTile extends TimedStateGateTile {

    public SimpleTimedStateGateTile(ICGateTileType gateType) {
        super(gateType);
    }

    //region BaseTile overrides

    protected Cuboid6 getBoundsForIOToggleZone(int r) {
        return ZONE_BOUNDS[getRotation() << 2 | r];
    }

    protected void toggleDeadSide(int r) {
        if (getDeadSides() == 0) return;

        int oldShape = getShape();
        int shape = oldShape ^ rotationToDeadSideBit(r);
        if (Integer.bitCount(shape) > getMaxDeadSides()) return;

        configureShapeAndSend(shape);
    }
    //endregion


    //region SimpleTimedStateGateTile override points
    protected int interactMask() {
        return 0;
    }

    protected int getDeadSides() {
        return 0;
    }

    protected int getMaxDeadSides() {
        return getDeadSides() - 1;
    }

    protected int rotationToDeadSideBit(int r) {
        return 1 << (r - 1);
    }
    //endregion

    public void buildInteractionZoneList(List<InteractionZone> zones) {
        super.buildInteractionZoneList(zones);
        addDeadSidesInteractions(zones, interactMask(), this::getBoundsForIOToggleZone, this::toggleDeadSide, this::getDeadSideToolTip);
    }

    protected ZoneText getDeadSideToolTip(int r) {
        boolean isEnabled = (getShape() & rotationToDeadSideBit(r)) == 0;
        return ZoneText.translatable(isEnabled ? UL_SIDE_ENABLED : UL_SIDE_DISABLED).withStyle(EngineContext.UNIFORM_GRAY);
    }
}
