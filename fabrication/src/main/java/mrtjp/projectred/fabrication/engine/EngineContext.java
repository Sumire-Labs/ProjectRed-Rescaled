package mrtjp.projectred.fabrication.engine;

import codechicken.lib.data.MCDataOutput;
import codechicken.lib.data.MCDataOutputWrapper;
import mrtjp.fengine.TileCoord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Hosts the portable engine while the world/editor adapter owns networking and rendering. */
public final class EngineContext {
    public static final net.minecraft.util.text.Style UNIFORM_GRAY=new net.minecraft.util.text.Style().setColor(net.minecraft.util.text.TextFormatting.GRAY);
    public static final Logger LOGGER = LogManager.getLogger("ProjectRed Fabrication");
    private final BaseTileMap tileMap = new BaseTileMap(this);
    private final EngineCompiler stateMachine = new EngineCompiler(this);
    private final MCDataOutput discarded = new MCDataOutputWrapper(OutputStream.nullOutputStream());
    private final Set<TileCoord> neighborChanges = new LinkedHashSet<>();
    public Consumer<BaseTile> tileChanged = tile -> { };
    public Runnable schematicChanged = () -> { };
    public java.util.function.IntFunction<MCDataOutput> compilerEvents = key -> discarded;
    public BaseTileMap getTileMap() { return tileMap; }
    public EngineCompiler getStateMachine() { return stateMachine; }
    public MCDataOutput getTileStream(BaseTile tile, int key) { tileChanged.accept(tile); return discarded; }
    public MCDataOutput discardStream() { return discarded; }
    public MCDataOutput compilerStream(int key) { return compilerEvents.apply(key); }
    public void markTileChange() { stateMachine.markEdited(); schematicChanged.run(); }
    public void queueNeighborChange(TileCoord coord) { neighborChanges.add(coord); }
    public void flushNeighborChanges() {
        while (!neighborChanges.isEmpty()) {
            TileCoord coord = neighborChanges.iterator().next();
            neighborChanges.remove(coord);
            tileMap.getBaseTile(coord).ifPresent(BaseTile::onNeighborChanged);
        }
    }
    public void notifySimulation(int mask) {
        for (var entry:tileMap.getBaseTileEntries()) entry.getValue().onSimRegistersChanged(mask,stateMachine.getSimulation());
    }
    public void save(net.minecraft.nbt.NBTTagCompound tag) {
        var mapTag=new net.minecraft.nbt.NBTTagCompound(); tileMap.save(mapTag); tag.setTag("tile_map",mapTag);
        var stateTag=new net.minecraft.nbt.NBTTagCompound(); stateMachine.save(stateTag); tag.setTag("state_machine",stateTag);
    }
    public void load(net.minecraft.nbt.NBTTagCompound tag) {
        neighborChanges.clear(); tileMap.load(tag.getCompoundTag("tile_map")); stateMachine.load(tag.getCompoundTag("state_machine"));
    }
}
