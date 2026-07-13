package org.graphiks.kanvas.surface.gpu

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendCoverageMask
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendCoverageMaskRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilCoverConfig
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilFillRule
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilMode
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendTriangleData
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.geometry.FlattenedPath
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.Point
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.surface.DiagnosticFact
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderConfig

/** Explicit non-fatal refusal when a frame-local mask would exceed its allocated budget. */
internal class GPUClipCoverageFrameBudgetExceededException(message: String) : IllegalStateException(message)

/** One reference to a frame-local cached object. Its final release is observable by the caller. */
internal class GPUClipCoverageFrameLease<T> internal constructor(
    val value: T,
    val cacheHit: Boolean,
    private val releaseAction: () -> Boolean,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    /** Returns true only for the close which consumes the final pre-registered use. */
    fun release(): Boolean =
        if (closed.compareAndSet(false, true)) releaseAction() else false

    override fun close() {
        release()
    }
}

/**
 * Per-frame cache for materialized clip masks.
 *
 * Every key must be counted during the prepass. Counts are consumed when, and
 * only when, a logical-draw lease closes; this prevents a subpart of Text,
 * Atlas, or image expansion from freeing a mask still used by the same draw.
 */
internal class GPUClipCoverageFrameCache(
    private val totalBudgetBytes: Long,
) {
    private data class Entry(
        var remainingUses: Int,
        var openLeases: Int = 0,
        var value: Any? = null,
        var accountedBytes: Long = 0L,
    )

    private val entries = linkedMapOf<String, Entry>()
    private var allocatedBytes = 0L

    init {
        require(totalBudgetBytes >= 0L) { "totalBudgetBytes must be non-negative" }
    }

    val bytesInUse: Long
        get() = synchronized(this) { allocatedBytes }

    fun registerUses(contentKey: String, count: Int) {
        require(contentKey.isNotBlank()) { "contentKey must not be blank" }
        require(count > 0) { "count must be positive" }
        synchronized(this) {
            val entry = entries[contentKey]
            check(entry == null || entry.value == null) {
                "Clip mask use prepass cannot change an acquired key: $contentKey"
            }
            if (entry == null) {
                entries[contentKey] = Entry(remainingUses = count)
            } else {
                entry.remainingUses = Math.addExact(entry.remainingUses, count)
            }
        }
    }

    fun contains(contentKey: String): Boolean = synchronized(this) {
        entries[contentKey]?.value != null
    }

    fun <T> acquire(
        plan: GPUClipCoveragePlan.Mask,
        create: () -> T,
    ): GPUClipCoverageFrameLease<T> {
        synchronized(this) {
            val entry = checkNotNull(entries[plan.contentKey]) {
                "Clip mask was acquired without a GPUClipUsePrepass count: ${plan.contentKey}"
            }
            check(entry.openLeases < entry.remainingUses) {
                "Clip mask acquire count exceeds prepass count: ${plan.contentKey}"
            }
            entry.value?.let { cached ->
                entry.openLeases++
                @Suppress("UNCHECKED_CAST")
                return GPUClipCoverageFrameLease(cached as T, cacheHit = true) {
                    release(plan.contentKey)
                }
            }

            require(plan.requiredBytes >= 0L) { "Clip mask requiredBytes must be non-negative" }
            if (plan.requiredBytes > totalBudgetBytes - allocatedBytes) {
                throw GPUClipCoverageFrameBudgetExceededException(
                    "Clip mask exceeds remaining frame budget: required=${plan.requiredBytes} " +
                        "remaining=${totalBudgetBytes - allocatedBytes}",
                )
            }
            entry.openLeases++
            allocatedBytes = Math.addExact(allocatedBytes, plan.requiredBytes)
            val created = try {
                create()
            } catch (failure: Throwable) {
                allocatedBytes -= plan.requiredBytes
                entry.openLeases--
                throw failure
            }
            entry.value = created
            entry.accountedBytes = plan.requiredBytes
            return GPUClipCoverageFrameLease(created, cacheHit = false) {
                release(plan.contentKey)
            }
        }
    }

    /**
     * Consumes one pre-registered use which failed before acquiring a lease.
     *
     * If this is the final use, returns the cached value so its owner can release
     * the backing resource after the cache has discarded its byte accounting.
     */
    fun <T> cancelUnacquiredUse(contentKey: String): T? {
        val finalValue = synchronized(this) {
            val entry = checkNotNull(entries[contentKey]) {
                "Clip mask cancellation has no prepass entry: $contentKey"
            }
            check(entry.remainingUses > 0) {
                "Clip mask cancellation exceeds prepass count: $contentKey"
            }
            check(entry.remainingUses > entry.openLeases) {
                "Clip mask cancellation would consume an acquired lease: $contentKey"
            }
            entry.remainingUses--
            if (entry.remainingUses != 0) return@synchronized null

            check(entry.openLeases == 0) {
                "Clip mask reached final cancelled use while leases remain open: $contentKey"
            }
            allocatedBytes -= entry.accountedBytes
            entries.remove(contentKey)
            entry.value
        }
        @Suppress("UNCHECKED_CAST")
        return finalValue as T?
    }

    private fun release(contentKey: String): Boolean = synchronized(this) {
        val entry = checkNotNull(entries[contentKey]) {
            "Clip mask release has no prepass entry: $contentKey"
        }
        check(entry.openLeases > 0) {
            "Clip mask release count exceeds acquired leases: $contentKey"
        }
        check(entry.remainingUses > 0) {
            "Clip mask release count exceeds prepass count: $contentKey"
        }
        entry.openLeases--
        entry.remainingUses--
        if (entry.remainingUses != 0) return@synchronized false

        check(entry.openLeases == 0) {
            "Clip mask reached final use while leases remain open: $contentKey"
        }
        check(entry.value != null) {
            "Clip mask reached final use without a materialized value: $contentKey"
        }
        allocatedBytes -= entry.accountedBytes
        entries.remove(contentKey)
        true
    }
}

