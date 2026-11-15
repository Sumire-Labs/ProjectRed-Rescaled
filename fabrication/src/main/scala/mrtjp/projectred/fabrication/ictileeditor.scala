package mrtjp.projectred.fabrication

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.packet.PacketCustom
import mrtjp.core.vec.{Point, Size}
import mrtjp.projectred.ProjectRedCore.log
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{NBTTagCompound, NBTTagList}
import net.minecraft.world.World

import scala.collection.mutable
import scala.collection.mutable.Map as MMap

trait IICTileEditorNetwork
{
    def getIC:ICTileMapEditor
    def getEditorWorld:World

    def getICStreamOf(key:Int):MCDataOutput
    def getTileStream(pos:Point):MCDataOutput
    def getTileStream(pos:Point,layer:Int):MCDataOutput = getTileStream(pos)

    def isRemote:Boolean
    def isSimulationRunning:Boolean = true
    def markSave(): Unit
}

trait TICTileEditorNetwork extends IICTileEditorNetwork
{
    private var editorStream:PacketCustom = null
    private var tileStream:PacketCustom = null

    def createTileStream():PacketCustom
    def sendTileStream(out:PacketCustom): Unit
    override def getTileStream(pos:Point):MCDataOutput =
        getTileStream(pos,getIC.currentLayer)

    override def getTileStream(pos:Point,layer:Int):MCDataOutput =
    {
        if tileStream == null then tileStream = createTileStream()

        val tile = getIC.withLayer(layer){getIC.getTile(pos)}
        if layer!=0 then tileStream.writeByte(254).writeByte(layer)
        tileStream.writeByte(tile.id)
        tileStream.writeByte(pos.x).writeByte(pos.y)

        tileStream
    }
    def flushTileStream(): Unit =
    {
        if tileStream != null then {
            tileStream.writeByte(255)//terminator
            sendTileStream(tileStream.compress())
            tileStream = null
        }
    }
    def readTileStream(in:MCDataInput): Unit =
    {
        try {
            var id = in.readUByte()
            while id != 255 do {
                val layer=if id==254 then {val y=in.readByte().toInt;id=in.readUByte();y} else 0
                val p = Point(in.readUByte(), in.readUByte())
                var tile = getIC.withLayer(layer){getIC.getTile(p)}
                if tile == null || tile.id != id then {
                    log.error("client tile stream couldnt find tile "+p)
                    tile = ICTile.createTile(id)
                }
                getIC.withLayer(layer){tile.read(in)}
                id = in.readUByte()
            }
        }
        catch {
            case ex:IndexOutOfBoundsException =>
                log.error("tile stream failed to be read.")
                ex.printStackTrace()
        }
    }

    def createEditorStream():PacketCustom
    def sendEditorStream(out:PacketCustom): Unit

    override def getICStreamOf(key:Int):MCDataOutput =
    {
        if editorStream == null then editorStream = createEditorStream()
        editorStream.writeByte(key)
        editorStream
    }
    def flushICStream(): Unit =
    {
        if editorStream != null then {
            editorStream.writeByte(255) //terminator
            sendEditorStream(editorStream.compress())
            editorStream = null
        }
    }
    def readICStream(in:MCDataInput): Unit =
    {
        try {
            var id = in.readUByte()
            while id != 255 do {
                getIC.read(in, id)
                id = in.readUByte()
            }
        } catch {
            case ex:IndexOutOfBoundsException =>
                log.error("Tile Map stream failed to be read")
        }
    }

    /** Client edits contain operations or tile controls, never server simulation snapshots. */
    def readClientICStream(in:MCDataInput):Unit = {
        var id = in.readUByte()
        while id != 255 do {
            if id == 3 || id == 10 then {
                val layer=if id==10 then in.readByte().toInt else 0
                if !getIC.validLayer(layer) then return
                val operation = in.readUByte()
                if !TileEditorOpDefs.values.isDefinedAt(operation) then return
                if getIC.externalSimulationActive!=null && Set(TileEditorOpDefs.Lever.getID,TileEditorOpDefs.Button.getID,TileEditorOpDefs.DecRandomizerGate.getID,
                    TileEditorOpDefs.NullCellGate.getID,TileEditorOpDefs.InvertCellGate.getID,TileEditorOpDefs.BufferCellGate.getID).contains(operation.toInt) then return
                getIC.withLayer(layer){TileEditorOp.getOperation(operation).readOp(getIC, in)}
            } else if id == 4 || id == 11 then {
                val layer=if id==11 then in.readByte().toInt else 0
                if !getIC.validLayer(layer) then return
                val point = Point(in.readUByte(), in.readUByte())
                if point.x >= getIC.size.width || point.y >= getIC.size.height then return
                val tile=getIC.withLayer(layer){getIC.getTile(point)}
                tile match {
                    case t:TClientNetICTile => getIC.withLayer(layer){t.readClientPacket(in)}
                    case _ => return
                }
            } else return
            id = in.readUByte()
        }
    }
}

