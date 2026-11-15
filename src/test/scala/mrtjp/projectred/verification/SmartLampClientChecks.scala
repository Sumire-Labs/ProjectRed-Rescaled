package mrtjp.projectred.verification

import java.util.concurrent.atomic.AtomicReference
import mrtjp.projectred.ProjectRedIllumination
import mrtjp.projectred.illumination.TileSmartLamp
import net.minecraft.client.Minecraft
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ScreenShotHelper
import net.minecraft.util.math.BlockPos

object SmartLampClientChecks {
    private val origin = new AtomicReference[BlockPos]()
    private var action:java.util.concurrent.Future[?] = null
    private var ticks = 0
    def start():Unit = {
        val mc = Minecraft.getMinecraft
        val server = mc.getIntegratedServer
        val id = mc.player.getUniqueID
        mc.player.closeScreen()
        action = server.addScheduledTask(new Runnable {
            def run():Unit = {
                val player = server.getPlayerList.getPlayerByUUID(id)
                val world = player.getServerWorld
                val start = player.getPosition.add(-5,0,4)
                for i <- 0 until 6 do {
                    val pos = start.add(i*2,0,0)
                    world.setBlockState(pos,ProjectRedIllumination.blockSmartLamp.getDefaultState,3)
                    val tile = world.getTileEntity(pos).asInstanceOf[TileSmartLamp]
                    val signal = new Array[Byte](16)
                    if i != 5 then signal(14) = 255.toByte
                    if i%2 == 1 || i == 2 then signal(11) = 255.toByte
                    val tag = new NBTTagCompound
                    tag.setByte("orientation",(i<<2).toByte)
                    tag.setByteArray("signal",signal)
                    tile.load(tag)
                    tile.markDescUpdate(); tile.markLight()
                }
                player.connection.setPlayerLocation(start.getX+5.5,start.getY+1,start.getZ-8.5,0,15)
                origin.set(start)
            }
        })
    }
    def tick():Boolean = {
        ticks += 1
        assert(ticks < 200,"Placed Smart Lamps did not synchronize")
        if !action.isDone then return false
        action.get()
        val mc = Minecraft.getMinecraft
        val start = origin.get()
        if !(0 until 6).forall(i => mc.world.getTileEntity(start.add(i*2,0,0)) match {
            case tile:TileSmartLamp => tile.side == i && tile.signal(14) == (if i == 5 then 0 else 255.toByte)
            case _ => false
        }) then return false
        if ticks < 30 then return false
        ScreenShotHelper.saveScreenshot(mc.gameDir,"projectred-smart-lamps.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer)
        println("PROJECTRED_SMART_LAMP_CLIENT_PASS: six orientations and mixed-colour world halos rendered")
        true
    }
}
