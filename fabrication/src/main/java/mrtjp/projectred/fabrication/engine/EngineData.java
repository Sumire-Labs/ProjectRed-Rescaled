package mrtjp.projectred.fabrication.engine;
import mrtjp.fengine.TileCoord;
import net.minecraft.nbt.NBTTagCompound;

public final class EngineData {
    public static final int MAX_JSON_BYTES = 32*1024*1024;
    public static final String KEY_COMPLETED_STEPS="completed_steps", KEY_CURRENT_PATH="current_path",
        KEY_PROBLEMS_LIST="problems", KEY_WARNING_COUNT="warning_count", KEY_ERROR_COUNT="error_count";
    private EngineData() { }
    public static NBTTagCompound tileCoordToNBT(TileCoord p) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("x", (byte) p.x); tag.setByte("y", (byte) p.y); tag.setByte("z", (byte) p.z);
        return tag;
    }
    public static TileCoord tileCoordFromNBT(NBTTagCompound tag) {
        return new TileCoord(tag.getByte("x"),tag.getByte("y"),tag.getByte("z"));
    }
    public static void saveTileCoord(NBTTagCompound tag, String key, TileCoord p) { tag.setTag(key,tileCoordToNBT(p)); }
    public static TileCoord loadTileCoord(NBTTagCompound tag, String key) { return tileCoordFromNBT(tag.getCompoundTag(key)); }
    public static void saveTileCoordList(NBTTagCompound tag, String key, java.util.List<TileCoord> coordinates) {
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        for (TileCoord p:coordinates) list.appendTag(tileCoordToNBT(p));
        tag.setTag(key,list);
    }
    public static void loadTileCoordList(NBTTagCompound tag, String key, java.util.List<TileCoord> coordinates) {
        coordinates.clear();
        var list=tag.getTagList(key,10);
        for(int i=0;i<list.tagCount();i++) coordinates.add(tileCoordFromNBT(list.getCompoundTagAt(i)));
    }
    /** 1.12 NBT strings use modified UTF with a 65535-byte limit. Engine JSON is stored as bounded gzip. */
    public static void putJson(NBTTagCompound tag,String key,String json) {
        byte[] bytes=json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(bytes.length>MAX_JSON_BYTES) throw new IllegalArgumentException("IC JSON exceeds its size limit");
        try {
            var buffer=new java.io.ByteArrayOutputStream();
            try(var gzip=new java.util.zip.GZIPOutputStream(buffer)) { gzip.write(bytes); }
            tag.removeTag(key);tag.setByteArray(key+"_gzip",buffer.toByteArray());
        } catch(java.io.IOException e) { throw new IllegalStateException("Cannot encode IC JSON",e); }
    }
    public static String getJson(NBTTagCompound tag,String key) {
        if(!tag.hasKey(key+"_gzip",7)) return tag.getString(key);
        try(var gzip=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(tag.getByteArray(key+"_gzip")))) {
            byte[] bytes=gzip.readNBytes(MAX_JSON_BYTES+1);
            if(bytes.length>MAX_JSON_BYTES) throw new IllegalArgumentException("Expanded IC JSON exceeds its size limit");
            return new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        } catch(java.io.IOException e) { throw new IllegalArgumentException("Invalid compressed IC JSON",e); }
    }
}
