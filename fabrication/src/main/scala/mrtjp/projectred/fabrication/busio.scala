package mrtjp.projectred.fabrication

import codechicken.lib.colour.EnumColour
import codechicken.lib.data.{MCDataInput,MCDataOutput}
import codechicken.lib.gui.GuiDraw
import mrtjp.core.gui.MCButtonNode
import mrtjp.core.vec.{Point,Size}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.client.gui.Gui
import net.minecraftforge.fml.relauncher.{Side,SideOnly}
import scala.collection.mutable.ListBuffer

/** Full-width bundled I/O, with a stable appended editor ID. */
class BundledBusIOGateTileLogic(tile:IOGateICTile) extends BundledIOGateTileLogic(tile) {
    var busMask=0
    override def save(tag:NBTTagCompound):Unit = { super.save(tag);tag.setShort("bus_mask",busMask.toShort) }
    override def load(tag:NBTTagCompound):Unit = { super.load(tag);busMask=tag.getShort("bus_mask")&65535 }
    override def writeDesc(out:MCDataOutput):Unit = { super.writeDesc(out);out.writeShort(busMask) }
    override def readDesc(in:MCDataInput):Unit = { super.readDesc(in);busMask=in.readUShort() }
    override def read(in:MCDataInput,key:Int):Unit = if key == 13 then busMask=in.readUShort() else super.read(in,key)
    def sendBusUpdate():Unit = gate.writeStreamOf(13).writeShort(busMask)
    override def getFreqName = "16-channel bus"
    @SideOnly(Side.CLIENT)
    override def createGui(gate:IOGateICTile):ICTileGui = new ICBusIOGui(gate)
    @SideOnly(Side.CLIENT)
    override def buildRolloverData(gate:IOGateICTile,buffer:ListBuffer[String]):Unit = {
        buffer += f"bus: 0x$busMask%04X"
        buffer += (if gate.shape == 0 then "input" else "output")
    }
}

@SideOnly(Side.CLIENT)
class ICBusIOGui(override val gate:IOGateICTile) extends ICTileGui(gate) with TGateGui {
    size=Size(160,100)
    private val logic=gate.getLogic[BundledBusIOGateTileLogic]
    private val mode=new MCButtonNode
    mode.position=Point(8,78);mode.size=Size(44,15);mode.text="I/O"
    mode.clickDelegate=()=>gate.sendClientPacket(_.writeByte(1))
    addChild(mode)
    for channel <- 0 until 16 do {
        val button=new MCButtonNode {
            override def drawButton(mouseover:Boolean):Unit = {
                val on=(logic.busMask&(1<<channel)) != 0
                Gui.drawRect(position.x+2,position.y+2,position.x+size.width-2,position.y+size.height-2,
                    if on then EnumColour.values()(channel).argb else 0xFF303030)
                GuiDraw.drawStringC(channel.toString,position.x+size.width/2,position.y+4,if on then 0xFFFFFFFF else 0xFFAAAAAA)
            }
        }
        button.position=Point(60+(channel%4)*23,12+(channel/4)*19)
        button.size=Size(22,17)
        button.tooltipBuilder = list => list += EnumColour.values()(channel).name
        button.clickDelegate=()=>gate.sendClientPacket(_.writeByte(7).writeByte(channel))
        addChild(button)
    }
}
