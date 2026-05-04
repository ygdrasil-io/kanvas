package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skia.core.SkBitmapDevice

class SkBlendModeTest {

    @Test
    fun `29 modes in upstream declaration order`() {
        val expected = listOf(
            // Porter-Duff coefficient modes (kClear..kScreen)
            SkBlendMode.kClear,
            SkBlendMode.kSrc,
            SkBlendMode.kDst,
            SkBlendMode.kSrcOver,
            SkBlendMode.kDstOver,
            SkBlendMode.kSrcIn,
            SkBlendMode.kDstIn,
            SkBlendMode.kSrcOut,
            SkBlendMode.kDstOut,
            SkBlendMode.kSrcATop,
            SkBlendMode.kDstATop,
            SkBlendMode.kXor,
            SkBlendMode.kPlus,
            SkBlendMode.kModulate,
            SkBlendMode.kScreen,
            // Separable component modes (kOverlay..kMultiply)
            SkBlendMode.kOverlay,
            SkBlendMode.kDarken,
            SkBlendMode.kLighten,
            SkBlendMode.kColorDodge,
            SkBlendMode.kColorBurn,
            SkBlendMode.kHardLight,
            SkBlendMode.kSoftLight,
            SkBlendMode.kDifference,
            SkBlendMode.kExclusion,
            SkBlendMode.kMultiply,
            // HSL modes (kHue..kLuminosity)
            SkBlendMode.kHue,
            SkBlendMode.kSaturation,
            SkBlendMode.kColor,
            SkBlendMode.kLuminosity,
        )
        assertEquals(expected, SkBlendMode.entries)
        assertEquals(29, SkBlendMode.entries.size)
        assertEquals(29, SkBlendMode.kSkBlendModeCount)
    }

    @Test
    fun `kLastCoeffMode is kScreen`() {
        // Upstream: kLastCoeffMode = kScreen. Index 14, last of the
        // Porter-Duff coefficient family.
        assertEquals(SkBlendMode.kScreen, SkBlendMode.kLastCoeffMode)
        assertEquals(14, SkBlendMode.kScreen.ordinal)
    }

    @Test
    fun `kLastSeparableMode is kMultiply`() {
        // Upstream: kLastSeparableMode = kMultiply. Index 24.
        assertEquals(SkBlendMode.kMultiply, SkBlendMode.kLastSeparableMode)
        assertEquals(24, SkBlendMode.kMultiply.ordinal)
    }

    @Test
    fun `kLastMode is kLuminosity`() {
        // Upstream: kLastMode = kLuminosity. Last enum value, index 28.
        assertEquals(SkBlendMode.kLuminosity, SkBlendMode.kLastMode)
        assertEquals(28, SkBlendMode.kLuminosity.ordinal)
        assertEquals(SkBlendMode.entries.last(), SkBlendMode.kLastMode)
    }

    @Test
    fun `kSrcOver is the canonical default and falls within Porter-Duff range`() {
        assertEquals(3, SkBlendMode.kSrcOver.ordinal)
        // It must be a coeff mode (<= kLastCoeffMode).
        assert(SkBlendMode.kSrcOver.ordinal <= SkBlendMode.kLastCoeffMode.ordinal) {
            "kSrcOver (${SkBlendMode.kSrcOver.ordinal}) should be <= kLastCoeffMode (${SkBlendMode.kLastCoeffMode.ordinal})"
        }
    }

    // ====================================================================
    // Phase 6 entry: per-pixel blend formulas. Verified against hand-computed
    // expected values for opaque-on-opaque (the common GM case) plus a
    // couple of partial-alpha cases per mode that cover the documented
    // residual error vs. Skia's premul reference pipeline.
    // ====================================================================

    private val device = SkBitmapDevice(SkBitmap(1, 1))

    private fun blend(src: SkColor, dst: SkColor, mode: SkBlendMode): SkColor =
        device.blendPixel(src, dst, mode)

    private fun argb(a: Int, r: Int, g: Int, b: Int): SkColor = SkColorSetARGB(a, r, g, b)

