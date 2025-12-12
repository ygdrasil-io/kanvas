package core

import com.kanvas.core.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

/**
 * Test suite for SkImageInterface implementation
 * Verifies that Kanvas Bitmap implements all required Skia-compatible image methods
 */
class SkImageInterfaceTest {

    @Test
    fun `test SkImageInterface implementation`() {
        // Create a bitmap and verify it implements the interface
        val bitmap = Bitmap(100, 100, BitmapConfig.ARGB_8888)
        assertTrue(bitmap is SkImageInterface, "Bitmap should implement SkImageInterface")
    }

    @Test
    fun `test basic image properties`() {
        val bitmap = Bitmap(200, 300, BitmapConfig.ARGB_8888)
        
        // Test dimensions
        assertEquals(200, bitmap.getWidth())
        assertEquals(300, bitmap.getHeight())
        
        // Test config
        assertEquals(BitmapConfig.ARGB_8888, bitmap.getConfig())
        
        // Test row bytes
        assertEquals(200 * 4, bitmap.getRowBytes()) // 4 bytes per pixel for ARGB_8888
        
        // Test color info
        assertEquals(ColorType.RGBA_8888, bitmap.getColorInfo().colorType)
        assertEquals(AlphaType.PREMUL, bitmap.getColorInfo().alphaType)
        assertEquals(ColorSpace.SRGB, bitmap.getColorInfo().colorSpace)
    }

    @Test
    fun `test pixel access`() {
        val bitmap = Bitmap(10, 10, BitmapConfig.ARGB_8888)
        
        // Test initial pixel (should be transparent black by default)
        val initialPixel = bitmap.getPixel(0, 0)
        assertEquals(Color(0, 0, 0, 0), initialPixel)
        
        // Test setting and getting pixel
        val testColor = Color(255, 128, 64, 200)
        bitmap.setPixel(5, 5, testColor)
        val retrievedColor = bitmap.getPixel(5, 5)
        assertEquals(testColor, retrievedColor)
        
        // Test eraseColor
        bitmap.eraseColor(Color(100, 150, 200, 255))
        val erasedColor = bitmap.getPixel(0, 0)
        assertEquals(Color(100, 150, 200, 255), erasedColor)
    }

    @Test
    fun `test image manipulation`() {
        val bitmap = Bitmap(50, 50, BitmapConfig.ARGB_8888)
        
        // Fill with a color
        bitmap.eraseColor(Color.RED)
        
        // Test copy
        val copy = bitmap.copy()
        assertEquals(bitmap.getWidth(), copy.getWidth())
        assertEquals(bitmap.getHeight(), copy.getHeight())
        assertEquals(bitmap.getPixel(25, 25), copy.getPixel(25, 25))
        
        // Test extractSubset
        val subset = bitmap.extractSubset(Rect(10f, 10f, 40f, 40f))
        assertEquals(30, subset.getWidth())
        assertEquals(30, subset.getHeight())
        assertEquals(Color.RED, subset.getPixel(10, 10))
        
        // Test scaling
        val scaled = bitmap.scale(100, 100, SamplingOptions.linear())
        assertEquals(100, scaled.getWidth())
        assertEquals(100, scaled.getHeight())
        
        // Test color filter
        val colorFilter = ColorFilter.makeBlend(Color.BLUE, BlendMode.SRC_OVER)
        val filtered = bitmap.applyColorFilter(colorFilter)
        // The result should be different from the original
        assertNotEquals(bitmap.getPixel(25, 25), filtered.getPixel(25, 25))
    }

    @Test
    fun `test image analysis`() {
        val bitmap = Bitmap(10, 10, BitmapConfig.ARGB_8888)
        
        // Test empty bitmap
        assertFalse(bitmap.hasNonTransparentPixels())
        assertTrue(bitmap.isOpaque()) // Empty is considered opaque
        
        // Test with transparent pixels
        bitmap.setPixel(5, 5, Color(255, 0, 0, 0)) // Transparent red
        assertFalse(bitmap.isOpaque())
        assertTrue(bitmap.hasNonTransparentPixels()) // Should still have the transparent pixel
        
        // Test with opaque pixels
        bitmap.eraseColor(Color.RED) // Opaque red
        assertTrue(bitmap.isOpaque())
        assertTrue(bitmap.hasNonTransparentPixels())
    }

