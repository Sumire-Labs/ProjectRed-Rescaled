package mrtjp.projectred.fabrication.engine;
public enum ICInterfaceType {
    NC, REDSTONE, BUNDLED, ANALOG;
    public int getId() { return ordinal(); }
    public static ICInterfaceType fromId(int id) { return values()[id]; }
    public String getUnlocalName() { return "gui.projectred.interface." + name().toLowerCase(java.util.Locale.ROOT); }
}
