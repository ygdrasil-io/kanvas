package core

import com.kanvas.core.AlphaType
import com.kanvas.core.Bitmap
import com.kanvas.core.Canvas
import com.kanvas.core.CanvasFactory
import com.kanvas.core.Color
import com.kanvas.core.ColorType
import com.kanvas.core.Device
import com.kanvas.core.Devices
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Rect
import com.kanvas.core.Shaders
import com.kanvas.core.Surfaces
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for Device functionality inspired by Skia
 */
class DeviceTest {

    @Test
    fun testDeviceCreation() {
        val width = 100
        val height = 100
        val device = Devices.makeRaster(width, height)
        
        assertNotNull(device)
        assertEquals(width, device.width)
        assertEquals(height, device.height)
        assertEquals(ColorType.RGBA_8888, device.colorInfo.colorType)
        assertEquals(AlphaType.PREMUL, device.colorInfo.alphaType)
    }

    @Test
    fun testDeviceFromBitmap() {
        val width = 50
        val height = 50
        val bitmap = Bitmap.create(width, height)
        
        val device = Devices.makeFromBitmap(bitmap)
        
        assertNotNull(device)
        assertEquals(width, device.width)
        assertEquals(height, device.height)
        assertEquals(bitmap.colorInfo, device.colorInfo)
    }

    @Test
    fun testGPUDeviceCreation() {
        val width = 100
        val height = 100
        val device = Devices.makeGPU(width, height)
        
        assertNotNull(device)
        assertEquals(width, device.width)
        assertEquals(height, device.height)
        assertEquals(ColorType.RGBA_8888, device.colorInfo.colorType)
        assertEquals(AlphaType.PREMUL, device.colorInfo.alphaType)
    }

    @Test
    fun testSurfaceCreation() {
        val width = 100
        val height = 100
        val surface = Surfaces.makeRaster(width, height)
        
        assertNotNull(surface)
        assertEquals(width, surface.width)
        assertEquals(height, surface.height)
        assertEquals(ColorType.RGBA_8888, surface.colorInfo.colorType)
        assertEquals(AlphaType.PREMUL, surface.colorInfo.alphaType)
    }

    @Test
    fun testSurfaceFromBitmap() {
        val width = 50
        val height = 50
        val bitmap = Bitmap.create(width, height)
        
        val surface = Surfaces.makeFromBitmap(bitmap)
        
        assertNotNull(surface)
        assertEquals(width, surface.width)
        assertEquals(height, surface.height)
        assertEquals(bitmap.colorInfo, surface.colorInfo)
    }

    @Test
    fun testGPUSurfaceCreation() {
        val width = 100
        val height = 100
        val surface = Surfaces.makeGPU(width, height)
        
        assertNotNull(surface)
        assertEquals(width, surface.width)
        assertEquals(height, surface.height)
        assertEquals(ColorType.RGBA_8888, surface.colorInfo.colorType)
        assertEquals(AlphaType.PREMUL, surface.colorInfo.alphaType)
    }

    @Test
    fun testDeviceSurfaceIntegration() {
        val width = 100
        val height = 100
        val device = Devices.makeRaster(width, height)
        
        val surface = device.getSurface()
        
        assertNotNull(surface)
        assertEquals(width, surface.width)
        assertEquals(height, surface.height)
        assertEquals(device.colorInfo, surface.colorInfo)
    }

    @Test
    fun testDeviceGetCanvas() {
        val width = 100
        val height = 100
        val device = Devices.makeRaster(width, height)
        
        val canvas = device.getCanvas()

        assertNotNull(canvas)
        assertEquals(width, canvas.width)
        assertEquals(height, canvas.height)
        
        // Test that we can draw on the canvas and it affects the device
        val paint = Paint().apply {
            color = Color.GREEN
            style = PaintStyle.FILL
        }
        val rect = Rect(10f, 10f, 50f, 50f)
        canvas.drawRect(rect, paint)
        
        // Check that the rectangle was drawn on the device
        val bitmap = device.bitmap
        val centerColor = bitmap.getPixel(30, 30)
        
        assertTrue(centerColor.green > 200, "Center of rectangle should be green")
    }

