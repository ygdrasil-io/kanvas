package org.skia.testing

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Phase 0 harness. `runGmTest` and `compareBitmaps` arrive in Phase 1
 * once SkBitmap / SkCanvas can rasterize.
 */
public object TestUtils {

    private const val REFERENCE_DIR: String = "original-888"

    /**
     * Load a reference image from `kanvas-skia/src/test/resources/original-888/{name}.png`
     * (wired through `sourceSets.test.resources.srcDir("../kanvas/src/test/resources")`).
     */
    public fun loadReferenceImage(name: String): BufferedImage? {
        val path = "$REFERENCE_DIR/$name.png"
        val url = TestUtils::class.java.classLoader.getResource(path)
            ?: return null.also { println("Reference image not found: $path") }
        return ImageIO.read(File(url.toURI()))
    }

    /**
     * Save a BufferedImage to `kanvas-skia/build/debug-images/{name}.png`.
     */
    public fun saveDebugImage(image: BufferedImage, name: String) {
        val dir = File("build/debug-images").apply { mkdirs() }
        ImageIO.write(image, "png", File(dir, "$name.png"))
    }

    /**
     * Pixel-exact similarity between two BufferedImages, in percent (0..100).
     */
    public fun compareImages(a: BufferedImage, b: BufferedImage): Double {
        if (a.width != b.width || a.height != b.height) return 0.0
        var matching = 0
        val total = a.width * a.height
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (a.getRGB(x, y) == b.getRGB(x, y)) matching++
            }
        }
        return matching.toDouble() / total.toDouble() * 100.0
    }
}
