package org.graphiks.kanvas.test

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.opentest4j.TestAbortedException

object GpuAvailability {
    val gpuAvailable: Boolean by lazy {
        GPUBackendRuntimeFactory.createOrNull() != null
    }

    fun requireWebGpu() {
        if (!gpuAvailable) {
            throw TestAbortedException("WebGPU not available on this machine")
        }
    }
}
