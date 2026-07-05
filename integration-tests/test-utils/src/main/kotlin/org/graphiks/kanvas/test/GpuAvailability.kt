package org.graphiks.kanvas.test

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.opentest4j.TestAbortedException

object GpuAvailability {
    fun requireWebGpu() {
        if (GPUBackendRuntimeFactory.createOrNull() == null) {
            throw TestAbortedException("WebGPU not available on this machine")
        }
    }
}
