package org.graphiks.kanvas.surface.gpu

import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.math.ceil
import org.graphiks.kanvas.font.FontSource
import org.graphiks.kanvas.font.FontSourceKind
import org.graphiks.kanvas.font.sfnt.DefaultOpenTypeFaceParser
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.PathVerb
import org.graphiks.kanvas.glyph.A8GlyphMask
import org.graphiks.kanvas.glyph.GlyphMaskBlurKey
import org.graphiks.kanvas.glyph.GlyphMaskBlurStyle
import org.graphiks.kanvas.glyph.GlyphMaskGenerator
import org.graphiks.kanvas.glyph.GlyphMaskKey
import org.graphiks.kanvas.glyph.OutlineGlyphRepresentation
import org.graphiks.kanvas.glyph.blurGlyphMask
import org.graphiks.kanvas.glyph.color.COLRV0ColorGlyphPlanner
import org.graphiks.kanvas.glyph.color.COLRV0Parser
import org.graphiks.kanvas.glyph.color.COLRV0Table
import org.graphiks.kanvas.glyph.color.CPALPalette
import org.graphiks.kanvas.glyph.color.CPALPaletteSelection
import org.graphiks.kanvas.glyph.color.CPALTable
import org.graphiks.kanvas.glyph.color.CPALV0Parser
import org.graphiks.kanvas.glyph.color.COLRV1GradientEvidence
import org.graphiks.kanvas.glyph.color.COLRV1PaintGraphEvidence
import org.graphiks.kanvas.glyph.color.COLRV1PaintGraphNode
import org.graphiks.kanvas.glyph.color.ColorGlyphBounds
import org.graphiks.kanvas.glyph.color.ColorGlyphPlan
import org.graphiks.kanvas.glyph.color.toGPUColorGlyphLayerPlan
import org.graphiks.kanvas.glyph.gpu.GPUColorGlyphLayerPlan
import org.graphiks.kanvas.glyph.gpu.GPUTextA8AtlasPageArtifact
import org.graphiks.kanvas.glyph.gpu.GPUTextA8AtlasPlacement
import org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.glyph.gpu.GPUTextAtlasPackingRefusal
import org.graphiks.kanvas.glyph.gpu.GPUTextAtlasPackingResult
import org.graphiks.kanvas.glyph.gpu.GPUTextAtlasRectItem
import org.graphiks.kanvas.glyph.gpu.GPUTextAtlasRectPacker
import org.graphiks.kanvas.glyph.gpu.GPUTextFloatRect
import org.graphiks.kanvas.glyph.gpu.GPUTextSourceGlyphIndex
import org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes
import org.graphiks.kanvas.glyph.gpu.GPU_COLOR_GLYPH_COMPOSITE_MAX_LAYERS
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.PreparedTextOutline
import org.graphiks.kanvas.types.Matrix33
import kotlin.uuid.Uuid

data class PreparedTextFrameInventoryLimits(
    val pageWidth: Int,
    val pageHeight: Int,
    val maxPages: Int,
    val maxPageBytes: Int,
    val maxTotalPageBytes: Int,
    val maxGlyphs: Int,
    val maxInstances: Int,
    val maxSubRuns: Int,
    val maxInstanceBytes: Int,
    val maxTextureDimension2D: Int,
) {
    init {
        require(pageWidth > 0) { "pageWidth must be positive." }
        require(pageHeight > 0) { "pageHeight must be positive." }
        require(maxPages >= 0) { "maxPages must be non-negative." }
        require(maxPageBytes >= 0) { "maxPageBytes must be non-negative." }
        require(maxTotalPageBytes >= 0) { "maxTotalPageBytes must be non-negative." }
        require(maxGlyphs >= 0) { "maxGlyphs must be non-negative." }
        require(maxInstances >= 0) { "maxInstances must be non-negative." }
        require(maxSubRuns >= 0) { "maxSubRuns must be non-negative." }
        require(maxInstanceBytes >= 0) { "maxInstanceBytes must be non-negative." }
        require(maxTextureDimension2D > 0) { "maxTextureDimension2D must be positive." }
    }
}

data class GPUPreparedTextFrameMetrics(
    val glyphCount: Int,
    val uniqueMaskCount: Int,
    val instanceCount: Int,
    val subRunCount: Int,
    val pageCount: Int,
    val pageBytes: Int,
    val instanceBytes: Int,
)

data class PreparedTextMaskIdentity(
    val operationIndex: Int,
    val glyphIndex: Int,
    val layerIndex: Int?,
    val maskKeySha256: String,
)

class GPUPreparedTextStrokePath private constructor(
    val operationIndex: Int,
    val glyphIndex: Int,
    val draw: GPUPreparedTextDraw,
    sourcePath: Path,
) {
    private val pathSnapshot: Path = sourcePath.preparedTextPathSnapshot()

    val path: Path
        get() = pathSnapshot.preparedTextPathSnapshot()

    companion object {
        internal fun create(
            operationIndex: Int,
            glyphIndex: Int,
            draw: GPUPreparedTextDraw,
            path: Path,
        ): GPUPreparedTextStrokePath = GPUPreparedTextStrokePath(
            operationIndex = operationIndex,
            glyphIndex = glyphIndex,
            draw = draw,
            sourcePath = path,
        )
    }
}

class GPUPreparedTextSubRun private constructor(
    val operationIndex: Int,
    val subRunIndex: Int,
    val draw: GPUPreparedTextDraw,
    val representation: GPUPreparedTextRepresentation,
    val pageIndex: Int?,
    sourceInstances: List<GPUTextA8Instance>,
    val materialKey: String,
    val blendPlanIdentity: String,
    val clipIdentity: String,
    val transformClass: String,
    val colorGlyphLayerPlan: GPUColorGlyphLayerPlan?,
) {
    val instances: List<GPUTextA8Instance> =
        Collections.unmodifiableList(ArrayList(sourceInstances))

    companion object {
        internal fun create(
            operationIndex: Int,
            subRunIndex: Int,
            draw: GPUPreparedTextDraw,
            representation: GPUPreparedTextRepresentation,
            pageIndex: Int?,
            instances: List<GPUTextA8Instance>,
            materialKey: String,
            blendPlanIdentity: String,
            clipIdentity: String,
            transformClass: String,
            colorGlyphLayerPlan: GPUColorGlyphLayerPlan?,
        ): GPUPreparedTextSubRun = GPUPreparedTextSubRun(
            operationIndex = operationIndex,
            subRunIndex = subRunIndex,
            draw = draw,
            representation = representation,
            pageIndex = pageIndex,
            sourceInstances = instances,
            materialKey = materialKey,
            blendPlanIdentity = blendPlanIdentity,
            clipIdentity = clipIdentity,
            transformClass = transformClass,
            colorGlyphLayerPlan = colorGlyphLayerPlan?.immutableSnapshot(),
        )
    }
}

class PreparedTextFrameInventory private constructor(
    val generation: GPUTextArtifactGeneration,
    sourcePages: List<GPUTextA8AtlasPageArtifact>,
    sourceSubRunsByOperationIndex: Map<Int, List<GPUPreparedTextSubRun>>,
    sourceStrokePathsByOperationIndex: Map<Int, List<GPUPreparedTextStrokePath>>,
    sourceAcceptedTextOperationIndices: Set<Int>,
    val metrics: GPUPreparedTextFrameMetrics,
    sourceMaskIdentityByGlyphUse: List<PreparedTextMaskIdentity>,
    val contentSha256: String,
) {
    val pages: List<GPUTextA8AtlasPageArtifact> =
        Collections.unmodifiableList(ArrayList(sourcePages))
    val subRunsByOperationIndex: Map<Int, List<GPUPreparedTextSubRun>> =
        Collections.unmodifiableMap(
            LinkedHashMap<Int, List<GPUPreparedTextSubRun>>().also { snapshot ->
                sourceSubRunsByOperationIndex.forEach { (operationIndex, subRuns) ->
                    snapshot[operationIndex] = Collections.unmodifiableList(ArrayList(subRuns))
                }
            },
        )
    val strokePathsByOperationIndex: Map<Int, List<GPUPreparedTextStrokePath>> =
        Collections.unmodifiableMap(
            LinkedHashMap<Int, List<GPUPreparedTextStrokePath>>().also { snapshot ->
                sourceStrokePathsByOperationIndex.forEach { (operationIndex, paths) ->
                    snapshot[operationIndex] = Collections.unmodifiableList(ArrayList(paths))
                }
            },
        )
    val acceptedTextOperationIndices: Set<Int> =
        Collections.unmodifiableSet(LinkedHashSet(sourceAcceptedTextOperationIndices))
    val maskIdentityByGlyphUse: List<PreparedTextMaskIdentity> =
        Collections.unmodifiableList(ArrayList(sourceMaskIdentityByGlyphUse))

    companion object {
        internal fun create(
            generation: GPUTextArtifactGeneration,
            pages: List<GPUTextA8AtlasPageArtifact>,
            subRunsByOperationIndex: Map<Int, List<GPUPreparedTextSubRun>>,
            strokePathsByOperationIndex: Map<Int, List<GPUPreparedTextStrokePath>>,
            acceptedTextOperationIndices: Set<Int>,
            metrics: GPUPreparedTextFrameMetrics,
            maskIdentityByGlyphUse: List<PreparedTextMaskIdentity>,
            contentSha256: String,
        ): PreparedTextFrameInventory = PreparedTextFrameInventory(
            generation = generation,
            sourcePages = pages,
            sourceSubRunsByOperationIndex = subRunsByOperationIndex,
            sourceStrokePathsByOperationIndex = strokePathsByOperationIndex,
            sourceAcceptedTextOperationIndices = acceptedTextOperationIndices,
            metrics = metrics,
            sourceMaskIdentityByGlyphUse = maskIdentityByGlyphUse,
            contentSha256 = contentSha256,
        )
    }
}

