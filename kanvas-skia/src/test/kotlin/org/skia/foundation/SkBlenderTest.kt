package org.skia.foundation



import org.skia.math.between
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorRED
import org.skia.math.SkColor4f
import org.skia.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.math.SkRect
import kotlin.math.abs

/**
 * D2.0 verification suite for [SkBlender], [SkBlendModeBlender]
 * and the [SkBlenders] static factories. Pixel-level integration
 * with `paint.blender` is in [`SkPaintBlenderRasterTest`](
 * SkPaintBlenderRasterTest.kt).
 *
 * **Behaviour under test** :
 *  - [SkBlender.Mode] returns a [SkBlendModeBlender] carrying the
 *    requested mode tag.
 *  - [SkBlendModeBlender.blend] implements the trivial modes
 *    (Clear / Src / Dst / SrcOver) directly ; non-trivial modes
 *    throw [UnsupportedOperationException] (the device dispatches
 *    around them via the legacy fast paths instead).
 *  - [SkBlenders.Arithmetic] :
 *    - rejects non-finite coefficients with `null` ;
 *    - collapses canonical mode-equivalent tuples to a
 *      [SkBlendModeBlender] short-circuit ;
 *    - otherwise returns a [SkArithmeticBlender] carrying the
 *      coefficients + premul flag ;
 *    - the formula matches `saturate(k1·src·dst + k2·src + k3·dst
 *      + k4)` per channel, with the [enforcePremul] post-clamp.
 *  - Equality / hashCode is correct for every blender variant.
 */
class SkBlenderTest {

    // ─── SkBlender.Mode factory ────────────────────────────────────────

    @Test
    fun `Mode returns an SkBlendModeBlender carrying the input mode`() {
        for (mode in SkBlendMode.values()) {
            val b = SkBlender.Mode(mode)
            assertTrue(b is SkBlendModeBlender, "Mode($mode) returns SkBlendModeBlender")
            assertEquals(mode, (b as SkBlendModeBlender).mode)
        }
    }

    @Test
    fun `Mode equality is mode-tag based`() {
        assertEquals(SkBlender.Mode(SkBlendMode.kSrcOver), SkBlender.Mode(SkBlendMode.kSrcOver))
        assertNotEquals(SkBlender.Mode(SkBlendMode.kSrcOver), SkBlender.Mode(SkBlendMode.kSrc))
        assertEquals(
            SkBlender.Mode(SkBlendMode.kSrcOver).hashCode(),
            SkBlender.Mode(SkBlendMode.kSrcOver).hashCode(),
        )
    }

    // ─── SkBlendModeBlender.blend — trivial modes ─────────────────────

    @Test
    fun `Clear mode blend always returns transparent`() {
        val b = SkBlender.Mode(SkBlendMode.kClear)
        val out = b.blend(SkColor4f.kRed, SkColor4f.kBlue)
        assertEquals(SkColor4f(0f, 0f, 0f, 0f), out)
    }

    @Test
    fun `Src mode blend returns src verbatim`() {
        val b = SkBlender.Mode(SkBlendMode.kSrc)
        val src = SkColor4f(0.7f, 0.3f, 0.1f, 0.8f)
        val dst = SkColor4f(0.1f, 0.2f, 0.9f, 1f)
        val out = b.blend(src, dst)
        assertEquals(src, out, "kSrc returns src")
    }

    @Test
    fun `Dst mode blend returns dst verbatim`() {
        val b = SkBlender.Mode(SkBlendMode.kDst)
        val src = SkColor4f(0.7f, 0.3f, 0.1f, 0.8f)
        val dst = SkColor4f(0.1f, 0.2f, 0.9f, 1f)
        val out = b.blend(src, dst)
        assertEquals(dst, out, "kDst returns dst")
    }

    @Test
    fun `SrcOver mode blend with opaque src returns src`() {
        val b = SkBlender.Mode(SkBlendMode.kSrcOver)
        val src = SkColor4f(1f, 0f, 0f, 1f)
        val dst = SkColor4f(0f, 0f, 1f, 1f)
        val out = b.blend(src, dst)
        assertEquals(src, out, "opaque src + opaque dst → src for kSrcOver")
    }

