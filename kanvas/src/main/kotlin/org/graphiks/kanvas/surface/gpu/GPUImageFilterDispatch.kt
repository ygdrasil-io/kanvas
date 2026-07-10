package org.graphiks.kanvas.surface.gpu

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import org.graphiks.kanvas.gpu.renderer.commands.GPUImageFilterPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.filters.BlurAxis
import org.graphiks.kanvas.gpu.renderer.filters.generateBlurPassWgsl
import org.graphiks.kanvas.surface.Diagnostics

internal data class GPUImageFilterDispatchResult(
    val rendered: Boolean,
)

/**
 * Executes the accepted single-node image blur route in local coordinates.
 *
 * The source image is tinted exactly once while drawn into the transparent
 * source texture. The blur passes only sample premultiplied intermediate
 * textures; the final composite maps that local texture back to scene
 * coordinates and uses SrcOver.
 */
internal fun GPUBackendOffscreenTarget.renderImageCommand(
    sceneTextureLabel: String,
    command: NormalizedDrawCommand.DrawImageRect,
    textureCache: Map<String, ByteArray>,
    sceneClearColor: GPUClearColor?,
    dispatched: MutableList<String> = mutableListOf(),
    diagnostics: Diagnostics = Diagnostics(),
    colorFormat: String = "rgba8unorm",
): GPUImageFilterDispatchResult {
    val plan = command.imageFilterPlan as? GPUImageFilterPlan.Blur
        ?: return GPUImageFilterDispatchResult(rendered = false)
    val outputWidth = ceil(plan.outputBounds.right - plan.outputBounds.left).toInt()
    val outputHeight = ceil(plan.outputBounds.bottom - plan.outputBounds.top).toInt()
    if (outputWidth <= 0 || outputHeight <= 0) return GPUImageFilterDispatchResult(rendered = false)

    val labelPrefix = "kanvas:image-filter:${command.commandId.value}"
    val sourceLabel = createOffscreenTexture(
        GPUBackendOffscreenTexture("$labelPrefix:source", outputWidth, outputHeight, colorFormat),
    )
    val horizontalLabel = createOffscreenTexture(
        GPUBackendOffscreenTexture("$labelPrefix:horizontal", outputWidth, outputHeight, colorFormat),
    )
    val verticalLabel = createOffscreenTexture(
        GPUBackendOffscreenTexture("$labelPrefix:vertical", outputWidth, outputHeight, colorFormat),
    )
    val transparent = GPUClearColor(0.0, 0.0, 0.0, 0.0)
    val pixels = textureCache[command.imageSourceId]
    if (pixels == null) {
        diagnostics.fatal("refuse:${command.diagnosticName}", command.diagnosticName, "texture_not_found:${command.imageSourceId}")
        return GPUImageFilterDispatchResult(rendered = false)
    }
    if (command.transform.type != GPUTransformType.Identity) {
        diagnostics.fatal(
            "refuse:${command.diagnosticName}",
            command.diagnosticName,
            "unsupported_transform:${command.transform.type.name}",
        )
        return GPUImageFilterDispatchResult(rendered = false)
    }
    val localDst = GPURect(
        left = command.dst.left - plan.outputBounds.left,
        top = command.dst.top - plan.outputBounds.top,
        right = command.dst.right - plan.outputBounds.left,
        bottom = command.dst.bottom - plan.outputBounds.top,
    )
    val fatalBeforeSource = diagnostics.fatalCount
    encodeOffscreenTexture(sourceLabel, transparent) {
        textureDimensionsRefusalReasonOrNull(command.pixelsWidth, command.pixelsHeight)?.let { reason ->
            diagnostics.fatal("refuse:${command.diagnosticName}", command.diagnosticName, reason)
            return@encodeOffscreenTexture
        }
        // The dedicated filtered source shader clamps the generated UV to the
        // command crop, not the full decoded texture, before sampling its halo.
        drawFullscreenTextureUniformPass(
            wgsl = FILTERED_IMAGE_SOURCE_WGSL,
            colorFormat = colorFormat,
            textureRgba = pixels,
            textureWidth = command.pixelsWidth,
            textureHeight = command.pixelsHeight,
            textureFormat = "rgba8unorm",
            draws = listOf(
                GPUBackendRawUniformDraw(
                    uniformBytes = imageTextureUniformBytes(command, localDst),
                    scissorX = 0,
                    scissorY = 0,
                    scissorWidth = outputWidth,
                    scissorHeight = outputHeight,
                ),
            ),
        )
    }
    if (diagnostics.fatalCount != fatalBeforeSource) return GPUImageFilterDispatchResult(rendered = false)

    val fullLocalDraw = GPUBackendRawUniformDraw(
        uniformBytes = ByteArray(16),
        scissorX = 0,
        scissorY = 0,
        scissorWidth = outputWidth,
        scissorHeight = outputHeight,
    )
    encodeOffscreenTexture(horizontalLabel, transparent) {
        drawCompositePass(
            wgsl = generateBlurPassWgsl(BlurAxis.HORIZONTAL, plan.sigmaX),
            colorFormat = colorFormat,
            textureLabel = sourceLabel,
            draws = listOf(fullLocalDraw),
        )
    }
    encodeOffscreenTexture(verticalLabel, transparent) {
        drawCompositePass(
            wgsl = generateBlurPassWgsl(BlurAxis.VERTICAL, plan.sigmaY),
            colorFormat = colorFormat,
            textureLabel = horizontalLabel,
            draws = listOf(fullLocalDraw),
        )
    }

    val sceneWidth = ceil(plan.outputBounds.right - plan.outputBounds.left).toInt()
    val sceneHeight = ceil(plan.outputBounds.bottom - plan.outputBounds.top).toInt()
    encodeOffscreenTexture(sceneTextureLabel, sceneClearColor) {
        drawCompositePass(
            wgsl = FILTERED_IMAGE_COMPOSITE_WGSL,
            colorFormat = colorFormat,
            textureLabel = verticalLabel,
            draws = listOf(
                GPUBackendRawUniformDraw(
                    uniformBytes = filteredImageCompositeUniformBytes(plan.outputBounds, outputWidth, outputHeight),
                    scissorX = plan.outputBounds.left.toInt(),
                    scissorY = plan.outputBounds.top.toInt(),
                    scissorWidth = sceneWidth,
                    scissorHeight = sceneHeight,
                ),
            ),
            blendMode = GPUBlendMode.SRC_OVER,
        )
    }
    if (diagnostics.fatalCount != fatalBeforeSource) return GPUImageFilterDispatchResult(rendered = false)
    dispatched.add(command.commandId.toString())
    return GPUImageFilterDispatchResult(rendered = true)
}

