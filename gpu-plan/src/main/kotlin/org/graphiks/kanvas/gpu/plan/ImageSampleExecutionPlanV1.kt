package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.ImageAlphaType
import org.graphiks.kanvas.render.ir.ImagePremultiplicationV1
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.toMatrix3x3F64
import org.graphiks.math.matrix.invertToMatrix3x3F32OrNull
import org.graphiks.math.matrix.composeInOrderF64
import org.graphiks.math.matrix.invertFiniteOrNull
import org.graphiks.math.matrix.toFiniteMatrix3x3F32OrNull
import org.graphiks.math.matrix.isFinite

public enum class ImageChannelOrderV1 { RGBA, BGRA, ALPHA }
public enum class ImageTransferPlanV1 { SRGB, LINEAR, NONE }
public enum class ImageGamutPlanV1 { SRGB, DISPLAY_P3, NONE }
/** Structural filter selection; a texture upload never carries this semantic. */
public sealed interface ImageSamplingPlanV1 {
    public val topologyId: String
    public val bindingIdentity: String
    public data object Nearest : ImageSamplingPlanV1 { override val topologyId: String = "nearest-floor-v1"; override val bindingIdentity: String = topologyId }
    public data object Linear : ImageSamplingPlanV1 { override val topologyId: String = "linear-four-tap-v1"; override val bindingIdentity: String = topologyId }
    public data class Cubic(public val bBitsI32: Int, public val cBitsI32: Int) : ImageSamplingPlanV1 {
        override val topologyId: String = "cubic-sixteen-tap-v1"
        override val bindingIdentity: String = "$topologyId:b=$bBitsI32:c=$cBitsI32"
        public val bF32: Float get() = Float.fromBits(bBitsI32)
        public val cF32: Float get() = Float.fromBits(cBitsI32)
    }
}

public enum class ImageTileAxisModePlanV1 { CLAMP, REPEAT, MIRROR, DECAL }

/** Independent image-axis address graph, sealed into program and execution identities. */
public class ImageTileModePlanV1(public val x: ImageTileAxisModePlanV1, public val y: ImageTileAxisModePlanV1) {
    public val topologyId: String = "w5e-image-tile-v1:x=${x.name}:y=${y.name}"
    override fun equals(other: Any?): Boolean = other is ImageTileModePlanV1 && x == other.x && y == other.y
    override fun hashCode(): Int = 31 * x.hashCode() + y.hashCode()
    override fun toString(): String = topologyId
    public companion object { public val ClampClamp: ImageTileModePlanV1 = ImageTileModePlanV1(ImageTileAxisModePlanV1.CLAMP, ImageTileAxisModePlanV1.CLAMP) }
}

public data class ImageColorAlphaPlanV1(public val channelOrder: ImageChannelOrderV1,
    public val alphaType: ImageAlphaType, public val transfer: ImageTransferPlanV1, public val gamut: ImageGamutPlanV1,
    public val premultiplication: ImagePremultiplicationV1 = ImagePremultiplicationV1.SOURCE_SPACE) {
    /** Colour decode branches on unit alpha before executing any division. */
    public val unpremultiplyOperation: ImageNumericOperationGraphV1.TexelOperation?
        get() = if (channelOrder != ImageChannelOrderV1.ALPHA && alphaType == ImageAlphaType.PREMUL &&
            premultiplication == ImagePremultiplicationV1.SOURCE_SPACE)
            ImageNumericOperationGraphV1.TexelOperation.UNIT_ALPHA_GUARDED_UNPREMULTIPLY_SOURCE else null
}