class ICTileMapContainer extends ISETileMap
{
    var layerIndex=0
    var minLayer=0
    var maxLayer=0
    val extraLayers=mutable.Map.empty[Int,ICTileMapContainer]
    def layerMap(layer:Int):ICTileMapContainer = if layer==0 then this else {
        require(layer>=minLayer && layer<=maxLayer,"Layer is outside the drawing")
        extraLayers.getOrElseUpdate(layer,{
            val map=new ICTileMapContainer;map.layerIndex=layer;map.size=size;map.name=name;map
        })
    }
    def allLayers:Seq[(Int,ICTileMapContainer)] = (Seq(0->this)++extraLayers.toSeq).sortBy(_._1)
    def allTiles:Seq[ICTile] = allLayers.flatMap(_._2.tiles.values)
    def tileCount:Int=tiles.size+extraLayers.valuesIterator.map(_.tiles.size).sum
    override val tiles: mutable.Map[(Int, Int), ICTile] = mutable.Map[(Int, Int), ICTile]()

    var tilesLoadedDelegate = {() => ()}

    var name = "untitled"

    var size = Size.zeroSize

    def isEmpty = size == Size.zeroSize

    def nonEmpty = !isEmpty

    def assertCoords(x:Int, y:Int): Unit =
    {
        if !(0 until size.width contains x) || !(0 until size.height contains y) then
            throw new IndexOutOfBoundsException("Tile Map does not contain "+Point(x, y))
    }

    def saveTiles(tag:NBTTagCompound): Unit =
    {
        tag.setString("name", name)
        tag.setByte("sw", size.width.toByte)
        tag.setByte("sh", size.height.toByte)

        val tagList = new NBTTagList
        for tile <- tiles.values do {
            val tileTag = new NBTTagCompound
            tileTag.setByte("id", tile.id.toByte)
            tileTag.setByte("xpos", tile.pos.x.toByte)
            tileTag.setByte("ypos", tile.pos.y.toByte)
            tile.save(tileTag)
            tagList.appendTag(tileTag)
        }
        tag.setTag("tiles", tagList)
        if layerIndex==0 then {
            tag.setByte("min_layer",minLayer.toByte);tag.setByte("max_layer",maxLayer.toByte)
            val layers=new NBTTagList
            for (index,map) <- extraLayers.toSeq.sortBy(_._1) do {
                val layerTag=new NBTTagCompound
                map.saveTiles(layerTag);layerTag.setByte("layer",index.toByte);layers.appendTag(layerTag)
            }
            tag.setTag("layers",layers)
        }
    }

    def loadTiles(tag:NBTTagCompound): Unit =
    {
        tiles.clear();extraLayers.clear()
        name = tag.getString("name")
        size = Size(tag.getByte("sw")&0xFF, tag.getByte("sh")&0xFF)

        val tileList = tag.getTagList("tiles", 10)
        for i <- 0 until tileList.tagCount do {
            val tileTag = tileList.getCompoundTagAt(i)
            val tile = ICTile.createTile(tileTag.getByte("id")&0xFF)
            val x = tileTag.getByte("xpos")&0xFF
            val y = tileTag.getByte("ypos")&0xFF
            tile.bindTileMap(this)
            tile.bindPos(Point(x, y))
            tiles += (x, y) -> tile
            tile.load(tileTag)
        }

        if layerIndex==0 then {
            minLayer=tag.getByte("min_layer");maxLayer=tag.getByte("max_layer")
            require(minLayer<=maxLayer,"Invalid drawing layer bounds")
            val layers=tag.getTagList("layers",10)
            for i <- 0 until layers.tagCount do {
                val saved=layers.getCompoundTagAt(i)
                val index=saved.getByte("layer").toInt
                require(index!=0 && index>=minLayer && index<=maxLayer && !extraLayers.contains(index),"Invalid drawing layer")
                val map=new ICTileMapContainer;map.layerIndex=index;map.loadTiles(saved)
                require(map.size==size,"Layer dimensions differ")
                extraLayers(index)=map
            }
        }
        tilesLoadedDelegate()
    }

