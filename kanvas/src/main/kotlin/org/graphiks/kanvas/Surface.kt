package org.graphiks.kanvas

import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendKind
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerScopeKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilMode
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendTriangleData
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecording
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID

private const val GPU_COLOR_FORMAT: String = "rgba8unorm"

private val SOLID_RECT_WGSL: String = """
    struct Uniforms {
        color: vec4f,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main() -> @location(0) vec4f {
        return uniforms.color;
    }
""".trimIndent()

private val RRECT_WGSL: String = """
    struct Uniforms {
        bounds: vec4f,
        radii: vec4f,
        color: vec4f,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;

    fn rrect_cov(p: vec2f, bounds: vec4f, rx_in: f32, ry_in: f32) -> f32 {
        let centre = vec2f(0.5 * (bounds.x + bounds.z), 0.5 * (bounds.y + bounds.w));
        let half = vec2f(0.5 * (bounds.z - bounds.x), 0.5 * (bounds.w - bounds.y));
        let rx = max(rx_in, 1e-4);
        let ry = max(ry_in, 1e-4);
        let q_abs = abs(p - centre);
        let q = q_abs - (half - vec2f(rx, ry));
        let inner_rect_sdf = max(q.x, q.y);
        let outer_rect_sdf = max(q_abs.x - half.x, q_abs.y - half.y);
        let qm = max(q, vec2f(0.0, 0.0));
        let n = vec2f(qm.x / rx, qm.y / ry);
        let nl = length(n);
        let nl_safe = max(nl, 1e-6);
        let dir = n / nl_safe;
        let effective_r = length(vec2f(rx * dir.x, ry * dir.y));
        let corner_sdf = (nl - 1.0) * effective_r;
        let in_corner_band = step(0.0, q.x) * step(0.0, q.y);
        let band_sdf = mix(outer_rect_sdf, corner_sdf, in_corner_band);
        let final_sdf = band_sdf;
        return clamp(0.5 - final_sdf, 0.0, 1.0);
    }

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let cov = rrect_cov(coord.xy, uniforms.bounds, uniforms.radii.x, uniforms.radii.y);
        return vec4f(uniforms.color.rgb * cov, uniforms.color.a * cov);
    }
""".trimIndent()

data class SurfaceRenderResult(
    val rgba: ByteArray,
    val dispatchedCount: Int,
    val refusedCount: Int,
    val diagnostics: List<String>,
) {
    val nonTransparentPixels: Int get() {
        var count = 0
        var i = 3
        while (i < rgba.size) {
            if (rgba[i].toInt() and 0xFF > 0) count++
            i += 4
        }
        return count
    }
}