sealed interface PreparedTextFrameInventoryResult {
    data class Ready(val inventory: PreparedTextFrameInventory) :
        PreparedTextFrameInventoryResult

    class Refused internal constructor(
        val code: String,
        val operationIndex: Int?,
        facts: Map<String, String>,
    ) : PreparedTextFrameInventoryResult {
        val facts: Map<String, String> =
            Collections.unmodifiableMap(LinkedHashMap(facts))
    }
}

sealed interface PreparedTextColorLayerArtifact {
    val layerIndex: Int
    val glyphId: Int

    data class A8(
        override val layerIndex: Int,
        val mask: A8GlyphMask,
        val maskKey: GlyphMaskKey,
    ) : PreparedTextColorLayerArtifact {
        override val glyphId: Int
            get() = mask.glyphId
    }

    data class Empty(
        override val layerIndex: Int,
        override val glyphId: Int,
    ) : PreparedTextColorLayerArtifact
}

sealed interface PreparedTextGlyphArtifact {
    data class A8(
        val mask: A8GlyphMask,
        val maskKey: GlyphMaskKey,
    ) : PreparedTextGlyphArtifact

    class COLRV0(
        layers: List<PreparedTextColorLayerArtifact>,
        colorPlan: ColorGlyphPlan,
    ) : PreparedTextGlyphArtifact {
        val layers: List<PreparedTextColorLayerArtifact> =
            Collections.unmodifiableList(ArrayList(layers))
        val colorPlan: ColorGlyphPlan = colorPlan.immutableSnapshot()
    }

    data object Empty : PreparedTextGlyphArtifact

    class Refused(
        val code: String,
        facts: Map<String, String>,
    ) : PreparedTextGlyphArtifact {
        val facts: Map<String, String> =
            Collections.unmodifiableMap(LinkedHashMap(facts))
    }
}

internal interface PreparedTextFrameInventoryObserver {
    fun onFaceParsed() = Unit

    fun onColorTablesParsed() = Unit

    fun onDrawFactsComputed() = Unit

    fun onBlurConvolution() = Unit

    fun onMaskFingerprintComputed() = Unit

    fun onMaskSamplesValidated() = Unit

    fun onPageMaterialized() = Unit
}

private object NoOpPreparedTextFrameInventoryObserver :
    PreparedTextFrameInventoryObserver

fun interface PreparedTextGlyphArtifactResolver {
    fun resolve(
        draw: GPUPreparedTextDraw,
        glyphIndex: Int,
        representation: GPUPreparedTextRepresentation,
    ): PreparedTextGlyphArtifact
}

/**
 * Exact product resolver: it reconstructs the snapshotted face, verifies all identity fields and
 * invokes the same face-indexed outline authority used during Task 4 lowering.
 */
object ExactPreparedTextGlyphArtifactResolver : PreparedTextGlyphArtifactResolver {
    override fun resolve(
        draw: GPUPreparedTextDraw,
        glyphIndex: Int,
        representation: GPUPreparedTextRepresentation,
    ): PreparedTextGlyphArtifact = when (val face = reconstructExactTypeface(draw)) {
        is ExactTypefaceResolution.Ready ->
            resolveExactArtifact(
                draw = draw,
                glyphIndex = glyphIndex,
                representation = representation,
                typeface = face.typeface,
                colorContext = null,
                layerResolver = null,
            )
        is ExactTypefaceResolution.Refused -> face.artifact
    }
}

internal class PerFrameExactPreparedTextGlyphArtifactResolver(
    private val observer: PreparedTextFrameInventoryObserver =
        NoOpPreparedTextFrameInventoryObserver,
) :
    PreparedTextGlyphArtifactResolver {
    private val faceKeys = IdentityHashMap<GPUPreparedFontFaceSnapshot, ExactFaceKey>()
    private val faces = LinkedHashMap<ExactFaceKey, ExactTypefaceResolution>()
    private val artifacts = LinkedHashMap<ExactArtifactRequestKey, PreparedTextGlyphArtifact>()
    private val colorContexts = LinkedHashMap<ExactFaceKey, ExactColorContextResolution>()

    override fun resolve(
        draw: GPUPreparedTextDraw,
        glyphIndex: Int,
        representation: GPUPreparedTextRepresentation,
    ): PreparedTextGlyphArtifact {
        val glyph = draw.glyphs.getOrNull(glyphIndex) ?: return artifactRefused(
            GPUTextRefusalCodes.GLYPH_ID_INVALID,
            "glyph-index-out-of-range",
        )
        val faceKey = faceKeys.getOrPut(draw.face) {
            ExactFaceKey(
                sourceId = draw.face.sourceId,
                typefaceId = draw.face.typefaceId,
                faceIndex = draw.face.faceIndex,
                bytesSha256 = sha256UnsignedBytes(draw.face.bytes),
                provenance = draw.face.provenance,
            )
        }
        return when (
            val face = faces.getOrPut(faceKey) {
                observer.onFaceParsed()
                reconstructExactTypeface(draw)
            }
        ) {
            is ExactTypefaceResolution.Ready -> artifacts.getOrPut(
                ExactArtifactRequestKey(
                    face = faceKey,
                    glyphId = glyph.glyphId,
                    strikeKey = glyph.strikeKey,
                    representation = representation,
                ),
            ) {
                val colorContext = if (representation == GPUPreparedTextRepresentation.COLRV0) {
                    colorContexts.getOrPut(faceKey) {
                        observer.onColorTablesParsed()
                        parseExactColorContext(draw, face.typeface)
                    }
                } else {
                    null
                }
                resolveExactArtifact(
                    draw = draw,
                    glyphIndex = glyphIndex,
                    representation = representation,
                    typeface = face.typeface,
                    colorContext = colorContext,
                    layerResolver = { layerGlyphId, layerStrike ->
                        artifacts.getOrPut(
                            ExactArtifactRequestKey(
                                face = faceKey,
                                glyphId = layerGlyphId,
                                strikeKey = layerStrike,
                                representation = GPUPreparedTextRepresentation.A8_MASK,
                            ),
                        ) {
                            resolveA8Artifact(
                                draw = draw,
                                glyphIndex = glyphIndex,
                                typeface = face.typeface,
                                glyphId = layerGlyphId,
                                strikeKey = layerStrike,
                            )
                        }
                    },
                )
            }
            is ExactTypefaceResolution.Refused -> face.artifact
        }
    }
}

private data class ExactFaceKey(
    val sourceId: org.graphiks.kanvas.font.FontSourceID,
    val typefaceId: org.graphiks.kanvas.font.TypefaceID,
    val faceIndex: Int,
    val bytesSha256: String,
    val provenance: String,
)

private data class ExactArtifactRequestKey(
    val face: ExactFaceKey,
    val glyphId: Int,
    val strikeKey: org.graphiks.kanvas.glyph.GlyphStrikeKey,
    val representation: GPUPreparedTextRepresentation,
)

private sealed interface ExactTypefaceResolution {
    data class Ready(val typeface: FontTypeface) : ExactTypefaceResolution
    data class Refused(val artifact: PreparedTextGlyphArtifact.Refused) :
        ExactTypefaceResolution
}

private data class ExactColorContext(
    val colr: COLRV0Table,
    val cpal: CPALTable,
)

private sealed interface ExactColorContextResolution {
    data class Ready(val context: ExactColorContext) : ExactColorContextResolution

    data class Refused(val artifact: PreparedTextGlyphArtifact.Refused) :
        ExactColorContextResolution
}

private fun reconstructExactTypeface(
    draw: GPUPreparedTextDraw,
): ExactTypefaceResolution {
    val bytes = draw.face.bytes.map(Int::toByte).toByteArray()
    val fontName = draw.face.provenance.removePrefix("memory:")
    val typeface = runCatching {
        FontTypeface(bytes, fontName = fontName, faceIndex = draw.face.faceIndex)
    }.getOrElse {
        return ExactTypefaceResolution.Refused(
            artifactRefused(
                GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                "face-reconstruction-failed",
            ),
        )
    }
    if (
        typeface.sourceId != draw.face.sourceId ||
        typeface.typefaceId != draw.face.typefaceId ||
        typeface.faceIndex != draw.face.faceIndex
    ) {
        return ExactTypefaceResolution.Refused(
            artifactRefused(
                GPUTextRefusalCodes.FONT_IDENTITY_UNSTABLE,
                "face-identity-mismatch",
            ),
        )
    }
    return ExactTypefaceResolution.Ready(typeface)
}

private fun resolveExactArtifact(
    draw: GPUPreparedTextDraw,
    glyphIndex: Int,
    representation: GPUPreparedTextRepresentation,
    typeface: FontTypeface,
    colorContext: ExactColorContextResolution?,
    layerResolver: ((Int, org.graphiks.kanvas.glyph.GlyphStrikeKey) -> PreparedTextGlyphArtifact)?,
): PreparedTextGlyphArtifact {
        val glyph = draw.glyphs.getOrNull(glyphIndex) ?: return artifactRefused(
            GPUTextRefusalCodes.GLYPH_ID_INVALID,
            "glyph-index-out-of-range",
        )
    return when (representation) {
        GPUPreparedTextRepresentation.A8_MASK ->
            resolveA8Artifact(draw, glyphIndex, typeface, glyph.glyphId, glyph.strikeKey)
        GPUPreparedTextRepresentation.COLRV0 ->
            resolveCOLRV0Artifact(
                draw = draw,
                glyphIndex = glyphIndex,
                typeface = typeface,
                colorContext = colorContext,
                layerResolver = layerResolver,
            )
    }
}

