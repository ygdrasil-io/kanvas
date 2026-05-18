package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkPoint
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkTileMode

/**
 * G4.4.3 acceptance tests -- conical (two-point) gradient fill of a
 * non-rect path (circle) on GPU. Mirror of [ConicalGradientRectTest]
 * (rect-only G4.4 / G4.4.1) but the geometry routes through the AA
 * stencil-and-cover conical-gradient pipeline instead of the rect-only
 * fast path.
 *
 * Two formula families are covered :
 *   - kRadial (concentric circles, c0 == c1) : routed through
 *     [aa_stencil_cover_conical_gradient.wgsl] ;
 *   - focal-inside well-behaved (focal point inside the end circle,
 *     fR1 > 1, not focal-on-circle) : routed through
 *     [aa_stencil_cover_conical_focal_gradient.wgsl].
 *
 * Tile mode is locked to kClamp at this slice (mirrors the rect-only
 * G4.4 / G4.4.1 dispatch gate). When G4.4.2 widens the rect-only path
 * to all 4 tile modes, the non-rect gate here can be opened in lockstep
 * (the shaders already wire up all 8 entry points : 2 sides x 4 tile
 * modes).
 *
 * Sample point expectations are wide bands -- the AA edge coverage +
 * present-pass colorspace transform shift channel values by up to a
 * few dozen LSBs across the gradient, mirroring the rect-only test
 * convention.
 */
class ConicalGradientPathTest {

    // ---- kRadial sub-case (G4.4.3) ------------------------------------

