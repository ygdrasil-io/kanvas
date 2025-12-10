package com.kanvas.examples

import com.kanvas.core.Bitmap
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

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