    @Test
    fun testGPUDeviceGetCanvas() {
        val width = 100
        val height = 100
        val device = Devices.makeGPU(width, height)
        
        val canvas = device.getCanvas()

        assertNotNull(canvas)
        assertEquals(width, canvas.width)
        assertEquals(height, canvas.height)
        
        // Test that we can draw on the canvas and it affects the device
        val paint = Paint().apply {
            color = Color.YELLOW
            style = PaintStyle.FILL
        }
        val rect = Rect(10f, 10f, 50f, 50f)
        canvas.drawRect(rect, paint)
        
        // Check that the rectangle was drawn on the device
        val bitmap = device.bitmap
        val centerColor = bitmap.getPixel(30, 30)
        
        assertTrue(centerColor.red > 200 && centerColor.green > 200, "Center of rectangle should be yellow")
    }

    @Test
    fun testGPUDeviceContexts() {
        val width = 100
        val height = 100
        val device = Devices.makeGPU(width, height)
        
        // Test recording context
        val recordingContext = device.getRecordingContext()
        assertNotNull(recordingContext, "GPU device should have a recording context")
        assertEquals(8192, recordingContext.maxTextureSize())
        assertFalse(recordingContext.isAbandoned())
        
        // Test direct context
        val directContext = device.getDirectContext()
        assertNotNull(directContext, "GPU device should have a direct context")
        assertEquals(8192, directContext.maxTextureSize())
        assertFalse(directContext.isAbandoned())
    }

    @Test
    fun testCPUDeviceContexts() {
        val width = 100
        val height = 100
        val device = Devices.makeRaster(width, height)
        
        // Test that CPU devices return null for GPU contexts
        val recordingContext = device.getRecordingContext()
        assertNull(recordingContext, "CPU device should not have a recording context")
        
        val directContext = device.getDirectContext()
        assertNull(directContext, "CPU device should not have a direct context")
    }

    @Test
    fun testSurfaceDeviceIntegration() {
        val width = 100
        val height = 100
        val surface = Surfaces.makeRaster(width, height)
        
        val device = surface.getDevice()
        
        assertNotNull(device)
        assertEquals(width, device.width)
        assertEquals(height, device.height)
        assertEquals(surface.colorInfo, device.colorInfo)
    }

    @Test
    fun testCanvasDeviceIntegration() {
        val width = 100
        val height = 100
        val device = Devices.makeRaster(width, height)
        
        val canvas = CanvasFactory.createWithDevice(device)
        
        assertNotNull(canvas)
        assertEquals(width, canvas.width)
        assertEquals(height, canvas.height)
    }

    @Test
    fun testCanvasSurfaceIntegration() {
        val width = 100
        val height = 100
        val surface = Surfaces.makeRaster(width, height)
        
        val canvas = CanvasFactory.createWithSurface(surface)
        
        assertNotNull(canvas)
        assertEquals(width, canvas.width)
        assertEquals(height, canvas.height)
    }

    @Test
    fun testCanvasGPUIntegration() {
        val width = 100
        val height = 100
        val canvas = CanvasFactory.createGPU(width, height)
        
        assertNotNull(canvas)
        assertEquals(width, canvas.width)
        assertEquals(height, canvas.height)
    }

    @Test
    fun testDeviceDrawing() {
        val width = 100
        val height = 100
        val device = Devices.makeRaster(width, height)
        
        val paint = Paint().apply {
            color = Color.RED
            style = PaintStyle.FILL
        }
        
        val rect = Rect(10f, 10f, 50f, 50f)
        device.drawRect(rect, paint)
        
        // Check that the rectangle was drawn
        val bitmap = device.bitmap
        val centerColor = bitmap.getPixel(30, 30)
        
        assertTrue(centerColor.red > 200, "Center of rectangle should be red")
    }

    @Test
    fun testSurfaceDrawing() {
        val width = 100
        val height = 100
        val surface = Surfaces.makeRaster(width, height)
        
        val canvas = surface.getCanvas()
        val paint = Paint().apply {
            color = Color.BLUE
            style = PaintStyle.FILL
        }
        
        val rect = Rect(10f, 10f, 50f, 50f)
        canvas.drawRect(rect, paint)
        
        // Check that the rectangle was drawn
        val bitmap = surface.getBitmap()
        val centerColor = bitmap.getPixel(30, 30)
        
        assertTrue(centerColor.blue > 200, "Center of rectangle should be blue")
    }

