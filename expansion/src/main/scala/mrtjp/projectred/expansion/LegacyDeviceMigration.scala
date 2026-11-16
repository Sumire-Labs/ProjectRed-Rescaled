package mrtjp.projectred.expansion

import mrtjp.core.block.ItemBlockCore
import mrtjp.projectred.ProjectRedExpansion
import mrtjp.projectred.core.LegacySaveMigration
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.NonNullList

object LegacyDeviceMigration {
    private val OldTile = "projectred-expansion:machine2|3"
    private val ItemId = "projectred-expansion:machine2"
    def init():Unit = {
        LegacySaveMigration.register(tag => tag.getString("id") == OldTile && !tag.hasKey(LegacySaveMigration.Key)) {
            tag => LegacySaveMigration.archive(tag,"filtered_importer")
        }
        LegacySaveMigration.register(tag => tag.getString("id") == ItemId && tag.hasKey("Count",99) && tag.getInteger("Damage") == 3) { tag =>
            val archive = LegacySaveMigration.archive(tag,"filtered_importer_item")
            val data = tag.getCompoundTag("tag")
            data.setTag(LegacySaveMigration.Key,archive)
            tag.removeTag(LegacySaveMigration.Key)
            if data.hasKey("BlockEntityTag",10) then data.setTag("BlockEntityTag",convertData(data.getCompoundTag("BlockEntityTag")))
            tag.setTag("tag",data)
            tag.setShort("Damage",1.toShort)
        }
        LegacySaveMigration.registerItemCheck(stack => stack.getItem.getRegistryName.toString == ItemId && stack.getItemDamage == 3)
    }
    def convertData(source:NBTTagCompound):NBTTagCompound = {
        val result = source.copy()
        val archive = LegacySaveMigration.archive(result,"filtered_importer")
        archive.setTag("recovered_items",source.getTagList("filtered importeritems",10).copy())
        result.removeTag("filtered importeritems")
        result.setString("id","projectred-expansion:machine2|1")
        return result
    }
    def convert(tile:TileFilteredImporter):Boolean = {
        val world = tile.getWorld
        val pos = tile.getPos
        if world == null || world.isRemote || world.getTileEntity(pos) != tile then return false
        val original = tile.writeToNBT(new NBTTagCompound)
        LegacySaveMigration.archive(original,"filtered_importer")
        val conversionSource = original.copy()
        val filters = conversionSource.getTagList("filtered importeritems",10)
        for i <- 0 until filters.tagCount do {
            val entry = filters.getCompoundTagAt(i)
            entry.setInteger("Count",tile.getStackInSlot(entry.getInteger("index")).getCount)
        }
        val converted = convertData(conversionSource)
        val prepared = new TileItemImporter
        prepared.setWorld(world)
        prepared.setPos(pos)
        prepared.load(converted)
        tile.suppressLegacyRecoveryDrop = true
        tile.clear()
        tile.itemStorage.clear()
        val target = ProjectRedExpansion.machine2.getStateFromMeta(1)
        if world.getBlockState(pos) != target && !world.setBlockState(pos,target,3) then {
            tile.load(original)
            tile.suppressLegacyRecoveryDrop = false
            return false
        }
        world.setTileEntity(pos,prepared)
        prepared.markDirty()
        world.notifyBlockUpdate(pos,target,target,3)
        true
    }
}

class ItemBlockDevice extends ItemBlockCore(ProjectRedExpansion.machine2) {
    override def getNBTShareTag(stack:ItemStack):NBTTagCompound = LegacySaveMigration.archiveShare(super.getNBTShareTag(stack))
    override def getSubItems(tab:CreativeTabs, items:NonNullList[ItemStack]):Unit = {
        super.getSubItems(tab,items)
        items.removeIf(stack => stack.getItem == this && stack.getItemDamage == 3)
    }
}
