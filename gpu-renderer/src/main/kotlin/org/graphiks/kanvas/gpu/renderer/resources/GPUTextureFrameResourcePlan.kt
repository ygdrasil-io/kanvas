package org.graphiks.kanvas.gpu.renderer.resources

/** Minimal handle-free upload contract shared by prepared texture formats. */
sealed interface GPUTextureFrameResourcePlan {
    val stagingRef: GPUFrameBufferRef
    val frameTextureRef: GPUFrameTextureRef
    val uploadTaskLayout: GPUUploadLayout
    val preparationRequests: List<GPUResourcePreparationRequest>
    val memoryAllocations: List<GPUFrameMemoryAllocation>

    fun bytesForUpload(): ByteArray
}