class Surface(
    val width: Int,
    val height: Int,
    val format: PixelFormat = PixelFormat.RGBA8,
) {
    internal val recorder: GPURecorder = GPURecorder(
        recordingId = GPURecordingID("kanvas-surface-${System.identityHashCode(this)}"),
    )

    internal val targetFacts: GPUTargetFacts = GPUTargetFacts(
        width = width,
        height = height,
        colorFormat = format.label,
    )

    fun flush(): Frame {
        val recording: GPURecording = recorder.close()
        return Frame(recording = recording)
    }

    fun renderToRgba(): SurfaceRenderResult {
        flush()
        val commands = recorder.recordedCommands()
        if (commands.isEmpty()) error("No commands recorded on this surface")

        val session = GPUBackendRuntimeFactory.createOrNull()
            ?: error("webgpu-context-unavailable")
        session.use { s ->
            val target = s.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = width,
                    height = height,
                    colorFormat = GPU_COLOR_FORMAT,
                ),
            )
            target.use { t ->
                val dispatched = mutableListOf<String>()
                val diagnostics = mutableListOf<String>()

                t.encode(clearColor = DEFAULT_CLEAR_COLOR) {
                    for (cmd in commands) {
                        when (cmd) {
                            is NormalizedDrawCommand.FillRect -> dispatchFillRect(cmd, dispatched, diagnostics)
                            is NormalizedDrawCommand.FillRRect -> dispatchFillRRect(cmd, dispatched, diagnostics)
                            is NormalizedDrawCommand.FillPath -> dispatchFillPath(cmd, dispatched, diagnostics)
                            is NormalizedDrawCommand.DrawTextRun -> dispatchDrawTextRun(cmd, dispatched, diagnostics)
                        }
                    }
                }

                if (dispatched.isEmpty() && diagnostics.isNotEmpty()) {
                    error("All commands refused: ${diagnostics.joinToString("; ")}")
                }

                val rgba = t.readRgba()
                return SurfaceRenderResult(
                    rgba = rgba,
                    dispatchedCount = dispatched.size,
                    refusedCount = diagnostics.size - dispatched.size,
                    diagnostics = diagnostics,
                )
            }
        }
    }

    private fun GPUBackendRenderRecorder.dispatchFillRect(
        cmd: NormalizedDrawCommand.FillRect,
        dispatched: MutableList<String>,
        diagnostics: MutableList<String>,
    ) {
        fun refuse(reason: String) {
            diagnostics.add("refuse:${cmd.diagnosticName}:$reason")
        }

        val material = cmd.material
        if (material !is GPUMaterialDescriptor.SolidColor) {
            refuse("unsupported_material:${material.kind.name}")
            return
        }
        if (cmd.transform.type != GPUTransformType.Identity) {
            refuse("unsupported_transform:${cmd.transform.type.name}")
            return
        }
        if (cmd.clip.kind !in listOf(GPUClipKind.WideOpen, GPUClipKind.DeviceRect)) {
            refuse("unsupported_clip:${cmd.clip.kind.name}")
            return
        }
        if (cmd.layer.scopeKind != GPULayerScopeKind.Root) {
            refuse("unsupported_layer:${cmd.layer.scopeKind.name}")
            return
        }
        if (cmd.blend.kind != GPUBlendKind.SrcOver) {
            refuse("unsupported_blend:${cmd.blend.modeLabel}")
            return
        }

        val rgba = floatArrayOf(
            material.r * material.a,
            material.g * material.a,
            material.b * material.a,
            material.a,
        )

        val rect = cmd.rect
        val clipBounds = cmd.clip.bounds
        val sx = maxOf(rect.left, clipBounds.left).toInt().coerceIn(0, width - 1)
        val sy = maxOf(rect.top, clipBounds.top).toInt().coerceIn(0, height - 1)
        val sw = (minOf(rect.right, clipBounds.right).toInt() - sx).coerceIn(1, width - sx)
        val sh = (minOf(rect.bottom, clipBounds.bottom).toInt() - sy).coerceIn(1, height - sy)

        drawFullscreenPass(
            wgsl = SOLID_RECT_WGSL,
            colorFormat = GPU_COLOR_FORMAT,
            draws = listOf(GPUBackendRectDraw(rgbaPremul = rgba, scissorX = sx, scissorY = sy, scissorWidth = sw, scissorHeight = sh)),
        )
        dispatched.add(cmd.commandId.toString())
        diagnostics.add("dispatch:${cmd.diagnosticName}")
    }

    private fun GPUBackendRenderRecorder.dispatchFillRRect(
        cmd: NormalizedDrawCommand.FillRRect,
        dispatched: MutableList<String>,
        diagnostics: MutableList<String>,
    ) {
        fun refuse(reason: String) {
            diagnostics.add("refuse:${cmd.diagnosticName}:$reason")
        }

        val material = cmd.material
        if (material !is GPUMaterialDescriptor.SolidColor) {
            refuse("unsupported_material:${material.kind.name}")
            return
        }
        if (cmd.transform.type != GPUTransformType.Identity) {
            refuse("unsupported_transform:${cmd.transform.type.name}")
            return
        }
        if (cmd.clip.kind !in listOf(GPUClipKind.WideOpen, GPUClipKind.DeviceRect)) {
            refuse("unsupported_clip:${cmd.clip.kind.name}")
            return
        }
        if (cmd.layer.scopeKind != GPULayerScopeKind.Root) {
            refuse("unsupported_layer:${cmd.layer.scopeKind.name}")
            return
        }
        if (cmd.blend.kind != GPUBlendKind.SrcOver) {
            refuse("unsupported_blend:${cmd.blend.modeLabel}")
            return
        }

        val rrect = cmd.rrect
        val rx = rrect.topLeft.x
        val ry = rrect.topLeft.y
        if (rrect.topRight.x != rx || rrect.topRight.y != ry ||
            rrect.bottomRight.x != rx || rrect.bottomRight.y != ry ||
            rrect.bottomLeft.x != rx || rrect.bottomLeft.y != ry
        ) {
            refuse("non_uniform_radii")
            return
        }

        val rect = rrect.rect
        val clipBounds = cmd.clip.bounds
        val sx = maxOf(rect.left, clipBounds.left).toInt().coerceIn(0, width - 1)
        val sy = maxOf(rect.top, clipBounds.top).toInt().coerceIn(0, height - 1)
        val sw = (minOf(rect.right, clipBounds.right).toInt() - sx).coerceIn(1, width - sx)
        val sh = (minOf(rect.bottom, clipBounds.bottom).toInt() - sy).coerceIn(1, height - sy)

        val bb = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.nativeOrder())
        bb.putFloat(rect.left)
        bb.putFloat(rect.top)
        bb.putFloat(rect.right)
        bb.putFloat(rect.bottom)
        bb.putFloat(rx)
        bb.putFloat(ry)
        bb.putFloat(0f)
        bb.putFloat(0f)
        bb.putFloat(material.r * material.a)
        bb.putFloat(material.g * material.a)
        bb.putFloat(material.b * material.a)
        bb.putFloat(material.a)

        drawFullscreenRawUniformPass(
            wgsl = RRECT_WGSL,
            colorFormat = GPU_COLOR_FORMAT,
            draws = listOf(
                GPUBackendRawUniformDraw(
                    uniformBytes = bb.array(),
                    scissorX = sx,
                    scissorY = sy,
                    scissorWidth = sw,
                    scissorHeight = sh,
                ),
            ),
        )
        dispatched.add(cmd.commandId.toString())
        diagnostics.add("dispatch:${cmd.diagnosticName}")
    }

    private fun stencilWriteWgsl(): String = """
struct VertexInput {
    @location(0) position: vec2f,
};

@vertex
fn vs_main(in: VertexInput) -> @builtin(position) vec4f {
    let hw = f32($width) / 2.0;
    let hh = f32($height) / 2.0;
    return vec4f(in.position.x / hw - 1.0, 1.0 - in.position.y / hh, 0.0, 1.0);
}

@fragment
fn fs_main() -> @location(0) vec4f {
    return vec4f(0.0, 0.0, 0.0, 0.0);
}
""".trimIndent()

    private fun GPUBackendRenderRecorder.dispatchFillPath(
        cmd: NormalizedDrawCommand.FillPath,
        dispatched: MutableList<String>,
        diagnostics: MutableList<String>,
    ) {
        fun refuse(reason: String) {
            diagnostics.add("refuse:${cmd.diagnosticName}:$reason")
        }

        val material = cmd.material
        if (material !is GPUMaterialDescriptor.SolidColor) {
            refuse("unsupported_material:${material.kind.name}")
            return
        }
        if (cmd.transform.type != GPUTransformType.Identity) {
            refuse("unsupported_transform:${cmd.transform.type.name}")
            return
        }
        if (cmd.clip.kind !in listOf(GPUClipKind.WideOpen, GPUClipKind.DeviceRect)) {
            refuse("unsupported_clip:${cmd.clip.kind.name}")
            return
        }
        if (cmd.layer.scopeKind != GPULayerScopeKind.Root) {
            refuse("unsupported_layer:${cmd.layer.scopeKind.name}")
            return
        }
        if (cmd.blend.kind != GPUBlendKind.SrcOver) {
            refuse("unsupported_blend:${cmd.blend.modeLabel}")
            return
        }

        val tessVertices = cmd.tessellatedVertices
        val vertexCount = cmd.totalVertexCount
        if (vertexCount < 3 || tessVertices.size < 6) {
            refuse("insufficient_vertices:count=$vertexCount")
            return
        }

        // Build per-contour triangle fan indices
        val contourStarts = cmd.contourStarts
        val indices = mutableListOf<Int>()
        for (ci in contourStarts.indices) {
            val start = contourStarts[ci]
            val end = if (ci + 1 < contourStarts.size) contourStarts[ci + 1] else vertexCount
            val cvCount = end - start
            if (cvCount < 3) continue
            for (i in 1 until cvCount - 1) {
                indices.add(start)
                indices.add(start + i)
                indices.add(start + i + 1)
            }
        }

        if (indices.size < 3) {
            refuse("no_triangles_generated")
            return
        }

        val triangleData = GPUBackendTriangleData(
            vertices = tessVertices.toFloatArray(),
            indices = indices.toIntArray(),
        )

        val clipBounds = cmd.clip.bounds
        val pathBounds = cmd.bounds
        val sx = maxOf(pathBounds.left, clipBounds.left).toInt().coerceIn(0, width - 1)
        val sy = maxOf(pathBounds.top, clipBounds.top).toInt().coerceIn(0, height - 1)
        val sw = (minOf(pathBounds.right, clipBounds.right).toInt() - sx).coerceIn(1, width - sx)
        val sh = (minOf(pathBounds.bottom, clipBounds.bottom).toInt() - sy).coerceIn(1, height - sy)

        val writeWgsl = stencilWriteWgsl()

        // Stencil write pass: render triangles to stencil buffer (no color write)
        drawFullscreenStencilPass(
            wgsl = writeWgsl,
            colorFormat = GPU_COLOR_FORMAT,
            stencilMode = GPUBackendStencilMode.Write,
            triangleData = triangleData,
            draws = emptyList(),
        )

        // Stencil test pass: fill where stencil != 0
        val colorBb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.nativeOrder())
        colorBb.putFloat(material.r * material.a)
        colorBb.putFloat(material.g * material.a)
        colorBb.putFloat(material.b * material.a)
        colorBb.putFloat(material.a)

        drawFullscreenStencilPass(
            wgsl = SOLID_RECT_WGSL,
            colorFormat = GPU_COLOR_FORMAT,
            stencilMode = GPUBackendStencilMode.Test,
            triangleData = null,
            draws = listOf(
                GPUBackendRawUniformDraw(
                    uniformBytes = colorBb.array(),
                    scissorX = sx,
                    scissorY = sy,
                    scissorWidth = sw,
                    scissorHeight = sh,
                ),
            ),
        )

        dispatched.add(cmd.commandId.toString())
        diagnostics.add("dispatch:${cmd.diagnosticName}")
    }

    private fun GPUBackendRenderRecorder.dispatchDrawTextRun(
        cmd: NormalizedDrawCommand.DrawTextRun,
        dispatched: MutableList<String>,
        diagnostics: MutableList<String>,
    ) {
        when (val plan = TextRunDispatchPlanner.plan(cmd, width, height)) {
            is TextRunDispatchPlan.Refused -> {
                diagnostics.add("refuse:${cmd.diagnosticName}:${plan.reason}")
            }
            is TextRunDispatchPlan.Draws -> {
                drawFullscreenTextureUniformPass(
                    wgsl = TextAtlasGlyphWgsl,
                    colorFormat = GPU_COLOR_FORMAT,
                    textureRgba = plan.atlasBytes,
                    textureWidth = plan.atlasWidth,
                    textureHeight = plan.atlasHeight,
                    textureFormat = "r8unorm",
                    draws = plan.placements.map { placement ->
                        GPUBackendRawUniformDraw(
                            uniformBytes = placement.uniformBytes(),
                            scissorX = placement.scissorX,
                            scissorY = placement.scissorY,
                            scissorWidth = placement.scissorWidth,
                            scissorHeight = placement.scissorHeight,
                        )
                    },
                )
                dispatched.add(cmd.commandId.toString())
                diagnostics.add(
                    "dispatch:${cmd.diagnosticName}:glyphs=${plan.placements.size}" +
                        ":atlas=${plan.atlasWidth}x${plan.atlasHeight}:bytes=${plan.atlasBytes.size}",
                )
            }
        }
    }

    private companion object {
        val DEFAULT_CLEAR_COLOR = GPUClearColor(0.0, 0.0, 0.0, 0.0)
    }
}
