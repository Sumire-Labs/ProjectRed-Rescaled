/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.expansion

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.multipart.BlockMultipart
import mrtjp.core.inventory.InvWrapper
import mrtjp.core.item.ItemKey
import mrtjp.core.world.WorldLib
import mrtjp.projectred.core.PRLib
import mrtjp.projectred.transportation.*
import net.minecraft.block.Block
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{NBTTagCompound, NBTTagList}
import net.minecraft.util.EnumFacing
import net.minecraft.entity.player.EntityPlayer
import mrtjp.projectred.core.LegacySaveMigration

import scala.collection.mutable.ListBuffer

class ItemStorage
{
    private val storage = ListBuffer[PressurePayload]()
    private val unreadable = ListBuffer[(Long,NBTTagCompound)]()
    private val order = new java.util.IdentityHashMap[PressurePayload,java.lang.Long]
    private var firstOrder = -1L
    private var lastOrder = 0L
    var backlogged = false

    def isEmpty = storage.isEmpty && unreadable.isEmpty
    def hasUnreadable = unreadable.nonEmpty
    def unreadableItems:Seq[NBTTagCompound] = unreadable.map { (_,tag) =>
        val item = if tag.hasKey("Item",10) then tag.getCompoundTag("Item").copy() else tag.copy()
        if tag.hasKey("stack_count",99) then item.setInteger("Count",tag.getInteger("stack_count"))
        item
    }.toVector
    def clearUnreadable():Unit = unreadable.clear()
    def clear():Unit = {
        storage.clear(); unreadable.clear(); order.clear()
        firstOrder = -1L; lastOrder = 0L; backlogged = false
    }

    def add(item:PressurePayload): Unit ={
        storage.prepend(item)
        order.put(item,java.lang.Long.valueOf(firstOrder))
        firstOrder -= 1
    }

    def add(item:ItemStack): Unit =
    {
        val p = new PressurePayload(AbstractPipePayload.claimID())
        p.setItemStack(item)
        add(p)
    }

    def addBacklog(item:PressurePayload): Unit ={
        storage.append(item); backlogged = true
        order.put(item,java.lang.Long.valueOf(lastOrder))
        lastOrder += 1
    }

    def poll() =
    {
        val item = storage.remove(storage.size-1)
        order.remove(item)
        if storage.size == 0 then backlogged = false
        item
    }

    def peek = storage(storage.size-1)

    def save(tag:NBTTagCompound): Unit =
    {
        val nbttaglist = new NBTTagList
        val entries = storage.map { r =>
            val payloadData = new NBTTagCompound
            r.save(payloadData)
            (order.get(r).longValue(),payloadData)
        }
        (entries ++ unreadable).sortBy(_._1).foreach((_,tag)=>nbttaglist.appendTag(tag))
        tag.setTag("itemFlow", nbttaglist)
        tag.setBoolean("itemFlowBacklogged",backlogged)
    }

    def load(tag:NBTTagCompound): Unit =
    {
        clear()
        val nbttaglist = tag.getTagList("itemFlow", 10)
        lastOrder = nbttaglist.tagCount.toLong
        for j <- 0 until nbttaglist.tagCount do
        {
            val payloadData = nbttaglist.getCompoundTagAt(j)
            try
            {
                val r = new PressurePayload(AbstractPipePayload.claimID())
                r.load(payloadData)
                if !r.isCorrupted then {
                    storage.append(r)
                    order.put(r,java.lang.Long.valueOf(j.toLong))
                } else unreadable += ((j.toLong,payloadData.copy()))
            }
            catch {case scala.util.control.NonFatal(_) => unreadable += ((j.toLong,payloadData.copy()))}
        }
        backlogged = !isEmpty && (!tag.hasKey("itemFlowBacklogged") || tag.getBoolean("itemFlowBacklogged"))
    }
}

trait TActiveDevice extends TileMachine
{
    private var legacyArchive:NBTTagCompound = null
    private var hasRecovery = false
    var suppressLegacyRecoveryDrop = false
    val itemStorage = new ItemStorage
    var powered = false
    var active = false

