package mrtjp.projectred.fabrication

import codechicken.lib.colour.EnumColour
import codechicken.lib.data.MCDataInput
import codechicken.lib.gui.GuiDraw
import codechicken.lib.texture.TextureUtils
import mrtjp.core.gui.*
import mrtjp.core.vec.{Point,Rect,Size}
import mrtjp.projectred.ProjectRedFabrication
import mrtjp.projectred.fabrication.lithography.{ProcessNode,WaferType,YieldCalculator}
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.{GlStateManager,RenderHelper}
import net.minecraft.client.resources.I18n
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.{Side,SideOnly}
import scala.jdk.CollectionConverters.*

class GuiICWorkbench(val tile:TileICWorkbench) extends NodeGui(408,222) {
    var pref:TileMapEditorNode = null
    var toolSets = Seq.empty[ICToolsetNode]
    var activeTab = 1
    private val clip = new ClipNode
    private val pan = new PanNode
    private val palette = new TNode {}
    private val tabs = scala.collection.mutable.ArrayBuffer.empty[ButtonNode]
    private val nameBox = new SimpleTextboxNode
    private val compileButton = new MCButtonNode
    private val automaticButton = new MCButtonNode
    private val interact = new MCButtonNode
    private val hints = new CheckBoxNode
    private val minus = new MCButtonNode
    private val plus = new MCButtonNode
    private val logMode = new MCButtonNode
    var showCompileTree = false
    private var boardSize = Size.zeroSize
    private def bodyX = 19
    private def bodyWidth = size.width-bodyX-85
    private def sideX = bodyX+bodyWidth+1
    private def diagnosticWidth = (bodyWidth-14)*198/290
    private def tr(key:String,args:AnyRef*):String = I18n.format("gui.projectred.workbench."+key,args*)
    private def text(value:String,x:Int,y:Int,width:Int,colour:Int):Unit =
        GuiDraw.drawString(Minecraft.getMinecraft.fontRenderer.trimStringToWidth(value,math.max(1,width)),x,y,colour,false)

    def selectTab(index:Int):Unit = {
        activeTab = math.max(0,math.min(2,index))
        toolSets.foreach(_.closePopup())
    }
    def centerOn(point:Point):Unit = {
        selectTab(1)
        pref.position = Point(pan.size/2)-Point(((point.x+0.5D)*8*pref.scale).toInt,((point.y+0.5D)*8*pref.scale).toInt)
    }

    override def onAddedToParent_Impl():Unit = {
        clip.position = Point(bodyX+7,18)
        addChild(clip)
        pan.clampSlack = 35
        pan.dragTestFunction = {() => net.minecraft.client.gui.GuiScreen.isShiftKeyDown}
        clip.addChild(pan)
        pref = new TileMapEditorNode(tile.editor)
        pref.zPosition = -0.01
        pref.opPickDelegate = { op =>
            toolSets.foreach(_.pickOp(op))
            if op == null then pref.currentOp = null
        }
        pan.addChild(pref)
        palette.zPosition = 2
        addChild(palette)
        import TileEditorOpDefs.*
        def tools(title:String,operations:Seq[TileEditorOpDefs.OpDef]):Unit = {
            val group = new ICToolsetNode
            group.title = title
            group.position = Point(toolSets.size%3*23,toolSets.size/3*22)
            group.opSet = operations.map(_.getOp)
            group.opSelectDelegate = { op => pref.currentOp = op }
            group.setup()
            palette.addChild(group)
            toolSets :+= group
        }
        def range(title:String,first:TileEditorOpDefs.OpDef,last:TileEditorOpDefs.OpDef):Unit =
            tools(title,(first.getID to last.getID).map(TileEditorOpDefs(_)))
        tools(tr("erase"),Seq(Erase))
        tools(tr("inputs"),Seq(SimpleIO))
        tools(tr("wire"),Seq(AlloyWire))
        range(tr("insulated"),WhiteInsulatedWire,BlackInsulatedWire)
        range(tr("bundled"),NeutralBundledCable,BlackBundledCable)
        tools(tr("io"),Seq(BundledIO,AnalogIO))
        tools(tr("logic"),Seq(ORGate,NORGate,NOTGate,ANDGate,NANDGate,XORGate,XNORGate,BufferGate,MultiplexerGate))
        tools(tr("timing"),Seq(PulseFormerGate,RepeaterGate,TimerGate,SequencerGate,StateCellGate))
        tools(tr("latches"),Seq(SRLatchGate,ToggleLatchGate,TransparentLatchGate))
        tools(tr("bus"),Seq(BundledBusIO))
        tools(tr("other"),Seq(RandomizerGate,CounterGate,SynchronizerGate))

        for (uv,index) <- Seq((420,1),(420,16),(420,31)).zipWithIndex do {
            val button = new IconButtonNode {
                override def drawButton(mouseover:Boolean):Unit = {
                    TextureUtils.changeTexture(GuiICWorkbench.background)
                    Gui.drawModalRectWithCustomSizedTexture(position.x+2,position.y+4,uv._1,uv._2,14,14,512,512)
                }
            }
            button.position = Point(0,4+index*25)
            button.size = Size(18,23)
            button.tooltipBuilder = { list => list += tr(Seq("info","edit","diagnostics")(index)) }
            button.clickDelegate = {() => selectTab(index)}
            tabs += button
            addChild(button)
        }
        nameBox.size = Size(100,14)
        nameBox.text = tile.editor.name
        nameBox.textReturnDelegate = {() =>
            tile.editor.tileMapContainer.name = nameBox.text.take(64)
            tile.sendICNameToServer()
        }
        addChild(nameBox)
        interact.size = Size(68,16)
        interact.text = tr("interact")
        interact.clickDelegate = {() => pref.currentOp = null; toolSets.foreach(_.setUnfocused())}
        addChild(interact)
        compileButton.text = tr("compile")
        compileButton.clickDelegate = {() => if tile.controller.canTriggerCompile then tile.sendCompileToServer()}
        addChild(compileButton)
        automaticButton.clickDelegate = {() => if tile.controller.autoCompileAvailable then tile.sendAutoCompileToggle()}
        addChild(automaticButton)
        logMode.clickDelegate = {() => showCompileTree = !showCompileTree}
        addChild(logMode)
        hints.state = true
        hints.clickDelegate = {() => pref.showTooltips = hints.state}
        addChild(hints)
        minus.size = Size(14,14); minus.text = "-"; minus.clickDelegate = {() => pref.decScale()}
        plus.size = Size(14,14); plus.text = "+"; plus.clickDelegate = {() => pref.incScale()}
        addChild(minus); addChild(plus)
        addChild(new CompileLog)
        relayout()
    }

