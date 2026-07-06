package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetry
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities

/** Describes an offscreen surface allocation request for the low-level GPU backend runtime. */
data class GPUOffscreenTargetRequest(
    val width: Int,
    val height: Int,
    val colorFormat: String = "rgba8unorm",
) {
    init {
        require(width > 0) { "GPUOffscreenTargetRequest.width must be positive" }
        require(height > 0) { "GPUOffscreenTargetRequest.height must be positive" }
        require(colorFormat.isNotBlank()) { "GPUOffscreenTargetRequest.colorFormat must not be blank" }
    }
}

/** Enumerates native surface platforms supported by the backend runtime bridge. */
enum class GPUNativePlatform {
    /** AppKit surface backed by a Metal layer. */
    AppKitMetalLayer,
}

/** Carries the native handles required to bind a presentable window surface. */
data class GPUNativeSurfaceBinding(
    val platform: GPUNativePlatform,
    val width: Int,
    val height: Int,
    val pointerLabels: Map<String, Long>,
) {
    init {
        require(width > 0) { "GPUNativeSurfaceBinding.width must be positive" }
        require(height > 0) { "GPUNativeSurfaceBinding.height must be positive" }
        require(pointerLabels.isNotEmpty()) { "GPUNativeSurfaceBinding.pointerLabels must not be empty" }
        require(pointerLabels.keys.all { it.isNotBlank() }) {
            "GPUNativeSurfaceBinding.pointerLabels keys must not be blank"
        }
    }
}

/** Stores a normalized clear color ready to feed GPU load operations. */
data class GPUClearColor(
    val red: Double,
    val green: Double,
    val blue: Double,
    val alpha: Double,
) {
    init {
        require(red in 0.0..1.0) { "GPUClearColor.red must be in [0, 1]" }
        require(green in 0.0..1.0) { "GPUClearColor.green must be in [0, 1]" }
        require(blue in 0.0..1.0) { "GPUClearColor.blue must be in [0, 1]" }
        require(alpha in 0.0..1.0) { "GPUClearColor.alpha must be in [0, 1]" }
    }
}

/** Summarizes the active adapter without exposing backend-native handles. */
data class GPUBackendAdapterSummary(
    val summary: String,
)

/** Aggregated passive counters for a GPU backend session. */
data class GPUBackendRuntimeTelemetry(
    val renderPasses: Long = 0L,
    val offscreenPasses: Long = 0L,
    val windowPasses: Long = 0L,
    val submissions: Long = 0L,
    val buffersCreated: Long = 0L,
    val texturesCreated: Long = 0L,
    val bindGroupsCreated: Long = 0L,
    val samplersCreated: Long = 0L,
    val queueWrites: Long = 0L,
    val uniformSlabsCreated: Long = 0L,
    val uniformSlabBytesAllocated: Long = 0L,
    val uniformSlabFallbacks: Long = 0L,
) {
    init {
        require(renderPasses >= 0L) { "GPUBackendRuntimeTelemetry.renderPasses must be non-negative" }
        require(offscreenPasses >= 0L) { "GPUBackendRuntimeTelemetry.offscreenPasses must be non-negative" }
        require(windowPasses >= 0L) { "GPUBackendRuntimeTelemetry.windowPasses must be non-negative" }
        require(submissions >= 0L) { "GPUBackendRuntimeTelemetry.submissions must be non-negative" }
        require(buffersCreated >= 0L) { "GPUBackendRuntimeTelemetry.buffersCreated must be non-negative" }
        require(texturesCreated >= 0L) { "GPUBackendRuntimeTelemetry.texturesCreated must be non-negative" }
        require(bindGroupsCreated >= 0L) { "GPUBackendRuntimeTelemetry.bindGroupsCreated must be non-negative" }
        require(samplersCreated >= 0L) { "GPUBackendRuntimeTelemetry.samplersCreated must be non-negative" }
        require(queueWrites >= 0L) { "GPUBackendRuntimeTelemetry.queueWrites must be non-negative" }
        require(uniformSlabsCreated >= 0L) { "GPUBackendRuntimeTelemetry.uniformSlabsCreated must be non-negative" }
        require(
            uniformSlabBytesAllocated >= 0L,
        ) { "GPUBackendRuntimeTelemetry.uniformSlabBytesAllocated must be non-negative" }
        require(uniformSlabFallbacks >= 0L) { "GPUBackendRuntimeTelemetry.uniformSlabFallbacks must be non-negative" }
    }

    /** Deterministic diagnostic lines without backend object identities. */
    fun dumpLines(): List<String> =
        listOf(
            "gpu-runtime.telemetry renderPasses=$renderPasses offscreenPasses=$offscreenPasses " +
                "windowPasses=$windowPasses submissions=$submissions buffersCreated=$buffersCreated " +
                "texturesCreated=$texturesCreated bindGroupsCreated=$bindGroupsCreated " +
                "samplersCreated=$samplersCreated queueWrites=$queueWrites " +
                "uniformSlabsCreated=$uniformSlabsCreated uniformSlabBytesAllocated=$uniformSlabBytesAllocated " +
                "uniformSlabFallbacks=$uniformSlabFallbacks",
        )

    companion object {
        val Empty = GPUBackendRuntimeTelemetry()
    }
}

