package org.skia.testing

import org.graphiks.kanvas.codec.SkCodec
import org.skia.dm.RasterSinkF16
import org.skia.dm.Sink
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.encode.SkPngEncoder
import org.skia.tests.GM
import java.io.File

public object TestUtils {

    private const val REFERENCE_DIR: String = "original-888"

    /**
     * Default per-channel similarity tolerance for **textual GMs**.
     *
     * Empirically validated across the four post-T4 textual ports
     * (`BigTextGM` 98.20%, `ColorWheelNativeGM` 99.75%,
     * `Crbug1073670GM` 72.52%, `AnnotatedTextGM` 99.90%): with
     * Liberation TTFs feeding both rasterisers, the dominant
     * pixel-level drift between us and upstream is the AWT-vs-FreeType
     * AA edge difference (~1-2 ulps on 8-bit) plus minor scaler /
     * hinting offsets. Tolerance = 8 absorbs this drift on bordering
     * pixels while staying tight enough to catch genuine rendering
     * regressions.
     *
     * Cf. `archives/MIGRATION_PLAN_TEXT.md` §"Décisions finales" — was the last
     * open decision in the text plan; this constant closes it.
     *
     * Non-textual GMs continue to use the per-test tolerance picked
     * to match each rasteriser's behaviour (e.g. `0` for axis-aligned
     * rect, `1` for solid-colour AA rect, etc.).
     */
    public const val TEXTUAL_GM_TOLERANCE: Int = 8

    /**
     * Color space the Skia DM reference PNGs in `original-888/` are encoded
     * with. See [colorspace-fingerprint.md](../resources/colorspace-fingerprint.md)
     * for the full ICC dump. Rendering GM tests into a bitmap with this
     * color space lets us compare against the references at single-ulp
     * tolerance instead of the worst-case ~150 we used before SkColorSpace
     * was wired up.
     */
    public val DM_REFERENCE_COLOR_SPACE: SkColorSpace =
        SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!

    /**
     * Render a GM into a freshly allocated bitmap of the GM's preferred size,
     * in the DM reference colorspace, filled with `gm.bgColor()` before
     * `onDraw` runs. Mirrors Skia's `gm.cpp` test runner with `--config f16`
     * and the "DM unified Rec.2020" working color space.
     *
     * Phase D4.1 routes this through the new [RasterSinkF16] DM sink so the
     * existing call sites benefit from the unified sink dispatch ; the
     * pixels and the colour space the bitmap is tagged with are unchanged.
     *
     * The bg color is sRGB-encoded (per the SkColor convention). When it is
     * black or white — both profile-invariant — a raw fill is bit-identical
     * to going through the device. ScaledRectsGM-style non-trivial bg
     * colors will need an xformed eraseColor; we add it when a GM in scope
     * demands it.
     */
    public fun runGmTest(gm: GM): SkBitmap {
        val sink = RasterSinkF16(DM_REFERENCE_COLOR_SPACE)
        return when (val result = sink.draw(gm)) {
            is Sink.Result.Ok -> result.bitmap
            is Sink.Result.Bytes -> throw IllegalStateException(
                "TestUtils.runGmTest expected raster output but RasterSinkF16 returned " +
                    "Sink.Result.Bytes ; this should never happen.",
            )
            is Sink.Result.Error -> throw IllegalStateException(result.message)
        }
    }

    public fun loadReferenceImage(name: String): SkBitmap? = loadReferenceBitmap(name)

    /**
     * Read raw PNG bytes from the classpath. Used by [loadReferenceCodec]
     * to feed the [SkCodec] dispatcher.
     */
    private fun readPngBytes(name: String): ByteArray? {
        val path = "$REFERENCE_DIR/$name.png"
        return TestUtils::class.java.classLoader.getResourceAsStream(path)?.readBytes()
    }

