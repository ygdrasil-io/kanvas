package org.graphiks.kanvas.gpu.renderer.pipelines

import org.graphiks.kanvas.gpu.renderer.state.GPUSourceCoverageEncoding

/**
 * Handle-free WGSL program authority for canonical premultiplied blend formulas.
 *
 * It deliberately accepts stable labels rather than semantic planner types so native execution can
 * consume a prepared formula identity without depending on the materials domain package.
 */
object GPUBlendFormulaProgramLibrary {
    fun coverageResultWgsl(
        sourceCoverageEncoding: GPUSourceCoverageEncoding,
    ): String? = when (sourceCoverageEncoding) {
        GPUSourceCoverageEncoding.None -> "return blended;"
        GPUSourceCoverageEncoding.ScalarCoverageInShader ->
            "return dst + coverage * (blended - dst);"
        GPUSourceCoverageEncoding.LCDCoverageInShader -> """
            let rgb = dst.rgb + coverage * (blended.rgb - dst.rgb);
            let alphaCandidates = vec3f(dst.a) + coverage * vec3f(blended.a - dst.a);
            return vec4f(rgb, max(max(alphaCandidates.r, alphaCandidates.g), alphaCandidates.b));
        """.trimIndent()
        else -> null
    }

    fun selectedFullCoverageFunctionWgsl(
        modeLabel: String,
        formulaId: String,
        functionName: String = "kanvasBlendPremul",
    ): String? {
        val expectedFormulaId = if (modeLabel == "plus") "plus_exact@v1" else "$modeLabel@v1"
        if (formulaId != expectedFormulaId) return null
        return selectedBlendFunctionWgsl(modeLabel, functionName)
    }

    fun selectedBlendFunctionWgsl(
        modeLabel: String,
        functionName: String = "kanvasBlendPremul",
    ): String? = org.graphiks.kanvas.gpu.plan.BlendFormulaProgramV1.selectedBlendFunctionWgsl(modeLabel, functionName)

    val advancedHelpersWgsl: String
        get() = org.graphiks.kanvas.gpu.plan.BlendFormulaProgramV1.advancedHelpersWgsl
}
