package com.kanvas.examples

import com.kanvas.core.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

fun main() {
    println("Kanvas Image Export Example")
    
    // Create a canvas
    val width = 800
    val height = 600
    val canvas = Canvas.createRaster(width, height)
    
    // Clear with white background
    canvas.clear(Color.WHITE)
    
    // Create some paints
    val redPaint = Paint().apply {
        color = Color.RED
        style = PaintStyle.FILL
    }
    
    val bluePaint = Paint().apply {
        color = Color.BLUE
        style = PaintStyle.STROKE
        strokeWidth = 5f
    }
    
    val greenPaint = Paint().apply {
        color = Color.GREEN
        style = PaintStyle.FILL_AND_STROKE
        strokeWidth = 3f
    }
    
    // Draw some shapes
    println("Drawing shapes...")
    
    // Draw rectangles
    canvas.drawRect(Rect(100f, 100f, 300f, 200f), redPaint)
    canvas.drawRect(Rect(400f, 100f, 600f, 200f), bluePaint)
    
    // Draw a circle using path
    val circlePath = Path().apply {
        addCircle(400f, 400f, 100f)
    }
    canvas.drawPath(circlePath, greenPaint)
    
    // Draw some text
    val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 24f
        style = PaintStyle.FILL
    }
    canvas.drawText("Kanvas Image Export", 200f, 550f, textPaint)
    
    // Draw a gradient-like pattern using multiple rectangles
    for (i in 0..5) {
        val alpha = 255 - (i * 40)
        val rectPaint = Paint().apply {
            color = Color(0, 0, 255, alpha)
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(500f + (i * 20f), 300f + (i * 20f), 700f + (i * 20f), 400f + (i * 20f)), rectPaint)
    }
    
    // Get the final bitmap
    val bitmap = canvas.getBitmap()
    println("Image created: ${bitmap.getWidth()}x${bitmap.getHeight()}")
    
    // Export to PNG file
    println("Exporting to PNG...")
    exportBitmapToPNG(bitmap, "kanvas_export.png")
    
    // Also export to a simple PPM file (easier to implement)
    println("Exporting to PPM...")
    exportBitmapToPPM(bitmap, "kanvas_export.ppm")
    
    println("Export completed!")
    println("Files created: kanvas_export.png and kanvas_export.ppm")
}

/**
 * Export bitmap to PNG using Java's ImageIO
 */
fun exportBitmapToPNG(bitmap: Bitmap, filename: String) {
    try {
        // Convert Kanvas Bitmap to BufferedImage
        val bufferedImage = BufferedImage(bitmap.getWidth(), bitmap.getHeight(), BufferedImage.TYPE_INT_ARGB)
        
        for (y in 0 until bitmap.getHeight()) {
            for (x in 0 until bitmap.getWidth()) {
                val color = bitmap.getPixel(x, y)
                val argb = (color.alpha shl 24) or (color.red shl 16) or (color.green shl 8) or color.blue
                bufferedImage.setRGB(x, y, argb)
            }
        }
        
        // Write to file
        val file = File(filename)
        ImageIO.write(bufferedImage, "png", file)
        println("Successfully exported to $filename")
        
    } catch (e: Exception) {
        println("Error exporting PNG: ${e.message}")
        e.printStackTrace()
    }
}

/**
 * Export bitmap to PPM (Portable Pixmap) format - simple text-based image format
 */
fun exportBitmapToPPM(bitmap: Bitmap, filename: String) {
    try {
        val width = bitmap.getWidth()
        val height = bitmap.getHeight()
        
        // Create PPM content
        val ppmContent = buildString {
            // PPM header
            append("P3\n")
            append("$width $height\n")
            append("255\n")
            
            // Pixel data (RGB only, ignoring alpha for simplicity)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val color = bitmap.getPixel(x, y)
                    // PPM uses RGB values (0-255) separated by spaces
                    append("${color.red} ${color.green} ${color.blue} ")
                }
                append("\n")
            }
        }
        
        // Write to file
        Files.write(Paths.get(filename), ppmContent.toByteArray())
        println("Successfully exported to $filename")
        
    } catch (e: Exception) {
        println("Error exporting PPM: ${e.message}")
        e.printStackTrace()
    }
}