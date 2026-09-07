package org.graphiks.kanvas.gpu.plan

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.ClipEntry
import org.graphiks.kanvas.render.ir.ClipOperation
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.PathEffectNode
import org.graphiks.kanvas.render.ir.ImmutableFloats
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.render.ir.StrokeCapNode
import org.graphiks.kanvas.render.ir.StrokeJoinNode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeLimitsI32
import org.graphiks.math.geometry.PathStrokeLimitsI64
import org.graphiks.math.geometry.PathStrokePolicyF64
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test

class W4dPathStrokePlanCompilerTest {
    private val compiler = W4dPathStrokePlanCompiler()

    @Test
    fun selectsMixedFramesOnlyWhenTheyContainAStroke() {
        val mixed = sceneOf(listOf(pathDraw(PaintStyleNode.FILL), pathDraw(PaintStyleNode.STROKE)))
        val fills = sceneOf(listOf(pathDraw(PaintStyleNode.FILL)))

        assertIs<GpuPlanSelection.Candidate>(compiler.select(mixed, target(mixed)))
        assertIs<GpuPlanSelection.NotCandidate>(compiler.select(fills, target(fills)))
    }

    @Test
    fun acceptsOneAnd512StrokeDrawsButNot513() {
        val one = sceneOf(listOf(pathDraw(PaintStyleNode.STROKE)))
        val many = sceneOf(List(512) { pathDraw(PaintStyleNode.STROKE) })
        val tooMany = sceneOf(List(513) { pathDraw(PaintStyleNode.STROKE) })

        assertIs<GpuPlanSelection.Candidate>(compiler.select(one, target(one)))
        assertIs<GpuPlanSelection.Candidate>(compiler.select(many, target(many)))
        assertIs<GpuPlanSelection.NotCandidate>(compiler.select(tooMany, target(tooMany)))
    }

