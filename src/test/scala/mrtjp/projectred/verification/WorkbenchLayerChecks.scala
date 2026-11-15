package mrtjp.projectred.verification

import mrtjp.core.vec.{Point,Size}
import mrtjp.projectred.fabrication.*
import codechicken.lib.packet.PacketCustom
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.world.WorldServer

object WorkbenchLayerChecks {
    def run(world:WorldServer):Unit={
        val camera=new WorkbenchCamera
        camera.width=290;camera.height=197
        camera.focus(-8,-8,8,8);camera.tick(0,16,16);camera.tick(500,16,16)
        for (x,y)<-Seq((0D,0D),(145D,98D),(290D,197D)) do {
            val p=camera.worldAt(x,y);val (sx,sy)=camera.screenAt(p.x,p.z)
            assert(math.abs(sx-x)<1e-6 && math.abs(sy-y)<1e-6,s"Viewport picking differs: ($x,$y) -> $p -> ($sx,$sy), camera=${camera.position}")
        }
        camera.setLayer(1);camera.tick(600,16,16);camera.tick(800,16,16)
        assert(camera.layerAnimation.vector.y==1 && camera.worldAt(145,98).y==1.125,"Layer transition or picking plane differs")
        class Network extends IICTileEditorNetwork {
            val getIC=new ICTileMapEditor(this)
            def getEditorWorld=world
            def isRemote=true
            def markSave():Unit=()
            private val sink=new codechicken.lib.data.MCDataOutputWrapper(java.io.OutputStream.nullOutputStream())
            def getICStreamOf(key:Int):codechicken.lib.data.MCDataOutput=sink
            def getTileStream(point:Point):codechicken.lib.data.MCDataOutput=sink
        }
        val network=new Network;val editor=network.getIC;val root=editor.rootTileMapContainer
        root.size=Size(16,16);root.maxLayer=1
        def add(layer:Int,y:Int,part:ICTile):Unit=editor.withLayer(layer){
            part.bindEditor(editor);part.bindPos(Point(8,y));editor.tileMapContainer.tiles((8,y))=part
        }
        val input=new IOGateICTile;input.preparePlacement(0,ICGateDefinition.IOSimple.ordinal)
        val output=new IOGateICTile;output.preparePlacement(2,ICGateDefinition.IOSimple.ordinal);output.setShape(1)
        add(0,0,input);add(1,15,output)
        for y<-1 to 8 do add(0,y,new AlloyWireICTile)
        for y<-8 to 14 do add(1,y,new AlloyWireICTile)
        val model=new ModernDesign(editor);model.rebuild();model.context.tileChanged=_=>()
        ModernEngineChecks.finish(model.context)
        assert(model.context.getStateMachine.isSimulating,"A multilayer drawing did not compile")
        model.context.getStateMachine.onInputRegistersChanged(0,_=>1.toShort)
        assert(model.context.getStateMachine.getSimulation.getOutput(2)==1,"Vertical wire connection lost its signal")
        val saved=new NBTTagCompound;root.saveTiles(saved)
        val loaded=new ICTileMapContainer;loaded.loadTiles(saved)
        assert(loaded.allTiles.size==17 && loaded.extraLayers(1).tiles.size==8,"Layered NBT lost components")
        val packet=new PacketCustom("PRVerification",1);editor.writeDesc(packet)
        val incoming=new PacketCustom(packet.copy());val remote=new Network
        remote.getIC.readDesc(incoming)
        assert(remote.getIC.rootTileMapContainer.extraLayers(1).tiles.size==8 && incoming.readableBytes()==0,"Layered descriptions lost framing")
        packet.release();incoming.release()
        val upperOnly=new TileICWorkbench
        upperOnly.setWorld(world);upperOnly.setPos(new net.minecraft.util.math.BlockPos(38,80,24));upperOnly.hasBP=true
        upperOnly.editor.rootTileMapContainer.loadTiles(saved)
        upperOnly.editor.rootTileMapContainer.tiles.clear()
        upperOnly.controller.markEdited()
        assert(upperOnly.controller.state==1 && upperOnly.editor.rootTileMapContainer.tileCount==8,
            "An upper-layer-only drawing was treated as an empty Workbench")
        val negative=new ICTileMapContainer;negative.size=Size(16,16);negative.minLayer = -1
        val below=new AlloyWireICTile;val lower=negative.layerMap(-1);below.bindTileMap(lower);below.bindPos(Point(4,4));lower.tiles((4,4))=below
        val negativeTag=new NBTTagCompound;negative.saveTiles(negativeTag)
        val restoredNegative=new ICTileMapContainer;restoredNegative.loadTiles(negativeTag)
        assert(restoredNegative.extraLayers(-1).tiles.size==1,"Negative layer index did not survive NBT")
        val editing=new TileICWorkbench;editing.setWorld(world);editing.setPos(new net.minecraft.util.math.BlockPos(38,80,24));editing.hasBP=true
        editing.editor.rootTileMapContainer.size=Size(16,16)
        val counter=new SequentialGateICTile;counter.preparePlacement(0,ICGateDefinition.Counter.ordinal)
        editing.editor.setTile(Point(4,4),counter)
        editing.controller.interact(0,Point(4,4),7,true)
        assert(counter.getLogic[Counter].max==20,"Dedicated-side Counter interaction zone did not update its settings")
        println("PROJECTRED_WORKBENCH_LAYERS_PASS: perspective picking, layer animation, vertical wires, NBT and descriptions")
    }
}
