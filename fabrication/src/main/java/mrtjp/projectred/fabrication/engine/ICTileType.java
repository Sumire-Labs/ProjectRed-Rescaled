package mrtjp.projectred.fabrication.engine;
import mrtjp.projectred.fabrication.engine.gates.*;
import mrtjp.projectred.fabrication.engine.wires.*;
import java.util.function.Supplier;
import static mrtjp.projectred.fabrication.engine.ICTileTypeOffsets.*;
public enum ICTileType {
    //@formatter:off
    REDSTONE_IO_GATE      (ID_OFFSET_IOGATE, "",      RedstoneIOGateTile::new),
    BUNDLED_COLOR_IO_GATE (ID_OFFSET_IOGATE + 1, "", BundledColorIOGateTile::new),
    BUNDLED_BUS_IO_GATE   (ID_OFFSET_IOGATE + 2, "",   BundledBusIOGateTile::new),
    ANALOG_IO_GATE        (ID_OFFSET_IOGATE + 3, "",        AnalogIOGateTile::new),

    OR_GATE               (ID_OFFSET_GATE, "",                ORGateTile::new),
    NOR_GATE              (ID_OFFSET_GATE + 1, "",               NORGateTile::new),
    NOT_GATE              (ID_OFFSET_GATE + 2, "",               NOTGateTile::new),
    AND_GATE              (ID_OFFSET_GATE + 3, "",               ANDGateTile::new),
    NAND_GATE             (ID_OFFSET_GATE + 4, "",              NANDGateTile::new),
    XOR_GATE              (ID_OFFSET_GATE + 5, "",               XORGateTile::new),
    XNOR_GATE             (ID_OFFSET_GATE + 6, "",              XNORGateTile::new),
    BUFFER_GATE           (ID_OFFSET_GATE + 7, "",            BufferGateTile::new),
    MULTIPLEXER_GATE      (ID_OFFSET_GATE + 8, "",       MultiplexerGateTile::new),
    PULSE_GATE            (ID_OFFSET_GATE + 9, "",             PulseGateTile::new),
    REPEATER_GATE         (ID_OFFSET_GATE + 10, "",          RepeaterGateTile::new),
    RANDOMIZER_GATE       (ID_OFFSET_GATE + 11, "",        RandomizerGateTile::new),
    SR_LATCH_GATE         (ID_OFFSET_GATE + 12, "",          SRLatchGateTile::new),
    TOGGLE_LATCH_GATE     (ID_OFFSET_GATE + 13, "",      ToggleLatchGateTile::new),
    TRANSPARENT_LATCH_GATE(ID_OFFSET_GATE + 14, "", TransparentLatchGateTile::new),
    TIMER_GATE            (ID_OFFSET_GATE + 15, "",             TimerGateTile::new),
    SEQUENCER_GATE        (ID_OFFSET_GATE + 16, "",         SequencerGateTile::new),
    COUNTER_GATE          (ID_OFFSET_GATE + 17, "",           CounterGateTile::new),
    STATE_CELL_GATE       (ID_OFFSET_GATE + 18, "",        StateCellGateTile::new),
    SYNCHRONIZER_GATE     (ID_OFFSET_GATE + 19, "",      SynchronizerGateTile::new),

    RED_ALLOY_WIRE           (ID_OFFSET_WIRE_RED, "",           RedAlloyWireTile::new),
    INSULATED_WHITE_WIRE     (ID_OFFSET_WIRE_RED + 1, "",      () -> new InsulatedWireTile(0)),
    INSULATED_ORANGE_WIRE    (ID_OFFSET_WIRE_RED + 2, "",     () -> new InsulatedWireTile(1)),
    INSULATED_MAGENTA_WIRE   (ID_OFFSET_WIRE_RED + 3, "",    () -> new InsulatedWireTile(2)),
    INSULATED_LIGHT_BLUE_WIRE(ID_OFFSET_WIRE_RED + 4, "", () -> new InsulatedWireTile(3)),
    INSULATED_YELLOW_WIRE    (ID_OFFSET_WIRE_RED + 5, "",     () -> new InsulatedWireTile(4)),
    INSULATED_LIME_WIRE      (ID_OFFSET_WIRE_RED + 6, "",       () -> new InsulatedWireTile(5)),
    INSULATED_PINK_WIRE      (ID_OFFSET_WIRE_RED + 7, "",       () -> new InsulatedWireTile(6)),
    INSULATED_GRAY_WIRE      (ID_OFFSET_WIRE_RED + 8, "",       () -> new InsulatedWireTile(7)),
    INSULATED_LIGHT_GRAY_WIRE(ID_OFFSET_WIRE_RED + 9, "", () -> new InsulatedWireTile(8)),
    INSULATED_CYAN_WIRE      (ID_OFFSET_WIRE_RED + 10, "",       () -> new InsulatedWireTile(9)),
    INSULATED_PURPLE_WIRE    (ID_OFFSET_WIRE_RED + 11, "",     () -> new InsulatedWireTile(10)),
    INSULATED_BLUE_WIRE      (ID_OFFSET_WIRE_RED + 12, "",       () -> new InsulatedWireTile(11)),
    INSULATED_BROWN_WIRE     (ID_OFFSET_WIRE_RED + 13, "",      () -> new InsulatedWireTile(12)),
    INSULATED_GREEN_WIRE     (ID_OFFSET_WIRE_RED + 14, "",      () -> new InsulatedWireTile(13)),
    INSULATED_RED_WIRE       (ID_OFFSET_WIRE_RED + 15, "",        () -> new InsulatedWireTile(14)),
    INSULATED_BLACK_WIRE     (ID_OFFSET_WIRE_RED + 16, "",      () -> new InsulatedWireTile(15)),

