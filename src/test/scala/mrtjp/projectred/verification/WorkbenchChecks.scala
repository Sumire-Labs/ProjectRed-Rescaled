package mrtjp.projectred.verification

import codechicken.lib.packet.PacketCustom
import mrtjp.core.vec.{Point,Size}
import mrtjp.projectred.ProjectRedFabrication
import mrtjp.projectred.core.Configurator
import mrtjp.projectred.fabrication.*
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldServer

object WorkbenchChecks {
    def run(world:WorldServer):Unit = {
        val pos = new BlockPos(38,80,24)
        val oldLimit = Configurator.autoCompileTileLimit
        world.setBlockState(pos,ProjectRedFabrication.icBlock.getStateFromMeta(0),3)
        val tile = world.getTileEntity(pos).asInstanceOf[TileICWorkbench]
        tile.hasBP = true
        try {
            ItemICBlueprint.loadTileMap(tile.editor.tileMapContainer,FabricationProductionChecks.blueprint())
            Configurator.autoCompileTileLimit = 2
            tile.controller.markEdited()
            tile.controller.tick()
            assert(tile.controller.state == 1 && !tile.controller.autoCompileAvailable && !tile.controller.autoCompileEnabled,
                "Oversized circuit bypassed the automatic compilation limit")
            tile.controller.requestCompile()
            tile.controller.tick()
            assert(tile.controller.state == 3 && tile.editor.canInteractSimulation,"Manual compilation did not start simulation")
            assert(!tile.controller.canTriggerCompile,"Unchanged simulated circuit offered recompilation")
            val tag = new NBTTagCompound
            tile.save(tag)
            val loaded = new TileICWorkbench
            loaded.setWorld(world); loaded.setPos(pos)
            loaded.load(tag)
            assert(loaded.controller.state == 3 && !loaded.controller.autoCompileEnabled,"Saved workflow was not restored")
            loaded.controller.tick()

            tile.editor.clear()
            tile.editor.tileMapContainer.size = Size(16,16)
            val lever = new LeverICTile
            tile.editor.setTile(Point(8,8),lever)
            tile.controller.markEdited()
            val packet = new PacketCustom("PRVerification",1)
            try lever.readClientPacket(packet) finally packet.release()
            assert(!lever.on,"An uncompiled circuit accepted simulation input")
            tile.controller.requestCompile()
            tile.controller.tick()
            assert(tile.controller.state == 4 && !tile.editor.canInteractSimulation,"Invalid circuit entered simulation")
            assert(tile.editor.simEngineContainer.logger.getErrors.size >= 2,"Missing I/O did not produce compile errors")
            assert(!tile.controller.canTriggerCompile,"Failed circuit offered recompilation without an edit")

            Configurator.autoCompileTileLimit = -1
            tile.editor.clear()
            ItemICBlueprint.loadTileMap(tile.editor.tileMapContainer,FabricationProductionChecks.blueprint())
            tile.controller.markEdited()
            tile.controller.toggleAutomatic()
            tile.controller.tick(); tile.controller.tick()
            assert(tile.controller.state == 3 && tile.controller.autoCompileEnabled,"Automatic compilation did not recover after an edit")
            val description = new PacketCustom("PRVerification",1)
            tile.controller.writeDesc(description)
            val incoming = new PacketCustom(description.copy())
            val remote = new TileICWorkbench
            remote.controller.readDesc(incoming)
            assert(remote.controller.state == 3 && remote.controller.autoCompileEnabled,"Workflow description lost client state")
            description.release(); incoming.release()
        } finally {
            Configurator.autoCompileTileLimit = oldLimit
            world.setBlockToAir(pos)
        }
    }
}
