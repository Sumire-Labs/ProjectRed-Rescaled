package mrtjp.projectred.verification

import codechicken.lib.data.{MCDataOutput, MCDataOutputWrapper}
import codechicken.lib.packet.PacketCustom
import io.netty.buffer.Unpooled
import mrtjp.core.vec.{Point, Size}
import mrtjp.projectred.fabrication.*
import net.minecraft.network.PacketBuffer
import net.minecraft.world.{World, WorldServer}

object LargeICPacketChecks {
    private def editor(world:WorldServer, remote:Boolean):ICTileMapEditor = {
        var result:ICTileMapEditor = null
        val network = new IICTileEditorNetwork {
            def getIC:ICTileMapEditor = result
            def getEditorWorld:World = world
            def getICStreamOf(key:Int):MCDataOutput = new MCDataOutputWrapper(new java.io.ByteArrayOutputStream)
            def getTileStream(pos:Point):MCDataOutput = new MCDataOutputWrapper(new java.io.ByteArrayOutputStream)
            def isRemote:Boolean = remote
            def markSave():Unit = ()
        }
        result = new ICTileMapEditor(network)
        result
    }

    def fixture(editor:ICTileMapEditor):Unit = {
        editor.tileMapContainer.size = Size(64,64)
        editor.tileMapContainer.name = "Large packet regression"
        val random = new java.util.Random(1257L)
        for x <- 1 until 63; y <- 1 until 63 do {
            val gate = new SequentialGateICTile
            gate.preparePlacement((x+y)%4,ICGateDefinition.Counter.ordinal)
            gate.bindEditor(editor)
            gate.bindPos(Point(x,y))
            val counter = gate.getLogic[Counter]
            counter.max = random.nextInt(32766)+2
            counter.value = random.nextInt(counter.max)
            counter.currentValue = counter.value
            counter.incr = random.nextInt(counter.max)+1
            counter.decr = random.nextInt(counter.max)+1
            editor.tileMapContainer.tiles += ((x,y) -> gate)
        }
    }

