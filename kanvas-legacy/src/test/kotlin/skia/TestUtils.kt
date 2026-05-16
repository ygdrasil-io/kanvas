package skia

import com.kanvas.core.Bitmap
import com.kanvas.core.CanvasFactory
import com.kanvas.core.Color
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Rect
import testing.GM
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import javax.imageio.ImageIO

/**
 * Class to manage similarity scores for GM tests
 */
class SimilarityTracker {
    companion object {
        private const val SCORES_FILE = "test-similarity-scores.properties"
        private val scores = Properties()
        private var loaded = false
        
        private fun loadScores() {
            if (loaded) return
            
            try {
                val scoresFile = File(SCORES_FILE)
                if (scoresFile.exists()) {
                    FileInputStream(scoresFile).use { 
                        scores.load(it)
                    }
                }
                loaded = true
            } catch (e: Exception) {
                println("⚠️  Could not load similarity scores: ${e.message}")
            }
        }
        
        private fun saveScores() {
            try {
                FileOutputStream(SCORES_FILE).use { 
                    scores.store(it, "Kanvas Skia GM Test Similarity Scores")
                }
            } catch (e: Exception) {
                println("❌ Could not save similarity scores: ${e.message}")
            }
        }
        
        fun getPreviousScore(testName: String): Double? {
            loadScores()
            val scoreString = scores.getProperty(testName)
            return scoreString?.toDoubleOrNull()
        }
        
        fun updateScore(testName: String, newScore: Double): Boolean {
            loadScores()
            
            val previousScore = getPreviousScore(testName)
            
            if (previousScore == null || newScore > previousScore) {
                // Store the new score if it's better or if there was no previous score
                scores.setProperty(testName, newScore.toString())
                saveScores()
                
                if (previousScore == null) {
                    println("📊 New similarity score recorded for $testName: ${String.format("%.2f", newScore)}%")
                } else {
                    val improvement = newScore - previousScore
                    println("📈 Improved similarity for $testName: ${String.format("%.2f", previousScore)}% → ${String.format("%.2f", newScore)}% (${String.format("+.2f", improvement)}%)")
                }
                return true
            } else {
                println("📉 Similarity for $testName: ${String.format("%.2f", newScore)}% (previous best: ${String.format("%.2f", previousScore)}%)")
                
                // Check if the drop is significant (more than 1% drop)
                val drop = previousScore - newScore
                if (drop > 1.0) {
                    println("❌ Significant similarity drop detected: ${String.format("%.2f", drop)}% decrease")
                    return false
                } else {
                    println("⚠️  Minor similarity fluctuation: ${String.format("%.2f", drop)}% decrease (within tolerance)")
                    return true
                }
            }
        }
    }
}

/**
 * Test utilities for Skia GM tests using Kotlin Test framework
 */
object TestUtils {
    
    /**
     * Run a GM test and return the resulting bitmap
     */
    fun runGmTest(gm: GM, width: Int, height: Int): Bitmap {
        val canvas = CanvasFactory.create(width, height)
        
        // Set white background
        val bgPaint = Paint().apply {
            color = Color(0xFF, 0xFF, 0xFF, 255)
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), bgPaint)
        
        // Run the GM test
        gm.onDraw(canvas)
        
        // Get the bitmap from the canvas
        return canvas.getBitmapCopy()
    }
    
    /**
     * Compare two bitmaps and return similarity percentage
     */
    fun compareBitmaps(bitmap1: Bitmap, bitmap2: Bitmap): Double {
        if (bitmap1.getWidth() != bitmap2.getWidth() || bitmap1.getHeight() != bitmap2.getHeight()) {
            return 0.0
        }
        
        var matchingPixels = 0
        val totalPixels = bitmap1.getWidth() * bitmap1.getHeight()
        
        for (y in 0 until bitmap1.getHeight()) {
            for (x in 0 until bitmap1.getWidth()) {
                val color1 = bitmap1.getPixel(x, y)
                val color2 = bitmap2.getPixel(x, y)
                
                if (color1 == color2) {
                    matchingPixels++
                }
            }
        }
        
        return (matchingPixels.toDouble() / totalPixels.toDouble()) * 100.0
    }
    
    /**
     * Load reference image from resources using Java ImageIO
     */
    fun loadReferenceImage(name: String): Bitmap? {
        return try {
            val resourcePath = "original-888/$name.png"
            val resourceUrl = TestUtils::class.java.classLoader.getResource(resourcePath)
            
            if (resourceUrl != null) {
                val bufferedImage = ImageIO.read(File(resourceUrl.toURI()))
                return bufferedImageToBitmap(bufferedImage)
            } else {
                println("⚠️  Reference image not found: $resourcePath")
                null
            }
        } catch (e: Exception) {
            println("⚠️  Could not load reference image: $name - ${e.message}")
            null
        }
    }
    
    /**
     * Convert BufferedImage to Kanvas Bitmap
     */
    private fun bufferedImageToBitmap(bufferedImage: BufferedImage): Bitmap {
        val width = bufferedImage.width
        val height = bufferedImage.height
        val bitmap = Bitmap.create(width, height)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = bufferedImage.getRGB(x, y)
                val alpha = (rgb shr 24) and 0xFF
                val red = (rgb shr 16) and 0xFF
                val green = (rgb shr 8) and 0xFF
                val blue = rgb and 0xFF
                
                bitmap.setPixel(x, y, Color(red, green, blue, alpha))
            }
        }
        
        return bitmap
    }
    
    /**
     * Save bitmap to file for debugging
     */
    fun saveDebugImage(bitmap: Bitmap, name: String) {
        try {
            val debugDir = File("build/debug-images")
            if (!debugDir.exists()) {
                debugDir.mkdirs()
            }
            
            val outputFile = File(debugDir, "$name.png")
            exportBitmapToPNG(bitmap, outputFile.absolutePath)
            println("💾 Debug image saved: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            println("❌ Could not save debug image: ${e.message}")
        }
    }
    
    /**
     * Export bitmap to PNG using Java's ImageIO
     */
    private fun exportBitmapToPNG(bitmap: Bitmap, filename: String) {
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
            ImageIO.write(bufferedImage, "png", File(filename))
            
        } catch (e: Exception) {
            println("Error exporting PNG: ${e.message}")
        }
    }
}