    private fun assertARGB(expected: SkColor, actual: SkColor, tolerance: Int = 0, label: String = "") {
        val ea = SkColorGetA(expected); val er = SkColorGetR(expected)
        val eg = SkColorGetG(expected); val eb = SkColorGetB(expected)
        val aa = SkColorGetA(actual); val ar = SkColorGetR(actual)
        val ag = SkColorGetG(actual); val ab = SkColorGetB(actual)
        val da = kotlin.math.abs(ea - aa)
        val dr = kotlin.math.abs(er - ar)
        val dg = kotlin.math.abs(eg - ag)
        val db = kotlin.math.abs(eb - ab)
        val ok = da <= tolerance && dr <= tolerance && dg <= tolerance && db <= tolerance
        if (!ok) {
            assertEquals(
                "ARGB(${ea}, ${er}, ${eg}, ${eb})",
                "ARGB(${aa}, ${ar}, ${ag}, ${ab})",
                "$label tolerance=$tolerance",
            )
        }
    }

    // ---------- kClear ---------------------------------------------------

    @Test
    fun `kClear zeroes any inputs`() {
        assertEquals(0, blend(SK_ColorRED, SK_ColorBLUE, SkBlendMode.kClear))
        assertEquals(0, blend(SK_ColorTRANSPARENT, argb(128, 200, 100, 50), SkBlendMode.kClear))
    }

    // ---------- kSrc -----------------------------------------------------

    @Test
    fun `kSrc replaces dst with src verbatim`() {
        assertEquals(SK_ColorRED, blend(SK_ColorRED, SK_ColorBLUE, SkBlendMode.kSrc))
        assertEquals(argb(128, 100, 200, 50),
            blend(argb(128, 100, 200, 50), SK_ColorWHITE, SkBlendMode.kSrc))
    }

    // ---------- kDst -----------------------------------------------------

    @Test
    fun `kDst preserves dst`() {
        assertEquals(SK_ColorBLUE, blend(SK_ColorRED, SK_ColorBLUE, SkBlendMode.kDst))
    }

    // ---------- kSrcOver -------------------------------------------------

    @Test
    fun `kSrcOver opaque src replaces dst`() {
        assertEquals(SK_ColorRED, blend(SK_ColorRED, SK_ColorBLUE, SkBlendMode.kSrcOver))
    }

    @Test
    fun `kSrcOver transparent src is a no-op`() {
        assertEquals(SK_ColorBLUE, blend(SK_ColorTRANSPARENT, SK_ColorBLUE, SkBlendMode.kSrcOver))
    }

    @Test
    fun `kSrcOver half-alpha red over opaque blue`() {
        // sa=128, src=(255,0,0); da=255, dst=(0,0,255).
        // outA = 128 + 255*127/255 ≈ 255 (rounded). outR≈128, outG=0, outB=127.
        val out = blend(argb(128, 255, 0, 0), SK_ColorBLUE, SkBlendMode.kSrcOver)
        assertEquals(0xFF, SkColorGetA(out))
        assert(SkColorGetR(out) in 120..135) { "outR=${SkColorGetR(out)}" }
        assertEquals(0, SkColorGetG(out))
    }

    // ---------- kDstOver -------------------------------------------------

    @Test
    fun `kDstOver opaque dst preserves dst`() {
        assertEquals(SK_ColorBLUE, blend(SK_ColorRED, SK_ColorBLUE, SkBlendMode.kDstOver))
    }

    @Test
    fun `kDstOver transparent dst exposes src`() {
        assertEquals(SK_ColorRED, blend(SK_ColorRED, SK_ColorTRANSPARENT, SkBlendMode.kDstOver))
    }

    // ---------- kSrcIn ---------------------------------------------------

    @Test
    fun `kSrcIn opaque dst keeps src`() {
        assertEquals(SK_ColorRED, blend(SK_ColorRED, SK_ColorBLUE, SkBlendMode.kSrcIn))
    }