/** Owns a GPU backend session that can allocate offscreen and window-backed targets. */
interface GPUBackendSession : AutoCloseable {
    val adapterInfo: GPUBackendAdapterSummary?

    /** Reports the backend implementation and behavior-affecting limits when known. */
    val capabilities: GPUCapabilities?
        get() = null

    /** Reports passive runtime counters emitted by this session. */
    val runtimeTelemetry: GPUBackendRuntimeTelemetry
        get() = GPUBackendRuntimeTelemetry.Empty

    /** Reports deterministic runtime telemetry dump lines without backend handles. */
    val runtimeTelemetryDumpLines: List<String>
        get() = runtimeTelemetry.dumpLines()

    /** Reports live execution-cache counters emitted by this session. */
    val executionCacheTelemetry: List<GPUCacheTelemetry>
        get() = emptyList()

    /** Reports deterministic execution-cache dump lines without backend handles. */
    val executionCacheDumpLines: List<String>
        get() = emptyList()

    /** Allocates an offscreen render target using the requested size and color format. */
    fun createOffscreenTarget(request: GPUOffscreenTargetRequest): GPUBackendOffscreenTarget

    /** Binds a native window surface that can encode and present fullscreen passes. */
    fun createWindowSurface(binding: GPUNativeSurfaceBinding): GPUBackendWindowSurface
}

/** Represents an offscreen target that supports rendering then RGBA readback. */
interface GPUBackendOffscreenTarget : AutoCloseable {
    val target: GPUSurfaceTarget

    /** Records one fullscreen render pass into the target with the provided clear color. */
    fun encode(
        clearColor: GPUClearColor,
        block: GPUBackendRenderRecorder.() -> Unit,
    )

    /** Reads the rendered content back as tightly packed RGBA bytes. */
    fun readRgba(): ByteArray

    /** Creates a secondary offscreen texture that can be bound as a texture source during a subsequent [encode]. */
    fun createOffscreenTexture(texture: GPUBackendOffscreenTexture): String

    /** Renders into a previously-created offscreen texture via a separate render pass. When [clearColor] is null the pass preserves existing texture content via [GPULoadOp.Load]. */
    fun encodeOffscreenTexture(
        textureLabel: String,
        clearColor: GPUClearColor?,
        block: GPUBackendRenderRecorder.() -> Unit,
    )
}

/** Represents a native surface that can be resized and presented to screen. */
interface GPUBackendWindowSurface : AutoCloseable {
    val adapterInfo: GPUBackendAdapterSummary?

    val target: GPUSurfaceTarget

    /** Reconfigures the surface size for subsequent presentations. */
    fun resize(width: Int, height: Int)

    /** Records and presents one fullscreen pass, returning true only when a frame was presented. */
    fun encodeAndPresent(
        clearColor: GPUClearColor,
        block: GPUBackendRenderRecorder.() -> Unit,
    ): Boolean
}

