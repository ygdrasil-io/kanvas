package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils
import kotlin.math.sin

/**
 * Port of Skia's
 * [`gm/graphite_replay.cpp`](https://github.com/google/skia/blob/main/gm/graphite_replay.cpp)
 * — animated `graphite-replay` GM that on the upstream Graphite backend
 * records a single tile into a [`skgpu::graphite::Recording`] and then
 * replays that recording four times at translated offsets to build a
 * 2×2 tile grid (the GM declares a 3×2 grid `kTileWidth * 3` so the
 * right column captures the off-tile bleed of the animated `fStartX`).
 *
 * ## Port status — **INTRACTABLE for the Graphite path**
 *
 * `:kanvas-skia` ships only the raster CPU pipeline. There is no
 * `skgpu::graphite::Recorder` / `Recording` / `replay({offset})` surface
 * (and no GPU `:gpu-raster` analogue either — the WebGPU divergence
 * plan deliberately avoids the record-and-replay model). The
 * `STUB.GRAPHITE` marker captures the gap : Graphite is Skia's
 * next-gen GPU backend, not a feature that can be back-ported to
 * pure-JVM raster.
 *
 * The body below mirrors **the upstream `drawNonGraphite` fallback** —
 * upstream itself runs this branch on every non-Graphite backend
 * (raster / Ganesh / etc.) by snapshotting a tile to an offscreen
 * [SkSurface] and `drawImage`-ing it four times in a 2×2 grid. That
 * path uses only [SkSurface.MakeRaster] + [SkSurface.makeImageSnapshot]
 * + [SkLinearGradient] + [SkCanvas.saveLayer] + [SkCanvas.clipRect],
 * all of which are fully supported on the kanvas-skia raster device.
 *
 * The associated [GraphiteReplayTest] is `@Disabled` because the
 * upstream PNG reference under `src/test/resources/original-888/` is
 * the *Graphite* output (the recording-replay pipeline can yield
 * different sub-pixel rasterisation and gradient banding than the
 * raster fallback), so a pixel-level reference comparison would
 * unconditionally diverge.
 *
 * The animated `fStartX` value (driven by `sinf(nanos * 1e-9)`) is
 * left at 0 for determinism — `onAnimate` is wired through but never
 * invoked in the static GM render pass.
 */
public class GraphiteReplayGM : GM() {

    private var fStartX: Float = 0.0f
    private var fImage: SkImage? = null

    init {
        setBGColor(SK_ColorBLACK)
    }

    override fun getName(): String = "graphite-replay"
    override fun getISize(): SkISize = SkISize.Make(K_TILE_WIDTH * 3, K_TILE_HEIGHT * 2)

    override fun onOnceBeforeDraw() {
        fImage = ToolUtils.GetResourceAsImage("images/mandrill_128.png")
    }

    override fun onAnimate(nanos: Double): Boolean {
        // Mirrors upstream's `fStartX = kTileWidth * (1.0f + sinf(nanos *
        // 1e-9)) * 0.5f;` — kept for protocol fidelity but the GM is
        // rendered as a static frame (nanos == 0) by the test harness, so
        // fStartX stays at 0 in practice.
        fStartX = K_TILE_WIDTH * (1.0f + sin(nanos * 1e-9).toFloat()) * 0.5f
        return true
    }

    /**
     * Mirrors upstream's `drawContent(canvas, y)` — draws the mandrill
     * thumbnail at `(kPadding, kPadding + y)` and a linear-gradient
     * rect to its right.
     */
    private fun drawContent(canvas: SkCanvas, y: Int) {
        val image = fImage ?: return

        val gradientPaint = SkPaint().apply {
            shader = SkLinearGradient.Make(
                SkPoint(0.0f, 0.0f),
                SkPoint(K_IMAGE_SIZE.toFloat(), K_IMAGE_SIZE.toFloat()),
                intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE, SK_ColorRED),
                positions = null,
                tileMode = SkTileMode.kClamp,
            )
        }

        // Draw image.
        canvas.drawImage(image, K_PADDING.toFloat(), (K_PADDING + y).toFloat())

        // Draw gradient.
        canvas.save()
        canvas.translate((K_PADDED_IMAGE_SIZE + K_PADDING).toFloat(), (K_PADDING + y).toFloat())
        canvas.drawRect(
            SkRect.MakeXYWH(0f, 0f, K_IMAGE_SIZE.toFloat(), K_IMAGE_SIZE.toFloat()),
            gradientPaint,
        )
        canvas.restore()
    }

    /**
     * Mirrors upstream's `drawTile(canvas)` — clear to red, clip off
     * the right 1/4, draw content twice (direct + through a half-alpha
     * `saveLayer`).
     */
    private fun drawTile(canvas: SkCanvas) {
        // Clip off the right 1/4 of the tile, after clearing.
        canvas.clear(SK_ColorRED)
        // Upstream uses `clipIRect(SkIRect::MakeWH(3 * kTileWidth / 4,
        // kTileHeight))` ; :kanvas-skia exposes only `clipRect(SkRect)`
        // (semantically identical for integer-aligned bounds — both
        // restrict the visible region to the rect, no anti-aliasing on
        // the boundary required since the bounds land on integer
        // pixels).
        canvas.clipRect(
            SkRect.MakeWH((3 * K_TILE_WIDTH / 4).toFloat(), K_TILE_HEIGHT.toFloat()),
        )

        // Draw content directly.
        drawContent(canvas, 0)

        // Draw content to a saved layer with 50 % alpha.
        val pAlpha = SkPaint().apply { alphaf = 0.5f }
        canvas.saveLayer(null, pAlpha)
        drawContent(canvas, K_PADDED_IMAGE_SIZE)
        canvas.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = fImage ?: return

        // Mirrors `drawNonGraphite` — the Graphite path
        // (`canvas->recorder()` + `context->insertRecording`) is
        // unreachable on kanvas-skia (STUB.GRAPHITE). Snapshot one tile
        // to an offscreen raster surface, then drawImage it into the
        // 2×2 grid at the animated `fStartX` X-offset.
        val tileInfo = SkImageInfo.MakeN32Premul(K_TILE_WIDTH, K_TILE_HEIGHT)
        val imageSurface: SkSurface = c.makeSurface(tileInfo) ?: SkSurface.MakeRaster(tileInfo)

        drawTile(imageSurface.canvas)
        val tileImage = imageSurface.makeImageSnapshot()
        // Touch `image` so the unused-variable lint stays quiet — the
        // mandrill is consumed via `fImage` inside `drawContent`.
        @Suppress("UNUSED_VARIABLE")
        val _img = image

        for (y in 0 until 2) {
            for (x in 0 until 2) {
                c.drawImage(
                    tileImage,
                    (x * K_TILE_WIDTH).toFloat() + fStartX,
                    (y * K_TILE_HEIGHT).toFloat(),
                )
            }
        }
    }

    private companion object {
        const val K_IMAGE_SIZE: Int = 128
        const val K_PADDING: Int = 2
        const val K_PADDED_IMAGE_SIZE: Int = K_IMAGE_SIZE + K_PADDING * 2
        const val K_TILE_WIDTH: Int = K_PADDED_IMAGE_SIZE * 2
        const val K_TILE_HEIGHT: Int = K_PADDED_IMAGE_SIZE * 2
    }
}