/** Immutable outcome of the count-only prepass performed before frame encoding. */
internal data class GPUClipUsePrepassResult(
    val registeredUsesByKey: Map<String, Int>,
    val refusalCodes: List<String>,
)

/** Static route refusal which must run before a mask lease can be acquired. */
internal data class GPUClipPreAcquireRefusal(
    val diagnosticCode: String,
    val reason: String,
    val facts: List<DiagnosticFact> = emptyList(),
)

/** Shared prepass/execution contract for routes that cannot acquire a clip mask. */
internal fun GPUClipCoveragePlan.preAcquireRefusalOrNull(
    blend: GPUBlendFacts,
): GPUClipPreAcquireRefusal? {
    if (blend.kind == GPUBlendKind.Unsupported ||
        (blend.kind != GPUBlendKind.SrcOver && blend.blendMode == null)
    ) {
        return GPUClipPreAcquireRefusal(
            diagnosticCode = "refuse:clip-blend",
            reason = "unsupported.clip.blend_unsupported:${blend.modeLabel.lowercase()}",
        )
    }
    return null
}

/**
 * Counts canonical non-scissor masks once per logical source draw before any
 * WebGPU command is encoded. Expanded cells, glyphs, points, and sprites are
 * deliberately not counted separately; their source wrapper owns one lease.
 */
