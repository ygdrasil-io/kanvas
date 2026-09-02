package org.graphiks.kanvas.gpu.plan

import java.security.MessageDigest
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
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

/** W3's closed capability: pixel-aligned solid rectangles and SrcOver only. */
public class W3SolidRectPlanCompiler : GpuPlanCompiler {
    override fun plan(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraph> {
        if (scene.extent != target.extent || scene.colorSpace != target.colorSpace) {
            return invalid(diag(W3PlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, "Scene and target descriptors disagree"))
        }
        val recognition = recognize(scene)
        when (recognition) {
            is Recognition.Gap -> return gap(recognition.diagnostic)
            is Recognition.Invalid -> return invalid(recognition.diagnostic)
            is Recognition.Accepted -> Unit
        }

        if (target.colorSpace != ColorSpace.SRGB) {
            return gap(diag(W3PlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.TARGET, "W3 supports only sRGB targets"))
        }

        val targetExtent = SizeI32(target.extent.width, target.extent.height)
        if (targetExtent.width > capabilities.maxTextureDimension2D || targetExtent.height > capabilities.maxTextureDimension2D) {
            return promoted(diag(W3PlanDiagnostics.CapabilityTextureDimension, RenderDiagnosticDomain.CAPABILITY, "Target extent exceeds device texture limits"))
        }
        if (FORMAT !in capabilities.supportedFormats()) {
            return promoted(diag(W3PlanDiagnostics.CapabilityFormat, RenderDiagnosticDomain.CAPABILITY, "W3 target format is unavailable"))
        }
        val memory = PlanMemoryBudget.calculate(targetExtent, PIXEL_BYTES, capabilities.copyBytesPerRowAlignment, budget)
        val withinBudget = when (memory) {
            is PlanMemoryBudgetResult.WithinBudget -> memory
            is PlanMemoryBudgetResult.Exceeded -> return resourceLimit(
                diag(W3PlanDiagnostics.BudgetFrameLocalExceeded, RenderDiagnosticDomain.RESOURCE, "Frame-local memory budget is exceeded"),
            )
            is PlanMemoryBudgetResult.Invalid -> return resourceLimit(
                diag(W3PlanDiagnostics.SizeOverflow, RenderDiagnosticDomain.RESOURCE, "Frame-local size calculation overflowed"),
            )
        }
        val targetBytes = try {
            Math.multiplyExact(Math.multiplyExact(target.extent.width.toLong(), target.extent.height.toLong()), PIXEL_BYTES)
        } catch (_: ArithmeticException) {
            return resourceLimit(diag(W3PlanDiagnostics.SizeOverflow, RenderDiagnosticDomain.RESOURCE, "Target size overflowed"))
        }
        val stagingBytes = try {
            Math.multiplyExact(withinBudget.readbackBytesPerRow, target.extent.height.toLong())
        } catch (_: ArithmeticException) {
            return resourceLimit(diag(W3PlanDiagnostics.SizeOverflow, RenderDiagnosticDomain.RESOURCE, "Readback size overflowed"))
        }
        if (stagingBytes > capabilities.maxBufferSizeBytes) {
            return promoted(diag(W3PlanDiagnostics.CapabilityBufferSize, RenderDiagnosticDomain.CAPABILITY, "Readback buffer exceeds device limits"))
        }

        return try {
            val logicalTarget = PlanResource.of(
                PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D, FORMAT, targetExtent, targetBytes,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource), PlanResourceLifetime.FrameLocal, 0, 2,
            )
            val staging = PlanResource.of(
                PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Buffer, null, null, stagingBytes,
                setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead), PlanResourceLifetime.FrameLocal, 1, 2,
            )
            val render = PlanPass.RenderPass(
                0, logicalTarget.id, recognition.draws, AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store,
            )
            val readback = PlanPass.ReadbackPass(0, logicalTarget.id, staging.id, withinBudget.readbackBytesPerRow)
            RenderPlanResult.Ready(
                RenderGraph.of(
                    id = PlanId(planIdentity(scene, target, capabilities, budget)),
                    capabilityId = CAPABILITY_ID,
                    targetExtent = targetExtent,
                    colorFormat = FORMAT,
                    capabilities = capabilities,
                    budget = budget,
                    visualCommandCount = recognition.draws.size,
                    resources = listOf(logicalTarget, staging),
                    passes = listOf(render, readback),
                    dependencies = listOf(PlanPassDependency(render.id, readback.id)),
                    peakFrameLocalBytes = withinBudget.peakBytes,
                ),
            )
        } catch (_: IllegalArgumentException) {
            promoted(diag(W3PlanDiagnostics.PlanIdentityInvalid, RenderDiagnosticDomain.TARGET, "W3 graph invariants were not satisfied"))
        }
    }

    private fun recognize(scene: SceneSnapshot): Recognition {
        if (scene.colorSpace != ColorSpace.SRGB) return Recognition.Gap(
            diag(W3PlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.SCENE, "W3 supports only sRGB scenes"),
        )
        val targetBounds = RectI32(0, 0, scene.extent.width, scene.extent.height)
        val draws = mutableListOf<SolidRectDraw>()
        for ((index, command) in scene.withIndex()) {
            when (command) {
                is SceneCommand.Draw -> when (val result = recognizeDraw(command.node, index, targetBounds)) {
                    is DrawRecognition.Accepted -> draws += result.draw
                    is DrawRecognition.Gap -> return Recognition.Gap(result.diagnostic)
                    is DrawRecognition.Invalid -> return Recognition.Invalid(result.diagnostic)
                }
                is SceneCommand.DrawColor -> when (val result = recognizeDrawColor(command, index, targetBounds)) {
                    is DrawRecognition.Accepted -> draws += result.draw
                    is DrawRecognition.Gap -> return Recognition.Gap(result.diagnostic)
                    is DrawRecognition.Invalid -> return Recognition.Invalid(result.diagnostic)
                }
                is SceneCommand.SetTransform -> when (val result = recognizeProvenanceTransform(command.matrix)) {
                    is ProvenanceRecognition.Accepted -> Unit
                    is ProvenanceRecognition.Gap -> return Recognition.Gap(result.diagnostic)
                    is ProvenanceRecognition.Invalid -> return Recognition.Invalid(result.diagnostic)
                }
                is SceneCommand.SetClip -> when (val result = recognizeClip(command.clip)) {
                    is ClipRecognition.Accepted -> Unit
                    is ClipRecognition.Gap -> return Recognition.Gap(result.diagnostic)
                    is ClipRecognition.Invalid -> return Recognition.Invalid(result.diagnostic)
                }
                is SceneCommand.Annotation -> if (!finite(command.copyBounds())) return Recognition.Invalid(
                    diag(W3PlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, "Annotation bounds are non-finite"),
                )
                else -> return Recognition.Gap(
                    diag(W3PlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.SCENE, "Scene command is outside W3"),
                )
            }
        }
        return if (draws.isEmpty()) Recognition.Gap(
            diag(W3PlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.SCENE, "W3 requires at least one visible draw"),
        ) else Recognition.Accepted(draws)
    }

    private fun recognizeDraw(node: DrawNode, index: Int, target: RectI32): DrawRecognition {
        val geometryNode = node.geometry as? GeometryNode.Rect
            ?: return semanticGap("Draw geometry or material is outside W3")
        val material = node.material as? org.graphiks.kanvas.render.ir.MaterialNode.Solid
            ?: return semanticGap("Draw geometry or material is outside W3")
        if (node.origin != DrawOrigin.RECT) {
            return semanticGap("Draw geometry or material is outside W3")
        }
        if (!w3Blend(node.blend) || node.effects !is EffectStack.Empty || node.resource != null || node.operationBlendMode != null || !w3Paint(node.paint)) {
            return semanticGap("Draw state is outside W3")
        }
        if (node.coverage != CoverageRequest.HARD_EDGE && node.coverage != CoverageRequest.ANTIALIASED) return semanticGap("Coverage is outside W3")
        val sourceBounds = geometryNode.copyBounds()
        if (!finite(sourceBounds) || !finite(node.transform)) {
            return DrawRecognition.Invalid(diag(W3PlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, "Draw geometry or transform is non-finite"))
        }
        if (sourceBounds.isEmpty) return geometryGap("Source geometry is empty or inverted")
        val geometry = resolveTransformed(sourceBounds, node.transform) ?: return geometryGap("Geometry is not pixel aligned")
        val clip = when (val recognizedClip = recognizeClip(node.clip)) {
            is ClipRecognition.Accepted -> recognizedClip.bounds
            is ClipRecognition.Gap -> return DrawRecognition.Gap(recognizedClip.diagnostic)
            is ClipRecognition.Invalid -> return DrawRecognition.Invalid(recognizedClip.diagnostic)
        }
        val visible = intersect(target, geometry) ?: return semanticGap("Draw is outside the target")
        val clipped = if (clip == null) visible else intersect(visible, clip)
            ?: return semanticGap("Draw is fully clipped out")
        return DrawRecognition.Accepted(SolidRectDraw.of(index, linearPremultiplied(material.color), clipped, clipped))
    }

    private fun recognizeDrawColor(command: SceneCommand.DrawColor, index: Int, target: RectI32): DrawRecognition {
        if (!finite(command.transform)) return DrawRecognition.Invalid(
            diag(W3PlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, "DrawColor transform is non-finite"),
        )
        if (command.mode != BlendMode.SRC_OVER) return semanticGap("DrawColor blend mode is outside W3")
        if (!command.transform.isIdentity) return geometryGap("DrawColor transform is outside W3")
        val clip = when (val recognizedClip = recognizeClip(command.clip)) {
            is ClipRecognition.Accepted -> recognizedClip.bounds
            is ClipRecognition.Gap -> return DrawRecognition.Gap(recognizedClip.diagnostic)
            is ClipRecognition.Invalid -> return DrawRecognition.Invalid(recognizedClip.diagnostic)
        }
        val visible = if (clip == null) target.copy() else intersect(target, clip)
            ?: return semanticGap("DrawColor is fully clipped out")
        return DrawRecognition.Accepted(SolidRectDraw.of(index, linearPremultiplied(command.color), visible, visible))
    }

    private fun recognizeProvenanceTransform(matrix: Matrix3x3F32): ProvenanceRecognition = when {
        !finite(matrix) -> ProvenanceRecognition.Invalid(diag(W3PlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, "Transform is non-finite"))
        matrix.isIdentity || matrix.isScaleTranslate() -> ProvenanceRecognition.Accepted
        else -> ProvenanceRecognition.Gap(diag(W3PlanDiagnostics.GeometryNotPixelAligned, RenderDiagnosticDomain.SCENE, "Transform is outside W3"))
    }

    private fun recognizeClip(clip: ClipStackNode): ClipRecognition = when (clip) {
        ClipStackNode.Empty -> ClipRecognition.Accepted(null)
        is ClipStackNode.DeviceRect -> {
            val bounds = clip.copyBounds()
            if (!finite(bounds)) ClipRecognition.Invalid(diag(W3PlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, "Clip bounds are non-finite"))
            else toIntegralRect(bounds)?.let { ClipRecognition.Accepted(it) }
                ?: ClipRecognition.Gap(diag(W3PlanDiagnostics.ClipNotPixelAligned, RenderDiagnosticDomain.SCENE, "Clip is not pixel aligned"))
        }
        is ClipStackNode.Operations -> ClipRecognition.Gap(
            diag(W3PlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.SCENE, "Complex clips are outside W3"),
        )
    }

    private fun resolveTransformed(bounds: RectF32, matrix: Matrix3x3F32): RectI32? {
        if (!finite(bounds) || !finite(matrix) || !(matrix.isIdentity || matrix.isScaleTranslate())) return null
        val points = listOf(
            matrix.transform(Point2F32(bounds.left, bounds.top)),
            matrix.transform(Point2F32(bounds.right, bounds.top)),
            matrix.transform(Point2F32(bounds.right, bounds.bottom)),
            matrix.transform(Point2F32(bounds.left, bounds.bottom)),
        )
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        return toIntegralRect(RectF32(
            points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y },
        ))
    }

    private fun toIntegralRect(bounds: RectF32): RectI32? {
        if (!finite(bounds)) return null
        val values = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val converted = values.map { value ->
            val integral = value.toLong()
            if (integral.toFloat() != value || integral !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
            integral.toInt()
        }
        val result = RectI32(converted[0], converted[1], converted[2], converted[3])
        return result.takeUnless { it.isEmpty64() }
    }

    private fun intersect(first: RectI32, second: RectI32): RectI32? = first.copy().takeIf { it.intersect(second) }

    private fun w3Blend(blend: BlendNode): Boolean = when (blend) {
        BlendNode.SrcOver -> true
        is BlendNode.Mode -> blend.mode == BlendMode.SRC_OVER
        is BlendNode.Paint -> blend.mode == BlendMode.SRC_OVER && blend.blender == null
        is BlendNode.Custom -> false
    }

    private fun w3Paint(paint: PaintNode?): Boolean = paint == null || (
        paint.shader == null && paint.blender == null && paint.colorFilter == null && paint.maskFilter == null &&
            paint.pathEffect == null && paint.imageFilter == null && paint.style == PaintStyleNode.FILL &&
            paint.blendMode == BlendMode.SRC_OVER
        )

    private fun linearPremultiplied(color: ColorARGB): ColorF32 {
        val alpha = color.alphaNormalized
        return ColorF32.of(
            ColorTransferFunction.sRgb.toLinear(color.redNormalized) * alpha,
            ColorTransferFunction.sRgb.toLinear(color.greenNormalized) * alpha,
            ColorTransferFunction.sRgb.toLinear(color.blueNormalized) * alpha,
            alpha,
        )
    }

    private fun finite(bounds: RectF32): Boolean = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all(Float::isFinite)
    private fun finite(matrix: Matrix3x3F32): Boolean = listOf(
        matrix.sx, matrix.kx, matrix.tx, matrix.ky, matrix.sy, matrix.ty, matrix.persp0, matrix.persp1, matrix.persp2,
    ).all(Float::isFinite)

    private fun semanticGap(message: String): DrawRecognition.Gap = DrawRecognition.Gap(
        diag(W3PlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.SCENE, message),
    )
    private fun geometryGap(message: String): DrawRecognition.Gap = DrawRecognition.Gap(
        diag(W3PlanDiagnostics.GeometryNotPixelAligned, RenderDiagnosticDomain.SCENE, message),
    )
    private fun diag(code: org.graphiks.kanvas.render.ir.RenderDiagnosticCode, domain: RenderDiagnosticDomain, message: String): RenderDiagnostic =
        W3PlanDiagnostics.diagnostic(code, domain, message)
    private fun gap(diagnostic: RenderDiagnostic): RenderPlanResult<Nothing> = RenderPlanResult.GapNotMigrated(listOf(diagnostic))
    private fun promoted(diagnostic: RenderDiagnostic): RenderPlanResult<Nothing> = RenderPlanResult.GapOnPromotedScope(listOf(diagnostic))
    private fun invalid(diagnostic: RenderDiagnostic): RenderPlanResult<Nothing> = RenderPlanResult.InvalidScene(listOf(diagnostic))
    private fun resourceLimit(diagnostic: RenderDiagnostic): RenderPlanResult<Nothing> = RenderPlanResult.ResourceLimitExceeded(listOf(diagnostic))

    private fun planIdentity(scene: SceneSnapshot, target: RenderTargetDescriptor, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): String {
        val fields = listOf(
            "w3-plan-v1", scene.canonicalId.value, target.extent.width.toString(), target.extent.height.toString(),
            target.colorSpace.name, target.colorSpace.transferFunction.name, target.colorSpace.gamut.name,
            capabilities.deviceGeneration.toString(), capabilities.maxTextureDimension2D.toString(), capabilities.maxBufferSizeBytes.toString(),
            capabilities.copyBytesPerRowAlignment.toString(), capabilities.supportedFormats().map { it.name }.sorted().joinToString(","),
            budget.maxFrameLocalBytes.toString(),
        )
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach { field ->
            val bytes = field.encodeToByteArray()
            digest.update(bytes.size.toString().encodeToByteArray())
            digest.update(0)
            digest.update(bytes)
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private sealed interface Recognition {
        data class Accepted(val draws: List<SolidRectDraw>) : Recognition
        data class Gap(val diagnostic: RenderDiagnostic) : Recognition
        data class Invalid(val diagnostic: RenderDiagnostic) : Recognition
    }
    private sealed interface DrawRecognition {
        data class Accepted(val draw: SolidRectDraw) : DrawRecognition
        data class Gap(val diagnostic: RenderDiagnostic) : DrawRecognition
        data class Invalid(val diagnostic: RenderDiagnostic) : DrawRecognition
    }
    private sealed interface ClipRecognition {
        data class Accepted(val bounds: RectI32?) : ClipRecognition
        data class Gap(val diagnostic: RenderDiagnostic) : ClipRecognition
        data class Invalid(val diagnostic: RenderDiagnostic) : ClipRecognition
    }
    private sealed interface ProvenanceRecognition {
        data object Accepted : ProvenanceRecognition
        data class Gap(val diagnostic: RenderDiagnostic) : ProvenanceRecognition
        data class Invalid(val diagnostic: RenderDiagnostic) : ProvenanceRecognition
    }

    public companion object {
        public const val CAPABILITY_ID: String = "solid-rect-pixel-aligned-simple-clip-src-over-srgb-v1"
        private val FORMAT: PlanLogicalColorFormat = PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL
        private const val PIXEL_BYTES: Long = 4L
    }
}
