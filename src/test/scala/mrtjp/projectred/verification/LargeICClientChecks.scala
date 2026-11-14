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
        assert(ticks < 200,s"Large IC client verification timed out in phase $phase")
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
                    mc.player.closeScreen()
                    val gatePos = position.get().add(2,0,0)
                    val result = mc.playerController.processRightClickBlock(mc.player,mc.world,gatePos.down(),EnumFacing.UP,
                        new Vec3d(gatePos.getX+0.5,gatePos.getY,gatePos.getZ+0.5),EnumHand.MAIN_HAND)
                    assert(result == EnumActionResult.SUCCESS,"Large IC gate placement was rejected")
                    ticks = 0; phase = 2
                }
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
