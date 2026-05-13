package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * R-suivi.7 — coverage for [SkPathEffect.filterPath]'s new `cullRect`
 * overload. [SkDashPathEffect] is the first concrete subclass to honour
 * it ; this test asserts :
 *
 *  - `cullRect = null` (the default) is bit-identical to the 2-arg call.
 *  - A `cullRect` that excludes part of the path drops the corresponding
 *    `moveTo + lineTo` pairs, but keeps every dash that overlaps it
 *    (including straddling dashes).
 *  - A `cullRect` that excludes the entire path produces an empty result.
 *  - Other path effects (e.g. compose / sum) forward the cullRect to
 *    their children — verified via a small spy.
 */
class SkPathEffectCullRectTest {

    private val identity = SkMatrix.Identity

    @Test
    fun `null cullRect is identical to the 2-arg overload`() {
        val pe = SkDashPathEffect.Make(floatArrayOf(10f, 10f), 0f)
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(100f, 0f).detach()
        val a = pe.filterPath(input, identity)
        val b = pe.filterPath(input, identity, cullRect = null)
        assertNotNull(a); assertNotNull(b)
        assertEquals(a!!.verbs.size, b!!.verbs.size)
        assertEquals(a.coords.size, b.coords.size)
        for (i in a.coords.indices) {
            assertEquals(a.coords[i], b.coords[i], 0f)
        }
    }

    @Test
    fun `cullRect drops dashes whose segment AABB is outside the rect`() {
        // 100-unit line dashed [10 on, 10 off] → dashes at
        // [0..10], [20..30], [40..50], [60..70], [80..90].
        val pe = SkDashPathEffect.Make(floatArrayOf(10f, 10f), 0f)
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(100f, 0f).detach()

        // Cull to x ∈ [25, 75], y ∈ [-1, 1] → keeps dashes at
        // [20..30], [40..50], [60..70] ; drops [0..10] and [80..90].
        val cull = SkRect(25f, -1f, 75f, 1f)
        val out = pe.filterPath(input, identity, cull)
        assertNotNull(out)
        val moves = out!!.verbs.count { it == SkPath.StorageVerb.kMove }
        val lines = out.verbs.count { it == SkPath.StorageVerb.kLine }
        assertEquals(3, moves, "kept 3 of 5 dashes")
        assertEquals(3, lines, "kept 3 of 5 dashes")
    }

    @Test
    fun `cullRect that excludes everything yields an empty path`() {
        val pe = SkDashPathEffect.Make(floatArrayOf(10f, 10f), 0f)
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(100f, 0f).detach()
        // Rect entirely off the path's Y range.
        val cull = SkRect(0f, 1000f, 100f, 2000f)
        val out = pe.filterPath(input, identity, cull)
        assertNotNull(out)
        assertTrue(out!!.isEmpty(), "expected empty path, got ${out.verbs.size} verbs")
    }

    @Test
    fun `cullRect that contains everything keeps every dash`() {
        val pe = SkDashPathEffect.Make(floatArrayOf(10f, 10f), 0f)
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(100f, 0f).detach()
        // Rect comfortably containing the path.
        val cull = SkRect(-10f, -10f, 110f, 10f)
        val out = pe.filterPath(input, identity, cull)
        val baseline = pe.filterPath(input, identity)
        assertNotNull(out); assertNotNull(baseline)
        assertEquals(baseline!!.verbs.size, out!!.verbs.size)
    }

    @Test
    fun `compose forwards cullRect to its children`() {
        var receivedOuter: SkRect? = null
        var receivedInner: SkRect? = null
        val inner = object : SkPathEffect() {
            override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? = input
            override fun filterPath(input: SkPath, ctm: SkMatrix, cullRect: SkRect?): SkPath? {
                receivedInner = cullRect
                return input
            }
        }
        val outer = object : SkPathEffect() {
            override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? = input
            override fun filterPath(input: SkPath, ctm: SkMatrix, cullRect: SkRect?): SkPath? {
                receivedOuter = cullRect
                return input
            }
        }
        val compose = SkPathEffect.MakeCompose(outer, inner)!!
        val cull = SkRect(0f, 0f, 10f, 10f)
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(5f, 5f).detach()
        compose.filterPath(input, identity, cull)
        assertNotNull(receivedInner, "inner saw the cullRect")
        assertNotNull(receivedOuter, "outer saw the cullRect")
        assertEquals(0f, receivedInner!!.left())
        assertEquals(10f, receivedOuter!!.right())
    }

    @Test
    fun `sum forwards cullRect to both branches`() {
        var receivedFirst: SkRect? = null
        var receivedSecond: SkRect? = null
        val first = object : SkPathEffect() {
            override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? = input
            override fun filterPath(input: SkPath, ctm: SkMatrix, cullRect: SkRect?): SkPath? {
                receivedFirst = cullRect
                return input
            }
        }
        val second = object : SkPathEffect() {
            override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? = input
            override fun filterPath(input: SkPath, ctm: SkMatrix, cullRect: SkRect?): SkPath? {
                receivedSecond = cullRect
                return input
            }
        }
        val sum = SkPathEffect.MakeSum(first, second)!!
        val cull = SkRect(0f, 0f, 10f, 10f)
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(5f, 5f).detach()
        sum.filterPath(input, identity, cull)
        assertNotNull(receivedFirst)
        assertNotNull(receivedSecond)
    }
}
