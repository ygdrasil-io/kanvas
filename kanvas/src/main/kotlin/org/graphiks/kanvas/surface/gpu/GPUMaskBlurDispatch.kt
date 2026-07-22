package org.graphiks.kanvas.surface.gpu

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.filters.BlurAxis
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlan
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlanner
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurRequest
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedBlurStyle
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedMaskFilter
import org.graphiks.kanvas.gpu.renderer.filters.MAX_MASK_BLUR_TAPS
import org.graphiks.kanvas.gpu.renderer.filters.blurKernelUniform
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.GPUColorFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode

internal const val MASK_BLUR_HORIZONTAL_MODULE_KEY = "mask-blur.horizontal.v1"
internal const val MASK_BLUR_VERTICAL_MODULE_KEY = "mask-blur.vertical.v1"

internal data class GPUMaskBlurDispatchResult(
    val rendered: Boolean,
)

internal fun GPUClipFacts.maskBlurClipBounds(targetWidth: Int, targetHeight: Int): GPUBounds =
    if (kind == GPUClipKind.DeviceRect) {
        bounds
    } else {
        GPUBounds(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat())
    }

internal fun GPUBackendOffscreenTarget.renderMaskBlurCommand(
    sourceTextureLabel: String,
    command: NormalizedDrawCommand,
    plan: MaskBlurPlan.Ready,
    sourceClearColor: GPUClearColor?,
    dispatched: MutableList<String>,
    diagnostics: Diagnostics,
    colorFormat: String,
    recordResult: Boolean = true,
): GPUMaskBlurDispatchResult {
    command.maskBlurPreflightRefusalReasonOrNull()?.let { reason ->
        diagnostics.fatal("refuse:${command.diagnosticName}", command.diagnosticName, reason)
        return GPUMaskBlurDispatchResult(rendered = false)
    }
    val material = command.material as? GPUMaterialDescriptor.SolidColor
        ?: run {
            diagnostics.fatal(
                "refuse:${command.diagnosticName}",
                command.diagnosticName,
                "unsupported.mask-filter.blur.material.${command.material.kind.name}",
            )
            return GPUMaskBlurDispatchResult(rendered = false)
        }

    val labelPrefix = "kanvas:mask-blur:${command.commandId.value}"
    val mask = createOffscreenTexture(
        GPUBackendOffscreenTexture("$labelPrefix:mask", plan.localWidth, plan.localHeight, "rgba8unorm"),
    )
    val horizontal = createOffscreenTexture(
        GPUBackendOffscreenTexture("$labelPrefix:horizontal", plan.localWidth, plan.localHeight, "rgba8unorm"),
    )
    val vertical = createOffscreenTexture(
        GPUBackendOffscreenTexture("$labelPrefix:vertical", plan.localWidth, plan.localHeight, "rgba8unorm"),
    )
    val styled = createOffscreenTexture(
        GPUBackendOffscreenTexture("$labelPrefix:styled", plan.localWidth, plan.localHeight, "rgba8unorm"),
    )
    val transparent = GPUClearColor(0.0, 0.0, 0.0, 0.0)
    val localCommand = command.toLocalMaskCommand(plan)
    val localConfig = RenderConfig(
        gpuColorFormat = GPUColorFormat.RGBA8_UNORM,
    )
    val transientLabels = listOf(mask, horizontal, vertical, styled)
    var renderingFailure: Throwable? = null
    try {
    val fatalBeforeMask = diagnostics.fatalCount
    encodeOffscreenTexture(mask, transparent) {
        when (localCommand) {
            is NormalizedDrawCommand.FillRect -> dispatchFillRect(
                localCommand,
                dispatched,
                diagnostics,
                plan.localWidth,
                plan.localHeight,
                localConfig,
                recordResult = false,
            )
            is NormalizedDrawCommand.FillPath -> dispatchFillPath(
                localCommand,
                dispatched,
                diagnostics,
                plan.localWidth,
                plan.localHeight,
                localConfig,
                recordResult = false,
            )
            is NormalizedDrawCommand.FillRRect -> dispatchFillRRect(
                localCommand,
                dispatched,
                diagnostics,
                plan.localWidth,
                plan.localHeight,
                localConfig,
                recordResult = false,
            )
            else -> error("Mask blur supports only fill rect, path, and rrect commands")
        }
    }
    if (diagnostics.fatalCount != fatalBeforeMask) return GPUMaskBlurDispatchResult(rendered = false)

    encodeOffscreenTexture(horizontal, transparent) {
        drawCompositePass(
            blurWgsl(BlurAxis.HORIZONTAL),
            "rgba8unorm",
            mask,
            listOf(blurUniformDraw(plan)),
        )
    }
    encodeOffscreenTexture(vertical, transparent) {
        drawCompositePass(
            blurWgsl(BlurAxis.VERTICAL),
            "rgba8unorm",
            horizontal,
            listOf(blurUniformDraw(plan)),
        )
    }
    encodeOffscreenTexture(styled, transparent) {
        drawBlendPass(
            MASK_BLUR_STYLE_WGSL,
            "rgba8unorm",
            vertical,
            mask,
            listOf(styleUniformDraw(plan.style, plan.localWidth, plan.localHeight)),
        )
    }
    encodeOffscreenTexture(sourceTextureLabel, sourceClearColor) {
        drawCompositePass(
            MASK_BLUR_SOLID_COMPOSITE_WGSL,
            colorFormat,
            styled,
            listOf(finalSolidUniformDraw(material, plan)),
            GPUBlendMode.SRC_OVER.canonicalFixedFunctionState(),
        )
    }

    if (recordResult) {
        dispatched.add(command.commandId.value.toString())
        diagnostics.degrade("dispatch:${command.diagnosticName}", command.diagnosticName, "dispatched")
    }
    return GPUMaskBlurDispatchResult(rendered = true)
    } catch (failure: Throwable) {
        renderingFailure = failure
        throw failure
    } finally {
        var releaseFailure: Throwable? = null
        transientLabels.forEach { label ->
            try {
                releaseOffscreenTexture(label)
            } catch (failure: Throwable) {
                val firstFailure = renderingFailure ?: releaseFailure
                if (firstFailure == null) {
                    releaseFailure = failure
                } else {
                    firstFailure.addSuppressed(failure)
                }
            }
        }
        if (renderingFailure == null) {
            releaseFailure?.let { throw it }
        }
    }
}

