package mrtjp.projectred.verification

import mrtjp.fengine.TileCoord
import mrtjp.projectred.fabrication.engine.*
import mrtjp.projectred.fabrication.engine.gates.*
import net.minecraft.nbt.NBTTagCompound
import codechicken.lib.packet.PacketCustom

object ModernEngineChecks {
    def design(input:ICTileType=ICTileType.REDSTONE_IO_GATE,output:ICTileType=ICTileType.REDSTONE_IO_GATE):EngineContext = {
        val context = new EngineContext
        def add(kind:ICTileType,z:Int,rotation:Int,shape:Int):Unit = {
            val tile = kind.create()
            tile match {
                case gate:GateTile => gate.preparePlacement(rotation); gate.setShape(shape)
                case _ =>
            }
            context.getTileMap.addTile(new TileCoord(0,0,z),tile)
        }
        add(input,-8,0,0)
        for z <- -7 to 6 do add(ICTileType.RED_ALLOY_WIRE,z,0,0)
        add(output,7,2,1)
        for entry <- context.getTileMap.getBaseTileEntries.toArray do
            entry.asInstanceOf[java.util.Map.Entry[TileCoord,BaseTile]].getValue.onAdded()
        context.flushNeighborChanges()
        context.getStateMachine.markEdited()
        context
    }
    def finish(context:EngineContext):Unit = {
        val compiler = context.getStateMachine
        compiler.startCompile()
        var ticks=0
        while compiler.getState == 2 && ticks<10000 do { compiler.tick(ticks); ticks+=1 }
        assert(compiler.getState != 2,"Staged IC compilation never finished")
    }
    def run():Unit = {
        val context=design()
        finish(context)
        val compiler=context.getStateMachine
        assert(compiler.isSimulating,s"Modern I/O circuit failed: ${compiler.getCompilerLog.getProblems}")
        assert(compiler.getCompilerLog.getCompletedSteps > 10,"Compiler did not execute the new assembly passes")
        compiler.onInputRegistersChanged(0,_ => 1.toShort)
        assert(compiler.getSimulation.getOutput(2) == 1,"New engine did not route a redstone signal")
        compiler.onInputRegistersChanged(0,_ => 0.toShort)
        assert(compiler.getSimulation.getOutput(2) == 0,"New engine left a stale output")
        val branch=ICTileType.REDSTONE_IO_GATE.create().asInstanceOf[GateTile]
        branch.preparePlacement(1);branch.setShape(1)
        context.getTileMap.addTile(new TileCoord(7,0,0),branch)
        for x <- 1 to 6 do context.getTileMap.addTile(new TileCoord(x,0,0),ICTileType.RED_ALLOY_WIRE.create())
        import scala.jdk.CollectionConverters.*
        context.getTileMap.getBaseTileEntries.asScala.foreach(_.getValue.onAdded())
        context.flushNeighborChanges();compiler.markEdited();finish(context)
        compiler.onInputRegistersChanged(0,_ => 1.toShort)
        assert(compiler.getSimulation.getOutput(1)==1 && compiler.getSimulation.getOutput(2)==1,
            "An output register alias swallowed one branch of I/O fan-out")
        compiler.onInputRegistersChanged(0,_ => 0.toShort)
        val saved=new NBTTagCompound
        context.save(saved)
        val copy=new EngineContext
        copy.load(saved)
        copy.getStateMachine.onInputRegistersChanged(0,_ => 1.toShort)
        assert(copy.getStateMachine.getSimulation.getOutput(2) == 1,"Serialized simulation did not resume")
        val count=copy.getStateMachine.getCompilerLog.getTotalSteps
        copy.load(saved)
        assert(copy.getStateMachine.getCompilerLog.getTotalSteps == count,"Repeated engine load duplicated the compile tree")
        for colour <- 0 until 16 do {
            val coloured=design()
            for z <- -7 to 6 do {
                val p=new TileCoord(0,0,z)
                coloured.getTileMap.removeTile(p)
                coloured.getTileMap.addTile(p,ICTileType.createFromId(ICTileType.INSULATED_WHITE_WIRE.getID+colour))
            }
            import scala.jdk.CollectionConverters.*
            coloured.getTileMap.getBaseTileEntries.asScala.foreach(_.getValue.onAdded())
            coloured.flushNeighborChanges();coloured.getStateMachine.markEdited();finish(coloured)
            coloured.getStateMachine.onInputRegistersChanged(0,_ => 1.toShort)
            assert(coloured.getStateMachine.getSimulation.getOutput(2)==1,s"Redstone traversal lost insulated colour $colour")
        }
        val buses=design(ICTileType.BUNDLED_BUS_IO_GATE,ICTileType.BUNDLED_BUS_IO_GATE)
        // Bundled buses connect through bundled cable, including all sixteen ports.
        for z <- -7 to 6 do {
            val p=new TileCoord(0,0,z)
            buses.getTileMap.removeTile(p)
            buses.getTileMap.addTile(p,ICTileType.BUNDLED_NEUTRAL_WIRE.create())
        }
        buses.getTileMap.getBaseTileEntries.asScala.foreach(_.getValue.onAdded())
        buses.flushNeighborChanges()
        finish(buses)
        assert(buses.getStateMachine.isSimulating)
        buses.getStateMachine.onInputRegistersChanged(0,_ => 0xA55A.toShort)
        assert((buses.getStateMachine.getSimulation.getOutput(2)&0xFFFF) == 0xA55A,"Bundled bus lost channels")
        val invalid=new EngineContext
        invalid.getTileMap.addTile(new TileCoord(0,0,0),ICTileType.OR_GATE.create())
        invalid.getStateMachine.markEdited(); finish(invalid)
        assert(invalid.getStateMachine.getState == 4 && invalid.getStateMachine.getCompilerLog.getErrorCount == 2)
        for gateType <- ICGateTileType.values().drop(4) do {
            val circuit=design()
            val tile=gateType.tileType.create()
            circuit.getTileMap.addTile(new TileCoord(3,0,0),tile)
            tile.onAdded();circuit.getStateMachine.markEdited()
            finish(circuit)
            assert(circuit.getStateMachine.isSimulating,s"Gate failed to compile: $gateType")
            val state=new NBTTagCompound
            circuit.save(state)
            val restored=new EngineContext
            restored.load(state)
            assert(restored.getStateMachine.isSimulating,s"Gate simulation failed to reload: $gateType")
        }
        val packet=new PacketCustom("PRVerification",1)
        context.getStateMachine.getCompilerLog.writeDesc(packet)
        val incoming=new PacketCustom(packet.copy())
        val remote=new EngineContext
        remote.getStateMachine.getCompilerLog.readDesc(incoming)
        assert(remote.getStateMachine.getCompilerLog.getTotalSteps == compiler.getCompilerLog.getTotalSteps,
            "Compile tree did not survive its network codec")
        packet.release();incoming.release()
        val text="[registers]"*10000
        val tag=new NBTTagCompound
        EngineData.putJson(tag,"flat_map",text)
        val bytes=new java.io.ByteArrayOutputStream
        net.minecraft.nbt.CompressedStreamTools.write(tag,new java.io.DataOutputStream(bytes))
        val decoded=net.minecraft.nbt.CompressedStreamTools.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray)))
        assert(EngineData.getJson(decoded,"flat_map") == text,"Large IC JSON exceeded the NBT UTF limit")
        println("PROJECTRED_MODERN_ENGINE_PASS: staged compiler, redstone, 16-bit bus, diagnostics and simulation reload")
    }
}
