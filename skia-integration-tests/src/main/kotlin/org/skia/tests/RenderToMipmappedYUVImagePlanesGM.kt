package org.skia.tests

import org.graphiks.math.SkColorMatrix
import org.graphiks.math.SkISize
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkColorTypeChannelFlags
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkImages
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkYUVAInfo
import org.skia.tools.ToolUtils
import kotlin.math.ceil

/**
 * Port of Skia's `gm/rendertomipmappedyuvimageplanes.cpp`
 * (`RenderToMipmappedYUVImagePlanes`, registered as
 * `render_to_mipmapped_yuv_image_planes`, 96 × 32).
 *
 * The upstream GM is **Graphite-only** — it relies on:
 *  1. `SkSurfaces::RenderTarget(recorder, info, Mipmapped::kYes)` to
 *     create a GPU-backed mipmapped surface for each YUV plane ;
 *  2. `SkImages::TextureFromYUVAImages(recorder, yuvaInfo, planes, colorSpace)`
 *     to assemble the per-plane GPU textures into a single YUVA image ;
 *  3. `SkColorMatrix::RGBtoYUV(yuvColorSpace)` to obtain the per-color-space
 *     RGB→YUV conversion matrix ;
 *  4. `SkYUVAInfo::toYUVALocations(channelFlags)` to map YUVA channels to
 *     plane indices and channel selectors.
 *
 * None of these GPU-Graphite APIs exist in the `:kanvas-skia` raster backend.
 * The body wires every upstream call site so that the TODO tags surface the
 * exact gaps, and the matching [RenderToMipmappedYUVImagePlanesTest] is
 * `@Disabled("STUB.YUVA_PIXMAPS")`.
 *
 * ## Bucket : INTRACTABLE (Graphite GPU-only)
 *
 * The three test cases cover PlaneConfig × Subsampling variants:
 *  - `kY_U_V` × `k420` (3-plane, 4:2:0)
 *  - `kY_UV`  × `k422` (2-plane, 4:2:2)
 *  - `kYUV`   × `k444` (1-plane, 4:4:4)
 *
 * Each renders the `mandrill_512.png` source into mipmapped per-plane GPU
 * surfaces via a per-plane RGB→YUV colour-filter, then assembles the planes
 * into a YUVA GPU image and draws it downscaled (÷ 16) onto the canvas.
 *
 * Upstream cpp: `gm/rendertomipmappedyuvimageplanes.cpp`.
 */
public class RenderToMipmappedYUVImagePlanesGM : GM() {

    private var fSrcImage: SkImage? = null

    override fun getName(): String = "render_to_mipmapped_yuv_image_planes"

    override fun getISize(): SkISize = SkISize.Make(96, 32)

    override fun onOnceBeforeDraw() {
        fSrcImage = ToolUtils.GetResourceAsImage("images/mandrill_512.png")
    }

    /**
     * Mirrors upstream `onDraw`. All three test cases throw
     * `TODO("STUB.YUVA_PIXMAPS")` at the first missing API call
     * (`SkColorMatrix.RGBtoYUV`) before any surface or image allocation
     * takes place. The surrounding structure is preserved verbatim so
     * that a future implementation only needs to replace the TODO stubs
     * with real calls.
     */
    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val srcImage = fSrcImage ?: return

        data class TestCase(
            val config: SkYUVAInfo.PlaneConfig,
            val subsampling: SkYUVAInfo.Subsampling,
        )

