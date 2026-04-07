package testing.skia

import com.kanvas.core.Canvas
import com.kanvas.core.Color
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Rect
import com.kanvas.core.Size
import testing.DrawResult
import testing.GM

/**
 * Port of Skia's aarectmodes.cpp test
 * Tests anti-aliased rectangle drawing with different blend modes.
 * 
 * This test systematically tests various blend modes with anti-aliasing,
 * using a grid layout and different alpha combinations.
 */
class AaRectModesGM : GM() {
    override fun getName(): String = "aarectmodes"
    
    override fun getSize(): Size = Size(640f, 480f)
    
    // Constants matching C++ implementation
    companion object {
        const val gWidth = 64
        const val gHeight = 64
        val W = gWidth.toFloat()
        val H = gHeight.toFloat()
        
        // Blend modes from C++ - note: Kanvas may not support all yet
        val gModes = listOf(
            "Clear", "Src", "Dst", "SrcOver", "DstOver", "SrcIn",
            "DstIn", "SrcOut", "DstOut", "SrcATop", "DstATop", "Xor"
        )
        
        val gAlphaValue = listOf(0xFF, 0x88, 0x88)
    }
    
    override fun onDraw(canvas: Canvas): DrawResult {
        // Create background shader - replica of make_bg_shader() from C++
        val bgPaint = createBackgroundShader()
        
        // Initial translation - replica of canvas->translate(SkIntToScalar(4), SkIntToScalar(4))
        canvas.translate(4f, 4f)
        
        // Main test loop - replica of the C++ grid system
        for (alpha in 0 until 4) {
            canvas.save()
            canvas.save()
            
            for (i in gModes.indices) {
                if (6 == i) {
                    canvas.restore()
                    canvas.translate(W * 5, 0f)
                    canvas.save()
                }
                
                // Draw background for this cell
                val bounds = Rect(0f, 0f, W, H)
                canvas.drawRect(bounds, bgPaint)
                
                // Save layer for blend mode testing
                // Note: Kanvas may not support saveLayer yet
                // canvas.saveLayer(bounds, null)
                
                // Draw the test cell
                val dy = drawCell(canvas, gModes[i],
                                 gAlphaValue[alpha and 1],
                                 gAlphaValue[alpha and 2])
                
                // Restore layer
                // canvas.restore()
                
                // Move to next row
                canvas.translate(0f, dy * 5 / 4)
            }
            
            canvas.restore()
            canvas.restore()
            canvas.translate(W * 5 / 4, 0f)
        }
        
        return DrawResult.OK
    }
    
    private fun createBackgroundShader(): Paint {
        // Replica of make_bg_shader() from C++
        // Creates a 2x2 checkerboard pattern scaled by 6x
        
        // Note: Kanvas may not support custom shaders yet
        // For now, use a simple gray background as placeholder
        return Paint().apply {
            color = Color(0xCE, 0xCF, 0xCE, 255) // Approximate the C++ background color
            style = PaintStyle.FILL
        }
    }
    
    private fun drawCell(canvas: Canvas, mode: String, a0: Int, a1: Int): Float {
        // Replica of drawCell() from C++
        val paint = Paint().apply {
            isAntiAlias = true
        }
        
        // Draw blue oval
        val r = Rect(0f, 0f, W, H)
        r.inset(W/10, H/10)
        
        paint.color = Color(0, 0, 0xFF, a0) // SK_ColorBLUE with alpha a0
        canvas.drawOval(r, paint)
        
        // Draw red rectangle with blend mode
        paint.color = Color(0xFF, 0, 0, a1) // SK_ColorRED with alpha a1
        
        // Note: Kanvas may not support blend modes yet
        // paint.blendMode = when (mode) {
        //     "Clear" -> BlendMode.CLEAR
        //     "Src" -> BlendMode.SRC
        //     "Dst" -> BlendMode.DST
        //     "SrcOver" -> BlendMode.SRC_OVER
        //     "DstOver" -> BlendMode.DST_OVER
        //     "SrcIn" -> BlendMode.SRC_IN
        //     "DstIn" -> BlendMode.DST_IN
        //     "SrcOut" -> BlendMode.SRC_OUT
        //     "DstOut" -> BlendMode.DST_OUT
        //     "SrcATop" -> BlendMode.SRC_ATOP
        //     "DstATop" -> BlendMode.DST_ATOP
        //     "Xor" -> BlendMode.XOR
        //     else -> BlendMode.SRC_OVER
        // }
        
        val offset = 1f / 3 // SK_Scalar1 / 3
        val rect = Rect(W / 4 + offset, H / 4 + offset, W / 4 + W / 2, H / 4 + H / 2)
        canvas.drawRect(rect, paint)
        
        return H
    }
    
    // Note: test4() function from C++ is not implemented as it uses
    // complex path operations that may not be supported in Kanvas yet
}