/** Enumerates stencil render modes for [GPUBackendRenderRecorder.drawFullscreenStencilPass]. */
enum class GPUBackendStencilMode {
    /** Render triangles into stencil buffer with increment/decrement winding ops (no color write). */
    Write,
    /** Fullscreen quad that passes only where stencil != 0, writing fill color. */
    Test,
}

/** Describes a secondary offscreen texture that can be bound as a texture source. */
data class GPUBackendOffscreenTexture(
    val width: Int,
    val height: Int,
    val format: String,
) {
    init {
        require(width > 0) { "GPUBackendOffscreenTexture.width must be positive" }
        require(height > 0) { "GPUBackendOffscreenTexture.height must be positive" }
        require(format.isNotBlank()) { "GPUBackendOffscreenTexture.format must not be blank" }
    }
}

/** Holds triangle vertex data for stencil write or vertex buffer passes. */
data class GPUBackendTriangleData(
    val vertices: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = vertices.size / 2
    val triangleCount: Int get() = indices.size / 3

    init {
        require(vertices.size >= 6) { "GPUBackendTriangleData.vertices must have at least 6 floats (3 positions)" }
        require(vertices.size % 2 == 0) { "GPUBackendTriangleData.vertices must contain pairs of floats" }
        require(indices.size >= 3) { "GPUBackendTriangleData.indices must have at least 3 indices" }
        require(indices.size % 3 == 0) { "GPUBackendTriangleData.indices must be a multiple of 3" }
        require(indices.all { it in 0 until vertexCount }) { "GPUBackendTriangleData.indices out of range" }
    }
}

/** Holds interleaved vertex data (position + padding + color) for vertex buffer passes. */
data class GPUBackendVertexColorData(
    val vertexData: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = vertexData.size / 8
    val strideFloats: Int = 8

    init {
        require(vertexData.size >= 8) { "GPUBackendVertexColorData.vertexData must have at least 8 floats" }
        require(vertexData.size % strideFloats == 0) {
            "GPUBackendVertexColorData.vertexData must be a multiple of $strideFloats"
        }
        require(indices.isNotEmpty()) { "GPUBackendVertexColorData.indices must not be empty" }
        require(indices.all { it in 0 until vertexCount }) { "GPUBackendVertexColorData.indices out of range" }
    }
}

/** Holds interleaved vertex data (position + uv) for textured vertex buffer passes. */
data class GPUBackendVertexPositionUVData(
    val vertexData: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = vertexData.size / 4
    val strideFloats: Int = 4

    init {
        require(vertexData.size >= 4) { "GPUBackendVertexPositionUVData.vertexData must have at least 4 floats" }
        require(vertexData.size % strideFloats == 0) {
            "GPUBackendVertexPositionUVData.vertexData must be a multiple of $strideFloats"
        }
        require(indices.isNotEmpty()) { "GPUBackendVertexPositionUVData.indices must not be empty" }
        require(indices.all { it in 0 until vertexCount }) { "GPUBackendVertexPositionUVData.indices out of range" }
    }
}