object PreparedTextFrameInventoryBuilder {
    fun build(
        draws: List<GPUPreparedTextDraw>,
        generation: GPUTextArtifactGeneration,
        limits: PreparedTextFrameInventoryLimits,
    ): PreparedTextFrameInventoryResult = build(
        draws = draws,
        generation = generation,
        limits = limits,
        artifactResolver = PerFrameExactPreparedTextGlyphArtifactResolver(),
    )

    internal fun build(
        draws: List<GPUPreparedTextDraw>,
        generation: GPUTextArtifactGeneration,
        limits: PreparedTextFrameInventoryLimits,
        artifactResolver: PreparedTextGlyphArtifactResolver,
        observer: PreparedTextFrameInventoryObserver =
            NoOpPreparedTextFrameInventoryObserver,
    ): PreparedTextFrameInventoryResult {
        val glyphCount = draws.sumOf { draw -> draw.glyphs.size.toLong() }
        if (glyphCount > limits.maxGlyphs.toLong()) {
            return refused(
                code = GPUTextRefusalCodes.GLYPH_BUDGET_EXCEEDED,
                operationIndex = draws.firstOrNull()?.operationIndex,
                facts = mapOf("glyphCount" to glyphCount.toString()),
            )
        }
        if (
            limits.pageWidth > limits.maxTextureDimension2D ||
            limits.pageHeight > limits.maxTextureDimension2D
        ) {
            return refused(
                code = GPUTextRefusalCodes.ATLAS_PAGE_BUDGET_EXCEEDED,
                operationIndex = draws.firstOrNull()?.operationIndex,
                facts = mapOf(
                    "pageWidth" to limits.pageWidth.toString(),
                    "pageHeight" to limits.pageHeight.toString(),
                    "maxTextureDimension2D" to limits.maxTextureDimension2D.toString(),
                ),
            )
        }
        val operationIndexes = draws.map { draw -> draw.operationIndex }
        if (operationIndexes.toSet().size != operationIndexes.size) {
            return refused(
                code = GPUTextRefusalCodes.OWNERSHIP_INVALID,
                operationIndex = operationIndexes.firstOrNull(),
                facts = mapOf("reason" to "duplicate-operation-index"),
            )
        }
        val drawByOperationIndex = draws.associateBy(GPUPreparedTextDraw::operationIndex)

        val uniqueMasks = LinkedHashMap<GlyphMaskKey, PreparedMask>()
        val rawContentByMaskKey = LinkedHashMap<GlyphMaskKey, String>()
        val rawFingerprintByMaskIdentity = IdentityHashMap<A8GlyphMask, String>()
        val preparedMaskCache = LinkedHashMap<PreparedMaskCacheKey, PreparedMask>()
        val drawFacts = IdentityHashMap<GPUPreparedTextDraw, PreparedTextDrawFacts>()
        val resolvedUses = ArrayList<ResolvedGlyphUse>()
        val strokePathsByOperation = LinkedHashMap<Int, List<GPUPreparedTextStrokePath>>()
        for (draw in draws) {
            if (draw.paint.style == PaintStyle.STROKE) {
                val strokePaths = when (val resolution = resolvePreparedTextStrokePaths(draw)) {
                    is PreparedTextStrokePathResolution.Ready -> resolution.paths
                    is PreparedTextStrokePathResolution.Refused -> return refused(
                        code = resolution.code,
                        operationIndex = draw.operationIndex,
                        facts = resolution.facts,
                    )
                }
                strokePathsByOperation[draw.operationIndex] = strokePaths
                continue
            }
            val facts = drawFacts.getOrPut(draw) { draw.inventoryFacts(observer) }
            if (draw.representationPolicy.representations.size != draw.glyphs.size) {
                return refused(
                    code = GPUTextRefusalCodes.REPRESENTATION_MISSING,
                    operationIndex = draw.operationIndex,
                    facts = mapOf("reason" to "representation-count-mismatch"),
                )
            }
            for (glyphIndex in draw.glyphs.indices) {
                val representation = draw.representationPolicy.representations[glyphIndex]
                val artifact = try {
                    artifactResolver.resolve(draw, glyphIndex, representation)
                } catch (_: Exception) {
                    return refused(
                        code = GPUTextRefusalCodes.MASK_GENERATION_FAILED,
                        operationIndex = draw.operationIndex,
                        facts = mapOf(
                            "reason" to "artifact-resolver-exception",
                            "glyphIndex" to glyphIndex.toString(),
                        ),
                    )
                }
                when (artifact) {
                    PreparedTextGlyphArtifact.Empty -> Unit
                    is PreparedTextGlyphArtifact.Refused ->
                        return refused(artifact.code, draw.operationIndex, artifact.facts)
                    is PreparedTextGlyphArtifact.A8 -> {
                        artifact.structuralValidationFailure(
                            expectedGlyphId = draw.glyphs[glyphIndex].glyphId,
                        )?.let { reason ->
                            return refused(
                                reason.maskValidationRefusalCode(),
                                draw.operationIndex,
                                mapOf("reason" to reason),
                            )
                        }
                        if (artifact.mask.width == 0 && artifact.mask.height == 0) continue
                        firstNonEmptyUseBudgetRefusal(limits, draw.operationIndex)?.let {
                            return it
                        }
                        artifact.sampleValidationFailure(observer)?.let { reason ->
                            return refused(
                                reason.maskValidationRefusalCode(),
                                draw.operationIndex,
                                mapOf("reason" to reason),
                            )
                        }
                        val prepared = when (
                            val resolution = prepareMask(
                                artifact = artifact,
                                blur = facts.blur,
                                limits = limits,
                                rawContentByMaskKey = rawContentByMaskKey,
                                rawFingerprintByMaskIdentity = rawFingerprintByMaskIdentity,
                                preparedMaskCache = preparedMaskCache,
                                uniqueMasks = uniqueMasks,
                                observer = observer,
                            )
                        ) {
                            is PreparedMaskResolution.Ready -> resolution.prepared
                            is PreparedMaskResolution.Refused -> return refused(
                                resolution.code,
                                draw.operationIndex,
                                mapOf("reason" to resolution.reason),
                            )
                        }
                        resolvedUses += ResolvedGlyphUse(
                            draw = draw,
                            glyphIndex = glyphIndex,
                            glyphId = draw.glyphs[glyphIndex].glyphId,
                            representation = representation,
                            preparedMask = prepared,
                            layerIndex = null,
                            colorPlan = null,
                        )
                    }
                    is PreparedTextGlyphArtifact.COLRV0 -> {
                        if (
                            artifact.layers.isEmpty() ||
                            artifact.layers.size != artifact.colorPlan.layers.size ||
                            artifact.colorPlan.glyphId != draw.glyphs[glyphIndex].glyphId ||
                            artifact.layers.indices.any { layerIndex ->
                                val layer = artifact.layers[layerIndex]
                                val planLayer = artifact.colorPlan.layers[layerIndex]
                                layer.layerIndex != layerIndex ||
                                    planLayer.layerIndex != layerIndex ||
                                    layer.glyphId != planLayer.glyphId ||
                                    (
                                        layer is PreparedTextColorLayerArtifact.A8 &&
                                            layer.maskKey.strikeKey.glyphId != planLayer.glyphId
                                        )
                            }
                        ) {
                            return refused(
                                GPUTextRefusalCodes.ARTIFACT_KEY_NONDETERMINISTIC,
                                draw.operationIndex,
                                mapOf("reason" to "colrv0-layer-plan-mismatch"),
                            )
                        }
                        val planLayerCount = artifact.colorPlan.layers.size
                        if (planLayerCount > GPU_COLOR_GLYPH_COMPOSITE_MAX_LAYERS) {
                            return refused(
                                GPUTextRefusalCodes.COLOR_PLAN_UNSUPPORTED,
                                draw.operationIndex,
                                mapOf(
                                    "reason" to "colrv0-layer-count-exceeds-payload-limit",
                                    "layerCount" to planLayerCount.toString(),
                                    "maxLayerCount" to
                                        GPU_COLOR_GLYPH_COMPOSITE_MAX_LAYERS.toString(),
                                ),
                            )
                        }
                        val gpuPlan = artifact.colorPlan.toGPUColorGlyphLayerPlan(
                            artifactID = COLOR_GLYPH_ARTIFACT_ID,
                            generation = generation,
                        ).immutableSnapshot()
                        artifact.layers.forEach { layer ->
                            if (layer is PreparedTextColorLayerArtifact.Empty) return@forEach
                            layer as PreparedTextColorLayerArtifact.A8
                            val layerArtifact = PreparedTextGlyphArtifact.A8(
                                mask = layer.mask,
                                maskKey = layer.maskKey,
                            )
                            layerArtifact.structuralValidationFailure(
                                expectedGlyphId = layer.mask.glyphId,
                            )?.let { reason ->
                                return refused(
                                    reason.maskValidationRefusalCode(),
                                    draw.operationIndex,
                                    mapOf("reason" to reason),
                                )
                            }
                            if (layer.mask.width == 0 && layer.mask.height == 0) return@forEach
                            firstNonEmptyUseBudgetRefusal(limits, draw.operationIndex)?.let {
                                return it
                            }
                            layerArtifact.sampleValidationFailure(observer)?.let { reason ->
                                return refused(
                                    reason.maskValidationRefusalCode(),
                                    draw.operationIndex,
                                    mapOf("reason" to reason),
                                )
                            }
                            val prepared = when (
                                val resolution = prepareMask(
                                    artifact = layerArtifact,
                                    blur = facts.blur,
                                    limits = limits,
                                    rawContentByMaskKey = rawContentByMaskKey,
                                    rawFingerprintByMaskIdentity = rawFingerprintByMaskIdentity,
                                    preparedMaskCache = preparedMaskCache,
                                    uniqueMasks = uniqueMasks,
                                    observer = observer,
                                )
                            ) {
                                is PreparedMaskResolution.Ready -> resolution.prepared
                                is PreparedMaskResolution.Refused -> return refused(
                                    resolution.code,
                                    draw.operationIndex,
                                    mapOf("reason" to resolution.reason),
                                )
                            }
                            resolvedUses += ResolvedGlyphUse(
                                draw = draw,
                                glyphIndex = glyphIndex,
                                glyphId = layer.mask.glyphId,
                                representation = representation,
                                preparedMask = prepared,
                                layerIndex = layer.layerIndex,
                                colorPlan = gpuPlan,
                            )
                        }
                    }
                }
            }
        }

        val maskKeyByHash = LinkedHashMap<String, GlyphMaskKey>()
        uniqueMasks.keys.forEach { maskKey ->
            val hash = maskKey.sha256()
            val previous = maskKeyByHash.putIfAbsent(hash, maskKey)
            if (previous != null && previous != maskKey) {
                return refused(
                    GPUTextRefusalCodes.ARTIFACT_KEY_NONDETERMINISTIC,
                    resolvedUses.firstOrNull()?.draw?.operationIndex,
                    mapOf("reason" to "mask-key-sha256-collision"),
                )
            }
        }

        if (resolvedUses.size > limits.maxInstances) {
            return refused(
                GPUTextRefusalCodes.INSTANCE_BUFFER_BUDGET_EXCEEDED,
                resolvedUses.firstOrNull()?.draw?.operationIndex,
                mapOf("instanceCount" to resolvedUses.size.toString()),
            )
        }
        val instanceBytes = resolvedUses.size.toLong() * GPUTextA8Instance.ENCODED_BYTE_SIZE
        if (instanceBytes > limits.maxInstanceBytes.toLong()) {
            return refused(
                GPUTextRefusalCodes.INSTANCE_BYTES_EXCEEDED,
                resolvedUses.firstOrNull()?.draw?.operationIndex,
                mapOf("instanceBytes" to instanceBytes.toString()),
            )
        }

        val packResult = GPUTextAtlasRectPacker.pack(
            items = uniqueMasks.values.map { prepared ->
                GPUTextAtlasRectItem(
                    itemKey = prepared.maskKey.sha256(),
                    width = prepared.mask.width,
                    height = prepared.mask.height,
                    guardPx = 1,
                )
            },
            pageWidth = limits.pageWidth,
            pageHeight = limits.pageHeight,
            maxPages = limits.maxPages,
        )
        val packing = when (packResult) {
            is GPUTextAtlasPackingResult.Ready -> packResult
            is GPUTextAtlasPackingResult.Refused -> return refused(
                code = when (packResult.reason) {
                    GPUTextAtlasPackingRefusal.ITEM_TOO_LARGE,
                    GPUTextAtlasPackingRefusal.PAGE_LIMIT,
                    -> GPUTextRefusalCodes.ATLAS_PAGE_BUDGET_EXCEEDED
                },
                operationIndex = resolvedUses.firstOrNull()?.draw?.operationIndex,
                facts = mapOf(
                    "reason" to packResult.reason.name,
                    "itemKey" to packResult.itemKey,
                ),
            )
        }
        val pageBytes = limits.pageWidth.toLong() * limits.pageHeight.toLong()
        val totalPageBytes = pageBytes * packing.pageCount.toLong()
        if (totalPageBytes > limits.maxTotalPageBytes.toLong()) {
            return refused(
                GPUTextRefusalCodes.ATLAS_TOTAL_BYTES_EXCEEDED,
                resolvedUses.firstOrNull()?.draw?.operationIndex,
                mapOf("totalPageBytes" to totalPageBytes.toString()),
            )
        }

        val placementByKey = packing.placements.associateBy { placement -> placement.itemKey }
        val maskIdentities = ArrayList<PreparedTextMaskIdentity>(resolvedUses.size)
        val groupedInput = ArrayList<SubRunInstance>(resolvedUses.size)
        for (use in resolvedUses) {
            val maskHash = use.preparedMask.maskKey.sha256()
            val placement = checkNotNull(placementByKey[maskHash])
            val facts = checkNotNull(drawFacts[use.draw])
            val instance = GPUTextA8Instance.create(
                glyphId = use.glyphId,
                sourceGlyphIndex = GPUTextSourceGlyphIndex(use.glyphIndex),
                deviceQuad = deviceQuad(use.draw, use.glyphIndex, use.preparedMask.mask),
                uvRect = GPUTextFloatRect(
                    left = placement.contentRect.left.toFloat() / limits.pageWidth.toFloat(),
                    top = placement.contentRect.top.toFloat() / limits.pageHeight.toFloat(),
                    right = placement.contentRect.right.toFloat() / limits.pageWidth.toFloat(),
                    bottom = placement.contentRect.bottom.toFloat() / limits.pageHeight.toFloat(),
                ),
                pageIndex = placement.pageIndex,
                colorLayerIndex = use.layerIndex,
            )
            val key = PreparedTextSubRunIdentity(
                operationIndex = use.draw.operationIndex,
                representation = use.representation,
                pageIndex = placement.pageIndex,
                materialKey = facts.materialKey,
                blendPlanIdentity = facts.blendPlanIdentity,
                clipIdentity = facts.clipIdentity,
                transformClass = facts.transformClass,
                colorPlanIdentity = use.colorPlan?.artifactKey?.contentFingerprint,
            )
            groupedInput += SubRunInstance(key, instance, use.colorPlan)
            maskIdentities += PreparedTextMaskIdentity(
                operationIndex = use.draw.operationIndex,
                glyphIndex = use.glyphIndex,
                layerIndex = use.layerIndex,
                maskKeySha256 = maskHash,
            )
        }
        val groupedSubRuns = groupPreparedTextSubRuns(groupedInput)
        val atlasSubRunCount = groupedSubRuns.size
        val strokePathCount = strokePathsByOperation.values.sumOf { paths -> paths.size }
        val subRunCount = Math.addExact(atlasSubRunCount, strokePathCount)
        if (subRunCount > limits.maxSubRuns) {
            return refused(
                GPUTextRefusalCodes.SUBRUN_BUDGET_EXCEEDED,
                groupedInput.firstOrNull()?.key?.operationIndex
                    ?: strokePathsByOperation.keys.firstOrNull(),
                mapOf("subRunCount" to subRunCount.toString()),
            )
        }

        val preparedByHash = uniqueMasks.values.associateBy { prepared ->
            prepared.maskKey.sha256()
        }
        val pageArtifacts = ArrayList<GPUTextA8AtlasPageArtifact>(packing.pageCount)
        for (pageIndex in 0 until packing.pageCount) {
            val placements = packing.placements.filter { placement ->
                placement.pageIndex == pageIndex
            }
            observer.onPageMaterialized()
            val bytes = MutableList(pageBytes.toInt()) { 0 }
            for (placement in placements) {
                val prepared = checkNotNull(preparedByHash[placement.itemKey])
                copyMaskToPage(prepared.mask, placement, limits.pageWidth, bytes)
            }
            val contentHash = GPUTextA8AtlasPageArtifact.sha256(bytes)
            val contentFingerprint = GPUTextA8AtlasPageArtifact.contentFingerprint(
                width = limits.pageWidth,
                height = limits.pageHeight,
                rowBytes = limits.pageWidth,
                contentSha256 = contentHash,
                placements = placements,
            )
            pageArtifacts += GPUTextA8AtlasPageArtifact.create(
                artifactKey = GPUTextArtifactKey(
                    artifactID = A8_PAGE_ARTIFACT_ID,
                    generation = generation,
                    contentFingerprint = contentFingerprint,
                ),
                pageIndex = pageIndex,
                width = limits.pageWidth,
                height = limits.pageHeight,
                rowBytes = limits.pageWidth,
                bytes = bytes,
                contentSha256 = contentHash,
                placements = placements,
            )
        }

        val subRunsByOperation = LinkedHashMap<Int, MutableList<GPUPreparedTextSubRun>>()
        groupedSubRuns.forEach { grouped ->
            val key = grouped.key
            val operationSubRuns = subRunsByOperation.getOrPut(key.operationIndex) {
                mutableListOf()
            }
            operationSubRuns += GPUPreparedTextSubRun.create(
                operationIndex = key.operationIndex,
                subRunIndex = operationSubRuns.size,
                draw = checkNotNull(drawByOperationIndex[key.operationIndex]),
                representation = key.representation,
                pageIndex = key.pageIndex,
                instances = grouped.instances,
                materialKey = key.materialKey,
                blendPlanIdentity = key.blendPlanIdentity,
                clipIdentity = key.clipIdentity,
                transformClass = key.transformClass,
                colorGlyphLayerPlan = grouped.colorPlan,
            )
        }
        check(subRunsByOperation.values.sumOf { subRuns -> subRuns.size } == atlasSubRunCount)

        val immutableSubRuns = LinkedHashMap<Int, List<GPUPreparedTextSubRun>>()
        subRunsByOperation.forEach { (operationIndex, subRuns) ->
            immutableSubRuns[operationIndex] = subRuns.toList()
        }
        val metrics = GPUPreparedTextFrameMetrics(
            glyphCount = glyphCount.toInt(),
            uniqueMaskCount = uniqueMasks.size,
            instanceCount = resolvedUses.size,
            subRunCount = subRunCount,
            pageCount = pageArtifacts.size,
            pageBytes = totalPageBytes.toInt(),
            instanceBytes = instanceBytes.toInt(),
        )
        val contentHash = inventoryHash(
            generation = generation,
            pages = pageArtifacts,
            subRuns = immutableSubRuns,
            strokePaths = strokePathsByOperation,
            acceptedTextOperationIndices = operationIndexes.toSet(),
            metrics = metrics,
            identities = maskIdentities,
        )
        return PreparedTextFrameInventoryResult.Ready(
            PreparedTextFrameInventory.create(
                generation = generation,
                pages = pageArtifacts,
                subRunsByOperationIndex = immutableSubRuns,
                strokePathsByOperationIndex = strokePathsByOperation,
                acceptedTextOperationIndices = operationIndexes.toSet(),
                metrics = metrics,
                maskIdentityByGlyphUse = maskIdentities,
                contentSha256 = contentHash,
            ),
        )
    }
}

