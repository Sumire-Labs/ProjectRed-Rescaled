package mrtjp.projectred.core

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream, IOException}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import com.google.common.io.ByteStreams
import net.minecraft.nbt.{CompressedStreamTools, NBTTagCompound, NBTSizeTracker}

/** A transport envelope; item and world saves keep their original compound tags. */
object CompressedNBT {
    private val payloadKey = "projectred:compressed_nbt"
    private val threshold = 16384
    private val maxBytes = 4*1024*1024
    private val maxCompressedBytes = 1024*1024
    private val maxAllocation = 16L*1024*1024

    def encode(tag:NBTTagCompound):NBTTagCompound = {
        if tag == null then return null
        val bytes = new ByteArrayOutputStream
        CompressedStreamTools.write(tag,new DataOutputStream(bytes))
        if bytes.size <= threshold then return tag
        if bytes.size > maxBytes then throw new IOException("ProjectRed transport NBT exceeds four MiB")
        val compressed = new ByteArrayOutputStream
        val gzip = new GZIPOutputStream(compressed)
        try gzip.write(bytes.toByteArray)
        finally gzip.close()
        if compressed.size > maxCompressedBytes then throw new IOException("ProjectRed compressed NBT exceeds one MiB")
        val envelope = new NBTTagCompound
        envelope.setByteArray(payloadKey,compressed.toByteArray)
        envelope
    }

    def decode(tag:NBTTagCompound):NBTTagCompound = {
        if tag == null || !tag.hasKey(payloadKey,7) || tag.getSize != 1 then return tag
        val compressed = tag.getByteArray(payloadKey)
        if compressed.length > maxCompressedBytes then throw new IOException("ProjectRed compressed NBT exceeds one MiB")
        val gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))
        val bytes = try ByteStreams.toByteArray(ByteStreams.limit(gzip,maxBytes.toLong+1))
            finally gzip.close()
        if bytes.length > maxBytes then throw new IOException("ProjectRed expanded NBT exceeds four MiB")
        CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(bytes)),new NBTSizeTracker(maxAllocation))
    }
}