    def getTile(x:Int, y:Int):ICTile = tiles.getOrElse((x, y), null)

    def getTile(p:Point):ICTile = getTile(p.x, p.y)
}

class ICTileMapEditor(val network:IICTileEditorNetwork) extends IICSimEngineContainerDelegate
{
    val rootTileMapContainer = new ICTileMapContainer
    private var operationLayer=0
    def currentLayer:Int=operationLayer
    def validLayer(layer:Int):Boolean=layer>=rootTileMapContainer.minLayer && layer<=rootTileMapContainer.maxLayer
    var revision=0L
    def tileMapContainer:ICTileMapContainer=rootTileMapContainer.layerMap(operationLayer)
    def withLayer[T](layer:Int)(body: => T):T = {
        val before=operationLayer
        require(layer>=rootTileMapContainer.minLayer && layer<=rootTileMapContainer.maxLayer,"Layer is outside the drawing")
        operationLayer=layer
        try body finally operationLayer=before
    }

    var simEngineContainer = new ICSimEngineContainer
    var simNeedsRefresh = true
    var externalSimulationActive:(() => Boolean) = null
    var externalActivation:(ICTile => Unit) = null
    var externalBusActivation:((IOGateICTile,Int) => Unit) = null

    var worldTimeOffset = -1L //number of ticks that the simulation is behind the total world time
    var lastWorldTime = -1L

    private var scheduledTicks = MMap[Point, Long]()

    tileMapContainer.tilesLoadedDelegate = {() =>
        simNeedsRefresh = true
        for tile <- rootTileMapContainer.allTiles do
            tile.bindEditor(this)
    }

    def size = tileMapContainer.size
    def name = tileMapContainer.name

    def save(tag:NBTTagCompound): Unit =
    {
        tileMapContainer.saveTiles(tag)
        simEngineContainer.saveSimState(tag)
    }

    def load(tag:NBTTagCompound):Unit = load(tag,compileLegacy=true)

    def load(tag:NBTTagCompound,compileLegacy:Boolean): Unit =
    {
        clear()
        tileMapContainer.loadTiles(tag)

        if compileLegacy then {
            recompileSchematic()
            simEngineContainer.loadSimState(tag)
        }
    }

    def writeDesc(out:MCDataOutput): Unit =
    {
        out.writeString(tileMapContainer.name)
        out.writeByte(tileMapContainer.size.width).writeByte(tileMapContainer.size.height)
        for i <- 0 until 4 do out.writeInt(simEngineContainer.iostate(i))
        simEngineContainer.logger.writeLog(out)

        for ((x, y), tile) <- tileMapContainer.tiles do {
            out.writeByte(tile.id)
            out.writeByte(x).writeByte(y)
            tile.writeDesc(out)
        }
        out.writeByte(255)
        val root=rootTileMapContainer
        out.writeByte(root.minLayer).writeByte(root.maxLayer).writeVarInt(root.extraLayers.size)
        for (layer,map) <- root.extraLayers.toSeq.sortBy(_._1) do {
            out.writeByte(layer)
            for ((x,y),tile) <- map.tiles do {
                out.writeByte(tile.id).writeByte(x).writeByte(y);tile.writeDesc(out)
            }
            out.writeByte(255)
        }
    }

    def readDesc(in:MCDataInput): Unit =
    {
        clear()
        tileMapContainer.name = in.readString()
        tileMapContainer.size = Size(in.readUByte(), in.readUByte())
        for i <- 0 until 4 do simEngineContainer.iostate(i) = in.readInt()
        simEngineContainer.logger.readLog(in)

        var id = in.readUByte()
        while id != 255 do {
            val tile = ICTile.createTile(id)
            setTile_do(Point(in.readUByte(), in.readUByte()), tile)
            tile.readDesc(in)
            id = in.readUByte()
        }
        rootTileMapContainer.minLayer=in.readByte();rootTileMapContainer.maxLayer=in.readByte()
        val count=in.readVarInt()
        require(count>=0 && count<=255,"Invalid layer count")
        for _ <- 0 until count do {
            val layer=in.readByte().toInt
            require(layer!=0 && validLayer(layer),"Invalid layer descriptor")
            withLayer(layer){
                var kind=in.readUByte()
                while kind!=255 do {
                    val tile=ICTile.createTile(kind)
                    setTile_do(Point(in.readUByte(),in.readUByte()),tile);tile.readDesc(in)
                    kind=in.readUByte()
                }
            }
        }
        revision+=1
    }

