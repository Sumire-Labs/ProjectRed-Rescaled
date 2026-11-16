package mrtjp.projectred.fabrication

import mrtjp.core.block.ItemBlockCore
import mrtjp.projectred.ProjectRedFabrication.*
import mrtjp.projectred.core.LegacySaveMigration
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{NBTTagCompound, NBTTagList}
import net.minecraft.util.NonNullList
import net.minecraft.creativetab.CreativeTabs

object LegacyFabricationMigration {
    private val PrinterId = "projectred-fabrication:ic_machine|1"
    private val ChipId = "projectred-fabrication:ic_chip"
    private val MachineItem = "projectred-fabrication:ic_machine"
    private case class Built(valid:Boolean, tag:NBTTagCompound)
    private val compiled = new java.util.LinkedHashMap[NBTTagCompound,Built](32,0.75F,true)

    private def canBuild(saved:NBTTagCompound):Boolean = compiled.synchronized {
        val source = saved.getCompoundTag("tag").copy()
        val history = source.getCompoundTag(LegacySaveMigration.Key).copy()
        source.removeTag(LegacySaveMigration.Key)
        var result = compiled.get(source)
        if result == null then {
            val candidate = LegacySaveMigration.stackFromSaved(saved,saved.getInteger("Count"))
            val valid = ICData.canFabricate(candidate)
            val template = candidate.getTagCompound.copy()
            template.removeTag(LegacySaveMigration.Key)
            result = Built(valid,template)
            compiled.put(source.copy(),result)
            if compiled.size() > 32 then compiled.remove(compiled.keySet().iterator().next())
        }
        val migrated = result.tag.copy()
        migrated.setTag(LegacySaveMigration.Key,history)
        saved.setTag("tag",migrated)
        result.valid
    }

    def init():Unit = {
        LegacySaveMigration.register(tag => tag.getString("id") == PrinterId && !tag.hasKey(LegacySaveMigration.Key)) {
            tag => LegacySaveMigration.archive(tag,"ic_printer")
        }
        LegacySaveMigration.register(tag => tag.hasKey("Count",99) &&
            (tag.getString("id") == ChipId && Set(0,1)(tag.getInteger("Damage")) || tag.getString("id") == MachineItem && tag.getInteger("Damage") == 1)) { tag =>
            val archive = LegacySaveMigration.archive(tag,"fabrication_item")
            val itemTag = tag.getCompoundTag("tag")
            itemTag.setTag(LegacySaveMigration.Key,archive)
            tag.removeTag(LegacySaveMigration.Key)
            tag.setTag("tag",itemTag)
            if tag.getString("id") == MachineItem then {
                tag.setString("id","projectred-fabrication:fabrication_machine")
                tag.setShort("Damage",2.toShort)
                if itemTag.hasKey("BlockEntityTag",10) then
                    itemTag.setTag("BlockEntityTag",packagingData(itemTag.getCompoundTag("BlockEntityTag")))
            } else {
                val written = itemTag.hasKey("tilemap",10)
                val creative = tag.getInteger("Damage") == 1
                val valid = written && !creative && canBuild(tag)
                tag.setString("id",if valid then "projectred-fabrication:valid_die"
                    else if written then "projectred-fabrication:ic_blueprint" else "projectred-fabrication:rough_silicon_wafer")
                tag.setShort("Damage",0.toShort)
            }
        }
        LegacySaveMigration.registerItemCheck(stack => {
            val id = stack.getItem.getRegistryName.toString
            id == ChipId && Set(0,1)(stack.getItemDamage) || id == MachineItem && stack.getItemDamage == 1
        })
    }

    /** Preserve all input/storage stacks; collect the recovery archive before starting new work. */
    def packagingData(source:NBTTagCompound):NBTTagCompound = {
        val result = new NBTTagCompound
        val archive = if source.hasKey(LegacySaveMigration.Key,10) then source.getCompoundTag(LegacySaveMigration.Key).copy()
            else LegacySaveMigration.archive(source.copy(),"ic_printer").copy()
        result.setTag(LegacySaveMigration.Key,archive)
        result.setString("id","projectred-fabrication:fabrication_machine|2")
        for key <- Seq("x","y","z") do result.setInteger(key,source.getInteger(key))
        result.setByte("rot",source.getByte("rot"))
        val old = source.getTagList("icprinteritems",10)
        val items = (0 until old.tagCount).map(i => LegacySaveMigration.migrate(old.getCompoundTagAt(i).copy()))
        val preferred = items.find(t => t.getInteger("index") == 20 && t.getString("id") == "projectred-fabrication:valid_die")
            .orElse(items.find(t => t.getInteger("index") == 19 && t.getString("id") == "projectred-fabrication:valid_die"))
        val inventory = new NBTTagList
        preferred.foreach { die =>
            val entry = die.copy()
            entry.setByte("Count",math.min(64,die.getInteger("Count")).toByte)
            entry.setInteger("index",4)
            inventory.appendTag(entry)
        }
        val recovered = new NBTTagList
        var slot = 0
        for entry <- items if !preferred.exists(_ eq entry) || entry.getInteger("Count") > 64 do {
            val saved = entry.copy()
            if preferred.exists(_ eq entry) then saved.setInteger("Count",entry.getInteger("Count")-64)
            saved.removeTag("index")
            saved.setByte("Slot",slot.toByte)
            recovered.appendTag(saved)
            slot += 1
        }
        val carrier = LegacySaveMigration.recoveryItem(archive,recovered).writeToNBT(new NBTTagCompound)
        carrier.setInteger("index",9)
        inventory.appendTag(carrier)
        result.setTag("packaging_tableitems",inventory)
        result.setInteger("packaging_tableitemsCount",10)
        result
    }
}

class ItemBlockICMachine extends ItemBlockCore(icBlock) {
    override def getSubItems(tab:CreativeTabs, items:NonNullList[ItemStack]):Unit =
        if isInCreativeTab(tab) then items.add(new ItemStack(this,1,0))
}

class ItemBlockFabricationMachine extends ItemBlockCore(fabricationMachineBlock) {
    override def getNBTShareTag(stack:ItemStack):NBTTagCompound = LegacySaveMigration.archiveShare(super.getNBTShareTag(stack))
}
