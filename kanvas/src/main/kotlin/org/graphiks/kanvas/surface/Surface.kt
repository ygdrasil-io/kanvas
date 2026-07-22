package org.graphiks.kanvas.surface

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.surface.gpu.renderViaGpu
import org.graphiks.kanvas.types.Rect

/**
 * A raster surface that produces a [RenderResult] from [Canvas] drawing commands.
 *
 * Wraps an off-screen pixel buffer of the given [width] x [height] and
 * [format]. Use [canvas] to obtain a [Canvas] for recording operations, then
 * call [render] to produce the final [RenderResult].
 *
 * @property width   surface width in pixels
 * @property height  surface height in pixels
 * @property format  pixel memory layout; defaults to [PixelFormat.RGBA8]
 */
class Surface(
    val width: Int,
    val height: Int,
    val format: PixelFormat = PixelFormat.RGBA8,
    val config: RenderConfig = RenderConfig.DEFAULT,
) {
    private val buffer = SurfaceDisplayListBuffer()
    private var canvasInstance: Canvas? = null

    /** Return a snapshot of recorded display operations (for diagnostic replay). */
    fun snapshotOps(): List<DisplayOp> = buffer.ops()

    /** Optional listener for per-operation pipeline events (DebugLevel.TRACE). */
    var renderOpListener: RenderOpListener? = null

    /**
     * Run a block of drawing commands on this surface's [Canvas].
     * The canvas is created lazily on first access and reused for subsequent calls.
     */
    fun canvas(block: Canvas.() -> Unit) { val c = canvas(); c.block() }

    /**
     * Obtain (or create) the [Canvas] associated with this surface.
     * Multiple calls return the same instance.
     */
    fun canvas(): Canvas { if (canvasInstance == null) canvasInstance = Canvas(buffer); return canvasInstance!! }

    /**
     * Render all recorded drawing commands to a pixel buffer.
     *
     * The returned [RenderResult] contains the rasterised pixels, any diagnostics
     * accumulated during processing, and rendering statistics. The pixel buffer
     * is allocated fresh each call.
     */
    fun render(): RenderResult = renderViaGpu(buffer, width, height, format, config)

    /**
     * Render all recorded commands and capture the result as an [Image].
     *
     * Equivalent to Skia's `surface.makeImageSnapshot()`.
     * The returned [Image] carries pixel data and can be passed to
     * [Canvas.drawImage] on another surface.
     */
    fun makeImageSnapshot(): Image = render().toImage("surface-snapshot")

    /**
     * Render and capture a sub-rectangle as an [Image].
     *
     * Equivalent to Skia's `surface.makeImageSnapshot(subset)`.
     * Returns null if [subset] is empty or lies outside the surface bounds.
     */
    fun makeImageSnapshot(subset: Rect): Image? {
        val result = render()
        val sx = subset.left.toInt().coerceIn(0, result.width)
        val sy = subset.top.toInt().coerceIn(0, result.height)
        val sw = subset.width.toInt().coerceAtMost(result.width - sx)
        val sh = subset.height.toInt().coerceAtMost(result.height - sy)
        if (sw <= 0 || sh <= 0) return null
        val pixels = ByteArray(sw * sh * 4)
        for (row in 0 until sh) {
            val srcOff = ((sy + row) * result.width + sx) * 4
            val dstOff = row * sw * 4
            result.pixels.toByteArray().copyInto(pixels, dstOff, srcOff, srcOff + sw * 4)
        }
        val colorType = when (result.format) {
            PixelFormat.RGBA8 -> ColorType.RGBA_8888
            PixelFormat.BGRA8 -> ColorType.BGRA_8888
        }
        return Image(sw, sh, colorType, "surface-snapshot-subset", pixels)
    }

    /**
     * Copy rendered pixels from a rectangular region into [dstBuffer].
     * Calls [render] on every invocation (no implicit caching).
     *
     * @param src the source rectangle in surface coordinates
     * @param dstBuffer pre-allocated buffer of size (src.width * src.height * 4)
     * @return true on success, false if the region is out of bounds
     */
    fun readPixels(src: Rect, dstBuffer: UByteArray): Boolean {
        val result = render()
        val sx = src.left.toInt().coerceIn(0, width)
        val sy = src.top.toInt().coerceIn(0, height)
        val sw = src.width.toInt().coerceAtMost(width - sx)
        val sh = src.height.toInt().coerceAtMost(height - sy)
        if (sw <= 0 || sh <= 0) return false
        val stride = 4
        val expectedSize = sw * sh * stride
        if (dstBuffer.size < expectedSize) return false
        for (row in 0 until sh) {
            val srcOffset = ((sy + row) * width + sx) * stride
            val dstOffset = row * sw * stride
            result.pixels.copyInto(dstBuffer, dstOffset, srcOffset, srcOffset + sw * stride)
        }
        return true
    }
}

private class SurfaceDisplayListBuffer : DisplayListBuffer {
    private val ops = mutableListOf<DisplayOp>()
    override fun append(op: DisplayOp) { ops.add(op) }
    override fun ops(): List<DisplayOp> = ops.toList()
}
