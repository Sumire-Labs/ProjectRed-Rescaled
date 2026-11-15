package mrtjp.projectred.verification

import java.util.concurrent.atomic.AtomicReference
import mrtjp.projectred.ProjectRedExploration
import mrtjp.projectred.exploration.DecorativeStoneDefs
import net.minecraft.block.{BlockFenceGate,BlockHorizontal,BlockWall}
import net.minecraft.client.Minecraft
import net.minecraft.init.Blocks
import net.minecraft.util.{EnumFacing,ScreenShotHelper}
import net.minecraft.util.math.BlockPos

object DecorativeWallClientChecks {
    private val origin = new AtomicReference[BlockPos]()
    private var action:java.util.concurrent.Future[?] = null
    private var ticks = 0
    def start():Unit = {
        val mc = Minecraft.getMinecraft
        val server = mc.getIntegratedServer
        val id = mc.player.getUniqueID
        action = server.addScheduledTask(new Runnable {
            def run():Unit = {
                val player = server.getPlayerList.getPlayerByUUID(id)
                val world = player.getServerWorld
                val start = player.getPosition.add(-5,0,7)
                for (variant,i) <- DecorativeStoneDefs.values.zipWithIndex do {
                    val pos = start.add(i%3*4,0,i/3*3)
                    world.setBlockState(pos,Blocks.COBBLESTONE_WALL.getStateFromMeta(i%2),3)
                    world.setBlockState(pos.east(),ProjectRedExploration.blockDecorativeWall.getStateFromMeta(variant.meta),3)
                    world.setBlockState(pos.add(2,0,0),Blocks.OAK_FENCE_GATE.getDefaultState
                        .withProperty(BlockHorizontal.FACING,EnumFacing.NORTH),3)
                }
                player.connection.setPlayerLocation(start.getX+5.5,start.getY+2,start.getZ-8.5,0,15)
                origin.set(start)
            }
        })
    }
    def tick():Boolean = {
        ticks += 1
        assert(ticks < 200,"Decorative walls did not synchronize to the client")
        if !action.isDone then return false
        action.get()
        val mc = Minecraft.getMinecraft
        val start = origin.get()
        val wall = ProjectRedExploration.blockDecorativeWall
        val ready = DecorativeStoneDefs.values.zipWithIndex.forall { (variant,i) =>
            val pos = start.add(i%3*4,0,i/3*3)
            val state = mc.world.getBlockState(pos.east())
            val gate = mc.world.getBlockState(pos.add(2,0,0))
            mc.world.getBlockState(pos).getBlock == Blocks.COBBLESTONE_WALL &&
                state.getBlock == wall && wall.getMetaFromState(state) == variant.meta &&
                gate.getBlock == Blocks.OAK_FENCE_GATE && gate.getValue(BlockHorizontal.FACING) == EnumFacing.NORTH
        }
        if !ready then return false
        for (variant,i) <- DecorativeStoneDefs.values.zipWithIndex do {
            val pos = start.add(i%3*4,0,i/3*3)
            val state = mc.world.getBlockState(pos.east())
            val actual = state.getActualState(mc.world,pos.east())
            assert(actual.getValue(BlockWall.WEST).booleanValue && actual.getValue(BlockWall.EAST).booleanValue)
            assert(wall.getMetaFromState(state) == variant.meta)
            val gatePos = pos.add(2,0,0)
            val gate = mc.world.getBlockState(gatePos)
            assert(gate.getActualState(mc.world,gatePos).getValue(BlockFenceGate.IN_WALL).booleanValue)
        }
        if ticks < 30 then return false
        ScreenShotHelper.saveScreenshot(mc.gameDir,"projectred-wall-connections.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer)
        println("PROJECTRED_WALL_CLIENT_PASS: nine existing variants join vanilla walls and gates")
        true
    }
}
