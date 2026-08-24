package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.font.FontSourceID
import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.glyph.GlyphStrikeKey
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32

/** Immutable font-source snapshot retained by a prepared text draw. */
@ConsistentCopyVisibility
data class GPUPreparedFontFaceSnapshot private constructor(
    val sourceId: FontSourceID,
    val typefaceId: TypefaceID,
    val faceIndex: Int,
    val bytes: List<Int>,
    val provenance: String,
) {
    init {
        require(faceIndex >= 0) { "Prepared font face index must be non-negative" }
        require(bytes.all { it in 0..255 }) { "Prepared font bytes must be unsigned byte values" }
        require(provenance.isNotBlank()) { "Prepared font provenance must not be blank" }
    }

    companion object {
        /** Creates a face snapshot without retaining the caller-owned byte collection. */
        fun create(
            sourceId: FontSourceID,
            typefaceId: TypefaceID,
            faceIndex: Int,
            bytes: Collection<Int>,
            provenance: String,
        ): GPUPreparedFontFaceSnapshot = GPUPreparedFontFaceSnapshot(
            sourceId = sourceId,
            typefaceId = typefaceId,
            faceIndex = faceIndex,
            bytes = immutablePreparedTextList(bytes),
            provenance = provenance,
        )
    }
}

/** Renderer-relevant source representation proven for one shaped glyph. */
enum class GPUPreparedTextSourceRepresentation {
    OUTLINE,
    COLRV0,
    CBDT_CBLC,
    SBIX,
    SVG,
    COLRV1,
    MISSING,
}

/** Prepared representation admitted for later mask/atlas inventory work. */
enum class GPUPreparedTextRepresentation {
    A8_MASK,
    COLRV0,
}

/** Per-draw representation selection in exact flattened glyph order. */
@ConsistentCopyVisibility
data class GPUPreparedTextRepresentationPolicy private constructor(
    val representations: List<GPUPreparedTextRepresentation>,
) {
    companion object {
        @JvmSynthetic
        internal fun create(
            representations: Collection<GPUPreparedTextRepresentation>,
        ): GPUPreparedTextRepresentationPolicy = GPUPreparedTextRepresentationPolicy(
            representations = immutablePreparedTextList(representations),
        )
    }
}

/**
 * One exact positioned glyph ready for later artifact generation.
 *
 * The affine transform `A = draw.transform` remains exact, including its
 * translation. Let `L = linear2x2(draw.transform)` be only A's linear part.
 * This glyph's strike stores the raster-density matrix
 * `D = diag(hypot(L.col0), hypot(L.col1))` in `sx/sy`, the device
 * anchor `A * (origin + glyphPosition)`, and the exact raw-bit identity of L
 * in `transformBucket`. Its raster phase is
 * `phase = (strikeKey.subpixelX, strikeKey.subpixelY)`.
 *
 * Task 4 proves only this preparation/key contract; it does not claim rendered
 * placement. For an original glyph-local coordinate `p`, let the unpacked
 * mask-local coordinate before atlas packing be `q = D * p + phase`. A later
 * artifact executor must use the translation-free residual
 * `R = L * inverse(D)` and place it with
 * `device(q) = anchorDevice + R * (q - phase)`. Atlas packing offsets are
 * separate from q. The executor must never apply A directly to bounds already
 * rasterized with D; doing so would double-apply scale or translation and can
 * shift rotation/skew subpixel phase.
 */
@ConsistentCopyVisibility
data class GPUPreparedGlyphInput private constructor(
    val glyphId: Int,
    val positionX: Float,
    val positionY: Float,
    val fontSize: Float,
    val strikeKey: GlyphStrikeKey,
) {
    companion object {
        @JvmSynthetic
        internal fun create(
            glyphId: Int,
            positionX: Float,
            positionY: Float,
            fontSize: Float,
            strikeKey: GlyphStrikeKey,
        ): GPUPreparedGlyphInput = GPUPreparedGlyphInput(
            glyphId = glyphId,
            positionX = positionX,
            positionY = positionY,
            fontSize = fontSize,
            strikeKey = strikeKey,
        )
    }
}

