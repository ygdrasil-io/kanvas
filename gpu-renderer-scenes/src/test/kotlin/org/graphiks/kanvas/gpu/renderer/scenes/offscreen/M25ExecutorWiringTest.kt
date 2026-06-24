package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.geometry.ConvexFanExecutor
import org.graphiks.kanvas.gpu.renderer.geometry.PathData
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.PathVerb
import org.graphiks.kanvas.gpu.renderer.geometry.Point
import org.graphiks.kanvas.gpu.renderer.geometry.StencilCoverExecutor
import org.graphiks.kanvas.gpu.renderer.geometry.isPathConvex
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTWgsl

/**
 * KGPU-M25-001..006: proves the offscreen renderer routes each family through the real delivered
 * executors / module snippets for diagnostic evidence. These assertions cover the wiring only;
 * real textures, atlases, secondary targets, and vertex/index buffers remain deferred to M26 and
 * no family is promoted to product support here (ImplementationCandidate).
 */
class M25ExecutorWiringTest {
    @Test
    fun `KGPU-M25-001 bitmap shader routes through the real snippet identity`() {
        // KGPU-M25-001 note: Bitmap integration is by snippet identity/reference
        // (BitmapShaderSnippetSourceHash + BitmapShaderClampEntryPoint), NOT by invoking a
        // dedicated executor. The real texture sampling (textureSample over an uploaded texture)
        // requires M26; until then the offscreen fullscreen-uniform backend only carries the
        // snippet identity + packed color uniform, with the procedural test texture staying in the
        // renderer wrapper (realTextureDeferred=M26).
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
    fun `KGPU-M25-003 runtime effect WGSL is imported from the real SimpleRTWgsl module not an inline copy`() {
        val wgsl = composeRuntimeEffectWgsl()
        // The runtime-effect pass must embed the registered SimpleRTWgsl module source verbatim;
        // only the bind group is rebound from @group(1) to @group(0) so the offscreen
        // fullscreen-uniform backend binds the gColor uniform at group 0. No inline copy.
        assertTrue(wgsl.contains(SimpleRTWgsl.replace("@group(1)", "@group(0)")), wgsl)
        assertTrue(wgsl.contains("struct SimpleRTUniform"), wgsl)
        assertTrue(wgsl.contains("uSimpleRT.gColor"), wgsl)
        assertTrue(wgsl.contains("@group(0) @binding(0)"), wgsl)
        assertFalse(wgsl.contains("@group(1)"), wgsl)
        assertTrue(wgsl.contains("$SimpleRTEntryPoint(pos.xy)"), wgsl)
    }

    @Test
    fun `KGPU-M25-004 save layer routes through SaveLayerExecutor`() {
        val lines = saveLayerWiringDiagnostics(sceneId = "savelayer-composite", width = 320, height = 200)
        assertTrue(lines.any { it.contains("savelayer:executor targetAllocated=true") }, lines.toString())
        assertTrue(lines.any { it.contains("compositeSnippetSourceHash=fragment:layer_composite:v1") }, lines.toString())
        assertTrue(lines.any { it.contains("secondaryTargetDeferred=M26") }, lines.toString())
    }

    @Test
    fun `KGPU-M25-005 path fill routes through PathTessellator StencilCoverExecutor and ConvexFanExecutor`() {
        val tessellator = PathTessellator()

        // Concave star contour -> StencilCoverExecutor (two-pass stencil + cover).
        val starFlat = tessellator.flatten(loopPath(starVertices()))
        val starTri = tessellator.triangulate(starFlat)
        assertTrue(starTri.triangleCount > 0, "PathTessellator produced no triangles for star")
        assertFalse(isPathConvex(starFlat), "star contour must be classified concave")
        val stencilStats = StencilCoverExecutor().execute(starTri)
        assertEquals(1, stencilStats.stencilPassCount, "stencil pass")
        assertEquals(1, stencilStats.coverPassCount, "cover pass")
        assertEquals(2, stencilStats.totalDrawCalls, "stencil-cover totalDraws")
        val stencilDiag = StencilCoverExecutor().stencilStateDiagnostics()
        assertTrue(stencilDiag.any { it.contains("twoPass=true") }, stencilDiag.toString())

        // Convex octagon contour -> ConvexFanExecutor (single-pass fan).
        val octFlat = tessellator.flatten(loopPath(octagonVertices()))
        val octTri = tessellator.triangulate(octFlat)
        assertTrue(octTri.triangleCount > 0, "PathTessellator produced no triangles for octagon")
        assertTrue(isPathConvex(octFlat), "octagon contour must be classified convex")
        val convexStats = ConvexFanExecutor().execute(octTri)
        assertTrue(convexStats.singlePass, "convex fan must be single-pass")
        assertEquals(1, convexStats.drawCallCount, "convex fan draw calls")
        val perf = ConvexFanExecutor().performanceDiagnostics(convexStats, stencilStats)
        assertTrue(perf.any { it.contains("singlePass=true") }, perf.toString())
    }

    @Test
    fun `KGPU-M25-006 vertices route through executor uploader and batcher`() {
        val lines = verticesWiringDiagnostics()
        assertTrue(lines.any { it.startsWith("vertices:executor executed=true") }, lines.toString())
        assertTrue(lines.any { it.startsWith("vertices:uploader uploaded=true") }, lines.toString())
        assertTrue(lines.any { it.startsWith("vertices:batcher inputDraws=2") }, lines.toString())
        assertTrue(lines.any { it.contains("realMeshDeferred=M26") }, lines.toString())
    }

    private fun loopPath(vertices: List<Point>): PathData =
        PathData(
            verbs = vertices.map { PathVerb.LineTo(it) } + listOf(PathVerb.Close),
            points = emptyList(),
        )

    private fun starVertices(): List<Point> {
        val centerX = 160f
        val centerY = 100f
        val outerRadius = 80f
        val innerRadius = 35f
        val points = 5
        return (0 until points * 2).map { i ->
            val angle = Math.PI * i / points - Math.PI / 2
            val r = if (i % 2 == 0) outerRadius else innerRadius
            Point((centerX + r * Math.cos(angle)).toFloat(), (centerY + r * Math.sin(angle)).toFloat())
        }
    }

    private fun octagonVertices(): List<Point> {
        val centerX = 160f
        val centerY = 100f
        val radius = 80f
        val sides = 8
        return (0 until sides).map { i ->
            val angle = 2.0 * Math.PI * i / sides - Math.PI / 2
            Point((centerX + radius * Math.cos(angle)).toFloat(), (centerY + radius * Math.sin(angle)).toFloat())
        }
    }
}
