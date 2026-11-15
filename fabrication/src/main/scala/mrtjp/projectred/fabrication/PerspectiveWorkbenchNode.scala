package mrtjp.projectred.fabrication

import codechicken.lib.gui.GuiDraw
import codechicken.lib.render.CCRenderState
import codechicken.lib.vec.{Vector3,Scale,Translation}
import mrtjp.core.gui.TNode
import mrtjp.core.vec.{Point,Rect,Size}
import mrtjp.projectred.core.LegacyKeys.*
import mrtjp.projectred.fabrication.engine.BaseTile
import mrtjp.projectred.fabrication.engine.zones.*
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager as GL
import org.lwjgl.BufferUtils
import org.lwjgl.input.{Keyboard,Mouse}
import org.lwjgl.opengl.{GL11,Display}
import scala.jdk.CollectionConverters.*

/** 1.21.1 camera and interaction semantics hosted by the 1.12 node/GL APIs. */
class TileMapEditorNode(editor:ICTileMapEditor) extends TNode {
    val camera=new WorkbenchCamera
    var viewportSize=Size(1,1)
    def size:Size=viewportSize
    override def frame:Rect=Rect(position,viewportSize)
    private var selectedOp:TileEditorOp=null
    private val wirePath=scala.collection.mutable.LinkedHashSet.empty[Point]
    def currentOp:TileEditorOp=selectedOp
    def currentOp_=(op:TileEditorOp):Unit={selectedOp=op;pressed = -1;startWorld=null;wirePath.clear()}
    var opPickDelegate:TileEditorOp=>Unit=_=>()
    var showTooltips=true
    var navigationEnabled=false
    def scale:Double=camera.position.y-camera.layer
    private var lastMouse=Point.zeroPoint
    private var pressed = -1
    private var startWorld:Vector3=null
    private var cachedOld:ICTile=null
    private var cachedTag:net.minecraft.nbt.NBTTagCompound=null
    private var hoveredModel:BaseTile=null
    private val view=new ModernDesign(editor)
    view.context.getStateMachine.setPresentation(
        ()=>editor.externalSimulationActive!=null && editor.externalSimulationActive(),
        ()=>editor.getTotalSimTimeClient)
    private def owner=editor.network.asInstanceOf[TileICWorkbench]
    private def world(p:Point):Vector3=camera.worldAt(p.x-position.x,p.y-position.y)
    private def point(p:Vector3):Point=Point(math.floor(p.x).toInt+editor.size.width/2,math.floor(p.z).toInt+editor.size.height/2)
    private def valid(p:Point):Boolean=editor.validLayer(camera.layer) && p.x>=0 && p.y>=0 && p.x<editor.size.width && p.y<editor.size.height
    private def onLayer[T](body: => T):T=editor.withLayer(camera.layer)(body)
    def tileScreenPoint(p:Point):Point={
        val (x,y)=camera.screenAt(p.x-editor.size.width/2+0.5,p.y-editor.size.height/2+0.5)
        convertPointToScreen(Point(x.toInt,y.toInt))
    }
    def worldScreenPoint(x:Double,z:Double):Point={val (px,py)=camera.screenAt(x,z);convertPointToScreen(Point(px.toInt,py.toInt))}
    def focusAt(p:Point,layer:Int=0):Unit={
        camera.setLayer(layer)
        val x=p.x-editor.size.width/2;val z=p.y-editor.size.height/2
        camera.focus(x,z,x+1,z+1)
    }
    def resetView():Unit={camera.width=math.max(1,size.width);camera.height=math.max(1,size.height);camera.focus(-editor.size.width/2D,-editor.size.height/2D,editor.size.width/2D,editor.size.height/2D)}
    def setLayer(layer:Int):Unit={camera.setLayer(layer);if startWorld!=null then startWorld.y=layer+0.125D;cachedOld=null}
    def incScale():Unit=camera.scroll(camera.worldAt(size.width/2,size.height/2),1)
    def decScale():Unit=camera.scroll(camera.worldAt(size.width/2,size.height/2),-1)
    override def update_Impl():Unit=if navigationEnabled && Display.isActive then {
        val dx=(if Keyboard.isKeyDown(KEY_D) then 0.05 else 0D)-(if Keyboard.isKeyDown(KEY_A) then 0.05 else 0D)
        val dz=(if Keyboard.isKeyDown(KEY_S) then 0.05 else 0D)-(if Keyboard.isKeyDown(KEY_W) then 0.05 else 0D)
        if dx!=0 || dz!=0 then camera.pan(dx,dz)
    }
    override def frameUpdate_Impl(mouse:Point,partial:Float):Unit={
        camera.width=math.max(1,size.width);camera.height=math.max(1,size.height)
        camera.tick(System.currentTimeMillis(),editor.size.width,editor.size.height)
        for popup<-children.collect{case p:ICTileGui=>p} do {
            val part=popup.part
            if part.editor==null || part.tileMap==null || !editor.validLayer(part.tileMap.layerIndex) ||
                (editor.withLayer(part.tileMap.layerIndex){editor.getTile(part.pos)} ne part) then popup.removeFromParent()
        }
        lastMouse=mouse
        updateHovered(mouse)
    }
    private def updateHovered(mouse:Point):Unit={
        if !navigationEnabled || !frame.contains(mouse) || !rayTest(mouse) then hoveredModel=null
        else {
            val p=point(world(mouse))
            val old=if valid(p) then onLayer{editor.getTile(p)} else null
            if old==null then {cachedOld=null;hoveredModel=null}
            else {
                val tag=new net.minecraft.nbt.NBTTagCompound;old.save(tag)
                old match {case gate:IOGateICTile=>tag.setInteger("presentation_io",editor.simEngineContainer.iostate(gate.rotation));case _=>}
                if (old ne cachedOld) || tag!=cachedTag then {
                    cachedOld=old;cachedTag=tag
                    hoveredModel=view.convert(old,presentation=true).orNull
                    if hoveredModel!=null then hoveredModel.bindMap(view.context.getTileMap,view.coordinate(p,camera.layer))
                }
            }
        }
    }
    def zoneScreenPoint(p:Point,index:Int):Point={
        val old=onLayer{editor.getTile(p)}
        val tile=view.convert(old,presentation=true).get
        tile.bindMap(view.context.getTileMap,view.coordinate(p,camera.layer))
        val bounds=tile.getInteractionZones()(index).getBounds
        worldScreenPoint(p.x-editor.size.width/2D+(bounds.min.x+bounds.max.x)/2,p.y-editor.size.height/2D+(bounds.min.z+bounds.max.z)/2)
    }
    private def zoneAt(p:Vector3):Int=if hoveredModel==null then -1 else {
        val local=p.copy().subtract(hoveredModel.getPos.x,hoveredModel.getPos.y,hoveredModel.getPos.z)
        hoveredModel.getInteractionZones.indexWhere(_.getBounds.contains(local))
    }
    override def mouseClicked_Impl(p:Point,button:Int,consumed:Boolean):Boolean={
        if consumed || !frame.contains(p) || !rayTest(p) || !navigationEnabled then false
        else if button==0 || button==1 then {lastMouse=p;updateHovered(p);pressed=button;startWorld=world(p);wirePath.clear();if button==0 then selectWire(startWorld);true}
        else if button==2 then {doPickOp();true} else false
    }
    override def mouseReleased_Impl(p:Point,button:Int,consumed:Boolean):Boolean={
        if pressed!=button || startWorld==null then return false
        lastMouse=p;updateHovered(p)
        val end=world(p);val a=point(startWorld);val b=point(end)
        if currentOp!=null && button==0 && editor.validLayer(camera.layer) then currentOp match {
            case wire:OpWire => selectWire(end);if wirePath.nonEmpty then owner.sendWirePath(camera.layer,wire.id,wirePath.toSeq)
            case gate:OpGateCommons if valid(a) =>
                val rotation=if ICGateDefinition(gate.meta).gateType==ICTileDefs.IOGate.id then onLayer{gate.findRot(editor,a,a)} else placementRotation(startWorld,end)
                owner.sendGatePlacement(camera.layer,gate.id,a,rotation)
            case _:TileEditorOpErase =>
                val minX=math.max(0,math.min(a.x,b.x));val maxX=math.min(editor.size.width-1,math.max(a.x,b.x))
                val minY=math.max(0,math.min(a.y,b.y));val maxY=math.min(editor.size.height-1,math.max(a.y,b.y))
                if minX<=maxX && minY<=maxY then onLayer{editor.sendOpUse(currentOp,Point(minX,minY),Point(maxX,maxY))}
            case _ =>
        }
        else if currentOp==null && a==b && valid(a) then {
            val zone=zoneAt(end)
            if zone>=0 && zone==zoneAt(startWorld) then {
                val native=onLayer{editor.getTile(a)}
                if button==1 && zone==0 && native.isInstanceOf[IOGateICTile] && native.asInstanceOf[IOGateICTile].subID==ICGateDefinition.IOBundledBus.ordinal then {
                    val popup=new ICBusIOGui(native.asInstanceOf[IOGateICTile])
                    popup.position=Point(4,4);popup.linePointerCalc=()=>Point(8,8)
                    addChild(popup);popup.pushZTo(4)
                } else owner.sendZoneClick(camera.layer,a,zone,button==0)
            }
        }
        pressed = -1;startWorld=null;wirePath.clear();true
    }
    private def selectWire(p:Vector3):Unit=if currentOp.isInstanceOf[OpWire] then {
        val target=point(p)
        if valid(target) && target.x>0 && target.y>0 && target.x<editor.size.width-1 && target.y<editor.size.height-1 && onLayer{editor.getTile(target)==null} then wirePath+=target
    }
    override def mouseDragged_Impl(p:Point,button:Int,time:Long,consumed:Boolean):Boolean={
        if pressed!=button then false else {lastMouse=p;if button==0 then selectWire(world(p));true}
    }
    private def placementRotation(start:Vector3,end:Vector3):Int={
        val dx=end.x-math.floor(start.x)-.5;val dz=end.z-math.floor(start.z)-.5
        if math.hypot(dx,dz)<1.2 then 0 else {
            var angle=math.acos(math.max(-1D,math.min(1D,dx/math.hypot(dx,dz))))
            if dz>0 then angle=2*math.Pi-angle
            angle=(angle+math.Pi/4)%(2*math.Pi)
            (((2*math.Pi-angle)/(2*math.Pi)*4).toInt+2)%4
        }
    }
    override def mouseScrolled_Impl(p:Point,direction:Int,consumed:Boolean):Boolean={
        if consumed || !frame.contains(p) || !rayTest(p) then false
        else {camera.scroll(world(p),direction);true}
    }
    override def keyPressed_Impl(c:Char,key:Int,consumed:Boolean):Boolean={
        if consumed || !navigationEnabled then return false
        key match {
            case KEY_ESCAPE if pressed==0 => pressed = -1;startWorld=null;wirePath.clear();true
            case KEY_ESCAPE if currentOp!=null => opPickDelegate(null);true
            case 200 => setLayer(camera.layer+1);true
            case 208 => setLayer(camera.layer-1);true
            case KEY_W|KEY_A|KEY_S|KEY_D => true
            case KEY_R|KEY_F if hoveredModel!=null && currentOp==null =>
                val glfw=if key==KEY_R then 82 else 70
                val flags=(if GuiScreen.isShiftKeyDown then 1 else 0)|(if GuiScreen.isCtrlKeyDown then 2 else 0)
                if hoveredModel.getInteractionZones.exists(_.canRespondToKey(glfw,flags)) then {
                    owner.sendZoneKey(camera.layer,point(world(lastMouse)),glfw,flags);true
                } else false
            case _ if key==mcInst.gameSettings.keyBindPickBlock.getKeyCode => doPickOp();true
            case _ => false
        }
    }
    def doPickOp():Unit={val p=point(world(lastMouse));if valid(p) then onLayer{val tile=editor.getTile(p);opPickDelegate(if tile==null then null else tile.getPickOp)}}
    override def drawBack_Impl(mouse:Point,partial:Float):Unit={
        if editor.isEmpty || size.width<=0 || size.height<=0 then return
        val root=getRoot;val origin=parent.convertPointToScreen(position)
        val priorDepth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);val priorCull=GL11.glIsEnabled(GL11.GL_CULL_FACE)
        val priorLight=GL11.glIsEnabled(GL11.GL_LIGHTING);val priorBlend=GL11.glIsEnabled(GL11.GL_BLEND)
        val priorTexture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D)
        val binding=BufferUtils.createIntBuffer(4);GL11.glGetIntegerv(GL11.GL_TEXTURE_BINDING_2D,binding)
        val sx=root.mc.displayWidth.toDouble/root.width;val sy=root.mc.displayHeight.toDouble/root.height
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        GL.matrixMode(GL11.GL_PROJECTION);GL.pushMatrix();GL.loadIdentity()
        val near=0.2;val edge=near*math.tan(camera.fov/2)
        GL11.glFrustum(-edge*size.width/size.height,edge*size.width/size.height,-edge,edge,near,20)
        GL.matrixMode(GL11.GL_MODELVIEW);GL.pushMatrix();GL.loadIdentity()
        try {
            GL11.glViewport(math.round(origin.x*sx).toInt,math.round((root.height-origin.y-size.height)*sy).toInt,
                math.round(size.width*sx).toInt,math.round(size.height*sy).toInt)
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT)
            GL.rotate(90,1,0,0);GL.translate(-camera.position.x,-camera.position.y,-camera.position.z)
            GL.enableDepth();GL.depthFunc(GL11.GL_LEQUAL);GL.disableCull();GL.disableLighting()
            GL.enableBlend();GL.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA)
            val ccrs=CCRenderState.instance()
            for layer <- camera.layer-1 to camera.layer+1 do {
                val alpha=255-(255*math.min(math.abs(layer-camera.layerAnimation.vector.y),1)).toInt
                if alpha>0 then {
                    ICComponentStore.viewportAlpha=alpha
                    val map=editor.rootTileMapContainer.extraLayers.get(layer).getOrElse(if layer==0 then editor.rootTileMapContainer else null)
                    val transform=new Scale(editor.size.width,1,editor.size.height).`with`(new Translation(-editor.size.width/2D,layer,-editor.size.height/2D))
                    PrefboardRenderer.render(ccrs,editor.rootTileMapContainer,transform,false)
                    if map!=null then {
                        // The legacy IC sprites are composited quads, unlike the newer solid gate models.
                        GL.disableDepth()
                        RenderICTileMap.renderTiles(ccrs,map,transform.`with`(new Translation(0,.125,0)),false,partial)
                        GL.enableDepth()
                    }
                }
            }
            ICComponentStore.viewportAlpha = -1
            if editor.externalSimulationActive==null || !editor.externalSimulationActive() then drawEditGrid()
            for problem<-owner.controller.compilerLog.getProblems.asScala;at<-problem.positions().asScala if at.y==camera.layer do {
                outline(new codechicken.lib.vec.Cuboid6(at.x,at.y+.14,at.z,at.x+1,at.y+.15,at.z+1),
                    if problem.severity==engine.log.CompileProblemSeverity.ERROR then 0xFF3333FF else 0xFFFF33FF)
            }
            if currentOp==null && hoveredModel!=null && frame.contains(mouse) && rayTest(mouse) then {
                val at=world(mouse);val hit=zoneAt(at)
                val selected=if pressed>=0 && startWorld!=null then zoneAt(startWorld) else -1
                for (zone,i) <- hoveredModel.getInteractionZones.zipWithIndex do drawZone(zone,hoveredModel.getPos.x,hoveredModel.getPos.y,hoveredModel.getPos.z,i==hit,i==selected)
            } else if currentOp!=null && editor.validLayer(camera.layer) then drawPreview(ccrs,world(mouse),partial)
        } finally {
            ICComponentStore.viewportAlpha = -1
            GL.popMatrix();GL.matrixMode(GL11.GL_PROJECTION);GL.popMatrix();GL.matrixMode(GL11.GL_MODELVIEW)
            GL11.glPopAttrib()
            if priorDepth then {GL.disableDepth();GL.enableDepth()} else {GL.enableDepth();GL.disableDepth()}
            if priorCull then {GL.disableCull();GL.enableCull()} else {GL.enableCull();GL.disableCull()}
            if priorLight then {GL.disableLighting();GL.enableLighting()} else {GL.enableLighting();GL.disableLighting()}
            if priorBlend then {GL.disableBlend();GL.enableBlend()} else {GL.enableBlend();GL.disableBlend()}
            if priorTexture then {GL.disableTexture2D();GL.enableTexture2D()} else {GL.enableTexture2D();GL.disableTexture2D()}
            GL.bindTexture(0);GL.bindTexture(binding.get(0));GL.color(1,1,1,1)
        }
    }
    private def drawEditGrid():Unit={
        val minX = -editor.size.width/2D;val maxX=editor.size.width/2D
        val minZ = -editor.size.height/2D;val maxZ=editor.size.height/2D
        val bottom=camera.layer-3D;val top=camera.layer+2D
        GL.disableTexture2D();GL.color(.7F,.85F,1,.15F);GL11.glBegin(GL11.GL_LINES)
        for x<-0 to editor.size.width;z<-Seq(minZ,maxZ) do {GL11.glVertex3d(minX+x,bottom,z);GL11.glVertex3d(minX+x,top,z)}
        for z<-0 to editor.size.height;x<-Seq(minX,maxX) do {GL11.glVertex3d(x,bottom,minZ+z);GL11.glVertex3d(x,top,minZ+z)}
        for y<-camera.layer-3 to camera.layer+2 do {
            GL11.glVertex3d(minX,y,minZ);GL11.glVertex3d(maxX,y,minZ)
            GL11.glVertex3d(minX,y,maxZ);GL11.glVertex3d(maxX,y,maxZ)
            GL11.glVertex3d(minX,y,minZ);GL11.glVertex3d(minX,y,maxZ)
            GL11.glVertex3d(maxX,y,minZ);GL11.glVertex3d(maxX,y,maxZ)
        }
        GL11.glEnd();GL.enableTexture2D();GL.color(1,1,1,1)
    }
    private def drawPreview(ccrs:CCRenderState,at:Vector3,partial:Float):Unit={
        val p=point(if pressed==0 && startWorld!=null then startWorld else at)
        if !valid(p) then return
        val end=point(at)
        onLayer{
            val preview:ICTile=currentOp match {
                case gate:OpGateCommons =>
                    val part=ICTile.createTile(ICGateDefinition(gate.meta).gateType).asInstanceOf[GateICTile]
                    val rot=if ICGateDefinition(gate.meta).gateType==ICTileDefs.IOGate.id then gate.findRot(editor,p,p) else if pressed==0 then placementRotation(startWorld,at) else 0
                    part.preparePlacement(rot,gate.meta);part
                case wire:OpWire => wire.createPart
                case _ => null
            }
            GL.disableDepth()
            if preview!=null && editor.getTile(p)==null then preview.renderDynamic(ccrs,new Translation(p.x-editor.size.width/2D,camera.layer+.125,p.y-editor.size.height/2D),false,partial)
            if currentOp.isInstanceOf[OpWire] then for selected<-wirePath if selected!=p do {
                val part=currentOp.asInstanceOf[OpWire].createPart
                part match {case wire:TConnectableICTile=>wire.connMap=(0 until 4).filter(r=>wirePath.contains(selected.offset(r))).map(r=>1<<r).sum.toByte;case _=>}
                part.renderDynamic(ccrs,new Translation(selected.x-editor.size.width/2D,camera.layer+.125,selected.y-editor.size.height/2D),false,partial)
            }
            GL.enableDepth()
            val box=new codechicken.lib.vec.Cuboid6(p.x-editor.size.width/2D,camera.layer+0.13,p.y-editor.size.height/2D,p.x-editor.size.width/2D+1,camera.layer+0.14,p.y-editor.size.height/2D+1)
            outline(box,0xFFFFFFFF)
        }
    }
    private def outline(box:codechicken.lib.vec.Cuboid6,rgba:Int,width:Float=2):Unit={
        GL11.glLineWidth(width)
        GL.disableTexture2D();GL.color((rgba>>>24&255)/255F,(rgba>>>16&255)/255F,(rgba>>>8&255)/255F,(rgba&255)/255F)
        GL11.glBegin(GL11.GL_LINE_LOOP)
        GL11.glVertex3d(box.min.x,box.max.y,box.min.z);GL11.glVertex3d(box.max.x,box.max.y,box.min.z)
        GL11.glVertex3d(box.max.x,box.max.y,box.max.z);GL11.glVertex3d(box.min.x,box.max.y,box.max.z)
        GL11.glEnd();GL.enableTexture2D();GL.color(1,1,1,1)
    }
    private def drawZone(zone:InteractionZone,x:Int,y:Int,z:Int,hover:Boolean,selected:Boolean):Unit={
        val box=zone.getBounds.copy().add(x,y,z)
        val colour=zone.highlightColor()
        GL.disableTexture2D();GL.color((colour>>>24&255)/255F,(colour>>>16&255)/255F,(colour>>>8&255)/255F,if selected && hover then 1F else if selected then .5F else if hover then .25F else .125F)
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glVertex3d(box.min.x,box.max.y+.001,box.min.z);GL11.glVertex3d(box.min.x,box.max.y+.001,box.max.z)
        GL11.glVertex3d(box.max.x,box.max.y+.001,box.max.z);GL11.glVertex3d(box.max.x,box.max.y+.001,box.min.z)
        GL11.glEnd();GL.enableTexture2D();GL.color(1,1,1,1)
        outline(box,zone.outlineColor(),zone.lineWidth().toFloat)
        if zone.icon()!=null then {
            val name=if zone.icon()==ZoneIcons.ROTATE then "rotate" else "reflect"
            codechicken.lib.texture.TextureUtils.changeTexture(new net.minecraft.util.ResourceLocation("projectred:textures/gui/ic_"+name+".png"))
            GL11.glBegin(GL11.GL_QUADS)
            GL11.glTexCoord2d(0,0);GL11.glVertex3d(box.min.x,box.max.y+.004,box.min.z)
            GL11.glTexCoord2d(0,1);GL11.glVertex3d(box.min.x,box.max.y+.004,box.max.z)
            GL11.glTexCoord2d(1,1);GL11.glVertex3d(box.max.x,box.max.y+.004,box.max.z)
            GL11.glTexCoord2d(1,0);GL11.glVertex3d(box.max.x,box.max.y+.004,box.min.z)
            GL11.glEnd()
        }
        val label=if zone.label()!=null then zone.label().getString else ""
        if label.nonEmpty then {
            GL.pushMatrix()
            GL.translate((box.min.x+box.max.x)/2,box.max.y+.003,(box.min.z+box.max.z)/2)
            GL.rotate(-90,1,0,0)
            val scale=math.min((box.max.x-box.min.x)/math.max(1,mcInst.fontRenderer.getStringWidth(label)),(box.max.z-box.min.z)/9)*.85
            GL.scale(scale,-scale,scale)
            mcInst.fontRenderer.drawString(label,-mcInst.fontRenderer.getStringWidth(label)/2F,-4F,0xFFFFFFFF,false)
            GL.popMatrix()
        }
    }
    override def drawFront_Impl(mouse:Point,partial:Float):Unit={
        if !showTooltips || !frame.contains(mouse) || !rayTest(mouse) || hoveredModel==null then return
        val data=new java.util.ArrayList[ZoneText]
        val zone=zoneAt(world(mouse))
        if zone>=0 then hoveredModel.getInteractionZones()(zone).buildToolTip(data)
        else hoveredModel.buildToolTip(data)
        if !data.isEmpty && data.get(0).getString.startsWith("gui.projectred.ic.") && cachedOld!=null then data.set(0,ZoneText.literal(cachedOld.getPartName))
        if !data.isEmpty then {
            translateToScreen()
            try {val p=parent.convertPointToScreen(mouse);GuiDraw.drawMultiLineTip(p.x+12,p.y-12,data.asScala.map(_.getString).asJava)}
            finally translateFromScreen()
        }
    }
}
