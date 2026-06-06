package org.skia.core

import org.skia.foundation.SkAAClip
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkClipOp
import org.graphiks.math.SkColor
import org.graphiks.math.SkColor4f
import org.skia.foundation.SkColorFilter
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkCubicBC
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkShader
import org.skia.foundation.SkStroker
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.asBlendModeFilter
import org.graphiks.math.SkIRect
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.pipeline.ClipInteraction
import org.skia.pipeline.CoverageLoweringResult
import org.skia.pipeline.CoverageModel
import org.skia.pipeline.CoveragePlan
import org.skia.pipeline.CoveragePlanAdapter
import org.skia.pipeline.CpuAnalyticRectCoverageExecutor
import org.skia.pipeline.FloatRect
import org.skia.pipeline.GeometryBounds
import org.skia.pipeline.GeometryPlan
import org.skia.pipeline.GeometryPrimitive
import org.skia.pipeline.IntRect
import org.skia.pipeline.MatrixSpec
import org.skia.pipeline.BackendKind
import org.skia.pipeline.ProductionRouteDiagnostics
import org.skia.pipeline.TransformFacts
import kotlin.math.ceil as kCeil
import kotlin.math.floor as kFloor

private fun floor(v: Float): Int = kFloor(v.toDouble()).toInt()
private fun ceil(v: Float): Int = kCeil(v.toDouble()).toInt()

private fun strictSampleMin(edge: Float, max: Int): Int =
    ceil(edge - 0.5f).coerceIn(0, max)

private fun strictSampleMax(edge: Float, min: Int, max: Int): Int =
    floor(edge - 0.5f).coerceIn(min, max)

/** Chord-error tolerance (in device-space pixels) for Bézier flattening. */
private const val PATH_FLATNESS: Float = 0.25f
/** Squared tolerance — used to compare against `cross² / chord²` without `sqrt`. */
private const val PATH_FLATNESS_SQ: Float = PATH_FLATNESS * PATH_FLATNESS
/** Recursion depth bound for adaptive subdivision (2^18 safety net). */
private const val PATH_MAX_DEPTH: Int = 18
/** Number of uniform-`t` segments for conic flattening. */
private const val CONIC_STEPS: Int = 32
private const val DESCRIPTOR_RECT_FLAG: String = "kanvas.cpu.descriptorRect.enabled"
private const val CPU_DESCRIPTOR_RECT_DISABLED_REASON: String = "coverage.cpu-descriptor-rect-disabled"
private const val CPU_DESCRIPTOR_FILL_STYLE_REASON: String = "coverage.cpu-descriptor-fill-style-only"
private const val CPU_DESCRIPTOR_SHADER_REASON: String = "coverage.cpu-descriptor-shader-unsupported"
private const val CPU_DESCRIPTOR_COLOR_FILTER_REASON: String = "coverage.cpu-descriptor-color-filter-unsupported"
private const val CPU_DESCRIPTOR_MASK_FILTER_REASON: String = "coverage.cpu-descriptor-mask-filter-unsupported"
private const val CPU_DESCRIPTOR_IMAGE_FILTER_REASON: String = "coverage.cpu-descriptor-image-filter-unsupported"
private const val CPU_DESCRIPTOR_PATH_EFFECT_REASON: String = "coverage.cpu-descriptor-path-effect-unsupported"
private const val CPU_DESCRIPTOR_BLENDER_REASON: String = "coverage.cpu-descriptor-blender-unsupported"
private const val CPU_DESCRIPTOR_BLEND_MODE_REASON: String = "coverage.cpu-descriptor-blend-mode-unsupported"
private const val CPU_DESCRIPTOR_AA_CLIP_REASON: String = "coverage.cpu-descriptor-aa-clip-unsupported"
private const val CPU_DESCRIPTOR_CLIP_SHADER_REASON: String = "coverage.cpu-descriptor-clip-shader-unsupported"
private const val CPU_DESCRIPTOR_LOWERING_UNSUPPORTED_REASON: String = "coverage.cpu-descriptor-lowering-unsupported"

public object SkCpuWriteChronologyTrace {
    public data class Target(public val x: Int, public val y: Int)
    public data class Bounds(
        public val left: Int,
        public val top: Int,
        public val right: Int,
        public val bottom: Int,
    )

    public data class A8SrcInPayloadTrace(
        public val maskLocalX: Int,
        public val maskLocalY: Int,
        public val maskOriginLeft: Int,
        public val maskOriginTop: Int,
        public val maskWidth: Int,
        public val maskHeight: Int,
        public val compositeX0: Int,
        public val compositeY0: Int,
        public val compositeX1: Int,
        public val compositeY1: Int,
        public val blurredMaskAlpha: Int,
        public val maskedAlphaBeforeBlend: Int,
        public val a8SkipReason: String?,
        public val a8SpanLeft: Int,
        public val a8SpanRight: Int,
        public val activeClipBounds: Bounds,
        public val layerBounds: Bounds,
        public val sourceLayerBounds: Bounds,
    )

    public data class Event(
        public val index: Int,
        public val x: Int,
        public val y: Int,
        public val bitmapWidth: Int,
        public val bitmapHeight: Int,
        public val deviceKind: String,
        public val rootDevice: Boolean,
        public val source: String,
        public val callsite: String,
        public val branch: String,
        public val mode: String,
        public val blender: String,
        public val coverage: Int,
        public val srcInput: SkColor,
        public val srcAfterCoverage: SkColor,
        public val valueBefore: SkColor,
        public val valueWritten: SkColor,
        public val valueReadAfter: SkColor,
        public val coverageScale: Float? = null,
        public val coverageSamples: Int? = null,
        public val coverageMaxSamples: Int? = null,
        public val paintColor4f: List<Float>? = null,
        public val srcPremulBeforeCoverageF16: List<Float>? = null,
        public val srcPremulAfterCoverageF16: List<Float>? = null,
        public val dstPremulBeforeStoreF16: List<Float>? = null,
        public val dstPremulAfterStoreF16: List<Float>? = null,
        public val maskLocalX: Int? = null,
        public val maskLocalY: Int? = null,
        public val maskOriginLeft: Int? = null,
        public val maskOriginTop: Int? = null,
        public val maskWidth: Int? = null,
        public val maskHeight: Int? = null,
        public val compositeX0: Int? = null,
        public val compositeY0: Int? = null,
        public val compositeX1: Int? = null,
        public val compositeY1: Int? = null,
        public val blurredMaskAlpha: Int? = null,
        public val maskedAlphaBeforeBlend: Int? = null,
        public val a8SkipReason: String? = null,
        public val a8SpanLeft: Int? = null,
        public val a8SpanRight: Int? = null,
        public val activeClipBounds: Bounds? = null,
        public val layerBounds: Bounds? = null,
        public val sourceLayerBounds: Bounds? = null,
    )

    private val lock = Any()
    @Volatile
    private var enabled: Boolean = false
    private var targetPixels: Set<Target> = emptySet()
    private var targetWidth: Int? = null
    private var targetHeight: Int? = null
    private var includeBitmapDirectWrites: Boolean = false
    private val events = mutableListOf<Event>()
    private val bitmapWriteTraceSuppression = ThreadLocal.withInitial { 0 }

    public fun configureForTargets(
        targets: Set<Target>,
        width: Int? = null,
        height: Int? = null,
        includeBitmapDirectWrites: Boolean = false,
    ) {
        synchronized(lock) {
            targetPixels = targets
            targetWidth = width
            targetHeight = height
            this.includeBitmapDirectWrites = includeBitmapDirectWrites
            events.clear()
            enabled = targets.isNotEmpty()
        }
    }

    public fun reset() {
        synchronized(lock) {
            enabled = false
            targetPixels = emptySet()
            targetWidth = null
            targetHeight = null
            includeBitmapDirectWrites = false
            events.clear()
        }
    }

    public fun snapshot(): List<Event> = synchronized(lock) { events.toList() }

    public fun configuredTargetCount(): Int = synchronized(lock) { targetPixels.size }

    internal fun shouldTrace(x: Int, y: Int, width: Int, height: Int): Boolean {
        if (!enabled) return false
        return synchronized(lock) {
            if (!enabled || Target(x, y) !in targetPixels) return false
            val expectedWidth = targetWidth
            val expectedHeight = targetHeight
            (expectedWidth == null || expectedWidth == width) &&
                (expectedHeight == null || expectedHeight == height)
        }
    }

    internal fun shouldTraceBitmapDirectWrite(x: Int, y: Int, width: Int, height: Int): Boolean {
        if (!enabled) return false
        if ((bitmapWriteTraceSuppression.get() ?: 0) > 0) return false
        return synchronized(lock) {
            if (!enabled || !includeBitmapDirectWrites || Target(x, y) !in targetPixels) return false
            val expectedWidth = targetWidth
            val expectedHeight = targetHeight
            (expectedWidth == null || expectedWidth == width) &&
                (expectedHeight == null || expectedHeight == height)
        }
    }

    internal fun bitmapDirectWriteTargets(width: Int, height: Int): List<Target> {
        if (!enabled) return emptyList()
        if ((bitmapWriteTraceSuppression.get() ?: 0) > 0) return emptyList()
        return synchronized(lock) {
            if (!enabled || !includeBitmapDirectWrites) return emptyList()
            val expectedWidth = targetWidth
            val expectedHeight = targetHeight
            if ((expectedWidth != null && expectedWidth != width) ||
                (expectedHeight != null && expectedHeight != height)
            ) {
                emptyList()
            } else {
                targetPixels
                    .filter { it.x in 0 until width && it.y in 0 until height }
                    .sortedWith(compareBy<Target> { it.y }.thenBy { it.x })
            }
        }
    }

    internal fun bitmapDirectWriteTracingActive(): Boolean {
        if (!enabled) return false
        return synchronized(lock) { enabled && includeBitmapDirectWrites }
    }

    internal inline fun <T> withBitmapDirectWriteTraceSuppressed(block: () -> T): T {
        val previous = bitmapWriteTraceSuppression.get() ?: 0
        bitmapWriteTraceSuppression.set(previous + 1)
        try {
            return block()
        } finally {
            if (previous == 0) {
                bitmapWriteTraceSuppression.remove()
            } else {
                bitmapWriteTraceSuppression.set(previous)
            }
        }
    }

    internal fun record(
        x: Int,
        y: Int,
        source: String,
        callsite: String,
        branch: String,
        mode: SkBlendMode,
        blender: org.skia.foundation.SkBlender?,
        coverage: Int,
        srcInput: SkColor,
        srcAfterCoverage: SkColor,
        valueBefore: SkColor,
        valueWritten: SkColor,
        valueReadAfter: SkColor,
        bitmapWidth: Int? = null,
        bitmapHeight: Int? = null,
        a8SrcInPayloadTrace: A8SrcInPayloadTrace? = null,
    ) {
        synchronized(lock) {
            if (!enabled || Target(x, y) !in targetPixels) return
            val eventBitmapWidth = bitmapWidth ?: targetWidth ?: -1
            val eventBitmapHeight = bitmapHeight ?: targetHeight ?: -1
            val root =
                (targetWidth == null || targetWidth == eventBitmapWidth) &&
                    (targetHeight == null || targetHeight == eventBitmapHeight)
            events += Event(
                index = events.size,
                x = x,
                y = y,
                bitmapWidth = eventBitmapWidth,
                bitmapHeight = eventBitmapHeight,
                deviceKind = if (root) "root" else "temporary",
                rootDevice = root,
                source = source,
                callsite = callsite,
                branch = branch,
                mode = mode.name,
                blender = blender?.javaClass?.simpleName ?: "null",
                coverage = coverage,
                srcInput = srcInput,
                srcAfterCoverage = srcAfterCoverage,
                valueBefore = valueBefore,
                valueWritten = valueWritten,
                valueReadAfter = valueReadAfter,
                maskLocalX = a8SrcInPayloadTrace?.maskLocalX,
                maskLocalY = a8SrcInPayloadTrace?.maskLocalY,
                maskOriginLeft = a8SrcInPayloadTrace?.maskOriginLeft,
                maskOriginTop = a8SrcInPayloadTrace?.maskOriginTop,
                maskWidth = a8SrcInPayloadTrace?.maskWidth,
                maskHeight = a8SrcInPayloadTrace?.maskHeight,
                compositeX0 = a8SrcInPayloadTrace?.compositeX0,
                compositeY0 = a8SrcInPayloadTrace?.compositeY0,
                compositeX1 = a8SrcInPayloadTrace?.compositeX1,
                compositeY1 = a8SrcInPayloadTrace?.compositeY1,
                blurredMaskAlpha = a8SrcInPayloadTrace?.blurredMaskAlpha,
                maskedAlphaBeforeBlend = a8SrcInPayloadTrace?.maskedAlphaBeforeBlend,
                a8SkipReason = a8SrcInPayloadTrace?.a8SkipReason,
                a8SpanLeft = a8SrcInPayloadTrace?.a8SpanLeft,
                a8SpanRight = a8SrcInPayloadTrace?.a8SpanRight,
                activeClipBounds = a8SrcInPayloadTrace?.activeClipBounds,
                layerBounds = a8SrcInPayloadTrace?.layerBounds,
                sourceLayerBounds = a8SrcInPayloadTrace?.sourceLayerBounds,
            )
        }
    }

    internal fun recordF16PremulStore(
        x: Int,
        y: Int,
        source: String,
        callsite: String,
        branch: String,
        mode: SkBlendMode,
        coverage: Int,
        coverageScale: Float?,
        coverageSamples: Int?,
        coverageMaxSamples: Int?,
        paintColor4f: SkColor4f?,
        srcPremulBeforeCoverageF16: FloatArray?,
        srcPremulAfterCoverageF16: FloatArray,
        dstPremulBeforeStoreF16: FloatArray,
        dstPremulAfterStoreF16: FloatArray,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ) {
        synchronized(lock) {
            if (!enabled || Target(x, y) !in targetPixels) return
            val root =
                (targetWidth == null || targetWidth == bitmapWidth) &&
                    (targetHeight == null || targetHeight == bitmapHeight)
            events += Event(
                index = events.size,
                x = x,
                y = y,
                bitmapWidth = bitmapWidth,
                bitmapHeight = bitmapHeight,
                deviceKind = if (root) "root" else "temporary",
                rootDevice = root,
                source = source,
                callsite = callsite,
                branch = branch,
                mode = mode.name,
                blender = "null",
                coverage = coverage,
                srcInput = premulF16ToSkColor(srcPremulBeforeCoverageF16 ?: srcPremulAfterCoverageF16),
                srcAfterCoverage = premulF16ToSkColor(srcPremulAfterCoverageF16),
                valueBefore = premulF16ToSkColor(dstPremulBeforeStoreF16),
                valueWritten = premulF16ToSkColor(dstPremulAfterStoreF16),
                valueReadAfter = premulF16ToSkColor(dstPremulAfterStoreF16),
                coverageScale = coverageScale,
                coverageSamples = coverageSamples,
                coverageMaxSamples = coverageMaxSamples,
                paintColor4f = paintColor4f?.let { listOf(it.fR, it.fG, it.fB, it.fA) },
                srcPremulBeforeCoverageF16 = srcPremulBeforeCoverageF16?.toTraceList(),
                srcPremulAfterCoverageF16 = srcPremulAfterCoverageF16.toTraceList(),
                dstPremulBeforeStoreF16 = dstPremulBeforeStoreF16.toTraceList(),
                dstPremulAfterStoreF16 = dstPremulAfterStoreF16.toTraceList(),
            )
        }
    }

    private fun FloatArray.toTraceList(): List<Float> =
        listOf(this[0], this[1], this[2], this[3])

    private fun premulF16ToSkColor(values: FloatArray): SkColor {
        val pa = values[3]
        val a = (pa * 256f).toInt().coerceIn(0, 255)
        if (a == 0 || pa <= 0f) return 0
        val invA = 1f / pa
        val r = (values[0] * invA * 256f).toInt().coerceIn(0, 255)
        val g = (values[1] * invA * 256f).toInt().coerceIn(0, 255)
        val b = (values[2] * invA * 256f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(a, r, g, b)
    }

    internal fun recordA8SrcInPayloadPreDispatch(
        x: Int,
        y: Int,
        mode: SkBlendMode,
        blender: org.skia.foundation.SkBlender?,
        valueBefore: SkColor,
        srcBeforeBlend: SkColor,
        bitmapWidth: Int,
        bitmapHeight: Int,
        trace: A8SrcInPayloadTrace,
    ) {
        record(
            x = x,
            y = y,
            source = "SkBitmapDevice.drawPathWithMaskFilter.A8.preDispatch",
            callsite = "SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload",
            branch = "SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload.preDispatch",
            mode = mode,
            blender = blender,
            coverage = 255,
            srcInput = srcBeforeBlend,
            srcAfterCoverage = srcBeforeBlend,
            valueBefore = valueBefore,
            valueWritten = valueBefore,
            valueReadAfter = valueBefore,
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
            a8SrcInPayloadTrace = trace,
        )
    }

    internal fun recordA8SrcInPayloadBlendSkip(
        x: Int,
        y: Int,
        mode: SkBlendMode,
        blender: org.skia.foundation.SkBlender?,
        coverage: Int,
        valueBefore: SkColor,
        srcInput: SkColor,
        srcAfterCoverage: SkColor,
        bitmapWidth: Int,
        bitmapHeight: Int,
        trace: A8SrcInPayloadTrace,
    ) {
        record(
            x = x,
            y = y,
            source = "SkBitmapDevice.drawPathWithMaskFilter.A8.blendSkip",
            callsite = "SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload",
            branch = "SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload.blendSkip",
            mode = mode,
            blender = blender,
            coverage = coverage,
            srcInput = srcInput,
            srcAfterCoverage = srcAfterCoverage,
            valueBefore = valueBefore,
            valueWritten = valueBefore,
            valueReadAfter = valueBefore,
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
            a8SrcInPayloadTrace = trace,
        )
    }

    internal fun recordBitmapDirectWrite(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        source: String,
        branch: String,
        valueBefore: SkColor,
        valueWritten: SkColor,
        valueReadAfter: SkColor,
    ) {
        synchronized(lock) {
            if (!enabled || !includeBitmapDirectWrites || Target(x, y) !in targetPixels) return
            val expectedWidth = targetWidth
            val expectedHeight = targetHeight
            val root =
                (expectedWidth == null || expectedWidth == width) &&
                    (expectedHeight == null || expectedHeight == height)
            events += Event(
                index = events.size,
                x = x,
                y = y,
                bitmapWidth = width,
                bitmapHeight = height,
                deviceKind = if (root) "root" else "temporary",
                rootDevice = root,
                source = source,
                callsite = source,
                branch = branch,
                mode = "direct",
                blender = "n/a",
                coverage = 255,
                srcInput = valueWritten,
                srcAfterCoverage = valueWritten,
                valueBefore = valueBefore,
                valueWritten = valueWritten,
                valueReadAfter = valueReadAfter,
            )
        }
    }
}

internal object SkBitmapDescriptorCoverageLowering {
    var lower: (CoveragePlan) -> CoverageLoweringResult = CoveragePlanAdapter::lower
}

public object SkScanFillPathSubsampleTrace {
    public data class Target(public val x: Int, public val y: Int)

    public data class PixelMask(
        public val x: Int,
        public val y: Int,
        public val supers: Int,
        public val mask4x4: Int,
        public val scanFillPathSamples: Int,
        public val tracedSpanCount: Int,
        public val spanQuantizationRows: List<SpanQuantizationRow>,
        public val source: String,
    )

    public data class SpanSampleCenter(
        public val sx: Int,
        public val deviceX: Float,
        public val covered: Boolean,
    )

    public data class SpanQuantizationRow(
        public val subRow: Int,
        public val spanLeft: Float,
        public val spanRight: Float,
        public val cellLeft: Float,
        public val cellRight: Float,
        public val intersectionLeft: Float,
        public val intersectionRight: Float,
        public val intersectionWidth: Float,
        public val widthTimesSupers: Float,
        public val roundedSamples: Int,
        public val centerCoveredCount: Int,
        public val roundedMinusCenter: Int,
        public val sampleCenters: List<SpanSampleCenter>,
    )

    private data class MutablePixelMask(
        val target: Target,
        val supers: Int,
        var mask: Int = 0,
        var scanFillPathSamples: Int = 0,
        var tracedSpanCount: Int = 0,
        val spanQuantizationRows: MutableList<SpanQuantizationRow> = mutableListOf(),
    )

    private data class Session(
        val targets: Set<Target>,
        val supers: Int,
        val pixels: MutableMap<Target, MutablePixelMask>,
    )

    private val active = ThreadLocal<Session?>()
    private val lastSnapshot = ThreadLocal<List<PixelMask>>()

    public fun configureForTargets(targets: Set<Target>, supers: Int = 4) {
        require(supers == 4) { "FOR-428 scanFillPath subsample trace only supports 4x4 samples" }
        val sortedTargets = targets.sortedWith(compareBy<Target> { it.y }.thenBy { it.x })
        active.set(
            Session(
                targets = sortedTargets.toSet(),
                supers = supers,
                pixels = sortedTargets.associateWith { target ->
                    MutablePixelMask(target = target, supers = supers)
                }.toMutableMap(),
            ),
        )
        lastSnapshot.remove()
    }

    public fun reset() {
        lastSnapshot.set(snapshot())
        active.remove()
    }

    public fun snapshot(): List<PixelMask> {
        val session = active.get()
        if (session == null) return lastSnapshot.get().orEmpty()
        return session.pixels.values
            .sortedWith(compareBy<MutablePixelMask> { it.target.y }.thenBy { it.target.x })
            .map { pixel ->
                PixelMask(
                    x = pixel.target.x,
                    y = pixel.target.y,
                    supers = pixel.supers,
                    mask4x4 = pixel.mask,
                    scanFillPathSamples = pixel.scanFillPathSamples,
                    tracedSpanCount = pixel.tracedSpanCount,
                    spanQuantizationRows = pixel.spanQuantizationRows.toList(),
                    source = "SkBitmapDevice.scanFillPath.addSpanCoverage",
                )
            }
    }

    internal fun recordSpanCoverage(
        pixelX: Int,
        pixelY: Int,
        subrowY: Int,
        supers: Int,
        spanLeft: Float,
        spanRight: Float,
        cellLeft: Float,
        cellRight: Float,
        intersectionLeft: Float,
        intersectionRight: Float,
        intersectionWidth: Float,
        widthTimesSupers: Float,
        samplesAddedByScanFillPath: Int,
    ) {
        val session = active.get() ?: return
        if (supers != session.supers || subrowY !in 0 until supers) return
        val target = Target(pixelX, pixelY)
        val pixel = session.pixels[target] ?: return
        pixel.scanFillPathSamples += samplesAddedByScanFillPath
        pixel.tracedSpanCount += 1
        val sampleCenters = mutableListOf<SpanSampleCenter>()
        var centerCoveredCount = 0
        for (sx in 0 until supers) {
            val sampleX = pixelX + (sx + 0.5f) / supers
            val covered = sampleX >= intersectionLeft && sampleX < intersectionRight
            if (covered) {
                pixel.mask = pixel.mask or (1 shl (subrowY * supers + sx))
                centerCoveredCount += 1
            }
            sampleCenters += SpanSampleCenter(
                sx = sx,
                deviceX = sampleX,
                covered = covered,
            )
        }
        pixel.spanQuantizationRows += SpanQuantizationRow(
            subRow = subrowY,
            spanLeft = spanLeft,
            spanRight = spanRight,
            cellLeft = cellLeft,
            cellRight = cellRight,
            intersectionLeft = intersectionLeft,
            intersectionRight = intersectionRight,
            intersectionWidth = intersectionWidth,
            widthTimesSupers = widthTimesSupers,
            roundedSamples = samplesAddedByScanFillPath,
            centerCoveredCount = centerCoveredCount,
            roundedMinusCenter = samplesAddedByScanFillPath - centerCoveredCount,
            sampleCenters = sampleCenters,
        )
    }
}

public data class SkBitmapDescriptorCoverageDiagnostics(
    val mode: String,
    val backend: BackendKind,
    val drawKind: String,
    val selectedRoute: String,
    val compatibilityFallbackRoute: String,
    val coveragePlan: String,
    val loweringResult: String,
    val executionEvidence: String,
    val fallbackReason: String?,
    val touchedPixels: Int,
) {
    public fun toProductionRouteDiagnostics(): ProductionRouteDiagnostics =
        ProductionRouteDiagnostics(
            mode = mode,
            backend = backend,
            drawKind = drawKind,
            selectedRoute = selectedRoute,
            fallbackRoute = compatibilityFallbackRoute,
            fallbackReason = fallbackReason,
            coveragePlan = coveragePlan,
            touchedPixels = touchedPixels,
            loweringResult = loweringResult,
            executionEvidence = executionEvidence,
        )

    public fun dump(): String = toProductionRouteDiagnostics().dump()
}

/**
 * Skia's non-AA rect rasterization rule: pixel N is covered iff
 * `rect.{l,t} - 0.5 < N ≤ rect.{r,b} - 0.5` (top-exclusive, bottom-inclusive),
 * equivalent to integer range `[floor(c + 0.5), floor(c + 0.5))` from
 * `SkScalarRoundToInt`. Half-integer ties round toward +∞.
 */
private fun pixelEdge(c: Float): Int = kFloor(c.toDouble() + 0.5).toInt()

/**
 * CPU raster device. Phase 1: non-AA rect fill and stroke. Phase 2: adds
 * analytic AA coverage for axis-aligned rects (`paint.isAntiAlias = true`).
 * Phase 3a: adds `drawPath` (line-only paths, fill style only) using a
 * scanline rasterizer with 4×4 supersampling for AA.
 *
 * Receives device-space coordinates from `SkCanvas`; the canvas owns the
 * matrix and clip stacks and is responsible for transforming and clipping
 * into the `clip` rect passed here. `drawPath` is the exception — it
 * receives source-space verbs along with the affine `(sx, sy, tx, ty)` so
 * it can transform vertices itself (cheaper than allocating a transformed
 * copy of the path).
 */
public class SkBitmapDevice(public val bitmap: SkBitmap) : SkDevice {

    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    /**
     * Source-space colors entering the device are sRGB-encoded (that's the
     * SkColor convention). Build a one-shot xform that brings them into the
     * bitmap's color space. When the bitmap is sRGB, this is the identity
     * pipeline (`flags.isIdentity == true`) and [transformPaintColor] is a
     * no-op.
     */
    private val xformSteps: SkColorSpaceXformSteps = SkColorSpaceXformSteps(
        src = SkColorSpace.makeSRGB(), srcAT = SkAlphaType.kUnpremul,
        dst = bitmap.colorSpace,      dstAT = SkAlphaType.kUnpremul,
    )

    override fun deviceClipBounds(): SkIRect = SkIRect.MakeWH(width, height)

    // ─── Phase I3.3.b — SkAAClip-backed clip plumbing ────────────────────
    //
    // Replaces the Phase 7q `clipMask: ByteArray?` 2D byte buffer with an
    // `SkAAClip` carrying band-encoded RLE (width, alpha) runs. Memory is
    // sparse for typical clip shapes (rect ⇒ 1 band × 1 run ; convex AA
    // path ⇒ ≤ 30 bands × ≤ 5 runs each, vs `width × height` bytes for
    // the byte buffer). Per-pixel queries are slightly more expensive
    // (binary-search Y + linear scan X runs) but typical row layouts
    // keep that constant small.

