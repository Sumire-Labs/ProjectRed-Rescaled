package mrtjp.projectred.verification

import mrtjp.core.vec.{Point, Size}
import mrtjp.projectred.fabrication.*

object ICWireNetChecks {
    private def put(map:ICTileMapContainer, wire:WireICTile, point:Point):Unit = {
        wire.bindTileMap(map)
        wire.bindPos(point)
        wire.connMap = 15.toByte
        map.tiles += ((point.x,point.y) -> wire)
    }
    def run():Unit = {
        // The two insulated branches share bundled cable but carry distinct channels.
        val branched = new ICTileMapContainer
        branched.size = Size(16,16)
        val white = new InsulatedWireICTile
        white.colour = 0
        val black = new InsulatedWireICTile
        black.colour = 15
        put(branched, white, Point(3,4))
        put(branched, new BundledCableICTile, Point(4,4))
        put(branched, black, Point(5,4))
        val branchedNet = new WireNet(branched, Point(3,4), 15)
        val whitePorts = branchedNet.mapChannelForPoint(Point(3,4), 0)
        val blackPorts = branchedNet.mapChannelForPoint(Point(5,4), 0)
        assert((0 until 4).forall(r => whitePorts.contains((Point(3,4),r))))
        assert((0 until 4).forall(r => blackPorts.contains((Point(5,4),r))))
        assert(whitePorts.exists(_._1 == Point(4,4)) && blackPorts.exists(_._1 == Point(4,4)))
        assert(!whitePorts.exists(_._1 == Point(5,4)), "Bundled cable merged white into black")
        assert(!blackPorts.exists(_._1 == Point(3,4)), "Bundled cable merged black into white")

        val crossing = new ICTileMapContainer
        crossing.size = Size(16,16)
        val crossed = new AlloyWireICTile {
            override def getPropMask(r:Int):Int = if r%2 == 0 then 5 else 10
        }
        put(crossing, crossed, Point(4,4))
        Seq(Point(3,4),Point(5,4),Point(4,3),Point(4,5)).foreach(p => put(crossing,new AlloyWireICTile,p))
        val crossedNet = new WireNet(crossing,Point(3,4),15)
        val horizontal = crossedNet.mapChannelForPoint(Point(3,4),0)
        val vertical = crossedNet.mapChannelForPoint(Point(4,3),0)
        assert(horizontal.contains((Point(4,4),1)) && horizontal.contains((Point(4,4),3)))
        assert(!horizontal.exists(p => p._1 == Point(4,3) || p._1 == Point(4,5)), "Crossed propagation groups merged")
        assert(vertical.contains((Point(4,4),0)) && vertical.contains((Point(4,4),2)))
        assert(!vertical.exists(p => p._1 == Point(3,4) || p._1 == Point(5,4)), "Crossed propagation groups merged")
        crossedNet.calculateNetwork()
        assert(crossedNet.points.toSet == horizontal, "Wire topology and channel search disagree")

        // Match the dense 32x32 blueprint shape reported in upstream issue #1747.
        val dense = new ICTileMapContainer
        dense.size = Size(32,32)
        for x <- 1 until 31; y <- 1 until 31 do put(dense, new AlloyWireICTile, Point(x,y))
        val denseNet = new WireNet(dense, Point(1,1), 15)
        val started = System.nanoTime()
        val ports = denseNet.mapChannelForPoint(Point(1,1), 0)
        val elapsedMs = (System.nanoTime()-started)/1000000L
        println(s"IC_WIRENET_BENCH: 900 wire tiles, ${ports.size} ports, $elapsedMs ms")
        assert(ports.size == 900*4, "Dense wire search omitted connected ports")
        assert(elapsedMs < 10000, "Dense IC channel search stalled the server for ten seconds")

        def io(point:Point,rotation:Int,shape:Int):Unit = {
            val gate = new IOGateICTile
            gate.preparePlacement(rotation, ICGateDefinition.IOSimple.ordinal)
            gate.setShape(shape)
            gate.connMap = (1<<((rotation+2)%4)).toByte
            gate.bindTileMap(dense)
            gate.bindPos(point)
            dense.tiles += ((point.x,point.y) -> gate)
        }
        io(Point(1,0),0,0)
        io(Point(1,31),2,1)
        def assertTransfer(map:ICTileMapContainer):Unit = {
            val simulation = new ICSimEngineContainer
            simulation.recompileSimulation(map)
            simulation.setInput(0,1)
            simulation.onInputChanged(1)
            simulation.repropagate()
            assert((simulation.iostate(2) >>> 16) == 1, "Dense blueprint failed to propagate IO")
            simulation.setInput(0,0)
            simulation.onInputChanged(1)
            simulation.repropagate()
            assert((simulation.iostate(2) >>> 16) == 0, "Dense blueprint retained stale IO")
        }
        assertTransfer(dense)
        val tag = new net.minecraft.nbt.NBTTagCompound
        dense.saveTiles(tag)
        val restored = new ICTileMapContainer
        restored.loadTiles(tag)
        assertTransfer(restored)
    }
}
