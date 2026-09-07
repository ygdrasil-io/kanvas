package org.graphiks.kanvas.gpu.plan

import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
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
import org.graphiks.math.geometry.PathFillInputF64
import org.graphiks.math.geometry.PathFillWithStrokeWorkPreparationResult
import org.graphiks.math.geometry.PathStrokeCap
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeJoin
import org.graphiks.math.geometry.PathStrokeLimitsI32
import org.graphiks.math.geometry.PathStrokeLimitsI64
import org.graphiks.math.geometry.PathStrokePolicyF64
import org.graphiks.math.geometry.PathStrokePreparationResult
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.math.geometry.PathStrokeWorkUsageI64
import org.graphiks.math.geometry.prepareMappedPathFillGeometryWithStrokeWorkF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.pathStrokeDeviceFillSegmentMapperF64
import org.graphiks.math.matrix.preparePathStrokeGeometryF32
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
        assertTrue(graph.verifyW4dCompilerWitness())
        assertIs<PathStrokeDraw>(assertIs<PlanPass.StencilProducer>(graph.passes().first()).draw)
    }

    @Test
    fun compilerWitnessIsStableDefensiveAndAbsentFromPublicGraphReconstruction() {
        val scene = sceneOf(
            listOf(
                pathDraw(
                    PaintStyleNode.STROKE,
                    effect = PathEffectNode.Dash(ImmutableFloats.copyOf(floatArrayOf(3f, 1f)), -2f),
                ),
            ),
        )
        fun compile(): RenderGraph {
            val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
            return assertIs<RenderPlanResult.Ready<RenderGraph>>(
                compiler.plan(candidate, capabilities(), PlanBudget(1L shl 20)),
            ).plan
        }

        val graph = compile()
        val repeated = compile()
        assertEquals(graph.id, repeated.id)
        assertTrue(graph.verifyW4dCompilerWitness())
        assertTrue(repeated.verifyW4dCompilerWitness())

        val stroke = assertIs<PathStrokeDraw>(assertIs<PlanPass.StencilProducer>(graph.passes().first()).draw)
        val vertices = stroke.copyGeometryF32().copyFillGeometryF32()
            .copyStencilEdgeFanF32OrNull()!!.copyVerticesF32()
        val dash = stroke.styleF64.dashF64!!.copyIntervalsF64()
        vertices[0] = 99f
        dash[0] = 99.0
        assertTrue(graph.verifyW4dCompilerWitness())

        val reconstructed = RenderGraph.of(
            id = graph.id,
            capabilityId = graph.capabilityId,
            targetExtent = graph.targetExtent,
            colorFormat = graph.colorFormat,
            capabilities = graph.capabilities,
            budget = graph.budget,
            visualCommandCount = graph.visualCommandCount,
            resources = graph.resources(),
            passes = graph.passes(),
            dependencies = graph.dependencies(),
            peakFrameLocalBytes = graph.peakFrameLocalBytes,
        )
        assertFalse(reconstructed.verifyW4dCompilerWitness())
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
            assertEquals(2.0, assertIs<PathStrokeWidthF64.Finite>(draw.styleF64.widthF64).valueF64)
            assertEquals(0.5, draw.styleF64.miterLimitF64)
            assertEquals(2, draw.styleF64.dashF64!!.intervalCountI32)
            assertContentEquals(doubleArrayOf(2.0, 1.0), draw.styleF64.dashF64!!.copyIntervalsF64())
            assertEquals(-1.0, draw.styleF64.dashF64!!.phaseF64)
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
    fun compilerFailsClosedForEveryRequiredCapabilityAndExactBudgetBoundary() {
        val scene = sceneOf(listOf(pathDraw(PaintStyleNode.STROKE)))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val capsBase = capabilities()
        val baseline = assertIs<RenderPlanResult.Ready<RenderGraph>>(compiler.plan(candidate, capsBase, PlanBudget(1L shl 20))).plan
        assertIs<RenderPlanResult.Ready<RenderGraph>>(compiler.plan(candidate, capsBase, PlanBudget(baseline.peakFrameLocalBytes)))
        assertEquals(W4dPlanDiagnostics.BudgetFrameLocalExceeded.value, assertIs<RenderPlanResult.ResourceLimitExceeded>(compiler.plan(candidate, capsBase, PlanBudget(baseline.peakFrameLocalBytes - 1L))).diagnostics.single().code.value)

        listOf(
            mutateCapabilities(capsBase, maxTextureDimension2D = 8),
            mutateCapabilities(capsBase, maxBufferSizeBytes = 1L),
            mutateCapabilities(capsBase, supportedFormats = emptySet()),
            *setOf(PlanOperationCapability.RenderPass, PlanOperationCapability.CopyUpload, PlanOperationCapability.UniformBuffer, PlanOperationCapability.Readback, PlanOperationCapability.DepthStencilAttachment, PlanOperationCapability.StencilCover)
                .map { operation -> mutateCapabilities(capsBase, operations = capsBase.supportedOperations() - operation) }.toTypedArray(),
            mutateCapabilities(capsBase, depthStencilFormats = emptySet()),
            mutateCapabilities(capsBase, maxDynamicUniformBuffersPerPipelineLayout = 0),
            mutateCapabilities(capsBase, copyBytesPerRowAlignment = 3),
            mutateCapabilities(capsBase, minUniformBufferOffsetAlignment = 3),
            mutateCapabilities(capsBase, bufferAllocationPolicy = PlanBufferAllocationPolicy.of(3, capsBase.bufferAllocationPolicy.indexFloorBytes, capsBase.bufferAllocationPolicy.uniformFloorBytes)),
            mutateCapabilities(capsBase, bufferAllocationPolicy = PlanBufferAllocationPolicy.of(capsBase.bufferAllocationPolicy.vertexFloorBytes, 3, capsBase.bufferAllocationPolicy.uniformFloorBytes)),
            mutateCapabilities(capsBase, bufferAllocationPolicy = PlanBufferAllocationPolicy.of(capsBase.bufferAllocationPolicy.vertexFloorBytes, capsBase.bufferAllocationPolicy.indexFloorBytes, 3)),
        ).forEach { unavailable ->
            assertIs<RenderPlanResult.GapOnPromotedScope>(compiler.plan(candidate, unavailable, PlanBudget(1L shl 20)))
        }
    }

    @Test
    fun planIdIsStableAndIncludesEveryAdmittedW4dPolicyAndCapabilityFact() {
        val scene = sceneOf(listOf(pathDraw(PaintStyleNode.STROKE)))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val capsBase = capabilities()
        fun id(caps: PlanCapabilitySnapshot = capsBase, budget: PlanBudget = PlanBudget(1L shl 20)): String =
            assertIs<RenderPlanResult.Ready<RenderGraph>>(compiler.plan(candidate, caps, budget)).plan.id.value
        val stable = id()
        assertEquals(stable, id())
        listOf(
            mutateCapabilities(capsBase, deviceGeneration = 1),
            mutateCapabilities(capsBase, maxTextureDimension2D = 128),
            mutateCapabilities(capsBase, maxBufferSizeBytes = 2L shl 20),
            mutateCapabilities(capsBase, copyBytesPerRowAlignment = 512),
            mutateCapabilities(capsBase, minUniformBufferOffsetAlignment = 512),
            mutateCapabilities(capsBase, maxDynamicUniformBuffersPerPipelineLayout = 2),
            mutateCapabilities(capsBase, bufferAllocationPolicy = PlanBufferAllocationPolicy.of(32_768, capsBase.bufferAllocationPolicy.indexFloorBytes, capsBase.bufferAllocationPolicy.uniformFloorBytes)),
            mutateCapabilities(capsBase, bufferAllocationPolicy = PlanBufferAllocationPolicy.of(capsBase.bufferAllocationPolicy.vertexFloorBytes, 8_192, capsBase.bufferAllocationPolicy.uniformFloorBytes)),
            mutateCapabilities(capsBase, bufferAllocationPolicy = PlanBufferAllocationPolicy.of(capsBase.bufferAllocationPolicy.vertexFloorBytes, capsBase.bufferAllocationPolicy.indexFloorBytes, 8_192)),
        ).forEach { changed -> assertNotEquals(stable, id(changed)) }
        assertNotEquals(stable, id(budget = PlanBudget(2L shl 20)))

        val policyBase = generousPolicy()
        fun policyId(policy: PathStrokePolicyF64): String {
            val policyCompiler = W4dPathStrokePlanCompiler(policy)
            val policyCandidate = assertIs<GpuPlanSelection.Candidate>(policyCompiler.select(scene, target(scene))).candidate
            return assertIs<RenderPlanResult.Ready<RenderGraph>>(policyCompiler.plan(policyCandidate, capsBase, PlanBudget(1L shl 20))).plan.id.value
        }
        val policyBaseId = policyId(policyBase)
        assertEquals(policyBaseId, policyId(policyBase))
        listOf(
            policyBase.copy(maximumSagittaErrorF64 = 0.125),
            policyBase.copy(maximumDashArcLengthErrorF64 = 0.03125),
            policyBase.copy(limitsI32 = policyBase.limitsI32.copy(maxSubdivisionDepthI32 = 31)),
            policyBase.copy(limitsI32 = policyBase.limitsI32.copy(maxAttemptedGeometryUnitsPerPathI32 = 100_000)),
            policyBase.copy(limitsI32 = policyBase.limitsI32.copy(maxAttemptedGeometryUnitsPerFrameI32 = 100_000)),
            policyBase.copy(limitsI32 = policyBase.limitsI32.copy(maxEmittedVertexCountPerPathI32 = 100_000)),
            policyBase.copy(limitsI32 = policyBase.limitsI32.copy(maxEmittedVertexCountPerFrameI32 = 100_000)),
            policyBase.copy(limitsI32 = policyBase.limitsI32.copy(maxEmittedIndexCountPerPathI32 = 100_000)),
            policyBase.copy(limitsI32 = policyBase.limitsI32.copy(maxEmittedIndexCountPerFrameI32 = 100_000)),
            policyBase.copy(limitsI64 = policyBase.limitsI64.copy(maxSnapshotByteCountPerPathI64 = 1L shl 20)),
            policyBase.copy(limitsI64 = policyBase.limitsI64.copy(maxSnapshotByteCountPerFrameI64 = 2L shl 20)),
        ).forEach { policy -> assertNotEquals(policyBaseId, policyId(policy)) }
    }

    @Test
    fun compilerSealsDashInputsAndPublishesImmutableDiagnostics() {
        val mutableIntervals = floatArrayOf(2f, 1f)
        val draw = pathDraw(PaintStyleNode.STROKE, effect = PathEffectNode.Dash(ImmutableFloats.copyOf(mutableIntervals), -1f))
        mutableIntervals[0] = 99f
        val scene = sceneOf(listOf(draw))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(compiler.plan(candidate, capabilities(), PlanBudget(1L shl 20))).plan
        val sealed = assertIs<PathStrokeDraw>(assertIs<PlanPass.StencilProducer>(graph.passes().first()).draw)
        assertContentEquals(doubleArrayOf(2.0, 1.0), sealed.styleF64.dashF64!!.copyIntervalsF64())
        val diagnostic = assertIs<GpuPlanSelection.NotCandidate>(compiler.select(sceneOf(listOf(pathDraw(PaintStyleNode.FILL))), target(sceneOf(listOf(pathDraw(PaintStyleNode.FILL)))))).diagnostics()
        assertFailsWith<UnsupportedOperationException> { (diagnostic as MutableList).add(diagnostic.single()) }
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
        val expected = mixedExpectedWork(generousPolicy())
        FrameAxis.entries.forEach { axis ->
            val fill = axis.valueOf(expected.fillAfter)
            val stroke = axis.valueOf(expected.strokeAfter) - fill
            val required = axis.valueOf(expected.strokeAfter)
            val ready = compilerWithFrameLimit(axis, required).select(scene, target(scene))
            val refused = compilerWithFrameLimit(axis, required - 1L).select(scene, target(scene))

            assertEquals(true, fill > 0L, "fill must contribute ${axis.name}")
            assertEquals(true, stroke > 0L, "stroke must contribute ${axis.name}")
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

    private fun capabilities(deviceGeneration: Long = 0, operations: Set<PlanOperationCapability> = PlanOperationCapability.entries.toSet(), maxTextureDimension2D: Int = 64, maxBufferSizeBytes: Long = 1L shl 20, copyBytesPerRowAlignment: Int = 256, supportedFormats: Set<PlanLogicalColorFormat> = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), minUniformBufferOffsetAlignment: Int = 256, bufferAllocationPolicy: PlanBufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096), depthStencilFormats: Set<PlanDepthStencilFormat> = setOf(PlanDepthStencilFormat.Depth24PlusStencil8)): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = deviceGeneration,
        maxTextureDimension2D = maxTextureDimension2D,
        maxBufferSizeBytes = maxBufferSizeBytes,
        copyBytesPerRowAlignment = copyBytesPerRowAlignment,
        supportedFormats = supportedFormats,
        minUniformBufferOffsetAlignment = minUniformBufferOffsetAlignment,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = operations,
        bufferAllocationPolicy = bufferAllocationPolicy,
        supportedDepthStencilFormats = depthStencilFormats,
    )

    private fun mutateCapabilities(
        base: PlanCapabilitySnapshot,
        deviceGeneration: Long = base.deviceGeneration,
        maxTextureDimension2D: Int = base.maxTextureDimension2D,
        maxBufferSizeBytes: Long = base.maxBufferSizeBytes,
        copyBytesPerRowAlignment: Int = base.copyBytesPerRowAlignment,
        supportedFormats: Set<PlanLogicalColorFormat> = base.supportedFormats(),
        minUniformBufferOffsetAlignment: Int = base.minUniformBufferOffsetAlignment,
        maxDynamicUniformBuffersPerPipelineLayout: Int = base.maxDynamicUniformBuffersPerPipelineLayout,
        operations: Set<PlanOperationCapability> = base.supportedOperations(),
        bufferAllocationPolicy: PlanBufferAllocationPolicy = base.bufferAllocationPolicy,
        depthStencilFormats: Set<PlanDepthStencilFormat> = base.supportedDepthStencilFormats(),
    ): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration, maxTextureDimension2D, maxBufferSizeBytes, copyBytesPerRowAlignment, supportedFormats,
        minUniformBufferOffsetAlignment, maxDynamicUniformBuffersPerPipelineLayout, operations,
        bufferAllocationPolicy, depthStencilFormats,
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

    private fun compilerWithFrameLimit(axis: FrameAxis, limit: Long): W4dPathStrokePlanCompiler {
        require(limit > 0L)
        val baseI32 = PathStrokeLimitsI32(
            maxAttemptedGeometryUnitsPerFrameI32 = Int.MAX_VALUE,
            maxEmittedVertexCountPerFrameI32 = Int.MAX_VALUE,
            maxEmittedIndexCountPerFrameI32 = Int.MAX_VALUE,
        )
        val baseI64 = PathStrokeLimitsI64(maxSnapshotByteCountPerFrameI64 = Long.MAX_VALUE)
        val limitsI32 = when (axis) {
            FrameAxis.Attempted -> baseI32.copy(maxAttemptedGeometryUnitsPerFrameI32 = limit.toInt())
            FrameAxis.Vertices -> baseI32.copy(maxEmittedVertexCountPerFrameI32 = limit.toInt())
            FrameAxis.Indices -> baseI32.copy(maxEmittedIndexCountPerFrameI32 = limit.toInt())
            FrameAxis.Bytes -> baseI32
        }
        val limitsI64 = if (axis == FrameAxis.Bytes) baseI64.copy(maxSnapshotByteCountPerFrameI64 = limit) else baseI64
        return W4dPathStrokePlanCompiler(PathStrokePolicyF64(limitsI32 = limitsI32, limitsI64 = limitsI64))
    }

    private fun generousPolicy(): PathStrokePolicyF64 = PathStrokePolicyF64(
        limitsI32 = PathStrokeLimitsI32(
            maxAttemptedGeometryUnitsPerFrameI32 = Int.MAX_VALUE,
            maxEmittedVertexCountPerFrameI32 = Int.MAX_VALUE,
            maxEmittedIndexCountPerFrameI32 = Int.MAX_VALUE,
        ),
        limitsI64 = PathStrokeLimitsI64(maxSnapshotByteCountPerFrameI64 = Long.MAX_VALUE),
    )

    private fun mixedExpectedWork(policy: PathStrokePolicyF64): MixedWork {
        val fill = assertIs<PathFillWithStrokeWorkPreparationResult.Ready>(
            prepareMappedPathFillGeometryWithStrokeWorkF32(
                PathFillInputF64.fromPathF32(trianglePath()),
                Matrix3x3F32.Identity.pathStrokeDeviceFillSegmentMapperF64(),
                strokePolicyF64 = policy,
            ),
        )
        val stroke = assertIs<PathStrokePreparationResult.Ready>(
            Matrix3x3F32.Identity.preparePathStrokeGeometryF32(
                trianglePath(),
                PathStrokeStyleF64(PathStrokeWidthF64.Finite(2.0), PathStrokeCap.Butt, PathStrokeJoin.Miter, 4.0),
                PathStrokeDrawMode.Stroke,
                policyF64 = policy,
                frameWorkUsageBeforeI64 = fill.frameWorkUsageAfterI64,
            ),
        )
        return MixedWork(fill.frameWorkUsageAfterI64, stroke.frameWorkUsageAfterI64)
    }

    private data class MixedWork(val fillAfter: PathStrokeWorkUsageI64, val strokeAfter: PathStrokeWorkUsageI64)

    private enum class FrameAxis {
        Attempted { override fun valueOf(work: PathStrokeWorkUsageI64): Long = work.attemptedGeometryUnitCountI64 },
        Vertices { override fun valueOf(work: PathStrokeWorkUsageI64): Long = work.emittedVertexCountI64 },
        Indices { override fun valueOf(work: PathStrokeWorkUsageI64): Long = work.emittedIndexCountI64 },
        Bytes { override fun valueOf(work: PathStrokeWorkUsageI64): Long = work.snapshotByteCountI64 },
        ;

        abstract fun valueOf(work: PathStrokeWorkUsageI64): Long
    }
}
