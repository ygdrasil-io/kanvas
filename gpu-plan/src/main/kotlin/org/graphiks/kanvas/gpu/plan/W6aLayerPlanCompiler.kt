package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.LayerDescriptor
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneSnapshot

/**
 * First layer authority. It recognizes every layer boundary before any child capability or
 * target-format decision, then freezes occurrence metadata before asking the existing W5 source
 * authority to lower the transparent/SRC_OVER subset.
 */
public class W6aLayerPlanCompiler public constructor(
    private val runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot,
) : GpuPlanCompiler {
    public constructor() : this(RuntimeEffectSemanticCatalogSnapshot.Unbound)

    internal data class ScopeOccurrence(
        val idI32: Int,
        val beginCommandIndexI32: Int,
        val endCommandIndexI32: Int,
        val descriptor: LayerDescriptor,
    )

    private class Candidate(
        val owner: W6aLayerPlanCompiler,
        override val sceneCanonicalId: org.graphiks.kanvas.render.ir.CanonicalId,
        override val target: RenderTargetDescriptor,
        val occurrences: List<ScopeOccurrence>,
        val segments: List<Segment>,
    ) : GpuPlanCandidate {
        override val capabilityId: String = CAPABILITY_ID
    }

    private class Segment(val scopeI32: Int?, val compiler: CapabilityCompilerChain, val candidate: GpuPlanCandidate)

    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection {
        val commands = scene.toList()
        if (commands.none { it is SceneCommand.BeginLayer || it is SceneCommand.EndLayer }) {
            return GpuPlanSelection.NotCandidate(listOf(diagnostic(W6aPlanDiagnostics.UnsupportedChild, "Scene has no layer boundary.")))
        }
        if (scene.extent != target.extent || scene.colorSpace != target.colorSpace) {
            return invalid(W6aPlanDiagnostics.UnsupportedChild, "Scene and target descriptors disagree.")
        }

        val scopes = mutableListOf<ScopeOccurrence>()
        var open: ScopeOccurrence? = null
        commands.forEachIndexed { indexI32, command ->
            when (command) {
                is SceneCommand.BeginLayer -> {
                    if (open != null) return invalid(
                        W6aPlanDiagnostics.UnsupportedNestedScope,
                        "W6a accepts one active layer scope at a time.",
                    )
                    refusalFor(command.descriptor)?.let { (code, message) -> return invalid(code, message) }
                    open = ScopeOccurrence(scopes.size, indexI32, -1, command.descriptor)
                }
                SceneCommand.EndLayer -> {
                    val begun = open ?: return invalid(
                        W6aPlanDiagnostics.MalformedStack,
                        "EndLayer has no matching BeginLayer.",
                    )
                    scopes += begun.copy(endCommandIndexI32 = indexI32)
                    open = null
                }
                is SceneCommand.Draw -> {
                    val paint = command.node.paint
                    if (open?.descriptor?.compositeClip?.let { it !is org.graphiks.kanvas.render.ir.ClipStackNode.Empty } == true &&
                        command.node.clip !is org.graphiks.kanvas.render.ir.ClipStackNode.Empty)
                        return invalid(W6aPlanDiagnostics.UnsupportedChild, "Combined child/restore clips await the bounded clip lane.")
                    if (paint?.imageFilter != null || paint?.maskFilter != null || command.node.effects !is EffectStack.Empty) {
                        return invalid(
                            W6aPlanDiagnostics.UnsupportedSpatialFilter,
                            "W6a does not admit image or mask filters in a layer frame.",
                        )
                    }
                    if (!finite(command.node.transform)) return invalid(
                        W6aPlanDiagnostics.NonFiniteTransform,
                        "A layer child transform is non-finite.",
                    )
                }
                is SceneCommand.SetTransform -> if (!finite(command.matrix)) return invalid(
                    W6aPlanDiagnostics.NonFiniteTransform,
                    "A layer transform is non-finite.",
                )
                is SceneCommand.SetClip, is SceneCommand.Annotation -> Unit
                else -> return invalid(W6aPlanDiagnostics.UnsupportedChild, "This layer-frame command has no admitted source lane.")
            }
        }
        if (open != null) return invalid(W6aPlanDiagnostics.MalformedStack, "BeginLayer has no matching EndLayer.")
        if (scopes.isEmpty()) return invalid(W6aPlanDiagnostics.MalformedStack, "Layer markers did not form a scope.")

        val segments = mutableListOf<Segment>()
        val boundaries = listOf(-1) + scopes.flatMap { listOf(it.beginCommandIndexI32, it.endCommandIndexI32) } + commands.size
        for ((before, after) in boundaries.zipWithNext()) {
            val occurrence = scopes.singleOrNull { it.beginCommandIndexI32 == before }
            val draws = (before + 1 until after).filter { commands[it] is SceneCommand.Draw }.toSet()
            if (draws.isEmpty()) continue
            val segment = SceneSnapshot.of(scene.extent, scene.colorSpace, commands.mapIndexed { index, command ->
                if (index in draws) {
                    command as SceneCommand.Draw
                    val clip = occurrence?.descriptor?.compositeClip
                    if (clip != null && command.node.clip is org.graphiks.kanvas.render.ir.ClipStackNode.Empty)
                        SceneCommand.Draw(command.node.copy(clip = clip)) else command
                } else SceneCommand.Annotation.of(org.graphiks.math.geometry.RectF32(0f, 0f, 0f, 0f), "w6a.segment", index.toString())
            })
            val child = CapabilityCompilerChain.of(listOf(W3SolidRectPlanCompiler()), runtimeCatalog)
            when (val selection = child.select(segment, target)) {
                is GpuPlanSelection.Candidate -> segments += Segment(occurrence?.idI32, child, selection.candidate)
                else -> return invalid(W6aPlanDiagnostics.UnsupportedChild, "Layer segment is outside the admitted child geometry/source lanes.")
            }
        }
        return GpuPlanSelection.Candidate(Candidate(this, scene.canonicalId, target, scopes.toList(), segments.toList()))
    }

    override fun plan(
        candidate: GpuPlanCandidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraph> {
        val selected = candidate as? Candidate
        if (selected == null || selected.owner !== this) {
            return RenderPlanResult.InvalidScene(listOf(diagnostic(W6aPlanDiagnostics.UnsupportedChild, "Foreign W6a candidate.")))
        }
        return try {
            val bindings = mutableListOf<W6aLayerSourceBinding>()
            for (segment in selected.segments) when (val result = segment.compiler.constructSourceLanes(segment.candidate, capabilities, budget)) {
                is RenderPlanResult.Ready -> result.plan.forEach { bindings += W6aLayerSourceBinding(segment.scopeI32, it) }
                is RenderPlanResult.ResourceLimitExceeded -> return W6aLayerPlanBudget.translate(result)
                is RenderPlanResult.GapNotMigrated -> return result
                is RenderPlanResult.GapOnPromotedScope -> return result
                is RenderPlanResult.InvalidScene -> return result
            }
            val frame = W6aLayerGraphConstruction(PlanId("w6a.${selected.sceneCanonicalId.value}"), org.graphiks.math.geometry.SizeI32(selected.target.extent.width, selected.target.extent.height),
                capabilities, budget, selected.occurrences, bindings)
            when (val layout = FrameSourceLayoutV4.layeredFrame(frame)) {
                is SourceConstructionResultV4.Built -> W6aLayerPlanBudget.translate(layout.value.prepareAndPublish())
                is SourceConstructionResultV4.Refused -> W6aLayerPlanBudget.translate(layout.failure)
            }
        } catch (failure: W6aResourceLimitFailure) {
            W6aLayerPlanBudget.refusal(failure.message ?: "Layer frame budget exceeded.")
        } catch (failure: RawMaterialRequirementsV2.Refusal) {
            W6aLayerPlanBudget.translate(sourceConstructionRefusalV4(failure.code).failure)
        } catch (failure: IllegalArgumentException) {
            RenderPlanResult.GapOnPromotedScope(listOf(diagnostic(W6aPlanDiagnostics.UnsupportedChild, failure.message ?: "Invalid layer construction.")))
        } catch (_: ArithmeticException) {
            W6aLayerPlanBudget.refusal("Layer resource sizing overflows.")
        }
    }

    private fun refusalFor(descriptor: LayerDescriptor): Pair<String, String>? {
        if (descriptor.initWithPrevious) return W6aPlanDiagnostics.UnsupportedRestore to "Previous-content initialization is outside this slice."
        if (descriptor.backdrop !is EffectStack.Empty) return W6aPlanDiagnostics.UnsupportedBackdrop to
            "W6a does not admit layer backdrop filters."
        if (descriptor.effects !is EffectStack.Empty) return W6aPlanDiagnostics.UnsupportedSpatialFilter to
            "W6a does not admit layer effects."
        if (!finite(descriptor.transform) || descriptor.copyBounds()?.isFinite() == false) {
            return W6aPlanDiagnostics.NonFiniteTransform to "A layer descriptor contains non-finite geometry."
        }
        if (descriptor.blend != BlendNode.SrcOver) return W6aPlanDiagnostics.UnsupportedRestore to "W6a initially supports only SRC_OVER restoration."
        if (descriptor.paint == null && descriptor.material != null) return W6aPlanDiagnostics.UnsupportedRestore to "A restore source without its captured paint is unsupported."
        val paint = descriptor.paint ?: return null
        if (paint.imageFilter != null || paint.maskFilter != null) return W6aPlanDiagnostics.UnsupportedSpatialFilter to
            "W6a does not admit filters on layer restore paint."
        if (paint.colorFilter != null || paint.shader != null || paint.blender != null || paint.alphaNotOne() ||
            paint.blendMode != BlendMode.SRC_OVER || descriptor.blend != BlendNode.SrcOver
        ) return W6aPlanDiagnostics.UnsupportedRestore to "W6a initially supports only opaque SRC_OVER restoration."
        return null
    }

    private fun PaintNode.alphaNotOne(): Boolean = color.alpha != 255

    private fun finite(matrix: org.graphiks.math.matrix.Matrix3x3F32): Boolean = listOf(
        matrix.sx, matrix.kx, matrix.tx, matrix.ky, matrix.sy, matrix.ty,
        matrix.persp0, matrix.persp1, matrix.persp2,
    ).all(Float::isFinite)

    private fun invalid(code: String, message: String): GpuPlanSelection.InvalidScene =
        GpuPlanSelection.InvalidScene(listOf(diagnostic(code, message)))

    private fun diagnostic(code: String, message: String): RenderDiagnostic = RenderDiagnostic(
        RenderDiagnosticCode(code), RenderDiagnosticDomain.SCENE, RenderDiagnosticSeverity.ERROR, message,
    )

    public companion object {
        public const val CAPABILITY_ID: String = "w6a.layer.v1"
    }
}
