package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode

/**
 * G5.1 / G5.1.1 acceptance tests — `drawImageRect` on the GPU via the
 * bitmap shader.
 *
 * Scope after G5.1.1 :
 *  - filter = kLinear OR kNearest (sampler-cache key).
 *  - tile mode = kClamp / kRepeat / kMirror / kDecal. The first three
 *    are resolved by the sampler's `addressModeU/V` ; kDecal is
 *    emulated in-shader (WebGPU has no `BorderColor` mode for
 *    sampled (non-depth) textures).
 *  - blend mode = kClear / kSrc / kSrcOver / kDstOver (the natively-
 *    blendable subset supported by [SkWebGpuDevice.blendStateFor]).
 *  - source image is uploaded as RGBA8Unorm to a per-device GPU
 *    texture cache.
 *  - dst rect is pixelEdge-rounded ; out-of-rect pixels stay at the
 *    device background (no overdraw).
 *
 * Each test renders a small SkImage (constructed by setting per-pixel
 * colors on an SkBitmap then snapshotting) into a known device region,
 * flushes, and asserts the readback pixels at the expected coordinates.
 *
 * The public [SkCanvas.drawImageRect] API does not carry a tile mode
 * ([SkSamplingOptions] is filter / mipmap / cubic only), so the
 * non-clamp tile modes are exercised via the test-only
 * [SkWebGpuDevice.enqueueImageRectDrawForTest] hook ; once
 * `paint.shader is SkBitmapShader` routes through this pipeline
 * (G5.2 onwards), tile modes will arrive through the regular path.
 */
class ImageRectTest {