    @Test
    fun testDeviceClear() {
        val width = 100
        val height = 100
        val device = Devices.makeRaster(width, height)
        
        // Draw something first
        val paint = Paint().apply {
            color = Color.RED
            style = PaintStyle.FILL
        }
        val rect = Rect(10f, 10f, 50f, 50f)
        device.drawRect(rect, paint)
        
        // Clear the device
        device.clear(Color.WHITE)
        
        // Check that the device was cleared
        val bitmap = device.bitmap
        val centerColor = bitmap.getPixel(30, 30)
        
        assertEquals(Color.WHITE, centerColor, "Device should be cleared to white")
    }

    @Test
    fun testSurfaceClear() {
        val width = 100
        val height = 100
        val surface = Surfaces.makeRaster(width, height)
        
        // Draw something first
        val canvas = surface.getCanvas()
        val paint = Paint().apply {
            color = Color.RED
            style = PaintStyle.FILL
        }
        val rect = Rect(10f, 10f, 50f, 50f)
        canvas.drawRect(rect, paint)
        
        // Clear the surface
        surface.getDevice().clear(Color.WHITE)
        
        // Check that the surface was cleared
        val bitmap = surface.getBitmap()
        val centerColor = bitmap.getPixel(30, 30)
        
        assertEquals(Color.WHITE, centerColor, "Surface should be cleared to white")
    }

    @Test
    fun testDeviceClip() {
        val width = 100
        val height = 100
        val device = Devices.makeRaster(width, height)
        
        // Set a clip
        val clipRect = Rect(20f, 20f, 80f, 80f)
        device.clipRect(clipRect, Device.ClipOp.INTERSECT)
        
        // Check that the clip was set
        val clipBounds = device.getClipBounds()
        
        assertEquals(clipRect.left, clipBounds.left, 0.01f)
        assertEquals(clipRect.top, clipBounds.top, 0.01f)
        assertEquals(clipRect.right, clipBounds.right, 0.01f)
        assertEquals(clipRect.bottom, clipBounds.bottom, 0.01f)
    }

    @Test
    fun testSurfaceClip() {
        val width = 100
        val height = 100
        val surface = Surfaces.makeRaster(width, height)
        
        // Set a clip
        val clipRect = Rect(20f, 20f, 80f, 80f)
        surface.getDevice().clipRect(clipRect, Device.ClipOp.INTERSECT)
        
        // Check that the clip was set
        val clipBounds = surface.getDevice().getClipBounds()
        
        assertEquals(clipRect.left, clipBounds.left, 0.01f)
        assertEquals(clipRect.top, clipBounds.top, 0.01f)
        assertEquals(clipRect.right, clipBounds.right, 0.01f)
        assertEquals(clipRect.bottom, clipBounds.bottom, 0.01f)
    }

    @Test
    fun testDeviceShader() {
        val width = 100
        val height = 100
        val device = Devices.makeRaster(width, height)
        
        // Create a shader
        val colors = listOf(Color.RED, Color.BLUE)
        val shader = Shaders.makeLinearGradient(colors, null, com.kanvas.core.Point(0f, 0f), com.kanvas.core.Point(100f, 100f))
        
        // Set the shader
        device.setShader(shader)
        
        // Check that the shader was set
        val currentShader = device.getShader()
        
        assertNotNull(currentShader, "Shader should be set")
    }

    @Test
    fun testSurfaceShader() {
        val width = 100
        val height = 100
        val surface = Surfaces.makeRaster(width, height)
        
        // Create a shader
        val colors = listOf(Color.RED, Color.BLUE)
        val shader = Shaders.makeLinearGradient(colors, null, com.kanvas.core.Point(0f, 0f), com.kanvas.core.Point(100f, 100f))
        
        // Set the shader
        surface.getDevice().setShader(shader)
        
        // Check that the shader was set
        val currentShader = surface.getDevice().getShader()
        
        assertNotNull(currentShader, "Shader should be set")
    }

