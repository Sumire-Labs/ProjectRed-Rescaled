/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import codechicken.lib.block.property.unlisted.{UnlistedBooleanProperty, UnlistedIntegerProperty}
import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.model.bakery.generation.IBakery
import codechicken.lib.model.bakery.{IBakeryProvider, ModelBakery, SimpleBlockRenderer}
import codechicken.lib.packet.PacketCustom
import codechicken.lib.vec.Rotation
import codechicken.lib.vec.uv.{MultiIconTransformation, UVTransformation}
import mrtjp.core.block.*
import mrtjp.core.gui.NodeContainer
import mrtjp.core.world.WorldLib
import mrtjp.projectred.ProjectRedFabrication
import mrtjp.projectred.api.IScrewdriver
import mrtjp.projectred.fabrication.ItemICBlueprint.*
import net.minecraft.block.material.Material
import net.minecraft.block.state.BlockStateContainer.Builder
import net.minecraft.block.state.{BlockStateContainer, IBlockState}
import net.minecraft.client.renderer.texture.{TextureAtlasSprite, TextureMap}
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.util.{EnumFacing, ResourceLocation}
import net.minecraft.world.IBlockAccess
import net.minecraftforge.common.property.IExtendedBlockState

import scala.collection.mutable.{Set as MSet}

class BlockICMachine(bakery:IBakery) extends MultiTileBlock(Material.ROCK) with IBakeryProvider
{
    setHardness(2)
    setCreativeTab(ProjectRedFabrication.tabFabrication)

    override def createBlockState(): BlockStateContainer = new Builder(this).add(MultiTileBlock.TILE_INDEX)
            .add(BlockICMachine.UNLISTED_ROTATION_PROPERTY)
            .add(BlockICMachine.UNLISTED_SIDE_PROPERTY)
            .add(BlockICMachine.UNLISTED_HAS_BP_PROPERTY)
            .build()

    override def getExtendedState(state: IBlockState, world: IBlockAccess, pos: BlockPos) = ModelBakery.handleExtendedState(state.asInstanceOf[IExtendedBlockState], world, pos)

    override def getBakery = bakery
}

object BlockICMachine
{
    val UNLISTED_ROTATION_PROPERTY = new UnlistedIntegerProperty("rotation")
    val UNLISTED_SIDE_PROPERTY = new UnlistedIntegerProperty("side")
    val UNLISTED_HAS_BP_PROPERTY = new UnlistedBooleanProperty("hasBP")
}

abstract class TileICMachine extends MTBlockTile with TTileOrient
{
    override def getBlock = ProjectRedFabrication.icBlock

    override def onBlockPlaced(side:Int, player:EntityPlayer, stack:ItemStack): Unit =
    {
        setSide(0)
        setRotation(if doesRotate then (Rotation.getSidedRotation(player, side)+2)%4 else 0)
    }

    override def writeDesc(out:MCDataOutput): Unit =
    {
        super.writeDesc(out)
        out.writeByte(orientation)
    }

    override def readDesc(in:MCDataInput): Unit =
    {
        super.readDesc(in)
        orientation = in.readByte
    }

    override def save(tag:NBTTagCompound): Unit =
    {
        tag.setByte("rot", orientation)
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        orientation = tag.getByte("rot")
    }

    override def read(in:MCDataInput, key:Int) = key match
    {
        case 1 =>
            orientation = in.readByte()
            markRender()
        case _ => super.read(in, key)
    }

    override def onBlockActivated(player:EntityPlayer, actside:Int):Boolean =
    {
        val held = player.getHeldItemMainhand
        if doesRotate && !held.isEmpty && held.getItem.isInstanceOf[IScrewdriver]
                && held.getItem.asInstanceOf[IScrewdriver].canUse(player, held) then
        {
            if world.isRemote then return true
            val old = rotation
            while { setRotation((rotation+1)%4) ; old != rotation && !isRotationAllowed(rotation)} do ()
            if old != rotation then sendOrientUpdate()
            world.notifyNeighborsRespectDebug(getPos, getBlock, false)
            onBlockRotated()
            held.getItem.asInstanceOf[IScrewdriver].damageScrewdriver(player, held)
            return true
        }
        false
    }

    def doesRotate = true

    def isRotationAllowed(rot:Int) = true

    def onBlockRotated(): Unit ={}

    def sendOrientUpdate(): Unit =
    {
        writeStream(1).writeByte(orientation).sendToChunk(this)
    }
}

class TileICWorkbench extends TileICMachine with TICTileEditorNetwork
{
    val editor = new ICTileMapEditor(this)
    val controller = new WorkbenchController(this)

