/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import java.math.MathContext

import codechicken.lib.colour.EnumColour
import codechicken.lib.data.MCDataInput
import codechicken.lib.gui.GuiDraw
import codechicken.lib.render.CCRenderState
import codechicken.lib.render.pipeline.ColourMultiplier
import codechicken.lib.texture.TextureUtils
import codechicken.lib.vec.uv.{UVScale, UVTranslation}
import com.mojang.realmsclient.gui.ChatFormatting
import mrtjp.core.gui.*
import mrtjp.core.vec.{Point, Rect, Size}
import mrtjp.projectred.core.LegacyKeys
import mrtjp.projectred.fabrication.ICComponentStore.*
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.{Side, SideOnly}
import org.lwjgl.input.{Keyboard, Mouse}
import org.lwjgl.opengl.GL11

import scala.jdk.CollectionConverters.*
import scala.collection.immutable.ListMap
import scala.collection.mutable.{ListBuffer as MListBuffer}

class ICToolsetNode extends TNode
{
    var opSet = Seq.empty[TileEditorOp]
    var title = ""
    var buttonSize = Size(16, 16)
    var buttonGap = 1

    var opSelectDelegate = {(_:TileEditorOp) => ()}

    private var focused = false
    private var popupOpen = false
    private var popupOrigin = Point(0,-18)
    def isPopupOpen:Boolean = popupOpen
    def closePopup():Unit = hideSubTools()
    private var buttonOpMap = ListMap.empty[ButtonNode, TileEditorOp]

    private var leadingButton:ButtonNode = null
    private var groupButton:ButtonNode = null

    def setup(): Unit =
    {
        for op <- opSet do {
            val b = createButtonFor(op)
            b.size = buttonSize
            b.hidden = true
            addChild(b)
            buttonOpMap += b -> op
        }

        placePopup(popupOrigin)

        leadingButton = buttonOpMap.head._1

        groupButton = new IconButtonNode {
            override def drawButton(mouseover:Boolean) = {
                val op = buttonOpMap(leadingButton)
                op.renderImage(CCRenderState.instance(), position.x+2, position.y+2, size.width-4, size.height-4)
            }
        }
        groupButton.size = buttonSize
        groupButton.tooltipBuilder = {_ += buttonOpMap(leadingButton).getOpName}
        groupButton.clickDelegate = {() =>
            val wasOpen = popupOpen
            leadingButton.clickDelegate()
            if !wasOpen then unhideSubTools()
        }
        addChild(groupButton)
    }

    private def buttonClicked(op:TileEditorOp, button:ButtonNode): Unit =
    {
        setFocused()
        opSelectDelegate(op)
        parent.children.collect {
            case t:ICToolsetNode if t != this => t
        }.foreach(_.setUnfocused())
        leadingButton.mouseoverLock = false
        leadingButton = button
        leadingButton.mouseoverLock = true
        hideSubTools()
    }

    def setUnfocused(): Unit =
    {
        if focused then hideSubTools()
        focused = false
        groupButton.mouseoverLock = false
    }

    def setFocused(): Unit =
    {
        focused = true
        groupButton.mouseoverLock = true
    }

    private def unhideSubTools(): Unit =
    {
        popupOpen = buttonOpMap.size > 1
        pushZTo(if popupOpen then 2 else 0)
        if buttonOpMap.size > 1 then for b <- buttonOpMap.keys do
            b.hidden = false
    }

    private def hideSubTools(): Unit =
    {
        popupOpen = false
        pushZTo(0)
        if buttonOpMap.size > 1 then for b <- buttonOpMap.keys do
            b.hidden = true
    }

    def placePopup(origin:Point):Unit = {
        popupOrigin = origin
        for (button,i) <- buttonOpMap.keys.zipWithIndex do
            button.position = origin+Point(i%4*(buttonSize.width+buttonGap),i/4*(buttonSize.height+buttonGap))
    }

    override def drawBack_Impl(mouse:Point,frame:Float):Unit = if popupOpen then {
        val columns = math.min(4,buttonOpMap.size)
        val rows = (buttonOpMap.size+3)/4
        GuiLib.drawGuiBox(position.x+popupOrigin.x-2,position.y+popupOrigin.y-2,
            columns*(buttonSize.width+buttonGap)+3,rows*(buttonSize.height+buttonGap)+3,0)
    }

    private def createButtonFor(op:TileEditorOp) =
    {
        val b = new IconButtonNode {
            override def drawButton(mouseover:Boolean): Unit = {
                op.renderImage(CCRenderState.instance(), position.x+2, position.y+2, size.width-4, size.height-4)
            }
        }
        b.tooltipBuilder = {_ += op.getOpName}
        b.clickDelegate = {() => buttonClicked(op, b)}
        b
    }

