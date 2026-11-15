package mrtjp.projectred.fabrication

import codechicken.lib.data.{MCDataInput,MCDataOutput}
import mrtjp.projectred.core.Configurator
import mrtjp.projectred.fabrication.engine.EngineContext
import net.minecraft.nbt.NBTTagCompound

/** World/editor adapter for the 1.21.1 compiler and simulation. */
class WorkbenchController(tile:TileICWorkbench) {
    var state = 0
    var autoCompileEnabled = true
    var autoCompileAvailable = true
    var modern:ModernDesign = null
    private var engineBackup:NBTTagCompound = null
    private val remoteLog = new EngineContext().getStateMachine.getCompilerLog
    var completedSteps = 0
    var totalSteps = 0
    tile.editor.externalSimulationActive = () => state == 3
    tile.editor.externalActivation = activate
    tile.editor.externalBusActivation = toggleBus
    def canTriggerCompile:Boolean = state == 1
    def simTime:Long = if modern == null then 0 else modern.context.getStateMachine.getSimSystemTime
    def compilerLog = if modern == null then remoteLog else modern.context.getStateMachine.getCompilerLog
    def writeLog(out:MCDataOutput):Unit = compilerLog.writeDesc(out)
    def readLog(in:MCDataInput):Unit = remoteLog.readDesc(in)
    def readLogEvents(in:MCDataInput):Unit = {
        var key=in.readUByte()
        while key != 255 do { remoteLog.readLogStream(in,key);key=in.readUByte() }
    }
    private def refreshAvailability():Unit = {
        val limit = Configurator.autoCompileTileLimit
        autoCompileAvailable = limit == -1 || limit > 0 && tile.editor.tileMapContainer.tiles.size <= limit
        autoCompileEnabled &&= autoCompileAvailable
    }
    private def changed():Unit = {
        tile.markDirty()
        if tile.getWorld != null && !tile.getWorld.isRemote then {
            val packet = tile.writeStream(7)
            writeDesc(packet)
            packet.sendToChunk(tile)
        }
    }
    def markEdited():Unit = {
        refreshAvailability()
        modern = null
        remoteLog.clear()
        completedSteps=0;totalSteps=0
        if tile.editor.tileMapContainer.tiles.nonEmpty || state != 0 then state = 1
        tile.editor.simNeedsRefresh=false // The workflow now owns this edit, including manual-compile waits.
        changed()
    }
    private def attachCallbacks():Unit = {
        modern.context.tileChanged = part => modern.mirror(part)
        modern.context.compilerEvents = key => tile.modernEventStream(key)
        modern.context.schematicChanged = () => { tile.editor.markSchematicChanged(); markEdited() }
    }
    def requestCompile():Unit = if canTriggerCompile then {
        modern = new ModernDesign(tile.editor)
        modern.rebuild()
        attachCallbacks()
        modern.context.getStateMachine.startCompile()
        modern.unsupported.foreach(modern.context.getStateMachine.getCompilerLog.addProblem)
        state = 2
        tile.editor.simNeedsRefresh = false
        changed()
    }
    def toggleAutomatic():Unit = {
        refreshAvailability()
        autoCompileEnabled = autoCompileAvailable && !autoCompileEnabled
        changed()
    }
    private def activate(part:ICTile):Unit = if part.isInstanceOf[GateICTile] &&
        Set(ICGateDefinition.Repeater.ordinal,ICGateDefinition.ToggleLatch.ordinal).contains(part.asInstanceOf[GateICTile].subID) then
        part.asInstanceOf[GateICTile].configure()
    else if modern != null && state == 3 then {
        val target=modern.context.getTileMap.getBaseTile(modern.coordinate(part.pos))
        if target.isPresent then target.get() match {
            case io:engine.gates.SingleBitIOGateTile => io.toggleWorldInput()
            case _ =>
        }
    }
    private def toggleBus(part:IOGateICTile,channel:Int):Unit = if modern != null && state == 3 && channel >= 0 && channel < 16 then {
        val target=modern.context.getTileMap.getBaseTile(modern.coordinate(part.pos))
        if target.isPresent && target.get().isInstanceOf[engine.gates.BundledBusIOGateTile] then {
            val bus=target.get().asInstanceOf[engine.gates.BundledBusIOGateTile]
            if bus.isInputIOMode then modern.context.getStateMachine.onInputRegistersChanged(bus.getIOSide,mask => (mask^(1<<channel)).toShort)
        }
    }
    def tick():Unit = {
        val old = (state,autoCompileEnabled,autoCompileAvailable)
        refreshAvailability()
        if !tile.hasBP || tile.editor.isEmpty then { state = 0; modern = null }
        else {
            if state == 0 && tile.editor.tileMapContainer.tiles.nonEmpty then markEdited()
            if tile.editor.simNeedsRefresh && state != 2 &&
                (state != 0 || tile.editor.tileMapContainer.tiles.nonEmpty) then markEdited()
            if state == 1 && autoCompileEnabled then requestCompile()
            if modern != null && (state == 2 || state == 3) then {
                modern.context.getStateMachine.tick(tile.getWorld.getTotalWorldTime)
                state = modern.context.getStateMachine.getState
                val log=modern.context.getStateMachine.getCompilerLog
                val steps=(log.getCompletedSteps,log.getTotalSteps)
                if steps != ((completedSteps,totalSteps)) then {
                    completedSteps=steps._1;totalSteps=steps._2;changed()
                }
                if state != 2 then {
                    if old._1 == 2 then modern.updateDiagnostics()
                    tile.editor.worldTimeOffset = tile.getWorld.getTotalWorldTime-simTime
                    val sim=modern.context.getStateMachine.getSimulation
                    var ioChanged=false
                    for r <- 0 until 4 do {
                        val value=(sim.getInput(r)&65535) | ((sim.getOutput(r)&65535)<<16)
                        if tile.editor.simEngineContainer.iostate(r) != value then {
                            tile.editor.simEngineContainer.iostate(r)=value;ioChanged=true
                        }
                    }
                    if old._1 == 2 then { tile.sendICDesc(); tile.sendModernLog(); tile.editor.sendWorldTimeOffset() }
                    else if ioChanged then tile.editor.sendIOUpdate()
                }
            }
        }
        if old != ((state,autoCompileEnabled,autoCompileAvailable)) then changed()
    }
    def save(tag:NBTTagCompound):Unit = {
        tag.setByte("compile_state",state.toByte)
        tag.setBoolean("auto_compile",autoCompileEnabled)
        if engineBackup != null then tag.setTag("modern_engine_backup",engineBackup.copy())
        if modern != null then {
            val engineTag=new NBTTagCompound
            modern.context.save(engineTag)
            tag.setTag("modern_engine",engineTag)
        }
    }
    def load(tag:NBTTagCompound):Unit = {
        autoCompileEnabled = !tag.hasKey("auto_compile") || tag.getBoolean("auto_compile")
        engineBackup=if tag.hasKey("modern_engine_backup") then tag.getCompoundTag("modern_engine_backup").copy() else null
        if tag.hasKey("modern_engine") then {
            try {
                val context=new EngineContext
                context.load(tag.getCompoundTag("modern_engine"))
                modern=new ModernDesign(tile.editor,context)
                attachCallbacks()
                state=context.getStateMachine.getState
                tile.editor.simNeedsRefresh=false
                modern.updateDiagnostics()
            } catch { case scala.util.control.NonFatal(error) =>
                engineBackup=tag.getCompoundTag("modern_engine").copy()
                modern=null;state=1;tile.editor.simNeedsRefresh=true
                engine.EngineContext.LOGGER.warn("Saved Workbench engine data was backed up; its drawing needs recompilation",error)
            }
        } else state = if tile.editor.tileMapContainer.tiles.nonEmpty then 1 else 0
        refreshAvailability()
    }
    def writeDesc(out:MCDataOutput):Unit = out.writeByte(state).writeBoolean(autoCompileEnabled).writeBoolean(autoCompileAvailable)
        .writeVarInt(completedSteps).writeVarInt(totalSteps)
    def readDesc(in:MCDataInput):Unit = {
        state = in.readUByte()
        autoCompileEnabled = in.readBoolean()
        autoCompileAvailable = in.readBoolean()
        completedSteps = in.readVarInt()
        totalSteps = in.readVarInt()
    }
}
