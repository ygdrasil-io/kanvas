package org.skia.dm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tests.GM

/**
 * D2.6 verification suite — pins the missing-runtime-effect
 * surfacing path :
 *
 *  1. A GM that calls `SkRuntimeEffect.MakeForShader(<unknown SkSL>)`
 *     fails gracefully (Sink returns `Result.Error` ; Runner records
 *     a failure record) — no crash, no infinite loop.
 *  2. The error message contains the canonical `"SkSL not registered:
 *     0x<16-hex>"` substring our dispatch table emits.
 *  3. [Report.missingRuntimeEffectHashes] extracts the hash from the
 *     failure record and surfaces it as a queryable set.
 *  4. The CLI flag `--list-missing-effects` propagates through
 *     [DmCli] and [DmMain.runFromArgs] without affecting the
 *     non-flag behaviour.
 */
class MissingRuntimeEffectTest {

    /**
     * GM that tries to compile an SkSL string we deliberately do not
     * register. The thrown exception bubbles up through the sink's
     * `try/catch` and lands in `Sink.Result.Error`.
     */
    private class UnregisteredEffectGM : GM() {
        // A unique SkSL string that no cluster registers — the
        // `gXBLAH_ZZ` uniform name is enough to be unique vs every
        // other registered effect. The body trivially returns
        // `(0, 0, 0, 1)` so the impl (if it existed) would draw black.
        private val sksl = """
            uniform float gXBLAH_ZZ_${this::class.simpleName};
            half4 main(float2 p) { return half4(0, 0, 0, 1); }
        """.trimIndent()

        override fun getName(): String = "unregistered_effect_gm"
        override fun getISize(): SkISize = SkISize.Make(8, 8)

        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
            val effect = SkRuntimeEffect.MakeForShader(sksl).effect
                ?: error(SkRuntimeEffect.MakeForShader(sksl).errorText)
            val paint = SkPaint().apply { shader = effect.makeShader(uniforms = null) }
            c.drawRect(SkRect.MakeWH(8f, 8f), paint)
        }
    }

    @Test
    fun `unregistered SkSL fails gracefully and surfaces the hash`() {
        val report = Runner(
            sinks = listOf<Sink>(RasterSink8888()),
            gms = listOf(UnregisteredEffectGM()),
        ).run()

        assertEquals(0, report.passed.size, "no record should pass")
        assertEquals(1, report.failed.size, "one failure record expected")
        val failure = report.failed.single()
        assertTrue(
            failure.errorMessage?.contains("SkSL not registered:") == true,
            "error message should reference the dispatch table : ${failure.errorMessage}",
        )

        val missing = report.missingRuntimeEffectHashes()
        assertEquals(1, missing.size, "exactly one missing hash should be surfaced")
        val hash = missing.single()
        assertTrue(
            Regex("^0x[0-9A-Fa-f]{16}$").matches(hash),
            "hash format should match upstream : got '$hash'",
        )
    }

    @Test
    fun `missingRuntimeEffectHashes is empty for a clean run`() {
        // No GM → nothing missing.
        val report = Runner(
            sinks = listOf<Sink>(RasterSink8888()),
            gms = emptyList(),
        ).run()
        assertEquals(emptySet<String>(), report.missingRuntimeEffectHashes())
    }

    @Test
    fun `DmCli parses --list-missing-effects boolean flag`() {
        val cliWith = DmCli.parse(arrayOf("--list-missing-effects", "--config", "8888"))
        assertTrue(cliWith.listMissingEffects, "flag should be set when present")

        val cliWithout = DmCli.parse(arrayOf("--config", "8888"))
        assertEquals(false, cliWithout.listMissingEffects, "flag should default to false")
    }
}
