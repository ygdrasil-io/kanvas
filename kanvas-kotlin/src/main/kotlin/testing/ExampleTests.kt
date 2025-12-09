package testing

import com.kanvas.core.*
import com.kanvas.core.PaintStyle

/**
 * Example GM test that demonstrates basic functionality
 */
class BasicShapesGM : GM() {
    override fun getName(): String = "basic_shapes"
    override fun getSize(): Size = Size(400f, 300f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        // Draw a red rectangle
        val redPaint = Paint().apply {
            color = Color(255, 0, 0, 255)
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(50f, 50f, 150f, 150f), redPaint)
        
        // Draw a green circle
        val greenPaint = Paint().apply {
            color = Color(0, 255, 0, 255)
            style = PaintStyle.FILL
        }
        val circlePath = Path().apply {
            addCircle(300f, 150f, 50f)
        }
        canvas.drawPath(circlePath, greenPaint)
        
        // Draw some text
        val textPaint = Paint().apply {
            color = Color(0, 0, 255, 255)
            textSize = 24f
            style = PaintStyle.FILL
        }
        canvas.drawText("Kanvas GM Test", 100f, 250f, textPaint)
        
        return DrawResult.OK
    }
}

/**
 * Example of a simple GM using the simpleGM helper
 */
val simpleRectTest = simpleGM("simple_rect", 200, 150) { canvas ->
    val bluePaint = Paint().apply {
        color = Color(0, 0, 255, 255)
        style = PaintStyle.FILL
    }
    canvas.drawRect(Rect(25f, 25f, 175f, 125f), bluePaint)
}

/**
 * Example of a GM with custom background color
 */
val gradientTest = simpleGM("gradient_background", 300, 200, Color(200, 200, 255)) { canvas ->
    // Draw a gradient effect using rectangles
    val colors = listOf(
        Color(255, 0, 0, 255),
        Color(0, 255, 0, 255),
        Color(0, 0, 255, 255)
    )
    
    for (i in colors.indices) {
        val paint = Paint().apply {
            color = colors[i]
            style = PaintStyle.FILL
        }
        val y = 50f + i * 50f
        canvas.drawRect(Rect(50f, y, 250f, y + 30f), paint)
    }
}

/**
 * Example of a GM that demonstrates clipping
 */
class ClippingTestGM : GM() {
    override fun getName(): String = "clipping_test"
    override fun getSize(): Size = Size(300f, 250f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        // Draw background rectangle
        val bgPaint = Paint().apply {
            color = Color(200, 200, 200, 255)
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(50f, 50f, 250f, 200f), bgPaint)
        
        // Note: Clipping is not yet implemented in Kanvas
        // For now, we'll just draw the red rectangle directly
        val redPaint = Paint().apply {
            color = Color(255, 0, 0, 255)
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(75f, 75f, 225f, 175f), redPaint)
        
        // Draw border around clip area
        val borderPaint = Paint().apply {
            color = Color(0, 0, 0, 255)
            strokeWidth = 2f
            style = PaintStyle.STROKE
        }
        canvas.drawRect(Rect(75f, 75f, 225f, 175f), borderPaint)
        
        return DrawResult.OK
    }
}

/**
 * Example of a GM that demonstrates transformations
 */
class TransformationTestGM : GM() {
    override fun getName(): String = "transformation_test"
    override fun getSize(): Size = Size(400f, 300f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        val bluePaint = Paint().apply {
            color = Color(0, 0, 255, 255)
            style = PaintStyle.FILL
        }
        
        // Draw original rectangle
        canvas.drawRect(Rect(50f, 50f, 150f, 100f), bluePaint)
        
        // Save state and apply transformation
        canvas.save()
        canvas.translate(200f, 100f)
        canvas.rotate(45f)
        
        // Draw transformed rectangle
        val redPaint = Paint().apply {
            color = Color(255, 0, 0, 255)
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(0f, 0f, 100f, 50f), redPaint)
        
        canvas.restore()
        
        return DrawResult.OK
    }
}