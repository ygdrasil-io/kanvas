package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderConfig

internal val IMAGE_TEXTURE_WGSL: String = """
struct Uniforms {
    dstRect: vec4f,
    srcRect: vec4f,
    texSize: vec4f,
    tintColor: vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(1) @binding(1) var imageTex: texture_2d<f32>;
@group(1) @binding(2) var imageSam: sampler;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let dstPos = pos.xy - uniforms.dstRect.xy;
    let dstSize = uniforms.dstRect.zw - uniforms.dstRect.xy;
    let uv = dstPos / dstSize;
    let mappedUv = uv * (uniforms.srcRect.zw - uniforms.srcRect.xy) / uniforms.texSize.xy + uniforms.srcRect.xy / uniforms.texSize.xy;
    let c = textureSample(imageTex, imageSam, mappedUv) * uniforms.tintColor;
    return vec4f(c.rgb * c.a, c.a);
}
""".trimIndent()

internal fun GPUBackendRenderRecorder.dispatchFillImage(
    cmd: NormalizedDrawCommand.DrawImageRect,
    imagePixels: ByteArray?,
    imageWidth: Int,
    imageHeight: Int,
    dispatched: MutableList<String>,
    diagnostics: Diagnostics,
    surfaceWidth: Int,
    surfaceHeight: Int,
    config: RenderConfig,
) {
    val src = cmd.src
    val dst = cmd.dst
    val clipBounds = cmd.clip.bounds
    val sx = maxOf(dst.left, clipBounds.left).toInt().coerceIn(0, surfaceWidth - 1)
    val sy = maxOf(dst.top, clipBounds.top).toInt().coerceIn(0, surfaceHeight - 1)
    val sw = (minOf(dst.right, clipBounds.right).toInt() - sx).coerceIn(1, surfaceWidth - sx)
    val sh = (minOf(dst.bottom, clipBounds.bottom).toInt() - sy).coerceIn(1, surfaceHeight - sy)

    if (imagePixels != null && imagePixels.isNotEmpty() && imageWidth > 0 && imageHeight > 0) {
        val bb = java.nio.ByteBuffer.allocate(64).order(java.nio.ByteOrder.nativeOrder())
        bb.putFloat(dst.left); bb.putFloat(dst.top); bb.putFloat(dst.right); bb.putFloat(dst.bottom)
        bb.putFloat(src.left); bb.putFloat(src.top); bb.putFloat(src.right); bb.putFloat(src.bottom)
        bb.putFloat(imageWidth.toFloat()); bb.putFloat(imageHeight.toFloat()); bb.putFloat(0f); bb.putFloat(0f)
        bb.putFloat(1f); bb.putFloat(1f); bb.putFloat(1f); bb.putFloat(1f)

        drawFullscreenTextureUniformPass(
            wgsl = IMAGE_TEXTURE_WGSL,
            colorFormat = config.gpuColorFormat.wgpuLabel,
            textureRgba = imagePixels,
            textureWidth = imageWidth,
            textureHeight = imageHeight,
            textureFormat = "rgba8unorm",
            draws = listOf(
                GPUBackendRawUniformDraw(
                    uniformBytes = bb.array(),
                    scissorX = sx, scissorY = sy,
                    scissorWidth = sw, scissorHeight = sh,
                ),
            ),
            blendMode = cmd.blend.blendMode,
        )
    } else {
        val hash = cmd.imageSourceId.hashCode()
        val r = ((hash shr 16) and 0xFF).toFloat() / 255f
        val g = ((hash shr 8) and 0xFF).toFloat() / 255f
        val b = (hash and 0xFF).toFloat() / 255f
        val rgba = floatArrayOf(r * 0.5f, g * 0.5f, b * 0.5f, 0.5f)
        drawFullscreenPass(
            wgsl = SOLID_RECT_WGSL,
            colorFormat = config.gpuColorFormat.wgpuLabel,
            draws = listOf(
                GPUBackendRectDraw(
                    rgbaPremul = rgba,
                    scissorX = sx, scissorY = sy,
                    scissorWidth = sw, scissorHeight = sh,
                ),
            ),
        )
    }

    dispatched.add(cmd.commandId.toString())
    diagnostics.degrade("dispatch:image:${cmd.commandId.value}", "drawImage", "dispatched")
}
