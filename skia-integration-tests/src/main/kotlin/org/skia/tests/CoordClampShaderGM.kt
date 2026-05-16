package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShaders
import org.skia.foundation.SkTileMode
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/coordclampshader.cpp::coordclampshader`
 * (`DEF_SIMPLE_GM`). Reference image: `coordclampshader.png` (1074 × 795).
 *
 * Lays out 4 + 4 = 8 mandrill renders that exercise [SkShaders.CoordClamp]
 * combined with several `makeWithLocalMatrix` permutations and four
 * sampler types (nearest / linear / mip / aniso).
 *
 * The mandrill source is the 256 × 256 PNG (the upstream comment about
 * the "bottom row of mostly black pixels" applies to the 512 cousin —
 * we still call `makeSubset({0, 0, w, h - 1})` to match upstream's
 * pre-warp pixel set bit-for-bit).
 */
public class CoordClampShaderGM : GM() {

    override fun getName(): String = "coordclampshader"

    override fun getISize(): SkISize = SkISize.Make(1074, 795)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        var image = ToolUtils.GetResourceAsImage("images/mandrill_256.png") ?: return
        // Drop the bottom row (mirrors upstream's mandrill_512 cleanup).
        image = image.makeSubset(SkIRect.MakeLTRB(0, 0, image.width, image.height - 1)) ?: image
        image = image.withDefaultMipmaps()

        val paint = SkPaint()
        var imageShader = image.makeShader(
            SkTileMode.kClamp, SkTileMode.kClamp,
            SkSamplingOptions(SkFilterMode.kLinear),
        )

        val drawRect = SkRect.MakeWH(image.width.toFloat(), image.height.toFloat())
        val rotate = SkMatrix.MakeRotate(45f, drawRect.centerX(), drawRect.centerY())
        val clampRect = drawRect.makeInset(20f, 40f)

        c.translate(10f, 10f)

        // Top-left: plain CoordClamp(imageShader)
        paint.shader = SkShaders.CoordClamp(imageShader, clampRect)
        c.drawRect(drawRect, paint)

        // Top-right: CoordClamp(image.localMatrix(rotate))
        c.save()
        c.translate(image.width.toFloat(), 0f)
        paint.shader = SkShaders.CoordClamp(imageShader.makeWithLocalMatrix(rotate), clampRect)
        c.drawRect(drawRect, paint)
        c.restore()

        // Bottom-left: CoordClamp(image).localMatrix(rotate)
        c.save()
        c.translate(0f, image.height.toFloat())
        paint.shader = SkShaders.CoordClamp(imageShader, clampRect).makeWithLocalMatrix(rotate)
        c.drawRect(drawRect, paint)
        c.restore()

        // Bottom-right: CoordClamp(image.localMatrix(rotate)).localMatrix(rotate)
        c.save()
        c.translate(image.width.toFloat(), image.height.toFloat())
        paint.shader = SkShaders
            .CoordClamp(imageShader.makeWithLocalMatrix(rotate), clampRect)
            .makeWithLocalMatrix(rotate)
        c.drawRect(drawRect, paint)
        c.restore()

        // Move down for the sampler-permutation row.
        c.translate(0f, 2f * image.height.toFloat() + 10f)

        val samplers = listOf(
            SkSamplingOptions(SkFilterMode.kNearest),
            SkSamplingOptions(SkFilterMode.kLinear),
            SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear),
            SkSamplingOptions.Aniso(16),
        )

        val scale03 = SkMatrix.MakeScale(0.3f, 1f)
        for (sampler in samplers) {
            imageShader = image.makeShader(
                SkTileMode.kMirror, SkTileMode.kMirror,
                sampler, scale03,
            )
            paint.shader = SkShaders.CoordClamp(imageShader, clampRect)
            c.drawRect(drawRect, paint)
            c.translate(image.width.toFloat() + 10f, 0f)
        }
    }
}
