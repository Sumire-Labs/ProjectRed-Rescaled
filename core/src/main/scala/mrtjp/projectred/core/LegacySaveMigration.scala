package mrtjp.projectred.core

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import net.minecraft.entity.item.EntityItem
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{CompressedStreamTools, NBTBase, NBTTagCompound, NBTTagList, NBTSizeTracker}
import net.minecraft.util.datafix.{FixTypes, IFixableData}
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.entity.player.PlayerContainerEvent
import net.minecraftforge.event.world.ChunkEvent
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.items.{CapabilityItemHandler, IItemHandlerModifiable}
import scala.jdk.CollectionConverters.*

/** One-time save conversion, plus ingress checks for items imported after conversion. */
object LegacySaveMigration {
    val Key = "projectred_legacy"
    private case class Rule(matches:NBTTagCompound => Boolean, convert:NBTTagCompound => Unit)
    private var rules = Vector.empty[Rule]
    private var itemChecks = Vector.empty[ItemStack => Boolean]

    def register(matches:NBTTagCompound => Boolean)(convert:NBTTagCompound => Unit):Unit =
        rules :+= Rule(matches,convert)
    def registerItemCheck(check:ItemStack => Boolean):Unit = itemChecks :+= check

    def init():Unit = {
        val fixes = FMLCommonHandler.instance().getDataFixer.init("projectred-core",1)
        for kind <- FixTypes.values() if kind != FixTypes.OPTIONS do
            fixes.registerFix(kind,new IFixableData {
                def getFixVersion:Int = 1
                def fixTagCompound(tag:NBTTagCompound):NBTTagCompound = migrate(tag)
            })
        MinecraftForge.EVENT_BUS.register(this)
    }

    def migrate(root:NBTTagCompound):NBTTagCompound = {
        val pending = new java.util.ArrayDeque[NBTBase]
        pending.add(root)
        while !pending.isEmpty do pending.removeFirst() match {
            case tag:NBTTagCompound =>
                for rule <- rules if rule.matches(tag) do {
                    val converted = tag.copy()
                    rule.convert(converted)
                    tag.getKeySet.asScala.toVector.foreach(tag.removeTag)
                    tag.merge(converted)
                }
                for key <- tag.getKeySet.asScala if key != Key do {
                    val child = tag.getTag(key)
                    if child.isInstanceOf[NBTTagCompound] || child.isInstanceOf[NBTTagList] then pending.add(child)
                }
            case list:NBTTagList =>
                for i <- 0 until list.tagCount do {
                    val child = list.get(i)
                    if child.isInstanceOf[NBTTagCompound] || child.isInstanceOf[NBTTagList] then pending.add(child)
                }
            case _ =>
        }
        root
    }

    def archive(tag:NBTTagCompound, kind:String):NBTTagCompound = {
        if !tag.hasKey(Key,10) then {
            val bytes = new ByteArrayOutputStream
            val stream = new DataOutputStream(new GZIPOutputStream(bytes))
            try CompressedStreamTools.write(tag,stream) finally stream.close()
            val saved = new NBTTagCompound
            saved.setInteger("version",1)
            saved.setString("kind",kind)
            saved.setByteArray("source_gzip",bytes.toByteArray)
            tag.setTag(Key,saved)
        }
        tag.getCompoundTag(Key)
    }