internal object GPUClipUsePrepass {
    fun register(
        operations: Iterable<DisplayOp>,
        target: GPUTargetFacts,
        config: RenderConfig,
        maxTextureDimension2D: Int,
        cache: GPUClipCoverageFrameCache,
    ): GPUClipUsePrepassResult {
        val registered = linkedMapOf<String, Int>()
        val refusals = mutableListOf<String>()
        var suppressedLayerDepth = 0

        fun registerLogical(operation: DisplayOp) {
            if (operation is DisplayOp.BeginLayer) {
                if (suppressedLayerDepth > 0) {
                    suppressedLayerDepth++
                    return
                }
                if (operation.rec.gpuCompositePreflightRefusalOrNull() != null) suppressedLayerDepth = 1
                return
            }
            if (operation is DisplayOp.EndLayer) {
                if (suppressedLayerDepth > 0) suppressedLayerDepth--
                return
            }
            if (suppressedLayerDepth > 0 || operation.perspectiveCaptureRefusalReasonOrNull() != null) return
            operation.coreRoutePreflightRefusalReason()?.let { refusal ->
                refusals += refusal
                return
            }
            when (val plan = operation.gpuClipCoveragePlanOrNull(target, config, maxTextureDimension2D)) {
                is GPUClipCoveragePlan.Mask -> {
                    operation.coveragePlaneTask4RefusalOrNull()?.let { refusal ->
                        refusals += refusal
                        return
                    }
                    plan.preAcquireRefusalOrNull(operation.clipCompositeBlendFacts())?.let { refusal ->
                        refusals += refusal.reason
                        return
                    }
                    cache.registerUses(plan.contentKey, count = 1)
                    registered[plan.contentKey] = (registered[plan.contentKey] ?: 0) + 1
                }
                is GPUClipCoveragePlan.Refused -> refusals += plan.code
                null,
                GPUClipCoveragePlan.NoClip,
                is GPUClipCoveragePlan.Scissor,
                -> Unit
            }
        }

        operations.forEach(::registerLogical)
        return GPUClipUsePrepassResult(registered.toMap(), refusals.toList())
    }
}

/** Plans the exact mask/scissor strategy for one logical display operation. */
internal fun DisplayOp.gpuClipCoveragePlanOrNull(
    target: GPUTargetFacts,
    config: RenderConfig,
    maxTextureDimension2D: Int,
): GPUClipCoveragePlan? {
    val request = clipForMaskPrepass()?.toGPUClipFacts(target)?.coverageRequest ?: return null
    return GPUClipCoveragePlanner.plan(request, config, maxTextureDimension2D)
}

/** Shared renderer/prepass refusal contract: refused draws must never reserve a mask use. */
internal fun DisplayOp.coreRoutePreflightRefusalReason(): String? = when (this) {
    is DisplayOp.DrawMesh -> if (mesh.program != null) "unsupported.mesh.program" else null
    is DisplayOp.DrawPicture -> picturePreflightRefusalReason()
    else -> null
}

/**
 * Task 4 supplies an S/G adapter for every visual operation accepted by this
 * renderer. Kept as a named boundary for prepass callers: a future visual
 * operation must either install its adapter or return a stable refusal here.
 */
internal fun DisplayOp.coveragePlaneTask4RefusalOrNull(): String? = null

private fun DisplayOp.DrawPicture.picturePreflightRefusalReason(): String? {
    if (paint != null) return "unsupported.picture.paint"

    fun validatePicture(picture: Picture): String? {
        for (nested in picture.ops) {
            when (nested) {
                is DisplayOp.DrawPicture -> {
                    if (nested.paint != null) return "unsupported.picture.nested_paint"
                    val nestedRefusal = validatePicture(nested.picture)
                    if (nestedRefusal != null) return nestedRefusal
                }
                else -> Unit
            }
        }
        return null
    }

    return validatePicture(picture)
}

/** The layer compositor accepts only alpha and BlendMode from its optional paint. */
internal fun SaveLayerRec.gpuCompositePreflightRefusalOrNull(): String? {
    if (backdrop != null) return "unsupported.layer.backdrop_filter"
    val layerPaint = paint ?: return null
    if (
        layerPaint.shader != null ||
        layerPaint.colorFilter != null ||
        layerPaint.maskFilter != null ||
        layerPaint.pathEffect != null ||
        layerPaint.imageFilter != null ||
        layerPaint.blender != null ||
        layerPaint.style != Paint().style ||
        layerPaint.strokeWidth != 0f ||
        layerPaint.strokeCap != Paint().strokeCap ||
        layerPaint.strokeJoin != Paint().strokeJoin ||
        layerPaint.strokeMiter != Paint().strokeMiter ||
        layerPaint.antiAlias != Paint().antiAlias
    ) {
        return "unsupported.layer.paint"
    }
    return layerPaint.blendMode.toGpuBlendFacts()
        .takeIf { it.kind == GPUBlendKind.Unsupported }
        ?.let { "unsupported.layer.blend:${it.modeLabel.lowercase()}" }
}

