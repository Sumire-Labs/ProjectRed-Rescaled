package mrtjp.projectred.fabrication.engine;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import mrtjp.fengine.TileCoord;
import mrtjp.fengine.tiles.FETile;
import net.minecraft.nbt.NBTTagCompound;

/** Platform-neutral tile lifecycle from the 1.21.1 engine. Rendering belongs to the editor adapter. */
public abstract class BaseTile implements FETile {
    private final ICTileType tileType;
    private BaseTileMap map;
    private TileCoord pos;
    protected BaseTile(ICTileType tileType) { this.tileType = tileType; }
    public void bindMap(BaseTileMap map, TileCoord pos) { this.map = map; this.pos = pos; }
    public void unbindMap() { map = null; pos = null; }
    public BaseTileMap getMap() { return map; }
    public EngineContext getEditor() { return map.getEditor(); }
    public TileCoord getPos() { return pos; }
    public ICTileType getTileType() { return tileType; }
    public abstract void save(NBTTagCompound tag);
    public abstract void load(NBTTagCompound tag);
    public abstract void writeDesc(MCDataOutput out);
    public abstract void readDesc(MCDataInput in);
    public MCDataOutput getWriteStream(int key) { return getEditor().getTileStream(this, key); }
    public void read(MCDataInput in, int key) { if (key == 0) readDesc(in); }
    public void sendDescUpdate() { writeDesc(getWriteStream(0)); }
    public void update() { }
    public void onAdded() { }
    public void onRemoved() { }
    public void onNeighborChanged() { }
    public void onSimRegistersChanged(int mask, ICSimulationContainer container) { }
}
