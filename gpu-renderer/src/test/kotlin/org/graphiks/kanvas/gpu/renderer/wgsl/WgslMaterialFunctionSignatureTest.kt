package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult

class WgslMaterialFunctionSignatureTest {
    @Test
    fun `IR proves the exact composable material function signature`() {
        assertTrue(
            lower(
                "fn material(p: vec2<f32>) -> vec4<f32> { return vec4<f32>(p, 0.0, 1.0); }",
            ).hasMaterialColorFunctionSignature("material"),
        )
        assertFalse(
            lower(
                "fn material(p: vec3<f32>) -> vec4<f32> { return vec4<f32>(p, 1.0); }",
            ).hasMaterialColorFunctionSignature("material"),
        )
        assertFalse(
            lower(
                "fn material(p: vec2<f32>) -> vec3<f32> { return vec3<f32>(p, 1.0); }",
            ).hasMaterialColorFunctionSignature("material"),
        )
        assertFalse(
            lower(
                "fn other(p: vec2<f32>) -> vec4<f32> { return vec4<f32>(p, 0.0, 1.0); }",
            ).hasMaterialColorFunctionSignature("material"),
        )
    }

    private fun lower(source: String) =
        parseWgslResult(source).let { parsed ->
            require(parsed.isSuccess)
            Lowerer().lower(parsed.translationUnit)
        }
}