    def pickOp(op:TileEditorOp): Unit =
    {
        setUnfocused()
        buttonOpMap.find(_._2 == op) match {
            case Some((b, _)) => b.clickDelegate()
            case _ =>
        }
    }

    override def drawFront_Impl(mouse:Point, rframe:Float): Unit =
    {
        if title.nonEmpty && groupButton.rayTest(parent.convertPointTo(mouse, this)) then {
            import ChatFormatting.*
            translateToScreen()
            val Point(mx, my) = parent.convertPointToScreen(mouse)
            GuiDraw.drawMultiLineTip(mx+12, my-32, Seq(AQUA.toString+ITALIC.toString+title).asJava)
            translateFromScreen()
        }
    }
}

class RenameICNode(oldName:String) extends TNode
{
    def this() = this("")

    val size = Size(100, 60)
    override def frame = Rect(position, size)

    var completionDelegate = {() => ()}

    private var textbox:SimpleTextboxNode = scala.compiletime.uninitialized

    def getName = {
        val t = textbox.text
        if t.isEmpty then "untitled" else t
    }

    override def traceHit(absPoint:Point) = true //overtake the screen

    override def onAddedToParent_Impl(): Unit =
    {
        val close = new MCButtonNode
        close.size = Size(8, 8)
        close.position = Point(4, 4)
        close.clickDelegate = {() => removeFromParent()}
        addChild(close)

        val fin = new MCButtonNode
        fin.size = Size(40, 15)
        fin.position = Point(size.width/2-fin.size.width/2, size.height-fin.size.height-4)
        fin.clickDelegate = {() =>
            removeFromParent()
            completionDelegate()
        }
        fin.text = "confirm"
        addChild(fin)

        textbox = new SimpleTextboxNode
        textbox.size = Size(80, 14)
        textbox.position = Point(size/2-textbox.size/2)
        textbox.phantom = "untitled"
        textbox.text = oldName
        addChild(textbox)
    }

    override def frameUpdate_Impl(mouse:Point, rframe:Float): Unit =
    {
        if !parent.asInstanceOf[GuiICWorkbench].tile.hasBP then
            removeFromParent()
    }

    override def drawBack_Impl(mouse:Point, rframe:Float): Unit =
    {
        GuiDraw.drawGradientRect(0, 0, parent.frame.width, parent.frame.height, -1072689136, -804253680)
        GuiLib.drawGuiBox(position.x, position.y, size.width, size.height, 0)
    }

    override def keyPressed_Impl(c:Char, keycode:Int, consumed:Boolean) = {
        if !consumed then keycode match {
            case mrtjp.projectred.core.LegacyKeys.KEY_ESCAPE =>
                removeFromParent()
                true
            case mrtjp.projectred.core.LegacyKeys.KEY_RETURN =>
                removeFromParent()
                completionDelegate()
                true
            case _ => false
        }
        else false
    }
}

class NewICNode extends TNode
{
    val size = Size(100, 120)
    override def frame = Rect(position, size)

    var sizerRenderSize = Size(50, 50)
    var sizerRenderOffset = Point(0, -16)
    var sizerRenderGap = 2

    var maxBoardSize = Size(4, 4)
    var selectedBoardSize = Size(1, 1)

    var completionDelegate = {() => ()}

    var outsideColour = EnumColour.LIGHT_GRAY.argb
    var insideColour = EnumColour.CYAN.argb(0x88)
    var hoverColour = EnumColour.BLUE.argb

    def getName =
    {
        val t = textbox.text
        if t.isEmpty then "untitled" else t
    }

    private var textbox:SimpleTextboxNode = scala.compiletime.uninitialized
    private var sizerMap:Map[(Int, Int), Rect] = scala.compiletime.uninitialized

    private def sizerPos = position+Point(size/2-sizerRenderSize/2)+sizerRenderOffset

    private def calcSizerRects =
    {
        val p = sizerPos
        val d = sizerRenderSize/maxBoardSize

        val rcol = GuiLib.createGrid(p.x, p.y, maxBoardSize.width, maxBoardSize.height, d.width, d.height)
        val icol = GuiLib.createGrid(0, 0, maxBoardSize.width, maxBoardSize.height, 1, 1)
        val zcol = rcol.zip(icol)

        var rects = Map[(Int, Int), Rect]()
        for ((px, py), (x, y)) <- zcol do
        {
            val rect = Rect(Point(px, py)+sizerRenderGap/2, d-sizerRenderGap/2)
            rects += (x, y) -> rect
        }
        rects
    }

