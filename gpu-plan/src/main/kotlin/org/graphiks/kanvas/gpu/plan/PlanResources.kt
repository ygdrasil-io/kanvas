package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.SizeI32

public enum class PlanResourceKind { Texture2D, Buffer }
public enum class PlanResourceRole { LogicalTarget, ReadbackStaging, VertexData, IndexData, UniformData }
public enum class PlanResourceUsage { RenderAttachment, CopySource, CopyDestination, MapRead, Vertex, Index, Uniform }
public enum class PlanResourceLifetime { FrameLocal }

public class PlanResource private constructor(
    public val id: PlanResourceId,
    public val role: PlanResourceRole,
    public val ordinal: Int,
    public val kind: PlanResourceKind,
    public val format: PlanLogicalColorFormat?,
    extent: SizeI32?,
    public val byteSize: Long,
    usages: Set<PlanResourceUsage>,
    public val lifetime: PlanResourceLifetime,
    public val firstPassIndex: Int,
    public val lastPassIndexExclusive: Int,
) {
    private val storedExtent = extent?.copy()
    private val storedUsages = immutableSet(usages)

    public fun copyExtent(): SizeI32? = storedExtent?.copy()
    public fun usages(): Set<PlanResourceUsage> = storedUsages

    public companion object {
        public fun of(
            role: PlanResourceRole,
            ordinal: Int,
            kind: PlanResourceKind,
            format: PlanLogicalColorFormat?,
            extent: SizeI32?,
            byteSize: Long,
            usages: Set<PlanResourceUsage>,
            lifetime: PlanResourceLifetime,
            firstPassIndex: Int,
            lastPassIndexExclusive: Int,
        ): PlanResource {
            require(ordinal >= 0) { "Resource ordinal must be non-negative" }
            require(byteSize > 0) { "Resource byte size must be positive" }
            require(usages.isNotEmpty()) { "Resource usages must not be empty" }
            require(firstPassIndex >= 0 && lastPassIndexExclusive > firstPassIndex) { "Resource lifetime must be non-empty" }
            when (kind) {
                PlanResourceKind.Texture2D -> {
                    require(format != null && extent != null && !extent.isEmpty()) {
                        "Textures require a format and non-empty extent"
                    }
                    require(byteSize >= minimumTextureByteSize(extent)) {
                        "Texture byte size is smaller than its extent"
                    }
                }
                PlanResourceKind.Buffer -> require(format == null && extent == null) {
                    "Buffers cannot declare a format or extent"
                }
            }
            return PlanResource(planResourceId(role, ordinal), role, ordinal, kind, format, extent, byteSize,
                usages, lifetime, firstPassIndex, lastPassIndexExclusive)
        }

        private fun minimumTextureByteSize(extent: SizeI32): Long = try {
            Math.multiplyExact(Math.multiplyExact(extent.width.toLong(), extent.height.toLong()), LOGICAL_PIXEL_BYTES)
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("Texture byte size calculation overflows", error)
        }

        private const val LOGICAL_PIXEL_BYTES: Long = 4L
    }
}
