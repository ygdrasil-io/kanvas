package org.graphiks.kanvas.gpu.renderer.passes

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskSampling
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
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
    val role: Role = Role.Shading,
    val frontFace: FrontFace = FrontFace.Ccw,
    val cullMode: CullMode = CullMode.None,
    val colorFormat: ColorFormat = ColorFormat.Rgba8Unorm,
    val depthStencil: DepthStencil = DepthStencil.None,
    val sampleCount: Int = 1,
) {
    enum class Role { Shading, PathStencilProducer, PathStencilCover }
    enum class Shader { DirectGeometry, AnalyticRRect, PathStencil }
    enum class Topology { DirectTriangleList, AnalyticRRect, StencilEdgeFan, StrokeStencilEdgeFan }
    enum class FrontFace { Ccw }
    enum class CullMode { None }
    enum class ColorFormat { Rgba8Unorm }
    enum class DepthStencilFormat { Depth24PlusStencil8 }
    enum class ClipGeometry { Rect, RRect, Path }

    data class StencilFace(
        val compare: GPUClipStencilCompare,
        val passOperation: GPUClipStencilOperation,
        val failOperation: GPUClipStencilOperation,
        val depthFailOperation: GPUClipStencilOperation,
    )

    sealed interface DepthStencil {
        data object None : DepthStencil

        data class Stencil(
            val format: DepthStencilFormat,
            val front: StencilFace,
            val back: StencilFace,
            val readMask: UInt,
            val writeMask: UInt,
        ) : DepthStencil {
            init {
                require(readMask <= 0xffu && writeMask in 1u..0xffu) {
                    "CorePrimitive path stencil masks must fit stencil8 and retain a reset write mask"
                }
            }
        }
    }

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
        data object ColorWriteNone : Blend
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
        when (role) {
            Role.Shading -> require(depthStencil == DepthStencil.None) {
                "CorePrimitive shading keys cannot retain path stencil state"
            }
            Role.PathStencilProducer,
            Role.PathStencilCover,
            -> {
                require(shader == Shader.PathStencil && depthStencil is DepthStencil.Stencil) {
                    "CorePrimitive path stencil roles require the path shader and exact stencil state"
                }
                require(sampleCount == 1) { "CorePrimitive path stencil roles are single-sample" }
            }
        }
    }

    /** Stable public/dump identity. Called only by the recording builder or explicit evidence tests. */
    fun stableRenderPipelineKey(prefix: String): GPURenderPipelineKey {
        val preimage = when (role) {
            Role.Shading -> buildString {
                // Compatibility ABI: direct/analytic shading descriptors did not change in B3.2.
                append("role=shading")
                append("|shader=").append(shader.name)
                append("|layout=dynamic-uniform32-v2")
                append("|topology=").append(topology.name)
                append("|frontFace=ccw|cull=none|target=rgba8unorm")
                append("|samples=").append(sampleCount)
                append("|blend=").append(blend)
                append("|clip=").append(clip)
            }
            Role.PathStencilProducer,
            Role.PathStencilCover,
            -> buildString {
                append("role=").append(role.name)
                append("|shader=").append(shader.name)
                append("|layout=dynamic-uniform32-v2")
                append("|topology=").append(topology.name)
                append("|frontFace=").append(frontFace.name)
                append("|cull=").append(cullMode.name)
                append("|target=").append(colorFormat.name)
                append("|depthStencil=").append(depthStencil)
                append("|samples=").append(sampleCount)
                append("|blend=").append(blend)
                append("|clip=").append(clip)
            }
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

/** Pure, handle-free structural authority for the two path stencil-cover roles. */
internal fun corePrimitivePathStencilRenderPipelineStructuralKey(
    semantic: GPUDrawSemanticPayload.CorePrimitive,
    role: GPUCorePrimitiveRenderPipelineStructuralKey.Role,
    clipExecutionPlan: GPUClipExecutionPlan,
    blendPlan: GPUBlendPlan,
): GPUCorePrimitiveRenderPipelineStructuralKey {
    require(role != GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading) {
        "Path stencil structural authority requires a producer or cover role"
    }
    require(semantic.coverageMode == GPUCorePrimitiveCoverageMode.Stencil1x) {
        "Path stencil structural authority requires Stencil1x coverage"
    }
    require(
        clipExecutionPlan == GPUClipExecutionPlan.NoClip ||
            clipExecutionPlan is GPUClipExecutionPlan.ScissorOnly,
    ) {
        "Path stencil structural authority currently accepts only no clip or dynamic scissor"
    }
    val geometry = semantic.geometry as? GPUCorePrimitiveGeometry.TriangulatedPath
        ?: error("Path stencil structural authority requires triangulated path geometry")
    require(geometry.geometryMode != GPUCorePrimitiveGeometryMode.DirectTriangles) {
        "Path stencil structural authority requires stencil edge-fan geometry"
    }

    return GPUCorePrimitiveRenderPipelineStructuralKey(
        role = role,
        shader = GPUCorePrimitiveRenderPipelineStructuralKey.Shader.PathStencil,
        topology = when (role) {
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer -> when (geometry.geometryMode) {
                GPUCorePrimitiveGeometryMode.StencilEdgeFan ->
                    GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StencilEdgeFan
                GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan ->
                    GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StrokeStencilEdgeFan
                GPUCorePrimitiveGeometryMode.DirectTriangles -> error("Validated above")
            }
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover ->
                GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading -> error("Validated above")
        },
        blend = when (role) {
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer ->
                GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover ->
                blendPlan.corePrimitiveStructuralBlend()
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading -> error("Validated above")
        },
        clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None,
        depthStencil = pathStencilState(role, geometry.fillRule, geometry.inverseFill),
        sampleCount = 1,
    )
}

private fun pathStencilState(
    role: GPUCorePrimitiveRenderPipelineStructuralKey.Role,
    fillRule: GPUCorePrimitiveFillRule,
    inverseFill: Boolean,
): GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil {
    fun face(
        compare: GPUClipStencilCompare,
        pass: GPUClipStencilOperation,
        fail: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
        depthFail: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
    ) = GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace(
        compare = compare,
        passOperation = pass,
        failOperation = fail,
        depthFailOperation = depthFail,
    )

    val (front, back) = when (role) {
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer -> when (fillRule) {
            GPUCorePrimitiveFillRule.Winding ->
                face(GPUClipStencilCompare.Always, GPUClipStencilOperation.IncrementWrap) to
                    face(GPUClipStencilCompare.Always, GPUClipStencilOperation.DecrementWrap)
            GPUCorePrimitiveFillRule.EvenOdd ->
                face(GPUClipStencilCompare.Always, GPUClipStencilOperation.Invert) to
                    face(GPUClipStencilCompare.Always, GPUClipStencilOperation.Invert)
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover -> {
            val compare = if (inverseFill) GPUClipStencilCompare.Equal else GPUClipStencilCompare.NotEqual
            val fail = if (inverseFill) GPUClipStencilOperation.Zero else GPUClipStencilOperation.Keep
            val pass = if (inverseFill) GPUClipStencilOperation.Keep else GPUClipStencilOperation.Zero
            val depthFail = if (inverseFill) GPUClipStencilOperation.Keep else GPUClipStencilOperation.Zero
            face(compare, pass, fail, depthFail) to face(compare, pass, fail, depthFail)
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading ->
            error("Path stencil state cannot be built for shading")
    }
    return GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil(
        format = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencilFormat.Depth24PlusStencil8,
        front = front,
        back = back,
        readMask = 0xffu,
        writeMask = when (role) {
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer -> when (fillRule) {
                GPUCorePrimitiveFillRule.Winding -> 0xffu
                GPUCorePrimitiveFillRule.EvenOdd -> 0x01u
            }
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover -> 0xffu
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading -> error("Validated above")
        },
    )
}

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
