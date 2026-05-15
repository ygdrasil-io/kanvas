package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.math.SkISize

/**
 * R-final.S — **STUB.SKSL** consumer GM. Iso-aligned port of
 * upstream's `gm/rippleshader.cpp` (which compiles a custom SkSL
 * runtime-effect shader that animates a wave-front pattern across
 * the canvas).
 *
 * `:kanvas-skia` does not parse arbitrary SkSL (see
 * [SkRuntimeEffect] kdoc + `SkSlStubMarker` for the design
 * rationale) — it dispatches through a hand-port-per-shader-hash
 * table. The custom RippleShader SkSL is therefore not registered ;
 * [SkRuntimeEffect.MakeForShader] returns
 * `Result(effect = null, errorText = "SkSL not registered: <hash>...")`
 * and the GM cannot draw anything meaningful.
 *
 * The body calls [SkRuntimeEffect.MakeForShader] so the compile
 * contract holds. [RippleShaderTest] is `@Disabled` because the
 * dispatch returns a null effect.
 *
 * See [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * § STUB.SKSL.
 */
public class RippleShaderGM : GM() {

    override fun getName(): String = "ripple-shader"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    /**
     * Minimal placeholder SkSL — never registered in
     * `SkRuntimeEffectDispatch`, so [SkRuntimeEffect.MakeForShader]
     * returns a `Result` with a null [SkRuntimeEffect.Result.effect]
     * and a populated `errorText`.
     */
    private val rippleSksl: String = """
        // RippleShaderGM stub source — the real upstream shader lives
        // in gm/rippleshader.cpp ; we don't ship its hand-port.
        half4 main(float2 fragCoord) {
            return half4(0.0, 0.0, 0.0, 1.0);
        }
    """.trimIndent()

    override fun onDraw(canvas: SkCanvas?) {
        // Touch the stubbed dispatch — returns Result(null, "SkSL not registered: ...").
        val result = SkRuntimeEffect.MakeForShader(rippleSksl)
        @Suppress("UNUSED_VARIABLE") val _kept = result
    }
}
