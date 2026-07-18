package org.graphiks.kanvas.gpu.renderer.passes

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskSampling
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
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
    enum class UniformLayout(val stableIdentity: String) {
        DynamicUniform32V2("dynamic-uniform32-v2"),
        AnalyticClipUniform64V1("dynamic-uniform64-analytic-clip-v1"),
        AnalyticClipUniform160V1("dynamic-uniform160-analytic-clip-intersection4-v1"),
    }

    /** Derived executable ABI axis. It deliberately leaves the legacy constructor and hashes intact. */
    val uniformLayout: UniformLayout
        get() = when {
            role == Role.Shading && clip is Clip.Analytic -> UniformLayout.AnalyticClipUniform64V1
            role == Role.Shading && clip == Clip.AnalyticIntersection4 ->
                UniformLayout.AnalyticClipUniform160V1
            else -> UniformLayout.DynamicUniform32V2
        }

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
                require(readMask <= 0xffu && writeMask <= 0xffu) {
                    "CorePrimitive stencil masks must fit stencil8"
                }
                require(writeMask != 0u || isDirectPathAttachmentNeutral()) {
                    "A zero-write stencil state must be the exact direct path-attachment state"
                }
            }

            internal fun isDirectPathAttachmentNeutral(): Boolean =
                readMask == 0u && writeMask == 0u &&
                    front.isDirectPathAttachmentNeutral() && back.isDirectPathAttachmentNeutral()

            private fun StencilFace.isDirectPathAttachmentNeutral(): Boolean =
                compare == GPUClipStencilCompare.Always &&
                    passOperation == GPUClipStencilOperation.Keep &&
                    failOperation == GPUClipStencilOperation.Keep &&
                    depthFailOperation == GPUClipStencilOperation.Keep
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

        /** Fixed-capacity analytic intersection; count, kinds, AA, and values are uniform-only. */
        data object AnalyticIntersection4 : Clip

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
            Role.Shading -> require(
                depthStencil == DepthStencil.None ||
                    (depthStencil as? DepthStencil.Stencil)?.isDirectPathAttachmentNeutral() == true
            ) {
                "CorePrimitive shading keys accept only no attachment or the neutral path attachment state"
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
                append("|layout=").append(uniformLayout.stableIdentity)
                append("|topology=").append(topology.name)
                append("|frontFace=ccw|cull=none|target=rgba8unorm")
                append("|samples=").append(sampleCount)
                append("|blend=").append(blend)
                append("|clip=").append(clip)
                if (depthStencil != DepthStencil.None) {
                    append("|depthStencil=").append(depthStencil)
                }
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

/** Exact no-op D24S8 state required when a direct draw shares a pass with path stencil draws. */
internal fun corePrimitiveDirectPathDepthStencilState():
    GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil =
    CORE_PRIMITIVE_DIRECT_PATH_DEPTH_STENCIL_STATE

/** Closed handle-free projection consumed by native lowering without importing clip semantics. */
internal enum class GPUCorePrimitivePathStencilStructuralProgram {
    ProducerWinding,
    ProducerEvenOdd,
    CoverRegular,
    CoverInverse,
}

internal fun GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.pathStencilStructuralProgramOrNull():
    GPUCorePrimitivePathStencilStructuralProgram? = when (this) {
    PATH_STENCIL_PRODUCER_WINDING_STATE -> GPUCorePrimitivePathStencilStructuralProgram.ProducerWinding
    PATH_STENCIL_PRODUCER_EVEN_ODD_STATE -> GPUCorePrimitivePathStencilStructuralProgram.ProducerEvenOdd
    PATH_STENCIL_COVER_REGULAR_STATE -> GPUCorePrimitivePathStencilStructuralProgram.CoverRegular
    PATH_STENCIL_COVER_INVERSE_STATE -> GPUCorePrimitivePathStencilStructuralProgram.CoverInverse
    else -> null
}

private val CORE_PRIMITIVE_DIRECT_PATH_DEPTH_STENCIL_STATE = run {
    val neutralFace = GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace(
        compare = GPUClipStencilCompare.Always,
        passOperation = GPUClipStencilOperation.Keep,
        failOperation = GPUClipStencilOperation.Keep,
        depthFailOperation = GPUClipStencilOperation.Keep,
    )
    GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil(
        format = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencilFormat.Depth24PlusStencil8,
        front = neutralFace,
        back = neutralFace,
        readMask = 0u,
        writeMask = 0u,
    )
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

private val PATH_STENCIL_PRODUCER_WINDING_STATE = pathStencilState(
    GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
    GPUCorePrimitiveFillRule.Winding,
    inverseFill = false,
)

private val PATH_STENCIL_PRODUCER_EVEN_ODD_STATE = pathStencilState(
    GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
    GPUCorePrimitiveFillRule.EvenOdd,
    inverseFill = false,
)

private val PATH_STENCIL_COVER_REGULAR_STATE = pathStencilState(
    GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
    GPUCorePrimitiveFillRule.Winding,
    inverseFill = false,
)

private val PATH_STENCIL_COVER_INVERSE_STATE = pathStencilState(
    GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
    GPUCorePrimitiveFillRule.Winding,
    inverseFill = true,
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
                    if (geometry.radii.chunked(2).all { pair -> pair == geometry.radii.take(2) } &&
                        geometry.radii.take(2).any { it == 0f }
                    ) {
                        GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect
                    } else {
                        GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect
                    }
                is GPUClipExecutionGeometry.Path ->
                    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Path
            },
            antiAlias = antiAlias,
        )
    is GPUClipExecutionPlan.AnalyticIntersection ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Clip.AnalyticIntersection4
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

/** Handle-free route predicate used by execution without depending on clip-domain concrete types. */
internal fun GPUClipExecutionPlan.isCorePrimitiveNoClipOrScissorExecution(): Boolean =
    this == GPUClipExecutionPlan.NoClip || this is GPUClipExecutionPlan.ScissorOnly

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

/** Immutable per-packet authority for the separate analytic-clip uniform64 slab. */
internal class GPUCorePrimitiveAnalyticClipUniformSeal(
    val plan: GPUUniformSlabPlan,
    val slotIndex: Int,
    val commandId: Int,
    val packetId: GPUDrawPacketID,
    val clipCanonicalIdentity: String,
    val clipType: GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry,
    clipBounds: List<Float>,
    clipRadii: List<Float>,
    val antiAlias: Boolean,
    val conservativeScissor: GPUPixelBounds,
    val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    val renderPipelineKey: GPURenderPipelineKey,
    val bindingLayoutHash: String,
    val resourceGeneration: Long,
    payloadBytes: ByteArray,
) {
    private val clipBoundsSnapshot = immutableList(clipBounds)
    private val clipRadiiSnapshot = immutableList(clipRadii)
    private val payloadBytesSnapshot = payloadBytes.copyOf()
    private val slot = plan.slots.getOrNull(slotIndex)
        ?: error("Analytic clip uniform seal slot index is outside its slab plan")

    val clipBounds: List<Float>
        get() = clipBoundsSnapshot
    val clipRadii: List<Float>
        get() = clipRadiiSnapshot
    val payloadBytes: Long
        get() = slot.payloadBytes
    val alignedOffset: Long
        get() = slot.alignedOffset
    val alignmentBytes: Long
        get() = plan.alignmentBytes
    val deviceGeneration: Long
        get() = plan.deviceGeneration

    init {
        require(commandId >= 0) { "Analytic clip uniform seal command id must be non-negative" }
        require(clipCanonicalIdentity.isNotBlank()) { "Analytic clip canonical identity must not be blank" }
        require(clipType == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect ||
            clipType == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect
        ) { "Analytic clip uniform seal accepts only rect or rrect" }
        require(clipBoundsSnapshot.size == 4 && clipBoundsSnapshot.all(Float::isFinite)) {
            "Analytic clip bounds require four finite scalars"
        }
        require(clipRadiiSnapshot.size == 8 && clipRadiiSnapshot.all { it.isFinite() && it >= 0f }) {
            "Analytic clip radii require four finite non-negative pairs"
        }
        require(!conservativeScissor.isEmpty) { "Analytic clip conservative scissor must not be empty" }
        require(structuralPipelineKey.uniformLayout ==
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1
        ) { "Analytic clip uniform seal requires the uniform64 structural ABI" }
        require(bindingLayoutHash.isNotBlank()) { "Analytic clip binding layout hash must not be blank" }
        require(resourceGeneration >= 0L) { "Analytic clip resource generation must be non-negative" }
        require(slot.payloadBytes == 64L && payloadBytesSnapshot.size == 64) {
            "Analytic clip uniform seal requires exactly 64 payload bytes"
        }
    }

    fun payloadBytesSnapshot(): ByteArray = payloadBytesSnapshot.copyOf()

    internal fun hasExactPayload(expected: ByteArray): Boolean =
        expected.size == 64 && payloadBytesSnapshot.contentEquals(expected)
}

/** One immutable, packet-local element retained by the uniform160 seal. */
internal class GPUCorePrimitiveAnalyticIntersectionElementSeal(
    val clipType: GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry,
    clipBounds: List<Float>,
    clipRadii: List<Float>,
    val antiAlias: Boolean,
) {
    val clipBounds: List<Float> = immutableList(clipBounds)
    val clipRadii: List<Float> = immutableList(clipRadii)

    init {
        require(clipType == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect ||
            clipType == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect
        ) { "Analytic intersection elements accept only rect or rrect" }
        require(this.clipBounds.size == 4 && this.clipBounds.all(Float::isFinite)) {
            "Analytic intersection element bounds require four finite scalars"
        }
        require(this.clipRadii.size == 2 && this.clipRadii.all { it.isFinite() && it >= 0f }) {
            "Analytic intersection element radii require one finite non-negative pair"
        }
        require(clipType != GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect ||
            this.clipRadii.all { it == 0f }
        ) { "Analytic intersection rect elements must retain zero radii" }
    }

    override fun equals(other: Any?): Boolean = this === other ||
        other is GPUCorePrimitiveAnalyticIntersectionElementSeal &&
        clipType == other.clipType && clipBounds == other.clipBounds &&
        clipRadii == other.clipRadii && antiAlias == other.antiAlias

    override fun hashCode(): Int = listOf(clipType, clipBounds, clipRadii, antiAlias).hashCode()
}

/** Immutable per-packet authority for the fixed-capacity analytic-intersection uniform160 slab. */
internal class GPUCorePrimitiveAnalyticIntersectionUniformSeal(
    val plan: GPUUniformSlabPlan,
    val slotIndex: Int,
    val commandId: Int,
    val packetId: GPUDrawPacketID,
    val clipCanonicalIdentity: String,
    elements: List<GPUCorePrimitiveAnalyticIntersectionElementSeal>,
    val conservativeScissor: GPUPixelBounds,
    val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    val renderPipelineKey: GPURenderPipelineKey,
    val bindingLayoutHash: String,
    val resourceGeneration: Long,
    payloadBytes: ByteArray,
) {
    val elements: List<GPUCorePrimitiveAnalyticIntersectionElementSeal> = immutableList(elements)
    private val payloadBytesSnapshot = payloadBytes.copyOf()
    private val slot = plan.slots.getOrNull(slotIndex)
        ?: error("Analytic intersection uniform seal slot index is outside its slab plan")

    val payloadBytes: Long get() = slot.payloadBytes
    val alignedOffset: Long get() = slot.alignedOffset
    val alignmentBytes: Long get() = plan.alignmentBytes
    val deviceGeneration: Long get() = plan.deviceGeneration

    init {
        require(commandId >= 0) { "Analytic intersection uniform seal command id must be non-negative" }
        require(clipCanonicalIdentity.isNotBlank()) { "Analytic intersection canonical identity must not be blank" }
        require(this.elements.size in 2..4) { "Analytic intersection uniform seal requires two to four elements" }
        require(!conservativeScissor.isEmpty) { "Analytic intersection conservative scissor must not be empty" }
        require(structuralPipelineKey.uniformLayout ==
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1
        ) { "Analytic intersection uniform seal requires the uniform160 structural ABI" }
        require(bindingLayoutHash.isNotBlank()) { "Analytic intersection binding layout hash must not be blank" }
        require(resourceGeneration >= 0L) { "Analytic intersection resource generation must be non-negative" }
        require(slot.payloadBytes == 160L && payloadBytesSnapshot.size == 160) {
            "Analytic intersection uniform seal requires exactly 160 payload bytes"
        }
    }

    fun payloadBytesSnapshot(): ByteArray = payloadBytesSnapshot.copyOf()

    internal fun hasExactPayload(expected: ByteArray): Boolean =
        expected.size == 160 && payloadBytesSnapshot.contentEquals(expected)
}

/** One-shot authority attached by the prepared-frame builder before the packet escapes. */
internal data class GPUCorePrimitivePreparedPacketAuthority(
    val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    val renderPipelineKey: GPURenderPipelineKey,
    val uniformSlabSeal: GPUCorePrimitiveUniformSlabSeal?,
    val analyticClipUniformSeal: GPUCorePrimitiveAnalyticClipUniformSeal? = null,
    val analyticIntersectionUniformSeal: GPUCorePrimitiveAnalyticIntersectionUniformSeal? = null,
)
