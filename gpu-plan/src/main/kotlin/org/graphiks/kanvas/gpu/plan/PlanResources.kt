package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.SizeI32

public enum class PlanResourceKind { Texture2D, Buffer }
public enum class PlanResourceRole {
    LogicalTarget,
    MultisampleColorTarget,
    PathHardEdgeMask,
    PathHardEdgeDepthStencil,
    CoverageMaskAccumulator,
    CoverageMaskScratch,
    CoverageMaskMultisampleScratch,
    CoverageMaskDepthStencil,
    ReadbackStaging,
    VertexData,
    IndexData,
    UniformData,
    GradientStopData,
    DepthStencil,
    DestinationSnapshot,
}
public enum class PlanResourceUsage {
    RenderAttachment,
    Sampled,
    CopySource,
    CopyDestination,
    MapRead,
    Vertex,
    Index,
    Uniform,
    DepthStencilAttachment,
    StorageRead,
}
public enum class PlanResourceLifetime { FrameLocal }

/**
 * Primitive lifetime accounting used to preflight a frame before issuing any [PlanResource].
 * The span deliberately carries no resource identity or GPU object.
 */
internal data class FrameResourceSpan(
    val byteSize: Long,
    val firstPassIndex: Int,
    val lastPassIndexExclusive: Int,
) {
    init {
        require(byteSize > 0L)
        require(firstPassIndex >= 0 && lastPassIndexExclusive > firstPassIndex)
    }
}

internal fun peakFrameLocalBytesI64(spans: List<FrameResourceSpan>, passCount: Int): Long {
    require(passCount > 0)
    return (0 until passCount).maxOf { passIndex ->
        spans.asSequence()
            .filter { it.firstPassIndex <= passIndex && passIndex < it.lastPassIndexExclusive }
            .fold(0L) { total, span -> Math.addExact(total, span.byteSize) }
    }
}

public sealed interface PlanTextureFormat {
    public data class Color(public val value: PlanLogicalColorFormat) : PlanTextureFormat
    public data class DepthStencil(public val value: PlanDepthStencilFormat) : PlanTextureFormat
    public object CoverageMask : PlanTextureFormat {
        public val value: PlanCoverageMaskFormat = PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR
        public operator fun invoke(value: PlanCoverageMaskFormat): CoverageMask {
            require(value == PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR) { "Unsupported coverage-mask format" }
            return this
        }
    }
}

/** Linear, un-premultiplied coverage storage used only by clip-mask passes. */
public enum class PlanCoverageMaskFormat { RGBA8_UNORM_LINEAR }