    @Test
    fun `test image conversion`() {
        val bitmap = Bitmap(5, 5, BitmapConfig.ARGB_8888)
        bitmap.eraseColor(Color(100, 150, 200, 255))
        
        // Test toByteBuffer
        val buffer = bitmap.toByteBuffer()
        assertEquals(5 * 5 * 4, buffer.capacity()) // 5x5 pixels, 4 bytes each
        
        // Test toByteArray
        val byteArray = bitmap.toByteArray()
        assertEquals(5 * 5 * 4, byteArray.size)
    }

    @Test
    fun `test Skia-compatible methods`() {
        val bitmap = Bitmap(100, 100, BitmapConfig.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)
        
        // Test makeSubset
        val subset = bitmap.makeSubset(10, 20, 90, 80)
        assertEquals(80, subset.getWidth())  // 90 - 10
        assertEquals(60, subset.getHeight()) // 80 - 20
        
        // Test makeTextureImage
        val textureImage = bitmap.makeTextureImage()
        assertEquals(bitmap.getWidth(), textureImage.getWidth())
        assertEquals(bitmap.getHeight(), textureImage.getHeight())
        
        // Test makeShader
        val shader = bitmap.makeShader(TileMode.CLAMP, TileMode.CLAMP, null)
        assertTrue(shader is BitmapShader)
        
        // Test makeNonTextureImage
        val nonTexture = bitmap.makeNonTextureImage()
        assertEquals(bitmap.getWidth(), nonTexture.getWidth())
        assertEquals(bitmap.getHeight(), nonTexture.getHeight())
        
        // Test makeRasterImage
        val raster = bitmap.makeRasterImage()
        assertEquals(bitmap.getWidth(), raster.getWidth())
        assertEquals(bitmap.getHeight(), raster.getHeight())
    }

    @Test
    fun `test metadata methods`() {
        val bitmap = Bitmap(50, 50, BitmapConfig.ARGB_8888)
        
        // Test alpha type
        bitmap.eraseColor(Color.RED) // Opaque
        assertEquals(AlphaType.OPAQUE, bitmap.getAlphaType())
        
        bitmap.setPixel(25, 25, Color(255, 0, 0, 128)) // Semi-transparent
        assertEquals(AlphaType.PREMUL, bitmap.getAlphaType())
        
        // Test color type
        assertEquals(ColorType.RGBA_8888, bitmap.getColorType())
        
        // Test color space
        assertEquals(ColorSpace.SRGB, bitmap.getColorSpace())
        
        // Test isAlphaOnly
        assertFalse(bitmap.isAlphaOnly())
        
        // Test alpha-only bitmap
        val alphaBitmap = Bitmap(10, 10, BitmapConfig.ALPHA_8)
        assertTrue(alphaBitmap.isAlphaOnly())
        
        // Test other metadata
        assertFalse(bitmap.isVolatile())
        assertFalse(bitmap.isLazyGenerated())
        assertNotEquals(0L, bitmap.uniqueID())
    }