    /**
     * Build an [SkCodec] over the named reference PNG. Returns `null`
     * if the resource is missing or the bytes are not a valid PNG.
     *
     * D3.1 wire-up : the codec replaces the inline iCCP / JVM image
     * plumbing that used to live in this file. Callers that need just
     * the colour space go through [loadReferenceColorSpace] ; callers
     * that need the decoded bitmap go through [loadReferenceBitmap].
     */
    public fun loadReferenceCodec(name: String): SkCodec? {
        val bytes = readPngBytes(name) ?: return null
        return SkCodec.MakeFromData(bytes)
    }

    /**
     * Best-effort extraction of the colorspace embedded in a reference
     * PNG. Returns `null` if the PNG has no `iCCP` chunk or the chunk
     * fails to parse as a usable ICC profile.
     *
     * Phase F7 wire-up: the reference PNGs in `original-888/` all carry
     * the `DM unified Rec.2020` profile, so this returns a colorspace
     * structurally equivalent to [DM_REFERENCE_COLOR_SPACE]. Tests can
     * use it to assert that assumption (a sanity check that catches a
     * future GM whose profile diverges).
     */
    public fun loadReferenceColorSpace(name: String): SkColorSpace? {
        val codec = loadReferenceCodec(name) ?: return null
        val profile = codec.getICCProfile() ?: return null
        return SkColorSpace.make(profile)
    }

    public fun loadReferenceBitmap(name: String): SkBitmap? {
        val codec = loadReferenceCodec(name) ?: return null
        val (bitmap, result) = codec.getImage()
        if (result != SkCodec.Result.kSuccess || bitmap == null) return null
        return bitmap
    }

    public fun saveDebugImage(bitmap: SkBitmap, name: String) {
        val dir = File("build/debug-images").apply { mkdirs() }
        writePng(bitmap, File(dir, "$name.png"))
    }

    /**
     * Per-channel tolerance similarity between two `SkBitmap`s, in percent (0..100).
     * Thin wrapper kept for legacy call sites — new code should prefer
     * [compareBitmapsDetailed], which exposes pixel counts, max diff per
     * channel, and mean diff across mismatching pixels.
     *
     * Now that GMs render into [DM_REFERENCE_COLOR_SPACE], pure colours land
     * within 1 ulp of the reference instead of ~150; `tolerance = 1` is the
     * default for the current crop of GM tests, with rasteriser-quality
     * residuals modelled by per-test thresholds.
     */
    public fun compareBitmaps(a: SkBitmap, b: SkBitmap, tolerance: Int = 0): Double =
        compareBitmapsDetailed(a, b, tolerance).similarity

