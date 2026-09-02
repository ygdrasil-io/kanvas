package org.graphiks.kanvas.pipeline

import org.graphiks.kanvas.paint.ColorFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RuntimeEffectCompileTest {

    @Test
    fun `compile valid WGSL returns success`() {
        RuntimeEffectWgsl4kWiring.install()
        val wgsl = """
            @fragment
            fn main() -> @location(0) vec4f {
                return vec4f(1.0, 0.0, 0.0, 1.0);
            }
        """.trimIndent()
        val result = RuntimeEffect.compile(wgsl)
        if (result.isFailure) {
            println("FAILED: ${result.exceptionOrNull()}")
        }
        assertTrue(result.isSuccess, "Expected success, got: ${result.exceptionOrNull()?.message}")
        assertNotNull(result.getOrNull())
    }

    @Test
    fun `compile invalid WGSL returns failure`() {
        val result = RuntimeEffect.compile("this is not valid wgsl")
        assertTrue(result.isFailure)
    }

    @Test
    fun `compile reflects struct uniform members and texture children without samplers`() {
        RuntimeEffectWgsl4kWiring.install()
        val wgsl = """
            struct Params {
                tint: vec4f,
                mode: i32,
            }
            @group(1) @binding(0) var<uniform> params: Params;
            @group(0) @binding(0) var input: texture_2d<f32>;
            @group(0) @binding(1) var input_sampler: sampler;

            fn source(uv: vec2f) -> vec4f {
                return textureSampleLevel(input, input_sampler, uv, 0.0) * params.tint;
            }
        """.trimIndent()

        val effect = RuntimeEffect.compile(wgsl).getOrThrow()

        assertEquals(listOf("tint", "mode"), effect.uniformLayout.slots.map { it.name })
        assertEquals(listOf(UniformType.FLOAT4, UniformType.INT1), effect.uniformLayout.slots.map { it.type })
        assertEquals(listOf(ChildSlot("input", ChildType.SHADER)), effect.children)
    }

    @Test
    fun `color filter construction preserves named runtime children`() {
        RuntimeEffectWgsl4kWiring.install()
        val effect = RuntimeEffect.compile(
            """
                @group(0) @binding(0) var child: texture_2d<f32>;
                @group(0) @binding(1) var child_sampler: sampler;
                fn source(color: vec4f) -> vec4f { return color; }
            """.trimIndent(),
        ).getOrThrow()

        val filter = assertIs<ColorFilter.RuntimeEffect>(
            effect.makeColorFilter(UniformBlock.EMPTY, mapOf("child" to ColorFilter.Luma)),
        )

        assertEquals(mapOf("child" to ColorFilter.Luma), filter.children)
    }
}