private data class PreparedMask(
    val mask: A8GlyphMask,
    val maskKey: GlyphMaskKey,
    val blurPaddingPx: Int,
    val contentFingerprint: String,
)

private data class PreparedMaskCacheKey(
    val maskKey: GlyphMaskKey,
    val rawContentFingerprint: String,
    val blur: GlyphMaskBlurKey?,
)

private data class PreparedTextDrawFacts(
    val blur: GlyphMaskBlurKey?,
    val materialKey: String,
    val blendPlanIdentity: String,
    val clipIdentity: String,
    val transformClass: String,
)

private sealed interface PreparedTextStrokePathResolution {
    data class Ready(
        val paths: List<GPUPreparedTextStrokePath>,
    ) : PreparedTextStrokePathResolution

    data class Refused(
        val code: String,
        val facts: Map<String, String>,
    ) : PreparedTextStrokePathResolution
}

private sealed interface PreparedMaskResolution {
    data class Ready(val prepared: PreparedMask) : PreparedMaskResolution

    data class Refused(
        val code: String,
        val reason: String,
    ) : PreparedMaskResolution
}

private data class ResolvedGlyphUse(
    val draw: GPUPreparedTextDraw,
    val glyphIndex: Int,
    val glyphId: Int,
    val representation: GPUPreparedTextRepresentation,
    val preparedMask: PreparedMask,
    val layerIndex: Int?,
    val colorPlan: GPUColorGlyphLayerPlan?,
)

