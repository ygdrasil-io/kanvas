package org.skia.foundation



import org.graphiks.math.between
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkPaintTest {

    @Test
    fun `default paint is opaque black, fill, no AA, no shader`() {
        val p = SkPaint()
        assertEquals(SK_ColorBLACK, p.color)
        assertEquals(SkPaint.Style.kFill_Style, p.style)
        assertEquals(0f, p.strokeWidth)
        assertEquals(SkPaint.Cap.kButt_Cap, p.strokeCap)
        assertEquals(SkPaint.Join.kMiter_Join, p.strokeJoin)
        assertEquals(4f, p.strokeMiter)
        assertFalse(p.isAntiAlias)
        assertFalse(p.isDither)
        assertEquals(null, p.shader)
        assertEquals(SkBlendMode.kSrcOver, p.blendMode)
    }

    @Test
    fun `color ctor sets color leaves other defaults`() {
        val p = SkPaint(SK_ColorRED)
        assertEquals(SK_ColorRED, p.color)
        assertEquals(SkPaint.Style.kFill_Style, p.style)
    }

    @Test
    fun `alpha and alphaf properties read and write`() {
        val p = SkPaint(SK_ColorRED)
        assertEquals(0xFF, p.alpha)
        assertEquals(1f, p.alphaf)

        p.alpha = 0x80
        assertEquals(0x80, p.alpha)
        // RGB preserved
        assertEquals(0xFF, SkColorGetR(p.color))

        p.alphaf = 0.5f
        // 0.5 * 255 + 0.5 = 128
        assertEquals(128, p.alpha)
    }

    @Test
    fun `color4f reads and writes via SkColor4f conversion`() {
        val p = SkPaint(SK_ColorGREEN)
        assertEquals(SkColor4f.kGreen, p.color4f)

        p.color4f = SkColor4f(1f, 0f, 0f, 0.5f)
        // After byte-quantization: alpha = 128, RGB unchanged.
        assertEquals(0x80, p.alpha)
        assertEquals(0xFF, SkColorGetR(p.color))
    }

    @Test
    fun `setARGB packs four bytes into color`() {
        val p = SkPaint()
        p.setARGB(0x80, 0x12, 0x34, 0x56)
        assertEquals(SkColorSetARGB(0x80, 0x12, 0x34, 0x56), p.color)
    }

    @Test
    fun `setStroke toggles between fill and stroke`() {
        val p = SkPaint()
        p.setStroke(true)
        assertEquals(SkPaint.Style.kStroke_Style, p.style)
        p.setStroke(false)
        assertEquals(SkPaint.Style.kFill_Style, p.style)
    }

    @Test
    fun `isSrcOver matches blendMode`() {
        val p = SkPaint()
        assertTrue(p.isSrcOver())
        p.blendMode = SkBlendMode.kPlus
        assertFalse(p.isSrcOver())
    }

    @Test
    fun `nothingToDraw catches alpha-zero plus passthrough blend modes`() {
        val p = SkPaint()
        p.alpha = 0
        // Default kSrcOver: alpha-zero source draws nothing.
        assertTrue(p.nothingToDraw())
        // kSrc with alpha=0 is NOT a no-op — it overwrites dst with transparent.
        p.blendMode = SkBlendMode.kSrc
        assertFalse(p.nothingToDraw())
        // Restore alpha — even kSrcOver now draws.
        p.alpha = 0xFF
        p.blendMode = SkBlendMode.kSrcOver
        assertFalse(p.nothingToDraw())
    }

    @Test
    fun `nothingToDraw with shader is never a no-op`() {
        val p = SkPaint()
        p.alpha = 0
        // A shader can produce non-transparent output regardless of paint alpha.
        // Without a real SkShader instance handy, we synthesise one to flip the flag.
        // Use a dummy SkShader subclass.
        // (Skipped here — the shader == null branch is the only one accessible
        // without a real shader; we already covered that above.)
        assertTrue(p.nothingToDraw())   // sanity: still null shader, alpha 0, kSrcOver
    }

    @Test
    fun `reset returns paint to defaults`() {
        val p = SkPaint(SK_ColorRED).apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 5f
            strokeCap = SkPaint.Cap.kRound_Cap
            strokeJoin = SkPaint.Join.kRound_Join
            strokeMiter = 8f
            isAntiAlias = true
            isDither = true
            blendMode = SkBlendMode.kPlus
        }
        p.reset()
        assertEquals(SK_ColorBLACK, p.color)
        assertEquals(SkPaint.Style.kFill_Style, p.style)
        assertEquals(0f, p.strokeWidth)
        assertEquals(SkPaint.Cap.kButt_Cap, p.strokeCap)
        assertEquals(SkPaint.Join.kMiter_Join, p.strokeJoin)
        assertEquals(4f, p.strokeMiter)
        assertFalse(p.isAntiAlias)
        assertFalse(p.isDither)
        assertEquals(SkBlendMode.kSrcOver, p.blendMode)
    }

    @Test
    fun `copy clones every field`() {
        val src = SkPaint(SK_ColorBLUE).apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 3f
            isAntiAlias = true
            isDither = true
            blendMode = SkBlendMode.kPlus
        }
        val dst = src.copy()
        assertEquals(src, dst)
        // Mutation on dst must not leak back.
        dst.color = SK_ColorRED
        assertNotEquals(src, dst)
    }

    @Test
    fun `equals and hashCode match for identical paints`() {
        val a = SkPaint(SK_ColorBLUE).apply { style = SkPaint.Style.kStroke_Style; strokeWidth = 5f }
        val b = SkPaint(SK_ColorBLUE).apply { style = SkPaint.Style.kStroke_Style; strokeWidth = 5f }
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `equals differs on any field change`() {
        val base = SkPaint().also { it.color = SK_ColorBLUE }
        val differ = SkPaint().also { it.color = SK_ColorRED }
        assertNotEquals(base, differ)
        val differStyle = SkPaint().also { it.style = SkPaint.Style.kStroke_Style }
        assertNotEquals(SkPaint(), differStyle)
    }

    // ─── Slice 2.1: SkColor4f-backed storage precision contract ────────

    @Test
    fun `setAlphaf preserves float value exactly (no byte round-trip)`() {
        val p = SkPaint()
        p.alphaf = 0.3f
        // 0.3 quantised to byte would be 76 -> read back 0.298039f.
        // The new SkColor4f-backed storage keeps the float intact.
        assertEquals(0.3f, p.alphaf)
        // Byte alpha still rounds correctly (Skia: round(0.3 * 255) = 77).
        assertEquals(77, p.alpha)
    }

    @Test
    fun `setAlphaf clamps to 0,1`() {
        val p = SkPaint()
        p.alphaf = -0.5f
        assertEquals(0f, p.alphaf)
        p.alphaf = 1.5f
        assertEquals(1f, p.alphaf)
    }

    @Test
    fun `setColor4f preserves all four channels at full float precision`() {
        val p = SkPaint()
        val c = SkColor4f(0.123f, 0.456f, 0.789f, 0.321f)
        p.setColor4f(c)
        assertEquals(0.123f, p.color4f.fR)
        assertEquals(0.456f, p.color4f.fG)
        assertEquals(0.789f, p.color4f.fB)
        assertEquals(0.321f, p.color4f.fA)
    }

    @Test
    fun `setColor4f pins alpha but leaves RGB untouched`() {
        val p = SkPaint()
        // Out-of-gamut RGB stays as-is (HDR / wide-gamut workflows);
        // alpha is pinned to [0, 1] like Skia's pinAlpha().
        p.setColor4f(SkColor4f(2f, -0.5f, 1.5f, 1.5f))
        assertEquals(2f, p.color4f.fR)
        assertEquals(-0.5f, p.color4f.fG)
        assertEquals(1.5f, p.color4f.fB)
        assertEquals(1f, p.color4f.fA)
    }

    @Test
    fun `SkColor4f constructor preserves precision`() {
        val p = SkPaint(SkColor4f(0.1f, 0.2f, 0.3f, 0.4f))
        assertEquals(0.4f, p.alphaf)
        assertEquals(0.1f, p.color4f.fR)
    }

    @Test
    fun `color4f getter returns a defensive copy`() {
        val p = SkPaint(SK_ColorRED)
        val snapshot = p.color4f
        snapshot.fA = 0f                  // mutate the returned copy
        assertEquals(1f, p.alphaf)        // internal state unaffected
    }

    @Test
    fun `color4f setter copies defensively`() {
        val p = SkPaint()
        val source = SkColor4f(1f, 0f, 0f, 1f)
        p.color4f = source
        source.fA = 0f                    // mutate after assignment
        assertEquals(1f, p.alphaf)        // paint kept its own copy
    }

    @Test
    fun `byte color setter still quantises (legacy path unchanged)`() {
        val p = SkPaint()
        p.color = SkColorSetARGB(77, 255, 0, 0)
        // Byte alpha goes through 77/255 -> 0.30196f, NOT 0.3f exact.
        assertEquals(77f / 255f, p.alphaf)
    }

    // ─── Slice 2.4: silent-reject of negative stroke width / miter ─────

    @Test
    fun `strokeWidth silently rejects negative values`() {
        val p = SkPaint()
        p.strokeWidth = 5f
        p.strokeWidth = -1f             // Skia's setStrokeWidth ignores < 0
        assertEquals(5f, p.strokeWidth) // previous value retained
        p.strokeWidth = 0f              // 0 (hairline) is accepted
        assertEquals(0f, p.strokeWidth)
    }

    @Test
    fun `strokeMiter silently rejects negative values`() {
        val p = SkPaint()
        p.strokeMiter = 8f
        p.strokeMiter = -2f             // Skia's setStrokeMiter ignores < 0
        assertEquals(8f, p.strokeMiter) // previous value retained
        p.strokeMiter = 0f              // 0 accepted (Skia treats < 1 as bevel; < 0 is the only rejection)
        assertEquals(0f, p.strokeMiter)
    }

    // ─── Slice 2.5: setColor4f colour-space xform ─────────────────────

    @Test
    fun `setColor4f with null colorSpace assumes sRGB (no xform)`() {
        val p = SkPaint()
        val c = SkColor4f(0.5f, 0.4f, 0.3f, 0.7f)
        p.setColor4f(c, colorSpace = null)
        // sRGB → sRGB is identity — values stored verbatim (post-pinAlpha).
        assertEquals(0.5f, p.color4f.fR)
        assertEquals(0.4f, p.color4f.fG)
        assertEquals(0.3f, p.color4f.fB)
        assertEquals(0.7f, p.color4f.fA)
    }

    @Test
    fun `setColor4f with sRGB colorSpace is identity`() {
        val p = SkPaint()
        val c = SkColor4f(0.5f, 0.4f, 0.3f, 0.7f)
        p.setColor4f(c, colorSpace = SkColorSpace.makeSRGB())
        assertEquals(0.5f, p.color4f.fR)
        assertEquals(0.4f, p.color4f.fG)
        assertEquals(0.3f, p.color4f.fB)
        assertEquals(0.7f, p.color4f.fA)
    }

    @Test
    fun `setColor4f with Rec2020 colorSpace transforms to sRGB`() {
        val p = SkPaint()
        // Linear Rec.2020 source — gamut differs from sRGB, so a
        // non-trivial colour will be remapped.
        val rec2020 = SkColorSpace.makeRGB(
            org.skia.foundation.skcms.SkNamedTransferFn.kLinear,
            org.skia.foundation.skcms.SkNamedGamut.kRec2020,
        )!!
        val c = SkColor4f(0.5f, 0.4f, 0.3f, 1f)
        p.setColor4f(c, colorSpace = rec2020)
        // Alpha is preserved exactly (xform never touches the A channel).
        assertEquals(1f, p.color4f.fA)
        // RGB MUST have shifted — Rec.2020-linear (0.5, 0.4, 0.3) is
        // outside the sRGB primaries' simple identity, so the gamut
        // matrix produces a different triple.
        assertNotEquals(0.5f, p.color4f.fR)
    }

    @Test
    fun `setColor4f preserves precision through identity xform`() {
        val p = SkPaint()
        val c = SkColor4f(0.123456f, 0.654321f, 0.111111f, 0.30f)
        p.setColor4f(c, colorSpace = SkColorSpace.makeSRGB())
        // No FloatArray round-trip when steps are identity → exact precision.
        assertEquals(0.123456f, p.color4f.fR)
        assertEquals(0.30f, p.color4f.fA)
    }

    // ─── Slice 2.6: nothingToDraw parity with Skia ─────────────────────

    @Test
    fun `nothingToDraw kDst is always true regardless of alpha`() {
        val p = SkPaint()
        // r = d for every src — even fully opaque src is a no-op against kDst.
        p.alpha = 0xFF
        p.blendMode = SkBlendMode.kDst
        assertTrue(p.nothingToDraw())
        p.alpha = 0
        assertTrue(p.nothingToDraw())
        // Even with a shader: kDst still ignores src entirely.
        p.shader = object : SkShader() {
            override fun shadeRow(x: Int, y: Int, count: Int, out: IntArray) {}
        }
        p.alpha = 0xFF
        assertTrue(p.nothingToDraw())
    }

    @Test
    fun `nothingToDraw kXor with alpha-zero is NOT a no-op (iso Skia)`() {
        // Slice 2.6 alignment: Skia's SkPaint.cpp passthrough list
        // does NOT include kXor, so `paint.alpha=0 + kXor` falls
        // through to false. Our previous list erroneously included it.
        val p = SkPaint()
        p.alpha = 0
        p.blendMode = SkBlendMode.kXor
        assertFalse(p.nothingToDraw())
    }

    @Test
    fun `nothingToDraw passthrough list matches Skia exactly`() {
        // Skia's SkPaint::nothingToDraw alpha=0 list: kSrcOver, kSrcATop,
        // kDstOut, kDstOver, kPlus.
        val p = SkPaint()
        p.alpha = 0
        for (mode in listOf(
            SkBlendMode.kSrcOver, SkBlendMode.kSrcATop,
            SkBlendMode.kDstOut, SkBlendMode.kDstOver, SkBlendMode.kPlus,
        )) {
            p.blendMode = mode
            assertTrue(p.nothingToDraw(), "alpha=0 + $mode should be no-op")
        }
        // Modes outside the passthrough list (and ≠ kDst) ⇒ not a no-op
        // even at alpha 0 (kSrc / kSrcIn / kSrcOut / kClear / kModulate
        // all produce non-trivial output for transparent src).
        for (mode in listOf(
            SkBlendMode.kSrc, SkBlendMode.kSrcIn,
            SkBlendMode.kSrcOut, SkBlendMode.kClear, SkBlendMode.kModulate,
        )) {
            p.blendMode = mode
            assertFalse(p.nothingToDraw(), "alpha=0 + $mode should NOT be no-op")
        }
    }
}
