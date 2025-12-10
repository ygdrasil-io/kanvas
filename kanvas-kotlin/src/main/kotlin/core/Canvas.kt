package com.kanvas.core

import device.BitmapDevice

/**
 * Enhanced Canvas implementation that uses a Device for rendering
 * This provides better separation of concerns and aligns with Skia's architecture
 */
class Canvas(private val device: Device) {
    
    // Current transformation matrix stack
    private val matrixStack: MutableList<Matrix> = mutableListOf(Matrix.identity())
    
    // Current clip region stack
    private val clipStack: MutableList<Rect> = mutableListOf(device.getClipBounds())
    
    // Current paint properties
    private var currentPaint: Paint = Paint()
    
    /**
     * Get the width of the canvas
     */
    val width: Int get() = device.width
    
    /**
     * Get the height of the canvas
     */
    val height: Int get() = device.height
    
    /**
     * Get the underlying device
     */
    val getDevice: Device get() = device
    
    /**
     * Get the current bitmap
     */
    val bitmap: Bitmap get() = device.bitmap

    /**
     * Get a copy of the current bitmap (compatibility method)
     */
    fun getBitmapCopy(): Bitmap = bitmap.copy()
    
    // ===== State Management =====
    
    /**
     * Saves the current canvas state (matrix and clip) onto a stack
     */
    fun save() {
        matrixStack.add(matrixStack.last().copy())
        clipStack.add(clipStack.last().copy())
    }
    
    /**
     * Restores the most recently saved canvas state
     */
    fun restore() {
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
    fun saveLayer(clippingRect: Rect, paint: Paint?): Int {
        save()
        clipRect(clippingRect, SkClipOp.INTERSECT, false)
        return matrixStack.size - 1 // Return save count
    }
    
    /**
     * Restores to the specified save count
     */
    fun restoreToCount(saveCount: Int) {
        while (matrixStack.size > saveCount + 1) {
            restore()
        }
    }
    
    // ===== Transformation Methods =====
    
    /**
     * Translates the canvas by (dx, dy)
     */
    fun translate(dx: Float, dy: Float) {
        val currentMatrix = matrixStack.last()
        matrixStack[matrixStack.size - 1] = currentMatrix.translate(dx, dy)
        updateDeviceState()
    }
    
    /**
     * Scales the canvas by (sx, sy)
     */
    fun scale(sx: Float, sy: Float) {
        val currentMatrix = matrixStack.last()
        matrixStack[matrixStack.size - 1] = currentMatrix.scale(sx, sy)
        updateDeviceState()
    }
    
    /**
     * Rotates the canvas by degrees around (x, y)
     */
    fun rotate(degrees: Float, x: Float, y: Float) {
        val currentMatrix = matrixStack.last()
        matrixStack[matrixStack.size - 1] = currentMatrix.rotate(degrees, x, y)
        updateDeviceState()
    }
    
    /**
     * Applies a transformation matrix
     */
    fun concat(matrix: Matrix) {
        val currentMatrix = matrixStack.last()
        matrixStack[matrixStack.size - 1] = currentMatrix.concat(matrix)
        updateDeviceState()
    }
    
    /**
     * Sets the transformation matrix
     */
    fun setMatrix(matrix: Matrix) {
        matrixStack[matrixStack.size - 1] = matrix.copy()
        updateDeviceState()
    }
    
    /**
     * Gets the current transformation matrix
     */
    fun getTotalMatrix(): Matrix = matrixStack.last().copy()
    
    // ===== Clip Methods =====
    
    /**
     * Clips the canvas to the specified rectangle
     */
    fun clipRect(rect: Rect, op: SkClipOp, doAntiAlias: Boolean = false) {
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
    fun clipRect(rect: Rect, doAntiAlias: Boolean = false) {
        clipRect(rect, SkClipOp.INTERSECT, doAntiAlias)
    }
    
    /**
     * Gets the current clip bounds
     */
    fun getClipBounds(): Rect = clipStack.last().copy()
    
    // Shader support
    fun setShader(shader: Shader?) {
        device.setShader(shader)
    }
    
    fun getShader(): Shader? {
        return device.getShader()
    }
    
    // ===== Drawing Methods =====
    
    /**
     * Draws a rectangle
     */
    fun drawRect(rect: Rect, paint: Paint) {
        device.drawRect(rect, paint)
    }
    
    /**
     * Draws a rounded rectangle
     */
    fun drawRoundRect(rect: Rect, rx: Float, ry: Float, paint: Paint) {
        // For now, approximate with a regular rect
        // TODO: Implement proper rounded rect rendering
        drawRect(rect, paint)
    }
    
    /**
     * Draws a circle
     */
    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        val rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius)
        // For now, draw as a square - TODO: implement proper circle rendering
        drawRect(rect, paint)
    }
    
    /**
     * Draws an oval
     */
    fun drawOval(oval: Rect, paint: Paint) {
        // For now, draw as the bounding rect - TODO: implement proper oval rendering
        drawRect(oval, paint)
    }
    
    /**
     * Draws a path
     */
    fun drawPath(path: Path, paint: Paint) {
        device.drawPath(path, paint)
    }
    
    /**
     * Draws text
     */
    fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        device.drawText(text, x, y, paint)
    }
    
    /**
     * Draws an image
     */
    fun drawImage(image: Bitmap, src: Rect, dst: Rect, paint: Paint) {
        device.drawImage(image, src, dst, paint)
    }
    
    /**
     * Clears the canvas with a color
     */
    fun clear(color: Color) {
        device.clear(color)
    }
    
    /**
     * Flushes any pending drawing operations
     */
    fun flush() {
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
        if (device is BitmapDevice) {
            device.setMatrix(currentMatrix)
            device.setClipBounds(currentClip)
        }
    }
}

/**
 * Canvas factory methods
 */
object CanvasFactory {
    
    /**
     * Create a canvas with a raster device (CPU rendering)
     */
    fun createRaster(width: Int, height: Int): Canvas {
        val device = Devices.makeRaster(width, height)
        return Canvas(device)
    }
    
    /**
     * Create a canvas from an existing bitmap
     */
    fun createFromBitmap(bitmap: Bitmap): Canvas {
        val device = Devices.makeFromBitmap(bitmap)
        return Canvas(device)
    }
    
    /**
     * Create a canvas with a specific device
     */
    fun createWithDevice(device: Device): Canvas {
        return Canvas(device)
    }

    /**
     * Create a canvas from width and height (compatibility constructor)
     * This provides the same interface as the old Canvas(width, height)
     */
    fun create(width: Int, height: Int): Canvas {
        return CanvasFactory.createRaster(width, height)
    }
}