internal data class PreparedTextSubRunIdentity(
    val operationIndex: Int,
    val representation: GPUPreparedTextRepresentation,
    val pageIndex: Int,
    val materialKey: String,
    val blendPlanIdentity: String,
    val clipIdentity: String,
    val transformClass: String,
    val colorPlanIdentity: String?,
)

internal fun countPreparedTextSubRuns(
    identities: List<PreparedTextSubRunIdentity>,
): Int {
    var count = 0
    var previous: PreparedTextSubRunIdentity? = null
    identities.forEach { identity ->
        if (identity != previous) {
            count += 1
            previous = identity
        }
    }
    return count
}

private data class SubRunInstance(
    val key: PreparedTextSubRunIdentity,
    val instance: GPUTextA8Instance,
    val colorPlan: GPUColorGlyphLayerPlan?,
)

private data class GroupedPreparedTextSubRun(
    val key: PreparedTextSubRunIdentity,
    val instances: List<GPUTextA8Instance>,
    val colorPlan: GPUColorGlyphLayerPlan?,
)

private fun groupPreparedTextSubRuns(
    inputs: List<SubRunInstance>,
): List<GroupedPreparedTextSubRun> {
    val grouped = ArrayList<GroupedPreparedTextSubRun>()
    var start = 0
    while (start < inputs.size) {
        val key = inputs[start].key
        var end = start
        if (key.representation == GPUPreparedTextRepresentation.COLRV0) {
            var layerCount = 0
            while (end < inputs.size && inputs[end].key == key) {
                val occurrence = inputs[end].instance.sourceGlyphIndex
                var occurrenceEnd = end + 1
                while (
                    occurrenceEnd < inputs.size &&
                    inputs[occurrenceEnd].key == key &&
                    inputs[occurrenceEnd].instance.sourceGlyphIndex == occurrence
                ) {
                    occurrenceEnd += 1
                }
                val occurrenceLayerCount = occurrenceEnd - end
                check(occurrenceLayerCount <= GPU_COLOR_GLYPH_COMPOSITE_MAX_LAYERS)
                if (
                    layerCount > 0 &&
                    layerCount + occurrenceLayerCount > GPU_COLOR_GLYPH_COMPOSITE_MAX_LAYERS
                ) {
                    break
                }
                layerCount += occurrenceLayerCount
                end = occurrenceEnd
            }
        } else {
            while (end < inputs.size && inputs[end].key == key) {
                end += 1
            }
        }
        check(end > start)
        grouped += GroupedPreparedTextSubRun(
            key = key,
            instances = inputs.subList(start, end).map(SubRunInstance::instance),
            colorPlan = inputs[start].colorPlan,
        )
        start = end
    }
    return grouped
}

private fun PreparedTextGlyphArtifact.A8.structuralValidationFailure(
    expectedGlyphId: Int,
): String? = when {
    mask.glyphId != expectedGlyphId -> "mask-glyph-id-mismatch"
    maskKey.strikeKey.glyphId != expectedGlyphId -> "mask-key-glyph-id-mismatch"
    maskKey.faceIndex < 0 -> "mask-key-face-index-invalid"
    mask.sourceOutlineSha256 != maskKey.sourceOutlineSha256 ->
        "mask-source-outline-hash-mismatch"
    mask.width < 0 || mask.height < 0 -> "mask-extents-negative"
    mask.rowBytes < mask.width || mask.rowBytes < 0 -> "mask-row-bytes-invalid"
    mask.rowBytes.toLong() * mask.height.toLong() != mask.pixels.size.toLong() ->
        "mask-pixel-count-mismatch"
    mask.left.toLong() + mask.width.toLong() !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ->
        "mask-horizontal-bounds-overflow"
    mask.top.toLong() + mask.height.toLong() !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ->
        "mask-vertical-bounds-overflow"
    (mask.width == 0) != (mask.height == 0) -> "mask-empty-extents-inconsistent"
    mask.width == 0 && mask.pixels.isNotEmpty() -> "empty-mask-pixels-not-empty"
    else -> null
}

private fun PreparedTextGlyphArtifact.A8.sampleValidationFailure(
    observer: PreparedTextFrameInventoryObserver,
): String? {
    observer.onMaskSamplesValidated()
    return if (mask.pixels.any { sample -> sample !in 0..255 }) {
        "mask-sample-invalid"
    } else {
        null
    }
}

private fun String.maskValidationRefusalCode(): String = when (this) {
    "mask-extents-negative",
    "mask-row-bytes-invalid",
    "mask-pixel-count-mismatch",
    "mask-sample-invalid",
    "mask-horizontal-bounds-overflow",
    "mask-vertical-bounds-overflow",
    "mask-empty-extents-inconsistent",
    "empty-mask-pixels-not-empty",
    -> GPUTextRefusalCodes.MASK_GENERATION_FAILED
    else -> GPUTextRefusalCodes.ARTIFACT_KEY_NONDETERMINISTIC
}

private fun prepareMask(
    artifact: PreparedTextGlyphArtifact.A8,
    blur: GlyphMaskBlurKey?,
    limits: PreparedTextFrameInventoryLimits,
    rawContentByMaskKey: MutableMap<GlyphMaskKey, String>,
    rawFingerprintByMaskIdentity: IdentityHashMap<A8GlyphMask, String>,
    preparedMaskCache: MutableMap<PreparedMaskCacheKey, PreparedMask>,
    uniqueMasks: MutableMap<GlyphMaskKey, PreparedMask>,
    observer: PreparedTextFrameInventoryObserver,
): PreparedMaskResolution {
    val rawFingerprint = rawFingerprintByMaskIdentity[artifact.mask] ?: run {
        observer.onMaskFingerprintComputed()
        artifact.mask.contentFingerprint().also { fingerprint ->
            rawFingerprintByMaskIdentity[artifact.mask] = fingerprint
        }
    }
    val previousRaw = rawContentByMaskKey.putIfAbsent(artifact.maskKey, rawFingerprint)
    if (previousRaw != null && previousRaw != rawFingerprint) {
        return PreparedMaskResolution.Refused(
            GPUTextRefusalCodes.ARTIFACT_KEY_NONDETERMINISTIC,
            "mask-key-content-mismatch",
        )
    }
    val exactBlur = blur?.copy(
        rasterScaleX = artifact.maskKey.strikeKey.scaleX,
        rasterScaleY = artifact.maskKey.strikeKey.scaleY,
    )
    val cacheKey = PreparedMaskCacheKey(
        maskKey = artifact.maskKey,
        rawContentFingerprint = rawFingerprint,
        blur = exactBlur,
    )
    val prepared = preparedMaskCache[cacheKey] ?: try {
        artifact.prepareForPaint(exactBlur, limits, observer).also { value ->
            preparedMaskCache[cacheKey] = value
        }
    } catch (failure: PreparedTextMaskPreparationException) {
        return PreparedMaskResolution.Refused(failure.code, failure.reason)
    } catch (_: Exception) {
        return PreparedMaskResolution.Refused(
            GPUTextRefusalCodes.MASK_GENERATION_FAILED,
            "mask-preparation-exception",
        )
    }
    val previousPrepared = uniqueMasks.putIfAbsent(prepared.maskKey, prepared)
    if (
        previousPrepared != null &&
        previousPrepared.contentFingerprint != prepared.contentFingerprint
    ) {
        return PreparedMaskResolution.Refused(
            GPUTextRefusalCodes.ARTIFACT_KEY_NONDETERMINISTIC,
            "mask-key-content-mismatch",
        )
    }
    return PreparedMaskResolution.Ready(prepared)
}