    var hasBP = false
    var watchers = MSet[EntityPlayer]()
    private var modernEvents:PacketCustom = null
    private val discardedEvents = new codechicken.lib.data.MCDataOutputWrapper(java.io.OutputStream.nullOutputStream())
    def modernEventStream(key:Int):MCDataOutput = {
        if watchers.isEmpty then discardedEvents
        else {
            if modernEvents == null then modernEvents=writeStream(10)
            modernEvents.writeByte(key)
            modernEvents
        }
    }
    private def flushModernEvents():Unit = if modernEvents != null then {
        modernEvents.writeByte(255)
        modernEvents.compress()
        watchers.foreach(modernEvents.sendToPlayer)
        modernEvents=null
    }

    override def save(tag:NBTTagCompound): Unit =
    {
        super.save(tag)
        val ictag = new NBTTagCompound
        editor.save(ictag)
        tag.setTag("ictag", ictag)
        tag.setBoolean("bp", hasBP)
        controller.save(tag)
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        super.load(tag)
        editor.load(tag.getCompoundTag("ictag"),compileLegacy=false)
        hasBP = tag.getBoolean("bp")
        if hasBP && editor.isEmpty then editor.tileMapContainer.size = mrtjp.core.vec.Size(16,16)
        controller.load(tag)
    }

    override def writeDesc(out:MCDataOutput): Unit =
    {
        super.writeDesc(out)
        out.writeBoolean(hasBP)
        controller.writeDesc(out)
    }

    override def readDesc(in:MCDataInput): Unit =
    {
        super.readDesc(in)
        hasBP = in.readBoolean()
        controller.readDesc(in)
    }

    override def read(in:MCDataInput, key:Int) = key match
    {
        case 1 =>
            hasBP = in.readBoolean()
            markRender()
        case 2 =>
            editor.readDesc(in)
            controller.readDesc(in)
        case 7 => controller.readDesc(in)
        case 9 => controller.readLog(in)
        case 10 => controller.readLogEvents(in)
        case 3 => readTileStream(in)
        case 4 => readICStream(in)
        case _ => super.read(in, key)
    }

    override def readFromClient(in:MCDataInput, key:Int, player:EntityPlayer):Unit = {
        if !watchers.contains(player) || !mrtjp.projectred.core.TileActions.mayEdit(this, player) then return
        key match {
            case 4 if hasBP =>
                readClientICStream(in)
                if editor.simNeedsRefresh then controller.markEdited()
                markDirty()
            case 5 if hasBP =>
                val name = in.readString().take(64)
                val width = in.readUByte()
                val height = in.readUByte()
                if Set(16,32,48,64).contains(width) && Set(16,32,48,64).contains(height) then {
                    editor.clear()
                    editor.tileMapContainer.name = name
                    editor.tileMapContainer.size = mrtjp.core.vec.Size(width, height)
                    controller.markEdited()
                    markDirty()
                    sendICDesc()
                }
            case 6 if hasBP =>
                editor.tileMapContainer.name = in.readString().take(64)
                markDirty()
                sendICDesc()
            case 7 if hasBP => controller.requestCompile()
            case 8 if hasBP => controller.toggleAutomatic()
            case 10 if hasBP =>
                val mode=in.readUByte()
                val layer=in.readByte().toInt
                val point=mrtjp.core.vec.Point(in.readUByte(),in.readUByte())
                if mode==0 then controller.interact(layer,point,in.readVarInt(),in.readBoolean())
                else if mode==1 then controller.interact(layer,point,-1,false,in.readInt(),in.readInt())
            case 11 if hasBP =>
                val layer=in.readByte().toInt;val operation=in.readVarInt();val overwrite=in.readBoolean();val count=in.readVarInt()
                if editor.validLayer(layer) && TileEditorOpDefs.values.isDefinedAt(operation) && count>=0 && count<=editor.size.width*editor.size.height then
                    TileEditorOp.getOperation(operation) match {
                        case wire:OpWire => editor.withLayer(layer){
                            for _<-0 until count do {
                                val p=mrtjp.core.vec.Point(in.readUByte(),in.readUByte())
                                if p.x>0 && p.y>0 && p.x<editor.size.width-1 && p.y<editor.size.height-1 && (editor.getTile(p)==null || overwrite) then {
                                    if editor.getTile(p)!=null then editor.removeTile(p)
                                    editor.setTile(p,wire.createPart)
                                }
                            }
                        }
                        case _ =>
                    }
                if editor.simNeedsRefresh then controller.markEdited()
            case 12 if hasBP =>
                val layer=in.readByte().toInt;val operation=in.readVarInt();val p=mrtjp.core.vec.Point(in.readUByte(),in.readUByte());val rotation=in.readUByte().toInt
                if editor.validLayer(layer) && TileEditorOpDefs.values.isDefinedAt(operation) && rotation<4 then
                    TileEditorOp.getOperation(operation) match {
                        case op:OpGateCommons if !Set(ICGateDefinition.DecRandomizer.ordinal,ICGateDefinition.NullCell.ordinal,ICGateDefinition.InvertCell.ordinal,ICGateDefinition.BufferCell.ordinal).contains(op.meta) => editor.withLayer(layer){
                            if p.x>=0 && p.y>=0 && p.x<editor.size.width && p.y<editor.size.height && op.canPlace(editor,p) && editor.getTile(p)==null then {
                                val part=ICTile.createTile(ICGateDefinition(op.meta).gateType).asInstanceOf[GateICTile]
                                part.preparePlacement(if part.isInstanceOf[IIOGateTile] then op.findRot(editor,p,p) else rotation,op.meta)
                                editor.setTile(p,part)
                            }
                        }
                        case _ =>
                    }
                if editor.simNeedsRefresh then controller.markEdited()
            case _ =>
        }
    }

