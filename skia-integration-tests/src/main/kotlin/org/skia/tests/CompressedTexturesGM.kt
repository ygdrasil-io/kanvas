package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SK_ColorYELLOW
import org.graphiks.math.SK_ScalarPI
import org.graphiks.math.SkColor
import org.graphiks.math.SkIPoint
import org.graphiks.math.SkIVector
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar
import org.graphiks.math.SkScalarCos
import org.graphiks.math.SkScalarSin
import org.graphiks.math.SkVector
import org.skia.core.SkCanvas
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkCompressedDataUtils
import org.skia.foundation.SkCubicResampler
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkImages
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTextureCompressionType
import kotlin.math.max
import kotlin.math.min

/**
 * Port of Skia's
 * [`gm/compressed_textures.cpp`](https://github.com/google/skia/blob/main/gm/compressed_textures.cpp)
 * — `compressed_textures`, `compressed_textures_npot`,
 * `compressed_textures_nmof`.
 *
 * Upstream draws a 2x2 cell grid : ETC2-RGB8 / BC1-RGB8 in the top row,
 * BC1-RGBA8 in the bottom-right. Each cell stamps the full mip pyramid
 * (level 0 + N downsamples) at native size, with a red 2-px outline
 * around every level when the image fell back to the raster (decompressed)
 * path — kanvas-skia is raster-only, so the outline is always stamped.
 *
 * Each compressed payload encodes a multicolour "gear" path against an
 * opaque-black (ETC2/BC1-RGB) or transparent-black (BC1-RGBA) backdrop,
 * with one mip level per power-of-two halving down to 1×1. The colour
 * cycles `{red, green, blue, cyan, magenta, yellow, white}` so a quick
 * visual scan tells the level apart.
 *
 * **Upstream API surface used (all flag-planted as `TODO()` in
 * `:kanvas-skia`).**
 *  - [SkImages.RasterFromCompressedTextureData] — wraps the compressed
 *    payload back into a decompressed raster [SkImage].
 *  - [SkCompressedDataUtils.SkCompressedDataSize] — payload byte count
 *    for a given dimension + format, including the mip pyramid.
 *  - [SkCompressedDataUtils.Etc1EncodeImage] — encode a 565 bitmap into
 *    an ETC1/ETC2 block grid.
 *  - [SkCompressedDataUtils.TwoColorBC1Compress] — encode a 2-colour
 *    8888 bitmap into a BC1 block grid (matches upstream's
 *    `sk_gpu_test::TwoColorBC1Compress`).
 *
 * **Ganesh-only paths skipped.** Upstream branches on `SK_GANESH` /
 * `SK_GRAPHITE` to upload the compressed payload as a real GPU texture
 * (`SkImages::TextureFromCompressedTextureData`, Graphite's
 * `ManagedGraphiteTexture`). `:kanvas-skia` is raster-only ; we always
 * take the
 * [`SkImages::RasterFromCompressedTextureData`](https://github.com/google/skia/blob/main/include/core/SkImage.h)
 * branch (and always stamp the red outline, mirroring the
 * `!isCompressed` arm of upstream's `drawCell`).
 *
 * **Three variants** (`Type`) mirror the three upstream `DEF_GM`
 * registrations :
 *  - [Type.kNormal] (`compressed_textures`) — 64×64, power-of-two,
 *    multiple-of-four base.
 *  - [Type.kNonPowerOfTwo] (`compressed_textures_npot`) — 20×60, top
 *    two mips degenerate to 1×3 then 1×1.
 *  - [Type.kNonMultipleOfFour] (`compressed_textures_nmof`) — 13×61,
 *    primes — exercises edge-padding in BC1 / ETC2 block grids.
 *
 * ## Port status
 *
 * Body fully ported against the freshly-introduced
 * [SkTextureCompressionType] / [SkImages.RasterFromCompressedTextureData]
 * / [SkCompressedDataUtils] surface. Every helper compiles ; the
 * compressed-payload allocation, mip iteration, gear rendering, and
 * draw-cell layout match upstream verbatim. Runtime fails at
 * `SkCompressedDataSize` (the first STUB call) — matching
 * `BC1TransparencyGM`. The [CompressedTexturesTest] is `@Disabled` until
 * the BC1 / ETC2 decode lands.
 */
