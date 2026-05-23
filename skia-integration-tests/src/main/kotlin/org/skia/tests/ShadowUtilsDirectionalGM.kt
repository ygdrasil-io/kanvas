package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint3
import org.graphiks.math.SkRect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.utils.SkShadowUtils
import kotlin.math.pow

/**
 * Port of Skia's `gm/shadowutils.cpp` :
 * `DEF_SIMPLE_GM(shadow_utils_directional, canvas, 256, 384)`.
 *
 * Exercises [SkShadowUtils.DrawShadow] with
 * [SkShadowUtils.kDirectionalLight_ShadowFlag]. A rounded-rectangle
 * occluder is rendered in four rows under four different transform
 * conditions — translation, rotation, scale, and perspective — with three
 * columns showing progressively stronger transform steps.
 *
 * Light position is a direction vector `(-45, -45, 77.94…)` (unit-length
 * approximation of a 60° elevation at NW azimuth). Ambient colour is 2 %
 * black, spot colour is 35 % black.
 *
 * C++ source : `gm/shadowutils.cpp::shadow_utils_directional`.
 * Reference : `shadow_utils_directional.png` (256 × 384).
 *
 * ```cpp
 * DEF_SIMPLE_GM(shadow_utils_directional, canvas, 256, 384) {
 *     static constexpr SkScalar kLightR = 1.f;
 *     static constexpr SkScalar kHeight = 12.f;
 *     SkPath rrect(SkPath::RRect(SkRect::MakeLTRB(-25,-25,25,25), 10, 10));
 *     SkPoint3 lightPos = { -45, -45, 77.9422863406f };
 *     ...
 * }
 * ```
 */
public class ShadowUtilsDirectionalGM : GM() {

    override fun getName(): String = "shadow_utils_directional"
    override fun getISize(): SkISize = SkISize.Make(256, 384)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val kLightR = 1f
        val kHeight = 12f

        val rrect = SkPath.RRect(SkRRect.MakeRectXY(SkRect.MakeLTRB(-25f, -25f, 25f, 25f), 10f, 10f))
        val lightPos = SkPoint3(-45f, -45f, 77.9422863406f)

        val ambientColor = SkColorSetARGB((0.02f * 255).toInt(), 0, 0, 0)
        val spotColor = SkColorSetARGB((0.35f * 255).toInt(), 0, 0, 0)

        val fillPaint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorWHITE
            style = SkPaint.Style.kFill_Style
        }

        // Row 1 — translation: 3 copies side-by-side at y=35.
        c.save()
        c.translate(35f, 35f)
        for (i in 0 until 3) {
            SkShadowUtils.DrawShadow(
                c, rrect, SkPoint3(0f, 0f, kHeight), lightPos, kLightR,
                ambientColor, spotColor, SkShadowUtils.kDirectionalLight_ShadowFlag,
            )
            c.drawPath(rrect, fillPaint)
            c.translate(80f, 0f)
        }
        c.restore()

        // Row 2 — rotation: each column rotated by 20°, 40°, 60° at y=105.
        for (i in 0 until 3) {
            c.save()
            c.translate(35f + 80f * i, 105f)
            c.rotate(20f * (i + 1))
            SkShadowUtils.DrawShadow(
                c, rrect, SkPoint3(0f, 0f, kHeight), lightPos, kLightR,
                ambientColor, spotColor, SkShadowUtils.kDirectionalLight_ShadowFlag,
            )
            c.drawPath(rrect, fillPaint)
            c.restore()
        }

        // Row 3 — scale: scaleFactor = 2^0, 2^-1, 2^-2 at y=185.
        for (i in 0 until 3) {
            c.save()
            val scaleFactor = 2.0.pow(-i.toDouble()).toFloat()
            c.translate(35f + 80f * i, 185f)
            c.scale(scaleFactor, scaleFactor)
            SkShadowUtils.DrawShadow(
                c, rrect, SkPoint3(0f, 0f, kHeight), lightPos, kLightR,
                ambientColor, spotColor, SkShadowUtils.kDirectionalLight_ShadowFlag,
            )
            c.drawPath(rrect, fillPaint)
            c.restore()
        }

        // Row 4 — perspective: mat[kMPersp1]=0.005, mat[kMPersp2]=1.005 at y=265.
        // kMPersp1 is the 8th element (persp1), kMPersp2 is the perspective scale
        // divisor (persp2) in row-major 3×3.
        for (i in 0 until 3) {
            c.save()
            val mat = SkMatrix.Identity.copy(persp1 = 0.005f, persp2 = 1.005f)
            c.translate(35f + 80f * i, 265f)
            c.concat(mat)
            SkShadowUtils.DrawShadow(
                c, rrect, SkPoint3(0f, 0f, kHeight), lightPos, kLightR,
                ambientColor, spotColor, SkShadowUtils.kDirectionalLight_ShadowFlag,
            )
            c.drawPath(rrect, fillPaint)
            c.restore()
        }
    }
}