    private def relayout():Unit = {
        val desiredWidth = math.min(408,math.max(260,width-8))
        val previous = pan.size
        size = Size(desiredWidth,222)
        xSize = size.width; ySize = size.height
        guiLeft = (width-size.width)/2; guiTop = (height-size.height)/2
        position = Point(guiLeft,guiTop)
        clip.position = Point(bodyX+7,18)
        clip.size = Size(bodyWidth-14,197)
        pan.size = clip.size
        if previous == Size.zeroSize then pref.position = Point(pan.size/2-pref.size/2)
        else pref.position += Point((pan.size-previous)/2)
        palette.position = Point(sideX+8,80)
        for group <- toolSets do group.placePopup(Point(bodyX+bodyWidth-82-palette.position.x-group.position.x,
            48-palette.position.y-group.position.y))
        nameBox.position = Point(bodyX+12,38)
        nameBox.size = Size(bodyWidth-24,16)
        interact.position = Point(sideX+8,28)
        hints.position = Point(sideX+8,164)
        minus.position = Point(sideX+8,196); plus.position = Point(sideX+62,196)
        compileButton.position = Point(bodyX+diagnosticWidth+14,108)
        compileButton.size = Size(bodyWidth-diagnosticWidth-22,16)
        automaticButton.position = Point(bodyX+diagnosticWidth+14,132)
        automaticButton.size = compileButton.size
        logMode.position = Point(bodyX+diagnosticWidth+14,184)
        logMode.size = compileButton.size
    }

    override def frameUpdate_Impl(mouse:Point,frame:Float):Unit = {
        if size.width != math.min(408,math.max(260,width-8)) then relayout()
        val active = tile.hasBP && tile.editor.nonEmpty
        clip.hidden = !active || activeTab != 1
        pref.navigationEnabled = !clip.hidden
        palette.hidden = clip.hidden
        interact.hidden = clip.hidden
        hints.hidden = clip.hidden; minus.hidden = clip.hidden; plus.hidden = clip.hidden
        nameBox.hidden = !active || activeTab != 0
        if !nameBox.focused && nameBox.text != tile.editor.name then nameBox.text = tile.editor.name
        compileButton.hidden = !active || activeTab != 2
        automaticButton.hidden = compileButton.hidden
        logMode.hidden = compileButton.hidden
        logMode.text = tr(if showCompileTree then "problems" else "compile_tree")
        automaticButton.text = tr(if tile.controller.autoCompileEnabled then "auto_on" else "auto_off")
        for (tab,index) <- tabs.zipWithIndex do tab.mouseoverLock = activeTab == index
        if boardSize != tile.editor.size then {
            boardSize = tile.editor.size
            pref.position = Point(pan.size/2-pref.frame.size/2)
        }
    }