    private var activeAaClip: SkAAClip? = null

    /** Bind the active AA clip before the next draw call. Called by SkCanvas. */
    internal fun setActiveClip(aaClip: SkAAClip?) {
        activeAaClip = aaClip
    }

    // ─── R-suivi.20 — clipShader rasterizer wiring ────────────────────
    //
    // The clip-shader (set via `SkCanvas.clipShader`) modulates per-pixel
    // coverage on every draw path, not just `drawPaint`. The shader is
    // captured against the CTM that was active at the `clipShader()` call
    // site (Skia's "frozen at call time" semantics) ; the device keeps a
    // single-row scratch buffer to avoid per-pixel allocation inside the
    // hot path.
    //
    // The blend funnels ([blend], [blendCustom], [blendF16], …) consult
    // these slots — when non-null, every pixel's source alpha is
    // multiplied by `clipShader.alpha / 255` (kIntersect) or
    // `(255 - alpha) / 255` (kDifference) before the blend.

    private var activeClipShader: SkShader? = null
    private var activeClipShaderOp: SkClipOp = SkClipOp.kIntersect
    private var lastDescriptorCoverageDiagnostics = SkBitmapDescriptorCoverageDiagnostics(
        mode = "not-run",
        backend = BackendKind.CPU,
        drawKind = "axis-aligned-filled-rect",
        selectedRoute = "none",
        compatibilityFallbackRoute = "kanvas-skia.current.draw-rect",
        coveragePlan = "none",
        loweringResult = "none",
        executionEvidence = "none",
        fallbackReason = "not-run",
        touchedPixels = 0,
    )

    /** Single-element scratch used by the per-pixel [clipShaderCoverage] helper. */
    private val clipShaderPixelScratch: IntArray = IntArray(1)

    /**
     * Bind the active clip shader before the next draw call. Called by
     * SkCanvas alongside [setActiveClip]. Pass `null` to clear.
     *
     * When [shader] is non-null, the device calls [SkShader.setupForDraw]
     * with [ctm] (the CTM frozen at the `SkCanvas.clipShader()` call
     * site) so subsequent `shadeRow` queries return alpha in the shader's
     * intended coordinate system.
     */
    internal fun setActiveClipShader(shader: SkShader?, ctm: SkMatrix, op: SkClipOp) {
        activeClipShader = shader
        activeClipShaderOp = op
        if (shader != null) {
            shader.setupForDraw(ctm, xformSteps)
        }
    }

    /**
     * Returns the clip-shader's coverage at device pixel `(x, y)` as a
     * `0..255` byte. 255 = fully visible, 0 = fully clipped out.
     * Honours [activeClipShaderOp] : [SkClipOp.kIntersect] uses the
     * shader's alpha as-is, [SkClipOp.kDifference] inverts it.
     * Returns 255 when no clip shader is bound (caller should
     * short-circuit via `activeClipShader == null` for the hot path).
     */
    private fun clipShaderCoverage(x: Int, y: Int): Int {
        val shader = activeClipShader ?: return 255
        shader.shadeRow(x, y, 1, clipShaderPixelScratch)
        val raw = SkColorGetA(clipShaderPixelScratch[0])
        return when (activeClipShaderOp) {
            SkClipOp.kIntersect -> raw
            SkClipOp.kDifference -> 255 - raw
        }
    }

    /** Clip-mask coverage at device pixel `(x, y)` ; 255 if no AA clip, 0 outside its bounds. */
    private fun clipCoverage(x: Int, y: Int): Int {
        val ac = activeAaClip ?: return 255
        return ac.coverage(x, y)
    }

    override fun drawRect(rect: SkRect, clip: SkIRect, paint: SkPaint) {
        val devPaint = inDeviceColorSpace(paint)
        if (tryDrawDescriptorAxisAlignedFilledRect(rect, clip, devPaint)) return
        if (devPaint.isAntiAlias) drawRectAA(rect, clip, devPaint) else drawRectNonAA(rect, clip, devPaint)
    }

    internal fun descriptorCoverageDiagnosticsForTests(): SkBitmapDescriptorCoverageDiagnostics =
        lastDescriptorCoverageDiagnostics

    private fun tryDrawDescriptorAxisAlignedFilledRect(rect: SkRect, clip: SkIRect, paint: SkPaint): Boolean {
        val coverage = CoveragePlan.AnalyticRect(
            bounds = FloatRect(rect.left, rect.top, rect.right, rect.bottom),
            aa = paint.isAntiAlias,
        )
        val geometry = GeometryPlan.Supported(
            primitive = GeometryPrimitive.Rect(
                source = FloatRect(rect.left, rect.top, rect.right, rect.bottom),
                device = FloatRect(rect.left, rect.top, rect.right, rect.bottom),
            ),
            bounds = GeometryBounds(
                conservative = FloatRect(rect.left, rect.top, rect.right, rect.bottom),
                tight = FloatRect(rect.left, rect.top, rect.right, rect.bottom),
            ),
            transform = TransformFacts(
                matrix = MatrixSpec.Identity,
                isAxisAligned = true,
                hasPerspective = false,
                maxScale = 1f,
                isInvertible = true,
            ),
            clip = ClipInteraction.None,
        )
        val lowering = SkBitmapDescriptorCoverageLowering.lower(coverage)
        val coverageDiagnostic = dumpAnalyticRectCoverage(coverage)
        val loweringDiagnostic = dumpCoverageLowering(lowering)
        val unsupportedReason = descriptorAxisAlignedRectFallbackReason(paint)
        if (unsupportedReason != null) {
            lastDescriptorCoverageDiagnostics = SkBitmapDescriptorCoverageDiagnostics(
                mode = if (!descriptorRectEnabled()) "Rollback" else "Fallback",
                backend = BackendKind.CPU,
                drawKind = "axis-aligned-filled-rect",
                selectedRoute = "kanvas-skia.current.draw-rect",
                compatibilityFallbackRoute = "cpu.descriptor.coverage-plan.solid-rect",
                coveragePlan = coverageDiagnostic,
                loweringResult = loweringDiagnostic,
                executionEvidence = "legacy-fallback-before-descriptor-execution",
                fallbackReason = unsupportedReason,
                touchedPixels = 0,
            )
            return false
        }

        val loweredCoverage = (lowering as? CoverageLoweringResult.CoverageModelResult)?.coverage
        val analyticRect = loweredCoverage as? CoverageModel.AnalyticRect
        if (analyticRect == null) {
            lastDescriptorCoverageDiagnostics = SkBitmapDescriptorCoverageDiagnostics(
                mode = "Fallback",
                backend = BackendKind.CPU,
                drawKind = "axis-aligned-filled-rect",
                selectedRoute = "kanvas-skia.current.draw-rect",
                compatibilityFallbackRoute = "cpu.descriptor.coverage-plan.solid-rect",
                coveragePlan = coverageDiagnostic,
                loweringResult = loweringDiagnostic,
                executionEvidence = "descriptor-execution-refused",
                fallbackReason = CPU_DESCRIPTOR_LOWERING_UNSUPPORTED_REASON,
                touchedPixels = 0,
            )
            return false
        }

        val execution = executeDescriptorAnalyticRect(analyticRect, clip, paint)
        val executionEvidence =
            "lowering-consumed:CoverageModel.AnalyticRect;kernel=${execution.kernelId};touchedPixels=${execution.touchedPixels}"
        lastDescriptorCoverageDiagnostics = SkBitmapDescriptorCoverageDiagnostics(
            mode = "Default",
            backend = BackendKind.CPU,
            drawKind = "axis-aligned-filled-rect",
            selectedRoute = "cpu.descriptor.coverage-plan.solid-rect",
            compatibilityFallbackRoute = "kanvas-skia.current.draw-rect",
            coveragePlan = coverageDiagnostic,
            loweringResult = loweringDiagnostic,
            executionEvidence = executionEvidence,
            fallbackReason = null,
            touchedPixels = execution.touchedPixels,
        )
        return true
    }

    private fun executeDescriptorAnalyticRect(
        coverage: CoverageModel.AnalyticRect,
        clip: SkIRect,
        paint: SkPaint,
    ) = if (coverage.aa) {
        executeDescriptorAnalyticRectAA(coverage, clip, paint)
    } else {
        CpuAnalyticRectCoverageExecutor.execute(coverage, clip.toPipelineIntRect()) { x, y, _ ->
            dispatchBlend(x, y, paint.color, paint.blendMode, paint.blender)
        }
    }

    private fun executeDescriptorAnalyticRectAA(
        coverage: CoverageModel.AnalyticRect,
        clip: SkIRect,
        paint: SkPaint,
    ): org.skia.pipeline.CpuAnalyticRectCoverageMetrics {
        val baseA = (paint.color4f.fA * 255f + 0.5f).toInt().coerceIn(0, 255)
        val mustBlendZero = modeAffectsZeroAlphaSrc(paint.blendMode)
        val customBlender = paint.blender != null && paint.blender !is org.skia.foundation.SkBlendModeBlender
        if (bitmap.colorType == org.skia.foundation.SkColorType.kRGBA_F16Norm && !customBlender) {
            val src = FloatArray(4)
            colorToF16Premul(paint.color4f, src)
            val sr = src[0]
            val sg = src[1]
            val sb = src[2]
            val sa = src[3]
            return CpuAnalyticRectCoverageExecutor.execute(coverage, clip.toPipelineIntRect()) { x, y, cov ->
                val saCov = sa * cov
                if (saCov > 0f || mustBlendZero) {
                    blendF16PremulMode(x, y, sr * cov, sg * cov, sb * cov, saCov, paint.blendMode)
                }
            }
        }

        val color = paint.color4f.toSkColor()
        val rgb = color and 0x00FFFFFF
        return CpuAnalyticRectCoverageExecutor.execute(coverage, clip.toPipelineIntRect()) { x, y, cov ->
            val effA = scaleAlpha(baseA, cov)
            if (effA != 0 || mustBlendZero) {
                dispatchBlend(x, y, (effA shl 24) or rgb, paint.blendMode, paint.blender)
            }
        }
    }

    private fun dumpAnalyticRectCoverage(coverage: CoveragePlan.AnalyticRect): String =
        "AnalyticRect(${coverage.bounds.left},${coverage.bounds.top},${coverage.bounds.right},${coverage.bounds.bottom},aa=${coverage.aa})"

    private fun dumpCoverageLowering(lowering: CoverageLoweringResult): String = when (lowering) {
        is CoverageLoweringResult.CoverageModelResult -> when (val coverage = lowering.coverage) {
            is CoverageModel.AnalyticRect ->
                "CoverageModel.AnalyticRect(${coverage.bounds.left},${coverage.bounds.top},${coverage.bounds.right},${coverage.bounds.bottom},aa=${coverage.aa})"
            CoverageModel.Full -> "CoverageModel.Full"
            CoverageModel.Span -> "CoverageModel.Span"
            is CoverageModel.AlphaMask ->
                "CoverageModel.AlphaMask(${coverage.bounds.left},${coverage.bounds.top},${coverage.bounds.right},${coverage.bounds.bottom},format=${coverage.format})"
        }
        is CoverageLoweringResult.StrategyResult -> "StrategyResult(${lowering.strategy.reasonCode})"
    }

    private fun descriptorAxisAlignedRectFallbackReason(paint: SkPaint): String? = when {
        !descriptorRectEnabled() -> CPU_DESCRIPTOR_RECT_DISABLED_REASON
        paint.style != SkPaint.Style.kFill_Style -> CPU_DESCRIPTOR_FILL_STYLE_REASON
        paint.shader != null -> CPU_DESCRIPTOR_SHADER_REASON
        paint.colorFilter != null -> CPU_DESCRIPTOR_COLOR_FILTER_REASON
        paint.maskFilter != null -> CPU_DESCRIPTOR_MASK_FILTER_REASON
        paint.imageFilter != null -> CPU_DESCRIPTOR_IMAGE_FILTER_REASON
        paint.pathEffect != null -> CPU_DESCRIPTOR_PATH_EFFECT_REASON
        paint.blender != null -> CPU_DESCRIPTOR_BLENDER_REASON
        paint.blendMode != SkBlendMode.kSrcOver -> CPU_DESCRIPTOR_BLEND_MODE_REASON
        activeAaClip != null -> CPU_DESCRIPTOR_AA_CLIP_REASON
        activeClipShader != null -> CPU_DESCRIPTOR_CLIP_SHADER_REASON
        else -> null
    }

    private fun descriptorRectEnabled(): Boolean =
        System.getProperty(DESCRIPTOR_RECT_FLAG, "true").toBoolean()

    private fun SkIRect.toPipelineIntRect(): IntRect = IntRect(left, top, right, bottom)

    /**
     * `true` when the blend mode produces a non-`dst` output for a fully
     * transparent source colour (e.g. [SkBlendMode.kClear], [SkBlendMode.kDstIn]
     * with `sa == 0` zeroes dst). Callers normally short-circuit when
     * `src.alpha == 0` to avoid touching covered pixels at all; for these
     * modes the device must still walk the spans and apply the blend.
     */
    private fun modeAffectsZeroAlphaSrc(mode: SkBlendMode): Boolean = when (mode) {
        // r = 0
        SkBlendMode.kClear,
        // r = s — when s premul = 0, dst becomes 0
        SkBlendMode.kSrc,
        // r = s * da — zeroes dst where src.alpha == 0
        SkBlendMode.kSrcIn,
        // r = d * sa — zeroes dst where src.alpha == 0
        SkBlendMode.kDstIn,
        // r = s * (1-da) — zeroes dst where src.alpha == 0
        SkBlendMode.kSrcOut,
        // r = d*sa + s*(1-da) — zeroes dst where src.alpha == 0
        SkBlendMode.kDstATop,
        // r = s * d — zeroes dst where src is 0
        SkBlendMode.kModulate -> true
        else -> false
    }

    /**
     * Mirrors Skia's `SkBitmapDevice::drawPaint`. Fills every pixel inside
     * [clip] with `paint.color` (or [paint.shader]'s output, when present),
     * composited via [paint.blendMode]. The clip is integer-aligned in
     * device coords, so per-pixel coverage is binary — no AA bookkeeping
     * needed regardless of `paint.isAntiAlias`.
     *
     * [ctm] is the current canvas matrix; only used when [paint] has a
     * shader (the shader's `setupForDraw` needs it to compute the
     * `device → local` transform).
     *
     * Phase R2.14 — optional [clipShader] mixin. When non-null, per-pixel
     * coverage is additionally multiplied by `clipShader.alpha / 255`
     * (or `1 - alpha / 255` when [clipShaderOp] is [SkClipOp.kDifference]).
     * The shader is bound against [clipShaderCtm] (captured at the
     * `SkCanvas.clipShader()` call site), not the current [ctm]. This
     * matches Skia's "clip is frozen at call time" semantics.
     */
    override fun drawPaint(
        ctm: SkMatrix,
        clip: SkIRect,
        paint: SkPaint,
    ) {
        val mode = paint.blendMode
        val blender = paint.blender
        val l = clip.left.coerceAtLeast(0)
        val t = clip.top.coerceAtLeast(0)
        val r = clip.right.coerceAtMost(width)
        val b = clip.bottom.coerceAtMost(height)
        if (l >= r || t >= b) return

        // R-suivi.20 — clipShader coverage is now folded inside the
        // blend funnels ([blend], [blendCustom], [blendF16],
        // [blendF16Premul], [blendF16PremulMode]) via the
        // [activeClipShader] device-level slot (bound by SkCanvas's
        // `bindClip`). The R2.14 scanline-walker is gone — every draw
        // entry point now honours the shader without duplicating the
        // per-row sampling here.

        // Phase G7 — when `paint.imageFilter` is set, route through an
        // implicit saveLayer / drawPaint / restore so the layer's filter
        // pass is applied. Mirror of [SkCanvas.restore] for layers with
        // an image filter. The inner draw happens into a fresh device
        // sized to the clip; we then run the filter on its snapshot and
        // composite the filtered image back with the paint's blend mode
        // / alpha / colour filter (imageFilter stripped to avoid
        // infinite recursion).
        val imageFilter = paint.imageFilter
        if (imageFilter != null) {
            val layerW = r - l
            val layerH = b - t
            val layerBitmap = SkBitmap(layerW, layerH, bitmap.colorSpace, bitmap.colorType)
                .also { it.eraseColor(0) }
            val layerDevice = SkBitmapDevice(layerBitmap)
            // Inner paint: clear the image filter to avoid infinite
            // recursion; also clear the blend mode / colour filter / alpha
            // so the inner pass writes the unmodified paint colour /
            // shader to the layer. Those properties take effect at the
            // outer composite step below.
            val innerPaint = paint.copy().apply {
                this.imageFilter = null
                this.colorFilter = null
                this.blendMode = SkBlendMode.kSrc
                this.alpha = 0xFF
            }
            val innerClip = SkIRect.MakeLTRB(0, 0, layerW, layerH)
            // Inner CTM is the parent CTM shifted by -(l, t) so a parent
            // device-space point lands at layer coords.
            val innerCtm = ctm.copy(tx = ctm.tx - l, ty = ctm.ty - t)
            layerDevice.drawPaint(innerCtm, innerClip, innerPaint)
            val snapshot = layerBitmap.asImage()
            val filterResult = imageFilter.filterImage(snapshot, ctm)
            val filteredImg = filterResult.image
            val filteredBitmap = SkBitmap(
                filteredImg.width, filteredImg.height,
                bitmap.colorSpace, bitmap.colorType,
            )
            for (yp in 0 until filteredImg.height) {
                for (xp in 0 until filteredImg.width) {
                    filteredBitmap.setPixel(xp, yp, filteredImg.peekPixel(xp, yp))
                }
            }
            val filteredDevice = SkBitmapDevice(filteredBitmap)
            val proxyPaint = paint.copy().apply { this.imageFilter = null }
            compositeFrom(
                filteredDevice,
                l + filterResult.offsetX,
                t + filterResult.offsetY,
                clip,
                proxyPaint,
            )
            return
        }

        val shader = paint.shader
        if (shader != null) {
            // Shader path. paint.alpha modulates the shader output (Skia's
            // semantics: shaderColor * paint.alpha). With kSrcOver + F16 we
            // take a pure premul-float path that mirrors the Phase 6b
            // shader path in [scanFillPath] (no per-pixel byte conversion).
            shader.setupForDraw(ctm, xformSteps)
            // Slice 2.3: paint.alphaf reads fColor4f.fA directly — no
            // byte round-trip. setAlphaf(0.3f) survives the modulation.
            val paintAlphaF = paint.alphaf
            if (paintAlphaF == 0f && !modeAffectsZeroAlphaSrc(mode)) return
            val rowWidth = r - l
            val isF16 = bitmap.colorType == org.skia.foundation.SkColorType.kRGBA_F16Norm
            // Phase 6s — F16 path covers all 29 modes (was: kSrcOver only).
            // Non-srcOver modes route through [blendF16PremulMode] which
            // dispatches inline for Porter-Duff / Plus / Modulate / Screen
            // and shares the existing pure-float [sepChannel] / [blendHSLF16Body]
            // helpers for the 14 separable + HSL modes.
            val useF16 = isF16
            if (useF16) {
                val rowF16 = FloatArray(rowWidth * 4)
                val mustBlendZero = modeAffectsZeroAlphaSrc(mode)
                for (y in t until b) {
                    shader.shadeRowF16(l, y, rowWidth, rowF16)
                    for (xOff in 0 until rowWidth) {
                        val si = xOff * 4
                        val sa = rowF16[si + 3] * paintAlphaF
                        if (sa <= 0f && !mustBlendZero) continue
                        val sr = rowF16[si]     * paintAlphaF
                        val sg = rowF16[si + 1] * paintAlphaF
                        val sb = rowF16[si + 2] * paintAlphaF
                        blendF16PremulMode(l + xOff, y, sr, sg, sb, sa, mode)
                    }
                }
            } else {
                val row = IntArray(rowWidth)
                val paintAlpha = paint.alpha
                for (y in t until b) {
                    shader.shadeRow(l, y, rowWidth, row)
                    for (xOff in 0 until rowWidth) {
                        val src = row[xOff]
                        val srcA = SkColorGetA(src)
                        if (srcA == 0) continue
                        val effA = if (paintAlpha == 0xFF) srcA
                            else (srcA * paintAlpha + 127) / 255
                        if (effA == 0) continue
                        dispatchBlend(l + xOff, y, (effA shl 24) or (src and 0x00FFFFFF), mode, blender)
                    }
                }
            }
            return
        }

        // Solid-colour path (Phase 1). Phase 7e — apply colour filter
        // BEFORE the colour-space xform, so the filter math runs in
        // sRGB (matching Skia upstream's working space for filters).
        // Pre-7e ordering applied filter in the device's working space
        // (Rec.2020 under the GM harness), which silently re-tuned the
        // matrix coefficients (Rec.709 luma weights) to a different
        // gamut. Closing this gap was the docstring TODO of Phase 7a.
        val color = transformPaintColor(applyColorFilter(paint.colorFilter, paint.color))
        if (SkColorGetA(color) == 0 && !modeAffectsZeroAlphaSrc(mode)) return
        for (y in t until b) {
            for (x in l until r) dispatchBlend(x, y, color, mode, blender)
        }
    }

    /**
     * Phase I5.3.b — rasterise a single triangle with per-vertex
     * colour interpolation. Maps `(p0, p1, p2)` through [ctm] into
     * device space, computes the AABB clipped to [clip], then per
     * pixel inside the triangle :
     *  1. computes barycentric weights `(w0, w1, w2)` with
     *     `w0 + w1 + w2 = 1` ;
     *  2. linearly interpolates the per-vertex ARGB values per
     *     channel ;
     *  3. if [paint] has a shader, combines that shader sample as
     *     the blend src with the interpolated vertex colour as the
     *     blend dst via [vertexBlend] ;
     *  4. modulates by `paint.color.alpha` (paint colour acts as a
     *     post-multiplier when [SkVertices.colors] are present) ;
     *  5. dispatches to [blend] with `paint.blendMode`.
     *
     * Color-space xform is applied to each interpolated colour
     * before blending — same pipeline as the solid-colour
     * [drawRect] / [drawPath] paths.
     *
     * Out of scope (next slice — I5.3.c) :
     *  - per-vertex texCoords / shader sampling and color × shader
     *    blending under the supplied `blendMode`.
     */
    internal fun drawColoredTriangle(
        p0x: Float, p0y: Float, c0: SkColor,
        p1x: Float, p1y: Float, c1: SkColor,
        p2x: Float, p2y: Float, c2: SkColor,
        vertexBlend: SkBlendMode,
        ctm: SkMatrix, clip: SkIRect, paint: SkPaint,
    ) {
        val shader = paint.shader
        shader?.setupForDraw(ctm, xformSteps)

        val (ax, ay) = ctm.mapXY(p0x, p0y)
        val (bx, by) = ctm.mapXY(p1x, p1y)
        val (cx, cy) = ctm.mapXY(p2x, p2y)

        val minXf = minOf(ax, bx, cx)
        val minYf = minOf(ay, by, cy)
        val maxXf = maxOf(ax, bx, cx)
        val maxYf = maxOf(ay, by, cy)
        val minX = maxOf(clip.left, kFloor(minXf.toDouble()).toInt())
        val minY = maxOf(clip.top, kFloor(minYf.toDouble()).toInt())
        val maxX = minOf(clip.right, kCeil(maxXf.toDouble()).toInt())
        val maxY = minOf(clip.bottom, kCeil(maxYf.toDouble()).toInt())
        if (minX >= maxX || minY >= maxY) return

        val area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
        if (kotlin.math.abs(area) < 1e-6f) return
        val invArea = 1f / area

        val mode = paint.blendMode
        val blender = paint.blender
        val paintAlpha = SkColorGetA(paint.color)
        val shaderRow = IntArray(1)

        val a0a = SkColorGetA(c0); val a0r = SkColorGetR(c0)
        val a0g = SkColorGetG(c0); val a0b = SkColorGetB(c0)
        val a1a = SkColorGetA(c1); val a1r = SkColorGetR(c1)
        val a1g = SkColorGetG(c1); val a1b = SkColorGetB(c1)
        val a2a = SkColorGetA(c2); val a2r = SkColorGetR(c2)
        val a2g = SkColorGetG(c2); val a2b = SkColorGetB(c2)

        for (y in minY until maxY) {
            for (x in minX until maxX) {
                val px = x + 0.5f
                val py = y + 0.5f
                val w0 = ((bx - px) * (cy - py) - (cx - px) * (by - py)) * invArea
                val w1 = ((cx - px) * (ay - py) - (ax - px) * (cy - py)) * invArea
                val w2 = 1f - w0 - w1
                if (w0 < 0 || w1 < 0 || w2 < 0) continue
                val ai = (a0a * w0 + a1a * w1 + a2a * w2).toInt().coerceIn(0, 255)
                val ri = (a0r * w0 + a1r * w1 + a2r * w2).toInt().coerceIn(0, 255)
                val gi = (a0g * w0 + a1g * w1 + a2g * w2).toInt().coerceIn(0, 255)
                val bi = (a0b * w0 + a1b * w1 + a2b * w2).toInt().coerceIn(0, 255)
                val vertexColor = transformPaintColor(SkColorSetARGB(ai, ri, gi, bi))
                val combinedRaw = if (shader != null) {
                    shader.shadeRow(x, y, 1, shaderRow)
                    blendPixel(shaderRow[0], vertexColor, vertexBlend)
                } else {
                    vertexColor
                }
                val combinedA = SkColorGetA(combinedRaw)
                val finalA = (combinedA * paintAlpha + 127) / 255
                if (finalA == 0 && !modeAffectsZeroAlphaSrc(mode)) continue
                val src = SkColorSetARGB(
                    finalA, SkColorGetR(combinedRaw), SkColorGetG(combinedRaw), SkColorGetB(combinedRaw),
                )
                dispatchBlend(x, y, applyColorFilter(paint.colorFilter, src), mode, blender)
            }
        }
    }