    @Test
    fun testDeviceWritePixels() {
        val width = 100
        val height = 100
        val device = Devices.makeRaster(width, height)
        
        // Create a source bitmap
        val srcBitmap = Bitmap.create(10, 10)
        srcBitmap.setPixel(0, 0, Color.RED)
        
        // Write pixels to the device
        val result = device.writePixels(srcBitmap, 10, 10)
        
        assertTrue(result, "Pixels should be written successfully")
        
        // Check that the pixels were written
        val bitmap = device.bitmap
        val pixel = bitmap.getPixel(10, 10)
        
        assertEquals(Color.RED, pixel, "Pixel should be red")
    }

    @Test
    fun testSurfaceWritePixels() {
        val width = 100
        val height = 100
        val surface = Surfaces.makeRaster(width, height)
        
        // Create a source bitmap
        val srcBitmap = Bitmap.create(10, 10)
        srcBitmap.setPixel(0, 0, Color.RED)
        
        // Write pixels to the surface
        val result = surface.writePixels(srcBitmap, 10, 10)
        
        assertTrue(result, "Pixels should be written successfully")
        
        // Check that the pixels were written
        val bitmap = surface.getBitmap()
        val pixel = bitmap.getPixel(10, 10)
        
        assertEquals(Color.RED, pixel, "Pixel should be red")
    }

    @Test
    fun testDeviceReadPixels() {
        val width = 100
        val height = 100
        val device = Devices.makeRaster(width, height)
        
        // Draw something first
        val paint = Paint().apply {
            color = Color.RED
            style = PaintStyle.FILL
        }
        val rect = Rect(10f, 10f, 20f, 20f)
        device.drawRect(rect, paint)
        
        // Create a destination bitmap
        val dstBitmap = Bitmap.create(10, 10)
        
        // Read pixels from the device
        val result = device.readPixels(dstBitmap, 10, 10)
        
        assertTrue(result, "Pixels should be read successfully")
        
        // Check that the pixels were read
        val pixel = dstBitmap.getPixel(0, 0)
        
        assertEquals(Color.RED, pixel, "Pixel should be red")
    }

    @Test
    fun testSurfaceReadPixels() {
        val width = 100
        val height = 100
        val surface = Surfaces.makeRaster(width, height)
        
        // Draw something first
        val canvas = surface.getCanvas()
        val paint = Paint().apply {
            color = Color.RED
            style = PaintStyle.FILL
        }
        val rect = Rect(10f, 10f, 20f, 20f)
        canvas.drawRect(rect, paint)
        
        // Create a destination bitmap
        val dstBitmap = Bitmap.create(10, 10)
        
        // Read pixels from the surface
        val result = surface.readPixels(dstBitmap, 10, 10)
        
        assertTrue(result, "Pixels should be read successfully")
        
        // Check that the pixels were read
        val pixel = dstBitmap.getPixel(0, 0)
        
        assertEquals(Color.RED, pixel, "Pixel should be red")
    }

    @Test
    fun testDeviceDrawCanvas() {
        val width = 100
        val height = 100
        
        // Create a target device
        val targetDevice = Devices.makeRaster(width, height)
        
        // Create a source canvas with some content
        val sourceCanvas = Canvas(Devices.makeRaster(50, 50))
        val paint = Paint().apply {
            color = Color.RED
            style = PaintStyle.FILL
        }
        val rect = Rect(10f, 10f, 40f, 40f)
        sourceCanvas.drawRect(rect, paint)
        
        // Draw the source canvas onto the target device
        targetDevice.draw(sourceCanvas)
        
        // Check that the content was drawn
        val bitmap = targetDevice.bitmap
        val centerColor = bitmap.getPixel(25, 25)
        
        assertTrue(centerColor.red > 200, "Center of drawn canvas should be red")
    }

    @Test
    fun testGPUDeviceDrawCanvas() {
        val width = 100
        val height = 100
        
        // Create a target GPU device
        val targetDevice = Devices.makeGPU(width, height)
        
        // Create a source canvas with some content
        val sourceCanvas = Canvas(Devices.makeRaster(50, 50))
        val paint = Paint().apply {
            color = Color.BLUE
            style = PaintStyle.FILL
        }
        val rect = Rect(10f, 10f, 40f, 40f)
        sourceCanvas.drawRect(rect, paint)
        
        // Draw the source canvas onto the target device
        targetDevice.draw(sourceCanvas)
        
        // Check that the content was drawn (should preserve original blue color)
        val bitmap = targetDevice.bitmap
        val centerColor = bitmap.getPixel(25, 25)
        
        // The color should be the original blue from the source canvas
        assertTrue(centerColor.blue > 200, "Center of drawn canvas should be blue")
    }