private fun GPUPreparedTextDraw.inventoryFacts(
    observer: PreparedTextFrameInventoryObserver,
): PreparedTextDrawFacts {
    observer.onDrawFactsComputed()
    val paintSnapshot = paint
    val blur = (paintSnapshot.maskFilter as? MaskFilter.Blur)
        ?.takeUnless { filter -> filter.sigma == 0f }
        ?.let { filter ->
        GlyphMaskBlurKey(
            style = when (filter.style) {
                BlurStyle.NORMAL -> GlyphMaskBlurStyle.NORMAL
                BlurStyle.SOLID -> GlyphMaskBlurStyle.SOLID
                BlurStyle.OUTER -> GlyphMaskBlurStyle.OUTER
                BlurStyle.INNER -> GlyphMaskBlurStyle.INNER
            },
            sigma = filter.sigma,
            rasterScaleX = glyphs.firstOrNull()?.strikeKey?.scaleX ?: 1f,
            rasterScaleY = glyphs.firstOrNull()?.strikeKey?.scaleY ?: 1f,
        )
    }
    return PreparedTextDrawFacts(
        blur = blur,
        materialKey = material.materialKey,
        blendPlanIdentity = blendPlan.canonicalIdentity(),
        clipIdentity = clipContentKey,
        transformClass = transformClass(transform),
    )
}

private fun resolvePreparedTextStrokePaths(
    draw: GPUPreparedTextDraw,
): PreparedTextStrokePathResolution {
    val typeface = when (val resolution = reconstructExactTypeface(draw)) {
        is ExactTypefaceResolution.Ready -> resolution.typeface
        is ExactTypefaceResolution.Refused -> return PreparedTextStrokePathResolution.Refused(
            code = resolution.artifact.code,
            facts = resolution.artifact.facts,
        )
    }
    val paths = ArrayList<GPUPreparedTextStrokePath>(draw.glyphs.size)
    draw.glyphs.forEachIndexed { glyphIndex, glyph ->
        val outline = typeface.preparedTextOutline(
            glyphId = glyph.glyphId,
            fontSize = glyph.fontSize,
            variationCoordinates = glyph.strikeKey.variationCoordinates,
        )
        when (outline) {
            PreparedTextOutline.ProvenEmpty -> Unit
            PreparedTextOutline.Unavailable -> return PreparedTextStrokePathResolution.Refused(
                code = GPUTextRefusalCodes.RASTERIZATION_FAILED,
                facts = mapOf(
                    "reason" to "exact-outline-unavailable",
                    "glyphIndex" to glyphIndex.toString(),
                    "glyphId" to glyph.glyphId.toString(),
                    "publishedStrokePathCount" to "0",
                ),
            )
            is PreparedTextOutline.ProvenNonEmpty -> {
                val effectiveX = draw.originX + glyph.positionX
                val effectiveY = draw.originY + glyph.positionY
                val positioned = outline.path.transform(effectiveX, effectiveY, 1f, 1f)
                paths += GPUPreparedTextStrokePath.create(
                    operationIndex = draw.operationIndex,
                    glyphIndex = glyphIndex,
                    draw = draw,
                    path = positioned,
                )
            }
        }
    }
    return PreparedTextStrokePathResolution.Ready(
        Collections.unmodifiableList(paths),
    )
}

private class PreparedTextMaskPreparationException(
    val code: String,
    val reason: String,
) : RuntimeException(reason)

private fun PreparedTextGlyphArtifact.A8.prepareForPaint(
    blurPrototype: GlyphMaskBlurKey?,
    limits: PreparedTextFrameInventoryLimits,
    observer: PreparedTextFrameInventoryObserver,
): PreparedMask {
    val blur = blurPrototype?.copy(
        rasterScaleX = maskKey.strikeKey.scaleX,
        rasterScaleY = maskKey.strikeKey.scaleY,
    )
    if (blur == null) {
        return PreparedMask(
            mask = mask,
            maskKey = maskKey,
            blurPaddingPx = 0,
            contentFingerprint = mask.contentFingerprint(),
        )
    }
    val effectiveSigma = blur.sigma.toDouble() * maxOf(
        kotlin.math.abs(blur.rasterScaleX.toDouble()),
        kotlin.math.abs(blur.rasterScaleY.toDouble()),
    )
    val paddingValue = ceil(3.0 * effectiveSigma)
    if (!paddingValue.isFinite() || paddingValue > Int.MAX_VALUE.toDouble()) {
        throw PreparedTextMaskPreparationException(
            GPUTextRefusalCodes.ATLAS_PAGE_BUDGET_EXCEEDED,
            "blur-padding-exceeds-int-range",
        )
    }
    val padding = paddingValue.toLong()
    val outputWidth = mask.width.toLong() + padding * 2L
    val outputHeight = mask.height.toLong() + padding * 2L
    val outputLeft = mask.left.toLong() - padding
    val outputTop = mask.top.toLong() - padding
    val outputRight = outputLeft + outputWidth
    val outputBottom = outputTop + outputHeight
    val allocationWidth = outputWidth + 2L
    val allocationHeight = outputHeight + 2L
    if (
        outputLeft !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ||
        outputTop !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
    ) {
        throw PreparedTextMaskPreparationException(
            GPUTextRefusalCodes.MASK_GENERATION_FAILED,
            "blur-bearing-overflow",
        )
    }
    if (
        outputRight !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ||
        outputBottom !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
    ) {
        throw PreparedTextMaskPreparationException(
            GPUTextRefusalCodes.MASK_GENERATION_FAILED,
            "blur-bounds-overflow",
        )
    }
    if (
        outputWidth !in 1..Int.MAX_VALUE.toLong() ||
        outputHeight !in 1..Int.MAX_VALUE.toLong() ||
        allocationWidth > limits.pageWidth.toLong() ||
        allocationHeight > limits.pageHeight.toLong()
    ) {
        throw PreparedTextMaskPreparationException(
            GPUTextRefusalCodes.ATLAS_PAGE_BUDGET_EXCEEDED,
            "blurred-mask-does-not-fit-page",
        )
    }
    observer.onBlurConvolution()
    val blurred = blurGlyphMask(mask, blur)
    return PreparedMask(
        mask = blurred.mask,
        maskKey = maskKey.copy(blur = blur),
        blurPaddingPx = blurred.paddingPx,
        contentFingerprint = blurred.mask.contentFingerprint(),
    )
}

private fun A8GlyphMask.contentFingerprint(): String = sha256String(
    buildString {
        append("a8-mask:v1")
        append('|').append(glyphId)
        append('|').append(width)
        append('|').append(height)
        append('|').append(left)
        append('|').append(top)
        append('|').append(rowBytes)
        append('|').append(sourceOutlineSha256)
        pixels.forEach { sample -> append('|').append(sample) }
    },
)

private fun firstNonEmptyUseBudgetRefusal(
    limits: PreparedTextFrameInventoryLimits,
    operationIndex: Int,
): PreparedTextFrameInventoryResult.Refused? {
    // Once O(1) structure proves that one drawable use exists, every impossible
    // minimum budget is terminal before sample validation or other O(pixels) work.
    val pageBytes = limits.pageWidth.toLong() * limits.pageHeight.toLong()
    return when {
        limits.maxInstances == 0 -> refused(
            GPUTextRefusalCodes.INSTANCE_BUFFER_BUDGET_EXCEEDED,
            operationIndex,
            mapOf("instanceCount" to "1"),
        )
        limits.maxInstanceBytes < GPUTextA8Instance.ENCODED_BYTE_SIZE -> refused(
            GPUTextRefusalCodes.INSTANCE_BYTES_EXCEEDED,
            operationIndex,
            mapOf("instanceBytes" to GPUTextA8Instance.ENCODED_BYTE_SIZE.toString()),
        )
        limits.maxPages == 0 -> refused(
            GPUTextRefusalCodes.ATLAS_PAGE_BUDGET_EXCEEDED,
            operationIndex,
            mapOf("reason" to GPUTextAtlasPackingRefusal.PAGE_LIMIT.name),
        )
        pageBytes > limits.maxPageBytes.toLong() -> refused(
            GPUTextRefusalCodes.ATLAS_PAGE_BYTES_EXCEEDED,
            operationIndex,
            mapOf("pageBytes" to pageBytes.toString()),
        )
        pageBytes > limits.maxTotalPageBytes.toLong() -> refused(
            GPUTextRefusalCodes.ATLAS_TOTAL_BYTES_EXCEEDED,
            operationIndex,
            mapOf("totalPageBytes" to pageBytes.toString()),
        )
        limits.maxSubRuns == 0 -> refused(
            GPUTextRefusalCodes.SUBRUN_BUDGET_EXCEEDED,
            operationIndex,
            mapOf("subRunCount" to "1"),
        )
        else -> null
    }
}