        for (tc in listOf(
            TestCase(SkYUVAInfo.PlaneConfig.kY_U_V, SkYUVAInfo.Subsampling.k420),
            TestCase(SkYUVAInfo.PlaneConfig.kY_UV,  SkYUVAInfo.Subsampling.k422),
            TestCase(SkYUVAInfo.PlaneConfig.kYUV,   SkYUVAInfo.Subsampling.k444),
        )) {
            val yuvaInfo = SkYUVAInfo(
                dimensions = SkISize.Make(srcImage.width, srcImage.height),
                planeConfig = tc.config,
                subsampling = tc.subsampling,
                yuvColorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
            )

            // TODO: STUB.YUVA_PIXMAPS — SkColorMatrix.RGBtoYUV(yuvColorSpace)
            // returns the per-color-space RGB→YUV conversion matrix.
            val rgbToYuv: FloatArray = run {
                val mat = SkColorMatrix.RGBtoYUV(yuvaInfo.yuvColorSpace)
                FloatArray(20).also { mat.getRowMajor(it) }
            }

            val numPlanes = yuvaInfo.numPlanes()
            val planes = arrayOfNulls<SkImage>(SkYUVAInfo.kMaxPlanes)

            val channelFlags = IntArray(SkYUVAInfo.kMaxPlanes)
            for (i in 0 until numPlanes) {
                val colorType = when (yuvaInfo.numChannelsInPlane(i)) {
                    1 -> org.skia.foundation.SkColorType.kAlpha_8
                    2 -> org.skia.foundation.SkColorType.kR8G8_unorm
                    3 -> org.skia.foundation.SkColorType.kRGB_888x
                    4 -> org.skia.foundation.SkColorType.kRGBA_8888
                    else -> continue
                }
                // TODO: STUB.YUVA_PIXMAPS — SkColorTypeChannelFlags(colorType)
                // returns the channel-flag bitmask for YUVA plane assembly.
                channelFlags[i] = SkColorTypeChannelFlags(colorType)
            }

            // TODO: STUB.YUVA_PIXMAPS — SkYUVAInfo.toYUVALocations(channelFlags)
            // maps YUVA channels to plane + channel-within-plane indices.
            val locations: SkYUVAInfo.YUVALocations = yuvaInfo.toYUVALocations(channelFlags)

            for (i in 0 until numPlanes) {
                val colorType = when (yuvaInfo.numChannelsInPlane(i)) {
                    1 -> org.skia.foundation.SkColorType.kAlpha_8
                    2 -> org.skia.foundation.SkColorType.kR8G8_unorm
                    3 -> org.skia.foundation.SkColorType.kRGB_888x
                    4 -> org.skia.foundation.SkColorType.kRGBA_8888
                    else -> continue
                }
                val planeDims: SkISize = yuvaInfo.planeDimensions(i)
                val info = SkImageInfo.Make(planeDims.width, planeDims.height, colorType, org.skia.foundation.SkAlphaType.kPremul)

                // GPU: SkSurfaces::RenderTarget(recorder, info, Mipmapped::kYes)
                // No raster equivalent — would need STUB.GPU_RENDER_TARGET.
                // We continue the loop to wire all downstream call sites.

                // Build the per-plane RGB→YUV colour matrix (20-float row-major).
                val matrix = FloatArray(20) { idx ->
                    when {
                        idx % 5 == (idx / 5) -> 1f  // identity diagonal
                        else -> 0f
                    }
                }
                for (ch in 0 until SkYUVAInfo.kYUVAChannelCount) {
                    if (locations[ch].fPlane == i) {
                        val d = locations[ch].fChannel.ordinal
                        for (k in 0 until 5) {
                            matrix[d * 5 + k] = rgbToYuv[ch * 5 + k]
                        }
                    }
                }

                val paint = SkPaint()
                paint.colorFilter = SkColorFilters.Matrix(matrix)

                // surf.canvas.drawImageRect(srcImage, …) — would draw via GPU surface.
                // surf.makeImageSnapshot() → planes[i]
                // (both require GPU recorder — no raster equivalent)
            }

            // TODO: STUB.YUVA_PIXMAPS — SkImages.TextureFromYUVAImages(recorder,
            // yuvaInfo, planes, imageColorSpace=null) assembles a Graphite YUVA
            // GPU image from the per-plane textures.
            val yuvaImage: SkImage? = SkImages.TextureFromYUVAImages(
                recorder = null,
                yuvaInfo = yuvaInfo,
                planes = planes,
                imageColorSpace = null,
            )
            if (yuvaImage != null) {
                val dstRect = org.graphiks.math.SkRect.MakeWH(
                    yuvaImage.width / 16f,
                    yuvaImage.height / 16f,
                )
                c.drawImageRect(
                    yuvaImage,
                    org.graphiks.math.SkRect.MakeWH(yuvaImage.width.toFloat(), yuvaImage.height.toFloat()),
                    dstRect,
                    SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear),
                    null,
                )
            }
            c.translate(ceil(yuvaInfo.width() / 16f), 0f)
        }
    }
}
