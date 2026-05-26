package org.skia.gpu.webgpu.tools

import io.ygdrasil.wgsl.parser.parseWgslResult
import io.ygdrasil.wgsl.parser.Lowerer
import io.ygdrasil.wgsl.wgsl.WgslModule

data class WgslValidationResult(
    val isSuccess: Boolean,
    val diagnostics: List<String>,
)

object GeneratedSolidRectWgsl {
    const val FEATURE_FLAG = "kanvas.gpu.generatedSolidRect.enabled"

    fun generateDeterministic(): String {
        val seedSource = seedRectSolidSrcOverWgsl()
        val parsed = parseWgslResult(seedSource)
        if (!parsed.isSuccess) {
            val diagnostics = parsed.errors.joinToString("; ") { "${it.message} span=${it.span}" }
            error("generated solid rect seed parse failure: $diagnostics")
        }
        val module = Lowerer().lower(parsed.translationUnit)
        return WgslModule.writeString(module)
    }

    private fun seedRectSolidSrcOverWgsl(): String = listOf(
        "struct Uniforms {",
        "    color: vec4f,",
        "    outerBounds: vec4f,",
        "    innerBounds: vec4f,",
        "};",
        "@binding(0) @group(0) var<uniform> uniforms: Uniforms;",
        "@vertex",
        "fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {",
        "    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;",
        "    let y = f32(idx & 2u) * 2.0 - 1.0;",
        "    return vec4f(x, y, 0.0, 1.0);",
        "}",
        "@fragment",
        "fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {",
        "    let ob = uniforms.outerBounds;",
        "    let outerCovX = clamp(min(pos.x + 0.5, ob.z) - max(pos.x - 0.5, ob.x), 0.0, 1.0);",
        "    let outerCovY = clamp(min(pos.y + 0.5, ob.w) - max(pos.y - 0.5, ob.y), 0.0, 1.0);",
        "    let ib = uniforms.innerBounds;",
        "    let innerCovX = clamp(min(pos.x + 0.5, ib.z) - max(pos.x - 0.5, ib.x), 0.0, 1.0);",
        "    let innerCovY = clamp(min(pos.y + 0.5, ib.w) - max(pos.y - 0.5, ib.y), 0.0, 1.0);",
        "    let coverage = max(0.0, outerCovX * outerCovY - innerCovX * innerCovY);",
        "    let a = uniforms.color.a * coverage;",
        "    return vec4f(uniforms.color.rgb * a, a);",
        "}",
    ).joinToString(separator = "\n", postfix = "\n")

    fun validate(source: String): WgslValidationResult {
        val parsed = parseWgslResult(source)
        val diagnostics = parsed.errors.map { "${it.message} span=${it.span}" }
        return WgslValidationResult(isSuccess = parsed.isSuccess, diagnostics = diagnostics)
    }
}
