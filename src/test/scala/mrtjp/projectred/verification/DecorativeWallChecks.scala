package mrtjp.projectred.verification

import mrtjp.projectred.ProjectRedExploration
import mrtjp.projectred.exploration.DecorativeStoneDefs
import net.minecraft.block.{Block,BlockFenceGate,BlockHorizontal,BlockWall}
import net.minecraft.init.Blocks
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldServer

object DecorativeWallChecks {
    def run(world:WorldServer):Unit = {
        val wall = ProjectRedExploration.blockDecorativeWall
        val pos = new BlockPos(32,80,16)
        try {
            for variant <- DecorativeStoneDefs.values do {
                val state = wall.getStateFromMeta(variant.meta)
                world.setBlockState(pos,state,3)
                world.setBlockState(pos.east(),Blocks.COBBLESTONE_WALL.getDefaultState,3)
                assert(wall.getActualState(state,world,pos).getValue(BlockWall.EAST).booleanValue,
                    s"ProjectRed ${variant.getVariantName} wall does not join a vanilla wall")
                val neighbor = world.getBlockState(pos.east())
                assert(neighbor.getBlock.getActualState(neighbor,world,pos.east()).getValue(BlockWall.WEST).booleanValue,
                    "Vanilla wall does not join a ProjectRed wall")
                assert(wall.getMetaFromState(state) == variant.meta && wall.damageDropped(state) == variant.meta,
                    "Wall metadata/drop changed")
                val decoded = Block.getStateById(Block.getStateId(state))
                assert(decoded.getBlock == wall && wall.getMetaFromState(decoded) == variant.meta,
                    "Wall state ID lost its existing variant")
                assert(wall.getCollisionBoundingBox(state,world,pos).maxY == 1.5)
                assert(wall.getBlockHardness(state,world,pos) == 0.0F,"Legacy wall hardness changed")
            }
            val state = wall.getStateFromMeta(0)
            world.setBlockState(pos,state,3)
            world.setBlockState(pos.east(),Blocks.OAK_FENCE_GATE.getDefaultState
                .withProperty(BlockHorizontal.FACING,EnumFacing.NORTH),3)
            val gate = world.getBlockState(pos.east())
            assert(wall.getActualState(state,world,pos).getValue(BlockWall.EAST).booleanValue)
            assert(gate.getBlock.getActualState(gate,world,pos.east()).getValue(BlockFenceGate.IN_WALL).booleanValue,
                "Fence gate does not recognize a ProjectRed wall")
            world.setBlockState(pos.east(),gate.withProperty(BlockHorizontal.FACING,EnumFacing.EAST),3)
            assert(!wall.getActualState(state,world,pos).getValue(BlockWall.EAST).booleanValue,
                "Wall joined the broad side of a perpendicular fence gate")
        } finally {
            world.setBlockToAir(pos)
            world.setBlockToAir(pos.east())
        }
    }
}
