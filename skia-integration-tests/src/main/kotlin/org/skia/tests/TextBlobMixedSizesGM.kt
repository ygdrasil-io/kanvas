package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.core.withRestore
import org.skia.core.withSave
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBlurMask
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSurfaceProps
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTextEncoding
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/textblobmixedsizes.cpp::TextBlobMixedSizes`](https://github.com/google/skia/blob/main/gm/textblobmixedsizes.cpp).
 *
 * Tests that text blobs of mixed sizes with a large glyph render properly.
 * Builds a single [SkTextBlob] with six runs at sizes 262 → 162 → 72 → 32 → 14 → 0,
 * then draws it four times with random rotations and (when `useDFT = false`) a
 * blurred shadow pass.
 *
 * The upstream C++ registers two GM variants :
 *  - `TextBlobMixedSizes(false)` → name `textblobmixedsizes` (2100 × 1900)
 *  - `TextBlobMixedSizes(true)`  → name `textblobmixedsizes_df` (2100 × 1900)
 *
 * The `useDFT = true` variant creates an offscreen surface with
 * [SkSurfaceProps.kUseDeviceIndependentFonts_Flag] — the same pattern as
 * `gm/dftext.cpp` (see [DFTextGM]).  On `:kanvas-skia` raster this flag
 * round-trips through [SkSurfaceProps] without behavioural effect (the
 * distance-field-text shader lives in the GPU backend only), so the `_df`
 * output differs from the upstream GPU reference. The matching test
 * ([TextBlobMixedSizesDfTest]) is therefore `@Disabled` with a
 * `STUB.DF_TEXT_RASTER` reason.
 *
 * **Font resource** : upstream loads `fonts/HangingS.ttf` and falls back to
 * `ToolUtils::DefaultPortableTypeface()` when the resource is absent.
 * `:kanvas-skia` ships no `HangingS.ttf`, so [ToolUtils.CreateTypefaceFromResource]
 * returns `null` and we unconditionally fall back to [ToolUtils.DefaultPortableTypeface]
 * — matching upstream's fallback path exactly.
 */
public class TextBlobMixedSizesGM(private val useDFT: Boolean = false) : GM() {

    private companion object {
        const val K_WIDTH: Int = 2100
        const val K_HEIGHT: Int = 1900
    }

    private var fBlob: SkTextBlob? = null

    override fun getName(): String =
        if (useDFT) "textblobmixedsizes_df" else "textblobmixedsizes"

    override fun getISize(): SkISize = SkISize.Make(K_WIDTH, K_HEIGHT)

    override fun onOnceBeforeDraw() {
        val builder = SkTextBlobBuilder()

        // Load HangingS.ttf — expected to be absent in `:kanvas-skia`, falls
        // back to the portable typeface (mirrors upstream's null-check fallback).
        val tf = ToolUtils.CreateTypefaceFromResource("fonts/HangingS.ttf")
            ?: ToolUtils.DefaultPortableTypeface()

        val font = SkFont(tf, 262f).apply {
            isSubpixel = true
            edging = SkFont.Edging.kSubpixelAntiAlias
        }

        val text = "Skia"

        // 262 pt run — largest, anchored at origin.
        ToolUtils.addToTextBlob(builder, text, font, 0f, 0f)

        // 162 pt run — offset below the previous run's bounding height.
        var bounds = org.graphiks.math.SkRect.MakeWH(0f, 0f)
        font.measureText(text, text.length, SkTextEncoding.kUTF8, bounds)
        var yOffset = bounds.height()
        font.size = 162f
        ToolUtils.addToTextBlob(builder, text, font, 0f, yOffset)

        // 72 pt run.
        bounds = org.graphiks.math.SkRect.MakeWH(0f, 0f)
        font.measureText(text, text.length, SkTextEncoding.kUTF8, bounds)
        yOffset += bounds.height()
        font.size = 72f
        ToolUtils.addToTextBlob(builder, text, font, 0f, yOffset)

        // 32 pt run.
        bounds = org.graphiks.math.SkRect.MakeWH(0f, 0f)
        font.measureText(text, text.length, SkTextEncoding.kUTF8, bounds)
        yOffset += bounds.height()
        font.size = 32f
        ToolUtils.addToTextBlob(builder, text, font, 0f, yOffset)

        // 14 pt run — micro, will fall out of distance-field range even when
        // distance-field text is enabled (upstream comment).
        bounds = org.graphiks.math.SkRect.MakeWH(0f, 0f)
        font.measureText(text, text.length, SkTextEncoding.kUTF8, bounds)
        yOffset += bounds.height()
        font.size = 14f
        ToolUtils.addToTextBlob(builder, text, font, 0f, yOffset)

        // 0 pt run — zero size.
        bounds = org.graphiks.math.SkRect.MakeWH(0f, 0f)
        font.measureText(text, text.length, SkTextEncoding.kUTF8, bounds)
        yOffset += bounds.height()
        font.size = 0f
        ToolUtils.addToTextBlob(builder, text, font, 0f, yOffset)

        fBlob = builder.make()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val inputCanvas = canvas ?: return

        // For the DFT variant, upstream creates an offscreen surface with the
        // kUseDeviceIndependentFonts_Flag on top of the input canvas's props,
        // draws into that surface, then blits the snapshot back with a reset
        // matrix.  For raster `:kanvas-skia`, the flag has no behavioural
        // effect (no SDF shader in the raster pipeline), so we go through the
        // same code path for API round-trip correctness.
        val drawCanvas: SkCanvas
        val offscreen: SkSurface?

        if (useDFT) {
            val size = getISize()
            val info = SkImageInfo.MakeN32(
                size.width, size.height,
                SkAlphaType.kPremul,
            )
            val inputProps: SkSurfaceProps = inputCanvas.surfaceProps()
            val props = SkSurfaceProps(
                flags = SkSurfaceProps.kUseDeviceIndependentFonts_Flag or inputProps.flags,
                pixelGeometry = inputProps.pixelGeometry,
            )
            offscreen = SkSurface.MakeRaster(info, props)
            drawCanvas = offscreen.canvas
            // Carry the input canvas's CTM onto the offscreen canvas (upstream:
            // `canvas->setMatrix(inputCanvas->getTotalMatrix())`).
            val inputMatrix: SkMatrix = inputCanvas.getLocalToDeviceAsMatrix() ?: SkMatrix.Identity
            drawCanvas.setMatrix(inputMatrix)
        } else {
            offscreen = null
            drawCanvas = inputCanvas
        }

        drawCanvas.drawColor(SK_ColorWHITE)

        val blob = fBlob ?: return
        val blobBounds = blob.bounds()

        val kPadX = (blobBounds.width() / 3f).toInt()
        val kPadY = (blobBounds.height() / 3f).toInt()

        var rowCount = 0
        drawCanvas.translate(kPadX.toFloat(), kPadY.toFloat())
        drawCanvas.save()

        val random = SkRandom()

        // Base paint — white for the non-DFT variant (text over dark background
        // in upstream screenshots), no colour in the DFT variant (black on white).
        val paint = SkPaint().apply {
            if (!useDFT) color = SK_ColorWHITE
            isAntiAlias = false
        }

        val kSigma: Float = SkBlurMask.ConvertRadiusToSigma(8f)

        // Blur paint used for the shadow pass (non-DFT only).
        val blurPaint = paint.copy().apply {
            color = SK_ColorBLACK
            maskFilter = SkMaskFilter.MakeBlur(SkBlurStyle.kNormal, kSigma)
        }

        for (i in 0 until 4) {
            drawCanvas.withSave {
                when (i % 2) {
                    0 -> drawCanvas.rotate(random.nextF() * 45f)
                    1 -> drawCanvas.rotate(-random.nextF() * 45f)
                }
                if (!useDFT) {
                    drawCanvas.drawTextBlob(blob, 0f, 0f, blurPaint)
                }
                drawCanvas.drawTextBlob(blob, 0f, 0f, paint)
            }
            drawCanvas.translate(blobBounds.width() + kPadX.toFloat(), 0f)
            ++rowCount
            if ((blobBounds.width() + 2 * kPadX) * rowCount > K_WIDTH) {
                drawCanvas.restore()
                drawCanvas.translate(0f, blobBounds.height() + kPadY.toFloat())
                drawCanvas.save()
                rowCount = 0
            }
        }
        drawCanvas.restore()

        // Blit the offscreen surface back onto the input canvas (DFT path only).
        if (offscreen != null) {
            inputCanvas.withRestore {
                inputCanvas.resetMatrix()
                inputCanvas.drawImage(offscreen.makeImageSnapshot(), 0f, 0f)
            }
        }
    }
}
