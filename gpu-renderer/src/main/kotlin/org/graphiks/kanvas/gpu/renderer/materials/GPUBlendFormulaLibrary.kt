package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUBlendFormulaProgramLibrary

/** Shader topology used to consume coverage after the complete premultiplied blend. */
enum class GPUBlendCoverageKind {
    Full,
    Scalar,
    LCD,
}

/** Stable group/binding slot owned by a blend formula topology. */
data class GPUBlendFormulaBinding(
    val group: Int,
    val binding: Int,
    val role: String,
)

/** Resource pairs required to execute one formula after draw-uniform binding. */
enum class GPUBlendBindingTopology(val resourceBindings: List<GPUBlendFormulaBinding>) {
    SourceDestination(
        listOf(
            GPUBlendFormulaBinding(1, 1, "source-texture"),
            GPUBlendFormulaBinding(1, 2, "source-sampler"),
            GPUBlendFormulaBinding(1, 3, "destination-texture"),
            GPUBlendFormulaBinding(1, 4, "destination-sampler"),
        ),
    ),
    SourceDestinationCoverage(
        SourceDestination.resourceBindings + listOf(
            GPUBlendFormulaBinding(1, 5, "coverage-texture"),
            GPUBlendFormulaBinding(1, 6, "coverage-sampler"),
        ),
    ),
}

/** Canonical, immutable description of one premultiplied blend shader formula. */
data class GPUBlendFormula(
    val formulaId: String,
    val mode: GPUBlendMode,
    val coverageKind: GPUBlendCoverageKind,
    val bindingTopology: GPUBlendBindingTopology,
    val wgsl: String,
)

/**
 * Semantic formula registry for material, destination-read, coverage, and LCD blend routes.
 *
 * The raw selected WGSL functions live once in [GPUBlendFormulaProgramLibrary], which is also the
 * handle-free program authority consumed by native execution. CPU references deliberately live
 * outside both objects so they cannot reproduce a shader bug by evaluating the same implementation.
 */
object GPUBlendFormulaLibrary {
    fun formulaFor(mode: GPUBlendMode, coverageKind: GPUBlendCoverageKind): GPUBlendFormula? {
        if (mode == GPUBlendMode.DST) return null
        val formulaId = when (coverageKind) {
            GPUBlendCoverageKind.LCD -> "lcd.${mode.gpuLabel}@v1"
            GPUBlendCoverageKind.Full,
            GPUBlendCoverageKind.Scalar,
            -> if (mode == GPUBlendMode.PLUS) "plus_exact@v1" else "${mode.gpuLabel}@v1"
        }
        return GPUBlendFormula(
            formulaId = formulaId,
            mode = mode,
            coverageKind = coverageKind,
            bindingTopology = when (coverageKind) {
                GPUBlendCoverageKind.Full -> GPUBlendBindingTopology.SourceDestination
                GPUBlendCoverageKind.Scalar,
                GPUBlendCoverageKind.LCD,
                -> GPUBlendBindingTopology.SourceDestinationCoverage
            },
            wgsl = selectedBlendFunctionWgsl(mode),
        )
    }

    /** Resolves only canonical shader plans. Fixed-function and no-op plans do not invent formulas. */
    fun resolve(plan: GPUBlendPlan): GPUBlendFormula? {
        val shader = when (plan) {
            is GPUBlendPlan.LayerCompositeBlend -> return resolve(plan.child)
            is GPUBlendPlan.ShaderBlendNoDstRead -> plan
            is GPUBlendPlan.ShaderBlendWithDstRead -> plan
            else -> return null
        }
        val coverageKind = when (shader.sourceCoverageEncoding) {
            GPUSourceCoverageEncoding.ScalarCoverageInShader -> GPUBlendCoverageKind.Scalar
            GPUSourceCoverageEncoding.LCDCoverageInShader -> GPUBlendCoverageKind.LCD
            else -> GPUBlendCoverageKind.Full
        }
        val formulaId = when (shader) {
            is GPUBlendPlan.ShaderBlendNoDstRead -> shader.formulaId
            is GPUBlendPlan.ShaderBlendWithDstRead -> shader.formulaId
            else -> return null
        }
        val formula = formulaFor(shader.mode, coverageKind) ?: return null
        return formula.takeIf { it.formulaId == formulaId }
    }

    /** Returns a selected two-input formula named [functionName]. */
    fun selectedBlendFunctionWgsl(
        mode: GPUBlendMode,
        functionName: String = "kanvasBlendPremul",
    ): String = requireNotNull(
        GPUBlendFormulaProgramLibrary.selectedBlendFunctionWgsl(mode.gpuLabel, functionName),
    )

    /** Dispatcher compatible with the stable 0..14 destination-read mode uniform ABI. */
    fun advancedBlendDispatcherWgsl(functionName: String = "blendPremul"): String = """
        ${GPUBlendFormulaProgramLibrary.advancedHelpersWgsl}

        fn $functionName(src: vec4f, dst: vec4f, blendMode: u32) -> vec4f {
            return kanvasBlendAdvancedPremul(src, dst, blendMode);
        }
    """.trimIndent()

