package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.*
import org.graphiks.math.geometry.RectF32

private class W5aCompositeBudgetExceeded : RuntimeException()

/** A separate capability: ordered native lanes, one material table and one target lifetime. */
public class W5aCompositePlanCompiler : GpuPlanCompiler {
    private class Lane(val compiler: GpuPlanCompiler, val candidate: GpuPlanCandidate)
    private class Candidate(
        val owner: W5aCompositePlanCompiler,
        override val sceneCanonicalId: CanonicalId,
        override val target: RenderTargetDescriptor,
        val lanes: List<Lane>,
    ) : GpuPlanCandidate { override val capabilityId: String = CAPABILITY_ID }

    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection {
        val commands = scene.toList()
        val draws = commands.withIndex().filter { it.value is SceneCommand.Draw }
        fun kind(command: SceneCommand): Int? = when ((command as? SceneCommand.Draw)?.node?.geometry) {
            is GeometryNode.Rect -> 0
            is GeometryNode.RRect -> 1
            is GeometryNode.Path -> if (command.node.paint?.style == PaintStyleNode.FILL) 2 else 3
            else -> null
        }
        if (draws.isEmpty() || draws.any { kind(it.value) == null } ||
            draws.map { kind(it.value) }.distinct().size < 2 ||
            commands.any { it !is SceneCommand.Draw && it !is SceneCommand.Annotation &&
                it !is SceneCommand.SetTransform && it !is SceneCommand.SetClip }
        ) return GpuPlanSelection.NotCandidate(listOf(diagnostic("Scene is outside the native mixed Rect/RRect/Path capability")))
        val runs = mutableListOf<MutableList<IndexedValue<SceneCommand>>>()
        draws.forEach { draw ->
            if (runs.lastOrNull()?.lastOrNull()?.let { kind(it.value) } != kind(draw.value)) runs += mutableListOf<IndexedValue<SceneCommand>>()
            runs.last() += draw
        }
        if (runs.size > MAX_LANES_I32) return GpuPlanSelection.ResourceLimitExceeded(listOf(
            RenderDiagnostic(RenderDiagnosticCode("w5a.composite.resource-limit"), RenderDiagnosticDomain.RESOURCE,
                RenderDiagnosticSeverity.ERROR, "Composite lane count exceeds its bound"),
        ))
        val lanes = mutableListOf<Lane>()
        val materialRefusals = mutableListOf<EffectiveMaterialPlanner.Result.Refused>()
        for (run in runs) {
            val indices = run.map { it.index }.toSet()
            // Metadata placeholders retain the original command indices, never geometry conversions.
            val laneScene = SceneSnapshot.of(scene.extent, scene.colorSpace, commands.mapIndexed { index, command ->
                if (command is SceneCommand.Draw && index !in indices)
                    SceneCommand.Annotation.of(RectF32(0f, 0f, 0f, 0f), "w5a.omitted-draw", index.toString())
                else command
            })
            val compiler: GpuPlanCompiler = when (kind(run.first().value)) {
                0 -> W3SolidRectPlanCompiler()
                1 -> W4bAnalyticRRectPlanCompiler()
                2 -> W4cPathFillPlanCompiler()
                else -> W4dPathStrokePlanCompiler()
            }
            when (val selection = compiler.select(laneScene, target)) {
                is GpuPlanSelection.Candidate -> lanes += Lane(compiler, selection.candidate)
                is GpuPlanSelection.MaterialOnlyRefusal -> materialRefusals += selection.materialRefusals
                else -> return selection
            }
        }
        if (materialRefusals.isNotEmpty()) {
            return GpuPlanSelection.MaterialOnlyRefusal(CAPABILITY_ID, scene.canonicalId, target, materialRefusals)
        }
        return GpuPlanSelection.Candidate(Candidate(this, scene.canonicalId, target, lanes.toList()))
    }

