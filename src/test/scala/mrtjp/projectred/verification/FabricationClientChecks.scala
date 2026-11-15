package mrtjp.projectred.verification

import java.util.concurrent.atomic.AtomicReference
import mrtjp.projectred.ProjectRedFabrication.*
import mrtjp.projectred.core.PartDefs
import mrtjp.projectred.fabrication.*
import mrtjp.core.gui.NodeGui
import net.minecraft.client.Minecraft
import net.minecraft.item.ItemStack
import net.minecraft.util.ScreenShotHelper
import net.minecraft.util.math.BlockPos

object FabricationClientChecks {
    private val origin = new AtomicReference[BlockPos]()
    private var action:java.util.concurrent.Future[?] = null
    private var phase = 0
    private var ticks = 0
    private var index = 0
    private var clicked = false
    def start():Unit = {
        val mc = Minecraft.getMinecraft
        val server = mc.getIntegratedServer
        val id = mc.player.getUniqueID
        action = server.addScheduledTask(new Runnable {
            def run():Unit = {
                val player = server.getPlayerList.getPlayerByUUID(id)
                val world = player.getServerWorld
                val start = player.getPosition.add(2,0,0)
                origin.set(start)
                for i <- 0 until 3 do {
                    val pos = start.add(0,0,i*2)
                    world.setBlockState(pos,fabricationMachineBlock.getStateFromMeta(i),3)
                    val tile = world.getTileEntity(pos).asInstanceOf[TFabricationPowered]
                    val design = FabricationProductionChecks.blueprint()
                    tile match {
                        case table:TilePlottingTable =>
                            table.setInventorySlotContents(0,design)
                            table.setInventorySlotContents(1,new ItemStack(itemBlankPhotomask))
                        case table:TileLithographyTable =>
                            val mask = new ItemStack(itemPhotomaskSet)
                            ICData.copyDesign(design,mask)
                            table.setInventorySlotContents(0,mask)
                            table.setInventorySlotContents(1,new ItemStack(itemRoughSiliconWafer))
                        case table:TilePackagingTable =>
                            val die = new ItemStack(itemValidDie)
                            ICData.copyDesign(design,die)
                            table.setInventorySlotContents(4,die)
                            table.cornerSlots.foreach(slot => table.setInventorySlotContents(slot,PartDefs.PLATE.makeStack))
                            for r <- 0 until 4 do table.setInventorySlotContents(table.sideSlots(r),table.getPlateFor(ICData.getInterfaceType(die,r)).makeStack)
                    }
                    tile.cond.Vloc = 200
                    tile.updateServer()
                    tile.workRemaining = 20 // Exercise GUI output handling after server processing.
                    tile.markDescUpdate()
                }
            }
        })
    }
    def tick():Boolean = {
        val mc = Minecraft.getMinecraft
        val server = mc.getIntegratedServer
        val id = mc.player.getUniqueID
        ticks += 1
        assert(ticks < 200,s"Fabrication GUI verification timed out: table $index, phase $phase")
        if !action.isDone then return false
        action.get()
        val pos = origin.get().add(0,0,index*2)
        phase match {
            case 0 =>
                if mc.world.getTileEntity(pos).isInstanceOf[TileFabricationMachine] then {
                    action = server.addScheduledTask(new Runnable {
                        def run():Unit = {
                            val player = server.getPlayerList.getPlayerByUUID(id)
                            val tile = player.getServerWorld.getTileEntity(pos).asInstanceOf[TileFabricationMachine]
                            player.inventory.setItemStack(ItemStack.EMPTY)
                            assert(tile.onBlockActivated(player,1))
                        }
                    })
                    ticks = 0; phase = 1
                }
            case 1 =>
                val expected = index match {
                    case 0 => classOf[GuiPlottingTable]
                    case 1 => classOf[GuiLithographyTable]
                    case 2 => classOf[GuiPackagingTable]
                }
                if expected.isInstance(mc.currentScreen) && ticks >= 15 then {
                    if index == 0 && !clicked then {
                        val slot = mc.player.openContainer.getSlot(2)
                        if slot.getStack.getItem != itemPhotomaskSet then return false
                        val gui = mc.currentScreen.asInstanceOf[NodeGui]
                        val x = gui.position.x+slot.xPos+8
                        val y = gui.position.y+slot.yPos+8
                        gui.mouseClicked(x,y,0); gui.mouseReleased(x,y,0)
                        clicked = true
                        ticks = 0
                        return false
                    }
                    ScreenShotHelper.saveScreenshot(mc.gameDir,s"projectred-production-$index.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer)
                    action = server.addScheduledTask(new Runnable {
                        def run():Unit = {
                            val player = server.getPlayerList.getPlayerByUUID(id)
                            if index == 0 then assert(player.inventory.getItemStack.getItem == itemPhotomaskSet,
                                "Plotting GUI mouse click did not transfer the produced mask")
                            player.inventory.setItemStack(ItemStack.EMPTY)
                        }
                    })
                    phase = 2
                }
            case 2 =>
                mc.player.closeScreen()
                index += 1
                if index == 3 then {
                    println("PROJECTRED_FABRICATION_CLIENT_PASS: three production GUIs and plotting output mouse interaction")
                    return true
                }
                ticks = 0; phase = 0
            case _ =>
        }
        false
    }
}
