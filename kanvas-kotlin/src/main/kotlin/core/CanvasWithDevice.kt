package com.kanvas.core

/**
 * Enhanced Canvas implementation that uses a Device for rendering
 * This provides better separation of concerns and aligns with Skia's architecture
 */
class CanvasWithDevice(private val device: Device) : CanvasInterface {
    
    // Current transformation matrix stack
    private val matrixStack: MutableList<Matrix> = mutableListOf(Matrix.identity())
    
    // Current clip region stack
    private val clipStack: MutableList<Rect> = mutableListOf(device.getClipBounds())
    
    // Current paint properties
    private var currentPaint: Paint = Paint()
    
    /**
     * Get the width of the canvas
     */
    override val width: Int get() = device.width
    
    /**
     * Get the height of the canvas
     */
    override val height: Int get() = device.height
    
    /**
     * Get the underlying device
     */
    val getDevice: Device get() = device
    
    /**
     * Get the current bitmap
     */
    override val bitmap: Bitmap get() = device.bitmap

    /**
     * Get a copy of the current bitmap (compatibility method)
     */
    fun getBitmapCopy(): Bitmap = bitmap.copy()
    
    // ===== State Management =====
    
    /**
     * Saves the current canvas state (matrix and clip) onto a stack
     */
    override fun save() {
        matrixStack.add(matrixStack.last().copy())
        clipStack.add(clipStack.last().copy())
    }
    
    /**
     * Restores the most recently saved canvas state
     */
    override fun restore() {
        if (matrixStack.size > 1) {
            matrixStack.removeAt(matrixStack.size - 1)
            updateDeviceState()
        }
        if (clipStack.size > 1) {
            clipStack.removeAt(clipStack.size - 1)
            updateDeviceState()
        }
    }
    
    /**
     * Saves the current state and applies a new clip
     */
    override fun saveLayer(clippingRect: Rect, paint: Paint?): Int {
        save()
        clipRect(clippingRect, SkClipOp.INTERSECT, false)
        return matrixStack.size - 1 // Return save count
    }
    
    /**
     * Restores to the specified save count
     */
    override fun restoreToCount(saveCount: Int) {
        while (matrixStack.size > saveCount + 1) {
            restore()
        }
    }
    
    // ===== Transformation Methods =====
    
    /**
     * Translates the canvas by (dx, dy)
     */
    override fun translate(dx: Float, dy: Float) {
        val currentMatrix = matrixStack.last()
        matrixStack[matrixStack.size - 1] = currentMatrix.translate(dx, dy)
        updateDeviceState()
    }
    
    /**
     * Scales the canvas by (sx, sy)
     */
    override fun scale(sx: Float, sy: Float) {
        val currentMatrix = matrixStack.last()
        matrixStack[matrixStack.size - 1] = currentMatrix.scale(sx, sy)
        updateDeviceState()
    }
    
    /**
     * Rotates the canvas by degrees around (x, y)
     */
    override fun rotate(degrees: Float, x: Float, y: Float) {
        val currentMatrix = matrixStack.last()
        matrixStack[matrixStack.size - 1] = currentMatrix.rotate(degrees, x, y)
        updateDeviceState()
    }
    
    /**
     * Applies a transformation matrix
     */
    override fun concat(matrix: Matrix) {
        val currentMatrix = matrixStack.last()
        matrixStack[matrixStack.size - 1] = currentMatrix.concat(matrix)
        updateDeviceState()
    }
    
    /**
     * Sets the transformation matrix
     */
    override fun setMatrix(matrix: Matrix) {
        matrixStack[matrixStack.size - 1] = matrix.copy()
        updateDeviceState()
    }
    
    /**
     * Gets the current transformation matrix
     */
    override fun getTotalMatrix(): Matrix = matrixStack.last().copy()
    
    // ===== Clip Methods =====
    
    /**
     * Clips the canvas to the specified rectangle
     */
    override fun clipRect(rect: Rect, op: SkClipOp, doAntiAlias: Boolean) {
        val currentClip = clipStack.last()
        val newClip = when (op) {
            SkClipOp.INTERSECT -> currentClip.intersect(rect)
            SkClipOp.DIFFERENCE -> {
                // Difference is more complex - for now just use the rect
                rect
            }
        }
        clipStack[clipStack.size - 1] = newClip
        updateDeviceState()
    }

    /**
     * Compatibility method for old Canvas interface - clip with INTERSECT operation
     */
    override fun clipRect(rect: Rect, doAntiAlias: Boolean) {
        clipRect(rect, SkClipOp.INTERSECT, doAntiAlias)
    }
    
