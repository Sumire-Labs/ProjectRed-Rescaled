// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.gates;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Transformation;
import mrtjp.projectred.fabrication.engine.BaseTile;
import mrtjp.projectred.fabrication.engine.IConnectableICTile;
import mrtjp.projectred.fabrication.engine.IRotatableICTile;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;
import java.util.Optional;


public abstract class GateTile extends BaseTile implements IConnectableICTile, IRotatableICTile {

    public static final int PACKET_ROTATION = 1;
    public static final int PACKET_SHAPE = 2;

    private byte connMask = 0;
    private byte gateShape = 0;
    private byte gateRotation = 0;

    private final ICGateTileType gateTileType;

    public GateTile(ICGateTileType gateTileType) {
        super(gateTileType.tileType);
        this.gateTileType = gateTileType;
    }

    public int getShape() {
        return gateShape & 0xFF;
    }

    public void setShape(int shape) {
        gateShape = (byte) shape;
    }

    public void preparePlacement(int r) {
        setRotation(r);
    }

    public void save(NBTTagCompound tag) {
        tag.setByte("orient", gateRotation);
        tag.setByte("shape", gateShape);
        tag.setByte("connMask", connMask);
    }

    public void load(NBTTagCompound tag) {
        gateRotation = tag.getByte("orient");
        gateShape = tag.getByte("shape");
        connMask = tag.getByte("connMask");
    }

    public void writeDesc(MCDataOutput out) {
        out.writeByte(gateRotation);
        out.writeByte(gateShape);
    }

    public void readDesc(MCDataInput in) {
        gateRotation = in.readByte();
        gateShape = in.readByte();
    }

    public void read(MCDataInput in, int key) {
        switch (key) {
            case PACKET_ROTATION -> gateRotation = in.readByte();
            case PACKET_SHAPE -> gateShape = in.readByte();
            default -> super.read(in, key);
        }
    }

    protected void sendRotationUpdate() {
        getWriteStream(PACKET_ROTATION).writeByte(gateRotation);
    }

    protected void sendShapeUpdate() {
        getWriteStream(PACKET_SHAPE).writeByte(gateShape);
    }

    protected void rotateAndSend() {
        setRotation((getRotation()+1)%4);
        updateConns();
        sendRotationUpdate();
        notifyNeighbors(0xF);
        getEditor().markTileChange();
    }

    protected void reflectAndSend() {
        // Reflection is implementation sepecific.
        // Override and call configureShapeAndSend with relected shape
    }

    protected void configureShapeAndSend(int newShape) {
        if (getShape() != newShape) {
            setShape(newShape);
            updateConns();
            sendShapeUpdate();
            notifyNeighbors(0xF);
            getEditor().markTileChange();
        }
    }

    public void onNeighborChanged() {
        super.onNeighborChanged();
        if (updateConns())
            getEditor().markTileChange();
    }

    public void onAdded() {
        super.onAdded();
        updateConns();
        onGatePlaced();
    }

    public void onRemoved() {
        super.onRemoved();
        notifyNeighbors(0xF);
    }

    protected void notifyNeighbors(int rMask) {
        int absRMask = toAbsoluteMask(rMask);
        for (int r = 0; r < 4; r++) if ((absRMask & 1 << r) != 0) {
            int absDir = IRotatableICTile.rotationToDir(r);
            getEditor().queueNeighborChange(getPos().offset(absDir));
        }
    }

    //region IRotatableICTile implementation

    public int getRotation() {
        return gateRotation & 0x3;
    }

    public void setRotation(int r) {
        gateRotation = (byte) (gateRotation & 0xFC | r);
    }
    //endregion

    //region IConnectableICTile implementation

    public int getConnMask() {
        return connMask & 0xFF;
    }

    public void setConnMask(int connMask) {
        this.connMask = (byte) connMask;
    }

    public IConnectableICTile getTileTowardsDir(int dir) {
        Optional<BaseTile> tile = getMap().getBaseTile(getPos().offset(dir));
        if (tile.isPresent() && tile.get() instanceof IConnectableICTile) return (IConnectableICTile) tile.get();
        return null;
    }

    public boolean canConnectTo(IConnectableICTile target, int towardsDir) {
        return canGateConnectTo(target, toInternalRotation(IRotatableICTile.dirToRotation(towardsDir)));
    }

    public void onMaskChanged() {
        // Gate rendering does not require connMask, so don't send to clients
    }
    //endregion

    //region IGateRenderKey implementation

    public int getOrientation() {
        return getRotation() & 0x3;
    }

    public int shape() {
        return this.gateShape;
    }
    //endregion

    //region GateTile logic override points
    protected void onGatePlaced() { }

    protected boolean canRotate() {
        return true;
    }

    protected boolean canReflect() {
        return false;
    }

    protected abstract boolean canGateConnectTo(IConnectableICTile target, int r);
    /** A redstone pin has one signal; insulated neighbours select that signal's colour port. */
    protected boolean acceptsPathPort(int direction,int port) {
        BaseTile neighbour=getMap().getBaseTile(getPos().offset(direction)).orElse(null);
        int required=neighbour instanceof mrtjp.projectred.fabrication.engine.IInsulatedConnectableICTile insulated
                ? insulated.getInsulatedColour() : 0;
        return port==required;
    }
    //endregion
}
