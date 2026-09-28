package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.RectI32

/** Fixed native axes for the one W4e clip-mask initialization that starts an ordered clip prefix. */
public enum class W4eClipMaskInitializeFamilyV1 { FullscreenTriangle }
public enum class W4eClipMaskInitializeFormatV1 { RGBA8Unorm }
public enum class W4eClipMaskInitializeLoadV1 { Clear }
public enum class W4eClipMaskInitializeGroupZeroAbiV1 { NoBindGroup }

/** Planner-selected target facts; W4e initialization never relies on renderer defaults. */
public data class W4eClipMaskInitializeTargetV1(
    public val format: W4eClipMaskInitializeFormatV1,
    public val sampleCountI32: Int,
) {
    init { require(format == W4eClipMaskInitializeFormatV1.RGBA8Unorm && sampleCountI32 == 1) }

    public companion object {
        public val Rgba8UnormSingleSample: W4eClipMaskInitializeTargetV1 =
            W4eClipMaskInitializeTargetV1(W4eClipMaskInitializeFormatV1.RGBA8Unorm, 1)
    }
}

/**
 * Immutable final-binding recipe for one W4e [PlanPass.ClipMaskInitialize].
 * This deliberately excludes PathMaskClearPass, producers, folds, and path covers.
 */
public class W4eClipMaskInitializeRecipeV1 internal constructor(
    public val passId: PlanPassId,
    /** Final planner packet ordinal for this one-bundle native pass; not a global catalog index. */
    public val packetOrdinalI32: Int,
    /** Logical output target, retained as a planner [PlanResourceId], never a native handle or slot. */
    public val output: PlanResourceId,
    domainI32: RectI32,
    clearCoverageF32: Float,
    public val family: W4eClipMaskInitializeFamilyV1,
    public val target: W4eClipMaskInitializeTargetV1,
    public val load: W4eClipMaskInitializeLoadV1,
    public val groupZeroAbi: W4eClipMaskInitializeGroupZeroAbiV1,
) {
    private val domainSnapshotI32 = domainI32.copy()
    /** Finite, normalized F32 with signed zero canonicalized before it reaches native encoding. */
    public val clearCoverageF32: Float = canonicalCoverage(clearCoverageF32)

    public fun copyDomainI32(): RectI32 = domainSnapshotI32.copy()

    init {
        require(packetOrdinalI32 >= 0)
        require(family == W4eClipMaskInitializeFamilyV1.FullscreenTriangle)
        require(target == W4eClipMaskInitializeTargetV1.Rgba8UnormSingleSample)
        require(load == W4eClipMaskInitializeLoadV1.Clear)
        require(groupZeroAbi == W4eClipMaskInitializeGroupZeroAbiV1.NoBindGroup)
        require(!domainSnapshotI32.isEmpty)
    }

    override fun equals(other: Any?): Boolean = other is W4eClipMaskInitializeRecipeV1 &&
        passId == other.passId && packetOrdinalI32 == other.packetOrdinalI32 && output == other.output && domainSnapshotI32 == other.domainSnapshotI32 &&
        clearCoverageF32.toBits() == other.clearCoverageF32.toBits() && family == other.family &&
        target == other.target && load == other.load && groupZeroAbi == other.groupZeroAbi

    override fun hashCode(): Int = listOf(passId, packetOrdinalI32, output, domainSnapshotI32, clearCoverageF32.toBits(), family,
        target, load, groupZeroAbi).fold(1) { hash, value -> 31 * hash + value.hashCode() }

    private companion object {
        fun canonicalCoverage(value: Float): Float {
            require(value.isFinite() && value in 0f..1f) {
                "Frozen W4e clip-mask initialize coverage must be finite and normalized."
            }
            return if (value == 0f) 0f else value
        }
    }
}

/** Freezes exactly the ClipMaskInitialize passes admitted by the final W4e bindings. */
public fun freezeW4eClipMaskInitializeRecipesV1(
    bindings: List<PlanW4eGeometryBindingV1>,
): Map<PlanPassId, W4eClipMaskInitializeRecipeV1> {
    val recipes = linkedMapOf<PlanPassId, W4eClipMaskInitializeRecipeV1>()
    bindings.forEach { binding ->
        binding.nativePasses().forEach { pass ->
            val initialize = pass as? PlanPass.ClipMaskInitialize ?: return@forEach
            require(binding.nativePass(initialize.id) === initialize)
            val recipe = W4eClipMaskInitializeRecipeV1(
                passId = initialize.id,
                packetOrdinalI32 = initialize.ordinal,
                output = initialize.output,
                domainI32 = initialize.copyDomainI32(),
                clearCoverageF32 = initialize.clearCoverageF32,
                family = W4eClipMaskInitializeFamilyV1.FullscreenTriangle,
                target = W4eClipMaskInitializeTargetV1.Rgba8UnormSingleSample,
                load = W4eClipMaskInitializeLoadV1.Clear,
                groupZeroAbi = W4eClipMaskInitializeGroupZeroAbiV1.NoBindGroup,
            )
            require(recipes.put(initialize.id, recipe) == null)
        }
    }
    return java.util.Collections.unmodifiableMap(LinkedHashMap(recipes))
}
