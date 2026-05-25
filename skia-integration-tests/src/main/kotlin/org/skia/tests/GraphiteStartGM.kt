package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorDKGRAY
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorLTGRAY
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SK_ColorYELLOW
import org.graphiks.math.SkColor
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkShaders
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/graphitestart.cpp::GraphiteStartGM`](https://github.com/google/skia/blob/main/gm/graphitestart.cpp)
 * — the "hello-world" GM written when Graphite (Skia's next-gen GPU
 * backend) was being bootstrapped. Lays out a 3×3 grid of 128×128
 * tiles inside a single rounded-rect clip, each tile exercising one
 * basic shader / colour-filter / blend feature :
 *
 * | row | col 0                            | col 1                          | col 2                            |
 * |-----|----------------------------------|--------------------------------|----------------------------------|
 * | 0   | image-shader (clamp/repeat) path | linear gradient on rotated rect | 3×3 swatch of colour-filters    |
 * | 1   | flat red rect                    | `Blend(kModulate, …)` shader   | grayscale colour-filter on image |
 * | 2   | `writePixels` (Graphite only)    | blend-mode swatch grid          | nested `saveLayer(kPlus)`        |
 *
 * ## Port status — **INTRACTABLE (Graphite-only features)**
 *
 * The GM is **Graphite-private** upstream — `writePixels(SkBitmap, …)`
 * in the bottom-left tile is gated on `#if defined(SK_GRAPHITE)`, and
 * `SkColorFilterPriv::MakeGaussian()` for slot `[4]` of the colour-
 * filter swatch grid lives behind Skia's private API. `:kanvas-skia`
 * is raster-CPU only — no Graphite backend, no Graphite-private
 * filters. Degradations applied :
 *
 *  - **`STUB.GRAPHITE.writePixels`** — the bottom-left tile is a black
 *    cell (the canvas's bgColor) instead of upstream's resource-loaded
 *    `images/color_wheel.gif`. The upstream code path runs only when
 *    `SK_GRAPHITE` is defined ; the raster CPU bot also produces a
 *    black cell for this tile (the gif resource isn't shipped under
 *    `skia-integration-tests/src/test/resources/images/` either — only the
 *    `.png` and `.jpg` siblings are).
 *  - **`STUB.GRAPHITE.gaussianCF`** — slot `[4]` of the upper-right
 *    swatch grid uses `SkColorFilterPriv::MakeGaussian()` upstream
 *    (alpha-only Gaussian shaping for SDF glyphs). That helper is in
 *    `src/core/SkColorFilterPriv.h`, not exposed in the public API.
 *    The Kotlin port draws the gradient un-filtered for this slot —
 *    a visual diff against upstream, but the surrounding 8 slots
 *    (Lighting, Table, Compose, Blend, LinearToSRGBGamma,
 *    SRGBToLinearGamma) still render through their normal pipelines.
 *  - **`STUB.GRAPHITE.makeWithColorFilter`** — the middle-right tile's
 *    `shader->makeWithColorFilter(create_grayscale_colorfilter())`
 *    call needs `SkShader::makeWithColorFilter` (private subclass
 *    `SkColorFilterShader`). `:kanvas-skia`'s [SkShader] only exposes
 *    `makeWithLocalMatrix` / `makeWithWorkingColorSpace`. We route
 *    the grayscale filter through `paint.colorFilter = …` instead —
 *    same visual effect for the single-shader case, though it doesn't
 *    compose into a nested `paint.shader` chain. Upstream also sets
 *    `paint.shader = shader` ; equivalent for this tile since the
 *    grayscale filter is the only filter in the paint.
 *
 * Everything else — the linear gradients, image-shader sampling, blend-
 * mode swatch, nested `saveLayer(kPlus)` composite, RRect clip — runs
 * through the raster CPU pipeline.
 *
 * ## Test status
 *
 * [GraphiteStartTest] stays `@Disabled` with the `STUB.GRAPHITE` tag :
 * the visual diff against the upstream Graphite reference would be
 * dominated by the missing `writePixels` tile (a 128 × 128 hole vs a
 * color wheel) and the Gaussian-filter slot. Once Graphite or the
 * private gaussian filter lands, drop the `@Disabled` and ratchet.
 */
public class GraphiteStartGM : GM() {

    init {
        setBGColor(SK_ColorBLACK)
    }

    override fun getName(): String = "graphitestart"

    override fun getISize(): SkISize = SkISize.Make(K_WIDTH, K_HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Clear to the GM's bgColor. The raster harness in
        // `:cpu-raster` doesn't pre-fill the canvas the way the GM
        // dispatcher does in upstream Skia, so we paint the bg
        // explicitly here. Matches upstream's `setBGColor(SK_ColorBLACK)`.
        c.drawColor(SK_ColorBLACK)

        val clipRect: SkRect = SkRect.MakeWH(K_WIDTH.toFloat(), K_HEIGHT.toFloat())
            .makeInset(K_CLIP_INSET.toFloat(), K_CLIP_INSET.toFloat())

        c.save()
        c.clipRRect(SkRRect.MakeRectXY(clipRect, 32f, 32f), true)

        // Upper-left corner — image-shader path with two tile modes.
        drawImageShaderTile(c, SkRect.MakeXYWH(0f, 0f, K_TILE_WIDTH.toFloat(), K_TILE_HEIGHT.toFloat()))

        // Upper-middle — linear gradient on a rotated rect.
        drawGradientTile(c, SkRect.MakeXYWH(K_TILE_WIDTH.toFloat(), 0f, K_TILE_WIDTH.toFloat(), K_TILE_HEIGHT.toFloat()))

        // Upper-right — 3×3 colour-filter swatch grid.
        drawColorFilterSwatches(
            c,
            SkRect.MakeXYWH(
                (2 * K_TILE_WIDTH).toFloat(), 0f,
                K_TILE_WIDTH.toFloat(), K_TILE_WIDTH.toFloat(),
            ),
        )

        // Middle-left — flat red rect.
        run {
            val p = SkPaint().apply { color = SK_ColorRED }
            val r = SkRect.MakeXYWH(
                0f, K_TILE_HEIGHT.toFloat(),
                K_TILE_WIDTH.toFloat(), K_TILE_HEIGHT.toFloat(),
            )
            c.drawRect(r.makeInset(1f, 1f), p)
        }

        // Middle-middle — `Blend(kModulate, transYellow, imageShader)`.
        run {
            val p = SkPaint()
            p.shader = createBlendShader(c, SkBlendMode.kModulate)
            val r = SkRect.MakeXYWH(
                K_TILE_WIDTH.toFloat(), K_TILE_HEIGHT.toFloat(),
                K_TILE_WIDTH.toFloat(), K_TILE_HEIGHT.toFloat(),
            )
            c.drawRect(r.makeInset(1f, 1f), p)
        }

        // Middle-right — mandrill image-shader + grayscale colour filter.
        run {
            val image: SkImage? = ToolUtils.GetResourceAsImage("images/mandrill_128.png")
            val p = SkPaint()
            if (image != null) {
                p.shader = image.makeShader(
                    tileX = SkTileMode.kRepeat,
                    tileY = SkTileMode.kRepeat,
                    sampling = SkSamplingOptions.Default,
                )
                // STUB.GRAPHITE.makeWithColorFilter — upstream wraps
                // the shader via `shader->makeWithColorFilter(filter)`.
                // We route the filter through the paint instead — same
                // visual effect for the single-shader case.
                p.colorFilter = createGrayscaleColorFilter()
            }
            val r = SkRect.MakeXYWH(
                (2 * K_TILE_WIDTH).toFloat(), K_TILE_HEIGHT.toFloat(),
                K_TILE_WIDTH.toFloat(), K_TILE_HEIGHT.toFloat(),
            )
            c.drawRect(r.makeInset(1f, 1f), p)
        }

        c.restore()

        // Bottom-left corner — `#if defined(SK_GRAPHITE) writePixels(...)`.
        // STUB.GRAPHITE.writePixels — kanvas-skia has no Graphite backend
        // and no `writePixels` API on SkCanvas. The raster CPU build of
        // upstream Skia leaves this tile as the canvas bgColor (black),
        // which we already painted above. No-op.

        // Bottom-middle — blend-mode swatch grid.
        drawBlendModeSwatches(
            c,
            SkRect.MakeXYWH(
                K_TILE_WIDTH.toFloat(), (2 * K_TILE_HEIGHT).toFloat(),
                K_TILE_WIDTH.toFloat(), K_TILE_HEIGHT.toFloat(),
            ),
        )

        // Bottom-right — nested `saveLayer(kPlus)` composite.
        run {
            val kTile = SkRect.MakeXYWH(
                (2 * K_TILE_WIDTH).toFloat(), (2 * K_TILE_HEIGHT).toFloat(),
                K_TILE_WIDTH.toFloat(), K_TILE_HEIGHT.toFloat(),
            )

            val circlePaint = SkPaint().apply {
                color = SK_ColorBLUE
                blendMode = SkBlendMode.kSrc
            }

            c.clipRect(kTile)
            c.drawRect(kTile.makeInset(10f, 20f), circlePaint)

            val restorePaint = SkPaint().apply { blendMode = SkBlendMode.kPlus }

            c.saveLayer(null, restorePaint)
            circlePaint.color = SK_ColorRED
            circlePaint.blendMode = SkBlendMode.kSrc
            c.drawRect(kTile.makeInset(15f, 25f), circlePaint)
            c.restore()
        }
    }

    // ─── Helpers — mirror upstream's anonymous-namespace utilities ──

    /**
     * Mirrors upstream's `create_gradient_shader(r, colors, offsets)` —
     * builds a horizontal 3-stop linear gradient from `(r.left, r.top)`
     * to `(r.right, r.top)`.
     */
    private fun createGradientShader(
        r: SkRect,
        colors: IntArray,
        offsets: FloatArray,
    ): SkShader = SkLinearGradient.Make(
        p0 = SkPoint.Make(r.left, r.top),
        p1 = SkPoint.Make(r.right, r.top),
        colors = colors,
        positions = offsets,
        tileMode = SkTileMode.kClamp,
    )

    /**
     * Mirrors upstream's `create_image_shader(destCanvas, tmX, tmY)` —
     * synthesises a 64×64 bitmap with 3×3 coloured tiles (22-pixel
     * squares on a 21-pixel grid), then exposes it as an image shader
     * with caller-supplied tile modes.
     *
     * Upstream's `ToolUtils::MakeTextureImage(destCanvas, image)`
     * collapses to identity in raster (no GPU backend) — see
     * `ToolUtils.MakeTextureImage` KDoc.
     */
    private fun createImageShader(
        destCanvas: SkCanvas,
        tmX: SkTileMode,
        tmY: SkTileMode,
    ): SkShader? {
        val bitmap = SkBitmap(64, 64)
        bitmap.eraseColor(SK_ColorWHITE)

        val colors = arrayOf(
            intArrayOf(SK_ColorRED, SK_ColorDKGRAY, SK_ColorBLUE),
            intArrayOf(SK_ColorLTGRAY, SK_ColorCYAN, SK_ColorYELLOW),
            intArrayOf(SK_ColorGREEN, SK_ColorWHITE, SK_ColorMAGENTA),
        )

        // Upstream paints the 3×3 tiles via a tmp SkCanvas wrapping the
        // bitmap. We can short-circuit through `setPixel` since the
        // tiles are axis-aligned colour blocks — same end state.
        for (y in 0 until 3) {
            for (x in 0 until 3) {
                val c = colors[y][x]
                val x0 = x * 21
                val y0 = y * 21
                for (dy in 0 until 22) {
                    val py = y0 + dy
                    if (py >= 64) continue
                    for (dx in 0 until 22) {
                        val px = x0 + dx
                        if (px >= 64) continue
                        bitmap.setPixel(px, py, c)
                    }
                }
            }
        }

        // Upstream calls bitmap.setImmutable() before wrapping it in an
        // image — kanvas-skia's SkImage.Make(bitmap) snapshots the
        // pixels, no immutability flag needed. Identity through
        // ToolUtils.MakeTextureImage (no-op in raster).
        val img: SkImage = ToolUtils.MakeTextureImage(destCanvas, bitmap.asImage()) ?: return null
        return img.makeShader(tmX, tmY, SkSamplingOptions.Default)
    }

    /**
     * Mirrors upstream's `create_blend_shader(destCanvas, bm)` —
     * `Blend(bm, transYellow, imageShader(kRepeat, kRepeat))`.
     */
    private fun createBlendShader(destCanvas: SkCanvas, bm: SkBlendMode): SkShader {
        // Upstream uses `SkColor4f{1, 1, 0, 0.5}` ; the byte
        // representation is `argb(0x80, 0xFF, 0xFF, 0x00)`.
        val kTransYellow: SkColor = SkColorSetARGB(0x80, 0xFF, 0xFF, 0x00)
        val dst = SkShaders.Color(kTransYellow)
        val src = createImageShader(destCanvas, SkTileMode.kRepeat, SkTileMode.kRepeat)
            ?: SkShaders.Empty()
        return SkShaders.Blend(bm, dst, src)
    }

    /**
     * Mirrors upstream's `create_grayscale_colorfilter()` — Rec.709
     * luma weights as a 4×5 colour matrix that maps RGB → grayscale
     * and forces alpha to 1.
     */
    private fun createGrayscaleColorFilter(): SkColorFilter {
        val matrix = FloatArray(20)
        matrix[0] = 0.2126f; matrix[5] = 0.2126f; matrix[10] = 0.2126f
        matrix[1] = 0.7152f; matrix[6] = 0.7152f; matrix[11] = 0.7152f
        matrix[2] = 0.0722f; matrix[7] = 0.0722f; matrix[12] = 0.0722f
        matrix[18] = 1f
        return SkColorFilters.Matrix(matrix)
    }

    /**
     * Mirrors upstream's `draw_image_shader_tile(canvas, clipRect)` —
     * draws the synthetic image shader twice (once direct, once
     * rotated 90° around the path's centre) under a `0.5 ×` scale.
     */
    private fun drawImageShaderTile(canvas: SkCanvas, clipRect: SkRect) {
        val p = SkPaint()
        p.shader = createImageShader(canvas, SkTileMode.kClamp, SkTileMode.kRepeat)

        val path: SkPath = SkPathBuilder()
            .moveTo(1f, 1f)
            .lineTo(32f, 127f)
            .lineTo(96f, 127f)
            .lineTo(127f, 1f)
            .lineTo(63f, 32f)
            .close()
            .detach()

        canvas.save()
        canvas.clipRect(clipRect)
        canvas.scale(0.5f, 0.5f)
        canvas.drawPath(path, p)

        canvas.save()
        canvas.concat(SkMatrix.MakeRotate(90f, 64f, 64f))
        canvas.translate(128f, 0f)
        canvas.drawPath(path, p)
        canvas.restore()
        canvas.restore()
    }

    /**
     * Mirrors upstream's `draw_gradient_tile(canvas, clipRect)` — a
     * red→green→blue horizontal gradient drawn twice (direct + 90°
     * rotation) inside a translated, half-scaled local space.
     */
    private fun drawGradientTile(canvas: SkCanvas, clipRect: SkRect) {
        val r = SkRect.MakeLTRB(1f, 1f, 127f, 127f)
        val p = SkPaint()
        p.shader = createGradientShader(
            r,
            intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE),
            floatArrayOf(0f, 0.75f, 1f),
        )

        canvas.save()
        canvas.clipRect(clipRect)
        canvas.translate(128f, 0f)
        canvas.scale(0.5f, 0.5f)
        canvas.drawRect(r, p)

        canvas.save()
        canvas.concat(SkMatrix.MakeRotate(90f, 64f, 64f))
        canvas.translate(128f, 0f)
        canvas.drawRect(r, p)
        canvas.restore()
        canvas.restore()
    }

    /**
     * Mirrors upstream's `draw_colorfilter_swatches(canvas, clipRect)`
     * — a 3 × 3 grid of mini-gradient swatches, each one filtered
     * through a different [SkColorFilter] from the swatch table.
     *
     * Slot `[4]` (centre) uses `SkColorFilterPriv::MakeGaussian()`
     * upstream — that helper lives behind Skia's private API
     * (`src/core/SkColorFilterPriv.h`) and isn't part of the public
     * [SkColorFilters] surface. Tagged `STUB.GRAPHITE.gaussianCF` ;
     * we render the gradient un-filtered for that slot.
     */
    private fun drawColorFilterSwatches(canvas: SkCanvas, clipRect: SkRect) {
        val numTilesPerSide = 3
        val tileW = clipRect.width() / numTilesPerSide
        val tileH = clipRect.height() / numTilesPerSide

        // Quantize to four colours.
        val table1 = ByteArray(256)
        for (i in 0 until 256) {
            table1[i] = ((i / 64) * 85).toByte()
        }

        // table2 is a band-pass filter for 85–170, table3 re-expands.
        val table2 = ByteArray(256)
        val table3 = ByteArray(256)
        for (i in 0 until 256) {
            if (i in 85..170) {
                table2[i] = i.toByte()
                table3[i] = (((i - 85) / 85f) * 255f).toInt().coerceIn(0, 255).toByte()
            } else {
                table2[i] = 0
                table3[i] = 0
            }
        }

        val skColorGrey: SkColor = SkColorSetARGB(0xFF, 0x80, 0x80, 0x80)

        val kGradientColors = arrayOf(
            intArrayOf(SK_ColorBLACK, skColorGrey, SK_ColorWHITE),
            intArrayOf(SK_ColorBLACK, skColorGrey, SK_ColorWHITE),
            intArrayOf(SK_ColorBLACK, skColorGrey, SK_ColorWHITE),
            intArrayOf(SK_ColorBLACK, skColorGrey, SK_ColorWHITE),
            // The Gaussian CF uses alpha only — gradient runs from
            // transparent black to opaque black.
            intArrayOf(0x00000000, 0x80000000.toInt(), 0xFF000000.toInt()),
            intArrayOf(SK_ColorBLACK, skColorGrey, SK_ColorWHITE),
            intArrayOf(SK_ColorBLACK, skColorGrey, SK_ColorWHITE),
            intArrayOf(SK_ColorBLACK, skColorGrey, SK_ColorWHITE),
            intArrayOf(SK_ColorBLACK, skColorGrey, SK_ColorWHITE),
        )

        // Pre-build the colour-filter table. Slot 4 is null
        // (STUB.GRAPHITE.gaussianCF — upstream's
        // `SkColorFilterPriv::MakeGaussian()` is private).
        val colorFilters: Array<SkColorFilter?> = arrayOf(
            SkColorFilters.Lighting(SK_ColorLTGRAY, 0xFF440000.toInt()),
            SkColorFilters.Table(table1),
            SkColorFilters.Compose(
                SkColorFilters.TableARGB(null, table3, table3, table3),
                SkColorFilters.TableARGB(null, table2, table2, table2),
            ),
            SkColorFilters.Blend(SK_ColorGREEN, SkBlendMode.kMultiply),
            null, // STUB.GRAPHITE.gaussianCF — see KDoc.
            SkColorFilters.LinearToSRGBGamma(),
            SkColorFilters.SRGBToLinearGamma(),
            null, // Upstream's [7] and [8] are uninitialised — see the
            null, // `SkColorFilters::Lighting` / `Table` block ; tagged
            // here to keep the array stable. Slots without a filter
            // render the unfiltered gradient.
        )

        val p = SkPaint()

        canvas.save()
        canvas.clipRect(clipRect)
        canvas.translate(clipRect.left, clipRect.top)

        for (y in 0 until numTilesPerSide) {
            for (x in 0 until numTilesPerSide) {
                val r = SkRect.MakeXYWH(
                    x * tileW, y * tileH,
                    tileW, tileH,
                ).makeInset(1f, 1f)
                // Upstream stores the filters in column-major order :
                // `colorFilterIndex = x * 3 + y` (cpp:202). Mirror that
                // exactly so each slot matches the upstream pixel.
                val colorFilterIndex = x * numTilesPerSide + y
                p.shader = createGradientShader(
                    r,
                    kGradientColors[colorFilterIndex],
                    floatArrayOf(0f, 0.5f, 1f),
                )
                p.colorFilter = colorFilters[colorFilterIndex]
                canvas.drawRect(r, p)
            }
        }

        canvas.restore()
    }

    /**
     * Mirrors upstream's `draw_blend_mode_swatches(canvas, clipRect)`.
     *
     * Two horizontal passes : (1) transparent-bluish over opaque white,
     * (2) transparent-white over transparent-bluish. Each pass walks
     * every Porter-Duff coefficient blend mode (up to
     * `SkBlendMode.kLastCoeffMode == kScreen`) in 16 × 16 tiles laid
     * out left-to-right with row wrap inside [clipRect].
     */
    private fun drawBlendModeSwatches(canvas: SkCanvas, clipRect: SkRect) {
        val kTileWidth = 16f
        val kTileHeight = 16f
        val kOpaqueWhite: SkColor = SK_ColorWHITE
        val kTransBluish: SkColor = SkColorSetARGB(0x80, 0x00, 0x80, 0xFF)
        val kTransWhite: SkColor = SkColorSetARGB(0xBF, 0xFF, 0xFF, 0xFF)

        val dstPaint = SkPaint().apply {
            color = kOpaqueWhite
            blendMode = SkBlendMode.kSrc
            isAntiAlias = false
        }

        val srcPaint = SkPaint().apply {
            color = kTransBluish
            isAntiAlias = false
        }

        var r = SkRect.MakeXYWH(clipRect.left, clipRect.top, kTileWidth, kTileHeight)

        for (pass in 0 until 2) {
            // Iterate `[0 .. kLastCoeffMode]` inclusive — upstream's
            // `<=` loop. SkBlendMode.entries is ordinal-stable and
            // matches Skia's enum order one-to-one.
            val lastCoeffOrdinal = SkBlendMode.kLastCoeffMode.ordinal
            for (i in 0..lastCoeffOrdinal) {
                if (r.left + kTileWidth > clipRect.right) {
                    r = SkRect.MakeXYWH(
                        clipRect.left, r.top + kTileHeight,
                        kTileWidth, kTileHeight,
                    )
                }

                canvas.drawRect(r.makeInset(1f, 1f), dstPaint)
                srcPaint.blendMode = SkBlendMode.entries[i]
                canvas.drawRect(r.makeInset(2f, 2f), srcPaint)

                r = r.makeOffset(kTileWidth, 0f)
            }

            r = SkRect.MakeXYWH(
                clipRect.left, r.top + kTileHeight,
                kTileWidth, kTileHeight,
            )
            srcPaint.color = kTransWhite
            dstPaint.color = kTransBluish
        }
    }

    private companion object {
        const val K_TILE_WIDTH: Int = 128
        const val K_TILE_HEIGHT: Int = 128
        const val K_WIDTH: Int = 3 * K_TILE_WIDTH
        const val K_HEIGHT: Int = 3 * K_TILE_HEIGHT
        const val K_CLIP_INSET: Int = 4
    }
}