    @Test
    fun `kSrcIn transparent dst clears`() {
        assertEquals(0, blend(SK_ColorRED, SK_ColorTRANSPARENT, SkBlendMode.kSrcIn))
    }

    @Test
    fun `kSrcIn half-alpha dst masks src alpha`() {
        // sa=255, da=128 -> outA = 255*128/255 = 128. RGB unchanged from src.
        val out = blend(SK_ColorRED, argb(128, 0, 0, 0), SkBlendMode.kSrcIn)
        assertEquals(128, SkColorGetA(out))
        assertEquals(255, SkColorGetR(out))
        assertEquals(0, SkColorGetG(out))
        assertEquals(0, SkColorGetB(out))
    }

    // ---------- kDstIn ---------------------------------------------------

    @Test
    fun `kDstIn opaque src keeps dst`() {
        assertEquals(SK_ColorBLUE, blend(SK_ColorRED, SK_ColorBLUE, SkBlendMode.kDstIn))
    }

    @Test
    fun `kDstIn transparent src clears`() {
        assertEquals(0, blend(SK_ColorTRANSPARENT, SK_ColorBLUE, SkBlendMode.kDstIn))
    }

    @Test
    fun `kDstIn half-alpha src masks dst alpha`() {
        val out = blend(argb(128, 0, 0, 0), SK_ColorBLUE, SkBlendMode.kDstIn)
        assertEquals(128, SkColorGetA(out))
        assertEquals(0, SkColorGetR(out))
        assertEquals(0, SkColorGetG(out))
        assertEquals(255, SkColorGetB(out))
    }

    // ---------- kPlus ----------------------------------------------------

    @Test
    fun `kPlus adds opaque components saturating`() {
        // (255,0,0) + (0,0,255) = (255,0,255), alpha clamps to 255.
        assertEquals(SK_ColorMAGENTA, blend(SK_ColorRED, SK_ColorBLUE, SkBlendMode.kPlus))
    }

    @Test
    fun `kPlus saturates over-bright sums`() {
        // (200,100,50) + (200,200,50) = clamp to (255,255,100).
        val out = blend(argb(255, 200, 100, 50), argb(255, 200, 200, 50), SkBlendMode.kPlus)
        assertEquals(argb(255, 255, 255, 100), out)
    }

    @Test
    fun `kPlus opaque white plus blue stays white`() {
        // ScaledRectsGM-style: opaque red on top of opaque blue saturates
        // colour channels independently.
        assertEquals(SK_ColorWHITE, blend(SK_ColorWHITE, SK_ColorBLUE, SkBlendMode.kPlus))
    }

    @Test
    fun `kPlus respects transparent src`() {
        assertEquals(SK_ColorBLUE, blend(SK_ColorTRANSPARENT, SK_ColorBLUE, SkBlendMode.kPlus))
    }

    // ---------- kModulate ------------------------------------------------

    @Test
    fun `kModulate opaque white acts as identity`() {
        // 255*255/255 = 255 on each component.
        assertEquals(SK_ColorBLUE, blend(SK_ColorWHITE, SK_ColorBLUE, SkBlendMode.kModulate))
    }

    @Test
    fun `kModulate opaque black yields zero RGB`() {
        // 0*x/255 = 0. Alpha 255*255/255 = 255 (opaque).
        val out = blend(SK_ColorBLACK, SK_ColorBLUE, SkBlendMode.kModulate)
        assertEquals(0xFF, SkColorGetA(out))
        assertEquals(0, SkColorGetR(out))
        assertEquals(0, SkColorGetG(out))
        assertEquals(0, SkColorGetB(out))
    }

    @Test
    fun `kModulate half-grey times red gives half-red`() {
        // Both inputs opaque: (128 * 255 + 127)/255 = 128 (with rounding).
        val out = blend(argb(255, 128, 128, 128), SK_ColorRED, SkBlendMode.kModulate)
        assertEquals(0xFF, SkColorGetA(out))
        assertARGB(argb(255, 128, 0, 0), out, tolerance = 1, label = "kModulate half-grey×red")
    }

