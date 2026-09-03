package org.graphiks.kanvas.gpu.plan

import java.security.MessageDigest
import java.util.Collections
import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.CanonicalId
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32
import org.graphiks.math.color.ColorTransferFunction
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.matrix.Matrix3x3F32

/** W4a's closed capability: fractional solid rectangles with analytic scalar AA. */
public class W4aAnalyticRectPlanCompiler : GpuPlanCompiler {
    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection {
        if (scene.extent != target.extent || scene.colorSpace != target.colorSpace) {
            return invalidSelection(diag(W4aPlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, "Scene and target descriptors disagree"))
        }
        if (target.colorSpace != ColorSpace.SRGB) return notCandidate("W4a supports only sRGB targets")
        return when (val recognition = recognize(scene)) {
            is Recognition.Accepted -> GpuPlanSelection.Candidate(W4aCandidate(this, scene.canonicalId, target, recognition.draws))
            is Recognition.Gap -> notCandidate(recognition.message)
            is Recognition.Invalid -> invalidSelection(diag(W4aPlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, recognition.message))
        }
    }

    override fun plan(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): RenderPlanResult<RenderGraph> {
        val selected = candidate as? W4aCandidate ?: return invalidCandidate()
        if (selected.owner !== this || !selected.hasMatchingFingerprints()) return invalidCandidate()
        val target = selected.target
        val extent = SizeI32(target.extent.width, target.extent.height)
        if (extent.width > capabilities.maxTextureDimension2D || extent.height > capabilities.maxTextureDimension2D) {
            return promoted(W4aPlanDiagnostics.CapabilityTextureDimension, "Target extent exceeds device texture limits")
        }
        if (FORMAT !in capabilities.supportedFormats()) return promoted(W4aPlanDiagnostics.CapabilityFormat, "W4a target format is unavailable")
        if (!REQUIRED_OPERATIONS.all { it in capabilities.supportedOperations() }) {
            return promoted(W4aPlanDiagnostics.CapabilityOperation, "W4a required operation is unavailable")
        }
        if (capabilities.maxDynamicUniformBuffersPerPipelineLayout < 1) {
            return promoted(W4aPlanDiagnostics.CapabilityDynamicUniform, "W4a requires one dynamic uniform buffer")
        }
        if (!validAllocationFacts(capabilities)) {
            return promoted(W4aPlanDiagnostics.CapabilityAllocationPolicy, "W4a allocation facts are not power-of-two aligned")
        }
        val plannedDraws = selected.draws.map { sealed ->
            val raster = rasterBounds(sealed.deviceBounds) ?: return resourceLimit(W4aPlanDiagnostics.SizeOverflow, "Device raster bounds exceed I32")
            val targetRaster = intersect(raster, targetBounds(extent))
                ?: return resourceLimit(W4aPlanDiagnostics.SizeOverflow, "Selected draw became empty during planning")
            val scissor = if (sealed.clip == null) targetRaster else intersect(targetRaster, sealed.clip)
                ?: return resourceLimit(W4aPlanDiagnostics.SizeOverflow, "Selected draw became empty during planning")
            AnalyticRectDraw.of(sealed.commandIndex, sealed.color, sealed.deviceBounds, raster, scissor)
        }
        val footprint = when (val memory = AnalyticRectPlanBudget.calculate(extent, plannedDraws.size, capabilities, budget)) {
            is AnalyticRectPlanBudgetResult.WithinBudget -> memory.footprint
            is AnalyticRectPlanBudgetResult.Exceeded -> return resourceLimit(W4aPlanDiagnostics.BudgetFrameLocalExceeded, "Frame-local memory budget is exceeded")
            is AnalyticRectPlanBudgetResult.Invalid -> return resourceLimit(W4aPlanDiagnostics.SizeOverflow, "W4a physical footprint overflowed")
        }
        if (listOf(footprint.readbackBytes, footprint.vertexCapacityBytes, footprint.indexCapacityBytes, footprint.uniformCapacityBytes)
                .any { it > capabilities.maxBufferSizeBytes }) {
            return promoted(W4aPlanDiagnostics.CapabilityBufferSize, "W4a buffer exceeds device limits")
        }
        return try {
            val logicalTarget = PlanResource.of(PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D, FORMAT, extent,
                footprint.targetBytes, setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource), PlanResourceLifetime.FrameLocal, 0, 2)
            val staging = PlanResource.of(PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Buffer, null, null,
                footprint.readbackBytes, setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead), PlanResourceLifetime.FrameLocal, 1, 2)
            val vertex = PlanResource.of(PlanResourceRole.VertexData, 0, PlanResourceKind.Buffer, null, null,
                footprint.vertexCapacityBytes, setOf(PlanResourceUsage.Vertex, PlanResourceUsage.CopyDestination), PlanResourceLifetime.FrameLocal, 0, 2)
            val index = PlanResource.of(PlanResourceRole.IndexData, 0, PlanResourceKind.Buffer, null, null,
                footprint.indexCapacityBytes, setOf(PlanResourceUsage.Index, PlanResourceUsage.CopyDestination), PlanResourceLifetime.FrameLocal, 0, 2)
            val uniform = PlanResource.of(PlanResourceRole.UniformData, 0, PlanResourceKind.Buffer, null, null,
                footprint.uniformCapacityBytes, setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination), PlanResourceLifetime.FrameLocal, 0, 2)
            val render = PlanPass.RenderPass(0, logicalTarget.id, plannedDraws, AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store,
                PlanDrawDataResources(vertex.id, index.id, uniform.id))
            val readback = PlanPass.ReadbackPass(0, logicalTarget.id, staging.id, footprint.readbackBytesPerRow)
            RenderPlanResult.Ready(RenderGraph.of(
                id = PlanId(planIdentity(selected.sceneCanonicalId, target, capabilities, budget)),
                capabilityId = CAPABILITY_ID,
                targetExtent = extent,
                colorFormat = FORMAT,
                capabilities = capabilities,
                budget = budget,
                visualCommandCount = plannedDraws.size,
                resources = listOf(logicalTarget, staging, vertex, index, uniform),
                passes = listOf(render, readback),
                dependencies = listOf(PlanPassDependency(render.id, readback.id)),
                peakFrameLocalBytes = footprint.peakBytes,
            ))
        } catch (_: IllegalArgumentException) {
            promoted(W4aPlanDiagnostics.PlanIdentityInvalid, "W4a graph invariants were not satisfied")
        }
    }

    private fun recognize(scene: SceneSnapshot): Recognition {
        if (scene.colorSpace != ColorSpace.SRGB) return Recognition.Gap("W4a supports only sRGB scenes")
        val target = RectF32(0f, 0f, scene.extent.width.toFloat(), scene.extent.height.toFloat())
        val draws = mutableListOf<SealedDraw>()
        for ((index, command) in scene.withIndex()) when (command) {
            is SceneCommand.Draw -> when (val draw = recognizeDraw(command.node, index, target)) {
                is DrawRecognition.Accepted -> draws += draw.draw
                is DrawRecognition.Gap -> return Recognition.Gap(draw.message)
                is DrawRecognition.Invalid -> return Recognition.Invalid(draw.message)
            }
            is SceneCommand.SetTransform -> if (!finite(command.matrix)) {
                return Recognition.Invalid("Transform is non-finite")
            }
            is SceneCommand.SetClip -> if (!finiteMetadataClip(command.clip)) {
                return Recognition.Invalid("Clip bounds are non-finite")
            }
            is SceneCommand.Annotation -> if (!finite(command.copyBounds())) return Recognition.Invalid("Annotation bounds are non-finite")
            else -> return Recognition.Gap("Scene command is outside W4a")
        }
        if (draws.isEmpty()) return Recognition.Gap("W4a requires at least one visible draw")
        if (draws.size > MAX_DRAWS) return Recognition.Gap("W4a accepts at most 512 visual draws")
        if (draws.none { hasFractionalEdge(it.deviceBounds) }) return Recognition.Gap("W4a requires a fractional device edge")
        return Recognition.Accepted(draws)
    }

    private fun recognizeDraw(node: DrawNode, index: Int, target: RectF32): DrawRecognition {
        val geometry = node.geometry as? GeometryNode.Rect ?: return DrawRecognition.Gap("Draw geometry is outside W4a")
        val material = node.material as? MaterialNode.Solid ?: return DrawRecognition.Gap("Draw material is outside W4a")
        if (node.origin != DrawOrigin.RECT) return DrawRecognition.Gap("Draw origin is outside W4a")
        if (!w4aPaint(node.paint) || !w4aBlend(node.blend) || node.effects !is EffectStack.Empty || node.resource != null || node.operationBlendMode != null) {
            return DrawRecognition.Gap("Draw state is outside W4a")
        }
        if (node.coverage != CoverageRequest.ANTIALIASED) return DrawRecognition.Gap("Coverage is outside W4a")
        val source = geometry.copyBounds()
        if (!finite(source) || !finite(node.transform)) return DrawRecognition.Invalid("Draw geometry or transform is non-finite")
        if (source.isEmpty) return DrawRecognition.Gap("Source geometry is empty or inverted")
        val device = deviceBounds(source, node.transform) ?: return DrawRecognition.Gap("Transform is outside W4a")
        val clip = when (val recognized = recognizeClip(node.clip)) {
            is ClipRecognition.Accepted -> recognized.bounds
            is ClipRecognition.Gap -> return DrawRecognition.Gap(recognized.message)
            is ClipRecognition.Invalid -> return DrawRecognition.Invalid(recognized.message)
        }
        val targetVisible = intersect(device, target)
            ?: return DrawRecognition.Gap("Draw is outside the target")
        val visible = if (clip == null) targetVisible else intersect(targetVisible, clip.toRectF32())
            ?: return DrawRecognition.Gap("Draw is fully clipped out")
        if (visible.isEmpty) return DrawRecognition.Gap("Draw is fully clipped out")
        return DrawRecognition.Accepted(SealedDraw(index, linearPremultiplied(material.color), device, clip))
    }

    private fun recognizeClip(clip: ClipStackNode): ClipRecognition = when (clip) {
        ClipStackNode.Empty -> ClipRecognition.Accepted(null)
        is ClipStackNode.DeviceRect -> {
            val bounds = clip.copyBounds()
            if (!finite(bounds)) ClipRecognition.Invalid("Clip bounds are non-finite")
            else if (clip.antiAlias) ClipRecognition.Gap("Antialiased clip is outside W4a")
            else integralRect(bounds)?.let(ClipRecognition::Accepted) ?: ClipRecognition.Gap("Clip is not an integral non-empty I32 rectangle")
        }
        is ClipStackNode.Operations -> ClipRecognition.Gap("Complex clip is outside W4a")
    }

    private fun deviceBounds(source: RectF32, matrix: Matrix3x3F32): RectF32? {
        if (!(matrix.isIdentity || matrix.isScaleTranslate()) || !finite(matrix)) return null
        val corners = listOf(
            matrix.transform(Point2F32(source.left, source.top)), matrix.transform(Point2F32(source.right, source.top)),
            matrix.transform(Point2F32(source.right, source.bottom)), matrix.transform(Point2F32(source.left, source.bottom)),
        )
        if (corners.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        return RectF32(corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y })
    }

    private fun rasterBounds(bounds: RectF32): RectI32? {
        val left = floor(bounds.left.toDouble())
        val top = floor(bounds.top.toDouble())
        val right = ceil(bounds.right.toDouble())
        val bottom = ceil(bounds.bottom.toDouble())
        if (listOf(left, top, right, bottom).any { it < Int.MIN_VALUE.toDouble() || it > Int.MAX_VALUE.toDouble() }) return null
        return RectI32(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()).takeUnless { it.isEmpty }
    }

    private fun integralRect(bounds: RectF32): RectI32? {
        val values = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val converted = values.map { value ->
            val long = value.toLong()
            if (long.toFloat() != value || long !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
            long.toInt()
        }
        return RectI32(converted[0], converted[1], converted[2], converted[3]).takeUnless { it.isEmpty64() }
    }

    private fun RectI32.toRectF32(): RectF32 = RectF32(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    private fun targetBounds(extent: SizeI32): RectI32 = RectI32(0, 0, extent.width, extent.height)
    private fun intersect(first: RectI32, second: RectI32): RectI32? = first.copy().takeIf { it.intersect(second) }
    private fun intersect(first: RectF32, second: RectF32): RectF32? = RectF32(
        maxOf(first.left, second.left), maxOf(first.top, second.top), minOf(first.right, second.right), minOf(first.bottom, second.bottom),
    ).takeUnless { it.isEmpty }

    private fun hasFractionalEdge(bounds: RectF32): Boolean = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
        .any { value -> value.toLong().toFloat() != value }
    private fun finiteMetadataClip(clip: ClipStackNode): Boolean = when (clip) {
        ClipStackNode.Empty, is ClipStackNode.Operations -> true
        is ClipStackNode.DeviceRect -> finite(clip.copyBounds())
    }
    private fun finite(bounds: RectF32): Boolean = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all(Float::isFinite)
    private fun finite(matrix: Matrix3x3F32): Boolean = listOf(matrix.sx, matrix.kx, matrix.tx, matrix.ky, matrix.sy, matrix.ty, matrix.persp0, matrix.persp1, matrix.persp2).all(Float::isFinite)
    private fun w4aBlend(blend: BlendNode): Boolean = when (blend) { BlendNode.SrcOver -> true; is BlendNode.Mode -> blend.mode == BlendMode.SRC_OVER; is BlendNode.Paint -> blend.mode == BlendMode.SRC_OVER && blend.blender == null; is BlendNode.Custom -> false }
    private fun w4aPaint(paint: PaintNode?): Boolean = paint == null || (paint.shader == null && paint.blender == null && paint.colorFilter == null && paint.maskFilter == null && paint.pathEffect == null && paint.imageFilter == null && paint.style == PaintStyleNode.FILL && paint.blendMode == BlendMode.SRC_OVER)
    private fun linearPremultiplied(color: ColorARGB): ColorF32 = ColorF32.of(
        ColorTransferFunction.sRgb.toLinear(color.redNormalized) * color.alphaNormalized,
        ColorTransferFunction.sRgb.toLinear(color.greenNormalized) * color.alphaNormalized,
        ColorTransferFunction.sRgb.toLinear(color.blueNormalized) * color.alphaNormalized, color.alphaNormalized,
    )
    private fun validAllocationFacts(capabilities: PlanCapabilitySnapshot): Boolean = listOf(
        capabilities.copyBytesPerRowAlignment.toLong(), capabilities.minUniformBufferOffsetAlignment.toLong(),
        capabilities.bufferAllocationPolicy.vertexFloorBytes, capabilities.bufferAllocationPolicy.indexFloorBytes, capabilities.bufferAllocationPolicy.uniformFloorBytes,
    ).all { it > 0 && it and (it - 1) == 0L }

    private fun notCandidate(message: String): GpuPlanSelection.NotCandidate = GpuPlanSelection.NotCandidate(listOf(diag(W4aPlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.SCENE, message)))
    private fun invalidSelection(diagnostic: RenderDiagnostic): GpuPlanSelection.InvalidScene = GpuPlanSelection.InvalidScene(listOf(diagnostic))
    private fun diag(code: RenderDiagnosticCode, domain: RenderDiagnosticDomain, message: String): RenderDiagnostic = W4aPlanDiagnostics.diagnostic(code, domain, message)
    private fun promoted(code: RenderDiagnosticCode, message: String): RenderPlanResult<Nothing> = RenderPlanResult.GapOnPromotedScope(listOf(diag(code, RenderDiagnosticDomain.CAPABILITY, message)))
    private fun resourceLimit(code: RenderDiagnosticCode, message: String): RenderPlanResult<Nothing> = RenderPlanResult.ResourceLimitExceeded(listOf(diag(code, RenderDiagnosticDomain.RESOURCE, message)))
    private fun invalidCandidate(): RenderPlanResult<Nothing> = RenderPlanResult.InvalidScene(listOf(RenderDiagnostic(RenderDiagnosticCode("gpu-plan.selection.invalid-candidate"), RenderDiagnosticDomain.SCENE, RenderDiagnosticSeverity.ERROR, "W4a candidate does not belong to this compiler.")))

    private fun planIdentity(scene: CanonicalId, target: RenderTargetDescriptor, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): String {
        val fields = listOf("w4a-plan-v1", scene.value, target.extent.width.toString(), target.extent.height.toString(), target.colorSpace.name,
            target.colorSpace.transferFunction.name, target.colorSpace.gamut.name, capabilities.deviceGeneration.toString(), capabilities.maxTextureDimension2D.toString(), capabilities.maxBufferSizeBytes.toString(), capabilities.copyBytesPerRowAlignment.toString(), capabilities.supportedFormats().map { it.name }.sorted().joinToString(","), capabilities.minUniformBufferOffsetAlignment.toString(), capabilities.maxDynamicUniformBuffersPerPipelineLayout.toString(), capabilities.supportedOperations().map { it.name }.sorted().joinToString(","), capabilities.bufferAllocationPolicy.vertexFloorBytes.toString(), capabilities.bufferAllocationPolicy.indexFloorBytes.toString(), capabilities.bufferAllocationPolicy.uniformFloorBytes.toString(), capabilities.bufferAllocationPolicy.growth.name, budget.maxFrameLocalBytes.toString())
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach { value -> val bytes = value.encodeToByteArray(); digest.update(bytes.size.toString().encodeToByteArray()); digest.update(0); digest.update(bytes); digest.update(0) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private sealed interface Recognition { data class Accepted(val draws: List<SealedDraw>) : Recognition; data class Gap(val message: String) : Recognition; data class Invalid(val message: String) : Recognition }
    private sealed interface DrawRecognition { data class Accepted(val draw: SealedDraw) : DrawRecognition; data class Gap(val message: String) : DrawRecognition; data class Invalid(val message: String) : DrawRecognition }
    private sealed interface ClipRecognition { data class Accepted(val bounds: RectI32?) : ClipRecognition; data class Gap(val message: String) : ClipRecognition; data class Invalid(val message: String) : ClipRecognition }
    private data class SealedDraw(val commandIndex: Int, val color: ColorF32, val deviceBounds: RectF32, val clip: RectI32?)
    private class W4aCandidate(val owner: W4aAnalyticRectPlanCompiler, override val sceneCanonicalId: CanonicalId, override val target: RenderTargetDescriptor, draws: List<SealedDraw>) : GpuPlanCandidate {
        override val capabilityId: String = CAPABILITY_ID
        val draws: List<SealedDraw> = Collections.unmodifiableList(draws.map { it.copy(deviceBounds = it.deviceBounds.copy(), clip = it.clip?.copy()) })
        private val sceneFingerprint = sceneCanonicalId
        private val targetFingerprint = target.canonicalId
        fun hasMatchingFingerprints(): Boolean = capabilityId == CAPABILITY_ID && sceneCanonicalId == sceneFingerprint && target.canonicalId == targetFingerprint
    }

    public companion object {
        public const val CAPABILITY_ID: String = "solid-rect-scalar-aa-simple-scissor-src-over-srgb-v1"
        private val FORMAT = PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL
        private val REQUIRED_OPERATIONS = setOf(PlanOperationCapability.RenderPass, PlanOperationCapability.CopyUpload, PlanOperationCapability.UniformBuffer, PlanOperationCapability.Readback)
        private const val MAX_DRAWS = 512
    }
}