    override def save(tag:NBTTagCompound): Unit =
    {
        super.save(tag)
        tag.setBoolean("pow", powered)
        tag.setBoolean("act", active)
        itemStorage.save(tag)
        if legacyArchive != null then tag.setTag(LegacySaveMigration.Key,legacyArchive.copy())
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        super.load(tag)
        powered = tag.getBoolean("pow")
        active = tag.getBoolean("act")
        itemStorage.load(tag)
        if !itemStorage.isEmpty then active = true
        legacyArchive = if tag.hasKey(LegacySaveMigration.Key,10) then tag.getCompoundTag(LegacySaveMigration.Key).copy() else null
        hasRecovery = legacyArchive != null && !legacyArchive.getBoolean("archive_exported")
    }

    override def writeDesc(out:MCDataOutput): Unit =
    {
        super.writeDesc(out)
        out.writeBoolean(powered).writeBoolean(active)
        out.writeBoolean(hasRecovery)
    }

    override def readDesc(in:MCDataInput): Unit =
    {
        super.readDesc(in)
        powered = in.readBoolean()
        active = in.readBoolean()
        hasRecovery = in.readBoolean()
    }

    override def onBlockActivated(player:EntityPlayer, face:Int):Boolean = {
        if itemStorage.hasUnreadable && player.getHeldItemMainhand.isEmpty then {
            if !getWorld.isRemote then player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.projectred.migration.queue_unreadable"))
            return true
        }
        if !hasRecovery || !player.getHeldItemMainhand.isEmpty then return super.onBlockActivated(player,face)
        if !getWorld.isRemote then {
            val items = legacyArchive.getTagList("recovered_items",10)
            val carrier = LegacySaveMigration.recoveryItem(legacyArchive,items)
            if !carrier.isEmpty then {
                LegacySaveMigration.insertRecovered(carrier,player)
                if carrier.isEmpty then {
                    legacyArchive.setTag("recovered_items",new NBTTagList)
                    legacyArchive.setBoolean("archive_exported",true)
                    hasRecovery = false
                    markDirty()
                    markDescUpdate()
                }
            }
        }
        true
    }

    override def read(in:MCDataInput, key:Int) = key match
    {
        case 4 =>
            powered = in.readBoolean()
            active = in.readBoolean()
            markRender()
        case _ => super.read(in, key)
    }

    def sendStateUpdate(): Unit =
    {
        writeStream(4).writeBoolean(powered).writeBoolean(active).sendToChunk(this)
    }

    def shouldAcceptBacklog = true
    def shouldAcceptInput = !powered && itemStorage.isEmpty

    override def updateServer():Unit = {
        super.updateServer()
        if !itemStorage.isEmpty && !isTickScheduled then scheduleTick(4)
    }

    override def onScheduledTick(): Unit =
    {
        if !getWorld.isRemote then
        {
            if !itemStorage.isEmpty then
            {
                exportBuffer()
                scheduleTick(if itemStorage.isEmpty then 4 else 16)
            }
            else if !powered then
            {
                active = false
                onDeactivate()
                sendStateUpdate()
            }
        }
    }

    override def onNeighborBlockChange(): Unit =
    {
        if getWorld.isBlockPowered(getPos) then
        {
            if powered then return
            powered = true
            markDirty()
            if active then return
            active = true
            onActivate()
            sendStateUpdate()
        }
        else
        {
            if active && !isTickScheduled then scheduleTick(4)
            powered = false
            markDirty()
        }
    }

    def onActivate(): Unit
    def onDeactivate(): Unit ={}

    def exportBuffer(): Unit =
    {
        if itemStorage.hasUnreadable then return
        while !itemStorage.isEmpty do
        {
            val r = itemStorage.peek
            if exportPipe(r) || exportEject(r) then itemStorage.poll()
            else itemStorage.backlogged = true

            if itemStorage.backlogged then return
        }
    }

    def exportPipe(r:PressurePayload) =
    {
        BlockMultipart.getPart(getWorld, getPos.offset(EnumFacing.VALUES(side)), 6) match
        {
            case pipe:mrtjp.projectred.transportation.pneumatics.PneumaticTransportContainer =>
                pipe.insertPayload(side,new mrtjp.projectred.transportation.pneumatics.part.PneumaticTubePayload(r.payload.makeStack.copy()))
            case _ => false
        }
    }

