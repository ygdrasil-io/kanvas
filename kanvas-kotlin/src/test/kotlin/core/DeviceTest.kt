package com.kanvas.core

import device.BitmapDevice
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceTest {

    @Test
    fun `test raster device creation`() {
        val width = 256
        val height = 256
        val device = Devices.makeRaster(width, height)
        
        assertEquals(width, device.width)
        assertEquals(height, device.height)
        assertEquals(ColorType.RGBA_8888, device.colorInfo.colorType)
        assertEquals(AlphaType.PREMUL, device.colorInfo.alphaType)
        assertEquals(ColorSpace.SRGB, device.colorInfo.colorSpace)
        
        // Verify bitmap dimensions
        assertEquals(width, device.bitmap.getWidth())
        assertEquals(height, device.bitmap.getHeight())
    }

    @Test
    fun `test device from bitmap`() {
        val width = 128
        val height = 128
        val originalBitmap = Bitmap(width, height, BitmapConfig.ARGB_8888)
        
        // Set some pixels in the original bitmap
        originalBitmap.setPixel(10, 10, Color.RED)
        originalBitmap.setPixel(20, 20, Color.BLUE)
        
        val device = Devices.makeFromBitmap(originalBitmap)
        
        assertEquals(width, device.width)
        assertEquals(height, device.height)
        
        // Verify that pixels were copied
        assertEquals(Color.RED, device.bitmap.getPixel(10, 10))
        assertEquals(Color.BLUE, device.bitmap.getPixel(20, 20))
    }

    @Test
    fun `test device clear`() {
        val device = Devices.makeRaster(100, 100)
        
        // Initially should be transparent
        assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(50, 50))
        
        // Clear with a color
        device.clear(Color.BLUE)
        
        // All pixels should now be blue
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                assertEquals(Color.BLUE, device.bitmap.getPixel(x, y))
            }
        }
    }

    @Test
    fun `test device draw rect fill`() {
        val device = Devices.makeRaster(100, 100)
        
        val paint = Paint().apply {
            color = Color.RED
            style = PaintStyle.FILL
        }
        
        val rect = Rect(10f, 10f, 30f, 30f)
        device.drawRect(rect, paint)
        
        // Check that the rectangle area is filled with red
        for (y in 10 until 30) {
            for (x in 10 until 30) {
                assertEquals(Color.RED, device.bitmap.getPixel(x, y))
            }
        }
        
        // Check that areas outside the rectangle are still transparent
        assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(5, 5))
        assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(35, 35))
    }

    @Test
    fun `test device draw rect stroke`() {
        val device = Devices.makeRaster(100, 100)
        
        val paint = Paint().apply {
            color = Color.GREEN
            style = PaintStyle.STROKE
        }
        
        val rect = Rect(20f, 20f, 40f, 40f)
        device.drawRect(rect, paint)
        
        // Check border pixels (stroke)
        // Top border
        for (x in 20 until 40) {
            assertEquals(Color.GREEN, device.bitmap.getPixel(x, 20))
        }
        
        // Bottom border
        for (x in 20 until 40) {
            assertEquals(Color.GREEN, device.bitmap.getPixel(x, 39))
        }
        
        // Left border
        for (y in 20 until 40) {
            assertEquals(Color.GREEN, device.bitmap.getPixel(20, y))
        }
        
        // Right border
        for (y in 20 until 40) {
            assertEquals(Color.GREEN, device.bitmap.getPixel(39, y))
        }
        
        // Check that inside is still transparent (stroke only)
        assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(30, 30))
    }

    @Test
    fun `test device clip bounds`() {
        val device = Devices.makeRaster(100, 100)
        
        // Initial clip bounds should be the full device
        val initialClip = device.getClipBounds()
        assertEquals(0f, initialClip.left)
        assertEquals(0f, initialClip.top)
        assertEquals(100f, initialClip.right)
        assertEquals(100f, initialClip.bottom)
        
        // Test that drawing outside clip bounds doesn't affect the bitmap
        if (device is BitmapDevice) {
            device.setClipBounds(Rect(20f, 20f, 80f, 80f))
            
            val paint = Paint().apply {
                color = Color.RED
                style = PaintStyle.FILL
            }
            
            // Draw a rectangle that's partially outside the clip
            val rect = Rect(10f, 10f, 90f, 90f)
            device.drawRect(rect, paint)
            
            // Check that pixels outside the clip are still transparent
            assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(15, 15))
            assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(85, 85))
            
            // Check that pixels inside the clip are red
            assertEquals(Color.RED, device.bitmap.getPixel(30, 30))
        }
    }

    @Test
    fun `test device matrix transform`() {
        val device = Devices.makeRaster(100, 100)
        
        if (device is BitmapDevice) {
            // Apply a translation
            val translationMatrix = Matrix.identity().translate(10f, 20f)
            device.setMatrix(translationMatrix)
            
            val paint = Paint().apply {
                color = Color.BLUE
                style = PaintStyle.FILL
            }
            
            // Draw a rectangle at (0, 0) - it should be translated to (10, 20)
            val rect = Rect(0f, 0f, 10f, 10f)
            device.drawRect(rect, paint)
            
            // The rectangle should appear at the translated position
            assertEquals(Color.BLUE, device.bitmap.getPixel(15, 25))
            assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(5, 5))
        }
    }

    @Test
    fun `test device draw image`() {
        val device = Devices.makeRaster(100, 100)
        
        // Create a small source image
        val sourceImage = Bitmap(10, 10, BitmapConfig.ARGB_8888).apply {
            // Fill with red
            for (y in 0 until 10) {
                for (x in 0 until 10) {
                    setPixel(x, y, Color.RED)
                }
            }
        }
        
        val paint = Paint()
        
        // Draw the image at (10, 10) with 1:1 scaling
        val srcRect = Rect(0f, 0f, 10f, 10f)
        val dstRect = Rect(10f, 10f, 20f, 20f)
        device.drawImage(sourceImage, srcRect, dstRect, paint)
        
        // Check that the image was drawn
        assertEquals(Color.RED, device.bitmap.getPixel(15, 15))
    }

    @Test
    fun `test canvas with device`() {
        val canvas = CanvasFactory.createRaster(100, 100)
        
        assertEquals(100, canvas.width)
        assertEquals(100, canvas.height)
        
        // Test drawing
        val paint = Paint().apply {
            color = Color.GREEN
            style = PaintStyle.FILL
        }
        
        canvas.drawRect(Rect(10f, 10f, 30f, 30f), paint)
        
        // Verify the drawing went through to the device
        assertEquals(Color.GREEN, canvas.bitmap.getPixel(20, 20))
    }

    @Test
    fun `test canvas state management`() {
        val canvas = CanvasFactory.createRaster(100, 100)
        
        // Save initial state
        canvas.save()
        
        // Apply transformations
        canvas.translate(10f, 20f)
        canvas.scale(2f, 2f)
        
        // Verify transformations are applied
        val transformedMatrix = canvas.getTotalMatrix()
        assertTrue(transformedMatrix != Matrix.identity())
        
        // Restore to initial state
        canvas.restore()
        
        // Verify we're back to identity
        val restoredMatrix = canvas.getTotalMatrix()
        assertEquals(Matrix.identity(), restoredMatrix)
    }

    @Test
    fun `test canvas clipping`() {
        val canvas = CanvasFactory.createRaster(100, 100)
        
        // Apply a clip
        val clipRect = Rect(20f, 20f, 80f, 80f)
        canvas.clipRect(clipRect, SkClipOp.INTERSECT)
        
        // Verify clip is applied
        val currentClip = canvas.getClipBounds()
        assertEquals(clipRect, currentClip)
        
        // Draw something
        val paint = Paint().apply {
            color = Color.BLUE
            style = PaintStyle.FILL
        }
        
        // This should be clipped
        canvas.drawRect(Rect(0f, 0f, 100f, 100f), paint)
        
        // Check that drawing outside clip didn't happen
        assertEquals(Color.TRANSPARENT, canvas.bitmap.getPixel(10, 10))
        
        // Check that drawing inside clip happened
        assertEquals(Color.BLUE, canvas.bitmap.getPixel(50, 50))
    }
}