    BUNDLED_NEUTRAL_WIRE   (ID_OFFSET_WIRE_BUNDLED, "",    () -> new BundledWireTile(-1)),
    BUNDLED_WHITE_WIRE     (ID_OFFSET_WIRE_BUNDLED + 1, "",      () -> new BundledWireTile(0)),
    BUNDLED_ORANGE_WIRE    (ID_OFFSET_WIRE_BUNDLED + 2, "",     () -> new BundledWireTile(1)),
    BUNDLED_MAGENTA_WIRE   (ID_OFFSET_WIRE_BUNDLED + 3, "",    () -> new BundledWireTile(2)),
    BUNDLED_LIGHT_BLUE_WIRE(ID_OFFSET_WIRE_BUNDLED + 4, "", () -> new BundledWireTile(3)),
    BUNDLED_YELLOW_WIRE    (ID_OFFSET_WIRE_BUNDLED + 5, "",     () -> new BundledWireTile(4)),
    BUNDLED_LIME_WIRE      (ID_OFFSET_WIRE_BUNDLED + 6, "",       () -> new BundledWireTile(5)),
    BUNDLED_PINK_WIRE      (ID_OFFSET_WIRE_BUNDLED + 7, "",       () -> new BundledWireTile(6)),
    BUNDLED_GRAY_WIRE      (ID_OFFSET_WIRE_BUNDLED + 8, "",       () -> new BundledWireTile(7)),
    BUNDLED_LIGHT_GRAY_WIRE(ID_OFFSET_WIRE_BUNDLED + 9, "", () -> new BundledWireTile(8)),
    BUNDLED_CYAN_WIRE      (ID_OFFSET_WIRE_BUNDLED + 10, "",       () -> new BundledWireTile(9)),
    BUNDLED_PURPLE_WIRE    (ID_OFFSET_WIRE_BUNDLED + 11, "",     () -> new BundledWireTile(10)),
    BUNDLED_BLUE_WIRE      (ID_OFFSET_WIRE_BUNDLED + 12, "",       () -> new BundledWireTile(11)),
    BUNDLED_BROWN_WIRE     (ID_OFFSET_WIRE_BUNDLED + 13, "",      () -> new BundledWireTile(12)),
    BUNDLED_GREEN_WIRE     (ID_OFFSET_WIRE_BUNDLED + 14, "",      () -> new BundledWireTile(13)),
    BUNDLED_RED_WIRE       (ID_OFFSET_WIRE_BUNDLED + 15, "",        () -> new BundledWireTile(14)),
    BUNDLED_BLACK_WIRE     (ID_OFFSET_WIRE_BUNDLED + 16, "",      () -> new BundledWireTile(15));
    //@formatter:on
    public static final int ID_MAP_FORMAT = 1;
    private final int id;
    private final Supplier<BaseTile> factory;
    ICTileType(int id, String name, Supplier<BaseTile> factory) { this.id=id; this.factory=factory; }
    public int getID() { return id; }
    public String getUnlocalizedName() { return "gui.projectred.ic." + name().toLowerCase(java.util.Locale.ROOT); }
    public BaseTile create() { return factory.get(); }
    public static BaseTile createFromId(int id) {
        for (ICTileType type:values()) if(type.id==id) return type.create();
        throw new IllegalArgumentException("Unknown IC tile ID " + id);
    }
    public static BaseTile createFromIdAndFormat(int id, int format) {
        if (format < 0 || format > ID_MAP_FORMAT) throw new IllegalArgumentException("Unknown IC tile format " + format);
        return createFromId(format==0 && id==0 ? 1 : id);
    }
}