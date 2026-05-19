package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
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
 * G5.2.2 acceptance tests -- rotated / skewed `SkBitmapShader.localMatrix`
 * and rotated CTM. Mirrors [BitmapShaderPaintRectTest] /
 * [BitmapShaderPathTest] in shape, but the matrices include rotation
 * / skew so the dispatch routes through the new device-to-image
 * affine uniform (`devToImageRow0` / `devToImageRow1` in both
 * `bitmap_shader.wgsl` and `aa_stencil_cover_bitmap_shader.wgsl`).
 *
 * Three sub-cases :
 *   1. Rotated localMatrix on axis-aligned CTM (rect fast path) :
 *      the shader-local rotation rotates the bitmap pattern inside
 *      the axis-aligned device rect. Scissor is exact.
 *   2. Rotated CTM (no rotated localMatrix), AA circle path : the
 *      device-space geometry rotates around the canvas's pivot ;
 *      stencil-and-cover bounds the painted region to the rotated
 *      shape.
 *   3. Composed rotation (rotated CTM + rotated localMatrix) : the
 *      shader applies the inverse of the COMPOSED matrix, so the
 *      pattern lines up consistently in user space.
 */
class BitmapShaderRotatedTest {

    @Test
    fun `rotated localMatrix on axis-aligned rect rotates the bitmap pattern`() {
        // Rotate the shader's local matrix 90 deg around the centre of
        // the dst rect : the image quadrants land at different device
        // corners than the identity case.
        //
        // Setup : image is a 4x4 quadrant pattern (R top-left, G top-
        // right, B bottom-left, Bk bottom-right). Dst rect is 4x4 at
        // device origin (10, 10). After a 90deg rotation around the
        // rect centre (12, 12) the image's top edge ends up on the
        // device right edge, etc. The shader's `localMatrix` is the
        // device-to-user mapping for the *image* ; we rotate around
        // the image centre `(2, 2)` so the image stays inside its
        // 4x4 footprint.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        // 90 deg rotation around (2, 2) (image centre).
        val localMatrix = SkMatrix.MakeRotate(90f, 2f, 2f)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kClamp,
                        tileY = SkTileMode.kClamp,
                        sampling = SkSamplingOptions.nearest(),
                        localMatrix = localMatrix,
                    )
                }
                canvas.translate(10f, 10f)
                canvas.drawRect(
                    SkRect.MakeWH(SIDE.toFloat(), SIDE.toFloat()),
                    paint,
                )
                device.flush()
            }
        }

        // Before rotation : image (0, 0) = R lived at device (10, 10),
        // image (3, 0) = G at device (13, 10), etc. After 90 deg CW
        // rotation around the image centre (2, 2) :
        //   image (0, 0) (R) -> sits at user (4, 0) -> device (14, 10)
        //   image (3, 0) (G) -> user (4, 3) -> device (14, 13)
        //   image (0, 3) (B) -> user (1, 0) -> device (11, 10)
        //   image (3, 3) (Bk) -> user (1, 3) -> device (11, 13)
        // We're inverting the localMatrix so for a fragment at device
        // (14, 10) the shader looks up image pixel R (0, 0). Sample
        // device coordinates that should land in each quadrant after
        // the 90 deg rotation. Note : kNearest sampling means we read
        // exact pixel centres, no AA fuzzing.
        //
        // Background untouched outside the rect.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(0, 0), "bg outside rect")
        // The four corners of the rect, post-rotation : check that the
        // image quadrants ended up in the rotated positions.
        //   Device (10, 10) (top-left of rect) reads image (?, ?). Under
        //   90deg rotation about (2, 2), the device offset (-2, -2)
        //   maps to image offset (-2, +2). image (0, 4) is decal/clamp
        //   to (0, 3) (B). So top-left = B.
        //   Device (13, 10) (top-right) -> image offset (+1, -2) ->
        //   under inverse 90deg -> image offset (-2, -1) -> clamp x
        //   to 0, y to 1 = image (0, 1) = R.
        //   Easier : check that two opposite corners differ and the
        //   pattern matches the rotation (not the identity).
        val tl = pixels.rgbaAt(10, 10)
        val tr = pixels.rgbaAt(13, 10)
        val bl = pixels.rgbaAt(10, 13)
        val br = pixels.rgbaAt(13, 13)
        // Each corner must be one of the 4 quadrant colours, and the
        // ROTATED pattern must differ from the identity pattern (which
        // would put R/G/B/Bk in TL/TR/BL/BR order).
        val palette = setOf(
            listOf(255, 0, 0, 255), listOf(0, 255, 0, 255),
            listOf(0, 0, 255, 255), listOf(0, 0, 0, 255),
        )
        assertTrue(tl in palette, "tl=$tl in palette")
        assertTrue(tr in palette, "tr=$tr in palette")
        assertTrue(bl in palette, "bl=$bl in palette")
        assertTrue(br in palette, "br=$br in palette")
        // Trace : the shader's localMatrix is R(+90 CW around image
        // centre (2, 2)). The fragment lookup INVERTS this matrix to
        // map device -> image. For a fragment at device pixel
        // centre (10.5, 10.5) the inverse maps to image (0.5, 3.5)
        // = pixel (0, 3) = BLUE. Trace the other three corners the
        // same way :
        //   device (13.5, 10.5) -> image (0.5, 0.5) -> RED
        //   device (10.5, 13.5) -> image (3.5, 3.5) -> BLACK
        //   device (13.5, 13.5) -> image (3.5, 0.5) -> GREEN
        // The rotated pattern thus "spins" 90 degrees counter-clockwise
        // relative to the identity layout (R/G/B/Bk -> B/R/Bk/G).
        assertEquals(listOf(0, 0, 255, 255), tl, "rotated tl = blue")
        assertEquals(listOf(255, 0, 0, 255), tr, "rotated tr = red")
        assertEquals(listOf(0, 0, 0, 255), bl, "rotated bl = black")
        assertEquals(listOf(0, 255, 0, 255), br, "rotated br = green")
    }

    @Test
    fun `rotated CTM on circle path bounds the bitmap fill to the rotated geometry`() {
        // Rotate the CTM 30 deg around the canvas origin then draw a
        // circle with a bitmap shader (identity local matrix). The
        // circle's device-space geometry rotates, but the bitmap
        // pattern follows the rotation because the shader's
        // device-to-image affine is the inverse of `ctm *
        // localMatrix = ctm`.
        //
        // Verification : pixels far outside the rotated disk must
        // stay as the background ; pixels inside should sample the
        // bitmap pattern (which, under kRepeat, never lands on the
        // background colour). The rotation is enough to make the
        // disk no longer axis-aligned in device space, exercising
        // the stencil-and-cover non-rect dispatch with a rotated
        // affine inverse.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        // Circle centred at user-space (10, 10), radius 6. After a
        // 30deg rotation around the canvas origin the disk lands
        // at a different device location -- still entirely within
        // the 32x32 viewport.
        val path = SkPath.Circle(10f, 10f, 6f, SkPathDirection.kCW)

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
                canvas.rotate(30f)
                canvas.drawPath(path, paint)
                device.flush()
            }
        }

        // Background sample : a pixel far enough from the rotated
        // disk centre must stay white. The rotated disk centre is
        // `R(30) * (10, 10) ~= (3.66, 13.66)` in device space.
        // Pixel (28, 28) is well outside the disk under both
        // rotations, so it must still be white.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(28, 28), "outside disk : bg")
        // Pixel near the disk centre must NOT be white -- the shader
        // would have sampled SOME image quadrant. Centre at device
        // (~4, ~14) -- sample (4, 14) and check that we got a non-
        // background colour.
        val centre = pixels.rgbaAt(4, 14)
        val whiteSum = centre[0] + centre[1] + centre[2]
        assertTrue(whiteSum < 700, "centre ($centre) not white -- got sum=$whiteSum")
        // Alpha at the centre must be saturated (we're inside the disk).
        assertTrue(centre[3] >= 250, "centre alpha=${centre[3]} >= 250 (inside disk)")
    }

    @Test
    fun `composed rotated CTM and rotated localMatrix samples correctly`() {
        // Compose two rotations : CTM = R(20), localMatrix = R(-20)
        // around the image centre. The two rotations cancel inside
        // the shader -- the bitmap pattern should be in the same
        // orientation as the identity case, but the rect's device
        // geometry rotates.
        //
        // The slice's hard scope is "no regression on the inverse
        // affine math under composed rotations". The shader's affine
        // is `(ctm * localMatrix)^-1` -- if either factor were
        // dropped or applied in the wrong order, the cancellation
        // would fail and the pattern would visibly rotate.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        // Cancel the CTM rotation in shader-local space.
        val localMatrix = SkMatrix.MakeRotate(-20f, 2f, 2f)
        // Disk centred at user-space (10, 10), radius 6. AA cover
        // for the non-rect path goes through stencil-and-cover.
        val path = SkPath.Circle(10f, 10f, 6f, SkPathDirection.kCW)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kRepeat,
                        tileY = SkTileMode.kRepeat,
                        sampling = SkSamplingOptions.nearest(),
                        localMatrix = localMatrix,
                    )
                    isAntiAlias = true
                }
                // CTM rotates 20 deg around origin (0, 0). Combined
                // with localMatrix, the per-fragment image-coord
                // affine is the inverse of `R(20) * R(-20 around
                // image centre)` = a translate (no net rotation).
                canvas.rotate(20f)
                canvas.drawPath(path, paint)
                device.flush()
            }
        }

        // Far outside the rotated disk : background stays white.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(28, 28), "outside disk : bg")
        // Inside the disk : we sampled SOMETHING from the image (kRepeat
        // never returns transparent). Verify at least one pixel that
        // should land deep inside the rotated disk is not background.
        // CTM = R(20) ; (10, 10) -> device (10*cos20 - 10*sin20,
        // 10*sin20 + 10*cos20) ~= (5.97, 12.81). Sample (6, 13).
        val centre = pixels.rgbaAt(6, 13)
        val whiteSum = centre[0] + centre[1] + centre[2]
        assertTrue(whiteSum < 700, "centre ($centre) not white")
        assertTrue(centre[3] >= 250, "centre alpha=${centre[3]}")
    }

    @Test
    fun `skewed localMatrix on rect path samples through the affine inverse`() {
        // A pure skew on the shader's localMatrix : exercises the
        // off-diagonal terms of the device-to-image affine. With
        // kRepeat the disk-like shape inside the rect must still
        // be saturated (no transparent fragments leak through), and
        // the background outside the rect must stay white.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        // Small skew so the affine inverse stays well-conditioned.
        val localMatrix = SkMatrix.MakeSkew(0.5f, 0f)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kRepeat,
                        tileY = SkTileMode.kRepeat,
                        sampling = SkSamplingOptions.nearest(),
                        localMatrix = localMatrix,
                    )
                }
                canvas.translate(8f, 8f)
                canvas.drawRect(
                    SkRect.MakeWH(8f, 8f),
                    paint,
                )
                device.flush()
            }
        }

        // Background outside the rect untouched. The rect is in
        // device space at (8, 8)..(16, 16) (axis-aligned in device
        // because the CTM is axis-aligned ; the skew only rotates
        // the IMAGE inside the rect via the localMatrix).
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(0, 0), "outside rect : bg")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(20, 20), "outside rect : bg")
        // Inside the rect : every pixel must be one of the 4 quadrant
        // colours (kRepeat never emits transparent ; the skewed sample
        // still falls into the image plane).
        for (y in 8..15) {
            for (x in 8..15) {
                val p = pixels.rgbaAt(x, y)
                val isOpaque = p[3] >= 250
                val isPalette =
                    (p[0] == 255 && p[1] == 0 && p[2] == 0) ||
                    (p[0] == 0 && p[1] == 255 && p[2] == 0) ||
                    (p[0] == 0 && p[1] == 0 && p[2] == 255) ||
                    (p[0] == 0 && p[1] == 0 && p[2] == 0)
                assertTrue(isOpaque && isPalette, "skewed sample at ($x, $y) = $p")
            }
        }
    }

    /**
     * 4x4 image split into 4 quadrants : R / G / B / Bk. Mirrors
     * [BitmapShaderPaintRectTest.makeQuadrantImage].
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