    private def sendHasBPUpdate(): Unit =
    {
        writeStream(1).writeBoolean(hasBP).sendToChunk(this)
    }

    def sendICDesc(): Unit ={ sendICDesc(watchers.toSeq*) }

    def sendModernLog(players:EntityPlayer*):Unit = {
        flushModernEvents()
        val recipients=if players.isEmpty then watchers.toSeq else players.toSeq
        if recipients.nonEmpty then {
            val out=writeStream(9)
            controller.writeLog(out)
            out.compress()
            recipients.foreach(out.sendToPlayer)
        }
    }

    private def sendICDesc(players:EntityPlayer*): Unit =
    {
        if players.nonEmpty then
        {
            val out = writeStream(2)
            editor.writeDesc(out)
            controller.writeDesc(out)
            for p <- players do
                out.sendToPlayer(p)
        }
    }

    def sendNewICToServer(ic:ICTileMapEditor): Unit =
    {
        val stream = writeStream(5)
        stream.writeString(ic.name).writeByte(ic.size.width).writeByte(ic.size.height)
        stream.sendToServer()
    }

    def sendICNameToServer(): Unit =
    {
        writeStream(6).writeString(editor.tileMapContainer.name).sendToServer()
    }

    def sendCompileToServer():Unit = writeStream(7).sendToServer()
    def sendAutoCompileToggle():Unit = writeStream(8).sendToServer()
    def sendZoneClick(layer:Int,point:mrtjp.core.vec.Point,index:Int,left:Boolean):Unit =
        writeStream(10).writeByte(0).writeByte(layer).writeByte(point.x).writeByte(point.y).writeVarInt(index).writeBoolean(left).sendToServer()
    def sendZoneKey(layer:Int,point:mrtjp.core.vec.Point,key:Int,flags:Int):Unit =
        writeStream(10).writeByte(1).writeByte(layer).writeByte(point.x).writeByte(point.y).writeInt(key).writeInt(flags).sendToServer()
    def sendWirePath(layer:Int,operation:Int,points:Seq[mrtjp.core.vec.Point],overwrite:Boolean=false):Unit={
        val out=writeStream(11).writeByte(layer).writeVarInt(operation).writeBoolean(overwrite).writeVarInt(points.size)
        points.foreach(p=>out.writeByte(p.x).writeByte(p.y));out.sendToServer()
    }
    def sendGatePlacement(layer:Int,operation:Int,p:mrtjp.core.vec.Point,rotation:Int):Unit=
        writeStream(12).writeByte(layer).writeVarInt(operation).writeByte(p.x).writeByte(p.y).writeByte(rotation).sendToServer()

    override def getIC = editor
    override def getEditorWorld = world
    override def isRemote = world.isRemote
    override def isSimulationRunning = controller.state == 3
    override def markSave(): Unit ={markDirty()}

    override def createTileStream() = writeStream(3)
    override def createEditorStream() = writeStream(4)
    override def sendTileStream(out:PacketCustom): Unit =
    {
        watchers.foreach(out.sendToPlayer)
    }
    override def sendEditorStream(out:PacketCustom): Unit =
    {
        if world.isRemote then out.sendToServer()
        else watchers.foreach(out.sendToPlayer)
    }

    override def updateServer(): Unit =
    {
        super.updateServer()
        flushICStream()
        flushTileStream()
        controller.tick()
        flushModernEvents()
    }
    override def updateClient(): Unit =
    {
        super.updateClient()
        flushICStream() //ic stream is bi-directional
    }

