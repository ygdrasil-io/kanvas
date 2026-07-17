package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.gpu.renderer.analysis.GPUColorGlyphRoutePlanner
import org.graphiks.kanvas.gpu.renderer.execution.GPUColorGlyphCompositeShaderResult
import org.graphiks.kanvas.gpu.renderer.execution.buildColorGlyphCompositeShader
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
        assertTrue(ready.plan.wgslSource.contains("layerDeviceRects"))
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

    @Test
    fun `composite shader maps fragment device position into each layer rectangle`() {
        val wgsl = colorGlyphCompositeWgsl()

        assertTrue(wgsl.contains("layerDeviceRects: array<vec4f, 16>"))
        assertTrue(wgsl.contains("let device_xy = in.position.xy;"))
        assertTrue(wgsl.contains("device_xy.x >= device_rect.x"))
        assertTrue(wgsl.contains("device_xy.x < device_rect.x + device_rect.z"))
        assertTrue(wgsl.contains("device_xy.y >= device_rect.y"))
        assertTrue(wgsl.contains("device_xy.y < device_rect.y + device_rect.w"))
        assertTrue(wgsl.contains("let local_uv = (device_xy - device_rect.xy) / device_rect.zw;"))
        assertTrue(wgsl.contains("let atlas_uv = atlas_rect.xy + local_uv * atlas_rect.zw;"))
    }

    @Test
    fun `composite shader exposes one exact bind group for uniform atlas and nearest sampler`() {
        val wgsl = colorGlyphCompositeWgsl()

        assertTrue(wgsl.contains("@group(0) @binding(0) var<uniform> uniforms: Uniforms;"))
        assertTrue(wgsl.contains("@group(0) @binding(1) var coverage_atlas: texture_2d<f32>;"))
        assertTrue(wgsl.contains("@group(0) @binding(2) var coverage_sampler: sampler;"))
        assertEquals(0, Regex("@group\\((?!0\\))\\d+\\)").findAll(wgsl).count())
    }
}
