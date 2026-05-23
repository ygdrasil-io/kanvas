package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorTRANSPARENT
import org.skia.core.SkSurface
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/localmatrixshader.cpp::DEF_SIMPLE_GM(localmatrixshader_persp, …, 542, 266)`](https://github.com/google/skia/blob/main/gm/localmatrixshader.cpp).
 *
 * Exercises local-matrix composition under perspective transforms.
 * A 128×128 downscale of `yellow_rose.png` is drawn four ways (two rows —
 * image shader and radial gradient), each varying which matrix is supplied to
 * `makeShader` / `makeWithLocalMatrix` vs. applied to the canvas CTM:
 *
 * 1. scale in makeShader, persp in CTM
 * 2. scale in makeShader, then `makeWithLocalMatrix(persp)` (post-concat)
 * 3. pre-computed `persp * scale` in makeShader
 * 4. pre-computed `persp * scale` in `makeWithLocalMatrix`
 *
 * **Downscaling** — upstream calls `SkImage::scalePixels(downsized.pixmap(), …)`,
 * which is not yet in kanvas-skia. We reproduce the same effect by drawing
 * the full-size image into a 128×128 [SkSurface] with bilinear sampling.
 *
 * **Radial gradient** — upstream uses the new-style `SkShaders::RadialGradient`
 * factory that accepts an `SkGradient` struct (not yet in kanvas-skia). We
 * fall back to [SkRadialGradient.Make] with an equivalent `IntArray` colour
 * list `[kBlack, kTransparent]` and `kRepeat` tile mode, which is functionally
 * identical.
 *
 * C++ original (full source in `gm/localmatrixshader.cpp`).
 */
public class LocalMatrixShaderPerspGM : GM() {

    override fun getName(): String = "localmatrixshader_persp"
    override fun getISize(): SkISize = SkISize.Make(542, 266)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // ── Load + downscale image ─────────────────────────────────────
        val fullImage = ToolUtils.GetResourceAsImage("images/yellow_rose.png") ?: return
        // Replicate upstream's `image->scalePixels(downsized.pixmap(), kLinear)`
        // by drawing the full image into a 128×128 surface.
        val smallInfo = fullImage.imageInfo().makeWH(kSmall, kSmall)
        val smallSurface = SkSurface.MakeRaster(smallInfo)
        val src = SkRect.MakeIWH(fullImage.width, fullImage.height)
        val dst = SkRect.MakeIWH(kSmall, kSmall)
        smallSurface.canvas.drawImageRect(
            fullImage, src, dst,
            SkSamplingOptions.linear(),
        )
        val image = smallSurface.makeImageSnapshot()
        val imgRect = SkRect.MakeIWH(image.width, image.height)

        // ── Matrices ───────────────────────────────────────────────────
        // scale matrix: 1/5 × 1/5
        val scale = SkMatrix.MakeScale(1f / 5f, 1f / 5f)

        // perspective matrix: maps the 4 corners of imgRect to a trapezoid
        val srcQuad = arrayOf(
            SkPoint(imgRect.left,  imgRect.top),
            SkPoint(imgRect.right, imgRect.top),
            SkPoint(imgRect.right, imgRect.bottom),
            SkPoint(imgRect.left,  imgRect.bottom),
        )
        val dstQuad = arrayOf(
            SkPoint(0f,               10f),
            SkPoint(image.width + 28f, -100f),
            SkPoint(image.width - 28f,  image.height + 100f),
            SkPoint(0f,               image.height - 10f),
        )
        val persp = SkMatrix.MakePolyToPoly(srcQuad, dstQuad) ?: return

        // pre-computed persp * scale
        val perspScale = SkMatrix.concat(persp, scale)

        // ── Row 1 : SkImageShader ──────────────────────────────────────
        c.withSave {
            // 1. scale in makeShader, drawn with persp CTM
            val s1 = image.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat,
                SkSamplingOptions.Default, scale)
            draw(this, imgRect, s1, applyPerspToCTM = true, perp = persp, advance = kAdvance)

            // 2. scale in makeShader + makeWithLocalMatrix(persp) post-concat → persp * scale
            val s2 = image.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat,
                SkSamplingOptions.Default, scale)
                .makeWithLocalMatrix(persp)
            draw(this, imgRect, s2, applyPerspToCTM = false, perp = persp, advance = kAdvance)

            // 3. pre-computed persp*scale in makeShader
            val s3 = image.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat,
                SkSamplingOptions.Default, perspScale)
            draw(this, imgRect, s3, applyPerspToCTM = false, perp = persp, advance = kAdvance)

            // 4. pre-computed persp*scale in makeWithLocalMatrix
            val s4 = image.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat,
                SkSamplingOptions.Default)
                .makeWithLocalMatrix(perspScale)
            draw(this, imgRect, s4, applyPerspToCTM = false, perp = persp, advance = kAdvance)
        }

        c.translate(0f, 10f + image.height)

        // ── Row 2 : Radial gradient ────────────────────────────────────
        // Upstream uses new-style SkShaders::RadialGradient(center, radius, grad, &localMatrix).
        // Equivalent via SkRadialGradient.Make with IntArray colors.
        val gradColors = intArrayOf(SK_ColorBLACK, SK_ColorTRANSPARENT)
        val center = imgRect.center()
        val radius = imgRect.width() / 2f

        c.withSave {
            // 1. scale in Make, drawn with persp CTM
            val g1 = SkRadialGradient.Make(center, radius, gradColors, null,
                SkTileMode.kRepeat, scale)
            draw(this, imgRect, g1, applyPerspToCTM = true, perp = persp, advance = kAdvance)

            // 2. scale in Make + makeWithLocalMatrix(persp) post-concat
            val g2 = g1.makeWithLocalMatrix(persp)
            draw(this, imgRect, g2, applyPerspToCTM = false, perp = persp, advance = kAdvance)

            // 3. pre-computed persp*scale in Make
            val g3 = SkRadialGradient.Make(center, radius, gradColors, null,
                SkTileMode.kRepeat, perspScale)
            draw(this, imgRect, g3, applyPerspToCTM = false, perp = persp, advance = kAdvance)

            // 4. pre-computed persp*scale in makeWithLocalMatrix
            val g4 = SkRadialGradient.Make(center, radius, gradColors, null,
                SkTileMode.kRepeat)
                .makeWithLocalMatrix(perspScale)
            draw(this, imgRect, g4, applyPerspToCTM = false, perp = persp, advance = kAdvance)
        }
    }

    /**
     * Mirrors upstream's local `draw` lambda — clips to `imgRect`, optionally
     * concatenates the perspective matrix to the CTM, fills with [shader],
     * then advances horizontally by [advance].
     */
    private fun draw(
        canvas: SkCanvas,
        imgRect: SkRect,
        shader: SkShader,
        applyPerspToCTM: Boolean,
        perp: SkMatrix,
        advance: Float,
    ) {
        canvas.withSave {
            clipRect(imgRect)
            if (applyPerspToCTM) {
                concat(perp)
            }
            val paint = SkPaint().apply { this.shader = shader }
            drawPaint(paint)
        }
        canvas.translate(advance + imgRect.width(), 0f)
    }

    private companion object {
        /** Target size for the downscaled image (matches upstream's 128). */
        const val kSmall: Int = 128

        /** Horizontal gap between each variant cell. */
        const val kAdvance: Float = 10f
    }
}
