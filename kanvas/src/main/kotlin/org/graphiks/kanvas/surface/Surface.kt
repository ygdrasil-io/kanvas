package org.graphiks.kanvas.surface

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.surface.gpu.renderViaGpu

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
) {
    private val buffer = SurfaceDisplayListBuffer()
    private var canvasInstance: Canvas? = null

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
    fun render(): RenderResult = renderViaGpu(buffer, width, height, format)
}

private class SurfaceDisplayListBuffer : DisplayListBuffer {
    private val ops = mutableListOf<DisplayOp>()
    override fun append(op: DisplayOp) { ops.add(op) }
    override fun ops(): List<DisplayOp> = ops.toList()
}