    def exportInv(r:PressurePayload) =
    {
        val w = InvWrapper.wrap(getWorld, getPos.offset(EnumFacing.VALUES(side)), EnumFacing.VALUES(side^1))
        if w != null then
        {
            r.payload.stackSize -= w.injectItem(r.payload.key, r.payload.stackSize)
            r.payload.stackSize <= 0
        }
        else false
    }

    def exportEject(r:PressurePayload):Boolean =
    {
        val pos = getPos.offset(EnumFacing.VALUES(side))
        if getWorld.isBlockLoaded(pos) &&
                !getWorld.isAirBlock(pos) then return false

        WorldLib.centerEject(getWorld, getPos, r.payload.makeStack, side, 0.25D)
        true
    }

    override def onBlockRemoval(): Unit =
    {
        super.onBlockRemoval()
        if itemStorage.hasUnreadable then {
            val source = writeToNBT(new NBTTagCompound)
            val history = LegacySaveMigration.archive(source,"device_queue")
            val recovered = history.getTagList("recovered_items",10).copy()
            itemStorage.unreadableItems.foreach(recovered.appendTag)
            val carrier = LegacySaveMigration.recoveryItem(history,recovered)
            if !carrier.isEmpty then WorldLib.dropItem(getWorld,getPos,carrier)
            itemStorage.clearUnreadable()
            if legacyArchive != null then legacyArchive.setBoolean("archive_exported",true)
            hasRecovery = false
        }
        if hasRecovery && legacyArchive != null && !suppressLegacyRecoveryDrop then {
            val carrier = LegacySaveMigration.recoveryItem(legacyArchive,legacyArchive.getTagList("recovered_items",10))
            if !carrier.isEmpty then WorldLib.dropItem(getWorld,getPos,carrier)
            hasRecovery = false
            legacyArchive.setBoolean("archive_exported",true)
        }
        while !itemStorage.isEmpty do
            LegacySaveMigration.dropStacks(getWorld,getPos,itemStorage.poll().payload.makeStack)
    }
}

trait TPressureActiveDevice extends TActiveDevice with TPressureDevice with mrtjp.projectred.transportation.pneumatics.PneumaticTransportDevice
{
    override def canConnectTube(side:Int):Boolean = canConnectSide(side)

    override def canAcceptPayload(side:Int, item:mrtjp.projectred.transportation.pneumatics.part.PneumaticTubePayload,
        mode:mrtjp.projectred.transportation.pneumatics.PneumaticTransportMode):Boolean = {
        if !canConnectSide(side) then return false
        val key = ItemKey.get(item.getItemStack)
        mode match {
            case mrtjp.projectred.transportation.pneumatics.PneumaticTransportMode.PASSIVE_NORMAL => canAcceptInput(key,side) && shouldAcceptInput
            case mrtjp.projectred.transportation.pneumatics.PneumaticTransportMode.PASSIVE_BACKSTUFF => canAcceptBacklog(key,side) && shouldAcceptBacklog
        }
    }

    override def insertPayload(side:Int, item:mrtjp.projectred.transportation.pneumatics.part.PneumaticTubePayload):Boolean = {
        if item.isCorrupted then return false
        val legacy = new PressurePayload(AbstractPipePayload.claimID())
        legacy.setItemStack(item.getItemStack.copy())
        acceptItem(legacy,side)
    }

    override def acceptItem(item:PressurePayload, side:Int):Boolean =
    {
        if !canConnectSide(side) then return false

        if canAcceptInput(item.payload.key, side) && shouldAcceptInput then
        {
            itemStorage.add(item)
            active = true
            sendStateUpdate()
            scheduleTick(4)
            exportBuffer()
            true
        }
        else if canAcceptBacklog(item.payload.key, side) && shouldAcceptBacklog then
        {
            itemStorage.addBacklog(item)
            active = true
            sendStateUpdate()
            scheduleTick(4)
            true
        }
        else false
    }
}
