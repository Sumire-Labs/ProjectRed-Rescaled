package mrtjp.projectred.core

import codechicken.lib.colour.EnumColour

/** Shared by the halo renderer and optional positional-light integrations. */
object BundledLightColour {
    /** Weighted palette colour in 0xRRGGBBAA; zero means no active channels. */
    def blend(signal:Array[Byte]):Int = {
        var total = 0
        var red = 0L
        var green = 0L
        var blue = 0L
        for i <- 0 until 16 do {
            val strength = signal(i)&0xFF
            val colour = EnumColour.values()(i).rgba
            total += strength
            red += ((colour >>> 24)&0xFF)*strength
            green += ((colour >>> 16)&0xFF)*strength
            blue += ((colour >>> 8)&0xFF)*strength
        }
        if total == 0 then 0
        else (red/total).toInt<<24 | (green/total).toInt<<16 | (blue/total).toInt<<8 | 0xFF
    }

    def maximum(signal:Array[Byte]):Int = signal.iterator.map(_&0xFF).max
}