    override fun plan(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): RenderPlanResult<RenderGraph> {
        val selected = candidate as? Candidate
        if (selected == null || selected.owner !== this) return RenderPlanResult.InvalidScene(listOf(diagnostic("Foreign composite candidate")))
        val graphs = mutableListOf<RenderGraph>()
        for (lane in selected.lanes) when (val result = lane.compiler.plan(lane.candidate, capabilities, budget)) {
            is RenderPlanResult.Ready -> graphs += result.plan
            else -> return result
        }
        return try {
            if (graphs.any { graph -> graph.verifyW5bGeometryCompilerWitness() || graph.passes().any {
                it is PlanPass.TextureCopy || it is PlanPass.RenderPass && it.draws().any { draw ->
                    draw.blend != BlendPlan.LegacySrcOverV1 && (draw.blend as? BlendPlan.FixedFunctionV1)?.mode != BlendMode.SRC_OVER
                }
            } }) RenderPlanResult.Ready(issueW5bNativeComposite(graphs))
            else RenderPlanResult.Ready(RenderGraph.issueW5aComposite(W5aCompositePlanV1.issue(graphs)))
        } catch (_: W5aCompositeBudgetExceeded) {
            RenderPlanResult.ResourceLimitExceeded(listOf(diagnostic("Composite frame exceeds its aggregate memory budget")))
        } catch (failure: IllegalArgumentException) {
            RenderPlanResult.GapOnPromotedScope(listOf(diagnostic(failure.message ?: "Invalid composite frame")))
        } catch (_: ArithmeticException) {
            RenderPlanResult.ResourceLimitExceeded(listOf(diagnostic("Composite memory footprint overflows I64")))
        }
    }

    public companion object {
        public const val CAPABILITY_ID: String = "w5a-native-rect-rrect-path-composite-v1"
        public const val MAX_LANES_I32: Int = 512
        private fun diagnostic(message: String): RenderDiagnostic = RenderDiagnostic(
            RenderDiagnosticCode("w5a.composite.unsupported"), RenderDiagnosticDomain.SCENE,
            RenderDiagnosticSeverity.ERROR, message,
        )
    }
}