    def original(archive:NBTTagCompound):NBTTagCompound = {
        val gzip = new GZIPInputStream(new ByteArrayInputStream(archive.getByteArray("source_gzip")))
        val bytes = try com.google.common.io.ByteStreams.toByteArray(
            com.google.common.io.ByteStreams.limit(gzip,32L*1024*1024+1)) finally gzip.close()
        require(bytes.length <= 32*1024*1024,"Legacy recovery data exceeds 32 MiB")
        CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(bytes)),new NBTSizeTracker(128L*1024*1024))
    }

    def item(stack:ItemStack):ItemStack = {
        if stack.isEmpty || !itemChecks.exists(_(stack)) then stack
        else {
            val saved = stack.writeToNBT(new NBTTagCompound)
            saved.setInteger("Count",stack.getCount)
            val result = stackFromSaved(migrate(saved),stack.getCount)
            if result.hasTagCompound && result.getTagCompound.hasKey(Key,10) then
                result.getTagCompound.getCompoundTag(Key).setInteger("source_count",stack.getCount)
            result
        }
    }

    def stackFromSaved(saved:NBTTagCompound, count:Int):ItemStack = {
        val data = saved.copy()
        // Keep item/capability initialization valid before restoring an extended count.
        if count > 0 then data.setByte("Count",math.min(127,count).toByte)
        val stack = new ItemStack(data)
        stack.setCount(count)
        stack
    }

    /** Recovery records use integer counts; ordinary ItemStack NBT narrows Count to a byte. */
    def readRecoveryItem(tag:NBTTagCompound):ItemStack =
        try {
            val migrated = migrate(tag.copy())
            stackFromSaved(migrated,migrated.getInteger("Count"))
        } catch { case scala.util.control.NonFatal(error) =>
            mrtjp.projectred.ProjectRedCore.log.warn("A legacy recovery record could not be decoded; its NBT was retained",error)
            ItemStack.EMPTY
        }

    def withdraw(items:NBTTagList, player:net.minecraft.entity.player.EntityPlayer):NBTTagList = {
        val remaining = new NBTTagList
        for i <- 0 until items.tagCount do {
            val original = items.getCompoundTagAt(i)
            val stack = readRecoveryItem(original)
            if stack.isEmpty then remaining.appendTag(original.copy())
            else {
                insertRecovered(stack,player)
                if !stack.isEmpty then {
                    val entry = stack.writeToNBT(new NBTTagCompound)
                    entry.setInteger("Count",stack.getCount)
                    remaining.appendTag(entry)
                }
            }
        }
        remaining
    }

    def insertRecovered(stack:ItemStack, player:net.minecraft.entity.player.EntityPlayer):Unit = {
        // Vanilla discards overflow in creative mode; recovery must retain every leftover.
        val creative = player.capabilities.isCreativeMode
        try {
            player.capabilities.isCreativeMode = false
            player.inventory.addItemStackToInventory(stack)
        } finally player.capabilities.isCreativeMode = creative
    }

    def dropStacks(world:net.minecraft.world.World, pos:net.minecraft.util.math.BlockPos, stack:ItemStack):Unit = {
        val limit = math.max(1,math.min(64,stack.getMaxStackSize))
        while !stack.isEmpty do mrtjp.core.world.WorldLib.dropItem(world,pos,stack.splitStack(limit))
    }

    def recoveryItem(archive:NBTTagCompound, items:NBTTagList):ItemStack = {
        val saved = new NBTTagCompound
        saved.setString("id","projectred-fabrication:ic_chip")
        saved.setByte("Count",1.toByte)
        saved.setShort("Damage",2.toShort)
        val tag = new NBTTagCompound
        val block = new NBTTagCompound
        block.setTag("Items",items.copy())
        tag.setTag("BlockEntityTag",block)
        val history = archive.copy()
        history.removeTag("recovered_items")
        tag.setTag(Key,history)
        saved.setTag("tag",tag)
        new ItemStack(saved)
    }

    /** Recovery payloads stay authoritative on the server, including on migrated machine items. */
    def archiveShare(tag:NBTTagCompound):NBTTagCompound = {
        if tag == null || !tag.hasKey(Key,10) then return tag
        val summary = new NBTTagCompound
        for name <- tag.getKeySet.asScala if name != Key && name != "BlockEntityTag" do
            summary.setTag(name,tag.getTag(name).copy())
        val history = tag.getCompoundTag(Key)
        val marker = new NBTTagCompound
        marker.setInteger("version",history.getInteger("version"))
        marker.setString("kind",history.getString("kind"))
        summary.setTag(Key,marker)
        summary
    }

    def inventory(inv:IInventory):Unit = {
        for i <- 0 until inv.getSizeInventory do {
            val before = inv.getStackInSlot(i)
            val after = item(before)
            if after ne before then inv.setInventorySlotContents(i,after)
        }
    }

    @SubscribeEvent def chunkLoaded(event:ChunkEvent.Load):Unit = if !event.getWorld.isRemote then {
        for tile <- event.getChunk.getTileEntityMap.values.asScala.toVector do tile match {
            case inv:IInventory => inventory(inv)
            case _ => tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null) match {
                case inv:IItemHandlerModifiable => for i <- 0 until inv.getSlots do {
                    val before = inv.getStackInSlot(i)
                    val after = item(before)
                    if after ne before then { inv.setStackInSlot(i,after); tile.markDirty() }
                }
                case _ =>
            }
        }
    }
    @SubscribeEvent def playerTick(event:TickEvent.PlayerTickEvent):Unit =
        if event.phase == TickEvent.Phase.END && !event.player.world.isRemote && event.player.ticksExisted%20 == 0 then
            inventory(event.player.inventory)
    @SubscribeEvent def opened(event:PlayerContainerEvent.Open):Unit = if !event.getEntityPlayer.world.isRemote then {
        for slot <- event.getContainer.inventorySlots.asScala do {
            val before = slot.getStack
            val after = item(before)
            if after ne before then slot.putStack(after)
        }
    }
    @SubscribeEvent def entityJoined(event:EntityJoinWorldEvent):Unit = if !event.getWorld.isRemote then event.getEntity match {
        case entity:EntityItem => entity.setItem(item(entity.getItem))
        case _ =>
    }
}
