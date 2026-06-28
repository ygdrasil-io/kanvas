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

/**
 * Returns the stable stroke refusal reason (`unsupported_stroke`) when [this]
 * command carries a stroke-style paint, or `null` for fill commands.
 *
 * Stroke-style draws (Skia `kStroke_Style` / `kStrokeAndFill_Style`) cannot be
 * rendered by the native fill pipeline; they are REFUSED instead of being
 * silently filled. Real stroke rendering is dependency-gated (KGPU-M3-003).
 * The dispatch methods surface this reason through
 * [SurfaceRenderResult.diagnostics] so the bridge's `emitRefusedDiagnostics`
 * reports `refuse:<command>:unsupported_stroke`.
 */
internal fun NormalizedDrawCommand.strokeRefusalReasonOrNull(): String? {
    val stroke = when (this) {
        is NormalizedDrawCommand.FillRect -> stroke
        is NormalizedDrawCommand.FillRRect -> stroke
        is NormalizedDrawCommand.FillPath -> stroke
        is NormalizedDrawCommand.DrawTextRun -> false
        is NormalizedDrawCommand.DrawImageRect -> false
        is NormalizedDrawCommand.DrawLayer -> false
        is NormalizedDrawCommand.ApplyFilter -> false
    }
    return if (stroke) "unsupported_stroke" else null
}

/**
 * Returns the first stable refusal reason for a fill command's shared dispatch
 * guards (stroke, material, transform, clip, layer, blend), or `null` when the
 * command passes every guard.
 *
 * The order mirrors the historical inline checks in the
 * `dispatchFillRect`/`dispatchFillRRect`/`dispatchFillPath` methods so behavior
 * is preserved exactly; extracting it makes the refusals hermetically testable
 * (no GPU) and keeps a single source of truth. Non-SolidColor materials
 * (gradients, image/runtime-effect shaders) refuse with
 * `unsupported_material:<kind>` BEFORE any fill dispatch, so a non-solid paint
 * is never silently solid-filled (KGPU-M32-010/-012/-016/-019). DrawTextRun is
 * not a fill command and is planned separately by [TextRunDispatchPlanner].
 */
internal fun NormalizedDrawCommand.fillGuardRefusalReasonOrNull(): String? {
    strokeRefusalReasonOrNull()?.let { return it }
    if (this is NormalizedDrawCommand.DrawTextRun) return null
    val material = this.material
    if (material !is GPUMaterialDescriptor.SolidColor) {
        return "unsupported_material:${material.kind.name}"
    }
    if (transform.type != GPUTransformType.Identity) {
        return "unsupported_transform:${transform.type.name}"
    }
    if (clip.kind !in listOf(GPUClipKind.WideOpen, GPUClipKind.DeviceRect)) {
        return "unsupported_clip:${clip.kind.name}"
    }
    if (layer.scopeKind != GPULayerScopeKind.Root) {
        return "unsupported_layer:${layer.scopeKind.name}"
    }
    if (blend.kind != GPUBlendKind.SrcOver) {
        return "unsupported_blend:${blend.modeLabel}"
    }
    return null
}

/**
 * Returns `non_uniform_radii` when this rounded rect has non-uniform corner
 * radii (the uniform-rrect fill route cannot represent them), or `null` for a
 * uniform rrect. Runs after [fillGuardRefusalReasonOrNull] in the rrect
 * dispatcher (KGPU-M32-012 refused sub-case).
 */