    /**
     * Gets the current clip bounds
     */
    override fun getClipBounds(): Rect = clipStack.last().copy()
    
    // ===== Drawing Methods =====
    
    /**
     * Draws a rectangle
     */
    override fun drawRect(rect: Rect, paint: Paint) {
        device.drawRect(rect, paint)
    }
    
    /**
     * Draws a rounded rectangle
     */
    override fun drawRoundRect(rect: Rect, rx: Float, ry: Float, paint: Paint) {
        // For now, approximate with a regular rect
        // TODO: Implement proper rounded rect rendering
        drawRect(rect, paint)
    }
    
    /**
     * Draws a circle
     */
    override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        val rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius)
        // For now, draw as a square - TODO: implement proper circle rendering
        drawRect(rect, paint)
    }
    
    /**
     * Draws an oval
     */
    override fun drawOval(oval: Rect, paint: Paint) {
        // For now, draw as the bounding rect - TODO: implement proper oval rendering
        drawRect(oval, paint)
    }
    
    /**
     * Draws a path
     */
    override fun drawPath(path: Path, paint: Paint) {
        device.drawPath(path, paint)
    }
    
    /**
     * Draws text
     */
    override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        device.drawText(text, x, y, paint)
    }
    
    /**
     * Draws an image
     */
    override fun drawImage(image: Bitmap, src: Rect, dst: Rect, paint: Paint) {
        device.drawImage(image, src, dst, paint)
    }
    
    /**
     * Clears the canvas with a color
     */
    override fun clear(color: Color) {
        device.clear(color)
    }
    
    /**
     * Flushes any pending drawing operations
     */
    override fun flush() {
        device.flush()
    }
    
    // ===== Paint Management =====
    
    /**
     * Sets the current paint
     */
    fun setPaint(paint: Paint) {
        currentPaint = paint.copy()
    }
    
    /**
     * Gets the current paint
     */
    fun getPaint(): Paint = currentPaint.copy()
    
    // ===== Private Helper Methods =====
    
    private fun updateDeviceState() {
        val currentMatrix = matrixStack.last()
        val currentClip = clipStack.last()
        
        // Update device state
        if (device is RasterDevice) {
            device.setMatrix(currentMatrix)
            device.setClipBounds(currentClip)
        }
    }
}

/**
 * Canvas interface that defines the drawing API
 */
interface CanvasInterface {
    val width: Int
    val height: Int
    val bitmap: Bitmap
    
    // State management
    fun save()
    fun restore()
    fun saveLayer(clippingRect: Rect, paint: Paint? = null): Int
    fun restoreToCount(saveCount: Int)
    
    // Transformations
    fun translate(dx: Float, dy: Float)
    fun scale(sx: Float, sy: Float)
    fun rotate(degrees: Float, x: Float, y: Float)
    fun concat(matrix: Matrix)
    fun setMatrix(matrix: Matrix)
    fun getTotalMatrix(): Matrix
    
    // Clipping
    fun clipRect(rect: Rect, op: SkClipOp, doAntiAlias: Boolean = false)
    fun clipRect(rect: Rect, doAntiAlias: Boolean = false) // Compatibility method
    fun getClipBounds(): Rect
    
    // Drawing
    fun drawRect(rect: Rect, paint: Paint)
    fun drawRoundRect(rect: Rect, rx: Float, ry: Float, paint: Paint)
    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint)
    fun drawOval(oval: Rect, paint: Paint)
    fun drawPath(path: Path, paint: Paint)
    fun drawText(text: String, x: Float, y: Float, paint: Paint)
    fun drawImage(image: Bitmap, src: Rect, dst: Rect, paint: Paint)
    fun clear(color: Color)
    fun flush()
}

/**
 * Canvas factory methods
 */
object CanvasFactory {
    
    /**
     * Create a canvas with a raster device (CPU rendering)
     */
    fun createRaster(width: Int, height: Int): CanvasWithDevice {
        val device = Devices.makeRaster(width, height)
        return CanvasWithDevice(device)
    }
    
    /**
     * Create a canvas from an existing bitmap
     */
    fun createFromBitmap(bitmap: Bitmap): CanvasWithDevice {
        val device = Devices.makeFromBitmap(bitmap)
        return CanvasWithDevice(device)
    }
    
    /**
     * Create a canvas with a specific device
     */
    fun createWithDevice(device: Device): CanvasWithDevice {
        return CanvasWithDevice(device)
    }

    /**
     * Create a canvas from width and height (compatibility constructor)
     * This provides the same interface as the old Canvas(width, height)
     */
    fun create(width: Int, height: Int): CanvasWithDevice {
        return CanvasFactory.createRaster(width, height)
    }
}