internal fun DisplayOp.clipCompositeBlendFacts(): GPUBlendFacts = when (this) {
    is DisplayOp.DrawRect -> paint.blendMode.toGpuBlendFacts()
    is DisplayOp.DrawRRect -> paint.blendMode.toGpuBlendFacts()
    is DisplayOp.DrawPath -> paint.blendMode.toGpuBlendFacts()
    is DisplayOp.DrawImage -> paint?.blendMode?.toGpuBlendFacts() ?: GPUBlendFacts.srcOver()
    is DisplayOp.DrawText -> paint.blendMode.toGpuBlendFacts()
    is DisplayOp.DrawColor -> mode.toGpuBlendFacts()
    is DisplayOp.DrawPoint -> paint.blendMode.toGpuBlendFacts()
    is DisplayOp.DrawPoints -> paint.blendMode.toGpuBlendFacts()
    is DisplayOp.DrawDRRect -> paint.blendMode.toGpuBlendFacts()
    is DisplayOp.DrawImageNine -> paint?.blendMode?.toGpuBlendFacts() ?: GPUBlendFacts.srcOver()
    is DisplayOp.DrawImageLattice -> paint?.blendMode?.toGpuBlendFacts() ?: GPUBlendFacts.srcOver()
    is DisplayOp.DrawPicture -> paint?.blendMode?.toGpuBlendFacts() ?: GPUBlendFacts.srcOver()
    is DisplayOp.DrawVertices -> paint.blendMode.toGpuBlendFacts()
    is DisplayOp.DrawMesh -> (blendMode ?: paint.blendMode).toGpuBlendFacts()
    is DisplayOp.DrawAtlas -> (paint?.blendMode ?: blendMode).toGpuBlendFacts()
    is DisplayOp.SetTransform,
    is DisplayOp.SetClip,
    is DisplayOp.BeginLayer,
    DisplayOp.EndLayer,
    is DisplayOp.Clear,
    is DisplayOp.Annotation,
    is DisplayOp.FlushAndSnapshot,
    -> GPUBlendFacts.srcOver()
}

private fun DisplayOp.clipForMaskPrepass(): ClipStack? = when (this) {
    is DisplayOp.DrawRect -> clip
    is DisplayOp.DrawRRect -> clip
    is DisplayOp.DrawPath -> clip
    is DisplayOp.DrawImage -> clip
    is DisplayOp.DrawText -> clip
    is DisplayOp.DrawColor -> clip
    is DisplayOp.DrawPoint -> clip
    is DisplayOp.DrawPoints -> clip
    is DisplayOp.DrawDRRect -> clip
    is DisplayOp.DrawImageNine -> clip
    is DisplayOp.DrawImageLattice -> clip
    is DisplayOp.DrawVertices -> clip
    is DisplayOp.DrawMesh -> clip
    is DisplayOp.DrawAtlas -> clip
    is DisplayOp.DrawPicture -> clip
    is DisplayOp.SetTransform,
    is DisplayOp.SetClip,
    is DisplayOp.BeginLayer,
    DisplayOp.EndLayer,
    is DisplayOp.Clear,
    is DisplayOp.Annotation,
    is DisplayOp.FlushAndSnapshot,
    -> null
}

/** Materialized GPU mask plus the plan which defines its exact scalar clip semantics. */
internal data class ClipMaskLease(
    val mask: GPUBackendCoverageMask,
    val plan: GPUClipCoveragePlan.Mask,
    val cacheHit: Boolean,
    private val releaseAction: () -> Unit,
) : AutoCloseable {
    override fun close() = releaseAction()
}

