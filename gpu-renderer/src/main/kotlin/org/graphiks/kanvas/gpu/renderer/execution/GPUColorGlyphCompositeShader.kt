package org.graphiks.kanvas.gpu.renderer.execution

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslReflection
import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslValidation
import org.graphiks.kanvas.gpu.renderer.color.validateColorWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.COLOR_GLYPH_COMPOSITE_MAX_LAYERS
import org.graphiks.kanvas.gpu.renderer.wgsl.colorGlyphCompositeWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.colorGlyphDestinationReadWgsl
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUBlendFormulaProgramLibrary
import org.graphiks.kanvas.gpu.renderer.wgsl.reflectionFactsHash

/** Parser-backed COLRv0 shader source ready for native pipeline materialization. */
internal data class GPUColorGlyphCompositePlan(
    val wgslSource: String,
    val wgslReflection: GPUColorWgslReflection?,
    val destinationProgramSeal: GPUColorGlyphDestinationProgramSeal? = null,
)

/** Pure structural authority consumed unchanged by preflight, cache, and materialization. */
internal data class GPUColorGlyphDestinationProgramSeal(
    val formulaId: String,
    val modeLabel: String,
    val sourceCoverageEncoding: GPUSourceCoverageEncoding,
    val clipVariant: String,
    val targetFormat: GPUColorFormat,
    val wgslSha256: String,
    val reflectionModuleHash: String,
    val reflectionFactsHash: String,
    val entryPoints: List<String>,
    val bindingSlots: List<Pair<Int, Int>>,
    val bindingLayoutKey: String,
    val pipelineKey: String,
)

internal enum class GPUColorGlyphDestinationClipVariant(
    val stableLabel: String,
) {
    AnalyticRect("analytic-rect"),
    CoverageMask("coverage-mask"),
}

/** Outcome of preparing the native COLRv0 shader source. */
internal sealed interface GPUColorGlyphCompositeShaderResult {
    data class Ready(val plan: GPUColorGlyphCompositePlan) : GPUColorGlyphCompositeShaderResult

    data class Rejected(val reason: String, val message: String) : GPUColorGlyphCompositeShaderResult
}

/** Generates and parser-validates the fixed native COLRv0 composite shader. */
internal fun buildColorGlyphCompositeShader(
    maxLayers: Int = COLOR_GLYPH_COMPOSITE_MAX_LAYERS,
): GPUColorGlyphCompositeShaderResult {
    val wgsl = colorGlyphCompositeWgsl(maxLayers)
    return when (val validation = validateColorWgsl(sourceId = "text.colrv0.composite", wgslSource = wgsl)) {
        is GPUColorWgslValidation.Validated ->
            GPUColorGlyphCompositeShaderResult.Ready(
                GPUColorGlyphCompositePlan(wgslSource = wgsl, wgslReflection = validation.reflection),
            )
        is GPUColorWgslValidation.Rejected ->
            GPUColorGlyphCompositeShaderResult.Rejected(reason = validation.reason, message = validation.message)
    }
}

/** Generates and parser-validates the closed scalar COLOR_DODGE destination-read variant. */
internal fun buildColorGlyphDestinationReadShader(
    maxLayers: Int = COLOR_GLYPH_COMPOSITE_MAX_LAYERS,
    clipVariant: GPUColorGlyphDestinationClipVariant =
        GPUColorGlyphDestinationClipVariant.AnalyticRect,
): GPUColorGlyphCompositeShaderResult {
    val blend = requireNotNull(
        GPUBlendFormulaProgramLibrary.selectedFullCoverageFunctionWgsl(
            modeLabel = "color_dodge",
            formulaId = "color_dodge@v1",
        ),
    )
    val coverageResult = requireNotNull(
        GPUBlendFormulaProgramLibrary.coverageResultWgsl(
            GPUSourceCoverageEncoding.ScalarCoverageInShader,
        ),
    )
    val wgsl = colorGlyphDestinationReadWgsl(
        blend,
        coverageResult,
        maxLayers,
        coverageMaskClip = clipVariant == GPUColorGlyphDestinationClipVariant.CoverageMask,
    )
    return when (
        val validation = validateColorWgsl(
            sourceId = "text.colrv0.destination-read.color-dodge",
            wgslSource = wgsl,
        )
    ) {
        is GPUColorWgslValidation.Validated -> {
            val reflection = validation.reflection
                ?: return GPUColorGlyphCompositeShaderResult.Rejected(
                    reason = "missing_wgsl_reflection",
                    message = "Destination-read ColorGlyph WGSL requires parser reflection.",
                )
            val entryPoints = reflection.report.entryPoints
                .map { entry -> "${entry.stage}:${entry.name}" }
                .sorted()
            val bindingSlots = reflection.report.bindings
                .map { binding -> binding.group to binding.binding }
                .sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
            if (!reflection.validated ||
                !reflection.report.validation.success ||
                entryPoints != listOf("fragment:fs_main", "vertex:vs_main") ||
                bindingSlots != (0..4).map { binding -> 0 to binding }
            ) {
                return GPUColorGlyphCompositeShaderResult.Rejected(
                    reason = "invalid_wgsl_reflection",
                    message = "Destination-read ColorGlyph WGSL reflection is not the sealed ABI.",
                )
            }
            val sourceHash = wgsl.sha256()
            val bindingLayoutKey = when (clipVariant) {
                GPUColorGlyphDestinationClipVariant.AnalyticRect ->
                    "g0:u0,texture2df1,filtering-sampler2,texture2df3,u4"
                GPUColorGlyphDestinationClipVariant.CoverageMask ->
                    "g0:u0,texture2df1,filtering-sampler2,texture2df3,texture2df4"
            }
            val seal = GPUColorGlyphDestinationProgramSeal(
                formulaId = "color_dodge@v1",
                modeLabel = "color_dodge",
                sourceCoverageEncoding = GPUSourceCoverageEncoding.ScalarCoverageInShader,
                clipVariant = clipVariant.stableLabel,
                targetFormat = GPUColorFormat.RGBA8UnormSrgb,
                wgslSha256 = sourceHash,
                reflectionModuleHash = reflection.report.moduleHash ?: sourceHash,
                reflectionFactsHash = reflection.report.reflectionFactsHash(),
                entryPoints = immutableList(entryPoints),
                bindingSlots = immutableList(bindingSlots),
                bindingLayoutKey = bindingLayoutKey,
                pipelineKey = listOf(
                    "color-glyph-destination-read",
                    "color_dodge@v1",
                    GPUSourceCoverageEncoding.ScalarCoverageInShader.name,
                    clipVariant.stableLabel,
                    GPUColorFormat.RGBA8UnormSrgb.value,
                    bindingLayoutKey,
                    reflection.report.reflectionFactsHash(),
                    sourceHash,
                ).joinToString(":"),
            )
            GPUColorGlyphCompositeShaderResult.Ready(
                GPUColorGlyphCompositePlan(
                    wgslSource = wgsl,
                    wgslReflection = reflection,
                    destinationProgramSeal = seal,
                ),
            )
        }
        is GPUColorWgslValidation.Rejected ->
            GPUColorGlyphCompositeShaderResult.Rejected(
                reason = validation.reason,
                message = validation.message,
            )
    }
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
