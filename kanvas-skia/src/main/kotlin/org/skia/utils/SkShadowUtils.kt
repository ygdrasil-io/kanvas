package org.skia.utils

import org.skia.core.SkCanvas
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorGetA
import org.skia.foundation.SkColorGetB
import org.skia.foundation.SkColorGetG
import org.skia.foundation.SkColorGetR
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkMatrix
import org.skia.math.SkPoint3
import org.skia.math.SkRect
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Iso-aligned port of Skia's
 * [`SkShadowUtils`](https://github.com/google/skia/blob/main/include/utils/SkShadowUtils.h)
 * — helpers for drawing the two-layer (ambient + spot) shadow under a
 * 3-D occluder path.
 *
 * R3.7 ships a **simplified but visually plausible** port :
 *  - [ComputeTonalColors] is a faithful 1-to-1 translation of upstream's
 *    `SkShadowUtils.cpp::ComputeTonalColors` (luminance-based alpha
 *    blending of the user-supplied ambient / spot colours).
 *  - [DrawShadow] uses upstream's `SkDrawShadowMetrics` formulas
 *    (`AmbientBlurRadius`, `AmbientRecipAlpha`, `GetSpotParams`,
 *    `GetDirectionalParams`) to compute blur radii / offsets, then
 *    renders the shadow via two blurred path draws — ambient as a
 *    direct blurred fill, spot as a translated + blurred fill — using
 *    [SkImageFilters.Blur] inside `saveLayer` paints. Upstream uses an
 *    analytic geometry pipeline that produces a single tessellated mesh
 *    for both layers ; the blur approximation here is intentionally
 *    cheaper and skips edge-fade falloff / opaque-occluder culling, but
 *    matches the broad visual shape (centred ambient halo, offset spot
 *    cast away from the light).
 *  - [OptimizeForSurface] is a no-op placeholder that always returns
 *    `true` (upstream caches a tessellated mesh keyed on
 *    `(path, ctm, light)`) — call sites can keep their cache-warming
 *    bookkeeping without behaviour change.
 *  - [GetLocalBounds] mirrors upstream's API surface ; the returned
 *    rect is the path's `computeBounds()` outset by the worst-case
 *    ambient + spot blur radii.
 *
 * The simplification is acceptable for R3 : pixel-exact parity with
 * upstream's analytic mesh requires
 * `SkDrawShadowMetrics::GetSpotShadowTransform` + a tessellator and is
 * tracked separately as `SkShadowMetrics` follow-up.
 */
public object SkShadowUtils {
    /** Mirrors upstream `SkShadowFlags::kNone_ShadowFlag`. */
    public const val kNone_ShadowFlag: Int = 0x00

    /**
     * The occluding object is not opaque. Mirrors upstream
     * `kTransparentOccluder_ShadowFlag` (`include/utils/SkShadowUtils.h:27`).
     * Without it, Skia is free to cull shadow geometry behind the occluder.
     */
    public const val kTransparentOccluder_ShadowFlag: Int = 0x01

    /**
     * Don't try to use analytic shadows. Mirrors upstream
     * `kGeometricOnly_ShadowFlag` — always uses the blur approximation.
     */
    public const val kGeometricOnly_ShadowFlag: Int = 0x02

    /**
     * Light position represents a direction vector ; light radius is the
     * blur radius at elevation 1. Mirrors upstream
     * `kDirectionalLight_ShadowFlag`.
     */
    public const val kDirectionalLight_ShadowFlag: Int = 0x04

    /**
     * Concave paths only use blur to generate the shadow (skips the
     * analytic mesh that would self-intersect). Mirrors upstream
     * `kConcaveBlurOnly_ShadowFlag`.
     */
    public const val kConcaveBlurOnly_ShadowFlag: Int = 0x08

    /** Mask for all shadow flags. Equals `0x0F`. */
    public const val kAll_ShadowFlag: Int =
        kTransparentOccluder_ShadowFlag or
            kGeometricOnly_ShadowFlag or
            kDirectionalLight_ShadowFlag or
            kConcaveBlurOnly_ShadowFlag

    // ─── SkDrawShadowMetrics constants (src/core/SkDrawShadowInfo.h) ──

    private const val kAmbientHeightFactor: Float = 1f / 128f
    private const val kAmbientGeomFactor: Float = 64f
    private const val kMaxAmbientRadius: Float =
        300f * kAmbientHeightFactor * kAmbientGeomFactor // == 150

    private fun divideAndPin(numer: Float, denom: Float, minV: Float, maxV: Float): Float {
        val r = if (denom == 0f || !denom.isFinite()) maxV else numer / denom
        if (r.isNaN()) return minV
        return when {
            r < minV -> minV
            r > maxV -> maxV
            else -> r
        }
    }

    private fun ambientBlurRadius(height: Float): Float =
        min(height * kAmbientHeightFactor * kAmbientGeomFactor, kMaxAmbientRadius)

    private fun ambientRecipAlpha(height: Float): Float =
        1f + max(height * kAmbientHeightFactor, 0f)

    /**
     * Result of `GetSpotParams` (upstream `SkDrawShadowInfo.h:63`). The
     * blur radius drives the Gaussian σ, the scale matches the spot
     * shadow's perspective enlargement, and the translate offsets the
     * projected shadow away from the light source.
     */
    private data class SpotParams(
        val blurRadius: Float,
        val scale: Float,
        val translateX: Float,
        val translateY: Float,
    )

    private fun getSpotParams(
        occluderZ: Float,
        lightX: Float, lightY: Float, lightZ: Float,
        lightRadius: Float,
    ): SpotParams {
        val zRatio = divideAndPin(occluderZ, lightZ - occluderZ, 0f, 0.95f)
        val blurRadius = lightRadius * zRatio
        val scale = divideAndPin(lightZ, lightZ - occluderZ, 1f, 1.95f)
        return SpotParams(blurRadius, scale, -zRatio * lightX, -zRatio * lightY)
    }

    private fun getDirectionalParams(
        occluderZ: Float,
        lightX: Float, lightY: Float, lightZ: Float,
        lightRadius: Float,
    ): SpotParams {
        val blurRadius = lightRadius * occluderZ
        // Upstream uses 64 / SK_ScalarNearlyZero as max ; we cap to the
        // same magnitude with a finite float to avoid 0-division NaNs.
        val kMaxZRatio = 64f / 1.0e-12f
        val zRatio = divideAndPin(occluderZ, lightZ, 0f, kMaxZRatio)
        return SpotParams(blurRadius, 1f, -zRatio * lightX, -zRatio * lightY)
    }

    // ─── Public API ──────────────────────────────────────────────────

    /**
     * Helper routine to compute color values for one-pass tonal alpha.
     *
     * 1-to-1 port of `SkShadowUtils.cpp::ComputeTonalColors`
     * (`src/utils/SkShadowUtils.cpp:487`).
     *
     * Writes the modified ambient colour into `outAmbientColor[0]` and
     * the modified spot colour into `outSpotColor[0]`. Both output
     * arrays must have length ≥ 1 ; this signature mirrors the
     * upstream C++ pointer-out parameters in a Kotlin-idiomatic way.
     */
    public fun ComputeTonalColors(
        inAmbientColor: SkColor,
        inSpotColor: SkColor,
        outAmbientColor: IntArray,
        outSpotColor: IntArray,
    ) {
        require(outAmbientColor.isNotEmpty()) { "outAmbientColor must have at least 1 element" }
        require(outSpotColor.isNotEmpty()) { "outSpotColor must have at least 1 element" }

        // Ambient : alpha preserved, RGB forced to black.
        outAmbientColor[0] = SkColorSetARGB(SkColorGetA(inAmbientColor), 0, 0, 0)

        // Spot : luminance + alpha-adjusted tonal blend.
        val spotR = SkColorGetR(inSpotColor)
        val spotG = SkColorGetG(inSpotColor)
        val spotB = SkColorGetB(inSpotColor)
        val maxC = max(max(spotR, spotG), spotB)
        val minC = min(min(spotR, spotG), spotB)
        val luminance = 0.5f * (maxC + minC) / 255f
        val origA = SkColorGetA(inSpotColor) / 255f

        val alphaAdjust = (2.6f + (-2.66667f + 1.06667f * origA) * origA) * origA
        var colorAlpha = (3.544762f + (-4.891428f + 2.3466f * luminance) * luminance) * luminance
        colorAlpha = (alphaAdjust * colorAlpha).coerceIn(0f, 1f)

        val greyscaleAlpha = (origA * (1f - 0.4f * luminance)).coerceIn(0f, 1f)

        val colorScale = colorAlpha * (1f - greyscaleAlpha)
        val tonalAlpha = colorScale + greyscaleAlpha
        // Guard against div-by-zero when both the spot and the greyscale
        // contributions collapse to 0 — upstream relies on IEEE 0/0 = NaN
        // being then clamped by SkColorSetARGB's 0..255 range, but a
        // deterministic 0-output is the better Kotlin idiom.
        val unPremulScale = if (tonalAlpha == 0f) 0f else colorScale / tonalAlpha

        outSpotColor[0] = SkColorSetARGB(
            (tonalAlpha * 255.999f).toInt().coerceIn(0, 255),
            (unPremulScale * spotR).toInt().coerceIn(0, 255),
            (unPremulScale * spotG).toInt().coerceIn(0, 255),
            (unPremulScale * spotB).toInt().coerceIn(0, 255),
        )
    }

    /**
     * Draw an offset spot shadow and outlining ambient shadow for the
     * given [path] using a disc light.
     *
     * **Implementation note (R3.7)** : this is a simplified blur-based
     * port — upstream uses an analytic tessellated mesh
     * (`src/gpu/ganesh/ops/ShadowRRectOp.cpp` + CPU fallbacks in
     * `SkDevice::drawShadow`). The visual shape (centred ambient halo
     * plus a directionally-offset spot) matches, but per-pixel parity
     * with upstream is not guaranteed. Edge falloff, opaque-occluder
     * culling, and analytic-vs-blur switchover ([kGeometricOnly_ShadowFlag]
     * is therefore a no-op here) are tracked as follow-up work.
     *
     * @param zPlaneParams `(a, b, c)` of the plane `z = ax + by + c` giving
     *  the occluder's elevation. The shadow uses the elevation at the path's
     *  centroid (i.e. `a·cx + b·cy + c`) as `occluderZ` — upstream
     *  evaluates the plane at every vertex of the tessellated mesh, but
     *  for a rect or smallish convex path the centroid is a good proxy.
     * @param lightPos 3-D position of the light source in canvas-local
     *  coords. When [kDirectionalLight_ShadowFlag] is set, this is a
     *  direction vector instead.
     * @param lightRadius Disc-light radius (`> 0`) ; controls penumbra
     *  width. With [kDirectionalLight_ShadowFlag], this is the blur
     *  radius at `z = 1`.
     */
    public fun DrawShadow(
        canvas: SkCanvas,
        path: SkPath,
        zPlaneParams: SkPoint3,
        lightPos: SkPoint3,
        lightRadius: Float,
        ambientColor: SkColor,
        spotColor: SkColor,
        flags: Int = kNone_ShadowFlag,
    ) {
        val bounds = path.computeBounds()
        if (bounds.isEmpty) return

        // ── R-suivi.31 — tilt-aware z sampling ────────────────────
        // Evaluate `z = a·x + b·y + c` at every vertex emitted by
        // SkPath.Iter (one endpoint per move/line/quad/conic/cubic verb).
        // The min/max of those samples drives the ambient blur radius
        // (we use the max, which gives the largest halo — the safe over-
        // estimate matching upstream's "worst-case" envelope used by
        // SkDrawShadowMetrics::GetLocalBounds), while the centroid-z is
        // kept as a fallback for paths with < 2 vertices.
        val cx = (bounds.left + bounds.right) * 0.5f
        val cy = (bounds.top + bounds.bottom) * 0.5f
        val centroidZ = max(zPlaneParams.fX * cx + zPlaneParams.fY * cy + zPlaneParams.fZ, 0f)

        val vertexZs = sampleVertexZs(path, zPlaneParams)
        val occluderZ = if (vertexZs.size < 2) centroidZ else {
            // Use the max z (largest blur) for the ambient layer and the
            // spot's perspective scale ; this matches the upstream over-
            // estimate used by GetLocalBounds.
            var m = 0f
            for (z in vertexZs) if (z > m) m = z
            m
        }

        // ── Ambient shadow ─────────────────────────────────────────
        // Blur sigma is upstream's AmbientBlurRadius (in device pixels)
        // divided by 2 — matches the σ convention used by SkImageFilters.Blur,
        // which uses a 3σ kernel ; upstream's "blur radius" is the kernel
        // half-width, so σ ≈ radius / 2 lands on the same visual diameter.
        val ambientBlurR = ambientBlurRadius(occluderZ)
        val ambientSigma = ambientBlurR * 0.5f
        if (SkColorGetA(ambientColor) > 0 && ambientSigma > 0f) {
            val ambientPaint = SkPaint().apply {
                color = ambientColor
                isAntiAlias = true
                imageFilter = SkImageFilters.Blur(ambientSigma, ambientSigma, null)
            }
            val layerBounds = bounds.makeOutset(ambientBlurR + 1f, ambientBlurR + 1f)
            val saveCount = canvas.saveLayer(layerBounds, ambientPaint)
            val fillPaint = SkPaint().apply {
                color = ambientColor
                isAntiAlias = true
            }
            canvas.drawPath(path, fillPaint)
            canvas.restoreToCount(saveCount)
        } else if (SkColorGetA(ambientColor) > 0) {
            val fillPaint = SkPaint().apply {
                color = ambientColor
                isAntiAlias = true
            }
            canvas.drawPath(path, fillPaint)
        }

        // ── Spot shadow ────────────────────────────────────────────
        val sp = if ((flags and kDirectionalLight_ShadowFlag) != 0) {
            getDirectionalParams(occluderZ, lightPos.fX, lightPos.fY, lightPos.fZ, lightRadius)
        } else {
            getSpotParams(occluderZ, lightPos.fX, lightPos.fY, lightPos.fZ, lightRadius)
        }
        val spotSigma = sp.blurRadius * 0.5f

        if (SkColorGetA(spotColor) > 0) {
            // R-suivi.31 — tilt-aware spot projection. When per-vertex z
            // samples differ noticeably (steep tilt), expand the spot
            // bounds to the envelope of per-vertex projections so the
            // larger-z side gets a proportionally larger shadow. We
            // implement this by computing a per-vertex scale anchor and
            // taking the union of the resulting transformed bounds.
            //
            // For non-tilted planes (a = b = 0), every vertex shares the
            // same z and the loop below collapses to the original
            // centroid-scale + translate transform, preserving the
            // pre-existing behaviour bit-for-bit.
            val spotBoundsRaw = computeSpotBounds(
                path, bounds, vertexZs,
                cx, cy,
                lightPos, lightRadius,
                useDirectional = (flags and kDirectionalLight_ShadowFlag) != 0,
            )

            // Spot transform : scale around the path's centroid (perspective
            // enlargement under the light) then translate (projection offset).
            val spotMatrix = SkMatrix.MakeTrans(cx + sp.translateX, cy + sp.translateY)
                .preConcat(SkMatrix.MakeScale(sp.scale, sp.scale))
                .preConcat(SkMatrix.MakeTrans(-cx, -cy))
            val spotPath = path.makeTransform(spotMatrix)

            // R-suivi.33 — projection cache. Pull a precomputed projected
            // path if [OptimizeForSurface] previously warmed it for the
            // same (path, lightPos, zPlaneParams) tuple ; otherwise the
            // cache stays cold and we fall through to the direct
            // makeTransform result we just computed above.
            val cached = projectionCacheGet(path, lightPos, zPlaneParams)
            val effectiveSpotPath = cached ?: spotPath

            // R-suivi.32 — transparent-occluder culling. When the flag is
            // unset (default = opaque occluder), the spot shadow under the
            // occluder is invisible — clip it out before drawing. When
            // the flag is set, emit the full shadow including the part
            // behind the occluder.
            val cullOpaque = (flags and kTransparentOccluder_ShadowFlag) == 0

            if (spotSigma > 0f) {
                val spotPaint = SkPaint().apply {
                    color = spotColor
                    isAntiAlias = true
                    imageFilter = SkImageFilters.Blur(spotSigma, spotSigma, null)
                }
                val spotBounds = (if (spotBoundsRaw.isEmpty) effectiveSpotPath.computeBounds() else spotBoundsRaw)
                    .makeOutset(sp.blurRadius + 1f, sp.blurRadius + 1f)
                val saveCount = canvas.saveLayer(spotBounds, spotPaint)
                if (cullOpaque) canvas.clipPath(path, SkClipOp.kDifference, true)
                val fillPaint = SkPaint().apply {
                    color = spotColor
                    isAntiAlias = true
                }
                canvas.drawPath(effectiveSpotPath, fillPaint)
                canvas.restoreToCount(saveCount)
            } else {
                if (cullOpaque) {
                    val saveCount = canvas.save()
                    canvas.clipPath(path, SkClipOp.kDifference, true)
                    val fillPaint = SkPaint().apply {
                        color = spotColor
                        isAntiAlias = true
                    }
                    canvas.drawPath(effectiveSpotPath, fillPaint)
                    canvas.restoreToCount(saveCount)
                } else {
                    val fillPaint = SkPaint().apply {
                        color = spotColor
                        isAntiAlias = true
                    }
                    canvas.drawPath(effectiveSpotPath, fillPaint)
                }
            }
        }
    }

    // ─── R-suivi.31 helpers ──────────────────────────────────────────

    /**
     * Sample `z = a·x + b·y + c` at every endpoint emitted by the
     * path's verb stream (one per move/line/quad/conic/cubic — close
     * verbs add no new point). The returned array is empty for an
     * empty path and has length 1 for a path with a single Move.
     *
     * Used by [DrawShadow] to pick a tilt-aware `occluderZ` instead of
     * the centroid-only fallback. Per the R-suivi.31 contract this
     * stays a "max-z over vertex endpoints" — a full per-vertex
     * analytic mesh is tracked separately as R-suivi.30.
     */
    private fun sampleVertexZs(path: SkPath, zPlaneParams: SkPoint3): FloatArray {
        val out = ArrayList<Float>(8)
        val iter = SkPath.Iter(path, forceClose = false)
        val pts = FloatArray(8)
        while (true) {
            val v = iter.next(pts)
            if (v == SkPath.Verb.kDone) break
            val endIdx: Int = when (v) {
                SkPath.Verb.kMove -> 0
                SkPath.Verb.kLine -> 1
                SkPath.Verb.kQuad -> 2
                SkPath.Verb.kConic -> 2
                SkPath.Verb.kCubic -> 3
                SkPath.Verb.kClose -> -1
                SkPath.Verb.kDone -> -1
            }
            if (endIdx < 0) continue
            val x = pts[2 * endIdx]
            val y = pts[2 * endIdx + 1]
            val z = zPlaneParams.fX * x + zPlaneParams.fY * y + zPlaneParams.fZ
            out.add(max(z, 0f))
        }
        return out.toFloatArray()
    }

    /**
     * Compute the union of per-vertex projected spot bounds. For a
     * tilted plane each endpoint has its own z, which yields a
     * different per-vertex scale + translate ; the resulting envelope
     * is bigger on the high-z side than the centroid-only bounds.
     *
     * Returns an empty rect when [vertexZs] is sparse (< 2 samples)
     * or when every vertex shares the same z — in those cases the
     * caller falls back to the original centroid-scale spot path
     * bounds.
     */
    @Suppress("LongParameterList")
    private fun computeSpotBounds(
        path: SkPath,
        bounds: SkRect,
        vertexZs: FloatArray,
        cx: Float,
        cy: Float,
        lightPos: SkPoint3,
        lightRadius: Float,
        useDirectional: Boolean,
    ): SkRect {
        if (vertexZs.size < 2) return SkRect.MakeLTRB(0f, 0f, 0f, 0f)
        var minZ = vertexZs[0]
        var maxZ = vertexZs[0]
        for (z in vertexZs) {
            if (z < minZ) minZ = z
            if (z > maxZ) maxZ = z
        }
        // Flat / near-flat plane : nothing to gain from per-vertex
        // expansion ; fall back to the centroid-derived bounds.
        if (maxZ - minZ < 1e-3f) return SkRect.MakeLTRB(0f, 0f, 0f, 0f)

        var unionL = Float.POSITIVE_INFINITY
        var unionT = Float.POSITIVE_INFINITY
        var unionR = Float.NEGATIVE_INFINITY
        var unionB = Float.NEGATIVE_INFINITY

        // Walk the verbs again to grab the (endpoint x, y, z) triples.
        val iter = SkPath.Iter(path, forceClose = false)
        val pts = FloatArray(8)
        var idx = 0
        while (true) {
            val v = iter.next(pts)
            if (v == SkPath.Verb.kDone) break
            val endIdx: Int = when (v) {
                SkPath.Verb.kMove -> 0
                SkPath.Verb.kLine -> 1
                SkPath.Verb.kQuad -> 2
                SkPath.Verb.kConic -> 2
                SkPath.Verb.kCubic -> 3
                else -> -1
            }
            if (endIdx < 0) continue
            val z = if (idx < vertexZs.size) vertexZs[idx] else 0f
            idx++

            val sp = if (useDirectional) {
                getDirectionalParams(z, lightPos.fX, lightPos.fY, lightPos.fZ, lightRadius)
            } else {
                getSpotParams(z, lightPos.fX, lightPos.fY, lightPos.fZ, lightRadius)
            }
            // Project the path's bounding box under this vertex's z. We
            // could project each endpoint individually, but that
            // collapses to a polygon — taking the bbox under each
            // vertex's z and unioning lands on the bbox of the per-
            // vertex polygon, which is what the caller needs.
            val halfW = (bounds.right - bounds.left) * 0.5f * sp.scale
            val halfH = (bounds.bottom - bounds.top) * 0.5f * sp.scale
            val spotCx = cx + sp.translateX
            val spotCy = cy + sp.translateY
            val l = spotCx - halfW
            val t = spotCy - halfH
            val r = spotCx + halfW
            val b = spotCy + halfH
            if (l < unionL) unionL = l
            if (t < unionT) unionT = t
            if (r > unionR) unionR = r
            if (b > unionB) unionB = b
        }
        if (unionL > unionR || unionT > unionB) return SkRect.MakeLTRB(0f, 0f, 0f, 0f)
        return SkRect.MakeLTRB(unionL, unionT, unionR, unionB)
    }

    // ─── R-suivi.33 — projection cache ───────────────────────────────

    /**
     * Cache key for the projected occluder geometry, indexed by path
     * identity + light position + plane params. Identity-based path
     * matching keeps the cache cheap (no full coord comparison) and is
     * sufficient for the animation use case (same path instance
     * shadowed across frames).
     */
    private data class ProjectionKey(
        val pathIdentity: Int,
        val lightX: Float, val lightY: Float, val lightZ: Float,
        val planeA: Float, val planeB: Float, val planeC: Float,
    )

    /**
     * Number of cache hits served by [projectionCacheGet] since process
     * start. Exposed via [projectionCacheHits] for tests — production
     * callers shouldn't depend on it.
     */
    @Volatile
    private var cacheHitCount: Long = 0L

    /** WeakHashMap keyed by path identity ; entries drop when the key
     *  string falls out of scope. We can't key directly on `SkPath`
     *  because we want to clear entries when the path becomes
     *  unreachable, so we route through an inner indirection that
     *  binds the cached projection to the path's lifetime via a
     *  separate WeakHashMap<SkPath, MutableMap<ProjectionKey, SkPath>>.
     */
    private val projectionCache: WeakHashMap<SkPath, MutableMap<ProjectionKey, SkPath>> =
        WeakHashMap()

    private fun makeProjectionKey(
        path: SkPath, lightPos: SkPoint3, zPlaneParams: SkPoint3,
    ): ProjectionKey = ProjectionKey(
        System.identityHashCode(path),
        lightPos.fX, lightPos.fY, lightPos.fZ,
        zPlaneParams.fX, zPlaneParams.fY, zPlaneParams.fZ,
    )

    private fun projectionCacheGet(
        path: SkPath, lightPos: SkPoint3, zPlaneParams: SkPoint3,
    ): SkPath? {
        val perPath = synchronized(projectionCache) { projectionCache[path] } ?: return null
        val key = makeProjectionKey(path, lightPos, zPlaneParams)
        val hit = synchronized(projectionCache) { perPath[key] }
        if (hit != null) cacheHitCount++
        return hit
    }

    private fun projectionCachePut(
        path: SkPath, lightPos: SkPoint3, zPlaneParams: SkPoint3, projected: SkPath,
    ) {
        val key = makeProjectionKey(path, lightPos, zPlaneParams)
        synchronized(projectionCache) {
            val perPath = projectionCache.getOrPut(path) { HashMap() }
            perPath[key] = projected
        }
    }

    /**
     * Number of times [projectionCacheGet] has returned a cached
     * projection. Test-only — production callers must not depend on
     * the exact value.
     */
    internal fun projectionCacheHits(): Long = cacheHitCount

    /** Test-only : clear the projection cache and reset the hit counter. */
    internal fun projectionCacheClearForTest() {
        synchronized(projectionCache) { projectionCache.clear() }
        cacheHitCount = 0L
    }

    /**
     * Pre-bake the shadow geometry to amortize cost across animation
     * frames.
     *
     * Upstream caches a tessellated shadow mesh keyed on
     * `(path-genID, ctm, light)`. This port keeps the same intent: we
     * precompute the projected spot path (scale + translate around the
     * path's centroid) and stash it in a [WeakHashMap] keyed on the
     * path identity + light position + zPlaneParams. The next
     * [DrawShadow] call with the same key reuses the cached projection
     * — useful in animation scenarios where the same shape is shadowed
     * across many frames.
     *
     * [zPlaneParams] defaults to `(0, 0, 1)` (a flat plane at z = 1)
     * to keep source-compat with call sites that don't carry the plane
     * yet. Always returns `true` — failures fall through to a fresh
     * projection on the matching [DrawShadow] call.
     */
    public fun OptimizeForSurface(
        canvas: SkCanvas,
        path: SkPath,
        lightPos: SkPoint3,
        lightRadius: Float,
        zPlaneParams: SkPoint3 = SkPoint3(0f, 0f, 1f),
    ): Boolean {
        val bounds = path.computeBounds()
        if (bounds.isEmpty) return true
        val cx = (bounds.left + bounds.right) * 0.5f
        val cy = (bounds.top + bounds.bottom) * 0.5f
        val centroidZ = max(zPlaneParams.fX * cx + zPlaneParams.fY * cy + zPlaneParams.fZ, 0f)
        val sp = getSpotParams(centroidZ, lightPos.fX, lightPos.fY, lightPos.fZ, lightRadius)
        val spotMatrix = SkMatrix.MakeTrans(cx + sp.translateX, cy + sp.translateY)
            .preConcat(SkMatrix.MakeScale(sp.scale, sp.scale))
            .preConcat(SkMatrix.MakeTrans(-cx, -cy))
        val projected = path.makeTransform(spotMatrix)
        projectionCachePut(path, lightPos, zPlaneParams, projected)
        // Touch the canvas parameter to keep the upstream API surface.
        @Suppress("UNUSED_VARIABLE") val _c = canvas
        return true
    }

    /**
     * Generate bounding box for shadows relative to path. Includes both
     * the ambient and spot shadow bounds.
     *
     * Mirrors upstream `SkShadowUtils::GetLocalBounds`
     * (`include/utils/SkShadowUtils.h:86`). The simplification is the
     * same as [DrawShadow] : we evaluate the plane at the path's
     * centroid, compute worst-case ambient + spot blur radii, and
     * outset the path's local bounds. Returns `true` if [bounds] was
     * populated, `false` if the path was empty.
     *
     * `bounds` must have length ≥ 1 ; the result is written to
     * `bounds[0]` (mirrors the C++ `SkRect*` out-parameter).
     */
    public fun GetLocalBounds(
        ctm: SkMatrix,
        path: SkPath,
        zPlaneParams: SkPoint3,
        lightPos: SkPoint3,
        lightRadius: Float,
        flags: Int,
        bounds: Array<SkRect?>,
    ): Boolean {
        require(bounds.isNotEmpty()) { "bounds must have at least 1 element" }
        val pb = path.computeBounds()
        if (pb.isEmpty) return false

        val cx = (pb.left + pb.right) * 0.5f
        val cy = (pb.top + pb.bottom) * 0.5f
        val occluderZ = max(zPlaneParams.fX * cx + zPlaneParams.fY * cy + zPlaneParams.fZ, 0f)

        val ambR = ambientBlurRadius(occluderZ)
        val sp = if ((flags and kDirectionalLight_ShadowFlag) != 0) {
            getDirectionalParams(occluderZ, lightPos.fX, lightPos.fY, lightPos.fZ, lightRadius)
        } else {
            getSpotParams(occluderZ, lightPos.fX, lightPos.fY, lightPos.fZ, lightRadius)
        }

        // Spot path bounds : scale around centroid, then translate.
        val halfW = (pb.right - pb.left) * 0.5f * sp.scale
        val halfH = (pb.bottom - pb.top) * 0.5f * sp.scale
        val spotCx = cx + sp.translateX
        val spotCy = cy + sp.translateY
        val spotL = spotCx - halfW - sp.blurRadius
        val spotT = spotCy - halfH - sp.blurRadius
        val spotR = spotCx + halfW + sp.blurRadius
        val spotB = spotCy + halfH + sp.blurRadius

        val ambL = pb.left - ambR
        val ambT = pb.top - ambR
        val ambR2 = pb.right + ambR
        val ambB = pb.bottom + ambR

        // Ignore ctm here — the upstream API also returns *local* bounds ;
        // the ctm parameter is kept on the signature for source-compat
        // with call sites that pass it.
        @Suppress("UNUSED_VARIABLE")
        val _ctm = ctm

        bounds[0] = SkRect.MakeLTRB(
            min(ambL, spotL),
            min(ambT, spotT),
            max(ambR2, spotR),
            max(ambB, spotB),
        )
        // Use the ambient recip alpha to silence the unused-helper warning
        // and keep the symbol live for follow-up patches that wire it into
        // the analytic path's alpha computation.
        @Suppress("UNUSED_VARIABLE")
        val _recip = ambientRecipAlpha(occluderZ)
        return true
    }
}