internal fun NormalizedDrawCommand.toMaskBlurRequest(
    targetWidth: Int,
    targetHeight: Int,
    maxTextureDimension2D: Int,
    config: RenderConfig,
): MaskBlurRequest {
    val blur = when (this) {
        is NormalizedDrawCommand.FillRect -> maskFilter
        is NormalizedDrawCommand.FillPath -> maskFilter
        is NormalizedDrawCommand.FillRRect -> maskFilter
        else -> null
    } as? NormalizedMaskFilter.Blur
        ?: error("Mask blur request requires a normalized blur mask filter")
    return MaskBlurRequest(
        bounds = bounds,
        clipBounds = clip.maskBlurClipBounds(targetWidth, targetHeight),
        targetWidth = targetWidth,
        targetHeight = targetHeight,
        style = blur.style,
        sigma = blur.sigma,
        maxTextureDimension2D = maxTextureDimension2D,
        maxIntermediateBytes = config.maxMaskBlurIntermediateBytes.toLong(),
    )
}

internal fun NormalizedDrawCommand.maskBlurPreflightRefusalReasonOrNull(): String? {
    val rrect = this as? NormalizedDrawCommand.FillRRect ?: return null
    val blur = rrect.maskFilter as? NormalizedMaskFilter.Blur ?: return null
    return if (blur.sigma != 0f) rrect.nonUniformRadiiRefusalReasonOrNull() else null
}

private fun NormalizedDrawCommand.toLocalMaskCommand(plan: MaskBlurPlan.Ready): NormalizedDrawCommand {
    val origin = plan.deviceBounds
    val localClip = GPUClipFacts.wideOpen(
        GPUBounds(0f, 0f, plan.localWidth.toFloat(), plan.localHeight.toFloat()),
    )
    val white = GPUMaterialDescriptor.SolidColor(1f, 1f, 1f, 1f)
    return when (this) {
        is NormalizedDrawCommand.FillRect -> copy(
            rect = rect.toLocal(origin, plan.scale),
            clip = localClip,
            material = white,
            blend = GPUBlendFacts.srcOver(),
            bounds = bounds.toLocal(origin, plan.scale),
            maskFilter = null,
        )
        is NormalizedDrawCommand.FillPath -> copy(
            tessellatedVertices = tessellatedVertices.mapIndexed { index, value ->
                if (index % 2 == 0) local(value, origin.left, plan.scale)
                else local(value, origin.top, plan.scale)
            },
            clip = localClip,
            material = white,
            blend = GPUBlendFacts.srcOver(),
            bounds = bounds.toLocal(origin, plan.scale),
            strokeWidth = strokeWidth * plan.scale,
            dashIntervals = dashIntervals?.map { it * plan.scale }?.toFloatArray(),
            dashPhase = dashPhase * plan.scale,
            maskFilter = null,
        )
        is NormalizedDrawCommand.FillRRect -> copy(
            rrect = rrect.toLocal(origin, plan.scale),
            clip = localClip,
            material = white,
            blend = GPUBlendFacts.srcOver(),
            bounds = bounds.toLocal(origin, plan.scale),
            maskFilter = null,
        )
        else -> error("Mask blur supports only fill rect, path, and rrect commands")
    }
}

