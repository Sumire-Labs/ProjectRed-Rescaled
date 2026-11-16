package mrtjp.projectred.verification

import codechicken.multipart.{MultiPartRegistry, TileMultipart}
import mrtjp.projectred.{ProjectRedExpansion, ProjectRedFabrication, ProjectRedTransportation}
import mrtjp.projectred.core.{LegacySaveMigration, PartDefs}
import mrtjp.projectred.fabrication.*
import mrtjp.projectred.transportation.{LegacyTransportMigration, PipeDefs}
import mrtjp.projectred.transportation.pneumatics.part.PneumaticTubePart
import net.minecraft.init.{Blocks, Items}
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{NBTTagCompound, NBTTagList}
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.util.{EnumHand, ResourceLocation}
import net.minecraft.util.datafix.FixTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldServer
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.fml.common.FMLCommonHandler
import scala.jdk.CollectionConverters.*

object LegacyContentMigrationChecks {
    private def saved(stack:ItemStack) = stack.writeToNBT(new NBTTagCompound)
    private def count(player:net.minecraft.entity.player.EntityPlayer,item:net.minecraft.item.Item):Int =
        (0 until player.inventory.getSizeInventory).map(player.inventory.getStackInSlot).filter(_.getItem == item).map(_.getCount).sum

    def run(world:WorldServer):Unit = {
        itemAndFixerChecks()
        tubeChecks(world)
        printerChecks(world)
        deviceChecks(world)
        importerPolicyChecks(world)
        println("PROJECTRED_LEGACY_CONTENT_PASS: nested save fixes, transport aliases/payloads/recovery and Printer-to-Packaging migration")
    }

    private def itemAndFixerChecks():Unit = {
        val old = saved(PipeDefs.RESISTANCETUBE.makeStack)
        val opaque = new NBTTagCompound
        opaque.setString("opaque","retained")
        old.setTag("tag",opaque)
        val before = old.copy()
        val custom = new NBTTagCompound
        val list = new NBTTagList
        list.appendTag(old)
        custom.setTag("foreign_capability_inventory",list)
        val fixed = FMLCommonHandler.instance().getDataFixer.process(FixTypes.CHUNK,custom)
        assert(old.getInteger("Damage") == 66,"Forge's actual mod data fixer did not visit a nested inventory")
        assert(LegacySaveMigration.original(old.getCompoundTag("tag").getCompoundTag(LegacySaveMigration.Key)) == before)
        val once = fixed.copy()
        LegacySaveMigration.migrate(fixed)
        assert(fixed == once,"Repeated save conversion changed already migrated items")

        val many = PipeDefs.PRESSURETUBE.makeStack
        many.setCount(1000)
        assert(LegacySaveMigration.item(many).getCount == 1000,"Live inventory migration truncated an oversized count")

        val blueprint = FabricationProductionChecks.blueprint()
        val chip = new ItemStack(ProjectRedFabrication.itemICChip)
        ItemICBlueprint.copyIC(blueprint,chip)
        val die = LegacySaveMigration.item(chip)
        assert(die.getItem == ProjectRedFabrication.itemValidDie && ICData.canFabricate(die))
        assert(ItemICBlueprint.getICName(die) == ItemICBlueprint.getICName(blueprint))
        die.getTagCompound.setString("icname","edited migrated result")
        val second = LegacySaveMigration.item(chip)
        assert(ItemICBlueprint.getICName(second) == ItemICBlueprint.getICName(blueprint) && ICData.canFabricate(second),
            "Compiled migration cache shared mutable design data")
        val creative = chip.copy()
        creative.setItemDamage(1)
        assert(LegacySaveMigration.item(creative).getItem == ProjectRedFabrication.itemICBlueprint,
            "Creative old chip became a free die")
        val blank = LegacySaveMigration.item(new ItemStack(ProjectRedFabrication.itemICChip))
        assert(blank.getItem == ProjectRedFabrication.itemRoughSiliconWafer)
        val invalid = chip.copy()
        invalid.setTagCompound(new NBTTagCompound)
        invalid.getTagCompound.setTag("tilemap",new NBTTagCompound)
        assert(LegacySaveMigration.item(invalid).getItem == ProjectRedFabrication.itemICBlueprint,
            "Unbuildable legacy circuitry was accepted as a valid die")

        val queue = new NBTTagCompound
        val records = new NBTTagList
        val missing = new NBTTagCompound
        val unknownItem = new NBTTagCompound
        unknownItem.setString("id","missing_mod:unavailable_item")
        unknownItem.setByte("Count",7.toByte)
        missing.setTag("Item",unknownItem)
        missing.setString("opaque","retain failed records")
        records.appendTag(missing)
        queue.setTag("itemFlow",records)
        val storage = new mrtjp.projectred.expansion.ItemStorage
        storage.load(queue)
        assert(storage.hasUnreadable && !storage.isEmpty)
        val rewritten = new NBTTagCompound
        storage.save(rewritten)
        assert(rewritten.getTagList("itemFlow",10).getCompoundTagAt(0) == missing,
            "An unreadable queue record was silently discarded")
    }

