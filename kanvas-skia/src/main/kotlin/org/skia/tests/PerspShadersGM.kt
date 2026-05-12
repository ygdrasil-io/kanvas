package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorYELLOW
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColor
import org.skia.foundation.SkCubicResampler
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/perspshaders.cpp::PerspShadersGM` (registered as
 * `persp_shaders_aa` when `doAA = true` and `persp_shaders_bw` when `false`).
 *
 * C++ original :
 * ```cpp
 * class PerspShadersGM : public GM {
 *   PerspShadersGM(bool doAA) : fDoAA(doAA) { }
 *   SkString getName() const override { return SkStringPrintf("persp_shaders_%s", fDoAA ? "aa" : "bw"); }
 *   SkISize getISize() override { return SkISize::Make(kCellSize*kNumCols, kCellSize*kNumRows); }
 *   ...
 *   void drawRow(SkCanvas* canvas, const SkSamplingOptions& sampling) {
 *     // For each row of 6 cells, concat a perspective matrix and draw
 *     // (1) drawImageRect of a checkerboard bitmap image,
 *     // (2) drawImage of a similarly-sized image,
 *     // (3) drawRect with bitmap shader,
 *     // (4) drawPath with bitmap shader,
 *     // (5) drawRect with linear gradient #1,
 *     // (6) drawPath with linear gradient #2.
 *   }
 *   void onDraw(SkCanvas* canvas) override {
 *     // five rows of sampling : Nearest, Linear, Linear+MipNearest,
 *     // Cubic Mitchell, Aniso(16). Stride = kCellSize vertically.
 *   }
 * };
 * DEF_GM(return new PerspShadersGM(true);)
 * DEF_GM(return new PerspShadersGM(false);)
 * ```
 *
 * **Iso-fidelity caveats** :
 *  - `drawImageRect` / `drawImage` under a perspective matrix go through
 *    `SkCanvas.drawImageRect`'s axis-aligned fast path in `:kanvas-skia` ;
 *    when the CTM is not axis-aligned the draw is dropped (see SkCanvas
 *    KDoc). Cells 1 & 2 will therefore be empty under the perspective
 *    concat. The shader-fill cells (3 - 6) still draw because shader paths
 *    don't have that restriction. This is a known H1.5 gap — perspective
 *    image sampling deferred work.
 *  - `mandrill_128.png` is **not** used by this GM — the bitmap is a
 *    procedural blue/yellow checkerboard built via [createCheckerboardImage]
 *    (substitutes upstream's `ToolUtils::create_checkerboard_image`).
 *
 *  The default constructor builds the AA variant. Use [bw] for the
 *  non-AA registered name.
 */
public class PerspShadersGM public constructor(private val fDoAA: Boolean = true) : GM() {

    private lateinit var fBitmapImage: SkImage
    private lateinit var fLinearGrad1: SkShader
    private lateinit var fLinearGrad2: SkShader
    private val fPerspMatrix: SkMatrix = SkMatrix(persp1 = 1f / 50f)
    private val fPath = SkPathBuilder()
        .moveTo(0f, 0f)
        .lineTo(0f, kCellSize.toFloat())
        .lineTo(kCellSize / 2f, kCellSize / 2f)
        .lineTo(kCellSize.toFloat(), kCellSize.toFloat())
        .lineTo(kCellSize.toFloat(), 0f)
        .close()
        .detach()

    override fun getName(): String = if (fDoAA) "persp_shaders_aa" else "persp_shaders_bw"

    override fun getISize(): SkISize = SkISize.Make(kCellSize * kNumCols, kCellSize * kNumRows)

    override fun onOnceBeforeDraw() {
        fBitmapImage = createCheckerboardImage(kCellSize, kCellSize, SK_ColorBLUE, SK_ColorYELLOW, kCellSize / 10)

        val colors = intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorRED, SK_ColorGREEN, SK_ColorRED)
        val positions = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        fLinearGrad1 = SkLinearGradient.Make(
            SkPoint(0f, 0f),
            SkPoint(kCellSize.toFloat(), kCellSize.toFloat()),
            colors,
            positions,
            SkTileMode.kClamp,
        )
        fLinearGrad2 = SkLinearGradient.Make(
            SkPoint(0f, 0f),
            SkPoint(0f, kCellSize.toFloat()),
            colors,
            positions,
            SkTileMode.kClamp,
        )
    }

    private fun drawRow(canvas: SkCanvas, sampling: SkSamplingOptions) {
        val filterPaint = SkPaint().apply { isAntiAlias = fDoAA }

        val pathPaint = SkPaint().apply {
            shader = fBitmapImage.makeShader(SkTileMode.kClamp, SkTileMode.kClamp, sampling)
            isAntiAlias = fDoAA
        }
        val gradPaint1 = SkPaint().apply {
            shader = fLinearGrad1
            isAntiAlias = fDoAA
        }
        val gradPaint2 = SkPaint().apply {
            shader = fLinearGrad2
            isAntiAlias = fDoAA
        }

        val r = SkRect.MakeWH(kCellSize.toFloat(), kCellSize.toFloat())

        canvas.save()

        // Cell 1 — drawImageRect of the procedural checkerboard image.
        canvas.save()
        canvas.concat(fPerspMatrix)
        canvas.drawImageRect(fBitmapImage, r, r, sampling, filterPaint)
        canvas.restore()

        canvas.translate(kCellSize.toFloat(), 0f)
        // Cell 2 — drawImage of the same image.
        canvas.save()
        canvas.concat(fPerspMatrix)
        canvas.drawImage(fBitmapImage, 0f, 0f, sampling, filterPaint)
        canvas.restore()

        canvas.translate(kCellSize.toFloat(), 0f)
        // Cell 3 — drawRect with bitmap shader.
        canvas.save()
        canvas.concat(fPerspMatrix)
        canvas.drawRect(r, pathPaint)
        canvas.restore()

        canvas.translate(kCellSize.toFloat(), 0f)
        // Cell 4 — drawPath with bitmap shader.
        canvas.save()
        canvas.concat(fPerspMatrix)
        canvas.drawPath(fPath, pathPaint)
        canvas.restore()

        canvas.translate(kCellSize.toFloat(), 0f)
        // Cell 5 — drawRect with linear gradient #1.
        canvas.save()
        canvas.concat(fPerspMatrix)
        canvas.drawRect(r, gradPaint1)
        canvas.restore()

        canvas.translate(kCellSize.toFloat(), 0f)
        // Cell 6 — drawPath with linear gradient #2.
        canvas.save()
        canvas.concat(fPerspMatrix)
        canvas.drawPath(fPath, gradPaint2)
        canvas.restore()

        canvas.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        drawRow(c, SkSamplingOptions(SkFilterMode.kNearest))
        c.translate(0f, kCellSize.toFloat())
        drawRow(c, SkSamplingOptions(SkFilterMode.kLinear))
        c.translate(0f, kCellSize.toFloat())
        drawRow(c, SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kNearest))
        c.translate(0f, kCellSize.toFloat())
        drawRow(c, SkSamplingOptions(SkCubicResampler.Mitchell))
        c.translate(0f, kCellSize.toFloat())
        drawRow(c, SkSamplingOptions.Aniso(16))
        c.translate(0f, kCellSize.toFloat())
    }

    /**
     * Substitutes upstream's `ToolUtils::create_checkerboard_image(w, h, c1, c2, size)`
     * (`tools/ToolUtils.cpp`). Builds a `w × h` image filled with a
     * [size]-pixel checker alternating between [c1] and [c2] — same recipe
     * as `ArithmodeBlenderGM.makeChecker`.
     */
    private fun createCheckerboardImage(w: Int, h: Int, c1: SkColor, c2: SkColor, size: Int): SkImage {
        val bm = SkBitmap(w, h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val cellX = x / size
                val cellY = y / size
                val pickC1 = (cellX + cellY) % 2 == 0
                bm.setPixel(x, y, if (pickC1) c1 else c2)
            }
        }
        return bm.asImage()
    }

    public companion object {
        internal const val kCellSize: Int = 50
        internal const val kNumRows: Int = 5
        internal const val kNumCols: Int = 6

        /** Factory for the `persp_shaders_bw` registered name. */
        public fun bw(): PerspShadersGM = PerspShadersGM(fDoAA = false)
    }
}