    @Test
    fun `kModulate transparent inputs zero out`() {
        assertEquals(0, blend(SK_ColorTRANSPARENT, SK_ColorBLUE, SkBlendMode.kModulate))
        assertEquals(0, blend(SK_ColorRED, SK_ColorTRANSPARENT, SkBlendMode.kModulate))
    }

    // ---------- kScreen --------------------------------------------------

    @Test
    fun `kScreen opaque white plus anything is white`() {
        // 255 + x - 255*x/255 = 255.
        val out = blend(SK_ColorWHITE, SK_ColorBLUE, SkBlendMode.kScreen)
        assertARGB(SK_ColorWHITE, out, tolerance = 1, label = "kScreen white over blue")
    }

    @Test
    fun `kScreen opaque black is identity`() {
        // 0 + x - 0 = x.
        val out = blend(SK_ColorBLACK, SK_ColorBLUE, SkBlendMode.kScreen)
        assertARGB(SK_ColorBLUE, out, tolerance = 1, label = "kScreen black over blue")
    }

    @Test
    fun `kScreen half-grey opaque commutative`() {
        // 128 + 128 - 128*128/255 ≈ 256 - 64 = 192.
        val a = argb(255, 128, 128, 128)
        val out = blend(a, a, SkBlendMode.kScreen)
        assertARGB(argb(255, 192, 192, 192), out, tolerance = 2, label = "kScreen 50%×50%")
    }

    @Test
    fun `kScreen red plus blue yields magenta`() {
        // r: 255+0-0 = 255. b: 0+255-0 = 255.
        assertEquals(SK_ColorMAGENTA, blend(SK_ColorRED, SK_ColorBLUE, SkBlendMode.kScreen))
    }

    // ====================================================================
    // Phase 6 Porter-Duff completion: kSrcOut, kDstOut, kSrcATop, kDstATop, kXor.
    // ====================================================================

    @Test
    fun `kSrcOut opaque dst clears`() {
        // s*(1-da) with da=255 ⇒ 0.
        assertEquals(0, blend(SK_ColorRED, SK_ColorBLUE, SkBlendMode.kSrcOut))
    }

    @Test
    fun `kSrcOut transparent dst keeps src`() {
        // s*(1-da) with da=0 ⇒ s.
        val out = blend(SK_ColorRED, 0, SkBlendMode.kSrcOut)
        assertARGB(SK_ColorRED, out, tolerance = 0, label = "kSrcOut red over transparent")
    }

    @Test
    fun `kSrcOut half-alpha dst gives half-alpha src`() {
        // sa=255, da=128 ⇒ outA = 255 * 127 / 255 = 127.
        val out = blend(SK_ColorRED, argb(128, 0, 0, 255), SkBlendMode.kSrcOut)
        assertARGB(argb(127, 255, 0, 0), out, tolerance = 1, label = "kSrcOut red over 50% blue")
    }

    @Test
    fun `kDstOut opaque src clears`() {
        // d*(1-sa) with sa=255 ⇒ 0.
        assertEquals(0, blend(SK_ColorRED, SK_ColorBLUE, SkBlendMode.kDstOut))
    }

    @Test
    fun `kDstOut transparent src keeps dst`() {
        // d*(1-sa) with sa=0 ⇒ d.
        val out = blend(0, SK_ColorBLUE, SkBlendMode.kDstOut)
        assertARGB(SK_ColorBLUE, out, tolerance = 0, label = "kDstOut transparent over blue")
    }

    @Test
    fun `kDstOut half-alpha src gives half-alpha dst`() {
        // sa=128, da=255 ⇒ outA = 255 * 127 / 255 = 127.
        val out = blend(argb(128, 255, 0, 0), SK_ColorBLUE, SkBlendMode.kDstOut)
        assertARGB(argb(127, 0, 0, 255), out, tolerance = 1, label = "kDstOut 50% red over blue")
    }