    @Test
    fun `SrcOver mode blend with transparent src returns dst`() {
        val b = SkBlender.Mode(SkBlendMode.kSrcOver)
        val src = SkColor4f(1f, 0f, 0f, 0f) // alpha = 0
        val dst = SkColor4f(0f, 0f, 1f, 1f)
        val out = b.blend(src, dst)
        // alpha = 0 + 1 · 1 = 1 ; rgb = (0 + 0·1·1) / 1 = 0,0,1
        assertNearlyEqual(dst, out, 1e-5f)
    }

    @Test
    fun `SrcOver mode blend with semi-transparent src lerps`() {
        val b = SkBlender.Mode(SkBlendMode.kSrcOver)
        val src = SkColor4f(1f, 0f, 0f, 0.5f) // half-opaque red
        val dst = SkColor4f(0f, 0f, 1f, 1f)   // opaque blue
        val out = b.blend(src, dst)
        // outA = 0.5 + 1·0.5 = 1
        // outR = (1·0.5 + 0·1·0.5) / 1 = 0.5
        // outG = 0 ; outB = (0·0.5 + 1·1·0.5) / 1 = 0.5
        assertNearlyEqual(SkColor4f(0.5f, 0f, 0.5f, 1f), out, 1e-5f)
    }

    @Test
    fun `SrcOver with both transparent collapses to transparent`() {
        val b = SkBlender.Mode(SkBlendMode.kSrcOver)
        val out = b.blend(SkColor4f.kTransparent, SkColor4f.kTransparent)
        assertEquals(SkColor4f(0f, 0f, 0f, 0f), out)
    }

    @Test
    fun `Non-trivial modes throw on direct blend`() {
        for (mode in SkBlendMode.values()) {
            if (mode in setOf(
                    SkBlendMode.kClear, SkBlendMode.kSrc, SkBlendMode.kDst, SkBlendMode.kSrcOver,
                )) continue
            val b = SkBlender.Mode(mode) as SkBlendModeBlender
            var caught = false
            try {
                b.blend(SkColor4f.kRed, SkColor4f.kBlue)
            } catch (e: UnsupportedOperationException) {
                caught = true
                assertTrue(e.message?.contains("$mode") == true, "error mentions the mode tag")
            }
            assertTrue(caught, "$mode must throw on direct .blend() (route through SkBitmapDevice)")
        }
    }

    // ─── SkBlenders.Arithmetic — factory invariants ────────────────────

    @Test
    fun `Arithmetic rejects non-finite coefficients`() {
        assertNull(SkBlenders.Arithmetic(Float.NaN, 0f, 0f, 0f, true))
        assertNull(SkBlenders.Arithmetic(0f, Float.POSITIVE_INFINITY, 0f, 0f, true))
        assertNull(SkBlenders.Arithmetic(0f, 0f, Float.NEGATIVE_INFINITY, 0f, true))
        assertNull(SkBlenders.Arithmetic(0f, 0f, 0f, Float.NaN, true))
    }

    @Test
    fun `Arithmetic short-circuits canonical mode tuples`() {
        // (0, 1, 0, 0) → kSrc
        val src = SkBlenders.Arithmetic(0f, 1f, 0f, 0f, true)
        assertTrue(src is SkBlendModeBlender)
        assertEquals(SkBlendMode.kSrc, (src as SkBlendModeBlender).mode)

        // (0, 0, 1, 0) → kDst
        val dst = SkBlenders.Arithmetic(0f, 0f, 1f, 0f, true)
        assertTrue(dst is SkBlendModeBlender)
        assertEquals(SkBlendMode.kDst, (dst as SkBlendModeBlender).mode)

        // (0, 0, 0, 0) → kClear
        val clr = SkBlenders.Arithmetic(0f, 0f, 0f, 0f, true)
        assertTrue(clr is SkBlendModeBlender)
        assertEquals(SkBlendMode.kClear, (clr as SkBlendModeBlender).mode)
    }

    @Test
    fun `Arithmetic returns SkArithmeticBlender for non-canonical tuples`() {
        val b = SkBlenders.Arithmetic(0.5f, 0.25f, 0.25f, 0f, true)
        assertTrue(b is SkArithmeticBlender)
        val arith = b as SkArithmeticBlender
        assertEquals(0.5f, arith.k1)
        assertEquals(0.25f, arith.k2)
        assertEquals(0.25f, arith.k3)
        assertEquals(0f, arith.k4)
        assertTrue(arith.enforcePremul)
    }

