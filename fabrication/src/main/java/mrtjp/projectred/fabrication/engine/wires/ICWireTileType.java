// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.wires;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import mrtjp.projectred.fabrication.engine.ICTileType;

public enum ICWireTileType {

    //@formatter:off
    RED_ALLOY(ICTileType.RED_ALLOY_WIRE),

    INSULATED_WHITE     (ICTileType.INSULATED_WHITE_WIRE),
    INSULATED_ORANGE    (ICTileType.INSULATED_ORANGE_WIRE),
    INSULATED_MAGENTA   (ICTileType.INSULATED_MAGENTA_WIRE),
    INSULATED_LIGHT_BLUE(ICTileType.INSULATED_LIGHT_BLUE_WIRE),
    INSULATED_YELLOW    (ICTileType.INSULATED_YELLOW_WIRE),
    INSULATED_LIME      (ICTileType.INSULATED_LIME_WIRE),
    INSULATED_PINK      (ICTileType.INSULATED_PINK_WIRE),
    INSULATED_GRAY      (ICTileType.INSULATED_GRAY_WIRE),
    INSULATED_LIGHT_GRAY(ICTileType.INSULATED_LIGHT_GRAY_WIRE),
    INSULATED_CYAN      (ICTileType.INSULATED_CYAN_WIRE),
    INSULATED_PURPLE    (ICTileType.INSULATED_PURPLE_WIRE),
    INSULATED_BLUE      (ICTileType.INSULATED_BLUE_WIRE),
    INSULATED_BROWN     (ICTileType.INSULATED_BROWN_WIRE),
    INSULATED_GREEN     (ICTileType.INSULATED_GREEN_WIRE),
    INSULATED_RED       (ICTileType.INSULATED_RED_WIRE),
    INSULATED_BLACK     (ICTileType.INSULATED_BLACK_WIRE),

    BUNDLED_NEUTRAL   (ICTileType.BUNDLED_NEUTRAL_WIRE),

    BUNDLED_WHITE     (ICTileType.BUNDLED_WHITE_WIRE),
    BUNDLED_ORANGE    (ICTileType.BUNDLED_ORANGE_WIRE),
    BUNDLED_MAGENTA   (ICTileType.BUNDLED_MAGENTA_WIRE),
    BUNDLED_LIGHT_BLUE(ICTileType.BUNDLED_LIGHT_BLUE_WIRE),
    BUNDLED_YELLOW    (ICTileType.BUNDLED_YELLOW_WIRE),
    BUNDLED_LIME      (ICTileType.BUNDLED_LIME_WIRE),
    BUNDLED_PINK      (ICTileType.BUNDLED_PINK_WIRE),
    BUNDLED_GRAY      (ICTileType.BUNDLED_GRAY_WIRE),
    BUNDLED_LIGHT_GRAY(ICTileType.BUNDLED_LIGHT_GRAY_WIRE),
    BUNDLED_CYAN      (ICTileType.BUNDLED_CYAN_WIRE),
    BUNDLED_PURPLE    (ICTileType.BUNDLED_PURPLE_WIRE),
    BUNDLED_BLUE      (ICTileType.BUNDLED_BLUE_WIRE),
    BUNDLED_BROWN     (ICTileType.BUNDLED_BROWN_WIRE),
    BUNDLED_GREEN     (ICTileType.BUNDLED_GREEN_WIRE),
    BUNDLED_RED       (ICTileType.BUNDLED_RED_WIRE),
    BUNDLED_BLACK     (ICTileType.BUNDLED_BLACK_WIRE),
    ;
    //@formatter:on

    public static final ICWireTileType[] INSULATED = {
            INSULATED_WHITE,
            INSULATED_ORANGE,
            INSULATED_MAGENTA,
            INSULATED_LIGHT_BLUE,
            INSULATED_YELLOW,
            INSULATED_LIME,
            INSULATED_PINK,
            INSULATED_GRAY,
            INSULATED_LIGHT_GRAY,
            INSULATED_CYAN,
            INSULATED_PURPLE,
            INSULATED_BLUE,
            INSULATED_BROWN,
            INSULATED_GREEN,
            INSULATED_RED,
            INSULATED_BLACK };

    public static final ICWireTileType[] BUNDLED_COLOURED = {
            BUNDLED_WHITE,
            BUNDLED_ORANGE,
            BUNDLED_MAGENTA,
            BUNDLED_LIGHT_BLUE,
            BUNDLED_YELLOW,
            BUNDLED_LIME,
            BUNDLED_PINK,
            BUNDLED_GRAY,
            BUNDLED_LIGHT_GRAY,
            BUNDLED_CYAN,
            BUNDLED_PURPLE,
            BUNDLED_BLUE,
            BUNDLED_BROWN,
            BUNDLED_GREEN,
            BUNDLED_RED,
            BUNDLED_BLACK };

    public final ICTileType tileType;
    ICWireTileType(ICTileType tileType) { this.tileType = tileType; }
}