    @Test
    fun `kSrcATop opaque src on opaque dst replaces RGB keeps alpha`() {
        // outA = da = 255, RGB = src.
        val out = blend(SK_ColorRED, SK_ColorBLUE, SkBlendMode.kSrcATop)
        assertARGB(SK_ColorRED, out, tolerance = 0, label = "kSrcATop opaque-on-opaque")
    }

    @Test
    fun `kSrcATop transparent dst clears`() {
        // outA = da = 0.
        assertEquals(0, blend(SK_ColorRED, 0, SkBlendMode.kSrcATop))
    }

    @Test
    fun `kSrcATop half-alpha src lerps RGB and keeps dst alpha`() {
        // sa=128, da=255 ⇒ outA = 255, outRGB = lerp(dst, src, 128/255) ≈ 50% mix.
        // R: (255*128 + 0*127)/255 = 128
        // B: (0*128 + 255*127)/255 = 127
        val out = blend(argb(128, 255, 0, 0), SK_ColorBLUE, SkBlendMode.kSrcATop)
        assertARGB(argb(255, 128, 0, 127), out, tolerance = 1, label = "kSrcATop 50% red atop blue")
    }

    @Test
    fun `kDstATop is symmetric to kSrcATop`() {
        // kDstATop(a, b) == kSrcATop(b, a) by definition.
        val a = argb(200, 200, 50, 100)
        val b = argb(150, 80, 200, 30)
        assertEquals(blend(b, a, SkBlendMode.kSrcATop), blend(a, b, SkBlendMode.kDstATop))
    }

    @Test
    fun `kXor opaque-on-opaque clears`() {
        // s*(1-da) + d*(1-sa) with sa=da=255 ⇒ 0.
        assertEquals(0, blend(SK_ColorRED, SK_ColorBLUE, SkBlendMode.kXor))
    }

    @Test
    fun `kXor transparent dst keeps src`() {
        val out = blend(SK_ColorRED, 0, SkBlendMode.kXor)
        assertARGB(SK_ColorRED, out, tolerance = 0, label = "kXor red over transparent")
    }

    @Test
    fun `kXor transparent src keeps dst`() {
        val out = blend(0, SK_ColorBLUE, SkBlendMode.kXor)
        assertARGB(SK_ColorBLUE, out, tolerance = 0, label = "kXor transparent over blue")
    }

    @Test
    fun `kXor half-alpha src and opaque dst yields half-alpha dst`() {
        // sa=128, da=255 ⇒ outA = (128*0 + 255*127)/255 = 127.
        // outRgb_premul = sr*128*0 + dr*255*127 = dr * 32385.
        // outRgb = outRgb_premul / (outA*255) = dr*32385/(127*255) = dr*32385/32385 = dr.
        val out = blend(argb(128, 255, 0, 0), SK_ColorBLUE, SkBlendMode.kXor)
        assertARGB(argb(127, 0, 0, 255), out, tolerance = 1, label = "kXor 50% red over blue")
    }

    @Test
    fun `kXor half-alpha both gives mixed half-alpha colour`() {
        // sa=128, da=128. outA = (128*127 + 128*127)/255 = 32512/255 ≈ 127.
        // outRgb_premul.r = 255*128*127 + 0*128*127 = 4143120
        // outRgb.r = 4143120 / (127*255) = 4143120 / 32385 ≈ 127.91 ⇒ 128.
        // outRgb_premul.b = 0*128*127 + 255*128*127 = 4143120 ⇒ 128.
        // So result is half-alpha magenta ≈ argb(127, 128, 0, 128).
        val out = blend(argb(128, 255, 0, 0), argb(128, 0, 0, 255), SkBlendMode.kXor)
        assertARGB(argb(127, 128, 0, 128), out, tolerance = 2, label = "kXor 50%×50%")
    }

    // ---------- Unimplemented modes throw -------------------------------

    @Test
    fun `unimplemented mode throws NotImplementedError`() {
        try {
            blend(SK_ColorRED, SK_ColorBLUE, SkBlendMode.kDarken)
            assert(false) { "expected NotImplementedError" }
        } catch (_: NotImplementedError) {
            // expected
        }
    }
}
