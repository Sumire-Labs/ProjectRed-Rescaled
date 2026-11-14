package mrtjp.projectred.core

import mrtjp.core.block.MTBlockTile
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import scala.jdk.CollectionConverters.*

/** Additional authorization for opt-in MrTJPCore client tile actions. */
object TileActions {
    def canAccess(tile:MTBlockTile, player:EntityPlayer):Boolean =
        player != null && tile.getWorld != null && player.world == tile.getWorld && !tile.isInvalid &&
        player.getDistanceSq(tile.getPos.getX+0.5, tile.getPos.getY+0.5, tile.getPos.getZ+0.5) <= 64 &&
        tile.getWorld.isBlockLoaded(tile.getPos) && tile.getWorld.getTileEntity(tile.getPos) == tile

    def mayEdit(tile:MTBlockTile, player:EntityPlayer):Boolean =
        canAccess(tile, player) && !tile.getWorld.isRemote && !player.isSpectator &&
        tile.getWorld.isBlockModifiable(player, tile.getPos)

    def hasOpenInventory(tile:MTBlockTile & IInventory, player:EntityPlayer):Boolean =
        mayEdit(tile, player) && player.openContainer != null &&
        player.openContainer.inventorySlots.asScala.exists(slot => slot.inventory eq tile)
}