private fun copyMaskToPage(
    mask: A8GlyphMask,
    placement: GPUTextA8AtlasPlacement,
    pageRowBytes: Int,
    pageBytes: MutableList<Int>,
) {
    require(placement.contentRect.right - placement.contentRect.left == mask.width)
    require(placement.contentRect.bottom - placement.contentRect.top == mask.height)
    for (row in 0 until mask.height) {
        for (column in 0 until mask.width) {
            pageBytes[
                (placement.contentRect.top + row) * pageRowBytes +
                    placement.contentRect.left + column
            ] = mask.pixels[row * mask.rowBytes + column]
        }
    }
}

private fun deviceQuad(
    draw: GPUPreparedTextDraw,
    glyphIndex: Int,
    mask: A8GlyphMask,
): List<Float> {
    val glyph = draw.glyphs[glyphIndex]
    val scaleX = glyph.strikeKey.scaleX
    val scaleY = glyph.strikeKey.scaleY
    require(scaleX.isFinite() && scaleX > 0f && scaleY.isFinite() && scaleY > 0f)
    val transform = draw.transform
    val residual00 = transform.scaleX / scaleX
    val residual01 = transform.skewX / scaleY
    val residual10 = transform.skewY / scaleX
    val residual11 = transform.scaleY / scaleY
    val effectiveX = draw.originX + glyph.positionX
    val effectiveY = draw.originY + glyph.positionY
    val anchorX =
        transform.scaleX * effectiveX + transform.skewX * effectiveY + transform.transX
    val anchorY =
        transform.skewY * effectiveX + transform.scaleY * effectiveY + transform.transY
    val phaseX = glyph.strikeKey.subpixelX
    val phaseY = glyph.strikeKey.subpixelY
    fun map(qx: Float, qy: Float): Pair<Float, Float> {
        val localX = qx - phaseX
        val localY = qy - phaseY
        return (
            anchorX + residual00 * localX + residual01 * localY
            ) to (
            anchorY + residual10 * localX + residual11 * localY
            )
    }
    val left = mask.left.toFloat()
    val top = mask.top.toFloat()
    val right = (mask.left + mask.width).toFloat()
    val bottom = (mask.top + mask.height).toFloat()
    return listOf(
        map(left, top),
        map(right, top),
        map(right, bottom),
        map(left, bottom),
    ).flatMap { (x, y) -> listOf(x, y) }
}

private fun transformClass(transform: Matrix33): String = when {
    transform.skewX != 0f || transform.skewY != 0f -> "affine"
    transform.scaleX != 1f || transform.scaleY != 1f -> "scale"
    transform.transX != 0f || transform.transY != 0f -> "translate"
    else -> "identity"
}

private fun inventoryHash(
    generation: GPUTextArtifactGeneration,
    pages: List<GPUTextA8AtlasPageArtifact>,
    subRuns: Map<Int, List<GPUPreparedTextSubRun>>,
    strokePaths: Map<Int, List<GPUPreparedTextStrokePath>>,
    acceptedTextOperationIndices: Set<Int>,
    metrics: GPUPreparedTextFrameMetrics,
    identities: List<PreparedTextMaskIdentity>,
): String = sha256String(
    buildString {
        append("prepared-text-frame-inventory:v1|generation=").append(generation.value)
        append("|metrics=").append(metrics)
        acceptedTextOperationIndices.forEach { operationIndex ->
            append("|accepted-operation:").append(operationIndex)
        }
        pages.forEach { page ->
            append("|page:").append(page.pageIndex)
            append(':').append(page.artifactKey.contentFingerprint)
            page.placements.forEach { placement ->
                append(':').append(placement.itemKey)
                append(':').append(placement.allocationRect)
                append(':').append(placement.contentRect)
            }
        }
        subRuns.forEach { (operationIndex, operationSubRuns) ->
            append("|operation:").append(operationIndex)
            operationSubRuns.forEach { subRun ->
                append("|subrun:").append(subRun.subRunIndex)
                append(':').append(subRun.representation.name)
                append(':').append(subRun.pageIndex)
                append(':').append(subRun.materialKey)
                append(':').append(subRun.blendPlanIdentity)
                append(':').append(subRun.clipIdentity)
                append(':').append(subRun.transformClass)
                append(':').append(subRun.colorGlyphLayerPlan?.artifactKey?.contentFingerprint)
                subRun.instances.forEach { instance ->
                    append("|instance:").append(instance.glyphId).append(':').append(instance.pageIndex)
                    append(':').append(instance.colorLayerIndex)
                    append(':').append(instance.sourceGlyphIndex.value)
                    instance.deviceQuad.forEach { value -> append(':').append(value.toRawBits()) }
                    listOf(
                        instance.uvRect.left,
                        instance.uvRect.top,
                        instance.uvRect.right,
                        instance.uvRect.bottom,
                    ).forEach { value -> append(':').append(value.toRawBits()) }
                }
            }
        }
        strokePaths.forEach { (operationIndex, operationPaths) ->
            append("|stroke-operation:").append(operationIndex)
            operationPaths.forEach { strokePath ->
                append("|stroke-path:").append(strokePath.glyphIndex)
                val path = strokePath.path
                path.verbs().forEach { verb -> append(':').append(verb.name) }
                path.points().forEach { point ->
                    append(':').append(point.x.toRawBits())
                    append(':').append(point.y.toRawBits())
                }
                val paint = strokePath.draw.paint
                append(":width=").append(paint.strokeWidth.toRawBits())
                append(":cap=").append(paint.strokeCap.name)
                append(":join=").append(paint.strokeJoin.name)
                append(":miter=").append(paint.strokeMiter.toRawBits())
                (paint.pathEffect as? org.graphiks.kanvas.paint.PathEffect.Dash)?.let { dash ->
                    append(":dashPhase=").append(dash.phase.toRawBits())
                    dash.intervals.forEach { interval ->
                        append(':').append(interval.toRawBits())
                    }
                }
            }
        }
        identities.forEach { identity ->
            append("|mask:").append(identity.operationIndex)
            append(':').append(identity.glyphIndex)
            append(':').append(identity.layerIndex)
            append(':').append(identity.maskKeySha256)
        }
    },
)

private fun refused(
    code: String,
    operationIndex: Int?,
    facts: Map<String, String>,
): PreparedTextFrameInventoryResult.Refused =
    PreparedTextFrameInventoryResult.Refused(
        code = code,
        operationIndex = operationIndex,
        facts = linkedMapOf(
            "publishedPageCount" to "0",
            "publishedInstanceCount" to "0",
            "publishedSubRunCount" to "0",
        ) + facts,
    )

private fun resolveA8Artifact(
    draw: GPUPreparedTextDraw,
    glyphIndex: Int,
    typeface: FontTypeface,
    glyphId: Int,
    strikeKey: org.graphiks.kanvas.glyph.GlyphStrikeKey,
): PreparedTextGlyphArtifact {
    val outline = when (
        val proof = typeface.preparedTextOutline(
            glyphId = glyphId,
            fontSize = draw.glyphs[glyphIndex].fontSize,
            variationCoordinates = strikeKey.variationCoordinates,
        )
    ) {
        PreparedTextOutline.ProvenEmpty -> return PreparedTextGlyphArtifact.Empty
        PreparedTextOutline.Unavailable -> return artifactRefused(
            GPUTextRefusalCodes.RASTERIZATION_FAILED,
            "exact-outline-unavailable",
        )
        is PreparedTextOutline.ProvenNonEmpty -> proof.path.toOutlineRepresentation(glyphId)
    }
    val mask = object : GlyphMaskGenerator {}.generate(outline, strikeKey)
    if (mask.width == 0 || mask.height == 0 || mask.diagnostics.isNotEmpty()) {
        return artifactRefused(
            GPUTextRefusalCodes.MASK_GENERATION_FAILED,
            "non-empty-outline-produced-no-mask",
        )
    }
    val sourceHash = mask.sourceOutlineSha256 ?: return artifactRefused(
        GPUTextRefusalCodes.ARTIFACT_KEY_NONDETERMINISTIC,
        "source-outline-hash-missing",
    )
    return PreparedTextGlyphArtifact.A8(
        mask = mask,
        maskKey = GlyphMaskKey(
            strikeKey = strikeKey,
            faceIndex = draw.face.faceIndex,
            sourceOutlineSha256 = sourceHash,
        ),
    )
}