    private def oldPart(kind:String, payload:Boolean):NBTTagCompound = {
        val tag = new NBTTagCompound
        tag.setString("id",kind)
        tag.setByte("meta",64.toByte)
        tag.setInteger("connMap",0)
        tag.setString("rid","50dd56ec-6684-41a5-8896-c5a668c290c9")
        tag.setString("opaque_setting","keep this")
        val flow = new NBTTagList
        if payload then {
            val entry = new NBTTagCompound
            val stack = new ItemStack(Items.GOLD_INGOT,7)
            stack.setStackDisplayName("in-flight payload")
            entry.setTag("Item",saved(stack))
            entry.setInteger("idata",(37<<16)|(5<<8)|64|5|(5<<3))
            flow.appendTag(entry)
        }
        tag.setTag("itemFlow",flow)
        tag
    }

    private def load(tag:NBTTagCompound):PneumaticTubePart = {
        val part = MultiPartRegistry.loadPart(new ResourceLocation(tag.getString("id")),tag)
        assert(part.isInstanceOf[PneumaticTubePart],"Old transport factory still creates a legacy network")
        part.load(tag)
        part.asInstanceOf[PneumaticTubePart]
    }

    private def tubeChecks(world:WorldServer):Unit = {
        val mixed = new NBTTagCompound
        val entries = new NBTTagList
        entries.appendTag(oldPart("projectred-transporation:pressure_tube",true))
        val wire = new mrtjp.projectred.transmission.RedAlloyWirePart
        wire.preparePlacement(1,0)
        val wireTag = new NBTTagCompound
        wire.save(wireTag)
        wireTag.setString("id",wire.getType.toString)
        entries.appendTag(wireTag)
        mixed.setTag("parts",entries)
        LegacySaveMigration.migrate(mixed)
        val composite = TileMultipart.createFromNBT(mixed)
        assert(composite.partList.size == 2 && composite.partList.exists(_.isInstanceOf[PneumaticTubePart]) &&
            composite.partList.exists(_.isInstanceOf[mrtjp.projectred.transmission.RedAlloyWirePart]),
            "Multipart save conversion lost another part sharing the block")
        for kind <- LegacyTransportMigration.oldParts do {
            val tag = oldPart(kind,true)
            val before = tag.copy()
            val part = load(tag)
            assert(part.getType.toString == LegacyTransportMigration.ModernPart && part.getLinkWeight == 1)
            val payload = part.getPneumaticTransport.getPayloads.head
            assert(payload.getItemStack.getCount == 7 && payload.getSpeed == 12 && payload.getProgress == 37*255/100)
            val savedPart = new NBTTagCompound
            part.save(savedPart)
            assert(LegacySaveMigration.original(savedPart.getCompoundTag(LegacySaveMigration.Key)) == before)
            part.load(savedPart)
            part.load(savedPart)
            assert(part.getPneumaticTransport.getPayloads.size == 1,"Repeated part load duplicated cargo")
        }

        val start = new BlockPos(8,82,40)
        val tubes = (0 until 3).map { offset =>
            val part = load(oldPart("projectred-transporation:pressure_tube",offset == 0))
            TileMultipart.addPart(world,start.add(offset,0,0),part)
            part
        }
        val chestPos = start.add(3,0,0)
        world.setBlockState(chestPos,Blocks.CHEST.getDefaultState,3)
        try {
            tubes.foreach(_.onNeighborChanged())
            for _ <- 0 until 160 do tubes.foreach(_.update())
            val chest = world.getTileEntity(chestPos).asInstanceOf[TileEntityChest]
            val cargo = (0 until chest.getSizeInventory).map(chest.getStackInSlot).filter(_.getItem == Items.GOLD_INGOT)
            assert(cargo.map(_.getCount).sum == 7 && cargo.forall(_.getDisplayName == "in-flight payload"))
            assert(tubes.forall(_.getPneumaticTransport.getPayloads.isEmpty),"Migrated cargo remained in flight after delivery")

            val player = FakePlayerFactory.getMinecraft(world)
            val previous = Array.tabulate(player.inventory.getSizeInventory)(i=>player.inventory.getStackInSlot(i).copy())
            try {
                player.inventory.clear()
                val router = oldPart("projectred-transporation:interface_pipe",false)
                val chips = new NBTTagList
                val chip = saved(new ItemStack(ProjectRedTransportation.itemRoutingChip))
                chip.setInteger("index",0)
                chips.appendTag(chip)
                router.setTag("chipsitems",chips)
                router.setBoolean("mat",true)
                val migrated = load(router)
                TileMultipart.addPart(world,start.add(0,0,2),migrated)
                migrated.activate(player,null,ItemStack.EMPTY,EnumHand.MAIN_HAND)
                val recovered = count(player,ProjectRedTransportation.itemRoutingChip)
                assert(recovered == 1 && count(player,Items.REDSTONE) == 1)
                migrated.activate(player,null,ItemStack.EMPTY,EnumHand.MAIN_HAND)
                assert(count(player,ProjectRedTransportation.itemRoutingChip) == recovered,"Recovery issued a routing chip twice")
                val item = migrated.getItem
                assert(item.getTagCompound.getCompoundTag(LegacySaveMigration.Key).getTagList("recovered_items",10).isEmpty)
            } finally {
                world.setBlockToAir(start.add(0,0,2))
                for i <- previous.indices do player.inventory.setInventorySlotContents(i,previous(i))
            }
        } finally {
            for i <- 0 until 3 do world.setBlockToAir(start.add(i,0,0))
            world.setBlockToAir(chestPos)
        }
    }

