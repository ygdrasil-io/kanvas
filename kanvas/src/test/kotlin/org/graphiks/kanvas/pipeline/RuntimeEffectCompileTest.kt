package org.graphiks.kanvas.pipeline

import kotlin.test.Test
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
}
