package org.skia.skcms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase D of MIGRATION_PLAN_COLORSPACE_PORT.md — verify that the named
 * transfer-function constants and their CicpId enum match upstream Skia.
 */
class SkNamedTransferFnConstantsTest {

    @Test
    fun `sRGBish constants classify as sRGBish`() {
        for ((name, tf) in listOf(
            "kSRGB" to SkNamedTransferFn.kSRGB,
            "kLinear" to SkNamedTransferFn.kLinear,
            "k2Dot2" to SkNamedTransferFn.k2Dot2,
            "kRec709" to SkNamedTransferFn.kRec709,
            "kRec470SystemM" to SkNamedTransferFn.kRec470SystemM,
            "kRec470SystemBG" to SkNamedTransferFn.kRec470SystemBG,
            "kRec601" to SkNamedTransferFn.kRec601,
            "kSMPTE_ST_240" to SkNamedTransferFn.kSMPTE_ST_240,
            "kIEC61966_2_4" to SkNamedTransferFn.kIEC61966_2_4,
            "kIEC61966_2_1" to SkNamedTransferFn.kIEC61966_2_1,
            "kRec2020_10bit" to SkNamedTransferFn.kRec2020_10bit,
            "kRec2020_12bit" to SkNamedTransferFn.kRec2020_12bit,
            "kSMPTE_ST_428_1" to SkNamedTransferFn.kSMPTE_ST_428_1,
            "kProPhotoRGB" to SkNamedTransferFn.kProPhotoRGB,
            "kA98RGB" to SkNamedTransferFn.kA98RGB,
            "kRec2020" to SkNamedTransferFn.kRec2020,
        )) {
            assertEquals(SkcmsTFType.sRGBish, classify(tf), "$name should classify as sRGBish")
        }
    }

    @Test
    fun `PQ and HLG sentinel TFs classify as their HDR kind`() {
        // Phase I activated: g=-4 → PQ (a=ref-white, b..f=0),
        // g=-5 → HLG (a=ref-white, b=peak luminance, c=system gamma).
        assertEquals(SkcmsTFType.PQ, classify(SkNamedTransferFn.kPQ))
        assertEquals(SkcmsTFType.HLG, classify(SkNamedTransferFn.kHLG))
    }

    @Test
    fun `aliases share identity with their target`() {
        assertSame(SkNamedTransferFn.kRec709, SkNamedTransferFn.kRec601)
        assertSame(SkNamedTransferFn.kRec709, SkNamedTransferFn.kIEC61966_2_4)
        assertSame(SkNamedTransferFn.kSRGB, SkNamedTransferFn.kIEC61966_2_1)
        assertSame(SkNamedTransferFn.kRec709, SkNamedTransferFn.kRec2020_10bit)
        assertSame(SkNamedTransferFn.kRec709, SkNamedTransferFn.kRec2020_12bit)
        assertSame(SkNamedTransferFn.k2Dot2, SkNamedTransferFn.kA98RGB)
    }

    @Test
    fun `CicpId values match ITU-T H_273 Table 3`() {
        // Spot-check against the spec.
        assertEquals(1, SkNamedTransferFn.CicpId.kRec709.value)
        assertEquals(4, SkNamedTransferFn.CicpId.kRec470SystemM.value)
        assertEquals(5, SkNamedTransferFn.CicpId.kRec470SystemBG.value)
        assertEquals(6, SkNamedTransferFn.CicpId.kRec601.value)
        assertEquals(7, SkNamedTransferFn.CicpId.kSMPTE_ST_240.value)
        assertEquals(8, SkNamedTransferFn.CicpId.kLinear.value)
        assertEquals(11, SkNamedTransferFn.CicpId.kIEC61966_2_4.value)
        assertEquals(13, SkNamedTransferFn.CicpId.kIEC61966_2_1.value)
        assertEquals(14, SkNamedTransferFn.CicpId.kRec2020_10bit.value)
        assertEquals(15, SkNamedTransferFn.CicpId.kRec2020_12bit.value)
        assertEquals(16, SkNamedTransferFn.CicpId.kPQ.value)
        assertEquals(17, SkNamedTransferFn.CicpId.kSMPTE_ST_428_1.value)
        assertEquals(18, SkNamedTransferFn.CicpId.kHLG.value)
    }

    @Test
    fun `kSRGB is a CicpId alias of kIEC61966_2_1`() {
        assertSame(SkNamedTransferFn.CicpId.kIEC61966_2_1, SkNamedTransferFn.CicpId.kSRGB)
        assertEquals(13, SkNamedTransferFn.CicpId.kSRGB.value)
    }

    @Test
    fun `kCicpIdApplicationDefined is 2 per the spec`() {
        assertEquals(2, SkNamedTransferFn.CicpId.kCicpIdApplicationDefined)
    }

    @Test
    fun `kRec709 evaluates to a 2_4 power`() {
        // y = (1*x + 0)^2.4 + 0  (linear branch unreachable: d=0)
        val y = skcmsTransferFunctionEval(SkNamedTransferFn.kRec709, 0.5f)
        val expected = Math.pow(0.5, 2.4).toFloat()
        assertTrue(kotlin.math.abs(y - expected) < 1e-5f,
            "kRec709(0.5) = $y expected $expected")
    }

    @Test
    fun `kSMPTE_ST_240 round-trips with its inverse`() {
        val tf = SkNamedTransferFn.kSMPTE_ST_240
        val inv = skcmsTransferFunctionInvert(tf)!!
        for (x in listOf(0f, 0.05f, 0.091286f, 0.5f, 1f)) {
            val y = skcmsTransferFunctionEval(tf, x)
            val back = skcmsTransferFunctionEval(inv, y)
            assertTrue(kotlin.math.abs(back - x) < 1f / 256f, "x=$x back=$back")
        }
    }
}
