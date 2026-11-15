package mrtjp.projectred.fabrication

import mrtjp.projectred.fabrication.engine.{EngineData,PRFabricationEngine}
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{NBTTagCompound,NBTTagList}

/** Compiled target plus an exact drawing snapshot; old drawings remain available for editing/migration. */
object ModernBlueprint {
    val Key="modern_ic"
    private def canonical(source:NBTTagCompound):NBTTagCompound = {
        val result=source.copy()
        val tiles=source.getTagList("tiles",10)
        val ordered=new NBTTagList
        (0 until tiles.tagCount).map(tiles.getCompoundTagAt).sortBy(t =>
            (t.getByte("xpos")&255,t.getByte("ypos")&255,t.getByte("id")&255)).foreach(t => ordered.appendTag(t.copy()))
        result.setTag("tiles",ordered)
        result
    }
    def matches(stack:ItemStack):Boolean = stack.hasTagCompound && stack.getTagCompound.hasKey(Key) && {
        val payload=stack.getTagCompound.getCompoundTag(Key)
        payload.getInteger("compile_format") == PRFabricationEngine.COMPILE_FORMAT &&
            payload.getCompoundTag("source") == canonical(stack.getTagCompound.getCompoundTag("tilemap"))
    }
    def isBuilt(stack:ItemStack):Boolean = matches(stack) && stack.getTagCompound.getCompoundTag(Key).getBoolean("is_built")
    def attach(stack:ItemStack,model:ModernDesign):Unit = {
        val tag=new NBTTagCompound
        tag.setInteger("compile_format",PRFabricationEngine.COMPILE_FORMAT)
        tag.setTag("source",canonical(stack.getTagCompound.getCompoundTag("tilemap")))
        val built=model != null && model.context.getStateMachine.isSimulating
        tag.setBoolean("is_built",built)
        if built then {
            EngineData.putJson(tag,"flat_map",model.context.getStateMachine.getFlatMap)
            model.context.getTileMap.getInterfaceSpec.saveTo(tag,"io_spec")
        }
        stack.getTagCompound.setTag(Key,tag)
    }
    /** Legacy designs are compiled once when entering the new production path. */
    def compileLegacy(stack:ItemStack):Boolean = {
        val editor=new ICTileMapEditor(null)
        editor.tileMapContainer.loadTiles(stack.getTagCompound.getCompoundTag("tilemap"))
        val model=new ModernDesign(editor)
        model.rebuild()
        model.context.tileChanged = _ => ()
        val compiler=model.context.getStateMachine
        compiler.startCompile()
        model.unsupported.foreach(compiler.getCompilerLog.addProblem)
        while compiler.getState == 2 do compiler.tick(0)
        if !compiler.isSimulating then false
        else { attach(stack,model);true }
    }
    def flatMap(stack:ItemStack) = {
        require(isBuilt(stack),"IC drawing needs recompilation")
        PRFabricationEngine.instance.deserializeFlatMap(EngineData.getJson(stack.getTagCompound.getCompoundTag(Key),"flat_map"))
    }
}