    /**
     * Phase I5.3.c — rasterise a single triangle with per-vertex
     * texture coordinates and (optionally) per-vertex colours. The
     * texture comes from `paint.shader` ; per pixel inside the
     * triangle we :
     *  1. compute barycentric weights `(w0, w1, w2)` ;
     *  2. linearly interpolate the texture coords to `(uvX, uvY)` ;
     *  3. sample the shader via [SkShader.sampleAtLocal] ;
     *  4. if [c0] / [c1] / [c2] are non-null, also interpolate the
     *     vertex ARGB and combine `shaderSample` as src with
     *     `vertexColor` as dst under [vertexBlend] ;
     *  5. modulate by `paint.color.alpha` ;
     *  6. dispatch to [blend] with `paint.blendMode`.
     *
     * @param vertexBlend mirrors the `blendMode` argument of
     *   `SkCanvas::drawVertices` — combines the per-vertex colour
     *   with the texture sample.
     */
    @Suppress("LongParameterList")
    internal fun drawTexturedTriangle(
        p0x: Float, p0y: Float, uv0x: Float, uv0y: Float, c0: SkColor?,
        p1x: Float, p1y: Float, uv1x: Float, uv1y: Float, c1: SkColor?,
        p2x: Float, p2y: Float, uv2x: Float, uv2y: Float, c2: SkColor?,
        vertexBlend: SkBlendMode,
        ctm: SkMatrix, clip: SkIRect, paint: SkPaint,
    ) {
        val shader = paint.shader ?: return
        // Pre-warm the shader (one-time per draw — reused across all
        // pixels of the triangle).
        shader.setupForDraw(ctm, xformSteps)
        val localToShader = shader.localMatrix.invert()

        val (ax, ay) = ctm.mapXY(p0x, p0y)
        val (bx, by) = ctm.mapXY(p1x, p1y)
        val (cx, cy) = ctm.mapXY(p2x, p2y)

        val minX = maxOf(clip.left, kFloor(minOf(ax, bx, cx).toDouble()).toInt())
        val minY = maxOf(clip.top, kFloor(minOf(ay, by, cy).toDouble()).toInt())
        val maxX = minOf(clip.right, kCeil(maxOf(ax, bx, cx).toDouble()).toInt())
        val maxY = minOf(clip.bottom, kCeil(maxOf(ay, by, cy).toDouble()).toInt())
        if (minX >= maxX || minY >= maxY) return

        val area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
        if (kotlin.math.abs(area) < 1e-6f) return
        val invArea = 1f / area

        val mode = paint.blendMode
        val blender = paint.blender
        val paintAlpha = SkColorGetA(paint.color)
        val haveColors = c0 != null && c1 != null && c2 != null

        for (y in minY until maxY) {
            for (x in minX until maxX) {
                val px = x + 0.5f
                val py = y + 0.5f
                val w0 = ((bx - px) * (cy - py) - (cx - px) * (by - py)) * invArea
                val w1 = ((cx - px) * (ay - py) - (ax - px) * (cy - py)) * invArea
                val w2 = 1f - w0 - w1
                if (w0 < 0 || w1 < 0 || w2 < 0) continue

                val uvX = uv0x * w0 + uv1x * w1 + uv2x * w2
                val uvY = uv0y * w0 + uv1y * w1 + uv2y * w2
                val (sampleX, sampleY) = if (localToShader != null) {
                    localToShader.mapXY(uvX, uvY)
                } else {
                    uvX to uvY
                }
                val texColor = shader.sampleAtLocal(sampleX, sampleY)

                val combinedRaw: SkColor = if (haveColors) {
                    val color0 = c0 ?: error("drawTexturedTriangle: missing c0 despite haveColors")
                    val color1 = c1 ?: error("drawTexturedTriangle: missing c1 despite haveColors")
                    val color2 = c2 ?: error("drawTexturedTriangle: missing c2 despite haveColors")
                    val vci = combineVertexColorTexture(
                        SkColorGetA(color0) * w0 + SkColorGetA(color1) * w1 + SkColorGetA(color2) * w2,
                        SkColorGetR(color0) * w0 + SkColorGetR(color1) * w1 + SkColorGetR(color2) * w2,
                        SkColorGetG(color0) * w0 + SkColorGetG(color1) * w1 + SkColorGetG(color2) * w2,
                        SkColorGetB(color0) * w0 + SkColorGetB(color1) * w1 + SkColorGetB(color2) * w2,
                        texColor, vertexBlend,
                    )
                    vci
                } else {
                    texColor
                }

                val ai = SkColorGetA(combinedRaw)
                if (ai == 0 && !modeAffectsZeroAlphaSrc(mode)) continue
                val finalA = (ai * paintAlpha + 127) / 255
                val src = SkColorSetARGB(
                    finalA, SkColorGetR(combinedRaw), SkColorGetG(combinedRaw), SkColorGetB(combinedRaw),
                )
                dispatchBlend(x, y, applyColorFilter(paint.colorFilter, src), mode, blender)
            }
        }
    }

    /**
     * Internal helper for [drawTexturedTriangle] : combine an
     * interpolated vertex colour (passed as float per channel to
     * preserve barycentric precision) with the sampled texture
     * colour under the requested vertex blend mode. Honours the
     * full [SkBlendMode] set, with the sampled texture as `src` and
     * the vertex colour as `dst`.
     */
    private fun combineVertexColorTexture(
        vA: Float, vR: Float, vG: Float, vB: Float,
        texColor: SkColor,
        vertexBlend: SkBlendMode,
    ): SkColor {
        val viA = vA.toInt().coerceIn(0, 255)
        val viR = vR.toInt().coerceIn(0, 255)
        val viG = vG.toInt().coerceIn(0, 255)
        val viB = vB.toInt().coerceIn(0, 255)
        val vertexColor = transformPaintColor(SkColorSetARGB(viA, viR, viG, viB))
        return blendPixel(texColor, vertexColor, vertexBlend)
    }

    /**
     * Composite `src`'s pixels onto this device, with `src`'s `(0, 0)`
     * landing at this device's `(originX, originY)`, intersecting writes
     * with [clip] (in this device's coords). Source pixels are blended
     * through `paint?.blendMode` (defaults to [SkBlendMode.kSrcOver])
     * after multiplying by `paint?.alpha`.
     *
     * Used by `SkCanvas.restore` to flatten a `saveLayer`'s offscreen
     * device back into its parent. For modes whose formula evaluates to
     * a non-`dst` value at `sa == 0` (e.g. [SkBlendMode.kClear],
     * [SkBlendMode.kSrcIn], [SkBlendMode.kDstIn], [SkBlendMode.kSrcOut],
     * [SkBlendMode.kDstATop], [SkBlendMode.kModulate]), transparent
     * source pixels inside the layer extent still trigger a blend so
     * the destination is zeroed where the layer carries no coverage.
     *
     * Pre-condition: `src` and this device share the same color space,
     * so no per-pixel xform is needed (the canvas seeds the layer
     * device with the parent's color space).
     */
    override fun compositeFrom(
        src: SkDevice,
        originX: Int,
        originY: Int,
        clip: SkIRect,
        paint: SkPaint?,
    ) {
        // Raster composite is implemented as a pixel-walk over the source
        // bitmap ; the GPU layer device would need to first read back to
        // CPU before reaching this path. The CPU layer pipeline never
        // mixes backends (SkCanvas constructs a raster layer when the
        // root is raster) so a hard cast is safe here.
        val srcBitmap = src as? SkBitmapDevice
            ?: error(
                "SkBitmapDevice.compositeFrom : source device is " +
                    "${src::class.simpleName} ; raster composite only consumes " +
                    "SkBitmapDevice. Generalising across backends requires a " +
                    "readback step (see SkWebGpuDevice.compositeFrom)."
            )
        val paintAlpha = paint?.alpha ?: 0xFF
        val mode = paint?.blendMode ?: SkBlendMode.kSrcOver
        val blender = paint?.blender
        val colorFilter = paint?.colorFilter
        // For "normal" modes (kSrcOver et al.), a fully transparent paint
        // alpha makes every src contribution vanish, so we can short-circuit
        // entirely. Modes like kClear / kSrcIn still need to walk the layer
        // bounds (they zero dst regardless of src alpha), so we let those
        // through and rely on the inner-loop guard. A colour filter may
        // produce non-trivial output for transparent input, so we also
        // skip the short-circuit when one is present.
        val mustBlendZero = modeAffectsZeroAlphaSrc(mode) || colorFilter != null
        if (paintAlpha == 0 && !mustBlendZero) return
        val l = maxOf(clip.left, originX, 0)
        val t = maxOf(clip.top, originY, 0)
        val r = minOf(clip.right, originX + srcBitmap.width, width)
        val b = minOf(clip.bottom, originY + srcBitmap.height, height)
        if (l >= r || t >= b) return
        // Read source pixels via the colorType-aware [SkBitmap.getPixel]
        // accessor so this composite works for both 8888-only and F16-only
        // layer pairs (and any future colorType combos). Slightly slower
        // than the historical raw-IntArray walk, but only matters when a
        // GM uses `saveLayer` heavily — none in scope do.
        for (y in t until b) {
            for (x in l until r) {
                val sample = srcBitmap.bitmap.getPixel(x - originX, y - originY)
                var effective = if (paintAlpha == 0xFF) sample else applyAlpha(sample, paintAlpha)
                effective = applyColorFilter(colorFilter, effective)
                if (effective ushr 24 == 0 && !mustBlendZero) continue
                dispatchBlend(x, y, effective, mode, blender, "SkBitmapDevice.compositeFrom")
            }
        }
    }

    /**
     * Phase G-saveLayer — allocate a raster layer-device matching this
     * device's colour profile. Always succeeds : raster always supports
     * layers (`SkCanvas.saveLayer` has worked on the CPU since Phase 7).
     */
    override fun makeLayerDevice(width: Int, height: Int, colorType: SkColorType?): SkDevice {
        val layerBitmap = SkBitmap(width, height, bitmap.colorSpace, colorType ?: bitmap.colorType)
            .also { it.eraseColor(0) }
        return SkBitmapDevice(layerBitmap)
    }

    /**
     * Draw `image` into the supplied **device-space** `devDst` rect, sampling
     * the image-space `src` sub-rectangle. The canvas has already applied its
     * CTM to produce `devDst`; the device only needs to perform the inverse
     * `dst → src` mapping per pixel and sample.
     *
     * Pixel coverage uses the same top-exclusive / bottom-inclusive rule as
     * the non-AA rect path (`pixelEdge`); AA edges of `devDst` are not
     * fractionally covered (matches Skia's default `drawImageRect` behaviour
     * when `paint` lacks `setAntiAlias(true)`).
     *
     * Supported [SkFilterMode]s : `kNearest`, `kLinear`. Mipmap and bicubic
     * sampling are out of scope. Out-of-range sample coordinates are clamped
     * to the image edge texels — Skia's `kClamp` tile mode and the default
     * behaviour under both [SrcRectConstraint] variants for axis-aligned
     * mappings without filter overflow.
     */
    override fun drawImageRect(
        image: SkImage,
        src: SkRect,
        devDst: SkRect,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        constraint: SrcRectConstraint,
        clip: SkIRect,
    ) {
        if (devDst.right <= devDst.left || devDst.bottom <= devDst.top) return
        if (src.right <= src.left || src.bottom <= src.top) return
        if (image.width <= 0 || image.height <= 0) return

        // Phase 7d.1 — when paint.imageFilter is set, route through
        // the filter then re-enter `drawImageRect` with the filtered
        // image + offset-shifted devDst. Recursion guard : the inner
        // paint has imageFilter cleared.
        val imageFilter = paint?.imageFilter
        if (imageFilter != null) {
            // Use an identity CTM proxy here — the device's own CTM is
            // already baked into [devDst]. The filter's `ctm` parameter
            // is for filters that need device-space precision (e.g.
            // Offset scales its (dx, dy) by the canvas's max scale).
            // We pass identity since the offset will be applied
            // directly in device coords.
            val result = imageFilter.filterImage(image, org.graphiks.math.SkMatrix.Identity)
            // Shift devDst by the filter's offset.
            val newDevDst = SkRect.MakeLTRB(
                devDst.left + result.offsetX,
                devDst.top + result.offsetY,
                devDst.right + result.offsetX,
                devDst.bottom + result.offsetY,
            )
            // Re-enter without the filter to avoid infinite recursion.
            // For the filtered image, the source rect is the full
            // filtered image (filters typically produce image-space
            // outputs that the device draws 1:1 to the destination).
            val filtered = result.image
            val newSrc = SkRect.MakeWH(filtered.width.toFloat(), filtered.height.toFloat())
            val innerPaint = paint.copy().apply { this.imageFilter = null }
            drawImageRect(filtered, newSrc, newDevDst, sampling, innerPaint, constraint, clip)
            return
        }

        val ix0 = pixelEdge(devDst.left).coerceAtLeast(clip.left)
        val iy0 = pixelEdge(devDst.top).coerceAtLeast(clip.top)
        val ix1 = pixelEdge(devDst.right).coerceAtMost(clip.right)
        val iy1 = pixelEdge(devDst.bottom).coerceAtMost(clip.bottom)
        if (ix0 >= ix1 || iy0 >= iy1) return

        val devW = devDst.right - devDst.left
        val devH = devDst.bottom - devDst.top
        val srcW = src.right - src.left
        val srcH = src.bottom - src.top
        // Phase G10 — anisotropic sampling : axis-aligned `drawImageRect`
        // has per-axis scale = (srcW / devW) and (srcH / devH). Pick the
        // larger as the **major** ; the N-tap along that axis averages
        // bilinear samples from the mip level that band-limits the
        // **minor** axis. This is the same shortcut the shader path
        // takes — see [SkBitmapShader.sampleAniso8].
        if (sampling.useAniso && image.levelCount() > 1) {
            drawImageRectAniso(image, src, devDst, sampling, paint, constraint, clip, ix0, iy0, ix1, iy1)
            return
        }
        // Phase G10 — mip LOD : when the destination is small enough
        // that the source/dst ratio crosses a power-of-two, snap the
        // input image to the matching mip level and proceed with the
        // existing nearest/linear/bicubic raster path. The
        // [SkBitmapShader] mip selection covers the more general CTM
        // cases ; here we only need axis-aligned scaling.
        if (sampling.mipmap != org.skia.foundation.SkMipmapMode.kNone &&
            image.levelCount() > 1) {
            val rawScaleX = srcW / devW
            val rawScaleY = srcH / devH
            val sMax = kotlin.math.max(rawScaleX, rawScaleY)
            if (sMax > 1f) {
                val level = kotlin.math.floor(
                    kotlin.math.ln(sMax) / kotlin.math.ln(2f)
                ).toInt().coerceIn(0, image.levelCount() - 1)
                if (level > 0) {
                    val mip = imageMipLevel(image, level)
                    val mipScaleX = mip.width.toFloat() / image.width
                    val mipScaleY = mip.height.toFloat() / image.height
                    val mippedSrc = SkRect.MakeLTRB(
                        src.left * mipScaleX, src.top * mipScaleY,
                        src.right * mipScaleX, src.bottom * mipScaleY,
                    )
                    val innerPaint = paint?.copy()
                    drawImageRect(mip, mippedSrc, devDst, sampling, innerPaint, constraint, clip)
                    return
                }
            }
        }
        val scaleX = srcW / devW
        val scaleY = srcH / devH

        val paintAlpha = paint?.alpha ?: 0xFF
        val colorFilter = paint?.colorFilter
        // When [paintAlpha] is 0 the (paint-multiplied) src is fully
        // transparent. We can still short-circuit unless the colour
        // filter would produce a non-trivial output for that
        // transparent input — without an `affectsTransparentBlack`
        // hint on [SkColorFilter] we conservatively skip the
        // short-circuit when a filter is present.
        if (paintAlpha == 0 && colorFilter == null) return
        val mode = paint?.blendMode ?: SkBlendMode.kSrcOver
        val blender = paint?.blender
        val maxX = image.width - 1
        val maxY = image.height - 1
        val strict = constraint == SrcRectConstraint.kStrict
        val strictMinX = strictSampleMin(src.left, maxX)
        val strictMaxX = strictSampleMax(src.right, strictMinX, maxX)
        val strictMinY = strictSampleMin(src.top, maxY)
        val strictMaxY = strictSampleMax(src.bottom, strictMinY, maxY)
        val alphaMaskTint = alphaMaskTintColor(image, paint, colorFilter)

        // [SkImage] currently has no `colorSpace` property, so by convention
        // image pixels are sRGB-encoded (same as SkColor). Phase 7e — when
        // [paint.colorFilter] is set we keep samples in sRGB throughout
        // the alpha-modulate / filter pipeline and xform per-pixel to the
        // device's working space *after* the filter, so the filter math
        // runs in sRGB (the working space Skia tunes its matrix / luma
        // coefficients for). When no filter is set, we keep the fast
        // path : pre-xform the entire image once and sample without
        // further xform inside the loop.
        val needsXform = !xformSteps.flags.isIdentity
        val deferXform = colorFilter != null && needsXform
        val devPixels = if (deferXform || alphaMaskTint != null) image.pixels else imagePixelsInDeviceColorSpace(image)

        // For modes whose formula reduces to a non-`dst` value at sa=0
        // (e.g. kClear, kSrc, kSrcIn, kSrcOut, kDstIn, kDstATop, kModulate),
        // we must still call `blend` on transparent samples — otherwise the
        // covered pixel keeps its dst value instead of being zeroed.
        // Same argument when a colour filter is present : we don't know
        // a priori whether it produces a non-trivial output for transparent
        // input, so we skip the short-circuit.
        val mustBlendZero = modeAffectsZeroAlphaSrc(mode) || colorFilter != null
        val cubic = sampling.cubic
        if (cubic != null) {
            for (py in iy0 until iy1) {
                val srcYf = src.top + (py + 0.5f - devDst.top) * scaleY - 0.5f
                val iyc = floor(srcYf)
                val fy = srcYf - iyc
                val wy0 = SkCubicBC.weight(1f + fy, cubic.B, cubic.C)
                val wy1 = SkCubicBC.weight(fy,       cubic.B, cubic.C)
                val wy2 = SkCubicBC.weight(1f - fy,  cubic.B, cubic.C)
                val wy3 = SkCubicBC.weight(2f - fy,  cubic.B, cubic.C)
                val yMin = if (strict) strictMinY else 0
                val yMax = if (strict) strictMaxY else maxY
                val iy0c = (iyc - 1).coerceIn(yMin, yMax)
                val iy1c = iyc.coerceIn(yMin, yMax)
                val iy2c = (iyc + 1).coerceIn(yMin, yMax)
                val iy3c = (iyc + 2).coerceIn(yMin, yMax)
                for (px in ix0 until ix1) {
                    val srcXf = src.left + (px + 0.5f - devDst.left) * scaleX - 0.5f
                    val ixc = floor(srcXf)
                    val fx = srcXf - ixc
                    val wx0 = SkCubicBC.weight(1f + fx, cubic.B, cubic.C)
                    val wx1 = SkCubicBC.weight(fx,       cubic.B, cubic.C)
                    val wx2 = SkCubicBC.weight(1f - fx,  cubic.B, cubic.C)
                    val wx3 = SkCubicBC.weight(2f - fx,  cubic.B, cubic.C)
                    val xMin = if (strict) strictMinX else 0
                    val xMax = if (strict) strictMaxX else maxX
                    val ix0c = (ixc - 1).coerceIn(xMin, xMax)
                    val ix1c = ixc.coerceIn(xMin, xMax)
                    val ix2c = (ixc + 1).coerceIn(xMin, xMax)
                    val ix3c = (ixc + 2).coerceIn(xMin, xMax)
                    var sample = cubicSampleARGB(
                        devPixels, image.width,
                        ix0c, ix1c, ix2c, ix3c,
                        iy0c, iy1c, iy2c, iy3c,
                        wx0, wx1, wx2, wx3,
                        wy0, wy1, wy2, wy3,
                    )
                    sample = if (alphaMaskTint != null) {
                        applyAlphaMaskTint(sample, alphaMaskTint, paintAlpha)
                    } else {
                        applyAlpha(sample, paintAlpha)
                    }
                    if (colorFilter != null) sample = colorFilter.filterColor(sample)
                    if (deferXform) sample = transformPaintColor(sample)
                    if (sample ushr 24 == 0 && !mustBlendZero) continue
                    dispatchBlend(px, py, sample, mode, blender)
                }
            }
            return
        }
        when (sampling.filter) {
            SkFilterMode.kNearest -> {
                for (py in iy0 until iy1) {
                    val srcYc = src.top + (py + 0.5f - devDst.top) * scaleY
                    val iy = if (strict) {
                        floor(srcYc).coerceIn(strictMinY, strictMaxY)
                    } else {
                        floor(srcYc).coerceIn(0, maxY)
                    }
                    for (px in ix0 until ix1) {
                        val srcXc = src.left + (px + 0.5f - devDst.left) * scaleX
                        val ix = if (strict) {
                            floor(srcXc).coerceIn(strictMinX, strictMaxX)
                        } else {
                            floor(srcXc).coerceIn(0, maxX)
                        }
                        var sample = if (alphaMaskTint != null) {
                            applyAlphaMaskTint(devPixels[iy * image.width + ix], alphaMaskTint, paintAlpha)
                        } else {
                            applyAlpha(devPixels[iy * image.width + ix], paintAlpha)
                        }
                        if (colorFilter != null) sample = colorFilter.filterColor(sample)
                        if (deferXform) sample = transformPaintColor(sample)
                        if (sample ushr 24 == 0 && !mustBlendZero) continue
                        dispatchBlend(px, py, sample, mode, blender)
                    }
                }
            }
            SkFilterMode.kLinear -> {
                for (py in iy0 until iy1) {
                    val srcYf = src.top + (py + 0.5f - devDst.top) * scaleY - 0.5f
                    val iyRaw = floor(srcYf)
                    val iy0i = if (strict) iyRaw.coerceIn(strictMinY, strictMaxY) else iyRaw.coerceIn(0, maxY)
                    val iy1i = if (strict) {
                        (iyRaw + 1).coerceIn(strictMinY, strictMaxY)
                    } else {
                        (iy0i + 1).coerceAtMost(maxY)
                    }
                    val fy = (srcYf - floor(srcYf).toFloat()).coerceIn(0f, 1f)
                    for (px in ix0 until ix1) {
                        val srcXf = src.left + (px + 0.5f - devDst.left) * scaleX - 0.5f
                        val ixRaw = floor(srcXf)
                        val ix0i = if (strict) ixRaw.coerceIn(strictMinX, strictMaxX) else ixRaw.coerceIn(0, maxX)
                        val ix1i = if (strict) {
                            (ixRaw + 1).coerceIn(strictMinX, strictMaxX)
                        } else {
                            (ix0i + 1).coerceAtMost(maxX)
                        }
                        val fx = (srcXf - floor(srcXf).toFloat()).coerceIn(0f, 1f)
                        val c00 = devPixels[iy0i * image.width + ix0i]
                        val c10 = devPixels[iy0i * image.width + ix1i]
                        val c01 = devPixels[iy1i * image.width + ix0i]
                        val c11 = devPixels[iy1i * image.width + ix1i]
                        var sample = if (alphaMaskTint != null) {
                            applyAlphaMaskTint(bilerpARGB(c00, c10, c01, c11, fx, fy), alphaMaskTint, paintAlpha)
                        } else {
                            applyAlpha(bilerpARGB(c00, c10, c01, c11, fx, fy), paintAlpha)
                        }
                        if (colorFilter != null) sample = colorFilter.filterColor(sample)
                        if (deferXform) sample = transformPaintColor(sample)
                        if (sample ushr 24 == 0 && !mustBlendZero) continue
                        dispatchBlend(px, py, sample, mode, blender)
                    }
                }
            }
        }
    }

