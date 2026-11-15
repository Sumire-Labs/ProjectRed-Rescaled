package mrtjp.projectred.fabrication

import codechicken.lib.data.{MCDataInput,MCDataOutput}
import mrtjp.projectred.core.Configurator
import net.minecraft.nbt.NBTTagCompound

/** Workbench workflow around the current compiler; the newer staged backend is tracked separately. */
class WorkbenchController(tile:TileICWorkbench) {
    var state = 0 // initial, awaiting compile, compiling, simulating, failed
    var autoCompileEnabled = true
    var autoCompileAvailable = true
    def canTriggerCompile:Boolean = state == 1
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
        if tile.editor.tileMapContainer.tiles.nonEmpty || state != 0 then state = 1
        changed()
    }
    def requestCompile():Unit = if canTriggerCompile then { state = 2; changed() }
    def toggleAutomatic():Unit = {
        refreshAvailability()
        autoCompileEnabled = autoCompileAvailable && !autoCompileEnabled
        changed()
    }
    def tick():Unit = {
        val old = (state,autoCompileEnabled,autoCompileAvailable)
        refreshAvailability()
        if !tile.hasBP || tile.editor.isEmpty then state = 0
        else {
            if tile.editor.simNeedsRefresh && state != 2 &&
                (state != 0 || tile.editor.tileMapContainer.tiles.nonEmpty) then state = 1
            if state == 0 && !tile.editor.simNeedsRefresh && tile.editor.simEngineContainer.simEngine != null then
                state = if tile.editor.simEngineContainer.logger.getErrors.nonEmpty then 4 else 3
            state match {
                case 1 if autoCompileEnabled => state = 2
                case 2 =>
                    tile.editor.recompileSchematic()
                    val time = tile.getWorld.getTotalWorldTime
                    tile.editor.lastWorldTime = time-1
                    tile.editor.worldTimeOffset = time
                    tile.editor.sendWorldTimeOffset()
                    state = if tile.editor.simEngineContainer.logger.getErrors.nonEmpty then 4 else 3
                case 3 => tile.editor.tick(autoCompile=false)
                case _ =>
            }
        }
        if old != ((state,autoCompileEnabled,autoCompileAvailable)) then changed()
    }
    def save(tag:NBTTagCompound):Unit = {
        tag.setByte("compile_state",state.toByte)
        tag.setBoolean("auto_compile",autoCompileEnabled)
    }
    def load(tag:NBTTagCompound):Unit = {
        autoCompileEnabled = !tag.hasKey("auto_compile") || tag.getBoolean("auto_compile")
        state = if tag.hasKey("compile_state") && tag.getByte("compile_state") == 3 &&
            tile.editor.simEngineContainer.logger.getErrors.isEmpty then 3
            else if tile.editor.tileMapContainer.tiles.nonEmpty then 1 else 0
        refreshAvailability()
    }
    def writeDesc(out:MCDataOutput):Unit = out.writeByte(state).writeBoolean(autoCompileEnabled).writeBoolean(autoCompileAvailable)
    def readDesc(in:MCDataInput):Unit = {
        state = in.readUByte()
        autoCompileEnabled = in.readBoolean()
        autoCompileAvailable = in.readBoolean()
    }
}
