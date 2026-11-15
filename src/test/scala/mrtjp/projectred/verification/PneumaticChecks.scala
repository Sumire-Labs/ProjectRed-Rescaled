package mrtjp.projectred.verification

import codechicken.lib.packet.PacketCustom
import codechicken.multipart.TileMultipart
import mrtjp.projectred.transportation.{PipeDefs, PressureTube, ResistanceTube}
import mrtjp.projectred.transportation.pneumatics.*
import mrtjp.projectred.transportation.pneumatics.part.*
import net.minecraft.init.{Blocks,Items}
import net.minecraft.inventory.ISidedInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldServer
import scala.collection.mutable

object PneumaticChecks {
    class Probe extends PneumaticTransportContainer {
        val transport = new PneumaticTransport(this)
        var onExit:PneumaticTubePayload => Boolean = _ => false
        def getPneumaticTransport = transport
        def setOutputDirection(payload:PneumaticTubePayload):Unit = payload.setOutputSide(5)
        def onPayloadAdded(id:Int,payload:PneumaticTubePayload):Unit = ()
        def onPayloadChanged(id:Int,payload:PneumaticTubePayload):Unit = ()
        def onPayloadRemoved(id:Int,payload:PneumaticTubePayload):Unit = ()
        def onPayloadReachedOutput(id:Int,payload:PneumaticTubePayload):Boolean = onExit(payload)
        def canItemEnterTube(payload:PneumaticTubePayload,side:Int):Boolean = true
        def canItemExitTube(payload:PneumaticTubePayload,side:Int,mode:PneumaticTransportMode):Boolean = true
        def insertPayload(side:Int,payload:PneumaticTubePayload):Boolean = { transport.addPayload(payload); true }
    }
    def run(world:WorldServer):Unit = {
        transportChecks()
        val touched = mutable.Set.empty[BlockPos]
        def chest(pos:BlockPos):TileEntityChest = {
            touched += pos
            world.setBlockState(pos,Blocks.CHEST.getDefaultState,3)
            world.getTileEntity(pos).asInstanceOf[TileEntityChest]
        }
        def tube(pos:BlockPos):PneumaticTubePart = {
            touched += pos
            val part = new PneumaticTubePart
            part.preparePlacement(5,PipeDefs.PNEUMATICTUBE.meta)
            TileMultipart.addPart(world,pos,part)
            part
        }
        def count(inv:TileEntityChest,item:net.minecraft.item.Item):Int =
            (0 until inv.getSizeInventory).map(inv.getStackInSlot).filter(_.getItem == item).map(_.getCount).sum
        def fill(inv:TileEntityChest):Unit =
            for i <- 0 until inv.getSizeInventory do inv.setInventorySlotContents(i,new ItemStack(Blocks.STONE,64))
        def tick(tubes:Seq[PneumaticTubePart],n:Int = 120):Unit =
            for _ <- 0 until n do tubes.foreach(_.update())
        def line(start:BlockPos):(TileEntityChest,Vector[PneumaticTubePart],TileEntityChest) = {
            val source = chest(start)
            val destination = chest(start.add(4,0,0))
            val pipes = (1 to 3).map(x => tube(start.add(x,0,0))).toVector
            pipes.foreach(_.onNeighborChanged())
            (source,pipes,destination)
        }
        try {
            val start = new BlockPos(40,80,8)
            val (source,pipes,destination) = line(start)
            val gold = new ItemStack(Items.GOLD_INGOT,7)
            val marker = new NBTTagCompound
            marker.setString("marker","pneumatic")
            gold.setTagCompound(marker)
            val chunk = world.getChunk(start.add(1,0,0))
            chunk.setModified(false)
            assert(pipes.head.insertPayload(5,new PneumaticTubePayload(gold.copy())),"New tube refused a reachable inventory")
            assert(chunk.needsSaving(false),"New payload did not mark its chunk dirty")
            val inFlight = new NBTTagCompound
            pipes.head.save(inFlight)
            pipes.head.onWorldSeparate()
            pipes.head.load(inFlight)
            pipes.head.onWorldJoin()
            assert(pipes.head.getPneumaticTransport.getPayloads.map(_.getItemStack.getCount).sum == 7,
                "World re-entry changed the in-flight payload")
            tick(pipes)
            assert(count(destination,Items.GOLD_INGOT) == 7 && count(source,Items.GOLD_INGOT) == 0)
            assert(destination.getStackInSlot(0).getTagCompound == marker,"Pneumatic transport lost item NBT")
            assert(pipes.forall(_.getPneumaticTransport.getPayloads.isEmpty))

            val near = chest(start.add(2,0,1))
            pipes.foreach(_.onNeighborChanged())
            assert(pipes.head.insertPayload(5,new PneumaticTubePayload(new ItemStack(Items.GOLD_INGOT,3))))
            tick(pipes)
            assert(count(near,Items.GOLD_INGOT) == 3 && count(destination,Items.GOLD_INGOT) == 7,"Graph routing ignored the nearer exit")
            val north = chest(start.add(2,0,-1))
            pipes.foreach(_.onNeighborChanged())
            for _ <- 0 until 2 do assert(pipes.head.insertPayload(5,new PneumaticTubePayload(new ItemStack(Items.IRON_INGOT))))
            tick(pipes)
            assert(count(near,Items.IRON_INGOT) == 1 && count(north,Items.IRON_INGOT) == 1,"Equal exits did not receive round-robin payloads")
            val saved = new NBTTagCompound
            pipes(1).save(saved)
            val restored = new PneumaticTubePart
            restored.load(saved)
            val savedAgain = new NBTTagCompound
            restored.save(savedAgain)
            assert(saved.hasKey("last_dir") && saved.getByte("last_dir") == savedAgain.getByte("last_dir"),"Tube reload lost round-robin state")

            fill(near); fill(north); fill(destination)
            near.setInventorySlotContents(0,new ItemStack(Items.IRON_INGOT,63))
            north.setInventorySlotContents(0,new ItemStack(Items.IRON_INGOT,63))
            assert(pipes.head.insertPayload(5,new PneumaticTubePayload(new ItemStack(Items.IRON_INGOT,5))))
            tick(pipes,240)
            assert(count(near,Items.IRON_INGOT) == 64 && count(north,Items.IRON_INGOT) == 64 && count(source,Items.IRON_INGOT) == 3,
                "Partial insertion/bounce lost items or retried the same full exit")
            near.setInventorySlotContents(0,ItemStack.EMPTY)
            assert(pipes.head.insertPayload(5,new PneumaticTubePayload(new ItemStack(Items.DIAMOND,5))))
            fill(near); fill(source)
            tick(pipes)
            assert(pipes.flatMap(_.getPneumaticTransport.getPayloads).map(_.getItemStack.getCount).sum == 5,
                "Items vanished when every destination became full")
            source.setInventorySlotContents(0,ItemStack.EMPTY)
            tick(pipes,240)
            assert(count(source,Items.DIAMOND) == 5 && pipes.forall(_.getPneumaticTransport.getPayloads.isEmpty))

            val deviceStart = new BlockPos(56,80,8)
            val (_,devicePipes,deviceOut) = line(deviceStart)
            world.setBlockState(deviceStart,mrtjp.projectred.ProjectRedExpansion.machine2.getStateFromMeta(1),3)
            val importer = world.getTileEntity(deviceStart).asInstanceOf[mrtjp.projectred.expansion.TileItemImporter]
            importer.setSide(5)
            devicePipes.foreach(_.onNeighborChanged())
            importer.itemStorage.add(gold.copy())
            importer.exportBuffer()
            tick(devicePipes)
            assert(importer.itemStorage.isEmpty && count(deviceOut,Items.GOLD_INGOT) == 7,"Existing importer could not export into the new tubes")
            importer.powered = true
            val back = new PneumaticTubePayload(new ItemStack(Items.EMERALD,9))
            assert(importer.canAcceptPayload(5,back,PneumaticTransportMode.PASSIVE_BACKSTUFF))
            assert(importer.insertPayload(5,back))
            val oldQueueSave = new NBTTagCompound
            importer.save(oldQueueSave)
            val reloadedImporter = new mrtjp.projectred.expansion.TileItemImporter
            reloadedImporter.load(oldQueueSave)
            assert(reloadedImporter.itemStorage.poll().getItemStack.getCount == 9 && reloadedImporter.itemStorage.isEmpty,
                "Pneumatic device adapter changed the existing queue format")

            val returnStart = new BlockPos(80,80,8)
            val (_,returnPipes,returnDestination) = line(returnStart)
            world.setBlockState(returnStart,mrtjp.projectred.ProjectRedExpansion.machine2.getStateFromMeta(1),3)
            val returnImporter = world.getTileEntity(returnStart).asInstanceOf[mrtjp.projectred.expansion.TileItemImporter]
            returnImporter.setSide(5)
            val alternate = chest(returnStart.add(1,0,1))
            returnPipes.foreach(_.onNeighborChanged())
            val returning = new PneumaticTubePayload(new ItemStack(Items.IRON_INGOT))
            returning.setInputSide(4)
            returnPipes.head.setOutputDirection(returning)
            assert(returning.getOutputSide == 3,"Backstuff was preferred over an available ordinary destination")
            fill(alternate)
            returnPipes.head.setOutputDirection(returning)
            assert(returning.getOutputSide == 4,"Returning payload was not routed into an available backstuff buffer")
            returnImporter.itemStorage.add(new ItemStack(Items.IRON_INGOT,5))
            returnImporter.exportBuffer()
            assert(returnImporter.itemStorage.isEmpty)
            fill(returnDestination)
            tick(returnPipes,240)
            assert(!returnImporter.itemStorage.isEmpty && returnImporter.itemStorage.peek.getItemStack.getCount == 5 &&
                returnImporter.itemStorage.backlogged && returnPipes.forall(_.getPneumaticTransport.getPayloads.isEmpty),
                "Blocked delivery did not return intact to its importer")
            assert(!returnPipes.head.insertPayload(5,new PneumaticTubePayload(new ItemStack(Items.IRON_INGOT))),
                "New input was accepted with only backstuff destinations available")

            val sidedStart = new BlockPos(64,80,8)
            val (sidedSource,sidedPipes,_) = line(sidedStart)
            fill(sidedSource)
            class RejectWest extends TileEntityChest with ISidedInventory {
                def getSlotsForFace(side:EnumFacing):Array[Int] = Array(0)
                def canInsertItem(slot:Int,stack:ItemStack,side:EnumFacing):Boolean = side != EnumFacing.WEST
                def canExtractItem(slot:Int,stack:ItemStack,side:EnumFacing):Boolean = true
            }
            world.setTileEntity(sidedStart.add(4,0,0),new RejectWest)
            sidedPipes.foreach(_.onNeighborChanged())
            assert(!sidedPipes.head.insertPayload(5,new PneumaticTubePayload(new ItemStack(Items.IRON_INGOT))),
                "Pneumatic routing bypassed sided insertion restrictions")
            val oldPressure = mrtjp.projectred.transportation.TransportationProxy.createPart(PipeDefs.PRESSURETUBE.partname,false)
            val oldResistance = mrtjp.projectred.transportation.TransportationProxy.createPart(PipeDefs.RESISTANCETUBE.partname,false)
            assert(oldPressure.isInstanceOf[PressureTube] && oldResistance.isInstanceOf[ResistanceTube],"Legacy tube registrations were replaced")
        } finally touched.toSeq.reverse.foreach(world.setBlockToAir)
    }