    def read(in:MCDataInput, key:Int) = key match
    {
        case 0 => readDesc(in)
        case 1 =>
            val tile = ICTile.createTile(in.readUByte())
            setTile_do(Point(in.readUByte(), in.readUByte()), tile)
            tile.readDesc(in)
        case 2 => removeTile_do(Point(in.readUByte(), in.readUByte()))
        case 3 => TileEditorOp.getOperation(in.readUByte()).readOp(this, in)
        case 4 => getTile(Point(in.readUByte(), in.readUByte())) match {
            case g:TClientNetICTile => g.readClientPacket(in)
            case _ => log.error("Server IC stream received invalid client packet")
        }
        case 5 =>
            for r <- 0 until 4 do
                simEngineContainer.iostate(r) = in.readInt()
        case 6 => simEngineContainer.setInput(in.readUByte(), in.readShort())//TODO remove? not used...
        case 7 => simEngineContainer.setOutput(in.readUByte(), in.readShort()) //TODO remove? not used...
        case 8 => simEngineContainer.logger.readLog(in)
        case 9 => worldTimeOffset = in.readLong()
        case 12 =>
            val layer=in.readByte().toInt
            withLayer(layer){
                val tile=ICTile.createTile(in.readUByte())
                setTile_do(Point(in.readUByte(),in.readUByte()),tile);tile.readDesc(in)
            }
            revision+=1
        case 13 =>
            val layer=in.readByte().toInt
            withLayer(layer){removeTile_do(Point(in.readUByte(),in.readUByte()))}
            revision+=1
        case _ =>
    }

    def sendTileAdded(tile:ICTile): Unit =
    {
        val layer=tile.tileMap.layerIndex
        val out = network.getICStreamOf(if layer==0 then 1 else 12)
        if layer!=0 then out.writeByte(layer)
        out.writeByte(tile.id)
        out.writeByte(tile.pos.x).writeByte(tile.pos.y)
        tile.writeDesc(out)
    }

    def sendRemoveTile(pos:Point): Unit =
    {
        val out=network.getICStreamOf(if currentLayer==0 then 2 else 13)
        if currentLayer!=0 then out.writeByte(currentLayer)
        out.writeByte(pos.x).writeByte(pos.y)
    }

    def sendOpUse(op:TileEditorOp, start:Point, end:Point) =
    {
        if op.checkOp(this, start, end) then {
            val out=network.getICStreamOf(if currentLayer==0 then 3 else 10)
            if currentLayer!=0 then out.writeByte(currentLayer)
            op.writeOp(this,start,end,out.writeByte(op.id))
            true
        }
        else false
    }

    def sendClientPacket(tile:TClientNetICTile, writer:MCDataOutput => Unit): Unit =
    {
        val layer=tile.tileMap.layerIndex
        val s=network.getICStreamOf(if layer==0 then 4 else 11)
        if layer!=0 then s.writeByte(layer)
        s.writeByte(tile.pos.x).writeByte(tile.pos.y)
        writer(s)
    }

    def sendIOUpdate(): Unit =
    {
        val stream = network.getICStreamOf(5)
            for r <- 0 until 4 do
                stream.writeInt(simEngineContainer.iostate(r))
    }

    def sendInputUpdate(r:Int): Unit = //TODO Remove?
    {
        network.getICStreamOf(6).writeByte(r).writeShort(simEngineContainer.iostate(r)&0xFFFF)
    }

    def sendOutputUpdate(r:Int): Unit = //TODO Remove?
    {
        network.getICStreamOf(7).writeByte(r).writeShort(simEngineContainer.iostate(r)>>>16)
    }

    def sendCompileLog(): Unit =
    {
        simEngineContainer.logger.writeLog(network.getICStreamOf(8))
    }

    def sendWorldTimeOffset(): Unit =
    {
        network.getICStreamOf(9).writeLong(worldTimeOffset)
    }

    def clear(): Unit =
    {
        rootTileMapContainer.allTiles.foreach(_.unbind())
        rootTileMapContainer.tiles.clear();rootTileMapContainer.extraLayers.clear()
        rootTileMapContainer.minLayer=0;rootTileMapContainer.maxLayer=0
        scheduledTicks = MMap()
        tileMapContainer.name = "untitled"
        tileMapContainer.size = Size.zeroSize
        for i <- 0 until 4 do simEngineContainer.iostate(i) = 0
        simNeedsRefresh = true
        revision+=1
    }