/** Acquires or materializes one AlphaMask clip entirely on the GPU. */
internal fun GPUBackendOffscreenTarget.acquireClipMask(
    plan: GPUClipCoveragePlan.Mask,
    cache: GPUClipCoverageFrameCache,
    diagnostics: Diagnostics,
    config: RenderConfig,
): ClipMaskLease {
    val cachedLease = try {
        cache.acquire(plan) {
            val mask = createCoverageMask(
                GPUBackendCoverageMaskRequest(
                    label = "clip:${plan.contentKey}",
                    width = plan.width,
                    height = plan.height,
                    sampleCount = plan.sampleCount,
                    format = config.gpuColorFormat.gpuLabel,
                ),
            )
            try {
                diagnostics.clipMaskFact(
                    reason = "clip_mask_clear",
                    plan = plan,
                    cacheHit = false,
                    pass = "clear-white",
                )
                encodeCoverageMask(mask, GPUClearColor(1.0, 1.0, 1.0, 1.0)) {
                    plan.elements.forEach { element ->
                        val pass = renderClipElement(element, mask, config)
                        diagnostics.clipMaskFact(
                            reason = "clip_mask_pass",
                            plan = plan,
                            cacheHit = false,
                            pass = pass,
                        )
                    }
                }
                mask
            } catch (failure: Throwable) {
                try {
                    releaseCoverageMask(mask)
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
                throw failure
            }
        }
    } catch (failure: Throwable) {
        val cancelledMask = try {
            cache.cancelUnacquiredUse<GPUBackendCoverageMask>(plan.contentKey)
        } catch (cleanupFailure: Throwable) {
            failure.addSuppressed(cleanupFailure)
            null
        }
        if (cancelledMask != null) {
            try {
                releaseCoverageMask(cancelledMask)
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
        }
        throw failure
    }
    diagnostics.clipMaskFact(
        reason = "clip_mask_acquire",
        plan = plan,
        cacheHit = cachedLease.cacheHit,
        pass = if (cachedLease.cacheHit) "cache-hit" else "cache-miss",
    )
    return ClipMaskLease(
        mask = cachedLease.value,
        plan = plan,
        cacheHit = cachedLease.cacheHit,
    ) {
        if (cachedLease.release()) {
            releaseCoverageMask(cachedLease.value)
            diagnostics.clipMaskFact(
                reason = "clip_mask_release",
                plan = plan,
                cacheHit = false,
                pass = "release",
            )
        }
    }
}

private fun Diagnostics.clipMaskFact(
    reason: String,
    plan: GPUClipCoveragePlan.Mask,
    cacheHit: Boolean,
    pass: String,
) {
    degrade(
        code = "clip-mask:${plan.contentKey}:$pass",
        operation = "clipMask",
        reason = reason,
        facts = listOf(
            DiagnosticFact("clip.mask.bytes", plan.requiredBytes.toString()),
            DiagnosticFact("clip.mask.cache", if (cacheHit) "hit" else "miss"),
            DiagnosticFact("clip.mask.key", plan.contentKey),
            DiagnosticFact("clip.mask.pass", pass),
            DiagnosticFact("clip.mask.samples", plan.sampleCount.toString()),
        ),
    )
}

private sealed interface GPUClipShape {
    data class Rect(val bounds: GPUBounds) : GPUClipShape
    data class RRect(val values: List<Float>) : GPUClipShape
    data class Path(val flattened: FlattenedPath) : GPUClipShape
}

private fun GPUBackendRenderRecorder.renderClipElement(
    element: GPUClipCoverageElement,
    mask: GPUBackendCoverageMask,
    config: RenderConfig,
): String {
    val blendMode = when (element.operation) {
        GPUClipCoverageOperation.Intersect -> GPUBlendMode.DST_IN
        GPUClipCoverageOperation.Difference -> GPUBlendMode.DST_OUT
    }
    return when (val shape = element.toShape()) {
        is GPUClipShape.Rect -> {
            drawFullscreenRawUniformPass(
                wgsl = RECT_AA_WGSL,
                colorFormat = config.gpuColorFormat.gpuLabel,
                draws = listOf(rectCoverageDraw(shape.bounds, element.antiAlias, mask)),
                blendMode = blendMode,
            )
            "rect"
        }
        is GPUClipShape.RRect -> {
            drawFullscreenRawUniformPass(
                wgsl = RRECT_WGSL,
                colorFormat = config.gpuColorFormat.gpuLabel,
                draws = listOf(rrectCoverageDraw(shape.values, element.antiAlias, mask)),
                blendMode = blendMode,
            )
            "rrect"
        }
        is GPUClipShape.Path -> {
            if (shape.flattened.points.size < 3) {
                return applyConstantCoverage(
                    operation = element.operation,
                    coverageIsFull = element.inverseFill,
                    mask = mask,
                    config = config,
                )
            }
            val stencilConfig = GPUBackendStencilCoverConfig(
                fillRule = when (element.fillRule) {
                    GPUClipFillRule.Winding -> GPUBackendStencilFillRule.NonZero
                    GPUClipFillRule.EvenOdd -> GPUBackendStencilFillRule.EvenOdd
                },
                inverse = element.inverseFill,
            )
            val edgeFan = PathTessellator().stencilEdgeFan(shape.flattened)
            val triangleData = GPUBackendTriangleData(edgeFan.vertices, edgeFan.indices)
            drawFullscreenStencilPass(
                wgsl = CLIP_STENCIL_WRITE_WGSL,
                colorFormat = config.gpuColorFormat.gpuLabel,
                stencilMode = GPUBackendStencilMode.Write,
                triangleData = triangleData,
                draws = emptyList(),
                stencilConfig = stencilConfig,
            )
            drawFullscreenStencilPass(
                wgsl = CLIP_MASK_COVER_WGSL,
                colorFormat = config.gpuColorFormat.gpuLabel,
                stencilMode = GPUBackendStencilMode.Test,
                triangleData = null,
                draws = listOf(fullMaskDraw(mask)),
                blendMode = blendMode,
                stencilConfig = stencilConfig,
            )
            if (element.operation == GPUClipCoverageOperation.Intersect) {
                // A stencil test discards the complement. DST_IN must also receive
                // transparent source there, otherwise the previous mask survives.
                drawFullscreenStencilPass(
                    wgsl = CLIP_MASK_COVER_WGSL,
                    colorFormat = config.gpuColorFormat.gpuLabel,
                    stencilMode = GPUBackendStencilMode.Test,
                    triangleData = null,
                    draws = listOf(fullMaskDraw(mask, alpha = 0f)),
                    blendMode = GPUBlendMode.DST_IN,
                    stencilConfig = stencilConfig.copy(inverse = !stencilConfig.inverse),
                )
            }
            "path-stencil"
        }
    }
}

/** Applies the empty/full result without substituting a bounds or scissor clip. */
private fun GPUBackendRenderRecorder.applyConstantCoverage(
    operation: GPUClipCoverageOperation,
    coverageIsFull: Boolean,
    mask: GPUBackendCoverageMask,
    config: RenderConfig,
): String {
    val changesDestination =
        (operation == GPUClipCoverageOperation.Intersect && !coverageIsFull) ||
            (operation == GPUClipCoverageOperation.Difference && coverageIsFull)
    if (!changesDestination) return "constant-noop"
    drawFullscreenRawUniformPass(
        wgsl = CLIP_MASK_COVER_WGSL,
        colorFormat = config.gpuColorFormat.gpuLabel,
        draws = listOf(fullMaskDraw(mask, alpha = 0f)),
        blendMode = GPUBlendMode.DST_IN,
    )
    return "constant-clear"
}

private fun GPUClipCoverageElement.toShape(): GPUClipShape = when (kind) {
    GPUClipCoverageElementKind.Rect -> GPUClipShape.Rect(
        GPUBounds(values[0], values[1], values[2], values[3]),
    )
    GPUClipCoverageElementKind.RRect -> {
        val uniformRadii = values.subList(4, 12).chunked(2).all { radii ->
            radii[0] == values[4] && radii[1] == values[5]
        }
        if (uniformRadii) GPUClipShape.RRect(values) else GPUClipShape.Path(values.toRRectPath())
    }
    GPUClipCoverageElementKind.Path -> GPUClipShape.Path(values.toFlattenedPath())
}

private fun List<Float>.toFlattenedPath(): FlattenedPath {
    val contourCount = first().toInt()
    val coordinateStart = 1 + contourCount
    return FlattenedPath(
        points = drop(coordinateStart).chunked(2).map { Point(it[0], it[1]) },
        contourStarts = subList(1, coordinateStart).map(Float::toInt),
    )
}

/** Lowers an asymmetric rrect to an ordered, finite contour for standard stencil coverage. */
private fun List<Float>.toRRectPath(): FlattenedPath {
    val left = this[0]
    val top = this[1]
    val right = this[2]
    val bottom = this[3]
    val maxRadiusX = ((right - left) * 0.5f).coerceAtLeast(0f)
    val maxRadiusY = ((bottom - top) * 0.5f).coerceAtLeast(0f)
    fun radius(index: Int): Pair<Float, Float> =
        this[index].coerceIn(0f, maxRadiusX) to this[index + 1].coerceIn(0f, maxRadiusY)

    val topLeft = radius(4)
    val topRight = radius(6)
    val bottomRight = radius(8)
    val bottomLeft = radius(10)
    val points = mutableListOf<Point>()
    fun append(x: Float, y: Float) {
        val point = Point(x, y)
        if (points.lastOrNull() != point) points += point
    }
    fun arc(cx: Float, cy: Float, rx: Float, ry: Float, start: Float, end: Float) {
        repeat(5) { index ->
            val angle = start + (end - start) * index / 4f
            append(cx + rx * cos(angle), cy + ry * sin(angle))
        }
    }

    append(left + topLeft.first, top)
    append(right - topRight.first, top)
    arc(right - topRight.first, top + topRight.second, topRight.first, topRight.second, -PI.toFloat() / 2f, 0f)
    append(right, bottom - bottomRight.second)
    arc(right - bottomRight.first, bottom - bottomRight.second, bottomRight.first, bottomRight.second, 0f, PI.toFloat() / 2f)
    append(left + bottomLeft.first, bottom)
    arc(left + bottomLeft.first, bottom - bottomLeft.second, bottomLeft.first, bottomLeft.second, PI.toFloat() / 2f, PI.toFloat())
    append(left, top + topLeft.second)
    arc(left + topLeft.first, top + topLeft.second, topLeft.first, topLeft.second, PI.toFloat(), PI.toFloat() * 1.5f)
    return FlattenedPath(points, contourStarts = listOf(0))
}

private fun rectCoverageDraw(
    bounds: GPUBounds,
    antiAlias: Boolean,
    mask: GPUBackendCoverageMask,
): GPUBackendRawUniformDraw {
    val bytes = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
        putFloat(bounds.left); putFloat(bounds.top); putFloat(bounds.right); putFloat(bounds.bottom)
        putFloat(1f); putFloat(1f); putFloat(1f); putFloat(1f)
        putInt(if (antiAlias) 1 else 0)
        putFloat(0f); putFloat(0f); putFloat(0f)
    }.array()
    return GPUBackendRawUniformDraw(bytes, 0, 0, mask.width, mask.height)
}

private fun rrectCoverageDraw(
    values: List<Float>,
    antiAlias: Boolean,
    mask: GPUBackendCoverageMask,
): GPUBackendRawUniformDraw {
    val bounds = GPUBounds(values[0], values[1], values[2], values[3])
    val bytes = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
        putFloat(values[0]); putFloat(values[1]); putFloat(values[2]); putFloat(values[3])
        putFloat(values[4]); putFloat(values[5]); putFloat(0f); putFloat(0f)
        putFloat(1f); putFloat(1f); putFloat(1f); putFloat(1f)
        putInt(if (antiAlias) 1 else 0)
        putFloat(0f); putFloat(0f); putFloat(0f)
    }.array()
    return GPUBackendRawUniformDraw(bytes, 0, 0, mask.width, mask.height)
}

/** Matches the parser-reflected `vec4f color` layout in [CLIP_MASK_COVER_WGSL]. */
private fun fullMaskDraw(mask: GPUBackendCoverageMask, alpha: Float = 1f): GPUBackendRawUniformDraw =
    GPUBackendRawUniformDraw(
        uniformBytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(alpha); putFloat(alpha); putFloat(alpha); putFloat(alpha)
        }.array(),
        scissorX = 0,
        scissorY = 0,
        scissorWidth = mask.width,
        scissorHeight = mask.height,
    )

/** Packs the parser-reflected 16-byte `_pad: vec4f` layout for source×mask composition. */
internal fun clipMaskCompositeUniformDraw(width: Int, height: Int): GPUBackendRawUniformDraw =
    GPUBackendRawUniformDraw(
        uniformBytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(0f); putFloat(0f); putFloat(0f); putFloat(0f)
        }.array(),
        scissorX = 0,
        scissorY = 0,
        scissorWidth = width,
        scissorHeight = height,
    )