    private def transportChecks():Unit = {
        val source = new Probe
        val oversized = new PneumaticTubePayload(new ItemStack(Items.IRON_INGOT,1000))
        val oversizedTag = new NBTTagCompound
        oversized.save(oversizedTag)
        val oversizedCopy = new PneumaticTubePayload
        oversizedCopy.load(oversizedTag)
        assert(oversizedCopy.getItemStack.getCount == 1000,"Tube NBT truncated an oversized stack count")
        for i <- 0 until 300 do {
            val payload = new PneumaticTubePayload(new ItemStack(Items.IRON_INGOT,if i == 0 then 1000 else i%64+1))
            payload.setInputSide(5); payload.setOutputSide(3); payload.setSpeed(12); payload.setProgress(17)
            source.transport.addPayload(payload)
        }
        val packet = new PacketCustom("PRVerification",1)
        source.transport.writeDesc(packet)
        val incoming = new PacketCustom(packet.copy())
        val client = new Probe
        client.transport.readDesc(incoming)
        assert(client.transport.getPayloads.size == 300,"Description truncated a tube with more than 255 payloads")
        assert(client.transport.getPayloads.map(_.getItemStack.getCount).sum == source.transport.getPayloads.map(_.getItemStack.getCount).sum)
        val saved = new NBTTagCompound
        source.transport.save(saved)
        client.transport.load(saved); client.transport.load(saved)
        assert(client.transport.getPayloads.size == 300 && client.transport.getPayloads.forall(_.getProgress == 17))
        incoming.release(); packet.release()
        val reentrant = new Probe
        val first = new PneumaticTubePayload(new ItemStack(Items.IRON_INGOT))
        first.setProgress(254); first.setSpeed(12)
        reentrant.transport.addPayload(first)
        reentrant.onExit = _ => {
            val added = new PneumaticTubePayload(new ItemStack(Items.GOLD_INGOT))
            added.setProgress(254); added.setSpeed(12)
            reentrant.transport.addPayload(added)
            true
        }
        reentrant.transport.tick()
        assert(reentrant.transport.getPayloads.size == 1 && reentrant.transport.getPayloads.head.getItemStack.getItem == Items.GOLD_INGOT)
        assert(reentrant.transport.getPayloads.head.getProgress == 254,"Payload callback insertion was ticked immediately")
    }
}
