package org.skia.testing

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
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
     * `onDraw` runs. Mirrors Skia's `gm.cpp` test runner with `--config 8888`
     * and the "DM unified Rec.2020" working color space.
     *
     * The bg color is sRGB-encoded (per the SkColor convention). When it is
     * black or white — both profile-invariant — a raw fill is bit-identical
     * to going through the device. ScaledRectsGM-style non-trivial bg
     * colors will need an xformed eraseColor; we add it when a GM in scope
     * demands it.
     */
    public fun runGmTest(gm: GM): SkBitmap {
        val size = gm.size()
        val bitmap = SkBitmap(size.width, size.height, DM_REFERENCE_COLOR_SPACE)
        bitmap.eraseColor(gm.bgColor())
        val canvas = SkCanvas(bitmap)
        gm.draw(canvas)
        return bitmap
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
        val total = a.width * a.height
        var matching = 0
        var maxA = 0; var maxR = 0; var maxG = 0; var maxB = 0
        var sumA = 0L; var sumR = 0L; var sumG = 0L; var sumB = 0L
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
        img.setRGB(0, 0, bitmap.width, bitmap.height, bitmap.pixels, 0, bitmap.width)
        return img
    }

    public fun bufferedImageToBitmap(
        img: BufferedImage,
        colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
    ): SkBitmap {
        val bitmap = SkBitmap(img.width, img.height, colorSpace)
        val raster = img.raster
        val buf = raster.dataBuffer
        // Skia GM PNGs ship an embedded ICC profile ("Google/Skia") that Java
        // applies during `getRGB` / `drawImage`, distorting pure colors. Read
        // the raw raster samples instead, treating them as straight sRGB.
        val numBands = raster.numBands
        if (buf is DataBufferUShort && numBands >= 3) {
            val pixel = IntArray(numBands)
            for (y in 0 until img.height) {
                for (x in 0 until img.width) {
                    raster.getPixel(x, y, pixel)
                    val r = (pixel[0] ushr 8) and 0xFF
                    val g = (pixel[1] ushr 8) and 0xFF
                    val b = (pixel[2] ushr 8) and 0xFF
                    val a = if (numBands >= 4) (pixel[3] ushr 8) and 0xFF else 0xFF
                    bitmap.pixels[y * img.width + x] =
                        (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            return bitmap
        }
        // Fallback: 8-bit images without an ICC profile load fine via getRGB.
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
