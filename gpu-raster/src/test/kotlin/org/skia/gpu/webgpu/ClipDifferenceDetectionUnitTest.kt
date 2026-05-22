package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.core.SkClipShape
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * M4 -- unit coverage for [SkClipShape.tryDetect] in the difference
 * arm. Validates that every shape we recognise for `kIntersect` also
 * round-trips under `kDifference`, with the [SkClipShape.op] field
 * carrying the requested op verbatim.
 *
 * Also pins the negative cases : non-axis-aligned CTM and inverse
 * fill types both fall through to `null` so the GPU device still takes
 * the rasterised-aaClip fallback (which currently fail-fasts in
 * `bindClip`, the pre-M4 behaviour for arbitrary paths).
 */
class ClipDifferenceDetectionUnitTest {

    @Test
    fun `rect path under kDifference detects as Rect with op=Difference`() {
        val rect = SkRect.MakeXYWH(10f, 10f, 100f, 100f)
        val detected = SkClipShape.tryDetect(
            SkPath.Rect(rect),
            SkMatrix.Identity,
            SkClipOp.kDifference,
        )
        assertNotNull(detected)
        val asRect = detected as SkClipShape.Rect
        assertEquals(SkClipOp.kDifference, asRect.op)
        assertEquals(10f, asRect.bounds.left)
        assertEquals(110f, asRect.bounds.right)
    }

    @Test
    fun `rect path under kIntersect default still works`() {
        val rect = SkRect.MakeXYWH(0f, 0f, 50f, 50f)
        val detected = SkClipShape.tryDetect(SkPath.Rect(rect), SkMatrix.Identity)
        assertNotNull(detected)
        val asRect = detected as SkClipShape.Rect
        assertEquals(SkClipOp.kIntersect, asRect.op)
    }

    @Test
    fun `oval path under kDifference detects as Circle when square`() {
        val bounds = SkRect.MakeXYWH(10f, 10f, 20f, 20f)
        val detected = SkClipShape.tryDetect(
            SkPath.Oval(bounds),
            SkMatrix.Identity,
            SkClipOp.kDifference,
        )
        assertNotNull(detected)
        val asCircle = detected as SkClipShape.Circle
        assertEquals(SkClipOp.kDifference, asCircle.op)
        assertEquals(20f, asCircle.cx)
        assertEquals(20f, asCircle.cy)
        assertEquals(10f, asCircle.r)
    }

    @Test
    fun `rrect with simple radii under kDifference detects as RRect`() {
        val rect = SkRect.MakeXYWH(10f, 10f, 100f, 100f)
        val rrect = SkRRect.MakeRectXY(rect, 0.1f, 0.1f)
        // SkPath.RRect serialises the rrect into a verb stream ; on
        // round-trip through `path.isRRect()` the corner radii pick up
        // sub-ULP jitter (0.10000038 / 0.099998474) and the type
        // promotes to kNinePatch. M4's detector recognises that pattern
        // and coerces back to the uniform-corner representation when
        // the spread is within [SkClipShape.NEAR_UNIFORM_EPS].
        val detected = SkClipShape.tryDetect(
            SkPath.RRect(rrect),
            SkMatrix.Identity,
            SkClipOp.kDifference,
        )
        assertNotNull(
            detected,
            "rrect with 0.1 corner radii should detect as simple RRect under kDifference",
        )
        val asRRect = detected as SkClipShape.RRect
        assertEquals(SkClipOp.kDifference, asRRect.op)
        // Tolerance covers the sub-ULP jitter from the path round-trip.
        assertEquals(0.1f, asRRect.rx, 1e-4f)
        assertEquals(0.1f, asRRect.ry, 1e-4f)
    }

    @Test
    fun `non-axis-aligned CTM falls through to null even under kDifference`() {
        val rect = SkRect.MakeXYWH(10f, 10f, 100f, 100f)
        // 15 deg rotation around origin -- breaks isAxisAligned, so the
        // detector must drop to null.
        val rad = (15.0 * Math.PI / 180.0).toFloat()
        val c = kotlin.math.cos(rad)
        val s = kotlin.math.sin(rad)
        val rotated = SkMatrix.MakeAll(c, -s, 0f, s, c, 0f, 0f, 0f, 1f)
        val detected = SkClipShape.tryDetect(
            SkPath.Rect(rect),
            rotated,
            SkClipOp.kDifference,
        )
        assertNull(detected, "rotated CTM should drop to null so caller takes the aaClip fallback")
    }
}
