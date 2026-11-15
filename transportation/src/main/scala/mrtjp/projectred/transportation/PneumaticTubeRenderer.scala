package mrtjp.projectred.transportation

import codechicken.lib.lighting.LightModel
import codechicken.lib.render.{CCModel,CCRenderState,OBJParser}
import codechicken.lib.vec.{Transformation,Translation,Vector3}
import codechicken.lib.vec.uv.IconTransformation
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.{Side,SideOnly}
import scala.jdk.CollectionConverters.*

/** The newer tube mesh has its own atlas; legacy pipes keep their original models. */
@SideOnly(Side.CLIENT)
object PneumaticTubeRenderer {
    private val parts = OBJParser.parseModels(new ResourceLocation("projectred:obj/pneumatic_tube.obj"),7,
        new Translation(0.5D,0.5D,0.5D)).asScala.map { (name,source) =>
            val model = source.backfacedCopy()
            model.computeNormals()
            model.shrinkUVs(0.0005)
            model.computeLighting(LightModel.standardLightModel)
            name -> model
        }.toMap
    private val cache = new Array[CCModel](64)

    /** Cached meshes are read-only after construction. */
    def modelFor(connections:Int):CCModel = cache.synchronized {
        val mask = connections&63
        if cache(mask) == null then {
            val axes = (0 until 3).count(axis => (mask & (3<<(axis*2))) != 0)
            cache(mask) = if Integer.bitCount(mask) == 2 && axes == 1 then {
                val axis = if (mask&3) != 0 then 0 else if (mask&12) != 0 then 1 else 2
                parts(s"a$axis").copy()
            } else {
                val models = parts("center") +: (0 until 6).filter(side => (mask&(1<<side)) != 0).map(side => parts(s"s$side"))
                CCModel.combine(models.asJava)
            }
        }
        cache(mask)
    }

    def prepare():Unit = { modelFor(0); modelFor(3); () }

    def render(connections:Int,icon:TextureAtlasSprite,transform:Transformation,ccrs:CCRenderState):Unit =
        modelFor(connections).render(ccrs,transform,new IconTransformation(icon))
}
