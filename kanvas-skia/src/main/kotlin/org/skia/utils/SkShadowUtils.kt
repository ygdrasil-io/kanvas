package org.skia.utils

import org.skia.core.SkCanvas
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

        // Evaluate the occluder height at the path's centroid.
        val cx = (bounds.left + bounds.right) * 0.5f
        val cy = (bounds.top + bounds.bottom) * 0.5f
        val occluderZ = max(zPlaneParams.fX * cx + zPlaneParams.fY * cy + zPlaneParams.fZ, 0f)

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
            // Spot transform : scale around the path's centroid (perspective
            // enlargement under the light) then translate (projection offset).
            val spotMatrix = SkMatrix.MakeTrans(cx + sp.translateX, cy + sp.translateY)
                .preConcat(SkMatrix.MakeScale(sp.scale, sp.scale))
                .preConcat(SkMatrix.MakeTrans(-cx, -cy))
            val spotPath = path.makeTransform(spotMatrix)

            if (spotSigma > 0f) {
                val spotPaint = SkPaint().apply {
                    color = spotColor
                    isAntiAlias = true
                    imageFilter = SkImageFilters.Blur(spotSigma, spotSigma, null)
                }
                val spotBounds = spotPath.computeBounds()
                    .makeOutset(sp.blurRadius + 1f, sp.blurRadius + 1f)
                val saveCount = canvas.saveLayer(spotBounds, spotPaint)
                val fillPaint = SkPaint().apply {
                    color = spotColor
                    isAntiAlias = true
                }
                canvas.drawPath(spotPath, fillPaint)
                canvas.restoreToCount(saveCount)
            } else {
                val fillPaint = SkPaint().apply {
                    color = spotColor
                    isAntiAlias = true
                }
                canvas.drawPath(spotPath, fillPaint)
            }
        }
    }

    /**
     * Pre-bake the shadow geometry to amortize cost across animation
     * frames.
     *
     * Upstream caches a tessellated shadow mesh keyed on
     * `(path-genID, ctm, light)` ; this port is blur-based and has no
     * such cache, so the method is a no-op that always returns `true`.
     * The signature mirrors the upstream surface so call sites can
     * compile unchanged.
     */
    @Suppress("UNUSED_PARAMETER")
    public fun OptimizeForSurface(
        canvas: SkCanvas,
        path: SkPath,
        lightPos: SkPoint3,
        lightRadius: Float,
    ): Boolean = true

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
