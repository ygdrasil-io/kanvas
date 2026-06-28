package org.graphiks.kanvas.gpu.renderer.images

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

/** Mipmap downsampling filter selection. */
enum class MipmapFilter {
    Box,
    Tent,
    Kaiser,
}

/** Mipmap generation execution path. */
enum class MipmapGenerationPath {
    Blit,
    Compute,
}

/** Opaque texture identifier for blit-based mipmap operations. */
@JvmInline
value class TextureId(val value: Int)

/** Single blit operation from a source mip level to a destination mip level. */
data class TextureBlitOp(
    val srcLevel: Int,
    val dstLevel: Int,
    val dstWidth: Int,
    val dstHeight: Int,
)

/** Compute shader workgroup dispatch sizing. */
data class WorkgroupSize(
    val x: Int,
    val y: Int,
    val z: Int = 1,
)

/** Deterministic mipmap cache lookup key. */
@JvmInline
value class MipmapCacheKey(val value: String)

/** Opaque reference to a cached mipmap generation artifact. */
@JvmInline
value class MipmapArtifactRef(val value: String)

/** Mipmap generation plan with level count, filter, and execution path. */
data class GPUImageMipmapGenerationPlan(
    val levels: Int,
    val filter: MipmapFilter,
    val path: MipmapGenerationPath,
)

/** WGPU blit-based mipmap level generation plan. */
data class GPUImageMipmapBlitPlan(
    val sourceTexture: TextureId,
    val levels: List<TextureBlitOp>,
)

/** WGSL compute shader path for filter-controlled mipmap downsampling. */
data class GPUImageMipmapComputePlan(
    val wgslModule: WgslModuleId,
    val dispatchSizes: List<WorkgroupSize>,
)

/** Cached mipmap generation artifact reference. */
data class GPUImageMipmapCachePlan(
    val key: MipmapCacheKey,
    val artifact: MipmapArtifactRef,
)

/** Mipmap generation result: generated or refused with a stable diagnostic code. */
sealed interface GPUImageMipmapGenerationResult {

    /** Mipmap generation succeeded with blit and optional compute plans. */
    data class Generated(
        val plan: GPUImageMipmapGenerationPlan,
        val blitPlan: GPUImageMipmapBlitPlan,
        val computePlan: GPUImageMipmapComputePlan?,
    ) : GPUImageMipmapGenerationResult

    /** Mipmap generation was refused with a stable diagnostic code. */
    data class Refused(
        val code: String,
        val message: String,
    ) : GPUImageMipmapGenerationResult

    /** Converts this result into a routing RefuseDiagnostic. */
    fun toRefuseDiagnostic(stage: String): RefuseDiagnostic =
        when (this) {
            is Refused -> RefuseDiagnostic(
                code = code,
                message = message,
                stage = stage,
                terminal = true,
            )
            is Generated -> RefuseDiagnostic(
                code = "unsupported.image.mipmap_unexpected_refusal",
                message = "Unexpected refusal for generated mipmap plan",
                stage = stage,
                terminal = false,
            )
        }

    /** Derives a deterministic cache plan keyed by level count, filter, and path. */
    fun cachePlan(): GPUImageMipmapCachePlan =
        when (this) {
            is Generated -> {
                val keyValue = "mipmap:${plan.levels}L:${plan.filter}:${plan.path}".lowercase()
                val artifactValue = "mipmap-artifact:$keyValue"
                GPUImageMipmapCachePlan(
                    key = MipmapCacheKey(keyValue),
                    artifact = MipmapArtifactRef(artifactValue),
                )
            }
            is Refused -> GPUImageMipmapCachePlan(
                key = MipmapCacheKey("mipmap:refused:${code.lowercase()}"),
                artifact = MipmapArtifactRef("mipmap-artifact:refused"),
            )
        }