private fun GPURect.toLocal(origin: GPUBounds, scale: Float): GPURect = GPURect(
    left = local(left, origin.left, scale),
    top = local(top, origin.top, scale),
    right = local(right, origin.left, scale),
    bottom = local(bottom, origin.top, scale),
)

private fun GPUBounds.toLocal(origin: GPUBounds, scale: Float): GPUBounds = GPUBounds(
    left = local(left, origin.left, scale),
    top = local(top, origin.top, scale),
    right = local(right, origin.left, scale),
    bottom = local(bottom, origin.top, scale),
)

private fun GPURRect.toLocal(origin: GPUBounds, scale: Float): GPURRect = GPURRect(
    rect = rect.toLocal(origin, scale),
    topLeft = topLeft.toLocal(scale),
    topRight = topRight.toLocal(scale),
    bottomRight = bottomRight.toLocal(scale),
    bottomLeft = bottomLeft.toLocal(scale),
)

private fun GPURRectCornerRadii.toLocal(scale: Float): GPURRectCornerRadii =
    GPURRectCornerRadii(x * scale, y * scale)

private fun local(value: Float, origin: Float, scale: Float): Float = (value - origin) * scale

private fun styleUniformDraw(
    style: NormalizedBlurStyle,
    localWidth: Int,
    localHeight: Int,
): GPUBackendRawUniformDraw =
    GPUBackendRawUniformDraw(
        uniformBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(
                when (style) {
                    NormalizedBlurStyle.NORMAL -> 0
                    NormalizedBlurStyle.SOLID -> 1
                    NormalizedBlurStyle.OUTER -> 2
                    NormalizedBlurStyle.INNER -> 3
                },
            )
        }.array(),
        scissorX = 0,
        scissorY = 0,
        scissorWidth = localWidth,
        scissorHeight = localHeight,
    )

/** Returns one of two static module sources; sigma is deliberately not an input. */
internal fun blurWgsl(axis: BlurAxis): String = when (axis) {
    BlurAxis.HORIZONTAL -> MASK_BLUR_HORIZONTAL_WGSL
    BlurAxis.VERTICAL -> MASK_BLUR_VERTICAL_WGSL
}

/** Pipeline/module identity has only the static horizontal and vertical axes. */
internal fun blurModuleKeysFor(plan: MaskBlurPlan): List<String> = when (plan) {
    is MaskBlurPlan.Ready -> listOf(MASK_BLUR_HORIZONTAL_MODULE_KEY, MASK_BLUR_VERTICAL_MODULE_KEY)
    MaskBlurPlan.Identity,
    is MaskBlurPlan.Refused,
    -> emptyList()
}

/** Packs the 144-byte parser-reflected `MaskBlurUniforms` layout. */
private fun blurUniformDraw(plan: MaskBlurPlan.Ready): GPUBackendRawUniformDraw {
    val kernel = blurKernelUniform(plan)
    return GPUBackendRawUniformDraw(
        uniformBytes = ByteBuffer.allocate(144).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(kernel.tapCount)
            putInt(0)
            putFloat(plan.localWidth.toFloat()); putFloat(plan.localHeight.toFloat())
            putFloat(0f); putFloat(0f); putFloat(0f); putFloat(0f)
            kernel.weights.forEach(::putFloat)
            repeat(28 - MAX_MASK_BLUR_TAPS) { putFloat(0f) }
        }.array(),
        scissorX = 0,
        scissorY = 0,
        scissorWidth = plan.localWidth,
        scissorHeight = plan.localHeight,
    )
}

private fun finalSolidUniformDraw(
    material: GPUMaterialDescriptor.SolidColor,
    plan: MaskBlurPlan.Ready,
): GPUBackendRawUniformDraw {
    val bounds = plan.deviceBounds
    val scissorX = floor(bounds.left).toInt().coerceAtLeast(0)
    val scissorY = floor(bounds.top).toInt().coerceAtLeast(0)
    return GPUBackendRawUniformDraw(
        uniformBytes = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(bounds.left)
            putFloat(bounds.top)
            putFloat(bounds.right)
            putFloat(bounds.bottom)
            putFloat(srgbToLinear(material.r) * material.a)
            putFloat(srgbToLinear(material.g) * material.a)
            putFloat(srgbToLinear(material.b) * material.a)
            putFloat(material.a)
        }.array(),
        scissorX = scissorX,
        scissorY = scissorY,
        scissorWidth = (ceil(bounds.right).toInt() - scissorX).coerceAtLeast(1),
        scissorHeight = (ceil(bounds.bottom).toInt() - scissorY).coerceAtLeast(1),
    )
}
