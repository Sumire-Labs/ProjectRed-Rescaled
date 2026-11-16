package mrtjp.projectred.transportation

import mrtjp.projectred.core.LegacySaveMigration
import net.minecraft.nbt.{NBTTagCompound, NBTTagList}

object LegacyTransportMigration {
    val PipeItem = "projectred-transportation:pipe"
    val ModernPart = "projectred-transporation:pneumatic_tube"
    private val oldMetas = Set(0,1,2,3,4,32,33,64,65)
    val oldParts:Set[String] = Set("pipe","routed_pipe","interface_pipe","request_pipe","firewall_pipe",
        "netvalve_pipe","netlatency_pipe","pressure_tube","resustance_tube").map("projectred-transporation:"+_)
    private val retiredItems = Set("projectred-transportation:routing_chip","projectred-transportation:router_utility")

    def init():Unit = {
        LegacySaveMigration.register(tag => tag.hasKey("Count",99) &&
            (tag.getString("id") == PipeItem && oldMetas(tag.getInteger("Damage")) ||
             retiredItems(tag.getString("id")) && !tag.getCompoundTag("tag").hasKey(LegacySaveMigration.Key))) { tag =>
            val archive = LegacySaveMigration.archive(tag,"transport_item")
            val itemTag = tag.getCompoundTag("tag")
            itemTag.setTag(LegacySaveMigration.Key,archive)
            tag.removeTag(LegacySaveMigration.Key)
            tag.setTag("tag",itemTag)
            if tag.getString("id") == PipeItem then tag.setShort("Damage",66.toShort)
        }
        LegacySaveMigration.register(tag => oldParts(tag.getString("id")))(tag => convertPart(tag))
        LegacySaveMigration.registerItemCheck(stack => {
            val id = stack.getItem.getRegistryName.toString
            id == PipeItem && oldMetas(stack.getItemDamage) ||
                retiredItems(id) && (!stack.hasTagCompound || !stack.getTagCompound.hasKey(LegacySaveMigration.Key))
        })
    }

    def convertPart(tag:NBTTagCompound, oldType:String = ""):NBTTagCompound = {
        if oldParts(tag.getString("id")) || oldParts(oldType) && !tag.hasKey("payloads",9) then {
            val archive = LegacySaveMigration.archive(tag,"transport_part")
            archive.setString("source_type",if tag.hasKey("id",8) then tag.getString("id") else oldType)
            val recovered = new NBTTagList
            val chips = tag.getTagList("chipsitems",10)
            for i <- 0 until chips.tagCount do recovered.appendTag(chips.getCompoundTagAt(i).copy())
            if tag.getBoolean("mat") then {
                val redstone = new NBTTagCompound
                redstone.setString("id","minecraft:redstone")
                redstone.setByte("Count",1.toByte)
                redstone.setShort("Damage",0.toShort)
                recovered.appendTag(redstone)
            }
            archive.setTag("recovered_items",recovered)
            val payloads = new NBTTagList
            val oldFlow = tag.getTagList("itemFlow",10)
            for i <- 0 until oldFlow.tagCount do {
                val old = oldFlow.getCompoundTagAt(i)
                if old.hasKey("Item",10) then {
                    val data = old.getInteger("idata")
                    val payload = new NBTTagCompound
                    payload.setInteger("id",i)
                    payload.setInteger("progress",math.min(255,((data >>> 16)&255)*255/100))
                    payload.setInteger("speed",12)
                    val input = if (data&7) < 6 then data&7 else 0
                    payload.setInteger("input_side",input)
                    payload.setInteger("output_side",if ((data>>>3)&7) < 6 then (data>>>3)&7 else input^1)
                    val stack = old.getCompoundTag("Item").copy()
                    val count = if old.hasKey("stack_count",99) then old.getInteger("stack_count") else stack.getInteger("Count")
                    stack.setInteger("Count",count)
                    payload.setTag("item_stack",stack)
                    payload.setInteger("stack_count",count)
                    if LegacySaveMigration.readRecoveryItem(stack).isEmpty then recovered.appendTag(stack)
                    else payloads.appendTag(payload)
                } else {
                    recovered.appendTag(old.copy())
                }
            }
            tag.setString("id",ModernPart)
            tag.setByte("meta",66.toByte)
            tag.setTag("payloads",payloads)
            tag.setInteger("next_id",oldFlow.tagCount)
            tag.setBoolean("migration_reroute",true)
            tag.removeTag("itemFlow")
            tag.removeTag("chipsitems")
        }
        tag
    }
}