public class PlanResource private constructor(
    public val id: PlanResourceId,
    public val role: PlanResourceRole,
    public val ordinal: Int,
    public val kind: PlanResourceKind,
    public val format: PlanTextureFormat?,
    extent: SizeI32?,
    public val byteSize: Long,
    usages: Set<PlanResourceUsage>,
    public val lifetime: PlanResourceLifetime,
    public val firstPassIndex: Int,
    public val lastPassIndexExclusive: Int,
    public val sampleCountI32: Int,
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
            format: PlanTextureFormat?,
            extent: SizeI32?,
            byteSize: Long,
            usages: Set<PlanResourceUsage>,
            lifetime: PlanResourceLifetime,
            firstPassIndex: Int,
            lastPassIndexExclusive: Int,
            sampleCountI32: Int = 1,
        ): PlanResource {
            require(ordinal >= 0) { "Resource ordinal must be non-negative" }
            require(byteSize > 0) { "Resource byte size must be positive" }
            require(usages.isNotEmpty()) { "Resource usages must not be empty" }
            require(firstPassIndex >= 0 && lastPassIndexExclusive > firstPassIndex) { "Resource lifetime must be non-empty" }
            when (kind) {
                PlanResourceKind.Texture2D -> {
                    require(sampleCountI32 == 1 || sampleCountI32 == 4) {
                        "Textures must be single-sample or four-sample"
                    }
                    require(format != null && extent != null && !extent.isEmpty()) {
                        "Textures require a format and non-empty extent"
                    }
                    when (format) {
                        is PlanTextureFormat.Color -> require(role != PlanResourceRole.DepthStencil) {
                            "Depth-stencil resources require a depth-stencil format"
                        }
                        is PlanTextureFormat.DepthStencil -> {
                            require(
                                role == PlanResourceRole.DepthStencil ||
                                    role == PlanResourceRole.PathHardEdgeDepthStencil ||
                                    role == PlanResourceRole.CoverageMaskDepthStencil,
                            ) {
                                "Depth-stencil textures require the depth-stencil role"
                            }
                            require(PlanResourceUsage.DepthStencilAttachment in usages) {
                                "Depth-stencil textures require depth-stencil attachment usage"
                            }
                        }
                        PlanTextureFormat.CoverageMask -> {
                            require(PlanTextureFormat.CoverageMask.value == PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR) {
                                "Coverage masks require linear RGBA8 storage"
                            }
                            require(role in setOf(
                                PlanResourceRole.PathHardEdgeMask,
                                PlanResourceRole.CoverageMaskAccumulator,
                                PlanResourceRole.CoverageMaskScratch,
                                PlanResourceRole.CoverageMaskMultisampleScratch,
                            )) { "Coverage masks require a typed mask role" }
                            val expectedSamples = if (role == PlanResourceRole.CoverageMaskMultisampleScratch) 4 else 1
                            require(sampleCountI32 == expectedSamples) { "Coverage-mask sample count does not match its role" }
                            require(PlanResourceUsage.RenderAttachment in usages) {
                                "Coverage masks require render-attachment usage"
                            }
                            if (sampleCountI32 == 1) require(PlanResourceUsage.Sampled in usages) {
                                "Single-sample coverage masks require sampled usage"
                            }
                        }
                    }
                    require(
                        byteSize == checkedTextureBytesI64(
                            textureBytesPerPixelI32(format),
                            extent.width,
                            extent.height,
                            sampleCountI32,
                        ),
                    ) {
                        "Texture byte size must equal its checked logical size"
                    }
                }
                PlanResourceKind.Buffer -> {
                    require(format == null && extent == null) { "Buffers cannot declare a format or extent" }
                    require(sampleCountI32 == 1) { "Buffers must be single-sample" }
                }
            }
            val isD24S8DepthStencilTexture = kind == PlanResourceKind.Texture2D &&
                (role == PlanResourceRole.DepthStencil || role == PlanResourceRole.PathHardEdgeDepthStencil ||
                    role == PlanResourceRole.CoverageMaskDepthStencil) &&
                format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8)
            require(PlanResourceUsage.DepthStencilAttachment !in usages || isD24S8DepthStencilTexture) {
                "Depth-stencil attachment usage requires a D24S8 depth-stencil texture"
            }
            return PlanResource(planResourceId(role, ordinal), role, ordinal, kind, format, extent, byteSize,
                usages, lifetime, firstPassIndex, lastPassIndexExclusive, sampleCountI32)
        }

        private fun textureBytesPerPixelI32(format: PlanTextureFormat): Int = when (format) {
            is PlanTextureFormat.Color,
            is PlanTextureFormat.DepthStencil,
            PlanTextureFormat.CoverageMask,
            -> 4
        }
    }
}

/** Checked logical allocation size for a texture, including its sample planes. */
public fun checkedTextureBytesI64(
    bytesPerPixelI32: Int,
    widthI32: Int,
    heightI32: Int,
    sampleCountI32: Int,
): Long {
    require(bytesPerPixelI32 > 0) { "Texture bytes per pixel must be positive" }
    require(widthI32 > 0 && heightI32 > 0) { "Texture extent must be positive" }
    require(sampleCountI32 == 1 || sampleCountI32 == 4) {
        "Texture sample count must be single-sample or four-sample"
    }
    return checkedTextureMultiplyI64(
        checkedTextureMultiplyI64(bytesPerPixelI32.toLong(), widthI32.toLong()),
        checkedTextureMultiplyI64(heightI32.toLong(), sampleCountI32.toLong()),
    )
}

private fun checkedTextureMultiplyI64(first: Long, second: Long): Long {
    require(first >= 0 && second >= 0) { "Texture allocation factors must be non-negative" }
    if (first != 0L && second > Long.MAX_VALUE / first) {
        throw IllegalArgumentException("Texture byte size calculation overflows")
    }
    return first * second
}
