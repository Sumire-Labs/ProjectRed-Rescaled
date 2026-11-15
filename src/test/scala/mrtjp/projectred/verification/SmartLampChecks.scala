package mrtjp.projectred.verification

import codechicken.lib.colour.EnumColour
import codechicken.lib.packet.PacketCustom
import mrtjp.projectred.ProjectRedIllumination
import mrtjp.projectred.api.IBundledTile
import mrtjp.projectred.core.BundledLightColour
import mrtjp.projectred.illumination.TileSmartLamp
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldServer

object SmartLampChecks {
    class Emitter extends TileEntity with IBundledTile {
        val signal = new Array[Byte](16)
        def canConnectBundled(side:Int):Boolean = true
        def getBundledSignal(side:Int):Array[Byte] = signal
    }
    def run(world:WorldServer):Unit = {
        val pos = new BlockPos(24,80,8)
        val player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world)
        val stack = new net.minecraft.item.ItemStack(ProjectRedIllumination.itemBlockSmartLamp)
        val signal = new Array[Byte](16)
        assert(BundledLightColour.blend(signal) == 0)
        for i <- 0 until 16 do {
            java.util.Arrays.fill(signal,0.toByte)
            signal(i) = 255.toByte
            assert(BundledLightColour.blend(signal) == EnumColour.values()(i).rgba,"Single-channel lamp colour changed")
        }
        try {
            for bottom <- 0 until 6 do {
                world.setBlockState(pos,ProjectRedIllumination.blockSmartLamp.getDefaultState,3)
                val lamp = world.getTileEntity(pos).asInstanceOf[TileSmartLamp]
                val sourcePos = pos.offset(EnumFacing.VALUES(bottom))
                val forbiddenPos = pos.offset(EnumFacing.VALUES(bottom^1))
                world.setBlockState(sourcePos,net.minecraft.init.Blocks.CHEST.getDefaultState,3)
                world.setBlockState(forbiddenPos,net.minecraft.init.Blocks.CHEST.getDefaultState,3)
                val source = new Emitter
                val forbidden = new Emitter
                source.signal(14) = 255.toByte
                forbidden.signal(11) = 255.toByte
                world.setTileEntity(sourcePos,source)
                world.setTileEntity(forbiddenPos,forbidden)
                lamp.onBlockPlaced(bottom^1,player,stack)
                assert(lamp.side == bottom && lamp.signal(14) == 255.toByte && lamp.signal(11) == 0,
                    s"Smart Lamp input: bottom=$bottom side=${lamp.side} red=${lamp.signal(14)&255} blue=${lamp.signal(11)&255} map=${lamp.connMap} source=${world.getTileEntity(sourcePos)}")
                assert(lamp.getLightValue == 15 && lamp.isOn)
                assert(lamp.getConnectionMask(bottom) == 31 && !lamp.canConnectBundled(bottom^1))
                assert(!lamp.maskConnectsStraightCenter(bottom^1),"Smart Lamp connected through its top face")
                val firstColour = lamp.getRgbColour
                val chunk = world.getChunk(pos)
                chunk.setModified(false)
                source.signal(14) = 0
                source.signal(11) = 255.toByte
                lamp.onNeighborBlockChange()
                assert(lamp.getLightValue == 15 && lamp.getRgbColour != firstColour,
                    "Equal-brightness colour change was lost")
                assert(chunk.needsSaving(false),"Colour-only update did not persist")
                source.signal(14) = 128.toByte
                lamp.onNeighborBlockChange()
                val tag = new NBTTagCompound
                lamp.save(tag)
                val restored = new TileSmartLamp
                restored.load(tag)
                assert(restored.side == bottom && restored.signal.sameElements(lamp.signal) && restored.getRgbColour == lamp.getRgbColour)
                val outgoing = new PacketCustom("PRVerification",1)
                lamp.writeDesc(outgoing)
                val incoming = new PacketCustom(outgoing.copy())
                val remote = new TileSmartLamp
                remote.setWorld(world); remote.setPos(pos)
                remote.readDesc(incoming)
                assert(remote.signal.sameElements(lamp.signal) && remote.side == bottom)
                incoming.release(); outgoing.release()
                java.util.Arrays.fill(source.signal,0.toByte)
                lamp.onNeighborBlockChange()
                assert(!lamp.isOn && lamp.getLightValue == 0 && lamp.getRgbColour == 0)
                world.setBlockToAir(pos)
                world.setBlockToAir(sourcePos)
                world.setBlockToAir(forbiddenPos)
            }
        } finally {
            world.setBlockToAir(pos)
            EnumFacing.VALUES.foreach(side => world.setBlockToAir(pos.offset(side)))
        }
    }
}
