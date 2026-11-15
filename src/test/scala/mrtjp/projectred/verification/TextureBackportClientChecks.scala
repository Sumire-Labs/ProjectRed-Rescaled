package mrtjp.projectred.verification

import java.util.concurrent.atomic.AtomicReference
import mrtjp.projectred.{ProjectRedExpansion,ProjectRedExploration}
import mrtjp.projectred.expansion.{TileMachine,TileBlockPlacer,TileChargingBench}
import net.minecraft.client.Minecraft
import net.minecraft.init.Blocks
import net.minecraft.item.{Item,ItemStack}
import net.minecraft.util.ScreenShotHelper
import net.minecraft.util.math.BlockPos

object TextureBackportClientChecks {
    private var action:java.util.concurrent.Future[?] = null
    private var phase = 0
    private var ticks = 0
    private var armorIndex = 0
    private var previousView = 0
    private var previousArmor = Array.empty[ItemStack]
    private val origin = new AtomicReference[BlockPos]()
    private def outfits:Seq[Seq[Item]] = {
        import ProjectRedExploration.*
        Seq(Seq(itemRubyBoots,itemRubyLeggings,itemRubyChestplate,itemRubyHelmet),
            Seq(itemSapphireBoots,itemSapphireLeggings,itemSapphireChestplate,itemSapphireHelmet),
            Seq(itemPeridotBoots,itemPeridotLeggings,itemPeridotChestplate,itemPeridotHelmet))
    }
    def start():Unit = {
        val mc = Minecraft.getMinecraft
        previousView = mc.gameSettings.thirdPersonView
        mc.gameSettings.thirdPersonView = 0
        val server = mc.getIntegratedServer
        val id = mc.player.getUniqueID
        action = server.addScheduledTask(new Runnable {
            def run():Unit = {
                val player = server.getPlayerList.getPlayerByUUID(id)
                previousArmor = Array.tabulate(4)(i => player.inventory.armorInventory.get(i).copy())
                val world = player.getServerWorld
                world.getWorldInfo.setWorldTime(6000L)
                val start = new BlockPos(player.getPosition.getX+24,4,player.getPosition.getZ+8)
                for (meta,row) <- Seq(2,6,11).zipWithIndex; column <- 0 until 2 do {
                    val pos = start.add((row*2+column)*2,0,0)
                    world.setBlockState(pos,ProjectRedExpansion.machine2.getStateFromMeta(meta),3)
                    val tile = world.getTileEntity(pos).asInstanceOf[TileMachine]
                    tile match {
                        case placer:TileBlockPlacer =>
                            placer.setSide(3)
                            if column == 1 then world.setBlockState(pos.down(),Blocks.REDSTONE_BLOCK.getDefaultState,3)
                            placer.onNeighborBlockChange()
                        case charger:TileChargingBench =>
                            if column == 1 then charger.cond.Vloc = 200
                        case _ =>
                    }
                    tile.markDescUpdate()
                }
                player.connection.setPlayerLocation(start.getX+5.5,start.getY+2,start.getZ-11.5,0,15)
                origin.set(start)
            }
        })
    }
    private def equip():Unit = {
        val mc = Minecraft.getMinecraft
        mc.gameSettings.thirdPersonView = 2
        val server = mc.getIntegratedServer
        val id = mc.player.getUniqueID
        val items = outfits(armorIndex)
        action = server.addScheduledTask(new Runnable {
            def run():Unit = {
                val player = server.getPlayerList.getPlayerByUUID(id)
                player.connection.setPlayerLocation(player.posX,4,player.posZ,0,0)
                for i <- 0 until 4 do player.inventory.armorInventory.set(i,new ItemStack(items(i)))
                player.inventory.markDirty()
                player.inventoryContainer.detectAndSendChanges()
            }
        })
        ticks = 0
    }
    def tick():Boolean = {
        ticks += 1
        assert(ticks < 200,s"Texture preview timed out in phase $phase")
        if !action.isDone then return false
        action.get()
        val mc = Minecraft.getMinecraft
        phase match {
            case 0 =>
                val start = origin.get()
                if !(for row <- 0 until 3; column <- 0 until 2 yield
                    mc.world.getTileEntity(start.add((row*2+column)*2,0,0)).isInstanceOf[TileMachine]).forall(identity) then return false
                mc.world.getTileEntity(start.add(2,0,0)) match {
                    case placer:TileBlockPlacer if placer.active =>
                    case _ => return false
                }
                mc.world.getTileEntity(start.add(6,0,0)) match {
                    case charger:TileChargingBench if charger.isCharged =>
                    case _ => return false
                }
                if ticks < 30 then return false
                ScreenShotHelper.saveScreenshot(mc.gameDir,"projectred-texture-machines.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer)
                equip(); phase = 1
            case 1 =>
                if !(0 until 4).forall(i => mc.player.inventory.armorInventory.get(i).getItem == outfits(armorIndex)(i)) then return false
                if ticks < 20 then return false
                ScreenShotHelper.saveScreenshot(mc.gameDir,s"projectred-texture-armor-$armorIndex.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer)
                armorIndex += 1
                if armorIndex < 3 then equip()
                else {
                    mc.gameSettings.thirdPersonView = previousView
                    val server = mc.getIntegratedServer
                    val id = mc.player.getUniqueID
                    action = server.addScheduledTask(new Runnable {
                        def run():Unit = {
                            val player = server.getPlayerList.getPlayerByUUID(id)
                            for i <- 0 until 4 do player.inventory.armorInventory.set(i,previousArmor(i))
                            player.inventoryContainer.detectAndSendChanges()
                        }
                    })
                    phase = 2
                }
            case 2 =>
                println("PROJECTRED_TEXTURE_CLIENT_PASS: machine appearance states and three equipped gem armor sets rendered")
                return true
            case _ =>
        }
        false
    }
}