/** Immutable projection and cell mapping, preserving signed source/destination extents. */
public class ImageCoordinatePlanV1 private constructor(inverseF32: Matrix3x3F32, sourceF32: RectF32, destinationF32: RectF32) {
    private val inverse = inverseF32.copy()
    private val source = sourceF32.copy()
    private val destination = destinationF32.copy()
    public fun copyInverseF32(): Matrix3x3F32 = inverse.copy()
    public fun copySourceF32(): RectF32 = source.copy()
    public fun copyDestinationF32(): RectF32 = destination.copy()
    public val canonicalIdentity: String = "image-coordinate-v1:" + uniformValuesF32().joinToString(",") { it.toRawBits().toString() }
    public fun uniformValuesF32(): List<Float> = listOf(inverse.sx, inverse.kx, inverse.tx, 0f,
        inverse.ky, inverse.sy, inverse.ty, 0f, inverse.persp0, inverse.persp1, inverse.persp2, 0f,
        source.left, source.top, source.right - source.left, source.bottom - source.top,
        destination.left, destination.top, destination.right - destination.left, destination.bottom - destination.top)
    internal companion object {
        fun sealShader(ctmF32: Matrix3x3F32, localMatricesF32: List<Matrix3x3F32>): ImageCoordinatePlanV1 {
            require(localMatricesF32.all { it.toMatrix3x3F64().isFinite() }) {
                "unsupported.material.image.local-matrix-non-finite"
            }
            val combinedF64 = composeInOrderF64(listOf(ctmF32) + localMatricesF32)
            val inverseF64 = combinedF64.invertFiniteOrNull()
                ?: throw IllegalArgumentException("unsupported.material.image.local-matrix-singular")
            val inverseF32 = inverseF64.toFiniteMatrix3x3F32OrNull()
                ?: throw IllegalArgumentException("unsupported.material.image.local-matrix-unrepresentable")
            // Shader coordinates are already image coordinates; the cell projection is identity.
            return ImageCoordinatePlanV1(inverseF32, RectF32.ofLTRB(0f, 0f, 1f, 1f), RectF32.ofLTRB(0f, 0f, 1f, 1f))
        }

        fun seal(ctmF32: Matrix3x3F32, sourceF32: RectF32, destinationF32: RectF32): ImageCoordinatePlanV1 {
            val inverse = ctmF32.toMatrix3x3F64().invertToMatrix3x3F32OrNull()
                ?: throw IllegalArgumentException(W5eImagePlanDiagnostics.NumericDomainUnbounded)
            return sealInverse(inverse, sourceF32, destinationF32)
        }
        fun sealInverse(inverse: Matrix3x3F32, sourceF32: RectF32, destinationF32: RectF32): ImageCoordinatePlanV1 {
            val result = ImageCoordinatePlanV1(inverse, sourceF32, destinationF32)
            require(result.uniformValuesF32().all(Float::isFinite) &&
                listOf(sourceF32.right - sourceF32.left, sourceF32.bottom - sourceF32.top,
                    destinationF32.right - destinationF32.left, destinationF32.bottom - destinationF32.top).all { it != 0f }) {
                W5eImagePlanDiagnostics.NumericDomainUnbounded
            }
            return result
        }
    }
}

