package org.graphiks.kanvas.gpu.renderer.passes

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskSampling
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState

/** Compact code/layout axes computed once while the prepared packet is recorded. */
internal data class GPUCorePrimitiveRenderPipelineStructuralKey(
    val shader: Shader,
    val topology: Topology,
    val blend: Blend,
    val clip: Clip,
    val sampleCount: Int = 1,
) {
    enum class Shader { DirectGeometry, AnalyticRRect, PathStencil }
    enum class Topology { DirectTriangleList, AnalyticRRect, StencilEdgeFan, StrokeStencilEdgeFan }
    enum class ClipGeometry { Rect, RRect, Path }

    sealed interface Blend {
        data class Fixed(
            val mode: GPUBlendMode,
            val sourceCoverage: GPUSourceCoverageEncoding,
            val state: GPUFixedFunctionBlendState,
        ) : Blend

        data class ShaderNoDestination(
            val mode: GPUBlendMode,
            val formulaId: String,
            val sourceCoverage: GPUSourceCoverageEncoding,
        ) : Blend

        data class ShaderWithDestination(
            val mode: GPUBlendMode,
            val formulaId: String,
            val sourceCoverage: GPUSourceCoverageEncoding,
        ) : Blend

        data class NoOp(val mode: GPUBlendMode) : Blend
        data class Unsupported(val mode: GPUBlendMode) : Blend
    }

    sealed interface Clip {
        data object None : Clip

        data class Analytic(
            val geometry: ClipGeometry,
            val antiAlias: Boolean,
        ) : Clip

        data class Stencil(
            val compare: GPUClipStencilCompare,
            val passOperation: GPUClipStencilOperation,
            val failOperation: GPUClipStencilOperation,
            val depthFailOperation: GPUClipStencilOperation,
            val readMask: UInt,
            val writeMask: UInt,
        ) : Clip

        data class Mask(
            val sampling: GPUClipMaskSampling,
            val invert: Boolean,
            val depthStencilRequired: Boolean,
        ) : Clip

        data object Refused : Clip
    }

    init {
        require(sampleCount > 0) { "CorePrimitive structural sample count must be positive" }
    }

    /** Stable public/dump identity. Called only by the recording builder or explicit evidence tests. */
    fun stableRenderPipelineKey(prefix: String): GPURenderPipelineKey {
        val preimage = buildString {
            append("role=shading")
            append("|shader=").append(shader.name)
            append("|layout=dynamic-uniform32-v2")
            append("|topology=").append(topology.name)
            append("|frontFace=ccw|cull=none|target=rgba8unorm")
            append("|samples=").append(sampleCount)
            append("|blend=").append(blend)
            append("|clip=").append(clip)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(preimage.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return GPURenderPipelineKey("$prefix.$digest")
    }
}

internal fun corePrimitiveRenderPipelineStructuralKey(
    semantic: GPUDrawSemanticPayload.CorePrimitive,
    clipExecutionPlan: GPUClipExecutionPlan,
    blendPlan: GPUBlendPlan,
): GPUCorePrimitiveRenderPipelineStructuralKey = GPUCorePrimitiveRenderPipelineStructuralKey(
    shader = when (val geometry = semantic.geometry) {
        is GPUCorePrimitiveGeometry.Rect ->
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry
        is GPUCorePrimitiveGeometry.RRect ->
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticRRect
        is GPUCorePrimitiveGeometry.TriangulatedPath -> when (geometry.geometryMode) {
            GPUCorePrimitiveGeometryMode.DirectTriangles ->
                GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry
            GPUCorePrimitiveGeometryMode.StencilEdgeFan,
            GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan,
            -> GPUCorePrimitiveRenderPipelineStructuralKey.Shader.PathStencil
        }
    },
    topology = when (val geometry = semantic.geometry) {
        is GPUCorePrimitiveGeometry.Rect ->
            GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList
        is GPUCorePrimitiveGeometry.RRect ->
            GPUCorePrimitiveRenderPipelineStructuralKey.Topology.AnalyticRRect
        is GPUCorePrimitiveGeometry.TriangulatedPath -> when (geometry.geometryMode) {
            GPUCorePrimitiveGeometryMode.DirectTriangles ->
                GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList
            GPUCorePrimitiveGeometryMode.StencilEdgeFan ->
                GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StencilEdgeFan
            GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan ->
                GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StrokeStencilEdgeFan
        }
    },
    blend = blendPlan.corePrimitiveStructuralBlend(),
    clip = clipExecutionPlan.corePrimitiveStructuralClip(),
)

private fun GPUBlendPlan.corePrimitiveStructuralBlend():
    GPUCorePrimitiveRenderPipelineStructuralKey.Blend = when (this) {
    is GPUBlendPlan.FixedFunctionBlend -> GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed(
        mode,
        sourceCoverageEncoding,
        state,
    )
    is GPUBlendPlan.ShaderBlendNoDstRead ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderNoDestination(
            mode,
            formulaId,
            sourceCoverageEncoding,
        )
    is GPUBlendPlan.ShaderBlendWithDstRead ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination(
            mode,
            formulaId,
            sourceCoverageEncoding,
        )
    is GPUBlendPlan.LayerCompositeBlend -> child.corePrimitiveStructuralBlend()
    is GPUBlendPlan.NoOp -> GPUCorePrimitiveRenderPipelineStructuralKey.Blend.NoOp(mode)
    is GPUBlendPlan.UnsupportedBlend -> GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Unsupported(mode)
}

private fun GPUClipExecutionPlan.corePrimitiveStructuralClip():
    GPUCorePrimitiveRenderPipelineStructuralKey.Clip = when (this) {
    GPUClipExecutionPlan.NoClip,
    is GPUClipExecutionPlan.ScissorOnly,
    -> GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None
    is GPUClipExecutionPlan.AnalyticCoverage ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic(
            geometry = when (geometry) {
                is GPUClipExecutionGeometry.Rect ->
                    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect
                is GPUClipExecutionGeometry.RRect ->
                    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect
                is GPUClipExecutionGeometry.Path ->
                    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Path
            },
            antiAlias = antiAlias,
        )
    is GPUClipExecutionPlan.StencilCoverage -> GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Stencil(
        compare = consumer.compare,
        passOperation = consumer.passOperation,
        failOperation = consumer.failOperation,
        depthFailOperation = consumer.depthFailOperation,
        readMask = consumer.readMask,
        writeMask = consumer.writeMask,
    )
    is GPUClipExecutionPlan.CoverageMask -> GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Mask(
        sampling = consumer.sampling,
        invert = consumer.invert,
        depthStencilRequired = depthStencilRequired,
    )
    is GPUClipExecutionPlan.Refused -> GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Refused
}

/** Builder-owned plan and bytes shared by every direct draw in one prepared pass. */
internal class GPUCorePrimitiveUniformSlabSeal(
    val plan: GPUUniformSlabPlan,
    commandIds: List<Int>,
    packedBytes: ByteArray,
) {
    private val commandIdsSnapshot = immutableList(commandIds)
    private val packedBytesSnapshot = packedBytes.copyOf()

    val commandIds: List<Int>
        get() = commandIdsSnapshot

    val drawCount: Int
        get() = commandIdsSnapshot.size

    init {
        require(commandIdsSnapshot.isNotEmpty() && commandIdsSnapshot.distinct().size == commandIdsSnapshot.size) {
            "CorePrimitive uniform slab seal requires unique draw commands"
        }
        require(plan.slots.size == commandIdsSnapshot.size) {
            "CorePrimitive uniform slab slots must match draw commands"
        }
        require(plan.totalBytes == packedBytesSnapshot.size.toLong()) {
            "CorePrimitive packed uniform bytes must match the planned slab size"
        }
    }

    fun hasExactPayloads(
        expectedCommandIds: List<Int>,
        uniformBytesByDraw: List<List<Int>>,
    ): Boolean {
        if (expectedCommandIds != commandIdsSnapshot || uniformBytesByDraw.size != plan.slots.size) return false
        return plan.slots.indices.all { index ->
            hasExactPayload(index, expectedCommandIds[index], uniformBytesByDraw[index])
        }
    }

    fun hasExactPayload(index: Int, commandId: Int, bytes: List<Int>): Boolean {
        if (index !in plan.slots.indices || commandIdsSnapshot[index] != commandId) return false
        val slot = plan.slots[index]
        return slot.payloadBytes == bytes.size.toLong() &&
            slot.alignedOffset >= 0L &&
            slot.alignedOffset <= packedBytesSnapshot.size.toLong() - bytes.size.toLong() &&
            bytes.indices.all { byteIndex ->
                packedBytesSnapshot[slot.alignedOffset.toInt() + byteIndex] == bytes[byteIndex].toByte()
            }
    }

    /** Internal zero-copy borrow valid only for the immediate queue upload. */
    fun packedBytesForUpload(): ByteArray = packedBytesSnapshot

    /** Defensive snapshot for tests and non-hot-path evidence. */
    fun packedBytesSnapshot(): ByteArray = packedBytesSnapshot.copyOf()
}

/** One-shot authority attached by the prepared-frame builder before the packet escapes. */
internal data class GPUCorePrimitivePreparedPacketAuthority(
    val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    val renderPipelineKey: GPURenderPipelineKey,
    val uniformSlabSeal: GPUCorePrimitiveUniformSlabSeal?,
)
