package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.effects.SkHighContrastConfig
import org.skia.effects.SkHighContrastFilter
import org.skia.effects.SkShaderMaskFilter
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkFilterMode
import org.graphiks.math.SkIPoint
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/imagefilters.cpp::DEF_SIMPLE_GM(imagefilters_effect_order, …)`
 * (registered as `imagefilters_effect_order`, 512 × 512).
 *
 * Tests that color filters and mask filters are applied *before* the image
 * filter, even for sprite draws that could normally avoid an auto-saveLayer.
 *
 * Two rows:
 * - Top row: expected (colour filter then image filter, represented explicitly
 *   in the filter graph) vs. test (colour filter on paint + image filter on
 *   paint — should match).
 * - Bottom row: same with a shader mask filter.
 *
 * GPU texture upload (`SkImages::TextureFromImage`) is skipped — raster image
 * is used directly.
 */
public class ImageFiltersEffectOrderGM : GM() {

    override fun getName(): String = "imagefilters_effect_order"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val image = ToolUtils.GetResourceAsImage("images/mandrill_256.png") ?: return

        val kernelSize = SkISize.Make(3, 3)
        val kernelOffset = SkIPoint.Make(1, 1)
        // Laplacian edge detector
        val kernel = floatArrayOf(
            -1f, -1f, -1f,
            -1f,  8f, -1f,
            -1f, -1f, -1f,
        )
        val edgeDetector: SkImageFilter = SkImageFilters.MatrixConvolution(
            kernelSize, kernel, 1f, 0f, kernelOffset,
            SkTileMode.kClamp, false, null,
        )

        // High-contrast pre-processing (grayscale=false, noInvert, contrast=0.5)
        val edgeAmplify = SkHighContrastFilter.Make(
            SkHighContrastConfig(
                grayscale = false,
                invertStyle = SkHighContrastConfig.InvertStyle.kNoInvert,
                contrast = 0.5f,
            ),
        ) ?: return

        // --- Colour-filter section ---

        // Expected: compose edgeDetector(edgeAmplify(src)) explicitly in graph
        val expectedCFPaint = SkPaint().apply {
            imageFilter = SkImageFilters.Compose(
                edgeDetector,
                SkImageFilters.ColorFilter(edgeAmplify, null),
            )
        }

        // Test: colour filter on paint + image filter on paint (engine must order CF before IF)
        val testCFPaint = SkPaint().apply {
            colorFilter = edgeAmplify
            imageFilter = edgeDetector
        }

        val crop = SkRect.MakeWH(image.width.toFloat(), image.height.toFloat())

        c.save()
        c.clipRect(crop)
        c.drawImage(image, 0f, 0f, SkSamplingOptions.Default, expectedCFPaint)
        c.restore()

        c.save()
        c.translate(image.width.toFloat(), 0f)
        c.clipRect(crop)
        c.drawImage(image, 0f, 0f, SkSamplingOptions.Default, testCFPaint)
        c.restore()

        // --- Mask-filter section ---

        // Radial gradient that pokes a hole in the centre (black→transparent)
        val alphaMaskShader = SkRadialGradient.Make(
            center = SkPoint(128f, 128f),
            radius = 128f,
            colors = intArrayOf(SK_ColorBLACK, SK_ColorTRANSPARENT),
            positions = floatArrayOf(0.4f, 0.9f),
            tileMode = SkTileMode.kClamp,
        )
        val maskFilter = SkShaderMaskFilter.Make(alphaMaskShader)

        // edge-blend composites edge-detected source over original image
        val edgeBlend: SkImageFilter = SkImageFilters.Blend(
            SkBlendMode.kSrcOver,
            SkImageFilters.Image(image, SkSamplingOptions(SkFilterMode.kNearest)),
            edgeDetector,
        )

        // Test: mask filter on paint + image filter on paint
        val testMaskPaint = SkPaint().apply {
            this.maskFilter = maskFilter
            imageFilter = edgeBlend
        }

        // Expected: compose edgeBlend( SrcIn(alphaMaskShader)(src) ) explicitly
        val expectedMaskPaint = SkPaint().apply {
            imageFilter = SkImageFilters.Compose(
                edgeBlend,
                SkImageFilters.Blend(
                    SkBlendMode.kSrcIn,
                    SkImageFilters.Shader(alphaMaskShader),
                ),
            )
        }

        c.save()
        c.translate(0f, image.height.toFloat())
        c.clipRect(crop)
        c.drawImage(image, 0f, 0f, SkSamplingOptions.Default, expectedMaskPaint)
        c.restore()

        c.save()
        c.translate(image.width.toFloat(), image.height.toFloat())
        c.clipRect(crop)
        c.drawImage(image, 0f, 0f, SkSamplingOptions.Default, testMaskPaint)
        c.restore()
    }
}