public sealed interface ImageMaterialProgramV3 : MaterialProgramPlan {
    override val versionI32: Int get() = 3
    public val selectsCells: Boolean
    public val latticeCellKinds: String?
    public val atlasBlendMode: org.graphiks.kanvas.render.ir.BlendMode?
    public data class ColorV3(public val channelOrder: ImageChannelOrderV1, public val alphaType: ImageAlphaType,
        public val transfer: ImageTransferPlanV1, public val gamut: ImageGamutPlanV1,
        public val sampling: ImageSamplingPlanV1, public val tileModes: ImageTileModePlanV1,
        override val selectsCells: Boolean = false, override val latticeCellKinds: String? = null,
        override val atlasBlendMode: org.graphiks.kanvas.render.ir.BlendMode? = null,
        public val premultiplication: ImagePremultiplicationV1 = ImagePremultiplicationV1.SOURCE_SPACE) : ImageMaterialProgramV3 {
        override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("w5e-image-color-v3:$channelOrder:$alphaType:$transfer:$gamut:${sampling.topologyId}:${tileModes.topologyId}${atlasBlendMode?.let { ":atlas-source-blend-v1:$it" }.orEmpty()}" +
            (if (premultiplication == ImagePremultiplicationV1.TRANSFER_ENCODED_LINEAR_PREMUL) ":attachment-linear-premul-v1"
                else if (alphaType == ImageAlphaType.PREMUL) ":unit-alpha-guard-v1" else "") +
            if (latticeCellKinds != null) ":lattice-cell-selector-v1:$latticeCellKinds" else if (selectsCells) ":nine-local-cell-selector-v2" else "")
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.imageColor()
    }
    public class MaskV3(public val child: MaterialProgramPlan, public val alphaType: ImageAlphaType,
        public val sampling: ImageSamplingPlanV1, public val tileModes: ImageTileModePlanV1,
        override val selectsCells: Boolean = false, override val latticeCellKinds: String? = null,
        override val atlasBlendMode: org.graphiks.kanvas.render.ir.BlendMode? = null) : ImageMaterialProgramV3 {
        override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("w5e-image-mask-v3:$alphaType:${sampling.topologyId}:${tileModes.topologyId}(${child.structuralId.value})${atlasBlendMode?.let { ":atlas-source-blend-v1:$it" }.orEmpty()}" +
            if (latticeCellKinds != null) ":lattice-cell-selector-v1:$latticeCellKinds" else if (selectsCells) ":nine-local-cell-selector-v2" else "")
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.imageMask()
    }
}

public class ImageSampleV3 private constructor(public val execution: ImageSampleExecutionPlanV1) : MaterialBindingPlan {
    override val versionI32: Int = 3
    public companion object { public fun of(execution: ImageSampleExecutionPlanV1): ImageSampleV3 = ImageSampleV3(execution) }
}

/** Complete source authority: only immutable values and defensive copies cross the backend seam. */
public class ImageSampleExecutionPlanV1 internal constructor(
    public val upload: ImageUploadPlanV1,
    public val coordinates: ImageCoordinatePlanV1,
    public val colorAlpha: ImageColorAlphaPlanV1,
    public val numericAuthority: ImageNumericAuthorityV1,
    public val paintAlphaF32: Float,
    public val childSourceIdentity: String?,
    public val totalPessimisticBudgetBytesI64: Long,
    public val sampling: ImageSamplingPlanV1,
    public val tileModes: ImageTileModePlanV1,
    public val atlasBlend: ImageAtlasBlendNumericAuthorityV1? = null,
) {
    public val tileX: ImageTileAxisModePlanV1 get() = tileModes.x
    public val tileY: ImageTileAxisModePlanV1 get() = tileModes.y
    public val cacheRequest: PlanCacheResourceRequest get() = upload.cacheRequest
    // Cells stay inside a single logical ImageDraw; their representation is already math-owned.
    public val cellSelection: ImageCellSelectionPlanV1? get() = numericAuthority.cellSelection
    public fun copySourceCellsF32(): List<RectF32> = cellSelection?.samples?.map { it.cell.copySourceF32() } ?: listOf(coordinates.copySourceF32())
    public fun copyDestinationCellsF32(): List<RectF32> = cellSelection?.cells?.map { it.copyDestinationF32() } ?: listOf(coordinates.copyDestinationF32())
    public val canonicalIdentity: String = "image-execution-v1:${upload.contentIdentity}:${coordinates.canonicalIdentity}:$colorAlpha:" +
        "${sampling.bindingIdentity}:${tileModes.topologyId}:${paintAlphaF32.toRawBits()}:" +
        childSourceIdentity.orEmpty() +
        ":${numericAuthority.canonicalIdentity}:budget=$totalPessimisticBudgetBytesI64" + atlasBlend?.let { ":${it.canonicalIdentity}" }.orEmpty()
    init {
        require(paintAlphaF32.isFinite() && paintAlphaF32 in 0f..1f && totalPessimisticBudgetBytesI64 >= upload.byteCountI64 &&
            upload.widthI32 > 0 && upload.heightI32 > 0)
    }
}
