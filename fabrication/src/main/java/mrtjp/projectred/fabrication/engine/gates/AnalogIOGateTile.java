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

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.vec.*;
import mrtjp.projectred.fabrication.engine.ICInterfaceType;
import mrtjp.projectred.fabrication.engine.ICSimulationContainer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;


public class AnalogIOGateTile extends SingleBitIOGateTile {

    protected static final int ANALOG_SIGNAL_PACKET = 6;

    private static final Cuboid6[] INPUT_TOGGLE_ZONE_BOUNDS = new Cuboid6[4];
    private static final Cuboid6[] DIR_ZONE_BOUNDS = new Cuboid6[4];
    private static final Cuboid6[] ANALOG_LEVEL_BOUNDS = new Cuboid6[4];

    protected byte analogSignal = 0;

    static {
        for (int r = 0; r < 4; r++) {
            Transformation t = new Scale(1/16D).with(Rotation.quarterRotations[r].at(Vector3.center));
            INPUT_TOGGLE_ZONE_BOUNDS[r] = new Cuboid6(1, 2, 0, 15, 3, 3).apply(t);       // Toggle state of IO register
            DIR_ZONE_BOUNDS[r]          = new Cuboid6(6, 2, 8, 10, 4, 12).apply(t);      // Toggle IO direction
            ANALOG_LEVEL_BOUNDS[r]      = new Cuboid6(2, 2, 8, 6, 4.5, 12).apply(t);     // Toggle analog level
        }
    }

    public AnalogIOGateTile() {
        super(ICGateTileType.ANALOG_IO);
    }

    //region Save/Load

    public void save(NBTTagCompound tag) {
        super.save(tag);
        tag.setByte("analog_signal", analogSignal);
    }

    public void load(NBTTagCompound tag) {
        super.load(tag);
        analogSignal = tag.getByte("analog_signal");
    }
    //endregion

    //region Network

    public void writeDesc(MCDataOutput out) {
        super.writeDesc(out);
        out.writeByte(analogSignal);
    }

    public void readDesc(MCDataInput in) {
        super.readDesc(in);
        analogSignal = in.readByte();
    }

    public void read(MCDataInput in, int key) {
        switch (key) {
            case ANALOG_SIGNAL_PACKET -> analogSignal = in.readByte();
            default -> super.read(in, key);
        }
    }

    protected void sendAnalogSignalUpdate() {
        getWriteStream(ANALOG_SIGNAL_PACKET).writeByte(analogSignal);
    }
    //endregion


    //region IIOConnectionTile overrides

    public ICInterfaceType getInterfaceType() {
        return ICInterfaceType.ANALOG;
    }
    //endregion

    // region SingleBitIOGateTile overrides

    protected short toggleWorldInputMask(short currentMask) {
        // Set entire side to be the one-hot representation of this analog signal
        return (short) (1 << ioBit);
    }
    // endregion

    //region BaseTile overrides

    public void onSimRegistersChanged(int rMask, ICSimulationContainer container) {
        super.onSimRegistersChanged(rMask, container);
        short ioMask = isInputIOMode() ? container.getInput(getIOSide()) : container.getOutput(getIOSide());
        byte newASig = (byte) Math.max(0,31 - Integer.numberOfLeadingZeros(ioMask & 0xFFFF));
        if (newASig != analogSignal) {
            analogSignal = newASig;
            sendAnalogSignalUpdate();
        }
    }

    //endregion

    //region IGateRenderKey overrides

    public int rsIO() {
        //TODO find better key?
        return analogSignal & 0xFF;
    }
    //endregion

    public void buildInteractionZoneList(List<InteractionZone> zones) {
        super.buildInteractionZoneList(zones);

        // For toggling input to simulation
        zones.add(new SimpleInteractionZone.Builder()
                .bounds(() -> INPUT_TOGGLE_ZONE_BOUNDS[getRotation()])
                .leftClickAction(this::toggleWorldInput)
                .tooltip(toolTip -> {
                    toolTip.add(ZoneText.translatable(isInputIOMode() ? UL_IO_ANALOG_INPUT : UL_IO_ANALOG_OUTPUT)
                            .append(ZoneText.literal(": " + analogSignal))
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

        // For toggling analog level
        zones.add(new SimpleInteractionZone.Builder()
                .bounds(() -> ANALOG_LEVEL_BOUNDS[getRotation()])
                .leftClickAction(() -> shiftIOBit(true))
                .rightClickAction(() -> shiftIOBit(false))
                .tooltip(toolTip -> {
                    toolTip.add(ZoneText.translatable(UL_IO_ANALOG_LEVEL)
                            .append(ZoneText.literal(": " + ioBit))
                            .withStyle(EngineContext.UNIFORM_GRAY));
                })
                .text(() -> ZoneText.literal(String.valueOf(ioBit)))
                .build());
    }
}