/** Records draw inputs for the backend runtime's fullscreen pass helper. */
interface GPUBackendRenderRecorder {
    /** Draws a fullscreen pass parameterized by WGSL source and per-draw rect payloads. */
    fun drawFullscreenPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendRectDraw>,
        blendMode: GPUBlendMode? = null,
    )

    /** Draws a fullscreen pass by uploading prepacked uniform payload bytes for each draw. */
    fun drawFullscreenUniformPayloadPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendUniformPayloadDraw>,
        blendMode: GPUBlendMode? = null,
    )

    /** Draws a fullscreen pass with raw uniform bytes per draw, bypassing provider materialization. */
    fun drawFullscreenRawUniformPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode? = null,
    )

    /** Draws a fullscreen pass with a generated texture+sampler binding alongside packed uniforms. */
    fun drawFullscreenTextureUniformPass(
        wgsl: String,
        colorFormat: String,
        textureRgba: ByteArray,
        textureWidth: Int,
        textureHeight: Int,
        textureFormat: String,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode? = null,
    )

    /** Draws a two-pass stencil-cover fill with triangle geometry (write) and fullscreen cover (test). */
    fun drawFullscreenStencilPass(
        wgsl: String,
        colorFormat: String,
        stencilMode: GPUBackendStencilMode,
        triangleData: GPUBackendTriangleData?,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode? = null,
    )

    /** Creates a GPU vertex buffer from interleaved position + color float data. Returns a stable label. */
    fun createVertexColorBuffer(data: GPUBackendVertexColorData): String

    /** Draws an indexed mesh using a previously-created vertex buffer. */
    fun drawVertexColorIndexed(
        vertexBufferLabel: String,
        indexCount: Int,
        uniformDraw: GPUBackendRawUniformDraw,
        blendMode: GPUBlendMode? = null,
    )

    /** Creates a GPU vertex buffer from interleaved position + uv float data. Returns a stable label. */
    fun createVertexPositionUVBuffer(data: GPUBackendVertexPositionUVData): String

    /** Draws an indexed mesh using a previously-created position+uv vertex buffer with a bound texture. */
    fun drawVertexPositionUVIndexed(
        vertexBufferLabel: String,
        indexCount: Int,
        uniformDraw: GPUBackendRawUniformDraw,
        textureRgba: ByteArray,
        textureWidth: Int,
        textureHeight: Int,
        textureFormat: String,
        blendMode: GPUBlendMode? = null,
    )

    /** Draws an indexed mesh with dual UV channels and two bound textures. */
    fun drawVertexPositionDualUVIndexed(
        vertexBufferLabel: String,
        indexCount: Int,
        uniformDraw: GPUBackendRawUniformDraw,
        texture1Rgba: ByteArray,
        texture1Width: Int, texture1Height: Int,
        texture2Rgba: ByteArray,
        texture2Width: Int, texture2Height: Int,
        textureFormat: String,
        blendMode: GPUBlendMode? = null,
    )

    /** Creates a secondary offscreen texture that can be bound as a texture source. */
    fun createOffscreenTexture(texture: GPUBackendOffscreenTexture): String

    /** Renders into a previously-created offscreen texture via a separate render pass. When [clearColor] is null the pass preserves existing texture content via [GPULoadOp.Load]. */
    fun encodeOffscreenTexture(
        textureLabel: String,
        clearColor: GPUClearColor?,
        block: GPUBackendRenderRecorder.() -> Unit,
    )

    /** Binds a previously-created offscreen texture as @group(1) texture source for compositing. */
    fun drawCompositePass(
        wgsl: String,
        colorFormat: String,
        textureLabel: String,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode? = null,
    )

    /** Dual-texture blend pass: composites source over destination using a shader-based blend formula. */
    fun drawBlendPass(
        wgsl: String,
        colorFormat: String,
        srcTextureLabel: String,
        dstTextureLabel: String,
        draws: List<GPUBackendRawUniformDraw>,
    )

    /** Draws indexed glyph quads from an A8 atlas texture with per-draw uniform payloads. */
    fun drawTextAtlasPass(
        atlasRgba: ByteArray,
        atlasWidth: Int,
        atlasHeight: Int,
        atlasFormat: String,
        vertexData: FloatArray,
        indexData: IntArray,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode? = null,
    )

    /** Draws indexed glyph quads for COLRv0 composite color glyphs. Same vertex/index/atlas structure as drawTextAtlasPass with a per-layer composite WGSL shader. */
    fun drawColorGlyphPass(
        atlasRgba: ByteArray,
        atlasWidth: Int,
        atlasHeight: Int,
        atlasFormat: String,
        vertexData: FloatArray,
        indexData: IntArray,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode? = null,
    )
}

/** Raw uniform bytes for a fullscreen draw, bypassing provider materialization contracts. */
data class GPUBackendRawUniformDraw(
    val uniformBytes: ByteArray,
    val scissorX: Int,
    val scissorY: Int,
    val scissorWidth: Int,
    val scissorHeight: Int,
) {
    init {
        require(uniformBytes.isNotEmpty()) { "GPUBackendRawUniformDraw.uniformBytes must not be empty" }
        require(scissorWidth > 0) { "GPUBackendRawUniformDraw.scissorWidth must be positive" }
        require(scissorHeight > 0) { "GPUBackendRawUniformDraw.scissorHeight must be positive" }
    }
}

