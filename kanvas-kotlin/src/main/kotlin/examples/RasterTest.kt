package com.kanvas.examples

import com.kanvas.core.*

fun main() {
    println("Kanvas Raster Rendering Test")
    
    // Create a canvas
    val canvas = Canvas.createRaster(400, 300)
    
    // Clear with white background
    canvas.clear(Color.WHITE)
    
    // Test 1: Basic rectangle drawing
    println("Test 1: Drawing basic rectangles")
    val redPaint = Paint().apply {
        color = Color.RED
        style = PaintStyle.FILL
    }
    
    canvas.drawRect(Rect(50f, 50f, 150f, 150f), redPaint)
    
    // Test 2: Stroked rectangle
    val bluePaint = Paint().apply {
        color = Color.BLUE
        style = PaintStyle.STROKE
        strokeWidth = 5f
    }
    
    canvas.drawRect(Rect(200f, 50f, 300f, 150f), bluePaint)
    
    // Test 3: Fill and stroke
    val greenPaint = Paint().apply {
        color = Color.GREEN
        style = PaintStyle.FILL_AND_STROKE
        strokeWidth = 3f
    }
    
    canvas.drawRect(Rect(50f, 200f, 150f, 250f), greenPaint)
    
    // Test 4: Path drawing
    println("Test 2: Drawing paths")
    val path = Path().apply {
        moveTo(200f, 200f)
        lineTo(300f, 200f)
        lineTo(250f, 250f)
        close()
    }
    
    canvas.drawPath(path, redPaint)
    
    // Test 5: Curved path
    val curvePath = Path().apply {
        moveTo(300f, 200f)
        quadTo(350f, 150f, 400f, 200f)
    }
    
    canvas.drawPath(curvePath, bluePaint.copy().apply { strokeWidth = 2f })
    
    // Test 6: Text drawing
    println("Test 3: Drawing text")
    val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 16f
        style = PaintStyle.FILL
    }
    
    canvas.drawText("Hello Kanvas!", 50f, 280f, textPaint)
    
    // Test 7: Transformations
    println("Test 4: Testing transformations")
    canvas.save()
    canvas.translate(100f, 50f)
    canvas.rotate(45f)
    
    val transformedPaint = Paint().apply {
        color = Color(255, 0, 255) // Purple
        style = PaintStyle.FILL
    }
    
    canvas.drawRect(Rect(0f, 0f, 50f, 50f), transformedPaint)
    canvas.restore()
    
    // Test 8: Alpha blending
    println("Test 5: Testing alpha blending")
    val semiTransparentPaint = Paint().apply {
        color = Color(255, 0, 0, 128) // Semi-transparent red
        style = PaintStyle.FILL
    }
    
    canvas.drawRect(Rect(250f, 200f, 350f, 250f), semiTransparentPaint)
    
    // Get the final bitmap and check some pixels
    val bitmap = canvas.getBitmap()
    
    println("Test 6: Verifying pixel data")
    val centerPixel = bitmap.getPixel(200, 150)
    println("Center pixel color: $centerPixel")
    
    val redPixel = bitmap.getPixel(100, 100)
    println("Red rectangle pixel: $redPixel")
    
    println("All tests completed!")
    println("Canvas size: ${bitmap.getWidth()}x${bitmap.getHeight()}")
}