    override def onBlockActivated(player:EntityPlayer, side:Int):Boolean =
    {
        if super.onBlockActivated(player, side) then return true
        if !world.isRemote then {
            import ItemICBlueprint.*
            val held = player.getHeldItemMainhand
            if !hasBP && !held.isEmpty && held.getItem.isInstanceOf[ItemICBlueprint] then {
                if hasICInside(held) then {
                    editor.clear()
                    loadTileMap(editor.tileMapContainer, held)
                    sendICDesc()
                } else {
                    editor.clear()
                    editor.tileMapContainer.size = mrtjp.core.vec.Size(16,16)
                }
                held.shrink(1)
                hasBP = true
                controller.markEdited()
                sendHasBPUpdate()
            }
            else if hasBP && player.isSneaking then {
                val stack = new ItemStack(ProjectRedFabrication.itemICBlueprint)
                if editor.nonEmpty then {
                    saveTileMap(editor.tileMapContainer, stack)
                    saveFlags(stack, editor.simEngineContainer.logger)
                    ModernBlueprint.attach(stack,controller.modern)
                    editor.clear()
                    sendICDesc()
                }
                val p = pos.offset(EnumFacing.values()(1))
                val item = new EntityItem(world, p.getX+0.5, p.getY+0.20, p.getZ+0.5, stack)
                item.setPickupDelay(10)
                item.motionX = 0
                item.motionY = 0.15
                item.motionZ = 0
                world.spawnEntity(item)
                hasBP = false
                sendHasBPUpdate()
            } else {
                // Send edits to existing viewers before opening a new viewer's complete snapshot.
                flushICStream()
                flushTileStream()
                val nc = new mrtjp.projectred.core.TileContainer(this)
                nc.startWatchDelegate = playerStartWatch
                nc.stopWatchDelegate = playerStopWatch
                GuiICWorkbench.open(player, nc, {p =>
                    p.writePos(pos)
                    editor.writeDesc(p)
                    p.writeLong(controller.simTime)
                    controller.writeDesc(p)
                })
            }
        }
        true
    }

    override def onBlockRemoval(): Unit =
    {
        super.onBlockRemoval()
        if hasBP then {
            val stack = new ItemStack(ProjectRedFabrication.itemICBlueprint)
            if editor.nonEmpty then {
                saveTileMap(editor.tileMapContainer, stack)
                saveFlags(stack, editor.simEngineContainer.logger)
                ModernBlueprint.attach(stack,controller.modern)
            }
            WorldLib.dropItem(world, pos, stack)
        }
    }

    override def doesRotate = false

    def playerStartWatch(p:EntityPlayer): Unit =
    {
        flushModernEvents()
        watchers += p
        sendModernLog(p)
    }

    def playerStopWatch(p:EntityPlayer): Unit =
    {
        watchers -= p
    }
}

object RenderICWorkbench extends SimpleBlockRenderer
{
    import java.lang.{Boolean as JBool}

    import BlockICMachine.*
    import org.apache.commons.lang3.tuple.Triple

    var bottom:TextureAtlasSprite = scala.compiletime.uninitialized
    var side1:TextureAtlasSprite = scala.compiletime.uninitialized
    var side2:TextureAtlasSprite = scala.compiletime.uninitialized
    var sidebp1:TextureAtlasSprite = scala.compiletime.uninitialized
    var sidebp2:TextureAtlasSprite = scala.compiletime.uninitialized
    var top:TextureAtlasSprite = scala.compiletime.uninitialized
    var topBP:TextureAtlasSprite = scala.compiletime.uninitialized

    var iconT:UVTransformation = scala.compiletime.uninitialized
    var iconTBP:UVTransformation = scala.compiletime.uninitialized

    override def handleState(state:IExtendedBlockState, world: IBlockAccess, pos:BlockPos):IExtendedBlockState = world.getTileEntity(pos) match {
        case t:TileICWorkbench =>
            state.withProperty(UNLISTED_HAS_BP_PROPERTY, t.hasBP.asInstanceOf[JBool])

        case _ => state
    }

    override def getWorldTransforms(state:IExtendedBlockState) =
    {
        val hasBP = state.getValue(UNLISTED_HAS_BP_PROPERTY)
        Triple.of(0, 0, if hasBP then iconTBP else iconT)
    }

    override def getItemTransforms(stack:ItemStack) = Triple.of(0, 0, iconT)

    override def shouldCull() = true

    override def registerIcons(reg:TextureMap): Unit =
    {
        def register(s:String) = reg.registerSprite(new ResourceLocation("projectred:blocks/fabrication/icworkbench/"+s))

        bottom = register("bottom")
        top = register("top")
        topBP = register("topbp")
        side1 = register("side1")
        side2 = register("side2")
        sidebp1 = register("sidebp1")
        sidebp2 = register("sidebp2")

        iconT = new MultiIconTransformation(bottom, top, side1, side1, side2, side2)
        iconTBP = new MultiIconTransformation(bottom, topBP, sidebp1, sidebp1, sidebp2, sidebp2)

        RenderICTileMap.registerIcons(reg)
    }
}
