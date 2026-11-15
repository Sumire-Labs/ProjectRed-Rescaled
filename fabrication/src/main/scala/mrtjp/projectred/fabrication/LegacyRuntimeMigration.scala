package mrtjp.projectred.fabrication

import mrtjp.core.vec.Point
import mrtjp.projectred.fabrication.engine.{ICSimulationContainer,PRFabricationEngine}
import net.minecraft.nbt.NBTTagCompound
import scala.jdk.CollectionConverters.*

/** Converts semantic state, never old numeric register IDs, and keeps the original save for recovery. */
object LegacyRuntimeMigration {
    case class Converted(runtime:ICSimulationContainer)
    def convert(map:ICTileMapContainer,old:ICSimEngineContainer):Either[String,Converted] = try {
        if map.extraLayers.nonEmpty then return Left("Layered legacy data needs recompilation in Workbench")
        val editor=new ICTileMapEditor(null)
        val drawing=new NBTTagCompound;map.saveTiles(drawing)
        editor.rootTileMapContainer.loadTiles(drawing)
        val model=new ModernDesign(editor);model.rebuild();model.context.tileChanged=_=>()
        val compiler=model.context.getStateMachine
        compiler.startCompile();model.unsupported.foreach(compiler.getCompilerLog.addProblem)
        while compiler.getState==2 do compiler.tick(0)
        val fatal=compiler.getCompilerLog.getProblems.asScala.filter(p=>p.severity==engine.log.CompileProblemSeverity.ERROR &&
            !p.isInstanceOf[engine.log.NoInputsError] && !p.isInstanceOf[engine.log.NoOutputsError])
        if fatal.nonEmpty then return Left(fatal.take(4).map{
            case p:engine.log.UnsupportedLegacyTileError=>s"${p.legacyType} at ${p.coord}"
            case p=>s"${p.`type`} at ${p.positions().asScala.mkString(",")}"
        }.mkString("Workbench conversion needed: ","; ",""))
        val values=scala.collection.mutable.Map.empty[Int,Byte]
        val previous=old.simEngine
        require(previous!=null,"Legacy simulation is absent")
        def byte(id:Int):Int={require(id>=0 && id<previous.registers.length,"Invalid legacy register");previous.getRegVal[Byte](id)&255}
        def long(id:Int):Long={require(id>=0 && id<previous.registers.length,"Invalid legacy time register");previous.getRegVal[Long](id)}
        def put(id:Int,value:Int):Unit=if id>=0 && id!=PRFabricationEngine.REG_ZERO && id!=PRFabricationEngine.REG_ONE then {
            val next=value.toByte
            require(!values.contains(id) || values(id)==next,"Conflicting legacy drivers")
            values(id)=next
        }
        def overwrite(id:Int,value:Int):Unit=if id>=0 then values(id)=value.toByte
        def wide(ids:Array[Int],value:Long):Unit={require(ids.length==8,"Invalid new time layout");for i<-0 until 8 do overwrite(ids(i),(value >>> ((7-i)*8)).toInt)}
        val time=old.systemTime
        for i<-0 until 128 do put(i,byte(i))
        wide(PRFabricationEngine.REG_TIME,time)
        for (layer,tiles) <- map.allLayers; ((x,y),native) <- tiles.tiles do native match {
            case gate:GateICTile =>
                val target=model.context.getTileMap.getBaseTile(model.coordinate(Point(x,y),layer)).orElseThrow()
                val layout=new NBTTagCompound;target.save(layout)
                for side<-0 until 4 do {
                    val id=layout.getInteger("regOut"+side)
                    if layout.hasKey("regOut"+side) && id>=0 then {
                        val previousId=previous.terminalRegisters.getOrElse((Point(x,y),side,true),SEIntegratedCircuit.REG_ZERO)
                        put(id,byte(previousId))
                    }
                }
                def state(value:Int):Unit={require(layout.hasKey("regS"),"New gate has no state register");overwrite(layout.getInteger("regS"),value)}
                def deadline(value:Long):Unit=wide(layout.getIntArray("reg_time"),value)
                gate match {
                    case sequential:SequentialGateICTile => sequential.getLogicPrimitive match {
                        case p:Pulse => val s=byte(p.stateReg);require(s<=2,"Invalid pulse phase");state(s);deadline(long(p.schdTimeReg))
                        case r:Repeater => val s=byte(r.stateReg);require(s<=3,"Invalid repeater phase");state(s);deadline(long(r.schdTimeReg))
                        case r:Randomizer =>
                            val s=byte(r.stateReg)
                            require(s<=1 || s==127,"Invalid randomizer phase")
                            val held=r.outputRegs.exists(id=>id>=0 && byte(id)!=0)
                            state(if s==0 || held then 1 else 0)
                            val start=long(r.timeStartReg)
                            deadline(if s==0 && start>=0 then start+((time-start)/2+1)*2 else if held then time+2 else -1)
                        case latch:SRLatch => val s=byte(latch.stateReg);require(s<=2 || s==127,"Invalid SR latch phase");state(if s==127 then 0 else s+1)
                        case latch:ToggleLatch =>
                            val s=byte(latch.stateReg)
                            require(s<=1 || s==127,"Invalid toggle latch phase")
                            state((byte(latch.prevInputMaskReg)<<4)|(if s==127 then 0 else s+1))
                        case latch:TransparentLatch => val s=byte(latch.stateReg);require(s<=1 || s==127,"Invalid transparent latch phase");state(if s==127 then 0 else s+1)
                        case timer:Timer =>
                            val s=byte(timer.stateReg);state(s match {case 0=>2;case 1=>3;case 2=>1;case _=>throw new IllegalArgumentException("Invalid timer phase")})
                            val start=long(timer.timerStartReg)
                            val phase=if start<0 then -1L else time-start
                            deadline(if s==2 || start<0 then -1 else time-Math.floorMod(phase,timer.getTimerMax.toLong)-1)
                        case _:Sequencer => // Its phase is defined entirely by the preserved 64-bit system time.
                        case counter:Counter =>
                            val count=previous.getRegVal[Int](counter.valueReg)
                            require(count>=0 && count<=counter.max,"Invalid saved counter value")
                            val regs=layout.getIntArray("reg_count");require(regs.length==2,"Invalid counter layout")
                            overwrite(regs(0),count>>>8);overwrite(regs(1),count)
                            val last=byte(counter.prevInputMaskReg)
                            state((((last&1)<<1)|((last&2)<<2))<<4|1)
                        case cell:StateCell =>
                            val s=byte(cell.stateReg);val start=long(cell.timerStartReg)
                            state(s match {case 0=>0;case 1=>if start<0 then 1 else 2;case 2=>3;case _=>throw new IllegalArgumentException("Invalid state-cell phase")})
                            deadline(start)
                        case sync:Synchronizer =>
                            val s=byte(sync.stateReg);require(s<=3,"Invalid synchronizer phase")
                            val last=byte(sync.prevInputMaskReg)
                            val reset=byte(sync.inputRegs(2))!=0
                            state(((((last&1)<<1)|((last&2)<<2)|(if reset then 4 else 0))<<4)|(s+1))
                            val start=long(sync.timerStartReg);deadline(if start<0 then -1 else start+2)
                        case _ => return Left("Unknown stateful legacy component")
                    }
                    case _ =>
                }
            case _ =>
        }
        val flatMap=PRFabricationEngine.instance.deserializeFlatMap(compiler.getFlatMap)
        val imported=new ICSimulationContainer
        imported.restoreCommitted(flatMap,values.iterator.map((id,value)=>Integer.valueOf(id)->java.lang.Byte.valueOf(value)).toMap.asJava,
            time,Array.tabulate(4)(r=>old.iostate(r).toShort),Array.tabulate(4)(r=>(old.iostate(r)>>>16).toShort))
        if !imported.outputsStableOnRecompute() then return Left("Legacy register layout or behavior needs conversion in Workbench")
        Right(Converted(imported))
    } catch {case scala.util.control.NonFatal(error)=>Left(Option(error.getMessage).getOrElse(error.getClass.getSimpleName))}
}
