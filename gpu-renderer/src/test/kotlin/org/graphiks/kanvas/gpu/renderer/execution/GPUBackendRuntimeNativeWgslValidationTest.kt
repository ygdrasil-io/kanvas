package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.wgsl.parser.parseWgslResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class GPUBackendRuntimeNativeWgslValidationTest {
    @Test
    fun `executed A8 text atlas shader exposes source planes and parses through wgsl4k`() {
        val source = nativeTextAtlasA8WgslSource()

        assertContains(source, "sourcePlane: u32")
        assertContains(source, "uniforms.sourcePlane == 1u")
        assertContains(source, "uniforms.sourcePlane == 2u")

        val parsed = parseWgslResult(source)
        assertTrue(
            parsed.isSuccess,
            "wgsl4k rejected the executed A8 text atlas shader: ${parsed.errors.joinToString { it.message }}",
        )
    }
}
