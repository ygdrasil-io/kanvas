package org.graphiks.kanvas.gpu.renderer.execution

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

enum class GPUNativePlatform {
    AppKitMetalLayer,
}

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

data class GPUBackendAdapterSummary(
    val summary: String,
)

interface GPUBackendSession : AutoCloseable {
    val adapterInfo: GPUBackendAdapterSummary?

    fun createOffscreenTarget(request: GPUOffscreenTargetRequest): GPUBackendOffscreenTarget

    fun createWindowSurface(binding: GPUNativeSurfaceBinding): GPUBackendWindowSurface
}

interface GPUBackendOffscreenTarget : AutoCloseable {
    val target: GPUSurfaceTarget

    fun encode(
        clearColor: GPUClearColor,
        block: GPUBackendRenderRecorder.() -> Unit,
    )

    fun readRgba(): ByteArray
}

interface GPUBackendWindowSurface : AutoCloseable {
    val adapterInfo: GPUBackendAdapterSummary?

    val target: GPUSurfaceTarget

    fun resize(width: Int, height: Int)

    fun encodeAndPresent(
        clearColor: GPUClearColor,
        block: GPUBackendRenderRecorder.() -> Unit,
    ): Boolean
}

interface GPUBackendRenderRecorder {
    fun drawFullscreenPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendRectDraw>,
    )
}

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

object GPUBackendRuntimeFactory {
    fun createOrNull(): GPUBackendSession? = WgpuBackendRuntimeFactory.createOrNull()
}