/** Encodes the rect-scoped payload consumed by the fullscreen pass helper. */
data class GPUBackendRectDraw(
    val rgbaPremul: FloatArray,
    val scissorX: Int,
    val scissorY: Int,
    val scissorWidth: Int,
    val scissorHeight: Int,
) {
    init {
        require(rgbaPremul.size == 4) { "GPUBackendRectDraw.rgbaPremul must contain 4 floats" }
        require(scissorWidth > 0) { "GPUBackendRectDraw.scissorWidth must be positive" }
        require(scissorHeight > 0) { "GPUBackendRectDraw.scissorHeight must be positive" }
    }
}

/** Encodes prepacked uniform bytes consumed by the fullscreen pass helper. */
class GPUBackendUniformPayloadDraw(
    uniformBytes: ByteArray,
    val materialization: GPUResourceMaterializationDecision.Materialized,
    val scissorX: Int,
    val scissorY: Int,
    val scissorWidth: Int,
    val scissorHeight: Int,
) {
    private val uniformBytesSnapshot: ByteArray = uniformBytes.copyOf()

    /** Provider-materialized operands consumed by this backend draw. */
    val materializedOperandLabels: List<String> =
        materialization.dumpOperandBridgeSnapshot.map { binding -> binding.operand.label }

    /** Uniform payload byte count accepted by provider materialization. */
    val materializedUniformByteSize: Int =
        materialization.materializedUniformByteSize()

    init {
        require(uniformBytes.isNotEmpty()) { "GPUBackendUniformPayloadDraw.uniformBytes must not be empty" }
        require(scissorWidth > 0) { "GPUBackendUniformPayloadDraw.scissorWidth must be positive" }
        require(scissorHeight > 0) { "GPUBackendUniformPayloadDraw.scissorHeight must be positive" }
        val operandKinds = materialization.dumpOperandBridgeSnapshot.map { binding -> binding.operand.kind }.toSet()
        require(GPUMaterializedCommandOperandKind.UniformBuffer in operandKinds) {
            "GPUBackendUniformPayloadDraw.materialization must include a uniform-buffer operand"
        }
        require(GPUMaterializedCommandOperandKind.BindGroup in operandKinds) {
            "GPUBackendUniformPayloadDraw.materialization must include a bind-group operand"
        }
        require(uniformBytes.size == materializedUniformByteSize) {
            "GPUBackendUniformPayloadDraw.uniformBytes size ${uniformBytes.size} must match materialized byteSize $materializedUniformByteSize"
        }
    }

    /** Returns a defensive copy of the prepacked uniform bytes. */
    fun uniformBytes(): ByteArray = uniformBytesSnapshot.copyOf()
}

private fun GPUResourceMaterializationDecision.Materialized.materializedUniformByteSize(): Int {
    val uniformOperand = dumpOperandBridgeSnapshot
        .map { binding -> binding.operand }
        .firstOrNull { operand -> operand.kind == GPUMaterializedCommandOperandKind.UniformBuffer }
        ?: throw IllegalArgumentException("GPUBackendUniformPayloadDraw.materialization must include a uniform-buffer operand")
    val byteSize = uniformOperand.dumpEvidenceFactsSnapshot["byteSize"]?.toIntOrNull()
    require(byteSize != null && byteSize > 0) {
        "GPUBackendUniformPayloadDraw.materialization uniform-buffer operand must expose positive byteSize"
    }
    return byteSize
}

/** Creates the default WebGPU-backed runtime when the local environment supports it. */
object GPUBackendRuntimeFactory {
    /** Returns a WebGPU-backed session or null when backend initialization is unavailable. */
    fun createOrNull(): GPUBackendSession? = WgpuBackendRuntimeFactory.createOrNull()

    /** Release the shared WGPU device and all cached resources. Must be called after
     *  all rendering is complete, before the JVM shuts down. */
    fun dispose() = WgpuBackendRuntimeFactory.dispose()
}
