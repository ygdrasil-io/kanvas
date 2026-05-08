package org.skia.testing

import org.skia.dm.RasterSinkF16
import org.skia.dm.Sink
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.skcms.SkNamedGamut
import org.skia.skcms.SkNamedTransferFn
import org.skia.skcms.skcmsParse
import org.skia.tests.GM
import java.awt.image.BufferedImage
import java.awt.image.DataBufferUShort
import java.io.DataInputStream
import java.io.File
import java.util.zip.Inflater
import javax.imageio.ImageIO

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
            is Sink.Result.Error -> throw IllegalStateException(result.message)
        }
    }

    public fun loadReferenceImage(name: String): BufferedImage? {
        val path = "$REFERENCE_DIR/$name.png"
        val url = TestUtils::class.java.classLoader.getResource(path) ?: return null
        return ImageIO.read(File(url.toURI()))
    }

    /**
     * Read raw PNG bytes from the classpath. Used by [loadReferenceBitmap]
     * to extract the iCCP chunk and parse the embedded color profile.
     */
    private fun readPngBytes(name: String): ByteArray? {
        val path = "$REFERENCE_DIR/$name.png"
        return TestUtils::class.java.classLoader.getResourceAsStream(path)?.readBytes()
    }

    /**
     * Walk a PNG looking for the `iCCP` chunk and return its inflated ICC
     * profile bytes, or `null` if the PNG has no ICC profile.
     */
    private fun extractIccProfile(pngBytes: ByteArray): ByteArray? {
        if (pngBytes.size < 8) return null
        val sig = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        if (!pngBytes.copyOfRange(0, 8).contentEquals(sig)) return null

        val dis = DataInputStream(pngBytes.inputStream())
        dis.skipBytes(8)
        while (dis.available() > 0) {
            val length = dis.readInt()
            val typeBytes = ByteArray(4).also { dis.readFully(it) }
            val type = String(typeBytes, Charsets.US_ASCII)
            val data = ByteArray(length).also { dis.readFully(it) }
            dis.readInt()
            if (type == "iCCP") {
                var nameEnd = 0
                while (nameEnd < data.size && data[nameEnd] != 0.toByte()) nameEnd++
                val compressed = data.copyOfRange(nameEnd + 2, data.size)
                val inflater = Inflater()
                inflater.setInput(compressed)
                val out = ByteArray(64 * 1024)
                val len = inflater.inflate(out)
                inflater.end()
                return out.copyOfRange(0, len)
            } else if (type == "IDAT") {
                // IDAT comes after iCCP per PNG spec; if we hit it without an
                // iCCP, there is none.
                return null
            }
        }
        return null
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
        val pngBytes = readPngBytes(name) ?: return null
        val iccBytes = extractIccProfile(pngBytes) ?: return null
        val profile = skcmsParse(iccBytes) ?: return null
        return SkColorSpace.make(profile)
    }

    public fun loadReferenceBitmap(name: String): SkBitmap? {
        val img = loadReferenceImage(name) ?: return null
        // Best-effort: tag the bitmap with the colorspace parsed from
        // the PNG's iCCP chunk. Falls back to the default sRGB if the
        // PNG has no profile or the parser rejects it.
        val cs = loadReferenceColorSpace(name) ?: SkColorSpace.makeSRGB()
        return bufferedImageToBitmap(img, cs)
    }

    public fun saveDebugImage(bitmap: SkBitmap, name: String) {
        val dir = File("build/debug-images").apply { mkdirs() }
        ImageIO.write(bitmapToBufferedImage(bitmap), "png", File(dir, "$name.png"))
    }

    public fun saveDebugImage(image: BufferedImage, name: String) {
        val dir = File("build/debug-images").apply { mkdirs() }
        ImageIO.write(image, "png", File(dir, "$name.png"))
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
        ImageIO.write(triptych, "png", File(dir, "$name-comparison.png"))
    }

    public fun bitmapToBufferedImage(bitmap: SkBitmap): BufferedImage {
        val img = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
        // For F16 bitmaps, materialize a 8-bit ARGB IntArray on the fly via
        // the colour-space-aware [SkBitmap.getPixel] accessor. (Skipping the
        // raw `pixels8888` alias would throw — it's an empty array for F16.)
        if (bitmap.colorType == SkColorType.kRGBA_F16Norm) {
            val argb = IntArray(bitmap.width * bitmap.height)
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    argb[y * bitmap.width + x] = bitmap.getPixel(x, y)
                }
            }
            img.setRGB(0, 0, bitmap.width, bitmap.height, argb, 0, bitmap.width)
        } else {
            img.setRGB(0, 0, bitmap.width, bitmap.height, bitmap.pixels, 0, bitmap.width)
        }
        return img
    }

    public fun bufferedImageToBitmap(
        img: BufferedImage,
        colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
    ): SkBitmap {
        val raster = img.raster
        val buf = raster.dataBuffer
        val numBands = raster.numBands
        // 16-bit-per-channel PNGs (the format Skia DM emits) — read raw
        // raster samples and preserve them as F16 floats. Going through
        // `getRGB` / `drawImage` would (a) apply the embedded ICC profile,
        // (b) quantize to 8 bits, both unwanted.
        if (buf is DataBufferUShort && numBands >= 3) {
            val bitmap = SkBitmap(img.width, img.height, colorSpace, SkColorType.kRGBA_F16Norm)
            val pixel = IntArray(numBands)
            val out = bitmap.pixelsF16
            val inv65535 = 1f / 65535f
            for (y in 0 until img.height) {
                for (x in 0 until img.width) {
                    raster.getPixel(x, y, pixel)
                    val r = pixel[0] * inv65535
                    val g = pixel[1] * inv65535
                    val b = pixel[2] * inv65535
                    val a = if (numBands >= 4) pixel[3] * inv65535 else 1f
                    val o = (y * img.width + x) * 4
                    // Premultiply — the F16 format requires it.
                    out[o] = r * a
                    out[o + 1] = g * a
                    out[o + 2] = b * a
                    out[o + 3] = a
                }
            }
            return bitmap
        }
        // Fallback: 8-bit images without an ICC profile load fine via getRGB.
        // Stay 8-bit (lossy, but matches the old behaviour).
        val bitmap = SkBitmap(img.width, img.height, colorSpace)
        val argb = if (img.type == BufferedImage.TYPE_INT_ARGB) img else
            BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB).also {
                val g = it.createGraphics()
                g.drawImage(img, 0, 0, null)
                g.dispose()
            }
        argb.getRGB(0, 0, argb.width, argb.height, bitmap.pixels, 0, argb.width)
        return bitmap
    }
}
