package mrtjp.projectred.verification

import mrtjp.projectred.integration.{SequentialGatePart, GateDefinition, Synchronizer}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.world.{World,WorldServer}

object SynchronizerChecks {
    def run(world:WorldServer):Unit = {
        val clock:World = world
        val originalTime = clock.getTotalWorldTime
        val pos = new BlockPos(8,80,12)
        class Probe extends SequentialGatePart {
            var inputs = 0
            var feedbackOnFall = false
            override def getRedstoneInput(r:Int):Int = if (inputs&(1<<r)) != 0 then 255 else 0
            override def onOutputChange(mask:Int):Unit = {
                super.onOutputChange(mask)
                if feedbackOnFall && (state&0x10) == 0 then {
                    feedbackOnFall = false
                    inputs = 10
                    getLogic[Synchronizer].onChange(this)
                }
            }
        }
        val tag = new NBTTagCompound
        tag.setByte("subID",GateDefinition.Synchronizer.ordinal.toByte)
        tag.setLong("schedTime",-1L)
        val gate = new Probe
        gate.load(tag)
        world.setBlockState(pos.down(),net.minecraft.init.Blocks.STONE.getDefaultState)
        try {
            codechicken.multipart.TileMultipart.addPart(world,pos,gate)
            val logic = gate.getLogic[Synchronizer]
            gate.inputs = 10
            logic.onChange(gate)
            assert(gate.schedTime == originalTime+2, "Synchronizer start delay changed")
            clock.getWorldInfo.setWorldTotalTime(originalTime+2)
            gate.update()
            assert((gate.state&0x10) != 0 && logic.pulsing)
            clock.getWorldInfo.setWorldTotalTime(originalTime+3)
            gate.inputs = 4
            logic.onChange(gate)
            clock.getWorldInfo.setWorldTotalTime(originalTime+4)
            gate.update()
            assert((gate.state&0x10) == 0 && !logic.pulsing, "Reset during pulse left synchronizer stuck high")

            gate.inputs = 0
            logic.onChange(gate)
            gate.inputs = 10
            logic.onChange(gate)
            clock.getWorldInfo.setWorldTotalTime(originalTime+6)
            gate.update()
            gate.inputs = 0
            logic.onChange(gate)
            gate.feedbackOnFall = true
            clock.getWorldInfo.setWorldTotalTime(originalTime+8)
            gate.update()
            assert(logic.left && logic.right && !logic.pulsing, "Falling-edge feedback latch was overwritten")
            clock.getWorldInfo.setWorldTotalTime(originalTime+10)
            gate.update()
            assert((gate.state&0x10) != 0 && logic.pulsing, "Synchronizer lost the feedback-triggered pulse")
        } finally {
            world.setBlockToAir(pos)
            world.setBlockToAir(pos.down())
            clock.getWorldInfo.setWorldTotalTime(originalTime)
        }
    }
}
