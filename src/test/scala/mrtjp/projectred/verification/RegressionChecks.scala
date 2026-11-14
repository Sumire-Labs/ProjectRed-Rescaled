package mrtjp.projectred.verification

import mrtjp.projectred.core.{WirePropagator, PropagationRun}
import mrtjp.projectred.expansion.{ItemStorage, TileProjectBench}
import mrtjp.projectred.ProjectRedExpansion
import codechicken.lib.data.MCDataInputWrapper
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.world.WorldServer
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.common.util.FakePlayerFactory
import com.mojang.authlib.GameProfile
import java.util.UUID
import io.netty.buffer.Unpooled

object RegressionChecks {
    def run(world:WorldServer):Unit = {
        for module <- Seq("core","transmission","integration","fabrication","illumination","transportation","expansion","relocation","exploration","compat") do
            assert(Loader.isModLoaded("projectred-"+module), "Missing module: "+module)

        val original = new ItemStorage
        val iron = new ItemStack(Items.IRON_INGOT, 7)
        val tag = new NBTTagCompound
        tag.setString("marker", "preserve-me")
        iron.setTagCompound(tag)
        original.add(iron)
        original.add(new ItemStack(Items.GOLD_INGOT, 11))
        val saved = new NBTTagCompound
        original.save(saved)
        val restored = new ItemStorage
        restored.load(saved)
        val first = restored.poll().payload.makeStack
        val second = restored.poll().payload.makeStack
        assert(first.getItem == Items.IRON_INGOT && first.getCount == 7 && first.getTagCompound == tag, "Payload NBT/FIFO changed")
        assert(second.getItem == Items.GOLD_INGOT && second.getCount == 11 && restored.isEmpty, "Payload count changed")
        restored.load(saved)
        restored.load(saved)
        restored.poll(); restored.poll()
        assert(restored.isEmpty, "Repeated load duplicated payloads")

        val bench = new TileProjectBench
        bench.setWorld(world)
        bench.setPos(new BlockPos(0,80,0))
        bench.setInventorySlotContents(9, iron.copy())
        val benchTag = new NBTTagCompound
        bench.save(benchTag)
        val benchCopy = new TileProjectBench
        benchCopy.setWorld(world)
        benchCopy.setPos(new BlockPos(0,80,0))
        benchCopy.load(benchTag)
        assert(ItemStack.areItemStacksEqual(benchCopy.getStackInSlot(9), iron), "Project Bench inventory round trip failed")

        val sentinel = new RuntimeException("injected neighbor failure")
        WirePropagator.reset()
        try WirePropagator.withoutWirePower { throw sentinel }
        catch { case e:RuntimeException => assert(e eq sentinel) }
        assert(WirePropagator.redwiresProvidePower, "Wire power flag remained disabled")
        try WirePropagator.withRedwiresConnectable(false) { throw sentinel }
        catch { case e:RuntimeException => assert(e eq sentinel) }
        assert(WirePropagator.redwiresConnectable, "Wire connection flag remained disabled")
        WirePropagator.withoutWirePower {
            WirePropagator.withoutWirePower { () }
            assert(!WirePropagator.redwiresProvidePower, "Nested signal calculation restored the wrong state")
        }
        val run = new PropagationRun
        run.world = world
        run.parent = new PropagationRun
        run.clear()
        assert(run.world == null && run.parent == null, "Pooled propagation retains a world")
        WirePropagator.reset()
        assert(WirePropagator.reusableRuns.empty(), "Server reset retained propagation runs")

        val player = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "PRSmokePlayer"))
        val barrel = new mrtjp.projectred.exploration.TileBarrel
        barrel.setWorld(world)
        barrel.setPos(new BlockPos(2,80,0))
        val barrelItems = new ItemStack(Items.IRON_INGOT, 5)
        assert(barrel.importStack(barrelItems) == 5)
        val wrapper = new mrtjp.projectred.exploration.BarrelInvWrapper(barrel)
        val ironKey = mrtjp.core.item.ItemKey.get(new ItemStack(Items.IRON_INGOT))
        wrapper.setHidePerType(true)
        assert(wrapper.getItemCount(ironKey) == 4)
        assert(wrapper.extractItem(ironKey, 100) == 4 && barrel.getStoredAmount == 1, "Barrel extracted its hidden item")
        assert(wrapper.getAllItemStacks.isEmpty, "Hidden barrel item was advertised")
        val wirePos = new BlockPos(4,80,0)
        world.setBlockState(wirePos.down(), net.minecraft.init.Blocks.REDSTONE_BLOCK.getDefaultState)
        val wire = new mrtjp.projectred.transmission.RedAlloyWirePart
        wire.preparePlacement(1,0)
        codechicken.multipart.TileMultipart.addPart(world, wirePos, wire)
        assert((wire.signal & 0xFF) > 0, "Placed wire did not acquire vanilla power")
        val wireTag = new NBTTagCompound
        wire.save(wireTag)
        val wireCopy = new mrtjp.projectred.transmission.RedAlloyWirePart
        wireCopy.load(wireTag)
        assert(wireCopy.signal == wire.signal && wireCopy.side == wire.side, "Wire save format changed")
        world.setBlockToAir(wirePos)
        world.setBlockToAir(wirePos.down())
        val pos = new BlockPos(0, 80, 0)
        world.setBlockState(pos, ProjectRedExpansion.machine2.getDefaultState, 3)
        world.setTileEntity(pos, bench)
        player.setPosition(0.5,80.5,0.5)
        player.openContainer = new mrtjp.projectred.expansion.ContainerProjectBench(player, bench)
        assert(mrtjp.projectred.core.TileActions.hasOpenInventory(bench, player), "Open bench was not authorized")
        def playerIdInput(id:Int):MCDataInputWrapper =
            new MCDataInputWrapper(new java.io.ByteArrayInputStream(java.nio.ByteBuffer.allocate(4).putInt(id).array()))
        bench.setInventorySlotContents(0, new ItemStack(Items.IRON_INGOT, 3))
        bench.readFromClient(playerIdInput(player.getEntityId+1000), 2, player)
        assert(bench.getStackInSlot(0).getCount == 3, "Another player's grid action was accepted")
        bench.readFromClient(playerIdInput(player.getEntityId), 2, player)
        assert(bench.getStackInSlot(0).isEmpty, "Authorized Project Bench action was ignored")
        player.openContainer = player.inventoryContainer
        assert(!mrtjp.projectred.core.TileActions.hasOpenInventory(bench, player), "Closed bench was authorized")
        player.setPosition(100,80,100)
        assert(!mrtjp.projectred.core.TileActions.mayEdit(bench, player), "Distant tile action was authorized")
        world.setBlockToAir(pos)
    }
}
