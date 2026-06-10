package org.skia.gpu.webgpu.tools

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

data class WgslStrictModuleSpec(
    val label: String,
    val expectedEntryPoints: Set<String>,
    val expectedBindings: Set<String>,
    val expectedUniformVariables: Set<String>,
)

data class WgslStrictModuleReport(
    val spec: WgslStrictModuleSpec,
    val validation: WgslValidationFileReport,
) {
    val failures: List<String>
        get() {
            val missingEntryPoints = spec.expectedEntryPoints - validation.entryPoints.toSet()
            val missingBindings = spec.expectedBindings - validation.bindings.toSet()
            val reflectedUniformVariables = validation.uniformStructs
                .filter { it.source == UniformReflectionSource.LoweredLayout }
                .map { it.variable }
                .toSet()
            val missingUniforms = spec.expectedUniformVariables - reflectedUniformVariables
            return buildList {
                if (!validation.success) {
                    add("parse failed")
                }
                validation.diagnostics.forEach { diagnostic ->
                    add("diagnostic: $diagnostic")
                }
                missingEntryPoints.forEach { entryPoint ->
                    add("missing entrypoint: $entryPoint")
                }
                missingBindings.forEach { binding ->
                    add("missing binding: $binding")
                }
                missingUniforms.forEach { uniform ->
                    add("missing reflected uniform: $uniform")
                }
            }
        }

    val success: Boolean get() = failures.isEmpty()
}

data class WgslStrictValidationSummary(
    val modules: List<WgslStrictModuleReport>,
) {
    val moduleCount: Int get() = modules.size
    val failedModules: List<WgslStrictModuleReport> get() = modules.filterNot { it.success }
    val success: Boolean get() = failedModules.isEmpty()
}

object WgslStrictValidationReport {
    private val generatedSolidSpec = WgslStrictModuleSpec(
        label = "generated/solid_rect_generated.wgsl",
        expectedEntryPoints = setOf("vertex:vs_main", "fragment:fs_main"),
        expectedBindings = setOf("uniforms@group=0,binding=0"),
        expectedUniformVariables = setOf("uniforms"),
    )
    private val generatedLinearGradientSpec = WgslStrictModuleSpec(
        label = "generated/linear_gradient_generated.wgsl",
        expectedEntryPoints = setOf("vertex:vs_main", "fragment:fs_clamp"),
        expectedBindings = setOf("uniforms@group=0,binding=0"),
        expectedUniformVariables = setOf("uniforms"),
    )
    private val runtimeSimpleRtSpec = WgslStrictModuleSpec(
        label = "registered/runtime_simple_rt.wgsl",
        expectedEntryPoints = setOf("vertex:vs_main", "fragment:fs_main"),
        expectedBindings = setOf("uniforms@group=0,binding=0"),
        expectedUniformVariables = setOf("uniforms"),
    )
    private val runtimeSpiralRtSpec = WgslStrictModuleSpec(
        label = "registered/runtime_spiral_rt.wgsl",
        expectedEntryPoints = setOf("vertex:vs_main", "fragment:fs_main"),
        expectedBindings = setOf("uniforms@group=0,binding=0"),
        expectedUniformVariables = setOf("uniforms"),
    )
    private val runtimeLinearGradientRtSpec = WgslStrictModuleSpec(
        label = "registered/runtime_linear_gradient_rt.wgsl",
        expectedEntryPoints = setOf("vertex:vs_main", "fragment:fs_main"),
        expectedBindings = setOf("uniforms@group=0,binding=0"),
        expectedUniformVariables = setOf("uniforms"),
    )
    private val runtimeColorFilterLumaToAlphaSpec = WgslStrictModuleSpec(
        label = "registered/runtime_color_filter_luma_to_alpha.wgsl",
        expectedEntryPoints = setOf("vertex:vs_main", "fragment:fs_main"),
        expectedBindings = setOf("uniforms@group=0,binding=0"),
        expectedUniformVariables = setOf("uniforms"),
    )

    fun run(shaderRoot: Path = Path.of("src/main/resources/shaders")): WgslStrictValidationSummary {
        val runtimeColorFilterLumaToAlpha = shaderRoot.resolve("runtime_color_filter_luma_to_alpha.wgsl")
        val runtimeLinearGradientRt = shaderRoot.resolve("runtime_linear_gradient_rt.wgsl")
        val runtimeSimpleRt = shaderRoot.resolve("runtime_simple_rt.wgsl")
        val runtimeSpiralRt = shaderRoot.resolve("runtime_spiral_rt.wgsl")
        val modules = listOf(
            validate(generatedSolidSpec, GeneratedSolidRectWgsl.generateDeterministic()),
            validate(generatedLinearGradientSpec, GeneratedLinearGradientWgsl.generateDeterministic()),
            validate(runtimeColorFilterLumaToAlphaSpec, Files.readString(runtimeColorFilterLumaToAlpha)),
            validate(runtimeLinearGradientRtSpec, Files.readString(runtimeLinearGradientRt)),
            validate(runtimeSimpleRtSpec, Files.readString(runtimeSimpleRt)),
            validate(runtimeSpiralRtSpec, Files.readString(runtimeSpiralRt)),
        )
        return WgslStrictValidationSummary(modules)
    }

    private fun validate(spec: WgslStrictModuleSpec, source: String): WgslStrictModuleReport =
        WgslStrictModuleReport(
            spec = spec,
            validation = WgslValidationReport.validateSource(spec.label, source),
        )
}

fun main(args: Array<String>) {
    val shaderRoot = args.firstOrNull()?.let(Path::of) ?: Path.of("src/main/resources/shaders")
    val summary = WgslStrictValidationReport.run(shaderRoot)
    println(
        "wgsl-validate-strict root=$shaderRoot modules=${summary.moduleCount} " +
            "failed=${summary.failedModules.size}",
    )
    summary.modules.forEach { report ->
        println(
            "strict-module=${Path.of(report.spec.label).name} success=${report.success} " +
                "entrypoints=${report.validation.entryPoints.size} bindings=${report.validation.bindings.size} " +
                "uniforms=${report.validation.uniformStructs.size}",
        )
        report.failures.forEach { failure -> println("strict-failure ${report.spec.label} $failure") }
    }
    if (!summary.success) {
        error(
            "Strict WGSL validation failed for " +
                summary.failedModules.joinToString(", ") { it.spec.label },
        )
    }
}
