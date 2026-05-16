package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.core.withRestore
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorTRANSPARENT
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkShaders
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's
 * [`gm/localmatrixshader.cpp::DEF_SIMPLE_GM(localmatrixshader_nested, …, 450, 1200)`](https://github.com/google/skia/blob/main/gm/localmatrixshader.cpp).
 *
 * Validates that nested `makeWithLocalMatrix` calls compose correctly
 * across **four** factory variants — image-shader-with-inner /
 * double-wrapped image / image-inside-blend / image-inside-blend-of-
 * inner-wrapped — each rendered in three columns (no extra CTM,
 * shifted-down repeat, post-`canvas.scale(2,2)`). The `outer = Scale(2)`
 * × `inner = Translate(20)` composition lands the image at the same
 * device-space position regardless of which variant builds the shader,
 * which is the property the test asserts visually.
 *
 * The 4 factories exercise four distinct shader topologies that must
 * all produce the same composed `outer · inner` localMatrix when
 * traversed by R-final.2's
 * [org.skia.foundation.SkShader.makeWithLocalMatrix] folding logic.
 *
 * **Image generation** — mirrors upstream's `make_image()` :
 *  - 50×50 N32 surface filled with a green anti-aliased circle,
 *  - red 1-pixel cross drawn through the centre.
 *
 * C++ original (full source in `gm/localmatrixshader.cpp`).
 */
public class LocalMatrixShaderGM : GM() {

    override fun getName(): String = "localmatrixshader_nested"
    override fun getISize(): SkISize = SkISize.Make(450, 1200)

    private fun makeImage(): SkImage {
        val info = SkImageInfo.MakeN32Premul(kSize.toInt(), kSize.toInt())
        val surface = SkSurface.MakeRaster(info)
        val canvas = surface.canvas
        // Wipe to fully transparent — upstream's `ToolUtils::makeSurface`
        // doesn't pre-clear so the snapshot picks up whatever the
        // surface allocator leaves; our SkBitmap default is already
        // 0-init but we make the intent explicit.
        canvas.clear(SK_ColorTRANSPARENT)

        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorGREEN
        }
        val half = kSize / 2f
        canvas.drawCircle(half, half, half, paint)

        paint.style = SkPaint.Style.kStroke_Style
        paint.color = SK_ColorRED
        canvas.drawLine(kSize * .25f, half, kSize * .75f, half, paint)
        canvas.drawLine(half, kSize * .25f, half, kSize * .75f, paint)
        return surface.makeImageSnapshot()
    }

    /**
     * Mirrors upstream's `gFactories[]` — four shader topologies that
     * must all compose to the same effective `outer · inner` local
     * matrix once R-final.2's folding kicks in.
     */
    private fun shaderFactories(image: SkImage): Array<(SkMatrix, SkMatrix) -> SkShader> = arrayOf(
        // (1) SkLocalMatrixShader(SkImageShader(inner), outer)
        { inner, outer ->
            image.makeShader(
                tileX = SkTileMode.kClamp, tileY = SkTileMode.kClamp,
                sampling = SkSamplingOptions.Default,
                localMatrix = inner,
            ).makeWithLocalMatrix(outer)
        },
        // (2) SkLocalMatrixShader(SkLocalMatrixShader(SkImageShader(I), inner), outer)
        { inner, outer ->
            image.makeShader(
                tileX = SkTileMode.kClamp, tileY = SkTileMode.kClamp,
                sampling = SkSamplingOptions.Default,
            ).makeWithLocalMatrix(inner).makeWithLocalMatrix(outer)
        },
        // (3) SkLocalMatrixShader(SkComposeShader(SkImageShader(inner)), outer)
        { inner, outer ->
            SkShaders.Blend(
                SkBlendMode.kSrcOver,
                SkShaders.Color(SK_ColorTRANSPARENT),
                image.makeShader(
                    tileX = SkTileMode.kClamp, tileY = SkTileMode.kClamp,
                    sampling = SkSamplingOptions.Default,
                    localMatrix = inner,
                ),
            ).makeWithLocalMatrix(outer)
        },
        // (4) SkLocalMatrixShader(SkComposeShader(SkLocalMatrixShader(SkImageShader(I), inner)), outer)
        { inner, outer ->
            SkShaders.Blend(
                SkBlendMode.kSrcOver,
                SkShaders.Color(SK_ColorTRANSPARENT),
                image.makeShader(
                    tileX = SkTileMode.kClamp, tileY = SkTileMode.kClamp,
                    sampling = SkSamplingOptions.Default,
                ).makeWithLocalMatrix(inner),
            ).makeWithLocalMatrix(outer)
        },
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = makeImage()
        val factories = shaderFactories(image)

        val outer = SkMatrix.MakeScale(2f, 2f)
        val inner = SkMatrix.MakeTrans(20f, 20f)

        val border = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
        }

        // Compose outer · inner once and apply to the image bounds —
        // the four shaders should all paint pixels inside this rect.
        val rect: SkRect = run {
            val baseRect = SkRect.MakeWH(image.width.toFloat(), image.height.toFloat())
            outer.preConcat(inner).mapRect(baseRect)
        }

        val drawColumn = {
            c.withRestore {
                for (factory in factories) {
                    val p = SkPaint().apply { shader = factory(inner, outer) }
                    drawRect(rect, p)
                    drawRect(rect, border)
                    translate(0f, rect.height() * 1.5f)
                }
            }
        }

        // Column 1 — straight render.
        drawColumn()

        // Column 2 — same rendering, shifted down by 4 rows × column
        // height. Demonstrates that the shader composes identically
        // when the canvas CTM has an extra translate vs. when not.
        c.withRestore {
            translate(0f, rect.height() * factories.size * 1.5f)
            drawColumn()
        }

        // Column 3 — translate right by `rect.width * 1.5` and apply
        // an additional `canvas.scale(2, 2)`. The inner / outer
        // matrices fold together with the CTM scale, so the result
        // appears at 2× size to the right.
        c.translate(rect.width() * 1.5f, 0f)
        c.scale(2f, 2f)
        drawColumn()
    }

    private companion object {
        const val kSize: Float = 50f
    }
}
