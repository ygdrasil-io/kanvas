package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.foundation.skcms.skcmsParse
import java.io.DataInputStream
import java.util.zip.Inflater

/**
 * Phase F5 of MIGRATION_PLAN_COLORSPACE_PORT.md — verify
 * `SkColorSpace.make(profile)` end-to-end on the ICC profile parsed
 * from the DM Rec.2020 reference PNG.
 */
class SkColorSpaceMakeProfileTest {

    private val rec2020Profile by lazy {
        skcmsParse(extractIccProfileFromPng("bigrect.png"))
            ?: error("Rec.2020 profile failed to parse")
    }

    @Test
    fun `make from Rec_2020 profile returns a Rec_2020-equivalent colorspace`() {
        val cs = SkColorSpace.make(rec2020Profile)
        assertNotNull(cs)
        // The Phase B snap only snaps to sRGB / linear / 2.2-power
        // singletons (matching upstream Skia), so the Rec.2020 TF stays
        // as the s15Fixed16-decoded values from the PNG. We assert
        // structural equivalence with the canonical kRec2020 colorspace:
        //  - TF parameters within transfer-fn tolerance (0.001)
        //  - gamut matrix within colorspace tolerance (0.01)
        //  - functional check: pure sRGB blue (0,0,1) → encoded ~(43, 13, 241).
        val tf = cs!!.transferFn
        assertTrue(kotlin.math.abs(tf.g - SkNamedTransferFn.kRec2020.g) < 1e-3f, "TF g")
        assertTrue(kotlin.math.abs(tf.a - SkNamedTransferFn.kRec2020.a) < 1e-3f, "TF a")
        assertTrue(kotlin.math.abs(tf.d - SkNamedTransferFn.kRec2020.d) < 1e-3f, "TF d")
        for (r in 0 until 3) for (c in 0 until 3) {
            val want = SkNamedGamut.kRec2020.vals[r][c]
            val got = cs.toXYZD50.vals[r][c]
            assertTrue(kotlin.math.abs(want - got) < 0.01f, "matrix[$r][$c]")
        }

        // Functional check: sRGB → parsed-Rec.2020 on pure blue.
        val xform = org.skia.core.SkColorSpaceXformSteps(
            SkColorSpace.makeSRGB(), org.skia.core.SkAlphaType.kOpaque,
            cs, org.skia.core.SkAlphaType.kOpaque,
        )
        val rgba = floatArrayOf(0f, 0f, 1f, 1f)
        xform.apply(rgba)
        val r = (rgba[0] * 255f + 0.5f).toInt()
        val g = (rgba[1] * 255f + 0.5f).toInt()
        val b = (rgba[2] * 255f + 0.5f).toInt()
        assertEquals(43, r, "R must match bigrect.png reference")
        assertEquals(13, g, "G must match bigrect.png reference")
        assertTrue(b in 240..242, "B must be ~241; got $b")
    }

    @Test
    fun `make from sRGB profile returns the sRGB singleton`() {
        // Build a synthetic sRGB profile by serializing makeSRGB(), then
        // reparsing it as raw bytes — but our serialize() is the 68-byte
        // custom format, not ICC. Instead, hand-construct an
        // SkcmsICCProfile with sRGB-equivalent parametric TRC + sRGB
        // gamut.
        val profile = org.skia.foundation.skcms.SkcmsICCProfile(
            buffer = ByteArray(0),
            size = 0,
            dataColorSpace = org.skia.foundation.skcms.SkcmsSignature.RGB.value,
            pcs = org.skia.foundation.skcms.SkcmsSignature.XYZ.value,
            tagCount = 0,
            trc = arrayOf(
                org.skia.foundation.skcms.SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
                org.skia.foundation.skcms.SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
                org.skia.foundation.skcms.SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
            ),
            toXYZD50 = SkNamedGamut.kSRGB,
            hasTrc = true,
            hasToXYZD50 = true,
        )
        assertSame(SkColorSpace.makeSRGB(), SkColorSpace.make(profile))
    }

