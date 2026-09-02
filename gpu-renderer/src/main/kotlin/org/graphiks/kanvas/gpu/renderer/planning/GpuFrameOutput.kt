package org.graphiks.kanvas.gpu.renderer.planning

import java.util.Collections
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderOutput

public enum class GpuFrameChannelOrder { RGBA }

public data class GpuFrameMetrics(
    public val opsDispatched: Int,
    public val pipelineCount: Int,
    public val drawCallCount: Int,
    public val coverage: Float,
    public val coverageMeasured: Boolean,
) {
    init {
        require(opsDispatched >= 0 && pipelineCount >= 0 && drawCallCount >= 0)
        require(coverage in 0f..1f)
    }
}

/** Immutable, handle-free result of one completed GPU frame. */
public class GpuFrameOutput private constructor(
    public val width: Int,
    public val height: Int,
    public val rowStrideBytes: Int,
    public val channelOrder: GpuFrameChannelOrder,
    bytes: ByteArray,
    public val metrics: GpuFrameMetrics,
    diagnostics: List<RenderDiagnostic>,
    structuralSteps: List<String>,
    nativeEvidenceCounters: Map<String, Long>,
    nativeEvidenceScopeKinds: List<String>,
) : RenderOutput {
    private val ownedBytes = bytes.copyOf()
    private val ownedDiagnostics = Collections.unmodifiableList(ArrayList(diagnostics))
    private val ownedStructuralSteps = Collections.unmodifiableList(ArrayList(structuralSteps))
    private val ownedCounters = Collections.unmodifiableMap(LinkedHashMap(nativeEvidenceCounters))
    private val ownedScopeKinds = Collections.unmodifiableList(ArrayList(nativeEvidenceScopeKinds))

    public fun copyBytes(): ByteArray = ownedBytes.copyOf()
    public fun diagnostics(): List<RenderDiagnostic> = ownedDiagnostics
    public fun structuralSteps(): List<String> = ownedStructuralSteps
    public fun nativeEvidenceCounters(): Map<String, Long> = ownedCounters
    public fun nativeEvidenceScopeKinds(): List<String> = ownedScopeKinds

    public companion object {
        public fun of(
            width: Int, height: Int, rowStrideBytes: Int, channelOrder: GpuFrameChannelOrder,
            bytes: ByteArray, metrics: GpuFrameMetrics, diagnostics: List<RenderDiagnostic>,
            structuralSteps: List<String>, nativeEvidenceCounters: Map<String, Long>,
            nativeEvidenceScopeKinds: List<String>,
        ): GpuFrameOutput {
            require(width > 0 && height > 0)
            val tight = Math.multiplyExact(width, 4)
            require(rowStrideBytes == tight)
            require(bytes.size == Math.multiplyExact(rowStrideBytes, height))
            require(nativeEvidenceCounters.keys.all(String::isNotBlank) && nativeEvidenceCounters.values.all { it >= 0L })
            return GpuFrameOutput(width, height, rowStrideBytes, channelOrder, bytes, metrics, diagnostics, structuralSteps, nativeEvidenceCounters, nativeEvidenceScopeKinds)
        }
    }
}
