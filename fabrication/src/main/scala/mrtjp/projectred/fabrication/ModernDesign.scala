package mrtjp.projectred.fabrication

import mrtjp.core.vec.Point
import mrtjp.fengine.TileCoord
import mrtjp.projectred.fabrication.engine.{BaseTile,EngineContext,ICTileType}
import mrtjp.projectred.fabrication.engine.gates.{GateTile,RedstoneGateTile}
import mrtjp.projectred.fabrication.engine.log.*
import net.minecraft.nbt.NBTTagCompound
import scala.jdk.CollectionConverters.*

/** Explicit conversion of the saved 1.12 drawing. The original drawing remains authoritative for editing. */
final class ModernDesign(val editor:ICTileMapEditor,val context:EngineContext = new EngineContext) {
    private val gateTypes = Map(
        ICGateDefinition.OR -> ICTileType.OR_GATE, ICGateDefinition.NOR -> ICTileType.NOR_GATE,
        ICGateDefinition.NOT -> ICTileType.NOT_GATE, ICGateDefinition.AND -> ICTileType.AND_GATE,
        ICGateDefinition.NAND -> ICTileType.NAND_GATE, ICGateDefinition.XOR -> ICTileType.XOR_GATE,
        ICGateDefinition.XNOR -> ICTileType.XNOR_GATE, ICGateDefinition.Buffer -> ICTileType.BUFFER_GATE,
        ICGateDefinition.Multiplexer -> ICTileType.MULTIPLEXER_GATE, ICGateDefinition.Pulse -> ICTileType.PULSE_GATE,
        ICGateDefinition.Repeater -> ICTileType.REPEATER_GATE, ICGateDefinition.Randomizer -> ICTileType.RANDOMIZER_GATE,
        ICGateDefinition.SRLatch -> ICTileType.SR_LATCH_GATE, ICGateDefinition.ToggleLatch -> ICTileType.TOGGLE_LATCH_GATE,
        ICGateDefinition.TransparentLatch -> ICTileType.TRANSPARENT_LATCH_GATE, ICGateDefinition.Timer -> ICTileType.TIMER_GATE,
        ICGateDefinition.Sequencer -> ICTileType.SEQUENCER_GATE, ICGateDefinition.Counter -> ICTileType.COUNTER_GATE,
        ICGateDefinition.StateCell -> ICTileType.STATE_CELL_GATE, ICGateDefinition.Synchronizer -> ICTileType.SYNCHRONIZER_GATE,
        ICGateDefinition.IOSimple -> ICTileType.REDSTONE_IO_GATE, ICGateDefinition.IOAnalog -> ICTileType.ANALOG_IO_GATE,
        ICGateDefinition.IOBundled -> ICTileType.BUNDLED_COLOR_IO_GATE,
        ICGateDefinition.IOBundledBus -> ICTileType.BUNDLED_BUS_IO_GATE)
    val unsupported = scala.collection.mutable.ArrayBuffer.empty[UnsupportedLegacyTileError]
    def coordinate(p:Point):TileCoord = new TileCoord(p.x-editor.size.width/2,0,p.y-editor.size.height/2)
    def point(p:TileCoord):Point = Point(p.x+editor.size.width/2,p.z+editor.size.height/2)
    private def kind(tile:ICTile):Option[BaseTile] = tile match {
        case _:AlloyWireICTile => Some(ICTileType.RED_ALLOY_WIRE.create())
        case wire:InsulatedWireICTile => Some(ICTileType.createFromId(ICTileType.INSULATED_WHITE_WIRE.getID+(wire.colour&255)))
        case wire:BundledCableICTile => Some(ICTileType.createFromId(ICTileType.BUNDLED_NEUTRAL_WIRE.getID+wire.colour+1))
        case gate:GateICTile => gateTypes.get(ICGateDefinition(gate.subID)).map(_.create())
        case _ => None
    }
    def rebuild():Unit = {
        context.getTileMap.removeAll()
        unsupported.clear()
        val min = new TileCoord(-editor.size.width/2,0,-editor.size.height/2)
        context.getTileMap.setBounds(min,min.add(editor.size.width-1,0,editor.size.height-1))
        for ((x,y),old) <- editor.tileMapContainer.tiles do {
            val pos=coordinate(Point(x,y))
            kind(old) match {
                case Some(tile) =>
                    val original=new NBTTagCompound
                    old.save(original)
                    val converted=new NBTTagCompound
                    tile.save(converted)
                    for key <- Seq("orient","shape","state","val","max","inc","dec") if original.hasKey(key) do
                        converted.setTag(key,original.getTag(key).copy())
                    if original.hasKey("pmax") then converted.setInteger("tmax",original.getInteger("pmax"))
                    if original.hasKey("freq") then converted.setByte("io_bit",original.getByte("freq"))
                    tile.load(converted)
                    context.getTileMap.addTile(pos,tile)
                case None => unsupported += new UnsupportedLegacyTileError(pos,old.getClass.getSimpleName)
            }
        }
        context.getTileMap.getBaseTileEntries.asScala.foreach(_.getValue.onAdded())
        context.flushNeighborChanges()
        context.getStateMachine.markEdited()
        context.tileChanged = tile => mirror(tile)
    }
    def mirror(tile:BaseTile):Unit = {
        val pos=point(tile.getPos)
        editor.getTile(pos) match {
            case old:RedstoneGateICTile => tile match {
                case gate:RedstoneGateTile if !gate.isInstanceOf[engine.gates.BundledBusIOGateTile] =>
                    if old.shape != gate.getShape then { old.setShape(gate.getShape); old.sendShapeUpdate() }
                    if old.state != gate.getState then { old.setState(gate.getState); old.sendStateUpdate() }
                    (old.getLogicPrimitive,gate) match {
                        case (counter:Counter,model:engine.gates.CounterGateTile) =>
                            if counter.currentValue != model.pointerValue() then {
                                counter.currentValue=model.pointerValue(); counter.sendCurrentValueUpdate()
                            }
                        case (timer:TTimerICGateLogic,model:engine.gates.RedstoneTimerGateTile) =>
                            val start=if model.isPointerStarted then context.getStateMachine.getSimSystemTime-model.pointerValue() else -1L
                            if timer.pointer_start != start then { timer.pointer_start=start;timer.sendPointerUpdate() }
                        case _ =>
                    }
                case bus:engine.gates.BundledBusIOGateTile =>
                    val gate=old.asInstanceOf[IOGateICTile]
                    val logic=gate.getLogic[BundledBusIOGateTileLogic]
                    val mask=(bus.bInput2() | bus.bOutput2())&65535
                    if logic.busMask != mask then { logic.busMask=mask;logic.sendBusUpdate() }
                    val state=if mask == 0 then 0 else if bus.isInputIOMode then 0x40 else 4
                    if old.state != state then { old.setState(state);old.sendStateUpdate() }
                case _ =>
            }
            case old:RedwireICTile => tile match {
                case wire:engine.wires.RedstoneWireTile =>
                    if old.signal != wire.getSignal then { old.signal=wire.getSignal;old.sendSignalUpdate() }
                case _ =>
            }
            case _ =>
        }
    }
    def updateDiagnostics():Unit = {
        val logger=editor.simEngineContainer.logger
        logger.clear()
        for problem <- context.getStateMachine.getCompilerLog.getProblems.asScala do {
            val points=problem match {
                case p:SimpleLocatableProblem => Seq(point(p.coord))
                case p:MultipleDriversError => Seq(point(p.coord))
                case p:MultiPositionProblem => p.coordList.asScala.toSeq.map(point)
                case _ => Seq.empty
            }
            val message=problem match {
                case p:UnsupportedLegacyTileError => s"Legacy component needs conversion: ${p.legacyType}"
                case _:NoInputsError => "IC has no inputs"
                case _:NoOutputsError => "IC has no outputs"
                case _:MultipleDriversError => "Multiple drivers connected"
                case _:IODirectionMismatchError => "Conflicting input/output direction on one side"
                case _:IOTypeMismatchError => "Conflicting interface types on one side"
                case _:DeadWireWarning => "Wire has no driver"
                case _:DeadGateWarning => "Gate has no outputs"
            }
            if problem.severity == CompileProblemSeverity.ERROR then logger.logError(points,message)
            else logger.logWarning(points,message)
        }
    }
}
