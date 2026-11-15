// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.zones.*;
import net.minecraft.util.text.TextFormatting;
import java.util.*;
import java.util.function.*;
import mrtjp.projectred.fabrication.engine.zones.InteractionZone;
import mrtjp.projectred.fabrication.engine.zones.SimpleInteractionZone;
import static mrtjp.projectred.fabrication.engine.zones.ZoneLabels.UL_TIMER_INTERVAL;
import static mrtjp.projectred.fabrication.engine.zones.ZoneLabels.UL_UNIT_ONLY_TICKS;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.vec.Cuboid6;
import mrtjp.projectred.core.Configurator;
import mrtjp.projectred.fabrication.engine.ICSimulationContainer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;
import java.util.function.Consumer;


public abstract class RedstoneTimerGateTile extends TimedStateGateTile {

    public static final int START_TIME_PACKET = 15;
    public static final int MAX_TIME_PACKET = 16;
    protected int pointerMax = 38;
    protected long pointerStartTime = -1;

    public RedstoneTimerGateTile(ICGateTileType gateType) { super(gateType); }

    public void save(NBTTagCompound tag) {
        super.save(tag);
        tag.setLong("tstart", pointerStartTime);
        tag.setInteger("tmax", pointerMax);
    }

    public void load(NBTTagCompound tag) {
        super.load(tag);
        pointerStartTime = tag.getLong("tstart");
        pointerMax = tag.getInteger("tmax");
    }

    public void writeDesc(MCDataOutput out) {
        super.writeDesc(out);
        out.writeLong(pointerStartTime);
        out.writeInt(pointerMax);
    }

    public void readDesc(MCDataInput in) {
        super.readDesc(in);
        pointerStartTime = in.readLong();
        pointerMax = in.readInt();
    }

    public void read(MCDataInput in, int key) {
        switch (key) {
            case START_TIME_PACKET -> pointerStartTime = in.readLong();
            case MAX_TIME_PACKET -> pointerMax = in.readInt();
            default -> super.read(in, key);
        }
    }

    protected void sendTimeStateUpdate() {
        getWriteStream(START_TIME_PACKET).writeLong(pointerStartTime);
    }

    protected void sendMaxTimeUpdate() {
        getWriteStream(MAX_TIME_PACKET).writeInt(pointerMax);
    }

    //region BaseTile overrides

    private void addTimerMax(int delta) {
        int newMax = pointerMax + delta;
        int min = Math.max(4, Configurator.minTimerTicks()) - 2;
        if (newMax < min) newMax = min;

        if (newMax != pointerMax) {
            pointerMax = newMax;
            sendMaxTimeUpdate();
            getEditor().markTileChange();
        }
    }

    public void onSimRegistersChanged(int rMask, ICSimulationContainer container) {
        super.onSimRegistersChanged(rMask, container);
        long startTime = container.pullLongValue(timeRegs, 0);
        if (startTime != pointerStartTime) {
            pointerStartTime = startTime;
            sendTimeStateUpdate();
        }
    }
    //endregion

    //region IGateRenderData

    public abstract boolean isPointerStarted();

    public int pointerMax() {
        return pointerMax;
    }

    public int pointerValue() {
        long simTime = getEditor().getStateMachine().getSimSystemTime();
        return (int) (simTime - pointerStartTime);
    }
    //endregion

    // Static utility for other gates that need time adjustment zones


    public void buildInteractionZoneList(List<InteractionZone> zones) {
        super.buildInteractionZoneList(zones);
        RedstoneTimerGateTile.addTimerAdjustmentInteractionZones(zones, this::addTimerMax);
    }

    public void buildToolTip(List<ZoneText> toolTip) {
        super.buildToolTip(toolTip);
        int tmax = pointerMax + 2; // Account for 2-tick high-state
        double smax = tmax * 0.05;
        toolTip.add(ZoneText.translatable(UL_TIMER_INTERVAL)
                .append(ZoneText.literal(": %.2fs (%d ".formatted(smax, tmax)))
                .append(ZoneText.translatable(UL_UNIT_ONLY_TICKS))
                .append(ZoneText.literal(")"))
                .withStyle(EngineContext.UNIFORM_GRAY));
    }

    public static void addTimerAdjustmentInteractionZones(List<InteractionZone> zones, Consumer<Integer> adjustFunc) {
        double w = 3 / 16D;
        double h = 0.5 / 16D;
        double l = 1.5 / 16D;
        double x = 0 / 16D;
        double y = 2 / 16D;
        double z = 0 / 16D;

        int[] deltas = new int[] { 200, 20, 1, -1, -20, -200 };
        int split = deltas.length / 2;

        for (int i = 0; i < deltas.length; i++) {
            Cuboid6 box = new Cuboid6(0, 0, 0, w, h, l);

            if (i < split) { // First 'split' buttons on top edge
                box.add(x, y, z + (l * i));
            } else { // Last 'split' buttons on bottom edge
                box.add(x, y, 1 - (l * split) + (l * (i - split)));
            }

            int tDelta = deltas[i];
            double sDelta = tDelta * 0.05;
            ZoneText text = ZoneText.literal("%+.2fs".formatted(sDelta));
            ZoneText toolTip = ZoneText.literal("%+.2fs (%+d ".formatted(sDelta, tDelta))
                    .append(ZoneText.translatable(UL_UNIT_ONLY_TICKS))
                    .append(ZoneText.literal(")"))
                    .withStyle(EngineContext.UNIFORM_GRAY);

            zones.add(new SimpleInteractionZone.Builder()
                    .bounds(() -> box)
                    .boundingBoxLineWidth(2.0)
                    .leftClickAction(() -> adjustFunc.accept(tDelta))
                    .text(text)
                    .tooltip(toolTip)
                    .build());
        }
    }
}
