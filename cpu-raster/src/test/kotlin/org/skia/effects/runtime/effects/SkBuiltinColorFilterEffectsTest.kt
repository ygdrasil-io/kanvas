package org.skia.effects.runtime.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDispatch
import org.graphiks.math.SkColor4f
import kotlin.math.abs

/**
 * D2.4.a verification suite for [SkBuiltinColorFilterEffects].
 *
 * **Behaviour under test** :
 *  - Each registered SkSL → impl mapping is wired correctly :
 *    `MakeForColorFilter(theSkSL).effect.makeColorFilter` returns
 *    a non-null filter for every built-in.
 *  - The math of each impl reproduces the SkSL formula :
 *    Identity returns input unchanged, Luma packs luma into
 *    alpha, ToneMap applies the piecewise scale, Compose chains
 *    its two children correctly.
 *  - The 3 tone-map variants (Ternary / Ifs / EarlyReturn) all
 *    produce **identical** output for the same input (confirms
 *    they map to the same impl class).
 *  - Auto-registration : after a [SkRuntimeEffectDispatch.clearForTest],
 *    a fresh `MakeForColorFilter(theSkSL)` call still resolves
 *    (the dispatch helper re-registers builtins on first lookup).
 */
class SkBuiltinColorFilterEffectsTest {

    // No `@AfterEach cleanup` — we rely on the auto-registration
    // path to repopulate the dispatch table on each MakeFor* call.

    private fun filter(sksl: String): (SkColor4f) -> SkColor4f {
        val effect = SkRuntimeEffect.MakeForColorFilter(sksl).effect
            ?: error("MakeForColorFilter returned null for : $sksl")
        val cf = effect.makeColorFilter(uniforms = null)
            ?: error("makeColorFilter returned null")
        return { c -> cf.filterColor4f(c) }
    }

    private fun assertNearly(
        expected: SkColor4f,
        actual: SkColor4f,
        tol: Float = 1e-4f,
        msg: String = "",
    ) {
        val drift = floatArrayOf(
            abs(expected.fR - actual.fR),
            abs(expected.fG - actual.fG),
            abs(expected.fB - actual.fB),
            abs(expected.fA - actual.fA),
        )
        assertTrue(drift.all { it < tol },
            "$msg : expected=$expected, actual=$actual, drift=${drift.toList()}")
    }

    // ─── Identity (gNoop) ─────────────────────────────────────────────

    @Test
    fun `identity returns input verbatim`() {
        val f = filter(SkBuiltinColorFilterEffects.NOOP_SKSL)
        val src = SkColor4f(0.7f, 0.3f, 0.1f, 0.8f)
        assertNearly(src, f(src))
    }

    @Test
    fun `identity preserves alpha unchanged flag`() {
        val effect = SkRuntimeEffect.MakeForColorFilter(
            SkBuiltinColorFilterEffects.NOOP_SKSL,
        ).effect!!
        val cf = effect.makeColorFilter(uniforms = null)!!
        assertTrue(cf.isAlphaUnchanged(),
            "Identity must declare kAlphaUnchanged_Flag")
    }

    // ─── LumaSrc (luma → alpha, RGB cleared) ─────────────────────────

    @Test
    fun `luma-to-alpha clears RGB and packs luma into alpha`() {
        val f = filter(SkBuiltinColorFilterEffects.LUMA_SRC_SKSL)
        // luma(red) = 0.3 * 1 + 0.6 * 0 + 0.1 * 0 = 0.3
        assertNearly(SkColor4f(0f, 0f, 0f, 0.3f), f(SkColor4f(1f, 0f, 0f, 1f)))
        // luma(green) = 0.3 * 0 + 0.6 * 1 + 0.1 * 0 = 0.6
        assertNearly(SkColor4f(0f, 0f, 0f, 0.6f), f(SkColor4f(0f, 1f, 0f, 1f)))
        // luma(blue) = 0.3 * 0 + 0.6 * 0 + 0.1 * 1 = 0.1
        assertNearly(SkColor4f(0f, 0f, 0f, 0.1f), f(SkColor4f(0f, 0f, 1f, 1f)))
        // luma(white) = 0.3 + 0.6 + 0.1 = 1
        assertNearly(SkColor4f(0f, 0f, 0f, 1f), f(SkColor4f(1f, 1f, 1f, 1f)))
        // luma(black) = 0
        assertNearly(SkColor4f(0f, 0f, 0f, 0f), f(SkColor4f(0f, 0f, 0f, 1f)))
    }

