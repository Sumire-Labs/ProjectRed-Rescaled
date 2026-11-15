// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.zones.*;
import net.minecraft.util.text.TextFormatting;
import java.util.*;
import java.util.function.*;
import mrtjp.projectred.fabrication.engine.zones.InteractionZone;
import mrtjp.projectred.fabrication.engine.zones.SimpleInteractionZone;
import static mrtjp.projectred.fabrication.engine.zones.ZoneLabels.*;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import codechicken.lib.vec.*;
import mrtjp.projectred.fabrication.engine.ICInterfaceType;

import java.util.List;


public class RedstoneIOGateTile extends SingleBitIOGateTile {

    private static final Cuboid6[] INPUT_TOGGLE_ZONE_BOUNDS = new Cuboid6[4];
    private static final Cuboid6[] DIR_ZONE_BOUNDS = new Cuboid6[4];

    static {
        for (int r = 0; r < 4; r++) {
            Transformation t = new Scale(1/16D).with(Rotation.quarterRotations[r].at(Vector3.center));
            INPUT_TOGGLE_ZONE_BOUNDS[r] = new Cuboid6(1, 2, 0, 15, 3, 3).apply(t);       // Toggle state of IO register
            DIR_ZONE_BOUNDS[r]          = new Cuboid6(6, 2, 8, 10, 4, 12).apply(t);      // Toggle IO direction
        }
    }

    public RedstoneIOGateTile() {
        super(ICGateTileType.REDSTONE_IO);
    }

    public ICInterfaceType getInterfaceType() {
        return ICInterfaceType.REDSTONE;
    }

    //region BaseTile overrides

    //endregion


    public void buildInteractionZoneList(List<InteractionZone> zones) {
        super.buildInteractionZoneList(zones);

        // For toggling input to simulation
        zones.add(new SimpleInteractionZone.Builder()
                .bounds(() -> INPUT_TOGGLE_ZONE_BOUNDS[getRotation()])
                .leftClickAction(this::toggleWorldInput)
                .tooltip(toolTip -> {
                    toolTip.add(ZoneText.translatable(isInputIOMode() ? UL_IO_RS_INPUT : UL_IO_RS_OUTPUT)
                            .append(ZoneText.literal(": ")
                            .append(ZoneText.translatable(((getState() & 0x44) != 0 ? UL_IO_LEVEL_HIGH : UL_IO_LEVEL_LOW))))
                            .withStyle(EngineContext.UNIFORM_GRAY));
                })
                .build());

        // For toggling input/output direction
        zones.add(new SimpleInteractionZone.Builder()
                .bounds(() -> DIR_ZONE_BOUNDS[getRotation()])
                .leftClickAction(this::toggleDirection)
                .tooltip(toolTip -> {
                    toolTip.add(ZoneText.translatable(UL_IO_DIRECTION)
                            .append(ZoneText.literal(": "))
                            .append(ZoneText.translatable((isInputIOMode() ? UL_IO_DIR_INPUT : UL_IO_DIR_OUTPUT)))
                            .withStyle(EngineContext.UNIFORM_GRAY));
                })
                .build());
    }
}
