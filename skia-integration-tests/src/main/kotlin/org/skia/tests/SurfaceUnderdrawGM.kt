package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SkColor
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/surface.cpp::surface_underdraw` (256 × 256).
 *
 * Saves away a right-hand strip of a noisy background, clears it, draws
 * a striped foreground, fades the strip with a `kDstIn` gradient, then
 * composites the original strip back underneath via `kDstOver`.
 *
 * This GM exercises `SkSurface.makeImageSnapshot(SkIRect)` after the
 * subset rectangle has been sanitized against the surface bounds.
 *
 * C++ body (`DEF_SURFACE_TESTS(surface_underdraw, canvas, 256, 256)`):
 * ```cpp
 * SkImageInfo info = SkImageInfo::MakeN32Premul(256, 256, nullptr);
 * auto surf = make(info);
 * const SkIRect subset = SkIRect::MakeLTRB(180, 0, 256, 256);
 * // noisy background (red→blue repeat gradient)
 * // save strip, clear it
 * sk_sp<SkImage> saveImg = surf->makeImageSnapshot(subset);
 * // clear strip, draw foreground (green stripes), fade strip (kDstIn gradient),
 * // restore strip under foreground (kDstOver)
 * surf->draw(canvas, 0, 0);
 * ```
 */
public class SurfaceUnderdrawGM : GM() {

    override fun getName(): String = "surface_underdraw"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val info = SkImageInfo.MakeN32Premul(256, 256)
        val surf = SkSurface.MakeRaster(info)

        val subset = SkIRect.MakeLTRB(180, 0, 256, 256)

        // Noisy background: red→blue repeat linear gradient
        val bgShader = SkLinearGradient.Make(
            p0 = SkPoint(0f, 0f),
            p1 = SkPoint(40f, 50f),
            colors = intArrayOf(
                SkColorSetARGB(0xFF, 0xFF, 0, 0),   // red
                SkColorSetARGB(0xFF, 0, 0, 0xFF),   // blue
            ),
            positions = null,
            tileMode = SkTileMode.kRepeat,
        )
        val bgPaint = SkPaint().apply { shader = bgShader }
        surf.canvas.drawPaint(bgPaint)

        // Save strip then clear it.
        val saveImg = surf.makeImageSnapshot(subset)

        val clearPaint = SkPaint().apply { blendMode = SkBlendMode.kClear }
        surf.canvas.drawRect(SkRect.Make(subset), clearPaint)

        // Draw foreground: green horizontal stripes
        val stripePaint = SkPaint().apply { color = SK_ColorGREEN }
        val r = SkRect.MakeLTRB(0f, 10f, 256f, 35f)
        while (r.bottom < 256f) {
            surf.canvas.drawRect(r, stripePaint)
            val h = r.height()
            r.offset(0f, h * 2f)
        }

        // Fade the strip: left→right transparency via kDstIn gradient
        val fadeShader = SkLinearGradient.Make(
            p0 = SkPoint(subset.left.toFloat(), 0f),
            p1 = SkPoint(subset.right.toFloat(), 0f),
            colors = intArrayOf(
                SkColorSetARGB(0xFF, 0, 0, 0),  // opaque black
                SkColorSetARGB(0x00, 0, 0, 0),  // transparent black
            ),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val fadePaint = SkPaint().apply {
            shader = fadeShader
            blendMode = SkBlendMode.kDstIn
        }
        surf.canvas.drawRect(SkRect.Make(subset), fadePaint)

        // Restore original strip under the foreground via kDstOver
        val restorePaint = SkPaint().apply { blendMode = SkBlendMode.kDstOver }
        surf.canvas.drawImage(
            saveImg,
            subset.left.toFloat(),
            subset.top.toFloat(),
            paint = restorePaint,
        )

        surf.draw(c, 0f, 0f)
    }
}
