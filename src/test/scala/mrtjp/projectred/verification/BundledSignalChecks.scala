package mrtjp.projectred.verification

import mrtjp.projectred.transmission.BundledCommons

object BundledSignalChecks {
    def run():Unit = {
        for mask <- Seq(0,1,0x80,0x8000,0x8081,0xFFFF) do {
            val signal = BundledCommons.unpackDigital(null,mask)
            assert(BundledCommons.packDigital(signal) == mask, "Bundled digital signal round trip changed channels")
            if mask == 0 then assert(signal == null)
            else for channel <- 0 until 16 do
                assert((signal(channel)&0xFF) == (if (mask&(1<<channel)) != 0 then 255 else 0))
        }
        val reusable = Array.fill[Byte](16)(-1)
        val one = BundledCommons.unpackDigital(reusable,1)
        assert(one eq reusable)
        assert((one(0)&0xFF) == 255 && one.drop(1).forall(_ == 0), "Reused signal retained old channels")
    }
}