    @Test
    fun `kRadial kClamp conical on a circle path interpolates between inner and outer radii`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Disk centred at (32, 32) radius 28 covers most of the 64x64
        // surface. Conical kRadial gradient with centre (32, 32),
        // startRadius = 5, endRadius = 20 :
        //   - distance < 5  -> t clamped to 0 -> red
        //   - distance > 20 -> t clamped to 1 -> blue (kClamp)
        //   - in between : lerp.
        val path = SkPath.Circle(32f, 32f, 28f, SkPathDirection.kCW)
        val grad = SkConicalGradient.Make(
            start = SkPoint(32f, 32f), startRadius = 5f,
            end = SkPoint(32f, 32f), endRadius = 20f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )!!
        assertEquals(SkConicalGradient.Type.kRadial, grad.getType())
        val paint = SkPaint().apply {
            shader = grad
            isAntiAlias = true
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPath(path, paint)
                device.flush()
            }
        }

        // Centre pixel (~0.7 px from centre) : t clamped to 0 -> red.
        val center = pixels.rgbaAt(32, 32)
        assertTrue(center[0] in 200..255, "center.R mostly red, got ${center[0]}")
        assertTrue(center[2] < 64, "center.B near zero, got ${center[2]}")
        assertTrue(center[3] >= 240, "center.A near opaque, got ${center[3]}")

        // Just inside the inner radius (x = 35, distance ~3) : clamped
        // to t = 0 -> still red.
        val nearCentre = pixels.rgbaAt(35, 32)
        assertTrue(nearCentre[0] in 200..255, "nearCentre.R mostly red, got ${nearCentre[0]}")
        assertTrue(nearCentre[2] < 64, "nearCentre.B near zero, got ${nearCentre[2]}")

        // Edge of gradient (x = 52, distance ~ 19.5) : t ~ 0.97 -> blue.
        val nearEdge = pixels.rgbaAt(52, 32)
        assertTrue(nearEdge[0] < 64, "nearEdge.R near zero, got ${nearEdge[0]}")
        assertTrue(nearEdge[2] >= 200, "nearEdge.B mostly blue, got ${nearEdge[2]}")

        // Halfway along +x (x = 44, distance ~12, t ~ 0.47) : balanced.
        val mid = pixels.rgbaAt(44, 32)
        assertTrue(mid[0] in 60..220, "mid.R balanced, got ${mid[0]}")
        assertTrue(mid[2] in 60..220, "mid.B balanced, got ${mid[2]}")

        // Past gradient radius, still inside the disk (x = 58, dist ~ 25.5) :
        // kClamp pins to blue.
        val pastEdge = pixels.rgbaAt(58, 32)
        assertTrue(pastEdge[0] < 64, "pastEdge.R near zero, got ${pastEdge[0]}")
        assertTrue(pastEdge[2] >= 200, "pastEdge.B mostly blue, got ${pastEdge[2]}")

        // Symmetry on the -x axis : x = 12, also ~20 px out -> blue.
        val edgeLeft = pixels.rgbaAt(12, 32)
        assertTrue(edgeLeft[0] < 64, "edgeLeft.R near zero, got ${edgeLeft[0]}")
        assertTrue(edgeLeft[2] >= 200, "edgeLeft.B mostly blue, got ${edgeLeft[2]}")

        // Outside the disk -> white background untouched.
        val outside = pixels.rgbaAt(0, 0)
        assertTrue(outside[0] >= 230, "outside.R near white, got ${outside[0]}")
        assertTrue(outside[1] >= 230, "outside.G near white, got ${outside[1]}")
        assertTrue(outside[2] >= 230, "outside.B near white, got ${outside[2]}")
    }

    // ---- focal-inside well-behaved sub-case (G4.4.3) ---------------------

    @Test
    fun `focal-inside kClamp conical on a circle path interpolates from focal to end-circle`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Same fixture as the rect-only focal-inside test : focal at
        // (16, 32), end circle (32, 32, r1 = 25). The fixture is
        // focal-inside well-behaved (fR1 = 25 / 16 > 1).
        // Use a wide disk path (centre 32, 32, radius 30) so the
        // gradient region is well inside the path -- the AA edge
        // coverage is clear of every sample.
        val path = SkPath.Circle(32f, 32f, 30f, SkPathDirection.kCW)
        val grad = SkConicalGradient.Make(
            start = SkPoint(16f, 32f), startRadius = 0f,
            end = SkPoint(32f, 32f), endRadius = 25f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )!!
        assertEquals(SkConicalGradient.Type.kFocal, grad.getType())
        val fd = grad.getFocalData()
        assertTrue(fd != null && fd.isWellBehaved(), "fixture must be focal-inside well-behaved")
        val paint = SkPaint().apply {
            shader = grad
            isAntiAlias = true
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPath(path, paint)
                device.flush()
            }
        }

        // Focal point pixel (16, 32) : t = 0 -> red.
        val focal = pixels.rgbaAt(16, 32)
        assertTrue(focal[0] in 200..255, "focal.R mostly red, got ${focal[0]}")
        assertTrue(focal[2] < 64, "focal.B near zero, got ${focal[2]}")

        // Inside the end circle, well away from focal (x = 55) : t ~ 1 -> blue.
        val nearEnd = pixels.rgbaAt(55, 32)
        assertTrue(nearEnd[0] < 64, "nearEnd.R near zero, got ${nearEnd[0]}")
        assertTrue(nearEnd[2] >= 200, "nearEnd.B mostly blue, got ${nearEnd[2]}")

        // Outside the path -> white background.
        val outside = pixels.rgbaAt(0, 0)
        assertTrue(outside[0] >= 230, "outside.R near white, got ${outside[0]}")
        assertTrue(outside[1] >= 230, "outside.G near white, got ${outside[1]}")
        assertTrue(outside[2] >= 230, "outside.B near white, got ${outside[2]}")
    }

    // ---- focal-on-circle sub-case (G4.4.6) ---------------------------

    @Test
    fun `focal-on-circle kClamp conical on a circle path interpolates inside the cone`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Focal-on-circle fixture : c0 = (16, 32), r0 = 5 ; c1 = (50, 32),
        // r1 = 39 ; dCenter = r1 - r0 = 34 so |fR1 - 1| ~ 0 (focal-on-
        // circle). Focal point in source space lies at (11, 32), exactly
        // on the end circle.
        // Use a wide disk path (centre (32, 32), radius 30) so the
        // gradient region is well inside the path -- the AA edge
        // coverage is clear of every sample.
        val path = SkPath.Circle(32f, 32f, 30f, SkPathDirection.kCW)
        val grad = SkConicalGradient.Make(
            start = SkPoint(16f, 32f), startRadius = 5f,
            end = SkPoint(50f, 32f), endRadius = 39f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )!!
        assertEquals(SkConicalGradient.Type.kFocal, grad.getType())
        val fd = grad.getFocalData()
        assertTrue(fd != null && fd.isFocalOnCircle(), "fixture must be focal-on-circle")
        val paint = SkPaint().apply {
            shader = grad
            isAntiAlias = true
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPath(path, paint)
                device.flush()
            }
        }

        // Start circle centre (16, 32) : t < 0 -> kClamp -> red.
        val startCentre = pixels.rgbaAt(16, 32)
        assertTrue(startCentre[0] in 180..255, "startCentre.R mostly red, got ${startCentre[0]}")
        assertTrue(startCentre[2] < 96, "startCentre.B near zero, got ${startCentre[2]}")

        // Mid-axis (40, 32) -- t ~ 0.29 -> red-leaning.
        val midAxis = pixels.rgbaAt(40, 32)
        assertTrue(midAxis[0] >= 130, "midAxis.R red-leaning, got ${midAxis[0]}")

        // Outside the disk -> white background untouched.
        val outside = pixels.rgbaAt(0, 0)
        assertTrue(outside[0] >= 230, "outside.R near white, got ${outside[0]}")
        assertTrue(outside[1] >= 230, "outside.G near white, got ${outside[1]}")
        assertTrue(outside[2] >= 230, "outside.B near white, got ${outside[2]}")
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
    }
}