    private def blit(x:Int,y:Int,u:Int,v:Int,uw:Int,vh:Int,w:Int,h:Int):Unit =
        Gui.drawScaledCustomSizeModalRect(x,y,u,v,uw,vh,w,h,512,512)
    private def panel(texture:ResourceLocation):Unit = {
        TextureUtils.changeTexture(texture)
        blit(bodyX,0,0,0,7,222,7,222)
        blit(bodyX+7,0,7,0,290,222,bodyWidth-14,222)
        blit(bodyX+bodyWidth-7,0,297,0,7,222,7,222)
        TextureUtils.changeTexture(GuiICWorkbench.background)
        blit(sideX,0,305,0,84,222,84,222)
    }
    override def drawBack_Impl(mouse:Point,frame:Float):Unit = {
        GlStateManager.color(1,1,1,1)
        panel(if activeTab == 0 then GuiICWorkbench.information else if activeTab == 2 then GuiICWorkbench.compilation else GuiICWorkbench.background)
        text(if tile.hasBP then tile.editor.name else tr("title"),bodyX+8,6,bodyWidth-16,EnumColour.GRAY.argb)
        text(tr("tools"),sideX+8,6,68,EnumColour.GRAY.argb)
        if !tile.hasBP || tile.editor.isEmpty then {
            TextureUtils.changeTexture(GuiICWorkbench.background)
            val x = bodyX+(bodyWidth-132)/2
            val y = 65
            blit(x,y,0,222,132,92,132,92)
            text(tr("place_blueprint"),x+6,y+6,120,EnumColour.GRAY.argb)
            RenderHelper.enableGUIStandardItemLighting()
            try {
                Minecraft.getMinecraft.getRenderItem.renderItemAndEffectIntoGUI(new ItemStack(ProjectRedFabrication.itemICBlueprint),x+58,y+24)
                Minecraft.getMinecraft.getRenderItem.renderItemAndEffectIntoGUI(new ItemStack(ProjectRedFabrication.icBlock),x+58,y+64)
            } finally RenderHelper.disableStandardItemLighting()
        } else if activeTab == 0 then drawInformation()
        else if activeTab == 2 then {
            val x = bodyX+diagnosticWidth+14
            val w = bodyWidth-diagnosticWidth-22
            val logger = tile.editor.simEngineContainer.logger
            text(tr("errors",Int.box(logger.getErrors.size)),x,32,w,EnumColour.GRAY.argb)
            text(tr("warnings",Int.box(logger.getWarnings.size)),x,49,w,EnumColour.GRAY.argb)
            text(tr("state_"+tile.controller.state),x,73,w,EnumColour.GRAY.argb)
            val progress=if tile.controller.totalSteps == 0 then 0 else tile.controller.completedSteps*100/tile.controller.totalSteps
            text(tr("progress",Int.box(progress)),x,89,w,EnumColour.GRAY.argb)
            if !tile.controller.autoCompileAvailable then text(tr("auto_limit"),x,158,w,EnumColour.GRAY.argb)
        }
        if !clip.hidden then {
            text(tr("tooltips"),sideX+25,168,54,EnumColour.WHITE.argb)
            text(tr("scale"),sideX+8,182,68,EnumColour.WHITE.argb)
            text(f"${pref.scale}%.1f",sideX+28,199,31,EnumColour.WHITE.argb)
        } else if tile.hasBP then {
            text(tr("state_"+tile.controller.state),sideX+8,28,68,EnumColour.WHITE.argb)
        }
    }
    private def drawInformation():Unit = {
        val logger = tile.editor.simEngineContainer.logger
        val x = bodyX+12
        val w = bodyWidth-24
        text(tr("name"),x,22,w,EnumColour.GRAY.argb)
        val rows = Seq(tr("dimensions",Int.box(tile.editor.size.width),Int.box(tile.editor.size.height)),
            tr("components",Int.box(tile.editor.tileMapContainer.tiles.size)),
            tr("errors",Int.box(logger.getErrors.size)),tr("warnings",Int.box(logger.getWarnings.size)))
        for (row,i) <- rows.zipWithIndex do text(row,x,66+i*16,w,EnumColour.GRAY.argb)
        val yieldCalc = new YieldCalculator(tile.editor.size,ProcessNode.PROCESS_64NM,WaferType.ROUGH_WAFER)
        text(tr("yield"),x,144,w,EnumColour.GRAY.argb)
        text(tr("dies",Int.box(yieldCalc.getCappedDieCount)),x,164,w,EnumColour.GRAY.argb)
        text(tr("success",f"${yieldCalc.getSingleLayerYield*100}%.1f"),x,182,w,EnumColour.GRAY.argb)
    }
    private class CompileLog extends TNode {
        private var scroll = 0
        private var visible = Vector.empty[(Seq[Point],String,Int)]
        override def frame:Rect = Rect(Point(bodyX+7,18),Size(diagnosticWidth,197))
        override def frameUpdate_Impl(mouse:Point,frame:Float):Unit = hidden = activeTab != 2 || !tile.hasBP || tile.editor.isEmpty
        private def entries = {
            val logger = tile.editor.simEngineContainer.logger
            logger.getErrors.iterator.map((p,s) => (p,s,EnumColour.RED.argb)) ++
                logger.getWarnings.iterator.map((p,s) => (p,s,EnumColour.YELLOW.argb)) ++
                logger.getRuntimeFlags.iterator.map((p,s) => (p,s,EnumColour.MAGENTA.argb))
        }
        private def treeEntries:Iterator[(Seq[Point],String,Int)] = {
            def nodes(children:java.util.List[engine.log.ICCompilerLog.CompileTreeNode],depth:Int):Iterator[(Seq[Point],String,Int)] =
                children.asScala.iterator.flatMap { node =>
                    val points=node.tileCoords.asScala.toSeq.map(p => Point(p.x+tile.editor.size.width/2,p.z+tile.editor.size.height/2))
                    val label=tr("step_"+node.step.name().toLowerCase(java.util.Locale.ROOT))
                    val message="  "*depth+label+s" (${node.registerIds.size()} / ${node.gateIds.size()})"
                    Iterator((points,message,EnumColour.WHITE.argb)) ++ nodes(node.children,depth+1)
                }
            nodes(tile.controller.compilerLog.getRootNodes,0)
        }
        override def drawBack_Impl(mouse:Point,partial:Float):Unit = {
            val logger = tile.editor.simEngineContainer.logger
            val count = if showCompileTree then tile.controller.compilerLog.getTotalSteps
                else logger.getErrors.size+logger.getWarnings.size+logger.getRuntimeFlags.size
            scroll = math.max(0,math.min(scroll,math.max(0,count-13)))
            visible = (if showCompileTree then treeEntries else entries).drop(scroll).take(13).toVector
            if visible.isEmpty then text(tr("no_problems"),frame.x+4,frame.y+4,frame.width-8,EnumColour.WHITE.argb)
            for ((points,message,colour),i) <- visible.zipWithIndex do {
                val prefix = points.headOption.map(p => s"${p.x},${p.y}: ").getOrElse("")
                text(prefix+message,frame.x+4,frame.y+4+i*14,frame.width-8,colour)
            }
        }
        override def mouseScrolled_Impl(p:Point,direction:Int,consumed:Boolean):Boolean = {
            if consumed || !frame.contains(p) || !rayTest(p) then false
            else { scroll = math.max(0,scroll-direction*3); true }
        }
        override def mouseClicked_Impl(p:Point,button:Int,consumed:Boolean):Boolean = {
            if consumed || button != 0 || !frame.contains(p) || !rayTest(p) then false
            else {
                visible.lift((p.y-frame.y-4)/14).flatMap(_._1.headOption).foreach(centerOn)
                true
            }
        }
        override def drawFront_Impl(mouse:Point,partial:Float):Unit = {
            if frame.contains(mouse) && rayTest(mouse) then visible.lift((mouse.y-frame.y-4)/14).foreach { entry =>
                translateToScreen()
                try {
                    val point = parent.convertPointToScreen(mouse)
                    GuiDraw.drawMultiLineTip(point.x+12,point.y-12,Seq(entry._2).asJava)
                } finally translateFromScreen()
            }
        }
    }
}

object GuiICWorkbench extends TGuiFactory {
    val background = new ResourceLocation("projectred:textures/gui/ic_workbench.png")
    val controls = new ResourceLocation("projectred:textures/gui/ic_workbench_controls.png")
    val information = new ResourceLocation("projectred:textures/gui/ic_workbench_info.png")
    val compilation = new ResourceLocation("projectred:textures/gui/ic_workbench_compile.png")
    override def getID = FabricationProxy.icWorkbenchGui
    @SideOnly(Side.CLIENT)
    override def buildGui(player:EntityPlayer,data:MCDataInput) = player.world.getTileEntity(data.readPos()) match {
        case tile:TileICWorkbench =>
            tile.editor.readDesc(data)
            tile.editor.worldTimeOffset = player.world.getTotalWorldTime-data.readLong()
            tile.controller.readDesc(data)
            new GuiICWorkbench(tile)
        case _ => null
    }
}
