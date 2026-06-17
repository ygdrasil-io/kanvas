package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetry
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision

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

/** Owns a GPU backend session that can allocate offscreen and window-backed targets. */
interface GPUBackendSession : AutoCloseable {
    val adapterInfo: GPUBackendAdapterSummary?

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

/** Records draw inputs for the backend runtime's fullscreen pass helper. */
interface GPUBackendRenderRecorder {
    /** Draws a fullscreen pass parameterized by WGSL source and per-draw rect payloads. */
    fun drawFullscreenPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendRectDraw>,
    )

    /** Draws a fullscreen pass by uploading prepacked uniform payload bytes for each draw. */
    fun drawFullscreenUniformPayloadPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendUniformPayloadDraw>,
    )
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
}