private fun resolveCOLRV0Artifact(
    draw: GPUPreparedTextDraw,
    glyphIndex: Int,
    typeface: FontTypeface,
    colorContext: ExactColorContextResolution?,
    layerResolver:
        ((Int, org.graphiks.kanvas.glyph.GlyphStrikeKey) -> PreparedTextGlyphArtifact)?,
): PreparedTextGlyphArtifact {
    val glyph = draw.glyphs[glyphIndex]
    val contextResolution = colorContext ?: parseExactColorContext(draw, typeface)
    val context = when (contextResolution) {
        is ExactColorContextResolution.Ready -> contextResolution.context
        is ExactColorContextResolution.Refused -> return contextResolution.artifact
    }
    val colr = context.colr
    val cpal = context.cpal
    val layerRecords = colr.layersForGlyph(glyph.glyphId)
    if (layerRecords.isEmpty()) {
        return artifactRefused(GPUTextRefusalCodes.COLOR_PLAN_UNSUPPORTED, "colrv0-layers-missing")
    }
    val layerArtifacts = ArrayList<PreparedTextColorLayerArtifact>(layerRecords.size)
    val bounds = LinkedHashMap<Int, ColorGlyphBounds>()
    for ((layerIndex, layer) in layerRecords.withIndex()) {
        val layerStrike = glyph.strikeKey.copy(glyphId = layer.glyphId)
        val artifact = layerResolver?.invoke(layer.glyphId, layerStrike) ?: resolveA8Artifact(
            draw = draw,
            glyphIndex = glyphIndex,
            typeface = typeface,
            glyphId = layer.glyphId,
            strikeKey = layerStrike,
        )
        val a8 = artifact as? PreparedTextGlyphArtifact.A8 ?: when (artifact) {
            PreparedTextGlyphArtifact.Empty -> {
                layerArtifacts += PreparedTextColorLayerArtifact.Empty(
                    layerIndex = layerIndex,
                    glyphId = layer.glyphId,
                )
                continue
            }
            is PreparedTextGlyphArtifact.Refused -> artifact
            else -> artifactRefused(GPUTextRefusalCodes.COLOR_PLAN_UNSUPPORTED, "colrv0-layer-invalid")
        }.let { return it }
        layerArtifacts += PreparedTextColorLayerArtifact.A8(
            layerIndex = layerIndex,
            mask = a8.mask,
            maskKey = a8.maskKey,
        )
        bounds[layer.glyphId] = ColorGlyphBounds(
            xMin = a8.mask.left,
            yMin = a8.mask.top,
            xMax = a8.mask.left + a8.mask.width,
            yMax = a8.mask.top + a8.mask.height,
        )
    }
    val representativeBounds = bounds.values.firstOrNull()
        ?: return PreparedTextGlyphArtifact.Empty
    layerArtifacts
        .filterIsInstance<PreparedTextColorLayerArtifact.Empty>()
        .forEach { emptyLayer ->
            bounds.putIfAbsent(emptyLayer.glyphId, representativeBounds)
        }
    val paletteIndex = glyph.strikeKey.paletteIdentity
        ?.removePrefix("cpal:")
        ?.toIntOrNull()
        ?: 0
    val decision = COLRV0ColorGlyphPlanner(
        colr = colr,
        cpal = cpal,
        layerBounds = bounds,
    ).plan(
        glyphId = glyph.glyphId,
        typefaceId = draw.face.typefaceId,
        strikeKey = glyph.strikeKey,
        paletteSelection = CPALPaletteSelection(index = paletteIndex),
    )
    val plan = decision.plan ?: return artifactRefused(
        GPUTextRefusalCodes.COLOR_PLAN_UNSUPPORTED,
        decision.diagnostics.firstOrNull()?.detail ?: "colrv0-plan-refused",
    )
    return PreparedTextGlyphArtifact.COLRV0(
        layers = layerArtifacts,
        colorPlan = plan,
    )
}

private fun ColorGlyphPlan.immutableSnapshot(): ColorGlyphPlan = copy(
    layers = Collections.unmodifiableList(ArrayList(layers)),
    paintGraph = paintGraph?.immutableSnapshot(),
    diagnostics = Collections.unmodifiableList(ArrayList(diagnostics)),
)

private fun COLRV1PaintGraphEvidence.immutableSnapshot(): COLRV1PaintGraphEvidence = copy(
    nodes = Collections.unmodifiableList(
        nodes.map { node -> node.immutableSnapshot() },
    ),
    diagnostics = Collections.unmodifiableList(ArrayList(diagnostics)),
)

private fun COLRV1PaintGraphNode.immutableSnapshot(): COLRV1PaintGraphNode = copy(
    childNodeIds = Collections.unmodifiableList(ArrayList(childNodeIds)),
    gradient = gradient?.immutableSnapshot(),
)

private fun COLRV1GradientEvidence.immutableSnapshot(): COLRV1GradientEvidence = copy(
    stops = Collections.unmodifiableList(ArrayList(stops)),
    variationCoordinates = Collections.unmodifiableMap(LinkedHashMap(variationCoordinates)),
)

private fun parseExactColorContext(
    draw: GPUPreparedTextDraw,
    typeface: FontTypeface,
): ExactColorContextResolution {
    val source = FontSource(
        id = draw.face.sourceId,
        kind = FontSourceKind.MEMORY,
        displayName = typeface.fontName,
        bytes = draw.face.bytes.map(Int::toByte).toByteArray(),
    )
    val parsed = runCatching {
        DefaultOpenTypeFaceParser().parse(source, draw.face.faceIndex)
    }.getOrElse {
        return ExactColorContextResolution.Refused(
            artifactRefused(GPUTextRefusalCodes.FONT_BYTES_MALFORMED, "colrv0-face-parse"),
        )
    }
    val colrBytes = parsed.rawTables.entries
        .firstOrNull { (tag, _) -> tag.value == "COLR" }
        ?.value
        ?.map(Int::toByte)
        ?.toByteArray()
        ?: return ExactColorContextResolution.Refused(
            artifactRefused(GPUTextRefusalCodes.COLOR_PLAN_UNSUPPORTED, "colr-table-missing"),
        )
    val cpalBytes = parsed.rawTables.entries
        .firstOrNull { (tag, _) -> tag.value == "CPAL" }
        ?.value
        ?.map(Int::toByte)
        ?.toByteArray()
        ?: return ExactColorContextResolution.Refused(
            artifactRefused(GPUTextRefusalCodes.COLOR_PLAN_UNSUPPORTED, "cpal-table-missing"),
        )
    if (colrBytes.size < 2) {
        return ExactColorContextResolution.Refused(
            artifactRefused(GPUTextRefusalCodes.FONT_BYTES_MALFORMED, "colr-version-truncated"),
        )
    }
    val version = ((colrBytes[0].toInt() and 0xff) shl 8) or (colrBytes[1].toInt() and 0xff)
    val colr = when (version) {
        0 -> COLRV0Parser.parse(colrBytes)
        1 -> COLRV0Parser.parseRetainedV0Records(colrBytes)
        else -> null
    } ?: return ExactColorContextResolution.Refused(
        artifactRefused(GPUTextRefusalCodes.COLOR_PLAN_UNSUPPORTED, "colrv0-parse"),
    )
    val cpal = parsePreparedTextCompatibleCPAL(cpalBytes)
        ?.let { neutral ->
            CPALTable(
                numPaletteEntries = neutral.palettes.firstOrNull()?.size ?: 0,
                numColorRecords = neutral.palettes.sumOf { palette -> palette.size },
                palettes = neutral.palettes.mapIndexed { index, colors ->
                    CPALPalette(index = index, colors = colors)
                },
            )
        }
        ?: return ExactColorContextResolution.Refused(
            artifactRefused(GPUTextRefusalCodes.COLOR_PLAN_UNSUPPORTED, "cpal-parse"),
        )
    return ExactColorContextResolution.Ready(
        ExactColorContext(
            colr = colr,
            cpal = cpal,
        ),
    )
}

private fun Path.toOutlineRepresentation(glyphId: Int): OutlineGlyphRepresentation {
    require(fillType == FillType.WINDING) {
        "Prepared A8 glyph outlines require non-zero winding."
    }
    val commands = ArrayList<String>()
    val pathPoints = points()
    var pointIndex = 0
    verbs().forEach { verb ->
        fun point(): org.graphiks.kanvas.types.Point = pathPoints[pointIndex++]
        commands += when (verb) {
            PathVerb.MOVE -> point().let { "M ${it.x} ${it.y}" }
            PathVerb.LINE -> point().let { "L ${it.x} ${it.y}" }
            PathVerb.QUAD -> {
                val control = point()
                val end = point()
                "Q ${control.x} ${control.y} ${end.x} ${end.y}"
            }
            PathVerb.CUBIC -> {
                val first = point()
                val second = point()
                val end = point()
                "C ${first.x} ${first.y} ${second.x} ${second.y} ${end.x} ${end.y}"
            }
            PathVerb.ARC_TO -> error("Exact font outlines must not contain arc verbs.")
            PathVerb.CLOSE -> "Z"
        }
    }
    return OutlineGlyphRepresentation(
        glyphId = glyphId,
        pathCommands = Collections.unmodifiableList(commands),
        windingRule = "nonZero",
    )
}

private fun artifactRefused(
    code: String,
    reason: String,
): PreparedTextGlyphArtifact.Refused = PreparedTextGlyphArtifact.Refused(
    code = code,
    facts = Collections.unmodifiableMap(mapOf("reason" to reason)),
)

private fun GPUColorGlyphLayerPlan.immutableSnapshot(): GPUColorGlyphLayerPlan =
    GPUColorGlyphLayerPlan(
        artifactKey = artifactKey,
        baseGlyphID = baseGlyphID,
        layers = Collections.unmodifiableList(ArrayList(layers)),
    )

private fun sha256String(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte ->
            String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
        }

private fun sha256UnsignedBytes(values: List<Int>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    values.forEach { value ->
        require(value in 0..255) { "Prepared font bytes must be unsigned." }
        digest.update(value.toByte())
    }
    return digest.digest().joinToString(separator = "") { byte ->
        String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
    }
}

private fun Path.preparedTextPathSnapshot(): Path = Path().also { snapshot ->
    snapshot.fillType = fillType
    snapshot.addPath(this)
}

private val A8_PAGE_ARTIFACT_ID =
    GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440510"))
private val COLOR_GLYPH_ARTIFACT_ID =
    GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440511"))