internal fun NormalizedDrawCommand.FillRRect.nonUniformRadiiRefusalReasonOrNull(): String? {
    val rrect = this.rrect
    val rx = rrect.topLeft.x
    val ry = rrect.topLeft.y
    return if (rrect.topRight.x != rx || rrect.topRight.y != ry ||
        rrect.bottomRight.x != rx || rrect.bottomRight.y != ry ||
        rrect.bottomLeft.x != rx || rrect.bottomLeft.y != ry
    ) {
        "non_uniform_radii"
    } else {
        null
    }
}

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
                            is NormalizedDrawCommand.DrawImageRect -> dispatchDrawImageRect(cmd, dispatched, diagnostics)
                            is NormalizedDrawCommand.DrawLayer -> dispatchDrawLayer(cmd, dispatched, diagnostics)
                            is NormalizedDrawCommand.ApplyFilter -> dispatchApplyFilter(cmd, dispatched, diagnostics)
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

        cmd.fillGuardRefusalReasonOrNull()?.let { refuse(it); return }

        val material = cmd.material as GPUMaterialDescriptor.SolidColor

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

        cmd.fillGuardRefusalReasonOrNull()?.let { refuse(it); return }
        cmd.nonUniformRadiiRefusalReasonOrNull()?.let { refuse(it); return }

        val material = cmd.material as GPUMaterialDescriptor.SolidColor
        val rrect = cmd.rrect
        val rx = rrect.topLeft.x
        val ry = rrect.topLeft.y

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

        cmd.fillGuardRefusalReasonOrNull()?.let { refuse(it); return }

        val material = cmd.material as GPUMaterialDescriptor.SolidColor

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

    private fun GPUBackendRenderRecorder.dispatchDrawImageRect(
        cmd: NormalizedDrawCommand.DrawImageRect,
        dispatched: MutableList<String>,
        diagnostics: MutableList<String>,
    ) {
        fun refuse(reason: String) {
            diagnostics.add("refuse:${cmd.diagnosticName}:$reason")
        }

        if (cmd.stroke) { refuse("stroke"); return }
        if (cmd.transform.type != GPUTransformType.Identity) {
            refuse("unsupported_transform:${cmd.transform.type.name}"); return
        }
        if (cmd.pixelsWidth <= 0 || cmd.pixelsHeight <= 0) { refuse("empty_pixels"); return }

        val clipBounds = cmd.clip.bounds
        val srcBounds = cmd.src
        val sx = maxOf(srcBounds.left, clipBounds.left).toInt().coerceIn(0, width - 1)
        val sy = maxOf(srcBounds.top, clipBounds.top).toInt().coerceIn(0, height - 1)
        val sw = (minOf(srcBounds.right, clipBounds.right).toInt() - sx).coerceIn(1, width - sx)
        val sh = (minOf(srcBounds.bottom, clipBounds.bottom).toInt() - sy).coerceIn(1, height - sy)

        val texW = cmd.pixelsWidth.coerceIn(1, 64)
        val texH = cmd.pixelsHeight.coerceIn(1, 64)
        val texRgba = ByteArray(texW * texH * 4) { i ->
            when (i % 4) {
                0 -> ((i / 4) % texW * 255 / texW).toByte()
                1 -> (((i / 4) / texW) * 255 / texH).toByte()
                2 -> 128.toByte()
                3 -> 255.toByte()
                else -> 0
            }
        }

        val bb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.nativeOrder())
        bb.putFloat(srcBounds.left)
        bb.putFloat(srcBounds.top)
        bb.putFloat(srcBounds.right)
        bb.putFloat(srcBounds.bottom)

        drawFullscreenTextureUniformPass(
            wgsl = IMAGE_RECT_WGSL,
            colorFormat = GPU_COLOR_FORMAT,
            textureRgba = texRgba,
            textureWidth = texW,
            textureHeight = texH,
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
        )
        dispatched.add(cmd.commandId.toString())
        diagnostics.add("dispatch:${cmd.diagnosticName}:${texW}x${texH}")
    }

    private fun GPUBackendRenderRecorder.dispatchDrawLayer(
        cmd: NormalizedDrawCommand.DrawLayer,
        dispatched: MutableList<String>,
        diagnostics: MutableList<String>,
    ) {
        fun refuse(reason: String) {
            diagnostics.add("refuse:${cmd.diagnosticName}:$reason")
        }

        if (cmd.stroke) { refuse("stroke"); return }
        if (cmd.transform.type != GPUTransformType.Identity) {
            refuse("unsupported_transform:${cmd.transform.type.name}"); return
        }
        if (cmd.scopeId.isBlank()) { refuse("empty_scope_id"); return }

        val clipBounds = cmd.clip.bounds
        val bounds = cmd.bounds
        val sx = maxOf(bounds.left, clipBounds.left).toInt().coerceIn(0, width - 1)
        val sy = maxOf(bounds.top, clipBounds.top).toInt().coerceIn(0, height - 1)
        val sw = (minOf(bounds.right, clipBounds.right).toInt() - sx).coerceIn(1, width - sx)
        val sh = (minOf(bounds.bottom, clipBounds.bottom).toInt() - sy).coerceIn(1, height - sy)

        val alpha = when (cmd.material) {
            is GPUMaterialDescriptor.SolidColor -> (cmd.material as GPUMaterialDescriptor.SolidColor).a
            else -> 1f
        }
        val bb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.nativeOrder())
        bb.putFloat(alpha)
        bb.putFloat((cmd.bounds.right - cmd.bounds.left) / width.toFloat())
        bb.putFloat((cmd.bounds.bottom - cmd.bounds.top) / height.toFloat())
        bb.putFloat(0f)

        drawFullscreenRawUniformPass(
            wgsl = LAYER_ALPHA_WGSL,
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
        diagnostics.add("dispatch:${cmd.diagnosticName}:alpha=$alpha")
    }

    private fun GPUBackendRenderRecorder.dispatchApplyFilter(
        cmd: NormalizedDrawCommand.ApplyFilter,
        dispatched: MutableList<String>,
        diagnostics: MutableList<String>,
    ) {
        fun refuse(reason: String) {
            diagnostics.add("refuse:${cmd.diagnosticName}:$reason")
        }

        if (cmd.transform.type != GPUTransformType.Identity) {
            refuse("unsupported_transform:${cmd.transform.type.name}"); return
        }

        val nodeKind = cmd.filterGraph.nodes.singleOrNull()?.nodeKind
        if (nodeKind == null) { refuse("unsupported_filter_dag"); return }

        val clipBounds = cmd.clip.bounds
        val bounds = cmd.bounds
        val sx = maxOf(bounds.left, clipBounds.left).toInt().coerceIn(0, width - 1)
        val sy = maxOf(bounds.top, clipBounds.top).toInt().coerceIn(0, height - 1)
        val sw = (minOf(bounds.right, clipBounds.right).toInt() - sx).coerceIn(1, width - sx)
        val sh = (minOf(bounds.bottom, clipBounds.bottom).toInt() - sy).coerceIn(1, height - sy)

        val draw = when (nodeKind) {
            "GaussianBlur" -> {
                val radius = (cmd.bounds.right - cmd.bounds.left) / 16f
                val bb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.nativeOrder())
                bb.putFloat(radius)
                bb.putFloat(0f)
                bb.putFloat(0f)
                bb.putFloat(0f)
                GPUBackendRawUniformDraw(
                    uniformBytes = bb.array(),
                    scissorX = sx,
                    scissorY = sy,
                    scissorWidth = sw,
                    scissorHeight = sh,
                )
            }
            "ColorFilter" -> {
                val strength = 1f
                val bb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.nativeOrder())
                bb.putFloat(strength)
                bb.putFloat(0f)
                bb.putFloat(0f)
                bb.putFloat(0f)
                GPUBackendRawUniformDraw(
                    uniformBytes = bb.array(),
                    scissorX = sx,
                    scissorY = sy,
                    scissorWidth = sw,
                    scissorHeight = sh,
                )
            }
            else -> { refuse("unsupported_filter_kind:$nodeKind"); return }
        }

        val filterWgsl = when (nodeKind) {
            "GaussianBlur" -> BLUR_WGSL
            "ColorFilter" -> COLOR_MATRIX_WGSL
            else -> { refuse("unsupported_filter_kind:$nodeKind"); return }
        }

        drawFullscreenRawUniformPass(
            wgsl = filterWgsl,
            colorFormat = GPU_COLOR_FORMAT,
            draws = listOf(draw),
        )
        dispatched.add(cmd.commandId.toString())
        diagnostics.add("dispatch:${cmd.diagnosticName}:kind=$nodeKind")
    }

    private companion object {
        val DEFAULT_CLEAR_COLOR = GPUClearColor(0.0, 0.0, 0.0, 0.0)

        val IMAGE_RECT_WGSL: String = """
            struct Uniforms { srcRect: vec4f };
            @group(0) @binding(0) var<uniform> uniforms: Uniforms;
            @group(1) @binding(1) var tex: texture_2d<f32>;
            @group(1) @binding(2) var samp: sampler;

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return vec4f(x, y, 0.0, 1.0);
            }

            @fragment
            fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
                let uv = (coord.xy - uniforms.srcRect.xy) / (uniforms.srcRect.zw - uniforms.srcRect.xy);
                return textureSample(tex, samp, uv);
            }
        """.trimIndent()

        val LAYER_ALPHA_WGSL: String = """
            struct Uniforms { alpha: f32, scaleW: f32, scaleH: f32, pad: f32 };
            @group(0) @binding(0) var<uniform> uniforms: Uniforms;

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return vec4f(x, y, 0.0, 1.0);
            }

            @fragment
            fn fs_main() -> @location(0) vec4f {
                return vec4f(0.0, 0.0, 0.0, uniforms.alpha);
            }
        """.trimIndent()

        val BLUR_WGSL: String = """
            struct Uniforms { radius: f32, pad1: f32, pad2: f32, pad3: f32 };
            @group(0) @binding(0) var<uniform> uniforms: Uniforms;

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return vec4f(x, y, 0.0, 1.0);
            }

            @fragment
            fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
                return vec4f(0.5, 0.5, 0.5, 1.0);
            }
        """.trimIndent()

        val COLOR_MATRIX_WGSL: String = """
            struct Uniforms { strength: f32, pad1: f32, pad2: f32, pad3: f32 };
            @group(0) @binding(0) var<uniform> uniforms: Uniforms;

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return vec4f(x, y, 0.0, 1.0);
            }

            @fragment
            fn fs_main() -> @location(0) vec4f {
                return vec4f(0.8, 0.2, 0.8, 1.0);
            }
        """.trimIndent()
    }
}
