package com.kanvas.examples

import com.kanvas.core.*
import com.kanvas.effects.*

fun main() {
    println("Kanvas Kotlin Example")
    
    // Create a canvas
    val canvas = Canvas.createRaster(800, 600)
    
    // Create some paints
    val redPaint = Paint().apply {
        color = Color.RED
        style = PaintStyle.FILL
        strokeWidth = 4f
    }
    
    val bluePaint = Paint().apply {
        color = Color.BLUE
        style = PaintStyle.STROKE
        strokeWidth = 8f
    }
    
    val gradientPaint = Paint().apply {
        shader = LinearGradientShader(
            Point(100f, 100f),
            Point(700f, 500f),
            intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
        )
        style = PaintStyle.FILL
    }
    
    // Draw some shapes
    canvas.save()
    canvas.translate(50f, 50f)
    
    // Draw a rectangle
    canvas.drawRect(Rect(100f, 100f, 300f, 200f), redPaint)
    
    // Draw a circle
    val circlePath = Path().apply {
        addCircle(400f, 300f, 100f)
    }
    canvas.drawPath(circlePath, bluePaint)
    
    // Draw with gradient
    val rectPath = Path().apply {
        addRect(Rect(200f, 400f, 600f, 500f))
    }
    canvas.drawPath(rectPath, gradientPaint)
    
    // Draw some text
    val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 24f
        isAntiAlias = true
    }
    canvas.drawText("Hello Kanvas!", 100f, 550f, textPaint)
    
    canvas.restore()
    
    // Test bitmap operations
    val bitmap = Bitmap.create(100, 100, BitmapConfig.ARGB_8888)
    bitmap.eraseColor(Color(255, 0, 0, 128)) // Semi-transparent red
    
    val scaledBitmap = bitmap.scale(50, 50)
    println("Original bitmap: ${bitmap.getWidth()}x${bitmap.getHeight()}")
    println("Scaled bitmap: ${scaledBitmap.getWidth()}x${scaledBitmap.getHeight()}")
    
    // Test path operations
    val path = Path().apply {
        moveTo(0f, 0f)
        lineTo(100f, 0f)
        lineTo(100f, 100f)
        lineTo(0f, 100f)
        close()
    }
    
    val pathLength = PathUtils.computeLength(path)
    println("Path length: $pathLength")
    
    // Test color filters
    val colorMatrix = floatArrayOf(
        0.5f, 0.0f, 0.0f, 0.0f, 0.0f,  // Red
        0.0f, 0.5f, 0.0f, 0.0f, 0.0f,  // Green
        0.0f, 0.0f, 0.5f, 0.0f, 0.0f,  // Blue
        0.0f, 0.0f, 0.0f, 1.0f, 0.0f   // Alpha
    )
    
    val colorFilter = ColorMatrixFilter(colorMatrix)
    val originalColor = Color(255, 128, 64)
    val filteredColor = colorFilter.apply(originalColor)
    
    println("Original color: $originalColor")
    println("Filtered color: $filteredColor")
    
    println("Example completed successfully!")
}