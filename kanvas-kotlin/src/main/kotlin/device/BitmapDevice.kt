package device

import com.kanvas.core.AlphaType
import com.kanvas.core.Arc
import com.kanvas.core.Bitmap
import com.kanvas.core.BitmapConfig
import com.kanvas.core.Color
import com.kanvas.core.ColorInfo
import com.kanvas.core.ColorSpace
import com.kanvas.core.ColorType
import com.kanvas.core.CubicResampler
import com.kanvas.core.Device
import com.kanvas.core.FilterMode
import com.kanvas.core.Matrix
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Path
import com.kanvas.core.RRect
import com.kanvas.core.Rect
import com.kanvas.core.SamplingOptions
import com.kanvas.core.Shader
import com.kanvas.core.SurfaceProps
import core.GlyphPainter
import core.GlyphRun
import core.GlyphRunList
import core.Point
import core.SimpleRect
import core.createGlyphRun

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
    
    // Glyph painter for text rendering
    private val glyphPainter: GlyphPainter = GlyphPainter()

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

    override fun drawOval(oval: Rect, paint: Paint) {
        // Apply clip
        val clippedOval = oval.intersect(clipBounds)
        if (clippedOval.isEmpty) return

        // Apply transform
        val transformedOval = currentMatrix.mapRect(clippedOval)

        // Rasterize the oval (simplified - use bounding box for now)
        rasterizeOval(transformedOval, paint)
    }

    override fun drawArc(arc: Arc, paint: Paint) {
        // Apply clip to the arc's oval
        val clippedOval = arc.oval.intersect(clipBounds)
        if (clippedOval.isEmpty) return

        // Create a transformed arc
        val transformedOval = currentMatrix.mapRect(clippedOval)
        val transformedArc = Arc(transformedOval, arc.startAngle, arc.sweepAngle, arc.useCenter)

        // Rasterize the arc
        rasterizeArc(transformedArc, paint)
    }

    override fun drawRRect(rrect: RRect, paint: Paint) {
        // Apply clip to the RRect's bounds
        val clippedBounds = rrect.rect.intersect(clipBounds)
        if (clippedBounds.isEmpty) return

        // Create a transformed RRect
        val transformedRect = currentMatrix.mapRect(clippedBounds)
        val transformedRRect = RRect(transformedRect, rrect.rx, rrect.ry)

        // Rasterize the rounded rectangle
        rasterizeRRect(transformedRRect, paint)
    }

    override fun drawPaint(paint: Paint) {
        // Fill the entire clip region with the paint
        val clipBounds = getClipBounds()
        if (clipBounds.isEmpty) return

        // Apply transform to the clip bounds
        val transformedClip = currentMatrix.mapRect(clipBounds)

        // Rasterize using the paint color
        rasterizeRect(transformedClip, paint)
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
        // Create a GlyphRun from the text
        val font = paint.getFont() ?: return
        val glyphRun = font.createGlyphRun(text, x, y)
        
        // Draw the GlyphRun using our GlyphPainter
        drawGlyphRun(glyphRun, paint)
    }
    
    /**
     * Draw a GlyphRun on this device.
     * This is the core text rendering method that handles the actual glyph drawing.
     */
    fun drawGlyphRun(glyphRun: GlyphRun, paint: Paint) {
        // Apply clip to the glyph run bounds
        val glyphBounds = glyphRun.getBounds()
        val clippedBounds = SimpleRect(
            kotlin.math.max(glyphBounds.left, clipBounds.left),
            kotlin.math.max(glyphBounds.top, clipBounds.top),
            kotlin.math.min(glyphBounds.right, clipBounds.right),
            kotlin.math.min(glyphBounds.bottom, clipBounds.bottom)
        )
        
        // If the glyph run is completely outside the clip, skip rendering
        if (clippedBounds.isEmpty) return
        
        // Apply transform to the glyph run
        val transformedGlyphRun = applyTransformToGlyphRun(glyphRun)
        
        // Use the GlyphPainter to render the glyph run
        glyphPainter.drawGlyphRun(this, transformedGlyphRun, paint)
    }

    /**
     * Handle glyph runs with RSXForm by simplifying and redrawing.
     * Inspired by Skia's simplifyGlyphRunRSXFormAndRedraw method.
     * 
     * For now, this converts RSXForm glyphs to path-based rendering.
     * In the future, this could be optimized with proper RSXForm support.
     */
    private fun simplifyGlyphRunRSXFormAndRedraw(glyphRunList: GlyphRunList, paint: Paint) {
        // Create a temporary paint that forces path-based rendering
        val tempPaint = paint.copy()
        // Note: We don't actually modify the paint here since our GlyphPainter
        // already handles complex transforms by using path-based rendering
        
        // Draw each glyph run with RSXForm handling
        for (glyphRun in glyphRunList) {
            if (glyphRun.hasRSXForm()) {
                // Use path-based rendering for glyphs with RSXForm
                drawGlyphRunWithRSXForm(glyphRun, tempPaint)
            } else {
                // Regular rendering for glyphs without RSXForm
                drawGlyphRun(glyphRun, tempPaint)
            }
        }
    }

    /**
     * Draw a glyph run that contains RSXForm transformations.
     * This uses path-based rendering to handle the complex transformations.
     */
    private fun drawGlyphRunWithRSXForm(glyphRun: GlyphRun, paint: Paint) {
        // Apply clip to the glyph run bounds
        val glyphBounds = glyphRun.getBounds()
        val clippedBounds = SimpleRect(
            kotlin.math.max(glyphBounds.left, clipBounds.left),
            kotlin.math.max(glyphBounds.top, clipBounds.top),
            kotlin.math.min(glyphBounds.right, clipBounds.right),
            kotlin.math.min(glyphBounds.bottom, clipBounds.bottom)
        )
        
        // If the glyph run is completely outside the clip, skip rendering
        if (clippedBounds.isEmpty) return
        
        // Apply transform to the glyph run
        val transformedGlyphRun = applyTransformToGlyphRun(glyphRun)
        
        // Use the GlyphPainter to render the glyph run with RSXForm support
        // The GlyphPainter should detect the complex transforms and use path-based rendering
        glyphPainter.drawGlyphRun(this, transformedGlyphRun, paint)
    }

    /**
     * Draw a list of glyph runs on this device.
     * This is the core text rendering method that handles multiple glyph runs efficiently.
     * Inspired by Skia's onDrawGlyphRunList method.
     */
    override fun onDrawGlyphRunList(glyphRunList: GlyphRunList, paint: Paint) {
        // Check if the glyph run list is empty
        if (glyphRunList.isEmpty()) return
        
        // Apply clip to the glyph run list bounds
        val glyphRunListBounds = glyphRunList.getSourceBoundsWithOrigin()
        val clippedBounds = SimpleRect(
            kotlin.math.max(glyphRunListBounds.left, clipBounds.left),
            kotlin.math.max(glyphRunListBounds.top, clipBounds.top),
            kotlin.math.min(glyphRunListBounds.right, clipBounds.right),
            kotlin.math.min(glyphRunListBounds.bottom, clipBounds.bottom)
        )
        
        // If the glyph run list is completely outside the clip, skip rendering
        if (clippedBounds.isEmpty) return
        
        // Check for RSXForm (rotated/scaled glyphs) - similar to Skia's approach
        if (glyphRunList.hasRSXForm()) {
            // RSXForm handling: For now, we simplify by converting to paths
            // In the future, this could be optimized with proper RSXForm support
            simplifyGlyphRunRSXFormAndRedraw(glyphRunList, paint)
        } else {
            // Regular glyph run rendering (no RSXForm)
            for (glyphRun in glyphRunList) {
                drawGlyphRun(glyphRun, paint)
            }
        }
    }
    

    
    /**
     * Apply the current transform matrix to a GlyphRun.
     * This transforms each glyph position according to the device's current matrix.
     */
    private fun applyTransformToGlyphRun(glyphRun: GlyphRun): GlyphRun {
        val transformedPositions = glyphRun.positions.map { position ->
            // Apply the matrix transformation to the point
            val newX = currentMatrix.scaleX * position.x + currentMatrix.skewY * position.y + currentMatrix.transX
            val newY = currentMatrix.skewX * position.x + currentMatrix.scaleY * position.y + currentMatrix.transY
            Point(newX, newY)
        }
        
        return GlyphRun(glyphRun.font, transformedPositions, glyphRun.glyphs)
    }

    override fun drawImage(image: Bitmap, src: Rect, dst: Rect, paint: Paint) {
        drawImage(image, src, dst, paint, SamplingOptions.DEFAULT)
    }

    override fun drawImage(image: Bitmap, src: Rect, dst: Rect, paint: Paint, sampling: SamplingOptions) {
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

        // Rasterize the image with sampling options
        rasterizeImageWithSampling(image, proportionalSrc, transformedDst, paint, sampling)
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
        rasterizeImageWithSampling(image, src, dst, paint, SamplingOptions.DEFAULT)
    }

    private fun rasterizeImageWithSampling(image: Bitmap, src: Rect, dst: Rect, paint: Paint, sampling: SamplingOptions) {
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

        when (sampling.filterMode) {
            FilterMode.NEAREST -> rasterizeWithNearest(image, srcLeft, srcTop, srcWidth, srcHeight, 
                                                       dstLeft, dstTop, dstWidth, dstHeight, paint)
            FilterMode.LINEAR -> rasterizeWithLinear(image, srcLeft, srcTop, srcWidth, srcHeight,
                                                     dstLeft, dstTop, dstWidth, dstHeight, paint)
            FilterMode.CUBIC -> rasterizeWithCubic(image, srcLeft, srcTop, srcWidth, srcHeight,
                                                     dstLeft, dstTop, dstWidth, dstHeight, paint, sampling.cubicResampler)
        }
    }

    private fun rasterizeWithNearest(image: Bitmap, srcLeft: Int, srcTop: Int, srcWidth: Int, srcHeight: Int,
                                     dstLeft: Int, dstTop: Int, dstWidth: Int, dstHeight: Int, paint: Paint) {
        for (dy in 0 until dstHeight) {
            for (dx in 0 until dstWidth) {
                // Nearest neighbor - simple rounding
                val sx = srcLeft + (dx * srcWidth / dstWidth)
                val sy = srcTop + (dy * srcHeight / dstHeight)

                var srcColor = image.getPixel(sx, sy)
                var finalColor = paint.colorFilter?.apply(srcColor) ?: srcColor

                if (currentShader != null) {
                    finalColor = applyShader(finalColor, (dstLeft + dx).toFloat(), (dstTop + dy).toFloat())
                }

                bitmap.setPixel(dstLeft + dx, dstTop + dy, finalColor)
            }
        }
    }

    private fun rasterizeWithLinear(image: Bitmap, srcLeft: Int, srcTop: Int, srcWidth: Int, srcHeight: Int,
                                    dstLeft: Int, dstTop: Int, dstWidth: Int, dstHeight: Int, paint: Paint) {
        for (dy in 0 until dstHeight) {
            for (dx in 0 until dstWidth) {
                // Calculate source coordinates with fractional parts
                val srcX = srcLeft + (dx * srcWidth.toFloat() / dstWidth)
                val srcY = srcTop + (dy * srcHeight.toFloat() / dstHeight)

                val x0 = srcX.toInt()
                val y0 = srcY.toInt()
                val x1 = minOf(x0 + 1, srcLeft + srcWidth - 1)
                val y1 = minOf(y0 + 1, srcTop + srcHeight - 1)

                val dxFrac = srcX - x0
                val dyFrac = srcY - y0

                // Get the 4 surrounding pixels
                val c00 = image.getPixel(x0, y0).toArgb()
                val c01 = image.getPixel(x0, y1).toArgb()
                val c10 = image.getPixel(x1, y0).toArgb()
                val c11 = image.getPixel(x1, y1).toArgb()

                // Bilinear interpolation
                val interpolated = interpolateColors(
                    interpolateColors(c00, c01, dyFrac),
                    interpolateColors(c10, c11, dyFrac),
                    dxFrac
                )

                var finalColor = paint.colorFilter?.apply(Color.fromArgb(interpolated)) ?: Color.fromArgb(interpolated)

                if (currentShader != null) {
                    finalColor = applyShader(finalColor, (dstLeft + dx).toFloat(), (dstTop + dy).toFloat())
                }

                bitmap.setPixel(dstLeft + dx, dstTop + dy, finalColor)
            }
        }
    }

    private fun rasterizeWithCubic(image: Bitmap, srcLeft: Int, srcTop: Int, srcWidth: Int, srcHeight: Int,
                                   dstLeft: Int, dstTop: Int, dstWidth: Int, dstHeight: Int, paint: Paint, resampler: CubicResampler) {
        for (dy in 0 until dstHeight) {
            for (dx in 0 until dstWidth) {
                // Calculate source coordinates with fractional parts
                val srcX = srcLeft + (dx * srcWidth.toFloat() / dstWidth)
                val srcY = srcTop + (dy * srcHeight.toFloat() / dstHeight)

                // Bicubic interpolation uses 4x4 neighborhood
                val x0 = maxOf(srcLeft, srcX.toInt() - 1)
                val y0 = maxOf(srcTop, srcY.toInt() - 1)

                val weightsX = resampler.getWeights(srcX - x0)
                val weightsY = resampler.getWeights(srcY - y0)

                // Get the 16 surrounding pixels (4x4 grid)
                val colors = Array(4) { Array(4) { 0 } }
                for (dyOffset in 0 until 4) {
                    for (dxOffset in 0 until 4) {
                        val px = minOf(x0 + dxOffset, srcLeft + srcWidth - 1)
                        val py = minOf(y0 + dyOffset, srcTop + srcHeight - 1)
                        colors[dyOffset][dxOffset] = image.getPixel(px, py).toArgb()
                    }
                }

                // Apply bicubic interpolation
                var finalColor = 0
                for (dyOffset in 0 until 4) {
                    var rowColor = 0
                    for (dxOffset in 0 until 4) {
                        rowColor = interpolateColors(rowColor, colors[dyOffset][dxOffset], weightsX[dxOffset])
                    }
                    finalColor = interpolateColors(finalColor, rowColor, weightsY[dyOffset])
                }

                var processedColor = paint.colorFilter?.apply(Color.fromArgb(finalColor)) ?: Color.fromArgb(finalColor)

                if (currentShader != null) {
                    processedColor = applyShader(processedColor, (dstLeft + dx).toFloat(), (dstTop + dy).toFloat())
                }

                bitmap.setPixel(dstLeft + dx, dstTop + dy, processedColor)
            }
        }
    }

    private fun interpolateColors(c1: Int, c2: Int, t: Float): Int {
        val a1 = (c1 shr 24) and 0xFF
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF

        val a2 = (c2 shr 24) and 0xFF
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF

        val a = ((a1 * (1 - t) + a2 * t)).toInt() and 0xFF
        val r = ((r1 * (1 - t) + r2 * t)).toInt() and 0xFF
        val g = ((g1 * (1 - t) + g2 * t)).toInt() and 0xFF
        val b = ((b1 * (1 - t) + b2 * t)).toInt() and 0xFF

        return (a shl 24) or (r shl 16) or (g shl 8) or b
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

    // ===== Advanced Primitive Rasterization =====

    private fun rasterizeOval(oval: Rect, paint: Paint) {
        // Simplified oval rasterization using midpoint algorithm
        val centerX = oval.centerX
        val centerY = oval.centerY
        val radiusX = oval.width / 2
        val radiusY = oval.height / 2
        
        // Convert to integer coordinates
        val left = oval.left.toInt().coerceAtLeast(0)
        val top = oval.top.toInt().coerceAtLeast(0)
        val right = oval.right.toInt().coerceAtMost(width - 1)
        val bottom = oval.bottom.toInt().coerceAtMost(height - 1)
        
        // Simple approach: fill the bounding box (for now)
        // TODO: Implement proper ellipse rasterization
        when (paint.style) {
            PaintStyle.FILL -> {
                for (y in top..bottom) {
                    for (x in left..right) {
                        // Check if point is inside the ellipse
                        val dx = (x - centerX) / radiusX
                        val dy = (y - centerY) / radiusY
                        if (dx * dx + dy * dy <= 1.0) {
                            val color = applyShader(paint.color, x.toFloat(), y.toFloat())
                            bitmap.setPixel(x, y, color)
                        }
                    }
                }
            }
            PaintStyle.STROKE -> {
                // Draw the bounding rectangle as a placeholder
                rasterizeRect(Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()), paint)
            }
            PaintStyle.FILL_AND_STROKE -> {
                rasterizeOval(oval, paint.copy().apply { style = PaintStyle.FILL })
                rasterizeOval(oval, paint.copy().apply { style = PaintStyle.STROKE })
            }
        }
    }

    private fun rasterizeArc(arc: Arc, paint: Paint) {
        // Simplified arc rasterization
        if (!arc.isValid) return
        
        // Convert to integer coordinates
        val left = arc.oval.left.toInt().coerceAtLeast(0)
        val top = arc.oval.top.toInt().coerceAtLeast(0)
        val right = arc.oval.right.toInt().coerceAtMost(width - 1)
        val bottom = arc.oval.bottom.toInt().coerceAtMost(height - 1)
        
        // For now, draw the bounding oval
        rasterizeOval(arc.oval, paint)
        
        // TODO: Implement proper arc rasterization based on angles
    }

    private fun rasterizeRRect(rrect: RRect, paint: Paint) {
        // Simplified rounded rectangle rasterization
        val rect = rrect.rect
        
        // Convert to integer coordinates
        val left = rect.left.toInt().coerceAtLeast(0)
        val top = rect.top.toInt().coerceAtLeast(0)
        val right = rect.right.toInt().coerceAtMost(width - 1)
        val bottom = rect.bottom.toInt().coerceAtMost(height - 1)
        
        when (paint.style) {
            PaintStyle.FILL -> {
                // Fill the main rectangle area
                for (y in top + rrect.ry.toInt()..bottom - rrect.ry.toInt()) {
                    for (x in left + rrect.rx.toInt()..right - rrect.rx.toInt()) {
                        val color = applyShader(paint.color, x.toFloat(), y.toFloat())
                        bitmap.setPixel(x, y, color)
                    }
                }
                
                // Fill the rounded corners (simplified)
                // Top-left corner
                fillRoundedCorner(left, top, rrect.rx, rrect.ry, paint)
                // Top-right corner
                fillRoundedCorner(right - rrect.rx.toInt(), top, rrect.rx, rrect.ry, paint)
                // Bottom-left corner
                fillRoundedCorner(left, bottom - rrect.ry.toInt(), rrect.rx, rrect.ry, paint)
                // Bottom-right corner
                fillRoundedCorner(right - rrect.rx.toInt(), bottom - rrect.ry.toInt(), rrect.rx, rrect.ry, paint)
            }
            PaintStyle.STROKE -> {
                // Draw the bounding rectangle as a placeholder
                rasterizeRect(rect, paint)
            }
            PaintStyle.FILL_AND_STROKE -> {
                rasterizeRRect(rrect, paint.copy().apply { style = PaintStyle.FILL })
                rasterizeRRect(rrect, paint.copy().apply { style = PaintStyle.STROKE })
            }
        }
    }

    private fun fillRoundedCorner(x: Int, y: Int, rx: Float, ry: Float, paint: Paint) {
        val radiusX = rx.toInt()
        val radiusY = ry.toInt()
        
        // Simple quarter-ellipse approximation
        for (cornerY in 0..radiusY) {
            for (cornerX in 0..radiusX) {
                val dx = cornerX.toFloat() / radiusX
                val dy = cornerY.toFloat() / radiusY
                if (dx * dx + dy * dy <= 1.0) {
                    val pixelX = x + cornerX
                    val pixelY = y + cornerY
                    if (pixelX >= 0 && pixelX < width && pixelY >= 0 && pixelY < height) {
                        val color = applyShader(paint.color, pixelX.toFloat(), pixelY.toFloat())
                        bitmap.setPixel(pixelX, pixelY, color)
                    }
                }
            }
        }
    }

    // ===== Pixel Access Methods =====

    override fun writePixels(src: Bitmap, x: Int, y: Int): Boolean {
        // Validate parameters
        if (x < 0 || y < 0 || x + src.getWidth() > width || y + src.getHeight() > height) {
            return false
        }

        // Copy pixels from source to device
        for (srcY in 0 until src.getHeight()) {
            for (srcX in 0 until src.getWidth()) {
                val dstX = x + srcX
                val dstY = y + srcY
                bitmap.setPixel(dstX, dstY, src.getPixel(srcX, srcY))
            }
        }
        return true
    }

    override fun readPixels(dst: Bitmap, x: Int, y: Int): Boolean {
        // Validate parameters
        if (x < 0 || y < 0 || x + dst.getWidth() > width || y + dst.getHeight() > height) {
            return false
        }

        // Copy pixels from device to destination
        for (dstY in 0 until dst.getHeight()) {
            for (dstX in 0 until dst.getWidth()) {
                val srcX = x + dstX
                val srcY = y + dstY
                dst.setPixel(dstX, dstY, bitmap.getPixel(srcX, srcY))
            }
        }
        return true
    }

    override fun accessPixels(): Bitmap {
        return bitmap
    }

    override fun peekPixels(): Bitmap {
        return bitmap
    }

    override fun replaceClip(rect: Rect) {
        // Apply device bounds to the new clip
        val deviceBounds = Rect(0f, 0f, width.toFloat(), height.toFloat())
        clipBounds = rect.intersect(deviceBounds)
    }
}