    /** Emits stable evidence lines for reports and test assertions. */
    fun dumpLines(): List<String> =
        when (this) {
            is Generated -> {
                val lines = mutableListOf<String>()
                lines.add(
                    "mipmap:generation levels=${plan.levels} filter=${plan.filter} " +
                        "path=${plan.path} sourceTexture=${blitPlan.sourceTexture.value}",
                )
                lines.add(
                    "mipmap:plan levels=${plan.levels} filter=${plan.filter} " +
                        "path=${plan.path} consumer=texture-sampler",
                )
                lines.add(
                    "mipmap:blit sourceTexture=${blitPlan.sourceTexture.value} " +
                        "levelCount=${blitPlan.levels.size}",
                )
                blitPlan.levels.forEach { op ->
                    lines.add(
                        "mipmap:blit-level src=${op.srcLevel} dst=${op.dstLevel} " +
                            "size=${op.dstWidth}x${op.dstHeight}",
                    )
                }
                if (computePlan != null) {
                    lines.add(
                        "mipmap:compute module=${computePlan.wgslModule.value} " +
                            "dispatchCount=${computePlan.dispatchSizes.size}",
                    )
                    computePlan.dispatchSizes.forEachIndexed { index, size ->
                        lines.add(
                            "mipmap:compute-dispatch level=${index + 1} " +
                                "workgroup=${size.x}x${size.y}x${size.z}",
                        )
                    }
                }
                val cache = cachePlan()
                lines.add(
                    "mipmap:cache key=${cache.key.value} " +
                        "artifact=${cache.artifact.value}",
                )
                lines.add(
                    "nonclaim:no-product-activation no-adapter-backed-execution " +
                        "no-gpu-resource-handle no-cpu-compat-fallback",
                )
                lines
            }
            is Refused -> listOf(
                "mipmap:refused code=$code message=$message",
                "nonclaim:no-product-activation no-adapter-backed-execution " +
                    "no-gpu-resource-handle no-cpu-compat-fallback",
            )
        }
}

/** Plans GPU mipmap generation for uploaded image textures. */
class GPUImageMipmapPlanner(
    val maxMipLevels: Int = 14,
) {

    /** Plans mipmap generation, selecting path and enforcing budget constraints. */
    fun plan(
        width: Int,
        height: Int,
        filter: MipmapFilter,
        computeAvailable: Boolean,
    ): GPUImageMipmapGenerationResult {
        val clampedWidth = width.coerceAtLeast(1)
        val clampedHeight = height.coerceAtLeast(1)
        val maxDimension = maxOf(clampedWidth, clampedHeight)

        val levels = computeMipLevelCount(maxDimension)

        if (levels > maxMipLevels) {
            return GPUImageMipmapGenerationResult.Refused(
                code = "unsupported.image.mipmap_budget_exceeded",
                message = "Mip level count $levels (from ${clampedWidth}x$clampedHeight) exceeds adapter limit $maxMipLevels.",
            )
        }

        val path = if (computeAvailable) MipmapGenerationPath.Compute else MipmapGenerationPath.Blit

        val generationPlan = GPUImageMipmapGenerationPlan(
            levels = levels,
            filter = filter,
            path = path,
        )

        val blitLevels = buildBlitLevels(clampedWidth, clampedHeight, levels)
        val blitPlan = GPUImageMipmapBlitPlan(
            sourceTexture = TextureId(0),
            levels = blitLevels,
        )

        val computePlan = if (computeAvailable) {
            val dispatchSizes = buildDispatchSizes(clampedWidth, clampedHeight, levels)
            GPUImageMipmapComputePlan(
                wgslModule = WgslModuleId("mipmap-generation-v1"),
                dispatchSizes = dispatchSizes,
            )
        } else {
            null
        }

        return GPUImageMipmapGenerationResult.Generated(
            plan = generationPlan,
            blitPlan = blitPlan,
            computePlan = computePlan,
        )
    }

    /** Computes mip level count from the maximum dimension using floor(log2) + 1. */
    private fun computeMipLevelCount(maxDimension: Int): Int =
        32 - maxDimension.countLeadingZeroBits()

    /** Builds blit operations for each mip level from the base dimensions. */
    private fun buildBlitLevels(
        baseWidth: Int,
        baseHeight: Int,
        levels: Int,
    ): List<TextureBlitOp> =
        (1 until levels).map { level ->
            val dstWidth = (baseWidth shr level).coerceAtLeast(1)
            val dstHeight = (baseHeight shr level).coerceAtLeast(1)
            TextureBlitOp(
                srcLevel = level - 1,
                dstLevel = level,
                dstWidth = dstWidth,
                dstHeight = dstHeight,
            )
        }

    /** Builds compute workgroup dispatch sizes for each mip level. */
    private fun buildDispatchSizes(
        baseWidth: Int,
        baseHeight: Int,
        levels: Int,
    ): List<WorkgroupSize> =
        (1 until levels).map { level ->
            val levelWidth = (baseWidth shr level).coerceAtLeast(1)
            val levelHeight = (baseHeight shr level).coerceAtLeast(1)
            WorkgroupSize(
                x = (levelWidth + 15) / 16,
                y = (levelHeight + 15) / 16,
            )
        }
}
