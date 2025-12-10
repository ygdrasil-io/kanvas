package com.kanvas.core

import core.GlyphRunList
import device.BitmapDevice
import device.GPUDevice

/**
 * Device interface, inspired by Skia's SkDevice
 * Represents a drawing surface that can render graphics primitives
 */
interface Device {
    /**
     * Get the width of the device in pixels
     */
    val width: Int
    
    /**
     * Get the height of the device in pixels
     */
    val height: Int
    
    /**
     * Get the color info of the device
     */
    val colorInfo: ColorInfo
    
    /**
     * Get the surface properties of the device
     */
    val surfaceProps: SurfaceProps
    
    /**
     * Access the underlying bitmap/pixels
     */
    val bitmap: Bitmap
    
    /**
     * Get ImageInfo for this device (compatibility with Skia API)
     */
    fun imageInfo(): ColorInfo {
        return colorInfo
    }
    
    /**
     * Check if this device is opaque
     */
    fun isOpaque(): Boolean {
        return colorInfo.isOpaque()
    }
    
    /**
     * Get device bounds as a rectangle
     */
    fun bounds(): Rect {
        return Rect(0f, 0f, width.toFloat(), height.toFloat())
    }
    
    /**
     * Draw a rectangle
     */
    fun drawRect(rect: Rect, paint: Paint)
    
    /**
     * Draw a path
     */
    fun drawPath(path: Path, paint: Paint)
    
    /**
     * Set the current shader for filling operations
     */
    fun setShader(shader: Shader?)
    
    /**
     * Get the current shader
     */
    fun getShader(): Shader?
    
    /**
     * Draw text
     */
    fun drawText(text: String, x: Float, y: Float, paint: Paint)

    /**
     * Draw a list of glyph runs on this device.
     * This is the core text rendering method that handles multiple glyph runs efficiently.
     * Inspired by Skia's onDrawGlyphRunList method.
     */
    fun onDrawGlyphRunList(glyphRunList: GlyphRunList, paint: Paint)
    
    /**
     * Draw an image
     */
    fun drawImage(image: Bitmap, src: Rect, dst: Rect, paint: Paint)
    fun drawImage(image: Bitmap, src: Rect, dst: Rect, paint: Paint, sampling: SamplingOptions)
    
    /**
     * Clear the device with a color
     */
    fun clear(color: Color)
    
    /**
     * Flush any pending operations
     */
    fun flush()
    
    /**
     * Get the current transform matrix (3x3 version for compatibility)
     */
    fun getTotalMatrix(): Matrix
    
    /**
     * Get the current transform matrix (4x4 version for advanced transformations)
     */
    fun localToDevice(): Matrix4x4 {
        // Default implementation: convert 3x3 matrix to 4x4
        return Matrix4x4.fromMatrix3x3(getTotalMatrix())
    }
    
    /**
     * Get device-to-global transform (identity for simple devices)
     */
    fun deviceToGlobal(): Matrix4x4 {
        return Matrix4x4.identity()
    }
    
    /**
     * Get global-to-device transform (identity for simple devices)
     */
    fun globalToDevice(): Matrix4x4 {
        return Matrix4x4.identity()
    }
    
    /**
     * Get the current clip bounds
     */
    fun getClipBounds(): Rect
    
    /**
     * Get device clip bounds in device coordinates
     */
    fun devClipBounds(): Rect {
        return getClipBounds()
    }
    
    /**
     * Check if clip is empty
     */
    fun isClipEmpty(): Boolean {
        return getClipBounds().isEmpty
    }
    
    /**
     * Check if clip is a simple rectangle
     */
    fun isClipRect(): Boolean {
        return true // Simplified - assume rectangular clips for now
    }
    
    /**
     * Check if clip is wide open (covers entire device)
     */
    fun isClipWideOpen(): Boolean {
        val clipBounds = getClipBounds()
        return clipBounds.left <= 0 && clipBounds.top <= 0 &&
               clipBounds.right >= width && clipBounds.bottom >= height
    }

    /**
     * Save the current clip state onto a stack
     * Returns the depth of the clip stack after the save
     */
    fun saveClipStack(): Int

    /**
     * Restore the most recently saved clip state from the stack
     * Returns the depth of the clip stack after the restore
     */
    fun restoreClipStack(): Int

    /**
     * Get the current depth of the clip stack
     */
    fun getClipStackDepth(): Int
    
    /**
     * Get the surface associated with this device
     * This is a convenience method to access the surface that this device is rendering to
     */
    fun getSurface(): Surface {
        // Create a surface that wraps this device
        return Surface(width, height, colorInfo, surfaceProps)
    }

    /**
     * Clip the current clip region with a rectangle
     */
    fun clipRect(rect: Rect, clipOp: ClipOp = ClipOp.INTERSECT, doAntiAlias: Boolean = false)

    /**
     * Clip the current clip region with a path
     */
    fun clipPath(path: Path, clipOp: ClipOp = ClipOp.INTERSECT, doAntiAlias: Boolean = false)

    /**
     * Clip operation types (matching Skia's SkClipOp)
     */
    enum class ClipOp {
        DIFFERENCE,  // Replace clip with the set difference of the current clip and the parameter
        INTERSECT    // Replace clip with the intersection of the current clip and the parameter
    }

    /**
     * Write pixels from a source bitmap to this device at the specified position
     * Returns true if the operation was successful
     */
    fun writePixels(src: Bitmap, x: Int, y: Int): Boolean

    /**
     * Read pixels from this device into a destination bitmap at the specified position
     * Returns true if the operation was successful
     */
    fun readPixels(dst: Bitmap, x: Int, y: Int): Boolean

