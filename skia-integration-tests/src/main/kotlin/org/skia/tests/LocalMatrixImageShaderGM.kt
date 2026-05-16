package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkColor
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/localmatriximageshader.cpp::localmatriximageshader`
 * (`DEF_SIMPLE_GM(localmatriximageshader, …, 250, 250)`).
 *
 * Validates that a `SkImage`-backed shader wrapped in a local matrix
 * composes correctly when the wrapping `makeWithLocalMatrix(M)` and the
 * inner shader's own `makeShader(…, &innerM)` stack up.
 *
 * Left panels (drawn first) — outer translate applied to a rotated image:
 *  - `red` cell: red square (offscreen surface), rotated 45° via the
 *    inner shader, then the whole shader is post-shifted by
 *    `Translate(100, 0)` via `makeWithLocalMatrix`.
 *  - `blue` cell: blue square, inner shader carries a translate of
 *    `(100, 0)`, then the shader is post-rotated via
 *    `makeWithLocalMatrix(rotate45)`.
 *
 * Right panels (drawn after `canvas->translate(100, 0)`):
 *  - upstream's `isAImage(&matrix, mode)` round-trips the composed
 *    shader back into `image->makeShader(mode[0], mode[1], …, &matrix)`.
 *    Since the composed shader is already representable as
 *    `image · totalLocalMatrix`, we re-construct the shader directly
 *    with the same composed matrix — pixel-iso to the round-trip.
 *
 * **Adaptation for `:kanvas-skia`** : `SkShader::makeWithLocalMatrix`
 * and `SkShader::isAImage` are not implemented yet ; we materialise the
 * equivalent composed local matrix at construction time
 * (`outerLocal · innerLocal`) and pass it to [SkImage.makeShader]
 * directly. The composition is identical to Skia's
 * `(canvasCtm × outerLocal × innerLocal)^-1` pipeline because the
 * matrix multiplication is associative.
 *
 * C++ original:
 * ```cpp
 * static sk_sp<SkImage> make_image(SkCanvas* rootCanvas, SkColor color) {
 *     SkImageInfo info = SkImageInfo::MakeN32Premul(100, 100);
 *     auto        surface(ToolUtils::makeSurface(rootCanvas, info));
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     paint.setColor(color);
 *     surface->getCanvas()->drawIRect(SkIRect::MakeXYWH(25, 25, 50, 50), paint);
 *     return surface->makeImageSnapshot();
 * }
 *
 * DEF_SIMPLE_GM(localmatriximageshader, canvas, 250, 250) {
 *     sk_sp<SkImage> redImage = make_image(canvas, SK_ColorRED);
 *     SkMatrix translate = SkMatrix::Translate(100.0f, 0.0f);
 *     SkMatrix rotate;
 *     rotate.setRotate(45.0f);
 *     sk_sp<SkShader> redImageShader = redImage->makeShader(SkSamplingOptions(), &rotate);
 *     sk_sp<SkShader> redLocalMatrixShader = redImageShader->makeWithLocalMatrix(translate);
 *
 *     SkPaint paint;
 *     paint.setShader(redLocalMatrixShader);
 *     canvas->drawIRect(SkIRect::MakeWH(250, 250), paint);
 *
 *     sk_sp<SkImage> blueImage = make_image(canvas, SK_ColorBLUE);
 *     sk_sp<SkShader> blueImageShader = blueImage->makeShader(SkSamplingOptions(), &translate);
 *     sk_sp<SkShader> blueLocalMatrixShader = blueImageShader->makeWithLocalMatrix(rotate);
 *
 *     paint.setShader(blueLocalMatrixShader);
 *     canvas->drawIRect(SkIRect::MakeWH(250, 250), paint);
 *
 *     canvas->translate(100.0f, 0.0f);
 *
 *     SkTileMode mode[2];
 *     SkMatrix matrix;
 *     SkImage* image = redLocalMatrixShader->isAImage(&matrix, mode);
 *     paint.setShader(image->makeShader(mode[0], mode[1], SkSamplingOptions(), &matrix));
 *     canvas->drawIRect(SkIRect::MakeWH(250, 250), paint);
 *     image = blueLocalMatrixShader->isAImage(&matrix, mode);
 *     paint.setShader(image->makeShader(mode[0], mode[1], SkSamplingOptions(), &matrix));
 *     canvas->drawIRect(SkIRect::MakeWH(250, 250), paint);
 * }
 * ```
 */
public class LocalMatrixImageShaderGM : GM() {
    override fun getName(): String = "localmatriximageshader"
    override fun getISize(): SkISize = SkISize.Make(250, 250)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val redImage = makeImage(c, SK_ColorRED)
        val translate = SkMatrix.MakeTrans(100f, 0f)
        val rotate = SkMatrix.MakeRotate(45f)

        // Upstream: redImage.makeShader(rotate).makeWithLocalMatrix(translate).
        // Composition: total localMatrix = translate · rotate. Rotation
        // applied first (innermost in the local→device chain), then translate.
        val redComposed = translate.preConcat(rotate)
        val paint = SkPaint().apply {
            shader = redImage.makeShader(
                tileX = SkTileMode.kClamp, tileY = SkTileMode.kClamp,
                sampling = SkSamplingOptions.Default,
                localMatrix = redComposed,
            )
        }
        c.drawRect(SkRect.MakeWH(250f, 250f), paint)

        val blueImage = makeImage(c, SK_ColorBLUE)
        // Upstream: blueImage.makeShader(translate).makeWithLocalMatrix(rotate).
        // Composition: total localMatrix = rotate · translate. Translate
        // applied first, then rotate.
        val blueComposed = rotate.preConcat(translate)
        paint.shader = blueImage.makeShader(
            tileX = SkTileMode.kClamp, tileY = SkTileMode.kClamp,
            sampling = SkSamplingOptions.Default,
            localMatrix = blueComposed,
        )
        c.drawRect(SkRect.MakeWH(250f, 250f), paint)

        c.translate(100f, 0f)

        // Upstream uses `isAImage(&matrix, mode)` to unwrap the composed
        // shader back into (image, mode, composedMatrix). Since we
        // already know the composed matrix and mode, we re-construct
        // the shader directly — pixel-iso to the round-trip.
        paint.shader = redImage.makeShader(
            tileX = SkTileMode.kClamp, tileY = SkTileMode.kClamp,
            sampling = SkSamplingOptions.Default,
            localMatrix = redComposed,
        )
        c.drawRect(SkRect.MakeWH(250f, 250f), paint)

        paint.shader = blueImage.makeShader(
            tileX = SkTileMode.kClamp, tileY = SkTileMode.kClamp,
            sampling = SkSamplingOptions.Default,
            localMatrix = blueComposed,
        )
        c.drawRect(SkRect.MakeWH(250f, 250f), paint)
    }

    /**
     * Mirrors the upstream helper `make_image(rootCanvas, color)` — paints a
     * single anti-aliased 50×50 square centred in a 100×100 offscreen
     * surface and snapshots it. `:kanvas-skia` has no `ToolUtils::makeSurface`
     * yet ; we route through [SkSurface.MakeRaster] with an N32-premul info
     * which matches upstream's `SkImageInfo::MakeN32Premul(100, 100)`.
     */
    private fun makeImage(@Suppress("UNUSED_PARAMETER") root: SkCanvas, color: SkColor): SkImage {
        val info = SkImageInfo.MakeN32Premul(100, 100)
        val surface = SkSurface.MakeRaster(info)
        val paint = SkPaint().apply {
            isAntiAlias = true
            this.color = color
        }
        surface.canvas.drawRect(SkRect.MakeXYWH(25f, 25f, 50f, 50f), paint)
        return surface.makeImageSnapshot()
    }
}
