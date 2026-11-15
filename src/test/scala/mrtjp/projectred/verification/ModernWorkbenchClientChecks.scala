package mrtjp.projectred.verification

import java.util.concurrent.atomic.AtomicReference
import mrtjp.core.vec.{Point,Size}
import mrtjp.projectred.fabrication.*
import net.minecraft.client.Minecraft
import net.minecraft.util.ScreenShotHelper
import net.minecraft.util.math.BlockPos

object ModernWorkbenchClientChecks {
    private var phase=0
    private var ticks=0
    private var action:java.util.concurrent.Future[?]=null
    private val position=new AtomicReference[BlockPos]()
    def start():Unit = {
        val mc=Minecraft.getMinecraft
        val server=mc.getIntegratedServer
        val id=mc.player.getUniqueID
        action=server.addScheduledTask(new Runnable {
            def run():Unit = {
                val player=server.getPlayerList.getPlayerByUUID(id)
                val world=player.getServerWorld
                val pos=player.getPosition.add(0,0,2)
                world.setBlockToAir(pos)
                world.setBlockState(pos,mrtjp.projectred.ProjectRedFabrication.icBlock.getStateFromMeta(0),3)
                val tile=world.getTileEntity(pos).asInstanceOf[TileICWorkbench]
                tile.hasBP=true
                tile.editor.tileMapContainer.size=Size(16,16)
                def io(p:Point,rotation:Int,shape:Int,kind:Int):Unit = {
                    val gate=new IOGateICTile
                    gate.preparePlacement(rotation,kind);gate.setShape(shape)
                    tile.editor.setTile(p,gate)
                }
                io(Point(8,0),0,0,ICGateDefinition.IOBundledBus.ordinal)
                io(Point(8,15),2,1,ICGateDefinition.IOBundledBus.ordinal)
                io(Point(0,8),3,0,ICGateDefinition.IOBundled.ordinal)
                io(Point(15,8),1,0,ICGateDefinition.IOAnalog.ordinal)
                for y <- 1 to 14 do {
                    val wire=new BundledCableICTile;wire.colour=(-1).toByte
                    tile.editor.setTile(Point(8,y),wire)
                }
                val counter=new SequentialGateICTile;counter.preparePlacement(0,ICGateDefinition.Counter.ordinal)
                tile.editor.setTile(Point(3,3),counter)
                tile.editor.rootTileMapContainer.maxLayer=1
                tile.editor.withLayer(1){val timer=new SequentialGateICTile;timer.preparePlacement(0,ICGateDefinition.Timer.ordinal);tile.editor.setTile(Point(4,4),timer)}
                tile.controller.markEdited()
                tile.markDescUpdate()
                player.inventory.setInventorySlotContents(0,net.minecraft.item.ItemStack.EMPTY)
                position.set(pos)
            }
        })
    }
    def tick():Boolean = {
        ticks+=1
        assert(ticks<300,s"Modern Workbench client timed out at phase $phase")
        if !action.isDone then return false
        action.get()
        val mc=Minecraft.getMinecraft
        if phase == 0 then {
            val pos=position.get()
            mc.world.getTileEntity(pos) match {
                case tile:TileICWorkbench if tile.hasBP =>
                    val server=mc.getIntegratedServer;val id=mc.player.getUniqueID
                    action=server.addScheduledTask(new Runnable {
                        def run():Unit = {
                            val player=server.getPlayerList.getPlayerByUUID(id)
                            val tile=player.getServerWorld.getTileEntity(pos).asInstanceOf[TileICWorkbench]
                            assert(tile.onBlockActivated(player,1))
                        }
                    })
                    ticks=0;phase=4
                case _ =>
            }
            return false
        }
        if !mc.currentScreen.isInstanceOf[GuiICWorkbench] then return false
        val gui=mc.currentScreen.asInstanceOf[GuiICWorkbench]
        if ticks%60==0 then println(s"MODERN_BUS_GUI: phase=$phase state=${gui.tile.controller.state} tiles=${gui.tile.editor.tileMapContainer.tiles.size} bp=${gui.tile.hasBP}")
        phase match {
            case 4 if gui.tile.controller.state==3 && ticks>10 =>
                val point=gui.pref.worldScreenPoint(.5,-7.90)
                gui.mouseClicked(point.x,point.y,1);gui.mouseReleased(point.x,point.y,1)
                assert(gui.pref.children.exists(_.isInstanceOf[ICBusIOGui]),"Bus I/O controls did not open")
                ticks=0;phase=1
            case 1 if ticks>5 =>
                val popup=gui.pref.children.collectFirst{case p:ICBusIOGui=>p}.get
                val point=popup.convertPointToScreen(Point(140,77))
                gui.mouseClicked(point.x,point.y,0);gui.mouseReleased(point.x,point.y,0)
                ticks=0;phase=2
            case 2 =>
                val bus=gui.tile.editor.getTile(Point(8,15)).asInstanceOf[IOGateICTile]
                if bus.getLogic[BundledBusIOGateTileLogic].busMask != 0x8000 then return false
                ScreenShotHelper.saveScreenshot(mc.gameDir,"projectred-workbench-bus.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer)
                val server=mc.getIntegratedServer;val id=mc.player.getUniqueID
                action=server.addScheduledTask(new Runnable {
                    def run():Unit = {
                        val tile=server.getPlayerList.getPlayerByUUID(id).getServerWorld.getTileEntity(position.get()).asInstanceOf[TileICWorkbench]
                        assert((tile.controller.modern.context.getStateMachine.getSimulation.getOutput(2)&65535)==0x8000,
                            "Bus channel 15 mouse action did not reach the server simulation")
                    }
                })
                ticks=0;phase=3
            case 3 =>
                gui.pref.children.foreach(_.removeFromParent())
                gui.pref.focusAt(Point(3,3));ticks=0;phase=5
            case 5 if ticks>12 =>
                val point=gui.pref.zoneScreenPoint(Point(3,3),7)
                gui.mouseClicked(point.x,point.y,0);gui.mouseReleased(point.x,point.y,0)
                ticks=0;phase=6
            case 6 =>
                val counter=gui.tile.editor.getTile(Point(3,3)).asInstanceOf[SequentialGateICTile].getLogic[Counter]
                if counter.max!=20 then return false
                gui.pref.setLayer(1);ticks=0;phase=7
            case 7 if ticks>8 =>
                assert(gui.pref.camera.layer==1 && math.abs(gui.pref.camera.layerAnimation.vector.y-1)<1e-6,s"Layer transition did not finish: ${gui.pref.camera.layerAnimation.vector.y}")
                assert(gui.tile.editor.rootTileMapContainer.extraLayers(1).tiles.size==1,"Upper layer was not synchronized")
                ScreenShotHelper.saveScreenshot(mc.gameDir,"projectred-workbench-layer1.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer)
                gui.pref.focusAt(Point(4,4),1)
                gui.pref.currentOp=TileEditorOpDefs.Erase.getOp
                ticks=0;phase=8
            case 8 if ticks>12 =>
                val point=gui.pref.tileScreenPoint(Point(4,4))
                gui.mouseClicked(point.x,point.y,0);gui.mouseReleased(point.x,point.y,0)
                ticks=0;phase=9
            case 9 =>
                if gui.tile.editor.rootTileMapContainer.extraLayers(1).tiles.nonEmpty then return false
                gui.pref.currentOp=TileEditorOpDefs.AlloyWire.getOp
                gui.pref.focusAt(Point(6,6),0);ticks=0;phase=10
            case 10 if ticks>12 =>
                val start=gui.pref.tileScreenPoint(Point(5,5));val bend=gui.pref.tileScreenPoint(Point(6,5));val end=gui.pref.tileScreenPoint(Point(6,6))
                gui.mouseClicked(start.x,start.y,0)
                gui.mouseClickMove(bend.x,bend.y,0,50)
                gui.mouseClickMove(end.x,end.y,0,100)
                gui.mouseReleased(end.x,end.y,0)
                ticks=0;phase=11
            case 11 =>
                if !Seq(Point(5,5),Point(6,5),Point(6,6)).forall(p=>gui.tile.editor.getTile(p).isInstanceOf[AlloyWireICTile]) then return false
                mc.player.closeScreen()
                println("PROJECTRED_MODERN_WORKBENCH_CLIENT_PASS: perspective, bus control, Counter zone, layer transition, upper-layer edit and freehand wires")
                return true
            case _ =>
        }
        false
    }
}