    private def printerChecks(world:WorldServer):Unit = {
        val pos = new BlockPos(8,82,44)
        val player = FakePlayerFactory.getMinecraft(world)
        val previous = Array.tabulate(player.inventory.getSizeInventory)(i=>player.inventory.getStackInSlot(i).copy())
        val wasCreative = player.capabilities.isCreativeMode
        try {
            world.setBlockState(pos,ProjectRedFabrication.icBlock.getStateFromMeta(1),3)
            val printer = world.getTileEntity(pos).asInstanceOf[TileICPrinter]
            val blueprint = FabricationProductionChecks.blueprint()
            val chip = new ItemStack(ProjectRedFabrication.itemICChip)
            ItemICBlueprint.copyIC(blueprint,chip)
            printer.setInventorySlotContents(0,new ItemStack(Items.GOLD_INGOT,7))
            printer.setInventorySlotContents(17,new ItemStack(Items.DIAMOND,9))
            printer.setInventorySlotContents(18,blueprint.copy())
            printer.setInventorySlotContents(19,new ItemStack(ProjectRedFabrication.itemICChip))
            printer.setInventorySlotContents(20,chip)
            printer.isWorking = true
            printer.progress = 0.9
            val before = printer.writeToNBT(new NBTTagCompound)
            printer.updateServer()
            val table = world.getTileEntity(pos).asInstanceOf[TilePackagingTable]
            assert(world.getBlockState(pos).getBlock == ProjectRedFabrication.fabricationMachineBlock)
            assert(!table.isWorking && table.workRemaining == 0 && table.getStackInSlot(4).getItem == ProjectRedFabrication.itemValidDie)
            val recovery = table.getStackInSlot(9)
            val description = table.getUpdateTag
            assert(!description.hasKey("packaging_tableitems") && !description.hasKey(LegacySaveMigration.Key),
                "Tile synchronization included the private recovery inventory or snapshot")
            assert(recovery.getItem == ProjectRedFabrication.itemICChip && recovery.getItemDamage == 2)
            assert(LegacySaveMigration.original(recovery.getTagCompound.getCompoundTag(LegacySaveMigration.Key)) == before)
            val shared = recovery.getItem.getNBTShareTag(recovery)
            assert(shared.getBoolean("recovery_summary") && !shared.hasKey("BlockEntityTag") && shared.getInteger("recovery_stacks") == 4)
            val client = recovery.copy()
            client.getItem.readNBTShareTag(client,shared)
            assert(client.getItem.getNBTShareTag(client) == shared,"Recovery summary is not stable for inventory click validation")

            player.inventory.clear()
            for i <- 0 until 36 do player.inventory.setInventorySlotContents(i,new ItemStack(Blocks.STONE,64))
            player.setHeldItem(EnumHand.MAIN_HAND,recovery)
            table.setInventorySlotContents(9,ItemStack.EMPTY)
            player.capabilities.isCreativeMode = true
            recovery.getItem.onItemRightClick(world,player,EnumHand.MAIN_HAND)
            assert(recovery.getTagCompound.getCompoundTag("BlockEntityTag").getTagList("Items",10).tagCount == 4 && count(player,Items.GOLD_INGOT) == 0,
                "A full creative inventory discarded recovery contents")
            assert(player.capabilities.isCreativeMode,"Recovery changed the player's game mode")
            player.inventory.clear()
            player.setHeldItem(EnumHand.MAIN_HAND,recovery)
            recovery.getItem.onItemRightClick(world,player,EnumHand.MAIN_HAND)
            assert(count(player,Items.GOLD_INGOT) == 7 && count(player,Items.DIAMOND) == 9)
            assert(count(player,ProjectRedFabrication.itemICBlueprint) == 1 && count(player,ProjectRedFabrication.itemRoughSiliconWafer) == 1)
            recovery.getItem.onItemRightClick(world,player,EnumHand.MAIN_HAND)
            assert(count(player,Items.GOLD_INGOT) == 7,"Repeated recovery duplicated Printer ingredients")

            val die = table.getStackInSlot(4)
            for i <- table.cornerSlots do table.setInventorySlotContents(i,PartDefs.PLATE.makeStack)
            for side <- 0 until 4 do table.setInventorySlotContents(table.sideSlots(side),table.getPlateFor(ICData.getInterfaceType(die,side)).makeStack)
            for _ <- 0 until 400 do { table.cond.Vloc = 200; table.updateServer() }
            assert(table.getStackInSlot(9).isEmpty,"Old Printer progress bypassed the new Packaging work duration")
            table.cond.Vloc = 200
            table.updateServer()
            assert(table.getStackInSlot(9).getItem == mrtjp.projectred.ProjectRedIntegration.itemPartGate)
            val savedTable = new NBTTagCompound
            table.save(savedTable)
            val reloaded = new TilePackagingTable
            reloaded.setWorld(world)
            reloaded.setPos(pos)
            reloaded.load(savedTable)
            assert(reloaded.getStackInSlot(9).getCount == 1 && reloaded.getStackInSlot(4).isEmpty)
        } finally {
            player.capabilities.isCreativeMode = wasCreative
            world.setBlockToAir(pos)
            for i <- previous.indices do player.inventory.setInventorySlotContents(i,previous(i))
        }
    }

