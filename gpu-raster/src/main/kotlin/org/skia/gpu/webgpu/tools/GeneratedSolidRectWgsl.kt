package org.skia.gpu.webgpu.tools

import io.ygdrasil.wgsl.parser.parseWgslResult
import io.ygdrasil.wgsl.parser.Lowerer
import io.ygdrasil.wgsl.wgsl.WgslModule
import java.io.InputStreamReader

data class WgslValidationResult(
    val isSuccess: Boolean,
    val diagnostics: List<String>,
)

object GeneratedSolidRectWgsl {
    const val FEATURE_FLAG = "kanvas.gpu.generatedSolidRect.enabled"
    private const val SOURCE_SHADER_RESOURCE = "shaders/solid_color.wgsl"

    fun generateDeterministic(): String {
        val seedSource = loadSourceShader()
        val parsed = parseWgslResult(seedSource)
        if (!parsed.isSuccess) {
            val diagnostics = parsed.errors.joinToString("; ") { "${it.message} span=${it.span}" }
            error("generated solid rect seed parse failure: $diagnostics")
        }
        val module = Lowerer().lower(parsed.translationUnit)
        return WgslModule.writeString(module)
    }

    private fun loadSourceShader(): String {
        val stream = GeneratedSolidRectWgsl::class.java.classLoader
            .getResourceAsStream(SOURCE_SHADER_RESOURCE)
            ?: error("$SOURCE_SHADER_RESOURCE missing from classpath")
        return InputStreamReader(stream, Charsets.UTF_8).use { it.readText() }
    }

    fun validate(source: String): WgslValidationResult {
        val parsed = parseWgslResult(source)
        val diagnostics = parsed.errors.map { "${it.message} span=${it.span}" }
        return WgslValidationResult(isSuccess = parsed.isSuccess, diagnostics = diagnostics)
    }
}
