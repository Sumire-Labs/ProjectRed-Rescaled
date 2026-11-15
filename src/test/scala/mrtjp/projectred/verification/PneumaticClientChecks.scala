package mrtjp.projectred.verification

import java.util.concurrent.atomic.AtomicReference
import codechicken.multipart.TileMultipart
import mrtjp.projectred.expansion.TileItemImporter
import mrtjp.projectred.transportation.PipeDefs
import mrtjp.projectred.transportation.pneumatics.part.PneumaticTubePart
import net.minecraft.client.Minecraft
import net.minecraft.init.{Blocks,Items}
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.util.ScreenShotHelper
import net.minecraft.util.math.BlockPos

object PneumaticClientChecks {
    private var phase = 0
    private var ticks = 0
    private var sawPayload = false
    private var action:java.util.concurrent.Future[?] = null
    private val origin = new AtomicReference[BlockPos]()
    private def pipe(world:net.minecraft.world.World,pos:BlockPos):Option[PneumaticTubePart] =
        world.getTileEntity(pos) match {
            case tile:TileMultipart => tile.partList.collectFirst { case part:PneumaticTubePart => part }
            case _ => None
        }
    def start():Unit = {
        val mc = Minecraft.getMinecraft
        val server = mc.getIntegratedServer
        val id = mc.player.getUniqueID
        action = server.addScheduledTask(new Runnable {
            def run():Unit = {
                val player = server.getPlayerList.getPlayerByUUID(id)
                val world = player.getServerWorld
                val start = player.getPosition.add(-3,0,6)
                world.setBlockState(start,mrtjp.projectred.ProjectRedExpansion.machine2.getStateFromMeta(1),3)
                val importer = world.getTileEntity(start).asInstanceOf[TileItemImporter]
                importer.setSide(5)
                importer.markDescUpdate()
                world.setBlockState(start.add(6,0,0),Blocks.CHEST.getDefaultState,3)
                for x <- 1 to 5 do {
                    val pos = start.add(x,0,0)
                    world.setBlockToAir(pos)
                    val tube = new PneumaticTubePart
                    tube.preparePlacement(5,PipeDefs.PNEUMATICTUBE.meta)
                    TileMultipart.addPart(world,pos,tube)
                }
                player.connection.setPlayerLocation(start.getX+3.5,start.getY+1,start.getZ-7.5,0,15)
                origin.set(start)
            }
        })
    }
    def tick():Boolean = {
        ticks += 1
        assert(ticks < 400,s"Pneumatic client verification timed out in phase $phase")
        val mc = Minecraft.getMinecraft
        if !action.isDone then return false
        action.get()
        val start = origin.get()
        val tubes = (1 to 5).flatMap(x => pipe(mc.world,start.add(x,0,0)))
        phase match {
            case 0 if tubes.size == 5 =>
                val server = mc.getIntegratedServer
                val id = mc.player.getUniqueID
                action = server.addScheduledTask(new Runnable {
                    def run():Unit = {
                        val world = server.getPlayerList.getPlayerByUUID(id).getServerWorld
                        val importer = world.getTileEntity(start).asInstanceOf[TileItemImporter]
                        val stack = new ItemStack(Items.GOLD_INGOT,9)
                        val tag = new NBTTagCompound
                        tag.setString("marker","client-pneumatic")
                        stack.setTagCompound(tag)
                        importer.itemStorage.add(stack)
                        importer.exportBuffer()
                        assert(importer.itemStorage.isEmpty,"Importer did not hand off to the new transport")
                    }
                })
                ticks = 0; phase = 1
            case 1 =>
                val moving = tubes.exists(_.getPneumaticTransport.getPayloads.nonEmpty)
                if moving && !sawPayload then {
                    sawPayload = true
                    ticks = 0
                }
                if moving && sawPayload && ticks == 5 then
                    ScreenShotHelper.saveScreenshot(mc.gameDir,"projectred-pneumatic.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer)
                if sawPayload && !moving && ticks > 10 then {
                    val server = mc.getIntegratedServer
                    val id = mc.player.getUniqueID
                    action = server.addScheduledTask(new Runnable {
                        def run():Unit = {
                            val world = server.getPlayerList.getPlayerByUUID(id).getServerWorld
                            val inventory = world.getTileEntity(start.add(6,0,0)).asInstanceOf[TileEntityChest]
                            val result = inventory.getStackInSlot(0)
                            assert(result.getItem == Items.GOLD_INGOT && result.getCount == 9 &&
                                result.getTagCompound.getString("marker") == "client-pneumatic",
                                "Server destination did not receive the synchronized pneumatic payload")
                            for i <- 1 until inventory.getSizeInventory do inventory.setInventorySlotContents(i,new ItemStack(Blocks.STONE,64))
                            inventory.setInventorySlotContents(0,ItemStack.EMPTY)
                            val importer = world.getTileEntity(start).asInstanceOf[TileItemImporter]
                            val returned = new ItemStack(Items.IRON_INGOT,7)
                            val tag = new NBTTagCompound
                            tag.setString("marker","client-backstuff")
                            returned.setTagCompound(tag)
                            importer.itemStorage.add(returned)
                            importer.exportBuffer()
                            assert(importer.itemStorage.isEmpty)
                            inventory.setInventorySlotContents(0,new ItemStack(Blocks.STONE,64))
                        }
                    })
                    phase = 2
                }
            case 2 =>
                sawPayload = false
                ticks = 0; phase = 3
            case 3 =>
                val moving = tubes.exists(_.getPneumaticTransport.getPayloads.nonEmpty)
                if moving && !sawPayload then { sawPayload = true; ticks = 0 }
                if sawPayload && !moving && ticks > 10 then {
                    val server = mc.getIntegratedServer
                    val id = mc.player.getUniqueID
                    action = server.addScheduledTask(new Runnable {
                        def run():Unit = {
                            val world = server.getPlayerList.getPlayerByUUID(id).getServerWorld
                            val importer = world.getTileEntity(start).asInstanceOf[TileItemImporter]
                            assert(!importer.itemStorage.isEmpty && importer.itemStorage.backlogged)
                            val returned = importer.itemStorage.peek.getItemStack
                            assert(returned.getItem == Items.IRON_INGOT && returned.getCount == 7 &&
                                returned.getTagCompound.getString("marker") == "client-backstuff",
                                "Blocked client delivery did not return intact to the importer")
                        }
                    })
                    phase = 4
                }
            case 4 =>
                println("PROJECTRED_PNEUMATIC_CLIENT_PASS: item flow, destination NBT and intact backstuff return through five tubes")
                return true
            case _ =>
        }
        false
    }
}