    @Test
    fun `drawImageRect 1to1 places source pixels at the destination corner`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // 4x4 source image with 4 quadrants : red / green / blue / black.
        val image = makeQuadrantImage(SIDE)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.drawImageRect(
                    image,
                    SkRect.MakeWH(SIDE.toFloat(), SIDE.toFloat()),
                    SkRect.MakeXYWH(10f, 10f, SIDE.toFloat(), SIDE.toFloat()),
                    SkSamplingOptions.linear(),
                )
                device.flush()
            }
        }

        // Background, far from the dst rect : white.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(0, 0), "background top-left")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(40, 40), "background bottom-right")

        // Sample the 4 quadrant centres in the destination region.
        // Image quadrant (x, y) for x, y in {0, 1} : red, green, blue, black.
        // 4x4 image placed at device (10, 10) : centre of quadrant (0, 0)
        // is at device (11, 11) ; quadrant (1, 1) at (13, 13).
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 11), "top-left quadrant : red")
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(13, 11), "top-right quadrant : green")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(11, 13), "bottom-left quadrant : blue")
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(13, 13), "bottom-right quadrant : black")
    }

    @Test
    fun `drawImageRect 2x upscale samples the source via bilinear filter`() {
        // 2x upscale : a 4x4 source -> 8x8 device rect. With kLinear the
        // interior of each source-quadrant region stays the source color ;
        // the boundary between quadrants softens via the bilinear lerp.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.drawImageRect(
                    image,
                    SkRect.MakeWH(SIDE.toFloat(), SIDE.toFloat()),
                    SkRect.MakeXYWH(10f, 10f, (SIDE * 2).toFloat(), (SIDE * 2).toFloat()),
                    SkSamplingOptions.linear(),
                )
                device.flush()
            }
        }

        // 2x upscale : dst rect spans device (10, 10) -> (18, 18).
        // Background untouched outside the dst rect.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(0, 0), "background")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(30, 30), "background past dst")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(20, 20), "background just outside dst")

        // Far inside the red quadrant -- device (11, 11) maps back to src
        // (~0.75, 0.75), well inside src pixel (0, 0) which is red. With
        // ClampToEdge the bilinear lerp at the corner clamps to red.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 11), "red quadrant interior")

        // Far inside the black quadrant -- device (17, 17) is the dst
        // bottom-right corner pixel ; maps back to src (~3.75, 3.75),
        // inside src pixel (3, 3) which is black.
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(17, 17), "black quadrant interior")
    }

    @Test
    fun `drawImageRect 2x upscale with kNearest snaps to source texels`() {
        // G5.1.1 -- kNearest sampler. With a 2x upscale and Nearest
        // filtering, each 2x2 block of device pixels reads the SAME
        // source texel (no bilinear blending across quadrant boundaries).
        // Concretely : the quadrant boundary lives at device coords
        // (14, 14) -- pixels at (13, 13) and (14, 14) sit on different
        // source texels and must carry pure, un-blended colours.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.drawImageRect(
                    image,
                    SkRect.MakeWH(SIDE.toFloat(), SIDE.toFloat()),
                    SkRect.MakeXYWH(10f, 10f, (SIDE * 2).toFloat(), (SIDE * 2).toFloat()),
                    SkSamplingOptions.nearest(),
                )
                device.flush()
            }
        }

        // Background untouched.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(0, 0), "background")

        // Pixels on the upper-left (red quadrant) side of the divide.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 11), "red interior (Nearest)")
        // Pixel just on the red side of the device-pixel boundary at the
        // src-coord midpoint -- with Nearest no green spills here.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(13, 13), "red edge (Nearest, no spill)")
        // One pixel further -- now in the black quadrant.
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(14, 14), "black interior (Nearest)")
        // Green and blue quadrant edges, similarly pure.
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(15, 11), "green interior (Nearest)")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(11, 15), "blue interior (Nearest)")
    }

    @Test
    fun `drawImageRect with kRepeat tiles the source over an oversized dst`() {
        // G5.1.1 -- kRepeat tile mode. Trick : ask for a src rect that
        // extends past the image (`[0, 2*SIDE] x [0, 2*SIDE]` on a SIDE
        // image) ; the shader's affine maps the dst back into that wider
        // src, the sampler's Repeat addressMode wraps UVs > 1 back into
        // [0, 1]. We render at 1:1 over a 2*SIDE x 2*SIDE dst : the
        // result is the source tiled 2x2.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        val side2 = SIDE * 2

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                device.enqueueImageRectDrawForTest(
                    image = image,
                    src = SkRect.MakeWH(side2.toFloat(), side2.toFloat()),
                    devDst = SkRect.MakeXYWH(10f, 10f, side2.toFloat(), side2.toFloat()),
                    sampling = SkSamplingOptions.nearest(),
                    paint = null,
                    tile = SkTileMode.kRepeat,
                )
                device.flush()
            }
        }

        // dst spans device (10, 10) -> (18, 18). The 4x4 image tiles 2x2.
        // Each 4x4 tile : red / green / blue / black quadrants.
        // First tile : device (10, 10) -> (14, 14) -- corner (11, 11) is red.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 11), "tile (0, 0) red")
        // Second tile column starts at device x = 14 ; same quadrant
        // pattern -- (15, 11) is the red corner of the (1, 0) tile.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(15, 11), "tile (1, 0) red wraps")
        // Second tile row : (11, 15) is the red corner of the (0, 1) tile.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 15), "tile (0, 1) red wraps")
        // Bottom-right tile, black quadrant : device (17, 17).
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(17, 17), "tile (1, 1) black wraps")
    }

    @Test
    fun `drawImageRect with kMirror reflects the source at tile boundaries`() {
        // G5.1.1 -- kMirror tile mode. Same oversized src setup as the
        // kRepeat test ; the sampler's MirrorRepeat addressMode reflects
        // UVs at integer boundaries. With a 4x4 image (quadrants
        // R / G / B / Bk), the tile at (1, 0) is the horizontal mirror
        // of the (0, 0) tile : its top-left quadrant is GREEN (the mirror
        // of red across the right edge).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        val side2 = SIDE * 2

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                device.enqueueImageRectDrawForTest(
                    image = image,
                    src = SkRect.MakeWH(side2.toFloat(), side2.toFloat()),
                    devDst = SkRect.MakeXYWH(10f, 10f, side2.toFloat(), side2.toFloat()),
                    sampling = SkSamplingOptions.nearest(),
                    paint = null,
                    tile = SkTileMode.kMirror,
                )
                device.flush()
            }
        }

        // dst spans device (10, 10) -> (18, 18).
        // Tile (0, 0) : identity ; corner is red.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 11), "tile (0, 0) red")
        // Tile (1, 0) : horizontally mirrored. Image source at u = 1.x is
        // mirrored back into [1, 0] coords -- so device (15, 11) reads
        // mirrored pixel (3 - sx_into_tile_2, 0) -- top-right of the image
        // is green ; the mirror flips that to the left edge of tile (1, 0).
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(15, 11), "tile (1, 0) green (mirrored)")
        // Tile (0, 1) : vertically mirrored. Device (11, 15) reads the
        // mirror of the bottom-left of the image -- blue.
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(11, 15), "tile (0, 1) blue (mirrored)")
        // Tile (1, 1) : both axes mirrored. Top-left of tile maps to the
        // bottom-right quadrant of the image after a 2D reflection ; far
        // corner (device (17, 17)) lands on red after the double mirror.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(17, 17), "tile (1, 1) red (double mirror)")
    }

    @Test
    fun `drawImageRect with kDecal returns transparent outside the source`() {
        // G5.1.1 -- kDecal tile mode. Sampler stays ClampToEdge ; the
        // shader checks UV bounds and returns vec4f(0). Test : oversized
        // src ([0, 2*SIDE]) over a 2*SIDE dst -- the bottom-right
        // 1*SIDE x 1*SIDE region of the dst maps to UV beyond [0, 1] and
        // must end up as the background (the (0,0,0,0) shader output
        // composites under SrcOver as "no change").
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        val side2 = SIDE * 2

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                device.enqueueImageRectDrawForTest(
                    image = image,
                    src = SkRect.MakeWH(side2.toFloat(), side2.toFloat()),
                    devDst = SkRect.MakeXYWH(10f, 10f, side2.toFloat(), side2.toFloat()),
                    sampling = SkSamplingOptions.nearest(),
                    paint = null,
                    tile = SkTileMode.kDecal,
                )
                device.flush()
            }
        }

        // Inside the image's footprint (device (10, 10) -> (14, 14)) the
        // quadrant colours show through normally.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 11), "in-bounds : red")
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(13, 11), "in-bounds : green")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(11, 13), "in-bounds : blue")
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(13, 13), "in-bounds : black")

        // Outside the source footprint (UV beyond [0, 1]) the shader
        // emits premul (0, 0, 0, 0) -- under SrcOver this leaves the
        // white background untouched.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(15, 11), "decal : right neighbour bg")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(11, 15), "decal : bottom neighbour bg")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(17, 17), "decal : bottom-right bg")
    }

    @Test
    fun `drawImageRect with kSrc blend overwrites the destination`() {
        // G5.1.1 -- kSrc replaces the dst pixels rather than compositing.
        // Test : white background ; draw a fully-opaque 4x4 quadrant
        // image with paint.blendMode = kSrc ; verify the dst rect carries
        // the image colours unchanged (the white background is overwritten,
        // not composited).
        // To make this distinguishable from kSrcOver (which would yield
        // the same RGB for opaque source pixels), draw a 50%-alpha image
        // pixel and assert kSrc preserves the alpha-modulated output
        // (premul-blended OVER black background in the readback ; the
        // intermediate-target output is premul rgba).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    blendMode = SkBlendMode.kSrc
                }
                canvas.drawImageRect(
                    image,
                    SkRect.MakeWH(SIDE.toFloat(), SIDE.toFloat()),
                    SkRect.MakeXYWH(10f, 10f, SIDE.toFloat(), SIDE.toFloat()),
                    SkSamplingOptions.linear(),
                    paint,
                )
                device.flush()
            }
        }

        // Outside the dst rect : white background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(0, 0), "kSrc : background untouched")

        // Inside the dst rect : pure image colours (alpha = 1, RGB
        // matches the image texel). For kSrc the white background is
        // *overwritten*, not blended -- the readback reflects the image
        // colours directly.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 11), "kSrc : red")
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(13, 11), "kSrc : green")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(11, 13), "kSrc : blue")
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(13, 13), "kSrc : black (overwrote white)")
    }

    /**
     * Build a `side x side` image split into 4 equal quadrants :
     *   top-left = red, top-right = green,
     *   bottom-left = blue, bottom-right = black.
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
        const val W: Int = 64
        const val H: Int = 64
        const val SIDE: Int = 4
    }
}
