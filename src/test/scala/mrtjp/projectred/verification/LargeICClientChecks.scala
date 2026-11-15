package mrtjp.projectred.verification

import java.util.concurrent.atomic.AtomicReference
import mrtjp.projectred.fabrication.{GuiICWorkbench, ICGatePart, ItemICBlueprint, TileICWorkbench}
import net.minecraft.client.Minecraft
import net.minecraft.util.{EnumActionResult, EnumFacing, EnumHand, ScreenShotHelper}
import net.minecraft.util.math.{BlockPos, Vec3d}

object LargeICClientChecks {
    private var phase = 0
    private var ticks = 0
    private var action:java.util.concurrent.Future[?] = null
    private val position = new AtomicReference[BlockPos]()
    private val savedGate = new AtomicReference[net.minecraft.item.ItemStack]()
    private var originalWidth = 0
    private var originalHeight = 0
    private def click(gui:GuiICWorkbench,x:Int,y:Int):Unit = {
        gui.mouseClicked(gui.position.x+x,gui.position.y+y,0)
        gui.mouseReleased(gui.position.x+x,gui.position.y+y,0)
    }

    def start():Unit = {
        val mc = Minecraft.getMinecraft
        val server = mc.getIntegratedServer
        val playerId = mc.player.getUniqueID
        action = server.addScheduledTask(new Runnable {
            def run():Unit = {
                val player = server.getPlayerList.getPlayerByUUID(playerId)
                val world = player.getServerWorld
                val pos = player.getPosition.add(1,0,0)
                world.setBlockToAir(pos)
                world.setBlockState(pos,mrtjp.projectred.ProjectRedFabrication.icBlock.getStateFromMeta(0),3)
                val tile = world.getTileEntity(pos).asInstanceOf[TileICWorkbench]
                tile.hasBP = true
                LargeICPacketChecks.fixture(tile.editor)
                tile.editor.recompileSchematic()
                val gate = mrtjp.projectred.integration.GateDefinition.ICGate.makeStack
                ItemICBlueprint.saveICToGate(tile.editor.tileMapContainer,gate)
                savedGate.set(gate.copy())
                player.inventory.setItemStack(net.minecraft.item.ItemStack.EMPTY)
                player.inventory.currentItem = 0
                player.inventory.setInventorySlotContents(0,gate)
                val gatePos = pos.add(2,0,0)
                world.setBlockToAir(gatePos)
                world.setBlockState(gatePos.down(),net.minecraft.init.Blocks.STONE.getDefaultState,3)
                position.set(pos)
            }
        })
    }