    private def deviceChecks(world:WorldServer):Unit = {
        val pos = new BlockPos(8,82,48)
        val player = FakePlayerFactory.getMinecraft(world)
        val previous = Array.tabulate(player.inventory.getSizeInventory)(i=>player.inventory.getStackInSlot(i).copy())
        try {
            world.setBlockState(pos,ProjectRedExpansion.machine2.getStateFromMeta(3),3)
            val old = world.getTileEntity(pos).asInstanceOf[mrtjp.projectred.expansion.TileFilteredImporter]
            val filter = new ItemStack(Items.GOLD_INGOT,7)
            filter.setStackDisplayName("saved filter")
            old.setInventorySlotContents(0,filter)
            old.colour = 11
            val buffered = new ItemStack(Items.DIAMOND,1000)
            old.itemStorage.add(buffered)
            old.itemStorage.backlogged = true
            val before = old.writeToNBT(new NBTTagCompound)
            old.updateServer()
            val modern = world.getTileEntity(pos).asInstanceOf[mrtjp.projectred.expansion.TileItemImporter]
            assert(modern.getClass == classOf[mrtjp.projectred.expansion.TileItemImporter])
            assert(modern.itemStorage.peek.getItemStack.getCount == 1000 && modern.itemStorage.backlogged)
            val after = modern.writeToNBT(new NBTTagCompound)
            assert(LegacySaveMigration.original(after.getCompoundTag(LegacySaveMigration.Key)) == before)
            val loaded = new mrtjp.projectred.expansion.TileItemImporter
            loaded.setWorld(world)
            loaded.setPos(pos)
            loaded.load(after)
            loaded.load(after)
            assert(loaded.itemStorage.poll().getItemStack.getCount == 1000 && loaded.itemStorage.isEmpty,
                "Device queue reload duplicated or truncated its buffer")
            player.inventory.clear()
            assert(modern.onBlockActivated(player,1))
            val archive = player.getHeldItemMainhand
            assert(archive.getItem == ProjectRedFabrication.itemICChip && archive.getItemDamage == 2)
            archive.getItem.onItemRightClick(world,player,EnumHand.MAIN_HAND)
            assert(count(player,Items.GOLD_INGOT) == 7 && modern.itemStorage.peek.getItemStack.getCount == 1000,
                "Filtered Importer recovery changed its filter contents or transit buffer")
            assert(!modern.onBlockActivated(player,1),"The converted device still has the old configuration GUI")

            val items = new NBTTagList
            val large = saved(new ItemStack(Items.EMERALD))
            large.setInteger("Count",1000)
            items.appendTag(large)
            val history = new NBTTagCompound
            val carrier = LegacySaveMigration.recoveryItem(history,items)
            player.inventory.clear()
            player.setHeldItem(EnumHand.MAIN_HAND,carrier)
            carrier.getItem.onItemRightClick(world,player,EnumHand.MAIN_HAND)
            assert(count(player,Items.EMERALD) == 1000,"Recovery record narrowed an integer item count to a byte")
            val dropped = new ItemStack(Items.EMERALD,1000)
            dropped.setStackDisplayName("migration-drop-probe")
            val dropPos = pos.add(0,0,8)
            LegacySaveMigration.dropStacks(world,dropPos,dropped)
            val entities = world.getEntitiesWithinAABB(classOf[net.minecraft.entity.item.EntityItem],
                new net.minecraft.util.math.AxisAlignedBB(dropPos).grow(2)).asScala
                .filter(_.getItem.getDisplayName == "migration-drop-probe").toVector
            assert(entities.map(_.getItem.getCount).sum == 1000 && entities.forall(_.getItem.getCount <= 64))
            assert(entities.map(_.writeToNBT(new NBTTagCompound).getCompoundTag("Item").getInteger("Count")).sum == 1000,
                "Dropped buffer items no longer fit vanilla entity saves")
            entities.foreach(_.setDead())
        } finally {
            world.setBlockToAir(pos)
            for i <- previous.indices do player.inventory.setInventorySlotContents(i,previous(i))
        }
    }