public class CompressedTexturesGM(
    private val type: Type = Type.kNormal,
) : GM() {

    /**
     * Mirrors upstream's `CompressedTexturesGM::Type` — picks one of the
     * three registered variants. Each pushes a different
     * [fImgDimensions] into [make_compressed_image] to exercise
     * power-of-two / non-power-of-two / non-multiple-of-four edge cases
     * in the block grid.
     */
    public enum class Type {
        /** 64×64 base, fully aligned to the 4×4 block grid. */
        kNormal,

        /** 20×60 base — top two mips degenerate to 1×3 then 1×1. */
        kNonPowerOfTwo,

        /** 13×61 base (primes) — exercises edge-padding in BC1 / ETC2. */
        kNonMultipleOfFour,
    }

    private val imgDimensions: SkISize = when (type) {
        Type.kNonPowerOfTwo -> SkISize.Make(20, 60)
        Type.kNonMultipleOfFour -> SkISize.Make(13, 61)
        Type.kNormal -> SkISize.Make(kBaseTexWidth, kBaseTexHeight)
    }

    init {
        setBGColor(kBackground)
    }

    override fun getName(): String = when (type) {
        Type.kNormal -> "compressed_textures"
        Type.kNonPowerOfTwo -> "compressed_textures_npot"
        Type.kNonMultipleOfFour -> "compressed_textures_nmof"
    }

    override fun getISize(): SkISize =
        SkISize.Make(2 * kCellWidth + 3 * kPad, 2 * kBaseTexHeight + 3 * kPad)

    private var opaqueETC2Image: SkImage? = null
    private var opaqueBC1Image: SkImage? = null
    private var transparentBC1Image: SkImage? = null
    private var imagesBuilt = false

    /**
     * Mirrors upstream's `onGpuSetup` for the raster path — builds the
     * three compressed [SkImage]s on first draw. Upstream defers this
     * to a GPU-setup hook because the Ganesh / Graphite paths need a
     * recording context ; the raster fallback is context-free, so we
     * fold it into a lazy initializer the way other kanvas-skia GMs do
     * (see [BC1TransparencyGM.ensureImages]).
     */
    private fun ensureImages(canvas: SkCanvas) {
        if (imagesBuilt) return
        imagesBuilt = true

        opaqueETC2Image = try {
            make_compressed_image(
                canvas, imgDimensions, SkColorType.kRGB_565, opaque = true,
                SkTextureCompressionType.kETC2_RGB8_UNORM,
            )
        } catch (_: NotImplementedError) {
            // ETC2 stays dependency-gated in this slice; keep the BC1 cells live.
            null
        }
        opaqueBC1Image = make_compressed_image(
            canvas, imgDimensions, SkColorType.kRGBA_8888, opaque = true,
            SkTextureCompressionType.kBC1_RGB8_UNORM,
        )
        transparentBC1Image = make_compressed_image(
            canvas, imgDimensions, SkColorType.kRGBA_8888, opaque = false,
            SkTextureCompressionType.kBC1_RGBA8_UNORM,
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        ensureImages(c)

        drawCell(c, opaqueETC2Image, SkIPoint(kPad, kPad))
        drawCell(c, opaqueBC1Image, SkIPoint(2 * kPad + kCellWidth, kPad))
        drawCell(
            c, transparentBC1Image,
            SkIPoint(2 * kPad + kCellWidth, 2 * kPad + kBaseTexHeight),
        )
    }

    /**
     * Mirrors upstream's `drawCell(canvas, image, offset)` — stamps every
     * mip level of [image] at native size, walking right one pixel for
     * level 0 → 1, then down for the remaining halvings. Always stamps a
     * 1-px-stroked red outline around every cell (upstream only does
     * this when `isCompressed == false`, which is always the case on the
     * raster fallback — see class KDoc).
     *
     * Sampling is bicubic Mitchell-Netravali — same kernel upstream
     * picks (`SkSamplingOptions(SkCubicResampler::Mitchell())`).
     */
    private fun drawCell(canvas: SkCanvas, image: SkImage?, startOffset: SkIVector) {
        val offset = SkIPoint(startOffset.fX, startOffset.fY)
        var levelDimensions = imgDimensions
        val numMipLevels = computeLevelCount(levelDimensions) + 1

        val sampling = SkSamplingOptions(SkCubicResampler.Mitchell)

        // Upstream queries `image->isTextureBacked()` + `GrCaps::isFormatCompressed`
        // to decide whether to stamp the red outline. On the raster-only
        // kanvas-skia path the image is *always* non-texture-backed, so
        // `isCompressed` is `false` and the outline always fires.
        val isCompressed = false

        val redStrokePaint = SkPaint().apply {
            color = SK_ColorRED
            style = SkPaint.Style.kStroke_Style
        }

        for (i in 0 until numMipLevels) {
            val r = SkRect.MakeXYWH(
                offset.fX.toFloat(), offset.fY.toFloat(),
                levelDimensions.width.toFloat(), levelDimensions.height.toFloat(),
            )

            if (image != null) {
                canvas.drawImageRect(
                    image,
                    SkRect.MakeWH(image.width.toFloat(), image.height.toFloat()),
                    r,
                    sampling,
                )
            }
            if (!isCompressed) {
                // Make it obvious which drawImages used decompressed images.
                canvas.drawRect(r, redStrokePaint)
            }

            if (i == 0) {
                offset.fX += levelDimensions.width + 1
            } else {
                offset.fY += levelDimensions.height + 1
            }

            levelDimensions = SkISize.Make(
                max(1, levelDimensions.width / 2),
                max(1, levelDimensions.height / 2),
            )
        }
    }

    public companion object {
        /** Padding around the GM and between cells (px). */
        public const val kPad: Int = 8
        public const val kBaseTexWidth: Int = 64
        public const val kBaseTexHeight: Int = 64

        /** Cell width = `1.5 * base` (matches upstream's `kCellWidth`). */
        public const val kCellWidth: Int = (1.5f * kBaseTexWidth).toInt()

        /** Upstream's `setBGColor(0xFFCCCCCC)` — light grey. */
        public const val kBackground: SkColor = 0xFFCCCCCC.toInt()

        /**
         * Mirrors upstream's `gen_pt(angle, scale)` — emits a point on
         * an axis-aligned ellipse of half-axes `(scale.fX, scale.fY)`
         * at angle [angle] radians. Cosine drives x, sine drives y
         * (right-handed, y-down in canvas space).
         */
        private fun gen_pt(angle: SkScalar, scale: SkVector): SkPoint {
            val s = SkScalarSin(angle)
            val c = SkScalarCos(angle)
            return SkPoint(scale.fX * c, scale.fY * s)
        }

        /**
         * Mirrors upstream's `make_gear(dimensions, numTeeth)` — emits a
         * closed gear path centred at `(0, 0)` and bounded by
         * `[-w/2, w/2] × [-h/2, h/2]`. Each tooth spans one third of
         * `kAnglePerTooth`'s 3× cycle ; the outer rim sits at the bbox,
         * the inner rim at 80 % radius. When the smaller dimension
         * exceeds 5 px, a hub circle (`fInnerRad = 0.1 * min(w, h)`) is
         * added CCW so the winding rule cuts it out.
         */
        private fun make_gear(dimensions: SkISize, numTeeth: Int): SkPath {
            val outerRad = SkVector(dimensions.width / 2.0f, dimensions.height / 2.0f)
            val innerRad = SkVector(dimensions.width / 2.5f, dimensions.height / 2.5f)
            val kAnglePerTooth: Float = 2.0f * SK_ScalarPI / (3 * numTeeth)

            var angle = 0.0f
            val tmp = SkPathBuilder(SkPathFillType.kWinding)

            tmp.moveTo(gen_pt(angle, outerRad).fX, gen_pt(angle, outerRad).fY)

            for (i in 0 until numTeeth) {
                val p1 = gen_pt(angle + kAnglePerTooth, outerRad)
                val p2 = gen_pt(angle + 1.5f * kAnglePerTooth, innerRad)
                val p3 = gen_pt(angle + 2.5f * kAnglePerTooth, innerRad)
                val p4 = gen_pt(angle + 3.0f * kAnglePerTooth, outerRad)
                tmp.lineTo(p1.fX, p1.fY)
                tmp.lineTo(p2.fX, p2.fY)
                tmp.lineTo(p3.fX, p3.fY)
                tmp.lineTo(p4.fX, p4.fY)
                angle += 3 * kAnglePerTooth
            }

            tmp.close()

            val fInnerRad = 0.1f * min(dimensions.width, dimensions.height)
            if (fInnerRad > 0.5f) {
                tmp.addCircle(0.0f, 0.0f, fInnerRad, SkPathDirection.kCCW)
            }

            return tmp.detach()
        }

        /**
         * Mirrors upstream's `render_level(dimensions, color, colorType,
         * opaque)` — rasterises one mip level of the gear into a fresh
         * [SkBitmap]. The bitmap is filled with opaque black (opaque
         * variant) or transparent black (non-opaque variant) and a gear
         * is drawn centred with the supplied [color] (alpha forced to
         * opaque via `color | 0xFF000000`, matching upstream).
         *
         * **`colorType`** : upstream passes [SkColorType.kRGB_565] for
         * the ETC2 path (the ETC2-RGB8 encoder ingests 565 directly)
         * and [SkColorType.kRGBA_8888] for the BC1 path. The kanvas-skia
         * raster backend stores both internally as 8888 (see
         * [SkBitmap] KDoc), but we surface the requested colour type
         * via [SkImageInfo] so the encoder stub sees the right tag.
         */
        private fun render_level(
            dimensions: SkISize,
            color: SkColor,
            colorType: SkColorType,
            opaque: Boolean,
        ): SkBitmap {
            val path = make_gear(dimensions, numTeeth = 9)

            val ii = SkImageInfo.Make(
                dimensions.width, dimensions.height,
                colorType,
                if (opaque) SkAlphaType.kOpaque else SkAlphaType.kPremul,
            )
            val bm = SkBitmap.allocPixels(ii)

            bm.eraseColor(if (opaque) SK_ColorBLACK else SK_ColorTRANSPARENT)

            val c = SkCanvas(bm)

            val paint = SkPaint().apply {
                // Mirrors upstream's `paint.setColor(color | 0xFF000000)`
                // — force alpha to opaque regardless of the input.
                this.color = color or 0xFF000000.toInt()
                this.isAntiAlias = false
            }

            c.translate(dimensions.width / 2.0f, dimensions.height / 2.0f)
            c.drawPath(path, paint)

            return bm
        }

        /**
         * Mirrors upstream's `make_compressed_image(canvas, dimensions,
         * colorType, opaque, compression)` — allocates the compressed
         * payload (all mip levels concatenated in a single
         * [SkData]-wrapped byte buffer), renders each level via
         * [render_level], encodes through the format-specific helper
         * ([SkCompressedDataUtils.Etc1EncodeImage] for ETC2,
         * [SkCompressedDataUtils.TwoColorBC1Compress] for BC1), and
         * wraps the result with [SkImages.RasterFromCompressedTextureData].
         *
         * **Ganesh / Graphite skipped.** Upstream conditionally bails to
         * `SkImages::TextureFromCompressedTextureData` (Ganesh) or
         * `SkImages::WrapTexture(recorder, …)` (Graphite) when a GPU
         * recorder is available ; the raster port always falls through
         * to `RasterFromCompressedTextureData`.
         */
        private fun make_compressed_image(
            @Suppress("UNUSED_PARAMETER") canvas: SkCanvas,
            dimensions: SkISize,
            colorType: SkColorType,
            opaque: Boolean,
            compression: SkTextureCompressionType,
        ): SkImage? {
            val totalSize = SkCompressedDataUtils.SkCompressedDataSize(
                compression, dimensions, mipMapOffsetsAndSizes = null, mipMapped = true,
            )

            val bytes = ByteArray(totalSize.toInt())

            val numMipLevels = computeLevelCount(dimensions) + 1

            // Use a different color for each mipmap level so the draws
            // can be visually told apart (matches upstream).
            val kColors: IntArray = intArrayOf(
                SK_ColorRED,
                SK_ColorGREEN,
                SK_ColorBLUE,
                SK_ColorCYAN,
                SK_ColorMAGENTA,
                SK_ColorYELLOW,
                SK_ColorWHITE,
            )

            var offset = 0L
            var levelDims = dimensions
            for (i in 0 until numMipLevels) {
                val levelSize = SkCompressedDataUtils.SkCompressedDataSize(
                    compression, levelDims, mipMapOffsetsAndSizes = null, mipMapped = false,
                )

                val bm = render_level(levelDims, kColors[i % 7], colorType, opaque)
                when (compression) {
                    SkTextureCompressionType.kETC2_RGB8_UNORM -> {
                        // Upstream asserts `bm.colorType() == kRGB_565` +
                        // `opaque`. The kanvas-skia render_level honours the
                        // requested colour-type tag.
                        check(bm.colorType == SkColorType.kRGB_565) {
                            "ETC2 requires kRGB_565 source ; got ${bm.colorType}"
                        }
                        check(opaque) { "ETC2 requires opaque source" }
                        SkCompressedDataUtils.Etc1EncodeImage(
                            srcBitmap = bm,
                            dst = bytes,
                            dstOffset = offset.toInt(),
                        )
                    }
                    SkTextureCompressionType.kBC1_RGB8_UNORM,
                    SkTextureCompressionType.kBC1_RGBA8_UNORM -> {
                        SkCompressedDataUtils.TwoColorBC1Compress(
                            srcBitmap = bm,
                            otherColor = kColors[i % 7],
                            dst = bytes,
                            dstOffset = offset.toInt(),
                        )
                    }
                    SkTextureCompressionType.kNone -> error(
                        "compression == kNone is not a valid compressed format",
                    )
                }

                offset += levelSize
                levelDims = SkISize.Make(
                    max(1, levelDims.width / 2),
                    max(1, levelDims.height / 2),
                )
            }

            return SkImages.RasterFromCompressedTextureData(
                SkData.MakeWithCopy(bytes),
                dimensions.width, dimensions.height,
                compression,
            )
        }

        /**
         * Mirrors Skia's `SkMipmap::ComputeLevelCount(SkISize)` —
         * `floor(log2(max(w, h)))`. The total mip count (base + chain)
         * is `1 + ComputeLevelCount`.
         */
        private fun computeLevelCount(dim: SkISize): Int {
            var n = max(dim.width, dim.height)
            var levels = 0
            while (n > 1) {
                n = n shr 1
                levels++
            }
            return levels
        }
    }
}