    def run(world:WorldServer):Unit = {
        val source = editor(world,false)
        fixture(source)
        val packet = new PacketCustom("PRVerification",1)
        source.writeDesc(packet)
        val rawBytes = packet.readableBytes()
        assert(rawBytes > 32767,"Large IC fixture did not exceed the reported packet limit")
        val proxy = packet.toPacket()
        val encodedBytes = proxy.payload().readableBytes()
        println(s"LARGE_IC_PACKET: ${source.tileMapContainer.tiles.size} tiles, $rawBytes raw bytes, $encodedBytes encoded bytes")
        val incoming = new PacketCustom(proxy.payload().copy())
        val restored = editor(world,true)
        restored.readDesc(incoming)
        assert(restored.size == source.size && restored.name == source.name)
        assert(restored.tileMapContainer.tiles.size == source.tileMapContainer.tiles.size)
        for (position,tile) <- source.tileMapContainer.tiles do {
            val before = tile.asInstanceOf[SequentialGateICTile].getLogic[Counter]
            val after = restored.tileMapContainer.tiles(position).asInstanceOf[SequentialGateICTile].getLogic[Counter]
            assert((before.value,before.max,before.incr,before.decr,before.currentValue) ==
                (after.value,after.max,after.incr,after.decr,after.currentValue),"Large IC description lost counter state")
        }
        val gateStack = mrtjp.projectred.integration.GateDefinition.ICGate.makeStack
        val blueprint = new net.minecraft.item.ItemStack(mrtjp.projectred.ProjectRedFabrication.itemICBlueprint)
        val chip = new net.minecraft.item.ItemStack(mrtjp.projectred.ProjectRedFabrication.itemICChip)
        val mask = new net.minecraft.item.ItemStack(mrtjp.projectred.ProjectRedFabrication.itemPhotomaskSet)
        val die = new net.minecraft.item.ItemStack(mrtjp.projectred.ProjectRedFabrication.itemValidDie)
        ItemICBlueprint.saveICToGate(source.tileMapContainer,gateStack)
        ItemICBlueprint.saveTileMap(source.tileMapContainer,blueprint)
        ItemICBlueprint.copyIC(blueprint,chip)
        ItemICBlueprint.copyIC(blueprint,mask)
        ItemICBlueprint.copyIC(blueprint,die)
        for stack <- Seq(gateStack,blueprint,chip,mask,die) do {
            val original = stack.getTagCompound.copy()
            val buffer = new PacketBuffer(Unpooled.buffer())
            try {
                buffer.writeItemStack(stack)
                println(s"LARGE_IC_ITEM: ${stack.getItem.getRegistryName}, ${buffer.readableBytes()} encoded bytes")
                val decoded = buffer.readItemStack()
                assert(net.minecraft.item.ItemStack.areItemStacksEqual(stack,decoded),"Large IC item NBT changed in transit")
                assert(stack.getTagCompound == original && stack.getTagCompound.hasKey("tilemap",10),
                    "Network encoding changed persistent IC item NBT")
                assert(ItemICBlueprint.loadTileMap(decoded).tiles.size == 3844,"Received IC item lost its tile map")
            } finally buffer.release()
            val carried = new mrtjp.projectred.transportation.pneumatics.part.PneumaticTubePayload(stack)
            val carrierPacket = new PacketCustom("PRVerification",3)
            carried.writeDesc(carrierPacket)
            val carrierInput = new PacketCustom(carrierPacket.copy())
            val received = new mrtjp.projectred.transportation.pneumatics.part.PneumaticTubePayload
            received.readDesc(carrierInput)
            assert(net.minecraft.item.ItemStack.areItemStacksEqual(stack,received.getItemStack),
                "Pneumatic payload description lost large IC data")
            carrierInput.release(); carrierPacket.release()
        }
        val player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world)
        val held = player.getHeldItemMainhand
        try {
            player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,gateStack)
            val gate = new ICGatePart
            gate.preparePlacement(player,new net.minecraft.util.math.BlockPos(16,80,8),1,gateStack.getItemDamage)
            val desc = new PacketCustom("PRVerification",2)
            gate.writeDesc(desc)
            val clientGate = new ICGatePart
            val encodedDesc = desc.toPacket()
            val decodedDesc = new PacketCustom(encodedDesc.payload().copy())
            clientGate.readDesc(decodedDesc)
            assert(net.minecraft.item.ItemStack.areItemStacksEqual(gateStack,clientGate.getItem),
                "Placed IC description lost its item NBT")
            val saved = new net.minecraft.nbt.NBTTagCompound
            gate.save(saved)
            assert(saved.getCompoundTag("itemTag") == gateStack.getTagCompound,
                "Placed IC save changed the persistent NBT format")
            val loadedGate = new ICGatePart
            loadedGate.load(saved)
            assert(loadedGate.getLogicIC.tmap.tiles.size == 3844,"Saved IC gate lost its circuit")
            assert(net.minecraft.item.ItemStack.areItemStacksEqual(gateStack,loadedGate.getItem))
            decodedDesc.release()
            encodedDesc.payload().release()
            desc.release()
        } finally player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,held)
        incoming.release()
        proxy.payload().release()
        packet.release()
        checkCodecBounds()
    }

    private def checkCodecBounds():Unit = {
        import mrtjp.projectred.core.CompressedNBT
        assert(CompressedNBT.encode(null) == null && CompressedNBT.decode(null) == null)
        val small = new net.minecraft.nbt.NBTTagCompound
        small.setString("legacy","unchanged")
        assert((CompressedNBT.encode(small) eq small) && (CompressedNBT.decode(small) eq small))
        val invalid = new net.minecraft.nbt.NBTTagCompound
        invalid.setByteArray("projectred:compressed_nbt",Array[Byte](1,2,3))
        var rejected = false
        try CompressedNBT.decode(invalid)
        catch { case _:java.io.IOException => rejected = true }
        assert(rejected,"Malformed compressed IC data was accepted")
        val bytes = new java.io.ByteArrayOutputStream
        val gzip = new java.util.zip.GZIPOutputStream(bytes)
        gzip.write(new Array[Byte](4*1024*1024+1))
        gzip.close()
        invalid.setByteArray("projectred:compressed_nbt",bytes.toByteArray)
        rejected = false
        try CompressedNBT.decode(invalid)
        catch { case _:java.io.IOException => rejected = true }
        assert(rejected,"Compressed IC expansion exceeded its size limit")
    }
}
