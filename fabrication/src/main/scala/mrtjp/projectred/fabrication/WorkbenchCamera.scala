package mrtjp.projectred.fabrication

import codechicken.lib.vec.Vector3

/** The 1.21.1 top-down perspective camera, independent of the rendering API. */
final class WorkbenchCamera {
    final class Animation(x:Double=0,y:Double=0,z:Double=0) {
        val vector=new Vector3(x,y,z)
        private val step=new Vector3
        private var remaining=0L
        private var last = -1L
        def idle:Boolean=remaining==0
        def move(target:Vector3,duration:Long):Unit = {
            remaining=duration;step.set(target).subtract(vector).divide(duration.toDouble)
        }
        def add(delta:Vector3,duration:Long):Unit = {
            if delta.x==0 && delta.y==0 && delta.z==0 then return
            step.multiply(remaining.toDouble).add(delta);remaining=duration;step.divide(duration.toDouble)
        }
        def tick(now:Long):Unit = {
            val dt=if last<0 then 0L else math.max(0L,now-last)
            last=now
            val n=math.min(remaining,dt)
            vector.add(step.x*n,step.y*n,step.z*n);remaining-=n
        }
    }
    val zoom=new Animation(0,8,0)
    val layerAnimation=new Animation
    val position=new Vector3(0,8,0)
    var layer=0
    var width=1
    var height=1
    val fov=math.toRadians(70)
    def setLayer(value:Int):Unit = {
        layerAnimation.add(new Vector3(0,value-layer,0),200)
        layer=value
    }
    def tick(now:Long,boardWidth:Int,boardHeight:Int):Unit = {
        zoom.vector.x=math.max(-boardWidth/2D,math.min(boardWidth-boardWidth/2D,zoom.vector.x))
        zoom.vector.z=math.max(-boardHeight/2D,math.min(boardHeight-boardHeight/2D,zoom.vector.z))
        zoom.vector.y=math.max(layerAnimation.vector.y+2,math.min(layerAnimation.vector.y+18,zoom.vector.y))
        zoom.tick(now);layerAnimation.tick(now)
        if layerAnimation.idle then layerAnimation.vector.y=layer.toDouble
        zoom.vector.x=math.max(-boardWidth/2D,math.min(boardWidth-boardWidth/2D,zoom.vector.x))
        zoom.vector.z=math.max(-boardHeight/2D,math.min(boardHeight-boardHeight/2D,zoom.vector.z))
        zoom.vector.y=math.max(layerAnimation.vector.y+2,math.min(layerAnimation.vector.y+18,zoom.vector.y))
        position.set(zoom.vector).add(layerAnimation.vector)
    }
    def worldAt(pixelX:Double,pixelY:Double):Vector3 = {
        val distance=position.y-layer-0.125D
        val factor=distance*math.tan(fov/2)
        new Vector3(position.x+(pixelX/width*2-1)*factor*width/height,
            layer+0.125D,position.z-(1-pixelY/height*2)*factor)
    }
    def screenAt(x:Double,z:Double): (Double,Double) = {
        val factor=(position.y-layer-0.125D)*math.tan(fov/2)
        ((1+(x-position.x)/(factor*width/height))*width/2,
            (1+(z-position.z)/factor)*height/2)
    }
    def scroll(at:Vector3,amount:Double):Unit = zoom.add(at.copy().subtract(position).normalize().multiply(amount*0.3D),100)
    def pan(dx:Double,dz:Double):Unit = {
        val scale=if width>height then worldAt(width,0).x-worldAt(0,0).x else worldAt(0,height).z-worldAt(0,0).z
        zoom.add(new Vector3(dx*scale,0,dz*scale),100)
    }
    def focus(minX:Double,minZ:Double,maxX:Double,maxZ:Double):Unit = {
        val fovX=fov*width/height
        val distance=math.max((maxX-minX+2)/(2*math.tan(fovX/2)),(maxZ-minZ+2)/(2*math.tan(fov/2)))
        zoom.move(new Vector3((minX+maxX)/2,distance,(minZ+maxZ)/2),400)
    }
}
