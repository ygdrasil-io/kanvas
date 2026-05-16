package org.skia.foundation


import org.skia.math.SkColorGetA
import org.skia.math.SkColorGetB
import org.skia.math.SkColorGetG
import org.skia.math.SkColorGetR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkAlphaType as CoreAlphaType
import org.skia.core.SkColorSpaceXformSteps
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn

/**
 * Unit tests for [SkLinearGradient]. Uses an identity colour-space xform
 * so the shader's output is sRGB-encoded ARGB ints — easy to inspect.
 */
class SkLinearGradientTest {

    private val identityXform = SkColorSpaceXformSteps(
        SkColorSpace.makeSRGB(), CoreAlphaType.kPremul,
        SkColorSpace.makeSRGB(), CoreAlphaType.kPremul,
    )

    @Test
    fun `linear gradient produces stop colour at endpoints`() {
        val g = SkLinearGradient.Make(
            p0 = SkPoint(0f, 0f),
            p1 = SkPoint(10f, 0f),
            colors = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()),    // red → blue
            positions = floatArrayOf(0f, 1f),
            tileMode = SkTileMode.kClamp,
        )
        g.setupForDraw(SkMatrix.Identity, identityXform)
        val row = IntArray(11)
        g.shadeRow(devX = 0, devY = 0, count = 11, dst = row)

        // At pixel 0 (sub-pixel centre 0.5), t ≈ 0.05 → very close to red.
        // At pixel 10 (sub-pixel centre 10.5), t ≈ 1.05 → clamps to blue.
        val first = row[0]
        val last = row[10]
        assertTrue(SkColorGetR(first) > SkColorGetB(first),
            "first pixel should be ~red, got ARGB(${SkColorGetA(first)}, ${SkColorGetR(first)}, ${SkColorGetG(first)}, ${SkColorGetB(first)})")
        assertTrue(SkColorGetB(last) > SkColorGetR(last),
            "last pixel should be ~blue, got ARGB(${SkColorGetA(last)}, ${SkColorGetR(last)}, ${SkColorGetG(last)}, ${SkColorGetB(last)})")
        assertNotEquals(0, SkColorGetA(first), "first pixel must be opaque")
        assertNotEquals(0, SkColorGetA(last), "last pixel must be opaque")
    }

    @Test
    fun `linear gradient interpolates between two stops`() {
        val g = SkLinearGradient.Make(
            p0 = SkPoint(0f, 0f),
            p1 = SkPoint(100f, 0f),
            colors = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()),    // black → white
            positions = floatArrayOf(0f, 1f),
            tileMode = SkTileMode.kClamp,
        )
        g.setupForDraw(SkMatrix.Identity, identityXform)
        val row = IntArray(101)
        g.shadeRow(devX = 0, devY = 0, count = 101, dst = row)
        // Mid-pixel should be ~50% gray.
        val mid = row[50]
        val r = SkColorGetR(mid); val g_ = SkColorGetG(mid); val b = SkColorGetB(mid)
        assertTrue(r in 120..140, "mid red ≈ 127 (got $r)")
        assertTrue(g_ in 120..140, "mid green ≈ 127 (got $g_)")
        assertTrue(b in 120..140, "mid blue ≈ 127 (got $b)")
    }

    @Test
    fun `linear gradient under non-identity xform produces non-black colours`() {
        // Reproduce TestUtils.runGmTest setup: sRGB stops, Rec.2020 working
        // space. The colours coming out of the shader should NOT be black —
        // failure here means transformStopColors is broken under non-identity
        // xform.
        val rec2020Cs = SkColorSpace.makeRGB(
            SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020,
        )!!
        val xform = SkColorSpaceXformSteps(
            SkColorSpace.makeSRGB(),
            CoreAlphaType.kPremul,
            rec2020Cs,
            CoreAlphaType.kPremul,
        )

        val g = SkLinearGradient.Make(
            p0 = SkPoint(0f, 0f),
            p1 = SkPoint(10f, 0f),
            colors = intArrayOf(0xFF00FF00.toInt(), 0xFFFFFFFF.toInt()),    // green → white
            positions = floatArrayOf(0f, 1f),
            tileMode = SkTileMode.kClamp,
        )
        g.setupForDraw(SkMatrix.Identity, xform)
        val row = IntArray(11)
        g.shadeRow(devX = 0, devY = 0, count = 11, dst = row)

        // Every output pixel must be opaque and non-zero — no double-premul,
        // no clipped-to-zero RGB.
        for (i in 0 until 11) {
            val a = SkColorGetA(row[i])
            val r = SkColorGetR(row[i])
            val g_ = SkColorGetG(row[i])
            val b = SkColorGetB(row[i])
            assertEquals(255, a, "pixel $i alpha must be 255 (got $a)")
            assertTrue(r + g_ + b > 0, "pixel $i must not be black (got R=$r G=$g_ B=$b)")
        }
    }

    @Test
    fun `kClamp clamps below first stop`() {
        val g = SkLinearGradient.Make(
            p0 = SkPoint(10f, 0f),
            p1 = SkPoint(20f, 0f),
            colors = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()),
            positions = floatArrayOf(0f, 1f),
            tileMode = SkTileMode.kClamp,
        )
        g.setupForDraw(SkMatrix.Identity, identityXform)
        val row = IntArray(5)
        g.shadeRow(devX = 0, devY = 0, count = 5, dst = row)
        // All these pixels are before t=0 (i.e. devX < 10). Should clamp to red.
        for (i in 0 until 5) {
            assertEquals(0xFFFF0000.toInt(), row[i],
                "pixel $i should clamp to red, got 0x${row[i].toUInt().toString(16)}")
        }
    }
}
