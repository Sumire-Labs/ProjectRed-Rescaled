package mrtjp.projectred.verification

import codechicken.lib.packet.PacketCustom
import mrtjp.projectred.ProjectRedExpansion
import mrtjp.projectred.expansion.{ItemPlan,TileAutoCrafter}
import net.minecraft.init.Blocks
import net.minecraft.item.{Item,ItemStack}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.world.{World,WorldServer}

object AutoCrafterVisualChecks {
    def plan():ItemStack = {
        val inputs = Array.fill(9)(ItemStack.EMPTY)
        inputs(0) = new ItemStack(Blocks.LOG)
        val stack = new ItemStack(ProjectRedExpansion.itemPlan)
        ItemPlan.savePlan(stack,inputs,new ItemStack(Blocks.PLANKS,4))
        stack
    }
    def run(world:WorldServer):Unit = {
        val clock:World = world
        val originalTime = clock.getTotalWorldTime
        val pos = new BlockPos(36,80,24)
        world.setBlockState(pos,ProjectRedExpansion.machine2.getStateFromMeta(11),3)
        val tile = world.getTileEntity(pos).asInstanceOf[TileAutoCrafter]
        def ticks(n:Int):Unit = for _ <- 0 until n do {
            clock.getWorldInfo.setWorldTotalTime(clock.getTotalWorldTime+1)
            tile.updateServer()
        }
        try {
            ticks(1)
            assert(tile.visualState == 0)
            tile.cond.Vloc = 200
            ticks(1)
            assert(tile.visualState == 1,"Charged idle crafter did not select the idle texture")
            tile.setInventorySlotContents(0,plan())
            tile.setInventorySlotContents(9,new ItemStack(Blocks.LOG))
            ticks(1)
            assert(tile.visualState == 2 && tile.workRemaining == 99,"Crafter did not start a 100-tick job")
            assert((9 until 27).map(tile.getStackInSlot).filter(_.getItem == Item.getItemFromBlock(Blocks.PLANKS)).isEmpty,
                "Crafter produced output before five seconds of work")
            ticks(98)
            assert(tile.workRemaining == 1)
            ticks(1)
            assert((9 until 27).map(tile.getStackInSlot).filter(_.getItem == Item.getItemFromBlock(Blocks.PLANKS)).map(_.getCount).sum == 4,
                "Visual state changed crafting output")
            val packet = new PacketCustom("PRVerification",1)
            tile.setInventorySlotContents(10,new ItemStack(Blocks.LOG))
            ticks(1)
            tile.writeDesc(packet)
            val incoming = new PacketCustom(packet.copy())
            val remote = new TileAutoCrafter
            remote.readDesc(incoming)
            assert(remote.visualState == 2,"Chunk description omitted the working state")
            packet.release(); incoming.release()
            tile.cond.Vloc = 0; tile.cond.Vflow = 0; tile.cond.Iflow = 0
            val paused = tile.workRemaining
            ticks(50)
            assert(tile.visualState == 0 && tile.workRemaining == paused,"Unpowered crafter advanced its job")
            val tag = new NBTTagCompound
            tile.save(tag)
            val loaded = new TileAutoCrafter
            loaded.load(tag)
            assert(loaded.getStackInSlot(0).getItem == ProjectRedExpansion.itemPlan)
            assert(loaded.workRemaining == paused && loaded.isWorking,"In-progress crafting was not restored")
            assert((9 until 27).map(loaded.getStackInSlot).map(_.getCount).sum == 5)

            tile.cond.Vloc = 200
            tile.setInventorySlotContents(1,plan())
            for i <- 9 until 27 do tile.setInventorySlotContents(i,new ItemStack(Blocks.STONE,64))
            tile.setInventorySlotContents(9,new ItemStack(Blocks.LOG,64))
            ticks(1)
            assert(!tile.isWorking && tile.workRemaining == 0 && tile.getStackInSlot(9).getCount == 64,
                "Blocked output did not cancel work without consuming ingredients")
            ticks(5)
            assert(tile.planSlot == 1,"Idle crafter did not cycle after ten unsuccessful work checks")
            tile.setInventorySlotContents(10,ItemStack.EMPTY)
            ticks(1)
            assert(tile.isWorking && tile.workRemaining == 99,"Unblocked crafter reused canceled progress")
            tile.setInventorySlotContents(1,ItemStack.EMPTY)
            ticks(1)
            assert(!tile.isWorking && tile.workRemaining == 0,"Removing a plan retained in-progress work")

            val legacy = tag.copy()
            Seq("remaining_work","total_work","working","charging","plan_slot").foreach(legacy.removeTag)
            legacy.setByte("cyt1",3.toByte); legacy.setByte("cyt2",1.toByte)
            val converted = new TileAutoCrafter
            converted.load(legacy)
            assert(!converted.isWorking && converted.workRemaining == 0 && converted.planSlot == 0)
            assert((9 until 27).map(converted.getStackInSlot).map(_.getCount).sum == 5,
                "Legacy cycle-timer migration discarded inventory")
        } finally {
            world.setBlockToAir(pos)
            clock.getWorldInfo.setWorldTotalTime(originalTime)
        }
    }
}
