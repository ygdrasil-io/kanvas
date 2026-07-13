package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSimplePassBatchKind
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.gpu.renderer.materials.GradientWgslShaderProvider
import org.graphiks.kanvas.surface.RenderConfig

internal fun GPUBackendRenderRecorder.dispatchFillRect(
    cmd: NormalizedDrawCommand.FillRect,
    dispatched: MutableList<String>,
    diagnostics: Diagnostics,
    surfaceWidth: Int,
    surfaceHeight: Int,
    config: RenderConfig,
    recordResult: Boolean = true,
    /** Emits unmodulated S while the sibling coverage pass emits G. */
    uncoveredSourceColor: Boolean = false,
) {
    fun refuse(reason: String) {
        diagnostics.fatal("refuse:${cmd.diagnosticName}", cmd.diagnosticName, reason)
    }

    cmd.fillGuardRefusalReasonOrNull()?.let { refuse(it); return }

    val blendMode = cmd.blend.blendMode
    val rect = cmd.rect
    val clipBounds = cmd.clip.bounds
    val coverageBounds = GPUBounds(
        left = maxOf(rect.left, clipBounds.left),
        top = maxOf(rect.top, clipBounds.top),
        right = minOf(rect.right, clipBounds.right),
        bottom = minOf(rect.bottom, clipBounds.bottom),
    )
    val bounds = GPUBounds(rect.left, rect.top, rect.right, rect.bottom)
    val coverageScissor = if (cmd.antiAlias && cmd.material is GPUMaterialDescriptor.SolidColor) {
        coverageScissor(bounds, clipBounds, surfaceWidth, surfaceHeight) ?: return
    } else {
        null
    }
    val truncatedScissor = if (coverageScissor == null) {
        truncatedScissor(bounds, clipBounds, surfaceWidth, surfaceHeight) ?: return
    } else {
        null
    }
    val scissor = coverageScissor ?: truncatedScissor!!
    val sx = scissor.x
    val sy = scissor.y
    val sw = scissor.width
    val sh = scissor.height

    when (val material = cmd.material) {
        is GPUMaterialDescriptor.SolidColor -> {
            if (cmd.antiAlias) {
                val aaBb = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.nativeOrder())
                aaBb.putFloat(coverageBounds.left); aaBb.putFloat(coverageBounds.top)
                aaBb.putFloat(coverageBounds.right); aaBb.putFloat(coverageBounds.bottom)
                aaBb.putFloat(srgbToLinear(material.r) * material.a)
                aaBb.putFloat(srgbToLinear(material.g) * material.a)
                aaBb.putFloat(srgbToLinear(material.b) * material.a)
                aaBb.putFloat(material.a)
                aaBb.putInt(1) // antiAlias = true (u32)
                aaBb.putFloat(0f); aaBb.putFloat(0f); aaBb.putFloat(0f) // padding to 48 bytes
                drawFullscreenRawUniformPass(
                    wgsl = if (uncoveredSourceColor) RECT_AA_SOURCE_COLOR_WGSL else RECT_AA_WGSL,
                    colorFormat = config.gpuColorFormat.gpuLabel,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = aaBb.array(),
                            scissorX = sx, scissorY = sy,
                            scissorWidth = sw, scissorHeight = sh,
                        ),
                    ),
                )
            } else {
                val rgba = floatArrayOf(
                    srgbToLinear(material.r) * material.a,
                    srgbToLinear(material.g) * material.a,
                    srgbToLinear(material.b) * material.a,
                    material.a,
                )
                drawFullscreenPass(
                    wgsl = SOLID_RECT_WGSL,
                    colorFormat = config.gpuColorFormat.gpuLabel,
                    draws = listOf(
                        GPUBackendRectDraw(
                            rgbaPremul = rgba,
                            scissorX = sx, scissorY = sy,
                            scissorWidth = sw, scissorHeight = sh,
                        ),
                    ),
                    blendMode = blendMode,
                    passBatchKind = GPUBackendSimplePassBatchKind.SolidFill,
                )
            }
        }
        is GPUMaterialDescriptor.LinearGradient -> {
            if (material.snippetSourceHash != null) {
                val shader = GradientWgslShaderProvider.shaderFor(material)!!
                val uniformBytes = GradientWgslShaderProvider.uniformBytesFor(material)!!
                drawFullscreenRawUniformPass(
                    wgsl = shader.wgslSource,
                    colorFormat = config.gpuColorFormat.gpuLabel,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = uniformBytes,
                            scissorX = sx, scissorY = sy,
                            scissorWidth = sw, scissorHeight = sh,
                        ),
                    ),
                )
            } else {
                val multiStop = material.allStopPositions != null && material.allStopPositions!!.size > 2
                if (multiStop) {
                    val n = material.allStopPositions!!.size.coerceAtMost(8)
                    val bb = java.nio.ByteBuffer.allocate(8224).order(java.nio.ByteOrder.nativeOrder())
                    bb.putFloat(material.startX); bb.putFloat(material.startY)
                    bb.putFloat(material.endX); bb.putFloat(material.endY)
                    bb.putInt(n)
                    bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                    for (i in 0 until 256) {
                        if (i < n) {
                            val pos = material.allStopPositions!!.getOrElse(i) { i.toFloat() / (n - 1).coerceAtLeast(1) }
                            bb.putFloat(pos); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                            if (material.allStopColors != null && i * 4 + 3 < material.allStopColors!!.size) {
                                bb.putFloat(srgbToLinear(material.allStopColors!![i * 4]) * material.allStopColors!![i * 4 + 3])
                                bb.putFloat(srgbToLinear(material.allStopColors!![i * 4 + 1]) * material.allStopColors!![i * 4 + 3])
                                bb.putFloat(srgbToLinear(material.allStopColors!![i * 4 + 2]) * material.allStopColors!![i * 4 + 3])
                                bb.putFloat(material.allStopColors!![i * 4 + 3])
                            } else {
                                bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                            }
                        } else {
                            bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                            bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                        }
                    }
                    drawFullscreenRawUniformPass(
                        wgsl = LINEAR_GRADIENT_MULTI_WGSL,
                        colorFormat = config.gpuColorFormat.gpuLabel,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = bb.array(),
                                scissorX = sx, scissorY = sy,
                                scissorWidth = sw, scissorHeight = sh,
                            ),
                        ),
                        blendMode = blendMode,
                    )
                } else {
                    val bb = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.nativeOrder())
                    bb.putFloat(material.startX); bb.putFloat(material.startY)
                    bb.putFloat(material.endX); bb.putFloat(material.endY)
                    bb.putFloat(srgbToLinear(material.startR) * material.startA)
                    bb.putFloat(srgbToLinear(material.startG) * material.startA)
                    bb.putFloat(srgbToLinear(material.startB) * material.startA)
                    bb.putFloat(material.startA)
                    bb.putFloat(srgbToLinear(material.endR) * material.endA)
                    bb.putFloat(srgbToLinear(material.endG) * material.endA)
                    bb.putFloat(srgbToLinear(material.endB) * material.endA)
                    bb.putFloat(material.endA)
                    drawFullscreenRawUniformPass(
                        wgsl = LINEAR_GRADIENT_WGSL,
                        colorFormat = config.gpuColorFormat.gpuLabel,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = bb.array(),
                                scissorX = sx, scissorY = sy,
                                scissorWidth = sw, scissorHeight = sh,
                            ),
                        ),
                        blendMode = blendMode,
                        passBatchKind = GPUBackendSimplePassBatchKind.SimpleGradient,
                    )
                }
            }
        }
        is GPUMaterialDescriptor.RadialGradient -> {
            if (material.snippetSourceHash != null) {
                val shader = GradientWgslShaderProvider.shaderFor(material)!!
                val uniformBytes = GradientWgslShaderProvider.uniformBytesFor(material)!!
                drawFullscreenRawUniformPass(
                    wgsl = shader.wgslSource,
                    colorFormat = config.gpuColorFormat.gpuLabel,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = uniformBytes,
                            scissorX = sx, scissorY = sy,
                            scissorWidth = sw, scissorHeight = sh,
                        ),
                    ),
                )
            } else {
                val multiStop = material.allStopPositions != null && material.allStopPositions!!.size > 2
                if (multiStop) {
                    val n = material.allStopPositions!!.size.coerceAtMost(256)
                    val bb = java.nio.ByteBuffer.allocate(8224).order(java.nio.ByteOrder.nativeOrder())
                    bb.putFloat(material.centerX); bb.putFloat(material.centerY)
                    bb.putFloat(material.radius)
                    bb.putInt(n)
                    for (i in 0 until 512) {
                        if (i < n * 2) {
                            if (i % 2 == 0) {
                                val pos = material.allStopPositions!!.getOrElse(i / 2) { (i / 2).toFloat() / (n - 1).coerceAtLeast(1) }
                                bb.putFloat(pos); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                            } else {
                                val ci = i / 2
                                if (material.allStopColors != null && ci * 4 + 3 < material.allStopColors!!.size) {
                                    bb.putFloat(srgbToLinear(material.allStopColors!![ci * 4]) * material.allStopColors!![ci * 4 + 3])
                                    bb.putFloat(srgbToLinear(material.allStopColors!![ci * 4 + 1]) * material.allStopColors!![ci * 4 + 3])
                                    bb.putFloat(srgbToLinear(material.allStopColors!![ci * 4 + 2]) * material.allStopColors!![ci * 4 + 3])
                                    bb.putFloat(material.allStopColors!![ci * 4 + 3])
                                } else {
                                    bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                                }
                            }
                        } else {
                            bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                        }
                    }
                    drawFullscreenRawUniformPass(
                        wgsl = RADIAL_GRADIENT_MULTI_WGSL,
                        colorFormat = config.gpuColorFormat.gpuLabel,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = bb.array(),
                                scissorX = sx, scissorY = sy,
                                scissorWidth = sw, scissorHeight = sh,
                            ),
                        ),
                        blendMode = blendMode,
                    )
                } else {
                    val bb = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.nativeOrder())
                    bb.putFloat(material.centerX); bb.putFloat(material.centerY)
                    bb.putFloat(material.radius)
                    bb.putFloat(0f)
                    bb.putFloat(srgbToLinear(material.startR) * material.startA)
                    bb.putFloat(srgbToLinear(material.startG) * material.startA)
                    bb.putFloat(srgbToLinear(material.startB) * material.startA)
                    bb.putFloat(material.startA)
                    bb.putFloat(srgbToLinear(material.endR) * material.endA)
                    bb.putFloat(srgbToLinear(material.endG) * material.endA)
                    bb.putFloat(srgbToLinear(material.endB) * material.endA)
                    bb.putFloat(material.endA)
                    drawFullscreenRawUniformPass(
                        wgsl = RADIAL_GRADIENT_WGSL,
                        colorFormat = config.gpuColorFormat.gpuLabel,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = bb.array(),
                                scissorX = sx, scissorY = sy,
                                scissorWidth = sw, scissorHeight = sh,
                            ),
                        ),
                        blendMode = blendMode,
                    )
                }
            }
        }
        is GPUMaterialDescriptor.SweepGradient -> {
            if (material.snippetSourceHash != null) {
                val shader = GradientWgslShaderProvider.shaderFor(material)!!
                val uniformBytes = GradientWgslShaderProvider.uniformBytesFor(material)!!
                drawFullscreenRawUniformPass(
                    wgsl = shader.wgslSource,
                    colorFormat = config.gpuColorFormat.gpuLabel,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = uniformBytes,
                            scissorX = sx, scissorY = sy,
                            scissorWidth = sw, scissorHeight = sh,
                        ),
                    ),
                )
            } else {
                val multiStop = material.allStopPositions != null && material.allStopPositions!!.size > 2
                if (multiStop) {
                    val n = material.allStopPositions!!.size.coerceAtMost(8)
                    val bb = java.nio.ByteBuffer.allocate(8224).order(java.nio.ByteOrder.nativeOrder())
                    bb.putFloat(material.centerX); bb.putFloat(material.centerY)
                    bb.putFloat(material.startAngle); bb.putFloat(material.endAngle)
                    bb.putInt(n)
                    bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                    for (i in 0 until 256) {
                        if (i < n) {
                            val pos = material.allStopPositions!!.getOrElse(i) { i.toFloat() / (n - 1).coerceAtLeast(1) }
                            bb.putFloat(pos); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                            if (material.allStopColors != null && i * 4 + 3 < material.allStopColors!!.size) {
                                bb.putFloat(srgbToLinear(material.allStopColors!![i * 4]) * material.allStopColors!![i * 4 + 3])
                                bb.putFloat(srgbToLinear(material.allStopColors!![i * 4 + 1]) * material.allStopColors!![i * 4 + 3])
                                bb.putFloat(srgbToLinear(material.allStopColors!![i * 4 + 2]) * material.allStopColors!![i * 4 + 3])
                                bb.putFloat(material.allStopColors!![i * 4 + 3])
                            } else {
                                bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                            }
                        } else {
                            bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                            bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                        }
                    }
                    drawFullscreenRawUniformPass(
                        wgsl = SWEEP_GRADIENT_MULTI_WGSL,
                        colorFormat = config.gpuColorFormat.gpuLabel,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = bb.array(),
                                scissorX = sx, scissorY = sy,
                                scissorWidth = sw, scissorHeight = sh,
                            ),
                        ),
                        blendMode = blendMode,
                    )
                } else {
                    val bb = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.nativeOrder())
                    bb.putFloat(material.centerX); bb.putFloat(material.centerY)
                    bb.putFloat(material.startAngle); bb.putFloat(material.endAngle)
                    bb.putFloat(srgbToLinear(material.startR) * material.startA)
                    bb.putFloat(srgbToLinear(material.startG) * material.startA)
                    bb.putFloat(srgbToLinear(material.startB) * material.startA)
                    bb.putFloat(material.startA)
                    bb.putFloat(srgbToLinear(material.endR) * material.endA)
                    bb.putFloat(srgbToLinear(material.endG) * material.endA)
                    bb.putFloat(srgbToLinear(material.endB) * material.endA)
                    bb.putFloat(material.endA)
                    drawFullscreenRawUniformPass(
                        wgsl = SWEEP_GRADIENT_WGSL,
                        colorFormat = config.gpuColorFormat.gpuLabel,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = bb.array(),
                                scissorX = sx, scissorY = sy,
                                scissorWidth = sw, scissorHeight = sh,
                            ),
                        ),
                        blendMode = blendMode,
                    )
                }
            }
        }
        is GPUMaterialDescriptor.ConicalGradient -> {
            if (material.snippetSourceHash != null) {
                val shader = GradientWgslShaderProvider.shaderFor(material)!!
                val uniformBytes = GradientWgslShaderProvider.uniformBytesFor(material)!!
                drawFullscreenRawUniformPass(
                    wgsl = shader.wgslSource,
                    colorFormat = config.gpuColorFormat.gpuLabel,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = uniformBytes,
                            scissorX = sx, scissorY = sy,
                            scissorWidth = sw, scissorHeight = sh,
                        ),
                    ),
                )
            } else {
                refuse("unsupported_material:conical_gradient_fallback")
            }
        }
        is GPUMaterialDescriptor.ImageDraw -> {
            if (material.rgbaPixels.isEmpty()) {
                refuse("unsupported_material:image_draw_missing_pixels")
                return
            }
            textureDimensionsRefusalReasonOrNull(material.imageWidth, material.imageHeight)?.let { reason ->
                refuse(reason)
                return
            }
            val iw = material.imageWidth.toFloat().coerceAtLeast(1f)
            val ih = material.imageHeight.toFloat().coerceAtLeast(1f)
            val uvScaleX = (rect.right - rect.left) / iw
            val uvScaleY = (rect.bottom - rect.top) / ih
            val bb = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            bb.putFloat(rect.left).putFloat(rect.top).putFloat(rect.right).putFloat(rect.bottom)
            bb.putFloat(uvScaleX).putFloat(uvScaleY)
            bb.putFloat(0f).putFloat(0f)
            bb.putFloat(material.tintR)
            bb.putFloat(material.tintG)
            bb.putFloat(material.tintB)
            bb.putFloat(material.tintA)
            drawFullscreenTextureUniformPass(
                wgsl = IMAGE_TEXTURE_WGSL,
                colorFormat = config.gpuColorFormat.gpuLabel,
                textureRgba = material.rgbaPixels,
                textureWidth = material.imageWidth,
                textureHeight = material.imageHeight,
                textureFormat = "rgba8unorm",
                draws = listOf(
                    GPUBackendRawUniformDraw(
                        uniformBytes = bb.array(),
                        scissorX = sx,
                        scissorY = sy,
                        scissorWidth = sw,
                        scissorHeight = sh,
                    ),
                ),
                blendMode = blendMode,
            )
        }
        is GPUMaterialDescriptor.BlendShader -> {
            if (material.wgslCombined.isNotBlank()) {
                val imageChild = when {
                    material.dst is GPUMaterialDescriptor.ImageDraw -> material.dst as GPUMaterialDescriptor.ImageDraw
                    material.src is GPUMaterialDescriptor.ImageDraw -> material.src as GPUMaterialDescriptor.ImageDraw
                    else -> null
                }
                if (imageChild != null && imageChild.rgbaPixels.isNotEmpty()) {
                    textureDimensionsRefusalReasonOrNull(imageChild.imageWidth, imageChild.imageHeight)?.let { reason ->
                        refuse(reason)
                        return
                    }
                    drawFullscreenTextureUniformPass(
                        wgsl = material.wgslCombined,
                        colorFormat = config.gpuColorFormat.gpuLabel,
                        textureRgba = imageChild.rgbaPixels,
                        textureWidth = imageChild.imageWidth,
                        textureHeight = imageChild.imageHeight,
                        textureFormat = "RGBA8Unorm",
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = material.uniformBytes,
                                scissorX = sx, scissorY = sy,
                                scissorWidth = sw, scissorHeight = sh,
                            ),
                        ),
                    )
                } else {
                    drawFullscreenRawUniformPass(
                        wgsl = material.wgslCombined,
                        colorFormat = config.gpuColorFormat.gpuLabel,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = material.uniformBytes,
                                scissorX = sx, scissorY = sy,
                                scissorWidth = sw, scissorHeight = sh,
                            ),
                        ),
                    )
                }
            } else {
                refuse("unsupported_material:blend_shader")
            }
        }
        else -> {
            refuse("unsupported_material:${material.kind.name}")
            return
        }
    }
    if (recordResult) {
        dispatched.add(cmd.commandId.toString())
        diagnostics.degrade("dispatch:${cmd.diagnosticName}", cmd.diagnosticName, "dispatched")
    }
}
