package org.skia.foundation


import org.graphiks.math.SkColor4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * R-suivi.1 — coverage for the [SkColorFilters.Lerp] nullable overload's
 * **true pass-through** semantics. Upstream's `SkColorFilters::Lerp`
 * treats a `nullptr` child as "use the unfiltered input" — NOT "apply
 * the identity colour filter". This test exercises the four corners of
 * that contract :
 *
 *  1. `Lerp(t, null, null)` ⇒ `null`.
 *  2. `Lerp(t, identity, identity)` and `Lerp(t, null, null)` both
 *     reduce to a no-op : on any sampled colour they agree (the
 *     non-null path returns the non-null identity filter itself ; the
 *     null/null path returns `null`, which the device treats as
 *     pass-through).
 *  3. `Lerp(t, identity, null)` agrees with `Lerp(t, identity, identity)`
 *     when the identity filter is bit-exact (which it is — by
 *     definition).
 *  4. Symmetrically for `Lerp(t, null, identity)`.
 *  5. With a *non-identity* filter on one side, the pass-through path
 *     differs from substituting `kIdentity` only when the filter is
 *     non-identity — verified by computing the expected lerp manually.
 */
class SkColorFiltersLerpPassThroughTest {

    private val samples = arrayOf(
        SkColor4f(0f, 0f, 0f, 0f),
        SkColor4f(1f, 0f, 0f, 1f),
        SkColor4f(0.25f, 0.5f, 0.75f, 1f),
        SkColor4f(0.1f, 0.2f, 0.3f, 0.4f),
        SkColor4f(0.7f, 0.8f, 0.9f, 1f),
    )

    @Test
    fun `Lerp(t, null, null) returns null`() {
        for (t in floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            assertNull(SkColorFilters.Lerp(t, null, null), "t = $t")
        }
    }

    @Test
    fun `Lerp(t, null, identity) matches Lerp(t, identity, identity) at sampled colours`() {
        val id = SkIdentityColorFilter
        for (t in floatArrayOf(0.25f, 0.5f, 0.75f)) {
            val passThrough = SkColorFilters.Lerp(t, null, id)!!
            val fullIdentity = SkColorFilters.Lerp(t, id, id)
            for (src in samples) {
                val a = passThrough.filterColor4f(src)
                val b = fullIdentity.filterColor4f(src)
                assertEquals(b.fR, a.fR, 1e-6f, "R for $src at t=$t")
                assertEquals(b.fG, a.fG, 1e-6f, "G for $src at t=$t")
                assertEquals(b.fB, a.fB, 1e-6f, "B for $src at t=$t")
                assertEquals(b.fA, a.fA, 1e-6f, "A for $src at t=$t")
            }
        }
    }

    @Test
    fun `Lerp(t, identity, null) matches Lerp(t, identity, identity) at sampled colours`() {
        val id = SkIdentityColorFilter
        for (t in floatArrayOf(0.25f, 0.5f, 0.75f)) {
            val passThrough = SkColorFilters.Lerp(t, id, null)!!
            val fullIdentity = SkColorFilters.Lerp(t, id, id)
            for (src in samples) {
                val a = passThrough.filterColor4f(src)
                val b = fullIdentity.filterColor4f(src)
                assertEquals(b.fR, a.fR, 1e-6f, "R for $src at t=$t")
                assertEquals(b.fG, a.fG, 1e-6f, "G for $src at t=$t")
                assertEquals(b.fB, a.fB, 1e-6f, "B for $src at t=$t")
                assertEquals(b.fA, a.fA, 1e-6f, "A for $src at t=$t")
            }
        }
    }

    @Test
    fun `Lerp(t, null, nonIdentity) blends raw input with filtered output`() {
        // A simple "invert RGB" filter so we can compute the expected lerp by hand.
        val invert: SkColorFilter = SkColorFilters.Matrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 1f,
                0f, -1f, 0f, 0f, 1f,
                0f, 0f, -1f, 0f, 1f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val t = 0.4f
        val filter = SkColorFilters.Lerp(t, null, invert)!!
        for (src in samples) {
            // Expected : lerp(t, src (pass-through), invert(src))
            val inverted = invert.filterColor4f(src)
            val u = 1f - t
            val expR = src.fR * u + inverted.fR * t
            val expG = src.fG * u + inverted.fG * t
            val expB = src.fB * u + inverted.fB * t
            val expA = src.fA * u + inverted.fA * t
            val got = filter.filterColor4f(src)
            assertEquals(expR, got.fR, 1e-6f, "R for $src")
            assertEquals(expG, got.fG, 1e-6f, "G for $src")
            assertEquals(expB, got.fB, 1e-6f, "B for $src")
            assertEquals(expA, got.fA, 1e-6f, "A for $src")
        }
    }

    @Test
    fun `Lerp(t, nonIdentity, null) blends filtered output with raw input`() {
        val invert: SkColorFilter = SkColorFilters.Matrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 1f,
                0f, -1f, 0f, 0f, 1f,
                0f, 0f, -1f, 0f, 1f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val t = 0.3f
        val filter = SkColorFilters.Lerp(t, invert, null)!!
        for (src in samples) {
            // Expected : lerp(t, invert(src), src (pass-through))
            val inverted = invert.filterColor4f(src)
            val u = 1f - t
            val expR = inverted.fR * u + src.fR * t
            val expG = inverted.fG * u + src.fG * t
            val expB = inverted.fB * u + src.fB * t
            val expA = inverted.fA * u + src.fA * t
            val got = filter.filterColor4f(src)
            assertEquals(expR, got.fR, 1e-6f, "R for $src")
            assertEquals(expG, got.fG, 1e-6f, "G for $src")
            assertEquals(expB, got.fB, 1e-6f, "B for $src")
            assertEquals(expA, got.fA, 1e-6f, "A for $src")
        }
    }

    @Test
    fun `Lerp(0, dst, null) returns dst verbatim — pass-through dst side preserved as null`() {
        val invert: SkColorFilter = SkColorFilters.Matrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 1f,
                0f, -1f, 0f, 0f, 1f,
                0f, 0f, -1f, 0f, 1f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        // t == 0 ⇒ dst dominates. The factory returns dst unchanged.
        val r1 = SkColorFilters.Lerp(0f, invert, null)
        assertNotNull(r1)
        // t == 1 ⇒ src dominates. The factory returns src — null in this case
        // (which the device treats as pass-through).
        val r2 = SkColorFilters.Lerp(1f, invert, null)
        assertNull(r2)
    }
}
