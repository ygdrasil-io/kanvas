package org.skia.effects

import org.graphiks.math.SkColor4f
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorSpace
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import kotlin.math.abs

class SkColorFilterPrivTest {

    @Test
    fun `withWorkingFormat delegates to working color space wrapper`() {
        val child = GChannelSplatFilter
        val wrapped = SkColorFilterPriv.withWorkingFormat(
            child = child,
            tf = SkNamedTransferFn.kLinear,
            gamut = SkNamedGamut.kXYZ,
            at = SkAlphaType.kUnpremul,
        )
        val expected = child.makeWithWorkingColorSpace(
            SkColorSpace.makeRGB(SkNamedTransferFn.kLinear, SkNamedGamut.kXYZ)!!,
        )

        val input = SkColor4f(0.8f, 0.25f, 0.1f, 0.6f)
        val actualOut = wrapped.filterColor4f(input)
        val expectedOut = expected.filterColor4f(input)

        assertNear(actualOut.fR, expectedOut.fR, "R")
        assertNear(actualOut.fG, expectedOut.fG, "G")
        assertNear(actualOut.fB, expectedOut.fB, "B")
        assertNear(actualOut.fA, expectedOut.fA, "A")
    }

    @Test
    fun `withWorkingFormat returns child for sRGB working format`() {
        val child = GChannelSplatFilter
        val wrapped = SkColorFilterPriv.withWorkingFormat(
            child = child,
            tf = SkNamedTransferFn.kSRGB,
            gamut = SkNamedGamut.kSRGB,
            at = SkAlphaType.kUnpremul,
        )

        assertSame(child, wrapped)
    }

    @Test
    fun `withWorkingFormat rejects premul alpha type for now`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkColorFilterPriv.withWorkingFormat(
                child = GChannelSplatFilter,
                tf = SkNamedTransferFn.kLinear,
                gamut = SkNamedGamut.kXYZ,
                at = SkAlphaType.kPremul,
            )
        }
    }

    private object GChannelSplatFilter : SkColorFilter() {
        override fun filterColor4f(src: SkColor4f): SkColor4f =
            SkColor4f(src.fG, src.fG, src.fG, src.fA)

        override fun isAlphaUnchanged(): Boolean = true
    }

    private fun assertNear(actual: Float, expected: Float, channel: String) {
        assertTrue(abs(actual - expected) <= 1e-6f, "$channel actual=$actual expected=$expected")
    }
}
