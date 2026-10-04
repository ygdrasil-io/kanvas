package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32
import org.graphiks.math.color.ColorTransferFunction
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.Matrix3x3F32

internal sealed interface W3DrawColorAdmissionResultV1 {
    data class Accepted(val draw: SolidRectDraw) : W3DrawColorAdmissionResultV1
    data class Gap(val diagnostic: RenderDiagnostic) : W3DrawColorAdmissionResultV1
    data class Invalid(val diagnostic: RenderDiagnostic) : W3DrawColorAdmissionResultV1
}

/** Single issuer for the existing W3 DrawColor admission and color conversion. */
internal object W3DrawColorAdmissionV1 {
    internal sealed interface ClipRecognition {
        data class Accepted(val bounds: RectI32?) : ClipRecognition
        data class Gap(val diagnostic: RenderDiagnostic) : ClipRecognition
        data class Invalid(val diagnostic: RenderDiagnostic) : ClipRecognition
    }

    internal fun recognize(
        command: SceneCommand.DrawColor,
        commandIndexI32: Int,
        target: RectI32,
        compositionDomain: CompositionDomain,
    ): W3DrawColorAdmissionResultV1 {
        if (!isFinite(command.transform)) return W3DrawColorAdmissionResultV1.Invalid(
            W3PlanDiagnostics.diagnostic(
                W3PlanDiagnostics.SceneInvalid,
                RenderDiagnosticDomain.SCENE,
                "DrawColor transform is non-finite",
            ),
        )
        if (command.mode != BlendMode.SRC_OVER) return W3DrawColorAdmissionResultV1.Gap(
            W3PlanDiagnostics.diagnostic(
                W3PlanDiagnostics.CommandNotMigrated,
                RenderDiagnosticDomain.SCENE,
                "DrawColor blend mode is outside W3",
            ),
        )
        if (!command.transform.isIdentity) return W3DrawColorAdmissionResultV1.Gap(
            W3PlanDiagnostics.diagnostic(
                W3PlanDiagnostics.GeometryNotPixelAligned,
                RenderDiagnosticDomain.SCENE,
                "DrawColor transform is outside W3",
            ),
        )
        val clip = when (val recognizedClip = recognizeClip(command.clip)) {
            is ClipRecognition.Accepted -> recognizedClip.bounds
            is ClipRecognition.Gap -> return W3DrawColorAdmissionResultV1.Gap(recognizedClip.diagnostic)
            is ClipRecognition.Invalid -> return W3DrawColorAdmissionResultV1.Invalid(recognizedClip.diagnostic)
        }
        val visible = if (clip == null) target.copy() else intersect(target, clip)
            ?: return W3DrawColorAdmissionResultV1.Gap(
                W3PlanDiagnostics.diagnostic(
                    W3PlanDiagnostics.CommandNotMigrated,
                    RenderDiagnosticDomain.SCENE,
                    "DrawColor is fully clipped out",
                ),
            )
        return W3DrawColorAdmissionResultV1.Accepted(
            SolidRectDraw.of(commandIndexI32, premultiplied(command.color, compositionDomain), visible, visible),
        )
    }

    internal fun recognizeClip(clip: ClipStackNode): ClipRecognition = when (clip) {
        ClipStackNode.Empty -> ClipRecognition.Accepted(null)
        is ClipStackNode.DeviceRect -> {
            val bounds = clip.copyBounds()
            if (!isFinite(bounds)) ClipRecognition.Invalid(
                W3PlanDiagnostics.diagnostic(
                    W3PlanDiagnostics.SceneInvalid,
                    RenderDiagnosticDomain.SCENE,
                    "Clip bounds are non-finite",
                ),
            ) else integralRect(bounds)?.let(ClipRecognition::Accepted)
                ?: ClipRecognition.Gap(
                    W3PlanDiagnostics.diagnostic(
                        W3PlanDiagnostics.ClipNotPixelAligned,
                        RenderDiagnosticDomain.SCENE,
                        "Clip is not pixel aligned",
                    ),
                )
        }
        is ClipStackNode.Operations -> ClipRecognition.Gap(
            W3PlanDiagnostics.diagnostic(
                W3PlanDiagnostics.CommandNotMigrated,
                RenderDiagnosticDomain.SCENE,
                "Complex clips are outside W3",
            ),
        )
    }

    internal fun intersect(first: RectI32, second: RectI32): RectI32? = first.copy().takeIf { it.intersect(second) }

    internal fun integralRect(bounds: RectF32): RectI32? {
        if (!isFinite(bounds)) return null
        val values = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val converted = values.map { value ->
            val integral = value.toLong()
            if (integral.toFloat() != value || integral !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
            integral.toInt()
        }
        val result = RectI32(converted[0], converted[1], converted[2], converted[3])
        return result.takeUnless { it.isEmpty64() }
    }

    internal fun isFinite(bounds: RectF32): Boolean =
        listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all(Float::isFinite)

    internal fun isFinite(matrix: Matrix3x3F32): Boolean = listOf(
        matrix.sx, matrix.kx, matrix.tx, matrix.ky, matrix.sy, matrix.ty, matrix.persp0, matrix.persp1, matrix.persp2,
    ).all(Float::isFinite)

    internal fun premultiplied(color: ColorARGB, domain: CompositionDomain): ColorF32 =
        if (domain == CompositionDomain.LINEAR) {
            val alpha = color.alphaNormalized
            ColorF32.of(
                ColorTransferFunction.sRgb.toLinear(color.redNormalized) * alpha,
                ColorTransferFunction.sRgb.toLinear(color.greenNormalized) * alpha,
                ColorTransferFunction.sRgb.toLinear(color.blueNormalized) * alpha,
                alpha,
            )
        } else {
            val alpha = color.alphaNormalized
            ColorF32.of(color.redNormalized * alpha, color.greenNormalized * alpha, color.blueNormalized * alpha, alpha)
        }
}