    @Test
    fun planningSealsTheMathStrokeAsTheOnlyPathDrawAuthority() {
        val scene = sceneOf(listOf(pathDraw(PaintStyleNode.STROKE)))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, capabilities(), PlanBudget(1L shl 20)),
        ).plan

        assertEquals(W4dPathStrokePlanCompiler.CAPABILITY_ID, graph.capabilityId)
        assertIs<PathStrokeDraw>(assertIs<PlanPass.StencilProducer>(graph.passes().first()).draw)
    }

    @Test
    fun zeroWidthStrokeAndFillSealsFiniteZeroInsteadOfHairline() {
        val scene = sceneOf(listOf(pathDraw(PaintStyleNode.STROKE_AND_FILL, 0f)))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, capabilities(), PlanBudget(1L shl 20)),
        ).plan

        val draw = assertIs<PathStrokeDraw>(graph.passes().mapNotNull { pass ->
            when (pass) {
                is PlanPass.RenderPass -> pass.draws().singleOrNull()
                is PlanPass.StencilProducer -> pass.draw
                else -> null
            }
        }.single())
        assertEquals(PathStrokeDrawMode.StrokeAndFill, draw.mode)
        assertEquals(0.0, assertIs<PathStrokeWidthF64.Finite>(draw.styleF64.widthF64).valueF64)
    }

    @Test
    fun `513 visual draws are refused even when the first 512 are off target`() {
        val offTarget = pathDraw(PaintStyleNode.STROKE).let { command ->
            SceneCommand.Draw(command.node.copy(transform = Matrix3x3F32(tx = 1_000f, ty = 1_000f)))
        }
        val scene = sceneOf(List(512) { offTarget } + pathDraw(PaintStyleNode.STROKE))

        assertIs<GpuPlanSelection.NotCandidate>(compiler.select(scene, target(scene)))
    }

    @Test
    fun nonFiniteUnsupportedPathEffectIsInvalidBeforeItsCapabilityGap() {
        val command = pathDraw(PaintStyleNode.STROKE).let { draw ->
            SceneCommand.Draw(draw.node.copy(paint = draw.node.paint!!.copy(pathEffect = PathEffectNode.Corner(Float.NaN))))
        }
        val result = compiler.select(sceneOf(listOf(command)), target(sceneOf(listOf(command))))

        assertIs<GpuPlanSelection.InvalidScene>(result)
    }

    @Test
    fun unsupportedCoverageInverseTransformClipEffectBlendAndMaterialAreAtomicGaps() {
        val base = pathDraw(PaintStyleNode.STROKE).node
        val inverse = GeometryNode.Path(PathBuilder(org.graphiks.math.geometry.FillRule.INVERSE_WINDING)
            .moveTo(2f, 2f).lineTo(12f, 2f).lineTo(2f, 12f).close().build())
        val rejected = listOf(
            base.copy(coverage = CoverageRequest.ANTIALIASED),
            base.copy(geometry = inverse),
            base.copy(transform = Matrix3x3F32.rotation(0.25f)),
            base.copy(clip = ClipStackNode.Operations.of(listOf(ClipEntry(base.geometry, ClipOperation.INTERSECT)))),
            base.copy(paint = base.paint!!.copy(pathEffect = PathEffectNode.Corner(1f))),
            base.copy(blend = BlendNode.Mode(BlendMode.SRC)),
            base.copy(material = MaterialNode.Transparent),
        )

        rejected.forEach { node ->
            val scene = sceneOf(listOf(SceneCommand.Draw(node)))
            assertIs<GpuPlanSelection.NotCandidate>(compiler.select(scene, target(scene)))
        }
    }

    @Test
    fun wideExactI32ClipIntersectsTheTargetWithoutOverflow() {
        val command = pathDraw(PaintStyleNode.STROKE).let { draw ->
            val path = PathBuilder().moveTo(-10f, -10f).lineTo(10f, -10f).lineTo(-10f, 10f).close().build()
            SceneCommand.Draw(draw.node.copy(geometry = GeometryNode.Path(path), clip = ClipStackNode.DeviceRect.of(
                org.graphiks.math.geometry.RectF32(-2_147_483_648f, 0f, 4f, 3f), false,
            )))
        }
        val scene = sceneOf(listOf(command))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, capabilities(), PlanBudget(1L shl 20)),
        ).plan
        val draw = assertIs<PathStrokeDraw>(assertIs<PlanPass.StencilProducer>(graph.passes().first()).draw)

        assertEquals(org.graphiks.math.geometry.RectI32(0, 0, 4, 3), draw.copyScissorI32())
    }

    @Test
    fun compilerSealsEveryPublicStrokeStyleAxis() {
        val caps = StrokeCapNode.entries
        val joins = StrokeJoinNode.entries
        caps.forEach { cap -> joins.forEach { join ->
            val scene = sceneOf(listOf(pathDraw(PaintStyleNode.STROKE, 2f, cap, join, 0.5f,
                PathEffectNode.Dash(ImmutableFloats.copyOf(floatArrayOf(2f, 1f)), -1f))))
            val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
            val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(compiler.plan(candidate, capabilities(), PlanBudget(1L shl 20))).plan
            val draw = graph.passes().mapNotNull { pass -> when (pass) {
                is PlanPass.RenderPass -> pass.draws().singleOrNull()
                is PlanPass.StencilProducer -> pass.draw
                else -> null
            } }.filterIsInstance<PathStrokeDraw>().single()
            assertEquals(cap.name.lowercase().replaceFirstChar(Char::uppercase), draw.styleF64.cap.name)
            assertEquals(join.name.lowercase().replaceFirstChar(Char::uppercase), draw.styleF64.join.name)
            assertEquals(0.5, draw.styleF64.miterLimitF64)
            assertEquals(2, draw.styleF64.dashF64!!.intervalCountI32)
        } }
        val hairlineScene = sceneOf(listOf(pathDraw(PaintStyleNode.STROKE, 0f)))
        val hairline = assertIs<GpuPlanSelection.Candidate>(compiler.select(hairlineScene, target(hairlineScene))).candidate
        val hairlineGraph = assertIs<RenderPlanResult.Ready<RenderGraph>>(compiler.plan(hairline, capabilities(), PlanBudget(1L shl 20))).plan
        val draw = hairlineGraph.passes().filterIsInstance<PlanPass.StencilProducer>().first().draw
        assertIs<PathStrokeWidthF64.Hairline>(assertIs<PathStrokeDraw>(draw).styleF64.widthF64)
    }

    @Test
    fun compilerEmitsOrderedMixedGraphWithExactlyOneStencilResource() {
        val scene = sceneOf(listOf(pathDraw(PaintStyleNode.FILL), pathDraw(PaintStyleNode.STROKE)))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(compiler.plan(candidate, capabilities(), PlanBudget(1L shl 20))).plan

        assertEquals(1, graph.resources().count { it.role == PlanResourceRole.DepthStencil })
        assertIs<PlanPass.RenderPass>(graph.passes().first())
        assertIs<PlanPass.StencilProducer>(graph.passes()[1])
        assertIs<PlanPass.StencilCover>(graph.passes()[2])
        assertEquals(graph.passes().lastIndex + 1, graph.resources().first { it.role == PlanResourceRole.DepthStencil }.lastPassIndexExclusive)
    }

    @Test
    fun compilerCapabilityPlanIdentityAndCandidateOwnershipAreTerminal() {
        val scene = sceneOf(listOf(pathDraw(PaintStyleNode.STROKE)))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val base = capabilities()
        val ready = assertIs<RenderPlanResult.Ready<RenderGraph>>(compiler.plan(candidate, base, PlanBudget(1L shl 20))).plan
        val changed = assertIs<RenderPlanResult.Ready<RenderGraph>>(compiler.plan(candidate, capabilities(deviceGeneration = 1), PlanBudget(1L shl 20))).plan
        assertNotEquals(ready.id, changed.id)
        assertIs<RenderPlanResult.GapOnPromotedScope>(compiler.plan(candidate, capabilities(operations = setOf(PlanOperationCapability.Readback)), PlanBudget(1L shl 20)))
        val foreign = W4dPathStrokePlanCompiler()
        assertIs<RenderPlanResult.InvalidScene>(foreign.plan(candidate, base, PlanBudget(1L shl 20)))
        val chain = CapabilityCompilerChain.of(listOf(W4cPathFillPlanCompiler(), compiler))
        val chainCandidate = assertIs<GpuPlanSelection.Candidate>(chain.select(scene, target(scene))).candidate
        assertIs<RenderPlanResult.GapOnPromotedScope>(chain.plan(chainCandidate, capabilities(operations = setOf(PlanOperationCapability.Readback)), PlanBudget(1L shl 20)))
    }

    @Test
    fun emptyStrokeIsCountedButDoesNotManufactureAGraphDraw() {
        val emptyPath = PathBuilder().moveTo(1f, 1f).build()
        val empty = pathDraw(PaintStyleNode.STROKE).let { SceneCommand.Draw(it.node.copy(geometry = GeometryNode.Path(emptyPath))) }
        val visible = pathDraw(PaintStyleNode.STROKE)
        val scene = sceneOf(listOf(empty, visible))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(compiler.plan(candidate, capabilities(), PlanBudget(1L shl 20))).plan

        assertEquals(1, graph.visualCommandCount)
        assertEquals(1, graph.passes().filterIsInstance<PlanPass.StencilProducer>().size)
    }

    @Test
    fun mixedFillThenStrokeUsesOneCumulativeLedgerAtEveryFrameBudgetBoundary() {
        val scene = sceneOf(listOf(pathDraw(PaintStyleNode.FILL), pathDraw(PaintStyleNode.STROKE)))
        FrameAxis.entries.forEach { axis ->
            val required = firstCandidateFrameLimit(scene, axis)
            val ready = compilerWithFrameLimit(axis, required).select(scene, target(scene))
            val refused = compilerWithFrameLimit(axis, required - 1L).select(scene, target(scene))

            assertIs<GpuPlanSelection.Candidate>(ready, "${axis.name} at its exact mixed fill→stroke limit")
            assertEquals(
                W4dPlanDiagnostics.PathResourceLimit.value,
                assertIs<GpuPlanSelection.ResourceLimitExceeded>(refused, "${axis.name} one unit below the mixed limit")
                    .diagnostics().single().code.value,
            )
        }
    }

    @Test
    fun compilerAppliesWindingStencilEdgeBoundaryWithoutCappingEvenOdd() {
        val winding255 = sceneOf(listOf(pathDraw(PaintStyleNode.FILL, path = multiContourStencilPath(255)), pathDraw(PaintStyleNode.STROKE)))
        val winding256 = sceneOf(listOf(pathDraw(PaintStyleNode.FILL, path = multiContourStencilPath(256)), pathDraw(PaintStyleNode.STROKE)))
        val evenOdd256 = sceneOf(listOf(pathDraw(PaintStyleNode.FILL, path = multiContourStencilPath(256, org.graphiks.math.geometry.FillRule.EVEN_ODD)), pathDraw(PaintStyleNode.STROKE)))

        assertIs<GpuPlanSelection.Candidate>(compiler.select(winding255, target(winding255)))
        assertEquals(
            W4dPlanDiagnostics.PathResourceLimit.value,
            assertIs<GpuPlanSelection.ResourceLimitExceeded>(compiler.select(winding256, target(winding256))).diagnostics().single().code.value,
        )
        assertIs<GpuPlanSelection.Candidate>(compiler.select(evenOdd256, target(evenOdd256)))
    }

    private fun sceneOf(draws: List<SceneCommand.Draw>): SceneSnapshot = SceneSnapshot.of(
        SceneExtent(16, 16), ColorSpace.SRGB, draws,
    )

    private fun target(scene: SceneSnapshot): RenderTargetDescriptor =
        RenderTargetDescriptor(scene.extent, scene.colorSpace)

    private fun capabilities(deviceGeneration: Long = 0, operations: Set<PlanOperationCapability> = PlanOperationCapability.entries.toSet()): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = deviceGeneration,
        maxTextureDimension2D = 64,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = operations,
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096),
        supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
    )

    private fun pathDraw(style: PaintStyleNode, width: Float = 2f, cap: StrokeCapNode = StrokeCapNode.BUTT, join: StrokeJoinNode = StrokeJoinNode.MITER, miter: Float = 4f, effect: PathEffectNode? = null, path: PathF32 = trianglePath()): SceneCommand.Draw {
        val color = ColorARGB.fromPackedUInt(0xFFFF0000u)
        val paint = PaintNode(
            color, null, BlendMode.SRC_OVER, null, null, null, null, null,
            style, width, cap, join, miter, false,
        )
        return SceneCommand.Draw(DrawNode(
            GeometryNode.Path(path), MaterialNode.Solid(color), CoverageRequest.HARD_EDGE,
            ClipStackNode.Empty, BlendNode.SrcOver, EffectStack.Empty, Matrix3x3F32.Identity,
            DrawOrigin.PATH, paint.copy(pathEffect = effect),
        ))
    }

    private fun trianglePath(): PathF32 = PathBuilder().moveTo(2f, 2f).lineTo(12f, 2f).lineTo(2f, 12f).close().build()

    private fun multiContourStencilPath(edgeCount: Int, fillRule: org.graphiks.math.geometry.FillRule = org.graphiks.math.geometry.FillRule.WINDING): PathF32 {
        require(edgeCount == 255 || edgeCount == 256)
        return PathBuilder(fillRule).also { builder ->
            repeat(62) { index ->
                val left = index * 0.2f
                builder.addRect(org.graphiks.math.geometry.RectF32(left, 1f, left + 0.1f, 2f))
            }
            builder.moveTo(13f, 8f).lineTo(15f, 8f).lineTo(15f, 10f)
            if (edgeCount == 255) {
                builder.lineTo(14f, 10f).lineTo(14f, 9f).lineTo(13.5f, 9f).lineTo(13f, 10f)
            } else {
                builder.lineTo(14.5f, 10f).lineTo(14.5f, 9f).lineTo(14f, 9f).lineTo(14f, 10f).lineTo(13f, 10f)
            }
            builder.close()
        }.build()
    }

    private fun firstCandidateFrameLimit(scene: SceneSnapshot, axis: FrameAxis): Long {
        var low = 1L
        var high = 1L
        while (compilerWithFrameLimit(axis, high).select(scene, target(scene)) !is GpuPlanSelection.Candidate) high *= 2L
        while (low < high) {
            val middle = low + (high - low) / 2L
            if (compilerWithFrameLimit(axis, middle).select(scene, target(scene)) is GpuPlanSelection.Candidate) high = middle else low = middle + 1L
        }
        return low
    }

    private fun compilerWithFrameLimit(axis: FrameAxis, limit: Long): W4dPathStrokePlanCompiler {
        require(limit > 0L)
        val baseI32 = PathStrokeLimitsI32()
        val baseI64 = PathStrokeLimitsI64()
        val limitsI32 = when (axis) {
            FrameAxis.Attempted -> baseI32.copy(maxAttemptedGeometryUnitsPerFrameI32 = limit.toInt())
            FrameAxis.Vertices -> baseI32.copy(maxEmittedVertexCountPerFrameI32 = limit.toInt())
            FrameAxis.Indices -> baseI32.copy(maxEmittedIndexCountPerFrameI32 = limit.toInt())
            FrameAxis.Bytes -> baseI32
        }
        val limitsI64 = if (axis == FrameAxis.Bytes) baseI64.copy(maxSnapshotByteCountPerFrameI64 = limit) else baseI64
        return W4dPathStrokePlanCompiler(PathStrokePolicyF64(limitsI32 = limitsI32, limitsI64 = limitsI64))
    }

    private enum class FrameAxis { Attempted, Vertices, Indices, Bytes }
}
