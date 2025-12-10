package testing.skia

import com.kanvas.core.Canvas
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Path
import com.kanvas.core.SK_Scalar1
import com.kanvas.core.Size
import com.kanvas.core.SkRandom
import testing.DrawResult
import testing.GM

/**
 * Port of Skia's beziers.cpp test
 * Tests drawing of random quadratic and cubic Bézier curves.
 * 
 * This test draws a series of random Bézier curves with varying colors and stroke widths
 * to verify that Bézier curve rendering works correctly. The test is divided into two parts:
 * - Top half: Quadratic Bézier curves
 * - Bottom half: Cubic Bézier curves
 */
class BeziersGM : GM() {
    override fun getName(): String = "beziers"
    
    override fun getSize(): Size = Size(400f, 800f) // W x H*2 from original
    
    override fun onDraw(canvas: Canvas): DrawResult {
        val paint = Paint().apply {
            style = PaintStyle.STROKE
            strokeWidth = SK_Scalar1 * 9f / 2f
            isAntiAlias = true
        }
        
        val rand = SkRandom()
        
        // Draw quadratic Bézier curves
        for (i in 0 until 10) {
            canvas.drawPath(rndQuad(paint, rand), paint)
        }
        
        // Move to bottom half for cubic curves
        canvas.translate(0f, 400f * SK_Scalar1)
        
        // Draw cubic Bézier curves
        for (i in 0 until 10) {
            canvas.drawPath(rndCubic(paint, rand), paint)
        }
        
        return DrawResult.OK
    }
    
    /**
     * Generate a random quadratic Bézier path
     */
    private fun rndQuad(paint: Paint, rand: SkRandom): Path {
        val a = rand.nextRangeScalar(0f, 400f)
        val b = rand.nextRangeScalar(0f, 400f)
        
        val path = Path()
        path.moveTo(a, b)
        
        for (x in 0 until 2) {
            val c = rand.nextRangeScalar(100f, 400f)  // W/4 to W
            val d = rand.nextRangeScalar(0f, 400f)    // 0 to H
            val e = rand.nextRangeScalar(0f, 400f)    // 0 to W
            val f = rand.nextRangeScalar(100f, 400f)  // H/4 to H
            path.quadTo(c, d, e, f)
        }
        
        paint.color = rand.nextColor()
        val width = rand.nextRangeScalar(1f, 5f)
        paint.strokeWidth = width * width
        paint.alpha = 255 // Fully opaque
        
        return path
    }
    
    /**
     * Generate a random cubic Bézier path
     */
    private fun rndCubic(paint: Paint, rand: SkRandom): Path {
        val a = rand.nextRangeScalar(0f, 400f)
        val b = rand.nextRangeScalar(0f, 400f)
        
        val path = Path()
        path.moveTo(a, b)
        
        for (x in 0 until 2) {
            val c = rand.nextRangeScalar(100f, 400f)  // W/4 to W
            val d = rand.nextRangeScalar(0f, 400f)    // 0 to H
            val e = rand.nextRangeScalar(0f, 400f)    // 0 to W
            val f = rand.nextRangeScalar(100f, 400f)  // H/4 to H
            val g = rand.nextRangeScalar(100f, 400f)  // W/4 to W
            val h = rand.nextRangeScalar(100f, 400f)  // H/4 to H
            path.cubicTo(c, d, e, f, g, h)
        }
        
        paint.color = rand.nextColor()
        val width = rand.nextRangeScalar(1f, 5f)
        paint.strokeWidth = width * width
        paint.alpha = 255 // Fully opaque
        
        return path
    }
}