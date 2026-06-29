package org.graphiks.kanvas.gpu.renderer.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GPUColorGlyphCompositeShaderTest {

    @Test
    fun `builds a wgsl4k-validated COLRv0 composite shader`() {
        val result = buildColorGlyphCompositeShader()

        val ready = assertIs<GPUColorGlyphCompositeShaderResult.Ready>(result)
        assertNotNull(ready.plan.wgslReflection)
        assertTrue(ready.plan.wgslSource.contains("fn fs_main"))
        assertTrue(ready.plan.wgslSource.contains("textureSample"))
        assertTrue(ready.plan.wgslSource.contains("layerColors"))
    }

    @Test
    fun `composite shader uses the COLRv0 layer budget`() {
        assertEquals(16, COLOR_GLYPH_COMPOSITE_MAX_LAYERS)
        val wgsl = colorGlyphCompositeWgsl()
        assertTrue(wgsl.contains("array<vec4f, 16>"))
        assertTrue(wgsl.contains("16u"))
    }
}
