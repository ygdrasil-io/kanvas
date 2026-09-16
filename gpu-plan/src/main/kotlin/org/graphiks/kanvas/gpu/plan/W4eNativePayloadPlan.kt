package org.graphiks.kanvas.gpu.plan

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import org.graphiks.math.geometry.ClipGeometryF32
import org.graphiks.math.geometry.InverseInteriorCoverageF32
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.SizeI32

/**
 * Immutable native V/I/U payload authority for one sealed W4e graph.
 *
 * W4e has more native consumers than the W4d construction seam: clip-path producers,
 * analytic Rect/RRect producers, inverse interiors, and finite inverse domains all execute
 * after graph publication.  Their bytes therefore belong to this graph-owned plan, rather than
 * being discovered by the backend while it is creating native buffers.
 */
public class W4eNativePayloadPlan private constructor(
    public val vertexResourceId: PlanResourceId,
    public val indexResourceId: PlanResourceId,
    public val uniformResourceId: PlanResourceId,
    vertexData: FloatArray,
    indexData: IntArray,
    uniformData: ByteArray,
    geometrySlices: List<W4eNativeGeometrySlice>,
    uniformSlices: List<W4eNativeUniformSlice>,
    public val vertexUsefulBytes: Long,
    public val indexUsefulBytes: Long,
    public val uniformUsefulBytes: Long,
    public val uniformReservedBytes: Long,
    public val vertexCapacityBytes: Long,
    public val indexCapacityBytes: Long,
    public val uniformCapacityBytes: Long,
) {
    private val vertexDataSnapshot: FloatArray = vertexData.copyOf()
    private val indexDataSnapshot: IntArray = indexData.copyOf()
    private val uniformDataSnapshot: ByteArray = uniformData.copyOf()
    private val geometrySlicesSnapshot: List<W4eNativeGeometrySlice> =
        Collections.unmodifiableList(geometrySlices.toList())
    private val uniformSlicesSnapshot: List<W4eNativeUniformSlice> =
        Collections.unmodifiableList(uniformSlices.toList())

    public val geometrySlices: List<W4eNativeGeometrySlice>
        get() = geometrySlicesSnapshot

    public val uniformSlices: List<W4eNativeUniformSlice>
        get() = uniformSlicesSnapshot

    public fun copyVertexData(): FloatArray = vertexDataSnapshot.copyOf()

    public fun copyIndexData(): IntArray = indexDataSnapshot.copyOf()

    public fun copyUniformData(): ByteArray = uniformDataSnapshot.copyOf()

    public fun geometrySlice(passId: String, purpose: String): W4eNativeGeometrySlice? =
        geometrySlicesSnapshot.singleOrNull { it.passId == passId && it.purpose == purpose }

    public fun uniformSlice(passId: String, purpose: String): W4eNativeUniformSlice? =
        uniformSlicesSnapshot.singleOrNull { it.passId == passId && it.purpose == purpose }

    /** Confirms that the graph published the same capacities this immutable payload reserved. */
    public fun matchesDeclaredResources(resources: List<PlanResource>): Boolean {
        val byId = resources.associateBy(PlanResource::id)
        return byId[vertexResourceId]?.let { resource ->
            resource.isExactW4eBuffer(PlanResourceRole.VertexData, PlanResourceUsage.Vertex) &&
                resource.byteSize == vertexCapacityBytes
        } == true && byId[indexResourceId]?.let { resource ->
            resource.isExactW4eBuffer(PlanResourceRole.IndexData, PlanResourceUsage.Index) &&
                resource.byteSize == indexCapacityBytes
        } == true && byId[uniformResourceId]?.let { resource ->
            resource.isExactW4eBuffer(PlanResourceRole.UniformData, PlanResourceUsage.Uniform) &&
                resource.byteSize == uniformCapacityBytes
        } == true
    }

    public companion object {
        /**
         * Builds the exact reusable native slab before [RenderGraph] publication.  A null result
         * means the graph cannot be represented within checked host, buffer-policy, or device
         * limits; callers turn that into a resource-limit diagnostic rather than publishing it.
         */
        public fun from(
            passes: List<PlanPass>,
            resources: List<PlanResource>,
            targetExtent: SizeI32,
            capabilities: PlanCapabilitySnapshot,
            materialPlanTable: MaterialPlanTable?,
        ): W4eNativePayloadPlan? = build(passes, resources, targetExtent, capabilities, materialPlanTable, null)

        /** Geometry bytes only; symbolic source refs are authenticated, never evaluated here. */
        internal fun fromDeferred(passes: List<PlanPass>,resources: List<PlanResource>,targetExtent: SizeI32,
            capabilities: PlanCapabilitySnapshot,sources: MaterialSourceConstructionTableV4): W4eNativePayloadPlan? =
            build(passes,resources,targetExtent,capabilities,null,null,sources)

        /** W4e producer-only payload. No color/path draw or material may enter this authority. */
        internal fun fromClipPrefix(
            passes: List<PlanPass>,
            resources: List<PlanResource>,
            targetExtent: SizeI32,
            capabilities: PlanCapabilitySnapshot,
            data: PlanDrawDataResources,
        ): W4eNativePayloadPlan? {
            require(passes.isNotEmpty() && passes.all { it is PlanPass.ClipMaskInitialize ||
                it is PlanPass.ClipMaskProducer || it is PlanPass.ClipMaskFold })
            return build(passes, resources, targetExtent, capabilities, null, data)
        }

        private fun build(
            passes: List<PlanPass>,
            resources: List<PlanResource>,
            targetExtent: SizeI32,
            capabilities: PlanCapabilitySnapshot,
            materialPlanTable: MaterialPlanTable?,
            clipOnlyData: PlanDrawDataResources?,
            deferredSources: MaterialSourceConstructionTableV4? = null,
        ): W4eNativePayloadPlan? = try {
            if (targetExtent.isEmpty()) return null
            val pathPasses = passes.filterIsInstance<PlanPass.PathRenderPass>()
            val dataResources = pathPasses.map(PlanPass.PathRenderPass::drawDataResources).distinct()
            if (clipOnlyData == null && (pathPasses.isEmpty() || dataResources.size != 1)) return null
            if (clipOnlyData != null && pathPasses.isNotEmpty()) return null
            val data = clipOnlyData ?: dataResources.single()
            val resourcesById = resources.associateBy(PlanResource::id)
            val vertex = resourcesById[data.vertex] ?: return null
            val index = resourcesById[data.index] ?: return null
            val uniform = resourcesById[data.uniform] ?: return null
            if (!vertex.isExactW4eBuffer(PlanResourceRole.VertexData, PlanResourceUsage.Vertex) ||
                !index.isExactW4eBuffer(PlanResourceRole.IndexData, PlanResourceUsage.Index) ||
                !uniform.isExactW4eBuffer(PlanResourceRole.UniformData, PlanResourceUsage.Uniform)
            ) return null

            val vertices = ArrayList<Float>()
            val indices = ArrayList<Int>()
            val geometrySlices = ArrayList<W4eNativeGeometrySlice>()
            val pendingUniforms = ArrayList<PendingUniform>()
            val geometryKeys = mutableSetOf<Pair<String, String>>()
            val uniformKeys = mutableSetOf<Pair<String, String>>()

            fun addGeometry(
                passId: String,
                purpose: String,
                rawVertices: FloatArray,
                rawIndices: IntArray,
            ): Boolean {
                if (!geometryKeys.add(passId to purpose) || rawVertices.size !in 2..Int.MAX_VALUE ||
                    rawVertices.size % 2 != 0 || rawIndices.isEmpty()
                ) return false
                val vertexCount = rawVertices.size / 2
                if (rawIndices.any { value -> value !in 0 until vertexCount }) return false
                val baseVertex = vertices.size / 2
                val firstIndex = indices.size
                val ndc = rawVertices.copyOf()
                ndc.indices.step(2).forEach { offset ->
                    ndc[offset] = ndc[offset] * 2f / targetExtent.width - 1f
                    ndc[offset + 1] = 1f - ndc[offset + 1] * 2f / targetExtent.height
                }
                vertices += ndc.toList()
                indices += rawIndices.toList()
                geometrySlices += W4eNativeGeometrySlice(
                    passId = passId,
                    purpose = purpose,
                    firstIndex = firstIndex,
                    indexCount = rawIndices.size,
                    baseVertex = baseVertex,
                    vertexCount = vertexCount,
                    maxLocalIndex = requireNotNull(rawIndices.maxOrNull()),
                )
                return true
            }

            fun addGeometry(passId: String, purpose: String, geometry: PathFillGeometryF32): Boolean {
                val direct = geometry.copyDirectTriangleF32OrNull()
                val fan = geometry.copyStencilEdgeFanF32OrNull()
                return when {
                    direct != null -> addGeometry(passId, purpose, direct.copyVerticesF32(), direct.copyIndicesI32())
                    fan != null -> addGeometry(passId, purpose, fan.copyVerticesF32(), fan.copyIndicesI32())
                    else -> false
                }
            }

            fun addDirectGeometry(passId: String, purpose: String, geometry: PathFillGeometryF32): Boolean {
                val direct = geometry.copyDirectTriangleF32OrNull() ?: return false
                return addGeometry(passId, purpose, direct.copyVerticesF32(), direct.copyIndicesI32())
            }

            fun addUniform(passId: String, purpose: String, values: FloatArray): Boolean {
                if (!uniformKeys.add(passId to purpose) || values.isEmpty() || values.size % 4 != 0) return false
                pendingUniforms += PendingUniform(passId, purpose, values.copyOf())
                return true
            }

            passes.forEach { pass ->
                when (pass) {
                    is PlanPass.ClipMaskProducer -> when (val geometry = pass.copyGeometryF32()) {
                        is ClipGeometryF32.Path -> if (!addGeometry(pass.id.value, PRODUCER_PATH, geometry.copyPathGeometryF32())) {
                            return null
                        }
                        else -> if (!addUniform(pass.id.value, PRODUCER_UNIFORM, producerBlock(geometry, pass))) {
                            return null
                        }
                    }
                    is PlanPass.PathRenderPass -> if (!collectPathPayload(
                            pass,
                            resourcesById,
                            materialPlanTable,
                            deferredSources,
                            ::addGeometry,
                            ::addDirectGeometry,
                            ::addUniform,
                        )) {
                        return null
                    }
                    else -> Unit
                }
            }

            val vertexData = vertices.toFloatArray()
            val indexData = indices.toIntArray()
            val vertexUsefulBytes = Math.multiplyExact(vertexData.size.toLong(), Float.SIZE_BYTES.toLong())
            val indexUsefulBytes = Math.multiplyExact(indexData.size.toLong(), Int.SIZE_BYTES.toLong())
            val uniformUsefulBytes = pendingUniforms.fold(0L) { total, entry ->
                Math.addExact(total, Math.multiplyExact(entry.values.size.toLong(), Float.SIZE_BYTES.toLong()))
            }
            val alignment = capabilities.minUniformBufferOffsetAlignment.toLong()
            var uniformOffset = 0L
            val uniformSlices = pendingUniforms.map { pending ->
                val byteSize = Math.multiplyExact(pending.values.size.toLong(), Float.SIZE_BYTES.toLong())
                val slice = W4eNativeUniformSlice(pending.passId, pending.purpose, uniformOffset, byteSize)
                uniformOffset = Math.addExact(uniformOffset, alignUp(byteSize, alignment))
                slice
            }
            val uniformReservedBytes = uniformOffset
            if (uniformReservedBytes > Int.MAX_VALUE.toLong()) return null
            val uniformData = ByteArray(uniformReservedBytes.toInt())
            uniformSlices.zip(pendingUniforms).forEach { (slice, pending) ->
                val bytes = floatsToBytes(pending.values)
                bytes.copyInto(uniformData, slice.offsetBytes.toInt())
            }
            val policy = capabilities.bufferAllocationPolicy
            val vertexCapacity = policy.reserve(PlanScratchBufferKind.Vertex, maxOf(vertexUsefulBytes, VERTEX_MINIMUM_BYTES)) ?: return null
            val indexCapacity = policy.reserve(PlanScratchBufferKind.Index, maxOf(indexUsefulBytes, INDEX_MINIMUM_BYTES)) ?: return null
            val uniformCapacity = policy.reserve(PlanScratchBufferKind.Uniform, maxOf(uniformReservedBytes, UNIFORM_MINIMUM_BYTES)) ?: return null
            if (listOf(vertexCapacity, indexCapacity, uniformCapacity).any { value ->
                    value > capabilities.maxBufferSizeBytes
                }) return null
            W4eNativePayloadPlan(
                vertexResourceId = data.vertex,
                indexResourceId = data.index,
                uniformResourceId = data.uniform,
                vertexData = vertexData,
                indexData = indexData,
                uniformData = uniformData,
                geometrySlices = geometrySlices,
                uniformSlices = uniformSlices,
                vertexUsefulBytes = vertexUsefulBytes,
                indexUsefulBytes = indexUsefulBytes,
                uniformUsefulBytes = uniformUsefulBytes,
                uniformReservedBytes = uniformReservedBytes,
                vertexCapacityBytes = vertexCapacity,
                indexCapacityBytes = indexCapacity,
                uniformCapacityBytes = uniformCapacity,
            )
        } catch (_: ArithmeticException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

        internal fun PlanResource.isExactW4eBuffer(
            expectedRole: PlanResourceRole,
            expectedUsage: PlanResourceUsage,
        ): Boolean = role == expectedRole && kind == PlanResourceKind.Buffer && format == null &&
            copyExtent() == null && lifetime == PlanResourceLifetime.FrameLocal &&
            usages() == setOf(PlanResourceUsage.CopyDestination, expectedUsage)

        private fun collectPathPayload(
            pass: PlanPass.PathRenderPass,
            resourcesById: Map<PlanResourceId, PlanResource>,
            materialPlanTable: MaterialPlanTable?,
            deferredSources: MaterialSourceConstructionTableV4?,
            addGeometry: (String, String, FloatArray, IntArray) -> Boolean,
            addDirectGeometry: (String, String, PathFillGeometryF32) -> Boolean,
            addUniform: (String, String, FloatArray) -> Boolean,
        ): Boolean {
            fun materialColor(): org.graphiks.math.color.ColorF32? {
                if (deferredSources != null) {
                    deferredSources.source(pass.draw.materialAuthority.materialPlanRef())
                    return org.graphiks.math.color.ColorF32.Transparent
                }
                return when (val authority = pass.draw.materialAuthority) {
                is PlanDrawMaterialAuthority.MaterialV5 -> error(W5gPlanDiagnostics.Unpromoted)
                is PlanDrawMaterialAuthority.MaterialV4 -> error(W5fPlanDiagnostics.Unpromoted)
                is PlanDrawMaterialAuthority.MaterialV3 -> error(W5eImagePlanDiagnostics.InvalidContract)
                is PlanDrawMaterialAuthority.MaterialV2 -> error(W5dPlanDiagnostics.CoordinatePlanSchema)
                is PlanDrawMaterialAuthority.MaterialV1 -> materialPlanTable?.let { table ->
                    // W5a material source is evaluated by the renderer fragment DAG. This
                    // historical geometry block does not own a flattened material value.
                    table.entry(authority.ref)
                    org.graphiks.math.color.ColorF32.Transparent
                }
                is PlanDrawMaterialAuthority.LegacyColorV1 -> authority.copyColorF32()
                }
            }
            fun drawableGeometry(): PathFillGeometryF32? = when (val geometry = pass.draw.copyPathGeometry()) {
                is PathDrawGeometry.Fill -> geometry.valueF32
                is PathDrawGeometry.Stroke -> geometry.valueF32.copyFillGeometryF32()
                is PathDrawGeometry.InverseDomainSource,
                PathDrawGeometry.Empty,
                -> null
            }
            fun addDrawable(purpose: String): Boolean {
                val geometry = drawableGeometry() ?: return false
                val direct = geometry.copyDirectTriangleF32OrNull()
                val fan = geometry.copyStencilEdgeFanF32OrNull()
                return when {
                    direct != null -> addGeometry(pass.id.value, purpose, direct.copyVerticesF32(), direct.copyIndicesI32())
                    fan != null -> addGeometry(pass.id.value, purpose, fan.copyVerticesF32(), fan.copyIndicesI32())
                    else -> false
                }
            }
            val consumer = pass.draw.w4eClipStrategyOrNull()
            val inverseDomain = consumer as? ClipPlanStrategy.InverseDomain
            val geometry = drawableGeometry()
            val preservesZeroInverseSource = inverseDomain?.geometryF32?.interiorCoverageF32 ==
                InverseInteriorCoverageF32.Zero && geometry != null
            val isStencilProducer = pass.phase in STENCIL_PRODUCER_PHASES
            val isStencilCover = pass.phase in STENCIL_COVER_PHASES

            if (consumer !is ClipPlanStrategy.InverseDomain &&
                pass.phase == PathRenderPhase.HardEdgeMaskProducer
            ) {
                // A truly empty inverse source is still a valid W4e hard-mask phase: the
                // materializer emits its sealed full-domain clear without V/I bytes.  Any
                // non-empty geometry remains a direct-triangle source as before.
                if (pass.draw.copyPathGeometry() is PathDrawGeometry.Empty) return true
                return geometry?.let { addDirectGeometry(pass.id.value, HARD_MASK_PRODUCER, it) } == true
            }
            if ((consumer !is ClipPlanStrategy.InverseDomain || preservesZeroInverseSource) && isStencilProducer) {
                return addDrawable(STENCIL_PRODUCER)
            }
            if ((consumer !is ClipPlanStrategy.InverseDomain || preservesZeroInverseSource) && isStencilCover) {
                if (inverseDomain?.geometryF32?.interiorCoverageF32 is InverseInteriorCoverageF32.Geometry) {
                    val interior = (inverseDomain.geometryF32.interiorCoverageF32 as InverseInteriorCoverageF32.Geometry)
                        .copyGeometryF32()
                    if (!addPathGeometry(pass.id.value, INVERSE_INTERIOR_COVER, interior, addGeometry)) return false
                }
                val targetRole = resourcesById[pass.target]?.role ?: return false
                if (targetRole.isW4eMaskTarget()) return true
                return addUniform(pass.id.value, STENCIL_COVER_UNIFORM, when (consumer) {
                    is ClipPlanStrategy.Mask,
                    is ClipPlanStrategy.InverseMask,
                    -> color8(materialColor() ?: return false)
                    is ClipPlanStrategy.InverseDomain,
                    null,
                    -> color4(materialColor() ?: return false)
                    else -> color4(materialColor() ?: return false)
                })
            }
            if (consumer is ClipPlanStrategy.InverseDomain &&
                (!preservesZeroInverseSource || (!isStencilProducer && !isStencilCover))
            ) {
                val inverse = consumer.geometryF32
                return when (val interior = inverse.interiorCoverageF32) {
                    InverseInteriorCoverageF32.Zero -> {
                        if (!addUniform(pass.id.value, INVERSE_DOMAIN_ZERO_UNIFORM, color4(materialColor() ?: return false))) return false
                        geometry?.copyDirectTriangleF32OrNull()?.let { direct ->
                            addGeometry(
                                pass.id.value,
                                INVERSE_DOMAIN_ZERO_SOURCE,
                                direct.copyVerticesF32(),
                                direct.copyIndicesI32(),
                            )
                        } ?: true
                    }
                    is InverseInteriorCoverageF32.Geometry -> {
                        if (geometry == null || !addUniform(
                                pass.id.value,
                                INVERSE_DOMAIN_UNIFORM,
                                color4(materialColor() ?: return false),
                            )) return false
                        val domain = inverse.copyDomainI32()
                        if (!addGeometry(
                                pass.id.value,
                                INVERSE_DOMAIN_QUAD,
                                floatArrayOf(
                                    domain.left.toFloat(), domain.top.toFloat(),
                                    domain.right.toFloat(), domain.top.toFloat(),
                                    domain.right.toFloat(), domain.bottom.toFloat(),
                                    domain.left.toFloat(), domain.bottom.toFloat(),
                                ),
                                intArrayOf(0, 1, 2, 0, 2, 3),
                            )
                        ) return false
                        addPathGeometry(pass.id.value, INVERSE_DOMAIN_INTERIOR, interior.copyGeometryF32(), addGeometry)
                    }
                }
            }

            val direct = geometry?.copyDirectTriangleF32OrNull()
            val inverseMask = consumer is ClipPlanStrategy.InverseMask
            if (!addUniform(
                    pass.id.value,
                    CONSUMER_UNIFORM,
                    if (consumer == null) {
                        color4(materialColor() ?: return false)
                    } else {
                        color8(materialColor() ?: return false, inverseMask)
                    },
                )
            ) return false
            return direct?.let {
                addGeometry(pass.id.value, CONSUMER_DIRECT, it.copyVerticesF32(), it.copyIndicesI32())
            } ?: true
        }

        private fun addPathGeometry(
            passId: String,
            purpose: String,
            geometry: PathFillGeometryF32,
            addGeometry: (String, String, FloatArray, IntArray) -> Boolean,
        ): Boolean {
            val direct = geometry.copyDirectTriangleF32OrNull()
            val fan = geometry.copyStencilEdgeFanF32OrNull()
            return when {
                direct != null -> addGeometry(passId, purpose, direct.copyVerticesF32(), direct.copyIndicesI32())
                fan != null -> addGeometry(passId, purpose, fan.copyVerticesF32(), fan.copyIndicesI32())
                else -> false
            }
        }

        private fun producerBlock(geometry: ClipGeometryF32, pass: PlanPass.ClipMaskProducer): FloatArray {
            val values = FloatArray(16)
            values[12] = 1f
            values[13] = if (pass.inverseCoverage) 1f else 0f
            values[14] = if (pass.antiAlias) 1f else 0f
            when (geometry) {
                is ClipGeometryF32.Rect -> geometry.copyRectF32().also { rect ->
                    values[0] = rect.left; values[1] = rect.top
                    values[2] = rect.right; values[3] = rect.bottom
                }
                is ClipGeometryF32.RRect -> geometry.copyRRectF32().also { rrect ->
                    values[0] = rrect.rect.left; values[1] = rrect.rect.top
                    values[2] = rrect.rect.right; values[3] = rrect.rect.bottom
                    values[4] = rrect.topLeft.x; values[5] = rrect.topLeft.y
                    values[6] = rrect.topRight.x; values[7] = rrect.topRight.y
                    values[8] = rrect.bottomRight.x; values[9] = rrect.bottomRight.y
                    values[10] = rrect.bottomLeft.x; values[11] = rrect.bottomLeft.y
                    values[12] = 2f
                }
                ClipGeometryF32.Empty -> Unit
                is ClipGeometryF32.Path -> error("Path producers use a geometry slice")
            }
            return values
        }

        private fun PathRenderDraw.w4eClipStrategyOrNull(): ClipPlanStrategy? = when (this) {
            is ClippedGeneralPathDraw -> clip
            is ClippedBinaryMaskedPathDraw -> clip
            is GeneralPathDraw,
            is BinaryMaskedPathDraw,
            -> null
        }

        private fun PlanResourceRole.isW4eMaskTarget(): Boolean = this in setOf(
            PlanResourceRole.CoverageMaskAccumulator,
            PlanResourceRole.CoverageMaskScratch,
            PlanResourceRole.CoverageMaskMultisampleScratch,
            PlanResourceRole.PathHardEdgeMask,
        )

        private fun color4(color: org.graphiks.math.color.ColorF32): FloatArray = floatArrayOf(
            color.red, color.green, color.blue, color.alpha,
        )

        private fun color8(
            color: org.graphiks.math.color.ColorF32,
            inverse: Boolean = false,
        ): FloatArray = floatArrayOf(
            color.red, color.green, color.blue, color.alpha,
            if (inverse) 1f else 0f, 0f, 0f, 0f,
        )

        private fun floatsToBytes(values: FloatArray): ByteArray = ByteBuffer
            .allocate(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { values.forEach(::putFloat) }
            .array()

        private fun alignUp(value: Long, alignment: Long): Long {
            require(value >= 0L && alignment > 0L)
            val remainder = value % alignment
            return if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
        }

        private data class PendingUniform(
            val passId: String,
            val purpose: String,
            val values: FloatArray,
        )

        private val STENCIL_PRODUCER_PHASES = setOf(
            PathRenderPhase.SingleSampleStencilProducer,
            PathRenderPhase.MultisampleStencilProducer,
            PathRenderPhase.HardEdgeMaskStencilProducer,
        )
        private val STENCIL_COVER_PHASES = setOf(
            PathRenderPhase.SingleSampleStencilColorCover,
            PathRenderPhase.MultisampleStencilColorCover,
            PathRenderPhase.HardEdgeMaskStencilCover,
        )

        public const val PRODUCER_PATH: String = "producer-path"
        public const val PRODUCER_UNIFORM: String = "producer-uniform"
        public const val HARD_MASK_PRODUCER: String = "hard-mask-producer"
        public const val STENCIL_PRODUCER: String = "stencil-producer"
        public const val STENCIL_COVER_UNIFORM: String = "stencil-cover-uniform"
        public const val INVERSE_INTERIOR_COVER: String = "inverse-interior-cover"
        public const val INVERSE_DOMAIN_ZERO_UNIFORM: String = "inverse-domain-zero-uniform"
        public const val INVERSE_DOMAIN_ZERO_SOURCE: String = "inverse-domain-zero-source"
        public const val INVERSE_DOMAIN_UNIFORM: String = "inverse-domain-uniform"
        public const val INVERSE_DOMAIN_QUAD: String = "inverse-domain-quad"
        public const val INVERSE_DOMAIN_INTERIOR: String = "inverse-domain-interior"
        public const val CONSUMER_UNIFORM: String = "consumer-uniform"
        public const val CONSUMER_DIRECT: String = "consumer-direct"

        private const val VERTEX_MINIMUM_BYTES: Long = 8L
        private const val INDEX_MINIMUM_BYTES: Long = 4L
        private const val UNIFORM_MINIMUM_BYTES: Long = 16L
    }
}

/** One checked range in the shared W4e Float32x2/Uint32 native slabs. */
public data class W4eNativeGeometrySlice(
    public val passId: String,
    public val purpose: String,
    public val firstIndex: Int,
    public val indexCount: Int,
    public val baseVertex: Int,
    public val vertexCount: Int,
    public val maxLocalIndex: Int,
) {
    init {
        require(passId.isNotBlank() && purpose.isNotBlank() && firstIndex >= 0 && indexCount > 0 &&
            baseVertex >= 0 && vertexCount > 0 && maxLocalIndex in 0 until vertexCount)
    }
}

/** One checked static binding range in the shared W4e uniform slab. */
public data class W4eNativeUniformSlice(
    public val passId: String,
    public val purpose: String,
    public val offsetBytes: Long,
    public val byteSize: Long,
) {
    init {
        require(passId.isNotBlank() && purpose.isNotBlank() && offsetBytes >= 0L && byteSize in setOf(16L, 32L, 64L))
    }
}
