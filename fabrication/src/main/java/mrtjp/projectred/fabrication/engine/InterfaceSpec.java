// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Arrays;
import java.util.Set;

public class InterfaceSpec {

    // Empty instance
    public static final InterfaceSpec EMPTY = new InterfaceSpec(
            new ICInterfaceType[]{ICInterfaceType.NC, ICInterfaceType.NC, ICInterfaceType.NC, ICInterfaceType.NC},
            0, 0, 0);

    private final ICInterfaceType[] sideInterfaces;
    private final int redstoneMask; // OOOO IIII
    private final int bundledMask; // OOOO IIII
    private final int analogMask; // OOOO IIII



    // Constructor for immutable creation
    private InterfaceSpec(ICInterfaceType[] sideInterfaces, int redstoneMask, int bundledMask, int analogMask) {
        this.sideInterfaces = Arrays.copyOf(sideInterfaces, 4);
        this.redstoneMask = redstoneMask;
        this.bundledMask = bundledMask;
        this.analogMask = analogMask;
    }

    //region Save/load
    public void save(NBTTagCompound tag) {
        for (int r = 0; r < 4; r++) {
            tag.setByte("if" + r, (byte) sideInterfaces[r].getId());
        }
        tag.setByte("rmask", (byte) redstoneMask);
        tag.setByte("bmask", (byte) bundledMask);
        tag.setByte("amask", (byte) analogMask);
    }

    public static InterfaceSpec load(NBTTagCompound tag) {
        ICInterfaceType[] sideInterfaces = new ICInterfaceType[4];
        for (int r = 0; r < 4; r++) {
            sideInterfaces[r] = ICInterfaceType.fromId(tag.getByte("if" + r) & 0xFF);
        }
        int redstoneMask = tag.getByte("rmask") & 0xFF;
        int bundledMask = tag.getByte("bmask") & 0xFF;
        int analogMask = tag.getByte("amask") & 0xFF;

        return new InterfaceSpec(sideInterfaces, redstoneMask, bundledMask, analogMask);
    }

    public void writeDesc(MCDataOutput packet) {
        for (int r = 0; r < 4; r++) {
            packet.writeByte(sideInterfaces[r].getId());
        }
        packet.writeByte(bundledMask);
        packet.writeByte(redstoneMask);
        packet.writeByte(analogMask);
    }

    public static InterfaceSpec readDesc(MCDataInput packet) {
        ICInterfaceType[] sideInterfaces = new ICInterfaceType[4];
        for (int r = 0; r < 4; r++) {
            sideInterfaces[r] = ICInterfaceType.fromId(packet.readByte() & 0xFF);
        }
        int bundledMask = packet.readByte();
        int redstoneMask = packet.readByte();
        int analogMask = packet.readByte();

        return new InterfaceSpec(sideInterfaces, redstoneMask, bundledMask, analogMask);
    }
    //endregion

    public static InterfaceSpec fromIOTiles(Set<IIOConnectionTile> ioTiles) {
        ICInterfaceType[] sideInterfaces = new ICInterfaceType[4];
        Arrays.fill(sideInterfaces, ICInterfaceType.NC);
        int bundledMask = 0;
        int redstoneMask = 0;
        int analogMask = 0;

        for (IIOConnectionTile t : ioTiles) {
            int side = t.getIOSide();
            ICInterfaceType type = t.getInterfaceType();
            int dir = t.isInputIOMode() ? 0x1 : 0x10;

            sideInterfaces[side] = type;

            switch (type) {
                case REDSTONE -> redstoneMask |= dir << side;
                case BUNDLED -> bundledMask |= dir << side;
                case ANALOG -> analogMask |= dir << side;
            }
        }

        return new InterfaceSpec(sideInterfaces, redstoneMask, bundledMask, analogMask);
    }

    public ICInterfaceType getInterfaceType(int side) {
        return sideInterfaces[side];
    }

    public int getInputMask() {
        return (redstoneMask | bundledMask | analogMask) & 0xF;
    }

    public int getOutputMask() {
        return (redstoneMask | bundledMask | analogMask) >> 4 & 0xF;
    }

    public boolean isInput(int side) {
        return ((redstoneMask | bundledMask | analogMask) & 1 << side) != 0;
    }

    public boolean isOutput(int side) {
        return ((redstoneMask | bundledMask | analogMask) & 0x10 << side) != 0;
    }

    public int getRedstoneInputMask() {
        return redstoneMask & 0xF;
    }

    public int getRedstoneOutputMask() {
        return redstoneMask >> 4 & 0xF;
    }

    public int getBundledInputMask() {
        return bundledMask & 0xF;
    }

    public int getBundledOutputMask() {
        return bundledMask >> 4 & 0xF;
    }

    public int getAnalogInputMask() {
        return analogMask & 0xF;
    }

    public int getAnalogOutputMask() {
        return analogMask >> 4 & 0xF;
    }

    //region Utilities
    public void saveTo(NBTTagCompound tag, String key) {
        NBTTagCompound tag1 = new NBTTagCompound();
        save(tag1);
        tag.setTag(key, tag1);
    }

    public static InterfaceSpec loadFrom(NBTTagCompound tag, String key) {
        NBTTagCompound tag1 = tag.getCompoundTag(key);
        return load(tag1);
    }

    public static InterfaceSpec createFrom(NBTTagCompound tag, String key) {
        return loadFrom(tag, key);
    }
    //endregion
}
