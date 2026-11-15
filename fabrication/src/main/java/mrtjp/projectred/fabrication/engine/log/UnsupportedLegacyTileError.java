package mrtjp.projectred.fabrication.engine.log;
import mrtjp.fengine.TileCoord;
import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import net.minecraft.nbt.NBTTagCompound;

/** Retains a legacy drawing while explicitly refusing a lossy conversion. */
public final class UnsupportedLegacyTileError extends SimpleLocatableProblem {
    public String legacyType="";
    public UnsupportedLegacyTileError() { super(CompileProblemType.LEGACY_TILE,CompileProblemSeverity.ERROR); }
    public UnsupportedLegacyTileError(TileCoord coordinate,String type) { this();coord=coordinate;legacyType=type; }
    public void save(NBTTagCompound tag) { super.save(tag);tag.setString("legacy_type",legacyType); }
    public void load(NBTTagCompound tag) { super.load(tag);legacyType=tag.getString("legacy_type"); }
    public void writeDesc(MCDataOutput out) { super.writeDesc(out);out.writeString(legacyType); }
    public void readDesc(MCDataInput in) { super.readDesc(in);legacyType=in.readString(); }
}