/** Compiler-issued immutable composition. Each lane retains the exact standalone topology. */
public class W5aCompositePlanV1 private constructor(
    lanes: List<RenderGraph>,
    public val materialTable: MaterialPlanTable,
    public val peakFrameLocalBytesI64: Long,
    rectScratchBytes: List<List<Long>>,
) {
    private val storedLanes = immutableList(lanes)
    private val storedRectScratchBytes = immutableList(rectScratchBytes.map(::immutableList))
    public fun lanes(): List<RenderGraph> = storedLanes
    public fun rectScratchBytesI64(laneOrdinalI32: Int): List<Long> = storedRectScratchBytes[laneOrdinalI32]

    internal companion object {
        fun issue(graphs: List<RenderGraph>): W5aCompositePlanV1 {
            require(graphs.size in 2..W5aCompositePlanCompiler.MAX_LANES_I32)
            val first = graphs.first()
            require(graphs.all { it.capabilityId in setOf(W3SolidRectPlanCompiler.W5A_CAPABILITY_ID,
                W4bAnalyticRRectPlanCompiler.CAPABILITY_ID, W4cPathFillPlanCompiler.CAPABILITY_ID,
                W4dPathStrokePlanCompiler.CAPABILITY_ID) &&
                it.targetExtent == first.targetExtent && it.capabilities == first.capabilities &&
                it.budget == first.budget && it.colorFormat == first.colorFormat })
            val interned = MaterialPlanTable.intern(graphs.map { requireNotNull(it.materialPlanTableOrNull()) })
            val remapped = graphs.mapIndexed { lane, graph ->
                val copied = mutableMapOf<PlanDraw, PlanDraw>()
                fun draw(draw: PlanDraw): PlanDraw = copied.getOrPut(draw) {
                    val ref = interned.remap(lane, (draw.materialAuthority as PlanDrawMaterialAuthority.MaterialV1).ref)
                    when (draw) {
                        is SolidRectDraw -> draw.withMaterialRef(ref)
                        is AnalyticRRectDraw -> draw.withMaterialRef(ref)
                        is PathFillDraw -> draw.withMaterialRef(ref)
                        is PathStrokeDraw -> PathStrokeDraw.ofMaterial(draw.commandIndex, ref,
                            draw.copyGeometryF32(), draw.copyScissorI32(), draw.mode, draw.styleF64)
                        else -> error("Unrecognized native composite draw")
                    }
                }
                val passes = graph.passes().map { pass -> when (pass) {
                    is PlanPass.RenderPass -> PlanPass.RenderPass(pass.ordinal, pass.target, pass.draws().map(::draw), pass.load, pass.store, pass.drawDataResources)
                    is PlanPass.StencilProducer -> PlanPass.StencilProducer(pass.ordinal, pass.target, pass.depthStencil, draw(pass.draw) as PathDraw, pass.drawDataResources, pass.atomicGroup, pass.load, pass.store, pass.depthStencilAccess, pass.depthStencilLoadStore)
                    is PlanPass.StencilCover -> PlanPass.StencilCover(pass.ordinal, pass.target, pass.depthStencil, draw(pass.draw) as PathDraw, pass.drawDataResources, pass.atomicGroup, pass.load, pass.store, pass.depthStencilAccess, pass.depthStencilLoadStore)
                    is PlanPass.ReadbackPass -> pass
                    else -> error("Unrecognized native composite pass")
                } }
                val remapped = RenderGraph.of(graph.id, graph.capabilityId, graph.targetExtent, graph.colorFormat, graph.capabilities,
                    graph.budget, graph.visualCommandCount, graph.resources(), passes, graph.dependencies(),
                    graph.peakFrameLocalBytes, interned.table)
                if (graph.capabilityId == W4dPathStrokePlanCompiler.CAPABILITY_ID) {
                    require(graph.verifyW4dCompilerWitness())
                    RenderGraph.issueW4dCompilerWitness(remapped)
                } else remapped
            }
            val commandOrder = remapped.flatMap { graph -> graph.passes().flatMap { pass -> when (pass) {
                is PlanPass.RenderPass -> pass.draws().map { it.commandIndex }
                is PlanPass.StencilCover -> listOf(pass.draw.commandIndex)
                else -> emptyList()
            } } }
            require(commandOrder.zipWithNext().all { (a, b) -> a < b })
            val shared = first.resources().filter { it.role == PlanResourceRole.LogicalTarget || it.role == PlanResourceRole.ReadbackStaging }
                .sumOf { it.byteSize }
            val rectScratch = graphs.map { graph ->
                if (graph.capabilityId != W3SolidRectPlanCompiler.W5A_CAPABILITY_ID) emptyList() else {
                    val count = graph.visualCommandCount.toLong()
                    val alignment = graph.capabilities.minUniformBufferOffsetAlignment
                    require(alignment > 0)
                    val stride = Math.addExact(32L, (alignment - 32L % alignment) % alignment)
                    listOf(PlanScratchBufferKind.Vertex to Math.multiplyExact(count, 32L),
                        PlanScratchBufferKind.Index to Math.multiplyExact(count, 24L),
                        PlanScratchBufferKind.Uniform to Math.multiplyExact(count, stride)).map { (kind, useful) ->
                        requireNotNull(graph.capabilities.bufferAllocationPolicy.reserve(kind, useful)).also {
                            require(it <= graph.capabilities.maxBufferSizeBytes)
                        }
                    }
                }
            }
            val rectBytes = rectScratch.flatten().fold(0L, Math::addExact)
            val peak = graphs.fold(Math.addExact(shared, rectBytes)) { bytes, graph -> Math.addExact(bytes, graph.resources()
                .filter { it.role != PlanResourceRole.LogicalTarget && it.role != PlanResourceRole.ReadbackStaging }.sumOf { it.byteSize }) }
            if (peak > first.budget.maxFrameLocalBytes) throw W5aCompositeBudgetExceeded()
            return W5aCompositePlanV1(remapped, interned.table, peak, rectScratch)
        }
    }
}