    // ─── Tone-map (gTernary / gIfs / gEarlyReturn) ───────────────────

    @Test
    fun `tone-map low-luma branch scales by 0_5`() {
        // Luma < 0.33333 → scale = 0.5.
        val low = SkColor4f(0.1f, 0.1f, 0.1f, 1f)  // luma ≈ 0.1
        val expected = SkColor4f(0.05f, 0.05f, 0.05f, 1f)  // × 0.5
        for (sksl in toneSksls()) {
            val f = filter(sksl)
            assertNearly(expected, f(low),
                msg = "low-luma branch failed for ${sksl.take(20)}...")
        }
    }

    @Test
    fun `tone-map mid-luma branch uses (0_166666 + 2 × dy) over luma`() {
        // 0.33333 ≤ luma < 0.66666.
        // Pick rgb = 0.5 → luma = 0.5 (in mid range).
        // scale = (0.166666 + 2 * (0.5 - 0.33333)) / 0.5
        //       = (0.166666 + 0.33334) / 0.5
        //       = 0.500006 / 0.5 = 1.000012
        // out = 0.5 * 1.000012 ≈ 0.500006
        val mid = SkColor4f(0.5f, 0.5f, 0.5f, 1f)
        for (sksl in toneSksls()) {
            val f = filter(sksl)
            val out = f(mid)
            assertNearly(SkColor4f(0.500006f, 0.500006f, 0.500006f, 1f), out, tol = 1e-3f)
        }
    }

    @Test
    fun `tone-map high-luma branch uses (0_833333 + 0_5 × dy) over luma`() {
        // luma ≥ 0.66666.
        // Pick rgb = 0.9 → luma = 0.9.
        // scale = (0.833333 + 0.5 * (0.9 - 0.66666)) / 0.9
        //       = (0.833333 + 0.11667) / 0.9
        //       = 0.95 / 0.9 ≈ 1.05556
        // out = 0.9 * 1.05556 ≈ 0.95
        val high = SkColor4f(0.9f, 0.9f, 0.9f, 1f)
        for (sksl in toneSksls()) {
            val f = filter(sksl)
            val out = f(high)
            assertNearly(SkColor4f(0.95f, 0.95f, 0.95f, 1f), out, tol = 1e-3f)
        }
    }

    @Test
    fun `tone-map preserves alpha across all luma branches`() {
        val cases = listOf(
            SkColor4f(0.1f, 0.1f, 0.1f, 0.5f),
            SkColor4f(0.5f, 0.5f, 0.5f, 0.5f),
            SkColor4f(0.9f, 0.9f, 0.9f, 0.5f),
        )
        for (sksl in toneSksls()) {
            val f = filter(sksl)
            for (src in cases) {
                assertEquals(src.fA, f(src).fA, 1e-5f,
                    "alpha must pass through unchanged")
            }
        }
    }

