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
        def finish():Unit = {
            var ticks=0
            while tile.controller.state == 2 && ticks<10000 do { tile.controller.tick();ticks+=1 }
            assert(tile.controller.state != 2,"Workbench staged compile never finished")
        }
        try {
            ItemICBlueprint.loadTileMap(tile.editor.tileMapContainer,FabricationProductionChecks.blueprint())
            Configurator.autoCompileTileLimit = 2
            tile.controller.markEdited()
            tile.controller.tick()
            assert(tile.controller.state == 1 && !tile.controller.autoCompileAvailable && !tile.controller.autoCompileEnabled,
                "Oversized circuit bypassed the automatic compilation limit")
            val waitingChunk=world.getChunk(pos)
            waitingChunk.setModified(false)
            for _ <- 0 until 10 do tile.controller.tick()
            assert(!waitingChunk.needsSaving(false),"Awaiting manual compilation dirtied the chunk every tick")
            tile.controller.requestCompile()
            tile.controller.tick()
            finish()
            assert(tile.controller.state == 3 && tile.editor.canInteractSimulation,"Manual compilation did not start simulation")
            assert(!tile.controller.canTriggerCompile,"Unchanged simulated circuit offered recompilation")
            val tag = new NBTTagCompound
            tile.save(tag)
            val loaded = new TileICWorkbench
            loaded.setWorld(world); loaded.setPos(pos)
            loaded.load(tag)
            assert(loaded.controller.state == 3 && !loaded.controller.autoCompileEnabled,"Saved workflow was not restored")
            loaded.controller.tick()
            val incompatible=tag.copy()
            incompatible.getCompoundTag("modern_engine").getCompoundTag("tile_map").getTagList("tiles",10)
                .getCompoundTagAt(0).setByte("_id",127.toByte)
            val recovered=new TileICWorkbench
            recovered.setWorld(world);recovered.setPos(pos);recovered.load(incompatible)
            assert(recovered.editor.tileMapContainer.tiles.size==3 && recovered.controller.state==1,
                "Unrecognized engine data destroyed the legacy drawing")
            val recoverySave=new NBTTagCompound
            recovered.save(recoverySave)
            assert(recoverySave.getCompoundTag("modern_engine_backup")==incompatible.getCompoundTag("modern_engine"),
                "Unrecognized engine data was not retained for recovery")

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
            finish()
            assert(tile.controller.state == 4 && !tile.editor.canInteractSimulation,"Invalid circuit entered simulation")
            assert(tile.editor.simEngineContainer.logger.getErrors.size >= 2,"Missing I/O did not produce compile errors")
            assert(!tile.controller.canTriggerCompile,"Failed circuit offered recompilation without an edit")

            Configurator.autoCompileTileLimit = -1
            tile.editor.clear()
            ItemICBlueprint.loadTileMap(tile.editor.tileMapContainer,FabricationProductionChecks.blueprint())
            tile.controller.markEdited()
            tile.controller.toggleAutomatic()
            tile.controller.tick(); tile.controller.tick()
            finish()
            assert(tile.controller.state == 3 && tile.controller.autoCompileEnabled,"Automatic compilation did not recover after an edit")
            val description = new PacketCustom("PRVerification",1)
            tile.controller.writeDesc(description)
            val incoming = new PacketCustom(description.copy())
            val remote = new TileICWorkbench
            remote.controller.readDesc(incoming)
            assert(remote.controller.state == 3 && remote.controller.autoCompileEnabled,"Workflow description lost client state")
            description.release(); incoming.release()

            tile.editor.clear()
            tile.editor.tileMapContainer.size=Size(16,16)
            def bus(y:Int,rotation:Int,shape:Int):IOGateICTile = {
                val part=new IOGateICTile
                part.preparePlacement(rotation,ICGateDefinition.IOBundledBus.ordinal)
                part.setShape(shape)
                tile.editor.setTile(Point(8,y),part)
                part
            }
            val busInput=bus(0,0,0)
            bus(15,2,1)
            for y <- 1 to 14 do {
                val wire=new BundledCableICTile
                wire.colour=(-1).toByte
                tile.editor.setTile(Point(8,y),wire)
            }
            tile.controller.markEdited();tile.controller.tick();finish()
            assert(tile.controller.state==3,"Workbench bundled bus failed to compile")
            val fullDescription=new PacketCustom("PRVerification",1)
            tile.editor.writeDesc(fullDescription)
            val clientNetwork=new IICTileEditorNetwork {
                val getIC=new ICTileMapEditor(this)
                def getEditorWorld=world
                def isRemote=true
                def markSave():Unit=()
                private val sink=new codechicken.lib.data.MCDataOutputWrapper(java.io.OutputStream.nullOutputStream())
                def getICStreamOf(key:Int):codechicken.lib.data.MCDataOutput=sink
                def getTileStream(point:Point):codechicken.lib.data.MCDataOutput=sink
            }
            val clientInput=new PacketCustom(fullDescription.copy())
            clientNetwork.getIC.readDesc(clientInput)
            assert(clientNetwork.getIC.tileMapContainer.tiles.size==16 && clientInput.readableBytes()==0,
                "Bundled-bus editor description was not framed correctly")
            fullDescription.release();clientInput.release()
            for channel <- 0 until 16 do {
                val packet=new PacketCustom("PRVerification",1)
                packet.writeByte(7).writeByte(channel)
                val input=new PacketCustom(packet.copy())
                busInput.readClientPacket(input)
                packet.release();input.release()
            }
            assert((tile.controller.modern.context.getStateMachine.getSimulation.getOutput(2)&65535)==65535,
                "Workbench bus channel controls lost part of the signal")
            val output=tile.editor.getTile(Point(8,15)).asInstanceOf[IOGateICTile]
            assert(output.getLogic[BundledBusIOGateTileLogic].busMask==65535,"Workbench bus display was stale")
            val gateStack=mrtjp.projectred.integration.GateDefinition.ICGate.makeStack
            ItemICBlueprint.saveICToGate(tile.editor.tileMapContainer,gateStack)
            ModernBlueprint.attach(gateStack,tile.controller.modern)
            assert(ICData.canFabricate(gateStack),"Modern compiled target was rejected")
            val part=new ICGatePart
            part.assertLogic()
            val logic=part.getLogicIC
            ICGateLogic.constructICLogic(logic,gateStack)
            assert(logic.modernSim!=null && logic.sim.simEngine==null,"Fabricated gate used the old simulator")
            logic.modernSim.setInput(0,0xA55A.toShort)
            logic.modernSim.pushInputs(1);logic.modernSim.simulate();logic.publishModernOutputs(false)
            assert((logic.modernSim.getOutput(2)&65535)==0xA55A)
            val runtime=new NBTTagCompound
            logic.save(runtime)
            val restoredPart=new ICGatePart
            restoredPart.assertLogic()
            restoredPart.getLogicIC.load(runtime)
            assert((restoredPart.getLogicIC.modernSim.getOutput(2)&65535)==0xA55A,"Placed IC runtime did not resume")
            val future=runtime.copy()
            future.setInteger("engine_format",999)
            val unavailable=new ICGatePart
            unavailable.assertLogic();unavailable.getLogicIC.load(future)
            assert(unavailable.getLogicIC.modernDisabled && unavailable.getLogicIC.tmap.tiles.size==16,
                "Unknown engine format lost its drawing or silently ran an old simulator")
            assert(ModernBlueprint.isBuilt(gateStack))
            gateStack.getTagCompound.getCompoundTag("tilemap").setString("name","changed after compilation")
            assert(!ICData.canFabricate(gateStack),"Changed source reused a compiled target")
        } finally {
            Configurator.autoCompileTileLimit = oldLimit
            world.setBlockToAir(pos)
        }
    }
}
