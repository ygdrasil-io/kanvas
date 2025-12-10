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
    )
) : Device {

    // Internal bitmap that holds the pixel data
    override val bitmap: Bitmap = Bitmap(width, height, BitmapConfig.ARGB_8888)

    // Current transform matrix
    private var currentMatrix: Matrix = Matrix.Companion.identity()

    // Current clip bounds
    private var clipBounds: Rect = Rect(0f, 0f, width.toFloat(), height.toFloat())

    // Current paint properties
    private var currentPaint: Paint? = null

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
                        bitmap.setPixel(x, y, paint.color)
                    }
                }
            }
            PaintStyle.STROKE -> {
                // Simple stroke: draw border pixels
                // Top border
                for (x in left until right) {
                    if (top >= 0 && top < bitmap.getHeight()) bitmap.setPixel(x, top, paint.color)
                }
                // Bottom border
                for (x in left until right) {
                    if (bottom - 1 >= 0 && bottom - 1 < bitmap.getHeight()) bitmap.setPixel(x, bottom - 1, paint.color)
                }
                // Left border
                for (y in top until bottom) {
                    if (left >= 0 && left < bitmap.getWidth()) bitmap.setPixel(left, y, paint.color)
                }
                // Right border
                for (y in top until bottom) {
                    if (right - 1 >= 0 && right - 1 < bitmap.getWidth()) bitmap.setPixel(right - 1, y, paint.color)
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
                        bitmap.setPixel(x, y, paint.color)
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
                val finalColor = paint.colorFilter?.apply(srcColor) ?: srcColor

                // Set destination pixel
                val dstX = dstLeft + dx
                val dstY = dstTop + dy
                bitmap.setPixel(dstX, dstY, finalColor)
            }
        }
    }
}