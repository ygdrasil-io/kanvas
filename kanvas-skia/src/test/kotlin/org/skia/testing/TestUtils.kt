package org.skia.testing

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.tests.GM
import java.awt.image.BufferedImage
import java.awt.image.DataBufferUShort
import java.io.File
import javax.imageio.ImageIO

public object TestUtils {

    private const val REFERENCE_DIR: String = "original-888"

    /**
     * Render a GM into a freshly allocated bitmap of the GM's preferred size,
     * filled white before `onDraw` runs. Mirrors Skia's `gm.cpp` test runner.
     */
    public fun runGmTest(gm: GM): SkBitmap {
        val size = gm.size()
        val bitmap = SkBitmap(size.width, size.height)
        bitmap.eraseColor(SK_ColorWHITE)
        val canvas = SkCanvas(bitmap)
        gm.draw(canvas)
        return bitmap
    }

    public fun loadReferenceImage(name: String): BufferedImage? {
        val path = "$REFERENCE_DIR/$name.png"
        val url = TestUtils::class.java.classLoader.getResource(path) ?: return null
        return ImageIO.read(File(url.toURI()))
    }

    public fun loadReferenceBitmap(name: String): SkBitmap? =
        loadReferenceImage(name)?.let { bufferedImageToBitmap(it) }

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
     * A pixel matches when every channel diff is within `tolerance`.
     *
     * Skia GM references ship with the `Google/Skia` ICC profile and a working
     * colour space that maps sRGB primaries non-trivially: pure `0xFF0000FF`
     * lands as `~0xFF2B0DF2` in the reference, with a per-channel offset of up
     * to ~64 even when rasterisation is structurally correct. Pixel-exact
     * compare on these references therefore caps far below 100%; upstream
     * Skia gold uses a tolerance of its own. Default 64 here matches the
     * worst-case channel shift observed on `bigrect.png`.
     */
    public fun compareBitmaps(a: SkBitmap, b: SkBitmap, tolerance: Int = 0): Double {
        if (a.width != b.width || a.height != b.height) return 0.0
        val total = a.width * a.height
        var matching = 0
        for (i in 0 until total) {
            val pa = a.pixels[i]; val pb = b.pixels[i]
            if (pa == pb) { matching++; continue }
            if (tolerance == 0) continue
            val dA = kotlin.math.abs(((pa ushr 24) and 0xFF) - ((pb ushr 24) and 0xFF))
            val dR = kotlin.math.abs(((pa ushr 16) and 0xFF) - ((pb ushr 16) and 0xFF))
            val dG = kotlin.math.abs(((pa ushr 8) and 0xFF) - ((pb ushr 8) and 0xFF))
            val dB = kotlin.math.abs((pa and 0xFF) - (pb and 0xFF))
            if (maxOf(dA, dR, dG, dB) <= tolerance) matching++
        }
        return matching.toDouble() / total.toDouble() * 100.0
    }

    public fun bitmapToBufferedImage(bitmap: SkBitmap): BufferedImage {
        val img = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, bitmap.width, bitmap.height, bitmap.pixels, 0, bitmap.width)
        return img
    }

    public fun bufferedImageToBitmap(img: BufferedImage): SkBitmap {
        val bitmap = SkBitmap(img.width, img.height)
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
