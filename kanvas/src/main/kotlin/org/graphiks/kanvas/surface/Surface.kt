package org.graphiks.kanvas.surface

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.SnapshotDisplayListBuffer
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.render.ir.DisplayOpSceneAdapter
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.surface.gpu.renderViaGpu
import org.graphiks.math.geometry.RectF32

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
    private val buffer = SnapshotDisplayListBuffer()
    private var canvasInstance: Canvas? = null

    /** Return a snapshot of recorded display operations (for diagnostic replay). */
    fun snapshotOps(): List<DisplayOp> = buffer.ops()

    /**
     * Capture the recorded operations as immutable backend-neutral scene data.
     *
     * This is a read-only recording operation: it does not initialize, submit to,
     * or read back from the legacy GPU renderer.
     */
    fun snapshotScene(limits: SceneCaptureLimits = SceneCaptureLimits.DEFAULT): SceneCaptureResult = DisplayOpSceneAdapter.capture(
        operations = snapshotOps(),
        extent = SceneExtent(width, height),
        colorSpace = ColorSpace.SRGB,
        limits = limits,
    )

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
    fun render(): RenderResult {
        check(!SceneRecordingScope.isRecordingOnly()) {
            "Surface.render is unavailable in a recording-only scene capture scope"
        }
        return renderViaGpu(buffer, width, height, format, config)
    }

    /**
     * Render all recorded commands and capture the result as an [Image].
     *
     * Equivalent to Skia's `surface.makeImageSnapshot()`.
     * The returned [Image] carries pixel data and can be passed to
     * [Canvas.drawImage] on another surface.
     */
    fun makeImageSnapshot(): Image =
        if (SceneRecordingScope.isRecordingOnly()) requireNotNull(recordingImageSnapshot())
        else render().toImage("surface-snapshot")

    /**
     * Render and capture a sub-rectangle as an [Image].
     *
     * Equivalent to Skia's `surface.makeImageSnapshot(subset)`.
     * Returns null if [subset] is empty or lies outside the surface bounds.
     */
    fun makeImageSnapshot(subset: RectF32): Image? {
        if (SceneRecordingScope.isRecordingOnly()) return recordingImageSnapshot(subset)
        val result = render()
        val sx = subset.left.toInt().coerceIn(0, result.width)
        val sy = subset.top.toInt().coerceIn(0, result.height)
        val sw = subset.width().toInt().coerceAtMost(result.width - sx)
        val sh = subset.height().toInt().coerceAtMost(result.height - sy)
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
        return Image(sw, sh, colorType, "surface-snapshot-subset", pixels, alphaType = AlphaType.PREMUL)
    }

    /**
     * Copy rendered pixels from a rectangular region into [dstBuffer].
     * Calls [render] on every invocation (no implicit caching).
     *
     * @param src the source rectangle in surface coordinates
     * @param dstBuffer pre-allocated buffer of size (src.width * src.height * 4)
     * @return true on success, false if the region is out of bounds
     */
    fun readPixels(src: RectF32, dstBuffer: UByteArray): Boolean {
        if (SceneRecordingScope.isRecordingOnly()) return false
        val result = render()
        val sx = src.left.toInt().coerceIn(0, width)
        val sy = src.top.toInt().coerceIn(0, height)
        val sw = src.width().toInt().coerceAtMost(width - sx)
        val sh = src.height().toInt().coerceAtMost(height - sy)
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

    private fun recordingImageSnapshot(subset: RectF32? = null): Image? {
        val (snapshotWidth, snapshotHeight, sourceSuffix) = if (subset == null) {
            Triple(width, height, "full")
        } else {
            val sx = subset.left.toInt().coerceIn(0, width)
            val sy = subset.top.toInt().coerceIn(0, height)
            val sw = subset.width().toInt().coerceAtMost(width - sx)
            val sh = subset.height().toInt().coerceAtMost(height - sy)
            if (sw <= 0 || sh <= 0) return null
            Triple(sw, sh, "subset:$sx,$sy,$sw,$sh")
        }
        val captured = when (val capture = snapshotScene()) {
            is SceneCaptureResult.Captured -> capture
            is SceneCaptureResult.Invalid -> throw IllegalStateException(
                "recording-only Surface snapshot contains invalid scene data: " +
                    capture.diagnostics.joinToString { diagnostic ->
                        "${diagnostic.code.value}: ${diagnostic.message}"
                    },
            )
        }
        val colorType = when (format) {
            PixelFormat.RGBA8 -> ColorType.RGBA_8888
            PixelFormat.BGRA8 -> ColorType.BGRA_8888
        }
        return Image(
            width = snapshotWidth,
            height = snapshotHeight,
            colorType = colorType,
            sourceId = "scene-recording:${captured.scene.canonicalId.value}:$sourceSuffix",
            pixels = null,
            colorSpace = captured.scene.colorSpace,
            alphaType = AlphaType.PREMUL,
        )
    }
}
