package mrtjp.projectred.verification

import mrtjp.projectred.integration.{ArrayGatePart, GateDefinition}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.world.{World,WorldServer}

object ArrayCellPulseChecks {
    def run(world:WorldServer):Unit = {
        val clock:World = world
        val originalTime = clock.getTotalWorldTime
        val pos = new BlockPos(12,80,8)
        class Probe extends ArrayGatePart {
            var control = 255
            override def getRedstoneInput(r:Int):Int = if r == 2 then control else 0
        }
        val tag = new NBTTagCompound
        tag.setByte("subID",GateDefinition.ANDCell.ordinal.toByte)
        tag.setLong("schedTime",-1L)
        val gate = new Probe
        gate.load(tag)
        world.setBlockState(pos.down(),net.minecraft.init.Blocks.STONE.getDefaultState)
        try {
            codechicken.multipart.TileMultipart.addPart(world,pos,gate)
            gate.getLogicArray.setSignal(10,255)
            gate.onSignalUpdate()
            assert(gate.schedTime == originalTime+2)
            clock.getWorldInfo.setWorldTotalTime(originalTime+2)
            // Propagation updates raw wire strength before delivering the notification.
            // Pending gate work must use the sampled input from the preceding change.
            gate.getLogicArray.setSignal(10,0)
            gate.onSignalUpdate()
            assert((gate.state&0x10) != 0, "AND cell lost a pulse ending on its pending output tick")
            clock.getWorldInfo.setWorldTotalTime(originalTime+4)
            gate.update()
            assert((gate.state&0x10) == 0, "AND cell pulse did not terminate")
            world.setBlockToAir(pos)

            val latchTag = new NBTTagCompound
            latchTag.setByte("subID",GateDefinition.StackingLatch.ordinal.toByte)
            latchTag.setLong("schedTime",-1L)
            val latch = new Probe
            latch.load(latchTag)
            codechicken.multipart.TileMultipart.addPart(world,pos,latch)
            latch.getLogicArray.setSignal(10,255)
            latch.onSignalUpdate()
            clock.getWorldInfo.setWorldTotalTime(originalTime+6)
            latch.getLogicArray.setSignal(10,0)
            latch.onSignalUpdate()
            assert((latch.state&0x10) != 0, "Stacking Latch lost its sampled enable pulse")
            latch.control = 0
            latch.onChange()
            clock.getWorldInfo.setWorldTotalTime(originalTime+8)
            latch.update()
            assert((latch.state&0x10) != 0, "Stacking Latch changed while disabled")
            latch.getLogicArray.setSignal(10,255)
            latch.onSignalUpdate()
            clock.getWorldInfo.setWorldTotalTime(originalTime+10)
            latch.getLogicArray.setSignal(10,0)
            latch.onSignalUpdate()
            assert((latch.state&0x10) == 0, "Stacking Latch failed to sample the low control input")

            val legacy = new NBTTagCompound
            legacy.setByte("subID",GateDefinition.ANDCell.ordinal.toByte)
            legacy.setByte("state",4.toByte)
            legacy.setByte("signal",255.toByte)
            val oldGate = new ArrayGatePart
            oldGate.load(legacy)
            assert((oldGate.state&15) == 14, "Legacy array-gate input snapshot was not initialized")
            val saved = new NBTTagCompound
            oldGate.save(saved)
            assert(saved.getBoolean("arrayInputSnapshot"))
            saved.setByte("signal",0.toByte)
            val restored = new ArrayGatePart
            restored.load(saved)
            assert((restored.state&15) == 14, "New array-gate snapshot was overwritten from raw signal during load")
        } finally {
            world.setBlockToAir(pos)
            world.setBlockToAir(pos.down())
            clock.getWorldInfo.setWorldTotalTime(originalTime)
        }
    }
}