    @Test
    fun `make from sRGB-linear profile returns the sRGB-linear singleton`() {
        val profile = org.skia.foundation.skcms.SkcmsICCProfile(
            buffer = ByteArray(0),
            tagCount = 0,
            trc = arrayOf(
                org.skia.foundation.skcms.SkcmsCurve.Parametric(SkNamedTransferFn.kLinear),
                org.skia.foundation.skcms.SkcmsCurve.Parametric(SkNamedTransferFn.kLinear),
                org.skia.foundation.skcms.SkcmsCurve.Parametric(SkNamedTransferFn.kLinear),
            ),
            toXYZD50 = SkNamedGamut.kSRGB,
            hasTrc = true,
            hasToXYZD50 = true,
        )
        assertSame(SkColorSpace.makeSRGBLinear(), SkColorSpace.make(profile))
    }

    @Test
    fun `make returns null when no usable TF`() {
        val profile = org.skia.foundation.skcms.SkcmsICCProfile(
            buffer = ByteArray(0),
            tagCount = 0,
            trc = arrayOfNulls(3),  // no TRC
            toXYZD50 = SkNamedGamut.kSRGB,
            hasTrc = false,
            hasToXYZD50 = true,
        )
        org.junit.jupiter.api.Assertions.assertNull(SkColorSpace.make(profile))
    }

    @Test
    fun `make returns null when TRCs disagree`() {
        val profile = org.skia.foundation.skcms.SkcmsICCProfile(
            buffer = ByteArray(0),
            tagCount = 0,
            trc = arrayOf(
                org.skia.foundation.skcms.SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
                org.skia.foundation.skcms.SkcmsCurve.Parametric(SkNamedTransferFn.kLinear),  // different!
                org.skia.foundation.skcms.SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
            ),
            toXYZD50 = SkNamedGamut.kSRGB,
            hasTrc = true,
            hasToXYZD50 = true,
        )
        org.junit.jupiter.api.Assertions.assertNull(SkColorSpace.make(profile))
    }

    @Test
    fun `make uses CICP fast-path when usable`() {
        // CICP tag with kRec709 primaries + kIEC61966_2_1 (sRGB) TF.
        // This should snap to the sRGB singleton.
        val profile = org.skia.foundation.skcms.SkcmsICCProfile(
            buffer = ByteArray(0),
            tagCount = 0,
            trc = arrayOf(
                org.skia.foundation.skcms.SkcmsCurve.Parametric(SkNamedTransferFn.kRec2020),  // CICP wins
                org.skia.foundation.skcms.SkcmsCurve.Parametric(SkNamedTransferFn.kRec2020),
                org.skia.foundation.skcms.SkcmsCurve.Parametric(SkNamedTransferFn.kRec2020),
            ),
            toXYZD50 = SkNamedGamut.kRec2020,    // CICP overrides
            cicp = org.skia.foundation.skcms.SkcmsCICP(
                colorPrimaries = SkNamedPrimaries.CicpId.kRec709.value,
                transferCharacteristics = SkNamedTransferFn.CicpId.kIEC61966_2_1.value,
                matrixCoefficients = 0,
                videoFullRangeFlag = 1,
            ),
            hasTrc = true,
            hasToXYZD50 = true,
            hasCICP = true,
        )
        assertSame(SkColorSpace.makeSRGB(), SkColorSpace.make(profile))
    }

    private fun extractIccProfileFromPng(name: String): ByteArray {
        val pngBytes = SkColorSpaceMakeProfileTest::class.java.classLoader
            .getResourceAsStream("original-888/$name")?.readBytes()
            ?: error("missing original-888/$name on classpath")
        val dis = DataInputStream(pngBytes.inputStream())
        dis.skipBytes(8)
        while (dis.available() > 0) {
            val length = dis.readInt()
            val typeBytes = ByteArray(4).also { dis.readFully(it) }
            val type = String(typeBytes, Charsets.US_ASCII)
            val data = ByteArray(length).also { dis.readFully(it) }
            dis.readInt()
            if (type == "iCCP") {
                var nameEnd = 0
                while (nameEnd < data.size && data[nameEnd] != 0.toByte()) nameEnd++
                val compressed = data.copyOfRange(nameEnd + 2, data.size)
                val inflater = Inflater()
                inflater.setInput(compressed)
                val out = ByteArray(64 * 1024)
                val len = inflater.inflate(out)
                inflater.end()
                return out.copyOfRange(0, len)
            }
        }
        error("no iCCP chunk in $name")
    }
}
