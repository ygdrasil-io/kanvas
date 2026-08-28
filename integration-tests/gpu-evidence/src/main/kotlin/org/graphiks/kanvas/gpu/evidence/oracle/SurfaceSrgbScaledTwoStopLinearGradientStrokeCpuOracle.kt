package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent W44 oracle: scales both four-band geometry and the linear-gradient axis. */
class SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle(
    private val bands: List<Rect>, start: Point, end: Point, scale: Int, translation: Point,
    private val first: Stop, private val last: Stop,
) : CpuOracle {
    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) { fun contains(x: Int,y: Int)=x in left until right && y in top until bottom }
    data class Point(val x: Double,val y: Double); data class Stop(val red:Int,val green:Int,val blue:Int)
    private val start=Point(start.x*scale+translation.x,start.y*scale+translation.y); private val end=Point(end.x*scale+translation.x,end.y*scale+translation.y)
    override fun render(width:Int,height:Int):ByteArray { val out=ByteArray(width*height*4); val dx=end.x-start.x; val dy=end.y-start.y; val n=dx*dx+dy*dy; val a=SurfaceSrgbOracleMath.decodeStraight(intArrayOf(first.red,first.green,first.blue,255)); val b=SurfaceSrgbOracleMath.decodeStraight(intArrayOf(last.red,last.green,last.blue,255)); for(y in 0 until height)for(x in 0 until width)if(bands.any{it.contains(x,y)}){val t=(((x+.5-start.x)*dx+(y+.5-start.y)*dy)/n).coerceIn(0.0,1.0);val c=SurfaceSrgbOracleMath.storeSrgb(SurfaceSrgbOracleMath.LinearPremul(a.red+(b.red-a.red)*t,a.green+(b.green-a.green)*t,a.blue+(b.blue-a.blue)*t,1.0));val o=(y*width+x)*4;c.indices.forEach{out[o+it]=c[it].toByte()}};return out }
}
