package mrtjp.projectred.verification

import mrtjp.core.vec.{Point, Size}
import mrtjp.projectred.ProjectRedFabrication.*
import mrtjp.projectred.core.PartDefs
import mrtjp.projectred.fabrication.*
import mrtjp.projectred.fabrication.lithography.{ProcessNode, WaferType, YieldCalculator}
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.world.{World, WorldServer}

object FabricationProductionChecks {
    def blueprint():ItemStack = {
        val map = new ICTileMapContainer
        map.name = "Production regression"
        map.size = Size(16,16)
        def io(pos:Point,rotation:Int,kind:Int,shape:Int):Unit = {
            val gate = new IOGateICTile
            gate.preparePlacement(rotation,kind)
            gate.setShape(shape)
            gate.bindTileMap(map); gate.bindPos(pos)
            map.tiles += ((pos.x,pos.y) -> gate)
        }
        io(Point(8,0),0,ICGateDefinition.IOSimple.ordinal,0)
        io(Point(8,15),2,ICGateDefinition.IOBundled.ordinal,1)
        io(Point(0,8),3,ICGateDefinition.IOAnalog.ordinal,0)
        val stack = new ItemStack(itemICBlueprint)
        ItemICBlueprint.saveTileMap(map,stack)
        stack
    }

    def run(world:WorldServer):Unit = {
        val clock:World = world
        val time = clock.getTotalWorldTime
        val pos = new BlockPos(28,80,8)
        val design = blueprint()
        assert(ICData.canFabricate(design),"Valid production design was rejected")
        val invalid = design.copy()
        assert(ICData.canFabricate(invalid))
        invalid.getTagCompound.getCompoundTag("tilemap").setByte("sw",0.toByte)
        assert(!ICData.canFabricate(invalid),"Mutated design bypassed cached validation")
        val conflict = ItemICBlueprint.loadTileMap(design)
        val gate = new IOGateICTile
        gate.preparePlacement(0,ICGateDefinition.IOBundled.ordinal)
        gate.setShape(1)
        gate.bindTileMap(conflict); gate.bindPos(Point(7,0))
        conflict.tiles += ((7,0) -> gate)
        val bad = new ItemStack(itemICBlueprint)
        ItemICBlueprint.saveTileMap(conflict,bad)
        assert(!ICData.canFabricate(bad),"Unreported compile error passed production validation")

        def place(meta:Int):TileFabricationMachine = {
            world.setBlockToAir(pos)
            world.setBlockState(pos,fabricationMachineBlock.getStateFromMeta(meta),3)
            world.getTileEntity(pos).asInstanceOf[TileFabricationMachine]
        }
        def ticks(machine:TFabricationPowered,count:Int):Unit =
            for _ <- 0 until count do {
                clock.getWorldInfo.setWorldTotalTime(clock.getTotalWorldTime+1)
                machine.updateServer()
            }
        try {
            val plotting = place(0).asInstanceOf[TilePlottingTable]
            plotting.setInventorySlotContents(0,design)
            plotting.setInventorySlotContents(1,new ItemStack(itemBlankPhotomask))
            ticks(plotting,11)
            assert(plotting.isWorking && plotting.workRemaining == 200,"Unpowered plotting advanced")
            plotting.cond.Vloc = 200
            ticks(plotting,80)
            assert(plotting.workRemaining == 120)
            val save = new NBTTagCompound
            plotting.save(save)
            val resumed = new TilePlottingTable
            resumed.setWorld(world); resumed.setPos(pos)
            resumed.setInventorySlotContents(2,new ItemStack(net.minecraft.init.Items.PAPER))
            resumed.load(save)
            world.setTileEntity(pos,resumed)
            assert(resumed.workRemaining == 120 && resumed.getStackInSlot(2).isEmpty,"Plotting progress/inventory reload failed")
            ticks(resumed,120)
            val mask = resumed.getStackInSlot(2).copy()
            assert(mask.getItem == itemPhotomaskSet && ItemICBlueprint.hasICInside(mask))
            assert(resumed.getStackInSlot(0).getCount == 1 && resumed.getStackInSlot(1).isEmpty,
                "Plotting consumed the design or failed to consume its blank mask")
            assert(resumed.cond.voltage() < 200,"Plotting did not consume conductor energy")
            resumed.setInventorySlotContents(2,ItemStack.EMPTY)
            resumed.setInventorySlotContents(1,new ItemStack(itemBlankPhotomask))
            ticks(resumed,6)
            assert(resumed.workRemaining < 200)
            resumed.setInventorySlotContents(2,new ItemStack(net.minecraft.init.Items.PAPER))
            assert(!resumed.isWorking && resumed.getStackInSlot(1).getCount == 1,
                "Blocked output consumed ingredients or retained in-progress work")
            resumed.setInventorySlotContents(2,ItemStack.EMPTY)
            ticks(resumed,1)
            assert(resumed.workRemaining == 200,"Canceled plotting reused old progress")
            resumed.setInventorySlotContents(0,bad)
            assert(!resumed.isWorking && resumed.getStackInSlot(1).getCount == 1,
                "Invalid input replacement did not cancel plotting")
            val copy = new ItemStack(itemPhotomaskSet)
            ICData.copyDesign(mask,copy)
            copy.getTagCompound.getCompoundTag("tilemap").setString("name","independent")
            assert(ItemICBlueprint.getICName(mask) == "Production regression" &&
                mask.getTagCompound.getCompoundTag("tilemap").getString("name") == "Production regression",
                "Production stages shared mutable design NBT")

            val lithography = place(1).asInstanceOf[TileLithographyTable]
            lithography.setInventorySlotContents(0,mask)
            lithography.setInventorySlotContents(1,new ItemStack(itemRoughSiliconWafer))
            lithography.cond.Vloc = 200
            world.rand.setSeed(0L)
            ticks(lithography,1201)
            val valid = lithography.getStackInSlot(2).copy()
            val rejected = lithography.getStackInSlot(3)
            val count = new YieldCalculator(Size(16,16),ProcessNode.PROCESS_64NM,WaferType.ROUGH_WAFER).getCappedDieCount
            assert(valid.getCount+rejected.getCount == count && valid.getCount <= 64 && rejected.getCount <= 64,
                "Lithography lost dies or exceeded slot capacity")
            assert(!valid.isEmpty && lithography.getStackInSlot(0).getCount == 1 && lithography.getStackInSlot(1).isEmpty)
            assert(count == 16 && WaferType.ROUGH_WAFER.getWaferWidth == 4096,"Basic production differs from 1.21.1")
            assert(new YieldCalculator(Size(16,16),ProcessNode.PROCESS_64NM,WaferType.ROUGH_WAFER).getDefectChancePerDie == 0.5)

            val packaging = place(2).asInstanceOf[TilePackagingTable]
            valid.setCount(1)
            packaging.setInventorySlotContents(4,valid)
            for slot <- packaging.cornerSlots do packaging.setInventorySlotContents(slot,PartDefs.PLATE.makeStack)
            for r <- 0 until 4 do packaging.setInventorySlotContents(packaging.sideSlots(r),packaging.getPlateFor(ICData.getInterfaceType(valid,r)).makeStack)
            assert(packaging.canStartWork)
            packaging.setInventorySlotContents(packaging.sideSlots(2),PartDefs.PLATE.makeStack)
            assert(!packaging.canStartWork && (packaging.getProblematicSlotMask & (1<<packaging.sideSlots(2))) != 0,
                "Packaging accepted the wrong bundled interface plate")
            packaging.setInventorySlotContents(packaging.sideSlots(2),PartDefs.BUNDLEDPLATE.makeStack)
            packaging.cond.Vloc = 200
            ticks(packaging,401)
            val output = packaging.getStackInSlot(9)
            assert(output.getItem == mrtjp.projectred.ProjectRedIntegration.itemPartGate && ItemICBlueprint.hasICInside(output))
            assert((0 until 9).forall(i => packaging.getStackInSlot(i).isEmpty),"Packaging ingredient accounting changed")
            assert(ItemICBlueprint.loadTileMap(output).tiles.size == 3 && ItemICBlueprint.getICName(output) == "Production regression")
            val player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world)
            player.setPosition(pos.getX+0.5,pos.getY+0.5,pos.getZ+0.5)
            val container = packaging.createContainer(player)
            assert(container.canInteractWith(player))
            player.setPosition(pos.getX+20,pos.getY,pos.getZ)
            assert(!container.canInteractWith(player),"Production machine container allowed distant access")
        } finally {
            world.setBlockToAir(pos)
            clock.getWorldInfo.setWorldTotalTime(time)
        }
    }
}
