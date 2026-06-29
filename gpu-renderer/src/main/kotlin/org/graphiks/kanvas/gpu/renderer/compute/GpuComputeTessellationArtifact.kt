package org.graphiks.kanvas.gpu.renderer.compute

import org.graphiks.kanvas.gpu.renderer.routing.CPUPreparedGPUArtifactDescriptor
import org.graphiks.kanvas.gpu.renderer.routing.CPUPreparedGPUArtifactKey
import org.graphiks.kanvas.gpu.renderer.routing.CPUPreparedGPUArtifactRegistry
import java.security.MessageDigest

data class GpuComputeTessellationArtifact(
    val planKey: String,
    val vertexCount: Int,
) {
    companion object {
        fun descriptor(key: CPUPreparedGPUArtifactKey): CPUPreparedGPUArtifactDescriptor {
            val hash = sha256(key.value)
            return CPUPreparedGPUArtifactDescriptor(
                artifactType = "GpuComputeTessellationArtifact",
                version = 1,
                lifetimeClass = "cache-resident",
                consumerKind = "gpu.tessellation.shader",
                budgetClass = "geometry-buffer",
                invalidationPolicy = "content-key",
                descriptorHash = hash,
            )
        }

        fun registerIn(registry: MutableMap<CPUPreparedGPUArtifactKey, CPUPreparedGPUArtifactDescriptor>) {
            val key = CPUPreparedGPUArtifactKey("prepared.GpuComputeTessellationArtifact.v1")
            registry[key] = descriptor(key)
        }

        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
            return "sha256:$digest"
        }
    }
}
