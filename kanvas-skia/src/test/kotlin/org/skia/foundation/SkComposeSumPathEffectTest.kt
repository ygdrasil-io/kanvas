package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkMatrix
import org.skia.tools.SkDiscretePathEffect

/**
 * Unit tests for [SkPathEffect.MakeCompose] / [SkPathEffect.MakeSum]
 * (Phase 7p3).
 *
 * Coverage :
 *  - Both `null` operands ⇒ returns `null`.
 *  - One `null` ⇒ returns the other (passthrough).
 *  - Compose evaluates inner first then outer.
 *  - Sum concatenates the two outputs.
 *  - When an inner effect returns `null` (passthrough), the chain
 *    falls back to the original input for that branch (Skia-iso
 *    semantic).
 */
class SkComposeSumPathEffectTest {

    private val identity = SkMatrix.Identity

    private val rectangle: SkPath =
        SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(100f, 0f)
            .lineTo(100f, 50f)
            .lineTo(0f, 50f)
            .close()
            .detach()

    @Test
    fun `MakeCompose with both nulls returns null`() {
        assertNull(SkPathEffect.MakeCompose(null, null))
    }

    @Test
    fun `MakeCompose with null outer returns inner unchanged`() {
        val inner = SkCornerPathEffect.Make(5f)!!
        assertSame(inner, SkPathEffect.MakeCompose(outer = null, inner = inner))
    }

    @Test
    fun `MakeCompose with null inner returns outer unchanged`() {
        val outer = SkCornerPathEffect.Make(5f)!!
        assertSame(outer, SkPathEffect.MakeCompose(outer = outer, inner = null))
    }

    @Test
    fun `MakeCompose evaluates inner first then outer`() {
        // Inner = Dash[10, 10] decomposes the rectangle perimeter
        // (300 units) into 15 dashes. Outer = Corner(2) smooths each
        // dash's start corner where it meets the path.
        val dash = SkDashPathEffect.Make(floatArrayOf(10f, 10f), 0f)
        val corner = SkCornerPathEffect.Make(2f)!!
        val composed = SkPathEffect.MakeCompose(corner, dash)!!

        val composedOut = composed.filterPath(rectangle, identity)!!
        // Compare to manual chain : dash first, corner second.
        val intermediate = dash.filterPath(rectangle, identity)!!
        val expected = corner.filterPath(intermediate, identity)!!
        // Verb counts should match — composition is order-sensitive
        // and `corner ∘ dash` should produce the same verb sequence
        // as the manual chain.
        assertEquals(expected.verbs.toList(), composedOut.verbs.toList())
    }

    @Test
    fun `MakeCompose with passthrough inner falls back to input`() {
        // Inner = Dash[0, 0] returns an empty path (passthrough-like
        // for the chain : its result is empty so outer should still
        // receive the original input).
        // Actually our SkDashPathEffect returns an EMPTY path for
        // [0, 0] intervals, not null. So the test below uses a custom
        // passthrough effect that returns null.
        val passthrough = object : SkPathEffect() {
            override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? = null
        }
        val outer = SkCornerPathEffect.Make(5f)!!
        val composed = SkPathEffect.MakeCompose(outer, passthrough)!!

        val out = composed.filterPath(rectangle, identity)!!
        // Compose should have applied outer to the original rectangle
        // (not to a degenerate empty path).
        val expected = outer.filterPath(rectangle, identity)!!
        assertEquals(expected.verbs.toList(), out.verbs.toList())
    }

    @Test
    fun `MakeSum with both nulls returns null`() {
        assertNull(SkPathEffect.MakeSum(null, null))
    }

    @Test
    fun `MakeSum with null first returns second`() {
        val s = SkCornerPathEffect.Make(5f)!!
        assertSame(s, SkPathEffect.MakeSum(null, s))
    }

    @Test
    fun `MakeSum with null second returns first`() {
        val f = SkCornerPathEffect.Make(5f)!!
        assertSame(f, SkPathEffect.MakeSum(f, null))
    }

    @Test
    fun `MakeSum concatenates the two outputs`() {
        val dash1 = SkDashPathEffect.Make(floatArrayOf(20f, 20f), 0f)
        val dash2 = SkDashPathEffect.Make(floatArrayOf(20f, 20f), 20f)
        val sum = SkPathEffect.MakeSum(dash1, dash2)!!

        val sumOut = sum.filterPath(rectangle, identity)!!
        val a = dash1.filterPath(rectangle, identity)!!
        val b = dash2.filterPath(rectangle, identity)!!
        // Verb count should equal the sum of the two branches.
        assertEquals(a.verbs.size + b.verbs.size, sumOut.verbs.size) {
            "sum verb count should equal a (${a.verbs.size}) + b (${b.verbs.size}), " +
                "got ${sumOut.verbs.size}"
        }
        // Coordinate count too.
        assertEquals(a.coords.size + b.coords.size, sumOut.coords.size)
    }

    @Test
    fun `MakeSum with passthrough first uses input for that branch`() {
        val passthrough = object : SkPathEffect() {
            override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? = null
        }
        val corner = SkCornerPathEffect.Make(5f)!!
        val sum = SkPathEffect.MakeSum(passthrough, corner)!!
        val out = sum.filterPath(rectangle, identity)!!
        // Sum should contain rectangle's verbs + corner-smoothed
        // rectangle's verbs.
        val cornered = corner.filterPath(rectangle, identity)!!
        assertEquals(rectangle.verbs.size + cornered.verbs.size, out.verbs.size)
    }

    @Test
    fun `MakeSum with both passthrough returns input + input`() {
        val pass1 = object : SkPathEffect() {
            override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? = null
        }
        val pass2 = object : SkPathEffect() {
            override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? = null
        }
        val sum = SkPathEffect.MakeSum(pass1, pass2)!!
        val out = sum.filterPath(rectangle, identity)!!
        // Both branches fall back to input ⇒ sum = input + input.
        assertEquals(rectangle.verbs.size * 2, out.verbs.size)
    }

    @Test
    fun `Compose chain with three effects via nested MakeCompose`() {
        // Compose(corner, Compose(dash, discrete)) — should evaluate
        // discrete first, then dash, then corner.
        val discrete = SkDiscretePathEffect.Make(15f, 1f, seed = 1)!!
        val dash = SkDashPathEffect.Make(floatArrayOf(15f, 5f), 0f)
        val corner = SkCornerPathEffect.Make(2f)!!
        val nested = SkPathEffect.MakeCompose(
            corner,
            SkPathEffect.MakeCompose(dash, discrete),
        )
        assertNotNull(nested)
        val out = nested!!.filterPath(rectangle, identity)
        assertNotNull(out)
        // Doesn't crash + produces a non-empty result.
        assertTrue(!out!!.isEmpty())
    }
}
