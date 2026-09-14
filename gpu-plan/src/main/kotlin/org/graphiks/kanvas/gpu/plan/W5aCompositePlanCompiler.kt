package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.*
import org.graphiks.math.geometry.RectF32

/** A separate capability: ordered native lanes, one material table and one target lifetime. */
public class W5aCompositePlanCompiler internal constructor(private val imageEntries: Map<Int, ImageConstructionEntryV1>) : GpuPlanCompiler {
    public constructor() : this(emptyMap())
    private enum class LaneKind { Rect, RRect, PathFill, PathStroke, Image }
    private data class LaneClassification(val kind: LaneKind, val geometryKindI32: Int)
    private class Lane(val compiler: GpuPlanCompiler, val candidate: GpuPlanCandidate)
    private class Candidate(
        val owner: W5aCompositePlanCompiler,
        override val sceneCanonicalId: CanonicalId,
        override val target: RenderTargetDescriptor,
        val lanes: List<Lane>,
    ) : GpuPlanCandidate { override val capabilityId: String = CAPABILITY_ID }

    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection {
        if (SceneSemanticValidator.validate(scene) is SceneSemanticValidationResult.Invalid)
            return GpuPlanSelection.InvalidScene(listOf(diagnostic("Composite scene metadata is invalid")))
        val commands = scene.toList()
        val draws = commands.withIndex().filter { it.value is SceneCommand.Draw }
        fun geometryKind(command: SceneCommand): Int? = when ((command as? SceneCommand.Draw)?.node?.geometry) {
            is GeometryNode.Rect -> 0
            is GeometryNode.RRect -> 1
            is GeometryNode.Path -> if (command.node.paint?.style == PaintStyleNode.FILL) 2 else 3
            else -> null
        }
        // Image is a distinct logical lane; its compiler-issued construction retains
        // the native Rect/Path topology. Source authority is sealed by the W5e bridge.
        fun kind(draw: IndexedValue<SceneCommand>): LaneClassification? = geometryKind(draw.value)?.let {
            LaneClassification(if (draw.index in imageEntries) LaneKind.Image else when (it) {
                0 -> LaneKind.Rect
                1 -> LaneKind.RRect
                2 -> LaneKind.PathFill
                else -> LaneKind.PathStroke
            }, it)
        }
        if (draws.isEmpty() || draws.any { kind(it) == null } ||
            draws.map { kind(it) }.distinct().size < 2 ||
            commands.any { it !is SceneCommand.Draw && it !is SceneCommand.Annotation &&
                it !is SceneCommand.SetTransform && it !is SceneCommand.SetClip }
        ) return GpuPlanSelection.NotCandidate(listOf(diagnostic("Scene is outside the native mixed Rect/RRect/Path capability")))
        val runs = mutableListOf<MutableList<IndexedValue<SceneCommand>>>()
        draws.forEach { draw ->
            if (runs.lastOrNull()?.lastOrNull()?.let { kind(it) } != kind(draw)) runs += mutableListOf<IndexedValue<SceneCommand>>()
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
                // Each DrawNode owns its complete transform/clip snapshot. Foreign metadata must
                // not narrow another lane; the whole scene was validated before this projection.
                if (command is SceneCommand.SetTransform || command is SceneCommand.SetClip ||
                    command is SceneCommand.Draw && index !in indices)
                    SceneCommand.Annotation.of(RectF32(0f, 0f, 0f, 0f), "w5a.omitted-draw", index.toString())
                else command
            })
            var compiler: GpuPlanCompiler = when (geometryKind(run.first().value)) {
                0 -> W3SolidRectPlanCompiler()
                1 -> W4bAnalyticRRectPlanCompiler()
                2 -> W4cPathFillPlanCompiler()
                else -> W4dPathStrokePlanCompiler()
            }
            var selection = compiler.select(laneScene, target)
            if (selection is GpuPlanSelection.NotCandidate && geometryKind(run.first().value) == 0) {
                compiler = W4aAnalyticRectPlanCompiler()
                selection = compiler.select(laneScene, target)
            }
            if (selection is GpuPlanSelection.NotCandidate && geometryKind(run.first().value) in setOf(2, 3) &&
                (run.any { it.index in imageEntries } || draws.any { when (val blend = (it.value as SceneCommand.Draw).node.blend) {
                    is BlendNode.Mode -> blend.mode != BlendMode.SRC_OVER
                    is BlendNode.Paint -> blend.blender == null && blend.mode != BlendMode.SRC_OVER
                    else -> false
                } })) {
                compiler = W4dGeneralPathPlanCompiler()
                selection = compiler.select(laneScene, target)
            }
            when (selection) {
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
        val graphs = mutableListOf<RenderGraphConstruction>()
        for (lane in selected.lanes) when (val result = lane.compiler.constructLane(lane.candidate, capabilities, budget)) {
            is RenderPlanResult.Ready -> graphs += result.plan
            else -> return result.publishConstructionResult()
        }
        return try {
            if (graphs.any { graph -> graph.verifyW5bGeometryCompilerWitness() || graph.capabilityId == W4dGeneralPathPlanCompiler.W5A_HARD_CAPABILITY_ID || graph.passes().any {
                it is PlanPass.TextureCopy || it is PlanPass.RenderPass && it.draws().any { draw ->
                    draw.blend != BlendPlan.LegacySrcOverV1 && (draw.blend as? BlendPlan.FixedFunctionV1)?.mode != BlendMode.SRC_OVER
                }
            } }) RenderPlanResult.Ready(issueW5bNativeComposite(graphs).publish())
            else RenderPlanResult.Ready(W5aCompositePlanV1.construct(graphs).publish())
        } catch (failure: RawMaterialRequirementsV2.Refusal) {
            RenderPlanResult.ResourceLimitExceeded(listOf(RenderDiagnostic(RenderDiagnosticCode(failure.code),
                RenderDiagnosticDomain.RESOURCE, RenderDiagnosticSeverity.ERROR, "Composite material frame exceeds its aggregate memory budget")))
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
        fun fromPublished(lanes: List<RenderGraph>, table: MaterialPlanTable, peakI64: Long,
            scratch: List<List<Long>>): W5aCompositePlanV1 = W5aCompositePlanV1(lanes,table,peakI64,scratch)
        fun construct(graphs: List<RenderGraphConstruction>): W5aCompositeConstruction {
            require(graphs.size in 2..W5aCompositePlanCompiler.MAX_LANES_I32)
            val first = graphs.first()
            require(graphs.all { it.capabilityId in setOf(W3SolidRectPlanCompiler.W5A_CAPABILITY_ID,
                W4bAnalyticRRectPlanCompiler.CAPABILITY_ID, W4cPathFillPlanCompiler.CAPABILITY_ID,
                W4dPathStrokePlanCompiler.CAPABILITY_ID) &&
                it.targetExtent == first.targetExtent && it.capabilities == first.capabilities &&
                it.budget == first.budget && it.colorFormat == first.colorFormat })
            val interned = MaterialPlanTable.intern(graphs.map { requireNotNull(it.materialPlanTableOrNull()) })
            val remapped = graphs.mapIndexed { lane, graph ->
                graph.rebindMaterials(interned.table) { interned.remap(lane,it) }
            }
            val commandOrder = remapped.flatMap { graph -> graph.passes().flatMap { pass -> when (pass) {
                is PlanPass.RenderPass -> pass.draws().map { it.commandIndex }
                is PlanPass.StencilCover -> listOf(pass.draw.commandIndex)
                else -> emptyList()
            } } }
            require(commandOrder.zipWithNext().all { (a, b) -> a < b })
            val shared = first.resources().filter { it.role == PlanResourceRole.LogicalTarget || it.role == PlanResourceRole.ReadbackStaging }
                .fold(0L) { bytesI64, resource -> Math.addExact(bytesI64, resource.byteSize) }
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
            // Final physical inventory: target/readback once, geometry per lane,
            // the interned stop slab once, and Raw allocations unique by value.
            val stopBytesI64 = interned.table.gradientStopSlab?.byteSizeI64 ?: 0L
            val peak = graphs.fold(Math.addExact(Math.addExact(shared, rectBytes), stopBytesI64)) { bytes, graph ->
                graph.resources().filter { it.role != PlanResourceRole.LogicalTarget &&
                    it.role != PlanResourceRole.ReadbackStaging && it.role != PlanResourceRole.GradientStopData }
                    .fold(bytes) { bytesI64, resource -> Math.addExact(bytesI64, resource.byteSize) }
            }
            // Kept non-uniform for the native lowerer's independent lifetime check;
            // material uniforms are admitted above and owned by its existing stages.
            return W5aCompositeConstruction(remapped, interned.table, peak, rectScratch)
        }
    }
}

internal class W5aCompositeConstruction(lanes: List<RenderGraphConstruction>, val table: MaterialPlanTable,
    val peakI64: Long, scratch: List<List<Long>>) {
    private val lanes = immutableList(lanes)
    private val scratch = immutableList(scratch.map(::immutableList))
    fun publish(): RenderGraph {
        val packed = packConstructedFrame(lanes,table,peakI64)
        val published = lanes.map { RenderGraph.publishConstruction(it,packed) }
        return RenderGraph.issueW5aComposite(W5aCompositePlanV1.fromPublished(published,table,peakI64,scratch))
    }
}

private fun GpuPlanCompiler.constructLane(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot,
    budget: PlanBudget): RenderPlanResult<RenderGraphConstruction> = when (this) {
    is W3SolidRectPlanCompiler -> construct(candidate,capabilities,budget)
    is W4aAnalyticRectPlanCompiler -> construct(candidate,capabilities,budget)
    is W4bAnalyticRRectPlanCompiler -> construct(candidate,capabilities,budget)
    is W4cPathFillPlanCompiler -> construct(candidate,capabilities,budget)
    is W4dPathStrokePlanCompiler -> construct(candidate,capabilities,budget)
    is W4dGeneralPathPlanCompiler -> construct(candidate,capabilities,budget)
    else -> error("Composite lane has no compiler-owned construction entry point")
}
