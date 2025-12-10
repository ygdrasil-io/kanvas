package com.kanvas.core

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
     * Access the underlying bitmap/pixels
     */
    val bitmap: Bitmap
    
    /**
     * Draw a rectangle
     */
    fun drawRect(rect: Rect, paint: Paint)
    
    /**
     * Draw a path
     */
    fun drawPath(path: Path, paint: Paint)
    
    /**
     * Draw text
     */
    fun drawText(text: String, x: Float, y: Float, paint: Paint)
    
    /**
     * Draw an image
     */
    fun drawImage(image: Bitmap, src: Rect, dst: Rect, paint: Paint)
    
    /**
     * Clear the device with a color
     */
    fun clear(color: Color)
    
    /**
     * Flush any pending operations
     */
    fun flush()
    
    /**
     * Get the current transform matrix
     */
    fun getTotalMatrix(): Matrix
    
    /**
     * Get the current clip bounds
     */
    fun getClipBounds(): Rect
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
    )): Device {
        return RasterDevice(width, height, colorInfo)
    }
    
    /**
     * Create a device from an existing bitmap
     */
    fun makeFromBitmap(bitmap: Bitmap): Device {
        return RasterDevice(bitmap.getWidth(), bitmap.getHeight(), bitmap.colorInfo).apply {
            // Copy existing bitmap content
            for (y in 0 until height) {
                for (x in 0 until width) {
                    this.bitmap.setPixel(x, y, bitmap.getPixel(x, y))
                }
            }
        }
    }
}