    @Test
    fun `test readPixels methods`() {
        val bitmap = Bitmap(10, 10, BitmapConfig.ARGB_8888)
        
        // Fill with a pattern
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                val colorValue = (x + y * 10) * 25
                bitmap.setPixel(x, y, Color(colorValue, colorValue, colorValue, 255))
            }
        }
        
        // Test readPixels with ByteBuffer
        val dstInfo = ImageInfo(5, 5, ColorType.RGBA_8888, AlphaType.PREMUL, ColorSpace.SRGB)
        val buffer = ByteBuffer.allocate(5 * 5 * 4)
        val success = bitmap.readPixels(dstInfo, buffer, 5 * 4, 2, 3)
        assertTrue(success)
        
        // Test readPixels with ByteArray
        val byteArray = ByteArray(5 * 5 * 4)
        val success2 = bitmap.readPixels(dstInfo, byteArray, 5 * 4, 2, 3)
        assertTrue(success2)
        
        // Test readPixels with IntArray
        val intArray = IntArray(5 * 5)
        val success3 = bitmap.readPixels(dstInfo, intArray, 5 * 4, 2, 3)
        assertTrue(success3)
        
        // Test invalid parameters
        val invalidInfo = ImageInfo(20, 20, ColorType.RGBA_8888, AlphaType.PREMUL, ColorSpace.SRGB)
        val invalidBuffer = ByteBuffer.allocate(20 * 20 * 4)
        val invalidSuccess = bitmap.readPixels(invalidInfo, invalidBuffer, 20 * 4, 5, 5)
        assertFalse(invalidSuccess) // Should fail because 5+20 > 10
    }

    @Test
    fun `test filter methods`() {
        val bitmap = Bitmap(20, 20, BitmapConfig.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        
        // Test makeWithFilter
        val blurFilter = BlurFilter(5f)
        val filtered = bitmap.makeWithFilter(blurFilter, null, null, null)
        assertEquals(bitmap.getWidth(), filtered.getWidth())
        assertEquals(bitmap.getHeight(), filtered.getHeight())
        
        // Test makeColorSpace
        val colorSpaceBitmap = bitmap.makeColorSpace(ColorSpace.LINEAR)
        assertEquals(bitmap.getWidth(), colorSpaceBitmap.getWidth())
        assertEquals(bitmap.getHeight(), colorSpaceBitmap.getHeight())
        
        // Test makeColorTypeAndColorSpace
        val colorTypeBitmap = bitmap.makeColorTypeAndColorSpace(ColorType.RGB_565, ColorSpace.LINEAR)
        assertEquals(bitmap.getWidth(), colorTypeBitmap.getWidth())
        assertEquals(bitmap.getHeight(), colorTypeBitmap.getHeight())
        
        // Test makeSubsetWithFilter
        val subset = Rect(5f, 5f, 15f, 15f)
        val colorFilter = ColorFilter.makeBlend(Color.RED, BlendMode.SRC_OVER)
        val colorFilterImageFilter = ColorFilterImageFilter(colorFilter)
        val subsetFiltered = bitmap.makeSubsetWithFilter(colorFilterImageFilter, subset)
        assertEquals(10, subsetFiltered.getWidth())
        assertEquals(10, subsetFiltered.getHeight())
        
        // Test makeWithFilterAndColorSpace
        val filterAndColorSpace = bitmap.makeWithFilterAndColorSpace(blurFilter, ColorSpace.LINEAR)
        assertEquals(bitmap.getWidth(), filterAndColorSpace.getWidth())
        assertEquals(bitmap.getHeight(), filterAndColorSpace.getHeight())
    }

    @Test
    fun `test Skia compatibility patterns`() {
        val bitmap = Bitmap(100, 100, BitmapConfig.ARGB_8888)
        
        // Test creating from pixels
        val pixels = IntArray(10 * 10)
        for (i in pixels.indices) {
            pixels[i] = 0xFF0000FF.toInt() // Opaque blue
        }
        val fromPixels = Bitmap.createFromPixels(pixels, 10, 10, BitmapConfig.ARGB_8888)
        assertEquals(10, fromPixels.getWidth())
        assertEquals(10, fromPixels.getHeight())
        assertEquals(Color(0, 0, 255, 255), fromPixels.getPixel(0, 0))
        
        // Test various configurations
        val alphaBitmap = Bitmap(5, 5, BitmapConfig.ALPHA_8)
        assertEquals(BitmapConfig.ALPHA_8, alphaBitmap.getConfig())
        assertEquals(ColorType.ALPHA_8, alphaBitmap.getColorType())
        
        val rgbBitmap = Bitmap(5, 5, BitmapConfig.RGB_565)
        assertEquals(BitmapConfig.RGB_565, rgbBitmap.getConfig())
        assertEquals(ColorType.RGB_565, rgbBitmap.getColorType())
    }
}