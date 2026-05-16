package org.skia.foundation


import org.skia.math.SK_ColorWHITE
import org.skia.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.math.SkRect
import kotlin.math.abs

/**
 * Phase **C3** verification suite for [SkEmbossMaskFilter].
 *
 * Two tiers :
 *  - **Unit tests** on the factory + the per-pixel emboss kernel —
 *    Make returns `null` for invalid inputs ; light direction
 *    normalisation ; format declared as [SkMaskFilter.Format.k3D] ;
 *    filterMask3D produces three same-sized planes ; alpha plane
 *    is preserved verbatim from the input.
 *  - **Integration test** through `drawRect` + a paint with the
 *    emboss filter — confirms the 3-plane dispatch in
 *    `SkBitmapDevice.drawPathWithMaskFilter` actually runs and
 *    produces non-uniform output (a flat fill would mean the
 *    multiply / additive planes weren't being applied).
 *
 * GM port : the upstream `EmbossGM` uses `drawImage` with a
 * mask filter — that path is not implemented in
 * `kanvas-skia`'s raster pipeline today (mask filters apply to
 * paths and rrects, not raw bitmap blits). A faithful port
 * would need image-blit-with-maskFilter support first ;
 * deferred outside this slice.
 */
class SkEmbossMaskFilterTest {

    // ─── Factory + invariants ─────────────────────────────────────────

    @Test
    fun `Make returns null for invalid blurSigma`() {
        val light = SkEmbossMaskFilter.Light(floatArrayOf(1f, 1f, 1f), ambient = 0, specular = 0)
        assertNull(SkEmbossMaskFilter.Make(0f, light))
        assertNull(SkEmbossMaskFilter.Make(-1f, light))
        assertNull(SkEmbossMaskFilter.Make(Float.NaN, light))
        assertNull(SkEmbossMaskFilter.Make(Float.POSITIVE_INFINITY, light))
    }

    @Test
    fun `Make returns null for zero-length light direction`() {
        val light = SkEmbossMaskFilter.Light(floatArrayOf(0f, 0f, 0f), ambient = 64, specular = 16)
        assertNull(SkEmbossMaskFilter.Make(2f, light))
    }

    @Test
    fun `Make accepts a valid blurSigma + light`() {
        val light = SkEmbossMaskFilter.Light(floatArrayOf(1f, 1f, 1f), ambient = 64, specular = 16)
        val mf = SkEmbossMaskFilter.Make(2f, light)
        assertNotNull(mf)
        assertTrue(mf is SkEmbossMaskFilter)
    }

    @Test
    fun `format is k3D so devices know to dispatch through filterMask3D`() {
        val mf = SkEmbossMaskFilter.Make(
            2f,
            SkEmbossMaskFilter.Light(floatArrayOf(0f, 0f, 1f), ambient = 0, specular = 0),
        )!!
        assertEquals(SkMaskFilter.Format.k3D, mf.format)
    }

    @Test
    fun `margin scales with blurSigma`() {
        val light = SkEmbossMaskFilter.Light(floatArrayOf(0f, 0f, 1f), ambient = 0, specular = 0)
        val small = SkEmbossMaskFilter.Make(1f, light)!!
        val big = SkEmbossMaskFilter.Make(5f, light)!!
        assertTrue(big.margin() > small.margin(), "larger sigma must want a wider margin")
        // 3-sigma rule : margin ≥ ceil(3 × sigma).
        assertTrue(small.margin() >= 3)
        assertTrue(big.margin() >= 15)
    }

    // ─── filterMask3D shape + invariants ──────────────────────────────

    @Test
    fun `filterMask3D returns three same-sized planes`() {
        val mf = SkEmbossMaskFilter.Make(
            2f,
            SkEmbossMaskFilter.Light(floatArrayOf(1f, 1f, 1f), ambient = 64, specular = 16),
        )!!
        val w = 16; val h = 16
        val src = ByteArray(w * h) { 0x80.toByte() } // mid-grey coverage
        val mask = mf.filterMask3D(src, w, h)
        assertEquals(w * h, mask.alpha.size)
        assertEquals(w * h, mask.multiply.size)
        assertEquals(w * h, mask.additive.size)
    }

