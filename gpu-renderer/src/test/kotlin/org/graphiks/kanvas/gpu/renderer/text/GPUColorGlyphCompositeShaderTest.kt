package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.glyph.gpu.GPU_COLOR_GLYPH_COMPOSITE_MAX_LAYERS
import org.graphiks.kanvas.gpu.renderer.analysis.GPUColorGlyphRoutePlanner
import org.graphiks.kanvas.gpu.renderer.execution.GPUColorGlyphCompositeShaderResult
import org.graphiks.kanvas.gpu.renderer.execution.buildColorGlyphCompositeShader
import org.graphiks.kanvas.gpu.renderer.execution.buildColorGlyphDestinationReadShader
import org.graphiks.kanvas.gpu.renderer.execution.GPUColorGlyphDestinationClipVariant
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.payloads.GPU_COLOR_GLYPH_MAX_LAYERS
import org.graphiks.kanvas.gpu.renderer.wgsl.COLOR_GLYPH_COMPOSITE_MAX_LAYERS
import org.graphiks.kanvas.gpu.renderer.wgsl.colorGlyphCompositeWgsl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GPUColorGlyphCompositeShaderTest {

    @Test
    fun `destination read shader exposes the canonical reflected scalar program seal`() {
        val ready = assertIs<GPUColorGlyphCompositeShaderResult.Ready>(
            buildColorGlyphDestinationReadShader(),
        )
        val seal = assertNotNull(ready.plan.destinationProgramSeal)

        assertEquals("color_dodge@v1", seal.formulaId)
        assertEquals(
            GPUSourceCoverageEncoding.ScalarCoverageInShader,
            seal.sourceCoverageEncoding,
        )
        assertEquals("analytic-rect", seal.clipVariant)
        assertEquals(GPUColorFormat.RGBA8UnormSrgb, seal.targetFormat)
        assertEquals(listOf("fragment:fs_main", "vertex:vs_main"), seal.entryPoints)
        assertEquals((0..4).map { binding -> 0 to binding }, seal.bindingSlots)
        assertFailsWith<UnsupportedOperationException> {
            (seal.entryPoints as MutableList<String>).add("fragment:forged")
        }
        assertFailsWith<UnsupportedOperationException> {
            (seal.bindingSlots as MutableList<Pair<Int, Int>>).add(0 to 5)
        }
        assertTrue(seal.pipelineKey.endsWith(seal.wgslSha256))
        assertTrue(
            ready.plan.wgslSource.contains(
                "return dst + coverage * (blended - dst);",
            ),
        )
    }

    @Test
    fun `coverage mask destination shader seals a distinct texture binding ABI`() {
        val ready = assertIs<GPUColorGlyphCompositeShaderResult.Ready>(
            buildColorGlyphDestinationReadShader(
                clipVariant = GPUColorGlyphDestinationClipVariant.CoverageMask,
            ),
        )
        val seal = assertNotNull(ready.plan.destinationProgramSeal)

        assertEquals("coverage-mask", seal.clipVariant)
        assertTrue(seal.bindingLayoutKey.endsWith("texture2df4"))
        assertTrue(ready.plan.wgslSource.contains("var clip_coverage_mask: texture_2d<f32>;"))
        assertTrue(ready.plan.wgslSource.contains("let stored_sample = textureLoad("))
    }

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
        assertEquals(GPU_COLOR_GLYPH_COMPOSITE_MAX_LAYERS, GPU_COLOR_GLYPH_MAX_LAYERS)
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