    @Test
    fun `tone-map variants produce identical outputs`() {
        // The 3 SkSL syntaxes should map to the same Kotlin impl,
        // therefore yield bit-identical outputs for any input.
        val cases = listOf(
            SkColor4f(0f, 0f, 0f, 1f),
            SkColor4f(0.1f, 0.2f, 0.3f, 0.5f),
            SkColor4f(0.5f, 0.5f, 0.5f, 1f),
            SkColor4f(0.9f, 0.8f, 0.7f, 0.6f),
        )
        val fT = filter(SkBuiltinColorFilterEffects.TERNARY_SKSL)
        val fI = filter(SkBuiltinColorFilterEffects.IFS_SKSL)
        val fE = filter(SkBuiltinColorFilterEffects.EARLY_RETURN_SKSL)
        for (src in cases) {
            val a = fT(src); val b = fI(src); val c = fE(src)
            assertEquals(a.fR, b.fR, 1e-6f, "ternary↔ifs R mismatch on $src")
            assertEquals(a.fG, b.fG, 1e-6f, "ternary↔ifs G mismatch")
            assertEquals(a.fB, b.fB, 1e-6f, "ternary↔ifs B mismatch")
            assertEquals(a.fR, c.fR, 1e-6f, "ternary↔early-return R mismatch")
            assertEquals(a.fG, c.fG, 1e-6f, "ternary↔early-return G mismatch")
            assertEquals(a.fB, c.fB, 1e-6f, "ternary↔early-return B mismatch")
        }
    }

    // ─── Compose-CF (2 colorFilter children) ──────────────────────────

    @Test
    fun `compose-cf chains inner then outer`() {
        // inner = identity, outer = invert-RGB.
        // Result : outer(inner(red)) = invert(red) = (0, 1, 1, 1).
        val effect = SkRuntimeEffect.MakeForColorFilter(
            SkBuiltinColorFilterEffects.COMPOSE_CF_SKSL,
        ).effect!!
        val identityCF = SkRuntimeEffect.MakeForColorFilter(
            SkBuiltinColorFilterEffects.NOOP_SKSL,
        ).effect!!.makeColorFilter(null)!!
        val invertCF = object : org.skia.foundation.SkColorFilter() {
            override fun filterColor4f(src: SkColor4f): SkColor4f =
                SkColor4f(1f - src.fR, 1f - src.fG, 1f - src.fB, src.fA)
        }
        val composed = effect.makeColorFilter(
            uniforms = null,
            children = arrayOf<org.skia.foundation.SkColorFilter?>(identityCF, invertCF),
        )!!
        val src = SkColor4f(1f, 0f, 0f, 1f)
        assertNearly(SkColor4f(0f, 1f, 1f, 1f), composed.filterColor4f(src))
    }

    @Test
    fun `compose-cf with both inner and outer as identity returns input`() {
        val effect = SkRuntimeEffect.MakeForColorFilter(
            SkBuiltinColorFilterEffects.COMPOSE_CF_SKSL,
        ).effect!!
        val identityCF = SkRuntimeEffect.MakeForColorFilter(
            SkBuiltinColorFilterEffects.NOOP_SKSL,
        ).effect!!.makeColorFilter(null)!!
        val composed = effect.makeColorFilter(
            uniforms = null,
            children = arrayOf<org.skia.foundation.SkColorFilter?>(identityCF, identityCF),
        )!!
        val src = SkColor4f(0.7f, 0.3f, 0.1f, 0.8f)
        assertNearly(src, composed.filterColor4f(src))
    }

    // ─── Auto-registration after clearForTest ─────────────────────────

    @Test
    fun `clearForTest does not break subsequent MakeForColorFilter`() {
        // Manually wipe the registry, then verify the next MakeFor*
        // call repopulates and resolves the impl.
        SkRuntimeEffectDispatch.clearForTest()
        val r = SkRuntimeEffect.MakeForColorFilter(SkBuiltinColorFilterEffects.NOOP_SKSL)
        assertNotNull(r.effect, "auto-registration must repopulate after clearForTest")
    }

    private fun toneSksls(): List<String> = listOf(
        SkBuiltinColorFilterEffects.TERNARY_SKSL,
        SkBuiltinColorFilterEffects.IFS_SKSL,
        SkBuiltinColorFilterEffects.EARLY_RETURN_SKSL,
    )
}
