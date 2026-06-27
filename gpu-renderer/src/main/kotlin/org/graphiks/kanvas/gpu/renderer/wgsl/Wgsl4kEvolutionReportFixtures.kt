package org.graphiks.kanvas.gpu.renderer.wgsl

import java.nio.file.Files
import java.nio.file.Path

const val REVIEWED_WGSL4K_REFLECTION_SHA: String = "72a35b58758f241756d984a84768ae77308730da"

/** Evaluates whether all registered WGSL modules have graduated to parser-backed reflection. */
sealed interface Wgsl4kEvolutionGate {
    /** All WGSL modules use parser-backed reflection. */
    data object Passed : Wgsl4kEvolutionGate

    /** One or more WGSL modules still use fixture-declared reflection. */
    data class NotPassed(val fixtureModules: List<String>) : Wgsl4kEvolutionGate
}

/** Evaluates all WGSL modules and returns an evolution gate result. */
fun evaluateWgsl4kEvolutionGate(modules: List<WGSLModule>): Wgsl4kEvolutionGate {
    val fixtureModules = modules.filter { module ->
        val reflection = module.reflection
        reflection is WGSLReflectionResult.Accepted && reflection.reflectionSource == "fixture-declared"
    }

    return if (fixtureModules.isNotEmpty()) {
        Wgsl4kEvolutionGate.NotPassed(fixtureModules.map { it.moduleLabel })
    } else {
        Wgsl4kEvolutionGate.Passed
    }
}

/** A deterministic report fixture path and JSON payload generated for WGSL4K-EVO evidence. */
data class WgslEvolutionReportFixture(
    val relativePath: String,
    val contents: String,
)

/** Builds the complete WGSL4K-EVO-004 report fixture set for the reviewed wgsl4k SHA. */
fun wgsl4kEvolutionReportFixtures(
    wgsl4kSha: String = REVIEWED_WGSL4K_REFLECTION_SHA,
): List<WgslEvolutionReportFixture> {
    val textReport = textWgsl4kReflectionReport(wgsl4kSha)
    val textExpectation = textWgslReflectionExpectation()
    val runtimeReport = runtimeEffectWgsl4kReflectionReport(wgsl4kSha)
    val runtimeExpectation = runtimeEffectWgslReflectionExpectation()

    return listOf(
        WgslEvolutionReportFixture(
            relativePath = "text-wgsl-reflection.json",
            contents = consumeWgsl4kReflectionReport(textReport, textExpectation).toDeterministicJson() + "\n",
        ),
        WgslEvolutionReportFixture(
            relativePath = "text-wgsl-validation-report.json",
            contents = consumeWgsl4kReflectionReport(textReport, textExpectation).toDeterministicJson() + "\n",
        ),
        WgslEvolutionReportFixture(
            relativePath = "runtime-effect-wgsl-reflection.json",
            contents = consumeWgsl4kReflectionReport(runtimeReport, runtimeExpectation).toDeterministicJson() + "\n",
        ),
        WgslEvolutionReportFixture(
            relativePath = "runtime-effect-wgsl-validation-report.json",
            contents = consumeWgsl4kReflectionReport(runtimeReport, runtimeExpectation).toDeterministicJson() + "\n",
        ),
        WgslEvolutionReportFixture(
            relativePath = "negative/parser-failure.json",
            contents = consumeWgsl4kReflectionReport(
                textReport.copy(
                    validation = Wgsl4kValidationSummary(
                        success = false,
                        diagnostics = listOf(Wgsl4kDiagnostic("wgsl4k.validation.syntax_error", "expected ';'")),
                    ),
                ),
                textExpectation,
            ).toDeterministicJson() + "\n",
        ),
        WgslEvolutionReportFixture(
            relativePath = "negative/binding-mismatch.json",
            contents = consumeWgsl4kReflectionReport(
                textReport.copy(bindings = textReport.bindings.drop(1)),
                textExpectation,
            ).toDeterministicJson() + "\n",
        ),
        WgslEvolutionReportFixture(
            relativePath = "negative/layout-mismatch.json",
            contents = consumeWgsl4kReflectionReport(
                textReport.copy(
                    layouts = listOf(
                        textReport.layouts.single().copy(
                            members = listOf(
                                Wgsl4kLayoutMemberReflection(
                                    name = "atlasScale",
                                    type = "vec2<f32>",
                                    offset = 4,
                                    size = 8,
                                    alignment = 8,
                                    stride = null,
                                ),
                            ),
                        ),
                    ),
                ),
                textExpectation,
            ).toDeterministicJson() + "\n",
        ),
        WgslEvolutionReportFixture(
            relativePath = "negative/unregistered-module.json",
            contents = consumeWgsl4kReflectionReport(
                textReport.copy(sourceId = "text/unregistered.wgsl"),
                textExpectation,
            ).toDeterministicJson() + "\n",
        ),
    ).sortedBy { it.relativePath }
}

/** Writes the generated WGSL4K-EVO-004 report fixtures to an output directory. */
fun writeWgsl4kEvolutionReportFixtures(
    outputDir: Path,
    wgsl4kSha: String = REVIEWED_WGSL4K_REFLECTION_SHA,
): List<Path> =
    wgsl4kEvolutionReportFixtures(wgsl4kSha).map { fixture ->
        val path = outputDir.resolve(fixture.relativePath)
        Files.createDirectories(path.parent)
        Files.writeString(path, fixture.contents)
        path
    }