    @Test
    fun `filterMask3D preserves the original coverage as the alpha plane`() {
        val mf = SkEmbossMaskFilter.Make(
            2f,
            SkEmbossMaskFilter.Light(floatArrayOf(0f, 0f, 1f), ambient = 0, specular = 0),
        )!!
        val w = 8; val h = 8
        // Source : a step function — left half opaque, right half transparent.
        val src = ByteArray(w * h) { i ->
            if ((i % w) < w / 2) 0xFF.toByte() else 0
        }
        val mask = mf.filterMask3D(src, w, h)
        // Alpha plane must equal src verbatim — preserves the path's
        // edge AA exactly. The blur only feeds the height-field
        // gradient computation.
        assertTrue(mask.alpha.contentEquals(src), "alpha plane must equal src ; emboss only modulates colour, not coverage")
    }

    @Test
    fun `flat coverage produces uniform multiply on the interior`() {
        // A fully-uniform mask has zero gradient, so the dot product
        // is constant across the interior — every interior pixel sees
        // the same `multiply` and `additive`. Boundary pixels
        // sample 0 outside the buffer and skew, so we sample two
        // points well inside the 8×8 region.
        val light = SkEmbossMaskFilter.Light(floatArrayOf(0f, 0f, 1f), ambient = 64, specular = 16)
        val mf = SkEmbossMaskFilter.Make(2f, light)!!
        val w = 8; val h = 8
        val src = ByteArray(w * h) { 0xFF.toByte() }
        val mask = mf.filterMask3D(src, w, h)
        val centre = mask.multiply[3 * w + 3].toInt() and 0xFF
        val centre2 = mask.multiply[4 * w + 4].toInt() and 0xFF
        assertEquals(centre, centre2, "flat interior must produce uniform multiply")
    }

    @Test
    fun `higher specular dampens additive`() {
        // The 4.4 fixed-point specular controls how many times the
        // raised-cosine peak is multiplied with itself : higher
        // specular = sharper falloff = smaller additive on slopes
        // that aren't perfectly aligned with the light. We check
        // the monotone relation : `specular = 0` (no falloff,
        // additive = raw hilite) must produce a larger additive
        // than `specular = 64` (4 iterations of div255).
        val direction = floatArrayOf(1f, 1f, 1f)
        val w = 16; val h = 16
        val src = ByteArray(w * h) { i -> if ((i % w) < w / 2) 0xFF.toByte() else 0 }
        val low = SkEmbossMaskFilter.Make(
            2f, SkEmbossMaskFilter.Light(direction, ambient = 0, specular = 0),
        )!!.filterMask3D(src, w, h)
        val high = SkEmbossMaskFilter.Make(
            2f, SkEmbossMaskFilter.Light(direction, ambient = 0, specular = 64),
        )!!.filterMask3D(src, w, h)
        var sumLow = 0
        var sumHigh = 0
        for (i in low.additive.indices) {
            sumLow += low.additive[i].toInt() and 0xFF
            sumHigh += high.additive[i].toInt() and 0xFF
        }
        assertTrue(sumLow > sumHigh, "lower specular must produce larger total additive ; got $sumLow vs $sumHigh")
    }

    @Test
    fun `non-flat coverage produces non-uniform multiply (the diffuse term varies)`() {
        // A vertical step gives a strong nx gradient at the boundary.
        // With light from the left (lx = 1, ly = 0, lz small), the
        // pixel just left of the edge sees positive dot(L, N), the
        // pixel just right sees zero — multiply must differ.
        val light = SkEmbossMaskFilter.Light(floatArrayOf(1f, 0f, 0.1f), ambient = 32, specular = 0)
        val mf = SkEmbossMaskFilter.Make(2f, light)!!
        val w = 16; val h = 16
        val src = ByteArray(w * h) { i ->
            if ((i % w) < w / 2) 0xFF.toByte() else 0
        }
        val mask = mf.filterMask3D(src, w, h)
        // Sample two columns either side of the step.
        val leftCol = (w / 2) - 2
        val rightCol = (w / 2) + 2
        val muLeft = mask.multiply[h / 2 * w + leftCol].toInt() and 0xFF
        val muRight = mask.multiply[h / 2 * w + rightCol].toInt() and 0xFF
        assertTrue(
            muLeft != muRight,
            "step + side-light must produce different multiplies left vs right ; got $muLeft vs $muRight",
        )
    }