private fun filteredImageCompositeUniformBytes(
    outputBounds: GPURect,
    localWidth: Int,
    localHeight: Int,
): ByteArray = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).apply {
    putFloat(outputBounds.left)
    putFloat(outputBounds.top)
    putFloat(outputBounds.right)
    putFloat(outputBounds.bottom)
    putFloat(localWidth.toFloat())
    putFloat(localHeight.toFloat())
    putFloat(0f)
    putFloat(0f)
}.array()

private fun imageTextureUniformBytes(
    command: NormalizedDrawCommand.DrawImageRect,
    dst: GPURect,
): ByteArray {
    val imageWidth = command.pixelsWidth.toFloat().coerceAtLeast(1f)
    val imageHeight = command.pixelsHeight.toFloat().coerceAtLeast(1f)
    val uvScaleX = (command.src.right - command.src.left) / imageWidth
    val uvScaleY = (command.src.bottom - command.src.top) / imageHeight
    val uvOffsetX = command.src.left / imageWidth
    val uvOffsetY = command.src.top / imageHeight
    val material = command.material as? GPUMaterialDescriptor.ImageDraw
    return ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
        putFloat(dst.left).putFloat(dst.top).putFloat(dst.right).putFloat(dst.bottom)
        putFloat(uvScaleX).putFloat(uvScaleY)
        putFloat(uvOffsetX).putFloat(uvOffsetY)
        putFloat(material?.tintR ?: 1f)
        putFloat(material?.tintG ?: 1f)
        putFloat(material?.tintB ?: 1f)
        putFloat(material?.tintA ?: 1f)
    }.array()
}
