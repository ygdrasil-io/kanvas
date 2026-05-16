package org.skia.foundation


import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorMAGENTA
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorTRANSPARENT
import org.skia.math.SK_ColorWHITE
import org.skia.math.SkColor
import org.skia.math.SkColorGetA
import org.skia.math.SkColorGetB
import org.skia.math.SkColorGetG
import org.skia.math.SkColorGetR
import org.skia.math.SkColorSetARGB
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

    // ====================================================================
    // Phase 6 separable (simple): kMultiply, kDarken, kLighten,
    // kDifference, kExclusion. Formulas operate in premul-float; tests
    // use opaque-on-opaque + at least one fractional-alpha case per mode.
    // ====================================================================

    @Test
    fun `kMultiply opaque white acts as identity`() {
        // Premul: (1-1)*d + (1-1)*1 + 1*d = d. Just the dst RGB.
        val out = blend(SK_ColorWHITE, SK_ColorBLUE, SkBlendMode.kMultiply)
        assertARGB(SK_ColorBLUE, out, tolerance = 1, label = "kMultiply white over blue")
    }

    @Test
    fun `kMultiply opaque black yields zero RGB`() {
        // Premul: (1-1)*d + (1-1)*0 + 0*d = 0 — black RGB, alpha = 1.
        val out = blend(SK_ColorBLACK, SK_ColorBLUE, SkBlendMode.kMultiply)
        assertARGB(SK_ColorBLACK, out, tolerance = 1, label = "kMultiply black over blue")
    }

    @Test
    fun `kMultiply red times green is dark green-red`() {
        // sc = (1, 0, 0) premul ; dc = (0, 1, 0) premul. With sa=da=1:
        // rc.r = 0 + 0 + 1*0 = 0
        // rc.g = 0 + 0 + 0*1 = 0
        // rc.b = 0 — all zero. Result is opaque black.
        val out = blend(SK_ColorRED, SK_ColorGREEN, SkBlendMode.kMultiply)
        assertARGB(SK_ColorBLACK, out, tolerance = 1, label = "kMultiply red×green")
    }

    @Test
    fun `kDarken opaque white preserves dst`() {
        // sc=1, dc=d. Premul: rc = 1 + d - max(1*1, d*1) = 1 + d - 1 = d.
        val out = blend(SK_ColorWHITE, SK_ColorBLUE, SkBlendMode.kDarken)
        assertARGB(SK_ColorBLUE, out, tolerance = 1, label = "kDarken white over blue")
    }

    @Test
    fun `kDarken opaque black returns black`() {
        // sc=0, dc=d. rc = 0 + d - max(0, d*1) = 0. Black RGB.
        val out = blend(SK_ColorBLACK, SK_ColorBLUE, SkBlendMode.kDarken)
        assertARGB(SK_ColorBLACK, out, tolerance = 1, label = "kDarken black over blue")
    }

    @Test
    fun `kDarken commutative on opaque inputs`() {
        // Darken should be symmetric in src/dst when both are opaque.
        val a = argb(255, 200, 50, 100)
        val b = argb(255, 80, 200, 30)
        assertEquals(blend(b, a, SkBlendMode.kDarken), blend(a, b, SkBlendMode.kDarken))
    }

    @Test
    fun `kLighten opaque white returns white`() {
        // sc=1, dc=d. rc = 1 + d - min(1*1, d*1) = 1 + d - d = 1.
        val out = blend(SK_ColorWHITE, SK_ColorBLUE, SkBlendMode.kLighten)
        assertARGB(SK_ColorWHITE, out, tolerance = 1, label = "kLighten white over blue")
    }

    @Test
    fun `kLighten opaque black preserves dst`() {
        // sc=0, dc=d. rc = 0 + d - min(0, d) = d.
        val out = blend(SK_ColorBLACK, SK_ColorBLUE, SkBlendMode.kLighten)
        assertARGB(SK_ColorBLUE, out, tolerance = 1, label = "kLighten black over blue")
    }

    @Test
    fun `kDifference of identical opaque colours is zero`() {
        // rc = c + c - 2*min(c, c) = 2c - 2c = 0.
        val out = blend(SK_ColorRED, SK_ColorRED, SkBlendMode.kDifference)
        assertARGB(SK_ColorBLACK, out, tolerance = 1, label = "kDifference red on red")
    }

    @Test
    fun `kDifference opaque black preserves dst`() {
        // sc=0, rc = 0 + d - 2*min(0, d) = d.
        val out = blend(SK_ColorBLACK, SK_ColorBLUE, SkBlendMode.kDifference)
        assertARGB(SK_ColorBLUE, out, tolerance = 1, label = "kDifference black over blue")
    }

    @Test
    fun `kDifference white on blue inverts`() {
        // sc=(1,1,1), dc=(0,0,1). rc = 1 + d - 2*min(1, d).
        // min(1, 0) = 0, min(1, 1) = 1.
        // r: 1 + 0 - 0 = 1; g: same = 1; b: 1 + 1 - 2 = 0.
        // Result: yellow (255, 255, 0).
        val out = blend(SK_ColorWHITE, SK_ColorBLUE, SkBlendMode.kDifference)
        assertARGB(argb(255, 255, 255, 0), out, tolerance = 1, label = "kDifference white on blue")
    }

    @Test
    fun `kExclusion of identical opaque colours is darker`() {
        // rc = c + c - 2*c*c. For c=1: rc = 2 - 2 = 0; for c=0.5: rc = 1 - 0.5 = 0.5.
        // Red (1,0,0): r = 0; g = 0; b = 0 → black.
        val out = blend(SK_ColorRED, SK_ColorRED, SkBlendMode.kExclusion)
        assertARGB(SK_ColorBLACK, out, tolerance = 1, label = "kExclusion red on red")
    }

    @Test
    fun `kExclusion white on blue gives yellow`() {
        // sc=(1,1,1), dc=(0,0,1). rc = s + d - 2*s*d.
        // r: 1 + 0 - 0 = 1; g: 1 + 0 - 0 = 1; b: 1 + 1 - 2 = 0.
        val out = blend(SK_ColorWHITE, SK_ColorBLUE, SkBlendMode.kExclusion)
        assertARGB(argb(255, 255, 255, 0), out, tolerance = 1, label = "kExclusion white on blue")
    }

    @Test
    fun `kExclusion of mid grey on itself yields half grey`() {
        // c = 0.5. rc = 0.5 + 0.5 - 2*0.5*0.5 = 1 - 0.5 = 0.5 → 128.
        val mid = argb(255, 128, 128, 128)
        val out = blend(mid, mid, SkBlendMode.kExclusion)
        assertARGB(argb(255, 128, 128, 128), out, tolerance = 2, label = "kExclusion 50%×50%")
    }

    // ====================================================================
    // Phase 6 separable (complex): kOverlay, kHardLight, kColorDodge,
    // kColorBurn, kSoftLight. Formulas have per-channel branches. Tests
    // pin the canonical opaque-on-opaque cases; sub-ulp drift at
    // fractional alpha is acceptable since float-premul → 8-bit
    // round-trip introduces ≤ 1 ulp per channel anyway.
    // ====================================================================

    @Test
    fun `kHardLight opaque white over half-grey is white`() {
        // Light src (sc > sa/2): rc = 1 - 2*(1-d)*(1-s) = 1 - 2*(0.5)*0 = 1.
        val out = blend(SK_ColorWHITE, argb(255, 128, 128, 128), SkBlendMode.kHardLight)
        assertARGB(SK_ColorWHITE, out, tolerance = 1, label = "kHardLight white over 50% grey")
    }

    @Test
    fun `kHardLight opaque black over half-grey is black`() {
        // Dark src (sc <= sa/2): rc = 2*s*d = 2*0*0.5 = 0.
        val out = blend(SK_ColorBLACK, argb(255, 128, 128, 128), SkBlendMode.kHardLight)
        assertARGB(SK_ColorBLACK, out, tolerance = 1, label = "kHardLight black over 50% grey")
    }

    @Test
    fun `kHardLight half-grey over half-grey is half-grey`() {
        // s = d = 0.5, sa = da = 1. Both 2*s <= sa (0.5 < 0.5 is false, but
        // ≤ is true). Body = 2*0.5*0.5 = 0.5. carrier = 0+0 = 0. rc = 0.5.
        val mid = argb(255, 128, 128, 128)
        val out = blend(mid, mid, SkBlendMode.kHardLight)
        assertARGB(mid, out, tolerance = 2, label = "kHardLight 50% on 50%")
    }

    @Test
    fun `kOverlay swaps the conditional vs kHardLight`() {
        // Overlay(s, d) = HardLight(d, s). For s=white, d=mid-grey:
        //   HardLight(white, mid-grey) = white (light src branch).
        //   Overlay (white, mid-grey) ≡ HardLight(mid-grey, white)
        //                              = 1 - 2*(1-1)*(1-0.5) = 1 = white.
        // Both happen to give white here; pick a case where they differ.
        // s=mid-grey, d=white: HardLight = 1 - 2*(0.5)*(0) = 1.
        //                      Overlay = HardLight(white, mid-grey) = 1.
        // Try s=mid-grey, d=black: HardLight: 2*s≤sa, rc = 2*0.5*0 = 0.
        //                          Overlay = HardLight(black, mid-grey)
        //                                  = (light src branch since 2*0=0 ≤ 1)
        //                                  = 2*0.5*0 = 0 (dark src branch). Hmm both are 0.
        // OK for s=mid-grey on d=mid-grey: HardLight = 0.5 (dark src branch);
        // Overlay = HardLight(mid-grey, mid-grey) — same swap, dark src branch
        //         = 2*0.5*0.5 = 0.5 too. Equivalence is symmetric here.
        val mid = argb(255, 128, 128, 128)
        assertEquals(blend(mid, mid, SkBlendMode.kHardLight),
                     blend(mid, mid, SkBlendMode.kOverlay))
    }

    @Test
    fun `kOverlay opaque red on white is red`() {
        // s = (1, 0, 0), d = (1, 1, 1), sa = da = 1.
        // Per channel: 2*d ≤ da? d=1, so 2 ≤ 1 is false → light dst branch.
        //   rc = sa*da - 2*(da-d)*(sa-s) = 1 - 2*0*(1-s) = 1 for each chan.
        // Wait, but s=(1,0,0). For r channel: rc = 1 - 2*0*0 = 1.
        // For g channel: rc = 1 - 2*0*1 = 1. Hmm that gives white.
        // Let me re-check. carrier = (1-sa)*d + (1-da)*s = 0+0 = 0.
        // Body for r-chan, s=1, d=1, sa=1, da=1: 2*d=2, 2 ≤ 1 false ⇒
        //   sa*da - 2*(da-d)*(sa-s) = 1 - 2*0*0 = 1.
        // Body for g-chan, s=0, d=1: 2*d=2 ≤ 1 false ⇒ 1 - 2*0*1 = 1.
        // So r=1, g=1, b=1 = white.
        val out = blend(SK_ColorRED, SK_ColorWHITE, SkBlendMode.kOverlay)
        assertARGB(SK_ColorWHITE, out, tolerance = 1, label = "kOverlay red on white")
    }

    @Test
    fun `kColorDodge black src is identity for opaque dst`() {
        // dc = blue, sc = 0, sa = 1, da = 1.
        // For r,g chans (dc=0): rc = sc*(1-da) = 0. — wait that's 0, but
        // the dst was 0 too. OK.
        // For b chan (dc=1): sc=0 < sa=1, dc=1 > 0; ratio = 1*1/(1-0) = 1.
        //   min(1, 1) = 1. n=1. rc = 1*1 + 0*0 + 1*0 = 1. Blue stays blue.
        val out = blend(SK_ColorBLACK, SK_ColorBLUE, SkBlendMode.kColorDodge)
        assertARGB(SK_ColorBLUE, out, tolerance = 1, label = "kColorDodge black on blue")
    }

    @Test
    fun `kColorDodge white src on blue keeps blue`() {
        // Skia's branch order is `dc == 0` before `sc == sa`. For blue
        // dst, R and G channels have dc=0, so they take the
        // `rc = sc*(1-da) = 0` branch even though sc=sa would normally
        // saturate to 1. B channel keeps its 1. Net: (0, 0, 1) = blue.
        // (Differs from W3C `if Cs==1: 1` because Skia hits the `dc==0`
        // short-circuit first; matches our reference renders.)
        val out = blend(SK_ColorWHITE, SK_ColorBLUE, SkBlendMode.kColorDodge)
        assertARGB(SK_ColorBLUE, out, tolerance = 1, label = "kColorDodge white on blue")
    }

    @Test
    fun `kColorDodge white src on grey saturates to white`() {
        // For a non-zero dst (mid-grey), the sc==sa branch wins: rc =
        // sa*da + 0 + 0 = 1 for every channel. So white src on mid-grey
        // dst → white.
        val out = blend(SK_ColorWHITE, argb(255, 128, 128, 128), SkBlendMode.kColorDodge)
        assertARGB(SK_ColorWHITE, out, tolerance = 1, label = "kColorDodge white on 50% grey")
    }

    @Test
    fun `kColorBurn white src is identity for opaque dst`() {
        // dc = blue, sc = 1, sa = 1, da = 1.
        // For b chan (dc=1=da): rc = sa*da + sc*(1-da) + dc*(1-sa)
        //   = 1 + 0 + 0 = 1.
        // For r,g chans (dc=0, sc=1): dc < da; sc > 0; ratio = (1-0)*1/1 = 1.
        //   min(1, 1) = 1. n=1. rc = (1-1)*1 + 1*0 + 0*0 = 0.
        // Result: (R=0, G=0, B=1) = blue.
        val out = blend(SK_ColorWHITE, SK_ColorBLUE, SkBlendMode.kColorBurn)
        assertARGB(SK_ColorBLUE, out, tolerance = 1, label = "kColorBurn white on blue")
    }

    @Test
    fun `kColorBurn black src darkens dst to black`() {
        // sc = 0. Branch dc < da only matters for non-saturated dc.
        // For dc=1 (b chan of blue), dc==da so first branch:
        //   rc = sa*da + sc*(1-da) + dc*(1-sa) = 1 + 0 + 0 = 1.
        // For dc=0 (r,g chans), dc < da, sc <= 0: branch sc <= 0:
        //   rc = dc * (1 - sa) = 0 * 0 = 0.
        // So (R=0, G=0, B=1) = blue. That's because blue is already
        // saturated on its B channel; kColorBurn can't darken it further.
        val out = blend(SK_ColorBLACK, SK_ColorBLUE, SkBlendMode.kColorBurn)
        assertARGB(SK_ColorBLUE, out, tolerance = 1, label = "kColorBurn black on blue")
    }

    @Test
    fun `kSoftLight opaque half-grey is identity for fully-saturated dst`() {
        // s = (0.5, 0.5, 0.5), d = (0, 0, 1) blue, sa = da = 1.
        // 2*s = 1 = sa, so dark src branch (≤ sa is true).
        //   m = d/da = d
        //   B = d * (sa + (2*s - sa) * (1 - m)) = d * (1 + 0 * (1-m)) = d.
        // carrier = (1-1)*d + (1-1)*s = 0.
        // rc = d. So output dst as-is.
        val out = blend(argb(255, 128, 128, 128), SK_ColorBLUE, SkBlendMode.kSoftLight)
        assertARGB(SK_ColorBLUE, out, tolerance = 2, label = "kSoftLight 50% grey on blue")
    }

    @Test
    fun `kSoftLight opaque black darkens dst toward black`() {
        // s = 0, d = (0, 0, 1). 2*s=0 ≤ sa=1, dark branch.
        //   m_b = 1; B_b = 1 * (1 + (0 - 1) * (1 - 1)) = 1. Stays at 1.
        //   m_r = 0; B_r = 0 * (...) = 0.
        //   m_g = 0; B_g = 0.
        // carrier = 0.
        // rc = (0, 0, 1) = blue. SoftLight black src is no-op on saturated channels.
        val out = blend(SK_ColorBLACK, SK_ColorBLUE, SkBlendMode.kSoftLight)
        assertARGB(SK_ColorBLUE, out, tolerance = 2, label = "kSoftLight black on blue")
    }

    @Test
    fun `kSoftLight opaque white brightens half-grey toward white`() {
        // s = 1, d = 0.5, sa = da = 1. 2*s = 2 > sa, light src branch.
        //   m = 0.5. 4*d = 2 > da = 1, so bright-dst sub-branch.
        //   correction = sqrt(0.5) - 0.5 ≈ 0.707 - 0.5 = 0.207.
        //   B = d*sa + da*(2*s - sa)*correction = 0.5 + 1*1*0.207 = 0.707.
        // carrier = 0. rc = 0.707 → 181 in 8-bit.
        val out = blend(SK_ColorWHITE, argb(255, 128, 128, 128), SkBlendMode.kSoftLight)
        assertARGB(argb(255, 181, 181, 181), out, tolerance = 3, label = "kSoftLight white on 50% grey")
    }

    // ====================================================================
    // Phase 6 HSL: kHue, kSaturation, kColor, kLuminosity. These work on
    // the whole RGB tuple (not per-channel) and use the W3C SetLum /
    // SetSat / clipColor helpers. All formulas tested with sa=da=1 so
    // the premul / non-premul distinction is invisible.
    // ====================================================================

    @Test
    fun `kHue on a grey dst is identity`() {
        // Grey dst has Sat = 0, so SetSat(src, 0) zeroes out the source's
        // chrominance entirely. Then SetLum brings the result back to dst's
        // luminance — i.e. dst itself.
        val grey = argb(255, 128, 128, 128)
        val out = blend(SK_ColorRED, grey, SkBlendMode.kHue)
        assertARGB(grey, out, tolerance = 2, label = "kHue red on 50% grey")
    }

    @Test
    fun `kSaturation on a grey dst is identity`() {
        // Same reason as kHue on grey: SetSat(grey, _) collapses to 0,
        // then SetLum restores grey.
        val grey = argb(255, 128, 128, 128)
        val out = blend(SK_ColorRED, grey, SkBlendMode.kSaturation)
        assertARGB(grey, out, tolerance = 2, label = "kSaturation red on 50% grey")
    }

    @Test
    fun `kColor red on grey gives red-tinted output at grey luminance`() {
        // SetLum(red, Lum(grey=0.5)). Lum(red) = 0.3, diff = 0.2.
        // (red+0.2) = (1.2, 0.2, 0.2). mx=1.2 > 1 → clip:
        //   factor = (1-0.5)/(1.2-0.5) = 5/7 ≈ 0.714
        //   r = 0.5 + (1.2-0.5)*0.714 = 1.0
        //   g = 0.5 + (0.2-0.5)*0.714 ≈ 0.286 → 73
        //   b ≈ 0.286 → 73
        // Final: (255, 73, 73).
        val grey = argb(255, 128, 128, 128)
        val out = blend(SK_ColorRED, grey, SkBlendMode.kColor)
        assertARGB(argb(255, 255, 73, 73), out, tolerance = 3, label = "kColor red on 50% grey")
    }

    @Test
    fun `kLuminosity red on grey lowers grey to red's luminance`() {
        // SetLum(grey=0.5, Lum(red=0.3)). diff = -0.2.
        // (0.3, 0.3, 0.3). mn=mx=0.3, no clip. Result: 0.3 → 77.
        val grey = argb(255, 128, 128, 128)
        val out = blend(SK_ColorRED, grey, SkBlendMode.kLuminosity)
        assertARGB(argb(255, 77, 77, 77), out, tolerance = 2, label = "kLuminosity red on 50% grey")
    }

    @Test
    fun `kColor preserves dst luminance, takes src chrominance`() {
        // src = blue (Lum = 0.11), dst = mid-grey (Lum = 0.5).
        // SetLum(blue, 0.5). Lum(blue) = 0.11, diff = 0.39.
        // (0.39, 0.39, 1.39). mx=1.39 > 1, clip:
        //   factor = (1-0.5)/(1.39-0.5) = 0.5/0.89 ≈ 0.562
        //   r = g = 0.5 + (0.39-0.5)*0.562 ≈ 0.5 - 0.062 = 0.438 → 112
        //   b = 0.5 + (1.39-0.5)*0.562 = 0.5 + 0.5 = 1.0 → 255
        // Final: (112, 112, 255).
        val grey = argb(255, 128, 128, 128)
        val out = blend(SK_ColorBLUE, grey, SkBlendMode.kColor)
        assertARGB(argb(255, 112, 112, 255), out, tolerance = 3, label = "kColor blue on 50% grey")
    }

    @Test
    fun `kLuminosity preserves dst chrominance, takes src luminance`() {
        // src = white (Lum = 1), dst = blue (Lum = 0.11).
        // SetLum(blue, 1). Lum(blue) = 0.11, diff = 0.89.
        // (0.89, 0.89, 1.89). mx=1.89 > 1, clip:
        //   factor = (1-1)/(1.89-1) = 0/0.89 = 0
        //   r = g = 1 + (0.89-1)*0 = 1
        //   b = 1 + (1.89-1)*0 = 1
        // Final: white. (Pushing a saturated colour to lum=1 makes it white.)
        val out = blend(SK_ColorWHITE, SK_ColorBLUE, SkBlendMode.kLuminosity)
        assertARGB(SK_ColorWHITE, out, tolerance = 2, label = "kLuminosity white on blue")
    }

    @Test
    fun `kColor on transparent dst clears`() {
        // da=0 ⇒ a=sa*da=0 ⇒ body is in [0,0] = always 0. Carrier:
        //   sc*(1-da) + dc*(1-sa) = sc*1 + 0 = sc.
        // oa = sa+da*(1-sa) = sa. Un-premul: sc/sa = (255,0,0)/255 = red.
        val out = blend(SK_ColorRED, 0, SkBlendMode.kColor)
        assertARGB(SK_ColorRED, out, tolerance = 2, label = "kColor red on transparent")
    }

    @Test
    fun `all 29 modes are dispatched without throwing`() {
        // Sanity smoke: with all 29 modes implemented, blendPixel must not
        // throw NotImplementedError for any value of the SkBlendMode enum.
        for (m in SkBlendMode.entries) {
            blend(argb(128, 200, 100, 50), argb(180, 60, 200, 100), m)
        }
    }
}
