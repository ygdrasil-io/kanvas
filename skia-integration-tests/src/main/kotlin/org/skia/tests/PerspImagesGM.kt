package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkCubicResampler
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/perspimages.cpp::PerspImages`
 * (registered as `persp_images`, 1150 × 1280).
 *
 * Exercises `drawImage` / `drawImageRect` (Strict and Fast constraints)
 * under two perspective [SkMatrix] transforms, with every combination of:
 *  - 3 draw types: `drawImage`, `drawImageRect kStrict`, `drawImageRect kFast`
 *  - 2 perspective matrices (see below)
 *  - 2 anti-alias settings: `false`, `true`
 *  - 4 sampling modes: nearest, linear, linear+mip-linear, Mitchell cubic
 *  - 2 images: mandrill_128.png and a 128×128 crop of brickwork-texture.jpg
 *
 * That is 3 × 2 × 2 × 4 × 2 = 96 cells laid out 8 per row.
 *
 * Upstream calls `ToolUtils::MakeTextureImage(canvas, origImage)` to upload
 * each image to a GPU texture before drawing. In `:kanvas-skia` (raster-only),
 * [ToolUtils.MakeTextureImage] is the identity — the raster image is used
 * directly. The `++n` counter and grid advance execute unconditionally even
 * when the image is `null` (matching upstream's layout logic).
 *
 * Matrix construction mirrors upstream's `SkTDArray<SkMatrix>`:
 *
 * ```cpp
 * matrices.append()->setAll(1, 0, 0,   0, 1, 0,   0, 0.005, 1);
 * matrices.append()->setAll(1, 0, 0,   0, 1, 0,   0.007, -0.005, 1);
 * matrices[1].preSkew(0.2, -0.1);
 * matrices[1].preRotate(-65);
 * matrices[1].preScale(1.2, 0.8);
 * matrices[1].postTranslate(0, 60);
 * ```
 *
 * In Kotlin's immutable [SkMatrix] each mutation produces a new value; we
 * chain them via successive `val` / `var` reassignment.
 *
 * The src rect for `drawImageRect` is the centre 50% of the image:
 * `{w/4, h/4, 3w/4, 3h/4}`, and the dst rect is `{0, 0, 3w/4, 3h/4}`.
 *
 * C++ original: `gm/perspimages.cpp` (Skia).
 *
 * **Classification: INTRACTABLE.GPU_ONLY** — the reference PNG
 * (`original-888/persp_images.png`) was captured on a GPU (Ganesh) backend.
 * On the raster path `MakeTextureImage` is a no-op so all draws execute, but
 * GPU-path perspective sampling produces visual output that differs structurally
 * from the raster pipeline. The test is `@Disabled("INTRACTABLE.GPU_ONLY: …")`;
 * the GM body is complete so a future GPU backend can activate it without changes.
 */
public class PerspImagesGM : GM() {

    override fun getName(): String = "persp_images"
    override fun getISize(): SkISize = SkISize.Make(1150, 1280)

    private val fImages: MutableList<SkImage> = mutableListOf()

    override fun onOnceBeforeDraw() {
        ToolUtils.GetResourceAsImage("images/mandrill_128.png")
            ?.let { fImages.add(it) }
        ToolUtils.GetResourceAsImage("images/brickwork-texture.jpg")
            ?.makeSubset(SkIRect.MakeLTRB(0, 0, 128, 128))
            ?.let { fImages.add(it) }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        if (fImages.isEmpty()) return

        // ── Build perspective matrices (mirrors upstream SkTDArray<SkMatrix>) ──

        // Matrix 0: pure vertical perspective (persp1 = 0.005)
        // C++: setAll(1, 0, 0,  0, 1, 0,  0, 0.005, 1)
        val m0 = SkMatrix.MakeFrom9(
            floatArrayOf(1f, 0f, 0f,  0f, 1f, 0f,  0f, 0.005f, 1f),
        )

        // Matrix 1: combined perspective + affine transforms
        // C++: setAll(1, 0, 0,  0, 1, 0,  0.007, -0.005, 1)
        //      .preSkew(0.2, -0.1)
        //      .preRotate(-65)
        //      .preScale(1.2, 0.8)
        //      .postTranslate(0, 60)
        var m1 = SkMatrix.MakeFrom9(
            floatArrayOf(1f, 0f, 0f,  0f, 1f, 0f,  0.007f, -0.005f, 1f),
        )
        m1 = m1.preSkew(0.2f, -0.1f)
        m1 = m1.preRotate(-65f)
        m1 = m1.preScale(1.2f, 0.8f)
        m1 = m1.postTranslate(0f, 60f)

        val matrices = listOf(m0, m1)

        // ── Compute bounding box over all image × matrix combinations ──
        val bounds = SkRect.MakeEmpty()
        for (img in fImages) {
            val imgB = SkRect.MakeWH(img.width.toFloat(), img.height.toFloat())
            for (m in matrices) {
                val temp = m.mapRect(imgB)
                bounds.join(temp)
            }
        }

        // ── Canvas setup: shift origin so nothing is clipped ──
        c.translate(-bounds.left + 10f, -bounds.top + 10f)
        c.save()

        val paint = SkPaint()
        var n = 0

        val samplings = listOf(
            SkSamplingOptions(SkFilterMode.kNearest),
            SkSamplingOptions(SkFilterMode.kLinear),
            SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear),
            SkSamplingOptions(SkCubicResampler.Mitchell),
        )

        // ── Main draw loop (mirrors upstream nested for-loops) ──
        // 3 types × 2 matrices × 2 aa × 4 samplings × 2 images = 96 cells
        for (type in DrawType.values()) {
            for (m in matrices) {
                for (aa in listOf(false, true)) {
                    paint.isAntiAlias = aa
                    for (sampling in samplings) {
                        for (origImage in fImages) {
                            // MakeTextureImage is identity on raster;
                            // mirrors upstream GPU upload with null-guard.
                            val img = ToolUtils.MakeTextureImage(c, origImage)
                            if (img != null) {
                                c.save()
                                c.concat(m)

                                val w = img.width.toFloat()
                                val h = img.height.toFloat()
                                val src = SkRect.MakeLTRB(
                                    w / 4f, h / 4f, 3f * w / 4f, 3f * h / 4f,
                                )
                                val dst = SkRect.MakeLTRB(
                                    0f, 0f, 3f * w / 4f, 3f * h / 4f,
                                )

                                when (type) {
                                    DrawType.kDrawImage ->
                                        c.drawImage(img, 0f, 0f, sampling, paint)
                                    DrawType.kDrawImageRectStrict ->
                                        c.drawImageRect(
                                            img, src, dst, sampling, paint,
                                            SrcRectConstraint.kStrict,
                                        )
                                    DrawType.kDrawImageRectFast ->
                                        c.drawImageRect(
                                            img, src, dst, sampling, paint,
                                            SrcRectConstraint.kFast,
                                        )
                                }

                                c.restore()
                            }

                            // Grid advance — unconditional (mirrors C++ ++n then if/else)
                            ++n
                            if (n < 8) {
                                c.translate(bounds.width() + 10f, 0f)
                            } else {
                                c.restore()
                                c.translate(0f, bounds.height() + 10f)
                                c.save()
                                n = 0
                            }
                        }
                    }
                }
            }
        }

        c.restore()
    }

    private enum class DrawType {
        kDrawImage,
        kDrawImageRectStrict,
        kDrawImageRectFast,
    }
}
