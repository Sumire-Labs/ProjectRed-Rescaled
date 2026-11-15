package mrtjp.projectred.fabrication.engine.zones;
import codechicken.lib.vec.Cuboid6;
import java.util.List;
public interface InteractionZone {
    Cuboid6 getBounds();
    void onLeftClick();
    void onRightClick();
    void buildToolTip(List<ZoneText> tooltip);
    boolean canRespondToKey(int key,int flags);
    boolean onKeyPressed(int key,int flags);
    default int highlightColor(){return 0x6699FFFF;}
    default int outlineColor(){return 0xFFFFFFFF;}
    default double lineWidth(){return 2;}
    default ZoneText label(){return null;}
    default Object icon(){return null;}
}
