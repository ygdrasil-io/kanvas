package device

import com.kanvas.core.AlphaType
import com.kanvas.core.Bitmap
import com.kanvas.core.BitmapConfig
import com.kanvas.core.Color
import com.kanvas.core.ColorInfo
import com.kanvas.core.ColorSpace
import com.kanvas.core.ColorType
import com.kanvas.core.Device
import com.kanvas.core.Matrix
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Path
import com.kanvas.core.Rect
import com.kanvas.core.Shader
import com.kanvas.core.SurfaceProps

/**
 * Base device implementation for CPU rasterization
 * Inspired by Skia's SkBaseDevice and SkRasterDevice
 */
class BitmapDevice(
    override val width: Int,
    override val height: Int,
    override val colorInfo: ColorInfo = ColorInfo(
        ColorType.RGBA_8888,
        AlphaType.PREMUL,
        ColorSpace.Companion.SRGB
    ),
    override val surfaceProps: SurfaceProps = SurfaceProps.default()
) : Device {

    // Internal bitmap that holds the pixel data
    override val bitmap: Bitmap = Bitmap(width, height, BitmapConfig.ARGB_8888)

    // Current transform matrix
    private var currentMatrix: Matrix = Matrix.Companion.identity()

    // Current clip bounds
    private var clipBounds: Rect = Rect(0f, 0f, width.toFloat(), height.toFloat())

    // Clip stack for save/restore operations
    private val clipStack: MutableList<Rect> = mutableListOf()

    // Current paint properties
    private var currentPaint: Paint? = null
    
    // Current shader for fill operations
    private var currentShader: Shader? = null

    init {
        // Initialize with transparent background
        clear(Color.Companion.TRANSPARENT)
    }

    override fun drawRect(rect: Rect, paint: Paint) {
        // Apply clip
        val clippedRect = rect.intersect(clipBounds)
        if (clippedRect.isEmpty) return

        // Apply transform
        val transformedRect = currentMatrix.mapRect(clippedRect)

        // Rasterize the rectangle
        rasterizeRect(transformedRect, paint)
    }

    override fun drawPath(path: Path, paint: Paint) {
        // Apply clip and transform
        val clippedPath = path.copy()
        // TODO: Implement path clipping

        // Transform the path
        clippedPath.transform(currentMatrix)

        // Rasterize the path
        rasterizePath(clippedPath, paint)
    }

    override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        // TODO: Implement text rendering
        // For now, we'll just draw a placeholder rectangle
        val textWidth = text.length * 10f // Approximate
        val textHeight = 20f // Approximate
        val textRect = Rect(x, y - textHeight, x + textWidth, y)
        drawRect(textRect, paint)
    }

    override fun drawImage(image: Bitmap, src: Rect, dst: Rect, paint: Paint) {
        // Apply clip to destination
        val clippedDst = dst.intersect(clipBounds)
        if (clippedDst.isEmpty) return

        // Apply transform to destination
        val transformedDst = currentMatrix.mapRect(clippedDst)

        // Calculate source rectangle proportionally
        val srcWidth = src.width
        val srcHeight = src.height
        val dstWidth = transformedDst.width
        val dstHeight = transformedDst.height

        val proportionalSrc = Rect(
            src.left,
            src.top,
            src.left + (dstWidth / transformedDst.width) * srcWidth,
            src.top + (dstHeight / transformedDst.height) * srcHeight
        )

        // Rasterize the image
        rasterizeImage(image, proportionalSrc, transformedDst, paint)
    }

    override fun clear(color: Color) {
        // Fill the entire bitmap with the clear color
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap.setPixel(x, y, color)
            }
        }
    }

    override fun flush() {
        // For CPU raster device, flushing is immediate
        // In GPU devices, this would send commands to the GPU
    }

    override fun getTotalMatrix(): Matrix = currentMatrix.copy()

    override fun getClipBounds(): Rect = clipBounds.copy()

    /**
     * Set the transform matrix
     */
    fun setMatrix(matrix: Matrix) {
        currentMatrix = matrix.copy()
    }

    /**
     * Set the clip bounds
     */
    fun setClipBounds(bounds: Rect) {
        clipBounds = bounds.intersect(Rect(0f, 0f, width.toFloat(), height.toFloat()))
    }

    // ===== Clip Stack Methods =====

    override fun saveClipStack(): Int {
        // Save current clip bounds to stack
        clipStack.add(clipBounds.copy())
        return clipStack.size
    }

    override fun restoreClipStack(): Int {
        if (clipStack.isEmpty()) {
            throw IllegalStateException("Clip stack is empty, cannot restore")
        }
        // Restore the most recent clip bounds
        clipBounds = clipStack.removeAt(clipStack.size - 1)
        return clipStack.size
    }

    override fun getClipStackDepth(): Int {
        return clipStack.size
    }

    override fun clipRect(rect: Rect, clipOp: Device.ClipOp, doAntiAlias: Boolean) {
        val deviceBounds = Rect(0f, 0f, width.toFloat(), height.toFloat())
        val clippedRect = rect.intersect(deviceBounds)
        
        when (clipOp) {
            Device.ClipOp.INTERSECT -> {
                // Intersect with current clip
                clipBounds = clipBounds.intersect(clippedRect)
            }
            Device.ClipOp.DIFFERENCE -> {
                // Set difference: current clip minus the parameter
                // This is a simplified implementation
                val difference = mutableListOf<Rect>()
                
                // Check if there's any area left after removing the clippedRect
                if (!clipBounds.intersects(clippedRect)) {
                    // No intersection, keep current clip
                    return
                }
                
                // For simplicity, we'll handle simple cases
                // In a full implementation, this would be more complex
                if (clippedRect.contains(clipBounds)) {
                    // clippedRect completely contains current clip, result is empty
                    clipBounds = Rect(0f, 0f, 0f, 0f)
                } else {
                    // Partial overlap - keep the non-overlapping parts
                    // This is simplified - real implementation would handle multiple regions
                    val newClip = clipBounds.copy()
                    
                    // Remove left part if clippedRect starts inside
                    if (clippedRect.left > clipBounds.left) {
                        newClip.right = clippedRect.left
                    }
                    
                    // Remove right part if clippedRect ends inside  
                    if (clippedRect.right < clipBounds.right) {
                        newClip.left = clippedRect.right
                        newClip.right = clipBounds.right
                    }
                    
                    // Remove top part if clippedRect starts below
                    if (clippedRect.top > clipBounds.top) {
                        newClip.bottom = clippedRect.top
                    }
                    
                    // Remove bottom part if clippedRect ends above
                    if (clippedRect.bottom < clipBounds.bottom) {
                        newClip.top = clippedRect.bottom
                        newClip.bottom = clipBounds.bottom
                    }
                    
                    clipBounds = newClip
                }
            }
        }
    }

    override fun clipPath(path: Path, clipOp: Device.ClipOp, doAntiAlias: Boolean) {
        // Simplified path clipping - use path bounds
        val pathBounds = path.getBounds()
        val deviceBounds = Rect(0f, 0f, width.toFloat(), height.toFloat())
        val clippedBounds = pathBounds.intersect(deviceBounds)
        
        when (clipOp) {
            Device.ClipOp.INTERSECT -> {
                // Intersect with current clip using path bounds
                clipBounds = clipBounds.intersect(clippedBounds)
            }
            Device.ClipOp.DIFFERENCE -> {
                // Set difference using path bounds (simplified)
                if (clippedBounds.contains(clipBounds)) {
                    clipBounds = Rect(0f, 0f, 0f, 0f)
                } else if (clipBounds.intersects(clippedBounds)) {
                    // Simplified: keep parts of current clip not overlapped by path
                    val newClip = clipBounds.copy()
                    
                    if (clippedBounds.left > clipBounds.left) {
                        newClip.right = clippedBounds.left
                    }
                    if (clippedBounds.right < clipBounds.right) {
                        newClip.left = clippedBounds.right
                    }
                    if (clippedBounds.top > clipBounds.top) {
                        newClip.bottom = clippedBounds.top
                    }
                    if (clippedBounds.bottom < clipBounds.bottom) {
                        newClip.top = clippedBounds.bottom
                    }
                    
                    clipBounds = newClip
                }
            }
        }
    }

    /**
     * Set the current paint
     */
    fun setPaint(paint: Paint) {
        currentPaint = paint.copy()
    }

    // ===== CPU Rasterization Methods =====

    private fun rasterizeRect(rect: Rect, paint: Paint) {
        // Convert rect to integer coordinates
        val left = rect.left.toInt().coerceAtLeast(0)
        val top = rect.top.toInt().coerceAtLeast(0)
        val right = rect.right.toInt().coerceAtMost(width)
        val bottom = rect.bottom.toInt().coerceAtMost(height)

        when (paint.style) {
            PaintStyle.FILL -> {
                for (y in top until bottom) {
                    for (x in left until right) {
                        // Apply shader if available, otherwise use paint color
                        val color = if (currentShader != null) {
                            applyShader(paint.color, x.toFloat(), y.toFloat())
                        } else {
                            paint.color
                        }
                        bitmap.setPixel(x, y, color)
                    }
                }
            }
            PaintStyle.STROKE -> {
                // Simple stroke: draw border pixels
                // Top border
                for (x in left until right) {
                    if (top >= 0 && top < bitmap.getHeight()) {
                        val color = if (currentShader != null) {
                            applyShader(paint.color, x.toFloat(), top.toFloat())
                        } else {
                            paint.color
                        }
                        bitmap.setPixel(x, top, color)
                    }
                }
                // Bottom border
                for (x in left until right) {
                    if (bottom - 1 >= 0 && bottom - 1 < bitmap.getHeight()) {
                        val color = if (currentShader != null) {
                            applyShader(paint.color, x.toFloat(), (bottom - 1).toFloat())
                        } else {
                            paint.color
                        }
                        bitmap.setPixel(x, bottom - 1, color)
                    }
                }
                // Left border
                for (y in top until bottom) {
                    if (left >= 0 && left < bitmap.getWidth()) {
                        val color = if (currentShader != null) {
                            applyShader(paint.color, left.toFloat(), y.toFloat())
                        } else {
                            paint.color
                        }
                        bitmap.setPixel(left, y, color)
                    }
                }
                // Right border
                for (y in top until bottom) {
                    if (right - 1 >= 0 && right - 1 < bitmap.getWidth()) {
                        val color = if (currentShader != null) {
                            applyShader(paint.color, (right - 1).toFloat(), y.toFloat())
                        } else {
                            paint.color
                        }
                        bitmap.setPixel(right - 1, y, color)
                    }
                }
            }
            PaintStyle.FILL_AND_STROKE -> {
                rasterizeRect(rect, paint.copy().apply { style = PaintStyle.FILL })
                rasterizeRect(rect, paint.copy().apply { style = PaintStyle.STROKE })
            }
        }
    }

    private fun rasterizePath(path: Path, paint: Paint) {
        // Simple path rasterization using scanline algorithm
        // This is a simplified version - real implementations use more sophisticated algorithms

        when (paint.style) {
            PaintStyle.FILL -> {
                // Use path bounds for filling (simplified approach)
                val bounds = path.getBounds()
                val left = bounds.left.toInt().coerceAtLeast(0)
                val top = bounds.top.toInt().coerceAtLeast(0)
                val right = bounds.right.toInt().coerceAtMost(width - 1)
                val bottom = bounds.bottom.toInt().coerceAtMost(height - 1)

                for (y in top..bottom) {
                    for (x in left..right) {
                        // Apply shader if available, otherwise use paint color
                        val color = if (currentShader != null) {
                            applyShader(paint.color, x.toFloat(), y.toFloat())
                        } else {
                            paint.color
                        }
                        bitmap.setPixel(x, y, color)
                    }
                }
            }
            PaintStyle.STROKE -> {
                // Draw the path outline
                // This is simplified - real stroke rendering is complex
                val bounds = path.getBounds()
                rasterizeRect(bounds, paint)
            }
            PaintStyle.FILL_AND_STROKE -> {
                rasterizePath(path, paint.copy().apply { style = PaintStyle.FILL })
                rasterizePath(path, paint.copy().apply { style = PaintStyle.STROKE })
            }
        }
    }

    private fun rasterizeImage(image: Bitmap, src: Rect, dst: Rect, paint: Paint) {
        // Simple image drawing with nearest-neighbor sampling
        val srcLeft = src.left.toInt().coerceAtLeast(0)
        val srcTop = src.top.toInt().coerceAtLeast(0)
        val srcRight = src.right.toInt().coerceAtMost(image.getWidth())
        val srcBottom = src.bottom.toInt().coerceAtMost(image.getHeight())

        val dstLeft = dst.left.toInt().coerceAtLeast(0)
        val dstTop = dst.top.toInt().coerceAtLeast(0)
        val dstRight = dst.right.toInt().coerceAtMost(width)
        val dstBottom = dst.bottom.toInt().coerceAtMost(height)

        val srcWidth = srcRight - srcLeft
        val srcHeight = srcBottom - srcTop
        val dstWidth = dstRight - dstLeft
        val dstHeight = dstBottom - dstTop

        if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0 || dstHeight <= 0) return

        for (dy in 0 until dstHeight) {
            for (dx in 0 until dstWidth) {
                // Calculate source coordinates
                val sx = srcLeft + (dx * srcWidth / dstWidth)
                val sy = srcTop + (dy * srcHeight / dstHeight)

                // Get source pixel
                val srcColor = image.getPixel(sx, sy)

                // Apply paint color filter if any
                var finalColor = paint.colorFilter?.apply(srcColor) ?: srcColor

                // Apply shader if available (shaders can modify image colors)
                if (currentShader != null) {
                    finalColor = applyShader(finalColor, (dstLeft + dx).toFloat(), (dstTop + dy).toFloat())
                }

                // Set destination pixel
                val dstX = dstLeft + dx
                val dstY = dstTop + dy
                bitmap.setPixel(dstX, dstY, finalColor)
            }
        }
    }
    
    // Shader support methods
    override fun setShader(shader: Shader?) {
        currentShader = shader
    }
    
    override fun getShader(): Shader? {
        return currentShader
    }
    
    /**
     * Apply shader to a color at specific coordinates
     */
    private fun applyShader(color: Color, x: Float, y: Float): Color {
        return currentShader?.applyToColor(color, x, y) ?: color
    }
}