    /**
     * Bicubic 4×4 convolution. The caller computes the 4 source-x and
     * 4 source-y indices (clamped to the image bounds — the device's
     * raster path is the "kClamp" equivalent of [SkBitmapShader]'s
     * per-axis tile mode) and the 8 kernel weights. Returns an 8-bit
     * ARGB sample with channels clamped to `[0, 255]` to absorb the
     * cubic kernel's negative-lobe overshoot.
     */
    private fun cubicSampleARGB(
        pixels: IntArray, stride: Int,
        ix0: Int, ix1: Int, ix2: Int, ix3: Int,
        iy0: Int, iy1: Int, iy2: Int, iy3: Int,
        wx0: Float, wx1: Float, wx2: Float, wx3: Float,
        wy0: Float, wy1: Float, wy2: Float, wy3: Float,
    ): SkColor {
        var sumA = 0f; var sumR = 0f; var sumG = 0f; var sumB = 0f
        val ys = intArrayOf(iy0, iy1, iy2, iy3)
        val xs = intArrayOf(ix0, ix1, ix2, ix3)
        val wys = floatArrayOf(wy0, wy1, wy2, wy3)
        val wxs = floatArrayOf(wx0, wx1, wx2, wx3)
        var sumW = 0f
        for (j in 0..3) {
            val rowBase = ys[j] * stride
            val wy = wys[j]
            for (i in 0..3) {
                val wt = wxs[i] * wy
                val c = pixels[rowBase + xs[i]]
                sumA += wt * SkColorGetA(c)
                sumR += wt * SkColorGetR(c)
                sumG += wt * SkColorGetG(c)
                sumB += wt * SkColorGetB(c)
                sumW += wt
            }
        }
        val invW = if (sumW != 0f) 1f / sumW else 0f
        val a = (sumA * invW + 0.5f).toInt().coerceIn(0, 255)
        val r = (sumR * invW + 0.5f).toInt().coerceIn(0, 255)
        val g = (sumG * invW + 0.5f).toInt().coerceIn(0, 255)
        val b = (sumB * invW + 0.5f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(a, r, g, b)
    }

    /**
     * Pre-convert all `image` pixels from sRGB into the bitmap's color space.
     * Identity-fast-path returns the underlying buffer when the device is
     * sRGB (no allocation, no per-pixel float work).
     */
    private fun imagePixelsInDeviceColorSpace(image: SkImage): IntArray {
        if (xformSteps.flags.isIdentity) return image.pixels
        val out = IntArray(image.width * image.height)
        for (i in out.indices) out[i] = transformPaintColor(image.pixels[i])
        return out
    }

    /**
     * Phase G10 — return a fresh [SkImage] backed by the requested mip
     * level's pre-rendered pixel buffer. The colour-type metadata of
     * the original image carries through. The returned image has no
     * mip pyramid attached (we already chose the level).
     */
    private fun imageMipLevel(image: SkImage, level: Int): SkImage {
        val levels = image.mipLevels ?: return image
        if (level <= 0 || level >= levels.size) return image
        val lvl = levels[level]
        return SkImage(lvl.width, lvl.height, lvl.pixels, image.colorType)
    }

    /**
     * Phase G10 — `drawImageRect` with [SkSamplingOptions.useAniso].
     * Walks the device-pixel grid like the kLinear branch, but per
     * device pixel takes an N-tap average along the texture-space
     * major axis on the band-limited mip level chosen from the
     * minor-axis footprint. Mirrors [SkBitmapShader.sampleAniso8].
     */
    private fun drawImageRectAniso(
        image: SkImage,
        src: SkRect,
        devDst: SkRect,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        constraint: SrcRectConstraint,
        clip: SkIRect,
        ix0: Int, iy0: Int, ix1: Int, iy1: Int,
    ) {
        val devW = devDst.right - devDst.left
        val devH = devDst.bottom - devDst.top
        val srcW = src.right - src.left
        val srcH = src.bottom - src.top
        val scaleX = srcW / devW
        val scaleY = srcH / devH
        // Major / minor in level-0 source units per device pixel.
        val majorLen = kotlin.math.max(scaleX, scaleY)
        val minorLen = kotlin.math.min(scaleX, scaleY)
        val n = sampling.maxAniso
        val perTap = if (n > 0) majorLen / n.toFloat() else majorLen
        val effective = kotlin.math.max(minorLen, perTap)
        val level = if (effective > 1f) {
            kotlin.math.floor(
                kotlin.math.ln(effective) / kotlin.math.ln(2f)
            ).toInt().coerceIn(0, image.levelCount() - 1)
        } else 0
        val mip = if (level > 0) imageMipLevel(image, level) else image
        val mipScaleX = mip.width.toFloat() / image.width
        val mipScaleY = mip.height.toFloat() / image.height

        val paintAlpha = paint?.alpha ?: 0xFF
        val colorFilter = paint?.colorFilter
        if (paintAlpha == 0 && colorFilter == null) return
        val mode = paint?.blendMode ?: SkBlendMode.kSrcOver
        val blender = paint?.blender
        val alphaMaskTint = alphaMaskTintColor(mip, paint, colorFilter)
        val needsXform = !xformSteps.flags.isIdentity
        val deferXform = colorFilter != null && needsXform
        val devPixels = if (deferXform || alphaMaskTint != null) mip.pixels else imagePixelsInDeviceColorSpace(mip)
        val mustBlendZero = modeAffectsZeroAlphaSrc(mode) || colorFilter != null
        val maxX = mip.width - 1
        val maxY = mip.height - 1
        val strict = constraint == SrcRectConstraint.kStrict
        val strictMinX = strictSampleMin(src.left * mipScaleX, maxX)
        val strictMaxX = strictSampleMax(src.right * mipScaleX, strictMinX, maxX)
        val strictMinY = strictSampleMin(src.top * mipScaleY, maxY)
        val strictMaxY = strictSampleMax(src.bottom * mipScaleY, strictMinY, maxY)

        // The aniso N-tap walks along the **major** axis at each device pixel.
        val majorIsX = scaleX >= scaleY
        for (py in iy0 until iy1) {
            val srcYbase = src.top + (py + 0.5f - devDst.top) * scaleY
            for (px in ix0 until ix1) {
                val srcXbase = src.left + (px + 0.5f - devDst.left) * scaleX
                var sumA = 0f; var sumR = 0f; var sumG = 0f; var sumB = 0f
                for (k in 0 until n) {
                    val t = if (n == 1) 0f else (k.toFloat() / (n - 1)) - 0.5f
                    val sxF: Float
                    val syF: Float
                    if (majorIsX) {
                        sxF = (srcXbase + t * scaleX) * mipScaleX
                        syF = srcYbase * mipScaleY
                    } else {
                        sxF = srcXbase * mipScaleX
                        syF = (srcYbase + t * scaleY) * mipScaleY
                    }
                    // Bilinear sample on the chosen mip level.
                    val xf = sxF - 0.5f
                    val yf = syF - 0.5f
                    val ixRaw = floor(xf)
                    val ix = if (strict) ixRaw.coerceIn(strictMinX, strictMaxX) else ixRaw.coerceIn(0, maxX)
                    val ixn = if (strict) {
                        (ixRaw + 1).coerceIn(strictMinX, strictMaxX)
                    } else {
                        (ix + 1).coerceAtMost(maxX)
                    }
                    val fx = (xf - floor(xf).toFloat()).coerceIn(0f, 1f)
                    val iyRaw = floor(yf)
                    val iy = if (strict) iyRaw.coerceIn(strictMinY, strictMaxY) else iyRaw.coerceIn(0, maxY)
                    val iyn = if (strict) {
                        (iyRaw + 1).coerceIn(strictMinY, strictMaxY)
                    } else {
                        (iy + 1).coerceAtMost(maxY)
                    }
                    val fy = (yf - floor(yf).toFloat()).coerceIn(0f, 1f)
                    val c00 = devPixels[iy * mip.width + ix]
                    val c10 = devPixels[iy * mip.width + ixn]
                    val c01 = devPixels[iyn * mip.width + ix]
                    val c11 = devPixels[iyn * mip.width + ixn]
                    val ifx = 1f - fx; val ify = 1f - fy
                    val w00 = ifx * ify; val w10 = fx * ify
                    val w01 = ifx * fy;  val w11 = fx * fy
                    sumA += SkColorGetA(c00) * w00 + SkColorGetA(c10) * w10 +
                            SkColorGetA(c01) * w01 + SkColorGetA(c11) * w11
                    sumR += SkColorGetR(c00) * w00 + SkColorGetR(c10) * w10 +
                            SkColorGetR(c01) * w01 + SkColorGetR(c11) * w11
                    sumG += SkColorGetG(c00) * w00 + SkColorGetG(c10) * w10 +
                            SkColorGetG(c01) * w01 + SkColorGetG(c11) * w11
                    sumB += SkColorGetB(c00) * w00 + SkColorGetB(c10) * w10 +
                            SkColorGetB(c01) * w01 + SkColorGetB(c11) * w11
                }
                val invN = 1f / n
                val a = (sumA * invN + 0.5f).toInt().coerceIn(0, 255)
                val r = (sumR * invN + 0.5f).toInt().coerceIn(0, 255)
                val g = (sumG * invN + 0.5f).toInt().coerceIn(0, 255)
                val b = (sumB * invN + 0.5f).toInt().coerceIn(0, 255)
                var sample = if (alphaMaskTint != null) {
                    applyAlphaMaskTint(SkColorSetARGB(a, r, g, b), alphaMaskTint, paintAlpha)
                } else {
                    applyAlpha(SkColorSetARGB(a, r, g, b), paintAlpha)
                }
                if (colorFilter != null) sample = colorFilter.filterColor(sample)
                if (deferXform) sample = transformPaintColor(sample)
                if (sample ushr 24 == 0 && !mustBlendZero) continue
                dispatchBlend(px, py, sample, mode, blender)
            }
        }
        // Reference [clip] to keep the parameter visible for future extension
        // (the (ix0/iy0/ix1/iy1) range already incorporates [clip]).
        @Suppress("UNUSED_VARIABLE") val unused = clip
    }

    /** Modulate `src.alpha` by `paintAlpha / 255`, leaving RGB unchanged. */
    private fun applyAlpha(src: SkColor, paintAlpha: Int): SkColor {
        if (paintAlpha == 0xFF) return src
        val sa = SkColorGetA(src)
        val newA = (sa * paintAlpha + 127) / 255
        return (src and 0x00FFFFFF) or (newA shl 24)
    }

    private fun alphaMaskTintColor(image: SkImage, paint: SkPaint?, colorFilter: SkColorFilter?): SkColor? {
        if (image.colorType != SkColorType.kAlpha_8 || paint == null || colorFilter != null) return null
        return transformPaintColor(SkColorSetARGB(0xFF, SkColorGetR(paint.color), SkColorGetG(paint.color), SkColorGetB(paint.color)))
    }

    /** Use an A8 image sample as coverage for the paint colour. */
    private fun applyAlphaMaskTint(maskSample: SkColor, tintColor: SkColor, paintAlpha: Int): SkColor {
        val finalA = (SkColorGetA(maskSample) * paintAlpha + 127) / 255
        return (finalA shl 24) or (tintColor and 0x00FFFFFF)
    }

    /** Bilinear interpolation in non-premultiplied ARGB; matches Skia for opaque samples. */
    private fun bilerpARGB(c00: SkColor, c10: SkColor, c01: SkColor, c11: SkColor, fx: Float, fy: Float): SkColor {
        val ifx = 1f - fx; val ify = 1f - fy
        val w00 = ifx * ify; val w10 = fx * ify; val w01 = ifx * fy; val w11 = fx * fy
        val a = (SkColorGetA(c00) * w00 + SkColorGetA(c10) * w10 + SkColorGetA(c01) * w01 + SkColorGetA(c11) * w11 + 0.5f).toInt().coerceIn(0, 255)
        val r = (SkColorGetR(c00) * w00 + SkColorGetR(c10) * w10 + SkColorGetR(c01) * w01 + SkColorGetR(c11) * w11 + 0.5f).toInt().coerceIn(0, 255)
        val g = (SkColorGetG(c00) * w00 + SkColorGetG(c10) * w10 + SkColorGetG(c01) * w01 + SkColorGetG(c11) * w11 + 0.5f).toInt().coerceIn(0, 255)
        val b = (SkColorGetB(c00) * w00 + SkColorGetB(c10) * w10 + SkColorGetB(c01) * w01 + SkColorGetB(c11) * w11 + 0.5f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(a, r, g, b)
    }

    /**
     * Fill or stroke a path under the current CTM `(sx, sy, tx, ty)`.
     *
     * Phase 3a: kFill_Style with line-only paths and a 4×4 supersampled
     * scanline rasterizer.
     * Phase 3b: full Bézier verbs (quad/conic/cubic) flattened in device
     * space inside [buildEdges] before the scanline walk.
     * Phase 3c: kStroke_Style and kStrokeAndFill_Style — the stroker
     * ([SkStroker]) converts the source-space path into a filled outline
     * path, which is then rasterized via the same fill pipeline. Source
     * space stroke means the stroke width scales with the CTM.
     *
     * The path's verbs are interpreted in source space and transformed via
     * `(sx, sy, tx, ty)` as edges are emitted, so `dev = (sx*x + tx, sy*y + ty)`.
     * Horizontal device-space edges contribute nothing to scanline crossings
     * and are dropped. Each contour ending without `kClose` (the convention
     * `SkPath::Polygon(pts, isClosed=false)` relies on) is implicitly closed
     * by the rasterizer back to its `kMove`.
     */
    override fun drawPath(
        path: SkPath,
        ctm: SkMatrix,
        clip: SkIRect, paint: SkPaint,
    ) {
        // Empty paths are a no-op — except under kInverse* fill rules,
        // where the empty interior means the *entire* clip is filled.
        // We let the scanline walker handle it (extended iteration plus
        // an initial-inside seed for inverse rules; see [scanFillPath]).
        if (path.isEmpty() && !path.fillType.isInverse()) return

        // Phase 7p — apply [SkPaint.pathEffect] before the stroker
        // (canonical Skia order : path → pathEffect → stroker → maskFilter
        // → colorFilter → blend). Dash effects decompose the path into a
        // stipple ; the stroker thickens each segment. When the effect
        // returns an empty path (degenerate intervals etc.) we drop the
        // draw.
        val effectivePath = paint.pathEffect?.filterPath(path, ctm, pathEffectCullRect(clip, ctm, paint)) ?: path
        if (effectivePath !== path && effectivePath.isEmpty() && !effectivePath.fillType.isInverse()) return

        // Phase 7c — when a mask filter (e.g. Gaussian blur) is set,
        // route through the offscreen-mask pipeline instead of the
        // direct rasteriser. The pathEffect-transformed path is what
        // gets rasterised into the mask. J4 — shader paths are now
        // supported : the shader is sampled per-pixel and modulated
        // by the (filtered) mask coverage.
        val maskFilter = paint.maskFilter
        if (maskFilter != null) {
            drawPathWithMaskFilter(effectivePath, ctm, clip, paint, maskFilter)
            return
        }

        val shader = paint.shader
        val color4f: SkColor4f
        val baseA: Int
        if (shader != null) {
            // Shader supplies per-pixel colour. Set it up once for this draw,
            // then the rasterizer modulates each shaded pixel by AA coverage
            // *and* by `paint.alpha` (matches Skia's
            // "shaderColor.alpha *= paint.alpha" semantics — Phase 5g).
            shader.setupForDraw(ctm, xformSteps)
            color4f = SkColor4f(0f, 0f, 0f, 0f)   // unused — per-pixel colour comes from the shader
            baseA = paint.alpha                    // paint.alpha modulator
            if (baseA == 0 && !modeAffectsZeroAlphaSrc(paint.blendMode)) return
        } else {
            // Slice 2.2: float-precision colour-space xform — `setAlphaf`
            // sub-byte precision survives the sRGB→working-space step.
            // Phase 7e: apply [SkPaint.colorFilter] BEFORE the
            // colour-space xform so filter math runs in sRGB (the
            // working space Skia tunes its matrix / luma coefficients
            // for). Pipeline order : `paint.color → colorFilter (sRGB)
            // → xform → blend`.
            color4f = transformPaintColor(applyColorFilter(paint.colorFilter, paint.color4f))
            baseA = (color4f.fA * 255f + 0.5f).toInt().coerceIn(0, 255)
            // Modes that produce a non-trivial output for transparent src
            // (e.g. kClear, kDstIn) must still walk covered pixels even when
            // the paint colour has alpha 0.
            if (baseA == 0 && !modeAffectsZeroAlphaSrc(paint.blendMode)) return
        }
        val supers = if (paint.isAntiAlias) 4 else 1
        val resScale = ctm.computeMaxScale().coerceAtLeast(1f)
        val mode = paint.blendMode
        val blender = paint.blender

        when (paint.style) {
            SkPaint.Style.kFill_Style ->
                fillPath(effectivePath, ctm, clip, color4f, baseA, supers, shader, mode, blender)
            SkPaint.Style.kStroke_Style -> {
                val outline = SkStroker.fromPaint(paint, resScale).stroke(effectivePath)
                if (outline.isEmpty()) return
                fillPath(outline, ctm, clip, color4f, baseA, supers, shader, mode, blender)
            }
            SkPaint.Style.kStrokeAndFill_Style -> {
                fillPath(effectivePath, ctm, clip, color4f, baseA, supers, shader, mode, blender)
                val outline = SkStroker.fromPaint(paint, resScale).stroke(effectivePath)
                if (outline.isEmpty()) return
                fillPath(outline, ctm, clip, color4f, baseA, supers, shader, mode, blender)
            }
        }
    }

    /**
     * Phase 7c — render [path] with a non-null [maskFilter] (currently
     * Gaussian blur via [org.skia.foundation.SkBlurMaskFilter]).
     *
     * Strategy : rasterise the (possibly stroked) path into a temporary
     * 8-bit alpha mask sized at the path's device-space bounds expanded
     * by the filter's [SkMaskFilter.margin]. Apply the filter to that
     * buffer. Then walk the buffer pixel-by-pixel and blend the paint's
     * colour modulated by the mask alpha onto the device.
     *
     * Reuses the existing rasteriser by recursing into a fresh
     * [SkBitmapDevice] over a temporary [SkBitmap]. The recursion
     * guard is the `paint.maskFilter == null` clear we apply to the
     * inner paint, so the inner `drawPath` doesn't re-enter this
     * branch.
     *
     * **Limitations** :
     *  - Inverse fill types fall back to the unfiltered path (the mask
     *    bounds are the *outside* of the path, conceptually unbounded).
     *
     * **J4 — shader support** : when `paint.shader != null`, the shader
     * is set up once for this draw and sampled per-pixel inside the
     * mask. Each device pixel composites as
     * `shader.sample(x,y) × paint.alpha × maskFilteredCoverage`.
     * This is what makes `drawImageRect` (which routes through a
     * shader-bound `drawRect`) honour the paint's mask filter — the
     * filter blurs the *boundary* of the image rect, the shader fills
     * the interior at full coverage.
     */
    private fun drawPathWithMaskFilter(
        path: SkPath, ctm: SkMatrix, clip: SkIRect,
        paint: SkPaint, maskFilter: org.skia.foundation.SkMaskFilter,
    ) {
        if (path.fillType.isInverse()) return  // out of scope for this slice
        val mode = paint.blendMode
        val blender = paint.blender
        val shader = paint.shader
        // Phase 7e — colour filter in sRGB, then xform to working space.
        // For shader paths, the per-pixel colour comes from the shader
        // (already in device working space) ; `effectiveColor` only
        // carries the paint's alpha modulator for those.
        val colorFilter = paint.colorFilter
        val filterMaskPayloadAfterMask = colorFilter
            ?.asBlendModeFilter()
            ?.mode == SkBlendMode.kSrcIn
        val effectiveColor = if (shader == null) {
            if (colorFilter == null) {
                transformPaintColor(paint.color)
            } else {
                transformPaintColor(applyColorFilter(paint.colorFilter, paint.color))
            }
        } else {
            paint.color
        }
        val paintA = SkColorGetA(effectiveColor)
        if (paintA == 0 && !modeAffectsZeroAlphaSrc(mode)) return

        // 1. Path's source-space bounds → device-space bbox. Expand for
        //    stroke (post-CTM) + filter margin + 1 px safety for the AA
        //    rasteriser's edge inflation.
        val srcBounds = path.computeBounds()
        val devBounds = ctm.mapRect(srcBounds)
        val scale = ctm.computeMaxScale().coerceAtLeast(1f)
        // Phase R1-C — if the mask filter ignores the CTM (i.e. its
        // sigma is in source-pixel units), rescale it now so its
        // `margin()` and `filterMask` calls below operate in
        // device-pixel units. When `respectCTM = true` (the default)
        // this is a no-op.
        val effectiveMaskFilter = if (!maskFilter.respectCTM) {
            maskFilter.withCtmScale(scale)
        } else maskFilter
        val strokeExpand = if (paint.style != SkPaint.Style.kFill_Style) {
            (paint.strokeWidth * scale * 0.5f) + 1f
        } else 1f
        val margin = effectiveMaskFilter.margin()
        var ml = floor(devBounds.left - strokeExpand).toInt() - margin
        var mt = floor(devBounds.top - strokeExpand).toInt() - margin
        var mr = ceil(devBounds.right + strokeExpand).toInt() + margin
        var mb = ceil(devBounds.bottom + strokeExpand).toInt() + margin

        // 2. Keep source mask generation independent from the final clip only
        // for active AA clips. A mask filter such as blur can pull coverage
        // from source pixels outside a non-rect clip into pixels that survive
        // it, and clipping the temporary source mask here truncates that
        // contribution before [filterMask] sees it. Rect-only clips keep the
        // established quick-reject behavior covered by BlurQuickRejectGM.
        val preserveOffClipMaskSource = activeAaClip != null
        if (preserveOffClipMaskSource) {
            ml = maxOf(ml, 0)
            mt = maxOf(mt, 0)
            mr = minOf(mr, width)
            mb = minOf(mb, height)
        } else {
            ml = maxOf(ml, clip.left, 0)
            mt = maxOf(mt, clip.top, 0)
            mr = minOf(mr, clip.right, width)
            mb = minOf(mb, clip.bottom, height)
        }
        val maskW = mr - ml
        val maskH = mb - mt
        if (maskW <= 0 || maskH <= 0) return
        val compositeX0 = maxOf(0, clip.left - ml)
        val compositeY0 = maxOf(0, clip.top - mt)
        val compositeX1 = minOf(maskW, clip.right - ml)
        val compositeY1 = minOf(maskH, clip.bottom - mt)
        if (compositeX0 >= compositeX1 || compositeY0 >= compositeY1) return

        // 3. Allocate a transparent 8-bit alpha-target bitmap.
        val maskBitmap = org.skia.foundation.SkBitmap(maskW, maskH).also {
            it.eraseColor(0)
        }
        val maskDevice = SkBitmapDevice(maskBitmap)
        val maskClip = SkIRect.MakeWH(maskW, maskH)

        // 4. Shift the CTM so the path lands at (-ml, -mt) in mask
        //    coords. postTranslate has the closed-form fast path for
        //    affine matrices ; for perspective it falls back to a
        //    full concat.
        val maskCtm = ctm.postTranslate(-ml.toFloat(), -mt.toFloat())

        // 5. Rasterise the path into the mask via WHITE + kSrc + the
        //    paint's stroke params + AA. The recursion guard is the
        //    cleared maskFilter on the inner paint.
        val whitePaint = paint.copy().apply {
            color = org.graphiks.math.SK_ColorWHITE
            blendMode = SkBlendMode.kSrc
            this.maskFilter = null
            this.shader = null
            this.colorFilter = null
            this.pathEffect = null
        }
        maskDevice.drawPath(path, maskCtm, maskClip, whitePaint)

        // 6. Extract alpha mask to a flat ByteArray.
        val srcMask = ByteArray(maskW * maskH)
        for (y in 0 until maskH) {
            val rowOffset = y * maskW
            for (x in 0 until maskW) {
                srcMask[rowOffset + x] =
                    SkColorGetA(maskBitmap.getPixel(x, y)).toByte()
            }
        }

        // 7. Run the mask filter. Single-plane filters (e.g. Gaussian
        //    blur) walk the [Format.kA8] branch ; emboss-class filters
        //    return three planes (alpha + multiply + additive) and
        //    take the [Format.k3D] branch.
        val mustBlendZero = modeAffectsZeroAlphaSrc(mode)
        // J4 — shader set-up : sampled per-pixel inside the composite
        // loop. Per-row scratch buffer reused across all rows.
        if (shader != null) shader.setupForDraw(ctm, xformSteps)
        val shaderRow = if (shader != null) IntArray(maskW) else null
        when (effectiveMaskFilter.format) {
            org.skia.foundation.SkMaskFilter.Format.kA8 -> {
                val blurred = effectiveMaskFilter.filterMask(srcMask, maskW, maskH)
                if (shader != null && shaderRow != null) {
                    for (y in compositeY0 until compositeY1) {
                        val devY = mt + y
                        shader.shadeRow(ml, devY, maskW, shaderRow)
                        for (x in compositeX0 until compositeX1) {
                            val devX = ml + x
                            val maskA = blurred[y * maskW + x].toInt() and 0xFF
                            if (maskA == 0 && !mustBlendZero) continue
                            val s = shaderRow[x]
                            val sA = SkColorGetA(s)
                            // sample.alpha *= paint.alpha * mask
                            val effA = (sA * paintA + 127) / 255
                            val finalA = (effA * maskA + 127) / 255
                            if (finalA == 0 && !mustBlendZero) continue
                            val rgb = s and 0x00FFFFFF
                            dispatchBlend(
                                devX,
                                devY,
                                (finalA shl 24) or rgb,
                                mode,
                                blender,
                                "SkBitmapDevice.drawPathWithMaskFilter.A8.shader",
                            )
                        }
                    }
                } else {
                    val rgb = effectiveColor and 0x00FFFFFF
                    val activeClipTraceBounds = SkCpuWriteChronologyTrace.Bounds(
                        clip.left,
                        clip.top,
                        clip.right,
                        clip.bottom,
                    )
                    val layerTraceBounds = SkCpuWriteChronologyTrace.Bounds(0, 0, width, height)
                    val sourceLayerTraceBounds = SkCpuWriteChronologyTrace.Bounds(ml, mt, mr, mb)
                    fun a8SrcInPayloadTrace(
                        maskLocalX: Int,
                        maskLocalY: Int,
                        blurredMaskAlpha: Int,
                        maskedAlphaBeforeBlend: Int,
                        skipReason: String?,
                    ): SkCpuWriteChronologyTrace.A8SrcInPayloadTrace =
                        SkCpuWriteChronologyTrace.A8SrcInPayloadTrace(
                            maskLocalX = maskLocalX,
                            maskLocalY = maskLocalY,
                            maskOriginLeft = ml,
                            maskOriginTop = mt,
                            maskWidth = maskW,
                            maskHeight = maskH,
                            compositeX0 = compositeX0,
                            compositeY0 = compositeY0,
                            compositeX1 = compositeX1,
                            compositeY1 = compositeY1,
                            blurredMaskAlpha = blurredMaskAlpha,
                            maskedAlphaBeforeBlend = maskedAlphaBeforeBlend,
                            a8SkipReason = skipReason,
                            a8SpanLeft = ml + compositeX0,
                            a8SpanRight = ml + compositeX1,
                            activeClipBounds = activeClipTraceBounds,
                            layerBounds = layerTraceBounds,
                            sourceLayerBounds = sourceLayerTraceBounds,
                        )
                    for (y in compositeY0 until compositeY1) {
                        val devY = mt + y
                        for (x in compositeX0 until compositeX1) {
                            val devX = ml + x
                            val maskA = blurred[y * maskW + x].toInt() and 0xFF
                            if (!filterMaskPayloadAfterMask) {
                                val effA = (paintA * maskA + 127) / 255
                                if (effA == 0 && !mustBlendZero) continue
                                dispatchBlend(
                                    devX,
                                    devY,
                                    (effA shl 24) or rgb,
                                    mode,
                                    blender,
                                    "SkBitmapDevice.drawPathWithMaskFilter.A8.solid",
                                )
                            } else {
                                val baseA = SkColorGetA(paint.color)
                                val maskedA = (baseA * maskA + 127) / 255
                                val traceActive = SkCpuWriteChronologyTrace.shouldTrace(
                                    devX,
                                    devY,
                                    width,
                                    height,
                                )
                                val skipReason = if (maskedA == 0 && !mustBlendZero) {
                                    if (maskA == 0) {
                                        "A8_SRCINPAYLOAD_MASK_ALPHA_ZERO"
                                    } else {
                                        "A8_SRCINPAYLOAD_MASKED_ALPHA_ZERO_BEFORE_BLEND"
                                    }
                                } else {
                                    null
                                }
                                val trace = if (traceActive) {
                                    a8SrcInPayloadTrace(
                                        maskLocalX = x,
                                        maskLocalY = y,
                                        blurredMaskAlpha = maskA,
                                        maskedAlphaBeforeBlend = maskedA,
                                        skipReason = skipReason,
                                    )
                                } else {
                                    null
                                }
                                val traceSrc = if (traceActive) {
                                    transformPaintColor(
                                        applyColorFilter(
                                            colorFilter,
                                            (maskedA shl 24) or (paint.color and 0x00FFFFFF),
                                        ),
                                    )
                                } else {
                                    0
                                }
                                if (trace != null) {
                                    SkCpuWriteChronologyTrace.recordA8SrcInPayloadPreDispatch(
                                        x = devX,
                                        y = devY,
                                        mode = mode,
                                        blender = blender,
                                        valueBefore = bitmap.getPixel(devX, devY),
                                        srcBeforeBlend = traceSrc,
                                        bitmapWidth = width,
                                        bitmapHeight = height,
                                        trace = trace,
                                    )
                                }
                                if (maskedA == 0 && !mustBlendZero) continue
                                val src = if (traceActive) {
                                    traceSrc
                                } else {
                                    transformPaintColor(
                                        applyColorFilter(
                                            colorFilter,
                                            (maskedA shl 24) or (paint.color and 0x00FFFFFF),
                                        ),
                                    )
                                }
                                dispatchBlend(
                                    devX,
                                    devY,
                                    src,
                                    mode,
                                    blender,
                                    "SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload",
                                    a8SrcInPayloadTrace = trace,
                                )
                            }
                        }
                    }
                }
            }
            org.skia.foundation.SkMaskFilter.Format.k3D -> {
                val mask3d = effectiveMaskFilter.filterMask3D(srcMask, maskW, maskH)
                // Composite per pixel : `src.rgb = paint.rgb × multiply / 255 + additive`,
                // `src.a = paint.a × mask.alpha / 255` ; then blend. K2 — when
                // `paint.shader != null`, the per-pixel RGB comes from the
                // shader (sampled here at the same device coordinates the
                // mask covers) instead of `paint.color`.
                if (shader != null && shaderRow != null) {
                    for (y in compositeY0 until compositeY1) {
                        val devY = mt + y
                        shader.shadeRow(ml, devY, maskW, shaderRow)
                        val rowOffset = y * maskW
                        for (x in compositeX0 until compositeX1) {
                            val devX = ml + x
                            val idx = rowOffset + x
                            val maskA = mask3d.alpha[idx].toInt() and 0xFF
                            val s = shaderRow[x]
                            val sA = SkColorGetA(s)
                            // sample.alpha *= paint.alpha * mask
                            val effA = (sA * paintA + 127) / 255
                            val finalA = (effA * maskA + 127) / 255
                            if (finalA == 0 && !mustBlendZero) continue
                            val mul = mask3d.multiply[idx].toInt() and 0xFF
                            val add = mask3d.additive[idx].toInt() and 0xFF
                            val sR = (s ushr 16) and 0xFF
                            val sG = (s ushr 8) and 0xFF
                            val sB = s and 0xFF
                            val r = ((sR * mul + 127) / 255 + add).coerceAtMost(255)
                            val g = ((sG * mul + 127) / 255 + add).coerceAtMost(255)
                            val b = ((sB * mul + 127) / 255 + add).coerceAtMost(255)
                            dispatchBlend(devX, devY, (finalA shl 24) or (r shl 16) or (g shl 8) or b, mode, blender)
                        }
                    }
                } else {
                    val paintR = (effectiveColor ushr 16) and 0xFF
                    val paintG = (effectiveColor ushr 8) and 0xFF
                    val paintB = effectiveColor and 0xFF
                    for (y in compositeY0 until compositeY1) {
                        val devY = mt + y
                        val rowOffset = y * maskW
                        for (x in compositeX0 until compositeX1) {
                            val devX = ml + x
                            val idx = rowOffset + x
                            val maskA = mask3d.alpha[idx].toInt() and 0xFF
                            val effA = (paintA * maskA + 127) / 255
                            if (effA == 0 && !mustBlendZero) continue
                            val mul = mask3d.multiply[idx].toInt() and 0xFF
                            val add = mask3d.additive[idx].toInt() and 0xFF
                            val r = ((paintR * mul + 127) / 255 + add).coerceAtMost(255)
                            val g = ((paintG * mul + 127) / 255 + add).coerceAtMost(255)
                            val b = ((paintB * mul + 127) / 255 + add).coerceAtMost(255)
                            dispatchBlend(devX, devY, (effA shl 24) or (r shl 16) or (g shl 8) or b, mode, blender)
                        }
                    }
                }
            }
        }
    }

    private fun fillPath(
        path: SkPath, ctm: SkMatrix,
        clip: SkIRect, color4f: SkColor4f, baseA: Int, supers: Int,
        shader: SkShader?, mode: SkBlendMode,
        blender: org.skia.foundation.SkBlender? = null,
    ) {
        val edges = buildEdges(path, ctm)
        // No edges + non-inverse fill = nothing to draw. Inverse fills
        // still need to flood the clip (no edges → entire clip is "outside
        // the path" → covered).
        if (edges.isEmpty() && !path.fillType.isInverse()) return
        scanFillPath(edges, path.fillType, clip, color4f, baseA, supers, shader, mode, blender)
    }

    /**
     * Return a [paint] copy with its colour transformed from sRGB into the
     * bitmap's color space and the colour filter applied (when present and
     * the paint has no shader — for shader paths the filter has to be
     * applied per-pixel after the shader runs, see the TODO in
     * [scanFillPath] / [drawPaint] shader branches). Identity-fast-path
     * when no xform and no filter are needed. Slice 2.2: routes through
     * the float overloads so `setAlphaf(x)` precision survives the
     * colour-space xform.
     *
     * Phase 7e — when [SkPaint.colorFilter] is non-null and the paint
     * has no shader, the filter is applied **first** (in sRGB on the
     * un-xformed `color4f`) and the result is then xformed to the
     * device's working space. Pre-7e applied the filter post-xform,
     * which silently re-tuned matrix coefficients (Rec.709 luma
     * weights designed for sRGB) to the destination gamut. The filter
     * slot is cleared on the returned copy so downstream code reads a
     * filter-baked colour and never re-applies the filter.
     */
    private fun inDeviceColorSpace(paint: SkPaint): SkPaint {
        val needsXform = !xformSteps.flags.isIdentity
        val cf = paint.colorFilter
        val solidWithFilter = paint.shader == null && cf != null
        if (!needsXform && !solidWithFilter) return paint
        return paint.copy().also { p ->
            var c = p.color4f
            if (solidWithFilter) {
                c = cf!!.filterColor4f(c)
                p.colorFilter = null  // baked into c (sRGB at this point)
            }
            if (needsXform) c = transformPaintColor(c)
            p.setColor4f(c)
        }
    }

    /**
     * Phase 7a — apply [SkPaint.colorFilter] (if any) to a single
     * source colour in the bitmap's working colour space. Used by
     * `drawPath` and `drawPaint` solid branches that don't go through
     * [inDeviceColorSpace] (those entry points run their own xform
     * inline). Identity-fast-path when [filter] is `null`.
     */
    private fun applyColorFilter(filter: SkColorFilter?, c: SkColor4f): SkColor4f =
        filter?.filterColor4f(c) ?: c

    /** [SkColor]-flavoured counterpart of [applyColorFilter]. */
    private fun applyColorFilter(filter: SkColorFilter?, c: SkColor): SkColor =
        filter?.filterColor(c) ?: c

    /**
     * sRGB-encoded `SkColor` → device-encoded `SkColor`. Short-circuits to
     * identity when the bitmap is sRGB (no float work).
     */
    private fun transformPaintColor(c: SkColor): SkColor {
        if (xformSteps.flags.isIdentity) return c
        val a = SkColorGetA(c)
        val r = SkColorGetR(c)
        val g = SkColorGetG(c)
        val b = SkColorGetB(c)
        val rgba = floatArrayOf(r / 255f, g / 255f, b / 255f, a / 255f)
        xformSteps.apply(rgba)
        val outR = (rgba[0] * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outG = (rgba[1] * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outB = (rgba[2] * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outA = (rgba[3] * 255f + 0.5f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(outA, outR, outG, outB)
    }

    /**
     * Slice 2.2: float-precision counterpart to [transformPaintColor].
     * No byte round-trip — the [SkColor4f] enters and leaves the function
     * as 4 floats, so `setAlphaf(0.3f)` precision is preserved through
     * the colour-space xform.
     */
    private fun transformPaintColor(c: SkColor4f): SkColor4f {
        if (xformSteps.flags.isIdentity) return c
        val rgba = c.vec()
        xformSteps.apply(rgba)
        return SkColor4f(rgba[0], rgba[1], rgba[2], rgba[3])
    }

    /**
     * Phase 6c: working-space `SkColor` (8-bit non-premul ARGB) →
     * premultiplied float quartet `(sr, sg, sb, sa) ∈ [0, 1]`. The colour
     * passed in **must** already be in the bitmap's working colour space
     * (the rasterizer entry points all run their input through
     * [inDeviceColorSpace] / [transformPaintColor] first).
     *
     * Used by the F16 + SrcOver solid-colour fast paths in [fillRectAA],
     * [strokeRectAA] and [scanFillPath]. Calling this once per draw and
     * then applying coverage as a float multiplier lets the rasterizer
     * skip the 8-bit `scaleAlpha(baseA, coverage)` round-to-nearest that
     * would otherwise happen at every covered pixel.
     */
    private fun colorToF16Premul(c: SkColor, out: FloatArray) {
        require(out.size >= 4)
        val a = SkColorGetA(c) / 255f
        out[0] = SkColorGetR(c) / 255f * a
        out[1] = SkColorGetG(c) / 255f * a
        out[2] = SkColorGetB(c) / 255f * a
        out[3] = a
    }

    /**
     * Slice 2.2: float-precision counterpart of [colorToF16Premul]. Reads
     * directly from [SkColor4f] without going through `SkColorGet{R,G,B,A}`,
     * so any sub-byte precision set via `paint.setAlphaf` / `setColor4f`
     * survives all the way to the F16 buffer. Iso with Skia's premul
     * extraction in `SkRasterPipeline_premul`.
     */
    private fun colorToF16Premul(c: SkColor4f, out: FloatArray) {
        require(out.size >= 4)
        val a = c.fA
        out[0] = c.fR * a
        out[1] = c.fG * a
        out[2] = c.fB * a
        out[3] = a
    }

    private fun pathEffectCullRect(clip: SkIRect, ctm: SkMatrix, paint: SkPaint): SkRect? {
        if (!ctm.isScaleTranslate()) return null
        val sx = ctm.getScaleX()
        val sy = ctm.getScaleY()
        if (sx == 0f || sy == 0f) return null

        val halfStroke = if (paint.style == SkPaint.Style.kFill_Style) 0f else paint.strokeWidth * 0.5f
        val padX = halfStroke / kotlin.math.abs(sx) + 1f
        val padY = halfStroke / kotlin.math.abs(sy) + 1f
        val x0 = (clip.left().toFloat() - ctm.getTranslateX()) / sx
        val x1 = (clip.right().toFloat() - ctm.getTranslateX()) / sx
        val y0 = (clip.top().toFloat() - ctm.getTranslateY()) / sy
        val y1 = (clip.bottom().toFloat() - ctm.getTranslateY()) / sy

        return SkRect.MakeLTRB(
            kotlin.math.min(x0, x1) - padX,
            kotlin.math.min(y0, y1) - padY,
            kotlin.math.max(x0, x1) + padX,
            kotlin.math.max(y0, y1) + padY,
        )
    }

    // --------------------------------------------------------------------
    // Non-AA path (Phase 1) — unchanged.
    // --------------------------------------------------------------------

    private fun drawRectNonAA(rect: SkRect, clip: SkIRect, paint: SkPaint) {
        val mode = paint.blendMode
        val blender = paint.blender
        when (paint.style) {
            SkPaint.Style.kFill_Style -> fillRect(rect, clip, paint.color, mode, blender)
            SkPaint.Style.kStroke_Style -> strokeRect(rect, paint.strokeWidth, clip, paint.color, mode, blender)
            SkPaint.Style.kStrokeAndFill_Style -> {
                fillRect(rect, clip, paint.color, mode, blender)
                strokeRect(rect, paint.strokeWidth, clip, paint.color, mode, blender)
            }
        }
    }

    private fun fillRect(
        rect: SkRect, clip: SkIRect, color: SkColor, mode: SkBlendMode,
        blender: org.skia.foundation.SkBlender? = null,
    ) {
        val l = pixelEdge(rect.left).coerceAtLeast(clip.left)
        val t = pixelEdge(rect.top).coerceAtLeast(clip.top)
        val r = pixelEdge(rect.right).coerceAtMost(clip.right)
        val b = pixelEdge(rect.bottom).coerceAtMost(clip.bottom)
        for (y in t until b) {
            for (x in l until r) {
                dispatchBlend(x, y, color, mode, blender)
            }
        }
    }

    private fun strokeRect(
        rect: SkRect, strokeWidth: Float, clip: SkIRect, color: SkColor, mode: SkBlendMode,
        blender: org.skia.foundation.SkBlender? = null,
    ) {
        if (strokeWidth <= 0f) {
            // Hairline: 1px-wide outline. Skia's AA-off hairline snaps the
            // outline to floor-style integer coords (matches `SkScan::HairLineRgn`).
            val l = floor(rect.left)
            val t = floor(rect.top)
            val r = floor(rect.right)
            val b = floor(rect.bottom)
            drawHLine(l, r + 1, t, clip, color, mode, blender)         // top edge
            drawHLine(l, r + 1, b, clip, color, mode, blender)         // bottom edge
            drawVLine(l, t + 1, b, clip, color, mode, blender)         // left edge
            drawVLine(r, t + 1, b, clip, color, mode, blender)         // right edge
            return
        }

        val half = strokeWidth * 0.5f
        val outer = SkRect.MakeLTRB(
            rect.left - half, rect.top - half, rect.right + half, rect.bottom + half
        )
        val inner = SkRect.MakeLTRB(
            rect.left + half, rect.top + half, rect.right - half, rect.bottom - half
        )

        val ol = pixelEdge(outer.left).coerceAtLeast(clip.left)
        val ot = pixelEdge(outer.top).coerceAtLeast(clip.top)
        val or = pixelEdge(outer.right).coerceAtMost(clip.right)
        val ob = pixelEdge(outer.bottom).coerceAtMost(clip.bottom)

        val il = pixelEdge(inner.left)
        val it = pixelEdge(inner.top)
        val ir = pixelEdge(inner.right)
        val ib = pixelEdge(inner.bottom)
        val innerEmpty = il >= ir || it >= ib

        for (y in ot until ob) {
            for (x in ol until or) {
                if (innerEmpty || x < il || x >= ir || y < it || y >= ib) {
                    dispatchBlend(x, y, color, mode, blender)
                }
            }
        }
    }

    // --------------------------------------------------------------------
    // AA path (Phase 2) — analytic axis-aligned coverage.
    //
    // For an axis-aligned rect, per-pixel coverage decomposes into the
    // product of 1-D overlaps along each axis. This is *exact* (no
    // supersampling artefact) and matches Skia's `SkScan::AntiFillRect`
    // closely on integer/fractional axis-aligned boundaries.
    // --------------------------------------------------------------------

    private fun drawRectAA(rect: SkRect, clip: SkIRect, paint: SkPaint) {
        val mode = paint.blendMode
        val blender = paint.blender
        when (paint.style) {
            SkPaint.Style.kFill_Style -> fillRectAA(rect, clip, paint.color4f, mode, blender)
            SkPaint.Style.kStroke_Style -> strokeRectAA(rect, paint.strokeWidth, clip, paint.color4f, mode, blender)
            SkPaint.Style.kStrokeAndFill_Style -> {
                fillRectAA(rect, clip, paint.color4f, mode, blender)
                strokeRectAA(rect, paint.strokeWidth, clip, paint.color4f, mode, blender)
            }
        }
    }

    private fun fillRectAA(
        rect: SkRect, clip: SkIRect, color4f: SkColor4f, mode: SkBlendMode,
        blender: org.skia.foundation.SkBlender? = null,
    ) {
        if (rect.right <= rect.left || rect.bottom <= rect.top) return
        val baseA = (color4f.fA * 255f + 0.5f).toInt().coerceIn(0, 255)
        if (baseA == 0 && !modeAffectsZeroAlphaSrc(mode)) return
        val ix0 = floor(rect.left).coerceAtLeast(clip.left)
        val iy0 = floor(rect.top).coerceAtLeast(clip.top)
        val ix1 = ceil(rect.right).coerceAtMost(clip.right)
        val iy1 = ceil(rect.bottom).coerceAtMost(clip.bottom)

        // Phase 6c — F16 + kSrcOver fast path: keep coverage as a float
        // multiplier all the way to the per-pixel premul-float SrcOver.
        // Eliminates the last 8-bit quantization in front of the F16 buffer
        // (the legacy `scaleAlpha(baseA, cx * cy)` step rounded coverage to
        // 1/255 before blending; now we go straight from the float coverage
        // computation to the F16 store).
        // Slice 2.2: float colour reaches the F16 path without byte
        // round-trip — `setAlphaf(0.3f)` survives end-to-end.
        // Phase 6s — F16 path covers all 29 modes (was: kSrcOver only).
        // Non-srcOver modes route through [blendF16PremulMode].
        // Phase D2.0 — custom blenders fall back to the 8-bit path
        // (the F16 lane only knows SkBlendMode).
        val customBlender = blender != null && blender !is org.skia.foundation.SkBlendModeBlender
        if (bitmap.colorType == org.skia.foundation.SkColorType.kRGBA_F16Norm && !customBlender) {
            val src = FloatArray(4)
            colorToF16Premul(color4f, src)
            val sr = src[0]; val sg = src[1]; val sb = src[2]; val sa = src[3]
            val mustBlendZero = modeAffectsZeroAlphaSrc(mode)
            for (y in iy0 until iy1) {
                val cy = covAxis(rect.top, rect.bottom, y)
                if (cy <= 0f) continue
                for (x in ix0 until ix1) {
                    val cx = covAxis(rect.left, rect.right, x)
                    if (cx <= 0f) continue
                    val cov = cx * cy
                    val saCov = sa * cov
                    if (saCov <= 0f && !mustBlendZero) continue
                    blendF16PremulMode(x, y, sr * cov, sg * cov, sb * cov, saCov, mode)
                }
            }
            return
        }

        // Legacy 8-bit path (also covers the F16 + non-SrcOver case where
        // we'd need a per-mode float dispatch — out of scope for Phase 6c).
        val color = color4f.toSkColor()
        val rgb = color and 0x00FFFFFF
        for (y in iy0 until iy1) {
            val cy = covAxis(rect.top, rect.bottom, y)
            if (cy <= 0f) continue
            for (x in ix0 until ix1) {
                val cx = covAxis(rect.left, rect.right, x)
                if (cx <= 0f) continue
                val effA = scaleAlpha(baseA, cx * cy)
                if (effA == 0) continue
                dispatchBlend(x, y, (effA shl 24) or rgb, mode, blender)
            }
        }
    }

    /**
     * AA stroke = AA fill of (outer rect minus inner rect). Hairline
     * (`strokeWidth <= 0`) renders as a 1-pixel-wide AA frame — for
     * axis-aligned rects this lights up the same pixel set as Skia's
     * `SkScan::AntiHairLineRgn` with matching coverage at half-integer edges.
     */
    private fun strokeRectAA(
        rect: SkRect, strokeWidth: Float, clip: SkIRect, color4f: SkColor4f, mode: SkBlendMode,
        blender: org.skia.foundation.SkBlender? = null,
    ) {
        val w = if (strokeWidth <= 0f) 1f else strokeWidth
        val half = w * 0.5f
        val ol = rect.left - half
        val ot = rect.top - half
        val or = rect.right + half
        val ob = rect.bottom + half
        val il = rect.left + half
        val it = rect.top + half
        val ir = rect.right - half
        val ib = rect.bottom - half
        val innerEmpty = ir <= il || ib <= it
        if (or <= ol || ob <= ot) return
        val baseA = (color4f.fA * 255f + 0.5f).toInt().coerceIn(0, 255)
        if (baseA == 0 && !modeAffectsZeroAlphaSrc(mode)) return
        val ix0 = floor(ol).coerceAtLeast(clip.left)
        val iy0 = floor(ot).coerceAtLeast(clip.top)
        val ix1 = ceil(or).coerceAtMost(clip.right)
        val iy1 = ceil(ob).coerceAtMost(clip.bottom)

        // Phase 6s — F16 path covers all 29 modes (was: kSrcOver only).
        // Phase D2.0 — custom blenders fall back to the 8-bit path
        // (the F16 lane only knows SkBlendMode).
        val customBlender = blender != null && blender !is org.skia.foundation.SkBlendModeBlender
        if (bitmap.colorType == org.skia.foundation.SkColorType.kRGBA_F16Norm && !customBlender) {
            val src = FloatArray(4)
            colorToF16Premul(color4f, src)
            val sr = src[0]; val sg = src[1]; val sb = src[2]; val sa = src[3]
            val mustBlendZero = modeAffectsZeroAlphaSrc(mode)
            for (y in iy0 until iy1) {
                val outerCY = covAxis(ot, ob, y)
                if (outerCY <= 0f) continue
                val innerCY = if (innerEmpty) 0f else covAxis(it, ib, y)
                for (x in ix0 until ix1) {
                    val outerCX = covAxis(ol, or, x)
                    if (outerCX <= 0f) continue
                    val innerCX = if (innerEmpty) 0f else covAxis(il, ir, x)
                    val cov = outerCX * outerCY - innerCX * innerCY
                    if (cov <= 0f) continue
                    val saCov = sa * cov
                    if (saCov <= 0f && !mustBlendZero) continue
                    blendF16PremulMode(x, y, sr * cov, sg * cov, sb * cov, saCov, mode)
                }
            }
            return
        }

        val color = color4f.toSkColor()
        val rgb = color and 0x00FFFFFF
        for (y in iy0 until iy1) {
            val outerCY = covAxis(ot, ob, y)
            if (outerCY <= 0f) continue
            val innerCY = if (innerEmpty) 0f else covAxis(it, ib, y)
            for (x in ix0 until ix1) {
                val outerCX = covAxis(ol, or, x)
                if (outerCX <= 0f) continue
                val innerCX = if (innerEmpty) 0f else covAxis(il, ir, x)
                val cov = outerCX * outerCY - innerCX * innerCY
                if (cov <= 0f) continue
                val effA = scaleAlpha(baseA, cov)
                if (effA == 0) continue
                dispatchBlend(x, y, (effA shl 24) or rgb, mode, blender)
            }
        }
    }

    /** Overlap in pixels between `[lo, hi)` and the unit cell `[pixel, pixel+1)`, clamped to `[0, 1]`. */
    private fun covAxis(lo: Float, hi: Float, pixel: Int): Float {
        val cov = minOf(hi, (pixel + 1).toFloat()) - maxOf(lo, pixel.toFloat())
        return when {
            cov >= 1f -> 1f
            cov <= 0f -> 0f
            else -> cov
        }
    }

    private fun scaleAlpha(baseA: Int, coverage: Float): Int {
        val a = (baseA * coverage + 0.5f).toInt()
        return when {
            a < 0 -> 0
            a > 255 -> 255
            else -> a
        }
    }

    // --------------------------------------------------------------------
    // Path scanline fill (Phase 3a).
    //
    // 4×4 supersampling for AA: for each device-space row `py`, run 4 sub-
    // scanlines at `py + (k + 0.5) / 4` for k in 0..3. At each sub-scanline:
    //
    //   - Find every edge whose device y-range contains the sub-scanline.
    //   - Compute their x crossing at that y.
    //   - Sort crossings left-to-right.
    //   - Walk the sorted crossings, maintaining the winding count, to
    //     enumerate "inside" spans.
    //   - For each span, accumulate sub-pixel x-samples (count of sub-pixel
    //     positions inside) into a row-wide coverage array.
    //
    // After the 4 sub-scanlines, `coverage[x]` lies in `[0, 16]`; the
    // pixel's effective alpha is `baseA * coverage / 16`.
    //
    // Half-open `[y0, y1)` interval prevents double-counting at shared
    // vertices. Even-odd uses `winding & 1` instead of `winding != 0`.
    // --------------------------------------------------------------------

    private data class Edge(
        val x0: Float, val y0: Float,
        val x1: Float, val y1: Float,
        val dir: Int,  // +1 if y0<y1 originally, -1 if y0>y1
    )

    /**
     * Walk the path's verb stream, transform every point to device space
     * via `(sx, sy, tx, ty)`, flatten Bézier curves into line segments,
     * and emit one `Edge` per non-horizontal segment.
     *
     * Curves are flattened in device space to a fixed 0.25-pixel chord
     * tolerance via recursive De Casteljau subdivision (adaptive). Conics
     * fall back to 32-step parametric flattening — they are rare and the
     * uniform stepping keeps the geometry simple.
     */
    private fun buildEdges(
        path: SkPath, ctm: SkMatrix,
    ): List<Edge> {
        if (ctm.hasPerspective()) return buildEdgesPerspective(path, ctm)

        // Cache the matrix scalars in locals to avoid property lookups inside
        // the hot loop. Mapping `(x, y)` to device space is `ctm.mapXY(x, y)`,
        // which expands to `(sx*x + kx*y + tx, ky*x + sy*y + ty)`. Skew terms
        // `kx` and `ky` are zero for axis-aligned matrices (the Phase 0–3 fast
        // path), but the JIT will fold them out when constant-zero anyway.
        val ax = ctm.sx; val bx = ctm.kx; val cx0 = ctm.tx
        val ay = ctm.ky; val by = ctm.sy; val cy0 = ctm.ty
        val out = ArrayList<Edge>(path.verbs.size)
        var px = 0f; var py = 0f          // current device-space point
        var cx = 0f; var cy = 0f          // contour-start device-space point
        var hasContour = false
        var coordIdx = 0
        var weightIdx = 0
        val coords = path.coords
        val weights = path.conicWeights
        for (verb in path.verbs) {
            when (verb) {
                SkPath.Verb.kMove -> {
                    if (hasContour) {
                        addEdge(out, px, py, cx, cy)  // implicit close
                    }
                    val sx0 = coords[coordIdx++]; val sy0 = coords[coordIdx++]
                    val x = ax * sx0 + bx * sy0 + cx0
                    val y = ay * sx0 + by * sy0 + cy0
                    px = x; py = y
                    cx = x; cy = y
                    hasContour = true
                }
                SkPath.Verb.kLine -> {
                    val sx0 = coords[coordIdx++]; val sy0 = coords[coordIdx++]
                    val x = ax * sx0 + bx * sy0 + cx0
                    val y = ay * sx0 + by * sy0 + cy0
                    addEdge(out, px, py, x, y)
                    px = x; py = y
                }
                SkPath.Verb.kQuad -> {
                    val sx1 = coords[coordIdx++]; val sy1 = coords[coordIdx++]
                    val sx2 = coords[coordIdx++]; val sy2 = coords[coordIdx++]
                    val x1 = ax * sx1 + bx * sy1 + cx0; val y1 = ay * sx1 + by * sy1 + cy0
                    val x2 = ax * sx2 + bx * sy2 + cx0; val y2 = ay * sx2 + by * sy2 + cy0
                    flattenQuad(out, px, py, x1, y1, x2, y2, depth = 0)
                    px = x2; py = y2
                }
                SkPath.Verb.kConic -> {
                    val sx1 = coords[coordIdx++]; val sy1 = coords[coordIdx++]
                    val sx2 = coords[coordIdx++]; val sy2 = coords[coordIdx++]
                    val w = weights[weightIdx++]
                    val x1 = ax * sx1 + bx * sy1 + cx0; val y1 = ay * sx1 + by * sy1 + cy0
                    val x2 = ax * sx2 + bx * sy2 + cx0; val y2 = ay * sx2 + by * sy2 + cy0
                    flattenConic(out, px, py, x1, y1, x2, y2, w)
                    px = x2; py = y2
                }
                SkPath.Verb.kCubic -> {
                    val sx1 = coords[coordIdx++]; val sy1 = coords[coordIdx++]
                    val sx2 = coords[coordIdx++]; val sy2 = coords[coordIdx++]
                    val sx3 = coords[coordIdx++]; val sy3 = coords[coordIdx++]
                    val x1 = ax * sx1 + bx * sy1 + cx0; val y1 = ay * sx1 + by * sy1 + cy0
                    val x2 = ax * sx2 + bx * sy2 + cx0; val y2 = ay * sx2 + by * sy2 + cy0
                    val x3 = ax * sx3 + bx * sy3 + cx0; val y3 = ay * sx3 + by * sy3 + cy0
                    flattenCubic(out, px, py, x1, y1, x2, y2, x3, y3, depth = 0)
                    px = x3; py = y3
                }
                SkPath.Verb.kClose -> {
                    if (hasContour) {
                        addEdge(out, px, py, cx, cy)
                        px = cx; py = cy
                        hasContour = false
                    }
                }
                SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
            }
        }
        if (hasContour) addEdge(out, px, py, cx, cy)
        return out
    }

    /**
     * Perspective-aware variant of [buildEdges]. Walks the path's verb
     * stream just like the affine fast path, but every source-space
     * control point is projected through the full 3×3 CTM (homogeneous
     * divide `(x', y') = ((sx*x + kx*y + tx) / w, (ky*x + sy*y + ty) / w)`
     * with `w = persp0*x + persp1*y + persp2`) before being fed to the
     * scanline-edge accumulator or the device-space Bézier flattener.
     *
     * **Béziers under perspective** : projecting the control points of a
     * Bézier curve and then flattening *in device space* is **not** the
     * mathematically-exact projection of the curve — the projected
     * curve's control polygon differs from the projected control polygon
     * of the source curve. Skia upstream subdivides in source space
     * first, projecting each linear sub-chord. We do the simpler "project
     * then flatten" because it's visually-correct for moderate
     * perspective (the curves we care about — circles via 4 cubics, arcs
     * via cubics, etc. — already get aggressively subdivided by the
     * adaptive flattener in device space). For extreme perspective with
     * tiny w-divisors near the vanishing point, this can produce visible
     * deviation ; treat it as best-effort until a source-space
     * subdivision pass is added.
     *
     * Lines (`kMove` / `kLine` / `kClose`) are exact under perspective —
     * the projection of a line through 2 points is the line through the
     * projections.
     */
    private fun buildEdgesPerspective(
        path: SkPath, ctm: SkMatrix,
    ): List<Edge> {
        val ax = ctm.sx; val bx = ctm.kx; val cx0 = ctm.tx
        val ay = ctm.ky; val by = ctm.sy; val cy0 = ctm.ty
        val pa = ctm.persp0; val pb = ctm.persp1; val pc = ctm.persp2
        val out = ArrayList<Edge>(path.verbs.size)
        var px = 0f; var py = 0f
        var cx = 0f; var cy = 0f
        var hasContour = false
        var coordIdx = 0
        var weightIdx = 0
        val coords = path.coords
        val weights = path.conicWeights

        // Local helper — avoids the Pair allocation of `ctm.mapXY`.
        // Returns the projected `(x, y)` via the captured outer-scope
        // mutable holders [outX] / [outY].
        var outX = 0f; var outY = 0f
        fun project(sx: Float, sy: Float) {
            val w = pa * sx + pb * sy + pc
            // Skia clamps `w` away from zero before the divide ; for our
            // path-fill use-case, an effectively-zero w means the point
            // is at the vanishing horizon, so we just pin to a large
            // finite value via 1/eps to avoid `NaN` from `0/0`.
            val invW = if (w == 0f) 0f else 1f / w
            outX = (ax * sx + bx * sy + cx0) * invW
            outY = (ay * sx + by * sy + cy0) * invW
        }

        for (verb in path.verbs) {
            when (verb) {
                SkPath.Verb.kMove -> {
                    if (hasContour) addEdge(out, px, py, cx, cy)
                    project(coords[coordIdx++], coords[coordIdx++])
                    px = outX; py = outY
                    cx = outX; cy = outY
                    hasContour = true
                }
                SkPath.Verb.kLine -> {
                    project(coords[coordIdx++], coords[coordIdx++])
                    addEdge(out, px, py, outX, outY)
                    px = outX; py = outY
                }
                SkPath.Verb.kQuad -> {
                    project(coords[coordIdx++], coords[coordIdx++])
                    val x1 = outX; val y1 = outY
                    project(coords[coordIdx++], coords[coordIdx++])
                    val x2 = outX; val y2 = outY
                    flattenQuad(out, px, py, x1, y1, x2, y2, depth = 0)
                    px = x2; py = y2
                }
                SkPath.Verb.kConic -> {
                    project(coords[coordIdx++], coords[coordIdx++])
                    val x1 = outX; val y1 = outY
                    project(coords[coordIdx++], coords[coordIdx++])
                    val x2 = outX; val y2 = outY
                    val w = weights[weightIdx++]
                    flattenConic(out, px, py, x1, y1, x2, y2, w)
                    px = x2; py = y2
                }
                SkPath.Verb.kCubic -> {
                    project(coords[coordIdx++], coords[coordIdx++])
                    val x1 = outX; val y1 = outY
                    project(coords[coordIdx++], coords[coordIdx++])
                    val x2 = outX; val y2 = outY
                    project(coords[coordIdx++], coords[coordIdx++])
                    val x3 = outX; val y3 = outY
                    flattenCubic(out, px, py, x1, y1, x2, y2, x3, y3, depth = 0)
                    px = x3; py = y3
                }
                SkPath.Verb.kClose -> {
                    if (hasContour) {
                        addEdge(out, px, py, cx, cy)
                        px = cx; py = cy
                        hasContour = false
                    }
                }
                SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
            }
        }
        if (hasContour) addEdge(out, px, py, cx, cy)
        return out
    }

    /** Add a non-horizontal device-space line segment as an oriented `Edge`. */
    private fun addEdge(
        out: MutableList<Edge>,
        dx0: Float, dy0: Float, dx1: Float, dy1: Float,
    ) {
        if (dy0 == dy1) return
        if (dy0 < dy1) out.add(Edge(dx0, dy0, dx1, dy1, +1))
        else out.add(Edge(dx1, dy1, dx0, dy0, -1))
    }

    /**
     * Recursive De Casteljau subdivision of a quadratic Bézier. Stops
     * when the control point is within [PATH_FLATNESS] of the chord, or
     * when [PATH_MAX_DEPTH] subdivisions have been performed (safety
     * bound — typically 4–6 levels suffice).
     */
    private fun flattenQuad(
        out: MutableList<Edge>,
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        depth: Int,
    ) {
        if (depth >= PATH_MAX_DEPTH || quadIsFlat(x0, y0, x1, y1, x2, y2)) {
            addEdge(out, x0, y0, x2, y2)
            return
        }
        val m01x = (x0 + x1) * 0.5f; val m01y = (y0 + y1) * 0.5f
        val m12x = (x1 + x2) * 0.5f; val m12y = (y1 + y2) * 0.5f
        val mx = (m01x + m12x) * 0.5f; val my = (m01y + m12y) * 0.5f
        flattenQuad(out, x0, y0, m01x, m01y, mx, my, depth + 1)
        flattenQuad(out, mx, my, m12x, m12y, x2, y2, depth + 1)
    }

    private fun quadIsFlat(
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
    ): Boolean {
        // Twice the perpendicular distance × |chord| = |cross product|. Comparing
        // squared values avoids a sqrt per call.
        val dx = x2 - x0; val dy = y2 - y0
        val chord2 = dx * dx + dy * dy
        if (chord2 < 1e-12f) return true       // degenerate chord
        val cross = (x1 - x0) * dy - (y1 - y0) * dx
        // distance² = cross² / chord²; flat iff distance ≤ tolerance.
        return (cross * cross) <= PATH_FLATNESS_SQ * chord2
    }

    /**
     * Recursive De Casteljau subdivision of a cubic Bézier. Stops when
     * both control points are within [PATH_FLATNESS] of the chord, or at
     * the depth bound.
     */
    private fun flattenCubic(
        out: MutableList<Edge>,
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        x3: Float, y3: Float,
        depth: Int,
    ) {
        if (depth >= PATH_MAX_DEPTH || cubicIsFlat(x0, y0, x1, y1, x2, y2, x3, y3)) {
            addEdge(out, x0, y0, x3, y3)
            return
        }
        val m01x = (x0 + x1) * 0.5f; val m01y = (y0 + y1) * 0.5f
        val m12x = (x1 + x2) * 0.5f; val m12y = (y1 + y2) * 0.5f
        val m23x = (x2 + x3) * 0.5f; val m23y = (y2 + y3) * 0.5f
        val m012x = (m01x + m12x) * 0.5f; val m012y = (m01y + m12y) * 0.5f
        val m123x = (m12x + m23x) * 0.5f; val m123y = (m12y + m23y) * 0.5f
        val mx = (m012x + m123x) * 0.5f; val my = (m012y + m123y) * 0.5f
        flattenCubic(out, x0, y0, m01x, m01y, m012x, m012y, mx, my, depth + 1)
        flattenCubic(out, mx, my, m123x, m123y, m23x, m23y, x3, y3, depth + 1)
    }

    private fun cubicIsFlat(
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, x3: Float, y3: Float,
    ): Boolean {
        val dx = x3 - x0; val dy = y3 - y0
        val chord2 = dx * dx + dy * dy
        if (chord2 < 1e-12f) return true
        val c1 = (x1 - x0) * dy - (y1 - y0) * dx
        val c2 = (x2 - x0) * dy - (y2 - y0) * dx
        val maxCross2 = maxOf(c1 * c1, c2 * c2)
        return maxCross2 <= PATH_FLATNESS_SQ * chord2
    }

    /**
     * Conic flattening via uniform parametric stepping. Skia uses
     * adaptive splitting based on tangent error; for the path GMs we
     * touch in Phase 3b, 32 steps keep visible chord error well below
     * 0.25 px even at radii of a few hundred pixels.
     */
    private fun flattenConic(
        out: MutableList<Edge>,
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, w: Float,
    ) {
        val n = CONIC_STEPS
        var prevX = x0; var prevY = y0
        for (k in 1..n) {
            val t = k.toFloat() / n
            val u = 1f - t
            val numW = u * u + 2f * u * t * w + t * t
            val numX = u * u * x0 + 2f * u * t * w * x1 + t * t * x2
            val numY = u * u * y0 + 2f * u * t * w * y1 + t * t * y2
            val px = numX / numW; val py = numY / numW
            addEdge(out, prevX, prevY, px, py)
            prevX = px; prevY = py
        }
    }

    private fun scanFillPath(
        edges: List<Edge>, fillType: SkPathFillType, clip: SkIRect,
        color4f: SkColor4f, baseA: Int, supers: Int,
        shader: SkShader?, mode: SkBlendMode,
        blender: org.skia.foundation.SkBlender? = null,
    ) {
        // Lazily computed on entry to the legacy 8-bit path. The F16 fast
        // path consumes [color4f] directly and never pays the byte
        // round-trip — that's the whole point of Slice 2.2.
        val color: SkColor by lazy { color4f.toSkColor() }
        val maxSamples = supers * supers
        // Inverse fills paint the complement of the path's interior,
        // clipped to the device clip. Iteration must therefore cover the
        // *entire* clip vertically — rows above the topmost edge and
        // below the bottommost edge contribute full-coverage spans.
        // Non-inverse fills can stay restricted to the path's y-range
        // (the existing fast path).
        val isInverse = fillType.isInverse()
        val py0: Int
        val py1: Int
        if (isInverse) {
            py0 = clip.top
            py1 = clip.bottom
        } else {
            var yMin = Float.POSITIVE_INFINITY
            var yMax = Float.NEGATIVE_INFINITY
            for (e in edges) {
                if (e.y0 < yMin) yMin = e.y0
                if (e.y1 > yMax) yMax = e.y1
            }
            py0 = floor(yMin).coerceAtLeast(clip.top)
            py1 = ceil(yMax).coerceAtMost(clip.bottom)
        }
        if (py0 >= py1) return
        // Whether the implicit "left of the first crossing" region is
        // already inside the fill set. For inverse rules at winding 0
        // the region is filled (the path's outside); for non-inverse
        // it isn't.
        val initialInside = isInside(0, fillType)
        val clipLeftF = clip.left.toFloat()
        val clipRightF = clip.right.toFloat()
        val rgb = color and 0x00FFFFFF
        val rowWidth = clip.right - clip.left
        if (rowWidth <= 0) return
        val coverage = IntArray(rowWidth)
        val crossX = FloatArray(edges.size)
        val crossDir = IntArray(edges.size)
        // Phase 6b/6c — when the bitmap is F16 *and* the blend mode is
        // SrcOver, the rasterizer takes a float-precision path: float
        // coverage, float-premul source (from a shader's `shadeRowF16` or
        // a once-per-draw `colorToF16Premul` for solid colours), direct
        // compositing into [SkBitmap.pixelsF16] without any byte-
        // quantization step. For other configurations we keep the 8-bit
        // path (and the existing F16 → byte → F16 round-trip in [blend]).
        val isF16 = bitmap.colorType == org.skia.foundation.SkColorType.kRGBA_F16Norm
        // Phase 6s — F16 path now covers all 29 modes (was: kSrcOver only).
        // Non-srcOver modes route through [blendF16PremulMode].
        // Phase D2.0 — custom blenders (anything that's not the
        // SkBlendModeBlender mode-tag wrapper) force the 8-bit path
        // through [dispatchBlend], so the blender's blend() runs
        // through the lossy round-trip rather than the F16 fast lane.
        // The F16 lane only knows about SkBlendMode ; teaching it to
        // run an arbitrary [SkBlender] is a future slice.
        val customBlender = blender != null && blender !is org.skia.foundation.SkBlendModeBlender
        val useF16Path = isF16 && !customBlender
        val useF16ShaderPath = shader != null && useF16Path
        val useF16SolidPath = shader == null && useF16Path
        val mustBlendZero = modeAffectsZeroAlphaSrc(mode)
        val shaderRow: IntArray? = if (shader != null && !useF16ShaderPath) IntArray(rowWidth) else null
        val shaderRowF16: FloatArray? = if (useF16ShaderPath) FloatArray(rowWidth * 4) else null
        val solidF16: FloatArray? = if (useF16SolidPath) FloatArray(4).also { colorToF16Premul(color4f, it) } else null
        val invMaxSamples = 1f / maxSamples.toFloat()

        for (py in py0 until py1) {
            // Reset coverage row.
            for (i in 0 until rowWidth) coverage[i] = 0
            for (k in 0 until supers) {
                val ySub = py + (k + 0.5f) / supers
                var n = 0
                for (e in edges) {
                    if (ySub >= e.y0 && ySub < e.y1) {
                        val t = (ySub - e.y0) / (e.y1 - e.y0)
                        crossX[n] = e.x0 + t * (e.x1 - e.x0)
                        crossDir[n] = e.dir
                        n++
                    }
                }
                // Non-inverse fast path: no edges → no fill in this subrow.
                if (n == 0 && !initialInside) continue
                if (n > 0) sortCrossings(crossX, crossDir, n)
                // Walk crossings, emitting spans where the fill rule says
                // "inside the fill set". For inverse rules `initialInside`
                // is true, so the region left of the first crossing is
                // already filled and we seed `spanStart = clip.left`.
                var winding = 0
                var inside = initialInside
                var spanStart = if (initialInside) clipLeftF else 0f
                for (j in 0 until n) {
                    val before = inside
                    winding += crossDir[j]
                    inside = isInside(winding, fillType)
                    if (!before && inside) {
                        spanStart = crossX[j]
                    } else if (before && !inside) {
                        addSpanCoverage(spanStart, crossX[j], clip, supers, coverage, py, k)
                    }
                }
                // Trailing span — for inverse fills with no crossings (or
                // an even number of them) the right portion of the clip
                // remains in the fill set and needs to be flushed.
                if (inside) {
                    addSpanCoverage(spanStart, clipRightF, clip, supers, coverage, py, k)
                }
            }

            // Phase 6b: F16 shader path — pure float precision end-to-end.
            // shadeRowF16 returns premultiplied floats already in the
            // bitmap's working colour space, coverage modulates as a float
            // multiplier, and [blendF16Premul] composites without ever
            // touching an 8-bit byte. This is the only path that delivers
            // the precision F16 storage promises for gradient draws.
            if (shaderRowF16 != null) {
                shader!!.shadeRowF16(clip.left, py, rowWidth, shaderRowF16)
                // Phase 5g: paint.alpha modulates the shader output (Skia's
                // `shaderColor *= paint.alpha` semantics). Folded into the
                // coverage multiplier so the inner loop stays a single mul.
                val baseAF = baseA / 255f
                for (xOff in 0 until rowWidth) {
                    val samples = coverage[xOff]
                    if (samples == 0) continue
                    val covRaw = if (samples >= maxSamples) 1f else samples * invMaxSamples
                    val cov = covRaw * baseAF
                    val si = xOff * 4
                    val sa = shaderRowF16[si + 3] * cov
                    if (sa <= 0f && !mustBlendZero) continue
                    val sr = shaderRowF16[si]     * cov
                    val sg = shaderRowF16[si + 1] * cov
                    val sb = shaderRowF16[si + 2] * cov
                    blendF16PremulMode(clip.left + xOff, py, sr, sg, sb, sa, mode)
                }
            } else if (solidF16 != null) {
                // Phase 6c / 6s: F16 solid-colour path. Source premul-float
                // is computed once at draw setup (no per-pixel byte → float
                // unpack), coverage stays in float, the blend lands straight
                // in [SkBitmap.pixelsF16] for any of the 29 modes.
                val sr0 = solidF16[0]; val sg0 = solidF16[1]
                val sb0 = solidF16[2]; val sa0 = solidF16[3]
                for (xOff in 0 until rowWidth) {
                    val samples = coverage[xOff]
                    if (samples == 0) continue
                    val cov = if (samples >= maxSamples) 1f else samples * invMaxSamples
                    val sa = sa0 * cov
                    if (sa <= 0f && !mustBlendZero) continue
                    blendF16PremulMode(
                        clip.left + xOff,
                        py,
                        sr0 * cov,
                        sg0 * cov,
                        sb0 * cov,
                        sa,
                        mode,
                        traceSource = "SkBitmapDevice.scanFillPath.solidF16",
                        traceCallsite = "SkBitmapDevice.scanFillPath",
                        tracePaintColor4f = color4f,
                        traceSrcPremulBeforeCoverageF16 = solidF16,
                        traceCoverageSamples = samples,
                        traceCoverageMaxSamples = maxSamples,
                        traceCoverageScale = cov,
                    )
                }
            } else if (shader != null && shaderRow != null) {
                // 8-bit shader path: same code as before (also covers the
                // F16-bitmap-with-non-SrcOver-mode case, where we accept the
                // round-trip rather than expand the F16 mode dispatch).
                // Phase 5g: paint.alpha is folded into the per-pixel
                // alpha modulation as `srcA * samples * baseA / (maxSamples * 255)`.
                shader.shadeRow(clip.left, py, rowWidth, shaderRow)
                for (xOff in 0 until rowWidth) {
                    val samples = coverage[xOff]
                    if (samples == 0) continue
                    val src = shaderRow[xOff]
                    val srcA = SkColorGetA(src)
                    if (srcA == 0) continue
                    val effA = if (samples >= maxSamples && baseA == 255) srcA
                        else (srcA * samples * baseA + (maxSamples * 255) / 2) / (maxSamples * 255)
                    if (effA == 0) continue
                    val srcRgb = src and 0x00FFFFFF
                    dispatchBlend(clip.left + xOff, py, (effA shl 24) or srcRgb, mode, blender)
                }
            } else {
                // Solid-colour path (Phase 1–4).
                for (xOff in 0 until rowWidth) {
                    val samples = coverage[xOff]
                    if (samples == 0) continue
                    val effA = if (samples >= maxSamples) baseA
                        else (baseA * samples + maxSamples / 2) / maxSamples
                    if (effA == 0) continue
                    dispatchBlend(clip.left + xOff, py, (effA shl 24) or rgb, mode, blender)
                }
            }
        }
    }

    private fun sortCrossings(xs: FloatArray, dirs: IntArray, n: Int) {
        // Insertion sort — `n` is typically small (tens) per scanline.
        for (i in 1 until n) {
            val x = xs[i]
            val d = dirs[i]
            var j = i - 1
            while (j >= 0 && xs[j] > x) {
                xs[j + 1] = xs[j]
                dirs[j + 1] = dirs[j]
                j--
            }
            xs[j + 1] = x
            dirs[j + 1] = d
        }
    }

    private fun addSpanCoverage(
        xL: Float,
        xR: Float,
        clip: SkIRect,
        supers: Int,
        coverage: IntArray,
        py: Int,
        subrowY: Int,
    ) {
        if (xR <= xL) return
        val left = xL.coerceAtLeast(clip.left.toFloat())
        val right = xR.coerceAtMost(clip.right.toFloat())
        if (right <= left) return
        val pxStart = floor(left)
        val pxEnd = ceil(right)
        for (px in pxStart until pxEnd) {
            val cellL = maxOf(left, px.toFloat())
            val cellR = minOf(right, (px + 1).toFloat())
            val width = cellR - cellL
            if (width <= 0f) continue
            val samples = (width * supers + 0.5f).toInt().coerceIn(0, supers)
            coverage[px - clip.left] += samples
            SkScanFillPathSubsampleTrace.recordSpanCoverage(
                pixelX = px,
                pixelY = py,
                subrowY = subrowY,
                supers = supers,
                spanLeft = left,
                spanRight = right,
                cellLeft = px.toFloat(),
                cellRight = (px + 1).toFloat(),
                intersectionLeft = cellL,
                intersectionRight = cellR,
                intersectionWidth = width,
                widthTimesSupers = width * supers,
                samplesAddedByScanFillPath = samples,
            )
        }
    }

    private fun isInside(winding: Int, fillType: SkPathFillType): Boolean = when (fillType) {
        SkPathFillType.kWinding -> winding != 0
        SkPathFillType.kEvenOdd -> (winding and 1) != 0
        // Inverse rules fill the *complement* of the corresponding
        // non-inverse rule, clipped to the device clip. The scanline walker
        // only needs the truth value here — the iteration extension is
        // handled in [scanFillPath].
        SkPathFillType.kInverseWinding -> winding == 0
        SkPathFillType.kInverseEvenOdd -> (winding and 1) == 0
    }

    // --------------------------------------------------------------------
    // Hairline / span helpers (Phase 1).
    // --------------------------------------------------------------------

    private fun drawHLine(
        x0: Int, x1: Int, y: Int, clip: SkIRect, color: SkColor, mode: SkBlendMode,
        blender: org.skia.foundation.SkBlender? = null,
    ) {
        if (y < clip.top || y >= clip.bottom) return
        val l = x0.coerceAtLeast(clip.left)
        val r = x1.coerceAtMost(clip.right)
        for (x in l until r) dispatchBlend(x, y, color, mode, blender)
    }

    private fun drawVLine(
        x: Int, y0: Int, y1: Int, clip: SkIRect, color: SkColor, mode: SkBlendMode,
        blender: org.skia.foundation.SkBlender? = null,
    ) {
        if (x < clip.left || x >= clip.right) return
        val t = y0.coerceAtLeast(clip.top)
        val b = y1.coerceAtMost(clip.bottom)
        for (y in t until b) dispatchBlend(x, y, color, mode, blender)
    }

    /**
     * Per-pixel blend dispatch. Combines:
     *  - the **F16 SrcOver fast path** (Phase 6a) when the bitmap is
     *    `kRGBA_F16Norm`, doing premultiplied-float compositing without
     *    any byte-quantization roundtrip,
     *  - the **8-bit SrcOver fast path** (Phase 1) when the bitmap is
     *    `kRGBA_8888` and `mode == kSrcOver`,
     *  - the **generic 9-mode dispatch** (Phase 6 entry) for all other
     *    blend modes.
     *
     * The generic dispatch operates on **non-premultiplied** ARGB inputs
     * and outputs; for F16 bitmaps it reads / writes via the colour-type-
     * aware `getPixel` / `setPixel` accessors, which convert to / from
     * premul float internally. That round-trip costs precision (~1 ulp
     * per channel at fractional alpha) but only for the non-SrcOver modes
     * the GMs in scope barely exercise; full F16 support for the rest of
     * the 9-mode slice is a Phase 6b task.
     */
    private fun tracedSetPixel(
        x: Int,
        y: Int,
        value: SkColor,
        source: String,
        branch: String,
        mode: SkBlendMode,
        blender: org.skia.foundation.SkBlender?,
        coverage: Int,
        srcInput: SkColor,
        srcAfterCoverage: SkColor,
        valueBefore: SkColor? = null,
        a8SrcInPayloadTrace: SkCpuWriteChronologyTrace.A8SrcInPayloadTrace? = null,
    ) {
        if (!SkCpuWriteChronologyTrace.shouldTrace(x, y, width, height)) {
            setPixelWithBitmapDirectTraceSuppressedIfNeeded(x, y, value)
            return
        }
        val before = valueBefore ?: bitmap.getPixel(x, y)
        setPixelWithBitmapDirectTraceSuppressedIfNeeded(x, y, value)
        val after = bitmap.getPixel(x, y)
        SkCpuWriteChronologyTrace.record(
            x = x,
            y = y,
            source = source,
            callsite = "SkBitmapDevice.blend",
            branch = branch,
            mode = mode,
            blender = blender,
            coverage = coverage,
            srcInput = srcInput,
            srcAfterCoverage = srcAfterCoverage,
            valueBefore = before,
            valueWritten = value,
            valueReadAfter = after,
            bitmapWidth = width,
            bitmapHeight = height,
            a8SrcInPayloadTrace = a8SrcInPayloadTrace,
        )
    }

    private fun setPixelWithBitmapDirectTraceSuppressedIfNeeded(x: Int, y: Int, value: SkColor) {
        if (SkCpuWriteChronologyTrace.bitmapDirectWriteTracingActive()) {
            SkCpuWriteChronologyTrace.withBitmapDirectWriteTraceSuppressed {
                bitmap.setPixel(x, y, value)
            }
        } else {
            bitmap.setPixel(x, y, value)
        }
    }

    private fun blend(
        x: Int,
        y: Int,
        srcIn: SkColor,
        mode: SkBlendMode,
        traceSource: String,
        traceBlender: org.skia.foundation.SkBlender?,
        a8SrcInPayloadTrace: SkCpuWriteChronologyTrace.A8SrcInPayloadTrace? = null,
    ) {
        // Phase 7q — clipPath / clipRRect alpha-mask modulation. When a
        // non-rect clip is active we modulate `src.alpha` by the mask
        // coverage at this pixel before any blend dispatch.
        // R-suivi.20 — clipShader coverage composes on top of the AA
        // clip ; we fold both into a single 0..255 effective coverage.
        var cov = 255
        if (activeAaClip != null) cov = clipCoverage(x, y)
        if (cov != 0 && activeClipShader != null) {
            val csCov = clipShaderCoverage(x, y)
            cov = (cov * csCov + 127) / 255
        }
        val src: SkColor = if (cov == 0) {
            if (!modeAffectsZeroAlphaSrc(mode)) {
                if (a8SrcInPayloadTrace != null) {
                    val before = bitmap.getPixel(x, y)
                    SkCpuWriteChronologyTrace.recordA8SrcInPayloadBlendSkip(
                        x = x,
                        y = y,
                        mode = mode,
                        blender = traceBlender,
                        coverage = cov,
                        valueBefore = before,
                        srcInput = srcIn,
                        srcAfterCoverage = SkColorSetARGB(0, 0, 0, 0),
                        bitmapWidth = width,
                        bitmapHeight = height,
                        trace = a8SrcInPayloadTrace.copy(
                            a8SkipReason = "A8_SRCINPAYLOAD_ACTIVE_CLIP_COVERAGE_ZERO",
                        ),
                    )
                }
                return
            }
            SkColorSetARGB(0, 0, 0, 0)
        } else if (cov == 255) {
            srcIn
        } else {
            val newA = (SkColorGetA(srcIn) * cov + 127) / 255
            SkColorSetARGB(newA, SkColorGetR(srcIn), SkColorGetG(srcIn), SkColorGetB(srcIn))
        }

        // Phase 6a — F16 SrcOver fast path.
        if (mode == SkBlendMode.kSrcOver &&
            bitmap.colorType == org.skia.foundation.SkColorType.kRGBA_F16Norm) {
            blendF16(x, y, src)
            return
        }
        // Phase 6s — F16 path for arbitrary blend modes.
        if (bitmap.colorType == org.skia.foundation.SkColorType.kRGBA_F16Norm) {
            val sa = SkColorGetA(src) / 255f
            val sr = SkColorGetR(src) / 255f * sa
            val sg = SkColorGetG(src) / 255f * sa
            val sb = SkColorGetB(src) / 255f * sa
            blendF16PremulMode(x, y, sr, sg, sb, sa, mode)
            return
        }
        // SrcOver fast path — same code as Phase 1 had — preserved bit-for-bit.
        if (mode == SkBlendMode.kSrcOver) {
            val sa = SkColorGetA(src)
            if (sa == 0xFF) {
                tracedSetPixel(
                    x = x,
                    y = y,
                    value = src,
                    source = traceSource,
                    branch = "SkBitmapDevice.blend.kSrcOver.opaqueSrc.setPixel(src)",
                    mode = mode,
                    blender = traceBlender,
                    coverage = cov,
                    srcInput = srcIn,
                    srcAfterCoverage = src,
                    a8SrcInPayloadTrace = a8SrcInPayloadTrace,
                )
                return
            }
            if (sa == 0) {
                if (a8SrcInPayloadTrace != null) {
                    val before = bitmap.getPixel(x, y)
                    SkCpuWriteChronologyTrace.recordA8SrcInPayloadBlendSkip(
                        x = x,
                        y = y,
                        mode = mode,
                        blender = traceBlender,
                        coverage = cov,
                        valueBefore = before,
                        srcInput = srcIn,
                        srcAfterCoverage = src,
                        bitmapWidth = width,
                        bitmapHeight = height,
                        trace = a8SrcInPayloadTrace.copy(
                            a8SkipReason = "A8_SRCINPAYLOAD_SRC_ALPHA_ZERO_AFTER_COVERAGE",
                        ),
                    )
                }
                return
            }
            val dst = bitmap.getPixel(x, y)
            val da = SkColorGetA(dst)
            val invSa = 255 - sa
            val outA = sa + (da * invSa + 127) / 255
            if (outA == 0) {
                tracedSetPixel(
                    x = x,
                    y = y,
                    value = 0,
                    source = traceSource,
                    branch = "SkBitmapDevice.blend.kSrcOver.partialSrc.setPixel(transparent)",
                    mode = mode,
                    blender = traceBlender,
                    coverage = cov,
                    srcInput = srcIn,
                    srcAfterCoverage = src,
                    valueBefore = dst,
                    a8SrcInPayloadTrace = a8SrcInPayloadTrace,
                )
                return
            }
            val sr = SkColorGetR(src); val sg = SkColorGetG(src); val sb = SkColorGetB(src)
            val dr = SkColorGetR(dst); val dg = SkColorGetG(dst); val db = SkColorGetB(dst)
            val outR = (sr * sa + dr * da * invSa / 255 + outA / 2) / outA
            val outG = (sg * sa + dg * da * invSa / 255 + outA / 2) / outA
            val outB = (sb * sa + db * da * invSa / 255 + outA / 2) / outA
            tracedSetPixel(
                x = x,
                y = y,
                value = SkColorSetARGB(outA, outR, outG, outB),
                source = traceSource,
                branch = "SkBitmapDevice.blend.kSrcOver.partialSrc.setPixel(out)",
                mode = mode,
                blender = traceBlender,
                coverage = cov,
                srcInput = srcIn,
                srcAfterCoverage = src,
                valueBefore = dst,
                a8SrcInPayloadTrace = a8SrcInPayloadTrace,
            )
            return
        }

        val dst = bitmap.getPixel(x, y)
        val out = blendPixel(src, dst, mode)
        tracedSetPixel(
            x = x,
            y = y,
            value = out,
            source = traceSource,
            branch = "SkBitmapDevice.blend.${mode.name}.setPixel(out)",
            mode = mode,
            blender = traceBlender,
            coverage = cov,
            srcInput = srcIn,
            srcAfterCoverage = src,
            valueBefore = dst,
            a8SrcInPayloadTrace = a8SrcInPayloadTrace,
        )
    }

    /**
     * Phase D2.0 — paint-aware blend dispatch. Routes between :
     *  - the legacy [blend] fast paths when the paint carries no
     *    custom blender (the common case ; `null` blender or a
     *    [org.skia.foundation.SkBlendModeBlender] tag) ;
     *  - the [blendCustom] generic path when an arbitrary
     *    [org.skia.foundation.SkBlender] is installed.
     *
     * Call sites read `paint.blendMode` and `paint.blender` once at
     * the top of their loop, then call this dispatch per-pixel.
     * The legacy path is preserved bit-iso when `blender == null` ;
     * a `SkBlendModeBlender` is treated as the equivalent
     * [SkBlendMode] (so `paint.blender = SkBlender.Mode(m)` is
     * indistinguishable from `paint.blendMode = m`).
     */
    private fun dispatchBlend(
        x: Int,
        y: Int,
        src: SkColor,
        mode: SkBlendMode,
        blender: org.skia.foundation.SkBlender?,
        traceSource: String = "SkBitmapDevice.dispatchBlend",
        a8SrcInPayloadTrace: SkCpuWriteChronologyTrace.A8SrcInPayloadTrace? = null,
    ) {
        when (blender) {
            null -> blend(x, y, src, mode, traceSource, null, a8SrcInPayloadTrace)
            is org.skia.foundation.SkBlendModeBlender -> {
                blend(x, y, src, blender.mode, traceSource, blender, a8SrcInPayloadTrace)
            }
            else -> blendCustom(x, y, src, blender, traceSource, a8SrcInPayloadTrace)
        }
    }

    /**
     * Generic per-pixel custom blender dispatch. Reads `dst` from
     * the bitmap, converts both `src` and `dst` to unpremul
     * [SkColor4f], runs [org.skia.foundation.SkBlender.blend], and
     * writes the result back via the colour-type-aware setPixel.
     *
     * Lossy round-trip on F16 devices (~1 ulp per channel due to
     * the byte-color → float → byte conversion at the
     * `bitmap.setPixel` boundary) ; acceptable for D2.0 since the
     * GMs unblocked by custom blenders (Arithmetic + future
     * runtime effects) are 8-bit by default. A future slice may
     * specialise an F16 path that keeps full precision.
     *
     * **Clip modulation** : the AA-clip coverage is applied before
     * the blender call, mirroring [blend]'s behaviour exactly.
     */
    private fun blendCustom(
        x: Int,
        y: Int,
        srcIn: SkColor,
        blender: org.skia.foundation.SkBlender,
        traceSource: String,
        a8SrcInPayloadTrace: SkCpuWriteChronologyTrace.A8SrcInPayloadTrace? = null,
    ) {
        // AA-clip + clipShader modulation parity with [blend].
        var cov = 255
        if (activeAaClip != null) cov = clipCoverage(x, y)
        if (cov != 0 && activeClipShader != null) {
            val csCov = clipShaderCoverage(x, y)
            cov = (cov * csCov + 127) / 255
        }
        val srcByte: SkColor = when (cov) {
            // Custom blenders don't get an "affects-zero-alpha-src"
            // shortcut yet — assume they always affect dst until we
            // expose a per-blender flag (parity with upstream's
            // `SkRuntimeEffect.kAlphaUnchanged_Flag` would let us
            // skip when the blender provably preserves alpha).
            0 -> SkColorSetARGB(0, 0, 0, 0)
            255 -> srcIn
            else -> {
                val newA = (SkColorGetA(srcIn) * cov + 127) / 255
                SkColorSetARGB(
                    newA, SkColorGetR(srcIn), SkColorGetG(srcIn), SkColorGetB(srcIn),
                )
            }
        }

        val dstByte = bitmap.getPixel(x, y)
        val src4f = org.graphiks.math.SkColor4f.FromColor(srcByte)
        val dst4f = org.graphiks.math.SkColor4f.FromColor(dstByte)
        val out4f = blender.blend(src4f, dst4f)
        tracedSetPixel(
            x = x,
            y = y,
            value = out4f.toSkColor(),
            source = traceSource,
            branch = "SkBitmapDevice.blendCustom.setPixel(out)",
            mode = SkBlendMode.kSrcOver,
            blender = blender,
            coverage = cov,
            srcInput = srcIn,
            srcAfterCoverage = srcByte,
            valueBefore = dstByte,
            a8SrcInPayloadTrace = a8SrcInPayloadTrace,
        )
    }

    /**
     * Pure function (no bitmap I/O) computing `mode(src, dst)` in
     * non-premultiplied ARGB. Exposed at package level so unit tests can
     * verify blend formulas independently of the rasterizer.
     *
     * SrcOver is included for completeness; the rasterizer keeps an inlined
     * fast path in [blend]. All formulas operate on the full 8-bit ARGB
     * tuple `[a r g b] ∈ [0, 255]`.
     */
    internal fun blendPixel(src: SkColor, dst: SkColor, mode: SkBlendMode): SkColor = when (mode) {
        SkBlendMode.kClear -> 0
        SkBlendMode.kSrc -> src
        SkBlendMode.kDst -> dst
        SkBlendMode.kSrcOver -> blendSrcOver(src, dst)
        SkBlendMode.kDstOver -> blendDstOver(src, dst)
        SkBlendMode.kSrcIn -> blendSrcIn(src, dst)
        SkBlendMode.kDstIn -> blendDstIn(src, dst)
        SkBlendMode.kSrcOut -> blendSrcOut(src, dst)
        SkBlendMode.kDstOut -> blendDstOut(src, dst)
        SkBlendMode.kSrcATop -> blendSrcATop(src, dst)
        SkBlendMode.kDstATop -> blendDstATop(src, dst)
        SkBlendMode.kXor -> blendXor(src, dst)
        SkBlendMode.kPlus -> blendPlus(src, dst)
        SkBlendMode.kModulate -> blendModulate(src, dst)
        SkBlendMode.kScreen -> blendScreen(src, dst)
        SkBlendMode.kMultiply,
        SkBlendMode.kDarken,
        SkBlendMode.kLighten,
        SkBlendMode.kDifference,
        SkBlendMode.kExclusion,
        SkBlendMode.kOverlay,
        SkBlendMode.kHardLight,
        SkBlendMode.kColorDodge,
        SkBlendMode.kColorBurn,
        SkBlendMode.kSoftLight -> blendSeparable(src, dst, mode)
        SkBlendMode.kHue,
        SkBlendMode.kSaturation,
        SkBlendMode.kColor,
        SkBlendMode.kLuminosity -> blendHSL(src, dst, mode)
    }

    // ----- 9-mode slice implementations ---------------------------------
    //
    // All operate on non-premul ARGB tuples. Skia's reference uses premul,
    // so for modes that combine alpha and colour non-trivially (kSrcIn,
    // kDstIn) we mirror the premul formula exactly: pre-multiply src & dst
    // by their alphas, run the formula, post-divide by the result alpha.
    // For modes that don't multiply colour by the *other* operand's alpha
    // (kPlus, kModulate, kScreen) the non-premul formula is bit-equivalent
    // to the premul one when both inputs are fully opaque, with a sub-ulp
    // discrepancy at fractional alpha — same accuracy budget as our
    // existing kSrcOver path.
    // --------------------------------------------------------------------

    private fun blendSrcOver(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src)
        if (sa == 0xFF) return src
        if (sa == 0) return dst
        val da = SkColorGetA(dst)
        val invSa = 255 - sa
        val outA = sa + (da * invSa + 127) / 255
        if (outA == 0) return 0
        val sr = SkColorGetR(src); val sg = SkColorGetG(src); val sb = SkColorGetB(src)
        val dr = SkColorGetR(dst); val dg = SkColorGetG(dst); val db = SkColorGetB(dst)
        val outR = (sr * sa + dr * da * invSa / 255 + outA / 2) / outA
        val outG = (sg * sa + dg * da * invSa / 255 + outA / 2) / outA
        val outB = (sb * sa + db * da * invSa / 255 + outA / 2) / outA
        return SkColorSetARGB(outA, outR, outG, outB)
    }

    /** `r = d + (1-da)*s` — symmetric to SrcOver with src/dst swapped. */
    private fun blendDstOver(src: SkColor, dst: SkColor): SkColor = blendSrcOver(dst, src)

    /**
     * `r = s * da` (premul). In non-premul, this becomes
     * `out.rgb = src.rgb` and `out.alpha = src.alpha * dst.alpha`. The dst
     * colour is replaced by the src colour, masked by dst's alpha.
     */
    private fun blendSrcIn(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src)
        val da = SkColorGetA(dst)
        val outA = (sa * da + 127) / 255
        if (outA == 0) return 0
        return (outA shl 24) or (src and 0x00FFFFFF)
    }

    /**
     * `r = d * sa` (premul). In non-premul, dst colour is preserved and
     * its alpha is masked by src's alpha.
     */
    private fun blendDstIn(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src)
        val da = SkColorGetA(dst)
        val outA = (sa * da + 127) / 255
        if (outA == 0) return 0
        return (outA shl 24) or (dst and 0x00FFFFFF)
    }

    // ----- Phase 6 Porter-Duff completion: kSrcOut, kDstOut, kSrcATop,
    // kDstATop, kXor. Like kSrcIn / kDstIn, alpha is the only thing that
    // really changes for the "Out" pair (RGB of the surviving operand is
    // preserved); ATop/Xor combine both operands' RGB and need a premul
    // round-trip to be exact.
    // --------------------------------------------------------------------

    /**
     * `r = s * (1 - da)` (premul). The visible part of src that lies
     * **outside** the dst. RGB of src is preserved; alpha = `sa*(1-da)`.
     */
    private fun blendSrcOut(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src)
        val da = SkColorGetA(dst)
        val outA = (sa * (255 - da) + 127) / 255
        if (outA == 0) return 0
        return (outA shl 24) or (src and 0x00FFFFFF)
    }

    /**
     * `r = d * (1 - sa)` (premul). The visible part of dst that lies
     * **outside** the src. RGB of dst preserved; alpha = `da*(1-sa)`.
     */
    private fun blendDstOut(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src)
        val da = SkColorGetA(dst)
        val outA = (da * (255 - sa) + 127) / 255
        if (outA == 0) return 0
        return (outA shl 24) or (dst and 0x00FFFFFF)
    }

    /**
     * `r = s * da + d * (1 - sa)` (premul). Source over dst, masked by
     * dst's alpha — paint *inside* the existing dst silhouette only.
     * Result alpha is exactly [da] (the source can never extend dst's
     * silhouette under ATop), and RGB is `lerp(dst, src, sa/255)` — the
     * standard "ATop" formula collapses cleanly when alpha is factored
     * out symbolically.
     */
    private fun blendSrcATop(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src)
        val da = SkColorGetA(dst)
        if (da == 0) return 0
        if (sa == 0) return dst
        if (sa == 0xFF) {
            // Source replaces dst RGB inside dst's silhouette. Alpha = da.
            return (da shl 24) or (src and 0x00FFFFFF)
        }
        val sr = SkColorGetR(src); val sg = SkColorGetG(src); val sb = SkColorGetB(src)
        val dr = SkColorGetR(dst); val dg = SkColorGetG(dst); val db = SkColorGetB(dst)
        val invSa = 255 - sa
        val outR = ((sr * sa + dr * invSa + 127) / 255).coerceIn(0, 255)
        val outG = ((sg * sa + dg * invSa + 127) / 255).coerceIn(0, 255)
        val outB = ((sb * sa + db * invSa + 127) / 255).coerceIn(0, 255)
        return SkColorSetARGB(da, outR, outG, outB)
    }