/** Pure, handle-free, transactionally prepared text draw. */
class GPUPreparedTextDraw private constructor(
    val operationIndex: Int,
    val face: GPUPreparedFontFaceSnapshot,
    val glyphs: List<GPUPreparedGlyphInput>,
    val originX: Float,
    val originY: Float,
    val transform: Matrix3x3F32,
    val clipContentKey: String,
    clip: ClipStack,
    paint: Paint,
    val material: GPUPreparedMaterialProgram,
    val blendPlan: GPUBlendPlan,
    val targetColorFormat: String,
    val capabilitySnapshotHash: String,
    val representationPolicy: GPUPreparedTextRepresentationPolicy,
) {
    init {
        require(targetColorFormat.isNotBlank()) { "Prepared text target format must not be blank" }
        require(clipContentKey.isNotBlank()) { "Prepared text clipContentKey must not be blank" }
        require(capabilitySnapshotHash.isNotBlank()) {
            "Prepared text capability snapshot hash must not be blank"
        }
    }
    private val clipSnapshot: ClipStack = clip.snapshotForPreparedText()
    private val paintSnapshot: Paint = paint.snapshotForPreparedText()

    /**
     * Returns a fresh deep copy so mutable [Path] values inside [ClipStack]
     * cannot alter the validated prepared draw.
     */
    val clip: ClipStack
        get() = clipSnapshot.snapshotForPreparedText()

    /**
     * Returns a fresh deep copy so image pixels, color-filter arrays and
     * runtime-effect graphs cannot alter the validated prepared draw.
     */
    val paint: Paint
        get() = paintSnapshot.snapshotForPreparedText()

    /** Immutable foreground color without re-snapshotting an unrelated shader graph. */
    internal val foregroundColor: ColorARGB
        get() = paintSnapshot.color

    companion object {
        @JvmSynthetic
        internal fun create(
            operationIndex: Int,
            face: GPUPreparedFontFaceSnapshot,
            glyphs: Collection<GPUPreparedGlyphInput>,
            originX: Float,
            originY: Float,
            transform: Matrix3x3F32,
            clipContentKey: String,
            clip: ClipStack,
            paint: Paint,
            material: GPUPreparedMaterialProgram,
            blendPlan: GPUBlendPlan,
            targetColorFormat: String,
            capabilitySnapshotHash: String,
            representationPolicy: GPUPreparedTextRepresentationPolicy,
        ): GPUPreparedTextDraw = GPUPreparedTextDraw(
            operationIndex = operationIndex,
            face = GPUPreparedFontFaceSnapshot.create(
                sourceId = face.sourceId,
                typefaceId = face.typefaceId,
                faceIndex = face.faceIndex,
                bytes = face.bytes,
                provenance = face.provenance,
            ),
            glyphs = immutablePreparedTextList(glyphs),
            originX = originX,
            originY = originY,
            transform = Matrix3x3F32.of(
                transform.sx, transform.kx, transform.tx,
                transform.ky, transform.sy, transform.ty,
                transform.persp0, transform.persp1, transform.persp2,
            ),
            clipContentKey = clipContentKey,
            clip = clip,
            paint = paint,
            material = material,
            blendPlan = blendPlan,
            targetColorFormat = targetColorFormat,
            capabilitySnapshotHash = capabilitySnapshotHash,
            representationPolicy = GPUPreparedTextRepresentationPolicy.create(
                representationPolicy.representations,
            ),
        )
    }
}

/** Terminal result of pure prepared-text lowering. */
sealed interface GPUPreparedTextLowering {
    @ConsistentCopyVisibility
    data class Ready private constructor(
        val draw: GPUPreparedTextDraw,
    ) : GPUPreparedTextLowering {
        companion object {
            @JvmSynthetic
            internal fun create(draw: GPUPreparedTextDraw): Ready = Ready(draw)
        }
    }

    @ConsistentCopyVisibility
    data class Refused private constructor(
        val code: String,
        val operationIndex: Int,
        val facts: Map<String, String>,
    ) : GPUPreparedTextLowering {
        val message: String
            get() = facts["message"].orEmpty()

        companion object {
            /** Creates a terminal refusal without retaining a caller-owned facts map. */
            @JvmSynthetic
            internal fun create(
                code: String,
                operationIndex: Int,
                facts: Map<String, String>,
            ): Refused = Refused(
                code = code,
                operationIndex = operationIndex,
                facts = Collections.unmodifiableMap(LinkedHashMap(facts)),
            )
        }
    }
}

internal fun <T> immutablePreparedTextList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