    // ─── SkArithmeticBlender.blend — formula correctness ──────────────

    @Test
    fun `Arithmetic with k2=1 reduces to kSrc`() {
        // Bypass the short-circuit by using a slightly-off coefficient,
        // then check the formula collapses to src for the residue.
        val b = SkArithmeticBlender(0f, 1f, 0f, 0f, false)
        val src = SkColor4f(0.7f, 0.3f, 0.1f, 0.8f)
        val dst = SkColor4f(0.1f, 0.2f, 0.9f, 1f)
        val out = b.blend(src, dst)
        assertNearlyEqual(src, out, 1e-5f)
    }

    @Test
    fun `Arithmetic with k3=1 reduces to kDst`() {
        val b = SkArithmeticBlender(0f, 0f, 1f, 0f, false)
        val src = SkColor4f(0.7f, 0.3f, 0.1f, 0.8f)
        val dst = SkColor4f(0.1f, 0.2f, 0.9f, 1f)
        val out = b.blend(src, dst)
        assertNearlyEqual(dst, out, 1e-5f)
    }

    @Test
    fun `Arithmetic with all zero except k4=1 returns opaque white`() {
        // formula: 0+0+0+1 = 1 per channel → all white, alpha 1
        val b = SkArithmeticBlender(0f, 0f, 0f, 1f, false)
        val out = b.blend(SkColor4f(0.5f, 0.5f, 0.5f, 0.5f), SkColor4f(0.2f, 0.3f, 0.4f, 1f))
        assertNearlyEqual(SkColor4f(1f, 1f, 1f, 1f), out, 1e-5f)
    }

    @Test
    fun `Arithmetic saturates each channel independently`() {
        // formula: k2=2 — src · 2 saturates to 1 for any positive src.
        val b = SkArithmeticBlender(0f, 2f, 0f, 0f, false)
        val src = SkColor4f(0.7f, 0.5f, 0.1f, 0.8f) // *2 = 1.4, 1.0, 0.2, 1.6
        val out = b.blend(src, SkColor4f.kTransparent)
        // Saturate clamps to [0, 1]: 1, 1, 0.2, 1
        assertNearlyEqual(SkColor4f(1f, 1f, 0.2f, 1f), out, 1e-5f)
    }

    @Test
    fun `Arithmetic with enforcePremul caps RGB to alpha`() {
        // formula : k1·src·dst + k2·src + 0 + 0 = src·(k2 + k1·dst).
        // For k1=0, k2=1 : same as kSrc. With enforcePremul = true,
        // we'd cap RGB to alpha — try a non-premul input and check.
        val b = SkArithmeticBlender(0f, 1f, 0f, 0f, true)
        // Non-premul: rgb > alpha is illegal but we accept it as input.
        // Output's RGB is clamped to alpha = 0.5.
        val src = SkColor4f(0.8f, 0.7f, 0.6f, 0.5f)
        val out = b.blend(src, SkColor4f.kBlack)
        assertNearlyEqual(SkColor4f(0.5f, 0.5f, 0.5f, 0.5f), out, 1e-5f,
            "enforcePremul should cap RGB to alpha")
    }

    @Test
    fun `Arithmetic without enforcePremul leaves RGB unclamped vs alpha`() {
        val b = SkArithmeticBlender(0f, 1f, 0f, 0f, false)
        val src = SkColor4f(0.8f, 0.7f, 0.6f, 0.5f)
        val out = b.blend(src, SkColor4f.kBlack)
        // Without premul-clamp, RGB stays as input (saturate already
        // capped to ≤ 1).
        assertNearlyEqual(src, out, 1e-5f)
    }

