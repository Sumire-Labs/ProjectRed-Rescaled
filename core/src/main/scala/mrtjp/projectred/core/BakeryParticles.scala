package mrtjp.projectred.core

import codechicken.lib.model.bakery.IBakeryProvider
import codechicken.lib.model.bakery.generation.IBlockBakery
import codechicken.lib.render.particle.CustomParticleHandler
import codechicken.lib.vec.Cuboid6
import net.minecraft.block.state.IBlockState
import net.minecraft.client.particle.ParticleManager
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.util.math.{BlockPos, RayTraceResult}
import net.minecraft.world.World
import net.minecraftforge.common.property.IExtendedBlockState
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

/** Use the bakery's particle sprite even when its model has no culled face quads. */
@SideOnly(Side.CLIENT)
object BakeryParticles {
    private def sprite(state:IBlockState, world:World, pos:BlockPos):TextureAtlasSprite = {
        val actual = state.getActualState(world,pos)
        val extended = actual.getBlock.getExtendedState(actual,world,pos).asInstanceOf[IExtendedBlockState]
        actual.getBlock.asInstanceOf[IBakeryProvider].getBakery.asInstanceOf[IBlockBakery].getParticleTexture(extended)
    }

    def hit(state:IBlockState, world:World, target:RayTraceResult, manager:ParticleManager):Boolean = {
        if target == null || target.typeOfHit != RayTraceResult.Type.BLOCK then return false
        val pos = target.getBlockPos
        val bounds = new Cuboid6(state.getBoundingBox(world,pos)).add(pos)
        CustomParticleHandler.addBlockHitEffects(world,bounds,target.sideHit,sprite(state,world,pos),manager)
        true
    }

    def destroy(world:World, pos:BlockPos, manager:ParticleManager):Boolean = {
        val state = world.getBlockState(pos)
        val bounds = new Cuboid6(state.getBoundingBox(world,pos)).add(pos)
        val icon = sprite(state,world,pos)
        CustomParticleHandler.addBlockDestroyEffects(world,bounds,Array.fill(6)(icon),manager)
        true
    }
}
