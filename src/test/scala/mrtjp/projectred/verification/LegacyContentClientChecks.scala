package mrtjp.projectred.verification

import java.util.concurrent.atomic.AtomicReference
import mrtjp.core.gui.NodeGui
import mrtjp.projectred.ProjectRedFabrication
import mrtjp.projectred.fabrication.*
import net.minecraft.client.Minecraft
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.{EnumHand, ScreenShotHelper}
import net.minecraft.util.math.BlockPos
import scala.jdk.CollectionConverters.*

/** Exercise summary-only recovery stacks through real container transactions and use packets. */
object LegacyContentClientChecks {
    private var action:java.util.concurrent.Future[?] = null
    private var phase = 0
    private var ticks = 0
    private var previous = Array.empty[ItemStack]
    private val origin = new AtomicReference[BlockPos]()
    def start():Unit = {
        val mc = Minecraft.getMinecraft
        val server = mc.getIntegratedServer
        val id = mc.player.getUniqueID
        action = server.addScheduledTask(new Runnable {
            def run():Unit = {
                val player = server.getPlayerList.getPlayerByUUID(id)
                val world = player.getServerWorld
                previous = Array.tabulate(player.inventory.getSizeInventory)(i=>player.inventory.getStackInSlot(i).copy())
                player.closeScreen()
                player.inventory.clear()
                val pos = player.getPosition.add(2,0,0)
                origin.set(pos)
                world.setBlockState(pos,ProjectRedFabrication.icBlock.getStateFromMeta(1),3)
                val printer = world.getTileEntity(pos).asInstanceOf[TileICPrinter]
                val blueprint = FabricationProductionChecks.blueprint()
                val chip = new ItemStack(ProjectRedFabrication.itemICChip)
                ItemICBlueprint.copyIC(blueprint,chip)
                printer.setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.GOLD_INGOT,7))
                printer.setInventorySlotContents(17,new ItemStack(net.minecraft.init.Items.DIAMOND,9))
                printer.setInventorySlotContents(18,blueprint)
                printer.setInventorySlotContents(19,new ItemStack(ProjectRedFabrication.itemICChip))
                printer.setInventorySlotContents(20,chip)
                printer.isWorking = true
                printer.progress = 0.9
                val saved = printer.writeToNBT(new NBTTagCompound)
                val delayed = new DelayedLegacyPrinter
                delayed.setWorld(world)
                delayed.readFromNBT(saved)
                world.setTileEntity(pos,delayed)
                val filterPos = pos.add(2,0,0)
                world.setBlockState(filterPos,mrtjp.projectred.ProjectRedExpansion.machine2.getStateFromMeta(3),3)
                val filtered = new DelayedLegacyFiltered
                filtered.setWorld(world)
                filtered.setPos(filterPos)
                filtered.setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.GOLD_INGOT,4))
                world.setTileEntity(filterPos,filtered)
                player.connection.setPlayerLocation(pos.getX+0.5,pos.getY+1,pos.getZ-2.5,0,0)
                player.inventoryContainer.detectAndSendChanges()
            }
        })
    }
    def tick():Boolean = {
        ticks += 1
        assert(ticks < 200,s"Legacy migration client check timed out in phase $phase")
        if !action.isDone then return false
        action.get()
        val mc = Minecraft.getMinecraft
        val server = mc.getIntegratedServer
        val id = mc.player.getUniqueID
        val pos = origin.get()
        phase match {
            case 0 =>
                if !mc.world.getTileEntity(pos).isInstanceOf[TileICPrinter] ||
                    !mc.world.getTileEntity(pos.add(2,0,0)).isInstanceOf[mrtjp.projectred.expansion.TileFilteredImporter] then return false
                action = server.addScheduledTask(new Runnable {
                    def run():Unit = {
                        val world = server.getPlayerList.getPlayerByUUID(id).getServerWorld
                        assert(world.getTileEntity(pos).asInstanceOf[TileICPrinter].migrateToPackaging())
                        assert(mrtjp.projectred.expansion.LegacyDeviceMigration.convert(
                            world.getTileEntity(pos.add(2,0,0)).asInstanceOf[mrtjp.projectred.expansion.TileFilteredImporter]))
                    }
                })
                ticks = 0; phase = 6
            case 6 =>
                if !mc.world.getTileEntity(pos).isInstanceOf[TilePackagingTable] then return false
                val imported = mc.world.getTileEntity(pos.add(2,0,0))
                if imported == null || imported.getClass != classOf[mrtjp.projectred.expansion.TileItemImporter] then return false
                action = server.addScheduledTask(new Runnable {
                    def run():Unit = {
                        val player = server.getPlayerList.getPlayerByUUID(id)
                        val table = player.getServerWorld.getTileEntity(pos).asInstanceOf[TilePackagingTable]
                        assert(!table.isWorking && table.getStackInSlot(4).getItem == ProjectRedFabrication.itemValidDie)
                        table.openGui(player)
                    }
                })
                ticks = 0; phase = 1
            case 1 =>
                if !mc.currentScreen.isInstanceOf[GuiPackagingTable] || ticks < 4 then return false
                val output = mc.player.openContainer.getSlot(9)
                if output.getStack.getItemDamage != 2 || output.getStack.getItem != ProjectRedFabrication.itemICChip then return false
                // Integrated local channels skip packet serialization. Round-trip the actual
                // vanilla codec and use that received stack for the following GUI transaction.
                val buffer = new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.buffer())
                val received = try {
                    buffer.writeItemStack(output.getStack)
                    buffer.readItemStack()
                } finally buffer.release()
                assert(received.getTagCompound.getBoolean("recovery_summary"))
                assert(!received.getTagCompound.hasKey("BlockEntityTag"),"Private recovery inventory was sent over the item codec")
                output.putStack(received)
                val model = mc.getRenderItem.getItemModelWithOverrides(output.getStack,mc.world,mc.player)
                assert(model != mc.getRenderItem.getItemModelMesher.getModelManager.getMissingModel)
                ScreenShotHelper.saveScreenshot(mc.gameDir,"projectred-legacy-packaging.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer)
                val gui = mc.currentScreen.asInstanceOf[NodeGui]
                gui.mouseClicked(gui.position.x+output.xPos+8,gui.position.y+output.yPos+8,0)
                gui.mouseReleased(gui.position.x+output.xPos+8,gui.position.y+output.yPos+8,0)
                ticks = 0; phase = 2
            case 2 =>
                if mc.player.inventory.getItemStack.isEmpty then return false
                val destination = mc.player.openContainer.inventorySlots.asScala.find(s=>s.inventory == mc.player.inventory && s.getSlotIndex == 0).get
                val gui = mc.currentScreen.asInstanceOf[NodeGui]
                gui.mouseClicked(gui.position.x+destination.xPos+8,gui.position.y+destination.yPos+8,0)
                gui.mouseReleased(gui.position.x+destination.xPos+8,gui.position.y+destination.yPos+8,0)
                ticks = 0; phase = 3
            case 3 =>
                val held = mc.player.inventory.getStackInSlot(0)
                if held.getItem != ProjectRedFabrication.itemICChip || held.getItemDamage != 2 || !mc.player.inventory.getItemStack.isEmpty then return false
                mc.player.closeScreen()
                mc.player.inventory.currentItem = 0
                mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND)
                ticks = 0; phase = 4
            case 4 =>
                def count(item:net.minecraft.item.Item):Int =
                    (0 until mc.player.inventory.getSizeInventory).map(mc.player.inventory.getStackInSlot).filter(_.getItem == item).map(_.getCount).sum
                if count(net.minecraft.init.Items.GOLD_INGOT) != 7 || count(net.minecraft.init.Items.DIAMOND) != 9 then return false
                assert(count(ProjectRedFabrication.itemICBlueprint) == 1 && count(ProjectRedFabrication.itemRoughSiliconWafer) == 1)
                ScreenShotHelper.saveScreenshot(mc.gameDir,"projectred-legacy-recovery.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer)
                action = server.addScheduledTask(new Runnable {
                    def run():Unit = {
                        val player = server.getPlayerList.getPlayerByUUID(id)
                        val held = player.inventory.getStackInSlot(0)
                        assert(held.getTagCompound.getCompoundTag("BlockEntityTag").getTagList("Items",10).tagCount == 0)
                        assert(player.getServerWorld.getTileEntity(pos).asInstanceOf[TilePackagingTable].getStackInSlot(9).isEmpty)
                        player.getServerWorld.setBlockToAir(pos)
                        player.getServerWorld.setBlockToAir(pos.add(2,0,0))
                        for i <- previous.indices do player.inventory.setInventorySlotContents(i,previous(i))
                        player.inventoryContainer.detectAndSendChanges()
                    }
                })
                ticks = 0; phase = 5
            case 5 =>
                println("PROJECTRED_LEGACY_CONTENT_CLIENT_PASS: saved Printer migrated, Packaging GUI transferred recovery archive, use packet recovered exact contents")
                return true
            case _ =>
        }
        false
    }
}

class DelayedLegacyPrinter extends TileICPrinter {
    override def updateServer():Unit = ()
}
class DelayedLegacyFiltered extends mrtjp.projectred.expansion.TileFilteredImporter {
    override def updateServer():Unit = ()
}