    private def importerPolicyChecks(world:WorldServer):Unit = {
        val pos = new BlockPos(8,82,60)
        val source = pos.west()
        val output = pos.east()
        try {
            world.setBlockState(pos,ProjectRedExpansion.machine2.getStateFromMeta(1),3)
            val importer = world.getTileEntity(pos).asInstanceOf[mrtjp.projectred.expansion.TileItemImporter]
            importer.setSide(5)
            world.setBlockState(source,Blocks.CHEST.getDefaultState,3)
            world.setBlockState(output,Blocks.CHEST.getDefaultState,3)
            val chest = world.getTileEntity(source).asInstanceOf[TileEntityChest]
            chest.setInventorySlotContents(0,new ItemStack(Items.EMERALD,3))
            chest.setInventorySlotContents(1,new ItemStack(Items.GOLD_INGOT,7))
            assert(importer.importInv() && chest.getStackInSlot(0).getCount == 2 && chest.getStackInSlot(1).getCount == 7,
                "Importer capability extraction did not take one item from the first slot")
            assert(importer.itemStorage.peek.getItemStack.getCount == 1 &&
                world.getTileEntity(output).asInstanceOf[TileEntityChest].isEmpty,"Device still exported directly into an inventory")
            importer.itemStorage.clear()
            class SidedChest extends TileEntityChest with net.minecraft.inventory.ISidedInventory {
                def getSlotsForFace(side:net.minecraft.util.EnumFacing):Array[Int] = Array(1,0)
                def canInsertItem(slot:Int,stack:ItemStack,side:net.minecraft.util.EnumFacing):Boolean = true
                def canExtractItem(slot:Int,stack:ItemStack,side:net.minecraft.util.EnumFacing):Boolean = true
            }
            val sided = new SidedChest
            world.setTileEntity(source,sided)
            sided.setInventorySlotContents(0,new ItemStack(Items.GOLD_INGOT,7))
            sided.setInventorySlotContents(1,new ItemStack(Items.DIAMOND,64))
            assert(importer.importInv() && sided.getStackInSlot(1).isEmpty && sided.getStackInSlot(0).getCount == 7 &&
                importer.itemStorage.peek.getItemStack.getCount == 64,"Sided extraction differs from the pinned 1.21.1 stack/slot behavior")
            assert(!importer.importEntities(),"Unimplemented upstream entity suction is still active")
        } finally {
            world.setBlockToAir(pos)
            world.setBlockToAir(source)
            world.setBlockToAir(output)
        }
    }
}