    /**
     * Per-channel tolerance similarity, returning pixel counts and per-channel
     * statistics on the mismatching pixels. A pixel matches when every channel
     * diff is `<= tolerance`. Mean diff is averaged over mismatching pixels
     * only (so it doesn't get diluted to zero by a sea of perfect matches).
     */
    public fun compareBitmapsDetailed(a: SkBitmap, b: SkBitmap, tolerance: Int = 0): BitmapComparison {
        if (a.width != b.width || a.height != b.height) {
            return BitmapComparison(
                similarity = 0.0,
                totalPixels = 0,
                matchingPixels = 0,
                tolerance = tolerance,
                maxChannelDiff = ChannelDiff(0, 0, 0, 0),
                meanMismatchDiff = ChannelDiff(0, 0, 0, 0),
            )
        }
        // Phase 6: when either side is F16, walk both bitmaps through the
        // float accessor, then quantize to 8-bit non-premul **per pixel**
        // before comparing. This keeps the comparison semantics identical
        // to the legacy 8-bit code path (so previously-passing scores stay
        // stable) while letting F16 rendering still benefit from float-
        // precision compositing arithmetic.
        val anyF16 = a.colorType == SkColorType.kRGBA_F16Norm ||
                     b.colorType == SkColorType.kRGBA_F16Norm
        val total = a.width * a.height
        var matching = 0
        var maxA = 0; var maxR = 0; var maxG = 0; var maxB = 0
        var sumA = 0L; var sumR = 0L; var sumG = 0L; var sumB = 0L
        if (anyF16) {
            for (y in 0 until a.height) {
                for (x in 0 until a.width) {
                    val pa = a.getPixel(x, y)
                    val pb = b.getPixel(x, y)
                    if (pa == pb) { matching++; continue }
                    val dA = kotlin.math.abs(((pa ushr 24) and 0xFF) - ((pb ushr 24) and 0xFF))
                    val dR = kotlin.math.abs(((pa ushr 16) and 0xFF) - ((pb ushr 16) and 0xFF))
                    val dG = kotlin.math.abs(((pa ushr 8) and 0xFF) - ((pb ushr 8) and 0xFF))
                    val dB = kotlin.math.abs((pa and 0xFF) - (pb and 0xFF))
                    if (maxOf(dA, maxOf(dR, maxOf(dG, dB))) <= tolerance) {
                        matching++
                    } else {
                        if (dA > maxA) maxA = dA
                        if (dR > maxR) maxR = dR
                        if (dG > maxG) maxG = dG
                        if (dB > maxB) maxB = dB
                        sumA += dA; sumR += dR; sumG += dG; sumB += dB
                    }
                }
            }
        } else {
            for (i in 0 until total) {
                val pa = a.pixels[i]
                val pb = b.pixels[i]
                if (pa == pb) { matching++; continue }
                val dA = kotlin.math.abs(((pa ushr 24) and 0xFF) - ((pb ushr 24) and 0xFF))
                val dR = kotlin.math.abs(((pa ushr 16) and 0xFF) - ((pb ushr 16) and 0xFF))
                val dG = kotlin.math.abs(((pa ushr 8) and 0xFF) - ((pb ushr 8) and 0xFF))
                val dB = kotlin.math.abs((pa and 0xFF) - (pb and 0xFF))
                if (maxOf(dA, maxOf(dR, maxOf(dG, dB))) <= tolerance) {
                    matching++
                } else {
                    if (dA > maxA) maxA = dA
                    if (dR > maxR) maxR = dR
                    if (dG > maxG) maxG = dG
                    if (dB > maxB) maxB = dB
                    sumA += dA; sumR += dR; sumG += dG; sumB += dB
                }
            }
        }
        val similarity = matching.toDouble() / total.toDouble() * 100.0
        val mismatched = total - matching
        val mean = if (mismatched == 0) ChannelDiff(0, 0, 0, 0) else ChannelDiff(
            a = (sumA / mismatched).toInt(),
            r = (sumR / mismatched).toInt(),
            g = (sumG / mismatched).toInt(),
            b = (sumB / mismatched).toInt(),
        )
        return BitmapComparison(
            similarity = similarity,
            totalPixels = total,
            matchingPixels = matching,
            tolerance = tolerance,
            maxChannelDiff = ChannelDiff(maxA, maxR, maxG, maxB),
            meanMismatchDiff = mean,
        )
    }

    /**
     * Save a `rendered ｜ diff ｜ reference` triptych at
     * `build/debug-images/<name>-comparison.png`. Replaces the
     * "two separate files" debug pattern: one image, easy to scan visually,
     * with the diff panel highlighting mismatches in magenta proportional
     * to severity beyond `comparison.tolerance`.
     */
    public fun saveComparisonImage(
        rendered: SkBitmap,
        reference: SkBitmap,
        comparison: BitmapComparison,
        name: String,
    ) {
        val dir = File("build/debug-images").apply { mkdirs() }
        val triptych = DiffImage.buildTriptych(rendered, reference, comparison.tolerance, comparison)
        writePng(triptych, File(dir, "$name-comparison.png"))
    }

    private fun writePng(bitmap: SkBitmap, file: File) {
        val bytes = SkPngEncoder.Encode(bitmap)
            ?: throw IllegalStateException("Could not encode ${file.name} as PNG")
        file.writeBytes(bytes)
    }

}
