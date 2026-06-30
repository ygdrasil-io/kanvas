package org.graphiks.kanvas.surface

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp

class Surface(
    val width: Int,
    val height: Int,
    val format: PixelFormat = PixelFormat.RGBA8,
) {
    private val buffer = SurfaceDisplayListBuffer()
    private var canvasInstance: Canvas? = null

    fun canvas(block: Canvas.() -> Unit) { val c = canvas(); c.block() }
    fun canvas(): Canvas { if (canvasInstance == null) canvasInstance = Canvas(buffer); return canvasInstance!! }

    fun render(): RenderResult {
        val ops = buffer.ops()
        val dispatched = ops.size
        return RenderResult(
            pixels = UByteArray(width * height * 4) { 0u },
            width = width, height = height,
            diagnostics = Diagnostics(),
            stats = RenderStats(dispatched, 0, 1, dispatched, if (dispatched > 0) 1.0f else 0f),
        )
    }
}

private class SurfaceDisplayListBuffer : DisplayListBuffer {
    private val ops = mutableListOf<DisplayOp>()
    override fun append(op: DisplayOp) { ops.add(op) }
    override fun ops(): List<DisplayOp> = ops.toList()
}
