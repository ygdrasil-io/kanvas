package core

import com.kanvas.core.Bitmap
import com.kanvas.core.BitmapConfig
import com.kanvas.core.Color
import com.kanvas.core.SamplingOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Test for SamplingOptions functionality
 */
class SamplingOptionsTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun testSamplingOptionsNearest() {
        // Create a simple test bitmap
        val original = Bitmap(2, 2, BitmapConfig.ARGB_8888)
        original.setPixel(0, 0, Color.RED)
        original.setPixel(1, 0, Color.GREEN)
        original.setPixel(0, 1, Color.BLUE)
        original.setPixel(1, 1, Color.WHITE)

        // Test nearest neighbor scaling
        val sampling = SamplingOptions.nearest()
        val scaled = sampling.applyToBitmap(original, 4, 4)

        // Verify dimensions
        assertEquals(4, scaled.getWidth())
        assertEquals(4, scaled.getHeight())

        // Verify that nearest neighbor scaling works (should preserve original colors)
        // Top-left should be red
        assertEquals(Color.RED, scaled.getPixel(0, 0))
        // Top-right should be green
        assertEquals(Color.GREEN, scaled.getPixel(3, 0))
        // Bottom-left should be blue
        assertEquals(Color.BLUE, scaled.getPixel(0, 3))
        // Bottom-right should be white
        assertEquals(Color.WHITE, scaled.getPixel(3, 3))
    }

    @Test
    fun testSamplingOptionsLinear() {
        // Create a simple test bitmap
        val original = Bitmap(2, 2, BitmapConfig.ARGB_8888)
        original.setPixel(0, 0, Color.RED)
        original.setPixel(1, 0, Color.GREEN)
        original.setPixel(0, 1, Color.BLUE)
        original.setPixel(1, 1, Color.WHITE)

        // Test linear scaling
        val sampling = SamplingOptions.linear()
        val scaled = sampling.applyToBitmap(original, 4, 4)

        // Verify dimensions
        assertEquals(4, scaled.getWidth())
        assertEquals(4, scaled.getHeight())

        // Linear interpolation should create blended colors
        // This is a basic test - just verify it doesn't crash and produces reasonable results
        val centerColor = scaled.getPixel(2, 2)
        assertNotEquals(Color.TRANSPARENT, centerColor)
        assertNotEquals(Color.BLACK, centerColor)
    }

    @Test
    fun testSamplingOptionsMitchell() {
        // Create a simple test bitmap
        val original = Bitmap(3, 3, BitmapConfig.ARGB_8888)
        original.setPixel(0, 0, Color.RED)
        original.setPixel(1, 0, Color.GREEN)
        original.setPixel(2, 0, Color.BLUE)
        original.setPixel(0, 1, Color.YELLOW)
        original.setPixel(1, 1, Color.CYAN)
        original.setPixel(2, 1, Color.MAGENTA)
        original.setPixel(0, 2, Color.BLACK)
        original.setPixel(1, 2, Color.WHITE)
        original.setPixel(2, 2, Color.GRAY)

        // Test Mitchell cubic scaling
        val sampling = SamplingOptions.mitchell()
        val scaled = sampling.applyToBitmap(original, 6, 6)

        // Verify dimensions
        assertEquals(6, scaled.getWidth())
        assertEquals(6, scaled.getHeight())

        // Cubic interpolation should create smooth transitions
        // This is a basic test - just verify it doesn't crash and produces reasonable results
        val centerColor = scaled.getPixel(3, 3)
        assertNotEquals(Color.TRANSPARENT, centerColor)
        assertNotEquals(Color.BLACK, centerColor)
    }

    @Test
    fun testSamplingOptionsIntegration() {
        // This test verifies that SamplingOptions can be created and used
        val nearest = SamplingOptions.nearest()
        val linear = SamplingOptions.linear()
        val mitchell = SamplingOptions.mitchell()
        val catmullRom = SamplingOptions.catmullRom()
        val bSpline = SamplingOptions.bSpline()
        
        // Verify that all sampling options have the correct filter modes
        assertEquals(com.kanvas.core.FilterMode.NEAREST, nearest.filterMode)
        assertEquals(com.kanvas.core.FilterMode.LINEAR, linear.filterMode)
        assertEquals(com.kanvas.core.FilterMode.CUBIC, mitchell.filterMode)
        assertEquals(com.kanvas.core.FilterMode.CUBIC, catmullRom.filterMode)
        assertEquals(com.kanvas.core.FilterMode.CUBIC, bSpline.filterMode)
        
        // Verify that cubic resamplers are set correctly
        assertEquals(com.kanvas.core.CubicResampler.Mitchell, mitchell.cubicResampler)
        assertEquals(com.kanvas.core.CubicResampler.CatmullRom, catmullRom.cubicResampler)
        assertEquals(com.kanvas.core.CubicResampler.BSpline, bSpline.cubicResampler)
    }
}