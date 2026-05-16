package org.skia.core

import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Backend-agnostic drawing surface — the bridge between [SkCanvas] and a
 * concrete rasterizer / GPU rasterizer.
 *
 * Extracted from [SkBitmapDevice] in Phase G1.1 of
 * [MIGRATION_PLAN_GPU_WEBGPU.md] so that `SkCanvas` can drive both a CPU
 * raster device ([SkBitmapDevice]) and an upcoming WebGPU device
 * (`SkWebGpuDevice` in `:gpu-raster`) through the same primitives.
 *
 * **Scope.** Only methods exercised by the cross-backend GMs land here.
 * The advanced raster-only surface (compositing layers back into a
 * parent via `compositeFrom`, the internal
 * `setActiveClip` / `setActiveClipShader` plumbing,
 * `drawColoredTriangle` / `drawTexturedTriangle`, direct `bitmap`
 * access) stays on [SkBitmapDevice] for now — `SkCanvas` casts to
 * [SkBitmapDevice] at those call sites and fails fast if a non-raster
 * device flows in. Generalising them is deferred to later G-phases (see
 * the G1.1 entry in the plan for the rationale).
 *
 * Coordinates entering this surface are already in **device pixels**;
 * the canvas owns the matrix and clip stacks and has transformed +
 * clipped before dispatching to the device. The `clip: SkIRect`
 * parameter on each draw call is the integer-aligned device clip that
 * the canvas has computed from its stack.
 */
public interface SkDevice {

    /** Device pixel width. */
    public val width: Int

    /** Device pixel height. */
    public val height: Int

    /**
     * Maximum drawable region in device coordinates — typically
     * `(0, 0, width, height)`. Layers and clip stacks intersect this
     * with the user's clip ops in [SkCanvas].
     */
    public fun deviceClipBounds(): SkIRect

    /** Fill / stroke a rectangle (with optional AA) per `paint`. */
    public fun drawRect(rect: SkRect, clip: SkIRect, paint: SkPaint)

    /**
     * Fill every pixel inside [clip] using [paint] — covers the
     * full clipped device area. Used by `SkCanvas.drawPaint` and
     * `SkCanvas.drawColor` paths.
     */
    public fun drawPaint(ctm: SkMatrix, clip: SkIRect, paint: SkPaint)

    /**
     * Fill / stroke [path] under [ctm] with [paint]. Source-space verbs;
     * the device flattens and rasterizes itself (matching upstream Skia's
     * `SkBaseDevice::drawPath` contract).
     */
    public fun drawPath(path: SkPath, ctm: SkMatrix, clip: SkIRect, paint: SkPaint)

    /**
     * Draw a region of [image] into [devDst] with sampling per
     * [sampling]. `constraint` controls whether texels outside [src]
     * may leak into the resampling kernel (matches upstream's
     * `SkCanvas::SrcRectConstraint`).
     */
    public fun drawImageRect(
        image: SkImage,
        src: SkRect,
        devDst: SkRect,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        constraint: SrcRectConstraint,
        clip: SkIRect,
    )
}