    /**
     * Get direct access to the pixel data of this device
     * Returns the underlying bitmap that contains the pixel data
     */
    fun accessPixels(): Bitmap

    /**
     * Get read-only access to the pixel data of this device
     * Returns the underlying bitmap for read-only access
     */
    fun peekPixels(): Bitmap

    /**
     * Replace the current clip with a new rectangular clip
     */
    fun replaceClip(rect: Rect)

    /**
     * Draw an oval within the specified rectangle
     */
    fun drawOval(oval: Rect, paint: Paint)

    /**
     * Draw an arc (portion of an oval)
     */
    fun drawArc(arc: Arc, paint: Paint)

    /**
     * Draw a rounded rectangle
     */
    fun drawRRect(rrect: RRect, paint: Paint)

    /**
     * Draw a paint (fill the entire clip region)
     */
    fun drawPaint(paint: Paint)
}

/**
 * Surface class inspired by Skia's SkSurface
 * Represents a drawing surface that can be rendered to
 */
class Surface(
    val width: Int,
    val height: Int,
    val colorInfo: ColorInfo,
    val surfaceProps: SurfaceProps
) {
    private val device: Device
    
    init {
        // Create a device for this surface
        device = BitmapDevice(width, height, colorInfo, surfaceProps)
    }
    
    /**
     * Get the device associated with this surface
     */
    fun getDevice(): Device {
        return device
    }
    
    /**
     * Get a canvas for drawing on this surface
     */
    fun getCanvas(): Canvas {
        // Create a canvas that draws to this surface's device
        return Canvas(device)
    }
    
    /**
     * Flush any pending operations
     */
    fun flush() {
        device.flush()
    }
    
    /**
     * Get the image info for this surface
     */
    fun imageInfo(): ColorInfo {
        return colorInfo
    }
    

    
    /**
     * Write pixels to this surface
     */
    fun writePixels(src: Bitmap, x: Int, y: Int): Boolean {
        return device.writePixels(src, x, y)
    }
    
    /**
     * Read pixels from this surface
     */
    fun readPixels(dst: Bitmap, x: Int, y: Int): Boolean {
        return device.readPixels(dst, x, y)
    }
    
    /**
     * Get the bitmap associated with this surface
     */
    fun getBitmap(): Bitmap {
        return device.bitmap
    }
}

/**
 * Device factory methods, similar to Skia's device creation
 */
object Devices {
    
    /**
     * Create a raster device for CPU rendering
     */
    fun makeRaster(width: Int, height: Int, colorInfo: ColorInfo = ColorInfo(
        ColorType.RGBA_8888,
        AlphaType.PREMUL,
        ColorSpace.SRGB
    ), surfaceProps: SurfaceProps = SurfaceProps.default()): Device {
        return BitmapDevice(width, height, colorInfo, surfaceProps)
    }
    
    /**
     * Create a device from an existing bitmap
     */
    fun makeFromBitmap(bitmap: Bitmap, surfaceProps: SurfaceProps = SurfaceProps.default()): Device {
        return BitmapDevice(bitmap.getWidth(), bitmap.getHeight(), bitmap.colorInfo, surfaceProps).apply {
            // Copy existing bitmap content
            for (y in 0 until height) {
                for (x in 0 until width) {
                    this.bitmap.setPixel(x, y, bitmap.getPixel(x, y))
                }
            }
        }
    }
    
    /**
     * Create a GPU device (currently a no-op implementation)
     * This is a placeholder for future GPU rendering implementation
     */
    fun makeGPU(width: Int, height: Int, colorInfo: ColorInfo = ColorInfo(
        ColorType.RGBA_8888,
        AlphaType.PREMUL,
        ColorSpace.SRGB
    ), surfaceProps: SurfaceProps = SurfaceProps.default()): Device {
        return GPUDevice(width, height, colorInfo, surfaceProps)
    }
}

/**
 * Surface factory methods, similar to Skia's surface creation
 */
object Surfaces {
    
    /**
     * Create a raster surface for CPU rendering
     */
    fun makeRaster(width: Int, height: Int, colorInfo: ColorInfo = ColorInfo(
        ColorType.RGBA_8888,
        AlphaType.PREMUL,
        ColorSpace.SRGB
    ), surfaceProps: SurfaceProps = SurfaceProps.default()): Surface {
        return Surface(width, height, colorInfo, surfaceProps)
    }
    
    /**
     * Create a surface from an existing bitmap
     */
    fun makeFromBitmap(bitmap: Bitmap, surfaceProps: SurfaceProps = SurfaceProps.default()): Surface {
        return Surface(bitmap.getWidth(), bitmap.getHeight(), bitmap.colorInfo, surfaceProps).apply {
            // Copy existing bitmap content to the surface's device
            for (y in 0 until height) {
                for (x in 0 until width) {
                    getDevice().bitmap.setPixel(x, y, bitmap.getPixel(x, y))
                }
            }
        }
    }
    
    /**
     * Create a GPU surface (currently falls back to CPU rendering)
     * This is a placeholder for future GPU rendering implementation
     */
    fun makeGPU(width: Int, height: Int, colorInfo: ColorInfo = ColorInfo(
        ColorType.RGBA_8888,
        AlphaType.PREMUL,
        ColorSpace.SRGB
    ), surfaceProps: SurfaceProps = SurfaceProps.default()): Surface {
        // For now, fall back to CPU rendering
        // TODO: Implement actual GPU surface once GPU rendering is available
        return Surface(width, height, colorInfo, surfaceProps)
    }
}