    /** `r = d * sa + s * (1 - da)` (premul). Symmetric of [blendSrcATop]. */
    private fun blendDstATop(src: SkColor, dst: SkColor): SkColor = blendSrcATop(dst, src)

    /**
     * `r = s * (1 - da) + d * (1 - sa)` (premul). The "exclusive or" of
     * coverage — pixels covered by exactly one of src / dst. Both alphas
     * non-trivial and the RGB combine, so we do the full premul round-
     * trip to stay exact at fractional alpha.
     */
    private fun blendXor(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src); val da = SkColorGetA(dst)
        if (sa == 0) return blendDstOut(src, dst)  // simplifies to dst*(1-sa) when sa=0 ⇒ dst
        if (da == 0) return blendSrcOut(src, dst)  // mirrored
        val sr = SkColorGetR(src); val sg = SkColorGetG(src); val sb = SkColorGetB(src)
        val dr = SkColorGetR(dst); val dg = SkColorGetG(dst); val db = SkColorGetB(dst)
        val invSa = 255 - sa
        val invDa = 255 - da
        // outA = sa*(1-da)/255 + da*(1-sa)/255  (premul)
        val outA = (sa * invDa + da * invSa + 127) / 255
        if (outA == 0) return 0
        // outRgb_premul = sr*sa*(1-da)/255 + dr*da*(1-sa)/255  (each term
        // is the premul colour weighted by the *other* operand's complementary
        // alpha). Un-premul by outA at the end.
        val divisor = outA * 255
        val outR = ((sr * sa * invDa + dr * da * invSa + divisor / 2) / divisor).coerceIn(0, 255)
        val outG = ((sg * sa * invDa + dg * da * invSa + divisor / 2) / divisor).coerceIn(0, 255)
        val outB = ((sb * sa * invDa + db * da * invSa + divisor / 2) / divisor).coerceIn(0, 255)
        return SkColorSetARGB(outA, outR, outG, outB)
    }

