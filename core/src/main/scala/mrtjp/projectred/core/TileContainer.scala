package mrtjp.projectred.core

import mrtjp.core.block.MTBlockTile
import mrtjp.core.gui.NodeContainer
import net.minecraft.entity.player.EntityPlayer

/** Applies tile lifetime/range checks to vanilla window clicks as well as custom packets. */
class TileContainer(private val containerTile:MTBlockTile) extends NodeContainer {
    override def canInteractWith(player:EntityPlayer):Boolean = TileActions.canAccess(containerTile, player)
}