    @Test
    fun `non-zero specular produces non-zero additive on lit slopes`() {
        val light = SkEmbossMaskFilter.Light(floatArrayOf(1f, 1f, 1f), ambient = 0, specular = 64)
        val mf = SkEmbossMaskFilter.Make(2f, light)!!
        val w = 16; val h = 16
        val src = ByteArray(w * h) { i ->
            // Diagonal step — half opaque, half transparent.
            if ((i / w) + (i % w) < w) 0xFF.toByte() else 0
        }
        val mask = mf.filterMask3D(src, w, h)
        var maxAdd = 0
        for (b in mask.additive) maxAdd = maxOf(maxAdd, b.toInt() and 0xFF)
        assertTrue(maxAdd > 0, "specular > 0 + lit slope must produce some additive highlight")
    }

    // ─── Integration through SkBitmapDevice ───────────────────────────

    @Test
    fun `drawRect with emboss paint produces non-uniform pixel output`() {
        // End-to-end : a 50×50 bitmap, draw a centred rect with
        // emboss filter ; verify the resulting pixel field is NOT
        // uniformly the paint colour (which would mean the multiply
        // / additive planes weren't applied).
        val bitmap = SkBitmap(50, 50)
        bitmap.eraseColor(SkColorSetARGB(0xFF, 0x80, 0x80, 0x80))
        val canvas = SkCanvas(bitmap)
        val light = SkEmbossMaskFilter.Light(
            direction = floatArrayOf(1f, 1f, 1f),
            ambient = 64,
            specular = 32,
        )
        val paint = SkPaint().apply {
            color = SK_ColorWHITE
            isAntiAlias = true
            maskFilter = SkEmbossMaskFilter.Make(3f, light)
        }
        canvas.drawRect(SkRect.MakeXYWH(10f, 10f, 30f, 30f), paint)

        // Sample a few pixels inside the painted region. They should
        // NOT all be the bg colour (the rect drew over them) AND
        // they should NOT all be plain white (emboss modulated them).
        val samples = listOf(
            Pair(15, 15), Pair(25, 25), Pair(35, 35),
            Pair(15, 25), Pair(25, 15),
        )
        var distinctCount = 0
        val seen = mutableSetOf<Int>()
        for ((x, y) in samples) {
            val px = bitmap.getPixel(x, y)
            seen.add(px)
        }
        distinctCount = seen.size
        assertTrue(
            distinctCount > 1,
            "emboss should produce non-uniform pixels inside the rect ; got $distinctCount distinct values across ${samples.size} samples : $seen",
        )
    }

    @Test
    fun `drawRect without emboss produces uniform pixel output (control)`() {
        // Sanity check : the same drawRect WITHOUT a maskFilter must
        // produce uniform output. If this fails, the previous test's
        // signal is contaminated by something else.
        val bitmap = SkBitmap(50, 50)
        bitmap.eraseColor(SkColorSetARGB(0xFF, 0x80, 0x80, 0x80))
        val canvas = SkCanvas(bitmap)
        val paint = SkPaint().apply {
            color = SK_ColorWHITE
            isAntiAlias = false
        }
        canvas.drawRect(SkRect.MakeXYWH(10f, 10f, 30f, 30f), paint)
        // Interior pixels must all be opaque white.
        for ((x, y) in listOf(Pair(15, 15), Pair(25, 25), Pair(35, 35))) {
            assertEquals(SK_ColorWHITE, bitmap.getPixel(x, y), "control: ($x,$y) should be white")
        }
    }

    @Test
    fun `Light data class equality compares by content not array reference`() {
        val a = SkEmbossMaskFilter.Light(floatArrayOf(1f, 0f, 0f), 64, 16)
        val b = SkEmbossMaskFilter.Light(floatArrayOf(1f, 0f, 0f), 64, 16)
        val c = SkEmbossMaskFilter.Light(floatArrayOf(0f, 1f, 0f), 64, 16)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == c)
    }
}
