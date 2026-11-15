// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.wires;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.vec.Transformation;
import mrtjp.projectred.fabrication.engine.BaseTile;
import mrtjp.projectred.fabrication.engine.ICTileType;
import mrtjp.projectred.fabrication.engine.IConnectableICTile;
import mrtjp.projectred.fabrication.engine.IRotatableICTile;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Optional;

public abstract class WireTile extends BaseTile implements IConnectableICTile {

    private static final int PACKET_CONN_MASK = 1;

    private final int renderType;

    public WireTile(ICTileType tileType, int renderType) {
        super(tileType);
        this.renderType = renderType;
    }

    private byte connMask = 0;

    public int getWireType() {
        return renderType;
    }

    public int getConnMask() {
        return connMask & 0xFF;
    }

    public void setConnMask(int connMask) {
        this.connMask = (byte) connMask;
    }

    public void save(NBTTagCompound tag) {
        tag.setByte("connMask", connMask);
    }

    public void load(NBTTagCompound tag) {
        connMask = tag.getByte("connMask");
    }

    public void writeDesc(MCDataOutput out) {
        out.writeByte(connMask);
    }

    public void readDesc(MCDataInput in) {
        connMask = in.readByte();
    }

    public void read(MCDataInput in, int key) {
        switch (key) {
            case PACKET_CONN_MASK:
                connMask = in.readByte();
                break;
            default:
                super.read(in, key);
        }
    }

    protected void sendConnUpdate() {
        getWriteStream(PACKET_CONN_MASK).writeByte(connMask);
    }

    public void onMaskChanged() {
        sendConnUpdate();
        getEditor().markTileChange();
    }

    public void onNeighborChanged() {
        super.onNeighborChanged();
        updateConns();
    }

    public void onAdded() {
        super.onAdded();
        updateConns();
    }

    public void onRemoved() {
        super.onRemoved();
        for (int s = 0; s < 6; s++) {
            getEditor().queueNeighborChange(getPos().offset(s));
        }
    }

    public IConnectableICTile getTileTowardsDir(int dir) {
        Optional<BaseTile> tile = getMap().getBaseTile(getPos().offset(dir));
        if (tile.isPresent() && tile.get() instanceof IConnectableICTile) return (IConnectableICTile) tile.get();
        return null;
    }

    protected int getRenderHue() {
        return -1;
    }

    protected int getTextureIndex() {
        return 0;
    }

}