    /** Dispatcher compatible with the stable 0..28 all-mode coverage uniform ABI. */
    fun allModeBlendDispatcherWgsl(functionName: String = "porterDuffPremul"): String = """
        ${GPUBlendFormulaProgramLibrary.advancedHelpersWgsl}

        fn $functionName(src: vec4f, dst: vec4f, blendMode: u32) -> vec4f {
            switch blendMode {
                case 0u: { return vec4f(0.0); }
                case 1u: { return src + dst * (1.0 - src.a); }
                case 2u: { return src; }
                case 3u: { return dst; }
                case 4u: { return dst + src * (1.0 - dst.a); }
                case 5u: { return src * dst.a; }
                case 6u: { return dst * src.a; }
                case 7u: { return src * (1.0 - dst.a); }
                case 8u: { return dst * (1.0 - src.a); }
                case 9u: { return src * dst.a + dst * (1.0 - src.a); }
                case 10u: { return dst * src.a + src * (1.0 - dst.a); }
                case 11u: { return src * (1.0 - dst.a) + dst * (1.0 - src.a); }
                case 12u: { return min(vec4f(1.0), src + dst); }
                case 13u: { return src * dst; }
                case 14u: { return kanvasBlendAdvancedPremul(src, dst, 0u); }
                case 15u: { return kanvasBlendAdvancedPremul(src, dst, 1u); }
                case 16u: { return kanvasBlendAdvancedPremul(src, dst, 2u); }
                case 17u: { return kanvasBlendAdvancedPremul(src, dst, 3u); }
                case 18u: { return kanvasBlendAdvancedPremul(src, dst, 4u); }
                case 19u: { return kanvasBlendAdvancedPremul(src, dst, 7u); }
                case 20u: { return kanvasBlendAdvancedPremul(src, dst, 8u); }
                case 21u: { return kanvasBlendAdvancedPremul(src, dst, 9u); }
                case 22u: { return kanvasBlendAdvancedPremul(src, dst, 10u); }
                case 23u: { return kanvasBlendAdvancedPremul(src, dst, 5u); }
                case 24u: { return kanvasBlendAdvancedPremul(src, dst, 6u); }
                case 25u: { return kanvasBlendAdvancedPremul(src, dst, 11u); }
                case 26u: { return kanvasBlendAdvancedPremul(src, dst, 12u); }
                case 27u: { return kanvasBlendAdvancedPremul(src, dst, 13u); }
                case 28u: { return kanvasBlendAdvancedPremul(src, dst, 14u); }
                default: { return src; }
            }
        }
    """.trimIndent()

    /** Applies the canonical coverage topology to variables named blended, dst, and coverage. */
    fun coverageResultWgsl(coverageKind: GPUBlendCoverageKind): String = when (coverageKind) {
        GPUBlendCoverageKind.Full -> "return blended;"
        GPUBlendCoverageKind.Scalar -> "return dst + coverage * (blended - dst);"
        GPUBlendCoverageKind.LCD -> """
            let rgb = dst.rgb + coverage * (blended.rgb - dst.rgb);
            let alphaCandidates = vec3f(dst.a) + coverage * vec3f(blended.a - dst.a);
            return vec4f(rgb, max(max(alphaCandidates.r, alphaCandidates.g), alphaCandidates.b));
        """.trimIndent()
    }

    /** Complete module used by parser/reflection/native validation without duplicating formula bodies. */
    fun assembleValidationModule(formula: GPUBlendFormula): String {
        val coverageBindings = when (formula.bindingTopology) {
            GPUBlendBindingTopology.SourceDestination -> ""
            GPUBlendBindingTopology.SourceDestinationCoverage -> """
                @group(1) @binding(5) var coverageTexture: texture_2d<f32>;
                @group(1) @binding(6) var coverageSampler: sampler;
            """.trimIndent()
        }
        val coverageRead = when (formula.coverageKind) {
            GPUBlendCoverageKind.Full -> ""
            GPUBlendCoverageKind.Scalar ->
                "let coverage = textureSample(coverageTexture, coverageSampler, uv).a;"
            GPUBlendCoverageKind.LCD ->
                "let coverage = textureSample(coverageTexture, coverageSampler, uv).rgb;"
        }
        val result = coverageResultWgsl(formula.coverageKind)
        return """
            struct BlendValidationUniforms {
                _pad: vec4u,
            };

            @group(0) @binding(0) var<uniform> blendValidationUniforms: BlendValidationUniforms;
            @group(1) @binding(1) var srcTexture: texture_2d<f32>;
            @group(1) @binding(2) var srcSampler: sampler;
            @group(1) @binding(3) var dstTexture: texture_2d<f32>;
            @group(1) @binding(4) var dstSampler: sampler;
            $coverageBindings

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                let uniformGuard = f32(blendValidationUniforms._pad.x) * 0.0;
                return vec4f(x, y, uniformGuard, 1.0);
            }

            ${formula.wgsl}

            @fragment
            fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
                let dims = textureDimensions(srcTexture);
                let uv = coord.xy / vec2f(dims);
                let src = textureSample(srcTexture, srcSampler, uv);
                let dst = textureSample(dstTexture, dstSampler, uv);
                let blended = kanvasBlendPremul(src, dst);
                $coverageRead
                $result
            }
        """.trimIndent()
    }

}