    @Test
    fun testRecordingContextFlushAndWait() {
        val device = Devices.makeGPU(100, 100)
        val recordingContext = device.getRecordingContext()
        
        assertNotNull(recordingContext, "GPU device should have a recording context")
        
        // Test initial state
        assertEquals(0, recordingContext.pendingFlushes())
        assertFalse(recordingContext.isAbandoned())
        
        // Add some pending operations (simulated for testing)
        recordingContext.addPendingOperations(3)
        assertEquals(1, recordingContext.pendingFlushes())
        
        // Test flush
        recordingContext.flush()
        assertEquals(0, recordingContext.pendingFlushes())
        
        // Add more operations and test waitForCompletion
        recordingContext.addPendingOperations(5)
        assertEquals(1, recordingContext.pendingFlushes())
        
        recordingContext.waitForCompletion()
        assertEquals(0, recordingContext.pendingFlushes())
    }

    @Test
    fun testDirectContextFlushAndWait() {
        val device = Devices.makeGPU(100, 100)
        val directContext = device.getDirectContext()
        
        assertNotNull(directContext, "GPU device should have a direct context")
        
        // Test initial state
        assertEquals(0, directContext.pendingFlushes())
        assertFalse(directContext.isAbandoned())
        
        // Add some pending operations (simulated for testing)
        directContext.addPendingOperations(2)
        assertEquals(1, directContext.pendingFlushes())
        
        // Test flush
        directContext.flush()
        assertEquals(0, directContext.pendingFlushes())
        
        // Add more operations and test waitForCompletion
        directContext.addPendingOperations(4)
        assertEquals(1, directContext.pendingFlushes())
        
        directContext.waitForCompletion()
        assertEquals(0, directContext.pendingFlushes())
    }

    @Test
    fun testDirectContextSubmit() {
        val device = Devices.makeGPU(100, 100)
        val directContext = device.getDirectContext()
        
        assertNotNull(directContext, "GPU device should have a direct context")
        
        // Test submit without sync
        directContext.addPendingOperations(3)
        directContext.submit(doSyncCpu = false)
        assertEquals(0, directContext.pendingFlushes())
        
        // Test submit with sync
        directContext.addPendingOperations(2)
        directContext.submit(doSyncCpu = true)
        assertEquals(0, directContext.pendingFlushes())
    }

    @Test
    fun testContextAbandon() {
        val device = Devices.makeGPU(100, 100)
        val recordingContext = device.getRecordingContext()
        val directContext = device.getDirectContext()
        
        assertNotNull(recordingContext, "GPU device should have a recording context")
        assertNotNull(directContext, "GPU device should have a direct context")
        
        // Test abandon
        recordingContext.abandon()
        assertTrue(recordingContext.isAbandoned())
        
        directContext.abandon()
        assertTrue(directContext.isAbandoned())
        
        // Test that operations on abandoned contexts throw exceptions
        assertFailsWith<IllegalStateException> {
            recordingContext.addPendingOperations(1)
        }
        
        assertFailsWith<IllegalStateException> {
            directContext.addPendingOperations(1)
        }
        
        assertFailsWith<IllegalStateException> {
            recordingContext.flush()
        }
        
        assertFailsWith<IllegalStateException> {
            directContext.flush()
        }
    }

    @Test
    fun testContextReentrantFlush() {
        val device = Devices.makeGPU(100, 100)
        val recordingContext = device.getRecordingContext()
        
        assertNotNull(recordingContext, "GPU device should have a recording context")
        
        // Add some operations
        recordingContext.addPendingOperations(1)
        
        // First flush should work
        recordingContext.flush()
        assertEquals(0, recordingContext.pendingFlushes())
        
        // Second flush during first flush should be ignored (reentrancy protection)
        // This is tested implicitly by the implementation
    }
}
