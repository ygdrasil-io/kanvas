package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.gpu.renderer.analysis.GPUColorGlyphRoutePlanner
import org.graphiks.kanvas.gpu.renderer.wgsl.COLOR_GLYPH_COMPOSITE_MAX_LAYERS
import org.graphiks.kanvas.gpu.renderer.wgsl.colorGlyphCompositeWgsl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GPUColorGlyphCompositeShaderTest {

    @Test
    fun `builds a parser-backed validated COLRv0 composite shader`() {
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
        assertEquals(GPUColorGlyphRoutePlanner.MAX_COLOR_LAYERS, COLOR_GLYPH_COMPOSITE_MAX_LAYERS)
        val wgsl = colorGlyphCompositeWgsl()
        assertTrue(wgsl.contains("array<vec4f, 16>"))
        assertTrue(wgsl.contains("16u"))
    }

    @Test
    fun `composite shader honors a custom layer budget`() {
        val wgsl = colorGlyphCompositeWgsl(maxLayers = 4)
        assertTrue(wgsl.contains("array<vec4f, 4>"))
        assertTrue(wgsl.contains("4u"))
    }
}