/** CLI entry point for the Gradle report fixture export task. */
fun main(args: Array<String>) {
    val outputDir = args.firstOrNull()?.let(Path::of)
        ?: Path.of("reports/wgsl4k-evolution/generated")
    val wgsl4kSha = args.getOrNull(1) ?: REVIEWED_WGSL4K_REFLECTION_SHA
    val written = writeWgsl4kEvolutionReportFixtures(outputDir, wgsl4kSha)
    println("wgsl4k-evolution-report-fixtures output=$outputDir files=${written.size} sha=$wgsl4kSha")
    written.sorted().forEach { println(it) }
}

private fun textWgslReflectionExpectation(): WgslReflectionExpectation =
    WgslReflectionExpectation(
        reportKind = "text",
        moduleId = "text.a8-mask",
        allowedSourceIds = setOf("text/a8_text_mask.wgsl"),
        expectedEntryPoints = listOf(WgslExpectedEntryPoint("fragmentMain", "fragment")),
        expectedBindings = listOf(
            WgslExpectedBinding(2, 0, "glyphAtlas", "sampledTexture", minBindingSize = null),
            WgslExpectedBinding(2, 1, "glyphSampler", "sampler", minBindingSize = null),
            WgslExpectedBinding(2, 2, "textParams", "uniformBuffer", minBindingSize = 16),
        ),
        expectedLayouts = listOf(
            WgslExpectedLayout(
                structName = "TextParams",
                addressSpace = "uniform",
                size = 16,
                alignment = 8,
                members = listOf(
                    WGSLUniformFieldLayout("atlasScale", "vec2<f32>", offset = 0L, sizeBytes = 8L, alignment = 8),
                    WGSLUniformFieldLayout("maskGamma", "f32", offset = 8L, sizeBytes = 4L, alignment = 4),
                ),
            ),
        ),
        routePromotion = "not-promoted",
        productActivation = true,
    )

private fun runtimeEffectWgslReflectionExpectation(): WgslReflectionExpectation =
    WgslReflectionExpectation(
        reportKind = "runtime-effect",
        moduleId = "runtime.simple.color",
        allowedSourceIds = setOf("runtime/runtime_simple_rt.wgsl"),
        expectedEntryPoints = listOf(WgslExpectedEntryPoint("fs_main", "fragment")),
        expectedBindings = listOf(
            WgslExpectedBinding(1, 0, "runtimeUniforms", "uniformBuffer", minBindingSize = 16),
        ),
        expectedLayouts = listOf(
            WgslExpectedLayout(
                structName = "RuntimeSimpleUniforms",
                addressSpace = "uniform",
                size = 16,
                alignment = 16,
                members = listOf(
                    WGSLUniformFieldLayout("color", "vec4<f32>", offset = 0L, sizeBytes = 16L, alignment = 16),
                ),
            ),
        ),
        descriptorId = "runtime.simple.color",
        descriptorVersion = 1,
        routePromotion = "not-promoted",
        productActivation = true,
    )

private fun textWgsl4kReflectionReport(wgsl4kSha: String): Wgsl4kReflectionReport =
    Wgsl4kReflectionReport(
        sourceId = "text/a8_text_mask.wgsl",
        moduleHash = "sha256:text-a8-mask",
        wgsl4kSha = wgsl4kSha,
        validation = Wgsl4kValidationSummary(success = true),
        entryPoints = listOf(Wgsl4kEntryPointReflection("fragmentMain", "fragment")),
        bindings = listOf(
            Wgsl4kBindingReflection(2, 0, "glyphAtlas", "sampledTexture", minBindingSize = null),
            Wgsl4kBindingReflection(2, 1, "glyphSampler", "sampler", minBindingSize = null),
            Wgsl4kBindingReflection(2, 2, "textParams", "uniformBuffer", minBindingSize = 16),
        ),
        layouts = listOf(
            Wgsl4kLayoutReflection(
                structName = "TextParams",
                addressSpace = "uniform",
                size = 16,
                alignment = 8,
                members = listOf(
                    Wgsl4kLayoutMemberReflection("atlasScale", "vec2<f32>", offset = 0, size = 8, alignment = 8, stride = null),
                    Wgsl4kLayoutMemberReflection("maskGamma", "f32", offset = 8, size = 4, alignment = 4, stride = null),
                ),
            ),
        ),
    )

private fun runtimeEffectWgsl4kReflectionReport(wgsl4kSha: String): Wgsl4kReflectionReport =
    Wgsl4kReflectionReport(
        sourceId = "runtime/runtime_simple_rt.wgsl",
        moduleHash = "sha256:runtime-simple",
        wgsl4kSha = wgsl4kSha,
        validation = Wgsl4kValidationSummary(success = true),
        entryPoints = listOf(Wgsl4kEntryPointReflection("fs_main", "fragment")),
        bindings = listOf(
            Wgsl4kBindingReflection(1, 0, "runtimeUniforms", "uniformBuffer", minBindingSize = 16),
        ),
        layouts = listOf(
            Wgsl4kLayoutReflection(
                structName = "RuntimeSimpleUniforms",
                addressSpace = "uniform",
                size = 16,
                alignment = 16,
                members = listOf(
                    Wgsl4kLayoutMemberReflection("color", "vec4<f32>", offset = 0, size = 16, alignment = 16, stride = null),
                ),
            ),
        ),
    )