    /**
     * `r = min(s + d, 1)` (premul). Used by `ScaledRectsGM` (deferred
     * since Phase 2). Saturating channel-wise add of premultiplied colours.
     *
     * For non-premul inputs this is equivalent when both are opaque (the
     * GM case). For fractional alpha we compute the premul sum then
     * un-premul; alpha is a separate saturating add.
     */
    private fun blendPlus(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src); val da = SkColorGetA(dst)
        if (sa == 0) return dst
        if (da == 0) return src
        if (sa == 0xFF && da == 0xFF) {
            // Common ScaledRects-style case: opaque + opaque, no alpha math.
            val r = (SkColorGetR(src) + SkColorGetR(dst)).coerceAtMost(0xFF)
            val g = (SkColorGetG(src) + SkColorGetG(dst)).coerceAtMost(0xFF)
            val b = (SkColorGetB(src) + SkColorGetB(dst)).coerceAtMost(0xFF)
            return SkColorSetARGB(0xFF, r, g, b)
        }
        val outA = (sa + da).coerceAtMost(0xFF)
        // Fall back to premul math at fractional alpha so the result is
        // exact w.r.t. Skia's pipeline.
        val sr = SkColorGetR(src) * sa; val sg = SkColorGetG(src) * sa; val sb = SkColorGetB(src) * sa
        val dr = SkColorGetR(dst) * da; val dg = SkColorGetG(dst) * da; val db = SkColorGetB(dst) * da
        val pr = (sr + dr).coerceAtMost(255 * 255)
        val pg = (sg + dg).coerceAtMost(255 * 255)
        val pb = (sb + db).coerceAtMost(255 * 255)
        // Un-premultiply by outA. With outA > 0 this is safe.
        val outR = (pr + outA / 2) / outA
        val outG = (pg + outA / 2) / outA
        val outB = (pb + outA / 2) / outA
        return SkColorSetARGB(outA, outR.coerceIn(0, 0xFF), outG.coerceIn(0, 0xFF), outB.coerceIn(0, 0xFF))
    }

    /**
     * `r = s * d` (premul). Skia comment: per-component multiply of
     * **premultiplied** colours. In non-premul ARGB this becomes
     * `out.rgb = (s.rgb * sa) * (d.rgb * da) / out.alpha` with
     * `out.alpha = sa * da`. Equivalent to `out.rgb = s.rgb * d.rgb` only
     * when both alphas are opaque — otherwise dst's alpha leaks into the
     * colour. We keep the formula explicit so the test suite can pin both
     * cases.
     */
    private fun blendModulate(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src); val da = SkColorGetA(dst)
        if (sa == 0 || da == 0) return 0
        if (sa == 0xFF && da == 0xFF) {
            val r = (SkColorGetR(src) * SkColorGetR(dst) + 127) / 255
            val g = (SkColorGetG(src) * SkColorGetG(dst) + 127) / 255
            val b = (SkColorGetB(src) * SkColorGetB(dst) + 127) / 255
            return SkColorSetARGB(0xFF, r, g, b)
        }
        val outA = (sa * da + 127) / 255
        if (outA == 0) return 0
        // Premul colour: (sr * sa) * (dr * da) / 255² gives the premul
        // result; un-premul by outA.
        val sr = SkColorGetR(src); val sg = SkColorGetG(src); val sb = SkColorGetB(src)
        val dr = SkColorGetR(dst); val dg = SkColorGetG(dst); val db = SkColorGetB(dst)
        val pr = sr * sa * dr * da
        val pg = sg * sa * dg * da
        val pb = sb * sa * db * da
        // Divide by 255² then un-premul by outA. Combine into a single
        // (outA * 255²) divisor to keep it integer.
        val divisor = outA * 255 * 255
        val outR = ((pr + divisor / 2) / divisor).coerceIn(0, 0xFF)
        val outG = ((pg + divisor / 2) / divisor).coerceIn(0, 0xFF)
        val outB = ((pb + divisor / 2) / divisor).coerceIn(0, 0xFF)
        return SkColorSetARGB(outA, outR, outG, outB)
    }

    /**
     * `r = s + d - s*d` (premul). With both operands opaque this is the
     * non-premul Screen formula directly. Alpha follows SrcOver.
     */
    private fun blendScreen(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src); val da = SkColorGetA(dst)
        if (sa == 0) return dst
        if (da == 0) return src
        val outA = sa + da - (sa * da + 127) / 255
        if (outA == 0) return 0
        if (sa == 0xFF && da == 0xFF) {
            val sr = SkColorGetR(src); val sg = SkColorGetG(src); val sb = SkColorGetB(src)
            val dr = SkColorGetR(dst); val dg = SkColorGetG(dst); val db = SkColorGetB(dst)
            val r = sr + dr - (sr * dr + 127) / 255
            val g = sg + dg - (sg * dg + 127) / 255
            val b = sb + db - (sb * db + 127) / 255
            return SkColorSetARGB(0xFF, r, g, b)
        }
        // Premul math at fractional alpha: outRGB = sr*sa + dr*da - (sr*sa*dr*da)/255,
        // then un-premul by outA.
        val sr = SkColorGetR(src) * sa; val sg = SkColorGetG(src) * sa; val sb = SkColorGetB(src) * sa
        val dr = SkColorGetR(dst) * da; val dg = SkColorGetG(dst) * da; val db = SkColorGetB(dst) * da
        val pr = sr + dr - (sr * dr + 127 * 255) / (255 * 255)
        val pg = sg + dg - (sg * dg + 127 * 255) / (255 * 255)
        val pb = sb + db - (sb * db + 127 * 255) / (255 * 255)
        val outR = ((pr + outA / 2) / outA).coerceIn(0, 0xFF)
        val outG = ((pg + outA / 2) / outA).coerceIn(0, 0xFF)
        val outB = ((pb + outA / 2) / outA).coerceIn(0, 0xFF)
        return SkColorSetARGB(outA, outR, outG, outB)
    }

    // --------------------------------------------------------------------
    // Phase 6 separable modes (simple): kMultiply, kDarken, kLighten,
    // kDifference, kExclusion. Formulas operate in **premultiplied
    // float** [0, 1] — Skia's reference compositor. Each takes (src,
    // dst) as 8-bit non-premul SkColor, premultiplies internally,
    // computes the per-channel result in float, and stores back as 8-bit
    // non-premul. Output alpha = SrcOver alpha = `sa + da*(1-sa)` for
    // every separable mode.
    //
    // Going through float (instead of integer fixed-point as the Phase 6
    // entry / Porter-Duff completion modes do) keeps the formulas
    // readable and avoids accuracy traps on multi-multiplication chains
    // like kMultiply. The 8-bit accuracy budget (≤ 1 ulp at fractional
    // alpha) is preserved.
    // --------------------------------------------------------------------

    private fun blendSeparable(src: SkColor, dst: SkColor, mode: SkBlendMode): SkColor {
        val sa = SkColorGetA(src) / 255f
        val da = SkColorGetA(dst) / 255f
        val sr = SkColorGetR(src) / 255f * sa
        val sg = SkColorGetG(src) / 255f * sa
        val sb = SkColorGetB(src) / 255f * sa
        val dr = SkColorGetR(dst) / 255f * da
        val dg = SkColorGetG(dst) / 255f * da
        val db = SkColorGetB(dst) / 255f * da

        // SrcOver alpha — shared by every separable mode in Skia.
        val oa = sa + da * (1f - sa)
        if (oa <= 0f) return 0

        // Apply the per-mode per-channel formula to (sX, dX, sa, da).
        val orPm = sepChannel(sr, dr, sa, da, mode)
        val ogPm = sepChannel(sg, dg, sa, da, mode)
        val obPm = sepChannel(sb, db, sa, da, mode)

        // Un-premultiply by oa and quantize.
        val invOa = 1f / oa
        val outA = (oa * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outR = (orPm * invOa * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outG = (ogPm * invOa * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outB = (obPm * invOa * 255f + 0.5f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(outA, outR, outG, outB)
    }

    /**
     * Per-channel separable formula in premul-float `[0, 1]`. Inputs `s`
     * and `d` are already premultiplied (i.e. `unpremul · alpha`); the
     * caller's `sa` / `da` give the operand alphas needed for the modes
     * that subtract `s*da` or `d*sa`.
     *
     * Phase 6 covers all 15 separable modes upstream: 5 simple
     * (kMultiply / kDarken / kLighten / kDifference / kExclusion) and
     * 5 complex (kOverlay / kHardLight / kColorDodge / kColorBurn /
     * kSoftLight). Each formula is the W3C Compositing Level 1 blend
     * function `B(Cb, Cs)` rewritten in premul space, plus the
     * SrcOver-style `(1-sa)*dc + (1-da)*sc` carrier terms.
     */
    private fun sepChannel(s: Float, d: Float, sa: Float, da: Float, mode: SkBlendMode): Float = when (mode) {
        // `rc = (1-sa)*dc + (1-da)*sc + sc*dc` — standard premul Multiply.
        // Note that this is **not** simply `s*d`: that variant ("Modulate"
        // in Skia) discards the (1-sa)*dc and (1-da)*sc terms and is
        // already covered by [blendModulate].
        SkBlendMode.kMultiply -> (1f - sa) * d + (1f - da) * s + s * d
        // `rc = sc + dc - max(sc*da, dc*sa)`. Picks the darker of the two
        // operand colours, weighted by the other's alpha.
        SkBlendMode.kDarken -> s + d - maxOf(s * da, d * sa)
        // `rc = sc + dc - min(sc*da, dc*sa)`.
        SkBlendMode.kLighten -> s + d - minOf(s * da, d * sa)
        // `rc = sc + dc - 2 * min(sc*da, dc*sa)`. Symmetric absolute
        // difference of the two operands' colours; equal colours cancel.
        SkBlendMode.kDifference -> s + d - 2f * minOf(s * da, d * sa)
        // `rc = sc + dc - 2*sc*dc`. Like Difference but doesn't depend on
        // alpha; identical to Screen at sc + dc small, but symmetric.
        SkBlendMode.kExclusion -> s + d - 2f * s * d
        // HardLight: `B(Cb, Cs) = if Cs ≤ 0.5: 2*Cb*Cs else 1-2*(1-Cb)*(1-Cs)`,
        // expressed in premul as a conditional on `2*sc ≤ sa`. Picks
        // either Multiply (when src is dark) or Screen (when src is light)
        // — equivalent to shining a hard spotlight from `s` onto `d`.
        SkBlendMode.kHardLight -> hardLightChannel(s, d, sa, da)
        // Overlay = HardLight with operands swapped (W3C: `Overlay(Cb, Cs)
        // = HardLight(Cs, Cb)`). The conditional moves to `2*dc ≤ da`,
        // but the body terms stay the same — Overlay applies the harder
        // contrast based on the **dst's** brightness.
        SkBlendMode.kOverlay -> hardLightChannel(d, s, da, sa)
        // ColorDodge: brightens dst toward white based on src.
        SkBlendMode.kColorDodge -> colorDodgeChannel(s, d, sa, da)
        // ColorBurn: darkens dst toward black based on src.
        SkBlendMode.kColorBurn -> colorBurnChannel(s, d, sa, da)
        // SoftLight: gentler version of HardLight; never produces fully
        // saturated output. Uses the W3C-canonical Pegtop formulation.
        SkBlendMode.kSoftLight -> softLightChannel(s, d, sa, da)
        else -> error("sepChannel called with non-separable mode: $mode")
    }

    /**
     * HardLight per-channel in premul space:
     * ```
     * B = if 2*sc ≤ sa: 2*sc*dc
     *     else        : sa*da - 2*(da-dc)*(sa-sc)
     * rc = (1-sa)*dc + (1-da)*sc + B
     * ```
     */
    private fun hardLightChannel(s: Float, d: Float, sa: Float, da: Float): Float {
        val carrier = (1f - sa) * d + (1f - da) * s
        val body = if (2f * s <= sa) {
            2f * s * d
        } else {
            sa * da - 2f * (da - d) * (sa - s)
        }
        return carrier + body
    }

    /**
     * ColorDodge per-channel in premul space. Skia's branch structure
     * (matches `SkBlendMode_RasterPipeline.cpp::colorDodge`):
     *
     * ```
     * if dc == 0:                 rc = sc * (1 - da)             // black dst stays black
     * elif sc ≥ sa:               rc = sa * da + sc * (1-da) + dc * (1-sa)
     * else:                       rc = min(da, dc * sa / (sa - sc)) * sa
     *                                  + sc * (1-da) + dc * (1-sa)
     * ```
     *
     * The last branch can produce divide-by-near-zero when `sa - sc` is
     * tiny but non-zero; we keep the literal Skia formulation since the
     * `min(da, …)` clamp absorbs the overflow.
     */
    private fun colorDodgeChannel(s: Float, d: Float, sa: Float, da: Float): Float {
        if (d <= 0f) return s * (1f - da)
        if (s >= sa) return sa * da + s * (1f - da) + d * (1f - sa)
        val ratio = d * sa / (sa - s)
        val n = if (ratio < da) ratio else da
        return n * sa + s * (1f - da) + d * (1f - sa)
    }

    /**
     * ColorBurn per-channel in premul space. Mirror of ColorDodge — burn
     * darkens dst toward black based on src:
     *
     * ```
     * if dc ≥ da:                 rc = sa * da + sc * (1-da) + dc * (1-sa)
     * elif sc ≤ 0:                rc = dc * (1 - sa)
     * else:                       rc = (da - min(da, (da-dc) * sa / sc)) * sa
     *                                  + sc * (1-da) + dc * (1-sa)
     * ```
     */
    private fun colorBurnChannel(s: Float, d: Float, sa: Float, da: Float): Float {
        if (d >= da) return sa * da + s * (1f - da) + d * (1f - sa)
        if (s <= 0f) return d * (1f - sa)
        val ratio = (da - d) * sa / s
        val n = if (ratio < da) ratio else da
        return (da - n) * sa + s * (1f - da) + d * (1f - sa)
    }

    /**
     * SoftLight per-channel in premul space. Direct port of Skia's
     * raster-pipeline implementation (`SkRasterPipeline_opts.h`):
     *
     * ```
     * m  = (da > 0) ? dc / da : 0           // unpremul Cb
     * s2 = 2 * sc                           // premul 2*Cs
     * if 2*sc ≤ sa:                          // dark src
     *     B = dc * (sa + (s2 - sa) * (1 - m))
     * else if 4*dc ≤ da:                     // light src + dark dst
     *     B = dc*sa + da * (s2 - sa) * ((4*dc/da) * (4*dc/da + 1) * (4*dc/da - 1) + 7*dc/da - 1)
     * else:                                  // light src + bright dst
     *     B = dc*sa + da * (s2 - sa) * (sqrt(dc/da) - dc/da)
     * rc = sc * (1 - da) + dc * (1 - sa) + B
     * ```
     *
     * The middle branch's `4*dc/da * (4*dc/da + 1) * (4*dc/da - 1) + 7*dc/da - 1`
     * is a cubic polynomial in `m = dc/da` that approximates `m^3 - 4*m`-ish
     * shape used by the W3C Pegtop softlight. We keep it as written.
     */
    private fun softLightChannel(s: Float, d: Float, sa: Float, da: Float): Float {
        val carrier = s * (1f - da) + d * (1f - sa)
        if (2f * s <= sa) {
            // Dark src: pull dst toward black proportional to (sa - 2*sc).
            val m = if (da > 0f) d / da else 0f
            val body = d * (sa + (2f * s - sa) * (1f - m))
            return carrier + body
        }
        // Light src — two sub-branches based on dst brightness.
        val m = if (da > 0f) d / da else 0f
        val correction = if (4f * d <= da) {
            // Dark dst: cubic approximation.
            val mm = 4f * m  // 4 * dc/da
            mm * (mm + 1f) * (mm - 1f) + 7f * m - 1f
        } else {
            // Bright dst: sqrt-based.
            val sqm = kotlin.math.sqrt(m)
            sqm - m
        }
        val body = d * sa + da * (2f * s - sa) * correction
        return carrier + body
    }

    // --------------------------------------------------------------------
    // Phase 6 HSL: kHue, kSaturation, kColor, kLuminosity. These modes
    // operate on the whole RGB tuple at once (not per-channel), so they
    // need their own dispatcher that doesn't fit the [sepChannel] shape.
    //
    // Formulas (W3C Compositing Level 1, in non-premul):
    //   Hue       (Cs, Cb) = SetLum(SetSat(Cs, Sat(Cb)), Lum(Cb))
    //   Saturation(Cs, Cb) = SetLum(SetSat(Cb, Sat(Cs)), Lum(Cb))
    //   Color     (Cs, Cb) = SetLum(Cs, Lum(Cb))
    //   Luminosity(Cs, Cb) = SetLum(Cb, Lum(Cs))
    //
    // Skia's premul implementation scales the operands by the *other*
    // operand's alpha so both work in `[0, sa*da]`, applies the formula,
    // and uses `sa*da` as the upper clip bound. The result is the B body
    // in premul space; carrier = `sc*(1-da) + dc*(1-sa)` is added after.
    // --------------------------------------------------------------------

    private fun blendHSL(src: SkColor, dst: SkColor, mode: SkBlendMode): SkColor {
        val sa = SkColorGetA(src) / 255f
        val da = SkColorGetA(dst) / 255f
        val sr = SkColorGetR(src) / 255f * sa
        val sg = SkColorGetG(src) / 255f * sa
        val sb = SkColorGetB(src) / 255f * sa
        val dr = SkColorGetR(dst) / 255f * da
        val dg = SkColorGetG(dst) / 255f * da
        val db = SkColorGetB(dst) / 255f * da

        val oa = sa + da * (1f - sa)
        if (oa <= 0f) return 0

        // Scale src by da and dst by sa so both live in `[0, sa*da]`.
        val a = sa * da
        val srA = sr * da; val sgA = sg * da; val sbA = sb * da
        val drA = dr * sa; val dgA = dg * sa; val dbA = db * sa

        // Compute the B body for each HSL mode in `[0, sa*da]` space.
        val body = FloatArray(3)
        when (mode) {
            SkBlendMode.kHue -> {
                // SetLum(SetSat(Cs', Sat(Cb')), a, Lum(Cb')).
                body[0] = srA; body[1] = sgA; body[2] = sbA
                setSat(body, sat3(drA, dgA, dbA))
                setLum(body, a, lum3(drA, dgA, dbA))
            }
            SkBlendMode.kSaturation -> {
                // SetLum(SetSat(Cb', Sat(Cs')), a, Lum(Cb')).
                body[0] = drA; body[1] = dgA; body[2] = dbA
                setSat(body, sat3(srA, sgA, sbA))
                setLum(body, a, lum3(drA, dgA, dbA))
            }
            SkBlendMode.kColor -> {
                // SetLum(Cs', a, Lum(Cb')).
                body[0] = srA; body[1] = sgA; body[2] = sbA
                setLum(body, a, lum3(drA, dgA, dbA))
            }
            SkBlendMode.kLuminosity -> {
                // SetLum(Cb', a, Lum(Cs')).
                body[0] = drA; body[1] = dgA; body[2] = dbA
                setLum(body, a, lum3(srA, sgA, sbA))
            }
            else -> error("blendHSL called with non-HSL mode: $mode")
        }

        // Add the SrcOver-style carrier: oc = sc*(1-da) + dc*(1-sa) + B.
        val orPm = sr * (1f - da) + dr * (1f - sa) + body[0]
        val ogPm = sg * (1f - da) + dg * (1f - sa) + body[1]
        val obPm = sb * (1f - da) + db * (1f - sa) + body[2]

        val invOa = 1f / oa
        val outA = (oa * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outR = (orPm * invOa * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outG = (ogPm * invOa * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outB = (obPm * invOa * 255f + 0.5f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(outA, outR, outG, outB)
    }

    /** Luminance: `0.3*R + 0.59*G + 0.11*B` (Skia's coefficients). */
    private fun lum3(r: Float, g: Float, b: Float): Float =
        r * 0.3f + g * 0.59f + b * 0.11f

    /** Saturation: `max(R, G, B) - min(R, G, B)` (channel-spread). */
    private fun sat3(r: Float, g: Float, b: Float): Float =
        maxOf(r, maxOf(g, b)) - minOf(r, minOf(g, b))

    /**
     * In-place: shift `rgb`'s luminance to [newLum] (uniform additive shift),
     * then [clipColor] back into `[0, alpha]` while preserving the new
     * luminance. Used by every HSL mode to lock the result's luminance to
     * either dst's (Hue/Saturation/Color) or src's (Luminosity).
     */
    private fun setLum(rgb: FloatArray, alpha: Float, newLum: Float) {
        val diff = newLum - lum3(rgb[0], rgb[1], rgb[2])
        rgb[0] += diff
        rgb[1] += diff
        rgb[2] += diff
        clipColor(rgb, alpha)
    }

    /**
     * In-place: scale `rgb`'s spread to [newSat] while preserving channel
     * order. The smallest channel collapses to 0, the largest becomes
     * `newSat`, and the middle scales proportionally — same behaviour as
     * the W3C `SetSat` algorithm without the explicit min/mid/max sort
     * (since the same factor applied to `(value - min)` gives the right
     * answer regardless of ordering).
     */
    private fun setSat(rgb: FloatArray, newSat: Float) {
        val r = rgb[0]; val g = rgb[1]; val b = rgb[2]
        val mn = minOf(r, minOf(g, b))
        val mx = maxOf(r, maxOf(g, b))
        val s = mx - mn
        if (s > 0f) {
            val factor = newSat / s
            rgb[0] = (r - mn) * factor
            rgb[1] = (g - mn) * factor
            rgb[2] = (b - mn) * factor
        } else {
            rgb[0] = 0f; rgb[1] = 0f; rgb[2] = 0f
        }
    }

    /**
     * In-place: clip `rgb` into `[0, alpha]` while preserving its
     * luminance. If a channel underflows to negative, we pull all three
     * toward `lum`; if a channel overflows past `alpha`, we push all
     * three toward `lum`. Mirrors Skia's `SkBlendMode_RasterPipeline.cpp::clipColor`.
     */
    private fun clipColor(rgb: FloatArray, alpha: Float) {
        val l = lum3(rgb[0], rgb[1], rgb[2])
        var r = rgb[0]; var g = rgb[1]; var b = rgb[2]
        val mn = minOf(r, minOf(g, b))
        val mx = maxOf(r, maxOf(g, b))
        if (mn < 0f) {
            val denom = l - mn
            val factor = if (denom > 0f) l / denom else 0f
            r = l + (r - l) * factor
            g = l + (g - l) * factor
            b = l + (b - l) * factor
        }
        if (mx > alpha) {
            val denom = mx - l
            val factor = if (denom > 0f) (alpha - l) / denom else 0f
            r = l + (r - l) * factor
            g = l + (g - l) * factor
            b = l + (b - l) * factor
        }
        rgb[0] = r; rgb[1] = g; rgb[2] = b
    }

    /**
     * Source-Over compositing in **premultiplied float** (`F16Norm` path).
     * Both src and dst are kept in `[0, 1]` premultiplied space, so the
     * formula reduces to `out = src + dst * (1 − srcA)` per channel — no
     * unpremul / repremul roundtrip and no byte quantization until the
     * pixel ultimately leaves the bitmap (PNG output).
     */
    private fun blendF16(x: Int, y: Int, src: SkColor) {
        var sa = SkColorGetA(src)
        if (sa == 0) return
        // Phase 7q — clip-mask alpha modulation. R-suivi.20 — and the
        // clip-shader coverage on top of it.
        var cov = 255
        if (activeAaClip != null) cov = clipCoverage(x, y)
        if (cov != 0 && activeClipShader != null) {
            val csCov = clipShaderCoverage(x, y)
            cov = (cov * csCov + 127) / 255
        }
        if (cov != 255) {
            if (cov == 0) return
            sa = (sa * cov + 127) / 255
            if (sa == 0) return
        }
        val saF = sa / 255f
        val srF = SkColorGetR(src) / 255f * saF
        val sgF = SkColorGetG(src) / 255f * saF
        val sbF = SkColorGetB(src) / 255f * saF

        val pixels = bitmap.pixelsF16
        val i = (y * bitmap.width + x) * 4
        val invSa = 1f - saF
        val outR = srF + pixels[i] * invSa
        val outG = sgF + pixels[i + 1] * invSa
        val outB = sbF + pixels[i + 2] * invSa
        val outA = saF + pixels[i + 3] * invSa
        pixels[i] = outR
        pixels[i + 1] = outG
        pixels[i + 2] = outB
        pixels[i + 3] = outA
    }

    /**
     * Phase 6b — pure premul-float SrcOver. The src tuple is already in
     * `[0, 1]` premultiplied (coverage already folded in by the caller),
     * matching the bitmap's storage convention exactly. No byte conversion
     * happens anywhere in this function — the only multiplications are the
     * two `[0, 1]` premul lerps.
     */
    private fun blendF16Premul(x: Int, y: Int, srIn: Float, sgIn: Float, sbIn: Float, saIn: Float) {
        if (saIn <= 0f) return
        // Phase 7q — clip-mask coverage modulation in premul-float.
        // R-suivi.20 — fold the clip-shader coverage on top.
        var cov = 255
        if (activeAaClip != null) cov = clipCoverage(x, y)
        if (cov != 0 && activeClipShader != null) {
            val csCov = clipShaderCoverage(x, y)
            cov = (cov * csCov + 127) / 255
        }
        val sr: Float; val sg: Float; val sb: Float; val sa: Float
        if (cov == 255) {
            sr = srIn; sg = sgIn; sb = sbIn; sa = saIn
        } else {
            if (cov == 0) return
            val k = cov / 255f
            sr = srIn * k; sg = sgIn * k; sb = sbIn * k; sa = saIn * k
            if (sa <= 0f) return
        }
        val pixels = bitmap.pixelsF16
        val i = (y * bitmap.width + x) * 4
        val invSa = 1f - sa
        pixels[i]     = sr + pixels[i]     * invSa
        pixels[i + 1] = sg + pixels[i + 1] * invSa
        pixels[i + 2] = sb + pixels[i + 2] * invSa
        pixels[i + 3] = sa + pixels[i + 3] * invSa
    }

    /**
     * Phase 6s — premul-float blend for arbitrary [SkBlendMode]. Same
     * input contract as [blendF16Premul] (premultiplied float `[0, 1]`
     * src) plus a `mode` selector. The result lands directly in the
     * F16 buffer — no intermediate quantization.
     *
     * For [SkBlendMode.kSrcOver] this delegates to the specialised
     * [blendF16Premul] which is one branch shorter ; every other
     * mode falls through the unified `when` here.
     *
     * Porter-Duff and the simple combinators (Plus / Modulate / Screen)
     * are inlined as 4-line cases. The 10 separable modes share the
     * existing pure-float [sepChannel] implementation (originally
     * factored out for the 8-bit path — same code, no quantization).
     * The 4 HSL modes delegate to [blendHSLF16Body] which mirrors
     * [blendHSL]'s pre-existing float-premul body.
     *
     * The caller is responsible for early-exiting on `sa == 0` when
     * the mode does not affect zero-alpha sources (cf.
     * [modeAffectsZeroAlphaSrc]). We do **not** short-circuit here
     * because some callers (notably saveLayer-with-blendmode) need
     * the zero-src path to zero out the destination.
     */
    private fun blendF16PremulMode(
        x: Int, y: Int,
        srIn: Float, sgIn: Float, sbIn: Float, saIn: Float,
        mode: SkBlendMode,
        traceSource: String = "SkBitmapDevice.blendF16PremulMode",
        traceCallsite: String = "SkBitmapDevice.blendF16PremulMode",
        tracePaintColor4f: SkColor4f? = null,
        traceSrcPremulBeforeCoverageF16: FloatArray? = null,
        traceCoverageSamples: Int? = null,
        traceCoverageMaxSamples: Int? = null,
        traceCoverageScale: Float? = null,
    ) {
        // Phase 7q — clip-mask modulation in premul-float space.
        // R-suivi.20 — fold the clip-shader coverage on top.
        var cov = 255
        if (activeAaClip != null) cov = clipCoverage(x, y)
        if (cov != 0 && activeClipShader != null) {
            val csCov = clipShaderCoverage(x, y)
            cov = (cov * csCov + 127) / 255
        }
        val sr: Float; val sg: Float; val sb: Float; val sa: Float
        if (cov == 0) {
            if (!modeAffectsZeroAlphaSrc(mode)) return
            sr = 0f; sg = 0f; sb = 0f; sa = 0f
        } else if (cov == 255) {
            sr = srIn; sg = sgIn; sb = sbIn; sa = saIn
        } else {
            val k = cov / 255f
            sr = srIn * k; sg = sgIn * k; sb = sbIn * k; sa = saIn * k
        }
        if (mode == SkBlendMode.kSrcOver) {
            // Inline kSrcOver here ; we MUST NOT recurse into
            // [blendF16Premul] because that path would re-apply the
            // clip-mask coverage to the already-modulated `sa` (double-
            // multiplying the coverage). The body below is identical to
            // [blendF16Premul] sans the mask check.
            if (sa <= 0f) return
            val pixels = bitmap.pixelsF16
            val i = (y * bitmap.width + x) * 4
            val trace = SkCpuWriteChronologyTrace.shouldTrace(x, y, width, height)
            val beforeF16 = if (trace) {
                floatArrayOf(pixels[i], pixels[i + 1], pixels[i + 2], pixels[i + 3])
            } else {
                null
            }
            val invSa = 1f - sa
            pixels[i]     = sr + pixels[i]     * invSa
            pixels[i + 1] = sg + pixels[i + 1] * invSa
            pixels[i + 2] = sb + pixels[i + 2] * invSa
            pixels[i + 3] = sa + pixels[i + 3] * invSa
            if (trace && beforeF16 != null) {
                SkCpuWriteChronologyTrace.recordF16PremulStore(
                    x = x,
                    y = y,
                    source = traceSource,
                    callsite = traceCallsite,
                    branch = "SkBitmapDevice.blendF16PremulMode.${mode.name}.f16Store",
                    mode = mode,
                    coverage = cov,
                    coverageScale = traceCoverageScale,
                    coverageSamples = traceCoverageSamples,
                    coverageMaxSamples = traceCoverageMaxSamples,
                    paintColor4f = tracePaintColor4f,
                    srcPremulBeforeCoverageF16 = traceSrcPremulBeforeCoverageF16,
                    srcPremulAfterCoverageF16 = floatArrayOf(sr, sg, sb, sa),
                    dstPremulBeforeStoreF16 = beforeF16,
                    dstPremulAfterStoreF16 = floatArrayOf(
                        pixels[i],
                        pixels[i + 1],
                        pixels[i + 2],
                        pixels[i + 3],
                    ),
                    bitmapWidth = width,
                    bitmapHeight = height,
                )
            }
            return
        }
        val pixels = bitmap.pixelsF16
        val i = (y * bitmap.width + x) * 4
        val dr = pixels[i]
        val dg = pixels[i + 1]
        val db = pixels[i + 2]
        val da = pixels[i + 3]

        var or = 0f; var og = 0f; var ob = 0f; var oa = 0f
        when (mode) {
            SkBlendMode.kClear -> { /* zeros */ }
            SkBlendMode.kSrc -> { or = sr; og = sg; ob = sb; oa = sa }
            SkBlendMode.kDst -> { or = dr; og = dg; ob = db; oa = da }
            SkBlendMode.kDstOver -> {
                val k = 1f - da
                or = dr + sr * k
                og = dg + sg * k
                ob = db + sb * k
                oa = da + sa * k
            }
            SkBlendMode.kSrcIn -> {
                or = sr * da; og = sg * da; ob = sb * da; oa = sa * da
            }
            SkBlendMode.kDstIn -> {
                or = dr * sa; og = dg * sa; ob = db * sa; oa = da * sa
            }
            SkBlendMode.kSrcOut -> {
                val k = 1f - da
                or = sr * k; og = sg * k; ob = sb * k; oa = sa * k
            }
            SkBlendMode.kDstOut -> {
                val k = 1f - sa
                or = dr * k; og = dg * k; ob = db * k; oa = da * k
            }
            SkBlendMode.kSrcATop -> {
                val k = 1f - sa
                or = sr * da + dr * k
                og = sg * da + dg * k
                ob = sb * da + db * k
                oa = sa * da + da * k  // simplifies to da
            }
            SkBlendMode.kDstATop -> {
                val k = 1f - da
                or = dr * sa + sr * k
                og = dg * sa + sg * k
                ob = db * sa + sb * k
                oa = da * sa + sa * k  // simplifies to sa
            }
            SkBlendMode.kXor -> {
                val ks = 1f - sa
                val kd = 1f - da
                or = sr * kd + dr * ks
                og = sg * kd + dg * ks
                ob = sb * kd + db * ks
                oa = sa * kd + da * ks
            }
            SkBlendMode.kPlus -> {
                or = (sr + dr).coerceAtMost(1f)
                og = (sg + dg).coerceAtMost(1f)
                ob = (sb + db).coerceAtMost(1f)
                oa = (sa + da).coerceAtMost(1f)
            }
            SkBlendMode.kModulate -> {
                or = sr * dr; og = sg * dg; ob = sb * db; oa = sa * da
            }
            SkBlendMode.kScreen -> {
                or = sr + dr - sr * dr
                og = sg + dg - sg * dg
                ob = sb + db - sb * db
                oa = sa + da - sa * da
            }
            // Separable modes — pure float-premul algebra already lives in
            // [sepChannel] (factored out for the 8-bit path; we simply
            // call it with float operands, no quantization round-trip).
            SkBlendMode.kMultiply, SkBlendMode.kDarken, SkBlendMode.kLighten,
            SkBlendMode.kDifference, SkBlendMode.kExclusion,
            SkBlendMode.kOverlay, SkBlendMode.kHardLight,
            SkBlendMode.kColorDodge, SkBlendMode.kColorBurn, SkBlendMode.kSoftLight -> {
                or = sepChannel(sr, dr, sa, da, mode)
                og = sepChannel(sg, dg, sa, da, mode)
                ob = sepChannel(sb, db, sa, da, mode)
                oa = sa + da - sa * da
            }
            // HSL modes — delegate to the 3-channel body helper.
            SkBlendMode.kHue, SkBlendMode.kSaturation,
            SkBlendMode.kColor, SkBlendMode.kLuminosity -> {
                val body = FloatArray(3)
                blendHSLF16Body(sr, sg, sb, sa, dr, dg, db, da, mode, body)
                // Add the SrcOver carrier `sc*(1-da) + dc*(1-sa) + B`.
                or = sr * (1f - da) + dr * (1f - sa) + body[0]
                og = sg * (1f - da) + dg * (1f - sa) + body[1]
                ob = sb * (1f - da) + db * (1f - sa) + body[2]
                oa = sa + da - sa * da
            }
            SkBlendMode.kSrcOver -> {
                // Already handled by the early-return above; this branch
                // is unreachable but kept for `when` exhaustiveness.
            }
        }

        pixels[i]     = or.coerceIn(0f, 1f)
        pixels[i + 1] = og.coerceIn(0f, 1f)
        pixels[i + 2] = ob.coerceIn(0f, 1f)
        pixels[i + 3] = oa.coerceIn(0f, 1f)
    }

    /**
     * Compute the HSL `body` term in premul-float `[0, sa*da]` space.
     * Mirrors [blendHSL]'s body computation but writes directly into
     * the 3-element [out] buffer instead of going through the 8-bit
     * un-premul-and-quantize path. The caller adds the SrcOver
     * carrier and writes into the F16 buffer.
     */
    private fun blendHSLF16Body(
        sr: Float, sg: Float, sb: Float, sa: Float,
        dr: Float, dg: Float, db: Float, da: Float,
        mode: SkBlendMode,
        out: FloatArray,
    ) {
        // Scale src by da and dst by sa so both live in `[0, sa*da]`.
        val a = sa * da
        val srA = sr * da; val sgA = sg * da; val sbA = sb * da
        val drA = dr * sa; val dgA = dg * sa; val dbA = db * sa
        when (mode) {
            SkBlendMode.kHue -> {
                out[0] = srA; out[1] = sgA; out[2] = sbA
                setSat(out, sat3(drA, dgA, dbA))
                setLum(out, a, lum3(drA, dgA, dbA))
            }
            SkBlendMode.kSaturation -> {
                out[0] = drA; out[1] = dgA; out[2] = dbA
                setSat(out, sat3(srA, sgA, sbA))
                setLum(out, a, lum3(drA, dgA, dbA))
            }
            SkBlendMode.kColor -> {
                out[0] = srA; out[1] = sgA; out[2] = sbA
                setLum(out, a, lum3(drA, dgA, dbA))
            }
            SkBlendMode.kLuminosity -> {
                out[0] = drA; out[1] = dgA; out[2] = dbA
                setLum(out, a, lum3(srA, sgA, sbA))
            }
            else -> error("blendHSLF16Body called with non-HSL mode: $mode")
        }
    }
}