    private def getMouseoverPos(mouse:Point) = sizerMap.find(_._2 `contains` mouse) match
    {
        case Some(((x, y), r)) => Point(x, y)
        case None => null
    }

    override def traceHit(absPoint:Point) = true

    override def onAddedToParent_Impl(): Unit =
    {
        sizerMap = calcSizerRects

        val close = new MCButtonNode
        close.size = Size(8, 8)
        close.position = Point(4, 4)
        close.clickDelegate = {() => removeFromParent()}
        addChild(close)

        val fin = new MCButtonNode
        fin.size = Size(40, 15)
        fin.position = Point(size.width/2-fin.size.width/2, size.height-fin.size.height-4)
        fin.clickDelegate = {() =>
            removeFromParent()
            completionDelegate()
        }
        fin.text = "start"
        addChild(fin)

        textbox = new SimpleTextboxNode
        textbox.size = Size(80, 14)
        textbox.position = Point(size/2-textbox.size/2)+Point(0, 24)
        textbox.phantom = "untitled"
        addChild(textbox)
    }

    override def frameUpdate_Impl(mouse:Point, rframe:Float): Unit =
    {
        if !parent.asInstanceOf[GuiICWorkbench].tile.hasBP then
            removeFromParent()
    }

    override def drawBack_Impl(mouse:Point, rframe:Float): Unit =
    {
        GuiDraw.drawGradientRect(0, 0, parent.frame.width, parent.frame.height, -1072689136, -804253680)
        GuiLib.drawGuiBox(position.x, position.y, size.width, size.height, 0)

        val mousePos = getMouseoverPos(mouse)
        for ((x, y), rect) <- sizerMap do {
            GuiDraw.drawRect(rect.x, rect.y, rect.width, rect.height, outsideColour)

            if x <= selectedBoardSize.width-1 && y <= selectedBoardSize.height-1 then
                GuiDraw.drawRect(rect.x, rect.y, rect.width, rect.height, insideColour)

            if mousePos != null && x == mousePos.x && y == mousePos.y then
                GuiDraw.drawRect(rect.midX-2, rect.midY-2, 4, 4, hoverColour)
        }
    }

    override def drawFront_Impl(mouse:Point, rframe:Float): Unit =
    {
        if rayTest(mouse) then {
            val mousePos = getMouseoverPos(mouse)
            if mousePos != null then {
                translateToScreen()
                val Point(mx, my) = parent.convertPointToScreen(mouse)
                import scala.jdk.CollectionConverters.*
                GuiDraw.drawMultiLineTip(mx+12, my-12, Seq(s"${(mousePos.x+1)*16} x ${(mousePos.y+1)*16}").asJava)
                translateFromScreen()
            }
        }
    }

    override def mouseClicked_Impl(p:Point, button:Int, consumed:Boolean):Boolean =
    {
        if !consumed then {
            val mousePos = getMouseoverPos(p)
            if mousePos != null then {
                selectedBoardSize = Size(mousePos+1)
                return true
            }
        }
        false
    }

    override def keyPressed_Impl(c:Char, keycode:Int, consumed:Boolean) =
    {
        if !consumed then keycode match {
            case mrtjp.projectred.core.LegacyKeys.KEY_ESCAPE =>
                removeFromParent()
                true
            case mrtjp.projectred.core.LegacyKeys.KEY_RETURN =>
                removeFromParent()
                completionDelegate()
                true
            case _ => false
        }
        else false
    }
}

class InfoNode extends TNode
{
    val size = Size(18, 18)
    override def frame = Rect(position, size)

    private def getTile = parent.asInstanceOf[GuiICWorkbench].tile

    override def drawBack_Impl(mouse:Point, rframe:Float): Unit =
    {
        TextureUtils.changeTexture(GuiICWorkbench.controls)

        if !getTile.hasBP || getTile.getIC.isEmpty then
            Gui.drawModalRectWithCustomSizedTexture(position.x, position.y, 330, 0, size.width, size.height, 512, 512)
    }

    override def drawFront_Impl(mouse:Point, rframe:Float): Unit =
    {
        val text =
            if !getTile.hasBP then
                "Lay down a blueprint on the workbench."
            else if getTile.getIC.isEmpty then
                "Blueprint is empty. Redraw it."
            else ""
        if text.nonEmpty && rayTest(mouse) then {
            translateToScreen()
            val Point(mx, my) = parent.convertPointToScreen(mouse)
            import scala.jdk.CollectionConverters.*
            GuiDraw.drawMultiLineTip(mx+12, my-12, Seq(text).asJava)
            translateFromScreen()
        }
    }
}
