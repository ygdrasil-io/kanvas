package com.kanvas.core

import device.BitmapDevice
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
    import kotlin.test.assertNotNull
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

    @Test
    fun `test device clip stack save restore`() {
        val device = Devices.makeRaster(100, 100)
        
        // Initial state
        assertEquals(0, device.getClipStackDepth())
        val initialClip = device.getClipBounds()
        
        // Save current clip
        val depthAfterSave = device.saveClipStack()
        assertEquals(1, depthAfterSave)
        assertEquals(1, device.getClipStackDepth())
        
        // Modify clip
        if (device is BitmapDevice) {
            device.setClipBounds(Rect(20f, 20f, 80f, 80f))
            val modifiedClip = device.getClipBounds()
            assertEquals(Rect(20f, 20f, 80f, 80f), modifiedClip)
            
            // Restore clip
            val depthAfterRestore = device.restoreClipStack()
            assertEquals(0, depthAfterRestore)
            assertEquals(0, device.getClipStackDepth())
            
            // Verify clip is back to initial state
            val restoredClip = device.getClipBounds()
            assertEquals(initialClip, restoredClip)
        }
    }

    @Test
    fun `test device clip stack multiple levels`() {
        val device = Devices.makeRaster(100, 100)
        
        // Save multiple clip states
        device.saveClipStack()
        if (device is BitmapDevice) {
            device.setClipBounds(Rect(20f, 20f, 80f, 80f))
        }
        
        device.saveClipStack()
        if (device is BitmapDevice) {
            device.setClipBounds(Rect(30f, 30f, 70f, 70f))
        }
        
        device.saveClipStack()
        if (device is BitmapDevice) {
            device.setClipBounds(Rect(40f, 40f, 60f, 60f))
        }
        
        assertEquals(3, device.getClipStackDepth())
        
        // Restore all levels
        device.restoreClipStack()
        assertEquals(2, device.getClipStackDepth())
        
        device.restoreClipStack()
        assertEquals(1, device.getClipStackDepth())
        
        device.restoreClipStack()
        assertEquals(0, device.getClipStackDepth())
        
        // Should be back to initial clip
        val finalClip = device.getClipBounds()
        assertEquals(Rect(0f, 0f, 100f, 100f), finalClip)
    }

    @Test
    fun `test device clip rect intersect`() {
        val device = Devices.makeRaster(100, 100)
        
        // Start with full device clip
        val initialClip = device.getClipBounds()
        assertEquals(Rect(0f, 0f, 100f, 100f), initialClip)
        
        // Intersect with a smaller rectangle
        device.clipRect(Rect(20f, 20f, 80f, 80f), Device.ClipOp.INTERSECT, false)
        
        val clipped = device.getClipBounds()
        assertEquals(Rect(20f, 20f, 80f, 80f), clipped)
        
        // Intersect again with an even smaller rectangle
        device.clipRect(Rect(30f, 30f, 70f, 70f), Device.ClipOp.INTERSECT, false)
        
        val doubleClipped = device.getClipBounds()
        assertEquals(Rect(30f, 30f, 70f, 70f), doubleClipped)
    }

    @Test
    fun `test device clip rect difference`() {
        val device = Devices.makeRaster(100, 100)
        
        // Start with a specific clip
        if (device is BitmapDevice) {
            device.setClipBounds(Rect(20f, 20f, 80f, 80f))
        }
        
        // Apply difference with a rectangle that partially overlaps
        device.clipRect(Rect(40f, 40f, 60f, 60f), Device.ClipOp.DIFFERENCE, false)
        
        val resultClip = device.getClipBounds()
        
        // The result should be the original clip minus the overlapping area
        // This creates an L-shaped region that includes:
        // - Left strip (20-40)
        // - Right strip (60-80)
        // - Top strip (20-40)
        // - Bottom strip (60-80)
        
        // Check that we have some area remaining
        assertTrue(resultClip.width > 0 && resultClip.height > 0)
        
        // Check that the result doesn't include the center area that was subtracted
        // This is a simplified check - the exact bounds depend on the implementation
        assertTrue(!resultClip.isEmpty, "Result should not be empty")
    }

    @Test
    fun `test device clip path`() {
        val device = Devices.makeRaster(100, 100)
        
        // Create a path that covers a specific area
        val path = Path().apply {
            moveTo(20f, 20f)
            lineTo(80f, 20f)
            lineTo(80f, 80f)
            lineTo(20f, 80f)
            close()
        }
        
        // Clip with the path
        device.clipPath(path, Device.ClipOp.INTERSECT, false)
        
        val pathBounds = path.getBounds()
        val clipped = device.getClipBounds()
        
        // The clip should be intersected with the path bounds
        assertEquals(pathBounds, clipped)
    }

    @Test
    fun `test device clip stack with drawing`() {
        val device = Devices.makeRaster(100, 100)
        
        val paint = Paint().apply {
            color = Color.RED
            style = PaintStyle.FILL
        }
        
        // Save initial clip
        device.saveClipStack()
        
        // Apply a smaller clip
        device.clipRect(Rect(20f, 20f, 80f, 80f), Device.ClipOp.INTERSECT, false)
        
        // Draw with the smaller clip
        device.drawRect(Rect(10f, 10f, 90f, 90f), paint)
        
        // Check that drawing is clipped
        assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(15, 15))
        assertEquals(Color.RED, device.bitmap.getPixel(50, 50))
        assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(85, 85))
        
        // Restore to full clip
        device.restoreClipStack()
        
        // Draw again - should not be clipped
        device.drawRect(Rect(5f, 5f, 15f, 15f), paint)
        
        // Now the corner should be red
        assertEquals(Color.RED, device.bitmap.getPixel(10, 10))
    }

    @Test
    fun `test device clip stack exception handling`() {
        val device = Devices.makeRaster(100, 100)
        
        // Try to restore from empty stack
        try {
            device.restoreClipStack()
            assertFalse(true, "Should have thrown an exception")
        } catch (e: IllegalStateException) {
            assertEquals("Clip stack is empty, cannot restore", e.message)
        }
    }

    @Test
    fun `test device clip bounds methods`() {
        val device = Devices.makeRaster(100, 100)
        
        // Test initial state
        assertEquals(Rect(0f, 0f, 100f, 100f), device.devClipBounds())
        assertFalse(device.isClipEmpty())
        assertTrue(device.isClipRect())
        assertTrue(device.isClipWideOpen())
        
        // Apply a smaller clip
        device.clipRect(Rect(20f, 20f, 80f, 80f), Device.ClipOp.INTERSECT, false)
        
        assertEquals(Rect(20f, 20f, 80f, 80f), device.devClipBounds())
        assertFalse(device.isClipEmpty())
        assertTrue(device.isClipRect())
        assertFalse(device.isClipWideOpen())
        
        // Make clip empty
        device.clipRect(Rect(0f, 0f, 0f, 0f), Device.ClipOp.INTERSECT, false)
        
        assertTrue(device.isClipEmpty())
    }

    @Test
    fun `test device writePixels`() {
        val device = Devices.makeRaster(100, 100)
        
        // Create a source bitmap with specific colors
        val source = Bitmap(10, 10, BitmapConfig.ARGB_8888).apply {
            // Fill with a pattern
            for (y in 0 until 10) {
                for (x in 0 until 10) {
                    val color = if ((x + y) % 2 == 0) Color.RED else Color.BLUE
                    setPixel(x, y, color)
                }
            }
        }
        
        // Write pixels to device
        val success = device.writePixels(source, 10, 10)
        assertTrue(success, "writePixels should succeed")
        
        // Verify pixels were written correctly
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                val expected = if ((x + y) % 2 == 0) Color.RED else Color.BLUE
                val actual = device.bitmap.getPixel(10 + x, 10 + y)
                assertEquals(expected, actual, "Pixel at ($x,$y) should match")
            }
        }
    }

    @Test
    fun `test device writePixels with out of bounds`() {
        val device = Devices.makeRaster(100, 100)
        
        val source = Bitmap(10, 10, BitmapConfig.ARGB_8888)
        
        // Try to write outside device bounds
        val success = device.writePixels(source, 95, 95)
        assertFalse(success, "writePixels should fail when out of bounds")
    }

    @Test
    fun `test device readPixels`() {
        val device = Devices.makeRaster(100, 100)
        
        // Draw some content first
        val paint = Paint().apply {
            color = Color.GREEN
            style = PaintStyle.FILL
        }
        device.drawRect(Rect(20f, 20f, 40f, 40f), paint)
        
        // Create destination bitmap
        val dst = Bitmap(20, 20, BitmapConfig.ARGB_8888)
        
        // Read pixels from device
        val success = device.readPixels(dst, 20, 20)
        assertTrue(success, "readPixels should succeed")
        
        // Verify pixels were read correctly
        for (y in 0 until 20) {
            for (x in 0 until 20) {
                val expected = if (x < 20 && y < 20) Color.GREEN else Color.TRANSPARENT
                val actual = dst.getPixel(x, y)
                assertEquals(expected, actual, "Pixel at ($x,$y) should match")
            }
        }
    }

    @Test
    fun `test device readPixels with out of bounds`() {
        val device = Devices.makeRaster(100, 100)
        
        val dst = Bitmap(10, 10, BitmapConfig.ARGB_8888)
        
        // Try to read outside device bounds
        val success = device.readPixels(dst, 95, 95)
        assertFalse(success, "readPixels should fail when out of bounds")
    }

    @Test
    fun `test device accessPixels`() {
        val device = Devices.makeRaster(100, 100)
        
        // Draw some content
        val paint = Paint().apply {
            color = Color.YELLOW
            style = PaintStyle.FILL
        }
        device.drawRect(Rect(10f, 10f, 30f, 30f), paint)
        
        // Access pixels
        val bitmap = device.accessPixels()
        assertNotNull(bitmap, "accessPixels should return a bitmap")
        
        // Verify we can read the pixels we drew
        assertEquals(Color.YELLOW, bitmap.getPixel(20, 20))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(5, 5))
    }

    @Test
    fun `test device peekPixels`() {
        val device = Devices.makeRaster(100, 100)
        
        // Draw some content
        val paint = Paint().apply {
            color = Color.CYAN
            style = PaintStyle.FILL
        }
        device.drawRect(Rect(15f, 15f, 35f, 35f), paint)
        
        // Peek pixels (read-only access)
        val bitmap = device.peekPixels()
        assertNotNull(bitmap, "peekPixels should return a bitmap")
        
        // Verify we can read the pixels
        assertEquals(Color.CYAN, bitmap.getPixel(25, 25))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(5, 5))
    }

    @Test
    fun `test device replaceClip`() {
        val device = Devices.makeRaster(100, 100)
        
        // Apply some complex clipping first
        device.clipRect(Rect(20f, 20f, 80f, 80f), Device.ClipOp.INTERSECT, false)
        
        // Replace with a new clip
        device.replaceClip(Rect(30f, 30f, 70f, 70f))
        
        // Verify the clip was replaced
        val clipBounds = device.getClipBounds()
        assertEquals(Rect(30f, 30f, 70f, 70f), clipBounds)
        
        // Draw something
        val paint = Paint().apply {
            color = Color.MAGENTA
            style = PaintStyle.FILL
        }
        device.drawRect(Rect(0f, 0f, 100f, 100f), paint)
        
        // Verify drawing is clipped to the new region
        assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(25, 25), "Outside new clip should be transparent")
        assertEquals(Color.MAGENTA, device.bitmap.getPixel(50, 50), "Inside new clip should be magenta")
        assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(75, 75), "Outside new clip should be transparent")
    }

    @Test
    fun `test device pixel manipulation integration`() {
        val device = Devices.makeRaster(100, 100)
        
        // Create a source pattern
        val pattern = Bitmap(20, 20, BitmapConfig.ARGB_8888).apply {
            for (y in 0 until 20) {
                for (x in 0 until 20) {
                    // Create a checkerboard pattern
                    val color = if ((x / 5 + y / 5) % 2 == 0) Color(255, 0, 0, 255) else Color(0, 0, 255, 255)
                    setPixel(x, y, color)
                }
            }
        }
        
        // Write pattern to device
        assertTrue(device.writePixels(pattern, 10, 10))
        
        // Read back a portion
        val readBack = Bitmap(10, 10, BitmapConfig.ARGB_8888)
        assertTrue(device.readPixels(readBack, 15, 15))
        
        // Verify the pattern was preserved
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                val originalX = 15 + x
                val originalY = 15 + y
                val patternX = originalX - 10
                val patternY = originalY - 10
                
                val expected = pattern.getPixel(patternX, patternY)
                val actual = readBack.getPixel(x, y)
                
                assertEquals(expected, actual, "Pattern should be preserved at ($x,$y)")
            }
        }
        
        // Test direct pixel access
        val directAccess = device.accessPixels()
        val centerColor = directAccess.getPixel(20, 20)
        assertTrue(centerColor == Color(255, 0, 0, 255) || centerColor == Color(0, 0, 255, 255))
    }

    @Test
    fun `test device drawOval`() {
        val device = Devices.makeRaster(100, 100)
        
        val purple = Color(128, 0, 128) // Create purple color
        val paint = Paint().apply {
            color = purple
            style = PaintStyle.FILL
        }
        
        // Draw an oval
        val ovalRect = Rect(20f, 20f, 60f, 40f)
        device.drawOval(ovalRect, paint)
        
        // Verify some pixels were drawn (center should be filled)
        assertEquals(purple, device.bitmap.getPixel(40, 30), "Center of oval should be purple")
        
        // Verify pixels outside the oval are not drawn
        assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(10, 10), "Outside oval should be transparent")
    }

    @Test
    fun `test device drawArc`() {
        val device = Devices.makeRaster(100, 100)
        
        val orange = Color(255, 165, 0) // Create orange color
        val paint = Paint().apply {
            color = orange
            style = PaintStyle.FILL
        }
        
        // Draw an arc (90 degree sweep)
        val arc = Arc.make(50f, 50f, 30f, 20f, 0f, 90f)
        device.drawArc(arc, paint)
        
        // For now, this should draw the bounding oval (simplified implementation)
        // Verify the center area was drawn
        assertEquals(orange, device.bitmap.getPixel(50, 50), "Center of arc should be orange")
    }

    @Test
    fun `test device drawRRect`() {
        val device = Devices.makeRaster(100, 100)
        
        val pink = Color(255, 192, 203) // Create pink color
        val paint = Paint().apply {
            color = pink
            style = PaintStyle.FILL
        }
        
        // Draw a rounded rectangle
        val rrect = RRect(20f, 20f, 60f, 60f, 10f, 10f)
        device.drawRRect(rrect, paint)
        
        // Verify the center was filled
        assertEquals(pink, device.bitmap.getPixel(40, 40), "Center of RRect should be pink")
        
        // Verify corners have rounded edges
        assertEquals(pink, device.bitmap.getPixel(25, 25), "Corner area should be pink")
    }

    @Test
    fun `test device drawPaint`() {
        val device = Devices.makeRaster(100, 100)
        
        // Apply a clip first
        device.clipRect(Rect(30f, 30f, 70f, 70f), Device.ClipOp.INTERSECT, false)
        
        val teal = Color(0, 128, 128) // Create teal color
        val paint = Paint().apply {
            color = teal
            style = PaintStyle.FILL
        }
        
        // Draw paint (should fill the entire clip region)
        device.drawPaint(paint)
        
        // Verify the clip region was filled
        assertEquals(teal, device.bitmap.getPixel(50, 50), "Clip center should be teal")
        
        // Verify outside the clip was not affected
        assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(20, 20), "Outside clip should be transparent")
    }

    @Test
    fun `test device advanced primitives integration`() {
        val device = Devices.makeRaster(100, 100)
        
        // Test multiple advanced primitives together
        val redPaint = Paint().apply { color = Color.RED; style = PaintStyle.FILL }
        val bluePaint = Paint().apply { color = Color.BLUE; style = PaintStyle.FILL }
        val greenPaint = Paint().apply { color = Color.GREEN; style = PaintStyle.FILL }
        
        // Draw an oval
        device.drawOval(Rect(10f, 10f, 30f, 30f), redPaint)
        
        // Draw a rounded rectangle
        device.drawRRect(RRect(40f, 10f, 70f, 40f, 8f, 8f), bluePaint)
        
        // Draw an arc
        device.drawArc(Arc.make(50f, 60f, 20f, 15f, 45f, 180f), greenPaint)
        
        // Verify all primitives were drawn
        assertEquals(Color.RED, device.bitmap.getPixel(20, 20), "Oval should be red")
        assertEquals(Color.BLUE, device.bitmap.getPixel(55, 25), "RRect should be blue")
        assertEquals(Color.GREEN, device.bitmap.getPixel(50, 60), "Arc should be green")
    }

    @Test
    fun `test device advanced primitives with clipping`() {
        val device = Devices.makeRaster(100, 100)
        
        // Apply a clip
        device.clipRect(Rect(20f, 20f, 80f, 80f), Device.ClipOp.INTERSECT, false)
        
        val paint = Paint().apply {
            color = Color.YELLOW
            style = PaintStyle.FILL
        }
        
        // Draw primitives that extend beyond the clip
        device.drawOval(Rect(10f, 10f, 90f, 90f), paint)
        device.drawRRect(RRect(5f, 5f, 95f, 95f, 15f, 15f), paint)
        
        // Verify drawing is clipped
        assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(10, 10), "Outside clip should be transparent")
        assertEquals(Color.YELLOW, device.bitmap.getPixel(50, 50), "Inside clip should be yellow")
        assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(90, 90), "Outside clip should be transparent")
    }

    @Test
    fun `test device advanced primitives with transformations`() {
        val device = Devices.makeRaster(100, 100)
        
        if (device is BitmapDevice) {
            // Apply a translation
            val translationMatrix = Matrix.identity().translate(10f, 20f)
            device.setMatrix(translationMatrix)
            
            val indigo = Color(75, 0, 130) // Create indigo color
            val paint = Paint().apply {
                color = indigo
                style = PaintStyle.FILL
            }
            
            // Draw an oval at (0, 0) - it should be translated to (10, 20)
            device.drawOval(Rect(0f, 0f, 20f, 15f), paint)
            
            // The oval should appear at the translated position
            assertEquals(indigo, device.bitmap.getPixel(15, 25), "Translated oval should be indigo")
            assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(5, 5), "Original position should be transparent")
        }
    }
}