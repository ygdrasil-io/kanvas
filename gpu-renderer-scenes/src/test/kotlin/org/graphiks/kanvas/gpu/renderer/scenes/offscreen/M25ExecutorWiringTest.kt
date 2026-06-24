package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * KGPU-M25-001..006: proves the offscreen renderer routes each family through the real delivered
 * executors / module snippets for diagnostic evidence. These assertions cover the wiring only;
 * real textures, atlases, secondary targets, and vertex/index buffers remain deferred to M26 and
 * no family is promoted to product support here (ImplementationCandidate).
 */
class M25ExecutorWiringTest {
    @Test
    fun `KGPU-M25-001 bitmap shader routes through the real snippet identity`() {
        val lines = bitmapShaderWiringDiagnostics()
        assertTrue(lines.any { it.contains("snippetSourceHash=fragment:bitmap_shader:v1") }, lines.toString())
        assertTrue(lines.any { it.contains("entryPoint=bitmap_shader_clamp") }, lines.toString())
        assertTrue(lines.any { it.contains("uniformPacker=UniformPacker.bitmapBytes") }, lines.toString())
        assertTrue(lines.any { it.contains("realTextureDeferred=M26") }, lines.toString())
    }

    @Test
    fun `KGPU-M25-002 text routes through TextA8AtlasExecutor and SDFGenerator`() {
        val lines = textAtlasWiringDiagnostics(width = 320, height = 200)
        assertTrue(lines.any { it.startsWith("textA8Atlas:executor accepted=") }, lines.toString())
        assertTrue(lines.any { it.contains("nonclaim:no-gpu-atlas-pages") }, lines.toString())
        assertTrue(lines.any { it.startsWith("textSdf:generator accepted=true") }, lines.toString())
        assertTrue(lines.any { it.contains("nonclaim:no-hybrid-sdf") }, lines.toString())
        assertTrue(lines.any { it.contains("realAtlasDeferred=M26") }, lines.toString())
    }

    @Test
    fun `KGPU-M25-003 runtime effect routes through the registered SimpleRT snippet`() {
        val lines = runtimeEffectWiringDiagnostics()
        assertTrue(lines.any { it.contains("wgslSnippetSourceHash=fragment:simple_rt:v1") }, lines.toString())
        assertTrue(lines.any { it.contains("entryPoint=simple_rt_source") }, lines.toString())
        assertTrue(lines.any { it.contains("uniformPacker=UniformPacker.simpleRtBytes") }, lines.toString())
        assertTrue(lines.any { it.contains("realGpuOutput=true") }, lines.toString())
    }

    @Test
    fun `KGPU-M25-004 save layer routes through SaveLayerExecutor`() {
        val lines = saveLayerWiringDiagnostics(sceneId = "savelayer-composite", width = 320, height = 200)
        assertTrue(lines.any { it.contains("savelayer:executor targetAllocated=true") }, lines.toString())
        assertTrue(lines.any { it.contains("compositeSnippetSourceHash=fragment:layer_composite:v1") }, lines.toString())
        assertTrue(lines.any { it.contains("secondaryTargetDeferred=M26") }, lines.toString())
    }

    @Test
    fun `KGPU-M25-006 vertices route through executor uploader and batcher`() {
        val lines = verticesWiringDiagnostics()
        assertTrue(lines.any { it.startsWith("vertices:executor executed=true") }, lines.toString())
        assertTrue(lines.any { it.startsWith("vertices:uploader uploaded=true") }, lines.toString())
        assertTrue(lines.any { it.startsWith("vertices:batcher inputDraws=2") }, lines.toString())
        assertTrue(lines.any { it.contains("realMeshDeferred=M26") }, lines.toString())
    }
}