    @Test
    fun `Arithmetic equality is field-by-field`() {
        val a = SkArithmeticBlender(0.5f, 0.25f, 0.25f, 0f, true)
        val b = SkArithmeticBlender(0.5f, 0.25f, 0.25f, 0f, true)
        val c = SkArithmeticBlender(0.5f, 0.25f, 0.25f, 0f, false) // different premul
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    // ─── End-to-end pixel parity via paint.blender = Mode(...) ────────

    @Test
    fun `paint blender = Mode(kSrcOver) is pixel-iso with paint blendMode = kSrcOver`() {
        val withBlender = SkBitmap(8, 8).also { it.eraseColor(SK_ColorBLUE) }
        val withMode = SkBitmap(8, 8).also { it.eraseColor(SK_ColorBLUE) }
        SkCanvas(withBlender).drawRect(
            SkRect.MakeWH(8f, 8f),
            SkPaint(SkColorSetARGB(0x80, 0xFF, 0, 0)).apply {
                blender = SkBlender.Mode(SkBlendMode.kSrcOver)
            },
        )
        SkCanvas(withMode).drawRect(
            SkRect.MakeWH(8f, 8f),
            SkPaint(SkColorSetARGB(0x80, 0xFF, 0, 0)).apply {
                blendMode = SkBlendMode.kSrcOver
            },
        )
        for (i in withBlender.pixels.indices) {
            assertEquals(
                withMode.pixels[i],
                withBlender.pixels[i],
                "pixel $i drift between paint.blender = Mode(kSrcOver) and paint.blendMode = kSrcOver",
            )
        }
    }

    @Test
    fun `paint blender = Mode(kClear) zeroes the rect`() {
        val bm = SkBitmap(8, 8).also { it.eraseColor(SK_ColorBLUE) }
        SkCanvas(bm).drawRect(
            SkRect.MakeWH(8f, 8f),
            SkPaint(SK_ColorRED).apply { blender = SkBlender.Mode(SkBlendMode.kClear) },
        )
        for (px in bm.pixels) assertEquals(0, px, "kClear blender must zero every pixel")
    }

    @Test
    fun `paint blender Arithmetic average produces midpoint pixels`() {
        // k1=0, k2=0.5, k3=0.5, k4=0 → out = 0.5·src + 0.5·dst (lerp 50/50).
        val bm = SkBitmap(8, 8).also { it.eraseColor(SK_ColorBLUE) } // 0xFF0000FF
        SkCanvas(bm).drawRect(
            SkRect.MakeWH(8f, 8f),
            SkPaint(SK_ColorRED).apply { // 0xFFFF0000
                blender = SkBlenders.Arithmetic(0f, 0.5f, 0.5f, 0f, true)
            },
        )
        // out_r ≈ 0.5 ; out_g = 0 ; out_b ≈ 0.5 ; out_a = 1 → 0xFF80_0080-ish
        // (with enforcePremul cap to alpha=1, no change)
        val mid = bm.getPixel(4, 4)
        val r = (mid ushr 16) and 0xFF
        val g = (mid ushr 8) and 0xFF
        val b = mid and 0xFF
        val a = mid ushr 24
        assertTrue(abs(r - 128) <= 2, "R should be ≈ 128, got $r")
        assertEquals(0, g, "G should be 0")
        assertTrue(abs(b - 128) <= 2, "B should be ≈ 128, got $b")
        assertEquals(0xFF, a)
    }

    // ─── Paint round-trip ─────────────────────────────────────────────

    @Test
    fun `paint copy preserves blender`() {
        val p = SkPaint(SK_ColorRED).apply {
            blender = SkBlender.Mode(SkBlendMode.kPlus)
        }
        val q = p.copy()
        assertEquals(p.blender, q.blender)
    }

    @Test
    fun `paint reset clears blender`() {
        val p = SkPaint(SK_ColorRED).apply {
            blender = SkBlender.Mode(SkBlendMode.kPlus)
        }
        p.reset()
        assertNull(p.blender)
    }

    @Test
    fun `paint equals + hashCode account for blender`() {
        val a = SkPaint(SK_ColorRED).apply { blender = SkBlender.Mode(SkBlendMode.kPlus) }
        val b = SkPaint(SK_ColorRED).apply { blender = SkBlender.Mode(SkBlendMode.kPlus) }
        val c = SkPaint(SK_ColorRED).apply { blender = SkBlender.Mode(SkBlendMode.kSrcOver) }
        val d = SkPaint(SK_ColorRED) // null blender
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertNotEquals(a, d)
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private fun assertNearlyEqual(
        expected: SkColor4f,
        actual: SkColor4f,
        tol: Float,
        msg: String = "",
    ) {
        val drift = floatArrayOf(
            abs(expected.fR - actual.fR),
            abs(expected.fG - actual.fG),
            abs(expected.fB - actual.fB),
            abs(expected.fA - actual.fA),
        )
        assertTrue(
            drift.all { it < tol },
            "$msg : expected=$expected, actual=$actual, drift=${drift.toList()}",
        )
    }
}
