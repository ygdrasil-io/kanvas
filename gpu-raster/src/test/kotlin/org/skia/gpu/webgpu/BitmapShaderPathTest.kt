package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode

/**
 * G5.2.1 acceptance tests -- `paint.shader is SkBitmapShader` routing
 * on a non-rect AA path (circle), exercising all 4 SkTileMode values.
 * Mirrors [BitmapShaderPaintRectTest] in shape, but the geometry
 * routes through the AA stencil-and-cover bitmap-shader pipeline
 * (`aa_stencil_cover_bitmap_shader.wgsl`) instead of the rect-only
 * full-screen-triangle fast path.
 *
 * Setup : a 4x4 quadrant image (R / G / B / Bk) with identity local
 * matrix (image pixel (0, 0) lines up with device (0, 0)) painted
 * through a circle large enough to contain the image footprint AND
 * extend beyond into tile-mode territory. The CTM is identity ; the
 * gate is `paint.shader is SkBitmapShader && paint.isAntiAlias &&
 * ctm.isAxisAligned && shader.localMatrix.isAxisAligned && !path.isRect`.
 */
class BitmapShaderPathTest {

    @Test
    fun `kClamp bitmap shader on a circle path samples image pixels inside the disk`() {
        // Disk centred at (8, 8) radius 12. Image footprint (0..4, 0..4)
        // sits well inside the disk. With identity local matrix the four
        // quadrant centres land at (1, 1) / (3, 1) / (1, 3) / (3, 3) in
        // device space. Past the image footprint the kClamp tile mode
        // pins to the matching edge pixel of the image.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        val path = SkPath.Circle(8f, 8f, 12f, SkPathDirection.kCW)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kClamp,
                        tileY = SkTileMode.kClamp,
                        sampling = SkSamplingOptions.nearest(),
                    )
                    isAntiAlias = true
                }
                canvas.drawPath(path, paint)
                device.flush()
            }
        }

        // Inside the image footprint (and inside the disk) : the four
        // quadrant centres paint as expected.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(1, 1), "image (1, 1) red")
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(3, 1), "image (3, 1) green")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(1, 3), "image (1, 3) blue")
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(3, 3), "image (3, 3) black")
        // Past the image footprint, inside the disk : kClamp pins to the
        // matching edge pixel (column 3 = green/black, row 3 = blue/black).
        // (10, 3) -> image (3, 3) = black (clamp x past edge).
        // (3, 10) -> image (3, 3) = black (clamp y past edge).
        // (10, 10) -> image (3, 3) = black (clamp both axes).
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(10, 3), "clamp right : image (3, *)")
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(3, 10), "clamp bottom : image (*, 3)")
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(10, 10), "clamp corner : image (3, 3)")
        // Outside the disk -> white background untouched.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(31, 31), "outside disk : bg")
    }

    @Test
    fun `kRepeat bitmap shader on a circle path tiles the image across the disk`() {
        // Same disk. With kRepeat the 4x4 image tiles across the disk :
        // device pixel (x, y) inside the disk samples image pixel
        // (x mod 4, y mod 4). Red corners land at every SIDE-aligned
        // device pixel.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        val path = SkPath.Circle(8f, 8f, 12f, SkPathDirection.kCW)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kRepeat,
                        tileY = SkTileMode.kRepeat,
                        sampling = SkSamplingOptions.nearest(),
                    )
                    isAntiAlias = true
                }
                canvas.drawPath(path, paint)
                device.flush()
            }
        }

        // Tile (0, 0) at (1, 1) -> image (1, 1) -> red.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(1, 1), "tile (0, 0) red")
        // Tile (1, 0) wraps : (5, 1) -> image (1, 1) -> red.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(5, 1), "tile (1, 0) red wraps x")
        // Tile (0, 1) wraps : (1, 5) -> image (1, 1) -> red.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(1, 5), "tile (0, 1) red wraps y")
        // Outside disk : background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(31, 31), "outside disk : bg")
    }

    @Test
    fun `kMirror bitmap shader on a circle path reflects the image across the disk`() {
        // Same disk. With kMirror the second tile reflects the image :
        // device pixel (SIDE + dx, y) samples image (SIDE - 1 - dx, y).
        // At (5, 1) -> image (2, 1) -> green quadrant.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        val path = SkPath.Circle(8f, 8f, 12f, SkPathDirection.kCW)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kMirror,
                        tileY = SkTileMode.kMirror,
                        sampling = SkSamplingOptions.nearest(),
                    )
                    isAntiAlias = true
                }
                canvas.drawPath(path, paint)
                device.flush()
            }
        }

        // First tile at (1, 1) : image (1, 1) -> red.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(1, 1), "tile (0, 0) red")
        // Mirror x at (5, 1) : image (2, 1) -> green.
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(5, 1), "mirror x : image (2, 1) green")
        // Mirror y at (1, 5) : image (1, 2) -> blue.
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(1, 5), "mirror y : image (1, 2) blue")
        // Outside disk : background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(31, 31), "outside disk : bg")
    }

    @Test
    fun `kDecal bitmap shader on a circle path transparent outside image footprint`() {
        // Same disk. With kDecal the per-axis check emits (0, 0, 0, 0)
        // outside the image footprint ; under default kSrcOver the
        // white background shows through. Inside the footprint the
        // quadrants paint as usual.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        val path = SkPath.Circle(8f, 8f, 12f, SkPathDirection.kCW)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kDecal,
                        tileY = SkTileMode.kDecal,
                        sampling = SkSamplingOptions.nearest(),
                    )
                    isAntiAlias = true
                }
                canvas.drawPath(path, paint)
                device.flush()
            }
        }

        // Inside image footprint : the four quadrants paint as expected.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(1, 1), "image (1, 1) red")
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(3, 1), "image (3, 1) green")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(1, 3), "image (1, 3) blue")
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(3, 3), "image (3, 3) black")
        // Past the image footprint, inside the disk : decal -> bg shows.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(10, 3), "decal x : bg")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(3, 10), "decal y : bg")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(10, 10), "decal corner : bg")
        // Outside the disk -> background untouched.
        val outside = pixels.rgbaAt(31, 31)
        assertTrue(outside[0] >= 230, "outside.R near white, got ${outside[0]}")
    }

    /**
     * 4x4 image split into 4 quadrants : R / G / B / Bk. Same helper
     * as [BitmapShaderPaintRectTest.makeQuadrantImage] -- duplicated
     * here to keep the two test classes independent.
     */
    private fun makeQuadrantImage(side: Int): SkImage {
        val bitmap = SkBitmap(side, side)
        val half = side / 2
        for (y in 0 until side) {
            for (x in 0 until side) {
                val color = when {
                    x < half && y < half -> SK_ColorRED
                    x >= half && y < half -> SK_ColorGREEN
                    x < half && y >= half -> SK_ColorBLUE
                    else -> SK_ColorBLACK
                }
                bitmap.setPixel(x, y, color)
            }
        }
        return SkImage.Make(bitmap)
    }

    private fun ByteArray.rgbaAt(x: Int, y: Int): List<Int> {
        val i = (y * W + x) * 4
        return listOf(
            this[i].toInt() and 0xFF,
            this[i + 1].toInt() and 0xFF,
            this[i + 2].toInt() and 0xFF,
            this[i + 3].toInt() and 0xFF,
        )
    }

    private companion object {
        const val W: Int = 32
        const val H: Int = 32
        const val SIDE: Int = 4
    }
}
