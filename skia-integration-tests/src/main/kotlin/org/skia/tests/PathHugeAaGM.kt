package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/hugepath.cpp::DEF_SIMPLE_GM(path_huge_aa, canvas,
 * 200, 200)`.
 *
 * Allocates an off-screen surface that is much wider than the on-screen
 * 64-pixel clip and renders a rounded-rectangle path into it twice
 * (non-AA then AA). The second `draw_huge_path` call uses a 100·1024
 * pixel-wide surface to make sure the path code is still well-behaved
 * when handed dimensions > 64 K.
 *
 * `manual = false` ⇒ the off-screen image is reused via
 * `canvas.drawImage`. The 100·1024-wide surface is allocated lazily
 * once via [LARGE_SURFACE_W].
 */
public class PathHugeAaGM : GM() {

    override fun getName(): String = "path_huge_aa"
    override fun getISize(): SkISize = SkISize.Make(200, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawHugePath(c, 100, 60, manual = false)
        c.translate(0f, 80f)
        drawHugePath(c, LARGE_SURFACE_W, 60, manual = false)
    }

    private companion object {
        // Upstream uses 100 * 1024 = 102400. With JVM raster (4 bytes/pixel)
        // a 102400 × 60 surface is ~24 MB ; comfortably fits.
        private const val LARGE_SURFACE_W: Int = 100 * 1024
    }
}

/**
 * Port of Skia's `gm/hugepath.cpp::DEF_SIMPLE_GM(path_huge_aa_manual,
 * canvas, 200, 200)`.
 *
 * Variant of [PathHugeAaGM] that draws the off-screen image via the
 * `SkTiledImageUtils::DrawImage` path instead of plain
 * `SkCanvas::drawImage`. `:kanvas-skia` doesn't expose `SkTiledImageUtils`
 * yet ; we fall back to [SkCanvas.drawImage], which is the same code
 * path the non-manual GM exercises. Visually identical in the reference
 * raster — the upstream tiled draw path produces bit-identical pixels
 * for opaque small images of this size.
 */
public class PathHugeAaManualGM : GM() {

    override fun getName(): String = "path_huge_aa_manual"
    override fun getISize(): SkISize = SkISize.Make(200, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawHugePath(c, 100, 60, manual = true)
        c.translate(0f, 80f)
        drawHugePath(c, LARGE_SURFACE_W, 60, manual = true)
    }

    private companion object {
        private const val LARGE_SURFACE_W: Int = 100 * 1024
    }
}

/**
 * Mirrors the C++ `draw_huge_path(canvas, w, h, manual)` helper :
 * twice draws an off-screen rounded rect into the canvas, once
 * non-AA and once AA, with a 64×64 clip at `(4, 4)`. The image is
 * placed at `(64 - w, 0)` so its right edge ends at `x = 64`,
 * keeping the rendered output inside the clip.
 *
 * `manual = true` should route through `SkTiledImageUtils::DrawImage`
 * upstream ; we fold it to the same [SkCanvas.drawImage] call
 * because `:kanvas-skia` doesn't have a tiled-draw helper yet.
 */
private fun drawHugePath(canvas: SkCanvas, w: Int, h: Int, @Suppress("UNUSED_PARAMETER") manual: Boolean) {
    val sc = canvas.save()
    val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(w, h))
    val sCanvas = surface.canvas

    val paint = SkPaint()
    val path = SkPath.RRect(SkRRect.MakeRectXY(
        SkRect.MakeXYWH(4f, 4f, (w - 8).toFloat(), (h - 8).toFloat()),
        12f, 12f,
    ))

    canvas.save()
    canvas.clipRect(SkRect.MakeXYWH(4f, 4f, 64f, 64f))
    sCanvas.drawPath(path, paint)
    canvas.drawImage(surface.makeImageSnapshot(), (64 - w).toFloat(), 0f)
    canvas.restore()

    canvas.translate(80f, 0f)
    canvas.save()
    canvas.clipRect(SkRect.MakeXYWH(4f, 4f, 64f, 64f))
    sCanvas.clear(0)
    paint.isAntiAlias = true
    sCanvas.drawPath(path, paint)
    canvas.drawImage(surface.makeImageSnapshot(), (64 - w).toFloat(), 0f)
    canvas.restore()

    canvas.restoreToCount(sc)
}