    def getTotalSimTimeClient = network.getEditorWorld.getTotalWorldTime-worldTimeOffset

    def canInteractSimulation:Boolean = if externalSimulationActive != null then externalSimulationActive()
        else network.isSimulationRunning && !simNeedsRefresh &&
            simEngineContainer.simEngine != null && simEngineContainer.logger.getErrors.isEmpty

    def isEmpty = tileMapContainer.isEmpty
    def nonEmpty = tileMapContainer.nonEmpty

    def tick():Unit = tick(autoCompile=true)

    def tick(autoCompile:Boolean): Unit =
    {
        if simNeedsRefresh && !autoCompile then return
        //Update tiles as needed
        val t = network.getEditorWorld.getTotalWorldTime
        val due = scheduledTicks.iterator.filter { case (_, deadline) => deadline <= t }.toList
        for (point, deadline) <- due do {
            // Remove the old entry before callbacks, which may schedule this or another tile again.
            if scheduledTicks.get(point).contains(deadline) then {
                scheduledTicks.remove(point)
                val tile = getTile(point)
                if tile != null then tile.scheduledTick()
            }
        }

        //Tick tiles
        for tile <- tileMapContainer.tiles.values do tile.update()

        //Rebuild circuit if needed
        if simNeedsRefresh then {
            if !autoCompile then return
            recompileSchematic()
            worldTimeOffset = network.getEditorWorld.getTotalWorldTime
            sendWorldTimeOffset()
        }

        //Tick Simulation time
        simEngineContainer.advanceTime(if lastWorldTime >= 0 then t-lastWorldTime else 1) //if first tick, advance 1 tick only
        simEngineContainer.repropagate()
        lastWorldTime = t
    }

    def setTile(pos:Point, tile:ICTile): Unit =
    {
        assert(!network.isRemote, "Tiles can only be added server-side")
        setTile_do(pos, tile)

        sendTileAdded(tile)
        network.markSave()
        markSchematicChanged()
    }

    private def setTile_do(pos:Point, tile:ICTile): Unit =
    {
        tileMapContainer.assertCoords(pos.x, pos.y)
        scheduledTicks.remove(pos)
        tile.bindPos(pos)
        tile.bindEditor(this)
        tileMapContainer.tiles += (pos.x, pos.y) -> tile
        tile.onAdded()
    }

    def getTile(pos:Point):ICTile = tileMapContainer.getTile(pos.x, pos.y)

    def removeTile(pos:Point): Unit =
    {
        assert(!network.isRemote, "Tiles can only be removed server-side")
        if removeTile_do(pos) then {
            sendRemoveTile(pos)
            network.markSave()
            markSchematicChanged()
        }
    }

    private def removeTile_do(pos:Point):Boolean =
    {
        tileMapContainer.assertCoords(pos.x, pos.y)
        val tile = getTile(pos)
        if tile == null then
            return false

        tileMapContainer.tiles.remove((pos.x, pos.y))
        scheduledTicks.remove(pos)
        tile.onRemoved()
        tile.unbind()
        true
    }

    def notifyNeighbor(pos:Point): Unit =
    {
        val tile = getTile(pos)
        if tile != null then tile.onNeighborChanged()
    }

    def notifyNeighbors(pos:Point, mask:Int): Unit =
    {
        for r <- 0 until 4 do if (mask&1<<r) != 0 then {
            val tile = getTile(pos.offset(r))
            if tile != null then tile.onNeighborChanged()
        }
    }

    def scheduleTick(pos:Point, ticks:Int): Unit ={scheduledTicks += pos -> (network.getEditorWorld.getTotalWorldTime+ticks)}

    def markSchematicChanged(): Unit =
    {
        simNeedsRefresh = true
        revision+=1
    }

    def recompileSchematic(): Unit =
    {
        simNeedsRefresh = false
        simEngineContainer.delegate = this
        simEngineContainer.recompileSimulation(tileMapContainer)
    }

    override def registersDidChange(registers:Set[Int]): Unit =
    {
        for tile <- tileMapContainer.tiles.values do
            tile.onRegistersChanged(registers)
    }

    override def ioRegistersDidChange(): Unit =
    {
        sendIOUpdate()
    }

    override def logDidChange(): Unit =
    {
        sendCompileLog()
    }
}
