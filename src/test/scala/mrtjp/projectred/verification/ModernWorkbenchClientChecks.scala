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
                val point=gui.pref.convertPointToScreen(Point(8*8+4,4))
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
                mc.player.closeScreen()
                println("PROJECTRED_MODERN_WORKBENCH_CLIENT_PASS: four I/O types rendered and bus channel 15 toggled through its actual GUI")
                return true
            case _ =>
        }
        false
    }
}