    def tick():Boolean = {
        val mc = Minecraft.getMinecraft
        val server = mc.getIntegratedServer
        val playerId = mc.player.getUniqueID
        ticks += 1
        assert(ticks < (if phase == 15 then 2400 else 200),s"Large IC client verification timed out in phase $phase")
        phase match {
            case 0 if action.isDone =>
                action.get()
                val pos = position.get()
                if pos != null && mc.world.getTileEntity(pos).isInstanceOf[TileICWorkbench] &&
                    ItemICBlueprint.hasICInside(mc.player.getHeldItemMainhand) then {
                    assert(ItemICBlueprint.loadTileMap(mc.player.getHeldItemMainhand).tiles.size == 3844,
                        "Large IC inventory synchronization lost its circuit")
                    action = server.addScheduledTask(new Runnable {
                        def run():Unit = {
                            val player = server.getPlayerList.getPlayerByUUID(playerId)
                            val tile = player.getServerWorld.getTileEntity(pos).asInstanceOf[TileICWorkbench]
                            player.inventory.setInventorySlotContents(0,net.minecraft.item.ItemStack.EMPTY)
                            assert(tile.onBlockActivated(player,1),"Large IC Workbench refused to open")
                        }
                    })
                    ticks = 0; phase = 1
                }
            case 1 if action.isDone =>
                action.get()
                if mc.currentScreen.isInstanceOf[GuiICWorkbench] && ticks >= 10 then {
                    val gui = mc.currentScreen.asInstanceOf[GuiICWorkbench]
                    assert(gui.tile.editor.tileMapContainer.tiles.size == 3844,
                        "Large IC Workbench description did not reach the client")
                    ScreenShotHelper.saveScreenshot(mc.gameDir,"projectred-large-ic.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer)
                    click(gui,8,15)
                    assert(gui.activeTab == 0,"Workbench information tab did not respond to the mouse")
                    ticks = 0; phase = 10
                }
            case 10 if ticks >= 5 =>
                val gui = mc.currentScreen.asInstanceOf[GuiICWorkbench]
                ScreenShotHelper.saveScreenshot(mc.gameDir,"projectred-workbench-info.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer)
                click(gui,8,65)
                assert(gui.activeTab == 2,"Workbench diagnostics tab did not respond to the mouse")
                ticks = 0; phase = 11
            case 11 if ticks >= 5 =>
                val gui = mc.currentScreen.asInstanceOf[GuiICWorkbench]
                println(s"MODERN_WORKBENCH_CLICK: state=${gui.tile.controller.state}, bp=${gui.tile.hasBP}, tab=${gui.activeTab}")
                click(gui,273,116)
                action = server.addScheduledTask(new Runnable {
                    def run():Unit = {
                        val player=server.getPlayerList.getPlayerByUUID(playerId)
                        val tile=player.getServerWorld.getTileEntity(position.get()).asInstanceOf[TileICWorkbench]
                        println(s"MODERN_WORKBENCH_SERVER: state=${tile.controller.state}, watching=${tile.watchers.contains(player)}, window=${player.openContainer.windowId}")
                    }
                })
                ticks=0;phase=15
            case 15 =>
                val gui = mc.currentScreen.asInstanceOf[GuiICWorkbench]
                if ticks%200 == 0 then println(s"MODERN_WORKBENCH_PROGRESS: state=${gui.tile.controller.state}, steps=${gui.tile.controller.completedSteps}/${gui.tile.controller.totalSteps}, received=${gui.tile.controller.compilerLog.getCompletedSteps}")
                if gui.tile.controller.state != 4 then return false
                assert(gui.tile.controller.completedSteps > 20000,"Large IC bypassed the staged compiler")
                if gui.tile.controller.compilerLog.getCompletedSteps != gui.tile.controller.completedSteps then return false
                ScreenShotHelper.saveScreenshot(mc.gameDir,"projectred-workbench-compile.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer)
                click(gui,273,192)
                assert(gui.showCompileTree,"Compile-tree toggle did not respond")
                ticks=0;phase=16
            case 16 if ticks >= 5 =>
                val gui=mc.currentScreen.asInstanceOf[GuiICWorkbench]
                ScreenShotHelper.saveScreenshot(mc.gameDir,"projectred-workbench-tree.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer)
                click(gui,8,40)
                ticks = 0; phase = 12
            case 12 if ticks >= 5 =>
                val gui = mc.currentScreen.asInstanceOf[GuiICWorkbench]
                gui.pref.showTooltips = false
                val group = gui.toolSets(3)
                val screen = group.convertPointToScreen(mrtjp.core.vec.Point(8,8))
                gui.mouseClicked(screen.x,screen.y,0); gui.mouseReleased(screen.x,screen.y,0)
                assert(group.isPopupOpen,"Workbench tool group did not open its palette")
                originalWidth = gui.width; originalHeight = gui.height
                ticks = 0; phase = 14
            case 14 if ticks >= 3 =>
                val gui = mc.currentScreen.asInstanceOf[GuiICWorkbench]
                ScreenShotHelper.saveScreenshot(mc.gameDir,"projectred-workbench-tools.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer)
                val group = gui.toolSets(3)
                val option = group.children.collect { case b:mrtjp.core.gui.ButtonNode if !b.hidden && b.position.x < 0 => b }(1)
                val point = option.convertPointToScreen(mrtjp.core.vec.Point(8,8))
                gui.mouseClicked(point.x,point.y,0); gui.mouseReleased(point.x,point.y,0)
                assert(!group.isPopupOpen && gui.pref.currentOp.id == group.opSet(1).id,
                    "Workbench palette option was occluded or did not select its tool")
                gui.setWorldAndResolution(mc,320,240)
                ticks = 0; phase = 13
            case 13 if ticks >= 5 =>
                    val gui = mc.currentScreen.asInstanceOf[GuiICWorkbench]
                    assert(gui.size.width <= 312 && gui.pref.size.width > 0,"Workbench did not fit the minimum GUI width")
                    gui.setWorldAndResolution(mc,originalWidth,originalHeight)
                    mc.player.closeScreen()
                    action=server.addScheduledTask(new Runnable {
                        def run():Unit = server.getPlayerList.getPlayerByUUID(playerId).inventory.setInventorySlotContents(0,savedGate.get())
                    })
                    ticks=0;phase=17
            case 17 if action.isDone && ItemICBlueprint.hasICInside(mc.player.getHeldItemMainhand) =>
                    action.get()
                    val gatePos = position.get().add(2,0,0)
                    val result = mc.playerController.processRightClickBlock(mc.player,mc.world,gatePos.down(),EnumFacing.UP,
                        new Vec3d(gatePos.getX+0.5,gatePos.getY,gatePos.getZ+0.5),EnumHand.MAIN_HAND)
                    assert(result == EnumActionResult.SUCCESS,"Large IC gate placement was rejected")
                    ticks = 0; phase = 2
            case 2 =>
                val gatePos = position.get().add(2,0,0)
                mc.world.getTileEntity(gatePos) match {
                    case tile:codechicken.multipart.TileMultipart if tile.partList.exists(_.isInstanceOf[ICGatePart]) =>
                        val gate = tile.partList.collectFirst { case part:ICGatePart => part }.get
                        assert(ItemICBlueprint.loadTileMap(gate.getItem).tiles.size == 3844,
                            "Placed large IC description lost its circuit on the client")
                        action = server.addScheduledTask(new Runnable {
                            def run():Unit = {
                                val tile = server.getPlayerList.getPlayerByUUID(playerId).getServerWorld
                                    .getTileEntity(gatePos).asInstanceOf[codechicken.multipart.TileMultipart]
                                val gate = tile.partList.collectFirst { case part:ICGatePart => part }.get
                                assert(gate.getLogicIC.tmap.tiles.size == 3844,"Server placed an incomplete large IC")
                            }
                        })
                        ticks = 0; phase = 3
                    case _ =>
                }
            case 3 if action.isDone =>
                action.get()
                println("PROJECTRED_LARGE_IC_CLIENT_PASS: 3844-tile inventory, Workbench GUI and multipart placement synchronized")
                return true
            case _ =>
        }
        false
    }
}
